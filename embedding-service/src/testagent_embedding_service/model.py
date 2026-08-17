"""离线加载并校验固定 BGE 权重，统一输出 512 维 L2 归一化向量。"""

from __future__ import annotations

import json
import math
from pathlib import Path
from typing import Protocol

import torch
from sentence_transformers import SentenceTransformer

from testagent_embedding_service.settings import (
    MODEL_DIMENSION,
    MODEL_ID,
    MODEL_REVISION,
    QUERY_PREFIX,
    EmbeddingServiceSettings,
)


class Encoder(Protocol):
    def encode(self, values: list[str], **kwargs: object) -> object: ...
    def get_sentence_embedding_dimension(self) -> int: ...


class ModelIdentityError(RuntimeError):
    pass


class BgeCpuModel:
    def __init__(
        self,
        settings: EmbeddingServiceSettings,
        encoder: Encoder | None = None,
    ):
        torch.set_num_threads(settings.torch_threads)
        try:
            # PyTorch 只允许在并行工作开始前设置一次；测试或同进程重建 app 时保持已设值。
            torch.set_num_interop_threads(1)
        except RuntimeError:
            pass
        if encoder is None:
            self._verify_manifest(settings.model_path)
            encoder = SentenceTransformer(
                str(settings.model_path),
                device="cpu",
                local_files_only=True,
                trust_remote_code=False,
            )
        self.encoder = encoder
        dimension = int(encoder.get_sentence_embedding_dimension())
        if dimension != MODEL_DIMENSION:
            raise ModelIdentityError(f"BGE 向量维度错误：{dimension}")

    @staticmethod
    def _verify_manifest(model_path: Path) -> None:
        manifest_path = model_path / ".testagent-embedding-model.json"
        if not model_path.is_dir() or not manifest_path.is_file():
            raise ModelIdentityError("镜像内缺少固定 BGE 模型或身份清单")
        try:
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exception:
            raise ModelIdentityError("BGE 模型身份清单不可读") from exception
        expected = {
            "model": MODEL_ID,
            "revision": MODEL_REVISION,
            "dimension": MODEL_DIMENSION,
            "normalized": True,
        }
        if any(manifest.get(key) != value for key, value in expected.items()):
            raise ModelIdentityError("BGE 模型身份清单与固定 profile 不一致")

    def embed(self, values: list[str], input_type: str) -> list[list[float]]:
        normalized = [value.strip() for value in values]
        if input_type == "query":
            normalized = [QUERY_PREFIX + value for value in normalized]
        encoded = self.encoder.encode(
            normalized,
            convert_to_numpy=True,
            normalize_embeddings=True,
            show_progress_bar=False,
        )
        vectors = encoded.tolist() if hasattr(encoded, "tolist") else encoded
        result = [[float(item) for item in vector] for vector in vectors]
        if any(len(vector) != MODEL_DIMENSION for vector in result):
            raise ModelIdentityError("BGE 返回了非 512 维向量")
        for vector in result:
            norm = math.sqrt(sum(item * item for item in vector))
            if not 0.999 <= norm <= 1.001:
                raise ModelIdentityError("BGE 返回向量未 L2 归一化")
        return result
