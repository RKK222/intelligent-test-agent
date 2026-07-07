# 企业内 Docker 部署文件

本目录提供企业内部署文件，默认按单服务器一套服务部署：1 个实体 Nginx、1 份前端 `dist/`、1 个直接运行的 Java 后端、1 个 `opencode-worker` 容器。Docker Compose 只负责启动这个 `opencode-worker` 容器。

企业部署根目录统一使用 `/data/testagent`，建议目录规划如下：

```text
/data/testagent/
  data/       Java 的 SYS_DATA_ROOT_DIR，也是 worker 的数据挂载目录
  frontend/   前端 dist 解压目录，供实体 Nginx 托管
  programs/   外挂 opencode-manager 和 opencode CLI
  dist/       打包脚本输出的 jar、前端包、程序包和镜像 tar
```

## 当前三机部署规划

当前企业内部署按以下服务器拆分：

| 角色 | 地址 | 部署内容 |
|---|---|---|
| 前端入口 | `122.233.30.2` | 实体 Nginx，托管 `/data/testagent/frontend`，把 `/api` 反向代理到 `122.233.30.4:8080`。 |
| 后端与 worker | `122.233.30.4` | JDK 21 直接运行 `test-agent-app.jar`，Docker Compose 运行 `opencode-worker`。 |
| Redis | `122.233.30.20` | Redis 外部依赖，Java 后端连接该地址。 |
| PostgreSQL | 待定 | 确认后写入后端外置配置 `TEST_AGENT_DB_URL/USERNAME/PASSWORD`。 |

外置配置文件建议固定放在：

```text
/data/testagent/config/
  backend.env     Java 后端运行环境变量，模板见 deploy/internal/backend.env.example
  docker.env      opencode-worker Compose 和打包环境变量，模板见 deploy/internal/env.example
```

其中 `docker.env` 里的 `VITE_TEST_AGENT_API_BASE_URL` 已按当前前端入口写为 `http://122.233.30.2`，`TEST_AGENT_BACKEND` 已按当前后端写为 `122.233.30.4:8080`。

## 端口约束

Java 后端创建用户 opencode 进程时，会从 manager 上报的 `portStart..portEnd` 里选择端口，并用 `TEST_AGENT_SERVER_ADVERTISED_HOST/.serverhost + port` 生成 `baseUrl`。当前协议没有独立的 `containerPort` 和 `publishedPort` 字段。

因此 `opencode-worker` 的端口池必须就是宿主机发布端口：

- `OPENCODE_MANAGER_PORT_START/END` 写宿主机可访问端口。
- Compose 的 `ports` 必须保持 `hostPort:containerPort` 数值一致，例如 `4096-4105:4096-4105`。
- 不要写 `14096:4096` 这类内外不一致映射，否则 Java 会生成错误的 `baseUrl`。

每个 worker 容器内只有 1 个 `opencode-manager run` 常驻进程；manager 按端口池动态启动 0..N 个 `opencode serve` 子进程。

## Java 直接部署前提

单服务器只启动 1 个 Java 后端。建议 Java 监听本机 `8080`，Nginx 监听对外入口端口，例如 `80`：

```bash
server.port=8080
SPRING_PROFILES_ACTIVE=prod
TEST_AGENT_DEPLOYMENT_MODE=internal
TEST_AGENT_SERVER_ADVERTISED_HOST=<host-ip-or-dns>
TEST_AGENT_OPENCODE_MANAGER_TOKEN=<same-manager-token>
TEST_AGENT_CORS_ALLOWED_ORIGINS=http://<gateway-host>
TEST_AGENT_RUN_EVENT_REDIS_BUS_ENABLED=true
TEST_AGENT_SERVER_BROADCAST_ENABLED=true
TEST_AGENT_MODEL_CATALOG_SOURCE=internal
TEST_AGENT_INTERNAL_DEFAULT_MODEL=Qwen3.6-35B-A3B
TEST_AGENT_ICBC_OPENAI_BASE_URL=http://ai-code.sdc.icbc:9070/icbc/jdt/model/api/openai/v1
TEST_AGENT_ICBC_OPENAI_TOKEN_ENV=ICBC_OPENAI_AUTH_TOKEN
ICBC_OPENAI_AUTH_TOKEN=<icbc-openai-token>
TEST_AGENT_ICBC_OPENAI_AUTH_MODE=bearer
TEST_AGENT_ICBC_OPENAI_UCID_HEADER_NAME=ucid
```

当前三机部署时，后端服务器 `122.233.30.4` 推荐把 Java 配置单独放到 `/data/testagent/config/backend.env`，从仓库模板复制后改 PostgreSQL、token 和模型密钥：

```bash
mkdir -p /data/testagent/config /data/testagent/data
cp deploy/internal/backend.env.example /data/testagent/config/backend.env
vi /data/testagent/config/backend.env
```

启动 Java 时加载该外置配置：

```bash
set -a
. /data/testagent/config/backend.env
set +a
java -jar /data/testagent/dist/backend/test-agent-app.jar
```

如果使用 systemd，`EnvironmentFile=/data/testagent/config/backend.env` 即可，不需要把环境变量写进服务文件。

Java 的 `SYS_DATA_ROOT_DIR` 需要与 worker 挂载的 `TEST_AGENT_DATA_ROOT` 对齐，企业内默认是 `/data/testagent/data`，以便 worker 读取 `.serverid` 和 `.serverhost`。如果数据库通用参数仍是 Linux 默认 `/data/.testagent`，部署时需要在系统管理通用参数中把 Linux 平台 `SYS_DATA_ROOT_DIR` 改为 `/data/testagent/data`。如果后续扩成多服务器部署，每台服务器仍按“一台服务器一套 Nginx、前端、Java、worker”的方式独立配置。

## 打包交付物

在仓库根目录执行：

```bash
deploy/internal/package-release.sh --env-file /data/testagent/config/docker.env
```

脚本默认读取 `deploy/internal/.env`；如果该文件不存在，则读取 `deploy/internal/env.example`。当前企业部署建议显式传入外置 `/data/testagent/config/docker.env`，避免打包机和目标服务器配置不一致。它会产出：

```text
/data/testagent/dist/backend/test-agent-app.jar
/data/testagent/dist/frontend/
/data/testagent/dist/test-agent-frontend-dist.tar.gz
/data/testagent/dist/programs/
/data/testagent/dist/test-agent-programs.tar.gz
test-agent-opencode-worker_internal-linux-amd64.tar
```

也就是说：后端 jar 和前端 dist 会随打包一起出来；前端不做业务镜像，实体 Nginx 直接托管 `dist/frontend`。
第一版 `opencode-worker` 镜像里仍内置 `opencode-manager` 和 `opencode-ai` CLI；同时脚本会把这两个程序导出到 `dist/programs/`，Compose 默认把该目录挂进 worker，运行时优先使用外挂程序，找不到时才回退镜像内置程序。

只打某一类交付物：

```bash
deploy/internal/package-release.sh --backend-only
deploy/internal/package-release.sh --frontend-only
deploy/internal/package-release.sh --opencode-only
```

opencode worker 镜像也可以手工执行：

```bash
docker buildx build \
  --platform linux/amd64 \
  -f deploy/internal/opencode-worker.Dockerfile \
  -t test-agent-opencode-worker:internal \
  --load \
  .
```

离线交付时导出 tar：

```bash
docker save -o test-agent-opencode-worker-internal-amd64.tar test-agent-opencode-worker:internal
```

目标机器导入：

```bash
docker load -i test-agent-opencode-worker-internal-amd64.tar
```

## opencode 程序外挂升级

worker 容器启动时按以下优先级选择程序：

```text
/data/testagent/programs/bin/opencode-manager
/data/testagent/programs/opencode/bin/opencode
```

如果上述路径不存在或不可执行，则回退到镜像内置的：

```text
/usr/local/bin/opencode-manager
/usr/local/bin/opencode
```

目标机器首次部署可把交付包中的 `test-agent-programs.tar.gz` 解压到统一目录，例如：

```bash
mkdir -p /data/testagent
tar -C /data/testagent -xzf /data/testagent/dist/test-agent-programs.tar.gz
```

然后在 `deploy/internal/.env` 中配置：

```dotenv
TEST_AGENT_PROGRAM_ROOT=/data/testagent/programs
```

后续只升级 opencode 或 manager 时，可以只替换 `/data/testagent/programs` 下对应文件，再重启 worker：

```bash
cd deploy/internal
docker compose --env-file .env restart opencode-worker
```

如果已有用户 `opencode serve` 子进程在运行，建议先通过平台运行管理停止或重启相关用户进程，避免旧子进程继续使用旧版本。

## 实体 Nginx 部署

实体 Nginx 至少需要做两件事：

- 静态资源根目录指向 `/data/testagent/frontend/` 或解压后的 `test-agent-frontend-dist.tar.gz`。
- `/api`、SSE 和 WebSocket 请求反向代理到本机直接部署的 Java 后端。

`deploy/internal/nginx/` 下的配置文件只作为实体 Nginx 配置参考，不由 Docker Compose 启动。

示例模板 `deploy/internal/nginx/gateway.conf.template` 使用这些变量：

```bash
TEST_AGENT_FRONTEND_ROOT=/data/testagent/frontend
TEST_AGENT_NGINX_LISTEN_PORT=80
TEST_AGENT_BACKEND=122.233.30.4:8080
```

生成实体 Nginx 配置示例：

```bash
envsubst '${TEST_AGENT_NGINX_LISTEN_PORT} ${TEST_AGENT_FRONTEND_ROOT} ${TEST_AGENT_BACKEND}' \
  < deploy/internal/nginx/gateway.conf.template \
  > /etc/nginx/conf.d/test-agent.conf
```

## 启动 opencode worker Compose

复制环境变量模板：

```bash
mkdir -p /data/testagent/config
cp deploy/internal/env.example /data/testagent/config/docker.env
```

编辑 `/data/testagent/config/docker.env`，至少修改：

- `VITE_TEST_AGENT_API_BASE_URL`，当前为 `http://122.233.30.2`；不要追加 `/api`
- `ICBC_OPENAI_AUTH_TOKEN`，填企业内 `icbc-openai` 访问 token；不要提交真实 token
- `TEST_AGENT_OPENCODE_MANAGER_TOKEN`
- `TEST_AGENT_DATA_ROOT`
- `TEST_AGENT_PROGRAM_ROOT`
- worker 的端口池

启动：

```bash
cd deploy/internal
docker compose --env-file /data/testagent/config/docker.env up -d
```

检查：

```bash
docker compose --env-file /data/testagent/config/docker.env ps
```

## 运行时外部依赖

`opencode-worker` 镜像内已包含 `opencode-manager` 和 npm 安装的 `opencode-ai` CLI；外挂程序目录用于后续小版本更新。目标环境仍必须提供 PostgreSQL、Redis、企业内模型服务、Git/SSH 网络和 Java 后端所需密钥。`/data/testagent/data/agent-opencode/.config/opencode/` 必须由超级管理员完成公共配置初始化且非空，否则 manager 会拒绝启动用户 opencode 进程。
