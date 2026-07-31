#!/usr/bin/env python3
"""以独立UID持有平台grant，仅向本容器回环地址提供最小OpenAI转发面。"""

from __future__ import annotations

from dataclasses import dataclass
import hmac
from http.client import HTTPConnection, HTTPSConnection
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import ipaddress
import json
import re
import ssl
import sys
import threading
from typing import Any
from urllib.parse import SplitResult, urlsplit


MAX_CONFIG_BYTES = 8 * 1024
MAX_REQUEST_BYTES = 16 * 1024 * 1024
ALLOWED_ROUTES = {
    ("GET", "/v1/models"): "/models",
    ("POST", "/v1/chat/completions"): "/chat/completions",
    ("POST", "/v1/responses"): "/responses",
}
SAFE_REQUEST_HEADERS = {"accept", "content-type", "openai-beta"}
SAFE_RESPONSE_HEADERS = {
    "cache-control",
    "content-type",
    "openai-processing-ms",
    "x-request-id",
}


@dataclass(frozen=True, slots=True)
class RelayConfig:
    local_token: str
    port: int
    upstream_grant: str
    upstream: SplitResult

    @classmethod
    def parse(cls, value: dict[str, Any]) -> "RelayConfig":
        local_token = str(value.get("localToken", ""))
        upstream_grant = str(value.get("upstreamGrant", ""))
        if not re.fullmatch(r"relay_[A-Za-z0-9_-]{32,128}", local_token):
            raise ValueError("本地relay token格式无效")
        if not re.fullmatch(r"wfg_[A-Za-z0-9_-]{16,508}", upstream_grant):
            raise ValueError("平台模型grant格式无效")
        port = int(value.get("port", 0))
        if port not in {18080, 18081, 18082}:
            raise ValueError("relay端口不在固定白名单")
        upstream = urlsplit(str(value.get("upstreamUrl", "")))
        if (
            upstream.scheme not in {"http", "https"}
            or not upstream.hostname
            or upstream.username
            or upstream.password
            or upstream.query
            or upstream.fragment
        ):
            raise ValueError("模型网关地址格式无效")
        address = ipaddress.ip_address(upstream.hostname)
        if address.version != 4:
            raise ValueError("模型网关必须使用固定IPv4")
        if upstream.path != "/api/internal/platform/model-gateway/v1":
            raise ValueError("模型网关base path不在固定白名单")
        return cls(local_token, port, upstream_grant, upstream)


class RelayServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, config: RelayConfig) -> None:
        self.config = config
        super().__init__(("127.0.0.1", config.port), RelayHandler)


class RelayHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "test-agent-model-relay"
    sys_version = ""

    @property
    def config(self) -> RelayConfig:
        server = self.server
        if not isinstance(server, RelayServer):
            raise RuntimeError("relay server类型无效")
        return server.config

    def do_GET(self) -> None:  # noqa: N802
        self._forward()

    def do_POST(self) -> None:  # noqa: N802
        self._forward()

    def _forward(self) -> None:
        route = ALLOWED_ROUTES.get((self.command, self.path))
        if route is None:
            self._error(404, "NOT_FOUND")
            return
        authorization = self.headers.get("Authorization", "")
        expected = f"Bearer {self.config.local_token}"
        if not hmac.compare_digest(authorization, expected):
            self._error(401, "UNAUTHENTICATED")
            return
        try:
            body = self._request_body()
        except ValueError:
            self._error(400, "INVALID_REQUEST")
            return
        connection: HTTPConnection | HTTPSConnection | None = None
        response_started = False
        try:
            upstream = self.config.upstream
            port = upstream.port or (443 if upstream.scheme == "https" else 80)
            connection_type = (
                HTTPSConnection if upstream.scheme == "https" else HTTPConnection
            )
            options: dict[str, Any] = {"timeout": 900}
            if upstream.scheme == "https":
                options["context"] = ssl.create_default_context()
            connection = connection_type(upstream.hostname, port, **options)
            headers = {
                "Authorization": f"Bearer {self.config.upstream_grant}",
                "User-Agent": "test-agent-analysis-relay/1",
            }
            for name in SAFE_REQUEST_HEADERS:
                if value := self.headers.get(name):
                    headers[name] = value
            connection.request(
                self.command,
                upstream.path + route,
                body=body if self.command == "POST" else None,
                headers=headers,
            )
            response = connection.getresponse()
            self.send_response_only(response.status)
            for name, value in response.getheaders():
                if (
                    name.lower() in SAFE_RESPONSE_HEADERS
                    and "\r" not in value
                    and "\n" not in value
                ):
                    self.send_header(name, value)
            self.send_header("Transfer-Encoding", "chunked")
            self.end_headers()
            response_started = True
            while chunk := response.read(64 * 1024):
                self.wfile.write(f"{len(chunk):X}\r\n".encode("ascii"))
                self.wfile.write(chunk)
                self.wfile.write(b"\r\n")
                self.wfile.flush()
            self.wfile.write(b"0\r\n\r\n")
            self.wfile.flush()
        except (BrokenPipeError, ConnectionError, OSError):
            if not response_started and not self.wfile.closed:
                try:
                    self._error(502, "MODEL_GATEWAY_UNAVAILABLE")
                except (BrokenPipeError, ConnectionError, OSError):
                    pass
        finally:
            if connection is not None:
                connection.close()

    def _request_body(self) -> bytes:
        if self.command != "POST":
            return b""
        if self.headers.get("Transfer-Encoding"):
            raise ValueError("不接受chunked请求")
        try:
            length = int(self.headers.get("Content-Length", ""))
        except ValueError as exception:
            raise ValueError("请求缺少Content-Length") from exception
        if length < 0 or length > MAX_REQUEST_BYTES:
            raise ValueError("请求体超过上限")
        body = self.rfile.read(length)
        if len(body) != length:
            raise ValueError("请求体不完整")
        return body

    def _error(self, status: int, code: str) -> None:
        body = json.dumps({"error": {"code": code}}, separators=(",", ":")).encode()
        self.send_response_only(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(body)
        self.close_connection = True

    def log_message(self, *_arguments: object) -> None:
        # 任何请求头、路径或下游错误都不进入容器日志。
        return None


def main() -> int:
    try:
        raw = sys.stdin.buffer.readline(MAX_CONFIG_BYTES + 1)
        if not raw or len(raw) > MAX_CONFIG_BYTES:
            return 78
        value = json.loads(raw)
        if not isinstance(value, dict):
            return 78
        config = RelayConfig.parse(value)
        server = RelayServer(config)
    except (ValueError, TypeError, json.JSONDecodeError, OSError):
        return 78
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    print("READY", flush=True)
    # Runner保持stdin打开作为生命线；关闭或取消时relay立即退出并清空内存grant。
    sys.stdin.buffer.read()
    server.shutdown()
    server.server_close()
    thread.join(timeout=5)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
