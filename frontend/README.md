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

`apps/agent-web/next.config.ts` 允许本地开发从 `http://127.0.0.1:3000` 访问 Next dev resource；联调时使用 `localhost` 或 `127.0.0.1` 都应保持完整交互能力。

## 访问边界

- 前端不得直连 opencode server。
- HTTP 请求只能通过 `packages/backend-api`。
- RunEvent SSE 只能通过 `packages/event-stream-client`。
- `apps/agent-web` 负责组合页面；业务能力必须沉淀到对应 package。
