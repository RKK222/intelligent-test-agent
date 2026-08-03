from __future__ import annotations

from pathlib import Path

import pytest
from pydantic import ValidationError

from testagent_workflow.settings import WorkflowSettings


def base_settings(**overrides: object) -> WorkflowSettings:
    values: dict[str, object] = {
        "platform_hmac_secret": "p" * 32,
        "runner_hmac_secret": "r" * 32,
        "runner_public_key": (
            "-----BEGIN PUBLIC KEY-----\nYWJj\n-----END PUBLIC KEY-----\n"
        ),
    }
    values.update(overrides)
    return WorkflowSettings(**values)  # type: ignore[arg-type]


def test_runner_public_key_can_be_loaded_from_read_only_file(tmp_path: Path) -> None:
    public_key = tmp_path / "runner-public.pem"
    public_key.write_text(
        "-----BEGIN PUBLIC KEY-----\nYWJj\n-----END PUBLIC KEY-----\n",
        encoding="ascii",
    )

    configured = base_settings(runner_public_key=None, runner_public_key_path=public_key)

    assert configured.resolved_runner_public_key().startswith("-----BEGIN PUBLIC KEY-----")


def test_runner_public_key_source_is_unambiguous() -> None:
    with pytest.raises(ValidationError, match="必须且只能"):
        base_settings(runner_public_key=None, runner_public_key_path=None)


def test_analysis_model_name_rejects_unsafe_configuration() -> None:
    with pytest.raises(ValidationError, match="模型ID"):
        base_settings(analysis_model_name="model\nleak")


def test_server_model_gateway_override_requires_fixed_safe_base_path() -> None:
    configured = base_settings(
        server_model_gateway_url=(
            "http://127.0.0.1:8080/api/internal/platform/model-gateway/v1/"
        )
    )
    assert configured.server_model_gateway_url.endswith("/model-gateway/v1")

    with pytest.raises(ValidationError, match="服务端模型网关URL"):
        base_settings(server_model_gateway_url="http://token@127.0.0.1:8080/v1?leak=1")
