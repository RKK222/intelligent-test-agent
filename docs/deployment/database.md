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
- `sessions.opencode_session_id` 和 `sessions.opencode_execution_node_id` 是后端内部字段，不进入 API DTO；旧 session 两列为空时由首次 Run 懒创建远端 opencode session。
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
