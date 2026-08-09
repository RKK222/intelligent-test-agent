import json
import math
from pathlib import Path

import numpy as np
import pytest

from mem0.configs.embeddings.base import BaseEmbedderConfig

from testagent_memory_service import embedding
from testagent_memory_service.embedding import LocalBgeEmbedding, ModelIdentityError
from testagent_memory_service.settings import (
    LOCAL_BGE_DIMENSION,
    LOCAL_BGE_MODEL_ID,
    LOCAL_BGE_QUERY_PREFIX,
    LOCAL_BGE_REVISION,
)


class FakeSentenceTransformer:
    encoded: list[str] = []

    def __init__(self, model_path: str, **kwargs: object):
        self.model_path = model_path
        self.kwargs = kwargs

    def get_sentence_embedding_dimension(self) -> int:
        return LOCAL_BGE_DIMENSION

    def encode(self, texts: str | list[str], **kwargs: object) -> np.ndarray:
        values = [texts] if isinstance(texts, str) else texts
        self.encoded.extend(values)
        vector = np.ones((len(values), LOCAL_BGE_DIMENSION), dtype=float)
        vector /= math.sqrt(LOCAL_BGE_DIMENSION)
        return vector[0] if isinstance(texts, str) else vector


def config(model_root: Path, model_path: Path) -> BaseEmbedderConfig:
    return BaseEmbedderConfig(
        model=str(model_path),
        embedding_dims=LOCAL_BGE_DIMENSION,
        model_kwargs={
            "model_root": str(model_root),
            "expected_model_id": LOCAL_BGE_MODEL_ID,
            "expected_revision": LOCAL_BGE_REVISION,
        },
    )


def write_manifest(model_path: Path) -> None:
    model_path.mkdir(parents=True)
    (model_path / ".qa-memory-model.json").write_text(
        json.dumps(
            {
                "model": LOCAL_BGE_MODEL_ID,
                "revision": LOCAL_BGE_REVISION,
                "dimension": LOCAL_BGE_DIMENSION,
            }
        ),
        encoding="utf-8",
    )


def test_search_uses_prefix_and_returns_normalized_512_vector(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    model_root = tmp_path / "models"
    model_path = model_root / "bge"
    write_manifest(model_path)
    FakeSentenceTransformer.encoded = []
    monkeypatch.setattr(embedding, "SentenceTransformer", FakeSentenceTransformer)

    provider = LocalBgeEmbedding(config(model_root, model_path))
    vector = provider.embed("边界场景", "search")

    assert FakeSentenceTransformer.encoded == [LOCAL_BGE_QUERY_PREFIX + "边界场景"]
    assert len(vector) == LOCAL_BGE_DIMENSION
    assert math.isclose(math.sqrt(sum(value * value for value in vector)), 1.0)


def test_missing_or_drifting_manifest_is_rejected(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    model_root = tmp_path / "models"
    model_path = model_root / "bge"
    model_path.mkdir(parents=True)
    monkeypatch.setattr(embedding, "SentenceTransformer", FakeSentenceTransformer)
    with pytest.raises(ModelIdentityError, match="身份清单"):
        LocalBgeEmbedding(config(model_root, model_path))

    (model_path / ".qa-memory-model.json").write_text(
        json.dumps(
            {"model": LOCAL_BGE_MODEL_ID, "revision": "main", "dimension": 512}
        ),
        encoding="utf-8",
    )
    with pytest.raises(ModelIdentityError, match="不一致"):
        LocalBgeEmbedding(config(model_root, model_path))
