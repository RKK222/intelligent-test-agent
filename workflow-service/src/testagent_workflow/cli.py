"""数据库迁移、LangGraph checkpoint初始化和服务进程入口。"""

from __future__ import annotations

import argparse
import asyncio

import uvicorn
from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver

from testagent_workflow.database import migrate_database
from testagent_workflow.factory import build_worker
from testagent_workflow.settings import WorkflowSettings


async def initialize_checkpoints(database_url: str) -> None:
    async with AsyncPostgresSaver.from_conn_string(database_url) as checkpointer:
        await checkpointer.setup()


async def run_worker(settings: WorkflowSettings) -> None:
    async with AsyncPostgresSaver.from_conn_string(
        settings.checkpoint_database_url()
    ) as checkpointer:
        worker = build_worker(checkpointer, settings)
        try:
            await asyncio.gather(
                worker.run_forever(),
                worker.workspace_cleanup.run_forever(),  # type: ignore[attr-defined]
            )
        finally:
            engine, platform, runner = worker.resources  # type: ignore[attr-defined]
            await engine.dispose()
            await platform.aclose()
            await runner.aclose()


def main() -> None:
    parser = argparse.ArgumentParser(description="Test Agent Python工作流服务")
    parser.add_argument("command", choices=("migrate", "checkpoint-setup", "api", "worker"))
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8090)
    arguments = parser.parse_args()
    settings = WorkflowSettings()  # type: ignore[call-arg]
    if arguments.command == "migrate":
        migrate_database(settings.sync_database_url())
        return
    if arguments.command == "checkpoint-setup":
        asyncio.run(initialize_checkpoints(settings.checkpoint_database_url()))
        return
    if arguments.command == "worker":
        asyncio.run(run_worker(settings))
        return
    uvicorn.run(
        "testagent_workflow.factory:build_app",
        host=arguments.host,
        port=arguments.port,
        factory=True,
        proxy_headers=True,
        forwarded_allow_ips="*",
        access_log=True,
    )


if __name__ == "__main__":
    main()
