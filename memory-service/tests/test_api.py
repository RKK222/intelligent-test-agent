from typing import Any

import httpx
import pytest

from testagent_memory_service.api import AppDependencies, create_app
from testagent_memory_service.settings import MemoryServiceSettings


class FakeControl:
    def projection_backlog(self) -> dict[str, int]:
        return {"PENDING": 0, "PROCESSING": 0, "DEAD": 0}


class FakeStore:
    def __init__(self) -> None:
        self.control = FakeControl()
        self.documents: dict[str, dict[str, Any]] = {}
        self.last_messages: Any = None

    def raw_message_count(self) -> int:
        return 0

    def readiness(self) -> dict[str, bool]:
        return {"cpu": True}

    def add(self, **kwargs: Any) -> list[dict[str, Any]]:
        messages = kwargs["messages"]
        self.last_messages = messages
        assert kwargs["infer"] is True
        assert isinstance(messages, list)
        document = {
            "id": "lmem_00000000000000000000000000000001",
            "content": "用户偏好中文回答",
            "metadata": kwargs["metadata"],
            "updatedAt": "2026-08-09T00:00:00Z",
        }
        self.documents[document["id"]] = document
        return [document]

    def get(self, memory_id: str) -> dict[str, Any] | None:
        return self.documents.get(memory_id)

    def update(self, memory_id: str, content: str, metadata: dict[str, Any], **kwargs: Any) -> dict[str, Any]:
        del kwargs
        self.documents[memory_id]["content"] = content
        self.documents[memory_id]["metadata"].update(metadata)
        return self.documents[memory_id]

    def delete(self, memory_id: str, **kwargs: Any) -> None:
        del kwargs
        del self.documents[memory_id]

    def history(self, memory_id: str) -> list[dict[str, Any]]:
        return [{"memory_id": memory_id, "event": "ADD"}]

    def search(self, **kwargs: Any) -> list[dict[str, Any]]:
        assert len(kwargs["scopes"]) == 3
        return [{**next(iter(self.documents.values())), "score": 0.032}]


@pytest.fixture
def configured() -> tuple[MemoryServiceSettings, FakeStore]:
    settings = MemoryServiceSettings(
        _env_file=None,
        api_key="service-key-" + "k" * 32,
        model_gateway_hmac_secret="gateway-secret-" + "s" * 32,
    )
    return settings, FakeStore()


@pytest.mark.asyncio
async def test_native_add_crud_combined_search_and_legacy_gone(
    configured: tuple[MemoryServiceSettings, FakeStore],
) -> None:
    settings, store = configured
    app = create_app(AppDependencies(settings, store))  # type: ignore[arg-type]
    transport = httpx.ASGITransport(app=app)
    headers = {"X-Memory-Service-Key": settings.api_key.get_secret_value()}
    context = {
        "requesterUserId": "u1",
        "runId": "run-0001",
        "sessionId": "session-0001",
        "operationId": "operation-0001",
    }
    async with httpx.AsyncClient(transport=transport, base_url="http://memory") as client:
        unauthorized = await client.post("/memories", json={})
        assert unauthorized.status_code == 401

        ready = await client.get("/ready", headers=headers)
        assert ready.status_code == 200
        assert ready.json()["rawMessageCount"] == 0

        created = await client.post(
            "/memories",
            headers=headers,
            json={
                **context,
                "messages": [
                    {"role": "user", "content": "  以后请使用中文回答\n并保留分行  "},
                    {"role": "assistant", "content": "好的"},
                ],
                "infer": True,
                "chatModelId": "memory-chat",
                "userId": "platform:u1",
                "applicationId": "app-1",
                "scope": "PERSONAL_APPLICATION",
                "metadata": {"source": "NATIVE"},
            },
        )
        assert created.status_code == 201
        assert store.last_messages[0]["content"] == "  以后请使用中文回答\n并保留分行  "
        memory_id = created.json()["results"][0]["id"]

        searched = await client.post(
            "/search",
            headers=headers,
            json={
                **context,
                "operationId": "operation-0002",
                "query": "回答语言",
                "topK": 6,
                "scopes": [
                    {"scope": "PERSONAL_GLOBAL", "userId": "platform:u1"},
                    {
                        "scope": "PERSONAL_APPLICATION",
                        "userId": "platform:u1",
                        "applicationId": "app-1",
                    },
                    {
                        "scope": "TEAM_APPLICATION",
                        "agentId": "team:app-1",
                        "applicationId": "app-1",
                    },
                ],
            },
        )
        assert searched.status_code == 200
        assert searched.json()["results"][0]["score"] == 0.032

        updated = await client.put(
            f"/memories/{memory_id}",
            headers=headers,
            json={**context, "operationId": "operation-0003", "text": "用户偏好简洁中文回答"},
        )
        assert "简洁" in updated.json()["content"]

        history = await client.get(f"/memories/{memory_id}/history", headers=headers)
        assert history.json()["results"][0]["event"] == "ADD"

        deleted = await client.delete(
            f"/memories/{memory_id}",
            headers={
                **headers,
                "X-Memory-Requester-User-Id": "u1",
                "X-Memory-Run-Id": "run-0001",
                "X-Memory-Session-Id": "session-0001",
                "X-Memory-Operation-Id": "operation-0004",
            },
        )
        assert deleted.status_code == 204
        gone = await client.post("/memory-api/v1/extract")
        assert gone.status_code == 410
        assert gone.json()["error"]["code"] == "API_GONE"


@pytest.mark.asyncio
async def test_metadata_rejects_raw_conversation(configured: tuple[MemoryServiceSettings, FakeStore]) -> None:
    settings, store = configured
    app = create_app(AppDependencies(settings, store))  # type: ignore[arg-type]
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://memory") as client:
        response = await client.post(
            "/memories",
            headers={"X-Memory-Service-Key": settings.api_key.get_secret_value()},
            json={
                "messages": "只保存派生记忆",
                "infer": False,
                "userId": "platform:u1",
                "requesterUserId": "u1",
                "runId": "manual-0001",
                "sessionId": "manual-0001",
                "operationId": "manual-operation-0001",
                "scope": "PERSONAL_GLOBAL",
                "metadata": {"nested": {"RAW_Messages": ["不得复制"]}},
            },
        )
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "RAW_CONVERSATION_FORBIDDEN"


@pytest.mark.asyncio
async def test_operation_id_is_rejected_before_exceeding_database_contract(
    configured: tuple[MemoryServiceSettings, FakeStore],
) -> None:
    settings, store = configured
    app = create_app(AppDependencies(settings, store))  # type: ignore[arg-type]
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://memory") as client:
        response = await client.post(
            "/memories",
            headers={"X-Memory-Service-Key": settings.api_key.get_secret_value()},
            json={
                "messages": "只保存派生记忆",
                "infer": False,
                "userId": "platform:u1",
                "requesterUserId": "u1",
                "runId": "manual-0001",
                "sessionId": "manual-0001",
                "operationId": "o" * 129,
                "scope": "PERSONAL_GLOBAL",
            },
        )
    assert response.status_code == 422
    assert store.documents == {}
