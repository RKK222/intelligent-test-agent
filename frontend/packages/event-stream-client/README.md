# @test-agent/event-stream-client

## 工程定位

前端消费平台 RunEvent SSE 和用户级运行态 fetch SSE 的唯一 client。

## 主要职责

- 连接 `/api/internal/agent/{agentId}/runs/{runId}/events`，`agentId` 默认 `opencode`；旧 `/api/runs/{runId}/events` 已作废并返回 `410 API_GONE`。
- 监听并按原样转发平台 RunEvent wire name，包括 `side_question.started/progress/delta` 和既有 `session.updated`；旁路 delta 与其它 transient 事件一样按真实 `eventId` 去重，最终答案由上层以 `run.succeeded.payload.answer` 校准；标题等业务状态由上层应用按会话范围消费 `platformSessionTitleSynchronized/platformSessionTitle` 平台确认字段处理。
- 将 `run.snapshot.reset` 作为已知 transient 事件投递给上层；该事件 `seq=0` 且没有 SSE `id`，client 不从 payload `seq/eventId` 推导或更新 `Last-Event-ID`，snapshot 的清空/重放由上层 reducer 负责。
- 所有事件（包括 transient 文本增量）优先按 `eventId` 去重，兼容缺失 `eventId` 的旧事件时才回退 `runId + seq`；`seq=0` 且缺失 `eventId` 的旧增量不能按固定序号互相去重。
- 订阅只投递与当前 `runId` 完全一致的事件；调用 `close()` 后，即使浏览器还有已排队的旧 listener 回调，也不会继续触发 `onEvent`。
- 使用 `lastEventId` query 参数支持浏览器原生 EventSource 的首次续传，agent URL 下格式不变。
- `baseUrl` 显式为空时按同源相对路径 `/api/...` 建立 RunEvent SSE，兼容企业版由 Nginx 统一代理前端与多后台；非空地址继续支持前后端分离部署。
- 已认证 fetch RunEvent SSE 和用户级运行态 SSE 可传 `linuxServerId`，非空时设置 `X-Test-Agent-Linux-Server-Id` 供 Nginx 首跳；值会修剪且不会写入 URL。空值和原生 EventSource 保持旧行为。
- 提供 `subscribeSessionRuntimeState()`，通过 fetch 订阅 `/api/internal/platform/opencode-runtime/sessions/runtime-state/events`，可携带 Bearer Token，解析 `session-runtime.snapshot` / `session-runtime.updated`、`permissionCount` 和 `sessions[].attention=PERMISSION`；旧摘要缺少计数时从会话 attention 安全推导。断线按 1/2/5/10/30 秒退避重连。是否执行 active-run fallback 及迟到结果 fencing 属于 app 交互状态，不在 client 内写 Vue 状态。
- 可选 `onRawMessage` 在解析 RunEvent 前回调浏览器实际收到的 SSE `MessageEvent.data`、事件名、`lastEventId`、runId 和接收时间；浏览器 `EventSource` 不暴露完整 HTTP 字节流，因此该回调不代表 DevTools 里的 wire bytes。
- 提供关闭订阅和连接状态回调。

已知事件包括旧 `run.*`、`run.snapshot.reset`、`assistant.message.delta`、`tool.*`、`diff.*`，以及 opencode Web App 运行态 `message.*`、`session.updated`、`session.status`、`todo.updated`、`permission.*`、`question.*`、`vcs.branch.updated`、`lsp.updated`、`mcp.tools.changed`。`session.updated` 沿用既有 wire name；只有后端成功持久化平台标题才带 `platformSessionTitleSynchronized/platformSessionTitle`，其余事件照常转发但没有标记，旧消费方可忽略。客户端不新增 API、DTO 或 SDK 映射。transient live output 与 `run.snapshot.reset` 的 payload `seq=0` 且没有 SSE `id`，客户端必须依赖 payload `eventId` 去重，且不能用 reset 更新 durable 游标。

## 禁止事项

- 不直接修改 Vue 状态。
- 不访问 opencode server。
- 不绕过 agent-scoped RunEvent SSE URL 或用户级 runtime-state fetch SSE URL 订阅旧 runtime 入口。
- 不处理业务卡片渲染。

## 验证

```bash
corepack pnpm --filter @test-agent/event-stream-client typecheck
corepack pnpm test -- event-stream-client
```

测试覆盖 RunEvent 与用户级运行态 fetch SSE 的路由头、Bearer Token、续传、解析、去重和关闭；该头不改变任何事件 payload 或游标。
