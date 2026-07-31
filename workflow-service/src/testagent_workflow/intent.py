"""基于AgentScope结构化输出的白名单意图分类器。"""

from __future__ import annotations

from collections.abc import Callable
import json
from typing import Any, Protocol

from agentscope.credential import OpenAICredential
from agentscope.message import Msg
from agentscope.model import OpenAIChatModel
from pydantic import BaseModel, ConfigDict, Field, SecretStr

from testagent_workflow.platform import PlatformCapabilityClient, PlatformRequestIdentity
from testagent_workflow.registry import IntentDecision, IntentInvocationContext, WorkflowRegistry


class StructuredChatModel(Protocol):
    async def generate_structured_output(
        self,
        messages: list[Msg],
        structured_model: type[BaseModel],
    ) -> Any: ...


class IntentOutput(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    intent_id: str = Field(alias="intentId", min_length=1, max_length=128)
    confidence: float = Field(ge=0.0, le=1.0)
    slots: dict[str, Any] = Field(default_factory=dict)
    missing_fields: list[str] = Field(default_factory=list, alias="missingFields")


class AgentScopeIntentClassifier:
    """只让模型做结构化判断，代码注册表仍是唯一执行授权来源。"""

    def __init__(
        self,
        registry: WorkflowRegistry,
        model: StructuredChatModel,
        *,
        confidence_threshold: float = 0.7,
    ) -> None:
        self._registry = registry
        self._model = model
        self._confidence_threshold = confidence_threshold

    async def __call__(
        self,
        message: str,
        allowed_ids: tuple[str, ...],
        context: IntentInvocationContext,
    ) -> IntentDecision:
        del context
        descriptions = [
            {
                "intentId": definition.id,
                "examples": list(definition.intent_examples),
                "inputSchema": definition.input_schema,
            }
            for workflow_id in allowed_ids
            for definition in (self._registry.get(workflow_id),)
        ]
        system = Msg(
            name="intent-router",
            role="system",
            content=[
                {
                    "type": "text",
                    "text": (
                        "你是工作流意图分类器。只允许选择下列已注册 intentId，不得创造新意图。"
                        "识别已知槽位，并列出启动所需但缺失的字段；不要执行任务。"
                        "如果消息明确要求对既有报告中的程序、模块、目录、文件或符号重新分析，"
                        "在slots中写入interactionType=REANALYSIS和scopeSelectors；"
                        "此时不要把历史任务已有的repositories、mode或analyzerIds列为缺失。"
                        "普通报告问答写入interactionType=QUESTION。\n"
                        + json.dumps(descriptions, ensure_ascii=False, separators=(",", ":"))
                    ),
                }
            ],
        )
        user = Msg(
            name="user",
            role="user",
            content=[{"type": "text", "text": message}],
        )
        response = await self._model.generate_structured_output([system, user], IntentOutput)
        output = IntentOutput.model_validate(response.content)

        # 低置信度不自动启动；由同一个AG-UI输入卡补充意图，不引入二次确认页面。
        missing = list(dict.fromkeys(value.strip() for value in output.missing_fields if value.strip()))
        if output.confidence < self._confidence_threshold and "intent" not in missing:
            missing.insert(0, "intent")
        return IntentDecision(
            intent_id=output.intent_id,
            confidence=output.confidence,
            slots=dict(output.slots),
            missing_fields=tuple(missing),
        )


ModelFactory = Callable[[str, str, str], StructuredChatModel]


class PlatformGrantedIntentClassifier:
    """按当前平台会话申请一次性短期grant，完成AgentScope识别后立即撤销。"""

    def __init__(
        self,
        registry: WorkflowRegistry,
        platform: PlatformCapabilityClient,
        *,
        model_name: str,
        model_factory: ModelFactory | None = None,
        confidence_threshold: float = 0.7,
    ) -> None:
        self._registry = registry
        self._platform = platform
        self._model_name = model_name
        self._model_factory = model_factory or create_platform_chat_model
        self._confidence_threshold = confidence_threshold

    async def __call__(
        self,
        message: str,
        allowed_ids: tuple[str, ...],
        context: IntentInvocationContext,
    ) -> IntentDecision:
        identity = PlatformRequestIdentity(context.user_id, context.session_digest)
        grant = await self._platform.issue_model_grant(
            identity,
            task_id=context.task_id,
            run_id=context.run_id,
            analyzer_ids=["agentscope-intent"],
        )
        grant_id = str(grant["grantId"])
        try:
            model = self._model_factory(
                str(grant["grant"]),
                str(grant["gatewayUrl"]),
                self._model_name,
            )
            delegate = AgentScopeIntentClassifier(
                self._registry,
                model,
                confidence_threshold=self._confidence_threshold,
            )
            return await delegate(message, allowed_ids, context)
        finally:
            await self._platform.revoke_model_grant(
                identity,
                task_id=context.task_id,
                run_id=context.run_id,
                grant_id=grant_id,
            )


def create_platform_chat_model(
    grant: str,
    gateway_url: str,
    model_name: str,
) -> StructuredChatModel:
    # grant是平台短期委托；真实供应商密钥始终只存在于Java模型网关。
    return OpenAIChatModel(
        credential=OpenAICredential(
            api_key=SecretStr(grant),
            base_url=gateway_url,
            name="workflow-user-grant",
        ),
        model=model_name,
        stream=False,
    )
