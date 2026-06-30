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

推荐从仓库根目录使用一键脚本重启三服务，脚本默认读取 `.env.test` 并以 `test` profile 启动，按「后端 → opencode-manager → 前端」顺序逐个先 kill 原进程再启动；当 `TEST_AGENT_OPENCODE_BASE_URL` 是本地地址时默认启动 Go `opencode-manager`（由它派生 opencode 子进程，不再单独启动 `opencode serve`）：

工作台左侧 Agent 配置树展示公共级 `opencode/` 和工作空间级 `.opencode/` 配置根，包含 `agents/` 与 `skills/`；普通工作空间文件树隐藏根级 `.opencode`，避免重复展示。工作空间级 `+` 只初始化应用自己的技能包：`skills/<name>/SKILL.md`、`rules/README.md` 和 `templates/README.md`，其中 `SKILL.md` 使用 opencode 支持的 `name` / `description` frontmatter。公共仓库有本地修改时仍可浏览，更新前必须明确勾选放弃已跟踪修改。左侧 Git 变更面板的测试数据分为应用工作区 mock 文件和应用级 opencode `agents/*.md` / `skills/<skill>/SKILL.md` mock 文件，不混入公共级配置。

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

脚本会从 `TEST_AGENT_FRONTEND_URL` 推导前端监听 host/port，并把 `TEST_AGENT_BASE_URL` 注入为 Vite 的 `VITE_TEST_AGENT_API_BASE_URL`；需要通过局域网地址访问时，可在启动前设置 `TEST_AGENT_FRONTEND_URL=http://192.168.100.115:3000` 和 `TEST_AGENT_BASE_URL=http://192.168.100.115:8080`，后端 CORS 未显式配置时会自动包含该前端 origin。

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
- RunEvent SSE 只能通过 `packages/event-stream-client`；默认使用 `/api/internal/agent/opencode/runs/{runId}/events`；刷新或重进运行中会话时先通过 `backend-api.getActiveRun(sessionId)` 查询非终态 Run，再恢复 SSE 订阅。
- `apps/agent-web` 负责组合页面；业务能力必须沉淀到对应 package。
- opencode Web App 复刻以运行态能力为范围，交互行为参考 `opencode-source/opencode-1.17.8/packages/app`；顶层 `frontend-opencode` 承载 Vue/Vite 复刻工程，opencode `packages/web` 官网/文档/公网分享轮询不进入默认边界。
- 当前已接入 backend-api runtime 方法、Agent/Provider/Model 运行态选择（Agent 下拉过滤为 primary+all，排除 subagent/hidden，当前用户 opencode 进程 `READY` 后自动刷新运行态目录）、右上角成员应用切换、应用版本工作区/个人工作区切换与同步、受控 Workspace 目录选择、session history 搜索/置顶/删除、历史会话完整消息渲染与工作区不可用只读态、assistant 消息满意/不满意反馈、message part reducer、active run 恢复入口、permission/question dock、Todo、上传附件前端弹窗样式（后台上传暂未接入）、busy follow-up 队列、输入法组合输入阶段 Enter 防误发、右侧输入区交互触发当前用户 opencode 进程状态重新探测且刷新中阻止提交、后台按未 READY 5 秒/READY 30 秒动态探测当前用户 opencode 进程健康状态、Monaco 选区上下文、slash command palette 与参数表单补全、`@` context picker、Run/Session/VCS Diff 来源切换、Diff hunk 导航与懒加载 editor、运行中实时追踪写文件工具变更、MCP/LSP/VCS 状态摘要、左下角设置模态（应用与工作空间管理含版本库英文名、创建工作空间进度轮询、个人 SSH key；无应用配置权限时显示角色提示）、仅 `SUPER_ADMIN` 可见的系统管理入口（定时任务管理 + 运行管理 + 运营分析，运行管理展示最新 CPU/内存并在点击容器或后端 Java 进程后用 ECharts 展示 Redis 48 小时指标趋势；运营分析展示用户漏斗、使用强度、Run 结果、满意度、Diff 采纳、token 强度、趋势、热力、排行、明细和 CSV 导出且不展示费用字段）、`/s/[sessionId]` 只读 transcript 和受控 PTY terminal panel；公开 share 授权、per-file/per-message 回滚和真实三服务联调 E2E 仍按后续批次推进。

## UI 与主题边界

- 全局 theme token、Figma Web IDE 风格 activity rail、Dockview/Monaco 视觉适配、滚动条、panel chrome 和轻量动画由 `apps/agent-web/src/styles/globals.css` 承载；包内组件只消费这些 token，不在业务组件里复制整套主题。
- `packages/ui-kit` 只提供 Button、Badge、Input、Tabs 等无业务状态基础控件；运行态选择、permission/question、terminal 和 Diff 语义仍放在对应 feature package。
- 面板、toolbar、terminal、Diff、Agent timeline 和文件树必须保持稳定尺寸，Agent timeline 需要把 reasoning 思考过程、任务分解、Skill/Tool 调用与最终回答分块展示并保留独立滚动区域；右侧 Agent 对话框使用独立浅色 `--ta-chat-*` token，避免 hover、streaming 文本、warning、hunk 导航或状态徽标导致布局跳动。
- 真实三服务 E2E 尚无最新通过记录；当前只能认为 mock E2E 和单元测试覆盖了主流程，不能把真实联调标记为完成。
