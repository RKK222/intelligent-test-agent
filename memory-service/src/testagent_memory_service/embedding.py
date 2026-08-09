"""固定 LOCAL_BGE Provider；运行期禁止从 HuggingFace 联网补模型。"""

from __future__ import annotations

import json
import math
from pathlib import Path
from typing import Literal

from mem0.configs.embeddings.base import BaseEmbedderConfig
from mem0.embeddings.base import EmbeddingBase
from sentence_transformers import SentenceTransformer

from testagent_memory_service.settings import (
    LOCAL_BGE_DIMENSION,
    LOCAL_BGE_MODEL_ID,
    LOCAL_BGE_QUERY_PREFIX,
    LOCAL_BGE_REVISION,
)


class ModelIdentityError(RuntimeError):
    """镜像内模型身份、维度或归一化合同不匹配。"""


class LocalBgeEmbedding(EmbeddingBase):
    """Mem0 自定义 Embedder，搜索时补 BGE 中文检索前缀并统一 L2 归一化。"""

    def __init__(self, config: BaseEmbedderConfig | None = None):
        super().__init__(config)
        model_path = Path(self.config.model or "")
        kwargs = dict(self.config.model_kwargs or {})
        model_root = Path(str(kwargs.pop("model_root", "/models")))
        expected_model = str(kwargs.pop("expected_model_id", LOCAL_BGE_MODEL_ID))
        expected_revision = str(kwargs.pop("expected_revision", LOCAL_BGE_REVISION))
        if kwargs:
            raise ModelIdentityError("LOCAL_BGE 不接受未登记的模型参数")
        if not model_path.is_absolute() or not model_root.is_absolute():
            raise ModelIdentityError("LOCAL_BGE 只能从绝对路径加载")
        try:
            model_path.relative_to(model_root)
        except ValueError as exception:
            raise ModelIdentityError("LOCAL_BGE 模型必须位于只读模型根目录") from exception
        self._verify_manifest(model_path, expected_model, expected_revision)
        self.model = SentenceTransformer(
            str(model_path),
            device="cpu",
            local_files_only=True,
            trust_remote_code=False,
        )
        dimension = int(self.model.get_sentence_embedding_dimension())
        if dimension != LOCAL_BGE_DIMENSION:
            raise ModelIdentityError(f"LOCAL_BGE 向量维度错误：{dimension}")
        self.config.embedding_dims = dimension

    @staticmethod
    def _verify_manifest(model_path: Path, model_id: str, revision: str) -> None:
        manifest_path = model_path / ".qa-memory-model.json"
        if not model_path.is_dir() or not manifest_path.is_file():
            raise ModelIdentityError("镜像内缺少固定 BGE 模型或身份清单")
        try:
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exception:
            raise ModelIdentityError("BGE 模型身份清单不可读") from exception
        expected = {
            "model": model_id,
            "revision": revision,
            "dimension": LOCAL_BGE_DIMENSION,
        }
        if any(manifest.get(key) != value for key, value in expected.items()):
            raise ModelIdentityError("BGE 模型身份清单与固定 profile 不一致")

    def embed(
        self,
        text: str,
        memory_action: Literal["add", "search", "update"] | None = None,
    ) -> list[float]:
        normalized = text.strip()
        if memory_action == "search":
            normalized = LOCAL_BGE_QUERY_PREFIX + normalized
        vector = self.model.encode(
            normalized,
            convert_to_numpy=True,
            normalize_embeddings=True,
            show_progress_bar=False,
        ).tolist()
        if len(vector) != LOCAL_BGE_DIMENSION:
            raise ModelIdentityError("LOCAL_BGE 返回了非 512 维向量")
        norm = math.sqrt(sum(float(item) * float(item) for item in vector))
        if not 0.999 <= norm <= 1.001:
            raise ModelIdentityError("LOCAL_BGE 返回向量未归一化")
        return [float(item) for item in vector]

    def embed_batch(
        self,
        texts: list[str],
        memory_action: Literal["add", "search", "update"] = "add",
    ) -> list[list[float]]:
        normalized = [text.strip() for text in texts]
        if memory_action == "search":
            normalized = [LOCAL_BGE_QUERY_PREFIX + text for text in normalized]
        vectors = self.model.encode(
            normalized,
            convert_to_numpy=True,
            normalize_embeddings=True,
            show_progress_bar=False,
        ).tolist()
        result = [[float(item) for item in vector] for vector in vectors]
        if any(len(vector) != LOCAL_BGE_DIMENSION for vector in result):
            raise ModelIdentityError("LOCAL_BGE 批量返回了非 512 维向量")
        return result


class EmbeddingProviderRegistry:
    """显式注册表；未来企业 Embedding 通过新增键和新集合接入。"""

    _providers = {"LOCAL_BGE": LocalBgeEmbedding}

    @classmethod
    def provider(cls, name: str) -> type[LocalBgeEmbedding]:
        try:
            return cls._providers[name]
        except KeyError as exception:
            raise ModelIdentityError(f"未登记的 Embedding Provider：{name}") from exception
