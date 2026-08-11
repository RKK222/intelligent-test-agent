"""OpenAI-compatible `/v1/embeddings` 与有界 CPU 并发。"""

from __future__ import annotations

import asyncio
from dataclasses import dataclass
from hmac import compare_digest
from typing import Any
from uuid import uuid4

from fastapi import Depends, FastAPI, Header, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field, field_validator

from testagent_embedding_service import __version__
from testagent_embedding_service.model import BgeCpuModel
from testagent_embedding_service.settings import (
    MODEL_DIMENSION,
    MODEL_ID,
    MODEL_REVISION,
    EmbeddingServiceSettings,
)


class EmbeddingRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    model: str
    input: str | list[str]
    encoding_format: str | None = None

    @field_validator("encoding_format")
    @classmethod
    def float_only(cls, value: str | None) -> str | None:
        if value not in {None, "float"}:
            raise ValueError("只支持 float encoding_format")
        return value


@dataclass(frozen=True, slots=True)
class Dependencies:
    settings: EmbeddingServiceSettings
    model: BgeCpuModel


class ServiceError(RuntimeError):
    def __init__(self, status: int, code: str, message: str):
        super().__init__(message)
        self.status = status
        self.code = code


def create_app(dependencies: Dependencies) -> FastAPI:
    app = FastAPI(title="Test Agent CPU Embedding", version=__version__)
    capacity = asyncio.Semaphore(dependencies.settings.max_concurrency)

    @app.middleware("http")
    async def trace_middleware(request: Request, call_next: Any) -> Any:
        incoming = request.headers.get("X-Trace-Id", "")
        request.state.trace_id = incoming if incoming.startswith("trace_") else f"trace_{uuid4().hex}"
        response = await call_next(request)
        response.headers["X-Trace-Id"] = request.state.trace_id
        response.headers["Cache-Control"] = "no-store"
        return response

    @app.exception_handler(ServiceError)
    async def service_error(request: Request, exception: ServiceError) -> JSONResponse:
        return _error(request, exception.status, exception.code, str(exception))

    @app.exception_handler(RequestValidationError)
    async def validation_error(request: Request, exception: RequestValidationError) -> JSONResponse:
        del exception
        return _error(request, 422, "VALIDATION_ERROR", "embedding 请求参数不合法")

    def authenticate(authorization: str | None = Header(default=None)) -> None:
        expected = f"Bearer {dependencies.settings.api_key.get_secret_value()}"
        if authorization is None or not compare_digest(authorization, expected):
            raise ServiceError(401, "UNAUTHENTICATED", "embedding 服务认证失败")

    @app.get("/health")
    async def health() -> dict[str, Any]:
        return {"status": "UP", "version": __version__}

    @app.get("/ready")
    async def ready(_: None = Depends(authenticate)) -> dict[str, Any]:
        return {
            "status": "UP",
            "model": MODEL_ID,
            "revision": MODEL_REVISION,
            "dimension": MODEL_DIMENSION,
            "device": "CPU",
            "normalized": True,
        }

    @app.post("/v1/embeddings")
    async def embeddings(
        command: EmbeddingRequest,
        x_embedding_input_type: str = Header(alias="X-Embedding-Input-Type"),
        _: None = Depends(authenticate),
    ) -> dict[str, Any]:
        if command.model != MODEL_ID:
            raise ServiceError(422, "MODEL_MISMATCH", "只允许固定 BGE 模型")
        if x_embedding_input_type not in {"query", "document"}:
            raise ServiceError(422, "INPUT_TYPE_REQUIRED", "必须显式指定 query 或 document")
        values = [command.input] if isinstance(command.input, str) else command.input
        if not values or len(values) > dependencies.settings.max_batch_size:
            raise ServiceError(422, "BATCH_LIMIT", "embedding 批量数量超过上限")
        if any(
            not isinstance(value, str)
            or not value.strip()
            or len(value) > dependencies.settings.max_input_chars
            or "\x00" in value
            for value in values
        ) or sum(len(value) for value in values) > dependencies.settings.max_batch_chars:
            raise ServiceError(422, "INPUT_LIMIT", "embedding 输入为空或超过字符上限")
        try:
            await asyncio.wait_for(
                capacity.acquire(), timeout=dependencies.settings.queue_timeout_seconds
            )
        except TimeoutError as exception:
            raise ServiceError(429, "CAPACITY_EXCEEDED", "CPU embedding 并发容量已满") from exception
        try:
            vectors = await asyncio.to_thread(
                dependencies.model.embed, values, x_embedding_input_type
            )
        finally:
            capacity.release()
        return {
            "object": "list",
            "model": MODEL_ID,
            "data": [
                {"object": "embedding", "index": index, "embedding": vector}
                for index, vector in enumerate(vectors)
            ],
            "usage": {
                "prompt_tokens": sum(max(1, (len(value) + 3) // 4) for value in values),
                "total_tokens": sum(max(1, (len(value) + 3) // 4) for value in values),
            },
        }

    return app


def production_app() -> FastAPI:
    settings = EmbeddingServiceSettings()
    return create_app(Dependencies(settings, BgeCpuModel(settings)))


def _error(request: Request, status: int, code: str, message: str) -> JSONResponse:
    return JSONResponse(
        status_code=status,
        content={
            "error": {
                "code": code,
                "message": message,
                "traceId": request.state.trace_id,
            }
        },
    )
