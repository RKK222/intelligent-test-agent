from pathlib import Path
from typing import Any

import httpx
import pytest

from testagent_memory_service.api import AppDependencies, create_app
from testagent_memory_service.settings import MemoryServiceSettings


class FakeStore:
    def __init__(self) -> None:
        self.documents: dict[str, dict[str, Any]] = {}

    def raw_message_count(self) -> int:
        return 0

    def add(self, **kwargs: Any) -> dict[str, Any]:
        document = {
            "id": "00000000-0000-0000-0000-000000000001",
            "content": kwargs["content"],
            "metadata": kwargs["metadata"],
            "updatedAt": "2026-08-09T00:00:00Z",
        }
        self.documents[document["id"]] = document
        return document

    def get(self, memory_id: str) -> dict[str, Any] | None:
        return self.documents.get(memory_id)

    def update(self, memory_id: str, content: str, metadata: dict[str, Any]) -> dict[str, Any]:
        self.documents[memory_id]["content"] = content
        self.documents[memory_id]["metadata"].update(metadata)
        return self.documents[memory_id]

    def delete(self, memory_id: str) -> None:
        del self.documents[memory_id]

    def history(self, memory_id: str) -> list[dict[str, Any]]:
        return [{"memory_id": memory_id, "event": "ADD"}]

    def search(self, **kwargs: Any) -> list[dict[str, Any]]:
        del kwargs
        return [{**next(iter(self.documents.values())), "score": 0.9}]


class FakeExtractor:
    async def extract(self, **kwargs: Any) -> list[dict[str, Any]]:
        assert kwargs["grant"].startswith("mfg_")
        return [
            {
                "content": "分析结论必须有证据支撑",
                "scopeSuggestion": "PERSONAL_GLOBAL",
                "taskTypes": ["DEFECT_ANALYSIS"],
                "explicit": True,
                "temporary": False,
                "replacesExisting": False,
                "confidence": 0.98,
                "reason": "用户明确要求",
            }
        ]


@pytest.fixture
def configured(tmp_path: Path) -> tuple[MemoryServiceSettings, FakeStore]:
    settings = MemoryServiceSettings(
        _env_file=None,
        api_key="service-key-" + "k" * 32,
        history_db_path=tmp_path / "history.db",
        model_root=tmp_path / "models",
        model_path=tmp_path / "models" / "bge",
    )
    return settings, FakeStore()


@pytest.mark.asyncio
async def test_crud_search_history_and_zero_message_readiness(
    configured: tuple[MemoryServiceSettings, FakeStore],
) -> None:
    settings, store = configured
    app = create_app(AppDependencies(settings, store, FakeExtractor()))  # type: ignore[arg-type]
    transport = httpx.ASGITransport(app=app)
    headers = {"X-Memory-Service-Key": settings.api_key.get_secret_value()}
    async with httpx.AsyncClient(transport=transport, base_url="http://memory") as client:
        unauthorized = await client.post(
            "/memory-api/v1/documents", json={"content": "不能写入"}
        )
        assert unauthorized.status_code == 401

        ready = await client.get("/memory-api/v1/ready", headers=headers)
        assert ready.status_code == 200
        assert ready.json()["data"]["rawMessageCount"] == 0

        created = await client.post(
            "/memory-api/v1/documents",
            headers=headers,
            json={
                "content": "测试案例必须覆盖边界条件",
                "userId": "platform:u1",
                "applicationId": "app-1",
                "taskTypes": ["TEST_CASE_GENERATION"],
                "metadata": {"scope": "PERSONAL_APPLICATION", "source": "MANUAL"},
            },
        )
        assert created.status_code == 201
        memory_id = created.json()["data"]["id"]

        searched = await client.post(
            "/memory-api/v1/search",
            headers=headers,
            json={"query": "设计测试", "userId": "platform:u1", "topK": 6},
        )
        assert searched.json()["data"]["items"][0]["score"] == 0.9

        updated = await client.patch(
            f"/memory-api/v1/documents/{memory_id}",
            headers=headers,
            json={"content": "测试案例必须覆盖异常和边界条件", "metadata": {}},
        )
        assert "异常" in updated.json()["data"]["content"]

        history = await client.get(
            f"/memory-api/v1/documents/{memory_id}/history", headers=headers
        )
        assert history.json()["data"][0]["event"] == "ADD"

        deleted = await client.delete(
            f"/memory-api/v1/documents/{memory_id}", headers=headers
        )
        assert deleted.status_code == 204


@pytest.mark.asyncio
async def test_extract_is_transient_and_returns_governed_candidates(
    configured: tuple[MemoryServiceSettings, FakeStore],
) -> None:
    settings, store = configured
    app = create_app(AppDependencies(settings, store, FakeExtractor()))  # type: ignore[arg-type]
    transport = httpx.ASGITransport(app=app)
    headers = {"X-Memory-Service-Key": settings.api_key.get_secret_value()}
    async with httpx.AsyncClient(transport=transport, base_url="http://memory") as client:
        response = await client.post(
            "/memory-api/v1/extract",
            headers=headers,
            json={
                "model": "internal-chat",
                "modelGrant": "mfg_" + "g" * 32,
                "userId": "user-1",
                "runId": "run-1",
                "sessionId": "session-1",
                "taskType": "DEFECT_ANALYSIS",
                "messages": [
                    {"role": "user", "content": "结论必须带证据"},
                    {"role": "assistant", "content": "明白"},
                ],
            },
        )
    assert response.status_code == 200
    assert response.json()["data"]["candidates"][0]["explicit"] is True
    assert store.documents == {}
