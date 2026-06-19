# 包说明：com.example.testagent.persistence

## 职责

持久化包，负责数据库映射、Repository 实现、Flyway migration 协作、Redis 可选适配和查询优化。

## 不负责

- 不承载 Controller。
- 不直接调用 generated SDK 或 opencode client。
- 不实现领域状态机。

## 主要程序清单

- `package-info.java`：说明 persistence 包是持久化适配边界。
- `JdbcWorkspaceRepository`：实现 Workspace 持久化端口。
- `JdbcSessionRepository`：实现 Session 持久化端口，并保存平台 session 到远端 opencode session/node 的内部映射。
- `JdbcSessionMessageRepository`：实现 SessionMessage 保存、查询、分页和计数。
- `JdbcRunRepository`：实现 Run 持久化端口。
- `JdbcRunEventRepository`：实现 RunEvent append-only 追加和增量读取；并发追加时依赖 `(run_id, seq)` 唯一约束冲突后重试来保持 seq 单调且不重复。
- `JdbcExecutionNodeRepository`：实现执行节点保存和可路由节点查询。
- `JdbcRoutingDecisionRepository`：实现路由决策保存和查询。
- `db/migration/V1__create_core_tables.sql`：创建核心业务表和索引。
- `db/migration/V2__create_session_messages.sql`：创建会话消息表和分页索引。
- `db/migration/V3__add_session_opencode_mapping.sql`：为 sessions 增加可空内部 opencode 映射列、成对 check、节点外键和索引。
- `db/migration/V4__add_session_management_fields.sql`：为 sessions 增加 pinned 字段和 ACTIVE 会话排序索引。
- 后续可新增 SQL 查询、migration 相关适配、Redis 限流或缓存实现。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- Spring Data JDBC。
- Flyway。
- PostgreSQL driver。
- Druid Spring Boot starter。
- Redis 客户端或 Spring Data Redis。

## 禁止依赖

- `test-agent-app` Controller。
- generated SDK。
- opencode client facade。
- 前端 DTO。

## 上游调用方

- `test-agent-app` 应用服务。
- event 模块通过接口追加和查询事件。

## 下游依赖

- 数据库。
- Redis，可选。
- domain 模型。

## 测试位置

- persistence 模块集成测试。
- Repository、migration、唯一约束、事务、分页和排序测试；当前使用 H2 PostgreSQL 模式执行 Flyway migration。
- RunEvent 测试必须覆盖同一 run 的并发 append，防止 stream 事件和取消事件同时落库时重复分配 seq。
- Druid 连接池配置测试；当前验证 `spring.datasource.druid.*` 可绑定为 Druid DataSource，且 Web 控制台默认关闭。

## 修改时必须同步更新

- `backend/test-agent-persistence/README.md`。
- `docs/backend/data-change-standards.md`。
- `docs/database/migrations.md`，如果存在。
- API 或事件文档中暴露的数据字段。
