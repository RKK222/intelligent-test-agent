from __future__ import annotations

import pytest
from agentscope.message import Msg, TextBlock
from agentscope.model import ChatResponse

from testagent_workflow.intent import (
    AgentScopeIntentClassifier,
    DirectPlatformChatModel,
    IntentOutput,
    PlatformGrantedIntentClassifier,
    create_platform_chat_model,
)
from testagent_workflow.registry import IntentInvocationContext, WorkflowRegistry
from testagent_workflow.workflows.impact_analysis import impact_analysis_definition


class FakeStructuredModel:
    def __init__(self, content: dict[str, object]) -> None:
        self.content = content
        self.messages = []
        self.closed = False

    async def generate_structured_output(self, messages, structured_model):  # type: ignore[no-untyped-def]
        self.messages = messages
        structured_model.model_validate(self.content)
        return type("Response", (), {"content": self.content})()

    async def aclose(self) -> None:
        self.closed = True


class FakePlatform:
    def __init__(self) -> None:
        self.issued: list[dict[str, object]] = []
        self.revoked: list[dict[str, object]] = []

    async def issue_model_grant(self, identity, **scope):  # type: ignore[no-untyped-def]
        self.issued.append({"identity": identity, **scope})
        return {
            "grantId": "wfgrantid_intent_123456",
            "grant": "wfg_intent_1234567890",
            "gatewayUrl": "http://platform.test/api/internal/platform/model-gateway/v1",
        }

    async def revoke_model_grant(self, identity, **scope):  # type: ignore[no-untyped-def]
        self.revoked.append({"identity": identity, **scope})


@pytest.mark.asyncio
async def test_agentscope_classifier_constrains_output_and_normalizes_missing_slots() -> None:
    model = FakeStructuredModel(
        {
            "intentId": "code-change-impact-analysis",
            "confidence": 0.93,
            "slots": {"mode": "SINGLE"},
            "missingFields": ["repositories", "analyzerIds", "repositories"],
        }
    )
    classifier = AgentScopeIntentClassifier(
        WorkflowRegistry([impact_analysis_definition()]),
        model,
    )

    decision = await classifier(
        "帮我分析 feature/a 的影响",
        ("code-change-impact-analysis",),
        IntentInvocationContext("usr_owner", "a" * 64, "intent_task_12345678", "intent_run_12345678"),
    )

    assert decision.intent_id == "code-change-impact-analysis"
    assert decision.missing_fields == ("repositories", "analyzerIds")
    assert "只允许选择" in model.messages[0].get_text_content()


@pytest.mark.asyncio
async def test_low_confidence_is_treated_as_missing_intent_confirmation() -> None:
    model = FakeStructuredModel(
        {
            "intentId": "code-change-impact-analysis",
            "confidence": 0.42,
            "slots": {},
            "missingFields": [],
        }
    )
    classifier = AgentScopeIntentClassifier(
        WorkflowRegistry([impact_analysis_definition()]),
        model,
        confidence_threshold=0.7,
    )

    decision = await classifier(
        "看看代码",
        ("code-change-impact-analysis",),
        IntentInvocationContext("usr_owner", "a" * 64, "intent_task_12345678", "intent_run_12345678"),
    )

    assert decision.missing_fields == ("intent",)


@pytest.mark.asyncio
async def test_platform_granted_classifier_issues_user_bound_grant_and_always_revokes() -> None:
    platform = FakePlatform()
    model = FakeStructuredModel(
        {
            "intentId": "code-change-impact-analysis",
            "confidence": 0.96,
            "slots": {},
            "missingFields": ["repositories"],
        }
    )
    captured: dict[str, str] = {}

    def model_factory(grant: str, gateway_url: str, model_name: str):  # type: ignore[no-untyped-def]
        captured.update(grant=grant, gateway_url=gateway_url, model_name=model_name)
        return model

    registry = WorkflowRegistry([impact_analysis_definition()])
    classifier = PlatformGrantedIntentClassifier(
        registry,
        platform,  # type: ignore[arg-type]
        model_name="workflow-intent",
        model_factory=model_factory,
        gateway_url_override=(
            "http://127.0.0.1:8080/api/internal/platform/model-gateway/v1"
        ),
    )
    context = IntentInvocationContext(
        "usr_owner",
        "b" * 64,
        "intent_task_12345678",
        "intent_run_12345678",
    )

    decision = await classifier("分析变更影响", registry.ids(), context)

    assert decision.intent_id == "code-change-impact-analysis"
    assert captured == {
        "grant": "wfg_intent_1234567890",
        "gateway_url": "http://127.0.0.1:8080/api/internal/platform/model-gateway/v1",
        "model_name": "workflow-intent",
    }
    assert platform.issued[0]["analyzer_ids"] == ["agentscope-intent"]
    assert platform.revoked[0]["grant_id"] == "wfgrantid_intent_123456"
    assert model.closed is True


@pytest.mark.asyncio
async def test_platform_model_ignores_environment_proxy_and_streams() -> None:
    model = create_platform_chat_model(
        "wfg_intent_1234567890",
        "http://127.0.0.1:8080/api/internal/platform/model-gateway/v1",
        "workflow-intent",
    )

    assert isinstance(model, DirectPlatformChatModel)
    assert model._http._trust_env is False  # noqa: SLF001 - 锁定内部模型网关不走环境代理
    assert model._delegate.stream is True  # noqa: SLF001 - 锁定本地模型使用流式首包
    assert model._delegate.parameters.temperature == 0.0  # noqa: SLF001
    assert model._delegate.parameters.reasoning_effort is None  # noqa: SLF001

    await model.aclose()
    assert model._http.is_closed is True  # noqa: SLF001 - 模型所有者负责释放连接


@pytest.mark.asyncio
async def test_platform_model_prefers_native_json_schema_output() -> None:
    model = create_platform_chat_model(
        "wfg_intent_1234567890",
        "http://127.0.0.1:8080/api/internal/platform/model-gateway/v1",
        "workflow-intent",
    )
    assert isinstance(model, DirectPlatformChatModel)

    class FakeNativeDelegate:
        model = "workflow-intent"

        def __init__(self) -> None:
            self.response_format: dict[str, object] | None = None
            self.reasoning_effort: str | None = None

        async def __call__(self, messages, **kwargs):  # type: ignore[no-untyped-def]
            del messages
            self.response_format = kwargs["response_format"]
            self.reasoning_effort = kwargs["reasoning_effort"]

            async def stream():  # type: ignore[no-untyped-def]
                yield ChatResponse(
                    content=[TextBlock(text=(
                        '{"intentId":"code-change-impact-analysis","confidence":0.99,'
                        '"slots":{},"missingFields":[]}'
                    ))],
                    is_last=True,
                )

            return stream()

        async def generate_structured_output(self, messages, structured_model):  # type: ignore[no-untyped-def]
            del messages, structured_model
            raise AssertionError("原生JSON Schema成功时不应回退工具调用")

    delegate = FakeNativeDelegate()
    model._delegate = delegate  # type: ignore[assignment]  # noqa: SLF001

    response = await model.generate_structured_output(
        [Msg(name="user", role="user", content=[{"type": "text", "text": "分析"}])],
        IntentOutput,
    )

    assert response.content["intentId"] == "code-change-impact-analysis"
    assert delegate.response_format is not None
    assert delegate.response_format["type"] == "json_schema"
    assert delegate.reasoning_effort == "none"
    await model.aclose()


@pytest.mark.asyncio
async def test_platform_model_falls_back_when_provider_rejects_json_schema() -> None:
    model = create_platform_chat_model(
        "wfg_intent_1234567890",
        "http://127.0.0.1:8080/api/internal/platform/model-gateway/v1",
        "workflow-intent",
    )
    assert isinstance(model, DirectPlatformChatModel)

    class FakeToolFallbackDelegate:
        model = "workflow-intent"

        def __init__(self) -> None:
            self.fallback_called = False

        async def __call__(self, messages, **kwargs):  # type: ignore[no-untyped-def]
            del messages, kwargs
            raise RuntimeError("json_schema unsupported")

        async def generate_structured_output(self, messages, structured_model):  # type: ignore[no-untyped-def]
            del messages
            self.fallback_called = True
            content = {
                "intentId": "code-change-impact-analysis",
                "confidence": 0.98,
                "slots": {},
                "missingFields": [],
            }
            structured_model.model_validate(content)
            return type("Response", (), {"content": content})()

    delegate = FakeToolFallbackDelegate()
    model._delegate = delegate  # type: ignore[assignment]  # noqa: SLF001

    response = await model.generate_structured_output(
        [Msg(name="user", role="user", content=[{"type": "text", "text": "分析"}])],
        IntentOutput,
    )

    assert response.content["confidence"] == 0.98
    assert delegate.fallback_called is True
    await model.aclose()
