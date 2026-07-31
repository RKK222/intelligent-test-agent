"""Docker 18.09兼容的非特权分析容器运行时。"""

from __future__ import annotations

from dataclasses import dataclass
import ipaddress
import json
from pathlib import Path
import re
import subprocess
from typing import Protocol


class DockerRuntimeError(RuntimeError):
    """分析节点不满足安全运行基线。"""


class CommandExecutor(Protocol):
    def run(self, command: list[str]) -> str: ...


class SubprocessCommandExecutor:
    def run(self, command: list[str]) -> str:
        result = subprocess.run(
            command,
            check=False,
            capture_output=True,
            text=True,
            timeout=120,
        )
        if result.returncode != 0:
            raise DockerRuntimeError(f"Docker命令失败，退出码 {result.returncode}")
        return result.stdout.strip()


@dataclass(frozen=True, slots=True)
class AnalysisNetworkPolicy:
    """由宿主机网络脚本落地、Runner逐任务复核的固定出站策略。"""

    network: str
    subnet: str
    model_gateway_cidr: str
    model_gateway_port: int

    def __post_init__(self) -> None:
        if not re.fullmatch(r"test-agent-analysis-[a-zA-Z0-9_.-]{1,80}", self.network):
            raise ValueError("分析网络名称不安全")
        try:
            subnet = ipaddress.ip_network(self.subnet, strict=True)
            gateway = ipaddress.ip_network(self.model_gateway_cidr, strict=False)
        except ValueError as exception:
            raise ValueError("分析网络CIDR格式无效") from exception
        if subnet.version != 4 or not subnet.is_private:
            raise ValueError("分析容器子网必须是私有IPv4 CIDR")
        if gateway.version != 4:
            raise ValueError("模型网关白名单必须是IPv4 CIDR")
        if not 1 <= self.model_gateway_port <= 65535:
            raise ValueError("模型网关端口无效")


@dataclass(frozen=True, slots=True)
class AnalysisContainerSpec:
    task_id: str
    image: str
    repositories_path: Path
    output_path: Path
    network: str
    analyzer_ids: tuple[str, ...]
    cpu_limit: str = "4.0"
    memory_limit: str = "8g"
    pids_limit: int = 1024
    nofile_limit: int = 65536
    tmpfs_size: str = "2g"

    def __post_init__(self) -> None:
        if not 1 <= len(self.analyzer_ids) <= 3:
            raise ValueError("每个任务必须选择1到最多3个智能体")
        if not re.fullmatch(r"task_[a-zA-Z0-9_-]{8,58}", self.task_id):
            raise ValueError("taskId格式不安全")
        if not (
            re.fullmatch(r"[A-Za-z0-9._:/-]+@sha256:[0-9a-f]{64}", self.image)
            or re.fullmatch(r"sha256:[0-9a-f]{64}", self.image)
        ):
            raise ValueError("分析镜像必须固定为仓库digest或离线Docker image ID")
        if not re.fullmatch(r"test-agent-analysis-[a-zA-Z0-9_.-]{1,80}", self.network):
            raise ValueError("分析容器必须使用test-agent-analysis-*专用受限网络")


class DockerRuntime:
    """唯一允许访问Docker的组件；参数表中不存在特权降级分支。"""

    def __init__(
        self,
        executor: CommandExecutor | None = None,
        *,
        network_policy: AnalysisNetworkPolicy | None = None,
    ) -> None:
        self._executor = executor or SubprocessCommandExecutor()
        self._network_policy = network_policy

    def verify_server(self) -> None:
        raw = self._executor.run(["docker", "version", "--format", "{{.Server.Version}}"])
        match = re.match(r"^(\d+)\.(\d+)", raw.strip())
        if match is None:
            raise DockerRuntimeError("无法读取Docker Server版本")
        major, minor = int(match.group(1), 10), int(match.group(2), 10)
        if (major, minor) < (18, 9):
            raise DockerRuntimeError("Analysis Runner要求Docker 18.09或更高版本")

    def create_and_start(self, spec: AnalysisContainerSpec) -> str:
        self.verify_server()
        if self._network_policy is not None:
            self.verify_network(spec.network)
        repositories = spec.repositories_path.resolve(strict=True)
        output = spec.output_path.resolve(strict=True)
        name = self.container_name(spec.task_id)
        command = [
            "docker",
            "create",
            "--name",
            name,
            "--user",
            "10001:10003",
            "--read-only",
            "--cap-drop",
            "ALL",
            "--security-opt",
            "no-new-privileges",
            "--pids-limit",
            str(spec.pids_limit),
            "--ulimit",
            f"nofile={spec.nofile_limit}:{spec.nofile_limit}",
            "--memory",
            spec.memory_limit,
            "--cpus",
            spec.cpu_limit,
            "--network",
            spec.network,
            "--tmpfs",
            f"/tmp:rw,noexec,nosuid,nodev,size={spec.tmpfs_size},uid=10001,gid=10003,mode=1700",
            "--tmpfs",
            "/run:rw,noexec,nosuid,nodev,size=16m,uid=10001,gid=10003,mode=1700",
            "--volume",
            f"{repositories}:/workspace/repos:ro",
            "--volume",
            f"{output}:/workspace/output:rw",
            "--env",
            f"TEST_AGENT_ANALYZERS={','.join(spec.analyzer_ids)}",
            "--env",
            f"TEST_AGENT_TASK_ID={spec.task_id}",
            spec.image,
        ]
        container_id = self._executor.run(command)
        if not container_id:
            raise DockerRuntimeError("Docker未返回容器ID")
        try:
            self._executor.run(["docker", "start", name])
            self._verify_container_constraints(name)
        except Exception:
            # 创建后启动失败时只清理精确容器名；不触碰工作区，以便排障。
            self._executor.run(["docker", "rm", "-f", name])
            raise
        return container_id

    def verify_network(self, network: str) -> str:
        """确认网络由受控脚本创建且标签与预期iptables白名单完全一致。"""

        policy = self._network_policy
        if policy is None:
            raise DockerRuntimeError("未配置分析受限网络策略")
        if network != policy.network:
            raise DockerRuntimeError("分析容器请求了未授权受限网络")
        try:
            raw = self._executor.run(["docker", "network", "inspect", network])
            values = json.loads(raw)
            if not isinstance(values, list) or len(values) != 1 or not isinstance(values[0], dict):
                raise ValueError("network inspect结构无效")
            value = values[0]
            labels = value.get("Labels") or {}
            ipam = value.get("IPAM") or {}
            configurations = ipam.get("Config") or []
            safe = (
                value.get("Name") == policy.network
                and value.get("Driver") == "bridge"
                and value.get("Scope") == "local"
                and value.get("Internal") is False
                and isinstance(labels, dict)
                and labels.get("com.enterprise.testagent.egress-policy")
                == "model-gateway-only"
                and labels.get("com.enterprise.testagent.model-gateway-cidr")
                == policy.model_gateway_cidr
                and labels.get("com.enterprise.testagent.model-gateway-port")
                == str(policy.model_gateway_port)
                and isinstance(configurations, list)
                and len(configurations) == 1
                and isinstance(configurations[0], dict)
                and configurations[0].get("Subnet") == policy.subnet
            )
            if not safe:
                raise ValueError("network policy不匹配")
            ipaddress.ip_network(str(configurations[0]["Subnet"]), strict=True)
        except (DockerRuntimeError, json.JSONDecodeError, KeyError, TypeError, ValueError) as exception:
            raise DockerRuntimeError("无法证明Docker受限网络符合模型网关白名单，阻断任务交付") from exception
        return policy.subnet

    def _verify_container_constraints(self, name: str) -> None:
        inspection = self._executor.run(
            [
                "docker",
                "inspect",
                "--format",
                "{{.Config.User}}|{{.HostConfig.Privileged}}|{{.HostConfig.ReadonlyRootfs}}|"
                "{{.HostConfig.CapDrop}}|{{.HostConfig.SecurityOpt}}",
                name,
            ]
        )
        parts = inspection.strip().split("|", 4)
        safe = (
            len(parts) == 5
            and parts[0] == "10001:10003"
            and parts[1].lower() == "false"
            and parts[2].lower() == "true"
            and "ALL" in parts[3].upper()
            and "no-new-privileges" in parts[4].lower()
        )
        if not safe:
            raise DockerRuntimeError("Docker未保持要求的非特权安全约束，阻断任务交付")

    def stop_retained(self, task_id: str) -> None:
        self._executor.run(["docker", "stop", "--time", "20", self.container_name(task_id)])

    def resume(self, task_id: str) -> None:
        self._executor.run(["docker", "start", self.container_name(task_id)])

    def restart_clean(self, task_id: str) -> None:
        """每次智能体调用后重启同一容器，确保后台后代不能污染下一次分析。"""

        name = self.container_name(task_id)
        self._executor.run(["docker", "restart", "--time", "20", name])
        self._verify_container_constraints(name)

    def remove(self, task_id: str) -> None:
        name = self.container_name(task_id)
        try:
            self._executor.run(["docker", "rm", "-f", name])
        except DockerRuntimeError as remove_error:
            try:
                self._executor.run(["docker", "container", "inspect", name])
            except DockerRuntimeError:
                # 到期清理和Runner自清理可能竞态；只把“精确容器已不存在”视为幂等成功。
                return
            raise remove_error

    @staticmethod
    def container_name(task_id: str) -> str:
        if not re.fullmatch(r"task_[a-zA-Z0-9_-]{8,58}", task_id):
            raise ValueError("taskId格式不安全")
        return f"test-agent-analysis-{task_id.removeprefix('task_')}"
