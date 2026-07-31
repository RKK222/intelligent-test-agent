"""Worker到Runner请求的时钟窗口、HMAC与nonce防重放。"""

from __future__ import annotations

import hashlib
import hmac
from pathlib import Path
import re
import sqlite3
import threading
import time
from typing import Mapping, Protocol


class RunnerRequestError(RuntimeError):
    pass


class NonceStore(Protocol):
    def reserve(self, nonce: str, expires_at: float) -> bool: ...


class InMemoryNonceStore:
    """单进程Runner使用；企业部署只运行一个controller实例。"""

    def __init__(self) -> None:
        self._values: dict[str, float] = {}
        self._lock = threading.Lock()

    def reserve(self, nonce: str, expires_at: float) -> bool:
        now = time.time()
        with self._lock:
            self._values = {key: expiry for key, expiry in self._values.items() if expiry > now}
            if nonce in self._values:
                return False
            self._values[nonce] = expires_at
            return True


class SqliteNonceStore:
    """在Runner重启之间保留短时nonce摘要，避免重启窗口绕过防重放。"""

    def __init__(self, path: Path, *, clock=time.time) -> None:  # type: ignore[no-untyped-def]
        self._path = path
        self._clock = clock
        self._path.parent.mkdir(parents=True, exist_ok=True)
        with self._connect() as connection:
            connection.execute(
                "CREATE TABLE IF NOT EXISTS runner_nonces (digest TEXT PRIMARY KEY, expires_at REAL NOT NULL)"
            )
        self._path.chmod(0o600)

    def reserve(self, nonce: str, expires_at: float) -> bool:
        digest = hashlib.sha256(nonce.encode()).hexdigest()
        connection = self._connect()
        try:
            connection.execute("BEGIN IMMEDIATE")
            connection.execute(
                "DELETE FROM runner_nonces WHERE expires_at <= ?",
                (float(self._clock()),),
            )
            try:
                connection.execute(
                    "INSERT INTO runner_nonces(digest, expires_at) VALUES (?, ?)",
                    (digest, float(expires_at)),
                )
            except sqlite3.IntegrityError:
                connection.rollback()
                return False
            connection.commit()
            return True
        finally:
            connection.close()

    def _connect(self) -> sqlite3.Connection:
        return sqlite3.connect(self._path, timeout=5, isolation_level=None)


class RunnerHmacAuthenticator:
    def __init__(
        self,
        secret: bytes,
        runner_id: str,
        nonces: NonceStore,
        *,
        clock=time.time,  # type: ignore[no-untyped-def]
        clock_window_seconds: int = 30,
    ) -> None:
        if len(secret) < 32:
            raise ValueError("Runner HMAC密钥至少需要32字节")
        self._secret = secret
        self._runner_id = runner_id
        self._nonces = nonces
        self._clock = clock
        self._window = clock_window_seconds

    def verify(
        self,
        method: str,
        path: str,
        body: bytes,
        headers: Mapping[str, str],
    ) -> None:
        normalized = {key.lower(): value for key, value in headers.items()}
        runner_id = normalized.get("x-workflow-runner-id", "")
        timestamp = normalized.get("x-workflow-timestamp", "")
        nonce = normalized.get("x-workflow-nonce", "")
        claimed_digest = normalized.get("x-workflow-body-sha256", "")
        signature = normalized.get("x-workflow-signature", "")
        if runner_id != self._runner_id:
            raise RunnerRequestError("Runner身份不匹配")
        try:
            timestamp_value = int(timestamp)
        except ValueError as exception:
            raise RunnerRequestError("Runner时间戳无效") from exception
        if abs(self._clock() - timestamp_value) > self._window:
            raise RunnerRequestError("Runner请求已过期")
        if re.fullmatch(r"[A-Za-z0-9._~-]{16,128}", nonce) is None:
            raise RunnerRequestError("Runner nonce无效")
        actual_digest = hashlib.sha256(body).hexdigest()
        if not hmac.compare_digest(claimed_digest, actual_digest):
            raise RunnerRequestError("Runner请求体摘要不匹配")
        canonical = "\n".join(
            [method.upper(), path, actual_digest, runner_id, timestamp, nonce]
        )
        expected = hmac.new(self._secret, canonical.encode(), hashlib.sha256).hexdigest()
        if not hmac.compare_digest(signature, expected):
            raise RunnerRequestError("Runner签名无效")
        if not self._nonces.reserve(nonce, self._clock() + self._window * 2):
            raise RunnerRequestError("Runner请求重放")
