"""多节点安全的投影 outbox 消费器；PostgreSQL SKIP LOCKED 负责副本间分工。"""

from __future__ import annotations

import asyncio
import logging
from uuid import uuid4

from testagent_memory_service.mem0_adapter import Mem0MemoryStore
from testagent_memory_service.settings import MemoryServiceSettings


LOGGER = logging.getLogger(__name__)


class ProjectionWorker:
    def __init__(self, store: Mem0MemoryStore, settings: MemoryServiceSettings):
        self.store = store
        self.settings = settings
        self.worker_id = f"mpw_{uuid4().hex}"
        self._stopping = asyncio.Event()
        self._task: asyncio.Task[None] | None = None

    def start(self) -> None:
        if self._task is None:
            self._task = asyncio.create_task(self._run(), name=self.worker_id)

    async def close(self) -> None:
        self._stopping.set()
        if self._task is not None:
            await self._task

    async def _run(self) -> None:
        while not self._stopping.is_set():
            try:
                await asyncio.to_thread(
                    self.store.control.reconcile_projection_outbox,
                    [profile.profile.profile_key for profile in self.store.profiles],
                    self.settings.projection_batch_size,
                )
                items = await asyncio.to_thread(
                    self.store.control.claim_outbox,
                    self.worker_id,
                    self.settings.projection_batch_size,
                )
                if items:
                    await asyncio.gather(
                        *(
                            asyncio.to_thread(self.store.retry_projection, item, self.worker_id)
                            for item in items
                        )
                    )
                    continue
            except Exception as exception:
                # 共享数据库或连接池短暂故障不能永久杀死副本内的补偿循环；这里只记录
                # 异常类型，不记录上游消息，下一轮继续由多副本竞争恢复。
                LOGGER.warning(
                    "Memory projection worker iteration failed, workerId=%s, exceptionType=%s",
                    self.worker_id,
                    type(exception).__name__,
                )
            try:
                await asyncio.wait_for(
                    self._stopping.wait(), timeout=self.settings.projection_poll_seconds
                )
            except TimeoutError:
                continue
