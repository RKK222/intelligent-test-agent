# test-agent-persistence

## 工程定位

持久化模块，负责数据库、迁移、Repository 和缓存访问适配。

## 技术栈

- Java 21
- Spring Data JDBC
- PostgreSQL
- Flyway Core + PostgreSQL database support
- Druid JDBC 连接池
- Redis optional
- Maven library jar

## 主要职责

- Workspace、Session、AgentSessionBinding、SessionMessage、Run、RunEvent、ExecutionNode、RoutingDecision、应用配置管理等持久化。
- Flyway migration，包含 PostgreSQL 16 所需的 Flyway database support。
- Repository 实现和数据库映射。
- Redis 限流、幂等或缓存能力的可选适配。

## 已有实现

- `V1__create_core_tables.sql`：创建 Workspace、Session、Run、RunEvent、ExecutionNode、RoutingDecision 核心表。
- `V2__create_session_messages.sql`：创建 SessionMessage 表和按 session 分页索引。
- `V3__add_session_opencode_mapping.sql`：为 Session 增加可空的远端 opencode session/node 内部映射列、约束和索引。
- `V4__add_session_management_fields.sql`：为 Session 增加 `pinned` 字段和 ACTIVE 会话列表排序索引。
- `V5__create_user_and_auth_tables.sql`：创建用户、登录日志、字典和用户角色表。
- `V6__create_agent_session_bindings.sql`：创建通用 agent session 绑定表，并从旧 `sessions.opencode_*` 字段回填 `opencode` 绑定。
- `V7__create_configuration_management_tables.sql`：创建应用定义、应用成员、代码库、应用仓库关联、应用工作空间和个人 SSH key 配置表。
- `JdbcWorkspaceRepository`、`JdbcSessionRepository`、`JdbcRunRepository`、`JdbcRunEventRepository`、`JdbcExecutionNodeRepository`、`JdbcRoutingDecisionRepository`。
- `JdbcAgentSessionBindingRepository`：实现按 `(sessionId, agentId)` 和 `(agentId, remoteSessionId)` 查询、upsert 通用远端 session 绑定。
- `JdbcSessionMessageRepository`：实现会话消息保存、查询、分页和计数。
- `JdbcConfigurationManagementRepository`：实现配置管理表的应用只读查询、成员逻辑删除、仓库关联、工作空间和个人 SSH key 元数据持久化。
- RunEvent append-only：持久化层分配 `eventId` 和同一 run 内单调递增 `seq`，并发追加时通过 `(run_id, seq)` 唯一约束冲突后重读重试，支持 `runId + lastSeq` 增量读取。

## 测试环境 PostgreSQL

`test-agent-app` 的 `test` profile 会通过环境变量装配 PostgreSQL 测试库，并复用本模块 `db/migration` 下的 Flyway migration。持久化模块提供 Druid starter 依赖，实际连接信息和连接池大小由应用 profile 配置注入，不保存环境专属账号、密码或主机地址。

## 测试覆盖

- `JdbcRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖 Workspace、Session、AgentSessionBinding、SessionMessage、Run、RunEvent、ExecutionNode、RoutingDecision 的保存和读取。
- RunEvent 覆盖 append-only seq 单调递增、并发追加唯一性、`runId + lastSeq` 增量读取和 `(run_id, seq)` 唯一约束。
- Session 覆盖远端 opencode 映射、全局搜索、置顶排序、工作区会话分页和归档过滤。
- AgentSessionBinding 覆盖 upsert、按 agent 查询、远端 session 唯一约束和从旧 opencode 字段回填。
- ConfigurationManagement 覆盖 V7 migration、成员逻辑删除恢复、应用与仓库多对多关联、应用工作空间保存和用户单 SSH key 唯一约束。
- Session 全局分页在空搜索条件下不会绑定可空 query pattern，避免 PostgreSQL 无法推断 null 参数类型。
- ExecutionNode 覆盖可路由节点过滤：仅 READY 且 `running_runs < max_runs`，并按负载、权重、更新时间稳定排序。
- `DruidDataSourceConfigurationTest` 覆盖 Druid DataSource 绑定和 Web 控制台默认关闭。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- Spring Data JDBC。
- Flyway、Flyway PostgreSQL database support、PostgreSQL、Druid、Redis。

## 禁止依赖

- `test-agent-api` 和业务模块通过 domain 端口调用。
- generated SDK。
- opencode client facade。

## 后续 AI 编码指引

新增表结构、Repository、数据库映射和 migration 时改这里。不要把任务状态机或 HTTP API 编排逻辑放进本模块。
JSON payload/capabilities 当前以文本列保存，未来切换 PostgreSQL JSONB 必须同步兼容策略和测试。
`agent_session_bindings` 是新链路的 agent 远端 session 绑定主数据源；Session 的 `opencode_session_id` 与 `opencode_execution_node_id` 仅作为 `opencode` 兼容字段，新增 agent 不得继续扩展 `sessions` 列，新增查询或导出时不得默认暴露给前端 DTO；`pinned` 是平台 Session API 字段，默认旧数据未置顶。
RunEvent 追加可能来自 opencode stream、取消和 Diff 动作等多个线程；修改 `JdbcRunEventRepository` 时必须保留并发 append 下 seq 单调且不重复的测试。
