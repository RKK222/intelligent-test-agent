# @test-agent/agent-web

## 工程定位

Next.js 主应用，组合 Web IDE 工作台、文件树、Monaco 编辑器、Agent 对话、Run 面板和 Diff 查看。

## 主要职责

- 提供 `/` 工作台页面和 `/s/[sessionId]` 只读 transcript 页面。
- 组合 Dockview 三栏布局和底部运行面板。
- 通过 TanStack Query 调用 `@test-agent/backend-api`。
- 通过 `@test-agent/event-stream-client` 订阅 RunEvent SSE。
- 用 Zustand 工作台状态管理打开文件、活动 tab 和 Diff 选择。
- 组合 Session History 搜索、切换、置顶和软删除。
- 组合 Phase 11 运行态能力：Agent/Provider/Model/Mode 选择、slash command、`@` context、MCP/LSP/VCS 状态、Run/Session/VCS Diff 来源切换。
- 组合 prompt 文件/图片附件、活动编辑器选区上下文和 busy follow-up 本地 FIFO 队列；真正提交仍通过 `@test-agent/backend-api`。
- 组合底部 PTY terminal panel；ticket 创建走 `@test-agent/backend-api`，WebSocket 生命周期由 `@test-agent/terminal` 管理。
- 承载全局 theme token、Dockview/Monaco 视觉适配、滚动条、panel chrome 和工作台级动画；业务包只消费样式变量，不各自复制主题。
- `/s/[sessionId]` 只读 transcript 只展示平台 session/messages 投影，不订阅 RunEvent，不暴露编辑、terminal 或 Diff 落盘动作。
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

真实三服务验收由仓库根目录 `tools/dev-phase11-real-e2e.sh --start-services` 触发，目前尚无最新通过记录；不能用该 README 的单包验证命令替代 Phase 11 real E2E 收口。
