# @test-agent/agent-web

## 工程定位

Vue 3 + Vite SPA 主应用，组合 Web IDE 工作台、文件树、Monaco 编辑器、Agent 对话、Run 面板和 Diff 查看。

## 主要职责

- 提供 `/login` 登录页、`/` 工作台页面和 `/s/[sessionId]` 只读 transcript 页面（vue-router 客户端路由），未知路径回退到工作台。
- 组合 dockview-vue 三栏布局和底部运行面板。
- 组合 Figma Web IDE 风格的 40px 顶栏、48px activity rail、紧凑左侧文件面板、中间编辑器和右侧 Agent 面板；Run/Terminal 默认隐藏在底部抽屉中，通过 activity rail 打开。
- 通过 `@tanstack/vue-query` 调用 `@test-agent/backend-api`。
- 顶栏「打开文件夹」按钮触发的 Workspace 目录选择弹窗，复用已有 `POST /api/workspaces` 创建并切换，切换时清空旧文件树、编辑器、Diff、Session/Run、队列和聊天运行态。
- 通过 `@test-agent/event-stream-client` 订阅 RunEvent SSE。
- 用 Pinia 工作台状态管理打开文件、活动 tab 和 Diff 选择。
- 承载"实时追踪"行为：用户开启后，运行中的 `message.part.updated`/`diff.proposed` 会打开最近变化文件、刷新只读预览，并把 `files[].additions/deletions` 传给文件树展示行数。`workbench-utils.inferDiffFromToolPart` 在 opencode 1.17.8 `session.diff` 事件 diff 字段为空时，基于 `write`/`edit`/`apply_patch`/`str_replace`/`multi_edit`/`create_file` 工具的 input 估算新增/删除行数并补齐 `RunDiffFile`，让"文件变更卡片 +N"在写盘后即时刷新；该推断与"实时追踪 toggle"解耦，关闭实时追踪时仍会同步 diffFiles 和文件树，但不再打开只读预览 tab。
- 组合 Session History 搜索、切换、置顶和软删除。
- 右上角应用菜单优先展示当前用户加入的应用；管理员未加入任何应用时回退展示启用应用，切换应用后优先切到应用最近使用工作区，没有最近工作区时清空旧文件树并提示选择工作空间；应用名旁边提供用户头像菜单，可退出登录并切换用户，头像菜单每次打开都会实时查询 `/api/internal/agent/opencode/processes/me` 并用灰/绿/红展示“未分配 / 运行中(服务器ip:端口) / 未运行(服务器ip:端口)”；未选择应用时不自动读取普通工作区文件树，避免应用与左侧工作区错位；下拉菜单顶部以灰显行展示当前用户的角色中文名（来自 `/api/auth/me` 的 `roleLabels`，链路 `users` → `user_roles` → `dictionaries.dict_label`），缺失时整行不渲染。
- 左侧文件树上方提供应用工作空间模板、版本和个人空间切换，支持新增版本、创建个人工作区、查看个人/应用差异以及双向同步。
- 工作台左下角"应用工作空间"两级菜单：第一级只显示模板 `workspaceName`（不展示 `directoryPath` / `branch`），hover 模板触发 `GET .../versions` 懒加载子菜单（`directoryPath` / `branch` 也不再展示）。子菜单底部固定一行「+新增版本」，点击后弹 el-dialog（`ElDatePicker` `type=month` / `format=yyyy年M月` / `value-format=yyyy年M月`），确认后调 `POST .../versions` 透传 `yyyy年M月` 字符串，成功后失效 `versionsByTemplateId` 缓存并把新版本切到工作区。
- 工作区文件树加载、打开文件、保存、文件状态、删除和实时预览读取均通过 `@test-agent/backend-api` 的目标后端文件 WebSocket client 完成；切换工作区会关闭旧连接并重新路由到当前用户 opencode 进程同服务器后端。文件 WebSocket 路由只依赖后端返回的用户进程服务器归属，不会触发 opencode-manager health/start 命令；发送消息、初始化进程和头像进程状态仍按后端强健康检查结果控制。
- 左侧文件区新增 `Agent` tab，与工作区/公共目录平级；固定展示“公共级”和“工作空间级”两个根目录。普通用户只读打开 agent 文件，`SUPER_ADMIN` 可更新公共配置、创建 worktree、切换公共 worktree、编辑文件、查看 Agent Git diff、双击文件 stage、提交和发布；公共级切换先选择已初始化服务器，再选择“直接公共配置目录”或该服务器上的 `ACTIVE` worktree，切换后只清空公共文件树缓存并改变 `worktreeId/linuxServerId` 上下文。Agent 配置文件目录列表、读取和写入均通过 backend-api 的 agent-config 文件 WebSocket route/ticket/RPC，公共直接模式会选择已初始化公共配置服务器并把 `linuxServerId` 随打开的 tab 保存，worktree 模式按落库服务器路由；已打开 tab 不随切换关闭，保存时沿用 tab 内的上下文。创建公共 worktree 弹窗会实时读取远端分支和在线服务器公共配置仓库状态，只允许选择已初始化服务器并提交 `linuxServerId`，无已初始化服务器时禁用确认并引导到系统管理初始化。Git 长操作通过 `agent-config` ticket WebSocket 展示进度，不走 RunEvent SSE。Git 远端失败时优先展示后端 `gitFailureHint` 和 `traceId`，不把原始 stderr、完整命令或内部路径直接展示到 UI。
- `SUPER_ADMIN` 用户在文件区底部工作空间切换控件右侧可见服务器切换图标按钮；点击后先选择后端服务器 IP，再通过目标后端 WebSocket 从该 Java 进程运行目录开始浏览目录并添加工作空间。选中服务器与当前 agent 不同服务器时，目录输入和确认按钮禁用，并提示“工作空间与 agent 不在同一服务器”。
- 点击历史会话时会按 `session.workspaceId` 切换运行态 Workspace；目标工作区不可用或无权限时保留 transcript 并禁用输入，作为只读会话展示。
- 组合运行态能力：Agent/Model/Mode 选择、按 Provider 分组的模型选择面板、slash command、`@` context、MCP/LSP/VCS 状态、Run/Session/VCS Diff 来源切换。
- 登录进入工作台后独立查询当前用户 opencode 进程状态；状态非 `READY` 时禁用发送、新建对话和 busy follow-up 出队，并在右侧对话面板展示初始化按钮或不可用原因。右侧输入卡片被点击或输入框获得焦点时会通过既有 `/processes/me` 查询重新探测进程状态，刷新中保持输入可编辑但暂时禁用发送和新建对话，避免旧的 `READY` 缓存放行已退出进程。初始化动作只调用平台后端 `initializeMyOpencodeProcess()`，不直连 opencode server 或容器管理进程。
  - 当状态为 `UNAVAILABLE` 且后端返回 `bindingClearable=true`（即用户绑定指向的 Linux 服务器已无可用容器，但本地仍有可路由的固定 `execution_node` 节点）时，右侧面板会额外显示"重置绑定以使用本地 opencode"按钮，调用 `clearMyOpencodeProcessBinding()` 删除 `user_opencode_process_bindings` 中指向已下线 Linux 服务器的脏绑定；后端会再走一次 `status()`，命中 `localRoutableNode()` 回退时把 `status=READY, localFallback=true, baseUrl=http://127.0.0.1:4096` 透出，前端据此直接放开对话输入。
- 组合活动编辑器选区上下文和 busy follow-up 本地 FIFO 队列；输入框在输入法组合输入阶段不会把 Enter 当作发送；右侧输入区保留“上传附件”前端弹窗样式，后台上传接口接入前不提交附件；真正提交仍通过 `@test-agent/backend-api`。
- 右侧对话消息区按 Session message 顺序完整渲染历史用户/助手消息；历史 `partsJson` 会复用实时消息归一化规则，把 opencode 原始 `id/tool/state` 字段恢复为统一的 text/tool/file part，生成文档既可在消息内展示 file part，也可从恢复后的“N 个文件已更改”卡片进入 Diff 抽屉。Run Diff 快照缺失时会从历史 write/edit/apply_patch tool part 推断文件列表兜底，避免切换历史会话后文档入口消失。
- 持久化的 assistant 消息下方展示“满意 / 不满意”反馈入口；满意直接提交，负反馈弹出原因与 300 字备注输入。反馈状态按当前用户查询，不展示或上传 prompt/assistant 原文。
- 新建 Session 的标题直接取第一次发送消息的去首尾空白内容，右侧标题和历史列表沿用后端返回的该标题，不再生成 `Agent HH:mm:ss` 临时标题。
- 中间编辑器 tab 表头最右侧在打开 Markdown 文件时显示"预览/关闭预览"按钮（Eye/EyeOff 图标），状态受控绑定到 `CodeEditor` 的 `showPreview` prop：开启后在编辑器下方追加 Markdown 预览分屏（懒加载 markdown-it + DOMPurify + highlight.js），关闭后回到全屏编辑；切换非 md 文件时由 `AgentWorkbench` 的 `activePath` 监听器自动复位，避免下次切回 md 时残留开启状态。CodeEditor 不再自带预览开关 UI，避免出现"两处入口状态不同步"。
- 底部 footer 移除「选择分支」「记住当前分支」两个 VCS 分支入口及对应的两级菜单、`getRecentBranch` / `markRecentBranch` 偏好持久化与 `loadBranchPreferenceOnEnter` 提示逻辑；分支信息仍由 `runtimeStatus` 从 `vcs.status` 拉取并展示在右侧 Agent 面板的运行态能力区，不影响运行态判定。
- Ctrl/Cmd+S 全局保存快捷键：编辑器内由 `CodeEditor` 通过 `editor.addCommand(KeyMod.CtrlCmd | KeyCode.KeyS, …)` 注册一层（仅非只读文件生效），向外 emit `save`；编辑器外由 `AgentWorkbench` 在 `window` 上挂 `keydown` 监听，识别到 `.monaco-editor` 容器后跳过避免重复触发，在其他位置（含 tab 表头、文件树、activity rail）按 Ctrl/Cmd+S 时 preventDefault 浏览器默认「保存网页」并执行与右下角保存按钮完全一致的 `saveMutation.mutate` 逻辑；可执行条件（activeTab 存在、文件 dirty、非 livePreview、非只读、未在保存中）任意一项不满足时只吞掉浏览器默认行为、不发起请求，避免产生半截保存请求。
- 右侧对话面板的“任务消耗”行展示 `duration / tokens / thought for`：duration 取 `chatStartedAt` 实时计算并在 Run 结束后锁定；tokens 累计助手消息中 `step-finish` part 的 `tokens.total`（来自 `message.part.updated`）；thought for 累计 reasoning part 的 `durationMs`；如 `run.*` 终态事件 payload 直接带上 `tokens` 或 `thoughtFor` 字段则优先采用以保持向后兼容。
- 右侧对话面板的"N 个文件已更改"提示点击后弹出本地抽屉：左侧是变更文件列表（每项含新增/修改/删除状态和 +/- 行数徽标），右侧按 unified diff 逐行渲染 git-merge 风格（`+` 绿底、`-` 红底、hunk header 灰条），默认选中第一个文件，支持点击切换、Esc 关闭和点击遮罩关闭；事件未携带 patch 文本时降级为只显示状态徽标。抽屉头部新增"仅变更 / 上下文"切换按钮，默认仅展示 `+`/`-` 行（隐藏 unified diff 的 ctx 上下文行），避免后端 patch 是整文件重写时出现"只改一行但全文飘红"的体验；切换为"上下文"模式可恢复 git 风格的完整上下文对比。`scanLiveToolParts` 在每次写文件工具（`write` / `edit` / `apply_patch` / `str_replace` / `multi_edit` / `create_file`）完成时由 `chatState.value.messages` 的 deep watch 触发，**不论走 assistant message 的 part 路径还是独立的 tool card 路径**都会调用 `expandPathToFile(path)` 把所有祖先目录加入 `expandedDirectories` 并懒加载，再调 `refreshParentDirectory(path)` 走 `loadDirectory(parent, undefined, true)` 强制重拉父目录条目录入新文件；正在加载中的目录由 `loadDirectory` 内部的 `loadingPath` 守卫去重，避免对同一目录堆积并发请求。"应用工作空间"标题栏上的手动刷新按钮同样走 `loadDirectory(path, workspaceId, force=true)`。
- 写文件工具完成时由 `workbench-utils.inferDiffFromToolPart` 基于 `input.content` / `oldString` / `newString` / `patchText` 合成 unified diff 文本，让"文件变更"抽屉对新文件也能渲染 +N 行的内容视图（`write` 全行 +、`edit` 旧/新行 +/-、`apply_patch` 透传原始 patch），不再展示"暂无 diff 内容"空态。
- `workbench-utils.mergeDiffFiles` 用 `normalizePathKey`（折叠 `\` 与 `/`、去 git a/b 前缀、大小写不敏感）做去重 key，配合 AgentWorkbench 的 `normalizeWorkspacePath`（剥离 workspace 根路径），让 `D:\workspace\vue\src\App.vue` 与 `src/App.vue` 落到同一行，"X 个文件已更改"不会因路径形态差异被错算成两份。
- 组合底部 PTY terminal panel；ticket 创建走 `@test-agent/backend-api`，WebSocket 生命周期由 `@test-agent/terminal` 管理。
- 在 activity rail 左下角提供设置模态入口；`APP_ADMIN` 或 `SUPER_ADMIN` 用户可管理应用人员、版本库管理、应用与版本库关联和应用工作空间，其他用户可看到“应用与工作空间管理”菜单但只显示当前角色无权限提示，所有登录用户可管理自己的 SSH key。
- activity rail 中仅 `SUPER_ADMIN` 显示“系统管理”入口；进入后右侧一级导航包含“定时任务管理”、“运行管理”、“通用参数管理”、“配置管理”和“运营分析”，其中“配置管理”当前二级菜单先提供“opencode公共配置管理”，展示所有在线后端服务器上的公共配置 Git 根目录、opencode 配置目录、worktree 根目录、初始化状态、分支和 commit，并可用当前管理员 SSH key 对允许初始化的服务器执行初始化。定时任务管理通过 scheduler API 展示任务定义、当前/最近执行状态和历史运行记录，支持刷新、启停、Cron 编辑、手工启动非 active 任务和停止 `RUNNING` 运行记录；运行管理复用 `getOpencodeRuntimeManagementOverview()` 展示“服务器 / Java 进程”合并表、“容器 / 管理进程”合并表、`Java -> Manager -> opencode server` 节点连线拓扑图，并提供筛选、手动刷新和 5 秒自动刷新；底部“用户 opencode server 进程”不再默认展示全部进程，必须输入用户名、`userId` 或统一认证号后调用 `getOpencodeRuntimeManagementUserProcesses()` 查询，列表展示数据库状态、manager/PID 状态、opencode 健康状态、健康消息和绑定信息，`restartable=true` 的未运行或健康失败进程可直接调用 `restartOpencodeRuntimeManagedProcess()` 重启；服务器合并表按 `linuxServerId` 关联 Linux 服务器与后端 Java 进程，保留服务器状态、Java 状态、CPU、内存、磁盘、JVM、心跳和“趋势”操作，异常缺任一侧时显示 `-`；容器合并表按当前“一容器一 manager”架构以 `containerId` 关联，行点击或 Enter 展开后按 `ownership=BOUND` 展示“有主进程”、其余展示“无主进程”，展示启动时间、端口、PID、baseUrl、启动命令、用户/绑定和健康信息，缺失字段显示 `-`，容量计数与明细数量不一致时提示计数来源差异；有主/无主明细行末提供“重启”“停止”按钮，分别调用 `restartOpencodeRuntimeManagedProcess()` / `stopOpencodeRuntimeManagedProcess()`，重启成功后刷新 overview，停止成功后先从当前 overview 缓存局部移除对应端口；拓扑图从 `backendProcesses`、`managerBackendConnections` 和 `managers[].managedProcesses[]` 派生 backend、manager、有主/无主 opencode 节点及连线，支持缩放、拖拽、hover tooltip 和点击节点高亮相邻关系，旧响应缺少 `managedProcesses` 时仍展示 manager 节点；来源列悬浮说明 `cgroup`、`process`、`不可采集` 和 `-` 的含义；后端 Java 趋势通过服务器合并表行点击或“趋势”按钮打开，容器趋势通过容器合并表行内“趋势”按钮打开，后端行点击后调用 Redis 指标历史 API 并按需加载 ECharts line chart，后端图表明确区分“服务器 CPU/内存/磁盘”和“当前进程 JVM”趋势，支持 1 分钟、30 分钟、1 小时、6 小时、12 小时、24 小时、48 小时趋势窗口，缺失字段显示 `-` 并在图表中保留断点；通用参数管理通过 `/configuration-management/common-parameters` API 展示 `common_parameters` 全量参数，支持 `windows/linux/macos/all` 平台筛选，选择平台后立即刷新，支持分页和行内编辑参数值后保存，**仅可修改 value，不可新增或删除参数**；运营分析通过 `/analytics/**` API 展示用户漏斗、使用强度、Run 结果、满意度、Diff 采纳、token 使用、趋势、热力、组织/用户排行、反馈明细和异常 Run 明细，支持筛选与 CSV 导出，页面不出现成本/费用/花费字段。
- 设置模态"应用人员管理"tab 的"添加成员"区使用 `el-autocomplete` 懒加载搜索：`trigger-on-focus="false"`，初始进入/聚焦输入框都不查后端；只有键入内容时（Element Plus 自带 300ms 防抖）才异步触发 `/configuration-management/users?keyword=`，下拉展示按 `userId` / `unifiedAuthId` / `username` 任一字段大小写不敏感 LIKE 命中的候选（每项单行展示 `userId · userName`）；选中后主按钮文案由"搜索"切换为"添加"，再点击即把该用户加入当前应用并刷新成员列表；"搜索"按钮在空输入时禁用，作为精确 userId 单条命中场景的兜底。
- 设置模态"工作空间管理"tab 的版本库新增/编辑表单要求填写“版本库英文名称”，提交前校验 1 到 29 位英文字母并转小写；历史版本库缺少英文名时列表显示“未配置英文名”。
- 设置模态"工作空间管理"tab 的"创建工作空间"区按三步展示：第一步刷新分支、第二步加载目录、第三步创建工作空间；标准版本库从所选 `feature_testagent_yyyyMMdd` 分支解析版本，非标准版本库额外展示 `yyyyMMdd` 版本输入框；创建时生成 `operationId` 并轮询 `workspace-create-operations` 展示“校验、保存配置、解析版本、下载代码、创建运行态工作区、完成/失败”等后端步骤。所有输入项必须有可见标签。
- 设置模态里的移除应用成员、解除应用与版本库关联属于破坏性操作，前端必须先展示页面内 div 确认框，再调用 backend-api，禁止使用浏览器原生确认模态框。
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
