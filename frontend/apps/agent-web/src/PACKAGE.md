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
- `components/AgentWorkbench.vue`：组合 workspace、应用切换、用户头像退出、系统管理入口、应用版本/个人工作区切换与同步、文件树、编辑器、Agent、RunEvent SSE、Session History 搜索/置顶/删除、历史会话只读态、follow-up 队列、编辑器选区上下文、Diff 操作和底部 PTY terminal panel；登录/刷新后查询一次 `/processes/me` 获取用户 opencode 进程归属，常态每 10 秒用弱健康接口驱动发送、目录和运行态 ready，弱健康不健康时复查 `/processes/me` 并以强状态结果覆盖；普通消息和 slash 技能统一创建平台 Run，启动请求 pending、页面刷新或切换历史会话时通过 `getActiveRun(sessionId)` 接管 `PENDING/RUNNING/CANCELLING` Run 并订阅 SSE；RunEvent 只应用到当前订阅且仍为页面活动态的 Run，防止旧订阅晚到终态污染新一轮；将运行态 Agent 列表和当前 `selectedAgent` 下发给 `FigmaChatPanel`，切换后下一次 `startRun` 携带用户选择的 `agent`；模型/Provider 选择作为用户级偏好持久化，不随工作区切换清空；同时维护当前页面生命周期内的会话级原始报文内存缓存，只收集会话创建、Run 启动/取消、active-run、消息加载、permission/question 回复和 RunEvent SSE。
- `components/AgentConfigPanel.vue`：左侧 Agent 配置树和 Git 操作面板；公共脏仓库保持可浏览，显式确认后可恢复已跟踪修改；worktree publish 合并冲突时展示后端 `details.conflictFiles`；工作空间 `+` 初始化符合 opencode `SKILL.md` frontmatter 约定的独立应用技能包；公共级 worktree 切换只更新 `worktreeId/linuxServerId` 上下文，文件列表/读取/写入继续通过 backend-api 的 Agent 配置文件 WebSocket RPC。
- `components/GitChangesPanel.vue`：应用工作区真实 stage/unstage、发布白名单、远端 push 确认和冲突编排；冲突期间允许普通文件调整 index 但禁止提交，文件行提供完整路径提示并把已加载 patch 直接交给工作台；冲突内容由 backend-api 读取，复用 `diff-viewer/MergeConflictEditor` 完成三方编辑、保存解决结果和取消 merge。
- `components/FigmaChatPanel.vue`：Figma 风格右侧对话面板，按顺序渲染完整历史用户/助手消息；新一轮运行开始时清理上一轮完成/失败/手动终止提示，避免旧终态覆盖当前结果；header 提供“原始输出”悬浮窗，使用 `<pre>` 展示前端捕获的请求体、响应体和 SSE `data` 原文，支持拖动、CSS resize、筛选和清空当前会话记录；`question.asked` / `permission.asked` 归并出的待处理请求固定展示在输入框上方，普通 assistant 编号列表不触发提问面板；`question.asked` 面板按单个问题分页展示 `1/N 个问题`、选项 label/description 和“输入自己的答案”，单选自定义答案与选项互斥，多选自定义答案作为附加答案，提交时按问题顺序 emit `answers: string[][]`；`todo.updated` 归并出的 Todo 面板固定在输入框上方，收起态展示各状态数量和总数，展开态展示任务列表；工作区上下文附件固定展示在输入框上方，支持预览、删除、清空和超限发送拦截；输入卡片底部提供受控 Agent 选择，按 `primary/all` 且非 hidden 过滤主运行 Agent；输入框 `@` 候选按 `subagent/all` 且非 hidden 过滤可提及 Agent；输入框处理输入法组合输入，避免候选词确认 Enter 被误识别为发送；输入区只消费工作台传入的 opencode 健康 ready 状态，不主动触发 `/processes/me` 强状态查询；上传附件按钮当前只打开前端样式弹窗，不接后台上传。
- `stores/chatContextStore.ts`：管理工作区上下文附件，包含 selection/file 类型、字符数限制、添加/删除/清空、总量统计、发送前校验和前端 prompt 序列化。
- `components/ChatContextAttachmentList.vue` / `ChatContextAttachmentCard.vue` / `ChatContextPreviewDrawer.vue`：输入框上方的上下文附件展示、单卡片删除和只读预览抽屉。
- `components/settings/SettingsDialog.vue`：左下角设置模态，组合应用人员、版本库管理、版本库关联、应用工作空间和个人 SSH key 配置管理；无应用配置权限时展示当前角色无权限提示；"应用人员管理" tab 用 `el-autocomplete` 懒加载搜索候选用户（userId/unifiedAuthId/username LIKE 匹配，空输入不查后端），选中后主按钮从"搜索"切换为"添加"；"工作空间管理" tab 校验版本库英文名，创建区按刷新分支、加载目录、创建工作空间三步展示，非标准库额外要求 `yyyyMMdd` 版本，创建时生成 `operationId` 并轮询后端进度接口；移除应用成员、解除应用与版本库关联前必须弹出页面内 div 确认框。
- `components/SystemManagementWrapper.vue`、`components/system/SystemManagementPanel.vue`：超级管理员系统管理入口和二级导航，包含定时任务管理与运行管理。
- `components/system/ScheduledTaskManagementPanel.vue`：定时任务管理页，展示任务定义、当前/最近执行状态和历史执行记录，支持启停、Cron 编辑、手工启动和停止 `RUNNING` 运行记录。
- `components/EditorPane.vue`、`ReadonlyTranscript.vue`：编辑器 tab 壳和只读 transcript 视图（不订阅 SSE，不直连 opencode）。
- `components/follow-up-queue.ts`：Run 忙碌时 prompt follow-up 的纯 FIFO 队列模型。
- `components/prompt-context.ts`：活动编辑器或 Monaco 选区到 `PromptPart` file context 的纯转换。
- `components/workbench-utils.ts`：Diff payload 解析、错误反馈、history/runtime status 派生、session-tree 历史快照恢复和子 Agent 索引兜底、opencode 弱健康 ready 规则、命令解析，以及普通工作空间根目录 `.opencode` 过滤等纯函数。
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
