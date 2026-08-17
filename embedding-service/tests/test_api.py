from typing import Any

import httpx
import pytest

from testagent_embedding_service.api import Dependencies, create_app
from testagent_embedding_service.settings import MODEL_DIMENSION, MODEL_ID, EmbeddingServiceSettings


class FakeModel:
    def __init__(self) -> None:
        self.calls: list[tuple[list[str], str]] = []

    def embed(self, values: list[str], input_type: str) -> list[list[float]]:
        self.calls.append((values, input_type))
        vector = [0.0] * MODEL_DIMENSION
        vector[0] = 1.0
        return [list(vector) for _ in values]


@pytest.mark.asyncio
async def test_openai_embeddings_requires_auth_fixed_model_and_input_type() -> None:
    settings = EmbeddingServiceSettings(
        _env_file=None,
        api_key="embedding-key-" + "k" * 32,
    )
    model = FakeModel()
    app = create_app(Dependencies(settings, model))  # type: ignore[arg-type]
    transport = httpx.ASGITransport(app=app)
    headers = {
        "Authorization": f"Bearer {settings.api_key.get_secret_value()}",
        "X-Embedding-Input-Type": "query",
    }
    async with httpx.AsyncClient(transport=transport, base_url="http://embedding") as client:
        unauthorized = await client.post(
            "/v1/embeddings", json={"model": MODEL_ID, "input": "测试"}
        )
        assert unauthorized.status_code == 401

        wrong_model = await client.post(
            "/v1/embeddings",
            headers=headers,
            json={"model": "other", "input": "测试"},
        )
        assert wrong_model.status_code == 422

        response = await client.post(
            "/v1/embeddings",
            headers=headers,
            json={"model": MODEL_ID, "input": ["边界", "异常"]},
        )
        assert response.status_code == 200
        payload: dict[str, Any] = response.json()
        assert len(payload["data"]) == 2
        assert len(payload["data"][0]["embedding"]) == MODEL_DIMENSION
        assert model.calls == [(["边界", "异常"], "query")]


@pytest.mark.asyncio
async def test_health_and_authenticated_ready() -> None:
    settings = EmbeddingServiceSettings(_env_file=None, api_key="ready-key-" + "x" * 32)
    app = create_app(Dependencies(settings, FakeModel()))  # type: ignore[arg-type]
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://embedding") as client:
        assert (await client.get("/health")).status_code == 200
        assert (await client.get("/ready")).status_code == 401
        ready = await client.get(
            "/ready",
            headers={"Authorization": f"Bearer {settings.api_key.get_secret_value()}"},
        )
        assert ready.status_code == 200
        assert ready.json()["dimension"] == 512
