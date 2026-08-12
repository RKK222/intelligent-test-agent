"""官方风格 Mem0 REST 合同；平台身份和逻辑分区是显式字段。"""

from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


ENTITY_PATTERN = r"^[A-Za-z0-9][A-Za-z0-9:_-]{0,127}$"
# Alembic `memory_operations.operation_id` 是 varchar(128)，REST 校验必须先在边界拒绝
# 更长值，不能把数据库错误包装成 503。
OPERATION_PATTERN = r"^[A-Za-z0-9][A-Za-z0-9:._-]{7,127}$"
SCOPES = {"PERSONAL_GLOBAL", "PERSONAL_APPLICATION", "TEAM_APPLICATION"}


class CamelModel(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="forbid")


class MemoryMessage(CamelModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=100_000)


class AddMemoriesRequest(CamelModel):
    messages: str | list[MemoryMessage]
    infer: bool = True
    chat_model_id: str | None = Field(
        default=None, alias="chatModelId", pattern=r"^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$"
    )
    user_id: str | None = Field(default=None, alias="userId", pattern=ENTITY_PATTERN)
    agent_id: str | None = Field(default=None, alias="agentId", pattern=ENTITY_PATTERN)
    requester_user_id: str = Field(alias="requesterUserId", pattern=ENTITY_PATTERN)
    run_id: str = Field(alias="runId", pattern=ENTITY_PATTERN)
    session_id: str = Field(alias="sessionId", pattern=ENTITY_PATTERN)
    operation_id: str = Field(alias="operationId", pattern=OPERATION_PATTERN)
    application_id: str | None = Field(
        default=None, alias="applicationId", pattern=ENTITY_PATTERN
    )
    scope: str
    metadata: dict[str, Any] = Field(default_factory=dict)

    @model_validator(mode="after")
    def valid_owner_scope(self) -> "AddMemoriesRequest":
        if bool(self.user_id) == bool(self.agent_id):
            raise ValueError("userId 与 agentId 必须且只能提供一个")
        if self.scope not in SCOPES:
            raise ValueError("scope 未登记")
        if self.scope == "PERSONAL_GLOBAL":
            if self.user_id is None or self.application_id is not None:
                raise ValueError("PERSONAL_GLOBAL owner/application 不匹配")
        elif self.scope == "PERSONAL_APPLICATION":
            if self.user_id is None or self.application_id is None:
                raise ValueError("PERSONAL_APPLICATION owner/application 不匹配")
        elif self.agent_id is None or self.application_id is None:
            raise ValueError("TEAM_APPLICATION owner/application 不匹配")
        if self.infer and self.chat_model_id is None:
            raise ValueError("infer=true 时必须指定固定 chatModelId")
        return self

    @field_validator("messages")
    @classmethod
    def bounded_messages(
        cls, value: str | list[MemoryMessage]
    ) -> str | list[MemoryMessage]:
        if isinstance(value, list) and (not value or len(value) > 100):
            raise ValueError("messages 数量必须在 1 到 100 之间")
        return value


class MutationContext(CamelModel):
    requester_user_id: str = Field(alias="requesterUserId", pattern=ENTITY_PATTERN)
    run_id: str = Field(alias="runId", pattern=ENTITY_PATTERN)
    session_id: str = Field(alias="sessionId", pattern=ENTITY_PATTERN)
    operation_id: str = Field(alias="operationId", pattern=OPERATION_PATTERN)


class UpdateMemoryRequest(MutationContext):
    text: str = Field(min_length=1, max_length=20_000)
    metadata: dict[str, Any] = Field(default_factory=dict)
    scope: str | None = None
    application_id: str | None = Field(
        default=None, alias="applicationId", pattern=ENTITY_PATTERN
    )

    @model_validator(mode="after")
    def valid_scope_move(self) -> "UpdateMemoryRequest":
        if self.scope is not None and self.scope not in {
            "PERSONAL_GLOBAL",
            "PERSONAL_APPLICATION",
        }:
            raise ValueError("手工范围调整只支持个人记忆")
        if self.scope == "PERSONAL_GLOBAL" and self.application_id is not None:
            raise ValueError("PERSONAL_GLOBAL 不允许 applicationId")
        if self.scope == "PERSONAL_APPLICATION" and self.application_id is None:
            raise ValueError("PERSONAL_APPLICATION 必须指定 applicationId")
        return self


class SearchScope(CamelModel):
    scope: str
    user_id: str | None = Field(default=None, alias="userId", pattern=ENTITY_PATTERN)
    agent_id: str | None = Field(default=None, alias="agentId", pattern=ENTITY_PATTERN)
    application_id: str | None = Field(
        default=None, alias="applicationId", pattern=ENTITY_PATTERN
    )

    @model_validator(mode="after")
    def valid_owner_scope(self) -> "SearchScope":
        if bool(self.user_id) == bool(self.agent_id) or self.scope not in SCOPES:
            raise ValueError("检索 scope 与 owner 不匹配")
        if self.scope == "PERSONAL_GLOBAL":
            valid = self.user_id is not None and self.application_id is None
        elif self.scope == "PERSONAL_APPLICATION":
            valid = self.user_id is not None and self.application_id is not None
        else:
            valid = self.agent_id is not None and self.application_id is not None
        if not valid:
            raise ValueError("检索 scope 与 application 不匹配")
        return self


class SearchMemoriesRequest(MutationContext):
    query: str = Field(min_length=1, max_length=8_000)
    scopes: list[SearchScope] = Field(min_length=1, max_length=3)
    top_k: int = Field(default=10, alias="topK", ge=1, le=100)
    threshold: float = Field(default=0.1, ge=0.0, le=1.0)

    @field_validator("scopes")
    @classmethod
    def distinct_scopes(cls, values: list[SearchScope]) -> list[SearchScope]:
        identities = [
            (value.scope, value.user_id, value.agent_id, value.application_id)
            for value in values
        ]
        if len(set(identities)) != len(identities):
            raise ValueError("scopes 不得重复")
        return values
