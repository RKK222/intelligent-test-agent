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

- Workspace、Session、AgentSessionBinding、SessionMessage、Run、RunEvent、ExecutionNode、RoutingDecision、opencode 用户进程管理拓扑、应用配置管理、应用版本工作区、个人工作区和定时任务框架等持久化。
- Flyway migration，包含 PostgreSQL 16 所需的 Flyway database support。
- Repository 实现和数据库映射。
- Redis 限流、幂等、缓存或运行心跳能力的可选适配。

## 已有实现

- `V1__create_core_tables.sql`：创建 Workspace、Session、Run、RunEvent、ExecutionNode、RoutingDecision 核心表。
- `V2__create_session_messages.sql`：创建 SessionMessage 表和按 session 分页索引。
- `V3__add_session_opencode_mapping.sql`：为 Session 增加可空的远端 opencode session/node 内部映射列、约束和索引。
- `V4__add_session_management_fields.sql`：为 Session 增加 `pinned` 字段和 ACTIVE 会话列表排序索引。
- `V5__create_user_and_auth_tables.sql`：创建用户、登录日志、字典和用户角色表。
- `V6__create_agent_session_bindings.sql`：创建通用 agent session 绑定表，并从旧 `sessions.opencode_*` 字段回填 `opencode` 绑定。
- `V7__create_configuration_management_tables.sql`：创建应用定义、应用成员、代码库、应用仓库关联、应用工作空间和个人 SSH key 配置表。
- `V8__grant_default_user_super_admin.sql`：为本地默认用户 `888888888` 幂等授予 `SUPER_ADMIN`。
- `V9__create_managed_workspace_tables.sql`：创建应用版本工作区、个人工作区、最近使用偏好和同步审计表。
- `V10__seed_fcoss_application.sql`：本地开发 F-COSS 应用种子数据，保留 V10 版本以兼容已应用该 seed 的历史本地库。
- `V16__add_message_and_run_usage_fields.sql`：为 `session_messages` 和 `runs` 增加 run/remote message/parts/token/cost 快照字段及 active-run 查询索引。
- `V14__create_opencode_process_management_tables.sql`：创建 Linux 服务器、后端 Java 进程、opencode 容器、容器管理进程、管理进程连接、用户专属 opencode server 进程和用户绑定表。
- `V15__add_opencode_process_id_check_constraints.sql`：为 opencode 进程管理表加 `process_id` 前缀、IPv4、状态、port、baseUrl 形状等 CHECK 约束。
- `V20260625184300__create_scheduler_framework_tables.sql`：创建 scheduler 任务定义、用户级计划、运行记录表，并给 sessions/runs/session_messages 增加来源预留字段；V17 之后新增 migration 统一使用 14 位时间戳版本，避免多人并行开发抢数字版本。
- `V17__seed_local_opencode_machine_for_default_user.sql`：本地开发环境预置一台 `127.0.0.1` 的 opencode 机器并绑定默认开发用户。
- `V14__create_opencode_process_management_tables.sql`：创建 Linux 服务器、后端 Java 进程、opencode 容器、容器管理进程、管理进程连接、用户专属 opencode server 进程和用户绑定表。V10 已用于 F-COSS 本地种子数据，运行管理表使用后续版本避免 Flyway 版本冲突。
- `V17__seed_local_opencode_machine_for_default_user.sql`：本地开发环境预置一台 `127.0.0.1` 的 opencode 机器（Linux 服务器 + 容器 + 管理进程）并把默认开发用户 `usr_test_dev` 绑定到端口 4096 的本地 opencode server，重复执行安全；如果历史本地库已存在同一 `linux_server_id + port` 的 opencode 进程，迁移会复用该进程完成默认用户绑定，避免端口唯一约束导致启动失败；`OpencodeManagerBackendConnection` 的 `backend_process_id` 由后端 `BackendJavaProcessLifecycleService.registerHeartbeat` 在启动时按本实例 ID 自举补齐。
- 在 `application-local.yml` 启用 `test-agent.opencode.manager-control.gateway-mode=local`（`TEST_AGENT_OPENCODE_GATEWAY_MODE` 覆盖）后，`LocalOpencodeProcessManagerGateway` 直连 `opencode_server_processes.baseUrl` 跑 HTTP GET 做健康检测，`startProcess` 走占位返回；该模式配合 V17 可让前台 `888888888` 登录后右侧对话窗口的 opencode 进程状态落到 READY，不必额外启动 opencode-manager 容器。生产 profile 不配置此开关时，`SocketOpencodeProcessManagerGateway` 走 manager WebSocket，行为与 V17 之前完全一致。
- `JdbcWorkspaceRepository`、`JdbcSessionRepository`、`JdbcRunRepository`、`JdbcRunEventRepository`、`JdbcExecutionNodeRepository`、`JdbcRoutingDecisionRepository`。
- `JdbcAgentSessionBindingRepository`：实现按 `(sessionId, agentId)` 和 `(agentId, remoteSessionId)` 查询、upsert 通用远端 session 绑定。
- `JdbcSessionMessageRepository`：实现会话消息保存、按远端 messageId 幂等查询、分页和计数。
- `JdbcConfigurationManagementRepository`：实现配置管理表的应用只读查询、成员逻辑删除、仓库关联、工作空间和个人 SSH key 元数据持久化。
- `JdbcManagedWorkspaceRepository`：实现应用版本工作区、个人工作区、最近使用偏好和同步审计持久化。
- `JdbcOpencodeProcessManagementRepository`：实现 opencode 用户进程管理拓扑、用户进程、用户绑定持久化，以及超级管理员运行管理页需要的拓扑列表、连接列表、进程分页筛选和绑定关联查询。
- `RedisOpencodeProcessHeartbeatStore`：在 Redis 启用时保存 Java 后端和 opencode server 进程运行心跳，心跳 key 5 分钟 TTL，供运行管理页跨后端实例识别活进程；Redis 未启用时由 `NoopOpencodeProcessHeartbeatStore` 降级。
- `JdbcScheduledTaskRepository`：实现 scheduler 任务定义、用户计划、运行记录、due task 查询和 pending run 查询。
- RunEvent append-only：持久化层分配 `eventId` 和同一 run 内单调递增 `seq`，并发追加时通过 `(run_id, seq)` 唯一约束冲突后重读重试，支持 `runId + lastSeq` 增量读取。

## 测试环境 PostgreSQL

`test-agent-app` 的 `test` profile 会通过环境变量装配 PostgreSQL 测试库，并复用本模块 `db/migration` 下的 Flyway migration。持久化模块提供 Druid starter 依赖，实际连接信息和连接池大小由应用 profile 配置注入，不保存环境专属账号、密码或主机地址。

## 测试覆盖

- `JdbcRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖 Workspace、Session、AgentSessionBinding、SessionMessage、Run、RunEvent、ExecutionNode、RoutingDecision 的保存和读取。
- SessionMessage/Run 覆盖 V16 token/cost 字段读写、parts_json 兼容、按 `(sessionId, remoteMessageId)` 查询以及最近非终态 Run 查询。
- RunEvent 覆盖 append-only seq 单调递增、并发追加唯一性、`runId + lastSeq` 增量读取和 `(run_id, seq)` 唯一约束。
- Session 覆盖远端 opencode 映射、全局搜索、置顶排序、工作区会话分页和归档过滤。
- AgentSessionBinding 覆盖 upsert、按 agent 查询、远端 session 唯一约束和从旧 opencode 字段回填。
- ConfigurationManagement 覆盖 V7 migration、V8 默认用户授权、成员逻辑删除恢复、应用与仓库多对多关联、应用工作空间保存和用户单 SSH key 唯一约束。
- ManagedWorkspace 覆盖 V9 migration、版本工作区唯一性、个人空间名称唯一性、最近使用偏好和同步审计保存。
- OpencodeProcessManagement 覆盖 V14 migration、拓扑读写、健康容器查询、运行管理拓扑列表、manager-backend 连接列表、opencode server 进程分页筛选、绑定关联查询、用户绑定唯一约束、服务器端口唯一约束和容器管理进程一对一约束。
- ScheduledTask 覆盖时间戳 migration、三张 scheduler 表、运行记录分页筛选、due task 查询和会话来源预留字段读写。
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

新增表结构、Repository、数据库映射和 migration 时改这里。V17 之后新增 migration 文件名必须使用 `VyyyyMMddHHmmss__description.sql`，时间戳按提交者创建迁移时的本地时间确定，不再使用顺序数字版本。不要把任务状态机或 HTTP API 编排逻辑放进本模块。
JSON payload/capabilities 当前以文本列保存，未来切换 PostgreSQL JSONB 必须同步兼容策略和测试。
`ai_model_configs` 只保存平台托管的企业内模型目录，不保存模型调用密钥；密钥仍通过环境变量或配置中心注入，并由 runtime 模块同步到 opencode provider 配置引用。
`agent_session_bindings` 是新链路的 agent 远端 session 绑定主数据源；Session 的 `opencode_session_id` 与 `opencode_execution_node_id` 仅作为 `opencode` 兼容字段，新增 agent 不得继续扩展 `sessions` 列，新增查询或导出时不得默认暴露给前端 DTO；`pinned` 是平台 Session API 字段，默认旧数据未置顶。
RunEvent 追加可能来自 opencode stream、取消和 Diff 动作等多个线程；修改 `JdbcRunEventRepository` 时必须保留并发 append 下 seq 单调且不重复的测试。修改 SessionMessage/Run 映射时必须覆盖 token/cost、parts_json 和 active-run 查询。
