# @test-agent/agent-web

## 工程定位

Vue 3 + Vite SPA 主应用，组合 Web IDE 工作台、文件树、Monaco 编辑器、Agent 对话、Run 面板和 Diff 查看。

## 主要职责

- 提供 `/login` 登录页、`/` 工作台页面和 `/s/[sessionId]` 只读 transcript 页面（vue-router 客户端路由），未知路径回退到工作台。
- 组合 dockview-vue 三栏布局和底部运行面板。
- 组合 Figma Web IDE 风格的 40px 顶栏、48px activity rail、紧凑左侧文件面板、中间编辑器和右侧 Agent 面板；Run/Terminal 默认隐藏在底部抽屉中，通过 activity rail 打开。
- 通过 `@tanstack/vue-query` 调用 `@test-agent/backend-api`。
- 承载文件树加号触发的 Workspace 目录选择弹窗，复用已有 `POST /api/workspaces` 创建并切换，切换时清空旧文件树、编辑器、Diff、Session/Run、队列和聊天运行态。
- 通过 `@test-agent/event-stream-client` 订阅 RunEvent SSE。
- 用 Pinia 工作台状态管理打开文件、活动 tab 和 Diff 选择。
- 承载“实时追踪”行为：用户开启后，运行中的 `message.part.updated`/`diff.proposed` 会打开最近变化文件、刷新只读预览，并把 `files[].additions/deletions` 传给文件树展示行数。
- 组合 Session History 搜索、切换、置顶和软删除。
- 右上角应用菜单展示当前用户加入的应用；切换应用后优先切到应用最近使用工作区，否则进入第一个可用版本。
- 左侧文件树上方提供应用工作空间模板、版本和个人空间切换，支持新增版本、创建个人工作区、查看个人/应用差异以及双向同步。
- 点击历史会话时会按 `session.workspaceId` 切换运行态 Workspace；目标工作区不可用或无权限时保留 transcript 并禁用输入，作为只读会话展示。
- 组合运行态能力：Agent/Model/Mode 选择、按 Provider 分组的模型选择面板、slash command、`@` context、MCP/LSP/VCS 状态、Run/Session/VCS Diff 来源切换。
- 组合 prompt 文件/图片附件、活动编辑器选区上下文和 busy follow-up 本地 FIFO 队列；真正提交仍通过 `@test-agent/backend-api`。
- 右侧对话面板的“任务消耗”行展示 `duration / tokens / thought for`：duration 取 `chatStartedAt` 实时计算并在 Run 结束后锁定；tokens 累计助手消息中 `step-finish` part 的 `tokens.total`（来自 `message.part.updated`）；thought for 累计 reasoning part 的 `durationMs`；如 `run.*` 终态事件 payload 直接带上 `tokens` 或 `thoughtFor` 字段则优先采用以保持向后兼容。
- 组合底部 PTY terminal panel；ticket 创建走 `@test-agent/backend-api`，WebSocket 生命周期由 `@test-agent/terminal` 管理。
- 在 activity rail 左下角提供设置模态入口；`APP_ADMIN` 或 `SUPER_ADMIN` 用户可管理应用人员、应用与代码库关联和应用工作空间，其他用户可看到“应用与工作区”菜单但只显示当前角色无权限提示，所有登录用户可管理自己的 SSH key。
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
