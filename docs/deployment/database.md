# 数据库 Migration 说明

本文档记录当前数据库结构和兼容策略。任何新增或修改 migration 都必须同步更新本文件。

## 数据访问规范

- 关系型数据库连接池继续统一使用 Druid，migration 继续由 Flyway 管理。
- 新增或修改关系型数据库 SQL 必须通过 `test-agent-persistence` 的 MyBatis XML mapper 实现；mapper 接口只声明方法，禁止写注解 SQL。
- 存量 `Jdbc*Repository` 仅保留迁移窗口，后续触及其 SQL 时迁移到 MyBatis XML。当前通用参数 `CommonParameterRepository` 已作为 MyBatis 试点迁移。
- Flyway migration 只能承载表结构变更、历史数据兼容迁移和生产必需的基础字典/系统参数；禁止通过 Flyway 写入测试、演示、个人开发或环境专属数据（例如样例应用/工作区、默认开发账号、默认本地进程绑定）。此类数据必须放在测试 fixture、`test-agent-test-support`、mock 数据、显式本地开发脚本或人工初始化流程中。历史已存在的开发种子迁移仅为兼容已落库环境保留，后续不得新增同类迁移。

## V1 核心表

`backend/test-agent-persistence/src/main/resources/db/migration/V1__create_core_tables.sql` 创建以下表：

| 表 | 说明 |
|---|---|
| `workspaces` | 平台工作区，包含业务 ID、名称、根路径、服务器归属、状态、traceId、创建和更新时间。 |
| `sessions` | 智能体会话，关联 workspace，包含标题、状态、traceId、创建和更新时间。 |
| `runs` | 运行记录，关联 session/workspace，包含 Run 状态、traceId、创建和更新时间；V10 后可记录单次 Run token/cost 快照。 |
| `run_events` | RunEvent append-only 事件流，按 `(run_id, seq)` 唯一并支持增量回放。 |
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
- `session_messages.content` 保留为旧文本 fallback；V10 后新增的 `parts_json`、token/cost 字段允许为空，旧数据和旧前端继续只读 `content`。
- `ai_model_configs` 只保存模型目录元数据，不保存 token、API key 或 provider secret；企业内调用密钥继续由部署环境变量或配置中心注入。
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
| `user_ssh_keys` | 用户个人 SSH 私钥配置，私钥密文、RSA 加密的临时 AES 密钥、nonce、指纹和名称。 |

关键约束：

- `application_members(app_id, user_id)` 唯一；`deleted_at` 为空表示有效关系。
- `code_repositories.git_url` 唯一；不提供删除仓库配置的业务接口。
- `code_repositories.english_name` 后续迁移新增，可空兼容历史数据；非空值唯一，新增/编辑代码库时由后端校验 1 到 29 位英文字母并统一小写保存。
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
- 应用版本和个人工作区物理根目录由 `common_parameters` 中的 `OPENCODE_APP_WORKSPACE_ROOT`、`OPENCODE_PERSONAL_WORKTREE_ROOT` 决定，`common_parameters` 为唯一事实源，缺失时直接抛业务异常（不再回退 yaml 或代码默认值）。数据库只记录最终路径，不负责创建或清理目录。

## V10 user_ssh_keys 新增 encrypted_aes_key 列

`backend/test-agent-persistence/src/main/resources/db/migration/V10__add_encrypted_aes_key_to_user_ssh_keys.sql` 为 `user_ssh_keys` 表新增 `encrypted_aes_key text` 列，承载混合加密方案中 RSA-OAEP 加密后的临时 AES 密钥。

兼容策略：

- 旧记录的 `encrypted_aes_key` 为 `NULL`，应用层在解密时检测到 NULL 抛「SSH key 使用的旧版加密格式，请重新添加」，提示用户通过新版前端重新添加。
- 新增 SSH key 时前端先用 `GET /api/internal/platform/configuration-management/ssh-key/public-key` 取服务端 RSA 公钥，再 AES-256-GCM 加密私钥、RSA-OAEP/SHA-256 加密临时 AES 密钥，连同 nonce、指纹一起提交；服务端 RSA 私钥（`classpath:rsa-private.key`）解密并校验指纹后落库。
- 静态 AES 密钥配置 `test-agent.security.ssh-key-encryption-key` / `TEST_AGENT_SSH_KEY_ENCRYPTION_KEY` 不再使用，迁移到 RSA 私钥文件。

## V20260626150000 通用参数与工作空间创建进度

`backend/test-agent-persistence/src/main/resources/db/migration/V20260626150000__add_common_parameters_and_workspace_create_operations.sql` 增加通用参数、代码库英文名和设置页创建工作空间进度表。

新增表与字段：

| 表/字段 | 说明 |
|---|---|
| `common_parameters` | 通用参数表，包含参数英文名、参数中文名、参数值、适用平台 `windows/linux/all`、创建和更新时间。 |
| `code_repositories.english_name` | 代码库英文名称，可空兼容历史数据，非空唯一，最大 29 字符。 |
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
- 通用参数读取按 `当前平台 -> all` 顺序选择，命中即用；未命中或值为空时抛 `INTERNAL_ERROR` 业务异常（`通用参数未配置：<参数英文名>`），强制运维在 `common_parameters` 表中补配。`OPENCODE_PUBLIC_AGENT_GIT_URL` 例外，其缺失或为 `UNCONFIGURED` 时视为公共级功能未启用，不抛异常。
- `workspace_create_operations` 只服务 HTTP 轮询进度，不写入 `run_events`，也不参与 RunEvent SSE 续传。

## V20260626180000 删除废弃参数 OPENCODE_WORKSPACE_ROOT

`backend/test-agent-persistence/src/main/resources/db/migration/V20260626180000__drop_deprecated_opencode_workspace_root_parameter.sql` 删除 `common_parameters` 中无消费方的 `OPENCODE_WORKSPACE_ROOT`（linux/windows 各一行）。该参数仅为 `OPENCODE_APP_WORKSPACE_ROOT` / `OPENCODE_PERSONAL_WORKTREE_ROOT` 的父目录，子目录参数已独立维护全路径，父参数不再需要。

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
| `agent_config_worktrees` | 公共级/工作空间级 Agent 配置 worktree 记录，包含 scope、workspaceId、worktreeName、branch、rootPath、createdBy、status 和时间戳。 |
| `agent_config_operations` | Agent 配置 Git 长操作进度快照，包含 operationId、scope、action、status、currentStep、错误信息、traceId、branch、commitHash 和时间戳。 |

兼容策略：

- `OPENCODE_PUBLIC_AGENT_GIT_URL` 默认 `UNCONFIGURED`，功能只读展示但禁用 Git 更新/发布；运维更新参数值后启用。
- scope/status 枚举由领域对象校验，数据库保存字符串并保留非空约束，避免 H2 与 PostgreSQL 在同名列 check 表达式上的兼容差异。
- 公共 agent 标准目录是 `OPENCODE_PUBLIC_CONFIG_GIT_ROOT/opencode/agents/`；读兼容 legacy `opencode/agent/`，写入标准目录。
- 工作空间级标准目录是 `{workspace.rootPath}/.opencode/agents/`；读兼容 `.opencode/agent/`，写入标准目录。
- Agent 配置 operation 供 WebSocket snapshot 和历史查询使用，不写入 `run_events`，也不参与 RunEvent SSE 续传。

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

- 旧 `application_workspace_versions.runtime_workspace_id/repo_root_path/workspace_root_path` 保留，作为首次创建节点和旧响应兼容字段。
- migration 只对已具备 `workspaces.linux_server_id` 的历史应用版本回填副本；`current_commit_hash` 为空，由启动/周期补偿任务读取本机 Git HEAD 后更新。
- `target_commit_hash` 为空的历史版本在首次本机副本校验成功后由业务层回填为当前 HEAD；随后各服务器通过内部广播和补偿扫描追平。

## 用户 → 应用 → 工作空间 默认进入行为

`user_application_workspace_preferences` 与 `user_global_workspace_preferences` 是前端"用户进入平台时默认工作空间"的持久化依据：

- `user_application_workspace_preferences(user_id, app_id)` 唯一键：前端 `GET /applications/{appId}/recent-workspace` 通过该键查询用户在指定应用下的最近工作空间。
- `user_global_workspace_preferences(user_id)` 唯一键：作为跨应用维度的兜底，避免用户切换应用时丢失上下文。
- 每次用户切换工作空间（`POST /workspaces/{workspaceId}/recent`）由后端 `ManagedWorkspaceApplicationService.markRecent` 同步写入两表，互不冲突。

首次进入（无 recent）回退策略：

- 前端 `handleSelectApp` → `pickDefaultWorkspaceForApp`：
  1. 读取 `user_application_workspace_preferences`；命中即用。
  2. 未命中：调 `listWorkspaceTemplates` + `listWorkspaceVersions` 拿到第一个模板的第一个版本的 `runtimeWorkspace`，再调 `POST /workspaces/{workspaceId}/recent` 写入偏好。
  3. 应用下没有任何模板/版本：保持空态，由用户手动选择本机目录。
- 兜底命中会立即持久化偏好，保证"第二次进入直接命中第 1 步"。

V10 种子数据对 F-COSS 的影响：

- `V10__seed_fcoss_application.sql` 同步写入 `user_application_workspace_preferences(user='888888888', app='app_fcoss', workspace='wrk_fcoss_20260701')`，本地开发用户首次进入 F-COSS 直接落到最新版本。
- 删除/重置后只要重新执行 `V10`（幂等）即可恢复默认状态；偏好表本身的幂等写入由 `INSERT ... ON CONFLICT DO UPDATE` 在 `ManagedWorkspaceRepository.savePreference` 内保证。

## opencode 用户进程管理表版本调整

设计阶段曾使用“V10 opencode 用户进程管理表”这一版本描述；实际仓库中 V10 保留给 F-COSS seed，消息/Run 消耗字段迁移使用 V16，最终表结构迁移以 `V14__create_opencode_process_management_tables.sql` 为准。该迁移都是新增表，不修改旧 `execution_nodes` 或 `sessions.opencode_*` 字段：

| 表 | 说明 |
|---|---|
| `linux_servers` | Linux 服务器快照，`linux_server_id` 当前直接保存服务器 IPv4，记录状态、最后心跳和容量摘要 JSON。 |
| `backend_java_processes` | 后端 Java 实例，记录所属 Linux IP、实例直连地址、状态、启动时间和最后心跳。 |
| `opencode_containers` | opencode 容器，记录所属 Linux 服务器、容器名称、独立端口池、最大进程数、当前进程数和状态。 |
| `opencode_container_managers` | 容器管理进程，每个容器最多一个 manager，记录协议版本、连接状态、能力 JSON 和最后心跳。 |
| `opencode_manager_backend_connections` | manager 与后端 Java 实例的 WebSocket 连接快照，按 `(manager_id, backend_process_id)` 唯一。 |
| `opencode_server_processes` | 用户专属 opencode server 进程，记录用户、Linux 服务器、容器、主机直通端口、PID、`base_url`、启动路径和健康状态。 |
| `user_opencode_process_bindings` | 用户到 opencode 进程的当前绑定，按 `(user_id, agent_id)` 唯一，首期 `agent_id='opencode'`。 |

关键约束：

- `linux_servers.linux_server_id` 使用服务器 IPv4 地址，后续 `opencode_server_processes.base_url` 由 `http://{linux_server_id}:{port}` 形成。
- `opencode_containers` 使用每容器独立端口范围，`max_processes` 不能超过端口数，`current_processes` 不能超过 `max_processes`。
- `opencode_container_managers.container_id` 唯一，保证每个容器只有一个管理进程。
- `opencode_server_processes(linux_server_id, port)` 唯一，保证同一 Linux 服务器端口不会绑定多个 opencode 进程。
- `user_opencode_process_bindings(user_id, agent_id)` 唯一，保证同一用户对同一 agent 只有一个当前绑定；`process_id` 同样唯一，避免一个进程被多个用户绑定。

兼容策略：

- 旧 `execution_nodes` 继续保留，供无用户主体的 static-token 兼容调用和本地固定节点探测使用。
- `agent_session_bindings` 继续作为平台 Session 到远端 session/node 的主绑定表；用户进程模型只会在 binding 指向的节点与当前用户进程不一致时覆盖当前绑定，不删除旧远端 session。
- 应用回滚时可保留这些新增表；如需完整回退 Web 用户对话到固定节点模式，应回滚后端和前端镜像，而不是删除 V10 表或清理 `/data/.testagent/agent-opencode/.session/{port}`。
- 后端启动/心跳更新 `linux_servers`、`backend_java_processes`；manager WebSocket 注册和心跳更新 `opencode_containers`、`opencode_container_managers` 和 `opencode_manager_backend_connections`。

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
| `linux_servers` | 后端 Linux 服务器节点，记录状态、容量摘要和心跳。 |
| `backend_java_processes` | 后端 Java 进程实例，记录监听地址、所属 Linux 服务器和心跳。 |
| `opencode_containers` | opencode 容器，记录端口池、容量、当前进程数和状态。 |
| `opencode_container_managers` | 容器内管理进程，记录协议版本、连接状态、能力和心跳。 |
| `opencode_manager_backend_connections` | 管理进程到后端 Java 进程的控制面连接状态。 |
| `opencode_server_processes` | 用户专属 opencode server 进程，记录用户、端口、PID、session/config 路径和健康状态。 |
| `user_opencode_process_bindings` | 用户到 agent/opencode 进程的唯一绑定。 |
- `created_by_user_id` 选择 `users.username = '888888888'` 的用户，没有该用户时整条插入被跳过；不引入新用户。
- 不影响 V9 的表结构与已有迁移路径；模板与版本均为 ACTIVE，运行态 `workspaces` 同步 ACTIVE 状态。

## V20260625184300 scheduler 框架表与来源预留字段

`backend/test-agent-persistence/src/main/resources/db/migration/V20260625184300__create_scheduler_framework_tables.sql` 创建通用定时任务框架表，并给会话、Run、消息增加来源预留字段。本次只提供框架和管理 API，不新增具体业务任务，也不开放普通用户创建 Cron 计划 API。该版本使用 14 位时间戳，避免与既有 `V15__add_opencode_process_id_check_constraints.sql`、`V17__seed_local_opencode_machine_for_default_user.sql` 或其他并行分支的数字版本冲突。

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

新增来源字段：

| 表 | 字段 | 说明 |
|---|---|---|
| `sessions` | `source_type`、`source_ref_id`、`created_by_user_id` | 会话来源，默认 `MANUAL`；`SCHEDULED_TASK` 用于未来定时会话。 |
| `runs` | `source_type`、`source_ref_id`、`triggered_by_user_id` | Run 来源，默认 `MANUAL`。 |
| `session_messages` | `source_type`、`source_ref_id`、`sender_user_id` | 消息来源，默认 `MANUAL`。 |

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

`backend/test-agent-persistence/src/main/resources/db/migration/V12__create_ai_model_configs.sql` 创建 `ai_model_configs`，用于企业内模型目录接口读取和默认模型控制。

| 字段 | 说明 |
|---|---|
| `id` | 数据库自增 surrogate PK，不对 API 暴露。 |
| `provider_id` | 模型所属 provider，企业内默认 `icbc-openai`。 |
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

- 启动时由 runtime 服务按配置 seed openclaw 企业 patch 中的内网模型清单；已存在的 `(provider_id, model_id)` 不会被覆盖，表内人工调整的启停、排序和默认模型会保留。
- 表内不保存调用密钥；`ICBC_OPENAI_AUTH_TOKEN` 或自定义 token 环境变量只在运行时同步 opencode provider 配置时引用。
- 删除模型建议先设 `enabled=false`，避免前端仍持有旧 `providerId/modelId` 时出现不可解释的目录缺失。

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

## V17 本地 opencode 机器与默认开发用户进程种子

`backend/test-agent-persistence/src/main/resources/db/migration/V17__seed_local_opencode_machine_for_default_user.sql` 为本地开发环境预置一个 "本地 opencode 机器"（Linux 服务器 + 容器 + 管理进程）和默认开发用户 `usr_test_dev`（用户名 `888888888`）的进程绑定，让前台登录后右侧对话窗口不再因 "没有可用的 opencode 容器" 而直接报错。

兼容历史本地库时，V17 不只按固定 `process_id='ocp_local_user_dev'` 判断是否已种子化，也会检查 `linux_server_id='127.0.0.1' and port=4096` 是否已有 opencode 进程。若同端口已有旧进程，迁移复用该进程写入默认用户绑定，不再插入新的 `ocp_local_user_dev`，避免 `uk_opencode_server_processes_linux_port` 唯一约束阻塞本地启动。

种子数据：

| 表 | 关键字段 | 值 |
|---|---|---|
| `linux_servers` | `linux_server_id` / `name` / `status` | `127.0.0.1` / `local-opencode-host` / `READY` |
| `opencode_containers` | `container_id` / `port_start..end` / `max_processes` / `current_processes` / `status` | `ctr_local_4096` / `4096..4096` / `1` / `1` / `READY` |
| `opencode_container_managers` | `manager_id` / `container_id` / `connection_status` | `mgr_local_4096` / `ctr_local_4096` / `CONNECTED` |
| `opencode_server_processes` | `process_id` / `user_id` / `port` / `base_url` / `status` | `ocp_local_user_dev` / `usr_test_dev` / `4096` / `http://127.0.0.1:4096` / `RUNNING` |
| `user_opencode_process_bindings` | `user_id` / `agent_id` / `process_id` / `status` | `usr_test_dev` / `opencode` / `ocp_local_user_dev` / `ACTIVE` |

说明：

- `linux_server_id = 127.0.0.1` 与默认 `TEST_AGENT_LINUX_SERVER_ID`（即 `ManagerControlSettings.linuxServerId`）保持一致；容器端口 `4096` 与默认 `TEST_AGENT_OPENCODE_BASE_URL` 监听端口一致。
- `opencode_server_processes.base_url` 满足 V15 校验 `= 'http://' || linux_server_id || ':' || port`。
- `process_id` 以 `ocp_` 开头（V15 校验），`manager_id` 以 `mgr_` 开头（V15 校验）。
- `OpencodeManagerBackendConnection` 的 `backend_process_id` 形如 `bjp_xxx`，由后端 `BackendJavaProcessLifecycleService.registerHeartbeat` 在启动时为本实例补齐，因此 migration 不预置该行。
- 补齐逻辑详见 `backend/test-agent-opencode-runtime/src/main/java/com/icbc/testagent/opencode/runtime/process/socket/BackendJavaProcessLifecycleService.java#bootstrapLocalManagerConnections`，仅在 (manager, backend) 组合不存在连接行时插入；已有行只更新 `last_heartbeat_at` / `status`，与 `ManagerControlApplicationService.register/heartbeat` 协调更新。

健康检测/启动网关选择：

- 默认 `test-agent.opencode.manager-control.gateway-mode=socket`（生产）：`SocketOpencodeProcessManagerGateway` 走 manager WebSocket，本地没起 manager 时 health/start 都会返回 `OPENCODE_UNAVAILABLE`，前端状态会落到 "opencode 进程健康检测失败，需要重新初始化"。
- `application-local.yml` 默认 `gateway-mode=local`（受 `TEST_AGENT_OPENCODE_GATEWAY_MODE` 覆盖）：`LocalOpencodeProcessManagerGateway` 直连 `baseUrl` 跑 HTTP GET，`startProcess` 走占位返回；本机 127.0.0.1:4096 真的在跑 opencode server 时，前台状态会从 UNAVAILABLE 升级为 READY。
- `local-direct` 完全短路：`application-local.yml` / `application-guo.yml` 默认 `test-agent.opencode.local-direct=true`（受 `TEST_AGENT_OPENCODE_LOCAL_DIRECT` 覆盖），`UserOpencodeProcessAssignmentService` 在 `status` / `initialize` / `requireReadyProcess` 三个入口跳过 database topology / user binding / manager health 校验链路，合成指向 `test-agent.opencode.local-direct-base-url`（默认 `http://127.0.0.1:4096`）的 READY 进程对象；无论 V17 种子 / 真实 opencode server / manager 状态如何，本地登录后状态接口都直接落到 READY。生产请把 `local-direct` 设回 `false`（也是 Java 字段默认值），保留 topology / health 校验。

## 后续 migration 版本规则

V17 及以前保留既有数字版本，已在本地或共享库执行过的 migration 禁止重命名。V17 之后新增 migration 必须使用 `VyyyyMMddHHmmss__description.sql`，时间戳按开发者创建迁移时的本地时间确定；多人并行开发时不得再抢占 `V18`、`V19` 这类顺序数字版本。提交前需运行持久化模块 migration 命名测试，确认版本唯一且时间戳规则生效。

兼容策略：

- 全部插入语句使用 `where not exists` / `where exists` 保护，重复执行迁移不会破坏数据或产生重复行。
- 仅在 `users.user_id = 'usr_test_dev'`（V5 默认开发用户）存在时才插入 `opencode_server_processes` 与 `user_opencode_process_bindings`；生产环境无该用户时整段种子不写用户进程相关表，仅保留拓扑种子，便于后续手工绑定。
- 容器 `current_processes = 1` 反映当前已有一个用户进程；若需要新增第二个用户，需要先把 `current_processes` 与 `max_processes` 调大并扩展端口池，或先解除已有绑定。

## V20260626210000 数据库表和字段中文注释

`backend/test-agent-persistence/src/main/resources/db/migration/V20260626210000__add_chinese_comments_for_all_tables.sql` 为项目中所有数据库表和字段添加中文注释：

### 添加注释的表

| 表 | 说明 |
|---|---|
| `workspaces` | 平台工作区表，包含业务ID、名称、根路径、服务器归属、状态等信息 |
| `sessions` | 智能体会话表，关联workspace，包含标题、状态、来源等信息 |
| `runs` | 运行记录表，关联session/workspace，记录Run状态、token消耗等信息 |
| `run_events` | RunEvent事件流表，append-only，按(run_id, seq)唯一并支持增量回放 |
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
