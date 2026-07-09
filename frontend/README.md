# frontend

## 工程定位

完全自研测试智能体 Web IDE 前端。`frontend/interaction-visual-demo` 只作为交互参考资料，不纳入 `pnpm-workspace.yaml` 构建；顶层 `frontend-opencode` 是 opencode IDE App 的 Vue/TypeScript/Vite 复刻交付物，作为独立工程单独安装、构建和验收，不纳入 `frontend/pnpm-workspace.yaml`。

## 技术栈

- Vue 3
- Vite 8
- TypeScript 6
- Tailwind CSS 4
- vue-router
- Pinia
- @tanstack/vue-query
- dockview-vue（Dockview 官方 Vue 封装）
- Monaco Editor（原生 `monaco-editor`，按需懒加载）
- lucide-vue-next
- @vscode/codicons（仅文件浏览区使用）
- pnpm workspace

## workspace

```text
apps/agent-web
packages/backend-api
packages/event-stream-client
packages/workbench-shell
packages/file-explorer
packages/editor
packages/diff-viewer
packages/agent-chat
packages/terminal
packages/test-runner
packages/ui-kit
packages/shared-types
```

## 本地命令

```bash
cd frontend
corepack pnpm install
corepack pnpm dev
corepack pnpm lint
corepack pnpm typecheck
corepack pnpm test
corepack pnpm build
corepack pnpm e2e
corepack pnpm e2e:real
```

完整前端检查也可以从仓库根目录执行：

```bash
tools/dev-frontend-check.sh
```

## 本地联调

推荐从仓库根目录使用一键脚本重启三服务，脚本默认读取 `.env.test` 并以 `test` profile 启动，按「后端 → opencode-manager → 前端」顺序逐个先 kill 原进程再启动；前端构建和 dev server 启动前会检查 `node_modules` 是否落后于 `pnpm-lock.yaml`、workspace 配置或各包 `package.json`，过期时自动执行 `corepack pnpm install --frozen-lockfile`；当 `TEST_AGENT_OPENCODE_BASE_URL` 是本地地址时默认启动 Go `opencode-manager`（由它派生 opencode 子进程，不再单独启动 `opencode serve`）：

工作台左侧 Agent 配置树展示公共级 `opencode/` 和工作空间级 `.opencode/` 配置根，包含 `agents/` 与 `skills/`；普通用户仍可只读查看根级 `agents/` 与 `skills/`，但隐藏 `.DS_Store`、`.gitignore`、`node_modules`、`package.json` 等配置仓库工程杂项；普通工作空间文件树隐藏根级 `.opencode`，避免重复展示。工作区文件树、搜索结果、变更列表和 Agent 配置树统一使用 VS Code Workbench 风格的局部文件浏览 token：背景 `#f8f8f8`、边框 `#e5e5e5`、13px 系统 UI 字体、22px 行高和 VS Code codicon 文件/目录图标。工作空间级 `+` 只初始化应用自己的技能包：`skills/<name>/SKILL.md`、`skills/<name>/rules/README.md` 和 `skills/<name>/templates/README.md`，其中 `SKILL.md` 使用当前 opencode skill 模板，`name` 只生成小写字母、数字和短横线，frontmatter 包含 `name`、`description`、`compatibility` 与 `metadata`。公共仓库有本地修改时仍可浏览，更新前必须明确勾选放弃已跟踪修改。Agent 配置 worktree 发布冲突时，前端读取 `BackendApiError.details.conflictFiles` 展示具体冲突文件。进入应用级 recent 且能反查 `versionId` 时，前端只读取该版本已有的 `workspaceName=default` 私人 worktree；没有 default 私人工作区记录时保持空态，不创建、不修复、不加载文件树。用户手动切换应用版本或新增版本时才显式确保并切到 default 私人 worktree，footer 按钮和版本子菜单会显示当前私人 worktree 分支；无应用历史或 recent 无 `versionId` 时只选择应用，不自动加载工作区，左侧切换入口仍保留。普通工作区保存后刷新平台 Git diff，不依赖 opencode `/vcs/diff`。左侧 Git 变更面板展示真实应用工作区 Git diff 和应用级 opencode `agents/*.md` / `skills/<skill>/SKILL.md` diff，不再提供 mock 测试数据入口；agents 分组只接收 `.opencode/agents` 与 `.opencode/skills` 变更，公共级文件和普通应用文件不会混入；应用工作区文件行提供真实 stage/unstage 与回退按钮，悬浮显示完整路径，点击直接复用已加载 patch。

应用工作区暂存通过平台 API 操作真实 Git index，同时仍作为发布文件白名单；后端普通发布会隔离历史 index。冲突期间允许普通文件 stage/unstage，但按 Git 原生规则禁止提交，冲突文件可在 Monaco 三方合并编辑器中可靠选择结果、保存或取消整次 merge。前端只有在后端明确返回 `remotePushed=true` 时才展示提交并推送成功。

```bash
./restart-dev-services.sh
```

Windows PowerShell 直接使用同名入口：

```powershell
powershell -ExecutionPolicy Bypass -File .\restart-dev-services.ps1 -Profile test -EnvFile .env.test
```

连接 `guo` 或其他个人调试环境时显式覆盖 profile 和 dotenv：

```bash
TEST_AGENT_BASE_URL=http://192.168.100.115:8080 \
TEST_AGENT_FRONTEND_URL=http://192.168.100.115:3000 \
./restart-dev-services.sh --profile guo --env-file .env.local --skip-frontend-build
```

Windows PowerShell 下使用 `$env:` 写入额外变量；若 `-EnvFile` 指定的 dotenv 已有同名键，以 dotenv 加载结果为准，与 Bash 脚本一致：

```powershell
$env:TEST_AGENT_BASE_URL = "http://192.168.100.115:8080"
$env:TEST_AGENT_FRONTEND_URL = "http://192.168.100.115:3000"
powershell -ExecutionPolicy Bypass -File .\restart-dev-services.ps1 -Profile guo -EnvFile .env.local -SkipFrontendBuild
```

脚本会从 `TEST_AGENT_FRONTEND_URL` 推导前端监听 host/port，并把 `TEST_AGENT_BASE_URL` 注入为 Vite 的 `VITE_TEST_AGENT_API_BASE_URL`；未显式设置 `TEST_AGENT_BASE_URL` 时，会使用自动探测到的后端内网地址（例如 `http://192.168.100.115:8080`），避免局域网访问前端时浏览器仍请求 `127.0.0.1`。需要指定固定入口时，可在启动前设置 `TEST_AGENT_FRONTEND_URL=http://192.168.100.115:3000` 和 `TEST_AGENT_BASE_URL=http://192.168.100.115:8080`，后端 CORS 未显式配置时会自动包含该前端 origin。

本机存在多个 opencode 版本时，在当前使用的 dotenv（默认 `.env.test`，或显式 `--env-file` 指定的文件）里指定 `TEST_AGENT_OPENCODE_BIN`，避免 PATH 命中旧版本。例如：

```bash
TEST_AGENT_OPENCODE_BIN=${HOME}/.opencode/bin/opencode
```

分别启动三个服务：

```bash
cd backend
mvn spring-boot:run -pl test-agent-app -Dspring-boot.run.profiles=local
```

```bash
opencode serve --hostname 127.0.0.1 --port 4096 --cors http://localhost:3000
```

```bash
cd frontend
corepack pnpm dev
```

服务启动后可在仓库根目录执行：

```bash
tools/dev-runnable-loop-check.sh
```

真实三服务联调 E2E 单独执行，不进入默认 `pnpm e2e`：

```bash
tools/dev-phase11-real-e2e.sh --start-services
```

该脚本默认复用已运行的 opencode server 和 `test-agent-app`；传入 `--start-services` 时会启动本地 Postgres、opencode server 和后端，前端由 `frontend/playwright.real.config.ts` 的 Playwright `webServer` 管理。服务日志保留在 `.tmp/phase11-real-e2e/`，脚本不会打印 `.env.local` 或 `.env.test` 中的敏感值。本机 Docker daemon 未运行时，脚本会在启动 Postgres 前直接失败并提示先启动 Docker Desktop，或手动启动后端后不带 `--start-services` 重试。

`frontend-opencode` 独立联调见 `frontend-opencode/README.md`，默认通过 Vite proxy 把 `/api` 转发到 `test-agent-app`。

## 访问边界

- 前端不得直连 opencode server。
- HTTP 请求只能通过 `packages/backend-api`；Run/Diff/runtime 默认使用 `agentId=opencode` 的 `/api/internal/agent/{agentId}/...` 后端 URL。
- 企业内浏览器基线按 Chromium 108 兼容，`agent-web` 生产构建显式使用 `build.target=chrome108` 和 `build.cssTarget=chrome108`；涉及 Web Crypto、Clipboard 等安全上下文 API 时必须先做能力检测，内网 HTTP 访问不能直接假设这些 API 可用。个人 SSH key 加密优先使用 Web Crypto，HTTP 内网下 `crypto.subtle` 不可用时使用 node-forge 纯 JS AES-GCM + RSA-OAEP/SHA-256 回退，仍禁止明文提交。
- RunEvent SSE 和用户级运行态 fetch SSE 只能通过 `packages/event-stream-client`；RunEvent 默认使用 `/api/internal/agent/opencode/runs/{runId}/events`，刷新或重进运行中会话时先通过 `backend-api.getActiveRun(sessionId)` 查询非终态 Run，再恢复 SSE 订阅；用户级运行态使用 `/api/internal/platform/opencode-runtime/sessions/runtime-state/events` 携带 Bearer Token 推送历史运行中计数和 `question.asked` 待答提醒。运行中点击新建对话只清空当前视图和关闭当前 RunEvent SSE，不调用 cancel/abort；前端只把 RunEvent 应用到当前订阅且仍为页面活动态的 Run。`session.status.retry` 会在右侧时间线展示原因和 60 秒倒计时，等待期间仍视为运行中，第 1/2 次到期后用最近一次 Run 草稿自动重试，第 3 次后本地兜底为失败；新 Run 请求和后到成功/取消终态会清理上一轮 `run.failed` 失败卡与 SSE 连接错误提示，避免旧 `Streaming response failed` 覆盖后续轮次。
- RunEvent SSE 和用户级运行态 fetch SSE 只能通过 `packages/event-stream-client`；RunEvent 默认使用 `/api/internal/agent/opencode/runs/{runId}/events`，刷新或重进运行中会话时先通过 `backend-api.getActiveRun(sessionId)` 查询非终态 Run，再恢复 SSE 订阅；用户级运行态使用 `/api/internal/platform/opencode-runtime/sessions/runtime-state/events` 携带 Bearer Token 推送历史运行中计数和 `question.asked` 待答提醒，历史按钮 badge 只按历史第一页 30 条会话派生。运行中点击新建对话只清空当前视图和关闭当前 RunEvent SSE，不调用 cancel/abort；前端只把 RunEvent 应用到当前订阅且仍为页面活动态的 Run。`session.status.retry` 会在右侧时间线展示原因和 60 秒倒计时，等待期间仍视为运行中，第 1/2 次到期后用最近一次 Run 草稿自动重试，第 3 次后本地兜底为失败；新 Run 请求和后到成功/取消终态会清理上一轮 `run.failed` 失败卡与 SSE 连接错误提示，避免旧 `Streaming response failed` 覆盖后续轮次。
- 右侧 Agent 面板的“原始输出”只展示当前页面生命周期内，前端捕获的浏览器与平台后端 HTTP 请求/响应正文和 RunEvent SSE `MessageEvent.data`；它不记录 opencode server 原始事件、不落库，刷新或换浏览器后不保留。
- `apps/agent-web` 负责组合页面；业务能力必须沉淀到对应 package。
- opencode Web App 复刻以运行态能力为范围，交互行为参考 `opencode-source/opencode-1.17.8/packages/app`；顶层 `frontend-opencode` 承载 Vue/Vite 复刻工程，opencode `packages/web` 官网/文档/公网分享轮询不进入默认边界。
- 当前已接入 backend-api runtime 方法、Agent/Provider/Model 运行态选择（底部 Agent 下拉按 opencode `local.agent.list()` 过滤为 primary+all 且排除 subagent/hidden，输入框 `@agent` 候选按 prompt autocomplete 过滤为 subagent+all 且排除 primary/hidden，当前用户 opencode 健康状态 ready 后自动刷新运行态目录）、右上角成员应用切换、应用版本工作区/个人工作区切换与同步、超级管理员服务器工作空间选择、用户级 session history 远端搜索/分页（默认 30 条、显示应用/工作区/版本、按更新时间倒序、不拼接本地伪历史，历史按钮数字只统计第一页 30 条中的未完成会话）、历史会话完整消息渲染与所属应用/工作区不可切换时的只读态、assistant 消息满意/不满意反馈、message part reducer、active run 恢复入口、permission/question dock、Todo、工作区上下文附件（Monaco 选区、文件树文件、编辑器 Tab 文件添加到对话，输入框上方预览/删除/清空，发送时前端结构化拼接 prompt，并按字符数拦截超长内容）、上传附件前端弹窗样式（后台上传暂未接入）、busy follow-up 队列、输入法组合输入阶段 Enter 防误发、后台每 10 秒调用弱健康接口检测当前用户 opencode 进程健康，弱健康不健康时复查 `/processes/me`，MCP/LSP 状态 5 分钟刷新一次且 VCS 状态保持 30 秒刷新、Monaco 选区上下文、统一进入可恢复 Run 的 slash command palette 与参数表单补全、`@` context picker、Run/Session/VCS Diff 来源切换、Diff hunk 导航与懒加载 editor、运行中实时追踪写文件工具变更、MCP/LSP/VCS 状态摘要、右侧对话底部 Agent/Skill/MCP/Plugin 已加载数量摘要和详情弹层、左下角设置模态（应用与工作空间管理含版本库内外部部署模式、版本库英文名、创建工作空间进度轮询、个人 SSH key、用户管理查询/创建测试用户和超管直接调整角色；无应用配置权限时显示角色提示）、仅 `SUPER_ADMIN` 可见的系统管理入口（定时任务管理 + 运行管理 + 运营分析，运行管理展示最新 CPU/内存并在点击容器或后端 Java 进程后用 ECharts 展示 Redis 48 小时指标趋势；运营分析展示用户漏斗、使用强度、Run 结果、满意度、Diff 采纳、token 强度、趋势、热力、排行、明细和 CSV 导出且不展示费用字段）、`/s/[sessionId]` 只读 transcript 和受控 PTY terminal panel；公开 share 授权、per-file/per-message 回滚和真实三服务联调 E2E 仍按后续批次推进。
- 运行管理的后端 Java 进程表格和趋势图会展示服务器 CPU/load/内存/swap/磁盘、Java 进程 CPU/RSS/FD、JVM heap/non-heap/direct/mapped、GC、线程等可空字段；旧后端缺失新增字段时继续显示 `-` 或使用旧字段回退，趋势图保留断点。

## UI 与主题边界

- 全局 theme token、Figma Web IDE 风格 activity rail、Dockview/Monaco 视觉适配、滚动条、panel chrome 和轻量动画由 `apps/agent-web/src/styles/globals.css` 承载；包内组件只消费这些 token，不在业务组件里复制整套主题。
- `packages/ui-kit` 只提供 Button、Badge、Input、Tabs 等无业务状态基础控件；运行态选择、permission/question、terminal 和 Diff 语义仍放在对应 feature package。
- 面板、toolbar、terminal、Diff、Agent timeline 和文件树必须保持稳定尺寸。Agent timeline 主路径使用 `packages/agent-chat/src/opencode-like` 的 `.oc-*` 时间线，把 reasoning 思考过程、上下文工具组、Skill/Tool 调用、文件引用、Diff 摘要与最终回答分块展示并保留独立滚动区域；`session.status.retry` 这类上游等待重试/限额状态必须转成 runtime retry 行展示，按前端首次收到事件的时间显示固定 60 秒倒计时，不能停留在普通“思考中”；右侧 Agent 对话框继续消费全局 `--ta-*`/`--ta-chat-*` token，避免 hover、streaming 文本、warning、hunk 导航或状态徽标导致布局跳动。
- 旧 `.figma-chat-*` 气泡消息循环、`AgentCard`/`TimelineCard` 和 `MessageParts` 旧 part 组件只作为作废兼容代码保留；新增对话展示能力必须落在 `packages/agent-chat/src/opencode-like`。
- 真实三服务 E2E 尚无最新通过记录；当前只能认为 mock E2E 和单元测试覆盖了主流程，不能把真实联调标记为完成。
