# 后端 API 文档

本文档是平台 HTTP API 的稳定入口。所有对外 API 新增或变更都必须更新本文件。

## 文档模板

每个 API 必须记录：

- 路径和方法。
- 用途。
- 鉴权要求。
- 请求参数、请求体。
- 响应体。
- 错误码。
- traceId 行为。
- 兼容性说明。
- 对应测试。

## 当前约定

1. 前端只能通过 `backend-api` 访问平台后端服务，当前由 `test-agent-app` 装配运行。
2. 前端不得直接访问 opencode server。
3. 后端不得直接返回 generated SDK DTO。
4. API 返回平台 DTO 和统一错误格式。
5. API 文档变更必须与 Controller、DTO、测试同步。
6. 旧 `/api/...` URL 全部保留；新增 URL 与旧 URL 并行暴露，不重定向、不删除。
7. CORS 本地默认仅覆盖主前端与 `frontend-opencode` 的 localhost/127.0.0.1 开发、预览和 real E2E 端口；生产必须通过 `TEST_AGENT_CORS_ALLOWED_ORIGINS` 显式配置允许来源。

## API URL 分层

同一业务能力可以同时暴露旧 URL 和新 URL。新旧 URL 必须共享同一 DTO、鉴权、traceId、限流、错误格式和业务实现。

| URL 前缀 | 用途 |
|---|---|
| `/api/...` | 旧兼容入口，当前前端和历史调用方继续可用。 |
| `/api/internal/platform/{business-project}/{business}/...` | 前端调用平台自身能力的新入口。 |
| `/api/internal/agent/{agentId}/...` | 与具体 agent 交互的新入口，`agentId` 由前端 URL 传递；当前唯一可运行值为 `opencode`。 |
| `/api/internal/platform/opencode-runtime/manager-backends` | opencode-manager 后端发现入口，使用独立 manager token。 |
| `/api/internal/platform/opencode-runtime/management/overview` | 超级管理员只读运行管理入口，使用用户 JWT 且要求 `SUPER_ADMIN`。 |
| `/api/public/...` | 其他系统调用平台的公开 API，当前预留；新增前必须完成鉴权、限流、安全和兼容性设计。 |

当前已落地的新平台入口：

| 业务工程 | 新 URL 示例 | 旧 URL 示例 |
|---|---|---|
| `workspace-management` | `/api/internal/platform/workspace-management/workspaces` | `/api/workspaces` |
| `workspace-management` | `/api/internal/platform/workspace-management/workspace-directories` | `/api/workspace-directories` |
| `workspace-management` | `/api/internal/platform/workspace-management/workspaces/{workspaceId}/files/content` | `/api/workspaces/{workspaceId}/files/content` |
| `workspace-management` | `/api/internal/platform/workspace-management/applications` | 无旧 URL |
| `workspace-management` | `/api/internal/platform/workspace-management/applications/{appId}/workspace-templates/{templateId}/versions` | 无旧 URL |
| `workspace-management` | `/api/internal/platform/workspace-management/workspace-versions/{versionId}/personal-workspaces` | 无旧 URL |
| `opencode-runtime` | `/api/internal/platform/opencode-runtime/sessions` | `/api/sessions` |
| `opencode-runtime` | `/api/internal/platform/opencode-runtime/runs` | `/api/runs` |
| `opencode-runtime` | `/api/internal/platform/opencode-runtime/runs/{runId}/events` | `/api/runs/{runId}/events` |
| `opencode-runtime` | `/api/internal/platform/opencode-runtime/agents` | `/api/agents` |
| `opencode-runtime` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/terminal/tickets` | `/api/sessions/{sessionId}/terminal/tickets` |
| `opencode-runtime` | `/api/internal/platform/opencode-runtime/management/overview` | 无旧 URL |
| `configuration-management` | `/api/internal/platform/configuration-management/applications` | 无旧 URL |
| `configuration-management` | `/api/internal/platform/configuration-management/personal/ssh-keys` | 无旧 URL |

当前已落地的 agent-scoped 入口示例：

| 新 URL | 平台业务实现 |
|---|---|
| `/api/internal/agent/{agentId}/runs` | 启动 Run；默认前端传 `opencode`。 |
| `/api/internal/agent/{agentId}/runs/{runId}/events` | 订阅 RunEvent SSE。 |
| `/api/internal/agent/{agentId}/runs/{runId}/diff` | 查询 Run 级 Diff。 |
| `/api/internal/agent/{agentId}/processes/me` | 查询或初始化当前用户的 opencode 进程。 |
| `/api/internal/agent/opencode/api/agent` | Agent 目录。 |
| `/api/internal/agent/opencode/api/model` | Model 目录。 |
| `/api/internal/agent/opencode/api/status` | Runtime 健康状态。 |
| `/api/internal/agent/opencode/file` | 文件列表。 |
| `/api/internal/agent/opencode/file/content` | 文件读取。 |
| `/api/internal/agent/opencode/vcs/status` | VCS 状态。 |
| `/api/internal/agent/opencode/session/{sessionId}/diff` | Session Diff。 |
| `/api/internal/agent/opencode/session/{sessionId}/abort` | Session abort。 |
| `/api/internal/agent/opencode/permission?sessionId={sessionId}` | Pending permission；opencode 原路径不包含平台 sessionId，因此使用 query 定位平台 session。 |
| `/api/internal/agent/opencode/question?sessionId={sessionId}` | Pending question；opencode 原路径不包含平台 sessionId，因此使用 query 定位平台 session。 |

## 统一响应

所有成功响应使用 `ApiResponse<T>`：

```json
{
  "success": true,
  "data": {},
  "traceId": "trace_1234567890abcdef"
}
```

- `success`：成功响应固定为 `true`。
- `data`：接口业务数据，可以是对象、数组或 `null`。
- `traceId`：入口请求携带的合法 `X-Trace-Id`，缺失或非法时由后端生成。

所有错误响应使用 `ApiErrorResponse`：

```json
{
  "success": false,
  "code": "VALIDATION_ERROR",
  "message": "请求参数无效",
  "traceId": "trace_1234567890abcdef",
  "details": {}
}
```

- `success`：错误响应固定为 `false`。
- `code`：稳定错误码，前端可以按错误码展示或降级。
- `message`：面向调用方的安全错误说明，不包含堆栈、SQL、密钥、token 或内部路径。
- `details`：可选安全结构化详情；不存在详情时为空对象。
- 未注册 agent 会返回 `NOT_FOUND`，`details.agentId` 为规范化后的 agent 标志；当前 `otheragent` 只是后端抽象占位，不注册为可调用实现。

## 错误码

| code | HTTP 状态 | 默认说明 |
|---|---:|---|
| `VALIDATION_ERROR` | 400 | 请求参数无效 |
| `UNAUTHENTICATED` | 401 | 未认证 |
| `FORBIDDEN` | 403 | 无权限 |
| `NOT_FOUND` | 404 | 资源不存在 |
| `CONFLICT` | 409 | 状态冲突 |
| `RATE_LIMITED` | 429 | 请求过于频繁 |
| `INTERNAL_ERROR` | 500 | 服务器内部错误 |
| `OPENCODE_BAD_GATEWAY` | 502 | opencode 服务响应异常 |
| `OPENCODE_UNAVAILABLE` | 503 | opencode 服务不可用 |
| `OPENCODE_TIMEOUT` | 504 | opencode 服务超时 |
| `GIT_UNAVAILABLE` | 503 | Git 服务不可用 |
| `GIT_TIMEOUT` | 504 | Git 操作超时 |

## TraceId 规则

- 请求头使用 `X-Trace-Id`。
- 合法 traceId 以 `trace_` 开头，只包含字母、数字、下划线和短横线。
- 缺失或非法 traceId 由后端生成，并通过响应头 `X-Trace-Id` 和响应体 `traceId` 返回。
- 全局异常处理也必须返回同一个 traceId。

## API 分类

后续 API 按以下分类维护：

- Workspace API。
- Session API。
- Run API。
- RunEvent API。
- Cancel API。
- opencode 后端内部封装能力。
- Public API。
- 健康检查和观测性 API。

## 认证 API

认证 API 提供用户登录、登出和 Token 管理功能。登录成功后返回 Token，前端需在后续请求的 `Authorization` 头中携带 `Bearer <token>`。Token 默认过期时间 1 天。

| 方法 | 路径 | 用途 |
|---|---|---|
| `POST` | `/api/auth/login` | 用户登录，返回 Token。 |
| `POST` | `/api/auth/logout` | 用户登出，删除 Token。 |
| `GET` | `/api/auth/me` | 获取当前登录用户信息。 |
| `POST` | `/api/auth/refresh` | 刷新 Token，旧 Token 失效。 |

`POST /api/auth/login` 请求体：

```json
{
  "username": "admin",
  "password": "password"
}
```

响应 `LoginResponse`：

```json
{
  "token": "uuid-token-string",
  "userId": "usr_...",
  "username": "admin",
  "unifiedAuthId": "统一认证号",
  "roles": ["APP_ADMIN"]
}
```

`GET /api/auth/me` 响应 `CurrentUserResponse`：

```json
{
  "userId": "usr_...",
  "username": "admin",
  "unifiedAuthId": "统一认证号",
  "organization": "所属机构",
  "rdDepartment": "所属研发部",
  "department": "所属部门",
  "roles": ["APP_ADMIN"],
  "roleLabels": ["应用管理员"]
}
```

- `roleLabels` 是 `roles` 对应的中文展示名（来自 `dictionaries.dict_label`），供前端用户菜单直接展示；多角色按当前用户的实际顺序返回。
- 数据来源：`users`（`/api/auth/me` 上下文）→ `user_roles`（关联角色 code）→ `dictionaries`（按 `dict_key = 'ROLE'` 取 `dict_label`）。
- 字典缺失或 `dict_key` 不匹配时回退为 role code 本身；不会阻断 `/api/auth/me` 主链路。

`POST /api/auth/refresh` 响应 `LoginResponse`（同上）。请求需携带当前有效 Token。

兼容性：
- 登录路径当前只有 `/api/auth/login`，后续可增加 `/api/internal/platform/system-management/auth/login` 平台入口。
- Token 存储在 Redis，1 天过期。
- 登录、刷新和 `/api/auth/me` 会返回当前用户全局角色 `roles`，以及中文展示名 `roleLabels`。旧 token 或旧响应缺少字段时前端按空列表兼容。
- 认证失败统一返回 `UNAUTHENTICATED` 错误码。
- 未配置 Token 时 `/api/` 默认放行（本地开发）。

## 应用配置管理 API

Base URL：`/api/internal/platform/configuration-management`。本能力只产生和维护配置数据，不触发 clone、不启动 Session/Run、不消费运行态 Workspace。

鉴权：

- 应用与工作区接口需要已登录用户具备全局角色 `APP_ADMIN`；`SUPER_ADMIN` 继承该权限。角色来自 `user_roles + dictionaries(ROLE)`，不在 `user_roles` 增加 `application_id`。
- 个人 SSH key 接口只要求已登录用户，用户只能管理自己的 key。

### 应用与人员

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/applications?enabled=true` | 查询已同步应用定义；应用只读，不提供 CRUD。 |
| `GET` | `/applications/{appId}/members` | 查询当前应用有效成员。 |
| `POST` | `/applications/{appId}/members` | 将已有平台用户加入应用，应用内角色固定为成员。 |
| `DELETE` | `/applications/{appId}/members/{userId}` | 逻辑删除应用成员关系。 |
| `GET` | `/users?keyword=&page=&size=` | 按 `userId` / `unifiedAuthId` / `username` 任一字段大小写不敏感 LIKE 搜索已有平台用户，用于添加成员；keyword 为空时返回全量。 |

`POST /applications/{appId}/members` 请求体：

```json
{ "userId": "usr_..." }
```

成员删除只更新 `application_members.deleted_at`，不影响平台用户、其他应用关系或后续运行态数据。

### 应用与代码库关联

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/repositories?page=&size=` | 分页查询代码库配置。 |
| `POST` | `/repositories` | 新增代码库配置。 |
| `PATCH` | `/repositories/{repoId}` | 编辑中文名称和是否测试智能体标准代码库。 |
| `GET` | `/applications/{appId}/repositories` | 按应用查询已关联代码库。 |
| `POST` | `/applications/{appId}/repositories` | 当前应用关联代码库。 |
| `DELETE` | `/applications/{appId}/repositories/{repoId}` | 删除应用与代码库关联。 |
| `GET` | `/repositories/{repoId}/applications` | 按代码库查询已关联应用。 |
| `POST` | `/repositories/{repoId}/applications` | 指定代码库关联应用。 |
| `DELETE` | `/repositories/{repoId}/applications/{appId}` | 删除代码库与应用关联。 |
| `GET` | `/repositories/{repoId}/branches` | 使用 Git 远端命令列分支。 |
| `GET` | `/repositories/{repoId}/directories?branch=main` | 使用 `git archive --remote` 解析指定分支目录。 |

`POST /repositories` 请求体：

```json
{
  "gitUrl": "git@gitee.com:org/repo.git",
  "name": "中文名称",
  "standard": true
}
```

约束：

- `gitUrl` 支持 SSH 和 HTTPS，创建后不可编辑且全局唯一；不提供代码库删除接口。
- HTTPS URL 不支持内嵌账号或 token；本期不做连通性校验。
- Git 目录读取不 clone、不 fetch；SSH URL 立即使用当前登录用户保存的唯一 SSH key。当前用户未配置 key 或远端不支持 `git archive --remote` 时返回统一 Git 错误。

### 应用工作空间

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/applications/{appId}/workspaces` | 查询当前应用工作空间配置。 |
| `POST` | `/applications/{appId}/workspaces` | 基于当前应用已关联代码库的分支和目录创建工作空间配置。 |
| `PATCH` | `/applications/{appId}/workspaces/{workspaceId}` | 修改工作空间名称。 |
| `DELETE` | `/applications/{appId}/workspaces/{workspaceId}` | 删除工作空间配置。 |

`POST /applications/{appId}/workspaces` 请求体：

```json
{
  "repositoryId": "repo_...",
  "branch": "main",
  "directoryPath": "src/main",
  "workspaceName": "main"
}
```

工作空间记录只保存 `应用 + 代码库 + 分支 + 目录路径 + 工作空间名称`，与运行态 `Workspace` 表和目录浏览器独立。删除仅删除配置记录。

### 个人 SSH key

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/personal/ssh-keys` | 查看自己的 SSH key 元信息。 |
| `POST` | `/personal/ssh-keys` | 新增自己的唯一 SSH 私钥。 |
| `DELETE` | `/personal/ssh-keys/{sshKeyId}` | 删除自己的 SSH key。 |

`POST /personal/ssh-keys` 请求体：

```json
{
  "name": "work",
  "privateKey": "-----BEGIN OPENSSH PRIVATE KEY-----..."
}
```

响应只返回：

```json
{
  "sshKeyId": "ssh_...",
  "name": "work",
  "fingerprint": "SHA256:...",
  "createdAt": "2026-06-23T00:00:00Z"
}
```

私钥使用 AES-GCM 加密存储，密钥来自 `TEST_AGENT_SSH_KEY_ENCRYPTION_KEY` 或 `test-agent.security.ssh-key-encryption-key`，不回显明文或密文。每个用户最多保存一把 key，重复新增返回 `CONFLICT`。

## 限流

Phase 02/03 不新增对外 HTTP API，也不新增 Controller。新增的 Workspace、Session、Run、RunEvent、ExecutionNode、RoutingDecision 字段目前只作为后端内部领域和持久化边界使用，Phase 04 暴露 Runtime API 时再在本文件固化请求/响应 DTO。

内部字段兼容策略：

- `workspaceId`、`sessionId`、`runId`、`eventId`、`executionNodeId` 均保持带前缀字符串，不向前端暴露数据库 surrogate PK。
- RunEvent payload 允许新增字段，前端和后续 API DTO 必须按忽略未知字段处理。
- opencode 错误已在 `test-agent-opencode-client` 映射为平台 `OPENCODE_BAD_GATEWAY`、`OPENCODE_UNAVAILABLE`、`OPENCODE_TIMEOUT`，对外仍使用统一错误响应。
- 平台 Session 与远端 agent Session 是不同概念；`agent_session_bindings` 是新链路主映射，`opencodeSessionId` 和 `opencodeExecutionNodeId` 只作为后端内部兼容字段保存，不进入 HTTP DTO。

## Phase 04 Runtime API

Phase 04 开始由 `test-agent-api` 定义可联调 HTTP API，并由 `test-agent-app` 装配为单一可部署服务包。Controller 只做协议转换、参数校验和统一响应封装，业务编排进入对应业务模块；Controller 不直接访问 Repository，也不直接调用 generated SDK。

### 鉴权、限流和 CORS

- 默认本地开发不配置 `TEST_AGENT_API_TOKEN`，`/api/**` 放行。
- 配置 `TEST_AGENT_API_TOKEN` 后，`/api/**` 必须携带 `Authorization: Bearer <token>`，失败返回 `UNAUTHENTICATED`。
- 内存限流通过 `test-agent.rate-limit.enabled` 控制，超限返回 `RATE_LIMITED`。
- CORS 本地默认允许 `http://localhost:3000` 和 `http://127.0.0.1:3000`，生产环境必须通过配置显式设置允许来源。

### 分页

列表接口统一使用 `page` 和 `size` 查询参数，页码从 1 开始，`size` 最大 200。响应 `data` 为：

```json
{
  "items": [],
  "page": 1,
  "size": 20,
  "total": 0,
  "totalPages": 0
}
```

### Workspace API

| 方法 | 路径 | 用途 |
|---|---|---|
| `POST` | `/api/workspaces` | 注册本地工作区。 |
| `GET` | `/api/workspaces` | 分页列出工作区。 |
| `GET` | `/api/workspace-directories?path=` | 在允许根目录内浏览可选择的本机目录。 |
| `GET` | `/api/workspaces/{workspaceId}` | 查询工作区详情。 |
| `GET` | `/api/workspaces/{workspaceId}/files` | 单层列目录。 |
| `GET` | `/api/workspaces/{workspaceId}/files/content` | 读取 UTF-8 文件。 |
| `PUT` | `/api/workspaces/{workspaceId}/files/content` | 保存 UTF-8 文件。 |
| `GET` | `/api/workspaces/{workspaceId}/files/status` | 查询文件基础状态。 |

新平台 URL 使用 `/api/internal/platform/workspace-management` 前缀。例如：

| 方法 | 新路径 |
|---|---|
| `POST` | `/api/internal/platform/workspace-management/workspaces` |
| `GET` | `/api/internal/platform/workspace-management/workspace-directories?path=` |
| `GET` | `/api/internal/platform/workspace-management/workspaces/{workspaceId}/files` |
| `GET` | `/api/internal/platform/workspace-management/workspaces/{workspaceId}/files/content` |
| `PUT` | `/api/internal/platform/workspace-management/workspaces/{workspaceId}/files/content` |

`POST /api/workspaces` 请求体：

```json
{
  "name": "demo",
  "rootPath": "/absolute/workspace/path"
}
```

`WorkspaceResponse`：

```json
{
  "workspaceId": "wrk_...",
  "name": "demo",
  "rootPath": "/absolute/workspace/path",
  "status": "ACTIVE",
  "createdAt": "2026-06-19T00:00:00Z",
  "updatedAt": "2026-06-19T00:00:00Z"
}
```

`GET /api/workspace-directories?path=` 用于前端受控选择 Workspace 根目录。`path` 缺省时进入第一个允许根目录；后端只返回一层子目录，不返回文件。允许根目录由 `test-agent.workspace-picker.allowed-roots` 配置，环境变量 `TEST_AGENT_WORKSPACE_PICKER_ROOTS` 使用逗号分隔，默认 `${user.home}/workspace`。

响应 `WorkspaceDirectoryListResponse`：

```json
{
  "path": "/Users/huang/workspace",
  "parentPath": null,
  "entries": [
    {
      "name": "demo",
      "path": "/Users/huang/workspace/demo"
    }
  ]
}
```

目录选择器路径必须解析在任一允许根目录内；越出允许根目录返回 `FORBIDDEN`，缺失、不可访问或非目录返回 `VALIDATION_ERROR`。该接口与其他 `/api/**` 入口共享鉴权、限流、CORS、统一错误响应和 traceId 行为。

文件 API 路径参数 `path` 必须解析在 workspace root 内，越权路径返回 `FORBIDDEN`。目录列表为单层，不递归，默认最多 1000 项；文件读取和写入只支持 UTF-8 文本，默认上限 1MB，可通过 `test-agent.files.*` 配置。

`GET /files` 返回 `FileTreeEntryResponse[]`：`path`、`name`、`directory`、`size`、`lastModifiedAt`。

`GET /files/content` 返回 `FileContentResponse`：`path`、`content`、`size`。

`PUT /files/content` 请求体：

```json
{
  "path": "src/App.java",
  "content": "text"
}
```

`GET /files/status` 返回 `FileStatusResponse`：`path`、`exists`、`directory`、`size`、`lastModifiedAt`。

### 应用版本工作区 API

Base URL：`/api/internal/platform/workspace-management`。该能力把配置管理中的应用工作空间模板落为物理 Git 目录，并同步创建运行态 `workspaces` 记录；`workspaces.root_path` 即后续传给 opencode 的工作目录。旧的手动目录注册 `/api/workspaces` 保持兼容。

鉴权：

- 所有接口要求已登录用户。
- 应用、模板、版本、切换最近使用等应用相关接口要求当前用户是 `application_members` 中的有效成员；不区分管理员和普通成员。
- 个人工作区接口要求当前用户是个人工作区拥有者且属于对应应用。

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/applications` | 查询当前用户加入的启用应用。 |
| `GET` | `/applications/{appId}/workspace-templates` | 查询应用工作空间模板，即配置表 `application_workspaces`。 |
| `GET` | `/applications/{appId}/workspace-templates/{templateId}/versions` | 查询模板下已创建的应用版本工作区。 |
| `POST` | `/applications/{appId}/workspace-templates/{templateId}/versions` | 创建或接管应用版本工作区，并创建运行态 Workspace。 |
| `GET` | `/workspace-versions/{versionId}/personal-workspaces` | 查询当前用户基于某版本派生的个人工作区。 |
| `POST` | `/workspace-versions/{versionId}/personal-workspaces` | 基于应用版本工作区创建 git worktree 个人工作区。 |
| `GET` | `/recent-workspace` | 查询当前用户全局最近使用的托管运行态 Workspace。 |
| `GET` | `/applications/{appId}/recent-workspace` | 查询当前用户在指定应用下最近使用的托管运行态 Workspace。 |
| `POST` | `/workspaces/{workspaceId}/recent` | 标记某个托管运行态 Workspace 为最近使用。 |
| `POST` | `/applications/{appId}/workspaces/{workspaceId}/branch-preference` | 记录当前用户在 (appId, workspaceId) 维度下最近一次选择的 VCS 分支。 |
| `GET` | `/applications/{appId}/workspaces/{workspaceId}/branch-preference` | 查询当前用户在 (appId, workspaceId) 维度下最近一次选择的 VCS 分支；未设置返回 `null`。 |
| `GET` | `/personal-workspaces/{personalWorkspaceId}/diff` | 查询个人工作区与应用版本工作区目录差异。 |
| `POST` | `/personal-workspaces/{personalWorkspaceId}/sync-to-application` | 将所选个人工作区文件同步到应用版本工作区，提交并 push。 |
| `POST` | `/personal-workspaces/{personalWorkspaceId}/sync-from-application` | 将所选应用版本工作区文件同步到个人工作区。 |

`POST /applications/{appId}/workspace-templates/{templateId}/versions` 请求体：

```json
{
  "version": "20260707",
  "branch": "feature_testagent_20260707"
}
```

规则：

- `version` 同时支持 `yyyyMMdd`（历史 8 位数字）和 `yyyy年M月`（前端「+新增版本」原样透传，如 `2024年1月`）；其它格式返回 `VALIDATION_ERROR`。
- `yyyy年M月` 格式入库时 `version` 字段保留原值；派生分支名/路径时转 `yyyy-MM`（如 `2024年1月` → `2024-01`），避免 git ref / 路径里出现中文。
- 标准代码库分支固定为 `feature_testagent_{branchFragment}`，其中 `branchFragment` 是 `version` 经 `sanitizeVersionForBranchAndPath` 转换后的值；后端会用当前用户 SSH key 先查分支；不存在时返回 `CONFLICT`。
- 非标准代码库必须传入 `branch`，后端按该分支 clone。
- 物理目录默认在 `${user.home}/test-agent-data` 下，可通过 `test-agent.managed-workspace.root` 或 `TEST_AGENT_MANAGED_WORKSPACE_ROOT` 覆盖。
- 应用版本工作区物理仓库目录为 `appworkspace/{branchFragment}/{repositoryId}`，opencode root 为仓库目录下模板 `directoryPath`。
- 磁盘目录已存在时，后端校验 origin URL 和当前分支，匹配则接管，不覆盖、不删除；不匹配返回 `CONFLICT`。
- SSH Git 操作只使用当前登录用户保存的唯一 SSH key；HTTPS 不额外支持账号或 token。

`ApplicationWorkspaceVersionResponse`：

```json
{
  "versionId": "awv_...",
  "applicationWorkspaceId": "awp_...",
  "appId": "app_...",
  "repositoryId": "repo_...",
  "version": "20260707",
  "branch": "feature_testagent_20260707",
  "repoRootPath": "/data/appworkspace/20260707/repo_...",
  "workspaceRootPath": "/data/appworkspace/20260707/repo_.../F-GCMS/workspace",
  "runtimeWorkspace": {
    "workspaceId": "wrk_...",
    "name": "F-GCMS-20260707",
    "rootPath": "/data/appworkspace/20260707/repo_.../F-GCMS/workspace",
    "status": "ACTIVE",
    "createdAt": "2026-06-23T00:00:00Z",
    "updatedAt": "2026-06-23T00:00:00Z"
  },
  "status": "ACTIVE",
  "createdAt": "2026-06-23T00:00:00Z",
  "updatedAt": "2026-06-23T00:00:00Z"
}
```

`POST /workspace-versions/{versionId}/personal-workspaces` 请求体：

```json
{
  "workspaceName": "私人空间"
}
```

个人工作区基于应用版本仓库创建 git worktree，分支名为 `{应用版本分支}_{统一认证号}_{personalWorkspaceId}`，物理路径使用系统 ID 隔离，前端只展示 `workspaceName`。同一用户在同一应用版本下 `workspaceName` 唯一。

同步请求体：

```json
{
  "files": ["src/App.java"],
  "force": false
}
```

`sync-to-application.force=true` 时使用 `--force-with-lease` 覆盖远端；失败、冲突或认证问题使用统一 Git/冲突错误码返回，并记录同步审计。应用版本工作区与个人工作区同步不新增 RunEvent/SSE 事件。

前端两级菜单（应用工作空间→版本）使用说明：

- 工作台左下角的"应用工作空间"按钮按当前应用（`selectedAppId`）查询 `GET /applications/{appId}/workspace-templates`，渲染第一级菜单（只显示 `workspaceName`，不显示 `directoryPath` / `branch`）。
- 鼠标 hover 第一级菜单项时按需触发 `GET /applications/{appId}/workspace-templates/{templateId}/versions` 加载该模板下的版本（懒加载，未展开的模板不发请求）。
- 点击版本后调用 `GET /workspaces/{workspaceId}` 拉取对应的运行态 `Workspace`，再调用 `POST /workspaces/{workspaceId}/recent` 写入最近使用偏好，并触发工作台切换。
- 当前版本匹配规则：优先按 `runtimeWorkspace.workspaceId` 精确匹配，其次按 `workspaceRootPath` 匹配 `selectedWorkspace.rootPath`。
- 第二级菜单（版本列表）底部固定一行「+新增版本」：点击后弹 el-dialog，内嵌 `ElDatePicker`（`type=month`, `format=yyyy年M月`），用户选月份后调用 `POST /applications/{appId}/workspace-templates/{templateId}/versions`，请求体 `version` 字段原样透传 `yyyy年M月` 字符串。成功后失效 `versionsByTemplateId` 缓存并把新建版本切到工作区。

应用级"默认工作空间"解析规则（前端 `handleSelectApp` + `pickDefaultWorkspaceForApp`）：

1. 调用 `GET /applications/{appId}/recent-workspace` 读取 `user_application_workspace_preferences` 中 `(user_id, app_id)` 对应的最近使用运行态 Workspace。
2. 命中 recent：调用 `POST /workspaces/{workspaceId}/recent` 刷新时间戳（幂等），再切工作台。
3. 未命中 recent：调用 `GET /applications/{appId}/workspace-templates` 拉取应用下的工作空间模板，取第一个模板；再调用 `GET /applications/{appId}/workspace-templates/{templateId}/versions` 拉取该模板下的版本，取第一个版本的 `runtimeWorkspace`。
4. 兜底命中：调用 `POST /workspaces/{workspaceId}/recent` 把该 Workspace 写入 `(user_id, app_id)` 与 `(user_id, NULL)` 两条偏好，下次进入直接命中第 2 步。
5. 应用下没有任何工作空间模板/版本时保持空态，由用户手动选择本机目录。

`POST /workspaces/{workspaceId}/recent` 兼容说明：

- 后端 `markRecentWorkspace` 同时写入 `user_global_workspace_preferences`（`app_id = NULL`）和 `user_application_workspace_preferences`（`app_id = 解析到的 appId`），对应"全局最近"和"应用内最近"两套维度。
- 工作区不属于任何应用（即 `appIdForRuntimeWorkspace` 既不匹配应用版本也不匹配个人工作区）时返回 `NOT_FOUND`；前端 `applyManagedWorkspace` 静默吞掉该错误，切换流程不受影响。
- `POST /applications/{appId}/workspaces/{workspaceId}/branch-preference` 用于在 (appId, workspaceId) 维度持久化用户最近选择的 VCS 分支（写入 `user_workspace_branch_preferences`，按 (user_id, app_id, workspace_id) 唯一索引 upsert）。请求体为 `{"branch":"<branch-name>"}`；调用方需先校验工作区属于该应用。
- `GET /applications/{appId}/workspaces/{workspaceId}/branch-preference` 返回 `BranchPreferenceResponse { appId, workspaceId, branch, updatedAt }`，未设置时返回 `null`，前端可据此在进入工作区时自动回填分支显示或提示用户当前本地分支与偏好分支不一致。

### Session API

| 方法 | 路径 | 用途 |
|---|---|---|
| `POST` | `/api/sessions` | 创建会话。 |
| `GET` | `/api/sessions?q=&page=&size=` | 全局搜索/分页查询 ACTIVE 会话，置顶优先。 |
| `GET` | `/api/workspaces/{workspaceId}/sessions` | 按工作区分页查询会话。 |
| `GET` | `/api/sessions/{sessionId}` | 查询会话详情。 |
| `PATCH` | `/api/sessions/{sessionId}` | 更新会话标题或置顶状态。 |
| `DELETE` | `/api/sessions/{sessionId}` | 软删除会话，状态变为 `ARCHIVED`。 |
| `POST` | `/api/sessions/{sessionId}/messages` | 追加会话消息。 |
| `GET` | `/api/sessions/{sessionId}/messages` | 分页读取会话消息。 |
| `GET` | `/api/sessions/{sessionId}/active-run` | 查询会话最近的非终态 Run；没有时 `data=null`。 |

新平台 URL 使用 `/api/internal/platform/opencode-runtime` 前缀。例如：

| 方法 | 新路径 |
|---|---|
| `POST` | `/api/internal/platform/opencode-runtime/sessions` |
| `GET` | `/api/internal/platform/opencode-runtime/sessions` |
| `GET` | `/api/internal/platform/opencode-runtime/workspaces/{workspaceId}/sessions` |
| `GET` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}` |
| `POST` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/messages` |
| `GET` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/messages` |
| `GET` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/active-run` |

`POST /api/sessions` 请求体：

```json
{
  "workspaceId": "wrk_...",
  "title": "new session"
}
```

`PATCH /api/sessions/{sessionId}` 请求体：

```json
{
  "title": "renamed session",
  "pinned": true
}
```

`SessionResponse`：`sessionId`、`workspaceId`、`title`、`status`、`pinned`、`createdAt`、`updatedAt`。

兼容要求：

- 旧的 `/api/workspaces/{workspaceId}/sessions` 继续有效，但默认只返回 `ACTIVE` 会话。
- `GET /api/sessions` 用于 History 全局搜索，`q` 为空时返回所有 `ACTIVE` 会话。
- `DELETE /api/sessions/{sessionId}` 为软删除，不删除消息、Run、事件或远端 opencode 映射；普通详情、列表和消息追加会把 `ARCHIVED` 会话视为不存在。

`POST /api/sessions/{sessionId}/messages` 请求体：

```json
{
  "role": "USER",
  "content": "message text"
}
```

`GET /api/sessions/{sessionId}/messages` 会先在存在 agent binding 时尝试读取当前 agent projected messages 并 upsert 到 `session_messages`；如果 opencode 进程不可用、超时或远端 session 不存在，接口回退返回数据库快照，不向前端暴露 generated SDK DTO。

`SessionMessageResponse` 基础字段：`messageId`、`sessionId`、`role`、`content`、`createdAt`。当前 role 使用 `USER`、`ASSISTANT`、`SYSTEM`。

新增可选字段：

| 字段 | 说明 |
|---|---|
| `runId` | 消息归属的 Run。 |
| `remoteMessageId` | 远端 agent message id。 |
| `parts` | 远端 message parts 投影数组；缺失时旧前端继续使用 `content`。 |
| `tokens` | `{ input, output, reasoning, cacheRead, cacheWrite }`，字段均可空。 |
| `costUsd` | 单次 Run 成本快照，可空。 |
| `updatedAt` | 快照更新时间。 |

### Run、Cancel 和 Event API

| 方法 | 路径 | 用途 |
|---|---|---|
| `POST` | `/api/runs` | 启动 Run。 |
| `GET` | `/api/runs/{runId}` | 查询 Run 状态。 |
| `POST` | `/api/runs/{runId}/cancel` | 取消 Run。 |
| `GET` | `/api/runs/{runId}/events` | 订阅 RunEvent SSE。 |
| `GET` | `/api/runs/{runId}/diff` | 查询 Run 级 Diff。 |
| `POST` | `/api/runs/{runId}/diff/accept` | 接受 Run 级 Diff。 |
| `POST` | `/api/runs/{runId}/diff/reject` | 拒绝 Run 级 Diff 并触发 opencode revert。 |

新平台 URL 使用 `/api/internal/platform/opencode-runtime` 前缀。例如：

| 方法 | 新路径 |
|---|---|
| `POST` | `/api/internal/platform/opencode-runtime/runs` |
| `GET` | `/api/internal/platform/opencode-runtime/runs/{runId}` |
| `POST` | `/api/internal/platform/opencode-runtime/runs/{runId}/cancel` |
| `GET` | `/api/internal/platform/opencode-runtime/runs/{runId}/events` |
| `GET` | `/api/internal/platform/opencode-runtime/runs/{runId}/diff` |

agent-scoped URL 使用 `/api/internal/agent/{agentId}` 前缀，前端默认传 `opencode`。例如：

| 方法 | agent-scoped 路径 |
|---|---|
| `POST` | `/api/internal/agent/{agentId}/runs` |
| `GET` | `/api/internal/agent/{agentId}/runs/{runId}` |
| `POST` | `/api/internal/agent/{agentId}/runs/{runId}/cancel` |
| `GET` | `/api/internal/agent/{agentId}/runs/{runId}/events` |
| `GET` | `/api/internal/agent/{agentId}/runs/{runId}/diff` |
| `POST` | `/api/internal/agent/{agentId}/runs/{runId}/diff/accept` |
| `POST` | `/api/internal/agent/{agentId}/runs/{runId}/diff/reject` |

### 用户 opencode 进程 API

用户进程 API 只支持 `agentId=opencode`，必须从认证主体读取当前用户；未认证返回 `UNAUTHENTICATED`，非 `opencode` agent 返回 `VALIDATION_ERROR`。初始化会通过当前后端实例已连接的 `opencode-manager` WebSocket 控制面启动进程；无 manager 连接、命令超时或 manager 返回失败时分别映射为 `OPENCODE_UNAVAILABLE`、`OPENCODE_TIMEOUT` 或 `OPENCODE_BAD_GATEWAY`。

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/api/internal/agent/{agentId}/processes/me` | 查询当前用户绑定的 opencode 进程状态。 |
| `POST` | `/api/internal/agent/{agentId}/processes/me/initialize` | 为当前用户初始化或重建 opencode 进程。 |
| `DELETE` | `/api/internal/agent/{agentId}/processes/me/binding` | 清除当前用户的 opencode 进程绑定，便于本地 opencode 场景下用户主动放弃指向已下线 Linux 服务器的脏绑定，让后续状态 / Run 链路回退到 `execution_nodes` 中的固定节点。 |

响应 `data`：

```json
{
  "status": "READY",
  "initializable": false,
  "bindingClearable": false,
  "localFallback": false,
  "message": "opencode 进程可用",
  "processId": "ocp_...",
  "linuxServerId": "10.0.0.12",
  "containerId": "ctr_...",
  "port": 4096,
  "baseUrl": "http://10.0.0.12:4096",
  "checkedAt": "2026-06-24T08:00:00Z"
}
```

字段说明：

- `status`：`READY`、`NEEDS_INITIALIZATION`、`UNAVAILABLE`。
- `initializable`：当前状态是否允许前端展示初始化动作；无当前后端可连接的健康容器时为 `false`。
- `bindingClearable`：当 `status=UNAVAILABLE` 时，如果后端检测到 `execution_nodes` 中仍有可路由的固定节点（例如本地启动的 opencode）作为兜底，会把 `bindingClearable` 置为 `true`，前端可以展示"重置绑定"按钮。
- `localFallback`：当 `status=READY` 时，如果响应已经回退到 `execution_nodes` 中的固定节点而非用户专属进程，`localFallback` 为 `true`；此时 `baseUrl` 来自固定节点，前端可以直接发起对话。
- `message`：面向用户的状态说明或失败原因；命中 `localFallback` 时通常包含"回退到本地 opencode 节点"。
- `processId`、`linuxServerId`、`containerId`、`port`、`baseUrl`：仅在已有或成功初始化进程时返回；`baseUrl` 固定为 `http://{linuxServerId}:{port}`。
- `checkedAt`：本次状态计算时间。

初始化规则：

- 未绑定用户时选择当前后端实例已连接的全局进程数最少 `READY` 容器。
- 已有绑定但进程不可用时固定原 `linuxServerId`，只在该 Linux 服务器内选择当前后端已连接的进程数最少容器。
- 端口从容器端口范围内选择第一个未被当前运行进程占用的端口。
- 启动参数固定为 `XDG_DATA_HOME=/data/opencode/session/{port}` 和 `OPENCODE_CONFIG_DIR=/data/opencode/.config/opencode/`。
- 初始化成功后会同步写入用户进程绑定、进程快照，以及兼容旧运行链路的 `execution_nodes` 投影。

`DELETE` 行为：

- 不会级联删除 `opencode_server_processes` 记录，避免误关停用户可能在用的后台进程。
- 不会重启任何容器；调用后立即返回当前最新状态。
- 典型使用：用户绑定指向的 Linux 服务器已经下线（`canRebuildOn=false`）但本地 `execution_nodes` 里还有 `node_local_opencode`；前端在 `status=UNAVAILABLE` 且 `bindingClearable=true` 时调用此接口，后端会回退到本地节点并把 `status=READY, localFallback=true` 透出。

### opencode-manager 控制面 API

控制面只供容器内 `opencode-manager` 使用，不复用用户 JWT 或普通 API token。后端通过 `test-agent.opencode.manager-control.token` / `TEST_AGENT_OPENCODE_MANAGER_TOKEN` 配置独立 manager token；manager 调用 discovery API 和 WebSocket upgrade 时必须携带 `Authorization: Bearer <token>`。token 缺失或错误返回统一 `UNAUTHENTICATED`。

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/api/internal/platform/opencode-runtime/manager-backends` | 查询 READY 且心跳未过期的后端实例。 |
| `WS` | `/api/internal/platform/opencode-runtime/manager/ws` | manager 与当前后端实例建立 JSON WebSocket 长连接。 |

Discovery 响应 `data`：

```json
[
  {
    "backendProcessId": "bjp_...",
    "linuxServerId": "10.8.0.21",
    "listenUrl": "http://10.8.0.21:8080",
    "webSocketUrl": "ws://10.8.0.21:8080/api/internal/platform/opencode-runtime/manager/ws",
    "lastHeartbeatAt": "2026-06-24T08:00:00Z"
  }
]
```

WebSocket 协议版本固定为 `opencode-manager.v1`。文本帧是 JSON envelope，稳定 `type` 包括 `register`、`registered`、`heartbeat`、`command`、`commandResult`、`error`。后端命令使用 `commandId=mcmd_...`，manager 回包必须带同一 `commandId` 和 `traceId`。当前命令集合为 `start`、`health`、`stop`、`restart`，其中用户初始化和健康检测链路使用 `start`、`health`；人工 stop/restart API 留到后续操作批次。

### opencode runtime 运行管理 API

运行管理 API 是只读高权限平台接口，只允许已认证用户且角色包含 `SUPER_ADMIN` 访问。未认证返回 `UNAUTHENTICATED`，非超级管理员返回 `FORBIDDEN`，非法分页或状态参数返回 `VALIDATION_ERROR`。本接口不触发 stop/restart 操作；管理页展示当前活跃的 Java 后端、opencode server、容器和 manager 运行态，僵死或 5 分钟内没有心跳/健康确认的进程不进入响应。

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/api/internal/platform/opencode-runtime/management/overview` | 查询 Linux 服务器、后端 Java 进程、容器、管理进程、manager-backend 连接、用户 opencode server 进程和绑定状态。 |

查询参数：

| 参数 | 说明 |
|---|---|
| `page` | opencode server 进程分页页码，默认 `1`。 |
| `size` | opencode server 进程分页大小，默认 `20`，上限沿用平台 `PageRequest` 的 `200`。 |
| `status` | 可选进程状态；当前活进程视图只返回 `RUNNING` opencode server 进程，非 `RUNNING` 状态会返回空进程页。 |
| `linuxServerId` | 可选 Linux 服务器 ID，当前等于服务器 IPv4。 |
| `containerId` | 可选容器 ID。 |
| `username` | 可选用户名，运行管理页按用户名筛选和展示。 |
| `userId` | 可选用户 ID，保留给旧客户端兼容；新客户端应使用 `username`。 |

响应 `data`：

```json
{
  "generatedAt": "2026-06-24T08:00:00Z",
  "summary": {
    "linuxServers": 2,
    "readyLinuxServers": 2,
    "backendProcesses": 2,
    "readyBackendProcesses": 2,
    "containers": 4,
    "readyContainers": 3,
    "managers": 4,
    "connectedManagers": 3,
    "managerBackendConnections": 6,
    "opencodeProcesses": 38,
    "runningOpencodeProcesses": 20,
    "userBindings": 38
  },
  "linuxServers": [
    {
      "linuxServerId": "10.8.0.21",
      "name": "10.8.0.21",
      "status": "READY",
      "capacitySummary": {},
      "lastHeartbeatAt": "2026-06-24T08:00:00Z",
      "createdAt": "2026-06-24T07:00:00Z",
      "updatedAt": "2026-06-24T08:00:00Z",
      "traceId": "trace_..."
    }
  ],
  "backendProcesses": [],
  "containers": [],
  "managers": [],
  "managerBackendConnections": [],
  "opencodeProcesses": {
    "items": [
      {
        "processId": "ocp_...",
        "userId": "usr_...",
        "username": "wr",
        "linuxServerId": "10.8.0.21",
        "containerId": "ctr_...",
        "port": 4101,
        "pid": 12345,
        "baseUrl": "http://10.8.0.21:4101",
        "status": "RUNNING",
        "sessionPath": "/data/opencode/session/4101",
        "configPath": "/data/opencode/.config/opencode/",
        "startedAt": "2026-06-24T07:30:00Z",
        "lastHealthCheckAt": "2026-06-24T08:00:00Z",
        "healthMessage": "OK",
        "createdAt": "2026-06-24T07:30:00Z",
        "updatedAt": "2026-06-24T08:00:00Z",
        "traceId": "trace_...",
        "bindingAgentId": "opencode",
        "bindingStatus": "ACTIVE",
        "bindingUpdatedAt": "2026-06-24T07:30:00Z"
      }
    ],
    "page": 1,
    "size": 20,
    "total": 38
  }
}
```

拓扑列表固定最多返回 500 条，避免管理页一次性读取过多连接和进程快照。Java 后端和 opencode server 进程通过 Redis 运行心跳做跨实例活跃判定；Redis 未启用时回退到数据库最近心跳/健康检查时间。后端实例注册会持续写入当前 Java 进程心跳，opencode server 由后端每 3 分钟通过 manager health 命令确认并刷新心跳，Redis 心跳 key 5 分钟过期，索引清理每 5 分钟执行一次。`opencodeProcesses.items[]` 的 `bindingAgentId`、`bindingStatus`、`bindingUpdatedAt` 仅在该进程仍是当前用户绑定时返回，否则为 `null`。

`POST /api/runs` 请求体：

```json
{
  "sessionId": "ses_...",
  "prompt": "run prompt"
}
```

请求体保持向后兼容，并支持以下可选字段：

```json
{
  "sessionId": "ses_...",
  "prompt": "run prompt",
  "parts": [
    { "type": "text", "text": "run prompt" },
    { "type": "file", "path": "src/App.tsx", "source": { "text": "file content", "start": 0, "end": 12 } },
    { "type": "agent", "agentId": "build" },
    { "type": "reference", "id": "ref_1", "label": "Current issue", "uri": "mcp://issues/1" }
  ],
  "messageId": "msg_...",
  "agent": "build",
  "model": "anthropic/claude-sonnet-4-5",
  "variant": "default",
  "mode": "build"
}
```

兼容要求：

- 旧 `prompt: string` 继续有效；`parts` 缺失时后端按单个 text part 处理。
- `parts`、`messageId`、`agent`、`model`、`variant`、`mode` 均为可选字段，旧前端不需要改动。
- `parts` 会下沉为当前 agent runtime 的 prompt parts；`opencode` 实现适配为 `prompt_async` 的 `text/file/agent` parts，`reference` part 会转换为可读 text part。
- file part 带 `source.text` 或 `content` 时后端生成 `data:` URL；前端图片附件可直接提交 `url: "data:<mime>;base64,..."`。只有没有内联内容或 URL 时，后端才把 workspace 内路径转为 `file://` URL，越出 workspace 的路径返回 `VALIDATION_ERROR`。
- `model` 使用 `providerId/modelId` 字符串格式；格式不完整时后端保留旧默认模型，不向 opencode 传 model override。
- 当后端启用托管模型目录时，前端从 Model 目录接口获取可选模型并仍按 `providerId/modelId` 提交；企业内默认模型为 `icbc-openai/DeepSeek-V4-Flash-W8A8`。
- Agent/Model/Variant/Mode 属于运行态选择，不代表 Provider/server/settings 配置；其中 `mode` 当前只保留为平台字段，opencode `PromptInput` 不支持该字段，因此 opencode runtime 不写入 `prompt_async` 请求体。

启动流程会先校验当前认证用户是否已有 `READY` opencode 进程；未就绪时返回 `OPENCODE_UNAVAILABLE`，不创建本地 Run。校验通过后追加用户消息，创建 `PENDING` Run，并使用当前用户进程投影出的 `executionNodeId = "node_" + processId` 和 `baseUrl = http://{linuxServerId}:{port}` 作为本次运行目标。若 `(sessionId, agentId)` 的既有 `agent_session_bindings` 指向的节点不是当前用户进程节点，后端会重新创建远端 session 并覆盖绑定；旧 `sessions.opencode_*` 字段只作为 `opencode` 兼容回填来源。无用户主体的兼容调用（例如 static API token、本地放行或旧系统集成）继续走固定 `execution_nodes` 路由，不要求用户进程。
Run 进入成功、失败或取消终态后，后端会尝试拉取 agent projected messages，将 assistant 输出、parts、token/cost 快照 upsert 到 `session_messages`，并把同一份 token/cost 写入 `runs`；拉取失败时保留数据库已有快照。

### opencode Web Runtime API

opencode Web App 运行态能力统一由 `test-agent-api` 的 runtime Controller 暴露。前端仍只调用平台后端 API，后端通过 `test-agent-opencode-runtime -> test-agent-agent-runtime -> test-agent-opencode-client` 访问 opencode HTTP API，不返回 generated SDK DTO，不允许 Controller 直接调用 generated SDK。

运行态代理与 Run 使用同一套目标解析规则：

- 已登录用户访问默认 `opencode` agent 时，workspace 级目录、文件、配置、provider、MCP 等接口会先校验当前用户已有 `READY` opencode 进程，并使用该进程投影出的 `executionNodeId = "node_" + processId` 与 `baseUrl = http://{linuxServerId}:{port}` 调用 opencode；未初始化或健康检测失败返回 `OPENCODE_UNAVAILABLE`。
- 无用户主体的兼容调用（例如 static API token、本地放行或旧系统集成）继续走固定 `execution_nodes` 路由，不要求用户进程。
- Session 级运行态接口在已登录用户访问默认 `opencode` 时，会校验 `(sessionId, agentId)` 绑定是否指向当前用户进程节点；绑定缺失或节点不一致时，后端会在当前用户进程上创建新的远端 session，并覆盖 `agent_session_bindings` 与兼容 `sessions.opencode_*` 字段。旧远端 session 不由本接口删除。

运行态目录接口：

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/api/agents?workspaceId=` | 读取当前 workspace 的 Agent 列表。 |
| `GET` | `/api/models?workspaceId=` | 读取当前 workspace 的 Model 列表。 |
| `GET` | `/api/providers?workspaceId=` | 读取当前 workspace 的 Provider 只读列表。 |
| `GET` | `/api/commands?workspaceId=` | 读取可执行命令列表。 |
| `GET` | `/api/references?workspaceId=` | 读取可引用上下文目录。 |
| `GET` | `/api/status?workspaceId=` | 读取 opencode runtime 健康状态，后端映射到 opencode `/global/health`。 |
| `GET` | `/api/fs/list?workspaceId=&path=` | 通过 opencode runtime 列目录。 |
| `GET` | `/api/fs/find?workspaceId=&query=` | 通过 opencode runtime 查找文件。 |
| `GET` | `/api/fs/read?workspaceId=&path=` | 通过 opencode runtime 读文件内容。 |
| `GET` | `/api/vcs/status?workspaceId=` | 读取 VCS 状态。 |
| `GET` | `/api/vcs/diff?workspaceId=&mode=working\|git\|branch&context=` | 读取 VCS Diff。 |
| `GET` | `/api/lsp/status?workspaceId=` | 读取 LSP 状态。 |
| `GET` | `/api/mcp/status?workspaceId=` | 读取 MCP 状态。 |
| `GET` | `/api/mcp/resources?workspaceId=` | 读取 MCP resource 目录，后端映射到 opencode `/experimental/resource`。 |
| `GET` | `/api/mcp/tools?workspaceId=&provider=&model=` | 读取 MCP/runtime tool 目录；带 provider/model 时返回工具 schema，否则返回 tool id 降级列表。 |
| `GET` | `/api/config?workspaceId=` | 读取 opencode global config。 |
| `PATCH` | `/api/config?workspaceId=` | 更新 opencode global config，body 透传给 runtime。 |
| `POST` | `/api/global/dispose` | 触发 opencode runtime dispose。 |
| `GET` | `/api/provider/auth?workspaceId=` | 查询 provider auth 状态。 |
| `POST` | `/api/provider/{providerId}/oauth/authorize` | 发起 provider OAuth。 |
| `POST` | `/api/provider/{providerId}/oauth/callback` | 完成 provider OAuth 回调。 |
| `PUT` | `/api/auth/{providerId}` | 写入 provider auth secret，secret 只透传不落前端持久化状态。 |
| `DELETE` | `/api/auth/{providerId}` | 删除 provider auth secret。 |
| `GET` | `/api/worktrees?workspaceId=` | 查询 opencode experimental worktree。 |
| `POST` | `/api/worktrees` | 创建 worktree，body 可包含 `workspaceId`、`branch`、`path` 等 opencode 兼容字段。 |
| `DELETE` | `/api/worktrees` | 删除 worktree，body 透传给 runtime。 |
| `POST` | `/api/worktrees/reset` | 重置 worktree。 |
| `POST` | `/api/mcp/{name}/auth` | 发起 MCP auth。 |
| `POST` | `/api/mcp/{name}/auth/callback` | 完成 MCP auth callback。 |
| `POST` | `/api/mcp/{name}/auth/authenticate` | 执行 MCP auth authenticate 步骤。 |
| `DELETE` | `/api/mcp/{name}/auth` | 删除 MCP auth。 |

以上运行态目录接口同时暴露 `/api/internal/platform/opencode-runtime/...` 兼容平台 URL，并按 agent path 暴露 `/api/internal/agent/{agentId}/...` 新入口。当前 `opencode` 的标准路径形态保持 opencode 原 path，例如 `/api/agents` 同时可通过 `/api/internal/platform/opencode-runtime/agents` 和 `/api/internal/agent/opencode/api/agent` 调用；后续 agent 必须适配到相同平台 DTO 和错误格式。

Model/Provider 目录兼容说明：

- `test-agent.model-catalog.source=opencode` 时，`/api/models` 和 `/api/providers` 保持旧行为，直接代理 opencode。
- `source=bailian` 时，`/api/models` 由后端请求百炼 OpenAI-compatible `/models` 获取；请求失败时返回配置内置外网模型，Provider 为 `modelstudio`。
- `source=internal` 时，`/api/models` 从 `ai_model_configs` 表读取启用模型，Provider 为 `icbc-openai`；启动时会按 openclaw 企业 patch 的模型清单初始化表，默认模型为 `DeepSeek-V4-Flash-W8A8`。
- Model 响应对象包含兼容字段 `id`、`modelId`、`modelID`、`providerId`、`providerID`、`name`，托管来源还会返回 `contextLimit`、`outputLimit` 和 `defaultModel`。前端优先选中 `defaultModel=true` 的模型。

Session 运行态接口：

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/api/sessions/{sessionId}/children` | 查询远端 opencode session children。 |
| `GET` | `/api/sessions/{sessionId}/todo` | 查询 Todo 列表。 |
| `GET` | `/api/sessions/{sessionId}/diff?messageId=` | 查询 session/message 级 Diff。 |
| `POST` | `/api/sessions/{sessionId}/abort` | 中止当前 session 执行。 |
| `POST` | `/api/sessions/{sessionId}/fork` | fork session。 |
| `POST` | `/api/sessions/{sessionId}/compact` | 调用 opencode summarize/compact。 |
| `POST` | `/api/sessions/{sessionId}/revert` | revert 指定 message。 |
| `POST` | `/api/sessions/{sessionId}/unrevert` | 取消 revert。 |
| `POST` | `/api/sessions/{sessionId}/command` | 执行 session command。 |
| `POST` | `/api/sessions/{sessionId}/shell` | 执行 shell command，P1/P2 前端以输出卡片展示。 |
| `POST` | `/api/sessions/{sessionId}/share` | 创建 opencode session share。 |
| `DELETE` | `/api/sessions/{sessionId}/share` | 取消 opencode session share。 |
| `GET` | `/api/sessions/{sessionId}/permissions` | 读取 pending permission。 |
| `POST` | `/api/sessions/{sessionId}/permissions/{requestId}/reply` | 回复 permission，body 支持 `{ "decision": "once|always|reject" }`。 |
| `GET` | `/api/sessions/{sessionId}/questions` | 读取 pending question。 |
| `POST` | `/api/sessions/{sessionId}/questions/{requestId}/reply` | 回复 question，body 为 `{ "answers": [[...], ...] }`；`answers` 为 `List<List<String>>`，外层按子问题顺序排列，内层是该问题的选中 label，一次回复覆盖同一请求下的全部子问题。平台也兼容扁平 `string[]`，按单问题整体包成单个内层数组。 |
| `POST` | `/api/sessions/{sessionId}/questions/{requestId}/reject` | 拒绝 question。 |

以上 Session 运行态接口同时暴露 `/api/internal/platform/opencode-runtime/sessions/{sessionId}/...` 兼容入口。agent path 使用 `/api/internal/agent/{agentId}/session/{sessionId}/...`；当前 `opencode` 的 children、todo、diff、abort、fork、compact、revert、unrevert、command、shell 路径保持 `/api/internal/agent/opencode/session/{sessionId}/...`。permission/question 的 agent path 入口使用 `/api/internal/agent/{agentId}/permission|question`，并通过 query `sessionId` 定位平台 session。

兼容和安全约束：

- 所有响应仍包裹 `ApiResponse<T>`，错误仍走统一错误码和 traceId。
- `workspaceId` 为平台 workspace id，后端只把 workspace root 映射为 opencode `directory`；不得把平台 id 当作 opencode `workspace` query。
- `sessionId` 为平台 session id。无用户主体时，后端通过 `agent_session_bindings` 中的 `(sessionId, agentId)` 定位远端 session；`opencode` 会兼容读取旧 `sessions.opencode_*` 字段并回填 binding，未绑定远端 session 时返回 `CONFLICT`。有用户主体且 agent 为 `opencode` 时，缺失或不匹配的绑定会自动在当前用户进程上重建。
- `permission`/`question` 的平台路径保留在 `/api/sessions/{sessionId}/...` 下，后端实际映射到 opencode `/permission`、`/question` 族 API。
- config/provider auth/worktree/share/MCP auth 均为受控代理能力，前端不得改为直接调用 opencode 原 URL；provider secret 不得写入 localStorage 或日志。
- 只读 transcript 页面 `/s/{sessionId}` 只消费平台 `GET /api/sessions/{sessionId}` 与 `GET /api/sessions/{sessionId}/messages`，不接 opencode 公网 `share_data/share_poll`，也不绕过平台鉴权。
- PTY WebSocket 未进入默认 HTTP/SSE 契约；P2 只能按 `docs/standards/security.md` 新增受控 ticket + WebSocket 例外。ticket 创建也提供新平台 URL `/api/internal/platform/opencode-runtime/sessions/{sessionId}/terminal/tickets`，响应中的 `webSocketUrl` 会随调用入口返回旧或新 WebSocket path。

对应测试：

- `OpencodeRuntimeFacadeTest`：验证 facade runtime 调用不泄漏 generated DTO。
- `OpencodeRuntimeApplicationServiceTest`：验证 workspace directory、用户进程节点路由、固定节点 fallback、远端 session id、binding mismatch 自动重建、permission reply body、MCP resources/tools、config/provider OAuth/worktree/share/MCP auth 映射。
- `PlatformOpencodeRuntimeControllerTest`：验证平台路径统一响应、MCP tools 查询、session share、traceId 和可选用户主体透传。
- `AgentOpencodeRuntimeControllerTest`：验证 `/api/internal/agent/opencode/...` agent path 统一响应、agentId 选择、traceId 和可选用户主体透传。
- `RuntimeControllerTest`：验证 `/api/internal/agent/opencode/runs` 与旧 Run URL 共享 DTO、错误格式和 service 实现。

- 首次 Run：先校验当前用户已有 `READY` opencode 进程，再通过该进程投影出的 execution node 创建远端 session，保存 `agent_session_bindings`；`opencode` 兼容字段 `sessions.opencode_session_id/opencode_execution_node_id` 暂时同步写入。
- 后续 Run：优先复用已保存且指向当前用户进程节点的同 agent 远端 session；若绑定节点与当前用户进程不一致，则重新创建远端 session 并覆盖绑定。用户进程不可用、节点不存在、离线或容量不可用时返回 `OPENCODE_UNAVAILABLE`。
- 本地集成默认只向 opencode 传 `directory=workspace.rootPath`，不把平台 `wrk_...` 作为 opencode `workspace` query 传入。

成功后写入 `run.created` 和 `run.started`。未找到可用节点返回 `OPENCODE_UNAVAILABLE`；opencode 超时或异常分别映射为平台 opencode 错误码。

`RunResponse`：`runId`、`sessionId`、`workspaceId`、`status`、`createdAt`、`updatedAt`，以及可选 `tokens`、`costUsd`。`tokens` 字段结构同 `SessionMessageResponse.tokens`。

`GET /api/sessions/{sessionId}/active-run` 返回最近的 `PENDING`、`RUNNING` 或 `CANCELLING` Run，供前端刷新后恢复 RunEvent SSE；没有非终态 Run 时响应仍为 `success=true` 且 `data=null`。

`POST /api/runs/{runId}/cancel` 对终态 Run 返回 `CONFLICT`。非终态 Run 会在存在 agent binding 时通过当前 `AgentRuntime.cancel` 取消远端执行，并追加 `run.cancelling`、`run.cancelled`；取消完成后也会触发一次消息快照持久化。

`GET /api/internal/agent/{agentId}/runs/{runId}/events` 返回 `text/event-stream`，旧 `GET /api/runs/{runId}/events` 和平台内部 URL 继续兼容。`event` 使用稳定 wire name。durable RunEvent 使用 `seq` 作为 SSE `id`，可通过 `Last-Event-ID` 续传；transient live output 不设置 SSE `id`，payload `seq=0`，不参与续传。浏览器原生 `EventSource` 首次续传可使用 `?lastEventId={seq}`，后端 header 优先、query 兜底。

SSE 建连时，后端会先尝试从当前 Run 绑定的 agent remote session 拉取消息快照，并转换为 transient `message.updated` / `message.part.updated` 发给前端；当前 `opencode` 实现读取 projected messages。随后进入 `run_events` durable replay 与 live bus 合流。消息内容、文本 delta、大段日志和 bash/tool output 不从本平台数据库恢复；如果远端 session 不可用或拉取失败，后端跳过消息恢复，不阻断 Run 状态、Diff、permission/question 等 durable RunEvent 回放。

PTY WebSocket 不在上述默认 HTTP/SSE 契约内，已按 `docs/standards/security.md` 增加后端受控例外入口，前端仍不得直连 opencode server、SSH、sidecar 或任意主机。

- `POST /api/sessions/{sessionId}/terminal/tickets`：创建一次性 PTY ticket，仍返回 `ApiResponse<T>`。
- `GET /api/sessions/{sessionId}/terminal/ws?ticket=...`：仅用于 WebSocket upgrade，ticket 单次使用并短期过期。
- `POST /api/internal/platform/opencode-runtime/sessions/{sessionId}/terminal/tickets`：ticket 创建新平台 URL。
- `GET /api/internal/platform/opencode-runtime/sessions/{sessionId}/terminal/ws?ticket=...`：WebSocket upgrade 新平台 URL。

创建 ticket 的请求体为：

```json
{
  "workspaceId": "wrk_...",
  "cwd": "relative/path",
  "shell": null,
  "cols": 120,
  "rows": 32
}
```

当前后端实现限制：

- `workspaceId` 可省略，省略时使用 session 归属 workspace；显式传入时必须与 session 匹配。
- session 必须已绑定远端 opencode session/execution node，否则返回 `CONFLICT`。
- `cwd` 会归一化到 workspace root 内，越界或非目录返回 `FORBIDDEN`。
- `shell` 暂不允许前端覆盖；后端只使用运行环境默认 shell，避免把任意可执行文件路径暴露给 Web 输入。
- `cols`、`rows` 会按后端上限截断；ticket 默认 60 秒过期且只能使用一次。
- ticket 创建按 session/workspace 维度限流，超限返回统一错误 `RATE_LIMITED`。

ticket 响应 data：

```json
{
  "ticket": "tty_...",
  "expiresAt": "2026-06-19T13:00:00Z",
  "webSocketUrl": "/api/sessions/ses_.../terminal/ws?ticket=tty_..."
}
```

通过新平台 ticket URL 创建时，`webSocketUrl` 返回 `/api/internal/platform/opencode-runtime/sessions/{sessionId}/terminal/ws?ticket=...`。

WebSocket 消息使用 JSON envelope：

```json
{ "type": "input", "data": "npm test\n" }
{ "type": "resize", "cols": 120, "rows": 32 }
{ "type": "close", "reason": "user" }
{ "type": "output", "data": "...", "seq": 12 }
{ "type": "output", "data": "...", "seq": 13, "truncated": true }
{ "type": "warning", "code": "PTY_OUTPUT_TRUNCATED", "message": "terminal output truncated" }
{ "type": "exit", "code": 0, "seq": 14 }
{ "type": "error", "code": "PTY_DENIED", "message": "..." }
```

当前已覆盖后端 ticket、Origin、session/workspace/cwd、单次使用、ticket 创建限流、每 session 单 active PTY、input/resize 限速、output 截断、结构化审计、idle/hard timeout 和前端 terminal package 基础接入。已有 active PTY 时，新 WebSocket 会返回 `error` envelope，`code=CONFLICT`，并关闭连接；非法 client envelope 返回 `error` envelope，`code=VALIDATION_ERROR`；input/resize 超限返回 `error` envelope，`code=RATE_LIMITED`；idle/hard timeout 返回 `error` envelope，`code=PTY_TIMEOUT`。真实前端、后端、opencode server 三服务 E2E 仍是后续完成项。

### Diff API

Diff API 属于平台 Run 级能力。Controller 只调用 `RunDiffApplicationService`，不直接访问 Repository、generated SDK 或 agent server。

`GET /api/runs/{runId}/diff` 返回当前 Run 的 Diff：

```json
{
  "runId": "run_...",
  "files": [
    {
      "path": "tests/checkout.spec.ts",
      "patch": "@@ -1 +1 @@\n-old\n+new\n",
      "additions": 1,
      "deletions": 1,
      "status": "modified"
    }
  ]
}
```

读取顺序：

1. 优先使用该 Run 最新 `diff.proposed` 事件 payload 中的 `diff` 或 `files`；运行中的写文件工具完成时也会派生该事件，用于实时追踪文件变化。
2. 若事件中没有 Diff 且 Session 已绑定远端 agent session，则通过当前 `AgentRuntime.diff` 查询；`opencode` 实现适配到 opencode `sessionDiff`。
3. 若没有可用映射，返回空文件列表，不暴露内部 agent binding 或远端字段。

`POST /api/runs/{runId}/diff/accept` 不修改文件系统；语义为“保留当前工作区变更并追加平台事件”。响应：

```json
{
  "runId": "run_...",
  "action": "accept",
  "status": "accepted",
  "fileCount": 2
}
```

后端会追加 `diff.accepted` RunEvent，payload 至少包含 `action`、`status`、`fileCount`。

`POST /api/runs/{runId}/diff/reject` 语义为“拒绝本次 Run 对应消息产生的变更”。后端会从 RunEvent payload 中查找最近的远端 `messageID`，并通过当前 `AgentRuntime.rejectDiff` 执行回滚；`opencode` 实现适配到 opencode `sessionRevert`。成功后追加 `diff.rejected` RunEvent。

拒绝失败规则：

- 缺少 `messageID` 返回 `CONFLICT`。
- Session 未绑定远端 agent session 返回 `CONFLICT`。
- opencode 超时、不可用或异常仍映射为 `OPENCODE_TIMEOUT`、`OPENCODE_UNAVAILABLE` 或 `OPENCODE_BAD_GATEWAY`。

兼容性：

- Diff 文件对象可新增字段，前端必须忽略未知字段。
- 当前不支持 per-file 后端回滚；前端“当前文件接受/拒绝”只能作为当前选择和反馈，不承诺后端按文件应用。
- 不新增数据库 migration；接受/拒绝动作通过 append-only RunEvent 记录。

### 健康检查

Actuator health 由 Spring Boot Actuator 提供，数据库健康使用 Spring Boot/Druid 数据源；opencode nodes 由 `OpencodeNodesHealthIndicator` 调用 facade `health`；Redis 未启用时返回 disabled，启用后做 TCP 连通检查。

### 兼容性

- API 不暴露数据库 surrogate PK。
- API 不暴露 `agent_session_bindings`、`opencodeSessionId`、`opencodeExecutionNodeId` 或 generated SDK DTO；前端只依赖平台 Workspace、Session、Run、Cancel 和 RunEvent SSE。
- 旧 `/api/...` URL 保持兼容；新增 URL 只能作为并行入口补充，不能删除旧 URL。
- 响应 DTO 可以新增字段，前端必须忽略未知字段。
- 文件 API 初版不承诺 Git 状态、二进制预览、递归扫描和搜索。
- RunEvent payload 可以新增字段；事件 wire name 不可重命名。
