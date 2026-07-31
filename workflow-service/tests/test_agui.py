from __future__ import annotations

import pytest

from testagent_workflow.agui import AgUiEventType, EventReplayService
from testagent_workflow.store import InMemoryWorkflowStore


@pytest.mark.asyncio
async def test_reconnect_starts_with_snapshots_then_replays_durable_events() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "代码影响分析")
    await store.append_message(
        conversation.id,
        "usr_owner",
        role="user",
        content="分析代码影响",
        client_request_id="req_1234567890abcdef",
    )
    first = await store.append_event(
        conversation.id,
        event_type=AgUiEventType.RUN_STARTED,
        payload={"runId": "run_1234567890abcdef"},
    )
    second = await store.append_event(
        conversation.id,
        event_type=AgUiEventType.CUSTOM,
        payload={"name": "workflow.progress", "value": {"step": "prepare"}},
    )

    replay = await EventReplayService(store).replay(
        conversation.id,
        "usr_owner",
        after_seq=first.seq,
    )

    assert [event.type for event in replay] == [
        AgUiEventType.STATE_SNAPSHOT,
        AgUiEventType.MESSAGES_SNAPSHOT,
        AgUiEventType.CUSTOM,
    ]
    assert replay[-1].seq == second.seq
    assert replay[1].payload["messages"][0]["content"] == "分析代码影响"


@pytest.mark.asyncio
async def test_event_sequence_is_monotonic_per_conversation() -> None:
    store = InMemoryWorkflowStore()
    left = await store.create_conversation("usr_owner", "左侧")
    right = await store.create_conversation("usr_owner", "右侧")

    left_one = await store.append_event(left.id, AgUiEventType.RUN_STARTED, {})
    left_two = await store.append_event(left.id, AgUiEventType.RUN_FINISHED, {})
    right_one = await store.append_event(right.id, AgUiEventType.RUN_STARTED, {})

    assert (left_one.seq, left_two.seq, right_one.seq) == (1, 2, 1)


@pytest.mark.asyncio
async def test_snapshots_restore_latest_run_workspace_and_message_timestamp() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "恢复")
    message = await store.append_message(
        conversation.id,
        "usr_owner",
        role="user",
        content="恢复状态",
        client_request_id="req_restore_12345678",
    )
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
        expires_at=None,
        metadata={},
    )

    replay = await EventReplayService(store).replay(conversation.id, "usr_owner")

    snapshot = replay[0].payload["snapshot"]
    assert snapshot["runId"] == run.id
    assert snapshot["taskId"] == run.task_id
    assert snapshot["runStatus"] == "QUEUED"
    assert snapshot["workspaceStatus"] == "STOPPED_RETAINED"
    assert replay[1].payload["messages"][0]["createdAt"] == message.created_at.isoformat()


@pytest.mark.asyncio
async def test_snapshot_projects_waiting_input_tools_and_report_even_after_cursor() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "持久投影")
    run = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={},
    )
    await store.append_event(
        conversation.id,
        AgUiEventType.RUN_STARTED,
        {"runId": run.id, "taskId": run.task_id},
    )
    await store.append_event(
        conversation.id,
        AgUiEventType.TOOL_CALL_START,
        {
            "runId": run.id,
            "toolCallId": f"{run.id}:analyze:codex",
            "toolCallName": "codex 代码影响分析",
        },
    )
    end = await store.append_event(
        conversation.id,
        AgUiEventType.TOOL_CALL_END,
        {
            "runId": run.id,
            "toolCallId": f"{run.id}:analyze:codex",
            "status": "SUCCEEDED",
        },
    )
    await store.append_event(
        conversation.id,
        AgUiEventType.CUSTOM,
        {
            "name": "workflow.input_required",
            "value": {
                "runId": run.id,
                "kind": "BASELINE_SELECTION",
                "baselines": [{"repositoryId": "repo_a", "availableBranches": ["main"]}],
                "currentInput": {"repositories": []},
            },
        },
    )

    replay = await EventReplayService(store).replay(
        conversation.id,
        "usr_owner",
        after_seq=end.seq or 0,
    )

    snapshot = replay[0].payload["snapshot"]
    assert snapshot["tools"] == [
        {
            "id": f"{run.id}:analyze:codex",
            "name": "codex 代码影响分析",
            "status": "SUCCEEDED",
        }
    ]
    assert snapshot["baselineInput"][0]["repositoryId"] == "repo_a"
