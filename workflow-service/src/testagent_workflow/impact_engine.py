"""场景1固定LangGraph拓扑及其可替换的外部能力端口。"""

from __future__ import annotations

import asyncio
from dataclasses import dataclass
from typing import Any, Protocol, TypedDict

from langgraph.graph import END, START, StateGraph
from langgraph.types import RetryPolicy


class ImpactState(TypedDict, total=False):
    task_id: str
    run_id: str
    conversation_id: str
    owner_user_id: str
    session_digest: str
    run_kind: str
    input_data: dict[str, Any]
    authorized_repositories: list[dict[str, Any]]
    required_baseline_input: list[dict[str, Any]]
    frozen_repositories: list["FrozenRepository"]
    diff_manifest: "DiffManifest"
    scope_resolution: dict[str, Any]
    required_scope_input: list[dict[str, Any]]
    analyzer_outcomes: list["AnalyzerOutcome"]
    report: dict[str, Any]
    report_reference: dict[str, Any]
    final_status: str
    failure_code: str


@dataclass(frozen=True, slots=True)
class FrozenRepository:
    repository_id: str
    alias: str
    default_branch: str
    default_head: str
    target_branch: str
    target_head: str
    merge_base: str
    missing_dependencies: tuple[dict[str, Any], ...] = ()

    def to_dict(self) -> dict[str, Any]:
        return {
            "repositoryId": self.repository_id,
            "repositoryAlias": self.alias,
            "defaultBranch": self.default_branch,
            "defaultHead": self.default_head,
            "targetBranch": self.target_branch,
            "targetHead": self.target_head,
            "mergeBase": self.merge_base,
            "missingDependencies": list(self.missing_dependencies),
        }


@dataclass(frozen=True, slots=True)
class DiffManifest:
    files: list[dict[str, Any]]
    statistics: dict[str, Any]
    hunks: list[dict[str, Any]]

    @property
    def file_count(self) -> int:
        return len(self.files)

    def to_dict(self) -> dict[str, Any]:
        return {
            "files": self.files,
            "statistics": self.statistics,
            "hunks": self.hunks,
        }


@dataclass(frozen=True, slots=True)
class AnalyzerOutcome:
    analyzer_id: str
    succeeded: bool
    result: dict[str, Any] | None
    error_code: str | None

    def to_dict(self) -> dict[str, Any]:
        return {
            "analyzerId": self.analyzer_id,
            "succeeded": self.succeeded,
            "result": self.result,
            "errorCode": self.error_code,
        }


class PlatformAuthorizationPort(Protocol):
    async def authorize(
        self,
        state: ImpactState,
        operation_key: str,
    ) -> list[dict[str, Any]]: ...


class AnalysisRunnerPort(Protocol):
    async def freeze(
        self,
        state: ImpactState,
        authorized: list[dict[str, Any]],
        operation_key: str,
    ) -> list[FrozenRepository]: ...

    async def build_manifest(
        self,
        state: ImpactState,
        repositories: list[FrozenRepository],
        operation_key: str,
    ) -> DiffManifest: ...

    async def resolve_scope(
        self,
        state: ImpactState,
        repositories: list[FrozenRepository],
        operation_key: str,
    ) -> dict[str, Any]: ...

    async def stop_and_retain(self, state: ImpactState, operation_key: str) -> None: ...


class AnalyzerPort(Protocol):
    async def analyze(
        self,
        analyzer_id: str,
        state: ImpactState,
        repositories: list[FrozenRepository],
        manifest: DiffManifest,
        operation_key: str,
    ) -> AnalyzerOutcome: ...


class SynthesisPort(Protocol):
    async def synthesize(
        self,
        state: ImpactState,
        repositories: list[FrozenRepository],
        manifest: DiffManifest,
        outcomes: list[AnalyzerOutcome],
        operation_key: str,
    ) -> dict[str, Any]: ...


class ReportPort(Protocol):
    async def publish(
        self,
        state: ImpactState,
        report: dict[str, Any],
        operation_key: str,
    ) -> dict[str, Any]: ...


class ProgressPort(Protocol):
    async def started(
        self,
        state: ImpactState,
        tool_call_id: str,
        tool_name: str,
    ) -> None: ...

    async def finished(
        self,
        state: ImpactState,
        tool_call_id: str,
        *,
        succeeded: bool,
    ) -> None: ...


class NoopProgressPort:
    async def started(self, state: ImpactState, tool_call_id: str, tool_name: str) -> None:
        del state, tool_call_id, tool_name

    async def finished(
        self,
        state: ImpactState,
        tool_call_id: str,
        *,
        succeeded: bool,
    ) -> None:
        del state, tool_call_id, succeeded


@dataclass(frozen=True, slots=True)
class ImpactGraphDependencies:
    platform: PlatformAuthorizationPort
    runner: AnalysisRunnerPort
    analyzers: AnalyzerPort
    synthesizer: SynthesisPort
    reports: ReportPort
    progress: ProgressPort = NoopProgressPort()


def create_impact_analysis_graph(
    dependencies: ImpactGraphDependencies,
    *,
    checkpointer: Any = None,
) -> Any:
    """构建固定拓扑；模型只能填充节点结果，不能修改边或执行任意工具。"""

    retry = RetryPolicy(max_attempts=3, initial_interval=0.25, max_interval=2.0, jitter=True)

    async def tracked(
        state: ImpactState,
        node_id: str,
        operation: str,
        display_name: str,
        invocation: Any,
    ) -> Any:
        tool_call_id = _operation_key(state, node_id, operation)
        await dependencies.progress.started(state, tool_call_id, display_name)
        try:
            result = await invocation()
        except Exception:
            await dependencies.progress.finished(
                state,
                tool_call_id,
                succeeded=False,
            )
            raise
        await dependencies.progress.finished(state, tool_call_id, succeeded=True)
        return result

    async def authorize(state: ImpactState) -> dict[str, Any]:
        operation_key = _operation_key(state, "authorize", "repositories")
        authorized = await tracked(
            state,
            "authorize",
            "repositories",
            "复核代码库访问权限",
            lambda: dependencies.platform.authorize(state, operation_key),
        )
        return {"authorized_repositories": authorized}

    async def freeze(state: ImpactState) -> dict[str, Any]:
        operation_key = _operation_key(state, "freeze", "coordinates")
        repositories = await tracked(
            state,
            "freeze",
            "coordinates",
            "冻结提交并准备隔离工作区",
            lambda: dependencies.runner.freeze(
                state,
                state["authorized_repositories"],
                operation_key,
            ),
        )
        return {"frozen_repositories": repositories}

    async def resolve_baselines(state: ImpactState) -> dict[str, Any]:
        # 局部重分析只能恢复首轮已经冻结的提交坐标；禁止因远端默认分支变化
        # 再次推导基线，从而悄然改变原任务的分析范围。
        if state.get("run_kind") == "REANALYSIS":
            return {
                "input_data": dict(state["input_data"]),
                "required_baseline_input": [],
            }
        authorized_by_id = {
            str(value["repositoryId"]): value for value in state["authorized_repositories"]
        }
        input_data = dict(state["input_data"])
        repositories = [dict(value) for value in input_data["repositories"]]
        required: list[dict[str, Any]] = []
        for repository in repositories:
            if repository.get("baselineBranch"):
                continue
            authorized = authorized_by_id[str(repository["repositoryId"])]
            # 生产适配器总会返回该字段；缺失字段仅为兼容旧测试或替换实现。
            if "defaultBranch" not in authorized:
                continue
            default_branch = authorized.get("defaultBranch")
            if default_branch:
                repository["baselineBranch"] = str(default_branch)
                continue
            required.append(
                {
                    "repositoryId": str(repository["repositoryId"]),
                    "availableBranches": list(authorized.get("availableBranches", [])),
                }
            )
        input_data["repositories"] = repositories
        return {"input_data": input_data, "required_baseline_input": required}

    async def wait_for_baseline_input(state: ImpactState) -> dict[str, Any]:
        # 此时尚未签发checkout ticket或创建Runner工作区，无需执行清理副作用。
        return {"final_status": "WAITING_INPUT"}

    async def manifest(state: ImpactState) -> dict[str, Any]:
        operation_key = _operation_key(state, "manifest", "joint-diff")
        value = await tracked(
            state,
            "manifest",
            "joint-diff",
            "生成确定性变更清单",
            lambda: dependencies.runner.build_manifest(
                state,
                state["frozen_repositories"],
                operation_key,
            ),
        )
        return {"diff_manifest": value}

    async def resolve_scope(state: ImpactState) -> dict[str, Any]:
        selectors = list(state["input_data"].get("scopeSelectors", []))
        if not selectors:
            return {"scope_resolution": {"resolvedSelectors": [], "requiredInput": []}}
        operation_key = _operation_key(state, "scope", "resolve")
        value = await tracked(
            state,
            "scope",
            "resolve",
            "解析局部重分析范围",
            lambda: dependencies.runner.resolve_scope(
                state,
                state["frozen_repositories"],
                operation_key,
            ),
        )
        updated_input = dict(state["input_data"])
        if value.get("resolvedSelectors"):
            updated_input["scopeSelectors"] = list(value["resolvedSelectors"])
        return {"scope_resolution": value, "input_data": updated_input}

    async def wait_for_scope_input(state: ImpactState) -> dict[str, Any]:
        operation_key = _operation_key(state, "workspace", "wait-input-retain")
        await tracked(
            state,
            "workspace",
            "wait-input-retain",
            "暂停并保留分析工作区",
            lambda: dependencies.runner.stop_and_retain(state, operation_key),
        )
        return {
            "final_status": "WAITING_INPUT",
            "required_scope_input": list(state["scope_resolution"].get("requiredInput", [])),
        }

    async def build_empty_report(state: ImpactState) -> dict[str, Any]:
        repositories = state["frozen_repositories"]
        manifest_value = state["diff_manifest"]
        return {
            "report": {
                "comparisonCoordinates": [item.to_dict() for item in repositories],
                "changeOverview": {
                    "fileCount": 0,
                    "summary": "冻结的比较范围内没有代码差异，未调用代码智能体。",
                    **manifest_value.statistics,
                },
                "impactedFeatures": [],
                "crossRepositoryImpacts": [],
                "risks": [],
                "codeEvidence": [],
                "recommendedRegressionTests": [],
                "uncertainties": [],
                "analyzerDisagreements": [],
                "changedFilesAppendix": [],
            },
            "analyzer_outcomes": [],
            "final_status": "SUCCEEDED",
        }

    async def analyze(state: ImpactState) -> dict[str, Any]:
        analyzer_ids = list(state["input_data"].get("analyzerIds", ["codex"]))

        async def invoke(analyzer_id: str) -> AnalyzerOutcome:
            operation_key = _operation_key(state, "analyze", analyzer_id)
            try:
                return await tracked(
                    state,
                    "analyze",
                    analyzer_id,
                    f"{analyzer_id} 代码影响分析",
                    lambda: dependencies.analyzers.analyze(
                        analyzer_id,
                        state,
                        state["frozen_repositories"],
                        state["diff_manifest"],
                        operation_key,
                    ),
                )
            except Exception as exception:  # 每个智能体独立失败，避免遮蔽成功结果。
                return AnalyzerOutcome(
                    analyzer_id=analyzer_id,
                    succeeded=False,
                    result=None,
                    error_code=type(exception).__name__.upper(),
                )

        outcomes = list(await asyncio.gather(*(invoke(value) for value in analyzer_ids)))
        return {"analyzer_outcomes": outcomes}

    async def synthesize(state: ImpactState) -> dict[str, Any]:
        outcomes = state["analyzer_outcomes"]
        operation_key = _operation_key(state, "synthesize", "report")
        report = await tracked(
            state,
            "synthesize",
            "report",
            "AgentScope综合共识与分歧",
            lambda: dependencies.synthesizer.synthesize(
                state,
                state["frozen_repositories"],
                state["diff_manifest"],
                outcomes,
                operation_key,
            ),
        )
        synthesis_fallback = bool(report.pop("_synthesisFallback", False))
        succeeded = sum(1 for outcome in outcomes if outcome.succeeded)
        status = (
            "SUCCEEDED"
            if succeeded == len(outcomes) and not synthesis_fallback
            else "PARTIAL_FAILED"
        )
        return {"report": report, "final_status": status}

    async def all_failed(state: ImpactState) -> dict[str, Any]:
        return {"final_status": "FAILED", "failure_code": "ALL_ANALYZERS_FAILED"}

    async def publish(state: ImpactState) -> dict[str, Any]:
        operation_key = _operation_key(state, "publish", "report")
        reference = await tracked(
            state,
            "publish",
            "report",
            "发布结构化与Markdown报告",
            lambda: dependencies.reports.publish(
                state,
                state["report"],
                operation_key,
            ),
        )
        return {"report_reference": reference}

    async def retain(state: ImpactState) -> dict[str, Any]:
        operation_key = _operation_key(state, "workspace", "stop-retain-48h")
        await tracked(
            state,
            "workspace",
            "stop-retain-48h",
            "停止容器并滚动保留工作区",
            lambda: dependencies.runner.stop_and_retain(state, operation_key),
        )
        return {}

    graph = StateGraph(ImpactState)
    graph.add_node("authorize", authorize, retry_policy=retry)
    graph.add_node("resolve_baselines", resolve_baselines)
    graph.add_node("wait_baseline_input", wait_for_baseline_input)
    graph.add_node("freeze", freeze, retry_policy=retry)
    graph.add_node("resolve_scope", resolve_scope)
    graph.add_node("wait_scope_input", wait_for_scope_input, retry_policy=retry)
    graph.add_node("manifest", manifest, retry_policy=retry)
    graph.add_node("empty_report", build_empty_report)
    graph.add_node("analyze", analyze)
    graph.add_node("synthesize", synthesize, retry_policy=retry)
    graph.add_node("all_failed", all_failed)
    graph.add_node("publish", publish, retry_policy=retry)
    graph.add_node("retain", retain, retry_policy=retry)
    graph.add_edge(START, "authorize")
    graph.add_edge("authorize", "resolve_baselines")
    graph.add_conditional_edges(
        "resolve_baselines",
        lambda state: "wait" if state.get("required_baseline_input") else "continue",
        {"wait": "wait_baseline_input", "continue": "freeze"},
    )
    graph.add_edge("wait_baseline_input", END)
    graph.add_edge("freeze", "resolve_scope")
    graph.add_conditional_edges(
        "resolve_scope",
        lambda state: "wait" if state["scope_resolution"].get("requiredInput") else "continue",
        {"wait": "wait_scope_input", "continue": "manifest"},
    )
    graph.add_edge("wait_scope_input", END)
    graph.add_conditional_edges(
        "manifest",
        lambda state: "empty" if state["diff_manifest"].file_count == 0 else "changed",
        {"empty": "empty_report", "changed": "analyze"},
    )
    graph.add_edge("empty_report", "publish")
    graph.add_conditional_edges(
        "analyze",
        lambda state: (
            "failed"
            if not any(outcome.succeeded for outcome in state["analyzer_outcomes"])
            else "synthesize"
        ),
        {"failed": "all_failed", "synthesize": "synthesize"},
    )
    graph.add_edge("synthesize", "publish")
    graph.add_edge("all_failed", "retain")
    graph.add_edge("publish", "retain")
    graph.add_edge("retain", END)
    return graph.compile(checkpointer=checkpointer, name="code-change-impact-analysis")


def _operation_key(state: ImpactState, node_id: str, operation: str) -> str:
    return f"{state['run_id']}:{node_id}:{operation}"
