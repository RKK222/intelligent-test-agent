import os
from pathlib import Path
import json
from datetime import datetime, timedelta, timezone
from typing import Any
from uuid import uuid4

from alembic import command
from alembic.config import Config
import httpx
import pytest

from testagent_memory_service.gateway import HmacModelGatewayClient
from testagent_memory_service.mem0_adapter import Mem0MemoryStore, OwnerScope
from testagent_memory_service.settings import MemoryServiceSettings


ROOT = Path(__file__).resolve().parents[1]
PGVECTOR_IMAGE = (
    "pgvector/pgvector:0.8.1-pg16@sha256:"
    "33198da2828a14c30348d2ccb4750833d5ed9a44c88d840a0e523d7417120337"
)


@pytest.mark.skipif(
    os.environ.get("TEST_AGENT_MEMORY_RUN_INTEGRATION") != "true",
    reason="需要 Docker 中的真实共享 PostgreSQL/pgvector",
)
def test_two_stateless_mem0_replicas_share_pgvector_history_and_rrf(monkeypatch: pytest.MonkeyPatch) -> None:
    """不下载本地模型；真实 Mem0/pgvector 通过受控模型网关桩验证双集合持久化。"""
    from testcontainers.postgres import PostgresContainer

    image = os.environ.get("TEST_AGENT_MEMORY_PGVECTOR_TEST_IMAGE", PGVECTOR_IMAGE)
    with PostgresContainer(image) as postgres:
        database_url = postgres.get_connection_url().replace(
            "postgresql+psycopg2", "postgresql"
        )
        api_key = "integration-service-key-" + "k" * 32
        hmac_secret = "integration-hmac-secret-" + "s" * 32
        monkeypatch.setenv("TEST_AGENT_MEMORY_SERVICE_API_KEY", api_key)
        monkeypatch.setenv("TEST_AGENT_MEMORY_SERVICE_DATABASE_URL", database_url)
        monkeypatch.setenv("TEST_AGENT_MEMORY_SERVICE_MODEL_GATEWAY_HMAC_SECRET", hmac_secret)

        alembic = Config(str(ROOT / "alembic.ini"))
        command.upgrade(alembic, "head")

        settings = MemoryServiceSettings(
            _env_file=None,
            api_key=api_key,
            database_url=database_url,
            model_gateway_hmac_secret=hmac_secret,
            enterprise_embedding_model_id="enterprise-integration",
            enterprise_embedding_dimension=8,
            enterprise_embedding_fingerprint="integration-fingerprint",
        )
        chat_calls = 0

        def gateway_response(request: httpx.Request) -> httpx.Response:
            nonlocal chat_calls
            if request.url.path.endswith("/chat/completions"):
                chat_calls += 1
                return httpx.Response(
                    200,
                    json={
                        "choices": [
                            {
                                "message": {
                                    "content": json.dumps(
                                        {"memory": [{"text": "用户希望先看结论再看细节"}]},
                                        ensure_ascii=False,
                                    )
                                }
                            }
                        ]
                    },
                )
            return _gateway_response(request)

        client = httpx.Client(transport=httpx.MockTransport(gateway_response))
        first = Mem0MemoryStore(
            settings,
            gateway=HmacModelGatewayClient(settings, client),
        )
        second = Mem0MemoryStore(
            settings,
            gateway=HmacModelGatewayClient(settings, client),
        )
        try:
            created = first.add(
                messages="生成测试案例时必须覆盖异常场景和输入边界",
                infer=False,
                requester_user_id="integration-user",
                run_id="integration-run-add",
                session_id="integration-session-a",
                operation_id="integration-operation-add",
                trace_id="trace_integration_add",
                owner=OwnerScope(
                    "PERSONAL_APPLICATION",
                    "platform:integration-user",
                    None,
                    "integration-app",
                ),
                metadata={"source": "MANUAL"},
            )[0]
            assert set(created["metadata"]["projectionStatus"].values()) == {"SYNCED"}
            assert len(created["metadata"]["projectionStatus"]) == 2
            assert first.raw_message_count() == 0

            native_command = {
                "messages": [
                    {"role": "user", "content": "回答时先告诉我结论"},
                    {"role": "assistant", "content": "好的"},
                ],
                "infer": True,
                "requester_user_id": "integration-user",
                "run_id": "integration-run-native",
                "session_id": "integration-session-native",
                "operation_id": "integration-operation-native",
                "trace_id": "trace_integration_native",
                "owner": OwnerScope(
                    "PERSONAL_GLOBAL", "platform:integration-user", None, None
                ),
                "metadata": {"source": "NATIVE"},
                "chat_model_id": "memory-chat",
            }
            native = first.add(**native_command)
            assert len(native) == 1
            assert second.add(**native_command) == native
            assert chat_calls == 1
            assert first.control.operation("integration-operation-native").native_started is True

            # 第二个进程对象没有任何本地 history/向量状态，仍可从共享库读取并 RRF 去重。
            found = second.search(
                query="设计用例时要关注哪些非正常输入",
                scopes=[
                    OwnerScope(
                        "PERSONAL_APPLICATION",
                        "platform:integration-user",
                        None,
                        "integration-app",
                    )
                ],
                top_k=5,
                threshold=0.0,
                requester_user_id="integration-user",
                run_id="integration-run-search",
                session_id="integration-session-b",
                operation_id="integration-operation-search",
                trace_id="trace_integration_search",
            )
            found_ids = [item["id"] for item in found]
            assert created["id"] in found_ids
            assert len(found_ids) == len(set(found_ids))
            assert all(len(item["metadata"]["profileRanks"]) == 2 for item in found)
            assert second.history(created["id"])[0]["event"] == "ADD"
            assert second.raw_message_count() == 0

            # 模拟副本在逻辑版本提交后、投影 outbox 写入前退出。共享巡检会补出任务，
            # 另一副本继续更新既有 Mem0 向量，证明新增 profile/崩溃窗口无需人工补数。
            repaired_target = next(
                profile for profile in first.profiles if not profile.profile.primary
            )
            repaired_profile = repaired_target.profile.profile_key
            original_projection = first.control.projections(created["id"])[repaired_profile]
            original_mem0_id = original_projection.mem0_memory_id
            assert original_mem0_id is not None
            with first.pool.connection() as connection:
                connection.execute(
                    """
                    update memory_projections
                    set mem0_memory_id = null, projected_version = 0, status = 'PENDING'
                    where logical_memory_id = %s and profile_key = %s
                    """,
                    (created["id"], repaired_profile),
                )
            assert first.control.reconcile_projection_outbox([repaired_profile], 32) == 1
            claimed = second.control.claim_outbox("integration-worker", 32)
            repair = next(
                item
                for item in claimed
                if item.logical_memory_id == created["id"]
                and item.target_profile_key == repaired_profile
            )
            second.retry_projection(repair, "integration-worker")
            repaired = second.control.projections(created["id"])[repaired_profile]
            assert repaired.status == "SYNCED"
            assert repaired.projected_version == 1
            assert repaired.mem0_memory_id == original_mem0_id
            projected_rows = repaired_target.engine.get_all(
                filters={
                    "user_id": "platform:integration-user",
                    "scope": "PERSONAL_APPLICATION",
                    "applicationId": "integration-app",
                    "logicalMemoryId": created["id"],
                },
                top_k=100,
            )["results"]
            assert [row["id"] for row in projected_rows] == [original_mem0_id]

            # 长时间 provider 故障进入 DEAD 后不能永久搁置；冷却期后巡检会重开
            # 同一幂等任务，恢复时仍更新原 Mem0 ID 而不新建逻辑记忆。
            updated = first.update(
                created["id"],
                "生成测试案例时必须覆盖异常、边界和权限场景",
                {},
                requester_user_id="integration-user",
                run_id="integration-run-update",
                session_id="integration-session-a",
                operation_id="integration-operation-update",
                trace_id="trace_integration_update",
            )
            assert updated["metadata"]["logicalVersion"] == 2
            stale = datetime.now(timezone.utc) - timedelta(minutes=6)
            with first.pool.connection() as connection:
                connection.execute(
                    """
                    update memory_projections
                    set projected_version = 1, status = 'FAILED'
                    where logical_memory_id = %s and profile_key = %s
                    """,
                    (created["id"], repaired_profile),
                )
                connection.execute(
                    """
                    insert into memory_projection_outbox(
                        outbox_id, logical_memory_id, target_profile_key, operation,
                        target_version, idempotency_key, status, attempts, available_at,
                        claimed_by, lease_until, last_error_code, created_at, updated_at
                    ) values (%s, %s, %s, 'UPSERT', 2, %s, 'DEAD', 12, %s,
                              null, null, 'GATEWAYUNAVAILABLE', %s, %s)
                    """,
                    (
                        f"mpo_{uuid4().hex}",
                        created["id"],
                        repaired_profile,
                        f"{created['id']}:{repaired_profile}:UPSERT:2",
                        stale,
                        stale,
                        stale,
                    ),
                )
            assert first.control.reconcile_projection_outbox([repaired_profile], 32) == 1
            revived = second.control.claim_outbox("integration-worker-revived", 32)
            revive = next(
                item
                for item in revived
                if item.logical_memory_id == created["id"]
                and item.target_version == 2
            )
            second.retry_projection(revive, "integration-worker-revived")
            repaired = second.control.projections(created["id"])[repaired_profile]
            assert repaired.status == "SYNCED"
            assert repaired.projected_version == 2

            # 模拟向量删除成功、控制表提交前节点退出。再次删除同一逻辑版本应幂等
            # 完成，不产生永久失败的 outbox。
            assert repaired.mem0_memory_id is not None
            repaired_target.engine.delete(repaired.mem0_memory_id)
            first.delete(
                created["id"],
                requester_user_id="integration-user",
                run_id="integration-run-delete",
                session_id="integration-session-a",
                operation_id="integration-operation-delete",
                trace_id="trace_integration_delete",
            )
            deleted_projections = first.control.projections(created["id"])
            assert set(value.status for value in deleted_projections.values()) == {"DELETED"}
            assert set(value.projected_version for value in deleted_projections.values()) == {3}
        finally:
            first.close()
            second.close()
            client.close()


def _gateway_response(request: httpx.Request) -> httpx.Response:
    if request.url.path.endswith("/embeddings"):
        payload = _json(request)
        values = payload["input"]
        if isinstance(values, str):
            values = [values]
        dimension = 8 if payload["model"] == "enterprise-integration" else 512
        # 固定单位向量只用于验证真实 Mem0/pgvector 的集合隔离、共享持久化和 RRF；
        # 中文语义质量由独立 CPU BGE 服务及真实浏览器链路验收。
        vector = [1.0] + [0.0] * (dimension - 1)
        return httpx.Response(
            200,
            json={
                "object": "list",
                "model": payload["model"],
                "data": [
                    {"object": "embedding", "index": index, "embedding": vector}
                    for index, _ in enumerate(values)
                ],
            },
        )
    return httpx.Response(500, json={"error": "integration test must not call CHAT"})


def _json(request: httpx.Request) -> dict[str, Any]:
    result = json.loads(request.content)
    assert isinstance(result, dict)
    return result
