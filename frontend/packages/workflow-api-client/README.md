# workflow-api-client

浏览器直连同源 Python `workflow-service` 的类型化客户端。该包不依赖 `backend-api`，并用 fetch 流实现可携带 `Authorization` 与 `Last-Event-ID` 的 AG-UI SSE。

测试：`corepack pnpm vitest run packages/workflow-api-client/tests`。
