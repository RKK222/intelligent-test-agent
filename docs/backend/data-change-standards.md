# 数据变更规范

本规范约束数据库表结构、字段、索引和持久化映射变更。

## Migration

1. 数据库表结构变更必须有 Flyway migration。
2. 不允许只改实体类、Repository 或 SQL 映射。
3. migration 文件命名必须稳定、递增、可重复执行环境构建。
4. migration 必须能从空库执行到最新版本。

## 兼容性

1. 新字段优先允许空值或提供默认值，避免破坏旧数据。
2. 删除字段必须先完成读取兼容和数据迁移，再分阶段删除。
3. 枚举值、状态值变更必须考虑旧数据。
4. 唯一约束和索引变更必须评估历史数据冲突。

## 测试

1. 新增表、字段、索引、约束必须有集成验证。
2. 数据迁移脚本必须验证成功路径和关键失败场景。
3. Repository 变更必须验证映射字段、查询条件、分页和排序。

## 文档

数据库变更必须同步：

- `docs/backend/data-change-standards.md` 中相关说明。
- `docs/database/migrations.md`，如果该文档已创建。
- 相关模块 README 或 PACKAGE.md。
- API 或事件文档中暴露的数据字段。

## 当前 migration

- `V1__create_core_tables.sql` 创建 `workspaces`、`sessions`、`runs`、`run_events`、`execution_nodes`、`routing_decisions`。
- `V2__create_session_messages.sql` 创建 `session_messages`，保存会话消息并通过 `(session_id, created_at, id)` 支持分页读取。
- `V3__add_session_opencode_mapping.sql` 为 `sessions` 增加可空的远端 opencode session/node 映射列，并通过成对 check、节点外键和唯一索引保证内部映射一致。
- 所有表使用数据库自增 surrogate PK，业务 ID 使用唯一约束并带稳定前缀；API 和事件不得暴露 surrogate PK。
- `run_events` 使用 `(run_id, seq)` 唯一约束实现同一 Run 内 append-only 顺序读取；Repository 必须处理同一 Run 并发追加时的唯一约束冲突并重试分配 seq。
- payload 和 capabilities 初版以 JSON 文本保存，兼容 H2 测试和 PostgreSQL；未来改为 JSONB 必须先保持双读兼容。
