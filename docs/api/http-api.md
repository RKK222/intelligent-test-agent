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
6. 旧 `/api/...` URL 默认保留；已明确作废的入口除外。新增 URL 与旧 URL 并行暴露时，不重定向、不删除。
7. CORS 本地默认仅覆盖主前端与 `frontend-opencode` 的 localhost/127.0.0.1 开发、预览和 real E2E 端口；生产必须通过 `TEST_AGENT_CORS_ALLOWED_ORIGINS` 显式配置允许来源。

## API URL 分层

同一业务能力可以同时暴露旧 URL 和新 URL。新旧 URL 必须共享同一 DTO、鉴权、traceId、限流、错误格式和业务实现。

| URL 前缀 | 用途 |
|---|---|
| `/api/...` | 旧兼容入口，当前前端和历史调用方继续可用。 |
| `/api/internal/platform/{business-project}/{business}/...` | 前端调用平台自身能力的新入口。 |
| `/api/internal/agent/{agentId}/...` | 与具体 agent 交互的新入口，`agentId` 由前端 URL 传递；当前唯一可运行值为 `opencode`。 |
| `/api/internal/platform/opencode-runtime/manager-backends` | opencode-manager 兼容诊断入口，使用独立 manager token；Go manager 运行路径不调用。 |
| `/api/internal/platform/opencode-runtime/management/overview` | 超级管理员只读运行管理入口，使用用户 JWT 且要求 `SUPER_ADMIN`。 |
| `/api/internal/platform/scheduler-management` | 超级管理员定时任务管理入口，使用用户 JWT 且要求 `SUPER_ADMIN`。 |
| `/api/internal/platform/system-management` | 超级管理员用户管理（测试）入口，使用用户 JWT 且要求 `SUPER_ADMIN`。 |
| `/api/public/...` | 其他系统调用平台的公开 API，当前预留；新增前必须完成鉴权、限流、安全和兼容性设计。 |

当前已落地的新平台入口：

| 业务工程 | 新 URL 示例 | 旧 URL 示例 |
|---|---|---|
| `workspace-management` | `/api/internal/platform/workspace-management/workspaces` | `/api/workspaces` |
| `workspace-management` | `/api/internal/platform/workspace-management/workspaces/{workspaceId}/files/content` | `/api/workspaces/{workspaceId}/files/content` |
| `workspace-management` | `/api/internal/platform/workspace-management/file-ws/tickets` | 无旧 URL |
| `workspace-management` | `/api/internal/platform/workspace-management/agent-config/public/status` | 无旧 URL |
| `workspace-management` | `/api/internal/platform/workspace-management/agent-config/operations/{operationId}/tickets` | 无旧 URL |
| `workspace-management` | `/api/internal/platform/workspace-management/backend-servers` | 无旧 URL |
| `workspace-management` | `/api/internal/platform/workspace-management/applications` | 无旧 URL |
| `workspace-management` | `/api/internal/platform/workspace-management/applications/{appId}/workspace-templates/{templateId}/versions` | 无旧 URL |
| `workspace-management` | `/api/internal/platform/workspace-management/workspace-versions/{versionId}/personal-workspaces` | 无旧 URL |
| `opencode-runtime` | `/api/internal/platform/opencode-runtime/sessions` | `/api/sessions` |
| `opencode-runtime` | `/api/internal/platform/opencode-runtime/runs` | `/api/runs` |
| `opencode-runtime` | `/api/internal/platform/opencode-runtime/runs/{runId}/events` | `/api/runs/{runId}/events` |
| `opencode-runtime` | `/api/internal/platform/opencode-runtime/runs/{runId}/session-tree/messages` | `/api/runs/{runId}/session-tree/messages` |
| `opencode-runtime` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/session-tree/messages` | `/api/sessions/{sessionId}/session-tree/messages` |
| `opencode-runtime` | `/api/internal/platform/opencode-runtime/agents` | `/api/agents` |
| `opencode-runtime` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/terminal/tickets` | `/api/sessions/{sessionId}/terminal/tickets` |
| `opencode-runtime` | `/api/internal/platform/opencode-runtime/management/overview` | 无旧 URL |
| `opencode-runtime` | `/api/internal/platform/opencode-runtime/management/containers/{containerId}/processes/{port}/restart` | 无旧 URL |
| `opencode-runtime` | `/api/internal/platform/opencode-runtime/management/containers/{containerId}/processes/{port}/stop` | 无旧 URL |
| `opencode-runtime` | `/api/internal/platform/opencode-runtime/messages/{messageId}/feedback` | 无旧 URL |
| `opencode-runtime` | `/api/internal/platform/opencode-runtime/messages/{messageId}/feedback/me` | 无旧 URL |
| `analytics` | `/api/internal/platform/analytics/overview` | 无旧 URL |
| `analytics` | `/api/internal/platform/analytics/timeseries` | 无旧 URL |
| `analytics` | `/api/internal/platform/analytics/peaks` | 无旧 URL |
| `analytics` | `/api/internal/platform/analytics/users` | 无旧 URL |
| `analytics` | `/api/internal/platform/analytics/organizations` | 无旧 URL |
| `analytics` | `/api/internal/platform/analytics/satisfaction` | 无旧 URL |
| `analytics` | `/api/internal/platform/analytics/exceptions` | 无旧 URL |
| `analytics` | `/api/internal/platform/analytics/export` | 无旧 URL |
| `scheduler-management` | `/api/internal/platform/scheduler-management/tasks` | 无旧 URL |
| `scheduler-management` | `/api/internal/platform/scheduler-management/runs` | 无旧 URL |
| `system-management` | `/api/internal/platform/system-management/users` | 无旧 URL |
| `system-management` | `/api/internal/platform/system-management/roles` | 无旧 URL |
| `configuration-management` | `/api/internal/platform/configuration-management/applications` | 无旧 URL |
| `configuration-management` | `/api/internal/platform/configuration-management/personal/ssh-keys` | 无旧 URL |
| `configuration-management` | `/api/internal/platform/configuration-management/workspace-create-operations/{operationId}` | 无旧 URL |
| `configuration-management` | `/api/internal/platform/configuration-management/common-parameters` | 无旧 URL |

当前已落地的 agent-scoped 入口示例：

| 新 URL | 平台业务实现 |
|---|---|
| `/api/internal/agent/{agentId}/runs` | 启动 Run；默认前端传 `opencode`。 |
| `/api/internal/agent/{agentId}/runs/{runId}/events` | 订阅 RunEvent SSE。 |
| `/api/internal/agent/{agentId}/runs/{runId}/session-tree/messages` | 查询当前 Run scope 的 root + child session message snapshot。 |
| `/api/internal/agent/{agentId}/sessions/{sessionId}/session-tree/messages` | 查询 root session 下全量历史 session tree message snapshot。 |
| `/api/internal/agent/{agentId}/runs/{runId}/diff` | 查询 Run 级 Diff。 |
| `/api/internal/agent/{agentId}/processes/me` | 查询或初始化当前用户的 opencode 进程。 |
| `/api/internal/agent/{agentId}/processes/me/initialize-operations/{operationId}` | 只读查询当前用户 opencode 进程初始化进度。 |
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

## 当前用户 opencode 进程 API

Base URL：`/api/internal/agent/{agentId}/processes/me`，当前 `agentId` 只支持 `opencode`。所有接口要求已登录用户，使用统一 `ApiResponse` / `ApiErrorResponse` 和 `X-Trace-Id`。

| 方法 | 路径 | 用途 | 请求体 | 响应 |
|---|---|---|---|---|
| `GET` | `/` | 查询当前用户 opencode 进程强健康状态，不自动启动。 | 无 | `UserOpencodeProcessResponse` |
| `POST` | `/initialize` | 初始化或重建当前用户 opencode 进程。 | 可空；可传 `{ "operationId": "opi_..." }` 开启进度记录。 | `UserOpencodeProcessResponse` |
| `GET` | `/initialize-operations/{operationId}` | 只读查询当前用户发起的初始化进度；不触发 manager health/start，不写 RunEvent。 | 无 | `OpencodeProcessStartOperationResponse` |

`operationId` 由前端生成，格式为 `opi_` 开头，后续 8 到 120 位字母、数字、下划线或短横线；旧客户端不传 `operationId` 时 `POST /initialize` 保持同步返回兼容。

`OpencodeProcessStartOperationResponse`：

```json
{
  "operationId": "opi_1234567890abcdef",
  "status": "RUNNING",
  "currentStep": "HEALTH_CHECKING",
  "steps": [
    { "step": "VALIDATING_REQUEST", "code": "VALIDATING_REQUEST", "name": "校验请求", "status": "SUCCEEDED" },
    { "step": "HEALTH_CHECKING", "code": "HEALTH_CHECKING", "name": "健康检查", "status": "RUNNING" }
  ],
  "errorCode": null,
  "errorMessage": null,
  "processId": null,
  "serviceAddress": null,
  "traceId": "trace_1234567890abcdef",
  "createdAt": "2026-07-02T12:00:00Z",
  "updatedAt": "2026-07-02T12:00:03Z"
}
```

步骤顺序固定为：`VALIDATING_REQUEST`、`CHECKING_ASSIGNMENT`、`SELECTING_CONTAINER`、`PREPARING_STARTUP`、`STARTING_PROCESS`、`SAVING_CANDIDATE`、`CHECKING_PROCESS`、`HEALTH_CHECKING`、`SAVING_BINDING`、`COMPLETED`。失败时 `status=FAILED`，`currentStep` 停留在失败步骤，`errorCode/errorMessage/traceId` 可直接展示给前端。

对应测试：`RuntimeControllerTest`、`UserOpencodeProcessAssignmentServiceTest`、`MyBatisOpencodeProcessStartOperationRepositoryIntegrationTest`、`backend-api.test.ts`、`OpencodeProcessStartupDialog.test.ts`。

## AI 回复反馈 API

Base URL：`/api/internal/platform/opencode-runtime/messages`。该能力只写满意度反馈事实，不同步刷新运营汇总。

鉴权：任意已登录用户。后端会校验目标消息存在、角色为 `ASSISTANT`，且当前用户是会话创建人或关联 Run 的触发人；不满足时返回 `FORBIDDEN` 或 `VALIDATION_ERROR`。

| 方法 | 路径 | 用途 | 请求体 | 响应 |
|---|---|---|---|---|
| `PUT` | `/{messageId}/feedback` | 提交或更新当前用户对 assistant 消息的反馈 | `{ "rating": "POSITIVE|NEGATIVE", "reasonCode": "WRONG_ANSWER|NOT_HELPFUL|DID_NOT_FOLLOW_INSTRUCTION|CODE_QUALITY_LOW|TEST_RESULT_BAD|TOO_SLOW|TOO_VERBOSE|TOO_SHORT|OTHER"?, "comment": string? }`，`comment` 最多 300 字 | `FeedbackResponse` |
| `GET` | `/{messageId}/feedback/me` | 查询当前用户对该消息的反馈 | 无 | `FeedbackResponse` 或 `null` |

`FeedbackResponse`：

```json
{
  "feedbackId": "fb_...",
  "messageId": "msg_...",
  "sessionId": "ses_...",
  "runId": "run_...",
  "rating": "NEGATIVE",
  "reasonCode": "WRONG_ANSWER",
  "comment": "不准确",
  "createdAt": "2026-06-28T00:00:00Z",
  "updatedAt": "2026-06-28T00:00:00Z"
}
```

对应测试：`AiMessageFeedbackControllerTest`、`AiMessageFeedbackApplicationServiceTest`。

## 运营分析 API

Base URL：`/api/internal/platform/analytics`。所有接口要求 `SUPER_ADMIN`，普通管理员和匿名用户分别返回 `FORBIDDEN`、`UNAUTHENTICATED`。查询接口只读 rollup 表；失败或延迟时通过响应中的 `freshness.status=STALE|FAILED` 标记最近成功数据，不在 API 请求时扫描原始事实宽表。

通用 query 参数：`startTime`、`endTime`、`granularity=hour|day|week|month`、`organization`、`rdDepartment`、`department`、`userId`、`agentId`、`model`、`workspaceId`、`topN`、`page`、`pageSize`、`sort`。约束：`topN<=100`，`pageSize<=100`，趋势点数 `<=500`；`hour` 粒度最多 48 小时，`day` 粒度最多 180 天。

| 方法 | 路径 | 用途 | 响应 |
|---|---|---|---|
| `GET` | `/overview` | 用户规模、漏斗、使用强度、Run 结果、满意度、Diff 采纳、token 强度与 freshness | `AnalyticsOverview` |
| `GET` | `/timeseries` | 按 hour/day/week/month 聚合趋势 | `AnalyticsTimeSeriesPoint[]` |
| `GET` | `/peaks` | 峰值时段和小时热力 | `AnalyticsPeaks` |
| `GET` | `/users` | 用户使用明细和排行，支持 `sort=active|runs|successRate|satisfactionRate|diffAcceptanceRate|cancelRate|negativeFeedback|tokenUsage` | `PageResponse<AnalyticsUserUsageRow>` |
| `GET` | `/organizations?groupBy=organization|rdDepartment|department` | 组织维度排行 | `AnalyticsOrganizationUsageRow[]` |
| `GET` | `/satisfaction` | 满意率、反馈覆盖率、负反馈原因分布和反馈明细 | `AnalyticsSatisfaction` |
| `GET` | `/exceptions` | 失败/取消 Run 明细，不返回 prompt 或 assistant 原文 | `PageResponse<AnalyticsExceptionDetail>` |
| `GET` | `/export?type=overview|timeseries|users|organizations|feedback|exceptions` | CSV 导出；不导出 prompt 原文、assistant 原文或 cost/costUsd 字段 | `text/csv` |

核心口径：满意率为 `positive/(positive+negative)`，无反馈时为 `null`；反馈覆盖率为 `(positive+negative)/assistantMessageCount`；Diff 采纳率为 `diffAccepted/diffProposed`，无 proposed 时为 `null`；p95 耗时基于 `analytics_run_duration_histogram_hourly` 近似计算；token 仅包含 input/output/reasoning/total，不统计、不展示、不导出费用字段。

对应测试：`AnalyticsControllerTest`、`AnalyticsQueryServiceTest`、`analytics-management-panel.test.ts`。

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

Git 命令返回 `GIT_UNAVAILABLE` 时，错误 `details` 可能包含 `gitFailureType` 和 `gitFailureHint`，用于区分认证失败、仓库不可访问、网络连接失败、分支不存在、worktree 冲突或未知失败；`gitFailureHint` 是可展示给管理员的安全排查提示，`stderr` 和 `command` 仅用于后端排查，不应在普通 UI 中直接展示。

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

## Agent 配置管理 API

Base URL：`/api/internal/platform/workspace-management/agent-config`。该能力管理工作台左侧 Agent 栏目中的“公共级”和“工作空间级”opencode 配置文件。公共级 Git 根目录来自通用参数 `OPENCODE_PUBLIC_CONFIG_GIT_ROOT`，opencode 运行时配置目录来自 `OPENCODE_PUBLIC_CONFIG_DIR`，文件树根为 Git 根目录下 `opencode/`；工作空间级文件树根为 `{workspace.rootPath}/.opencode/`。前端在该根下管理 `agents/` Agent Markdown 和 `skills/<skill-name>/SKILL.md` 技能包，写入路径必须显式带 `agents/` 或 `skills/` 前缀。

鉴权：

- `GET status/files/content`：任意已登录用户可读。
- 公共级和工作空间级的写入、更新、worktree、diff、stage、commit、publish：要求 `SUPER_ADMIN`。
- 所有 SSH Git 操作使用当前登录 `SUPER_ADMIN` 保存的唯一 SSH key；未配置或配置多把时返回 `VALIDATION_ERROR`。
- Agent 配置文件的目录列表、读取、写入前端必须走平台文件 WebSocket；HTTP `files` 入口仅保留兼容，本地执行，远端 worktree 返回 `CONFLICT` 并提示使用 WebSocket。

公共级接口：

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/public/status` | 查询公共 Agent Git 是否启用、根目录、agent 目录、当前分支和 commit。 |
| `GET` | `/public/branches` | 使用当前登录用户唯一 SSH key 实时查询公共 Agent Git 远端分支，不缓存。 |
| `GET` | `/public/repositories` | 查询 Redis 中当前仍在线的后端服务器及当前后端的公共配置仓库初始化状态；在线 Java 快照按 `linuxServerId` 合并，响应不包含目标后端 `listenUrl`。 |
| `GET` | `/public/repositories/local` | 目标后端本机状态查询入口，仅供后端到后端代理使用。 |
| `POST` | `/public/repositories/{linuxServerId}/initialize` | 通过当前后端代理到目标服务器，用当前登录用户唯一 SSH key 初始化或刷新该服务器本地公共配置仓库。 |
| `POST` | `/public/update` | 按分支 clone/fetch/checkout/pull 公共配置并广播其他服务器同步；工作树有已跟踪修改时必须显式确认恢复。 |
| `POST` | `/public/update-and-push` | 公共配置"提交并推送"复合操作：stage 工作区全部变更并用 `commitMessage` 生成一次提交，再 `git push` 到远端；`discardLocalChanges=true` 时先 `git reset --hard HEAD` 放弃受控仓库中的已跟踪修改。本接口不预拉取远端内容，避免覆盖本地未提交修改。 |
| `POST` | `/file-ws-route` | 查询 Agent 配置文件 WebSocket 应连接的目标后端，body 包含 `scope`、`workspaceId?`、`worktreeId?`、`linuxServerId?`。 |
| `GET` | `/public/files?path=&worktreeId=` | 兼容保留：列出本机公共 opencode 配置目录一层子项，前端不再使用。 |
| `GET` | `/public/files/content?path=&worktreeId=` | 兼容保留：读取本机公共 opencode 配置文件，前端不再使用。 |
| `PUT` | `/public/files/content` | 兼容保留：写入本机公共 opencode 配置文件，前端不再使用。 |
| `POST` | `/public/worktrees` | 在请求指定且已初始化的 `linuxServerId` 上创建公共配置 git worktree，后端自动拼接 `-yyyyMMdd`；目标服务器本地 Git 根目录未初始化时返回 `CONFLICT`，不在创建 worktree 时 clone。 |
| `GET` | `/public/worktrees?linuxServerId=` | 查询指定已初始化服务器上的 `ACTIVE` 公共配置 worktree 切换选项；仅 `SUPER_ADMIN` 可调用，响应包含创建人 ID 和用户名。 |
| `GET` | `/public/diff?worktreeId=` | 查询 Git 变更文件和 patch；后端复用公共 porcelain 解析与 diff 聚合，保留 Git 原始状态简写。 |
| `POST` | `/public/stage` / `/public/unstage` | 暂存或取消暂存文件。 |
| `POST` | `/public/commit` | 提交当前暂存区。 |
| `POST` | `/public/publish` | 直接模式先校验 clean、fetch/pull --ff-only 后 push；worktree 模式先校验 clean、fetch/pull --ff-only，再 merge 本地 worktree 分支、push，并广播同步。 |

工作空间级接口把同名能力挂在 `/workspaces/{workspaceId}/...`，其中 `files/content/diff/stage/unstage/commit/publish/worktrees/status` 的语义与公共级一致；HTTP `files/content` 同样仅兼容保留，前端文件读写必须通过文件 WebSocket。物理目录为当前运行态 Workspace 或指定 worktree 下的 `.opencode/`，但普通工作空间文件树不重复展示根级 `.opencode`。工作空间级 `diff` 只返回 `.opencode/agents` 与 `.opencode/skills` 下的变更，响应 path 会去掉 `.opencode/` 前缀。工作空间新增配置包时只初始化应用技能包 `skills/<name>/SKILL.md`、`skills/<name>/rules/README.md` 和 `skills/<name>/templates/README.md`，不自动创建 Agent 入口。

公共级和工作空间级 worktree publish 发生 merge 冲突时返回 HTTP 409、错误码 `CONFLICT`，`details.conflictFiles` 携带可展示的冲突文件列表；后端会在返回前执行 `git merge --abort`，不 push、不把 worktree 标记为已发布。`/public/update-and-push` 保持不预拉取远端内容的既有契约，不纳入该 publish workflow。

长操作进度：

| 方法/路径 | 用途 |
|---|---|
| `POST /operations/{operationId}/tickets` | 为 Agent 配置进度 WebSocket 签发一次性 ticket。 |
| `WS /operations/{operationId}/ws?ticket=agt_...` | 推送 `snapshot`、`step`、`completed`、`failed` 消息；`step` 消息可携带 `command` 表示当前正在执行的安全 Git 命令文本。 |
| `GET /operations/{operationId}` | 查询当前 operation 快照。 |

`POST /public/update` 请求体：

```json
{
  "branch": "main",
  "operationId": "aco_1234567890abcdef",
  "discardLocalChanges": false
}
```

`discardLocalChanges` 可选且默认 `false`。公共仓库存在已跟踪文件修改（包括误删）时，默认返回 `CONFLICT`；只有超级管理员在页面明确勾选放弃本地修改并传 `true` 后，后端才执行 `git reset --hard HEAD` 再 fetch/checkout/pull。该操作不删除未跟踪文件。

`POST /public/update-and-push` 请求体：

```json
{
  "branch": "main",
  "commitMessage": "chore: sync public agent docs",
  "operationId": "aco_1234567890abcdef",
  "discardLocalChanges": false
}
```

`commitMessage` 必填且不能为空字符串。该接口**不**预拉取远端内容，流程为：
1. 当 `discardLocalChanges=true` 且工作区有已跟踪修改时执行 `git reset --hard HEAD`（不删除未跟踪文件）；
2. `git add --all` 把工作区全部变更加入暂存；
3. 若产生新变更则用 `commitMessage` 生成一次提交；
4. 若产生了新提交则 `git push` 到 `branch`；否则视为幂等成功，仅返回当前 commit hash；
5. 广播 `agent-config.public-sync-requested` 事件并返回最新 commit hash。

该接口要求 `SUPER_ADMIN`，所有 Git 操作使用当前登录用户唯一 SSH key；提交说明为空返回 `VALIDATION_ERROR`，push 被远端拒绝（如 non-fast-forward）时返回 `INTERNAL_ERROR`，不修改仓库外的状态。

`GET /public/repositories` 响应元素字段：

响应按稳定 `linuxServerId` 唯一返回服务器行；同一服务器上 Java 进程重启导致 Redis TTL 窗口内同时存在多个 `backendProcessId` 快照时，后端会合并为一条公共配置仓库状态，前端也以 `linuxServerId` 作为去重 key。

```json
{
  "linuxServerId": "10.8.0.12",
  "serverName": "10.8.0.12",
  "gitRootPath": "/data/.testagent/agent-opencode/.config",
  "configDirPath": "/data/.testagent/agent-opencode/.config/opencode",
  "worktreeRootPath": "/data/.testagent/agent-opencode/.configdev",
  "status": "READY",
  "initialized": true,
  "initializationAllowed": true,
  "currentBranch": "main",
  "commitHash": "abc1234",
  "message": "已初始化"
}
```

`POST /public/worktrees` 请求体：

```json
{
  "baseName": "change-agent-md",
  "branch": "main",
  "linuxServerId": "10.8.0.12",
  "operationId": "aco_1234567890abcdef"
}
```

`GET /public/worktrees?linuxServerId=10.8.0.12` 响应元素字段：

```json
{
  "worktreeId": "agw_...",
  "scope": "PUBLIC",
  "workspaceId": null,
  "linuxServerId": "10.8.0.12",
  "worktreeName": "change-agent-md-20260628",
  "branch": "main",
  "rootPath": "/data/.testagent/agent-opencode/.configdev/change-agent-md-20260628",
  "agentDirectory": "/data/.testagent/agent-opencode/.configdev/change-agent-md-20260628/opencode/agents",
  "status": "ACTIVE",
  "createdAt": "2026-06-28T00:00:00Z",
  "updatedAt": "2026-06-28T00:00:00Z",
  "createdByUserId": "usr_admin",
  "createdByUsername": "admin"
}
```

`POST /file-ws-route` 请求体：

```json
{
  "scope": "PUBLIC",
  "workspaceId": null,
  "worktreeId": "agw_...",
  "linuxServerId": "10.8.0.12"
}
```

响应：

```json
{
  "scope": "PUBLIC",
  "workspaceId": null,
  "worktreeId": "agw_...",
  "linuxServerId": "10.8.0.12",
  "baseUrl": "http://10.8.0.12:8080",
  "webSocketPath": "/api/internal/platform/workspace-management/file/ws",
  "sameServer": false,
  "message": null
}
```

`POST /public/stage` 请求体：

```json
{ "files": ["opencode/agents/review.md"], "worktreeId": "agw_..." }
```

进度 WebSocket 消息：

```json
{
  "type": "step",
  "operationId": "aco_1234567890abcdef",
  "status": "RUNNING",
  "currentStep": "PUSHING",
  "traceId": "trace_...",
  "occurredAt": "2026-06-26T00:00:00Z"
}
```

兼容性：

- 公共 Git 地址参数 `OPENCODE_PUBLIC_AGENT_GIT_URL` 默认 `UNCONFIGURED`，此时公共 Git 更新、分支、worktree、diff/commit/publish 返回禁用或校验错误，并提示超级管理员先到“系统管理 → 通用参数管理”配置该参数；只读 status 仍可返回目录信息。
- 公共 Git origin 和 `opencode/` 配置目录有效时，即使工作树存在未提交变更，仓库仍返回 `initialized=true` 和 `status=CONFLICT`，文件树保持可浏览；更新操作按 `discardLocalChanges` 规则决定拒绝或恢复。
- `OPENCODE_PUBLIC_CONFIG_GIT_ROOT` 缺失或为空目录时，只允许初始化/公共更新流程 clone；公共 worktree 创建只校验目标服务器已有 Git 仓库，未初始化返回 `CONFLICT`，提示 `服务器{linuxServerId}上公共配置仓库在{gitRootPath}目录中未初始化。`
- 新建公共/工作空间 Agent worktree 均返回并保存 `linuxServerId`。公共 worktree 后续文件目录列表、读取、写入按落库服务器归属通过文件 WebSocket route/ticket/RPC 执行；diff/stage/unstage/commit/publish 仍按现有 HTTP 后端代理执行。
- 公共级切换 worktree 时，浏览器先用 `GET /public/repositories` 选择已初始化服务器，再用 `GET /public/worktrees?linuxServerId=` 选择该服务器上的 `ACTIVE` worktree；选择“直接公共配置目录”时清空 `worktreeId`，但仍必须把所选 `linuxServerId` 传给 `/file-ws-route` 绑定目标服务器。
- 历史 `agent_config_worktrees.linux_server_id is null` 记录按当前服务器兼容执行；如果本地目录不存在，管理员应重新创建 worktree。
- 公共 Git clone/fetch/pull/worktree 失败时仍返回统一 Git 错误码，但会在 `details.gitFailureHint` 中给出安全排查建议；浏览器可展示该提示和 `traceId`，不得展示原始 `stderr`、完整命令或内部路径。
- Agent 配置进度不走 RunEvent SSE，也不写入 `run_events`；浏览器只通过 ticket WebSocket 或 `GET /operations/{operationId}` 查看。多实例部署开启服务器广播后，执行实例会通过 `agent-config.operation-progress` 广播安全进度字段，让连接到非执行实例的页面也能收到进度；未启用广播时仍可通过 `GET /operations/{operationId}` 查看最终状态。Git 命令进度复用执行器记录点，在实际命令启动前发送，不通过轮询或额外 Git 查询获取。
- 内部服务器广播事件 `agent-config.public-sync-requested` 的 payload 只包含 `branch`、`commitHash`、`reason`，`agent-config.operation-progress` 只包含 operation/status/step/command/error/commit 等安全进度字段；envelope 继续携带 `traceId`，不携带文件内容、私钥、token、Authorization 或 Cookie。

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

Base URL：`/api/internal/platform/configuration-management`。除设置页创建应用工作空间接口会委托 workspace-management 创建初始版本工作区并执行 Git clone/checkout 外，本能力只产生和维护配置数据，不启动 Session/Run、不产生 RunEvent。

鉴权：

- 应用定义列表、个人 SSH key 接口和“把当前认证用户自己加入应用”只要求已登录用户；SSH key 只能管理自己的 key。
- 应用成员查询/删除、给其他用户加成员、代码库与工作空间配置接口需要已登录用户具备全局角色 `APP_ADMIN`；`SUPER_ADMIN` 继承该权限。角色来自 `user_roles + dictionaries(ROLE)`，不在 `user_roles` 增加 `application_id`。

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

普通用户只能提交当前认证用户自己的 `userId`，用于“加入其他应用”；提交其他用户返回 `FORBIDDEN`，`APP_ADMIN` / `SUPER_ADMIN` 可给其他用户加入应用。成员添加成功后前端只刷新已加入应用和可加入应用列表，不自动切换到新应用。

成员删除只更新 `application_members.deleted_at`，不影响平台用户、其他应用关系或后续运行态数据。

### 应用与代码库关联

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/repositories?page=&size=` | 分页查询代码库配置。 |
| `GET` | `/repository-types` | 查询版本库类型下拉选项，来源 `dictionaries(REPOSITORY_TYPE)`。 |
| `GET` | `/repository-deployment-options` | 查询版本库部署模式选项、默认模式和当前用户内部 SSH 前缀。 |
| `POST` | `/repositories` | 新增代码库配置。 |
| `PATCH` | `/repositories/{repoId}` | 编辑中文名称、英文名称；旧客户端传 `standard` 时继续兼容。 |
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
  "englishName": "demo",
  "deploymentMode": "EXTERNAL",
  "repositoryType": "TEST_WORK_REPOSITORY",
  "standard": true
}
```

`repositoryType` 可选；新客户端应优先传 `repositoryType`，取值来自 `GET /repository-types`：

```json
[
  { "typeCode": "TEST_WORK_REPOSITORY", "typeLabel": "测试工作库" },
  { "typeCode": "APPLICATION_CODE_REPOSITORY", "typeLabel": "应用代码库" },
  { "typeCode": "APPLICATION_ASSET_REPOSITORY", "typeLabel": "应用资产库" }
]
```

兼容旧客户端：未传 `repositoryType` 时，后端按 `standard=true` 推导为 `TEST_WORK_REPOSITORY`，否则推导为 `APPLICATION_CODE_REPOSITORY`。传入 `repositoryType=TEST_WORK_REPOSITORY` 时后端写入旧字段 `standard=true`，其它版本库类型写入 `standard=false`。

`GET /repository-deployment-options` 响应：

```json
{
  "defaultDeploymentMode": "INTERNAL",
  "internalSshPrefix": "ssh://001177621@",
  "options": [
    { "mode": "EXTERNAL", "label": "外部部署" },
    { "mode": "INTERNAL", "label": "内部部署" }
  ]
}
```

`PATCH /repositories/{repoId}` 请求体：

```json
{
  "name": "中文名称",
  "englishName": "demo",
  "standard": true
}
```

`CodeRepositoryResponse`：

```json
{
  "repositoryId": "repo_...",
  "gitUrl": "git@gitee.com:org/repo.git",
  "name": "中文名称",
  "englishName": "demo",
  "deploymentMode": "EXTERNAL",
  "repositoryType": "TEST_WORK_REPOSITORY",
  "repositoryTypeLabel": "测试工作库",
  "standard": true,
  "createdAt": "2026-06-26T00:00:00Z",
  "updatedAt": "2026-06-26T00:00:00Z"
}
```

约束：

- `gitUrl` 支持 SSH 和 HTTPS，创建后不可编辑且全局唯一；不提供代码库删除接口。
- `deploymentMode` 可选，默认 `EXTERNAL`。`EXTERNAL` 模式保持旧语义，`gitUrl` 保存完整 SSH/HTTPS Git 地址；`INTERNAL` 模式只保存 `host[:port]/path`，不保存 `ssh://统一认证号@`。
- `INTERNAL` 模式下前端展示只读前缀 `ssh://{当前用户统一认证号}@`，用户输入后半段；后端保存和列表响应均只返回后半段。分支、目录、clone、fetch、pull、push 等 Git 操作会按当前操作人动态拼接 `ssh://{unifiedAuthId}@{gitUrl}`，并用当前用户 SSH key。
- `INTERNAL` 模式下 `englishName` 为空时，后端按 Git 路径派生默认值：去掉可选 `.git`，把 `/` 替换为 `-` 并转小写，例如 `scm-share.sdc.cs.icbc:29418/hzefficiencytools/interfaceplatform` 派生为 `hzefficiencytools-interfaceplatform`。
- `englishName` 为必填英文名称，仅允许字母、数字和连字符，长度 1 到 128，且不能以连字符开头或结尾；后端统一按小写保存，非空值唯一。历史数据可能为 `null`，但缺少英文名称的历史代码库不能再创建新的应用版本工作区。
- `repositoryType` 由通用字典 `REPOSITORY_TYPE` 管理；编辑区只展示类型，不提供修改入口。`standard` 字段保留给存量工作空间逻辑，语义统一由版本库类型派生。
- HTTPS URL 不支持内嵌账号或 token；本期不做连通性校验。
- Git 目录读取不直接写业务配置；外部 SSH URL 和内部版本库会立即使用当前登录用户保存的唯一 SSH key。当前用户未配置 key 或远端不支持 `git archive --remote` 时返回统一 Git 错误。统一认证号不按敏感信息脱敏，SSH 私钥、token、Cookie、Authorization 仍不得出现在错误详情中。

### 应用工作空间

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/applications/{appId}/workspaces` | 查询当前应用工作空间配置。 |
| `POST` | `/applications/{appId}/workspaces` | 基于当前应用已关联代码库的分支和目录创建工作空间配置，并创建初始应用版本工作区。 |
| `PATCH` | `/applications/{appId}/workspaces/{workspaceId}` | 修改工作空间名称。 |
| `DELETE` | `/applications/{appId}/workspaces/{workspaceId}` | 删除工作空间配置。 |
| `GET` | `/workspace-create-operations/{operationId}` | 查询设置页创建应用工作空间进度。 |

`POST /applications/{appId}/workspaces` 请求体：

```json
{
  "repositoryId": "repo_...",
  "branch": "feature_testagent_20260707",
  "directoryPath": "src/main",
  "workspaceName": "main",
  "operationId": "wco_1234567890abcdef",
  "version": "20260707"
}
```

规则：

- `operationId` 可选；前端传入时用于进度轮询，格式为 `wco_` 前缀加 8 到 128 位字母、数字、下划线或短横线。
- 标准代码库必须选择形如 `feature_testagent_yyyyMMdd` 的分支，后端从分支名提取版本号。
- 非标准代码库必须传入 `version`，格式为 `yyyyMMdd`；标准代码库传入的 `version` 会被分支解析结果覆盖。
- 后端会先保存或复用 `应用 + 代码库 + 分支 + 目录路径` 对应的工作空间模板，再创建同版本的应用版本工作区并完成 Git clone/fetch、分支 checkout 和运行态 `Workspace` 创建。
- 创建前会按当前用户 READY 的 opencode 进程确定目标 `linuxServerId`，确保初始运行态工作区落在当前用户 agent 所在服务器。
- 应用版本工作区目录使用通用参数 `{OPENCODE_APP_WORKSPACE_ROOT}/{yyyymmdd}/{repository.englishName}/{directoryPath}`；缺少代码库英文名称时返回统一 `VALIDATION_ERROR`。
- 删除配置记录只删除模板配置，不级联清理已创建的应用版本工作区、个人工作区或运行态 `Workspace`。

响应 `ApplicationWorkspaceCreateResponse`：

```json
{
  "workspaceId": "awp_...",
  "appId": "app_...",
  "repositoryId": "repo_...",
  "branch": "feature_testagent_20260707",
  "directoryPath": "src/main",
  "workspaceName": "main",
  "createdAt": "2026-06-26T00:00:00Z",
  "updatedAt": "2026-06-26T00:00:00Z",
  "initialVersion": {
    "versionId": "awv_...",
    "version": "20260707",
    "branch": "feature_testagent_20260707",
    "repoRootPath": "/data/.testagent/agent-opencode/workspace/appworkspace/20260707/demo",
    "workspaceRootPath": "/data/.testagent/agent-opencode/workspace/appworkspace/20260707/demo/src/main",
    "runtimeWorkspace": {
      "workspaceId": "wrk_...",
      "rootPath": "/data/.testagent/agent-opencode/workspace/appworkspace/20260707/demo/src/main",
      "linuxServerId": "10.8.0.12"
    }
  }
}
```

`GET /workspace-create-operations/{operationId}` 响应：

```json
{
  "operationId": "wco_1234567890abcdef",
  "status": "RUNNING",
  "currentStep": "PREPARING_REPOSITORY",
  "steps": [
    { "code": "VALIDATING_INPUT", "name": "校验", "status": "SUCCEEDED" },
    { "code": "SAVING_TEMPLATE", "name": "保存配置", "status": "SUCCEEDED" },
    { "code": "RESOLVING_VERSION", "name": "解析版本", "status": "SUCCEEDED" },
    { "code": "PREPARING_REPOSITORY", "name": "下载代码", "status": "RUNNING" },
    { "code": "CREATING_RUNTIME_WORKSPACE", "name": "创建运行态工作区", "status": "PENDING" },
    { "code": "COMPLETED", "name": "完成", "status": "PENDING" }
  ],
  "errorCode": null,
  "errorMessage": null,
  "workspaceId": "awp_...",
  "versionId": null,
  "createdAt": "2026-06-26T00:00:00Z",
  "updatedAt": "2026-06-26T00:00:05Z"
}
```

进度查询只允许创建该操作的用户读取。进度状态通过 HTTP 轮询获取，不走 RunEvent SSE。

### 个人 SSH key

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/ssh-key/public-key` | 获取服务端 RSA 公钥（SPKI Base64），前端混合加密用，无需鉴权。 |
| `GET` | `/personal/ssh-keys` | 查看自己的 SSH key 元信息。 |
| `POST` | `/personal/ssh-keys` | 新增自己的唯一 SSH 私钥（前端已混合加密）。 |
| `DELETE` | `/personal/ssh-keys/{sshKeyId}` | 删除自己的 SSH key。 |

`POST /personal/ssh-keys` 请求体（前端先用 `GET /ssh-key/public-key` 取公钥，再用 AES-256-GCM 加密私钥、RSA-OAEP/SHA-256 加密临时 AES 密钥后提交密文）：

```json
{
  "name": "work",
  "encryptedPrivateKey": "AES-GCM 密文（Base64）",
  "encryptedAesKey": "RSA-OAEP 加密的临时 AES 密钥（Base64）",
  "encryptionNonce": "AES-GCM nonce（Base64）",
  "fingerprint": "SHA256:<url-safe-base64-no-padding>"
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

私钥采用混合加密：前端 AES-256-GCM 加密私钥、RSA-OAEP 加密临时 AES 密钥，服务端 RSA 私钥（`classpath:rsa-private.key`，缺失时自动生成临时密钥）解出 AES 密钥再解密私钥并校验指纹，不回显明文或密文。每个用户最多保存一把 key，重复新增返回 `CONFLICT`。

## 通用参数管理 API

通用参数（`common_parameters`）保存跨模块共享的稳定运行参数（如 `SYS_DATA_ROOT_DIR`、`OPENCODE_APP_WORKSPACE_ROOT`、`OPENCODE_PUBLIC_AGENT_GIT_URL` 等路径与 Git 参数）。本接口只允许已认证用户且角色包含 `SUPER_ADMIN` 访问：未认证返回 `UNAUTHENTICATED`，非超级管理员返回 `FORBIDDEN`，非法平台值或空参数值返回 `VALIDATION_ERROR`，参数不存在返回 `NOT_FOUND`。**接口仅提供列表查询与「仅修改 value」的更新，不提供新增/删除**，保证通用参数集合稳定。

参数值支持变量引用：值中可使用 `${englishName}` 引用其他通用参数的值，读取时按调用方指定的上下文平台展开；`platform=all` 的参数可由调用方以当前 JVM 平台或目标平台作为上下文，因此也能引用平台参数。循环引用、缺失引用或超深度嵌套（上限 16 层）时保留字面 `${...}` 占位符不展开，不抛异常。运行态通过 `RepositoryCommonParameterValues` 直接查询数据库，不把通用参数缓存在 JVM 或 Redis 中；数据库修改后通过 `common-parameter.refresh-requested` 广播通知其他后端实例直接查库联动。

Base URL：`/api/internal/platform/configuration-management/common-parameters`

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/` | 分页查询全部通用参数（原始值），可按平台过滤。 |
| `PATCH` | `/{parameterId}` | 仅更新指定参数的 `value`，不修改其他字段。 |
| `GET` | `/{parameterId}/change-logs` | 查询指定参数的修改历史记录。 |

`GET /` 查询参数：

| 参数 | 说明 |
|---|---|
| `platform` | 可选平台过滤值：`windows`、`linux`、`macos`、`all`；缺失表示不过滤。非法值返回 `VALIDATION_ERROR`。 |
| `page` | 页码，默认 `1`。 |
| `size` | 分页大小，默认 `50`，上限沿用平台 `PageRequest` 的 `200`。 |

响应体（`PageResponse<CommonParameterResponse>`）：

```json
{
  "items": [
    {
      "parameterId": "param_opencode_app_workspace_root_linux",
      "englishName": "OPENCODE_APP_WORKSPACE_ROOT",
      "chineseName": "应用工作空间根目录",
      "parameterValue": "/data/test-agent/appworkspace",
      "platform": "linux",
      "editable": false,
      "createdAt": "2026-06-26T00:00:00Z",
      "updatedAt": "2026-06-26T10:00:00Z"
    }
  ],
  "page": 1,
  "size": 50,
  "total": 23
}
```

`PATCH /{parameterId}` 请求体：

```json
{
  "value": "/data/test-agent/appworkspace"
}
```

`value` 为空或空白返回 `VALIDATION_ERROR`；`parameterId` 不存在返回 `NOT_FOUND`；只读参数（`editable=false`）返回 `VALIDATION_ERROR`「该通用参数为只读参数，修改后将影响系统正常运行」。成功返回更新后的 `CommonParameterResponse`（单条，非分页）。响应中的 `parameterId` 为业务 ID（如 `param_opencode_app_workspace_root_linux`），不暴露数据库代理主键。修改成功后自动记录修改日志，包含修改前后的值、修改用户和修改时间。

兼容性：新增接口，对既有按 `findByEnglishNameAndPlatform` 读取的消费方无影响。

`GET /{parameterId}/change-logs` 返回指定参数的修改历史记录（响应体 `List<ChangeLogResponse>`，按修改时间倒序，最多返回 50 条）：

```json
[
  {
    "logId": "log_xxx",
    "parameterId": "param_opencode_app_workspace_root_linux",
    "oldValue": "/data/test-agent/old-appworkspace",
    "newValue": "/data/test-agent/appworkspace",
    "changedByUserId": "usr_xxx",
    "changedByUsername": "888888888",
    "traceId": "trace_xxx",
    "createdAt": "2026-06-28T10:00:00Z"
  }
]
```

`oldValue` 为修改前的值，首次修改或历史数据迁移时可能为 `null`；`newValue` 为修改后的值。`changedByUserId` 和 `changedByUsername` 为修改用户的 ID 和用户名，static token 或本地开发场景可能为 `null`。该接口仅用于审计追溯，不产生 RunEvent/SSE。

### manager 运行公共参数

通用参数表中的 `OPENCODE_MANAGER_MAX_PROCESSES`（`platform=all`，全局单值）是 opencode manager 的最大并发进程数权威来源；`OPENCODE_SESSION_DIR` 和 `OPENCODE_PUBLIC_CONFIG_DIR` 是 manager 启动用户 opencode server 时的 session/config 路径权威来源。manager 通过 WebSocket 控制面连入本服务器后端时，后端在注册成功后只返回 `registered`；manager 收到后主动发送 `configRequest` 拉取完整配置。连接异常断开后，manager 按重连间隔无限重连，重连成功后重新发送 `configRequest`。前端仅允许修改 `editable=true` 的通用参数（`OPENCODE_MANAGER_MAX_PROCESSES`、`OPENCODE_PUBLIC_AGENT_GIT_URL`）；其余为只读部署/初始化参数，修改将影响系统正常运行，接口对只读参数的更新返回 `VALIDATION_ERROR`。对 `OPENCODE_PUBLIC_AGENT_GIT_URL` 的修改同样发布 `common-parameter.refresh-requested` 跨实例广播以保证 DB 一致，但不触发 manager 热刷新（URL 属部署参数，由 AgentConfig 在下次公共配置操作时直读 DB 生效）。最大进程数 `PATCH` 成功后发布进程内 `CommonParameterReloadedEvent`，后端经控制面 WebSocket 把只包含 `maxProcesses` 的 `configUpdate` 广播给当前 Java 实例持有的本服务器 manager 连接；多实例部署下，广播器同时发布 `common-parameter.refresh-requested` 跨实例广播，每台实例收到事件后直接从数据库读取最新最大进程数并只向本服务器 manager 下发。响应中的 `editable` 字段标识是否允许前端修改：`true` 可改，`false` 只读。

manager 收到后按自身端口池容量 `PortEnd-PortStart+1` 做 clamp（超上限 clamp 到容量、`<1` 拒绝）并热更新，成功应用 `configUpdate` 后立即补发一次 `managerHeartbeat` 上报生效值，后续周期 heartbeat 继续同步，`opencode_containers.max_processes` 随之更新。首次或重连后完整 `configUpdate` 中，`OPENCODE_SESSION_DIR` 会拼接端口作为 `XDG_DATA_HOME={sessionRoot}/{port}`，`OPENCODE_PUBLIC_CONFIG_DIR` 会作为 `OPENCODE_CONFIG_DIR`；两者均使用通用参数 `resolvedValue`，不会把字面 `$HOME` 下发给 manager。任一必需参数缺失、空白或最大进程数非正整数时，后端不会下发可启动配置，manager 保持未 ready 并拒绝 `start`/`restart`，不会回退 `OPENCODE_SESSION_ROOT`、`OPENCODE_CONFIG_DIR` 或 `OPENCODE_MANAGER_MAX_PROCESSES` 环境变量。该下发通道不产生 RunEvent/SSE，不向前端推送。

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
- CORS 本地默认允许 `http://localhost:3000` 和 `http://127.0.0.1:3000`；`guo` 等本地 profile 可通过 `TEST_AGENT_CORS_ALLOWED_ORIGINS` 覆盖，根目录启动脚本在局域网 IP 访问时会自动追加实际前端 origin。生产环境必须通过配置显式设置允许来源。

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
| `GET` | `/api/workspaces` | 分页列出工作区。 |
| `GET` | `/api/workspaces/{workspaceId}` | 查询工作区详情。 |
| `POST` | `/api/workspaces/{workspaceId}/file-ws-route` | 查询当前工作区文件 WebSocket 应连接的目标后端。 |
| `GET` | `/api/workspaces/{workspaceId}/files` | 兼容保留：单层列目录，前端工作区文件操作不再使用。 |
| `GET` | `/api/workspaces/{workspaceId}/files/content` | 兼容保留：读取 UTF-8 文件，前端工作区文件操作不再使用。 |
| `PUT` | `/api/workspaces/{workspaceId}/files/content` | 兼容保留：保存 UTF-8 文件，前端工作区文件操作不再使用。 |
| `GET` | `/api/workspaces/{workspaceId}/files/status` | 兼容保留：查询文件基础状态，前端工作区文件操作不再使用。 |

新平台 URL 使用 `/api/internal/platform/workspace-management` 前缀。例如：

| 方法 | 新路径 |
|---|---|
| `POST` | `/api/internal/platform/workspace-management/workspaces/{workspaceId}/file-ws-route` |
| `GET` | `/api/internal/platform/workspace-management/backend-servers` |
| `POST` | `/api/internal/platform/workspace-management/file-ws/tickets` |
| `GET` | `/api/internal/platform/workspace-management/workspaces/{workspaceId}/files` |
| `GET` | `/api/internal/platform/workspace-management/workspaces/{workspaceId}/files/content` |
| `PUT` | `/api/internal/platform/workspace-management/workspaces/{workspaceId}/files/content` |

普通前端不再通过 HTTP 传入物理目录注册 Workspace。应用版本工作区和个人工作区由后端根据应用、模板、版本、个人工作区等 id 读取通用参数并派生物理目录；仅超级管理员服务器工作空间选择器可通过目标后端文件 WebSocket ticket 在目标服务器上创建运行态 Workspace。

`WorkspaceResponse`：

```json
{
  "workspaceId": "wrk_...",
  "name": "demo",
  "rootPath": "/absolute/workspace/path",
  "linuxServerId": "127.0.0.1",
  "status": "ACTIVE",
  "createdAt": "2026-06-19T00:00:00Z",
  "updatedAt": "2026-06-19T00:00:00Z"
}
```

超级管理员服务器工作空间选择器通过文件 WebSocket `directory.list` 获取目标后端服务器上的一层目录，不暴露普通用户本机目录选择入口。响应 `WorkspaceDirectoryListResponse`：

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

`POST /api/workspaces/{workspaceId}/file-ws-route` 使用当前登录用户的 `opencode` 进程服务器归属定位同服务器后端，返回浏览器应直连的目标后端地址。该路由查询只读取 ACTIVE binding 和可恢复进程记录，不下发 opencode-manager `health` 或 `start` 命令；工作区服务器归属、用户 opencode 进程服务器和目标后端服务器不一致时返回统一 `CONFLICT`。本地服务器身份变化或切换测试库后，若历史 workspace 仍绑定旧 `linuxServerId`，且旧服务器没有在线后端快照、当前 opencode 进程在本后端、workspace 根目录在本机可访问，后端会在路由时把 workspace 回绑到当前服务器；多机环境中旧服务器仍在线或目录不可访问时不会自动迁移。Run 启动、用户进程初始化和 `/processes/me` 状态查询仍按用户进程 API 执行强健康检查。

响应 `WorkspaceFileRouteResponse`：

```json
{
  "workspaceId": "wrk_...",
  "linuxServerId": "127.0.0.1",
  "baseUrl": "http://127.0.0.1:8080",
  "webSocketPath": "/api/internal/platform/workspace-management/file/ws",
  "sameServer": true,
  "message": null
}
```

`GET /api/internal/platform/workspace-management/backend-servers` 仅 `SUPER_ADMIN` 可调用，用于服务器工作空间选择器。后端基于活跃 `linux_servers` 与 `backend_java_processes` 返回稳定服务器身份、可访问后端地址、文件 WebSocket path 和默认目录；`defaultDirectory` 为目标 Java 进程运行目录，缺失时回退当前后端 `user.dir`。

响应示例：

```json
[
  {
    "linuxServerId": "127.0.0.1",
    "name": "local-opencode-host",
    "baseUrl": "http://127.0.0.1:8080",
    "webSocketPath": "/api/internal/platform/workspace-management/file/ws",
    "defaultDirectory": "/opt/test-agent",
    "sameAsAgent": true
  }
]
```

`POST /api/internal/platform/workspace-management/file-ws/tickets` 在目标后端创建短期一次性 ticket，供浏览器建立文件 WebSocket。该接口必须使用用户登录态；`mode=workspace` 要求当前用户 opencode 进程服务器归属、workspace 和目标后端同服务器。签发优先使用轻量归属快照；当快照未 READY 时会复查当前用户 opencode 强状态，避免文件树与进程状态卡可用性不一致，但不会触发 `start` 命令；`mode=directory-picker` 允许 `SUPER_ADMIN` 浏览目标服务器目录，普通用户只能浏览与当前 opencode 进程同服务器的目录；`mode=agent-config` 绑定 Agent 配置 scope/workspace/worktree，读取允许登录用户，写入由 WebSocket handler 再校验 `SUPER_ADMIN`。

请求体：

```json
{
  "workspaceId": "wrk_...",
  "linuxServerId": "127.0.0.1",
  "mode": "workspace"
}
```

Agent 配置文件 ticket 请求体：

```json
{
  "workspaceId": "wrk_...",
  "linuxServerId": "10.8.0.12",
  "mode": "agent-config",
  "scope": "WORKSPACE",
  "worktreeId": "agw_..."
}
```

响应：

```json
{
  "ticket": "wft_...",
  "expiresAt": "2026-06-19T00:01:00Z",
  "webSocketUrl": "/api/internal/platform/workspace-management/file/ws?ticket=wft_..."
}
```

WebSocket 消息协议见 `docs/api/event-stream.md` 的“Workspace File WebSocket”段。旧 HTTP 文件接口继续保留给历史调用方和调试脚本；前端工作区文件树、打开、保存、状态、删除和实时预览读取必须走 route + ticket + 目标后端 WebSocket。

服务器目录选择器只通过短期 ticket 建立的文件 WebSocket 使用；缺失、不可访问或非目录返回 `VALIDATION_ERROR`。创建服务器工作空间仍要求 `SUPER_ADMIN`，且目标服务器必须与当前 agent 服务器一致。

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

Base URL：`/api/internal/platform/workspace-management`。该能力把配置管理中的应用工作空间模板落为托管 Git 目录，并同步创建运行态 `workspaces` 记录。新建或显式修复的托管路径在数据库中保存逻辑值：应用版本/副本使用 `appworkspace:<versionSegment>/<repositoryEnglishName>[/<templateDirectory>]`，个人 worktree 使用 `personalworktree:<versionSegment>/<userId>/<repositoryEnglishName>/<branch>[/<templateDirectory>]`；使用时分别基于通用参数 `OPENCODE_APP_WORKSPACE_ROOT`、`OPENCODE_PERSONAL_WORKTREE_ROOT` 解析为当前服务器物理路径。响应里的 `repoRootPath`、`workspaceRootPath`、`runtimeWorkspace.rootPath` 对托管工作区均为解析后的当前服务器物理路径；历史 Unix/Windows 绝对路径只兼容读取，不批量迁移。旧的手动目录注册 `/api/workspaces` 保持兼容，可继续保存用户选择的绝对目录。

鉴权：

- 所有接口要求已登录用户。
- 应用、模板、版本、切换最近使用等应用相关接口要求当前用户是 `application_members` 中的有效成员；不区分管理员和普通成员。
- 个人工作区接口要求当前用户是个人工作区拥有者且属于对应应用。
- 托管工作区成员校验失败返回 `FORBIDDEN`，message 固定包含当前加载上下文：`无该应用工作区权限：当前正在加载应用 {appName}({appId})，版本 {version/versionId/未确定}，工作区 {workspaceKind}:{workspaceName/workspaceId/未确定}`。`details` 仅放安全业务字段：`loadingStage`、`appId`、`appName`、`versionId`、`version`、`applicationWorkspaceId`、`workspaceKind`、`workspaceName`、`workspaceId`、`personalWorkspaceId`；无值字段不返回。

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/applications` | 查询当前用户加入的启用应用。 |
| `GET` | `/applications/{appId}/workspace-templates` | 查询应用工作空间模板，即配置表 `application_workspaces`。 |
| `GET` | `/applications/{appId}/workspace-templates/{templateId}/versions` | 查询模板下已创建的应用版本工作区。 |
| `POST` | `/applications/{appId}/workspace-templates/{templateId}/versions` | 创建或接管应用版本工作区，并创建运行态 Workspace。 |
| `POST` | `/workspace-versions/{versionId}/git-pull` | 在当前用户 READY opencode agent 所在服务器对应用版本工作区执行 `git pull --ff-only`，成功后广播其他服务器同步到同一 commit。 |
| `GET` | `/workspace-versions/{versionId}/personal-workspaces` | 查询当前用户基于某版本派生的个人工作区。 |
| `POST` | `/workspace-versions/{versionId}/personal-workspaces` | 基于应用版本工作区创建 git worktree 个人工作区。 |
| `GET` | `/recent-workspace` | 查询当前用户全局最近使用的托管运行态 Workspace。 |
| `GET` | `/applications/{appId}/recent-workspace` | 查询当前用户在指定应用下最近使用的托管运行态 Workspace。 |
| `POST` | `/workspaces/{workspaceId}/recent` | 标记某个托管运行态 Workspace 为最近使用。 |
| `POST` | `/applications/{appId}/workspaces/{workspaceId}/branch-preference` | 记录当前用户在 (appId, workspaceId) 维度下最近一次选择的 VCS 分支。 |
| `GET` | `/applications/{appId}/workspaces/{workspaceId}/branch-preference` | 查询当前用户在 (appId, workspaceId) 维度下最近一次选择的 VCS 分支；未设置返回 `null`。 |
| `GET` | `/personal-workspaces/{personalWorkspaceId}/diff` | 查询个人工作区与应用版本工作区目录差异。 |
| `POST` | `/personal-workspaces/{personalWorkspaceId}/sync-to-application` | 将所选个人工作区文件同步到应用版本工作区；目标副本必须 clean，后端先 fetch/pull --ff-only，再复制文件、提交并 push。 |
| `POST` | `/personal-workspaces/{personalWorkspaceId}/sync-from-application` | 将所选应用版本工作区文件同步到个人工作区。 |
| `POST` | `/workspace-versions/{versionId}/ensure-default-personal-workspace` | 显式确保默认个人工作区存在：查询 (versionId, userId, workspaceName=default)，存在则复用返回，不存在则后台创建。 |
| `GET` | `/workspaces/{workspaceId}/git-diff` | 基于本地 Git（不依赖 opencode）获取工作区变更文件列表，返回 `{ files: [{ path, rawStatus, status, staged, patch, additions, deletions }] }`；Git unmerged 状态会返回 `status=conflict`。 |
| `POST` | `/workspaces/{workspaceId}/git-discard` | 丢弃当前个人 worktree 中指定工作区相对路径的本地 Git 改动；已跟踪文件执行 restore，新增/未跟踪文件定点 clean。 |
| `POST` | `/workspaces/{workspaceId}/git-stage` | 把当前个人 worktree 中指定的非冲突文件定点加入真实 Git index。 |
| `POST` | `/workspaces/{workspaceId}/git-unstage` | 把当前个人 worktree 中指定的非冲突文件从真实 Git index 撤回工作树。 |
| `GET` | `/workspaces/{workspaceId}/git-conflict?path={path}` | 读取个人 worktree 冲突文件的 Git base/current/incoming stage 与工作树结果。 |
| `POST` | `/workspaces/{workspaceId}/git-conflict/resolve` | 解决单个冲突并定点 stage，支持当前、应用、两者、手工内容和删除语义。 |
| `POST` | `/workspaces/{workspaceId}/git-conflict/resolve-all` | 使用 Git index 原生 ours/theirs 批量解决全部冲突；只支持 `CURRENT/INCOMING`。 |
| `POST` | `/workspaces/{workspaceId}/git-conflict/abort` | 在个人 worktree 中执行 `merge --abort`，取消整次未完成合并。 |
| `POST` | `/personal-workspaces/{personalWorkspaceId}/publish-preview` | 发布前预检应用分支 HEAD、待合入提交数、A/M/D/R 汇总和样例路径；不修改个人 worktree。若个人 worktree 已处于未完成 merge，只返回已记录的应用 HEAD，不重复拉取远程。 |
| `POST` | `/personal-workspaces/{personalWorkspaceId}/publish` | 先校验可选 `expectedApplicationHead`，再只提交 `files` 白名单、合并并推送；冲突返回 `CONFLICT`，推送失败不会返回成功。响应包含 `currentStep/executedCommands` 供前端展示真实 Git 阶段。 |

`POST /applications/{appId}/workspace-templates/{templateId}/versions` 请求体：

```json
{
  "version": "20260707",
  "branch": "feature_testagent_20260707"
}
```

规则：

- `version` 支持 `yyyyMMdd`（8 位数字，前端日期选择器结果）和 `yyyy年M月`（历史数据格式）；其它格式返回 `VALIDATION_ERROR`。
- `yyyy年M月` 格式入库时 `version` 字段保留原值；派生分支名/路径时转 `yyyy-MM`（如 `2024年1月` → `2024-01`），避免 git ref / 路径里出现中文。
- 标准代码库分支固定为 `feature_testagent_{branchFragment}`，其中 `branchFragment` 是 `version` 经 `sanitizeVersionForBranchAndPath` 转换后的值（`yyyyMMdd` 原样使用）；后端会用当前用户 SSH key 先查分支；不存在时返回 `CONFLICT`。
- 非标准代码库必须传入 `branch`（前端选择的分支名），后端按该分支 clone。
- 内部部署模式版本库在应用版本、服务器副本和个人工作区的 clone/fetch/pull/push 前，都会按当前操作人统一认证号拼接实际 Git URL，并在本地仓库 origin 中刷新为当前操作人的地址；校验已有 origin 时忽略 `ssh://任意用户@` 前缀，只比较数据库保存的 `host[:port]/path`。
- 应用版本工作区物理仓库根目录读取通用参数 `OPENCODE_APP_WORKSPACE_ROOT`（`common_parameters` 唯一来源，缺失抛 `INTERNAL_ERROR`）；最终仓库目录为 `{root}/{branchFragment}/{repository.englishName}`，opencode root 为仓库目录下模板 `directoryPath`。新记录入库保存 `appworkspace:` 逻辑路径，响应返回解析后的当前服务器物理路径。
- 历史代码库若缺少 `englishName`，创建或接管应用版本工作区会返回 `VALIDATION_ERROR`，需要先在版本库管理补齐英文名称。
- 磁盘目录已存在时，后端校验 origin URL 和当前分支，匹配则接管，不覆盖、不删除；不匹配返回 `CONFLICT`。
- SSH Git 操作只使用当前登录用户保存的唯一 SSH key；HTTPS 不额外支持账号或 token。
- 多服务器部署下，版本主记录保存 `targetCommitHash`，每台服务器通过 `application_workspace_version_replicas` 记录本机副本路径、运行态 Workspace、当前 commit 和同步状态。`runtimeWorkspace` 返回当前用户 READY 的 opencode agent 所在服务器副本；目标副本未就绪时返回 `CONFLICT`。

`ApplicationWorkspaceVersionResponse`（路径字段均为当前服务器解析后的物理路径，不是数据库原始逻辑值）：

```json
{
  "versionId": "awv_...",
  "applicationWorkspaceId": "awp_...",
  "appId": "app_...",
  "repositoryId": "repo_...",
  "version": "20260707",
  "branch": "feature_testagent_20260707",
  "repoRootPath": "/data/.testagent/agent-opencode/workspace/appworkspace/20260707/demo",
  "workspaceRootPath": "/data/.testagent/agent-opencode/workspace/appworkspace/20260707/demo/F-GCMS/workspace",
  "runtimeWorkspace": {
    "workspaceId": "wrk_...",
    "name": "F-GCMS-20260707",
    "rootPath": "/data/.testagent/agent-opencode/workspace/appworkspace/20260707/demo/F-GCMS/workspace",
    "status": "ACTIVE",
    "linuxServerId": "10.8.0.12",
    "createdAt": "2026-06-23T00:00:00Z",
    "updatedAt": "2026-06-23T00:00:00Z"
  },
  "status": "ACTIVE",
  "targetCommitHash": "abc123...",
  "replicaCommitHash": "abc123...",
  "replicaLinuxServerId": "10.8.0.12",
  "replicaStatus": "READY",
  "createdAt": "2026-06-23T00:00:00Z",
  "updatedAt": "2026-06-23T00:00:00Z"
}
```

`POST /workspace-versions/{versionId}/git-pull` 无请求体。后端先解析当前登录用户的 READY opencode agent 所在 `linuxServerId`，再在同服务器应用版本副本上执行 `git pull --ff-only origin {branch}`。工作树存在未提交变更、非 fast-forward、目标服务器副本缺失或 SSH key 不可用时返回统一错误；成功后更新 `targetCommitHash` 与本机副本 `replicaCommitHash`，并通过内部服务器广播要求其他服务器同步到同一 commit。

`POST /workspace-versions/{versionId}/personal-workspaces` 请求体：

```json
{
  "workspaceName": "私人空间"
}
```

个人工作区基于应用版本仓库创建 git worktree，分支名为 `{应用版本分支}_{userId}_{workspaceName}`，其中 `workspaceName` 会安全化为 Git/path 可用片段。物理根目录读取通用参数 `OPENCODE_PERSONAL_WORKTREE_ROOT`（`common_parameters` 唯一来源，缺失抛 `INTERNAL_ERROR`）；最终目录包含 `{version}/{userId}/{repository.englishName}/{应用版本分支}_{userId}_{workspaceName}`，新记录入库保存 `personalworktree:` 逻辑路径，响应返回解析后的当前服务器物理路径。前端展示 `workspaceName` 和当前 worktree 分支。同一用户在同一应用版本下 `workspaceName` 唯一。

同步请求体：

```json
{
  "files": ["src/App.java"],
  "force": false
}
```

`sync-to-application.force=true` 时使用 `--force-with-lease` 覆盖远端；失败、冲突或认证问题使用统一 Git/冲突错误码返回，并记录同步审计。复制个人文件前，目标应用版本副本必须是 clean 状态，后端会先 `git fetch origin` 和 `git pull --ff-only {branch}`；目标副本脏或无法快进时不复制文件、不提交。同步成功后更新应用版本 `targetCommitHash` 与当前服务器副本 `replicaCommitHash`，并通过内部服务器广播要求其他服务器同步。应用版本工作区与个人工作区同步不新增 RunEvent/SSE 事件。

### 默认个人工作区显式创建/修复

`POST /workspace-versions/{versionId}/ensure-default-personal-workspace` 无请求体。后端逻辑：

1. 先查询 `(versionId, userId, workspaceName=default)` 是否已有个人工作区记录。
2. 存在：直接复用，返回 `DefaultPersonalWorkspaceResponse`（含 `personalWorkspaceId`、`personalWorkspaceName`、`personalWorkspaceBranch`、`runtimeWorkspace`）。
3. 不存在：先按 `ENSURE_LOCAL` 确保当前服务器有 READY 应用版本副本，再后台创建个人工作区（`git worktree add -b {branch}_{userId}_default`）。如果当前服务器没有副本，后端会基于 `OPENCODE_APP_WORKSPACE_ROOT` 创建本机副本；禁止再用旧 `application_workspace_versions` 绝对路径伪造成 READY replica。如果同名个人分支已存在，后端会尝试复用该分支挂载 worktree；如果目标目录已存在且是同一分支的 Git worktree，则接管并补运行态记录；如果同名分支仍登记在旧路径且目标规范路径不存在，后端会先 `git worktree move` 重挂载到规范路径。只有目标目录被其他内容占用时返回 `CONFLICT`。

默认个人工作区分支命名规则：`{应用版本分支}_{userId}_default`（与旧规则的 `_{personalWorkspaceId}` 不同）。已有 `workspaceName=default` 的旧个人工作区记录如果 branch/path 不符合新规范，`ensure-default-personal-workspace` 会非破坏式创建或重挂载规范 worktree，并把 `personal_workspaces` 与关联运行态 `workspaces.root_path` 更新为 `personalworktree:` 逻辑路径；旧物理目录不会被自动删除。新建自定义私人空间同样使用 `{应用版本分支}_{userId}_{workspaceName}`。

登录和切换应用的默认加载不调用该接口，不会自动创建或修复 default 私人工作区；只有用户显式点击版本、创建新版本或其它明确创建/修复动作才调用该接口。

响应 `DefaultPersonalWorkspaceResponse`：

```json
{
  "personalWorkspaceId": "psw_...",
  "personalWorkspaceName": "default",
  "personalWorkspaceBranch": "feature_testagent_20260707_usr_xxx_default",
  "runtimeWorkspace": {
    "workspaceId": "wrk_...",
    "name": "default",
    "rootPath": "/data/.testagent/personal-worktrees/...",
    ...
  }
}
```

### 工作区本地 Git Diff

`GET /workspaces/{workspaceId}/git-diff` 无请求参数。后端通过 runtime workspace 反查 personal workspace，使用其 `repoRootPath` 执行 `git status --porcelain` + `git diff`，并复用公共解析逻辑处理路径反转义、rename 新路径、staged/unstaged patch 合并和 additions/deletions 统计，返回每个变更文件的 path、rawStatus、status、staged、patch、additions、deletions。`rawStatus` 是 Git porcelain 两字符状态码，`DD/AU/UD/UA/DU/AA/UU` 统一映射为 `status=conflict`，供前端把冲突文件从普通 staged/unstaged 文件中拆出展示。不依赖 opencode `/vcs/diff`，opencode 服务异常不影响变更列表刷新。

响应 `WorkspaceGitDiffResponse`：

```json
{
  "files": [
    {
      "path": "src/App.java",
      "rawStatus": " M",
      "status": "modified",
      "staged": false,
      "patch": "@@ -1,3 +1,4 @@\n...",
      "additions": 3,
      "deletions": 1
    }
  ]
}
```

`POST /workspaces/{workspaceId}/git-discard` 请求体：

```json
{
  "files": ["src/App.java"]
}
```

`files` 使用 `git-diff` 响应中的工作区相对路径。后端只允许当前用户个人 worktree 的运行态 workspace 调用；会先把路径映射到仓库相对路径，已跟踪修改/删除执行 `git restore --staged --worktree -- <file>`，新增或未跟踪文件先取消暂存再执行定点 `git clean -f -- <file>`。成功后无业务响应体，前端刷新 `git-diff` 后该文件不再出现在变更列表中。

`POST /workspaces/{workspaceId}/git-stage` 与 `POST /workspaces/{workspaceId}/git-unstage` 使用相同请求体：

```json
{
  "files": ["src/App.java"]
}
```

后端复用个人工作区归属和路径校验，把工作区相对路径映射为仓库相对路径后分别执行定点 `git add -- <files>` 和 `git restore --staged -- <files>`。merge 冲突期间仍允许操作普通非冲突文件；unmerged 路径必须通过 `git-conflict/resolve` 处理，传入普通 stage/unstage API 会返回 `CONFLICT`。只要仍存在 unmerged 文件，Git 原生规则仍禁止 commit/push。

`GET /workspaces/{workspaceId}/git-conflict?path=src/App.java` 返回 `path/rawStatus/baseContent/currentContent/incomingContent/resultContent`。四个 content 可为 `null`，表示对应 Git stage 或工作树中不存在文件；文本上限 1MB。`POST /workspaces/{workspaceId}/git-conflict/resolve` 请求体为：

```json
{"path":"src/App.java","resolution":"MANUAL","content":"编辑后的最终内容"}
```

`resolution` 支持 `CURRENT`、`INCOMING`、`BOTH`、`MANUAL`、`DELETE`。非冲突路径返回 `CONFLICT`。批量接口请求体为 `{"resolution":"CURRENT"}` 或 `{"resolution":"INCOMING"}`，分别将全部冲突采用个人侧 stage 2 或远程侧 stage 3；目标侧不存在的文件按删除处理，不要求逐个解决。`POST /workspaces/{workspaceId}/git-conflict/abort` 无请求体，仅在存在未完成 merge 时成功。

### 个人工作区提交并推送

`POST /personal-workspaces/{personalWorkspaceId}/publish` 请求体：

```json
{
  "commitMessage": "feat: 新增测试案例",
  "files": ["src/App.java", "README.md"],
  "expectedApplicationHead": "6093725...",
  "operationId": "aco_1234567890abcdef"
}
```

提交前调用 `POST /personal-workspaces/{personalWorkspaceId}/publish-preview`。预览返回 `applicationHead/personalHead/incomingCommitCount/changedFileCount/addedCount/modifiedCount/deletedCount/renamedCount/samplePaths`。前端只把 `applicationHead` 原样传给 publish 作为并发保护，不因待合入提交数弹二次确认；真正的冲突由后续 Git merge 保留原生冲突现场并返回 `CONFLICT`。若 preview 后应用 HEAD 再次变化，publish 会在修改个人 index 或创建提交前返回 `CONFLICT`。`operationId` 可选，格式与 Agent 配置长操作一致；前端传入时可先连接 `/agent-config/operations/{operationId}/ws`，再调用 publish，并通过 `command` 事件实时展示当前正在执行的 Git 命令。应用分支 pull 成功后会立即同步版本 target commit 和本机副本 commit，即使随后个人 merge 冲突也不保留陈旧元数据。若个人 worktree 已经处于 Git 原生 merge 状态，预览和继续发布都不再重复拉取远程；用户解决全部 unmerged 文件后，重新发布只完成 merge commit、把个人分支合回应用副本并 push。

后端执行流程：

1. 将 `files` 映射为仓库相对路径。普通发布先把 index 恢复到 `HEAD`，再只 stage 并提交这些文件；未选择文件保留在工作树。merge 重试则保留完整 merge index，在所有冲突解决后提交 Git 自动合并项和解决结果。
2. 确保当前服务器应用版本副本可用：副本路径不存在、不是当前机器路径或历史运行态 Workspace 根目录仍指向旧系统路径时，按当前 `OPENCODE_APP_WORKSPACE_ROOT` 重新准备本机副本并更新副本/Workspace 记录。
3. 普通发布切到应用版本副本（checkout 在应用版本特性分支）：`git fetch origin` + `git pull --ff-only {appVersionBranch}`，先把远端特性分支拉到本地。若个人 worktree 已有未完成 merge 且冲突已解决，则复用已有 READY 应用副本，不再 fetch/pull。
4. 普通发布在个人 worktree 中 `git merge --no-ff {appVersionBranch}`，把最新特性分支合入个人分支；如发生冲突，冲突文件保留在当前个人 worktree。merge 重试时跳过该步骤，直接提交用户已解决的 merge index。
5. 在应用版本副本中 `git merge --no-ff {personalBranch}`，把已包含最新特性分支的本地个人分支合回特性分支（合并方向为「特性分支 ← 个人分支」）。
6. `git push origin {appVersionBranch}` 推送应用版本特性分支；个人分支不推送远端。

合并结果：

- **成功（MERGED）**：更新应用版本 `targetCommitHash` 和当前服务器 replica commit，广播其他服务器同步。
- **冲突（CONFLICT）**：返回冲突文件列表，不推送特性分支；如果冲突发生在个人 worktree 合入特性分支阶段，后端不会 abort，冲突文件保留在当前个人 worktree。用户在当前个人 worktree 中解决冲突并保存后，重新点击提交并推送完成 merge commit 和特性分支推送。前端必须保留 `CONFLICT` 提示，并通过 `git-diff` 中的 `status=conflict/rawStatus` 把 unmerged 文件单独展示为待解决冲突，不能作为普通 staged 删除展示；冲突未解决期间普通非冲突文件仍可真实 stage/unstage，但提交按钮必须禁用，直到所有 unmerged 文件解决。应用版本副本合并阶段如出现冲突，后端会 abort 应用版本副本上的 merge 且不推送。

响应 `PersonalWorkspacePublishResponse`：

```json
// 成功
{
  "status": "MERGED",
  "personalWorkspaceId": "psw_...",
  "versionId": "awv_...",
  "conflictFiles": [],
  "message": "合并成功: abc123...",
  "remotePushed": true,
  "headCommit": "abc123...",
  "executedCommands": ["git fetch", "git merge", "git push"],
  "currentStep": "COMPLETED"
}

// 冲突
{
  "status": "CONFLICT",
  "personalWorkspaceId": "psw_...",
  "versionId": "awv_...",
  "conflictFiles": ["src/App.java", "src/Config.java"],
  "message": "合并冲突，请在个人工作区中解决冲突后重新提交并推送",
  "executedCommands": ["git fetch", "git merge"],
  "currentStep": "MERGE_PERSONAL"
}
```

`currentStep` 取值为 `PREPARE_REMOTE`、`COMMIT_LOCAL`、`MERGE_PERSONAL`、`MERGE_APPLICATION`、`PUSH_REMOTE`、`COMPLETED`。`remotePushed` 只有在应用版本分支 `git push` 成功并读取发布后的 HEAD 后才为 `true`；前端未收到 `remotePushed=true` 时不得展示推送成功。发布接口抛出统一错误时，`details.failedStep` 和 `details.executedCommands` 会尽量返回失败前已进入的 Git 阶段和已执行命令，前端命令日志应按失败步骤过滤展示，不展示预检或后续步骤的命令。处理中的弹窗应优先展示 WebSocket `command` 中的当前命令；接口最终返回后可回填完整 `executedCommands`。

前端两级菜单（应用工作空间→版本）使用说明：

- 工作台左下角的"应用工作空间"按钮按当前应用（`selectedAppId`）查询 `GET /applications/{appId}/workspace-templates`，渲染第一级菜单（只显示 `workspaceName`，不显示 `directoryPath` / `branch`）。
- 鼠标 hover 第一级菜单项时按需触发 `GET /applications/{appId}/workspace-templates/{templateId}/versions` 加载该模板下的版本（懒加载，未展开的模板不发请求）。
- 点击版本或创建新版本等用户显式动作会调用 `POST /workspace-versions/{versionId}/ensure-default-personal-workspace` 确保默认个人工作区存在（复用、接管或创建），再通过 `POST /workspaces/{workspaceId}/recent` 写入最近使用偏好，并触发工作台切换。登录/切换应用的自动默认加载只读取已有 default 私人工作区，不创建、不修复；当前用户当前应用没有 recent、recent 不能反查 `versionId`，或该版本没有 `workspaceName=default` 且带运行态 workspaceId 的个人工作区记录时，只选择应用，不自动加载工作区。普通工作区文件树、保存和左侧 Git 变更面板都基于已加载的 default 私人 worktree。
- 当前版本匹配规则：优先按 `runtimeWorkspace.workspaceId` 精确匹配，其次按 `workspaceRootPath` 匹配 `selectedWorkspace.rootPath`。
- 第二级菜单（版本列表）底部固定一行「+新增版本」：点击后弹 el-dialog，内嵌 `ElDatePicker`（`type=date`, `format=yyyyMMdd`），标准库直接选日期；非标准库先通过 `GET /repositories/{repoId}/branches` 加载分支列表，用户选择分支后再选日期。提交时调用 `POST /applications/{appId}/workspace-templates/{templateId}/versions`，请求体 `version` 字段为 `yyyyMMdd` 格式，非标准库同时传递 `branch`。成功后失效 `versionsByTemplateId` 缓存并把新建版本切到工作区。

应用级"默认工作空间"解析规则（前端 `handleSelectApp` + `pickDefaultWorkspaceForApp`）：

1. 调用 `GET /applications/{appId}/recent-workspace` 读取 `user_application_workspace_preferences` 中 `(user_id, app_id)` 对应的最近使用运行态 Workspace。
2. 命中 recent 且响应带 `versionId`：调用 `GET /workspace-versions/{versionId}/personal-workspaces`，只选择当前用户该版本下 `workspaceName=default` 且存在 `runtimeWorkspace.workspaceId` 的个人工作区记录并切工作台；该只读流程不调用 `ensure-default-personal-workspace`。
3. 未命中 recent、recent 不能反查 `versionId`，或私人工作区列表中没有可用 default 记录：保持工作区空态，不拉首模板首版本，不自动创建 default 私人 worktree；左侧工作区切换入口仍显示，用户可手动选择版本或新增版本。
4. 应用下没有任何工作空间模板/版本时保持空态，不提供普通本机目录选择入口。

跨应用"默认进入应用"还原规则（前端 `trySelectDefaultApp`，重新登录 / 换电脑登录时使用）：

1. 应用目录加载完成且 `selectedAppId` 为空时，先等待 `GET /recent-workspace` 返回「全局最近 Workspace」及其 `appId`（`WorkspaceRuntimeResponse.appId`，仅全局接口填充，其他接口为 `null`）。
2. 命中且 `appId` 在当前账号的应用目录里：调用 `handleSelectApp(appId)` 走上面「应用级默认工作空间」解析链；只有 per-app recent 带 `versionId` 且该版本已有可用 default 私人工作区时才加载工作区。
3. 命中但 `appId` 不在当前账号的应用目录里（应用被禁用 / 被移除 / 切换租户）：降级到 `apps[0]`，但不会因此自动加载工作区。
4. 全局 recent 接口报错或返回 `null`：同样降级到 `apps[0]`，只选择应用。
5. 应用目录为空：保持空态，由应用目录和工作空间模板配置驱动后续进入。

`POST /workspaces/{workspaceId}/recent` 兼容说明：

- 后端 `markRecentWorkspace` 同时写入 `user_global_workspace_preferences`（`app_id = NULL`）和 `user_application_workspace_preferences`（`app_id = 解析到的 appId`），对应"全局最近"和"应用内最近"两套维度。
- 工作区不属于任何应用（即 `appIdForRuntimeWorkspace` 既不匹配应用版本也不匹配个人工作区）时返回 `NOT_FOUND`；前端 `applyManagedWorkspace` 静默吞掉该错误，切换流程不受影响。
- `WorkspaceRuntimeResponse.appId` / `versionId` / `applicationWorkspaceId` 字段：
  - `GET /recent-workspace`、`GET /applications/{appId}/recent-workspace`：必定填充（工作区不属于任何应用版本时三者均为 `null`），用于前端在重新登录或换电脑登录时直接还原上次所在的应用 + 模板 + 版本，让左下角"切换工作空间"按钮立刻显示当前工作区名 + 版本号。
  - `POST /workspaces/{workspaceId}/recent`：同样回填上述三个字段，调用方拿到响应后可把 versionId / applicationWorkspaceId 合并到工作区上，确保会话切换和用户显式选择版本等路径也能立即定位当前版本与模板，不必等模板 versions 异步加载。
  - 其他接口（`GET /workspaces/{workspaceId}` 等）：仍返回 `null`，由调用方按 `appId` 自行索引；不在响应里重复写出托管应用信息，避免引入「运行态 Workspace → 托管应用」反向依赖。
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
| `GET` | `/api/sessions/{sessionId}/session-tree/messages` | 查询 root session 下全量历史 session tree message snapshot。 |
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
| `GET` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/session-tree/messages` |
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

`GET /api/sessions/{sessionId}/messages` 会先在存在 agent binding 时，从 bounded-elastic 线程读取当前 agent 标准 session messages 并 upsert 到 `session_messages`；如果 opencode 进程不可用、超时或远端 session 不存在，接口回退返回数据库快照，不向前端暴露 generated SDK DTO。assistant 的 `content` 只保存可见 text part，不混入 reasoning 或 tool output；仅包含工具/文件 parts 的 assistant 消息允许 `content=""`，结构化内容仍由 `parts` 返回。

`GET /api/internal/agent/{agentId}/sessions/{sessionId}/session-tree/messages` 返回 Session root 下全量历史消息树快照，旧 `/api/sessions/{sessionId}/session-tree/messages` 和平台内部 URL 继续兼容。后端通过 `AgentSessionBinding` 找到 root remote session，再按 `run_session_scope_sessions.root_session_id` 拉取跨 Run 已发现的 root/child session；scope 表为空时返回 root-only snapshot。响应字段与 Run 级 snapshot 一致，但顶层标识为 `sessionId`。

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
| `GET` | `/api/runs/{runId}/session-tree/messages` | 查询当前 Run scope 的 root + child session message snapshot。 |
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
| `GET` | `/api/internal/platform/opencode-runtime/runs/{runId}/session-tree/messages` |
| `GET` | `/api/internal/platform/opencode-runtime/runs/{runId}/diff` |

agent-scoped URL 使用 `/api/internal/agent/{agentId}` 前缀，前端默认传 `opencode`。例如：

| 方法 | agent-scoped 路径 |
|---|---|
| `POST` | `/api/internal/agent/{agentId}/runs` |
| `GET` | `/api/internal/agent/{agentId}/runs/{runId}` |
| `POST` | `/api/internal/agent/{agentId}/runs/{runId}/cancel` |
| `GET` | `/api/internal/agent/{agentId}/runs/{runId}/events` |
| `GET` | `/api/internal/agent/{agentId}/runs/{runId}/session-tree/messages` |
| `GET` | `/api/internal/agent/{agentId}/runs/{runId}/diff` |
| `POST` | `/api/internal/agent/{agentId}/runs/{runId}/diff/accept` |
| `POST` | `/api/internal/agent/{agentId}/runs/{runId}/diff/reject` |

### 用户 opencode 进程 API

用户进程 API 只支持 `agentId=opencode`，必须从认证主体读取当前用户；未认证返回 `UNAUTHENTICATED`，非 `opencode` agent 返回 `VALIDATION_ERROR`。如果当前用户已有 ACTIVE binding 且 `linuxServerId` 不等于当前 Java 所在服务器，API 层会先用统一 `BackendJavaRouteResolver` 找到 binding 所属服务器 Java 的 `listenUrl`，再通过统一 `BackendHttpForwarder` 透传原始 `Authorization`、`X-Trace-Id`、query、请求 body 和统一错误响应到目标 Java；内部路由头 `X-Test-Agent-Backend-Routed: true` 会阻止循环转发。配置管理创建应用工作区、应用版本工作区创建、版本 `git-pull`、Run 创建、初始化和 runtime 代理都纳入同一用户 binding 路由判断。是否已分配只以 `user_opencode_process_bindings(user_id, agent_id)` 的 ACTIVE 记录为准；`GET /processes/me` 目标后端不在线、转发失败或目标返回 5xx 时返回 200 成功响应，`data.status=UNAVAILABLE`、`serviceStatus=NOT_RUNNING`，并保留绑定的 `linuxServerId/port`；若能解析到目标服务器当前在线 Java 的可访问 host，则返回 `serviceAddress={currentHost}:{端口}`，否则 `serviceAddress=null`，表示已分配但暂无法确认健康状态。初始化、Run 启动和 runtime 代理仍在目标后端不可用时返回 `OPENCODE_UNAVAILABLE`，不会自动迁移 binding，也不会在当前 Java 启动旧 binding。目标 Java 上所有强状态查询统一调用 `OpencodeProcessStatusQueryService`：先查询平台进程记录是否存在，再通过本机 manager health 归一为未启动、运行中或 `STALE`；健康成功和明确未启动才更新稳定状态，瞬时 HTTP/manager 异常保留数据库最近状态。已有 RUNNING 进程仅在最近成功健康检查后的 60 秒内允许沿用 READY，超过宽限期后状态查询和 Run 前置校验都会拒绝旧绿灯。初始化最终由 binding 所属服务器或当前服务器 Java 通过本机已连接的 `opencode-manager` WebSocket 控制面启动进程，并统一调用公共启动服务在 manager `STARTED` 后复用公共状态查询，默认最多等待 manager command-timeout（10 秒）确认 manager state/PID、`/global/health` 和 `/global/config` 都 healthy 后才返回 READY、写入 RUNNING/binding/heartbeat/兼容节点；无 manager 连接、命令超时、manager 返回失败或启动后 health 在等待窗口内仍不健康时分别映射为 `OPENCODE_UNAVAILABLE`、`OPENCODE_TIMEOUT`、`OPENCODE_BAD_GATEWAY` 或统一 opencode 不可用错误。本地和生产都必须启动 Go manager，不再支持 `local-direct` 或 `gateway-mode=local` 绕过。

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
  "linuxServerId": "server-a",
  "containerId": "ctr_...",
  "port": 4096,
  "baseUrl": "http://10.0.0.12:4096",
  "serviceStatus": "RUNNING",
  "serviceAddress": "10.0.0.12:4096",
  "checkedAt": "2026-06-24T08:00:00Z"
}
```

字段说明：

- `status`：`READY`、`NEEDS_INITIALIZATION`、`UNAVAILABLE`。
- `initializable`：当前状态是否允许前端展示初始化动作；无当前后端可连接的健康容器时为 `false`。
- `bindingClearable`：当 `status=UNAVAILABLE` 时，如果后端检测到 `execution_nodes` 中仍有可路由的固定节点（例如本地启动的 opencode）作为兜底，会把 `bindingClearable` 置为 `true`，前端可以展示"重置绑定"按钮。
- `localFallback`：当 `status=READY` 时，如果响应已经回退到 `execution_nodes` 中的固定节点而非用户专属进程，`localFallback` 为 `true`；此时 `baseUrl` 来自固定节点，前端可以直接发起对话。
- `serviceStatus`：头像菜单使用的服务展示状态，取值为 `UNASSIGNED`（未分配）、`RUNNING`（运行中）、`NOT_RUNNING`（已分配但未运行或健康不可确认）；该字段不改变 `status` 的对话门禁语义。
- `serviceAddress`：头像菜单展示地址，格式为 `{currentHost}:{内部opencode端口}`；来源优先是真实进程 `baseUrl`，进程缺失或未运行时按绑定的 `linuxServerId` 选择当前在线 Java 并从其 `listenUrl` 提取 host，无法解析时为 `null`，不会用 `linuxServerId` 拼伪地址。
- `message`：面向用户的状态说明或失败原因；目标后端不可用的状态查询会说明已分配但暂无法确认健康状态。
- `processId`、`containerId`、`baseUrl`：仅在已有或成功初始化进程时返回；`linuxServerId` 表示稳定服务器身份，`port` 表示绑定端口，绑定存在但进程缺失或不可确认时也会返回；`baseUrl` 使用当前 advertised host 与端口生成，不使用 `linuxServerId` 拼地址。
- `checkedAt`：本次状态计算时间。

初始化规则：

- 未绑定用户时选择当前后端实例已连接的本服务器进程数最少 `READY` 容器。
- 已有绑定但进程不可用时，只能在 binding 原 `linuxServerId` 内选择目标后端已连接的进程数最少容器；原服务器无可用容器或目标后端不在线时返回 `OPENCODE_UNAVAILABLE`，不 fallback 到当前后端任意健康容器，也不迁移用户 binding。
- 端口从容器端口范围内选择第一个未被同一 `linuxServerId` 下历史进程行占用的端口；避让范围按数据库唯一约束 `(linux_server_id, port)` 生效，包含其它容器和非运行态脏数据。
- 启动参数读取通用参数：`XDG_DATA_HOME={OPENCODE_SESSION_DIR}/{port}`、`OPENCODE_CONFIG_DIR={OPENCODE_PUBLIC_CONFIG_DIR}`；缺失或空白时返回平台错误，不回退环境变量或代码默认路径。
- Java 后端不检查本机 `OPENCODE_PUBLIC_CONFIG_DIR` 目录；初始化先按当前候选中进程数最少且有空闲端口的容器选择目标 manager，再下发 `start`。目标 manager 在所在服务器检查 `OPENCODE_PUBLIC_CONFIG_DIR` 必须是已存在且非空的目录；缺失、为空、非目录或不可读时返回 `FAILED + errorCode=OPENCODE_UNAVAILABLE`，`message` 包含目标服务器和 manager 实际检查的配置目录，并提示联系超级管理员进入“系统管理 → 配置管理 → opencode公共配置管理”完成初始化，不会创建 session、不会启动 opencode server，Java 将该结果映射为同码平台错误。
- 若 manager 本地 state 已托管目标端口且健康，`start` 命令按幂等成功处理，后端继续补齐用户进程绑定、进程快照和兼容 `execution_nodes` 投影；若该 state 不健康，仍按 manager 失败结果返回统一 opencode 错误。
- 初始化成功必须同时满足 manager 已管理该端口、PID 存活、opencode server `/global/health` 和 `/global/config` healthy；仅 manager 返回 `STARTED` 不算成功。启动确认期间只有 OpenCode HTTP 暂未就绪会在窗口内重试，manager 超时或网关错误立即失败；最终失败候选会收敛为 `STOPPED/UNHEALTHY/FAILED`，不会长期残留 `STARTING`。普通状态轮询遇到瞬时 HTTP 或 manager 异常时返回 `STALE` 且不覆盖数据库稳定状态。
- 初始化成功后会同步写入用户进程绑定、进程快照、Redis heartbeat，以及兼容旧运行链路的 `execution_nodes` 投影。

`DELETE` 行为：

- 不会级联删除 `opencode_server_processes` 记录，避免误关停用户可能在用的后台进程。
- 不会重启任何容器；调用后立即返回当前最新状态。
- 典型使用：用户绑定指向的 Linux 服务器已经下线（`canRebuildOn=false`）但本地 `execution_nodes` 里还有 `node_local_opencode`；前端在 `status=UNAVAILABLE` 且 `bindingClearable=true` 时调用此接口，后端会回退到本地节点并把 `status=READY, localFallback=true` 透出。

### opencode-manager 控制面 API

控制面只供容器内 `opencode-manager` 使用，不复用用户 JWT 或普通 API token。后端通过 `test-agent.opencode.manager-control.token` / `TEST_AGENT_OPENCODE_MANAGER_TOKEN` 配置独立 manager token；manager 建立 WebSocket upgrade 时必须携带 `Authorization: Bearer <token>`。token 缺失或错误返回统一 `UNAUTHENTICATED`。Go manager 运行路径不得通过 HTTP 与 Java 交互，`manager-backends` 仅保留为只读诊断/兼容接口。每个 manager 只连接 `.serverhost + OPENCODE_MANAGER_BACKEND_PORT` 推导出的本服务器 Java；多 Java 后端之间的用户请求转发由 Java API 层完成，manager 不再连接其他服务器 Java。

后端 Java 启动时会把稳定服务器身份写入通用参数 `SYS_DATA_ROOT_DIR` 派生的 `SYS_DATA_ROOT_DIR/.serverid`，把可访问主机地址写入 `SYS_DATA_ROOT_DIR/.serverhost`。Go manager 在非 Windows 环境启动时按同一系统参数的平台默认根目录读取这两个文件（Linux `/data/.testagent/.serverid`、`/data/.testagent/.serverhost`，macOS `$HOME/.testagent/.serverid`、`$HOME/.testagent/.serverhost`），最多等待 30 秒；因此 WebSocket `register` / `heartbeat` 中的 `linuxServerId` 表示稳定服务器身份，不表示容器网卡 IP，也不要求是 IP。`containerId` 继续表示容器身份，非 Windows 先读系统 hostname，再读 `/etc/hostname`，最后才读 `OPENCODE_MANAGER_CONTAINER_ID` 兜底；Windows 本机开发态直接使用机器名。

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/api/internal/platform/opencode-runtime/manager-backends` | 兼容诊断：查询 Redis 中仍在线的后端实例；Go manager 不使用该 HTTP 接口。 |
| `WS` | `/api/internal/platform/opencode-runtime/manager/ws` | manager 与当前后端实例建立 JSON WebSocket 长连接。 |

兼容诊断响应 `data`：

```json
[
  {
    "backendProcessId": "bjp_...",
    "linuxServerId": "linux-prod-a",
    "listenUrl": "http://10.8.0.21:8080",
    "webSocketUrl": "ws://10.8.0.21:8080/api/internal/platform/opencode-runtime/manager/ws",
    "lastHeartbeatAt": "2026-06-24T08:00:00Z"
  }
]
```

WebSocket 协议版本固定为 `opencode-manager.v1`。文本帧是 JSON envelope，稳定 `type` 包括 `register`、`registered`、`configRequest`、`configUpdate`、`managerHeartbeat`、`command`、`commandResult`、`error`；`backendListRequest`、`backendListResponse` 和旧 `heartbeat` 只作为兼容枚举保留，不作为新运行路径主入口，Java 收到 `backendListRequest` 会忽略。`configUpdate` 首次由 manager 的 `configRequest` 拉取，完整帧返回 `maxProcesses`、`sessionRoot`、`configDir`，其中 `sessionRoot/configDir` 分别来自 `common_parameters.OPENCODE_SESSION_DIR` 与 `OPENCODE_PUBLIC_CONFIG_DIR`；后续最大进程数刷新帧只携带 `maxProcesses`，`sessionRoot/configDir` 为空表示路径不变。`managerHeartbeat` 每 5 秒由 manager 通过本服务器 Java socket 发送一次，后端写入 Redis manager 快照，TTL 为 10 秒，同时把容器资源指标追加到 Redis 48 小时历史 ZSET；资源指标可带 `metricsSource=cgroup|process|unavailable`，其中 `cgroup` 表示 Linux 容器 cgroup 指标，`process` 表示开发态或降级的 manager 进程指标，`unavailable` 表示当前平台不可采集。`managerHeartbeat.managedProcesses[]` 可选携带本 manager 管理的 opencode server 明细：`port`、`pid`、`baseUrl`、`sessionPath`、`configPath`、`startedAt`、`startCommand`、`traceId`；`startCommand` 是 manager 生成的安全展示命令，只包含 `XDG_DATA_HOME`、`OPENCODE_CONFIG_DIR` 和 `opencode serve` 固定参数，不包含 manager token、用户 JWT、Cookie、prompt 或 API key。manager 生成心跳前会清理 PID 已不存在的本地 stale state，旧 manager 或旧 Redis 快照缺少该字段时按 `null`/缺失兼容。后端命令使用 `commandId=mcmd_...`，manager 回包必须带同一 `commandId` 和 `traceId`；`commandResult.errorCode` 为可选平台错误码，目前 `start` 因目标服务器公共配置目录未初始化失败时返回 `OPENCODE_UNAVAILABLE`，`message` 包含目标服务器和 manager 实际检查的配置目录。当前命令集合为 `start`、`health`、`stop`、`restart`，其中用户初始化、健康检测、运行管理用户进程探测、后台 heartbeat、启动后确认和停止后确认都由目标 Java 通过公共状态查询服务统一下发 `health`；收到完整 `configUpdate` 前，`start`/`restart` 返回 `FAILED`，不会拉起 opencode server；运行管理页面按容器和端口调用 HTTP stop/restart API，当前 Java 先按 Redis manager 快照把请求路由到容器所属 Linux 服务器的 Java，再由目标 Java 转发为本机 manager WebSocket `command`。`stop` 对 state 存在但 OS 进程已结束的端口按幂等成功返回 `STOPPED` 并清理 state；`configUpdate` 成功应用以及 `start`、`stop`、`restart` 成功后，manager 都会立即补发一次 `managerHeartbeat`，不必等待 5 秒周期。

### opencode runtime 运行管理 API

运行管理 API 是高权限平台接口，只允许已认证用户且角色包含 `SUPER_ADMIN` 访问。未认证返回 `UNAUTHENTICATED`，非超级管理员返回 `FORBIDDEN`，非法分页、状态、容器 ID 或端口参数返回 `VALIDATION_ERROR`。overview 和 metrics 只读展示当前 Redis 中仍在线的 Java 后端、容器、manager、manager 管理的本地 opencode server 明细和 manager-backend 连接；页面底部用户进程列表不再从 overview 展示全部进程，而是通过 `user-processes` 按用户关键字查询数据库记录，只在进程所属 `linuxServerId` 为当前 Java 时主动调用本机 manager health，远端进程返回 `REMOTE_SERVER/CHECK_SKIPPED` 避免随机 Java 控制其他服务器 manager。运行管理前端展示形态由 overview 现有 wire shape 派生：按 `linuxServerId` 合并 `linuxServers[]` 与 `backendProcesses[]` 为“服务器 / Java 进程”列表，并从 `backendProcesses[].listenUrl` 提取可访问 host 展示“IP地址”列；按 `containerId` 合并 `containers[]` 与 `managers[]` 为“容器 / 管理进程”列表，并按同一 `linuxServerId` 复用对应 Java host 展示“IP地址”列；再用 `backendProcesses[]`、`managerBackendConnections[]`、`managers[].managedProcesses[]` 渲染 `Java -> Manager -> opencode server` 节点连线拓扑图。restart/stop 只按 `containerId + port` 发起 HTTP 命令；入口 Java 先用统一 `BackendJavaRouteResolver` 定位容器所属 `linuxServerId`，必要时把同一 HTTP 请求通过 `BackendHttpForwarder` 转发到目标 Java，由目标 Java 向本服务器 manager WebSocket 长连接发送控制命令，不直接访问 opencode server。平台已有用户进程记录的命令由公共启动/停止服务回写状态；restart 命令必须在 manager `STARTED` 后通过公共启动服务等待 health healthy，默认最多等待 10 秒，超时仍按统一错误返回；停止命令必须在 manager `STOPPED` 后继续 health，确认不健康才写 `STOPPED` 并返回成功。没有平台用户进程记录的无主 manager state 不新增数据库记录，只同步返回 manager stop/restart 结果。停止成功后前端会先从当前 overview 缓存局部移除对应 `containerId + port` 的明细并更新容量，随后由 manager 立即心跳和周期心跳同步 Redis latest snapshot；重启/停止失败时前端也会刷新 overview 和当前用户进程查询，让最新 DB 状态、managerStatus、healthStatus 与 healthMessage 立即展示。Redis 不可用时 overview 不会回退数据库 heartbeat 字段，命令路由也无法定位远端 Java。

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/api/internal/platform/opencode-runtime/management/overview` | 查询 Linux 服务器、后端 Java 进程、容器、管理进程、manager-backend 连接和 manager 上报的本地 opencode server 明细。 |
| `GET` | `/api/internal/platform/opencode-runtime/management/user-processes` | 按用户关键字查询用户 opencode server 进程，并返回 manager/PID 与 opencode HTTP 健康检查后的实际状态。 |
| `GET` | `/api/internal/platform/opencode-runtime/management/containers/{containerId}/metrics` | 查询指定容器 Redis 中近 48 小时运行指标历史。 |
| `GET` | `/api/internal/platform/opencode-runtime/management/linux-servers/{linuxServerId}/backend-metrics` | 主接口：按稳定服务器身份查询后端 Java 服务 Redis 中近 48 小时运行指标历史。 |
| `GET` | `/api/internal/platform/opencode-runtime/management/backend-processes/{backendProcessId}/metrics` | 兼容入口：先解析当前后端进程所属 `linuxServerId` 后委托服务器级查询，无法解析时才回退旧样本读取。 |
| `POST` | `/api/internal/platform/opencode-runtime/management/containers/{containerId}/processes/{port}/restart` | 重启指定容器端口上的 opencode server；平台已有用户进程记录时，manager `STARTED` 后还必须在公共启动等待窗口内 health healthy 才视为成功。 |
| `POST` | `/api/internal/platform/opencode-runtime/management/containers/{containerId}/processes/{port}/stop` | 停止指定容器端口上的 opencode server；平台已有用户进程记录时，manager `STOPPED` 后还必须 health 不健康才视为成功。 |

查询参数：

| 参数 | 说明 |
|---|---|
| `page` | opencode server 进程分页页码，默认 `1`。 |
| `size` | opencode server 进程分页大小，默认 `20`，上限沿用平台 `PageRequest` 的 `200`。 |
| `status` | 可选进程状态；当前活进程视图只返回 `RUNNING` opencode server 进程，非 `RUNNING` 状态会返回空进程页。 |
| `linuxServerId` | 可选 Linux 服务器稳定身份，来自 `TEST_AGENT_LINUX_SERVER_ID` 或 Java 主机名。 |
| `containerId` | 可选容器 ID；Windows 本机开发态为机器名。 |
| `username` | 可选用户名，运行管理页按用户名筛选和展示。 |
| `userId` | 可选用户 ID，保留给旧客户端兼容；新客户端应使用 `username`。 |

`user-processes` 查询参数：

| 参数 | 说明 |
|---|---|
| `keyword` | 必填用户关键字，按用户名、`userId` 或统一认证号定位用户。 |
| `page` | 用户 opencode server 进程分页页码，默认 `1`。 |
| `size` | 用户 opencode server 进程分页大小，默认 `20`，上限沿用平台 `PageRequest` 的 `200`。 |

指标历史查询参数：

| 参数 | 说明 |
|---|---|
| `windowMinutes` | 可选历史窗口分钟数，默认 `60`；只允许 `1`、`30`、`60`、`360`、`720`、`1440`、`2880`，分别对应 1 分钟、30 分钟、1 小时、6 小时、12 小时、24 小时、48 小时。传入时优先于兼容参数 `hours`。 |
| `hours` | 兼容旧客户端的可选历史窗口小时数，最大 `48`，最小 `1`；新客户端应使用 `windowMinutes`。 |
| `maxPoints` | 可选最大返回点数，默认 `720`，最大 `2000`。Redis 保存每 5 秒原始样本，接口在超出上限时按时间桶降采样。 |

进程命令路径参数：

| 参数 | 说明 |
|---|---|
| `containerId` | 必填容器 ID，用于路由到当前连接的 manager。 |
| `port` | 必填端口号，范围 `1..65535`，用于定位 manager 本地托管的 opencode server。 |

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
  "containers": [
    {
      "containerId": "ctr_...",
      "linuxServerId": "10.8.0.21",
      "containerName": "opencode-a",
      "portStart": 4096,
      "portEnd": 4100,
      "maxProcesses": 8,
      "currentProcesses": 3,
      "availableCapacity": 5,
      "metricsSource": "cgroup",
      "cpuUsagePercent": 12.5,
      "memoryMaxBytes": 1073741824,
      "memoryUsedBytes": 536870912,
      "memoryUsagePercent": 50.0,
      "diskReadBytesPerSecond": 1024.0,
      "diskWriteBytesPerSecond": 2048.0,
      "status": "READY",
      "lastHeartbeatAt": "2026-06-24T08:00:00Z",
      "createdAt": "2026-06-24T07:00:00Z",
      "updatedAt": "2026-06-24T08:00:00Z",
      "traceId": "trace_..."
    }
  ],
  "managers": [
    {
      "managerId": "mgr_...",
      "containerId": "ctr_...",
      "linuxServerId": "10.8.0.21",
      "protocolVersion": "opencode-manager.v1",
      "connectionStatus": "CONNECTED",
      "capabilities": {
        "commands": ["start", "health", "stop", "restart"]
      },
      "lastHeartbeatAt": "2026-06-24T08:00:00Z",
      "createdAt": "2026-06-24T07:00:00Z",
      "updatedAt": "2026-06-24T08:00:00Z",
      "traceId": "trace_...",
      "managedProcesses": [
        {
          "port": 4096,
          "pid": 12345,
          "baseUrl": "http://10.8.0.21:4096",
          "sessionPath": "/data/.testagent/agent-opencode/.session/4096",
          "configPath": "/data/.testagent/agent-opencode/.config/opencode/",
          "startedAt": "2026-06-24T07:30:00Z",
          "startCommand": "XDG_DATA_HOME=/data/.testagent/agent-opencode/.session/4096 OPENCODE_CONFIG_DIR=/data/.testagent/agent-opencode/.config/opencode/ opencode serve --hostname 0.0.0.0 --port 4096 --print-logs",
          "traceId": "trace_...",
          "ownership": "BOUND",
          "processId": "ocp_...",
          "processStatus": "RUNNING",
          "healthMessage": "OK",
          "userId": "usr_...",
          "username": "wr",
          "bindingAgentId": "opencode",
          "bindingStatus": "ACTIVE",
          "bindingUpdatedAt": "2026-06-24T07:30:00Z"
        }
      ]
    }
  ],
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
        "managerStatus": "RUNNING",
        "healthStatus": "HEALTHY",
        "restartable": false,
        "sessionPath": "/data/.testagent/agent-opencode/.session/4101",
        "configPath": "/data/.testagent/agent-opencode/.config/opencode/",
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

`user-processes` 响应 `data`：

```json
{
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
      "status": "STOPPED",
      "managerStatus": "NOT_RUNNING",
      "healthStatus": "NOT_RUNNING",
      "restartable": true,
      "sessionPath": "/data/.testagent/agent-opencode/.session/4101",
      "configPath": "/data/.testagent/agent-opencode/.config/opencode/",
      "startedAt": "2026-06-24T07:30:00Z",
      "lastHealthCheckAt": "2026-06-24T08:00:00Z",
      "healthMessage": "process pid is not alive",
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
  "total": 1
}
```

进程命令响应 `data`：

```json
{
  "command": "restart",
  "status": "STARTED",
  "port": 4096,
  "pid": 12345,
  "baseUrl": "http://10.8.0.21:4096",
  "sessionPath": "/data/.testagent/agent-opencode/.session/4096",
  "configPath": "/data/.testagent/agent-opencode/.config/opencode/",
  "healthy": true,
  "message": "opencode server started",
  "traceId": "trace_..."
}
```

后端 Java 进程行会额外返回 `cpuUsagePercent`、`memoryMaxBytes`、`memoryUsedBytes`、`memoryUsagePercent`、`diskMaxBytes`、`diskUsedBytes`、`diskUsagePercent`、`jvmMemoryUsedBytes`、`jvmMemoryCommittedBytes`、`jvmMemoryMaxBytes`、`jvmGcPauseMillis`、`jvmThreadsLive`，字段不可采集时为 `null`。容器行和容器 history 样本可返回 `metricsSource`，取值为 `cgroup`、`process`、`unavailable` 或旧样本的 `null`；容器 history 样本包含 `sampledAt/maxProcesses/currentProcesses/metricsSource/cpuUsagePercent/memoryMaxBytes/memoryUsedBytes/memoryUsagePercent/diskReadBytesPerSecond/diskWriteBytesPerSecond`；后端 history 响应包含 `linuxServerId`，`backendProcessId` 保留为可空兼容字段。后端 history 样本保持 overview 后端指标同名字段，服务器 CPU、内存、磁盘来自 `test-agent:runtime-metrics:server:{linuxServerId}`，JVM 指标来自 `test-agent:runtime-metrics:backend:{linuxServerId}`，因此同一服务器上的 Java 后端重启后服务器资源和 JVM 趋势都按 `linuxServerId` 连续查询；旧 `backendProcessId` history API 先解析到 `linuxServerId` 后委托同一稳定服务器身份主接口，无法解析时才尝试读取旧样本。运行管理页面按 `linuxServerId` 合并服务器与 Java 行，点击行或“趋势”按钮查询该服务器 Java 服务指标历史；异常缺服务器或缺 Java 时展示 `-`，不改变 overview JSON 结构。`managers[].managedProcesses[]` 在 manager 本地快照字段之外可返回归属字段：`ownership` 为 `BOUND` 表示该端口匹配到当前 `ACTIVE` 用户绑定，前端展示为“有主进程”；`UNBOUND` 表示没有当前活跃绑定或没有匹配用户进程，前端展示为“无主进程”。`processId`、`processStatus`、`healthMessage` 来自同服务器同容器同端口的用户进程候选；`userId`、`username`、`bindingAgentId`、`bindingStatus`、`bindingUpdatedAt` 仅在 `ACTIVE` 绑定存在时返回。所有归属字段保持可空/可缺失以兼容旧后端、旧 manager 和旧 Redis 快照；运行管理页面按 `containerId` 合并容器与 manager 行，若容器 `currentProcesses` 与 `managedProcesses.length` 不一致，会提示容量计数来自 manager state，而明细来自 manager 上报数组。`user-processes` 返回的 `managerStatus` 表示 manager/PID 层面的实际状态，`healthStatus` 表示 opencode HTTP 健康检查结果：`HEALTHY` 为健康，`NOT_RUNNING` 表示 manager 确认进程未运行或 PID 不存在，`UNHEALTHY` 表示 PID 存在但 HTTP 健康检查失败，`CHECK_FAILED` 表示 manager 通信或探测异常；`restartable=true` 时前端允许调用 restart 命令。Manager 与后端连接不再以独立表格展示，前端改为使用 `managerBackendConnections[]` 生成 Java 到 manager 的连线，并使用 `managers[].managedProcesses[]` 生成 manager 到 opencode server 的连线；旧响应缺少 `managedProcesses` 时仍展示 manager 节点，manager 到 opencode 的边为空。展开明细中的“重启/停止”按钮调用上述 HTTP 命令端点，成功后前端重新拉取 overview；底部用户进程列表中的“重启”按钮同样按该进程的 `containerId + port` 调用 restart，成功后刷新当前用户查询。对已有平台用户进程记录的端口，restart 成功必须经过公共启动服务再次 health 确认；用户进程已 `STOPPED` 或 manager 返回 `port ... is not managed` 时，目标 Java 会复用原 `containerId + port` 调用 manager `start` 并同样确认 health 后才返回成功。对已有平台用户进程记录的端口，stop 成功必须经过公共停止服务再次 health 确认，health 仍 healthy 时返回统一错误且不回写 `STOPPED`；health 不健康时才回写 `STOPPED`，返回的 command result 中 `healthy=false`。没有平台用户进程记录的无主 manager state 仍只同步返回本次 manager 回包。命令结果不代表后续 Redis 快照一定已经刷新。拓扑列表固定最多返回 500 条，避免管理页一次性读取过多连接和进程快照。Java 后端每 5 秒按 `backendProcessId` 写入 Redis Java 快照，并按 `linuxServerId` 分组用于服务器级展示和目标选择；Go manager 每 5 秒通过 WebSocket 写入 Redis manager 快照，两类快照 TTL 固定 10 秒；manager 成功应用 `configUpdate` 时会立即补发心跳，使容量参数变更尽快进入 overview。运行管理前端打开页面后每 5 秒刷新 overview，避免长时间停留时继续展示旧 Redis 快照；底部用户进程查询只在输入用户关键字后触发，不随 overview 自动展示所有进程。数据库中的历史 heartbeat 字段保留兼容但不参与在线判断。Java/manager 运行指标历史写入 Redis ZSET，保留近 48 小时原始 5 秒样本，history API 默认查询近 1 小时，前端使用 `windowMinutes` 在 1 分钟到 48 小时预设之间切换，超出 `maxPoints` 时按时间桶降采样。Redis 历史只保证同一稳定服务器身份的 Java 后端重启后连续；若 Redis 自身重启且未启用 AOF/RDB，历史样本会丢失。opencode server 由后端每 3 分钟通过 manager health 命令确认并刷新 Redis 进程心跳，Redis 进程心跳 key 5 分钟过期，索引清理每 5 分钟执行一次。`opencodeProcesses.items[]` 的 `bindingAgentId`、`bindingStatus`、`bindingUpdatedAt` 仅在该进程仍是当前用户绑定时返回，否则为 `null`。

### scheduler-management 定时任务管理 API

定时任务管理 API 是高权限平台接口，只允许已认证用户且角色包含 `SUPER_ADMIN` 访问。未认证返回 `UNAUTHENTICATED`，非超级管理员返回 `FORBIDDEN`，非法分页、任务 key、状态、触发类型、Cron 或锁 TTL 返回 `VALIDATION_ERROR`。本接口只管理框架任务定义和运行记录，不开放普通用户级 Cron 计划创建 API，也不创建定时会话或 Run。

Base URL：`/api/internal/platform/scheduler-management`

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/tasks` | 分页查询代码注册的任务定义。 |
| `GET` | `/tasks/{taskKey}` | 查询单个任务定义。 |
| `PATCH` | `/tasks/{taskKey}` | 调整任务启停、Cron 表达式和锁 TTL。 |
| `POST` | `/tasks/{taskKey}/trigger` | 创建管理员手动触发运行记录，后台 runner 异步执行；任务停用时超级管理员仍可手动触发。 |
| `GET` | `/runs` | 分页查询运行记录，可按任务、状态、触发类型和请求用户过滤。 |
| `GET` | `/runs/{taskRunId}` | 查询单次运行记录详情。 |
| `POST` | `/runs/{taskRunId}/stop` | 对正在执行的运行记录发起协作式停止。 |

`GET /tasks` 查询参数：

| 参数 | 说明 |
|---|---|
| `page` | 页码，默认 `1`。 |
| `size` | 分页大小，默认 `50`，上限沿用平台 `PageRequest` 的 `200`。 |

任务响应字段：

```json
{
  "taskKey": "daily.cleanup",
  "name": "Daily Cleanup",
  "cronExpression": "0 0 2 * * *",
  "enabled": true,
  "lockTtlSeconds": 300,
  "nextFireAt": "2026-06-25T02:00:00Z",
  "registrationStatus": "REGISTERED",
  "registrationStatusLabel": "已注册",
  "currentRun": null,
  "latestRun": {
    "taskRunId": "str_...",
    "status": "SUCCEEDED",
    "statusLabel": "成功",
    "triggerType": "CRON",
    "triggerTypeLabel": "定时触发",
    "requestedByUserId": null,
    "scheduledFireAt": "2026-06-25T02:00:00Z",
    "startedAt": "2026-06-25T02:00:01Z",
    "endedAt": "2026-06-25T02:00:02Z",
    "ownerInstanceId": "backend-..."
  },
  "createdAt": "2026-06-24T08:00:00Z",
  "updatedAt": "2026-06-24T08:00:00Z",
  "traceId": "trace_..."
}
```

`PATCH /tasks/{taskKey}` 请求体，字段均可选，缺失表示保持原值：

```json
{
  "enabled": false,
  "cronExpression": "0 0 3 * * *",
  "lockTtlSeconds": 600
}
```

`GET /runs` 查询参数：

| 参数 | 说明 |
|---|---|
| `page` / `size` | 分页参数，默认 `1/50`。 |
| `taskKey` | 可选任务 key。 |
| `status` | 可选：`PENDING`、`RUNNING`、`STOPPING`、`SUCCEEDED`、`FAILED`、`SKIPPED`、`MANUALLY_STOPPED`。 |
| `triggerType` | 可选：`CRON`、`MANUAL`、`USER_PLAN`。首版 HTTP 只创建 `MANUAL`。 |
| `requestedByUserId` | 可选管理员用户 ID。 |

运行记录响应字段：

```json
{
  "taskRunId": "str_...",
  "taskKey": "daily.cleanup",
  "planId": null,
  "triggerType": "MANUAL",
  "triggerTypeLabel": "手工触发",
  "status": "MANUALLY_STOPPED",
  "statusLabel": "人工停止",
  "requestedByUserId": "usr_...",
  "scheduledFireAt": "2026-06-25T02:00:00Z",
  "startedAt": "2026-06-25T02:00:01Z",
  "endedAt": "2026-06-25T02:00:02Z",
  "ownerInstanceId": "backend-...",
  "stopRequestedAt": "2026-06-25T02:00:01Z",
  "stopRequestedByUserId": "usr_...",
  "stopReason": "管理员手工停止",
  "skipReason": null,
  "errorCode": null,
  "errorMessage": null,
  "result": {},
  "traceId": "trace_...",
  "createdAt": "2026-06-25T02:00:00Z",
  "updatedAt": "2026-06-25T02:00:02Z"
}
```

兼容性与审计：

- `scheduled_task_plans` 只作为用户级 Cron 计划预留模型，本批次不开放普通用户 HTTP API。
- 同一 `taskKey` 已有 `PENDING`、`RUNNING` 或 `STOPPING` 记录时，管理员手动触发返回统一 `CONFLICT` 错误，不创建新的手动运行记录；Cron 调度重叠仍会写入 `SKIPPED` 并保存 `skipReason`。
- `POST /runs/{taskRunId}/stop` 只允许停止 `RUNNING` 记录，成功后状态先变为 `STOPPING` 并记录 `stopRequestedAt/stopRequestedByUserId/stopReason`；handler 协作退出后 runner 保存终态 `MANUALLY_STOPPED`。终态、`PENDING`、不存在记录返回统一错误。
- `TaskResponse`、`RunResponse` 的中文 label 由后端按字典表查询，字典缺失时回退为原 code；不新增通用字典查询 API。
- 分布式互斥只使用 Redis 锁；Redis 不可用时 scheduler 不降级为本机锁。
- 对应测试：`SchedulerManagementControllerTest`、`SchedulerManagementServiceTest`、`ScheduledTaskRunnerTest`。

`POST /api/runs` 请求体：

```json
{
  "sessionId": "ses_...",
  "prompt": "run prompt"
}
```

Run 路由、远端 session 解析和事件订阅完成后，接口立即返回 `RUNNING`，不等待 agent 的 prompt HTTP 请求完成。prompt 提交或后续事件流失败时通过同一 RunEvent 链路追加 `run.failed`，前端不应把创建 Run 接口的等待时间当作智能体执行超时。

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
  "mode": "build",
  "command": "generate-cases-path",
  "arguments": "对车贷的开发文档，生成路径图"
}
```

`command` / `arguments` 为可选字段。提供 `command` 时，平台仍先创建并持久化 Run、订阅 RunEvent，再由后端后台调用 opencode 原生 `/session/{sessionID}/command`；创建 Run 接口不会等待技能执行完成。这样 slash 技能与普通 prompt 共用 active-run 恢复、SSE 实时输出和取消语义。未提供 `command` 时继续使用 `prompt_async`。

兼容要求：

- 旧 `prompt: string` 继续有效；`parts` 缺失时后端按单个 text part 处理。
- `parts`、`messageId`、`agent`、`model`、`variant`、`mode` 均为可选字段，旧前端不需要改动。
- `parts` 会下沉为当前 agent runtime 的 prompt parts；`opencode` 实现适配为 `prompt_async` 的 `text/file/agent` parts，`reference` part 会转换为可读 text part。
- file part 带 `source.text` 或 `content` 时后端生成 `data:` URL；前端图片附件可直接提交 `url: "data:<mime>;base64,..."`。只有没有内联内容或 URL 时，后端才把 workspace 内路径转为 `file://` URL，越出 workspace 的路径返回 `VALIDATION_ERROR`。
- `model` 使用 `providerId/modelId` 字符串格式；未启用托管模型目录时，格式不完整仍保持旧行为，不向 opencode 传 model override。
- 当后端启用托管模型目录时，前端从 Model 目录接口获取可选模型并仍按 `providerId/modelId` 提交；后端会按当前模型目录校验该期望模型。请求缺失、格式不完整或模型已不在当前目录内时，后端回退到 `defaultModel=true` 的模型，找不到默认项时使用目录首项；目录为空时返回 `VALIDATION_ERROR`，不启动远端 run。企业内默认模型为 `icbc-openai/DeepSeek-V4-Flash-W8A8`。
- Agent/Model/Variant/Mode 属于运行态选择，不代表 Provider/server/settings 配置；其中 `mode` 当前只保留为平台字段，opencode `PromptInput` 不支持该字段，因此 opencode runtime 不写入 `prompt_async` 请求体。

启动流程会先校验当前认证用户是否已有 `READY` opencode 进程；未就绪时返回 `OPENCODE_UNAVAILABLE`，不创建本地 Run。校验通过后追加用户消息，创建 `PENDING` Run，并使用当前用户进程投影出的 `executionNodeId = "node_" + processId` 和进程记录中的 `baseUrl` 作为本次运行目标；`baseUrl` 由当前 advertised host 与端口生成。若 `(sessionId, agentId)` 的既有 `agent_session_bindings` 指向的节点不是当前用户进程节点，后端会重新创建远端 session 并覆盖绑定；旧 `sessions.opencode_*` 字段只作为 `opencode` 兼容回填来源。无用户主体的兼容调用（例如 static API token、本地放行或旧系统集成）继续走固定 `execution_nodes` 路由，不要求用户进程。
Run 进入成功、失败或取消终态后，后端会尝试拉取 agent 标准 session messages，将 assistant 可见 text、完整 parts、token/cost 快照 upsert 到 `session_messages`，并把同一份 token/cost 写入 `runs`；reasoning 和 tool output 不拼入可见正文，拉取失败时保留数据库已有快照。

### system-management 用户管理（测试）API

用户管理（测试）API 是高权限平台接口，仅用于研发测试便捷造号，只允许已认证用户且角色包含 `SUPER_ADMIN` 访问。未认证返回 `UNAUTHENTICATED`，非超级管理员返回 `FORBIDDEN`。创建用户时使用默认密码 `123456`，前端不传密码字段。

Base URL：`/api/internal/platform/system-management`

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/users` | 分页查询用户列表，可按关键字匹配用户名/统一认证号。 |
| `POST` | `/users` | 创建测试用户，密码默认为 `123456`，并授予单个角色。 |
| `GET` | `/roles` | 查询可选角色列表，供前端新增用户下拉选择。 |

`GET /users` 查询参数：

| 参数 | 说明 |
|---|---|
| `keyword` | 可选，按用户名/统一认证号模糊匹配。 |
| `page` | 页码，默认 `1`。 |
| `size` | 分页大小，默认 `50`，上限 `200`。 |

用户响应字段（不含密码）：

```json
{
  "userId": "usr_...",
  "username": "alice",
  "unifiedAuthId": "AUTH_1",
  "organization": "工行",
  "rdDepartment": "研发部",
  "department": "测试部",
  "status": "ACTIVE",
  "roles": ["APP_ADMIN"],
  "roleLabels": ["应用管理员"],
  "createdAt": "2026-06-26T00:00:00Z"
}
```

`POST /users` 请求体：

```json
{
  "unifiedAuthId": "AUTH_2",
  "username": "bob",
  "role": "USER",
  "organization": "工行",
  "rdDepartment": "研发部",
  "department": "测试部"
}
```

- `unifiedAuthId`、`username`、`role` 必填；`organization`/`rdDepartment`/`department` 选填。
- 密码由后端注入默认值 `123456`，前端不传。
- 用户名或统一认证号已存在时返回 `CONFLICT`。
- 角色无效时返回 `VALIDATION_ERROR`。

`GET /roles` 响应：

```json
[
  { "roleCode": "SUPER_ADMIN", "roleLabel": "超级管理员" },
  { "roleCode": "SYSTEM_ADMIN", "roleLabel": "系统管理员" },
  { "roleCode": "APP_ADMIN", "roleLabel": "应用管理员" },
  { "roleCode": "USER", "roleLabel": "普通用户" }
]
```

#### 查询数据库 IDENTITY 状态

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/identity` | 查询白名单表 identity 状态，含当前值、max(id)、是否错位。 |

权限：仅 `SUPER_ADMIN`。响应 `data` 为数组，每项字段：

| 字段 | 说明 |
|---|---|
| `table` | 枚举名，如 `USERS` |
| `tableName` | 真实表名，如 `users`（仅展示） |
| `currentValue` | identity 序列当前 `last_value`，表空时为 `null` |
| `maxId` | 表中当前 `max(id)`，表空时为 `null` |
| `conflict` | 序列落后于已有主键时为 `true`（需对齐） |
| `lastUpdatedAt` | 本次查询时间 |

示例：

```json
[
  { "table": "USERS", "tableName": "users", "currentValue": 8, "maxId": 8, "conflict": false, "lastUpdatedAt": "..." },
  { "table": "USER_ROLES", "tableName": "user_roles", "currentValue": 1000000, "maxId": 1000000, "conflict": false, "lastUpdatedAt": "..." }
]
```

#### 对齐数据库 IDENTITY 到 max(id)+1

| 方法 | 路径 | 用途 |
|---|---|---|
| `POST` | `/identity/align` | 把指定表 identity 对齐到 `max(id)+1`。 |

权限：仅 `SUPER_ADMIN`。请求体：

```json
{ "table": "USERS" }
```

`table` 为白名单枚举名（`USERS`/`USER_ROLES`/`DICTIONARIES`/`USER_LOGIN_LOGS`）。响应为对齐后的单表状态。错误码：`VALIDATION_ERROR`（表名不在白名单）、`FORBIDDEN`（非超管）。

#### 手动重启数据库 IDENTITY 到目标值

| 方法 | 路径 | 用途 |
|---|---|---|
| `POST` | `/identity/restart` | 手动重启 identity 到目标值。 |

权限：仅 `SUPER_ADMIN`。请求体：

```json
{ "table": "USERS", "targetValue": 1000000 }
```

`targetValue` 必须为正整数且大于当前 `maxId`（不允许往回滚）。响应为重启后的单表状态。错误码：`VALIDATION_ERROR`（表名非法、目标值非正整数或 ≤ maxId）、`FORBIDDEN`（非超管）。

### opencode Web Runtime API

opencode Web App 运行态能力统一由 `test-agent-api` 的 runtime Controller 暴露。前端仍只调用平台后端 API，后端通过 `test-agent-opencode-runtime -> test-agent-agent-runtime -> test-agent-opencode-client` 访问 opencode HTTP API，不返回 generated SDK DTO，不允许 Controller 直接调用 generated SDK。

运行态代理与 Run 使用同一套目标解析规则：

- 已登录用户访问默认 `opencode` agent 时，workspace 级目录、文件、配置、provider、MCP 等接口会先校验当前用户已有 `READY` opencode 进程，并使用该进程投影出的 `executionNodeId = "node_" + processId` 与进程记录中的 `baseUrl` 调用 opencode；未初始化或健康检测失败返回 `OPENCODE_UNAVAILABLE`。
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
- `source=external` 时，`/api/models` 由后端请求外部 OpenAI-compatible `/models` 获取；请求失败时返回配置内置外网模型，Provider 默认为 `external-openai`。历史 `source=bailian` 会按 `external` 兼容处理。
- `source=internal` 时，`/api/models` 从 `ai_model_configs` 表读取启用模型，Provider 为 `icbc-openai`；启动时会按 openclaw 企业 patch 的模型清单初始化表，默认模型为 `DeepSeek-V4-Flash-W8A8`。
- Model 响应对象包含兼容字段 `id`、`modelId`、`modelID`、`providerId`、`providerID`、`name`，托管来源还会返回 `contextLimit`、`outputLimit` 和 `defaultModel`。前端优先选中 `defaultModel=true` 的模型；浏览器已保存的模型偏好若不在当前目录内或与 provider 不匹配，会自动清理并切回当前默认模型。

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

SSE 建连时，后端会先尝试从当前 Run 绑定的 agent remote session 拉取标准 session messages，并仅把 assistant 消息转换为 transient `message.updated` / `message.part.updated` 发给前端；user 消息已在 Run 启动前由平台保存，不重复回放其 text part，避免被前端误拼进 assistant 正文。随后进入 `run_events` durable replay 与 live bus 合流。高频文本 delta、大段日志和 bash/tool output 不写入 `run_events`；如果远端 session 不可用或拉取失败，后端跳过消息恢复，不阻断 Run 状态、Diff、permission/question 等 durable RunEvent 回放。

`GET /api/internal/agent/{agentId}/runs/{runId}/session-tree/messages` 返回当前 Run scope 的消息树快照，旧 `/api/runs/{runId}/session-tree/messages` 和平台内部 URL 继续兼容。scope 表存在时后端按 root + 当前 Run child session 逐个拉取 agent projected messages；scope 表为空时按旧 root-only 远端 session 降级。响应 `events` 会合并本次消息 snapshot 与当前 Run 的 durable RunEvent，因此 permission/question/todo 等状态类事件可随 HTTP 历史响应恢复；`messagesBySessionId` 只包含 `message.*` payload，不会混入状态事件。响应 `data`：

```json
{
  "runId": "run_...",
  "sessions": [
    {
      "rootSessionId": "ses_root",
      "sessionId": "ses_child",
      "parentSessionId": "ses_root",
      "childSession": true,
      "taskMessageId": "msg_task",
      "taskPartId": "part_task",
      "taskCallId": "call_task"
    }
  ],
  "childSessionIdByTaskPartId": { "part_task": "ses_child" },
  "messagesBySessionId": {
    "ses_child": [
      { "sessionId": "ses_child", "message": { "id": "msg_...", "role": "assistant" } }
    ]
  },
  "events": [
    { "type": "message.updated", "sessionId": "ses_child", "payload": {} },
    { "type": "permission.asked", "sessionId": "ses_child", "payload": { "requestId": "perm_..." } }
  ]
}
```

该接口是 HTTP snapshot 辅助入口，不替代 RunEvent SSE。它只返回当前 Run scope 子树；root session 下全量历史 child 使用 `GET /api/internal/agent/{agentId}/sessions/{sessionId}/session-tree/messages` 查询。Session 级接口会按消息 snapshot 中的远端 `rootSessionId` 补读 `run_events.root_session_id` 下的 durable 状态事件，用于恢复跨 Run child permission/question/todo 状态。

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

Actuator health 由 Spring Boot Actuator 提供，数据库健康使用 Spring Boot/Druid 数据源；固定 opencode node yml 配置已作废，不再作为 Actuator health 来源；Redis 是系统必需依赖，健康检查会做 TCP 连通探测。

### 兼容性

- API 不暴露数据库 surrogate PK。
- API 不暴露 `agent_session_bindings`、`opencodeSessionId`、`opencodeExecutionNodeId` 或 generated SDK DTO；前端只依赖平台 Workspace、Session、Run、Cancel 和 RunEvent SSE。
- 旧 `/api/...` URL 默认保持兼容；已明确作废的入口除外。新增 URL 只能作为并行入口补充，不能无计划删除旧 URL。
- 响应 DTO 可以新增字段，前端必须忽略未知字段。
- 文件 API 初版不承诺 Git 状态、二进制预览、递归扫描和搜索。
- RunEvent payload 可以新增字段；事件 wire name 不可重命名。
