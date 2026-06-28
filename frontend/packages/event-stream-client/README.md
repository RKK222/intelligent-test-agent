# @test-agent/event-stream-client

## 工程定位

前端消费平台 RunEvent SSE 的唯一 client。

## 主要职责

- 连接 `/api/internal/agent/{agentId}/runs/{runId}/events`，`agentId` 默认 `opencode`；旧 `/api/runs/{runId}/events` 仅作为后端兼容入口。
- 监听平台 RunEvent wire name。
- 所有事件（包括 transient 文本增量）优先按 `eventId` 去重，兼容缺失 `eventId` 的旧事件时才回退 `runId + seq`；`seq=0` 且缺失 `eventId` 的旧增量不能按固定序号互相去重。
- 使用 `lastEventId` query 参数支持浏览器原生 EventSource 的首次续传，agent URL 下格式不变。
- 提供关闭订阅和连接状态回调。

已知事件包括旧 `run.*`、`assistant.message.delta`、`tool.*`、`diff.*`，以及 opencode Web App 运行态 `message.*`、`session.status`、`todo.updated`、`permission.*`、`question.*`、`vcs.branch.updated`、`lsp.updated`、`mcp.tools.changed`。transient live output 的 payload `seq=0` 且没有 SSE `id`，客户端必须依赖 payload `eventId` 去重。

## 禁止事项

- 不直接修改 Vue 状态。
- 不访问 opencode server。
- 不绕过 agent-scoped SSE URL 订阅旧 runtime 入口。
- 不处理业务卡片渲染。

## 验证

```bash
corepack pnpm --filter @test-agent/event-stream-client typecheck
corepack pnpm test -- event-stream-client
```
