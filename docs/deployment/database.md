# 数据库 Migration 说明

本文档记录当前数据库结构和兼容策略。任何新增或修改 migration 都必须同步更新本文件。

## V1 核心表

`backend/test-agent-persistence/src/main/resources/db/migration/V1__create_core_tables.sql` 创建以下表：

| 表 | 说明 |
|---|---|
| `workspaces` | 平台工作区，包含业务 ID、名称、根路径、状态、traceId、创建和更新时间。 |
| `sessions` | 智能体会话，关联 workspace，包含标题、状态、traceId、创建和更新时间。 |
| `runs` | 运行记录，关联 session/workspace，包含 Run 状态、traceId、创建和更新时间。 |
| `run_events` | RunEvent append-only 事件流，按 `(run_id, seq)` 唯一并支持增量回放。 |
| `execution_nodes` | opencode 执行节点，包含 baseUrl、健康状态、运行容量、权重、心跳和能力标签。 |
| `routing_decisions` | Run 到 ExecutionNode 的路由决策审计记录。 |

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

- `idx_sessions_active_pinned_updated(status, pinned, updated_at, id)` 支持全局 ACTIVE 会话搜索后的置顶优先排序。
- `idx_sessions_workspace_active_pinned_updated(workspace_id, status, pinned, updated_at, id)` 支持 workspace 维度会话列表排序。

## 兼容策略

- 所有表使用自增 surrogate PK；业务层只使用带前缀业务 ID。
- 新增字段优先允许空值或提供默认值，避免破坏旧数据。
- `agent_session_bindings` 是 agent 运行态绑定主数据源，按 `(session_id, agent_id)` 记录平台 session 到远端 session/node 的映射。
- `sessions.opencode_session_id` 和 `sessions.opencode_execution_node_id` 是后端内部兼容字段，不进入 API DTO；旧 session 两列为空时由首次 `opencode` Run 懒创建远端 session，非 opencode agent 不扩展这些列。
- `sessions.pinned` 进入 Session API DTO；软删除复用 `status=ARCHIVED`，不新增删除时间字段，旧数据默认 `ACTIVE` 且 `pinned=false`。
- `run_events.payload_json` 和 `execution_nodes.capabilities_json` 当前为 JSON 文本，便于 H2 和 PostgreSQL 共用测试；未来迁移到 JSONB 时必须先保持旧列读取兼容。
- `run_events.seq` 由持久化层按同一 run 分配，取消、Diff 动作和 opencode stream 并发追加时必须依赖 `(run_id, seq)` 唯一约束冲突后重试，保持事件流单调递增且不重复。
- `session_messages.content` 当前直接保存文本；后续如拆分富文本 parts，必须保留旧 content 读取兼容。
- 删除或重命名状态、事件类型、数据库字段必须拆分为读取兼容、数据迁移、清理三个阶段。

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
| `user_ssh_keys` | 用户个人 SSH 私钥配置，私钥密文、nonce、指纹和名称。 |

关键约束：

- `application_members(app_id, user_id)` 唯一；`deleted_at` 为空表示有效关系。
- `code_repositories.git_url` 唯一；不提供删除仓库配置的业务接口。
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
| `application_workspace_versions` | 应用工作空间模板的版本实例，记录版本、实际分支、物理仓库目录、opencode 工作目录和关联运行态 `workspaces.workspace_id`。 |
| `personal_workspaces` | 用户基于应用版本工作区派生的 git worktree，记录展示名称、私有分支、物理目录、base commit 和关联运行态 Workspace。 |
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
- `application_workspaces.branch` 继续保留作为模板创建和目录选择兼容字段；版本实际分支以 `application_workspace_versions.branch` 为准。
- 物理路径默认由业务配置 `test-agent.managed-workspace.root` / `TEST_AGENT_MANAGED_WORKSPACE_ROOT` 决定，数据库只记录最终路径，不负责创建或清理目录。

## V10 F-COSS 应用开发种子数据

`backend/test-agent-persistence/src/main/resources/db/migration/V10__seed_fcoss_application.sql` 在本地开发环境提供开箱即用的 F-COSS 应用数据，让工作台左下角的两级菜单（应用→工作空间→版本）首次进入就能看到内容：

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
