"""在同一任务容器中为每个智能体创建独立HOME和输出目录。"""

from __future__ import annotations

import asyncio
from contextlib import suppress
import errno
import json
import os
from pathlib import Path
import stat
from typing import Any

from testagent_runner.docker_runtime import DockerRuntime
from testagent_runner.model_relay import DockerModelRelay, ModelRelayError, ModelRelayFactory


MAX_ANALYZER_RESULT_BYTES = 3 * 1024 * 1024


class AnalyzerExecutionError(RuntimeError):
    pass


_DIRECTORY_FLAGS = (
    os.O_RDONLY
    | getattr(os, "O_DIRECTORY", 0)
    | getattr(os, "O_NOFOLLOW", 0)
    | getattr(os, "O_CLOEXEC", 0)
)
_FILE_NOFOLLOW = getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_CLOEXEC", 0)


def _ensure_directory_at(parent_fd: int, name: str) -> None:
    """通过目录fd创建并验证目录，避免分析容器用符号链接劫持宿主路径。"""

    try:
        os.mkdir(name, mode=0o770, dir_fd=parent_fd)
    except FileExistsError:
        pass
    try:
        descriptor = os.open(name, _DIRECTORY_FLAGS, dir_fd=parent_fd)
    except OSError as exception:
        raise AnalyzerExecutionError("智能体私有目录不是安全的普通目录") from exception
    try:
        os.fchmod(descriptor, 0o770)
    finally:
        os.close(descriptor)


def _replace_file_at(parent_fd: int, name: str, value: bytes, mode: int) -> None:
    """原子创建Runner控制文件，最终分量和父目录均不跟随符号链接。"""

    try:
        os.unlink(name, dir_fd=parent_fd)
    except FileNotFoundError:
        pass
    try:
        descriptor = os.open(
            name,
            os.O_WRONLY | os.O_CREAT | os.O_EXCL | _FILE_NOFOLLOW,
            mode,
            dir_fd=parent_fd,
        )
    except OSError as exception:
        raise AnalyzerExecutionError("智能体控制文件创建失败") from exception
    try:
        with os.fdopen(descriptor, "wb", closefd=False) as stream:
            stream.write(value)
            stream.flush()
            os.fchmod(descriptor, mode)
    finally:
        os.close(descriptor)


def _read_result_at(parent_fd: int, name: str) -> dict[str, Any]:
    try:
        descriptor = os.open(name, os.O_RDONLY | _FILE_NOFOLLOW, dir_fd=parent_fd)
    except FileNotFoundError as exception:
        raise AnalyzerExecutionError("代码智能体未生成结果") from exception
    except OSError as exception:
        if exception.errno == errno.ELOOP:
            raise AnalyzerExecutionError("代码智能体结果必须是普通文件") from exception
        raise AnalyzerExecutionError("代码智能体结果读取失败") from exception
    try:
        metadata = os.fstat(descriptor)
        if not stat.S_ISREG(metadata.st_mode):
            raise AnalyzerExecutionError("代码智能体结果必须是普通文件")
        if metadata.st_size > MAX_ANALYZER_RESULT_BYTES:
            raise AnalyzerExecutionError("代码智能体结果超出安全上限")
        with os.fdopen(descriptor, "rb", closefd=False) as stream:
            raw = stream.read(MAX_ANALYZER_RESULT_BYTES + 1)
        if len(raw) > MAX_ANALYZER_RESULT_BYTES:
            raise AnalyzerExecutionError("代码智能体结果超出安全上限")
        try:
            value = json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exception:
            raise AnalyzerExecutionError("代码智能体结果格式无效") from exception
        if not isinstance(value, dict):
            raise AnalyzerExecutionError("代码智能体结果格式无效")
        return value
    finally:
        os.close(descriptor)


def _wipe_and_unlink_at(parent_fd: int, name: str) -> None:
    """只擦除目录fd下的普通文件；若智能体换成链接则仅删除链接本身。"""

    descriptor: int | None = None
    try:
        descriptor = os.open(name, os.O_WRONLY | _FILE_NOFOLLOW, dir_fd=parent_fd)
        metadata = os.fstat(descriptor)
        if stat.S_ISREG(metadata.st_mode):
            remaining = metadata.st_size
            zeros = b"\0" * min(remaining, 64 * 1024)
            while remaining > 0:
                written = os.write(descriptor, zeros[:remaining])
                remaining -= written
            os.fsync(descriptor)
    except FileNotFoundError:
        return
    except OSError:
        # ELOOP等错误表示容器已替换路径；不能跟随，只在finally删除目录项。
        pass
    finally:
        if descriptor is not None:
            os.close(descriptor)
        try:
            os.unlink(name, dir_fd=parent_fd)
        except FileNotFoundError:
            pass


class DockerAnalyzerExecutor:
    def __init__(
        self,
        docker: DockerRuntime,
        *,
        relay_factory: ModelRelayFactory | None = None,
    ) -> None:
        self._docker = docker
        self._relay_factory = relay_factory or (
            lambda task_id, analyzer_id, upstream_url, upstream_grant: DockerModelRelay(
                docker,
                task_id,
                analyzer_id,
                upstream_url,
                upstream_grant,
            )
        )

    async def execute(
        self,
        task_id: str,
        analyzer_id: str,
        output_root: Path,
        payload: dict[str, Any],
    ) -> dict[str, Any]:
        if analyzer_id not in {"codex", "opencode"}:
            raise AnalyzerExecutionError("未注册的代码智能体")
        trusted_output_root = output_root.resolve(strict=True)
        analyzer_root = trusted_output_root / analyzer_id
        analyzer_root.mkdir(exist_ok=True, mode=0o770)
        try:
            analyzer_fd = os.open(analyzer_root, _DIRECTORY_FLAGS)
        except OSError as exception:
            raise AnalyzerExecutionError("智能体输出目录不是安全的普通目录") from exception
        safe_payload = dict(payload)
        grant = str(safe_payload.pop("modelGrant"))
        upstream_url = str(safe_payload["modelGatewayUrl"])
        safe_payload["modelRelayTokenFile"] = (
            f"/workspace/output/{analyzer_id}/.model-relay-token"
        )
        execution_failure: BaseException | None = None
        try:
            async with self._relay_factory(
                task_id,
                analyzer_id,
                upstream_url,
                grant,
            ) as relay:
                safe_payload["modelGatewayUrl"] = relay.base_url
                os.fchmod(analyzer_fd, 0o770)
                for directory in ("home", "cache"):
                    _ensure_directory_at(analyzer_fd, directory)
                _replace_file_at(
                    analyzer_fd,
                    "request.json",
                    json.dumps(safe_payload, ensure_ascii=False).encode("utf-8"),
                    0o640,
                )
                # 分析进程只获得回环relay的一次性本地token，绝不接触平台grant。
                _replace_file_at(
                    analyzer_fd,
                    ".model-relay-token",
                    relay.local_token.encode("utf-8"),
                    0o640,
                )
                try:
                    os.unlink("result.json", dir_fd=analyzer_fd)
                except FileNotFoundError:
                    pass
                command = [
                    "docker",
                    "exec",
                    "--user",
                    "10001:10003",
                    "--env",
                    f"HOME=/workspace/output/{analyzer_id}/home",
                    "--env",
                    f"XDG_CACHE_HOME=/workspace/output/{analyzer_id}/cache",
                    "--workdir",
                    "/workspace/repos",
                    self._docker.container_name(task_id),
                    "/usr/local/bin/test-agent-analysis",
                    analyzer_id,
                    f"/workspace/output/{analyzer_id}/request.json",
                    f"/workspace/output/{analyzer_id}/result.json",
                ]
                process = await asyncio.create_subprocess_exec(
                    *command,
                    stdout=asyncio.subprocess.DEVNULL,
                    stderr=asyncio.subprocess.DEVNULL,
                )
                # 产品不设置固定总时长，取消由停止任务容器完成。
                return_code = await process.wait()
                if return_code != 0:
                    raise AnalyzerExecutionError("代码智能体执行失败")
                return _read_result_at(analyzer_fd, "result.json")
        except ModelRelayError as exception:
            execution_failure = AnalyzerExecutionError("模型凭据relay启动或关闭失败")
            raise execution_failure from exception
        except BaseException as exception:
            execution_failure = exception
            raise
        finally:
            _wipe_and_unlink_at(analyzer_fd, ".model-relay-token")
            try:
                if not isinstance(execution_failure, asyncio.CancelledError):
                    await asyncio.to_thread(self._docker.restart_clean, task_id)
            except Exception as exception:
                with suppress(Exception):
                    await asyncio.to_thread(self._docker.remove, task_id)
                raise AnalyzerExecutionError("分析容器无法恢复到干净进程状态") from exception
            finally:
                os.close(analyzer_fd)
