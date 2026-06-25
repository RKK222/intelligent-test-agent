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
- 右上角应用菜单优先展示当前用户加入的应用；管理员未加入任何应用时回退展示启用应用，切换应用后优先切到应用最近使用工作区，没有最近工作区时清空旧文件树并提示选择工作空间；应用名旁边提供用户头像菜单，可退出登录并切换用户；未选择应用时不自动读取普通工作区文件树，避免应用与左侧工作区错位；下拉菜单顶部以灰显行展示当前用户的角色中文名（来自 `/api/auth/me` 的 `roleLabels`，链路 `users` → `user_roles` → `dictionaries.dict_label`），缺失时整行不渲染。
- 左侧文件树上方提供应用工作空间模板、版本和个人空间切换，支持新增版本、创建个人工作区、查看个人/应用差异以及双向同步。
- 工作台左下角"应用工作空间"两级菜单：第一级只显示模板 `workspaceName`（不展示 `directoryPath` / `branch`），hover 模板触发 `GET .../versions` 懒加载子菜单（`directoryPath` / `branch` 也不再展示）。子菜单底部固定一行「+新增版本」，点击后弹 el-dialog（`ElDatePicker` `type=month` / `format=yyyy年M月` / `value-format=yyyy年M月`），确认后调 `POST .../versions` 透传 `yyyy年M月` 字符串，成功后失效 `versionsByTemplateId` 缓存并把新版本切到工作区。
- 点击历史会话时会按 `session.workspaceId` 切换运行态 Workspace；目标工作区不可用或无权限时保留 transcript 并禁用输入，作为只读会话展示。
- 组合运行态能力：Agent/Model/Mode 选择、按 Provider 分组的模型选择面板、slash command、`@` context、MCP/LSP/VCS 状态、Run/Session/VCS Diff 来源切换。
- 登录进入工作台后独立查询当前用户 opencode 进程状态；状态非 `READY` 时禁用发送、新建对话和 busy follow-up 出队，并在右侧对话面板展示初始化按钮或不可用原因。初始化动作只调用平台后端 `initializeMyOpencodeProcess()`，不直连 opencode server 或容器管理进程。
  - 当状态为 `UNAVAILABLE` 且后端返回 `bindingClearable=true`（即用户绑定指向的 Linux 服务器已无可用容器，但本地仍有可路由的固定 `execution_node` 节点）时，右侧面板会额外显示"重置绑定以使用本地 opencode"按钮，调用 `clearMyOpencodeProcessBinding()` 删除 `user_opencode_process_bindings` 中指向已下线 Linux 服务器的脏绑定；后端会再走一次 `status()`，命中 `localRoutableNode()` 回退时把 `status=READY, localFallback=true, baseUrl=http://127.0.0.1:4096` 透出，前端据此直接放开对话输入。
- 组合 prompt 文件/图片附件、活动编辑器选区上下文和 busy follow-up 本地 FIFO 队列；真正提交仍通过 `@test-agent/backend-api`。
- 中间编辑器 tab 表头最右侧在打开 Markdown 文件时显示"预览/关闭预览"按钮（Eye/EyeOff 图标），状态受控绑定到 `CodeEditor` 的 `showPreview` prop：开启后在编辑器下方追加 Markdown 预览分屏（懒加载 markdown-it + DOMPurify + highlight.js），关闭后回到全屏编辑；切换非 md 文件时由 `AgentWorkbench` 的 `activePath` 监听器自动复位，避免下次切回 md 时残留开启状态。CodeEditor 不再自带预览开关 UI，避免出现"两处入口状态不同步"。
- 底部 footer 移除「选择分支」「记住当前分支」两个 VCS 分支入口及对应的两级菜单、`getRecentBranch` / `markRecentBranch` 偏好持久化与 `loadBranchPreferenceOnEnter` 提示逻辑；分支信息仍由 `runtimeStatus` 从 `vcs.status` 拉取并展示在右侧 Agent 面板的运行态能力区，不影响运行态判定。
- Ctrl/Cmd+S 全局保存快捷键：编辑器内由 `CodeEditor` 通过 `editor.addCommand(KeyMod.CtrlCmd | KeyCode.KeyS, …)` 注册一层（仅非只读文件生效），向外 emit `save`；编辑器外由 `AgentWorkbench` 在 `window` 上挂 `keydown` 监听，识别到 `.monaco-editor` 容器后跳过避免重复触发，在其他位置（含 tab 表头、文件树、activity rail）按 Ctrl/Cmd+S 时 preventDefault 浏览器默认「保存网页」并执行与右下角保存按钮完全一致的 `saveMutation.mutate` 逻辑；可执行条件（activeTab 存在、文件 dirty、非 livePreview、非只读、未在保存中）任意一项不满足时只吞掉浏览器默认行为、不发起请求，避免产生半截保存请求。
- 右侧对话面板的“任务消耗”行展示 `duration / tokens / thought for`：duration 取 `chatStartedAt` 实时计算并在 Run 结束后锁定；tokens 累计助手消息中 `step-finish` part 的 `tokens.total`（来自 `message.part.updated`）；thought for 累计 reasoning part 的 `durationMs`；如 `run.*` 终态事件 payload 直接带上 `tokens` 或 `thoughtFor` 字段则优先采用以保持向后兼容。
- 右侧对话面板的"N 个文件已更改"提示点击后弹出本地抽屉：左侧是变更文件列表（每项含新增/修改/删除状态和 +/- 行数徽标），右侧按 unified diff 逐行渲染 git-merge 风格（`+` 绿底、`-` 红底、hunk header 灰条），默认选中第一个文件，支持点击切换、Esc 关闭和点击遮罩关闭；事件未携带 patch 文本时降级为只显示状态徽标。抽屉头部新增"仅变更 / 上下文"切换按钮，默认仅展示 `+`/`-` 行（隐藏 unified diff 的 ctx 上下文行），避免后端 patch 是整文件重写时出现"只改一行但全文飘红"的体验；切换为"上下文"模式可恢复 git 风格的完整上下文对比。`refreshParentDirectory` 在每次写文件工具完成时主动 `loadDirectory(父目录)`，让做测工作目录即时出现新文件而无需用户手动点刷新。
- 写文件工具完成时由 `workbench-utils.inferDiffFromToolPart` 基于 `input.content` / `oldString` / `newString` / `patchText` 合成 unified diff 文本，让"文件变更"抽屉对新文件也能渲染 +N 行的内容视图（`write` 全行 +、`edit` 旧/新行 +/-、`apply_patch` 透传原始 patch），不再展示"暂无 diff 内容"空态。
- `workbench-utils.mergeDiffFiles` 用 `normalizePathKey`（折叠 `\` 与 `/`、去 git a/b 前缀、大小写不敏感）做去重 key，配合 AgentWorkbench 的 `normalizeWorkspacePath`（剥离 workspace 根路径），让 `D:\workspace\vue\src\App.vue` 与 `src/App.vue` 落到同一行，"X 个文件已更改"不会因路径形态差异被错算成两份。
- 组合底部 PTY terminal panel；ticket 创建走 `@test-agent/backend-api`，WebSocket 生命周期由 `@test-agent/terminal` 管理。
- 在 activity rail 左下角提供设置模态入口；`APP_ADMIN` 或 `SUPER_ADMIN` 用户可管理应用人员、应用与代码库关联和应用工作空间，其他用户可看到“应用与工作区”菜单但只显示当前角色无权限提示，所有登录用户可管理自己的 SSH key。
- 设置模态内仅 `SUPER_ADMIN` 显示“运行管理”菜单；该面板通过 `getOpencodeRuntimeManagementOverview()` 展示 Linux 服务器、后端 Java 进程、opencode 容器、manager、manager-backend 连接、用户进程和绑定状态，只读展示并提供筛选、分页和刷新。
- 设置模态“应用人员管理”tab 的“添加成员”区使用 `el-autocomplete` 异步下拉搜索：输入即触发后端 `/configuration-management/users?keyword=`（Element Plus 自带 300ms 防抖），下拉展示按 `userId` / `unifiedAuthId` / `username` 任一字段大小写不敏感 LIKE 命中的候选；选中后主按钮文案由“搜索”切换为“添加”，再点击即把该用户加入当前应用并刷新成员列表。
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
