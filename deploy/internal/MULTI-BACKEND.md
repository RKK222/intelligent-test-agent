# 企业内多后台部署

当前代码的普通 HTTP、RunEvent SSE、用户 OpenCode 进程路由、内部模型代理和受控 WebSocket 支持两个或更多后台节点。任意入口 Java 收到需要归属路由的请求后，会根据公共路由程序选择目标 Java，再由目标 Java 控制本机 manager。PTY 和 Agent 配置进度 ticket 响应返回签发 Java 的绝对 WebSocket 地址；Workspace/Agent 配置文件 route 原本就返回目标 Java 地址，因此一次性 ticket 的签发和消费不会跨 JVM，也不依赖 Nginx sticky。

本文用两个后台举例：

| 角色 | 地址/身份 |
|---|---|
| 前端实体 Nginx | `122.233.30.2` |
| 后台 A + worker A | `122.233.30.4` / `test-agent-backend-122-233-30-4` |
| 后台 B + worker B | `122.233.30.114` / `test-agent-backend-122-233-30-114` |
| Redis | `122.233.30.20:6379` |
| PostgreSQL | `122.233.30.147:5432/postgres` |
| 行内模型 | `ai-code.sdc.icbc:9070` |

## 1. 正式拓扑

```text
浏览器 -> 122.233.30.2 Nginx
                    |-> 122.233.30.4:8080   Java A -> 本机 worker A/OpenCode A
                    `-> 122.233.30.114:8080 Java B -> 本机 worker B/OpenCode B

Java A <---------------- 互访 8080 ----------------> Java B
   |                                                     |
   +---------------- 共享 PostgreSQL / Redis ------------+
   |                                                     |
   `-> ai-code.sdc.icbc:9070          ai-code.sdc.icbc:9070 <-'
```

必须同时满足：

1. 所有 Java 使用同一版本 JAR 和 `backend/lib/`，共享同一 PostgreSQL 和 Redis。
2. 每台物理服务器使用唯一、长期稳定的 `TEST_AGENT_LINUX_SERVER_ID`。
3. 每台后台只运行一个本机 worker；worker 只连接本机 `.serverhost:8080`。
4. 各节点 `/data/testagent/data` 是本机目录，不做跨服务器共享挂载。
5. 所有后台之间能双向访问对方声明的 `TEST_AGENT_SERVER_ADVERTISED_HOST:8080`。
6. Nginx 能访问全部后台 `:8080`；每台 Java 宿主机都能访问 PostgreSQL、Redis 和 `ai-code.sdc.icbc:9070`。
7. 所有后台启用相同的服务器广播 channel。
8. 每台后台都要初始化本服务器公共 OpenCode 配置。

不要照搬旧双后台文档中的 RunEvent Redis bus 开关；当前代码已经删除该参数。运行态和跨 Java 路由使用已有的 Redis 存储、服务器快照及公共转发程序。

## 2. 网络和端口

| 来源 | 目标 | 用途 |
|---|---|---|
| `.2` | `.4:8080`、`.114:8080` | Nginx 负载均衡 |
| 企业浏览器网段 | `.4:8080`、`.114:8080` | PTY、Workspace/Agent 文件和 Agent 配置进度 WebSocket 按 ticket 签发节点直连 |
| `.4` | `.114:8080` | Java A 转发到 Java B |
| `.114` | `.4:8080` | Java B 转发到 Java A |
| 每台 worker 容器 | 本机 Java `:8080` | manager WebSocket、内部模型代理 |
| 每台后台 | PostgreSQL、Redis | 共享持久化和运行态 |
| 每台后台 | `ai-code.sdc.icbc:9070` | 行内模型调用 |
| Java 后台 | 每台后台 `4096-4105` | 访问本机或目标服务器上的用户 OpenCode 进程；浏览器不直连这些端口 |

两个服务器可以重复使用 `4096-4105`，因为 IP 不同；同一台服务器的宿主机和容器端口必须同号。正式部署不使用 `--network host`、`19070` relay 或额外 model relay。

部署前验证：

```bash
# 在 .4
curl -fsS http://122.233.30.114:8080/actuator/health
nc -vz 122.233.30.20 6379
nc -vz ai-code.sdc.icbc 9070

# 在 .114
curl -fsS http://122.233.30.4:8080/actuator/health
nc -vz 122.233.30.20 6379
nc -vz ai-code.sdc.icbc 9070
```

## 3. Mac 打包与分发

只构建一份版本，禁止两个后台分别打包：

```bash
cd /Users/kaka/Desktop/intelligent-test-agent
deploy/internal/package-release.sh --output-dir deploy/internal/dist
```

交付：

```text
deploy/internal/dist/test-agent-internal-release.zip
deploy/internal/dist/test-agent-internal-release.zip.sha256
```

将同一份 zip 和校验文件复制到：

```text
122.233.30.2:/data/0709/
122.233.30.4:/data/0709/
122.233.30.114:/data/0709/
```

每台都执行：

```bash
cd /data/0709
sha256sum -c test-agent-internal-release.zip.sha256
unzip -t test-agent-internal-release.zip
```

## 4. 每个后台的 backend.env

以下两份都是可整文件替换的完整配置。IP、端口、路径、超时和安全默认值已经填好；两台机器必须把 3 个同名 `REPLACE_...` 替换为同一组现场值。模板按 Redis 无密码、平台 API token 为空填写；如果现网这两项非空，必须保留现网值。替换前先备份原文件：

```bash
install -d -m 0755 /data/testagent/config
cp -a /data/testagent/config/backend.env \
  /data/testagent/config/backend.env.bak.$(date +%Y%m%d%H%M%S) 2>/dev/null || true
```

后台 A `.4` 的 `/data/testagent/config/backend.env` 全文：

```dotenv
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
TEST_AGENT_DEPLOYMENT_MODE=internal
TEST_AGENT_SERVER_ADVERTISED_HOST=122.233.30.4
TEST_AGENT_LINUX_SERVER_ID=test-agent-backend-122-233-30-4
SYS_DATA_ROOT_DIR=/data/testagent/data

TEST_AGENT_DB_URL=jdbc:postgresql://122.233.30.147:5432/postgres
TEST_AGENT_DB_USERNAME=postgres
TEST_AGENT_DB_PASSWORD=REPLACE_PRODUCTION_DB_PASSWORD
TEST_AGENT_DB_DRIVER_CLASS_NAME=org.postgresql.Driver

TEST_AGENT_REDIS_HOST=122.233.30.20
TEST_AGENT_REDIS_PORT=6379
TEST_AGENT_REDIS_PASSWORD=
TEST_AGENT_REDIS_TIMEOUT=1s

TEST_AGENT_CORS_ALLOWED_ORIGINS=http://122.233.30.2
TEST_AGENT_API_TOKEN=
TEST_AGENT_OPENCODE_MANAGER_TOKEN=REPLACE_MANAGER_TOKEN
TEST_AGENT_INTERNAL_PROXY_API_KEY=REPLACE_INTERNAL_PROXY_API_KEY
TEST_AGENT_MODEL_CATALOG_SOURCE=internal

TEST_AGENT_SERVER_BROADCAST_ENABLED=true
TEST_AGENT_SERVER_BROADCAST_CHANNEL=test-agent:server-broadcast

TEST_AGENT_DB_POOL_INITIAL_SIZE=1
TEST_AGENT_DB_POOL_MIN_IDLE=1
TEST_AGENT_DB_POOL_MAX_ACTIVE=10
TEST_AGENT_DB_POOL_MAX_WAIT_MILLIS=30000
TEST_AGENT_DB_POOL_TEST_ON_BORROW=true

TEST_AGENT_RATE_LIMIT_ENABLED=false
TEST_AGENT_RATE_LIMIT_CAPACITY=120
TEST_AGENT_RATE_LIMIT_WINDOW=1m
TEST_AGENT_REDIS_SUMMARY_ENABLED=false
TEST_AGENT_REDIS_SUMMARY_ROLLOUT_PERCENTAGE=0
TEST_AGENT_LEGACY_RUN_WITHOUT_CONTEXT_ENABLED=true
TEST_AGENT_MAX_FILE_BYTES=1048576
TEST_AGENT_MAX_DIRECTORY_ENTRIES=1000

TEST_AGENT_BACKEND_HEARTBEAT_INTERVAL=5s
TEST_AGENT_BACKEND_STALE_AFTER=10s
TEST_AGENT_OPENCODE_MANAGER_COMMAND_TIMEOUT=10s
TEST_AGENT_BACKEND_DISCOVERY_LIMIT=100

TEST_AGENT_SCHEDULER_ENABLED=false
TEST_AGENT_SCHEDULER_SCAN_INTERVAL=30s
TEST_AGENT_SCHEDULER_DUE_TASK_LIMIT=50
TEST_AGENT_SCHEDULER_MANUAL_RUN_LIMIT=50
```

后台 B `.114` 的 `/data/testagent/config/backend.env` 全文：

```dotenv
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
TEST_AGENT_DEPLOYMENT_MODE=internal
TEST_AGENT_SERVER_ADVERTISED_HOST=122.233.30.114
TEST_AGENT_LINUX_SERVER_ID=test-agent-backend-122-233-30-114
SYS_DATA_ROOT_DIR=/data/testagent/data

TEST_AGENT_DB_URL=jdbc:postgresql://122.233.30.147:5432/postgres
TEST_AGENT_DB_USERNAME=postgres
TEST_AGENT_DB_PASSWORD=REPLACE_PRODUCTION_DB_PASSWORD
TEST_AGENT_DB_DRIVER_CLASS_NAME=org.postgresql.Driver

TEST_AGENT_REDIS_HOST=122.233.30.20
TEST_AGENT_REDIS_PORT=6379
TEST_AGENT_REDIS_PASSWORD=
TEST_AGENT_REDIS_TIMEOUT=1s

TEST_AGENT_CORS_ALLOWED_ORIGINS=http://122.233.30.2
TEST_AGENT_API_TOKEN=
TEST_AGENT_OPENCODE_MANAGER_TOKEN=REPLACE_MANAGER_TOKEN
TEST_AGENT_INTERNAL_PROXY_API_KEY=REPLACE_INTERNAL_PROXY_API_KEY
TEST_AGENT_MODEL_CATALOG_SOURCE=internal

TEST_AGENT_SERVER_BROADCAST_ENABLED=true
TEST_AGENT_SERVER_BROADCAST_CHANNEL=test-agent:server-broadcast

TEST_AGENT_DB_POOL_INITIAL_SIZE=1
TEST_AGENT_DB_POOL_MIN_IDLE=1
TEST_AGENT_DB_POOL_MAX_ACTIVE=10
TEST_AGENT_DB_POOL_MAX_WAIT_MILLIS=30000
TEST_AGENT_DB_POOL_TEST_ON_BORROW=true

TEST_AGENT_RATE_LIMIT_ENABLED=false
TEST_AGENT_RATE_LIMIT_CAPACITY=120
TEST_AGENT_RATE_LIMIT_WINDOW=1m
TEST_AGENT_REDIS_SUMMARY_ENABLED=false
TEST_AGENT_REDIS_SUMMARY_ROLLOUT_PERCENTAGE=0
TEST_AGENT_LEGACY_RUN_WITHOUT_CONTEXT_ENABLED=true
TEST_AGENT_MAX_FILE_BYTES=1048576
TEST_AGENT_MAX_DIRECTORY_ENTRIES=1000

TEST_AGENT_BACKEND_HEARTBEAT_INTERVAL=5s
TEST_AGENT_BACKEND_STALE_AFTER=10s
TEST_AGENT_OPENCODE_MANAGER_COMMAND_TIMEOUT=10s
TEST_AGENT_BACKEND_DISCOVERY_LIMIT=100

TEST_AGENT_SCHEDULER_ENABLED=false
TEST_AGENT_SCHEDULER_SCAN_INTERVAL=30s
TEST_AGENT_SCHEDULER_DUE_TASK_LIMIT=50
TEST_AGENT_SCHEDULER_MANUAL_RUN_LIMIT=50
```

保存后，两台都执行以下检查；命令必须无输出：

```bash
grep -n 'REPLACE_' /data/testagent/config/backend.env
```

集群内 manager token 和内部代理 key 使用同一组值，降低滚动部署误配风险；本机 `backend.env` 的 manager token 必须与本机 `docker.env` 完全一致。

## 5. 每个后台的 docker.env

`.4` 和 `.114` 都使用下面这份完整 `/data/testagent/config/docker.env`；只替换 `REPLACE_MANAGER_TOKEN`，并确保与本机 `backend.env` 完全相同：

```dotenv
TEST_AGENT_BASE_DIR=/data/testagent

TEST_AGENT_OPENCODE_MANAGER_TOKEN=REPLACE_MANAGER_TOKEN
TEST_AGENT_DATA_ROOT=/data/testagent/data
TEST_AGENT_PROGRAM_ROOT=/data/testagent/programs
TEST_AGENT_OPENCODE_WORKER_IMAGE=test-agent-opencode-worker:internal

VITE_TEST_AGENT_API_BASE_URL=http://122.233.30.2

OPENCODE_WORKER_BACKEND_PORT=8080
OPENCODE_WORKER_PORT_START=4096
OPENCODE_WORKER_PORT_END=4105

OPENCODE_ALLOWED_CORS=http://122.233.30.2
OPENCODE_MANAGER_HEARTBEAT_INTERVAL=5s
OPENCODE_MANAGER_RECONNECT_INTERVAL=10s

OPENCODE_VERSION=1.17.8
OPENCODE_SOURCE_COMMIT=11e47f91496005aab4d7c5a2d0a7da5d2651b4ac
OPENCODE_SOURCE_REPOSITORY=https://github.com/anomalyco/opencode.git
GO_IMAGE=golang@sha256:167053a2bb901972bf2c1611f8f52c44d5fe7e762e5cab213708d82c421614db
BUN_IMAGE=oven/bun@sha256:9dba1a1b43ce28c9d7931bfc4eb00feb63b0114720a0277a8f939ae4dfc9db6f
NODE_IMAGE=node@sha256:e24976116684e0fd211cbdb3c40fc9cb997565d063fb7fe656d2e2b603c5bb0a

NPM_REGISTRY=https://registry.npmmirror.com
COREPACK_NPM_REGISTRY=https://registry.npmmirror.com
GOPROXY=https://goproxy.cn,direct
DEBIAN_MIRROR=https://mirrors.tuna.tsinghua.edu.cn/debian
DEBIAN_SECURITY_MIRROR=https://mirrors.tuna.tsinghua.edu.cn/debian-security

TEST_AGENT_IMAGE_OUTPUT_DIR=/data/testagent/dist
```

当前 worker 不读取旧的 `TEST_AGENT_BACKEND`，而是读取本机 Java 写出的 `.serverhost` 并结合 `OPENCODE_WORKER_BACKEND_PORT` 建立 manager WebSocket，所以不要恢复旧变量。Nginx 使用前端服务器独立的 `nginx.env`，不在每台 worker 的 `docker.env` 中维护 upstream。

每台 Java 启动后，本机身份文件分别应为：

```text
# .4
/data/testagent/data/.serverid   = test-agent-backend-122-233-30-4
/data/testagent/data/.serverhost = 122.233.30.4

# .114
/data/testagent/data/.serverid   = test-agent-backend-122-233-30-114
/data/testagent/data/.serverhost = 122.233.30.114
```

## 6. 多后台 Nginx

在前端 `.2` 创建 `/data/testagent/config/nginx.env`：

```dotenv
TEST_AGENT_NGINX_MODE=multi
TEST_AGENT_NGINX_BACKENDS=122.233.30.4:8080,122.233.30.114:8080
TEST_AGENT_NGINX_LISTEN_PORT=80
TEST_AGENT_FRONTEND_ROOT=/data/testagent/frontend
TEST_AGENT_NGINX_CONF_PATH=/etc/nginx/conf.d/test-agent-gateway.conf
```

`TEST_AGENT_NGINX_CONF_PATH` 必须是当前 Nginx 主配置实际 include 的 `.conf` 文件。前端部署脚本统一调用 [configure-nginx.sh](configure-nginx.sh)，由同一个 [gateway.conf.template](nginx/gateway.conf.template) 生成 `least_conn` upstream、逐节点 `max_fails/fail_timeout`、WebSocket Upgrade、SSE 禁缓冲和长连接超时；配置失败自动恢复旧文件，不再手工维护另一份多节点 Nginx 配置。

普通 HTTP、RunEvent SSE 和模型请求不依赖 sticky session：用户进程归属由数据库 binding 和 Redis 服务器快照决定，入口 Java 会转发到进程所属服务器；长连接建立后由 Nginx 保持当前上游。一次性 WebSocket ticket 仍是 JVM 内存状态，但路由已经闭合：

- PTY ticket 请求按用户进程归属转发，响应返回实际签发 Java 的绝对 WebSocket 地址。
- Workspace/Agent 配置文件 route 返回目标 Java `baseUrl`，浏览器在目标 Java 申请 ticket 并连接同一 Java。
- Agent 配置进度 ticket 返回当前签发 Java 的绝对 WebSocket 地址；跨节点进度由既有服务器广播汇入。

因此不需要 `ip_hash`、cookie sticky、共享 ticket 或 Java 间文件/WebSocket 代理。浏览器网段必须能访问 `.4:8080` 和 `.114:8080`，两台 Java 的 `TEST_AGENT_CORS_ALLOWED_ORIGINS` 都必须包含 `http://122.233.30.2`；这是绝对 WebSocket 地址可用的网络前提。

## 7. 部署与启动顺序

前端 `.2` 只部署一次：

```bash
unzip -p /data/0709/test-agent-internal-release.zip \
  deploy/internal/deploy-internal-frontend.sh \
  > /tmp/deploy-internal-frontend.sh
bash /tmp/deploy-internal-frontend.sh \
  --archive /data/0709/test-agent-internal-release.zip \
  --validate-only
bash /tmp/deploy-internal-frontend.sh \
  --archive /data/0709/test-agent-internal-release.zip
```

后台 A `.4`：

```bash
unzip -p /data/0709/test-agent-internal-release.zip \
  deploy/internal/deploy-internal-release.sh \
  > /tmp/deploy-internal-release.sh
bash /tmp/deploy-internal-release.sh \
  --archive /data/0709/test-agent-internal-release.zip \
  --backend-host 122.233.30.4 \
  --skip-frontend \
  --validate-only
bash /tmp/deploy-internal-release.sh \
  --archive /data/0709/test-agent-internal-release.zip \
  --backend-host 122.233.30.4 \
  --skip-frontend
```

后台 B `.114`：

```bash
unzip -p /data/0709/test-agent-internal-release.zip \
  deploy/internal/deploy-internal-release.sh \
  > /tmp/deploy-internal-release.sh
bash /tmp/deploy-internal-release.sh \
  --archive /data/0709/test-agent-internal-release.zip \
  --backend-host 122.233.30.114 \
  --skip-frontend \
  --validate-only
bash /tmp/deploy-internal-release.sh \
  --archive /data/0709/test-agent-internal-release.zip \
  --backend-host 122.233.30.114 \
  --skip-frontend
```

首次部署时先让两台 Java 都通过 health/readiness 并写对身份文件，再分别启动本机 worker。升级时可以逐台滚动，但每一台内部仍必须按：

```text
替换 JAR/lib -> 重启 Java -> 检查 .serverid/.serverhost -> 重启本机 worker
```

不得清理 `/data/testagent/data`，也不要把 A 的数据目录复制覆盖到 B。

每台后台部署脚本都会校验已有 systemd unit 的 JAR/env 指向；`systemctl stop` 后若 `8080` 仍被同一路径的旧 `test-agent-app.jar` 占用，会安全终止该遗留进程，其他程序占用则拒绝误杀。新 Java health/readiness 通过后还会核对 systemd `MainPID` 正是 `8080` 监听者，避免滚动升级时误连旧手工进程。

## 8. 公共配置和模型

超级管理员进入“系统管理 → 配置管理 → opencode 公共配置管理”，分别初始化：

```text
test-agent-backend-122-233-30-4
test-agent-backend-122-233-30-114
```

两个服务器使用同一版本的 [opencode.jsonc.example](opencode.jsonc.example)，模型为：

在任意一台已收到交付包的后台导出完整 JSONC，分别粘贴到两个 `linuxServerId` 的公共配置编辑器；两个节点内容必须一致：

```bash
unzip -p /data/0709/test-agent-internal-release.zip \
  deploy/internal/opencode.jsonc.example \
  >/tmp/opencode.jsonc
sed -n '1,220p' /tmp/opencode.jsonc
```

JSONC 中三个 `{env:...}` 引用必须原样保留，由各用户所属 Java 动态注入，不要替换成固定 UCID 或固定代理地址。

```text
icbc-qwen/Qwen3.6-27B                 -> qwen-prod
icbc-deepseek/DeepSeek-V4-Flash-W8A8 -> deepseek-prod
```

共享数据库的“内部模型供应商”页面完整填写如下；只需把两个 token 替换为现场已有值：

| Provider ID | 名称 | Base URL | Token | 启用 | 排序 |
|---|---|---|---|---|---:|
| `qwen-prod` | `企业通义` | `http://ai-code.sdc.icbc:9070/icbc/jdt/model/api/openai/v1` | `REPLACE_QWEN_UPSTREAM_TOKEN` | 是 | `1` |
| `deepseek-prod` | `企业 DeepSeek` | `http://ai-code.sdc.icbc:9070/icbc/jdt/model/api/openai/v1` | `REPLACE_DEEPSEEK_UPSTREAM_TOKEN` | 是 | `2` |

公共配置必须包含 `includeUsage=false`，避免 OpenCode 1.17.8 默认添加行内接口不支持的 `stream_options.include_usage`。供应商地址、启用状态和上游 token 来自共享数据库：`qwen-prod`、`deepseek-prod` 均启用，`baseUrl` 为 `http://ai-code.sdc.icbc:9070/icbc/jdt/model/api/openai/v1`。公共配置工作树位于各后台本机，因此数据库已经配置供应商并不等于另一台服务器已经初始化公共配置。

`icbc-qwen` / `icbc-deepseek` 是 OpenCode provider key；`qwen-prod` / `deepseek-prod` 是数据库和 `X-ICBC-Model-Provider` 使用的 Java 路由键，不能混用。上游 token 只在共享数据库维护，由 Java 以 `Authorization: Bearer <token>` 注入。用户 UCID 由拥有该用户进程的 Java 从用户表读取并逐进程注入，不使用全局 UCID env 文件。

变更生效规则：

1. 修改某台服务器公共 `opencode.jsonc` 后，只重启该服务器上已有的用户 OpenCode 进程；新进程直接读取。
2. 修改共享数据库供应商或 token 后点击“刷新 Java 内存”；广播启用时所有 Java 会分别重载同一数据库快照。
3. 在两台后台分别查询 refresh-status，必须都包含 `qwen-prod`、`deepseek-prod` 且 `tokenConfigured=true`；广播失败时逐台重启 Java 重新加载。
4. 只重启 Java 不会让已经运行的用户 OpenCode 重新读取公共配置或重新注入 UCID，涉及进程配置时仍要重启对应用户进程。

## 9. 集群验收

两台后台分别执行：

```bash
systemctl status test-agent-backend --no-pager
curl -fsS http://127.0.0.1:8080/actuator/health/readiness
cat /data/testagent/data/.serverid
cat /data/testagent/data/.serverhost

cd /data/testagent/deploy/internal
./opencode-worker-docker.sh --env-file /data/testagent/config/docker.env status
docker logs --tail 200 test-agent-opencode-worker | \
  egrep 'config update applied|websocket|serverhost|serverid|OPENCODE_UNAVAILABLE'
```

然后验收：

1. 运行管理中出现两个不同 `linuxServerId` 的 Java、manager 和容器，连接均在线。
2. 从 `.4` curl `.114:8080/actuator/health`，从 `.114` curl `.4:8080/actuator/health`。
3. 两个服务器都能初始化用户进程，动态端口的 `/global/health`、`/api/provider`、`/api/model` 正常。
4. 从前端连续创建多个用户/会话，确认进程可分布在两个服务器。
5. 让请求从另一个入口 Java 进入，已有会话仍能继续发送、停止、重启和读取状态，证明 Java 跨节点路由生效。
6. 分别用 Qwen 和 DeepSeek 验证普通正文、think/reasoning、工具调用、持续 SSE 和 `[DONE]`。
7. 两台后台都确认 9070 直连；正式链路中没有监听 19070 的 relay。
8. 分别打开终端、Workspace/Agent 文件编辑和 Agent 配置 Git 进度，浏览器 WebSocket URL 应直连 ticket 响应中的 `.4:8080` 或 `.114:8080`，连接不出现 ticket 无效。

模型验收不能只在其中一台执行。在 `.4` 和 `.114` 分别用本机 `127.0.0.1:8080` 执行 [单后台文档的两条 Java 代理 curl](SINGLE-BACKEND.md#8-验收)，分别验证 `qwen-prod + Qwen3.6-27B` 和 `deepseek-prod + DeepSeek-V4-Flash-W8A8`。两台都应持续返回单层 `data:`、正确的 `reasoning_content` 和单层 `[DONE]`；这样才能同时覆盖每台 Java 的内存快照、内部代理 key、UCID 转发和本机 9070 出站网络。

## 10. 故障定位与回滚

| 现象 | 排查顺序 |
|---|---|
| Nginx 只命中一台或出现 502 | `.2` 分别 curl 两个 health；检查 upstream、`nginx -t` 和后台防火墙。 |
| WebSocket 报 ticket 无效或浏览器连接超时 | 检查 ticket 响应是否为目标 Java 的绝对 `ws://<后台>:8080/...`，再从浏览器网段检查两个 `:8080` 可达性和两台 Java 的 Origin 白名单；不增加 sticky。 |
| 部署提示 systemd/8080 不匹配 | 执行 `systemctl show test-agent-backend -p ExecStart -p EnvironmentFiles -p MainPID` 和 `lsof -nP -iTCP:8080 -sTCP:LISTEN`；脚本只自动清理同一路径交付 JAR，其他进程必须人工确认。 |
| 运行管理只显示一个服务器 | 检查两台是否共享同一 Redis、ID 是否唯一、heartbeat 日志和广播 channel 是否一致。 |
| worker 连接到错误 Java | 检查本机 `.serverhost`、两份 env 的数据根目录和本机 manager token；禁止复制另一节点身份文件。 |
| 跨节点请求失败 | 两台互相 curl `advertised-host:8080`；检查 Redis 快照、目标 Java health 和日志中的 traceId。 |
| 某一台模型不通 | 先在故障节点直接调用本机 Java 代理并读取 4xx 正文，再在该 Java 宿主机检查 9070；共享数据库配置不能替代每台宿主机的内存刷新和网络可达性。 |
| 某一台报“供应商未启用或不存在” | 对比两台 refresh-status；确认广播开启且 channel 相同，必要时在故障节点重启 Java 重载数据库。 |
| 某一台模型不显示 | 初始化该 `linuxServerId` 的公共配置，确认包含 `includeUsage=false`，并重启该服务器上的用户 OpenCode 进程。共享数据库不会复制本机公共配置工作树。 |
| 只有某些旧用户 400 | 重启这些用户所属节点上的 OpenCode 进程，让其重新读取公共配置并由所属 Java 重新注入内部代理地址、key 和 UCID。 |
| 两台都连接 9070 超时 | 确认 OpenCode `baseURL` 指向同节点 Java `:8080`；只允许 Java 宿主机出站访问 9070，不恢复 19070 relay、host network 或 OpenCode 直连 9070。 |
| 后台下线后已有用户不可用 | 用户 binding 不会自动迁移；恢复所属服务器，或按正式迁移流程停止并重新分配，不能由入口 Java 本机降级。 |

滚动回滚时一次只回滚一台，恢复该节点旧 JAR、`backend/lib/`、programs 和 worker 镜像，并按“Java → 身份文件 → worker”启动。不要删除共享数据库/Redis数据，也不要清空任一节点 `/data/testagent/data`。
