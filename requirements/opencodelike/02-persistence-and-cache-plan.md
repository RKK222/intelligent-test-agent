# 02 Persistence And Cache Plan

## 目标

DB 作为 Run scope 和断线恢复事实源；Redis 只作为运行中缓存、pending child event buffer 和 dedup。

## 已落地

- Flyway migration：`V20260703141000__create_run_session_scopes.sql`。
- 新表：
  - `run_session_scopes`
  - `run_session_scope_sessions`
- `run_events` 预留可空 scope/dedup 列：
  - `root_session_id`
  - `session_id`
  - `parent_session_id`
  - `is_child_session`
  - `scope_version`
  - `task_message_id`
  - `task_part_id`
  - `task_call_id`
  - `raw_event_id`
- `raw_event_id` 缺失保持 `NULL`，唯一索引为 `(run_id, session_id, raw_event_id)`，避免 `"unknown"` 误去重。
- metadata 使用 `metadata_json text`，不使用 JSONB。
- 新增 `RunSessionScopeMapper.xml` 和 `MyBatisRunSessionScopeRepository`，新增 SQL 走 MyBatis XML。
- 集成测试覆盖 H2 PostgreSQL 模式空库 Flyway + MyBatis upsert/query。

## 未落地但保留设计

- `JdbcRunEventRepository` 尚未迁移到 MyBatis，因此本批只预留 `run_events` scope 列，尚未在 append 时写入结构化列。
- Redis pending buffer key 建议：
  - `test-agent:run-scope:{runId}:pending:{sessionId}`
  - value 为 raw/mapped event 简要 JSON，TTL 30 分钟。
- Redis dedup key 建议：
  - `test-agent:run-scope:{runId}:dedup:{sessionId}:{rawEventId}`
  - rawEventId 缺失时不写 dedup key，只依赖 DB seq。
- Redis scope cache key 建议：
  - `test-agent:run-scope:{runId}:sessions`
  - DB 仍为恢复事实源；服务重启后从 DB 重建 cache。

## 后续批次

1. RunEvent Repository MyBatis 化。
   - append 时写入 scope 列和 raw_event_id。
   - 保留 `(run_id, seq)` 并发唯一性重试。

2. Redis pending/drain。
   - child 事件早于 discovery 时进入 pending。
   - child 纳入 scope 后 drain 并按原事件时间顺序映射。

3. Redis dedup。
   - 对 rawEventId 非空事件做短 TTL 去重。
   - DB unique index 作为最终幂等兜底。
