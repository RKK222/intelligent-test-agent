# @test-agent/backend-api

## 工程定位

前端访问 `test-agent-app` HTTP API 的唯一 client。

## 主要职责

- 统一 baseUrl、traceId、鉴权头和 JSON 解析。
- 默认 30 秒请求超时，可通过 `requestTimeoutMs` 覆盖；超时统一映射为 `BackendApiError` 的 `REQUEST_TIMEOUT`。
- 映射统一错误响应为 `BackendApiError`。
- 暴露 Workspace、受控目录选择、文件、Session、Session message、Run、Diff API 方法；Session 支持 workspace 列表、全局搜索、标题/置顶更新和软删除。
- `startRun` 同时支持旧 `(sessionId, prompt)` 参数和对象 payload（`parts`、`messageId`、`agent`、`model`、`variant`、`mode`）。
- 暴露 opencode Web App 运行态方法：Agent/Model/Provider/Command/Reference 目录、config、provider auth/OAuth、worktree、Session children/todo/diff/abort/fork/compact/revert/command/shell/share、permission/question、fs/vcs/lsp/mcp status/resources/tools/auth 和 terminal ticket。
- Command catalog 映射会保留 opencode runtime 的 `source/hints` 可选字段，供 frontend-opencode 生成 slash command 参数表单。
- 把后端文件 DTO 转换为前端稳定展示模型。

## 禁止事项

- 不依赖 Vue 组件、dockview-vue、Monaco 或页面状态。
- 不访问 opencode server。

## 验证

```bash
corepack pnpm --filter @test-agent/backend-api typecheck
corepack pnpm test -- backend-api
```
