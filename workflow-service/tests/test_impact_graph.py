from __future__ import annotations

from dataclasses import dataclass, field

import pytest
from langgraph.checkpoint.memory import MemorySaver

from testagent_workflow.impact_engine import (
    AnalyzerOutcome,
    DiffManifest,
    FrozenRepository,
    ImpactGraphDependencies,
    create_impact_analysis_graph,
)


@dataclass
class FakePlatform:
    async def authorize(self, state, operation_key):  # type: ignore[no-untyped-def]
        return [{"repositoryId": item["repositoryId"]} for item in state["input_data"]["repositories"]]


@dataclass
class MissingDefaultPlatform:
    async def authorize(self, state, operation_key):  # type: ignore[no-untyped-def]
        return [
            {
                "repositoryId": "repo_12345678",
                "defaultBranch": None,
                "availableBranches": ["develop", "release"],
            }
        ]


@dataclass
class FakeRunner:
    manifest: DiffManifest
    calls: list[str] = field(default_factory=list)

    async def freeze(self, state, authorized, operation_key):  # type: ignore[no-untyped-def]
        self.calls.append("freeze")
        return [
            FrozenRepository(
                repository_id=item["repositoryId"],
                alias=f"repo-{index}",
                default_branch="main",
                default_head="a" * 40,
                target_branch=item["targetBranch"],
                target_head="b" * 40,
                merge_base="a" * 40,
            )
            for index, item in enumerate(state["input_data"]["repositories"])
        ]

    async def build_manifest(self, state, repositories, operation_key):  # type: ignore[no-untyped-def]
        self.calls.append("manifest")
        return self.manifest

    async def resolve_scope(self, state, repositories, operation_key):  # type: ignore[no-untyped-def]
        self.calls.append("resolve_scope")
        return {
            "resolvedSelectors": [],
            "requiredInput": [
                {
                    "selector": state["input_data"]["scopeSelectors"][0],
                    "reason": "AMBIGUOUS",
                    "candidatePaths": ["a/OrderService.java", "b/OrderService.java"],
                }
            ],
        }

    async def stop_and_retain(self, state, operation_key):  # type: ignore[no-untyped-def]
        self.calls.append("retain")


@dataclass
class FakeAnalyzers:
    outcomes: dict[str, AnalyzerOutcome]
    called: list[str] = field(default_factory=list)

    async def analyze(self, analyzer_id, state, repositories, manifest, operation_key):  # type: ignore[no-untyped-def]
        self.called.append(analyzer_id)
        return self.outcomes[analyzer_id]


@dataclass
class FakeSynthesizer:
    async def synthesize(self, state, repositories, manifest, outcomes, operation_key):  # type: ignore[no-untyped-def]
        return {
            "comparisonCoordinates": [item.to_dict() for item in repositories],
            "changeOverview": {"fileCount": manifest.file_count},
            "impactedFeatures": ["订单提交"],
            "crossRepositoryImpacts": [],
            "risks": [{"level": "MEDIUM", "confidence": 0.8}],
            "codeEvidence": [],
            "recommendedRegressionTests": ["订单提交回归"],
            "uncertainties": [],
            "analyzerDisagreements": [],
            "changedFilesAppendix": [item for item in manifest.files],
        }


@dataclass
class FallbackSynthesizer(FakeSynthesizer):
    async def synthesize(self, state, repositories, manifest, outcomes, operation_key):  # type: ignore[no-untyped-def]
        report = await super().synthesize(
            state, repositories, manifest, outcomes, operation_key
        )
        report["_synthesisFallback"] = True
        return report


@dataclass
class FakeReports:
    reports: list[dict] = field(default_factory=list)

    async def publish(self, state, report, operation_key):  # type: ignore[no-untyped-def]
        self.reports.append(report)
        return {"reportVersionId": "report_v1", "version": 1}


def initial_state(analyzer_ids: list[str]) -> dict:
    return {
        "task_id": "task_12345678",
        "run_id": "run_12345678",
        "conversation_id": "conv_12345678",
        "owner_user_id": "usr_12345678",
        "input_data": {
            "repositories": [
                {"repositoryId": "repo_12345678", "targetBranch": "feature/impact"},
            ],
            "mode": "REVIEW" if len(analyzer_ids) > 1 else "SINGLE",
            "analyzerIds": analyzer_ids,
        },
    }


@pytest.mark.asyncio
async def test_empty_diff_succeeds_without_calling_code_analyzer() -> None:
    runner = FakeRunner(DiffManifest(files=[], statistics={"files": 0}, hunks=[]))
    analyzers = FakeAnalyzers({})
    reports = FakeReports()
    graph = create_impact_analysis_graph(
        ImpactGraphDependencies(FakePlatform(), runner, analyzers, FakeSynthesizer(), reports),
        checkpointer=MemorySaver(),
    )

    result = await graph.ainvoke(
        initial_state(["codex"]),
        {"configurable": {"thread_id": "task_12345678", "checkpoint_ns": "run_12345678"}},
    )

    assert result["final_status"] == "SUCCEEDED"
    assert analyzers.called == []
    assert reports.reports[0]["changeOverview"]["fileCount"] == 0
    assert runner.calls[-1] == "retain"


@pytest.mark.asyncio
async def test_one_analyzer_failure_publishes_partial_report_with_all_outcomes() -> None:
    manifest = DiffManifest(
        files=[{"repositoryAlias": "repo-0", "path": "src/Order.java", "status": "MODIFIED"}],
        statistics={"files": 1, "additions": 8, "deletions": 2},
        hunks=[{"path": "src/Order.java", "patch": "@@ -1 +1 @@"}],
    )
    runner = FakeRunner(manifest)
    analyzers = FakeAnalyzers(
        {
            "codex": AnalyzerOutcome("codex", True, {"impactedFeatures": ["订单提交"]}, None),
            "opencode": AnalyzerOutcome("opencode", False, None, "MODEL_TIMEOUT"),
        }
    )
    reports = FakeReports()
    graph = create_impact_analysis_graph(
        ImpactGraphDependencies(FakePlatform(), runner, analyzers, FakeSynthesizer(), reports)
    )

    result = await graph.ainvoke(initial_state(["codex", "opencode"]))

    assert result["final_status"] == "PARTIAL_FAILED"
    assert set(analyzers.called) == {"codex", "opencode"}
    assert len(result["analyzer_outcomes"]) == 2
    assert result["report_reference"]["reportVersionId"] == "report_v1"


@pytest.mark.asyncio
async def test_synthesis_fallback_keeps_report_but_marks_run_partial() -> None:
    manifest = DiffManifest(
        files=[{"repositoryAlias": "repo-0", "path": "src/Order.java", "status": "MODIFIED"}],
        statistics={"files": 1},
        hunks=[],
    )
    reports = FakeReports()
    graph = create_impact_analysis_graph(
        ImpactGraphDependencies(
            FakePlatform(),
            FakeRunner(manifest),
            FakeAnalyzers({
                "codex": AnalyzerOutcome("codex", True, {"summary": "ok"}, None)
            }),
            FallbackSynthesizer(),
            reports,
        )
    )

    result = await graph.ainvoke(initial_state(["codex"]))

    assert result["final_status"] == "PARTIAL_FAILED"
    assert len(reports.reports) == 1
    assert "_synthesisFallback" not in reports.reports[0]


@pytest.mark.asyncio
async def test_all_analyzers_failing_marks_run_failed_without_report() -> None:
    manifest = DiffManifest(
        files=[{"repositoryAlias": "repo-0", "path": "a.py", "status": "MODIFIED"}],
        statistics={"files": 1},
        hunks=[],
    )
    reports = FakeReports()
    graph = create_impact_analysis_graph(
        ImpactGraphDependencies(
            FakePlatform(),
            FakeRunner(manifest),
            FakeAnalyzers({"codex": AnalyzerOutcome("codex", False, None, "BROKEN")}),
            FakeSynthesizer(),
            reports,
        )
    )

    result = await graph.ainvoke(initial_state(["codex"]))

    assert result["final_status"] == "FAILED"
    assert result["failure_code"] == "ALL_ANALYZERS_FAILED"
    assert reports.reports == []


@pytest.mark.asyncio
async def test_ambiguous_reanalysis_scope_waits_for_agui_input_without_analyzing() -> None:
    runner = FakeRunner(
        DiffManifest(
            files=[{"repositoryAlias": "repo-0", "path": "a.py", "status": "MODIFIED"}],
            statistics={"files": 1},
            hunks=[],
        )
    )
    analyzers = FakeAnalyzers({})
    graph = create_impact_analysis_graph(
        ImpactGraphDependencies(
            MissingDefaultPlatform(), runner, analyzers, FakeSynthesizer(), FakeReports()
        )
    )
    state = initial_state(["codex"])
    state["run_kind"] = "REANALYSIS"
    state["input_data"]["scopeSelectors"] = [
        {"repositoryId": "repo_12345678", "kind": "SYMBOL", "value": "OrderService"}
    ]

    result = await graph.ainvoke(state)

    assert result["final_status"] == "WAITING_INPUT"
    assert result["required_scope_input"][0]["reason"] == "AMBIGUOUS"
    assert analyzers.called == []
    assert runner.calls == ["freeze", "resolve_scope", "retain"]


@pytest.mark.asyncio
async def test_missing_default_branch_waits_for_baseline_before_creating_workspace() -> None:
    runner = FakeRunner(DiffManifest(files=[], statistics={"files": 0}, hunks=[]))
    analyzers = FakeAnalyzers({})
    graph = create_impact_analysis_graph(
        ImpactGraphDependencies(
            MissingDefaultPlatform(), runner, analyzers, FakeSynthesizer(), FakeReports()
        )
    )

    result = await graph.ainvoke(initial_state(["codex"]))

    assert result["final_status"] == "WAITING_INPUT"
    assert result["required_baseline_input"] == [
        {
            "repositoryId": "repo_12345678",
            "availableBranches": ["develop", "release"],
        }
    ]
    assert runner.calls == []
    assert analyzers.called == []
