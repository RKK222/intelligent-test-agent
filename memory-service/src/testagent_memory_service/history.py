"""保留派生记忆变更历史，同时硬性禁止 Mem0 保存原始消息。"""

from __future__ import annotations

from typing import Any

from mem0.memory.storage import SQLiteManager


class NoRawMessageHistoryManager(SQLiteManager):
    """Mem0 2.0.3 history manager 合同适配：messages 永久为零行。"""

    def save_messages(self, messages: list[dict[str, Any]], session_scope: str) -> None:
        del messages, session_scope

    def get_last_messages(
        self, session_scope: str, limit: int = 10
    ) -> list[dict[str, Any]]:
        del session_scope, limit
        return []

    def raw_message_count(self) -> int:
        with self._lock:
            row = self.connection.execute("SELECT COUNT(*) FROM messages").fetchone()
        return int(row[0]) if row else 0
