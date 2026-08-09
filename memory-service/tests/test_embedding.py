import httpx

from mem0.configs.embeddings.base import BaseEmbedderConfig
from mem0.configs.llms.base import BaseLlmConfig

from testagent_memory_service.embedding import (
    GatewayEmbedding,
    GatewayMemoryLlm,
    ProviderRuntime,
)
from testagent_memory_service.gateway import (
    GatewayCallContext,
    HmacModelGatewayClient,
    gateway_call_context,
)
from testagent_memory_service.settings import MemoryServiceSettings


def test_gateway_embedding_batches_and_marks_query_type() -> None:
    captured: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        return httpx.Response(
            200,
            json={
                "data": [
                    {"index": 0, "embedding": [1.0, 0.0]},
                    {"index": 1, "embedding": [0.0, 1.0]},
                ]
            },
        )

    settings = MemoryServiceSettings(
        _env_file=None,
        api_key="service-key-" + "k" * 32,
        model_gateway_hmac_secret="gateway-secret-" + "s" * 32,
    )
    client = httpx.Client(transport=httpx.MockTransport(handler))
    gateway = HmacModelGatewayClient(settings, client)
    ProviderRuntime.install(gateway)
    provider = GatewayEmbedding(
        BaseEmbedderConfig(
            model="embedding-model", embedding_dims=2, model_kwargs={"timeout_seconds": 2}
        )
    )
    with gateway_call_context(
        GatewayCallContext(
            "u1",
            "run-1",
            "session-1",
            "operation-1",
            "trace_1",
            embedding_timeout_seconds=1.5,
        )
    ):
        vectors = provider.embed_batch(["边界", "异常"], "search")
    client.close()

    assert vectors == [[1.0, 0.0], [0.0, 1.0]]
    assert captured[0].headers["x-embedding-input-type"] == "query"
    assert captured[0].headers["x-memory-capability"] == "EMBEDDING"
    assert captured[0].headers["x-memory-signature"]
    assert "authorization" not in captured[0].headers
    assert captured[0].extensions["timeout"]["read"] == 1.5


def test_gateway_llm_uses_service_chat_timeout_by_default() -> None:
    captured: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        return httpx.Response(
            200,
            json={"choices": [{"message": {"content": "ok"}}]},
        )

    settings = MemoryServiceSettings(
        _env_file=None,
        api_key="service-key-" + "k" * 32,
        model_gateway_hmac_secret="gateway-secret-" + "s" * 32,
        chat_timeout_seconds=7.25,
    )
    client = httpx.Client(transport=httpx.MockTransport(handler))
    gateway = HmacModelGatewayClient(settings, client)
    ProviderRuntime.install(gateway)
    provider = GatewayMemoryLlm(BaseLlmConfig(model="memory-chat"))
    with gateway_call_context(
        GatewayCallContext(
            "u1",
            "run-1",
            "session-1",
            "operation-1",
            "trace_1",
        )
    ):
        assert provider.generate_response([{"role": "user", "content": "偏好"}]) == "ok"
    client.close()

    assert captured[0].extensions["timeout"]["read"] == 7.25
