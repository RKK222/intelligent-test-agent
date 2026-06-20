# 前端部署说明

## 部署边界

默认平台前端是 `frontend/apps/agent-web`。`frontend-opencode` 是独立的 Vue/TypeScript/Vite opencode IDE App 复刻工程，需要按该目录 README 单独构建和部署；后端 `test-agent-app`、PostgreSQL、Redis 和 opencode server 都是外部服务。`frontend/interaction-visual-demo` 和 `opencode-source/` 不纳入部署构建。

## 构建

在 `frontend/` 目录执行：

```bash
corepack pnpm install --frozen-lockfile
corepack pnpm build
```

`apps/agent-web` 使用 Next.js 生产构建。若启用 `output: 'standalone'`（见 `apps/agent-web/next.config.ts`），构建产物在 `apps/agent-web/.next/standalone/`，可独立运行无需完整 `node_modules`；静态资源在 `apps/agent-web/.next/static/` 和 `public/`，需一同部署。

`frontend-opencode` 使用 Vite 生产构建：

```bash
cd frontend-opencode
corepack pnpm install --frozen-lockfile
corepack pnpm build
```

构建产物在 `frontend-opencode/dist/`，可由任意静态服务器托管；反向代理需把 `/api`、RunEvent SSE 和 PTY WebSocket 路径转发到 `test-agent-app`。

## 环境变量

前端运行时需要：

```bash
NEXT_PUBLIC_API_BASE_URL=https://<backend-host>   # backend-api 的统一 base URL
VITE_TEST_AGENT_API_BASE_URL=https://<backend-host> # frontend-opencode 可选；同域部署可留空走 /api
```

- `NEXT_PUBLIC_*` 变量在构建时注入，变更需重新构建。
- 前端不得把密钥写入源码、`localStorage` 或构建产物；`TEST_AGENT_API_TOKEN` 等 Bearer token 由前端通过受控方式获取并经 `backend-api` 携带，不固化在构建环境。
- 本地开发默认 `NEXT_PUBLIC_API_BASE_URL` 指向 `http://127.0.0.1:8080`（见 `frontend/README.md`）。

## 运行

standalone 产物：

```bash
NODE_ENV=production \
NEXT_PUBLIC_API_BASE_URL=https://<backend-host> \
node apps/agent-web/.next/standalone/apps/agent-web/server.js
```

默认平台前端端口由对应 dev server 决定；`frontend-opencode` 本地 Vite dev server 可通过 `corepack pnpm dev` 启动，生产静态托管时由外部 Web server 决定监听端口。

## 反向代理与 CORS

- 前端与后端通常分属不同 origin，后端必须通过 `TEST_AGENT_CORS_ALLOWED_ORIGINS` 显式声明前端 origin（见 `docs/deployment/backend.md`）。
- 若通过同域反代把 `/api` 转发到 `test-agent-app`，可避免跨域，但仍需保证后端 CORS 配置与实际访问 origin 一致。
- SSE（`text/event-stream`）和 PTY WebSocket 升级路径需在反代层禁用缓冲、支持长连接和 `Upgrade` 头。

## 本地联调

本地三服务联调见 `frontend/README.md`：分别启动 `test-agent-app`（local profile）、`opencode serve` 和 `corepack pnpm dev`，或用 `./restart-dev-services.sh` 一键重启。`deploy/local/docker-compose.yml` 仅作为个人离线开发备用入口。
