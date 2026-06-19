# frontend

## 工程定位

完全自研测试智能体 Web IDE 前端。`frontend/interaction-visual-demo` 只作为交互参考资料，不纳入 `pnpm-workspace.yaml` 构建。

## 技术栈

- Next.js 16
- React 19
- TypeScript 6
- Tailwind CSS 4
- assistant-ui
- Dockview
- Monaco Editor / Monaco Diff Editor
- TanStack Query
- Zustand
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

`apps/agent-web/next.config.ts` 允许本地开发从 `http://127.0.0.1:3000` 访问 Next dev resource；联调时使用 `localhost` 或 `127.0.0.1` 都应保持完整交互能力。

## 访问边界

- 前端不得直连 opencode server。
- HTTP 请求只能通过 `packages/backend-api`。
- RunEvent SSE 只能通过 `packages/event-stream-client`。
- `apps/agent-web` 负责组合页面；业务能力必须沉淀到对应 package。
- Phase 11 opencode Web App 复刻以运行态能力为范围，交互行为参考 `opencode-source/opencode-1.17.8/packages/app`；settings/config/provider/server 配置页和 opencode `packages/web` 公网分享轮询不进入默认前端边界。
- 当前 Phase 11 实现已接入 backend-api runtime 方法、Agent/Provider/Model/Mode 运行态选择、session history 搜索/置顶/删除、message part reducer、permission/question dock、Todo、文件/图片附件、busy follow-up 队列、Monaco 选区上下文、slash command palette、`@` context picker、Run/Session/VCS Diff 来源切换、Diff hunk 导航与 hunk context、MCP/LSP/VCS 状态摘要、`/s/[sessionId]` 只读 transcript 和受控 PTY terminal panel；公开 share 授权、per-file/per-message 回滚和真实三服务联调 E2E 仍按后续批次推进。
