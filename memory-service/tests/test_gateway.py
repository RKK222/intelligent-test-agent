from hashlib import sha256
import hmac

import httpx

from testagent_memory_service.gateway import (
    GatewayCallContext,
    HmacModelGatewayClient,
    gateway_call_context,
)
from testagent_memory_service.settings import MemoryServiceSettings


def test_hmac_covers_body_identity_nonce_and_capability() -> None:
    captured: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        return httpx.Response(200, json={"choices": [{"message": {"content": "{}"}}]})

    secret = "gateway-secret-" + "s" * 32
    settings = MemoryServiceSettings(
        _env_file=None,
        api_key="service-key-" + "k" * 32,
        model_gateway_hmac_secret=secret,
    )
    client = httpx.Client(transport=httpx.MockTransport(handler))
    gateway = HmacModelGatewayClient(settings, client)
    with gateway_call_context(
        GatewayCallContext("u1", "run-1", "session-1", "operation-1", "trace_1")
    ):
        gateway.post_json(
            "/chat/completions",
            {"model": "chat", "messages": []},
            capability="CHAT",
            timeout_seconds=2,
        )
    request = captured[0]
    body_digest = sha256(request.content).hexdigest()
    canonical = "\n".join(
        [
            "POST",
            request.url.path,
            body_digest,
            request.headers["x-memory-client-id"],
            "u1",
            "run-1",
            "session-1",
            "operation-1",
            request.headers["x-memory-timestamp"],
            request.headers["x-memory-nonce"],
            "CHAT",
            "",
        ]
    )
    expected = hmac.new(secret.encode(), canonical.encode(), "sha256").hexdigest()
    assert hmac.compare_digest(expected, request.headers["x-memory-signature"])
    assert request.headers["x-memory-body-sha256"] == body_digest
    client.close()


def test_embedding_input_type_is_signed() -> None:
    captured: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        return httpx.Response(200, json={"data": [{"index": 0, "embedding": [1.0]}]})

    secret = "gateway-secret-" + "s" * 32
    settings = MemoryServiceSettings(
        _env_file=None,
        api_key="service-key-" + "k" * 32,
        model_gateway_hmac_secret=secret,
    )
    client = httpx.Client(transport=httpx.MockTransport(handler))
    gateway = HmacModelGatewayClient(settings, client)
    with gateway_call_context(
        GatewayCallContext("u1", "run-1", "session-1", "operation-2", "trace_2")
    ):
        gateway.post_json(
            "/embeddings",
            {"model": "embedding", "input": ["查询"]},
            capability="EMBEDDING",
            timeout_seconds=2,
            input_type="query",
        )
    request = captured[0]
    canonical = "\n".join([
        "POST",
        request.url.path,
        sha256(request.content).hexdigest(),
        request.headers["x-memory-client-id"],
        "u1",
        "run-1",
        "session-1",
        "operation-2",
        request.headers["x-memory-timestamp"],
        request.headers["x-memory-nonce"],
        "EMBEDDING",
        "query",
    ])
    expected = hmac.new(secret.encode(), canonical.encode(), "sha256").hexdigest()
    assert hmac.compare_digest(expected, request.headers["x-memory-signature"])
    assert request.headers["x-embedding-input-type"] == "query"
    client.close()
