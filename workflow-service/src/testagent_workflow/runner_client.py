"""Worker到专用Analysis Runner的签名客户端与场景1适配器。"""

from __future__ import annotations

import asyncio
from contextlib import suppress
from dataclasses import dataclass
from datetime import UTC, datetime
import hashlib
import hmac
import json
import re
import secrets
import time
from typing import Any

import httpx
from pydantic import BaseModel, ConfigDict, Field, ValidationError

from testagent_workflow.agui import AgUiEventType
from testagent_workflow.impact_engine import (
    AnalyzerOutcome,
    DiffManifest,
    FrozenRepository,
    ImpactState,
)
from testagent_workflow.platform import PlatformCapabilityClient, PlatformRequestIdentity


RUNNER_PREFIX = "/runner-api/v1"


class RunnerApiError(RuntimeError):
    def __init__(self, code: str, status_code: int) -> None:
        super().__init__(code)
        self.code = code
        self.status_code = status_code


class RunnerApiClient:
    def __init__(
        self,
        base_url: str,
        runner_id: str,
        hmac_secret: bytes,
        *,
        http_client: httpx.AsyncClient | None = None,
    ) -> None:
        if len(hmac_secret) < 32:
            raise ValueError("Runner HMAC密钥至少需要32字节")
        self._base_url = base_url.rstrip("/")
        self.runner_id = runner_id
        self._secret = hmac_secret
        # 代码分析不设总读取时长；连接、写入与连接池等待仍保持故障保护。
        self._http = http_client or httpx.AsyncClient(
            timeout=httpx.Timeout(connect=30.0, read=None, write=60.0, pool=30.0)
        )

    async def prepare(self, task_id: str, payload: dict[str, Any]) -> dict[str, Any]:
        return dict(await self._request("POST", f"{RUNNER_PREFIX}/tasks/{task_id}/prepare", payload))

    async def resume(self, task_id: str, run_id: str) -> dict[str, Any]:
        return dict(await self._request(
            "POST", f"{RUNNER_PREFIX}/tasks/{task_id}/resume", {"runId": run_id}
        ))

    async def manifest(self, task_id: str, run_id: str) -> dict[str, Any]:
        return dict(await self._request(
            "POST", f"{RUNNER_PREFIX}/tasks/{task_id}/manifest", {"runId": run_id}
        ))

    async def resolve_scope(
        self,
        task_id: str,
        run_id: str,
        selectors: list[dict[str, Any]],
    ) -> dict[str, Any]:
        return dict(await self._request(
            "POST",
            f"{RUNNER_PREFIX}/tasks/{task_id}/scope/resolve",
            {"runId": run_id, "selectors": selectors},
        ))

    async def analyze(
        self,
        task_id: str,
        analyzer_id: str,
        payload: dict[str, Any],
    ) -> dict[str, Any]:
        return dict(await self._request(
            "POST",
            f"{RUNNER_PREFIX}/tasks/{task_id}/analyzers/{analyzer_id}",
            payload,
        ))

    async def retain(
        self,
        task_id: str,
        run_id: str,
        retention_hours: int,
        operation_key: str,
    ) -> dict[str, Any]:
        return dict(await self._request(
            "POST",
            f"{RUNNER_PREFIX}/tasks/{task_id}/retain",
            {
                "runId": run_id,
                "retentionHours": retention_hours,
                "operationKey": operation_key,
            },
        ))

    async def cancel(self, task_id: str, run_id: str) -> None:
        await self._request(
            "POST", f"{RUNNER_PREFIX}/tasks/{task_id}/cancel", {"runId": run_id}
        )

    async def cleanup(self, task_id: str, run_id: str) -> None:
        await self._request(
            "POST", f"{RUNNER_PREFIX}/tasks/{task_id}/cleanup", {"runId": run_id}
        )

    async def aclose(self) -> None:
        await self._http.aclose()

    async def _request(self, method: str, path: str, payload: dict[str, Any]) -> Any:
        body = json.dumps(payload, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode()
        timestamp = str(int(time.time()))
        nonce = secrets.token_hex(24)
        digest = hashlib.sha256(body).hexdigest()
        canonical = "\n".join(
            [method, path, digest, self.runner_id, timestamp, nonce]
        )
        signature = hmac.new(self._secret, canonical.encode(), hashlib.sha256).hexdigest()
        response = await self._http.request(
            method,
            f"{self._base_url}{path}",
            content=body,
            headers={
                "Content-Type": "application/json",
                "X-Workflow-Runner-Id": self.runner_id,
                "X-Workflow-Timestamp": timestamp,
                "X-Workflow-Nonce": nonce,
                "X-Workflow-Body-SHA256": digest,
                "X-Workflow-Signature": signature,
            },
        )
        if response.is_error:
            try:
                code = str(response.json().get("code", "RUNNER_FAILED"))
            except ValueError:
                code = "RUNNER_FAILED"
            raise RunnerApiError(code, response.status_code)
        return response.json().get("data")


class ReportFollowupRetentionService:
    """在报告追问成功后同时滚动Runner与控制库中的源码保留期限。"""

    def __init__(
        self,
        store: Any,
        runner: RunnerApiClient,
        *,
        retention_hours: int = 48,
    ) -> None:
        self._store = store
        self._runner = runner
        self._retention_hours = retention_hours

    async def roll(self, report: Any, context: Any) -> None:
        try:
            current = await self._store.get_workspace_lease(report.task_id)
        except KeyError:
            # 历史报告允许在没有源码工作区时继续问答。
            return
        if current.get("status") != "STOPPED_RETAINED":
            # EXPIRED/CLEANUP_FAILED不能被普通报告追问复活；需要源码时必须新建任务。
            return

        request_hash = hashlib.sha256(str(context.request_id).encode()).hexdigest()[:24]
        operation_key = f"{report.run_id}:workspace:followup-{request_hash}"
        response = await self._runner.retain(
            report.task_id,
            report.run_id,
            self._retention_hours,
            operation_key,
        )
        workspace = dict(response.get("workspace", {}))
        expires_value = workspace.get("expiresAt")
        if workspace.get("status") != "STOPPED_RETAINED" or not expires_value:
            raise RunnerApiError("RUNNER_RESPONSE_INVALID", 502)
        try:
            expires_at = datetime.fromisoformat(
                str(expires_value).replace("Z", "+00:00")
            )
            if expires_at.tzinfo is None:
                raise ValueError("expiresAt缺少时区")
        except ValueError as exception:
            raise RunnerApiError("RUNNER_RESPONSE_INVALID", 502) from exception

        await self._store.upsert_workspace_lease(
            report.task_id,
            runner_id=self._runner.runner_id,
            container_id=workspace.get("containerId"),
            image_digest=workspace.get("imageDigest"),
            status="STOPPED_RETAINED",
            expires_at=expires_at,
            metadata={"runId": report.run_id},
        )
        run = await self._store.get_run(report.run_id, context.user_id)
        await self._store.append_event(
            run.conversation_id,
            AgUiEventType.CUSTOM,
            {
                "name": "workflow.workspace_state",
                "value": {
                    "taskId": report.task_id,
                    "runId": report.run_id,
                    "status": "STOPPED_RETAINED",
                    "expiresAt": str(expires_value),
                },
            },
            dedup_key=f"{operation_key}:workspace-state",
        )


class AnalyzerResult(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    summary: str
    impacted_features: list[dict[str, Any]] = Field(alias="impactedFeatures")
    cross_repository_impacts: list[dict[str, Any]] = Field(alias="crossRepositoryImpacts")
    risks: list[dict[str, Any]]
    code_evidence: list[dict[str, Any]] = Field(alias="codeEvidence")
    recommended_regression_tests: list[Any] = Field(alias="recommendedRegressionTests")
    uncertainties: list[Any]


@dataclass(slots=True)
class RunGrantManager:
    platform: PlatformCapabilityClient
    identity: PlatformRequestIdentity
    task_id: str
    run_id: str
    analyzer_ids: list[str]
    refresh_margin_seconds: float = 30.0
    minimum_refresh_interval_seconds: float = 5.0
    _grant: dict[str, Any] | None = None
    _lock: asyncio.Lock | None = None
    _refresh_task: asyncio.Task[None] | None = None
    _refresh_failure: RuntimeError | None = None

    async def get(self) -> dict[str, Any]:
        if self._lock is None:
            self._lock = asyncio.Lock()
        async with self._lock:
            if self._refresh_failure is not None:
                raise self._refresh_failure
            if self._grant is None:
                self._grant = await self.platform.issue_model_grant(
                    self.identity,
                    task_id=self.task_id,
                    run_id=self.run_id,
                    analyzer_ids=self.analyzer_ids,
                )
            value = dict(self._grant)
        self._ensure_refresh_task(value)
        return value

    async def refresh(self) -> dict[str, Any]:
        if self._lock is None:
            self._lock = asyncio.Lock()
        async with self._lock:
            if self._grant is None:
                raise RuntimeError("模型grant尚未签发")
            current = dict(self._grant)
            refreshed = await self.platform.refresh_model_grant(
                self.identity,
                task_id=self.task_id,
                run_id=self.run_id,
                grant_id=str(current["grantId"]),
            )
            if str(refreshed.get("grantId")) != str(current["grantId"]):
                raise RuntimeError("模型grant续期不得更换grantId")
            # Java只延长同一grant的TTL，刷新响应刻意不再次传输原始Bearer。
            merged = dict(current)
            merged.update({key: value for key, value in refreshed.items() if value is not None})
            self._grant = merged
            self._refresh_failure = None
            return dict(merged)

    async def revoke(self) -> None:
        refresh_task = self._refresh_task
        self._refresh_task = None
        if refresh_task is not None and refresh_task is not asyncio.current_task():
            refresh_task.cancel()
            with suppress(asyncio.CancelledError):
                await refresh_task
        if self._lock is None:
            self._lock = asyncio.Lock()
        async with self._lock:
            current = self._grant
            self._grant = None
            self._refresh_failure = None
        if current is None:
            return
        await self.platform.revoke_model_grant(
            self.identity,
            task_id=self.task_id,
            run_id=self.run_id,
            grant_id=str(current["grantId"]),
        )

    def _ensure_refresh_task(self, grant: dict[str, Any]) -> None:
        if not grant.get("expiresAt"):
            return
        if self._refresh_task is None or self._refresh_task.done():
            self._refresh_task = asyncio.create_task(self._refresh_loop())

    async def _refresh_loop(self) -> None:
        while True:
            if self._grant is None:
                return
            delay = self._refresh_delay(self._grant)
            await asyncio.sleep(delay)
            try:
                await self.refresh()
            except asyncio.CancelledError:
                raise
            except Exception:
                # 不保存下游异常正文，调用节点只看到稳定失败类型。
                self._refresh_failure = RuntimeError("模型grant续期失败")
                return

    def _refresh_delay(self, grant: dict[str, Any]) -> float:
        try:
            expires_at = datetime.fromisoformat(str(grant["expiresAt"]).replace("Z", "+00:00"))
            if expires_at.tzinfo is None:
                raise ValueError("expiresAt缺少时区")
            remaining = (expires_at.astimezone(UTC) - datetime.now(UTC)).total_seconds()
        except (KeyError, TypeError, ValueError) as exception:
            raise RuntimeError("模型grant过期时间无效") from exception
        return max(
            self.minimum_refresh_interval_seconds,
            remaining - self.refresh_margin_seconds,
        )


class PlatformAuthorizationAdapter:
    def __init__(self, platform: PlatformCapabilityClient) -> None:
        self._platform = platform

    async def authorize(self, state: ImpactState, operation_key: str) -> list[dict[str, Any]]:
        del operation_key
        identity = PlatformRequestIdentity(state["owner_user_id"], state["session_digest"])
        repository_ids = [
            str(value["repositoryId"]) for value in state["input_data"]["repositories"]
        ]
        authorized = await self._platform.authorize_repositories(identity, repository_ids)
        branch_lists = await asyncio.gather(
            *(self._platform.list_branches(identity, repository_id) for repository_id in repository_ids)
        )
        branches_by_id = dict(zip(repository_ids, branch_lists, strict=True))
        result: list[dict[str, Any]] = []
        for repository in authorized:
            repository_id = str(repository["repositoryId"])
            branches = branches_by_id[repository_id]
            default_branch = next(
                (str(value["name"]) for value in branches if bool(value.get("default"))),
                None,
            )
            result.append(
                {
                    **repository,
                    "defaultBranch": default_branch,
                    "availableBranches": [str(value["name"]) for value in branches],
                }
            )
        return result


class StoreProgressPublisher:
    """将固定图节点映射为可重放的原生AG-UI工具生命周期事件。"""

    def __init__(self, store: Any) -> None:
        self._store = store

    async def started(
        self,
        state: ImpactState,
        tool_call_id: str,
        tool_name: str,
    ) -> None:
        await self._store.append_event(
            state["conversation_id"],
            AgUiEventType.TOOL_CALL_START,
            {
                "toolCallId": tool_call_id,
                "toolCallName": tool_name,
                "runId": state["run_id"],
                "taskId": state["task_id"],
            },
            dedup_key=f"{tool_call_id}:start",
        )

    async def finished(
        self,
        state: ImpactState,
        tool_call_id: str,
        *,
        succeeded: bool,
    ) -> None:
        await self._store.append_event(
            state["conversation_id"],
            AgUiEventType.TOOL_CALL_END,
            {
                "toolCallId": tool_call_id,
                "runId": state["run_id"],
                "taskId": state["task_id"],
                "status": "SUCCEEDED" if succeeded else "FAILED",
            },
            dedup_key=f"{tool_call_id}:end:{'succeeded' if succeeded else 'failed'}",
        )


class RemoteRunnerAdapter:
    def __init__(
        self,
        platform: PlatformCapabilityClient,
        runner: RunnerApiClient,
        runner_public_key: str,
        store: Any,
        grant_manager: RunGrantManager,
        *,
        retention_hours: int = 48,
    ) -> None:
        self._platform = platform
        self._runner = runner
        self._runner_public_key = runner_public_key
        self._store = store
        self._grant_manager = grant_manager
        self._retention_hours = retention_hours

    async def freeze(
        self,
        state: ImpactState,
        authorized: list[dict[str, Any]],
        operation_key: str,
    ) -> list[FrozenRepository]:
        if state.get("run_kind") == "REANALYSIS":
            await self._emit_workspace_state(state, "RESUMING")
            response = await self._runner.resume(state["task_id"], state["run_id"])
        else:
            await self._emit_workspace_state(state, "PROVISIONING")
            identity = PlatformRequestIdentity(state["owner_user_id"], state["session_digest"])
            requested = {
                str(value["repositoryId"]): value
                for value in state["input_data"]["repositories"]
            }
            checkout_values: list[dict[str, Any]] = []
            for index, repository in enumerate(authorized):
                repository_id = str(repository["repositoryId"])
                ticket = await self._platform.issue_checkout_ticket(
                    identity,
                    repository_id=repository_id,
                    task_id=state["task_id"],
                    run_id=state["run_id"],
                    runner_id=self._runner.runner_id,
                    runner_public_key=self._runner_public_key,
                    target_branch=str(requested[repository_id]["targetBranch"]),
                    baseline_branch=requested[repository_id].get("baselineBranch"),
                )
                checkout_values.append(
                    {
                        "repositoryId": repository_id,
                        "repositoryAlias": _repository_alias(repository, index),
                        "targetBranch": str(requested[repository_id]["targetBranch"]),
                        "checkoutTicketId": ticket,
                    }
                )
            response = await self._runner.prepare(
                state["task_id"],
                {
                    "runId": state["run_id"],
                    "operationKey": operation_key,
                    "repositories": checkout_values,
                    "analyzerIds": state["input_data"].get("analyzerIds", ["codex"]),
                },
            )
        frozen = [_frozen_repository(value) for value in response["repositories"]]
        await self._store.save_task_repositories(state["task_id"], frozen)
        await self._persist_workspace(state, response)
        return frozen

    async def build_manifest(
        self,
        state: ImpactState,
        repositories: list[FrozenRepository],
        operation_key: str,
    ) -> DiffManifest:
        del repositories, operation_key
        value = await self._runner.manifest(state["task_id"], state["run_id"])
        return DiffManifest(
            files=list(value.get("files", [])),
            statistics=dict(value.get("statistics", {})),
            hunks=list(value.get("hunks", [])),
        )

    async def resolve_scope(
        self,
        state: ImpactState,
        repositories: list[FrozenRepository],
        operation_key: str,
    ) -> dict[str, Any]:
        del repositories, operation_key
        value = await self._runner.resolve_scope(
            state["task_id"],
            state["run_id"],
            list(state["input_data"].get("scopeSelectors", [])),
        )
        resolved = list(value.get("resolvedSelectors", []))
        return {
            "resolvedSelectors": resolved,
            "requiredInput": list(value.get("requiredInput", [])),
        }

    async def stop_and_retain(self, state: ImpactState, operation_key: str) -> None:
        await self._grant_manager.revoke()
        response = await self._runner.retain(
            state["task_id"],
            state["run_id"],
            self._retention_hours,
            operation_key,
        )
        await self._persist_workspace(state, response)

    async def _persist_workspace(
        self,
        state: ImpactState,
        response: dict[str, Any],
    ) -> None:
        workspace = dict(response.get("workspace", {}))
        if not workspace:
            return
        expires_at = workspace.get("expiresAt")
        await self._store.upsert_workspace_lease(
            state["task_id"],
            runner_id=self._runner.runner_id,
            container_id=workspace.get("containerId"),
            image_digest=workspace.get("imageDigest"),
            status=str(workspace.get("status", "NONE")),
            expires_at=datetime.fromisoformat(expires_at) if expires_at else None,
            metadata={"runId": state["run_id"]},
        )
        await self._emit_workspace_state(
            state,
            str(workspace.get("status", "NONE")),
            expires_at=expires_at,
        )

    async def _emit_workspace_state(
        self,
        state: ImpactState,
        status: str,
        *,
        expires_at: str | None = None,
    ) -> None:
        await self._store.append_event(
            state["conversation_id"],
            AgUiEventType.CUSTOM,
            {
                "name": "workflow.workspace_state",
                "value": {
                    "taskId": state["task_id"],
                    "runId": state["run_id"],
                    "status": status,
                    "expiresAt": expires_at,
                },
            },
        )


class RunnerAnalyzerAdapter:
    def __init__(
        self,
        runner: RunnerApiClient,
        grant_manager: RunGrantManager,
        *,
        model_name: str,
    ) -> None:
        self._runner = runner
        self._grant_manager = grant_manager
        self._model_name = model_name

    async def analyze(
        self,
        analyzer_id: str,
        state: ImpactState,
        repositories: list[FrozenRepository],
        manifest: DiffManifest,
        operation_key: str,
    ) -> AnalyzerOutcome:
        grant = await self._grant_manager.get()
        payload = {
            "runId": state["run_id"],
            "operationKey": operation_key,
            "modelGrant": grant["grant"],
            "modelGatewayUrl": grant["gatewayUrl"],
            "modelName": self._model_name,
            "scopeSelectors": state["input_data"].get("scopeSelectors", []),
            "outputSchema": AnalyzerResult.model_json_schema(by_alias=True),
        }
        try:
            value = await self._runner.analyze(state["task_id"], analyzer_id, payload)
        except RunnerApiError as exception:
            if exception.status_code != 401:
                raise
            grant = await self._grant_manager.refresh()
            payload["modelGrant"] = grant["grant"]
            value = await self._runner.analyze(state["task_id"], analyzer_id, payload)
        try:
            parsed = AnalyzerResult.model_validate(value["result"])
        except ValidationError:
            repair_payload = {
                **payload,
                "operationKey": f"{operation_key}:repair",
                "repairResult": value.get("result"),
            }
            repaired = await self._runner.analyze(
                state["task_id"], analyzer_id, repair_payload
            )
            parsed = AnalyzerResult.model_validate(repaired["result"])
        return AnalyzerOutcome(
            analyzer_id=analyzer_id,
            succeeded=True,
            result=parsed.model_dump(mode="json", by_alias=True),
            error_code=None,
        )


class RunCancellationService:
    """用户取消时并行撤销run级模型委托并删除对应Runner工作区。"""

    def __init__(self, platform: PlatformCapabilityClient, runner: RunnerApiClient) -> None:
        self._platform = platform
        self._runner = runner

    async def cancel(self, run: Any, identity: PlatformRequestIdentity) -> None:
        results = await asyncio.gather(
            self._platform.revoke_model_grants_for_run(
                identity,
                task_id=run.task_id,
                run_id=run.id,
            ),
            self._runner.cancel(run.task_id, run.id),
            return_exceptions=True,
        )
        failures = [value for value in results if isinstance(value, BaseException)]
        if failures:
            # 不回显下游异常正文，避免内部URL或凭据上下文进入HTTP错误。
            raise RunnerApiError("RUN_CANCELLATION_INCOMPLETE", 502)


def _frozen_repository(value: dict[str, Any]) -> FrozenRepository:
    return FrozenRepository(
        repository_id=str(value["repositoryId"]),
        alias=str(value["repositoryAlias"]),
        default_branch=str(value["defaultBranch"]),
        default_head=str(value["defaultHead"]),
        target_branch=str(value["targetBranch"]),
        target_head=str(value["targetHead"]),
        merge_base=str(value["mergeBase"]),
        missing_dependencies=tuple(value.get("missingDependencies", [])),
    )


def _repository_alias(repository: dict[str, Any], index: int) -> str:
    """生成跨应用唯一、可读且能安全作为Runner一级目录名的确定性别名。"""

    repository_id = str(repository["repositoryId"])
    readable = str(repository.get("englishName") or repository.get("name") or "repository")
    prefix = re.sub(r"[^A-Za-z0-9._-]+", "-", readable).strip("._-") or "repository"
    digest = hashlib.sha256(repository_id.encode()).hexdigest()[:10]
    return f"{prefix[:100]}-{index + 1}-{digest}"
