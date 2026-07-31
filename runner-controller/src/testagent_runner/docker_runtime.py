"""Docker 18.09兼容的非特权分析容器运行时。"""

from __future__ import annotations

from dataclasses import dataclass
import ipaddress
import json
import math
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
        if (
            len(set(self.analyzer_ids)) != len(self.analyzer_ids)
            or any(value not in {"codex", "opencode"} for value in self.analyzer_ids)
        ):
            raise ValueError("分析容器智能体必须来自Runner注册表且不能重复")
        if not re.fullmatch(r"task_[a-zA-Z0-9_-]{8,58}", self.task_id):
            raise ValueError("taskId格式不安全")
        if not (
            re.fullmatch(r"[A-Za-z0-9._:/-]+@sha256:[0-9a-f]{64}", self.image)
            or re.fullmatch(r"sha256:[0-9a-f]{64}", self.image)
        ):
            raise ValueError("分析镜像必须固定为仓库digest或离线Docker image ID")
        if not re.fullmatch(r"test-agent-analysis-[a-zA-Z0-9_.-]{1,80}", self.network):
            raise ValueError("分析容器必须使用test-agent-analysis-*专用受限网络")
        if self.pids_limit <= 0 or self.nofile_limit <= 0:
            raise ValueError("分析容器进程和文件句柄限制必须为正数")
        _parse_size(self.memory_limit)
        _parse_size(self.tmpfs_size)
        try:
            cpu_limit = float(self.cpu_limit)
        except ValueError as exception:
            raise ValueError("分析容器CPU限制无效") from exception
        if not math.isfinite(cpu_limit) or cpu_limit <= 0:
            raise ValueError("分析容器CPU限制无效")


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
            self._verify_container_constraints(name, spec)
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

    def _verify_container_constraints(
        self,
        name: str,
        spec: AnalysisContainerSpec | None = None,
    ) -> None:
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

        if spec is not None:
            self._verify_container_identity(name, spec)

    def _verify_container_identity(self, name: str, spec: AnalysisContainerSpec) -> None:
        try:
            raw = self._executor.run(
                [
                    "docker",
                    "inspect",
                    "--format",
                    "{{.Config.Image}}|{{.HostConfig.NetworkMode}}|{{json .Mounts}}|"
                    "{{.HostConfig.PidsLimit}}|{{.HostConfig.Memory}}|"
                    "{{.HostConfig.NanoCpus}}|{{json .HostConfig.Ulimits}}|"
                    "{{json .HostConfig.Tmpfs}}",
                    name,
                ]
            )
            (
                image,
                network,
                mounts_json,
                pids_limit,
                memory_limit,
                nano_cpus,
                ulimits_json,
                tmpfs_json,
            ) = raw.split("|", 7)
            mounts = json.loads(mounts_json)
            if not isinstance(mounts, list):
                raise ValueError("mounts不是数组")
            expected = {
                "/workspace/repos": (str(spec.repositories_path.resolve(strict=True)), False),
                "/workspace/output": (str(spec.output_path.resolve(strict=True)), True),
            }
            actual: dict[str, tuple[str, bool]] = {}
            for mount in mounts:
                if not isinstance(mount, dict) or mount.get("Type") != "bind":
                    raise ValueError("存在非bind挂载")
                destination = str(mount.get("Destination", ""))
                if destination in actual:
                    raise ValueError("挂载目标重复")
                actual[destination] = (
                    str(mount.get("Source", "")),
                    mount.get("RW") is True,
                )
            if image != spec.image or network != spec.network or actual != expected:
                raise ValueError("容器身份漂移")
            ulimits = json.loads(ulimits_json)
            nofile = [
                value
                for value in ulimits
                if isinstance(value, dict) and value.get("Name") == "nofile"
            ] if isinstance(ulimits, list) else []
            tmpfs = json.loads(tmpfs_json)
            tmp_options = _option_set(tmpfs.get("/tmp")) if isinstance(tmpfs, dict) else set()
            run_options = _option_set(tmpfs.get("/run")) if isinstance(tmpfs, dict) else set()
            resource_safe = (
                int(pids_limit) == spec.pids_limit
                and int(memory_limit) == _parse_size(spec.memory_limit)
                and int(nano_cpus) == int(float(spec.cpu_limit) * 1_000_000_000)
                and len(nofile) == 1
                and nofile[0].get("Soft") == spec.nofile_limit
                and nofile[0].get("Hard") == spec.nofile_limit
                and isinstance(tmpfs, dict)
                and set(tmpfs) == {"/tmp", "/run"}
                and {
                    "rw",
                    "noexec",
                    "nosuid",
                    "nodev",
                    f"size={spec.tmpfs_size}",
                    "uid=10001",
                    "gid=10003",
                    "mode=1700",
                } <= tmp_options
                and {
                    "rw",
                    "noexec",
                    "nosuid",
                    "nodev",
                    "size=16m",
                    "uid=10001",
                    "gid=10003",
                    "mode=1700",
                } <= run_options
            )
            if not resource_safe:
                raise ValueError("容器资源限制漂移")
        except (DockerRuntimeError, json.JSONDecodeError, OSError, TypeError, ValueError) as exception:
            raise DockerRuntimeError(
                "Docker任务容器镜像、网络、挂载或资源限制与冻结状态不一致，阻断任务交付"
            ) from exception

    def verify_running(self, spec: AnalysisContainerSpec) -> None:
        """每次执行模型前复核运行容器仍绑定冻结镜像、源码和输出目录。"""

        self._verify_container_constraints(self.container_name(spec.task_id), spec)

    def stop_retained(self, task_id: str) -> None:
        self._executor.run(["docker", "stop", "--time", "20", self.container_name(task_id)])

    def resume(self, task: str | AnalysisContainerSpec) -> None:
        spec = task if isinstance(task, AnalysisContainerSpec) else None
        task_id = spec.task_id if spec is not None else task
        name = self.container_name(task_id)
        self._executor.run(["docker", "start", name])
        try:
            self._verify_container_constraints(name, spec)
        except Exception:
            # 保留工作区源码供排障，但绝不留下约束已漂移且正在运行的任务容器。
            self._executor.run(["docker", "rm", "-f", name])
            raise

    def restart_clean(self, task_id: str) -> None:
        """每次智能体调用后重启同一容器，确保后台后代不能污染下一次分析。"""

        name = self.container_name(task_id)
        self._executor.run(["docker", "restart", "--time", "20", name])
        self._verify_container_constraints(name)

    def quiesce_for_cleanup(self, task_id: str) -> None:
        """确认精确容器存在后先停止所有不可信进程，宿主才可放宽输出目录权限。"""

        name = self.container_name(task_id)
        if not self._container_exists(name):
            return
        self._executor.run(["docker", "stop", "--time", "20", name])

    def sanitize_workspace(self, task: str | AnalysisContainerSpec) -> None:
        """停止分析进程后，用分析UID恢复其私有目录权限，再交还宿主Runner删除。"""

        spec = task if isinstance(task, AnalysisContainerSpec) else None
        task_id = spec.task_id if spec is not None else task
        name = self.container_name(task_id)
        if not self._container_exists(name):
            # 精确查询成功且容器确实不存在时，由宿主直接校验目录删除。
            return
        started = False
        try:
            self._executor.run(["docker", "stop", "--time", "20", name])
            self._executor.run(["docker", "start", name])
            started = True
            self._verify_container_constraints(name, spec)
            self._executor.run(
                [
                    "docker",
                    "exec",
                    "--user",
                    "10001:10003",
                    name,
                    "/usr/local/bin/test-agent-clean-output",
                    "/workspace/output",
                ]
            )
        finally:
            if started:
                self._executor.run(["docker", "stop", "--time", "20", name])

    def remove(self, task_id: str) -> None:
        name = self.container_name(task_id)
        try:
            self._executor.run(["docker", "rm", "-f", name])
        except DockerRuntimeError as remove_error:
            try:
                exists = self._container_exists(name)
            except DockerRuntimeError:
                # Docker控制面不可用不能伪装为容器不存在。
                raise remove_error
            if not exists:
                # 到期清理和Runner自清理可能竞态；精确列表确认不存在才幂等成功。
                return
            raise remove_error

    def _container_exists(self, name: str) -> bool:
        output = self._executor.run(
            [
                "docker",
                "container",
                "ls",
                "--all",
                "--filter",
                f"name=^/{name}$",
                "--format",
                "{{.Names}}",
            ]
        )
        names = [value.strip() for value in output.splitlines() if value.strip()]
        if not names:
            return False
        if names == [name]:
            return True
        raise DockerRuntimeError("Docker精确容器查询返回了意外结果")

    @staticmethod
    def container_name(task_id: str) -> str:
        if not re.fullmatch(r"task_[a-zA-Z0-9_-]{8,58}", task_id):
            raise ValueError("taskId格式不安全")
        return f"test-agent-analysis-{task_id.removeprefix('task_')}"


def _parse_size(value: str) -> int:
    match = re.fullmatch(r"([1-9][0-9]*)([kmgt]?)b?", value.strip().lower())
    if match is None:
        raise ValueError("分析容器容量限制无效")
    units = {"": 1, "k": 1024, "m": 1024**2, "g": 1024**3, "t": 1024**4}
    return int(match.group(1)) * units[match.group(2)]


def _option_set(value: object) -> set[str]:
    if not isinstance(value, str):
        return set()
    return {item.strip().lower() for item in value.split(",") if item.strip()}
