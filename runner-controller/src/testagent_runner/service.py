"""任务工作区、冻结检出、分析容器和48小时保留的Runner业务实现。"""

from __future__ import annotations

import asyncio
from contextlib import ExitStack, suppress
from datetime import UTC, datetime, timedelta
import hashlib
import json
from pathlib import Path
import re
import shutil
from typing import Any

from testagent_runner.analyzer_executor import DockerAnalyzerExecutor
from testagent_runner.credentials import RunnerCredentialDecryptor
from testagent_runner.docker_runtime import AnalysisContainerSpec, DockerRuntime
from testagent_runner.git_workspace import (
    AuthorizedSubmoduleSpec,
    FrozenCheckout,
    GitWorkspace,
    RepositoryCheckoutSpec,
    validate_known_hosts_file,
)
from testagent_runner.settings import RunnerSettings
from testagent_runner.scope_resolver import ScopeResolver
from testagent_runner.tickets import CheckoutTicketClient


class RunnerServiceError(RuntimeError):
    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code


class RunnerService:
    _TASK_ID = re.compile(r"^task_[a-zA-Z0-9_-]{8,58}$")

    def __init__(
        self,
        settings: RunnerSettings,
        tickets: CheckoutTicketClient,
        credentials: RunnerCredentialDecryptor,
        docker: DockerRuntime,
        analyzers: DockerAnalyzerExecutor,
    ) -> None:
        self._settings = settings
        self._tickets = tickets
        self._credentials = credentials
        self._docker = docker
        self._analyzers = analyzers
        self._scope_resolver = ScopeResolver()
        self._locks: dict[str, asyncio.Lock] = {}
        self._analyzer_locks: dict[str, asyncio.Lock] = {}
        settings.root.mkdir(parents=True, exist_ok=True)
        validate_known_hosts_file(settings.known_hosts_path)

    async def readiness(self) -> dict[str, str]:
        """就绪探测必须重新验证磁盘、Docker版本与受限网络标签。"""

        self._ensure_capacity()
        validate_known_hosts_file(self._settings.known_hosts_path)
        await asyncio.to_thread(self._docker.verify_server)
        await asyncio.to_thread(self._docker.verify_network, self._settings.analysis_network)
        return {"status": "UP", "network": self._settings.analysis_network}

    async def aclose(self) -> None:
        close = getattr(self._tickets, "aclose", None)
        if close is not None:
            await close()

    async def prepare(self, task_id: str, payload: dict[str, Any]) -> dict[str, Any]:
        async with self._lock(task_id):
            self._ensure_capacity()
            task_root = self._task_root(task_id)
            state_path = task_root / "state.json"
            if state_path.exists():
                state = self._read_state(state_path)
                if state.get("operationKey") == payload.get("operationKey"):
                    return self._state_response(state)
                raise RunnerServiceError("WORKSPACE_ALREADY_EXISTS", "任务工作区已经存在")
            workspace_root = task_root / "workspace"
            output_root = workspace_root / "output"
            output_root.mkdir(parents=True, exist_ok=False, mode=0o770)
            output_root.chmod(0o770)
            frozen: list[FrozenCheckout] = []
            container_started = False
            try:
                git_workspace = GitWorkspace(
                    workspace_root,
                    known_hosts_path=self._settings.known_hosts_path,
                )
                for value in payload.get("repositories", []):
                    material = await self._tickets.consume(
                        str(value["checkoutTicketId"]), task_id, str(payload["runId"])
                    )
                    if material.repository_id != str(value["repositoryId"]):
                        raise RunnerServiceError("TICKET_SCOPE_MISMATCH", "checkout ticket仓库不匹配")
                    if material.target_branch != str(value["targetBranch"]):
                        raise RunnerServiceError("TICKET_SCOPE_MISMATCH", "checkout ticket目标分支不匹配")
                    with ExitStack() as credential_stack:
                        private_key = credential_stack.enter_context(
                            self._credentials.materialize(material.encrypted_private_key)
                        )
                        authorized_submodules = tuple(
                            AuthorizedSubmoduleSpec(
                                repository_id=value.repository_id,
                                remote_url=value.remote_url,
                                private_key_path=(
                                    str(key_path)
                                    if (key_path := credential_stack.enter_context(
                                        self._credentials.materialize(value.encrypted_private_key)
                                    ))
                                    else None
                                ),
                            )
                            for value in material.authorized_submodules
                        )
                        checkout = await asyncio.to_thread(
                            git_workspace.checkout,
                            RepositoryCheckoutSpec(
                                repository_id=material.repository_id,
                                alias=str(value["repositoryAlias"]),
                                remote_url=material.remote_url,
                                default_branch=material.default_branch,
                                target_branch=material.target_branch,
                                private_key_path=str(private_key) if private_key else None,
                                authorized_submodules=authorized_submodules,
                            ),
                        )
                    frozen.append(checkout)
                for analyzer_id in payload.get("analyzerIds", []):
                    analyzer_root = output_root / str(analyzer_id)
                    analyzer_root.mkdir(mode=0o770)
                    analyzer_root.chmod(0o770)
                container_id = await asyncio.to_thread(
                    self._docker.create_and_start,
                    AnalysisContainerSpec(
                        task_id=task_id,
                        image=self._settings.analysis_image,
                        repositories_path=git_workspace.repositories_root,
                        output_path=output_root,
                        network=self._settings.analysis_network,
                        analyzer_ids=tuple(payload.get("analyzerIds", ["codex"])),
                    ),
                )
                container_started = True
                state = {
                    "taskId": task_id,
                    "runId": payload["runId"],
                    "operationKey": payload.get("operationKey"),
                    "containerId": container_id,
                    "imageDigest": self._settings.analysis_image,
                    "analyzerIds": list(payload.get("analyzerIds", ["codex"])),
                    "status": "ACTIVE",
                    "expiresAt": None,
                    "repositories": [self._repository_response(value) for value in frozen],
                }
                self._write_state(state_path, state)
                return self._state_response(state)
            except Exception:
                # 状态落盘也属于prepare事务边界；容器已启动时必须先精确删除，
                # 避免留下仍挂载源码卷但不再受Runner状态管理的孤儿容器。
                if container_started:
                    with suppress(Exception):
                        await asyncio.to_thread(self._docker.remove, task_id)
                # 准备失败不保留可能含部分源码的目录；凭据上下文已先行擦除。
                shutil.rmtree(task_root, ignore_errors=True)
                raise

    async def manifest(self, task_id: str, run_id: str) -> dict[str, Any]:
        async with self._lock(task_id):
            task_root = self._task_root(task_id)
            state = self._active_state(task_root / "state.json", run_id)
            checkouts = [self._checkout_from_state(task_root, value) for value in state["repositories"]]
            manifest = await asyncio.to_thread(
                GitWorkspace(task_root / "workspace").build_manifest, checkouts
            )
            return {
                "files": manifest.files,
                "statistics": manifest.statistics,
                "hunks": manifest.hunks,
            }

    async def resolve_scope(
        self,
        task_id: str,
        run_id: str,
        selectors: list[dict[str, Any]],
    ) -> dict[str, Any]:
        async with self._lock(task_id):
            task_root = self._task_root(task_id)
            state = self._active_state(task_root / "state.json", run_id)
            checkouts = [
                self._checkout_from_state(task_root, value) for value in state["repositories"]
            ]
            return await asyncio.to_thread(
                self._scope_resolver.resolve,
                checkouts,
                selectors,
            )

    async def analyze(
        self,
        task_id: str,
        analyzer_id: str,
        payload: dict[str, Any],
    ) -> dict[str, Any]:
        task_root = self._task_root(task_id)
        state = self._active_state(task_root / "state.json", str(payload["runId"]))
        if analyzer_id not in state.get("analyzerIds", []):
            raise RunnerServiceError("ANALYZER_NOT_SELECTED", "智能体未在任务中注册")
        if payload.get("modelGatewayUrl") != self._settings.model_gateway_url:
            raise RunnerServiceError("MODEL_GATEWAY_SCOPE_MISMATCH", "模型网关地址不在Runner白名单")
        if not re.fullmatch(
            r"[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}",
            str(payload.get("modelName", "")),
        ):
            raise RunnerServiceError("MODEL_SCOPE_INVALID", "代码分析模型ID无效")
        operation_key = str(payload.get("operationKey", ""))
        if not operation_key.startswith(f"{payload['runId']}:") or len(operation_key) > 255:
            raise RunnerServiceError("INVALID_OPERATION_KEY", "operationKey未绑定当前run")
        cache_path = self._analyzer_cache_path(task_root, analyzer_id, operation_key)
        # 同任务智能体顺序执行；每次调用后Runner会重启容器，避免遗留后台进程污染下一智能体。
        async with self._analyzer_locks.setdefault(task_id, asyncio.Lock()):
            if cache_path.is_file():
                cached = json.loads(cache_path.read_text(encoding="utf-8"))
                if not isinstance(cached, dict):
                    raise RunnerServiceError("ANALYZER_CACHE_INVALID", "智能体幂等结果损坏")
                return {"result": cached}
            enriched = {
                **payload,
                "repositories": state["repositories"],
                "taskId": task_id,
                "imageDigest": state["imageDigest"],
            }
            result = await self._analyzers.execute(
                task_id,
                analyzer_id,
                task_root / "workspace" / "output",
                enriched,
            )
            # 幂等缓存属于Runner控制状态，不能放在分析容器可写的output挂载中。
            cache_path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
            cache_path.parent.chmod(0o700)
            temporary = cache_path.with_suffix(".tmp")
            temporary.write_text(json.dumps(result, ensure_ascii=False), encoding="utf-8")
            temporary.chmod(0o600)
            temporary.replace(cache_path)
            return {"result": result}

    async def retain(
        self,
        task_id: str,
        run_id: str,
        retention_hours: int,
        operation_key: str,
    ) -> dict[str, Any]:
        async with self._lock(task_id):
            state_path = self._task_root(task_id) / "state.json"
            state = self._read_state(state_path)
            if state.get("runId") != run_id:
                raise RunnerServiceError("RUN_SCOPE_MISMATCH", "runId与工作区不匹配")
            if not operation_key.startswith(f"{run_id}:") or len(operation_key) > 255:
                raise RunnerServiceError("INVALID_OPERATION_KEY", "operationKey未绑定当前run")
            if (
                state.get("status") == "STOPPED_RETAINED"
                and state.get("retentionOperationKey") == operation_key
            ):
                return self._state_response(state)
            if state.get("status") == "ACTIVE":
                await asyncio.to_thread(self._docker.stop_retained, task_id)
            elif state.get("status") != "STOPPED_RETAINED":
                raise RunnerServiceError("WORKSPACE_NOT_ACTIVE", "任务工作区未运行")
            state["status"] = "STOPPED_RETAINED"
            state["runId"] = run_id
            state["retentionOperationKey"] = operation_key
            state["expiresAt"] = (datetime.now(UTC) + timedelta(hours=retention_hours)).isoformat()
            self._write_state(state_path, state)
            return self._state_response(state)

    async def resume(self, task_id: str, run_id: str) -> dict[str, Any]:
        async with self._lock(task_id):
            state_path = self._task_root(task_id) / "state.json"
            state = self._read_state(state_path)
            expires_at = state.get("expiresAt")
            if expires_at and datetime.fromisoformat(expires_at) <= datetime.now(UTC):
                raise RunnerServiceError("WORKSPACE_EXPIRED", "任务工作区已过期")
            if state.get("status") == "STOPPED_RETAINED":
                await asyncio.to_thread(self._docker.resume, task_id)
            state["status"] = "ACTIVE"
            state["runId"] = run_id
            state["expiresAt"] = None
            self._write_state(state_path, state)
            return self._state_response(state)

    async def cancel(self, task_id: str, run_id: str) -> None:
        await self.cleanup(task_id, run_id)

    async def cleanup(self, task_id: str, run_id: str) -> None:
        """幂等删除精确任务容器与源码；取消和到期清理共用同一安全边界。"""

        async with self._lock(task_id):
            task_root = self._task_root(task_id)
            if task_root.exists():
                state_path = task_root / "state.json"
                if state_path.exists():
                    state = self._read_state(state_path)
                    if state.get("runId") != run_id:
                        raise RunnerServiceError("RUN_SCOPE_MISMATCH", "runId与工作区不匹配")
            # 无论本地状态是否存在都精确检查并删除同名容器；只有Docker明确成功
            # （含已不存在）后才删除状态与源码，使控制面CLEANUP_FAILED可以真实重试。
            await asyncio.to_thread(self._docker.remove, task_id)
            shutil.rmtree(task_root, ignore_errors=True)

    async def cleanup_expired(self) -> list[str]:
        cleaned: list[str] = []
        for state_path in self._settings.root.glob("task_*/state.json"):
            state = self._read_state(state_path)
            expires_at = state.get("expiresAt")
            if not expires_at or datetime.fromisoformat(expires_at) > datetime.now(UTC):
                continue
            task_id = str(state["taskId"])
            try:
                await asyncio.to_thread(self._docker.remove, task_id)
                shutil.rmtree(state_path.parent, ignore_errors=True)
                cleaned.append(task_id)
            except Exception:
                state["status"] = "CLEANUP_FAILED"
                self._write_state(state_path, state)
        return cleaned

    def _task_root(self, task_id: str) -> Path:
        if not self._TASK_ID.fullmatch(task_id):
            raise RunnerServiceError("INVALID_TASK_ID", "taskId格式无效")
        path = (self._settings.root / task_id).resolve()
        if path.parent != self._settings.root.resolve():
            raise RunnerServiceError("INVALID_TASK_ID", "task目录越界")
        return path

    def _lock(self, task_id: str) -> asyncio.Lock:
        self._task_root(task_id)
        return self._locks.setdefault(task_id, asyncio.Lock())

    @staticmethod
    def _analyzer_cache_path(task_root: Path, analyzer_id: str, operation_key: str) -> Path:
        digest = hashlib.sha256(operation_key.encode()).hexdigest()
        return task_root / "control" / "operations" / analyzer_id / f"{digest}.json"

    def _ensure_capacity(self) -> None:
        if shutil.disk_usage(self._settings.root).free < self._settings.minimum_free_bytes:
            raise RunnerServiceError("RUNNER_DISK_PRESSURE", "分析节点磁盘水位不足")

    @staticmethod
    def _write_state(path: Path, value: dict[str, Any]) -> None:
        temporary = path.with_suffix(".tmp")
        temporary.write_text(json.dumps(value, ensure_ascii=False), encoding="utf-8")
        temporary.chmod(0o600)
        temporary.replace(path)

    @staticmethod
    def _read_state(path: Path) -> dict[str, Any]:
        if not path.is_file():
            raise RunnerServiceError("WORKSPACE_NOT_FOUND", "任务工作区不存在")
        value = json.loads(path.read_text(encoding="utf-8"))
        if not isinstance(value, dict):
            raise RunnerServiceError("WORKSPACE_STATE_INVALID", "任务工作区状态损坏")
        return value

    def _active_state(self, path: Path, run_id: str) -> dict[str, Any]:
        state = self._read_state(path)
        if state.get("status") != "ACTIVE":
            raise RunnerServiceError("WORKSPACE_NOT_ACTIVE", "任务工作区未运行")
        if state.get("runId") != run_id:
            raise RunnerServiceError("RUN_SCOPE_MISMATCH", "runId与工作区不匹配")
        return state

    @staticmethod
    def _state_response(state: dict[str, Any]) -> dict[str, Any]:
        return {
            "repositories": state["repositories"],
            "workspace": {
                "status": state["status"],
                "containerId": state.get("containerId"),
                "imageDigest": state.get("imageDigest"),
                "expiresAt": state.get("expiresAt"),
            },
        }

    @staticmethod
    def _repository_response(value: FrozenCheckout) -> dict[str, Any]:
        return {
            "repositoryId": value.repository_id,
            "repositoryAlias": value.alias,
            "defaultBranch": value.default_branch,
            "defaultHead": value.default_head,
            "targetBranch": value.target_branch,
            "targetHead": value.target_head,
            "mergeBase": value.merge_base,
            "missingDependencies": list(value.missing_dependencies),
        }

    @staticmethod
    def _checkout_from_state(task_root: Path, value: dict[str, Any]) -> FrozenCheckout:
        return FrozenCheckout(
            repository_id=value["repositoryId"],
            alias=value["repositoryAlias"],
            repository_path=task_root / "workspace" / "repos" / value["repositoryAlias"],
            default_branch=value["defaultBranch"],
            default_head=value["defaultHead"],
            target_branch=value["targetBranch"],
            target_head=value["targetHead"],
            merge_base=value["mergeBase"],
            missing_dependencies=tuple(value.get("missingDependencies", [])),
        )
