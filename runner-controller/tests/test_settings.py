from __future__ import annotations

from pathlib import Path

import pytest
from pydantic import ValidationError

from testagent_runner.settings import RunnerSettings


def settings(tmp_path: Path, **overrides: object) -> RunnerSettings:
    known_hosts = tmp_path / "known_hosts"
    known_hosts.chmod(0o644) if known_hosts.exists() else None
    known_hosts.write_text("git.example.test ssh-ed25519 AAAATEST\n", encoding="utf-8")
    known_hosts.chmod(0o444)
    values: dict[str, object] = {
        "runner_id": "runner-analysis-1",
        "worker_hmac_secret": "w" * 32,
        "platform_hmac_secret": "p" * 32,
        "private_key_path": tmp_path / "runner-private.pem",
        "known_hosts_path": known_hosts,
        "analysis_image": "test-agent-analysis@sha256:" + "a" * 64,
        "analysis_network": "test-agent-analysis-egress",
        "analysis_network_subnet": "172.31.250.0/24",
        "model_gateway_url": (
            "http://10.20.30.40:8080/api/internal/platform/model-gateway/v1"
        ),
        "model_gateway_cidr": "10.20.30.40/32",
        "model_gateway_port": 8080,
    }
    values.update(overrides)
    return RunnerSettings(**values)  # type: ignore[arg-type]


def test_runner_settings_bind_gateway_url_to_firewall_allowlist(tmp_path: Path) -> None:
    configured = settings(tmp_path)

    assert configured.model_gateway_url.endswith("/api/internal/platform/model-gateway/v1")
    assert configured.analysis_network_subnet == "172.31.250.0/24"
    assert settings(tmp_path, analysis_image="sha256:" + "b" * 64).analysis_image.startswith(
        "sha256:"
    )


@pytest.mark.parametrize(
    ("overrides", "message"),
    [
        (
            {
                "model_gateway_url": (
                    "http://gateway.internal:8080/api/internal/platform/model-gateway/v1"
                )
            },
            "IP地址",
        ),
        (
            {
                "model_gateway_url": (
                    "http://10.20.30.41:8080/api/internal/platform/model-gateway/v1"
                )
            },
            "白名单",
        ),
        (
            {
                "model_gateway_url": (
                    "http://10.20.30.40:8081/api/internal/platform/model-gateway/v1"
                )
            },
            "端口",
        ),
        ({"model_gateway_url": "http://10.20.30.40:8080/api/other/v1"}, "base path"),
        ({"analysis_network_subnet": "8.8.8.0/24"}, "私有IPv4"),
        ({"analysis_image": "test-agent-analysis:latest"}, "digest"),
        ({"known_hosts_path": Path("known_hosts")}, "绝对路径"),
    ],
)
def test_runner_settings_reject_ambiguous_network_or_image_configuration(
    tmp_path: Path,
    overrides: dict[str, object],
    message: str,
) -> None:
    with pytest.raises(ValidationError, match=message):
        settings(tmp_path, **overrides)
