"""记忆服务窄 HTTP 合同；不暴露 Mem0 上游类型。"""

from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, SecretStr, field_validator, model_validator


ENTITY_PATTERN = r"^[A-Za-z0-9][A-Za-z0-9:_-]{0,127}$"
MEMORY_ID_PATTERN = r"^[0-9a-fA-F-]{32,36}$"
TASK_TYPES = {
    "GENERAL",
    "TEST_CASE_GENERATION",
    "TEST_DATA_PREPARATION",
    "REQUIREMENT_ANALYSIS",
    "TEST_PLAN_DESIGN",
    "DEFECT_ANALYSIS",
    "ROOT_CAUSE_ANALYSIS",
    "AUTOMATION_TESTING",
    "RISK_ANALYSIS",
    "TEST_REPORTING",
    "RESULT_ACCEPTANCE",
}
SCOPES = {"PERSONAL_GLOBAL", "PERSONAL_APPLICATION", "TEAM_APPLICATION"}


class CamelModel(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="forbid")


class AddDocumentRequest(CamelModel):
    content: str = Field(min_length=1)
    user_id: str | None = Field(default=None, alias="userId", pattern=ENTITY_PATTERN)
    agent_id: str | None = Field(default=None, alias="agentId", pattern=ENTITY_PATTERN)
    application_id: str | None = Field(
        default=None, alias="applicationId", pattern=ENTITY_PATTERN
    )
    task_types: list[str] = Field(default_factory=lambda: ["GENERAL"], alias="taskTypes")
    metadata: dict[str, Any] = Field(default_factory=dict)

    @model_validator(mode="after")
    def exactly_one_owner(self) -> "AddDocumentRequest":
        if bool(self.user_id) == bool(self.agent_id):
            raise ValueError("userId 与 agentId 必须且只能提供一个")
        return self

    @field_validator("task_types")
    @classmethod
    def valid_tasks(cls, values: list[str]) -> list[str]:
        normalized = list(dict.fromkeys(values or ["GENERAL"]))
        if len(normalized) > 11 or any(value not in TASK_TYPES for value in normalized):
            raise ValueError("taskTypes 包含未登记类型")
        return normalized


class UpdateDocumentRequest(CamelModel):
    content: str = Field(min_length=1)
    metadata: dict[str, Any] = Field(default_factory=dict)


class SearchRequest(CamelModel):
    query: str = Field(min_length=1, max_length=8_000)
    user_id: str | None = Field(default=None, alias="userId", pattern=ENTITY_PATTERN)
    agent_id: str | None = Field(default=None, alias="agentId", pattern=ENTITY_PATTERN)
    application_id: str | None = Field(
        default=None, alias="applicationId", pattern=ENTITY_PATTERN
    )
    scope: str | None = None
    top_k: int = Field(default=10, alias="topK", ge=1, le=50)
    threshold: float = Field(default=0.1, ge=0.0, le=1.0)

    @model_validator(mode="after")
    def exactly_one_owner(self) -> "SearchRequest":
        if bool(self.user_id) == bool(self.agent_id):
            raise ValueError("userId 与 agentId 必须且只能提供一个")
        if self.scope is not None and self.scope not in SCOPES:
            raise ValueError("scope 未登记")
        return self


class ExtractionMessage(CamelModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=100_000)


class ExtractRequest(CamelModel):
    model: str = Field(pattern=r"^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$")
    model_grant: SecretStr = Field(alias="modelGrant")
    user_id: str = Field(alias="userId", pattern=ENTITY_PATTERN)
    run_id: str = Field(alias="runId", pattern=ENTITY_PATTERN)
    session_id: str = Field(alias="sessionId", pattern=ENTITY_PATTERN)
    application_id: str | None = Field(
        default=None, alias="applicationId", pattern=ENTITY_PATTERN
    )
    task_type: str = Field(default="GENERAL", alias="taskType")
    messages: list[ExtractionMessage] = Field(min_length=1, max_length=20)

    @field_validator("task_type")
    @classmethod
    def valid_task(cls, value: str) -> str:
        if value not in TASK_TYPES:
            raise ValueError("taskType 未登记")
        return value


class ExtractedCandidate(CamelModel):
    content: str = Field(min_length=1, max_length=8_000)
    scope_suggestion: str = Field(alias="scopeSuggestion")
    task_types: list[str] = Field(alias="taskTypes", min_length=1, max_length=11)
    explicit: bool
    temporary: bool
    replaces_existing: bool = Field(default=False, alias="replacesExisting")
    confidence: float = Field(ge=0.0, le=1.0)
    reason: str = Field(min_length=1, max_length=500)

    @field_validator("scope_suggestion")
    @classmethod
    def valid_scope(cls, value: str) -> str:
        if value not in SCOPES:
            raise ValueError("scopeSuggestion 未登记")
        return value

    @field_validator("task_types")
    @classmethod
    def valid_tasks(cls, values: list[str]) -> list[str]:
        if any(value not in TASK_TYPES for value in values):
            raise ValueError("候选 taskTypes 未登记")
        return list(dict.fromkeys(values))
