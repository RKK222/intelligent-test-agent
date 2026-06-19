# @test-agent/agent-web

## 工程定位

Next.js 主应用，组合 Web IDE 工作台、文件树、Monaco 编辑器、Agent 对话、Run 面板和 Diff 查看。

## 主要职责

- 提供 `/` 工作台页面和 `/s/[sessionId]` 只读 transcript 页面。
- 组合 Dockview 三栏布局和底部运行面板。
- 通过 TanStack Query 调用 `@test-agent/backend-api`。
- 通过 `@test-agent/event-stream-client` 订阅 RunEvent SSE。
- 用 Zustand 工作台状态管理打开文件、活动 tab 和 Diff 选择。
- 组合 Phase 11 运行态能力：Agent/Provider/Model/Mode 选择、slash command、`@` context、MCP/LSP/VCS 状态、Run/Session/VCS Diff 来源切换。
- Next dev 配置允许 `127.0.0.1` 作为开发来源，避免本地从该地址访问时 dev resource 被拦截导致工作台交互不可用。

## 禁止事项

- 不直接拼接后端 URL。
- 不直连 opencode server。
- 不把通用业务组件堆在 app 内，必须下沉到 packages。
- `/s/[sessionId]` 只能读取平台 session transcript，不得接 opencode 公网 share API。

## 验证

```bash
corepack pnpm --filter @test-agent/agent-web typecheck
corepack pnpm --filter @test-agent/agent-web build
```
