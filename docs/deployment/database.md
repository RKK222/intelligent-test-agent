# 数据库 Migration 说明

本文档记录当前数据库结构和兼容策略。任何新增或修改 migration 都必须同步更新本文件。

## 数据访问规范

- 平台 PostgreSQL 连接池继续使用 Druid；XXL Admin 子上下文按上游运行方式使用隔离 Hikari MySQL DataSource。两套 schema 都由各自 Flyway 管理，禁止共用连接或 migration location。
- 新增或修改平台 PostgreSQL SQL 必须通过 `test-agent-persistence` 的 MyBatis XML mapper 实现；XXL MySQL 的平台扩展 SQL 只能写在 `test-agent-xxl-job-integration` 的 MyBatis XML。mapper 接口只声明方法，禁止写注解 SQL。
- 存量 `Jdbc*Repository` 仅保留迁移窗口，后续触及其 SQL 时迁移到 MyBatis XML。当前通用参数 `CommonParameterRepository`、Agent 配置 `AgentConfigRepository`、`RunEventRepository` 与 scheduler `ScheduledTaskRepository` 已迁移到 MyBatis XML；夜间任务从首版即只使用 MyBatis XML。
- Flyway migration 只能承载表结构变更、历史数据兼容迁移和生产必需的基础字典/系统参数；禁止通过 Flyway 写入测试、演示、个人开发或环境专属数据（例如样例应用/工作区、默认开发账号、默认本地进程绑定）。此类数据必须放在测试 fixture、`test-agent-test-support`、mock 数据、显式本地开发脚本或人工初始化流程中。历史已存在的开发种子迁移仅为兼容已落库环境保留，后续不得新增同类迁移。

## Python workflow 独立 PostgreSQL

长程任务使用同一 PostgreSQL 集群中的独立 `test_agent_workflow` 数据库和最小权限账号，与平台主库及 XXL MySQL 完全隔离。业务表由 `workflow-service/migrations/` 的 Alembic 管理，LangGraph checkpoint 表由独立 `testagent-workflow checkpoint-setup` 命令初始化；不扫描 Java Flyway location、不写平台 `flyway_schema_history`，也不受 Java MyBatis XML 规则约束。

业务库保存 `conversations/messages/tasks/task_repositories/runs/durable_events/analyzer_results/report_versions/workspace_leases/audit_logs/transactional_outbox`。活动run部分唯一约束保证同一会话只有一个 `QUEUED/RUNNING/WAITING_INPUT`；Worker通过 `FOR UPDATE SKIP LOCKED`、租约和心跳认领任务。源码、容器和工具原始日志不写数据库，48小时工作区过期由数据库租约驱动Runner清理，失败状态持久化为 `CLEANUP_FAILED` 并重试。

建库、迁移账号、备份、发布顺序和回滚见 `docs/deployment/workflow-offline.md`。平台 Java migration不得创建、修改或兼容这些Python业务表。

## XXL-JOB 独立 MySQL migration

XXL MySQL 与平台 PostgreSQL 完全分离。Admin 子上下文只扫描 `backend/test-agent-xxl-job-integration/src/main/resources/xxl-job/db/migration`，平台主 Flyway 的 `classpath:db/migration` 不会扫描该独立顶层目录。

### 当前企业现场连接配置

当前企业现场使用外部共享 XXL-JOB MySQL：

| 配置项 | 值 |
|---|---|
| 地址 | `122.210.106.43` |
| 端口 | `3306` |
| 数据库 | `xxl_job` |
| 账号 | `root` |
| Java 配置键 | `TEST_AGENT_XXL_JOB_MYSQL_URL`、`TEST_AGENT_XXL_JOB_MYSQL_USERNAME`、`TEST_AGENT_XXL_JOB_MYSQL_PASSWORD` |

生产密码属于敏感部署配置：本次纳管密码只写入 `.4/.114` 企业节点包中的 `backend.env`，不提交到仓库模板、文档、日志或命令行。密码含 `=`、`@`、`*` 等特殊字符，写入 dotenv 文件时必须保持原值；部署前只校验键数量和占位符，不回显密码。两台 Java 必须使用同一个密码和同一个 XXL access token，并先确认 `122.210.106.43:3306` 网络可达，再启动 Java。

| 版本 | 内容 |
|---|---|
| `V1__xxl_job_3_4_2_base_schema.sql` | XXL-JOB 3.4.2 基础表与 schedule lock；不包含示例任务和默认管理员。 |
| `V2__platform_sso_and_task_keys.sql` | `xxl_job_user` 增加唯一 `platform_user_id`、SHA-256 session digest/expiry，用户名扩为 128；`xxl_job_info` 增加唯一 `platform_task_key`。 |
| `V3__register_platform_executor_and_tasks.sql` | 新增自动注册执行器组 `test-agent-backend` 与六个首批周期任务。 |
| `V4__register_night_execution_dispatch_task.sql` | 注册每 15 分钟执行的 `opencode-runtime.night-execution-dispatch`，使用 ROUND、DISCARD_LATER、DO_NOTHING、GLOBAL_MUTEX 和零 XXL 重试。 |
| `V5__schedule_night_execution_dispatch_every_minute.sql` | 把既有分发任务 Cron 更新为每分钟并触发下一次时间重算，其它策略不变。 |
| `V6__register_app_source_cleanup_task.sql` | 注册每分钟应用源码到期清理广播任务。 |
| `V7__register_personal_workspace_relocation_task.sql` | 注册每分钟个人工作区跨服务器搬迁广播任务。 |
| `V8__schedule_personal_workspace_relocation_every_thirty_minutes.sql` | 保留 V7 原始字节，把既有搬迁任务 Cron 更新为每 30 分钟并触发下一次时间重算。 |
| `V9__register_inactive_user_process_cleanup_task.sql` | 注册每天北京时间 02:00 执行的十五天未使用用户 OpenCode 进程关闭广播任务。 |

V3-V9 是生产必需基础调度配置，不是演示数据。后续新增任务或调整既有生产默认配置，都必须新建不可变的更高版本 SQL；新增任务按新的 `platform_task_key` 插入，配置调整只修改明确目标字段。不得改写已执行 migration，也不得在应用启动阶段用非版本化 upsert 覆盖页面参数。

所有平台任务固定 `ROUND + DISCARD_LATER + DO_NOTHING + retry=0`，参数只含 `taskKey/concurrencyPolicy/payload`。V1-V9 可被多个 Admin 节点并发启动，Flyway schema history 负责互斥；重复启动不得重复 executor 组或任务。

PostgreSQL 的旧任务定义和运行记录不搬运到 MySQL；旧行保留审计，不再产生新的 PostgreSQL scheduler 运行。短暂停机升级 migration 将旧夜间 `PENDING/RUNNING/STOPPING USER_PLAN` 全部标记为 `SKIPPED`，避免旧 runner 删除后留下永久活动记录。XXL 运行日志独立留在 MySQL，默认保留 30 天。

## V20260718123000 Agent 配置发布 rollout 范围

`V20260718123000__generalize_agent_config_rollout_scope.sql` 为既有 `public_agent_config_rollouts` 增加 `config_scope` 与 `scope_key`。历史记录通过默认值保持 `PUBLIC`；新应用 Agent 配置发布写入 `APPLICATION + 应用版本 ID`。服务器租约、目标进程租约和用户消息闸门继续共用既有排空状态机。`config_scope` 受 CHECK 约束限制为 `PUBLIC/APPLICATION`，旧 Java 未写该字段时仍兼容为 `PUBLIC`。

## V20260721213000 Agent 配置发布作用域隔离与个人 worktree 补偿

`V20260721213000__isolate_agent_config_rollout_scopes.sql` 删除原集群全局活动 rollout 唯一索引，改为：

- `PUBLIC` 范围同一时刻最多一个 `PREPARING/DRAINING`；
- `APPLICATION` 范围按 `scope_key=应用版本 ID` 各自最多一个活动发布，不同应用版本和公共发布可以并行；
- 新增 `public_agent_config_rollout_worktrees`，按 `(rollout_id, personal_workspace_id)` 保存 `PENDING/PROCESSING/AWAITING_USER/SYNCED/ABANDONED`、稳定原因码、目标 commit、服务器和租约。

应用服务器共享副本达到目标 commit 后，即使某个个人 worktree 有本地修改或合并冲突，也会把该项写成 `AWAITING_USER` 并确认服务器同步，主 rollout 不再无限占锁。后台仍按服务器租约重试；用户完成提交、回退或冲突合并后，任务转 `SYNCED`，同一用户在该 rollout 的全部 worktree 都收敛时才登记该用户旧进程 dispose。删除个人 worktree 通过外键级联清理任务；永久退役服务器把未完成任务标记为 `ABANDONED`。迁移不修改现有 rollout 状态，部署后现存 `DRAINING + PERSONAL_WORKTREE_UPDATE_PENDING` 会在下一次服务器 claim 时自动生成补偿行并完成主 rollout。

## V20260728100000 个人拉取应用 Agent 单用户排空范围

`V20260728100000__add_personal_application_rollout_scope.sql` 仅扩展 `public_agent_config_rollouts.config_scope` 的 CHECK 约束，允许 `PERSONAL_APPLICATION`。该范围在个人 `git-pull` 已完成 Git merge 后使用，`scope_key` 保存个人工作区 ID，`initiated_by_user_id` 保存唯一受影响用户；只为当前服务器创建 server 行，并复用既有 target 租约、进程身份、空闲检测和 dispose 状态机。它不进入 PUBLIC 单锁或 APPLICATION 按版本唯一索引，因此不会占用共享发布锁；门禁 SQL 只按 `initiated_by_user_id = 当前用户` 命中。迁移不新增表、不回填历史行，也不写测试、演示或个人数据。

## V20260728160000 公共 Agent 全局刷新确认与个人 worktree 补偿

`V20260728160000__extend_public_agent_config_refresh.sql` 为公共全局刷新增加两项持久化边界：

- `public_agent_config_rollouts.discard_shared_runtime_changes` 记录超级管理员是否已明确确认恢复所有服务器共享运行副本的本地内容。默认 `false` 兼容旧 Java；worker 只能在该值为 `true` 且已取得服务器租约后 reset/clean，共享副本不会在全局锁建立前被修改。
- 新增 `public_agent_config_rollout_public_worktrees`，按 `(rollout_id, worktree_id)` 保存公共个人 worktree 的用户、服务器、固定目标 commit、稳定原因码、重试次数与 fencing 租约。`worktree_id` 外键指向 `agent_config_worktrees`，不复用应用个人工作区外键表。

公共主 rollout 在共享副本同步和本机进程快照完成后即可继续排空；某个个人 worktree 因覆盖风险、未完成 merge 或真实冲突而进入 `AWAITING_USER` 时，不占用主 rollout 锁。用户处理完成后独立 worker 原生 merge 同一目标 commit 并转为 `SYNCED`；删除 worktree 或服务器退役时转为级联删除或 `ABANDONED`。迁移只包含生产运行状态结构、索引和注释，不写测试、演示或个人数据。

## V20260803133000 公共 Agent 纠错替换与强停标记

`V20260803133000__support_public_agent_config_rollout_supersede.sql` 为无法通过普通会话空闲检查收敛的公共发布增加可审计纠错链：

- `public_agent_config_rollouts.supersedes_rollout_id/superseded_by_rollout_id` 建立一对一双向替换关系，自引用外键、非自身 CHECK 和部分唯一索引共同防止重复替换或错误回链；历史行保持 `NULL`。
- `public_agent_config_rollouts.supersede_reason` 保存超级管理员必填原因；旧任务使用新增终态 `SUPERSEDED`，新任务保持既有 `DRAINING -> COMPLETED` 状态机。
- `public_agent_config_rollout_targets.force_stop` 非空且默认 `false`，旧 Java 和历史目标继续走原有空闲检查。只有新纠错 rollout 中与旧批次 `ABANDONED/ROLLOUT_SUPERSEDED` 目标的用户、服务器、容器、端口、PID、启动时间全部一致的目标才由插入 SQL 派生为 `true`。

纠错仓储必须在同一个 PostgreSQL 事务内 CAS 更新指定 `DRAINING` 旧任务、清除其 server/target/worktree fencing 租约、创建唯一的新活动任务并写入双向关系；任一步失败都整体回滚，因此消息门禁没有可见空窗。迁移不改写现有 `DRAINING` 数据，也不会自动停止现场进程；升级后仍需超级管理员从公共配置管理页对精确旧 rollout 执行一次“强制终止并替换发布”。该 migration 一旦在共享或企业数据库执行即不可改写；发布集成时仍须按根规范核对 `flyway_schema_history` 版本和 checksum，并从各已知企业基线升级验证。

当前 release 将该 migration 的原始 SHA-256 锁定为 `8b3cbad538f856d5daa06d15f118554ecefb2380a249287cdfe291eb71199022`；正式 persistence JAR 和外层企业包都会复核该字节。

## V20260804123000 个人工作区跨服务器搬迁状态

`V20260804123000__create_personal_workspace_relocations.sql` 创建 `personal_workspace_relocations`，每个 `personal_workspace_id` 最多保留一条当前搬迁状态。表记录 `relocation_id`、用户/版本/运行态 Workspace、源/目标稳定服务器、个人分支、发现时源逻辑路径、状态、次数、租约、归档 SHA-256/大小、安全错误和目标应用/最终完成时间；外键关联个人工作区、版本、用户和运行态 Workspace，并限制源目标不同、非负次数/大小与状态枚举。

状态按 `DISCOVERED → EXPORTING → TRANSFERRING → APPLYING → CLEANUP_PENDING → SUCCEEDED` 推进，目标切换前的瞬时失败进入 `RETRY_WAIT`；目标已切换后的清理失败保持 `CLEANUP_PENDING` 并按 `next_retry_at` 重新认领。`PersonalWorkspaceRelocationMapper.xml` 只扫描 ACTIVE 个人工作区、ACTIVE Workspace 和 ACTIVE `agent_id='opencode'` binding 的真实错配，并排除 `PENDING/RUNNING/CANCELLING` Run。目标完成事务先 `FOR UPDATE` 重读版本、用户、运行态 Workspace、分支、源路径、服务器、binding 和活动 Run，再依次更新 `workspaces.root_path/linux_server_id`、`personal_workspaces.repo_root_path/workspace_root_path/base_commit` 和搬迁状态；任一步不命中整体回滚，源目录不得清理。`CLEANUP_PENDING` 不会被用户连续换服产生的新候选覆盖；即使源 worktree 已删除后数据库确认瞬时失败，下一轮也会幂等完成收尾，旧源清理终态后才允许下一段搬迁。

该 migration 只增加生产状态表、约束、索引和注释，不回填、不改写现有 Workspace，也不包含测试或环境数据。首次引入该能力的版本必须先停止或至少完成全部 Java 的同版本切换边界，先让平台 PostgreSQL Flyway 应用本 migration，再让 XXL MySQL 连续应用 `V7`、`V8`；V7 登记任务，V8 在 scheduler 启动前把最终 Cron 调整为每 30 分钟。禁止让已注册 V7/V8 的新任务调用尚未包含新表/handler 的旧 Java。正式集成仍必须核对目标 `flyway_schema_history` 的版本/checksum，并从每套已知企业基线升级验证，禁止 `outOfOrder`、`repair` 或手工修改历史表。

当前 release 将该 migration 的原始 SHA-256 锁定为 `f41a9aaab637f4b196f63cb7d37ef58cf0b15c9521abd1050c9929c6ce27b212`；`FlywayMigrationNamingTest` 和最终 persistence JAR 字节校验必须保持一致。XXL `V7` 源码 SHA-256 为 `be1705cac272b9c4e89c43136f0125132c2afc4bbc3525322678cd02fb2c5305`，`V8` 为 `f4919a2f6ce224ecf50b347f2d438ad746753d9bbb8856a3403adf963f031bf2`，`V9` 为 `1d2e78716f3ffc33993de2c2b160fb48f6c8b6e9b71a7b592e4beaf6943a45e3`；任一版本进入共享 MySQL 后都禁止改写。

## V1 核心表

`backend/test-agent-persistence/src/main/resources/db/migration/V1__create_core_tables.sql` 创建以下表：

| 表 | 说明 |
|---|---|
| `workspaces` | 平台工作区，包含业务 ID、名称、根路径、服务器归属、状态、traceId、创建和更新时间。 |
| `sessions` | 智能体会话，关联 workspace，包含标题、状态、traceId、创建和更新时间。 |
| `runs` | 运行记录，关联 session/workspace，包含 Run 状态、traceId、创建和更新时间；V10 后可记录单次 Run token/cost 快照。 |
| `run_events` | `LEGACY_FULL` RunEvent append-only 事件流，按 `(run_id, seq)` 唯一并支持增量回放；`REDIS_SUMMARY` 不写该表。 |
| `execution_nodes` | opencode 执行节点，包含 baseUrl、健康状态、运行容量、权重、心跳和能力标签。 |
| `routing_decisions` | Run 到 ExecutionNode 的路由决策审计记录。 |

## V20260626090000 工作空间服务器归属字段

`backend/test-agent-persistence/src/main/resources/db/migration/V20260626090000__add_workspace_linux_server_id.sql` 为运行态 `workspaces` 增加可空字段：

| 表 | 字段 | 说明 |
|---|---|---|
| `workspaces` | `linux_server_id` | 工作空间所在 Linux 服务器 ID，当前与 opencode 进程管理中的 `linux_servers.linux_server_id` 一致。 |

索引：

- `idx_workspaces_linux_server_id` 支撑按服务器归属排查和后续迁移。

兼容策略：

- 新建运行态 Workspace 默认写入当前 Java 进程所属服务器 ID。
- 历史 `linux_server_id is null` 的工作区按 legacy local 处理；文件 WebSocket ticket 校验 root path 与当前 opencode 进程同服务器成功后回填服务器 ID。
- 如果 workspace、用户 opencode 进程或目标后端 Java 进程不在同一服务器，文件 WebSocket 路由和 ticket 创建返回 `CONFLICT`，要求用户重新选择工作空间。

## V2 会话消息表

`backend/test-agent-persistence/src/main/resources/db/migration/V2__create_session_messages.sql` 创建 `session_messages`：

| 字段 | 说明 |
|---|---|
| `id` | 数据库自增 surrogate PK，不对 API 暴露。 |
| `message_id` | 平台消息业务 ID，使用 `msg_` 前缀并有唯一约束。 |
| `session_id` | 关联 `sessions.session_id` 的业务 ID。 |
| `role` | 消息角色，当前为 `USER`、`ASSISTANT`、`SYSTEM`。 |
| `content` | UTF-8 文本内容。 |
| `trace_id` | 创建消息的 traceId。 |
| `created_at` | 创建时间。 |

索引：

- `uk_session_messages_message_id` 保证消息业务 ID 唯一。
- `idx_session_messages_session_created(session_id, created_at, id)` 支持按会话分页读取消息。

## V3 Session opencode 映射

`backend/test-agent-persistence/src/main/resources/db/migration/V3__add_session_opencode_mapping.sql` 为 `sessions` 增加后端内部映射字段：

| 字段 | 说明 |
|---|---|
| `opencode_session_id` | 远端 opencode session id，可空；首次 Run 成功创建远端 session 后写入。 |
| `opencode_execution_node_id` | 远端 session 所在 execution node，可空；引用 `execution_nodes.execution_node_id`。 |

约束和索引：

- `fk_sessions_opencode_execution_node` 保证映射节点存在。
- `chk_sessions_opencode_mapping` 保证两个映射字段同时为空或同时非空。
- `uk_sessions_opencode_session_id` 保证远端 opencode session 与平台 session 一对一。
- `idx_sessions_opencode_execution_node` 支持按执行节点排查会话映射。

## V4 Session 管理字段

`backend/test-agent-persistence/src/main/resources/db/migration/V4__add_session_management_fields.sql` 为 `sessions` 增加 History 管理字段：

| 字段 | 说明 |
|---|---|
| `pinned` | 会话是否置顶，非空，默认 `false`，旧数据自动保持未置顶。 |

索引：

- `idx_sessions_active_pinned_updated(status, pinned, updated_at, id)` 保留给旧内部列表兼容路径。
- `idx_sessions_workspace_active_pinned_updated(workspace_id, status, pinned, updated_at, id)` 支持 workspace 维度会话列表排序。

## 兼容策略

- 所有表使用自增 surrogate PK；业务层只使用带前缀业务 ID。
- 新增字段优先允许空值或提供默认值，避免破坏旧数据。
- `agent_session_bindings` 是 agent 运行态绑定主数据源，按 `(session_id, agent_id)` 记录平台 session 到远端 session/node 的映射。
- `sessions.opencode_session_id` 和 `sessions.opencode_execution_node_id` 是后端内部兼容字段，不进入 API DTO；旧 session 两列为空时由首次 `opencode` Run 懒创建远端 session，非 opencode agent 不扩展这些列。
- `sessions.pinned` 进入 Session API DTO；软删除复用 `status=ARCHIVED`，不新增删除时间字段，旧数据默认 `ACTIVE` 且 `pinned=false`。
- `run_events.payload_json` 和 `execution_nodes.capabilities_json` 当前为 JSON 文本，便于 H2 和 PostgreSQL 共用测试；前者只服务 `LEGACY_FULL`/旧数据，`REDIS_SUMMARY` 的运行态 JSON 位于 Redis。未来迁移到 JSONB 时必须先保持旧列读取兼容。
- `LEGACY_FULL` 的 `run_events.seq` 由持久化层按同一 run 分配，取消、Diff 动作和 opencode stream 并发追加时必须依赖 `(run_id, seq)` 唯一约束冲突后重试，保持事件流单调递增且不重复；`REDIS_SUMMARY` seq 由 Redis Lua 原子分配，Stream ID 为 `${seq}-0`。
- `run_events.raw_event_id` 可空；opencode raw event id 缺失时必须保持 `NULL`，不能写入 `"unknown"` 这类伪值，否则会导致唯一索引误去重。
- `session_messages.content` 保留为旧文本 fallback；V10 后新增的 `parts_json`、token/cost 字段允许为空，旧数据和旧前端继续只读 `content`。
- `ai_model_configs` 只保存模型目录元数据，不保存 token、API key 或 provider secret；企业内调用密钥继续由部署环境变量或配置中心注入。
- 删除或重命名状态、事件类型、数据库字段必须拆分为读取兼容、数据迁移、清理三个阶段。

## V20260710143000 Run 摘要控制面

`backend/test-agent-persistence/src/main/resources/db/migration/V20260710143000__add_run_summary_persistence.sql` 将 PostgreSQL 调整为新模式控制面锚点，不承载运行中原始数据：

| 表 | 字段组 | 说明 |
|---|---|---|
| `runs` | `storage_mode/status_version/client_request_id` | 创建时固定存储模式、终态 CAS 版本和 Session 内客户端幂等键。`(session_id, client_request_id)` 唯一。 |
| `runs` | `producer_linux_server_id/execution_node_id_snapshot/opencode_process_id_snapshot` | 生产服务器、执行节点和用户进程路由快照。 |
| `runs` | `root_remote_session_id/dispatch_message_id/assistant_summary_message_id` | 远端根会话、稳定派发 ID 和前端反馈使用的稳定助手摘要 ID。 |
| `runs` | `scheduled_dispatch_attempt_id/scheduled_dispatch_lease_until/scheduled_dispatch_accepted_at` | legacy Scheduled Run 的启动恢复 fencing、5 分钟租约和完成事件订阅/异步 prompt handoff 的 durable 受理标记；普通手工 Run 与 Redis 摘要 Run 保持空。 |
| `runs` | `terminal_* / remote_stop_confirmed / last_event_seq / details_expires_at` | 终态来源、安全错误说明、远端停止确认、Redis 最后序号和详情期限。 |
| `runs` | `diff_*_count/last_remote_message_id/last_remote_part_id` | Analytics Diff 计数和显式低频 Diff 动作定位。 |
| `session_messages` | `content_kind/summary_key/summary_version/summary_status` | 区分历史 `RAW_LEGACY` 与新模式 `SUMMARY`，摘要键唯一并记录规则版本、完整/截断/fallback 状态。 |

兼容与写入约束：

- 历史 Run 和消息分别默认 `LEGACY_FULL`、`RAW_LEGACY`；迁移不清理、不改写既有原文。
- 新模式启动只 INSERT 一条不含 prompt/回答/parts/事件的 Run 锚点；已有远端 Session 的正常启动不执行关系型 SELECT。
- 终态事务最多三条 SQL：按 `status_version` CAS 更新 Run、批量 MERGE USER/ASSISTANT 最多两条摘要、更新 Session 时间。较高 `last_event_seq` 只允许一次晚到刷新；跨终态状态仅允许已落库的 `FAILED + TRANSPORT_ERROR` 被 `REMOTE_ROOT/RECOVERY_REMOTE_ROOT` 事实纠正，禁止其它来源任意翻转。摘要 `parts_json` 必须为 `NULL`。
- 终态事务异常时 PostgreSQL 不承担重试队列；Redis 只保存已清洗 `RunTerminalProjection`，状态为 `TERMINAL_PENDING_DB`。record 与 due ZSET 固定使用 `{terminal-retry}` 同一 hash slot；保存 Lua 按 `terminalProjectionVersion → lastEventSeq → failedAttempts/nextAttemptAt` 单调覆盖，删除 Lua 仅在完整白名单 JSON 仍匹配当前 worker 所处理记录时执行，防止旧重试覆盖或删除晚到纠正版。按 5 秒、15 秒、30 秒、1 分钟、2 分钟、5 分钟后封顶 5 分钟重试；成功/版本冲突后 compare-delete。未来的 `details_expires_at` 是更早上限；Redis 运行态已经丢失或详情已到期时，安全控制面投影仍可独立保留最多 24 小时，且不包含 prompt、回答、parts 或原始事件。
- USER 摘要最多 512、ASSISTANT 最多 2000 个 Unicode 字符；完整 prompt、回答、reasoning、工具输入输出、附件正文和原始事件不得写入 PostgreSQL。
- 接受/拒绝 Diff 是显式低频动作，各允许一条 MyBatis XML UPDATE 更新 `runs.diff_*_count`；不写 `run_events`。终态 CAS 使用单调最大值，避免并发动作计数被旧投影覆盖。
- Analytics 按 `storage_mode` 双读：legacy 读取旧消息/事件，新模式读取摘要消息和 Run Diff 计数；即使灰度验证残留 shadow 事件也不得双计数。

## V20260703141000 Run Session Scope

`backend/test-agent-persistence/src/main/resources/db/migration/V20260703141000__create_run_session_scopes.sql` 为 Run session tree 和断线恢复增加结构化 scope：

| 表/字段 | 说明 |
|---|---|
| `run_session_scopes` | Run 到 root opencode session 的 scope 主表，只服务 `LEGACY_FULL`/旧数据的恢复；`REDIS_SUMMARY` 不写该表。 |
| `run_session_scopes.root_session_id` | 当前 Run root opencode session ID。 |
| `run_session_scopes.scope_version` | scope 版本，发现 child 时递增。 |
| `run_session_scopes.metadata_json` | scope 扩展元数据 JSON 文本，不使用 JSONB。 |
| `run_session_scope_sessions` | 当前 Run scope 内 root/child session 清单。 |
| `run_session_scope_sessions.session_id` | scope 内 opencode session ID。 |
| `run_session_scope_sessions.parent_session_id` | 父 opencode session ID，root 为空。 |
| `run_session_scope_sessions.discovery_source` | 发现来源，如 `ROOT`、`TASK_PART`、`SESSION_EVENT`、`BOOTSTRAP`。 |
| `run_session_scope_sessions.task_message_id/task_part_id/task_call_id` | child 与本 Run task part 的绑定信息。 |
| `run_events.root_session_id/session_id/parent_session_id/is_child_session/scope_version` | RunEvent scope 预留列，可空兼容历史事件。 |
| `run_events.raw_event_id` | opencode raw event ID，缺失保持 `NULL`。 |

约束和索引：

- `run_session_scopes.run_id` 唯一并外键引用 `runs.run_id`。
- `run_session_scope_sessions(run_id, session_id)` 唯一。
- `uq_run_events_scope_raw_event(run_id, session_id, raw_event_id)` 用于 raw event 去重；`raw_event_id is null` 的事件不参与误去重。
- `idx_run_events_scope_session_seq(run_id, root_session_id, session_id, seq)` 支持 Run scope 内按 session 恢复事件。
- `run_events.root_session_id` 也用于 Session 级历史树读取跨 Run durable 状态事件；新增查询必须继续放在 MyBatis XML。
- `idx_run_session_scope_sessions_root(root_session_id, discovered_at)` 支持 Session 级历史树按 root session 汇总跨 Run 已发现 child。

当前 `RunSessionScopeRepository` 与 `RunEventRepository` 均已通过 MyBatis XML 实现，并只作为 `LEGACY_FULL`/旧数据恢复链路使用。legacy Run 启动后记录 root scope；runtime 发现 child session 后写入 `run_session_scope_sessions`，并在 `run_events` append 时写入结构化 scope 列。`REDIS_SUMMARY` 的 scope、dedup、pending 和 durable 事件只进入 Redis，不查询或写入上述表。`RunSessionScopeMapper.xml` 使用 PostgreSQL `MERGE ... USING (VALUES ...)` 时会显式 cast 时间参数为 `timestamp`，避免未定型参数在 PostgreSQL 中被推断为 `text`。`RunEventRepository` 支持按 Run 回放和按 root session 回放，后者用于 legacy Session 历史树恢复 permission/question/todo 等 durable 状态。`JdbcRunEventRepository` 仅保留迁移窗口，不再作为生产 Spring Bean。`raw_event_id` 缺失必须写 `NULL`；root session 事件派生的 `run.succeeded/run.failed` 不复用原始 raw event id，避免与对应 session 事件误去重。

## V5 用户认证表

`backend/test-agent-persistence/src/main/resources/db/migration/V5__create_user_and_auth_tables.sql` 创建以下表：

| 表 | 说明 |
|---|---|
| `users` | 平台用户，包含统一认证号、用户名、BCrypt 密码哈希、所属机构/研发部/部门。 |
| `user_login_logs` | 用户登录日志，记录登录时间、IP、User-Agent 和结果。 |
| `dictionaries` | 通用字典表，存储应用角色等字典数据。 |
| `user_roles` | 用户角色对照关系表，关联用户和角色字典。 |

### users 用户表

| 字段 | 说明 |
|---|---|
| `id` | 数据库自增 surrogate PK，不对 API 暴露。 |
| `user_id` | 用户业务 ID，使用 `usr_` 前缀。 |
| `unified_auth_id` | 统一认证号，唯一不可空。 |
| `username` | 用户名，唯一。 |
| `password_hash` | BCrypt 密码哈希值。 |
| `organization` | 所属机构，可空。 |
| `rd_department` | 所属研发部，可空。 |
| `department` | 所属部门，可空。 |
| `status` | 用户状态，ACTIVE/INACTIVE，默认 ACTIVE。 |
| `created_at` | 创建时间。 |
| `updated_at` | 更新时间。 |

### user_login_logs 用户登录日志表

| 字段 | 说明 |
|---|---|
| `id` | 数据库自增 PK。 |
| `log_id` | 日志业务 ID，使用 `log_` 前缀。 |
| `user_id` | 用户业务 ID，外键引用 users.user_id。 |
| `login_at` | 登录时间。 |
| `ip_address` | 客户端 IP 地址。 |
| `user_agent` | 浏览器 User-Agent。 |
| `login_result` | 登录结果：SUCCESS/FAILURE。 |

### dictionaries 通用字典表

| 字段 | 说明 |
|---|---|
| `id` | 数据库自增 PK。 |
| `dict_id` | 字典业务 ID，使用 `dict_` 前缀。 |
| `dict_name` | 字典名称，如"应用角色"。 |
| `dict_key` | 字典键，如 `ROLE`。 |
| `dict_value` | 字典值，如 `SUPER_ADMIN`。 |
| `dict_label` | 显示标签，如"超级管理员"。 |
| `sort_order` | 排序序号。 |
| `created_at` | 创建时间。 |
| `updated_at` | 更新时间。 |

唯一约束：`(dict_key, dict_value)`。

初始化角色字典：
- `SUPER_ADMIN`（超级管理员）
- `SYSTEM_ADMIN`（系统管理员）
- `APP_ADMIN`（应用管理员）
- `USER`（普通用户）

`V20260702153000` 初始化版本库类型字典 `REPOSITORY_TYPE`：
- `TEST_WORK_REPOSITORY`（测试工作库）
- `APPLICATION_CODE_REPOSITORY`（应用代码库）
- `APPLICATION_ASSET_REPOSITORY`（应用资产库）

### user_roles 用户角色对照表

| 字段 | 说明 |
|---|---|
| `id` | 数据库自增 PK。 |
| `user_id` | 用户业务 ID，外键引用 users.user_id。 |
| `dict_id` | 字典业务 ID，外键引用 dictionaries.dict_id。 |
| `created_at` | 创建时间。 |

唯一约束：`(user_id, dict_id)`。

## V6 通用 Agent Session Binding 表

`backend/test-agent-persistence/src/main/resources/db/migration/V6__create_agent_session_bindings.sql` 创建 `agent_session_bindings`，用于替代继续扩展 `sessions.opencode_*` 字段的模式：

| 字段 | 说明 |
|---|---|
| `id` | 数据库自增 surrogate PK，不对 API 暴露。 |
| `session_id` | 平台 session 业务 ID，外键引用 `sessions.session_id`。 |
| `agent_id` | 规范化后的 agent 标志，当前可运行值为 `opencode`。 |
| `remote_session_id` | 对应 agent 的远端 session id。 |
| `execution_node_id` | 远端 session 所在 execution node，外键引用 `execution_nodes.execution_node_id`。 |
| `created_at` | 绑定创建时间。 |
| `updated_at` | 绑定更新时间，upsert 时刷新。 |
| `trace_id` | 创建或更新绑定的 traceId。 |

约束和索引：

- `uk_agent_session_bindings_session_agent(session_id, agent_id)` 保证同一平台 session 对同一 agent 只有一个远端绑定。
- `uk_agent_session_bindings_agent_remote(agent_id, remote_session_id)` 保证同一 agent 的远端 session 不会绑定到多个平台 session。
- `fk_agent_session_bindings_session` 和 `fk_agent_session_bindings_execution_node` 保证引用有效。
- `idx_agent_session_bindings_execution_node` 支持按执行节点排查远端 session 绑定。

迁移会从已有 `sessions.opencode_session_id/opencode_execution_node_id` 回填 `agent_id='opencode'` 的绑定记录。旧字段暂时保留，用于旧链路兼容和回滚窗口；新链路以 `agent_session_bindings` 为主数据源。

## V7 应用配置管理表

`backend/test-agent-persistence/src/main/resources/db/migration/V7__create_configuration_management_tables.sql` 创建独立配置管理表：

| 表 | 说明 |
|---|---|
| `applications` | 外部系统同步的应用定义，本期只读消费，不提供应用 CRUD。 |
| `application_members` | 应用与平台用户成员关系，删除使用 `deleted_at` 逻辑删除。 |
| `code_repositories` | 代码库配置，`git_url` 全局唯一且创建后不可编辑。 |
| `application_repository_links` | 应用与代码库多对多关联。 |
| `application_workspaces` | 应用级工作空间配置，与运行态 `workspaces` 表独立。 |
| `user_ssh_keys` | 用户个人 SSH 私钥配置，私钥密文、RSA 加密的临时 AES 密钥、nonce、指纹和名称。 |

关键约束：

- `application_members(app_id, user_id)` 唯一；`deleted_at` 为空表示有效关系。
- `code_repositories.git_url` 唯一；不提供删除仓库配置的业务接口。
- `code_repositories.english_name` 后续迁移新增，可空兼容历史数据；非空值唯一，新增/编辑代码库时由后端校验字母、数字和连字符，最大 128 字符，并统一小写保存。
- `code_repositories.repository_type` 后续迁移新增，取值来自通用字典 `REPOSITORY_TYPE`；旧 `standard` 字段保留，新增/读取时统一由版本库类型派生兼容值。
- `code_repositories.deployment_mode` 后续迁移新增，取值 `EXTERNAL` / `INTERNAL`；存量数据默认 `EXTERNAL`。
- `application_repository_links(app_id, repository_id)` 唯一。
- `application_workspaces(app_id, repository_id, branch, directory_path)` 唯一，一个目录对应一个应用工作空间配置。
- `user_ssh_keys.user_id` 唯一，保证每个用户最多保存一把 SSH key。

兼容策略：

- `applications` 数据由外部同步写入，平台只读查询；同步机制本期不实现。
- `application_workspaces` 不复用、不引用运行态 `workspaces`，后续使用场景再决定如何衔接。
- SSH 私钥只保存 AES-GCM 密文和 nonce，API 不返回明文或密文；加密密钥由部署环境配置。

## V8 默认开发用户超级管理员授权

`backend/test-agent-persistence/src/main/resources/db/migration/V8__grant_default_user_super_admin.sql` 为本地默认前端用户 `888888888` 幂等授予 `SUPER_ADMIN` 角色。

兼容策略：

- 迁移按 `users.username = '888888888'` 和 `dictionaries(ROLE, SUPER_ADMIN)` 查找数据，不硬编码数据库自增主键。
- 插入前检查 `user_roles(user_id, dict_id)` 是否已存在，重复执行语义下不会产生重复角色关系。
- 已登录旧 Token 不会自动带上新角色，需要重新登录后 `/api/auth/me.roles` 才会返回 `SUPER_ADMIN`。

## V9 应用版本工作区与个人工作区表

`backend/test-agent-persistence/src/main/resources/db/migration/V9__create_managed_workspace_tables.sql` 创建托管工作区运行配置表：

| 表 | 说明 |
|---|---|
| `application_workspace_versions` | 应用工作空间模板的版本实例，记录版本、实际分支、托管仓库目录逻辑值、opencode 工作目录逻辑值和关联运行态 `workspaces.workspace_id`。 |
| `personal_workspaces` | 用户基于应用版本工作区派生的 git worktree，记录展示名称、私有分支、托管目录逻辑值、base commit 和关联运行态 Workspace。 |
| `user_global_workspace_preferences` | 用户全局最近使用的托管运行态 Workspace。 |
| `user_application_workspace_preferences` | 用户在某应用下最近使用的托管运行态 Workspace。 |
| `workspace_sync_records` | 个人工作区与应用版本工作区同步审计，记录方向、文件列表、是否强推、结果和 traceId。 |

关键约束：

- `application_workspace_versions(application_workspace_id, version)` 唯一，保证同一模板同一日期版本只有一条记录。
- `application_workspace_versions.runtime_workspace_id` 唯一并引用 `workspaces.workspace_id`。
- `personal_workspaces(app_workspace_version_id, user_id, workspace_name)` 唯一，保证同一用户在同一应用版本下个人空间名称不重复。
- 最近使用偏好按全局 `user_id` 唯一、按应用 `(user_id, app_id)` 唯一。
- 同步审计中的源/目标 workspace 均引用运行态 `workspaces`。

兼容策略：

- 不迁移、不删除既有手动 `workspaces`、sessions、runs；新增托管工作区只是在创建版本或个人空间时新增运行态 `workspaces` 记录。
- 新建或显式修复的托管路径不再写数据库绝对路径：应用版本/副本使用 `appworkspace:<versionSegment>/<repositoryEnglishName>[/<templateDirectory>]`，个人 worktree 使用 `personalworktree:<versionSegment>/<userId>/<repositoryEnglishName>/<branch>[/<templateDirectory>]`。运行时分别按通用参数 `OPENCODE_APP_WORKSPACE_ROOT`、`OPENCODE_PERSONAL_WORKTREE_ROOT` 解析为当前服务器物理路径；旧 Unix/Windows 绝对路径只兼容读取，不做批量迁移。
- `application_workspaces.branch` 继续保留作为模板创建兼容字段；版本实际分支以 `application_workspace_versions.branch` 为准。
- 应用版本和个人工作区物理根目录由 `common_parameters` 中的 `OPENCODE_APP_WORKSPACE_ROOT`、`OPENCODE_PERSONAL_WORKTREE_ROOT` 决定，`common_parameters` 为唯一事实源，缺失时直接抛业务异常（不再回退 yaml 或代码默认值）。托管表只记录逻辑路径，不负责创建或清理目录；普通手工注册 `workspaces` 可继续保存用户选择的绝对目录。

## V20260627010000 user_ssh_keys 新增 encrypted_aes_key 列

`backend/test-agent-persistence/src/main/resources/db/migration/V20260627010000__add_encrypted_aes_key_to_user_ssh_keys.sql` 为 `user_ssh_keys` 表新增 `encrypted_aes_key text` 列，承载混合加密方案中 RSA-OAEP 加密后的临时 AES 密钥。该 migration 必须使用时间戳版本，不能复用已落库的 `V10__seed_fcoss_application.sql`，否则会触发 Flyway checksum mismatch。

兼容策略：

- 旧记录的 `encrypted_aes_key` 为 `NULL`，应用层在解密时检测到 NULL 抛「SSH key 使用的旧版加密格式，请重新添加」，提示用户通过新版前端重新添加。
- 新增 SSH key 时前端先用 `GET /api/internal/platform/configuration-management/ssh-key/public-key` 取服务端 RSA 公钥，再 AES-256-GCM 加密私钥、RSA-OAEP/SHA-256 加密临时 AES 密钥，连同 nonce、指纹一起提交；服务端 RSA 私钥（`classpath:rsa-private.key`）解密并校验指纹后落库。
- 静态 AES 密钥配置 `test-agent.security.ssh-key-encryption-key` / `TEST_AGENT_SSH_KEY_ENCRYPTION_KEY` 不再使用，迁移到 RSA 私钥文件。

## V20260627020000 通用参数种子 OPENCODE_MANAGER_MAX_PROCESSES

`backend/test-agent-persistence/src/main/resources/db/migration/V20260627020000__seed_opencode_manager_max_processes_param.sql` 初始化生产必需通用参数：

| 参数 | 平台 | 默认值 | 说明 |
|---|---|---|---|
| `OPENCODE_MANAGER_MAX_PROCESSES` | `all` | `8` | opencode-manager 容器运行时最大进程数，后端可通过控制面下发，manager 按自身端口池容量裁剪。 |

兼容策略：

- 使用独立时间戳版本 `20260627020000`，不得复用已存在的 `20260627010000` SSH key migration 版本。
- 该参数属于生产运行所需系统参数，不是测试或演示数据；如果运维需要调整默认值，应通过通用参数管理或显式 SQL 更新现有记录，不改写已发布 migration。

## V20260629203006 通用参数种子 SYS_DATA_ROOT_DIR

`backend/test-agent-persistence/src/main/resources/db/migration/V20260629203006__seed_sys_data_root_dir_param.sql` 初始化生产必需通用参数：

| 参数 | 平台 | 默认值 | 说明 |
|---|---|---|---|
| `SYS_DATA_ROOT_DIR` | `macos` | `$HOME/.testagent` | macOS 系统数据根目录，读取时由通用参数解析器展开 `$HOME`。 |
| `SYS_DATA_ROOT_DIR` | `linux` | `/data/.testagent` | Linux 系统数据根目录。 |
| `SYS_DATA_ROOT_DIR` | `windows` | `D:/data/.testagent` | Windows 系统数据根目录。 |

Java 后端启动时会把稳定服务器身份写入 `SYS_DATA_ROOT_DIR/.serverid`，把可访问主机地址写入 `SYS_DATA_ROOT_DIR/.serverhost`；非 Windows Go manager 在连接 Java 前按同一系统参数的平台默认路径读取这两个文件，用于上报 `linuxServerId` 并派生初始 WebSocket seed 地址。

兼容策略：

- 该 migration 只新增 `common_parameters` 行，不修改表结构、API DTO 或事件类型。
- `macos` 沿用现有通用参数平台枚举值；用户口头称 “mac” 时落库仍使用稳定值 `macos`。
- 该参数属于生产运行所需系统参数，不是测试或演示数据；既有环境如需调整实际目录，应通过通用参数管理页面/API 修改 value，不改写已发布 migration。

## V20260629230000 OPENCODE 路径参数收敛为 all 行

`backend/test-agent-persistence/src/main/resources/db/migration/V20260629230000__consolidate_opencode_path_params_to_all.sql` 将 6 个 OPENCODE 路径类通用参数由 `linux`/`windows`/`macos` 三平台分别种子，收敛为单条 `all` 行，值统一引用 `${SYS_DATA_ROOT_DIR}`：

| 参数 | 平台 | 默认值 |
|---|---|---|
| `OPENCODE_APP_WORKSPACE_ROOT` | `all` | `${SYS_DATA_ROOT_DIR}/agent-opencode/workspace/appworkspace/` |
| `OPENCODE_PERSONAL_WORKTREE_ROOT` | `all` | `${SYS_DATA_ROOT_DIR}/agent-opencode/workspace/personalworktree/` |
| `OPENCODE_PUBLIC_CONFIG_DIR` | `all` | `${SYS_DATA_ROOT_DIR}/agent-opencode/.config/opencode/` |
| `OPENCODE_PUBLIC_CONFIG_GIT_ROOT` | `all` | `${SYS_DATA_ROOT_DIR}/agent-opencode/.config/` |
| `OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT` | `all` | `${SYS_DATA_ROOT_DIR}/agent-opencode/.configdev/` |
| `OPENCODE_SESSION_DIR` | `all` | `${SYS_DATA_ROOT_DIR}/agent-opencode/.session/` |

迁移先 `delete` 上述 6 个参数的既有 `linux`/`windows`/`macos` 行，再用普通 `insert` 写入 `all` 行；该写法保持 PostgreSQL 与 H2 PostgreSQL 模式的 Flyway 测试兼容。`SYS_DATA_ROOT_DIR` 仍保持三平台行不变；`all` 行在运行态由 `CommonParameterReferenceResolver` 按当前/目标平台作为解析上下文展开 `${SYS_DATA_ROOT_DIR}`（见 `CommonParameterReferenceResolver` 的 `all` 引用平台参数支持）。

兼容策略：

- 该 migration 只改 `common_parameters` 数据，不改表结构、API DTO 或事件类型。
- `macOS` 实际路径由历史 `/tmp/test-agent/...` 变为 `$HOME/.testagent/agent-opencode/...`（来自 `SYS_DATA_ROOT_DIR` 的 macOS 值 `$HOME/.testagent`），本地开发既有 `/tmp` 数据需迁移到新位置。
- 该参数属于生产运行所需系统参数，不是测试或演示数据；既有环境如需调整实际目录，应通过通用参数管理页面/API 修改 value，不改写已发布 migration。

## V20260627214000 user_roles identity 序列兼容修复

`backend/test-agent-persistence/src/main/resources/db/migration/V20260627214000__reset_user_roles_identity_sequence.sql` 将 `user_roles.id` identity 起点重置到 `1000000`，兼容历史库或人工数据导入后序列值落后于已有主键，导致新增用户授予角色时报 `user_roles_pkey` 冲突的问题。

兼容策略：

- `user_roles.id` 是数据库 surrogate PK，不对 API 暴露；业务唯一性仍由 `(user_id, dict_id)` 约束保证。
- 迁移只调整 identity 后续发号起点，不写入测试、演示或环境专属数据，不修改已有角色关系。
- `test-agent-system-management` 的创建用户流程在业务服务层使用事务，确保用户和角色要么同时写入成功，要么同时回滚，避免角色写入失败时留下无角色用户。

## 数据库 IDENTITY 运维入口

`generated by default as identity` 列的序列可能因历史数据导入、手动插入带 ID 行等原因落后于已有主键，导致新增数据命中主键唯一约束（如 `users_pkey`），被全局异常处理器翻译成"数据冲突：当前操作因存在关联数据无法执行"。`user_roles` 表历史上由 `V20260627214000` 用 `restart with 1000000` 修复。

为避免每次都靠加 Flyway migration 临时修复，平台提供超管运维入口（`/api/internal/platform/system-management/identity`），支持查询 `users`/`user_roles`/`dictionaries`/`user_login_logs` 四张白名单表的 identity 当前值与 `max(id)`，并支持一键对齐到 `max(id)+1` 或手动 `RESTART WITH` 指定值（禁止往回滚）。表名走白名单枚举、目标值走 Long 校验，杜绝 SQL 注入。详见 `docs/api/http-api.md`。

## V20260626150000 通用参数与工作空间创建进度

`backend/test-agent-persistence/src/main/resources/db/migration/V20260626150000__add_common_parameters_and_workspace_create_operations.sql` 增加通用参数、代码库英文名和设置页创建工作空间进度表。

新增表与字段：

| 表/字段 | 说明 |
|---|---|
| `common_parameters` | 通用参数表，包含参数英文名、参数中文名、参数值、适用平台 `windows/linux/macos/all`、是否允许前端修改 `editable`、创建和更新时间。 |
| `code_repositories.english_name` | 代码库英文名称，可空兼容历史数据，非空唯一，初始最大 29 字符，后续扩展到 128 字符。 |
| `workspace_create_operations` | 设置页创建应用工作空间的进度表，按 `operation_id` 记录状态、当前步骤、错误信息、关联应用/用户/模板/版本和 traceId。 |

`common_parameters` 初始化 8 条 opencode 路径参数：

| 参数 | Linux 默认值 | Windows 默认值 |
|---|---|---|
| `OPENCODE_PUBLIC_CONFIG_DIR` | `/data/.testagent/agent-opencode/.config/opencode/` | `D:/data/.testagent/agent-opencode/.config/opencode/` |
| `OPENCODE_SESSION_DIR` | `/data/.testagent/agent-opencode/.session/` | `D:/data/.testagent/agent-opencode/.session/` |
| `OPENCODE_APP_WORKSPACE_ROOT` | `/data/.testagent/agent-opencode/workspace/appworkspace/` | `D:/data/.testagent/agent-opencode/workspace/appworkspace/` |
| `OPENCODE_PERSONAL_WORKTREE_ROOT` | `/data/.testagent/agent-opencode/workspace/personalworktree/` | `D:/data/.testagent/agent-opencode/workspace/personalworktree/` |

兼容策略：

- 历史代码库的 `english_name` 保持 `null`；列表和详情响应允许返回 `null`，但新增/编辑代码库时必须提供合法英文名。
- 缺少英文名的历史代码库不能创建新的应用版本工作区，后端返回 `VALIDATION_ERROR`，避免新路径规则下目录冲突。
- 通用参数读取按 `当前平台 -> all` 顺序选择，命中即用；未命中或值为空时抛 `INTERNAL_ERROR` 业务异常（`通用参数未配置：<参数英文名>`），强制运维在 `common_parameters` 表中补配。公共 Agent Git 地址参数例外：始终读取 `OPENCODE_PUBLIC_AGENT_GIT_URL`，外部部署直接作为完整 URL 使用，内部部署按 `host[:port]/path` 片段解释；参数缺失或为 `UNCONFIGURED` 时视为公共级功能未启用，不抛异常。
- `workspace_create_operations` 只服务 HTTP 轮询进度，不写入 `run_events`，也不参与 RunEvent SSE 续传。
- 设置页创建流程先保存模板、后执行 Git 与版本持久化；本次请求新插入的模板在后续失败且仍无版本时由业务服务补偿删除，既有模板和已经形成版本的模板保留。历史失败遗留可用 `tools/cleanup-orphan-application-workspaces.sql` 全库审计并在停机、备份、候选数量二次确认后受控清理。该脚本默认只读，排除近期运行任务、个人工作区和 Hub 资产/引用，保留失败 operation、物理 Git 目录及运行态 Workspace；它是显式运维修复，不作为 Flyway migration 自动删除业务数据，也禁止用 SQL 伪造版本、运行态 Workspace 或服务器副本。

## V20260702153000 版本库类型字典与字段

`backend/test-agent-persistence/src/main/resources/db/migration/V20260702153000__add_repository_type_to_code_repositories.sql` 为版本库类型优化增加生产必需字典和字段：

| 表/字段 | 说明 |
|---|---|
| `dictionaries(REPOSITORY_TYPE)` | 初始化 `TEST_WORK_REPOSITORY`、`APPLICATION_CODE_REPOSITORY`、`APPLICATION_ASSET_REPOSITORY` 三个版本库类型下拉选项。 |
| `code_repositories.repository_type` | 版本库类型，非空，默认 `APPLICATION_CODE_REPOSITORY`，业务含义由 `dictionaries.dict_key='REPOSITORY_TYPE'` 的值定义。 |

兼容策略：

- 历史 `standard=true` 的代码库回填为 `TEST_WORK_REPOSITORY`。
- 历史 `standard=false` 的代码库无法自动区分代码或资产，统一回填为 `APPLICATION_CODE_REPOSITORY`。
- 旧 `standard` 布尔列继续保留给工作空间分支规则等存量逻辑；新增版本库选择测试工作库时写 `standard=true`，选择应用代码库或应用资产库时写 `standard=false`。
- 字典种子属于生产必需基础字典，不包含测试、演示或个人开发数据。

## V20260702180000 版本库部署模式字段

`backend/test-agent-persistence/src/main/resources/db/migration/V20260702180000__add_code_repository_deployment_mode.sql` 为版本库增加内外部部署模式：

| 表/字段 | 说明 |
|---|---|
| `code_repositories.deployment_mode` | 版本库部署模式，非空，默认 `EXTERNAL`；取值由领域和服务层归一化为 `EXTERNAL` / `INTERNAL`。 |
| `code_repositories.english_name` | 从 29 字符扩展到 128 字符，以支持内部 SCM 路径派生的 `group-repository` 形式英文名。 |

兼容策略：

- 存量版本库统一按 `EXTERNAL` 处理，`git_url` 仍表示完整 Git 地址。
- `INTERNAL` 版本库只在 `git_url` 保存 `host[:port]/path`，例如 `scm-share.sdc.cs.enterprise:29418/hzefficiencytools/interfaceplatform`；运行 Git 操作时按当前操作人统一认证号动态拼接 `ssh://{unifiedAuthId}@{git_url}`。
- 列表查询和应用关联查询只返回数据库保存的 `git_url`，不拼接统一认证号。

## V20260626180000 删除废弃参数 OPENCODE_WORKSPACE_ROOT

`backend/test-agent-persistence/src/main/resources/db/migration/V20260626180000__drop_deprecated_opencode_workspace_root_parameter.sql` 删除 `common_parameters` 中无消费方的 `OPENCODE_WORKSPACE_ROOT`（linux/windows 各一行）。该参数仅为 `OPENCODE_APP_WORKSPACE_ROOT` / `OPENCODE_PERSONAL_WORKTREE_ROOT` 的父目录，子目录参数已独立维护全路径，父参数不再需要。

## 通用参数数据库直读与变量解析（无 schema 变更）

通用参数值支持变量引用 `${englishName}`，在应用层读取时展开；`${NAME}` 先按通用参数引用解析，未命中时再读取 Java 后端进程环境变量，`$NAME` 直接读取环境变量；路径值开头的 `$HOME` 和 `~/` 会展开为当前用户主目录，**不涉及表结构变更**（`parameter_value` 已为 `text`）。通用参数运行态默认通过 `RepositoryCommonParameterValues` 直接读取数据库，不写入 JVM 内存缓存或 Redis 参数快照。只有显式实现 `CommonParameterMemoryEntry` 的条目才由本机注册表缓存；首个条目是夜间容量，启动、匹配广播或手工刷新时仍从数据库读取，只在 JVM 保存已校验的正整数快照。

`PATCH` 修改 `OPENCODE_MANAGER_MAX_PROCESSES` 后，后端仍发布 `common-parameter.refresh-requested` 跨实例广播，但 payload 只携带参数标识，不携带参数值；各 Java 实例收到后直接从数据库读取最新值并向本服务器 manager 下发 max-only `configUpdate`。路径类参数属于部署/初始化参数，不通过前端热刷新。

## V20260701100000 通用参数 editable 列

`backend/test-agent-persistence/src/main/resources/db/migration/V20260701100000__add_common_parameter_editable_column.sql` 为 `common_parameters` 新增 `editable` 列（`boolean not null default false`），标识是否允许在前端修改参数值：`true` 可修改，`false` 只读（部署/初始化参数，修改将影响系统正常运行）。该迁移仅将 `OPENCODE_MANAGER_MAX_PROCESSES` 与 `OPENCODE_PUBLIC_AGENT_GIT_URL` 置为 `true`，其余参数为 `false`。

兼容策略：

- 纯加列带默认值 `false`，存量行自动为只读；不停机、不破坏既有读取。
- `updateValue` 不触及 `editable` 列（仍只 `set parameter_value` 与 `updated_at`）；可改性由 `CommonParameterManagementApplicationService` 的 `existing.editable()` 校验，只读参数更新返回 `VALIDATION_ERROR`「该通用参数为只读参数，修改后将影响系统正常运行」。
- `OPENCODE_MANAGER_MAX_PROCESSES` 与公共 Git 地址参数为生产必需的可改参数；本 migration 回填的 `editable=true` 属生产系统参数，非测试/演示数据。
- API 响应 `CommonParameterResponse` 增 `editable` 字段（additive，向后兼容）；公共 Git 地址参数更新经 `common-parameter.refresh-requested` 广播保证跨实例 DB 一致，但不触发 manager 热刷新，由 AgentConfig 下次操作按当前部署模式直读 DB 生效。

## V20260709130000 清理公共 Agent 内部 Git 参数

`backend/test-agent-persistence/src/main/resources/db/migration/V20260709130000__cleanup_public_agent_internal_git_parameter.sql` 清理由短暂方案写入的 `OPENCODE_PUBLIC_AGENT_GIT_URL_INTERNAL` 参数行。公共 Agent Git 地址只保留 `OPENCODE_PUBLIC_AGENT_GIT_URL` 一个通用参数，外部部署保存完整 SSH/HTTPS Git URL，内部部署保存 `host[:port]/path` 片段，Java 后端按当前用户统一认证号动态拼接 SSH URL。

兼容策略：

- 源码中保留 `V20260709110000__add_public_agent_internal_git_url_parameter.sql` 的原始内容，专门兼容已经执行过该版本的环境，避免 Flyway validate 报 “applied migration not resolved locally”；新环境会先执行该旧迁移再执行本清理迁移，最终仍只剩一个公共 Git 参数。
- 先删除旧内部参数关联的 `common_parameter_change_logs`，再删除 `common_parameters` 行，避免外键阻塞升级。
- 新环境没有旧内部参数时该 migration 为幂等 no-op；已有环境会在升级后从通用参数页面移除第二个地址。
- 回填 `OPENCODE_PUBLIC_AGENT_GIT_URL` 的中文名为 `公共agent配置Git库地址` 且保持 `editable=true`。前端修改弹窗复用 `repository-deployment-options` 提供外部/内部选择；内部模式仅保存 `host[:port]/path` 片段，Java 后端按保存值形态判断是否拼接当前用户统一认证号，不通过新增参数表达内部/外部。

macOS 本地环境迁移到项目内 `temp/` 时，先停止服务并运行 `tools/cleanup-old-path-data.sql` 的默认审计模式；确认引用后，再传入 `apply_cleanup=true` 和绝对 `test_agent_root` 迁移 `workspaces`、版本、replica、个人工作区、Agent worktree 和非运行 opencode 进程的路径字段。六个 macOS 通用参数和公共 Git 地址参数必须通过通用参数管理 API/页面修改，以保留修改历史并触发配置联动。该脚本不删除 Session、Run、审计记录或磁盘目录，也不是 Flyway migration。


## V20260718100000 通用参数种子引用资产参数

`backend/test-agent-persistence/src/main/resources/db/migration/V20260718100000__seed_references_params.sql` 初始化引用资产相关通用参数：

| 参数 | 平台 | 默认值 | editable | 说明 |
|---|---|---|---|---|
| `OPENCODE_REFERENCES_DIR` | `all` | `${SYS_DATA_ROOT_DIR}/agent-opencode/references` | `false` | 引用资产根目录，统一引用 `SYS_DATA_ROOT_DIR`，运行态由通用参数解析器按当前/目标平台展开。只读，不允许前端修改。 |
| `REFERENCES_SDD_FOLDER_NAMES` | `all` | `docs,spec` | `true` | 规格驱动（SDD）场景识别规格目录的名称清单，逗号分隔、小写。允许前端按团队约定调整。 |

兼容策略：

- 两参数均为 `platform=all` 单行；`OPENCODE_REFERENCES_DIR` 复用 `all` 行引用平台参数 `SYS_DATA_ROOT_DIR` 的解析能力（`SYS_DATA_ROOT_DIR` 仅有 linux/windows/macos 行，无 all 行），与 `OPENCODE_SESSION_DIR` 等路径参数一致。macOS 实际路径为 `$HOME/.testagent/agent-opencode/references`，Linux 为 `/data/.testagent/agent-opencode/references`，Windows 为 `D:/data/.testagent/agent-opencode/references`。
- Flyway 默认把 `${...}` 当作占位符替换，故 `OPENCODE_REFERENCES_DIR` 的值在 SQL 中用 `'$' || '{SYS_DATA_ROOT_DIR}/agent-opencode/references'` 拼接，使 SQL 文本不出现占位符序列，DB 实际存储美元符加大括号包裹的 `SYS_DATA_ROOT_DIR` 字面量；`REFERENCES_SDD_FOLDER_NAMES` 值无占位符，直接以字面量写入。
- `OPENCODE_REFERENCES_DIR` 为部署/初始化参数（`editable=false`），更新返回 `VALIDATION_ERROR`「该通用参数为只读参数，修改后将影响系统正常运行」；`REFERENCES_SDD_FOLDER_NAMES` 为可改参数（`editable=true`），修改后经 `common-parameter.refresh-requested` 跨实例广播保证 DB 一致。二者均不触发 opencode manager 热刷新，由消费方下次读取时直读 DB 生效。
- 该参数属于生产运行所需系统参数，不是测试或演示数据；既有环境如需调整实际目录或目录名清单，应通过通用参数管理页面/API 修改 value，不改写已发布 migration。

## V20260718110000 引用资产库状态与服务器副本表

`backend/test-agent-persistence/src/main/resources/db/migration/V20260718110000__create_reference_repository_replica_tables.sql` 新增引用资产库总体状态和每台 Linux 服务器副本任务，只创建运行必需结构，不写测试、演示或个人开发数据。

`reference_repository_states` 每个代码库一行：

| 字段 | 说明 |
|---|---|
| `repository_id` | 主键，同时外键引用 `code_repositories.repository_id`。 |
| `branch` / `target_commit_hash` | 当前 generation 期望落盘的目标分支和固定远端目标提交；分支可由受控切换操作变更。未初始化兼容状态允许为空。 |
| `generation` | 非负同步代次，默认 0；首次初始化写 1，后续同步只递增。 |
| `status` | `UNINITIALIZED`、`INITIALIZING`、`VERIFYING`、`SYNCHRONIZING`、`READY`、`FAILED`。 |
| `credential_user_id` | 当前 generation 执行 Git 操作的用户，可空，外键引用 `users.user_id`；凭据内容不进入本表。 |
| `trace_id` / `last_error` | 最近一次操作 traceId 和可空安全错误说明。 |
| `initialized_at` / `created_at` / `updated_at` | 首次初始化、记录创建和最近更新时间。 |

`reference_repository_replicas` 每个代码库、每台稳定服务器一行：

| 字段 | 说明 |
|---|---|
| `repository_id` / `linux_server_id` | 复合主键；`repository_id` 外键引用总体状态并 `ON DELETE CASCADE`，`linux_server_id` 保存稳定服务器身份。 |
| `generation` / `branch` | 本副本当前目标代次，以及最近一次从本机完整观察到的实际分支；实际分支未观察时允许为空。 |
| `status` | `PENDING`、`PROCESSING`、`READY`、`RETRY_WAIT`、`BLOCKED`、`DEFERRED`。 |
| `current_commit_hash` / `synced_at` | 最近一次从本机完整观察到的实际提交和最近成功同步时间。 |
| `retry_count` / `next_retry_at` | 非负重试次数和指数退避后的下次可认领时间。 |
| `lease_token` / `lease_until` | worker 数据库租约 fencing token 和到期时间；不保存用户凭据。 |
| `last_error` / `created_at` / `updated_at` | 可空安全错误说明和时间戳。 |

generation、租约和 CAS 规则：

- 首次初始化使用主键 insert-if-absent；并发不同分支只有一个胜者，失败方读取胜者状态，不能混合分支与提交。
- 新同步只允许在总体仍为预期 generation、分支未变且旧状态为 `READY` / `FAILED` 时 CAS 推进；活动状态的重复同步幂等返回当前 generation。
- 副本目标只接受相同或更高 generation。更高 generation 会清空旧租约和重试，但保留上一代实际 branch、commit、同步及核验时间作为明确的历史快照；同 generation 的 `DEFERRED` 服务器恢复上线时重置为 `PENDING`。
- worker 只能认领 `PENDING` / `DEFERRED`、已到期的 `RETRY_WAIT` 或租约已过期的 `PROCESSING`。续租及 `READY` / `RETRY_WAIT` / `BLOCKED` 写回必须同时匹配 `repository_id + linux_server_id + generation + lease_token`，并要求租约在写回时仍未过期；旧 generation 或旧 token 无权修改共享副本状态。
- 补偿扫描把当前 generation 中离线服务器的 `PENDING`、`RETRY_WAIT`、`PROCESSING` 转为 `DEFERRED` 并清除租约；离线目标保留，恢复后重新参与同步，但不阻塞当前在线服务器汇总为 `READY`。

索引：

- `idx_reference_repository_replicas_claim(linux_server_id, status, next_retry_at, lease_until, updated_at)` 支持本机按状态、退避和租约查找可认领任务。
- `idx_reference_repository_replicas_generation(repository_id, generation, status)` 支持当前代次的总体状态汇总。
- `reference_repository_states` 使用 `repository_id` 主键稳定游标分页，不另建重复索引。

所有新增关系型 SQL 均由 `ReferenceRepositoryMapper.xml` 维护，`ReferenceRepositoryMapper` 只声明参数化方法，`MyBatisReferenceRepositoryRepository` 负责领域对象映射和事务边界；未新增 JDBC SQL 或 MyBatis 注解 SQL。迁移是纯新增表，旧客户端和旧代码库记录不受影响；只有成功写入 `branch` 的引用资产库会触发配置管理的 `englishName` 与代码库类型冻结兼容规则。

## V20260718143000 引用资产操作类型与指针核验

`backend/test-agent-persistence/src/main/resources/db/migration/V20260718143000__add_reference_repository_operations_and_verification.sql` 在既有两表上增量增加：

- `reference_repository_states.operation_type`：非空，取值为 `INITIALIZE`、`SYNCHRONIZE`、`SWITCH_BRANCH`、`VERIFY_POINTERS`。存量 `UNINITIALIZED/INITIALIZING` 行回填 `INITIALIZE`，`VERIFYING` 行回填 `VERIFY_POINTERS`，其它行回填 `SYNCHRONIZE`。
- `reference_repository_replicas.verified_at`：最近一次完整读取本机实际 branch 与 HEAD 的时间；未核验或读取未完成时为空或保留旧值。
- `reference_repository_replicas.branch` 改为可空并明确表示实际观察值，而不是目标分支；目标分支只读取主状态 `branch`。

分支切换和核验都先以旧 `generation + branch + READY/FAILED` CAS 推进新 generation。副本建档保留上一代实际指针，活动请求重试及 60 秒补偿扫描会按“在线服务器 + 全部历史副本服务器”补齐当前 generation，避免广播丢失、Java 退出或 CAS 后短暂中断留下无目标活动状态。worker 的同步、核验及终态写回继续要求 generation、lease token 和未过期租约；核验不需要 Git 凭据，也不执行任何远端或工作树写操作。

迁移只包含兼容性字段、约束、回填和注释，不写测试、演示或个人数据；旧客户端忽略新增响应字段即可继续运行。


## V20260626170000 公共 Agent 配置管理

`backend/test-agent-persistence/src/main/resources/db/migration/V20260626170000__add_agent_config_management.sql` 增加公共 Agent 配置参数、worktree 记录和 Git 长操作进度表。

新增通用参数：

| 参数 | Linux 默认值 | Windows 默认值 / all 默认值 |
|---|---|---|
| `OPENCODE_PUBLIC_AGENT_GIT_URL` | `UNCONFIGURED`（platform=`all`） | `UNCONFIGURED` |
| `OPENCODE_PUBLIC_CONFIG_GIT_ROOT` | `/data/.testagent/agent-opencode/.config/` | `D:/data/.testagent/agent-opencode/.config/` |
| `OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT` | `/data/.testagent/agent-opencode/.configdev/` | `D:/data/.testagent/agent-opencode/.configdev/` |

新增表：

| 表 | 说明 |
|---|---|
| `agent_config_worktrees` | 公共级/工作空间级 Agent 配置 worktree 记录，包含 scope、workspaceId、worktreeName、branch、rootPath、createdBy、status 和时间戳。后续迁移追加 `linux_server_id` 记录所在服务器。 |
| `agent_config_operations` | Agent 配置 Git 长操作进度快照，包含 operationId、scope、action、status、currentStep、错误信息、traceId、branch、commitHash 和时间戳。 |

兼容策略：

- `OPENCODE_PUBLIC_AGENT_GIT_URL` 默认 `UNCONFIGURED`；外部部署保存完整 Git URL，内部部署保存 `host[:port]/path` 片段并由 Java 按当前用户拼接 SSH URL。未配置时功能只读展示但禁用 Git 更新/发布；运维更新该参数值后启用。
- scope/status 枚举由领域对象校验，数据库保存字符串并保留非空约束，避免 H2 与 PostgreSQL 在同名列 check 表达式上的兼容差异。
- 公共 agent 标准目录是 `OPENCODE_PUBLIC_CONFIG_GIT_ROOT/opencode/agents/`；读兼容 legacy `opencode/agent/`，写入标准目录。
- 公共配置 Git 根目录首次使用时允许缺失或为空目录，初始化/公共更新流程会按所选分支 clone；公共 worktree 创建不再 clone，必须选择已初始化服务器，已有非空非 Git 目录不会被覆盖。
- 工作空间级标准目录是 `{workspace.rootPath}/.opencode/agents/`；读兼容 `.opencode/agent/`，写入标准目录。
- Agent 配置 operation 供 WebSocket snapshot 和历史查询使用，不写入 `run_events`，也不参与 RunEvent SSE 续传。

## V20260628194000 Agent 配置 worktree 服务器归属

`backend/test-agent-persistence/src/main/resources/db/migration/V20260628194000__add_agent_config_worktree_server.sql` 为 Agent 配置 worktree 增加分布式部署下的服务器归属字段。

| 表 | 字段 | 说明 |
|---|---|---|
| `agent_config_worktrees` | `linux_server_id` | Agent 配置 worktree 所在 Linux 服务器 ID；历史记录允许为空。 |

索引：

- `idx_agent_config_worktrees_linux_server(linux_server_id, status, updated_at)` 支撑按服务器归属查询、排查和后续清理。

兼容策略：

- 新建公共和工作空间 Agent worktree 均写入当前目标服务器 `linuxServerId`。
- 公共 worktree 后续文件、diff、stage、commit、publish 依据该字段由当前后端代理到目标服务器执行；浏览器不直连目标服务器。
- 历史空值记录按当前服务器兼容执行；如果本地目录不存在或归属无法确认，管理员重新创建 worktree。
- AgentConfig 持久化实现已迁到 MyBatis XML：`AgentConfigMapper.xml` 维护 SQL，`JdbcAgentConfigRepository` 仅保留为迁移窗口类，不再注册为 Spring Repository。

## V20260626120900 应用版本工作区服务器副本

`backend/test-agent-persistence/src/main/resources/db/migration/V20260626120900__add_managed_workspace_replicas.sql` 为多服务器应用版本工作区同步增加 commit 与副本记录：

| 表/字段 | 说明 |
|---|---|
| `application_workspace_versions.target_commit_hash` | 当前应用版本所有服务器副本应同步到的目标 Git commit hash，可空兼容历史数据。 |
| `application_workspace_versions.target_commit_updated_at` | 目标 commit 最近更新时间，可空兼容历史数据。 |
| `application_workspace_version_replicas` | 每台 Linux 服务器上的应用版本副本，记录副本路径、运行态 Workspace、当前 commit、同步状态和最近错误。 |

关键约束：

- `application_workspace_version_replicas(version_id, linux_server_id)` 唯一，保证同一应用版本在同一服务器只有一个副本。
- `runtime_workspace_id` 唯一并引用 `workspaces.workspace_id`，确保每个副本对应独立运行态 Workspace。
- `sync_status` 取值由业务枚举控制：`PENDING`、`SYNCING`、`READY`、`FAILED`。

兼容策略：

- 旧 `application_workspace_versions.runtime_workspace_id/repo_root_path/workspace_root_path` 保留，作为首次创建节点和旧响应兼容字段；新建/显式修复记录保存 `appworkspace:` 逻辑路径，接口响应返回解析后的当前服务器物理路径。
- migration 只对已具备 `workspaces.linux_server_id` 的历史应用版本回填副本；`current_commit_hash` 为空，由启动/周期补偿任务读取本机 Git HEAD 后更新。
- `target_commit_hash` 为空的历史版本在首次本机副本校验成功后由业务层回填为当前 HEAD；随后各服务器通过内部广播和补偿扫描追平。
- 物理仓库身份按 `app_id + repository_id + version + branch` 判定；同组不同 `application_workspace_id` 只是目录视图。兼容现有表结构，任何 publish、pull、新目录提交或版本读取都会把组内 `target_commit_hash/target_commit_updated_at` 扇出为同一值；检测历史值不一致时，以 `target_commit_updated_at` 最新的非空提交为准自动收敛。该修复不新增表和 migration。
- 历史 `personal_workspaces.workspace_root_path` 或关联 `workspaces.root_path` 指向个人仓库根、旧服务器绝对路径时，默认个人工作区进入流程会按版本、用户、仓库、分支和模板目录重新计算逻辑路径并更新既有记录；目标目录不存在且因本地变更不能合并时保持原数据并返回阻塞文件，禁止将仓库根继续写成工作空间根。

## 用户 → 应用 → 工作空间 默认进入行为

`user_application_workspace_preferences` 与 `user_global_workspace_preferences` 是前端"用户进入平台时默认工作空间"的持久化依据：

- `user_application_workspace_preferences(user_id, app_id)` 唯一键：前端 `GET /applications/{appId}/recent-workspace` 通过该键查询用户在指定应用下的最近工作空间。
- `user_global_workspace_preferences(user_id)` 唯一键：作为跨应用维度的兜底，避免用户切换应用时丢失上下文。
- 每次用户切换工作空间（`POST /workspaces/{workspaceId}/recent`）由后端 `ManagedWorkspaceApplicationService.markRecent` 同步写入两表，互不冲突。

首次进入（无 recent）行为：

- 前端 `handleSelectApp` → `pickDefaultWorkspaceForApp`：
  1. 读取 `user_application_workspace_preferences`；命中且能反查 `versionId` 时，只读查询该版本下当前用户已有个人工作区列表。
  2. 仅当存在 `workspaceName=default` 且带运行态 `workspaceId` 的个人工作区记录时加载该 default 私人 worktree。
  3. 未命中、命中记录不能反查 `versionId` 或没有 default 私人工作区记录：只选择应用，保持工作区空态，不兜底首模板首版本，不创建 default 私人 worktree。
  4. 空态仍保留左侧工作区切换入口，用户可手动选择版本、创建版本或新增私人工作区。

V10 种子数据对 F-COSS 的影响：

- `V10__seed_fcoss_application.sql` 同步写入 `user_application_workspace_preferences(user='888888888', app='app_fcoss', workspace='wrk_fcoss_20260701')`，本地开发用户首次进入 F-COSS 可还原到该应用/版本上下文；只有该版本下已经存在当前用户 `default` 私人工作区记录时才会自动加载工作区。
- 删除/重置后只要重新执行 `V10`（幂等）即可恢复默认状态；偏好表本身的幂等写入由 `INSERT ... ON CONFLICT DO UPDATE` 在 `ManagedWorkspaceRepository.savePreference` 内保证。

## opencode 用户进程管理表版本调整

设计阶段曾使用“V10 opencode 用户进程管理表”这一版本描述；实际仓库中 V10 保留给 F-COSS seed，消息/Run 消耗字段迁移使用 V16，最终表结构迁移以 `V14__create_opencode_process_management_tables.sql` 为准。该迁移都是新增表，不修改旧 `execution_nodes` 或 `sessions.opencode_*` 字段：

| 表 | 说明 |
|---|---|
| `linux_servers` | Linux 服务器持久拓扑快照，`linux_server_id` 保存稳定服务器身份，记录状态、历史心跳字段和容量摘要 JSON；在线状态以 Redis Java 快照为准。 |
| `backend_java_processes` | 后端 Java 实例持久拓扑，记录所属稳定服务器身份、实例直连地址、状态、启动时间和历史心跳字段；在线状态以 Redis Java 快照为准。首次心跳以进程启动时间写入 `created_at`，读取历史 `updated_at < created_at` 脏记录时按 `created_at` 归一化，避免阻断 manager 注册。 |
| `opencode_containers` | opencode worker 容器持久拓扑，记录所属 Linux 服务器、可读容器名称、独立端口池以及历史容量/状态；用户进程候选的在线状态和实时 `currentProcesses` 以 Redis manager 快照为准；`container_id` 是由稳定 `linux_server_id` 自动派生的 68 字符 SHA-256 ID。 |
| `opencode_container_managers` | 容器管理进程，每个容器最多一个 manager，`manager_id` 由 `container_id` 自动派生，记录协议版本、连接状态、能力 JSON 和历史心跳字段；在线状态以 Redis manager 快照为准。 |
| `opencode_manager_backend_connections` | manager 与后端 Java 实例的持久 WebSocket 连接拓扑，按 `(manager_id, backend_process_id)` 唯一；在线连接视图以 Redis manager 快照中的连接列表为准。 |
| `opencode_server_processes` | 用户专属 opencode server 进程的当前快照，记录用户、Linux 服务器、容器、主机直通端口、PID、`base_url`、启动路径和健康状态；后续状态查询、停止、重启或恢复可能覆盖状态、PID、traceId 和更新时间。 |
| `user_opencode_process_bindings` | 用户到 opencode 进程的当前绑定快照，按 `(user_id, agent_id)` 唯一，首期 `agent_id='opencode'`；不保存 binding 变更历史。 |
| `opencode_process_start_operations` | 每个显式 `operationId` 的当前用户 opencode 进程初始化进度结果，供前端轮询展示启动步骤和失败原因，也可在页面缓存清理或现场恢复后按用户、traceId 和时间继续排查。 |

关键约束：

- `linux_servers.linux_server_id` 使用稳定服务器身份，允许字母、数字、`.`、`_`、`-`，长度 1-128；`opencode_server_processes.base_url` 使用 advertised host 拼接端口，不再要求 host 等于 `linux_server_id`。
- 每个稳定 `linux_server_id` 只部署一个 worker；`opencode_containers.container_id = "ctr_" + SHA256("test-agent/opencode-container/v1\0" + linux_server_id)`，`opencode_container_managers.manager_id = "mgr_" + SHA256("test-agent/opencode-manager/v1\0" + container_id)`，SHA-256 为完整小写十六进制。现有 `varchar(128)` 和所有表约束无需变更。
- `opencode_containers` 使用 worker 独立端口范围，`max_processes` 不能超过端口数，`current_processes` 不能超过 `max_processes`。
- `opencode_container_managers.container_id` 唯一，保证每个容器只有一个管理进程。
- `opencode_server_processes(linux_server_id, port)` 唯一，保证同一 Linux 服务器端口不会绑定多个 opencode 进程。
- `user_opencode_process_bindings(user_id, agent_id)` 唯一，保证同一用户对同一 agent 只有一个当前绑定；`process_id` 同样唯一，避免一个进程被多个用户绑定。
- 用户进程候选不查询数据库中的 container/manager/connection 状态：只使用 TTL 10 秒的 Redis manager 最新快照，并由目标 Java 当前内存中的 manager WebSocket 连接做最终可调度校验。数据库继续承担拓扑历史、用户 binding、进程记录和端口避让；端口选择必须查询同一 `linux_server_id` 下所有状态的历史进程行，并由 `(linux_server_id, port)` 唯一约束兜底。

兼容策略：

- 旧 `execution_nodes` 继续保留，供无用户主体的 static-token 兼容调用和本地固定节点探测使用。
- `agent_session_bindings` 继续作为平台 Session 到远端 session/node 的主绑定表；用户进程模型只会在 binding 指向的节点与当前用户进程不一致时覆盖当前绑定，不删除旧远端 session。
- 历史 `opencode_server_processes.updated_at` 可能早于 `created_at`；读取这类旧记录时由 persistence 映射层按 `created_at` 归一化，避免领域对象校验阻断用户进程状态查询和重新初始化。新写入数据仍必须保持 `updated_at >= created_at`。
- 应用回滚时可保留这些新增表；如需完整回退 Web 用户对话到固定节点模式，应回滚后端和前端镜像，而不是删除 V10 表或清理 `/data/.testagent/agent-opencode/.session/{port}`。
- 后端启动或拓扑变化时更新 `linux_servers`、`backend_java_processes`，Java 进程在线心跳写入 Redis 快照，TTL 为 10 秒；manager WebSocket 注册更新 `opencode_containers`、`opencode_container_managers` 和 `opencode_manager_backend_connections` 的持久拓扑，`managerHeartbeat` 只写 Redis manager 快照，TTL 为 10 秒。数据库中的历史连接、容器状态和 `current_processes` 不作为用户进程实时分配候选。
- 本次只改变新注册记录的 ID 生成语义，不修改表结构、不新增 Flyway migration；当前系统尚未部署，不提供旧人工/hostname ID 的数据迁移。

## V20260702120000 opencode 进程初始化进度表

`backend/test-agent-persistence/src/main/resources/db/migration/V20260702120000__create_opencode_process_start_operations.sql` 创建 `opencode_process_start_operations`，只保存当前用户初始化 opencode 进程的一次进度快照，不写测试、演示或本地开发数据。

该表一行对应一个显式 `operationId`，操作结束后保留最后步骤和结果；浏览器清缓存不影响该表。
它不是 `opencode_server_processes` 的逐次变更审计日志：旧客户端、自动恢复或未携带 `operationId` 的运行管理启动不会产生对应操作行，
同一进程后续被强状态查询、停止、重启或恢复改写时，也不会在本表追加进程状态历史。排查
“进程分配已变化，拒绝旧启动结果回写”时，应先用本表确定显式操作的阶段与时间，再按
[后端部署说明的现场排查流程](backend.md#opencode-process-assignment-conflict-troubleshooting)关联当前进程快照、Java/manager 和 access log。

| 字段 | 说明 |
|---|---|
| `operation_id` | 前端生成的业务 ID，使用 `opi_` 前缀，唯一。 |
| `requested_by_user_id` | 发起初始化的用户，外键引用 `users.user_id`；进度查询按该字段做用户隔离。 |
| `agent_id` | Agent 标识，当前为 `opencode`。 |
| `status` | `RUNNING`、`SUCCEEDED`、`FAILED`。 |
| `current_step` | 当前启动步骤：校验、确认分配、选择容器、准备参数、进程启动、记录候选进程、检查进程、健康检查、写入绑定或完成。 |
| `error_code` / `error_message` | 失败时可展示给前端的稳定错误码和安全失败说明。 |
| `process_id` / `service_address` | 成功后回填的 opencode 进程 ID 和展示地址。 |
| `trace_id` | 初始化链路 traceId。 |

索引：

- `operation_id` 唯一约束支持按操作 ID 定位。
- `idx_opencode_process_start_operations_user_updated(requested_by_user_id, updated_at)` 支持当前用户进度查询和排查。

兼容策略：

- 旧客户端不传 `operationId` 时不写该表，`POST /initialize` 仍按同步接口返回。
- 该表是 HTTP 轮询进度快照，不写入 `run_events`，也不参与 RunEvent SSE 续传。

## V10 F-COSS 应用开发种子数据

`backend/test-agent-persistence/src/main/resources/db/migration/V10__seed_fcoss_application.sql` 在本地开发环境提供开箱即用的 F-COSS 应用数据，让工作台左下角的两级菜单（应用→工作空间→版本）首次进入就能看到内容。该文件保留 V10 版本号，兼容已经应用过旧 V10 seed 的本地库，避免 Flyway 报“applied migration not resolved locally”。

| 数据 | 标识 | 说明 |
|---|---|---|
| 应用 `app_fcoss`（F-COSS） | `applications` | 启用状态，配合 F-COSS 应用工作空间模板。 |
| 标准代码库 `repo_fcoss_main` | `code_repositories` | 占位 git URL，仅供 UI 浏览；不会被实际 clone。 |
| 应用工作空间模板 `aws_fcoss_main` | `application_workspaces` | `main` 分支 / `src/main` 目录的 F-COSS 主服务模板。 |
| 应用版本 `20260620` | `application_workspace_versions` → `wks_fcoss_20260620` | 默认模板派生出的首个 yyyyMMdd 版本。 |
| 应用版本 `20260701` | `application_workspace_versions` → `wks_fcoss_20260701` | 默认模板派生出的最新 yyyyMMdd 版本；同时作为默认 recent 偏好。 |
| 应用成员 | `application_members` | 把默认开发用户 `888888888` 加入 F-COSS，便于 `listApplications` 看到该应用。 |

兼容策略：

- 全部插入语句使用 `where not exists` / `where exists` 保护，重复执行迁移不会破坏数据。
- 仅在 `users.username = '888888888'` 存在时才插入应用、成员和 recent 偏好，避免在没有初始化用户的环境（如生产）执行失败。
- 不影响 V5/V8/V9 的用户、角色、配置表结构与已有迁移路径。

## V13 F-COSS 应用开发种子数据扩展

`backend/test-agent-persistence/src/main/resources/db/migration/V13__seed_fcoss_more_workspaces.sql` 在 V10 的基础上为 F-COSS 应用追加几个工作空间模板和初始版本，给「+新增版本」和工作空间选择器提供更多可选项：

| 数据 | 标识 | 说明 |
|---|---|---|
| 应用工作空间模板 `awp_fcoss_mobile` | `application_workspaces` | F-COSS 移动端，`mobile` 分支 / `src/mobile` 目录。 |
| 应用工作空间模板 `awp_fcoss_sync` | `application_workspaces` | F-COSS 数据同步，`sync` 分支 / `sync` 目录。 |
| 应用工作空间模板 `awp_fcoss_report` | `application_workspaces` | F-COSS 报表，`report` 分支 / `reports` 目录。 |
| 应用版本 `20260705` | `application_workspace_versions` → `wrk_fcoss_mobile_20260705` | 移动端首个 yyyyMMdd 版本。 |
| 应用版本 `20260710` | `application_workspace_versions` → `wrk_fcoss_sync_20260710` | 数据同步首个 yyyyMMdd 版本。 |
| 应用版本 `20260715` | `application_workspace_versions` → `wrk_fcoss_report_20260715` | 报表首个 yyyyMMdd 版本。 |

兼容策略：

- 全部插入语句使用 `where exists` / `where not exists` 保护，重复执行迁移不会破坏数据。
- 仅在 V10 的 `app_fcoss` / `repo_fcoss_main` 存在时才追加模板/版本，与 V10 的「依赖基础数据存在」策略一致。

## V14 opencode 用户进程管理表

`backend/test-agent-persistence/src/main/resources/db/migration/V14__create_opencode_process_management_tables.sql` 创建企业内部署所需的 opencode 用户进程管理表。V10 已用于 F-COSS 本地种子数据，因此该表结构迁移使用 V14，避免 Flyway 版本冲突。

| 表 | 说明 |
|---|---|
| `linux_servers` | 后端 Linux 服务器节点，记录状态、容量摘要和历史心跳字段；在线视图读取 Redis 快照。 |
| `backend_java_processes` | 后端 Java 进程实例，记录监听地址、所属 Linux 服务器和历史心跳字段；在线视图读取 Redis 快照。 |
| `opencode_containers` | opencode 容器，记录端口池、容量、当前进程数和状态。 |
| `opencode_container_managers` | 容器内管理进程，记录协议版本、连接状态、能力和历史心跳字段；在线视图读取 Redis 快照。 |
| `opencode_manager_backend_connections` | 管理进程到后端 Java 进程的控制面连接状态。 |
| `opencode_server_processes` | 用户专属 opencode server 进程，记录用户、端口、PID、session/config 路径和健康状态。 |
| `user_opencode_process_bindings` | 用户到 agent/opencode 进程的唯一绑定。 |
- `created_by_user_id` 选择 `users.username = '888888888'` 的用户，没有该用户时整条插入被跳过；不引入新用户。
- 不影响 V9 的表结构与已有迁移路径；模板与版本均为 ACTIVE，运行态 `workspaces` 同步 ACTIVE 状态。

## V20260625184300 scheduler 框架表与来源预留字段

`backend/test-agent-persistence/src/main/resources/db/migration/V20260625184300__create_scheduler_framework_tables.sql` 创建旧 scheduler 框架表，并给会话、Run、消息增加来源预留字段。迁移到 XXL 后这些表只保留历史与审计，不再由应用 runner 调度。该版本使用 14 位时间戳，避免与既有数字版本冲突。

| 表 | 说明 |
|---|---|
| `scheduled_tasks` | 代码注册任务定义，保存任务 key、名称、Cron、启停、锁 TTL、下次触发时间和注册状态。 |
| `scheduled_task_plans` | 用户级 Cron 计划预留表，包含 owner 用户、Cron、payload、启停和下次触发时间。 |
| `scheduled_task_runs` | 旧 Cron、手工与 `USER_PLAN` 运行历史；迁移后不再新增。 |

关键字段：

- `scheduled_tasks.task_key` 唯一，作为代码注册、数据库定义、Redis 锁和运行记录关联键。
- `scheduled_tasks.lock_ttl_seconds` 保存 Redis 锁租约秒数，必须为正数。
- `scheduled_task_plans.plan_id`、`scheduled_task_runs.task_run_id` 是业务 ID，不暴露数据库自增 surrogate PK。
- `scheduled_task_runs.trigger_type` 兼容历史 `CRON`、`MANUAL`、`USER_PLAN`；新业务不再写入。
- `scheduled_task_runs.status` 当前支持 `PENDING`、`RUNNING`、`STOPPING`、`SUCCEEDED`、`FAILED`、`SKIPPED`、`MANUALLY_STOPPED`。
- `scheduled_task_runs.skip_reason` 保存同一 `taskKey` 已有未结束运行或 Redis 锁竞争失败时的跳过原因。
- `scheduled_task_runs.stop_requested_at`、`stop_requested_by_user_id`、`stop_reason` 记录超级管理员发起协作式停止的时间、操作者和原因。
- `V20260715000000__add_scheduler_run_retention_index.sql` 为 `scheduled_task_runs.ended_at` 创建索引。`scheduler.run-retention-cleanup` 现在由 XXL 在北京时间 08:00 触发同一 handler，删除早于 7 天的已结束 PostgreSQL 记录；活动状态始终保留。

新增来源字段：

| 表 | 字段 | 说明 |
|---|---|---|
| `sessions` | `source_type`、`source_ref_id`、`created_by_user_id` | 会话来源，默认 `MANUAL`；支持 `SCHEDULED_TASK` 和内部 `SIDE_QUESTION`。 |
| `runs` | `source_type`、`source_ref_id`、`triggered_by_user_id` | Run 来源，默认 `MANUAL`；宠物旁路 Run 使用 `SIDE_QUESTION`。 |
| `session_messages` | `source_type`、`source_ref_id`、`sender_user_id` | 消息来源，默认 `MANUAL`；允许值注释包含 `SIDE_QUESTION`。 |

兼容策略：

- 旧数据通过 `default 'MANUAL'` 保持兼容；新增用户字段均可空。
- `scheduled_task_plans`、`scheduled_tasks` 和 `scheduled_task_runs` 只作为历史预留/审计，不开放可用管理 API；夜间任务不再写这些表。
- 分布式互斥由 Redis 锁保证，数据库表不作为锁 fallback；Redis 不可用时 scheduler 启用校验失败或运行失败，不降级为本机锁。
- `result_json`、`payload_json` 保存结构化 JSON 文本，禁止写入密钥、Token、完整 prompt 或其他敏感内容。

## V20260718210000 scheduler USER_PLAN 执行亲和

`V20260718210000__extend_scheduler_user_plan.sql` 是旧夜间实现的兼容 schema：允许 `scheduled_tasks.cron_expression` 为空，并为 `scheduled_task_runs` 增加可空 `execution_affinity` 和到期索引。新版本不再读取该 affinity 或创建 `USER_PLAN`；字段、索引与 MyBatis 映射保留用于历史查询，旧 `JdbcScheduledTaskRepository` 仍保持删除状态。

## V20260718211000 夜间异步执行任务

`V20260718211000__create_night_execution_tasks.sql` 创建三张表：

| 表 | 说明 |
|---|---|
| `night_execution_tasks` | 定时任务聚合，保存 owner、Session/Workspace、幂等请求、展示预览、待执行输入、调度模式/时间窗口、固定目标服务器、历史 USER_PLAN/当前 Run 关联、状态和安全错误。 |
| `night_execution_session_locks` | 每个 Session 至多一个待执行任务的持久化写锁；任务取消、最终失败或 Run 成功创建后删除。 |
| `night_execution_slot_reservations` | 以 `slot_start` 为主键保存每个 15 分钟时段的全局已占名额；条件更新保证不超过当前内存容量快照。 |

关键约束和保留策略：

- `task_id` 唯一；历史 `scheduled_task_run_id` 保持 nullable/唯一，新任务不再写入；`(owner_user_id, client_request_id)` 保证用户提交幂等。
- `status` 仅允许 `SCHEDULED/DISPATCHING/DISPATCHED/CANCELLED/FAILED`，`slot_start < slot_end <= window_end`。
- `night_execution_session_locks.session_id` 为主键，`task_id` 唯一，数据库层保证单会话互斥；删除任务时锁级联删除。
- `run_input_json` 只在待执行期短期保存完整 Run 输入，任务进入 `DISPATCHED/CANCELLED/FAILED` 后清空；查询 API 只返回 `content_preview`。不得把该字段写入 XXL 参数/结果、跨服务器请求、RunEvent、日志或运营分析表。终态行清理在删除时再次校验状态、30 天 cutoff 和 `state_version`，避免与失败卡关闭等并发更新竞态。
- 任务绑定已有 Session，或在创建事务中预创建 `source_type=SCHEDULED_TASK/source_ref_id=task_id` 的空白 Session。成功 Run 和 USER 消息沿用同一来源字段；旧数据默认 `MANUAL` 兼容。
- 终态任务和历史容量行保留 30 天，由 `opencode-runtime.night-execution-reconcile` 分批清理。标准夜间任务在普通 Run 受理、取消和最终失败时立即释放时段名额；改期先占用新时段，事务成功后再释放原时段。
- 全部关系型 SQL 位于 `NightExecutionTaskMapper.xml`；首次创建用 PostgreSQL 事务级 advisory lock 串行化同一 owner/request。新分发认领和补偿 fencing 由下节 migration 增加。

## V20260722130000 夜间任务迁移到 XXL-JOB

`V20260722130000__migrate_night_execution_to_xxl.sql` 的版本晚于已交付的 `V20260721213000`，保证存量库按序升级；它为 `night_execution_tasks` 增加：

| 字段 | 说明 |
|---|---|
| `dispatch_attempt_id` | 当前分发尝试的唯一 fencing token。 |
| `dispatch_owner_backend_process_id` | 认领任务的精确 Java `backendProcessId`，不只记录 Linux 服务器。 |
| `dispatch_lease_until` | 5 分钟分发租约截止时间；同步 Run 受理调用每分钟续租。 |
| `state_version` | 聚合状态 CAS 版本，默认 `0`，兼容旧行。 |

同一 migration 还为 `runs` 增加 `scheduled_dispatch_attempt_id`、`scheduled_dispatch_lease_until`、`scheduled_dispatch_accepted_at`。默认 `LEGACY_FULL` 夜间 Run 先写唯一锚点和当前 attempt/租约；只有异步 prompt 已提交或按 remoteSessionId + dispatchMessageId 确认远端消息存在后才写受理时间。锚点后崩溃的下一 attempt 必须等待旧租约到期，以 MyBatis XML CAS 认领并复用原 `run_id + dispatch_message_id` 继续启动；重投前远端探测为 ACCEPTED 时只补标记、NOT_ACCEPTED 时才提交、UNKNOWN 时不提交，远端副作用前再次续租，旧 attempt 不能提交或完成新认领。活动态 legacy 锚点缺少受理时间时，补偿不得退回“已有平台用户消息即成功”的历史判断；通用 stale legacy Run 扫描还会排除带 Scheduled attempt 且尚未受理的锚点。

迁移重建 `status + slot_start + window_end + created_at` 到期扫描索引，并增加 `status + dispatch_lease_until + created_at`、`owner + status + lease` 补偿索引。新到期查询固定为 `SCHEDULED AND slot_start<=now AND window_end>now`，按 `slot_start, created_at` 返回最多 500 条；认领匹配 `status + state_version + target_linux_server_id`，续租、完成、失败和回退统一匹配 `task_id + DISPATCHING + dispatch_attempt_id`，旧执行者无法覆盖新 attempt。

兼容数据处理只做生产必需收敛：旧 `opencode-runtime.night-execution` 的 `PENDING/RUNNING/STOPPING USER_PLAN` 运行全部标记为 `SKIPPED` 并补齐结束时间；`SCHEDULED` 任务清空历史 `scheduled_task_run_id`；遗留 `DISPATCHING` 补齐已过期的迁移 attempt/owner/lease，由 5 分钟补偿先查 Run 锚点再恢复。历史运行和审计行不删除。发布必须先停止全部旧 Java，再应用 PostgreSQL/XXL MySQL migration 并启动新版本，避免两套入口并行。

## V20260724143000 夜间任务调度模式

`V20260724143000__add_night_execution_schedule_mode.sql` 为 `night_execution_tasks` 增加非空 `schedule_mode varchar(32)`，默认值为 `NIGHT_WINDOW`，并通过检查约束只允许 `NIGHT_WINDOW/ADMIN_CUSTOM`。存量任务和未显式写入该列的旧客户端数据自动回填为 `NIGHT_WINDOW`，因此原夜间窗口、容量和展示语义保持不变。

`ADMIN_CUSTOM` 仍写同一任务表并复用同一到期扫描索引：`slot_start` 为超级管理员选择的完整分钟，`slot_end=slot_start+1 分钟`，`window_end=slot_start+15 分钟`。该模式不写 `night_execution_slot_reservations`，终态也不记录容量释放时间；会话锁、固定目标服务器、attempt、租约和 Run 幂等字段与标准夜间任务一致。新增读写 SQL仍位于 `NightExecutionTaskMapper.xml`，没有新增 JDBC 或 MyBatis 注解 SQL。

XXL MySQL 的独立 migration `xxl-job/db/migration/V5__schedule_night_execution_dispatch_every_minute.sql` 只把 `platform_task_key=opencode-runtime.night-execution-dispatch` 的 Cron 从每 15 分钟改为 `0 0/1 * * * ? *`，并把 `trigger_next_time` 清零以便 Admin 重算。执行器组、启停状态、`GLOBAL_MUTEX`、`ROUND`、`DISCARD_LATER`、`DO_NOTHING` 和重试次数 `0` 均保持不变；平台 PostgreSQL Flyway 不扫描该路径。

## V20260719210000 夜间任务容量通用参数

`V20260719210000__seed_night_execution_capacity_parameter.sql` 初始化生产必需系统参数：

| 参数 | 平台 | 初始值 | 可修改 | 用途 |
|---|---|---:|---|---|
| `NIGHT_EXECUTION_SLOT_CAPACITY` | `all` | `20` | `true` | 每个北京时间 15 分钟夜间启动时段的全局任务数上限。 |

该参数由 `SUPER_ADMIN` 通过既有通用参数管理 API 修改；服务端只接受正整数。每个 Java 实例启动时从数据库加载到 `NightExecutionCapacityRegistry`，缺失或非法时启动失败；修改后通过既有 `common-parameter.refresh-requested` 广播触发各实例查库并原子替换内存快照，广播 payload 不携带参数值。运行中刷新失败保留上一有效值。调低容量不删除既有任务或容量占位，只阻止已达到新上限的时段继续预约；调高后后续查询和占位立即使用新值。旧部署容量环境变量不再读取。

## V20260801093854 / V20260801104000 UI 平台地址通用参数

`V20260801093854__seed_ui_test_platform_base_url.sql` 是已经执行过的早期种子 migration，SHA-256 固定为 `aa08c1cedc64bd0b8dd230227f9dcb7a0ef6a33572473d8a14795f5f6b93e6e5`，原始参数名和业务 ID 必须保持字节不变，避免破坏既有 Flyway checksum。`V20260801104000__rename_ui_test_platform_parameter.sql` 的 SHA-256 固定为 `0e306671eda36a9bb8881cf3d85b4e87b5373e00770dcd6503693b11d008e45c`；它在后一版本把该行迁移为 `UITEST_BASE_URL/all` 和 `param_uitest_base_url_all`，保留原参数值、创建时间、可编辑状态及已有修改日志关联。新环境顺序执行两条 migration 后也只保留新名称，两条已执行文件均由 `FlywayMigrationNamingTest` 锁定原始字节。

`UITEST_BASE_URL` 初始值为 `UNCONFIGURED`，由超级管理员在通用参数管理页面修改。它是生产 UI 自动化调用所需的系统参数，不是测试或个人环境数据。消费方每次调用前直接从数据库读取，不写入 JVM 或 Redis 缓存；地址修改后下一次调用即时生效。管理 API 只接受无 user-info、query 和 fragment 的 HTTP/HTTPS 地址，或显式停用值 `UNCONFIGURED`。

## V20260625192100 scheduler 停止字段与状态字典

`backend/test-agent-persistence/src/main/resources/db/migration/V20260625192100__extend_scheduler_management_stop_and_dicts.sql` 是旧 scheduler 管理页的兼容 schema。迁移后字段和字典保留历史，不再由已作废的管理 API 写入：

- `scheduled_task_runs` 增加 `stop_requested_at`、`stop_requested_by_user_id`、`stop_reason`，用于记录管理员停止正在运行任务的审计信息；`stop_requested_by_user_id` 外键指向 `users(user_id)`。
- 新增 `idx_scheduled_task_runs_stop_user`，支持按停止操作者追溯。
- seed `SCHEDULER_RUN_STATUS`、`SCHEDULER_TRIGGER_TYPE`、`SCHEDULER_TASK_REGISTRATION_STATUS` 字典，供 scheduler API 直接返回中文 label。字典缺失时 API fallback 为原 code，不影响运行。
- active run 判定包含 `PENDING`、`RUNNING`、`STOPPING`；管理员手动触发遇到 active run 返回冲突，Cron 重叠触发仍按框架记录 `SKIPPED`。

## V11 用户工作区分支偏好表

`backend/test-agent-persistence/src/main/resources/db/migration/V11__create_user_workspace_branch_preferences.sql` 持久化用户在 (appId, workspaceId) 维度下最近一次手动选择的 VCS 分支，支撑工作台工作区下分支选择按钮的"下次进入默认切换"：

| 表 | 说明 |
|---|---|
| `user_workspace_branch_preferences` | 记录 (userId, appId, workspaceId) 维度下用户最近选择的 VCS 分支，唯一键保证同一 (user, app, workspace) 仅保留最新一条。 |

| 字段 | 说明 |
|---|---|
| `id` | 数据库自增 surrogate PK，不对 API 暴露。 |
| `user_id` | 关联 `users.user_id`。 |
| `app_id` | 关联 `applications.app_id`。 |
| `workspace_id` | 关联 `workspaces.workspace_id`。 |
| `branch` | VCS 分支名，最大 255 字符。 |
| `updated_at` | 最近一次写入时间。 |

索引与约束：

- `uk_user_workspace_branch_preferences_scope(user_id, app_id, workspace_id)` 唯一约束，`ManagedWorkspaceRepository.saveBranchPreference` 命中即更新 branch 与 updated_at。
- `fk_user_workspace_branch_preferences_user/app/workspace` 外键保证用户、应用、工作区存在。
- `idx_user_workspace_branch_preferences_user(user_id, updated_at)` 支撑"我最近切过分支的所有工作区"列表型查询。
- `idx_user_workspace_branch_preferences_workspace(workspace_id, updated_at)` 支撑按工作区维度排查分支偏好。

兼容策略：

- 与 `user_application_workspace_preferences` 保持一致：复合唯一键、upsert 写入，不引入历史数据迁移。
- 唯一键冲突由 `INSERT ... ON CONFLICT DO UPDATE` 在 Jdbc 仓库内显式处理，重复执行迁移不会破坏数据。
- 仅在分支切换按钮的 `markRecentBranch` 接口写入，删除工作区或重置偏好时直接 `DELETE` 行即可，不影响运行态工作区。

## V12 AI 模型配置表

`backend/test-agent-persistence/src/main/resources/db/migration/V12__create_ai_model_configs.sql` 创建 `ai_model_configs`。该表保留历史兼容，不再作为前端对话框模型和供应商目录事实源；对话框目录始终来自 opencode 配置文件的 `/api/model`、`/api/provider`。

| 字段 | 说明 |
|---|---|
| `id` | 数据库自增 surrogate PK，不对 API 暴露。 |
| `provider_id` | 模型所属 provider，企业内默认 `enterprise-openai`。 |
| `model_id` | 模型标识，例如 `DeepSeek-V4-Flash-W8A8`。 |
| `name` | 前端展示名称。 |
| `enabled` | 是否在模型目录中展示。 |
| `default_model` | 是否为默认模型；前端优先选中该模型。 |
| `input_modalities_json` | 输入模态 JSON 文本，例如 `["text"]` 或 `["text","image"]`。 |
| `context_limit` | 上下文窗口限制。 |
| `output_limit` | 输出 token 限制。 |
| `sort_order` | 模型展示排序，默认模型仍会优先展示。 |
| `metadata_json` | 模型来源等扩展元数据 JSON 文本。 |
| `created_at` | 创建时间。 |
| `updated_at` | 更新时间。 |

约束和索引：

- `uk_ai_model_configs_provider_model` 保证同一 provider 下模型唯一。
- `idx_ai_model_configs_provider_enabled(provider_id, enabled, sort_order)` 支持模型目录按 provider 查询启用模型。

兼容策略：

- 新实现不再启动时 seed 企业内模型清单，也不再用该表校验 Run 请求模型；历史数据可留存用于追溯。

`V20260708100000__create_internal_model_provider_tables.sql` 创建内部模型代理配置表：

- `internal_model_providers(provider_id, name, base_url, enabled, sort_order, created_at, updated_at)` 最初只保存内部供应商地址；`provider_id` 对应代理请求头 `X-Enterprise-Model-Provider` 的路由键（当前为 `qwen-prod` / `deepseek-prod`），不是 opencode 配置中的 `enterprise-qwen` / `enterprise-deepseek` provider key。
- `internal_model_proxy_settings(setting_id='default', enterprise_openai_auth_token, created_at, updated_at)` 是历史全局 Token 单例表，按既有约定明文保存且不回显到前端。
- `V20260716143000__rename_internal_model_auth_token_column` Java migration 按第二列识别并重命名既有环境的历史鉴权列；新建数据库已使用目标列名时幂等跳过。两条因去机构标识而调整的历史 SQL migration 通过兼容注释保持原 Flyway checksum，升级不需要执行 `repair`。

`V20260722180000__add_internal_model_token_definitions.sql` 增加可复用 Token 定义与 Provider 关联：

- 新建 `internal_model_tokens(token_id, name, token_value, legacy_key, created_at, updated_at)`。`token_id` 由数据库 identity 生成，只标识平台记录；Token 值来自外部系统，平台不生成。`name` 唯一，`token_value` 按既有约定明文保存，`legacy_key='default'` 仅标识滚动升级兼容记录。
- `internal_model_providers` 增加可空 `token_id` 外键，删除规则为 `RESTRICT`；应用层也使用条件删除，在仍有 Provider 引用时返回 `CONFLICT`。
- 升级时若旧单例 Token 非空，只创建一条名为“默认 Token”的兼容记录，并让全部现有供应商共享引用；旧值为空时不创建定义，也不伪造 Token。
- 旧 `internal_model_proxy_settings` 不删除，供混合版本 Java 短期读取及旧 `authToken` 请求双写；新 Java 运行时只从 Provider/Token 联表快照取值。
- 供应商或 Token 成功变更继续发布既有 `internal-model-provider.refresh-requested` 广播，各 Java 一次联表重载 `providerId -> provider/token` 内存快照。单次代理请求不访问数据库。

滚动升级必须先完成所有 Java 节点的后端升级，再开放新页面的 Token 维护；混合版本期间不得为不同 Provider 配置不同 Token。

## V20260708200000 用户级历史会话索引

`backend/test-agent-persistence/src/main/resources/db/migration/V20260708200000__add_user_session_history_indexes.sql` 为当前用户历史会话列表增加归因查询索引：

| 索引 | 用途 |
|---|---|
| `idx_sessions_user_active_updated(created_by_user_id, status, updated_at, id)` | 支持按会话创建人查询当前用户 ACTIVE 历史并按更新时间倒序分页。 |
| `idx_runs_session_trigger_user(session_id, triggered_by_user_id)` | 支持旧会话通过 Run 触发人归因到当前用户。 |
| `idx_session_messages_session_sender(session_id, sender_user_id)` | 支持旧会话通过用户消息发送人归因到当前用户。 |

用户历史列表 SQL 位于 `SessionHistoryMapper.xml`，通过 MyBatis XML left join 个人工作区、应用版本工作区、副本、应用工作空间模板和应用信息补齐 `workspaceContext`。该查询只返回 `sessions.status='ACTIVE'` 且能归因到当前用户的会话，排序严格使用 `sessions.updated_at desc, sessions.id desc`，`pinned` 字段仅保留展示和兼容，不参与用户历史排序。

## V16 会话消息与 Run 消耗快照字段

`backend/test-agent-persistence/src/main/resources/db/migration/V16__add_message_and_run_usage_fields.sql` 扩展 `session_messages` 和 `runs`：

### session_messages 扩展字段

| 字段 | 说明 |
|---|---|
| `run_id` | 本条消息归属的 Run，可空；用户输入在启动 Run 时写入，assistant 快照在 Run 终态/取消后回写。 |
| `agent_id` | 归一化后的 agent 标志，例如 `opencode`。 |
| `remote_message_id` | 远端 agent message id，用于 projected messages 刷新时幂等 upsert。 |
| `parts_json` | 远端 message parts 的 JSON 文本快照，前端优先用它展示结构化 part，旧 `content` 仍作为 fallback。 |
| `tokens_input` / `tokens_output` / `tokens_reasoning` | 单次 Run 对应 assistant 输出的 token 消耗，可空。 |
| `tokens_cache_read` / `tokens_cache_write` | cache token 消耗，可空。 |
| `cost_usd` | 本次 Run 成本美元快照，可空。 |
| `updated_at` | 快照更新时间；历史数据迁移时回填为 `created_at`。 |

新增索引：

- `idx_session_messages_session_run(session_id, run_id, created_at, id)` 支持按会话和 Run 查询消息快照。
- `idx_session_messages_session_remote(session_id, remote_message_id)` 支持远端 message 幂等刷新。

### runs 扩展字段

`runs` 同步新增 `tokens_input`、`tokens_output`、`tokens_reasoning`、`tokens_cache_read`、`tokens_cache_write`、`cost_usd`，用于按 Run 查询每次对话消耗；缺失统计时保持 `null`。

新增索引：

- `idx_runs_session_active_updated(session_id, status, updated_at, id)` 支持 `GET /api/sessions/{sessionId}/active-run` 查询最近非终态 Run。

兼容策略：

- 旧消息没有 `run_id`、`remote_message_id`、`parts_json` 和 token/cost 时仍通过 `content` 展示。
- 远端 opencode 不可用时，消息查询回退读取数据库快照，不要求 V10 字段非空。
- `run_id` 外键引用 `runs.run_id`，字段可空，避免历史消息迁移失败。

## V17 本地 opencode 机器与默认开发用户进程种子（历史）

`backend/test-agent-persistence/src/main/resources/db/migration/V17__seed_local_opencode_machine_for_default_user.sql` 曾为本地开发环境预置一个 "本地 opencode 机器"（Linux 服务器 + 容器 + 管理进程）和默认开发用户 `usr_test_dev`（用户名 `888888888`）的进程绑定。该 migration 已可能在历史本地库或共享库执行过，禁止删除、重命名或直接改写，否则会破坏 Flyway validate。

兼容历史本地库时，V17 不只按固定 `process_id='ocp_local_user_dev'` 判断是否已种子化，也会检查 `linux_server_id='127.0.0.1' and port=4096` 是否已有 opencode 进程。若同端口已有旧进程，迁移复用该进程写入默认用户绑定，不再插入新的 `ocp_local_user_dev`，避免 `uk_opencode_server_processes_linux_port` 唯一约束阻塞本地启动。

历史种子数据：

| 表 | 关键字段 | 值 |
|---|---|---|
| `linux_servers` | `linux_server_id` / `name` / `status` | `127.0.0.1` / `local-opencode-host` / `READY` |
| `opencode_containers` | `container_id` / `port_start..end` / `max_processes` / `current_processes` / `status` | `ctr_local_4096` / `4096..4096` / `1` / `1` / `READY` |
| `opencode_container_managers` | `manager_id` / `container_id` / `connection_status` | `mgr_local_4096` / `ctr_local_4096` / `CONNECTED` |
| `opencode_server_processes` | `process_id` / `user_id` / `port` / `base_url` / `status` | `ocp_local_user_dev` / `usr_test_dev` / `4096` / `http://127.0.0.1:4096` / `RUNNING` |
| `user_opencode_process_bindings` | `user_id` / `agent_id` / `process_id` / `status` | `usr_test_dev` / `opencode` / `ocp_local_user_dev` / `ACTIVE` |

说明：

- `opencode_server_processes.base_url` 满足 V15 校验 `= 'http://' || linux_server_id || ':' || port`。
- `process_id` 以 `ocp_` 开头（V15 校验），`manager_id` 以 `mgr_` 开头（V15 校验）。
- `OpencodeManagerBackendConnection` 的 `backend_process_id` 形如 `bjp_xxx`，由后端 `BackendJavaProcessLifecycleService.registerHeartbeat` 在启动时为本实例补齐，因此 migration 不预置该行。
- 补齐逻辑详见 `backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/process/socket/BackendJavaProcessLifecycleService.java#bootstrapLocalManagerConnections`，仅在 (manager, backend) 组合不存在连接行时插入；已有行只更新兼容字段 `last_heartbeat_at` / `status`。真实 manager 连上后由 `ManagerControlApplicationService.register` 维护持久连接行，在线连接状态由 Redis manager 快照表达。
- 后续完整迁移会执行 `V20260627000000__cleanup_loopback_linux_server_seed.sql` 清理这些 `127.0.0.1` 行；本地开发不再依赖 V17 数据，必须通过真实 manager/backend 心跳注册获得运行态拓扑。

兼容策略：

- V17 自身仍保留既有幂等写法，保证只迁移到 `target=17` 的历史库行为不变。
- 仅在 `users.user_id = 'usr_test_dev'`（V5 默认开发用户）存在时才插入 `opencode_server_processes` 与 `user_opencode_process_bindings`；生产环境无该用户时整段种子不写用户进程相关表。

## V20260627000000 清理 V17 本地 loopback 种子数据

`backend/test-agent-persistence/src/main/resources/db/migration/V20260627000000__cleanup_loopback_linux_server_seed.sql` 用于清理 V17 预置的 `linux_server_id='127.0.0.1'` 本地 opencode 种子拓扑。该脚本只删除历史测试/本地开发数据，不删除默认用户、角色字典或真实 IP 服务器行。

清理范围：

| 表 | 清理条件 |
|---|---|
| `opencode_manager_backend_connections` | `manager_id` 属于 `127.0.0.1` 的 manager / loopback container，或 `backend_process_id` 属于 `127.0.0.1` 的后端进程 |
| `user_opencode_process_bindings` | `linux_server_id = '127.0.0.1'`，或绑定的进程引用 loopback container |
| `opencode_server_processes` | `linux_server_id = '127.0.0.1'`，或 `container_id` 引用 loopback container |
| `opencode_container_managers` | `linux_server_id = '127.0.0.1'`，或 `container_id` 引用 loopback container |
| `opencode_containers` | `linux_server_id = '127.0.0.1'` |
| `backend_java_processes` | `linux_server_id = '127.0.0.1'` |
| `linux_servers` | `linux_server_id = '127.0.0.1'` |

约束：

- 外键未配置级联删除，脚本按子表到父表顺序删除。
- 历史库可能存在进程自身 `linux_server_id` 不是 `127.0.0.1`、但 `container_id` 仍指向 V17 loopback container 的脏数据，清理脚本必须先删这类进程和绑定再删容器。
- 全部使用 `delete where` 条件，重复执行不会报错。
- 不把测试、演示、个人开发或环境专属数据继续写入 Flyway；本地初始化数据应放在测试 fixture、mock、`test-agent-test-support` 或显式本地开发脚本中。

本地开发健康检测/启动网关选择：

- `SocketOpencodeProcessManagerGateway` 是唯一生产装配，本地和生产都走 manager WebSocket；本地没起 manager 时 health/start 都会返回 `OPENCODE_UNAVAILABLE`，前端状态会落到 "opencode 进程健康检测失败，需要重新初始化"。
- `application-local.yml` / `application-guo.yml` 不再配置 `gateway-mode=local` 或 `local-direct`；本地调试用户进程必须启动 Go manager，并依赖真实 manager/backend 心跳注册获得运行态拓扑。

## V20260725143000 Agent & Skill Hub 不可变快照

新增六张 PostgreSQL 表：

- `agent_skill_hub_artifacts`：以 canonical JSON 的 SHA-256 为主键，保存 `GZIP_JSON_V1` 压缩 `bytea`、manifest、压缩前后大小和文件数；相同内容跨应用/提交物理去重。
- `agent_skill_hub_assets`：以“来源应用 + 应用工作空间模板 + 类型 + 英文技术 ID”唯一标识逻辑资产，并指向最新 pushed / published 修订。
- `agent_skill_hub_revisions`：每个来源 Git commit 固化一个不可变修订；`(asset_id, source_commit_hash)` 唯一。删除用 tombstone 修订表达，不改写历史制品；更新判断比较内容 SHA-256，避免未改动资产因新 commit 误报。
- `agent_skill_hub_dependencies`：发布时把依赖固定到精确资产修订。
- `agent_skill_hub_references`：保存目标应用工作空间路径、别名、active/pending 修订和待推送内容摘要；它描述应用级引用状态，不绑定单个用户 worktree。
- `agent_skill_hub_update_operations`：保存三方合并的 base/current/incoming 冲突状态；终态为 `COMPLETED` 或 `ABORTED`。

快照只在远端 push 成功后从该次精确 commit 读取，单资产最多 256 个文件、解压前总计 20 MiB；读取时重新校验压缩编码和 SHA-256，解压安全上限为 32 MiB。关系型 SQL 全部位于 `AgentSkillHubMapper.xml`，Flyway 仅负责结构创建。

## V20260725230000 Hub 取消引用状态

- `agent_skill_hub_references.status` 增加 `PENDING_REMOVE`：文件先从个人 worktree 移除，远端 push 确认目标路径已不存在后才删除引用记录。
- `agent_skill_hub_update_operations.reference_id` 外键改为 `ON DELETE CASCADE`；正式解除引用时同步清理只服务于该引用的临时三方合并操作。
- `PENDING_PUSH` 与 `ACTIVE` 继续严格区分；前者只表示本地已写入，不表示远端应用已经引用。

## 后续 migration 版本规则

V18 及以前保留既有数字版本，已在共享或稳定数据库执行过的 migration 禁止删除、重命名或改写。V18 之后新增 migration 必须使用 `VyyyyMMddHHmmss__description.sql`，开发分支创建时可先使用本地时间作为候选版本；多人并行开发时不得再抢占 `V19`、`V20` 这类顺序数字版本。提交前需运行持久化模块 migration 命名测试，确认版本唯一、历史已落库 migration 仍可解析且时间戳规则生效。

14 位时间戳只能降低同号冲突，不能保证多个分支按相同顺序合并和部署。多人或多分支同时增加 migration 时，发布集成人必须执行以下门禁：

1. 以本次所有目标环境 `flyway_schema_history` 的全部已执行 `version/checksum/success` 为发布基线，同时列出自上次已部署提交以来所有待合并 migration，不能只检查自己分支的文件名或最高版本。
2. 尚未进入任何共享或稳定数据库的 migration 可以在合并前统一调整候选时间戳；最终版本必须彼此严格递增，并全部高于发布基线。版本顺序应表达依赖顺序，不以提交先后或谁先部署为准。
3. migration 一旦进入任何共享、稳定或企业数据库即视为字节不可变；SQL 改成幂等形式、只改注释或空白也会改变 checksum，不是兼容方案。已执行文件必须保留原始字节并用 SHA-256 回归锁定。若不同环境已经形成分叉，立即停止合并和发布，先盘点各环境历史，再通过现有 Flyway 兼容装配和隔离 location 制定显式方案；禁止新建第二套迁移器，也禁止用 `SPRING_FLYWAY_OUT_OF_ORDER=true`、Flyway `repair` 或手工修改 `flyway_schema_history` 让校验表面通过。
4. 正式打包前必须用真实 PostgreSQL 分别模拟空库、已部署企业基线和每套已知分叉历史，再使用默认 Flyway 配置升级到当前 HEAD，覆盖“旧包已运行、新包首次启动”的现场路径；只验证空库全量建库不算通过。
5. 正式 JAR/ZIP 产生后必须解出其中 migration 计算 SHA-256，与通过上述升级测试的源码比较。当前企业包使用瘦 `test-agent-app.jar` 和外置 `backend/lib/`，migration 位于 `test-agent-persistence-*.jar`；必须同时校验发布 ZIP 内与目标机 `/data/testagent/dist/backend/lib/` 安装后的 persistence JAR，且完整 JAR SHA 一致。包内字节不同、目标库出现未知 checksum，或没有取得目标库 history 时，均不得进入部署。

多台 Java 对同一套、已排好序的 migration 并发启动由 Flyway schema history 锁负责互斥，不是这里的问题；这里防的是不同开发者把较小的新版本晚合入，导致目标库已经执行更大版本后拒绝启动。

## V20260730090000 LobeHub 企业模型目录与每日聚合

`V20260730090000__add_lobehub_model_gateway.sql` 是开发期候选版本，创建以下平台 PostgreSQL 结构：

| 表 | 口径与边界 |
|---|---|
| `internal_model_provider_models` | 关联现有 `internal_model_providers`，保存全局唯一公开模型 ID、仅网关可见的上游 ID、展示名、上下文限制、启用状态和九项声明能力。 |
| `internal_model_provider_model_probes` | 按供应商、模型和能力覆盖保存最近一次成功/失败与时间；不保存固定探测输入、上游响应或原始错误。 |
| `model_gateway_usage_daily` | 按日期、来源 client、用户、供应商、公开模型和端点原子累加请求/成功/失败、token 和总耗时；不保存逐请求、prompt、回答、UCID、traceId 或错误。 |

历史企业基线包的精确源码提交为 `0352efa987219b9dde5c09e77b1eabfa719fc068`；该提交主 migration
最高为 `V20260801104000`，且 history 中缺失后来合入的 `V20260730090000`，因此默认
Flyway 顺序校验会失败。该分叉由现有 `DatabaseMigrationCompatibilityCustomizer` 在校验前精确识别并隐藏主
目录中无法再顺序执行的旧候选。早期 history 加载
`db/migration-compat/lobehub-missing/V20260802173416__backfill_lobehub_model_gateway.sql`；如果 release
`V20260803133000` 已执行而早期补偿尚未执行，则只加载更高版本
`db/migration-compat/lobehub-missing-after-rollout/V20260803141754__backfill_lobehub_model_gateway_after_rollout.sql`；
早期补偿已执行的库继续解析其原始资源。两条补偿都幂等创建同样三张表和四个生产必需参数；正常顺序库和
空库不加载 compatibility location，仍执行原始 `V20260730090000`。真实 PostgreSQL 集成测试会先按
`0352efa...` 的完整主 migration 上界 `V20260801104000`（排除当时尚不存在的 LobeHub migration）构造现网
history，再验证默认 `outOfOrder=false` 升级为 `V20260802173416`，随后执行 `V20260803133000`。

企业现网当前实际发布基线已升级为
`cec4ccf13769d9084c7d02efc158b021afe23c23`。正常 PostgreSQL history 因此应已包含成功的
`20260802173416`、`20260803133000` 与 `20260804123000`，不应倒序补写 `20260730090000`，也不应包含
仅用于另一套已知分叉的 `20260803141754`，部署前也不应已有 `20260805132000`。XXL MySQL 应为 V1-V8
全部成功且没有 V9，个人工作区搬迁任务最终 Cron 已为 `0 0/30 * * * ? *`；第一台新版启动后 PostgreSQL
只新增 `20260805132000`，XXL MySQL 只新增 V9 并注册每天北京时间 02:00 的闲置用户进程关闭任务。上述
`0352efa...` 测试继续作为历史兼容回归，不再代表
本轮现场的直接部署基线。任何目标库与该路径不一致时都必须停止并按完整 history 制定显式兼容方案，不能用
提交号替代现场核对。

主 migration、早期补偿和 release 后补偿分别锁定 SHA-256
`0f16f1b2f3108e60580cfeb00102e10ac21e20220be255fae77bad9871f0bcb7`、
`4f773e35e55380592f094c03cc5a66fb69b7dee4a2799e1c9ab5d0b9d8f1634a` 和
`b73b06fb14f407979646df32a8342603ab957c2f4812a4013ab9635cdfdcce64`。所有路径保持
`outOfOrder=false`，禁止 `repair` 或手工修改 history。

关系型运行 SQL 全部位于 `InternalModelProviderModelMapper.xml` 和 `ModelGatewayUsageDailyMapper.xml`；能力探测
使用 PostgreSQL `ON CONFLICT` 覆盖最近结果，每日聚合使用单条 upsert 增量，避免 JVM 先读后写造成并发丢失。
`ai_model_configs` 保持历史兼容，不是 LobeHub 目录来源。

同一 migration 只写入四个生产必需且默认禁用/不可用的公共参数：

- `LOBEHUB_ENABLED=false`
- `LOBEHUB_BASE_URL=http://127.0.0.1:3210`
- `LOBEHUB_SSO_EMAIL_DOMAIN=disabled.invalid`
- `LOBEHUB_INITIAL_OWNER_UNIFIED_AUTH_ID=NOT_CONFIGURED`

HMAC、委托加密密钥、数据库密码和对象存储密钥不进入数据库 migration。票据、nonce 和模型委托是 Redis
短期状态，也不创建关系型明细表。

持久化测试先在 H2 PostgreSQL mode 覆盖完整 Flyway、目录替换、探测结果和聚合；
`MyBatisModelGatewayRepositoryPostgresqlIntegrationTest` 使用 PostgreSQL 16 Testcontainers 模拟既有
`V20260728210000` 基线升级到 HEAD，并以并发增量验证 upsert。没有可用 Docker 时该测试会显式 skip，不能把
skip 当作正式发布验收。`RedisLobehubSsoStoreIntegrationTest` 另用真实 Redis 5.0.14 并发消费同一 ticket，
验证只成功一次、nonce 防重放、grant 轮换/撤销、TTL 和全部 key 前缀；它不替代 fork 的 `lobehub:app:*`
ACL/pubsub 全路径测试。正式合并/企业打包前仍必须读取每个目标环境的 `flyway_schema_history`，必要时只对
尚未在任何共享库执行的候选版本重新编号，并完成“真实已部署基线 → 当前 HEAD”的 PostgreSQL 升级。

LobeHub fork 使用独立 ParadeDB/PostgreSQL 17、独立账号、卷和自身 migration；平台 Flyway datasource 永远
不得访问该库。详细安装和回滚见 `docs/deployment/lobehub-offline.md`。

## V20260628100000 通用参数修改日志表

`backend/test-agent-persistence/src/main/resources/db/migration/V20260628100000__add_common_parameter_change_logs.sql` 创建通用参数修改日志表，用于记录每次参数值修改的审计信息：

| 字段 | 说明 |
|---|---|
| `id` | 数据库自增 surrogate PK，不对 API 暴露。 |
| `log_id` | 日志业务 ID，使用 `log_` 前缀。 |
| `parameter_id` | 关联参数业务 ID，外键引用 `common_parameters.parameter_id`。 |
| `old_value` | 修改前的参数值，可为空。 |
| `new_value` | 修改后的参数值。 |
| `changed_by_user_id` | 修改用户 ID，可为空（兼容 static token 场景）。 |
| `changed_by_username` | 修改用户名，可为空。 |
| `trace_id` | 链路 traceId。 |
| `created_at` | 修改时间。 |

约束和索引：

- `fk_common_parameter_change_logs_parameter` 外键保证日志关联的参数存在。
- `idx_common_parameter_change_logs_parameter` 支撑按参数查询修改历史并按时间倒序排列。

兼容策略：

- 新增表，不影响现有 `common_parameters` 数据。
- 修改参数值时自动写入日志，无需人工干预。
- 日志表只追加，不提供删除接口，满足审计要求。

## V20260628231000 运营分析反馈与汇总表

`backend/test-agent-persistence/src/main/resources/db/migration/V20260628231000__create_analytics_feedback_and_rollups.sql` 为 AI 回复反馈和运营分析 rollup 增加以下结构。

### runs 归因扩展

| 字段 | 说明 |
|---|---|
| `agent_id` | Run 使用的业务 Agent 标识快照，用于按 agent 过滤和排行。 |
| `model_id` | Run 使用的模型标识快照，用于按 model 过滤和排行。 |

新增索引覆盖 Run 创建/更新时间窗、状态、用户、agent/model 维度；运营分析不统计、不展示、不导出 `cost_usd`。

### ai_message_feedbacks

保存当前登录用户对自己归属的 `ASSISTANT` 消息的满意度反馈。

| 字段 | 说明 |
|---|---|
| `feedback_id` | 反馈业务 ID，`fb_` 前缀。 |
| `user_id` / `session_id` / `run_id` / `message_id` | 反馈用户、会话、Run 和消息归属；`run_id` 可空兼容历史消息。 |
| `rating` | `POSITIVE` 或 `NEGATIVE`。 |
| `reason_code` | 负反馈原因：`WRONG_ANSWER`、`NOT_HELPFUL`、`DID_NOT_FOLLOW_INSTRUCTION`、`CODE_QUALITY_LOW`、`TEST_RESULT_BAD`、`TOO_SLOW`、`TOO_VERBOSE`、`TOO_SHORT`、`OTHER`。 |
| `comment` | 用户补充说明，最多 300 字。 |
| `organization` / `rd_department` / `department` | 提交反馈时的组织快照，用于历史归因。 |
| `trace_id` / `created_at` / `updated_at` | 审计字段。 |

约束和索引：

- `(user_id, message_id)` 唯一，表示同一用户对同一消息只能有一条反馈，后续提交为更新。
- 外键引用 `users`、`sessions`、`runs`、`session_messages`。
- 时间、组织时间和 Run 维度索引用于反馈明细和负反馈分布查询。

## V20260715213000 AI 反馈迁移为 Run 口径

`V20260715213000__migrate_ai_feedback_to_run_scope.sql` 将可关联的历史消息反馈回填 `run_id`，同一用户同一 Run 的重复记录只保留最后更新的一条；`message_id` 改为可空的历史来源字段，并新增 `(user_id, run_id)` 唯一约束及 `run_id/message_id` 至少存在一个的检查约束。新反馈以 Run 为业务主键且 `message_id=null`；旧消息记录与 `(user_id, message_id)` 约束继续保留兼容。运营满意度统计以 Run 反馈事实计数，明细中的 `messageId` 允许为空。

### analytics_user_activity_hourly / analytics_user_activity_daily

运营分析 API 只读 hourly/daily rollup 表，不在请求链路扫描原始事实宽表。

| 主要字段 | 说明 |
|---|---|
| `bucket_start` / `activity_date` | 小时或日期桶。 |
| `user_id`、`username`、`organization`、`rd_department`、`department` | 用户和组织快照。 |
| `workspace_id`、`agent_id`、`model_id` | 过滤和排行维度。 |
| `login_count`、`session_count`、`active_session_count`、`empty_session_count`、`continuous_session_count` | 用户规模、会话和连续对话口径。 |
| `user_message_count`、`assistant_message_count`、`run_count`、`valid_interaction_count` | 使用强度口径。 |
| `succeeded_run_count`、`failed_run_count`、`cancelled_run_count`、`active_termination_count` | Run 结果口径。 |
| `positive_feedback_count`、`negative_feedback_count` | 满意度口径。 |
| `diff_proposed_count`、`diff_accepted_count`、`diff_rejected_count` | Diff 采纳口径。 |
| `tokens_input`、`tokens_output`、`tokens_reasoning`、`tokens_total` | token 使用强度，不含 cache 和费用字段。 |
| `duration_total_ms`、`duration_run_count` | 平均耗时计算来源。 |
| `first_activity_at`、`last_activity_at`、`updated_at` | 活动和刷新时间。 |

主键分别为 `(bucket_start, user_id, workspace_id, agent_id, model_id)` 和 `(activity_date, user_id, workspace_id, agent_id, model_id)`；组织+时间索引用于看板筛选。

### analytics_run_duration_histogram_hourly

Run 耗时小时直方图，字段包括 `bucket_start`、组织维度、`workspace_id`、`agent_id`、`model_id`、`le_ms`、`run_count`。p95 通过直方图近似计算，不在查询时对原始 Run 明细排序。

### analytics_rollup_watermarks / analytics_rollup_job_runs / analytics_job_locks

- `analytics_rollup_watermarks` 保存 rollup 水位、最近生成时间、`FRESH|STALE|FAILED` 状态和消息；API 通过 `freshness` 返回最近成功数据状态。
- `analytics_rollup_job_runs` 是历史预留的 rollup 任务运行审计表；当前统一定时任务审计写入 `scheduled_task_runs`，本表不再作为运行记录来源。
- `analytics_job_locks` 暂时提供业务数据库互斥，保证新版本 `opencode-runtime.analytics-rollup` handler 与滚动部署期间旧版本 `@Scheduled` 实例不并发刷新同一窗口；scheduler 框架自身的多实例互斥仍只使用 Redis 锁。

兼容策略：

- 新增表和可空字段，不破坏历史数据；历史 Run 的 `agent_id/model_id` 为空时 rollup 使用 `__none__` 维度。
- 会话创建人、Run 触发人、用户消息发送人由业务层逐步补齐；历史空值按 session 创建人或 `__unknown__` 兜底。
- 主链路只写事实表，统计刷新由后台定时任务执行；失败时保留最近成功 rollup 并标记 freshness。

## V20260626210000 数据库表和字段中文注释

`backend/test-agent-persistence/src/main/resources/db/migration/V20260626210000__add_chinese_comments_for_all_tables.sql` 为项目中所有数据库表和字段添加中文注释：

### 添加注释的表

| 表 | 说明 |
|---|---|
| `workspaces` | 平台工作区表，包含业务ID、名称、根路径、服务器归属、状态等信息 |
| `sessions` | 智能体会话表，关联workspace，包含标题、状态、来源等信息 |
| `runs` | 运行记录表，关联session/workspace，记录Run状态、token消耗等信息 |
| `run_events` | `LEGACY_FULL` RunEvent 事件流表，append-only，按 `(run_id, seq)` 唯一并支持增量回放；新模式不写 |
| `execution_nodes` | opencode执行节点表，包含baseUrl、健康状态、运行容量、权重、心跳和能力标签 |
| `routing_decisions` | Run到ExecutionNode的路由决策审计记录表 |
| `session_messages` | 会话消息表，记录用户与助手的对话内容 |
| `agent_session_bindings` | 通用agent远端会话绑定表 |
| `users` | 平台用户表，包含统一认证号、用户名、BCrypt密码哈希、所属机构/研发部/部门 |
| `user_login_logs` | 用户登录日志表，记录登录时间、IP、User-Agent和结果 |
| `dictionaries` | 通用字典表，存储应用角色等字典数据 |
| `user_roles` | 用户角色对照关系表 |
| `applications` | 应用定义表，由外部系统同步 |
| `application_members` | 应用成员关系表 |
| `code_repositories` | 代码库配置表 |
| `application_repository_links` | 应用与代码库多对多关联表 |
| `application_workspaces` | 应用级工作空间配置表 |
| `user_ssh_keys` | 用户个人SSH私钥配置表 |
| `application_workspace_versions` | 应用工作空间模板的版本实例表 |
| `personal_workspaces` | 用户基于应用版本工作区派生的git worktree表 |
| `user_global_workspace_preferences` | 用户全局最近使用的托管运行态Workspace表 |
| `user_application_workspace_preferences` | 用户在某应用下最近使用的托管运行态Workspace表 |
| `workspace_sync_records` | 个人工作区与应用版本工作区同步审计表 |
| `user_workspace_branch_preferences` | 用户工作区分支偏好表 |
| `ai_model_configs` | AI模型配置表 |
| `linux_servers` | 后端Linux服务器节点表 |
| `backend_java_processes` | 后端Java进程实例表 |
| `opencode_containers` | opencode容器表 |
| `opencode_container_managers` | opencode容器管理进程表 |
| `opencode_manager_backend_connections` | 管理进程到后端Java进程的控制面连接状态表 |
| `opencode_server_processes` | 用户专属opencode server进程表 |
| `user_opencode_process_bindings` | 用户到agent/opencode进程的当前绑定表 |
| `scheduled_tasks` | 定时任务定义表 |
| `scheduled_task_plans` | 用户级Cron计划预留表 |
| `scheduled_task_runs` | 定时任务运行记录表 |
| `night_execution_tasks` | 夜间异步执行任务主表 |
| `night_execution_session_locks` | 待执行夜间任务会话写锁表 |
| `night_execution_slot_reservations` | 夜间15分钟启动时段容量占位表 |
| `toolbox_tool_click_events` | 工具盒子永久点击明细表 |
| `toolbox_tool_click_totals` | 工具盒子累计点击投影表 |
| `toolbox_tool_user_click_states` | 用户与工具30秒计数窗口状态表 |

## V20260723145200 应用工作空间启用状态

`backend/test-agent-persistence/src/main/resources/db/migration/V20260723145200__add_application_workspace_enabled.sql` 为 `application_workspaces` 增加非空布尔字段 `enabled`，默认值为 `true`。存量记录和新建记录因此默认继续出现在工作空间切换入口；设置页显式停用后只影响切换模板列表，不删除关联版本、个人工作区、运行态工作区或最近使用记录。

## V20260727203500 / V20260728160800 工具盒子点击跟踪兼容

`backend/test-agent-persistence/src/main/resources/db/migration/V20260728160800__create_toolbox_click_tracking.sql` 新增三个生产业务表，不写工具目录、测试点击或演示数据。该版本已在企业库执行，Flyway checksum 为 `-1966404877`，原始文件 SHA-256 为 `777a96f12342b0cc049748a6f910e56214a4c8ca52488e1429edb1409adb51f2`；主 migration 必须永久保持这组原始字节。不得为兼容其它环境把它改成 `IF NOT EXISTS`，因为这会将 checksum 改成 `-74327385` 并直接阻断企业启动。

已知历史共有四类：空库/企业基线尚未执行工具盒子迁移；企业库已执行 `V20260728160800` 且 checksum 为 `-1966404877`；早期测试库已执行 `V20260727203500` 但未执行当前版本；以及曾执行过 checksum `-74327385` 幂等变体的过渡库。旧 `V20260727203500` 原文和误发幂等变体都只能作为原始字节不变的隔离 compatibility 资源保留；`DatabaseMigrationCompatibilityCustomizer` 在 Spring Boot 唯一 Flyway Bean 校验前读取已应用版本和 checksum，只为命中的历史选择对应资源。未知 checksum 不降级、不自动修复，继续由 Flyway 失败关闭。

四条路径都必须使用 Flyway 默认顺序模式，不执行 `repair`，不手工修改 `flyway_schema_history`。`DatabaseMigrationCompatibilityCustomizerPostgresqlIntegrationTest` 必须使用真实 Spring Boot Flyway 初始化和 PostgreSQL 覆盖上述四类历史；`FlywayMigrationNamingTest` 必须同时锁定企业主 migration、旧版本兼容资源和误发幂等变体的 SHA-256。企业打包后还要从 `test-agent-app.jar` 解出主 migration，确认 SHA-256 仍为 `777a96f12342b0cc049748a6f910e56214a4c8ca52488e1429edb1409adb51f2`。

### `toolbox_tool_click_events`

永久 append-only 点击明细，`event_id` 是客户端一次打开动作生成的最长 128 字符全局幂等键。表保存稳定 `tool_id`、`IT_TOOLS/OMNI_TOOLS` 来源、可空用户、traceId、是否计入累计和服务端点击时间。用户删除时外键 `ON DELETE SET NULL`，历史事件继续用于总量审计，但不再关联身份。

索引 `(tool_id, clicked_at desc)` 支撑单工具容量排查，`(user_id, clicked_at desc)` 支撑用户删除/审计定位。首版不提供明细查询 API，也不建立清理任务。

### `toolbox_tool_click_totals`

每个稳定工具最多一行累计投影，保存非负 `click_count`、最后有效计数时间和更新时间。零点击工具不预写行；目录查询将缺失投影映射为 0。热门查询不直接扫描事件明细，业务服务只批量读取当前目录工具的累计投影，再按累计数、最后计数时间和目录顺序排序。

### `toolbox_tool_user_click_states`

以 `(tool_id, user_id)` 为主键保存该用户对该工具最后一次有效计数时间。MyBatis XML 使用 PostgreSQL `INSERT ... ON CONFLICT ... DO UPDATE ... WHERE` 原子竞争 30 秒窗口；只有影响一行的请求递增累计并标记事件 counted。用户删除时 `ON DELETE CASCADE`，不保留可识别的节流状态。

### 事务、兼容与监控

- `eventId` 幂等插入、窗口竞争、累计递增和事件 counted 标记在同一个 Spring 事务中；重复事件直接返回当前累计。
- 所有运行 SQL 都在 `ToolboxClickMapper.xml`，没有新增 JDBC SQL。PostgreSQL 是生产方言，H2 `MERGE` 只用于 PostgreSQL 模式集成测试。
- 三张表和两个 API 都是向后兼容新增；旧 Java/前端不会访问。回滚应用或工具镜像时不回退 migration，累计数据保留。
- `toolbox_tool_click_events` 永久保留且会持续增长。数据库监控必须采集表行数、表/索引字节数、日增量和剩余容量；达到容量阈值前需另行评审归档/保留策略，不能在首版临时删除明细。
- PostgreSQL Testcontainers 验证完整 Flyway、MyBatis 方言、并发窗口单赢家和用户删除匿名化；H2 集成测试验证首次点击、窗口内重复、窗口到期和 `eventId` 幂等。

## V20260728103000 应用源码快照持久化

`backend/test-agent-persistence/src/main/resources/db/migration/V20260728103000__create_app_source_snapshot_tables.sql` 新增七类生产状态表，不写测试、演示或环境专属业务数据：

- `app_source_repository_slots`：每个 repositoryId 唯一，保存 active/pending generation、下一代次、最近操作和 `lock_version`；分配流程使用 `SELECT FOR UPDATE` 或显式版本 CAS，旧执行者不能覆盖新槽位。
- `app_source_snapshots`：以 `(repository_id, generation)` 为主键，冻结仓库英文名、`PERSONAL/TEAM` 用途、owner、分支、目标提交和 JSONB 结构化选择路径；领域对象和数据库都强制 `expires_at = accepted_at + 1..72` 整小时，默认 48 小时，`index_sha256` 非空时必须为 64 位十六进制。每个仓库最多一个 `ACTIVE` 快照。
- `app_source_replicas`：以 repository/generation/server 唯一，保存可空运行态 Workspace、状态、attempt、退避和绝对租约；初始化使用 insert-if-absent，不覆盖已进入 `RUNNING/READY` 的行，后续写回同时校验 generation、owner、未过期租约和合法状态流转。
- `app_source_operations` / `app_source_operation_steps`：保存用户操作、幂等摘要和全局/服务器步骤。部分唯一索引分别固定 `(operation_id, step_code)` 的 GLOBAL 步骤和 `(operation_id, linux_server_id, step_code)` 的 SERVER 步骤，避免 NULL 破坏全局幂等；步骤 upsert 只允许从 `PENDING/RUNNING` 向合法状态推进，终态行不再被更新。
- `app_source_cleanup_tasks`：以 repository/generation/server 唯一，保存绝对 `delete_at`、认领租约和安全错误。到 operation 和 snapshot 的两个外键均为 `DEFERRABLE INITIALLY DEFERRED`，业务事务可以先插 cleanup，再补齐 operation/snapshot，提交时统一校验；其它服务器外键仍即时校验。
- `app_source_recent_selections`：仅以 userId 唯一，保存 app/repository/generation，不保存 workspaceId；打开时按用户当前 opencode 进程服务器解析 READY 副本。

同一 migration 初始化 `platform=all`、`editable=false` 的 `OPENCODE_APP_SOURCE_ROOT`，数据库值精确为 `${SYS_DATA_ROOT_DIR}/agent-opencode/workspace/appsource/`。SQL 通过 `'$' || '{SYS_DATA_ROOT_DIR}/...'` 拼接规避 Flyway 占位符替换，运行态继续由通用参数解析器按当前或目标平台展开。

兼容策略：所有表和参数均为向后兼容新增，旧 Java 不访问这些表；回滚应用版本时保留 migration 和历史 cleanup。H2 使用等价时间函数验证可移植 mapper 行为；JSONB、整小时过期/十六进制摘要约束、步骤终态保护、部分唯一索引、完整 Flyway 链及 cleanup 第一写的延迟外键由 PostgreSQL 16 Testcontainers 原样验证。

运行态只通过 `AppSourceMapper.xml` 访问这些关系表，不新增 JDBC SQL。物化/重试事务先对 `code_repositories` 执行 `SELECT FOR UPDATE`；同 operationId 的物化重放先读取 existing operation 并严格校验不可变身份，再读取可变 slot，返回目标从首请求 SERVER steps/冻结 generation replicas 恢复。全新物化的 cleanup task 仍是第一条持久化写；副本完成和 cleanup 完成分别用 generation、服务器、owner、绝对租约 fencing。每个服务器登记固定 13 步；步骤开始/完成、失败/跳过和新 attempt 全量重置都先对精确 RUNNING 副本活租约执行行锁校验，旧 owner 不能覆盖新 attempt。dispatcher 按服务器扫描 `PENDING`、租约已到期的 `RUNNING` 和仍有旧 `RETRY_QUEUED` 的失败副本。stranded SQL 对 DOWNLOAD/UPDATE 保留全 replica 已 `READY/FAILED` 的历史恢复条件；RETRY 则要求存在 SERVER steps 且全部终态、没有 `PENDING/RUNNING` 目标，避免旧 FAILED replica 让离线 retry 目标被提前终结。副本结果 CAS 后，success/failure/recovery 使用一致锁序取得 repository slot 行锁，再重读 operation、步骤和全体副本并串行写 snapshot/slot/operation 终态；直接 recovery 也重复执行 retry 步骤门禁。全失败清 pending 并保留旧 active/expiry，混合终态为 `PARTIAL_FAILED`，全 READY 为 `SUCCEEDED`。首台 READY 在同一事务内先把旧 ACTIVE 改为 EXPIRED，再激活 pending，以满足单 ACTIVE 唯一约束。Runtime Workspace 反查直接按受外键保护的 `runtime_workspace_id` 查询；旧 generation 提升为立即清理时，`makeCleanupDueNow` 只提前 `PENDING/RETRY_WAIT` 的绝对执行时间，不覆盖正在执行或终态任务；清理成功把 replica 单向推进到 `CLEANED`，同步归档 Workspace，并且只在 slot 仍指向该 generation 时清除 active。H2 集成测试覆盖旧步骤领取、attempt reset/upsert、类型感知 stranded 扫描和恢复索引；PostgreSQL 16 Testcontainers 原样执行生产 SQL，并用三副本 barrier 验证普通终态收敛、retry 离线门禁及同 operationId 并发单写。

## V20260728210000 应用源码在途恢复索引

`V20260728210000__index_in_flight_app_source_operations.sql` 只新增 `app_source_operations(status, accepted_at, operation_id)` 复合索引，不写入或改写业务数据。`status` 前导列服务 dispatcher 的 `PENDING/RUNNING` stranded 有界扫描，后两列服务稳定顺序和 limit；该形式同时兼容 PostgreSQL 与 H2 验证环境。回滚旧 Java 时可保留该索引，不影响旧查询和 API wire。

## V20260731115520 / V20260731123600 调整应用源码保留期

`V20260731115520__extend_app_source_retention.sql` 是已经在开发数据库执行的 365 天扩容版本，文件名、注释和 SQL 字节保持冻结；`V20260731123600__cap_app_source_retention_at_one_week.sql` 再把最终上限收紧为 168 小时（7 天）。两者都只替换 `app_source_snapshots` 到期约束，不修改已有快照或测试数据；合并前必须确认 365 天迁移与一周收紧迁移连续执行，禁止删除、改写前一 migration 或执行 Flyway `repair`。下限始终为相对 `accepted_at` 1 个整小时，且只接受整小时。

运行态续期先锁代码库与 slot，再锁同 generation 的全部 cleanup 行；仅当快照仍为当前 ACTIVE、未过期且 cleanup 全部为 `PENDING/RETRY_WAIT` 时，使用 MyBatis XML CAS 同步更新 `expires_at/index_sha256` 与 `delete_at/next_retry_at`。任一行数不一致会使整个事务回滚。回滚到旧 Java 时，已有超过 72 小时的快照无法通过旧领域校验，因此应用版本不能单独回滚而保留长保留期数据；需先把相关快照恢复到旧边界或继续使用新 Java。

### 字段注释原则

- 业务ID字段均标注格式，如：`wks_xxx`、`ses_xxx`、`run_xxx`、`msg_xxx`
- 状态、来源类型等枚举字段标注可选值，如：`ACTIVE/ARCHIVED`、`MANUAL/SCHEDULED_TASK`
- JSON字段标注结构样例，如：`{"tools": ["git", "docker"]}`、`["text","image"]`
- 已有中文注释的表（`common_parameters`、`workspace_create_operations`、`agent_config_worktrees`、`agent_config_operations`、`application_workspace_version_replicas`）不再重复添加

## V20260711120000 旁路问答来源注释

`backend/test-agent-persistence/src/main/resources/db/migration/V20260711120000__document_side_question_run_source.sql` 只把 `sessions`、`runs`、`session_messages` 三个 `source_type` 字段的允许值注释扩展为 `MANUAL/SCHEDULED_TASK/SIDE_QUESTION`。该迁移不改变表结构、约束或索引，也不写入任何业务、测试或演示数据。

## V20260717173000 公共 Agent 配置发布排空

`backend/test-agent-persistence/src/main/resources/db/migration/V20260717173000__create_public_agent_config_rollouts.sql` 新增三张生产运行状态表：

- `public_agent_config_rollouts`：保存公共分支、commit、`DRAINING/COMPLETED/FAILED` 状态和 traceId；部分唯一索引保证集群同一时刻只有一个排空任务。`FAILED` 仅是历史结构兼容值，新发布在远端已经更新并建立 rollout 后不再写入该状态或提前开闸。
- `public_agent_config_rollout_servers`：保存本次需要同步的 `linuxServerId` 及 `PENDING/SYNCED` 状态；服务器在共享 Git 副本更新并登记进程后才写 `SYNCED`。
- `public_agent_config_rollout_targets`：保存 manager 心跳中的存量 opencode `linuxServerId/containerId/port/baseUrl`、`PROCESSING/RETRY_WAIT/DISPOSED`、重试次数、下次重试时间、处理租约和最后错误。认领 SQL 强制 `linux_server_id=当前 Java 所在服务器`，再以 `FOR UPDATE SKIP LOCKED` 允许同服务器多 Java worker 安全认领；发布服务器可以统一落表，但不能跨服务器代查 Session 或 dispose，过期租约由目标所属服务器恢复。

该迁移只创建运行必需结构，不写测试或演示数据。Git 同步补偿、目标轮询和 Session 重试间隔可通过 Spring 属性 `test-agent.public-agent-config.rollout.sync-retry-delay-ms`、`poll-delay-ms`、`retry-delay-ms` 调整，默认均为 5000ms；Redis 广播丢失或 Java 重启后会从 PENDING 服务器记录继续同步，目标会持续重试直至 dispose 成功，不设最大次数。

## V20260717200000 公共配置发布排空加固

`backend/test-agent-persistence/src/main/resources/db/migration/V20260717200000__harden_public_agent_config_rollout.sql` 为已发布的排空任务补充以下持久化边界：

- `public_agent_config_rollouts.initiated_by_user_id` 保存发起发布的超级管理员，目标服务器据此读取数据库内该用户的 SSH key；私钥不写入 rollout 或广播事件。
- `public_agent_config_rollout_targets.user_id` 在取得各服务器 manager 进程清单时快照用户归属，历史数据按 `linuxServerId + containerId + port` 从进程表回填。门禁直接读取这份发布窗口快照，不再依赖之后可能变化或删除的实时进程行；同一用户的目标全部 `DISPOSED` 后立即开闸。
- `public_agent_config_rollout_targets.lease_token` 为每次认领生成唯一 fencing token；只有仍持有当前 token 的 worker 可以写入 `RETRY_WAIT/DISPOSED`，防止超时旧 worker 覆盖新一轮处理结果。
- 新增 `(rollout_id, user_id, status)` 索引，支持用户级门禁查询。

该迁移只包含兼容性回填、列、索引和注释，不写测试或演示数据。历史 rollout 的发起人允许为空；新任务始终写入发起人。服务器同步必须取得本机 manager 实时清单后才可从 `PENDING` 转为 `SYNCED`，因此发布瞬间离线的持久化服务器会保持全员门禁并由补偿任务持续重试。

## V20260717213000 公共配置发布完整状态机

`backend/test-agent-persistence/src/main/resources/db/migration/V20260717213000__complete_public_agent_config_rollout_state_machine.sql` 补齐发布和排空的并发边界：

- rollout 增加发起服务器和发布前共享副本提交 `previous_commit_hash`，纯拉取时 `commit_hash` 在 `PREPARING` 可空；共享运行副本直接提交场景会先写 `PENDING_LOCAL_COMMIT` 占位，最终本地 commit 产生后在 push 前回写，避免进程崩溃恢复时把旧远端 HEAD 误认成本次发布结果。占位值尚未回写说明 push 必然还未发起，超过 5 分钟恢复窗口后可先恢复共享副本再转 `ABORTED`；实际 commit 已回写时，远端未包含目标提交也只有先完成同样恢复才允许开闸。唯一活跃索引同时覆盖 `PREPARING/DRAINING`，保证远端 push 或共享副本修改前已经建立全员消息门禁。
- 新增 `public_agent_config_rollout_memberships`，把实际发布成员与 `linux_servers` 历史拓扑分离。新版 Java 自动登记 `ACTIVE`，临时离线成员继续参与；永久下线服务器由超级管理员显式置为 `DECOMMISSIONED`。
- server 行增加重试、下次执行、租约和 fencing token；同一 Linux 服务器上的多个 Java 只有数据库认领者可以操作本机共享 Git 副本和确认 manager 快照。
- target 增加 `process_pid/process_started_at`；worker 每次远端调用前后续租，dispose 前按 PID 和启动时间复核 manager 当前进程，避免端口复用后误 dispose 新实例。
- 无法按服务器、容器、端口、PID 和启动时间映射用户的 target 保持 `user_id=null`，该目标完成前全员门禁，不允许因历史进程表缺失而提前放行。

服务器退役会把当前 rollout 中该服务器置为 `DECOMMISSIONED`、残留 target 置为 `ABANDONED`，仅用于运维明确确认该节点永久离开集群的场景。迁移不自动导入历史 `linux_servers`，也不写测试、演示或个人数据。

## V20260717214000 公共配置发布进程身份兼容回填

`backend/test-agent-persistence/src/main/resources/db/migration/V20260717214000__backfill_public_agent_rollout_process_identity.sql` 为升级时仍处于排空状态的存量 target，按 `linuxServerId + containerId + port` 从平台进程表回填 PID 和启动时间。旧进程表使用无时区 timestamp，迁移按当前数据库会话时区转换成绝对时间，与既有 JDBC 读写语义保持一致；无法可靠回填的行继续保留空身份，worker 会持久化重试并保持消息门禁，不会把端口复用或未知进程误判成已 dispose。

## V20260805132000 超级管理员问题排查授权与审计

`V20260805132000__create_support_access_audit.sql` 只创建生产必需结构，不写入测试、演示、默认管理员或部署侧暗号：

该 migration 源码 SHA-256 固定为
`54cea9a84948f8e4cee14d630772b8ee0668c2a7e5fc897ede5e792a15edd761`；内层封包、双后台外层封装和目标机安装后
复验都必须从 `test-agent-persistence` JAR 读取该资源并得到相同摘要。

- `support_access_grants`：保存 `sag_` 授权 ID、actor 用户/用户名快照、`incident_id` 排查单号、原因、平台登录会话 SHA-256 摘要、签发/到期/撤销时间、撤销原因和 traceId。表不保存平台 Token 或 `sat_` 授权 Token 明文；actor 删除时外键 `ON DELETE SET NULL`，用户名与排查单号快照继续保留。
- `support_access_audit_events`：append-only 保存 `sae_` 事件 ID、可空 grant/actor/target 外键、身份与排查单号快照、动作、资源类型/业务 ID、文件路径 SHA-256、结果/错误码、traceId、IP、User-Agent SHA-256 和发生时间。消息正文、文件正文、文件路径明文和 Token 均不得入表；用户或授权删除时外键置空，不级联删除审计。
- actor、target、grant、排查单号和保留期索引均以前述查询字段加时间倒序建立；授权表额外按会话摘要和到期时间索引。历史 actor/target 外键置空后，快照仍可按排查单号、动作和 traceId 检索。

运行 SQL 全部位于 `SupportAccessMapper.xml`；目标用户关联工作区查询位于 `UserWorkspaceQueryMapper.xml`，没有新增 JDBC SQL。当前工程没有权威工单数据源，排查单号由业务层生成，不查询历史授权，也不需要新增表或 Flyway migration。工作区范围只包含 ACTIVE 个人工作区或 ACTIVE 非旁路会话通过创建人、Run 触发人、消息发送人归因到的工作区；历史空白 workspace name 只在读模型中回退为 workspaceId，不修改存量数据。

Redis 使用 `test-agent:support-access:session:{sessionDigest}` 与 `test-agent:support-access:token:{grantTokenDigest}` 保存当前授权摘要和短期 payload；两类 key/value 都不含原始平台/授权 Token。Lua rotate 保证同一平台登录会话的新授权立即淘汰旧 token，revoke 只删除仍与当前摘要匹配的 session key。TTL 与授权绝对到期时间一致，Redis 不可用时不降级 JVM 内存或数据库明文 Token。

每日清理以当前 UTC 时间减 365 天为 cutoff，先删除更早的审计，再删除已到期且不再被审计引用的授权；一年内审计不会因用户删除或授权撤销提前消失。应用回滚时保留 migration 和历史审计，新表对旧 Java 为向后兼容新增。发布前仍须按本文件 migration 规则，用目标环境真实 PostgreSQL 历史执行基线到当前 HEAD，禁止 `repair`、`outOfOrder` 或改写已经执行的 SQL。
