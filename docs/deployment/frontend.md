# 前端部署说明

## 部署边界

默认平台前端是 `frontend/apps/agent-web`，基于 Vue 3 + Vite SPA。`frontend-opencode` 是独立的 Vue/TypeScript/Vite opencode IDE App 复刻工程，需要按该目录 README 单独构建和部署；后端 `test-agent-app`、PostgreSQL、Redis 和 opencode server 都是外部服务。`frontend/interaction-visual-demo` 和 `opencode-source/` 不纳入部署构建。

## 构建

在 `frontend/` 目录执行：

```bash
corepack pnpm install --frozen-lockfile
corepack pnpm build
```

`apps/agent-web` 使用 Vite 生产构建，产物输出到 `apps/agent-web/dist/`：纯静态 HTML/JS/CSS，可由任意静态服务器或反向代理托管，无需 Node 运行时。

每次构建会按 `Asia/Shanghai` 把构建时刻固化为 `VyyyyMMdd.HHmmss`，设置弹窗左侧导航底部可核对。该值不从 Nginx 或运行时环境读取，刷新页面、reload Nginx 不会变化；必须重新构建并替换静态产物才会更新。

企业内当前部署不单独手工执行本节命令，统一使用 `deploy/internal/package-release.sh --env-file /data/testagent/config/docker.env` 生成 `test-agent-frontend-dist.tar.gz` 和完整 `test-agent-internal-release.zip`。部署入口见 `deploy/internal/README.md`：单后台按 `deploy/internal/SINGLE-BACKEND.md`，多后台按 `deploy/internal/MULTI-BACKEND.md`。两种模式都只在 `122.233.30.2:/data/testagent/frontend` 部署一份静态资源；`/data/testagent/config/nginx.env` 分别声明单个或多个 Java endpoint，前端部署脚本自动渲染、校验和 reload 实体 Nginx。多后台的 PTY、文件和 Agent 配置进度 WebSocket 都使用 ticket 签发 Java 的绝对地址，浏览器网段必须能访问每台 Java `:8080`。

企业内完整 ZIP 和 SHA-256 校验文件统一上传到每台目标服务器的 `/data/0709/`，固定为 `test-agent-internal-release.zip` 和 `test-agent-internal-release.zip.sha256`；部署前在该目录执行 `sha256sum -c test-agent-internal-release.zip.sha256`。如果后端服务器能免密直连前端服务器，可在后端执行 `/data/testagent/deploy/internal/deploy-internal-release.sh --archive /data/0709/test-agent-internal-release.zip`，脚本会用 `scp` 分发前端包到 `122.233.30.2`。如果现场统一登录策略导致 `Permission denied (publickey,gssapi-keyex,gssapi-with-mic)`，把同一份 ZIP 和校验文件放到 `122.233.30.2:/data/0709/`，在前端机本地执行 `deploy/internal/deploy-internal-frontend.sh --archive /data/0709/test-agent-internal-release.zip`，后端节点部署时统一加 `--skip-frontend`。`/data/0709/` 只存放上传交付物，前端静态资源仍安装到 `/data/testagent/frontend`。

`frontend-opencode` 使用 Vite 生产构建：

```bash
cd frontend-opencode
corepack pnpm install --frozen-lockfile
corepack pnpm build
```

构建产物在 `frontend-opencode/dist/`，可由任意静态服务器托管；反向代理需把 `/api`、RunEvent SSE 和 PTY WebSocket 路径转发到 `test-agent-app`。

`frontend-opencode` 的真实联调使用独立 Playwright 配置：

```bash
cd frontend-opencode
FRONTEND_OPENCODE_REAL_API_BASE_URL=http://127.0.0.1:8080 corepack pnpm e2e:real
```

该命令要求 `test-agent-app` 和 opencode runtime 已经可用；Vite 只代理平台 `/api`，浏览器不会直连 opencode server。

## 环境变量

前端运行时需要：

```bash
VITE_TEST_AGENT_API_BASE_URL=https://<backend-host>   # agent-web backend-api 的统一 base URL；同域部署可留空走 /api
```

- `VITE_` 前缀变量在构建时注入 `import.meta.env`，变更需重新构建。
- `VITE_TEST_AGENT_BUILD_VERSION` 由 Vite 配置内部生成，不是部署参数，不得在 `nginx.env` 或外部构建环境中覆盖。
- 前端不得把密钥写入源码、`localStorage` 或构建产物；`TEST_AGENT_API_TOKEN` 等 Bearer token 由前端通过受控方式获取并经 `backend-api` 携带，不固化在构建环境。
- 本地重启脚本未显式设置 `TEST_AGENT_BASE_URL` 时，会把自动探测到的后端内网地址注入为 `VITE_TEST_AGENT_API_BASE_URL`，避免通过局域网地址访问前端时仍请求浏览器本机 `127.0.0.1`。

## 运行

Vite 构建产物为纯静态 SPA，用任意静态服务器托管 `apps/agent-web/dist/` 即可，例如：

```bash
npx serve apps/agent-web/dist -l 3000
```

或通过反向代理把静态资源指向 `dist/`、把 `/api` 转发到 `test-agent-app`。SPA 路由（`/`、`/s/:sessionId`）需配置 history fallback，所有非静态路径回退到 `dist/index.html`。生产监听端口由外部 Web server 决定。

## 反向代理与 CORS

- 前端与后端通常分属不同 origin，后端必须通过 `TEST_AGENT_CORS_ALLOWED_ORIGINS` 显式声明前端 origin（见 `docs/deployment/backend.md`）。
- 若通过同域反代把 `/api` 转发到 `test-agent-app`，可避免跨域，但仍需保证后端 CORS 配置与实际访问 origin 一致。
- SSE（`text/event-stream`）和 PTY WebSocket 升级路径需在反代层禁用缓冲、支持长连接和 `Upgrade` 头。

## 本地联调

本地三服务联调见 `frontend/README.md`：默认用 `./restart-dev-services.sh` 或 Windows PowerShell 下的 `.\restart-dev-services.ps1` 读取 `.env.test` 并以 `test` profile 一键重启；个人离线开发也可分别启动 `test-agent-app`（local profile）、`opencode serve` 和 `corepack pnpm dev`，备用依赖通过本地开发脚本启动。
