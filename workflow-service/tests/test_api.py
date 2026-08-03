from __future__ import annotations

from datetime import UTC, datetime, timedelta
import json

import httpx
import pytest
import pytest_asyncio

from testagent_workflow.api import AppDependencies, create_app
from testagent_workflow.auth import RedisTokenAuthenticator
from testagent_workflow.models import RunStatus
from testagent_workflow.registry import IntentDecision, IntentInvocationContext, WorkflowRegistry
from testagent_workflow.store import InMemoryWorkflowStore
from testagent_workflow.workflows.impact_analysis import impact_analysis_definition


TOKEN = "platform-token"
AUTHORIZATION = {"Authorization": f"Bearer {TOKEN}"}


class FakeRedis:
    async def get(self, key: str) -> str | None:
        if key != f"test-agent:token:{TOKEN}":
            return None
        return json.dumps(
            {
                "token": TOKEN,
                "userId": {"value": "usr_1234567890abcdef"},
                "username": "测试用户",
                "unifiedAuthId": "001177621",
                "roles": ["USER"],
                "issuedAt": (datetime.now(UTC) - timedelta(minutes=1)).isoformat(),
                "expiresAt": (datetime.now(UTC) + timedelta(hours=1)).isoformat(),
            }
        )

    async def pttl(self, key: str) -> int:
        return 3_600_000 if key == f"test-agent:token:{TOKEN}" else -2


class LogoutDuringSseRedis(FakeRedis):
    def __init__(self) -> None:
        self.reads = 0

    async def get(self, key: str) -> str | None:
        self.reads += 1
        if self.reads > 1:
            return None
        return await super().get(key)


class RoleChangedDuringSseRedis(FakeRedis):
    def __init__(self) -> None:
        self.reads = 0

    async def get(self, key: str) -> str | None:
        self.reads += 1
        raw = await super().get(key)
        assert raw is not None
        value = json.loads(raw)
        if self.reads > 1:
            value["roles"] = ["SUPER_ADMIN"]
        return json.dumps(value)


class FakePlatform:
    async def list_repositories(self, identity):  # type: ignore[no-untyped-def]
        assert identity.user_id == "usr_1234567890abcdef"
        return [
            {
                "applicationId": "app_12345678",
                "applicationName": "订单中心",
                "repositories": [{"id": "repo_1234567890abcdef", "name": "orders"}],
            }
        ]

    async def list_branches(self, identity, repository_id):  # type: ignore[no-untyped-def]
        assert repository_id == "repo_1234567890abcdef"
        return [{"name": "main", "default": True}, {"name": "feature-a", "default": False}]

    async def verify_super_admin(self, identity):  # type: ignore[no-untyped-def]
        return False


class FakeReportQuestionAnswerer:
    def __init__(self) -> None:
        self.questions: list[str] = []

    async def answer(self, question, report, context):  # type: ignore[no-untyped-def]
        self.questions.append(question)
        assert report.structured_report["changeOverview"]["fileCount"] == 1
        assert context.user_id == "usr_1234567890abcdef"
        return "订单校验风险来自 `OrderService.java` 的条件分支变化。"


async def classifier(
    message: str,
    allowed: tuple[str, ...],
    context: IntentInvocationContext,
) -> IntentDecision:
    assert allowed == ("code-change-impact-analysis",)
    assert context.user_id == "usr_1234567890abcdef"
    return IntentDecision(
        intent_id="code-change-impact-analysis",
        confidence=0.95,
        slots={},
        missing_fields=("repositories", "analysisMode", "analyzerIds"),
    )


@pytest_asyncio.fixture
async def client() -> httpx.AsyncClient:
    registry = WorkflowRegistry([impact_analysis_definition()])
    store = InMemoryWorkflowStore()
    question_answerer = FakeReportQuestionAnswerer()
    app = create_app(
        AppDependencies(
            authenticator=RedisTokenAuthenticator(FakeRedis()),
            store=store,
            registry=registry,
            classifier=classifier,
            platform=FakePlatform(),  # type: ignore[arg-type]
            question_answerer=question_answerer,
        )
    )
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app),
        base_url="http://workflow.test",
    ) as api:
        api.workflow_store = store  # type: ignore[attr-defined]
        api.question_answerer = question_answerer  # type: ignore[attr-defined]
        yield api


@pytest.mark.asyncio
async def test_me_and_definitions_are_served_by_python(client: httpx.AsyncClient) -> None:
    me = await client.get("/workflow-api/v1/me", headers=AUTHORIZATION)
    definitions = await client.get("/workflow-api/v1/definitions", headers=AUTHORIZATION)

    assert me.status_code == 200
    assert me.json()["data"] == {
        "userId": "usr_1234567890abcdef",
        "username": "测试用户",
        "unifiedAuthId": "001177621",
        "roles": ["USER"],
    }
    definition = definitions.json()["data"][0]
    assert definition["id"] == "code-change-impact-analysis"
    assert definition["requiredPermissions"] == ["repository:read"]
    assert definition["requiresSandbox"] is True
    assert definition["intentExamples"]
    assert definition["outputSchema"]["type"] == "object"


@pytest.mark.asyncio
async def test_liveness_and_readiness_do_not_require_a_second_login_cookie() -> None:
    async def unavailable() -> dict[str, str]:
        raise RuntimeError("postgresql://secret must not be returned")

    app = create_app(
        AppDependencies(
            authenticator=RedisTokenAuthenticator(FakeRedis()),
            store=InMemoryWorkflowStore(),
            registry=WorkflowRegistry([impact_analysis_definition()]),
            classifier=classifier,
            readiness_probe=unavailable,
        )
    )
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app), base_url="http://workflow.test"
    ) as api:
        health = await api.get("/workflow-api/v1/health")
        ready = await api.get("/workflow-api/v1/ready")

    assert health.json() == {"status": "UP"}
    assert ready.status_code == 503
    assert ready.json()["code"] == "WORKFLOW_NOT_READY"
    assert "postgresql" not in ready.text


@pytest.mark.asyncio
async def test_missing_input_returns_agui_input_request_without_starting_run(
    client: httpx.AsyncClient,
) -> None:
    created = await client.post(
        "/workflow-api/v1/conversations",
        headers=AUTHORIZATION,
        json={"title": "影响分析"},
    )
    conversation_id = created.json()["data"]["id"]

    submitted = await client.post(
        f"/workflow-api/v1/conversations/{conversation_id}/messages",
        headers=AUTHORIZATION,
        json={
            "clientRequestId": "req_missing_123456",
            "text": "帮我分析代码变化的影响",
        },
    )

    assert submitted.status_code == 200
    assert submitted.json()["data"]["runId"] is None
    assert submitted.json()["data"]["requiredInput"] == [
        "repositories",
        "analysisMode",
        "analyzerIds",
    ]
    events = await client.workflow_store.list_events(conversation_id)  # type: ignore[attr-defined]
    assert [event.type.value for event in events[-4:]] == [
        "TEXT_MESSAGE_START",
        "TEXT_MESSAGE_CONTENT",
        "TEXT_MESSAGE_END",
        "CUSTOM",
    ]


@pytest.mark.asyncio
async def test_complete_input_auto_starts_once_for_duplicate_request(
    client: httpx.AsyncClient,
) -> None:
    created = await client.post(
        "/workflow-api/v1/conversations",
        headers=AUTHORIZATION,
        json={"title": "影响分析"},
    )
    conversation_id = created.json()["data"]["id"]
    payload = {
        "clientRequestId": "req_complete_123456",
        "text": "分析feature-a",
        "structuredInput": {
            "repositories": [
                {
                    "repositoryId": "repo_1234567890abcdef",
                    "targetBranch": "feature-a",
                }
            ],
            "mode": "SINGLE",
            "analyzerIds": ["codex"],
        },
    }

    first = await client.post(
        f"/workflow-api/v1/conversations/{conversation_id}/messages",
        headers=AUTHORIZATION,
        json=payload,
    )
    replay = await client.post(
        f"/workflow-api/v1/conversations/{conversation_id}/messages",
        headers=AUTHORIZATION,
        json=payload,
    )

    assert first.status_code == 200
    assert first.json()["data"]["status"] == "QUEUED"
    assert first.json()["data"]["runId"].startswith("run_")
    assert replay.json() == first.json()


@pytest.mark.asyncio
async def test_errors_use_safe_traceable_envelope(client: httpx.AsyncClient) -> None:
    response = await client.get("/workflow-api/v1/me")

    assert response.status_code == 401
    body = response.json()
    assert body["code"] == "UNAUTHENTICATED"
    assert body["message"] == "缺少登录凭证"
    assert body["traceId"].startswith("trace_")
    assert "platform-token" not in response.text


@pytest.mark.asyncio
async def test_repository_and_conversation_queries_are_python_endpoints(
    client: httpx.AsyncClient,
) -> None:
    created = await client.post(
        "/workflow-api/v1/conversations",
        headers=AUTHORIZATION,
        json={"title": "独立工作流"},
    )
    conversation_id = created.json()["data"]["id"]

    conversations = await client.get("/workflow-api/v1/conversations", headers=AUTHORIZATION)
    detail = await client.get(
        f"/workflow-api/v1/conversations/{conversation_id}", headers=AUTHORIZATION
    )
    repositories = await client.get("/workflow-api/v1/repositories", headers=AUTHORIZATION)
    branches = await client.get(
        "/workflow-api/v1/repositories/repo_1234567890abcdef/branches",
        headers=AUTHORIZATION,
    )

    assert conversations.json()["data"][0]["id"] == conversation_id
    assert detail.json()["data"]["messages"] == []
    assert repositories.json()["data"][0]["applicationName"] == "订单中心"
    assert branches.json()["data"][0] == {"name": "main", "default": True}


@pytest.mark.asyncio
async def test_cancel_and_report_download_keep_workflow_data_in_python(
    client: httpx.AsyncClient,
) -> None:
    created = await client.post(
        "/workflow-api/v1/conversations", headers=AUTHORIZATION, json={"title": "报告"}
    )
    conversation_id = created.json()["data"]["id"]
    started = await client.post(
        f"/workflow-api/v1/conversations/{conversation_id}/messages",
        headers=AUTHORIZATION,
        json={
            "clientRequestId": "req_report_12345678",
            "text": "分析",
            "structuredInput": {
                "repositories": [
                    {"repositoryId": "repo_1234567890abcdef", "targetBranch": "feature-a"}
                ],
                "mode": "SINGLE",
                "analyzerIds": ["codex"],
            },
        },
    )
    run = started.json()["data"]
    store = client.workflow_store  # type: ignore[attr-defined]
    report = await store.publish_report(
        run["taskId"],
        run["runId"],
        "usr_1234567890abcdef",
        {"changeOverview": {"fileCount": 1}},
        "# 影响报告\n",
    )

    listed = await client.get(
        f"/workflow-api/v1/tasks/{run['taskId']}/reports", headers=AUTHORIZATION
    )
    downloaded = await client.get(
        f"/workflow-api/v1/reports/{report.id}/download", headers=AUTHORIZATION
    )
    canceled = await client.post(
        f"/workflow-api/v1/runs/{run['runId']}/cancel", headers=AUTHORIZATION
    )

    assert listed.json()["data"][0]["markdownReport"] == "# 影响报告\n"
    assert downloaded.text == "# 影响报告\n"
    assert downloaded.headers["content-type"].startswith("text/markdown")
    assert canceled.json()["data"]["status"] == "CANCELED"

    reanalysis = await client.post(
        f"/workflow-api/v1/conversations/{conversation_id}/messages",
        headers=AUTHORIZATION,
        json={
            "clientRequestId": "req_reanalysis_123456",
            "text": "只重新分析 OrderService",
            "structuredInput": {
                "scopeSelectors": [
                    {
                        "repositoryId": "repo_1234567890abcdef",
                        "kind": "SYMBOL",
                        "value": "OrderService",
                    }
                ]
            },
        },
    )
    assert reanalysis.status_code == 409
    assert reanalysis.json()["code"] == "WORKSPACE_EXPIRED_NEW_TASK_REQUIRED"


@pytest.mark.asyncio
async def test_cancel_is_idempotent_and_does_not_rewrite_completed_run(
    client: httpx.AsyncClient,
) -> None:
    created = await client.post(
        "/workflow-api/v1/conversations", headers=AUTHORIZATION, json={"title": "取消幂等"}
    )
    conversation_id = created.json()["data"]["id"]
    started = await client.post(
        f"/workflow-api/v1/conversations/{conversation_id}/messages",
        headers=AUTHORIZATION,
        json={
            "clientRequestId": "req_cancel_idempotent_1234",
            "text": "分析",
            "structuredInput": {
                "repositories": [
                    {"repositoryId": "repo_1234567890abcdef", "targetBranch": "feature-a"}
                ],
                "mode": "SINGLE",
                "analyzerIds": ["codex"],
            },
        },
    )
    run = started.json()["data"]

    first = await client.post(
        f"/workflow-api/v1/runs/{run['runId']}/cancel", headers=AUTHORIZATION
    )
    second = await client.post(
        f"/workflow-api/v1/runs/{run['runId']}/cancel", headers=AUTHORIZATION
    )

    assert first.status_code == 200
    assert second.status_code == 200
    events = await client.workflow_store.list_events(conversation_id)  # type: ignore[attr-defined]
    canceled = [
        event
        for event in events
        if event.type.value == "RUN_FINISHED" and event.payload.get("status") == "CANCELED"
    ]
    assert len(canceled) == 1

    completed_conversation = await client.post(
        "/workflow-api/v1/conversations", headers=AUTHORIZATION, json={"title": "完成任务"}
    )
    completed_id = completed_conversation.json()["data"]["id"]
    completed = await client.post(
        f"/workflow-api/v1/conversations/{completed_id}/messages",
        headers=AUTHORIZATION,
        json={
            "clientRequestId": "req_completed_cancel_1234",
            "text": "分析",
            "structuredInput": {
                "repositories": [
                    {"repositoryId": "repo_1234567890abcdef", "targetBranch": "feature-a"}
                ],
                "mode": "SINGLE",
                "analyzerIds": ["codex"],
            },
        },
    )
    completed_run = completed.json()["data"]
    await client.workflow_store.update_run_status(  # type: ignore[attr-defined]
        completed_run["runId"], RunStatus.SUCCEEDED
    )

    rejected = await client.post(
        f"/workflow-api/v1/runs/{completed_run['runId']}/cancel", headers=AUTHORIZATION
    )
    assert rejected.status_code == 409
    assert rejected.json()["code"] == "RUN_NOT_CANCELABLE"
    assert (
        await client.workflow_store.get_run(  # type: ignore[attr-defined]
            completed_run["runId"], "usr_1234567890abcdef"
        )
    ).status is RunStatus.SUCCEEDED


@pytest.mark.asyncio
async def test_scope_clarification_requeues_the_waiting_run_instead_of_creating_another(
    client: httpx.AsyncClient,
) -> None:
    created = await client.post(
        "/workflow-api/v1/conversations", headers=AUTHORIZATION, json={"title": "范围消歧"}
    )
    conversation_id = created.json()["data"]["id"]
    started = await client.post(
        f"/workflow-api/v1/conversations/{conversation_id}/messages",
        headers=AUTHORIZATION,
        json={
            "clientRequestId": "req_scope_start_123456",
            "text": "分析OrderService",
            "structuredInput": {
                "repositories": [
                    {"repositoryId": "repo_1234567890abcdef", "targetBranch": "feature-a"}
                ],
                "mode": "SINGLE",
                "analyzerIds": ["codex"],
                "scopeSelectors": [
                    {
                        "repositoryId": "repo_1234567890abcdef",
                        "kind": "SYMBOL",
                        "value": "OrderService",
                    }
                ],
            },
        },
    )
    original = started.json()["data"]
    store = client.workflow_store  # type: ignore[attr-defined]
    await store.update_run_status(original["runId"], RunStatus.WAITING_INPUT)

    clarified = await client.post(
        f"/workflow-api/v1/conversations/{conversation_id}/messages",
        headers=AUTHORIZATION,
        json={
            "clientRequestId": "req_scope_clarify_123456",
            "text": "是 src/orders/OrderService.java",
            "structuredInput": {
                "scopeSelectors": [
                    {
                        "repositoryId": "repo_1234567890abcdef",
                        "kind": "FILE",
                        "value": "src/orders/OrderService.java",
                    }
                ]
            },
        },
    )

    assert clarified.status_code == 200
    assert clarified.json()["data"]["runId"] == original["runId"]
    assert clarified.json()["data"]["status"] == "QUEUED"


@pytest.mark.asyncio
async def test_follow_up_after_report_answers_from_persisted_report_without_new_run(
    client: httpx.AsyncClient,
) -> None:
    created = await client.post(
        "/workflow-api/v1/conversations", headers=AUTHORIZATION, json={"title": "报告追问"}
    )
    conversation_id = created.json()["data"]["id"]
    started = await client.post(
        f"/workflow-api/v1/conversations/{conversation_id}/messages",
        headers=AUTHORIZATION,
        json={
            "clientRequestId": "req_qa_start_12345678",
            "text": "分析feature-a",
            "structuredInput": {
                "repositories": [
                    {"repositoryId": "repo_1234567890abcdef", "targetBranch": "feature-a"}
                ],
                "mode": "SINGLE",
                "analyzerIds": ["codex"],
            },
        },
    )
    run = started.json()["data"]
    store = client.workflow_store  # type: ignore[attr-defined]
    await store.update_run_status(run["runId"], RunStatus.SUCCEEDED)
    await store.publish_report(
        run["taskId"],
        run["runId"],
        "usr_1234567890abcdef",
        {"changeOverview": {"fileCount": 1}},
        "# 报告\n",
    )

    answered = await client.post(
        f"/workflow-api/v1/conversations/{conversation_id}/messages",
        headers=AUTHORIZATION,
        json={
            "clientRequestId": "req_qa_followup_123456",
            "text": "这个风险为什么会影响订单校验？",
        },
    )

    assert answered.status_code == 200
    assert answered.json()["data"]["runId"] is None
    assert answered.json()["data"]["requiredInput"] == []
    detail = await client.get(
        f"/workflow-api/v1/conversations/{conversation_id}", headers=AUTHORIZATION
    )
    assert detail.json()["data"]["messages"][-1]["content"].startswith("订单校验风险")
    assert client.question_answerer.questions == ["这个风险为什么会影响订单校验？"]  # type: ignore[attr-defined]


@pytest.mark.asyncio
async def test_sse_rechecks_exact_token_and_closes_after_platform_logout() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_1234567890abcdef", "登出断流")
    redis = LogoutDuringSseRedis()
    app = create_app(
        AppDependencies(
            authenticator=RedisTokenAuthenticator(redis),
            store=store,
            registry=WorkflowRegistry([impact_analysis_definition()]),
            classifier=classifier,
            sse_auth_recheck_seconds=0,
            sse_poll_seconds=0,
        )
    )

    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app),
        base_url="http://workflow.test",
    ) as api:
        response = await api.get(
            f"/workflow-api/v1/conversations/{conversation.id}/events",
            headers=AUTHORIZATION,
        )

    assert response.status_code == 200
    assert "STATE_SNAPSHOT" in response.text
    assert redis.reads == 2


@pytest.mark.asyncio
async def test_sse_closes_if_platform_identity_or_roles_change_in_place() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_1234567890abcdef", "角色变化断流")
    redis = RoleChangedDuringSseRedis()
    app = create_app(
        AppDependencies(
            authenticator=RedisTokenAuthenticator(redis),
            store=store,
            registry=WorkflowRegistry([impact_analysis_definition()]),
            classifier=classifier,
            sse_auth_recheck_seconds=0,
            sse_poll_seconds=0,
        )
    )

    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app), base_url="http://workflow.test"
    ) as api:
        response = await api.get(
            f"/workflow-api/v1/conversations/{conversation.id}/events",
            headers=AUTHORIZATION,
        )

    assert response.status_code == 200
    assert redis.reads == 2


@pytest.mark.asyncio
async def test_lifespan_closes_all_external_clients_even_if_one_close_fails() -> None:
    closed: list[str] = []

    async def first() -> None:
        closed.append("first")

    async def failing() -> None:
        closed.append("failing")
        raise RuntimeError("close failed")

    async def last() -> None:
        closed.append("last")

    app = create_app(
        AppDependencies(
            authenticator=RedisTokenAuthenticator(FakeRedis()),
            store=InMemoryWorkflowStore(),
            registry=WorkflowRegistry([impact_analysis_definition()]),
            classifier=classifier,
            shutdown_callbacks=(first, failing, last),
        )
    )

    async with app.router.lifespan_context(app):
        pass

    assert closed == ["last", "failing", "first"]


@pytest.mark.asyncio
async def test_audit_log_records_resource_actions_without_token_or_message_body(
    client: httpx.AsyncClient,
) -> None:
    created = await client.post(
        "/workflow-api/v1/conversations",
        headers=AUTHORIZATION,
        json={"title": "审计"},
    )
    conversation_id = created.json()["data"]["id"]
    await client.post(
        f"/workflow-api/v1/conversations/{conversation_id}/messages",
        headers=AUTHORIZATION,
        json={
            "clientRequestId": "req_audit_12345678",
            "text": "这段用户正文不能进入审计",
        },
    )

    audits = await client.workflow_store.list_audit_logs()  # type: ignore[attr-defined]
    serialized = json.dumps(audits, ensure_ascii=False)
    assert [value["action"] for value in audits] == [
        "CONVERSATION_CREATED",
        "MESSAGE_SUBMITTED",
    ]
    assert TOKEN not in serialized
    assert "这段用户正文不能进入审计" not in serialized
