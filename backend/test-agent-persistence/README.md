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
- `RunMapper.xml` 提供精确 `SIDE_QUESTION + active + updated_at < cutoff` 孤儿查询；Session history 与用户 runtime-state 查询显式排除内部 `SIDE_QUESTION` Session，即使异常数据误为 ACTIVE 也不可见。
- Flyway migration，包含 PostgreSQL 16 所需的 Flyway database support。
- Repository 实现和数据库映射；新增或修改关系型 SQL 必须通过 MyBatis XML mapper。
- Redis 会话运行上下文、Run 运行数据面、限流、幂等和运行心跳能力适配；用户进程运行管理与 manager 控制面在线状态依赖 Redis。通用参数值不写入 Redis，运行态读取直接查询数据库。

## 建表规范

- 表和字段必须添加中文注释说明。

## 已有实现

- `V1__create_core_tables.sql`：创建 Workspace、Session、Run、RunEvent、ExecutionNode、RoutingDecision 核心表。
- `V20260711120000__document_side_question_run_source.sql`：只更新三个 `source_type` 字段的允许值注释以包含 `SIDE_QUESTION`，不改结构、不写数据。
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
- `V20260715000000__add_scheduler_run_retention_index.sql`：为 `scheduled_task_runs.ended_at` 增加每日保留清理使用的索引，不写入业务数据。
- `V20260717173000__create_public_agent_config_rollouts.sql`：创建公共 Agent/Skill 发布 rollout、服务器同步和存量进程目标表。
- `V20260717200000__harden_public_agent_config_rollout.sql`：为 rollout 保存发起人，为目标快照用户并增加认领 fencing token；历史目标按服务器、容器、端口回填用户归属，不写测试或演示数据。
- `V17__seed_local_opencode_machine_for_default_user.sql`：历史本地开发种子脚本，曾预置一台 `127.0.0.1` 的 opencode 机器并绑定默认开发用户；该版本已可能被历史库应用，禁止删除、重命名或直接改写。
- `V20260627000000__cleanup_loopback_linux_server_seed.sql`：清理 V17 留下的 `127.0.0.1` loopback opencode 拓扑、用户进程、绑定和关联的 manager-backend 连接。
- `V20260627010000__add_encrypted_aes_key_to_user_ssh_keys.sql`：为 `user_ssh_keys` 增加 `encrypted_aes_key` 列；V10 已被 F-COSS seed 占用，后续 schema 变更不得复用 V10。
- `V20260627020000__seed_opencode_manager_max_processes_param.sql`：初始化生产必需通用参数 `OPENCODE_MANAGER_MAX_PROCESSES`，供后端向 opencode-manager 下发运行时最大进程数。
- `V20260628223000__add_macos_platform_support.sql`：扩展 `common_parameters.platform` 允许 `macos`，并写入 macOS 平台路径参数；CHECK 约束和种子 INSERT 保持 PostgreSQL 与 H2 PostgreSQL 模式兼容。
- `V20260629203006__seed_sys_data_root_dir_param.sql`：初始化生产必需通用参数 `SYS_DATA_ROOT_DIR`，分别为 macOS、Linux、Windows 提供系统数据根目录默认值。
- `V20260629230000__consolidate_opencode_path_params_to_all.sql`：将 `OPENCODE_APP_WORKSPACE_ROOT`、`OPENCODE_PERSONAL_WORKTREE_ROOT`、`OPENCODE_PUBLIC_CONFIG_DIR`、`OPENCODE_PUBLIC_CONFIG_GIT_ROOT`、`OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT`、`OPENCODE_SESSION_DIR` 六个路径参数由 linux/windows/macos 三平台行收敛为单条 `all` 行，值统一引用 `${SYS_DATA_ROOT_DIR}`；`all` 行在运行态由通用参数解析器按当前/目标平台展开 `SYS_DATA_ROOT_DIR`，migration 使用删除后普通插入以兼容 H2 集成测试。
- `V20260718100000__seed_references_params.sql`：初始化引用资产通用参数 `OPENCODE_REFERENCES_DIR`（`all`、`editable=false`，值引用 `${SYS_DATA_ROOT_DIR}/agent-opencode/references`）与 `REFERENCES_SDD_FOLDER_NAMES`（`all`、`editable=true`，默认 `docs,spec`）；`OPENCODE_REFERENCES_DIR` 的值用 `'$' || '{SYS_DATA_ROOT_DIR}/...'` 拼接规避 Flyway 占位符替换，与 `OPENCODE_SESSION_DIR` 等路径参数一致，由通用参数解析器在运行态按当前平台展开。
- `V20260718110000__create_reference_repository_replica_tables.sql`：创建 `reference_repository_states` 总体状态表和 `reference_repository_replicas` 服务器副本表，后者以 `(repository_id, linux_server_id)` 为复合主键并保存 generation、重试、`DEFERRED`、lease token/到期时间；认领、续租和终态写回由 `ReferenceRepositoryMapper.xml` 使用 generation + token + 未过期租约做 CAS fencing。
- `V20260718143000__add_reference_repository_operations_and_verification.sql`：增加引用资产 generation 操作类型和副本最近核验时间，把副本 branch/commit 明确为可空的实际观察值；兼容回填存量操作类型，不写测试或演示数据。
- `V20260627214000__reset_user_roles_identity_sequence.sql`：将 `user_roles.id` identity 起点抬高，兼容历史库中序列落后于已有主键导致新增用户授予角色失败的问题。
- `V20260626090000__add_workspace_linux_server_id.sql`：为 `workspaces` 增加可空 `linux_server_id` 和索引，新增工作区写当前服务器，历史空值由业务层在同服务器文件 WebSocket ticket 校验成功后回填。
- `V20260626150000__add_common_parameters_and_workspace_create_operations.sql`：创建通用参数表、初始化 Linux/Windows opencode 路径参数，为 `code_repositories` 增加可空唯一 `english_name`，并创建设置页工作空间创建进度表。
- `V20260702153000__add_repository_type_to_code_repositories.sql`：初始化生产必需字典 `REPOSITORY_TYPE`，为 `code_repositories` 增加非空 `repository_type`，并按历史 `standard` 回填测试工作库或应用代码库。
- `V20260702180000__add_code_repository_deployment_mode.sql`：为 `code_repositories` 增加非空 `deployment_mode`（`EXTERNAL`/`INTERNAL`，存量默认外部），并把 `english_name` 扩展到 128 字符。
- `V20260703141000__create_run_session_scopes.sql`：创建 `run_session_scopes` 和 `run_session_scope_sessions`，并为 `run_events` 预留可空 session scope 与 `raw_event_id` 列；metadata 使用 `metadata_json text`，不使用 JSONB。
- `V20260628231000__create_analytics_feedback_and_rollups.sql`：增加 Run 的 `agent_id/model_id` 快照、`ai_message_feedbacks`、hourly/daily 用户运营 rollup、Run 耗时直方图、水位、任务运行记录和 DB 锁表；不新增任何测试/演示数据。
- `V20260708200000__add_user_session_history_indexes.sql`：为用户级历史会话列表增加 `sessions.created_by_user_id`、`runs.triggered_by_user_id` 和 `session_messages.sender_user_id` 归因查询索引，不写入任何数据。
- `V20260710143000__add_run_summary_persistence.sql`：为 `runs` 增加新模式、幂等、生产路由、终态、详情期限、Diff 与远端定位控制面字段；为 `session_messages` 增加摘要形态、版本、状态和幂等键。历史行默认 `LEGACY_FULL/RAW_LEGACY`，不迁移或删除旧原文。
- `SocketOpencodeProcessManagerGateway` 是唯一生产装配，本地和生产都走 manager WebSocket；本地开箱即用状态必须由真实 manager/backend 心跳注册承载，不再由 V17 seed、`gateway-mode=local` 或 `local-direct` 承载。
- `JdbcWorkspaceRepository` 映射 `linux_server_id`，读取历史脏数据时会兼容 `updated_at < created_at` 的行并把 `updated_at` 归一化到 `created_at`，同时打印 WARN 供排障；正常写入路径仍由领域层不变量保证 `updated_at >= created_at`。其余存量核心 JDBC 仓储包括 `JdbcSessionRepository`、`JdbcExecutionNodeRepository`、`JdbcRoutingDecisionRepository`。
- `MyBatisSessionTitleUpdateRepository`：通过 `SessionTitleUpdateMapper.xml` 实现 `where session_id = ? and title = ?` 的原子标题条件更新；OpenCode 首轮标题兜底仅在平台标题仍为首条消息临时标题时成功，避免覆盖后到的原生标题或用户手动改名。
- `MyBatisRunRepository`：通过 `RunMapper.xml` 实现 Run 保存、读取、最近非终态 Run 查询、stale active Run 候选查询和 `saveIfStatus` 条件状态写入，是当前生产 Spring Bean；stale 查询固定为 `storage_mode='LEGACY_FULL'`，再按 `runs.updated_at`、active status 和 limit 排序筛选，不判断 ask 状态。`REDIS_SUMMARY` 的两小时无活动收敛只扫描 Redis manifest，禁止进入旧数据库事件链路。`saveIfStatus` 使用 `where run_id = ? and status = ?` 原子避免后到的异步失败覆盖已落库终态。
- `JdbcRunRepository`：Run 存量 JDBC 实现已不再作为生产 Spring Bean，仅保留给旧集成测试和迁移窗口；后续 Run SQL 变更必须改 MyBatis XML。
- `MyBatisRunEventRepository`：通过 `RunEventMapper.xml` 实现 RunEvent append-only 追加、同一 run 内 seq 分配、scope 列写入、`raw_event_id` 可空写入、`runId + lastSeq` 增量读取和 `root_session_id` 历史状态读取，是当前生产 Spring Bean。
- `JdbcRunEventRepository`：RunEvent 存量 JDBC 实现已不再作为 Spring Bean，仅保留给旧集成测试和迁移窗口。
- `JdbcAgentSessionBindingRepository`：实现按 `(sessionId, agentId)` 和 `(agentId, remoteSessionId)` 查询、upsert 通用远端 session 绑定。
- `JdbcSessionMessageRepository`：实现会话消息保存、按远端 messageId 幂等查询、分页和计数。
- `MyBatisConfigurationManagementRepository`：通过 `ConfigurationManagementMapper.xml` 实现配置管理表的应用只读查询、成员逻辑删除、仓库关联、版本库类型、版本库部署模式、工作空间和个人 SSH key 元数据持久化，是当前生产 Spring Bean。
- `JdbcConfigurationManagementRepository`：配置管理存量 JDBC 实现已不再作为 Spring Bean，仅保留给旧集成测试和迁移窗口；其中 `repository_type` / `deployment_mode` 映射只为兼容新增非空列，后续配置管理 SQL 变更必须改 MyBatis XML。
- `MyBatisCommonParameterRepository`：当前 MyBatis 试点实现，按参数英文名和平台读取、列出并更新通用参数；SQL 位于 `src/main/resources/mybatis/CommonParameterMapper.xml`。
- `MyBatisAiRunFeedbackRepository`：通过 `AiRunFeedbackMapper.xml` 实现 Run 反馈保存与 `(user_id, run_id)` 单查/批查，新记录不写 `message_id`；`MyBatisAiMessageFeedbackRepository` 保留历史消息兼容。
- `MyBatisAnalyticsRepository`：通过 `AnalyticsMapper.xml` 实现原始事实读取、hourly/daily rollup 写入、直方图、水位/锁、用户/组织/满意度/异常明细查询；Diff 事实按 storageMode 双读 legacy 事件与新模式 Run 计数，排除 shadow 事件双计数；看板查询只读 rollup 表，不返回 prompt、assistant 原文或费用字段。
- `MyBatisDatabaseIdentityMaintenanceRepository`：通过 `DatabaseIdentityMapper.xml` 实现 identity 运维护口，查询 `pg_sequences` 当前值与 `max(id)`、执行 `ALTER TABLE ... RESTART WITH`；SQL 注入防护依赖白名单表名与服务层校验。
- `MyBatisRunSessionScopeRepository`：通过 `RunSessionScopeMapper.xml` 保存 Run root scope 和当前 Run root/child session 清单，供 SSE/HTTP snapshot 按当前 Run 子树恢复消息，并支持按 `root_session_id` 汇总 Session 历史树；mapper 中 `MERGE ... USING (VALUES ...)` 的时间参数显式 cast 为 `timestamp`，避免 PostgreSQL 将未定型参数推断为 `text`。
- `MyBatisSessionHistoryRepository`：通过 `SessionHistoryMapper.xml` 实现当前用户历史会话只读分页，按 `sessions.created_by_user_id`、`runs.triggered_by_user_id`、`session_messages.sender_user_id` 归因，left join 托管应用/工作区/版本上下文，排序严格按 `updated_at desc, id desc`，不复用 `JdbcSessionRepository` 的 pinned 排序 SQL。
- `MyBatisSessionRuntimeStateRepository`：通过 `SessionRuntimeStateMapper.xml` 实现当前用户历史会话运行态只读摘要，复用历史会话可见性归因，只返回每个 ACTIVE 会话最近一个 `PENDING/RUNNING/CANCELLING` Run，并按最新 `question.asked/replied/rejected` 派生 `QUESTION` 待关注标记；不新增数据库表或 Flyway migration。
- `MyBatisPublicAgentConfigRolloutRepository`：通过 `PublicAgentConfigRolloutMapper.xml` 持久化发布硬闸门、服务器同步和本机目标认领；record 构造器中的 `port/retryCount` 使用 MyBatis `_int` 原始类型映射，避免查询排空任务时以 `Integer` 反射调用失败；运行时登记目标时按当前 `linuxServerId` 和分页仓储允许的 200 条上限读取平台进程映射；用户门禁读取发布窗口的用户快照，目标认领按 `linuxServerId + leaseToken` fencing，Workspace 根目录查询复用既有 execution node、opencode binding、Session 和 Workspace 关系。
- `MyBatisRunSummaryPersistenceRepository`：通过 `RunSummaryMapper.xml` 写无原文 Run 锚点、终态双摘要，并从既有 `runs.dispatch_message_id` 读取稳定用户轮次锚点与远端 ID 定位快照，供 Redis 详情过期后的目标 Run 恢复和显式 Diff 动作使用；accepted/rejected 计数使用单条 UPDATE，且不写 `run_events`。终态 CAS 只允许一次较高事件序号的晚到刷新；跨终态状态仅允许已落库的 `FAILED + TRANSPORT_ERROR` 被 `REMOTE_ROOT/RECOVERY_REMOTE_ROOT` 事实纠正，禁止其它来源任意翻转。本变更复用既有列，不新增 migration。
- `ScheduledTaskRunRetentionMapper` / `ScheduledTaskRunRetentionMapper.xml`：按 `ended_at` 和终态 status 清理超过 7 天的 scheduler 运行记录，显式排除活动状态。
- `MyBatisScheduledTaskRunRetentionRepository`：实现 scheduler 运行记录保留策略 domain 端口，供框架维护任务调用。
- `RedisRunTerminalRetryStore`：独立保存 PostgreSQL 终态事务失败后的已清洗 `RunTerminalProjection`。record 与全局 due ZSET 统一使用固定 `{terminal-retry}` hash tag；保存 Lua 按 `terminalProjectionVersion → lastEventSeq → failedAttempts/nextAttemptAt` 单调更新，旧 worker 不能覆盖晚到纠正版；删除 Lua 只在完整白名单 JSON 仍等于调用方处理记录时执行 `ZREM + DEL`，旧 worker 不能删除新版。缺失/非法 due member 的自愈 Lua 也只在 record 仍不存在时移除索引，不会误删并发新记录。每 Run JSON 使用 24 小时内的绝对 TTL，due ZSET 只保存 runId 和 `nextAttemptAt`；显式白名单序列化形状不包含 prompt、完整回答、parts、reasoning、工具输入输出或原始事件，Redis 不可用时返回 `RUNTIME_STATE_UNAVAILABLE`，不回退数据库或 JVM 内存队列。
- `JdbcCommonParameterRepository`：通用参数存量 JDBC 实现已不再作为 Spring Bean，仅保留给旧集成测试直接构造；后续通用参数 SQL 变更必须改 MyBatis XML。
- `JdbcWorkspaceCreateOperationRepository`：实现设置页创建应用工作空间进度保存、步骤更新、成功/失败记录和按 `operationId` 查询。
- `JdbcManagedWorkspaceRepository`：实现应用版本工作区、每服务器副本、目标 commit、个人工作区、最近使用偏好和同步审计持久化。
- `JdbcOpencodeProcessManagementRepository`：实现 opencode 用户进程管理拓扑、用户进程、用户绑定持久化，以及超级管理员运行管理页需要的拓扑列表、连接列表、进程分页筛选和绑定关联查询；读取历史用户进程或后端 Java 进程时会兼容 `updated_at < created_at` 的脏数据并按 `created_at` 归一化，避免旧记录阻断状态查询、manager 注册和重新初始化。
- `RedisOpencodeProcessHeartbeatStore`：保存 Java 后端运行快照、manager 运行快照和 opencode server 进程运行心跳。Java/manager 快照 TTL 固定 10 秒，供运行管理页和 manager 后端列表发现识别在线实例；Java 后端 latest snapshot 按 `backendProcessId` 保存，服务器在线集合和指标历史按稳定 `linuxServerId` 分组；Java 服务器级指标按 `test-agent:runtime-metrics:server:{linuxServerId}` 保存，包含 CPU/load/内存/swap/磁盘字段；Java 进程与 JVM 指标按 `test-agent:runtime-metrics:backend:{linuxServerId}` 保存，包含进程 CPU/RSS/FD、heap/non-heap、direct/mapped、GC delta 和线程字段；旧 `backendProcessId` JVM key 仅供兼容接口回退读取，容器指标按 `test-agent:runtime-metrics:container:{containerId}` 保存；Redis 反序列化忽略未知字段，旧样本缺失的新字段保持 `null`；opencode server 进程心跳 key 保留 5 分钟 TTL。Redis 是系统必需依赖，不再提供 no-op 心跳存储。
- `RedisConversationContextStore`：实现 `ConversationContextStore` 领域端口；所有 key 使用 `{conversation-context}` hash tag，token key 只含原始 `contextToken` 的 SHA-256 摘要。Lua 原子维护 token、用户+Session/用户/Session/Workspace/进程五类 ZSET 反向索引及 generation 快照；索引 score 为 token 绝对过期时间，保存、续期和失效时会先删除已过期成员，避免活跃业务实体的索引成员无界累积。权威读取前的 `beginIssue` 同时检查 Session revoke 和 user mutation gate 并捕获签发 fence、资源和全局代次，`saveIfCurrent` 还会检查 Workspace mutation gate；`resolveForRouting`/`touch` 同样拒绝任一关联 gate。Session 归档使用逐归档 token 的 Redis Set gate；用户权限及 Workspace 可信字段变更使用覆盖关系型写入窗口的 mutation gate，完成脚本原子执行“再次失效 + `SREM` 自己的 gate token”，数据库失败只释放自己的 token，Redis 完成失败则保留 gate fail-closed。三类 gate 均设置 24 小时 TTL，避免异常后永久锁死；generation 不设 TTL，确保 gate 过期后旧 token 仍不能复活。全局可信路径变化通过 O(1) generation 失效。Redis 访问、脚本或 JSON 序列化异常统一映射为 `RUNTIME_STATE_UNAVAILABLE`，不回退 PostgreSQL 或 JVM 内存。
- `RedisRunRuntimeStore`：实现 `RunRuntimeStore` 领域端口。单 Run 的 manifest、input、durable `events` Stream、全事件 `runtime-events` Stream、snapshot Hash + order ZSET、动态 key registry、scope、dedup、pending 和 15 秒 owner lease key 统一使用 `{runId}` hash tag。`clientRequestId` 在锚点 INSERT 前只声明 30 秒 crash window，锚点成功后用 compare-confirm 延长到幂等保留期；初始化后、锚点前退出的孤儿由恢复扫描在 30 秒保护期后调用 `discardBeforeDispatch`，不会阻塞七天或被当作已成功 Run 返回。plain owner claim 原子拒绝终态 Run 并保留同 owner 重入 token；条件接管同时校验活跃 manifest 的状态/版本/seq/容量/attention/更新时间快照并总是提升 token。durable/transient 事件、远端 Session 绑定及 scope/dedup/pending 写入均在各自 Lua 的首个副作用前校验 owner + token，旧 token 返回稳定冲突且不续期索引；pending 字节计入统一详情预算，append 原子更新 `pendingBytes/detailBytes/updatedAt`，drain 原子扣减并移出 registry。claim/renew 返回的本地截止时间按 Redis 请求前时刻计算。未写锚点的 Run 使用 `discardBeforeDispatch` 清理详情和 active/history/client 索引。durable Stream ID 为 `${seq}-0`；runtime Stream 同时包含 durable/transient，ID 为 `${runtimeVersion}-0`。
- 物化 snapshot 不是最近 N 条日志：初始化时先用 Redis input 生成本轮 USER `message.updated`，并使用独立 `p:user-input:*` key，使 reset 不依赖浏览器乐观消息且容量淘汰不会把输入当作普通旧消息删除；后续 message/part/entity 使用稳定 SHA-256 projection key 覆盖，标识兼容顶层 ID 与真实 `info.id` / `part.messageID` / `part.id`。`message.part.delta` 按 part + `field` 分别聚合，full part update/remove 清理该 part 所有 field delta，`assistant.message.delta` 同样聚合文本。Todo、permission/question、tool、Diff、scope 和 Run status 保留当前可见状态；Hash 存 projection，ZSET 保 reducer 应用顺序。外部 snapshot 写入必须同时 CAS `barrierSeq + runtimeVersion`，任一版本变化都拒绝覆盖新投影。SSE 首帧总是发送完整 snapshot reset，后续按 runtimeVersion 分页读取 `runtime-events`；live bus 只负责低延迟唤醒。
- active Run 维护用户、Session、服务器索引及用户运行态 marker；这些跨 slot 索引在单 Run 初始化 Lua 前先按“所有运行态中最长物理 TTL 的两倍”保守登记，后续也不缩短 TTL/score。这样即使上次刷新时 Run 已进入 7 天 pending 窗口，下一次最长 TTL 事件 Lua 提交后 Java 立即退出，索引仍覆盖完整恢复窗口；服务器/用户恢复读取会再次预留全部索引，初始化 Lua 失败产生的悬空成员由普通读路径回读 manifest 清理。事件 Lua 在分配 runtimeVersion、写 Stream 和更新 snapshot 之前校验 status 转换，终态后的晚到非终态事件不会进入 `runtime-events`、run-status projection 或 live bus（仍允许终态之间的幂等纠正）。reply/reject 只在 request ID 匹配当前 attention 时清除，attention 保存与实际 RunEvent 一致的 eventId 和 occurredAt。读取索引后回读 manifest 校验所有者和非终态状态，过期/脏成员会被清理。普通活跃 key 使用 3 小时滑动 TTL，存在待处理 question/permission 时整组运行态延长到 7 天，终态详情保留 24 小时；动态 scope/dedup/pending key 通过 registry 参与续期，pending list 自身最长 7 天，drain 使用 Lua 原子读取并删除。生产 32 MiB 详情预算固定为 input/scope/pending 等非快照数据最多 28 MiB、关键物化快照预留 4 MiB；输入初始化和后续 scope/pending 超过非快照预算时直接拒绝，避免基础数据挤占 reset 最小恢复空间。
- 单 Run durable 事件、runtime 事件或 snapshot 投影项超过 20,000，或 input + scope + pending + 两条 Stream + snapshot 规范化详情超过 32 MiB 时不依赖 Redis eviction：Lua 显式删除旧 Stream、递增 `resetGeneration`、更新 `earliestSeq/earliestRuntimeVersion/detailsTruncated` 并保留当前物化状态。过大的单个 payload 先做深度/字符串/集合规范化并标记 `snapshotTruncated`；累积文本 delta 超过单槽预算时保留当前规范化片段并同样触发 reset，不会静默覆盖。容量压力下先移除 latest/tool/diff/session-status/child/scope 等低价值投影，再只保留独立 USER 输入、从 JSON role 判定的最新 assistant message、与其 messageId 对应的最新可见 text part（delta 仅 `field=text`，full part 缺 type 时保守视为可见）以及 run-status；tool/reasoning part 和后到非 assistant message 不得挤掉最终可见回答。活跃 SSE 的旧 runtimeVersion 会收到 `RUNTIME_STREAM_TRUNCATED` reset。Redis 访问、Lua、JSON 或 manifest 缺失分别映射为 `RUNTIME_STATE_UNAVAILABLE` 或 `RUN_DETAILS_EXPIRED`，新模式不得回退 PostgreSQL/JVM 内存。
- `JdbcScheduledTaskRepository`：实现 scheduler 任务定义、用户计划、运行记录、due task 查询和 pending run 查询。
- RunEvent append-only：持久化层分配 `eventId` 和同一 run 内单调递增 `seq`，并发追加时通过 `(run_id, seq)` 唯一约束冲突后重读重试，支持 `runId + lastSeq` 增量读取；Session 级历史树按 `root_session_id` 读取跨 Run durable 状态事件。opencode raw event id 缺失时写入 `NULL`，避免 `"unknown"` 误去重。

## 测试环境 PostgreSQL

`test-agent-app` 的 `test` profile 会通过环境变量装配 PostgreSQL 测试库，并复用本模块 `db/migration` 下的 Flyway migration。持久化模块提供 Druid starter 依赖，实际连接信息和连接池大小由应用 profile 配置注入，不保存环境专属账号、密码或主机地址。

内部模型代理鉴权列改为企业中性命名时，两条已落库历史 migration 通过仅影响校验和的兼容注释保持原 Flyway checksum；`V20260716143000__rename_internal_model_auth_token_column` Java migration 按固定列位置识别历史列并重命名，新建数据库目标列已存在时幂等跳过。

## 测试覆盖

- `JdbcRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖 Workspace（含 `linux_server_id`、历史脏 `updated_at < created_at` 归一化）、Session、AgentSessionBinding、SessionMessage、Run、RunEvent、ExecutionNode、RoutingDecision 的保存和读取。
- `MyBatisCommonParameterRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖通用参数 MyBatis XML 查询、列表、按 ID 查询和仅更新 value。
- `JdbcRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖 Workspace（含 `linux_server_id`）、Session、AgentSessionBinding、SessionMessage、Run、RunEvent、ExecutionNode、RoutingDecision 的保存和读取。
- `MyBatisCommonParameterRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖通用参数 MyBatis XML 查询、列表、按 ID 查询、`SYS_DATA_ROOT_DIR` 三平台种子和仅更新 value。
- `MyBatisRunSessionScopeRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖 Run session scope 表、MyBatis XML upsert/query、按 root session 查询和 root/child session 映射；`PersistenceSqlConventionTest` 固化 Run session scope mapper 在 PostgreSQL `MERGE` 中必须显式 cast 时间参数。
- `MyBatisSessionHistoryRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖当前用户历史分页、创建人/Run/消息归因兜底、更新时间倒序、托管应用上下文 join、非托管工作区名称 fallback 和无成员关系仍返回历史。
- `MyBatisSessionRuntimeStateRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖用户级运行计数、终态 Run 排除、`question.asked` 待关注、`question.replied/rejected` 清除，以及不可见会话过滤。
- `MyBatisRunRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖 Run MyBatis XML 保存/读取、active-run 查询、stale active 候选排除 `REDIS_SUMMARY`、token/cost/source 字段映射、`saveIfStatus` 成功更新和状态不匹配时不覆盖终态。
- `MyBatisRunEventRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖 RunEvent MyBatis XML append、scope 列写入、`raw_event_id=NULL` 语义和 seq 单调分配。
- `MyBatisSessionTitleUpdateRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖 Session 标题 XML 条件更新成功与预期标题不匹配时不覆盖新标题。
- 运营分析原始事实扫描按 `storage_mode` 双读：legacy Diff 只读 `run_events`，`REDIS_SUMMARY` 只读 `runs.diff_*_count`，即使灰度期间残留 shadow 事件也不重复计数；新模式 USER/ASSISTANT 数量只读取 `content_kind=SUMMARY` 的终态摘要，残留 RAW shadow 消息同样排除。相关 XML 通过持久化模块编译、Flyway 集成和运行时服务单测覆盖；`AnalyticsQueryServiceTest` 固化空分母、满意率、采纳率、p95 和 CSV 字段口径。
- `PersistenceSqlConventionTest` 固化持久层 SQL 规则：存量 JDBC 文件只允许留在白名单，MyBatis mapper 不得使用注解 SQL。
- `MyBatisPublicAgentConfigRolloutRepositoryTest` 固化目标认领生成用户/trace/lease token 快照，并验证过期 lease 不能把目标误写为重试或已 dispose。
- `MyBatisReferenceRepositoryRepositoryIntegrationTest` 使用真实 Flyway + MyBatis 覆盖两表、并发初始化/推进 generation 单胜者、同服务器租约互斥与续租、过期 token/generation 写回拒绝、离线 `DEFERRED`/恢复和状态游标分页；`MyBatisReferenceRepositoryPostgresqlIntegrationTest` 覆盖 PostgreSQL 方言下的副本 upsert、认领和总体状态写回。
- SessionMessage/Run 覆盖 V16 token/cost 字段读写、parts_json 兼容、按 `(sessionId, remoteMessageId)` 查询以及最近非终态 Run 查询。
- RunEvent 覆盖 append-only seq 单调递增、并发追加唯一性、`runId + lastSeq` 增量读取、结构化 scope 列和 `(run_id, seq)` 唯一约束。
- Session 覆盖远端 opencode 映射、全局搜索、置顶排序、工作区会话分页和归档过滤。
- AgentSessionBinding 覆盖 upsert、按 agent 查询、远端 session 唯一约束和从旧 opencode 字段回填。
- ConfigurationManagement 覆盖 V7 migration、V8 默认用户授权、成员逻辑删除恢复、应用与仓库多对多关联、代码库英文名保存/查询、版本库类型字典与 `repository_type` 回填、版本库部署模式 `deployment_mode` 默认值和 MyBatis XML 保存/读取、通用参数默认值、工作空间创建进度表、应用工作空间保存和用户单 SSH key 唯一约束。
- ManagedWorkspace 覆盖 V9/V20260626120900 migration、版本工作区唯一性、每服务器副本 upsert、目标 commit、个人空间名称唯一性、最近使用偏好和同步审计保存。
- OpencodeProcessManagement 覆盖 V14 migration、V17 loopback 种子清理、拓扑读写、历史用户进程与后端 Java 进程时间戳归一化、健康容器查询、运行管理拓扑列表、manager-backend 连接列表、opencode server 进程分页筛选、绑定关联查询、用户绑定唯一约束、服务器端口唯一约束和容器管理进程一对一约束。
- RedisOpencodeProcessHeartbeatStore 覆盖 Java/manager 运行快照写入 Redis 的 key、索引、10 秒 TTL，Java latest snapshot、服务器级指标与 Java/JVM 指标按 `linuxServerId` 分流写入、未知 JSON 字段宽容读取、旧 JSON 缺新字段保持 `null` 和容器指标历史 key。
- `RedisConversationContextStoreTest` 覆盖同 slot SHA-256 token key、五类反向索引/generation、只读路由解析、签发 fence CAS、Session revoke gate、全局代次、Lua 原子保存与续期及 Redis 异常映射；`RedisConversationContextStoreIntegrationTest` 在提供真实 Redis 端口时验证完整 `OpencodeServerProcess` JSON 往返、Workspace/进程/全局失效、并发归档 gate CAS 回滚及 `beginIssue → invalidate/revoke → late save` 拒绝。
- `RedisRunCapacityPolicyTest` 固化 USER 输入专用 key、4 MiB 关键快照预留、单槽规范化上限以及 APPEND/PROJECT Lua 对 assistant role、text part 和显式 reset 的脚本契约。`RedisRunRuntimeStoreIntegrationTest` 在提供真实 Redis 端口时验证并发 append 的原子 seq/`${seq}-0` Stream、manifest 与 active 索引、容量截断后仍保留 USER/最终 assistant/text part/run-status 的物化 snapshot/reset、transient delta 聚合、durable/transient runtimeVersion 顺序、status/attention、动态 key TTL、scopeVersion、dedup、pending 字节记账/容量拒绝/原子 drain，并校验真实 Redis `noeviction` / `appendfsync everysec`；`RedisRunRuntimeIndexReservationTest` 验证跨 slot 恢复索引先于单 Run Lua 且始终使用最大保留窗口；`RedisRunOwnerLeaseIntegrationTest` 额外覆盖条件接管、终态拒绝和所有 fenced 写入口的旧 token 隔离。测试未提供真实 Redis 端口时会跳过，不能用 H2 或 mock 替代 Lua/Streams 原子行为验证。
- `RedisRunTerminalRetryStoreIntegrationTest` 验证 record/due 固定同 slot 与 Lua 原子写删契约，并使用真实 Redis 验证安全投影白名单、due 时间、generation 单调覆盖、旧重排拒绝、compare-delete 和不超过 24 小时的 TTL。
- ScheduledTask 覆盖时间戳 migration、三张 scheduler 表、运行记录分页筛选、due task 查询和会话来源预留字段读写；`MyBatisScheduledTaskRunRetentionRepositoryIntegrationTest` 额外覆盖七天边界、活动状态保留和 `ended_at` 索引。
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
新增或修改关系型 SQL 必须新增/调整 `mybatis/*.xml` 与 `com.enterprise.testagent.persistence.mybatis` 内部 mapper，不能继续扩展 `Jdbc*Repository` 或使用 MyBatis 注解 SQL；存量 JDBC 仓储后续按触点分批迁移。
JSON payload/capabilities 当前以文本列保存，未来切换 PostgreSQL JSONB 必须同步兼容策略和测试。
`ai_model_configs` 只保存平台托管的企业内模型目录，不保存模型调用密钥；密钥仍通过环境变量或配置中心注入，并由 runtime 模块同步到 opencode provider 配置引用。
`agent_session_bindings` 是新链路的 agent 远端 session 绑定主数据源；Session 的 `opencode_session_id` 与 `opencode_execution_node_id` 仅作为 `opencode` 兼容字段，新增 agent 不得继续扩展 `sessions` 列，新增查询或导出时不得默认暴露给前端 DTO；`pinned` 是平台 Session API 字段，默认旧数据未置顶。
RunEvent 追加可能来自 opencode stream、取消和 Diff 动作等多个线程；修改 `MyBatisRunEventRepository` 时必须保留并发 append 下 seq 单调且不重复、scope 列写入、`raw_event_id` 缺失为 `NULL` 的测试。修改 SessionMessage/Run 映射时必须覆盖 token/cost、parts_json 和 active-run 查询。
修改 `RedisRunRuntimeStore` 时必须使用真实 Redis 验证 Lua 原子性、durable seq 与 runtimeVersion 双 Stream、Hash/ZSET 物化、分页 tail、动态 key 滑动 TTL、7 天 attention/pending、active 索引清理、scope/dedup/pending、20,000 条/32 MiB 显式截断和 `run.snapshot.reset` 语义；禁止以 `MAXLEN`、LRU/LFU 或静默 eviction 代替显式截断。
