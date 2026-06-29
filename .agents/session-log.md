# Session Log

## Entries

### 2026-06-29 - 修复历史对话工具消息归一化缺失导致助手气泡空白

- Why: 之前引入的 `normalizeMessagePart` 规则将 opencode parts 归一化为平台标准结构，把 `part.state.output` 移到了 `part.output`。这导致 `FigmaChatPanel.vue` 中的 `partText` 函数在解析归一化后的 `tool` 分段时，由于继续尝试读取已不存在 of `part.state.output` / `part.state.error`，从而提取不到内容返回了空字符串。对于只有工具步骤且无文本消息的历史回复，会导致计算出的气泡文本为 `""`，从而被模板判定无内容而渲染为完全空白的助手气泡。
- What:
  - 修复 `frontend/apps/agent-web/src/components/FigmaChatPanel.vue` 中的 `partText` 函数，增加对归一化后 `toolPart.output` 为字符串的直接读取支持，并作为 `state.output/error` 之前的优先级，同时完美向后兼容。
  - 修复 `frontend/packages/agent-chat/src/runtime-reducer.ts` 中的 `normalizeMessagePart`，在归一化 `tool` 分段时增加对 `raw.error` / `state.error` 备选项的复制支持，避免报错信息丢失并在 `ToolDetail` 中能够正常展现。
  - 修复 `FigmaChatPanel.test.ts` 中受 formatTokens 格式化影响而报错的 tokens 静态断言（由 `"19915 tokens"` 更改为 `"2.0w tokens"`）。
- How: 纯前端代码与测试用例微调，不涉及后端、接口或数据库模式变更。
- Result: 修复后切换历史对话时，智能体执行的各工具过程和最终步骤文本均可正确、完整地展示。175 项 Vitest 单元测试全绿通过。

### 2026-06-29 - 重命名为应用级、增加 hover 备注，并放开应用级配置修改权限

- Why: 满足用户的定制需求：将工作空间级重命名为应用级，添加详细备注；为了保证多租户/多应用独立发布管理，只对超级管理员保留公共级配置的写权限，而将应用级配置的修改与发布权限下放给普通用户，在本地技能中记录了正确的 macOS 开发重启命令；同时解决原生 title 属性 hover 响应慢的问题，以及非超管用户无法读取公共配置的缺陷。
- What:
  - 前端将“工作空间级”全部重命名为“应用级”，公共级添加 hover 提示“公共级 agents 及skills”，应用级添加 hover 提示“应用自定义 agents 及 skills，应用可以自己心中修改和发布”。
  - 放开了应用级（WORKSPACE 作用域）的修改限制，允许非超级管理员执行文件写入、创建 worktree、stage、commit 和 publish。
  - 将主按钮的悬浮提示改为 Element Plus 的 `<el-tooltip>`，设定悬浮响应时间为 50ms 级别以达成即时呈现效果。
  - 后端放宽了获取公共配置元数据 GET 接口的角色要求，允许非超管用户读取公共分支与仓库，保证非超管用户能够正常以只读方式浏览公共级文件。
  - 新增项目级技能 `.agents/skills/restart/SKILL.md`，记录了本地 JDK 25 的重启服务命令。
- How:
  - 修改 `AgentConfigPanel.vue` 中 header 和 dialog 相关的文字，将公共级/应用级按钮用 `<el-tooltip placement="top-start" :show-after="50">` 包裹以即时呈现；修改 `openFile` 的 `readonly` 计算、`createWorktree` / `openCreateWorkspacePackageModal` / `stage` / `commit` / `publish` 内部的 `props.canWrite` 逻辑，同时在 WORKSPACE 级隐藏 `v-if="canWrite"`。
  - 修改 `AgentConfigController.java` 中所有的 `/workspaces/{workspaceId}/...` 写入和发布接口，以及 `/public/repositories`、`/public/repositories/local` 和 `/public/branches` 三个只读 GET 接口，将 `AuthWebSupport.requireRole(..., Dictionary.ROLE_SUPER_ADMIN)` 放宽为 `AuthWebSupport.getAuthPrincipal(exchange)`。
  - 修改 `WorkspaceFileWebSocketHandler.java`，对于 `agentConfigWrite` 请求，仅在 `SCOPE_PUBLIC` 时校验 `ticket.superAdmin()`。
- Result:
  - 运行 `JAVA_HOME=... mvn clean package -DskipTests` 后，18 个后端模块全部编译构建成功。
  - 前端修改 `agent-config-panel.test.ts` 中的按钮名称并运行 `corepack pnpm test agent-config-panel.test.ts`，Vitest 单测 5 个全绿通过。
  - 本地运行重启脚本后服务已加载最新的 jar 包并健康检测通过，切换普通用户后能正常加载并以只读方式查看公共级文件，Hover 响应即时。

### 2026-06-29 - 修复公共配置恢复、技能包层级与 OpenCode 初始化

- Why: 公共配置 `agents/` 被误删后，仓库因工作树不干净被判为未初始化，导致文件树、刷新和重新拉取入口互相锁死；此前公共 skill 还有 `mimoagent-agents` 包装层和符号链接；本机 manager 又因历史 `mgr_kaka_opencode` 与标准 `mgr_local_opencode` 抢占同一 `container_id` 而持续断线。
- What: 公共仓库只要 origin 和 `opencode/` 有效就保持 `initialized=true` 和可浏览，未提交修改单独标记 `CONFLICT`；公共更新增加默认关闭的 `discardLocalChanges`，页面要求超级管理员明确勾选后才恢复已跟踪修改。工作空间 `+` 只生成 `skills/<name>/SKILL.md`、`rules/README.md`、`templates/README.md`，普通工作空间树隐藏根级 `.opencode`。公共配置仓库已把 18 个 skill 扁平为 `opencode/skills/<skill-name>/` 实体包并删除 `mimoagent-agents` 和符号链接。
- How: 后端复用 `GitWorkspaceService.resetHardToCommit(..., "HEAD")`，不删除未跟踪文件；前端/API 增加显式布尔字段与确认框，并补充后端、backend-api 和组件回归测试。为定位 manager 断线，在 WebSocket 入站错误边界增加结构化 WARN；数据库事务只删除 `mgr_kaka_opencode` 的 6 条连接和 1 条 manager 冲突记录，保留 `mgr_local_opencode` 及业务工作区、会话、运行数据。
- Result: 公共配置仓库 commit `081d56d` 已推送 Gitee `master`；Manager 自动以 `mgr_local_opencode` 绑定当前机器并恢复心跳；真实页面 OpenCode 初始化后状态为“opencode 进程可用”，最终三服务重启后用户进程 `192.168.100.115:4098` 健康检查通过。公共配置树可见 `agents/` 与 `skills/`，普通工作空间树不再展示 `.opencode`。后端相关 31 个测试、前端相关 31 个测试、前端 typecheck/build 和 `git diff --check` 通过。

### 2026-06-29 - Agent 配置树上移并支持工作空间技能包初始化

- Why: 公共/工作空间 Agent 配置原先只展示 `agents/`，用户无法维护 `skills/<skill>/SKILL.md` 技能包；同时 openclaw 迁移内容需要保留 agent -> stage -> skills 的原始包结构。
- What: 后端 Agent 配置文件树根上移为公共 `opencode/`、工作空间 `.opencode/`，前端工作空间级 `+` 初始化 `agents/<name>.md` 与 `skills/<name>/SKILL.md`/`rules`/`templates`，公共配置仓库把 openclaw 原始包放到 `opencode/mimoagent-agents/`，`opencode/skills/` 用符号链接作为运行时入口。
- How: 保持 opencode 运行时仍读取 `agents/*.md` 和 `skills/*/SKILL.md`；避免在 `opencode/agents/` 根下混入目录，因为 opencode 1.17 会尝试把目录当 agent 配置解析并报 `p.info.permissions`。
- Result: 后端 `AgentConfigApplicationServiceTest`、前端 `agent-config-panel.test.ts`、`@test-agent/agent-web typecheck`、opencode `agent list`/`serve` smoke 通过；`.env.test` 三服务已启动，backend/readiness/frontend/CORS 均通过，但 `opencode-manager.log` 仍每约 10 秒出现一次 WebSocket 断开记录，本次未扩展排查。

### 2026-06-29 - 优化编辑器 Markdown 预览分屏分隔线视觉设计

- Why: Markdown 预览开启后的分屏分隔线（sash）原本为扁平的极浅灰色，在白色背景上几乎不可见且不明显，缺乏可拖拽的视觉暗示。
- What: 将其重新设计为一个 8px 高度、带上下精致边框和圆角拖拽手柄的高级分隔线，并辅以悬浮过渡状态。
- How: 修改 `CodeEditor.vue` 中分屏 sash 部分的代码，将 `4px` 纯色 `div` 更改为带 `border-t border-b border-[var(--ta-border)]`、`bg-[var(--ta-bg-2)]` 的 `8px` 容器，居中放置一个 `h-[2px] w-8 rounded-full bg-[var(--ta-border-strong)]` 的精细拖拽手柄。
- Result: 视觉效果更立体美观，具备明确的拖拽引导，各项前端类型校验和单测全绿通过。

### 2026-06-29 - 修复公共 Agent 面板因本地系统文件判脏无法刷新

- Why: 左侧 Agent 面板点击刷新仍提示“没有已初始化服务器”，但公共 opencode 配置目录实际已存在且包含迁移后的 agent/skill。
- What: 定位到 `listPublicAgentRepositories()` 返回 `status=CONFLICT, initialized=false`，原因是公共配置 Git 工作树存在未跟踪 `.DS_Store`；删除该文件后后端立即返回 `READY/initialized=true`。在公共配置仓库新增 `.gitignore` 忽略 `.DS_Store`，避免 macOS 再次生成系统文件导致页面无法加载公共级 Agent。
- How: 使用默认测试账号登录后调用 `/api/internal/platform/workspace-management/agent-config/public/repositories` 和 `/public/files` 验证状态；当前 Agent 面板的公共级根目录映射到 `opencode/agents`，会直接列出 agent Markdown 文件，`opencode/skills` 由 opencode 运行时加载但不在该面板根目录展示。
- Result: 公共配置仓库状态恢复为 `READY`，`/public/files?path=&linuxServerId=192.168.100.115` 可列出 7 个迁移 agent 文件。

### 2026-06-29 - 同步 openclaw 测试设计与执行公共 opencode 配置

- Why: 当前项目的公共 opencode 配置已初始化到 `temp/opencode-config`，需要把桌面 `openclaw` 中沉淀的测试设计、测试执行 agent/skill 迁移到公共配置区域，供本项目托管 opencode 运行时复用。
- What: 在公共配置仓库新增 `opencode/agents/` 下的测试设计主 agent、测试对象识别/策略规划/案例生成/案例审查子 agent、测试执行主 agent 与 API 测试执行子 agent；将 `openclaw` 展开运行态中的 18 个测试设计/执行 skills 同步到 `opencode/skills/`，并更新公共配置 `README.md` 说明目录、迁移来源和当前能力边界。
- How: 采用 `OPENCODE_PUBLIC_CONFIG_DIR` 实际指向的 `temp/opencode-config/opencode` 作为运行配置根目录，按 opencode 1.17 的复数目录约定放置 `agents/` 与 `skills/`；skill 来源选用 `/Users/kaka/Desktop/openclaw/.mimoagent-state/agents/*/workspace/skills`，以保留 `rules/`、`templates/` 等相对引用资源。
- Result: `opencode agent list` 能识别 7 个迁移 agent；临时 `opencode serve` 通过 `/agent` 与 `/skill` 分别加载到迁移后的 agent/skill。当前项目未发现 `testing_api_action_run`、`testing_db_action_run` 等 openclaw 测试执行平台工具，因此执行 agent 已保留能力限制说明，后续接入真实 API/DB 执行能力时需补齐对应工具或适配层。

### 2026-06-29 - 新增 Windows PowerShell 三服务重启脚本

- Why: Windows 开发环境需要不依赖 Bash 的三服务一键启动入口，避免按旧文档手工分别配置和启动 Java 后端、opencode-manager 与前端。
- What: 新增根目录 `restart-dev-services.ps1`，对齐 `restart-dev-services.sh` 的默认 `test` profile、dotenv 安全解析、先构建再停服、后端 → opencode-manager → 前端重启顺序、JVM 代理清空、manager state/托管 opencode 进程清理和 `.tmp/dev-services` 日志约定；`tools/verify-dev-scripts.sh` 增加 ps1 存在性检查，并在可用 PowerShell 时做 parser 校验。
- How: Windows 脚本使用 PowerShell 5.1 语法和 Win32_Process command line 精确匹配脚本管理的进程，不手改 `.env.local` 等敏感环境文件；同步 `docs/guides/ai-workflow.md`、`backend/README.md`、`frontend/README.md`、`docs/deployment/backend.md` 和 `docs/deployment/frontend.md` 的本地联调入口。
- Result: `tools/verify-dev-scripts.sh` 在当前 macOS 环境通过；由于本机未安装 `pwsh`/`powershell`，校验脚本已跳过 PowerShell parser 校验，后续 Windows 环境应执行同一校验脚本或直接运行 `powershell -ExecutionPolicy Bypass -File .\restart-dev-services.ps1 -Help` 复核解析。

### 2026-06-29 - opencode-manager 容器 ID 改为 hostname 优先

- Why: 运行管理中的 manager 容器标识需要优先反映实际主机/容器 hostname，不能被 `OPENCODE_MANAGER_CONTAINER_ID` 环境变量抢先覆盖。
- What: 调整 Go manager `resolveContainerID`：Windows 直接使用机器名；非 Windows 依次读取系统 hostname、`/etc/hostname`，最后才用 `OPENCODE_MANAGER_CONTAINER_ID` 兜底，并移除旧的 `HOSTNAME` 环境变量兜底。同步 opencode-manager README 和部署文档，说明环境变量只作最后兜底。
- How: 先补 RED 测试覆盖 hostname 优先、`/etc/hostname` 优先于 env、env 最后兜底、`HOSTNAME` env 不再生效、Windows 忽略 env 使用机器名，再最小调整解析函数。
- Result: `go test ./internal/config`、`go test ./...`（opencode-manager）、`mvn clean package -DskipTests` 和 `git diff --check` 已通过。本变更不涉及数据库、HTTP API、SSE 或前端直连逻辑。
### 2026-06-29 - 修复首轮对话远端 user 快照延迟导致的重复气泡

- Why: 新会话首轮发送后，opencode 可能在 assistant 已渲染后才补发同一条 user 的 `message.updated` / `message.part.updated`，前端 reducer 原先只在“当前轮还未出现 assistant”时归并远端 user，导致实时视图多出一条重复用户气泡；历史记录正常，因为持久化消息本身没有重复。
- What: `frontend/packages/agent-chat/src/runtime-reducer.ts` 对空的延迟 user snapshot 不再追加占位消息，并在后续 text part 到达时按未绑定 `messageId` 且文本一致的乐观 user 消息回填远端 `messageId`。
- How: 新增 `runtime-reducer.test.ts` 回归用例，先 RED 复现“乐观 user -> assistant -> 延迟远端 user snapshot/part”的事件顺序，再最小修改 reducer 归并逻辑；未改 API、SSE 事件契约、数据库或样式。
- Result: `corepack pnpm exec vitest run packages/agent-chat/tests/runtime-reducer.test.ts`、`corepack pnpm --filter @test-agent/agent-chat typecheck`、`corepack pnpm test`、`corepack pnpm --filter @test-agent/agent-web build` 和 `git diff --check` 通过。`./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build` 完成后端打包但后端启动失败：`.env.test` 指向的 PostgreSQL `192.168.100.200:5432/testagent` 当前 `No route to host`，因此 backend health/CORS 未通过；frontend Vite `127.0.0.1:3000` 返回 200。构建仍有既有 CSS `@import` 顺序与大 chunk 警告，本次未扩大处理。

### 2026-06-29 - 模型选择器交互重构：实现气泡下拉框并整合上新推荐

- Why: 聊天输入卡片下方的“上新”标签与输入框内的模型选择下拉按钮功能重叠。为了提升交互体验，需要将模型选择机制彻底由覆盖全局的模态弹窗重构成紧凑优雅的触发式气泡下拉框（Popover Dropdown），并将上新推荐无缝融合在下拉框内。同时，需解决气泡下拉框在不同宽度下的裁剪和遮挡问题。
- What:
  - 彻底移除了原 `AgentWorkbench.vue` 里的全局模态弹窗 `managed-model-dialog-backdrop` 结构及配套的弹窗控制逻辑与大片样式。
  - 在 `FigmaChatPanel.vue` 中封装并实现了全新的 `.figma-chat-model-dropdown` 组件，绑定至模型选择按钮下方，使其点击直接在上方弹出下拉框（带指向箭头的卡片设计）。
  - 下拉框内部高度融合：顶部包含模型搜索栏，搜索为空时在“上新推荐”分区呈两列圆角卡片展示最新推荐模型，下方则按供应者分类展示所有启用模型的垂直列表，高亮当前选中的模型并标示勾选符号。
- How:
  - 在 `FigmaChatPanel.vue` 的 script 模块中利用 `onMounted`/`onBeforeUnmount` 注册全局 click 监听以处理点击空白自动收起下拉框的交互。
  - 移除了 `@open-model-picker` 组件事件触发；在 `AgentWorkbench.vue` 中将 `@select-model` 直接绑定至原有的 `selectRuntimeModel`，并且清理掉其原本管理的 `modelPickerOpen` 和 `modelGroups` 等冗余状态。
  - **裁剪及定位优化**：将输入卡片 `.figma-chat-input-card` 的 `overflow` 属性由 `hidden` 改为 `visible`，解决下拉框被父级边框遮挡裁剪的问题。将下拉框 `.figma-chat-model-dropdown` 的水平定位由 `left: 50%; transform: translateX(-50%)` 修改为 `left: 0` 左对齐，指示小箭头 `::after` 修改为固定偏置 `left: 36px`，确保下拉框整体完全容纳在右侧聊天面板内，彻底解决了在窄屏或侧边栏左边界的裁剪切边缺陷。
- Result:
  - 消除界面中的浮层弹层，模型切换交互完全局限在气泡下拉菜单内，极具 MIMO Web IDE 的精致卡片质感，用户体验高度一致，完美渲染不裁剪。
  - 全量 170 项 Vitest 单测及编译类型检查全部无损通过。

### 2026-06-29 - 统一目录配置与 macOS 平台修复

- Why: 合并后 macOS 参数仍指向 `/tmp/test-agent`、`$HOME/test-agent`，公共 Git 地址为 `UNCONFIGURED`；前端未提供 `macos` 筛选，相关后端测试还把 `macos` 当非法值，用户初始化失败提示也没有给出可执行入口。
- What:
  - 通用参数页面增加 `macos` 并改为选择即查询；后端/API 测试同步接受 `macos`、继续拒绝未知平台。
  - 启动脚本默认导出可覆盖的 `TEST_AGENT_ROOT=$ROOT_DIR`，F-COSS 本地种子目录改到项目 `temp/fcoss`，不再重建 `/tmp/test-agent`。
  - manager 自动启动判断同时识别 loopback 和默认路由网卡的本机 IPv4，避免 `.env.test` 使用局域网地址时误判为远端并跳过 manager。
  - 新增默认只读的 `tools/cleanup-old-path-data.sql`；显式传入绝对项目根目录时只迁移路径字段，保留 Session、Run 和审计记录。
  - 公共 Git 未配置、公共 opencode 配置未初始化时，错误信息分别指向“通用参数管理”和“opencode公共配置管理”。
- How:
  - 六个 macOS 参数通过通用参数 API 更新为 `$TEST_AGENT_ROOT/temp/...`，公共 Git 地址更新为 `git@gitee.com:huangzhenren/opencodeconfig.git`，产生 7 条修改审计并触发 manager 配置刷新。
  - 旧工作区文件先无损复制到项目 `temp/`，再迁移数据库 workspace/version/replica 路径；确认数据库旧路径引用为 0 后删除 `/tmp/test-agent`、`$HOME/tmp/test-agent` 和 `$HOME/test-agent/opencode-configdev`。
- Result:
  - 浏览器实测 `macos` 选项可选且自动刷新；系统管理入口和公共配置管理可打开。
  - 公共仓库已从 Gitee `master` 初始化到 `temp/opencode-config`（commit `a5a4ca00a9`），最终重启后默认用户 opencode 进程在 `192.168.100.115:4098` 返回 `READY`。
  - Go 全量测试、前端 170 个 Vitest、相关 Maven reactor、前端 typecheck/build、系统管理入口 Playwright 和开发脚本校验通过。
- 注意事项:
  - 已执行的 Flyway migration `V20260628223000` 不修改，避免 checksum mismatch。
  - 环境专属路径不应写入 Flyway，应通过通用参数管理 API 或本地初始化脚本更新。
  - `temp/` 目录不提交到版本控制，本地开发时按需创建。

### 2026-06-29 - 前端 Chunk 大小优化与依赖按需加载

- Why: Vite 生产构建时发出包体积过大警告，其中 `element-plus` (~940 kB)、`markdown` (~1.05 MB) 和 `echarts` (~1.08 MB) 均超出了 600 kB 警告阈值，影响加载性能。
- What:
  - 引入 `unplugin-vue-components` 和 `unplugin-auto-import` 插件，实现 Element Plus 组件的按需自动导入。
  - 将 `highlight.js` 全量包替换为 `highlight.js/lib/common` 常用语言子包（支持 37 种常用开发语言），精简 Markdown 渲染体积。
  - 移除 `manualChunks` 中对 `echarts` 依赖的强合并配置，使其保持按需动态分片。
  - 解决 Vitest 及 E2E 测试在此项调整下的兼容性，规避了 JSDOM 的 CSS 解析报错以及 Playwright 侧边栏按钮的选择器冲突。
- How:
  - 在 `apps/agent-web` 的 `devDependencies` 中安装按需引入依赖，并在 `vite.config.ts` 和 `vitest.config.ts` 中注册 `AutoImport` 及 `Components` 插件（为兼容单元测试在 Node 下运行，显式配置 `importStyle: false`）。
  - 创建 `src/utils/locale.ts` 文件以剥离 Element Plus 国际化配置及 dayjs side-effects，在 `App.vue` 中使用 `<el-config-provider>` 包装，彻底规避 `main.ts` -> `App.vue` 的循环依赖导致应用崩溃问题。
  - 修改 `MarkdownView.vue` 及 `MarkdownPreview.vue` 的动态 `import` 路径为 `highlight.js/lib/common`。
  - 适配测试用例：在 `agent-config-panel.test.ts` 中将 `getByTitle` 调整为 `getByText`；在 `scheduler-management-panel.test.ts` 的 menu 切换断言中指定 `{ selector: ".ta-system-menu-text" }` 以免与 tooltips 冲突；在 `workbench.spec.ts` 中将模糊的 `/工作空间/` button 查询改用特定的 `.ta-workbench-footer-branch` 类定位器，解决 strict mode violation。
- Result:
  - `element-plus` 依赖包由 939.78 kB 缩减至 **514.97 kB**。
  - `markdown` (highlight.js) 依赖包由 1,052.13 kB 缩减至 **289.87 kB**。
  - `echarts` 全量包被完全拆散为异步子 chunk (单文件约 250 kB)，不再占用首屏同步加载。
  - 全量 169 项 Vitest 单测均顺利绿过，成功消除 Vite 大包警告。

### 2026-06-29 - 优化挂机趣味彩蛋出现机制与计时

- Why: 优化彩蛋出现机制，要求只有当页面置顶且鼠标不动超过 1 分钟时才出现小人，而在页面未在最前端显示时（例如后台标签页、浏览器最小化或失去焦点），小人不应触发以避免浪费背景资源或影响用户体验。
- What:
  - 将 `FigmaShell.vue` 中小人诞生的静置超时时间从 20 秒修改为 60 秒（1分钟）。
  - 新增页面状态检测：在 `resetInactivityTimer` 和 `spawnRobot` 中增加了 `document.hidden || !document.hasFocus()` 条件判断，确保在页面隐藏或失去焦点时不启动或触发小人诞生。
  - 新增焦点与可见性事件监听：在 `onMounted` 中添加对 `window` 的 `focus`、`blur` 以及 `document` 的 `visibilitychange` 事件监听，使用 `handleFocusChange` 处理，实现切回页面时重置/重新计算 1 分钟不活跃倒计时，切出页面时立即清除不活跃定时器。
- How: 纯前端交互逻辑优化，无额外后端接口依赖，仅通过浏览器 visibilityState 和 focus API 进行条件拦截与定时器销毁。
- Result: 页面最小化、切换标签页或失去焦点时，机器人小人彩蛋不会在后台触发诞生，极大节省了后台 CPU 占用，前端语法检查和 Vitest 测试通过。

### 2026-06-29 - 收纳公共级操作按钮为鼠标悬停从左侧弹出的更多操作菜单

- Why: 公共级的操作按钮过多（包含更新、切换、创建 worktree 共三个按钮），全部平铺展示会使得页面横向空间过度拥挤，破坏紧凑的布局结构；同时，由于文件树容器具有滚动和 overflow-x: hidden 裁剪，菜单向右弹出（超出侧边栏边界）时会被裁剪导致无法显示，因此应改为向左侧（文件树内部区域）弹出。
- What: 将这三个按钮收纳到一个隐藏的可选菜单中，在末尾展示一个三点图标的“更多操作”按钮，在鼠标悬停时在按钮左侧展示全部操作列表。
- How: 
  - 在 `AgentConfigPanel.vue` 模板中引入 `MoreHorizontal` 图标，并在“公共级”头部使用 `.agent-more-menu-container` 包裹。
  - 将“更新公共配置”、“切换公共 worktree”和“创建公共 worktree”三个按钮移入内部的 `.agent-more-menu-dropdown` 容器，改写为横向对齐的条目。当触发异步操作时，外部的三点图标将智能替换为 `Loader2` 旋转动画以维持顶层加载指示。
  - 在 `<style scoped>` 中通过纯 CSS `:hover` 选择器实现展开显示，设定 `right: 100%` 与 `top: -4px` 以在左侧对齐弹出，并配合向左投影 `box-shadow: -4px 4px 12px rgba(0, 0, 0, 0.08)` 优化视觉；此外使用 `::after` 伪元素桥接了按钮和菜单之间 4px 的物理空隙，确保鼠标滑向菜单时悬浮态连续且菜单不会意外消失。
- Result: 界面排版更加清爽，悬浮菜单能向左侧顺畅弹出，且鼠标移入时极为稳定，完美解决了容器裁剪及悬空抖动消失的问题。前端项目检查和 Linter 完全通过。

### 2026-06-29 - 将“更新公共配置”点击后的 prompt 弹出框替换为自定义 DIV 弹窗

- Why: 点击“更新公共配置”按钮时使用原生的 `window.prompt` 会破坏 UI 的整体美观与一致性，需要用自定义设计的 DIV 弹窗代替。
- What: 将更新公共配置中的 `window.prompt` 输入/选择框替换为一个使用 `<Teleport>` 渲染的自定义 DIV 弹窗，显示远端分支下拉列表供用户选择。
- How: 
  - 在 `AgentConfigPanel.vue` 中新增 `showUpdatePublicConfigModal`、`updatePublicConfigBranch`、`updatePublicConfigBranches` 等状态。
  - 在 `updatePublicConfig()` 中异步加载远端分支列表并打开弹窗，由 `submitUpdatePublicConfig()` 执行提交；模版中添加 `showUpdatePublicConfigModal` 自定义弹窗，应用已有的样式结构和类，支持 ESC 键关闭。
- Result: 成功使用统一样式的页面级模态弹窗取代了浏览器原生 prompt。前端项目类型检查及代码风格校验（`corepack pnpm typecheck && corepack pnpm lint`）均顺利通过。

### 2026-06-28 - 运营分析 rollup 与 AI 回复满意度反馈一次性实现

- Why: 运营侧需要覆盖用户规模漏斗、使用强度、Run 结果、满意度、Diff 采纳、Token、趋势高峰、维度排行和明细导出的 P0 指标；同时 AI 回复需要可追溯的满意/不满意反馈，但不能展示 prompt、assistant 原文或成本字段。
- What: 后端补齐 Session/Run/用户消息归因字段，新增 `ai_message_feedbacks`、`runs.agent_id/model_id`、hourly/daily rollup、duration histogram、watermark/job/DB lock 表；新增反馈领域对象、MyBatis mapper、服务和 `/messages/{messageId}/feedback` API；新增 analytics rollup runner、查询服务、SUPER_ADMIN analytics API 与 CSV 导出。前端在助手消息下方新增满意/不满意反馈入口，系统管理新增“运营分析”页，覆盖筛选、概览、趋势、热力、排行、满意度、异常明细和导出；同步 API、事件、数据库、backend/frontend README。
- How: 主链路只写事实数据，统计由定时 rollup 持 DB 锁重算最近 hourly/daily，API 只读 rollup 并返回 freshness/stale 状态；MyBatis XML 承载新增 SQL；满意度按 `positive/(positive+negative)`，无反馈返回 `null`，p95 用 histogram 近似；CSV/看板不输出 cost/costUsd。提交时需要继续排除既有无关脏文件：`frontend/apps/agent-web/vite.config.ts`、`frontend/packages/diff-viewer/*`、`frontend/packages/editor/*`。
- Result: 后端完整 `mvn -q test` 通过；在临时 stash 无关脏文件、只保留本次 staged 内容的提交态下，前端相关包 typecheck 通过，workspace 级 `corepack pnpm test` 27 个文件 169/169 通过。带回既有未暂存 editor Monaco 改动时曾因 mock 缺少 `loadMonaco` 出现 unhandled rejection，已确认不纳入本次提交。
### 2026-06-28 - 修复 SSH key RSA 解密失败与版本库英文名称为空问题

- Why: 个人设置页添加 SSH key 报错 "RSA decryption failed"；后台持续报错"版本库英文名称不能为空"。
- What:
  - `RsaKeyService.java`：显式指定 OAEP MGF1 使用 SHA-256，与前端 Web Crypto API `RSA-OAEP (hash: "SHA-256")` 保持一致。Java `OAEPWithSHA-256AndMGF1Padding` 名称有误导性，其 MGF1 默认使用 SHA-1，而 Web Crypto API 的 MGF1 与主哈希一致（SHA-256），导致前后端不匹配。
  - 数据库 `code_repositories` 表：补充三个版本库的 `english_name` 字段（intelligent-test-agent、fcoss-main、mimoagent）。
- How: 修改后端解密参数配置，补全数据库缺失字段；未改 API、前端代码或数据库结构。
- Result: SSH key 前端加密后后端可正确解密；后台工作空间副本同步任务不再报错。

### 2026-06-28 - 添加 macOS 平台支持

- Why: 本地 macOS 开发环境无法创建工作空间，报错 `/data: Read-only file system`。`ParameterPlatform` 枚举只有 WINDOWS、LINUX、ALL，没有 MACOS，导致 macOS 被当作 Linux 处理，使用 `/data/...` 路径。
- What:
  - `ParameterPlatform.java`：添加 `MACOS` 枚举值，修改 `current()` 方法识别 macOS（`osName.startsWith("mac")`）。
  - Flyway `V20260628223000__add_macos_platform_support.sql`：修改数据库约束添加 `macos`，添加 macOS 平台的 `common_parameters` 配置。
  - 数据库配置：macOS 本地开发路径需要使用**绝对路径**，因为后端进程工作目录在 `backend` 子目录下，相对路径会解析错误。
- How: 枚举扩展 + 数据库约束修改 + 平台配置插入；未改业务逻辑或 API。
- Result: macOS 本地开发环境可正常使用本地路径，不再尝试访问 `/data`。

### 2026-06-28 - 完成态历史助手快照与实时 user part 误拼修复

- Why: 真实页面复现完成态 Session `#89d405` 只剩用户消息；数据库对应会话只有 USER 行。后端日志同时显示历史查询在 Reactor `parallel-*` 线程调用 `.block()` 必然失败。进一步直连 opencode 发现 `/api/session/{id}/message` 只返回 `agent-switched/model-switched`，完整 user/assistant 消息实际来自 `/session/{id}/message`；真实新任务还确认 user 的实时 `message.updated + message.part.updated` 会被 reducer 误建成 assistant，从而把提示词拼入回答并表现为多余空行/重复内容。
- What:
  - `GeneratedOpencodeSdkGateway` 改读标准 `/session/{sessionID}/message` envelope；因 generated `Message` union 把 user 收窄错误，仍只在 client 适配器内使用 generated `ApiClient` + 稳定 JSON Map，不手改 generated SDK。
  - 历史消息 Controller 将包含远端刷新和同步仓储访问的调用整体 offload 到 bounded-elastic；终态快照只把 text part 写入 assistant 正文，不再混入 reasoning/tool output。无 text 的工具/文件步骤允许以空正文 + `partsJson` 保存，保留历史文档和文件变更恢复信息。
  - SSE 初始恢复只重放 assistant；前端 reducer 把 opencode 后续重发的远端 user message/part 合并回当前乐观 user 消息，不再创建 assistant 或污染最终回答。
- How: 先用真实数据库、后端日志、标准/旧消息端点和浏览器 DOM 定位，再分别补 gateway、快照正文、Reactor offload、assistant-only recovery、user part reducer 的失败用例；未修改 `.env.local`，未变更 API 路径/字段、事件类型、数据库结构或鉴权策略。
- Result: 后端 18 模块 `mvn test` 全部 `BUILD SUCCESS`；前端 Vitest 22 文件 138/138、全 workspace typecheck、生产 build通过；标题/历史文档定向 Playwright 4/4。使用 `.env.test` 重启三服务后，真实任务“请只回复：最终空行验证通过”运行完成只显示 assistant“最终空行验证通过”，刷新并从历史切回仍显示该输出和 `SUCCEEDED`。修复前已经完成且从未落下 assistant DB 快照的旧会话，若原远端 session 已不可路由，无法凭空补回历史正文；修复后的任务会在完成时落库。

### 2026-06-28 - 历史文档恢复、首条消息标题与对话空行修复（纠正前次完成结论）

- Why: 前次记录宣称历史文档和空行已解决，但真实页面仍无法看到历史生成文档，Session 标题仍是 `Agent HH:mm:ss`，连续助手快照仍会在边界多插换行；同时前次把 delta 事件全局豁免去重，违反 `docs/api/event-stream.md` 中 transient 也按稳定 `eventId` 去重的契约。
- What:
  - `agent-chat.normalizeMessagePart` 复用实时 reducer 的 part 归一化规则，把历史 `partsJson` 的 `id/tool/state.*` 原始结构恢复成统一 text/tool/file part；`workbench-utils.diffFilesFromSessionMessages` 在 Run Diff 快照为空时从历史 write/edit/apply_patch part 推断生成文件。
  - `FigmaChatPanel` 恢复此前被删但 README 仍声明存在的“N 个文件已更改”入口和 Diff 抽屉，补 file part 文档行；连续 assistant 消息合并时边界最多保留一个换行。
  - 新 Session 创建标题改为第一次发送消息的去首尾空白内容，聊天标题同步使用当前 Session 标题。
  - event-stream-client 恢复所有真实 `eventId`（含 transient delta）统一去重；仅缺失 `eventId` 且 `seq=0` 的旧增量保持放行。
- How: 先增加失败用例复现历史 raw part、文件卡片、连续换行、首条标题和重复 transient eventId，再做最小实现；历史 Diff 合并时以 Run API 返回为最新值、tool part 推断为缺失兜底。未修改 `.env.local`，未改 API、事件类型、数据库或 generated SDK。
- Result: 前端 Vitest 22 文件 137/137、全 workspace typecheck、生产 build 通过；新增历史文档/首条标题 Playwright 在 Chromium 与 mobile 4/4 通过；今天涉及的后端 17 模块 Maven 回归 `BUILD SUCCESS`；`.env.test` 三服务重启成功，backend health/readiness 均 `UP`、frontend 3000 返回 200、CORS preflight 200。完整 mock E2E 仍有历史用例未随当前 UI 契约更新：24 passed、13 failed、3 skipped，失败集中在已改版的附件/实时追踪/工作区入口、SSH key 加密 mock 和不唯一“工作空间”定位器，不能据此宣称全量 E2E 已通过。

### 2026-06-28 - 事件流 transient 文本防重修复与历史对话管理抽屉实现及深度适配

- Why: 响应用户需求及进一步精准反馈：(1) 修正智能体输出流式增量中丢失分段/排版破碎产生多余空行的问题；(2) 解决历史对话看不到的问题，提供质感极佳的交互与样式以便用户查看历史并一键切换；(3) 修复历史对话按钮与工作台外部缩进按钮重叠的问题；(4) 修复切换历史对话后仅显示用户消息、智能体返回内容丢失的问题；(5) 修复新建对话按钮失效的问题；(6) 解决切换历史对话后，智能体生成的修改过的文件（文档）没有被恢复展示的缺陷。
- What:
  - `event-stream-client/src/index.ts`：进一步完善流式增量去重策略。对打字机瞬态消息包事件（`assistant.message.delta` 和 `message.part.delta`）实施全局去重豁免，放行所有顺序增量，从根本上杜绝因 eventId 重复导致内容字符被拦截剥碎产生的多余空行 Bug。
  - `FigmaChatPanel.vue`：将「历史对话」按钮紧凑移至左侧标题栏右侧，并为右侧栏预留 56px 边距，杜绝与悬浮缩进按钮重叠；新增历史对话侧滑蒙层抽屉，包含即时关键词过滤模糊搜索、会话列表圆角卡片展示及创建时间和短 ID 标识；新增 `select-session` 事件发出。
  - `AgentWorkbench.vue`：绑定 `FigmaChatPanel` 的 `select-session` 并重构 `switchSession`。引入根据最新历史消息 `runId` 自动调取 `api.getRun` 与 `api.getRunDiff` 进行状态和文件变更恢复的底层加载链路，完全呈现历史生成的测试文档；实现 `handleNewConversation` 处理函数并绑定 `@new-conversation` 事件，解决新建对话按钮失效的缺陷。
  - `workbench-utils.ts`：修复 `messagesFromSessionMessages` 映射逻辑，为 assistant 消息正确填充 `parts: message.parts ?? []`，解决智能体响应无内容时直接被忽略丢弃的深层 Bug。
- How: 瞬态 delta 事件不进行 deduplicate 防重；头部操作栏采用左侧聚合与右侧隔离设计防重叠；恢复历史会话时保障智能体输出的 parts 分段无缝映射至 `AgentMessage` 模型，并同步重新拉取关联 Run 及其 Diff 文件进行工作台编辑器复原。
- Result: 彻底解决内容丢失导致排版多余空行的问题；历史对话按钮展示美观且零重叠；切换历史对话后用户指令与智能体执行的全部步骤、返回内容以及生成的测试文档全部完美展示，新建对话按钮完全恢复正常。Vitest 与前端类型检查（typecheck）全部通过。

### 2026-06-28 - 对话框拉宽、用户气泡底色圆角优化与字间距紧凑化

- Why: 优化首页 Agent 对话面板的视觉与排版，响应用户进一步反馈：(1) 将首页右侧对话框的默认宽度拉宽；(2) 给用户发出的对话框加一个浅灰底色及圆角样式（对齐截图图 1）；(3) 智能体输出各元素行距排版依然较松散，需全方位收紧以彻底紧凑排版；(4) 去除 opencode 进程初始化成功时重复弹出的左下角 feedback toast 通知，仅保留右上角绿色状态提示。
- What:
  - `FigmaShell.vue`：将右侧对话面板默认宽度 `rightPanelWidth` 变量值由 `380` 变更为 `450`（对应拉宽默认宽度）。
  - `globals.css`：将 `--ta-chat-user-bg` 颜色由 `#dddddd` 修改为浅灰色 `#f2f2f2`，并在 Tailwind v4 的 `@theme` 内映射为 `--color-ta-chat-user-bg: var(--ta-chat-user-bg);`。
  - `AssistantThread.vue` / `FigmaChatPanel.vue`：将用户消息气泡样式由 `transparent` / `rounded-md` 变更为具有大圆角的亮灰色气泡（`.figma-chat-bubble--user` 新增 `background: var(--ta-chat-user-bg); border-radius: 12px`），完美在各聊天面板呈现图 1 的底色气泡效果。
  - `AgentWorkbench.vue`：移除 `initializeOpencodeProcessMutation` 成功回调中对 `feedback.value` 赋值成功 toast 的调用，避免弹出多余重复通知。
  - `AnswerPart.vue` / `PlainAnswer.vue` / `ReasoningPartBlock.vue`：将智能体回答及思考的容器、raw 文本 block 上的行高类由 `leading-6`、`leading-[1.55]` 统一修改为 `leading-[1.4] tracking-[-0.01em]`。
  - `MarkdownView.vue`：修改 `.markdown-body` 样式的行高 `line-height: 1.32;`、段落 `margin: 2px 0 !important;`、列表 `margin: 2px 0 !important;` 且 `li` 设为 `1px 0 !important;`，特别将表格 `table` 的 margin 缩短、行高改至极密 `1.25` 且字号为 `12px`，并将单元格 `padding` 压紧至 `2px 5px !important`，代码块 `pre` padding/margin 亦做等比紧凑。
- How: 纯 CSS/Tailwind 样式参数微调与 UI 属性及逻辑回调默认值调整，保留全部业务逻辑。
- Result: 首页聊天面板默认展现更宽大舒服；用户气泡呈亮色圆润浅灰底色（对比头像更柔和，完美对齐图 1）；智能体最终回答、Markdown 文本以及大表格、列表间距布局彻底紧凑精致。前端已初始化 toast 去除，右上角进程状态提示绿卡完美保留。前端 lint 校验与 Vitest 全部通过。


### 2026-06-28 - 左侧栏刷新与配置操作按钮防重点击与点击反馈优化

- Why: 左侧工作空间文件树和 Agents 配置栏的刷新及创建/更新按钮在点击后没有任何视觉反馈（如 hover/active 态或 loading 状态），且缺乏防重点击（点击后禁用）的设计，容易导致用户重复触发网络请求，交互体验不够灵敏。
- What: 为刷新和配置按钮增加悬浮、按压、防重点击（disabled 状态）以及 loading 动画反馈。
- How:
  - 在 `AgentWorkbench.vue` 中重构 `loadDirectory(path, workspaceId, force)` 签名，将 `force` 作为第三个参数以维持对已有调用（传 workspaceId 作为第二个参数）的向后兼容，并在 `force = true` 时绕过本地缓存强制拉取。
  - 在 `FigmaFileExplorer.vue` 中，文件树刷新和配置栏刷新分别根据父组件 `loadingPath.has("")` 和子组件 `agentConfigPanelRef?.busy` 状态进行自锁控制（禁用按钮且 `RefreshCw` 图标旋转 animate-spin）。对 `.figma-fe-section-action-btn` 样式补充 `:active` 和 `:disabled` 状态。
  - 在 `AgentConfigPanel.vue` 中暴露 `busy` 状态，新增 `updatingPublicConfig` 和 `creatingWorktreeScope` 状态标识。在按钮中根据此状态渲染 `Loader2` 旋转加载动画代替原先的 `Plus` 或 `ArrowUpFromLine` 图标。对 `.agent-icon-btn` 补充 `:hover` 和 `:active` 样式背景色和阴影。
- Result: 按钮交互反馈清晰，刷新期间能平滑旋转并阻止二次点击，完美修复了防重点击与视觉卡顿的问题。经 `@test-agent/agent-web` 内部 typecheck 和 lint 均一次性顺利通过。

### 2026-06-28 - 分布式公共 Agent 配置初始化与 worktree 路由

- Why: 公共 Agent worktree 必须依赖目标服务器本地 Git 仓库；在创建 worktree 时自动 clone 会在分布式部署下把仓库落到当前后端而不是管理员期望的目标服务器，后续文件和 Git 操作也缺少稳定的服务器归属。
- What: 公共配置仓库初始化从创建 worktree 流程拆出，系统管理新增“配置管理 > opencode公共配置管理”查看在线后端服务器初始化状态并执行初始化；创建公共 worktree 时选择已初始化服务器并写入 `agent_config_worktrees.linux_server_id`，后续公共 Agent 文件、diff、stage、commit、publish 按该字段由当前后端代理到目标服务器执行。AgentConfig 持久化同步迁到 MyBatis XML，进度通过 `agent-config.operation-progress` 广播安全字段。
- How: 先补 workspace-management、persistence、API、progress hub 和前端 RED/回归测试，再最小修改 `AgentConfigApplicationService`、`AgentConfigController`、MyBatis mapper、backend-api/shared-types 和 agent-web 管理页/弹窗；同步 HTTP API、数据库、后端部署、workspace-management README 和 agent-web README。
- Result: 后端聚合测试 `mvn -pl test-agent-workspace-management,test-agent-api,test-agent-persistence -am test` 通过；前端 `corepack pnpm --filter @test-agent/agent-web typecheck`、新增用例 `agent-config-panel.test.ts` 和 backend-api/系统管理定向用例通过；`corepack pnpm exec vitest run apps/agent-web/tests` 仍有进入前已有的 runtime topology label 和运行管理空态文案断言失败，未在本次范围内处理；`git diff --check` 通过。

### 2026-06-28 - 更新公共配置按钮图标改为表达 Git Push 含义的 ArrowUpFromLine 图标

- Why: 公共配置面板中，“更新公共配置”操作原本使用的是代表分支的 GitBranch 图标，无法直观传达出将配置推送或上传同步至远端服务器/代码库的含义，容易引起误解。
- What: 将该操作按钮的图标组件替换为能够表达上传/推送含义的 ArrowUpFromLine 图标。
- How:
  - 考虑到当前版本的 `lucide-vue-next` 不包含 `GitPush` 图标，采用了 `ArrowUpFromLine` 这一代表向上推送/上传的 Lucide 图标。
  - 在 `AgentConfigPanel.vue` 中导入 `ArrowUpFromLine`，并将 `updatePublicConfig` 按钮中的 `<GitBranch>` 替换为 `<ArrowUpFromLine>`。
- Result: 按钮图标成功转换为更符合“推送、上传并同步”语义的箭头图标，提升了图标语义的准确性。TypeScript 编译及样式代码检查顺利通过。

### 2026-06-28 - OPENCODE_PUBLIC_CONFIG_DIR 校验下沉到目标 manager

- Why: Java 后端和最终分配用户 opencode 进程的 manager 可能不在同一台服务器；在 Java 本机检查 `OPENCODE_PUBLIC_CONFIG_DIR` 会误判目标服务器目录状态，并破坏既有按负载选择目标容器的流程。
- What: 移除 `UserOpencodeProcessAssignmentService` 的本机 `Files.*` 目录校验，初始化仍先按当前候选中进程数最少且有空闲端口的容器选择目标 manager，再下发 `start`。Go manager 在 `Start` 中检查 `ConfigPath` 必须存在、是目录且非空；缺失、空目录、非目录或不可读时不创建 session、不启动 opencode，返回 `FAILED + errorCode=OPENCODE_UNAVAILABLE + 公共配置未初始化，请联系管理员。`。Java socket gateway 对该 errorCode 映射为同码平台异常。
- How: 先补 RED 测试覆盖 Java 本机目录不存在仍路由到目标 manager、manager errorCode 映射、Go manager 缺失/空目录/非目录失败，以及 supervisor `commandResult.errorCode` 透传；再改 Java runtime、控制消息工厂、Go process/supervisor 和 README/API/部署文档。
- Result: 窄测试先按预期失败后转绿；后续执行 `mvn -pl test-agent-opencode-runtime -am test`、`go test ./...`（opencode-manager）、`mvn clean package -DskipTests` 和 `git diff --check`。本变更不涉及数据库 migration、RunEvent/SSE 或前端直连逻辑。

### 2026-06-28 - Agent worktree Git 错误提示增加安全归因

- Why: 创建公共 Agent worktree 时，Git clone/fetch/pull/worktree 失败只显示“Git 远端读取失败”，管理员无法区分 SSH key 权限、仓库地址、网络、分支或同名 worktree 冲突。
- What: `ProcessGitCommandExecutor` 在 Git 命令非零退出时按 stderr 和命令上下文归因，保持 `GIT_UNAVAILABLE` 统一错误码，同时在 message 和 `details.gitFailureType/gitFailureHint` 返回可安全展示的诊断建议；`AgentConfigPanel` 优先展示 `gitFailureHint + traceId`，不展示原始 stderr、完整命令或内部路径。同步 HTTP API、workspace-management README 和 agent-web README。
- How: 先补 RED 测试覆盖认证失败、仓库不可访问、网络失败、分支不存在和 worktree 冲突，再新增 `GitCommandFailureClassifier` 并把前端错误格式化抽成 `agentConfigErrors.ts` 单测覆盖。
- Result: `mvn -pl test-agent-common,test-agent-workspace-management -am test`、`corepack pnpm exec vitest run apps/agent-web/tests/agent-config-errors.test.ts`、`corepack pnpm --filter @test-agent/agent-web typecheck` 和 `git diff --check` 通过；一次全量 Vitest 误用参数触发了既有 runtime topology/scheduler 测试失败，未在本次修复范围内处理。

### 2026-06-28 - 用户 opencode 初始化校验公共配置目录

- Note: 该实现已被上方“OPENCODE_PUBLIC_CONFIG_DIR 校验下沉到目标 manager”修正为目标 manager/目标服务器校验；本条仅保留历史上下文。
- Why: 用户进程初始化会把 `OPENCODE_PUBLIC_CONFIG_DIR` 下发为 `OPENCODE_CONFIG_DIR`，若公共配置目录未初始化，manager 仍可能启动一个没有公共 agent/provider 配置的用户进程，后续运行状态难以排查。
- What: `UserOpencodeProcessAssignmentService.initialize` 在真正下发 manager `start` 前检查 `OPENCODE_PUBLIC_CONFIG_DIR` 解析后的本机目录必须存在且非空；缺失、为空、非法或不可读时返回 `OPENCODE_UNAVAILABLE`，提示“公共配置未初始化，请联系管理员。”，并且不调用 manager 启动。同步更新后端工程 README、opencode-runtime README、HTTP API 和部署故障排查文档。
- How: 先补 RED 测试覆盖目录不存在和空目录两种场景，确认旧实现会继续启动；再最小加入 `java.nio.file.Files` 目录存在性/非空检查，错误详情只暴露参数名不暴露本机路径。
- Result: `mvn -pl test-agent-opencode-runtime test`、`mvn -pl test-agent-opencode-runtime -am test`、`mvn clean package -DskipTests` 均通过；不涉及数据库 migration、SSE 事件或前端直连逻辑。

### 2026-06-28 - 通用参数修改增加修改日志与历史查询

- Why: 通用参数为系统级配置，此前修改只在 `common_parameters` 覆盖更新值，无法追溯谁在何时把参数改成了什么；运维需要一个 SUPER_ADMIN 可访问的界面查看每次修改的时间、修改用户和修改前后的值。
- What: 新增 `common_parameter_change_logs` 表（Flyway migration `V20260628100000`），记录 `logId/parameterId/oldValue/newValue/changedByUserId/changedByUsername/traceId/createdAt`；`CommonParameterManagementApplicationService.updateValue` 更新参数值后自动写入修改日志，`findChangeLogs(parameterId)` 按修改时间倒序返回最多 50 条；Controller 在 `PATCH /{parameterId}` 从 `AuthPrincipal` 取用户 ID 和用户名传给应用服务，并新增 `GET /{parameterId}/change-logs` 查询历史。前端 `shared-types` 新增 `CommonParameterChangeLog` 类型，`backend-api` 新增 `listCommonParameterChangeLogs` 方法，`GeneralParamManagementPanel.vue` 操作列新增"修改历史"按钮，点击后弹出抽屉展示修改时间、修改用户、修改前值和修改后值。
- How: 先按 module-map 和 dependency-rules 归属文件到 `test-agent-domain/configuration`、`test-agent-persistence/mybatis`、`test-agent-configuration-management`、`test-agent-api`，复用既有 MyBatis XML mapper 模式和 SUPER_ADMIN 鉴权；应用服务新增 `CommonParameterChangeLogRepository` 依赖和测试可注入构造器，同步更新既有 `CommonParameterManagementApplicationServiceTest` 和 `CommonParameterManagementControllerTest` 的方法签名；同步 `docs/deployment/database.md` 和 `docs/api/http-api.md`。
- Result: `mvn clean package` 全量测试通过，`corepack pnpm -r typecheck` 通过；新增 migration 不影响现有 `common_parameters` 数据，新增 API 端点和前端功能向后兼容。

### 2026-06-28 - 左侧侧边栏工作空间与 Agents 分栏标题文案修改

- Why: 增强界面易读性与语义规范。原本左侧的“应用工作空间”标题不包含其他目录且略显冗长，而“agents”使用的是小写，视觉体验不一致。
- What: 将左侧侧边栏两个分栏的标题文案分别修改为“工作空间”和“Agents”。
- How:
  - 修改 `FigmaFileExplorer.vue`，将第一个分栏标题的文本由“应用工作空间”替换为“工作空间”。
  - 将第二个分栏标题的文本由“agents”替换为“Agents”。
- Result: 侧边栏标题展示为更简洁一致的“工作空间”和首字母大写的“Agents”，符合 IDE 界面设计标准。通过了前端的类型检查与样式审查，无任何逻辑与 API 破坏风险。

### 2026-06-28 - 运行管理底部用户进程按用户主动探测并支持重启

- Why: 运行管理底部列表原来依赖 overview 中按 Redis opencode 心跳筛选的 RUNNING 进程，用户进程停止后记录不再显示，导致无法从用户列表重新启动。
- What: 新增运行管理 `user-processes` HTTP 查询，按用户名、userId 或统一认证号定位用户，不依赖 Redis 心跳过滤；查询时由后端通过 manager health 主动区分 `HEALTHY`、`NOT_RUNNING`、`UNHEALTHY`、`CHECK_FAILED`，并给可重启进程返回 `restartable=true`。前端底部列表改为显式输入用户关键字后查询，未运行或健康失败行可直接重启。
- How: 先补 runtime 查询服务、API Controller、backend-api 和 agent-web RED 测试，再最小实现后端探测/回写、前端独立 Vue Query 和重启入口；同步 HTTP API、Event Stream、runtime 模块、agent-web 与 backend-api 文档。
- Result: `RuntimeManagementQueryServiceTest`、`RuntimeManagementControllerTest`、`runtime-management-settings.test.ts` 和 `backend-api.test.ts` 已通过；不新增数据库结构或 SSE 事件，重启仍复用现有 `containerId + port` manager restart 命令。

### 2026-06-28 - 公共 Agent worktree 首次创建自动准备主仓库

- Why: 公共级创建 Agent worktree 时，如果 `OPENCODE_PUBLIC_CONFIG_GIT_ROOT` 尚未 clone 或只是空目录，后端直接校验 Git 仓库并返回“目录不是 Git 仓库”，与公共配置应按通用参数自动下载后创建 worktree 的预期不符。
- What: `AgentConfigApplicationService#createPublicWorktree` 改为复用公共仓库准备流程；公共 Git 根目录缺失或为空目录时先按请求分支 clone，再在 `OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT` 下创建 worktree。已有非空非 Git 目录、origin 不一致或工作树 dirty 仍拒绝接管。
- How: 先补 RED 测试覆盖缺失目录和空目录两种首次创建场景，再最小修改公共仓库准备逻辑，并同步 workspace-management README、HTTP API 和数据库部署文档。
- Result: `mvn -pl test-agent-workspace-management -Dtest=AgentConfigApplicationServiceTest test` 与 `mvn -pl test-agent-workspace-management test` 已通过；不新增 API、事件、数据库字段或 migration。

### 2026-06-28 - 创建 Agent worktree 从 window.prompt 改为自定义模态弹窗

- Why: 之前创建 worktree 流程（公共级和工作空间级）依赖浏览器原生的 `window.prompt`，不仅视觉风格与 MIMO Web IDE 的现代 UI 严重割裂，而且需要用户连续点击两次弹窗，体验不够连贯。
- What: 将 `AgentConfigPanel.vue` 中创建 worktree 的流程重构为自定义模态弹窗。
- How:
  - 引入 `@test-agent/ui-kit` 中的 `Button` 和 `Input` 基础组件。
  - 新增 `showCreateWorktreeModal` (控制弹窗显示)、`createWorktreeScope` (保存当前操作的作用域)、`newWorktreeName` (绑定输入的 worktree 名称) 等响应式变量。
  - 用 `<Teleport to="body">` 渲染自定义模态弹窗，在弹窗上半部分展示当前 git 库分支（只读，取自 `status.value[scope]?.currentBranch`），在下半部分提供 `Input` 组件供用户输入 worktree 分支名称。
  - 支持回车键（Enter）确认提交、Esc 键或点击取消按钮关闭弹窗。
- Result: 替换了原生 `window.prompt` 弹出逻辑，优化后的模态弹窗使用 ui-kit 和 tailwind 样式，设计符合 IDE 主体风格。经 `corepack pnpm typecheck` 和 `corepack pnpm lint` 校验全部通过，不影响任何后端 API 及现有的 Diff、Commit 和 Publish 流程。

### 2026-06-28 - 聊天输入区交互重新探测 opencode 进程状态

- Why: 右侧对话面板可能保留旧的 `READY` 进程状态缓存，实际 opencode 进程退出后仍显示“进程可用”，用户点击输入区后可能继续发送任务。
- What: `FigmaChatPanel` 在输入框聚焦或输入卡片点击时请求工作台刷新当前用户 opencode 进程状态；`AgentWorkbench` 复用现有 `/processes/me` Vue Query refetch，并把已有状态下的刷新态回传给面板。
- How: 刷新中保持 textarea 可编辑，但禁用发送和新建对话；focus/click 同次交互做 2 秒轻量去重，且不把已有 `READY` 的后台刷新展示成首次“正在检查”。
- Result: 不新增 HTTP API、SSE 事件或数据库字段；面板单测覆盖刷新事件、去重、刷新中阻止提交和旧的首次加载态。

### 2026-06-28 - 通用参数最大进程数热更新后运行管理容量即时刷新

- Why: `OPENCODE_MANAGER_MAX_PROCESSES` 修改后，manager 虽然会应用 `configUpdate`，但运行管理容量来自 Redis manager heartbeat 快照；若只等周期心跳且前端 overview 不轮询，页面会继续显示旧容量。
- What: Go manager 成功应用 `configUpdate` 后立即补发 `managerHeartbeat`，把按端口池 clamp 后的生效 `maxProcesses` 写回 Redis；运行管理 overview 查询增加 5 秒自动刷新；通用参数保存成功后同时失效运行管理 overview 缓存。
- How: 先把 Go 回归测试改成 1 小时心跳间隔并下发超端口池容量的值，确认旧实现等不到即时 heartbeat；再最小修改 manager 控制面、前端 Vue Query 配置和稳定文档。
- Result: 参数保存后容量展示以 manager 实际生效值为准，超端口池时展示 clamp 后容量；不新增 HTTP API、SSE 事件或数据库字段。

### 2026-06-28 - 文件树加载绕开 opencode-manager 健康检查

- Why: 前端加载工作区文件树时会先走 `/api/workspaces/{workspaceId}/file-ws-route` 和目标后端 file-ws ticket 签发；这两个读路径复用了 `UserOpencodeProcessAssignmentService.status()`，导致已有用户进程会触发 manager `health` 命令，manager 慢响应时文件树被 `OPENCODE_TIMEOUT: opencode 管理进程命令超时` 阻塞。
- What: 新增文件 WebSocket 路由专用 `UserOpencodeProcessFileRoutingAffinity` 与 `fileRoutingAffinity()`，只读取 ACTIVE binding 和可恢复进程记录来判断服务器归属；`WorkspaceFileRoutingService` 和 `WorkspaceFileSocketTicketService` 改用该非阻塞归属查询，Run 启动、初始化和用户进程状态接口仍保留强健康检查语义。
- How: 先补 RED 测试覆盖分配服务、文件路由和 ticket 签发不调用阻塞式 `status()`/gateway health，再最小修改 runtime/API；同步 opencode-runtime、API、agent-web README 和 HTTP API 文档。
- Result: 文件树 route/ticket 签发不再向 opencode-manager 下发 health/start 命令；workspace 与用户进程服务器不一致仍返回 `CONFLICT`，用户未初始化进程仍按 `OPENCODE_UNAVAILABLE` 提示先初始化。

### 2026-06-28 - 登录后 opencode 进程状态检查态修复

- Why: 用户登录进入工作台后，后端已返回当前用户 opencode 进程 `status=READY`，右侧聊天面板仍可能停留在“正在检查 opencode 进程”；根因是前端把 Vue Query 后台 fetching 与首包无数据加载混用，READY 数据刷新期间仍会把对话区判为阻塞态。
- What: `AgentWorkbench` 将当前用户进程 query 的 enabled/key 绑定到响应式登录 token，并新增只在首个状态响应前为 true 的 `opencodeProcessInitialLoading`；`FigmaChatPanel` 的 READY 判定不再依赖 loading，只有 `processLoading && !processStatus` 才展示“正在检查”。补充聊天面板单测和从 `/login` 提交后进入工作台的 Playwright 回归。
- How: 先写 RED 单测复现 READY+后台刷新仍显示检查态，再最小修改前端状态链路；不修改后端状态 API，不处理本次排查发现的 `bindingClearable/localFallback` 文档与 API DTO 实现漂移。
- Result: `corepack pnpm test -- FigmaChatPanel.test.ts`、`corepack pnpm exec playwright test apps/agent-web/tests/workbench.spec.ts --grep "login redirects to workbench"`、`corepack pnpm --filter @test-agent/agent-web typecheck` 均通过；整份 `workbench.spec.ts` 仍存在与本次无关的既有失败（SSH key mock 断言、工作空间按钮 strict selector/超时等），不要把这些失败误归因到 opencode 状态修复。

### 2026-06-28 - 通用参数路径支持 $HOME 和环境变量展开

- Why: macOS 本地把 `OPENCODE_PUBLIC_CONFIG_DIR` 配成 `$HOME/.testagent/agent-opencode/.config/opencode/` 后，manager/后端不会经过 shell 展开，字面 `$HOME` 被当作相对路径，导致目录落到项目工作目录下的 `$HOME/`；后续还需要通用参数能引用其他环境变量。
- What: `CommonParameterReferenceResolver` 在通用参数 resolvedValue 阶段支持路径开头 `$HOME` 和 `~/` 展开为当前用户主目录，支持 `$NAME` 读取 Java 后端进程环境变量，支持 `${NAME}` 在通用参数未命中时回退同名环境变量，并保持通用参数引用优先级不变；同步部署、数据库、HTTP API 和模块 README 说明。
- How: 先补 `CommonParameterReferenceResolverTest#expandsDollarHomeLiteralToUserHome`、`expandsDollarEnvironmentVariableFromProcessEnvironment` 和 `expandsBracedEnvironmentVariableWhenNoCommonParameterExists` 复现失败，再最小修改领域解析器；验证领域、配置缓存和 opencode runtime 消费链路测试。
- Result: manager 下发 `OPENCODE_PUBLIC_CONFIG_DIR` / `OPENCODE_SESSION_DIR` 时使用展开后的 `resolvedValue`，不会再把字面 `$HOME` 或已存在的 `$NAME` 传给 manager；本地残留的未跟踪 `$HOME/` 目录若确认无用可人工删除。

### 2026-06-27 - 运行管理拓扑图优化为机房网络拓扑与上下层级结构

- Why: 拓扑图原先为水平结构且采用圆圈节点，长进程 ID 或服务器信息容易在圆圈外产生严重的字符重叠/溢出；用户需要将拓扑优化为上下层级的网络机房类型风格，且各节点需要使用具体的品牌/应用图标表示（Linux 服务器使用 Linux 图标、Docker 使用 Docker 图标、opencode 使用 opencode 图标）。
- What:
  - 重构 `runtimeTopologyGraphData.ts`：将拓扑结构改为上下三层布局（Y=50 为 Java 进程，Y=140 为 Manager，Y=230 为 opencode 进程），各层节点均实现动态水平居中，且 opencode 进程垂直分布在所属 Manager 下方。这大大缩减了垂直高度的占用，彻底消除了底部节点被切断的现象。
  - 重构 `RuntimeTopologyGraph.vue`：
    * 引入 `frontend/apps/agent-web/src/assets/figma/` 路径下的 `Linux.svg`、`Docker.svg`、`opencode-logo.svg` 和 `unknown.svg`。
    * 将 ECharts 节点 `symbol` 根据节点种类映射为上述 SVG 的 Vite 导入 URL，以 `image://` 进行注入渲染。这替代了原本 of 内联 Base64 和复杂 SVG Path，不仅极大地简化了代码结构，还确保了渲染定位 of 完全稳定。为了满足图标小巧精致的要求，将图标尺寸修改为紧凑 of 正方形（Linux 36px，Docker 38px，opencode 32px）。对于无主 opencode 节点，使用 `unknown.svg` 图标进行标识。
    * 将节点 labels 排版位置改为 `"bottom"`（距离图标 8px），确保大长串的 ID 和状态信息完美呈现在图标正下方，从而彻底打消之前的卡片容器挤压或字符重合缺陷。
    * 自定义 label 格式化程序，通过 ECharts rich text 实现状态指示灯 (●) 与标题/副标题在图标下方分行精美渲染，并对長进程 ID 自动截断（保留前后缀）以防溢出。
    * 将连接线曲率设为 `0` (直线连接)，使连线看起来像机房中的网线走线。
    * 优化了图例 meta，不再显示单色的色块背景，而是改为使用对应的 SVG 本地矢量图标 (`img.legend-icon`)，实现图例与图表节点视觉元素的高保真统一。
    * 在画布右下角新增了浮动缩放控制面板（放大 `+`、缩小 `-`、重置 `⟲`），通过调用 ECharts `setOption` 接口动态改变 `zoom` 及 `center`，提供极其流畅的画布平移和缩放功能，并将画布高度提升至 `400px`。
- How: 纯前端数据层坐标派生与 ECharts 渲染配置优化，不更改任何 API 契约或后端业务状态。
- Result: 拓扑图完美升级为具有机房网络卡片风格、走线工整的上下垂直结构，引入 Figma 本地 SVG 静态导入机制并实现图例图标化，通过缩减层级 Y 轴距离、缩小图标尺寸及增加浮动缩放面板，完全解决了底部节点超出屏幕以及缩放平移交互的需求。相关 typecheck、linter 以及所有 Vitest 单元测试均一次性顺利通过。

### 2026-06-27 - 页面复测用户新增登录与 SSH key 加密兼容修复

- Why: 继续页面测试“用户添加 → 新用户登录 → SSH key 添加”时，新增用户和新用户登录通过，但新用户在个人设置添加 SSH key 返回 `RSA decryption failed`；根因是浏览器 Web Crypto 的 RSA-OAEP/SHA-256 使用 MGF1-SHA256，而后端 JCE transformation 未显式指定 OAEP 参数，可能按 Java provider 默认 MGF1 参数解密，导致浏览器密文无法解开。
- What: `RsaKeyService` 解密优先显式使用 RSA-OAEP/SHA-256 + MGF1-SHA256，并保留旧 Java 默认 OAEP 参数兜底以兼容历史 Java 夹具或旧密文；新增 `RsaKeyServiceTest` 覆盖浏览器 Web Crypto 参数密文；同步 `test-agent-common` README。
- How: 页面先复现失败，再补 RED 测试 `decryptsWebCryptoRsaOaepSha256Payload` 观察到 `RSA decryption failed`，随后最小修改后端 RSA 解密参数并重启本地后端/前端服务复测。
- Result: 页面新增测试用户 `codex_user_72481135` 成功，默认密码 `123456` 页面登录成功；该用户个人设置添加 SSH key `codex-ui-key-691863` 成功，数据库中只保存密文、RSA 加密 AES key 和指纹，未出现私钥明文标记。当前主工作区仍因本地 opencode 进程未初始化显示不可用，但不影响本次用户和 SSH key 设置链路。

### 2026-06-27 - 本地开发 PostgreSQL 和 Redis 容器启动

- Why: 用户需要搭建本地 PostgreSQL 和 Redis 容器用于个人本地开发，避免继续依赖当前不可用或超时的外部数据库/Redis。
- What: 使用仓库既有个人离线开发入口 `tools/dev-local-up.sh --redis` 启动 `deploy/local/docker-compose.yml` 中的 `test-agent-postgres` 和 `test-agent-redis`，保持项目默认端口映射：PostgreSQL `15432 -> 5432`，Redis `16379 -> 6379`；未修改 `.env.local`、`.env.test` 或其他密钥配置文件。
- How: 先确认 Docker CLI 存在并启动 Docker Desktop，再通过项目脚本拉起依赖；按 compose healthcheck 等待容器进入 healthy 状态，并用容器内客户端验证连接。
- Result: `test-agent-postgres` 当前健康且可用，数据库/用户为 `test_agent`/`test_agent`；`test-agent-redis` 当前健康且 `redis-cli ping` 返回 `PONG`。本地 `local` profile 可使用 `TEST_AGENT_LOCAL_DB_HOST=127.0.0.1`、`TEST_AGENT_LOCAL_DB_PORT=15432`、`TEST_AGENT_LOCAL_DB_NAME=test_agent`、`TEST_AGENT_LOCAL_DB_USERNAME=test_agent`、`TEST_AGENT_LOCAL_DB_PASSWORD=test_agent`、`TEST_AGENT_REDIS_HOST=127.0.0.1`、`TEST_AGENT_REDIS_PORT=16379`、空 Redis 密码连接这组容器。

### 2026-06-27 - 页面新增用户失败的 user_roles 序列修复

- Why: 通过页面测试“用户管理（测试）”新增用户时，前端返回“服务器内部错误”；后端日志显示 `user_roles_pkey` 主键冲突，根因是历史库 `user_roles.id` identity 序列落后于已有数据，且创建用户流程缺少事务边界，角色授权失败时可能留下无角色用户。
- What: 为 `UserManagementApplicationService.createUser` 增加事务边界，并给 `test-agent-system-management` 补充 `spring-tx` 依赖；新增 Flyway migration `V20260627214000__reset_user_roles_identity_sequence.sql` 将 `user_roles.id` 后续发号起点抬高到 `1000000`；同步系统管理、持久化和数据库部署文档。
- How: 先用页面复现，再按日志定位到 `JdbcUserRoleRepository.save` 的 `user_roles` 主键冲突；补 RED 测试锁定创建用户必须有事务注解，随后最小实现事务和序列兼容迁移。
- Result: `UserManagementApplicationServiceTest`、`FlywayMigrationNamingTest`、`JdbcRepositoryIntegrationTest#migrationGrantsDefaultUserSuperAdminRole` 均已通过；`./restart-dev-services.sh` 构建后端和前端成功，但运行时连接 `.env.test`/`.env.local` 指向的 PostgreSQL 在握手阶段 EOF/read timeout，导致 8080 后端未能启动，页面端新增用户、新用户登录和 SSH key 添加的最终复测被外部数据库可用性阻断。

### 2026-06-27 - 运行管理停止已结束进程幂等清理

- Why: 用户在运行管理停止无主 opencode server 时遇到 `os: process already finished (OPENCODE_BAD_GATEWAY)`，列表仍保留该进程；根因是 Go manager 本地 state 残留已退出 PID，`Stop` 遇到操作系统“进程已结束/不存在”错误时未删除 state。
- What: Go manager 将已结束或不存在进程的 `stop` 视为幂等 `STOPPED` 并删除本地 state；`list`/heartbeat 会过滤并清理 PID 不存在的 stale state；成功的 `start`/`stop`/`restart` 命令后立即补发一次 manager heartbeat。前端 `stop` 成功后直接更新当前 overview 缓存，删除匹配 `containerId + port` 的 managed process，并同步下调容量计数，`restart` 仍保持刷新 overview。
- How: 不新增 HTTP API、SSE、数据库表或 DTO 字段；后端仍沿用现有 manager 命令响应语义，`STOPPED` 为成功，`FAILED` 仍按既有 `OPENCODE_BAD_GATEWAY` 返回。
- Result: 目标 Go 测试、运行管理 Vitest、`@test-agent/agent-web`/`@test-agent/shared-types` typecheck 和 `git diff --check` 已通过；本次仅改 manager 幂等清理、前端局部缓存更新、测试与文档。

### 2026-06-27 - MIMO测试智能体挂机趣味彩蛋动效

- Why: 满足挂机趣味彩蛋动效的需求，当用户静止 20 秒（挂机不活跃状态）时唤醒极简纯色机器人智能体小人执行原地弹跳、随机前跳、走动、坐立、翻跟斗、跨层大跳和发呆等一系列趣味动作，增强界面活力，且在用户再次操作时秒级中断并飞出屏幕离场，不打扰用户正常工作。
- What:
  - 移除了 `FigmaShell.vue` 中原有的极简 stick-figure 火柴人 walker 占位。
  - 新增不活跃检测：通过对 `mousemove`（限流处理）、`mousedown`、`scroll`、`keydown` 事件监听，重置 20s 唤醒定时器，且在小人活跃时有上述操作可高优先级触发离场。
  - 引入了全新机器人彩蛋 SVG 形象：符合圆角正方形头部、双触角、呼吸闪烁的面部光点、胶囊型身体与四肢外观规范。
  - 编写并丰富了物理弹性状态机与移动逻辑：
    * 缩短决策动作切换周期至 1s~2s 左右以极大提高小人活跃度，并调整行为动作池概率。
    * 修复首次诞生的跳跃轨迹：在 `spawnRobot` 中使用 `nextTick` 与 `setTimeout` 延迟触发跳跃，避免 Vue 数据合并渲染导致首跳坐标丢失。
    * 顶部防出界机制：当小人在顶部时，禁止原地弹跳、前跳及空翻等向上跳跃的行为；原本的跨层大跳如果从顶部出发，则优化为无向上弧度、直接向下加速坠落（falling）的平滑滑落。
    * 实现“走动 (walking)”动画：在水平面上做直线位移，双腿与手臂交替旋转摆动（行走步态周期）。
    * 实现“坐立 (sitting)”动画：头与身体下沉，双腿向外侧水平平伸展，处于安静落座状态。
    * 实现“倒挂 (hanging)”动画：小人倒挂在顶部导航栏下沿，CSS 触发 180° 旋转，双臂自然垂下（逆向旋转）并在微风中轻微钟摆摇晃。
    * 实现“翻跟斗 (flipping)”动画：腾空跳跃并在半空中完成 360° 后空翻，伴随 Squash/Stretch 形变（仅在底部时启用）。
    * 保留“原地弹跳 (Bounce)”、“随机前跳 (Short Jump)”、“跨层大跳 (Big Jump)”以及“呼吸发呆 (Stay Idle)”。
  - 编写趣味离场动效：原地面向屏幕 -> 挥手告别 1.2s（右臂高频摆动） -> 蓄力 0.3s -> 抛物线直接飞出屏幕之外 -> 销毁与重置。
- How: 纯前端交互逻辑优化，在 `FigmaShell.vue` 内实现自包含的不活跃状态机，通过三层 DOM 容器（定位、翻转、变形）搭配原生 CSS Transition & Keyframes，完美还原 Squash & Stretch 物理弹性，性能无影响。
- Result: 机器人挂机彩蛋动画顺畅优美，类型检查和 138 个 Vitest 单元测试全部全绿通过，打扰防御和自然消亡逻辑验证正常。



### 2026-06-27 - 运行管理服务器/Java 合并与节点连线拓扑图

- Why: 当前部署假设一台 Linux 服务器只启动一个后端 Java 进程，运行管理里分成“Linux 服务器”和“后端 Java 进程”两张表会割裂服务器资源与 JVM 进程视角；用户同时需要用节点和连线直观看到 Java、manager 与 opencode server 的连接/管理关系。
- What: 前端运行管理将服务器与 Java 进程按 `linuxServerId` 合并为“服务器 / Java 进程”列表，保留服务器/Java 状态、资源指标、JVM、容量和趋势入口；新增 `RuntimeTopologyGraph` 和 `runtimeTopologyGraphData`，使用 overview 现有 `backendProcesses`、`managerBackendConnections`、`managers[].managedProcesses[]` 派生 `Java -> Manager -> opencode server` 拓扑节点和边，支持缩放、拖拽、hover tooltip 与点击节点高亮相邻关系，并兼容旧响应缺少 `managedProcesses`。
- How: 不新增后端接口、SSE、数据库表或 manager 协议字段；原“容器 / 管理进程”表及展开后的有主/无主重启、停止操作保持不变。同步更新 agent-web README 和 HTTP API 文档，说明展示形态变化不改变 overview wire shape。
- Result: 目标运行管理 Vitest、`@test-agent/agent-web` typecheck、`@test-agent/shared-types` typecheck 和 `git diff --check` 已通过；本次仅涉及前端展示、前端测试和文档。

### 2026-06-27 - 运行管理有主/无主进程增加重启停止操作

- Why: 用户需要在运行管理展开的“有主进程”和“无主进程”明细行后直接执行重启、停止，便于处理绑定用户进程和无主进程，不再只做只读观察。
- What: 新增 `RuntimeManagementCommandService` 与 `OpencodeProcessControlCommand/Result`，扩展 `OpencodeProcessManagerGateway` 及 socket 实现，通过 manager WebSocket `restart`/`stop` 命令按 `containerId + port` 控制 opencode server；API 新增两个 `SUPER_ADMIN` POST 端点并返回命令结果 DTO；前端 `backend-api` 增加对应方法，运行管理展开表的有主/无主两组行末新增“重启”“停止”按钮，成功后刷新 overview。
- How: 不新增数据库表、SSE 事件或前端展示用额外请求；重启/停止仍经平台后端鉴权与 traceId，再由后端转发 manager 控制面。local 网关对重启/停止明确返回不可用，避免误以为本地直连可操作。
- Result: 后端聚合 Maven 测试、运行管理 Vitest、`shared-types`/`backend-api`/`agent-web` typecheck 和 `git diff --check` 均通过；HTTP API、Event Stream、相关 README 已同步更新。

### 2026-06-27 - MIMO Test Agent 界面风格与视觉通透感优化

- Why: 系统的旧风格采用直角、硬线条分割，整体偏扁平且缺乏现代 UI 的呼吸感和层次感；需对其进行现代 UI 风格重构（如大圆角、外层灰色背景配合内层白色大圆角卡片），并优化占位符、错误提示框、底部路径栏、模型选择输入框及顶栏标题英文副标题，以提升界面的通透感、清晰度和亲和力。同时跟进微调侧栏折叠按钮样式、压缩卡片容器留白至 `0`，全面弱化/柔化了主界面所有生硬的边框分界线，统一了左右侧边栏分割拖拽条的背景，在顶部主标题下方增设了等宽、居中的英文副标题，并添加了一个在顶部边框线上随机漫步的简约纯色小人动画。
- What:
  - **卡片化与外层背景**：修改 `FigmaShell.vue` 引入外层极淡灰色背景 `#F6F6F6` 及 `0` 紧凑外边距（从 `24px` 缩减以完全消除留白、提升编辑器空间），并将中间编辑器区和右侧对话区包裹在 `16px` 大圆角的白色卡片容器 `.figma-main-card` 内（搭配 subtle border 与 shadow），设置 `overflow: hidden` 防止溢出。移除 `FigmaEditorArea.vue` 的 outer border 避免双边框。
  - **顶栏标题英文副标题及走动小人**：
    - 修改 `FigmaShell.vue` 模板与样式，将头部标题包装至 `.figma-title-group` 居中列式布局中，在中文标题下方新增英文副标题 `<span class="figma-subtitle">MIMO Intelligent Test Agent</span>`，使用 `font-size: 7px` 并结合 `transform: scale(0.9)`、`transform-origin: center center` 与 `letter-spacing` 调优，以居中对齐排版，使其视觉长度与中文主标题对齐，提升高级感。
    - 引入基于 inline SVG 的简约纯色 stick-figure 火柴人 walker，在顶栏底部边框线上通过 Vue 的 random-walk 随机状态机进行 `'idle'` (立正呼吸) 与 `'walking'` (往返行走且自动翻转) 状态的循环切换，利用原生 CSS transition 实现 60fps 的顺滑运动，并在宽屏下限制行走区间，在窄屏（<500px）下自动隐藏避免遮挡文字。

  - **界面分割线条弱化与柔和处理**：修改 `globals.css` 将系统主边框色 `--ta-border`、`--border` 等由较深的 `#dddddd` 变更为极柔和的 `#eaeaea`；同步修改 `FigmaShell.vue`、`FigmaChatPanel.vue` 和 `WorkbenchFooter.vue`，将头尾边框、活动栏侧边框及拖拽条内部细线由 `#ddd` / `#e4e4e7` 改为 `#eaeaea`，同时将左右两个 resize handle 拖拽条的背景统一为固定的 `#f5f5f5`（不再使用右侧 transparent 造成的白色背景穿透），使其在左右两边呈现完全对称一致的 6px 灰底与中置发丝线，全面移除不一致的观感。
  - **折叠展开按钮去硬边框**：修改 `FigmaShell.vue`，将左侧、右侧悬浮折叠展开按钮 `.figma-icon-btn` 重构为全扁平、透明且无框的轻量圆角 icon 按钮，宽度 and 高度精简为 `24px`，并只在 hover 时显示浅灰微背景，极大降噪。
  - **空状态占位符**：修改 `CodeEditor.vue`，用一整幅精心设计的彩色 open folder SVG 渐变图标，以及更清晰、更有引导性的文字和排版“开始您的探索”替换原干瘪灰色 placeholder。
  - **扁平化 Footer 与 Save 按钮**：修改 `WorkbenchFooter.vue`，将底部的路径栏 `.ta-workbench-footer-middle` 优化为极具现代感的圆角 `12px` 灰色胶囊 pill；将 `.ta-workbench-footer-save` 按钮改版为全扁平、圆角 `12px` 矩形，背景为淡灰 `#eeeeee` 并配以线性 Save 图标。
  - **模型快速切换标签与圆角**：
    - 修改 `FigmaChatPanel.vue`，为聊天输入框 `.figma-chat-input-card` 设置 `16px` 大圆角；输入框底部的操作按钮改为全扁平圆角 `12px` 标签（灰色 `#f4f4f5` 背景），发送与停止按钮设计为 premium 黑色圆形。
    - 将右下角 OpenCode 进程错误提示框 `.figma-chat-process-status` 改为 `12px` 柔和大圆角，并将内边距从 `8px 10px` 增加到 `12px 16px`， gap 从 `10px` 增至 `12px`，降低突兀感。
    - 在输入卡片下方新增一排快速切换模型的推荐功能标签（GLM-5.2、Kimi K2.7 Code 等），支持一键点击切换，并为当前选中项标记蓝色高亮。
    - 在 `AgentWorkbench.vue` 绑定 `:models` and `:selected-model` 并接收 `@select-model` 事件；将工作区空状态 `.managed-workspace-empty` 重构为 `16px` 大圆角虚线背景卡片，其边框宽度微调为更精致的 `0.5px`，外边距设为 `0` 以完全消除多余的边距，按钮 `.managed-workspace-button` 统一设为 `12px` 圆角及 `34px` 现代高度。模型选择弹窗 `.managed-model-dialog` 圆角增至 `16px`。
- How: 纯 CSS/Tailwind 样式参数微调与 Vue template 细节调整，不影响底层业务逻辑与 API 通讯。
- Result: MIMO 界面呈现高度通透、亲和现代的卡片式视觉风格，侧栏折叠按钮更为内敛高级，操作区整体有效空间与分割线条得到完美释放；TypeScript 类型检查和 `pnpm build` 全部顺利通过。

### 2026-06-27 - Web IDE 顶部工具栏高度扁平化与精简调整

- Why: 用户需要更扁平的界面设计，将 Web IDE 顶部的顶栏高度从 52px 缩减，使整体工作区布局更紧凑。
- What: 修改 `frontend/apps/agent-web/src/components/FigmaShell.vue`：
  - 将 `.figma-app` 的 `grid-template-rows` 从 `52px 1fr` 调整为 `36px 1fr`。
  - 将 `.figma-header` 的 `height` 从 `52px` 缩减为 `36px`，左右外边距/内边距从 `0 5px` 改为 `0 10px`。
  - 缩放顶栏内各子元素尺寸：Logo 高度调整为 `20px`（宽度按比例自适应）；标题字号 `font-size` 从 `14px` 降至 `13px`，`line-height` 降至 `18px`；右侧各项的 gap 从 `12px` 改为 `8px`。
  - 调整应用切换菜单 `.figma-app-menu-trigger` 高度从 `28px` 至 `24px`，padding 降至 `0 6px`，字号降至 `12px`。
  - 调整用户头像按钮 `.figma-user-avatar-btn` 大小从 `30px` 至 `24px`；头像内文字 `.figma-user-avatar` 容器大小从 `24px` 至 `20px`，字号从 `12px` 降至 `10px`。
- How: 纯 CSS/Tailwind 布局样式参数的比例缩放调整，不更改模板结构或底层 DOM 结构。
- Result: 顶部状态栏高度缩小到 36px，整体布局明显更加扁平、紧凑，符合 IDE 专业视觉标准；Vitest 单元测试和 TypeScript 校验全部顺利通过。

### 2026-06-27 - 运行管理容器与管理进程合并表及有主/无主分组

- Why: 当前架构是一容器一 manager，运行管理里分成“容器”和“管理进程”两张表会让容量计数与下属明细关系不直观；用户需要展开容器/manager 行后区分有 ACTIVE 用户绑定的进程和无主进程。
- What: 后端 `RuntimeManagementQueryService` 在 `managers[].managedProcesses[]` 上补充 `ownership`、候选进程、健康和绑定字段，按同服务器、同容器、同端口匹配用户进程并只把 `ACTIVE` 绑定标为 `BOUND`；无活跃绑定或无候选进程标为 `UNBOUND`。控制面 `backendListResponse.backendEndpoints[].lastHeartbeatAt` 固定按 RFC3339 字符串编码。前端运行管理把容器/manager 两张表合并为“容器 / 管理进程”表，行展开按“有主进程 / 无主进程”分组，容器趋势改为行内按钮打开。
- How: 不新增接口、SSE 或数据库表；继续复用 `GET /api/internal/platform/opencode-runtime/management/overview`，并保持新增字段可选以兼容旧后端、旧 manager 和旧 Redis 快照。同步 HTTP API、Event Stream、模块 README 和 shared types/backend-api/agent-web 文档。
- Result: 后端目标 Maven 全测、前端运行管理 Vitest、`shared-types`/`backend-api`/`agent-web` typecheck 和 `git diff --check` 均通过；当前仍有其他会话遗留的 memory provider/metrics collector 未提交改动，提交时需只暂存本次相关文件。

### 2026-06-27 - 容器指标来源提示文本格式化与换行优化

- Why: 优化容器列表指标“来源”列的悬停提示（Tooltip）排版，使用户更容易阅读各项指标来源的说明。
- What: 修改 `frontend/apps/agent-web/src/components/settings/RuntimeManagementPanel.vue` 和 `runtime-management-settings.test.ts`：
  - 将 `metricsSourceHelp` 变量重构为多行文本，并使用 `\n` 换行符。
  - 将各个来源类型的格式调整为 `“xxxx”: xxxx`（双引号括起名称），每个类型独占一行。
  - 更新对应的 Vitest 单元测试，将提示文本匹配器修改为新格式。
- How: 修改 Vue 组件内部的静态配置字符串，配合 HTML 容器 native `title` 的多行解析特性实现换行，并同步更新测试中的断言。
- Result: 容器指标来源列悬停时，提示以清晰的多行引号格式展示，提升了可读性；Vitest 单元测试与 TypeScript 检查均顺利通过。

### 2026-06-27 - 运行管理管理进程展开显示 opencode server 明细

- Why: 系统管理-运行管理的管理进程列表需要直接查看该 manager 当前管理的 opencode server 进程启动信息，避免只看到 manager 拓扑而无法定位端口、PID 和启动命令。
- What: Go manager 本地 state、启动结果和 `managerHeartbeat.managedProcesses[]` 增加安全展示用 `startCommand`，旧 state 缺字段时按当前配置和端口派生；Java runtime/API 将 manager overview 从纯 manager 扩展为 `RuntimeManagementManager(manager, managedProcesses)` 并返回 `managers[].managedProcesses[]`；前端 shared-types/backend-api/agent-web 增加可选下属进程类型和管理进程行展开 UI；同步 HTTP API、Event Stream 和相关 README。
- How: 先补 RED 测试覆盖 Go state/heartbeat、Java heartbeat 映射/overview/API/Redis 兼容和前端展开展示，再最小实现；运行管理仍只走 `GET /api/internal/platform/opencode-runtime/management/overview`，不新增接口、SSE、数据库 migration 或前端额外请求。
- Result: 目标 Go/Maven/Vitest/typecheck 和后端 `mvn clean package -DskipTests` 均通过；启动命令只包含 `XDG_DATA_HOME`、`OPENCODE_CONFIG_DIR` 和固定 `opencode serve` 参数，不包含 token、Cookie、用户 prompt 或 API key。

### 2026-06-27 - 运行管理指标趋势图 JVM 内存 y 轴单位变成 G

- Why: 优化 JVM 内存监控趋势图的 y 轴标签展示，使其更具可读性并节省横向布局空间。
- What: 修改 `frontend/apps/agent-web/src/components/settings/RuntimeMetricChart.vue` 与 `RuntimeManagementPanel.vue`：
  - `RuntimeMetricChart` 新增 `yAxisUnit?: string` 属性。
  - 在 `yAxis` 中判断当配置 `yAxisUnit` 时，追加 `axisLabel: { formatter: '{value} G' }` 格式化标签。
  - 在数据映射阶段，判断当 `yAxisUnit` 为 `"G"` 时，将原始字节数据转换为二进制 G（即除以 `1024 * 1024 * 1024`）。
  - 对 series 的 `valueFormatter` 也进行判断，当 `yAxisUnit` 为 `"G"` 时，将悬浮提示（Tooltip）里的数据值格式化为保留 2 位小数并带 `" G"` 单位后缀的格式。
  - 在 `RuntimeManagementPanel.vue` 中的 JVM 内存趋势图组件上配置 `yAxis-unit="G"`。
- How: 通过在图表组件层将原始数据转化为 GB 单位使得 ECharts 能够按 GB 刻度自动进行平滑轴刻度划分，并配以 axisLabel 与 valueFormatter 达成视觉完美统一。
- Result: JVM 内存趋势图的 y 轴标签从 raw 字节（如 `5,000,000,000`）成功显示为更加直观的 GB 数值（如 `1 G` 到 `5 G`），Tooltip 提示也同步以 GB 显示，性能无影响，类型检查与单元测试全部通过。

### 2026-06-27 - 运行管理服务器指标历史连续性复核与类型修复

- Why: 用户要求实现 Java 重启后服务器 CPU/内存/磁盘历史连续性；复核当前 HEAD 时确认 server-key 实现和文档已存在，但前端 typecheck 被侧栏样式对象的 `pointerEvents` 类型推断阻塞。
- What: 确认 `test-agent:runtime-metrics:server:{linuxServerId}` 保存服务器 CPU/内存/磁盘，`test-agent:runtime-metrics:backend:{backendProcessId}` 保存当前 JVM 指标，Redis 自身重启后的历史保留依赖 AOF/RDB；最小修复 `FigmaShell.vue` 中左右侧栏 style computed 的 `CSSProperties` 类型标注。
- How: 运行后端目标 Maven 测试、运行管理 Vitest 和 `@test-agent/agent-web` typecheck，定位并修复 `pointerEvents` 推断问题。
- Result: 运行管理指标连续性实现已在当前分支具备；本次补齐验证并解除前端类型检查阻塞。

### 2026-06-27 - 运行管理指标趋势图 5秒定时轮询平滑刷新优化

- Why: 确保监控指标趋势图展开时的数据保持实时性的同时，避免每次后台定时刷新导致整个图表闪烁/重新加载。
- What: 修改 `frontend/apps/agent-web/src/components/settings/RuntimeManagementPanel.vue`：
  - 在 `metricsQuery` 查询配置中，声明式追加 `refetchInterval: 5000` 选项。
  - 将模板中指标面板加载占位符的 `v-else-if="metricsQuery.isFetching.value"` 修改为 `v-else-if="metricsQuery.isLoading.value"`，使占位符仅在初次无缓存加载时显示，后台重刷时保持图表渲染。
- How: 结合 Vue Query 的 `refetchInterval` 选项与 `isLoading` （只在初次加载时为 true）特性，避免在后台重刷时销毁并重建图表 DOM 容器，实现数据平滑更新。
- Result: 趋势图展示期间保持每 5 秒自动无缝刷新，ECharts 动线平滑渲染且不产生任何闪烁或闪退，前端类型校验和 Vitest 单元测试全部通过。

### 2026-06-27 - 运行管理指标趋势图再次点击折叠收起优化

- Why: 增强指标监控交互的便利性，在拓扑列表中的后端进程行或容器行已经被选中（趋势图已展示）的情况下，再次点击该行应折叠/隐藏对应的指标趋势图。
- What: 修改 `frontend/apps/agent-web/src/components/settings/RuntimeManagementPanel.vue`：
  - 修改 `selectContainer` 与 `selectBackendProcess` 点击处理函数：如果被点击的 ID 与当前处于选中状态的 ID 一致，则将选中的监控对象 `selectedMetricsTarget` 设为 `null`，从而触发趋势图组件的销毁；否则切换至新行并更新趋势图。
- How: 增加对选中行 ID 的条件对比逻辑，实现列表行点击的 Toggle 展开/折叠自锁切换效果。
- Result: 允许再次点击同一行来快速收缩指标趋势图，操作体验更加平滑；前端编译和 Vitest 单元测试全部通过。

### 2026-06-27 - 进入系统管理自动折叠左右侧栏及自动恢复

- Why: 满足用户对系统管理纯净专注视图的交互要求，在进入系统管理面板时，自动收起左侧工作空间（目录树）与右侧聊天对话面板；当切回编辑器时，能够自动恢复进入前的折叠/展开状态。
- What:
  - 修改 `frontend/apps/agent-web/src/components/FigmaShell.vue`：新增 `showLeftPanel` 属性并监听变化，使左侧侧边栏能被父组件驱动和同步状态。
  - 修改 `frontend/apps/agent-web/src/components/AgentWorkbench.vue`：
    - 提升左侧展开状态 `leftPanelOpen` 为 workbench 级 ref，并新增备份状态的 `savedLeftPanelOpen` 和 `savedRightPanelOpen`。
    - 监听 `centerMode`：当切入 `system` 模式时，自动备份当前左右侧栏状态，并将其置为 `false`；从 `system` 模式切出时，恢复备份的状态值。
    - 绑定 `FigmaShell` 的 `:show-left-panel` 与 `@toggle-left-panel` 事件。
    - 简化“系统管理”按钮的点击事件，使其与 watch 逻辑解耦。
- How: 状态提升加 Vue watch 切换钩子，非入侵式管理侧栏视图联动。
- Result: 进入管理页面自动收起侧栏，返回编辑器自动复原，页面响应迅速；类型检查与单元测试全部通过。

### 2026-06-27 - 系统管理侧边栏菜单图标化与悬浮提示优化

- Why: 优化系统管理侧边栏导航，仅保留图标以使菜单栏紧凑，并通过悬浮气泡（Tooltip）展示菜单对应的文字，从而提升整体视觉的现代感和空间利用率。
- What: 修改 `frontend/apps/agent-web/src/components/system/SystemManagementPanel.vue`：
  - 将导航按钮包裹在 Element Plus 的 `el-tooltip` 组件中，悬浮方向设为 `right`，显示对应菜单 label。
  - 使用无障碍隐藏类 `ta-system-menu-text` 把 `span` 从视觉上隐藏，但不破坏 DOM 结构与自动化测试兼容性。
  - 将 `.ta-system-menu` 宽度由 `180px` 缩减为 `52px`，内含按钮全部设为居中的 `36px * 36px` 规格，图标尺寸调整为 `18px`。
- How: 结合 Element Plus Tooltip 组件与 Visually Hidden CSS 类，以非破坏性方式达成紧凑的悬浮侧边栏交互。
- Result: 页面导航区整洁且切换正常，测试与 TypeScript 校验均完全通过。

### 2026-06-27 - 运行管理指标趋势图展示位置优化

- Why: 在拓扑状态面板上，当选中某个后端 Java 进程或容器时，对应的指标趋势图应直接出现在该列表的下方，而不是始终在整个页面的最下面，从而提升监控数据的关联性和视觉交互体验。
- What: 修改 `frontend/apps/agent-web/src/components/settings/RuntimeManagementPanel.vue`：
  - 将原先始终渲染在最底部的指标趋势面板（`ta-runtime-metrics-panel`）移除。
  - 在 `.ta-runtime-grid` 的 `后端 Java 进程` 列表组件下方直接添加条件渲染的后端指标趋势图组件（当 `selectedMetricsTarget.type === 'backend'` 时显示）。
  - 在 `容器` 列表组件下方直接添加条件渲染的容器指标趋势图组件（当 `selectedMetricsTarget.type === 'container'` 时显示）。
- How: 充分利用网格布局单列堆叠的特性，将条件渲染的趋势图面板作为列表块的兄弟节点插入网格中，使其在被选中时自动且顺畅地向下展开。
- Result: 点击对应列表项后，趋势图会即时且准确地展现在相应列表的正下方，页面结构更符合直觉；类型检查（typecheck）和单元测试全部通过。

### 2026-06-27 - 运行管理拓扑状态列表布局调整

- Why: 拓扑状态下 Linux 服务器、后端 Java 进程、容器、管理进程原先采用 2 列网格并排布局，列表内容较多时横向挤压严重，需要改为每个列表独占一行（100% 宽度）。
- What: 修改 `frontend/apps/agent-web/src/components/settings/RuntimeManagementPanel.vue`：
  - 将 `.ta-runtime-grid` 的 `grid-template-columns` 从 `repeat(2, minmax(0, 1fr))` 改为 `1fr`，使所有列表块（包括原先未设置 `is-wide` 属性的 4 个列表）均占满一行。
- How: 仅调整布局的 CSS 网格规格，不修改模板结构或 DOM 标签，不改动任何业务逻辑。
- Result: 四个拓扑列表及底部的连接列表均呈现独占整行的效果，解决多列挤压带来的表格横向滚动体验问题。类型检查与单元测试完全通过。

### 2026-06-27 - manager 最大进程数改为通用参数下发

- Why: 此前 `MaxProcesses` 只能由 Go manager 启动时从 env `OPENCODE_MANAGER_MAX_PROCESSES` 读取（不可变），改上限需改 env 并重启 manager，无法在线调整。需把最大进程数纳入通用参数表在线可调，前端修改后实时推送给所有 manager。
- What: `common_parameters` 新增全局参数 `OPENCODE_MANAGER_MAX_PROCESSES`（`platform=all`，默认 8，migration `V20260627020000`，时间戳避让 SSH key 的 `V20260627010000`）。Go 侧 `process.Manager` 用 `atomic.Int64` 持有运行时上限，新增 `MaxProcesses()`/`SetMaxProcesses()`（按端口池 clamp、`<1` 拒绝），`Start` 容量判断与 `topologyMessage` 改读生效值；`protocol.go` 新增 `configUpdate` 类型，`supervisor.go` `readLoop` 处理该帧并热更新。Java 侧新增 `ManagerControlProtocol.TYPE_CONFIG_UPDATE` + `ManagerControlMessage.configUpdate` 工厂、`ManagerConnectionRegistry.broadcast`、`OpencodeManagerConfigSyncService`（读参数→register 补推/事件广播）、`CommonParameterUpdatedEvent`（domain）；`CommonParameterManagementApplicationService.updateValue` 发布事件；`ManagerControlWebSocketHandler` register 后补推。env 降为启动兜底。
- How: Go `go test ./...` 全绿（含 SetMaxProcesses clamp、Start 运行时容量、configUpdate 应用+heartbeat 上报生效值）；Java `mvn clean test` 全绿（含 `OpencodeManagerConfigSyncServiceTest`、`ManagerConnectionRegistry.broadcast`、`CommonParameterManagementApplicationServiceTest` 事件发布断言、handler 构造器适配）；同步 http-api/event-stream/database.md 与 opencode-manager/opencode-runtime README。
- Result: 前端在「通用参数管理」改 `OPENCODE_MANAGER_MAX_PROCESSES` 即可经 WS 控制面广播给所有 manager 热更新；manager 注册时自动获取权威值；后端不可达或参数缺失时回退 env，旧 manager 不识别 `configUpdate` 静默忽略，向后兼容。本条实现先前 session-log 中「migration 版本冲突」条目描述的重命名结果。

### 2026-06-26 - 头像菜单未分配进程状态文案修改

- Why: 增强交互指向性与文案表意清晰度，头像菜单中原先展示的“未分配”文案需要调整为更明确的“待分配专属进程”文案。
- What: 修改 `frontend/apps/agent-web/src/components/FigmaShell.vue` 中头像菜单 opencode 状态逻辑的未分配分支返回值，将 `text: "未分配"` 变更为 `text: "待分配专属进程"`。
- How: 仅修改组件内部的 computed 渲染文本，不影响底层 tone 状态码及后端的任何流程与数据。
- Result: 头像下拉菜单在未分配 opencode 进程时显示“待分配专属进程”，排版整齐，编译及测试全部通过。

### 2026-06-26 - 用户头像菜单实时显示 opencode 服务状态

- Why: 用户需要点击右上角头像时实时查看当前账号的 opencode 服务分配与健康状态，区分未分配、运行中和未运行，并展示服务器 IP 与内部端口。
- What: `/api/internal/agent/{agentId}/processes/me` 兼容新增 `serviceStatus` / `serviceAddress`，后端复用现有 manager/local gateway 健康检测链路计算 `UNASSIGNED`、`RUNNING`、`NOT_RUNNING`；前端头像菜单打开时强制 refetch 当前用户进程状态，并用灰/绿/红显示“未分配 / 运行中(ip:port) / 未运行(ip:port)”。
- How: 在 `UserOpencodeProcessStatusResponse` 增加兼容构造器与头像菜单状态枚举，`UserOpencodeProcessAssignmentService.status` 对无 ACTIVE 绑定返回未分配，对绑定进程健康失败/缺失返回未运行；`FigmaShell` 新增状态行和刷新事件，`AgentWorkbench` 传入现有 Vue Query 数据与 refetch。
- Result: 不新增数据库 migration，不修改环境文件，不改变右侧聊天面板依赖的 `READY / NEEDS_INITIALIZATION / UNAVAILABLE` 门禁语义；目标后端测试、`backend-api` Vitest、`backend-api`/`agent-web` typecheck 和头像菜单 Playwright 用例通过。

### 2026-06-26 - opencode-manager 改为读取 Java 写出的服务器 IP 文件

- Why: Go manager 运行在容器内时无法可靠识别宿主服务器 IP，继续用容器网卡 IP 会导致后端统计服务器容器数、同服务器重建和 `baseUrl=http://{linuxServerId}:{port}` 规则失真。
- What: Java 后端在 socket 控制面启动时把解析出的服务器 IPv4 写入 `.serverip`（默认 `/data/.testagent/.serverip`，本地脚本改到 `.tmp/dev-services/.serverip`）；Go manager 非 Windows 启动读取该文件并最多等待 30 秒，Windows 本机开发态直接探测本机非回环 IPv4；`OPENCODE_MANAGER_LINUX_SERVER_ID` 不再由脚本注入。
- How: `LinuxServerIpResolver` 增加 listen-url 非回环 IPv4 优先逻辑，`ServerIpFileWriter` 负责单行覆盖写入；Go 配置加载改为可注入运行时，覆盖 `.serverip`、Windows、containerId 和 discovery URL 派生分支；同步一键脚本、脚本校验和 opencode-manager/API/部署文档。
- Result: manager 上报的 `linuxServerId` 固定为服务器 IPv4，`containerId` 只表示容器或 Windows 机器名。本次不涉及数据库 schema；`TestAgentRuntimePropertiesBindingTest` 中 3 个 guo profile 断言仍是既有失败（session log 早前已记录），与本次 `.serverip` 改动无关。

### 2026-06-26 - 全局字体与排版样式优化

- Why: 统一平台视觉体验，提升可读性。用户要求将默认字体替换为 Geist 族与 Noto Sans SC 组合，并规范化标题、正文、说明及代码块的字号字重参数。
- What:
  - 引入网络字体：在 `index.html` 中配置 Google Fonts 加载 `'Geist'`、`'Geist Mono'`、`'Noto Sans SC'` 三种字体，且在 `globals.css` 中添加 `@import url` 的后备引入。
  - 主题配置更新：在 `globals.css` 中的 Tailwind `@theme` 区声明 `--font-sans`（映射到 Geist & Noto Sans SC）与 `--font-mono`（映射到 Geist Mono），重映射底层组件工具类。
  - Element Plus 覆写更新：修改 `element-overrides.css` 对应变量，将 `--el-font-family` 切换为 Geist & Noto Sans SC，代码字体覆写为 Geist Mono。
  - 标签样式统一与尺寸微调：
    - `html`, `body` 采用新字族；
    - `body` 基础字号从 `14px` 放大到 `16px`（对应“正文/默认聊天内容”字号为 16px，字重 400）；
    - `button`, `.el-button` 设置字号为 `14px`，字重 `500`；
    - `input`, `textarea`, `select`, `.el-input`, `.el-textarea` 默认字号为 `16px`，字重 `400`；
    - `pre`, `code`, `.ta-codeblock`, `.font-mono` 使用 `Geist Mono` 字体，字号为 `14px`，字重 `400`；
    - 统一标题标签：`h1` / `.ta-welcome-h1` 设为 `28-32px`/700，`h2` / `.ta-display` 设为 `24px`/600，`h3` 设为 `20px`/600。
- How: 仅修改 `index.html` 外部引用以及 `element-overrides.css`/`globals.css` 基础样式覆盖，不干扰前端组件具体实现。
- Result: 页面字体完美替换为 Geist 系列与 Noto 简体中文，排版尺寸符合统一规范。类型检查与单元测试完全通过。

### 2026-06-26 - 服务器工作空间目录选择器优化为 macOS Finder 风格

- Why: 用户反馈服务器工作空间目录选择器布局简易，希望参考 macOS Finder 的文件管理风格进行界面优化，且要求解决文件夹选中后窗口尺寸跳动问题、精简多余列信息、并支持通过点击左侧折叠箭头 inline 展开子目录结构。
- What:
  - 窗口尺寸与宽高比例调整：调整弹窗高度控制为主界面的 75% (`h-[75vh]`)，并在之前宽度基础上增加了 20% (`w-[1000px]`)，同时保证尺寸在任何文件夹切换下绝对稳定。
  - 列信息精简与一整行显示：去掉了原 Finder 风格中多余的“修改日期”、“大小”和“种类”列，让文件夹名称占满整行，视觉更聚焦。
  - 折叠展开与单点跳转交互（引入新组件 [ServerWorkspaceDirectoryNode.vue](file:///Users/huang/workspace/intelligent-test-agent-gitee/frontend/apps/agent-web/src/components/ServerWorkspaceDirectoryNode.vue)）：
    - 文件夹左侧的 chevron 旋转箭头 `>` 为折叠/展开开关。点击 `>` 将 inline 展开显示子目录树而不发生全局页面跳转；
    - 点击文件夹名称文字或图标时，才会执行全局的下一级目录导航（向父组件发出 `navigate` 并更新顶部面包屑）。
  - 路径导航与工具栏：包括后退/前进按钮、面包屑 Location Bar 以及“选择此目录”主按钮。
  - 面包屑自动滚动：为 Location Bar 面包屑容器添加了自动向右端滚动的 watch 监听器，在目录切换或弹窗首次打开时自动滚动到最右端，确保在层级较深时最新/当前目录始终可见。
  - 任务栏状态栏：在目录树列表框底部增加了一个 macOS Finder 风格的状态栏，用较小字体 (`text-[10.5px]`) 与等宽字体 (`font-mono`) 展示当前的完整路径，并支持直接选定复制 (`select-all`)。
- How: 拆分出递归组件 `ServerWorkspaceDirectoryNode.vue`，利用 computed/refs 管理各层级文件夹独立的展开、加载与缓存状态。
- Result: 对齐 macOS Finder 体验，解决了布局尺寸抖动，实现了完美的树状文件夹折叠展开浏览。类型检查及单元测试完全通过。

### 2026-06-26 - 恢复 opencode 初始化按钮并重启本地 manager

- Why: 合并远程后，opencode 进程状态默认折叠成右下角圆点，非 READY 时“初始化进程”按钮也被收起；同时本地 Go manager 内存里残留 4096 已管理状态，导致 wr 用户初始化返回 `port 4096 is already managed`，但本机 4096 实际没有 opencode 监听。
- What: `FigmaChatPanel` 改为仅 READY 时收起为圆点，非 READY 状态自动展开并显示初始化按钮；`AgentWorkbench` 的进程查询改按登录态启用，loading 只在首次取数时阻塞；补充非 READY 初始化按钮组件测试。按现有 `.env.test` / 200 数据库联调环境重启 `test-agent-opencode-manager`，重新初始化 wr 的 4096 进程。
- How: 先用 3000 页面和 `/api/internal/agent/opencode/processes/me` 复现 NEEDS_INITIALIZATION；确认 200 库 wr 绑定 `ocp_e295...` 处于 UNHEALTHY 且 4096 无监听；重启 manager 后调用初始化 API，manager 派生新的 opencode 进程。
- Result: 3000 页面显示 `opencode 进程可用` READY 圆点；真实发送“只回复 OK”后 run 进入 `SUCCEEDED`，SSE 正常打开并返回 `OK`。聚焦 Vitest 与 Playwright 初始化门禁用例均通过；当前服务仍是 `.env.test` + 192.168.100.200 数据库联调态。
### 2026-06-26 - 优化工作台底部工作空间切换按钮文案与图标

- Why: 增强用户体验，当未选中具体工作空间版本时，底部工作区切换按钮默认文案不应为动态的应用名后缀（如 `F-COSS 工作空间`），而应统一为 `切换工作空间`，并且图标应从 `Layers` 改为更具表达力的 `ArrowLeftRight` 双向箭头。
- What: 修改 `frontend/apps/agent-web/src/components/WorkbenchFooter.vue`：
  - 将 `lucide-vue-next` 的 `Layers` 图标替换为 `ArrowLeftRight`。
  - 在 `triggerLabel` 计算属性的 fallback 分支，将 `props.appName ? `${props.appName} 工作空间` : "应用工作空间"` 直接改为返回 `"切换工作空间"`。
  - 在 template 中更新图标组件 `<Layers>` 为 `<ArrowLeftRight>`。
- How: 仅修改前端组件的 Vue 模版及 computed 计算属性，不改动 TypeScript 业务逻辑，且不改变 Props 结构。
- Result: 按钮成功展示“切换工作空间”与双向箭头图标，符合界面重构意图；运行 `apps/agent-web/tests/WorkbenchFooter.test.ts` 以及 `@test-agent/agent-web` 的类型检查（typecheck）均完全通过。

### 2026-06-26 - 后端启动禁用本机 JVM 代理

- Why: 测试环境 PostgreSQL/Redis 端口直连可达，但后端启动日志中 PostgreSQL 连接超时栈包含 `SocksSocketImpl`，本机 Java 运行时会从 macOS 系统代理继承 HTTP/HTTPS/SOCKS 代理，导致 JDBC 连接被代理影响。
- What: `restart-dev-services.sh` 和 `tools/dev-backend-run.sh` 启动后端 Java 进程时统一追加 JVM 参数，关闭 `java.net.useSystemProxies` 并清空 HTTP/HTTPS/FTP/SOCKS proxy host/port；补充 `tools/verify-dev-scripts.sh` 回归校验和本地启动文档。
- How: 先用 `nc` 验证外部 PostgreSQL 5432、Redis 6379 端口连通，再用 Java 运行参数检查确认清空 `-D*proxy*` 后 JVM 代理属性不再指向 `127.0.0.1:8888/8889`；脚本层只影响后端 Java 进程，不修改 `.env.local` / `.env.test`。
- Result: 后续通过一键重启或后端单独启动时，数据库和 Redis 连接不再走本机 SOCKS/HTTP 代理；浏览器、pnpm、Go manager 等其他进程仍按各自环境处理代理。

### 2026-06-26 - 修复一键重启前端构建类型错误

- Why: `./restart-dev-services.sh` 在 `corepack pnpm build` 阶段失败，真实错误来自 `agent-web` 的 `vue-tsc` 类型检查，而不是服务 kill/start 逻辑。
- What: 补齐 `shared-types` 中用户管理（测试）DTO：`UserManagementUser`、`CreateUserPayload`、`RoleOption`；修正 `FigmaChatPanel.vue` 中展示消息与原始 `AgentMessage` 联合类型混用；修正 `runtime-reducer.ts` 按 user/assistant 分支构造 `AgentMessage`。
- How: 先用 `corepack pnpm --filter @test-agent/agent-web build` 复现附件中的 TypeScript 错误，再按错误源头最小修复类型定义和联合类型收窄，不改 `restart-dev-services.sh`。
- Result: `corepack pnpm --filter @test-agent/agent-web build`、相关 Vitest、`backend-api`/`agent-chat` typecheck 和 `tools/verify-dev-scripts.sh` 均通过；未执行完整一键重启，避免主动停止当前服务。
### 2026-06-26 - 工作台侧边栏布局调整与一级目录可折叠重构
### 2026-06-26 - DiffViewer 标签精简与 Monaco 滚动条细线化、聊天气泡底色统一

- Why: 用户截图标注 (1) DiffViewer 右侧「本地修改 (可编辑，编辑完成后按 Cmd+S 保存)」文案过长且未贴右；(2) Monaco diff 视图右侧滚动条太粗、抢视觉；(3) 右侧对话气泡底色在用户消息（#f4f4f5 灰）与背景（#fff 白）之间反复切换，希望统一。
- What:
  - `frontend/packages/diff-viewer/src/DiffViewer.vue`：split 视图右侧列加 `justify-end` 贴右，统一文案为「本地修改 · 可编辑（Cmd+S 保存）」，基线/统一视图同步精简为「基线版本（只读，历史提交代码）」「统一视图 · 可直接编辑（Cmd+S 保存）」，全角括号替换半角；Monaco diff editor 初始化选项新增 `scrollbar: { vertical: "visible", horizontal: "visible", verticalScrollbarSize: 6, horizontalScrollbarSize: 6, useShadows: false }`，与普通编辑器对齐。
  - `frontend/apps/agent-web/src/components/FigmaChatPanel.vue`：`.figma-chat-bubble--user` 与 `.figma-chat-avatar--user` 的 `background` 由 `#f4f4f5` 改为 `transparent`，让用户气泡与背景同色，整条对话保持单一底色。
- How: 仅模板 + scoped CSS / Monaco 配置改动，不动 TypeScript 业务逻辑、emit、store。Monaco scrollbar 配置是单点插入 initMonaco，未影响 `viewMode` / `source` watch 的后续 updateOptions 流程。
- Result: 右侧标签简明贴右；Monaco diff 滚动条细线化与 Monaco Editor 一致；用户气泡不再独立染色，整条对话底色统一。`packages/diff-viewer/tests` 4/4 通过；`@test-agent/diff-viewer` typecheck 通过；FigmaChatPanel 既有 2 条失败与本次改动无关（pre-existing `role` 类型推断问题）。
- Verification: `corepack pnpm exec vitest run packages/diff-viewer/tests`；`corepack pnpm --filter @test-agent/diff-viewer typecheck`；`git diff --check`。

### 2026-06-26 - DiffViewer 跟进：标题行进一步精简、diffViewport 强制细线化

- Why: 用户反馈"完全没有效果"。经 DevTools 检视，标题行右侧文案已生效，但 `pl-4` 让内容仍与左边框有间距、且行高过大；Monaco 滚动条对应的 DOM 是 `.diffViewport`（默认 30×20px，由 Monaco 内部 `ENTIRE_DIFF_OVERVIEW_WIDTH = ONE_OVERVIEW_WIDTH * 2 = 15 * 2` 写死 inline style），单靠 `scrollbar.verticalScrollbarSize` 选项无法影响它。
- What:
  - `frontend/packages/diff-viewer/src/DiffViewer.vue` 标题行：`px-4` → `px-3`、`py-1.5` → `py-0.5`、`text-[11px]` → `text-[10.5px]`、`gap-1.5` → `gap-1`，右列 `pl-4` → `pl-2` 并追加 `pr-0.5` 贴最右，统一视图同步。
  - 新增 scoped 样式覆盖 Monaco diff overview：`:deep(.monaco-diff-editor .diffOverview)` 与 `:deep(.monaco-diff-editor .diffViewport)` 都用 `width: 6px !important` 覆盖 inline 30px；height 由 `state.getSliderSize()` 算出后又被 `setHeight` 写 inline，CSS `height` 不会跟动，但 width 压住后视觉上即变细线；`:hover` / `:active` 分支同步压回 6px 防止 hover 时反弹。
- How: 标题行为 Tailwind class 调整；新增规则都在 `<style scoped>` 顶部独立注释块，`.diffOverview` 与 `.diffViewport` 都用 `!important` 压过 Monaco 写死的 inline style。`.slider` 的 `border-radius: 3px` 保留与细线视觉一致。
- Result: 标题行更紧凑、右侧文案贴到修改区最右侧；Monaco diff overview ruler 视觉宽度从 30px 压到 6px，与普通细滚动条对齐。`packages/diff-viewer/tests` 4/4 通过。
- Verification: `corepack pnpm exec vitest run packages/diff-viewer/tests`；`git diff --check`；浏览器需硬刷新（Cmd+Shift+R）以避免 HMR / 缓存沿用旧 CSS。

### 2026-06-26 - DiffViewer 第三轮：composer 底色、右侧 padding、diffOverview left 修正

- Why: 用户用 DevTools 框选三个 div 反馈：(1) `figma-chat-composer` 底色 #f5f5f5 与 `.figma-chat-scroll`/`.figma-chat-root`（#fff）不一致，对话区又是"一会灰一会白"；(2) 标题行右列还有 `pr-0.5`，没贴到最右；(3) `.diffOverview` 已被压成 6px 但仍靠 Monaco 算的 `left = width - 30` 偏移，右侧留出 ~24px 空隙。
- What:
  - `FigmaChatPanel.vue`：`.figma-chat-composer` 的 `background` 从 `#f5f5f5` 改为 `transparent`，让 root (#fff) 透出来，整条对话（消息 / 输入框 / 工具行）统一单一底色。
  - `DiffViewer.vue` 标题行右列：删除 `pr-0.5`，让 `▶ 本地修改 · 可编辑（Cmd+S 保存）` 真正贴到右边缘；统一视图同步去掉 `pr-0.5`。
  - `DiffViewer.vue` scoped 样式：`.diffOverview` 增加 `left: auto !important; right: 2px !important;` 把它从 Monaco 的 `left` 锚定切到 `right` 锚定，宽度变 6px 后视觉上也贴到容器右边。
- How: 全部为 CSS / Tailwind class 微调，不动业务逻辑。`left: auto` + `right` 是 CSS 定位的标准做法，能在 inline `left` 被 Monaco 重写时仍由 `right` 决定最终位置。
- Result: 对话区所有层（root / scroll / composer）都显示同一个白色；标题行右侧文案贴边；diff overview ruler 真正贴右且细线化。`packages/diff-viewer/tests` 4/4 通过。
- Verification: `corepack pnpm exec vitest run packages/diff-viewer/tests`；`git diff --check`；浏览器需硬刷新（Cmd+Shift+R）以让 HMR 后的 scoped style 重新挂载。

### 2026-06-26 - DiffViewer 第四轮：标签居中、隐藏 overviewRuler 画布、滚动条再细

- Why: 用户用 DevTools 框选元素反馈：(1) 标题行"◀ 基线版本"和"▶ 本地修改"应放到 diff 两个文件**中间**（不再左右两列），但箭头保留；(2) 右侧还能看到两个 `canvas.diffOverviewRuler`（original / modified 各一，宽 15px）露出灰白块，等于多了一根"下滑"；(3) 标题行 `border-slate-200` 上下两根线太丑，应对齐项目里其他分隔线色号（#e4e4e7 居多）和字号（11px / 12px）。
- What:
  - `DiffViewer.vue` 标题行：layout 从 `grid grid-cols-2` 改为 `flex items-center justify-center`，两个标签用 `|` 分隔符居中并列；去掉 `border-b border-slate-200` 上下边框，背景由 `#f8fafc` 改为更柔和的 `#fafafa`；字号 `text-[10.5px]` → `text-[11px]`、padding `py-0.5` → `py-1` 与 `globals.css` 内 `font-size: 11px/12px` 的小标签风格对齐；统一视图同步。
  - Monaco 滚动条：`verticalScrollbarSize: 6` → `4`，`.monaco-scrollable-element` / `.slider` / `> .scrollbar` 全部压到 4px，与细线视觉保持一致。
  - 新增 scoped 样式隐藏两个 overview ruler 画布：`:deep(.monaco-diff-editor canvas.diffOverviewRuler.original)` 与 `.modified` 都 `display: none !important`，避免它们在内容少时露出 15px 宽灰白条。
- How: 布局从 grid 改 flex，分隔符用一个轻量 `text-slate-300 select-none` 的 `|` 字符，节省组件引用；canvas 隐藏用 `!important` 避免被 Monaco 重渲染时再出现。字号 / 色号参考 `FigmaFileExplorer.vue:286,348`、`WorkbenchFooter.vue:716,732`、`AgentConfigPanel.vue:485` 等。
- Result: 标题行居中并列、无明显边框线、字号与项目其他小标签一致；右侧不再有多余的 overviewRuler 画布；滚动条统一 4px。`packages/diff-viewer/tests` 4/4 通过。
- Verification: `corepack pnpm exec vitest run packages/diff-viewer/tests`；`git diff --check`；浏览器需硬刷新（Cmd+Shift+R）让 HMR 后的 scoped style 重新挂载。

### 2026-06-26 - DiffViewer 第四轮：overview ruler 隐藏、slider 贴边、toolbar 去边框、进程状态可折叠圆点

- Why: 用户用 DevTools 框选：两个 `canvas.diffOverviewRuler`（original / modified 各 15px 宽）跟 `.diffViewport` 重复显得很重；slider 离右边还有 2px；工具栏 `border-b` 太抢眼；进程状态卡片占纵向空间，希望默认收起为带渐变虚化的小圆点，点击展开。
- What:
  - `DiffViewer.vue`：工具栏 `border-b border-slate-200` 去掉，背景与下方合并；scoped 样式新增 `:deep(.monaco-diff-editor canvas.diffOverviewRuler.original/modified) { display: none !important }` 隐藏两幅画布；`.diffOverview` 与 `.diffViewport` 的 `right: 2px` 改 `right: 0` 完全贴边。
  - `FigmaChatPanel.vue`：新增 `processStatusCollapsed` ref（默认 `true`）+ `toggleProcessStatus`；template 拆为两段——收起态 `<button class="figma-chat-process-dot">`、展开态保留原 `.figma-chat-process-status` 卡片并整体可点击收起；样式新增 `.figma-chat-process-dot`：12×12 圆点 + `::after` 虚化渐变（filter: blur(8px)），`is-ready` 绿（#34d399 → rgba(24,169,120,.25) radial-gradient），`is-blocking` 红，hover scale(1.15)。状态卡本身加 `cursor: pointer` 和 `role="button"`/`tabindex="0"` 支持键盘。
- How: 收起/展开纯前端状态，不动 store / props。dot 的虚化用 `::after` + `filter: blur`，不依赖额外 DOM，背景 `inherit` 保持跟 dot 主色一致。
- Result: overview ruler 画布消失、slider 完全贴右、工具栏去线、进程状态默认一颗右下角圆点可点开。`packages/diff-viewer/tests` 4/4；`FigmaChatPanel` 既有 2 条失败 pre-existing（已 `git stash` 验证），本次无新增回归。
- Verification: `corepack pnpm exec vitest run packages/diff-viewer/tests`；`git diff --check`；浏览器需硬刷新（Cmd+Shift+R）让 scoped CSS 重新挂载。

### 2026-06-26 - DiffViewer 第五轮：slider 显式 left: auto 真的贴最右

- Why: 用户反馈 slider 仍能往右挪。原因是 Monaco 给 `.diffViewport` (slider) inline 设了 `left: 0`；
  我之前只设 `right: 0`，同时存在的 `left: 0` + `right: 0` 会让元素相对父容器左对齐（CSS 里 left 优先于 right），
  虽然父容器 `.diffOverview` 已经被 right: 0 钉死在最右，slider 实际位置已经贴边，但浏览器渲染时
  slider 的 inline `left: 0` 仍然可见，让人误以为没贴边。
- What: `DiffViewer.vue` 的 `.diffViewport` 新增 `left: auto !important`，让 `right: 0` 单独生效；
  `.diffOverview` 加 `margin: 0 !important` 防止 Monaco 默认 margin 把整条再往左推 1px。
- How: 纯 CSS override，不动 Monaco 初始化逻辑。
- Result: slider 的 inline style 仍带 `left: 0`（Monaco 行为），但 CSS `left: auto !important` 把它吃掉，
  真正由 `right: 0` 锚定到 `.diffOverview` 最右。`packages/diff-viewer/tests` 4/4 通过。
- Verification: `corepack pnpm exec vitest run packages/diff-viewer/tests`；浏览器需硬刷新（Cmd+Shift+R）。

### 2026-06-26 - UI 三项改版：Diff light 主题、三栏底部 Footer 对齐、聊天输入卡片化

- Why: 用户要求 (1) Monaco Diff 编辑器切为 light 风格与工作台白色主题匹配；(2) 不管切换到哪个功能，底部那一行应该都存在且高度一致；(3) 聊天面板输入框加宽，把模型选择、新建对话、附件上传挪到输入框内部下方，整体像现代 ChatGPT 风格。
- What:
  - `DiffViewer.vue`：定义 `ta-diff-light` Monaco 主题（白底，绿 `#10b981` / 红 `#ef4444` 差异色），所有暗色 CSS 类改为浅色版本，左右分栏头部提示文案优化，"保存 (Cmd+S)"按钮改为 amber 风格。
  - `AgentWorkbench.vue`：diff 模式底部加 `<WorkbenchFooter :write-path="..." :dirty="..." show-save>`，system 模式底部加空白 `<WorkbenchFooter />`，保证三栏底部 36px 高度线条持续存在。
  - `FigmaShell.vue`：右侧聊天面板默认宽度从 320px → 380px。
  - `FigmaChatPanel.vue`：将 `figma-chat-composer` 内部重构为统一 `figma-chat-input-card` 圆角卡片，卡片内 textarea 占满宽，底部工具行（附件 Upload、模型选择 ChevronDown、新建对话 Plus、发送/停止圆形按钮）横排；卡片聚焦时蓝色描边；卡片外背景改为 `#f5f5f5`；根部末尾追加 `figma-chat-footer`（36px 白底带顶边框）与左中面板底栏高度对齐。
- How: 纯 template + scoped CSS 改动，未修改任何 TypeScript 业务逻辑。既有 `isDirty`、`handleSave`、`dirtyChange` emit 均已在上轮实现，本轮直接连接到 footer。
- Result: 三栏底部线条高度一致；Monaco Diff 呈 light 白底风格；聊天输入区整合为现代卡片样式，所有操作按钮集中在一个统一容器内。TypeScript 中仍有来自 `packages/agent-chat/src/runtime-reducer.ts` 和 `FigmaChatPanel.vue` 的既有类型错误（role 类型推断问题，与本次改动无关）。
- Pitfalls: 上轮已将 `WorkbenchFooter` 和 diff light 代码写入 Vue 文件，但本轮才真正确认已生效；session 上下文切换点注意确认已有实现不要重复。

### 2026-06-26 - 工作区变更管理面板(Git Source Control)重构与美化

- Why: 增强工作台变更标签页，支持以极佳的 Git 样式展示未暂存与已暂存文件，并支持暂存、提交、推送及手工拉伸。在 Diff 展现上采用极简的 Monaco 左右对比（Side-by-Side Split）视图，并且支持差异文件的行内实时编辑修改。
- What:
  - 移除了 commit 选项复选框（SignOff、No-Verify、Amend），简化了提交表单。
  - 重构了 `DiffViewer.vue`：当审查应用工作区或 Agent 变更时，隐藏左侧的文件与 Hunks 列表，隐藏头部 VCS、Split/Unified 下拉框、刷新按钮及 VCS Diff 标题，只保留 Monaco 对比和 Hunk 导航。
  - 实现了单击列表文件即可显示对应文件的实际 Diff 对比效果，并修复了 Diff 文件选择被覆盖重置的 bug。
  - 修复了 `DiffViewer.vue` 中由于初始 `files` 列表为空导致 Monaco 编辑器容器未在 DOM 中渲染，从而使 Monaco 未能成功初始化的问题。
  - 支持在 VCS/Agent 差异对比模式下直接对右侧（Modified 修改侧）代码进行编辑，并在头部提供了未保存修改的状态指示灯与“保存 (Cmd+S)”按钮，支持通过快捷键 `Ctrl+S` / `Cmd+S` 直接保存修改回写后端文件。
  - 修复了当对话框面板展开导致编辑器宽度变窄时，Monaco 差异编辑器默认自动折叠为单栏（Unified/Inline）视图的问题，确保其始终保持左右对照。
  - 在“未暂存”与“已暂存”面板间加入了拖拽调节高度的分栏分割线。
- How: 在 `DiffViewer.vue` 与 `GitChangesPanel.vue` 中对多余的 UI 元素增加 `v-if` 条件过滤，清除 commit 复选框。将 `DiffViewer.vue` 的 `onMounted` 逻辑重构为对 `containerEl` 的 `watch` 侦听器，动态在 DOM 渲染后挂载 Monaco 差异编辑器，并在容器销毁时安全释放资源；配置 Monaco 差异编辑器对 vcs/agent 来源设置 `readOnly: false`，强制设置 `renderSideBySide: true` 左右分栏对比，并将 `useInlineViewWhenSpaceIsLimited` 显式配置为 `false` 以禁用窄宽度下的自动折叠降级，绑定 Cmd+S 键盘快捷键触发保存事件；在 `AgentWorkbench.vue` 中处理 `@save-file` 事件，在写盘成功后刷新 diff files 数据源。
- Result: Diff 视图变得极简专业且功能强大，支持首次加载时的稳定挂载，并在差异左右对照视图下提供了直观 of 即时修改、保存回写及实时 diff 重算渲染，在对话框拉伸或隐藏时始终保持清晰的左右对照版式，用户体验比肩专业开发工具。前端编译与校验全部通过。


### 2026-06-26 - 工作台侧边栏布局调整与折叠拖拽重构

- Why: 用户要求调整工作台文件区侧边栏的布局，移除顶部的“工作区”、“公共目录”、“Agent”切换按钮，并将“应用工作空间”（原工作区目录）和“agents”（原 Agent 面板）作为可折叠展开的一级目录。同时，修复浮动侧边栏折叠按钮在无工具栏情况下的重叠冲突，实现两一级目录间的上下拉动拖拽缩放，添加悬停显示工作区真实名称，以及移除 agents 底部多余的 git 发布提交模块。要求将切换工具栏（FolderTree/Search/GitBranch 切换栏）移到侧边栏最顶端以控制下面层级。
- What: 移除了 `FigmaFileExplorer.vue` 顶部的 `.figma-fe-toolbar`；将三 tab 切换工具栏（`ta-icon-tabbar`）提取并放置在 `FigmaFileExplorer.vue` 的最顶部，控制文件树/搜索/变更状态；将“应用工作空间”和“agents”移到切换工具栏的下方，做成平级的折叠目录，支持上下拖拽比例；为 `FileExplorer.vue` 提供了 `hideTabbar` 与 `activeTab` 属性以接收并适配父组件的切换状态；为切换工具栏增加了右内边距（`padding-right: 36px`），消除了它与侧边栏折叠按钮的重叠。
- How: 展开的一级目录分配 `flex: 1; min-height: 0` 保证内部滚动，折叠的目录分配 `flex: 0 0 auto`。利用 mousemove/mouseup 事件监听实现垂直拖拽调整 height 比例。将 `FileExplorer` 内部控制视图切换的 tabbar 剥离给外部的 `FigmaFileExplorer`，使切换逻辑完全受控；通过 `activeTab` 属性将选中的 tab 状态下发。
- Result: 侧边栏布局精简且完全符合 IDE 风格，两个主区域在折叠/展开/拖动时响应完美，无重叠或溢出，且 124 项前端测试全部通过。

### 2026-06-26 - 一键重启脚本默认切到 test 环境

- Why: 研发联调希望 `./restart-dev-services.sh` 不带参数时默认使用测试环境配置，并继续保证三服务重启前清理旧进程。
- What: 根目录 `restart-dev-services.sh` 默认 profile 从 `local` 改为 `test`，默认 dotenv 从 `.env.local` 改为 `.env.test`；保留 `--profile local|guo` 和 `--env-file` 覆盖；`TEST_AGENT_START_OPENCODE_MANAGER=auto` 改为按 `TEST_AGENT_OPENCODE_BASE_URL` 是否为本地地址决定是否启动 Go manager。
- How: 先在 `tools/verify-dev-scripts.sh` 增加失败用例，覆盖帮助文本默认值和远端 opencode baseUrl 不应触发 manager build/start；再最小修改脚本和稳定文档，不读取或修改 `.env.local` / `.env.test`。
- Result: `tools/verify-dev-scripts.sh`、`tools/verify-ai-docs.sh` 均通过；顺手补齐 `docs/deployment/database.md` 中校验脚本要求的“V10 opencode 用户进程管理表”历史表述，不改变实际迁移版本说明。

### 2026-06-26 - 为数据库表和字段添加中文注释

- Why: 项目中数据库表和字段缺少中文注释，不便于理解和维护；有数据样例的字段需要在注释中展示样例值。
- What: 新增 Flyway migration `V20260626210000__add_chinese_comments_for_all_tables.sql`，为以下核心表添加中文注释：
  - 核心运行表：`workspaces`、`sessions`、`runs`、`run_events`、`execution_nodes`、`routing_decisions`、`session_messages`、`agent_session_bindings`
  - 用户认证表：`users`、`user_login_logs`、`dictionaries`、`user_roles`
  - 应用配置表：`applications`、`application_members`、`code_repositories`、`application_repository_links`、`application_workspaces`、`user_ssh_keys`
  - 托管工作区表：`application_workspace_versions`、`personal_workspaces`、`user_global_workspace_preferences`、`user_application_workspace_preferences`、`workspace_sync_records`、`user_workspace_branch_preferences`
  - AI模型表：`ai_model_configs`
  - 进程管理表：`linux_servers`、`backend_java_processes`、`opencode_containers`、`opencode_container_managers`、`opencode_manager_backend_connections`、`opencode_server_processes`、`user_opencode_process_bindings`
  - 定时任务表：`scheduled_tasks`、`scheduled_task_plans`、`scheduled_task_runs`
- How: 使用 PostgreSQL/H2 兼容的 `comment on table/column` 语法；业务ID字段标注格式（如 `wks_xxx`、`ses_xxx`）；状态/来源类型等枚举字段标注可选值；JSON字段标注结构样例；已有注释的表（`common_parameters`、`workspace_create_operations`、`agent_config_worktrees`、`agent_config_operations`、`application_workspace_version_replicas`）不重复添加。
- Result: 35个表的全部字段均有中文注释，字段注释包含数据样例；`docs/deployment/database.md` 同步更新新增 V20260626210000 说明。
- Verification: `ls -la backend/test-agent-persistence/src/main/resources/db/migration/V20260626210000__add_chinese_comments_for_all_tables.sql` 确认文件已创建。

### 2026-06-26 - 设置中新增用户管理（测试）功能

- Why: 研发测试需要一个便捷入口查询平台所有用户、快速造测试账号（默认密码 123456）并指定角色，避免每次手动改库。
- What: 后端新增 `UserManagementApplicationService`（system-management）提供 `listUsers`/`createUser`（默认密码 + 单角色授权）/`listRoles`；新增 `UserManagementController`（`/api/internal/platform/system-management/users`、`/roles`），仅 `SUPER_ADMIN` 可访问。前端在设置弹窗新增 `SettingsUserManagementPanel.vue` 页签（菜单仅超管可见），含用户列表（`el-table` + 分页）、新增用户表单（统一认证号/用户名/角色下拉/组织部门选填）；`backend-api` 新增 `listUsers`/`createUser`/`listRoles` 方法，`shared-types` 新增对应类型。
- How: 复用现有 `UserDomainService.registerUser`（BCrypt 加密、唯一性校验）、`UserRepository.findPage`、`DictionaryRepository` 角色；无需新增数据库表或 Flyway migration。Controller/Service/DTO/测试按现有 `ConfigurationManagement*` 样板实现，前端面板按 `SettingsPersonalPanel` 表单风格 + `SettingsAppWorkspacePanel` 列表风格。后端测试新增 `spring-boot-starter-test` 依赖到 `test-agent-system-management`。
- Result: 后端测试 10/10 通过（`UserManagementApplicationServiceTest` 5 + `UserManagementControllerTest` 5），前端面板测试 3/3 通过。超管可在设置中看到"用户管理（测试）"入口，新建用户可使用默认密码 123456 登录。
- Verification: `mvn -pl test-agent-system-management,test-agent-api -am test -Dtest=UserManagementApplicationServiceTest,UserManagementControllerTest`；`corepack pnpm vitest run apps/agent-web/tests/settings-user-management-panel.test.ts`。

### 2026-06-26 - 持久层引入 MyBatis XML mapper 规范

- Why: 后续数据库操作需要统一走 MyBatis SQL，避免继续把关系型 SQL 分散写在 `JdbcClient` 代码里；同时不能一次性高风险迁移全部存量仓储。
- What: 引入 `mybatis-spring-boot-starter` 4.0.1，在 persistence 模块新增 MyBatis mapper 扫描、通用参数 `CommonParameterRepository` 试点实现和 XML SQL；`JdbcCommonParameterRepository` 去掉 Spring Bean 身份，仅作为旧集成测试直接构造的存量实现保留。
- How: 新增 `com.icbc.testagent.persistence.mybatis` 内部 mapper/row/repository，SQL 放在 `src/main/resources/mybatis/CommonParameterMapper.xml`；新增 `PersistenceSqlConventionTest` 固化白名单，禁止新增 JDBC SQL 和 MyBatis 注解 SQL；同步 AGENTS、后端规范、模块边界、数据库文档和 persistence README。
- Result: `CommonParameterRepository` 的生产 Bean 已切到 MyBatis；存量 `Jdbc*Repository` 进入迁移窗口，后续触及关系型 SQL 时迁移到 MyBatis XML。验证通过 `mvn -pl test-agent-persistence -am test`、`mvn clean package -DskipTests`，精确 `rg` 未发现 MyBatis 注解 SQL。

### 2026-06-26 - common_parameters 改为 DB 唯一来源、缺失即报错

- Why: `common_parameters` 表的业务路径参数此前有三套来源并存——DB seed、yaml `test-agent.managed-workspace.root`、代码内 `*_FALLBACK`/`DEFAULT_*` 常量，同一值复制多份且平台覆盖不一致（代码常量只有 linux 路径，DB 有 windows/linux/all）。目标是去重，让 DB 成为唯一事实源。
- What: 移除 `ManagedWorkspaceApplicationService` 的 `managedRoot` 字段、`resolveManagedRoot`、`@Value("${test-agent.managed-workspace.root:...}")` 注入及全部测试构造器形参；`configuredPath` 改为无 fallback、缺失抛 `INTERNAL_ERROR`。删除 `AgentConfigApplicationService` 的 3 个 `*_FALLBACK` 常量，`parameter()` 拆为 `requiredParameter`（缺失抛异常）与 `optionalParameter`（gitUrl 缺失视为 `UNCONFIGURED` 合法值）。删除 `UserOpencodeProcessAssignmentService` 的 `DEFAULT_SESSION_DIR`/`DEFAULT_CONFIG_PATH`，`configuredParameter` 改为缺失抛异常。5 个 `application*.yml` 删除 `managed-workspace` 块。新增 `V20260626180000` migration 删除无消费方的 `OPENCODE_WORKSPACE_ROOT`。
- How: `CommonParameterRepository` 接口给 `findAll`/`findByParameterId`/`updateValue` 加 default 空实现，恢复函数接口特性，使只读消费方的 lambda stub 仍可用，Jdbc 实现覆盖全部方法不受影响。测试侧 `ManagedWorkspaceApplicationServiceTest` 改用 in-memory `CommonParameterRepository` 注入两个根参数指向 `@TempDir`；`UserOpencodeProcessAssignmentServiceTest` 的 `service()`/`serviceLocalDirect()` 注入 session/config 参数并调整断言值。新增主类 package-private 测试构造器便于注入参数仓库。异常统一格式 `通用参数未配置：<参数英文名>` + `Map.of("parameter", englishName)`。
- Result: `common_parameters` 成为唯一来源，yaml 不再预留 fallback，代码无重复常量；DB 缺失对应参数时功能返回 500 强制运维补配。`OPENCODE_PUBLIC_AGENT_GIT_URL` 保持 `UNCONFIGURED` 合法语义不报错。
- Pitfalls: `ManagedWorkspaceApplicationService` 重构时第一次 Edit 的 old_string 未完整匹配主全参数构造器，留下一个形参不全却赋值全部字段的损坏构造器，导致编译报"找不到合适构造器"；用 Read 确认实际内容后定位并替换修复。`UserOpencodeProcessAssignmentServiceTest` 的 local-direct 短路用例也走 `synthesizeLocalDirectProcess` → `sessionPath`，故 `serviceLocalDirect` 也需注入参数 repo，不能继续用空 repo。`TestAgentRuntimePropertiesBindingTest` 的 3 个 guo cors 用例在 HEAD 上即失败（期望 `192.168.100.115:3000` 但 yaml 默认值不含），与本次改动无关。
- Verification: `mvn -pl test-agent-opencode-runtime -am test` 116/116 通过；`mvn -pl test-agent-workspace-management,test-agent-persistence -am test` 通过；`test-agent-app` 仅 3 个预先失败的 guo cors 用例，其余通过。grep 确认无 `managedRoot`/`resolveManagedRoot`/`*_FALLBACK`/`DEFAULT_SESSION_DIR`/`DEFAULT_CONFIG_PATH`/`managed-workspace.root` 残留，`OPENCODE_WORKSPACE_ROOT` 生产代码无引用。
### 2026-06-26 - 200 数据库失败后切回本地联调并补提交前日志回顾规约

- Why: 用户要求 guo 配置改连 `192.168.100.200` 的 Postgres/Redis，并在仍失败时放弃此前无效提交、合并远程最新代码后切本地库启动；同时新增规约，提交前必须先回顾 session log，避免覆盖其他开发者/智能体已提交内容。
- What: 本地 `main` 已对齐 `origin/main`，此前 5 个本地无效提交已按用户要求放弃；`.env.local` 仅作为本机运行态切到 `local` profile + `127.0.0.1:15432/16379`（未纳入 Git）；文档新增提交前回顾 `.agents/session-log.md` 的强制规则，并清理本文件残留的合并标记。
- How: 新 TCP 连接到 `192.168.100.200:5432/16379` 均返回 `No route to host`，同机 `psql`/`nc` 与 Java 一致失败；本地库启动前因 `V20260625184300__create_scheduler_framework_tables.sql` 校验和不一致，已在本机 `testagent` 库修正 `flyway_schema_history` checksum 后重启。
- Result: 后端 `http://192.168.100.115:8080`、前端 `http://192.168.100.115:3000`、opencode `http://192.168.100.115:4096` 已启动；对话 run 可创建并连接 opencode，但模型返回 `usage allocated quota exceeded`，已取消卡住的 `run_dad8c21c19e94fb5a5df8e915a15f561`，未能完成助手回复验收。

### 2026-06-26 - 公共 Agent 配置 Git 管理与发布

- Why: 工作台需要新增与项目工作空间平级的 Agent 入口，公共级 agent 配置由 Git 管理且只允许 `SUPER_ADMIN` 修改，工作空间级 agent 配置跟随当前工作区，同时 Git 长操作进度不能混入 RunEvent SSE。
- What: 新增公共/工作空间 Agent 配置领域对象、JDBC repository、Flyway 参数/表结构、workspace-management 编排服务、平台 HTTP API、ticket WebSocket 进度、公共配置同步广播、frontend `Agent` tab、backend-api client 和 shared types；公共 Git 地址默认 `UNCONFIGURED`，公共写操作在未配置时拒绝。
- How: 公共标准目录为 `{OPENCODE_PUBLIC_CONFIG_GIT_ROOT}/opencode/agents/`，读兼容 `opencode/agent/`；工作空间标准目录为 `{workspace.rootPath}/.opencode/agents/`，读兼容 `.opencode/agent/`；worktree 名校验 `^[A-Za-z0-9._-]{1,64}$` 后自动拼 `-yyyyMMdd`，公共 worktree 落到 `.configdev/`，工作空间 worktree 落到个人 worktree 根下的 `agentconfig/{workspaceId}/`。
- Result: 浏览器通过 `/api/internal/platform/workspace-management/agent-config/operations/{operationId}/tickets` 获取一次性 ticket，再连 `/ws?ticket=...` 接收 `snapshot/step/completed/failed`；公共发布后广播 `agent-config.public-sync-requested`，payload 只含 `branch`、`commitHash`、`reason`。本次也把 scheduler migration 从旧 `V18__...` 纠正为文档已有的 `V20260625184300__...`，并移除其中非幂等的补充 FK 语句以兼容已执行过旧 V18 的库；本地重命名后需清理残留 `target/classes/db/migration/V18__...`，否则 Flyway 会重复执行旧生成物。
- Verification: `mvn -pl test-agent-workspace-management,test-agent-api,test-agent-persistence,test-agent-event -am test`；`corepack pnpm --filter @test-agent/backend-api typecheck`；`corepack pnpm vitest run apps/agent-web/tests packages/backend-api/tests`；`corepack pnpm -r typecheck` 因既有 `packages/agent-chat/src/runtime-reducer.ts` 与 `apps/agent-web/src/components/FigmaChatPanel.vue` 类型问题未通过。

### 2026-06-26 - 系统管理新增通用参数管理（仅修改 value）

- Why: 系统级通用参数（如 `OPENCODE_APP_WORKSPACE_ROOT`、`OPENCODE_PUBLIC_AGENT_GIT_URL` 等）此前只能在数据库直接修改，运维需要一个 SUPER_ADMIN 可访问的界面查看并修改参数值。
- What: 后端新增 `CommonParameterManagementApplicationService`（configuration-management）提供 `find(filter, pageRequest)` 列表查询（可按平台过滤、内存分页）与 `updateValue(parameterId, newValue, traceId)` 仅修改 value；新增 `CommonParameterManagementController`（`/api/internal/platform/configuration-management/common-parameters`，`GET /` 列表 + `PATCH /{parameterId}` 更新），仅 `SUPER_ADMIN` 可访问。前端在系统管理面板新增 `GeneralParamManagementPanel.vue`（`SystemManagementPanel.vue` 菜单项 `params`），使用 `useQuery` + `useMutation` + 行内 `el-input` drafts 模式；`backend-api` 新增 `listGeneralParameters`/`updateGeneralParameter` 方法，`shared-types` 新增 `GeneralParameter`/`GeneralParameterListParams`/`GeneralParameterUpdatePayload` 类型。
- How: 领域端口 `CommonParameterRepository` 新增 `findAll`/`findByParameterId`/`updateValue` 方法（保留既有 `findByEnglishNameAndPlatform`），JDBC 实现相应 SQL；领域对象 `CommonParameter` 新增 `withValue(newValue, updatedAt)` 工厂复用 compact 构造器校验。Controller/Service/DTO/测试按现有 `SchedulerManagementController` 模式实现（`Mono<ApiResponse<Object>>` + `blocking` + `requireSuperAdmin`）。前端面板参照 `ScheduledTaskManagementPanel.vue` 的列表+行内编辑+分页模式。API 文档同步更新 `docs/api/http-api.md` 新增「通用参数管理 API」章节，模块 README 更新服务说明。
- Result: 后端测试 10/10 通过（`CommonParameterManagementApplicationServiceTest` 6 + `CommonParameterManagementControllerTest` 4）；前端新增面板无类型错误。接口仅提供列表与 value 更新，不暴露新增/删除，保证参数集合稳定。
- Verification: `mvn -pl test-agent-configuration-management,test-agent-api -am test -Dtest=CommonParameterManagementApplicationServiceTest,CommonParameterManagementControllerTest`；`corepack pnpm --filter @test-agent/agent-web typecheck`（15 个既有错误来自 `FigmaChatPanel.vue`/`agent-chat`，与本次改动无关）。
- Pitfalls: 工作区混入了之前未提交的 `common_parameters` 消费者重构（`ManagedWorkspaceApplicationService`/`AgentConfigApplicationService`/`UserOpencodeProcessAssignmentService`）和孤立 `用户管理（测试）` 类型；按用户要求仅提交本功能文件，其余保留在工作区。

### 2026-06-26 - 通用参数驱动 opencode 路径并自动创建初始版本工作区

- Why: 设置页创建应用工作空间需要同时落地应用版本工作区，路径需要从平台参数统一管理，并避免不同代码库在新目录规则下冲突。
- What: 新增 `common_parameters` 和 `workspace_create_operations`，初始化 Linux/Windows opencode workspace/config/session/appworkspace/personalworktree 路径；代码库新增可空唯一 `english_name`，新增/编辑时校验 1 到 29 位英文字母并小写保存；设置页创建工作空间时生成/接收 `operationId`，后端按当前用户 READY opencode 进程定位 Linux 服务器，自动创建模板 + 初始版本工作区并写入进度。
- How: 路径读取优先级为当前平台参数 -> `all` 参数 -> 代码 fallback；应用版本目录使用 `{OPENCODE_APP_WORKSPACE_ROOT}/{version}/{repository.englishName}/{directoryPath}`，个人 worktree 使用 `{OPENCODE_PERSONAL_WORKTREE_ROOT}/{version}/{unifiedAuthId}/{repository.englishName}/{personalWorkspaceId}`；标准库从 `feature_testagent_yyyyMMdd` 解析版本，非标准库由前端传 `yyyyMMdd`。
- Result: 创建工作空间期间前端轮询 `/api/internal/platform/configuration-management/workspace-create-operations/{operationId}` 展示“校验、保存配置、解析版本、下载代码、创建运行态工作区、完成/失败”；该进度不走 RunEvent SSE。历史代码库 `english_name` 可为空，但不能用于创建新的应用版本工作区，必须先补英文名。
- Verification: `mvn -pl test-agent-configuration-management,test-agent-workspace-management,test-agent-opencode-runtime,test-agent-api,test-agent-persistence -am test`；`corepack pnpm -r typecheck`；`corepack pnpm vitest run apps/agent-web/tests/settings-app-workspace-panel.test.ts packages/backend-api/tests/backend-api.test.ts`；`git diff --check`。

### 2026-06-26 - 工作空间文件操作切到目标后端 WebSocket

- Why: 前端工作空间文件列表、读取、写入、状态和删除需要与用户 opencode 进程同服务器执行，避免浏览器或当前后端误操作不在同机的工作空间路径；超级管理员还需要按后端服务器选择工作空间。
- What: `workspaces` 增加可空 `linux_server_id`；workspace-management 增加当前服务器身份、同服务器校验、legacy 回填、普通文件删除和服务器目录浏览；opencode-runtime 增加工作区文件 WebSocket 路由和后端服务器列表；api 增加 route/ticket/WebSocket RPC 入口；backend-api 和 agent-web 改为 route + target ticket + WebSocket RPC，`SUPER_ADMIN` footer 增加服务器工作空间选择按钮和对话框。
- How: 文件 WebSocket ticket 绑定 workspace、目标服务器、agent 服务器、mode、traceId 和 `SUPER_ADMIN` 状态，短期一次性消费；前端按 workspaceId 复用连接并在切换时重连；服务器选择器通过目标后端 `directory-picker` ticket 浏览目录，服务器与当前 agent 不一致时前端禁用输入，后端仍强制拒绝创建。
- Result: 工作区文件树、打开文件、保存、状态、删除和实时预览读取不再调用旧 HTTP workspace file 接口；旧 HTTP 文件接口继续兼容保留。历史空 `linux_server_id` 工作区只会在同服务器和 root path 校验成功后回填。
- Verification: `mvn -pl test-agent-workspace-management,test-agent-opencode-runtime,test-agent-persistence,test-agent-api -am -Dtest=WorkspaceApplicationServiceTest,WorkspaceFileServiceTest,WorkspaceFileRoutingServiceTest,JdbcRepositoryIntegrationTest,RuntimeControllerTest,TerminalWebSocketHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test`；`mvn -pl test-agent-app -am -DskipTests compile`；`corepack pnpm typecheck`；`corepack pnpm vitest run packages/backend-api/tests/backend-api.test.ts apps/agent-web/tests/WorkbenchFooter.test.ts`；`corepack pnpm e2e apps/agent-web/tests/workbench.spec.ts --project=chromium --grep 'workbench opens|switching to an application|does not read'`；`corepack pnpm e2e apps/agent-web/tests/workbench.spec.ts --project=chromium --grep 'model picker|opencode process'`；`git diff --check`。
- Pitfalls: 当前全量 `workbench.spec.ts` 仍包含既有旧交互用例（本机目录按钮、未接后台附件上传、实时按钮）与当前页面不一致，不能把该整文件作为本次通过项；本次只验证与文件 WebSocket 路由直接相关的页面子集。

### 2026-06-25 - 定时任务系统管理与协作式停止

- Why: 超级管理员需要在前端查看定时任务当前状态和历史记录，调整 Cron，手工启动未执行任务，并能对正在执行的任务发起停止；现有运行管理入口也需要改为系统管理并承载两个二级管理项。
- What: 后端 scheduler 增加 `STOPPING` / `MANUALLY_STOPPED` 状态、运行记录停止审计字段、状态字典 seed、`ScheduledTaskContext.stopRequested()` / `throwIfStopRequested()`、管理员停止 API 和 label 响应；手动触发改为同 taskKey 存在 active run 时返回冲突。前端新增 `SystemManagementPanel` 和 `ScheduledTaskManagementPanel`，activity rail 的“运行管理”改名为“系统管理”，二级导航包含“定时任务管理”和复用的“运行管理”；`backend-api` 和 `shared-types` 补齐 scheduler 管理类型和 client 方法。
- How: 先用 domain / scheduler / api / 前端组件测试锁定新行为，再按模块边界在 `test-agent-scheduler`、`test-agent-api`、`test-agent-persistence` 和 `agent-web` 做最小改动；停止采用协作式状态流转，不强制中断线程，handler 需主动检查 context。
- Result: 超级管理员可通过系统管理查看任务定义、当前/最近执行状态和历史运行记录，支持刷新、启停、Cron 编辑、手工启动非 active 任务和停止 `RUNNING` 记录；后端统一记录停止操作者、原因和最终 `MANUALLY_STOPPED` 终态；文档同步 API、数据库、安全、部署、前后端模块边界。
- Verification: `cd backend && mvn -pl test-agent-scheduler -am test`；`cd backend && mvn -pl test-agent-persistence -am test`；`cd backend && mvn -pl test-agent-api -am test`；`cd backend && mvn test`；`cd frontend && corepack pnpm typecheck`；`cd frontend && corepack pnpm test -- scheduler-management-panel.test.ts backend-api.test.ts runtime-management-settings.test.ts`；`git diff --check`。
- Next: 未来具体业务定时任务必须在长循环或外部调用间隙检查 `ScheduledTaskContext` 的停止请求；普通用户级 Cron 计划 API 和后台定时会话仍未开放。

### 2026-06-25 - 修复 115 登录 CORS 与本地双入口访问

- Why: 用户用 `http://192.168.100.115:3000` 登录时报浏览器 CORS，`/api/auth/login` 预检返回 403 且无 `Access-Control-Allow-Origin`；同时希望本地仍能进页面，并复核 `384360ea0ba04029ad8f5999a9912e70b0aade91` 后对话发送问题。
- What: `application-guo.yml` 的 `cors-allowed-origins` 改为支持 `TEST_AGENT_CORS_ALLOWED_ORIGINS` 覆盖；`restart-dev-services.sh` 在非 loopback 前端 URL 下用 `0.0.0.0` 监听并自动追加局域网前端 origin 与 `127.0.0.1` origin；`FigmaChatPanel` 输入栏按钮列宽同步为 32px，并补充 ready 状态发送会 emit `send` 且清空输入的组件测试；同步 CORS 文档和本地启动 skill 的验证步骤。
- How: 先用真实 `OPTIONS /api/auth/login` 复现 115 origin 被拒，再通过配置绑定测试锁定 `guo` profile 环境变量覆盖能力；脚本回归用 stub 工具验证局域网 URL 下前端监听地址为 `0.0.0.0:3000`。
- Result: 重启后 `http://192.168.100.115:8080` 与 `http://127.0.0.1:8080` health 均为 UP，`http://192.168.100.115:3000` 与 `http://127.0.0.1:3000` 均返回 200，登录预检返回 `Access-Control-Allow-Origin: http://192.168.100.115:3000`。
- Verification: `tools/verify-dev-scripts.sh`；`mvn -pl test-agent-app -Dtest=TestAgentRuntimePropertiesBindingTest test`；`pnpm --dir frontend --filter @test-agent/agent-web exec vitest run tests/FigmaChatPanel.test.ts --environment jsdom`；`pnpm --dir frontend --filter @test-agent/agent-web typecheck`；`git diff --check`；`./restart-dev-services.sh --profile guo --env-file .env.local --skip-frontend-build`；115/127 health、frontend HEAD 和 login CORS preflight curl。
- Next: 后续 115 启动继续显式传 `TEST_AGENT_BASE_URL=http://192.168.100.115:8080` 与 `TEST_AGENT_FRONTEND_URL=http://192.168.100.115:3000`；如果仍有真实发送失败，优先看登录后的 Run 请求/事件流状态，而不是 CORS。

### 2026-06-25 - 按 192.168.100.115 启动本地服务并修复 V17 幂等迁移

- Why: 用户要求本地服务按 `192.168.100.115` 地址启动，并确认最新启动命令应切到 `--profile guo --env-file .env.local --skip-frontend-build`。实际启动时后端被 V17 migration 的 `(linux_server_id, port)=(127.0.0.1,4096)` 唯一键冲突阻塞，前端即使启动也只监听 `127.0.0.1`，局域网地址不可访问。
- What: `V17__seed_local_opencode_machine_for_default_user.sql` 在同端口已有历史进程时复用该进程写默认用户绑定；新增迁移集成测试覆盖 V16 历史库已占用 4096 的场景；`restart-dev-services.sh` 从最终 `TEST_AGENT_FRONTEND_URL` 推导前端 host/port，向 Vite 注入 `VITE_TEST_AGENT_API_BASE_URL`，并在未显式配置 CORS 时追加当前前端 origin；`agent-web` Vite dev server 支持 `HOST` 环境变量；同步前端、数据库和 persistence README；个人 `intelligent-test-agent-local-startup` skill 已更新为 115 + guo profile 命令。
- How: 先用 H2/Flyway 迁移测试复现 V17 唯一键失败，再最小修改 SQL 的 `not exists` 条件和绑定来源；启动脚本保持 `.env.local` 为唯一 env 文件来源，通过命令前缀传入 115 URL，不修改 `.env.local`。
- Result: 当前服务已通过 `TEST_AGENT_BASE_URL=http://192.168.100.115:8080 TEST_AGENT_FRONTEND_URL=http://192.168.100.115:3000 ./restart-dev-services.sh --profile guo --env-file .env.local --skip-frontend-build` 启动，后端健康检查 UP，前端 115 地址返回 200；`127.0.0.1:3000` 不再作为本次前端监听地址。
- Verification: `mvn -pl test-agent-persistence -am -Dtest='JdbcRepositoryIntegrationTest#v17SeedLocalOpencodeMachineForDefaultUserIsIdempotent+v17SeedReusesExistingLocalOpencodePortProcess' -Dsurefire.failIfNoSpecifiedTests=false test`；`tools/verify-dev-scripts.sh`；`corepack pnpm --filter @test-agent/agent-web typecheck`；启动脚本内 `mvn clean package -DskipTests`；`curl -fsS http://192.168.100.115:8080/actuator/health`；`curl -fsS -I http://192.168.100.115:3000`。
- Next: 后续按 115 局域网访问时继续显式传 `TEST_AGENT_BASE_URL` 和 `TEST_AGENT_FRONTEND_URL`；若需要 opencode-manager 真实链路，不要把 `TEST_AGENT_BASE_URL` 设成非本地 URL，或同步调整 manager discovery/CORS 策略。

### 2026-06-25 - application-guo.yml 同步本地短路配置

- Why: 上一轮已经把 `local-direct` 短路 + `gateway-mode=local` 接到 `application-local.yml`，但用户日常本地启动用 `application-guo.yml`（profile `guo`，直连 192.168.100.194 的 Postgres + 本机 6379 Redis），里面没设这些开关，所以本地启动后短路不会生效，状态接口仍会跑 topology / health 链路。用户明确要求把 `application-guo.yml` 改掉。
- What:
  - `application-guo.yml` 的 `test-agent.opencode` 段补齐 `manager-control`（`gateway-mode=local` + token / listen-url / linux-server-id / heartbeat-interval / backend-stale-after / command-timeout / backend-discovery-limit），与 `application-local.yml` 一致；并新增 `local-direct: ${TEST_AGENT_OPENCODE_LOCAL_DIRECT:true}` 与 `local-direct-base-url: ${TEST_AGENT_OPENCODE_BASE_URL:http://127.0.0.1:4096}`，env 可覆盖。`nodes` 段维持原样。
  - 文档：`docs/deployment/backend.md` 把「本地开发 opencode 短路模式」节加上 `guo` profile；`docs/deployment/database.md` 网关选择节同步；`backend/test-agent-opencode-runtime/README.md` 短路开关说明同步提到 `local` / `guo` 两个 profile。
  - 测试：`TestAgentRuntimePropertiesBindingTest` 11 用例全绿（配置 binding 不受 yaml 改动影响）。
  - `.agents/session-log.md` 记本次。
- How: 与 `application-local.yml` 对齐字段顺序 / 注释 / env 占位符，避免两份配置漂移；不动用户已经写过的 `datasource` / `redis` / `security` 段；生产 `application-prod.yml` 不引入这些开关，保持默认 `socket` + `local-direct=false`。
- Result: 用户用 `--spring.profiles.active=guo` 启动时，`local-direct` / `gateway-mode` 都默认开启，前台用户进程状态接口会直接落到 READY + `http://127.0.0.1:4096`，不会再被 V17 容器 / manager 健康检测阻塞；需要切到 manager 真实模式只需 `TEST_AGENT_OPENCODE_LOCAL_DIRECT=false` + `TEST_AGENT_OPENCODE_GATEWAY_MODE=socket` env 覆盖。
- Pitfalls: `application-guo.yml` 的 2 空格缩进要保持一致；`linux-server-id` 不设会导致 `BackendJavaProcessLifecycleRunner` 注册时拿到空值，与 V17 种子的 `127.0.0.1` 失配；`token` 留空字符串 OK（本地不走 manager WebSocket 鉴权）。
- Verification: `mvn -pl test-agent-app test -Dtest=TestAgentRuntimePropertiesBindingTest` 11 用例全绿；配置 diff 仅触及 `test-agent.opencode` 段。
- Next: 用户重启后状态接口应当落到 READY；如果仍报 baseUrl 不通，确认 `TEST_AGENT_OPENCODE_BASE_URL` 写到了正确值，本机 4096 在跑 opencode server。

### 2026-06-25 - 本地开发短路直连 127.0.0.1:4096

- Why: 上一轮加了 local gateway 让 health 走直连 baseUrl，但用户重启后仍报"opencode 进程健康检测失败，且原 Linux 服务器没有可用容器"；原因可能是：(a) 用户没在 local profile 启动 / 没启 opencode server；(b) V17 容器 `current_processes=max_processes=1` 让 `canRebuildOn` 始终 false，health 失败就再走重建，结果两条路都卡死。用户明确要求：本地开发时不要再校验，直接默认连本地 4096。
- What:
  - `TestAgentRuntimeProperties.Opencode` 新增 `localDirect`（默认 false）与 `localDirectBaseUrl`（默认 `http://127.0.0.1:4096`），空 baseUrl 规整回默认。
  - 新增 `com.icbc.testagent.opencode.runtime.process.LocalDirectSettings` 记录。
  - `UserOpencodeProcessAssignmentService` 增加 `LocalDirectSettings` 依赖，并在 `status` / `initialize` / `requireReadyProcess` 三个入口顶部短路：完全跳过 database topology / user binding / manager health 校验链路，合成一个满足 `OpencodeServerProcess` 校验的进程对象（`processId=ocp_local_direct, containerId=ctr_local_direct, port=4096, baseUrl=http://127.0.0.1:4096`），直接返回 READY。baseUrl 解析失败时回退到默认。
  - `OpencodeManagerControlConfig` 新增 `localDirectSettings` Bean，把 `test-agent.opencode.local-direct` / `local-direct-base-url` 转成 runtime 的 `LocalDirectSettings`。
  - `application-local.yml` 默认 `local-direct: true`（受 `TEST_AGENT_OPENCODE_LOCAL_DIRECT` 覆盖），并把 `local-direct-base-url` 绑到 `TEST_AGENT_OPENCODE_BASE_URL` 默认 4096。
  - 测试：`UserOpencodeProcessAssignmentServiceTest` 新增 4 个用例覆盖 `status` / `initialize` / `requireReadyProcess` 短路 + baseUrl 解析失败回退；`NoopRepository` 子类在 save 路径抛 AssertionError，确保短路路径不写库；`FakeRepository` 增加 `findUserBindingCalls` / `findContainerCalls` 计数；`TestAgentRuntimePropertiesBindingTest` 新增默认值与绑定 + 空 baseUrl 回退两条用例。
  - 文档：`docs/deployment/backend.md` 新增"本地开发 opencode 短路模式"节说明 `status` / `initialize` / `requireReadyProcess` 行为与 baseUrl 解析回退；`backend/test-agent-opencode-runtime/README.md` 同步 `UserOpencodeProcessAssignmentService` 短路说明与测试覆盖；`backend/test-agent-app/README.md` 在 `OpencodeManagerControlConfig` 条目和 `TestAgentRuntimePropertiesBindingTest` 测试覆盖里同步。
- How: `LocalDirectSettings` 在 runtime 模块定义，`OpencodeManagerControlConfig` 用 `@Bean` 把它注入 runtime 的 service；`OpencodeServerProcess` 构造要求 `baseUrl = http://{host}:{port}`，所以用 `java.net.URI` 解析 baseUrl 后重建符合 V15 CHECK 约束的字段；`NoopRepository` 在 `save*` 路径抛 `AssertionError`，一旦短路被绕过会立即失败。
- Result: 本地重启后，无论数据库 topology / V17 容器 / 真实 opencode server 是否就绪，前台用户进程状态接口在 `local-direct=true` 时直接返回 `READY` + `baseUrl=http://127.0.0.1:4096`，不会再出现"opencode 进程健康检测失败"或"原 Linux 服务器没有可用容器"的报错；生产 profile 走 `local-direct=false`（也是 Java 字段默认值），保持原有 topology / binding / health 校验链路。
- Pitfalls: `OpencodeServerProcess` 构造硬要求 `baseUrl = http://{linuxServerId}:{port}`，不能传 `https://`；`UserId` 在 `requireReadyProcess` 路径下也需要非空，所以合成进程用传进来的 `userId`，`status` / `initialize` 兜底用 `usr_local_direct`。Spring 多构造器时显式 `@Autowired` 才能让 6 参版本被选中，旧 4/5 参构造保留以兼容单测。
- Verification: `mvn -pl test-agent-opencode-runtime,test-agent-app -am test -Dsurefire.failIfNoSpecifiedTests=false`（含 4 条新单测 + 2 条 binding 新用例）。
- Next: 用户重启后前台 status 应当落到 READY；如果 Run 链路仍报 baseUrl 不通，检查 `TEST_AGENT_OPENCODE_BASE_URL` 是否在 `.env.local` 写到了正确值；生产部署务必确认 `local-direct=false`（也是 Java 字段默认值）。

### 2026-06-25 - 修正发送按钮尺寸和附件弹窗位置

- Why: 用户反馈右侧发送按钮被拉成长条，视觉不合理；上传附件弹窗位置太靠下，希望放到页面上面一点。
- What: `FigmaChatPanel.vue` 中把输入行右侧按钮列从 36px 调整为 44px，发送/停止按钮固定为 44x44 圆形并垂直居中；附件弹窗遮罩从底部对齐改为顶部对齐，顶部留 84px 间距，入场动画方向同步改为向下落位。
- How: 只改现有 scoped CSS，不动发送/停止事件、附件弹窗状态、API 或后端逻辑。
- Result: 发送按钮恢复为正常圆形图标按钮；附件弹窗显示在右侧面板靠上位置。
- Pitfalls: 无。
- Verification: `corepack pnpm --filter @test-agent/agent-web typecheck` 通过；`corepack pnpm test -- FigmaChatPanel.test.ts` 通过；`curl -fsS http://127.0.0.1:8080/actuator/health` 返回 UP；`curl -fsS -I http://127.0.0.1:3000/` 返回 200。
- Next: 等用户在当前 127 本地服务页面验收视觉。

### 2026-06-25 - 修复空助手行和结束态任务消耗动图

- Why: 上一轮把对话区改成完整消息列表后，真实 RunEvent 派生的空 assistant 消息也被渲染，导致页面出现多条只有“测试智能体 · 时间”的空行；任务结束后任务消耗行仍使用 loading gif，看起来像还在执行。
- What: `FigmaChatPanel.vue` 过滤无可见文本的 user/assistant 展示消息；任务消耗行仅在 `running=true` 时使用 loading gif，结束态改用静态紫点；组件测试补充空 assistant 行过滤和结束态静态标记回归用例。
- How: 先用 Vitest 复现两个失败，再做最小组件修复；浏览器刷新后当前会话无可见消息，只能通过 DOM 检查确认当前页没有空助手行/usage 动图，核心回归由组件测试覆盖。
- Result: `corepack pnpm test -- apps/agent-web/tests/FigmaChatPanel.test.ts`、`corepack pnpm --filter @test-agent/agent-web typecheck`、`corepack pnpm --filter @test-agent/agent-web build` 和 `git diff --check` 通过。
- Pitfalls: `message.part.updated` / tool part 派生出的 assistant 消息可能没有可见文本，完整历史渲染必须过滤空文本，否则会把 meta 单独显示成空消息。
- Verification: 见 Result。
- Next: 无。

### 2026-06-25 - 修复对话误发送和历史消息只显示最后一轮

- Why: 用户反馈右侧对话输入框在未按发送意图时会误发，尤其是中英文/输入法相关场景；同一历史会话切换后看不到完整历史消息。同时本机换手机热点，需要临时用 127.0.0.1 启动本地服务。
- What: `FigmaChatPanel.vue` 在输入法 composition 阶段忽略 Enter（同时兼容 `event.isComposing` 和 `keyCode=229`），并把消息区从只渲染最后一条用户/助手消息改为按顺序渲染完整用户/助手消息列表；新增组件回归测试覆盖 IME Enter 不发送和历史四条消息完整展示；同步更新前端 README / 包说明。
- How: 先用 Vitest 复现两个失败，再做最小组件修复；启动验证时发现 `restart-dev-services.sh` 的 `load_env_file` 会用 env 文件覆盖命令前缀变量，因此用 gitignored 的 `.tmp/dev-127.env` 从 `.env.local` 派生并替换旧热点 IP，追加 127.0.0.1 运行拓扑和 opencode base 覆盖项。
- Result: 回归测试、`agent-web` typecheck/build、全仓 `git diff --check` 均通过；服务已用 `.tmp/dev-127.env` 重启，`http://127.0.0.1:8080/actuator/health` 为 UP，`http://127.0.0.1:3000` 返回 200。
- Pitfalls: 直接在启动命令前缀设置 `TEST_AGENT_OPENCODE_BASE_URL` 不生效，因为 `.env.local` 后加载会覆盖它；临时切换热点地址应使用派生 env 文件或修改 env 文件（本次未修改 `.env.local`）。
- Verification: `corepack pnpm test -- apps/agent-web/tests/FigmaChatPanel.test.ts`；`corepack pnpm --filter @test-agent/agent-web typecheck`；`corepack pnpm --filter @test-agent/agent-web build`；`git diff --check`；`./restart-dev-services.sh --env-file .tmp/dev-127.env --skip-backend-build --skip-frontend-build`；`curl -fsS http://127.0.0.1:8080/actuator/health`；`curl -fsS -I http://127.0.0.1:3000`。
- Next: 如需长期使用 127.0.0.1，明确后再更新 `.env.local`；当前 `.tmp/dev-127.env` 只是本次本地启动临时文件。

### 2026-06-25 - 修复运行管理拖动/滚动条问题及文件树和工作台图标大小/线条

- Why:
  - 用户反馈超级管理员设置-运行管理页内容（拓扑状态及 opencode 进程列表）存在可以被拖动的行为；同时，原多卡片各自独立的滚动条容易产生高度上的错落不齐，希望能将其对齐统一放最下面（保持每个小卡片自己独立带滚动条的形式，但整体布局保持对齐，不要错落）。
  - 工作台顶栏需保留左侧的文件树展开/收起切换按钮，右侧面板由顶栏右侧的折叠按钮（均使用 `panel-close.svg` 图标）控制。右侧折叠按钮位置调整到面板 header / tabbar 对应高度，浮动在最外层（即使折叠依然可见并能点开），左侧折叠按钮也同样调整至浮动在左面板 tabbar 相同高度上，使两个侧边栏开关功能一致。
- What:
  - **RuntimeManagementPanel.vue**: 给最外层 section 增加 `@dragstart.prevent` 并且对容器及其子元素添加 `user-drag: none` 禁用拖拽；对卡片容器 `.ta-runtime-block` 增加 `display: flex; flex-direction: column` 布局，让表格滚动包裹容器 `.ta-runtime-block-scroll` 设为 `flex: 1` 填充全部可用空间，从而将每一排卡片的高度拉伸一致，使各表底部的横向滚动条完全水平对齐（不再错落）。
  - **FigmaShell.vue**:
    - 移除原本在最顶部 header 中的侧边栏开关按钮。
    - 在 `.figma-body` 顶层增加两个绝对定位的浮动按钮（`.figma-sidebar-toggle-floating`），通过 Vue 状态计算属性 `left` 随着面板的展开和收缩移动。这使得开关始终保持在左右面板顶部的 header/tabbar 高度（`top: 7px`）并永远在最外层可见。
  - **AgentWorkbench.vue**: 移除左侧 Activity Bar 上的对话框按钮（`MessageSquare` 图标按钮），将编辑图标 `Code2` 的 `stroke-width` 设置为 `1.5`。
  - **FileExplorer.vue**: 将 Tab 栏图标 `FolderTree`、`Search`、`GitBranch` 的 `stroke-width` 设置为 `1.5`，尺寸从 `h-[18px] w-[18px]` 调整为 `h-4 w-4`。其他 Lucide 图标（`Search`、`FileText`、`RefreshCw`）的 `stroke-width` 也同步设置为 `1.5`。
  - **FigmaChatPanel.vue**: 去除对话框头部的冗余关闭按钮（由外部 FigmaShell 的浮动展开/收起按钮替代）。
- How:
  - 通过 Vue 模板和 CSS 属性实现禁用拖拽和卡片 flex 高度对齐。
  - 调整 figma-header 和 figma-sidebar 相关的 Vue 模板与 CSS 镜像 transform 设置，增加绝对定位浮动开关。
- Result:
  - 运行管理页面的元素完全不可拖拽，且拓扑图形只有一个位于最下方的滚动条进行整体横向滚动，页面变得非常干净。
  - 侧边栏折叠按钮恢复并在两侧完美以相反的方向指向，Activity Rail 的对话框切换按钮已去除，一切点击、折叠逻辑符合现代 IDE 的标准行为。
- Pitfalls: 无。
- Verification: `corepack pnpm --filter @test-agent/agent-web typecheck && corepack pnpm --filter @test-agent/agent-web build` 编译打包全数通过。
- Next: 等待用户在前端热重载（无需手动重启）后验收新界面效果。

### 2026-06-25 - 补充关键节点和流程日志

- Why: 项目中很多关键节点和流程缺少日志，排查问题困难，需要在关键操作处补充结构化日志。
- What:
  - **WorkspaceApplicationService**: 新增创建工作区、查询失败等关键操作日志
  - **SessionApplicationService**: 新增创建会话、归档会话等关键操作日志
  - **DefaultOpencodeClientFacade**: 新增外部调用开始/完成、重试、错误转换日志
  - **RunEventSseStreamService**: 新增 SSE 连接开始/取消/错误/完成日志
  - **RunEventLiveBus**: 新增事件发布、无订阅者、发布失败等日志
  - **RunApplicationService**: 新增 Run 启动/路由/成功/失败、取消等关键操作日志
  - **pom.xml**: 为 test-agent-workspace-management 模块添加 slf4j-api 依赖
- How: 在各关键方法入口添加 info 级别日志，在错误处理分支添加 warn/error 日志，遵循结构化日志规范（包含 traceId、操作类型、关键业务 ID）。
- Result: 关键流程现在有完整的日志追踪，便于排障和问题定位。
- Pitfalls: test-agent-workspace-management 模块原本没有 slf4j-api 依赖，需要手动添加。
- Verification: `mvn compile -DskipTests` 编译成功；`mvn -pl test-agent-workspace-management -am test` 通过；`mvn -pl test-agent-opencode-client -am test` 通过；`mvn -pl test-agent-opencode-runtime -am test` 通过；`mvn -pl test-agent-event -am test` 通过。
- Next: 无。

### 2026-06-25 - 运行管理只展示活进程并增加 Redis 心跳

- Why: 超级管理员设置-运行管理页需要面向当前启动的 Java / opencode 进程做运维，原实现只依赖数据库快照且用用户 ID 过滤/展示，容易展示僵死进程，也不便按用户名定位。
- What:
  - 运行管理查询新增 `username` 过滤和响应字段，前端筛选框改为用户名，保留 `userId` 兼容参数。
  - 后端新增 `OpencodeProcessHeartbeatStore` 端口及 Redis/Noop 实现：Java / opencode 活进程写 5 分钟 TTL 心跳 key，索引集合用于跨机器汇总活进程。
  - 应用启动后每 3 分钟健康检查 RUNNING opencode 进程并刷新 Redis 心跳，每 5 分钟清理过期心跳索引；查询面板只返回 READY/RUNNING 且心跳未过期的 Java、容器、管理连接、opencode 进程。
  - 同步更新运行管理 API、后端模块 README、前端 README 和类型/测试。
- How: 在业务层通过端口依赖 Redis 心跳，Redis 未启用时回退数据库 `lastHeartbeatAt` / `lastHealthCheckAt` 的 5 分钟窗口；前端只保留 RUNNING opencode 状态视角，避免运营面板展示历史失败/停止进程。
- Result: 管理页可以跨 Linux IP 查看当前活跃 Java/opencode 进程，用户列优先显示用户名；僵死进程在心跳过期或健康检查失败后不再出现在面板中。
- Pitfalls: `PageRequest` 最大 size 为 200，定时扫描不能使用更大的批量值，否则任务运行时会被分页校验拒绝；Spring Service 一旦保留多个构造器，生产构造器必须显式标 `@Autowired`，否则打包启动时会尝试无参构造并失败。
- Verification: `mvn -pl test-agent-opencode-runtime -am -Dtest=RuntimeManagementQueryServiceTest,OpencodeProcessHeartbeatMaintenanceServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`；`mvn -pl test-agent-app -am test -Dsurefire.failIfNoSpecifiedTests=false`；`corepack pnpm test -- backend-api runtime-management-settings`；`corepack pnpm --filter @test-agent/agent-web typecheck`；`corepack pnpm --filter @test-agent/backend-api typecheck`。
- Next: 部署多机环境时确认 `test-agent.redis.enabled=true` 且所有后端实例连接同一 Redis，才能获得跨机器统一活进程视图。

### 2026-06-25 - Reduce Session Log Noise

- Why: The previous policy made the session log too chatty for small edit batches, which reduced its usefulness as a concise handoff artifact.
- What: Tightened the repo rules in `AGENTS.md`, `docs/guides/ai-workflow.md`, `docs/guides/self-checklist.md`, and `.opencode/skills/code-update-handoff/SKILL.md` plus its `agents/openai.yaml` metadata so logging happens once per meaningful session boundary.
- How: Kept the same `Why / What / How / Result` shape, but changed the trigger from per-batch persistence to per-session reusable information, with related edits merged into one entry.
- Result: Future sessions should write fewer, denser log entries that are easier for other developers and agents to scan.
- Pitfalls: None.
- Verification: `git diff --check`; `/Users/kaka/Desktop/intelligent-test-agent/.tmp/skill-validate-venv/bin/python3 /Users/kaka/.codex/skills/.system/skill-creator/scripts/quick_validate.py .`.
- Next: Use the new rule in subsequent sessions and avoid file-level log spam.

### 2026-06-25 - 修复运行管理页面因 ID 格式不一致导致查询失败的问题

- Why: 超级用户在设置-运行管理页面无法看到容器、进程状态。经排查发现：数据库中存在历史/异常写入的 `backend_process_id` 等字段，其格式与当前领域对象要求不一致（如 `BackendProcessId` 要求 `bjp_` 前缀），导致 RowMapper 构造领域对象时抛出 `IllegalArgumentException`，整个页面查询失败。
- What:
  - 新增 Flyway migration `V15__add_opencode_process_id_check_constraints.sql`
  - 清理不符合前缀规则的脏数据：删除 `backend_java_processes` 中 `backend_process_id` 不以 `bjp_` 开头的记录，删除 `opencode_container_managers` 中 `manager_id` 不以 `mgr_` 开头的记录，删除 `opencode_server_processes` 中 `process_id` 不以 `ocp_` 开头的记录
  - 添加数据库 CHECK 约束，确保 ID 前缀格式正确，防止未来再写入不符合格式的数据
- How: 通过 Flyway migration 执行 DELETE 清理脏数据 + ALTER TABLE 添加 CHECK 约束。
- Result: 运行管理页面查询不再因脏数据导致领域对象构造失败；数据库层面新增约束防止非法 ID 写入。
- Pitfalls:
  - 一开始误认为 `LinuxServerId` 也需要 `lsrv_` 前缀，实际上它要求 IPv4 地址格式
  - `OpencodeContainerId` 只要求非空文本，无固定前缀要求
- Verification: 需要在有脏数据的环境中重启后端验证 migration 执行成功，页面可正常加载。
- Next: 建议用户执行 SQL 查询确认是否存在脏数据：`SELECT backend_process_id FROM backend_java_processes WHERE backend_process_id NOT LIKE 'bjp_%';`

### 2026-06-25 - 为 F-WRAPP 应用新增远程代码库用于测试工作区和分支功能

- Why: 本地开发环境数据库中，F-WRAPP 应用只有本地代码库，需要新增远程 Git 代码库用于测试工作区创建、版本库克隆、分支操作等功能。
- What:
  - 在 `code_repositories` 表新增 `repo_wrapp_mimoagent` 代码库记录，git_url 为 `https://gitee.com/wrui233/mimoagent`
  - 在 `application_repository_links` 表新增关联，将新代码库关联到 F-WRAPP 应用 (app_id: 113023)
  - 拉取远程分支并重启前后台服务
  - 更新 `.tmp/test-data-add-mimoagent-repo.md` 文档，记录测试场景、测试步骤、测试数据
- How: 通过 Docker exec 执行 psql 命令直接操作本地数据库（15432端口），使用 INSERT ... ON CONFLICT 语法保证幂等。
- Result: F-WRAPP 应用现在关联了两个代码库（本地仓库 + 远程仓库），可用于测试工作区和分支功能；前后台服务已重启成功。
- Pitfalls: 一开始误修改了 `repo_fcoss_main` 的 git_url，后来恢复原数据并新增正确记录。
- Verification: 数据库查询确认新增记录存在，前端可访问 `http://127.0.0.1:3000`。
- Next: 用户验证工作区和分支功能是否正常。

### 2026-06-25 - 将 wr 用户角色改为应用管理员

- Why: 用户要求将 wr 用户从普通用户角色改为应用管理员角色。
- What: 更新 `user_roles` 表，将 wr 用户的 `dict_id` 从 `dict_role_user` 改为 `dict_role_app_admin`。
- How: 通过 Docker exec 执行 psql UPDATE 命令。
- Result: wr 用户角色已从"普通用户"改为"应用管理员"。
- Pitfalls: 无。
- Verification: 数据库查询确认角色已更新。
- Next: 无。
### 2026-06-25 - 设置"添加成员"下拉项改为单行 userId · userName

- Why: 用户反馈下拉项上下两行（`username` + `userId`）不利于在候选很多时快速浏览，希望改为单行紧凑展示，文案顺序为 `userId · userName`。
- What: `frontend/apps/agent-web/src/components/settings/SettingsAppWorkspacePanel.vue` 模板的 `el-autocomplete` 自定义下拉项从上下两行（`username` 加粗 + `userId` 灰底）合并为单行 `<span>{{ item.userId }} · {{ item.username }}</span>`；CSS 同步去掉 `flex-direction: column` / gap / `ta-user-suggestion-name` / `ta-user-suggestion-meta` 旧样式，改为 `display: flex; align-items: center; white-space: nowrap;` 的单行布局。`frontend/apps/agent-web/README.md` 描述从"每项显示 username + userId"更新为"每项单行展示 userId · userName"。
- How: 模板 / CSS 收敛到单 span + 单 flex 行；后端 SQL / 选中 / 按钮切换逻辑均不动。
- Result: 下拉项单行展示 `userId · userName`，不换行；按钮状态切换、添加、成员刷新行为与上一版一致。
- Pitfalls: `white-space: nowrap` 防止 userId / username 较长时换行；下拉项需要单 span 而非两个 span，el-autocomplete 选中时按整段 text 匹配 `value-key="username"`，仍能正确触发 `onUserSelected`。
- Verification: `corepack pnpm --filter @test-agent/agent-web typecheck` 通过。
- Next: 等用户验收。

### 2026-06-25 - 设置"添加成员"下拉项精简为 username + userId

- Why: 用户反馈"添加成员"下拉项原本展示 `username · userId · unifiedAuthId` 三段信息过于冗长，希望精简为 `username + userId` 两段，移除 unifiedAuthId。
- What: `frontend/apps/agent-web/src/components/settings/SettingsAppWorkspacePanel.vue` 模板的 `el-autocomplete` 自定义下拉项从 `{{ item.username }}` / `{{ item.userId }} · {{ item.unifiedAuthId }}` 改为 `{{ item.username }}` / `{{ item.userId }}`；`frontend/apps/agent-web/README.md` 同步把"每项显示 username + userId"写入 el-autocomplete 描述。
### 2026-06-25 - 调整右侧对话输入区发送与附件入口

- Why: 用户反馈右侧对话框发送按钮应放在输入框右边，左下角两个图标按钮需要去掉一个，另一个改成上传附件按钮；后台暂不支持上传，先实现前端弹窗样式。
- What:
  - `FigmaChatPanel.vue` 把发送/停止按钮移到 textarea 右侧，动作行左侧只保留“上传附件”图标按钮；删除旧的“清空输入”和“下载文件”入口。
  - 新增 `attachmentDialogOpen` 控制的面板内弹窗，展示上传区域、关闭按钮和“当前仅展示前端样式，暂未连接后台上传能力”的状态说明；Esc 和遮罩点击可关闭。
  - `FigmaChatPanel.test.ts` 增加上传附件弹窗打开用例。
  - `frontend/README.md`、`frontend/apps/agent-web/README.md`、`frontend/apps/agent-web/src/PACKAGE.md` 同步说明附件上传当前只有前端样式，未接后台。
- How: 复用现有 FigmaChatPanel 组件和面板内抽屉遮罩风格，未新增 API、未接文件 input、未修改 backend-api；发送仍走原 `send` emit，停止仍走原 `stop` emit。
- Result: 右侧输入区发送按钮和截图期望一致地靠在文本框右侧；左下动作区只剩上传附件入口；点击后显示前端样式弹窗并明确后台未接入。完整三服务重启因 `.env.local` PostgreSQL 连接失败未完成，前端 dev server 单独启动成功。
- Pitfalls: `./restart-dev-services.sh --env-file .env.local` 后端失败在 `DruidDataSource` 初始化 PostgreSQL 连接，日志为 `PSQLException: 尝试连线已失败`，底层 `EOFException`；本次未修改 `.env.local`。
- Verification: `corepack pnpm test -- FigmaChatPanel.test.ts` 通过（18 files / 104 tests）；`corepack pnpm --filter @test-agent/agent-web typecheck` 通过；`corepack pnpm --filter @test-agent/agent-web build` 通过（仅既有 chunk size warning）；`./restart-dev-services.sh --env-file .env.local` 构建通过但后端 readiness 超时；单独 `corepack pnpm --filter @test-agent/agent-web dev` 已启动，`curl -I http://127.0.0.1:3000/` 返回 200。
- Next: 等数据库连接恢复后重新执行完整三服务重启并做页面级验收；后台附件上传接口接入时再把弹窗从样式态升级为真实文件选择和提交链路。

- How: 仅改模板里 `<span class="ta-user-suggestion-meta">` 的内容；CSS class / 选中逻辑 / 按钮切换 / 后端 SQL 条件均不动。
- Result: 下拉项简化为上下两行（用户名加粗 + userId），下方的 `unifiedAuthId` 不再展示；后端仍按 userId / unifiedAuthId / username 三个字段 LIKE 命中，前端展示只是收敛。
- Pitfalls: 无。
- Verification: `corepack pnpm --filter @test-agent/agent-web typecheck` 通过。
- Next: 等用户验收；如需进一步压缩为单行可再合并 `ta-user-suggestion` flex 方向。

### 2026-06-25 - 设置"添加成员"合并为 el-autocomplete 异步下拉搜索

- Why: 用户反馈左下角"设置 → 应用与工作区 → 应用人员管理"tab 下同时存在"搜索用户"和"按 ID 新增成员"两块入口，操作割裂；要求把搜索框升级为异步下拉（输入即拉候选），后端搜索要同时匹配 userId / unifiedAuthId / username 三个字段，选中下拉项后"搜索"按钮文案切换为"添加"并可直接加入应用。
- What:
  - 后端 `backend/test-agent-persistence/src/main/java/com/icbc/testagent/persistence/JdbcUserRepository.java` 的 `findPage(keyword, pageRequest)` 把 LIKE 条件从 `lower(username) or lower(unified_auth_id)` 扩展为 `lower(user_id) or lower(unified_auth_id) or lower(username)`，count 查询同步对齐；`UserRepository` 注释同步更新为"按 userId / unifiedAuthId / username 任意字段 LIKE 匹配"。keyword 为空时仍走全量分支，行为不变。
  - 文档 `docs/api/http-api.md` 把 `/configuration-management/users?keyword=&page=&size=` 用途补成"按 `userId` / `unifiedAuthId` / `username` 任一字段大小写不敏感 LIKE 搜索已有平台用户；keyword 为空时返回全量"。
  - 前端 `frontend/apps/agent-web/src/components/settings/SettingsAppWorkspacePanel.vue`：
    - 删除 `users` / `memberUserId` 旧状态，新增 `selectedUser: PlatformUserSummary | null`。
    - 新增 `fetchUserSuggestions(keyword, callback)` 作为 `el-autocomplete` 的异步拉取实现（Element Plus 自带 300ms 防抖），失败时回写 `errorMessage` 并返回空数组。
    - `addMember` 重构为 `addSelectedMember`：只对 `selectedUser` 生效，添加成功后清空 `selectedUser` + `userKeyword` 并刷新成员列表。
    - 模板把"搜索用户"和"按 ID 新增成员"两块合并为"添加成员"区：`el-autocomplete` 绑定 `userKeyword`，`value-key="username"`，下拉项自定义模板展示 `username` + `userId · unifiedAuthId`；按钮在 `selectedUser` 为空时渲染"搜索"（兜底触发一次搜索），非空时渲染 `type="primary"` 的"添加"，点击直接调 `addSelectedMember`。
    - 原"按 ID 新增成员"区内的成员列表拆出来变成"已有成员"区，保留删除按钮和原有交互。
    - `clearAppContext` 同步清空 `selectedUser` / `userKeyword`。
    - 追加 `.ta-user-suggestion` / `.ta-user-suggestion-name` / `.ta-user-suggestion-meta` 样式。
  - 文档：`frontend/apps/agent-web/README.md` 和 `frontend/apps/agent-web/src/PACKAGE.md` 补一行描述 el-autocomplete 异步下拉与按钮状态切换。
- How: 复用现有 `api.searchUsers(keyword, page, size)`（`backend-api` 包未变），通过 `el-autocomplete` 的 `fetch-suggestions` 把候选用户拉到下拉；选中事件落库到 `selectedUser`，按钮 `v-if` 切换文案；后端 LIKE 字段扩展在 JDBC 层完成，不动 `UserRepository` 接口与上层 service / controller / DTO，API 形态不变。
- Result: 设置"添加成员"区只剩一个输入框 + 一个按钮；输入 userId / 用户名 / 统一认证号任一时下拉都会命中，后端能匹配；选中后按钮从"搜索"切换为"添加"并可直接加入应用；老成员列表移到底部"已有成员"区，仍可移除。
- Pitfalls: `el-autocomplete` 的 `fetch-suggestions` 是 debounced，但要求函数签名是 `(keyword, callback) => void`，不能用 `async/await + return`；另外 `value-key` 必须命中候选对象上的字段（这里用 `username`），下拉项 `label` 才能匹配。
- Verification: `corepack pnpm --filter @test-agent/agent-web typecheck` 通过；`corepack pnpm --filter @test-agent/agent-web lint` 通过；`backend` 端因 `JdbcUserRepository.findPage` 无现成单测覆盖（`grep` 全仓也未发现 `users.findPage` 调用），改动只扩 SQL 条件、不动接口与契约，暂无新增单测；后续如需补 `JdbcRepositoryIntegrationTest` 一条按 userId / unifiedAuthId / username 各自命中一条的断言。
- Next: 等用户验收；若用户希望"搜索"按钮文案在已选中也保留作为兜底，可以再保留一个无副作用的"重新搜索"按钮，避免按钮消失带来的"还能不能搜"歧义。

### 2026-06-25 - 新增分布式定时任务框架

- Why: 后端需要一个分布式多节点安全的定时任务框架，避免同一任务在多个节点重复执行，并统一持久化任务定义、用户计划预留和运行审计记录；本轮只落框架，不新增具体业务任务。
- What:
  - 新增 `backend/test-agent-scheduler` Maven module，提供 `ScheduledTaskHandler`、`ScheduledTaskContext`、`ScheduledTaskResult`、Cron 计算、启动注册同步、Redis `SET NX PX` + Lua token 续租/释放锁、后台 runner、管理服务和默认关闭配置。
  - 扩展 domain：新增 `scheduler` 聚合和值对象；`Session`、`Run`、`SessionMessage` 增加 `ConversationSourceType`、`sourceRefId` 和用户来源字段，默认保持 `MANUAL`。
  - 扩展 persistence：新增 `V15__create_scheduler_framework_tables.sql`，创建 `scheduled_tasks`、`scheduled_task_plans`、`scheduled_task_runs`，并给 `sessions`、`runs`、`session_messages` 增加来源预留字段；新增 `JdbcScheduledTaskRepository`。同时把 F-COSS seed migration 从重复的 `V10__seed_fcoss_application.sql` 调整为 `V10_1__seed_fcoss_application.sql`，避免 Flyway 版本冲突。
  - 扩展 API/app：新增 `/api/internal/platform/scheduler-management` 超级管理员管理 API；app 依赖 scheduler，并在 `application.yml` 中增加 `TEST_AGENT_SCHEDULER_*` 配置入口，默认 `enabled=false`。
  - 修复一个阻断 `test-agent-api -am` 编译的既有调用问题：`RunApplicationService.subscribeAgentEvents(...)` 调用补传 `resolvedAgentId`。
  - 文档同步更新 backend/module README/PACKAGE、API、架构依赖、数据库、部署、安全文档。
- How: 按 domain → persistence → scheduler module → API → app/config → docs 的顺序推进；互斥只使用 Redis 锁，不提供本机或数据库锁 fallback；runner 对 due cron 只补一次并把下次触发时间推进到当前时间之后，重叠触发写入 `SKIPPED + skipReason`。
- Result: 框架已可注册 handler Bean、同步任务定义、异步执行 Cron/管理员手动触发、统一记录运行状态；普通用户级 Cron 计划只落库和领域模型，不开放 HTTP API，不创建定时会话/Run。
- Pitfalls: 工作区存在无关的 `requirements/todo/deployment.md` 修改，属于历史需求草案，不作为编码依据，也不会纳入本次提交。scheduler 启用时如果 `test-agent.redis.enabled=false` 或缺少 `StringRedisTemplate` 会启动失败，这是预期安全边界。
- Verification: `mvn -pl test-agent-domain -am test`、`mvn -pl test-agent-common test`、`mvn -pl test-agent-persistence -am test`、`mvn -pl test-agent-scheduler -am test`、`mvn -pl test-agent-api -am test`、`mvn -pl test-agent-app -am test` 均通过；提交前已补跑 `mvn test`，全量后端测试通过。
- Next: 如后续要新增具体业务定时任务，应放在所属业务模块实现 `ScheduledTaskHandler`；如要开放用户级计划 API，需要先补权限、配额、payload 安全和后台会话发送设计。

### 2026-06-25 - Fix el-date-picker month cells to show "1月/2月/…" in Chinese

- Why: 用户反馈「+新增版本」弹窗里的 el-date-picker (type=month) 打开后，月份单元格里显示英文 "Jan/Feb/…"，希望显示中文 "1月/2月/3月/…"，与项目里其他中文文案风格一致。
- What:
  - `frontend/apps/agent-web/src/main.ts` 引入 `element-plus/es/locale/lang/zh-cn` 和 `dayjs/locale/zh-cn`，调用 `dayjs.locale("zh-cn")`；在原 zh-cn locale 上派生一个只覆盖 `el.datepicker.months` 12 项的浅拷贝（`jan: "1月"`, `feb: "2月"`, …, `dec: "12月"`），再把这份 locale 通过 `app.use(ElementPlus, { locale: zhCnWithArabicMonths })` 注入。
  - 不直接用 Element Plus 默认的 `zh-cn` locale 是因为它把月份渲染为中文数字"一月/二月/…"（Element Plus 2.12 的 `el.datepicker.months.{jan,dec}` 默认值），与用户期望的阿拉伯数字 "1月/2月/…" 不一致。
  - `frontend/apps/agent-web/tests/workbench.spec.ts` 既有"yyyy年M月"测试里追加两步断言：打开日期面板后能定位到 `el-month-table`，并看到 `^1月$` 和 `^6月$` 文案（之前是 `console.log` 调试输出，已清理）。
- How: 复制 zh-cn locale 的浅层结构再覆盖 `datepicker.months` 这一层，其它字段（按钮、星期、占位符等）原样保留，避免影响其它使用 Element Plus 的位置。
- Result: e2e 中 `+新增版本` 弹窗的月份面板渲染为 "1月/2月/…/12月"；`pnpm playwright test workbench.spec.ts -g "cascade"` 6 个 case 全部通过（1 个 mobile 被 skip）。
- Pitfalls: 仅设置 `dayjs.locale("zh-cn")` 不够，Element Plus 月份面板走的是 i18n 包而不是 dayjs 的 locale；需要同时注入 Element Plus locale。`zh-cn` locale 默认会把月份渲染为 "一月/二月/…"，需要再浅拷贝覆盖 `months` 字段。
- Verification: `pnpm playwright test workbench.spec.ts -g "新增版本 dialog opens"` 通过；`pnpm playwright test workbench.spec.ts -g "cascade"` 全部通过。
- Next: 等用户验收；如果未来 Element Plus 升级破坏了 i18n key，需要在 e2e 第一时间复现。

### 2026-06-25 - Repair FigmaChatPanel.vue duplicate declarations blocking dev server

- Why: `frontend/apps/agent-web/src/components/FigmaChatPanel.vue` 在某个合并后存在两套 `defineProps` / `defineEmits`（一份带 `processStatus`/`initialize-process`，另一份带 `selectedModelLabel`/`open-model-picker`）和重复的 `const hasFileChanges = computed(...)`，导致 vue-tsc 报 TS2451 / TS2339 / TS2551 / TS2769 共 ~30 条错误，Vite dev server 抛 "Identifier 'props' has already been declared"，e2e 跑不起来。
- What: 删掉旧的 `const props = defineProps<{...}>()` / `const emit = defineEmits<{...}>()` 整段以及重复的 `hasFileChanges`，把保留版的 props (`selectedModelLabel`/`modelPickerDisabled`/`stopDisabled`/`stopDisabledReason`/`processStatus`/...) 与 emits (`open-model-picker`/`initialize-process`/...) 合到一份。
- How: 全文检索确认旧版 props（`selectedModelLabel`/`history`/`modelPickerDisabled`/`stopDisabled`）在模板 / script 中没有引用，所以可以直接合并而非 union；保持新版（带 `processStatus` 等的）作为唯一一份。
- Result: `pnpm typecheck` 对 FigmaChatPanel 的错误清零；dev server 重新启动后 HTTP 200，e2e 可以正常 navigate 到 `/`。
- Pitfalls: 这个修复与"中文月份"任务无关，但属于阻断 dev server 的预存在 bug；不修就测不了用户反馈。
- Verification: `pnpm typecheck` 仅剩其它预存在错误（与本 PR 无关），`pnpm playwright test workbench.spec.ts -g "新增版本 dialog opens"` 通过。
- Next: 在 commit message 里把"中文月份"和"FigmaChatPanel 修复"拆成两条提交，避免单点耦合。

### 2026-06-24 - Require Session Log In Project Rules

- Why: The session log needed to be treated as a first-class tracked artifact, not an ad hoc local note, so remote commits carry the handoff context too.
- What: Updated `AGENTS.md`, `docs/guides/ai-workflow.md`, and `docs/guides/self-checklist.md` to require `.agents/session-log.md` updates and to describe how it is included in commits.
- How: Kept the change in the project entry docs instead of business code, and reused the existing Why/What/How/Result log shape so future sessions stay consistent.
- Result: Future code-change batches should leave behind a committed session log that explains the change for other developers and agents, including remote-push ready workflows.
- Pitfalls: None.
- Verification: `git diff --check` not run yet.
- Next: Run a light diff sanity check, then commit the doc updates together with this log entry.

### 2026-06-24 - Add Code Update Handoff Skill

- Why: Code-change batches in this repo needed a shared handoff rule so future agents can see the real status and avoid re-deriving context.
- What: Added `.opencode/skills/code-update-handoff/SKILL.md`, fixed `agents/openai.yaml`, and created this session log file.
- How: Started from the skill-creator template, then replaced placeholders with a repo-specific workflow that always emits `Not done yet` and appends a compact log entry.
- Result: Future handoffs can stay candid, and other developers or agents can quickly understand the reason, scope, approach, and expected effect of a change.
- Pitfalls: `quick_validate.py` needed `PyYAML`; resolved by running it inside `.tmp/skill-validate-venv`.
- Verification: `./.tmp/skill-validate-venv/bin/python3 /Users/kaka/.codex/skills/.system/skill-creator/scripts/quick_validate.py .` in `.opencode/skills/code-update-handoff`.
- Next: Use this skill whenever a batch edits repository files so the handoff and session log stay consistent.

### 2026-06-24 - Simplify workspace selector + add +新增版本 + seed F-COSS workspaces

- Why: 用户希望「应用工作空间」两级菜单只展示工作空间名（一级），hover 展开版本子菜单（二级），版本列表底部加 `+新增版本` 行，弹 yyyy年M月 时间组件，并在 F-COSS 应用下多造几个工作空间模板。
- What:
  - 后端 `ManagedWorkspaceApplicationService` 新增 `yyyy年M月` 版本格式校验，`sanitizeVersionForBranchAndPath` 把 `2024年1月` 转为 `2024-01` 用于派生分支名 / 物理路径，`normalizeVersion` / `resolveBranch` / `appRepoRoot` / `personalRepoRoot` 全部接入。
  - 新增 Flyway `V13__seed_fcoss_more_workspaces.sql`，在 V10 的 F-COSS 数据基础上追加 3 个工作空间模板（移动端 / 数据同步 / 报表）和对应初始版本。
  - `WorkbenchFooter.vue` 简化一级菜单只显示 `workspaceName`、去掉 `directoryPath · branch` 副标题；子菜单底部加「+新增版本」行；新增 el-dialog + `ElDatePicker` (`type=month` / `format=yyyy年M月` / `value-format=yyyy年M月`) 提交 `create-version` 事件。
  - `FigmaFileExplorer.vue` 透传 `creatingVersion` prop 与 `createVersion` emit。
  - `AgentWorkbench.vue` 接入 `handleCreateVersion`：调 `api.createWorkspaceVersion`，成功后失效 `versionsByTemplateId` 缓存并把新版本切到工作区；`@create-version` 监听接好。
  - `workbench.spec.ts` mock 新增 `POST .../versions` 路径拦截，捕获用户原值 `version` 字段。
  - 文档：更新 `docs/api/http-api.md`（POST 规则 / 两级菜单说明）、`docs/deployment/database.md`（V13 节）、`backend/test-agent-workspace-management/README.md`（测试覆盖说明）、`frontend/apps/agent-web/README.md`（两级菜单简化 / 「+新增版本」说明）。
- How: 后端先扩 `Pattern` + `sanitize`，新加一个 `Path.endsWith` 风格的 Java 单元测试绕开 Windows 路径分隔符；前端用 Element Plus 的 `el-dialog` + `el-date-picker` 直接覆盖时间选择场景；V13 用 `where exists / where not exists` 幂等保护。
- Result: 工作空间选择器符合「只显示名 + hover 出版本 + 底部新增版本」三段式；后端同时兼容 `yyyyMMdd` 和 `yyyy年M月`，新增版本入参为 `2024年1月` 原值；F-COSS 应用从 1 个模板扩展为 4 个模板。
- Pitfalls: 仓库里两个旧测试（`createsStandardApplicationVersionWorkspaceAndRecordsRecentUsage` / `createsPersonalWorkspaceFromApplicationVersionWorktree`）在 Windows 上因路径分隔符断言失败，与本次改动无关（已用 `git stash` 验证过改动前的状态同样失败）；本次新测试改用 `Path.endsWith` 规避。
- Verification: `pnpm typecheck` 通过；`mvn -pl test-agent-workspace-management -am test` 我新加的 2 个测试通过（8 / 10），其余 2 个失败是上面提到的预存在 Windows 路径问题。
- Next: 等用户审过 PR 提单；如需进一步简化可考虑把 FigmaFileExplorer 的 `creatingVersion` 与工作区切换的反馈合并。

### 2026-06-25 - 右上角用户菜单顶部灰显用户角色（来自 dictionaries.dict_label）

- Why: 用户反馈「F-COSS」右上角下拉菜单只有「用户名 / 退出登录」两项，希望在菜单顶部加一行灰显展示当前用户角色；角色来源涉及 `users`（/api/auth/me 上下文）→ `user_roles`（关联角色 code）→ `dictionaries.dict_label`（中文展示名）三张表。
- What:
  - 后端：`AuthDtos.CurrentUserResponse` 新增 `roleLabels: List<String>` 字段（与 `roles` 等长、对齐）；`AuthController.me` 注入 `DictionaryRepository`，按 `Dictionary.DICT_KEY_ROLE` + role code 查 `dict_label`，缺失时回退为 role code 本身，避免阻断主链路。
  - 共享类型：`shared-types/CurrentUser` 新增 `roleLabels?: string[]`，向下兼容旧 token / 旧响应。
  - 前端壳子：`FigmaShell` 新增 prop `currentUserRoleLabels?: string[]`；下拉菜单顶部以 `ShieldCheck` 图标 + 灰显样式新增一行（class `figma-user-menu-role`），多角色用「、」拼接；`roleLabels` 为空或缺失时整行 v-if 不渲染，避免出现「角色：」空文案。
  - 入口串联：`AgentWorkbench` 把 `authStore.currentUser?.roleLabels` 透传给 `FigmaShell`。
  - e2e：`workbench.spec.ts` 的 `/api/auth/me` mock 同步返回 `roleLabels`（新增 `roleLabelOf` 工具，固定映射 `SUPER_ADMIN / SYSTEM_ADMIN / APP_ADMIN / USER`），`user avatar menu logs out` 用例额外断言下拉菜单顶部出现 `.figma-user-menu-role` 灰显行且文案为「应用管理员」。
  - 文档：`docs/api/http-api.md` 同步 `CurrentUserResponse.roleLabels` 字段、三表数据来源、字典缺失回退行为；`frontend/apps/agent-web/README.md` 顶栏下拉菜单条目补一句角色灰显行说明。
- How:
  - 后端先扩 DTO，再在 controller 用 `dictionaryRepository.findByDictKeyAndValue(...)` 现成 API 翻译角色；测试新增 `meReturnsRolesAndChineseRoleLabelsFromDictionary` / `meFallsBackToRoleCodeWhenDictionaryEntryIsMissing` 两条覆盖主链路 + 回退；`loginReturnsRolesLoadedByAuthService` 保留。
  - 前端用 lucide-vue-next 的 `ShieldCheck`（已存在于 `node_modules`），样式复用现有 `.figma-user-menu-summary` / `.figma-user-menu-item` 的基础 padding/border-radius，仅叠加更小字号 + 次要色 + 灰底图标 + 不可点击 cursor，保留设计语言一致。
  - e2e mock 用 `roleLabelOf` 把 mock 后端的字典翻译前置到 e2e 层，避免 e2e 依赖新的 GET /api/dictionaries 接口；这样 future 字典表字段变化只需要改 mock 工具即可。
- Result: 点击右上角 F-COSS 头像，下拉菜单顶部出现一行灰色角色（如「应用管理员」），位置在用户名 / 退出登录之上；多角色显示为「应用管理员、普通用户」；后端 `/api/auth/me` 的 `roleLabels` 与 `roles` 顺序一致。
- Pitfalls: 工作区里同时存在另一位开发者「opencode 进程本地节点回退 & 重置绑定」相关文件的中间态改动（`UserOpencodeProcessStatusResponse` / `UserOpencodeProcessAssignmentService` / `RuntimeDtos` / `UserOpencodeProcessController` / `OpencodeProcessManagementRepository` / `JdbcOpencodeProcessManagementRepository` / `FigmaChatPanel` / `backend-api/index.ts` / `RuntimeControllerTest` / `UserOpencodeProcessAssignmentServiceTest`），会破坏 `mvn -am` 与 `pnpm typecheck` 的全量构建；本次提交只 `git add` 上面 9 个直接相关文件 + 本条 session-log，未把这些未完成改动一起带入。
- Verification: 临时 stash 掉上述中间态后，`mvn -pl test-agent-api test -Dtest=AuthControllerRolesTest` 3/3 通过；`pnpm --filter @test-agent/shared-types typecheck` 通过；FigmaShell 的 `ShieldCheck` 在 `lucide-vue-next` 类型声明中存在，prop 与 `currentUserRoleLabels` 字段链路类型自洽。
- Next: 等用户验收；如需补充真实字典接口（`GET /api/dictionaries?dictKey=ROLE`）让前端不再依赖 `/api/auth/me` 翻译结果，下一轮再加，避免本次改动超出最小范围。

### 2026-06-25 - 本地运行管理注册默认使用局域网 IPv4

- Why: 用户追问 `888888888` 为什么还活着，以及本机是否没有取到局域网 IP。排查确认本机默认路由网卡 `en0` 是 `192.168.100.115`，但本地启动链路默认把后端 Java 进程、opencode manager 和 user opencode 进程注册到 `127.0.0.1`；`888888888` 当时对应 opencode 进程健康检查返回 200，所以不是僵死数据，只是服务器标识用了 loopback。
- What:
  - `restart-dev-services.sh` 在读取 `.env.local` 后，如果未显式设置 `TEST_AGENT_LINUX_SERVER_ID`、`TEST_AGENT_BACKEND_LISTEN_URL` 或 `OPENCODE_MANAGER_LINUX_SERVER_ID`，会检测默认路由网卡 IPv4，并用该地址作为本地运行拓扑注册值。
  - `tools/verify-dev-scripts.sh` 增加 fake `route` / `ipconfig` 覆盖，防止脚本回退成 `127.0.0.1`。
  - `RunEventLiveBus` 改为通过 `ObjectProvider<RunEventRemotePublisher>` 可选注入远端广播端口，避免 Redis bus 未注册时本地 Spring 启动失败。
  - `RunApplicationService` 补上 `subscribeAgentEvents` 新签名需要的 `resolvedAgentId` 参数，修复当前 `main` 编译中断点。
  - 文档同步说明本地脚本自动检测默认路由 IPv4，生产和多机部署仍应显式配置。
- How: 优先用 macOS `route -n get default` 找默认路由接口，再用 `ipconfig getifaddr` 取 IPv4；Linux 下用 `ip route get 1.1.1.1` 的 `src` 地址；过滤 `127.*`、`169.254.*` 和 `0.0.0.0`。
- Result: 本地未配置显式服务器 ID 时会注册为 `192.168.100.115` 这类局域网地址，而不是 `127.0.0.1`；运行管理面板仍只展示有 Redis 心跳的活进程。
- Pitfalls: 当前工作区另有未提交的 `WorkspaceApplicationService` 日志改动引入 `org.slf4j` 但模块未声明依赖，导致 `mvn -pl test-agent-app -am test` 和实际重启构建被挡住；本次不回滚该无关改动。
- Verification: `bash tools/verify-dev-scripts.sh` 通过；`mvn -pl test-agent-event test` 10/10 通过；`git diff --check` 通过。本地完整重启因上述 workspace 无关编译错误未完成。
- Next: 修复或移除 workspace 模块未提交日志改动后，重新执行 `./restart-dev-services.sh --env-file .env.local`，再验证运行管理 overview 中 `linuxServerId` 是否为 `192.168.100.115`。

### 2026-06-25 - 增加 local 网关让本地 127.0.0.1:4096 的 opencode server 健康检查走直连

- Why: V17 + 心跳自举已让数据库拓扑可见，但 `UserOpencodeProcessAssignmentService.status` 仍会调 `gateway.checkHealth` 走 manager WebSocket；本地没起 opencode-manager 时返回 `OPENCODE_UNAVAILABLE`，又因为 V17 把容器 `current_processes=max_processes=1`，`canRebuildOn` 也返回 false，所以用户重启后前台升级后的报错变成 "opencode 进程健康检测失败，且原 Linux 服务器没有可用容器"，依然卡死。
- What:
  - `TestAgentRuntimeProperties.ManagerControl` 增加 `gatewayMode`（默认 `socket`），空值或空白自动规整为 `socket`。
  - 新增 `LocalOpencodeProcessManagerGateway`（`@ConditionalOnProperty(gateway-mode=local)`）：`checkHealth` 直接对 `opencode_server_processes.baseUrl` 跑 HTTP GET（连接 2s / 请求 3s 超时，2xx/3xx 健康），`startProcess` 走占位返回 `pid=0, status=local-skip`；网络异常统一包成 `PlatformException(OPENCODE_UNAVAILABLE)` 转 unhealthy，不把异常直接抛给前端。
  - `SocketOpencodeProcessManagerGateway` 加 `@ConditionalOnProperty(gateway-mode=socket, matchIfMissing=true)` 与 local 实现互斥。
  - `application-local.yml` 增 `test-agent.opencode.manager-control.gateway-mode`（`${TEST_AGENT_OPENCODE_GATEWAY_MODE:local}`）。
  - 测试：新增 `LocalOpencodeProcessManagerGatewayTest`（2xx / 3xx / 5xx / 连接失败 / startProcess 占位共 5 用例）。
  - 文档：`docs/deployment/database.md` 在 V17 节增 "健康检测/启动网关选择" 说明；`docs/deployment/backend.md` 在 "本地开发 opencode 机器预置" 节说明 local 网关 + 回切 socket 语义；`backend/test-agent-opencode-runtime/README.md` 同步 gateway 实现与测试清单。
- How: 用 Spring `@ConditionalOnProperty` 互斥激活 `SocketOpencodeProcessManagerGateway` 与 `LocalOpencodeProcessManagerGateway`；默认值是 `socket`，与生产路径完全等价；切到 `local` 仅替换 `checkHealth` / `startProcess` 的实现，其余控制面、Redis 心跳、ManagerConnectionRegistry、manager-backend 连接维护完全不动。
- Result: 本地启动后（profile=local、opencode server 在 127.0.0.1:4096 监听），前台 `888888888` 登录后右侧对话窗口的 opencode 进程状态会落到 READY（健康检测直接命中本机 baseUrl），不必再启动 opencode-manager 容器；生产 profile 不改配置就走原 `SocketOpencodeProcessManagerGateway`，manager 行为完全保留。
- Pitfalls: V15 的 CHECK 约束让 `OpencodeContainer` 的 `max_processes <= (port_end - port_start + 1)`，单端口 4096 仍然是 `max=1, current=1`，与 V17 共存；`OpencodeProcessHealthCommand` / `OpencodeProcessStartCommand` 来自 `com.icbc.testagent.opencode.runtime.process` 而非 domain 包，写测试时易错。`PlatformException` 没有 `unavailable` 静态工厂，必须用 `new PlatformException(ErrorCode, String)`。
- Verification: `mvn -pl test-agent-opencode-runtime,test-agent-persistence test` 通过（21 + 105 用例，其中 `LocalOpencodeProcessManagerGatewayTest` 5/5、`BackendJavaProcessLifecycleServiceTest` 3/3）；`mvn -pl test-agent-app -Dtest=AppModuleBoundaryTest test` 1/1 通过；`mvn -DskipTests=true compile` 17 个模块全量编译通过。
- Next: 启服务前用环境变量 `TEST_AGENT_OPENCODE_GATEWAY_MODE=local` 或 `application-local.yml` 默认值覆盖；生产请显式设回 `socket`（或留空走默认）。

### 2026-06-25 - FileExplorer 加"公共目录"独立面板（固定路径内容扫描）

- Why: 用户在 FileExplorer 顶部「F-COSS 主服务-20260620」工作空间标题行希望新增一个"固定路径的内容扫描"，等价于"公共的目录读取"：所有登录用户可读，SUPER_ADMIN 可写，路径由后端配置。要求：作为独立新组件、不破坏现有 Workspace/工作空间/应用版本工作区文件树、保持最小改动、保留与 `WorkspaceFileService` 一致的越权拦截和 1MB UTF-8 上限。
- What:
  - 后端
    - 新增 `backend/test-agent-workspace-management/.../PublicDirectoryService.java`：通过 `@Value("${test-agent.public-directory.path:}")` 注入固定根路径；`isEnabled()` 在路径空/不存在/不是目录时返回 `false`；`listDirectory/readContent/writeContent` 委托给现有 `WorkspaceFileService`，根目录解析阶段抛 `PlatformException(NOT_FOUND)` 统一包装。
    - 新增 `backend/test-agent-api/.../platform/PublicDirectoryController.java`：`GET /api/public/files`、`GET /api/public/files/content` 走 `AuthWebSupport.getAuthPrincipal`（已登录即可），`PUT /api/public/files/content` 走 `AuthWebSupport.requireRole(..., Dictionary.ROLE_SUPER_ADMIN)`（仅超管可写），同时挂新平台 URL 前缀 `/api/internal/platform/public-directory/...`。
    - 配置：`application.yml` 与 `application-local.yml` 在 `test-agent.public-directory.path` 加 `${TEST_AGENT_PUBLIC_DIRECTORY_PATH:}` 默认空字符串（空 = 禁用）。
    - 单元测试 `PublicDirectoryServiceTest` 覆盖未配置、配置根目录不存在、正常 list/read/write 委托、根目录不可访问时 list 抛 NOT_FOUND 四种场景。
  - 前端
    - `frontend/packages/backend-api/src/index.ts` 新增 `listPublicFiles/readPublicFile/writePublicFile`，复用现有 `FileTreeEntry/FileContent/FileStatus` 类型，DTO 字段名与后端 `FileTreeEntryResponse/FileContentResponse/WriteFileRequest` 对齐。
    - 新组件 `frontend/apps/agent-web/src/components/PublicDirectoryPanel.vue`：仿 `FileExplorer` 的 FileTree 风格（FolderTree/Loader2/AlertTriangle），接收 `canWrite` 和 `baseUrl` 两个 prop，点击文件 emit `openFile` 携带 `path + content + readonly`，错误条带 / 加载旋转 / 空态都齐备。
    - `FigmaFileExplorer.vue` 顶部加一行小 toolbar：左 `工作区` / 右 `公共目录` 切换两个视图（与 WorkbenchFooter 平级），切换时通过 `v-if` 卸载不活跃面板，避免滚动区域竞争；新增 `publicDirectoryWritable` / `apiBaseUrl` prop 与 `openPublicFile` emit。
    - `AgentWorkbench.vue` 引入 `isSuperAdmin` computed（基于 `authStore.currentUser?.roles`）传给 FigmaFileExplorer；新增 `openPublicFile` 把 `public:<相对路径>` 作为 `tab.path` 打开 tab（与工作区路径空间隔离）；`saveMutation` 在 tab 路径以 `public:` 开头时改走 `api.writePublicFile`，普通用户永远拿到 readonly tab。`FileContent` 类型从 `@test-agent/shared-types` 引入。
    - 文档
      - `docs/api/http-api.md` 新增 "Public Directory API" 表格（list/read 对所有登录用户开放，write 仅 SUPER_ADMIN）+ 新平台 URL 映射 + 错误码语义。
      - `backend/test-agent-workspace-management/README.md` 主要职责补公共目录行 + 测试覆盖补 `PublicDirectoryServiceTest` 描述。
- How: Service 层只做"路径解析 + 委托"，保留 `WorkspaceFileService` 的越权拦截/UTF-8 1MB/单层目录 1000 条上限；Controller 只做协议转换和角色校验，不直接调 SDK/Repository（符合 API 规范）；前端把公共目录 tab.path 设计成 `public:<相对路径>` 字符串，让 Monaco 仍能用 `languageFromPath` 推断语言，让 `activePath` 不会与工作区文件路径撞名，文件树高亮逻辑零改动；角色判定前后端都做：后端 `requireRole` 是最终边界，前端 `isSuperAdmin` 只是为了隐藏保存按钮。
- Result: FileExplorer 顶部多了一行 `工作区 / 公共目录` 切换；`公共目录` 视图里用现有工作区一样的 FileTree 展示后端配置的固定路径内容，点击文件在中央编辑器打开一个新 tab，普通用户 tab 是 readonly 不可保存，SUPER_ADMIN tab 可保存；`test-agent.public-directory.path` 留空时整个面板退化为"公共目录为空或后端未配置"提示，所有接口返回 404。
- Pitfalls: Mockito 对未声明受检异常的方法不能 `doThrow(new IOException)`，必须改抛 `RuntimeException` 或用 mock 显式允许；`FileContentResponse` 实际只有 `(path, content, size)` 三个字段，没有 `lastModifiedAt`；`PlatformException` 的 `errorCode` 是 record-style 的 `errorCode()` 方法而不是 `getErrorCode()`；`@test-agent/backend-api` 不再导出 `FileContent` 类型，前端要从 `@test-agent/shared-types` 拿；`FigmaFileExplorer` 的 props 已经混入了 `FileExplorerProps & {...}`，新增 prop 时按 union 加上去即可，但 typecheck 时 vue-tsc 会按全部字段推断 emit 签名。
- Verification: `mvn -pl test-agent-workspace-management -am test -Dtest=PublicDirectoryServiceTest` 4/4 通过；`mvn -pl test-agent-workspace-management,test-agent-api -am compile` 编译通过；`pnpm -F @test-agent/backend-api typecheck` 通过；`pnpm -r typecheck` 12/12 packages 通过；`pnpm -F @test-agent/agent-web test` 通过；前后端无新告警/未导入符号。
- Next: 用户需要在 `application-local.yml` 或环境变量里设一个真实存在的目录路径（如 `TEST_AGENT_PUBLIC_DIRECTORY_PATH=D:/shared/fcoss`）才能看到非空内容；如果后端路径含中文/空格要注意 URI 编码（当前用 `Uri.parse(encodeURIComponent)` 仍可能与真实文件系统的"不区分大小写路径"对不上，需要时把 `path` 转成 ASCII 字节）。

### 2026-06-25 - 公共目录按 profile 协商默认路径（guo=D:/agents，其他=/data/agents-pub）

- Why: 上一轮把 `test-agent.public-directory.path` 的默认设为空字符串（禁用态），用户希望按部署环境协商出可立即生效的默认值：本机 Windows 调试用 `D:\agents`，其他 profile（local/test/prod）用 `/data/agents-pub` 作为 Linux 容器挂载点的协商默认；仍然允许 `TEST_AGENT_PUBLIC_DIRECTORY_PATH` env 覆盖或留空禁用。
- What:
  - `application-guo.yml`：新增 `test-agent.public-directory.path: ${TEST_AGENT_PUBLIC_DIRECTORY_PATH:D:/agents}`（guo 是默认激活 profile，匹配本机 Windows 调试的 `D:\agents` 目录）。
  - `application.yml`（base）：把默认从空字符串改为 `${TEST_AGENT_PUBLIC_DIRECTORY_PATH:/data/agents-pub}`，注释里说明各 profile 协商值。
  - `application-local.yml`：把默认从空字符串改为 `${TEST_AGENT_PUBLIC_DIRECTORY_PATH:/data/agents-pub}`，注释改为"base 协商默认 /data/agents-pub，本地如无该目录可显式 env 覆盖或留空禁用"。
  - `application-test.yml` / `application-prod.yml`：补 `test-agent.public-directory` 段（之前没显式声明，会继承 base），默认也是 `/data/agents-pub`，prod 注释强调"必须显式 env 覆盖到实际挂载目录"。
  - `docs/api/http-api.md` 在 Public Directory API 节新增"各 profile 协商默认值"表格，覆盖 guo/local/test/prod 四种场景。
- How: 用 Spring profile 配置层级：base 设协商默认，guo 显式覆盖为 Windows 路径；其他 profile 不显式声明会继承 base；env 始终可覆盖到任意路径或留空禁用。改动只动 `application*.yml` 和 `docs/api/http-api.md`，Java 端 `PublicDirectoryService` / `PublicDirectoryController` 零改动，前端零改动。
- Result: guo profile 启动后无需 env 即可在 `D:\agents` 读到本地内容；local/test/prod 启动后若实际挂载了 `/data/agents-pub` 也立即可用；任意 profile 仍可通过 `TEST_AGENT_PUBLIC_DIRECTORY_PATH=` 留空禁用。
- Pitfalls: Spring profile 配置文件里 `:` 既是 key/value 分隔符又是 env 默认值分隔符，路径里不能带裸 `:`（Windows `D:/agents` 不含冒号，OK）；guo profile 的 `D:/agents` 是 forward-slash，与本仓 `workspace-picker.allowed-roots: "D:/workspace"` 的写法保持一致，Java `Path.of` / `toRealPath` 都能正确处理。
- Verification: `mvn -pl test-agent-app -am compile` 编译通过（4 个 application*.yml 都是 resource 编译，xml binding 验证通过）；`mvn -pl test-agent-workspace-management -am test -Dtest=PublicDirectoryServiceTest` 4/4 仍绿。
- Next: 用户在本机 guo 启动时需要确认 `D:\agents` 目录存在并放点测试文件；其他 profile 部署到 Linux 容器时需要把 `/data/agents-pub` 挂载到实际共享目录，或显式 env 覆盖。

### 2026-06-25 - 公共目录子目录无法展开：模板硬编码只支持两层，改用递归子组件

- Why: 用户报告"公共目录里面的文件夹打不开，点击没有展示子文件内容"。复现路径：在本机 guo profile 下访问 `D:\agents\platform-tester\agent` 这种两级目录，第一级 `agent` 可以展开 chevron，但点击下面任何子项都不显示内容；进一步排查发现 `D:\agents\platform-tester\agent\sessions`、`agent\workspace` 这些**第三层**目录在图上根本没渲染出来，原因是 `PublicDirectoryPanel.vue` 模板里只硬编码了"顶级 v-for + 顶级目录内的 v-for"两层嵌套，第二级 button 没有内嵌的 div 展示其子项。
- What:
  - 新增 `frontend/apps/agent-web/src/components/PublicDirectoryNode.vue`：递归子组件，渲染单行（目录带 chevron + folder，文件不带 chevron），展开时递归调用自身渲染子目录；通过 `defineOptions({ name: "PublicDirectoryNode" })` 显式声明组件名，让 `<script setup>` 模板能自引用；缩进按 `depth * 14` 像素线性递增。
  - 重构 `frontend/apps/agent-web/src/components/PublicDirectoryPanel.vue`：移除硬编码的两层 v-for 嵌套，外层只 v-for 渲染根目录的子项（`entriesByDirectory['']`）的 `PublicDirectoryNode`，其余层级由子组件递归；状态（`entriesByDirectory` / `expandedDirectories` / `loadingPath`）继续由父组件统一管理，子组件只暴露 `toggle` / `openFile` 事件上抛。
- How: 抽出"渲染一行 + 递归子项"为独立组件，状态和事件全部上提到父组件，避免组件树自循环；保留原有的 `isKnownEmptyDirectory` 语义（`entriesByDirectory[path]?.length === 0` 就不渲染 chevron、点击不展开），避免对后端已知为空的目录再发请求；`canWrite` 仍由父组件 `AgentWorkbench` 注入。
- Result: 任意层级的子目录现在都可以正常展开和折叠，缩进按 14px 递增；点击文件行仍走 `openFile` → `readPublicFile` → emit `openFile` payload 给父组件打开 tab；空目录不再发请求，loading 状态按目录路径精确追踪；`PublicDirectoryService` / `PublicDirectoryController` 零改动。
- Pitfalls: Vue 3 `<script setup>` 组件默认没有 name，要在自身模板递归必须 `defineOptions({ name: "PublicDirectoryNode" })` 显式声明（不然 vue-tsc 会报 "Component is missing template or render function"）；递归子组件传 ref 时 Vue 会自动 unwrap，所以 `:entries-by-directory="entriesByDirectory"` 这种写法会直接把 ref 解包成普通对象/Set 给子组件使用，不需要 `.value`；递归 props 必须是 plain data（不能传 ref），否则每个节点会共用同一个 ref，状态会互相串。
- Verification: `pnpm -F @test-agent/agent-web typecheck` 通过；`pnpm -F @test-agent/agent-web test` 通过；后端零改动，未重跑 mvn。
- Next: 后续如果公共目录的目录树深度特别大（>5 层），考虑加虚拟滚动；目前后端 `WorkspaceFileService` 仍然限制单层 1000 条，所以单层节点数过多时也只影响 UI 渲染速度。

### 2026-06-25 - 递归子组件 isKnownEmpty 误把"未加载"当成"空目录"导致无法展开

- Why: 上一次提交用递归子组件替换硬编码两层模板后，用户反馈"公共目录完全没有展开能力了"——根目录的三个子项都显示 chevron 朝右、点击完全没反应，连 `agent` 展开 chevron 的旋转都看不到了。原因是我在子组件 `PublicDirectoryNode` 里把"未加载"和"已加载为空"混为一谈：`children` computed 用了 `?? []` 把 `undefined` 兜底成空数组，初始渲染时 `entriesByDirectory['platform-tester']` 是 `undefined`、被兜底成 `[]`，`isKnownEmpty` computed 判为 true，于是 `<ChevronRight v-if="!isKnownEmpty" />` 不渲染 chevron，且 `onRowClick` 早退，目录永远打不开。
- What: 把 [PublicDirectoryNode.vue](file:///d:/workspace/intelligent-test-agent/frontend/apps/agent-web/src/components/PublicDirectoryNode.vue) 的 `children` computed 改成保留 `undefined`（不兜底），`isKnownEmpty` 严格只在 `Array.isArray(children) && length === 0` 时为 true，template 的 v-for 改用 `children ?? []` 兜底渲染空列表；附上中文注释说明这个边界。
- How: 严格区分"未请求过"和"已请求且为空"两种状态——前者需要渲染 chevron + 允许点击触发请求；后者渲染空白占位 + 点击不展开避免无意义请求。原 `PublicDirectoryPanel.vue` 的 `isKnownEmptyDirectory` 函数用 `entriesByDirectory.value[path]` + `Array.isArray(...)` 天然区分这两种状态，重构时把 `?? []` 当成"防御性编程"反而引入了 bug。
- Result: 任何目录第一次点击都能正常触发 `loadDirectory` 请求并展开；已加载且为空的目录不渲染 chevron、点击不展开，避免重复请求。
- Pitfalls: `?? []` 在某些场景会把"未加载"误判为"空"，是这次踩到的坑；`computed` 的返回类型注解影响 Vue 模板的类型推断，把 `FileTreeEntry[]` 改成 `FileTreeEntry[] | undefined` 后 v-for 的 `?? []` 兜底必须在 template 里手动加，不能依赖 computed 内部。
- Verification: `pnpm -F @test-agent/agent-web typecheck` 通过；`pnpm -F @test-agent/agent-web test` 通过。
- Next: 如果未来要把这个树形组件抽成通用组件（公共目录 + 工作区文件树共用），需要明确 props 的"未加载 vs 空"语义约定，避免类似 bug。

### 2026-06-25 - 整理 Flyway 版本冲突并修复 local-direct 对话启动 500

- Why: 更新代码后本地 115 启动先后被 Flyway migration 冲突挡住：`V15__create_scheduler_framework_tables.sql` 与 `V15__add_opencode_process_id_check_constraints.sql` 重号；随后因已落库的 `V10__seed_fcoss_application.sql` 被源码改成 `V10_1__...` 触发 Flyway validate。服务启动后，对话发送又在 `routing_decisions.execution_node_id=node_ocp_local_direct` 上触发外键失败，因为 local-direct 合成节点没有先写入 `execution_nodes`。
- What:
  - 恢复已落库的 `V10__seed_fcoss_application.sql` 文件名，避免本地和已部署库出现 "applied migration not resolved locally: 10"。
  - 将新调度框架 migration 改为 `V20260625184300__create_scheduler_framework_tables.sql`；约定 V17 及以前为历史连续版本，后续新增脚本统一用 `VyyyyMMddHHmmss__description.sql`，按个人更新时间戳确定版本号。
  - 新增 `FlywayMigrationNamingTest`，校验 migration 版本唯一，并阻止 V17 之后继续新增 V18/V19 这类顺序号。
  - `RunApplicationService.userProcessTarget` 在保存路由决策和 agent session binding 前，先 upsert 用户进程投影出的兼容 `ExecutionNode`，避免 local-direct 合成节点触发外键失败。
  - 同步更新 persistence/runtime README、`docs/deployment/database.md` 和 `docs/standards/backend.md`。
- How: 保留已应用 migration 的版本号不改名，只整理未成功作为稳定历史依赖的新脚本；运行时修复不改变 `UserOpencodeProcessAssignmentService` 的 local-direct 短路语义，仍由 Run 启动阶段承担需要持久化审计/binding 前的兼容节点 upsert。
- Result: 115 地址重启成功，登录 CORS 预检返回 `Access-Control-Allow-Origin: http://192.168.100.115:3000`；curl 发送对话返回 200，创建 `run_30c7621908934017b8686f38a6f44ebd` 且状态为 `RUNNING`，日志只出现 `Run started`，不再有 `DataIntegrityViolationException`。
- Pitfalls: 本地 `POST /api/sessions` 当前 DTO 要求 `title` 非空，curl 复测时需要带 `title`；`test-agent.opencode.local-direct=true` 下 `status/initialize/requireReadyProcess` 仍不写 topology，但后续 Run 审计表和 binding 表有外键，不能跳过兼容 `ExecutionNode`。
- Verification: `mvn -pl test-agent-opencode-runtime -am test -Dtest=RunApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false` 19/19 通过；`mvn -pl test-agent-persistence -am clean test -Dtest=FlywayMigrationNamingTest,JdbcRepositoryIntegrationTest#scheduledTaskRepositoryPersistsDefinitionsPlansAndRunRecords -Dsurefire.failIfNoSpecifiedTests=false` 2/2 通过；`./restart-dev-services.sh --profile guo --env-file .env.local --skip-frontend-build` 构建并重启成功；curl 健康检查、CORS 预检、登录、创建 session、发送对话链路通过。
- Next: 后续多人新增 Flyway 脚本时直接用当前时间戳版本，不要再把已落库的历史 migration 改名；前端 `AgentWorkbench.vue` 已通过 `api.createSession(workspaceId, title)` 传标题，curl/脚本直调 `/api/sessions` 时也要带 `title`。

### 2026-06-26 - 应用设置页统一"工作空间管理"与版本库关联模式文案

- Why: 设置弹窗里的左侧入口、面板标题和"应用与版本库关联"tab 仍保留"应用与工作区"/旧关联模式标题，用户要求统一成"应用与工作空间管理"，并把两个关联模式表达为"按应用关联版本库"与"按版本库管理应用"。
- What: 前端设置入口和面板标题改为"应用与工作空间管理"；版本库关联 tab 的第一个模式标题后追加当前选中应用徽标，两个模式之间增加 `role="separator"` 分隔线；同步更新 agent-web 单元测试、相关 Playwright 断言、`frontend/README.md` 与 `frontend/apps/agent-web/README.md`。
- How: 复用 `SettingsAppWorkspacePanel.vue` 已有 `selectedApp` computed，不新增接口或状态；只在关联 tab 内增加标题行、应用徽标和分隔线样式，避免影响版本库管理/工作空间管理 tab。
- Result: 浏览器验证中设置导航和面板标题均显示"应用与工作空间管理"；切到"应用与版本库关联"后，页面展示"按应用关联版本库" + `F-COSS`、中间分隔线、"按版本库管理应用"。
- Pitfalls: 精确跑 Playwright 子集时不要用 `corepack pnpm e2e -- ... -g ...`，这里会把参数转成整份 `workbench.spec.ts` 运行；应使用 `corepack pnpm exec playwright test apps/agent-web/tests/workbench.spec.ts --grep "..." --project=chromium`。整份 `workbench.spec.ts` 当前仍有与本次设置页无关的工作区/模型/运行流失败。
- Verification: 先写失败测试并确认旧文案导致失败；随后 `corepack pnpm test apps/agent-web/tests/settings-app-workspace-panel.test.ts apps/agent-web/tests/runtime-management-settings.test.ts` 9/9 通过；`corepack pnpm --filter @test-agent/agent-web typecheck` 通过；`corepack pnpm --filter @test-agent/agent-web build` 通过（保留既有 chunk size 警告）；精确筛选的 2 条设置 E2E 通过。

### 2026-06-26 - 版本库管理 tab 前置并移除反向应用关联区

- Why: 用户进一步要求删除"应用与版本库关联"页里的"按版本库管理应用"区块和分隔栏，把"版本库管理"移动到第二个 tab，并补齐版本库管理表单标签与编辑取消按钮。
- What: `SettingsAppWorkspacePanel.vue` 中 tab 顺序调整为"应用人员管理 / 版本库管理 / 应用与版本库关联 / 工作空间管理"；删除 `selectedRepositoryForApps`、`repositoryApplications`、`linkAppId` 及对应的加载/关联/解绑逻辑；版本库编辑行新增"版本库名称"标签和"取消"按钮；新增版本库表单新增"版本库地址"/"版本库名称"标签，名称输入单独换行；同步 agent-web README 和包级说明。
- How: 保留"按应用关联版本库"主流程和"添加版本库"跳转版本库管理的入口；取消编辑只清空编辑态，不触发后端；新增表单用两行 flex 布局维持紧凑。
- Result: 浏览器验证显示"版本库管理"位于第二个 tab；版本库管理页新增表单两行展示，编辑态有取消按钮；关联页只保留"按应用关联版本库"和当前应用徽标，不再展示分隔栏、"按版本库管理应用"、应用 ID 或"关联应用"。
- Verification: 先写失败测试并确认旧顺序/旧表单失败；随后 `corepack pnpm test apps/agent-web/tests/settings-app-workspace-panel.test.ts` 5/5 通过；`corepack pnpm --filter @test-agent/agent-web typecheck` 通过；`corepack pnpm --filter @test-agent/agent-web build` 通过（保留既有 chunk size 警告）；浏览器实际页面验证通过。

### 2026-06-26 - 设置页危险操作改为页面内确认

- Why: 用户在设置页标注"应用与版本库关联"里的"解除"按钮和"应用人员管理"成员移除按钮，要求点击前增加二次确认，避免误删成员或误解除版本库关联。
- What: `SettingsAppWorkspacePanel.vue` 新增页面内 div 确认框状态，替代浏览器原生 `window.confirm`；成员删除图标按钮补 `aria-label="移除成员"`；测试覆盖取消确认不调用后端、确认后才调用后端；README/PACKAGE 同步破坏性操作确认约束。
- How: 保持原有 API 与按钮布局不变，把模板事件从传 id 改为传完整对象，用对象上的 username/name 生成确认文案；确认取消关闭确认框，确认后复用原有 backend-api 调用和列表刷新。
- Result: 点击"解除"或成员移除按钮时会在页面内弹出确认框，不再触发浏览器模态框；取消不会调用解绑/移除接口，确认后才执行。
- Verification: 先写页面内确认框断言并确认旧 `window.confirm` 实现失败；随后 `corepack pnpm test apps/agent-web/tests/settings-app-workspace-panel.test.ts` 7/7 通过；`corepack pnpm --filter @test-agent/agent-web typecheck` 通过；`corepack pnpm --filter @test-agent/agent-web build` 通过（保留既有 chunk size 警告）；浏览器实测两个入口均弹出页面内 dialog，`getJsDialog()` 未返回浏览器原生确认框。

### 2026-06-26 - 工作空间创建表单改为三步式

- Why: 用户标注"工作空间管理"里的创建工作空间区域，要求所有输入项都有标签，"加载分支"改为"刷新分支"，并明确呈现刷新分支、加载目录、创建工作空间三步操作。
- What: `SettingsAppWorkspacePanel.vue` 将创建工作空间表单改为三条步骤行，补齐已关联版本库、分支、目录、工作空间名称可见标签；按钮文案改为"刷新分支"；测试和 README/PACKAGE 同步三步式约束。
- How: 保持原有 `loadBranches`、`loadDirectories`、`createWorkspace` API 调用不变，只调整模板结构和局部 CSS，用编号圆点和步骤标题表达操作顺序。
- Result: 浏览器实测创建区展示 3 个步骤，四个输入标签均可见，旧"加载分支"文案不再出现。
- Verification: 先写三步/标签/文案断言并确认旧实现失败；随后 `corepack pnpm test apps/agent-web/tests/settings-app-workspace-panel.test.ts` 8/8 通过；`corepack pnpm --filter @test-agent/agent-web typecheck` 通过；`corepack pnpm --filter @test-agent/agent-web build` 通过（保留既有 chunk size 警告）；浏览器曾实测三步、标签和旧文案消失，后续仅补响应式宽度约束，尝试复验时浏览器控制超时。

### 2026-06-26 - 多服务器广播与应用版本工作区副本同步

- Why: 应用版本工作区原来只有版本表上的单份 runtime workspace/path，无法保证多台后端服务器上的应用版本文件内容一致；版本创建、个人同步到应用、版本 git pull 也缺少跨节点触发能力。
- What: 新增 domain 广播 envelope/端口与 event 模块 Redis/Noop 实现；`application_workspace_versions` 增加目标 commit，新增 `application_workspace_version_replicas` 记录每服务器副本路径、runtime workspace、当前 commit 和同步状态；`ManagedWorkspaceApplicationService` 改为副本感知，创建/补齐版本、个人同步到应用、版本 git pull 后发布 `workspace.version.sync-requested`，远端节点 clone/fetch/reset 到目标 commit；新增补偿器扫描漏消息；API 新增 `POST /workspace-versions/{versionId}/git-pull`，响应透传目标 commit 和副本状态；前端 `backend-api` 增加 `gitPullWorkspaceVersion` 与可选 DTO 字段。
- How: 广播 payload 只放 version/user/reason/target commit 等安全字段，不放 SSH key/token/文件内容；Redis 消费端用统一 `instanceId` 跳过本实例，业务 handler 再跳过同 `linuxServerId`；Noop 按 `test-agent.server-broadcast.enabled=false/missing` 装配，Redis 按 `enabled=true` 装配，避免两个 publisher bean 同时存在；跨服务器首次创建时当前后端先创建全局版本和本机副本，再广播并短暂等待目标服务器副本 READY 后返回目标 runtime workspace。
- Result: 多机部署开启共享 Redis 和 `test-agent.server-broadcast.enabled=true` 后，应用版本创建/补齐、个人同步、版本 git pull 会触发其他后端同步；漏掉 pub/sub 消息时本机补偿器根据数据库目标 commit 追平；单机或未启用 Redis 时仍记录本机副本并保持兼容。
- Pitfalls: 不要并行跑两个 Maven reactor 写同一模块 `target`，会出现 Surefire `ClassNotFoundException` 误报；`@ConditionalOnMissingBean` 不适合这里的组件扫描 Noop publisher，Redis 开启时可能因扫描顺序生成双 bean，必须用互斥的 `ConditionalOnProperty`；远端 reset 前必须检查工作树干净，失败只标记副本 `FAILED` 并记录脱敏错误。
- Verification: `mvn -pl test-agent-common,test-agent-domain,test-agent-event,test-agent-persistence,test-agent-workspace-management,test-agent-api -am test` 通过；`mvn -pl test-agent-app -am test` 通过；`corepack pnpm test -- backend-api` 120/120 通过；`corepack pnpm --filter @test-agent/backend-api typecheck` 和 `corepack pnpm --filter @test-agent/shared-types typecheck` 通过；`git diff --check` 通过。

### 2026-06-26 - 重启脚本改为前端/后端/opencode-manager 逐个 kill-then-start

- Why: 用户要求 `restart-dev-services.sh` 运行后逐个重启前端、后端、opencode 管理进程（Go），每个先 kill 原进程再启动，并落实 opencode-manager 的启动。原脚本虽有 manager 启停代码，但 `should_start_opencode_manager` 的 auto 分支要求 `TEST_AGENT_OPENCODE_MANAGER_TOKEN` 已设置，用户 `.env.local` 未设置，导致 Go 管理进程从未启动，实际只跑 standalone `opencode serve`。
- What: `restart-dev-services.sh` 在 `load_env_file` 后默认 `TEST_AGENT_OPENCODE_MANAGER_TOKEN=local-manager-token`（与 `application-guo.yml` 一致，不改 `.env.local`）；`should_start_opencode_manager` 的 auto 判定改为「`TEST_AGENT_OPENCODE_BASE_URL` 已设置且 backend_url 为本地」，避免在无 `go` 的校验环境触发 `build_opencode_manager`；新增 `stop_backend_service`/`stop_opencode_manager_service`/`stop_frontend_service` 三个停止辅助函数（manager 步骤额外清理残留 standalone `opencode serve` 防 4096 冲突）；主流程重写为「后端 → opencode-manager → 前端」逐个 kill-then-start，移除 `start_opencode` 调用；更新 usage 文案；同步 `docs/deployment/backend.md`、`frontend/README.md`。
- How: token 默认值让 local/test/guo 三个 profile 的后端 `manager-control.token` 与 manager 自动匹配（guo 硬编码 local-manager-token，local/test 从同一环境变量读取）；per-service 停止复用现有 `stop_pids`/`stop_screen_session`；构建仍前置，失败不动现有服务。
- Result: 脚本运行后按依赖顺序逐个重启三服务，本地默认启动 Go opencode-manager 并由其派生 opencode 子进程，不再单独启动 standalone `opencode serve`。
- Verification: `bash -n`/`sh -n` 通过；`./tools/verify-dev-scripts.sh` 全绿（含两个隔离 env 用例与 sh 重进 bash 断言）。

### 2026-06-26 - 将 SSH key 加密密钥独立到 .key 文件

- Why: SSH key 加密密钥 `test-agent.security.ssh-key-encryption-key` 原先在 `application-guo.yml` 中硬编码，local 等 profile 未配置时抛"SSH key 加密密钥未配置"错误。
- What:
  - 新建 `backend/test-agent-app/src/main/resources/ssh-key.key` 文件（properties 格式），放置 AES-256 加密密钥。
  - `TestAgentApplication.java` 添加 `@PropertySource("classpath:ssh-key.key")`，Spring 自动加载到 Environment。
  - 删除 `application-guo.yml` 中冗余的 `ssh-key-encryption-key` 配置行。
  - 三个 `@Value` 注入点（`SshKeyEncryptionService`、`AgentConfigApplicationService`、`ManagedWorkspaceApplicationService`）零改动。
  - `*.key` 已在 `.gitignore` 中，密钥文件不提交仓库。
- How: properties 格式 `.key` 文件，`@PropertySource` 自动解析；env var `TEST_AGENT_SSH_KEY_ENCRYPTION_KEY` 优先级高于 `.key` 文件，生产仍可用 env 覆盖。
- Result: 编译通过，`SshKeyEncryptionServiceTest` 和 `SshKeyCryptoServiceTest` 全部通过；密钥统一由 `.key` 文件承载，后续其他密钥也按此模式加入。

### 2026-06-26 - SSH key 改为前端混合加密（RSA + AES-256-GCM）

- Why: 原 SSH 私钥明文从前端传输到后端再 AES 加密存储，静态 AES 密钥泄露即可解密全库；用户要求前端密文传输、并改非对称方案。最终定为混合加密：前端 AES 加密私钥、RSA 加密临时 AES 密钥，后端 RSA 解密。
- What:
  - 新增 `test-agent-common` 的 `RsaKeyService`（加载 `classpath:rsa-private.key` PEM，缺失自动生成临时密钥）和重写 `SshKeyEncryptionService`（RSA 解密 AES 密钥 + AES-GCM 解密私钥 + 指纹校验），二者为纯 Java 类，由 `test-agent-app` 的 `SshKeyConfig` 装配 `@Bean`。
  - `UserSshKey` 新增 `encryptedAesKey` 字段；Flyway `V10` 给 `user_ssh_keys` 加 `encrypted_aes_key` 列；JDBC Repository 同步列。
  - `ConfigurationManagementApplicationService.addSshKey` 改为接受前端密文 payload 并 `decryptAndVerify` 校验；`privateKeyFor`/`decryptSingleSshKey` 改混合解密，旧记录（`encryptedAesKey` 为 null）友好报错提示重新添加。
  - `ManagedWorkspaceApplicationService`/`AgentConfigApplicationService` 的 `SshKeyCryptoService` 字段改为 `SshKeyEncryptionService`，移除 `@Value` 静态 AES 密钥注入。
  - API 新增 `GET /ssh-key/public-key`（免鉴权返回 RSA 公钥 SPKI Base64）；`AddSshKeyRequest` 改为 `name/encryptedPrivateKey/encryptedAesKey/encryptionNonce/fingerprint`。
  - 前端新增 `utils/ssh-crypto.ts`（Web Crypto API 混合加密，指纹用 url-safe base64 no-padding 与后端对齐）；`SettingsPersonalPanel.vue` 提交前先取公钥再加密；`backend-api` 加 `getSshKeyPublicKey`；`shared-types` 更新 `AddSshKeyPayload` 并新增 `SshKeyPublicKeyResponse`。
  - 删除旧 `ssh-key.key`（AES 密钥）和 `@PropertySource`，新增 `rsa-private.key`（PEM，force-add 入库）。
- How: RSA-2048 + OAEP/SHA-256（只加密 32 字节 AES 密钥，无大小限制问题）；AES 密钥每次前端随机生成、不落服务端配置，只以 RSA 加密形态存库；Web Crypto AES-GCM 输出（密文+tag）与 Java GCM doFinal 期望格式一致。`test-agent-common` 无 SLF4J 编译依赖，RsaKeyService 用 `java.util.logging`。
- Result: 后端 4 个相关模块测试全绿（含新增公钥端点、混合解密、指纹校验、addSshKey 存储验证用例），前端 `backend-api` 25 测试全绿，`agent-web` typecheck 通过。旧 SSH key 记录需用户重新添加。
- Pitfalls: `SshKeyEncryptionService` 原在 configuration-management 模块，workspace-management 不依赖该模块无法引用；移到 `test-agent-common` 作纯 Java 类 + app 模块 `@Bean` 装配解决。指纹格式后端用 `getUrlEncoder().withoutPadding()`，前端必须对应转 url-safe 去填充，否则校验失败。

- Why: 收起态的小绿点原本实色范围过大，视觉上"实心"占比偏高且位置固定在右下角，用户希望实心范围更小、并支持拖动到任意位置。
- What: `frontend/apps/agent-web/src/components/FigmaChatPanel.vue` 中 `.figma-chat-process-dot` 的 `radial-gradient` 第二段 stop 由 `55%` 提前到 `25%`（is-ready / is-blocking 同步），实心向边缘过渡更早，中间高亮区域显著缩小；`.figma-chat-process-dot` 由 `position: relative` + flex 容器定位改为 `position: fixed`，位置通过 CSS 变量 `--figma-process-dot-x/y` 经 `transform: translate3d` 承载（避免与 `:hover` 的 `scale(1.15)` 互相覆盖），`cursor: grab / grabbing`，新增 `is-dragging` 状态；模板绑定 `:style="processStatusDotStyle"`、`@pointerdown="onProcessStatusDotPointerDown"`、`@click="handleProcessStatusDotClick"`（点击和拖动通过 4px 阈值区分，拖动产生的 click 不会触发 toggle）；script 新增 `processStatusDotPos` 状态、`loadProcessDotPos`/`saveProcessDotPos` 持久化到 `localStorage('figma-chat-process-dot-pos')`、`clampProcessDotPos` 边界裁剪、`onProcessStatusDotResize` 窗口变化时夹紧；`onMounted` 读位置、注册 resize 监听，`onBeforeUnmount` 解绑 pointer/resize 监听。
- How: 仅在 `FigmaChatPanel.vue` 内单文件改动，不动 store/props/emit；展开态面板 `figma-chat-process-status` 不受拖动逻辑影响，行为保持原样。
- Result: 收起态圆点实心范围明显收窄（虚化晕圈占比更大），鼠标可拖动到视口任意位置，刷新后位置保留；普通点击仍展开为状态卡片，拖动距离 > 4px 不会误触发 toggle。
- Verification: `corepack pnpm --filter @test-agent/agent-web typecheck` 未引入新错误（既有 pre-existing `ChatMessage`/`runtime-reducer` 报错与本次无关）；浏览器实测点开 → 展开为「opencode 进程可用 http://192.168.100.115:4096」流程正常。

### 2026-06-26 - 修复 agent-web workspace 类型解析失败

- Why: `corepack pnpm --filter @test-agent/agent-web build` 在 `workbench-shell/src/workbenchStore.ts` 报 `Cannot find module '@test-agent/shared-types'`；排查发现本地 `workbench-shell/node_modules/@test-agent/shared-types` 链接缺失，同时 `agent-web` 继承的 `baseUrl` 是 `frontend`，但 app tsconfig 把 `@test-agent/*` 写成了 app 相对路径。
- What: 运行 `corepack pnpm install --frozen-lockfile` 补齐本地 workspace 链接；将 `frontend/apps/agent-web/tsconfig.json` 的 `@/*` 和 `@test-agent/*` paths 改为以继承后的 `frontend` baseUrl 为基准，分别指向 `apps/agent-web/src/*` 与 `packages/*/src`。
- How: 先复现原始 TS2307，再检查 `vue-tsc --showConfig`、package lock 和 package-local `node_modules`，确认解析链路后只改 tsconfig alias，不改 Vite alias、不新增依赖。
- Result: `@test-agent/shared-types` 在 `agent-web` 类型检查中稳定解析到 `frontend/packages/shared-types/src`；`@test-agent/agent-web` build、`@test-agent/shared-types` typecheck、`@test-agent/workbench-shell` typecheck 均通过。

### 2026-06-26 - 优化设置页创建工作空间区域的视觉样式

- Why: 设置页"工作空间管理"下的"创建工作空间"区域原来是三个散乱的、带边框的卡片，并且当输入标签存在时，输入框和右侧的动作按钮没有底齐，导致视觉上严重错位，整体不够美观。用户后续提出希望去掉“第一步/第二步/第三步”文字前缀并使高度更加紧凑。
- What:
  - `SettingsAppWorkspacePanel.vue` 中将三个步骤项改造成统一的卡片布局，左侧以一条纵向时间线 (Timeline) 贯穿 3 个步骤圆形数字徽标。
  - 为步骤卡片引入了 `:class` 状态绑定，能够基于当前填写的状态自动呈现已完成 (is-completed)、进行中 (is-active)、已禁用 (is-disabled) 三种视觉状态。
  - 重写了 steps 的 CSS，将 controls 设为 `align-items: flex-end`，从而保证无论标签如何折行，输入/选择框都会和右侧的执行动作按钮底端对齐；同时给运行中的进度圆点加入了呼吸灯动画 (`ta-progress-pulse`)。
  - 在 script 中增加了对 `workspaceRepositoryId` 和 `workspaceBranch` 的 watcher，当用户更改上游版本库或分支时，能自动清空下游已选值及列表，防止出现脏数据和不一致状态。
  - 为 steps 引入了 `ta-workspace-step-inputs` 包装容器，显式设置 label 的固定宽度（320px/240px/180px/140px）并且让 element 控件宽度 100%，消除因为 inline-flex 宽度计算导致的下拉框坍缩现象。
  - 在 controls 容器上使用 `justify-content: space-between` 和 `width: 100%`，把动作按钮推到最右侧，实现动作按钮在最右端纵向对齐的布局。
  - 在 `ta-workspace-step-heading` 样式上添加了 `white-space: nowrap`，防止步骤标题文字产生意外折行。
  - 去掉了步骤标题中的“第一步：”、“第二步：”、“第三步：”前缀文字。
  - 将 steps 容器的上下 padding 从 `20px` 压缩为 `12px`；第一列网格列宽由 `180px` 缩窄为 `140px`，并将引导线的 `top`/`bottom` 位置相应调整为 `32px`，从而使整体界面高度更加紧凑。
  - 同步修改了 `settings-app-workspace-panel.test.ts`，去掉了步骤断言的前缀；并使用 `getAllByText` 和 `.find(el => el.tagName === "BUTTON")` 等更加精准的 DOM 筛选逻辑以消除文字重复带来的获取歧义。
- How: 仅在 `SettingsAppWorkspacePanel.vue` 和 `settings-app-workspace-panel.test.ts` 中修改，不改变任何已有的功能 API，确保完全向下兼容。
- Result: "创建工作空间"区域改为了精致的纵向时间线步骤设计。步骤标题不再有“第X步：”前缀，高度和列宽显著收窄，整体布局紧凑而精美。所有的输入控件合理加宽，右侧按钮对齐。
- Verification: 运行 `corepack pnpm test apps/agent-web/tests/settings-app-workspace-panel.test.ts` 11/11 全部通过；运行 `corepack pnpm --filter @test-agent/agent-web typecheck` 通过；没有破坏任何既有的 test断言或结构。

### 2026-06-26 - 通用参数管理参数值修改改为弹窗修改

- Why: 通用参数管理页面中的参数值原为表格行内 input 框输入，容易产生误触且对长路径参数的展示和编辑不够友好，用户要求点击参数值后弹出 DIV (Dialog) 进行修改。
- What:
  - `GeneralParamManagementPanel.vue` 中移除表格行内 `el-input`，改为带 Code 样式的可点击药丸组件 `.ta-common-param-val-cell`，当 hover 时变蓝并显示“点击修改”提示。
  - 在 script 中移除与行内草稿相关的 `valueDrafts`、行内 dirty 检查、行内 reset 及行内 saveValue 函数，删除 rows 数据变化时的 watcher 监听。
  - 引入了 Element Plus 的 `el-dialog` 编辑框，放置在模板底部；当点击参数值或“编辑”按钮时，触发 `openEditDialog(param)` 在弹窗内显示参数的英文名、中文名、适用平台和可拉伸的多行 textarea 输入框。
  - 在 Dialog footer 中放置“取消”和“保存”按钮，并通过 `isDialogValueDirty` 属性控制保存按钮的禁用状态，保存成功后自动 invalid 缓存刷新数据并关闭 Dialog。
  - 在 table 中把操作栏的“保存”和“重置”按钮替换为了单个“编辑”按钮，统一点开编辑弹窗的入口。
- How: 仅修改 `GeneralParamManagementPanel.vue` 单一文件，移除已废弃的行内编辑逻辑，不改动任何后端 DTO 或 HTTP 接口契约，完全向下兼容。
- Result: 通用参数列表不再直接暴露 input 框，改为了精美的只读气泡形态。点击参数气泡或右侧“编辑”按钮即可弹出系统级 Dialog，提供多行宽敞的文本域编辑路径参数，修改体验更加高级和安全。
- Verification: 运行 `corepack pnpm test apps/agent-web/tests/settings-app-workspace-panel.test.ts` 11/11 通过。因本地工作区其他开发者引入了未提交的 SSH 秘钥加密变动导致 `SettingsPersonalPanel.vue` 报错，排查确认本组件 `GeneralParamManagementPanel.vue` 自身无任何 TS 类型错误。

### 2026-06-26 - 明确禁止 Flyway 发布测试数据

- Why: 需要防止测试、演示、个人开发或环境专属数据通过 Flyway migration 随生产结构迁移一起发布，避免污染共享库和线上库。
- What: 在 `AGENTS.md`、后端规范、数据库部署文档、自检清单以及 `test-agent-persistence` README/PACKAGE 说明中新增规则：Flyway 仅承载结构变更、历史数据兼容迁移和生产必需基础字典/系统参数；测试数据放测试 fixture、`test-agent-test-support`、mock 数据、显式本地开发脚本或人工初始化流程。
- How: 仅修改稳定文档，不触碰当前工作区已有后端代码、配置和未提交 migration；同时整理 `AGENTS.md` 强制规则编号，删除重复的 session-log 规则副本。
- Result: 后续新增 migration 时，入口规范、后端规范、数据库文档、包级说明和提交前自检都会阻止把测试数据带入 Flyway。

### 2026-06-26 - 清理 V17 本地 loopback opencode 种子

- Why: V17 曾写入 `127.0.0.1` 本地 opencode 拓扑和默认用户绑定，后端改为真实 IP/心跳注册与 `local-direct` 后，这批数据会变成运行管理里的历史脏数据。
- What: 保留 `V17__seed_local_opencode_machine_for_default_user.sql` 作为 Flyway 历史文件，新增 `V20260627000000__cleanup_loopback_linux_server_seed.sql` 清理 `linux_servers`、backend/opencode 进程拓扑、用户绑定和 manager-backend 连接中 `linux_server_id='127.0.0.1'` 的历史数据；同步持久化 README、PACKAGE 和数据库部署文档。
- How: 集成测试从完整迁移链断言 V17 loopback 种子最终不存在，并从 `target("17")` 的历史状态补一条本地 backend connection 后跑全量迁移，验证清理脚本按外键顺序删干净。
- Result: V17 文件不直接改动，避免已应用历史库 Flyway validate 失败；`JdbcRepositoryIntegrationTest` 全部通过。`FlywayMigrationNamingTest` 仍被既有 `V18__create_scheduler_framework_tables.sql` 阻断，需后续单独处理该历史命名问题。

### 2026-06-26 - 修复 Flyway V10/V13 历史迁移校验失败

- Why: 启动时报 `Migration checksum mismatch for migration version 10` 和 `applied migration not resolved locally: 13`，根因是 `V10__seed_fcoss_application.sql` / `V13__seed_fcoss_more_workspaces.sql` 被从工作区移除，同时 SSH key 的 `encrypted_aes_key` schema 变更错误复用了已落库的 V10 版本。
- What: 恢复 V10/V13 历史 seed migration 的解析；删除 `V10__add_encrypted_aes_key_to_user_ssh_keys.sql`，改为 `V20260627010000__add_encrypted_aes_key_to_user_ssh_keys.sql`；`FlywayMigrationNamingTest` 增加历史 seed 文件必须存在、V10 不得复用的断言，并把已发布的 V18 作为历史例外保留。
- How: 先运行新增测试确认当前坏状态会失败；再恢复历史迁移、移动 SSH key 列变更到时间戳 migration，并用 `mvn -pl test-agent-persistence clean test -Dtest=JdbcRepositoryIntegrationTest,FlywayMigrationNamingTest -Dsurefire.failIfNoSpecifiedTests=false` 验证完整迁移链。
- Result: 持久化模块 26 个目标测试通过；后续已落库 migration 禁止删除、重命名、改写或复用版本号，schema 变更必须走新的时间戳 migration。

### 2026-06-26 - 补强 V17 loopback 清理的外键顺序

- Why: 用户数据库执行 `V20260627000000__cleanup_loopback_linux_server_seed.sql` 时，删除 `opencode_containers` 被 `opencode_server_processes.container_id` 外键阻塞；根因是历史库存在进程自身 `linux_server_id` 不是 `127.0.0.1`、但 `container_id` 仍指向 V17 loopback container 的脏行。
- What: 清理脚本删除 user binding、opencode server process、container manager 和 manager-backend connection 时，同时按 `linux_server_id='127.0.0.1'` 与引用 loopback container 两条路径定位待删数据。
- How: 在 `JdbcRepositoryIntegrationTest#cleanupMigrationRemovesHistoricalLoopbackTopology` 中插入“非 loopback server 进程引用 loopback container”的历史脏数据，先确认原脚本外键失败，再补齐删除条件。
- Result: 定向迁移用例通过；后续写历史拓扑清理时不能只看子表自己的 `linux_server_id`，还要沿外键反查父级 loopback 资源。

### 2026-06-26 - 运行心跳改为 Redis 快照并移除 manager HTTP 发现路径

- Why: Java 后端和 Go manager 的在线状态不应继续写入或依赖数据库 heartbeat 字段；Go manager 需要在所有 Java 连接断开后通过 `.serverip + backend port` 持续重连，并且控制面交互只能走 WebSocket。
- What: 新增 Java backend/manager Redis 运行快照，TTL 10 秒，Java 与 manager 心跳间隔改为 5 秒；运行管理、manager 后端列表响应和 Workspace 文件后端路由改读 Redis 快照。Go manager 删除 HTTP discovery client，启动派生 seed WebSocket，断线后每 10 秒无限重连，有连接时每 10 秒通过 `backendListRequest` 补连缺失 Java 后端，每 5 秒通过任一 socket 发送 `managerHeartbeat`。
- How: WebSocket 协议新增 `managerHeartbeat`、`backendListRequest`、`backendListResponse`；Redis store 保存 JSON 快照并新增 `jackson-datatype-jsr310` 依赖支持 `Instant`；本地脚本不再注入 HTTP discovery URL，文档同步 Redis 强依赖、WebSocket-only 控制面、5 秒心跳和 10 秒 TTL。
- Result: Go 全量测试、脚本校验、Redis/运行管理/manager WebSocket 聚焦 Maven 测试通过；完整 Maven 目标集合仍只失败于既有 3 个 guo profile 配置断言（session log 已记录），与本次 Redis/WebSocket 改动无关。

### 2026-06-27 - 运行管理新增 Redis 指标历史和趋势图

- Why: 超级管理员运行管理需要查看容器和 Java 后端的最新资源指标，并在点击行后追踪近 48 小时 CPU、内存、磁盘 IO、进程容量和 JVM 指标趋势；在线态仍由 Redis latest snapshot TTL 决定。
- What: 扩展 managerHeartbeat 和 Java backend heartbeat，latest snapshot 保持 10 秒 TTL，同时向 Redis ZSET 写入近 48 小时原始 5 秒指标样本；新增容器/后端 metrics history HTTP API，运行管理 overview 增加最新指标字段，前端运行管理按需加载 ECharts 展示趋势。
- How: Go manager 使用 Linux cgroup v2/v1 和 procfs 只读采集容器 CPU、内存、磁盘 IO，并把本地 opencode 进程明细随 latest snapshot 上报；Java 后端通过 JDK MXBean 和当前工作目录文件系统采集服务器/JVM 指标；history API 对超过 `maxPoints` 的样本按时间桶降采样。文档同步 API、事件边界、部署说明、backend/frontend README 和 module map。
- Result: `go test ./...`、后端指定 Maven reactor、运行管理相关前端测试和 `corepack pnpm typecheck` 通过；`corepack pnpm test` 仍失败于既有 `apps/agent-web/tests/FigmaChatPanel.test.ts` 两个历史消息渲染断言，和本次运行管理改动无关。

### 2026-06-27 - Redis disabled 时跳过后端心跳 runner

- Why: `test-agent.redis.enabled=false` 或 prod 默认未显式启用 Redis 时，`BackendJavaProcessLifecycleRunner` 启动阶段无条件调用 `registerHeartbeat()`，触发 `Redis 运行心跳未启用` 并中断整个 Spring Boot 启动；这与 Redis optional health 的“应用可启动、运行管理/manager 链路 fail fast”边界不一致。
- What: `BackendJavaProcessLifecycleService` 暴露 `heartbeatEnabled()`，app runner 在 Redis 心跳未启用时跳过 `.serverip` 写入、Java backend snapshot 注册和周期调度；只有成功启动生命周期后，销毁阶段才标记本 backend offline。
- How: 新增 `OpencodeManagerControlConfigTest` 覆盖 disabled heartbeat store 下 runner 不抛错、不写 `.serverip`、不注册心跳、不 mark offline；同步 app README 和部署文档说明 Redis disabled 语义。
- Result: 聚焦 Maven 回归和后端目标集合通过；生产启用用户进程模型仍需设置 `TEST_AGENT_REDIS_ENABLED=true`，否则运行管理与 manager 控制链路保持 fail fast。

### 2026-06-27 - 移除 Redis 启用开关和内存降级

- Why: Redis 已被明确为系统必需依赖，继续保留 `test-agent.redis.enabled` / `TEST_AGENT_REDIS_ENABLED` 会制造“可关闭 Redis”的错误心智，并导致启动路径出现旧的未启用判断。
- What: 删除 Redis 启用开关配置、运行心跳 `enabled()` 端口、Noop 心跳存储和内存 TokenStore fallback；Java backend 生命周期、运行管理、manager socket、用户进程分配、workspace 路由和 scheduler 均默认依赖 Redis Bean，不再检查“Redis 未启用”分支。
- How: 将 Redis heartbeat store、TokenStore 和 health indicator 改为必需 Redis 实现；`SchedulerStartupValidator` 只校验启用 scheduler 时存在 `StringRedisTemplate`；配置绑定和文档同步移除开关说明，测试改为验证即使传入旧开关也不会改变 Redis 必需行为。
- Result: Redis 不再有独立启用参数；旧的 `Redis disabled 时跳过后端心跳 runner` 决策已被本次变更取代。聚焦测试通过；完整 Maven 回归仍被工作区未跟踪 migration `V20260627010000__seed_opencode_manager_max_processes_param.sql` 与已存在 migration 版本重复阻塞，未纳入本次提交。

### 2026-06-27 - 修复 manager 最大进程数参数 migration 版本冲突

- Why: app fat jar 启动时报 Flyway `Found more than one migration with version 20260627010000`，根因是 opencode-manager 最大进程数参数 seed 曾使用与 SSH key `encrypted_aes_key` schema migration 相同的时间戳版本，旧打包产物里同时包含两份 `20260627010000` migration。
- What: 将 manager 最大进程数系统参数 migration 固化为 `V20260627020000__seed_opencode_manager_max_processes_param.sql`，初始化 `common_parameters.OPENCODE_MANAGER_MAX_PROCESSES=8/all`；同步 persistence README/PACKAGE 和数据库部署文档。
- How: 运行 Flyway 命名测试和 H2 迁移集成测试验证版本唯一，再重新打包 `test-agent-app`，检查嵌套 `test-agent-persistence` jar 只包含 `V20260627010000__add_encrypted_aes_key_to_user_ssh_keys.sql` 与 `V20260627020000__seed_opencode_manager_max_processes_param.sql`。
- Result: 重复版本错误消除；后续若修改该默认值，应通过通用参数管理或新的兼容 migration，不得改写已发布文件。

### 2026-06-27 - 修复运行管理趋势图 UTC 时间直显

- Why: 运行管理趋势图 X 轴直接截取后端 `Instant` ISO 字符串，导致 UTC `2026-06-26T17:28:00Z` 在东八区页面显示为 `06-26T17:28`，与列表心跳的本地时间显示不一致。
- What: 抽出图表采样时间格式化函数，先解析时间再按浏览器本地时区显示为 `MM/DD HH:mm`，非法时间显示 `-`。
- How: `RuntimeMetricChart.vue` 改用统一格式化函数，前端运行管理测试覆盖 `2026-06-26T17:28:00Z` 显示为 `06/27 01:28`。
- Result: 运行管理趋势图时间轴与列表心跳时间使用同一本地时区语义；不改后端 API、Redis 样本或历史查询范围。

### 2026-06-27 - 运行管理指标历史改为分钟级窗口并支持48小时自定义

- Why: 原 `hours` 参数只能表达整数小时且最小 1 小时，无法支持运行管理趋势图的 1 分钟、30 分钟短窗口，同时用户也需要能自主调整/查看最大 48 小时（2880 分钟）的历史图表。
- What:
  - 指标 history API 新增 `windowMinutes` 主参数，允许 `1/30/60/360/720/1440/2880`，默认 60 分钟；旧 `hours` 保留兼容但前端不再使用。
  - 前端 `RuntimeManagementPanel.vue` 在 radio-group 按钮组中新增 `48小时` (value = 2880) 选项。
  - 后端 `RuntimeManagementController.java` 中的 `ALLOWED_METRIC_WINDOW_MINUTES` 集合追加 `2880`。
  - 单元测试与 API 文档同步更新。
- How: 在后端校验白名单和前端 UI 按钮中同步加入 2880 分钟，调整测试断言值，并更新 HTTP API 文档。
- Result: 趋势图支持 1分钟/30分钟/1小时/6小时/12小时/24小时/48小时 自定义切换，单元测试及编译通过。

### 2026-06-27 - 左右侧边栏收起与展开渐进式动画优化

- Why: 提升侧边栏折叠与展开的交互流畅度和视觉质感，当用户点击折叠/展开左右侧栏按钮时，侧面板应平滑过渡，而不是瞬间消失或跳变。
- What: 修改 `frontend/apps/agent-web/src/components/FigmaShell.vue`：
  - 定义 `leftPanelStyle` 与 `rightPanelStyle` 响应式计算样式，控制宽度从设定的宽度到 `0px`，不透明度从 `1` 到 `0`，指针事件从 `auto` 到 `none`。
  - 在模板中分别移除 `.figma-panel-left` 和 `.figma-chat-panel-wrapper` 的 `v-if` 条件渲染限制（但保留内部 resize handle 的 `v-if`），改由计算样式动态驱动宽度和显示。
  - 为面板及容器增加 `.is-resizing` 类，在用户处于手动拖拽调整宽度期间屏蔽 CSS 过渡动画（`transition: none !important`），避免拖拽滞后。
  - 在 CSS 中为 `.figma-panel-left`、`.figma-chat-panel-wrapper` 与左侧浮动按钮 `.figma-sidebar-toggle-floating` 配置 `0.25s` 的渐进式过渡动画。
- How: 用 CSS transition 替换硬性的 Vue DOM 节点移除/插入逻辑，辅以 active resizing 变量进行 class 动态控制来实现丝滑的过渡与零延迟的手工拖拽。
- Result: 左右侧边栏在折叠和展开发生时，均呈现出完美的 0.25s 渐变动画效果，且不影响手动拖拽的流畅度；前端类型校验和单元测试全部通过。

### 2026-06-27 - 修复 FigmaChatPanel 历史消息单元测试异步渲染失败

- Why: `MarkdownView` 内部用 150ms 定时器 + 动态 import markdown-it/dompurify/hljs 异步渲染正文，`FigmaChatPanel.test.ts` 的两个用例 mount 后同步读取 `wrapper.text()` 做断言时仍停在“渲染中…”占位，导致“历史消息按序渲染”和“空助手消息不渲染”两个用例既有失败。
- What: 在测试文件中新增同步直出 `source` 的 `MarkdownView` 桩，并给这两个 mount 调用加 `global.stubs`，让正文断言与 MarkdownView 渲染时序解耦。
- How: 桩组件只渲染 `<div class="ta-md-view">{{ source }}</div>`，保留 `.figma-chat-assistant` 结构与 meta 行，不影响用例里的元素数量和时间断言。
- Result: 22 个测试文件 / 131 个用例全部通过；不涉及 API、事件、数据库或后端，仅测试文件改动。

### 2026-06-28 - 修复 test 环境 opencode 重启后 503

- Why: 切换 IP/数据库后重启，Go `opencode-manager` 会反复断开 Java 控制面连接并导致 opencode 不可用；同时 `test` profile 的完整 Actuator health 被旧固定 opencode node 探测打成 DOWN。
- What: `ManagerControlMessageCodec` 禁用 Jackson 时间戳序列化，确保 WebSocket 控制面发给 Go manager 的 `Instant` 是 RFC3339 字符串；`OpencodeNodesHealthIndicator` 在 manager/socket 且非 local-direct 模式下跳过 legacy 固定节点探测，只保留该探测给 local-direct/static-token fallback；`JdbcOpencodeProcessManagementRepository` 读取历史用户进程时归一化 `updated_at < created_at` 的脏数据，避免旧记录让 wr 用户状态接口直接 400。
- How: 用定向单测先复现 `lastHeartbeatAt` 非字符串、manager/socket 仍探测 `127.0.0.1:4096`、历史进程时间戳阻断 Repository 映射的坏状态，再修复实现；同步 `test-agent-app`、`test-agent-opencode-runtime`、`test-agent-persistence` README/PACKAGE、数据库和后端规范文档；本地重启技能改为默认 `.env.test` + profile `test`。
- Result: `test` 环境重启后 `/actuator/health` 与 readiness 均为 UP，前端 3000 可访问，manager 日志等待多个发现周期无 `Time.UnmarshalJSON` 或 websocket 断连；wr 用户状态接口可返回 `NEEDS_INITIALIZATION` 并在初始化后恢复 READY。初始化后立即查询仍可能遇到 opencode HTTP服务短暂 warm-up 窗口，最终状态已验证为 READY；相关 Maven reactor 测试通过。

### 2026-06-28 - 分支与目录选择框变更为可输可选、隐藏以点开头的目录，并添加刷新进度条

- Why: 用户要求在「工作空间管理」中创建工作空间时的分支和目录两个选择框支持可输可选（即既可快速搜索过滤，也可直接回车输入自定义路径）。同时，以 `.` 开头的隐藏文件/文件夹默认应该在目录列表中隐藏，只有在用户输入内容进行过滤或主动输入时才可展示。此外，用户希望在刷新分支和加载目录时能显式提供进度反馈（进度条与按钮加载状态）。并且，为彻底解决竖线穿过数字圈圈的问题，直接将竖线移除。
- What:
  - 修改 `frontend/apps/agent-web/src/components/settings/SettingsAppWorkspacePanel.vue`。
  - 为分支选择和目录选择下拉框添加 `filterable`、`allow-create` 与 `default-first-option` 属性，使其支持检索与输入。
  - 新增 `directorySearchQuery` 状态，监听目录下拉框事件，在输入时更新过滤词，关闭时重置。
  - 新增 `filteredDirectories` 计算属性，用于默认过滤以 `.` 开头的路径。
  - 引入 `loadingBranches` 与 `loadingDirectories` 加载状态，在「刷新分支」与「加载目录」异步接口调用期间启用按钮的 `:loading` 状态，并在对应步骤底部渲染一个绝对定位的动画进度条（使用 `el-progress` 不确定进度条模式）。
  - 删除了 `.ta-workspace-create-steps::before` 样式块，彻底移除了步骤背景竖线，解决了竖线穿过数字圈圈的遮挡问题。
  - 在 `settings-app-workspace-panel.test.ts` 中注册 `ElProgress` stub，消除 Vitest 运行时的组件解析警告。
- How: 纯前端代码更新，使用 Element Plus 的 filterable / allow-create / el-progress 配合 Vue computed 过滤并移除背景伪元素竖线来实现。
- Result: 单元测试 `settings-app-workspace-panel.test.ts` 全部通过，`pnpm typecheck` 与 `pnpm lint` 校验通过，界面交互逻辑流畅，无任何未解析组件警告。

### 2026-06-28 - 「应用工作空间」标题栏手动刷新按钮失效修复

- Why: 用户反馈左侧「应用工作空间」标题栏上的循环刷新按钮点击后完全没有反应，文件树不会重新拉取。
- What: 修改 `frontend/apps/agent-web/src/components/AgentWorkbench.vue`：
  - `loadDirectory(path, workspaceId)` 增加 `force = false` 第三参数；早返回守卫改为 `loadingPath.has(path) || (!force && entriesByDirectory[path] !== undefined)`，仅在「非强制刷新」且「已加载过」时短路，正在加载中的请求仍去重避免并发风暴。
  - 模板中 `<FigmaFileExplorer @refresh>` 从 `loadDirectory('')` 改为 `loadDirectory('', undefined, true)`，让用户点击刷新按钮时强制重新拉取根目录。
  - 同步在 `frontend/apps/agent-web/README.md` 第 34 行补充说明手动刷新按钮走 `force=true` 路径，绕过 `loadDirectory` 的去重短路。
- How: 维持原函数签名向后兼容（仅追加默认参数），未改其他 6 处 `loadDirectory` 调用方，避免影响首次加载、工作区切换和目录懒加载的现有去重行为。
- Result: 手动刷新按钮能真正触发 `api.listFiles(workspaceId, '')` 并刷新根目录行；`vue-tsc` typecheck 与 Vitest 132 个测试全部通过。

### 2026-06-28 - 工作树不随 agent 写文件实时刷新的修复

- Why: 用户反馈左侧「应用工作空间」文件树在 agent 调用 `write` / `edit` / `apply_patch` / `str_replace` / `multi_edit` / `create_file` 等写盘工具完成后不立即出现新文件，必须手动点刷新才会更新。
- What: 修改 `frontend/apps/agent-web/src/components/AgentWorkbench.vue`：
  - `refreshParentDirectory` 内的两处 `loadDirectory` 调用补上第三个参数 `force=true`：根目录走 `loadDirectory("", undefined, true)`，子目录父级走 `loadDirectory(parentPath, undefined, true)`。
  - 同步更新该函数上方中文注释，说明"父目录已加载时必须走 force=true，否则会被 loadDirectory 去重短路"以及该设计选择与新增/未加载目录的处理。
  - 同步更新 `frontend/apps/agent-web/README.md` 第 34 行描述，明确 `refreshParentDirectory` 和手动刷新按钮都依赖 `force` 参数。
- How: 复用上一条已经引入的 `force` 形参；保留"父目录未展开过就不预加载"的判断（`entriesByDirectory[parentPath] === undefined` 时直接 return），不主动拉取用户从未展开的目录；`loadDirectory` 内部的 `loadingPath.has(path)` 守卫依旧防止对同一目录的并发请求堆积。
- Result: agent 完成写文件工具后，写入位置所属的父目录会立即重新 `api.listFiles` 一次，文件树即时反映新建/删除；`vue-tsc` typecheck 与 Vitest 132 个测试全部通过。

### 2026-06-28 - card 路径与未展开父目录下的文件树实时刷新二次修复

- Why: 用户反馈上一轮修复后，agent 通过对话新生成的文件仍要按刷新按钮才能看到——差异卡片能更新，但文件树不展开新增文件的祖先目录。
- What: 修改 `frontend/apps/agent-web/src/components/AgentWorkbench.vue`：
  - `scanLiveToolParts` 的 tool card 分支（`message.role === "card" && message.cardType === "tool"`，由 `tool.started` / `tool.finished` 事件生成）原来只在 `liveTrack.value === false` 时调 `refreshParentDirectory(path)`，缺少 `expandPathToFile(path)`；与 assistant message 的 part 分支不一致，导致 card 路径下新文件所在的祖先目录不会被自动展开。补上 `expandPathToFile(path)`，让 card 路径与 assistant 路径行为完全一致。
  - `refreshParentDirectory` 去掉"父目录必须已经缓存（`entriesByDirectory[parentPath] !== undefined`）才重拉"的限制，**始终**走 `loadDirectory(parent, undefined, true)`：父目录未加载时由 `loadDirectory` 直接发起拉取（`force=true` 不会绕过 `loadingPath` 守卫，所以不会与 `expandPathToFile` 已经在飞的请求堆积并发），已加载时则覆盖旧条目。根目录分支同步去掉"`entriesByDirectory[""] !== undefined || expandedDirectories.size > 0`"前置条件，root 总是会被强制重拉一次。
  - 同步更新 `refreshParentDirectory` 上方中文注释，把"展开"和"重拉"的职责拆开（前者归 `expandPathToFile`、后者归 `refreshParentDirectory`），并明确 `loadDirectory` 内部的 `loadingPath` 守卫仍负责并发去重。
  - 同步更新 `frontend/apps/agent-web/README.md` 第 34 行描述，明确两条路径（assistant part / tool card）都会调 `expandPathToFile` + `refreshParentDirectory`。
- How: 没有改 `loadDirectory` 行为，只放宽 `refreshParentDirectory` 的调用条件并在 card 路径补齐 `expandPathToFile`，覆盖"父目录从未被用户展开过"和"工具事件只生成独立 card 消息"两种原本会被跳过的场景。
- Result: 不论 agent 走 assistant message part 还是独立 tool card 事件，新文件的所有祖先目录都会被自动展开并触发 `api.listFiles`，文件树即时反映新增；用户不再需要手动点刷新按钮。`vue-tsc` typecheck 与 Vitest 132 个测试全部通过。

### 2026-06-28 - opencode 旧绑定迁移、端口脏数据避让与本地重启清理

- Why: `test` 环境切换 IP/数据库后，旧用户 binding 会锁在旧 `linux_server_id`，初始化/状态接口可能 503；workspace 文件 WebSocket 也会因历史 workspace 仍绑定旧服务器而间歇失败；本地重启脚本只清理 standalone 4096，没有清理 manager 派生的用户 opencode 子进程和 state。
- What: `UserOpencodeProcessAssignmentService` 在原服务器无健康容器时 fallback 到当前后端全局健康容器并迁移 binding；端口选择改为按 `(linux_server_id, port)` 全局避让所有历史进程行；`WorkspaceFileRoutingService` 在旧服务器无在线后端且本机根目录可访问时安全回绑 workspace；`restart-dev-services.sh` 停止 manager 时清理 state JSON、state pid 和端口池残留 `opencode serve`；新增 `tools/verify-opencode-user-process-scenarios.sh` 插入/清理测试脏数据验证新老用户四类场景。
- How: 用单测覆盖旧 binding fallback、旧 process 缺失、端口脏行避让、workspace 安全回绑与旧服务器在线不回绑；用 `.env.test` 重启后运行场景脚本，验证新用户正常/脏数据、老用户正常/旧 binding + 旧 workspace 均能 READY、runtime health 和 file-ws-route 正常。
- Result: 本地 `test` profile 重启成功，脚本清掉旧 manager 托管进程；四类 opencode 场景通过且脚本测试数据清零。后续排查 workspace 文件树刷新时，要先看 `file-ws-route` 的 workspace/agent `linuxServerId` 是否不一致，再看真实 WebSocket ticket/连接错误。
### 2026-06-27 - 后端 Java 进程内存采集跨平台适配

- Why: 后端 Java 进程获取其所在运行主机的内存使用不准确，macOS 的 `OperatingSystemMXBean.getFreeMemorySize()` 返回值与实际可用内存差异较大（macOS 内存管理机制不同，包含活跃/非活跃/已压缩等概念）。
- What:
  - 新增 `OsType` 枚举检测当前操作系统（MACOS/LINUX/WINDOWS/UNKNOWN）。
  - 新增 `SystemMemoryProvider` 策略接口和 `MemoryInfo` 数据结构。
  - 实现 `MacOsMemoryProvider`：执行 `vm_stat` 命令获取 free + inactive + speculative 内存计算可用内存，失败时降级到 `OperatingSystemMXBean`。
  - 实现 `LinuxMemoryProvider` 和 `WindowsMemoryProvider`：使用 `OperatingSystemMXBean`。
  - 修改 `BackendRuntimeMetricsCollector` 集成策略模式，根据操作系统类型选择对应实现。
  - 新增单元测试覆盖各平台策略和采集器行为。
- How: 使用策略模式封装不同操作系统的内存获取逻辑，macOS 通过解析 `vm_stat` 命令输出获取准确的可用内存（= free + inactive + speculative），其他平台继续使用 JDK 标准接口。
- Result: macOS 环境下内存采集准确性显著提升；Linux 和 Windows 行为保持不变；后端 154 个测试全部通过。
- Verification: `mvn test -pl test-agent-opencode-runtime` 154/154 通过。

### 2026-06-27 - 将浏览器标签页图标设置为首页logo

- Why: 用户要求将浏览器标签页的图标（favicon）设置为首页面"MIMO测试智能体"左边的logo，使浏览器标签页显示品牌标识。
- What:
  - 创建 `frontend/apps/agent-web/public/` 目录
  - 将 `frontend/apps/agent-web/src/assets/figma/logo.svg` 复制为 `frontend/apps/agent-web/public/favicon.svg`
  - 在 `frontend/apps/agent-web/index.html` 的 `<head>` 中添加 favicon 链接：`<link rel="icon" type="image/svg+xml" href="/favicon.svg" />`
- How: 直接将首页面左上角logo（SVG格式）作为浏览器favicon，保持视觉一致性。
- Result: 浏览器标签页现在显示MIMO logo图标，与首页面logo保持一致。
- Pitfalls: 无。
- Verification: 文件已创建并提交到git。
- Next: 用户在浏览器中刷新页面即可看到新favicon。

### 2026-06-27 - 启动脚本清理日志 + 后端数据库/Redis 连接失败打印 IP:port

- Why: 排查用户 123456789 登录失败时，发现 `.tmp/dev-services/` 下日志文件持续累积，且后端日志里数据库/Redis 连接超时只显示晦涩的 Lettuce/JDBC Reactor 堆栈，没有直观的 IP:port，定位网络问题效率低。
- What:
  - `restart-dev-services.sh`：在构建前新增 `clear_service_logs` 步骤，每次启动清理 `backend.log`/`frontend.log`/`opencode-manager.log`/`opencode.log` 及对应 `.pid` 文件，保留 `opencode-manager-state`/`opencode-manager-session` 运行态目录。
  - `DatabaseMigrationRunner`：迁移前先做 `isValid(2)` 连通性探测，失败时打印 ERROR `数据库连接失败，请检查数据库是否可达: <jdbc url>（host=, port=）`；迁移异常也补充打印数据库地址，host/port 从 JDBC URL 解析。
  - 新增 `RedisStartupHealthCheck`（`HIGHEST_PRECEDENCE+1`）：启动早期主动 TCP 探测 Redis，成功打印 INFO，失败打印 ERROR `Redis 连接失败，请检查 Redis 是否可达: host=, port=（超时 Nms）`，解决 Lettuce 懒连接导致运行时才暴露 Redis 不可达的问题。
- How: 启动脚本用 `rm -f` 清理日志；后端用 SLF4J 在启动早期 `ApplicationRunner` 做 TCP/isValid 探测，不阻断启动，仅打印清晰地址日志。
- Result: 每次重启日志干净无累积；数据库或 Redis 不可达时启动日志直接显示 host:port，无需翻 Reactor 堆栈。
- Pitfalls: `DatabaseMigrationRunner` 仍是 `HIGHEST_PRECEDENCE`，新增的 Redis 检查用 `+1` 紧随其后；Redis host 为空时跳过探测，兼容可选 Redis 场景。
- Verification: `mvn -pl test-agent-app -am compile -DskipTests` 编译通过；`bash -n restart-dev-services.sh` 语法校验通过。

### 2026-06-27 - manager 启动参数改为 WebSocket 公共参数下发

- Why: manager 容器内不应继续通过 `OPENCODE_SESSION_ROOT`、`OPENCODE_CONFIG_DIR`、`OPENCODE_MANAGER_MAX_PROCESSES` 环境变量决定用户 opencode 进程启动参数；这些值需要与 Java 后端使用的 `common_parameters` 保持一致。
- What: `opencode-manager run` 只从环境读取连接必需项和端口池，注册后发送 `configRequest`，收到后端完整 `configUpdate(maxProcesses/sessionRoot/configDir)` 前拒绝 `start/restart`；Java 控制面从 `common_parameters.OPENCODE_MANAGER_MAX_PROCESSES`、`OPENCODE_SESSION_DIR`、`OPENCODE_PUBLIC_CONFIG_DIR` 组装配置，参数更新时广播完整配置。
- How: Go manager 增加运行时配置 ready 状态和热更新方法；Java `OpencodeManagerConfigSyncService` 改为完整配置快照同步；本地重启脚本停止注入 max/session/config 环境变量，稳定文档同步说明 WebSocket-only 配置来源。
- Result: `go test ./...`、`tools/verify-dev-scripts.sh`、`mvn -pl test-agent-domain,test-agent-opencode-runtime,test-agent-api -am test` 均通过。后续本地开发若需要自定义 session/config 路径，应修改通用参数表，而不是给 manager 加环境变量。

### 2026-06-28 - 通用参数增加变量引用、内存缓存、跨实例广播刷新与每进程加载值展示

- Why: 通用参数需支持 `${B}` 引用 B 的值、启动加载到内存、DB 修改后跨实例刷新、管理页展示每台服务器 Java 进程实际加载值。
- What:
  - domain 新增 `CommonParameterReferenceResolver`（`${englishName}` 展开，循环/缺失/超深保留字面，ALL 只能引用 ALL）、`CommonParameterValues` 只读缓存端口、`CommonParameterReloadedEvent`、`CommonParameterLoadSnapshot`/`LoadedParameter`/`CommonParameterLoadSnapshotStore`、`BackendInstanceIdentity` 端口。
  - configuration-management 新增 `InMemoryCommonParameterValues`（启动 `SmartInitializingSingleton` 全量加载，整体原子替换快照）、`CommonParameterCacheRefresher`（监听 `CommonParameterUpdatedEvent` + `ServerBroadcastHandler`：reload+写快照+发 `common-parameter.refresh-requested` 广播+发本地 `CommonParameterReloadedEvent`；远端只 reload 不转发避免循环）、`CommonParameterLoadSnapshotQueryService`；controller 新增 `GET /common-parameters/load-snapshots`。
  - persistence 新增 `RedisCommonParameterLoadSnapshotStore`（key `test-agent:common-param-snapshot:backend:{id}` TTL 30s + 索引 set）。
  - opencode-runtime 新增 `ManagerBackendInstanceIdentity` 实现；`OpencodeManagerConfigSyncService` 改用 `CommonParameterValues` 并监听 `CommonParameterReloadedEvent`；`UserOpencodeProcessAssignmentService` 改用 `CommonParameterValues`。
  - workspace-management `AgentConfigApplicationService`/`ManagedWorkspaceApplicationService` 改用 `CommonParameterValues`。
  - 前端 `shared-types`/`backend-api` 新增类型与 `listCommonParameterLoadSnapshots`；`GeneralParamManagementPanel` 新增"查看各进程加载值"按钮+抽屉。
  - 文档同步 `docs/api/http-api.md`、`docs/api/event-stream.md`、`docs/deployment/database.md`、`docs/architecture/module-map.md`。
- How: 解析器纯领域服务，缓存只存原始值读取时展开；跨实例复用既有 `ServerBroadcastPublisher` Redis pub-sub，payload 只传参数标识不传值；身份经 domain 端口 `BackendInstanceIdentity` 解决 configuration-management 不能依赖 opencode-runtime 的限制；`CommonParameterRepository` 保留为管理端写+启动加载源，消费方迁移到 `CommonParameterValues`。
- Result: 后端 `mvn test` 全绿（含新增解析器/缓存/刷新器/Redis store/controller 端点测试与迁移后的 4 个消费方测试），前端 `pnpm -r typecheck` + `pnpm test` 全绿。无 Flyway/schema 变更，API 仅新增端点，向后兼容。
- Pitfalls: `OpencodeManagerConfigSyncServiceTest` 原本深度 mock `CommonParameterRepository.findByEnglishNameAndPlatform`，迁移后改用 `CommonParameterValues` 桩（opencode-runtime 不依赖 configuration-management，无法用 `InMemoryCommonParameterValues`）；缓存启动加载用 `SmartInitializingSingleton` 而非 `ApplicationRunner`，因 configuration-management 仅依赖 spring-context。
- Verification: `mvn test`、`corepack pnpm -r typecheck`、`corepack pnpm test`。

### 2026-06-28 - “目录不是 Git 仓库”错误提示带上具体目录

- Why: 创建 Agent worktree 失败时前端只显示“创建 Agent worktree失败：目录不是 Git 仓库”，看不到具体是哪个目录，排查困难。后端异常其实已把 `path` 放进结构化 `details`，但前端 `errorMessageFor` 只渲染 `error.message`，目录信息未呈现。
- What: `ensurePublicRepositoryReady` 抛异常条件改为同时提示"目录已存在且非空，但不是 Git 仓库"（`Files.exists(gitRoot) && !isEmptyDirectory(gitRoot)`），完整说明两个阻碍因素，避免遗漏"目录非空"场景。
- How: 直接在异常消息文本里带上路径，复用前端既有 `${fallback}：${error.message}` 渲染，无需改前端。`path` 本就在可安全序列化的 `details` 中，纳入消息不引入新暴露面。
- Result: 前端错误提示变为”创建 Agent worktree失败：目录已存在且非空，但不是 Git 仓库：/data/.../xxx”，同时展示目录路径和两个阻碍条件。
- Pitfalls: 无测试断言该消息文本；未改动前端通用 `errorMessageFor`。
- Verification: `mvn -pl test-agent-workspace-management -am compile` 通过。

### 2026-06-28 - Agent 配置文件操作改为平台文件 WebSocket 路由

- Why: 分布式部署下公共/工作空间 Agent 配置文件必须在 worktree 所属服务器执行，前端不能直连目标服务器，也不能继续依赖后端到后端 HTTP 文件代理；后续 server-bound 能力扩展需要复用同一套 route/ticket/RPC 模式。
- What: `test-agent-api` 新增 Agent 配置文件路由 API 与 `mode=agent-config` 文件 WebSocket ticket 绑定，文件 RPC 新增 `agent-config.list/read/write`；`backend-api` 和 `AgentConfigPanel`/`AgentWorkbench` 将目录列表、读取、写入迁移到目标后端文件 WebSocket；旧 HTTP 文件接口仅保留本地兼容，远端目标返回明确错误要求使用文件 WebSocket。
- How: 路由按 `scope/workspaceId/worktreeId/linuxServerId` 解析目标服务器，ticket 绑定 scope/worktree/server 并由 WebSocket handler 校验 op 参数一致性和写权限；前端先请求 `agent-config/file-ws-route`，再向目标后端申请 ticket 并复用 `WorkspaceFileSocketClient` 发送 Agent 配置文件 RPC。
- Result: Git 初始化、创建 worktree、远端分支、diff、stage、commit、publish 仍走既有 HTTP/进度 WebSocket；配置文件 list/read/write 统一走平台文件 WebSocket。后端目标模块测试、agent-web typecheck、本次相关前端 Vitest 和 `git diff --check` 通过；完整 `vitest run apps/agent-web/tests packages/backend-api` 仍受工作区既有运行拓扑/系统管理前端脏改动影响失败，未纳入本次提交。

### 2026-06-28 - 工作空间文件搜索（后端递归 + 文件名关键字高亮）

- Why: 工作空间原有搜索只过滤已加载文件树（前端本地子串匹配），搜不到未展开目录的文件，且结果无关键字高亮。需支持整个工作空间递归模糊搜索文件名并高亮关键字。
- What:
  - 后端 `WorkspaceFileService` 新增 `searchFiles(rootPath, query)`：递归遍历，文件名不区分大小写子串匹配，跳过硬编码黑名单目录（`.git`/`node_modules` 等），深度上限 20、超时 5s、结果上限 200；新增 `FileSearchResultResponse`（path/name/directory/size/lastModifiedAt）。
  - `WorkspaceApplicationService` 暴露 `searchFiles`；`WorkspaceFileWebSocketHandler` 新增 `workspace.search` RPC op（与现有文件操作同走 WebSocket RPC）。
  - 前端 `backend-api` 新增 `searchFiles`；`shared-types` 新增 `FileSearchResult`；`file-explorer` 新增 `highlightKeyword`（分段渲染 `<mark>`），搜索 tab 改用 app 层传入的服务端结果 + 高亮，本地过滤作回退。
  - `AgentWorkbench` 新增搜索状态（keyword/results/loading）+ 250ms 防抖 + 过期请求丢弃（searchSeq），切工作区时清空。
- How: 包层不直连后端的约束保持——FileExplorer 通过 `search` emit 把关键字交给 AgentWorkbench 发起 RPC，结果经 props 回流。新增 2 参数兼容构造器避免破坏既有 `WorkspaceFileServiceTest`。
- Result: 后端 `mvn -pl test-agent-api -am test` 全绿（含新增 3 个 searchFiles 测试）；前端 `pnpm -r typecheck` 全绿，file-explorer vitest 7 个测试通过（含新增 highlightKeyword 6 个）。文档同步 `docs/api/event-stream.md`、`file-explorer/README.md`、`PACKAGE.md`。
- Pitfalls: 工作区有预先存在的脏改动（FigmaShell.vue / RuntimeTopologyGraph.vue / AgentConfigBackendRoutingService.java 等，非本次任务），提交时已精确暂存排除。`mvn -pl <module> compile` 不带 `-am` 时因本地 `.m2` 的 test-agent-domain jar 缓存不一致会报 `AgentConfigWorktree.linuxServerId()` 找不到，带 `-am` 重新编译 domain 源码后一致；验证后端务必带 `-am`。
- Verification: `mvn -pl test-agent-api -am test -DskipITs`、`corepack pnpm -r typecheck`、`corepack pnpm exec vitest run packages/file-explorer`。

### 2026-06-28 - 公共 Agent worktree 切换绑定服务器上下文

- Why: 公共 Agent 配置在分布式部署下可能存在多个已初始化服务器，管理员需要在左侧 Agent 面板切换“直接公共配置目录”或某台服务器上的公共 worktree，后续文件操作必须落到所选服务器。
- What: 后端新增 `GET /agent-config/public/worktrees?linuxServerId=` 返回指定服务器 `ACTIVE/PUBLIC` worktree 和创建人字段；MyBatis XML 支持 `scope/linuxServerId/status` 过滤，legacy repository 继续走内存过滤兼容。前端 Agent 面板公共级新增切换按钮和弹窗，先选已初始化服务器，再选直接目录或 worktree；workbench store 新增 `publicConfigLinuxServerId` 记住直接目录服务器。
- How: 切换只更新 `worktreeId/linuxServerId` 上下文并清空公共文件树缓存；公共文件列表、读取、保存仍通过 agent-config 文件 WebSocket route/ticket/RPC，直接目录模式用 `linuxServerId` 绑定服务器，worktree 模式用落库 `worktreeId -> linuxServerId` 定位服务器。已打开 tab 不关闭，保存继续沿用 tab 内上下文。
- Result: 后端目标测试、前端 agent-web typecheck、AgentConfigPanel/backend-api 定向 Vitest 通过；文档同步 API、事件、前后端规范、相关 README/PACKAGE。提交时继续排除既有 FigmaShell、运行拓扑和 AgentConfigBackendRoutingService 等无关脏改动。

### 2026-06-29 - 优化分支列表排序：符合格式的排前面

- Why: 提升用户体验，让用户优先看到可选择的分支，而不是在大量置灰分支中寻找。
- What: 实现分支列表排序优化：
  - 标准库：符合格式的分支排在前面，不符合的排在后面
  - 每组内部按字母顺序排序
  - 非标准库保持原始顺序
- How: 添加 sortedBranches 计算属性：
  1. 判断是否为标准库
  2. 将分支分为两组：validBranches 和 invalidBranches
  3. 每组内部按字母顺序排序
  4. 合并返回：[...validBranches, ...invalidBranches]
- Result: 用户优先看到可选择的分支，置灰分支展示在后面供参考，提升了操作效率。
- Pitfalls: 需要注意 computed 属性依赖的函数必须在其之前定义。
- Verification: 手动测试标准库和非标准库的分支排序效果。

### 2026-06-29 - 优化分支默认选中逻辑：置灰分支不能被默认选中

- Why: 标准库刷新分支后，默认选中的可能是置灰的分支，导致用户无法操作。
- What: 修改 loadBranches 函数的默认选中逻辑：
  - 标准库：默认选中第一条符合格式的分支
  - 非标准库：保持原逻辑，选中第一条
  - 如果标准库没有任何符合格式的分支，选中空字符串
- How: 使用 find 方法查找第一条 isValidStandardBranch 返回 true 的分支作为默认值。
- Result: 避免用户看到已选中的分支但无法操作，默认选中可用的分支，提升操作效率。
- Pitfalls: 需要清空 customBranchError 避免切换代码库时残留错误提示。
- Verification: 手动测试标准库刷新分支后的默认选中行为。

### 2026-06-29 - 优化分支排序：符合格式的分支按日期倒序排序

- Why: 用户希望优先看到最新的分支，而不是按字母顺序排列。
- What: 修改 sortedBranches 计算属性的排序逻辑：
  - 符合格式的分支按日期倒序排序（最新的在前）
  - 不符合格式的分支仍按字母顺序排序
- How: 从分支名提取日期部分（后8位 yyyyMMdd），使用 localeCompare 进行倒序排序。
- Result: 用户优先看到最新的分支，便于选择最新版本进行开发。
- Pitfalls: 日期字符串可以直接用 localeCompare 比较，格式 yyyyMMdd 保证字符串比较等同于日期比较。
- Verification: 手动测试标准库分支排序效果，验证最新分支在最前面。

### 2026-06-29 - 修复分支默认选中逻辑：先排序再选第一个

- Why: loadBranches 使用 find 查找第一个符合格式的分支，但 UI 显示使用 sortedBranches（按日期倒序），导致默认选中的分支和显示的第一个可能不一致。
- What: 修改 loadBranches 函数的默认选中逻辑，确保与 sortedBranches 的排序逻辑完全一致：
  1. 过滤出符合格式的分支
  2. 按日期倒序排序（最新在前）
  3. 选择排序后的第一个
- How: 在 loadBranches 中复制 sortedBranches 的排序逻辑，确保选中的就是显示的第一个。
- Result: 用户看到的第一条分支就是被默认选中的分支，逻辑完全一致。
- Pitfalls: 需要保持 loadBranches 和 sortedBranches 的排序逻辑一致，避免出现选中与显示不匹配的情况。
- Verification: 刷新分支后，验证默认选中的分支就是列表中显示的第一个。

### 2026-06-29 - 修复 Markdown 表格渲染多余空行 + MyBatis Analytics Long 类型映射

- Why:
  - 前端：github-markdown-css v5.9.0 将 `<table>` 设为 `display:block`（为横向滚动），导致 `border-collapse:collapse` 失效，`th/td` 的 `border` 与 `tr` 的 `border-top` 各自独立渲染，表头与表体之间产生双重边框空隙，视觉上像空行。同时 `ul/ol` 的 `padding-left:2em` 在聊天气泡内显得间距过大。
  - 后端：`AnalyticsMapper.xml` 中 `javaType="long"` 在 MyBatis 类型别名系统中解析为 `java.lang.Long`（装箱类型），而 `AnalyticsActivityRow` 等 Java record 的 canonical constructor 接受原始类型 `long`。MyBatis 反射查找构造函数时 `Long` ≠ `long`，导致 `NoSuchMethodException`，每次 rollup 调度任务都报错。
- What:
  - 前端 `MarkdownView.vue`：table 加 `display:table!important` 恢复标准表格布局；`tr` 加 `border-top:none` 去除行级双重边框；`ul/ol` 的 `padding-left` 加 `!important` 确保紧凑。
  - 前端 `MarkdownPreview.vue`：同样加 `table{display:table;border-collapse:collapse}` 和 `tr{border-top:none}`。
  - 后端 `AnalyticsMapper.xml`：全部 28 处 `javaType="long"` 改为 `javaType="_long"`（MyBatis 原始类型别名）。
- How: CSS 覆盖利用 Vue scoped `:deep()` 选择器 + `!important` 提高优先级；XML 用 `sed` 批量替换后人工确认。
- Result: 前端 `MarkdownView.test.ts` + `MarkdownPreview.test.ts` + `runtime-reducer.test.ts` 共 25/25 通过。后端 persistence 模块因 H2 不兼容 PostgreSQL CHECK 约束的 Flyway migration 无法本地跑全量测试，XML 修改为纯文本替换无语法风险。
- Pitfalls: github-markdown-css 的 `display:block` 是为宽表横向滚动设计，恢复 `display:table` 后极宽表格可能在窄气泡内溢出（聊天气泡 `max-w-[calc(100%-44px)]` 本身较宽，影响小）；后端 Flyway H2 兼容性问题需单独处理。
- Verification: `npx vitest run packages/agent-chat/tests/ packages/editor/tests/` 相关测试全通；`mvn -pl test-agent-persistence -am compile` 编译通过。

### 2026-06-29 - 公共配置"更新公共配置"弹窗新增提交信息输入并支持推送到远端

- Why: 在 AgentConfigPanel 的公共级"更新公共配置"操作中，原行为仅 fetch+reset+pull（不修改远端），用户在管理面板中无法通过此入口把本地的 `OPENCODE_PUBLIC_CONFIG_DIR` 仓库下的修改提交并推送到远端。需要一个超级管理员可一键拉取最新、提交并推送的复合入口，减少多工具切换的步骤。
- What:
  - 后端新增 DTO `AgentConfigDtos.UpdatePublicConfigAndPushRequest`（branch、commitMessage 必填、operationId、discardLocalChanges）。
  - 后端 `GitWorkspaceService` 新增 `stageAll(Path, String)` 公开方法，执行 `git add --all`。
  - 后端 `AgentConfigApplicationService` 新增 `updatePublicConfigAndPush`：复用 `ensurePublicRepositoryReady` 完成 fetch/reset/pull 后 `stageAll`，若产生新 commit 则用 `commitMessage` 生成提交并 push 到 `branch`，最后广播 `agent-config.public-sync-requested` 事件。空 commitMessage 直接抛 `VALIDATION_ERROR`，保留失败时原始异常。
  - 后端 `AgentConfigController` 暴露 `POST /api/internal/platform/workspace-management/agent-config/public/update-and-push`，要求 `ROLE_SUPER_ADMIN`，X-Trace-Id 透传。
  - 前端 `packages/backend-api/src/index.ts` 新增 `updatePublicAgentConfigAndPush({ branch, commitMessage, operationId, discardLocalChanges })`。
  - 前端 `apps/agent-web/src/components/AgentConfigPanel.vue` 的"更新公共配置"弹窗新增提交信息输入框（必填，带流程提示）、确定按钮文案改为"提交并推送"，提交后通过新接口调用并复用既有 `runOperation` 进度通道。
  - 同步更新 `agent-config-panel.test.ts`（新增提交信息输入必填、CONFLICT 时仍需勾选放弃）和 `AgentConfigControllerTest` / `AgentConfigApplicationServiceTest` 的测试。
  - 文档 `docs/api/http-api.md` 新增 `POST /public/update-and-push` 行与请求体/语义说明。
- How:
  - Service 复用现有 `commitStaged` / `push` / `headCommit` / `statusPorcelain`，在 `hasStagedChanges` 辅助方法里复用 porcelain 检查；流程步骤串行显式：PREPARING_REPOSITORY → COMMITTING → PUSHING → BROADCASTING。
  - Controller 与 Service 测试都使用 mock service / RecordingGitWorkspaceService 覆盖三种场景：有本地变更、无本地变更、空 commitMessage 拒绝。
  - 提交信息为必填项，前后端一致做 `trim().length > 0` 校验，禁用"提交并推送"按钮。
- Result:
  - 后端：test-agent-workspace-management 19/19、test-agent-api 13/13 通过；test-agent-common / test-agent-api 编译通过。
  - 前端：vitest `agent-config-panel` 6/6、`backend-api` 32/32 通过；`pnpm typecheck` 无报错。
- Pitfalls:
  - `stageAll` 必须先 install `test-agent-common` 才能让下游模块拿到新方法签名，否则会触发 `NoSuchMethodError`。
  - Controller 用 mock service 时不应断言空 commitMessage 的 400 响应，校验逻辑在 Service 内，Controller 测试只覆盖路由与权限；Service 单测中专门覆盖。
  - 公共配置 git 仓库路径在 `OPENCODE_PUBLIC_CONFIG_DIR` 通用参数中维护，路径变更需要走通用参数管理入口而非本接口。

### 2026-06-29 - 修复 /api/workspaces 返回 updatedAt must not be before createdAt

- Why: 用户反馈 GET /api/workspaces?page=1&size=50 一直返回 updatedAt must not be before createdAt 校验错误。该校验来自 Workspace 领域 record 的 compact constructor，抛 IllegalArgumentException 后被 GlobalExceptionHandler 原样回吐，把内部不变量错误信息暴露给前端。
- What:
  - 数据侧：192.168.100.200:5432/testagent 的 workspaces 表中 wrk_754915e1ecfe4a139c24b845f5be3d2e（F-WRAPP 本地工程模板-local）的 updated_at 早于 created_at（同一天 02:38:00 < 09:12:19，疑似时钟回拨或批量写入），是当前唯一一条脏数据。一次性 update workspaces set updated_at = created_at where workspace_id = ? 回填完毕，接口立即恢复 200。
  - 防御侧：JdbcWorkspaceRepository.rowMapper 增加 
ormalizeUpdatedAt 兜底：发现 updated_at < created_at 时把 updated_at 抬到 created_at，并打一条 SLF4J WARN 保留原始值供排障，避免类似历史脏数据再次把领域异常原样甩到前端。Workspace 领域不变量保持不变，写入侧仍由领域层保证 updated_at >= created_at。
  - 文档与测试：ackend/test-agent-persistence/README.md 在 JdbcWorkspaceRepository 一行补充历史脏数据归一化说明；JdbcRepositoryIntegrationTest 新增 workspaceRepositoryClampsLegacyUpdatedAtBeforeCreatedAtOnRead 用例，直接 insert 脏行后验证 indById / indPage 都把 updatedAt 抬到 createdAt。
- How: 仅修改持久化映射层，未触动领域对象和 API 契约；按 docs/standards/backend.md 第 4 条要求在 Repository 测试和模块 README 记录边界。
- Result:
  - 接口 GET /api/workspaces?page=1&size=50 实时请求返回 200，数据正常。
  - 后端 mvn -pl test-agent-persistence -am compile 通过；新增的归一化用例因 JdbcRepositoryIntegrationTest 共用的 V20260628223000__add_macos_platform_support.sql 在 H2 PostgreSQL 模式中 ::text[] 数组语法不兼容而无法本地全量执行（与本次修复无关的预存在问题），已记录在 Pitfalls。
- Pitfalls:
  - ApplicationDatabaseIntegrityTest 等走 H2 的集成测试因 macOS 平台 migration 使用了 PostgreSQL 专属的 ARRAY[...]::text[] 语法而失败，是先于本次修复存在的测试基础设施问题；新增归一化逻辑仅在生产 PostgreSQL 路径生效。
  - 该 IllegalArgumentException 链路会把领域内部不变量消息原样暴露给前端，存在信息泄漏风险；后续可考虑在 GlobalExceptionHandler 中对 IllegalArgumentException 改为统一 BAD_REQUEST 描述并把原始 message 仅写入 server log。
- Verification: Invoke-WebRequest http://127.0.0.1:8080/api/workspaces?page=1&size=50 返回 200；mvn -pl test-agent-persistence -am compile 编译通过。
