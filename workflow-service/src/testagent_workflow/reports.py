"""场景1结构化报告校验、AgentScope综合与Markdown渲染。"""

from __future__ import annotations

import asyncio
import json
from typing import Any, Protocol

from agentscope.message import Msg
from pydantic import BaseModel, ConfigDict, Field

from testagent_workflow.agui import AgUiEventType
from testagent_workflow.application import ReportQuestionContext
from testagent_workflow.impact_engine import AnalyzerOutcome, DiffManifest, FrozenRepository, ImpactState
from testagent_workflow.intent import (
    ModelFactory,
    close_platform_chat_model,
    create_platform_chat_model,
)
from testagent_workflow.platform import PlatformCapabilityClient, PlatformRequestIdentity
from testagent_workflow.store import ReportVersion


class ImpactReportOutput(BaseModel):
    """智能体综合结果的稳定顶层契约；明细保持JSON以兼容多语言仓库。"""

    model_config = ConfigDict(populate_by_name=True)

    comparison_coordinates: list[dict[str, Any]] = Field(alias="comparisonCoordinates")
    change_overview: dict[str, Any] = Field(alias="changeOverview")
    impacted_features: list[dict[str, Any]] = Field(alias="impactedFeatures")
    cross_repository_impacts: list[dict[str, Any]] = Field(alias="crossRepositoryImpacts")
    risks: list[dict[str, Any]]
    code_evidence: list[dict[str, Any]] = Field(alias="codeEvidence")
    recommended_regression_tests: list[Any] = Field(alias="recommendedRegressionTests")
    uncertainties: list[Any]
    analyzer_disagreements: list[dict[str, Any]] = Field(alias="analyzerDisagreements")
    changed_files_appendix: list[dict[str, Any]] = Field(alias="changedFilesAppendix")


class StructuredModel(Protocol):
    async def generate_structured_output(
        self,
        messages: list[Msg],
        structured_model: type[BaseModel],
    ) -> Any: ...


class ReportQuestionOutput(BaseModel):
    answer: str = Field(min_length=1, max_length=20_000)
    evidence: list[str] = Field(default_factory=list, max_length=100)
    limitations: list[str] = Field(default_factory=list, max_length=100)


class PlatformGrantedReportQuestionAnswerer:
    """工作区过期后仍仅依据长期保存报告回答，不触碰源码或Runner。"""

    def __init__(
        self,
        platform: PlatformCapabilityClient,
        model_name: str,
        *,
        model_factory: ModelFactory = create_platform_chat_model,
        gateway_url_override: str | None = None,
    ) -> None:
        self._platform = platform
        self._model_name = model_name
        self._model_factory = model_factory
        self._gateway_url_override = gateway_url_override

    async def answer(
        self,
        question: str,
        report: ReportVersion,
        context: ReportQuestionContext,
    ) -> str:
        identity = PlatformRequestIdentity(context.user_id, context.session_digest)
        qa_run_id = f"qarun_{context.request_id.removeprefix('msg_')}"
        grant = await self._platform.issue_model_grant(
            identity,
            task_id=report.task_id,
            run_id=qa_run_id,
            analyzer_ids=["agentscope-report-qa"],
        )
        model: StructuredModel | None = None
        try:
            model = self._model_factory(
                str(grant["grant"]),
                self._gateway_url_override or str(grant["gatewayUrl"]),
                self._model_name,
            )
            payload = {
                "reportVersion": report.version,
                "structuredReport": report.structured_report,
                "question": question,
            }
            response = await model.generate_structured_output(
                [
                    Msg(
                        name="report-question-answerer",
                        role="system",
                        content=[{
                            "type": "text",
                            "text": (
                                "只根据给定的代码影响分析报告回答问题。不得声称读取了源码；"
                                "证据不足时必须写入limitations，并优先引用报告中的仓库、文件和符号。"
                            ),
                        }],
                    ),
                    Msg(
                        name="user",
                        role="user",
                        content=[{
                            "type": "text",
                            "text": json.dumps(payload, ensure_ascii=False, separators=(",", ":")),
                        }],
                    ),
                ],
                ReportQuestionOutput,
            )
            output = ReportQuestionOutput.model_validate(response.content)
            lines = [output.answer]
            if output.evidence:
                lines.extend(["", "**报告证据**", *[f"- {value}" for value in output.evidence]])
            if output.limitations:
                lines.extend(["", "**限制与不确定项**", *[f"- {value}" for value in output.limitations]])
            return "\n".join(lines)
        finally:
            try:
                if model is not None:
                    await close_platform_chat_model(model)
            finally:
                await self._platform.revoke_model_grant(
                    identity,
                    task_id=report.task_id,
                    run_id=qa_run_id,
                    grant_id=str(grant["grantId"]),
                )


class AgentScopeResultSynthesizer:
    """保留每个分析器结论，并让AgentScope只负责共识/分歧综合。"""

    def __init__(self, model: StructuredModel) -> None:
        self._model = model

    async def synthesize(
        self,
        state: ImpactState,
        repositories: list[FrozenRepository],
        manifest: DiffManifest,
        outcomes: list[AnalyzerOutcome],
        operation_key: str,
    ) -> dict[str, Any]:
        sampled_files = list(manifest.files[:1000])
        payload = {
            "operationKey": operation_key,
            "comparisonCoordinates": [value.to_dict() for value in repositories],
            # 完整hunk由代码智能体在只读仓库中分析；综合请求只携带有界元数据，
            # 避免大仓库绕过模型网关16 MiB请求上限。完整文件附录在模型返回后确定性回填。
            "manifest": {
                "statistics": manifest.statistics,
                "files": sampled_files,
                "filesTruncated": len(sampled_files) < len(manifest.files),
                "totalFiles": len(manifest.files),
            },
            "analyzerResults": [value.to_dict() for value in outcomes],
            "analysisExecution": {
                "successfulAnalyzerIds": [
                    value.analyzer_id
                    for value in outcomes
                    if value.succeeded and value.result is not None
                ],
                "failedAnalyzerIds": [value.analyzer_id for value in outcomes if not value.succeeded],
            },
            "scopeSelectors": state["input_data"].get("scopeSelectors", []),
        }
        messages = [
            Msg(
                name="impact-synthesizer",
                role="system",
                content=[
                    {
                        "type": "text",
                        "text": (
                            "综合多个代码分析器结果。只能依据给定代码证据；明确区分共识、分歧和无法裁决项。"
                            "analysisExecution.successfulAnalyzerIds中的智能体已经执行并返回代码证据，"
                            "不得声称未执行代码分析、未读取变更或未使用分析工具。"
                            "证据仅包含文档或配置时可以说明未观察到运行时代码变化，但不能否定分析过程。"
                            "不得丢失比较坐标和变更文件附录，所有风险必须给出0到1置信度。"
                        ),
                    }
                ],
            ),
            Msg(
                name="workflow",
                role="user",
                content=[
                    {
                        "type": "text",
                        "text": json.dumps(payload, ensure_ascii=False, separators=(",", ":")),
                    }
                ],
            ),
        ]
        response = await self._model.generate_structured_output(messages, ImpactReportOutput)
        report = ImpactReportOutput.model_validate(response.content)
        value = report.model_dump(mode="json", by_alias=True)
        value["comparisonCoordinates"] = [item.to_dict() for item in repositories]
        value["changedFilesAppendix"] = list(manifest.files)
        overview = dict(value["changeOverview"])
        if not isinstance(overview.get("summary"), str) or not overview["summary"].strip():
            overview["summary"] = _deterministic_change_summary(outcomes, len(manifest.files))
        value["changeOverview"] = {
            **overview,
            **dict(manifest.statistics),
            "fileCount": len(manifest.files),
        }
        return value


def _deterministic_change_summary(outcomes: list[AnalyzerOutcome], file_count: int) -> str:
    summaries = [
        str(value.result.get("summary", "")).strip()
        for value in outcomes
        if value.succeeded and value.result is not None
    ]
    summaries = [value for value in summaries if value]
    if len(summaries) == 1:
        return summaries[0]
    return f"已综合{len(summaries)}个代码分析器结果，涉及{file_count}个变更文件。"


class GrantBackedResultSynthesizer:
    """复用当前run的短期平台grant执行AgentScope结论综合。"""

    def __init__(
        self,
        grant_manager: Any,
        model_name: str,
        store: Any,
        *,
        model_factory: ModelFactory = create_platform_chat_model,
        gateway_url_override: str | None = None,
    ) -> None:
        self._grant_manager = grant_manager
        self._model_name = model_name
        self._store = store
        self._model_factory = model_factory
        self._gateway_url_override = gateway_url_override

    async def synthesize(
        self,
        state: ImpactState,
        repositories: list[FrozenRepository],
        manifest: DiffManifest,
        outcomes: list[AnalyzerOutcome],
        operation_key: str,
    ) -> dict[str, Any]:
        cached = await self._store.get_node_operation_result(
            state["run_id"],
            "synthesize",
            operation_key,
        )
        if cached is not None:
            return cached
        grant = await self._grant_manager.get()
        model = self._model_factory(
            str(grant["grant"]),
            self._gateway_url_override or str(grant["gatewayUrl"]),
            self._model_name,
        )
        result: dict[str, Any] | None = None
        try:
            for attempt in range(3):
                try:
                    result = await AgentScopeResultSynthesizer(model).synthesize(
                        state,
                        repositories,
                        manifest,
                        outcomes,
                        operation_key,
                    )
                    break
                except Exception:
                    if attempt < 2:
                        await asyncio.sleep(0.1 * (attempt + 1))
        finally:
            await close_platform_chat_model(model)
        if result is None:
            # 代码智能体已有成功证据时，综合模型不可用不能抹掉整次任务。
            # 降级报告逐项标注analyzerId，便于人工裁决且不伪造共识。
            result = _fallback_report(repositories, manifest, outcomes)
        return await self._store.save_node_operation_result(
            state["run_id"],
            "synthesize",
            operation_key,
            result,
        )


def _fallback_report(
    repositories: list[FrozenRepository],
    manifest: DiffManifest,
    outcomes: list[AnalyzerOutcome],
) -> dict[str, Any]:
    successful = [value for value in outcomes if value.succeeded and value.result is not None]

    def sourced_dicts(section: str) -> list[dict[str, Any]]:
        values: list[dict[str, Any]] = []
        for outcome in successful:
            for item in list((outcome.result or {}).get(section, [])):
                if isinstance(item, dict):
                    # 来源由执行控制面写入，模型结果中的同名字段不得伪造归属。
                    values.append({**item, "analyzerId": outcome.analyzer_id})
        return values

    recommended: list[Any] = []
    uncertainties: list[Any] = ["AgentScope综合不可用，当前报告保留各代码智能体原始结论，尚未形成自动共识。"]
    summaries: list[dict[str, str]] = []
    disagreements: list[dict[str, Any]] = []
    for outcome in outcomes:
        if outcome.succeeded and outcome.result is not None:
            summary = str(outcome.result.get("summary", "未提供摘要"))
            summaries.append({"analyzerId": outcome.analyzer_id, "summary": summary})
            disagreements.append(
                {
                    "analyzerId": outcome.analyzer_id,
                    "status": "SUCCEEDED_UNSYNTHESIZED",
                    "conclusion": summary,
                }
            )
            recommended.extend(
                {
                    "analyzerId": outcome.analyzer_id,
                    "value": item,
                }
                for item in list(outcome.result.get("recommendedRegressionTests", []))
            )
            uncertainties.extend(
                {
                    "analyzerId": outcome.analyzer_id,
                    "value": item,
                }
                for item in list(outcome.result.get("uncertainties", []))
            )
        else:
            disagreements.append(
                {
                    "analyzerId": outcome.analyzer_id,
                    "status": "FAILED",
                    "errorCode": outcome.error_code or "ANALYZER_FAILED",
                }
            )
    return {
        "_synthesisFallback": True,
        "comparisonCoordinates": [value.to_dict() for value in repositories],
        "changeOverview": {
            "summary": "AgentScope综合失败，以下内容按代码智能体来源原样归集。",
            "analyzerSummaries": summaries,
            **dict(manifest.statistics),
            "fileCount": len(manifest.files),
        },
        "impactedFeatures": sourced_dicts("impactedFeatures"),
        "crossRepositoryImpacts": sourced_dicts("crossRepositoryImpacts"),
        "risks": sourced_dicts("risks"),
        "codeEvidence": sourced_dicts("codeEvidence"),
        "recommendedRegressionTests": recommended,
        "uncertainties": uncertainties,
        "analyzerDisagreements": disagreements,
        "changedFilesAppendix": list(manifest.files),
    }


class StoreReportPublisher:
    def __init__(self, store: Any) -> None:
        self._store = store

    async def publish(
        self,
        state: ImpactState,
        report: dict[str, Any],
        operation_key: str,
    ) -> dict[str, Any]:
        published_report = report
        if state.get("run_kind") == "REANALYSIS":
            previous = await self._store.get_current_report(
                state["task_id"],
                state["owner_user_id"],
            )
            published_report = _merge_reanalysis_report(
                previous.structured_report,
                report,
                base_version=previous.version,
                run_id=state["run_id"],
                scope_selectors=list(state["input_data"].get("scopeSelectors", [])),
            )
        version = await self._store.publish_report(
            state["task_id"],
            state["run_id"],
            state["owner_user_id"],
            published_report,
            render_impact_report_markdown(published_report),
        )
        await self._store.append_event(
            state["conversation_id"],
            AgUiEventType.CUSTOM,
            {
                "name": "workflow.report_published",
                "value": {
                    "taskId": state["task_id"],
                    "runId": state["run_id"],
                    "reportVersionId": version.id,
                    "version": version.version,
                    "operationKey": operation_key,
                },
            },
            dedup_key=f"{operation_key}:published",
        )
        return {"reportVersionId": version.id, "version": version.version}


def render_impact_report_markdown(report: dict[str, Any]) -> str:
    """固定章节顺序，便于版本比较、长期保存和离线下载。"""

    sections: list[tuple[str, Any]] = [
        ("比较坐标", report.get("comparisonCoordinates", [])),
        ("变更概览", report.get("changeOverview", {})),
        ("受影响功能", report.get("impactedFeatures", [])),
        ("跨仓库影响", report.get("crossRepositoryImpacts", [])),
        ("风险和置信度", report.get("risks", [])),
        ("代码证据", report.get("codeEvidence", [])),
        ("建议回归测试", report.get("recommendedRegressionTests", [])),
        ("不确定项", report.get("uncertainties", [])),
        ("多智能体分歧", report.get("analyzerDisagreements", [])),
        ("变更文件附录", report.get("changedFilesAppendix", [])),
    ]
    lines = ["# 代码变动影响分析报告", ""]
    if report.get("revisionContext"):
        lines.extend(["## 报告版本说明", "", *_markdown_value(report["revisionContext"]), ""])
    for title, value in sections:
        lines.extend([f"## {title}", "", *_markdown_value(value), ""])
    return "\n".join(lines).rstrip() + "\n"


def _markdown_value(value: Any) -> list[str]:
    if value in (None, [], {}):
        return ["无。"]
    if isinstance(value, list):
        return [f"- {_inline(item)}" for item in value]
    if isinstance(value, dict):
        return [f"- **{key}**：{_inline(item)}" for key, item in value.items()]
    return [str(value)]


def _inline(value: Any) -> str:
    if isinstance(value, dict):
        return "；".join(f"{key}={_inline(item)}" for key, item in value.items())
    if isinstance(value, list):
        return "、".join(_inline(item) for item in value)
    return str(value).replace("\n", " ")


def _merge_reanalysis_report(
    previous: dict[str, Any],
    scoped: dict[str, Any],
    *,
    base_version: int,
    run_id: str,
    scope_selectors: list[dict[str, Any]],
) -> dict[str, Any]:
    """保留原完整结论，将局部重分析按章节补充并标注不可变证据来源。"""

    combined = dict(previous)
    list_sections = (
        "impactedFeatures",
        "crossRepositoryImpacts",
        "risks",
        "codeEvidence",
        "recommendedRegressionTests",
        "uncertainties",
        "analyzerDisagreements",
    )
    source = {"runId": run_id, "scopeSelectors": scope_selectors}
    changed: list[dict[str, str]] = []
    for section in list_sections:
        additions = list(scoped.get(section, []))
        if not additions:
            continue
        combined[section] = [*list(previous.get(section, [])), *additions]
        changed.append({"section": section, "action": "SUPPLEMENTED"})
    combined["comparisonCoordinates"] = previous.get(
        "comparisonCoordinates", scoped.get("comparisonCoordinates", [])
    )
    combined["changedFilesAppendix"] = previous.get(
        "changedFilesAppendix", scoped.get("changedFilesAppendix", [])
    )
    combined["changeOverview"] = {
        **dict(previous.get("changeOverview", {})),
        "latestReanalysis": scoped.get("changeOverview", {}),
    }
    changed.insert(0, {"section": "changeOverview", "action": "SUPPLEMENTED"})
    combined["revisionContext"] = {
        "kind": "LOCAL_REANALYSIS",
        "baseVersion": base_version,
        "sectionChanges": changed,
        "evidenceSource": source,
    }
    return combined
