import json
import math
from pathlib import Path

from testagent_embedding_service.model import BgeCpuModel
from testagent_embedding_service.settings import (
    MODEL_DIMENSION,
    MODEL_ID,
    MODEL_REVISION,
    QUERY_PREFIX,
    EmbeddingServiceSettings,
)


class Values:
    def __init__(self, value: list[list[float]]):
        self.value = value

    def tolist(self) -> list[list[float]]:
        return self.value


class FakeEncoder:
    def __init__(self) -> None:
        self.values: list[str] = []

    def get_sentence_embedding_dimension(self) -> int:
        return MODEL_DIMENSION

    def encode(self, values: list[str], **kwargs: object) -> Values:
        del kwargs
        self.values = values
        vector = [1.0 / math.sqrt(MODEL_DIMENSION)] * MODEL_DIMENSION
        return Values([list(vector) for _ in values])


def test_query_prefix_and_document_identity(tmp_path: Path) -> None:
    settings = EmbeddingServiceSettings(
        _env_file=None,
        api_key="model-key-" + "x" * 32,
        model_root=tmp_path,
        model_path=tmp_path / "model",
    )
    encoder = FakeEncoder()
    model = BgeCpuModel(settings, encoder)
    query = model.embed(["边界条件"], "query")
    assert encoder.values == [QUERY_PREFIX + "边界条件"]
    assert len(query[0]) == MODEL_DIMENSION
    model.embed(["事实文档"], "document")
    assert encoder.values == ["事实文档"]


def test_manifest_contract(tmp_path: Path) -> None:
    model_path = tmp_path / "model"
    model_path.mkdir()
    (model_path / ".testagent-embedding-model.json").write_text(
        json.dumps(
            {
                "model": MODEL_ID,
                "revision": MODEL_REVISION,
                "dimension": MODEL_DIMENSION,
                "normalized": True,
            }
        ),
        encoding="utf-8",
    )
    BgeCpuModel._verify_manifest(model_path)
