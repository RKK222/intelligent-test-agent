"""共享记忆控制面：逻辑 ID、幂等、advisory lock 与投影 outbox。"""

from __future__ import annotations

from contextlib import contextmanager
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
import json
from typing import Any, Iterator
from uuid import uuid4

from psycopg.rows import dict_row
from psycopg.types.json import Jsonb
from psycopg_pool import ConnectionPool


@dataclass(frozen=True, slots=True)
class LogicalRecord:
    logical_memory_id: str
    scope: str
    owner_user_id: str | None
    application_id: str | None
    content: str
    metadata: dict[str, Any]
    version: int
    deleted: bool
    created_at: datetime
    updated_at: datetime


@dataclass(frozen=True, slots=True)
class Projection:
    logical_memory_id: str
    profile_key: str
    mem0_memory_id: str | None
    projected_version: int
    status: str


@dataclass(frozen=True, slots=True)
class OutboxItem:
    outbox_id: str
    logical_memory_id: str
    target_profile_key: str
    operation: str
    target_version: int
    attempts: int


@dataclass(frozen=True, slots=True)
class OperationState:
    status: str
    result: list[dict[str, Any]] | None
    updated_at: datetime
    binding: str
    native_started: bool = False


class MemoryControlStore:
    def __init__(self, pool: ConnectionPool):
        self.pool = pool

    @contextmanager
    def partition_lock(self, partition_key: str) -> Iterator[None]:
        """Session advisory lock 覆盖原生抽取与写入；同分区串行、不同分区并行。"""
        with self.pool.connection() as connection:
            previous_autocommit = connection.autocommit
            connection.autocommit = True
            try:
                connection.execute(
                    "select pg_advisory_lock(hashtextextended(%s, 0))", (partition_key,)
                )
                yield
            finally:
                connection.execute(
                    "select pg_advisory_unlock(hashtextextended(%s, 0))", (partition_key,)
                )
                connection.autocommit = previous_autocommit

    def operation(self, operation_id: str) -> OperationState | None:
        with self.pool.connection() as connection, connection.cursor(row_factory=dict_row) as cursor:
            cursor.execute(
                """
                select status, result_json, updated_at, partition_key, native_started
                from memory_operations where operation_id = %s
                """,
                (operation_id,),
            )
            row = cursor.fetchone()
        if row is None:
            return None
        payload = row["result_json"]
        if isinstance(payload, str):
            payload = json.loads(payload)
        return OperationState(
            str(row["status"]), payload, row["updated_at"], str(row["partition_key"]),
            bool(row["native_started"]),
        )

    def start_operation(self, operation_id: str, binding: str) -> OperationState:
        now = datetime.now(timezone.utc)
        with self.pool.connection() as connection:
            connection.execute(
                """
                insert into memory_operations(
                    operation_id, partition_key, status, result_json, error_code,
                    native_started, started_at, updated_at
                ) values (%s, %s, 'STARTED', null, null, false, %s, %s)
                on conflict (operation_id) do nothing
                """,
                (operation_id, binding, now, now),
            )
        state = self.operation(operation_id)
        if state is None:
            raise RuntimeError("幂等操作创建后不可读")
        return state

    def restart_operation(self, operation_id: str, binding: str) -> None:
        now = datetime.now(timezone.utc)
        with self.pool.connection() as connection:
            result = connection.execute(
                """
                update memory_operations set status = 'STARTED', error_code = null, updated_at = %s
                where operation_id = %s and partition_key = %s and status != 'COMPLETED'
                """,
                (now, operation_id, binding),
            )
            if result.rowcount != 1:
                raise RuntimeError("幂等操作状态已变更")

    def mark_native_started(self, operation_id: str, binding: str) -> None:
        """在进入 Mem0 infer=true 前持久化 at-most-once 边界。"""
        with self.pool.connection() as connection:
            result = connection.execute(
                """
                update memory_operations set native_started = true, updated_at = %s
                where operation_id = %s and partition_key = %s
                  and status = 'STARTED' and native_started = false
                """,
                (datetime.now(timezone.utc), operation_id, binding),
            )
            if result.rowcount != 1:
                raise RuntimeError("原生抽取已开始或幂等操作状态已变更")

    def complete_operation(self, operation_id: str, result: list[dict[str, Any]]) -> None:
        with self.pool.connection() as connection:
            connection.execute(
                """
                update memory_operations
                set status = 'COMPLETED', result_json = %s, error_code = null, updated_at = %s
                where operation_id = %s
                """,
                (Jsonb(result), datetime.now(timezone.utc), operation_id),
            )

    def fail_operation(self, operation_id: str, error_code: str) -> None:
        with self.pool.connection() as connection:
            connection.execute(
                """
                update memory_operations set status = 'FAILED', error_code = %s, updated_at = %s
                where operation_id = %s and status != 'COMPLETED'
                """,
                (error_code[:64], datetime.now(timezone.utc), operation_id),
            )

    def create_logical(
        self,
        *,
        logical_memory_id: str,
        scope: str,
        owner_user_id: str | None,
        application_id: str | None,
        content: str,
        metadata: dict[str, Any],
        source_profile_key: str,
        source_mem0_memory_id: str,
    ) -> LogicalRecord:
        now = datetime.now(timezone.utc)
        with self.pool.connection() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    insert into memory_logical_records(
                        logical_memory_id, scope, owner_user_id, application_id, content,
                        metadata, version, deleted, created_at, updated_at
                    ) values (%s, %s, %s, %s, %s, %s, 1, false, %s, %s)
                    """,
                    (
                        logical_memory_id,
                        scope,
                        owner_user_id,
                        application_id,
                        content,
                        Jsonb(metadata),
                        now,
                        now,
                    ),
                )
                cursor.execute(
                    """
                    insert into memory_projections(
                        logical_memory_id, profile_key, mem0_memory_id, projected_version,
                        status, last_error_code, updated_at
                    ) values (%s, %s, %s, 1, 'SYNCED', null, %s)
                    """,
                    (logical_memory_id, source_profile_key, source_mem0_memory_id, now),
                )
                cursor.execute(
                    """
                    insert into memory_logical_history(
                        event_id, logical_memory_id, version, event, old_memory, new_memory, created_at
                    ) values (%s, %s, 1, 'ADD', null, %s, %s)
                    """,
                    (f"mhe_{uuid4().hex}", logical_memory_id, content, now),
                )
        return LogicalRecord(
            logical_memory_id,
            scope,
            owner_user_id,
            application_id,
            content,
            dict(metadata),
            1,
            False,
            now,
            now,
        )

    def find_by_projection(self, profile_key: str, mem0_memory_id: str) -> LogicalRecord | None:
        with self.pool.connection() as connection, connection.cursor(row_factory=dict_row) as cursor:
            cursor.execute(
                """
                select r.* from memory_logical_records r
                join memory_projections p on p.logical_memory_id = r.logical_memory_id
                where p.profile_key = %s and p.mem0_memory_id = %s
                """,
                (profile_key, mem0_memory_id),
            )
            row = cursor.fetchone()
        return _logical(row) if row else None

    def get_logical(self, logical_memory_id: str, include_deleted: bool = False) -> LogicalRecord | None:
        with self.pool.connection() as connection, connection.cursor(row_factory=dict_row) as cursor:
            cursor.execute(
                """
                select * from memory_logical_records
                where logical_memory_id = %s and (%s or deleted = false)
                """,
                (logical_memory_id, include_deleted),
            )
            row = cursor.fetchone()
        return _logical(row) if row else None

    def projections(self, logical_memory_id: str) -> dict[str, Projection]:
        with self.pool.connection() as connection, connection.cursor(row_factory=dict_row) as cursor:
            cursor.execute(
                "select * from memory_projections where logical_memory_id = %s",
                (logical_memory_id,),
            )
            rows = cursor.fetchall()
        return {
            str(row["profile_key"]): Projection(
                str(row["logical_memory_id"]),
                str(row["profile_key"]),
                row["mem0_memory_id"],
                int(row["projected_version"]),
                str(row["status"]),
            )
            for row in rows
        }

    def update_logical(
        self,
        logical_memory_id: str,
        content: str,
        metadata: dict[str, Any],
        *,
        scope: str | None = None,
        application_id: str | None = None,
    ) -> LogicalRecord:
        current = self.get_logical(logical_memory_id)
        if current is None:
            raise KeyError(logical_memory_id)
        now = datetime.now(timezone.utc)
        version = current.version + 1
        merged = {**current.metadata, **metadata}
        next_scope = scope or current.scope
        next_application_id = application_id if scope is not None else current.application_id
        if current.scope == "TEAM_APPLICATION" and next_scope != current.scope:
            raise ValueError("团队记忆不能通过个人范围接口移动")
        if next_scope == "PERSONAL_GLOBAL":
            next_application_id = None
        with self.pool.connection() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    update memory_logical_records
                    set scope = %s, application_id = %s, content = %s,
                        metadata = %s, version = %s, updated_at = %s
                    where logical_memory_id = %s and version = %s and deleted = false
                    """,
                    (
                        next_scope,
                        next_application_id,
                        content,
                        Jsonb(merged),
                        version,
                        now,
                        logical_memory_id,
                        current.version,
                    ),
                )
                if cursor.rowcount != 1:
                    raise RuntimeError("逻辑记忆并发版本冲突")
                cursor.execute(
                    """
                    insert into memory_logical_history(
                        event_id, logical_memory_id, version, event, old_memory, new_memory, created_at
                    ) values (%s, %s, %s, 'UPDATE', %s, %s, %s)
                    """,
                    (f"mhe_{uuid4().hex}", logical_memory_id, version, current.content, content, now),
                )
        return LogicalRecord(
            logical_memory_id,
            next_scope,
            current.owner_user_id,
            next_application_id,
            content,
            merged,
            version,
            False,
            current.created_at,
            now,
        )

    def delete_logical(
        self, logical_memory_id: str, metadata: dict[str, Any] | None = None
    ) -> LogicalRecord:
        current = self.get_logical(logical_memory_id)
        if current is None:
            raise KeyError(logical_memory_id)
        now = datetime.now(timezone.utc)
        version = current.version + 1
        merged = {**current.metadata, **(metadata or {})}
        with self.pool.connection() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    update memory_logical_records
                    set deleted = true, metadata = %s, version = %s, updated_at = %s
                    where logical_memory_id = %s and version = %s and deleted = false
                    """,
                    (Jsonb(merged), version, now, logical_memory_id, current.version),
                )
                if cursor.rowcount != 1:
                    raise RuntimeError("逻辑记忆并发版本冲突")
                cursor.execute(
                    """
                    insert into memory_logical_history(
                        event_id, logical_memory_id, version, event, old_memory, new_memory, created_at
                    ) values (%s, %s, %s, 'DELETE', %s, null, %s)
                    """,
                    (f"mhe_{uuid4().hex}", logical_memory_id, version, current.content, now),
                )
        return LogicalRecord(
            logical_memory_id,
            current.scope,
            current.owner_user_id,
            current.application_id,
            current.content,
            merged,
            version,
            True,
            current.created_at,
            now,
        )

    def logical_history(self, logical_memory_id: str) -> list[dict[str, Any]]:
        with self.pool.connection() as connection, connection.cursor(row_factory=dict_row) as cursor:
            cursor.execute(
                """
                select event_id as id, logical_memory_id as memory_id, old_memory, new_memory,
                       event, created_at, null::timestamptz as updated_at,
                       (event = 'DELETE') as is_deleted, version
                from memory_logical_history where logical_memory_id = %s
                order by version
                """,
                (logical_memory_id,),
            )
            return [_json_times(dict(row)) for row in cursor.fetchall()]

    def resolve_logical_ids(
        self, profile_key: str, mem0_ids: list[str]
    ) -> dict[str, str]:
        if not mem0_ids:
            return {}
        with self.pool.connection() as connection, connection.cursor(row_factory=dict_row) as cursor:
            cursor.execute(
                """
                select p.mem0_memory_id, p.logical_memory_id
                from memory_projections p
                join memory_logical_records r on r.logical_memory_id = p.logical_memory_id
                where p.profile_key = %s and p.mem0_memory_id = any(%s) and r.deleted = false
                """,
                (profile_key, mem0_ids),
            )
            return {
                str(row["mem0_memory_id"]): str(row["logical_memory_id"])
                for row in cursor.fetchall()
            }

    def mark_projection_pending(self, logical_memory_id: str, profile_key: str) -> None:
        with self.pool.connection() as connection:
            connection.execute(
                """
                insert into memory_projections(
                    logical_memory_id, profile_key, mem0_memory_id, projected_version,
                    status, last_error_code, updated_at
                ) values (%s, %s, null, 0, 'PENDING', null, %s)
                on conflict (logical_memory_id, profile_key) do update
                set status = 'PENDING', last_error_code = null, updated_at = excluded.updated_at
                """,
                (logical_memory_id, profile_key, datetime.now(timezone.utc)),
            )

    def mark_projection_synced(
        self,
        logical_memory_id: str,
        profile_key: str,
        mem0_memory_id: str | None,
        version: int,
        deleted: bool = False,
    ) -> None:
        with self.pool.connection() as connection:
            connection.execute(
                """
                insert into memory_projections(
                    logical_memory_id, profile_key, mem0_memory_id, projected_version,
                    status, last_error_code, updated_at
                ) values (%s, %s, %s, %s, %s, null, %s)
                on conflict (logical_memory_id, profile_key) do update
                set mem0_memory_id = coalesce(excluded.mem0_memory_id, memory_projections.mem0_memory_id),
                    projected_version = excluded.projected_version,
                    status = excluded.status, last_error_code = null, updated_at = excluded.updated_at
                """,
                (
                    logical_memory_id,
                    profile_key,
                    mem0_memory_id,
                    version,
                    "DELETED" if deleted else "SYNCED",
                    datetime.now(timezone.utc),
                ),
            )

    def mark_projection_failed(self, logical_memory_id: str, profile_key: str, code: str) -> None:
        with self.pool.connection() as connection:
            connection.execute(
                """
                update memory_projections set status = 'FAILED', last_error_code = %s, updated_at = %s
                where logical_memory_id = %s and profile_key = %s
                """,
                (code[:64], datetime.now(timezone.utc), logical_memory_id, profile_key),
            )

    def fail_and_enqueue_projection(
        self,
        logical_memory_id: str,
        profile_key: str,
        operation: str,
        version: int,
        code: str,
    ) -> None:
        """失败状态与 outbox 同事务提交，避免节点退出留下无补偿的 FAILED。"""
        now = datetime.now(timezone.utc)
        idempotency_key = f"{logical_memory_id}:{profile_key}:{operation}:{version}"
        with self.pool.connection() as connection, connection.cursor() as cursor:
            cursor.execute(
                """
                update memory_projections
                set status = 'FAILED', last_error_code = %s, updated_at = %s
                where logical_memory_id = %s and profile_key = %s
                """,
                (code[:64], now, logical_memory_id, profile_key),
            )
            cursor.execute(
                """
                insert into memory_projection_outbox(
                    outbox_id, logical_memory_id, target_profile_key, operation,
                    target_version, idempotency_key, status, attempts, available_at,
                    claimed_by, lease_until, last_error_code, created_at, updated_at
                ) values (%s, %s, %s, %s, %s, %s, 'PENDING', 0, %s,
                          null, null, %s, %s, %s)
                on conflict (idempotency_key) do nothing
                """,
                (
                    f"mpo_{uuid4().hex}",
                    logical_memory_id,
                    profile_key,
                    operation,
                    version,
                    idempotency_key,
                    now,
                    code[:64],
                    now,
                    now,
                ),
            )

    def enqueue_projection(
        self,
        logical_memory_id: str,
        profile_key: str,
        operation: str,
        version: int,
    ) -> None:
        now = datetime.now(timezone.utc)
        idempotency_key = f"{logical_memory_id}:{profile_key}:{operation}:{version}"
        with self.pool.connection() as connection:
            connection.execute(
                """
                insert into memory_projection_outbox(
                    outbox_id, logical_memory_id, target_profile_key, operation,
                    target_version, idempotency_key, status, attempts, available_at,
                    claimed_by, lease_until, last_error_code, created_at, updated_at
                ) values (%s, %s, %s, %s, %s, %s, 'PENDING', 0, %s, null, null, null, %s, %s)
                on conflict (idempotency_key) do nothing
                """,
                (
                    f"mpo_{uuid4().hex}",
                    logical_memory_id,
                    profile_key,
                    operation,
                    version,
                    idempotency_key,
                    now,
                    now,
                    now,
                ),
            )

    def reconcile_projection_outbox(self, profile_keys: list[str], limit: int) -> int:
        """补齐崩溃窗口和新增 profile 的缺失投影；多副本用行锁安全竞争。"""
        if not profile_keys:
            return 0
        now = datetime.now(timezone.utc)
        queued = 0
        with self.pool.connection() as connection, connection.cursor(row_factory=dict_row) as cursor:
            cursor.execute(
                """
                select r.logical_memory_id, r.version, r.deleted, desired.profile_key
                from memory_logical_records r
                cross join unnest(%s::text[]) as desired(profile_key)
                left join memory_projections p
                  on p.logical_memory_id = r.logical_memory_id
                 and p.profile_key = desired.profile_key
                where (
                    p.logical_memory_id is null
                    or p.projected_version < r.version
                    or p.status in ('PENDING', 'FAILED')
                )
                  and not exists (
                    select 1 from memory_projection_outbox o
                    where o.logical_memory_id = r.logical_memory_id
                      and o.target_profile_key = desired.profile_key
                      and o.target_version = r.version
                      and not (
                          o.status = 'DEAD'
                          and o.updated_at <= %s - interval '5 minutes'
                      )
                  )
                order by r.updated_at, r.logical_memory_id, desired.profile_key
                for update of r skip locked
                limit %s
                """,
                (profile_keys, now, limit),
            )
            rows = cursor.fetchall()
            for row in rows:
                logical_id = str(row["logical_memory_id"])
                profile_key = str(row["profile_key"])
                version = int(row["version"])
                operation = "DELETE" if bool(row["deleted"]) else "UPSERT"
                idempotency_key = f"{logical_id}:{profile_key}:{operation}:{version}"
                cursor.execute(
                    """
                    insert into memory_projections(
                        logical_memory_id, profile_key, mem0_memory_id, projected_version,
                        status, last_error_code, updated_at
                    ) values (%s, %s, null, 0, 'PENDING', null, %s)
                    on conflict (logical_memory_id, profile_key) do update
                    set status = 'PENDING', last_error_code = null, updated_at = excluded.updated_at
                    where memory_projections.projected_version < %s
                       or memory_projections.status in ('PENDING', 'FAILED')
                    """,
                    (logical_id, profile_key, now, version),
                )
                cursor.execute(
                    """
                    insert into memory_projection_outbox(
                        outbox_id, logical_memory_id, target_profile_key, operation,
                        target_version, idempotency_key, status, attempts, available_at,
                        claimed_by, lease_until, last_error_code, created_at, updated_at
                    ) values (%s, %s, %s, %s, %s, %s, 'PENDING', 0, %s,
                              null, null, null, %s, %s)
                    on conflict (idempotency_key) do update
                    set status = 'PENDING', attempts = 0, available_at = excluded.available_at,
                        claimed_by = null, lease_until = null, last_error_code = null,
                        updated_at = excluded.updated_at
                    where memory_projection_outbox.status = 'DEAD'
                    """,
                    (
                        f"mpo_{uuid4().hex}",
                        logical_id,
                        profile_key,
                        operation,
                        version,
                        idempotency_key,
                        now,
                        now,
                        now,
                    ),
                )
                queued += cursor.rowcount
        return queued

    def claim_outbox(self, worker_id: str, limit: int) -> list[OutboxItem]:
        now = datetime.now(timezone.utc)
        lease = now + timedelta(minutes=2)
        with self.pool.connection() as connection, connection.cursor(row_factory=dict_row) as cursor:
            cursor.execute(
                """
                with candidates as (
                    select outbox_id from memory_projection_outbox
                    where available_at <= %s
                      and (status = 'PENDING' or (status = 'PROCESSING' and lease_until < %s))
                    order by available_at, created_at
                    for update skip locked limit %s
                )
                update memory_projection_outbox o
                set status = 'PROCESSING', claimed_by = %s, lease_until = %s, updated_at = %s
                from candidates c where o.outbox_id = c.outbox_id
                returning o.*
                """,
                (now, now, limit, worker_id, lease, now),
            )
            rows = cursor.fetchall()
        return [
            OutboxItem(
                str(row["outbox_id"]),
                str(row["logical_memory_id"]),
                str(row["target_profile_key"]),
                str(row["operation"]),
                int(row["target_version"]),
                int(row["attempts"]),
            )
            for row in rows
        ]

    def complete_outbox(self, outbox_id: str, worker_id: str) -> None:
        with self.pool.connection() as connection:
            connection.execute(
                """
                update memory_projection_outbox
                set status = 'COMPLETED', claimed_by = null, lease_until = null,
                    last_error_code = null, updated_at = %s
                where outbox_id = %s and claimed_by = %s and status = 'PROCESSING'
                """,
                (datetime.now(timezone.utc), outbox_id, worker_id),
            )

    def retry_outbox(self, outbox_id: str, worker_id: str, code: str, attempts: int) -> None:
        now = datetime.now(timezone.utc)
        next_attempt = attempts + 1
        available = now + timedelta(seconds=min(600, 2 ** min(9, next_attempt)))
        with self.pool.connection() as connection:
            connection.execute(
                """
                update memory_projection_outbox
                set status = case when attempts + 1 >= 12 then 'DEAD' else 'PENDING' end,
                    attempts = attempts + 1, available_at = %s, claimed_by = null,
                    lease_until = null, last_error_code = %s, updated_at = %s
                where outbox_id = %s and claimed_by = %s and status = 'PROCESSING'
                """,
                (available, code[:64], now, outbox_id, worker_id),
            )

    def projection_backlog(self) -> dict[str, int]:
        with self.pool.connection() as connection, connection.cursor(row_factory=dict_row) as cursor:
            cursor.execute(
                """
                select status, count(*) as count from memory_projection_outbox
                where status in ('PENDING', 'PROCESSING', 'DEAD') group by status
                """
            )
            values = {str(row["status"]): int(row["count"]) for row in cursor.fetchall()}
        return {key: values.get(key, 0) for key in ("PENDING", "PROCESSING", "DEAD")}


def _logical(row: dict[str, Any]) -> LogicalRecord:
    metadata = row["metadata"]
    if isinstance(metadata, str):
        metadata = json.loads(metadata)
    return LogicalRecord(
        str(row["logical_memory_id"]),
        str(row["scope"]),
        row["owner_user_id"],
        row["application_id"],
        str(row["content"]),
        dict(metadata or {}),
        int(row["version"]),
        bool(row["deleted"]),
        row["created_at"],
        row["updated_at"],
    )


def _json_times(row: dict[str, Any]) -> dict[str, Any]:
    for key in ("created_at", "updated_at"):
        value = row.get(key)
        if isinstance(value, datetime):
            row[key] = value.isoformat()
    return row
