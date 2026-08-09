from pathlib import Path

import pytest
from pydantic import ValidationError

from testagent_memory_service.settings import (
    LOCAL_BGE_REVISION,
    MemoryServiceSettings,
)


def settings(tmp_path: Path, **overrides: object) -> MemoryServiceSettings:
    values = {
        "api_key": "k" * 32,
        "history_db_path": tmp_path / "history.db",
        "model_root": tmp_path / "models",
        "model_path": tmp_path / "models" / "bge",
    }
    values.update(overrides)
    return MemoryServiceSettings(_env_file=None, **values)


def test_fixed_profile_builds_versioned_collection(tmp_path: Path) -> None:
    configured = settings(tmp_path)

    assert configured.embedding_profile()["dimension"] == 512
    assert configured.embedding_profile()["runtimeOffline"] is True
    assert LOCAL_BGE_REVISION[:8] in configured.collection_name()
    assert "d512_v1" in configured.collection_name()
    assert len(configured.collection_name().encode("ascii")) <= 63


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("embedding_provider", "ENTERPRISE_EMBEDDING"),
        ("embedding_model_id", "another/model"),
        ("embedding_revision", "main"),
        ("embedding_dimension", 768),
    ],
)
def test_v1_profile_cannot_be_overwritten(
    tmp_path: Path, field: str, value: object
) -> None:
    with pytest.raises(ValidationError):
        settings(tmp_path, **{field: value})


def test_model_must_stay_under_model_root(tmp_path: Path) -> None:
    with pytest.raises(ValidationError, match="模型只能从只读"):
        settings(tmp_path, model_path=tmp_path / "outside")


def test_gateway_rejects_credentials_and_wrong_path(tmp_path: Path) -> None:
    with pytest.raises(ValidationError, match="固定 model-gateway"):
        settings(tmp_path, extraction_gateway_url="http://user:pass@models/internal")
