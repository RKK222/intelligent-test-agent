"""工作流注册表和受约束的意图路由。"""

from __future__ import annotations

from collections.abc import Awaitable, Callable, Iterable
from dataclasses import dataclass, field
from types import MappingProxyType
from typing import Any, Literal, Mapping


RiskLevel = Literal["READ_ONLY", "CONTROLLED_WRITE", "DANGEROUS"]


@dataclass(frozen=True, slots=True)
class WorkflowDefinition:
    """一个可执行工作流的代码级、版本化契约。"""

    id: str
    version: str
    display_name: str
    intent_examples: tuple[str, ...]
    required_permissions: tuple[str, ...]
    risk_level: RiskLevel
    capabilities: tuple[str, ...]
    requires_sandbox: bool
    input_schema: Mapping[str, Any]
    ui_schema: Mapping[str, Any]
    output_schema: Mapping[str, Any]
    graph_factory: Callable[..., object] = field(repr=False, compare=False)


@dataclass(frozen=True, slots=True)
class IntentDecision:
    """AgentScope分类结果的稳定内部表示。"""

    intent_id: str
    confidence: float
    slots: Mapping[str, Any]
    missing_fields: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class IntentInvocationContext:
    """一次意图识别的当前用户会话及短期模型委托范围。"""

    user_id: str
    session_digest: str
    task_id: str
    run_id: str


class UnknownWorkflowError(RuntimeError):
    """模型选择了未注册或未启用的工作流。"""


class WorkflowRegistry:
    """只允许显式注册，禁止运行时按模型文本加载代码。"""

    def __init__(self, definitions: Iterable[WorkflowDefinition] = ()) -> None:
        self._definitions: dict[str, WorkflowDefinition] = {}
        for definition in definitions:
            self.register(definition)

    def register(self, definition: WorkflowDefinition) -> None:
        if definition.id in self._definitions:
            raise ValueError(f"重复的工作流ID: {definition.id}")
        self._definitions[definition.id] = definition

    def ids(self) -> tuple[str, ...]:
        return tuple(self._definitions)

    def get(self, workflow_id: str) -> WorkflowDefinition:
        try:
            return self._definitions[workflow_id]
        except KeyError as exception:
            raise UnknownWorkflowError(f"未注册的工作流意图: {workflow_id}") from exception

    def definitions(self) -> Mapping[str, WorkflowDefinition]:
        return MappingProxyType(self._definitions)


IntentClassifier = Callable[
    [str, tuple[str, ...], IntentInvocationContext],
    Awaitable[IntentDecision],
]


class RegisteredIntentRouter:
    """将注册ID作为硬白名单包裹在AgentScope分类器之外。"""

    def __init__(self, registry: WorkflowRegistry, classifier: IntentClassifier) -> None:
        self._registry = registry
        self._classifier = classifier

    async def classify(
        self,
        message: str,
        context: IntentInvocationContext,
    ) -> IntentDecision:
        decision = await self._classifier(message, self._registry.ids(), context)
        self._registry.get(decision.intent_id)
        if not 0.0 <= decision.confidence <= 1.0:
            raise ValueError("意图置信度必须位于0到1之间")
        return decision
