from __future__ import annotations

import asyncio
from datetime import UTC, datetime, timedelta

import pytest
from pydantic import ValidationError

from testagent_workflow.application import ConversationApplicationService, MessageSubmission
from testagent_workflow.models import AnalysisMode, ImpactAnalysisInput, RepositorySelection, RunStatus
from testagent_workflow.registry import (
    IntentDecision,
    RegisteredIntentRouter,
    WorkflowRegistry,
)
from testagent_workflow.store import (
    ActiveRunConflict,
    InMemoryWorkflowStore,
    WorkspaceNotReusable,
)
from testagent_workflow.workflows.impact_analysis import impact_analysis_definition


@pytest.mark.asyncio
async def test_message_client_request_id_is_idempotent() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "代码影响分析")

    first = await store.append_message(
        conversation.id,
        "usr_owner",
        role="user",
        content="分析 feature-a 的影响",
        client_request_id="req_1234567890abcdef",
    )
    replay = await store.append_message(
        conversation.id,
        "usr_owner",
        role="user",
        content="这段内容不应覆盖首请求",
        client_request_id="req_1234567890abcdef",
    )

    assert replay.id == first.id
    assert replay.content == "分析 feature-a 的影响"
    assert len(await store.list_messages(conversation.id, "usr_owner")) == 1


@pytest.mark.asyncio
async def test_conversation_rejects_a_second_active_run() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "代码影响分析")
    input_data = ImpactAnalysisInput(
        repositories=[RepositorySelection(repository_id="repo_1234567890abcdef", target_branch="feature-a")],
        mode=AnalysisMode.SINGLE,
        analyzer_ids=["codex"],
    )

    first = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data=input_data.model_dump(mode="json"),
    )

    with pytest.raises(ActiveRunConflict, match="已有正在执行的任务"):
        await store.create_run(
            conversation.id,
            "usr_owner",
            workflow_id="code-change-impact-analysis",
            workflow_version="1.0.0",
            input_data=input_data.model_dump(mode="json"),
        )

    await store.update_run_status(first.id, RunStatus.SUCCEEDED)
    second = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data=input_data.model_dump(mode="json"),
    )
    assert second.id != first.id


def test_impact_input_rejects_unknown_analyzer_and_single_agent_review() -> None:
    base = {
        "repositories": [
            {"repositoryId": "repo_12345678", "targetBranch": "feature-a"}
        ],
        "mode": "REVIEW",
    }

    with pytest.raises(ValidationError, match="复核模式至少选择两个"):
        ImpactAnalysisInput.model_validate({**base, "analyzerIds": ["codex"]})
    with pytest.raises(ValidationError, match="未注册的代码智能体"):
        ImpactAnalysisInput.model_validate(
            {**base, "analyzerIds": ["codex", "unregistered-agent"]}
        )


@pytest.mark.asyncio
async def test_only_owner_or_super_admin_can_read_conversation() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "代码影响分析")

    assert (await store.get_conversation(conversation.id, "usr_owner")).id == conversation.id
    assert (
        await store.get_conversation(conversation.id, "usr_admin", is_super_admin=True)
    ).id == conversation.id

    with pytest.raises(PermissionError, match="无权访问该对话"):
        await store.get_conversation(conversation.id, "usr_other")


@pytest.mark.asyncio
async def test_partial_reanalysis_reuses_task_and_frozen_initial_input() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "局部重分析")
    initial = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={
            "repositories": [{"repositoryId": "repo_12345678", "targetBranch": "feature-a"}],
            "mode": "SINGLE",
            "analyzerIds": ["codex"],
        },
        session_digest="a" * 64,
    )
    await store.update_run_status(initial.id, RunStatus.SUCCEEDED)
    await store.upsert_workspace_lease(
        initial.task_id,
        runner_id="runner-a",
        container_id="container-a",
        image_digest="analysis@sha256:" + "a" * 64,
        status="STOPPED_RETAINED",
        expires_at=datetime.now(UTC) + timedelta(hours=48),
        metadata={"runId": initial.id},
    )

    rerun = await store.create_reanalysis_run(
        conversation.id,
        "usr_owner",
        scope_selectors=[
            {"repositoryId": "repo_12345678", "kind": "SYMBOL", "value": "OrderService"}
        ],
        session_digest="b" * 64,
    )

    assert rerun.task_id == initial.task_id
    assert rerun.id != initial.id
    assert rerun.input_data["repositories"] == initial.input_data["repositories"]
    assert rerun.input_data["scopeSelectors"][0]["value"] == "OrderService"
    assert rerun.session_digest == "b" * 64


@pytest.mark.asyncio
async def test_partial_reanalysis_rejects_an_expired_workspace() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "过期重分析")
    initial = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={},
    )
    await store.update_run_status(initial.id, RunStatus.SUCCEEDED)
    await store.upsert_workspace_lease(
        initial.task_id,
        runner_id="runner-a",
        container_id=None,
        image_digest="analysis@sha256:" + "a" * 64,
        status="STOPPED_RETAINED",
        expires_at=datetime.now(UTC) - timedelta(seconds=1),
        metadata={"runId": initial.id},
    )

    with pytest.raises(WorkspaceNotReusable, match="创建新的分析任务"):
        await store.create_reanalysis_run(
            conversation.id,
            "usr_owner",
            scope_selectors=[{"kind": "FILE", "value": "src/App.java"}],
            session_digest="b" * 64,
        )

    assert (await store.get_workspace_lease(initial.task_id))["status"] == "EXPIRED"


@pytest.mark.asyncio
async def test_waiting_run_is_requeued_in_place_with_a_fresh_checkpoint_namespace() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "范围消歧")
    run = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={"scopeSelectors": [{"kind": "SYMBOL", "value": "OrderService"}]},
        session_digest="a" * 64,
    )
    await store.update_run_status(run.id, RunStatus.WAITING_INPUT)

    resumed = await store.resume_waiting_run(
        conversation.id,
        "usr_owner",
        input_patch={
            "scopeSelectors": [
                {
                    "repositoryId": "repo_12345678",
                    "kind": "FILE",
                    "value": "src/orders/OrderService.java",
                }
            ]
        },
        session_digest="b" * 64,
    )

    assert resumed.id == run.id
    assert resumed.status is RunStatus.QUEUED
    assert resumed.checkpoint_namespace != run.checkpoint_namespace
    assert resumed.input_data["scopeSelectors"][0]["kind"] == "FILE"


@pytest.mark.asyncio
async def test_waiting_baseline_run_accepts_repository_patch_without_new_task() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "基线选择")
    run = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={
            "repositories": [
                {"repositoryId": "repo_12345678", "targetBranch": "feature-a"}
            ],
            "mode": "SINGLE",
            "analyzerIds": ["codex"],
        },
    )
    await store.update_run_status(run.id, RunStatus.WAITING_INPUT)

    resumed = await store.resume_waiting_run(
        conversation.id,
        "usr_owner",
        input_patch={
            "repositories": [
                {
                    "repositoryId": "repo_12345678",
                    "targetBranch": "feature-a",
                    "baselineBranch": "develop",
                }
            ]
        },
        session_digest="c" * 64,
    )

    assert resumed.id == run.id
    assert resumed.task_id == run.task_id
    assert resumed.input_data["repositories"][0]["baselineBranch"] == "develop"


@pytest.mark.asyncio
async def test_concurrent_duplicate_submission_has_one_deterministic_run_and_result() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "并发幂等")
    registry = WorkflowRegistry([impact_analysis_definition()])

    async def slow_classifier(message, allowed, context):  # type: ignore[no-untyped-def]
        await asyncio.sleep(0.03)
        return IntentDecision(
            "code-change-impact-analysis",
            0.99,
            {},
            (),
        )

    service = ConversationApplicationService(
        store,
        registry,
        RegisteredIntentRouter(registry, slow_classifier),
    )
    submission = MessageSubmission(
        client_request_id="req_concurrent_123456",
        text="分析 feature/a",
        structured_input={
            "repositories": [
                {"repositoryId": "repo_12345678", "targetBranch": "feature/a"}
            ],
            "mode": "SINGLE",
            "analyzerIds": ["codex"],
        },
    )

    first, duplicate = await asyncio.gather(
        service.submit(conversation.id, "usr_owner", submission),
        service.submit(conversation.id, "usr_owner", submission),
    )

    assert duplicate == first
    assert len(store._runs) == 1  # noqa: SLF001 - 锁定跨请求副作用幂等语义
    events = await store.list_events(conversation.id)
    assert sum(event.type.value == "RUN_STARTED" for event in events) == 1


@pytest.mark.asyncio
async def test_duplicate_request_lookup_cannot_bypass_conversation_ownership() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "幂等越权")
    registry = WorkflowRegistry([impact_analysis_definition()])

    async def classifier(message, allowed, context):  # type: ignore[no-untyped-def]
        del message, allowed, context
        return IntentDecision("code-change-impact-analysis", 0.99, {}, ())

    service = ConversationApplicationService(
        store,
        registry,
        RegisteredIntentRouter(registry, classifier),
    )
    submission = MessageSubmission(
        "req_cross_owner_123456",
        "开始分析",
        {
            "repositories": [
                {"repositoryId": "repo_12345678", "targetBranch": "feature/a"}
            ],
            "mode": "SINGLE",
            "analyzerIds": ["codex"],
        },
    )
    await service.submit(conversation.id, "usr_owner", submission)

    with pytest.raises(PermissionError, match="无权访问该对话"):
        await service.submit(conversation.id, "usr_attacker", submission)


@pytest.mark.asyncio
async def test_complete_agentscope_slots_start_without_a_second_confirmation() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "自然语言完整输入")
    registry = WorkflowRegistry([impact_analysis_definition()])

    async def classifier(message, allowed, context):  # type: ignore[no-untyped-def]
        del message, allowed, context
        return IntentDecision(
            "code-change-impact-analysis",
            0.98,
            {
                "repositories": [
                    {"repositoryId": "repo_12345678", "targetBranch": "feature/a"}
                ],
                "mode": "SINGLE",
                "analyzerIds": ["codex"],
            },
            (),
        )

    result = await ConversationApplicationService(
        store,
        registry,
        RegisteredIntentRouter(registry, classifier),
    ).submit(
        conversation.id,
        "usr_owner",
        MessageSubmission("req_natural_complete", "用 Codex 分析 feature/a", None),
    )

    assert result.status == "QUEUED"
    assert result.required_input == ()


@pytest.mark.asyncio
async def test_natural_follow_up_can_start_local_reanalysis_instead_of_report_qa() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "自然语言局部重分析")
    initial = await store.create_run(
        conversation.id,
        "usr_owner",
        workflow_id="code-change-impact-analysis",
        workflow_version="1.0.0",
        input_data={
            "repositories": [
                {"repositoryId": "repo_12345678", "targetBranch": "feature/a"}
            ],
            "mode": "SINGLE",
            "analyzerIds": ["codex"],
        },
    )
    await store.update_run_status(initial.id, RunStatus.SUCCEEDED)
    await store.publish_report(
        initial.task_id,
        initial.id,
        "usr_owner",
        {"changeOverview": {"summary": "initial"}},
        "# initial",
    )
    await store.upsert_workspace_lease(
        initial.task_id,
        runner_id="runner-a",
        container_id="container-a",
        image_digest="analysis@sha256:" + "a" * 64,
        status="STOPPED_RETAINED",
        expires_at=datetime.now(UTC) + timedelta(hours=48),
        metadata={"runId": initial.id},
    )
    registry = WorkflowRegistry([impact_analysis_definition()])

    async def classifier(message, allowed, context):  # type: ignore[no-untyped-def]
        del message, allowed, context
        return IntentDecision(
            "code-change-impact-analysis",
            0.99,
            {
                "interactionType": "REANALYSIS",
                "scopeSelectors": [
                    {
                        "repositoryId": "repo_12345678",
                        "kind": "SYMBOL",
                        "value": "OrderService",
                    }
                ],
            },
            (),
        )

    class MustNotAnswer:
        async def answer(self, question, report, context):  # type: ignore[no-untyped-def]
            del question, report, context
            raise AssertionError("局部重分析请求不应降级为报告问答")

    result = await ConversationApplicationService(
        store,
        registry,
        RegisteredIntentRouter(registry, classifier),
        MustNotAnswer(),
    ).submit(
        conversation.id,
        "usr_owner",
        MessageSubmission("req_natural_reanalysis", "重新分析 OrderService", None),
    )

    assert result.task_id == initial.task_id
    assert result.run_id != initial.id
    assert result.status == "QUEUED"


@pytest.mark.asyncio
async def test_report_follow_up_rolls_workspace_retention_after_answering() -> None:
    store = InMemoryWorkflowStore()
    conversation = await store.create_conversation("usr_owner", "报告追问续期")
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
        {"changeOverview": {"summary": "initial"}},
        "# initial",
    )
    registry = WorkflowRegistry([impact_analysis_definition()])

    async def classifier(message, allowed, context):  # type: ignore[no-untyped-def]
        del message, allowed, context
        return IntentDecision(
            "code-change-impact-analysis",
            0.99,
            {"interactionType": "QUESTION"},
            (),
        )

    class Answerer:
        async def answer(self, question, selected_report, context):  # type: ignore[no-untyped-def]
            assert question == "为什么影响订单校验？"
            assert selected_report == report
            assert context.request_id.startswith("msg_")
            return "因为校验分支发生了变化。"

    class Retention:
        def __init__(self) -> None:
            self.calls = []

        async def roll(self, selected_report, context):  # type: ignore[no-untyped-def]
            self.calls.append((selected_report, context))

    retention = Retention()
    result = await ConversationApplicationService(
        store,
        registry,
        RegisteredIntentRouter(registry, classifier),
        Answerer(),
        retention,
    ).submit(
        conversation.id,
        "usr_owner",
        MessageSubmission("req_report_followup_123", "为什么影响订单校验？", None),
        session_digest="a" * 64,
    )

    assert result.task_id is None
    assert len(retention.calls) == 1
    assert retention.calls[0][0] == report
    assert retention.calls[0][1].session_digest == "a" * 64
