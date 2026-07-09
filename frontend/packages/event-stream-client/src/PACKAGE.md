# 包说明：@test-agent/event-stream-client/src

## 职责

封装 agent-scoped RunEvent SSE 与用户级运行态 fetch SSE 的订阅、解析、去重、续传和关闭。

## 主要程序清单

- `index.ts`：`subscribeRunEvents`、`subscribeSessionRuntimeState`、`parseRunEvent`、已知事件类型列表；`agentId?: string` 默认 `opencode`，RunEvent SSE URL 使用 `/api/internal/agent/{agentId}/runs/{runId}/events`；浏览器 EventSource 续传使用 `lastEventId` query 参数；用户级运行态使用 fetch 访问 `/api/internal/platform/opencode-runtime/sessions/runtime-state/events`，可携带 Bearer Token 并解析 `session-runtime.snapshot` / `session-runtime.updated`；可选 `onRawMessage` 在解析 RunEvent 前回调浏览器实际收到的 `MessageEvent.data`、事件名、lastEventId、runId 和接收时间；durable 和 transient 事件都优先使用 payload `eventId` 去重，缺失 `eventId` 的旧事件才回退 `runId + seq`，旧 `seq=0` 增量保持放行。

## 允许依赖

- `@test-agent/shared-types`。
- 浏览器 `EventSource` / `fetch` API。

## 禁止依赖

- React 组件、业务状态、opencode server。

## 修改时必须同步更新

- `docs/api/event-stream.md`。
- `docs/architecture/module-map.md`。
- 本包 README 和测试。
