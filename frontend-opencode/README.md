# frontend-opencode

## 工程定位

`frontend-opencode` 是 opencode IDE App 的 Vue 3 + TypeScript + Vite 复刻工程，行为参考 `opencode-source/opencode-1.17.8/packages/app`。本工程不复刻 `packages/web` 官网、文档站或公网分享轮询页。

浏览器侧禁止直连 opencode server：HTTP 通过平台 `test-agent-app` 的 `/api` 入口，实时消息通过 RunEvent SSE，受控 PTY 通过后端签发 ticket。

## 技术栈

- Vue 3、Vue Router、Pinia。
- TypeScript 6、Vite 8、Vitest、Playwright。
- `@test-agent/backend-api`、`@test-agent/event-stream-client`、`@test-agent/shared-types` 通过 Vite/TS alias 复用 `frontend/packages/*/src`。
- lucide 图标与本目录 `src/styles/theme.css` 中的 opencode 风格 token。

## 本地命令

```bash
cd frontend-opencode
corepack pnpm install
corepack pnpm dev
corepack pnpm typecheck
corepack pnpm test
corepack pnpm build
corepack pnpm e2e
corepack pnpm e2e:real
```

开发服务器默认把 `/api` 代理到 `http://127.0.0.1:8080`，可用 `VITE_TEST_AGENT_API_PROXY_TARGET` 覆盖。生产同域部署时可不设置 `VITE_TEST_AGENT_API_BASE_URL`，请求会走同源 `/api`。

`corepack pnpm e2e` 只运行 mock E2E；`corepack pnpm e2e:real` 使用 `playwright.real.config.ts` 和 `tests/e2e-real/*.real-spec.ts`，只跑一次桌面 smoke，必须先启动真实 `test-agent-app` 与 opencode runtime。真实 E2E 默认把 Vite 的同源 `/api` 代理到 `http://127.0.0.1:8080`，可用 `FRONTEND_OPENCODE_REAL_API_BASE_URL` 或 `VITE_TEST_AGENT_API_PROXY_TARGET` 覆盖；可选环境变量包括：

- `FRONTEND_OPENCODE_REAL_WORKSPACE_ID`：直接复用已有 workspace。
- `FRONTEND_OPENCODE_REAL_WORKSPACE_ROOT`：未提供 workspaceId 时用于查找或创建 workspace，默认当前仓库根目录。
- `FRONTEND_OPENCODE_REAL_SESSION_ID`：复用已有 session；未提供时从 `/new-session` UI 创建。
- `FRONTEND_OPENCODE_REAL_PROMPT` / `FRONTEND_OPENCODE_REAL_EXPECT_TEXT`：真实 prompt 与等待的 assistant 文本片段。
- `FRONTEND_OPENCODE_REAL_API_TOKEN`：平台 API 需要鉴权时使用。

## 目录说明

- `src/api/platform.ts`：平台 API 适配层，补齐 opencode Web parity 所需 config/provider/worktree/share/MCP auth 方法。
- `src/stores/`：platform、workspace、session、prompt、run-events、settings、terminal 状态。
- `src/views/`：`/`、`/w/:workspaceId/session/:sessionId?`、`/new-session` 三个默认路由。
- `src/components/`：composer、timeline、session toolbar/fork dialog、side panel、settings、command palette、toast。
- `docs/`：依赖、API 映射和 parity 验收记录。

## 验收边界

首版覆盖 opencode App 的 Web IDE 壳层、工作区/会话入口、session toolbar、composer、RunEvent message reducer、permission/question/todo/diff/terminal 面板和 settings/provider 展示入口。真实三服务联调需要同时启动 `test-agent-app`、opencode server 与本工程，并通过 `corepack pnpm e2e:real` 验证 prompt -> RunEvent SSE -> timeline 渲染闭环。
