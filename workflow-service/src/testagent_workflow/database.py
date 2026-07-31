"""独立工作流数据库模型、迁移入口与PostgreSQL存储实现。"""

from __future__ import annotations

from dataclasses import asdict
from datetime import timedelta
import hashlib
from pathlib import Path
from typing import Any
from uuid import uuid4

from alembic import command
from alembic.config import Config
from sqlalchemy import (
    BigInteger,
    Boolean,
    Column,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    MetaData,
    String,
    Table,
    Text,
    UniqueConstraint,
    and_,
    func,
    or_,
    select,
    update,
)
from sqlalchemy.dialects.postgresql import JSONB, insert
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncEngine

from testagent_workflow.agui import AgUiEvent, AgUiEventType
from testagent_workflow.models import RunStatus, utc_now
from testagent_workflow.store import (
    ActiveRunConflict,
    Conversation,
    Message,
    ReportVersion,
    SubmissionResult,
    WorkflowRun,
    WorkspaceNotReusable,
)


metadata = MetaData()

conversations = Table(
    "conversations",
    metadata,
    Column("id", String(64), primary_key=True),
    Column("owner_user_id", String(128), nullable=False, index=True),
    Column("title", String(200), nullable=False),
    Column("created_at", DateTime(timezone=True), nullable=False),
    Column("updated_at", DateTime(timezone=True), nullable=False),
)

messages = Table(
    "messages",
    metadata,
    Column("id", String(64), primary_key=True),
    Column("conversation_id", String(64), ForeignKey("conversations.id", ondelete="CASCADE"), nullable=False),
    Column("role", String(32), nullable=False),
    Column("content", Text, nullable=False),
    Column("client_request_id", String(128)),
    Column("submission_result", JSONB),
    Column("created_at", DateTime(timezone=True), nullable=False),
    UniqueConstraint("conversation_id", "client_request_id", name="uq_messages_conversation_request"),
    Index("ix_messages_conversation_created", "conversation_id", "created_at"),
)

tasks = Table(
    "tasks",
    metadata,
    Column("id", String(64), primary_key=True),
    Column("conversation_id", String(64), ForeignKey("conversations.id", ondelete="CASCADE"), nullable=False),
    Column("owner_user_id", String(128), nullable=False),
    Column("session_digest", String(64)),
    Column("workflow_id", String(128), nullable=False),
    Column("workflow_version", String(32), nullable=False),
    Column("parent_task_id", String(64), ForeignKey("tasks.id")),
    Column("created_at", DateTime(timezone=True), nullable=False),
    Index("ix_tasks_owner_created", "owner_user_id", "created_at"),
)

task_repositories = Table(
    "task_repositories",
    metadata,
    Column("id", String(64), primary_key=True),
    Column("task_id", String(64), ForeignKey("tasks.id", ondelete="CASCADE"), nullable=False),
    Column("repository_id", String(128), nullable=False),
    Column("repository_alias", String(128), nullable=False),
    Column("default_branch", String(255)),
    Column("default_head", String(64)),
    Column("target_branch", String(255), nullable=False),
    Column("target_head", String(64)),
    Column("merge_base", String(64)),
    Column("checkout_ticket_id", String(128)),
    Column("metadata", JSONB, nullable=False),
    UniqueConstraint("task_id", "repository_id", name="uq_task_repositories_task_repository"),
)

runs = Table(
    "runs",
    metadata,
    Column("id", String(64), primary_key=True),
    Column("task_id", String(64), ForeignKey("tasks.id", ondelete="CASCADE"), nullable=False),
    Column("conversation_id", String(64), ForeignKey("conversations.id", ondelete="CASCADE"), nullable=False),
    Column("owner_user_id", String(128), nullable=False),
    Column("session_digest", String(64)),
    Column("workflow_id", String(128), nullable=False),
    Column("workflow_version", String(32), nullable=False),
    Column("run_kind", String(32), nullable=False),
    Column("checkpoint_namespace", String(128), nullable=False),
    Column("input_data", JSONB, nullable=False),
    Column("status", String(32), nullable=False),
    Column("cancel_requested", Boolean, nullable=False),
    Column("worker_id", String(128)),
    Column("lease_expires_at", DateTime(timezone=True)),
    Column("heartbeat_at", DateTime(timezone=True)),
    Column("created_at", DateTime(timezone=True), nullable=False),
    Column("updated_at", DateTime(timezone=True), nullable=False),
    Index("ix_runs_status_lease", "status", "lease_expires_at"),
    Index(
        "uq_runs_one_active_per_conversation",
        "conversation_id",
        unique=True,
        postgresql_where=Column("status").in_([status.value for status in RunStatus if status.active]),
    ),
)

durable_events = Table(
    "durable_events",
    metadata,
    Column("id", BigInteger, primary_key=True, autoincrement=True),
    Column("conversation_id", String(64), ForeignKey("conversations.id", ondelete="CASCADE"), nullable=False),
    Column("sequence", BigInteger, nullable=False),
    Column("event_type", String(64), nullable=False),
    Column("dedup_key", String(255)),
    Column("payload", JSONB, nullable=False),
    Column("created_at", DateTime(timezone=True), nullable=False),
    UniqueConstraint("conversation_id", "sequence", name="uq_durable_events_conversation_sequence"),
    UniqueConstraint("conversation_id", "dedup_key", name="uq_durable_events_conversation_dedup"),
)

analyzer_results = Table(
    "analyzer_results",
    metadata,
    Column("id", String(64), primary_key=True),
    Column("run_id", String(64), ForeignKey("runs.id", ondelete="CASCADE"), nullable=False),
    Column("analyzer_id", String(64), nullable=False),
    Column("status", String(32), nullable=False),
    Column("result", JSONB),
    Column("error_code", String(128)),
    Column("created_at", DateTime(timezone=True), nullable=False),
    UniqueConstraint("run_id", "analyzer_id", name="uq_analyzer_results_run_analyzer"),
)

node_operation_results = Table(
    "node_operation_results",
    metadata,
    Column("run_id", String(64), ForeignKey("runs.id", ondelete="CASCADE"), primary_key=True),
    Column("node_id", String(128), primary_key=True),
    Column("operation_key", String(255), primary_key=True),
    Column("result", JSONB, nullable=False),
    Column("created_at", DateTime(timezone=True), nullable=False),
    UniqueConstraint(
        "run_id",
        "node_id",
        "operation_key",
        name="uq_node_operation_results_scope",
    ),
)

report_versions = Table(
    "report_versions",
    metadata,
    Column("id", String(64), primary_key=True),
    Column("task_id", String(64), ForeignKey("tasks.id", ondelete="CASCADE"), nullable=False),
    Column("run_id", String(64), ForeignKey("runs.id", ondelete="CASCADE"), nullable=False),
    Column("version", Integer, nullable=False),
    Column("is_current", Boolean, nullable=False),
    Column("structured_report", JSONB, nullable=False),
    Column("markdown_report", Text, nullable=False),
    Column("created_at", DateTime(timezone=True), nullable=False),
    UniqueConstraint("task_id", "version", name="uq_report_versions_task_version"),
    UniqueConstraint("run_id", name="uq_report_versions_run"),
    Index("ix_report_versions_task_current", "task_id", "is_current"),
)

workspace_leases = Table(
    "workspace_leases",
    metadata,
    Column("task_id", String(64), ForeignKey("tasks.id", ondelete="CASCADE"), primary_key=True),
    Column("runner_id", String(128)),
    Column("container_id", String(128)),
    Column("image_digest", String(255)),
    Column("status", String(32), nullable=False),
    Column("expires_at", DateTime(timezone=True)),
    Column("last_activity_at", DateTime(timezone=True), nullable=False),
    Column("metadata", JSONB, nullable=False),
)

audit_logs = Table(
    "audit_logs",
    metadata,
    Column("id", BigInteger, primary_key=True, autoincrement=True),
    Column("actor_user_id", String(128), nullable=False),
    Column("action", String(128), nullable=False),
    Column("resource_type", String(64), nullable=False),
    Column("resource_id", String(128), nullable=False),
    Column("trace_id", String(128), nullable=False),
    Column("details", JSONB, nullable=False),
    Column("created_at", DateTime(timezone=True), nullable=False),
)

transactional_outbox = Table(
    "transactional_outbox",
    metadata,
    Column("id", BigInteger, primary_key=True, autoincrement=True),
    Column("aggregate_type", String(64), nullable=False),
    Column("aggregate_id", String(128), nullable=False),
    Column("event_type", String(128), nullable=False),
    Column("payload", JSONB, nullable=False),
    Column("available_at", DateTime(timezone=True), nullable=False),
    Column("published_at", DateTime(timezone=True)),
    Column("attempts", Integer, nullable=False),
    Index("ix_outbox_pending", "published_at", "available_at"),
)


def migrate_database(database_url: str) -> None:
    """显式执行Alembic；服务启动本身不会偷偷修改数据库。"""

    service_root = Path(__file__).resolve().parents[2]
    config = Config(str(service_root / "alembic.ini"))
    config.set_main_option("script_location", str(service_root / "migrations"))
    config.set_main_option("sqlalchemy.url", database_url.replace("%", "%%"))
    command.upgrade(config, "head")


class PostgresWorkflowStore:
    """以数据库唯一约束、行锁和租约承载跨进程一致性。"""

    def __init__(self, engine: AsyncEngine) -> None:
        self._engine = engine

    async def create_conversation(self, owner_user_id: str, title: str) -> Conversation:
        now = utc_now()
        conversation = Conversation(
            id=self._id("conv"),
            owner_user_id=owner_user_id,
            title=title.strip() or "新对话",
            created_at=now,
        )
        async with self._engine.begin() as connection:
            await connection.execute(
                insert(conversations).values(
                    id=conversation.id,
                    owner_user_id=conversation.owner_user_id,
                    title=conversation.title,
                    created_at=now,
                    updated_at=now,
                )
            )
        return conversation

    async def get_conversation(
        self,
        conversation_id: str,
        actor_user_id: str,
        *,
        is_super_admin: bool = False,
    ) -> Conversation:
        async with self._engine.connect() as connection:
            row = (await connection.execute(
                select(conversations).where(conversations.c.id == conversation_id)
            )).mappings().first()
        if row is None:
            raise KeyError("对话不存在")
        if row["owner_user_id"] != actor_user_id and not is_super_admin:
            raise PermissionError("无权访问该对话")
        return self._conversation(row)

    async def list_conversations(
        self,
        actor_user_id: str,
        *,
        owner_user_id: str | None = None,
        is_super_admin: bool = False,
    ) -> list[Conversation]:
        requested_owner = owner_user_id or actor_user_id
        if requested_owner != actor_user_id and not is_super_admin:
            raise PermissionError("无权查看其他用户的对话")
        async with self._engine.connect() as connection:
            rows = (await connection.execute(
                select(conversations)
                .where(conversations.c.owner_user_id == requested_owner)
                .order_by(conversations.c.created_at.desc())
            )).mappings().all()
        return [self._conversation(row) for row in rows]

    async def append_message(
        self,
        conversation_id: str,
        actor_user_id: str,
        *,
        role: str,
        content: str,
        client_request_id: str | None = None,
    ) -> Message:
        await self.get_conversation(conversation_id, actor_user_id)
        now = utc_now()
        message_id = self._id("msg")
        statement = insert(messages).values(
            id=message_id,
            conversation_id=conversation_id,
            role=role,
            content=content,
            client_request_id=client_request_id,
            created_at=now,
        )
        if client_request_id is not None:
            statement = statement.on_conflict_do_nothing(
                constraint="uq_messages_conversation_request"
            )
        statement = statement.returning(messages)
        async with self._engine.begin() as connection:
            row = (await connection.execute(statement)).mappings().first()
            if row is None:
                row = (await connection.execute(
                    select(messages).where(
                        messages.c.conversation_id == conversation_id,
                        messages.c.client_request_id == client_request_id,
                    )
                )).mappings().one()
        return self._message(row)

    async def list_messages(
        self,
        conversation_id: str,
        actor_user_id: str,
        *,
        is_super_admin: bool = False,
    ) -> list[Message]:
        await self.get_conversation(conversation_id, actor_user_id, is_super_admin=is_super_admin)
        async with self._engine.connect() as connection:
            rows = (await connection.execute(
                select(messages)
                .where(messages.c.conversation_id == conversation_id)
                .order_by(messages.c.created_at, messages.c.id)
            )).mappings().all()
        return [self._message(row) for row in rows]

    async def create_run(
        self,
        conversation_id: str,
        actor_user_id: str,
        *,
        workflow_id: str,
        workflow_version: str,
        input_data: dict[str, Any],
        session_digest: str = "",
        request_key: str | None = None,
    ) -> WorkflowRun:
        await self.get_conversation(conversation_id, actor_user_id)
        now = utc_now()
        task_id = self._stable_id("task", request_key) if request_key else self._id("task")
        run_id = self._stable_id("run", request_key) if request_key else self._id("run")
        try:
            async with self._engine.begin() as connection:
                existing = (await connection.execute(
                    select(runs).where(runs.c.id == run_id)
                )).mappings().first()
                if existing is not None:
                    return self._same_idempotent_run(
                        existing, conversation_id, actor_user_id
                    )
                await connection.execute(
                    insert(tasks)
                    .values(
                        id=task_id,
                        conversation_id=conversation_id,
                        owner_user_id=actor_user_id,
                        session_digest=session_digest or None,
                        workflow_id=workflow_id,
                        workflow_version=workflow_version,
                        created_at=now,
                    )
                    .on_conflict_do_nothing()
                )
                row = (await connection.execute(
                    insert(runs)
                    .values(
                        id=run_id,
                        task_id=task_id,
                        conversation_id=conversation_id,
                        owner_user_id=actor_user_id,
                        session_digest=session_digest or None,
                        workflow_id=workflow_id,
                        workflow_version=workflow_version,
                        run_kind="INITIAL",
                        checkpoint_namespace=run_id,
                        input_data=dict(input_data),
                        status=RunStatus.QUEUED.value,
                        cancel_requested=False,
                        created_at=now,
                        updated_at=now,
                    )
                    .on_conflict_do_nothing()
                    .returning(runs)
                )).mappings().first()
                if row is None:
                    row = (await connection.execute(
                        select(runs).where(runs.c.id == run_id)
                    )).mappings().first()
                    if row is None:
                        raise ActiveRunConflict("该对话已有正在执行的任务")
                    return self._same_idempotent_run(
                        row, conversation_id, actor_user_id
                    )
        except IntegrityError as exception:
            if "uq_runs_one_active_per_conversation" in str(exception):
                raise ActiveRunConflict("该对话已有正在执行的任务") from exception
            raise
        return self._run(row)

    async def create_reanalysis_run(
        self,
        conversation_id: str,
        actor_user_id: str,
        *,
        scope_selectors: list[dict[str, Any]],
        session_digest: str,
        request_key: str | None = None,
    ) -> WorkflowRun:
        await self.get_conversation(conversation_id, actor_user_id)
        now = utc_now()
        run_id = self._stable_id("run", request_key) if request_key else self._id("run")
        try:
            async with self._engine.begin() as connection:
                existing = (await connection.execute(
                    select(runs).where(runs.c.id == run_id)
                )).mappings().first()
                if existing is not None:
                    return self._same_idempotent_run(
                        existing, conversation_id, actor_user_id
                    )
                latest = (await connection.execute(
                    select(runs)
                    .where(runs.c.conversation_id == conversation_id)
                    .order_by(runs.c.created_at.desc())
                    .limit(1)
                    .with_for_update()
                )).mappings().first()
                if latest is None:
                    raise KeyError("没有可重分析的历史任务")
                initial = (await connection.execute(
                    select(runs)
                    .where(runs.c.task_id == latest["task_id"])
                    .order_by(runs.c.created_at)
                    .limit(1)
                )).mappings().one()
                workspace = (await connection.execute(
                    select(workspace_leases)
                    .where(workspace_leases.c.task_id == initial["task_id"])
                    .with_for_update()
                )).mappings().first()
                if (
                    workspace is None
                    or workspace["status"] != "STOPPED_RETAINED"
                    or workspace["expires_at"] is None
                    or workspace["expires_at"] <= now
                ):
                    raise WorkspaceNotReusable(
                        "历史源码工作区已过期，请创建新的分析任务"
                    )
                input_data = dict(initial["input_data"])
                input_data["scopeSelectors"] = list(scope_selectors)
                row = (await connection.execute(
                    insert(runs)
                    .values(
                        id=run_id,
                        task_id=initial["task_id"],
                        conversation_id=conversation_id,
                        owner_user_id=actor_user_id,
                        session_digest=session_digest or None,
                        workflow_id=initial["workflow_id"],
                        workflow_version=initial["workflow_version"],
                        run_kind="REANALYSIS",
                        checkpoint_namespace=run_id,
                        input_data=input_data,
                        status=RunStatus.QUEUED.value,
                        cancel_requested=False,
                        created_at=now,
                        updated_at=now,
                    )
                    .on_conflict_do_nothing()
                    .returning(runs)
                )).mappings().first()
                if row is None:
                    row = (await connection.execute(
                        select(runs).where(runs.c.id == run_id)
                    )).mappings().first()
                    if row is None:
                        raise ActiveRunConflict("该对话已有正在执行的任务")
                    return self._same_idempotent_run(
                        row, conversation_id, actor_user_id
                    )
        except IntegrityError as exception:
            if "uq_runs_one_active_per_conversation" in str(exception):
                raise ActiveRunConflict("该对话已有正在执行的任务") from exception
            raise
        return self._run(row)

    async def resume_waiting_run(
        self,
        conversation_id: str,
        actor_user_id: str,
        *,
        input_patch: dict[str, Any],
        session_digest: str,
        request_key: str | None = None,
    ) -> WorkflowRun:
        await self.get_conversation(conversation_id, actor_user_id)
        async with self._engine.begin() as connection:
            checkpoint_namespace = (
                self._stable_id("input", request_key) if request_key else None
            )
            if checkpoint_namespace is not None:
                replay = (await connection.execute(
                    select(runs).where(
                        runs.c.conversation_id == conversation_id,
                        runs.c.owner_user_id == actor_user_id,
                        runs.c.checkpoint_namespace == checkpoint_namespace,
                    )
                )).mappings().first()
                if replay is not None:
                    return self._run(replay)
            waiting = (await connection.execute(
                select(runs)
                .where(
                    runs.c.conversation_id == conversation_id,
                    runs.c.owner_user_id == actor_user_id,
                    runs.c.status == RunStatus.WAITING_INPUT.value,
                )
                .with_for_update()
            )).mappings().first()
            if waiting is None:
                raise KeyError("没有等待补充输入的运行")
            input_data = dict(waiting["input_data"])
            input_data.update(input_patch)
            row = (await connection.execute(
                update(runs)
                .where(
                    runs.c.id == waiting["id"],
                    runs.c.status == RunStatus.WAITING_INPUT.value,
                )
                .values(
                    input_data=input_data,
                    session_digest=session_digest or None,
                    status=RunStatus.QUEUED.value,
                    checkpoint_namespace=(
                        checkpoint_namespace
                        or f"{waiting['id']}-input-{uuid4().hex[:12]}"
                    ),
                    worker_id=None,
                    lease_expires_at=None,
                    heartbeat_at=None,
                    updated_at=utc_now(),
                )
                .returning(runs)
            )).mappings().first()
            if row is None:
                raise ActiveRunConflict("等待输入的运行状态已变化")
        return self._run(row)

    async def find_submission(
        self,
        conversation_id: str,
        client_request_id: str,
    ) -> SubmissionResult | None:
        async with self._engine.connect() as connection:
            value = (await connection.execute(
                select(messages.c.submission_result).where(
                    messages.c.conversation_id == conversation_id,
                    messages.c.client_request_id == client_request_id,
                )
            )).scalar_one_or_none()
        return self._submission(value) if value else None

    async def get_run(
        self,
        run_id: str,
        actor_user_id: str,
        *,
        is_super_admin: bool = False,
    ) -> WorkflowRun:
        async with self._engine.connect() as connection:
            row = (await connection.execute(
                select(runs).where(runs.c.id == run_id)
            )).mappings().first()
        if row is None:
            raise KeyError("运行不存在")
        if row["owner_user_id"] != actor_user_id and not is_super_admin:
            raise PermissionError("无权访问该运行")
        return self._run(row)

    async def get_latest_run_for_conversation(
        self,
        conversation_id: str,
        actor_user_id: str,
        *,
        is_super_admin: bool = False,
    ) -> WorkflowRun:
        await self.get_conversation(
            conversation_id,
            actor_user_id,
            is_super_admin=is_super_admin,
        )
        async with self._engine.connect() as connection:
            row = (await connection.execute(
                select(runs)
                .where(runs.c.conversation_id == conversation_id)
                .order_by(runs.c.created_at.desc())
                .limit(1)
            )).mappings().first()
        if row is None:
            raise KeyError("运行不存在")
        return self._run(row)

    async def request_cancel(self, run_id: str, actor_user_id: str) -> WorkflowRun:
        await self.get_run(run_id, actor_user_id)
        async with self._engine.begin() as connection:
            row = (await connection.execute(
                update(runs)
                .where(runs.c.id == run_id)
                .values(
                    cancel_requested=True,
                    status=RunStatus.CANCELED.value,
                    lease_expires_at=None,
                    updated_at=utc_now(),
                )
                .returning(runs)
            )).mappings().one()
        return self._run(row)

    async def save_submission(
        self,
        conversation_id: str,
        client_request_id: str,
        result: SubmissionResult,
    ) -> SubmissionResult:
        payload = asdict(result)
        payload["required_input"] = list(result.required_input)
        async with self._engine.begin() as connection:
            await connection.execute(
                update(messages)
                .where(
                    messages.c.conversation_id == conversation_id,
                    messages.c.client_request_id == client_request_id,
                    messages.c.submission_result.is_(None),
                )
                .values(submission_result=payload)
            )
            stored = (await connection.execute(
                select(messages.c.submission_result).where(
                    messages.c.conversation_id == conversation_id,
                    messages.c.client_request_id == client_request_id,
                )
            )).scalar_one()
        return self._submission(stored)

    async def update_run_status(self, run_id: str, status: RunStatus) -> WorkflowRun:
        async with self._engine.begin() as connection:
            row = (await connection.execute(
                update(runs)
                .where(runs.c.id == run_id)
                .values(status=status.value, updated_at=utc_now())
                .returning(runs)
            )).mappings().one()
        return self._run(row)

    async def append_event(
        self,
        conversation_id: str,
        event_type: AgUiEventType,
        payload: dict[str, Any],
        *,
        dedup_key: str | None = None,
    ) -> AgUiEvent:
        if dedup_key is not None and (not dedup_key or len(dedup_key) > 255):
            raise ValueError("durable event dedup_key长度必须为1到255")
        now = utc_now()
        async with self._engine.begin() as connection:
            # 锁住会话行，以低成本保证每个会话的事件序号严格单调。
            exists = (await connection.execute(
                select(conversations.c.id)
                .where(conversations.c.id == conversation_id)
                .with_for_update()
            )).scalar_one_or_none()
            if exists is None:
                raise KeyError("对话不存在")
            if dedup_key is not None:
                existing = (await connection.execute(
                    select(durable_events).where(
                        durable_events.c.conversation_id == conversation_id,
                        durable_events.c.dedup_key == dedup_key,
                    )
                )).mappings().first()
                if existing is not None:
                    return self._event(existing)
            sequence = (await connection.execute(
                select(func.coalesce(func.max(durable_events.c.sequence), 0) + 1).where(
                    durable_events.c.conversation_id == conversation_id
                )
            )).scalar_one()
            await connection.execute(insert(durable_events).values(
                conversation_id=conversation_id,
                sequence=sequence,
                event_type=event_type.value,
                dedup_key=dedup_key,
                payload=dict(payload),
                created_at=now,
            ))
            await connection.execute(insert(transactional_outbox).values(
                aggregate_type="conversation",
                aggregate_id=conversation_id,
                event_type=event_type.value,
                payload={
                    "conversationId": conversation_id,
                    "sequence": int(sequence),
                    "payload": dict(payload),
                },
                available_at=now,
                attempts=0,
            ))
        return AgUiEvent(conversation_id, event_type, dict(payload), int(sequence))

    async def list_events(self, conversation_id: str, *, after_seq: int = 0) -> list[AgUiEvent]:
        async with self._engine.connect() as connection:
            rows = (await connection.execute(
                select(durable_events)
                .where(
                    durable_events.c.conversation_id == conversation_id,
                    durable_events.c.sequence > after_seq,
                )
                .order_by(durable_events.c.sequence)
            )).mappings().all()
        return [self._event(row) for row in rows]

    async def publish_report(
        self,
        task_id: str,
        run_id: str,
        owner_user_id: str,
        structured_report: dict[str, Any],
        markdown_report: str,
    ) -> ReportVersion:
        now = utc_now()
        async with self._engine.begin() as connection:
            task = (await connection.execute(
                select(tasks.c.id, tasks.c.owner_user_id)
                .where(tasks.c.id == task_id)
                .with_for_update()
            )).mappings().first()
            if task is None or task["owner_user_id"] != owner_user_id:
                raise KeyError("任务不存在")
            existing = (await connection.execute(
                select(report_versions).where(report_versions.c.run_id == run_id)
            )).mappings().first()
            if existing is not None:
                return self._report(existing)
            version = (await connection.execute(
                select(func.coalesce(func.max(report_versions.c.version), 0) + 1).where(
                    report_versions.c.task_id == task_id
                )
            )).scalar_one()
            await connection.execute(
                update(report_versions)
                .where(report_versions.c.task_id == task_id)
                .values(is_current=False)
            )
            row = (await connection.execute(insert(report_versions).values(
                id=self._id("report"),
                task_id=task_id,
                run_id=run_id,
                version=version,
                is_current=True,
                structured_report=dict(structured_report),
                markdown_report=markdown_report,
                created_at=now,
            ).returning(report_versions))).mappings().one()
        return self._report(row)

    async def list_reports(
        self,
        task_id: str,
        actor_user_id: str,
        *,
        is_super_admin: bool = False,
    ) -> list[ReportVersion]:
        async with self._engine.connect() as connection:
            owner = (await connection.execute(
                select(tasks.c.owner_user_id).where(tasks.c.id == task_id)
            )).scalar_one_or_none()
            if owner is None:
                raise KeyError("任务不存在")
            if owner != actor_user_id and not is_super_admin:
                raise PermissionError("无权访问该报告")
            rows = (await connection.execute(
                select(report_versions)
                .where(report_versions.c.task_id == task_id)
                .order_by(report_versions.c.version.desc())
            )).mappings().all()
        return [self._report(row) for row in rows]

    async def get_report(
        self,
        report_id: str,
        actor_user_id: str,
        *,
        is_super_admin: bool = False,
    ) -> ReportVersion:
        async with self._engine.connect() as connection:
            row = (await connection.execute(
                select(report_versions, tasks.c.owner_user_id)
                .join(tasks, tasks.c.id == report_versions.c.task_id)
                .where(report_versions.c.id == report_id)
            )).mappings().first()
        if row is None:
            raise KeyError("报告不存在")
        if row["owner_user_id"] != actor_user_id and not is_super_admin:
            raise PermissionError("无权访问该报告")
        return self._report(row)

    async def get_current_report(
        self,
        task_id: str,
        actor_user_id: str,
        *,
        is_super_admin: bool = False,
    ) -> ReportVersion:
        async with self._engine.connect() as connection:
            row = (await connection.execute(
                select(report_versions, tasks.c.owner_user_id)
                .join(tasks, tasks.c.id == report_versions.c.task_id)
                .where(
                    report_versions.c.task_id == task_id,
                    report_versions.c.is_current.is_(True),
                )
            )).mappings().first()
        if row is None:
            raise KeyError("当前报告不存在")
        if row["owner_user_id"] != actor_user_id and not is_super_admin:
            raise PermissionError("无权访问该报告")
        return self._report(row)

    async def get_current_report_for_conversation(
        self,
        conversation_id: str,
        actor_user_id: str,
        *,
        is_super_admin: bool = False,
    ) -> ReportVersion:
        conversation = await self.get_conversation(
            conversation_id,
            actor_user_id,
            is_super_admin=is_super_admin,
        )
        async with self._engine.connect() as connection:
            row = (await connection.execute(
                select(report_versions)
                .join(tasks, tasks.c.id == report_versions.c.task_id)
                .where(
                    tasks.c.conversation_id == conversation.id,
                    report_versions.c.is_current.is_(True),
                )
                .order_by(report_versions.c.created_at.desc())
                .limit(1)
            )).mappings().first()
        if row is None:
            raise KeyError("当前报告不存在")
        return self._report(row)

    async def save_task_repositories(self, task_id: str, values: list[Any]) -> None:
        async with self._engine.begin() as connection:
            await connection.execute(
                select(tasks.c.id).where(tasks.c.id == task_id).with_for_update()
            )
            existing = (await connection.execute(
                select(task_repositories)
                .where(task_repositories.c.task_id == task_id)
                .order_by(task_repositories.c.repository_alias)
            )).mappings().all()
            if existing:
                old_coordinates = sorted(
                    (
                        row["repository_id"],
                        row["default_head"],
                        row["target_head"],
                        row["merge_base"],
                    )
                    for row in existing
                )
                new_coordinates = sorted(
                    (
                        value.repository_id,
                        value.default_head,
                        value.target_head,
                        value.merge_base,
                    )
                    for value in values
                )
                if old_coordinates != new_coordinates:
                    raise RuntimeError("冻结仓库坐标不可变")
                return
            for value in values:
                await connection.execute(insert(task_repositories).values(
                    id=self._id("taskrepo"),
                    task_id=task_id,
                    repository_id=value.repository_id,
                    repository_alias=value.alias,
                    default_branch=value.default_branch,
                    default_head=value.default_head,
                    target_branch=value.target_branch,
                    target_head=value.target_head,
                    merge_base=value.merge_base,
                    metadata={"missingDependencies": list(value.missing_dependencies)},
                ))

    async def save_analyzer_results(self, run_id: str, values: list[Any]) -> None:
        async with self._engine.begin() as connection:
            for value in values:
                payload = value.to_dict()
                await connection.execute(
                    insert(analyzer_results)
                    .values(
                        id=self._id("analyzer"),
                        run_id=run_id,
                        analyzer_id=payload["analyzerId"],
                        status="SUCCEEDED" if payload["succeeded"] else "FAILED",
                        result=payload["result"],
                        error_code=payload["errorCode"],
                        created_at=utc_now(),
                    )
                    .on_conflict_do_nothing(constraint="uq_analyzer_results_run_analyzer")
                )

    async def list_analyzer_results(self, run_id: str) -> list[dict[str, Any]]:
        async with self._engine.connect() as connection:
            rows = (await connection.execute(
                select(analyzer_results)
                .where(analyzer_results.c.run_id == run_id)
                .order_by(analyzer_results.c.analyzer_id)
            )).mappings().all()
        return [
            {
                "analyzerId": row["analyzer_id"],
                "succeeded": row["status"] == "SUCCEEDED",
                "result": dict(row["result"]) if row["result"] is not None else None,
                "errorCode": row["error_code"],
            }
            for row in rows
        ]

    async def get_node_operation_result(
        self,
        run_id: str,
        node_id: str,
        operation_key: str,
    ) -> dict[str, Any] | None:
        self._validate_operation_scope(run_id, node_id, operation_key)
        async with self._engine.connect() as connection:
            value = (await connection.execute(
                select(node_operation_results.c.result).where(
                    node_operation_results.c.run_id == run_id,
                    node_operation_results.c.node_id == node_id,
                    node_operation_results.c.operation_key == operation_key,
                )
            )).scalar_one_or_none()
        return dict(value) if value is not None else None

    async def save_node_operation_result(
        self,
        run_id: str,
        node_id: str,
        operation_key: str,
        result: dict[str, Any],
    ) -> dict[str, Any]:
        """首次成功结果不可变；checkpoint重放只读取同一节点的原始结果。"""

        self._validate_operation_scope(run_id, node_id, operation_key)
        statement = (
            insert(node_operation_results)
            .values(
                run_id=run_id,
                node_id=node_id,
                operation_key=operation_key,
                result=dict(result),
                created_at=utc_now(),
            )
            .on_conflict_do_nothing(constraint="uq_node_operation_results_scope")
            .returning(node_operation_results.c.result)
        )
        async with self._engine.begin() as connection:
            stored = (await connection.execute(statement)).scalar_one_or_none()
            if stored is None:
                stored = (await connection.execute(
                    select(node_operation_results.c.result).where(
                        node_operation_results.c.run_id == run_id,
                        node_operation_results.c.node_id == node_id,
                        node_operation_results.c.operation_key == operation_key,
                    )
                )).scalar_one()
        return dict(stored)

    async def upsert_workspace_lease(
        self,
        task_id: str,
        *,
        runner_id: str,
        container_id: str | None,
        image_digest: str | None,
        status: str,
        expires_at: Any,
        metadata: dict[str, Any],
    ) -> None:
        now = utc_now()
        statement = insert(workspace_leases).values(
            task_id=task_id,
            runner_id=runner_id,
            container_id=container_id,
            image_digest=image_digest,
            status=status,
            expires_at=expires_at,
            last_activity_at=now,
            metadata=dict(metadata),
        )
        statement = statement.on_conflict_do_update(
            index_elements=[workspace_leases.c.task_id],
            set_={
                "runner_id": statement.excluded.runner_id,
                "container_id": statement.excluded.container_id,
                "image_digest": statement.excluded.image_digest,
                "status": statement.excluded.status,
                "expires_at": statement.excluded.expires_at,
                "last_activity_at": statement.excluded.last_activity_at,
                "metadata": statement.excluded.metadata,
            },
        )
        async with self._engine.begin() as connection:
            await connection.execute(statement)

    async def get_workspace_lease(self, task_id: str) -> dict[str, Any]:
        async with self._engine.connect() as connection:
            row = (await connection.execute(
                select(workspace_leases).where(workspace_leases.c.task_id == task_id)
            )).mappings().first()
        if row is None:
            raise KeyError("工作区租约不存在")
        status = row["status"]
        if status != "CLEANUP_FAILED" and (
            status == "EXPIRED"
            or (row["expires_at"] is not None and row["expires_at"] <= utc_now())
        ):
            status = "EXPIRED"
        return {
            "taskId": row["task_id"],
            "runnerId": row["runner_id"],
            "containerId": row["container_id"],
            "imageDigest": row["image_digest"],
            "status": status,
            "expiresAt": row["expires_at"],
            "lastActivityAt": row["last_activity_at"],
            "metadata": dict(row["metadata"]),
        }

    async def list_workspace_leases_due_for_cleanup(
        self,
        *,
        limit: int = 100,
    ) -> list[dict[str, Any]]:
        if not 1 <= limit <= 1000:
            raise ValueError("工作区清理批次必须位于1到1000之间")
        async with self._engine.connect() as connection:
            rows = (await connection.execute(
                select(workspace_leases)
                .where(
                    workspace_leases.c.status.in_(["STOPPED_RETAINED", "CLEANUP_FAILED"]),
                    workspace_leases.c.expires_at.is_not(None),
                    workspace_leases.c.expires_at <= utc_now(),
                )
                .order_by(workspace_leases.c.expires_at)
                .limit(limit)
            )).mappings().all()
        return [
            {
                "taskId": row["task_id"],
                "runnerId": row["runner_id"],
                "containerId": row["container_id"],
                "imageDigest": row["image_digest"],
                "status": row["status"],
                "expiresAt": row["expires_at"],
                "lastActivityAt": row["last_activity_at"],
                "metadata": dict(row["metadata"]),
            }
            for row in rows
        ]

    async def set_workspace_cleanup_status(self, task_id: str, status: str) -> None:
        if status not in {"EXPIRED", "CLEANUP_FAILED"}:
            raise ValueError("工作区清理状态无效")
        values: dict[str, Any] = {"status": status, "last_activity_at": utc_now()}
        if status == "EXPIRED":
            values["container_id"] = None
        async with self._engine.begin() as connection:
            result = await connection.execute(
                update(workspace_leases)
                .where(workspace_leases.c.task_id == task_id)
                .values(**values)
            )
        if result.rowcount != 1:
            raise KeyError("工作区租约不存在")

    async def append_audit_log(
        self,
        *,
        actor_user_id: str,
        action: str,
        resource_type: str,
        resource_id: str,
        trace_id: str,
        details: dict[str, Any],
    ) -> None:
        async with self._engine.begin() as connection:
            await connection.execute(insert(audit_logs).values(
                actor_user_id=actor_user_id,
                action=action,
                resource_type=resource_type,
                resource_id=resource_id,
                trace_id=trace_id,
                details=dict(details),
                created_at=utc_now(),
            ))

    async def list_audit_logs(self) -> list[dict[str, Any]]:
        async with self._engine.connect() as connection:
            rows = (await connection.execute(
                select(audit_logs).order_by(audit_logs.c.id)
            )).mappings().all()
        return [
            {
                "actorUserId": row["actor_user_id"],
                "action": row["action"],
                "resourceType": row["resource_type"],
                "resourceId": row["resource_id"],
                "traceId": row["trace_id"],
                "details": dict(row["details"]),
                "createdAt": row["created_at"].isoformat(),
            }
            for row in rows
        ]

    async def claim_next_run(self, worker_id: str, *, lease_seconds: int) -> WorkflowRun | None:
        now = utc_now()
        async with self._engine.begin() as connection:
            candidate = (await connection.execute(
                select(runs.c.id)
                .where(
                    runs.c.cancel_requested.is_(False),
                    or_(
                        runs.c.status == RunStatus.QUEUED.value,
                        and_(
                            runs.c.status == RunStatus.RUNNING.value,
                            runs.c.lease_expires_at < now,
                        ),
                    ),
                )
                .order_by(runs.c.created_at)
                .with_for_update(skip_locked=True)
                .limit(1)
            )).scalar_one_or_none()
            if candidate is None:
                return None
            row = (await connection.execute(
                update(runs)
                .where(runs.c.id == candidate)
                .values(
                    status=RunStatus.RUNNING.value,
                    worker_id=worker_id,
                    heartbeat_at=now,
                    lease_expires_at=now + timedelta(seconds=lease_seconds),
                    updated_at=now,
                )
                .returning(runs)
            )).mappings().one()
        return self._run(row)

    async def renew_run_lease(
        self,
        run_id: str,
        worker_id: str,
        *,
        lease_seconds: int,
    ) -> bool:
        now = utc_now()
        async with self._engine.begin() as connection:
            result = await connection.execute(
                update(runs)
                .where(
                    runs.c.id == run_id,
                    runs.c.worker_id == worker_id,
                    runs.c.status == RunStatus.RUNNING.value,
                    runs.c.cancel_requested.is_(False),
                )
                .values(
                    heartbeat_at=now,
                    lease_expires_at=now + timedelta(seconds=lease_seconds),
                    updated_at=now,
                )
            )
        return result.rowcount == 1

    @staticmethod
    def _conversation(row: Any) -> Conversation:
        return Conversation(row["id"], row["owner_user_id"], row["title"], row["created_at"])

    @staticmethod
    def _message(row: Any) -> Message:
        return Message(
            row["id"],
            row["conversation_id"],
            row["role"],
            row["content"],
            row["client_request_id"],
            row["created_at"],
        )

    @staticmethod
    def _run(row: Any) -> WorkflowRun:
        return WorkflowRun(
            id=row["id"],
            task_id=row["task_id"],
            conversation_id=row["conversation_id"],
            owner_user_id=row["owner_user_id"],
            workflow_id=row["workflow_id"],
            workflow_version=row["workflow_version"],
            input_data=dict(row["input_data"]),
            status=RunStatus(row["status"]),
            created_at=row["created_at"],
            updated_at=row["updated_at"],
            session_digest=row.get("session_digest") or "",
            run_kind=row.get("run_kind") or "INITIAL",
            checkpoint_namespace=row.get("checkpoint_namespace") or row["id"],
        )

    @staticmethod
    def _report(row: Any) -> ReportVersion:
        return ReportVersion(
            id=row["id"],
            task_id=row["task_id"],
            run_id=row["run_id"],
            version=int(row["version"]),
            is_current=bool(row["is_current"]),
            structured_report=dict(row["structured_report"]),
            markdown_report=row["markdown_report"],
            created_at=row["created_at"],
        )

    @staticmethod
    def _event(row: Any) -> AgUiEvent:
        return AgUiEvent(
            conversation_id=row["conversation_id"],
            type=AgUiEventType(row["event_type"]),
            payload=dict(row["payload"]),
            seq=int(row["sequence"]),
        )

    @staticmethod
    def _validate_operation_scope(run_id: str, node_id: str, operation_key: str) -> None:
        if not node_id or len(node_id) > 128:
            raise ValueError("node_id长度必须为1到128")
        if len(operation_key) > 255 or not operation_key.startswith(f"{run_id}:{node_id}:"):
            raise ValueError("operation_key未绑定当前run和node")

    @staticmethod
    def _submission(value: dict[str, Any]) -> SubmissionResult:
        return SubmissionResult(
            message_id=value["message_id"],
            assistant_message_id=value.get("assistant_message_id"),
            task_id=value.get("task_id"),
            run_id=value.get("run_id"),
            status=value.get("status"),
            required_input=tuple(value.get("required_input", ())),
        )

    @staticmethod
    def _id(prefix: str) -> str:
        return f"{prefix}_{uuid4().hex}"

    @staticmethod
    def _stable_id(prefix: str, request_key: str) -> str:
        return f"{prefix}_{hashlib.sha256(request_key.encode()).hexdigest()[:32]}"

    @classmethod
    def _same_idempotent_run(
        cls,
        row: Any,
        conversation_id: str,
        actor_user_id: str,
    ) -> WorkflowRun:
        if (
            row["conversation_id"] != conversation_id
            or row["owner_user_id"] != actor_user_id
        ):
            raise ActiveRunConflict("幂等运行范围不匹配")
        return cls._run(row)
