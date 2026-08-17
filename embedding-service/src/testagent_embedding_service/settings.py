"""CPU BGE 固定模型身份与服务容量配置。"""

from pathlib import Path

from pydantic import Field, SecretStr, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


MODEL_ID = "BAAI/bge-small-zh-v1.5"
MODEL_REVISION = "7999e1d3359715c523056ef9478215996d62a620"
MODEL_DIMENSION = 512
QUERY_PREFIX = "为这个句子生成表示以用于检索相关文章："


class EmbeddingServiceSettings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="TEST_AGENT_EMBEDDING_",
        case_sensitive=False,
        extra="ignore",
    )

    api_key: SecretStr = Field(min_length=32)
    model_root: Path = Path("/models")
    model_path: Path = Path("/models/BAAI__bge-small-zh-v1.5")
    max_batch_size: int = Field(default=64, ge=1, le=256)
    max_input_chars: int = Field(default=8_000, ge=1, le=32_000)
    max_batch_chars: int = Field(default=120_000, ge=1, le=500_000)
    max_concurrency: int = Field(default=4, ge=1, le=64)
    queue_timeout_seconds: float = Field(default=2.0, gt=0.0, le=30.0)
    torch_threads: int = Field(default=1, ge=1, le=32)

    @field_validator("model_root", "model_path")
    @classmethod
    def absolute_path(cls, value: Path) -> Path:
        if not value.is_absolute():
            raise ValueError("模型路径必须是绝对路径")
        return value

    @model_validator(mode="after")
    def model_under_root(self) -> "EmbeddingServiceSettings":
        try:
            self.model_path.relative_to(self.model_root)
        except ValueError as exception:
            raise ValueError("模型只能从只读 model_root 加载") from exception
        return self
