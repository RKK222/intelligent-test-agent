# 包说明：frontend/apps/agent-web/src

## 职责

承载 Vue + Vite SPA 路由页面和工作台组合层。

## 主要程序清单

- `main.ts`：应用入口，装配 Pinia、`@tanstack/vue-query` 的 `VueQueryPlugin` 和 vue-router。
- `App.vue`：根组件，渲染 `<RouterView />`。
- `router.ts`：SPA 客户端路由，`/login` 登录页、`/` 工作台、`/s/:sessionId` 只读 transcript，以及未知路径回退。
- `views/LoginView.vue`：登录页入口，登录成功后只跳回 SPA 内已知页面，非法 redirect 回退到工作台。
- `views/WorkbenchView.vue`：工作台首页入口。
- `views/TranscriptView.vue`：只读 transcript 页面入口，复用平台 session/messages API。
- `components/AgentWorkbench.vue`：组合 workspace、应用切换、用户头像退出、系统管理入口、应用版本/个人工作区切换与同步、文件树、编辑器、Agent、RunEvent SSE、Session History 搜索/置顶/删除、历史会话只读态、follow-up 队列、编辑器选区上下文、Diff 操作和底部 PTY terminal panel；启动 Run 请求 pending、页面刷新或切换历史会话时通过 `getActiveRun(sessionId)` 接管 `PENDING/RUNNING/CANCELLING` Run 并订阅 SSE，模型/Provider 选择作为用户级偏好持久化，不随工作区切换清空。
- `components/AgentConfigPanel.vue`：左侧 Agent 配置树和 Git 操作面板；公共脏仓库保持可浏览，显式确认后可恢复已跟踪修改；工作空间 `+` 初始化符合 opencode `SKILL.md` frontmatter 约定的独立应用技能包；公共级 worktree 切换只更新 `worktreeId/linuxServerId` 上下文，文件列表/读取/写入继续通过 backend-api 的 Agent 配置文件 WebSocket RPC。
- `components/FigmaChatPanel.vue`：Figma 风格右侧对话面板，按顺序渲染完整历史用户/助手消息；输入框处理输入法组合输入，避免候选词确认 Enter 被误识别为发送；输入卡片交互会请求工作台重新探测 opencode 进程状态，刷新中保持输入但暂时阻止提交；上传附件按钮当前只打开前端样式弹窗，不接后台上传。
- `components/settings/SettingsDialog.vue`：左下角设置模态，组合应用人员、版本库管理、版本库关联、应用工作空间和个人 SSH key 配置管理；无应用配置权限时展示当前角色无权限提示；"应用人员管理" tab 用 `el-autocomplete` 懒加载搜索候选用户（userId/unifiedAuthId/username LIKE 匹配，空输入不查后端），选中后主按钮从"搜索"切换为"添加"；"工作空间管理" tab 校验版本库英文名，创建区按刷新分支、加载目录、创建工作空间三步展示，非标准库额外要求 `yyyyMMdd` 版本，创建时生成 `operationId` 并轮询后端进度接口；移除应用成员、解除应用与版本库关联前必须弹出页面内 div 确认框。
- `components/SystemManagementWrapper.vue`、`components/system/SystemManagementPanel.vue`：超级管理员系统管理入口和二级导航，包含定时任务管理与运行管理。
- `components/system/ScheduledTaskManagementPanel.vue`：定时任务管理页，展示任务定义、当前/最近执行状态和历史执行记录，支持启停、Cron 编辑、手工启动和停止 `RUNNING` 运行记录。
- `components/EditorPane.vue`、`WorkspaceBootstrap.vue`、`ReadonlyTranscript.vue`：编辑器 tab 壳、Workspace 注册引导和只读 transcript 视图（不订阅 SSE，不直连 opencode）。
- `components/follow-up-queue.ts`：Run 忙碌时 prompt follow-up 的纯 FIFO 队列模型。
- `components/prompt-context.ts`：活动编辑器或 Monaco 选区到 `PromptPart` file context 的纯转换。
- `components/workbench-utils.ts`：Diff payload 解析、错误反馈、history/runtime status 派生、命令解析，以及普通工作空间根目录 `.opencode` 过滤等纯函数。
- `styles/globals.css`：Tailwind 4 全局入口、theme token、dockview-vue/Monaco 视觉适配、滚动条、panel chrome 和工作台级动画。
- `../vite.config.ts`：Vite 应用配置（Vue 插件、Tailwind 插件、workspace alias、dev server）。

## 允许依赖

- `@test-agent/*` 前端 workspace packages。
- Vue 3、vue-router、Pinia、`@tanstack/vue-query`、dockview-vue、monaco-editor、lucide-vue-next。

## 禁止依赖

- opencode server URL 或 SDK。
- 后端内部实现、数据库或 generated SDK。

## 修改时必须同步更新

- `frontend/README.md`。
- `frontend/apps/agent-web/README.md`。
- `docs/standards/frontend.md`、`docs/architecture/module-map.md`。
- `docs/api/*`，如果影响 API 或事件契约。
