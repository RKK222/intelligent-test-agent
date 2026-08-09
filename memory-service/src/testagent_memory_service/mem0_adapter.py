"""Mem0 2.0.17 多 profile 编排：一次原生抽取、逻辑 ID、投影与 RRF 检索。"""

from __future__ import annotations

import logging
import os

# 运行时禁止遥测；memory-service 镜像已不包含模型权重，也不会访问 Hugging Face。
os.environ["MEM0_TELEMETRY"] = "False"
os.environ["MEM0_TELEMETRY_SAMPLE_RATE"] = "0"
os.environ["DO_NOT_TRACK"] = "1"
os.environ.setdefault("MEM0_DIR", "/tmp/test-agent-memory-mem0")

# Mem0 2.0.17 的 INFO/WARNING 会输出正文或实体，部分 ERROR 还会拼接模型解析异常。
# 服务边界已经把失败转换为不含正文的统一错误，因此第三方 logger 只保留 CRITICAL，
# 避免原始对话片段、模型响应或派生记忆进入容器日志。
logging.getLogger("mem0").setLevel(logging.CRITICAL)

from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from datetime import datetime, timezone
from hashlib import sha256
import json
from typing import Any, Protocol
from uuid import uuid4

from mem0 import Memory
from mem0.configs.llms.openai import OpenAIConfig
from mem0.utils.factory import EmbedderFactory, LlmFactory
from psycopg_pool import ConnectionPool

from testagent_memory_service.control import (
    LogicalRecord,
    MemoryControlStore,
    OperationState,
    OutboxItem,
)
from testagent_memory_service.embedding import GatewayEmbedding, GatewayMemoryLlm, ProviderRuntime
from testagent_memory_service.gateway import (
    GatewayCallContext,
    HmacModelGatewayClient,
    gateway_call_context,
)
from testagent_memory_service.history import PostgresHistoryManager
from testagent_memory_service.settings import EmbeddingProfile, MemoryServiceSettings


class MemoryEngine(Protocol):
    def add(self, messages: Any, **kwargs: Any) -> dict[str, Any]: ...
    def get(self, memory_id: str) -> dict[str, Any] | None: ...
    def update(
        self, memory_id: str, text: str | None = None, metadata: dict[str, Any] | None = None
    ) -> dict[str, Any]: ...
    def delete(self, memory_id: str) -> dict[str, Any]: ...
    def search(self, query: str, **kwargs: Any) -> dict[str, Any]: ...
    def get_all(self, **kwargs: Any) -> dict[str, Any]: ...


@dataclass(frozen=True, slots=True)
class ProfileEngine:
    profile: EmbeddingProfile
    engine: MemoryEngine
    history: PostgresHistoryManager | None = None


@dataclass(frozen=True, slots=True)
class OwnerScope:
    scope: str
    user_id: str | None
    agent_id: str | None
    application_id: str | None

    def partition_key(self) -> str:
        return ":".join(
            [self.scope, self.user_id or "-", self.agent_id or "-", self.application_id or "-"]
        )

    def filters(self) -> dict[str, Any]:
        result: dict[str, Any] = {"scope": self.scope}
        if self.user_id is not None:
            result["user_id"] = self.user_id
        if self.agent_id is not None:
            result["agent_id"] = self.agent_id
        if self.application_id is not None:
            result["applicationId"] = self.application_id
        if self.scope == "PERSONAL_APPLICATION" and self.application_id is not None:
            # Mem0 2.0.17 的原生抽取只用 user_id/agent_id/run_id 查找既有事实，
            # 不会把任意 metadata 纳入去重边界。用稳定、不可逆的 run_id 作为
            # Application 分区，避免 App A 的事实抑制 App B 的独立个人记忆。
            result["run_id"] = self.mem0_run_id()
        return result

    def mem0_run_id(self) -> str | None:
        if self.scope != "PERSONAL_APPLICATION" or self.application_id is None:
            return None
        digest = sha256(self.application_id.encode("utf-8")).hexdigest()[:32]
        return f"application_{digest}"


class AllEmbeddingProfilesUnavailable(RuntimeError):
    pass


class IdempotencyConflict(RuntimeError):
    """同一幂等键被不同操作、记忆或租户分区复用。"""


class Mem0MemoryStore:
    """无节点本地状态的通用记忆存储；所有副本共享 pgvector 与控制表。"""

    def __init__(
        self,
        settings: MemoryServiceSettings,
        *,
        pool: ConnectionPool | None = None,
        control: MemoryControlStore | None = None,
        profiles: tuple[ProfileEngine, ...] | None = None,
        gateway: HmacModelGatewayClient | None = None,
    ):
        self.settings = settings
        self._owns_pool = pool is None
        self.pool = pool or ConnectionPool(
            conninfo=settings.database_url.get_secret_value(),
            min_size=settings.postgres_pool_min_size,
            max_size=settings.postgres_pool_max_size,
            open=True,
        )
        self.control = control or MemoryControlStore(self.pool)
        self.gateway = gateway or HmacModelGatewayClient(settings)
        ProviderRuntime.install(self.gateway)
        self.profiles = profiles or tuple(
            self._build_profile(profile) for profile in settings.profiles()
        )
        self._by_key = {item.profile.profile_key: item for item in self.profiles}

    def close(self) -> None:
        self.gateway.close()
        if self._owns_pool:
            self.pool.close()

    def _build_profile(self, profile: EmbeddingProfile) -> ProfileEngine:
        EmbedderFactory.provider_to_class["huggingface"] = (
            "testagent_memory_service.embedding.GatewayEmbedding"
        )
        LlmFactory.provider_to_class["openai"] = (
            "testagent_memory_service.embedding.GatewayMemoryLlm",
            OpenAIConfig,
        )
        config = {
            "version": "v1.1",
            "history_db_path": ":memory:",
            "vector_store": {
                "provider": "pgvector",
                "config": {
                    "connection_string": self.settings.database_url.get_secret_value(),
                    "collection_name": profile.collection_name(),
                    "embedding_model_dims": profile.dimension,
                    "hnsw": True,
                    "diskann": False,
                    "minconn": 1,
                    "maxconn": 10,
                },
            },
            "embedder": {
                "provider": "huggingface",
                "config": {
                    "model": profile.model_id,
                    "embedding_dims": profile.dimension,
                    "model_kwargs": {
                        "timeout_seconds": self.settings.embedding_timeout_seconds,
                    },
                },
            },
            "llm": {
                "provider": "openai",
                "config": {
                    "model": self.settings.chat_model_id,
                    "api_key": "hmac-authenticated-by-memory-service",
                    "max_tokens": 2_000,
                    "temperature": 0.1,
                },
            },
        }
        memory = Memory.from_config(config)
        memory.db.close()
        history = PostgresHistoryManager(self.pool, profile.profile_key)
        memory.db = history
        return ProfileEngine(profile, memory, history)

    def add(
        self,
        *,
        messages: str | list[dict[str, str]],
        infer: bool,
        requester_user_id: str,
        run_id: str,
        session_id: str,
        operation_id: str,
        trace_id: str,
        owner: OwnerScope,
        metadata: dict[str, Any],
        chat_model_id: str | None = None,
    ) -> list[dict[str, Any]]:
        partition = owner.partition_key()
        binding = _add_binding(
            partition, messages, infer, metadata, chat_model_id
        )
        with self.control.partition_lock(partition):
            previous = _bound_operation(self.control.operation(operation_id), binding)
            if previous is not None and previous.status == "COMPLETED":
                return list(previous.result or [])
            context = GatewayCallContext(
                requester_user_id, run_id, session_id, operation_id, trace_id, chat_model_id
            )
            if previous is not None:
                # Mem0 与控制表无法组成同一数据库事务。节点若在向量写入后、控制表完成前
                # 退出，重试先按 operationId 找回已落入任一 collection 的结果，避免再次
                # 调用原生 LLM 抽取；partition advisory lock 保证此时没有同分区活跃写入。
                recovered = self._recover_operation(owner, metadata, context)
                if recovered is not None:
                    self.control.complete_operation(operation_id, recovered)
                    return recovered
                if infer and previous.native_started:
                    # LLM 调用开始后若没有可恢复向量，宁可本次不产出记忆，也不能在
                    # 节点崩溃/上游超时后对同一 operation 再执行第二次原生抽取。
                    self.control.complete_operation(operation_id, [])
                    return []
                self.control.restart_operation(operation_id, binding)
            else:
                started = _bound_operation(
                    self.control.start_operation(operation_id, binding), binding
                )
                if started is not None and started.status == "COMPLETED":
                    return list(started.result or [])
            try:
                source = self._first_available(owner, context)
                if infer:
                    self.control.mark_native_started(operation_id, binding)
                with gateway_call_context(context):
                    response = source.engine.add(
                        messages,
                        user_id=owner.user_id,
                        agent_id=owner.agent_id,
                        run_id=owner.mem0_run_id(),
                        metadata={
                            **metadata,
                            **owner.filters(),
                            "operationId": context.operation_id,
                        },
                        infer=infer,
                    )
                result = self._register_native_results(
                    source, response, owner, metadata, context
                )
                self.control.complete_operation(operation_id, result)
                return result
            except Exception as exception:
                self.control.fail_operation(operation_id, _error_code(exception))
                raise

    def _recover_operation(
        self,
        owner: OwnerScope,
        metadata: dict[str, Any],
        context: GatewayCallContext,
    ) -> list[dict[str, Any]] | None:
        """找回已写入 Mem0、但尚未完成共享幂等记录的一次学习。"""
        filters = {**owner.filters(), "operationId": context.operation_id}
        for profile in sorted(self.profiles, key=lambda value: not value.profile.primary):
            try:
                response = profile.engine.get_all(filters=filters, top_k=100)
            except Exception:
                continue
            rows = [
                row
                for row in response.get("results", [])
                if str((row.get("metadata") or {}).get("operationId", ""))
                == context.operation_id
            ]
            if not rows:
                continue
            recovered = self._register_native_results(
                profile,
                {"results": [{**row, "event": "ADD"} for row in rows]},
                owner,
                metadata,
                context,
            )
            # 同一 operation 可能一次抽取多条事实，按逻辑 ID 去重并保持稳定顺序。
            unique = {item["id"]: item for item in recovered}
            return [unique[key] for key in sorted(unique)]
        return None

    def _first_available(
        self, owner: OwnerScope, context: GatewayCallContext
    ) -> ProfileEngine:
        failures: list[Exception] = []
        selected: ProfileEngine | None = None
        for item in sorted(self.profiles, key=lambda value: not value.profile.primary):
            try:
                with gateway_call_context(context):
                    # 先只做 embedding + pgvector 探测，再选定唯一原生抽取 profile。
                    # 这样企业 embedding 不可用时会在 LLM 调用前切到 CPU，选定后失败也
                    # 不会换 profile 再执行第二次 Mem0 原生抽取。
                    item.engine.search(
                        "memory profile preflight",
                        filters=owner.filters(),
                        top_k=1,
                        threshold=1.0,
                        rerank=False,
                    )
                selected = item
                break
            except Exception as exception:
                failures.append(exception)
        if selected is None:
            cause = failures[-1] if failures else None
            raise AllEmbeddingProfilesUnavailable("所有 embedding profile 写入失败") from cause
        return selected

    def _register_native_results(
        self,
        source: ProfileEngine,
        response: dict[str, Any],
        owner: OwnerScope,
        metadata: dict[str, Any],
        context: GatewayCallContext,
    ) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        for item in response.get("results", []):
            mem0_id = str(item.get("id", ""))
            raw_content = item.get("memory")
            if (
                not mem0_id
                or not isinstance(raw_content, str)
                or not raw_content.strip()
                or str(item.get("event", "ADD")) == "NONE"
            ):
                continue
            # 不对 Mem0 原生抽取结果做 trim、重写或二次语义过滤。
            content = raw_content
            existing = self.control.find_by_projection(source.profile.profile_key, mem0_id)
            if existing is None:
                logical_id = f"lmem_{uuid4().hex}"
                record = self.control.create_logical(
                    logical_memory_id=logical_id,
                    scope=owner.scope,
                    owner_user_id=_platform_user(owner.user_id),
                    application_id=owner.application_id,
                    content=content,
                    metadata={
                        **metadata,
                        "logicalMemoryId": logical_id,
                        "requesterUserId": context.user_id,
                        "runId": context.run_id,
                        "sessionId": context.session_id,
                        "operationId": context.operation_id,
                    },
                    source_profile_key=source.profile.profile_key,
                    source_mem0_memory_id=mem0_id,
                )
            else:
                record = existing
            for target in self.profiles:
                if target.profile.profile_key == source.profile.profile_key:
                    continue
                self.control.mark_projection_pending(
                    record.logical_memory_id, target.profile.profile_key
                )
                try:
                    self._project(record, target, context)
                except Exception as exception:
                    self.control.fail_and_enqueue_projection(
                        record.logical_memory_id,
                        target.profile.profile_key,
                        "UPSERT",
                        record.version,
                        _error_code(exception),
                    )
            result.append(self._document(record))
        return result

    def get(self, logical_memory_id: str) -> dict[str, Any] | None:
        record = self.control.get_logical(logical_memory_id)
        return self._document(record) if record else None

    def update(
        self,
        logical_memory_id: str,
        content: str,
        metadata: dict[str, Any],
        *,
        scope: str | None = None,
        application_id: str | None = None,
        requester_user_id: str,
        run_id: str,
        session_id: str,
        operation_id: str,
        trace_id: str,
    ) -> dict[str, Any]:
        current = self.control.get_logical(logical_memory_id, include_deleted=True)
        if current is None:
            raise KeyError(logical_memory_id)
        with self.control.partition_lock(_record_partition(current)):
            binding = _update_binding(
                logical_memory_id, content, metadata, scope, application_id
            )
            context = GatewayCallContext(
                requester_user_id, run_id, session_id, operation_id, trace_id
            )
            previous = _bound_operation(self.control.operation(operation_id), binding)
            if previous is not None and previous.status == "COMPLETED":
                rows = list(previous.result or [])
                if len(rows) != 1 or rows[0].get("id") != logical_memory_id:
                    raise IdempotencyConflict("幂等键已用于其他记忆操作")
                return rows[0]
            latest = self.control.get_logical(logical_memory_id, include_deleted=True)
            if latest is None or latest.deleted:
                raise KeyError(logical_memory_id)
            if previous is not None and latest.metadata.get("operationId") == operation_id:
                # 逻辑表提交后、投影调度前崩溃时，重试必须重新执行幂等投影，不能只把
                # operation 标成完成而遗留永久不一致的 collection。
                self._project_all(latest, "UPSERT", context)
                recovered = self._document(latest)
                self.control.complete_operation(operation_id, [recovered])
                return recovered
            if previous is None:
                _bound_operation(self.control.start_operation(operation_id, binding), binding)
            else:
                self.control.restart_operation(operation_id, binding)
            try:
                record = self.control.update_logical(
                    logical_memory_id,
                    content,
                    {**metadata, "operationId": operation_id},
                    scope=scope,
                    application_id=application_id,
                )
                self._project_all(record, "UPSERT", context)
                result = self._document(record)
                self.control.complete_operation(operation_id, [result])
                return result
            except Exception as exception:
                self.control.fail_operation(operation_id, _error_code(exception))
                raise

    def delete(
        self,
        logical_memory_id: str,
        *,
        requester_user_id: str,
        run_id: str,
        session_id: str,
        operation_id: str,
        trace_id: str,
    ) -> None:
        current = self.control.get_logical(logical_memory_id, include_deleted=True)
        if current is None:
            raise KeyError(logical_memory_id)
        with self.control.partition_lock(_record_partition(current)):
            binding = f"DELETE:{logical_memory_id}"
            context = GatewayCallContext(
                requester_user_id, run_id, session_id, operation_id, trace_id
            )
            previous = _bound_operation(self.control.operation(operation_id), binding)
            if previous is not None and previous.status == "COMPLETED":
                if previous.result:
                    raise IdempotencyConflict("幂等键已用于其他记忆操作")
                return
            latest = self.control.get_logical(logical_memory_id, include_deleted=True)
            if latest is None:
                raise KeyError(logical_memory_id)
            if latest.deleted:
                if previous is not None and latest.metadata.get("operationId") == operation_id:
                    self._project_all(latest, "DELETE", context)
                    self.control.complete_operation(operation_id, [])
                    return
                raise KeyError(logical_memory_id)
            if previous is None:
                _bound_operation(self.control.start_operation(operation_id, binding), binding)
            else:
                self.control.restart_operation(operation_id, binding)
            try:
                record = self.control.delete_logical(
                    logical_memory_id, {"operationId": operation_id}
                )
                self._project_all(record, "DELETE", context)
                self.control.complete_operation(operation_id, [])
            except Exception as exception:
                self.control.fail_operation(operation_id, _error_code(exception))
                raise

    def _project_all(
        self, record: LogicalRecord, operation: str, context: GatewayCallContext
    ) -> None:
        for target in self.profiles:
            try:
                self._project(record, target, context)
            except Exception as exception:
                self.control.fail_and_enqueue_projection(
                    record.logical_memory_id,
                    target.profile.profile_key,
                    operation,
                    record.version,
                    _error_code(exception),
                )

    def _project(
        self, record: LogicalRecord, target: ProfileEngine, context: GatewayCallContext
    ) -> None:
        projection = self.control.projections(record.logical_memory_id).get(
            target.profile.profile_key
        )
        if projection and projection.projected_version >= record.version and projection.status in {
            "SYNCED",
            "DELETED",
        }:
            return
        with gateway_call_context(context):
            if record.deleted:
                if projection and projection.mem0_memory_id:
                    # delete 已在向量库成功、控制表尚未提交时，outbox 会重放同一版本。
                    # 先读后删，把“目标已不存在”视为成功，避免任务永久进入 DEAD。
                    if target.engine.get(projection.mem0_memory_id) is not None:
                        target.engine.delete(projection.mem0_memory_id)
                self.control.mark_projection_synced(
                    record.logical_memory_id,
                    target.profile.profile_key,
                    projection.mem0_memory_id if projection else None,
                    record.version,
                    deleted=True,
                )
                return
            owner = _owner_from_record(record)
            projected_metadata = {
                **record.metadata,
                **owner.filters(),
                "logicalMemoryId": record.logical_memory_id,
                "logicalVersion": record.version,
            }
            mem0_id = projection.mem0_memory_id if projection else None
            recovered_ids: list[str] = []
            if mem0_id and target.engine.get(mem0_id) is None:
                # 外部误删或前一次失败留下陈旧映射时，按缺失投影重新建立。
                mem0_id = None
            if mem0_id is None:
                # infer=false 已写入、但 mark_projection_synced 尚未提交是唯一会导致
                # 重复向量的崩溃窗口。logicalMemoryId 由平台生成且分区内串行，可用来
                # 找回原向量；不得再次 add，更不能再次调用原生 LLM 抽取。
                recovered_ids = self._projected_mem0_ids(target, owner, record.logical_memory_id)
                mem0_id = recovered_ids[0] if recovered_ids else None
            if mem0_id:
                target.engine.update(
                    mem0_id,
                    text=record.content,
                    metadata=projected_metadata,
                )
            else:
                response = target.engine.add(
                    record.content,
                    user_id=owner.user_id,
                    agent_id=owner.agent_id,
                    run_id=owner.mem0_run_id(),
                    metadata=projected_metadata,
                    infer=False,
                )
                rows = response.get("results", [])
                if len(rows) != 1 or not rows[0].get("id"):
                    raise RuntimeError("Mem0 投影未返回唯一 ID")
                mem0_id = str(rows[0]["id"])
            # 兼容旧版本在崩溃重试中已经产生的重复项。先清理再提交控制状态；若清理
            # 中断，下次仍会按 logicalMemoryId 找回并继续，不会丢失主向量。
            for duplicate_id in recovered_ids[1:]:
                if target.engine.get(duplicate_id) is not None:
                    target.engine.delete(duplicate_id)
            self.control.mark_projection_synced(
                record.logical_memory_id,
                target.profile.profile_key,
                mem0_id,
                record.version,
            )

    @staticmethod
    def _projected_mem0_ids(
        target: ProfileEngine, owner: OwnerScope, logical_memory_id: str
    ) -> list[str]:
        response = target.engine.get_all(
            filters={**owner.filters(), "logicalMemoryId": logical_memory_id},
            top_k=100,
        )
        ids = {
            str(row["id"])
            for row in response.get("results", [])
            if row.get("id")
            and str((row.get("metadata") or {}).get("logicalMemoryId", ""))
            == logical_memory_id
        }
        return sorted(ids)

    def retry_projection(self, item: OutboxItem, worker_id: str) -> None:
        record = self.control.get_logical(item.logical_memory_id, include_deleted=True)
        target = self._by_key.get(item.target_profile_key)
        if record is None or target is None or record.version < item.target_version:
            self.control.complete_outbox(item.outbox_id, worker_id)
            return
        context = GatewayCallContext(
            record.metadata.get("requesterUserId") or record.owner_user_id or "memory-system",
            record.metadata.get("runId") or "memory-projection",
            record.metadata.get("sessionId") or "memory-projection",
            f"projection:{item.outbox_id}",
            f"trace_{uuid4().hex}",
        )
        try:
            with self.control.partition_lock(_record_partition(record)):
                self._project(record, target, context)
            self.control.complete_outbox(item.outbox_id, worker_id)
        except Exception as exception:
            self.control.mark_projection_failed(
                record.logical_memory_id, target.profile.profile_key, _error_code(exception)
            )
            self.control.retry_outbox(
                item.outbox_id, worker_id, _error_code(exception), item.attempts
            )

    def history(self, logical_memory_id: str) -> list[dict[str, Any]]:
        if self.control.get_logical(logical_memory_id, include_deleted=True) is None:
            raise KeyError(logical_memory_id)
        return self.control.logical_history(logical_memory_id)

    def search(
        self,
        *,
        query: str,
        scopes: list[OwnerScope],
        top_k: int,
        threshold: float,
        requester_user_id: str,
        run_id: str,
        session_id: str,
        operation_id: str,
        trace_id: str,
    ) -> list[dict[str, Any]]:
        context = GatewayCallContext(
            requester_user_id,
            run_id,
            session_id,
            operation_id,
            trace_id,
            embedding_timeout_seconds=self.settings.search_profile_timeout_seconds,
        )
        profile_rankings: dict[str, list[dict[str, Any]]] = {}
        profile_success: set[str] = set()
        jobs: dict[Any, tuple[ProfileEngine, OwnerScope]] = {}
        with ThreadPoolExecutor(max_workers=max(1, len(self.profiles) * len(scopes))) as executor:
            for profile in self.profiles:
                for scope in scopes:
                    future = executor.submit(
                        self._search_one,
                        profile,
                        scope,
                        query,
                        top_k,
                        threshold,
                        context,
                    )
                    jobs[future] = (profile, scope)
            for future in as_completed(jobs):
                profile, _ = jobs[future]
                try:
                    rows = future.result()
                    profile_success.add(profile.profile.profile_key)
                    profile_rankings.setdefault(profile.profile.profile_key, []).extend(rows)
                except Exception:
                    continue
        if not profile_success:
            raise AllEmbeddingProfilesUnavailable("所有 embedding profile 检索失败")
        return self._rrf(profile_rankings, top_k)

    def _search_one(
        self,
        profile: ProfileEngine,
        scope: OwnerScope,
        query: str,
        top_k: int,
        threshold: float,
        context: GatewayCallContext,
    ) -> list[dict[str, Any]]:
        with gateway_call_context(context):
            response = profile.engine.search(
                query,
                filters=scope.filters(),
                top_k=top_k,
                threshold=threshold,
                rerank=False,
            )
        rows = list(response.get("results", []))
        rows.sort(key=lambda item: float(item.get("score") or 0.0), reverse=True)
        ids = [str(item.get("id", "")) for item in rows if item.get("id")]
        resolved = self.control.resolve_logical_ids(profile.profile.profile_key, ids)
        result: list[dict[str, Any]] = []
        for item in rows:
            mem0_id = str(item.get("id", ""))
            logical_id = resolved.get(mem0_id)
            if logical_id:
                result.append({**item, "logicalMemoryId": logical_id})
        return result

    def _rrf(
        self, profile_rankings: dict[str, list[dict[str, Any]]], top_k: int
    ) -> list[dict[str, Any]]:
        fused: dict[str, dict[str, Any]] = {}
        for profile_key, rows in profile_rankings.items():
            deduplicated: list[dict[str, Any]] = []
            seen: set[str] = set()
            for row in sorted(
                rows, key=lambda item: float(item.get("score") or 0.0), reverse=True
            ):
                logical_id = str(row["logicalMemoryId"])
                if logical_id not in seen:
                    seen.add(logical_id)
                    deduplicated.append(row)
            for rank, row in enumerate(deduplicated, start=1):
                logical_id = str(row["logicalMemoryId"])
                value = fused.setdefault(
                    logical_id,
                    {
                        "logicalMemoryId": logical_id,
                        "rrfScore": 0.0,
                        "profileRanks": {},
                    },
                )
                value["rrfScore"] += 1.0 / (60.0 + rank)
                value["profileRanks"][profile_key] = rank
        ordered = sorted(
            fused.values(), key=lambda item: (-float(item["rrfScore"]), item["logicalMemoryId"])
        )[:top_k]
        result: list[dict[str, Any]] = []
        for item in ordered:
            record = self.control.get_logical(str(item["logicalMemoryId"]))
            if record is None:
                continue
            result.append(
                self._document(
                    record,
                    score=float(item["rrfScore"]),
                    extra_metadata={"profileRanks": item["profileRanks"]},
                )
            )
        return result

    def readiness(self) -> dict[str, bool]:
        status: dict[str, bool] = {}
        for profile in self.profiles:
            try:
                # readiness 只验证共享 collection 可读，不能伪造平台用户调用 Java 模型
                # 网关。Embedding 的真实可用性由带真实用户身份的学习/检索请求判定并
                # 自动切换 profile；因此这里不会产生探测向量或污染模型调用审计。
                profile.engine.get_all(
                    filters={"user_id": "__memory_readiness__"},
                    top_k=1,
                )
                status[profile.profile.profile_key] = True
            except Exception:
                status[profile.profile.profile_key] = False
        return status

    def raw_message_count(self) -> int:
        return 0

    def _document(
        self,
        record: LogicalRecord,
        score: float | None = None,
        extra_metadata: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        projections = self.control.projections(record.logical_memory_id)
        metadata = {
            **record.metadata,
            **(extra_metadata or {}),
            "logicalMemoryId": record.logical_memory_id,
            "logicalVersion": record.version,
            "projectionStatus": {
                key: value.status for key, value in projections.items()
            },
        }
        document: dict[str, Any] = {
            "id": record.logical_memory_id,
            "memory": record.content,
            "content": record.content,
            "metadata": metadata,
            "created_at": record.created_at.isoformat(),
            "updated_at": record.updated_at.isoformat(),
            "createdAt": record.created_at.isoformat(),
            "updatedAt": record.updated_at.isoformat(),
        }
        if score is not None:
            document["score"] = score
        return document


def _platform_user(value: str | None) -> str | None:
    if value is None:
        return None
    return value.removeprefix("platform:")


def _owner_from_record(record: LogicalRecord) -> OwnerScope:
    if record.scope == "TEAM_APPLICATION":
        return OwnerScope(
            record.scope,
            None,
            f"team:{record.application_id}",
            record.application_id,
        )
    return OwnerScope(
        record.scope,
        f"platform:{record.owner_user_id}",
        None,
        record.application_id,
    )


def _record_partition(record: LogicalRecord) -> str:
    return _owner_from_record(record).partition_key()


def _error_code(exception: Exception) -> str:
    return type(exception).__name__.upper()[:64]


def _bound_operation(state: OperationState | None, expected_binding: str) -> OperationState | None:
    if state is not None and state.binding != expected_binding:
        raise IdempotencyConflict("幂等键已绑定其他操作或记忆分区")
    return state


def _add_binding(
    partition: str,
    messages: str | list[dict[str, str]],
    infer: bool,
    metadata: dict[str, Any],
    chat_model_id: str | None,
) -> str:
    return f"ADD:{partition}:{_payload_digest({
        'messages': messages,
        'infer': infer,
        'metadata': metadata,
        'chatModelId': chat_model_id,
    })}"


def _update_binding(
    logical_memory_id: str,
    content: str,
    metadata: dict[str, Any],
    scope: str | None,
    application_id: str | None,
) -> str:
    return f"UPDATE:{logical_memory_id}:{_payload_digest({
        'content': content,
        'metadata': metadata,
        'scope': scope,
        'applicationId': application_id,
    })}"


def _payload_digest(value: dict[str, Any]) -> str:
    encoded = json.dumps(
        value, ensure_ascii=False, separators=(",", ":"), sort_keys=True
    ).encode("utf-8")
    return sha256(encoded).hexdigest()
