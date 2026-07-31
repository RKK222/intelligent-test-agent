"""基于PostgreSQL租约和LangGraph checkpoint的可恢复Worker。"""

from __future__ import annotations

import asyncio
from collections.abc import Callable
from contextlib import suppress
import logging
from typing import Any
from uuid import uuid4

from testagent_workflow.agui import AgUiEventType
from testagent_workflow.models import RunStatus
from testagent_workflow.registry import WorkflowRegistry
from testagent_workflow.store import WorkflowRun


class RunLeaseLost(RuntimeError):
    """当前Worker不再拥有run；旧执行者必须停止写入终态或操作工作区。"""


LOGGER = logging.getLogger(__name__)


class WorkspaceCleanupService:
    """由控制面数据库驱动过期清理，并把Runner结果收敛回持久工作区状态。"""

    def __init__(self, store: Any, runner: Any, *, batch_size: int = 100) -> None:
        self._store = store
        self._runner = runner
        self._batch_size = batch_size

    async def run_once(self) -> int:
        cleaned = 0
        leases = await self._store.list_workspace_leases_due_for_cleanup(
            limit=self._batch_size
        )
        for lease in leases:
            task_id = str(lease["taskId"])
            metadata = lease.get("metadata")
            run_id = str(metadata.get("runId", "")) if isinstance(metadata, dict) else ""
            try:
                if not run_id:
                    raise RuntimeError("工作区租约缺少runId")
                await self._runner.cleanup(task_id, run_id)
            except asyncio.CancelledError:
                raise
            except Exception:
                # Runner错误正文可能包含内部地址；持久状态只记录稳定枚举。
                await self._store.set_workspace_cleanup_status(task_id, "CLEANUP_FAILED")
                continue
            await self._store.set_workspace_cleanup_status(task_id, "EXPIRED")
            cleaned += 1
        return cleaned

    async def run_forever(self, *, interval_seconds: float = 60.0) -> None:
        while True:
            try:
                await self.run_once()
            except asyncio.CancelledError:
                raise
            except Exception:
                LOGGER.warning("工作区清理轮询失败，将在下一周期重试")
            await asyncio.sleep(interval_seconds)


class WorkflowWorker:
    def __init__(
        self,
        worker_id: str,
        store: Any,
        registry: WorkflowRegistry,
        dependency_factory: Callable[[WorkflowRun], Any],
        *,
        checkpointer: Any = None,
        lease_seconds: int = 60,
    ) -> None:
        # 配置值只标识逻辑实例；每个进程追加随机owner token，防止重启后的新进程
        # 与尚未退出的旧进程共享worker_id并错误续租同一run。
        self._worker_id = f"{worker_id[:80]}:{uuid4().hex}"
        self._store = store
        self._registry = registry
        self._dependency_factory = dependency_factory
        self._checkpointer = checkpointer
        self._lease_seconds = lease_seconds

    async def run_once(self) -> bool:
        run = await self._store.claim_next_run(
            self._worker_id,
            lease_seconds=self._lease_seconds,
        )
        if run is None:
            return False
        heartbeat = asyncio.create_task(self._heartbeat(run.id))
        graph_dependencies: Any | None = None
        try:
            await self._store.append_event(
                run.conversation_id,
                AgUiEventType.RUN_STARTED,
                {
                    "runId": run.id,
                    "taskId": run.task_id,
                    "status": "RUNNING",
                    "runKind": run.run_kind,
                },
            )
            await self._store.append_event(
                run.conversation_id,
                AgUiEventType.CUSTOM,
                {
                    "name": "workflow.worker_claimed",
                    "value": {"runId": run.id, "taskId": run.task_id},
                },
            )
            definition = self._registry.get(run.workflow_id)
            graph_dependencies = self._dependency_factory(run)
            graph = definition.graph_factory(
                graph_dependencies,
                checkpointer=self._checkpointer,
            )
            graph_task = asyncio.create_task(
                graph.ainvoke(
                    {
                        "task_id": run.task_id,
                        "run_id": run.id,
                        "conversation_id": run.conversation_id,
                        "owner_user_id": run.owner_user_id,
                        "session_digest": run.session_digest,
                        "run_kind": run.run_kind,
                        "input_data": run.input_data,
                    },
                    {
                        "configurable": {
                            "thread_id": run.task_id,
                            "checkpoint_ns": run.checkpoint_namespace or run.id,
                        }
                    },
                )
            )
            completed, _ = await asyncio.wait(
                {graph_task, heartbeat},
                return_when=asyncio.FIRST_COMPLETED,
            )
            if heartbeat in completed:
                graph_task.cancel()
                with suppress(asyncio.CancelledError):
                    await graph_task
                await heartbeat
                raise RunLeaseLost("run租约已丢失")
            result = await graph_task
            await self._assert_lease(run.id)
            analyzer_outcomes = list(result.get("analyzer_outcomes", ()))
            if analyzer_outcomes:
                await self._store.save_analyzer_results(run.id, analyzer_outcomes)
            final_status = RunStatus(str(result.get("final_status", "FAILED")))
            transitioned = await self._store.transition_claimed_run(
                run.id,
                self._worker_id,
                final_status,
            )
            if transitioned is None:
                current = await self._store.get_run(run.id, run.owner_user_id)
                if current.status is RunStatus.CANCELED:
                    return True
                raise RunLeaseLost("run终态写入时租约已丢失")
            if final_status is RunStatus.WAITING_INPUT:
                baseline_input = list(result.get("required_baseline_input", []))
                scope_input = list(result.get("required_scope_input", []))
                await self._store.append_event(
                    run.conversation_id,
                    AgUiEventType.CUSTOM,
                    {
                        "name": "workflow.input_required",
                        "value": {
                            "runId": run.id,
                            "taskId": run.task_id,
                            "kind": (
                                "BASELINE_SELECTION" if baseline_input else "SCOPE_DISAMBIGUATION"
                            ),
                            "baselines": baseline_input,
                            "scope": scope_input,
                            "currentInput": result.get("input_data", run.input_data),
                        },
                    },
                )
                return True
            await self._store.append_event(
                run.conversation_id,
                AgUiEventType.RUN_FINISHED,
                {"runId": run.id, "taskId": run.task_id, "status": final_status.value},
            )
        except RunLeaseLost:
            # 新Worker可能已经接管；旧Worker不得覆盖状态、发布失败事件或停止共享任务容器。
            await self._revoke_abandoned_model_access(graph_dependencies)
        except Exception:
            try:
                # 失败收敛同样是共享副作用；先续租形成短时栅栏，避免旧Worker停掉接管者容器。
                await self._assert_lease(run.id)
            except RunLeaseLost:
                await self._revoke_abandoned_model_access(graph_dependencies)
            else:
                current = await self._store.get_run(run.id, run.owner_user_id)
                if current.status is not RunStatus.CANCELED:
                    cleanup_runner = getattr(graph_dependencies, "runner", None)
                    if cleanup_runner is not None:
                        try:
                            await cleanup_runner.stop_and_retain(
                                {
                                    "task_id": run.task_id,
                                    "run_id": run.id,
                                    "conversation_id": run.conversation_id,
                                    "owner_user_id": run.owner_user_id,
                                    "session_digest": run.session_digest,
                                    "run_kind": run.run_kind,
                                    "input_data": run.input_data,
                                },
                                f"{run.id}:workspace:failure-retain",
                            )
                        except Exception:
                            # Runner拒绝保留通常意味着容器或控制状态已不安全；控制库必须进入可重试清理态。
                            converged, updated = await self._converge_workspace_cleanup_failure(
                                run.task_id
                            )
                            if not converged:
                                # 不终态化run，停止心跳后由租约接管重放幂等图并再次收敛控制状态。
                                return True
                            if updated:
                                with suppress(Exception):
                                    await self._store.append_event(
                                        run.conversation_id,
                                        AgUiEventType.CUSTOM,
                                        {
                                            "name": "workflow.workspace_state",
                                            "value": {
                                                "taskId": run.task_id,
                                                "runId": run.id,
                                                "status": "CLEANUP_FAILED",
                                                "expiresAt": None,
                                            },
                                        },
                                    )
                    transitioned = await self._store.transition_claimed_run(
                        run.id,
                        self._worker_id,
                        RunStatus.FAILED,
                    )
                    if transitioned is None:
                        await self._revoke_abandoned_model_access(graph_dependencies)
                        return True
                    # 异常文本可能包含供应商响应或密钥，只发布固定错误码与trace关联事件。
                    await self._store.append_event(
                        run.conversation_id,
                        AgUiEventType.RUN_ERROR,
                        {
                            "runId": run.id,
                            "taskId": run.task_id,
                            "code": "WORKFLOW_EXECUTION_FAILED",
                            "message": "工作流执行失败，请使用traceId联系管理员",
                        },
                    )
        finally:
            heartbeat.cancel()
            with suppress(asyncio.CancelledError, RunLeaseLost):
                await heartbeat
        return True

    async def _converge_workspace_cleanup_failure(
        self,
        task_id: str,
    ) -> tuple[bool, bool]:
        """短暂重试控制库状态；持续失败时保留RUNNING租约供接管重试。"""

        for attempt in range(3):
            try:
                await self._store.set_workspace_cleanup_status(
                    task_id,
                    "CLEANUP_FAILED",
                )
                return True, True
            except KeyError:
                # QUEUED早期失败时Runner尚未创建工作区，不存在孤立ACTIVE租约。
                return True, False
            except Exception:
                if attempt < 2:
                    await asyncio.sleep(0.05 * (2**attempt))
        LOGGER.warning("工作区清理状态写入失败，保留运行租约等待接管重试")
        return False, False

    async def run_forever(self, *, idle_seconds: float = 1.0) -> None:
        while True:
            if not await self.run_once():
                await asyncio.sleep(idle_seconds)

    async def _heartbeat(self, run_id: str) -> None:
        interval = max(1.0, self._lease_seconds / 3)
        while True:
            await asyncio.sleep(interval)
            await self._assert_lease(run_id)

    async def _assert_lease(self, run_id: str) -> None:
        try:
            renewed = await self._store.renew_run_lease(
                run_id,
                self._worker_id,
                lease_seconds=self._lease_seconds,
            )
        except asyncio.CancelledError:
            raise
        except Exception as exception:
            raise RunLeaseLost("run租约续期失败") from exception
        if not renewed:
            raise RunLeaseLost("run租约已丢失")

    @staticmethod
    async def _revoke_abandoned_model_access(graph_dependencies: Any | None) -> None:
        cleanup_runner = getattr(graph_dependencies, "runner", None)
        revoke_model_access = getattr(cleanup_runner, "revoke_model_access", None)
        if revoke_model_access is not None:
            # 旧Worker自己的grant可安全精确撤销；这里只清理授权，不操作共享工作区。
            with suppress(Exception):
                await revoke_model_access()
