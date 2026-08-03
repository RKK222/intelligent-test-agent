from __future__ import annotations

import asyncio
import json

import pytest

from testagent_runner.model_relay import DockerModelRelay, ModelRelayError


class ContainerNames:
    @staticmethod
    def container_name(task_id: str) -> str:
        return f"container-{task_id}"


class Writer:
    def __init__(self) -> None:
        self.value = bytearray()
        self.closed = False

    def write(self, value: bytes) -> None:
        self.value.extend(value)

    async def drain(self) -> None:
        return None

    def close(self) -> None:
        self.closed = True

    async def wait_closed(self) -> None:
        return None


class Reader:
    def __init__(self, line: bytes = b"READY\n") -> None:
        self.line = line

    async def readline(self) -> bytes:
        return self.line


class RelayProcess:
    def __init__(self, line: bytes = b"READY\n", wait_code: int = 0) -> None:
        self.stdin = Writer()
        self.stdout = Reader(line)
        self.returncode: int | None = None
        self.terminated = False
        self.killed = False
        self.wait_code = wait_code

    async def wait(self) -> int:
        self.returncode = self.wait_code
        return self.wait_code

    def terminate(self) -> None:
        self.terminated = True

    def kill(self) -> None:
        self.killed = True


@pytest.mark.asyncio
async def test_relay_sends_real_grant_only_over_stdin_to_a_distinct_container_user(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    process = RelayProcess()
    commands: list[tuple[str, ...]] = []

    async def create_subprocess(*command: str, **options: object) -> RelayProcess:
        commands.append(command)
        assert options["stdin"] == asyncio.subprocess.PIPE
        assert options["stdout"] == asyncio.subprocess.PIPE
        return process

    monkeypatch.setattr(asyncio, "create_subprocess_exec", create_subprocess)
    relay = DockerModelRelay(
        ContainerNames(),  # type: ignore[arg-type]
        "task_12345678",
        "codex",
        "http://10.20.30.40:8080/api/internal/platform/model-gateway/v1",
        "wfg_real_platform_grant_123456",
    )

    async with relay as access:
        assert access.base_url == "http://127.0.0.1:18080/v1"
        assert access.local_token.startswith("relay_")

    command_text = "\0".join(commands[0])
    assert "wfg_real_platform_grant_123456" not in command_text
    assert "10.20.30.40" not in command_text
    assert commands[0][commands[0].index("--user") + 1] == "10002:10002"
    assert commands[0][commands[0].index("--workdir") + 1] == "/"
    assert "--interactive" in commands[0]
    configuration = json.loads(bytes(process.stdin.value).decode().strip())
    assert configuration == {
        "localToken": access.local_token,
        "port": 18080,
        "upstreamGrant": "wfg_real_platform_grant_123456",
        "upstreamUrl": "http://10.20.30.40:8080/api/internal/platform/model-gateway/v1",
    }
    assert process.stdin.closed is True


@pytest.mark.asyncio
async def test_relay_fails_closed_when_container_helper_does_not_become_ready(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    process = RelayProcess(b"FAILED\n")

    async def create_subprocess(*_command: str, **_options: object) -> RelayProcess:
        return process

    monkeypatch.setattr(asyncio, "create_subprocess_exec", create_subprocess)

    with pytest.raises(ModelRelayError, match="就绪"):
        async with DockerModelRelay(
            ContainerNames(),  # type: ignore[arg-type]
            "task_12345678",
            "opencode",
            "http://10.20.30.40:8080/api/internal/platform/model-gateway/v1",
            "wfg_real_platform_grant_123456",
        ):
            pass

    assert process.stdin.closed is True


@pytest.mark.asyncio
async def test_relay_fails_closed_when_helper_exits_nonzero_after_ready(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    process = RelayProcess(wait_code=78)

    async def create_subprocess(*_command: str, **_options: object) -> RelayProcess:
        return process

    monkeypatch.setattr(asyncio, "create_subprocess_exec", create_subprocess)

    with pytest.raises(ModelRelayError, match="退出"):
        async with DockerModelRelay(
            ContainerNames(),  # type: ignore[arg-type]
            "task_12345678",
            "codex",
            "http://10.20.30.40:8080/api/internal/platform/model-gateway/v1",
            "wfg_real_platform_grant_123456",
        ):
            pass
