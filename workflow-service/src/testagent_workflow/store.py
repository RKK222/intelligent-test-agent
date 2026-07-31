"""工作流存储端口及用于单元/API测试的内存实现。"""

from __future__ import annotations

import asyncio
from dataclasses import dataclass
from datetime import datetime
import hashlib
from typing import Any, Protocol
from uuid import uuid4

from testagent_workflow.agui import AgUiEvent, AgUiEventType
from testagent_workflow.models import RunStatus, utc_now


class ActiveRunConflict(RuntimeError):
    """一个对话已经存在活动run。"""


class WorkspaceNotReusable(RuntimeError):
    """历史任务的源码工作区已过期、被清理或不处于可恢复状态。"""


class RunNotCancelable(RuntimeError):
    """运行已经进入非取消终态，不能改写为CANCELED。"""


@dataclass(frozen=True, slots=True)
class Conversation:
    id: str
    owner_user_id: str
    title: str
    created_at: datetime


@dataclass(frozen=True, slots=True)
class Message:
    id: str
    conversation_id: str
    role: str
    content: str
    client_request_id: str | None
    created_at: datetime


@dataclass(frozen=True, slots=True)
class WorkflowRun:
    id: str
    task_id: str
    conversation_id: str
    owner_user_id: str
    workflow_id: str
    workflow_version: str
    input_data: dict[str, Any]
    status: RunStatus
    created_at: datetime
    updated_at: datetime
    session_digest: str = ""
    run_kind: str = "INITIAL"
    checkpoint_namespace: str = ""


@dataclass(frozen=True, slots=True)
class ReportVersion:
    id: str
    task_id: str
    run_id: str
    version: int
    is_current: bool
    structured_report: dict[str, Any]
    markdown_report: str
    created_at: datetime


@dataclass(frozen=True, slots=True)
class SubmissionResult:
    message_id: str
    assistant_message_id: str | None
    task_id: str | None
    run_id: str | None
    status: str | None
    required_input: tuple[str, ...]


class WorkflowStore(Protocol):
    async def create_conversation(self, owner_user_id: str, title: str) -> Conversation: ...

    async def get_conversation(
        self,
        conversation_id: str,
        actor_user_id: str,
        *,
        is_super_admin: bool = False,
    ) -> Conversation: ...

    async def list_messages(
        self,
        conversation_id: str,
        actor_user_id: str,
        *,
        is_super_admin: bool = False,
    ) -> list[Message]: ...

    async def list_events(self, conversation_id: str, *, after_seq: int = 0) -> list[AgUiEvent]: ...

    async def get_node_operation_result(
        self, run_id: str, node_id: str, operation_key: str
    ) -> dict[str, Any] | None: ...

    async def save_node_operation_result(
        self,
        run_id: str,
        node_id: str,
        operation_key: str,
        result: dict[str, Any],
    ) -> dict[str, Any]: ...

    async def get_latest_run_for_conversation(
        self,
        conversation_id: str,
        actor_user_id: str,
        *,
        is_super_admin: bool = False,
    ) -> WorkflowRun: ...

    async def transition_claimed_run(
        self,
        run_id: str,
        worker_id: str,
        status: RunStatus,
    ) -> WorkflowRun | None: ...

    async def get_workspace_lease(self, task_id: str) -> dict[str, Any]: ...

    async def list_workspace_leases_due_for_cleanup(
        self, *, limit: int = 100
    ) -> list[dict[str, Any]]: ...

    async def set_workspace_cleanup_status(self, task_id: str, status: str) -> None: ...


class InMemoryWorkflowStore:
    """保持与PostgreSQL实现相同领域约束的并发安全内存存储。"""

    def __init__(self) -> None:
        self._lock = asyncio.Lock()
        self._conversations: dict[str, Conversation] = {}
        self._messages: dict[str, list[Message]] = {}
        self._message_requests: dict[tuple[str, str], Message] = {}
        self._runs: dict[str, WorkflowRun] = {}
        self._events: dict[str, list[AgUiEvent]] = {}
        self._event_dedup: dict[tuple[str, str], AgUiEvent] = {}
        self._submissions: dict[tuple[str, str], SubmissionResult] = {}
        self._reports: dict[str, list[ReportVersion]] = {}
        self._run_workers: dict[str, str] = {}
        self._task_repositories: dict[str, list[Any]] = {}
        self._analyzer_results: dict[str, dict[str, dict[str, Any]]] = {}
        self._node_operation_results: dict[tuple[str, str, str], dict[str, Any]] = {}
        self._workspace_leases: dict[str, dict[str, Any]] = {}
        self._audit_logs: list[dict[str, Any]] = []

    async def create_conversation(self, owner_user_id: str, title: str) -> Conversation:
        conversation = Conversation(
            id=self._id("conv"),
            owner_user_id=owner_user_id,
            title=title.strip() or "新对话",
            created_at=utc_now(),
        )
        async with self._lock:
            self._conversations[conversation.id] = conversation
            self._messages[conversation.id] = []
            self._events[conversation.id] = []
        return conversation

    async def get_conversation(
        self,
        conversation_id: str,
        actor_user_id: str,
        *,
        is_super_admin: bool = False,
    ) -> Conversation:
        conversation = self._conversations.get(conversation_id)
        if conversation is None:
            raise KeyError("对话不存在")
        if conversation.owner_user_id != actor_user_id and not is_super_admin:
            raise PermissionError("无权访问该对话")
        return conversation

    async def list_conversations(
        self,
        actor_user_id: str,
        *,
        owner_user_id: str | None = None,
        is_super_admin: bool = False,
    ) -> list[Conversation]:
        requested_owner = owner_user_id or actor_user_id
        if requested_owner != actor_user_id and not is_super_admin:
            raise PermissionError("无权查看其他用户的对话")
        return sorted(
            [value for value in self._conversations.values() if value.owner_user_id == requested_owner],
            key=lambda value: value.created_at,
            reverse=True,
        )

    async def append_message(
        self,
        conversation_id: str,
        actor_user_id: str,
        *,
        role: str,
        content: str,
        client_request_id: str | None = None,
    ) -> Message:
        await self.get_conversation(conversation_id, actor_user_id)
        async with self._lock:
            if client_request_id is not None:
                existing = self._message_requests.get((conversation_id, client_request_id))
                if existing is not None:
                    return existing
            message = Message(
                id=self._id("msg"),
                conversation_id=conversation_id,
                role=role,
                content=content,
                client_request_id=client_request_id,
                created_at=utc_now(),
            )
            self._messages[conversation_id].append(message)
            if client_request_id is not None:
                self._message_requests[(conversation_id, client_request_id)] = message
            return message

    async def list_messages(
        self,
        conversation_id: str,
        actor_user_id: str,
        *,
        is_super_admin: bool = False,
    ) -> list[Message]:
        await self.get_conversation(conversation_id, actor_user_id, is_super_admin=is_super_admin)
        return list(self._messages[conversation_id])

    async def create_run(
        self,
        conversation_id: str,
        actor_user_id: str,
        *,
        workflow_id: str,
        workflow_version: str,
        input_data: dict[str, Any],
        session_digest: str = "",
        request_key: str | None = None,
    ) -> WorkflowRun:
        await self.get_conversation(conversation_id, actor_user_id)
        async with self._lock:
            run_id = self._stable_id("run", request_key) if request_key else self._id("run")
            existing = self._runs.get(run_id)
            if existing is not None:
                if (
                    existing.conversation_id != conversation_id
                    or existing.owner_user_id != actor_user_id
                ):
                    raise ActiveRunConflict("幂等运行范围不匹配")
                return existing
            if any(
                run.conversation_id == conversation_id and run.status.active
                for run in self._runs.values()
            ):
                raise ActiveRunConflict("该对话已有正在执行的任务")
            now = utc_now()
            run = WorkflowRun(
                id=run_id,
                task_id=(
                    self._stable_id("task", request_key) if request_key else self._id("task")
                ),
                conversation_id=conversation_id,
                owner_user_id=actor_user_id,
                workflow_id=workflow_id,
                workflow_version=workflow_version,
                input_data=dict(input_data),
                status=RunStatus.QUEUED,
                created_at=now,
                updated_at=now,
                session_digest=session_digest,
                run_kind="INITIAL",
                checkpoint_namespace=run_id,
            )
            self._runs[run.id] = run
            return run

    async def create_reanalysis_run(
        self,
        conversation_id: str,
        actor_user_id: str,
        *,
        scope_selectors: list[dict[str, Any]],
        session_digest: str,
        request_key: str | None = None,
    ) -> WorkflowRun:
        await self.get_conversation(conversation_id, actor_user_id)
        async with self._lock:
            run_id = self._stable_id("run", request_key) if request_key else self._id("run")
            existing = self._runs.get(run_id)
            if existing is not None:
                if (
                    existing.conversation_id != conversation_id
                    or existing.owner_user_id != actor_user_id
                ):
                    raise ActiveRunConflict("幂等运行范围不匹配")
                return existing
            conversation_runs = [
                value for value in self._runs.values() if value.conversation_id == conversation_id
            ]
            if any(value.status.active for value in conversation_runs):
                raise ActiveRunConflict("该对话已有正在执行的任务")
            if not conversation_runs:
                raise KeyError("没有可重分析的历史任务")
            latest = max(conversation_runs, key=lambda value: value.created_at)
            initial = min(
                (value for value in conversation_runs if value.task_id == latest.task_id),
                key=lambda value: value.created_at,
            )
            lease = self._workspace_leases.get(initial.task_id)
            if not self._workspace_reusable(lease):
                raise WorkspaceNotReusable("历史源码工作区已过期，请创建新的分析任务")
            now = utc_now()
            input_data = dict(initial.input_data)
            input_data["scopeSelectors"] = list(scope_selectors)
            run = WorkflowRun(
                id=run_id,
                task_id=initial.task_id,
                conversation_id=conversation_id,
                owner_user_id=actor_user_id,
                workflow_id=initial.workflow_id,
                workflow_version=initial.workflow_version,
                input_data=input_data,
                status=RunStatus.QUEUED,
                created_at=now,
                updated_at=now,
                session_digest=session_digest,
                run_kind="REANALYSIS",
                checkpoint_namespace=run_id,
            )
            self._runs[run.id] = run
            return run

    async def resume_waiting_run(
        self,
        conversation_id: str,
        actor_user_id: str,
        *,
        input_patch: dict[str, Any],
        session_digest: str,
        request_key: str | None = None,
    ) -> WorkflowRun:
        await self.get_conversation(conversation_id, actor_user_id)
        async with self._lock:
            checkpoint_namespace = (
                self._stable_id("input", request_key) if request_key else None
            )
            if checkpoint_namespace is not None:
                replay = next(
                    (
                        value
                        for value in self._runs.values()
                        if value.conversation_id == conversation_id
                        and value.checkpoint_namespace == checkpoint_namespace
                    ),
                    None,
                )
                if replay is not None:
                    return replay
            waiting = next(
                (
                    value
                    for value in self._runs.values()
                    if value.conversation_id == conversation_id
                    and value.status is RunStatus.WAITING_INPUT
                ),
                None,
            )
            if waiting is None:
                raise KeyError("没有等待补充输入的运行")
            input_data = dict(waiting.input_data)
            input_data.update(input_patch)
            updated = WorkflowRun(
                id=waiting.id,
                task_id=waiting.task_id,
                conversation_id=waiting.conversation_id,
                owner_user_id=waiting.owner_user_id,
                workflow_id=waiting.workflow_id,
                workflow_version=waiting.workflow_version,
                input_data=input_data,
                status=RunStatus.QUEUED,
                created_at=waiting.created_at,
                updated_at=utc_now(),
                session_digest=session_digest,
                run_kind=waiting.run_kind,
                checkpoint_namespace=(
                    checkpoint_namespace or f"{waiting.id}-input-{uuid4().hex[:12]}"
                ),
            )
            self._runs[waiting.id] = updated
            return updated

    async def get_run(
        self,
        run_id: str,
        actor_user_id: str,
        *,
        is_super_admin: bool = False,
    ) -> WorkflowRun:
        run = self._runs.get(run_id)
        if run is None:
            raise KeyError("运行不存在")
        if run.owner_user_id != actor_user_id and not is_super_admin:
            raise PermissionError("无权访问该运行")
        return run

    async def get_latest_run_for_conversation(
        self,
        conversation_id: str,
        actor_user_id: str,
        *,
        is_super_admin: bool = False,
    ) -> WorkflowRun:
        await self.get_conversation(
            conversation_id,
            actor_user_id,
            is_super_admin=is_super_admin,
        )
        values = [
            value for value in self._runs.values() if value.conversation_id == conversation_id
        ]
        if not values:
            raise KeyError("运行不存在")
        return max(values, key=lambda value: value.created_at)

    async def request_cancel(self, run_id: str, actor_user_id: str) -> WorkflowRun:
        async with self._lock:
            run = self._runs.get(run_id)
            if run is None:
                raise KeyError("运行不存在")
            if run.owner_user_id != actor_user_id:
                raise PermissionError("无权访问该运行")
            if run.status is RunStatus.CANCELED:
                return run
            if not run.status.active:
                raise RunNotCancelable("运行已经结束，不能取消")
            updated = self._with_run_status(run, RunStatus.CANCELED)
            self._runs[run_id] = updated
            self._run_workers.pop(run_id, None)
            return updated

    async def claim_next_run(
        self,
        worker_id: str,
        *,
        lease_seconds: int,
    ) -> WorkflowRun | None:
        del lease_seconds
        async with self._lock:
            queued = sorted(
                (value for value in self._runs.values() if value.status is RunStatus.QUEUED),
                key=lambda value: value.created_at,
            )
            if not queued:
                return None
            run = queued[0]
            claimed = WorkflowRun(
                id=run.id,
                task_id=run.task_id,
                conversation_id=run.conversation_id,
                owner_user_id=run.owner_user_id,
                workflow_id=run.workflow_id,
                workflow_version=run.workflow_version,
                input_data=run.input_data,
                status=RunStatus.RUNNING,
                created_at=run.created_at,
                updated_at=utc_now(),
                session_digest=run.session_digest,
                run_kind=run.run_kind,
                checkpoint_namespace=run.checkpoint_namespace,
            )
            self._runs[run.id] = claimed
            self._run_workers[run.id] = worker_id
            return claimed

    async def renew_run_lease(
        self,
        run_id: str,
        worker_id: str,
        *,
        lease_seconds: int,
    ) -> bool:
        del lease_seconds
        return (
            run_id in self._runs
            and self._runs[run_id].status is RunStatus.RUNNING
            and self._run_workers.get(run_id) == worker_id
        )

    async def transition_claimed_run(
        self,
        run_id: str,
        worker_id: str,
        status: RunStatus,
    ) -> WorkflowRun | None:
        """仅当前租约owner可把RUNNING原子收敛到等待输入或终态。"""

        if status not in {
            RunStatus.WAITING_INPUT,
            RunStatus.SUCCEEDED,
            RunStatus.PARTIAL_FAILED,
            RunStatus.FAILED,
        }:
            raise ValueError("Worker目标状态无效")
        async with self._lock:
            run = self._runs.get(run_id)
            if (
                run is None
                or run.status is not RunStatus.RUNNING
                or self._run_workers.get(run_id) != worker_id
            ):
                return None
            updated = self._with_run_status(run, status)
            self._runs[run_id] = updated
            self._run_workers.pop(run_id, None)
            return updated

    async def find_submission(
        self,
        conversation_id: str,
        client_request_id: str,
    ) -> SubmissionResult | None:
        return self._submissions.get((conversation_id, client_request_id))

    async def save_submission(
        self,
        conversation_id: str,
        client_request_id: str,
        result: SubmissionResult,
    ) -> SubmissionResult:
        async with self._lock:
            return self._submissions.setdefault((conversation_id, client_request_id), result)

    async def update_run_status(self, run_id: str, status: RunStatus) -> WorkflowRun:
        async with self._lock:
            run = self._runs[run_id]
            updated = self._with_run_status(run, status)
            self._runs[run_id] = updated
            if status is not RunStatus.RUNNING:
                self._run_workers.pop(run_id, None)
            return updated

    @staticmethod
    def _with_run_status(run: WorkflowRun, status: RunStatus) -> WorkflowRun:
        return WorkflowRun(
            id=run.id,
            task_id=run.task_id,
            conversation_id=run.conversation_id,
            owner_user_id=run.owner_user_id,
            workflow_id=run.workflow_id,
            workflow_version=run.workflow_version,
            input_data=run.input_data,
            status=status,
            created_at=run.created_at,
            updated_at=utc_now(),
            session_digest=run.session_digest,
            run_kind=run.run_kind,
            checkpoint_namespace=run.checkpoint_namespace,
        )

    async def append_event(
        self,
        conversation_id: str,
        event_type: AgUiEventType,
        payload: dict[str, Any],
        *,
        dedup_key: str | None = None,
    ) -> AgUiEvent:
        async with self._lock:
            if dedup_key is not None:
                if not dedup_key or len(dedup_key) > 255:
                    raise ValueError("durable event dedup_key长度必须为1到255")
                existing = self._event_dedup.get((conversation_id, dedup_key))
                if existing is not None:
                    return existing
            events = self._events[conversation_id]
            event = AgUiEvent(
                conversation_id=conversation_id,
                type=event_type,
                payload=dict(payload),
                seq=len(events) + 1,
            )
            events.append(event)
            if dedup_key is not None:
                self._event_dedup[(conversation_id, dedup_key)] = event
            return event

    async def list_events(self, conversation_id: str, *, after_seq: int = 0) -> list[AgUiEvent]:
        return [event for event in self._events[conversation_id] if (event.seq or 0) > after_seq]

    async def publish_report(
        self,
        task_id: str,
        run_id: str,
        owner_user_id: str,
        structured_report: dict[str, Any],
        markdown_report: str,
    ) -> ReportVersion:
        run = self._runs.get(run_id)
        if run is None or run.task_id != task_id or run.owner_user_id != owner_user_id:
            raise KeyError("任务不存在")
        versions = self._reports.setdefault(task_id, [])
        existing_for_run = next((value for value in versions if value.run_id == run_id), None)
        if existing_for_run is not None:
            return existing_for_run
        for index, existing in enumerate(versions):
            versions[index] = ReportVersion(
                existing.id,
                existing.task_id,
                existing.run_id,
                existing.version,
                False,
                existing.structured_report,
                existing.markdown_report,
                existing.created_at,
            )
        report = ReportVersion(
            id=self._id("report"),
            task_id=task_id,
            run_id=run_id,
            version=len(versions) + 1,
            is_current=True,
            structured_report=dict(structured_report),
            markdown_report=markdown_report,
            created_at=utc_now(),
        )
        versions.append(report)
        return report

    async def list_reports(
        self,
        task_id: str,
        actor_user_id: str,
        *,
        is_super_admin: bool = False,
    ) -> list[ReportVersion]:
        run = next((value for value in self._runs.values() if value.task_id == task_id), None)
        if run is None:
            raise KeyError("任务不存在")
        if run.owner_user_id != actor_user_id and not is_super_admin:
            raise PermissionError("无权访问该报告")
        return list(self._reports.get(task_id, ()))

    async def get_report(
        self,
        report_id: str,
        actor_user_id: str,
        *,
        is_super_admin: bool = False,
    ) -> ReportVersion:
        for task_id, versions in self._reports.items():
            report = next((value for value in versions if value.id == report_id), None)
            if report is not None:
                await self.list_reports(task_id, actor_user_id, is_super_admin=is_super_admin)
                return report
        raise KeyError("报告不存在")

    async def get_current_report(
        self,
        task_id: str,
        actor_user_id: str,
        *,
        is_super_admin: bool = False,
    ) -> ReportVersion:
        versions = await self.list_reports(
            task_id,
            actor_user_id,
            is_super_admin=is_super_admin,
        )
        try:
            return next(value for value in versions if value.is_current)
        except StopIteration as exception:
            raise KeyError("当前报告不存在") from exception

    async def get_current_report_for_conversation(
        self,
        conversation_id: str,
        actor_user_id: str,
        *,
        is_super_admin: bool = False,
    ) -> ReportVersion:
        await self.get_conversation(
            conversation_id,
            actor_user_id,
            is_super_admin=is_super_admin,
        )
        conversation_runs = sorted(
            (value for value in self._runs.values() if value.conversation_id == conversation_id),
            key=lambda value: value.created_at,
            reverse=True,
        )
        for run in conversation_runs:
            current = next(
                (value for value in self._reports.get(run.task_id, ()) if value.is_current),
                None,
            )
            if current is not None:
                return current
        raise KeyError("当前报告不存在")

    async def save_task_repositories(self, task_id: str, values: list[Any]) -> None:
        async with self._lock:
            existing = self._task_repositories.get(task_id)
            if existing is not None:
                old_coordinates = [
                    (value.repository_id, value.default_head, value.target_head, value.merge_base)
                    for value in existing
                ]
                new_coordinates = [
                    (value.repository_id, value.default_head, value.target_head, value.merge_base)
                    for value in values
                ]
                if old_coordinates != new_coordinates:
                    raise RuntimeError("冻结仓库坐标不可变")
                return
            self._task_repositories[task_id] = list(values)

    async def save_analyzer_results(self, run_id: str, values: list[Any]) -> None:
        async with self._lock:
            target = self._analyzer_results.setdefault(run_id, {})
            for value in values:
                payload = value.to_dict()
                target.setdefault(str(payload["analyzerId"]), payload)

    async def list_analyzer_results(self, run_id: str) -> list[dict[str, Any]]:
        values = self._analyzer_results.get(run_id, {})
        return [dict(values[key]) for key in sorted(values)]

    async def get_node_operation_result(
        self,
        run_id: str,
        node_id: str,
        operation_key: str,
    ) -> dict[str, Any] | None:
        self._validate_operation_scope(run_id, node_id, operation_key)
        value = self._node_operation_results.get((run_id, node_id, operation_key))
        return dict(value) if value is not None else None

    async def save_node_operation_result(
        self,
        run_id: str,
        node_id: str,
        operation_key: str,
        result: dict[str, Any],
    ) -> dict[str, Any]:
        self._validate_operation_scope(run_id, node_id, operation_key)
        if run_id not in self._runs:
            raise KeyError("运行不存在")
        async with self._lock:
            stored = self._node_operation_results.setdefault(
                (run_id, node_id, operation_key),
                dict(result),
            )
        return dict(stored)

    async def upsert_workspace_lease(
        self,
        task_id: str,
        *,
        runner_id: str,
        container_id: str | None,
        image_digest: str | None,
        status: str,
        expires_at: datetime | None,
        metadata: dict[str, Any],
    ) -> None:
        async with self._lock:
            self._workspace_leases[task_id] = {
                "taskId": task_id,
                "runnerId": runner_id,
                "containerId": container_id,
                "imageDigest": image_digest,
                "status": status,
                "expiresAt": expires_at,
                "lastActivityAt": utc_now(),
                "metadata": dict(metadata),
            }

    async def get_workspace_lease(self, task_id: str) -> dict[str, Any]:
        try:
            value = dict(self._workspace_leases[task_id])
        except KeyError as exception:
            raise KeyError("工作区租约不存在") from exception
        if value.get("status") != "CLEANUP_FAILED" and self._workspace_expired(value):
            value["status"] = "EXPIRED"
        return value

    async def list_workspace_leases_due_for_cleanup(
        self,
        *,
        limit: int = 100,
    ) -> list[dict[str, Any]]:
        if not 1 <= limit <= 1000:
            raise ValueError("工作区清理批次必须位于1到1000之间")
        async with self._lock:
            values = [
                dict(value)
                for value in self._workspace_leases.values()
                if value.get("status") in {"STOPPED_RETAINED", "CLEANUP_FAILED"}
                and isinstance(value.get("expiresAt"), datetime)
                and value["expiresAt"] <= utc_now()
            ]
        values.sort(key=lambda value: value["expiresAt"])
        return values[:limit]

    async def set_workspace_cleanup_status(self, task_id: str, status: str) -> None:
        if status not in {"EXPIRED", "CLEANUP_FAILED"}:
            raise ValueError("工作区清理状态无效")
        async with self._lock:
            if task_id not in self._workspace_leases:
                raise KeyError("工作区租约不存在")
            lease = dict(self._workspace_leases[task_id])
            lease["status"] = status
            lease["lastActivityAt"] = utc_now()
            if status == "CLEANUP_FAILED":
                # 主动取消可能发生在ACTIVE且无过期时间的工作区；立即进入持久重试队列。
                lease["expiresAt"] = utc_now()
            if status == "EXPIRED":
                lease["containerId"] = None
            self._workspace_leases[task_id] = lease

    async def append_audit_log(
        self,
        *,
        actor_user_id: str,
        action: str,
        resource_type: str,
        resource_id: str,
        trace_id: str,
        details: dict[str, Any],
    ) -> None:
        async with self._lock:
            self._audit_logs.append(
                {
                    "actorUserId": actor_user_id,
                    "action": action,
                    "resourceType": resource_type,
                    "resourceId": resource_id,
                    "traceId": trace_id,
                    "details": dict(details),
                    "createdAt": utc_now().isoformat(),
                }
            )

    async def list_audit_logs(self) -> list[dict[str, Any]]:
        return [dict(value) for value in self._audit_logs]

    @staticmethod
    def _workspace_expired(lease: dict[str, Any]) -> bool:
        expires_at = lease.get("expiresAt")
        return (
            str(lease.get("status")) in {"EXPIRED", "CLEANUP_FAILED"}
            or (isinstance(expires_at, datetime) and expires_at <= utc_now())
        )

    @classmethod
    def _workspace_reusable(cls, lease: dict[str, Any] | None) -> bool:
        return bool(
            lease
            and lease.get("status") == "STOPPED_RETAINED"
            and isinstance(lease.get("expiresAt"), datetime)
            and not cls._workspace_expired(lease)
        )

    @staticmethod
    def _id(prefix: str) -> str:
        return f"{prefix}_{uuid4().hex}"

    @staticmethod
    def _stable_id(prefix: str, request_key: str) -> str:
        return f"{prefix}_{hashlib.sha256(request_key.encode()).hexdigest()[:32]}"

    @staticmethod
    def _validate_operation_scope(run_id: str, node_id: str, operation_key: str) -> None:
        if not node_id or len(node_id) > 128:
            raise ValueError("node_id长度必须为1到128")
        if len(operation_key) > 255 or not operation_key.startswith(f"{run_id}:{node_id}:"):
            raise ValueError("operation_key未绑定当前run和node")
