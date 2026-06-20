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

Phase 11 真实三服务联调 E2E 单独执行，不进入默认 `pnpm e2e`：

```bash
tools/dev-phase11-real-e2e.sh --start-services
```

该脚本默认复用已运行的 opencode server 和 `test-agent-app`；传入 `--start-services` 时会启动本地 Postgres、opencode server 和后端，前端由 `frontend/playwright.real.config.ts` 的 Playwright `webServer` 管理。服务日志保留在 `.tmp/phase11-real-e2e/`，脚本不会打印 `.env.local` 或 `.env.test` 中的敏感值。本机 Docker daemon 未运行时，脚本会在启动 Postgres 前直接失败并提示先启动 Docker Desktop，或手动启动后端后不带 `--start-services` 重试。

`frontend-opencode` 独立联调见 `frontend-opencode/README.md`，默认通过 Vite proxy 把 `/api` 转发到 `test-agent-app`。

## 访问边界

- 前端不得直连 opencode server。
- HTTP 请求只能通过 `packages/backend-api`。
- RunEvent SSE 只能通过 `packages/event-stream-client`。
- `apps/agent-web` 负责组合页面；业务能力必须沉淀到对应 package。
- Phase 11 opencode Web App 复刻以运行态能力为范围，交互行为参考 `opencode-source/opencode-1.17.8/packages/app`；顶层 `frontend-opencode` 承载 Vue/Vite 复刻工程，opencode `packages/web` 官网/文档/公网分享轮询不进入默认边界。
- 当前 Phase 11 实现已接入 backend-api runtime 方法、Agent/Provider/Model/Mode 运行态选择、session history 搜索/置顶/删除、message part reducer、permission/question dock、Todo、文件/图片附件、busy follow-up 队列、Monaco 选区上下文、slash command palette 与参数表单补全、`@` context picker、Run/Session/VCS Diff 来源切换、Diff hunk 导航与懒加载 editor、MCP/LSP/VCS 状态摘要、`/s/[sessionId]` 只读 transcript 和受控 PTY terminal panel；公开 share 授权、per-file/per-message 回滚和真实三服务联调 E2E 仍按后续批次推进。

## Phase 11 UI 与主题边界

- 全局 theme token、Dockview/Monaco 视觉适配、滚动条、panel chrome 和轻量动画由 `apps/agent-web/src/styles/globals.css` 承载；包内组件只消费这些 token，不在业务组件里复制整套主题。
- `packages/ui-kit` 只提供 Button、Badge、Input、Tabs 等无业务状态基础控件；运行态选择、permission/question、terminal 和 Diff 语义仍放在对应 feature package。
- 面板、toolbar、terminal、Diff、Agent timeline 和文件树必须保持稳定尺寸，Agent timeline 需要把 reasoning 思考过程、任务分解、Skill/Tool 调用与最终回答分块展示并保留独立滚动区域；右侧 Agent 对话框使用独立浅色 `--ta-chat-*` token，避免 hover、streaming 文本、warning、hunk 导航或状态徽标导致布局跳动。
- Phase 11 的真实三服务 E2E 尚无最新通过记录；当前只能认为 mock E2E 和单元测试覆盖了主流程，不能把真实联调标记为完成。
