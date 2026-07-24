# 包说明：com.enterprise.testagent.persistence

## 职责

持久化包，负责数据库映射、Repository 实现、MyBatis XML mapper、Flyway migration 协作、Redis 必需适配和查询优化。

## 不负责

- 不承载 Controller。
- 不直接调用 generated SDK 或 opencode client。
- 不实现领域状态机。

## 主要程序清单

- `package-info.java`：说明 persistence 包是持久化适配边界。
- `mybatis.MyBatisPersistenceConfig`：扫描 persistence 内部 MyBatis mapper。
- `mybatis.CommonParameterMapper` / `mybatis/CommonParameterMapper.xml`：通用参数 MyBatis 试点 SQL。
- `mybatis.MyBatisCommonParameterRepository`：通用参数领域端口的生产 Bean。
- `mybatis.InternalModelProviderMapper` / `mybatis/InternalModelProviderMapper.xml`：内部模型供应商、可复用 Token 定义、Provider 关联和一次联表运行快照的全部关系型 SQL。
- `mybatis.MyBatisInternalModelProviderRepository` / `mybatis.MyBatisInternalModelTokenRepository`：内部模型供应商与 Token 定义领域端口的生产 Bean；普通返回模型不包含 Token 明文。
- `mybatis.UserDeletionMapper` / `mybatis/UserDeletionMapper.xml` / `mybatis.MyBatisUserDeletionRepository`：用户安全删除领域端口的生产实现，锁定目标用户、识别会话/工作区/进程/调度等受保护引用，并按外键顺序清理可随账号删除的附属表。
- `mybatis.RunMapper` / `mybatis/RunMapper.xml`：Run MyBatis SQL，包含保存、读取、最近非终态 Run 查询、只选择 `LEGACY_FULL` 的 stale active 查询和 `status` 条件更新。
- `mybatis.MyBatisRunRepository`：Run 领域端口的生产 Bean，通过 `saveIfStatus` 原子条件写入避免终态竞态覆盖。
- `mybatis.RunEventMapper` / `mybatis/RunEventMapper.xml`：RunEvent append-only MyBatis SQL，写入结构化 scope 列和可空 raw event id，并支持按 `root_session_id` 读取历史状态事件。
- `mybatis.MyBatisRunEventRepository`：RunEvent 领域端口的生产 Bean，保留 `(run_id, seq)` 冲突重试、`runId + lastSeq` 增量读取和 root session 历史状态读取。
- `mybatis.RunSessionScopeMapper` / `mybatis/RunSessionScopeMapper.xml`：Run session scope MyBatis SQL，包含按 Run 和按 root session 查询；`MERGE ... USING (VALUES ...)` 写入时间参数时显式 cast 为 `timestamp`，兼容 PostgreSQL 参数类型推断。
- `mybatis.MyBatisRunSessionScopeRepository`：RunSessionScope 领域端口的生产 Bean。
- `mybatis.RunSummaryMapper` / `mybatis.MyBatisRunSummaryPersistenceRepository`：新模式启动执行单条无原文锚点 INSERT；legacy Scheduled Run 额外用 XML SQL 按 attempt/租约认领 anchor-only 恢复并写 durable handoff 受理时间。终态事务执行 Run statusVersion CAS、最多两条摘要批量 MERGE、Session 时间更新三条 SQL；较高事件序号只允许一次晚到刷新，跨终态状态仅允许 `FAILED + TRANSPORT_ERROR` 被可信 root 事实纠正；详情 locator 从既有 `dispatch_message_id` 映射目标用户轮次，低频 Run 恢复、Diff 定位和 accepted/rejected 计数都只走 XML SQL，不写 `run_events`。
- `mybatis.ScheduledTaskRunRetentionMapper` / `mybatis/ScheduledTaskRunRetentionMapper.xml`：按 `ended_at` 和终态 status 清理超过 7 天的 scheduler 运行记录，显式排除活动状态。
- `mybatis.MyBatisScheduledTaskRunRetentionRepository`：实现 scheduler 运行记录保留策略 domain 端口，供 scheduler 框架维护任务调用。
- `mybatis.ScheduledTaskMapper` / `mybatis/ScheduledTaskMapper.xml` / `mybatis.MyBatisScheduledTaskRepository`：旧 scheduler 任务与运行记录的 MyBatis 历史兼容实现；生产不再扫描或认领 `USER_PLAN`。
- `mybatis.NightExecutionTaskMapper` / `mybatis/NightExecutionTaskMapper.xml` / `mybatis.MyBatisNightExecutionTaskRepository`：双模式定时任务的到期/窗口扫描、模式往返、固定目标 state-version 认领、attempt 租约续期与完成 fencing、会话锁和仅标准夜间使用的时段容量实现。
- `mybatis.NightExecutionTaskMapper` / `mybatis/NightExecutionTaskMapper.xml` / `mybatis.MyBatisNightExecutionTaskRepository`：定时任务、幂等创建、会话写锁、15 分钟夜间容量占位和 30 天清理的生产实现；`ADMIN_CUSTOM` 不创建容量记录。
- `mybatis.ReferenceRepositoryMapper` / `mybatis/ReferenceRepositoryMapper.xml`：引用资产总体状态与服务器副本的全部关系型 SQL，包含操作类型、旧分支/generation CAS、保留实际指针的目标 upsert、离线 `DEFERRED`、租约认领/续期和带 fencing 条件的同步/核验写回。
- `mybatis.MyBatisReferenceRepositoryRepository`：引用资产仓储领域端口的生产 Bean，负责行模型映射、分页上限和多目标事务边界。
- `RedisRunRuntimeStore` / `RunRuntimeStoreConfig`：Run 运行数据面领域端口的 Redis 唯一生产实现和装配；单 Run key 使用 `{runId}` hash tag，durable `events` Stream 使用 `${seq}-0`，durable/transient `runtime-events` Stream 使用 `${runtimeVersion}-0`，snapshot 使用 Hash + order ZSET 物化当前实体状态，外部 snapshot 同时 CAS seq/runtimeVersion，动态 key registry 统一滑动 TTL；跨 slot active/history 索引在单 Run Lua 前按“active TTL + pending TTL”安全窗保守登记并由读路径清脏，避免任一事件 Lua 提交后 Java 退出造成恢复失联；用户级 dispose 以 `{userId}` slot 原子清理过期 active 索引、确认用户空闲并申请/续租 token 闸门，新 Run 则在同一用户 slot 原子检查闸门、登记 `active:user` 并以随机 owner 创建 `runtime-user` marker，闸门拒绝时在 marker 与其他外部索引写入前返回；owner 条件接管原子校验活跃 manifest 快照并提升 token，事件、远端 Session 绑定和 scope/dedup/pending Lua 在副作用前校验 owner + token，pending 同时原子计入/扣减统一详情字节预算；生产 32 MiB 中为关键快照固定预留 4 MiB，durable/runtime 事件或 snapshot 投影项超过 20,000 或总详情超限时显式截断旧 Stream、递增 reset generation，并保留专用 USER 输入、JSON role 为 assistant 的最新 message、对应最新可见 text part 和 run-status，tool/reasoning/非 assistant 实体只作为可淘汰投影。
- `RedisRunTerminalRetryStore` / `RunTerminalRetryStoreConfig`：终态关系型事务故障后的独立 Redis 安全重试实现和装配；record/due 固定使用 `{terminal-retry}` hash tag，保存 Lua 按终态 outbox generation、事件序号和重试代次单调覆盖，删除 Lua 对完整白名单 JSON 执行 compare-and-delete，防止旧 worker 覆盖或删除晚到纠正版；悬空 due 自愈只在 record 仍不存在时移除索引；due ZSET 只含 runId/时间，记录 TTL 不超过 24 小时。
- `RedisTokenStore` / `TokenStoreConfig`：平台 Token Redis 实现，同时提供 SHA-256 session marker；保存、单 Token 删除、按用户批量撤销和过期均与 marker 同步，供 XXL 会话逐请求失效校验。批量撤销使用 `SCAN test-agent:token:*` 兼容历史已签发 Token，仅解析认证主体匹配 userId，不输出包含 Token 的 key 或 value。
- `JdbcWorkspaceRepository`：实现 Workspace 持久化端口。
- `JdbcSessionRepository`：实现 Session 持久化端口，并保存平台 session 到远端 opencode session/node 的内部映射。
- `JdbcSessionRepository.findPage`：全局 ACTIVE session 查询按置顶、更新时间和自增 ID 排序；空搜索不绑定可空 query pattern，兼容 PostgreSQL 参数类型推断。
- `JdbcAgentSessionBindingRepository`：实现通用 agent session 绑定端口，支持按 `(sessionId, agentId)` 查询、按 `(agentId, remoteSessionId)` 查询和 upsert。
- `JdbcSessionMessageRepository`：实现 SessionMessage 保存、按 messageId/远端 messageId 查询、分页和计数，并映射 parts/token/cost 快照字段。
- `JdbcRunRepository`：Run 存量 JDBC 实现已不再作为生产 Spring Bean，仅保留旧集成测试和迁移窗口。
- `JdbcRunEventRepository`：RunEvent 存量 JDBC 实现已不再作为生产 Spring Bean，仅保留迁移窗口。
- `JdbcExecutionNodeRepository`：实现执行节点保存和可路由节点查询。
- `JdbcRoutingDecisionRepository`：实现路由决策保存和查询。
- `JdbcOpencodeProcessManagementRepository`：实现 opencode 用户进程管理拓扑、用户进程、用户绑定持久化，以及运行管理页拓扑列表、连接列表、进程分页筛选和绑定关联查询；读取历史用户进程时会兼容 `updated_at < created_at` 的脏数据并按 `created_at` 归一化，避免旧记录阻断状态查询和重新初始化。
- `mybatis.OpencodeProcessReservationLockMapper` / `mybatis/OpencodeProcessReservationLockMapper.xml` / `mybatis.MyBatisOpencodeProcessReservationLockPort`：按用户、Linux 服务器顺序执行 `SELECT ... FOR UPDATE`，串行化短事务内的 binding 和服务器端口预留。
- `mybatis.OpencodeProcessAtomicMutationMapper` / `mybatis/OpencodeProcessAtomicMutationMapper.xml` / `mybatis.MyBatisOpencodeProcessAtomicMutationPort`：以旧坐标和 `status/PID/traceId` 生命周期代次同时 CAS process/binding；端口迁移保留原 processId/createdAt，任一表未命中时整笔事务回滚。本实现复用 V14 表结构，不新增 migration。
- `JdbcCommonParameterRepository`：通用参数存量 JDBC 实现，不再作为生产 Spring Bean，仅保留旧集成测试直接构造。
- `JdbcWorkspaceCreateOperationRepository`：实现设置页创建应用工作空间进度记录，供配置管理 HTTP 轮询接口读取。
- `db/migration/V1__create_core_tables.sql`：创建核心业务表和索引。
- `db/migration/V2__create_session_messages.sql`：创建会话消息表和分页索引。
- `db/migration/V3__add_session_opencode_mapping.sql`：为 sessions 增加可空内部 opencode 映射列、成对 check、节点外键和索引。
- `db/migration/V4__add_session_management_fields.sql`：为 sessions 增加 pinned 字段和 ACTIVE 会话排序索引。
- `db/migration/V6__create_agent_session_bindings.sql`：创建通用 agent session binding 表，并从旧 opencode 映射列回填 `opencode` 绑定。
- `db/migration/V10__seed_fcoss_application.sql`：本地 F-COSS 应用种子数据。
- `db/migration/V13__seed_fcoss_more_workspaces.sql`：历史本地 F-COSS 扩展种子数据。
- `db/migration/V16__add_message_and_run_usage_fields.sql`：扩展 session_messages/runs 的 run、remote message、parts、token、cost 和 active-run 索引。
- `db/migration/V14__create_opencode_process_management_tables.sql`：创建 Linux 服务器、后端 Java 进程、opencode 容器、容器管理进程、管理进程连接、用户专属 opencode server 进程和用户绑定表。
- `db/migration/V15__add_opencode_process_id_check_constraints.sql`：为 opencode 进程管理表加 `process_id` 前缀、稳定服务器身份、状态、port、baseUrl 形状等 CHECK 约束。
- `db/migration/V20260625184300__create_scheduler_framework_tables.sql`：创建 scheduler 表并为 sessions/runs/session_messages 增加来源预留字段。
- `db/migration/V20260626150000__add_common_parameters_and_workspace_create_operations.sql`：创建通用参数表、代码库英文名字段和工作空间创建进度表。
- `db/migration/V17__seed_local_opencode_machine_for_default_user.sql`：历史本地开发种子脚本，曾预置一台 `127.0.0.1` 的 opencode 机器并绑定默认开发用户；该版本只做 Flyway 历史保留，禁止删除、重命名或直接改写。
- `db/migration/V20260627000000__cleanup_loopback_linux_server_seed.sql`：清理 V17 留下的 `127.0.0.1` loopback opencode 拓扑、用户进程、绑定和关联 manager-backend 连接。
- `db/migration/V20260627010000__add_encrypted_aes_key_to_user_ssh_keys.sql`：为 `user_ssh_keys` 增加 `encrypted_aes_key` 列，禁止复用已落库的 V10。
- `db/migration/V20260627020000__seed_opencode_manager_max_processes_param.sql`：初始化 `OPENCODE_MANAGER_MAX_PROCESSES` 通用参数，供 manager 运行时最大进程数配置使用。
- `db/migration/V20260703141000__create_run_session_scopes.sql`：创建 Run session scope 表并为 `run_events` 预留 scope/raw event id 列。
- `db/migration/V20260715000000__add_scheduler_run_retention_index.sql`：为 `scheduled_task_runs.ended_at` 增加运行记录保留清理索引。
- `db/migration/V20260718210000__extend_scheduler_user_plan.sql`：允许 USER_PLAN 专用任务无 Cron，并为运行记录增加执行亲和字段和到期索引。
- `db/migration/V20260722130000__migrate_night_execution_to_xxl.sql`：增加夜间任务 attempt/精确 owner/租约/state-version 与 legacy Scheduled Run attempt/租约/受理时间，重建扫描索引，跳过待执行旧 USER_PLAN，并保留历史审计数据；版本晚于已交付迁移，兼容存量库按序升级。
- `db/migration/V20260724143000__add_night_execution_schedule_mode.sql`：增加非空调度模式列和双枚举检查约束，存量任务默认回填 `NIGHT_WINDOW`。
- `db/migration/V20260718211000__create_night_execution_tasks.sql`：创建夜间任务、会话锁和时段容量占位表及约束/索引/中文注释。
- `db/migration/V20260719210000__seed_night_execution_capacity_parameter.sql`：初始化 `platform=all`、可编辑、默认值 20 的夜间时段容量通用参数。
- `db/migration/V20260718100000__seed_references_params.sql`：初始化引用资产根目录和 SDD 根层目录名称清单。
- `db/migration/V20260718110000__create_reference_repository_replica_tables.sql`：创建引用资产总体状态/服务器副本表及认领、generation 查询索引。
- `db/migration/V20260718143000__add_reference_repository_operations_and_verification.sql`：增加引用资产操作类型、实际指针可空语义与核验时间。
- 后续可新增 SQL 查询、migration 相关适配、Redis 限流、缓存或运行心跳实现；Run 运行数据面不得新增 PostgreSQL 或 JVM 内存降级实现。
- 新增 migration 禁止写入测试、演示、个人开发或环境专属数据；这类数据应进入 `test-agent-test-support`、测试 fixture、mock 数据或显式本地开发脚本。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- MyBatis Spring Boot starter。
- Spring Data JDBC（仅存量 `Jdbc*Repository` 迁移窗口）。
- Flyway。
- PostgreSQL driver。
- Druid Spring Boot starter。
- Redis 客户端或 Spring Data Redis（系统必需依赖）。

## 禁止依赖

- `test-agent-api` 和业务模块通过 domain 端口调用。
- generated SDK。
- opencode client facade。
- 前端 DTO。

## 上游调用方

- workspace-management、opencode-runtime、agent-runtime 等业务模块。
- event 模块通过接口追加和查询事件。

## 下游依赖

- 数据库。
- Redis，系统必需依赖。
- domain 模型。

## 测试位置

- persistence 模块集成测试。
- Repository、migration、唯一约束、事务、分页和排序测试；当前使用 H2 PostgreSQL 模式执行 Flyway migration。
- AgentSessionBinding 测试必须覆盖 upsert 查询、唯一约束和旧 `sessions.opencode_*` 字段回填。
- RunEvent 测试必须覆盖同一 run 的并发 append、scope 列写入和 raw event id 缺失写 `NULL`，防止 stream 事件和取消事件同时落库时重复分配 seq 或误去重。
- SessionMessage/Run 测试必须覆盖 token/cost 字段、parts_json、远端 messageId 幂等查询、active-run 查询和 Run `saveIfStatus` 条件状态写入。
- ExecutionNode 测试必须覆盖可路由节点过滤和排序，防止不可用或满载节点被派发。
- OpencodeProcessManagement 测试必须覆盖拓扑读写、V17 loopback 种子清理、历史用户进程时间戳归一化、健康容器查询、用户绑定唯一约束、服务器端口唯一约束、容器管理进程一对一约束，以及真实 PostgreSQL 下用户/服务器行锁、并发单胜者和 process/binding 双表 CAS 回滚。
- ScheduledTask 测试必须覆盖任务定义、用户计划、运行记录、分页筛选和来源字段读写。
- NightExecution 测试必须覆盖幂等键、状态 CAS、单会话锁、容量上限/释放、过期占位清理和 owner 隔离。
- CommonParameter 和 WorkspaceCreateOperation 测试必须覆盖平台优先级、默认路径 seed、进度步骤更新、成功/失败状态和按用户隔离查询。
- UserDeletion 测试必须覆盖目标锁定、受保护业务引用阻断、账号附属表清理、批量原子边界和只撤销目标用户 Token。
- ReferenceRepository 测试必须覆盖 Flyway 建表、MyBatis XML generation/CAS、同服务器租约互斥/续期、过期 worker fencing、离线 `DEFERRED`/恢复和稳定游标分页。
- MyBatis 试点测试必须覆盖 XML mapper 查询和更新；源码约束测试必须阻止新增 JDBC SQL、MyBatis 注解 SQL，并固化 PostgreSQL 专有 SQL 兼容约束。
- Druid 连接池配置测试；当前验证 `spring.datasource.druid.*` 可绑定为 Druid DataSource，且 Web 控制台默认关闭。
- Flyway migration 命名测试必须覆盖版本唯一性和已落库历史文件仍可解析；V18 之后新增 migration 只能使用 `VyyyyMMddHHmmss__description.sql`。
- 内部模型代理鉴权列去机构标识时，历史 SQL migration 保持已落库 checksum，`db.migration.V20260716143000__rename_internal_model_auth_token_column` 负责兼容重命名既有数据库列。
- `RedisRunRuntimeStoreIntegrationTest` 必须连接真实 Redis，覆盖 Lua 并发 seq/runtimeVersion、双 Stream、Hash/ZSET 物化、分页 tail、动态 key TTL、attention/active 索引、scope/dedup/pending 和容量截断；H2 或 mock 不能替代 Redis Streams/Lua 行为验证。
- `RedisRunTerminalRetryStoreIntegrationTest` 覆盖同 slot Lua 原子写删契约，并在连接真实 Redis 时覆盖安全白名单、严格 due、generation 单调覆盖、旧重排拒绝、compare-delete 和 24 小时 TTL。

## 修改时必须同步更新

- `backend/test-agent-persistence/README.md`。
- `docs/standards/backend.md`。
- `docs/deployment/database.md`，如果存在。
- API 或事件文档中暴露的数据字段。

## MyBatis 规范

- 新增或修改关系型数据库 SQL 必须写在 `src/main/resources/mybatis/**/*.xml`。
- Mapper 接口只声明方法和 `@Param`，禁止使用 `@Select`、`@Insert`、`@Update`、`@Delete` 等注解 SQL。
- MyBatis mapper、行模型和 Repository 实现均为 persistence 内部细节，业务模块只能依赖 domain 端口。
- 存量 `Jdbc*Repository` 仅保留迁移窗口；触及其 SQL 时必须迁移到 MyBatis XML。
