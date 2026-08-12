import pytest
from pydantic import ValidationError

from testagent_memory_service.settings import CPU_DIMENSION, MemoryServiceSettings


def settings(**overrides: object) -> MemoryServiceSettings:
    values = {
        "api_key": "k" * 32,
        "model_gateway_hmac_secret": "s" * 32,
    }
    values.update(overrides)
    return MemoryServiceSettings(_env_file=None, **values)


def test_cpu_only_profile_is_primary_and_versioned() -> None:
    profile = settings().primary_profile()
    assert profile.provider == "CPU"
    assert profile.dimension == CPU_DIMENSION
    assert profile.primary is True
    assert len(profile.collection_name().encode("ascii")) <= 63


def test_enterprise_profile_has_separate_collection_and_cpu_hot_standby() -> None:
    configured = settings(
        enterprise_embedding_model_id="enterprise-embedding",
        enterprise_embedding_dimension=1024,
        enterprise_embedding_fingerprint="sha256:1234567890abcdef",
    )
    enterprise, cpu = configured.profiles()
    assert enterprise.primary is True
    assert cpu.primary is False
    assert enterprise.collection_name() != cpu.collection_name()
    assert enterprise.dimension == 1024
    assert cpu.dimension == 512


def test_enterprise_profile_requires_complete_identity() -> None:
    with pytest.raises(ValidationError, match="必须同时配置"):
        settings(enterprise_embedding_model_id="enterprise-embedding")


def test_compose_empty_enterprise_profile_values_mean_cpu_only(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("TEST_AGENT_MEMORY_SERVICE_API_KEY", "k" * 32)
    monkeypatch.setenv(
        "TEST_AGENT_MEMORY_SERVICE_MODEL_GATEWAY_HMAC_SECRET", "h" * 32
    )
    monkeypatch.setenv("TEST_AGENT_MEMORY_SERVICE_ENTERPRISE_EMBEDDING_MODEL_ID", "")
    monkeypatch.setenv("TEST_AGENT_MEMORY_SERVICE_ENTERPRISE_EMBEDDING_DIMENSION", "")
    monkeypatch.setenv("TEST_AGENT_MEMORY_SERVICE_ENTERPRISE_EMBEDDING_FINGERPRINT", "")

    configured = MemoryServiceSettings(_env_file=None)

    assert configured.enterprise_embedding_model_id is None
    assert configured.enterprise_embedding_dimension is None
    assert configured.enterprise_embedding_fingerprint is None
    assert [profile.provider for profile in configured.profiles()] == ["CPU"]


def test_gateway_rejects_credentials_and_wrong_path() -> None:
    with pytest.raises(ValidationError, match="固定 model-gateway"):
        settings(model_gateway_url="http://user:pass@models/internal")
