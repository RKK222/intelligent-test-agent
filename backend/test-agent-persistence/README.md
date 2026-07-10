# test-agent-persistence

## 工程定位

持久化模块，负责数据库、迁移、Repository 和 Redis 运行态访问适配。

## 技术栈

- Java 21
- MyBatis XML mapper
- Spring Data JDBC（仅存量 `Jdbc*Repository` 迁移窗口）
- PostgreSQL
- Flyway Core + PostgreSQL database support
- Druid JDBC 连接池
- Redis required
- Maven library jar

## 主要职责

- Workspace、Session、AgentSessionBinding、SessionMessage、Run、RunEvent、ExecutionNode、RoutingDecision、opencode 用户进程管理拓扑、AI 回复反馈、运营分析 rollup、应用配置管理、应用版本工作区、个人工作区和定时任务框架等持久化；运行态 Workspace 记录可空 `linux_server_id` 以支持文件 WebSocket 同服务器校验和 legacy 回填。
- Flyway migration，包含 PostgreSQL 16 所需的 Flyway database support。
- Repository 实现和数据库映射；新增或修改关系型 SQL 必须通过 MyBatis XML mapper。
- Redis 限流、幂等和运行心跳能力适配；用户进程运行管理与 manager 控制面在线状态依赖 Redis。通用参数值不写入 Redis，运行态读取直接查询数据库。

## 建表规范

- 表和字段必须添加中文注释说明。

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
- `V20260626120900__add_managed_workspace_replicas.sql`：为应用版本工作区增加目标 commit，并创建每服务器副本表。
- `V10__seed_fcoss_application.sql`：本地开发 F-COSS 应用种子数据，保留 V10 版本以兼容已应用该 seed 的历史本地库。
- `V13__seed_fcoss_more_workspaces.sql`：历史本地开发 F-COSS 扩展种子数据，保留 V13 版本以兼容已应用该 seed 的历史本地库。
- `V16__add_message_and_run_usage_fields.sql`：为 `session_messages` 和 `runs` 增加 run/remote message/parts/token/cost 快照字段及 active-run 查询索引。
- `V14__create_opencode_process_management_tables.sql`：创建 Linux 服务器、后端 Java 进程、opencode 容器、容器管理进程、管理进程连接、用户专属 opencode server 进程和用户绑定表。
- `V15__add_opencode_process_id_check_constraints.sql`：为 opencode 进程管理表加 `process_id` 前缀、稳定服务器身份、状态、port、baseUrl 形状等 CHECK 约束。
- `V20260625184300__create_scheduler_framework_tables.sql`：创建 scheduler 任务定义、用户级计划、运行记录表，并给 sessions/runs/session_messages 增加来源预留字段；V18 之后新增 migration 统一使用 14 位时间戳版本，避免多人并行开发抢数字版本。
- `V17__seed_local_opencode_machine_for_default_user.sql`：历史本地开发种子脚本，曾预置一台 `127.0.0.1` 的 opencode 机器并绑定默认开发用户；该版本已可能被历史库应用，禁止删除、重命名或直接改写。
- `V20260627000000__cleanup_loopback_linux_server_seed.sql`：清理 V17 留下的 `127.0.0.1` loopback opencode 拓扑、用户进程、绑定和关联的 manager-backend 连接。
- `V20260627010000__add_encrypted_aes_key_to_user_ssh_keys.sql`：为 `user_ssh_keys` 增加 `encrypted_aes_key` 列；V10 已被 F-COSS seed 占用，后续 schema 变更不得复用 V10。
- `V20260627020000__seed_opencode_manager_max_processes_param.sql`：初始化生产必需通用参数 `OPENCODE_MANAGER_MAX_PROCESSES`，供后端向 opencode-manager 下发运行时最大进程数。
- `V20260628223000__add_macos_platform_support.sql`：扩展 `common_parameters.platform` 允许 `macos`，并写入 macOS 平台路径参数；CHECK 约束和种子 INSERT 保持 PostgreSQL 与 H2 PostgreSQL 模式兼容。
- `V20260629203006__seed_sys_data_root_dir_param.sql`：初始化生产必需通用参数 `SYS_DATA_ROOT_DIR`，分别为 macOS、Linux、Windows 提供系统数据根目录默认值。
- `V20260629230000__consolidate_opencode_path_params_to_all.sql`：将 `OPENCODE_APP_WORKSPACE_ROOT`、`OPENCODE_PERSONAL_WORKTREE_ROOT`、`OPENCODE_PUBLIC_CONFIG_DIR`、`OPENCODE_PUBLIC_CONFIG_GIT_ROOT`、`OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT`、`OPENCODE_SESSION_DIR` 六个路径参数由 linux/windows/macos 三平台行收敛为单条 `all` 行，值统一引用 `${SYS_DATA_ROOT_DIR}`；`all` 行在运行态由通用参数解析器按当前/目标平台展开 `SYS_DATA_ROOT_DIR`，migration 使用删除后普通插入以兼容 H2 集成测试。
- `V20260627214000__reset_user_roles_identity_sequence.sql`：将 `user_roles.id` identity 起点抬高，兼容历史库中序列落后于已有主键导致新增用户授予角色失败的问题。
- `V20260626090000__add_workspace_linux_server_id.sql`：为 `workspaces` 增加可空 `linux_server_id` 和索引，新增工作区写当前服务器，历史空值由业务层在同服务器文件 WebSocket ticket 校验成功后回填。
- `V20260626150000__add_common_parameters_and_workspace_create_operations.sql`：创建通用参数表、初始化 Linux/Windows opencode 路径参数，为 `code_repositories` 增加可空唯一 `english_name`，并创建设置页工作空间创建进度表。
- `V20260702153000__add_repository_type_to_code_repositories.sql`：初始化生产必需字典 `REPOSITORY_TYPE`，为 `code_repositories` 增加非空 `repository_type`，并按历史 `standard` 回填测试工作库或应用代码库。
- `V20260702180000__add_code_repository_deployment_mode.sql`：为 `code_repositories` 增加非空 `deployment_mode`（`EXTERNAL`/`INTERNAL`，存量默认外部），并把 `english_name` 扩展到 128 字符。
- `V20260703141000__create_run_session_scopes.sql`：创建 `run_session_scopes` 和 `run_session_scope_sessions`，并为 `run_events` 预留可空 session scope 与 `raw_event_id` 列；metadata 使用 `metadata_json text`，不使用 JSONB。
- `V20260628231000__create_analytics_feedback_and_rollups.sql`：增加 Run 的 `agent_id/model_id` 快照、`ai_message_feedbacks`、hourly/daily 用户运营 rollup、Run 耗时直方图、水位、任务运行记录和 DB 锁表；不新增任何测试/演示数据。
- `V20260708200000__add_user_session_history_indexes.sql`：为用户级历史会话列表增加 `sessions.created_by_user_id`、`runs.triggered_by_user_id` 和 `session_messages.sender_user_id` 归因查询索引，不写入任何数据。
- `SocketOpencodeProcessManagerGateway` 是唯一生产装配，本地和生产都走 manager WebSocket；本地开箱即用状态必须由真实 manager/backend 心跳注册承载，不再由 V17 seed、`gateway-mode=local` 或 `local-direct` 承载。
- `JdbcWorkspaceRepository` 映射 `linux_server_id`，读取历史脏数据时会兼容 `updated_at < created_at` 的行并把 `updated_at` 归一化到 `created_at`，同时打印 WARN 供排障；正常写入路径仍由领域层不变量保证 `updated_at >= created_at`。其余存量核心 JDBC 仓储包括 `JdbcSessionRepository`、`JdbcExecutionNodeRepository`、`JdbcRoutingDecisionRepository`。
- `MyBatisRunRepository`：通过 `RunMapper.xml` 实现 Run 保存、读取、最近非终态 Run 查询、stale active Run 候选查询和 `saveIfStatus` 条件状态写入，是当前生产 Spring Bean；stale 查询只按 `runs.updated_at`、`status in ('PENDING','RUNNING','CANCELLING')` 和 limit 排序筛选，不判断 ask 状态；pending ask 属于 runtime Redis 状态，由 `test-agent-opencode-runtime` 判断。`saveIfStatus` 使用 `where run_id = ? and status = ?` 原子避免后到的异步失败覆盖已落库终态。
- `MyBatisSessionTitleUpdateRepository`：通过 `SessionTitleUpdateMapper.xml` 实现 `where session_id = ? and title = ?` 的原子标题条件更新；OpenCode 首轮标题兜底仅在平台标题仍为首条消息临时标题时成功，避免覆盖后到的原生标题或用户手动改名。
- `JdbcRunRepository`：Run 存量 JDBC 实现已不再作为生产 Spring Bean，仅保留给旧集成测试和迁移窗口；后续 Run SQL 变更必须改 MyBatis XML。
- `MyBatisRunEventRepository`：通过 `RunEventMapper.xml` 实现 RunEvent append-only 追加、同一 run 内 seq 分配、scope 列写入、`raw_event_id` 可空写入、`runId + lastSeq` 增量读取和 `root_session_id` 历史状态读取，是当前生产 Spring Bean。
- `JdbcRunEventRepository`：RunEvent 存量 JDBC 实现已不再作为 Spring Bean，仅保留给旧集成测试和迁移窗口。
- `JdbcAgentSessionBindingRepository`：实现按 `(sessionId, agentId)` 和 `(agentId, remoteSessionId)` 查询、upsert 通用远端 session 绑定。
- `JdbcSessionMessageRepository`：实现会话消息保存、按远端 messageId 幂等查询、分页和计数。
- `MyBatisConfigurationManagementRepository`：通过 `ConfigurationManagementMapper.xml` 实现配置管理表的应用只读查询、成员逻辑删除、仓库关联、版本库类型、版本库部署模式、工作空间和个人 SSH key 元数据持久化，是当前生产 Spring Bean。
- `JdbcConfigurationManagementRepository`：配置管理存量 JDBC 实现已不再作为 Spring Bean，仅保留给旧集成测试和迁移窗口；其中 `repository_type` / `deployment_mode` 映射只为兼容新增非空列，后续配置管理 SQL 变更必须改 MyBatis XML。
- `MyBatisCommonParameterRepository`：当前 MyBatis 试点实现，按参数英文名和平台读取、列出并更新通用参数；SQL 位于 `src/main/resources/mybatis/CommonParameterMapper.xml`。
- `MyBatisAiMessageFeedbackRepository`：通过 `AiMessageFeedbackMapper.xml` 实现反馈保存与 `(user_id, message_id)` 查询，服务层据此做单用户单消息 upsert。
- `MyBatisAnalyticsRepository`：通过 `AnalyticsMapper.xml` 实现原始事实读取、hourly/daily rollup 写入、直方图、水位/锁、用户/组织/满意度/异常明细查询；看板查询只读 rollup 表，不返回 prompt、assistant 原文或费用字段。
- `MyBatisDatabaseIdentityMaintenanceRepository`：通过 `DatabaseIdentityMapper.xml` 实现 identity 运维护口，查询 `pg_sequences` 当前值与 `max(id)`、执行 `ALTER TABLE ... RESTART WITH`；SQL 注入防护依赖白名单表名与服务层校验。
- `MyBatisRunSessionScopeRepository`：通过 `RunSessionScopeMapper.xml` 保存 Run root scope 和当前 Run root/child session 清单，供 SSE/HTTP snapshot 按当前 Run 子树恢复消息，并支持按 `root_session_id` 汇总 Session 历史树；mapper 中 `MERGE ... USING (VALUES ...)` 的时间参数显式 cast 为 `timestamp`，避免 PostgreSQL 将未定型参数推断为 `text`。
- `MyBatisSessionHistoryRepository`：通过 `SessionHistoryMapper.xml` 实现当前用户历史会话只读分页，按 `sessions.created_by_user_id`、`runs.triggered_by_user_id`、`session_messages.sender_user_id` 归因，left join 托管应用/工作区/版本上下文，排序严格按 `updated_at desc, id desc`，不复用 `JdbcSessionRepository` 的 pinned 排序 SQL。
- `MyBatisSessionRuntimeStateRepository`：通过 `SessionRuntimeStateMapper.xml` 实现当前用户历史会话运行态只读摘要，复用历史会话可见性归因，只返回每个 ACTIVE 会话最近一个 `PENDING/RUNNING/CANCELLING` Run，并按最新 `question.asked/replied/rejected` 派生 `QUESTION` 待关注标记；不新增数据库表或 Flyway migration。
- `JdbcCommonParameterRepository`：通用参数存量 JDBC 实现已不再作为 Spring Bean，仅保留给旧集成测试直接构造；后续通用参数 SQL 变更必须改 MyBatis XML。
- `JdbcWorkspaceCreateOperationRepository`：实现设置页创建应用工作空间进度保存、步骤更新、成功/失败记录和按 `operationId` 查询。
- `JdbcManagedWorkspaceRepository`：实现应用版本工作区、每服务器副本、目标 commit、个人工作区、最近使用偏好和同步审计持久化。
- `JdbcOpencodeProcessManagementRepository`：实现 opencode 用户进程管理拓扑、用户进程、用户绑定持久化，以及超级管理员运行管理页需要的拓扑列表、连接列表、进程分页筛选和绑定关联查询；读取历史用户进程或后端 Java 进程时会兼容 `updated_at < created_at` 的脏数据并按 `created_at` 归一化，避免旧记录阻断状态查询、manager 注册和重新初始化。
- `RedisOpencodeProcessHeartbeatStore`：保存 Java 后端运行快照、manager 运行快照和 opencode server 进程运行心跳。Java/manager 快照 TTL 固定 10 秒，供运行管理页和 manager 后端列表发现识别在线实例；Java 后端 latest snapshot 按 `backendProcessId` 保存，服务器在线集合和指标历史按稳定 `linuxServerId` 分组；Java 服务器级指标按 `test-agent:runtime-metrics:server:{linuxServerId}` 保存，包含 CPU/load/内存/swap/磁盘字段；Java 进程与 JVM 指标按 `test-agent:runtime-metrics:backend:{linuxServerId}` 保存，包含进程 CPU/RSS/FD、heap/non-heap、direct/mapped、GC delta 和线程字段；旧 `backendProcessId` JVM key 仅供兼容接口回退读取，容器指标按 `test-agent:runtime-metrics:container:{containerId}` 保存；Redis 反序列化忽略未知字段，旧样本缺失的新字段保持 `null`；opencode server 进程心跳 key 保留 5 分钟 TTL。Redis 是系统必需依赖，不再提供 no-op 心跳存储。
- `JdbcScheduledTaskRepository`：实现 scheduler 任务定义、用户计划、运行记录、due task 查询和 pending run 查询。
- RunEvent append-only：持久化层分配 `eventId` 和同一 run 内单调递增 `seq`，并发追加时通过 `(run_id, seq)` 唯一约束冲突后重读重试，支持 `runId + lastSeq` 增量读取；Session 级历史树按 `root_session_id` 读取跨 Run durable 状态事件。opencode raw event id 缺失时写入 `NULL`，避免 `"unknown"` 误去重。

## 测试环境 PostgreSQL

`test-agent-app` 的 `test` profile 会通过环境变量装配 PostgreSQL 测试库，并复用本模块 `db/migration` 下的 Flyway migration。持久化模块提供 Druid starter 依赖，实际连接信息和连接池大小由应用 profile 配置注入，不保存环境专属账号、密码或主机地址。

## 测试覆盖

- `JdbcRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖 Workspace（含 `linux_server_id`、历史脏 `updated_at < created_at` 归一化）、Session、AgentSessionBinding、SessionMessage、Run、RunEvent、ExecutionNode、RoutingDecision 的保存和读取。
- `MyBatisCommonParameterRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖通用参数 MyBatis XML 查询、列表、按 ID 查询和仅更新 value。
- `JdbcRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖 Workspace（含 `linux_server_id`）、Session、AgentSessionBinding、SessionMessage、Run、RunEvent、ExecutionNode、RoutingDecision 的保存和读取。
- `MyBatisCommonParameterRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖通用参数 MyBatis XML 查询、列表、按 ID 查询、`SYS_DATA_ROOT_DIR` 三平台种子和仅更新 value。
- `MyBatisRunSessionScopeRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖 Run session scope 表、MyBatis XML upsert/query、按 root session 查询和 root/child session 映射；`PersistenceSqlConventionTest` 固化 Run session scope mapper 在 PostgreSQL `MERGE` 中必须显式 cast 时间参数。
- `MyBatisSessionHistoryRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖当前用户历史分页、创建人/Run/消息归因兜底、更新时间倒序、托管应用上下文 join、非托管工作区名称 fallback 和无成员关系仍返回历史。
- `MyBatisSessionRuntimeStateRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖用户级运行计数、终态 Run 排除、`question.asked` 待关注、`question.replied/rejected` 清除，以及不可见会话过滤。
- `MyBatisRunRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖 Run MyBatis XML 保存/读取、active-run 查询、stale active 候选查询、token/cost/source 字段映射、`saveIfStatus` 成功更新和状态不匹配时不覆盖终态。
- `MyBatisRunEventRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖 RunEvent MyBatis XML append、scope 列写入、`raw_event_id=NULL` 语义和 seq 单调分配。
- `MyBatisSessionTitleUpdateRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖 Session 标题 XML 条件更新成功与预期标题不匹配时不覆盖新标题。
- 运营分析相关 XML 通过持久化模块编译、Flyway 集成和运行时服务单测覆盖；`AnalyticsQueryServiceTest` 固化空分母、满意率、采纳率、p95 和 CSV 字段口径。
- `PersistenceSqlConventionTest` 固化持久层 SQL 规则：存量 JDBC 文件只允许留在白名单，MyBatis mapper 不得使用注解 SQL。
- SessionMessage/Run 覆盖 V16 token/cost 字段读写、parts_json 兼容、按 `(sessionId, remoteMessageId)` 查询以及最近非终态 Run 查询。
- RunEvent 覆盖 append-only seq 单调递增、并发追加唯一性、`runId + lastSeq` 增量读取、结构化 scope 列和 `(run_id, seq)` 唯一约束。
- Session 覆盖远端 opencode 映射、全局搜索、置顶排序、工作区会话分页和归档过滤。
- AgentSessionBinding 覆盖 upsert、按 agent 查询、远端 session 唯一约束和从旧 opencode 字段回填。
- ConfigurationManagement 覆盖 V7 migration、V8 默认用户授权、成员逻辑删除恢复、应用与仓库多对多关联、代码库英文名保存/查询、版本库类型字典与 `repository_type` 回填、版本库部署模式 `deployment_mode` 默认值和 MyBatis XML 保存/读取、通用参数默认值、工作空间创建进度表、应用工作空间保存和用户单 SSH key 唯一约束。
- ManagedWorkspace 覆盖 V9/V20260626120900 migration、版本工作区唯一性、每服务器副本 upsert、目标 commit、个人空间名称唯一性、最近使用偏好和同步审计保存。
- OpencodeProcessManagement 覆盖 V14 migration、V17 loopback 种子清理、拓扑读写、历史用户进程与后端 Java 进程时间戳归一化、健康容器查询、运行管理拓扑列表、manager-backend 连接列表、opencode server 进程分页筛选、绑定关联查询、用户绑定唯一约束、服务器端口唯一约束和容器管理进程一对一约束。
- RedisOpencodeProcessHeartbeatStore 覆盖 Java/manager 运行快照写入 Redis 的 key、索引、10 秒 TTL，Java latest snapshot、服务器级指标与 Java/JVM 指标按 `linuxServerId` 分流写入、未知 JSON 字段宽容读取、旧 JSON 缺新字段保持 `null` 和容器指标历史 key。
- ScheduledTask 覆盖时间戳 migration、三张 scheduler 表、运行记录分页筛选、due task 查询和会话来源预留字段读写。
- Session 全局分页在空搜索条件下不会绑定可空 query pattern，避免 PostgreSQL 无法推断 null 参数类型。
- ExecutionNode 覆盖可路由节点过滤：仅 READY 且 `running_runs < max_runs`，并按负载、权重、更新时间稳定排序。
- `DruidDataSourceConfigurationTest` 覆盖 Druid DataSource 绑定和 Web 控制台默认关闭。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- MyBatis Spring Boot starter。
- Spring Data JDBC（仅存量 `Jdbc*Repository` 迁移窗口）。
- Flyway、Flyway PostgreSQL database support、PostgreSQL、Druid、Redis。

## 禁止依赖

- `test-agent-api` 和业务模块通过 domain 端口调用。
- generated SDK。
- opencode client facade。

## 后续 AI 编码指引

新增表结构、Repository、数据库映射和 migration 时改这里。V18 之后新增 migration 文件名必须使用 `VyyyyMMddHHmmss__description.sql`，时间戳按提交者创建迁移时的本地时间确定，不再使用顺序数字版本。不要把任务状态机或 HTTP API 编排逻辑放进本模块。
Flyway migration 只能承载表结构变更、历史数据兼容迁移和生产必需的基础字典/系统参数；禁止新增写入测试、演示、个人开发或环境专属数据的 seed migration。测试数据应放在 `test-agent-test-support`、测试 fixture、mock 数据或显式本地开发脚本中。
新增或修改关系型 SQL 必须新增/调整 `mybatis/*.xml` 与 `com.icbc.testagent.persistence.mybatis` 内部 mapper，不能继续扩展 `Jdbc*Repository` 或使用 MyBatis 注解 SQL；存量 JDBC 仓储后续按触点分批迁移。
JSON payload/capabilities 当前以文本列保存，未来切换 PostgreSQL JSONB 必须同步兼容策略和测试。
`ai_model_configs` 只保存平台托管的企业内模型目录，不保存模型调用密钥；密钥仍通过环境变量或配置中心注入，并由 runtime 模块同步到 opencode provider 配置引用。
`agent_session_bindings` 是新链路的 agent 远端 session 绑定主数据源；Session 的 `opencode_session_id` 与 `opencode_execution_node_id` 仅作为 `opencode` 兼容字段，新增 agent 不得继续扩展 `sessions` 列，新增查询或导出时不得默认暴露给前端 DTO；`pinned` 是平台 Session API 字段，默认旧数据未置顶。
RunEvent 追加可能来自 opencode stream、取消和 Diff 动作等多个线程；修改 `MyBatisRunEventRepository` 时必须保留并发 append 下 seq 单调且不重复、scope 列写入、`raw_event_id` 缺失为 `NULL` 的测试。修改 SessionMessage/Run 映射时必须覆盖 token/cost、parts_json 和 active-run 查询。
