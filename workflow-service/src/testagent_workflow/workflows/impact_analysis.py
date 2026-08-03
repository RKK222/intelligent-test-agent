"""代码变动影响分析工作流定义。"""

from __future__ import annotations

from testagent_workflow.impact_engine import create_impact_analysis_graph
from testagent_workflow.models import ImpactAnalysisInput
from testagent_workflow.registry import WorkflowDefinition
from testagent_workflow.reports import ImpactReportOutput


WORKFLOW_ID = "code-change-impact-analysis"
WORKFLOW_VERSION = "1.0.0"


def impact_analysis_definition() -> WorkflowDefinition:
    """返回首期唯一启用的工作流契约。"""

    return WorkflowDefinition(
        id=WORKFLOW_ID,
        version=WORKFLOW_VERSION,
        display_name="代码变动影响分析",
        intent_examples=(
            "分析这个分支的代码变动影响",
            "这些仓库的修改会影响哪些功能",
            "帮我做代码影响分析",
        ),
        required_permissions=("repository:read",),
        risk_level="READ_ONLY",
        capabilities=("git:read", "model:invoke", "report:write"),
        requires_sandbox=True,
        input_schema=ImpactAnalysisInput.model_json_schema(by_alias=True),
        ui_schema={
            "layout": "impact-analysis",
            "repositorySelector": {"multiple": True, "groupBy": "application"},
            "analyzerSelector": {"default": ["codex"], "maximum": 3},
        },
        output_schema=ImpactReportOutput.model_json_schema(by_alias=True),
        graph_factory=create_impact_analysis_graph,
    )
