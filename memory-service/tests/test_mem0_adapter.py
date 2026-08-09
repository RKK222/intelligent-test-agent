from pathlib import Path
from typing import Any

from testagent_memory_service.mem0_adapter import Mem0MemoryStore
from testagent_memory_service.settings import MemoryServiceSettings


class FakeEngine:
    def __init__(self) -> None:
        self.documents: dict[str, dict[str, Any]] = {}
        self.add_kwargs: dict[str, Any] = {}

    def add(self, messages: Any, **kwargs: Any) -> dict[str, Any]:
        self.add_kwargs = {"messages": messages, **kwargs}
        self.documents["memory-1"] = {
            "id": "memory-1",
            "memory": messages,
            "metadata": kwargs["metadata"],
            "user_id": kwargs.get("user_id"),
            "updated_at": "2026-08-09T00:00:00Z",
        }
        return {"results": [{"id": "memory-1"}]}

    def get(self, memory_id: str) -> dict[str, Any] | None:
        return self.documents.get(memory_id)

    def update(self, memory_id: str, data: str, metadata: dict[str, Any]) -> dict[str, Any]:
        self.documents[memory_id]["memory"] = data
        self.documents[memory_id]["metadata"].update(metadata)
        return {"message": "updated"}

    def delete(self, memory_id: str) -> dict[str, Any]:
        del self.documents[memory_id]
        return {"message": "deleted"}

    def history(self, memory_id: str) -> list[dict[str, Any]]:
        return [{"memory_id": memory_id, "event": "ADD"}]

    def search(self, query: str, **kwargs: Any) -> dict[str, Any]:
        del query, kwargs
        item = dict(self.documents["memory-1"])
        item["score"] = 0.92
        return {"results": [item]}


def test_store_always_uses_non_infer_path(tmp_path: Path) -> None:
    settings = MemoryServiceSettings(
        _env_file=None,
        api_key="k" * 32,
        history_db_path=tmp_path / "history.db",
        model_root=tmp_path / "models",
        model_path=tmp_path / "models" / "bge",
    )
    engine = FakeEngine()
    store = Mem0MemoryStore(settings, engine=engine)

    created = store.add(
        content="测试案例必须覆盖边界条件",
        user_id="platform:u1",
        agent_id=None,
        metadata={"scope": "PERSONAL_GLOBAL"},
    )

    assert engine.add_kwargs["infer"] is False
    assert engine.add_kwargs["messages"] == "测试案例必须覆盖边界条件"
    assert created["content"] == "测试案例必须覆盖边界条件"
    assert store.search(
        query="设计用例", filters={"user_id": "platform:u1"}, top_k=5, threshold=0.1
    )[0]["score"] == 0.92
