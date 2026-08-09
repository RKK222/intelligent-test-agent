import os
from pathlib import Path

import pytest

from testagent_memory_service.mem0_adapter import Mem0MemoryStore
from testagent_memory_service.settings import MemoryServiceSettings


@pytest.mark.skipif(
    os.environ.get("TEST_AGENT_MEMORY_RUN_INTEGRATION") != "true",
    reason="需要固定 BGE 权重和真实 pgvector",
)
def test_real_mem0_pgvector_restart_and_chinese_semantic_search(tmp_path: Path) -> None:
    from testcontainers.postgres import PostgresContainer

    model_path = Path(os.environ["TEST_AGENT_MEMORY_MODEL_PATH"])
    with PostgresContainer("pgvector/pgvector:pg16") as postgres:
        settings = MemoryServiceSettings(
            _env_file=None,
            api_key="integration-key-" + "k" * 32,
            database_url=postgres.get_connection_url().replace("postgresql+psycopg2", "postgresql"),
            history_db_path=tmp_path / "history.db",
            model_root=model_path.parent,
            model_path=model_path,
        )
        first = Mem0MemoryStore(settings)
        created = first.add(
            content="生成测试案例时必须覆盖异常场景和输入边界",
            user_id="platform:integration-user",
            agent_id=None,
            metadata={"scope": "PERSONAL_GLOBAL", "taskTypes": ["TEST_CASE_GENERATION"]},
        )
        assert first.raw_message_count() == 0

        restarted = Mem0MemoryStore(settings)
        found = restarted.search(
            query="设计用例时要关注哪些非正常输入",
            filters={"user_id": "platform:integration-user"},
            top_k=5,
            threshold=0.1,
        )
        assert any(item["id"] == created["id"] for item in found)
        assert restarted.history(created["id"])[0]["event"] == "ADD"
        assert restarted.raw_message_count() == 0
