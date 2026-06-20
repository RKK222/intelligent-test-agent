# @test-agent/agent-web

## 工程定位

Vue 3 + Vite SPA 主应用，组合 Web IDE 工作台、文件树、Monaco 编辑器、Agent 对话、Run 面板和 Diff 查看。

## 主要职责

- 提供 `/` 工作台页面和 `/s/[sessionId]` 只读 transcript 页面（vue-router 客户端路由）。
- 组合 dockview-vue 三栏布局和底部运行面板。
- 组合 Figma Web IDE 风格的 40px 顶栏、48px activity rail、紧凑左侧文件面板、中间编辑器和右侧 Agent 面板；Run/Terminal 默认隐藏在底部抽屉中，通过 activity rail 打开。
- 通过 `@tanstack/vue-query` 调用 `@test-agent/backend-api`。
- 承载文件树加号触发的 Workspace 目录选择弹窗，复用已有 `POST /api/workspaces` 创建并切换，切换时清空旧文件树、编辑器、Diff、Session/Run、队列和聊天运行态。
- 通过 `@test-agent/event-stream-client` 订阅 RunEvent SSE。
- 用 Pinia 工作台状态管理打开文件、活动 tab 和 Diff 选择。
- 承载“实时追踪”行为：用户开启后，运行中的 `message.part.updated`/`diff.proposed` 会打开最近变化文件、刷新只读预览，并把 `files[].additions/deletions` 传给文件树展示行数。
- 组合 Session History 搜索、切换、置顶和软删除。
- 组合运行态能力：Agent/Model/Mode 选择、按 Provider 分组的模型选择面板、slash command、`@` context、MCP/LSP/VCS 状态、Run/Session/VCS Diff 来源切换。
- 组合 prompt 文件/图片附件、活动编辑器选区上下文和 busy follow-up 本地 FIFO 队列；真正提交仍通过 `@test-agent/backend-api`。
- 组合底部 PTY terminal panel；ticket 创建走 `@test-agent/backend-api`，WebSocket 生命周期由 `@test-agent/terminal` 管理。
- 承载全局 theme token、dockview-vue/Monaco 视觉适配、滚动条、activity rail、panel chrome 和工作台级动画；业务包只消费样式变量，不各自复制主题。
- `/s/[sessionId]` 只读 transcript 只展示平台 session/messages 投影，不订阅 RunEvent，不暴露编辑、terminal 或 Diff 落盘动作。
- 入口 `src/main.ts` 装配 Pinia、`VueQueryPlugin` 和 vue-router；全局样式由 `src/styles/globals.css` 承载。

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

真实三服务验收由仓库根目录 `tools/dev-phase11-real-e2e.sh --start-services` 触发，目前尚无最新通过记录；不能用该 README 的单包验证命令替代真实三服务 E2E 收口。
