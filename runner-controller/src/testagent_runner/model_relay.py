"""在分析容器内以独立UID运行短期模型凭据relay。"""

from __future__ import annotations

import asyncio
from contextlib import suppress
from dataclasses import dataclass
import json
import secrets
from typing import AsyncContextManager, Callable, Protocol


class ModelRelayError(RuntimeError):
    """真实平台grant未能建立安全的容器内relay。"""


class ContainerNames(Protocol):
    def container_name(self, task_id: str) -> str: ...


@dataclass(frozen=True, slots=True)
class ModelRelayAccess:
    base_url: str
    local_token: str


ModelRelayFactory = Callable[
    [str, str, str, str],
    AsyncContextManager[ModelRelayAccess],
]


class DockerModelRelay:
    """平台grant仅通过docker exec stdin交给UID 10002，绝不进入分析进程。"""

    _PORTS = {"codex": 18080, "opencode": 18081}

    def __init__(
        self,
        docker: ContainerNames,
        task_id: str,
        analyzer_id: str,
        upstream_url: str,
        upstream_grant: str,
    ) -> None:
        try:
            self._port = self._PORTS[analyzer_id]
        except KeyError as exception:
            raise ModelRelayError("未注册智能体的relay端口") from exception
        self._docker = docker
        self._task_id = task_id
        self._upstream_url = upstream_url
        self._upstream_grant = upstream_grant
        self._local_token = "relay_" + secrets.token_urlsafe(32)
        self._process: asyncio.subprocess.Process | None = None

    async def __aenter__(self) -> ModelRelayAccess:
        command = [
            "docker",
            "exec",
            "--interactive",
            "--user",
            "10002:10002",
            "--workdir",
            "/",
            self._docker.container_name(self._task_id),
            "/usr/local/bin/test-agent-model-relay",
        ]
        try:
            process = await asyncio.create_subprocess_exec(
                *command,
                stdin=asyncio.subprocess.PIPE,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.DEVNULL,
            )
            self._process = process
            if process.stdin is None or process.stdout is None:
                raise ModelRelayError("模型relay控制管道不可用")
            configuration = {
                "localToken": self._local_token,
                "port": self._port,
                "upstreamGrant": self._upstream_grant,
                "upstreamUrl": self._upstream_url,
            }
            process.stdin.write(
                (json.dumps(configuration, separators=(",", ":")) + "\n").encode()
            )
            await process.stdin.drain()
            ready = await asyncio.wait_for(process.stdout.readline(), timeout=10)
            if ready != b"READY\n":
                raise ModelRelayError("模型relay未通过就绪检查")
            return ModelRelayAccess(
                base_url=f"http://127.0.0.1:{self._port}/v1",
                local_token=self._local_token,
            )
        except ModelRelayError:
            await self._stop()
            raise
        except (OSError, asyncio.TimeoutError) as exception:
            await self._stop()
            raise ModelRelayError("模型relay启动失败") from exception

    async def __aexit__(
        self,
        exception_type: type[BaseException] | None,
        _exception: BaseException | None,
        _traceback: object,
    ) -> None:
        return_code = await self._stop()
        if exception_type is None and return_code != 0:
            raise ModelRelayError("模型relay退出状态无效")

    async def _stop(self) -> int | None:
        process = self._process
        self._process = None
        if process is None:
            return None
        if process.stdin is not None:
            process.stdin.close()
            with suppress(BrokenPipeError, ConnectionError):
                await process.stdin.wait_closed()
        try:
            return await asyncio.wait_for(process.wait(), timeout=10)
        except asyncio.TimeoutError:
            process.terminate()
        try:
            return await asyncio.wait_for(process.wait(), timeout=5)
        except asyncio.TimeoutError:
            process.kill()
            with suppress(asyncio.TimeoutError):
                return await asyncio.wait_for(process.wait(), timeout=5)
        return process.returncode
