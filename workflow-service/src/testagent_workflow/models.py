"""工作流服务跨 API、编排与存储层共享的稳定模型。"""

from __future__ import annotations

from datetime import UTC, datetime
from enum import StrEnum
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


class AnalysisMode(StrEnum):
    SINGLE = "SINGLE"
    REVIEW = "REVIEW"


class RunStatus(StrEnum):
    QUEUED = "QUEUED"
    RUNNING = "RUNNING"
    WAITING_INPUT = "WAITING_INPUT"
    SUCCEEDED = "SUCCEEDED"
    PARTIAL_FAILED = "PARTIAL_FAILED"
    FAILED = "FAILED"
    CANCELED = "CANCELED"

    @property
    def active(self) -> bool:
        return self in {self.QUEUED, self.RUNNING, self.WAITING_INPUT}


class WorkspaceStatus(StrEnum):
    NONE = "NONE"
    PROVISIONING = "PROVISIONING"
    ACTIVE = "ACTIVE"
    STOPPED_RETAINED = "STOPPED_RETAINED"
    RESUMING = "RESUMING"
    EXPIRED = "EXPIRED"
    CLEANUP_FAILED = "CLEANUP_FAILED"


class RepositorySelection(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    repository_id: str = Field(alias="repositoryId", min_length=8, max_length=128)
    target_branch: str = Field(alias="targetBranch", min_length=1, max_length=255)
    baseline_branch: str | None = Field(default=None, alias="baselineBranch", max_length=255)

    @field_validator("repository_id", "target_branch")
    @classmethod
    def strip_text(cls, value: str) -> str:
        stripped = value.strip()
        if not stripped:
            raise ValueError("字段不能为空")
        return stripped

    @field_validator("baseline_branch")
    @classmethod
    def strip_optional_text(cls, value: str | None) -> str | None:
        if value is None:
            return None
        stripped = value.strip()
        return stripped or None


class ScopeSelector(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    repository_id: str = Field(alias="repositoryId", min_length=8, max_length=128)
    kind: Literal["PROGRAM", "MODULE", "DIRECTORY", "FILE", "SYMBOL"]
    value: str = Field(min_length=1, max_length=1024)


class ImpactAnalysisInput(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    repositories: list[RepositorySelection] = Field(min_length=1)
    mode: AnalysisMode = AnalysisMode.SINGLE
    analyzer_ids: list[str] = Field(default_factory=lambda: ["codex"], alias="analyzerIds", min_length=1, max_length=3)
    scope_selectors: list[ScopeSelector] = Field(default_factory=list, alias="scopeSelectors")

    @field_validator("analyzer_ids")
    @classmethod
    def normalize_analyzers(cls, values: list[str]) -> list[str]:
        normalized = [value.strip().lower() for value in values]
        if any(not value for value in normalized):
            raise ValueError("智能体ID不能为空")
        if len(set(normalized)) != len(normalized):
            raise ValueError("智能体ID不能重复")
        if not set(normalized) <= {"codex", "opencode"}:
            raise ValueError("包含未注册的代码智能体")
        return normalized

    @model_validator(mode="after")
    def validate_mode(self) -> "ImpactAnalysisInput":
        if self.mode is AnalysisMode.SINGLE and len(self.analyzer_ids) != 1:
            raise ValueError("单智能体模式必须且只能选择一个智能体")
        if self.mode is AnalysisMode.REVIEW and len(self.analyzer_ids) < 2:
            raise ValueError("复核模式至少选择两个智能体")
        repository_ids = [value.repository_id for value in self.repositories]
        if len(set(repository_ids)) != len(repository_ids):
            raise ValueError("同一个代码库不能重复选择")
        return self


def utc_now() -> datetime:
    return datetime.now(UTC)


JsonObject = dict[str, Any]
