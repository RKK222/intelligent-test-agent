"""通用记忆服务配置：共享 PostgreSQL、模型网关和互不混写的向量 profile。"""

from __future__ import annotations

from dataclasses import dataclass
from hashlib import sha256
import re
from urllib.parse import urlsplit

from pydantic import Field, SecretStr, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


CPU_PROFILE_KEY = "cpu:bge-small-zh-v1.5:512:7999e1d3359715c523056ef9478215996d62a620"
CPU_MODEL_ID = "BAAI/bge-small-zh-v1.5"
CPU_MODEL_REVISION = "7999e1d3359715c523056ef9478215996d62a620"
CPU_DIMENSION = 512
CPU_QUERY_PREFIX = "为这个句子生成表示以用于检索相关文章："


@dataclass(frozen=True, slots=True)
class EmbeddingProfile:
    """一个可独立读写的 embedding profile；profile_key 是集合身份而非展示名。"""

    profile_key: str
    model_id: str
    dimension: int
    fingerprint: str
    provider: str
    primary: bool

    def collection_name(self) -> str:
        digest = sha256(self.profile_key.encode("utf-8")).hexdigest()[:16]
        prefix = "memory_cpu" if self.provider == "CPU" else "memory_enterprise"
        return f"{prefix}_d{self.dimension}_{digest}"

    def public_view(self) -> dict[str, object]:
        return {
            "profileKey": self.profile_key,
            "provider": self.provider,
            "model": self.model_id,
            "dimension": self.dimension,
            "fingerprint": self.fingerprint,
            "collection": self.collection_name(),
            "primary": self.primary,
            "normalized": True,
        }


class MemoryServiceSettings(BaseSettings):
    """所有节点读取同一配置；节点自身不保存 history、幂等或投影状态。"""

    model_config = SettingsConfigDict(
        env_prefix="TEST_AGENT_MEMORY_SERVICE_",
        case_sensitive=False,
        extra="ignore",
    )

    api_key: SecretStr = Field(min_length=32)
    database_url: SecretStr = SecretStr(
        "postgresql://testagent_memory:testagent_memory@127.0.0.1:15433/testagent_memory"
    )
    model_gateway_url: str = "http://127.0.0.1:8080/api/internal/platform/model-gateway/v1"
    model_gateway_hmac_secret: SecretStr = SecretStr("replace-memory-hmac-secret-at-least-32-bytes")
    model_gateway_client_id: str = Field(default="mem0-cluster", pattern=r"^[A-Za-z0-9][A-Za-z0-9._:-]{2,63}$")
    chat_model_id: str = Field(default="memory-chat", pattern=r"^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$")
    cpu_embedding_model_id: str = Field(
        default="memory-bge-small-zh-v1.5",
        pattern=r"^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$",
    )
    enterprise_embedding_model_id: str | None = Field(
        default=None, pattern=r"^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$"
    )
    enterprise_embedding_dimension: int | None = Field(default=None, ge=1, le=65_535)
    enterprise_embedding_fingerprint: str | None = Field(default=None, max_length=128)
    chat_timeout_seconds: float = Field(default=120.0, gt=0.0, le=180.0)
    embedding_timeout_seconds: float = Field(default=15.0, gt=0.0, le=60.0)
    search_profile_timeout_seconds: float = Field(default=1.5, ge=0.1, le=1.8)
    max_document_chars: int = Field(default=8_000, ge=1, le=20_000)
    max_learning_chars: int = Field(default=120_000, ge=1, le=200_000)
    max_search_top_k: int = Field(default=50, ge=1, le=100)
    postgres_pool_min_size: int = Field(default=1, ge=1, le=20)
    postgres_pool_max_size: int = Field(default=20, ge=2, le=100)
    projection_poll_seconds: float = Field(default=1.0, ge=0.1, le=30.0)
    projection_batch_size: int = Field(default=32, ge=1, le=200)
    operation_stale_seconds: int = Field(default=300, ge=30, le=3600)

    @field_validator(
        "enterprise_embedding_model_id",
        "enterprise_embedding_dimension",
        "enterprise_embedding_fingerprint",
        mode="before",
    )
    @classmethod
    def empty_optional_profile_value(cls, value: object) -> object | None:
        # Docker Compose 对未配置的可选环境变量会传空字符串；这在语义上就是
        # “没有企业 profile”，必须在类型/正则校验前统一为 None。
        if isinstance(value, str) and not value.strip():
            return None
        return value

    @field_validator("model_gateway_url")
    @classmethod
    def fixed_gateway_path(cls, value: str) -> str:
        normalized = value.rstrip("/")
        parsed = urlsplit(normalized)
        if (
            parsed.scheme not in {"http", "https"}
            or not parsed.hostname
            or parsed.username
            or parsed.password
            or parsed.query
            or parsed.fragment
            or parsed.path != "/api/internal/platform/model-gateway/v1"
        ):
            raise ValueError("模型网关必须使用固定 model-gateway base path 且不得内嵌凭据")
        return normalized

    @field_validator("enterprise_embedding_fingerprint")
    @classmethod
    def safe_fingerprint(cls, value: str | None) -> str | None:
        if value is None:
            return None
        normalized = value.strip()
        if not re.fullmatch(r"[A-Za-z0-9._:-]{8,128}", normalized):
            raise ValueError("企业 embedding fingerprint 格式无效")
        return normalized

    @model_validator(mode="after")
    def validate_profiles(self) -> "MemoryServiceSettings":
        secret = self.model_gateway_hmac_secret.get_secret_value().encode("utf-8")
        if len(secret) < 32:
            raise ValueError("模型网关 HMAC secret 至少需要 32 字节")
        configured = self.enterprise_embedding_model_id is not None
        if configured != (self.enterprise_embedding_dimension is not None) or configured != (
            self.enterprise_embedding_fingerprint is not None
        ):
            raise ValueError("企业 embedding 的 modelId、dimension、fingerprint 必须同时配置或同时为空")
        if self.postgres_pool_max_size < self.postgres_pool_min_size:
            raise ValueError("PostgreSQL pool max size 不能小于 min size")
        return self

    def profiles(self) -> tuple[EmbeddingProfile, ...]:
        cpu = EmbeddingProfile(
            profile_key=CPU_PROFILE_KEY,
            model_id=self.cpu_embedding_model_id,
            dimension=CPU_DIMENSION,
            fingerprint=CPU_MODEL_REVISION,
            provider="CPU",
            primary=self.enterprise_embedding_model_id is None,
        )
        if self.enterprise_embedding_model_id is None:
            return (cpu,)
        enterprise_key = ":".join(
            [
                "enterprise",
                self.enterprise_embedding_model_id,
                str(self.enterprise_embedding_dimension),
                str(self.enterprise_embedding_fingerprint),
            ]
        )
        enterprise = EmbeddingProfile(
            profile_key=enterprise_key,
            model_id=self.enterprise_embedding_model_id,
            dimension=int(self.enterprise_embedding_dimension),
            fingerprint=str(self.enterprise_embedding_fingerprint),
            provider="ENTERPRISE",
            primary=True,
        )
        return (enterprise, cpu)

    def primary_profile(self) -> EmbeddingProfile:
        return next(profile for profile in self.profiles() if profile.primary)

    def embedding_profiles(self) -> list[dict[str, object]]:
        return [profile.public_view() for profile in self.profiles()]
