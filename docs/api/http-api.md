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
6. 旧 runtime/workspace `/api/...` URL 已强制作废，命中时统一返回 `410 API_GONE` 和 `ApiErrorResponse`；登录认证 `/api/auth/login|logout|me|refresh` 保留为稳定入口。
7. CORS 本地默认仅覆盖主前端与 `frontend-opencode` 的 localhost/127.0.0.1 开发、预览和 real E2E 端口；生产必须通过 `TEST_AGENT_CORS_ALLOWED_ORIGINS` 显式配置允许来源。

## API URL 分层

平台业务能力只暴露新的 internal platform 或 agent-scoped URL。旧 runtime/workspace `/api/...` 兼容 URL 不再进入 Controller、业务服务或跨 Java 转发，统一由 `LegacyApiGoneWebFilter` 返回 `410 API_GONE`，响应仍携带 `traceId`。

| URL 前缀 | 用途 |
|---|---|
| `/api/auth/login`、`/api/auth/logout`、`/api/auth/me`、`/api/auth/refresh` | 当前稳定登录认证入口，暂不平台化。 |
| `/api/...` 旧 runtime/workspace URL | 已作废，返回 `410 API_GONE`；包括旧 `/api/runs/**`、`/api/sessions/**`、`/api/workspaces/**`、`/api/agents`、`/api/models`、`/api/providers`、`/api/commands`、`/api/references`、`/api/status`、`/api/fs/**`、`/api/vcs/**`、`/api/lsp/**`、`/api/mcp/**`、`/api/config`、`/api/global/**`、`/api/provider/**`、`/api/worktrees/**`。 |
| `/api/internal/platform/{business-project}/{business}/...` | 前端调用平台自身能力的新入口。 |
| `/api/internal/agent/{agentId}/...` | 与具体 agent 交互的新入口，`agentId` 由前端 URL 传递；当前唯一可运行值为 `opencode`。 |
| `/api/internal/platform/opencode-runtime/manager-backends` | 已作废，返回 `410 API_GONE`；运行管理请使用 `management/overview`。 |
| `/api/internal/platform/opencode-runtime/management/overview` | 超级管理员只读运行管理入口，使用用户 JWT 且要求 `SUPER_ADMIN`。 |
| `/api/internal/platform/scheduler-management` | 超级管理员定时任务管理入口，使用用户 JWT 且要求 `SUPER_ADMIN`。 |
| `/api/internal/platform/system-management` | 超级管理员用户管理入口，使用用户 JWT 且要求 `SUPER_ADMIN`。 |
| `/api/public/...` | 其他系统调用平台的公开 API，当前预留；新增前必须完成鉴权、限流、安全和兼容性设计。 |

当前已落地的新平台入口：

| 业务工程 | 新 URL 示例 | 旧 URL 状态 |
|---|---|---|
| `workspace-management` | `/api/internal/platform/workspace-management/workspaces` | 旧 `/api/workspaces` 返回 `410 API_GONE`。 |
| `workspace-management` | `/api/internal/platform/workspace-management/workspaces/{workspaceId}/file-ws-route` + `/api/internal/platform/workspace-management/file-ws/tickets` + 文件 WebSocket | 旧 HTTP 文件接口和旧 `/api/workspaces/{workspaceId}/file-ws-route` 返回 `410 API_GONE`。 |
| `workspace-management` | `/api/internal/platform/workspace-management/file-ws/tickets` | 无旧 URL |
| `workspace-management` | `/api/internal/platform/workspace-management/agent-config/public/status` | 无旧 URL |
| `workspace-management` | `/api/internal/platform/workspace-management/agent-config/operations/{operationId}/tickets` | 无旧 URL |
| `workspace-management` | `/api/internal/platform/workspace-management/backend-servers` | 无旧 URL |
| `workspace-management` | `/api/internal/platform/workspace-management/applications` | 无旧 URL |
| `workspace-management` | `/api/internal/platform/workspace-management/applications/{appId}/workspace-templates/{templateId}/versions` | 无旧 URL |
| `workspace-management` | `/api/internal/platform/workspace-management/workspace-versions/{versionId}/personal-workspaces` | 无旧 URL |
| `opencode-runtime` | `/api/internal/platform/opencode-runtime/sessions` | 旧 `/api/sessions/**` 返回 `410 API_GONE`。 |
| `opencode-runtime` | `/api/internal/agent/{agentId}/sessions/{sessionId}/run-context` | 无旧 URL；签发后续 Run 使用的会话运行上下文。 |
| `opencode-runtime` | `/api/internal/agent/{agentId}/runs` 或 `/api/internal/platform/opencode-runtime/runs` | 旧 `/api/runs/**` 返回 `410 API_GONE`。 |
| `opencode-runtime` | `/api/internal/agent/{agentId}/runs/{runId}/events` 或 `/api/internal/platform/opencode-runtime/runs/{runId}/events` | 旧 `/api/runs/{runId}/events` 返回 `410 API_GONE`。 |
| `opencode-runtime` | `/api/internal/agent/{agentId}/runs/{runId}/session-tree/messages` 或 `/api/internal/platform/opencode-runtime/runs/{runId}/session-tree/messages` | 旧 `/api/runs/{runId}/session-tree/messages` 返回 `410 API_GONE`。 |
| `opencode-runtime` | `/api/internal/agent/{agentId}/sessions/{sessionId}/session-tree/messages` 或 `/api/internal/platform/opencode-runtime/sessions/{sessionId}/session-tree/messages` | 旧 `/api/sessions/{sessionId}/session-tree/messages` 返回 `410 API_GONE`。 |
| `opencode-runtime` | `/api/internal/platform/opencode-runtime/agents` | 旧 `/api/agents` 返回 `410 API_GONE`。 |
| `opencode-runtime` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/terminal/tickets` | 旧 `/api/sessions/{sessionId}/terminal/tickets|ws` 返回 `410 API_GONE`。 |
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
| `system-management` | `/api/internal/platform/system-management/users/{userId}/roles` | 无旧 URL |
| `system-management` | `/api/internal/platform/system-management/roles` | 无旧 URL |
| `configuration-management` | `/api/internal/platform/configuration-management/applications` | 无旧 URL |
| `configuration-management` | `/api/internal/platform/configuration-management/personal/ssh-keys` | 无旧 URL |
| `configuration-management` | `/api/internal/platform/configuration-management/workspace-create-operations/{operationId}` | 无旧 URL |
| `configuration-management` | `/api/internal/platform/configuration-management/common-parameters` | 无旧 URL |

当前已落地的 agent-scoped 入口示例：

| 新 URL | 平台业务实现 |
|---|---|
| `/api/internal/agent/{agentId}/sessions/{sessionId}/run-context` | 为当前登录用户签发会话运行上下文。 |
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
| `GET` | `/health?linuxServerId=&containerId=&port=` | 前端周期弱健康检查；只按 Redis 快照定位目标进程并直接调用 opencode `/global/health`，不读写数据库、不触发 manager 强健康检查。 | 无 | `UserOpencodeProcessHealthResponse` |
| `POST` | `/initialize` | 初始化或重建当前用户 opencode 进程。 | 可空；可传 `{ "operationId": "opi_..." }` 开启进度记录。 | `UserOpencodeProcessResponse` |
| `GET` | `/initialize-operations/{operationId}` | 只读查询当前用户发起的初始化进度；不触发 manager health/start，不写 RunEvent。 | 无 | `OpencodeProcessStartOperationResponse` |

`operationId` 由前端生成，格式为 `opi_` 开头，后续 8 到 120 位字母、数字、下划线或短横线；旧客户端不传 `operationId` 时 `POST /initialize` 保持同步返回兼容。

`GET /processes/me` 的可初始化判断和 `POST /initialize` 的容器候选只读取 TTL 10 秒的 Redis manager 快照，不读取 PostgreSQL 中历史 `CONNECTED/READY/current_processes` 做候选或回退。同一容器有多份快照时取 `lastHeartbeatAt` 最新一份；候选必须同时满足 manager 已连接、容器 READY 且实时容量未满、连接列表包含当前 Java 的 `backendProcessId`、当前 Java 内存中仍持有该 manager WebSocket。Redis 正常但无候选返回既有 `OPENCODE_UNAVAILABLE`/不可初始化响应；Redis 访问异常返回 `503 RUNTIME_STATE_UNAVAILABLE`。候选按实时进程数、容器 ID 排序，端口占用仍查询数据库并受 `(linux_server_id, port)` 唯一约束保护；命令发送前连接断开时可尝试下一候选，命令超时或发送后失败不切换。

`GET /health` 的三个 query 参数必须来自最近一次 `/processes/me` 响应中的 `linuxServerId`、`containerId`、`port`。请求到达非目标服务器时，入口 Java 会从 Redis 在线后端快照中随机选择目标 `linuxServerId` 的一个 Java 后端转发；已转发但仍未到达目标服务器或目标后端不可用时返回 HTTP 200 且 `healthy=false/status=BACKEND_UNAVAILABLE`，不返回 5xx。

`GET /processes/me` 还返回公共配置发布闸门字段：`messageSendAllowed`、`messageSendBlockedReason`、`publicConfigRolloutId`。兼容旧前端时字段缺失等同允许；新前端登录后每 5 秒调用轻量 `GET /processes/me/message-gate`，该接口只读 rollout 门禁，不触发 manager health 或进程状态写回，因此页面在发布前已经打开也能在下一轮发现门禁，当前用户旧实例 dispose 后下一轮立即恢复，不等待其他用户。闸门按当前登录用户判断：`PREPARING` 或所有服务器尚未同步完成时全部用户禁发；服务器同步完成后，只阻止当前用户仍未 dispose 的旧 opencode 实例。无法映射用户归属的存量目标在完成前保持全员门禁。该字段只用于提前反馈；`POST /runs`、旁路问答以及 opencode command/shell Session 的后端入口都会读取同一持久化闸门强制拒绝新消息，避免绕过前端或轮询延迟产生竞态。

`UserOpencodeProcessHealthResponse`：

```json
{
  "healthy": true,
  "status": "HEALTHY",
  "serviceStatus": "RUNNING",
  "linuxServerId": "server-a",
  "containerId": "ctr_01",
  "port": 4096,
  "baseUrl": "http://10.8.0.12:4096",
  "checkedAt": "2026-07-06T12:00:00Z",
  "message": "ok"
}
```

`status` 可为 `HEALTHY`、`UNHEALTHY`、`PROCESS_NOT_FOUND`、`MANAGER_UNAVAILABLE`、`BACKEND_UNAVAILABLE`。普通不可用状态均返回统一成功响应，前端按 `healthy=false` 更新运行态；参数非法、未认证或非 `opencode` agent 仍按统一错误格式返回。

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

对应测试：`RuntimeControllerTest`、`UserOpencodeBackendRoutingWebFilterTest`、`UserOpencodeWeakHealthRoutingWebFilterTest`、`OpencodeProcessStatusQueryServiceTest`、`UserOpencodeProcessAssignmentServiceTest`、`MyBatisOpencodeProcessStartOperationRepositoryIntegrationTest`、`backend-api.test.ts`、`workbench-utils.test.ts`、`OpencodeProcessStartupDialog.test.ts`。

## AI Run 整体回复反馈 API

Base URL：`/api/internal/platform/opencode-runtime`。该能力只写满意度反馈事实，不同步刷新运营汇总，也不依赖某条 assistant 消息。

鉴权：任意已登录用户。后端会校验当前用户是 Run 触发人或所属 Session 创建人；只有主对话 `SUCCEEDED` Run 可提交，其他状态或旁路问答返回 `CONFLICT`。

| 方法 | 路径 | 用途 | 请求体 | 响应 |
|---|---|---|---|---|
| `PUT` | `/runs/{runId}/feedback` | 按 Run 提交或更新整体回复反馈 | `{ "rating": "POSITIVE|NEGATIVE", "reasonCode": "WRONG_ANSWER|NOT_HELPFUL|DID_NOT_FOLLOW_INSTRUCTION|CODE_QUALITY_LOW|TEST_RESULT_BAD|TOO_SLOW|TOO_VERBOSE|TOO_SHORT|OTHER"?, "comment": string? }`，`comment` 最多 300 字 | `AiRunFeedback` |
| `GET` | `/runs/{runId}/feedback/me` | 查询当前用户对该 Run 的反馈 | 无 | `AiRunFeedback` 或 `null` |
| `POST` | `/run-feedbacks/me/query` | 批量查询可见 Run 的真实状态与当前用户反馈 | `{ "runIds": ["run_..."] }`，去重后最多 100 个 | `RunFeedbackState[]` |

旧 `/messages/{messageId}/feedback` 与 `/messages/{messageId}/feedback/me` 保留兼容：消息能关联 Run 时转入 Run 反馈逻辑，不能关联的历史消息继续使用消息口径。

`FeedbackResponse`：

```json
{
  "feedbackId": "fb_...",
  "sessionId": "ses_...",
  "runId": "run_...",
  "rating": "NEGATIVE",
  "reasonCode": "WRONG_ANSWER",
  "comment": "不准确",
  "createdAt": "2026-06-28T00:00:00Z",
  "updatedAt": "2026-06-28T00:00:00Z"
}
```

批量响应每项包含 `runId/sessionId/runStatus/feedback`；反馈加载失败不改变 Run 状态。对应测试：`AiRunFeedbackControllerTest`、`AiRunFeedbackApplicationServiceTest`、`AiMessageFeedbackControllerTest`。

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
| `CONVERSATION_CONTEXT_REQUIRED` | 409 | 需要会话运行上下文 |
| `CONVERSATION_CONTEXT_EXPIRED` | 409 | 会话运行上下文已过期 |
| `RUN_DETAILS_EXPIRED` | 410 | 运行详情已过期 |
| `RATE_LIMITED` | 429 | 请求过于频繁 |
| `INTERNAL_ERROR` | 500 | 服务器内部错误 |
| `OPENCODE_BAD_GATEWAY` | 502 | opencode 服务响应异常 |
| `OPENCODE_UNAVAILABLE` | 503 | opencode 服务不可用 |
| `OPENCODE_TIMEOUT` | 504 | opencode 服务超时 |
| `RUNTIME_STATE_UNAVAILABLE` | 503 | 运行态存储不可用 |
| `GIT_UNAVAILABLE` | 503 | Git 服务不可用 |
| `GIT_TIMEOUT` | 504 | Git 操作超时 |

Git 命令返回 `GIT_UNAVAILABLE` 或 `GIT_TIMEOUT` 时，错误 `details` 可能包含 `gitFailureType` 和 `gitFailureHint`，用于区分认证失败、仓库不可访问、网络连接失败、分支不存在、worktree 冲突、超时或未知失败；`gitFailureHint` 是可展示给管理员的安全排查提示，`stderr`、`command`、`timeoutMillis` 和 `durationMillis` 仅用于后端排查，不应在普通 UI 中直接展示。`command` 会隐藏 SSH/HTTP URL 中的用户名或 token。

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

- `GET status` 和文件 WebSocket 读取：任意已登录用户可读。
- 公共级写入、worktree、diff、stage、commit、publish 要求 `SUPER_ADMIN`，且必须操作当前用户自己的公共 worktree；共享公共 Git 根目录只用于初始化和运行时同步，不接受前端直接写入。工作空间级权限保持原有口径。
- 所有 SSH Git 操作使用当前登录 `SUPER_ADMIN` 保存的唯一 SSH key；未配置或配置多把时返回 `VALIDATION_ERROR`。
- Agent 配置文件的目录列表、读取、写入前端必须走平台文件 WebSocket；旧 HTTP `public/files*` 和 `workspaces/{workspaceId}/files*` 入口已作废，返回 `410 API_GONE`。

公共级接口：

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/public/status` | 查询公共 Agent Git 是否启用、根目录、agent 目录、当前分支和 commit。 |
| `GET` | `/public/branches` | 使用当前登录用户唯一 SSH key 实时查询公共 Agent Git 远端分支，不缓存；内部部署保存 `host[:port]/path` 片段时，后端会在执行 `git ls-remote` 前按当前用户统一认证号拼接 `ssh://{unifiedAuthId}@...`。 |
| `GET` | `/public/repositories` | 查询 Redis 中当前仍在线的后端服务器及当前后端的公共配置仓库初始化状态；在线 Java 快照按 `linuxServerId` 合并，响应不包含目标后端 `listenUrl`。 |
| `GET` | `/public/repositories/local` | 目标后端本机状态查询入口，仅供后端到后端代理使用。 |
| `POST` | `/public/repositories/{linuxServerId}/initialize` | 通过当前后端代理到目标服务器，用当前登录用户唯一 SSH key 初始化或刷新该服务器本地公共配置仓库。 |
| `POST` | `/public/update` | 先把远端公共分支合并到当前管理员在本服务器的稳定个人 worktree，再按分支 clone/fetch/checkout/pull 共享运行副本并广播其他服务器同步；任一工作树有已跟踪修改时必须显式确认恢复。 |
| `POST` | `/public/update-and-push` | 公共配置"提交并推送"复合操作：先 `fetch` 远端最新提交，再 stage/commit 本地变更，随后 merge `origin/{branch}` 并 push；`discardLocalChanges=true` 时先 `git reset --hard HEAD` 放弃受控仓库中的已跟踪修改。 |
| `POST` | `/file-ws-route` | 查询 Agent 配置文件 WebSocket 应连接的目标后端，body 包含 `scope`、`workspaceId?`、`worktreeId?`、`linuxServerId?`。 |
| `POST` | `/public/worktrees` | 在请求指定且已初始化的 `linuxServerId` 上确保当前用户的长期公共配置 worktree；分支和目录按用户稳定命名、不包含应用版本，同一用户重复调用返回已有有效 worktree。目标服务器本地 Git 根目录未初始化时返回 `CONFLICT`，不在该接口 clone。 |
| `GET` | `/public/worktrees?linuxServerId=` | 查询当前用户在指定服务器上的有效 `ACTIVE` 公共配置 worktree；不会返回其他用户的 worktree。 |
| `GET` | `/public/diff?worktreeId=` | 查询 Git 变更文件和 patch；后端复用公共 porcelain 解析与 diff 聚合，保留 Git 原始状态简写。 |
| `POST` | `/public/stage` / `/public/unstage` / `/public/discard` | 暂存、取消暂存或回退文件。discard 会恢复已跟踪文件并定点清理新增/未跟踪文件；冲突文件必须走合并接口。 |
| `GET` | `/public/git-conflicts?worktreeId=&linuxServerId=` | 查询当前用户公共 worktree 中未解决冲突文件路径，不返回 patch 内容。 |
| `GET` | `/public/git-conflict?path=&worktreeId=&linuxServerId=` | 读取当前用户公共 worktree 中冲突文件的 Git base/current/incoming stage 与工作树结果。 |
| `POST` | `/public/git-conflict/resolve` | 解决公共配置单个冲突并定点 stage，支持当前、远端、两者、手工内容和删除语义。 |
| `POST` | `/public/git-conflict/resolve-all` | 使用 Git index 原生 ours/theirs 批量解决公共配置全部冲突；只支持 `CURRENT/INCOMING`。 |
| `POST` | `/public/git-conflict/abort` | 取消公共配置未完成 merge。 |
| `POST` | `/public/commit` | 提交当前暂存区。 |
| `POST` | `/public/publish` | 在当前用户公共 worktree 中 fetch 并 merge `origin/{公共分支}`，远端 push 前建立持久化禁发任务，再以非强制 refspec 把个人分支推送到公共分支；push 与 rollout 激活确认后即返回发布成功，本机即时同步触发失败由持久化补偿任务继续，不能反转已经完成的发布结果。各服务器同步共享副本并登记本机 manager 进程，旧 Session 空闲后逐实例 dispose；某用户旧实例 dispose 后该用户立即恢复发送，个人 worktree 保持 `ACTIVE`。 |

工作空间级接口把同名能力挂在 `/workspaces/{workspaceId}/...`，其中 `diff/stage/unstage/discard/commit/publish/worktrees/status` 的语义与公共级一致；文件读写必须通过文件 WebSocket。物理目录为当前运行态 Workspace 或指定 worktree 下的 `.opencode/`，但普通工作空间文件树不重复展示根级 `.opencode`。工作空间级 `diff` 只返回 `.opencode/agents` 与 `.opencode/skills` 下的变更，响应 path 会去掉 `.opencode/` 前缀。工作空间新增配置包时只初始化应用技能包 `skills/<name>/SKILL.md`、`skills/<name>/rules/README.md` 和 `skills/<name>/templates/README.md`，不自动创建 Agent 入口。

公共 publish 在个人 worktree 合并远端公共分支发生冲突时返回 HTTP 409、错误码 `CONFLICT`，`details.conflictFiles` 携带冲突文件，并保留个人 worktree 的 Git 原生 merge 现场；前端在统一 Git 变更面板中选择保留本地、保留远程、手工合并或取消合并，解决后提交并再次推送。工作空间级旧 worktree publish 的冲突语义保持不变。

公共 `update`、`update-and-push`、`publish` 的同步广播携带内部 `rolloutId`。发布端在远端 push 或共享副本修改前先写 `PREPARING` 任务、发起人用户 ID 以及持久化服务器清单（包含发布瞬间离线的已登记服务器），远端提交确认后激活为 `DRAINING`，形成后端禁发硬闸门；一旦该任务建立，广播失败、服务器离线或 Java 重启都只会保留 `PENDING/DRAINING` 并由定时补偿继续处理，不允许以失败状态提前开闸。发布请求会尝试立即认领本服务器同步记录以降低延迟；该即时触发查询或同步失败时，数据库任务仍由默认每 5 秒运行的补偿程序持续认领，因此接口不会把已确认的远端提交误报为发布失败。每台服务器使用发起人的已存 SSH 凭据把本机共享运行仓库 checkout/reset 到目标 commit；只有取得本服务器 manager 的实时进程清单、把已有 opencode 进程及其用户快照写入目标表后，才确认该服务器同步完成。凭据只在目标 Java 从数据库读取并解密，不进入广播 payload。

所有服务器确认后，每台 Java 的固定延迟任务只认领 `target.linuxServerId=本机 linuxServerId` 的一条目标；租约 token 隔离过期 worker，发布端可以统一插表，但不能替其他服务器执行。目标 Java 先用本机 manager 快照确认端口仍存在，再经本机 opencode 逐一对该进程历史绑定的所有 Workspace 目录调用 `GET /session/status`；任一目录出现 `busy/retry`、未知状态或非法响应都跳过 dispose、累计 `retryCount` 并按退避持续重试。全部目录明确空闲后，对这个用户专属 opencode 进程只调用一次 `POST /global/dispose`；明确返回布尔 `true` 才把目标置为 `DISPOSED`。该用户的全部目标完成后立即恢复发送，下一次请求重新创建 Instance 并加载已同步的 `opencode.jsonc`、Agent 和 Skill；不等待其他用户。manager 已明确确认目标进程不存在时按已释放处理；manager 清单不可用时继续重试。全部目标结束后 rollout 原子变为 `COMPLETED`；同一时刻只允许一个 `DRAINING` 任务。

长操作进度：

| 方法/路径 | 用途 |
|---|---|
| `POST /operations/{operationId}/tickets` | 为 Agent 配置进度 WebSocket 签发一次性 ticket。 |
| `WS /operations/{operationId}/ws?ticket=agt_...` | 推送 `snapshot`、`step`、`completed`、`failed` 消息；`step` 消息可携带 `command` 表示当前正在执行的安全 Git 命令文本。 |
| `GET /operations/{operationId}` | 查询当前 operation 快照。 |

ticket 响应中的 `webSocketUrl` 是签发 ticket 的当前 Java 绝对地址，例如
`ws://122.233.30.114:8080/api/internal/platform/workspace-management/agent-config/operations/{operationId}/ws?ticket=...`。
一次性 ticket 仍只保存在签发 JVM；前端必须使用该绝对地址，不能改回入口 Nginx 相对路径。

`POST /public/update` 请求体：

```json
{
  "branch": "main",
  "operationId": "aco_1234567890abcdef",
  "discardLocalChanges": false
}
```

`discardLocalChanges` 可选且默认 `false`。公共仓库存在已跟踪文件修改（包括误删）时，默认返回 `CONFLICT`；只有超级管理员在页面明确勾选放弃本地修改并传 `true` 后，后端才执行 `git reset --hard HEAD` 再 fetch/checkout/pull。该操作不删除未跟踪文件。

`POST /public/repositories/{linuxServerId}/pull` 请求体同 `/public/update`，用于超级管理员在“系统管理 → 配置管理 → opencode 公共配置管理”中对指定服务器执行显式拉取。该接口按 `linuxServerId` 路由到目标 Java 后端，使用当前登录管理员唯一 SSH key；如果该管理员在目标服务器已有稳定 `public-{userId}` 个人 worktree，后端先在个人 worktree 中 `fetch` 并合并 `origin/{branch}`，成功后才更新服务器共享运行副本的 `fetch/checkout/pull --ff-only`，避免接口返回成功但文件树仍读取旧 worktree。内部部署公共 Git 地址同样按当前用户统一认证号拼接实际 SSH URL。`discardLocalChanges` 可选且默认 `false`；个人 worktree 或共享副本有未提交修改时默认返回 `CONFLICT`，传 `true` 时只恢复已跟踪本地修改、不删除未跟踪文件。个人 worktree 合并冲突会保留冲突文件并返回既有 `conflictFiles`，共享副本不会继续更新。响应为最新的 `PublicRepositoryStatusResponse`。该接口只负责拉取远端最新提交，不主动提交本地业务修改、不 push。`configDirPath` 必须由公共配置 Git 仓库初始化后产生且非空，后端不会在 manager 启动时创建空配置目录。

`POST /public/update-and-push` 请求体：

```json
{
  "branch": "main",
  "commitMessage": "chore: sync public agent docs",
  "operationId": "aco_1234567890abcdef",
  "discardLocalChanges": false
}
```

执行顺序为：校验仓库 origin、内部部署按当前管理员统一认证号刷新 origin、可选 `reset --hard HEAD`、`fetch`、`stage all`、按需 `commit`、`merge origin/{branch}`、`push`、广播公共配置同步。若共享公共仓库由另一位管理员初始化，刷新 origin 可保证当前私钥与 SSH 登录用户名属于同一用户。若本地没有新的可提交文件，仍会继续 merge 和 push，确保已有未推送本地提交不会被 UI 误报成功。若远端有新提交且合并冲突，响应为 HTTP 409 / `CONFLICT`，`details.conflictFiles` 返回冲突文件；前端应刷新公共 Git diff，把 `status=conflict/rawStatus` 的文件作为冲突处理，用户解决全部冲突后再次调用本接口会提交已解决的 merge index 并 push。

`commitMessage` 必填且不能为空字符串。完整流程为：
1. 当 `discardLocalChanges=true` 且工作区有已跟踪修改时执行 `git reset --hard HEAD`（不删除未跟踪文件）；
2. `git fetch origin` 拉取远端最新引用；
3. `git add --all` 把工作区全部变更加入暂存；
4. 若产生新变更则用 `commitMessage` 生成一次提交；
5. `git merge --no-ff origin/{branch}` 合并远端分支；如冲突则保留 merge 现场并返回 `CONFLICT`；
6. 执行 `git push` 到 `branch`，确保已有未推送本地提交也会推到远端；
7. 广播 `agent-config.public-sync-requested` 事件并返回最新 commit hash。

该接口要求 `SUPER_ADMIN`，所有 Git 操作使用当前登录用户唯一 SSH key；commit 和可能产生 commit 的 merge 同时使用当前登录用户的命令级 Git 作者/提交者身份，不写入公共仓库或服务器全局 Git 配置；平台用户没有邮箱字段时使用 `testagent.local` 保留域名生成 Git email。提交说明为空返回 `VALIDATION_ERROR`，push 被远端拒绝（如 non-fast-forward）时返回 `GIT_UNAVAILABLE`，错误 details 携带 `gitFailureType=REMOTE_REJECTED` 与可展示的 `gitFailureHint`，不修改仓库外的状态。

`GET /public/repositories` 响应元素字段：

响应按稳定 `linuxServerId` 唯一返回服务器行；同一服务器上 Java 进程重启导致 Redis TTL 窗口内同时存在多个 `backendProcessId` 快照时，后端会合并为一条公共配置仓库状态，前端也以 `linuxServerId` 作为去重 key。
公共配置目录存在且非空、origin 匹配，但 Git 工作树有未提交内容时返回 `initialized=true/status=CONFLICT`；这不表示磁盘目录缺失。`message` 使用 `Git 工作树存在未提交变更：<path1>、<path2>`，最多列出五个真实 Git 路径，超过时追加“等”。

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
  "worktreeName": "public-usr_admin",
  "branch": "public-usr_admin",
  "rootPath": "/data/.testagent/agent-opencode/.configdev/public-usr_admin",
  "agentDirectory": "/data/.testagent/agent-opencode/.configdev/public-usr_admin/opencode",
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

`POST /public/stage`、`POST /public/unstage` 与 `POST /public/discard` 请求体：

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

- 公共 Git 地址按当前部署模式解释同一个 `OPENCODE_PUBLIC_AGENT_GIT_URL`：外部部署直接使用完整 SSH/HTTPS Git URL，内部部署把该值视为 `host[:port]/path`，运行时按当前管理员统一认证号拼接 `ssh://{unifiedAuthId}@...`。参数默认 `UNCONFIGURED` 时，公共 Git 更新、分支、worktree、diff/commit/publish 返回禁用或校验错误，并提示超级管理员先到“系统管理 → 通用参数管理”配置对应参数；只读 status 仍可返回目录信息。内部部署比较已有公共配置仓库 origin 时忽略 `ssh://任意用户@` 前缀，避免不同管理员操作同一内部库时误判不一致。
- 公共 Git origin 和 `opencode/` 配置目录有效时，即使工作树存在未提交变更，仓库仍返回 `initialized=true` 和 `status=CONFLICT`，文件树保持可浏览；更新操作按 `discardLocalChanges` 规则决定拒绝或恢复。
- `OPENCODE_PUBLIC_CONFIG_GIT_ROOT` 缺失或为空目录时，只允许初始化/公共更新流程 clone；公共 worktree 创建只校验目标服务器已有 Git 仓库，未初始化返回 `CONFLICT`，提示 `服务器{linuxServerId}上公共配置仓库在{gitRootPath}目录中未初始化。`
- 新建公共/工作空间 Agent worktree 均返回并保存 `linuxServerId`。公共 worktree 后续文件目录列表、读取、写入按落库服务器归属通过文件 WebSocket route/ticket/RPC 执行；diff/stage/unstage/discard/commit/publish 仍按现有 HTTP 后端代理执行，并在目标后端再次校验创建人。
- 浏览器选择已初始化公共仓库服务器后，自动调用 `GET /public/worktrees` 并在缺失时调用 `POST /public/worktrees`，随后始终使用当前用户 worktree；“更多操作”同时提供显式创建和切换入口。创建仍只确保当前用户的 `public-{userId}` 稳定分支/worktree，不允许任意命名，也不提供切换他人 worktree 或回退共享直接目录编辑的入口。
- 历史带日期或手工名称的公共 `ACTIVE` worktree 不再作为当前用户默认 worktree 返回；首次进入时创建并挂载 `public-{userId}` 稳定分支，旧记录和目录保留，避免自动删除尚未提交的历史改动。
- 历史 `agent_config_worktrees.linux_server_id is null` 记录按当前服务器兼容执行；如果本地目录不存在，管理员应重新创建 worktree。
- 公共 Git clone/fetch/pull/worktree 失败时仍返回统一 Git 错误码，但会在 `details.gitFailureHint` 中给出安全排查建议；浏览器可展示该提示和 `traceId`，不得展示原始 `stderr`、完整命令或内部路径。
- 公共 Git 远端分支和远端目录只读查询允许最长 60 秒；后端日志会记录 `event=git_command_start|git_command_success|git_command_slow|git_command_failed|git_command_timeout|git_command_unavailable` 以及公共配置入口的 `event=agent_config_public_branches_*` / `event=agent_config_public_repository_initialize_*`，用于按 traceId 追踪具体 Git 阶段、目标服务器、脱敏后的 Git URL 和耗时；失败类日志额外包含 `failureType` 和安全排查建议。
- Agent 配置进度不走 RunEvent SSE，也不写入 `run_events`；浏览器只通过 ticket WebSocket 或 `GET /operations/{operationId}` 查看。多实例部署开启服务器广播后，执行实例会通过 `agent-config.operation-progress` 广播安全进度字段，让连接到非执行实例的页面也能收到进度；未启用广播时仍可通过 `GET /operations/{operationId}` 查看最终状态。Git 命令进度复用执行器记录点，在实际命令启动前发送，不通过轮询或额外 Git 查询获取。
- 内部服务器广播事件 `agent-config.public-sync-requested` 的 payload 只包含 `branch`、`commitHash`、`reason`、`rolloutId`，发起人用户 ID 和 SSH 凭据不进入 payload；目标 Java 按 `rolloutId + 本机 linuxServerId` 读取持久化同步任务，再用任务中发起人用户 ID 查询数据库内已存 SSH key。`agent-config.operation-progress` 只包含 operation/status/step/command/error/commit 等安全进度字段；envelope 继续携带 `traceId`，不携带文件内容、私钥、token、Authorization 或 Cookie。

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

Base URL：`/api/internal/platform/configuration-management`。除设置页保存应用工作空间时会委托 workspace-management 创建初始版本工作区并执行 Git clone/checkout 外，本能力只产生和维护配置数据；版本库分支、目录和远端树加载均使用远端只读 Git 命令，不 clone、不落本地磁盘、不启动 Session/Run、不产生 RunEvent。

鉴权：

- 应用定义列表、个人 SSH key 接口和“把当前认证用户自己加入应用”只要求已登录用户；SSH key 只能管理自己的 key。
- 新建应用属于平台级主数据操作，只允许 `SUPER_ADMIN`。
- 应用成员查询/删除、给其他用户加成员、代码库与工作空间配置接口需要已登录用户具备全局角色 `APP_ADMIN`；`SUPER_ADMIN` 继承该权限。角色来自 `user_roles + dictionaries(ROLE)`，不在 `user_roles` 增加 `application_id`。

### 应用与人员

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/applications?enabled=true` | 查询已启用应用定义。 |
| `POST` | `/applications` | 超级管理员新建并启用应用；body 为 `{ appId, appName }`，`appId` 全局唯一且最长 128 字符，`appName` 最长 255 字符。 |
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
| `GET` | `/applications/{appId}/repositories/{repoId}/tree?branch=main` | 使用 `git archive --remote` 解析指定分支目录/文件树；测试工作库只返回当前应用同名根目录及其子树。 |

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
- `INTERNAL` 模式下 `englishName` 为空时，后端按 Git 路径派生默认值：去掉可选 `.git`，把 `/` 替换为 `-` 并转小写，例如 `scm-share.sdc.cs.enterprise:29418/hzefficiencytools/interfaceplatform` 派生为 `hzefficiencytools-interfaceplatform`。
- `englishName` 为必填英文名称，仅允许字母、数字和连字符，长度 1 到 128，且不能以连字符开头或结尾；后端统一按小写保存，非空值唯一。历史数据可能为 `null`，但缺少英文名称的历史代码库不能再创建新的应用版本工作区。
- `repositoryType` 由通用字典 `REPOSITORY_TYPE` 管理；编辑区只展示类型，不提供修改入口。`standard` 字段保留给存量工作空间逻辑，语义统一由版本库类型派生。
- HTTPS URL 不支持内嵌账号或 token；本期不做连通性校验。
- Git 目录和远端树读取不直接写业务配置、不 clone 到本地磁盘；外部 SSH URL 和内部版本库会立即使用当前登录用户保存的唯一 SSH key。当前用户未配置 key 或远端不支持 `git archive --remote` 时返回统一 Git 错误。统一认证号不按敏感信息脱敏，SSH 私钥、token、Cookie、Authorization 仍不得出现在错误详情中。

`GET /applications/{appId}/repositories/{repoId}/tree?branch=feature_testagent_20260707` 响应：

```json
{
  "nodes": [
    {
      "name": "F-COSS",
      "path": "F-COSS",
      "type": "directory",
      "children": [
        {
          "name": "W1",
          "path": "F-COSS/W1",
          "type": "directory",
          "children": [
            {
              "name": "case.md",
              "path": "F-COSS/W1/case.md",
              "type": "file",
              "children": []
            }
          ]
        }
      ]
    }
  ]
}
```

树节点 `type` 仅为 `directory` 或 `file`，文件节点 `children` 为空列表。后端会校验应用存在、代码库已关联到该应用、当前用户有可用 SSH key；测试工作库按 `applications.app_name` 过滤，只返回与当前应用同名的根目录及其全部子目录/文件。旧 `GET /repositories/{repoId}/directories?branch=` 保留兼容，只返回目录路径列表。

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
  "directoryPath": "F-COSS/W1",
  "workspaceName": "ai-test",
  "directoryNew": true,
  "operationId": "wco_1234567890abcdef",
  "version": "20260707"
}
```

规则：

- `operationId` 可选；前端传入时用于进度轮询，格式为 `wco_` 前缀加 8 到 128 位字母、数字、下划线或短横线。
- `workspaceName` 为工作空间别名，前端默认传 `ai-test`；后端按去首尾空白后的精确字符串校验同一应用下不可重复。旧客户端不传时仍按 `directoryPath` 末段兜底。
- 测试工作库必须选择形如 `feature_testagent_yyyyMMdd` 的分支，后端从分支名提取版本号；非测试工作库所有分支均可提交。
- 测试工作库的 `directoryPath` 必须是当前应用同名根目录的一级子目录，例如 `F-COSS/W1` 可选，`F-COSS/W1/F1` 只能浏览不能作为工作空间；非测试工作库不套用该限制。
- 非标准代码库必须传入 `version`，格式为 `yyyyMMdd`；标准代码库传入的 `version` 会被分支解析结果覆盖。
- 只有保存接口会触发 Git clone/fetch、分支 checkout 和本地目录准备；页面上的分支、远端树和新增目录操作均不落磁盘。
- `directoryNew=true` 表示前端在远端树内存中新增了测试工作库应用根目录下的一级子目录。后端在 clone/checkout 后如果目标目录不存在，则在保存阶段创建该目录；不会向 Git 提交空目录。旧客户端不传该字段时行为不变。
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
  "directoryPath": "F-COSS/W1",
  "workspaceName": "ai-test",
  "createdAt": "2026-06-26T00:00:00Z",
  "updatedAt": "2026-06-26T00:00:00Z",
  "initialVersion": {
    "versionId": "awv_...",
    "version": "20260707",
    "branch": "feature_testagent_20260707",
    "repoRootPath": "/data/.testagent/agent-opencode/workspace/appworkspace/20260707/demo",
    "workspaceRootPath": "/data/.testagent/agent-opencode/workspace/appworkspace/20260707/demo/F-COSS/W1",
    "runtimeWorkspace": {
      "workspaceId": "wrk_...",
      "rootPath": "/data/.testagent/agent-opencode/workspace/appworkspace/20260707/demo/F-COSS/W1",
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

公共 Agent Git 地址只保留 `OPENCODE_PUBLIC_AGENT_GIT_URL` 一个参数。前端通用参数页复用 `GET /api/internal/platform/configuration-management/repository-deployment-options` 展示当前默认部署模式，修改弹窗允许选择外部/内部模式；内部模式输入框展示当前管理员 `ssh://{unifiedAuthId}@` 前缀，但实际只保存 `host[:port]/path`。Java 后端按该参数保存值形态判断：完整 SSH/HTTPS Git URL 直接使用，`host[:port]/path` 片段在公共 Agent Git 操作时按当前管理员统一认证号拼接 `ssh://{unifiedAuthId}@...`，不通过新增 `OPENCODE_PUBLIC_AGENT_GIT_URL_INTERNAL` 参数选择。

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

通用参数表中的 `OPENCODE_MANAGER_MAX_PROCESSES`（`platform=all`，全局单值）是 opencode manager 的最大并发进程数权威来源；`OPENCODE_SESSION_DIR` 和 `OPENCODE_PUBLIC_CONFIG_DIR` 是 manager 启动用户 opencode server 时的 session/config 路径权威来源。manager 通过 WebSocket 控制面连入本服务器后端时，后端在注册成功后只返回 `registered`；manager 收到后主动发送 `configRequest` 拉取完整配置。连接异常断开后，manager 按重连间隔无限重连，重连成功后重新发送 `configRequest`。前端仅允许修改 `editable=true` 的通用参数（`OPENCODE_MANAGER_MAX_PROCESSES`、`OPENCODE_PUBLIC_AGENT_GIT_URL`）；其余为只读部署/初始化参数，修改将影响系统正常运行，接口对只读参数的更新返回 `VALIDATION_ERROR`。对公共 Git 地址参数的修改同样发布 `common-parameter.refresh-requested` 跨实例广播以保证 DB 一致，但不触发 manager 热刷新（URL 属部署参数，由 AgentConfig 在下次公共配置操作时按当前部署模式直读 DB 生效）。最大进程数 `PATCH` 成功后发布进程内 `CommonParameterReloadedEvent`，后端经控制面 WebSocket 把只包含 `maxProcesses` 的 `configUpdate` 广播给当前 Java 实例持有的本服务器 manager 连接；多实例部署下，广播器同时发布 `common-parameter.refresh-requested` 跨实例广播，每台实例收到事件后直接从数据库读取最新最大进程数并只向本服务器 manager 下发。响应中的 `editable` 字段标识是否允许前端修改：`true` 可改，`false` 只读。

manager 收到后按自身端口池容量 `PortEnd-PortStart+1` 做 clamp（超上限 clamp 到容量、`<1` 拒绝）并热更新，成功应用 `configUpdate` 后立即补发一次 `managerHeartbeat` 上报生效值，后续周期 heartbeat 继续同步，`opencode_containers.max_processes` 随之更新。首次或重连后完整 `configUpdate` 中，`OPENCODE_SESSION_DIR` 会拼接端口作为 `XDG_DATA_HOME={sessionRoot}/{port}`、`OPENCODE_PUBLIC_CONFIG_DIR` 会作为 `OPENCODE_CONFIG_DIR`；两者均使用通用参数 `resolvedValue`，不会把字面 `$HOME` 下发给 manager。OpenCode 会合并用户全局配置和 `OPENCODE_CONFIG_DIR` 自定义目录，企业部署必须保证运行用户 `~/.config/opencode/config.json`、`opencode.json`、`opencode.jsonc` 不维护模型或供应商，模型和供应商只维护在公共配置 Git 库 `{OPENCODE_PUBLIC_CONFIG_GIT_ROOT}/opencode/opencode.jsonc` 中。任一必需参数缺失、空白或最大进程数非正整数时，后端不会下发可启动配置，manager 保持未 ready 并拒绝 `start`/`restart`，不会回退 `OPENCODE_SESSION_ROOT`、`OPENCODE_CONFIG_DIR` 或 `OPENCODE_MANAGER_MAX_PROCESSES` 环境变量。该下发通道不产生 RunEvent/SSE，不向前端推送。

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
| `GET` | `/api/internal/platform/workspace-management/workspaces` | 分页列出工作区。 |
| `GET` | `/api/internal/platform/workspace-management/workspaces/{workspaceId}` | 查询工作区详情。 |
| `POST` | `/api/internal/platform/workspace-management/workspaces/{workspaceId}/file-ws-route` | 查询当前工作区文件 WebSocket 应连接的目标后端。 |
| `GET` | `/api/internal/platform/workspace-management/backend-servers` | 查询可用于服务器工作空间选择器的后端服务器。 |
| `POST` | `/api/internal/platform/workspace-management/file-ws/tickets` | 在目标后端创建文件 WebSocket 一次性 ticket。 |

旧 `/api/workspaces/**`、旧 HTTP 文件接口以及内部平台 HTTP 文件 `workspaces/{workspaceId}/files*` 已作废，返回 `410 API_GONE`。工作区文件列表、读取、写入、上传、复制、移动、状态和删除必须走 `file-ws-route`、目标后端 ticket 和文件 WebSocket RPC。

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

`POST /api/internal/platform/workspace-management/workspaces/{workspaceId}/file-ws-route` 使用当前登录用户的 `opencode` 进程服务器归属定位同服务器后端，返回浏览器应直连的目标后端地址。该路由查询只读取 ACTIVE binding 和可恢复进程记录，不下发 opencode-manager `health` 或 `start` 命令；工作区服务器归属、用户 opencode 进程服务器和目标后端服务器不一致时返回统一 `CONFLICT`。本地服务器身份变化或切换测试库后，若历史 workspace 仍绑定旧 `linuxServerId`，且旧服务器没有在线后端快照、当前 opencode 进程在本后端、workspace 根目录在本机可访问，后端会在路由时把 workspace 回绑到当前服务器；多机环境中旧服务器仍在线或目录不可访问时不会自动迁移。用户进程初始化、`/processes/me` 状态查询和未携带有效会话运行上下文的兼容 Run 启动仍按用户进程 API 执行强健康检查。

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

`POST /api/internal/platform/workspace-management/file-ws/tickets` 在目标后端创建短期一次性 ticket，供浏览器建立文件 WebSocket。该接口必须使用用户登录态；`mode=workspace` 要求当前用户 opencode 进程服务器归属、workspace 和目标后端同服务器。签发优先使用轻量归属快照；当快照未 READY 时会复查当前用户 opencode 强状态，避免文件树与进程状态卡可用性不一致，但不会触发 `start` 命令；`mode=directory-picker` 允许 `SUPER_ADMIN` 浏览目标服务器目录，普通用户只能浏览与当前 opencode 进程同服务器的目录；`mode=agent-config` 绑定 Agent 配置 scope/workspace/worktree，读取允许登录用户，公共 Git 写入仅 `SUPER_ADMIN`，应用级配置写入由 WebSocket handler 校验 `APP_ADMIN`（`SUPER_ADMIN` 继承）。普通用户写应用版本副本会返回只读错误；个人 worktree 普通文件仍可写，并可通过 `workspace.delete` 删除普通文件或递归删除目录树；删除不跟随符号链接，工作区根目录和任意层级 `.git` 元数据禁止删除。`.opencode` 根及其 `agents/**`、`skills/**`（含 rules/templates）仅 APP_ADMIN 可写。

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

WebSocket 消息协议见 `docs/api/event-stream.md` 的“Workspace File WebSocket”段。旧 HTTP 文件接口已作废，历史调用方和调试脚本也必须迁移到 route + ticket + 目标后端 WebSocket。

服务器目录选择器只通过短期 ticket 建立的文件 WebSocket 使用；缺失、不可访问或非目录返回 `VALIDATION_ERROR`。创建服务器工作空间仍要求 `SUPER_ADMIN`，且目标服务器必须与当前 agent 服务器一致。

文件 WebSocket RPC 的 `path` / `sourcePath` / `targetPath` 必须解析在 workspace root 内，越权路径返回 `FORBIDDEN`。目录列表为单层、不递归，默认最多 1000 项；`workspace.search` 按工作区相对路径递归匹配，空 query 可返回受深度、数量和超时保护的文件目录，默认最多 200 项、20 层、5 秒，并跳过 `.git`、`node_modules` 等黑名单目录。文件读取和文本写入只支持 UTF-8，Base64 二进制上传允许新建任意普通文件；读取、写入和上传默认单文件上限 1MB，可通过 `test-agent.files.*` 配置，WebSocket 单帧上限会按该值的 Base64 膨胀量加 RPC envelope 余量同步设置。`workspace.upload`、`workspace.copy`、`workspace.move` 不覆盖已有目标，其中复制和移动只处理普通文件；源路径与目标路径分别执行写权限及 `.opencode/agents/**`、`.opencode/skills/**` 保护校验。

### 应用版本工作区 API

Base URL：`/api/internal/platform/workspace-management`。该能力把配置管理中的应用工作空间模板落为托管 Git 目录，并同步创建运行态 `workspaces` 记录。新建或显式修复的托管路径在数据库中保存逻辑值：应用版本/副本使用 `appworkspace:<versionSegment>/<repositoryEnglishName>[/<templateDirectory>]`，个人 worktree 使用 `personalworktree:<versionSegment>/<userId>/<repositoryEnglishName>/<branch>[/<templateDirectory>]`；使用时分别基于通用参数 `OPENCODE_APP_WORKSPACE_ROOT`、`OPENCODE_PERSONAL_WORKTREE_ROOT` 解析为当前服务器物理路径。响应里的 `repoRootPath`、`workspaceRootPath`、`runtimeWorkspace.rootPath` 对托管工作区均为解析后的当前服务器物理路径；历史 Unix/Windows 绝对路径只兼容读取，不批量迁移。旧的手动目录注册 `/api/workspaces` 已作废，返回 `410 API_GONE`。

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
| `POST` | `/personal-workspaces/{personalWorkspaceId}/sync-to-application` | 兼容同步入口；只读取个人 `HEAD` 的白名单文件，复用 feature 投影发布，不把未提交工作树内容复制到应用分支。 |
| `POST` | `/personal-workspaces/{personalWorkspaceId}/sync-from-application` | 将所选应用版本工作区文件同步到个人工作区。 |
| `POST` | `/workspace-versions/{versionId}/ensure-default-personal-workspace` | 显式确保默认个人工作区存在：查询 (versionId, userId, workspaceName=default)，存在则复用返回，不存在则后台创建。 |
| `GET` | `/workspaces/{workspaceId}/git-diff` | 基于本地 Git（不依赖 opencode）获取应用版本工作区或个人 worktree 的变更文件列表，返回 `{ files: [{ path, rawStatus, status, staged, patch, additions, deletions }] }`；Git unmerged 状态会返回 `status=conflict`。 |
| `POST` | `/workspaces/{workspaceId}/git-discard` | 丢弃当前应用版本工作区或个人 worktree 中指定工作区相对路径的本地 Git 改动；已跟踪文件执行 restore，新增/未跟踪文件定点 clean；`.opencode/**` 要求 `APP_ADMIN`。 |
| `POST` | `/workspaces/{workspaceId}/git-stage` | 把当前应用版本工作区或个人 worktree 中指定的非冲突文件定点加入真实 Git index；`.opencode/**` 要求 `APP_ADMIN`。 |
| `POST` | `/workspaces/{workspaceId}/git-unstage` | 把当前应用版本工作区或个人 worktree 中指定的非冲突文件从真实 Git index 撤回工作树；`.opencode/**` 要求 `APP_ADMIN`。 |
| `GET` | `/workspaces/{workspaceId}/git-conflict?path={path}` | 读取个人 worktree 冲突文件的 Git base/current/incoming stage 与工作树结果。 |
| `POST` | `/workspaces/{workspaceId}/git-conflict/resolve` | 解决单个冲突并定点 stage，支持当前、应用、两者、手工内容和删除语义；`.opencode/**` 要求 `APP_ADMIN`。 |
| `POST` | `/workspaces/{workspaceId}/git-conflict/resolve-all` | 使用 Git index 原生 ours/theirs 批量解决全部冲突；只支持 `CURRENT/INCOMING`；当前冲突包含 `.opencode/**` 时要求 `APP_ADMIN`。 |
| `POST` | `/workspaces/{workspaceId}/git-conflict/abort` | 在个人 worktree 中执行 `merge --abort`，取消整次未完成合并；当前冲突包含 `.opencode/**` 时要求 `APP_ADMIN`。 |
| `POST` | `/personal-workspaces/{personalWorkspaceId}/publish-preview` | 发布前预检应用分支 HEAD、待合入提交数、A/M/D/R 汇总和样例路径；不修改个人 worktree。若个人 worktree 已处于未完成 merge，只返回已记录的应用 HEAD，不重复拉取远程。 |
| `POST` | `/personal-workspaces/{personalWorkspaceId}/commit` | 仅在个人 worktree stage 并提交 `files` 白名单；不推送、不广播。请求包含 `.opencode/**` 时要求 `APP_ADMIN`（`SUPER_ADMIN` 继承）。 |
| `POST` | `/personal-workspaces/{personalWorkspaceId}/publish` | 要求 `files` 已在个人 worktree 本地提交，再从个人 `HEAD` 按白名单投影到应用 feature worktree，提交并推送；不 merge 整个个人分支。请求包含 `.opencode/**` 时要求 `APP_ADMIN`，响应包含 `currentStep/executedCommands`。 |

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
- 磁盘目录已存在时，空目录会删除后重新 clone；目录仅包含 `.git` 且无有效 HEAD 时视为上次 Git clone 超时/中断留下的残留，会删除后重新 clone。已有有效 Git 仓库会校验 origin URL 和当前分支，匹配则接管，不匹配返回 `CONFLICT`；非 Git 非空目录返回 `CONFLICT`，不覆盖用户内容。
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

`sync-to-application` 保留 `force` 字段用于兼容审计，但不再绕过权限和提交约束：后端要求所选文件在个人 worktree 已提交，读取个人 `HEAD`，再按白名单投影到应用 feature worktree，提交并推送。`spec/**` 是个人本地资产，任何角色都不能发布；请求只要包含规范化后位于 `spec/**` 的路径（包括 `./spec/**` 等别名）即返回 `FORBIDDEN`，`force` 不能绕过。其它未选文件也不会进入 feature 分支。成功后更新应用版本 `targetCommitHash` 与当前服务器副本 `replicaCommitHash`，并广播 `workspace.version.sync-requested`；其它在线用户只收到手动刷新/同步提示，不自动覆盖脏工作树。应用版本工作区与个人工作区同步不新增 RunEvent/SSE 事件。

### 默认个人工作区显式创建/修复

`POST /workspace-versions/{versionId}/ensure-default-personal-workspace` 无请求体。后端逻辑：

1. 先查询 `(versionId, userId, workspaceName=default)` 是否已有个人工作区记录。
2. 存在：先校验记录里的运行态目录、仓库根和分支是否仍是可用 Git worktree；校验通过才复用并返回 `DefaultPersonalWorkspaceResponse`（含 `personalWorkspaceId`、`personalWorkspaceName`、`personalWorkspaceBranch`、`runtimeWorkspace`）。
3. 不存在或已有记录的物理 worktree 缺失/不可复用：先按 `ENSURE_LOCAL` 确保当前服务器有 READY 应用版本副本，再后台创建或修复个人工作区（`git worktree add -b {branch}_{userId}_default`）。如果当前服务器没有副本，后端会基于 `OPENCODE_APP_WORKSPACE_ROOT` 创建本机副本；禁止再用旧 `application_workspace_versions` 绝对路径伪造成 READY replica。如果同名个人分支已存在，后端会尝试复用该分支挂载 worktree；如果目标目录已存在且是同一分支的 Git worktree，则接管并补运行态记录；如果同名分支仍登记在旧路径且目标规范路径不存在，后端会先 `git worktree move` 重挂载到规范路径。已有 default 记录但规范物理目录被删除时，显式 ensure 会重新创建该 worktree 并刷新运行态记录。只有目标目录被其他内容占用时返回 `CONFLICT`。

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

`GET /workspaces/{workspaceId}/git-diff` 无请求参数。后端通过 runtime workspace 反查 personal workspace、应用版本工作区副本或应用版本工作区记录，使用对应 `repoRootPath` 执行 `git status --porcelain` + `git diff`，应用模板子目录通过 pathspec 限定扫描范围，并复用公共解析逻辑处理路径反转义、rename 新路径、staged/unstaged patch 合并和 additions/deletions 统计。响应中的 `path` 始终是当前运行态工作区相对路径，例如仓库内 `F-GCMS/workspace/docs/app.md` 返回为 `docs/app.md`。`rawStatus` 是 Git porcelain 两字符状态码，`DD/AU/UD/UA/DU/AA/UU` 统一映射为 `status=conflict`，供前端把冲突文件从普通 staged/unstaged 文件中拆出展示。不依赖 opencode `/vcs/diff`，opencode 服务异常不影响变更列表刷新。

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

`files` 使用 `git-diff` 响应中的工作区相对路径。后端允许当前用户可访问的应用版本工作区或当前用户个人 worktree 的运行态 workspace 调用；会先把路径映射到仓库相对路径，已跟踪修改/删除执行 `git restore --staged --worktree -- <file>`，新增或未跟踪文件先取消暂存再执行定点 `git clean -f -- <file>`。成功后无业务响应体，前端刷新 `git-diff` 后该文件不再出现在变更列表中。

`POST /workspaces/{workspaceId}/git-stage` 与 `POST /workspaces/{workspaceId}/git-unstage` 使用相同请求体：

```json
{
  "files": ["src/App.java"]
}
```

后端复用托管工作区归属和路径校验，把工作区相对路径映射为仓库相对路径后分别执行定点 `git add -- <files>` 和 `git restore --staged -- <files>`。merge 冲突期间仍允许操作普通非冲突文件；unmerged 路径必须通过 `git-conflict/resolve` 处理，传入普通 stage/unstage API 会返回 `CONFLICT`。只要仍存在 unmerged 文件，Git 原生规则仍禁止 commit/push。

`GET /workspaces/{workspaceId}/git-conflict?path=src/App.java` 返回 `path/rawStatus/baseContent/currentContent/incomingContent/resultContent`。四个 content 可为 `null`，表示对应 Git stage 或工作树中不存在文件；文本上限 1MB。`POST /workspaces/{workspaceId}/git-conflict/resolve` 请求体为：

```json
{"path":"src/App.java","resolution":"MANUAL","content":"编辑后的最终内容"}
```

`resolution` 支持 `CURRENT`、`INCOMING`、`BOTH`、`MANUAL`、`DELETE`。非冲突路径返回 `CONFLICT`。批量接口请求体为 `{"resolution":"CURRENT"}` 或 `{"resolution":"INCOMING"}`，分别将全部冲突采用个人侧 stage 2 或远程侧 stage 3；目标侧不存在的文件按删除处理，不要求逐个解决。`POST /workspaces/{workspaceId}/git-conflict/abort` 无请求体，仅在存在未完成 merge 时成功。

### 个人工作区本地提交与 feature 发布

先调用 `POST /personal-workspaces/{personalWorkspaceId}/commit` 完成个人 worktree 本地提交；需要发布时再调用 `POST /personal-workspaces/{personalWorkspaceId}/publish`。两个请求体均可使用：

```json
{
  "commitMessage": "feat: 新增测试案例",
  "files": ["src/App.java", "README.md"],
  "expectedApplicationHead": "6093725...",
  "operationId": "aco_1234567890abcdef"
}
```

发布前可调用 `publish-preview` 查看当前应用 HEAD 和个人 HEAD，但预览不执行 merge。`operationId` 可选，前端可连接 `/agent-config/operations/{operationId}/ws` 展示 `PREPARE_REMOTE/PROJECT_HEAD/COMMIT_FEATURE/PUSH_REMOTE/COMPLETED` 阶段及安全 Git 命令。发布不会提交个人未提交内容，也不会把个人分支整体合入 feature。

后端执行流程：

1. API 入口先按规范化相对路径检查目录权限；`.opencode/**` 仅 `APP_ADMIN` 或 `SUPER_ADMIN` 可提交/发布，避免绕过 Agent 配置接口。随后 `commit` 在个人 worktree 隔离 index，只 stage `files` 白名单并提交；不推送、不广播。
2. `publish` 校验个人 worktree 未处于 merge 状态，且 `files` 在个人 worktree 没有未提交变更；未先本地提交时返回 `CONFLICT`。
3. 确保当前服务器的应用 feature worktree clean，`git fetch` + `git pull --ff-only {appVersionBranch}`，并校验可选 `expectedApplicationHead`。
4. 读取个人仓库 `HEAD`，将 `files` 映射为 feature worktree 的仓库相对路径；存在的文件执行 checkout 投影，不存在的文件执行定点删除。
5. 在 feature worktree 提交投影结果并 `git push origin {appVersionBranch}`；个人分支不 push。应用 Agent 使用同一流程，只把选中的 `.opencode/**` 从个人 `HEAD` 投影到 feature。服务端在准备 feature worktree 前对所有角色强制拒绝 `spec/**`，`SUPER_ADMIN` 也不能绕过目录规则，其它未选文件仍不会泄漏。

发布结果：

- **成功（LOCAL_COMMITTED）**：只更新个人分支 HEAD，不推送、不广播。
- **成功（PUBLISHED）**：更新应用版本 `targetCommitHash` 和当前服务器 replica commit，广播其他服务器同步。
- **冲突（CONFLICT）**：个人存在 merge 状态、选中文件未提交、应用 feature worktree 脏或 HEAD 并发保护失败时返回冲突，不推送 feature 分支；不会在个人 worktree 中创建发布 merge。

响应 `PersonalWorkspacePublishResponse`：

```json
// 成功
{
  "status": "PUBLISHED",
  "personalWorkspaceId": "psw_...",
  "versionId": "awv_...",
  "conflictFiles": [],
  "message": "已从个人 HEAD 投影并推送 feature 分支: abc123...",
  "remotePushed": true,
  "headCommit": "abc123...",
  "executedCommands": ["git fetch", "git checkout", "git commit", "git push"],
  "currentStep": "COMPLETED"
}

// 冲突
{
  "status": "CONFLICT",
  "personalWorkspaceId": "psw_...",
  "versionId": "awv_...",
  "conflictFiles": ["src/App.java", "src/Config.java"],
  "message": "发布文件存在未提交变更，请先提交个人 worktree",
  "executedCommands": ["git status --porcelain"],
  "currentStep": "PROJECT_HEAD"
}
```

`currentStep` 取值为 `PREPARE_REMOTE`、`PROJECT_HEAD`、`COMMIT_FEATURE`、`PUSH_REMOTE`、`COMPLETED`。本地提交响应的 `status=LOCAL_COMMITTED`、`remotePushed=false`；发布响应只有 feature 分支 `git push` 成功并读取发布后的 HEAD 后才返回 `status=PUBLISHED`、`remotePushed=true`。前端未收到 `remotePushed=true` 时不得展示推送成功。发布接口抛出统一错误时，`details.failedStep` 和 `details.executedCommands` 会尽量返回失败前已进入的 Git 阶段和已执行命令。

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
| `POST` | `/api/internal/platform/opencode-runtime/sessions` | 创建会话。 |
| `GET` | `/api/internal/platform/opencode-runtime/sessions?q=&page=&size=` | 当前登录用户历史会话分页；`q` 为空时返回该用户全部 ACTIVE 会话，默认前端每页 30 条。 |
| `GET` | `/api/internal/platform/opencode-runtime/workspaces/{workspaceId}/sessions` | 按工作区分页查询会话。 |
| `GET` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}` | 查询会话详情。 |
| `PATCH` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}` | 更新会话标题或置顶状态。 |
| `DELETE` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}` | 软删除会话，状态变为 `ARCHIVED`。 |
| `POST` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/messages` | 追加会话消息。 |
| `GET` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/messages` | 分页读取会话消息；主历史恢复优先使用 session tree。 |
| `GET` | `/api/internal/agent/{agentId}/sessions/{sessionId}/session-tree/messages` | 查询 root session 下全量历史 session tree message snapshot，工作台历史恢复主入口。 |
| `GET` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/session-tree/messages` | 同上，内部平台入口。 |
| `GET` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/active-run` | 查询会话最近的非终态 Run；用户已有 Redis 运行态 marker 时只读 Session active 索引，legacy 用户兼容查询数据库；没有时 `data=null`。 |

旧 `/api/sessions/**` 和 `/api/workspaces/{workspaceId}/sessions` 已作废，返回 `410 API_GONE`。

`POST /api/internal/platform/opencode-runtime/sessions` 请求体：

```json
{
  "workspaceId": "wrk_...",
  "title": "new session"
}
```

`PATCH /api/internal/platform/opencode-runtime/sessions/{sessionId}` 请求体：

```json
{
  "title": "renamed session",
  "pinned": true
}
```

`SessionResponse`：`sessionId`、`workspaceId`、`title`、`status`、`pinned`、`createdAt`、`updatedAt`、`workspaceContext`。

`workspaceContext` 仅在用户历史列表中尽量补齐，详情/更新/删除等单会话接口可为 `null`：

```json
{
  "appId": "app_...",
  "appName": "智能测试平台",
  "applicationWorkspaceId": "aw_...",
  "workspaceName": "主干工作区",
  "versionId": "ver_...",
  "version": "20260708"
}
```

兼容要求：

- `GET /api/internal/platform/opencode-runtime/sessions` 只返回当前登录用户的历史会话。用户归因按 `sessions.created_by_user_id` 优先，并用 `runs.triggered_by_user_id`、`session_messages.sender_user_id` 兜底兼容旧会话；完全没有用户归因的旧会话不返回，避免泄露其他用户历史。
- 列表严格按 `updatedAt desc, id desc` 排序，`pinned` 字段保留在响应中但不再影响历史排序。
- 列表查询不校验当前用户是否仍属于历史会话所属应用，确保用户被移出应用后仍能看到自己的历史；前端点击历史会话时再调用 `/workspace-management/workspaces/{workspaceId}/recent` 校验切换权限，失败后只能只读查看该会话。
- `workspaceContext.workspaceName` 对托管工作区展示应用工作空间模板名；非托管工作区回退运行态 `workspaces.name`。应用、版本或模板缺失时对应字段可为 `null`。
- `DELETE /api/internal/platform/opencode-runtime/sessions/{sessionId}` 为软删除，不删除消息、Run、事件或远端 opencode 映射；普通详情、列表和消息追加会把 `ARCHIVED` 会话视为不存在。

`POST /api/internal/platform/opencode-runtime/sessions/{sessionId}/messages` 请求体：

```json
{
  "role": "USER",
  "content": "message text"
}
```

`GET /api/internal/platform/opencode-runtime/sessions/{sessionId}/messages` 是分页接口，查询参数为 `page`、`size` 和可选 `refresh`。`refresh` 默认 `true`，会在存在 agent binding 时从 bounded-elastic 线程分页读取当前 agent 标准 session messages 并 upsert 到 `session_messages`；这是 Session 级全量同步，只保留已存在消息的 `runId`，新发现的远端历史 assistant 写空 `runId`，不得把整个 Session 重新归给最新 Run。`refresh=false` 只读数据库快照，用于前端反馈 messageId 映射、只读 transcript 或过渡只读场景，避免触发远端快照刷新。如果 opencode 进程不可用、超时或远端 session 不存在，接口回退返回数据库快照，不向前端暴露 generated SDK DTO。assistant 的 `content` 只保存可见 text part，不混入 reasoning 或 tool output；仅包含工具/文件 parts 的 assistant 消息允许 `content=""`，结构化内容仍由 `parts` 返回。

`GET /api/internal/agent/{agentId}/sessions/{sessionId}/session-tree/messages` 是前端工作台历史恢复的主接口，返回 Session root 下全量历史消息树快照；内部平台入口 `/api/internal/platform/opencode-runtime/sessions/{sessionId}/session-tree/messages` 保留，旧 `/api/sessions/{sessionId}/session-tree/messages` 返回 `410 API_GONE`。完整历史按 Redis 24 小时详情、OpenCode 完整会话、PostgreSQL 终态摘要的顺序读取。响应字段与 Run 级 snapshot 一致，但顶层标识为 `sessionId`，并增加以下可选兼容字段：

| 字段 | 说明 |
|---|---|
| `historyRepresentation` | `FULL` 表示完整详情，`SUMMARY` 表示 PostgreSQL 双摘要降级。旧完整历史映射默认返回 `FULL`。 |
| `replayAvailable` | 是否仍可恢复完整消息、part 与状态事件；摘要降级时为 `false`。 |
| `detailsAvailableUntil` | Redis 运行详情的到期时间；无 Redis 详情或旧数据时可为 `null`。 |

旧客户端可以忽略这些字段；旧后端缺失字段时，新前端按 `FULL` 兼容展示，但不得据此假定 Redis 详情永久可用。

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
| `contentKind` | `RAW_LEGACY` 表示旧原文，`SUMMARY` 表示新模式终态摘要；旧数据可空。 |
| `summaryStatus` | 摘要状态；正常、截断和安全兜底分别使用 `COMPLETE`、`PARTIAL`、`FALLBACK`。 |
| `summaryVersion` | 确定性摘要规则版本；旧数据可空。 |

### Run、Cancel 和 Event API

| 方法 | 路径 | 用途 |
|---|---|---|
| `POST` | `/api/internal/agent/{agentId}/runs` | 启动 Run，前端默认 `agentId=opencode`。 |
| `GET` | `/api/internal/agent/{agentId}/runs/{runId}` | 查询 Run 状态。 |
| `POST` | `/api/internal/agent/{agentId}/runs/{runId}/cancel` | 取消 Run。 |
| `GET` | `/api/internal/agent/{agentId}/runs/{runId}/events` | 订阅 RunEvent SSE。 |
| `GET` | `/api/internal/agent/{agentId}/runs/{runId}/session-tree/messages` | 查询当前 Run scope 的 root + child session message snapshot。 |
| `GET` | `/api/internal/agent/{agentId}/runs/{runId}/diff` | 查询 Run 级 Diff。 |
| `POST` | `/api/internal/agent/{agentId}/runs/{runId}/diff/accept` | 接受 Run 级 Diff。 |
| `POST` | `/api/internal/agent/{agentId}/runs/{runId}/diff/reject` | 拒绝 Run 级 Diff 并触发 opencode revert。 |

旧 `/api/runs/**` 已作废，返回 `410 API_GONE`。内部平台路径 `/api/internal/platform/opencode-runtime/runs/**` 保留给平台内部调用。

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

工作区个人 Git、应用级 Agent 配置 Git 和版本工作区文件操作同样按当前用户 ACTIVE opencode binding 路由到目标 Java；公共配置聚合与服务器列表留在当前 Java，避免把跨服务器操作落到错误磁盘。

用户进程 API 只支持 `agentId=opencode`，必须从认证主体读取当前用户；未认证返回 `UNAUTHENTICATED`，非 `opencode` agent 返回 `VALIDATION_ERROR`。如果当前用户已有 ACTIVE binding 且 `linuxServerId` 不等于当前 Java 所在服务器，API 层会先用统一 `BackendJavaRouteResolver` 找到 binding 所属服务器 Java 的 `listenUrl`，再通过统一 `BackendHttpForwarder` 透传原始 `Authorization`、`X-Trace-Id`、query、请求 body 和统一错误响应到目标 Java；内部路由头 `X-Test-Agent-Backend-Routed: true` 会阻止循环转发。配置管理创建应用工作区、应用版本工作区创建、版本 `git-pull`、Run 创建、初始化和 runtime 代理都纳入同一用户 binding 路由判断。是否已分配只以 `user_opencode_process_bindings(user_id, agent_id)` 的 ACTIVE 记录为准；`GET /processes/me` 目标后端不在线、转发失败或目标返回 5xx 时返回 200 成功响应，`data.status=UNAVAILABLE`、`serviceStatus=NOT_RUNNING`，并保留绑定的 `linuxServerId/port`；若能解析到目标服务器当前在线 Java 的可访问 host，则返回 `serviceAddress={currentHost}:{端口}`，否则 `serviceAddress=null`，表示已分配但暂无法确认健康状态。初始化、Run 启动和 runtime 代理仍在目标后端不可用时返回 `OPENCODE_UNAVAILABLE`，不会自动迁移 binding，也不会在当前 Java 启动旧 binding。目标 Java 上所有强状态查询统一调用 `OpencodeProcessStatusQueryService`：先查询平台进程记录是否存在，再通过本机 manager health 归一为未启动、运行中或 `STALE`；健康成功和明确未启动才更新稳定状态，瞬时 HTTP/manager 异常保留数据库最近状态。已有 RUNNING 进程仅在最近成功健康检查后的 60 秒内允许沿用 READY，超过宽限期后状态查询和未携带有效会话运行上下文的兼容 Run 前置校验都会拒绝旧绿灯。初始化最终由 binding 所属服务器或当前服务器 Java 通过本机已连接的 `opencode-manager` WebSocket 控制面启动进程，并统一调用公共启动服务在 manager `STARTED` 后复用公共状态查询，默认最多等待 manager command-timeout（10 秒）确认 manager state/PID、`/global/health` 和 `/global/config` 都 healthy 后才返回 READY、写入 RUNNING/binding/heartbeat/兼容节点；无 manager 连接、命令超时、manager 返回失败或启动后 health 在等待窗口内仍不健康时分别映射为 `OPENCODE_UNAVAILABLE`、`OPENCODE_TIMEOUT`、`OPENCODE_BAD_GATEWAY` 或统一 opencode 不可用错误。本地和生产都必须启动 Go manager，不再支持 `local-direct` 或 `gateway-mode=local` 绕过。

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/api/internal/agent/{agentId}/processes/me` | 查询当前用户绑定的 opencode 进程状态。 |
| `GET` | `/api/internal/agent/{agentId}/processes/me/message-gate` | 只读查询当前用户公共配置发布消息闸门；不按 binding 路由，不探测 manager/opencode。 |
| `GET` | `/api/internal/agent/{agentId}/processes/me/health?linuxServerId=&containerId=&port=` | 前端弱健康轮询；只按参数和 Redis 快照检查 opencode `/global/health`，不读写数据库。 |
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

- 未绑定用户时从当前 Java 可实际下发命令的 Redis manager 实时候选中选择进程数最少的 `READY` 容器。
- 已有绑定但进程不可用时，只能在 binding 原 `linuxServerId` 内选择当前 Java 可实际下发命令的 Redis 实时候选；原服务器无可用容器或目标后端不在线时返回 `OPENCODE_UNAVAILABLE`，不 fallback 到当前后端任意健康容器，也不迁移用户 binding。
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

控制面只供容器内 `opencode-manager` 使用，不复用用户 JWT 或普通 API token。后端通过 `test-agent.opencode.manager-control.token` / `TEST_AGENT_OPENCODE_MANAGER_TOKEN` 配置独立 manager token；manager 建立 WebSocket upgrade 时必须携带 `Authorization: Bearer <token>`。token 缺失或错误返回统一 `UNAUTHENTICATED`。Go manager 运行路径不得通过 HTTP 与 Java 交互，旧 `manager-backends` 诊断 HTTP 入口已作废并返回 `410 API_GONE`。每个 manager 只连接 `.serverhost + OPENCODE_MANAGER_BACKEND_PORT` 推导出的本服务器 Java；多 Java 后端之间的用户请求转发由 Java API 层完成，manager 不再连接其他服务器 Java。

后端 Java 启动时会把稳定服务器身份写入通用参数 `SYS_DATA_ROOT_DIR` 派生的 `SYS_DATA_ROOT_DIR/.serverid`，把可访问主机地址写入 `SYS_DATA_ROOT_DIR/.serverhost`。Go manager 在非 Windows 环境启动时按同一系统参数的平台默认根目录读取这两个文件（Linux `/data/.testagent/.serverid`、`/data/.testagent/.serverhost`，macOS `$HOME/.testagent/.serverid`、`$HOME/.testagent/.serverhost`），最多等待 30 秒；因此 WebSocket `register` / `managerHeartbeat` 中的 `linuxServerId` 表示稳定服务器身份，不表示容器网卡 IP，也不要求是 IP。`containerId` 固定为 `"ctr_" + SHA256("test-agent/opencode-container/v1\0" + linuxServerId)`，`managerId` 固定为 `"mgr_" + SHA256("test-agent/opencode-manager/v1\0" + containerId)`，SHA-256 使用完整小写十六进制；二者都是 68 字符的不透明 ID。`containerName` 才是可读展示名称，非 Windows 依次取系统 hostname、`/etc/hostname`，Windows 取机器名；解析失败时 manager 启动失败，不接受人工 ID 兜底。每个稳定 `linuxServerId` 只部署一个 worker，容器改名或重建不会改变 ID。

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/api/internal/platform/opencode-runtime/manager-backends` | 已作废，返回 `410 API_GONE`；运行管理请使用 `GET /api/internal/platform/opencode-runtime/management/overview`。 |
| `WS` | `/api/internal/platform/opencode-runtime/manager/ws` | manager 与当前后端实例建立 JSON WebSocket 长连接。 |

历史兼容诊断响应 `data` 已不再由 HTTP 暴露：

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

WebSocket 协议版本固定为 `opencode-manager.v1`。文本帧是 JSON envelope，稳定 `type` 包括 `register`、`registered`、`configRequest`、`configUpdate`、`managerHeartbeat`、`command`、`commandResult`、`error`；`backendListRequest`、`backendListResponse` 和旧 `heartbeat` 只作为兼容枚举保留，不作为新运行路径主入口，Java 收到 `backendListRequest` 会忽略。manager 发出的 `register` 与 `managerHeartbeat` 可带 `buildVersion?: string`，值为二进制构建时按北京时间注入的 `VyyyyMMdd.HHmmss`；旧 manager 缺字段时 Java 保持 `null`，不会阻断连接。`configUpdate` 首次由 manager 的 `configRequest` 拉取，完整帧返回 `maxProcesses`、`sessionRoot`、`configDir`，其中 `sessionRoot/configDir` 分别来自 `common_parameters.OPENCODE_SESSION_DIR` 与 `OPENCODE_PUBLIC_CONFIG_DIR`；后续最大进程数刷新帧只携带 `maxProcesses`，`sessionRoot/configDir` 为空表示路径不变。企业部署要求运行用户的 `~/.config/opencode` 不维护模型或供应商，以避免 OpenCode 合并全局配置时污染 `OPENCODE_CONFIG_DIR` 公共目录。`managerHeartbeat` 每 5 秒由 manager 通过本服务器 Java socket 发送一次，后端写入 Redis manager 快照，TTL 为 10 秒，同时把容器资源指标追加到 Redis 48 小时历史 ZSET；资源指标可带 `metricsSource=cgroup|process|unavailable`，其中 `cgroup` 表示 Linux 容器 cgroup 指标，`process` 表示开发态或降级的 manager 进程指标，`unavailable` 表示当前平台不可采集。`managerHeartbeat.managedProcesses[]` 可选携带本 manager 管理的 opencode server 明细：`port`、`pid`、`baseUrl`、`sessionPath`、`configPath`、`startedAt`、`startCommand`、`traceId`；`startCommand` 是 manager 生成的安全展示命令，只包含 `XDG_DATA_HOME`、`OPENCODE_CONFIG_DIR` 和 `opencode serve` 固定参数，不包含 manager token、用户 JWT、Cookie、prompt 或 API key。manager 生成心跳前会清理 PID 已不存在的本地 stale state，旧 manager 或旧 Redis 快照缺少该字段时按 `null`/缺失兼容。后端命令使用 `commandId=mcmd_...`，manager 回包必须带同一 `commandId` 和 `traceId`；`commandResult.errorCode` 为可选平台错误码，目前 `start` 因目标服务器公共配置目录未初始化失败时返回 `OPENCODE_UNAVAILABLE`，`message` 包含目标服务器和 manager 实际检查的配置目录。当前命令集合为 `start`、`health`、`stop`、`restart`，其中用户初始化、健康检测、运行管理用户进程探测、后台 heartbeat、启动后确认和停止后确认都由目标 Java 通过公共状态查询服务统一下发 `health`；收到完整 `configUpdate` 前，`start`/`restart` 返回 `FAILED`，不会拉起 opencode server；运行管理页面按容器和端口调用 HTTP stop/restart API，当前 Java 先按 Redis manager 快照把请求路由到容器所属 Linux 服务器的 Java，再由目标 Java 转发为本机 manager WebSocket `command`。`stop` 对 state 存在但 OS 进程已结束的端口按幂等成功返回 `STOPPED` 并清理 state；`configUpdate` 成功应用以及 `start`、`stop`、`restart` 成功后，manager 都会立即补发一次 `managerHeartbeat`，不必等待 5 秒周期。

### opencode runtime 运行管理 API

运行管理 API 是高权限平台接口，只允许已认证用户且角色包含 `SUPER_ADMIN` 访问。未认证返回 `UNAUTHENTICATED`，非超级管理员返回 `FORBIDDEN`，非法分页、状态、容器 ID 或端口参数返回 `VALIDATION_ERROR`。overview 和 metrics 只读展示当前 Redis 中仍在线的 Java 后端、容器、manager、manager 管理的本地 opencode server 明细和 manager-backend 连接；页面底部用户进程列表不再从 overview 展示全部进程，而是通过 `user-processes` 按用户关键字查询数据库记录，只在进程所属 `linuxServerId` 为当前 Java 时主动调用本机 manager health，远端进程返回 `REMOTE_SERVER/CHECK_SKIPPED` 避免随机 Java 控制其他服务器 manager。运行管理前端展示形态由 overview 现有 wire shape 派生：按 `linuxServerId` 合并 `linuxServers[]` 与 `backendProcesses[]` 为“服务器 / Java 进程”列表，并从 `backendProcesses[].listenUrl` 提取可访问 host 展示“IP地址”列；按 `containerId` 合并 `containers[]` 与 `managers[]` 为“容器 / 管理进程”列表，并按同一 `linuxServerId` 复用对应 Java host 展示“IP地址”列；再用 `backendProcesses[]`、`managerBackendConnections[]`、`managers[].managedProcesses[]` 渲染 `Java -> Manager -> opencode server` 节点连线拓扑图。restart/stop 只按 `containerId + port` 发起 HTTP 命令；入口 Java 先用统一 `BackendJavaRouteResolver` 定位容器所属 `linuxServerId`，必要时把同一 HTTP 请求通过 `BackendHttpForwarder` 转发到目标 Java，由目标 Java 向本服务器 manager WebSocket 长连接发送控制命令，不直接访问 opencode server。平台已有用户进程记录的命令由公共启动/停止服务回写状态；restart 命令必须在 manager `STARTED` 后通过公共启动服务等待 health healthy，默认最多等待 10 秒，超时仍按统一错误返回；停止命令必须在 manager `STOPPED` 后继续 health，确认不健康才写 `STOPPED` 并返回成功。没有平台用户进程记录的无主 manager state 不新增数据库记录，只同步返回 manager stop/restart 结果。停止成功后前端会先从当前 overview 缓存局部移除对应 `containerId + port` 的明细并更新容量，随后由 manager 立即心跳和周期心跳同步 Redis latest snapshot；重启/停止失败时前端也会刷新 overview 和当前用户进程查询，让最新 DB 状态、managerStatus、healthStatus 与 healthMessage 立即展示。Redis 不可用时 overview 不会回退数据库 heartbeat 字段，命令路由也无法定位远端 Java。

overview 兼容新增 `backendProcesses[].buildVersion?: string` 与 `managers[].buildVersion?: string`。两者均来自各进程自身仍在线的 Redis 快照，非空时匹配 `^V\d{8}\.\d{6}$`；Java 值来自 Spring Boot build-info，manager 值来自 Go linker flag。旧 Java、旧 manager 或旧 Redis JSON 缺字段时返回 `null`/缺失，前端统一显示 `-`；滚动升级期间每行展示自身版本，不做跨服务器探测或启动时间补值。

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/api/internal/platform/opencode-runtime/management/overview` | 查询 Linux 服务器、后端 Java 进程、容器、管理进程、manager-backend 连接和 manager 上报的本地 opencode server 明细。 |
| `GET` | `/api/internal/platform/opencode-runtime/management/user-processes` | 按用户关键字查询用户 opencode server 进程，并返回 manager/PID 与 opencode HTTP 健康检查后的实际状态。 |
| `GET` | `/api/internal/platform/opencode-runtime/management/containers/{containerId}/metrics` | 查询指定容器 Redis 中近 48 小时运行指标历史。 |
| `GET` | `/api/internal/platform/opencode-runtime/management/linux-servers/{linuxServerId}/backend-metrics` | 主接口：按稳定服务器身份查询后端 Java 服务 Redis 中近 48 小时运行指标历史。 |
| `GET` | `/api/internal/platform/opencode-runtime/management/backend-processes/{backendProcessId}/metrics` | 已作废，返回 `410 API_GONE`；新客户端必须使用 `linux-servers/{linuxServerId}/backend-metrics`。 |
| `POST` | `/api/internal/platform/opencode-runtime/management/containers/{containerId}/processes/{port}/restart` | 重启指定容器端口上的 opencode server；平台已有用户进程记录时，manager `STARTED` 后还必须在公共启动等待窗口内 health healthy 才视为成功。 |
| `POST` | `/api/internal/platform/opencode-runtime/management/containers/{containerId}/processes/{port}/stop` | 停止指定容器端口上的 opencode server；平台已有用户进程记录时，manager `STOPPED` 后还必须 health 不健康才视为成功。 |
| `POST` | `/api/internal/platform/opencode-runtime/management/linux-servers/{linuxServerId}/decommission` | 将已停止 Java 和 opencode-manager 的离线服务器从公共配置发布 membership 退役；在线服务器或仍有 PREPARING 发布的发起服务器返回 `CONFLICT`。 |

查询参数：

| 参数 | 说明 |
|---|---|
| `page` | opencode server 进程分页页码，默认 `1`。 |
| `size` | opencode server 进程分页大小，默认 `20`，上限沿用平台 `PageRequest` 的 `200`。 |
| `status` | 可选进程状态；当前活进程视图只返回 `RUNNING` opencode server 进程，非 `RUNNING` 状态会返回空进程页。 |
| `linuxServerId` | 可选 Linux 服务器稳定身份，来自 `TEST_AGENT_LINUX_SERVER_ID` 或 Java 主机名。 |
| `containerId` | 可选容器 ID；由稳定 `linuxServerId` 自动哈希，客户端必须作为不透明字符串处理。 |
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

后端 Java 进程行和后端 history 样本会额外返回可空运行指标；旧字段保留并维持语义：`cpuUsagePercent` 仍表示整机 CPU，`memoryMaxBytes` 等同 `memoryTotalBytes`，`jvmGcPauseMillis` 等同 `jvmGcCollectionTimeDeltaMillis`。服务器字段包括 `cpuCoreCount`、`loadAverage1m/loadAverage5m/loadAverage15m`、`memoryTotalBytes/memoryAvailableBytes/memoryFreeBytes/memoryBuffersBytes/memoryCachedBytes`、`swapTotalBytes/swapFreeBytes/swapUsedBytes/swapUsagePercent`、`diskAvailableBytes`，并继续返回 `memoryUsedBytes/memoryUsagePercent/diskMaxBytes/diskUsedBytes/diskUsagePercent`。Java 进程字段包括 `jvmProcessCpuUsagePercent`、`jvmProcessCpuCoreUsage`、`jvmProcessCpuTimeNanos`、`jvmProcessResidentMemoryBytes`、`jvmProcessPeakResidentMemoryBytes`、`jvmProcessVirtualMemoryBytes`、`jvmProcessSwapBytes`、`jvmOpenFileDescriptorCount`、`jvmMaxFileDescriptorCount`。JVM 字段包括 heap/non-heap used/committed/max、direct/mapped buffer count/used/capacity、`jvmGcCollectionTimeDeltaMillis`、`jvmGcCollectionCountDelta`、`jvmGcTimePercent`、`jvmThreadsDaemon`、`jvmThreadsPeak`、`jvmThreadsTotalStarted`，并继续返回 `jvmMemoryUsedBytes/jvmMemoryCommittedBytes/jvmMemoryMaxBytes/jvmThreadsLive`。容器行和容器 history 样本可返回 `metricsSource`，取值为 `cgroup`、`process`、`unavailable` 或旧样本的 `null`；容器 history 样本包含 `sampledAt/maxProcesses/currentProcesses/metricsSource/cpuUsagePercent/memoryMaxBytes/memoryUsedBytes/memoryUsagePercent/diskReadBytesPerSecond/diskWriteBytesPerSecond`；后端 history 响应包含 `linuxServerId`，`backendProcessId` 保留为可空兼容字段。服务器 CPU/load/内存/swap/磁盘来自 `test-agent:runtime-metrics:server:{linuxServerId}`，Java 进程与 JVM 指标来自 `test-agent:runtime-metrics:backend:{linuxServerId}`，history API 会按时间合并两类样本并按新旧字段降采样；旧 Redis JSON 缺字段读取为 `null`，未知字段会被忽略。旧 `backendProcessId` history API 已作废，返回 `410 API_GONE`。运行管理页面按 `linuxServerId` 合并服务器与 Java 行，点击行或“趋势”按钮查询该服务器 Java 服务指标历史；异常缺服务器或缺 Java 时展示 `-`，不改变 overview JSON 结构。`managers[].managedProcesses[]` 在 manager 本地快照字段之外可返回归属字段：`ownership` 为 `BOUND` 表示该端口匹配到当前 `ACTIVE` 用户绑定，前端展示为“有主进程”；`UNBOUND` 表示没有当前活跃绑定或没有匹配用户进程，前端展示为“无主进程”。`processId`、`processStatus`、`healthMessage` 来自同服务器同容器同端口的用户进程候选；`userId`、`username`、`bindingAgentId`、`bindingStatus`、`bindingUpdatedAt` 仅在 `ACTIVE` 绑定存在时返回。所有归属字段保持可空/可缺失以兼容旧后端、旧 manager 和旧 Redis 快照；运行管理页面按 `containerId` 合并容器与 manager 行，若容器 `currentProcesses` 与 `managedProcesses.length` 不一致，会提示容量计数来自 manager state，而明细来自 manager 上报数组。`user-processes` 返回的 `managerStatus` 表示 manager/PID 层面的实际状态，`healthStatus` 表示 opencode HTTP 健康检查结果：`HEALTHY` 为健康，`NOT_RUNNING` 表示 manager 确认进程未运行或 PID 不存在，`UNHEALTHY` 表示 PID 存在但 HTTP 健康检查失败，`CHECK_FAILED` 表示 manager 通信或探测异常；`restartable=true` 时前端允许调用 restart 命令。Manager 与后端连接不再以独立表格展示，前端改为使用 `managerBackendConnections[]` 生成 Java 到 manager 的连线，并使用 `managers[].managedProcesses[]` 生成 manager 到 opencode server 的连线；旧响应缺少 `managedProcesses` 时仍展示 manager 节点，manager 到 opencode 的边为空。展开明细中的“重启/停止”按钮调用上述 HTTP 命令端点，成功后前端重新拉取 overview；底部用户进程列表中的“重启”按钮同样按该进程的 `containerId + port` 调用 restart，成功后刷新当前用户查询。对已有平台用户进程记录的端口，restart 成功必须经过公共启动服务再次 health 确认；用户进程已 `STOPPED` 或 manager 返回 `port ... is not managed` 时，目标 Java 会复用原 `containerId + port` 调用 manager `start` 并同样确认 health 后才返回成功。对已有平台用户进程记录的端口，stop 成功必须经过公共停止服务再次 health 确认，health 仍 healthy 时返回统一错误且不回写 `STOPPED`；health 不健康时才回写 `STOPPED`，返回的 command result 中 `healthy=false`。没有平台用户进程记录的无主 manager state 仍只同步返回本次 manager 回包。命令结果不代表后续 Redis 快照一定已经刷新。拓扑列表固定最多返回 500 条，避免管理页一次性读取过多连接和进程快照。Java 后端每 5 秒按 `backendProcessId` 写入 Redis Java 快照，并按 `linuxServerId` 分组用于服务器级展示和目标选择；Go manager 每 5 秒通过 WebSocket 写入 Redis manager 快照，两类快照 TTL 固定 10 秒；manager 成功应用 `configUpdate` 时会立即补发心跳，使容量参数变更尽快进入 overview。运行管理前端打开页面后每 5 秒刷新 overview，避免长时间停留时继续展示旧 Redis 快照；底部用户进程查询只在输入用户关键字后触发，不随 overview 自动展示所有进程。数据库中的历史 heartbeat 字段保留兼容但不参与在线判断。Java/manager 运行指标历史写入 Redis ZSET，保留近 48 小时原始 5 秒样本，history API 默认查询近 1 小时，前端使用 `windowMinutes` 在 1 分钟到 48 小时预设之间切换，超出 `maxPoints` 时按时间桶降采样。Redis 历史只保证同一稳定服务器身份的 Java 后端重启后连续；若 Redis 自身重启且未启用 AOF/RDB，历史样本会丢失。opencode server 由后端每 3 分钟通过 manager health 命令确认并刷新 Redis 进程心跳，Redis 进程心跳 key 5 分钟过期，索引清理每 5 分钟执行一次。`opencodeProcesses.items[]` 的 `bindingAgentId`、`bindingStatus`、`bindingUpdatedAt` 仅在该进程仍是当前用户绑定时返回，否则为 `null`。

### scheduler-management 定时任务管理 API

定时任务管理 API 是高权限平台接口，只允许已认证用户且角色包含 `SUPER_ADMIN` 访问。未认证返回 `UNAUTHENTICATED`，非超级管理员返回 `FORBIDDEN`，非法分页、任务 key、状态、触发类型、Cron 或锁 TTL 返回 `VALIDATION_ERROR`。全局 `TEST_AGENT_SCHEDULER_ENABLED=false` 时手动触发返回 `CONFLICT`，避免创建不会被后台 runner 消费的 `PENDING` 运行记录。本接口只管理框架任务定义和运行记录，不开放普通用户级 Cron 计划创建 API，也不创建定时会话或 Run。

Base URL：`/api/internal/platform/scheduler-management`

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/tasks` | 分页查询代码注册的任务定义。 |
| `GET` | `/tasks/{taskKey}` | 查询单个任务定义。 |
| `GET` | `/diagnostics?taskKey={taskKey}` | 查询当前 Java 进程内 scheduler 生效配置、扫描线程、Redis 锁和选中任务阻塞原因。 |
| `PATCH` | `/tasks/{taskKey}` | 调整任务启停、Cron 表达式和锁 TTL。 |
| `POST` | `/tasks/{taskKey}/trigger` | 创建管理员手动触发运行记录，后台 runner 异步执行；要求全局 scheduler 已启用，任务停用时超级管理员仍可手动触发。 |
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

`GET /diagnostics` 查询参数：

| 参数 | 说明 |
|---|---|
| `taskKey` | 必填任务 key。 |

诊断响应字段：

```json
{
  "scheduler": {
    "enabled": true,
    "runnerRunning": true,
    "instanceId": "backend-...",
    "scanIntervalSeconds": 30,
    "dueTaskLimit": 50,
    "manualRunLimit": 50,
    "lastScanStartedAt": "2026-06-25T02:00:00Z",
    "lastScanFinishedAt": "2026-06-25T02:00:01Z",
    "lastScanErrorMessage": null
  },
  "redisLock": {
    "checkable": true,
    "lockKey": "test-agent:scheduler:lock:daily.cleanup",
    "locked": false,
    "ttlMillis": null,
    "errorMessage": null
  },
  "task": {
    "taskKey": "daily.cleanup",
    "enabled": true,
    "registrationStatus": "REGISTERED",
    "registrationStatusLabel": "已注册",
    "nextFireAt": "2026-06-25T02:00:00Z",
    "lockTtlSeconds": 300,
    "currentRun": null,
    "latestRun": null,
    "pendingManualRunCount": 0
  },
  "diagnosis": {
    "manualTriggerReady": true,
    "cronReady": true,
    "blockers": []
  }
}
```

`diagnosis.blockers[].code` 是稳定诊断码，当前可能值：`SCHEDULER_DISABLED`、`RUNNER_NOT_RUNNING`、`HANDLER_MISSING`、`TASK_DISABLED_FOR_CRON`、`ACTIVE_RUN`、`LOCK_HELD`。诊断接口只读，不抢锁、不释放锁、不修改运行记录；`redisLock` 不返回锁 token。

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

### 会话运行上下文 API

`POST /api/internal/agent/{agentId}/sessions/{sessionId}/run-context` 为当前登录用户签发后续 Run 使用的会话运行上下文，无请求体。前端在新建 Session、首次进入或切换到历史 Session 时调用一次；页面内后续 Run 复用同一个结果，只有上下文失效或页面刷新后才重新签发。

后端从权威 Session、Workspace、当前用户 `READY` 进程、agent binding、执行节点、Linux 服务器和后端解析后的可信工作区根路径构造上下文。响应只暴露 opaque token、版本和过期时间，不返回上述内部字段：

```json
{
  "success": true,
  "data": {
    "contextToken": "ctx_<256-bit-opaque-token>",
    "contextVersion": 1,
    "expiresAt": "2026-07-11T08:00:00Z"
  },
  "traceId": "trace_1234567890abcdef"
}
```

`contextToken` 使用 256 位安全随机数生成，绑定认证用户、Session、Workspace、agent、完整进程快照、执行节点、Linux 服务器、可复用远端 session 和可信工作区路径。原始 token 只返回给浏览器并保存在当前页面内存；Redis token key 只保存 token 的 SHA-256 摘要。Redis 同时维护五类 ZSET 反向索引、generation、Session revoke gate 与 user/Workspace mutation gate；三类 gate TTL 均为 24 小时。签发先校验 Session owner；托管 Workspace 随后权威校验应用仍启用、当前用户仍是有效成员，个人 Workspace 还必须属于当前用户，`SUPER_ADMIN` 不旁路。该权限校验先于历史 Workspace 回绑；自回绑发生后放弃本轮保存并只用全新租约完整重读一次，其它 CAS 失败直接返回过期。非托管历史 Workspace 沿用 Session owner、ACTIVE、可信路径和服务器归属规则。

主动失效入口包括 Session 归档、进程状态变化、Workspace 可信字段变化、成员/角色撤权和可信路径参数重载。用户权限及 Workspace 关系型变更先建立 mutation gate 并失效旧 token，保存成功后 Lua 原子再次失效并释放自己的 gate token，数据库失败只释放自己的 token；Redis 完成失败时 gate 留存 fail-closed，最多 24 小时。generation 不设 TTL，确保 gate 过期后旧 token 仍不能复活。Redis 读取、脚本或写入失败返回 `503 RUNTIME_STATE_UNAVAILABLE`，不回退 PostgreSQL 或 JVM 内存。

鉴权、Session/Workspace 归属或当前用户进程校验失败仍使用既有 `UNAUTHENTICATED`、`FORBIDDEN`、`NOT_FOUND`、`OPENCODE_UNAVAILABLE`。Run 缺少 token 且兼容开关关闭时返回 `409 CONVERSATION_CONTEXT_REQUIRED`；token 未命中、过期、版本不匹配或与当前用户/agent/Session 不匹配时返回 `409 CONVERSATION_CONTEXT_EXPIRED`。

对应测试：`ConversationContextControllerTest`、`ConversationContextApplicationServiceTest`、`ManagedConversationWorkspaceAccessAuthorizerTest`、`ConversationMemberRevocationIntegrationTest`、`ConversationRunContextResolverTest`、`RedisConversationContextStoreTest`、`RedisConversationContextStoreIntegrationTest`。

### Run 创建

`POST /api/internal/agent/{agentId}/runs` 请求体：

```json
{
  "sessionId": "ses_...",
  "prompt": "run prompt",
  "contextToken": "ctx_...",
  "clientRequestId": "req_..."
}
```

Run 路由、远端 session 解析和事件订阅完成后，接口立即返回 `RUNNING`，不等待 agent 的 prompt/command HTTP 请求完成。后台 `prompt_async` 或 `/session/{sessionID}/command` 的调用完成异常只是候选失败：平台保留 300ms 根终态裁决窗口，窗口内由 root session 的 `idle` / `session.error` 派生的 `run.succeeded` / `run.failed` 获胜；窗口结束仍无 root 终态且 Run 仍为运行态时，才通过同一 RunEvent 链路追加一次安全的 `run.failed`。真正的事件流中断继续使用运行态丢失与 owner 恢复规则。前端不应把创建 Run 接口的等待时间当作智能体执行超时。

携带有效 `contextToken` 时，`ConversationRunContextResolver` 在 Run 产生数据库副作用前完成 Redis 校验，并以完整进程快照调用公共状态服务 `querySnapshot` 动态探测；该路径不按 processId 查询数据库，稳定 `RUNNING` 为 0 次 Repository SELECT、0 次数据库写入，只有状态、PID 或服务地址确有变化时写一次。探测返回 `STALE` 时拒绝本次 Run 但保留 token；只有明确返回 `NOT_STARTED` 时才按 processId 失效相关上下文。`RunApplicationService` 随后直接复用 Session、Workspace、ExecutionNode 和可空 AgentSessionBinding 快照，已有远端 session 的其余控制面查询仍为 0 次 PostgreSQL SELECT。

`POST /api/internal/agent/{agentId}/runs` 与兼容入口 `POST /api/internal/platform/opencode-runtime/runs` 在进入 Controller 前也会读取一次缓存请求体，硬上限为 32 MiB，超限返回 `400 VALIDATION_ERROR` 且不执行 assignment 查询。存在 `contextToken` 时，路由过滤器调用 Redis `resolveForRouting` 校验认证用户、agent、Session、过期时间、全部 generation 与 revoke gate，再按上下文 `linuxServerId` 使用公共路由器选择目标 Java；该读取不续期、不修改 Redis，也不查询用户进程 assignment。字段已出现但为空、非字符串或无效时返回 `409 CONVERSATION_CONTEXT_EXPIRED`，不得回退无 token 兼容路径。过滤器装饰后的请求体可供远端转发器或本地 Controller 再次完整读取。无 token 请求仅在兼容期开启时走原 assignment 路由。

请求体保持向后兼容，并支持以下可选字段：

```json
{
  "sessionId": "ses_...",
  "prompt": "run prompt",
  "contextToken": "ctx_...",
  "clientRequestId": "req_...",
  "parts": [
    { "type": "text", "text": "run prompt" },
    {
      "type": "file",
      "path": "src/App.tsx",
      "source": { "text": "file content", "start": 0, "end": 12, "contextType": "selection", "startLine": 3, "endLine": 8 }
    },
    { "type": "agent", "agentId": "build" },
    { "type": "reference", "id": "ref_1", "label": "Current issue", "uri": "mcp://issues/1" }
  ],
  "messageId": "msg_...",
  "agent": "build",
  "model": "anthropic/claude-sonnet-4-5",
  "variant": "default",
  "mode": "build",
  "command": "test-design-path",
  "arguments": "对车贷的开发文档，生成路径图"
}
```

`command` / `arguments` 为可选字段。提供 `command` 时，平台仍先创建并持久化 Run、订阅 RunEvent，再由后端后台调用 opencode 原生 `/session/{sessionID}/command`；创建 Run 接口不会等待技能执行完成。这样 slash 技能与普通 prompt 共用 active-run 恢复、SSE 实时输出、终态裁决和取消语义。未提供 `command` 时继续使用 `prompt_async`。两种调用的 HTTP 完成异常都不能覆盖已经到达的 root 终态，也不能依赖第三方英文错误文案判断是否延迟裁决。

兼容要求：

- 旧 `prompt: string` 继续有效；`parts` 缺失时后端按单个 text part 处理。
- `contextToken`、`clientRequestId`、`parts`、`messageId`、`agent`、`model`、`variant`、`mode` 均为可选字段；新前端必须传入前两项，旧客户端在 `test-agent.redis-summary.legacy-run-without-context-enabled=true` 的兼容窗口内仍可省略。
- `messageId` 同时是本轮远端 USER dispatch 锚点。`LEGACY_FULL` 优先沿用显式旧值并原样透传，缺失时由当前 agent runtime 生成；`REDIS_SUMMARY` 始终由 runtime 为新 Run 自动生成。opencode 自动 ID 固定为与 1.17.8 `MessageID.ascending()` 字典序兼容的 `msg_[0-9a-f]{12}[0-9A-Za-z]{14}`，不能使用随机 UUID，否则后续 user ID 可能小于上一轮 assistant 并被远端误判为已经回复。同一生成值传给 agent command、写入平台 USER `remoteMessageId`，并复用于 root scope、Redis manifest 和持久化锚点；锚点来源不一致时 Run 级投影 fail-closed，不按“最后一条 user”猜测。其它 agent 未覆盖 runtime 工厂时仍保持原有 `msg_` + 32 位十六进制 UUID。
- `clientRequestId` 由浏览器为一次发送生成；若 `contextToken` 失效，前端重新签发上下文并只重试一次，重试必须复用同一个 `clientRequestId`。服务端只以已成功写入 PostgreSQL 的唯一 Run 锚点确认幂等成功；Redis 中已声明但尚无锚点的 crash-window manifest 不会作为成功响应返回，短保护期后由恢复扫描清理。
- 前端 HTTP 与 RunEvent SSE 原始报文观察副本在进入页面缓存前统一递归脱敏 `contextToken`，后端 API/Service 日志与错误详情也必须脱敏；`clientRequestId` 不是密钥，但不得被用来替代鉴权或 token 绑定校验。
- `parts` 会下沉为当前 agent runtime 的 prompt parts；`opencode` 实现适配为 `prompt_async` 的 `text/file/agent` parts，`reference` part 会转换为可读 text part。
- file part 带 `source.text` 或 `content` 时后端生成 `data:` URL；前端图片附件可直接提交 `url: "data:<mime>;base64,..."`。只有没有内联内容或 URL 时，后端才把 workspace 内路径转为 `file://` URL，越出 workspace 的路径返回 `VALIDATION_ERROR`。`source.startLine/endLine/contextType` 是可选前端来源元数据，当前用于工作区选区附件展示，旧客户端和旧后端可忽略。
- `model` 使用 `providerId/modelId` 字符串格式；Java 端只解析并透传给 opencode，不再读取数据库模型目录做校验、默认模型回退或 `/global/config` provider 同步。前端模型和供应商下拉始终以 opencode 配置文件的 `/api/model`、`/api/provider` 原生结果为准。
- Agent/Model/Variant/Mode 属于运行态选择，不代表 Provider/server/settings 配置；其中 `mode` 当前只保留为平台字段，opencode `PromptInput` 不支持该字段，因此 opencode runtime 不写入 `prompt_async` 请求体。

有效 `contextToken` 的启动流程复用完整进程、执行节点和可空 binding 快照；公共 `querySnapshot` 复用统一 manager health 映射，但不先查询进程 Repository。稳定 `RUNNING` 只刷新 Redis heartbeat，状态、PID 或服务地址变化时最多写一次；`STALE` 拒绝本次 Run 但不删除 token，`NOT_STARTED` 才失效该进程关联的上下文。未携带 token 的兼容路径仍先校验当前认证用户是否已有 `READY` opencode 进程，未就绪时返回 `OPENCODE_UNAVAILABLE`，不创建本地 Run；其余 binding 兼容与匿名固定节点路由保持不变。
Run 进入成功、失败或取消终态后，后端会从 agent 标准 session messages 的最新页沿 `before` cursor 向前查找本轮稳定 USER 锚点，单页 100、最多 20 页，只把该 user 以及 `parentID/parentId` 直接指向它的 assistant 纳入当前 Run。锚点未到达、来源冲突、重复 cursor、页数超限或旧 Run 时间窗内不唯一时不写任何消息；不得边分页边把当前 `runId` 赋给全会话。选择成功后只 upsert 本轮 assistant 可见 text、完整 parts，并把本轮最后一条 assistant 的 token/cost 写入 `runs`；reasoning 和 tool output 不拼入可见正文，拉取失败时保留数据库已有快照。已经错误归属的历史消息不在读取或刷新时修复。

`test-agent.redis-summary.enabled=false`、rollout `0` 是稳定默认。部署方完成 Redis 持久化、安全、容量与故障恢复验收后，可按 userId 稳定哈希逐步提高比例；只有携带有效 `contextToken + clientRequestId` 的新 Run 可进入 `REDIS_SUMMARY`，活动 Run 固定创建时模式，回滚只影响后续新 Run。旧客户端兼容调用会递增 `legacy_run_without_context_total`；该指标连续 7 天为 0 后再关闭 `legacy-run-without-context-enabled`，关闭后缺 token 返回 `409 CONVERSATION_CONTEXT_REQUIRED`，不会自动查询数据库。

### system-management 用户管理 API

用户管理 API 是高权限平台接口，只允许已认证用户且角色包含 `SUPER_ADMIN` 访问。未认证返回 `UNAUTHENTICATED`，非超级管理员返回 `FORBIDDEN`。当前创建用户能力用于研发测试便捷造号，创建时使用默认密码 `123456`，前端不传密码字段。当前不包含普通用户发起审批通知流，角色调整由超级管理员直接操作。

Base URL：`/api/internal/platform/system-management`

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/users` | 分页查询用户列表，可按关键字匹配用户名/统一认证号。 |
| `POST` | `/users` | 创建测试用户，密码默认为 `123456`，并授予单个角色。 |
| `PUT` | `/users/{userId}/roles` | 替换指定用户的全局角色，当前测试入口只保留单个角色。 |
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
  "organization": "企业",
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
  "organization": "企业",
  "rdDepartment": "研发部",
  "department": "测试部"
}
```

- `unifiedAuthId`、`username`、`role` 必填；`organization`/`rdDepartment`/`department` 选填。
- 密码由后端注入默认值 `123456`，前端不传。
- 用户名或统一认证号已存在时返回 `CONFLICT`。
- 角色无效时返回 `VALIDATION_ERROR`。

`PUT /users/{userId}/roles` 请求体：

```json
{
  "role": "USER"
}
```

- `userId` 为平台用户 ID。
- `role` 必填，必须是 `ROLE` 字典中的有效角色 code。
- 用户不存在时返回 `NOT_FOUND`；角色无效时返回 `VALIDATION_ERROR`。
- 更新成功后返回更新后的用户响应字段，`roles` / `roleLabels` 反映最新角色。

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
| `GET` | `/api/internal/platform/opencode-runtime/agents?workspaceId=` | 读取当前 workspace 的 Agent 列表。 |
| `GET` | `/api/internal/platform/opencode-runtime/models?workspaceId=` | 读取当前 workspace 的 Model 列表。 |
| `GET` | `/api/internal/platform/opencode-runtime/providers?workspaceId=` | 读取当前 workspace 的 Provider 只读列表。 |
| `GET` | `/api/internal/platform/opencode-runtime/commands?workspaceId=` | 读取可执行命令列表。 |
| `GET` | `/api/internal/platform/opencode-runtime/references?workspaceId=` | 读取可引用上下文目录。 |
| `GET` | `/api/internal/platform/opencode-runtime/status?workspaceId=` | 读取 opencode runtime 健康状态，后端映射到 opencode `/global/health`。 |
| `GET` | `/api/internal/platform/opencode-runtime/fs/list?workspaceId=&path=` | 通过 opencode runtime 列目录。 |
| `GET` | `/api/internal/platform/opencode-runtime/fs/find?workspaceId=&query=` | 通过 opencode runtime 查找文件。 |
| `GET` | `/api/internal/platform/opencode-runtime/fs/read?workspaceId=&path=` | 通过 opencode runtime 读文件内容。 |
| `GET` | `/api/internal/platform/opencode-runtime/vcs/status?workspaceId=` | 读取 VCS 状态。 |
| `GET` | `/api/internal/platform/opencode-runtime/vcs/diff?workspaceId=&mode=working\|git\|branch&context=` | 读取 VCS Diff。 |
| `GET` | `/api/internal/platform/opencode-runtime/lsp/status?workspaceId=` | 读取 LSP 状态。 |
| `GET` | `/api/internal/platform/opencode-runtime/mcp/status?workspaceId=` | 读取 MCP 状态。 |
| `GET` | `/api/internal/platform/opencode-runtime/mcp/resources?workspaceId=` | 读取 MCP resource 目录，后端映射到 opencode `/experimental/resource`。 |
| `GET` | `/api/internal/platform/opencode-runtime/mcp/tools?workspaceId=&provider=&model=` | 读取 MCP/runtime tool 目录；带 provider/model 时返回工具 schema，否则返回 tool id 降级列表。 |
| `GET` | `/api/internal/platform/opencode-runtime/config?workspaceId=` | 读取 opencode global config。 |
| `PATCH` | `/api/internal/platform/opencode-runtime/config?workspaceId=` | 更新 opencode global config，body 透传给 runtime。 |
| `POST` | `/api/internal/platform/opencode-runtime/global/dispose` | 触发 opencode runtime dispose。 |
| `GET` | `/api/internal/platform/opencode-runtime/provider/auth?workspaceId=` | 查询 provider auth 状态。 |
| `POST` | `/api/internal/platform/opencode-runtime/provider/{providerId}/oauth/authorize` | 发起 provider OAuth。 |
| `POST` | `/api/internal/platform/opencode-runtime/provider/{providerId}/oauth/callback` | 完成 provider OAuth 回调。 |
| `PUT` | `/api/internal/platform/opencode-runtime/auth/{providerId}` | 写入 provider auth secret，secret 只透传不落前端持久化状态。 |
| `DELETE` | `/api/internal/platform/opencode-runtime/auth/{providerId}` | 删除 provider auth secret。 |
| `GET` | `/api/internal/platform/opencode-runtime/worktrees?workspaceId=` | 查询 opencode experimental worktree。 |
| `POST` | `/api/internal/platform/opencode-runtime/worktrees` | 创建 worktree，body 可包含 `workspaceId`、`branch`、`path` 等 opencode 兼容字段。 |
| `DELETE` | `/api/internal/platform/opencode-runtime/worktrees` | 删除 worktree，body 透传给 runtime。 |
| `POST` | `/api/internal/platform/opencode-runtime/worktrees/reset` | 重置 worktree。 |
| `POST` | `/api/internal/platform/opencode-runtime/mcp/{name}/auth` | 发起 MCP auth。 |
| `POST` | `/api/internal/platform/opencode-runtime/mcp/{name}/auth/callback` | 完成 MCP auth callback。 |
| `POST` | `/api/internal/platform/opencode-runtime/mcp/{name}/auth/authenticate` | 执行 MCP auth authenticate 步骤。 |
| `DELETE` | `/api/internal/platform/opencode-runtime/mcp/{name}/auth` | 删除 MCP auth。 |

旧 `/api/agents`、`/api/models`、`/api/providers`、`/api/commands`、`/api/references`、`/api/status`、`/api/fs/**`、`/api/vcs/**`、`/api/lsp/**`、`/api/mcp/**`、`/api/config`、`/api/global/**`、`/api/provider/**`、`/api/worktrees/**` 已作废，统一返回 `410 API_GONE`。agent path 继续使用 `/api/internal/agent/{agentId}/...`，后续 agent 必须适配到相同平台 DTO 和错误格式。

Model/Provider 目录兼容说明：

- `/api/internal/platform/opencode-runtime/models` 和 `/api/internal/platform/opencode-runtime/providers` 始终代理当前用户 opencode server 的 `/api/model`、`/api/provider`，不再受 `ai_model_configs`、内部供应商表或 `test-agent.model-catalog.source` 影响。
- 前端会把 opencode 原生 provider map 和 model map 归一化成已有 `ModelInfo` / `ProviderInfo`，但不新增数据库模型字段；浏览器历史偏好仍按当前 opencode 返回目录做前端侧清理。

内部模型供应商配置 API：

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/api/internal/platform/configuration-management/internal-model-providers` | 查询内部供应商地址配置和 token 是否已配置。 |
| `PUT` | `/api/internal/platform/configuration-management/internal-model-providers` | 覆盖保存内部供应商地址配置；`authToken` 传空/缺失时不修改 token。 |
| `GET` | `/api/internal/platform/configuration-management/internal-model-providers/refresh-status` | 查询当前 Java 进程内存中的启用供应商快照。 |
| `POST` | `/api/internal/platform/configuration-management/internal-model-providers/refresh` | 发布跨实例刷新事件并返回当前 Java 内存快照。 |

内部模型代理 API：

| 方法 | 路径 | 用途 |
|---|---|---|
| `*` | `/api/internal/platform/opencode-runtime/internal-model-proxy/v1/**` | 仅供 opencode 子进程调用的 OpenAI-compatible 代理，不给前端 SDK 暴露会话便捷方法。 |

代理只接受 `Authorization: Bearer ${TEST_AGENT_INTERNAL_PROXY_API_KEY}`；请求头 `X-Enterprise-Model-Provider` 指定内部供应商，`ucid` 由 opencode 配置从 `ENTERPRISE_UCID` 注入。Java 从内存供应商快照找到 `baseUrl` 后转发到对应 OpenAI-compatible 路径，并从 `internal_model_proxy_settings` 注入数据库维护的全局上游 token、`ucid` 和 traceId。仅 `2xx + text/event-stream` 进入 SSE 语义转换，事件字段和 `[DONE]` 保留；没有 `reasoning_content` 时，`delta.content` 里的 `<think>...</think>` 会转换为 `delta.reasoning_content`，普通正文仍保留在 `delta.content`；已有 textual `reasoning_content` 时整个 delta 原样保留，不再解析 `content`。非 `2xx`（包括 `4xx + text/event-stream`）和非 SSE 响应原样透传状态码、Content-Type、Content-Encoding、错误正文、Retry-After 与 trace header；连接/首个响应/首个事件/事件空闲边界分别为 10 秒/30 秒/30 秒/120 秒，不设置整体 SSE 生命周期超时，下游取消会取消上游订阅。

opencode 公共配置样例（企业单后端部署可直接使用 `deploy/internal/opencode.jsonc.example`）：

```json
{
  "$schema": "https://opencode.ai/config.json",
  "model": "enterprise-qwen/Qwen3.6-27B",
  "small_model": "enterprise-qwen/Qwen3.6-27B",
  "enabled_providers": ["enterprise-qwen", "enterprise-deepseek"],
  "provider": {
    "enterprise-qwen": {
      "name": "企业通义",
      "npm": "@ai-sdk/openai-compatible",
      "api": "{env:TEST_AGENT_INTERNAL_PROXY_BASE_URL}",
      "env": ["TEST_AGENT_INTERNAL_PROXY_API_KEY", "TEST_AGENT_INTERNAL_PROXY_BASE_URL", "ENTERPRISE_UCID"],
      "options": {
        "apiKey": "{env:TEST_AGENT_INTERNAL_PROXY_API_KEY}",
        "baseURL": "{env:TEST_AGENT_INTERNAL_PROXY_BASE_URL}",
        "includeUsage": false,
        "timeout": false,
        "headerTimeout": 30000,
        "chunkTimeout": 120000,
        "headers": {
          "X-Enterprise-Model-Provider": "qwen-prod",
          "ucid": "{env:ENTERPRISE_UCID}"
        }
      },
      "models": {
        "Qwen3.6-27B": {
          "name": "Qwen3.6 27B",
          "id": "Qwen3.6-27B",
          "reasoning": true,
          "tool_call": true,
          "temperature": true,
          "interleaved": { "field": "reasoning_content" },
          "limit": { "context": 131072, "output": 8192 }
        }
      }
    },
    "enterprise-deepseek": {
      "name": "企业 DeepSeek",
      "npm": "@ai-sdk/openai-compatible",
      "api": "{env:TEST_AGENT_INTERNAL_PROXY_BASE_URL}",
      "env": ["TEST_AGENT_INTERNAL_PROXY_API_KEY", "TEST_AGENT_INTERNAL_PROXY_BASE_URL", "ENTERPRISE_UCID"],
      "options": {
        "apiKey": "{env:TEST_AGENT_INTERNAL_PROXY_API_KEY}",
        "baseURL": "{env:TEST_AGENT_INTERNAL_PROXY_BASE_URL}",
        "includeUsage": false,
        "timeout": false,
        "headerTimeout": 30000,
        "chunkTimeout": 120000,
        "headers": {
          "X-Enterprise-Model-Provider": "deepseek-prod",
          "ucid": "{env:ENTERPRISE_UCID}"
        }
      },
      "models": {
        "DeepSeek-V4-Flash-W8A8": {
          "name": "DeepSeek V4 Flash W8A8",
          "id": "DeepSeek-V4-Flash-W8A8",
          "reasoning": true,
          "tool_call": true,
          "temperature": true,
          "interleaved": { "field": "reasoning_content" },
          "limit": { "context": 65536, "output": 8192 }
        }
      }
    }
  }
}
```

`provider` 下的 `enterprise-qwen` / `enterprise-deepseek` 是 opencode 原生 provider key，决定前端模型标识；`X-Enterprise-Model-Provider` 的 `qwen-prod` / `deepseek-prod` 是 Java 内部代理路由键，必须与数据库 `internal_model_providers.provider_id` 完全一致。`includeUsage=false` 用于避免 opencode 1.17.8 默认向不支持 `stream_options.include_usage` 的企业内部接口追加该参数。上游 token 只保存在 `internal_model_proxy_settings`，不得写入 opencode 配置、`backend.env` 或 `docker.env`。

Session 运行态接口：

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/children` | 查询远端 opencode session children。 |
| `GET` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/todo` | 查询 Todo 列表。 |
| `GET` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/diff?messageId=` | 查询 session/message 级 Diff。 |
| `POST` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/abort` | 中止当前 session 执行。 |
| `POST` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/fork` | fork session。 |
| `POST` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/side-question` | 旁路问答：从指定消息边界创建临时 fork，必要时只在临时 fork 上调用 summarize/compact，再使用 `plan` agent 的只读权限发送问题，等待工具执行后的自然语言最终回答并删除临时会话；问题和回答不写入主会话历史。body 为 `{ question, messageId?, agent?, model? }`，`question` 最长 4000 字；上下文超过 40 条消息或约 48000 字符时必须提供 `provider/model` 格式的 `model`。响应为 `{ answer, compacted }`。 |
| `POST` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/side-question/runs` | 启动流式旁路问答。body 为 `{ question, messageId?, model? }`，服务端固定使用 `build` agent，并以系统提示限制为只读，立即返回 `{ runId }`；客户端随后通过既有 RunEvent SSE 订阅该 Run。平台创建从一开始即为 `ARCHIVED` 的内部 Session 和 `SIDE_QUESTION` Run，问题与答案不进入主 Session 消息历史。旧同步 `side-question` 路径继续保留兼容。 |
| `POST` | `/api/internal/platform/opencode-runtime/manual-question/runs` | 无主对话的手册问答。body 为 `{ workspaceId, question, model? }`，前端问题已携带当前内置手册章节；后端创建归档内部 Session 和独立 `SIDE_QUESTION` Run，直接使用内部远端临时会话回答并在终态后删除，不创建普通主 Session。立即返回 `{ runId }`，事件仍通过既有 RunEvent SSE 订阅。 |
| `POST` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/compact` | 调用 opencode summarize/compact。 |
| `POST` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/revert` | revert 指定 message。 |
| `POST` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/unrevert` | 取消 revert。 |
| `POST` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/command` | 执行 session command。 |
| `POST` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/shell` | 执行 shell command，P1/P2 前端以输出卡片展示。 |
| `POST` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/share` | 创建 opencode session share。 |
| `DELETE` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/share` | 取消 opencode session share。 |
| `GET` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/permissions` | 读取当前 Session 的 pending permission；OpenCode 原生列表是进程级结果，后端按绑定的 remote session 过滤，不能把其它 Session 的请求返回给当前会话。 |
| `POST` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/permissions/{requestId}/reply` | 回复 permission，body 支持 `{ "decision": "once|always|reject" }`。 |
| `GET` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/questions` | 读取当前 Session 的 pending question；OpenCode 原生列表是进程级结果，后端按绑定的 remote session 过滤，不能把其它 Session 的请求返回给当前会话。 |
| `POST` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/questions/{requestId}/reply` | 回复 question，body 为 `{ "answers": [[...], ...] }`；`answers` 为 `List<List<String>>`，外层按子问题顺序排列，内层是该问题的选中 label，一次回复覆盖同一请求下的全部子问题。平台也兼容扁平 `string[]`，按单问题整体包成单个内层数组。远端接受成功后平台立即补记既有 `question.replied` RunEvent，确保待回答状态与原 question 工具卡收敛；模型真实生成的后续 assistant 回复仍保留在消息时间线。 |
| `POST` | `/api/internal/platform/opencode-runtime/sessions/{sessionId}/questions/{requestId}/reject` | 拒绝 question。 |
| `GET` | `/api/internal/platform/opencode-runtime/sessions/runtime-state` | 查询当前登录用户历史会话运行态摘要，用于历史入口运行计数和 ask 提醒。 |
| `GET` | `/api/internal/platform/opencode-runtime/sessions/runtime-state/events` | fetch SSE 订阅当前登录用户历史会话运行态摘要变更，首帧 snapshot，后续 updated。 |

旧 `/api/sessions/{sessionId}/...` 运行态入口已作废，统一返回 `410 API_GONE`。agent path 使用 `/api/internal/agent/{agentId}/session/{sessionId}/...`；当前 `opencode` 的 children、todo、diff、abort、fork、side-question、side-question/runs、compact、revert、unrevert、command、shell 路径保持 `/api/internal/agent/opencode/session/{sessionId}/...`。permission/question 的 agent path 入口使用 `/api/internal/agent/{agentId}/permission|question`，并通过 query `sessionId` 定位平台 session。

工作台选择历史会话时，先用 `GET .../messages?refresh=false` 的平台数据库快照渲染正文；permission/question、Todo 和 session-tree 快照并行作为后台增强，不阻塞“正在加载消息列表”首屏。完整历史投影结束前前端仍保持独立发送锁。该编排不改变上述接口的请求、响应、错误或兼容性语义。

用户级会话运行态摘要只面向已登录用户，统计范围与当前用户可见历史会话一致：会话创建人、Run 触发人或消息发送人为当前用户，且会话仍为 ACTIVE；内部 `SIDE_QUESTION` Session 即使异常保留为 ACTIVE 也必须排除。`runningCount` 只统计 `PENDING/RUNNING/CANCELLING` Run；同一会话只返回最近一个非终态 Run。`questionCount` 只统计最新 question 状态仍为 `question.asked` 的运行中会话；收到 `question.replied`、`question.rejected` 或 Run 终态后，该会话会从待关注集合移除。

`GET /api/internal/platform/opencode-runtime/sessions/runtime-state` 响应仍包裹 `ApiResponse<T>`，`data` 结构为：

```json
{
  "runningCount": 2,
  "questionCount": 1,
  "sessions": [
    {
      "sessionId": "ses_...",
      "runId": "run_...",
      "runStatus": "RUNNING",
      "attention": "QUESTION",
      "attentionEventId": "evt_...",
      "attentionAt": "2026-07-08T14:00:00Z",
      "updatedAt": "2026-07-08T14:00:01Z"
    }
  ],
  "generatedAt": "2026-07-08T14:00:02Z"
}
```

`attention` 目前仅支持 `"QUESTION"` 或 `null`。前端不得把 `attentionEventId` 当作通用 RunEvent 续传游标，只用于去重/展示待答提醒。

`GET /api/internal/platform/opencode-runtime/sessions/runtime-state/events` 返回 `text/event-stream`，使用 fetch SSE 以携带 `Authorization: Bearer ...`。SSE data 为上面的摘要 DTO 本体，不再额外包裹 `ApiResponse`；事件名首帧为 `session-runtime.snapshot`，后续变更为 `session-runtime.updated`。服务端在 `run.created/run.started/run.cancelling/run.succeeded/run.failed/run.cancelled/question.asked/question.replied/question.rejected` 后刷新摘要，并保留低频触发器兜底；用户已有 Redis 运行态 marker 时，首帧、事件触发和低频触发都只读取 Redis active 索引/manifest，不查询 PostgreSQL。新前端把该 SSE 作为恢复主入口，不再并行调用 runtime-state HTTP；断线按 1、2、5、10、30 秒退避重连，30 秒为后续重试上限。只有流不可用时，当前 Session 才允许执行一次 `active-run` HTTP fallback；重连成功并收到新摘要后再恢复下一故障窗口的 fallback 资格。该用户级通道不替代单个 Run 的 RunEvent SSE。

兼容和安全约束：

- 所有响应仍包裹 `ApiResponse<T>`，错误仍走统一错误码和 traceId。
- `workspaceId` 为平台 workspace id，后端只把 workspace root 映射为 opencode `directory`；不得把平台 id 当作 opencode `workspace` query。
- `sessionId` 为平台 session id。无用户主体时，后端通过 `agent_session_bindings` 中的 `(sessionId, agentId)` 定位远端 session；`opencode` 会兼容读取旧 `sessions.opencode_*` 字段并回填 binding，未绑定远端 session 时返回 `CONFLICT`。有用户主体且 agent 为 `opencode` 时，缺失或不匹配的绑定会自动在当前用户进程上重建。
- `permission`/`question` 的平台路径保留在 `/api/internal/platform/opencode-runtime/sessions/{sessionId}/...` 下，后端实际映射到 opencode `/permission`、`/question` 族 API。
- config/provider auth/worktree/share/MCP auth 均为受控代理能力，前端不得改为直接调用 opencode 原 URL；provider secret 不得写入 localStorage 或日志。
- 只读 transcript 页面 `/s/{sessionId}` 只消费平台 `GET /api/internal/platform/opencode-runtime/sessions/{sessionId}` 与 `GET /api/internal/platform/opencode-runtime/sessions/{sessionId}/messages?refresh=false`，不接 opencode 公网 `share_data/share_poll`，也不绕过平台鉴权。
- PTY WebSocket 未进入默认 HTTP/SSE 契约；P2 只能按 `docs/standards/security.md` 新增受控 ticket + WebSocket 例外。ticket 只通过新平台 URL `/api/internal/platform/opencode-runtime/sessions/{sessionId}/terminal/tickets` 创建，响应中的 `webSocketUrl` 固定为签发 ticket 的当前 Java 绝对 WebSocket URL。

对应测试：

- `OpencodeRuntimeFacadeTest`：验证 facade runtime 调用不泄漏 generated DTO。
- `OpencodeRuntimeApplicationServiceTest`：验证 workspace directory、用户进程节点路由、固定节点 fallback、远端 session id、binding mismatch 自动重建、permission reply body、MCP resources/tools、config/provider OAuth/worktree/share/MCP auth 映射。

交互请求若因 OpenCode 重启而不再存在，question/permission reply 会返回 `CONFLICT`（`reason=REMOTE_INTERACTION_EXPIRED`），前端清理旧弹框并提示重新发起；仍在 OpenCode pending 列表中的 request 继续按原接口回复。
- `PlatformOpencodeRuntimeControllerTest`：验证平台路径统一响应、MCP tools 查询、session share、traceId 和可选用户主体透传。
- `AgentOpencodeRuntimeControllerTest`：验证 `/api/internal/agent/opencode/...` agent path 统一响应、agentId 选择、traceId 和可选用户主体透传。
- `RuntimeControllerTest`：验证 `/api/internal/agent/opencode/runs` 与内部平台 Run URL 的 DTO、错误格式和 service 实现；`LegacyApiGoneWebFilterTest` 覆盖旧 Run URL 返回 `410 API_GONE`。

- 首次 Run 或签发上下文时尚无可复用 binding：先确认当前用户已有 `READY` opencode 进程，再通过该进程投影出的 execution node 创建远端 session，保存 `agent_session_bindings`；`opencode` 兼容字段 `sessions.opencode_session_id/opencode_execution_node_id` 暂时同步写入。
- 已有远端 session 且携带有效上下文：先以签发时的完整进程快照调用公共 `querySnapshot` 动态健康探测，再直接复用 Session、Workspace、ExecutionNode 和 AgentSessionBinding 快照；探测本身不读取进程 Repository，稳定 `RUNNING` 不落库。未携带 token 的兼容路径仍按当前 binding/进程解析；binding 节点不一致时重新创建远端 session 并覆盖绑定，用户进程不可用、节点不存在、离线或容量不可用时返回 `OPENCODE_UNAVAILABLE`。
- 本地集成默认只向 opencode 传 `directory=workspace.rootPath`，不把平台 `wrk_...` 作为 opencode `workspace` query 传入。

成功后写入 `run.created` 和 `run.started`。未找到可用节点返回 `OPENCODE_UNAVAILABLE`；opencode 超时或异常分别映射为平台 opencode 错误码。

`RunResponse`：`runId`、`sessionId`、`workspaceId`、`status`、`createdAt`、`updatedAt`，以及可选 `tokens`、`costUsd`、`storageMode`、`clientRequestId`、`detailsAvailableUntil`。`storageMode` 为创建时固定的 `LEGACY_FULL` 或 `REDIS_SUMMARY`，活动 Run 不允许中途切换；`clientRequestId` 用于同一次发送的幂等关联；`detailsAvailableUntil` 表示 Redis 完整详情最晚可用时间。三个新字段对旧 Run、旧后端或尚未接入新锚点的兼容响应均可为 `null`/缺失；`tokens` 字段结构同 `SessionMessageResponse.tokens`。

`REDIS_SUMMARY` 的 `run.created` 事件还会携带 `assistantSummaryMessageId`，格式为稳定的 `msg_` + 32 位十六进制；终态 ASSISTANT 摘要复用同一 ID。反馈目标已经统一为 `runId`，该消息 ID 只保留摘要定位和旧消息反馈接口兼容用途。

`GET /api/internal/platform/opencode-runtime/sessions/{sessionId}/active-run` 返回最近的 `PENDING`、`RUNNING` 或 `CANCELLING` Run，供 runtime-state 流不可用时做一次恢复 fallback；历史会话切换后若摘要标记为运行中，前端也会在正文展示后后台调用一次该接口校准终态，不阻塞历史首屏。查询 legacy active Run 时会读取远端最新 assistant 消息，只有本 Run 创建之后且明确 `finish=stop` 才补写 `RUN_SUCCEEDED`。用户已有 Redis 运行态 marker 时只读取 `active:session` 索引并回读 manifest 校验用户/Session/状态，即使索引为空也不回查 PostgreSQL；legacy 用户继续使用最近非终态 Run 查询。没有非终态 Run 时响应仍为 `success=true` 且 `data=null`。

Java 重启后恢复调度器会立即扫描当前服务器的 `LEGACY_FULL` active Run，并低频重试远端终态补偿；OpenCode 子进程尚未恢复或远端不可达时只保守保留 `RUNNING`，不会把未确认的慢模型误判为成功，也不会重新发送 prompt。进程恢复后，后台扫描或历史 `active-run` 查询会再次收敛已完成 Run。

所有带 `{runId}` 的详情、取消、Diff、RunEvent SSE 和 Run 级 session-tree 接口都要求当前认证用户拥有该 Run，并在读取详情或执行副作用前完成校验。`REDIS_SUMMARY` manifest 存在时只比较 manifest `userId`，鉴权阶段为 0 次 PostgreSQL；legacy 或 manifest 已过期时才回查 Run 与 Session 归属。归属缺失、不一致或属于其他用户统一返回 `403 FORBIDDEN`，跨 Java 转发到目标节点后仍执行同一校验。

`POST /api/internal/agent/{agentId}/runs/{runId}/cancel` 或 `/api/internal/platform/opencode-runtime/runs/{runId}/cancel` 对终态 Run 返回 `CONFLICT`。入口 Java 先按 Redis manifest 的 `producerLinuxServerId` 定位生产 Java，manifest 缺失的 legacy/旧 Run 才兼容读取固定 routing decision 和生产 opencode 进程；目标不是当前 Java 时通过统一 `BackendHttpForwarder` 透传 Authorization、traceId、query/body。cancel 属于写操作，因此每一跳都重新执行 strict owner 解析，绝不把客户端可控的 `X-Test-Agent-Backend-Routed` 当成可信放行凭据；到达当前被选中的生产 Java 后解析器返回本机，避免循环。归属缺失、节点无法映射、目标后端不可用或转发失败时返回统一 `OPENCODE_UNAVAILABLE`，不得降级到入口 Java 执行。非终态 Run 只在生产 Java 上通过当前 `AgentRuntime.cancel` 取消远端执行，并追加 `run.cancelling`、`run.cancelled`；取消完成后也会触发一次消息快照持久化。

`GET /api/internal/agent/{agentId}/runs/{runId}/events` 和 `/api/internal/platform/opencode-runtime/runs/{runId}/events` 返回 `text/event-stream`；旧 `GET /api/runs/{runId}/events` 返回 `410 API_GONE`。`event` 使用稳定 wire name。durable RunEvent 使用 `seq` 作为 SSE `id`，可通过 `Last-Event-ID` 续传；transient live output（含 `run.snapshot.reset`）不设置 SSE `id`，payload `seq=0`，不参与续传。浏览器原生 `EventSource` 首次续传可使用 `?lastEventId={seq}`，后端 header 优先、query 兜底。

RunEvent SSE 按 Run 原始生产 Java 路由，不按当前用户最新 binding 路由。任意 Java 收到 `/runs/{runId}/events` 后，优先从 Redis manifest 读取 `producerLinuxServerId`；manifest 缺失的 legacy/旧 Run 才使用 `routing_decisions -> executionNodeId -> opencode process -> linuxServerId`。如果目标不是当前 Java，则流式转发到目标 Java 并保留 `Authorization`、`X-Trace-Id`、`Last-Event-ID`、query 和 `text/event-stream`。

目标 Java 按 manifest 的 `storageMode` 固定分流：`LEGACY_FULL` 继续执行消息 snapshot、DB durable polling replay 和本机 live bus；`REDIS_SUMMARY` 首帧总发送完整 Redis 物化 `run.snapshot.reset`，再以 `snapshot.runtimeVersion` 为起点，由最短 5 秒的 Redis 安全扫描和本机 live bus 只唤醒、分页读取 `${runtimeVersion}-0` 的 durable/transient 尾流，live 事件仍即时唤醒但帧本身不直接输出，活跃 SSE 连接不轮询 PostgreSQL。初始 reset 的 reason 为 `TRANSIENT_SNAPSHOT_RECOVERY`，旧 durable 游标需重置时为 `CURSOR_BEFORE_EARLIEST_OR_DETAILS_TRUNCATED`，连接期间容量换代为 `RUNTIME_STREAM_TRUNCATED`。payload 包含 `reason/resetGeneration/earliestSeq/detailsAvailableUntil/snapshot.barrierSeq/snapshot.runtimeVersion/snapshot.events`；前端先清空该 Run reducer 并按顺序应用 snapshot，再只用随后 durable SSE id 推进 `Last-Event-ID`。Redis manifest/详情缺失返回 `410 RUN_DETAILS_EXPIRED`，Redis 不可用返回 `503 RUNTIME_STATE_UNAVAILABLE`，不得回退 PostgreSQL 原始事件。

`LEGACY_FULL` SSE 建连时，后端会从当前 Run 绑定的 agent remote session 最新消息页沿 `before` cursor 向前查找稳定 USER dispatch 锚点，并只把 `parentID/parentId` 直接指向它的 assistant 转换为 transient `message.updated` / `message.part.updated` 发给前端；因此同一 Session 旧轮的 `todowrite` part 不会被包装成当前 `runId`。user 消息已在 Run 启动前由平台保存，不重复回放其 text part。单页 100、最多 20 页；明确锚点尚未到达、平台 USER/root scope 锚点冲突、重复 cursor、超限或兼容时间窗歧义时返回空消息投影，不回退“最后一条 user”。已记录的 child scope 仍可独立恢复，但只有选中的 root 消息能发现新 child。随后进入 `run_events` durable replay 与 live bus 合流。高频文本 delta、大段日志和 bash/tool output 不写入 `run_events`；如果远端 session 不可用或拉取失败，后端跳过消息恢复，不阻断 Run 状态、Diff、permission/question 等 durable RunEvent 回放。`REDIS_SUMMARY` 不触发该兼容远端 snapshot，而是完全使用 Redis 物化 snapshot 与 `runtime-events` Stream 恢复当前 Run 详情。

`GET /api/internal/agent/{agentId}/runs/{runId}/session-tree/messages` 和 `/api/internal/platform/opencode-runtime/runs/{runId}/session-tree/messages` 返回当前 Run scope 的消息树快照；旧 `/api/runs/{runId}/session-tree/messages` 返回 `410 API_GONE`。读取顺序固定为 Redis 24 小时物化详情 → OpenCode 当前用户轮次 → PostgreSQL 终态双摘要：Redis 命中时不查询 Run、Session、binding、scope 或 `run_events`；Redis 缺失或不可用时才读取 OpenCode，并按同一 dispatch user 规则做因果裁剪，绝不返回 root Session 的其它轮次；OpenCode 也不可用时把最多两条摘要映射成既有 `message.updated` / `message.part.updated` reducer 事件，事件同时带 `contentKind=SUMMARY`、`summaryStatus` 和 `summaryVersion`。只有命中 legacy OpenCode 来源时才补读旧 durable RunEvent；`messagesBySessionId` 仍只包含 `message.*` payload，不混入状态事件。响应 `data`：

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
  ],
  "historyRepresentation": "FULL",
  "replayAvailable": true,
  "detailsAvailableUntil": "2026-07-12T00:00:00Z"
}
```

三个历史元数据字段与 Session 级接口语义一致，均保持可选以兼容旧客户端；Redis 来源的 `detailsAvailableUntil` 取 manifest 到期时间，OpenCode 完整来源可为 `null`，PostgreSQL 摘要来源固定返回 `historyRepresentation=SUMMARY`、`replayAvailable=false`。该接口是 HTTP snapshot 辅助入口，不替代 RunEvent SSE。它只返回因果裁剪后的当前 Run scope 子树；root session 下全量多轮历史及全部历史 child 使用 `GET /api/internal/agent/{agentId}/sessions/{sessionId}/session-tree/messages` 查询。Session 级命中 legacy OpenCode 来源时仍按消息 snapshot 中的远端 `rootSessionId` 补读 `run_events.root_session_id` 下的 durable 状态事件；Redis 和摘要来源禁止回查旧事件表。

PTY WebSocket 不在上述默认 HTTP/SSE 契约内，已按 `docs/standards/security.md` 增加后端受控例外入口，前端仍不得直连 opencode server、SSH、sidecar 或任意主机。

- `POST /api/internal/platform/opencode-runtime/sessions/{sessionId}/terminal/tickets`：创建一次性 PTY ticket，仍返回 `ApiResponse<T>`。
- `GET /api/internal/platform/opencode-runtime/sessions/{sessionId}/terminal/ws?ticket=...`：仅用于 WebSocket upgrade，ticket 单次使用并短期过期。
- 旧 `POST /api/sessions/{sessionId}/terminal/tickets` 和 `GET /api/sessions/{sessionId}/terminal/ws?ticket=...` 已作废，返回 `410 API_GONE`。

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
  "webSocketUrl": "ws://122.233.30.114:8080/api/internal/platform/opencode-runtime/sessions/ses_.../terminal/ws?ticket=tty_..."
}
```

`webSocketUrl` 固定返回签发 ticket 的当前 Java 绝对地址。多后台时 ticket 请求可先经入口 Java 转发到用户进程所属 Java，响应仍指向实际签发节点，后续 upgrade 不再由 Nginx 二次负载；浏览器必须能访问该 Java 的 `listenUrl`，后端 Origin 白名单仍校验前端 origin。

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

`GET /api/internal/agent/{agentId}/runs/{runId}/diff` 或 `/api/internal/platform/opencode-runtime/runs/{runId}/diff` 返回当前 Run 的 Diff；旧 `GET /api/runs/{runId}/diff` 返回 `410 API_GONE`：

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

1. `REDIS_SUMMARY` 优先使用 Redis 物化 snapshot 中最新 `diff.proposed` 的 `diff/files`；命中时不查询 PostgreSQL `run_events` 或 Run 锚点。legacy 继续读取该 Run 最新 `diff.proposed` 事件。
2. 若 snapshot/legacy 事件中没有 Diff，则通过当前 `AgentRuntime.diff` 查询；新模式只在远端 message/part 缺失时读取 `runs` 非原文定位字段，legacy 继续使用 Session agent binding。
3. 新模式 Redis manifest 已过期但 PostgreSQL 锚点仍存在时返回 `410 RUN_DETAILS_EXPIRED`，禁止回退 legacy 事件表；legacy 没有可用映射时仍返回空文件列表。

`POST /api/internal/agent/{agentId}/runs/{runId}/diff/accept` 或 `/api/internal/platform/opencode-runtime/runs/{runId}/diff/accept` 不修改文件系统；语义为“保留当前工作区变更并追加平台事件”。响应：

```json
{
  "runId": "run_...",
  "action": "accept",
  "status": "accepted",
  "fileCount": 2
}
```

后端会追加 `diff.accepted` RunEvent，payload 至少包含 `action`、`status`、`fileCount`。`REDIS_SUMMARY` 只追加 Redis 事件，并在成功后以单条 SQL 增加 `runs.diff_accepted_count`；不写 `run_events`。

`POST /api/internal/agent/{agentId}/runs/{runId}/diff/reject` 或 `/api/internal/platform/opencode-runtime/runs/{runId}/diff/reject` 语义为“拒绝本次 Run 对应消息产生的变更”。旧 `POST /api/runs/{runId}/diff/reject` 返回 `410 API_GONE`。后端会从 Redis snapshot/legacy RunEvent payload 中查找最近的远端 `messageID`，新模式必要时使用 Run 锚点中的 `last_remote_message_id/last_remote_part_id`，再通过当前 `AgentRuntime.rejectDiff` 执行回滚；`opencode` 实现适配到 opencode `sessionRevert`。成功后追加 `diff.rejected`；新模式再以单条 SQL 增加 `runs.diff_rejected_count`，不写 `run_events`。

拒绝失败规则：

- legacy 缺少 `messageID` 返回 `CONFLICT`；新模式的 Redis 与 Run 锚点都无法提供定位 ID 时返回 `RUN_DETAILS_EXPIRED`。
- Session 未绑定远端 agent session 返回 `CONFLICT`。
- opencode 超时、不可用或异常仍映射为 `OPENCODE_TIMEOUT`、`OPENCODE_UNAVAILABLE` 或 `OPENCODE_BAD_GATEWAY`。

兼容性：

- Diff 文件对象可新增字段，前端必须忽略未知字段。
- 当前不支持 per-file 后端回滚；前端“当前文件接受/拒绝”只能作为当前选择和反馈，不承诺后端按文件应用。
- legacy 接受/拒绝继续通过 append-only RunEvent 记录；`REDIS_SUMMARY` 只在 Redis 保存动作详情，PostgreSQL 仅更新聚合计数。

### 健康检查

Actuator health 由 Spring Boot Actuator 提供，数据库健康使用 Spring Boot/Druid 数据源；固定 opencode node yml 配置已作废，不再作为 Actuator health 来源；Redis 是系统必需依赖，健康检查会做 TCP 连通探测。

### 兼容性

- API 不暴露数据库 surrogate PK。
- API 不暴露 `agent_session_bindings`、`opencodeSessionId`、`opencodeExecutionNodeId` 或 generated SDK DTO；前端只依赖平台 Workspace、Session、Run、Cancel 和 RunEvent SSE。
- 旧 runtime/workspace `/api/...` URL、旧 terminal WebSocket、旧 manager-backends 诊断入口和旧 backend-process metrics 入口已强制作废，统一返回 `410 API_GONE`；`/api/auth/login|logout|me|refresh` 暂为稳定认证入口。
- 响应 DTO 可以新增字段，前端必须忽略未知字段。
- 文件 API 初版不承诺 Git 状态、二进制预览、递归扫描和搜索。
- RunEvent payload 可以新增字段；事件 wire name 不可重命名。
