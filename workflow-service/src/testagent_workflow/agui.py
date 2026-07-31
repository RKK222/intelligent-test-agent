"""AG-UI持久事件、快照和断线重放。"""

from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum
from typing import TYPE_CHECKING, Any

if TYPE_CHECKING:
    from testagent_workflow.store import WorkflowStore


class AgUiEventType(StrEnum):
    RUN_STARTED = "RUN_STARTED"
    RUN_FINISHED = "RUN_FINISHED"
    RUN_ERROR = "RUN_ERROR"
    TEXT_MESSAGE_START = "TEXT_MESSAGE_START"
    TEXT_MESSAGE_CONTENT = "TEXT_MESSAGE_CONTENT"
    TEXT_MESSAGE_END = "TEXT_MESSAGE_END"
    TOOL_CALL_START = "TOOL_CALL_START"
    TOOL_CALL_ARGS = "TOOL_CALL_ARGS"
    TOOL_CALL_END = "TOOL_CALL_END"
    STATE_SNAPSHOT = "STATE_SNAPSHOT"
    STATE_DELTA = "STATE_DELTA"
    MESSAGES_SNAPSHOT = "MESSAGES_SNAPSHOT"
    CUSTOM = "CUSTOM"


@dataclass(frozen=True, slots=True)
class AgUiEvent:
    conversation_id: str
    type: AgUiEventType
    payload: dict[str, Any]
    seq: int | None


class EventReplayService:
    """重连先校准完整视图，再补发游标后的持久事件。"""

    def __init__(self, store: "WorkflowStore") -> None:
        self._store = store

    async def replay(
        self,
        conversation_id: str,
        actor_user_id: str,
        *,
        after_seq: int = 0,
        is_super_admin: bool = False,
    ) -> list[AgUiEvent]:
        conversation = await self._store.get_conversation(
            conversation_id,
            actor_user_id,
            is_super_admin=is_super_admin,
        )
        messages = await self._store.list_messages(
            conversation_id,
            actor_user_id,
            is_super_admin=is_super_admin,
        )
        run = None
        workspace = None
        try:
            run = await self._store.get_latest_run_for_conversation(
                conversation_id,
                actor_user_id,
                is_super_admin=is_super_admin,
            )
            try:
                workspace = await self._store.get_workspace_lease(run.task_id)
            except KeyError:
                workspace = None
        except KeyError:
            run = None
        state_snapshot: dict[str, Any] = {
            "conversationId": conversation.id,
            "title": conversation.title,
        }
        if run is not None:
            state_snapshot.update(
                {
                    "runId": run.id,
                    "taskId": run.task_id,
                    "runStatus": run.status.value,
                }
            )
        if workspace is not None:
            state_snapshot["workspaceStatus"] = workspace["status"]
        history = await self._store.list_events(conversation_id, after_seq=0)
        state_snapshot.update(_project_durable_state(history, run.id if run is not None else None))
        snapshots = [
            AgUiEvent(
                conversation_id=conversation_id,
                type=AgUiEventType.STATE_SNAPSHOT,
                payload={
                    "snapshot": state_snapshot
                },
                seq=None,
            ),
            AgUiEvent(
                conversation_id=conversation_id,
                type=AgUiEventType.MESSAGES_SNAPSHOT,
                payload={
                    "messages": [
                        {
                            "id": message.id,
                            "role": message.role,
                            "content": message.content,
                            "createdAt": message.created_at.isoformat(),
                        }
                        for message in messages
                    ]
                },
                seq=None,
            ),
        ]
        return snapshots + [
            event for event in history if event.seq is not None and event.seq > after_seq
        ]


def _project_durable_state(events: list[AgUiEvent], run_id: str | None) -> dict[str, Any]:
    """从持久事件恢复断线后仍需展示的输入卡片、工具摘要和报告状态。"""

    tools: dict[str, dict[str, str]] = {}
    required_input: list[str] = []
    scope_input: list[dict[str, Any]] = []
    baseline_input: list[dict[str, Any]] = []
    current_input: dict[str, Any] | None = None
    report_published = False
    for event in events:
        event_run_id = event.payload.get("runId")
        value = event.payload.get("value")
        if event_run_id is None and isinstance(value, dict):
            event_run_id = value.get("runId")
        if run_id is not None and event_run_id not in {None, run_id}:
            continue
        if event.type is AgUiEventType.RUN_STARTED:
            required_input = []
            scope_input = []
            baseline_input = []
            current_input = None
            report_published = False
            tools = {}
        elif event.type is AgUiEventType.TOOL_CALL_START:
            identifier = str(event.payload.get("toolCallId", ""))
            if identifier:
                tools[identifier] = {
                    "id": identifier,
                    "name": str(event.payload.get("toolCallName", "分析工具")),
                    "status": "RUNNING",
                }
        elif event.type is AgUiEventType.TOOL_CALL_END:
            identifier = str(event.payload.get("toolCallId", ""))
            if identifier:
                tool = tools.setdefault(
                    identifier,
                    {"id": identifier, "name": "分析工具", "status": "RUNNING"},
                )
                tool["status"] = str(event.payload.get("status", "FINISHED"))
        elif event.type is AgUiEventType.CUSTOM and isinstance(value, dict):
            name = event.payload.get("name")
            if name == "workflow.input_required":
                required_input = [str(item) for item in value.get("requiredInput", [])]
                scope_input = [dict(item) for item in value.get("scope", [])]
                baseline_input = [dict(item) for item in value.get("baselines", [])]
                raw_input = value.get("currentInput")
                current_input = dict(raw_input) if isinstance(raw_input, dict) else None
            elif name == "workflow.report_published":
                report_published = True
    return {
        "requiredInput": required_input,
        "scopeInput": scope_input,
        "baselineInput": baseline_input,
        "currentInput": current_input,
        "tools": list(tools.values())[-50:],
        "reportPublished": report_published,
    }
