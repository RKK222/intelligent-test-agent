"""Mem0 到 Java 模型网关的 HMAC 客户端；请求身份与正文摘要共同签名。"""

from __future__ import annotations

from contextlib import contextmanager
from contextvars import ContextVar
from dataclasses import dataclass
from hashlib import sha256
import hmac
import json
import time
from typing import Any, Iterator
from uuid import uuid4

import httpx

from testagent_memory_service.settings import MemoryServiceSettings


@dataclass(frozen=True, slots=True)
class GatewayCallContext:
    user_id: str
    run_id: str
    session_id: str
    operation_id: str
    trace_id: str
    chat_model_id: str | None = None
    embedding_timeout_seconds: float | None = None


_CALL_CONTEXT: ContextVar[GatewayCallContext | None] = ContextVar(
    "memory_gateway_call_context", default=None
)


@contextmanager
def gateway_call_context(context: GatewayCallContext) -> Iterator[None]:
    token = _CALL_CONTEXT.set(context)
    try:
        yield
    finally:
        _CALL_CONTEXT.reset(token)


def current_gateway_context() -> GatewayCallContext | None:
    return _CALL_CONTEXT.get()


class GatewayUnavailable(RuntimeError):
    """模型网关或其目标 provider 当前不可用。"""


class HmacModelGatewayClient:
    def __init__(
        self,
        settings: MemoryServiceSettings,
        client: httpx.Client | None = None,
    ):
        self.settings = settings
        self._owns_client = client is None
        self.client = client or httpx.Client(follow_redirects=False, trust_env=False)

    def close(self) -> None:
        if self._owns_client:
            self.client.close()

    def post_json(
        self,
        path: str,
        payload: dict[str, Any],
        *,
        capability: str,
        timeout_seconds: float,
        input_type: str | None = None,
    ) -> dict[str, Any]:
        context = _CALL_CONTEXT.get()
        if context is None:
            raise GatewayUnavailable("模型网关调用缺少记忆操作上下文")
        normalized_path = "/" + path.lstrip("/")
        request_path = f"/api/internal/platform/model-gateway/v1{normalized_path}"
        body = json.dumps(
            payload, ensure_ascii=False, separators=(",", ":"), sort_keys=True
        ).encode("utf-8")
        body_digest = sha256(body).hexdigest()
        timestamp = str(int(time.time()))
        nonce = uuid4().hex
        canonical = "\n".join(
            [
                "POST",
                request_path,
                body_digest,
                self.settings.model_gateway_client_id,
                context.user_id,
                context.run_id,
                context.session_id,
                context.operation_id,
                timestamp,
                nonce,
                capability,
                input_type or "",
            ]
        )
        signature = hmac.new(
            self.settings.model_gateway_hmac_secret.get_secret_value().encode("utf-8"),
            canonical.encode("utf-8"),
            "sha256",
        ).hexdigest()
        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json",
            "X-Trace-Id": context.trace_id,
            "X-Memory-Client-Id": self.settings.model_gateway_client_id,
            "X-Memory-User-Id": context.user_id,
            "X-Memory-Run-Id": context.run_id,
            "X-Memory-Session-Id": context.session_id,
            "X-Memory-Operation-Id": context.operation_id,
            "X-Memory-Timestamp": timestamp,
            "X-Memory-Nonce": nonce,
            "X-Memory-Body-SHA256": body_digest,
            "X-Memory-Capability": capability,
            "X-Memory-Signature": signature,
        }
        if input_type is not None:
            headers["X-Embedding-Input-Type"] = input_type
        try:
            response = self.client.post(
                f"{self.settings.model_gateway_url}{normalized_path}",
                headers=headers,
                content=body,
                timeout=timeout_seconds,
            )
        except httpx.HTTPError as exception:
            raise GatewayUnavailable("平台模型网关不可用") from exception
        if response.status_code in {401, 403}:
            raise PermissionError("平台模型网关拒绝记忆服务 HMAC")
        if response.status_code < 200 or response.status_code >= 300:
            raise GatewayUnavailable(f"平台模型网关返回 {response.status_code}")
        try:
            result = response.json()
        except ValueError as exception:
            raise GatewayUnavailable("平台模型网关响应不是 JSON") from exception
        if not isinstance(result, dict):
            raise GatewayUnavailable("平台模型网关响应格式无效")
        return result
