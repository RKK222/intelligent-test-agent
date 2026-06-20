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
```

开发服务器默认把 `/api` 代理到 `http://127.0.0.1:8080`，可用 `VITE_TEST_AGENT_API_PROXY_TARGET` 覆盖。生产同域部署时可不设置 `VITE_TEST_AGENT_API_BASE_URL`，请求会走同源 `/api`。

## 目录说明

- `src/api/platform.ts`：平台 API 适配层，补齐 opencode Web parity 所需 config/provider/worktree/share/MCP auth 方法。
- `src/stores/`：platform、workspace、session、prompt、run-events、settings、terminal 状态。
- `src/views/`：`/`、`/w/:workspaceId/session/:sessionId?`、`/new-session` 三个默认路由。
- `src/components/`：composer、timeline、session toolbar/fork dialog、side panel、settings、command palette、toast。
- `docs/`：依赖、API 映射和 parity 验收记录。

## 验收边界

首版覆盖 opencode App 的 Web IDE 壳层、工作区/会话入口、session toolbar、composer、RunEvent message reducer、permission/question/todo/diff/terminal 面板和 settings/provider 展示入口。真实三服务联调需要同时启动 `test-agent-app`、opencode server 与本工程。
