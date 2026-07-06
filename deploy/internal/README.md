# 企业内 Docker 部署文件

本目录提供企业内部署文件：后端按 jar 直接部署，Nginx 按实体服务部署并托管前端 `dist/`，Docker Compose 只负责 2 个 `opencode-worker` 容器。

## 端口约束

Java 后端创建用户 opencode 进程时，会从 manager 上报的 `portStart..portEnd` 里选择端口，并用 `TEST_AGENT_SERVER_ADVERTISED_HOST/.serverhost + port` 生成 `baseUrl`。当前协议没有独立的 `containerPort` 和 `publishedPort` 字段。

因此 `opencode-worker` 的端口池必须就是宿主机发布端口：

- `OPENCODE_MANAGER_PORT_START/END` 写宿主机可访问端口。
- Compose 的 `ports` 必须保持 `hostPort:containerPort` 数值一致，例如 `4096-4105:4096-4105`。
- 不要写 `14096:4096` 这类内外不一致映射，否则 Java 会生成错误的 `baseUrl`。

每个 worker 容器内只有 1 个 `opencode-manager run` 常驻进程；manager 按端口池动态启动 0..N 个 `opencode serve` 子进程。

## Java 直接部署前提

两路 Java 后端示例：

```bash
server.port=8080
SPRING_PROFILES_ACTIVE=prod
TEST_AGENT_DEPLOYMENT_MODE=internal
TEST_AGENT_SERVER_ADVERTISED_HOST=<host-ip-or-dns>
TEST_AGENT_OPENCODE_MANAGER_TOKEN=<same-manager-token>
TEST_AGENT_CORS_ALLOWED_ORIGINS=http://<gateway-host>
TEST_AGENT_RUN_EVENT_REDIS_BUS_ENABLED=true
TEST_AGENT_SERVER_BROADCAST_ENABLED=true
```

第二路 Java 使用不同 `server.port`，例如 `8081`。如果两路 Java 在同一台 Linux 上，保持相同 `TEST_AGENT_LINUX_SERVER_ID`；如果在不同服务器上，每台服务器使用自己的稳定 ID。Java 的 `SYS_DATA_ROOT_DIR` 需要与 worker 挂载的 `TEST_AGENT_DATA_ROOT` 对齐，默认是 `/data/.testagent`，以便 worker 读取 `.serverid` 和 `.serverhost`。

## 打包交付物

在仓库根目录执行：

```bash
deploy/internal/package-release.sh
```

脚本默认读取 `deploy/internal/.env`；如果该文件不存在，则读取 `deploy/internal/env.example`。它会产出：

```text
deploy/internal/dist/backend/test-agent-app.jar
deploy/internal/dist/frontend/
deploy/internal/dist/test-agent-frontend-dist.tar.gz
deploy/internal/dist/programs/
deploy/internal/dist/test-agent-programs.tar.gz
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
/opt/test-agent/programs/bin/opencode-manager
/opt/test-agent/programs/opencode/bin/opencode
```

如果上述路径不存在或不可执行，则回退到镜像内置的：

```text
/usr/local/bin/opencode-manager
/usr/local/bin/opencode
```

目标机器首次部署可把交付包中的 `test-agent-programs.tar.gz` 解压到统一目录，例如：

```bash
mkdir -p /opt/test-agent
tar -C /opt/test-agent -xzf deploy/internal/dist/test-agent-programs.tar.gz
```

然后在 `deploy/internal/.env` 中配置：

```dotenv
TEST_AGENT_PROGRAM_ROOT=/opt/test-agent/programs
```

后续只升级 opencode 或 manager 时，可以只替换 `/opt/test-agent/programs` 下对应文件，再重启 worker：

```bash
cd deploy/internal
docker compose --env-file .env restart opencode-worker-1 opencode-worker-2
```

如果已有用户 `opencode serve` 子进程在运行，建议先通过平台运行管理停止或重启相关用户进程，避免旧子进程继续使用旧版本。

## 实体 Nginx 部署

实体 Nginx 至少需要做两件事：

- 静态资源根目录指向 `deploy/internal/dist/frontend/` 或解压后的 `test-agent-frontend-dist.tar.gz`。
- `/api`、SSE 和 WebSocket 请求反向代理到两个直接部署的 Java 后端。

`deploy/internal/nginx/` 下的配置文件只作为实体 Nginx 配置参考，不由 Docker Compose 启动。

示例模板 `deploy/internal/nginx/gateway.conf.template` 使用这些变量：

```bash
TEST_AGENT_FRONTEND_ROOT=/opt/test-agent/frontend
TEST_AGENT_BACKEND_1=127.0.0.1:8080
TEST_AGENT_BACKEND_2=127.0.0.1:8081
```

生成实体 Nginx 配置示例：

```bash
envsubst '${TEST_AGENT_FRONTEND_ROOT} ${TEST_AGENT_BACKEND_1} ${TEST_AGENT_BACKEND_2}' \
  < deploy/internal/nginx/gateway.conf.template \
  > /etc/nginx/conf.d/test-agent.conf
```

## 启动 opencode worker Compose

复制环境变量模板：

```bash
cp deploy/internal/env.example deploy/internal/.env
```

编辑 `deploy/internal/.env`，至少修改：

- `TEST_AGENT_OPENCODE_MANAGER_TOKEN`
- `TEST_AGENT_DATA_ROOT`
- `TEST_AGENT_PROGRAM_ROOT`
- 两个 worker 的端口池

启动：

```bash
cd deploy/internal
docker compose --env-file .env up -d
```

检查：

```bash
docker compose --env-file .env ps
```

## 运行时外部依赖

`opencode-worker` 镜像内已包含 `opencode-manager` 和 npm 安装的 `opencode-ai` CLI；外挂程序目录用于后续小版本更新。目标环境仍必须提供 PostgreSQL、Redis、企业内模型服务、Git/SSH 网络和 Java 后端所需密钥。`/data/.testagent/agent-opencode/.config/opencode/` 必须由超级管理员完成公共配置初始化且非空，否则 manager 会拒绝启动用户 opencode 进程。
