from __future__ import annotations

import pytest

from testagent_workflow.intent import AgentScopeIntentClassifier, PlatformGrantedIntentClassifier
from testagent_workflow.registry import IntentInvocationContext, WorkflowRegistry
from testagent_workflow.workflows.impact_analysis import impact_analysis_definition


class FakeStructuredModel:
    def __init__(self, content: dict[str, object]) -> None:
        self.content = content
        self.messages = []

    async def generate_structured_output(self, messages, structured_model):  # type: ignore[no-untyped-def]
        self.messages = messages
        structured_model.model_validate(self.content)
        return type("Response", (), {"content": self.content})()


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
        "gateway_url": "http://platform.test/api/internal/platform/model-gateway/v1",
        "model_name": "workflow-intent",
    }
    assert platform.issued[0]["analyzer_ids"] == ["agentscope-intent"]
    assert platform.revoked[0]["grant_id"] == "wfgrantid_intent_123456"
