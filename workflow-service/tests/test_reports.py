from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

import pytest

from testagent_workflow.application import ReportQuestionContext
from testagent_workflow.reports import (
    AgentScopeResultSynthesizer,
    GrantBackedResultSynthesizer,
    PlatformGrantedReportQuestionAnswerer,
    StoreReportPublisher,
    render_impact_report_markdown,
)
from testagent_workflow.impact_engine import AnalyzerOutcome, DiffManifest, FrozenRepository
from testagent_workflow.store import InMemoryWorkflowStore


def complete_report() -> dict:
    return {
        "comparisonCoordinates": [
            {
                "repositoryAlias": "orders",
                "defaultBranch": "main",
                "defaultHead": "a" * 40,
                "targetBranch": "feature/a",
                "targetHead": "b" * 40,
                "mergeBase": "a" * 40,
            }
        ],
        "changeOverview": {"summary": "修改订单校验", "fileCount": 1},
        "impactedFeatures": [{"name": "订单提交", "impact": "参数校验变化"}],
        "crossRepositoryImpacts": [],
        "risks": [{"level": "MEDIUM", "confidence": 0.82, "description": "兼容性"}],
        "codeEvidence": [{"repositoryAlias": "orders", "path": "Order.java", "line": 42}],
        "recommendedRegressionTests": ["无效订单参数"],
        "uncertainties": ["动态调用无法静态确认"],
        "analyzerDisagreements": [],
        "changedFilesAppendix": [{"repositoryAlias": "orders", "path": "Order.java", "status": "MODIFIED"}],
    }


@dataclass
class FakeResponse:
    content: dict


class FakeQuestionModel:
    def __init__(self) -> None:
        self.closed = False

    async def generate_structured_output(self, messages, structured_model):  # type: ignore[no-untyped-def]
        assert "Order.java" in str(messages)
        return FakeResponse({
            "answer": "订单校验分支发生变化。",
            "evidence": ["orders/Order.java:42"],
            "limitations": ["动态调用仍需运行时确认"],
        })

    async def aclose(self) -> None:
        self.closed = True


class FakeQuestionPlatform:
    def __init__(self) -> None:
        self.revoked = False

    async def issue_model_grant(self, identity, **scope):  # type: ignore[no-untyped-def]
        assert scope["analyzer_ids"] == ["agentscope-report-qa"]
        return {
            "grantId": "wfgrantid_question",
            "grant": "wfg_question",
            "gatewayUrl": "http://model.test/v1",
        }

    async def revoke_model_grant(self, identity, **scope):  # type: ignore[no-untyped-def]
        assert scope["grant_id"] == "wfgrantid_question"
        self.revoked = True


class CountingSynthesisModel:
    def __init__(self) -> None:
        self.calls = 0
        self.closed = False

    async def generate_structured_output(self, messages, structured_model):  # type: ignore[no-untyped-def]
        self.calls += 1
        return FakeResponse(complete_report())

    async def aclose(self) -> None:
        self.closed = True


class CapturingSynthesisModel:
    def __init__(self) -> None:
        self.messages = []

    async def generate_structured_output(self, messages, structured_model):  # type: ignore[no-untyped-def]
        del structured_model
        self.messages = messages
        forged = complete_report()
        forged["comparisonCoordinates"] = []
        forged["changeOverview"] = {"summary": None, "fileCount": 999}
        forged["changedFilesAppendix"] = []
        return FakeResponse(forged)


class FailingSynthesisModel:
    def __init__(self) -> None:
        self.calls = 0
        self.closed = False

    async def generate_structured_output(self, messages, structured_model):  # type: ignore[no-untyped-def]
        del messages, structured_model
        self.calls += 1
        raise RuntimeError("model unavailable")

    async def aclose(self) -> None:
        self.closed = True


class FakeSynthesisGrantManager:
    async def get(self) -> dict[str, str]:
        return {
            "grant": "wfg_synthesis",
            "gatewayUrl": "http://model.test/v1",
        }


def test_markdown_contains_every_mandatory_section() -> None:
    markdown = render_impact_report_markdown(complete_report())

    for heading in (
        "比较坐标",
        "变更概览",
        "受影响功能",
        "跨仓库影响",
        "风险和置信度",
        "代码证据",
        "建议回归测试",
        "不确定项",
        "多智能体分歧",
        "变更文件附录",
    ):
        assert f"## {heading}" in markdown


@pytest.mark.asyncio
async def test_synthesis_omits_hunks_and_restores_deterministic_manifest_fields() -> None:
    model = CapturingSynthesisModel()
    repository = FrozenRepository(
        "repo_12345678",
        "orders",
        "main",
        "a" * 40,
        "feature/a",
        "b" * 40,
        "a" * 40,
    )
    changed_file = {
        "repositoryAlias": "orders",
        "path": "src/Order.java",
        "status": "MODIFIED",
    }
    result = await AgentScopeResultSynthesizer(model).synthesize(
        {"input_data": {}},  # type: ignore[arg-type]
        [repository],
        DiffManifest(
            files=[changed_file],
            statistics={"additions": 8, "deletions": 2},
            hunks=[{"patch": "SENSITIVE_FULL_PATCH_SHOULD_NOT_ENTER_SYNTHESIS"}],
        ),
        [AnalyzerOutcome("codex", True, {"summary": "ok"}, None)],
        "run_12345678:synthesize:report",
    )

    assert "SENSITIVE_FULL_PATCH_SHOULD_NOT_ENTER_SYNTHESIS" not in str(model.messages)
    assert "不得声称未执行代码分析" in model.messages[0].get_text_content()
    assert '"successfulAnalyzerIds":["codex"]' in model.messages[1].get_text_content()
    assert result["comparisonCoordinates"] == [repository.to_dict()]
    assert result["changedFilesAppendix"] == [changed_file]
    assert result["changeOverview"] == {
        "summary": "ok",
        "fileCount": 1,
        "additions": 8,
        "deletions": 2,
    }


@pytest.mark.asyncio
async def test_report_versions_are_immutable_and_current_moves_forward() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "报告")
    first_run = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={},
    )
    first = await store.publish_report(
        first_run.task_id,
        first_run.id,
        "usr_owner",
        complete_report(),
        "# v1",
    )
    # 局部重分析在同一task下生成新run；测试直接构造存储内的新run以锁定报告版本语义。
    from dataclasses import replace
    from testagent_workflow.models import RunStatus, utc_now

    await store.update_run_status(first_run.id, RunStatus.SUCCEEDED)
    second_run = replace(first_run, id="run_reanalysis_123456", status=RunStatus.RUNNING, updated_at=utc_now())
    store._runs[second_run.id] = second_run  # noqa: SLF001 - 仅测试版本语义
    second = await store.publish_report(
        first_run.task_id,
        second_run.id,
        "usr_owner",
        complete_report(),
        "# v2",
    )

    versions = await store.list_reports(first_run.task_id, "usr_owner")
    assert (first.version, second.version) == (1, 2)
    assert [version.is_current for version in versions] == [False, True]
    assert versions[0].markdown_report == "# v1"


@pytest.mark.asyncio
async def test_reanalysis_publishes_a_composite_report_with_section_provenance() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "局部重分析")
    first_run = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={},
    )
    publisher = StoreReportPublisher(store)
    await publisher.publish(
        {
            "task_id": first_run.task_id,
            "run_id": first_run.id,
            "conversation_id": conversation.id,
            "owner_user_id": "usr_owner",
            "run_kind": "INITIAL",
            "input_data": {},
        },
        complete_report(),
        "initial:publish",
    )
    from testagent_workflow.models import RunStatus

    await store.update_run_status(first_run.id, RunStatus.SUCCEEDED)
    await store.upsert_workspace_lease(
        first_run.task_id,
        runner_id="runner-a",
        container_id="container-a",
        image_digest="analysis@sha256:" + "a" * 64,
        status="STOPPED_RETAINED",
        expires_at=datetime.now(UTC) + timedelta(hours=48),
        metadata={"runId": first_run.id},
    )
    second_run = await store.create_reanalysis_run(
        conversation.id,
        "usr_owner",
        scope_selectors=[
            {"repositoryId": "repo_orders", "kind": "SYMBOL", "value": "OrderService"}
        ],
        session_digest="a" * 64,
    )
    scoped = complete_report()
    scoped["impactedFeatures"] = [{"name": "订单撤销", "impact": "补充影响"}]

    await publisher.publish(
        {
            "task_id": second_run.task_id,
            "run_id": second_run.id,
            "conversation_id": conversation.id,
            "owner_user_id": "usr_owner",
            "run_kind": "REANALYSIS",
            "input_data": second_run.input_data,
        },
        scoped,
        "reanalysis:publish",
    )

    current = await store.get_current_report(first_run.task_id, "usr_owner")
    assert [item["name"] for item in current.structured_report["impactedFeatures"]] == [
        "订单提交",
        "订单撤销",
    ]
    revision = current.structured_report["revisionContext"]
    assert revision["baseVersion"] == 1
    assert revision["sectionChanges"][0]["action"] == "SUPPLEMENTED"
    assert revision["evidenceSource"]["runId"] == second_run.id


@pytest.mark.asyncio
async def test_report_question_uses_short_grant_and_returns_evidence_markdown() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "报告追问")
    run = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={},
    )
    report = await store.publish_report(
        run.task_id,
        run.id,
        "usr_owner",
        complete_report(),
        "# report",
    )
    platform = FakeQuestionPlatform()
    captured: dict[str, str] = {}
    question_model = FakeQuestionModel()

    def model_factory(grant: str, gateway: str, model_name: str) -> FakeQuestionModel:
        captured.update(grant=grant, gateway=gateway, model=model_name)
        return question_model

    answerer = PlatformGrantedReportQuestionAnswerer(
        platform,  # type: ignore[arg-type]
        "workflow-report-qa",
        model_factory=model_factory,
        gateway_url_override=(
            "http://127.0.0.1:8080/api/internal/platform/model-gateway/v1"
        ),
    )

    answer = await answerer.answer(
        "为什么会影响订单校验？",
        report,
        ReportQuestionContext("usr_owner", "a" * 64, "msg_12345678"),
    )

    assert answer.startswith("订单校验分支发生变化。")
    assert "orders/Order.java:42" in answer
    assert "动态调用仍需运行时确认" in answer
    assert captured["gateway"].startswith("http://127.0.0.1:8080/")
    assert platform.revoked is True
    assert question_model.closed is True


@pytest.mark.asyncio
async def test_agentscope_synthesis_reuses_persisted_node_result_on_replay() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "综合幂等")
    run = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={},
    )
    model = CountingSynthesisModel()
    captured: dict[str, str] = {}

    def model_factory(grant: str, gateway: str, name: str) -> CountingSynthesisModel:
        captured.update(grant=grant, gateway=gateway, name=name)
        return model

    synthesizer = GrantBackedResultSynthesizer(
        FakeSynthesisGrantManager(),
        "workflow-synthesis",
        store,
        model_factory=model_factory,
        gateway_url_override=(
            "http://127.0.0.1:8080/api/internal/platform/model-gateway/v1"
        ),
    )
    state = {
        "task_id": run.task_id,
        "run_id": run.id,
        "input_data": {},
    }
    repositories = [
        FrozenRepository(
            repository_id="repo_12345678",
            alias="orders",
            default_branch="main",
            default_head="a" * 40,
            target_branch="feature/a",
            target_head="b" * 40,
            merge_base="a" * 40,
        )
    ]
    manifest = DiffManifest(files=[], statistics={"files": 0}, hunks=[])
    outcomes = [AnalyzerOutcome("codex", True, {"summary": "ok"}, None)]
    operation_key = f"{run.id}:synthesize:report"

    first = await synthesizer.synthesize(  # type: ignore[arg-type]
        state, repositories, manifest, outcomes, operation_key
    )
    replay = await synthesizer.synthesize(  # type: ignore[arg-type]
        state, repositories, manifest, outcomes, operation_key
    )

    assert replay == first
    assert model.calls == 1
    assert model.closed is True
    assert captured["gateway"].startswith("http://127.0.0.1:8080/")


@pytest.mark.asyncio
async def test_synthesis_failure_falls_back_to_successful_analyzer_evidence() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "综合降级")
    run = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={},
    )
    model = FailingSynthesisModel()
    synthesizer = GrantBackedResultSynthesizer(
        FakeSynthesisGrantManager(),
        "workflow-synthesis",
        store,
        model_factory=lambda grant, gateway, name: model,
    )
    repository = FrozenRepository(
        "repo_12345678",
        "orders",
        "main",
        "a" * 40,
        "feature/a",
        "b" * 40,
        "a" * 40,
    )
    manifest = DiffManifest(
        files=[{"repositoryAlias": "orders", "path": "Order.java", "status": "MODIFIED"}],
        statistics={"files": 1, "additions": 3, "deletions": 1},
        hunks=[],
    )
    outcome = AnalyzerOutcome(
        "codex",
        True,
        {
            "summary": "订单校验变化",
            "impactedFeatures": [{"name": "订单提交", "analyzerId": "forged"}],
            "crossRepositoryImpacts": [],
            "risks": [{"level": "MEDIUM", "confidence": 0.7}],
            "codeEvidence": [{"repositoryAlias": "orders", "path": "Order.java"}],
            "recommendedRegressionTests": ["订单提交回归"],
            "uncertainties": ["动态分支待确认"],
        },
        None,
    )

    result = await synthesizer.synthesize(  # type: ignore[arg-type]
        {"run_id": run.id, "input_data": {}},
        [repository],
        manifest,
        [outcome],
        f"{run.id}:synthesize:report",
    )

    assert model.calls == 3
    assert model.closed is True
    assert result["_synthesisFallback"] is True
    assert result["impactedFeatures"][0]["analyzerId"] == "codex"
    assert result["codeEvidence"][0]["path"] == "Order.java"
    assert result["changedFilesAppendix"] == manifest.files
    assert "综合不可用" in str(result["uncertainties"])
