"""工作流服务显式环境配置；敏感值使用SecretStr避免日志展开。"""

from __future__ import annotations

from pathlib import Path
import re

from pydantic import Field, SecretStr, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class WorkflowSettings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="TEST_AGENT_WORKFLOW_",
        case_sensitive=False,
        extra="ignore",
    )

    database_url: str = "postgresql+asyncpg://workflow:workflow@127.0.0.1:5432/test_agent_workflow"
    redis_url: SecretStr = SecretStr("redis://127.0.0.1:6379/0")
    platform_base_url: str = "http://127.0.0.1:8080"
    platform_hmac_secret: SecretStr
    intent_model_name: str = "workflow-intent"
    synthesis_model_name: str = "workflow-impact-synthesis"
    report_qa_model_name: str = "workflow-report-qa"
    analysis_model_name: str = "workflow-code-analysis"
    runner_base_url: str = "http://127.0.0.1:8091"
    runner_hmac_secret: SecretStr
    runner_id: str = "runner-default"
    runner_public_key: str | None = None
    runner_public_key_path: Path | None = None
    worker_id: str = "workflow-worker-default"
    worker_lease_seconds: int = Field(default=60, ge=15, le=600)
    workspace_retention_hours: int = Field(default=48, ge=1)

    @field_validator("platform_hmac_secret", "runner_hmac_secret")
    @classmethod
    def validate_hmac_secret(cls, value: SecretStr) -> SecretStr:
        if len(value.get_secret_value().encode()) < 32:
            raise ValueError("HMAC密钥至少需要32字节")
        return value

    @field_validator("analysis_model_name")
    @classmethod
    def validate_analysis_model_name(cls, value: str) -> str:
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}", value):
            raise ValueError("代码分析模型ID格式无效")
        return value

    def sync_database_url(self) -> str:
        return self.database_url.replace("postgresql+asyncpg://", "postgresql+psycopg://")

    def checkpoint_database_url(self) -> str:
        return self.database_url.replace("postgresql+asyncpg://", "postgresql://")

    @model_validator(mode="after")
    def validate_runner_public_key_source(self) -> "WorkflowSettings":
        if bool(self.runner_public_key) == bool(self.runner_public_key_path):
            raise ValueError("runner_public_key与runner_public_key_path必须且只能配置一个")
        if self.runner_public_key_path is not None and not self.runner_public_key_path.is_absolute():
            raise ValueError("runner_public_key_path必须是绝对路径")
        return self

    def resolved_runner_public_key(self) -> str:
        value = self.runner_public_key
        if self.runner_public_key_path is not None:
            if not self.runner_public_key_path.is_file():
                raise ValueError("Runner公钥文件不存在")
            value = self.runner_public_key_path.read_text(encoding="ascii")
        normalized = (value or "").strip() + "\n"
        if (
            len(normalized.encode()) > 16 * 1024
            or "-----BEGIN PUBLIC KEY-----" not in normalized
            or "-----END PUBLIC KEY-----" not in normalized
        ):
            raise ValueError("Runner公钥不是受支持的PEM公钥")
        return normalized
