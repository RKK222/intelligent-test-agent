# test-agent-persistence

- 用户管理组合分页查询和“全部检索结果”有界 ID 解析由 `UserManagementQueryMapper.xml` / `MyBatisUserManagementQueryRepository` 实现，支持用户关键字、角色（含未分配角色）、组织、研发部门和部门筛选，并可在 SQL 中排除当前操作者；未修改 users/user_roles 表结构，也没有新增 Flyway migration。

- `V20260723145200__add_application_workspace_enabled.sql` 为应用工作空间配置增加默认启用的 `enabled` 字段；配置管理 MyBatis XML 负责该字段的查询、新增和更新，未新增 JDBC SQL。
- `V20260728160800__create_toolbox_click_tracking.sql` 新增工具盒子永久点击明细、工具累计和用户/工具 30 秒窗口状态三张表；主文件恢复并锁定企业已执行的 `-1966404877` 原始 checksum。旧 `V20260727203500` 原文保存在 `db/migration-compat/toolbox`，曾误发的当前版本 `-74327385` 幂等原文保存在 `db/migration-compat/toolbox-current-idempotent`，由 app 按已应用 version/checksum 隔离选择；删除用户时明细匿名化、窗口状态级联删除，累计保留，不写生产演示数据。
- `V20260728103000__create_app_source_snapshot_tables.sql` 新增应用源码 slot、固定内容 snapshot、服务器 replica、operation/step、cleanup 和 recent 表，结构化选择使用 PostgreSQL JSONB；初始 snapshot 约束为 `expires_at = accepted_at + 1..72` 整小时且索引摘要必须为 64 位十六进制；cleanup 到 operation/snapshot 的外键为 `DEFERRABLE INITIALLY DEFERRED`，并初始化只读 `OPENCODE_APP_SOURCE_ROOT=${SYS_DATA_ROOT_DIR}/agent-opencode/workspace/appsource/`。
- `V20260731115520__extend_app_source_retention.sql` 保留已经执行的 365 天扩容原始字节，`V20260731123600__cap_app_source_retention_at_one_week.sql` 再把最终 snapshot 整小时上限收紧为 168 小时（7 天），两者均不改历史数据；续期运行 SQL 位于 `AppSourceMapper.xml`，通过快照 expiry CAS 同步更新权威索引摘要和全部未开始 cleanup 的 `delete_at/next_retry_at`。
- `V20260728210000__index_in_flight_app_source_operations.sql` 为 dispatcher 周期恢复增加 `(status, accepted_at, operation_id)` 索引，使 `PENDING/RUNNING` stranded 扫描不随历史终态 operation 全表增长；迁移只新增索引，不写业务数据。
- `V20260804123000__create_personal_workspace_relocations.sql` 新增每个个人工作区唯一的跨服务器搬迁状态，保存源/目标服务器、源逻辑路径、租约、快照摘要/大小、安全错误和完成时间。目标完成由 MyBatis XML 在一个 PostgreSQL 事务内锁定搬迁事实并更新运行态 Workspace、个人工作区路径/基线提交和 `CLEANUP_PENDING`；旧源清理完成后才转 `SUCCEEDED`。再次换服不会覆盖尚待清理的旧搬迁，终态后才开启下一段搬迁。
- `V20260805132000__create_support_access_audit.sql` 新增问题排查授权与访问审计表；只保存平台会话/文件路径/User-Agent 的 SHA-256 摘要和必要业务快照，不保存平台/授权 Token、消息或文件正文。审计保留一年，用户删除时外键置空而快照保留。
- `db/migration-compat/lobehub-missing/V20260802173416__backfill_lobehub_model_gateway.sql` 只对已执行 `V20260801093854`、却漏掉 `V20260730090000` 的已知分叉加载；通过高版本幂等补偿创建模型目录、探测、每日聚合表和四个默认禁用参数，正常顺序库与空库不可见。
- `db/migration-compat/lobehub-missing-after-rollout/V20260803141754__backfill_lobehub_model_gateway_after_rollout.sql` 只对上述缺失仍存在、早期补偿未执行且 release `V20260803133000` 已落库的更高历史加载，避免倒序执行早期补偿；SQL 语义与早期补偿一致。

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
- `InactiveOpencodeProcessMapper.xml` 按 ACTIVE binding 和本机 Linux 服务器查询 `RUNNING/UNHEALTHY` 用户进程，以该用户全部来源 Run 的最大 `updated_at` 聚合最近 OpenCode 活动；无 Run 时回退 manager 权威 `started_at`。SQL 先收窄相关进程，再分别利用 `runs.triggered_by_user_id` 和 `sessions.created_by_user_id` 现有索引聚合直接归属与 legacy Run，避免按每个候选重复全表扫描；同时通过 `night_execution_tasks(owner_user_id,status,slot_start)` 现有索引排除执行窗口尚未结束的跨夜遗留任务和北京时间当天 `SCHEDULED/DISPATCHING` 任务。候选和闸门内精确复核都走该 MyBatis XML，不向存量进程 JDBC Repository 新增 SQL，也不新增数据库结构。
- Flyway migration，包含 PostgreSQL 16 所需的 Flyway database support。
- Repository 实现和数据库映射；新增或修改关系型 SQL 必须通过 MyBatis XML mapper。
- `UserDeletionMapper.xml` / `MyBatisUserDeletionRepository` 以 MyBatis XML 锁定目标用户、汇总受保护业务引用，并按外键顺序清理账号附属数据；不级联删除会话、工作区、进程或调度历史。`RedisTokenStore` 使用增量 `SCAN` 撤销目标用户上线前已签发的 Token，不执行阻塞式 `KEYS`，也不记录 Token key/value。
- Redis 会话运行上下文、Run 运行数据面、限流、幂等和运行心跳能力适配；用户进程运行管理与 manager 控制面在线状态依赖 Redis。用户级 OpenCode dispose 闸门与 `active:user`、`runtime-user` marker 使用同一 `{userId}` slot：新 Run 在一个 Lua 内先检查闸门再登记 active/marker，dispose 则先清理过期 active 成员再原子确认空闲并申请可续租 token，禁止跨 `{runId}`/`{userId}` slot 执行脚本。通用参数值不写入 Redis，运行态读取直接查询数据库。
- `RedisTokenStore` 在保存平台 Token 时同步写入 SHA-256 session marker，并使用相同绝对过期时间；删除、刷新或过期清理 Token 时同步删除 marker。原始 Token 不进入 marker key/value，XXL 只持有 digest。
- `RedisSupportAccessGrantStore` 以 Lua 原子轮换/撤销同一平台登录会话的当前排查授权，只保存 `sessionDigest/grantTokenDigest` 与有界 payload，TTL 与 grant 到期时间一致；查找时同时校验 token key 和 session 当前摘要，不降级 JVM 内存。
- `RedisWorkflowCapabilityStore` 在 `test-agent:workflow-capability:*` 前缀保存HMAC nonce摘要、一次性checkout ticket摘要和短期model grant摘要；Lua保证消费/续期/撤销原子性。取消run先写撤销墓碑再撤销现存grant，并使并发签发或刷新失败。该适配不保存Python conversation/task/report/event，也不新增关系型SQL或Flyway migration。

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
- `V20260718210000__extend_scheduler_user_plan.sql`：允许 USER_PLAN 专用任务不配置 Cron，为运行记录增加服务器执行亲和字段和到期扫描索引。
- `V20260718211000__create_night_execution_tasks.sql`：创建夜间任务、单会话待执行锁和 15 分钟时段容量占位表，包含幂等键、状态/时间约束、索引和中文注释。
- `V20260719210000__seed_night_execution_capacity_parameter.sql`：初始化可编辑全局通用参数 `NIGHT_EXECUTION_SLOT_CAPACITY=20`，供各 Java 实例启动加载和跨实例热刷新。
- `V20260722130000__migrate_night_execution_to_xxl.sql`：为夜间任务增加 `dispatch_attempt_id`、精确 owner backend process、租约到期和状态版本字段/索引；为 legacy Scheduled Run 增加启动 attempt、租约和 durable handoff 受理时间。版本晚于已交付的 `V20260721213000`，存量库可按序升级；短暂停机升级时把旧夜间 `PENDING/RUNNING/STOPPING USER_PLAN` 全部标记为 `SKIPPED`，历史关联字段保留 nullable，遗留 `DISPATCHING` 交给锚点优先的补偿机制收敛。
- `V20260724143000__add_night_execution_schedule_mode.sql`：为夜间任务增加非空 `schedule_mode` 和 `NIGHT_WINDOW/ADMIN_CUSTOM` 检查约束；存量行默认回填标准夜间模式，自定义模式仍走同一到期扫描但不写夜间容量表。
- `V20260717173000__create_public_agent_config_rollouts.sql`：创建公共 Agent/Skill 发布 rollout、服务器同步和存量进程目标表。
- `V20260717200000__harden_public_agent_config_rollout.sql`：为 rollout 保存发起人，为目标快照用户并增加认领 fencing token；历史目标按服务器、容器、端口回填用户归属，不写测试或演示数据。
- `V20260721213000__isolate_agent_config_rollout_scopes.sql`：把活动发布唯一锁拆为公共单锁和应用版本维度锁，并新增应用个人 worktree `AWAITING_USER` 补偿表；本地修改或合并冲突不再无限占用公共/其它应用发布。
- `V20260728100000__add_personal_application_rollout_scope.sql`：把 rollout 作用域约束扩展为 `PERSONAL_APPLICATION`，供个人拉取应用 Agent 后仅持久化当前用户的空闲检测与 dispose；不新增表、不写业务数据，也不加入公共/应用发布唯一锁。
- `V20260728160000__extend_public_agent_config_refresh.sql`：为 rollout 持久化共享运行副本恢复确认，并创建以 `agent_config_worktrees` 为外键的公共个人 worktree 补偿表；公共冲突不占主 rollout，认领和终态更新使用 fencing token。
- `V20260803133000__support_public_agent_config_rollout_supersede.sql`：为公共卡死 rollout 增加一对一替换审计链、`SUPERSEDED` 终态说明和 target `force_stop` 标记；历史行保持可空/默认 `false`，不自动改写或停止现场任务。
- `V20260804123000__create_personal_workspace_relocations.sql`：创建个人工作区跨服务器两阶段搬迁表、认领索引、外键和状态/服务器/次数/归档大小约束；不回填或自动改写现有 Workspace。`PersonalWorkspaceRelocationMapper.xml` 负责错配扫描、活动 Run 闸门、发现、租约认领、版本/用户/分支/源路径 fencing、目标三表切换、退避和可重复认领的旧源清理终态，未新增 JDBC SQL。
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
- `MyBatisConfigurationManagementRepository`：通过 `ConfigurationManagementMapper.xml` 实现配置管理表的应用只读查询、成员逻辑删除、仓库关联、版本库类型、版本库部署模式、版本库是否已有应用工作空间历史、工作空间和个人 SSH key 元数据持久化，是当前生产 Spring Bean。
- `MyBatisAppSourceRepository`：通过 `AppSourceMapper.xml` 实现 slot `SELECT FOR UPDATE`/乐观 CAS、snapshot JSONB、replica 只在不存在时建档并以 generation+owner+lease+合法状态流转 fencing、活租约行锁下的步骤推进和整条稳定 attempt 重置、operation 类型感知的 stranded 扫描、延迟 cleanup 认领和 recent selection；DOWNLOAD/UPDATE 兼容按全副本终态恢复历史脏状态，RETRY 必须存在 SERVER steps 且全部终态、不得残留 `PENDING/RUNNING` 目标后才成为候选。旧 `RETRY_QUEUED` 可领取并在新 attempt 回填稳定步骤，终态步骤防回退。`hasRepositoryHistory` 同时检查 slot/snapshot/operation/cleanup，供配置管理冻结源码仓库磁盘身份。
- AppSource `claimReplica` 在单条 MyBatis UPDATE 中匹配 exact repository/generation/operation/server、operation `PENDING/RUNNING` 和 SERVER timeline。普通 `PENDING/FAILED/STALE` 副本只有存在任一 `PENDING/RUNNING` 步骤才可领取；step code 故意不限，使过期 lease 可从任一中途步骤接管。仅过期 `RUNNING` 副本允许以已有任意状态 SERVER step 作为旧 attempt 恢复锚点，领取后仍由统一 attempt reset 清理终态时间线；终态 operation 的迟到 worker 不能重新制造 `RUNNING` 副本。
- `JdbcConfigurationManagementRepository`：配置管理存量 JDBC 实现已不再作为 Spring Bean，仅保留给旧集成测试和迁移窗口；其中 `repository_type` / `deployment_mode` 映射只为兼容新增非空列，后续配置管理 SQL 变更必须改 MyBatis XML。
- `MyBatisCommonParameterRepository`：当前 MyBatis 试点实现，按参数英文名和平台读取、列出并更新通用参数；SQL 位于 `src/main/resources/mybatis/CommonParameterMapper.xml`。
- `MyBatisAiRunFeedbackRepository`：通过 `AiRunFeedbackMapper.xml` 实现 Run 反馈保存与 `(user_id, run_id)` 单查/批查，新记录不写 `message_id`；`MyBatisAiMessageFeedbackRepository` 保留历史消息兼容。
- `MyBatisAnalyticsRepository`：通过 `AnalyticsMapper.xml` 实现原始事实读取、hourly/daily rollup 写入、直方图、水位/锁、用户/组织/满意度/异常明细查询；Diff 事实按 storageMode 双读 legacy 事件与新模式 Run 计数，排除 shadow 事件双计数；看板查询只读 rollup 表，不返回 prompt、assistant 原文或费用字段。
- `MyBatisToolboxClickRepository`：通过 `ToolboxClickMapper.xml` 先按全局 `eventId` 幂等插入永久明细，再原子竞争用户/工具 30 秒窗口；竞争成功者原子递增累计并把该明细标记为 counted。PostgreSQL 使用 `ON CONFLICT`，H2 PostgreSQL 模式使用 `MERGE`，业务代码不包含 JDBC SQL。
- `MyBatisDatabaseIdentityMaintenanceRepository`：通过 `DatabaseIdentityMapper.xml` 实现 identity 运维护口，查询 `pg_sequences` 当前值与 `max(id)`、执行 `ALTER TABLE ... RESTART WITH`；SQL 注入防护依赖白名单表名与服务层校验。
- `MyBatisRunSessionScopeRepository`：通过 `RunSessionScopeMapper.xml` 保存 Run root scope 和当前 Run root/child session 清单，供 SSE/HTTP snapshot 按当前 Run 子树恢复消息，并支持按 `root_session_id` 汇总 Session 历史树；mapper 中 `MERGE ... USING (VALUES ...)` 的时间参数显式 cast 为 `timestamp`，避免 PostgreSQL 将未定型参数推断为 `text`。
- `MyBatisSessionHistoryRepository`：通过 `SessionHistoryMapper.xml` 实现当前用户历史会话只读分页、单会话归属校验和指定工作区历史，按 `sessions.created_by_user_id`、`runs.triggered_by_user_id`、`session_messages.sender_user_id` 归因；默认仅 ACTIVE，排查入口显式请求时包含 ARCHIVED，但始终排除内部 SIDE_QUESTION。查询 left join 托管应用/工作区/版本上下文，排序严格按 `updated_at desc, id desc`，不复用 `JdbcSessionRepository` 的 pinned 排序 SQL。
- `MyBatisUserWorkspaceQueryRepository` / `UserWorkspaceQueryMapper.xml`：按 ACTIVE 个人工作区或上述会话归因分页查询用户工作区，历史空白名称只在读模型回退 workspaceId。`MyBatisSupportAccessRepository` / `SupportAccessMapper.xml` 保存授权、筛选审计与执行一年期清理；排查单号由业务层生成，不再查询历史授权；所有关系 SQL 均在 MyBatis XML。
- `MyBatisSessionRuntimeStateRepository`：通过 `SessionRuntimeStateMapper.xml` 实现当前用户历史会话运行态只读摘要，复用历史会话可见性归因，只返回每个 ACTIVE 会话最近一个 `PENDING/RUNNING/CANCELLING` Run，并按最新 `question.asked/replied/rejected` 派生 `QUESTION` 待关注标记；不新增数据库表或 Flyway migration。
- `MyBatisPublicAgentConfigRolloutRepository`：通过 `PublicAgentConfigRolloutMapper.xml` 持久化作用域发布闸门、服务器同步、个人 worktree 补偿和本机目标认领；公共纠错替换以事务内 CAS 把旧 `DRAINING` 变为 `SUPERSEDED`、失效旧租约并创建唯一新活动任务，插入新 target 时仅按旧目标与新快照的用户/服务器/容器/端口/PID/启动时间全匹配派生 `force_stop`，前端不能直接指定。状态查询按服务器额外读取最多 200 个未进入 `DISPOSED/ABANDONED` 的目标，left join 用户表取得内部 userId/username，并返回进程身份、重试和错误诊断；不查询统一认证号或 Session 内容。record 构造器中的 `port/retryCount` 使用 MyBatis `_int` 原始类型映射，避免查询排空任务时以 `Integer` 反射调用失败；运行时登记目标时按当前 `linuxServerId` 和分页仓储允许的 200 条上限读取平台进程映射；用户门禁读取发布窗口的用户快照、已完成应用 rollout 的待 dispose 目标，以及 `PERSONAL_APPLICATION.initiated_by_user_id = 当前用户` 的单用户任务，不会把个人拉取门禁扩大到同应用其他成员；应用版本与个人工作空间通过 `application_workspace_versions.version_id = personal_workspaces.app_workspace_version_id` 关联，目标和 worktree 认领都按 `linuxServerId + leaseToken` fencing，Workspace 根目录查询复用既有 execution node、opencode binding、Session 和 Workspace 关系。
- `MyBatisSessionRuntimeStateRepository`：通过 `SessionRuntimeStateMapper.xml` 实现当前用户历史会话运行态只读摘要，复用历史会话可见性归因，只返回每个 ACTIVE 会话最近一个 `PENDING/RUNNING/CANCELLING` Run；按顶层 `requestId/requestID/id/permissionId/questionId` 匹配 asked 与后到 reply/reject，分别派生 `QUESTION/PERMISSION` 待关注标记，不能误取 question option 等嵌套 `id`；不新增数据库表或 Flyway migration。
- `MyBatisPublicAgentConfigRolloutRepository`：通过 `PublicAgentConfigRolloutMapper.xml` 持久化发布硬闸门、服务器同步和本机目标认领；record 构造器中的 `port/retryCount` 使用 MyBatis `_int` 原始类型映射，避免查询排空任务时以 `Integer` 反射调用失败；运行时登记目标时按当前 `linuxServerId` 和分页仓储允许的 200 条上限读取平台进程映射；用户门禁读取发布窗口的用户快照，目标认领按 `linuxServerId + leaseToken` fencing，Workspace 根目录查询复用既有 execution node、opencode binding、Session 和 Workspace 关系。
- `MyBatisRunSummaryPersistenceRepository`：通过 `RunSummaryMapper.xml` 写无原文 Run 锚点、终态双摘要；默认 legacy Scheduled Run 也先写稳定 clientRequestId，和 Redis 摘要模式共用 `(session_id, client_request_id)` 唯一索引。仅有锚点时通过 attempt/租约 CAS 认领并复用同一 runId/dispatchMessageId 继续启动，远端副作用前续租，异步 prompt handoff 或远端稳定消息确认后再按 attempt 写受理时间；通用 stale legacy 扫描排除带 attempt 且尚未受理的 Scheduled 锚点，避免抢先终态化。适配器还从既有 `runs.dispatch_message_id` 读取稳定用户轮次锚点与远端 ID 定位快照，供 Redis 详情过期后的目标 Run 恢复和显式 Diff 动作使用；accepted/rejected 计数使用单条 UPDATE，且不写 `run_events`。终态 CAS 只允许一次较高事件序号的晚到刷新；跨终态状态仅允许已落库的 `FAILED + TRANSPORT_ERROR` 被 `REMOTE_ROOT/RECOVERY_REMOTE_ROOT` 事实纠正，禁止其它来源任意翻转。
- `ScheduledTaskRunRetentionMapper` / `ScheduledTaskRunRetentionMapper.xml`：按 `ended_at` 和终态 status 清理超过 7 天的 scheduler 运行记录，显式排除活动状态。
- `MyBatisScheduledTaskRunRetentionRepository`：实现 scheduler 运行记录保留策略 domain 端口，供框架维护任务调用。
- `ScheduledTaskMapper` / `ScheduledTaskMapper.xml` / `MyBatisScheduledTaskRepository`：保留旧 scheduler 任务定义和运行记录的历史兼容持久化；生产不再调用其 Cron、手工或 `USER_PLAN` 扫描/认领能力。
- `NightExecutionTaskMapper` / `NightExecutionTaskMapper.xml` / `MyBatisNightExecutionTaskRepository`：持久化双模式定时任务、owner/clientRequestId 幂等锁、会话锁和标准夜间全局时段容量；`schedule_mode` 以枚举名往返。按 `slot_start, created_at` 有限扫描两种模式中已到期且窗口未结束的 `SCHEDULED`，以 `status + state_version + target_linux_server_id` 原子认领 attempt，所有续租/完成/回退 SQL 匹配 `task_id + DISPATCHING + attemptId`，续租还要求 `window_end > now`，作为 Run 创建副作用前的最终窗口 fencing。PostgreSQL 首次创建使用事务级 advisory lock；30 天终态清理再次匹配状态、更新时间和 stateVersion，不能删除扫描后刚被用户关闭/更新的任务。
- `RedisRunTerminalRetryStore`：独立保存 PostgreSQL 终态事务失败后的已清洗 `RunTerminalProjection`。record 与全局 due ZSET 统一使用固定 `{terminal-retry}` hash tag；保存 Lua 按 `terminalProjectionVersion → lastEventSeq → failedAttempts/nextAttemptAt` 单调更新，旧 worker 不能覆盖晚到纠正版；删除 Lua 只在完整白名单 JSON 仍等于调用方处理记录时执行 `ZREM + DEL`，旧 worker 不能删除新版。缺失/非法 due member 的自愈 Lua 也只在 record 仍不存在时移除索引，不会误删并发新记录。每 Run JSON 使用 24 小时内的绝对 TTL，due ZSET 只保存 runId 和 `nextAttemptAt`；显式白名单序列化形状不包含 prompt、完整回答、parts、reasoning、工具输入输出或原始事件，Redis 不可用时返回 `RUNTIME_STATE_UNAVAILABLE`，不回退数据库或 JVM 内存队列。
- `JdbcCommonParameterRepository`：通用参数存量 JDBC 实现已不再作为 Spring Bean，仅保留给旧集成测试直接构造；后续通用参数 SQL 变更必须改 MyBatis XML。
- `JdbcWorkspaceCreateOperationRepository`：实现设置页创建应用工作空间进度保存、步骤更新、成功/失败记录和按 `operationId` 查询。
- `JdbcManagedWorkspaceRepository`：实现应用版本工作区、每服务器副本、目标 commit、个人工作区、最近使用偏好和同步审计持久化。
- `JdbcOpencodeProcessManagementRepository`：实现 opencode 用户进程管理拓扑、用户进程、用户绑定持久化，以及超级管理员运行管理页需要的拓扑列表、连接列表、进程分页筛选和绑定关联查询；读取历史用户进程或后端 Java 进程时会兼容 `updated_at < created_at` 的脏数据并按 `created_at` 归一化，避免旧记录阻断状态查询、manager 注册和重新初始化。
- `MyBatisOpencodeProcessReservationLockPort`：通过 XML `SELECT ... FOR UPDATE` 固定先锁用户、再锁目标 Linux 服务器，供初始化短事务串行化同用户绑定和同服务器端口预留；manager WebSocket 调用不在事务内执行。
- `MyBatisOpencodeProcessAtomicMutationPort`：通过 MyBatis XML 同时对 `opencode_server_processes` 与 `user_opencode_process_bindings` 做条件更新；首次预留写入 `STARTING + ACTIVE`，迁移保留原 `processId/createdAt`，并以旧服务器/容器/端口及 `status/PID/traceId` 生命周期代次做 CAS。binding 更新不命中时事务整体回滚，禁止只迁移 process。该能力复用 V14 既有表和唯一约束，不新增 Flyway migration。
- `RedisOpencodeProcessHeartbeatStore`：保存 Java 后端运行快照、manager 运行快照和 opencode server 进程运行心跳。Java/manager 快照 TTL 固定 10 秒，供运行管理页和 manager 后端列表发现识别在线实例；Java 后端 latest snapshot 按 `backendProcessId` 保存，服务器在线集合和指标历史按稳定 `linuxServerId` 分组；Java 服务器级指标按 `test-agent:runtime-metrics:server:{linuxServerId}` 保存，包含 CPU/load/内存/swap/磁盘字段；Java 进程与 JVM 指标按 `test-agent:runtime-metrics:backend:{linuxServerId}` 保存，包含进程 CPU/RSS/FD、heap/non-heap、direct/mapped、GC delta 和线程字段；旧 `backendProcessId` JVM key 仅供兼容接口回退读取，容器指标按 `test-agent:runtime-metrics:container:{containerId}` 保存；Redis 反序列化忽略未知字段，旧样本缺失的新字段保持 `null`；opencode server 进程心跳 key 保留 5 分钟 TTL。Redis 是系统必需依赖，不再提供 no-op 心跳存储。
- `RedisConversationContextStore`：实现 `ConversationContextStore` 领域端口；所有 key 使用 `{conversation-context}` hash tag，token key 只含原始 `contextToken` 的 SHA-256 摘要。Lua 原子维护 token、用户+Session/用户/Session/Workspace/进程五类 ZSET 反向索引及 generation 快照；索引 score 为 token 绝对过期时间，保存、续期和失效时会先删除已过期成员，避免活跃业务实体的索引成员无界累积。权威读取前的 `beginIssue` 同时检查 Session revoke 和 user mutation gate 并捕获签发 fence、资源和全局代次，`saveIfCurrent` 还会检查 Workspace mutation gate；`resolveForRouting`/`touch` 同样拒绝任一关联 gate。Session 归档使用逐归档 token 的 Redis Set gate；用户权限及 Workspace 可信字段变更使用覆盖关系型写入窗口的 mutation gate，完成脚本原子执行“再次失效 + `SREM` 自己的 gate token”，数据库失败只释放自己的 token，Redis 完成失败则保留 gate fail-closed。三类 gate 均设置 24 小时 TTL，避免异常后永久锁死；generation 不设 TTL，确保 gate 过期后旧 token 仍不能复活。全局可信路径变化通过 O(1) generation 失效。Redis 访问、脚本或 JSON 序列化异常统一映射为 `RUNTIME_STATE_UNAVAILABLE`，不回退 PostgreSQL 或 JVM 内存。
- `RedisTokenStore` / `TokenStoreConfig`：用户 Token 存储同时实现 `TokenSessionMarkerStore`，用 SHA-256 digest marker 支撑 XXL 每请求校验；marker 与 Token 同寿命并在删除时同步失效。
- `RedisRunRuntimeStore`：实现 `RunRuntimeStore` 领域端口。单 Run 的 manifest、input、durable `events` Stream、全事件 `runtime-events` Stream、snapshot Hash + order ZSET、动态 key registry、scope、dedup、pending 和 15 秒 owner lease key 统一使用 `{runId}` hash tag。`clientRequestId` 在锚点 INSERT 前只声明 30 秒 crash window，锚点成功后用 compare-confirm 延长到幂等保留期；初始化后、锚点前退出的孤儿由恢复扫描在 30 秒保护期后调用 `discardBeforeDispatch`，不会阻塞七天或被当作已成功 Run 返回。plain owner claim 原子拒绝终态 Run 并保留同 owner 重入 token；条件接管同时校验活跃 manifest 的状态/版本/seq/容量/attention/更新时间快照并总是提升 token。durable/transient 事件、远端 Session 绑定及 scope/dedup/pending 写入均在各自 Lua 的首个副作用前校验 owner + token，旧 token 返回稳定冲突且不续期索引；pending 字节计入统一详情预算，append 原子更新 `pendingBytes/detailBytes/updatedAt`，drain 原子扣减并移出 registry。claim/renew 返回的本地截止时间按 Redis 请求前时刻计算。未写锚点的 Run 使用 `discardBeforeDispatch` 清理详情和 active/history/client 索引。durable Stream ID 为 `${seq}-0`；runtime Stream 同时包含 durable/transient，ID 为 `${runtimeVersion}-0`。
- 物化 snapshot 不是最近 N 条日志：初始化时先用 Redis input 生成本轮 USER `message.updated`，并使用独立 `p:user-input:*` key，使 reset 不依赖浏览器乐观消息且容量淘汰不会把输入当作普通旧消息删除；后续 message/part/entity 使用稳定 SHA-256 projection key 覆盖，标识兼容顶层 ID 与真实 `info.id` / `part.messageID` / `part.id`。`message.part.delta` 按 part + `field` 分别聚合，full part update/remove 清理该 part 所有 field delta，`assistant.message.delta` 同样聚合文本。Todo、permission/question、tool、Diff、scope 和 Run status 保留当前可见状态；Hash 存 projection，ZSET 保 reducer 应用顺序。外部 snapshot 写入必须同时 CAS `barrierSeq + runtimeVersion`，任一版本变化都拒绝覆盖新投影。SSE 首帧总是发送完整 snapshot reset，后续按 runtimeVersion 分页读取 `runtime-events`；live bus 只负责低延迟唤醒。
- active Run 维护用户、Session、服务器索引及用户运行态 marker；这些跨 slot 索引在单 Run 初始化 Lua 前先按“所有运行态中最长物理 TTL 的两倍”保守登记，后续也不缩短 TTL/score。这样即使上次刷新时 Run 已进入 7 天 pending 窗口，下一次最长 TTL 事件 Lua 提交后 Java 立即退出，索引仍覆盖完整恢复窗口；服务器/用户恢复读取会再次预留全部索引，初始化 Lua 失败产生的悬空成员由普通读路径回读 manifest 清理。事件 Lua 在分配 runtimeVersion、写 Stream 和更新 snapshot 之前校验 status 转换，终态后的晚到非终态事件不会进入 `runtime-events`、run-status projection 或 live bus（仍允许终态之间的幂等纠正）。manifest 对外仍投影最新一个 attention；全部未决 question/permission 使用同一 Run hash tag 下的独立 Hash + order ZSET，按 `SHA-256(类型 + requestId)` 区分同 ID 的两类请求，reply/reject 只移除匹配项，回复最新请求后恢复更早请求及其真实 eventId/occurredAt。集合字节计入 `detailBytes` 和非快照预算，条目最多 `min(1024, maxDurableEvents)`，超长请求 ID 只在集合中保存摘要，超限按最旧项淘汰并触发显式 details truncation；终态统一清空。读取索引后回读 manifest 校验所有者和非终态状态，过期/脏成员会被清理。普通活跃 key 使用 3 小时滑动 TTL，存在待处理 question/permission 时整组运行态延长到 7 天，终态详情保留 24 小时；动态 scope/dedup/pending/attention key 通过 registry 参与续期，pending list 自身最长 7 天，drain 使用 Lua 原子读取并删除。生产 32 MiB 详情预算固定为 input/scope/pending/attention 等非快照数据最多 28 MiB、关键物化快照预留 4 MiB；输入初始化和后续 scope/pending 超过非快照预算时直接拒绝，避免基础数据挤占 reset 最小恢复空间。
- 单 Run durable 事件、runtime 事件或 snapshot 投影项超过 20,000，或 input + scope + pending + 两条 Stream + snapshot 规范化详情超过 32 MiB 时不依赖 Redis eviction：Lua 显式删除旧 Stream、递增 `resetGeneration`、更新 `earliestSeq/earliestRuntimeVersion/detailsTruncated` 并保留当前物化状态。过大的单个 payload 先做深度/字符串/集合规范化并标记 `snapshotTruncated`；累积文本 delta 超过单槽预算时保留当前规范化片段并同样触发 reset，不会静默覆盖。容量压力下先移除 latest/tool/diff/session-status/child/scope 等低价值投影，再只保留独立 USER 输入、从 JSON role 判定的最新 assistant message、与其 messageId 对应的最新可见 text part（delta 仅 `field=text`，full part 缺 type 时保守视为可见）以及 run-status；tool/reasoning part 和后到非 assistant message 不得挤掉最终可见回答。活跃 SSE 的旧 runtimeVersion 会收到 `RUNTIME_STREAM_TRUNCATED` reset。Redis 访问、Lua、JSON 或 manifest 缺失分别映射为 `RUNTIME_STATE_UNAVAILABLE` 或 `RUN_DETAILS_EXPIRED`，新模式不得回退 PostgreSQL/JVM 内存。
- RunEvent append-only：持久化层分配 `eventId` 和同一 run 内单调递增 `seq`，并发追加时通过 `(run_id, seq)` 唯一约束冲突后重读重试，支持 `runId + lastSeq` 增量读取；Session 级历史树按 `root_session_id` 读取跨 Run durable 状态事件。opencode raw event id 缺失时写入 `NULL`，避免 `"unknown"` 误去重。

### Agent & Skill Hub

- `MyBatisAgentSkillHubRepository` / `AgentSkillHubMapper.xml` 保存内容寻址 GZIP 制品、每个远端 commit 的不可变修订、发布依赖、应用级引用和更新操作。
- `V20260725143000__create_agent_skill_hub.sql` 创建六张 Hub 表；`V20260725230000__support_hub_reference_removal.sql` 增加 `PENDING_REMOVE` 并允许引用解除时级联清理临时更新操作。相同 canonical 内容按 SHA-256 去重，逻辑修订仍按 `(asset_id, source_commit_hash)` 保留每次 push 身份。
- `V20260806143000__classify_skill_hub_assets.sql` 为资产增加 Skill 一级/二级事项分类及最近分类操作者、时间；历史与新资产默认 `OTHER`，数据库约束只允许 `TEST` 的四类测试事项和 `CODE/WHITE_BOX_ANALYSIS` 组合，`WORKER/OTHER` 当前不带二级事项。分类更新 SQL 继续位于 `AgentSkillHubMapper.xml`，不会被后续 push 覆盖。
- `V20260806190000__persist_public_skill_hub_snapshots.sql` 新增公共内置修订和当前提交状态表，公共正文继续复用 `agent_skill_hub_artifacts` 按 SHA-256 去重。状态切换在事务内使用提交 compare-and-set，多 Java 对账时旧共享副本不能把较新公共目录回退；历史公共 revision 继续保留，Hub 读请求不再执行 Git 命令。
- `V20260806190500__classify_public_skill_hub_snapshots.sql` 为公共 Git Skill 增加独立的逻辑资产分类表；首次入库默认 `OTHER`，超级管理员分类不会被后续 commit 快照覆盖，Agent 不写入该表。
- 多资产引用批量保存以及“引用状态 + 更新操作终态”在同一 Spring 事务中提交；全量目录可按目标应用工作空间投影 `PENDING_PUSH/ACTIVE/PENDING_REMOVE/UPDATE_CONFLICT`，当前应用过滤、有效引用应用计数和消费者投影均排除 `PENDING_REMOVE`，待删除引用也不进入更新角标；同一记录从取消状态恢复时同步更新目标路径和别名。H2 集成测试覆盖 mapper CRUD/发布/引用/取消与恢复状态/引用方查询、公共快照 CAS、公共 Skill 跨 commit 分类及 PostgreSQL `bytea` 映射，完整 migration 链由真实 PostgreSQL 启动验证。

## 测试环境 PostgreSQL

`test-agent-app` 的 `test` profile 会通过环境变量装配 PostgreSQL 测试库，并复用本模块 `db/migration` 下的 Flyway migration。持久化模块提供 Druid starter 依赖，实际连接信息和连接池大小由应用 profile 配置注入，不保存环境专属账号、密码或主机地址。

内部模型代理鉴权列改为企业中性命名时，两条已落库历史 migration 通过仅影响校验和的兼容注释保持原 Flyway checksum；`V20260716143000__rename_internal_model_auth_token_column` Java migration 按固定列位置识别历史列并重命名，新建数据库目标列已存在时幂等跳过。`V20260722180000__add_internal_model_token_definitions.sql` 新建数据库 identity 主键的 `internal_model_tokens`，给 Provider 增加 `RESTRICT` 外键，并把非空旧全局值迁移成单个共享“默认 Token”；旧单例表继续保留，但新运行时只读取联表快照。

## 测试覆盖

- 通用 H2 PostgreSQL 模式测试固定迁移到 `V20260715213000` 这一最后兼容基线；后续含 `timestamptz`、部分表达式索引和 `ON CONFLICT DO UPDATE` 的完整生产链由真实 PostgreSQL 集成测试与应用启动验证。旧 H2 用例只按 mapper 所需补列或单独执行可兼容 migration，历史 V17 本机种子改由 `src/test/resources/db/fixture` 注入；测试不会修改已发布 migration 及其 checksum，也不会依赖生产 migration 写入开发用户。
- `MyBatisPersonalWorkspaceRelocationPostgresqlIntegrationTest` 在 PostgreSQL 16 上执行完整 Flyway 链，验证错配扫描、ACTIVE Run 阻断、租约状态、目标三表原子切换，以及 `CLEANUP_PENDING` 在连续换服时不被下一段搬迁覆盖；`MyBatisPersonalWorkspaceRelocationRepositoryTest` 固化 MyBatis XML 条件和目标更新顺序。
- `JdbcRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖 Workspace（含 `linux_server_id`、历史脏 `updated_at < created_at` 归一化）、Session、AgentSessionBinding、SessionMessage、Run、RunEvent、ExecutionNode、RoutingDecision 的保存和读取。
- `MyBatisCommonParameterRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖通用参数 MyBatis XML 查询、列表、按 ID 查询和仅更新 value。
- `MyBatisUserManagementQueryRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式加载真实 MyBatis XML，覆盖组合分页、未分配角色及按筛选返回有界 ID 并排除操作者；`MyBatisUserDeletionRepositoryIntegrationTest` 覆盖无业务用户的角色、登录日志、应用成员清理和受保护业务引用阻断；`RedisTokenStoreTest` 覆盖增量扫描只删除目标用户 Token。
- `JdbcRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖 Workspace（含 `linux_server_id`）、Session、AgentSessionBinding、SessionMessage、Run、RunEvent、ExecutionNode、RoutingDecision 的保存和读取。
- `MyBatisCommonParameterRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖通用参数 MyBatis XML 查询、列表、按 ID 查询、`SYS_DATA_ROOT_DIR` 三平台种子和仅更新 value。
- `MyBatisRunSessionScopeRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖 Run session scope 表、MyBatis XML upsert/query、按 root session 查询和 root/child session 映射；`PersistenceSqlConventionTest` 固化 Run session scope mapper 在 PostgreSQL `MERGE` 中必须显式 cast 时间参数。
- `MyBatisSessionHistoryRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖当前用户历史分页、单会话/工作区归属、创建人/Run/消息归因兜底、默认隐藏/显式包含 ARCHIVED、SIDE_QUESTION 持续排除、用户工作区范围、更新时间倒序、托管应用上下文 join、非托管工作区名称 fallback 和无成员关系仍返回历史。`SupportAccessRepositoryIntegrationTest` 从迁移前基线执行新 migration，覆盖授权/审计写读、撤销和一年期清理。
- `MyBatisSessionRuntimeStateRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖用户级运行计数、终态 Run 排除、真实 asked `id` / replied `sessionID -> requestID` 形式的 `QUESTION/PERMISSION` 待关注与清除、以 Run `seq` 抵御 occurredAt 回拨、缺失/空白 ID 旧事件兜底、不同 request ID 回复隔离、嵌套 option id 隔离，以及不可见会话过滤；`MyBatisSessionRuntimeStatePostgresqlIntegrationTest` 通过 Testcontainers 验证生产 jsonb/databaseId 分支和完整 Flyway 链；`RedisRunRuntimeStoreIntegrationTest` 覆盖 durable/transient 的并发未决 attention、同 ID 跨类型隔离、逐个回复回退、字节/数量容量边界、scope/snapshot 总量账本和终态清理。
- `MyBatisRunRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖 Run MyBatis XML 保存/读取、active-run 查询、stale active 候选排除 `REDIS_SUMMARY`、token/cost/source 字段映射、`saveIfStatus` 成功更新和状态不匹配时不覆盖终态。
- `MyBatisRunEventRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖 RunEvent MyBatis XML append、scope 列写入、`raw_event_id=NULL` 语义和 seq 单调分配。
- `MyBatisInternalModelProviderRepositoryIntegrationTest` 覆盖旧全局 Token 迁移为共享定义、Provider 关联保存、改名/轮换保持关系、引用删除受阻、解除引用后删除，以及名称更新时保留外部 Token 原值。
- `MyBatisSessionTitleUpdateRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式执行 Flyway migration，覆盖 Session 标题 XML 条件更新成功与预期标题不匹配时不覆盖新标题。
- 运营分析原始事实扫描按 `storage_mode` 双读：legacy Diff 只读 `run_events`，`REDIS_SUMMARY` 只读 `runs.diff_*_count`，即使灰度期间残留 shadow 事件也不重复计数；新模式 USER/ASSISTANT 数量只读取 `content_kind=SUMMARY` 的终态摘要，残留 RAW shadow 消息同样排除。相关 XML 通过持久化模块编译、Flyway 集成和运行时服务单测覆盖；`AnalyticsQueryServiceTest` 固化空分母、满意率、采纳率、p95 和 CSV 字段口径。
- `PersistenceSqlConventionTest` 固化持久层 SQL 规则：存量 JDBC 文件只允许留在白名单，MyBatis mapper 不得使用注解 SQL。
- `MyBatisPublicAgentConfigRolloutRepositoryTest` 固化目标认领生成用户/trace/lease token/force-stop 快照，并验证过期 lease 不能把目标误写为重试或已 dispose；同时检查个人拉取门禁只通过 `PERSONAL_APPLICATION + initiated_by_user_id` 命中发起用户，并校验未排空用户明细映射。`MyBatisPublicAgentConfigRolloutPostgresqlIntegrationTest` 在 PostgreSQL 16/Testcontainers 上执行完整 Flyway 链，验证旧/新 rollout 原子双向关联、旧租约封存、唯一活动门禁、只有六维进程身份完全匹配的新目标派生 `force_stop=true`，以及状态查询返回对应 username/forceStop 诊断。
- `MyBatisReferenceRepositoryRepositoryIntegrationTest` 使用真实 Flyway + MyBatis 覆盖两表、并发初始化/推进 generation 单胜者、同服务器租约互斥与续租、过期 token/generation 写回拒绝、离线 `DEFERRED`/恢复和状态游标分页；`MyBatisReferenceRepositoryPostgresqlIntegrationTest` 覆盖 PostgreSQL 方言下的副本 upsert、认领和总体状态写回。
- `MyBatisAppSourceRepositoryIntegrationTest` 使用 H2 PostgreSQL 模式覆盖 XML mapper 的 slot 乐观冲突、结构化路径往返、snapshot 状态/摘要 CAS、副本首次建档与租约 fencing、旧/新步骤领取和 attempt reset/upsert、全终态步骤 operation 绑定、stranded 扫描、恢复索引、recent/cleanup；`MyBatisAppSourcePostgresqlIntegrationTest` 原样执行完整 PostgreSQL Flyway 链和真实 reset/backfill SQL，验证 JSONB、整小时过期/十六进制摘要约束、步骤终态保护、global/server 部分唯一索引、在途索引和业务事务 cleanup 第一写的延迟外键。
- AppSource mapper 认领测试额外覆盖 exact operation 绑定、非终态步骤门禁、过期 `RUNNING` attempt 接管，以及 PostgreSQL 并发屏障下“worker 读 operation 后暂停、另一事务先终态化步骤再终态化 operation、迟到 claim 被拒绝且副本不变”。
- SessionMessage/Run 覆盖 V16 token/cost 字段读写、parts_json 兼容、按 `(sessionId, remoteMessageId)` 查询以及最近非终态 Run 查询。
- RunEvent 覆盖 append-only seq 单调递增、并发追加唯一性、`runId + lastSeq` 增量读取、结构化 scope 列和 `(run_id, seq)` 唯一约束。
- Session 覆盖远端 opencode 映射、全局搜索、置顶排序、工作区会话分页和归档过滤。
- AgentSessionBinding 覆盖 upsert、按 agent 查询、远端 session 唯一约束和从旧 opencode 字段回填。
- ConfigurationManagement 覆盖 V7 migration、V8 默认用户授权、成员逻辑删除恢复、应用与仓库多对多关联、代码库英文名保存/查询、版本库类型字典与 `repository_type` 回填、版本库部署模式 `deployment_mode` 默认值和 MyBatis XML 保存/读取、通用参数默认值、工作空间创建进度表、应用工作空间保存和用户单 SSH key 唯一约束。
- ManagedWorkspace 覆盖 V9/V20260626120900 migration、版本工作区唯一性、每服务器副本 upsert、目标 commit、个人空间名称唯一性、最近使用偏好和同步审计保存。
- OpencodeProcessManagement 覆盖 V14 migration、V17 loopback 种子清理、拓扑读写、历史用户进程与后端 Java 进程时间戳归一化、健康容器查询、运行管理拓扑列表、manager-backend 连接列表、opencode server 进程分页筛选、绑定关联查询、用户绑定唯一约束、服务器端口唯一约束和容器管理进程一对一约束。
- `MyBatisOpencodeProcessReservationLockPostgresqlIntegrationTest` 使用真实 PostgreSQL 覆盖用户/服务器 `FOR UPDATE` 锁顺序、首次 process/binding 原子预留、同用户并发单胜者、不同用户同服务器端口互斥，以及迁移双表 CAS 成功与冲突整笔回滚。
- RedisOpencodeProcessHeartbeatStore 覆盖 Java/manager 运行快照写入 Redis 的 key、索引、10 秒 TTL，Java latest snapshot、服务器级指标与 Java/JVM 指标按 `linuxServerId` 分流写入、未知 JSON 字段宽容读取、旧 JSON 缺新字段保持 `null` 和容器指标历史 key。
- `RedisConversationContextStoreTest` 覆盖同 slot SHA-256 token key、五类反向索引/generation、只读路由解析、签发 fence CAS、Session revoke gate、全局代次、Lua 原子保存与续期及 Redis 异常映射；`RedisConversationContextStoreIntegrationTest` 在提供真实 Redis 端口时验证完整 `OpencodeServerProcess` JSON 往返、Workspace/进程/全局失效、并发归档 gate CAS 回滚及 `beginIssue → invalidate/revoke → late save` 拒绝。
- `RedisTokenStoreSessionMarkerTest` 覆盖 marker digest 不包含原始 Token、同 TTL 写入、有效性检查和删除同步失效。
- `RedisRunCapacityPolicyTest` 固化 USER 输入专用 key、4 MiB 关键快照预留、单槽规范化上限以及 APPEND/PROJECT Lua 对 assistant role、text part 和显式 reset 的脚本契约。`RedisRunRuntimeStoreIntegrationTest` 在提供真实 Redis 端口时验证并发 append 的原子 seq/`${seq}-0` Stream、manifest 与 active 索引、容量截断后仍保留 USER/最终 assistant/text part/run-status 的物化 snapshot/reset、transient delta 聚合、durable/transient runtimeVersion 顺序、status/attention、动态 key TTL、scopeVersion、dedup、pending 字节记账/容量拒绝/原子 drain，并校验真实 Redis `noeviction` / `appendfsync everysec`；`RedisRunRuntimeIndexReservationTest` 验证用户 slot 闸门拒绝发生在 marker/Session/服务器/历史索引写入前，并固化单 Run 初始化脚本的 13 参数契约；`RedisUserRuntimeDisposeLeaseTest` 验证过期 active 清理、token 申请、续租和 compare-delete 释放；`RedisRunOwnerLeaseIntegrationTest` 额外覆盖条件接管、终态拒绝和所有 fenced 写入口的旧 token 隔离。测试未提供真实 Redis 端口时会跳过，不能用 H2 或 mock 替代 Lua/Streams 原子行为验证。
- `RedisRunTerminalRetryStoreIntegrationTest` 验证 record/due 固定同 slot 与 Lua 原子写删契约，并使用真实 Redis 验证安全投影白名单、due 时间、generation 单调覆盖、旧重排拒绝、compare-delete 和不超过 24 小时的 TTL。
- `MyBatisScheduledTaskRepositoryIntegrationTest` 保留 scheduler 历史任务/运行记录 XML 兼容测试；`MyBatisScheduledTaskRunRetentionRepositoryIntegrationTest` 覆盖七天边界、活动状态保留和 `ended_at` 索引；`MyBatisNightExecutionTaskRepositoryIntegrationTest` 覆盖到期/窗口扫描、固定目标与 attempt fencing、续租和状态版本并发 CAS。
- `MyBatisNightExecutionTaskRepositoryIntegrationTest` 以相同标识符规则覆盖 Flyway 建表、驼峰 Map 别名、幂等查询、任务读写、会话锁、容量上限/释放和过期占位清理。
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

新增表结构、Repository、数据库映射和 migration 时改这里。V18 之后新增 migration 文件名必须使用 `VyyyyMMddHHmmss__description.sql`，开发分支先使用创建时的本地时间作为候选版本，不再使用顺序数字版本。多人或多分支合并、打企业包前必须由发布集成人对照目标库最高 `flyway_schema_history` 和本次全部新 migration 统一排序：尚未进入共享库的候选版本可调整，但最终版本必须严格递增且全部高于已部署基线；已在任何共享或稳定库执行过的文件禁止删除、改名或改写。必须验证真实数据库从已部署基线按默认顺序升级，禁止用 `outOfOrder`、`repair` 或手改历史表绕过。不要把任务状态机或 HTTP API 编排逻辑放进本模块。
Flyway migration 只能承载表结构变更、历史数据兼容迁移和生产必需的基础字典/系统参数；禁止新增写入测试、演示、个人开发或环境专属数据的 seed migration。测试数据应放在 `test-agent-test-support`、测试 fixture、mock 数据或显式本地开发脚本中。
新增或修改关系型 SQL 必须新增/调整 `mybatis/*.xml` 与 `com.enterprise.testagent.persistence.mybatis` 内部 mapper，不能继续扩展 `Jdbc*Repository` 或使用 MyBatis 注解 SQL；存量 JDBC 仓储后续按触点分批迁移。
JSON payload/capabilities 当前以文本列保存，未来切换 PostgreSQL JSONB 必须同步兼容策略和测试。
`ai_model_configs` 只保留旧平台模型目录兼容，不是 LobeHub 模型配置来源；LobeHub 使用
`internal_model_provider_models` 关联现有供应商/Token，密钥不进入模型目录或每日聚合。
`agent_session_bindings` 是新链路的 agent 远端 session 绑定主数据源；Session 的 `opencode_session_id` 与 `opencode_execution_node_id` 仅作为 `opencode` 兼容字段，新增 agent 不得继续扩展 `sessions` 列，新增查询或导出时不得默认暴露给前端 DTO；`pinned` 是平台 Session API 字段，默认旧数据未置顶。
RunEvent 追加可能来自 opencode stream、取消和 Diff 动作等多个线程；修改 `MyBatisRunEventRepository` 时必须保留并发 append 下 seq 单调且不重复、scope 列写入、`raw_event_id` 缺失为 `NULL` 的测试。修改 SessionMessage/Run 映射时必须覆盖 token/cost、parts_json 和 active-run 查询。
修改 `RedisRunRuntimeStore` 时必须使用真实 Redis 验证 Lua 原子性、durable seq 与 runtimeVersion 双 Stream、Hash/ZSET 物化、分页 tail、动态 key 滑动 TTL、7 天 attention/pending、active 索引清理、scope/dedup/pending、20,000 条/32 MiB 显式截断和 `run.snapshot.reset` 语义；禁止以 `MAXLEN`、LRU/LFU 或静默 eviction 代替显式截断。

## LobeHub 与模型网关持久化

- `RedisLobehubSsoStore` 把 ticket、nonce 和 grant 摘要限制在 `test-agent:lobehub-sso:*`，使用 Lua 原子消费、
  nonce 占用、单用户 grant 轮换和撤销；Redis value 不保存原始 ticket/grant。
- `InternalModelProviderModelMapper.xml` 覆盖保存公开模型并级联清理旧探测，按能力保存最近探测结果。
- `ModelGatewayUsageDailyMapper.xml` 使用 PostgreSQL/H2 兼容 upsert 原子累加每日聚合，不先读后写。
- `InternalModelObservabilityMapper.xml` 维护内部模型调用可观测：明细 insert 与小时聚合 upsert 同一事务，探活状态 upsert 由 SQL 依据本次结果原子递增/归零连续失败计数；`V20260807130134__create_internal_model_observability.sql` 创建明细/小时聚合/探活状态三张表，后续 migration 分别增加首 token 与 `[DONE]` 流完成明细及各自小时 sum/max/count。`duration_ms` 保留端到端口径，`stream_complete_ms` 单独表达上游完整流耗时；所有表只存结构化字段，不保存请求/响应正文或 Token。
- `V20260730090000__add_lobehub_model_gateway.sql` 只创建上述平台表和四个生产必需公共参数；不触碰独立
  LobeHub ParadeDB，也不写测试/演示数据。
- 已知历史若存在 `V20260801093854`、但缺少 `V20260730090000`，app 兼容装配会隐藏无法再顺序执行的
  旧候选文件。早期补偿 `V20260802173416` 已执行时只加载其原始资源；尚未补偿且 release
  `V20260803133000` 已执行时只加载更高的 `V20260803141754`；否则继续使用早期补偿。三份 migration 都由
  SHA-256 测试锁定；不启用 `outOfOrder`、不执行 `repair`、不修改 `flyway_schema_history`。
- H2 集成测试覆盖完整 Flyway 和 mapper；PostgreSQL Testcontainers 测试覆盖模型网关并发累加，以及内部模型可观测从首 token 已部署基线升级到流完成 migration、PostgreSQL `ON CONFLICT` 聚合。无 Docker时显式 skip，正式发布仍需在真实目标基线上运行。
- Redis Testcontainers 使用真实 Redis 5.0.14 让 8 个线程并发消费同一 ticket，锁定 Lua 只成功一次、nonce
  防重放、grant 轮换/撤销、TTL 上限和 `test-agent:lobehub-sso:*` 前缀隔离。
