# 事件流 API 文档

本文档是 SSE 和平台事件流的稳定入口。新增或修改事件类型必须更新本文件。

## 文档模板

每个事件类型必须记录：

- event name。
- event id 规则。
- 字段说明。
- 是否可重复投递。
- 兼容性说明。
- 前端展示或处理方式。
- 对应测试。

## 通用规则

1. RunEvent 只追加，不更新。
2. durable RunEvent 写入 `run_events`，每个 runId 内 seq 单调递增。
3. transient live output 不写入 `run_events`，payload `seq=0`，SSE 不设置 `id`。
4. durable SSE event id 使用 seq；transient event 不参与 `Last-Event-ID` 恢复。
5. 前端断线后通过 Last-Event-ID 续传 durable RunEvent；消息内容恢复只从 opencode session projected messages 获取。
6. 不把 opencode raw event 原样透传给前端，也不把大段日志、bash/tool output 或高频文本 delta 作为平台持久化事件保存。
7. generated SDK 事件必须在 `test-agent-event` 或 `test-agent-opencode-client` 映射为平台事件。

## RunEvent 基础字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `eventId` | string | 平台事件 ID，使用 `evt_` 前缀。 |
| `runId` | string | 所属 Run ID，使用 `run_` 前缀。 |
| `seq` | number | durable 事件为同一 runId 内从 1 开始单调递增；transient 事件固定为 0，不参与恢复。 |
| `type` | string | 平台事件类型 wire name，例如 `run.started`、`assistant.message.delta`。 |
| `traceId` | string | 产生该事件的请求或异步链路 traceId。 |
| `occurredAt` | string | ISO-8601 时间。 |
| `payload` | object | 事件业务载荷，前端必须允许新增字段。 |

稳定事件类型使用 wire name，Java enum 只作为后端内部表达：

| wire name | 说明 |
|---|---|
| `run.created` | Run 已创建。 |
| `run.started` | Run 已开始执行。 |
| `run.cancelling` | Run 正在取消。 |
| `run.succeeded` | Run 成功结束。 |
| `run.failed` | Run 失败结束。 |
| `run.cancelled` | Run 已取消。 |
| `assistant.message.delta` | 助手消息增量，只实时发送，不写入 `run_events`。 |
| `message.updated` | opencode Web App message projection 更新；实时发送，断线后由 opencode session messages snapshot 恢复。 |
| `message.removed` | opencode Web App message projection 移除。 |
| `message.part.updated` | message part 内容或状态更新；实时发送，断线后由 opencode session messages snapshot 恢复。 |
| `message.part.removed` | message part 移除。 |
| `message.part.delta` | message part 流式增量，只实时发送，不写入 `run_events`。 |
| `session.diff` | session 级 Diff 状态更新。 |
| `session.status` | session busy/idle/status 更新。 |
| `todo.updated` | Todo 列表更新。 |
| `tool.started` | 工具调用开始。 |
| `tool.finished` | 工具调用结束，payload 使用 `status` 区分 success/failed。 |
| `diff.proposed` | 智能体提出文件 Diff；Run 运行中也会从完成的写文件工具派生实时 Diff。 |
| `diff.accepted` | 用户已接受 Run 级 Diff。 |
| `diff.rejected` | 用户已拒绝 Run 级 Diff，后端已提交 opencode revert。 |
| `test.finished` | 测试执行结束。 |
| `permission.asked` | 权限请求。 |
| `permission.replied` | 权限回复。 |
| `question.asked` | 提问请求。 |
| `question.replied` | 提问回复。 |
| `question.rejected` | 提问拒绝。 |
| `vcs.branch.updated` | VCS 分支更新。 |
| `lsp.updated` | LSP 状态更新。 |
| `mcp.tools.changed` | MCP 工具目录更新。 |
| `opencode.event.unknown` | 未识别 opencode raw event 的兼容兜底。 |

## SSE 续传

- SSE `event` 使用 RunEvent 的 `type`。
- Phase 02/03 后，SSE `event` 使用 `RunEventType.wireName()`，例如 `tool.finished`。
- durable SSE `id` 使用 RunEvent 的 `seq` 字符串。
- transient SSE 不设置 `id`，避免浏览器把非持久化事件作为 `Last-Event-ID`。
- 客户端断线后携带 `Last-Event-ID`，后端按当前 runId 返回 `seq > Last-Event-ID` 的 durable 事件。
- 浏览器原生 `EventSource` 不能设置自定义请求头；前端首次续传优先使用 `GET /api/internal/agent/{agentId}/runs/{runId}/events?lastEventId={seq}`，默认 `agentId=opencode`。旧兼容入口 `GET /api/runs/{runId}/events?lastEventId={seq}` 和 `GET /api/internal/platform/opencode-runtime/runs/{runId}/events?lastEventId={seq}` 继续有效。后端 header 优先，query 参数作为浏览器兼容入口。
- 如果 `Last-Event-ID` 缺失，默认从当前订阅策略允许的起点开始返回。
- 如果 `Last-Event-ID` 非数字或小于 0，后端返回统一错误格式，错误码为 `VALIDATION_ERROR`。
- 消息内容、文本增量和日志/tool output 不从本地 `run_events` 恢复；SSE 建连时后端会先通过当前 `AgentRuntime.messages` 拉取 projected messages，并转换为 transient `message.updated` / `message.part.updated` snapshot 事件。当前 `opencode` 实现适配 opencode `GET /api/session/{sessionID}/message`。前端刷新恢复时先用 `GET /api/sessions/{sessionId}/messages` 加载数据库/远端快照，再用 `GET /api/sessions/{sessionId}/active-run` 判断是否重新订阅本 Run SSE。

## Phase 04 Runtime SSE

`GET /api/internal/agent/{agentId}/runs/{runId}/events` 是 agent-scoped RunEvent 实时入口，前端默认使用 `agentId=opencode`。`GET /api/runs/{runId}/events` 和 `GET /api/internal/platform/opencode-runtime/runs/{runId}/events` 是旧兼容入口，默认按 `opencode` 处理。三者返回 `text/event-stream`，共享同一续传、traceId、错误格式和事件模型，payload 格式不随 agentId 改变。

应用配置管理和个人 SSH key 管理不产生 RunEvent，也不新增 SSE 事件类型。`/api/internal/platform/configuration-management/**` 的设置页创建应用工作空间接口虽然会触发初始版本工作区 clone/checkout 和运行态 Workspace 创建，但进度写入 `workspace_create_operations` 并由 `GET /api/internal/platform/configuration-management/workspace-create-operations/{operationId}` HTTP 轮询读取；不通过 RunEvent SSE 发布“校验、保存配置、解析版本、下载代码、创建运行态工作区、完成/失败”等步骤。

应用版本工作区和个人工作区管理接口也不产生 RunEvent/SSE。`/api/internal/platform/workspace-management/applications/**`、`/workspace-versions/**`、`/personal-workspaces/**` 会执行 Git clone/worktree/diff/push 并创建或切换运行态 `Workspace` 配置，但不会启动 Session/Run；后续 opencode 对话仍只通过 Run API 产生 RunEvent。多服务器下应用版本工作区同步使用后端内部服务器广播，不暴露给浏览器 SSE。

Agent 配置管理接口不产生 RunEvent/SSE。`/api/internal/platform/workspace-management/agent-config/**` 的公共级/工作空间级 Git 更新、worktree、commit、publish 进度通过 ticket 保护的 WebSocket `/operations/{operationId}/ws?ticket=...` 推送 `snapshot`、`step`、`completed`、`failed`，也可通过 `GET /operations/{operationId}` 查询快照；公共级 worktree 切换只改变后续文件 WebSocket route/ticket 的 `worktreeId/linuxServerId` 绑定，不产生 RunEvent。该进度不写入 `run_events`，不参与 RunEvent `Last-Event-ID` 续传。

opencode-manager 兼容诊断 API 和 `/api/internal/platform/opencode-runtime/manager/ws` 控制面 WebSocket 不产生 RunEvent/SSE，不向前端广播注册、Redis 心跳、后端列表发现或命令结果。manager 只连接本服务器 Java；`backendListRequest/backendListResponse` 仅作为兼容协议保留，Java 收到 `backendListRequest` 会忽略。控制面除 `command`/`commandResult` 等帧外，还包含 manager→后端的 `configRequest` 和后端→manager 的 `configUpdate` 控制帧：manager 注册成功或重连后主动发送一次 `configRequest`，后端经完整 `configUpdate` 下发 `common_parameters` 中的 `OPENCODE_MANAGER_MAX_PROCESSES`、`OPENCODE_SESSION_DIR`、`OPENCODE_PUBLIC_CONFIG_DIR`；后续前端只允许修改最大进程数，后端收到跨 Java 参数刷新广播后只向本服务器 managers 下发 max-only `configUpdate`。该帧不进入 RunEvent 流，不向前端推送。

超级管理员运行管理页调用的 `GET /api/internal/platform/opencode-runtime/management/overview` 读取 Redis 中仍在线的 Java/manager 运行快照和 manager 管理的本地 opencode server 明细；“容器 / 管理进程”合并表、展开后的“有主进程 / 无主进程”分组、启动时间、端口、PID、baseUrl、启动命令和归属字段都来自该 HTTP overview。底部用户 opencode server 列表改为通过 `GET /api/internal/platform/opencode-runtime/management/user-processes?keyword=...` 按用户查询，只在进程所属服务器就是当前 Java 时调用本机 manager health；远端进程返回 `REMOTE_SERVER/CHECK_SKIPPED`，避免随机 Java 控制其他服务器 manager。容器行内“趋势”按钮调用容器 metrics history HTTP API，后端 Java 行按 `linuxServerId` 调用 `GET /api/internal/platform/opencode-runtime/management/linux-servers/{linuxServerId}/backend-metrics` 读取 Redis 48 小时指标历史；旧 `backendProcessId` history API 仅保留兼容入口。有主/无主进程明细和底部用户进程列表中的“重启/停止”或“重启”按钮调用运行管理 HTTP POST 命令端点，入口后端会通过统一 Java 路由解析器把请求路由到容器所属服务器 Java，再由目标 Java 控制本服务器 manager；已有平台用户进程记录的端口必须通过公共启动服务完成 restart/start 后 manager health 和 opencode HTTP health 确认才同步返回成功。该结果不写入 RunEvent，也不新增 SSE 事件类型。运行管理不向 RunEvent 流发布拓扑、连接、进程状态、启动命令、归属状态、控制命令结果或监控指标变化。

超级管理员定时任务管理页调用的 `/api/internal/platform/scheduler-management/**` 只维护 scheduler 任务定义和运行记录，不新增 SSE 事件类型，也不向 RunEvent 流发布任务状态变化；页面刷新通过 HTTP 查询完成。

AI 回复满意度反馈接口 `/api/internal/platform/opencode-runtime/messages/{messageId}/feedback` 只写入 `ai_message_feedbacks` 事实表，不产生 RunEvent，不通过 SSE 推送反馈状态；当前用户刷新或重新进入会话时通过 `GET .../feedback/me` 查询自己的反馈。运营分析页 `/api/internal/platform/analytics/**` 只读取 hourly/daily rollup、水位和明细查询接口，不订阅 RunEvent，也不新增 SSE 事件类型。反馈、Diff、Run 状态和 token 等运营指标由后台 rollup runner 定期从事实表聚合，主链路不在 RunEvent 里补发统计事件。

## Internal Server Broadcast

内部服务器广播不是浏览器事件流。它用于一台后端把跨服务器业务事件 fan-out 到其他后端实例，当前稳定事件为应用版本工作区副本同步和公共 Agent 配置同步。

传输：

- 默认空实现；多服务器部署通过 `test-agent.server-broadcast.enabled=true` 开启 Redis pub/sub。
- 默认 channel：`test-agent:server-broadcast`，可通过 `test-agent.server-broadcast.channel` 覆盖。
- Redis 广播是实时增强通道，不保证持久投递；应用版本工作区通过数据库 `target_commit_hash` 和本机补偿扫描恢复漏消息。

事件 envelope：

```json
{
  "eventId": "sbe_...",
  "type": "workspace.version.sync-requested",
  "originInstanceId": "10.8.0.11-...",
  "originLinuxServerId": "10.8.0.11",
  "traceId": "trace_...",
  "occurredAt": "2026-06-26T00:00:00Z",
  "payload": {
    "reason": "CREATED",
    "versionId": "awv_...",
    "applicationWorkspaceId": "awp_...",
    "appId": "app_...",
    "repositoryId": "repo_...",
    "version": "20260707",
    "branch": "feature_testagent_20260707",
    "userId": "usr_...",
    "targetCommitHash": "abc123..."
  }
}
```

`workspace.version.sync-requested` 的 `reason` 当前包括 `CREATED`、`EXISTING_VERSION`、`SYNC_TO_APPLICATION`、`GIT_PULL_REQUESTED`、`GIT_PULLED`。payload 不允许携带 SSH 私钥、token、Authorization、Cookie 或文件内容；远端节点使用 `userId` 在本机业务服务内读取该用户已加密保存的 SSH key，并在当前服务器上 clone/fetch/reset 到目标 commit。消费者必须跳过 `originLinuxServerId` 与本机相同的事件，避免本机重复执行。

`common-parameter.refresh-requested` 用于通用参数 `value` 修改后的跨实例联动。某实例 `PATCH` 修改参数后，本地广播器发布该广播并发布本地 `CommonParameterReloadedEvent`；其他实例收到后发布本地 `CommonParameterReloadedEvent`，监听方直接从数据库读取最新参数并向本实例持有的 opencode manager 下发最新运行配置。远端处理不再转发广播，避免循环；消费者跳过 `originInstanceId` 与本机相同的事件。payload 只携带参数标识，不携带参数值（各实例自行从库读取，避免值在总线明文）：

```json
{
  "englishName": "OPENCODE_MANAGER_MAX_PROCESSES",
  "platform": "all",
  "parameterId": "param_opencode_manager_max_processes",
  "traceId": "trace_..."
}
```

`agent-config.public-sync-requested` 用于公共 Agent 配置更新或发布后的多服务器同步，payload 只允许包含：
```json
{
  "branch": "main",
  "commitHash": "abc123...",
  "reason": "publish"
}
```

消费者在本机公共配置 Git 根目录工作树 clean 时 fetch/checkout/reset 到指定 commit；dirty、未配置或非 Git 仓库时跳过，不覆盖本机修改。该广播不暴露给浏览器，也不通过 RunEvent SSE 下发。

## Platform File WebSocket

平台文件 WebSocket 不产生 RunEvent/SSE，属于前端工作区文件和 Agent 配置文件操作的受控双向 RPC 通道。工作区文件先调用 `POST /api/workspaces/{workspaceId}/file-ws-route` 定位目标后端；Agent 配置文件先调用 `POST /api/internal/platform/workspace-management/agent-config/file-ws-route` 定位目标后端。公共 Agent 直接目录模式必须在 route 请求中提供已初始化服务器 `linuxServerId`，公共 worktree 模式由 `worktreeId` 落库服务器决定目标。随后都在目标后端调用 `POST /api/internal/platform/workspace-management/file-ws/tickets` 创建一次性 ticket，最后连接：

```text
/api/internal/platform/workspace-management/file/ws?ticket=wft_...
```

客户端请求 envelope：

```json
{
  "id": "req_1",
  "op": "workspace.read",
  "params": {
    "workspaceId": "wrk_...",
    "path": "src/App.java"
  }
}
```

成功响应：

```json
{
  "id": "req_1",
  "type": "result",
  "data": {},
  "traceId": "trace_..."
}
```

错误响应：

```json
{
  "id": "req_1",
  "type": "error",
  "code": "FORBIDDEN",
  "message": "路径越权",
  "traceId": "trace_...",
  "details": {}
}
```

当前稳定操作：

| `op` | `params` | 响应 |
|---|---|---|
| `workspace.list` | `workspaceId`, `path?` | `FileTreeEntryResponse[]` |
| `workspace.search` | `workspaceId`, `query` | `FileSearchResultResponse[]`；递归搜索文件名（不区分大小写子串匹配），跳过黑名单目录，结果按文件名排序并限制数量 |
| `workspace.read` | `workspaceId`, `path` | `FileContentResponse` |
| `workspace.write` | `workspaceId`, `path`, `content` | `null` |
| `workspace.status` | `workspaceId`, `path` | `FileStatusResponse` |
| `workspace.delete` | `workspaceId`, `path` | `null`；仅删除普通文件，目录删除返回统一错误 |
| `agent-config.list` | `scope`, `workspaceId?`, `worktreeId?`, `path?` | `FileTreeEntryResponse[]`；用于公共级/工作空间级 Agent 配置文件 |
| `agent-config.read` | `scope`, `workspaceId?`, `worktreeId?`, `path` | `FileContentResponse` |
| `agent-config.write` | `scope`, `workspaceId?`, `worktreeId?`, `path`, `content` | `null`；仅 `SUPER_ADMIN` |
| `directory.list` | `path?` | `WorkspaceDirectoryListResponse`；用于服务器工作空间选择器 |
| `workspace.create` | `name`, `rootPath` | `WorkspaceResponse`；仅 `SUPER_ADMIN` 且目标服务器与当前 agent 同服务器时允许 |

服务端必须按 ticket 绑定的 workspace、服务器和模式校验请求：workspace 操作的 `workspaceId` 必须等于 ticket 绑定值；Agent 配置操作的 `scope/workspaceId/worktreeId` 必须等于 ticket 绑定值，公共直接目录模式还必须受 ticket 绑定的 `linuxServerId` 约束；`directory.list` 仅 `directory-picker` ticket 可用；`workspace.create` 必须由 `SUPER_ADMIN` 创建，并且工作空间服务器与当前用户 opencode 进程服务器一致。客户端必须按 `id` 匹配响应，允许未知字段，收到错误 envelope 后按统一错误码处理。

示例：

```text
id: 2
event: run.started
data: {"eventId":"evt_...","runId":"run_...","seq":2,"type":"run.started","traceId":"trace_...","occurredAt":"2026-06-19T00:00:00Z","payload":{"status":"RUNNING"}}
```

transient 示例没有 SSE `id`：

```text
event: message.part.delta
data: {"eventId":"evt_live_...","runId":"run_...","seq":0,"type":"message.part.delta","traceId":"trace_...","occurredAt":"2026-06-19T00:00:00Z","payload":{"messageID":"msg_...","partID":"part_...","delta":"hello"}}
```

实现策略：

- SSE 合并三个来源：durable replay 继续按 `run_events` 查询可恢复事件；本机 live bus 即时发送当前进程新产生的 durable 和 transient 事件；Redis run-event bus 开启时接收其他实例发布的 live event。
- durable replay 每次按 `runId + lastSeq` 查询增量事件，默认批量上限 100。
- **durable 事件可能重复投递**：落库的 durable 事件既经 live bus 即时下发，又可能在下一轮 replay 轮询中被查出（live 推送与轮询游标推进存在竞态）。同一 durable 事件携带稳定的 `evt_` 前缀 `eventId`，前端必须按 `eventId` 去重；transient 事件 `eventId` 为 `evt_live_` 前缀且 `seq=0`，同样按 `eventId` 去重。
- `RunEventLiveBus` 基于 Reactor `Sinks`，默认只服务当前进程已连接的 SSE 订阅。设置 `TEST_AGENT_RUN_EVENT_REDIS_BUS_ENABLED=true` 或 `test-agent.run-event.redis-bus.enabled=true` 后，后端会把 durable/transient `RunEventSsePayload` 发布到 Redis channel `test-agent:run-events`，消息包含 `originInstanceId`；本机收到自己发布的消息会忽略，其他实例收到后转发给本机 SSE 客户端。Redis 不可用、未启用或发布/订阅失败时自动降级为本机 live bus + durable replay。
- polling 查询必须 offload 阻塞式 Repository；单次回放查询失败不改变 Run 状态，后端跳过本轮轮询并在下一轮继续尝试，客户端仍按既有 SSE 续传规则处理。
- 客户端断开时释放 Flux 订阅。
- `Last-Event-ID` 解析委托 `RunEventReplayService`；非法值映射为 `VALIDATION_ERROR`。
- SSE body 使用 `RunEventSsePayload`，不返回 generated SDK DTO 或 opencode raw event。
- `message.updated`、`message.part.updated`、`message.part.delta`、`assistant.message.delta` 等消息内容投影事件只进入 live bus；`run.*`、`diff.*`、`permission.*`、`question.*`、`todo.updated` 和关键 tool 状态继续入库。
- `tool.finished` 入库前会移除 `rawPayload`、`output`、`input`、`metadata` 等大字段，只保留 tool/call/message/part/status/title/error 等摘要。
- `message.part.updated` 本身仍是 transient；当其中的 `write`、`edit`、`apply_patch` tool part 进入 `completed` 状态时，后端会额外派生 durable `diff.proposed`。payload 只保留 `source=tool`、`tool`、`messageID/messageId`、`partID/partId` 和 `files[]`，不会持久化原始 `rawPayload`、完整 `input/output` 或 tool `metadata`。
- Skill 调用不新增 `skill.*` wire name；opencode 中的 Skill 仍作为 tool 上报，前端仅在 `tool.started`、`tool.finished`、`message.part.updated`、`message.part.delta` 中根据 `payload.tool`、`payload.toolName` 或 ToolPart `toolName=skill` 分类为 Skill 调用展示。

Phase 08 后，opencode raw event 的终态映射为：

- `session.next.step.ended` -> `run.succeeded`，应用服务同时把 Run 状态更新为 `SUCCEEDED`。
- `session.status` 且 `payload.status.type=idle` -> `run.succeeded`，兼容 opencode 1.17.8 的完成信号。
- `session.idle` -> `run.succeeded`，兼容 opencode 1.17.8 的完成信号。
- `session.next.step.failed` -> `run.failed`，应用服务同时把 Run 状态更新为 `FAILED`。
- `session.error` -> `run.failed`，应用服务同时把 Run 状态更新为 `FAILED`。
- `message.updated`、`message.part.updated`、`message.part.delta` 等 opencode App 事件进入同名 transient SSE，用于 message timeline；不写入 `run_events`。
- `permission.*`、`question.*`、`todo.updated`、`vcs.branch.updated`、`lsp.updated`、`mcp.tools.changed` 进入同名平台 RunEvent，用于运行态同步。

本地 RunEvent 持久化异常不是 opencode 终态事件，不能单独生成 `run.failed` 或把 Run 状态改为 `FAILED`；前端可通过 SSE 重连和后续事件继续恢复视图。

Diff 动作事件：

```text
id: 12
event: diff.accepted
data: {"eventId":"evt_...","runId":"run_...","seq":12,"type":"diff.accepted","traceId":"trace_...","occurredAt":"2026-06-19T00:00:00Z","payload":{"action":"accept","status":"accepted","fileCount":2}}
```

```text
id: 13
event: diff.rejected
data: {"eventId":"evt_...","runId":"run_...","seq":13,"type":"diff.rejected","traceId":"trace_...","occurredAt":"2026-06-19T00:00:00Z","payload":{"action":"reject","status":"rejected","fileCount":2}}
```

前端处理：

- `diff.proposed` 更新 Changed Files 和 DiffActionCard；开启实时追踪时，前端也用运行中的 `diff.proposed.files[].path/additions/deletions/status` 刷新文件树行数并跟随打开最近变化文件。
- `diff.accepted` / `diff.rejected` 展示动作结果并保留 traceId。
- 终态 `run.succeeded` / `run.failed` / `run.cancelled` 必须停止“运行中”状态。

订阅建议：

- 首次订阅不传 `Last-Event-ID`。
- 断线重连传上次成功处理的 SSE `id`。
- 客户端必须优先按 `eventId` 去重；缺失 `eventId` 的旧事件才回退按 `runId + seq` 去重，允许同一事件重复投递。
- 客户端必须忽略未知 payload 字段和未知 event name。

## 兼容性

1. 新增事件字段必须保持旧前端可忽略。
2. 删除或重命名字段属于破坏性变更，必须提供迁移说明。
3. 新事件类型必须有默认忽略策略。
4. opencode raw event 不直接透传；已知事件映射为平台稳定类型，未知事件映射为 `opencode.event.unknown` 并保留安全的 `rawType`、`rawEventId`、`rawPayload`。
5. RunEvent payload 当前以 JSON 文本持久化；后续切换 PostgreSQL JSONB 时必须保持读取兼容。
