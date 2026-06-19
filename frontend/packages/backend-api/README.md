# @test-agent/backend-api

## 工程定位

前端访问 `test-agent-app` HTTP API 的唯一 client。

## 主要职责

- 统一 baseUrl、traceId、鉴权头和 JSON 解析。
- 映射统一错误响应为 `BackendApiError`。
- 暴露 Workspace、文件、Session、Session message、Run、Diff API 方法。
- `startRun` 同时支持旧 `(sessionId, prompt)` 参数和 Phase 11 对象 payload（`parts`、`messageId`、`agent`、`model`、`variant`、`mode`）。
- 暴露 Phase 11 opencode Web App 运行态方法：Agent/Model/Provider/Command/Reference 目录、Session children/todo/diff/abort/fork/compact/revert/command/shell、permission/question、fs/vcs/lsp/mcp status/resources/tools。
- 把后端文件 DTO 转换为前端稳定展示模型。

## 禁止事项

- 不依赖 React 组件、Dockview、Monaco 或页面状态。
- 不访问 opencode server。

## 验证

```bash
corepack pnpm --filter @test-agent/backend-api typecheck
corepack pnpm test -- backend-api
```
