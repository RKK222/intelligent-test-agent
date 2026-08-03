"""仅供workflow Worker调用的Runner管理API。"""

from __future__ import annotations

from contextlib import asynccontextmanager, suppress
import asyncio
from typing import Any, Literal
from uuid import uuid4

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field, ValidationError

from testagent_runner.analyzer_executor import AnalyzerExecutionError
from testagent_runner.docker_runtime import DockerRuntimeError
from testagent_runner.security import RunnerHmacAuthenticator, RunnerRequestError
from testagent_runner.service import RunnerService, RunnerServiceError


class PrepareRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    run_id: str = Field(alias="runId", min_length=8, max_length=64)
    operation_key: str = Field(alias="operationKey", min_length=8, max_length=255)
    repositories: list[dict[str, Any]] = Field(min_length=1)
    analyzer_ids: list[Literal["codex", "opencode"]] = Field(
        alias="analyzerIds",
        min_length=1,
        max_length=3,
    )


class RunRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    run_id: str = Field(alias="runId", min_length=8, max_length=64)


class RetainRequest(RunRequest):
    operation_key: str = Field(alias="operationKey", min_length=8, max_length=255)
    retention_hours: int = Field(alias="retentionHours", ge=1, le=24 * 30)


class ScopeRequest(RunRequest):
    selectors: list[dict[str, Any]] = Field(min_length=1)


class AnalyzeRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    run_id: str = Field(alias="runId", min_length=8, max_length=64)
    operation_key: str = Field(alias="operationKey", min_length=8, max_length=255)
    model_grant: str = Field(alias="modelGrant", min_length=16)
    model_gateway_url: str = Field(alias="modelGatewayUrl", min_length=8)
    model_name: str = Field(
        alias="modelName",
        pattern=r"^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$",
    )
    scope_selectors: list[dict[str, Any]] = Field(default_factory=list, alias="scopeSelectors")
    output_schema: dict[str, Any] = Field(alias="outputSchema")
    repair_result: Any | None = Field(default=None, alias="repairResult")


def create_runner_app(
    service: RunnerService,
    authenticator: RunnerHmacAuthenticator,
    *,
    public_key_pem: str,
) -> FastAPI:
    cleanup_task: asyncio.Task[None] | None = None

    @asynccontextmanager
    async def lifespan(_: FastAPI):  # type: ignore[no-untyped-def]
        nonlocal cleanup_task

        async def cleanup_loop() -> None:
            while True:
                await asyncio.sleep(300)
                await service.cleanup_expired()

        cleanup_task = asyncio.create_task(cleanup_loop())
        try:
            yield
        finally:
            cleanup_task.cancel()
            with suppress(asyncio.CancelledError):
                await cleanup_task
            await service.aclose()

    app = FastAPI(title="Test Agent Analysis Runner", version="0.1.0", lifespan=lifespan)

    @app.middleware("http")
    async def trace(request: Request, call_next):  # type: ignore[no-untyped-def]
        request.state.trace_id = f"trace_{uuid4().hex}"
        response = await call_next(request)
        response.headers["X-Trace-Id"] = request.state.trace_id
        return response

    @app.exception_handler(RunnerRequestError)
    async def auth_error(request: Request, exception: RunnerRequestError) -> JSONResponse:
        return _error(request, 401, "RUNNER_UNAUTHENTICATED", str(exception))

    @app.exception_handler(RunnerServiceError)
    async def service_error(request: Request, exception: RunnerServiceError) -> JSONResponse:
        status = 409 if exception.code in {"WORKSPACE_ALREADY_EXISTS", "WORKSPACE_EXPIRED"} else 422
        return _error(request, status, exception.code, str(exception))

    @app.exception_handler(DockerRuntimeError)
    async def docker_error(request: Request, exception: DockerRuntimeError) -> JSONResponse:
        del exception
        return _error(
            request,
            503,
            "RUNNER_NOT_READY",
            "Docker版本、受限网络或非特权约束未通过验收",
        )

    @app.exception_handler(AnalyzerExecutionError)
    async def analyzer_error(
        request: Request, exception: AnalyzerExecutionError
    ) -> JSONResponse:
        del exception
        return _error(
            request,
            502,
            "ANALYZER_EXECUTION_FAILED",
            "代码智能体执行失败",
        )

    @app.exception_handler(ValidationError)
    async def validation_error(request: Request, exception: ValidationError) -> JSONResponse:
        return _error(request, 422, "VALIDATION_ERROR", "Runner请求参数不合法")

    @app.exception_handler(Exception)
    async def unexpected_error(request: Request, exception: Exception) -> JSONResponse:
        del exception
        return _error(request, 500, "INTERNAL_ERROR", "Runner内部错误")

    async def payload(request: Request, model: type[BaseModel]) -> BaseModel:
        body = await request.body()
        authenticator.verify(request.method, request.url.path, body, request.headers)
        return model.model_validate_json(body)

    @app.get("/runner-api/v1/health")
    async def health() -> dict[str, Any]:
        return {"status": "UP"}

    @app.get("/runner-api/v1/ready")
    async def ready() -> dict[str, Any]:
        return {"data": await service.readiness()}

    @app.get("/runner-api/v1/public-key")
    async def public_key() -> dict[str, Any]:
        return {"data": {"algorithm": "RSA-OAEP-256", "publicKey": public_key_pem}}

    @app.post("/runner-api/v1/tasks/{task_id}/prepare")
    async def prepare(task_id: str, request: Request) -> dict[str, Any]:
        value = await payload(request, PrepareRequest)
        assert isinstance(value, PrepareRequest)
        return {"data": await service.prepare(task_id, value.model_dump(mode="json", by_alias=True))}

    @app.post("/runner-api/v1/tasks/{task_id}/manifest")
    async def manifest(task_id: str, request: Request) -> dict[str, Any]:
        value = await payload(request, RunRequest)
        assert isinstance(value, RunRequest)
        return {"data": await service.manifest(task_id, value.run_id)}

    @app.post("/runner-api/v1/tasks/{task_id}/scope/resolve")
    async def resolve_scope(task_id: str, request: Request) -> dict[str, Any]:
        value = await payload(request, ScopeRequest)
        assert isinstance(value, ScopeRequest)
        return {
            "data": await service.resolve_scope(task_id, value.run_id, value.selectors)
        }

    @app.post("/runner-api/v1/tasks/{task_id}/resume")
    async def resume(task_id: str, request: Request) -> dict[str, Any]:
        value = await payload(request, RunRequest)
        assert isinstance(value, RunRequest)
        return {"data": await service.resume(task_id, value.run_id)}

    @app.post("/runner-api/v1/tasks/{task_id}/analyzers/{analyzer_id}")
    async def analyze(task_id: str, analyzer_id: str, request: Request) -> dict[str, Any]:
        value = await payload(request, AnalyzeRequest)
        assert isinstance(value, AnalyzeRequest)
        return {
            "data": await service.analyze(
                task_id,
                analyzer_id,
                value.model_dump(mode="json", by_alias=True),
            )
        }

    @app.post("/runner-api/v1/tasks/{task_id}/retain")
    async def retain(task_id: str, request: Request) -> dict[str, Any]:
        value = await payload(request, RetainRequest)
        assert isinstance(value, RetainRequest)
        return {
            "data": await service.retain(
                task_id,
                value.run_id,
                value.retention_hours,
                value.operation_key,
            )
        }

    @app.post("/runner-api/v1/tasks/{task_id}/cancel")
    async def cancel(task_id: str, request: Request) -> dict[str, Any]:
        value = await payload(request, RunRequest)
        assert isinstance(value, RunRequest)
        await service.cancel(task_id, value.run_id)
        return {"data": {"status": "CANCELED"}}

    @app.post("/runner-api/v1/tasks/{task_id}/cleanup")
    async def cleanup(task_id: str, request: Request) -> dict[str, Any]:
        value = await payload(request, RunRequest)
        assert isinstance(value, RunRequest)
        await service.cleanup(task_id, value.run_id)
        return {"data": {"status": "EXPIRED"}}

    return app


def _error(request: Request, status: int, code: str, message: str) -> JSONResponse:
    return JSONResponse(
        status_code=status,
        content={
            "code": code,
            "message": message,
            "traceId": request.state.trace_id,
            "details": {},
        },
    )
