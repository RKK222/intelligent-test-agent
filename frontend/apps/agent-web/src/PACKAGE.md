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
- `components/AgentWorkbench.vue`：组合 workspace、应用切换、用户头像退出、系统管理入口、应用版本/个人工作区切换与同步、文件树、编辑器、Agent、RunEvent SSE、用户级运行态 fetch SSE、Session History 搜索/置顶/删除、历史会话只读态、follow-up 队列、编辑器选区上下文、Diff 操作、底部 PTY terminal panel 和宠物旁路问答 API 编排；登录/刷新后查询一次 `/processes/me` 获取用户 opencode 进程归属，常态每 10 秒用弱健康接口驱动发送、目录和运行态 ready，弱健康不健康时复查 `/processes/me` 并以强状态结果覆盖；普通消息和 slash 技能统一创建平台 Run，先签发并复用页面内存 `contextToken`，`startRun` 携带稳定 `clientRequestId`，认证、Session、Workspace 或历史交互变化时通过 interaction fence 丢弃迟到的 Session/context/Run 结果，历史切换加载完成前由聊天面板和 `handleSend` 双层阻断发送；用户级 runtime-state fetch SSE 是启动 pending、页面刷新和历史切回的主恢复入口，`active-run` 仅在流不可用时按“每故障窗口、每 Session 一次”fallback，不做 1.5 秒轮询；切换历史会话时同时读取 OpenCode 当前 permission/question pending 列表并覆盖历史事件中的 ask 快照，避免拿已失效的 requestId 提交；运行中点击新建对话只清空当前视图并关闭当前 RunEvent SSE，不取消后端 Run，后台运行计数由用户级运行态摘要补齐但历史按钮 badge 只按历史第一页 30 条派生；RunEvent 只应用到当前订阅且仍为页面活动态的 Run，防止旧订阅晚到终态污染新一轮；将运行态 Agent 列表和当前 `selectedAgent` 下发给 `FigmaChatPanel`，切换后下一次 `startRun` 携带用户选择的 `agent`；模型/Provider 选择作为用户级偏好持久化，不随工作区切换清空；同时维护当前页面生命周期内的会话级原始报文内存缓存，只收集会话创建、Run 启动/取消、active-run、消息加载、permission/question 回复和 RunEvent SSE。
- `run.snapshot.reset`：由 agent-chat reducer 原子清空并重放当前 Run，`AgentWorkbench` 同步清空独立 Diff/实时跟随状态后按快照原顺序重建，且不重复桌面通知、不推进 durable 游标。
- `REDIS_SUMMARY` 记录 `run.created.assistantSummaryMessageId`，终态把最后 root assistant 的 remote ID 直接绑定到稳定平台反馈 ID，不轮询 Session 消息表。
- `components/useSideQuestionRun.ts`：只管理宠物旁路 Run 的单飞、RunEvent SSE、真实阶段、delta、终态校准、重连提示和订阅释放；不持有主 Run abort 能力，主 Session 切换时清理旧上下文展示。
- `components/FigmaShell.vue`：工作台整体 shell 和顶部栏，应用切换左侧展示已加载 Agent、Skill、MCP、Plugin 数量摘要，点击后弹出只读详情面板；应用菜单、用户头像菜单、左右面板拖拽、宠物本体进程心形、宠物进程状态气泡，以及点击宠物后以对话为主体的统一浮层也在此组件内；小游戏只通过浮层标题栏的低强调手柄按钮进入，不提供独立活动栏入口。首次确认进程未初始化时，活动栏机器人与红心显示呼吸效果，并在当前页面生命周期内自动唤出一次宠物初始化询问；气泡中的初始化按钮复用工作台已有初始化流程，旁路加载或小游戏打开期间自然离场不会关闭浮层，显式关闭与收起宠物仍有效。
- `components/HelpCenterDialog.vue` 与 `components/help-center.ts`：从顶部问号、初始化状态卡和宠物进程气泡打开 `/help/` 下的 VitePress 静态手册，维护稳定章节 ID、同源 URL 和有界上下文问答 prompt；问答使用当前章节 Markdown 作为单一事实源并复用 `useSideQuestionRun`，没有主 Session 时保持手册与本地全文搜索可用。
- `components/PetMiniGames.vue`：嵌入宠物统一浮层的本地小游戏区域，以紧凑 2×2 小卡片提供可操作的 10×16 俄罗斯方块、8×8 扫雷、9×9 数独和 12×12 贪吃蛇；扫雷数字格支持在相邻旗子数匹配时双击展开其余邻格。组件只维护页面内存中的棋盘、分数、暂停和输赢状态，不访问后端、不持久化数据，切换或卸载时释放俄罗斯方块、贪吃蛇计时器。
- 宠物旁路 fork 可用性由 `AgentWorkbench` 的真实主 `sessionId` 下发；没有历史主 Session 或仅有未发送的新对话草稿时，`FigmaShell` 禁用旁路入口，提交处理层继续保留空 Session 防御。
- 宠物单击按进程状态分流：进程 ready 时打开统一浮层，默认进入对话页；有真实主 Session 时聚焦提问输入框，无 Session 时只禁用对话并允许切换游戏。进程未 ready 时保留状态气泡作为初始化或不可用说明入口。
- `components/AgentConfigPanel.vue`：左侧 Agent 配置树和 Git 操作面板；公共脏仓库保持可浏览，显式确认后可恢复已跟踪修改；worktree publish 合并冲突时展示后端 `details.conflictFiles`；工作空间 `+` 初始化符合 opencode `SKILL.md` frontmatter 约定的独立应用技能包；公共级 worktree 切换只更新 `worktreeId/linuxServerId` 上下文，文件列表/读取/写入继续通过 backend-api 的 Agent 配置文件 WebSocket RPC。
- `components/GitChangesPanel.vue`：应用工作区真实 stage/unstage、发布白名单、远端 push 确认和冲突编排；冲突期间允许普通文件调整 index 但禁止提交，文件行提供完整路径提示并把已加载 patch 直接交给工作台；冲突内容由 backend-api 读取，复用 `diff-viewer/MergeConflictEditor` 完成三方编辑、保存解决结果和取消 merge。
- `components/FigmaChatPanel.vue`：Figma 风格右侧对话面板，按顺序渲染完整历史用户/助手消息；新一轮运行开始时清理上一轮完成/失败/手动终止提示，避免旧终态覆盖当前结果；header 提供“原始输出”悬浮窗，使用 `<pre>` 展示前端捕获的请求体、响应体和 SSE `data` 原文，支持拖动、CSS resize、筛选和清空当前会话记录；历史按钮展示用户级运行中计数、旋转图标和待回答铃铛，历史卡片按会话运行态展示 Spinner、已完成图标和待答铃铛；`question.asked` / `permission.asked` 归并出的待处理请求固定展示在输入框上方，普通 assistant 编号列表不触发提问面板；历史子 Agent task 卡片必须先由 session tree 或 task `<task_result>` 恢复出 `subagentsBySessionId` 索引，点击后才进入对应 child scope，避免 metadata-only 卡片进入空视图；`question.asked` 面板按单个问题分页展示 `1/N 个问题`、选项 label/description 和“输入自己的答案”，单选自定义答案与选项互斥，多选自定义答案作为附加答案，提交时按问题顺序 emit `answers: string[][]`；`todo.updated` 归并出的 Todo 面板固定在输入框上方，收起态展示各状态数量和总数，展开态展示任务列表；工作区上下文附件固定展示在输入框上方，支持预览、删除、清空和超限发送拦截，超过 3 个时默认折叠并显示阶段数量摘要，展开区最高 220px 可滚动；输入卡片底部提供受控 Agent 选择，按 `primary/all` 且非 hidden 过滤主运行 Agent；输入卡片使用极简边框，无投影；输入框 `@` 同时展示按 `subagent/all` 且非 hidden 过滤的 Agent 与当前 worktree 文件，完整文件名作为主信息、完整相对目录作为低强调次信息，均允许换行且选择复用现有上下文附件动作；输入框 `#` 按需求项和同名子条目聚合 `01-需求、02-设计、03-编码、04-测试` 下的全部关联文件，候选仅显示需求项/子条目/文件数，长名称自然换行且不展开文件清单，选择后再把全部关联文件加入上下文；输入框处理输入法组合输入，避免候选词确认 Enter 被误识别为发送；输入区只消费工作台传入的 opencode 健康 ready 状态，不主动触发 `/processes/me` 强状态查询；运行中且进程 ready 时新建对话保持可点；工作台以宠物承载进程状态，传入 `processStatusPlacement="pet"` 时隐藏聊天区旧进程点/卡片但仍保留发送拦截；上传附件按钮当前只打开前端样式弹窗，不接后台上传。
- `FigmaChatPanel` 的旧内联进程状态卡在展示时把初始化按钮和上下文手册问号放在同一操作区；两者分别向工作台发出初始化和 `process-initialization` 章节事件，帮助入口不会折叠或拖动状态卡。
- `stores/chatContextStore.ts`：管理工作区上下文附件，包含 selection/file 类型、字符数限制、添加/删除/清空、总量统计、发送前校验和前端 prompt 序列化。
- 对话可输入条件：主输入卡不再依赖 Session 选择态；进程 ready 且不存在历史切换锁、只读或上下文校验错误时，可以直接输入首条消息，`AgentWorkbench` 在发送时延迟创建 Session。“新建对话”只负责主动清空当前上下文；宠物旁路仍复用真实 `sessionId` 单独设防。
- `components/ChatContextAttachmentList.vue` / `ChatContextAttachmentCard.vue` / `ChatContextPreviewDrawer.vue`：输入框上方的上下文附件展示、单卡片删除和只读预览抽屉。
- `components/settings/SettingsDialog.vue`：左下角设置模态，组合应用人员、版本库管理、版本库关联、应用工作空间和个人 SSH key 配置管理；无应用配置权限时展示当前角色无权限提示；"应用人员管理" tab 用 `el-autocomplete` 懒加载搜索候选用户（userId/unifiedAuthId/username LIKE 匹配，空输入不查后端），选中后主按钮从"搜索"切换为"添加"；"工作空间管理" tab 校验版本库英文名，创建区按刷新分支、加载目录、创建工作空间三步展示，非标准库额外要求 `yyyyMMdd` 版本，创建时生成 `operationId` 并轮询后端进度接口；移除应用成员、解除应用与版本库关联前必须弹出页面内 div 确认框。
- `utils/ssh-crypto.ts`：个人 SSH key 浏览器端混合加密工具，按后端契约生成 AES-GCM 私钥密文、RSA-OAEP/SHA-256 临时 AES 密钥密文和 SHA-256 指纹；优先使用 Web Crypto，企业内 Chromium 108 的 HTTP 内网访问缺少 `crypto.subtle` 时使用 node-forge 纯 JS 回退；如果 `getRandomValues` 不可用则返回明确错误，不允许明文降级。
- `components/SystemManagementWrapper.vue`、`components/system/SystemManagementPanel.vue`：超级管理员系统管理入口和二级导航，包含定时任务管理与运行管理。
- `components/system/ScheduledTaskManagementPanel.vue`：定时任务管理页，展示任务定义、当前/最近执行状态和历史执行记录，支持启停、Cron 编辑、手工启动和停止 `RUNNING` 运行记录。
- `components/EditorPane.vue`、`ReadonlyTranscript.vue`：编辑器 tab 壳和只读 transcript 视图（不订阅 SSE，不直连 opencode）。
- `components/follow-up-queue.ts`：Run 忙碌时 prompt follow-up 的纯 FIFO 队列模型。
- `components/prompt-context.ts`：活动编辑器或 Monaco 选区到 `PromptPart` file context 的纯转换。
- `components/workbench-utils.ts`：Diff payload 解析、错误反馈、history/runtime status 派生、session-tree 历史快照恢复和子 Agent 索引兜底、opencode 弱健康 ready 规则、命令解析、跨需求/设计/编码/测试阶段的子条目路径聚合，以及普通工作空间根目录 `.opencode` 过滤等纯函数。
- `styles/globals.css`：Tailwind 4 全局入口、theme token、dockview-vue/Monaco 视觉适配、滚动条、panel chrome 和工作台级动画。
- `../vite.config.ts`：Vite 应用配置（Vue 插件、Tailwind 插件、workspace alias、dev server）。
- `AgentWorkbench.handleSend` 继续把完整 text/file parts 交给 Run 请求；本地乐观 user message 只接收经 `promptPartsForUserDisplay` 收敛后的文本和附件元数据。历史 session-tree 恢复会用同 messageId 的首个非 synthetic text part 补齐无正文的 OpenCode user envelope，再复用原 reducer 归并 file part，避免文件正文成为独立消息且不改变 Timeline、事件投影和 OpenCode parts 协议。

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
