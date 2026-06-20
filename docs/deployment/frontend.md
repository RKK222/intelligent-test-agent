# 前端部署说明

## 部署边界

前端是 `frontend/apps/agent-web` 这个 Next.js 应用（Next.js 16、React 19）。生产部署只构建并运行该应用；后端 `test-agent-app`、PostgreSQL、Redis 和 opencode server 都是外部服务，前端通过环境变量注入后端地址。`frontend/interaction-visual-demo`、顶层 `frontend-opencode` 和 `opencode-source/` 不纳入构建。

## 构建

在 `frontend/` 目录执行：

```bash
corepack pnpm install --frozen-lockfile
corepack pnpm build
```

`apps/agent-web` 使用 Next.js 生产构建。若启用 `output: 'standalone'`（见 `apps/agent-web/next.config.ts`），构建产物在 `apps/agent-web/.next/standalone/`，可独立运行无需完整 `node_modules`；静态资源在 `apps/agent-web/.next/static/` 和 `public/`，需一同部署。

## 环境变量

前端运行时需要：

```bash
NEXT_PUBLIC_API_BASE_URL=https://<backend-host>   # backend-api 的统一 base URL
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

默认监听 Next.js 端口（本地开发 3000）。生产可通过 `PORT` 或反向代理暴露。

## 反向代理与 CORS

- 前端与后端通常分属不同 origin，后端必须通过 `TEST_AGENT_CORS_ALLOWED_ORIGINS` 显式声明前端 origin（见 `docs/deployment/backend.md`）。
- 若通过同域反代把 `/api` 转发到 `test-agent-app`，可避免跨域，但仍需保证后端 CORS 配置与实际访问 origin 一致。
- SSE（`text/event-stream`）和 PTY WebSocket 升级路径需在反代层禁用缓冲、支持长连接和 `Upgrade` 头。

## 本地联调

本地三服务联调见 `frontend/README.md`：分别启动 `test-agent-app`（local profile）、`opencode serve` 和 `corepack pnpm dev`，或用 `./restart-dev-services.sh` 一键重启。`deploy/local/docker-compose.yml` 仅作为个人离线开发备用入口。
