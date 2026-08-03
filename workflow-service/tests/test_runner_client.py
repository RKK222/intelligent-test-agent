from __future__ import annotations

import asyncio
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
import json
import re

import httpx
import pytest

from testagent_workflow.platform import PlatformRequestIdentity
from testagent_workflow.runner_client import (
    PlatformAuthorizationAdapter,
    ReportFollowupRetentionService,
    RemoteRunnerAdapter,
    RunGrantManager,
    RunCancellationService,
    RunnerApiError,
    RunnerAnalyzerAdapter,
    RunnerApiClient,
)
from testagent_workflow.impact_engine import DiffManifest, FrozenRepository
from testagent_workflow.application import ReportQuestionContext
from testagent_workflow.models import RunStatus
from testagent_workflow.store import InMemoryWorkflowStore


class FakePlatform:
    def __init__(self) -> None:
        self.revoked: tuple[str, str, str] | None = None

    async def revoke_model_grants_for_run(self, identity, *, task_id, run_id):  # type: ignore[no-untyped-def]
        self.revoked = (identity.user_id, task_id, run_id)

    async def issue_checkout_ticket(self, identity, **scope):  # type: ignore[no-untyped-def]
        return "ticket_12345678"

    async def authorize_repositories(self, identity, repository_ids):  # type: ignore[no-untyped-def]
        return [
            {"repositoryId": repository_id, "englishName": "orders"}
            for repository_id in repository_ids
        ]

    async def list_branches(self, identity, repository_id):  # type: ignore[no-untyped-def]
        return [
            {"name": "develop", "default": False},
            {"name": "feature/a", "default": False},
        ]


@pytest.mark.asyncio
async def test_runner_client_does_not_impose_a_total_analysis_read_timeout() -> None:
    client = RunnerApiClient(
        "http://runner.test",
        "runner-a",
        b"0123456789abcdef0123456789abcdef",
    )

    assert client._http.timeout.read is None  # noqa: SLF001 - 锁定无总分析时长限制
    assert client._http._trust_env is False  # noqa: SLF001 - 锁定内部调用不继承宿主代理
    await client.aclose()


@pytest.mark.asyncio
async def test_runner_cleanup_uses_a_distinct_signed_endpoint() -> None:
    captured: dict[str, object] = {}

    async def handler(request: httpx.Request) -> httpx.Response:
        captured["path"] = request.url.path
        captured["body"] = json.loads(request.content)
        return httpx.Response(200, json={"data": {"status": "EXPIRED"}})

    client = RunnerApiClient(
        "http://runner.test",
        "runner-analysis-1",
        b"r" * 32,
        http_client=httpx.AsyncClient(transport=httpx.MockTransport(handler)),
    )

    await client.cleanup("task_12345678", "run_12345678")
    await client.aclose()

    assert captured == {
        "path": "/runner-api/v1/tasks/task_12345678/cleanup",
        "body": {"runId": "run_12345678"},
    }


class FakeRunner:
    def __init__(self) -> None:
        self.canceled: tuple[str, str] | None = None

    async def cancel(self, task_id: str, run_id: str) -> None:
        self.canceled = (task_id, run_id)


class FailingCancellationRunner(FakeRunner):
    async def cancel(self, task_id: str, run_id: str) -> None:
        self.canceled = (task_id, run_id)
        raise RuntimeError("runner internal address must not escape")


class FakeCancellationStore:
    def __init__(self) -> None:
        self.statuses: list[tuple[str, str]] = []

    async def set_workspace_cleanup_status(self, task_id: str, status: str) -> None:
        self.statuses.append((task_id, status))


class FakeWorkspaceRunner(FakeRunner):
    runner_id = "runner-a"

    async def prepare(self, task_id, payload):  # type: ignore[no-untyped-def]
        return {
            "repositories": [
                {
                    "repositoryId": "repo_12345678",
                    "repositoryAlias": "orders",
                    "defaultBranch": "main",
                    "defaultHead": "a" * 40,
                    "targetBranch": "feature/a",
                    "targetHead": "b" * 40,
                    "mergeBase": "a" * 40,
                }
            ],
            "workspace": {
                "status": "ACTIVE",
                "containerId": "container-1",
                "imageDigest": "analysis@sha256:" + "c" * 64,
                "expiresAt": None,
            },
        }


class FakeStore:
    def __init__(self) -> None:
        self.repositories = []
        self.lease = None
        self.events = []

    async def save_task_repositories(self, task_id, values):  # type: ignore[no-untyped-def]
        self.repositories = values

    async def upsert_workspace_lease(self, task_id, **value):  # type: ignore[no-untyped-def]
        self.lease = {"taskId": task_id, **value}

    async def append_event(  # type: ignore[no-untyped-def]
        self, conversation_id, event_type, payload, *, dedup_key=None
    ):
        del dedup_key
        self.events.append((conversation_id, event_type.value, payload))


class FakeGrantManager:
    def __init__(self) -> None:
        self.revoke_calls = 0

    async def revoke(self) -> None:
        self.revoke_calls += 1


@dataclass
class FakeRun:
    id: str = "run_12345678"
    task_id: str = "task_12345678"


@pytest.mark.asyncio
async def test_cancel_revokes_all_run_grants_and_removes_workspace() -> None:
    platform = FakePlatform()
    runner = FakeRunner()
    store = FakeCancellationStore()

    await RunCancellationService(platform, runner, store).cancel(  # type: ignore[arg-type]
        FakeRun(),
        PlatformRequestIdentity("usr_owner", "a" * 64),
    )

    assert platform.revoked == ("usr_owner", "task_12345678", "run_12345678")
    assert runner.canceled == ("task_12345678", "run_12345678")
    assert store.statuses == [("task_12345678", "EXPIRED")]


@pytest.mark.asyncio
async def test_cancel_schedules_durable_cleanup_when_runner_is_unavailable() -> None:
    platform = FakePlatform()
    runner = FailingCancellationRunner()
    store = FakeCancellationStore()

    with pytest.raises(RunnerApiError) as captured:
        await RunCancellationService(platform, runner, store).cancel(  # type: ignore[arg-type]
            FakeRun(),
            PlatformRequestIdentity("usr_owner", "a" * 64),
        )

    assert captured.value.code == "RUN_CANCELLATION_INCOMPLETE"
    assert platform.revoked == ("usr_owner", "task_12345678", "run_12345678")
    assert store.statuses == [("task_12345678", "CLEANUP_FAILED")]


@pytest.mark.asyncio
async def test_freeze_persists_runner_workspace_lease_without_credentials() -> None:
    platform = FakePlatform()
    runner = FakeWorkspaceRunner()
    store = FakeStore()
    adapter = RemoteRunnerAdapter(
        platform,  # type: ignore[arg-type]
        runner,  # type: ignore[arg-type]
        "runner-public-key",
        store,
        FakeGrantManager(),  # type: ignore[arg-type]
    )
    state = {
        "task_id": "task_12345678",
        "run_id": "run_12345678",
        "conversation_id": "conv_12345678",
        "owner_user_id": "usr_owner",
        "session_digest": "a" * 64,
        "run_kind": "INITIAL",
        "input_data": {
            "repositories": [
                {"repositoryId": "repo_12345678", "targetBranch": "feature/a"}
            ],
            "analyzerIds": ["codex"],
        },
    }

    frozen = await adapter.freeze(
        state,  # type: ignore[arg-type]
        [{"repositoryId": "repo_12345678", "englishName": "orders"}],
        "run_12345678:freeze:coordinates",
    )

    assert frozen[0].target_head == "b" * 40
    assert store.lease["status"] == "ACTIVE"
    assert store.lease["container_id"] == "container-1"
    assert "ticket" not in str(store.lease).lower()
    assert [event[2]["value"]["status"] for event in store.events] == [
        "PROVISIONING",
        "ACTIVE",
    ]


@pytest.mark.asyncio
async def test_lease_loss_cleanup_revokes_only_the_adapter_model_grant() -> None:
    grants = FakeGrantManager()
    adapter = RemoteRunnerAdapter(
        FakePlatform(),  # type: ignore[arg-type]
        FakeWorkspaceRunner(),  # type: ignore[arg-type]
        "runner-public-key",
        FakeStore(),
        grants,  # type: ignore[arg-type]
    )

    await adapter.revoke_model_access()

    assert grants.revoke_calls == 1


class MultiRepositoryRunner:
    runner_id = "runner-a"

    def __init__(self) -> None:
        self.payload = None

    async def prepare(self, task_id, payload):  # type: ignore[no-untyped-def]
        self.payload = payload
        return {
            "repositories": [
                {
                    "repositoryId": value["repositoryId"],
                    "repositoryAlias": value["repositoryAlias"],
                    "defaultBranch": "main",
                    "defaultHead": "a" * 40,
                    "targetBranch": value["targetBranch"],
                    "targetHead": "b" * 40,
                    "mergeBase": "a" * 40,
                }
                for value in payload["repositories"]
            ],
            "workspace": {
                "status": "ACTIVE",
                "containerId": "container-multi",
                "imageDigest": "analysis@sha256:" + "c" * 64,
                "expiresAt": None,
            },
        }


@pytest.mark.asyncio
async def test_freeze_generates_unique_safe_aliases_for_same_named_cross_app_repositories() -> None:
    runner = MultiRepositoryRunner()
    store = FakeStore()
    adapter = RemoteRunnerAdapter(
        FakePlatform(),  # type: ignore[arg-type]
        runner,  # type: ignore[arg-type]
        "runner-public-key",
        store,
        FakeGrantManager(),  # type: ignore[arg-type]
    )
    state = {
        "task_id": "task_12345678",
        "run_id": "run_12345678",
        "conversation_id": "conv_12345678",
        "owner_user_id": "usr_owner",
        "session_digest": "a" * 64,
        "run_kind": "INITIAL",
        "input_data": {
            "repositories": [
                {"repositoryId": "repo_12345678", "targetBranch": "feature/a"},
                {"repositoryId": "repo_87654321", "targetBranch": "feature/b"},
            ],
            "analyzerIds": ["codex"],
        },
    }

    frozen = await adapter.freeze(
        state,  # type: ignore[arg-type]
        [
            {"repositoryId": "repo_12345678", "englishName": "orders / core"},
            {"repositoryId": "repo_87654321", "englishName": "orders / core"},
        ],
        "run_12345678:freeze:coordinates",
    )

    aliases = [value.alias for value in frozen]
    assert len(set(aliases)) == 2
    assert all(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}", value) for value in aliases)
    assert all(value.startswith("orders-core-") for value in aliases)
    assert [value["repositoryAlias"] for value in runner.payload["repositories"]] == aliases


class RetainingRunner:
    runner_id = "runner-a"

    def __init__(self) -> None:
        self.calls: list[tuple[str, str, int, str]] = []

    async def retain(
        self,
        task_id: str,
        run_id: str,
        retention_hours: int,
        operation_key: str,
    ) -> dict[str, object]:
        self.calls.append((task_id, run_id, retention_hours, operation_key))
        return {
            "workspace": {
                "status": "STOPPED_RETAINED",
                "containerId": "container-1",
                "imageDigest": "analysis@sha256:" + "c" * 64,
                "expiresAt": (datetime.now(UTC) + timedelta(hours=48)).isoformat(),
            }
        }


@pytest.mark.asyncio
async def test_report_followup_retention_rolls_control_and_runner_leases() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "追问续期")
    run = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={},
    )
    await store.update_run_status(run.id, RunStatus.SUCCEEDED)
    report = await store.publish_report(
        run.task_id,
        run.id,
        "usr_owner",
        {"changeOverview": {}},
        "# report",
    )
    await store.upsert_workspace_lease(
        run.task_id,
        runner_id="runner-a",
        container_id="container-1",
        image_digest="analysis@sha256:" + "c" * 64,
        status="STOPPED_RETAINED",
        expires_at=datetime.now(UTC) + timedelta(hours=1),
        metadata={"runId": run.id},
    )
    runner = RetainingRunner()
    service = ReportFollowupRetentionService(store, runner, retention_hours=48)

    await service.roll(
        report,
        ReportQuestionContext("usr_owner", "a" * 64, "msg_followup_12345678"),
    )

    assert len(runner.calls) == 1
    assert runner.calls[0][:3] == (run.task_id, run.id, 48)
    assert runner.calls[0][3].startswith(f"{run.id}:workspace:followup-")
    lease = await store.get_workspace_lease(run.task_id)
    assert lease["status"] == "STOPPED_RETAINED"
    assert lease["expiresAt"] > datetime.now(UTC) + timedelta(hours=47)
    events = await store.list_events(conversation.id)
    assert events[-1].payload["name"] == "workflow.workspace_state"
    assert events[-1].payload["value"]["expiresAt"] is not None


@pytest.mark.asyncio
async def test_report_followup_does_not_revive_an_expired_workspace() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "过期报告追问")
    run = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={},
    )
    await store.update_run_status(run.id, RunStatus.SUCCEEDED)
    report = await store.publish_report(
        run.task_id,
        run.id,
        "usr_owner",
        {"changeOverview": {}},
        "# report",
    )
    await store.upsert_workspace_lease(
        run.task_id,
        runner_id="runner-a",
        container_id=None,
        image_digest="analysis@sha256:" + "c" * 64,
        status="STOPPED_RETAINED",
        expires_at=datetime.now(UTC) - timedelta(seconds=1),
        metadata={"runId": run.id},
    )
    runner = RetainingRunner()

    await ReportFollowupRetentionService(
        store,
        runner,
        retention_hours=48,
    ).roll(
        report,
        ReportQuestionContext("usr_owner", "a" * 64, "msg_followup_expired"),
    )

    assert runner.calls == []
    assert (await store.get_workspace_lease(run.task_id))["status"] == "EXPIRED"


@pytest.mark.asyncio
async def test_authorization_adapter_exposes_default_resolution_without_git_credentials() -> None:
    state = {
        "owner_user_id": "usr_owner",
        "session_digest": "a" * 64,
        "input_data": {
            "repositories": [
                {"repositoryId": "repo_12345678", "targetBranch": "feature/a"}
            ]
        },
    }

    authorized = await PlatformAuthorizationAdapter(FakePlatform()).authorize(  # type: ignore[arg-type]
        state,  # type: ignore[arg-type]
        "run_12345678:authorize:repositories",
    )

    assert authorized == [
        {
            "repositoryId": "repo_12345678",
            "englishName": "orders",
            "defaultBranch": None,
            "availableBranches": ["develop", "feature/a"],
        }
    ]


class RenewingGrantPlatform:
    def __init__(self) -> None:
        self.refresh_calls = 0
        self.revoked = False

    async def issue_model_grant(self, identity, **scope):  # type: ignore[no-untyped-def]
        return {
            "grantId": "wfgrantid_stable",
            "grant": "wfg_stable_token",
            "gatewayUrl": "http://model.test/v1",
            "expiresAt": (datetime.now(UTC) + timedelta(seconds=0.15)).isoformat(),
        }

    async def refresh_model_grant(self, identity, **scope):  # type: ignore[no-untyped-def]
        self.refresh_calls += 1
        return {
            "grantId": "wfgrantid_stable",
            "gatewayUrl": "http://model.test/v1",
            "expiresAt": (datetime.now(UTC) + timedelta(seconds=0.15)).isoformat(),
        }

    async def revoke_model_grant(self, identity, **scope):  # type: ignore[no-untyped-def]
        self.revoked = True


@pytest.mark.asyncio
async def test_run_grant_is_renewed_without_changing_token_used_by_running_tool() -> None:
    platform = RenewingGrantPlatform()
    manager = RunGrantManager(
        platform,  # type: ignore[arg-type]
        PlatformRequestIdentity("usr_owner", "a" * 64),
        "task_12345678",
        "run_12345678",
        ["codex"],
        refresh_margin_seconds=0.12,
        minimum_refresh_interval_seconds=0.01,
    )

    issued = await manager.get()
    await asyncio.sleep(0.08)
    renewed = await manager.get()
    await manager.revoke()

    assert platform.refresh_calls >= 1
    assert renewed["grant"] == issued["grant"] == "wfg_stable_token"
    assert renewed["grantId"] == issued["grantId"] == "wfgrantid_stable"
    assert platform.revoked is True


class RepairRunner:
    def __init__(self) -> None:
        self.operation_keys: list[str] = []
        self.payloads: list[dict] = []

    async def analyze(self, task_id, analyzer_id, payload):  # type: ignore[no-untyped-def]
        self.operation_keys.append(payload["operationKey"])
        self.payloads.append(payload)
        if len(self.operation_keys) == 1:
            return {"result": {"summary": "schema-invalid"}}
        return {
            "result": {
                "summary": "ok",
                "impactedFeatures": [],
                "crossRepositoryImpacts": [],
                "risks": [],
                "codeEvidence": [],
                "recommendedRegressionTests": [],
                "uncertainties": [],
            }
        }


class StableGrant:
    async def get(self):  # type: ignore[no-untyped-def]
        return {"grant": "wfg_test", "gatewayUrl": "http://model.test/v1"}

    async def refresh(self):  # type: ignore[no-untyped-def]
        return await self.get()


@pytest.mark.asyncio
async def test_structured_output_repair_uses_a_distinct_idempotency_key() -> None:
    runner = RepairRunner()
    adapter = RunnerAnalyzerAdapter(  # type: ignore[arg-type]
        runner,
        StableGrant(),
        model_name="workflow-code-analysis",
    )
    state = {
        "task_id": "task_12345678",
        "run_id": "run_12345678",
        "input_data": {"scopeSelectors": []},
    }

    outcome = await adapter.analyze(
        "codex",
        state,  # type: ignore[arg-type]
        [
            FrozenRepository(
                "repo_12345678",
                "orders",
                "main",
                "a" * 40,
                "feature/a",
                "b" * 40,
                "a" * 40,
            )
        ],
        DiffManifest([], {}, []),
        "run_12345678:analyze:codex",
    )

    assert outcome.succeeded is True
    assert runner.operation_keys == [
        "run_12345678:analyze:codex",
        "run_12345678:analyze:codex:repair",
    ]
    assert [payload["modelName"] for payload in runner.payloads] == [
        "workflow-code-analysis",
        "workflow-code-analysis",
    ]
