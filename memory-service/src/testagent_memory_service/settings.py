"""记忆服务显式配置；模型身份、集合版本和离线约束集中在这里。"""

from __future__ import annotations

from hashlib import sha256
from pathlib import Path
import re
from urllib.parse import urlsplit

from pydantic import Field, SecretStr, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


LOCAL_BGE_PROVIDER = "LOCAL_BGE"
LOCAL_BGE_MODEL_ID = "BAAI/bge-small-zh-v1.5"
LOCAL_BGE_REVISION = "7999e1d3359715c523056ef9478215996d62a620"
LOCAL_BGE_DIMENSION = 512
LOCAL_BGE_QUERY_PREFIX = "为这个句子生成表示以用于检索相关文章："


class MemoryServiceSettings(BaseSettings):
    """生产配置只接受固定本地 BGE profile，不允许运行期漂移。"""

    model_config = SettingsConfigDict(
        env_prefix="TEST_AGENT_MEMORY_SERVICE_",
        case_sensitive=False,
        extra="ignore",
    )

    api_key: SecretStr = Field(min_length=32)
    database_url: SecretStr = SecretStr(
        "postgresql://qa_memory:qa_memory@127.0.0.1:15433/qa_memory"
    )
    history_db_path: Path = Path("/data/mem0-history.db")
    model_root: Path = Path("/models")
    model_path: Path = Path("/models/BAAI__bge-small-zh-v1.5")
    embedding_provider: str = LOCAL_BGE_PROVIDER
    embedding_model_id: str = LOCAL_BGE_MODEL_ID
    embedding_revision: str = LOCAL_BGE_REVISION
    embedding_dimension: int = LOCAL_BGE_DIMENSION
    collection_version: str = "v1"
    request_timeout_seconds: float = Field(default=120.0, gt=0.0, le=180.0)
    extraction_gateway_url: str | None = None
    max_document_chars: int = Field(default=8_000, ge=1, le=20_000)
    max_extraction_chars: int = Field(default=120_000, ge=1, le=200_000)

    @field_validator("history_db_path", "model_root", "model_path")
    @classmethod
    def require_absolute_path(cls, value: Path) -> Path:
        if not value.is_absolute():
            raise ValueError("记忆服务持久化和模型路径必须为绝对路径")
        return value

    @field_validator("embedding_provider")
    @classmethod
    def fixed_provider(cls, value: str) -> str:
        if value != LOCAL_BGE_PROVIDER:
            raise ValueError("V1 只允许 LOCAL_BGE；企业 Embedding 必须新增 Provider 和集合")
        return value

    @field_validator("embedding_model_id")
    @classmethod
    def fixed_model(cls, value: str) -> str:
        if value != LOCAL_BGE_MODEL_ID:
            raise ValueError("V1 模型身份不可覆盖")
        return value

    @field_validator("embedding_revision")
    @classmethod
    def fixed_revision(cls, value: str) -> str:
        if value != LOCAL_BGE_REVISION:
            raise ValueError("V1 模型 revision 不可覆盖")
        return value

    @field_validator("embedding_dimension")
    @classmethod
    def fixed_dimension(cls, value: int) -> int:
        if value != LOCAL_BGE_DIMENSION:
            raise ValueError("V1 向量维度必须为 512")
        return value

    @field_validator("collection_version")
    @classmethod
    def safe_collection_version(cls, value: str) -> str:
        if not re.fullmatch(r"v[1-9][0-9]{0,3}", value):
            raise ValueError("collection_version 格式无效")
        return value

    @field_validator("extraction_gateway_url")
    @classmethod
    def fixed_gateway_path(cls, value: str | None) -> str | None:
        if value is None:
            return None
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
            raise ValueError("抽取网关必须使用固定 model-gateway base path 且不得内嵌凭据")
        return normalized

    @model_validator(mode="after")
    def require_model_under_root(self) -> "MemoryServiceSettings":
        try:
            self.model_path.relative_to(self.model_root)
        except ValueError as exception:
            raise ValueError("模型只能从只读 /models 根目录加载") from exception
        return self

    def collection_name(self) -> str:
        """PostgreSQL 标识符最多 63 字节，保留可读身份并追加完整 profile 摘要。"""
        identity = ":".join(
            [
                self.embedding_provider,
                self.embedding_model_id,
                self.embedding_revision,
                str(self.embedding_dimension),
                self.collection_version,
            ]
        )
        digest = sha256(identity.encode("utf-8")).hexdigest()[:8]
        return (
            "qm_local_bge_bge_small_zh_15_"
            f"r{self.embedding_revision[:8]}_d{self.embedding_dimension}_"
            f"{self.collection_version}_{digest}"
        )

    def embedding_profile(self) -> dict[str, object]:
        return {
            "provider": self.embedding_provider,
            "model": self.embedding_model_id,
            "revision": self.embedding_revision,
            "dimension": self.embedding_dimension,
            "device": "CPU",
            "normalized": True,
            "queryPrefix": LOCAL_BGE_QUERY_PREFIX,
            "collection": self.collection_name(),
            "collectionVersion": self.collection_version,
            "runtimeOffline": True,
        }
