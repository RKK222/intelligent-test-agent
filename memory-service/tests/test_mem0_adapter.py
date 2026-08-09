from contextlib import contextmanager
from datetime import datetime, timezone
from typing import Any, Iterator

import pytest

from testagent_memory_service.control import LogicalRecord, OperationState, Projection
from testagent_memory_service.gateway import GatewayCallContext
from testagent_memory_service.mem0_adapter import (
    IdempotencyConflict,
    Mem0MemoryStore,
    OwnerScope,
    ProfileEngine,
    _add_binding,
    _update_binding,
)
from testagent_memory_service.settings import MemoryServiceSettings


NOW = datetime(2026, 8, 9, tzinfo=timezone.utc)


class FakeGateway:
    def close(self) -> None:
        pass


class FakeEngine:
    def __init__(
        self,
        prefix: str,
        *,
        fail_preflight: bool = False,
        fail_add: bool = False,
        native_content: str = "用户偏好中文回答",
    ) -> None:
        self.prefix = prefix
        self.fail_preflight = fail_preflight
        self.fail_add = fail_add
        self.native_content = native_content
        self.documents: dict[str, dict[str, Any]] = {}
        self.add_calls: list[dict[str, Any]] = []
        self.counter = 0

    def add(self, messages: Any, **kwargs: Any) -> dict[str, Any]:
        self.add_calls.append({"messages": messages, **kwargs})
        if self.fail_add:
            raise RuntimeError("native add failed after entering Mem0")
        self.counter += 1
        memory_id = f"{self.prefix}-{self.counter}"
        content = (
            self.native_content
            if kwargs["infer"]
            else str(messages)
        )
        identity = {
            key: kwargs[key]
            for key in ("user_id", "agent_id", "run_id")
            if kwargs.get(key) is not None
        }
        self.documents[memory_id] = {
            "id": memory_id,
            "memory": content,
            "metadata": {**kwargs["metadata"], **identity},
            "score": 0.91,
        }
        return {"results": [{"id": memory_id, "memory": content, "event": "ADD"}]}

    def get(self, memory_id: str) -> dict[str, Any] | None:
        return self.documents.get(memory_id)

    def update(self, memory_id: str, text: str | None = None, metadata: dict[str, Any] | None = None) -> dict[str, Any]:
        self.documents[memory_id]["memory"] = text
        self.documents[memory_id]["metadata"].update(metadata or {})
        return {"message": "updated"}

    def delete(self, memory_id: str) -> dict[str, Any]:
        self.documents.pop(memory_id, None)
        return {"message": "deleted"}

    def search(self, query: str, **kwargs: Any) -> dict[str, Any]:
        del query
        if self.fail_preflight and kwargs.get("threshold") == 1.0:
            raise RuntimeError("profile unavailable")
        filters = kwargs.get("filters") or {}
        return {
            "results": [
                value
                for value in self.documents.values()
                if all(value.get("metadata", {}).get(key) == expected for key, expected in filters.items())
            ]
        }

    def get_all(self, **kwargs: Any) -> dict[str, Any]:
        filters = kwargs.get("filters") or {}
        return {
            "results": [
                value
                for value in self.documents.values()
                if all(value.get("metadata", {}).get(key) == expected for key, expected in filters.items())
            ]
        }


class FakeControl:
    def __init__(self) -> None:
        self.operations: dict[str, OperationState] = {}
        self.records: dict[str, LogicalRecord] = {}
        self.projection_values: dict[tuple[str, str], Projection] = {}

    @contextmanager
    def partition_lock(self, partition_key: str) -> Iterator[None]:
        del partition_key
        yield

    def operation(self, operation_id: str) -> OperationState | None:
        return self.operations.get(operation_id)

    def start_operation(self, operation_id: str, binding: str) -> OperationState:
        self.operations.setdefault(operation_id, OperationState("STARTED", None, NOW, binding))
        return self.operations[operation_id]

    def restart_operation(self, operation_id: str, binding: str) -> None:
        current = self.operations[operation_id]
        self.operations[operation_id] = OperationState(
            "STARTED", None, NOW, binding, current.native_started
        )

    def mark_native_started(self, operation_id: str, binding: str) -> None:
        current = self.operations[operation_id]
        assert current.binding == binding
        assert not current.native_started
        self.operations[operation_id] = OperationState("STARTED", None, NOW, binding, True)

    def complete_operation(self, operation_id: str, result: list[dict[str, Any]]) -> None:
        current = self.operations[operation_id]
        self.operations[operation_id] = OperationState(
            "COMPLETED", result, NOW, current.binding, current.native_started
        )

    def fail_operation(self, operation_id: str, error_code: str) -> None:
        del error_code
        current = self.operations[operation_id]
        self.operations[operation_id] = OperationState(
            "FAILED", None, NOW, current.binding, current.native_started
        )

    def find_by_projection(self, profile_key: str, mem0_memory_id: str) -> LogicalRecord | None:
        for (logical_id, key), projection in self.projection_values.items():
            if key == profile_key and projection.mem0_memory_id == mem0_memory_id:
                return self.records[logical_id]
        return None

    def create_logical(self, **kwargs: Any) -> LogicalRecord:
        record = LogicalRecord(
            kwargs["logical_memory_id"],
            kwargs["scope"],
            kwargs["owner_user_id"],
            kwargs["application_id"],
            kwargs["content"],
            kwargs["metadata"],
            1,
            False,
            NOW,
            NOW,
        )
        self.records[record.logical_memory_id] = record
        key = kwargs["source_profile_key"]
        self.projection_values[(record.logical_memory_id, key)] = Projection(
            record.logical_memory_id,
            key,
            kwargs["source_mem0_memory_id"],
            1,
            "SYNCED",
        )
        return record

    def projections(self, logical_memory_id: str) -> dict[str, Projection]:
        return {
            key: value
            for (record_id, key), value in self.projection_values.items()
            if record_id == logical_memory_id
        }

    def mark_projection_pending(self, logical_memory_id: str, profile_key: str) -> None:
        self.projection_values[(logical_memory_id, profile_key)] = Projection(
            logical_memory_id, profile_key, None, 0, "PENDING"
        )

    def mark_projection_synced(
        self,
        logical_memory_id: str,
        profile_key: str,
        mem0_memory_id: str | None,
        version: int,
        deleted: bool = False,
    ) -> None:
        self.projection_values[(logical_memory_id, profile_key)] = Projection(
            logical_memory_id,
            profile_key,
            mem0_memory_id,
            version,
            "DELETED" if deleted else "SYNCED",
        )

    def mark_projection_failed(self, *args: Any) -> None:
        del args

    def enqueue_projection(self, *args: Any) -> None:
        del args

    def fail_and_enqueue_projection(self, *args: Any) -> None:
        self.mark_projection_failed(*args)

    def get_logical(self, logical_memory_id: str, include_deleted: bool = False) -> LogicalRecord | None:
        record = self.records.get(logical_memory_id)
        if record and (include_deleted or not record.deleted):
            return record
        return None

    def update_logical(
        self,
        logical_memory_id: str,
        content: str,
        metadata: dict[str, Any],
        *,
        scope: str | None = None,
        application_id: str | None = None,
    ) -> LogicalRecord:
        current = self.records[logical_memory_id]
        record = LogicalRecord(
            logical_memory_id,
            scope or current.scope,
            current.owner_user_id,
            application_id if scope is not None else current.application_id,
            content,
            {**current.metadata, **metadata},
            current.version + 1,
            False,
            current.created_at,
            NOW,
        )
        self.records[logical_memory_id] = record
        return record

    def delete_logical(
        self, logical_memory_id: str, metadata: dict[str, Any] | None = None
    ) -> LogicalRecord:
        current = self.records[logical_memory_id]
        record = LogicalRecord(
            logical_memory_id,
            current.scope,
            current.owner_user_id,
            current.application_id,
            current.content,
            {**current.metadata, **(metadata or {})},
            current.version + 1,
            True,
            current.created_at,
            NOW,
        )
        self.records[logical_memory_id] = record
        return record

    def resolve_logical_ids(self, profile_key: str, mem0_ids: list[str]) -> dict[str, str]:
        result: dict[str, str] = {}
        for (logical_id, key), projection in self.projection_values.items():
            if key == profile_key and projection.mem0_memory_id in mem0_ids:
                result[str(projection.mem0_memory_id)] = logical_id
        return result


def configured() -> MemoryServiceSettings:
    return MemoryServiceSettings(
        _env_file=None,
        api_key="service-key-" + "k" * 32,
        model_gateway_hmac_secret="gateway-secret-" + "s" * 32,
        enterprise_embedding_model_id="enterprise-embedding",
        enterprise_embedding_dimension=768,
        enterprise_embedding_fingerprint="fingerprint-123456",
    )


def build_store(primary: FakeEngine, cpu: FakeEngine, control: FakeControl) -> Mem0MemoryStore:
    settings = configured()
    enterprise_profile, cpu_profile = settings.profiles()
    return Mem0MemoryStore(
        settings,
        pool=object(),  # type: ignore[arg-type]
        control=control,  # type: ignore[arg-type]
        profiles=(ProfileEngine(enterprise_profile, primary), ProfileEngine(cpu_profile, cpu)),
        gateway=FakeGateway(),  # type: ignore[arg-type]
    )


def test_native_extract_runs_once_and_projects_infer_false() -> None:
    primary = FakeEngine("enterprise")
    cpu = FakeEngine("cpu")
    control = FakeControl()
    store = build_store(primary, cpu, control)
    messages = [
        {"role": "user", "content": "以后请使用中文回答"},
        {"role": "assistant", "content": "好的"},
    ]

    result = store.add(
        messages=messages,
        infer=True,
        requester_user_id="u1",
        run_id="run-1",
        session_id="session-1",
        operation_id="operation-1",
        trace_id="trace_1",
        owner=OwnerScope("PERSONAL_APPLICATION", "platform:u1", None, "app-1"),
        metadata={"source": "NATIVE"},
    )

    assert len(result) == 1
    assert [call["infer"] for call in primary.add_calls] == [True]
    assert [call["infer"] for call in cpu.add_calls] == [False]
    assert result[0]["id"].startswith("lmem_")
    projections = result[0]["metadata"]["projectionStatus"]
    assert set(projections.values()) == {"SYNCED"}

    repeated = store.add(
        messages=messages,
        infer=True,
        requester_user_id="u1",
        run_id="run-1",
        session_id="session-1",
        operation_id="operation-1",
        trace_id="trace_1",
        owner=OwnerScope("PERSONAL_APPLICATION", "platform:u1", None, "app-1"),
        metadata={"source": "NATIVE"},
    )
    assert repeated == result
    assert len(primary.add_calls) == 1


def test_native_memory_content_is_preserved_without_rewriting() -> None:
    original = "  用户希望先看结论。\n第二行保留原样。  "
    primary = FakeEngine("enterprise", native_content=original)
    store = build_store(primary, FakeEngine("cpu"), FakeControl())

    result = store.add(
        messages="本次对话上下文",
        infer=True,
        requester_user_id="u1",
        run_id="run-original",
        session_id="session-original",
        operation_id="operation-original",
        trace_id="trace_original",
        owner=OwnerScope("PERSONAL_GLOBAL", "platform:u1", None, None),
        metadata={"source": "NATIVE"},
    )

    assert result[0]["content"] == original


def test_personal_application_uses_stable_mem0_partition() -> None:
    primary = FakeEngine("enterprise")
    cpu = FakeEngine("cpu")
    store = build_store(primary, cpu, FakeControl())
    app_a = OwnerScope("PERSONAL_APPLICATION", "platform:u1", None, "app-a")
    app_b = OwnerScope("PERSONAL_APPLICATION", "platform:u1", None, "app-b")

    created = store.add(
        messages="App A 的个人记忆",
        infer=False,
        requester_user_id="u1",
        run_id="manual-app-a",
        session_id="manual-app-a",
        operation_id="manual-operation-app-a",
        trace_id="trace_app_a",
        owner=app_a,
        metadata={"source": "MANUAL"},
    )[0]

    assert app_a.mem0_run_id() == app_a.mem0_run_id()
    assert app_a.mem0_run_id() != app_b.mem0_run_id()
    assert primary.add_calls[0]["run_id"] == app_a.mem0_run_id()
    assert store.search(
        query="个人记忆",
        scopes=[app_b],
        top_k=5,
        threshold=0.0,
        requester_user_id="u1",
        run_id="search-app-b",
        session_id="search-app-b",
        operation_id="search-operation-app-b",
        trace_id="trace_app_b",
    ) == []
    assert store.search(
        query="个人记忆",
        scopes=[app_a],
        top_k=5,
        threshold=0.0,
        requester_user_id="u1",
        run_id="search-app-a",
        session_id="search-app-a",
        operation_id="search-operation-app-a",
        trace_id="trace_app_a_search",
    )[0]["id"] == created["id"]


def test_enterprise_preflight_failure_selects_cpu_before_native_extract() -> None:
    primary = FakeEngine("enterprise", fail_preflight=True)
    cpu = FakeEngine("cpu")
    store = build_store(primary, cpu, FakeControl())

    store.add(
        messages="用户偏好简洁回答",
        infer=True,
        requester_user_id="u1",
        run_id="run-2",
        session_id="session-2",
        operation_id="operation-2",
        trace_id="trace_2",
        owner=OwnerScope("PERSONAL_GLOBAL", "platform:u1", None, None),
        metadata={"source": "NATIVE"},
    )

    assert primary.add_calls[0]["infer"] is False  # CPU 抽取后向企业集合补投影
    assert [call["infer"] for call in cpu.add_calls] == [True]


def test_started_operation_recovers_vector_result_without_second_native_extract() -> None:
    primary = FakeEngine("enterprise")
    cpu = FakeEngine("cpu")
    control = FakeControl()
    binding = _add_binding(
        "PERSONAL_GLOBAL:platform:u1:-:-",
        "这段消息不能触发第二次抽取",
        True,
        {"source": "NATIVE"},
        None,
    )
    control.operations["operation-recover"] = OperationState(
        "STARTED", None, NOW, binding, True
    )
    primary.documents["enterprise-existing"] = {
        "id": "enterprise-existing",
        "memory": "用户偏好简洁中文回答",
        "metadata": {
            "scope": "PERSONAL_GLOBAL",
            "user_id": "platform:u1",
            "operationId": "operation-recover",
        },
        "score": 0.91,
    }
    store = build_store(primary, cpu, control)

    recovered = store.add(
        messages="这段消息不能触发第二次抽取",
        infer=True,
        requester_user_id="u1",
        run_id="run-recover",
        session_id="session-recover",
        operation_id="operation-recover",
        trace_id="trace_recover",
        owner=OwnerScope("PERSONAL_GLOBAL", "platform:u1", None, None),
        metadata={"source": "NATIVE"},
    )

    assert len(recovered) == 1
    assert primary.add_calls == []
    assert [call["infer"] for call in cpu.add_calls] == [False]
    assert control.operations["operation-recover"].status == "COMPLETED"


def test_failed_native_attempt_is_not_extracted_twice_on_retry() -> None:
    primary = FakeEngine("enterprise", fail_add=True)
    control = FakeControl()
    store = build_store(primary, FakeEngine("cpu"), control)
    command = {
        "messages": "用户希望先看结论",
        "infer": True,
        "requester_user_id": "u1",
        "run_id": "run-once",
        "session_id": "session-once",
        "operation_id": "operation-once",
        "trace_id": "trace_once",
        "owner": OwnerScope("PERSONAL_GLOBAL", "platform:u1", None, None),
        "metadata": {"source": "NATIVE"},
    }

    with pytest.raises(RuntimeError, match="native add failed"):
        store.add(**command)
    assert control.operations["operation-once"].native_started is True

    assert store.add(**command) == []
    assert len(primary.add_calls) == 1
    assert control.operations["operation-once"].status == "COMPLETED"


def test_readiness_checks_shared_collections_without_embedding_preflight() -> None:
    primary = FakeEngine("enterprise", fail_preflight=True)
    cpu = FakeEngine("cpu", fail_preflight=True)
    store = build_store(primary, cpu, FakeControl())

    assert store.readiness() == {
        store.profiles[0].profile.profile_key: True,
        store.profiles[1].profile.profile_key: True,
    }
    assert primary.add_calls == []
    assert cpu.add_calls == []


def test_dual_profile_search_deduplicates_by_logical_id_with_rrf() -> None:
    primary = FakeEngine("enterprise")
    cpu = FakeEngine("cpu")
    control = FakeControl()
    store = build_store(primary, cpu, control)
    created = store.add(
        messages="用户偏好中文回答",
        infer=False,
        requester_user_id="u1",
        run_id="manual-1",
        session_id="manual-1",
        operation_id="manual-operation-1",
        trace_id="trace_3",
        owner=OwnerScope("PERSONAL_GLOBAL", "platform:u1", None, None),
        metadata={"source": "MANUAL"},
    )[0]

    found = store.search(
        query="回答语言",
        scopes=[OwnerScope("PERSONAL_GLOBAL", "platform:u1", None, None)],
        top_k=5,
        threshold=0.1,
        requester_user_id="u1",
        run_id="run-3",
        session_id="session-3",
        operation_id="search-operation-3",
        trace_id="trace_4",
    )
    assert [item["id"] for item in found] == [created["id"]]
    assert len(found[0]["metadata"]["profileRanks"]) == 2


def test_update_and_delete_retries_do_not_increment_version_twice() -> None:
    primary = FakeEngine("enterprise")
    cpu = FakeEngine("cpu")
    control = FakeControl()
    store = build_store(primary, cpu, control)
    created = store.add(
        messages="用户偏好中文回答",
        infer=False,
        requester_user_id="u1",
        run_id="manual-2",
        session_id="manual-2",
        operation_id="manual-operation-2",
        trace_id="trace_5",
        owner=OwnerScope("PERSONAL_GLOBAL", "platform:u1", None, None),
        metadata={"source": "MANUAL"},
    )[0]

    updated = store.update(
        created["id"],
        "用户偏好简洁中文回答",
        {},
        requester_user_id="u1",
        run_id="manual-2",
        session_id="manual-2",
        operation_id="update-operation-2",
        trace_id="trace_6",
    )
    repeated = store.update(
        created["id"],
        "用户偏好简洁中文回答",
        {},
        requester_user_id="u1",
        run_id="manual-2",
        session_id="manual-2",
        operation_id="update-operation-2",
        trace_id="trace_7",
    )
    assert repeated == updated
    assert control.records[created["id"]].version == 2

    with pytest.raises(IdempotencyConflict):
        store.update(
            created["id"],
            "同一幂等键不能改写成另一段内容",
            {},
            requester_user_id="u1",
            run_id="manual-2",
            session_id="manual-2",
            operation_id="update-operation-2",
            trace_id="trace_7_conflict",
        )

    store.delete(
        created["id"],
        requester_user_id="u1",
        run_id="manual-2",
        session_id="manual-2",
        operation_id="delete-operation-2",
        trace_id="trace_8",
    )
    store.delete(
        created["id"],
        requester_user_id="u1",
        run_id="manual-2",
        session_id="manual-2",
        operation_id="delete-operation-2",
        trace_id="trace_9",
    )
    assert control.records[created["id"]].version == 3


def test_mutation_crash_recovery_finishes_all_projections() -> None:
    control = FakeControl()
    store = build_store(FakeEngine("enterprise"), FakeEngine("cpu"), control)
    created = store.add(
        messages="用户偏好中文回答",
        infer=False,
        requester_user_id="u1",
        run_id="manual-crash",
        session_id="manual-crash",
        operation_id="manual-operation-crash",
        trace_id="trace_crash_create",
        owner=OwnerScope("PERSONAL_GLOBAL", "platform:u1", None, None),
        metadata={"source": "MANUAL"},
    )[0]

    update_operation = "update-operation-crash"
    update_binding = _update_binding(
        created["id"], "用户偏好先给结论", {}, None, None
    )
    control.operations[update_operation] = OperationState(
        "STARTED", None, NOW, update_binding
    )
    control.update_logical(
        created["id"], "用户偏好先给结论", {"operationId": update_operation}
    )

    recovered = store.update(
        created["id"],
        "用户偏好先给结论",
        {},
        requester_user_id="u1",
        run_id="manual-crash",
        session_id="manual-crash",
        operation_id=update_operation,
        trace_id="trace_crash_update",
    )
    assert recovered["metadata"]["logicalVersion"] == 2
    assert {value.projected_version for value in control.projections(created["id"]).values()} == {2}

    delete_operation = "delete-operation-crash"
    control.operations[delete_operation] = OperationState(
        "STARTED", None, NOW, f"DELETE:{created['id']}"
    )
    control.delete_logical(created["id"], {"operationId": delete_operation})

    store.delete(
        created["id"],
        requester_user_id="u1",
        run_id="manual-crash",
        session_id="manual-crash",
        operation_id=delete_operation,
        trace_id="trace_crash_delete",
    )
    projections = control.projections(created["id"]).values()
    assert {value.projected_version for value in projections} == {3}
    assert {value.status for value in projections} == {"DELETED"}


def test_projection_retry_recovers_existing_vector_and_delete_is_idempotent() -> None:
    primary = FakeEngine("enterprise")
    cpu = FakeEngine("cpu")
    control = FakeControl()
    store = build_store(primary, cpu, control)
    created = store.add(
        messages="用户偏好中文回答",
        infer=False,
        requester_user_id="u1",
        run_id="projection-crash",
        session_id="projection-crash",
        operation_id="projection-crash-add",
        trace_id="trace_projection_crash_add",
        owner=OwnerScope("PERSONAL_GLOBAL", "platform:u1", None, None),
        metadata={"source": "MANUAL"},
    )[0]

    # 模拟 infer=false 已写入 CPU collection，但控制表提交前进程退出。
    cpu_profile_key = store.profiles[1].profile.profile_key
    original_id = control.projections(created["id"])[cpu_profile_key].mem0_memory_id
    assert original_id is not None
    control.mark_projection_pending(created["id"], cpu_profile_key)
    store._project(
        control.records[created["id"]],
        store.profiles[1],
        context=GatewayCallContext(
            "u1",
            "projection-retry",
            "projection-retry",
            "projection-retry",
            "trace_projection_retry",
        ),
    )

    projection = control.projections(created["id"])[cpu_profile_key]
    assert projection.mem0_memory_id == original_id
    assert len(cpu.documents) == 1
    assert len(cpu.add_calls) == 1

    # 模拟 delete 已成功、控制表尚未更新；重放应直接把投影标为 DELETED。
    cpu.delete(original_id)
    deleted = control.delete_logical(created["id"], {"operationId": "delete-crash"})
    store._project(
        deleted,
        store.profiles[1],
        context=GatewayCallContext(
            "u1",
            "delete-retry",
            "delete-retry",
            "delete-retry",
            "trace_delete_retry",
        ),
    )
    projection = control.projections(created["id"])[cpu_profile_key]
    assert projection.status == "DELETED"
    assert projection.projected_version == deleted.version


def test_idempotency_key_is_bound_to_partition_operation_and_target() -> None:
    primary = FakeEngine("enterprise")
    cpu = FakeEngine("cpu")
    control = FakeControl()
    store = build_store(primary, cpu, control)
    created = store.add(
        messages="用户偏好中文回答",
        infer=False,
        requester_user_id="u1",
        run_id="manual-3",
        session_id="manual-3",
        operation_id="shared-operation-3",
        trace_id="trace_10",
        owner=OwnerScope("PERSONAL_GLOBAL", "platform:u1", None, None),
        metadata={"source": "MANUAL"},
    )[0]

    with pytest.raises(IdempotencyConflict):
        store.add(
            messages="不能返回另一个用户的幂等结果",
            infer=False,
            requester_user_id="u2",
            run_id="manual-4",
            session_id="manual-4",
            operation_id="shared-operation-3",
            trace_id="trace_11",
            owner=OwnerScope("PERSONAL_GLOBAL", "platform:u2", None, None),
            metadata={"source": "MANUAL"},
        )

    with pytest.raises(IdempotencyConflict):
        store.add(
            messages="同一用户也不能用相同幂等键提交另一正文",
            infer=False,
            requester_user_id="u1",
            run_id="manual-3",
            session_id="manual-3",
            operation_id="shared-operation-3",
            trace_id="trace_11_same_user",
            owner=OwnerScope("PERSONAL_GLOBAL", "platform:u1", None, None),
            metadata={"source": "MANUAL"},
        )

    store.update(
        created["id"],
        "用户偏好简洁中文回答",
        {},
        requester_user_id="u1",
        run_id="manual-3",
        session_id="manual-3",
        operation_id="shared-mutation-3",
        trace_id="trace_12",
    )
    with pytest.raises(IdempotencyConflict):
        store.delete(
            created["id"],
            requester_user_id="u1",
            run_id="manual-3",
            session_id="manual-3",
            operation_id="shared-mutation-3",
            trace_id="trace_13",
        )
