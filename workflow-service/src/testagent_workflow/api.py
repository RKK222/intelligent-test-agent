"""完全由Python承载的工作流HTTP与AG-UI入口。"""

from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator, Awaitable, Callable
from contextlib import asynccontextmanager
from dataclasses import dataclass
import json
import time
from typing import Any
from uuid import uuid4

from fastapi import Depends, FastAPI, Header, Query, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, Response
from pydantic import BaseModel, ConfigDict, Field
from sse_starlette import EventSourceResponse, ServerSentEvent

from testagent_workflow.agui import AgUiEvent, AgUiEventType, EventReplayService
from testagent_workflow.application import ConversationApplicationService, MessageSubmission
from testagent_workflow.auth import (
    AuthenticatedPrincipal,
    AuthenticationError,
    RedisTokenAuthenticator,
)
from testagent_workflow.registry import IntentClassifier, RegisteredIntentRouter, WorkflowRegistry
from testagent_workflow.runner_client import RunnerApiError
from testagent_workflow.platform import (
    PlatformCapabilityClient,
    PlatformCapabilityError,
    PlatformRequestIdentity,
)
from testagent_workflow.store import (
    ActiveRunConflict,
    RunNotCancelable,
    WorkflowStore,
    WorkspaceNotReusable,
)


API_PREFIX = "/workflow-api/v1"


@dataclass(frozen=True, slots=True)
class AppDependencies:
    authenticator: RedisTokenAuthenticator
    store: WorkflowStore
    registry: WorkflowRegistry
    classifier: IntentClassifier
    platform: PlatformCapabilityClient | None = None
    cancellation_service: Any | None = None
    question_answerer: Any | None = None
    followup_retention: Any | None = None
    sse_auth_recheck_seconds: float = 30.0
    sse_poll_seconds: float = 1.0
    readiness_probe: Callable[[], Awaitable[dict[str, Any]]] | None = None
    shutdown_callbacks: tuple[Callable[[], Awaitable[None]], ...] = ()


class CreateConversationRequest(BaseModel):
    title: str = Field(default="新对话", max_length=200)


class SubmitMessageRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    client_request_id: str = Field(alias="clientRequestId", min_length=8, max_length=128)
    text: str = Field(min_length=1, max_length=100_000)
    structured_input: dict[str, Any] | None = Field(default=None, alias="structuredInput")


def create_app(dependencies: AppDependencies) -> FastAPI:
    @asynccontextmanager
    async def lifespan(_: FastAPI) -> AsyncIterator[None]:
        try:
            yield
        finally:
            for callback in reversed(dependencies.shutdown_callbacks):
                try:
                    await callback()
                except Exception:
                    # 关闭阶段不回显连接串或下游异常，容器仍需可终止。
                    continue

    app = FastAPI(title="Test Agent Workflow Service", version="0.1.0", lifespan=lifespan)
    router = RegisteredIntentRouter(dependencies.registry, dependencies.classifier)
    conversations = ConversationApplicationService(
        dependencies.store,
        dependencies.registry,
        router,
        dependencies.question_answerer,
        dependencies.followup_retention,
    )
    replay_service = EventReplayService(dependencies.store)
    app.state.workflow_store = dependencies.store

    @app.middleware("http")
    async def trace_middleware(request: Request, call_next: Any) -> Any:
        incoming = request.headers.get("X-Trace-Id", "")
        request.state.trace_id = incoming if incoming.startswith("trace_") else f"trace_{uuid4().hex}"
        response = await call_next(request)
        response.headers["X-Trace-Id"] = request.state.trace_id
        return response

    @app.exception_handler(AuthenticationError)
    async def authentication_error(request: Request, exception: AuthenticationError) -> JSONResponse:
        return _error(request, 401, "UNAUTHENTICATED", str(exception))

    @app.exception_handler(PermissionError)
    async def permission_error(request: Request, exception: PermissionError) -> JSONResponse:
        return _error(request, 403, "FORBIDDEN", str(exception))

    @app.exception_handler(KeyError)
    async def not_found_error(request: Request, exception: KeyError) -> JSONResponse:
        return _error(request, 404, "NOT_FOUND", str(exception).strip("'"))

    @app.exception_handler(ActiveRunConflict)
    async def active_run_error(request: Request, exception: ActiveRunConflict) -> JSONResponse:
        return _error(request, 409, "RUN_ALREADY_ACTIVE", str(exception))

    @app.exception_handler(WorkspaceNotReusable)
    async def workspace_expired_error(
        request: Request, exception: WorkspaceNotReusable
    ) -> JSONResponse:
        return _error(
            request,
            409,
            "WORKSPACE_EXPIRED_NEW_TASK_REQUIRED",
            str(exception),
        )

    @app.exception_handler(RunNotCancelable)
    async def run_not_cancelable_error(
        request: Request, exception: RunNotCancelable
    ) -> JSONResponse:
        return _error(request, 409, "RUN_NOT_CANCELABLE", str(exception))

    @app.exception_handler(PlatformCapabilityError)
    async def platform_error(request: Request, exception: PlatformCapabilityError) -> JSONResponse:
        status = exception.status_code if 400 <= exception.status_code < 500 else 502
        return _error(request, status, exception.code, str(exception))

    @app.exception_handler(RunnerApiError)
    async def runner_error(request: Request, exception: RunnerApiError) -> JSONResponse:
        status = exception.status_code if 400 <= exception.status_code < 600 else 502
        return _error(request, status, exception.code, "分析工作区操作失败")

    @app.exception_handler(RequestValidationError)
    async def validation_error(request: Request, exception: RequestValidationError) -> JSONResponse:
        details = {"errors": [_safe_validation_error(error) for error in exception.errors()]}
        return _error(request, 422, "VALIDATION_ERROR", "请求参数不合法", details)

    @app.exception_handler(Exception)
    async def unexpected_error(request: Request, exception: Exception) -> JSONResponse:
        del exception
        return _error(request, 500, "INTERNAL_ERROR", "工作流服务内部错误")

    async def principal(
        authorization: str | None = Header(default=None),
    ) -> AuthenticatedPrincipal:
        return await dependencies.authenticator.authenticate(authorization)

    def identity(actor: AuthenticatedPrincipal) -> PlatformRequestIdentity:
        return PlatformRequestIdentity(actor.user_id, actor.session_digest)

    async def verified_super_admin(actor: AuthenticatedPrincipal) -> bool:
        if "SUPER_ADMIN" not in actor.roles or dependencies.platform is None:
            return False
        return await dependencies.platform.verify_super_admin(identity(actor))

    @app.get(f"{API_PREFIX}/health")
    async def health() -> dict[str, str]:
        return {"status": "UP"}

    @app.get(f"{API_PREFIX}/ready")
    async def ready(request: Request) -> Any:
        if dependencies.readiness_probe is None:
            return {"data": {"status": "UP"}}
        try:
            return {"data": await dependencies.readiness_probe()}
        except Exception:
            return _error(
                request,
                503,
                "WORKFLOW_NOT_READY",
                "工作流数据库或认证Redis尚未就绪",
            )

    @app.get(f"{API_PREFIX}/me")
    async def me(actor: AuthenticatedPrincipal = Depends(principal)) -> dict[str, Any]:
        return {
            "data": {
                "userId": actor.user_id,
                "username": actor.username,
                "unifiedAuthId": actor.unified_auth_id,
                "roles": list(actor.roles),
            }
        }

    @app.get(f"{API_PREFIX}/definitions")
    async def definitions(
        _: AuthenticatedPrincipal = Depends(principal),
    ) -> dict[str, Any]:
        return {
            "data": [
                {
                    "id": definition.id,
                    "version": definition.version,
                    "displayName": definition.display_name,
                    "inputSchema": definition.input_schema,
                    "uiSchema": definition.ui_schema,
                    "outputSchema": definition.output_schema,
                    "intentExamples": list(definition.intent_examples),
                    "requiredPermissions": list(definition.required_permissions),
                    "riskLevel": definition.risk_level,
                    "capabilities": list(definition.capabilities),
                    "requiresSandbox": definition.requires_sandbox,
                }
                for definition in dependencies.registry.definitions().values()
            ]
        }

    @app.post(f"{API_PREFIX}/conversations")
    async def create_conversation(
        payload: CreateConversationRequest,
        request: Request,
        actor: AuthenticatedPrincipal = Depends(principal),
    ) -> dict[str, Any]:
        conversation = await dependencies.store.create_conversation(actor.user_id, payload.title)
        await _audit(
            dependencies.store,
            request,
            actor.user_id,
            "CONVERSATION_CREATED",
            "conversation",
            conversation.id,
            {},
        )
        return {
            "data": {
                "id": conversation.id,
                "title": conversation.title,
                "ownerUserId": conversation.owner_user_id,
                "createdAt": conversation.created_at.isoformat(),
            }
        }

    @app.get(f"{API_PREFIX}/conversations")
    async def list_conversations(
        owner_user_id: str | None = Query(default=None, alias="ownerUserId"),
        actor: AuthenticatedPrincipal = Depends(principal),
    ) -> dict[str, Any]:
        cross_user = owner_user_id is not None and owner_user_id != actor.user_id
        super_admin = await verified_super_admin(actor) if cross_user else False
        values = await dependencies.store.list_conversations(
            actor.user_id,
            owner_user_id=owner_user_id,
            is_super_admin=super_admin,
        )
        return {"data": [_conversation_response(value) for value in values]}

    @app.get(f"{API_PREFIX}/conversations/{{conversation_id}}")
    async def get_conversation(
        conversation_id: str,
        actor: AuthenticatedPrincipal = Depends(principal),
    ) -> dict[str, Any]:
        try:
            conversation = await dependencies.store.get_conversation(conversation_id, actor.user_id)
            super_admin = False
        except PermissionError:
            super_admin = await verified_super_admin(actor)
            conversation = await dependencies.store.get_conversation(
                conversation_id,
                actor.user_id,
                is_super_admin=super_admin,
            )
        messages = await dependencies.store.list_messages(
            conversation_id,
            actor.user_id,
            is_super_admin=super_admin,
        )
        return {
            "data": {
                **_conversation_response(conversation),
                "messages": [_message_response(value) for value in messages],
            }
        }

    @app.get(f"{API_PREFIX}/repositories")
    async def repositories(actor: AuthenticatedPrincipal = Depends(principal)) -> dict[str, Any]:
        if dependencies.platform is None:
            raise PlatformCapabilityError("PLATFORM_UNAVAILABLE", "平台仓库能力未配置", 503)
        return {"data": await dependencies.platform.list_repositories(identity(actor))}

    @app.get(f"{API_PREFIX}/repositories/{{repository_id}}/branches")
    async def branches(
        repository_id: str,
        actor: AuthenticatedPrincipal = Depends(principal),
    ) -> dict[str, Any]:
        if dependencies.platform is None:
            raise PlatformCapabilityError("PLATFORM_UNAVAILABLE", "平台仓库能力未配置", 503)
        return {
            "data": await dependencies.platform.list_branches(identity(actor), repository_id)
        }

    @app.post(f"{API_PREFIX}/conversations/{{conversation_id}}/messages")
    async def submit_message(
        conversation_id: str,
        payload: SubmitMessageRequest,
        request: Request,
        actor: AuthenticatedPrincipal = Depends(principal),
    ) -> dict[str, Any]:
        result = await conversations.submit(
            conversation_id,
            actor.user_id,
            MessageSubmission(
                client_request_id=payload.client_request_id,
                text=payload.text,
                structured_input=payload.structured_input,
            ),
            session_digest=actor.session_digest,
        )
        await _audit(
            dependencies.store,
            request,
            actor.user_id,
            "MESSAGE_SUBMITTED",
            "conversation",
            conversation_id,
            {
                "structuredInput": payload.structured_input is not None,
                "runId": result.run_id,
                "taskId": result.task_id,
            },
        )
        return {
            "data": {
                "messageId": result.message_id,
                "assistantMessageId": result.assistant_message_id,
                "taskId": result.task_id,
                "runId": result.run_id,
                "status": result.status,
                "requiredInput": list(result.required_input),
            }
        }

    @app.get(f"{API_PREFIX}/conversations/{{conversation_id}}/events")
    async def conversation_events(
        request: Request,
        conversation_id: str,
        authorization: str | None = Header(default=None),
        last_event_id: str | None = Header(default=None, alias="Last-Event-ID"),
        actor: AuthenticatedPrincipal = Depends(principal),
    ) -> EventSourceResponse:
        after_seq = _event_cursor(last_event_id)
        try:
            await dependencies.store.get_conversation(conversation_id, actor.user_id)
            super_admin = False
        except PermissionError:
            super_admin = await verified_super_admin(actor)
            await dependencies.store.get_conversation(
                conversation_id,
                actor.user_id,
                is_super_admin=super_admin,
            )

        async def stream() -> AsyncIterator[ServerSentEvent]:
            cursor = after_seq
            for event in await replay_service.replay(
                conversation_id,
                actor.user_id,
                after_seq=cursor,
                is_super_admin=super_admin,
            ):
                if event.seq is not None:
                    cursor = max(cursor, event.seq)
                yield _server_event(event)
            last_authentication = time.monotonic()
            while not await request.is_disconnected():
                if time.monotonic() - last_authentication >= dependencies.sse_auth_recheck_seconds:
                    try:
                        refreshed = await dependencies.authenticator.authenticate(authorization)
                        if not _same_session_actor(actor, refreshed):
                            return
                    except AuthenticationError:
                        return
                    last_authentication = time.monotonic()
                values = await dependencies.store.list_events(conversation_id, after_seq=cursor)
                for event in values:
                    cursor = max(cursor, event.seq or cursor)
                    yield _server_event(event)
                await asyncio.sleep(dependencies.sse_poll_seconds)

        return EventSourceResponse(
            stream(),
            ping=15,
            headers={"Cache-Control": "no-cache, no-transform", "X-Accel-Buffering": "no"},
        )

    @app.post(f"{API_PREFIX}/runs/{{run_id}}/cancel")
    async def cancel_run(
        run_id: str,
        request: Request,
        actor: AuthenticatedPrincipal = Depends(principal),
    ) -> dict[str, Any]:
        run = await dependencies.store.request_cancel(run_id, actor.user_id)
        if dependencies.cancellation_service is not None:
            await dependencies.cancellation_service.cancel(run, identity(actor))
        await dependencies.store.append_event(
            run.conversation_id,
            AgUiEventType.RUN_FINISHED,
            {"runId": run.id, "taskId": run.task_id, "status": "CANCELED"},
            dedup_key=f"{run.id}:run:canceled",
        )
        await _audit(
            dependencies.store,
            request,
            actor.user_id,
            "RUN_CANCELED",
            "run",
            run.id,
            {"taskId": run.task_id},
        )
        return {"data": {"runId": run.id, "status": "CANCELED"}}

    @app.get(f"{API_PREFIX}/tasks/{{task_id}}/reports")
    async def task_reports(
        task_id: str,
        actor: AuthenticatedPrincipal = Depends(principal),
    ) -> dict[str, Any]:
        try:
            reports = await dependencies.store.list_reports(task_id, actor.user_id)
        except PermissionError:
            reports = await dependencies.store.list_reports(
                task_id,
                actor.user_id,
                is_super_admin=await verified_super_admin(actor),
            )
        return {"data": [_report_response(value, include_content=True) for value in reports]}

    @app.get(f"{API_PREFIX}/reports/{{report_id}}/download")
    async def download_report(
        report_id: str,
        request: Request,
        actor: AuthenticatedPrincipal = Depends(principal),
    ) -> Response:
        try:
            report = await dependencies.store.get_report(report_id, actor.user_id)
        except PermissionError:
            report = await dependencies.store.get_report(
                report_id,
                actor.user_id,
                is_super_admin=await verified_super_admin(actor),
            )
        await _audit(
            dependencies.store,
            request,
            actor.user_id,
            "REPORT_DOWNLOADED",
            "report",
            report.id,
            {"taskId": report.task_id, "version": report.version},
        )
        return Response(
            content=report.markdown_report,
            media_type="text/markdown; charset=utf-8",
            headers={
                "Content-Disposition": f'attachment; filename="impact-report-v{report.version}.md"'
            },
        )

    return app


def _same_session_actor(
    original: AuthenticatedPrincipal,
    refreshed: AuthenticatedPrincipal,
) -> bool:
    return (
        original.user_id == refreshed.user_id
        and original.unified_auth_id == refreshed.unified_auth_id
        and original.roles == refreshed.roles
        and original.session_digest == refreshed.session_digest
    )


def _conversation_response(value: Any) -> dict[str, Any]:
    return {
        "id": value.id,
        "title": value.title,
        "ownerUserId": value.owner_user_id,
        "createdAt": value.created_at.isoformat(),
    }


def _message_response(value: Any) -> dict[str, Any]:
    return {
        "id": value.id,
        "role": value.role,
        "content": value.content,
        "createdAt": value.created_at.isoformat(),
    }


def _report_response(value: Any, *, include_content: bool) -> dict[str, Any]:
    response = {
        "id": value.id,
        "taskId": value.task_id,
        "runId": value.run_id,
        "version": value.version,
        "current": value.is_current,
        "createdAt": value.created_at.isoformat(),
    }
    if include_content:
        response.update(
            {"structuredReport": value.structured_report, "markdownReport": value.markdown_report}
        )
    return response


def _event_cursor(value: str | None) -> int:
    if value is None or not value.strip():
        return 0
    try:
        parsed = int(value)
    except ValueError as exception:
        raise RequestValidationError(
            [{"type": "int_parsing", "loc": ("header", "Last-Event-ID"), "msg": "事件游标必须为整数"}]
        ) from exception
    if parsed < 0:
        raise RequestValidationError(
            [{"type": "greater_than_equal", "loc": ("header", "Last-Event-ID"), "msg": "事件游标不能为负数"}]
        )
    return parsed


def _server_event(event: AgUiEvent) -> ServerSentEvent:
    return ServerSentEvent(
        data=json.dumps({"type": event.type.value, **event.payload}, ensure_ascii=False),
        event=event.type.value,
        id=str(event.seq) if event.seq is not None else None,
    )


def _error(
    request: Request,
    status: int,
    code: str,
    message: str,
    details: dict[str, Any] | None = None,
) -> JSONResponse:
    return JSONResponse(
        status_code=status,
        content={
            "code": code,
            "message": message,
            "traceId": getattr(request.state, "trace_id", f"trace_{uuid4().hex}"),
            "details": details or {},
        },
    )


def _safe_validation_error(error: dict[str, Any]) -> dict[str, Any]:
    return {
        "type": str(error.get("type", "validation_error")),
        "location": [str(value) for value in error.get("loc", ())],
        "message": str(error.get("msg", "参数无效")),
    }


async def _audit(
    store: Any,
    request: Request,
    actor_user_id: str,
    action: str,
    resource_type: str,
    resource_id: str,
    details: dict[str, Any],
) -> None:
    await store.append_audit_log(
        actor_user_id=actor_user_id,
        action=action,
        resource_type=resource_type,
        resource_id=resource_id,
        trace_id=request.state.trace_id,
        details=details,
    )
