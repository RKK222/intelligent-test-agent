from __future__ import annotations

import asyncio
import json
from pathlib import Path

import pytest

from testagent_runner.analyzer_executor import (
    MAX_ANALYZER_RESULT_BYTES,
    AnalyzerExecutionError,
    DockerAnalyzerExecutor,
)
from testagent_runner.docker_runtime import AnalysisContainerSpec
from testagent_runner.model_relay import ModelRelayAccess


class ContainerNames:
    resets: list[str] = []

    @staticmethod
    def container_name(task_id: str) -> str:
        return f"container-{task_id}"

    @classmethod
    def restart_clean(cls, spec: AnalysisContainerSpec) -> None:
        cls.resets.append(spec.task_id)


class CompletedProcess:
    async def wait(self) -> int:
        return 0


class FakeRelay:
    def __init__(self) -> None:
        self.access = ModelRelayAccess(
            base_url="http://127.0.0.1:18080/v1",
            local_token="relay_local_only_token_1234567890",
        )

    async def __aenter__(self) -> ModelRelayAccess:
        return self.access

    async def __aexit__(self, *_: object) -> None:
        return None


class FakeRelayFactory:
    def __init__(self) -> None:
        self.calls: list[tuple[str, str, str, str]] = []

    def __call__(
        self,
        task_id: str,
        analyzer_id: str,
        upstream_url: str,
        upstream_grant: str,
    ) -> FakeRelay:
        self.calls.append((task_id, analyzer_id, upstream_url, upstream_grant))
        return FakeRelay()


def container_spec(tmp_path: Path) -> AnalysisContainerSpec:
    return AnalysisContainerSpec(
        task_id="task_12345678",
        image="test-agent-analysis@sha256:" + "a" * 64,
        repositories_path=tmp_path / "repos",
        output_path=tmp_path / "output",
        network="test-agent-analysis-egress",
        analyzer_ids=("codex",),
    )


@pytest.mark.asyncio
async def test_analyzer_isolates_each_output_directory_without_exposing_grant_to_others(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    output_root = tmp_path / "output"
    analyzer_root = output_root / "codex"
    analyzer_root.mkdir(parents=True, mode=0o770)
    other_analyzer_root = output_root / "opencode"
    other_analyzer_root.mkdir(mode=0o770)
    captured: list[str] = []
    relay_factory = FakeRelayFactory()
    ContainerNames.resets = []

    async def create_subprocess(*command: str, **_: object) -> CompletedProcess:
        captured.extend(command)
        assert output_root.stat().st_mode & 0o777 == 0o710
        assert analyzer_root.stat().st_mode & 0o777 == 0o770
        assert other_analyzer_root.stat().st_mode & 0o777 == 0
        assert (analyzer_root / "request.json").stat().st_mode & 0o777 == 0o640
        assert (analyzer_root / ".model-relay-token").stat().st_mode & 0o777 == 0o640
        assert (analyzer_root / ".model-relay-token").read_text() == (
            "relay_local_only_token_1234567890"
        )
        request = json.loads((analyzer_root / "request.json").read_text(encoding="utf-8"))
        assert request["modelGatewayUrl"] == "http://127.0.0.1:18080/v1"
        assert "wfg_secret" not in (analyzer_root / "request.json").read_text(encoding="utf-8")
        (analyzer_root / "result.json").write_text(
            json.dumps({"summary": "ok"}),
            encoding="utf-8",
        )
        return CompletedProcess()

    monkeypatch.setattr(asyncio, "create_subprocess_exec", create_subprocess)
    executor = DockerAnalyzerExecutor(  # type: ignore[arg-type]
        ContainerNames(),
        relay_factory=relay_factory,
    )

    result = await executor.execute(
        "task_12345678",
        "codex",
        output_root,
        {
            "modelGrant": "wfg_secret_credential",
            "modelGatewayUrl": "http://10.20.30.40:8080/model/v1",
            "outputSchema": {},
        },
        container_spec=container_spec(tmp_path),
    )

    assert result == {"summary": "ok"}
    assert captured[captured.index("--user") + 1] == "10001:10003"
    assert (analyzer_root / "home").stat().st_mode & 0o777 == 0o770
    assert (analyzer_root / "cache").stat().st_mode & 0o777 == 0o770
    assert relay_factory.calls == [
        (
            "task_12345678",
            "codex",
            "http://10.20.30.40:8080/model/v1",
            "wfg_secret_credential",
        )
    ]
    assert not (analyzer_root / ".model-relay-token").exists()
    assert ContainerNames.resets == ["task_12345678"]
    # 测试进程不是容器内Runner，恢复临时目录权限，避免pytest清理被mode 000阻断。
    other_analyzer_root.chmod(0o770)
    output_root.chmod(0o770)


@pytest.mark.asyncio
async def test_analyzer_rejects_a_result_that_cannot_fit_the_synthesis_gateway(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    output_root = tmp_path / "output"
    analyzer_root = output_root / "codex"
    analyzer_root.mkdir(parents=True, mode=0o770)

    async def create_subprocess(*command: str, **_: object) -> CompletedProcess:
        del command
        (analyzer_root / "result.json").write_bytes(
            b"x" * (MAX_ANALYZER_RESULT_BYTES + 1)
        )
        return CompletedProcess()

    monkeypatch.setattr(asyncio, "create_subprocess_exec", create_subprocess)
    relay_factory = FakeRelayFactory()

    with pytest.raises(AnalyzerExecutionError, match="安全上限"):
        await DockerAnalyzerExecutor(  # type: ignore[arg-type]
            ContainerNames(),
            relay_factory=relay_factory,
        ).execute(
            "task_12345678",
            "codex",
            output_root,
            {
                "modelGrant": "wfg_secret_credential",
                "modelGatewayUrl": "http://10.20.30.40:8080/model/v1",
                "outputSchema": {},
            },
            container_spec=container_spec(tmp_path),
        )

    assert not (analyzer_root / ".model-relay-token").exists()


@pytest.mark.asyncio
async def test_analyzer_does_not_follow_result_symlink_created_by_container(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    output_root = tmp_path / "output"
    analyzer_root = output_root / "codex"
    analyzer_root.mkdir(parents=True, mode=0o770)
    forged = tmp_path / "forged.json"
    forged.write_text(json.dumps({"summary": "forged"}), encoding="utf-8")

    async def create_subprocess(*command: str, **_: object) -> CompletedProcess:
        del command
        (analyzer_root / "result.json").symlink_to(forged)
        return CompletedProcess()

    monkeypatch.setattr(asyncio, "create_subprocess_exec", create_subprocess)
    relay_factory = FakeRelayFactory()

    with pytest.raises(AnalyzerExecutionError, match="普通文件"):
        await DockerAnalyzerExecutor(  # type: ignore[arg-type]
            ContainerNames(),
            relay_factory=relay_factory,
        ).execute(
            "task_12345678",
            "codex",
            output_root,
            {
                "modelGrant": "wfg_secret_credential",
                "modelGatewayUrl": "http://10.20.30.40:8080/model/v1",
                "outputSchema": {},
            },
            container_spec=container_spec(tmp_path),
        )


@pytest.mark.asyncio
async def test_analyzer_grant_cleanup_does_not_follow_container_symlink(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    output_root = tmp_path / "output"
    analyzer_root = output_root / "codex"
    analyzer_root.mkdir(parents=True, mode=0o770)
    victim = tmp_path / "runner-secret"
    victim.write_text("must-not-change", encoding="utf-8")

    async def create_subprocess(*command: str, **_: object) -> CompletedProcess:
        del command
        (analyzer_root / ".model-relay-token").unlink()
        (analyzer_root / ".model-relay-token").symlink_to(victim)
        (analyzer_root / "result.json").write_text(
            json.dumps({"summary": "ok"}),
            encoding="utf-8",
        )
        return CompletedProcess()

    monkeypatch.setattr(asyncio, "create_subprocess_exec", create_subprocess)
    relay_factory = FakeRelayFactory()

    result = await DockerAnalyzerExecutor(  # type: ignore[arg-type]
        ContainerNames(),
        relay_factory=relay_factory,
    ).execute(
        "task_12345678",
        "codex",
        output_root,
        {
            "modelGrant": "wfg_secret_credential",
            "modelGatewayUrl": "http://10.20.30.40:8080/model/v1",
            "outputSchema": {},
        },
        container_spec=container_spec(tmp_path),
    )

    assert result == {"summary": "ok"}
    assert victim.read_text(encoding="utf-8") == "must-not-change"


@pytest.mark.asyncio
async def test_analyzer_removes_container_when_clean_restart_cannot_be_proved(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class UnsafeContainer(ContainerNames):
        removed: list[str] = []

        @classmethod
        def restart_clean(cls, spec: AnalysisContainerSpec) -> None:
            del spec
            raise RuntimeError("constraint verification failed")

        @classmethod
        def remove(cls, task_id: str) -> None:
            cls.removed.append(task_id)

    output_root = tmp_path / "output"
    analyzer_root = output_root / "codex"
    analyzer_root.mkdir(parents=True, mode=0o770)

    async def create_subprocess(*_command: str, **_options: object) -> CompletedProcess:
        (analyzer_root / "result.json").write_text(
            json.dumps({"summary": "must-not-return"}),
            encoding="utf-8",
        )
        return CompletedProcess()

    monkeypatch.setattr(asyncio, "create_subprocess_exec", create_subprocess)
    UnsafeContainer.removed = []

    with pytest.raises(AnalyzerExecutionError, match="干净进程状态"):
        await DockerAnalyzerExecutor(  # type: ignore[arg-type]
            UnsafeContainer(),
            relay_factory=FakeRelayFactory(),
        ).execute(
            "task_12345678",
            "codex",
            output_root,
            {
                "modelGrant": "wfg_secret_credential",
                "modelGatewayUrl": "http://10.20.30.40:8080/model/v1",
                "outputSchema": {},
            },
            container_spec=container_spec(tmp_path),
        )

    assert UnsafeContainer.removed == ["task_12345678"]


@pytest.mark.asyncio
async def test_analyzer_does_not_hide_container_removal_failure_after_unsafe_restart(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class UnremovableContainer(ContainerNames):
        @classmethod
        def restart_clean(cls, spec: AnalysisContainerSpec) -> None:
            del spec
            raise RuntimeError("constraint verification failed")

        @classmethod
        def remove(cls, task_id: str) -> None:
            del task_id
            raise RuntimeError("container removal failed")

    output_root = tmp_path / "output"
    analyzer_root = output_root / "codex"
    analyzer_root.mkdir(parents=True, mode=0o770)

    async def create_subprocess(*_command: str, **_options: object) -> CompletedProcess:
        (analyzer_root / "result.json").write_text(
            json.dumps({"summary": "must-not-return"}),
            encoding="utf-8",
        )
        return CompletedProcess()

    monkeypatch.setattr(asyncio, "create_subprocess_exec", create_subprocess)

    with pytest.raises(AnalyzerExecutionError, match="干净进程状态") as caught:
        await DockerAnalyzerExecutor(  # type: ignore[arg-type]
            UnremovableContainer(),
            relay_factory=FakeRelayFactory(),
        ).execute(
            "task_12345678",
            "codex",
            output_root,
            {
                "modelGrant": "wfg_secret_credential",
                "modelGatewayUrl": "http://10.20.30.40:8080/model/v1",
                "outputSchema": {},
            },
            container_spec=container_spec(tmp_path),
        )

    assert caught.value.container_unsafe is True
    assert str(caught.value.__cause__) == "container removal failed"


@pytest.mark.asyncio
async def test_analyzer_passes_frozen_spec_to_post_execution_restart(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class SpecRecordingContainer(ContainerNames):
        reset_specs: list[AnalysisContainerSpec] = []

        @classmethod
        def restart_clean(cls, spec: AnalysisContainerSpec) -> None:
            cls.reset_specs.append(spec)

    output_root = tmp_path / "output"
    analyzer_root = output_root / "codex"
    analyzer_root.mkdir(parents=True, mode=0o770)
    spec = container_spec(tmp_path)

    async def create_subprocess(*_command: str, **_options: object) -> CompletedProcess:
        (analyzer_root / "result.json").write_text(
            json.dumps({"summary": "ok"}),
            encoding="utf-8",
        )
        return CompletedProcess()

    monkeypatch.setattr(asyncio, "create_subprocess_exec", create_subprocess)
    SpecRecordingContainer.reset_specs = []

    result = await DockerAnalyzerExecutor(  # type: ignore[arg-type]
        SpecRecordingContainer(),
        relay_factory=FakeRelayFactory(),
    ).execute(
        "task_12345678",
        "codex",
        output_root,
        {
            "modelGrant": "wfg_secret_credential",
            "modelGatewayUrl": "http://10.20.30.40:8080/model/v1",
            "outputSchema": {},
        },
        container_spec=spec,
    )

    assert result == {"summary": "ok"}
    assert SpecRecordingContainer.reset_specs == [spec]
