from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator
from datetime import UTC, datetime, timedelta

import pytest
import pytest_asyncio
from sqlalchemy import inspect, text
from sqlalchemy.ext.asyncio import AsyncEngine, create_async_engine
from testcontainers.postgres import PostgresContainer

from testagent_workflow.agui import AgUiEventType
from testagent_workflow.database import PostgresWorkflowStore, migrate_database
from testagent_workflow.impact_engine import AnalyzerOutcome
from testagent_workflow.models import RunStatus
from testagent_workflow.store import ActiveRunConflict, RunNotCancelable, WorkspaceNotReusable


EXPECTED_TABLES = {
    "alembic_version",
    "conversations",
    "messages",
    "tasks",
    "task_repositories",
    "runs",
    "durable_events",
    "node_operation_results",
    "analyzer_results",
    "report_versions",
    "workspace_leases",
    "audit_logs",
    "transactional_outbox",
}


@pytest.fixture(scope="session")
def postgres_url() -> AsyncIterator[str]:
    with PostgresContainer("postgres:17-alpine", driver="psycopg") as postgres:
        yield postgres.get_connection_url()


@pytest_asyncio.fixture
async def engine(postgres_url: str) -> AsyncIterator[AsyncEngine]:
    migrate_database(postgres_url)
    async_url = postgres_url.replace("postgresql+psycopg://", "postgresql+asyncpg://")
    database_engine = create_async_engine(async_url)
    try:
        async with database_engine.begin() as connection:
            await connection.execute(
                text(
                    "TRUNCATE transactional_outbox, audit_logs, workspace_leases, "
                    "report_versions, analyzer_results, node_operation_results, durable_events, runs, "
                    "task_repositories, tasks, messages, conversations CASCADE"
                )
            )
        yield database_engine
    finally:
        await database_engine.dispose()


@pytest.mark.asyncio
async def test_alembic_creates_the_isolated_workflow_schema(engine: AsyncEngine) -> None:
    async with engine.begin() as connection:
        tables = await connection.run_sync(lambda sync: set(inspect(sync).get_table_names()))

    assert EXPECTED_TABLES <= tables


@pytest.mark.asyncio
async def test_database_enforces_idempotency_active_run_and_event_order(
    engine: AsyncEngine,
) -> None:
    store = PostgresWorkflowStore(engine)
    conversation = await store.create_conversation("usr_owner", "代码影响分析")

    first_message = await store.append_message(
        conversation.id,
        "usr_owner",
        role="user",
        content="分析 feature-a",
        client_request_id="req_postgres_123456",
    )
    replay = await store.append_message(
        conversation.id,
        "usr_owner",
        role="user",
        content="不得覆盖",
        client_request_id="req_postgres_123456",
    )
    assert replay.id == first_message.id
    assert replay.content == "分析 feature-a"

    run = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={"repositories": [{"repositoryId": "repo_12345678", "targetBranch": "feature-a"}]},
    )
    with pytest.raises(ActiveRunConflict):
        await store.create_run(
            conversation.id,
            "usr_owner",
            workflow_id="code-change-impact-analysis",
            workflow_version="1.0.0",
            input_data={},
        )

    first_event = await store.append_event(conversation.id, AgUiEventType.RUN_STARTED, {"runId": run.id})
    second_event = await store.append_event(conversation.id, AgUiEventType.CUSTOM, {"name": "progress"})
    assert (first_event.seq, second_event.seq) == (1, 2)

    await store.update_run_status(run.id, RunStatus.SUCCEEDED)
    assert (await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={},
    )).id != run.id


@pytest.mark.asyncio
async def test_concurrent_run_creation_replays_one_deterministic_postgres_run(
    engine: AsyncEngine,
) -> None:
    store = PostgresWorkflowStore(engine)
    conversation = await store.create_conversation("usr_owner", "并发幂等")

    async def create():  # type: ignore[no-untyped-def]
        return await store.create_run(
            conversation.id,
            "usr_owner",
            workflow_id="code-change-impact-analysis",
            workflow_version="1.0.0",
            input_data={"repositories": []},
            request_key="msg_same_postgres_request",
        )

    first, duplicate = await asyncio.gather(create(), create())

    assert duplicate.id == first.id
    assert duplicate.task_id == first.task_id
    async with engine.connect() as connection:
        assert (await connection.execute(text("SELECT count(*) FROM runs"))).scalar_one() == 1
        assert (await connection.execute(text("SELECT count(*) FROM tasks"))).scalar_one() == 1


@pytest.mark.asyncio
async def test_worker_claim_uses_a_database_lease(engine: AsyncEngine) -> None:
    store = PostgresWorkflowStore(engine)
    conversation = await store.create_conversation("usr_worker", "租约测试")
    queued = await store.create_run(
        conversation.id,
        "usr_worker",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={},
    )

    claimed = await store.claim_next_run("worker-a", lease_seconds=30)
    assert claimed is not None
    assert claimed.id == queued.id
    assert claimed.status is RunStatus.RUNNING
    assert await store.claim_next_run("worker-b", lease_seconds=30) is None


@pytest.mark.asyncio
async def test_durable_event_outbox_and_analyzer_results_are_idempotent(
    engine: AsyncEngine,
) -> None:
    store = PostgresWorkflowStore(engine)
    conversation = await store.create_conversation("usr_owner", "结果持久化")
    run = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={},
    )

    await store.append_event(conversation.id, AgUiEventType.CUSTOM, {"name": "progress"})
    outcomes = [
        AnalyzerOutcome("codex", True, {"summary": "ok"}, None),
        AnalyzerOutcome("opencode", False, None, "MODEL_TIMEOUT"),
    ]
    await store.save_analyzer_results(run.id, outcomes)
    await store.save_analyzer_results(run.id, outcomes)

    assert len(await store.list_analyzer_results(run.id)) == 2
    async with engine.connect() as connection:
        outbox = (await connection.execute(
            text("SELECT event_type, payload FROM transactional_outbox")
        )).mappings().all()
    assert outbox[0]["event_type"] == "CUSTOM"
    assert outbox[0]["payload"]["sequence"] == 1


@pytest.mark.asyncio
async def test_node_result_report_and_durable_event_replays_are_idempotent(
    engine: AsyncEngine,
) -> None:
    store = PostgresWorkflowStore(engine)
    conversation = await store.create_conversation("usr_owner", "节点幂等")
    run = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={},
    )
    operation_key = f"{run.id}:synthesize:report"

    assert await store.get_node_operation_result(
        run.id, "synthesize", operation_key
    ) is None
    first_result = await store.save_node_operation_result(
        run.id,
        "synthesize",
        operation_key,
        {"summary": "first"},
    )
    replay_result = await store.save_node_operation_result(
        run.id,
        "synthesize",
        operation_key,
        {"summary": "must-not-overwrite"},
    )
    assert first_result == replay_result == {"summary": "first"}

    first_event = await store.append_event(
        conversation.id,
        AgUiEventType.TOOL_CALL_START,
        {"toolCallId": operation_key},
        dedup_key=f"{operation_key}:start",
    )
    replay_event = await store.append_event(
        conversation.id,
        AgUiEventType.TOOL_CALL_START,
        {"toolCallId": "must-not-overwrite"},
        dedup_key=f"{operation_key}:start",
    )
    assert replay_event.seq == first_event.seq
    assert replay_event.payload == {"toolCallId": operation_key}

    first_report = await store.publish_report(
        run.task_id,
        run.id,
        "usr_owner",
        {"changeOverview": {"summary": "first"}},
        "# first",
    )
    replay_report = await store.publish_report(
        run.task_id,
        run.id,
        "usr_owner",
        {"changeOverview": {"summary": "must-not-overwrite"}},
        "# must-not-overwrite",
    )
    assert replay_report.id == first_report.id
    assert replay_report.markdown_report == "# first"


@pytest.mark.asyncio
async def test_workspace_lease_tracks_runner_state_and_rolling_expiry(
    engine: AsyncEngine,
) -> None:
    store = PostgresWorkflowStore(engine)
    conversation = await store.create_conversation("usr_owner", "工作区")
    run = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={},
    )
    first_expiry = datetime.now(UTC) + timedelta(hours=48)

    await store.upsert_workspace_lease(
        run.task_id,
        runner_id="runner-a",
        container_id="container-1",
        image_digest="analysis@sha256:" + "a" * 64,
        status="STOPPED_RETAINED",
        expires_at=first_expiry,
        metadata={"runId": run.id},
    )
    lease = await store.get_workspace_lease(run.task_id)

    assert lease["status"] == "STOPPED_RETAINED"
    assert lease["containerId"] == "container-1"
    assert lease["expiresAt"] == first_expiry


@pytest.mark.asyncio
async def test_postgres_reanalysis_rejects_expired_workspace(
    engine: AsyncEngine,
) -> None:
    store = PostgresWorkflowStore(engine)
    conversation = await store.create_conversation("usr_owner", "过期工作区")
    run = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={},
    )
    await store.update_run_status(run.id, RunStatus.SUCCEEDED)
    await store.upsert_workspace_lease(
        run.task_id,
        runner_id="runner-a",
        container_id=None,
        image_digest="analysis@sha256:" + "a" * 64,
        status="STOPPED_RETAINED",
        expires_at=datetime.now(UTC) - timedelta(seconds=1),
        metadata={"runId": run.id},
    )

    with pytest.raises(WorkspaceNotReusable, match="创建新的分析任务"):
        await store.create_reanalysis_run(
            conversation.id,
            "usr_owner",
            scope_selectors=[{"kind": "FILE", "value": "src/App.java"}],
            session_digest="b" * 64,
        )

    assert (await store.get_workspace_lease(run.task_id))["status"] == "EXPIRED"


@pytest.mark.asyncio
async def test_postgres_workspace_cleanup_queue_and_status_are_durable(
    engine: AsyncEngine,
) -> None:
    store = PostgresWorkflowStore(engine)
    conversation = await store.create_conversation("usr_owner", "清理队列")
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

    due = await store.list_workspace_leases_due_for_cleanup(limit=10)
    assert [(value["taskId"], value["metadata"]["runId"]) for value in due] == [
        (run.task_id, run.id)
    ]

    await store.set_workspace_cleanup_status(run.task_id, "CLEANUP_FAILED")
    assert (await store.get_workspace_lease(run.task_id))["status"] == "CLEANUP_FAILED"
    await store.set_workspace_cleanup_status(run.task_id, "EXPIRED")
    lease = await store.get_workspace_lease(run.task_id)
    assert lease["status"] == "EXPIRED"
    assert lease["containerId"] is None


@pytest.mark.asyncio
async def test_cancel_cleanup_failure_makes_active_workspace_immediately_retryable(
    engine: AsyncEngine,
) -> None:
    store = PostgresWorkflowStore(engine)
    conversation = await store.create_conversation("usr_owner", "取消清理重试")
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
        status="ACTIVE",
        expires_at=None,
        metadata={"runId": run.id},
    )

    await store.set_workspace_cleanup_status(run.task_id, "CLEANUP_FAILED")

    due = await store.list_workspace_leases_due_for_cleanup(limit=10)
    assert [value["taskId"] for value in due] == [run.task_id]


@pytest.mark.asyncio
async def test_postgres_cancel_is_idempotent_but_cannot_rewrite_success_terminal_state(
    engine: AsyncEngine,
) -> None:
    store = PostgresWorkflowStore(engine)
    conversation = await store.create_conversation("usr_owner", "取消状态")
    canceled_run = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={},
    )
    first = await store.request_cancel(canceled_run.id, "usr_owner")
    second = await store.request_cancel(canceled_run.id, "usr_owner")
    assert first.status is RunStatus.CANCELED
    assert second.status is RunStatus.CANCELED

    second_conversation = await store.create_conversation("usr_owner", "成功终态")
    succeeded_run = await store.create_run(
        second_conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={},
    )
    await store.update_run_status(succeeded_run.id, RunStatus.SUCCEEDED)

    with pytest.raises(RunNotCancelable):
        await store.request_cancel(succeeded_run.id, "usr_owner")
    assert (await store.get_run(succeeded_run.id, "usr_owner")).status is RunStatus.SUCCEEDED


@pytest.mark.asyncio
async def test_postgres_claimed_transition_cannot_overwrite_concurrent_cancel(
    engine: AsyncEngine,
) -> None:
    store = PostgresWorkflowStore(engine)
    conversation = await store.create_conversation("usr_owner", "取消与终态竞态")
    queued = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={},
    )
    claimed = await store.claim_next_run("worker-owner-token", lease_seconds=30)
    assert claimed is not None and claimed.id == queued.id

    await store.request_cancel(queued.id, "usr_owner")

    assert (
        await store.transition_claimed_run(
            queued.id,
            "worker-owner-token",
            RunStatus.SUCCEEDED,
        )
        is None
    )
    assert (await store.get_run(queued.id, "usr_owner")).status is RunStatus.CANCELED
