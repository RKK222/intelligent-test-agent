from __future__ import annotations

import httpx
import pytest

from testagent_runner.analyzer_executor import AnalyzerExecutionError
from testagent_runner.api import create_runner_app


class AllowRequest:
    def verify(self, method, path, body, headers):  # type: ignore[no-untyped-def]
        del method, path, body, headers


class FailingAnalyzerService:
    async def analyze(self, task_id, analyzer_id, payload):  # type: ignore[no-untyped-def]
        del task_id, analyzer_id, payload
        raise AnalyzerExecutionError("供应商返回中可能包含敏感信息")


@pytest.mark.asyncio
async def test_analyzer_failure_uses_safe_unified_error_envelope() -> None:
    app = create_runner_app(
        FailingAnalyzerService(),  # type: ignore[arg-type]
        AllowRequest(),  # type: ignore[arg-type]
        public_key_pem="test-public-key",
    )
    transport = httpx.ASGITransport(app=app, raise_app_exceptions=False)
    async with httpx.AsyncClient(transport=transport, base_url="http://runner") as client:
        response = await client.post(
            "/runner-api/v1/tasks/task_12345678/analyzers/codex",
            json={
                "runId": "run_12345678",
                "operationKey": "run_12345678:analyze:codex",
                "modelGrant": "grant_1234567890abcdef",
                "modelGatewayUrl": "https://model.example.internal/v1",
                "modelName": "workflow-code-analysis",
                "scopeSelectors": [],
                "outputSchema": {"type": "object"},
            },
        )

    assert response.status_code == 502
    assert response.json()["code"] == "ANALYZER_EXECUTION_FAILED"
    assert response.json()["message"] == "代码智能体执行失败"
    assert "敏感信息" not in response.text
    assert response.json()["traceId"].startswith("trace_")
