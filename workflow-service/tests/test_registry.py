from __future__ import annotations

import pytest

from testagent_workflow.registry import (
    IntentDecision,
    IntentInvocationContext,
    RegisteredIntentRouter,
    UnknownWorkflowError,
    WorkflowDefinition,
    WorkflowRegistry,
)
from testagent_workflow.workflows.impact_analysis import impact_analysis_definition


def definition(workflow_id: str) -> WorkflowDefinition:
    return WorkflowDefinition(
        id=workflow_id,
        version="1.0.0",
        display_name="代码变动影响分析",
        intent_examples=("分析这个分支会影响什么功能",),
        required_permissions=("repository:read",),
        risk_level="READ_ONLY",
        capabilities=("git:read", "model:invoke", "report:write"),
        requires_sandbox=True,
        input_schema={"type": "object"},
        ui_schema={"layout": "impact-analysis"},
        output_schema={"type": "object"},
        graph_factory=lambda: object(),
    )


def test_registry_rejects_duplicate_workflow_ids() -> None:
    registry = WorkflowRegistry()
    registry.register(definition("code-change-impact-analysis"))

    with pytest.raises(ValueError, match="重复的工作流ID"):
        registry.register(definition("code-change-impact-analysis"))


def test_impact_definition_exposes_the_complete_report_contract() -> None:
    schema = impact_analysis_definition().output_schema

    assert set(schema["required"]) == {
        "comparisonCoordinates",
        "changeOverview",
        "impactedFeatures",
        "crossRepositoryImpacts",
        "risks",
        "codeEvidence",
        "recommendedRegressionTests",
        "uncertainties",
        "analyzerDisagreements",
        "changedFilesAppendix",
    }


@pytest.mark.asyncio
async def test_router_rejects_model_selected_unregistered_workflow() -> None:
    registry = WorkflowRegistry([definition("code-change-impact-analysis")])

    async def malicious_classifier(
        _: str,
        __: tuple[str, ...],
        ___: IntentInvocationContext,
    ) -> IntentDecision:
        return IntentDecision(
            intent_id="delete-production-data",
            confidence=0.99,
            slots={},
            missing_fields=(),
        )

    router = RegisteredIntentRouter(registry, malicious_classifier)

    with pytest.raises(UnknownWorkflowError, match="未注册的工作流意图"):
        await router.classify(
            "帮我删除生产数据",
            IntentInvocationContext("usr_owner", "a" * 64, "intent_task_12345678", "intent_run_12345678"),
        )


@pytest.mark.asyncio
async def test_router_passes_only_registered_ids_to_classifier() -> None:
    registry = WorkflowRegistry([definition("code-change-impact-analysis")])
    observed: list[tuple[str, ...]] = []

    async def classifier(
        _: str,
        allowed: tuple[str, ...],
        __: IntentInvocationContext,
    ) -> IntentDecision:
        observed.append(allowed)
        return IntentDecision(
            intent_id="code-change-impact-analysis",
            confidence=0.91,
            slots={"mode": "SINGLE"},
            missing_fields=("repositories",),
        )

    decision = await RegisteredIntentRouter(registry, classifier).classify(
        "分析代码影响",
        IntentInvocationContext("usr_owner", "a" * 64, "intent_task_12345678", "intent_run_12345678"),
    )

    assert observed == [("code-change-impact-analysis",)]
    assert decision.missing_fields == ("repositories",)
