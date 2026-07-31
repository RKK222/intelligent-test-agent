"""Analysis Runner显式配置。"""

import ipaddress
from pathlib import Path
import re
from urllib.parse import urlsplit

from pydantic import Field, SecretStr, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class RunnerSettings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="TEST_AGENT_RUNNER_", extra="ignore")

    runner_id: str
    worker_hmac_secret: SecretStr
    platform_hmac_secret: SecretStr
    platform_base_url: str = "http://127.0.0.1:8080"
    root: Path = Path("/data/testagent/workflow-runner")
    credential_root: Path = Path("/run/test-agent-workflow-credentials")
    private_key_path: Path
    known_hosts_path: Path = Path("/run/secrets/workflow-git-known-hosts")
    analysis_image: str
    analysis_network: str = "test-agent-analysis-egress"
    analysis_network_subnet: str = "172.31.250.0/24"
    model_gateway_url: str
    model_gateway_cidr: str
    model_gateway_port: int = Field(ge=1, le=65535)
    minimum_free_bytes: int = Field(default=10 * 1024 * 1024 * 1024, ge=1024 * 1024 * 1024)

    @field_validator("worker_hmac_secret", "platform_hmac_secret")
    @classmethod
    def validate_secret(cls, value: SecretStr) -> SecretStr:
        if len(value.get_secret_value().encode()) < 32:
            raise ValueError("HMAC密钥至少需要32字节")
        return value

    @field_validator("analysis_image")
    @classmethod
    def validate_image(cls, value: str) -> str:
        if not (
            re.fullmatch(r"[A-Za-z0-9._:/-]+@sha256:[0-9a-f]{64}", value)
            or re.fullmatch(r"sha256:[0-9a-f]{64}", value)
        ):
            raise ValueError("分析镜像必须固定为仓库digest或离线Docker image ID")
        return value

    @field_validator("runner_id")
    @classmethod
    def validate_runner_id(cls, value: str) -> str:
        if not re.fullmatch(r"runner-[a-zA-Z0-9_-]{3,80}", value):
            raise ValueError("runnerId格式无效")
        return value

    @field_validator("analysis_network")
    @classmethod
    def validate_network_name(cls, value: str) -> str:
        if not re.fullmatch(r"test-agent-analysis-[a-zA-Z0-9_.-]{1,80}", value):
            raise ValueError("分析网络必须使用test-agent-analysis-*专用名称")
        return value

    @field_validator("root", "credential_root", "private_key_path", "known_hosts_path")
    @classmethod
    def validate_absolute_path(cls, value: Path) -> Path:
        if not value.is_absolute():
            raise ValueError("Runner安全路径必须是绝对路径")
        return value

    @model_validator(mode="after")
    def validate_network_policy(self) -> "RunnerSettings":
        try:
            subnet = ipaddress.ip_network(self.analysis_network_subnet, strict=True)
            gateway_cidr = ipaddress.ip_network(self.model_gateway_cidr, strict=False)
        except ValueError as exception:
            raise ValueError("Runner网络CIDR格式无效") from exception
        if subnet.version != 4 or not subnet.is_private:
            raise ValueError("分析容器子网必须是私有IPv4 CIDR")
        if gateway_cidr.version != 4:
            raise ValueError("模型网关白名单必须是IPv4 CIDR")

        parsed = urlsplit(self.model_gateway_url)
        if parsed.scheme not in {"http", "https"} or not parsed.hostname:
            raise ValueError("模型网关URL必须是HTTP(S)绝对地址")
        if parsed.username or parsed.password or parsed.query or parsed.fragment:
            raise ValueError("模型网关URL禁止凭据、查询串或片段")
        if parsed.path != "/api/internal/platform/model-gateway/v1":
            raise ValueError("模型网关URL必须使用固定base path")
        try:
            gateway_ip = ipaddress.ip_address(parsed.hostname)
        except ValueError as exception:
            raise ValueError("模型网关URL必须使用被防火墙锁定的IP地址") from exception
        if gateway_ip.version != 4 or gateway_ip not in gateway_cidr:
            raise ValueError("模型网关URL不在配置的出站白名单中")
        actual_port = parsed.port or (443 if parsed.scheme == "https" else 80)
        if actual_port != self.model_gateway_port:
            raise ValueError("模型网关URL端口与防火墙白名单端口不一致")
        return self
