from __future__ import annotations

from dataclasses import replace
from datetime import UTC, datetime, timedelta
from types import SimpleNamespace

import pytest

from testagent_workflow.models import RunStatus
from testagent_workflow.impact_engine import AnalyzerOutcome
from testagent_workflow.registry import WorkflowDefinition, WorkflowRegistry
from testagent_workflow.store import InMemoryWorkflowStore
from testagent_workflow.worker import WorkflowWorker, WorkspaceCleanupService


class FakeGraph:
    def __init__(self, result: dict | None = None, error: Exception | None = None) -> None:
        self.result = result
        self.error = error
        self.config = None

    async def ainvoke(self, state, config):  # type: ignore[no-untyped-def]
        self.config = config
        if self.error:
            raise self.error
        return {**state, **(self.result or {})}


@pytest.mark.asyncio
async def test_each_worker_process_uses_a_distinct_lease_owner_token() -> None:
    class ClaimRecordingStore:
        def __init__(self) -> None:
            self.worker_ids: list[str] = []

        async def claim_next_run(self, worker_id, *, lease_seconds):  # type: ignore[no-untyped-def]
            del lease_seconds
            self.worker_ids.append(worker_id)
            return None

    store = ClaimRecordingStore()
    registry = WorkflowRegistry([])
    first = WorkflowWorker("worker-a", store, registry, lambda run: None)  # type: ignore[arg-type]
    second = WorkflowWorker("worker-a", store, registry, lambda run: None)  # type: ignore[arg-type]

    assert await first.run_once() is False
    assert await second.run_once() is False
    assert len(set(store.worker_ids)) == 2
    assert all(value.startswith("worker-a:") for value in store.worker_ids)
    assert all(len(value) <= 128 for value in store.worker_ids)


def definition(graph: FakeGraph) -> WorkflowDefinition:
    return WorkflowDefinition(
        id="code-change-impact-analysis",
        version="1.0.0",
        display_name="影响分析",
        intent_examples=(),
        required_permissions=(),
        risk_level="READ_ONLY",
        capabilities=(),
        requires_sandbox=True,
        input_schema={},
        ui_schema={},
        output_schema={},
        graph_factory=lambda dependencies, checkpointer=None: graph,
    )


@pytest.mark.asyncio
async def test_worker_claims_run_with_task_thread_and_run_checkpoint_namespace() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "worker")
    queued = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={"analyzerIds": ["codex"]},
        session_digest="a" * 64,
    )
    graph = FakeGraph({"final_status": "PARTIAL_FAILED"})
    worker = WorkflowWorker(
        "worker-a",
        store,
        WorkflowRegistry([definition(graph)]),
        dependency_factory=lambda run: object(),
        checkpointer=object(),
    )

    processed = await worker.run_once()

    assert processed is True
    finished = await store.get_run(queued.id, "usr_owner")
    assert finished.status is RunStatus.PARTIAL_FAILED
    assert graph.config == {
        "configurable": {"thread_id": queued.task_id, "checkpoint_ns": queued.id}
    }


@pytest.mark.asyncio
async def test_worker_records_safe_failure_without_losing_lease_recovery() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "worker")
    queued = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={},
    )
    graph = FakeGraph(error=RuntimeError("provider token must never leak"))
    worker = WorkflowWorker(
        "worker-a",
        store,
        WorkflowRegistry([definition(graph)]),
        dependency_factory=lambda run: object(),
    )

    assert await worker.run_once() is True
    assert (await store.get_run(queued.id, "usr_owner")).status is RunStatus.FAILED
    events = await store.list_events(conversation.id)
    assert events[-1].payload["code"] == "WORKFLOW_EXECUTION_FAILED"
    assert "provider token" not in str(events[-1].payload)


@pytest.mark.asyncio
async def test_worker_failure_revokes_grants_and_retains_any_created_workspace() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "failure cleanup")
    queued = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={},
    )

    class CleanupRunner:
        def __init__(self) -> None:
            self.calls = []

        async def stop_and_retain(self, state, operation_key):  # type: ignore[no-untyped-def]
            self.calls.append((state["run_id"], operation_key))

    cleanup = CleanupRunner()
    worker = WorkflowWorker(
        "worker-a",
        store,
        WorkflowRegistry([definition(FakeGraph(error=RuntimeError("failed")))]),
        dependency_factory=lambda run: SimpleNamespace(runner=cleanup),
    )

    await worker.run_once()

    assert cleanup.calls == [
        (queued.id, f"{queued.id}:workspace:failure-retain"),
    ]


@pytest.mark.asyncio
async def test_worker_persists_each_analyzer_outcome_before_finishing_run() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "worker")
    queued = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={"analyzerIds": ["codex", "opencode"]},
    )
    outcomes = [
        AnalyzerOutcome("codex", True, {"summary": "ok"}, None),
        AnalyzerOutcome("opencode", False, None, "MODEL_TIMEOUT"),
    ]
    worker = WorkflowWorker(
        "worker-a",
        store,
        WorkflowRegistry([definition(FakeGraph({"final_status": "PARTIAL_FAILED", "analyzer_outcomes": outcomes}))]),
        dependency_factory=lambda run: object(),
    )

    await worker.run_once()

    saved = await store.list_analyzer_results(queued.id)
    assert [value["analyzerId"] for value in saved] == ["codex", "opencode"]
    assert saved[1]["errorCode"] == "MODEL_TIMEOUT"


@pytest.mark.asyncio
async def test_worker_emits_scope_input_request_and_keeps_run_waiting() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "scope")
    queued = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={},
    )
    required = [{"reason": "AMBIGUOUS", "candidatePaths": ["a.py", "b.py"]}]
    worker = WorkflowWorker(
        "worker-a",
        store,
        WorkflowRegistry([
            definition(FakeGraph({"final_status": "WAITING_INPUT", "required_scope_input": required}))
        ]),
        dependency_factory=lambda run: object(),
    )

    await worker.run_once()

    assert (await store.get_run(queued.id, "usr_owner")).status is RunStatus.WAITING_INPUT
    events = await store.list_events(conversation.id)
    assert events[-1].type.value == "CUSTOM"
    assert events[-1].payload["name"] == "workflow.input_required"
    assert events[-1].payload["value"]["scope"] == required


@pytest.mark.asyncio
async def test_worker_emits_baseline_selection_with_original_run_input() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "基线选择")
    queued = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={
            "repositories": [
                {"repositoryId": "repo_12345678", "targetBranch": "feature-a"}
            ],
            "mode": "SINGLE",
            "analyzerIds": ["codex"],
        },
    )
    required = [
        {"repositoryId": "repo_12345678", "availableBranches": ["main", "develop"]}
    ]
    worker = WorkflowWorker(
        "worker-a",
        store,
        WorkflowRegistry([
            definition(FakeGraph({
                "final_status": "WAITING_INPUT",
                "required_baseline_input": required,
            }))
        ]),
        dependency_factory=lambda run: object(),
    )

    await worker.run_once()

    event = (await store.list_events(conversation.id))[-1]
    assert event.payload["value"]["kind"] == "BASELINE_SELECTION"
    assert event.payload["value"]["baselines"] == required
    assert event.payload["value"]["currentInput"] == queued.input_data


@pytest.mark.asyncio
async def test_worker_that_loses_its_lease_does_not_overwrite_the_new_owner() -> None:
    class LeaseLosingStore(InMemoryWorkflowStore):
        async def renew_run_lease(self, run_id, worker_id, *, lease_seconds):  # type: ignore[no-untyped-def]
            del run_id, worker_id, lease_seconds
            return False

    store = LeaseLosingStore()
    conversation = await store.create_conversation("usr_owner", "租约接管")
    queued = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={},
    )

    class LeaseLossCleanupRunner:
        def __init__(self) -> None:
            self.revoke_calls = 0
            self.retain_calls = 0

        async def revoke_model_access(self) -> None:
            self.revoke_calls += 1

        async def stop_and_retain(self, state, operation_key):  # type: ignore[no-untyped-def]
            del state, operation_key
            self.retain_calls += 1

    cleanup = LeaseLossCleanupRunner()
    worker = WorkflowWorker(
        "worker-old",
        store,
        WorkflowRegistry([definition(FakeGraph({"final_status": "SUCCEEDED"}))]),
        dependency_factory=lambda run: SimpleNamespace(runner=cleanup),
    )

    assert await worker.run_once() is True
    assert (await store.get_run(queued.id, "usr_owner")).status is RunStatus.RUNNING
    events = await store.list_events(conversation.id)
    assert all(event.type.value != "RUN_ERROR" for event in events)
    assert all(event.type.value != "RUN_FINISHED" for event in events)
    assert cleanup.revoke_calls == 1
    assert cleanup.retain_calls == 0


@pytest.mark.asyncio
async def test_cancel_wins_when_it_races_with_worker_terminal_transition() -> None:
    class CancelBeforeTransitionStore(InMemoryWorkflowStore):
        async def transition_claimed_run(  # type: ignore[no-untyped-def]
            self,
            run_id,
            worker_id,
            status,
        ):
            await self.request_cancel(run_id, "usr_owner")
            return await super().transition_claimed_run(run_id, worker_id, status)

    store = CancelBeforeTransitionStore()
    conversation = await store.create_conversation("usr_owner", "取消终态竞态")
    queued = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={},
    )
    worker = WorkflowWorker(
        "worker-racing",
        store,
        WorkflowRegistry([definition(FakeGraph({"final_status": "SUCCEEDED"}))]),
        dependency_factory=lambda run: object(),
    )

    assert await worker.run_once() is True
    assert (await store.get_run(queued.id, "usr_owner")).status is RunStatus.CANCELED
    events = await store.list_events(conversation.id)
    assert all(
        event.payload.get("status") != RunStatus.SUCCEEDED.value
        for event in events
        if event.type.value == "RUN_FINISHED"
    )


@pytest.mark.asyncio
async def test_graph_failure_after_lease_loss_is_fenced_from_shared_state_and_workspace() -> None:
    class LeaseLosingStore(InMemoryWorkflowStore):
        async def renew_run_lease(self, run_id, worker_id, *, lease_seconds):  # type: ignore[no-untyped-def]
            del run_id, worker_id, lease_seconds
            return False

    store = LeaseLosingStore()
    conversation = await store.create_conversation("usr_owner", "异常接管")
    queued = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={},
    )

    class LeaseLossCleanupRunner:
        def __init__(self) -> None:
            self.revoke_calls = 0
            self.retain_calls = 0

        async def revoke_model_access(self) -> None:
            self.revoke_calls += 1

        async def stop_and_retain(self, state, operation_key):  # type: ignore[no-untyped-def]
            del state, operation_key
            self.retain_calls += 1

    cleanup = LeaseLossCleanupRunner()
    worker = WorkflowWorker(
        "worker-old",
        store,
        WorkflowRegistry([definition(FakeGraph(error=RuntimeError("node failed")))]),
        dependency_factory=lambda run: SimpleNamespace(runner=cleanup),
    )

    assert await worker.run_once() is True
    assert (await store.get_run(queued.id, "usr_owner")).status is RunStatus.RUNNING
    events = await store.list_events(conversation.id)
    assert all(event.type.value != "RUN_ERROR" for event in events)
    assert cleanup.revoke_calls == 1
    assert cleanup.retain_calls == 0


@pytest.mark.asyncio
async def test_workspace_cleanup_records_failure_then_converges_to_expired() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "工作区清理")
    run = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={},
    )
    await store.upsert_workspace_lease(
        run.task_id,
        runner_id="runner-a",
        container_id="container-a",
        image_digest="analysis@sha256:" + "a" * 64,
        status="STOPPED_RETAINED",
        expires_at=datetime.now(UTC) - timedelta(seconds=1),
        metadata={"runId": run.id},
    )

    class FlakyRunner:
        def __init__(self) -> None:
            self.calls = 0

        async def cleanup(self, task_id, run_id):  # type: ignore[no-untyped-def]
            assert (task_id, run_id) == (run.task_id, run.id)
            self.calls += 1
            if self.calls == 1:
                raise RuntimeError("runner unavailable")

    janitor = WorkspaceCleanupService(store, FlakyRunner())

    assert await janitor.run_once() == 0
    assert (await store.get_workspace_lease(run.task_id))["status"] == "CLEANUP_FAILED"
    assert await janitor.run_once() == 1
    lease = await store.get_workspace_lease(run.task_id)
    assert lease["status"] == "EXPIRED"
    assert lease["containerId"] is None
