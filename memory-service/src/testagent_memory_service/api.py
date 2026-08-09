"""通用 Mem0 REST 服务；节点无状态，所有副本共享 PostgreSQL/pgvector。"""

from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from dataclasses import dataclass
from hmac import compare_digest
import json
from typing import Any
from uuid import uuid4

from fastapi import Depends, FastAPI, Header, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, Response

from testagent_memory_service import __version__
from testagent_memory_service.mem0_adapter import (
    IdempotencyConflict,
    Mem0MemoryStore,
    OwnerScope,
)
from testagent_memory_service.projection_worker import ProjectionWorker
from testagent_memory_service.schemas import (
    AddMemoriesRequest,
    MutationContext,
    SearchMemoriesRequest,
    UpdateMemoryRequest,
)
from testagent_memory_service.settings import MemoryServiceSettings


FORBIDDEN_METADATA_KEYS = {
    "messages",
    "rawmessages",
    "transcript",
    "rawconversation",
    "conversation",
    "prompt",
    "answer",
    "assistantmessage",
    "usermessage",
}


@dataclass(frozen=True, slots=True)
class AppDependencies:
    settings: MemoryServiceSettings
    store: Mem0MemoryStore
    projection_worker: ProjectionWorker | None = None
    close_callbacks: tuple[Any, ...] = ()


class ServiceError(RuntimeError):
    def __init__(self, status: int, code: str, message: str):
        super().__init__(message)
        self.status = status
        self.code = code


def create_app(dependencies: AppDependencies) -> FastAPI:
    @asynccontextmanager
    async def lifespan(_: FastAPI) -> AsyncIterator[None]:
        if dependencies.projection_worker is not None:
            dependencies.projection_worker.start()
        try:
            yield
        finally:
            if dependencies.projection_worker is not None:
                await dependencies.projection_worker.close()
            for callback in reversed(dependencies.close_callbacks):
                try:
                    result = callback()
                    if asyncio.iscoroutine(result):
                        await result
                except Exception:
                    continue

    app = FastAPI(title="Test Agent Memory Service", version=__version__, lifespan=lifespan)

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

    @app.get("/health")
    async def health() -> dict[str, Any]:
        return {"status": "UP", "version": __version__}

    @app.get("/ready")
    async def ready(_: None = Depends(authenticate)) -> dict[str, Any]:
        profile_status = await asyncio.to_thread(dependencies.store.readiness)
        available = any(profile_status.values())
        if not available:
            raise ServiceError(503, "EMBEDDING_PROFILES_UNAVAILABLE", "所有 embedding profile 不可用")
        backlog = await asyncio.to_thread(dependencies.store.control.projection_backlog)
        return {
            "status": "UP",
            "version": __version__,
            "rawMessageCount": dependencies.store.raw_message_count(),
            "profiles": dependencies.settings.embedding_profiles(),
            "profileAvailability": profile_status,
            "projectionBacklog": backlog,
        }

    @app.post("/memories", status_code=201)
    async def add_memories(
        command: AddMemoriesRequest,
        request: Request,
        _: None = Depends(authenticate),
    ) -> dict[str, Any]:
        messages = _messages(command, dependencies.settings.max_learning_chars)
        metadata = _metadata(command.metadata)
        owner = OwnerScope(
            command.scope,
            command.user_id,
            command.agent_id,
            command.application_id,
        )
        try:
            rows = await asyncio.to_thread(
                dependencies.store.add,
                messages=messages,
                infer=command.infer,
                requester_user_id=command.requester_user_id,
                run_id=command.run_id,
                session_id=command.session_id,
                operation_id=command.operation_id,
                trace_id=request.state.trace_id,
                owner=owner,
                metadata=metadata,
                chat_model_id=command.chat_model_id,
            )
        except PermissionError as exception:
            raise ServiceError(403, "MODEL_GATEWAY_FORBIDDEN", "模型网关拒绝记忆服务") from exception
        except IdempotencyConflict as exception:
            raise ServiceError(409, "IDEMPOTENCY_CONFLICT", "幂等键已绑定其他操作") from exception
        except Exception as exception:
            raise ServiceError(503, "MEM0_UNAVAILABLE", "记忆学习失败") from exception
        return {"results": rows}

    @app.get("/memories/{memory_id}")
    async def get_memory(memory_id: str, _: None = Depends(authenticate)) -> dict[str, Any]:
        stored = await asyncio.to_thread(dependencies.store.get, memory_id)
        if stored is None:
            raise ServiceError(404, "MEMORY_NOT_FOUND", "记忆不存在")
        return stored

    @app.put("/memories/{memory_id}")
    async def update_memory(
        memory_id: str,
        command: UpdateMemoryRequest,
        request: Request,
        _: None = Depends(authenticate),
    ) -> dict[str, Any]:
        content = _bounded_content(command.text, dependencies.settings.max_document_chars)
        try:
            return await asyncio.to_thread(
                dependencies.store.update,
                memory_id,
                content,
                _metadata(command.metadata),
                scope=command.scope,
                application_id=command.application_id,
                requester_user_id=command.requester_user_id,
                run_id=command.run_id,
                session_id=command.session_id,
                operation_id=command.operation_id,
                trace_id=request.state.trace_id,
            )
        except KeyError as exception:
            raise ServiceError(404, "MEMORY_NOT_FOUND", "记忆不存在") from exception
        except IdempotencyConflict as exception:
            raise ServiceError(409, "IDEMPOTENCY_CONFLICT", "幂等键已绑定其他操作") from exception
        except Exception as exception:
            raise ServiceError(503, "MEM0_UNAVAILABLE", "记忆更新失败") from exception

    @app.delete("/memories/{memory_id}", status_code=204, response_class=Response)
    async def delete_memory(
        memory_id: str,
        request: Request,
        requester_user_id: str = Header(alias="X-Memory-Requester-User-Id"),
        run_id: str = Header(alias="X-Memory-Run-Id"),
        session_id: str = Header(alias="X-Memory-Session-Id"),
        operation_id: str = Header(alias="X-Memory-Operation-Id"),
        _: None = Depends(authenticate),
    ) -> Response:
        try:
            context = MutationContext(
                requesterUserId=requester_user_id,
                runId=run_id,
                sessionId=session_id,
                operationId=operation_id,
            )
            await asyncio.to_thread(
                dependencies.store.delete,
                memory_id,
                requester_user_id=context.requester_user_id,
                run_id=context.run_id,
                session_id=context.session_id,
                operation_id=context.operation_id,
                trace_id=request.state.trace_id,
            )
        except KeyError as exception:
            raise ServiceError(404, "MEMORY_NOT_FOUND", "记忆不存在") from exception
        except IdempotencyConflict as exception:
            raise ServiceError(409, "IDEMPOTENCY_CONFLICT", "幂等键已绑定其他操作") from exception
        except Exception as exception:
            raise ServiceError(503, "MEM0_UNAVAILABLE", "记忆删除失败") from exception
        return Response(status_code=204)

    @app.get("/memories/{memory_id}/history")
    async def history(memory_id: str, _: None = Depends(authenticate)) -> dict[str, Any]:
        try:
            return {"results": await asyncio.to_thread(dependencies.store.history, memory_id)}
        except KeyError as exception:
            raise ServiceError(404, "MEMORY_NOT_FOUND", "记忆不存在") from exception

    @app.post("/search")
    async def search(
        command: SearchMemoriesRequest,
        request: Request,
        _: None = Depends(authenticate),
    ) -> dict[str, Any]:
        try:
            rows = await asyncio.to_thread(
                dependencies.store.search,
                query=command.query,
                scopes=[
                    OwnerScope(
                        scope.scope,
                        scope.user_id,
                        scope.agent_id,
                        scope.application_id,
                    )
                    for scope in command.scopes
                ],
                top_k=min(command.top_k, dependencies.settings.max_search_top_k),
                threshold=command.threshold,
                requester_user_id=command.requester_user_id,
                run_id=command.run_id,
                session_id=command.session_id,
                operation_id=command.operation_id,
                trace_id=request.state.trace_id,
            )
        except Exception as exception:
            raise ServiceError(503, "MEM0_UNAVAILABLE", "记忆检索失败") from exception
        return {"results": rows}

    @app.api_route("/memory-api/v1/{legacy_path:path}", methods=["GET", "POST", "PUT", "PATCH", "DELETE"])
    async def legacy_gone(legacy_path: str, request: Request) -> JSONResponse:
        del legacy_path
        return _error(request, 410, "API_GONE", "旧记忆服务接口已迁移")

    return app


def production_dependencies(settings: MemoryServiceSettings) -> AppDependencies:
    store = Mem0MemoryStore(settings)
    worker = ProjectionWorker(store, settings)
    return AppDependencies(settings, store, worker, (store.close,))


def _messages(
    command: AddMemoriesRequest, maximum: int
) -> str | list[dict[str, str]]:
    if isinstance(command.messages, str):
        value = _bounded_content(command.messages, maximum)
        return value
    total = sum(len(message.content) for message in command.messages)
    if total > maximum:
        raise ServiceError(422, "VALIDATION_ERROR", "记忆学习输入超过安全上限")
    return [
        {
            "role": message.role,
            "content": _bounded_content(message.content, 100_000),
        }
        for message in command.messages
    ]


def _bounded_content(value: str, maximum: int) -> str:
    if not value.strip() or len(value) > maximum or "\x00" in value:
        raise ServiceError(422, "VALIDATION_ERROR", "记忆正文为空或超过安全上限")
    # Mem0 原生抽取结果和人工记忆均保持原文；这里只校验结构边界，不改写空白或语义。
    return value


def _metadata(value: dict[str, Any]) -> dict[str, Any]:
    if _contains_forbidden_key(value):
        raise ServiceError(422, "RAW_CONVERSATION_FORBIDDEN", "metadata 不得包含原始对话")
    try:
        encoded = json.dumps(value, ensure_ascii=False, separators=(",", ":"))
    except (TypeError, ValueError) as exception:
        raise ServiceError(422, "VALIDATION_ERROR", "metadata 必须是 JSON 对象") from exception
    if len(encoded.encode("utf-8")) > 16 * 1024:
        raise ServiceError(422, "VALIDATION_ERROR", "metadata 超过 16 KiB 安全上限")
    return dict(value)


def _contains_forbidden_key(value: Any) -> bool:
    if isinstance(value, dict):
        return any(
            _normalized_metadata_key(key) in FORBIDDEN_METADATA_KEYS
            or _contains_forbidden_key(item)
            for key, item in value.items()
        )
    if isinstance(value, list):
        return any(_contains_forbidden_key(item) for item in value)
    return False


def _normalized_metadata_key(value: Any) -> str:
    """大小写和常见分隔符不能绕过原始对话字段门禁。"""
    return "".join(character for character in str(value).lower() if character.isalnum())


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
        "error": {"code": code, "message": message, "traceId": request.state.trace_id}
    }
    if details:
        payload["error"]["details"] = details
    return JSONResponse(status_code=status, content=payload)
