from __future__ import annotations

import json
from pathlib import Path

import pytest

from testagent_runner.docker_runtime import (
    AnalysisContainerSpec,
    AnalysisNetworkPolicy,
    DockerRuntime,
    DockerRuntimeError,
)


class RecordingExecutor:
    def __init__(
        self,
        *,
        version: str = "18.09.7",
        inspection: str | None = None,
        network_inspection: str | None = None,
    ) -> None:
        self.version = version
        self.inspection = inspection or "true|10001:10003|false|true|[ALL]|[no-new-privileges]"
        self.network_inspection = network_inspection or """[{
          "Name": "test-agent-analysis-egress",
          "Driver": "bridge",
          "Scope": "local",
          "Internal": false,
          "Labels": {
            "com.enterprise.testagent.egress-policy": "model-gateway-only",
            "com.enterprise.testagent.model-gateway-cidr": "10.20.30.40/32",
            "com.enterprise.testagent.model-gateway-port": "8080"
          },
          "IPAM": {"Config": [{"Subnet": "172.31.250.0/24"}]}
        }]"""
        self.commands: list[list[str]] = []

    def run(self, command: list[str]) -> str:
        self.commands.append(command)
        if command[1:3] == ["version", "--format"]:
            return self.version
        if command[1:3] == ["inspect", "--format"] and ".Mounts" not in command[3]:
            return self.inspection
        if command[1:3] == ["inspect", "--format"] and ".Mounts" in command[3]:
            create = next(value for value in self.commands if value[1] == "create")
            volumes = [
                create[index + 1]
                for index, value in enumerate(create)
                if value == "--volume"
            ]
            mounts = []
            for volume in volumes:
                source, destination, mode = volume.rsplit(":", 2)
                mounts.append(
                    {
                        "Type": "bind",
                        "Source": source,
                        "Destination": destination,
                        "Mode": mode,
                        "RW": mode == "rw",
                    }
                )
            memory = create[create.index("--memory") + 1]
            memory_bytes = int(memory[:-1]) * 1024**3
            cpu_nanos = int(float(create[create.index("--cpus") + 1]) * 1_000_000_000)
            nofile = int(create[create.index("--ulimit") + 1].split("=")[1].split(":")[0])
            tmpfs_values = [
                create[index + 1]
                for index, value in enumerate(create)
                if value == "--tmpfs"
            ]
            tmpfs = {
                target: options
                for target, options in (value.split(":", 1) for value in tmpfs_values)
            }
            return "|".join(
                [
                    create[-1],
                    create[create.index("--network") + 1],
                    json.dumps(mounts),
                    create[create.index("--pids-limit") + 1],
                    str(memory_bytes),
                    str(cpu_nanos),
                    json.dumps([{"Name": "nofile", "Soft": nofile, "Hard": nofile}]),
                    json.dumps(tmpfs),
                ]
            )
        if command[1:3] == ["network", "inspect"]:
            return self.network_inspection
        if command[1:3] == ["container", "ls"]:
            expected = (
                command[command.index("--filter") + 1]
                .removeprefix("name=^/")
                .removesuffix("$")
            )
            return expected
        return "container_1234567890"


def test_task_container_is_non_privileged_read_only_and_resource_bounded(tmp_path: Path) -> None:
    executor = RecordingExecutor()
    runtime = DockerRuntime(executor)
    spec = AnalysisContainerSpec(
        task_id="task_12345678",
        image="test-agent-analysis-task@sha256:" + "a" * 64,
        repositories_path=tmp_path / "repos",
        output_path=tmp_path / "output",
        network="test-agent-analysis-egress",
        analyzer_ids=("codex", "opencode"),
    )
    spec.repositories_path.mkdir()
    spec.output_path.mkdir()

    runtime.create_and_start(spec)

    create = next(command for command in executor.commands if command[1] == "create")
    assert "--privileged" not in create
    assert "--read-only" in create
    assert create[create.index("--user") + 1] == "10001:10003"
    tmpfs_values = [
        create[index + 1]
        for index, value in enumerate(create)
        if value == "--tmpfs"
    ]
    assert all("gid=10003" in value for value in tmpfs_values)
    assert ["--cap-drop", "ALL"] == create[create.index("--cap-drop"):create.index("--cap-drop") + 2]
    assert "no-new-privileges" in create
    assert "--pids-limit" in create
    assert "--memory" in create
    assert "--cpus" in create
    assert not any("docker.sock" in value for value in create)
    assert f"{spec.repositories_path.resolve()}:/workspace/repos:ro" in create
    assert any(command[1] == "inspect" for command in executor.commands)


def test_runtime_refuses_old_docker_or_more_than_three_analyzers(tmp_path: Path) -> None:
    with pytest.raises(DockerRuntimeError, match="18.09"):
        DockerRuntime(RecordingExecutor(version="18.06.3")).verify_server()

    with pytest.raises(ValueError, match="最多3个"):
        AnalysisContainerSpec(
            task_id="task_12345678",
            image="image@sha256:" + "a" * 64,
            repositories_path=tmp_path,
            output_path=tmp_path,
            network="analysis",
            analyzer_ids=("a", "b", "c", "d"),
        )

    with pytest.raises(ValueError, match="专用受限网络"):
        AnalysisContainerSpec(
            task_id="task_12345678",
            image="image@sha256:" + "a" * 64,
            repositories_path=tmp_path,
            output_path=tmp_path,
            network="bridge",
            analyzer_ids=("codex",),
        )

    with pytest.raises(ValueError, match="智能体"):
        AnalysisContainerSpec(
            task_id="task_12345678",
            image="image@sha256:" + "a" * 64,
            repositories_path=tmp_path,
            output_path=tmp_path,
            network="test-agent-analysis-egress",
            analyzer_ids=("../../escape",),
        )


@pytest.mark.parametrize(
    "identity",
    [
        "wrong-image@sha256:" + "b" * 64
        + "|test-agent-analysis-egress|[]",
        "test-agent-analysis-task@sha256:" + "a" * 64
        + "|bridge|[]",
        "test-agent-analysis-task@sha256:" + "a" * 64
        + "|test-agent-analysis-egress|[]",
    ],
)
def test_running_container_rejects_image_network_or_mount_drift(
    tmp_path: Path,
    identity: str,
) -> None:
    class DriftedExecutor(RecordingExecutor):
        def run(self, command: list[str]) -> str:
            if command[1:3] == ["inspect", "--format"] and ".Mounts" in command[3]:
                self.commands.append(command)
                return identity
            return super().run(command)

    spec = AnalysisContainerSpec(
        task_id="task_12345678",
        image="test-agent-analysis-task@sha256:" + "a" * 64,
        repositories_path=tmp_path / "repos",
        output_path=tmp_path / "output",
        network="test-agent-analysis-egress",
        analyzer_ids=("codex",),
    )
    spec.repositories_path.mkdir()
    spec.output_path.mkdir()

    with pytest.raises(DockerRuntimeError, match="镜像、网络、挂载或资源限制"):
        DockerRuntime(DriftedExecutor()).verify_running(spec)


def test_container_resource_limit_drift_blocks_delivery(tmp_path: Path) -> None:
    class UnboundedExecutor(RecordingExecutor):
        def run(self, command: list[str]) -> str:
            raw = super().run(command)
            if command[1:3] == ["inspect", "--format"] and ".Mounts" in command[3]:
                parts = raw.split("|", 7)
                parts[3] = "0"
                return "|".join(parts)
            return raw

    repositories = tmp_path / "repos"
    output = tmp_path / "output"
    repositories.mkdir()
    output.mkdir()
    runtime = DockerRuntime(UnboundedExecutor())

    with pytest.raises(DockerRuntimeError, match="镜像、网络、挂载或资源限制"):
        runtime.create_and_start(
            AnalysisContainerSpec(
                task_id="task_12345678",
                image="test-agent-analysis-task@sha256:" + "a" * 64,
                repositories_path=repositories,
                output_path=output,
                network="test-agent-analysis-egress",
                analyzer_ids=("codex",),
            )
        )

def test_runtime_blocks_delivery_if_docker_drops_non_privileged_constraints(tmp_path: Path) -> None:
    executor = RecordingExecutor(
        inspection="0:0|true|false|[]|[]",
    )
    repositories = tmp_path / "repos"
    output = tmp_path / "output"
    repositories.mkdir()
    output.mkdir()

    with pytest.raises(DockerRuntimeError, match="非特权安全约束"):
        DockerRuntime(executor).create_and_start(
            AnalysisContainerSpec(
                task_id="task_12345678",
                image="image@sha256:" + "a" * 64,
                repositories_path=repositories,
                output_path=output,
                network="test-agent-analysis-egress",
                analyzer_ids=("codex",),
            )
        )

    assert ["docker", "rm", "-f", "test-agent-analysis-12345678"] in executor.commands


def test_running_verification_rejects_a_stopped_container(tmp_path: Path) -> None:
    repositories = tmp_path / "repos"
    output = tmp_path / "output"

    class StoppedContainerExecutor(RecordingExecutor):
        def run(self, command: list[str]) -> str:
            if command[1:3] == ["inspect", "--format"] and ".Mounts" not in command[3]:
                self.commands.append(command)
                if ".State.Running" in command[3]:
                    return "false|10001:10003|false|true|[ALL]|[no-new-privileges]"
            if command[1:3] == ["inspect", "--format"] and ".Mounts" in command[3]:
                self.commands.append(command)
                mounts = [
                    {
                        "Type": "bind",
                        "Source": str(repositories.resolve()),
                        "Destination": "/workspace/repos",
                        "RW": False,
                    },
                    {
                        "Type": "bind",
                        "Source": str(output.resolve()),
                        "Destination": "/workspace/output",
                        "RW": True,
                    },
                ]
                tmpfs = {
                    "/tmp": "rw,noexec,nosuid,nodev,size=2g,uid=10001,gid=10003,mode=1700",
                    "/run": "rw,noexec,nosuid,nodev,size=16m,uid=10001,gid=10003,mode=1700",
                }
                return "|".join(
                    [
                        "image@sha256:" + "a" * 64,
                        "test-agent-analysis-egress",
                        json.dumps(mounts),
                        "1024",
                        str(8 * 1024**3),
                        "4000000000",
                        json.dumps([{"Name": "nofile", "Soft": 65536, "Hard": 65536}]),
                        json.dumps(tmpfs),
                    ]
                )
            return super().run(command)

    repositories.mkdir()
    output.mkdir()
    spec = AnalysisContainerSpec(
        task_id="task_12345678",
        image="image@sha256:" + "a" * 64,
        repositories_path=repositories,
        output_path=output,
        network="test-agent-analysis-egress",
        analyzer_ids=("codex",),
    )

    with pytest.raises(DockerRuntimeError, match="运行状态"):
        DockerRuntime(StoppedContainerExecutor()).verify_running(spec)


def test_runtime_verifies_restricted_network_before_creating_container(tmp_path: Path) -> None:
    executor = RecordingExecutor()
    runtime = DockerRuntime(
        executor,
        network_policy=AnalysisNetworkPolicy(
            network="test-agent-analysis-egress",
            subnet="172.31.250.0/24",
            model_gateway_cidr="10.20.30.40/32",
            model_gateway_port=8080,
        ),
    )
    repositories = tmp_path / "repos"
    output = tmp_path / "output"
    repositories.mkdir()
    output.mkdir()

    runtime.create_and_start(
        AnalysisContainerSpec(
            task_id="task_12345678",
            image="image@sha256:" + "a" * 64,
            repositories_path=repositories,
            output_path=output,
            network="test-agent-analysis-egress",
            analyzer_ids=("codex",),
        )
    )

    network_inspect_index = executor.commands.index(
        ["docker", "network", "inspect", "test-agent-analysis-egress"]
    )
    create_index = next(
        index for index, command in enumerate(executor.commands) if command[1] == "create"
    )
    assert network_inspect_index < create_index


@pytest.mark.parametrize(
    "network_inspection",
    [
        "[]",
        "not-json",
        """[{"Name":"test-agent-analysis-egress","Driver":"bridge","Scope":"local",
        "Internal":false,"Labels":{},"IPAM":{"Config":[{"Subnet":"172.31.250.0/24"}]}}]""",
        """[{"Name":"test-agent-analysis-egress","Driver":"bridge","Scope":"local",
        "Internal":false,"Labels":{"com.enterprise.testagent.egress-policy":"model-gateway-only",
        "com.enterprise.testagent.model-gateway-cidr":"10.20.30.41/32",
        "com.enterprise.testagent.model-gateway-port":"8080"},
        "IPAM":{"Config":[{"Subnet":"172.31.250.0/24"}]}}]""",
    ],
)
def test_runtime_fails_closed_when_restricted_network_cannot_be_proved(
    network_inspection: str,
) -> None:
    runtime = DockerRuntime(
        RecordingExecutor(network_inspection=network_inspection),
        network_policy=AnalysisNetworkPolicy(
            network="test-agent-analysis-egress",
            subnet="172.31.250.0/24",
            model_gateway_cidr="10.20.30.40/32",
            model_gateway_port=8080,
        ),
    )

    with pytest.raises(DockerRuntimeError, match="受限网络"):
        runtime.verify_network("test-agent-analysis-egress")


def test_remove_is_idempotent_only_when_the_exact_container_is_already_absent() -> None:
    class MissingContainerExecutor:
        def run(self, command: list[str]) -> str:
            if command[1:3] == ["rm", "-f"]:
                raise DockerRuntimeError("missing")
            if command[1:3] == ["container", "ls"]:
                return ""
            raise AssertionError(command)

    DockerRuntime(MissingContainerExecutor()).remove("task_12345678")


def test_remove_does_not_treat_docker_daemon_failure_as_container_absence() -> None:
    class UnavailableDockerExecutor:
        def run(self, command: list[str]) -> str:
            raise DockerRuntimeError("docker unavailable")

    with pytest.raises(DockerRuntimeError, match="docker unavailable"):
        DockerRuntime(UnavailableDockerExecutor()).remove("task_12345678")


def test_analyzer_reset_restarts_the_container_and_rechecks_constraints() -> None:
    executor = RecordingExecutor()
    runtime = DockerRuntime(executor)

    runtime.restart_clean("task_12345678")

    assert [
        "docker",
        "restart",
        "--time",
        "20",
        "test-agent-analysis-12345678",
    ] in executor.commands
    assert any(command[1:3] == ["inspect", "--format"] for command in executor.commands)


def test_analyzer_reset_rechecks_the_complete_frozen_container_identity(tmp_path: Path) -> None:
    class DriftedContainerExecutor(RecordingExecutor):
        def run(self, command: list[str]) -> str:
            if command[1:3] == ["inspect", "--format"] and ".Mounts" in command[3]:
                self.commands.append(command)
                return "wrong-image@sha256:" + "b" * 64 + "|bridge|[]|0|0|0|[]|{}"
            return super().run(command)

    repositories = tmp_path / "repos"
    output = tmp_path / "output"
    repositories.mkdir()
    output.mkdir()
    spec = AnalysisContainerSpec(
        task_id="task_12345678",
        image="image@sha256:" + "a" * 64,
        repositories_path=repositories,
        output_path=output,
        network="test-agent-analysis-egress",
        analyzer_ids=("codex",),
    )

    with pytest.raises(DockerRuntimeError, match="镜像、网络、挂载或资源限制"):
        DockerRuntime(DriftedContainerExecutor()).restart_clean(spec)


def test_retained_container_resume_rechecks_non_privileged_constraints() -> None:
    executor = RecordingExecutor()
    runtime = DockerRuntime(executor)

    runtime.resume("task_12345678")

    assert ["docker", "start", "test-agent-analysis-12345678"] in executor.commands
    assert any(command[1:3] == ["inspect", "--format"] for command in executor.commands)


def test_workspace_sanitizer_stops_analyzers_and_runs_only_as_the_analysis_uid() -> None:
    executor = RecordingExecutor()
    runtime = DockerRuntime(executor)

    runtime.sanitize_workspace("task_12345678")

    name = "test-agent-analysis-12345678"
    assert executor.commands.count(["docker", "stop", "--time", "20", name]) == 2
    assert ["docker", "start", name] in executor.commands
    assert [
        "docker",
        "exec",
        "--user",
        "10001:10003",
        name,
        "/usr/local/bin/test-agent-clean-output",
        "/workspace/output",
    ] in executor.commands


def test_cleanup_quiesce_confirms_exact_container_before_stopping() -> None:
    executor = RecordingExecutor()
    runtime = DockerRuntime(executor)

    runtime.quiesce_for_cleanup("task_12345678")

    name = "test-agent-analysis-12345678"
    assert executor.commands[0] == [
        "docker",
        "container",
        "ls",
        "--all",
        "--filter",
        f"name=^/{name}$",
        "--format",
        "{{.Names}}",
    ]
    assert executor.commands[1] == ["docker", "stop", "--time", "20", name]
