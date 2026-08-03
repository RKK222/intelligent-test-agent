"""基于AgentScope结构化输出的白名单意图分类器。"""

from __future__ import annotations

from collections.abc import Callable
import json
import logging
from typing import Any, Protocol

from agentscope.credential import OpenAICredential
from agentscope.message import Msg, TextBlock
from agentscope.model import ChatResponse, OpenAIChatModel, StructuredResponse
import httpx
from pydantic import BaseModel, ConfigDict, Field, SecretStr

from testagent_workflow.platform import PlatformCapabilityClient, PlatformRequestIdentity
from testagent_workflow.registry import IntentDecision, IntentInvocationContext, WorkflowRegistry


LOGGER = logging.getLogger(__name__)


class StructuredChatModel(Protocol):
    async def generate_structured_output(
        self,
        messages: list[Msg],
        structured_model: type[BaseModel],
    ) -> Any: ...


class DirectPlatformChatModel:
    """通过不继承系统代理的长连接调用平台模型网关。"""

    def __init__(self, grant: str, gateway_url: str, model_name: str) -> None:
        # 平台地址是固定内部控制面；继承宿主机代理会把短期grant错误转发到外部代理，
        # 同时非流式本地模型可能超过模型网关的首包等待时间。
        self._http = httpx.AsyncClient(
            timeout=httpx.Timeout(connect=10.0, read=None, write=60.0, pool=30.0),
            trust_env=False,
        )
        self._delegate = OpenAIChatModel(
            credential=OpenAICredential(
                api_key=SecretStr(grant),
                base_url=gateway_url,
                name="workflow-user-grant",
            ),
            model=model_name,
            parameters=OpenAIChatModel.Parameters(temperature=0.0),
            stream=True,
            client_kwargs={"http_client": self._http},
        )

    async def generate_structured_output(
        self,
        messages: list[Msg],
        structured_model: type[BaseModel],
    ) -> Any:
        try:
            return await self._generate_json_schema_output(messages, structured_model)
        except Exception as exception:
            # 部分企业OpenAI-compatible供应商尚未实现json_schema；保留AgentScope
            # 工具调用作为兼容回退，但本地Ollama优先使用其原生结构化输出能力。
            LOGGER.warning(
                "Native JSON-schema output failed for model %s; falling back to tool output: %s",
                self._delegate.model,
                type(exception).__name__,
            )
            return await self._delegate.generate_structured_output(messages, structured_model)

    async def _generate_json_schema_output(
        self,
        messages: list[Msg],
        structured_model: type[BaseModel],
    ) -> StructuredResponse:
        response = await self._delegate(
            messages,
            reasoning_effort="none",
            response_format={
                "type": "json_schema",
                "json_schema": {
                    "name": structured_model.__name__,
                    "schema": structured_model.model_json_schema(),
                    "strict": True,
                },
            },
        )
        completed: ChatResponse | None = None
        if isinstance(response, ChatResponse):
            completed = response
        else:
            async for chunk in response:
                if chunk.is_last:
                    completed = chunk
        if completed is None:
            raise RuntimeError("平台模型流未返回最终响应")
        text = "".join(
            block.text for block in completed.content if isinstance(block, TextBlock)
        )
        if not text.strip():
            raise RuntimeError("平台模型未返回结构化JSON正文")
        validated = structured_model.model_validate_json(text)
        return StructuredResponse(
            content=validated.model_dump(mode="json", by_alias=True),
            id=completed.id,
            created_at=completed.created_at,
            usage=completed.usage,
            finished_reason=completed.finished_reason,
        )

    async def aclose(self) -> None:
        await self._http.aclose()


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


async def close_platform_chat_model(model: StructuredChatModel) -> None:
    """关闭生产模型持有的HTTP连接，同时兼容测试或外部注入的轻量模型。"""

    close = getattr(model, "aclose", None)
    if close is not None:
        await close()


class PlatformGrantedIntentClassifier:
    """按当前平台会话申请一次性短期grant，完成AgentScope识别后立即撤销。"""

    def __init__(
        self,
        registry: WorkflowRegistry,
        platform: PlatformCapabilityClient,
        *,
        model_name: str,
        model_factory: ModelFactory | None = None,
        gateway_url_override: str | None = None,
        confidence_threshold: float = 0.7,
    ) -> None:
        self._registry = registry
        self._platform = platform
        self._model_name = model_name
        self._model_factory = model_factory or create_platform_chat_model
        self._gateway_url_override = gateway_url_override
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
        model: StructuredChatModel | None = None
        try:
            model = self._model_factory(
                str(grant["grant"]),
                self._gateway_url_override or str(grant["gatewayUrl"]),
                self._model_name,
            )
            delegate = AgentScopeIntentClassifier(
                self._registry,
                model,
                confidence_threshold=self._confidence_threshold,
            )
            return await delegate(message, allowed_ids, context)
        finally:
            try:
                if model is not None:
                    await close_platform_chat_model(model)
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
    return DirectPlatformChatModel(grant, gateway_url, model_name)
