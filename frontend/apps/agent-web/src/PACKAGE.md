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
- `components/ReferenceConfigurationDialog.vue`、`components/reference-configuration-access.ts`：仅在 `APP_ADMIN`/`SUPER_ADMIN` 且具备应用、个人工作区和运行态工作区上下文时提供引用配置；左栏初始化资产库，点击已初始化卡片会在同步 POST 返回前展示三阶段/逐服务器同步进度，也可经接管焦点的二次确认切换应用资产分支；右栏对照目标与各服务器实际 Git 指针、同步/核验时间并提供独立的只读主动核验，再只允许选择后端标记的根层 SDD 目录编辑当前个人工作区引用。同步与核验复用操作感知弹层但分别调用真实接口，按选择代次、operation、后端 generation 和同代次请求序号 fencing 丢弃迟到结果；活动状态每 2 秒轮询，READY 先通知工作区刷新再独立加载弹窗目录，不从缺失的在线/匹配字段推断可信状态。保存后由工作台在运行态空闲时复用 OpenCode `/global/dispose` 重建当前个人工作区实例，运行中则延迟到任务结束。
- `AgentWorkbench` 的夜间任务编排复用普通 Run 的 prompt parts、Agent/Model/Mode/Command 参数和稳定请求 ID，但提交时不写乐观时间线；会话列表抽屉内的待执行页按 200 条逐页收齐，切换页签时立即查询，并每 30 秒及窗口 focus 刷新用户待执行任务和当前 Session 失败卡，容量冲突时重取时段。创建成功后才清空输入/上下文，任务成功投递后继续走既有 RunEvent SSE。当前 Session 待执行时 `handleSend` 后端 UI 守卫拒绝普通发送。
- `components/ReferenceConfigurationDialog.vue`、`components/reference-configuration-access.ts`：仅在 `APP_ADMIN`/`SUPER_ADMIN` 且具备应用、个人工作区和运行态工作区上下文时提供引用配置；左栏初始化资产库，点击已初始化卡片会在同步 POST 返回前展示三阶段/逐服务器同步进度，也可经接管焦点的二次确认切换应用资产分支；右栏对照目标与各服务器实际 Git 指针、同步/核验时间并提供独立的只读主动核验，再只允许选择后端标记的根层 SDD 目录编辑当前个人工作区引用。同步与核验复用操作感知弹层但分别调用真实接口，按选择代次、operation、后端 generation 和同代次请求序号 fencing 丢弃迟到结果；活动状态每 2 秒轮询，READY 先通知工作区刷新再独立加载弹窗目录，不从缺失的在线/匹配字段推断可信状态。
- 指针核验 UI 在刷新按钮左侧显示可空服务器绝对路径，并在 POST 前创建按仓库 ID/核验 generation 绑定的三阶段进度弹层；逐服务器映射等待、处理、完成、重试、阻塞和离线延后，活动期锁定父弹层与焦点，终态保留并在关闭后恢复当前刷新按钮焦点，后台状态读取临时失败继续按 2 秒轮询。
- `components/reference-config-jsonc.ts`：使用 `jsonc-parser` 检查本地引用冲突，并对 `.opencode/opencode.jsonc` 的目标 alias 执行最小字段补丁；保存前由弹窗重新读取磁盘正文，文件读写统一委托 backend-api 工作区文件 WebSocket RPC。
- `components/AgentWorkbench.vue` 的工作区文件编排：消费组合视图稳定 `id/locator/source/readonly/workspacePath`，按配置代次刷新已展开目录并丢弃迟到结果；引用文件以独立只读 tab 打开，可用 `references/<alias>/<relativePath>` 加入对话，但不进入搜索、Git Diff 或 requirements。
- `AgentWorkbench` 的普通工作区文件入口统一使用带 workspace/路径请求代次的加载器：首次读取不挂载 Monaco，成功零字节文件也进入 loaded；后台响应只更新仍存在且内容修订代次未变化的所属 tab，关闭 tab、切 workspace、同路径后续请求或读取期间任何编辑都会使旧响应失效，即使编辑随后已保存/回退为 clean。稳定快照身份独立于瞬时 loading，刷新失败保留已有正文；批量磁盘刷新固定起始 workspace 上下文，不能跨 await 继续读取新 workspace。
- `AgentWorkbench` 的普通文件复制/移动和浏览器多文件上传统一调用 backend-api 文件 WebSocket RPC；上传按块转 Base64，成功后刷新目标目录与 Git diff，移动时同步已打开 Tab 路径；当前个人 worktree 内维护复制、移动、上传的逆操作栈供 Ctrl/Cmd+Z 撤销，切换 worktree 时清空。
- `AgentWorkbench` 删除普通文件或目录树后同步清理文件树缓存、后代展开状态和目录内全部已打开 Tab，并刷新 Git diff；删除不进入撤销栈。
- `AgentWorkbench` 的公共级与应用级 Agent 文件入口使用独立加载器：按 scope/workspace/worktree/server 上下文代次、合成 tab 路径、同路径请求代次、tab 存在性和内容修订代次隔离异步响应；首次 loading 不挂载空 Monaco，零字节文件进入 loaded，dirty 或读取期间发生编辑时保留用户正文，缓存刷新失败保留上次正文，NOT_FOUND 刷新只关闭 clean tab。顶部 tab 对 loaded 使用缓存，对 loading 去重，对 error/旧版未标记 tab 重新读取；通用重试按 Agent/普通文件类型分发。合成 tab path 只用于身份和读写路由，页脚复制路径使用打开文件时固化的真实绝对路径。
- `AgentWorkbench` 每次 `run.requested` 记录用户消息 ID 和被替代 Run；旧 Run 因标题同步继续订阅时，`runEventProjectionMode` 只放行 `session.updated`，其余消息、Todo、snapshot、错误回调和终态不进入对话 reducer。HTTP 或 runtime-state 接管仅使用本页显式未决启动请求绑定用户消息，外部 Run 等远端 user message 后再归属；follow-up、手动重试和自动重试均携带原用户消息 ID。历史 session Todo HTTP 通过 `reconcileCurrentTurnTodos` 按最新 root 轮保守校准，无归属证据的非空结果不展示，显式事件快照优先于持久化 part fallback。
- `AgentWorkbench` 的主编辑器保存按钮与 Ctrl/Cmd+S 共用 `saveMutation`；只有 active tab dirty、非 livePreview、非只读且没有保存请求进行中时才写盘。成功保存 Agent 定义、Skill 的 `SKILL.md` 或 `opencode.jsonc` 后重新拉取当前工作区 Agent/Command 目录：应用个人配置复用 `disposeGlobal()`，公共个人配置调用 `reloadPublicPersonalAgentRuntime()`，由后端把当前用户固定公共配置链接切到本人公共 worktree 后只 dispose 本人；运行中延迟到任务空闲，进程未 READY 时不因保存拉起进程。应用配置会在后续 workspace bootstrap 原生读取，公共个人预览因新进程启动默认恢复共享链接而需在 READY 后再次保存或正式推送。普通 rules/templates 只保存不热加载。任何 Agent 配置文件成功落盘都会递增修订号，让隐藏或显示中的 `GitChangesPanel` 立即重新统计公共/应用 Agent diff；文件保存成功但运行态刷新失败时明确提示部分成功，不把已落盘文件误报为保存失败。
- `run.snapshot.reset`：由 agent-chat reducer 原子清空并重放当前 Run，`AgentWorkbench` 同步清空独立 Diff/实时跟随状态后按快照原顺序重建，且不重复桌面通知、不推进 durable 游标。
- `REDIS_SUMMARY` 记录 `run.created.assistantSummaryMessageId`，终态把最后 root assistant 的 remote ID 直接绑定到稳定平台反馈 ID，不轮询 Session 消息表。
- `components/useSideQuestionRun.ts`：只管理宠物旁路 Run 的单飞、RunEvent SSE、真实阶段、delta、终态校准、重连提示和订阅释放；不持有主 Run abort 能力，主 Session 切换时清理旧上下文展示。
- `components/FigmaShell.vue`：工作台整体 shell 和顶部栏，应用切换左侧展示已加载 Agent、Skill、MCP、Plugin 数量摘要，点击后弹出只读详情面板；应用菜单、用户头像菜单、左右面板拖拽、宠物进程状态、宠物进程状态气泡，以及点击宠物后以对话为主体的统一浮层也在此组件内；伙伴名册复用 `PetCompanionAvatar.vue` 的七种图片角色和 `pet-companions.ts` 的本地自然日轮换/每日随机/固定选择策略，并以单行七列展示，支持大小比例滑杆，伙伴和大小偏好只写浏览器 localStorage。运行态使用头像下方的青蓝/暖红/银蓝细光圈，名册按角色保留不同底色，不再把状态叠加到眼睛或独立状态点。宠物拖动和大小调整按企业内 Chromium 108 基线实现：拖动在 pointerdown 后使用 window 捕获阶段的 pointermove/up/cancel 监听，避免工作台子组件停止冒泡后中断拖动，且不依赖 pointer capture；大小使用根元素 width/height 计算，不使用新版 CSS `scale` 属性；鼠标悬浮宠物时冻结当前视觉位置并暂停自然动作。小游戏只通过浮层标题栏的低强调手柄按钮进入，不提供独立活动栏入口。首次确认进程未初始化时，活动栏伙伴与宠物暖红状态环显示呼吸效果，并在当前页面生命周期内自动唤出一次宠物初始化询问；已分配进程终止时，活动栏入口直接复用工作台初始化流程，READY 后只唤出宠物，避免二次点击；气泡中的初始化按钮继续保留。旁路加载或小游戏打开期间自然离场不会关闭浮层，显式关闭与收起宠物仍有效。
- `components/AgentConfigPanel.vue`：展示公共级/应用级 Agent 配置树；左侧根节点保留 Agent 配置更新按钮，公共/应用动作组统一布局且刷新图标固定在最右侧；宠物浮层仅在对话页提供同一能力，选择页不展示，均按权限显示并复用工作台运行态处理。
- `components/HelpCenterDialog.vue` 与 `components/help-center.ts`：从工作台首页、顶部问号、初始化状态卡和宠物进程气泡打开 `/help/` 下的 VitePress 静态手册，维护稳定章节 ID、同源 URL 和有界上下文问答 prompt；新增手册章节必须同时注册到 `HELP_TOPICS`，保证首页 Help 导航、iframe 和宠物问答共同使用该 Markdown，当前“设置与权限”章节说明普通用户 SSH Key、应用管理员成员/版本库关联/工作空间操作并排除超级管理员专属流程；问答复用 `useSideQuestionRun`，没有主 Session 时保持手册与本地全文搜索可用。
- 首次登录引导在 `FirstLoginGuide.vue` 中复用真实的应用下拉、workspace/version 切换、小地球、对话按钮、宠物、设置和手册锚点；进入设置流程时由 `AgentWorkbench` 按权限打开 `SettingsDialog`，再等待并锚定 `SettingsMenu` 的真实菜单项，分别说明 SSH 配置、应用与版本库配置、应用工作区配置；普通用户不展示应用管理员步骤，引导文案与 `apps/user-manual/docs/guide/` 相关章节同步维护。
- `components/PetMiniGames.vue`：嵌入宠物统一浮层的本地小游戏区域，以紧凑 2×2 小卡片提供可操作的 10×16 俄罗斯方块、8×8 扫雷、9×9 数独和 12×12 贪吃蛇；俄罗斯方块展示下一个方块并随消行提速，扫雷和数独每局随机抽取难度，贪吃蛇随得分提速，扫雷数字格支持在相邻旗子数匹配时双击展开其余邻格。组件只维护页面内存中的棋盘、分数、难度、暂停和输赢状态，不访问后端、不持久化数据，切换或卸载时释放俄罗斯方块、贪吃蛇计时器。
- 小游戏权限：`AgentWorkbench` 仅向 `SUPER_ADMIN` 传递 `canPlayPetGames`，`FigmaShell` 同时隐藏入口并拒绝未授权打开，不能通过旧状态绕过。
- 宠物旁路 fork 可用性由 `AgentWorkbench` 的真实主 `sessionId` 下发；没有历史主 Session 或仅有未发送的新对话草稿时，`FigmaShell` 禁用旁路入口，提交处理层继续保留空 Session 防御。
- 宠物单击按进程状态分流：进程 ready 时打开统一浮层，默认进入对话页；有真实主 Session 时聚焦提问输入框，无 Session 时只禁用对话并允许切换游戏。已分配但未运行时，活动栏入口直接启动并在 READY 后唤出宠物；首次未分配或其它未 ready 状态保留状态气泡作为初始化或不可用说明入口。
- `components/AgentConfigPanel.vue`：左侧 Agent 配置树和 Git 操作面板；公共级展示当前个人 worktree、服务器和物理配置根，完整刷新目录缓存，并仅向父层上报 Agent 文件 scope/path/workspace/worktree/server、真实绝对路径、权限和打开/后台刷新语义，不在子组件读取正文；公共级“更多操作”提供显式创建与切换，创建由后端确保当前用户 `public-{userId}` 稳定分支/worktree，不能任意命名或挂载他人 worktree；公共脏仓库保持可浏览；worktree publish 合并冲突时展示后端 `details.conflictFiles`；工作空间 `+` 初始化符合 opencode `SKILL.md` frontmatter 约定的独立应用技能包；公共级 worktree 创建/切换只更新 `worktreeId/linuxServerId` 上下文，文件列表/读取/写入继续通过 backend-api 的 Agent 配置文件 WebSocket RPC。
- `components/agentFileLoad.ts`：`AgentFileLoadRequest` 内部契约，集中描述 Agent 文件路由上下文、真实绝对路径、readonly 和打开/后台刷新语义；不包含正文，不改变 backend-api 或 WebSocket wire。
- `components/GitChangesPanel.vue`：Git 变更用三个短标签 `workspace` / `应用Agent` / `公共Agent` 分段切换，单次只展示一个作用域，避免普通 docs/spec 与 Agent 配置混在同一棵树；应用工作空间保留真实 stage/unstage、spec 仅本地发布白名单、远端 push 确认和冲突编排，Agent 作用域复用现有 stage/unstage/discard/提交/发布 API，提交按钮只处理当前作用域。两类 Agent 文件支持逐个和批量回退，操作后通知工作台以磁盘结果刷新已打开 tab；unmerged 文件禁用普通回退，仍走既有合并编辑器。`FigmaFileExplorer` 在进入变更面板时立即调用其既有 `refreshChanges`，并仅在面板可见期间每 5 秒继续调用，补齐保存修订信号之外的磁盘变化感知。面板加载后三个作用域的文件数会汇总回传给外层“变更”入口，spec、应用 Agent 和公共 Agent 均计入总量，当前 Tab 与发布白名单不影响角标。标签下方继续显示普通文件、应用 `.opencode` 或公共 `opencode` 的作用域说明。冲突期间允许普通文件调整 index 但禁止提交，文件行提供完整路径提示并把已加载 patch 直接交给工作台；冲突内容由 backend-api 读取，复用 `diff-viewer/MergeConflictEditor` 完成三方编辑、保存解决结果和取消 merge。
- `GitChangesPanel.vue` 的普通 workspace diff 会剔除 `.opencode` 路径，避免同一个人 worktree 中的应用 Agent 配置重复出现在 workspace；“应用Agent”通过 Agent diff 精确读取同一 Git 根的 `.opencode/opencode.jsonc`、`.opencode/agents/**` 和 `.opencode/skills/**`，其它 `.opencode` 文件不进入该作用域，提交与发布复用个人 worktree 的按路径提交和 feature 投影；公共 `opencode` 仍只从公共个人 worktree进入“公共Agent”。
- `components/FigmaChatPanel.vue`：Figma 风格右侧对话面板，按顺序渲染完整历史用户/助手消息；新一轮运行开始时清理上一轮完成/失败/手动终止提示，避免旧终态覆盖当前结果；只有明确成功的根 Run 才把当前状态移到最后一个 assistant 输出下方并收为图标，在同一摘要行展示一次满意/不满意入口，文件修改块继续固定在输入框上方，失败、取消、停止、重试、运行中、无状态和子 Agent 视图均隐藏反馈；header 提供“原始输出”悬浮窗，使用 `<pre>` 展示前端捕获的请求体、响应体和 SSE `data` 原文，支持拖动、CSS resize、筛选和清空当前会话记录；页面复制动作统一复用 ui-kit 的 Clipboard API + Chromium 108 HTTP 回退；历史按钮展示用户级运行中计数、旋转图标和待回答铃铛，历史卡片按会话运行态展示 Spinner、已完成图标和待答铃铛；`question.asked` / `permission.asked` 归并出的待处理请求固定展示在输入框上方，普通 assistant 编号列表不触发提问面板；历史子 Agent task 卡片必须先由 session tree 或 task `<task_result>` 恢复出 `subagentsBySessionId` 索引，点击后才进入对应 child scope，避免 metadata-only 卡片进入空视图；`question.asked` 面板按单个问题分页展示 `1/N 个问题`、选项 label/description 和“输入自己的答案”，单选自定义答案与选项互斥，多选自定义答案作为附加答案，提交时按问题顺序 emit `answers: string[][]`；当前轮非成功工作状态固定在原 Todo 区域并位于输入框上方，初始只显示思考行，出现事件后增加事件图标行，Todo 作为可展开的附加行合并进状态块；历史轮状态默认收起为最后一条 assistant 消息下方的单个图标，且一次只展开一个历史状态；工作区上下文附件固定展示在输入框上方，支持预览、删除、清空和超限发送拦截，超过 3 个时默认折叠并显示阶段数量摘要，展开区最高 220px 可滚动；输入卡片底部提供受控 Agent 选择，按 `primary/all` 且非 hidden 过滤主运行 Agent；输入卡片使用极简边框，无投影；输入框 `@` 同时展示按 `subagent/all` 且非 hidden 过滤的 Agent 与当前 worktree 文件，完整文件名作为主信息、完整相对目录作为低强调次信息，均允许换行且选择复用现有上下文附件动作；输入框 `#` 按需求项和同名子条目聚合 `01-需求、02-设计、03-编码、04-测试` 下的全部关联文件，候选仅显示需求项/子条目/文件数，长名称自然换行且不展开文件清单，选择后再把全部关联文件加入上下文；输入框处理输入法组合输入，避免候选词确认 Enter 被误识别为发送；输入区只消费工作台传入的 opencode 健康 ready 状态，不主动触发 `/processes/me` 强状态查询；运行中且进程 ready 时新建对话保持可点；工作台以宠物承载进程状态，传入 `processStatusPlacement="pet"` 时隐藏聊天区旧进程点/卡片但仍保留发送拦截；上传附件按钮当前只打开前端样式弹窗，不接后台上传。
- `FigmaChatPanel` 会话列表交互：主对话不再显示“对话 / 待执行任务”页签；header 的“会话列表”通过 Teleport 在对话栏左侧展示最大 360px 的非模态浮层，窄屏退化为视口内覆盖。浮层内部提供“会话 / 待执行任务”页签，选择会话或任务后保持打开并高亮当前会话；再次点击 header 入口、关闭按钮、Esc 或 `panelVisible=false` 时关闭。
- `FigmaChatPanel` 夜间交互：定时图标固定在发送按钮左侧，弹层只选择 15 分钟启动时段、不显示执行位置；会话列表浮层的“待执行任务”页签展示全局待执行列表及创建时间，当前任务卡支持改期和内联确认取消。当前会话锁定时输入/发送/定时按钮禁用但“新建对话”保持可用；最终失败卡可关闭且不继续锁定输入。
- `AgentWorkbench` 的会话级原始输出缓存只保留每个 Session 最新 2000 条；缓存继续在页面刷新后清空，写入前沿用递归 `contextToken` 脱敏与正文截断。
- `components/GitChangesPanel.vue`：应用工作区真实 stage/unstage、发布白名单、远端 push 确认和冲突编排；冲突期间允许普通文件调整 index 但禁止提交，文件行提供完整路径提示并把已加载 patch 直接交给工作台；冲突内容由 backend-api 读取，复用 `diff-viewer/MergeConflictEditor` 完成三方编辑、保存解决结果和取消 merge。
- `components/FigmaChatPanel.vue`：Figma 风格右侧对话面板，按顺序渲染完整历史用户/助手消息；新一轮运行开始时清理上一轮完成/失败/手动终止提示，避免旧终态覆盖当前结果；只有明确成功的根 Run 才把当前状态移到最后一个 assistant 输出下方并收为图标，在同一摘要行展示一次满意/不满意入口，文件修改块继续固定在输入框上方，失败、取消、停止、重试、运行中、无状态和子 Agent 视图均隐藏反馈；header 提供“原始输出”悬浮窗，使用 `<pre>` 展示前端捕获的请求体、响应体和 SSE `data` 原文，支持拖动、CSS resize、筛选和清空当前会话记录；页面复制动作统一复用 ui-kit 的 Clipboard API + Chromium 108 HTTP 回退；历史按钮展示用户级运行中计数、旋转图标和待回答铃铛，历史卡片按会话运行态展示 Spinner、已完成图标和待答铃铛；`question.asked` / `permission.asked` 归并出的待处理请求固定展示在输入框上方，普通 assistant 编号列表不触发提问面板；历史子 Agent task 卡片必须先由 session tree 或 task `<task_result>` 恢复出 `subagentsBySessionId` 索引，点击后才进入对应 child scope，避免 metadata-only 卡片进入空视图；`question.asked` 面板按单个问题分页展示 `1/N 个问题`、选项 label/description 和“输入自己的答案”，单选自定义答案与选项互斥，多选自定义答案作为附加答案，提交时按问题顺序 emit `answers: string[][]`；当前轮非成功工作状态固定在原 Todo 区域并位于输入框上方，初始只显示思考行，出现事件后增加事件图标行，Todo 作为可展开的附加行合并进状态块；历史轮状态默认收起为最后一条 assistant 消息下方的单个图标，展开后按钮与反馈入口保持在摘要行、状态块显示在其下方，再次点击同一按钮收起，且一次只展开一个历史状态；工作区上下文附件固定展示在输入框上方，支持预览、删除、清空和超限发送拦截，超过 3 个时默认折叠并显示阶段数量摘要，展开区最高 220px 可滚动；输入卡片底部提供受控 Agent 选择，按 `primary/all` 且非 hidden 过滤主运行 Agent；输入卡片使用极简边框，无投影；输入框 `@` 同时展示按 `subagent/all` 且非 hidden 过滤的 Agent 与当前 worktree 文件，完整文件名作为主信息、完整相对目录作为低强调次信息，均允许换行且选择复用现有上下文附件动作；输入框 `#` 按需求项和同名子条目聚合 `01-需求、02-设计、03-编码、04-测试` 下的全部关联文件，候选仅显示需求项/子条目/文件数，长名称自然换行且不展开文件清单，选择后再把全部关联文件加入上下文；输入框处理输入法组合输入，避免候选词确认 Enter 被误识别为发送；输入区只消费工作台传入的 opencode 健康 ready 状态，不主动触发 `/processes/me` 强状态查询；运行中且进程 ready 时新建对话保持可点；工作台以宠物承载进程状态，传入 `processStatusPlacement="pet"` 时隐藏聊天区旧进程点/卡片但仍保留发送拦截；上传附件按钮当前只打开前端样式弹窗，不接后台上传。
- `FigmaChatPanel` 的旧内联进程状态卡在展示时把初始化按钮和上下文手册问号放在同一操作区；两者分别向工作台发出初始化和 `process-initialization` 章节事件，帮助入口不会折叠或拖动状态卡。
- `stores/chatContextStore.ts`：管理工作区上下文附件，包含 selection/file 类型、字符数限制、添加/删除/清空、总量统计、发送前校验和前端 prompt 序列化。
- 对话可输入条件：主输入卡不再依赖 Session 选择态；进程 ready 且不存在历史切换锁、只读或上下文校验错误时，可以直接输入首条消息，`AgentWorkbench` 在发送时延迟创建 Session。“新建对话”只负责主动清空当前上下文；宠物旁路仍复用真实 `sessionId` 单独设防。
- `components/ChatContextAttachmentList.vue` / `ChatContextAttachmentCard.vue` / `ChatContextPreviewDrawer.vue`：输入框上方的上下文附件展示、单卡片删除和只读预览抽屉。
- `components/settings/SettingsDialog.vue`：左下角设置模态，组合应用人员、版本库管理、版本库关联、应用工作空间和个人 SSH key 配置管理；无应用配置权限时展示当前角色无权限提示；"应用人员管理" tab 用 `el-autocomplete` 懒加载搜索候选用户（userId/unifiedAuthId/username LIKE 匹配，空输入不查后端），选中后主按钮从"搜索"切换为"添加"；"工作空间管理" tab 的创建区只允许从已关联测试工作库中选择版本库，随后自动刷新分支、加载目录并创建工作空间，创建时生成 `operationId` 并轮询后端进度接口；移除应用成员、解除应用与版本库关联前必须弹出页面内 div 确认框。
- `utils/ssh-crypto.ts`：个人 SSH key 浏览器端混合加密工具，按后端契约生成 AES-GCM 私钥密文、RSA-OAEP/SHA-256 临时 AES 密钥密文和 SHA-256 指纹；优先使用 Web Crypto，企业内 Chromium 108 的 HTTP 内网访问缺少 `crypto.subtle` 时使用 node-forge 纯 JS 回退；如果 `getRandomValues` 不可用则返回明确错误，不允许明文降级。
- `components/SystemManagementWrapper.vue`、`components/system/SystemManagementPanel.vue`：超级管理员系统管理入口和二级导航，包含定时任务管理与运行管理。
- `components/system/ScheduledTaskManagementPanel.vue`：定时任务管理页，展示任务定义、当前/最近执行状态和历史执行记录，支持启停、Cron 编辑、手工启动和停止 `RUNNING` 运行记录。
- `components/EditorPane.vue`、`ReadonlyTranscript.vue`：编辑器 tab 壳和只读 transcript 视图（不订阅 SSE，不直连 opencode）。
- `components/follow-up-queue.ts`：Run 忙碌时 prompt follow-up 的纯 FIFO 队列模型。
- `components/prompt-context.ts`：活动编辑器或 Monaco 选区到 `PromptPart` file context 的纯转换。
- `components/workbench-utils.ts`：Diff payload 解析、RunEvent 三态投影门禁、session-tree 分轮 Todo 历史恢复、session Todo 保守校准、history/runtime status 派生、子 Agent 索引兜底、弱健康 ready、命令解析、跨阶段子条目路径聚合和工作空间根目录过滤等纯函数。
- `styles/globals.css`：Tailwind 4 全局入口、theme token、dockview-vue/Monaco 视觉适配、滚动条、panel chrome 和工作台级动画。
- `../vite.config.ts`：Vite 应用配置（Vue 插件、Tailwind 插件、workspace alias、dev server）。
- `AgentWorkbench.handleSend` 继续把完整 text/file parts 交给 Run 请求；本地乐观 user message 只接收经 `promptPartsForUserDisplay` 收敛后的文本和附件元数据。历史 session-tree 恢复会预先按 `sessionId + messageId` 建立首个非 synthetic text 索引，仅补齐同 Session、无正文的 OpenCode user envelope；`events` 与 `messagesBySessionId` 两次回放复用该索引后再交给原 reducer 归并 file part，避免后续用户文本落入上一条 assistant，且不改变 Timeline、实时事件投影和 OpenCode parts 协议。

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
