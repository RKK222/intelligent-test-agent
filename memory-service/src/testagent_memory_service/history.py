"""共享 PostgreSQL Mem0 history；原始 USER/ASSISTANT 消息永不落库。"""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Any
from uuid import uuid4

from psycopg.rows import dict_row
from psycopg_pool import ConnectionPool


class PostgresHistoryManager:
    """实现 Mem0 history manager 协议，并把 message history 固定为空。"""

    def __init__(self, pool: ConnectionPool, profile_key: str):
        self.pool = pool
        self.profile_key = profile_key

    def add_history(
        self,
        memory_id: str,
        old_memory: str | None,
        new_memory: str | None,
        event: str,
        *,
        created_at: str | None = None,
        updated_at: str | None = None,
        is_deleted: int = 0,
        actor_id: str | None = None,
        role: str | None = None,
    ) -> None:
        with self.pool.connection() as connection, connection.cursor() as cursor:
            cursor.execute(
                """
                insert into memory_mem0_history(
                    history_id, profile_key, mem0_memory_id, old_memory, new_memory,
                    event, created_at, updated_at, is_deleted, actor_id, role
                ) values (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                """,
                (
                    str(uuid4()),
                    self.profile_key,
                    memory_id,
                    old_memory,
                    new_memory,
                    event,
                    created_at or datetime.now(timezone.utc),
                    updated_at,
                    bool(is_deleted),
                    actor_id,
                    role,
                ),
            )

    def batch_add_history(self, records: list[dict[str, Any]]) -> None:
        if not records:
            return
        now = datetime.now(timezone.utc)
        values = [
            (
                str(uuid4()),
                self.profile_key,
                record.get("memory_id"),
                record.get("old_memory"),
                record.get("new_memory"),
                record.get("event"),
                record.get("created_at") or now,
                record.get("updated_at"),
                bool(record.get("is_deleted", 0)),
                record.get("actor_id"),
                record.get("role"),
            )
            for record in records
        ]
        with self.pool.connection() as connection, connection.cursor() as cursor:
            cursor.executemany(
                """
                insert into memory_mem0_history(
                    history_id, profile_key, mem0_memory_id, old_memory, new_memory,
                    event, created_at, updated_at, is_deleted, actor_id, role
                ) values (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                """,
                values,
            )

    def get_history(self, memory_id: str) -> list[dict[str, Any]]:
        with self.pool.connection() as connection, connection.cursor(row_factory=dict_row) as cursor:
            cursor.execute(
                """
                select history_id as id, mem0_memory_id as memory_id, old_memory, new_memory,
                       event, created_at, updated_at, is_deleted, actor_id, role
                from memory_mem0_history
                where profile_key = %s and mem0_memory_id = %s
                order by created_at, history_id
                """,
                (self.profile_key, memory_id),
            )
            return [_json_times(dict(row)) for row in cursor.fetchall()]

    def save_messages(self, messages: list[dict[str, Any]], session_scope: str) -> None:
        """Mem0 原生推理可读取本次请求，但禁止把它追加成跨请求聊天 history。"""
        del messages, session_scope

    def get_last_messages(
        self, session_scope: str, limit: int = 10
    ) -> list[dict[str, Any]]:
        del session_scope, limit
        return []

    def raw_message_count(self) -> int:
        return 0

    def reset(self) -> None:
        with self.pool.connection() as connection, connection.cursor() as cursor:
            cursor.execute(
                "delete from memory_mem0_history where profile_key = %s",
                (self.profile_key,),
            )

    def close(self) -> None:
        """连接池由应用统一管理，profile history 不单独关闭共享资源。"""


def _json_times(row: dict[str, Any]) -> dict[str, Any]:
    for key in ("created_at", "updated_at"):
        value = row.get(key)
        if isinstance(value, datetime):
            row[key] = value.isoformat()
    return row
