"""Mem0 自定义 Provider：CHAT 与 embedding 统一经 Java HMAC 模型网关。"""

from __future__ import annotations

from threading import RLock
from typing import Any, Literal

from mem0.configs.embeddings.base import BaseEmbedderConfig
from mem0.embeddings.base import EmbeddingBase
from mem0.llms.base import LLMBase

from testagent_memory_service.gateway import HmacModelGatewayClient, current_gateway_context


class ProviderRuntime:
    """Mem0 factory 只能按类路径构造 Provider，因此显式安装进程级无状态客户端。"""

    _lock = RLock()
    _gateway: HmacModelGatewayClient | None = None

    @classmethod
    def install(cls, gateway: HmacModelGatewayClient) -> None:
        with cls._lock:
            cls._gateway = gateway

    @classmethod
    def gateway(cls) -> HmacModelGatewayClient:
        with cls._lock:
            if cls._gateway is None:
                raise RuntimeError("模型网关 Provider 尚未安装")
            return cls._gateway


class GatewayEmbedding(EmbeddingBase):
    """OpenAI-compatible embedding；只有 search 使用 query 类型。"""

    def __init__(self, config: BaseEmbedderConfig | None = None):
        super().__init__(config)
        if not self.config.model or not self.config.embedding_dims:
            raise ValueError("embedding Provider 缺少固定 model 或 dimension")

    def embed(
        self,
        text: str,
        memory_action: Literal["add", "search", "update"] | None = None,
    ) -> list[float]:
        return self.embed_batch([text], memory_action or "add")[0]

    def embed_batch(
        self,
        texts: list[str],
        memory_action: Literal["add", "search", "update"] = "add",
    ) -> list[list[float]]:
        if not texts:
            return []
        input_type = "query" if memory_action == "search" else "document"
        configured_timeout = float(
            (self.config.model_kwargs or {}).get("timeout_seconds", 15.0)
        )
        context = current_gateway_context()
        timeout_seconds = (
            min(configured_timeout, context.embedding_timeout_seconds)
            if context is not None and context.embedding_timeout_seconds is not None
            else configured_timeout
        )
        response = ProviderRuntime.gateway().post_json(
            "/embeddings",
            {"model": self.config.model, "input": texts},
            capability="EMBEDDING",
            timeout_seconds=timeout_seconds,
            input_type=input_type,
        )
        try:
            ordered = sorted(response["data"], key=lambda item: int(item["index"]))
            vectors = [[float(value) for value in item["embedding"]] for item in ordered]
        except (KeyError, TypeError, ValueError) as exception:
            raise RuntimeError("模型网关 embedding 响应格式无效") from exception
        expected = int(self.config.embedding_dims)
        if len(vectors) != len(texts) or any(len(vector) != expected for vector in vectors):
            raise RuntimeError("模型网关 embedding 数量或维度不匹配")
        return vectors


class GatewayMemoryLlm(LLMBase):
    """直接转发 Mem0 2.0.17 原生抽取提示；本项目不拼接自定义抽取提示词。"""

    def generate_response(
        self,
        messages: list[dict[str, str]],
        tools: list[dict[str, Any]] | None = None,
        tool_choice: str = "auto",
        **kwargs: Any,
    ) -> str:
        body: dict[str, Any] = {
            "model": (current_gateway_context().chat_model_id
                      if current_gateway_context() and current_gateway_context().chat_model_id
                      else self.config.model),
            "messages": messages,
            "temperature": self.config.temperature,
            "max_tokens": self.config.max_tokens,
            "stream": False,
        }
        if tools:
            body["tools"] = tools
            body["tool_choice"] = tool_choice
        response_format = kwargs.get("response_format")
        if response_format is not None:
            body["response_format"] = response_format
        response = ProviderRuntime.gateway().post_json(
            "/chat/completions",
            body,
            capability="CHAT",
            timeout_seconds=float(
                kwargs.get(
                    "timeout_seconds",
                    ProviderRuntime.gateway().settings.chat_timeout_seconds,
                )
            ),
        )
        try:
            return str(response["choices"][0]["message"]["content"])
        except (KeyError, IndexError, TypeError) as exception:
            raise RuntimeError("模型网关 CHAT 响应格式无效") from exception
