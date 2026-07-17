# 数据库 Migration 说明

本文档记录当前数据库结构和兼容策略。任何新增或修改 migration 都必须同步更新本文件。

## 数据访问规范

- 关系型数据库连接池继续统一使用 Druid，migration 继续由 Flyway 管理。
- 新增或修改关系型数据库 SQL 必须通过 `test-agent-persistence` 的 MyBatis XML mapper 实现；mapper 接口只声明方法，禁止写注解 SQL。
- 存量 `Jdbc*Repository` 仅保留迁移窗口，后续触及其 SQL 时迁移到 MyBatis XML。当前通用参数 `CommonParameterRepository`、Agent 配置 `AgentConfigRepository` 与 `RunEventRepository` 已迁移到 MyBatis XML。
- Flyway migration 只能承载表结构变更、历史数据兼容迁移和生产必需的基础字典/系统参数；禁止通过 Flyway 写入测试、演示、个人开发或环境专属数据（例如样例应用/工作区、默认开发账号、默认本地进程绑定）。此类数据必须放在测试 fixture、`test-agent-test-support`、mock 数据、显式本地开发脚本或人工初始化流程中。历史已存在的开发种子迁移仅为兼容已落库环境保留，后续不得新增同类迁移。

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

通用参数值支持变量引用 `${englishName}`，在应用层读取时展开；`${NAME}` 先按通用参数引用解析，未命中时再读取 Java 后端进程环境变量，`$NAME` 直接读取环境变量；路径值开头的 `$HOME` 和 `~/` 会展开为当前用户主目录，**不涉及表结构变更**（`parameter_value` 已为 `text`）。通用参数运行态通过 `RepositoryCommonParameterValues` 直接读取数据库，不写入 JVM 内存缓存或 Redis 参数快照。

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
| `opencode_server_processes` | 用户专属 opencode server 进程，记录用户、Linux 服务器、容器、主机直通端口、PID、`base_url`、启动路径和健康状态。 |
| `user_opencode_process_bindings` | 用户到 opencode 进程的当前绑定，按 `(user_id, agent_id)` 唯一，首期 `agent_id='opencode'`。 |
| `opencode_process_start_operations` | 当前用户 opencode 进程初始化进度快照，供前端按 `operationId` 轮询展示启动步骤和失败原因。 |

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

`backend/test-agent-persistence/src/main/resources/db/migration/V20260625184300__create_scheduler_framework_tables.sql` 创建通用定时任务框架表，并给会话、Run、消息增加来源预留字段。框架内置运行记录保留清理任务，其它具体业务任务仍由所属业务模块提供；不开放普通用户创建 Cron 计划 API。该版本使用 14 位时间戳，避免与既有 `V15__add_opencode_process_id_check_constraints.sql`、`V17__seed_local_opencode_machine_for_default_user.sql` 或其他并行分支的数字版本冲突。

| 表 | 说明 |
|---|---|
| `scheduled_tasks` | 代码注册任务定义，保存任务 key、名称、Cron、启停、锁 TTL、下次触发时间和注册状态。 |
| `scheduled_task_plans` | 用户级 Cron 计划预留表，包含 owner 用户、Cron、payload、启停和下次触发时间。 |
| `scheduled_task_runs` | 单次运行记录，统一记录 Cron、管理员手动触发和未来用户计划触发的状态、时间、实例、跳过原因、错误和结果。 |

关键字段：

- `scheduled_tasks.task_key` 唯一，作为代码注册、数据库定义、Redis 锁和运行记录关联键。
- `scheduled_tasks.lock_ttl_seconds` 保存 Redis 锁租约秒数，必须为正数。
- `scheduled_task_plans.plan_id`、`scheduled_task_runs.task_run_id` 是业务 ID，不暴露数据库自增 surrogate PK。
- `scheduled_task_runs.trigger_type` 当前支持 `CRON`、`MANUAL`、`USER_PLAN`；HTTP 首版只创建 `MANUAL`。
- `scheduled_task_runs.status` 当前支持 `PENDING`、`RUNNING`、`STOPPING`、`SUCCEEDED`、`FAILED`、`SKIPPED`、`MANUALLY_STOPPED`。
- `scheduled_task_runs.skip_reason` 保存同一 `taskKey` 已有未结束运行或 Redis 锁竞争失败时的跳过原因。
- `scheduled_task_runs.stop_requested_at`、`stop_requested_by_user_id`、`stop_reason` 记录超级管理员发起协作式停止的时间、操作者和原因。
- `V20260715000000__add_scheduler_run_retention_index.sql` 为 `scheduled_task_runs.ended_at` 创建 `idx_scheduled_task_runs_ended_at` 索引。`scheduler.run-retention-cleanup` 每天 UTC 00:00 删除 `ended_at` 早于当前时间 7 天且状态为 `SUCCEEDED`、`FAILED`、`SKIPPED`、`MANUALLY_STOPPED` 的记录；`PENDING`、`RUNNING`、`STOPPING` 始终保留。

新增来源字段：

| 表 | 字段 | 说明 |
|---|---|---|
| `sessions` | `source_type`、`source_ref_id`、`created_by_user_id` | 会话来源，默认 `MANUAL`；支持 `SCHEDULED_TASK` 和内部 `SIDE_QUESTION`。 |
| `runs` | `source_type`、`source_ref_id`、`triggered_by_user_id` | Run 来源，默认 `MANUAL`；宠物旁路 Run 使用 `SIDE_QUESTION`。 |
| `session_messages` | `source_type`、`source_ref_id`、`sender_user_id` | 消息来源，默认 `MANUAL`；允许值注释包含 `SIDE_QUESTION`。 |

兼容策略：

- 旧数据通过 `default 'MANUAL'` 保持兼容；新增用户字段均可空。
- `scheduled_task_plans` 只预留领域模型和 Repository，不开放 HTTP API，不会被普通用户直接创建。
- 分布式互斥由 Redis 锁保证，数据库表不作为锁 fallback；Redis 不可用时 scheduler 启用校验失败或运行失败，不降级为本机锁。
- `result_json`、`payload_json` 保存结构化 JSON 文本，禁止写入密钥、Token、完整 prompt 或其他敏感内容。

## V20260625192100 scheduler 停止字段与状态字典

`backend/test-agent-persistence/src/main/resources/db/migration/V20260625192100__extend_scheduler_management_stop_and_dicts.sql` 在 scheduler 框架表上扩展管理可视化所需字段和字典：

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

- `internal_model_providers(provider_id, name, base_url, enabled, sort_order, created_at, updated_at)` 保存内部供应商地址；`provider_id` 对应代理请求头 `X-Enterprise-Model-Provider` 的路由键（当前为 `qwen-prod` / `deepseek-prod`），不是 opencode 配置中的 `enterprise-qwen` / `enterprise-deepseek` provider key。
- `internal_model_proxy_settings(setting_id='default', enterprise_openai_auth_token, created_at, updated_at)` 保存全局 `ENTERPRISE_OPENAI_AUTH_TOKEN`，按需求明文保存，不回显到前端。
- Java 启动时把启用供应商和 token 加载到内存；管理端保存后发布 `internal-model-provider.refresh-requested` 广播，各 Java 从数据库重新加载内存快照。
- `V20260716143000__rename_internal_model_auth_token_column` Java migration 按第二列识别并重命名既有环境的历史鉴权列；新建数据库已使用目标列名时幂等跳过。两条因去机构标识而调整的历史 SQL migration 通过兼容注释保持原 Flyway checksum，升级不需要执行 `repair`。

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

## 后续 migration 版本规则

V18 及以前保留既有数字版本，已在本地或共享库执行过的 migration 禁止删除、重命名或改写。V18 之后新增 migration 必须使用 `VyyyyMMddHHmmss__description.sql`，时间戳按开发者创建迁移时的本地时间确定；多人并行开发时不得再抢占 `V19`、`V20` 这类顺序数字版本。提交前需运行持久化模块 migration 命名测试，确认版本唯一、历史已落库 migration 仍可解析且时间戳规则生效。

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
