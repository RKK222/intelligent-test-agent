# Phase 02 持久化和执行节点路由

## 阶段目标

落地 Workspace、Session、Run、RunEvent、ExecutionNode 的数据库结构、Repository 和执行节点路由策略，为多 opencode server 节点执行做准备。

## 可验收功能清单

1. Flyway migration 创建核心表。
2. Repository 可保存和查询 Workspace、Session、Run、RunEvent、ExecutionNode。
3. RunEvent 使用 append-only 模型。
4. SessionRoute 或 RoutingDecision 能选择执行节点。
5. 数据库字段变更有兼容策略。

## 修改项目

- `backend/test-agent-persistence`
- `backend/test-agent-domain`
- `backend/test-agent-common`
- `backend/test-agent-app`
- `docs/backend/data-change-standards.md`
- `docs/api/backend-api.md`

## 实现功能

- 表结构包含主键、业务 id、状态、创建时间、更新时间、traceId 和必要索引。
- RunEvent 按 runId、sequence 或 eventId 支持顺序读取。
- ExecutionNode 记录 baseUrl、健康状态、权重、最近心跳和能力标签。
- 路由策略支持最简单的健康节点选择，并为后续负载策略预留接口。

## 验收方式

- Flyway migration 在空库可执行。
- Repository 单元测试或数据层测试通过。
- 不能只改实体类而没有 migration。
- 查询 RunEvent 支持按最后事件 id 之后继续读取。
- 文档说明新增表和字段的兼容策略。
