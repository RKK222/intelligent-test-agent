# Phase 02 持久化和执行节点路由详细设计

## 目标

Phase 02 落地 Workspace、Session、Run、RunEvent、ExecutionNode 和 RoutingDecision 的数据库结构、Repository 端口与执行节点路由策略。该阶段不新增对外 HTTP API，所有能力先作为后端内部稳定边界，供 Phase 04 的 Runtime API 编排复用。

## 领域边界

- `test-agent-domain` 保持纯领域层，新增 Repository 端口、`RunEventDraft` 和 `ExecutionNodeRouter`，不依赖 Spring、JDBC、Flyway 或 generated SDK。
- Workspace、Session、Run、ExecutionNode 增加持久化必要字段：状态、创建时间、更新时间和 traceId；ExecutionNode 额外记录权重、最近心跳和能力标签。
- `RunEvent` 仍是 append-only 已落库事件；`RunEventDraft` 表达待追加事件，由持久化层分配 `eventId` 和同一 run 内单调递增的 `seq`。
- `ExecutionNodeRouter` 只从 READY 且仍有容量的候选节点中选择执行节点；初版选择最低运行数、较高权重、较早更新时间的节点，无可用节点时抛出 `OPENCODE_UNAVAILABLE`。

## 数据库设计

Flyway V1 migration 创建以下表：

- `workspaces`：保存 `workspace_id`、名称、根路径、状态、traceId、创建和更新时间。
- `sessions`：保存 `session_id`、所属 `workspace_id`、标题、状态、traceId、创建和更新时间。
- `runs`：保存 `run_id`、所属 session/workspace、状态、traceId、创建和更新时间。
- `run_events`：保存 `event_id`、`run_id`、`seq`、稳定事件类型、traceId、发生时间和 JSON payload；`(run_id, seq)` 唯一，读取使用 `seq > Last-Event-ID`。
- `execution_nodes`：保存 `execution_node_id`、baseUrl、健康状态、运行数、最大运行数、权重、最近心跳、能力 JSON、traceId、创建和更新时间。
- `routing_decisions`：保存 run 到 execution node 的路由结果、原因、traceId 和决策时间。

所有表使用自增 surrogate PK，业务 ID 使用唯一约束。JSON 字段以文本保存，兼容本地 H2 测试和 PostgreSQL；未来切换 PostgreSQL JSONB 时先新增兼容读取，再做分阶段迁移。

## Repository 设计

- domain 定义 `WorkspaceRepository`、`SessionRepository`、`RunRepository`、`RunEventRepository`、`ExecutionNodeRepository`、`RoutingDecisionRepository` 端口。
- persistence 使用 Spring `JdbcClient` 实现端口，并在模块内部完成 domain 对象与表字段映射。
- `RunEventRepository.append(RunEventDraft)` 在事务内读取目标 run 当前最大 seq 后插入新事件；后续并发优化可改为数据库序列或锁表策略，不影响端口。
- 事件回放通过 `findByRunIdAfter(runId, lastSeq, limit)` 增量查询，必须有明确 limit，避免一次性加载大事件流。

## 验收

- 空库执行 Flyway V1 migration 成功。
- Repository 测试覆盖保存、查询、RunEvent append-only、按 `runId + seq` 续读和唯一约束。
- domain 路由测试覆盖健康节点选择和无可用节点错误。
- 文档同步 API、事件、数据变更、测试规范和模块 README/PACKAGE.md。
