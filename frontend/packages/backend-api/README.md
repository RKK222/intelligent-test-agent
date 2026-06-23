# @test-agent/backend-api

## 工程定位

前端访问 `test-agent-app` HTTP API 的唯一 client。

## 主要职责

- 统一 baseUrl、traceId、鉴权头和 JSON 解析。
- 支持 `agentId?: string` 配置，默认 `opencode`；Run、Diff 和 runtime 相关请求统一走 `/api/internal/agent/{agentId}/...`。
- 默认 30 秒请求超时，可通过 `requestTimeoutMs` 覆盖；超时统一映射为 `BackendApiError` 的 `REQUEST_TIMEOUT`。
- 映射统一错误响应为 `BackendApiError`。
- 暴露 Workspace、受控目录选择、文件、Session、Session message、Run、Diff API 方法；Session 支持 workspace 列表、全局搜索、标题/置顶更新和软删除。Workspace/Session CRUD 仍走平台 URL，不放入 agent URL。
- 暴露配置管理和个人 SSH key API 方法，统一走 `/api/internal/platform/configuration-management`，不直连 Git 服务或 opencode server。
- 暴露应用版本工作区和个人工作区 API 方法，统一走 `/api/internal/platform/workspace-management`，包括成员应用、模板、版本、个人空间、最近使用、diff 和同步。
- `startRun` 同时支持旧 `(sessionId, prompt)` 参数和对象 payload（`parts`、`messageId`、`agent`、`model`、`variant`、`mode`）。
- 暴露 opencode Web App 标准运行态方法：Agent/Model/Provider/Command/Reference 目录、config、provider auth/OAuth、worktree、Session children/todo/diff/abort/fork/compact/revert/command/shell/share、permission/question、fs/vcs/lsp/mcp status/resources/tools/auth 和 terminal ticket；除 terminal ticket 外均按 agent URL 调用。
- Command catalog 映射会保留 opencode runtime 的 `source/hints` 可选字段，供 frontend-opencode 生成 slash command 参数表单。
- 把后端文件 DTO 转换为前端稳定展示模型。
- SSH key 新增方法只发送私钥给平台后端，响应类型只包含 key 元信息，不包含明文或密文。

## 禁止事项

- 不依赖 Vue 组件、dockview-vue、Monaco 或页面状态。
- 不访问 opencode server。
- 不绕过 agent-scoped 后端 URL 调用旧 `/api/...` runtime 入口。

## 验证

```bash
corepack pnpm --filter @test-agent/backend-api typecheck
corepack pnpm test -- backend-api
```
