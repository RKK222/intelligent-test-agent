# @test-agent/event-stream-client

## 工程定位

前端消费平台 RunEvent SSE 的唯一 client。

## 主要职责

- 连接 `/api/runs/{runId}/events`。
- 监听平台 RunEvent wire name。
- 按 `runId + seq` 去重。
- 提供关闭订阅和连接状态回调。

## 禁止事项

- 不直接修改 React 状态。
- 不访问 opencode server。
- 不处理业务卡片渲染。

## 验证

```bash
corepack pnpm --filter @test-agent/event-stream-client typecheck
corepack pnpm test -- event-stream-client
```
