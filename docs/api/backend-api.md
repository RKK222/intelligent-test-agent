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

1. 前端只能通过 `backend-api` 访问 `test-agent-app`。
2. 前端不得直接访问 opencode server。
3. 后端不得直接返回 generated SDK DTO。
4. API 返回平台 DTO 和统一错误格式。
5. API 文档变更必须与 Controller、DTO、测试同步。

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
- 健康检查和观测性 API。

## Phase 02/03 内部能力说明

Phase 02/03 不新增对外 HTTP API，也不新增 Controller。新增的 Workspace、Session、Run、RunEvent、ExecutionNode、RoutingDecision 字段目前只作为后端内部领域和持久化边界使用，Phase 04 暴露 Runtime API 时再在本文件固化请求/响应 DTO。

内部字段兼容策略：

- `workspaceId`、`sessionId`、`runId`、`eventId`、`executionNodeId` 均保持带前缀字符串，不向前端暴露数据库 surrogate PK。
- RunEvent payload 允许新增字段，前端和后续 API DTO 必须按忽略未知字段处理。
- opencode 错误已在 `test-agent-opencode-client` 映射为平台 `OPENCODE_BAD_GATEWAY`、`OPENCODE_UNAVAILABLE`、`OPENCODE_TIMEOUT`，对外仍使用统一错误响应。
- 平台 Session 与远端 opencode Session 是不同概念；`opencodeSessionId` 和 `opencodeExecutionNodeId` 只作为后端内部映射保存，不进入 HTTP DTO。

## Phase 04 Runtime API

Phase 04 开始由 `test-agent-app` 暴露可联调 HTTP API。Controller 只做协议转换、参数校验和统一响应封装，业务编排进入 application service；Controller 不直接访问 Repository，也不直接调用 generated SDK。

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
| `GET` | `/api/workspaces/{workspaceId}` | 查询工作区详情。 |
| `GET` | `/api/workspaces/{workspaceId}/files` | 单层列目录。 |
| `GET` | `/api/workspaces/{workspaceId}/files/content` | 读取 UTF-8 文件。 |
| `PUT` | `/api/workspaces/{workspaceId}/files/content` | 保存 UTF-8 文件。 |
| `GET` | `/api/workspaces/{workspaceId}/files/status` | 查询文件基础状态。 |

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

### Session API

| 方法 | 路径 | 用途 |
|---|---|---|
| `POST` | `/api/sessions` | 创建会话。 |
| `GET` | `/api/workspaces/{workspaceId}/sessions` | 按工作区分页查询会话。 |
| `GET` | `/api/sessions/{sessionId}` | 查询会话详情。 |
| `POST` | `/api/sessions/{sessionId}/messages` | 追加会话消息。 |
| `GET` | `/api/sessions/{sessionId}/messages` | 分页读取会话消息。 |

`POST /api/sessions` 请求体：

```json
{
  "workspaceId": "wrk_...",
  "title": "new session"
}
```

`SessionResponse`：`sessionId`、`workspaceId`、`title`、`status`、`createdAt`、`updatedAt`。

`POST /api/sessions/{sessionId}/messages` 请求体：

```json
{
  "role": "USER",
  "content": "message text"
}
```

`SessionMessageResponse`：`messageId`、`sessionId`、`role`、`content`、`createdAt`。当前 role 使用 `USER`、`ASSISTANT`、`SYSTEM`。

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

`POST /api/runs` 请求体：

```json
{
  "sessionId": "ses_...",
  "prompt": "run prompt"
}
```

Phase 11 起请求体保持向后兼容，并预留以下可选字段：

```json
{
  "sessionId": "ses_...",
  "prompt": "run prompt",
  "parts": [
    { "type": "text", "text": "run prompt" },
    { "type": "file", "path": "src/App.tsx", "source": { "start": 1, "end": 20 } },
    { "type": "agent", "agentId": "build" }
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
- Agent/Model/Variant/Mode 属于运行态选择，不代表 Provider/server/settings 配置。
- P0 contract prework 阶段，后端已可接收上述字段并把 text parts 合成为当前 Run 编排使用的 prompt；file/agent/reference parts 的完整语义需等 `test-agent-opencode-client` facade 扩展后再透传。

启动流程会追加用户消息，创建 `PENDING` Run，再按平台 session 的内部 opencode 映射决定路由：

### Phase 11 opencode Web Runtime API

Phase 11 新增的 opencode Web App 运行态能力统一由 `OpencodeRuntimeController` 暴露。前端仍只调用平台 `/api/**`，后端通过 `OpencodeRuntimeApplicationService -> test-agent-opencode-client` facade 访问 opencode HTTP API，不返回 generated SDK DTO，不允许 Controller 直接调用 generated SDK。

运行态目录接口：

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/api/agents?workspaceId=` | 读取当前 workspace 的 Agent 列表。 |
| `GET` | `/api/models?workspaceId=` | 读取当前 workspace 的 Model 列表。 |
| `GET` | `/api/providers?workspaceId=` | 读取当前 workspace 的 Provider 只读列表。 |
| `GET` | `/api/commands?workspaceId=` | 读取可执行命令列表。 |
| `GET` | `/api/references?workspaceId=` | 读取可引用上下文目录。 |
| `GET` | `/api/fs/list?workspaceId=&path=` | 通过 opencode runtime 列目录。 |
| `GET` | `/api/fs/find?workspaceId=&query=` | 通过 opencode runtime 查找文件。 |
| `GET` | `/api/fs/read?workspaceId=&path=` | 通过 opencode runtime 读文件内容。 |
| `GET` | `/api/vcs/status?workspaceId=` | 读取 VCS 状态。 |
| `GET` | `/api/vcs/diff?workspaceId=&mode=working\|git\|branch&context=` | 读取 VCS Diff。 |
| `GET` | `/api/lsp/status?workspaceId=` | 读取 LSP 状态。 |
| `GET` | `/api/mcp/status?workspaceId=` | 读取 MCP 状态。 |
| `GET` | `/api/mcp/resources?workspaceId=` | 读取 MCP resource 目录，后端映射到 opencode `/experimental/resource`。 |
| `GET` | `/api/mcp/tools?workspaceId=&provider=&model=` | 读取 MCP/runtime tool 目录；带 provider/model 时返回工具 schema，否则返回 tool id 降级列表。 |

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
| `GET` | `/api/sessions/{sessionId}/permissions` | 读取 pending permission。 |
| `POST` | `/api/sessions/{sessionId}/permissions/{requestId}/reply` | 回复 permission，body 支持 `{ "decision": "once|always|reject" }`。 |
| `GET` | `/api/sessions/{sessionId}/questions` | 读取 pending question。 |
| `POST` | `/api/sessions/{sessionId}/questions/{requestId}/reply` | 回复 question，body 为 `{ "answers": [...] }`。 |
| `POST` | `/api/sessions/{sessionId}/questions/{requestId}/reject` | 拒绝 question。 |

兼容和安全约束：

- 所有响应仍包裹 `ApiResponse<T>`，错误仍走统一错误码和 traceId。
- `workspaceId` 为平台 workspace id，后端只把 workspace root 映射为 opencode `directory`；不得把平台 id 当作 opencode `workspace` query。
- `sessionId` 为平台 session id，后端通过内部 `opencodeSessionId` 和 `opencodeExecutionNodeId` 定位远端 session；未绑定远端 session 时返回 `CONFLICT`。
- `permission`/`question` 的平台路径保留在 `/api/sessions/{sessionId}/...` 下，后端实际映射到 opencode `/permission`、`/question` 族 API。
- 只读 transcript 页面 `/s/{sessionId}` 只消费平台 `GET /api/sessions/{sessionId}` 与 `GET /api/sessions/{sessionId}/messages`，不接 opencode 公网 `share_data/share_poll`，也不绕过平台鉴权。
- PTY WebSocket 未进入本轮接口；交互式终端必须先补架构和安全文档例外。

对应测试：

- `OpencodeRuntimeFacadeTest`：验证 facade runtime 调用不泄漏 generated DTO。
- `OpencodeRuntimeApplicationServiceTest`：验证 workspace directory、远端 session id、permission reply body、MCP resources/tools 映射。
- `OpencodeRuntimeControllerTest`：验证统一响应、MCP tools 查询和 traceId 透传。

- 首次 Run：先选择可用 execution node，调用 opencode `POST /session` 创建远端 session，保存 `opencodeSessionId` 与 `opencodeExecutionNodeId` 内部映射，然后用远端 session id 调用 opencode `prompt_async`。
- 后续 Run：复用已保存的远端 opencode session，并固定路由到原 execution node；节点不存在、离线或容量不可用时返回 `OPENCODE_UNAVAILABLE`。
- 本地集成默认只向 opencode 传 `directory=workspace.rootPath`，不把平台 `wrk_...` 作为 opencode `workspace` query 传入。

成功后写入 `run.created` 和 `run.started`。未找到可用节点返回 `OPENCODE_UNAVAILABLE`；opencode 超时或异常分别映射为平台 opencode 错误码。

`RunResponse`：`runId`、`sessionId`、`workspaceId`、`status`、`createdAt`、`updatedAt`。

`POST /api/runs/{runId}/cancel` 对终态 Run 返回 `CONFLICT`。非终态 Run 会在存在内部映射时使用远端 opencode session id 调用 opencode cancel，并追加 `run.cancelling`、`run.cancelled`。

`GET /api/runs/{runId}/events` 返回 `text/event-stream`，SSE `id` 使用 RunEvent `seq`，`event` 使用稳定 wire name。`Last-Event-ID` 续传规则见 `docs/api/event-stream-api.md`。浏览器原生 `EventSource` 首次续传可使用 `GET /api/runs/{runId}/events?lastEventId={seq}`，后端 header 优先、query 兜底。

### Phase 11 opencode Web App 运行态 API 规划

以下接口是 Phase 11 的新增契约方向，必须通过 `test-agent-app -> test-agent-opencode-client` facade 实现；未实现前不得由前端直连 opencode server。

| 域 | 方法与路径 | 用途 | 优先级 |
|---|---|---|---|
| Session | `GET /api/sessions` | 列表、搜索、分页、置顶过滤 | P0 |
| Session | `PATCH /api/sessions/{sessionId}` | 更新标题或置顶 | P0 |
| Session | `DELETE /api/sessions/{sessionId}` | 删除会话 | P0 |
| Session | `GET /api/sessions/{sessionId}/children` | 子会话列表 | P1 |
| Session | `GET /api/sessions/{sessionId}/diff` | session/message diff | P1 |
| Session | `GET /api/sessions/{sessionId}/todo` | Todo 列表 | P1 |
| Session | `POST /api/sessions/{sessionId}/fork` | 从会话或消息 fork | P1 |
| Session | `POST /api/sessions/{sessionId}/abort` | 中断当前运行 | P0 |
| Session | `POST /api/sessions/{sessionId}/compact` | 压缩/总结 | P1 |
| Session | `POST /api/sessions/{sessionId}/revert`、`/unrevert` | 撤销/重做 | P1 |
| Session | `POST /api/sessions/{sessionId}/command` | 斜杠命令 | P1 |
| Session | `POST /api/sessions/{sessionId}/shell` | shell 命令 | P1 |
| Permission | `GET /api/sessions/{sessionId}/permissions` | 待审批权限请求 | P0 |
| Permission | `POST /api/sessions/{sessionId}/permissions/{requestId}/reply` | once/always/reject | P0 |
| Question | `GET /api/sessions/{sessionId}/questions` | 待回答提问 | P0 |
| Question | `POST /api/sessions/{sessionId}/questions/{requestId}/reply`、`/reject` | 回复或拒绝提问 | P0 |
| Runtime | `GET /api/agents`、`/models`、`/providers` | Agent/Model/Provider 只读列表 | P0 |
| Runtime | `GET /api/commands`、`/references` | 命令和引用目录 | P1 |
| Runtime | `GET /api/fs/find`、`/fs/read`、`/fs/list` | context picker 文件能力 | P1 |
| Runtime | `GET /api/vcs/diff`、`/vcs/status` | VCS diff/status | P1 |
| Runtime | `GET /api/lsp/status` | LSP 状态 | P2 |
| Runtime | `GET /api/mcp/status`、`/mcp/resources`、`/mcp/tools` | MCP 状态和目录 | P2 |

PTY WebSocket 不在上述默认 HTTP/SSE 契约内；只有在架构和安全文档确认受控 WebSocket 例外后才能新增。

### Diff API

Diff API 属于平台 Run 级能力。Controller 只调用 `RunDiffApplicationService`，不直接访问 Repository、generated SDK 或 opencode server。

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

1. 优先使用该 Run 最新 `diff.proposed` 事件 payload 中的 `diff` 或 `files`。
2. 若事件中没有 Diff 且 Session 已绑定远端 opencode session，则通过 `OpencodeClientFacade.getDiff` 调用 opencode `sessionDiff`。
3. 若没有可用映射，返回空文件列表，不暴露内部 opencode 字段。

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

`POST /api/runs/{runId}/diff/reject` 语义为“拒绝本次 Run 对应消息产生的变更”。后端会从 RunEvent payload 中查找最近的 opencode `messageID`，并通过 `OpencodeClientFacade.rejectDiff` 调用 opencode `sessionRevert`。成功后追加 `diff.rejected` RunEvent。

拒绝失败规则：

- 缺少 `messageID` 返回 `CONFLICT`。
- Session 未绑定远端 opencode session 返回 `CONFLICT`。
- opencode 超时、不可用或异常仍映射为 `OPENCODE_TIMEOUT`、`OPENCODE_UNAVAILABLE` 或 `OPENCODE_BAD_GATEWAY`。

兼容性：

- Diff 文件对象可新增字段，前端必须忽略未知字段。
- 当前不支持 per-file 后端回滚；前端“当前文件接受/拒绝”只能作为当前选择和反馈，不承诺后端按文件应用。
- 不新增数据库 migration；接受/拒绝动作通过 append-only RunEvent 记录。

### 健康检查

Actuator health 由 Spring Boot Actuator 提供，数据库健康使用 Spring Boot/Druid 数据源；opencode nodes 由 `OpencodeNodesHealthIndicator` 调用 facade `health`；Redis 未启用时返回 disabled，启用后做 TCP 连通检查。

### 兼容性

- API 不暴露数据库 surrogate PK。
- API 不暴露 `opencodeSessionId`、`opencodeExecutionNodeId` 或 generated SDK DTO；前端只依赖平台 Workspace、Session、Run、Cancel 和 RunEvent SSE。
- 响应 DTO 可以新增字段，前端必须忽略未知字段。
- 文件 API 初版不承诺 Git 状态、二进制预览、递归扫描和搜索。
- RunEvent payload 可以新增字段；事件 wire name 不可重命名。
