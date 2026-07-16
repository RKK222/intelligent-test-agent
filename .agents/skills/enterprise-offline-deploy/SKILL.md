---
name: enterprise-offline-deploy
description: Use whenever the user asks about enterprise/internal/offline deployment, packaging on Mac, release artifacts, deploy/internal/package-release.sh, backend.env, docker.env, opencode-worker, opencode-manager, or how to deploy this project in a network-isolated enterprise environment. Always provide the full Mac packaging to offline enterprise deployment workflow and list which configuration files must be changed.
---

# 企业内离线部署说明

本技能用于 `/Users/kaka/Desktop/intelligent-test-agent` 的企业内部署问答。用户提到“企业内部署”“内网部署”“离线部署”“Mac 打包”“完全不能联网”“opencode worker”“opencode manager port”“backend.env”“docker.env”时必须使用。

## 固定前提

- 打包机是 Mac，允许联网，用来拉 Maven、pnpm、Docker base image、npm/opencode 包等构建依赖。
- 企业内部署环境完全不能联网，只能接收 Mac 打好的交付物。
- 企业内不使用 Docker Compose；`opencode-worker` 用 `deploy/internal/opencode-worker-docker.sh` 纯 Docker 命令管理。
- 企业内不要使用根目录 `.env.local`、`.env.test` 作为生产配置。
- Java 后端读取 `/data/testagent/config/backend.env`。
- worker/打包配置读取 `/data/testagent/config/docker.env`，模板来自 `deploy/internal/env.example`。
- Java 的 `SYS_DATA_ROOT_DIR` 必须与 worker 的 `TEST_AGENT_DATA_ROOT` 指向同一个宿主机目录，默认 `/data/testagent/data`。
- 新版不再配置 `OPENCODE_MANAGER_ID`、`OPENCODE_MANAGER_SERVER_IP_FILE`、`OPENCODE_MANAGER_LINUX_SERVER_ID`。
- 当前前端实体 Nginx 安装在 `/data/apps/nginx`；单后台现场先运行 `configure-single-deployment.sh frontend` 生成 `nginx.env`，不要用 PATH 中可能读取 `/root/conf/nginx.conf` 的其他 `nginx`。

## 每次回答必须包含

回答企业内部署问题时，不要只给单条命令。必须覆盖：

1. Mac 打包前提和打包命令。
2. 打包产物清单。
3. 每个产物传到企业内哪台服务器、哪个路径。
4. 企业内需要准备和修改的配置文件：`backend.env`、`docker.env`、必要时 `nginx.env` 或 Nginx conf。
5. 启动/升级顺序：先 Java，确认 `.serverid/.serverhost`，再 worker。
6. 验证命令和预期现象。
7. `opencode-manager` 端口或连接报错时的优先排查点。

## 标准目录

企业内统一使用：

```text
/data/testagent/
  config/
    backend.env
    docker.env
    nginx.env
  data/
  deploy/internal/
  dist/
  frontend/
  programs/
```

## Mac 打包命令

从 Mac 仓库根目录执行：

```bash
cd /Users/kaka/Desktop/intelligent-test-agent
deploy/internal/package-release.sh
```

本地 Mac 默认输出到：

```text
deploy/internal/dist/
```

如果需要指定输出目录：

```bash
deploy/internal/package-release.sh --output-dir /path/to/dist
```

## 打包产物

必须说明这些产物：

```text
deploy/internal/dist/backend/test-agent-app.jar
deploy/internal/dist/test-agent-frontend-dist.tar.gz
deploy/internal/dist/test-agent-programs.tar.gz
deploy/internal/dist/test-agent-opencode-worker_internal-linux-amd64.tar
deploy/internal/dist/frontend/
```

还要同步 `deploy/internal/` 目录到企业内，用于 Nginx 模板和 `opencode-worker-docker.sh`。

## 企业内配置文件

### `/data/testagent/config/backend.env`

这是 Java 后端配置。必须提醒用户至少检查：

```dotenv
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
TEST_AGENT_DEPLOYMENT_MODE=internal
TEST_AGENT_SERVER_ADVERTISED_HOST=<后端服务器IP或域名>
TEST_AGENT_LINUX_SERVER_ID=<稳定服务器ID>
SYS_DATA_ROOT_DIR=/data/testagent/data

TEST_AGENT_DB_URL=jdbc:postgresql://<pg-host>:<port>/<db>
TEST_AGENT_DB_USERNAME=<user>
TEST_AGENT_DB_PASSWORD=<password>

TEST_AGENT_REDIS_HOST=<redis-host>
TEST_AGENT_REDIS_PORT=6379
TEST_AGENT_REDIS_PASSWORD=

TEST_AGENT_CORS_ALLOWED_ORIGINS=http://<前端入口>
TEST_AGENT_API_TOKEN=
TEST_AGENT_OPENCODE_MANAGER_TOKEN=<manager-token>
TEST_AGENT_INTERNAL_PROXY_API_KEY=<random-internal-proxy-api-key>

TEST_AGENT_SERVER_BROADCAST_ENABLED=true
TEST_AGENT_MODEL_CATALOG_SOURCE=internal
```

### `/data/testagent/config/docker.env`

这是 worker 和打包配置。必须提醒用户至少检查：

```dotenv
TEST_AGENT_OPENCODE_MANAGER_TOKEN=<必须和backend.env一致>
TEST_AGENT_DATA_ROOT=/data/testagent/data
TEST_AGENT_PROGRAM_ROOT=/data/testagent/programs
TEST_AGENT_OPENCODE_WORKER_IMAGE=test-agent-opencode-worker:internal

OPENCODE_WORKER_BACKEND_PORT=8080
OPENCODE_WORKER_PORT_START=4096
OPENCODE_WORKER_PORT_END=4105

VITE_TEST_AGENT_API_BASE_URL=http://<前端入口>
TEST_AGENT_BACKEND=<后端服务器IP或域名>:8080
```

端口池必须是宿主机可访问端口，Docker 映射保持 `4096-4105:4096-4105` 这种内外一致形式，不要做 `14096:4096`。
`TEST_AGENT_INTERNAL_PROXY_API_KEY` 是 Java 内部模型代理鉴权 key，只配置在 `backend.env`，不要放到 `docker.env`；Java 会在启动用户 opencode server 时通过 manager command 注入给子进程。

### 单后台配置脚本

交付 ZIP 内的 `deploy/internal/configure-single-deployment.sh` 用于当前 `.2 + .114` 单后台现场：

```bash
# 122.233.30.114：保留当前数据库密码及 token，重建 backend.env/docker.env
bash deploy/internal/configure-single-deployment.sh backend

# 122.233.30.2：探测 /data/apps/nginx 实际 include 目录并重建 nginx.env
bash deploy/internal/configure-single-deployment.sh frontend --nginx-home /data/apps/nginx
```

后台角色要求现有 `backend.env` 中已有数据库密码和内部代理 key，且两份 env 的 manager token 一致；任一条件不满足时必须在写文件前失败。前端角色生成的 `nginx.env` 明确设置 `/data/apps/nginx/sbin/nginx`、prefix、主配置和 binary reload；`configure-nginx.sh` 仍保留 PATH nginx + systemd 作为其他标准安装环境的兼容兜底。

## 标准部署顺序

1. 前端服务器解压前端包到 `/data/testagent/frontend`，配置 Nginx，反代 `/api` 到 Java 后端。
2. 后端服务器放置 jar、programs 包、worker 镜像 tar、`deploy/internal/`。
3. 启动 Java 后端。
4. 确认 Java 写出：

```bash
cat /data/testagent/data/.serverid
cat /data/testagent/data/.serverhost
```

5. 再启动 worker：

```bash
docker load -i /data/testagent/dist/test-agent-opencode-worker_internal-linux-amd64.tar
tar -C /data/testagent -xzf /data/testagent/dist/test-agent-programs.tar.gz
cd /data/testagent/deploy/internal
./opencode-worker-docker.sh --env-file /data/testagent/config/docker.env restart
```

6. 验证：

```bash
curl -fsS http://<后端服务器>:8080/actuator/health
curl -fsS http://<后端服务器>:8080/actuator/health/readiness
curl -fsS http://<前端入口>/
docker logs --tail 120 test-agent-opencode-worker
```

worker 日志期望看到 `manager config update applied`。

## 常见问题提醒

- `opencode-manager` 报端口配置缺失：不要手工直接跑 `opencode-manager run`；生产用 `opencode-worker-docker.sh`。端口写在 `docker.env` 的 `OPENCODE_WORKER_PORT_START/END`。
- manager 等待 `.serverhost` 或连接旧 IP：检查 `backend.env` 的 `SYS_DATA_ROOT_DIR` 是否等于 `docker.env` 的 `TEST_AGENT_DATA_ROOT`，并确认 Java 已先启动并写出 `.serverid/.serverhost`。
- 数据库通用参数 `SYS_DATA_ROOT_DIR` 仍是 Linux 默认 `/data/.testagent`：企业内部署需要在系统管理通用参数或数据库中改为 `/data/testagent/data`，否则 Java/worker 共享目录会错位。
- 公共配置目录未初始化：超级管理员进入“系统管理 -> 配置管理 -> opencode公共配置管理”初始化，确保 `OPENCODE_PUBLIC_CONFIG_DIR` 指向的目录存在且非空。
- 企业内不能联网：不要在企业内执行需要拉依赖的构建命令；企业内只执行 `docker load`、解压、启动 Java、启动 worker、Nginx reload。

## 回答风格

优先给可复制命令和明确检查项。涉及 token、数据库密码、模型密钥时使用占位符，不要要求用户把真实密钥发回聊天。
