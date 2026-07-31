"""对话输入收集和任务自动启动应用层。"""

from __future__ import annotations

from dataclasses import dataclass
import hashlib
from typing import Any, Protocol

from testagent_workflow.agui import AgUiEventType
from pydantic import TypeAdapter, ValidationError

from testagent_workflow.models import ImpactAnalysisInput, ScopeSelector
from testagent_workflow.registry import (
    IntentInvocationContext,
    RegisteredIntentRouter,
    WorkflowRegistry,
)
from testagent_workflow.store import SubmissionResult, WorkflowStore


@dataclass(frozen=True, slots=True)
class MessageSubmission:
    client_request_id: str
    text: str
    structured_input: dict[str, Any] | None


@dataclass(frozen=True, slots=True)
class ReportQuestionContext:
    user_id: str
    session_digest: str
    request_id: str


class ReportQuestionAnswerer(Protocol):
    async def answer(
        self,
        question: str,
        report: Any,
        context: ReportQuestionContext,
    ) -> str: ...


class FollowupRetentionPort(Protocol):
    async def roll(self, report: Any, context: ReportQuestionContext) -> None: ...


class ConversationApplicationService:
    """将自然语言、结构化输入和版本化工作流连接起来。"""

    def __init__(
        self,
        store: WorkflowStore,
        registry: WorkflowRegistry,
        router: RegisteredIntentRouter,
        question_answerer: ReportQuestionAnswerer | None = None,
        followup_retention: FollowupRetentionPort | None = None,
    ) -> None:
        self._store = store
        self._registry = registry
        self._router = router
        self._question_answerer = question_answerer
        self._followup_retention = followup_retention

    async def submit(
        self,
        conversation_id: str,
        actor_user_id: str,
        submission: MessageSubmission,
        *,
        session_digest: str = "",
    ) -> SubmissionResult:
        # 幂等结果本身含taskId/runId，读取前也必须先完成对话所有权校验。
        await self._store.get_conversation(conversation_id, actor_user_id)
        replay = await self._store.find_submission(conversation_id, submission.client_request_id)
        if replay is not None:
            return replay

        user_message = await self._store.append_message(
            conversation_id,
            actor_user_id,
            role="user",
            content=submission.text,
            client_request_id=submission.client_request_id,
        )
        intent_scope_id = user_message.id.removeprefix("msg_")
        decision = await self._router.classify(
            submission.text,
            IntentInvocationContext(
                user_id=actor_user_id,
                session_digest=session_digest,
                task_id=f"intent_task_{intent_scope_id}",
                run_id=f"intent_run_{intent_scope_id}",
            ),
        )
        definition = self._registry.get(decision.intent_id)
        structured_input = submission.structured_input
        interaction_type = str(decision.slots.get("interactionType", "")).upper()
        reanalysis_requested = interaction_type == "REANALYSIS"

        if (
            structured_input is None
            and not reanalysis_requested
            and self._question_answerer is not None
        ):
            try:
                report = await self._store.get_current_report_for_conversation(
                    conversation_id,
                    actor_user_id,
                )
            except KeyError:
                report = None
            if report is not None:
                answer = await self._question_answerer.answer(
                    submission.text,
                    report,
                    ReportQuestionContext(
                        user_id=actor_user_id,
                        session_digest=session_digest,
                        request_id=user_message.id,
                    ),
                )
                if self._followup_retention is not None:
                    # 仅在回答成功后滚动工作区期限；同一clientRequestId的重放会在
                    # 方法入口返回已保存结果，不会重复延长租约。
                    await self._followup_retention.roll(
                        report,
                        ReportQuestionContext(
                            user_id=actor_user_id,
                            session_digest=session_digest,
                            request_id=user_message.id,
                        ),
                    )
                assistant = await self._store.append_message(
                    conversation_id,
                    actor_user_id,
                    role="assistant",
                    content=answer,
                    client_request_id=self._assistant_request_id(user_message.id),
                )
                await self._publish_assistant_message(
                    conversation_id, assistant, user_message.id
                )
                return await self._store.save_submission(
                    conversation_id,
                    submission.client_request_id,
                    SubmissionResult(
                        message_id=user_message.id,
                        assistant_message_id=assistant.id,
                        task_id=None,
                        run_id=None,
                        status=None,
                        required_input=(),
                    ),
                )

        if structured_input is None and "intent" not in decision.missing_fields:
            if reanalysis_requested:
                try:
                    selectors = TypeAdapter(list[ScopeSelector]).validate_python(
                        decision.slots.get("scopeSelectors", [])
                    )
                except ValidationError:
                    selectors = []
                if selectors:
                    structured_input = {
                        "scopeSelectors": [
                            value.model_dump(mode="json", by_alias=True)
                            for value in selectors
                        ]
                    }
            elif not decision.missing_fields:
                try:
                    inferred = ImpactAnalysisInput.model_validate(dict(decision.slots))
                except ValidationError:
                    inferred = None
                if inferred is not None:
                    structured_input = inferred.model_dump(mode="json", by_alias=True)

        if structured_input is None:
            required = (
                ("scopeSelectors",)
                if reanalysis_requested and "intent" not in decision.missing_fields
                else tuple(decision.missing_fields)
            )
            if not required:
                required = ("repositories", "mode", "analyzerIds")
            assistant = await self._store.append_message(
                conversation_id,
                actor_user_id,
                role="assistant",
                content="请补充需要分析的代码库、目标分支和分析智能体。",
                client_request_id=self._assistant_request_id(user_message.id),
            )
            await self._publish_assistant_message(
                conversation_id, assistant, user_message.id
            )
            await self._store.append_event(
                conversation_id,
                AgUiEventType.CUSTOM,
                {
                    "name": "workflow.input_required",
                    "value": {
                        "workflowId": definition.id,
                        "requiredInput": list(required),
                        "inputSchema": definition.input_schema,
                        "uiSchema": definition.ui_schema,
                    },
                },
                dedup_key=f"{user_message.id}:input-required",
            )
            result = SubmissionResult(
                message_id=user_message.id,
                assistant_message_id=assistant.id,
                task_id=None,
                run_id=None,
                status=None,
                required_input=required,
            )
        else:
            scope_values = structured_input.get("scopeSelectors", [])
            run = None
            # WAITING_INPUT既可能等待代码范围消歧，也可能等待默认基线选择；
            # 两者都在原run上以新checkpoint namespace恢复，禁止偷偷新建任务。
            try:
                if "repositories" in structured_input:
                    validated_input = ImpactAnalysisInput.model_validate(structured_input)
                    resume_patch = validated_input.model_dump(mode="json", by_alias=True)
                else:
                    resume_patch = dict(structured_input)
                if scope_values and "repositories" not in structured_input:
                    selectors = TypeAdapter(list[ScopeSelector]).validate_python(scope_values)
                    resume_patch["scopeSelectors"] = [
                        value.model_dump(mode="json", by_alias=True) for value in selectors
                    ]
                run = await self._store.resume_waiting_run(
                    conversation_id,
                    actor_user_id,
                    input_patch=resume_patch,
                    session_digest=session_digest,
                    request_key=user_message.id,
                )
            except KeyError:
                run = None
            if run is None and scope_values:
                selectors = TypeAdapter(list[ScopeSelector]).validate_python(scope_values)
                serialized_selectors = [
                    value.model_dump(mode="json", by_alias=True) for value in selectors
                ]
                try:
                    run = await self._store.create_reanalysis_run(
                        conversation_id,
                        actor_user_id,
                        scope_selectors=serialized_selectors,
                        session_digest=session_digest,
                        request_key=user_message.id,
                    )
                except KeyError:
                    # 首次分析也允许限定范围；没有历史task时仍按完整初始输入启动。
                    run = None
            if run is None:
                parsed_input = ImpactAnalysisInput.model_validate(structured_input)
                run = await self._store.create_run(
                    conversation_id,
                    actor_user_id,
                    workflow_id=definition.id,
                    workflow_version=definition.version,
                    input_data=parsed_input.model_dump(mode="json", by_alias=True),
                    session_digest=session_digest,
                    request_key=user_message.id,
                )
            await self._store.append_event(
                conversation_id,
                AgUiEventType.RUN_STARTED,
                {
                    "runId": run.id,
                    "taskId": run.task_id,
                    "workflowId": run.workflow_id,
                    "status": run.status.value,
                },
                dedup_key=f"{user_message.id}:run-started",
            )
            result = SubmissionResult(
                message_id=user_message.id,
                assistant_message_id=None,
                task_id=run.task_id,
                run_id=run.id,
                status=run.status.value,
                required_input=(),
            )
        return await self._store.save_submission(
            conversation_id,
            submission.client_request_id,
            result,
        )

    async def _publish_assistant_message(
        self,
        conversation_id: str,
        assistant: Any,
        request_message_id: str,
    ) -> None:
        await self._store.append_event(
            conversation_id,
            AgUiEventType.TEXT_MESSAGE_START,
            {"messageId": assistant.id, "role": "assistant"},
            dedup_key=f"{request_message_id}:assistant:start",
        )
        await self._store.append_event(
            conversation_id,
            AgUiEventType.TEXT_MESSAGE_CONTENT,
            {"messageId": assistant.id, "delta": assistant.content},
            dedup_key=f"{request_message_id}:assistant:content",
        )
        await self._store.append_event(
            conversation_id,
            AgUiEventType.TEXT_MESSAGE_END,
            {"messageId": assistant.id},
            dedup_key=f"{request_message_id}:assistant:end",
        )

    @staticmethod
    def _assistant_request_id(request_message_id: str) -> str:
        digest = hashlib.sha256(request_message_id.encode()).hexdigest()
        return f"assistant_{digest[:48]}"
