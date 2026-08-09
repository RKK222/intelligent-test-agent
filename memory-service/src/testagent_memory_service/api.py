"""平台内部专用的 Mem0 窄 HTTP API。"""

from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from dataclasses import dataclass
from hmac import compare_digest
from typing import Any
from uuid import uuid4

from fastapi import Depends, FastAPI, Header, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, Response
from pydantic import ValidationError

from testagent_memory_service import __version__
from testagent_memory_service.extraction import (
    ExtractionUnavailable,
    Extractor,
    ModelGatewayExtractor,
)
from testagent_memory_service.mem0_adapter import Mem0MemoryStore
from testagent_memory_service.schemas import (
    AddDocumentRequest,
    ExtractRequest,
    ExtractedCandidate,
    SearchRequest,
    UpdateDocumentRequest,
)
from testagent_memory_service.settings import MemoryServiceSettings


API_PREFIX = "/memory-api/v1"
ALLOWED_METADATA = {"scope", "source", "taskTypes", "applicationId", "candidateKey"}


@dataclass(frozen=True, slots=True)
class AppDependencies:
    settings: MemoryServiceSettings
    store: Mem0MemoryStore
    extractor: Extractor
    close_callbacks: tuple[Any, ...] = ()


class ServiceError(RuntimeError):
    def __init__(self, status: int, code: str, message: str):
        super().__init__(message)
        self.status = status
        self.code = code


def create_app(dependencies: AppDependencies) -> FastAPI:
    @asynccontextmanager
    async def lifespan(_: FastAPI) -> AsyncIterator[None]:
        try:
            yield
        finally:
            for callback in reversed(dependencies.close_callbacks):
                try:
                    result = callback()
                    if asyncio.iscoroutine(result):
                        await result
                except Exception:
                    continue

    app = FastAPI(title="Test Agent Memory Service", version=__version__, lifespan=lifespan)
    app.state.memory_store = dependencies.store

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
        details = {"errors": [_safe_validation_error(error) for error in exception.errors()]}
        return _error(request, 422, "VALIDATION_ERROR", "请求参数不合法", details)

    @app.exception_handler(Exception)
    async def unexpected_error(request: Request, exception: Exception) -> JSONResponse:
        del exception
        return _error(request, 500, "INTERNAL_ERROR", "记忆服务内部错误")

    def authenticate(
        x_memory_service_key: str | None = Header(default=None, alias="X-Memory-Service-Key"),
    ) -> None:
        expected = dependencies.settings.api_key.get_secret_value()
        if x_memory_service_key is None or not compare_digest(x_memory_service_key, expected):
            raise ServiceError(401, "UNAUTHENTICATED", "记忆服务认证失败")

    @app.get(f"{API_PREFIX}/health")
    async def health() -> dict[str, Any]:
        return {"data": {"status": "UP", "version": __version__}}

    @app.get(f"{API_PREFIX}/ready")
    async def ready(_: None = Depends(authenticate)) -> dict[str, Any]:
        count = await asyncio.to_thread(dependencies.store.raw_message_count)
        if count != 0:
            raise ServiceError(503, "RAW_MESSAGE_PERSISTED", "Mem0 原始消息合同被破坏")
        return {
            "data": {
                "status": "UP",
                "version": __version__,
                "rawMessageCount": count,
                "embedding": dependencies.settings.embedding_profile(),
            }
        }

    @app.get(f"{API_PREFIX}/embedding-profile")
    async def profile(_: None = Depends(authenticate)) -> dict[str, Any]:
        return {"data": dependencies.settings.embedding_profile()}

    @app.post(f"{API_PREFIX}/documents", status_code=201)
    async def add_document(
        command: AddDocumentRequest, _: None = Depends(authenticate)
    ) -> dict[str, Any]:
        content = _bounded_content(command.content, dependencies.settings.max_document_chars)
        metadata = _metadata(command.metadata)
        metadata["applicationId"] = command.application_id
        metadata["taskTypes"] = command.task_types
        try:
            stored = await asyncio.to_thread(
                dependencies.store.add,
                content=content,
                user_id=command.user_id,
                agent_id=command.agent_id,
                metadata={key: value for key, value in metadata.items() if value is not None},
            )
        except Exception as exception:
            raise ServiceError(503, "MEM0_UNAVAILABLE", "派生记忆写入失败") from exception
        return {"data": stored}

    @app.get(f"{API_PREFIX}/documents/{{memory_id}}")
    async def get_document(memory_id: str, _: None = Depends(authenticate)) -> dict[str, Any]:
        try:
            stored = await asyncio.to_thread(dependencies.store.get, memory_id)
        except Exception as exception:
            raise ServiceError(503, "MEM0_UNAVAILABLE", "派生记忆读取失败") from exception
        if stored is None:
            raise ServiceError(404, "MEMORY_NOT_FOUND", "派生记忆不存在")
        return {"data": stored}

    @app.patch(f"{API_PREFIX}/documents/{{memory_id}}")
    async def update_document(
        memory_id: str,
        command: UpdateDocumentRequest,
        _: None = Depends(authenticate),
    ) -> dict[str, Any]:
        content = _bounded_content(command.content, dependencies.settings.max_document_chars)
        try:
            stored = await asyncio.to_thread(
                dependencies.store.update, memory_id, content, _metadata(command.metadata)
            )
        except KeyError as exception:
            raise ServiceError(404, "MEMORY_NOT_FOUND", "派生记忆不存在") from exception
        except Exception as exception:
            raise ServiceError(503, "MEM0_UNAVAILABLE", "派生记忆更新失败") from exception
        return {"data": stored}

    @app.delete(
        f"{API_PREFIX}/documents/{{memory_id}}",
        status_code=204,
        response_class=Response,
    )
    async def delete_document(memory_id: str, _: None = Depends(authenticate)) -> Response:
        try:
            await asyncio.to_thread(dependencies.store.delete, memory_id)
        except ValueError as exception:
            raise ServiceError(404, "MEMORY_NOT_FOUND", "派生记忆不存在") from exception
        except Exception as exception:
            raise ServiceError(503, "MEM0_UNAVAILABLE", "派生记忆删除失败") from exception
        return Response(status_code=204)

    @app.get(f"{API_PREFIX}/documents/{{memory_id}}/history")
    async def history(memory_id: str, _: None = Depends(authenticate)) -> dict[str, Any]:
        try:
            rows = await asyncio.to_thread(dependencies.store.history, memory_id)
        except Exception as exception:
            raise ServiceError(503, "MEM0_UNAVAILABLE", "派生记忆历史读取失败") from exception
        return {"data": rows}

    @app.post(f"{API_PREFIX}/search")
    async def search(command: SearchRequest, _: None = Depends(authenticate)) -> dict[str, Any]:
        filters = {
            key: value
            for key, value in {
                "user_id": command.user_id,
                "agent_id": command.agent_id,
                "applicationId": command.application_id,
                "scope": command.scope,
            }.items()
            if value is not None
        }
        try:
            rows = await asyncio.to_thread(
                dependencies.store.search,
                query=command.query,
                filters=filters,
                top_k=command.top_k,
                threshold=command.threshold,
            )
        except Exception as exception:
            raise ServiceError(503, "MEM0_UNAVAILABLE", "派生记忆检索失败") from exception
        return {"data": {"items": rows}}

    @app.post(f"{API_PREFIX}/extract")
    async def extract(command: ExtractRequest, request: Request, _: None = Depends(authenticate)) -> dict[str, Any]:
        total_chars = sum(len(message.content) for message in command.messages)
        if total_chars > dependencies.settings.max_extraction_chars:
            raise ServiceError(422, "VALIDATION_ERROR", "抽取输入超过安全上限")
        try:
            raw = await dependencies.extractor.extract(
                model=command.model,
                grant=command.model_grant.get_secret_value(),
                user_id=command.user_id,
                run_id=command.run_id,
                messages=[message.model_dump() for message in command.messages],
                task_type=command.task_type,
                application_id=command.application_id,
                trace_id=request.state.trace_id,
            )
            candidates = [ExtractedCandidate.model_validate(item) for item in raw]
        except PermissionError as exception:
            raise ServiceError(403, "MODEL_GRANT_REJECTED", str(exception)) from exception
        except (ExtractionUnavailable, ValidationError) as exception:
            raise ServiceError(503, "EXTRACTION_UNAVAILABLE", "记忆候选抽取失败") from exception
        return {
            "data": {
                "candidates": [candidate.model_dump(by_alias=True) for candidate in candidates],
            }
        }

    return app


def production_dependencies(settings: MemoryServiceSettings) -> AppDependencies:
    store = Mem0MemoryStore(settings)
    extractor = ModelGatewayExtractor(settings)
    return AppDependencies(settings, store, extractor, (extractor.close,))


def _bounded_content(value: str, maximum: int) -> str:
    normalized = " ".join(value.split()).strip()
    if not normalized or len(normalized) > maximum or "\x00" in value:
        raise ServiceError(422, "VALIDATION_ERROR", "记忆正文为空或超过安全上限")
    return normalized


def _metadata(value: dict[str, Any]) -> dict[str, Any]:
    if any(key not in ALLOWED_METADATA for key in value):
        raise ServiceError(422, "VALIDATION_ERROR", "记忆 metadata 包含未登记字段")
    if len(str(value)) > 4_000:
        raise ServiceError(422, "VALIDATION_ERROR", "记忆 metadata 超过安全上限")
    return dict(value)


def _safe_validation_error(error: dict[str, Any]) -> dict[str, Any]:
    return {
        "type": str(error.get("type", "validation_error")),
        "location": [str(item) for item in error.get("loc", ())],
    }


def _error(
    request: Request,
    status: int,
    code: str,
    message: str,
    details: dict[str, Any] | None = None,
) -> JSONResponse:
    payload: dict[str, Any] = {
        "error": {
            "code": code,
            "message": message,
            "traceId": request.state.trace_id,
        }
    }
    if details:
        payload["error"]["details"] = details
    return JSONResponse(status_code=status, content=payload)
