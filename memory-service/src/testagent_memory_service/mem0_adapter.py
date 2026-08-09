"""Mem0 2.0.3 固定版本适配器；仅向平台暴露派生记忆对象。"""

from __future__ import annotations

import os

# 必须在首次导入 Mem0/Transformers 前关闭遥测和所有模型网络回退。
os.environ["MEM0_TELEMETRY"] = "False"
os.environ["MEM0_TELEMETRY_SAMPLE_RATE"] = "0"
os.environ["HF_HUB_OFFLINE"] = "1"
os.environ["TRANSFORMERS_OFFLINE"] = "1"
os.environ["HF_DATASETS_OFFLINE"] = "1"
os.environ["DO_NOT_TRACK"] = "1"
os.environ.setdefault("MEM0_DIR", "/tmp/test-agent-memory-mem0")

from datetime import datetime, timezone
from threading import RLock
from typing import Any, Protocol

from mem0 import Memory
from mem0.configs.llms.openai import OpenAIConfig
from mem0.llms.base import LLMBase
from mem0.utils.factory import EmbedderFactory, LlmFactory

from testagent_memory_service.embedding import EmbeddingProviderRegistry
from testagent_memory_service.history import NoRawMessageHistoryManager
from testagent_memory_service.settings import MemoryServiceSettings


class DisabledMem0Llm(LLMBase):
    """Mem0 内建 infer 被关闭；抽取只能走平台受控的短期模型授权。"""

    def generate_response(self, messages: list[dict[str, str]], **kwargs: Any) -> str:
        del messages, kwargs
        raise RuntimeError("Mem0 内建 LLM 推理已禁用")


class MemoryEngine(Protocol):
    def add(self, messages: Any, **kwargs: Any) -> dict[str, Any]: ...
    def get(self, memory_id: str) -> dict[str, Any] | None: ...
    def update(
        self, memory_id: str, data: str, metadata: dict[str, Any] | None = None
    ) -> dict[str, Any]: ...
    def delete(self, memory_id: str) -> dict[str, Any]: ...
    def history(self, memory_id: str) -> list[dict[str, Any]]: ...
    def search(self, query: str, **kwargs: Any) -> dict[str, Any]: ...


class Mem0MemoryStore:
    """线程安全的窄适配，绝不把对话消息传给 Mem0 的 infer 路径。"""

    def __init__(
        self,
        settings: MemoryServiceSettings,
        engine: MemoryEngine | None = None,
        history: NoRawMessageHistoryManager | None = None,
    ):
        self.settings = settings
        self._lock = RLock()
        if engine is None:
            engine, history = self._build_engine(settings)
        self.engine = engine
        self.history_manager = history

    @staticmethod
    def _build_engine(
        settings: MemoryServiceSettings,
    ) -> tuple[MemoryEngine, NoRawMessageHistoryManager]:
        # 使用 Mem0 已登记的 provider 名称，但将实现固定替换为平台 Provider。
        EmbeddingProviderRegistry.provider(settings.embedding_provider)
        EmbedderFactory.provider_to_class[
            "huggingface"
        ] = "testagent_memory_service.embedding.LocalBgeEmbedding"
        LlmFactory.provider_to_class["openai"] = (
            "testagent_memory_service.mem0_adapter.DisabledMem0Llm",
            OpenAIConfig,
        )
        config = {
            "version": "v1.1",
            "history_db_path": str(settings.history_db_path),
            "vector_store": {
                "provider": "pgvector",
                "config": {
                    "connection_string": settings.database_url.get_secret_value(),
                    "collection_name": settings.collection_name(),
                    "embedding_model_dims": settings.embedding_dimension,
                    "hnsw": True,
                    "diskann": False,
                    "minconn": 1,
                    "maxconn": 5,
                },
            },
            "embedder": {
                "provider": "huggingface",
                "config": {
                    "model": str(settings.model_path),
                    "embedding_dims": settings.embedding_dimension,
                    "model_kwargs": {
                        "model_root": str(settings.model_root),
                        "expected_model_id": settings.embedding_model_id,
                        "expected_revision": settings.embedding_revision,
                    },
                },
            },
            "llm": {
                "provider": "openai",
                "config": {"model": "disabled", "api_key": "disabled"},
            },
        }
        settings.history_db_path.parent.mkdir(parents=True, exist_ok=True)
        memory = Memory.from_config(config)
        memory.db.close()
        governed_history = NoRawMessageHistoryManager(str(settings.history_db_path))
        memory.db = governed_history
        return memory, governed_history

    def add(
        self,
        *,
        content: str,
        user_id: str | None,
        agent_id: str | None,
        metadata: dict[str, Any],
    ) -> dict[str, Any]:
        with self._lock:
            result = self.engine.add(
                content,
                user_id=user_id,
                agent_id=agent_id,
                metadata=metadata,
                infer=False,
            )
            items = result.get("results", [])
            if len(items) != 1 or not items[0].get("id"):
                raise RuntimeError("Mem0 未返回唯一派生记忆")
            stored = self.engine.get(str(items[0]["id"]))
            if stored is None:
                raise RuntimeError("Mem0 派生记忆写入后不可读")
            return self._document(stored)

    def get(self, memory_id: str) -> dict[str, Any] | None:
        with self._lock:
            stored = self.engine.get(memory_id)
        return self._document(stored) if stored is not None else None

    def update(
        self, memory_id: str, content: str, metadata: dict[str, Any]
    ) -> dict[str, Any]:
        with self._lock:
            self.engine.update(memory_id, content, metadata)
            stored = self.engine.get(memory_id)
        if stored is None:
            raise KeyError(memory_id)
        return self._document(stored)

    def delete(self, memory_id: str) -> None:
        with self._lock:
            self.engine.delete(memory_id)

    def history(self, memory_id: str) -> list[dict[str, Any]]:
        with self._lock:
            return list(self.engine.history(memory_id))

    def search(
        self,
        *,
        query: str,
        filters: dict[str, Any],
        top_k: int,
        threshold: float,
    ) -> list[dict[str, Any]]:
        with self._lock:
            response = self.engine.search(
                query,
                filters=filters,
                top_k=top_k,
                threshold=threshold,
                rerank=False,
            )
        return [self._document(item) for item in response.get("results", [])]

    def raw_message_count(self) -> int:
        if self.history_manager is None:
            return 0
        return self.history_manager.raw_message_count()

    @staticmethod
    def _document(item: dict[str, Any]) -> dict[str, Any]:
        metadata = dict(item.get("metadata") or {})
        for key in ("user_id", "agent_id", "run_id", "role"):
            if key in item:
                metadata[key] = item[key]
        updated = item.get("updated_at") or item.get("created_at")
        if not updated:
            updated = datetime.now(timezone.utc).isoformat()
        result: dict[str, Any] = {
            "id": str(item["id"]),
            "content": str(item.get("memory", "")),
            "metadata": metadata,
            "updatedAt": str(updated),
        }
        if item.get("score") is not None:
            result["score"] = float(item["score"])
        return result
