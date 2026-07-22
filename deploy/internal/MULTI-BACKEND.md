# 企业内多后台部署

当前代码的普通 HTTP、RunEvent SSE、用户 OpenCode 进程路由、内部模型代理和受控 WebSocket 支持两个或更多后台节点。页面首次查询 `/processes/me` 时不带服务器路由头，仍按 `least_conn` 进入任意 Java；用户未绑定时，入口 Java 会基于共享 Redis 快照选择进程总数最少且存在本地可用容器的服务器，必要时通过公共 Java→Java 转发器把状态或初始化请求单次转发到该服务器，目标 Java 再完成本地容器选择与原子端口预留。拿到 binding 中的 `linuxServerId` 后，前端仅在页面内存保存它，并给后续用户 OpenCode、会话、Run、SSE 和本地工作区请求增加 `X-Test-Agent-Linux-Server-Id`。Nginx 通过静态白名单把这些请求直接送到一机一 Java 的目标节点，避免正常链路中的 Java→Java 二次转发。

这个头只是首跳性能提示，不是权限或路由事实源。缺失、未知或过期时仍由 `least_conn` 和后端公共路由程序根据真实 binding 兜底；尚无 binding 的精确状态/初始化请求则重新按当轮集群负载选服，已有 binding 不因负载变化迁移；Nginx 在转发前删除外部传入的路由头和 `X-Test-Agent-Backend-Routed`，后端鉴权、Session 归属和运行上下文校验继续生效。workspace PTY 和 Agent 配置进度 ticket 响应沿用签发 Java 地址；当前 HTTP 现场的服务器终端 ticket POST 也复用同一静态路由表，响应再返回签发 Java 的直接 `ws://` 地址，因此 ticket 的签发和消费不会跨 JVM，也不依赖 sticky。

本文用两个后台举例：

| 角色 | 地址/身份 |
|---|---|
| 浏览器域名入口 | `http://mimo.sdc.cs.icbc:9996`，企业入口继续转发到实体 Nginx `:80` |
| 浏览器 IP 入口 | `http://122.233.30.2:9996`，直连实体 Nginx 新增监听 `:9996` |
| 前端实体 Nginx | `122.233.30.2:80` + `122.233.30.2:9996`，安装目录 `/data/apps/nginx` |
| 后台 A + worker A | `122.233.30.4` / `test-agent-backend-122-233-30-4` |
| 后台 B + worker B | `122.233.30.114` / `test-agent-backend-122-233-30-114` |
| Redis | `122.233.30.20:6379` |
| PostgreSQL | `122.233.30.147:5432/postgres` |
| XXL MySQL | `122.233.30.147:3306/xxl_job`（与 PostgreSQL 同机的独立 MySQL 8.4 容器） |
| 企业内部模型 | `ai-code.sdc.enterprise:9070` |

## 1. 正式拓扑

```text
浏览器 -> mimo.sdc.cs.icbc:9996 -> 企业入口 -> 122.233.30.2:80 Nginx
       `-> 122.233.30.2:9996 ----------------------^ 同一个 Nginx server 块
              无/未知头 |-> least_conn(Java A, Java B)
          server A 路由头 |-> 122.233.30.4:8080   Java A -> 本机 worker A/OpenCode A
          server B 路由头 `-> 122.233.30.114:8080 Java B -> 本机 worker B/OpenCode B

Java A <---------------- 互访 8080 ----------------> Java B
   |                                                     |
   +------------- 共享 PostgreSQL / Redis / XXL MySQL ----+
   |                                                     |
   `-> ai-code.sdc.enterprise:9070          ai-code.sdc.enterprise:9070 <-'
```

必须同时满足：

1. 所有 Java 使用同一版本 JAR 和 `backend/lib/`，共享同一 PostgreSQL 和 Redis。
2. 每台物理服务器使用唯一、长期稳定的 `TEST_AGENT_LINUX_SERVER_ID`。
3. 每台后台只运行一个本机 worker；worker 只连接本机 `.serverhost:8080`。
4. 各节点 `/data/testagent/data` 是本机目录，不做跨服务器共享挂载。
5. 所有后台之间能双向访问对方声明的 `TEST_AGENT_SERVER_ADVERTISED_HOST:8080`。
6. Nginx 能访问全部后台 `:8080`；每台 Java 宿主机都能访问 PostgreSQL、Redis 和 `ai-code.sdc.enterprise:9070`。
7. 所有后台启用相同的服务器广播 channel。
8. 每台后台都要初始化本服务器公共 OpenCode 配置。
9. 每台 Linux 只运行一个 Java，Admin 固定与同 JVM executor 配对；executor 注册地址复用平台 advertised host，必须可从全部 Admin 节点访问。XXL 路由不携带 `linuxServerId`。

不要照搬旧双后台文档中的 RunEvent Redis bus 开关；当前代码已经删除该参数。运行态和跨 Java 路由使用已有的 Redis 存储、服务器快照及公共转发程序。

## 2. 网络和端口

| 来源 | 目标 | 用途 |
|---|---|---|
| 企业浏览器网段 | `mimo.sdc.cs.icbc:9996`、`.2:9996` | 同一版本前端的域名和 IP 双入口 |
| `.2` | `.4:8080`、`.114:8080` | Nginx 负载均衡 |
| `.2` | `.4:18080`、`.114:18080` | 同源 XXL Admin 负载均衡 |
| 企业浏览器网段 | `.4:8080`、`.114:8080` | PTY、Workspace/Agent 文件和 Agent 配置进度 WebSocket 按 ticket 签发节点直连 |
| `.4` | `.114:8080` | Java A 转发到 Java B |
| `.114` | `.4:8080` | Java B 转发到 Java A |
| 每台 worker 容器 | 本机 Java `:8080` | manager WebSocket、内部模型代理 |
| 每台后台 | PostgreSQL、Redis、XXL MySQL | 平台持久化、运行态与 XXL 独立调度库 |
| `.147` MySQL 容器 | 本机 `/data/testagent/mysql` | 与 PostgreSQL 进程分离的 XXL 调度数据；禁止把目录挂到 PostgreSQL 数据目录 |
| 每个 Admin | `.4:9999`、`.114:9999` | 调度所有 Java executor；仅可信内网开放 |
| 每台后台 | `ai-code.sdc.enterprise:9070` | 企业内部模型调用 |
| Java 后台 | 每台后台 `4096-4115` | 访问本机或目标服务器上的用户 OpenCode 进程；浏览器不直连这些端口 |

两个服务器可以重复使用 `4096-4115`，因为 IP 不同；同一台服务器的宿主机和容器端口必须同号。正式部署不使用 `--network host`、`19070` relay 或额外 model relay。域名和 IP 虽然都由浏览器访问 `9996`，但域名链路已有企业入口把 `9996` 转到实体 Nginx `80`；实体 Nginx 额外监听 `9996` 是为了让 IP 直连，不得删掉原来的 `listen 80`。

部署前验证：

```bash
# 在 .2；如果已有非 Nginx 进程占用 9996，先停止并确认归属，不能直接覆盖。
ss -lntp | grep ':9996 ' || true

# 在 .4
curl -fsS http://122.233.30.114:8080/actuator/health
nc -vz 122.233.30.20 6379
nc -vz ai-code.sdc.enterprise 9070

# 在 .114
curl -fsS http://122.233.30.4:8080/actuator/health
nc -vz 122.233.30.20 6379
nc -vz ai-code.sdc.enterprise 9070
```

## 3. Mac 打包与分发

只构建一份版本，禁止两个后台分别打包：

```bash
cd /Users/kaka/Desktop/intelligent-test-agent
VITE_TEST_AGENT_API_BASE_URL="" \
  deploy/internal/package-release.sh --output-dir deploy/internal/dist
```

空值是有意配置：前端统一使用同源相对 `/api`，所以从域名打开时请求域名，从 IP 打开时请求 IP。不得固定成其中任一 origin，否则另一个入口会重新产生跨域或名称解析问题。

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
122.233.30.147:/data/0709/
```

每台都执行：

```bash
cd /data/0709
sha256sum -c test-agent-internal-release.zip.sha256
unzip -t test-agent-internal-release.zip
```

如果需要先按现有现场生成无占位符的逐机配置和操作脚本，只需把独立采集脚本复制到三台机器，
不需要上传或解压完整发布包：

```bash
chmod 0700 /data/0709/collect-multi-backend-context.sh
```

然后在 `.2` 使用 `frontend` 角色，在 `.4`、`.114` 使用 `backend` 角色，并显式传入
`--include-sensitive`。该模式只采集原始 env、后台身份/systemd unit 和 Nginx 配置，保留其中的密码、
token，但不采集 JAR/RSA、日志、Docker、programs、worker 镜像、业务数据或前端文件；输出权限为
`0600`，且强制不超过 `1 MiB`。完整命令和内容清单见
[企业内部署文档入口](README.md#现场配置轻量敏感采集)。采集不会改变配置或重启服务。

## 4. 每个后台的 backend.env

以下两份都是可整文件替换的完整配置。两台机器必须把 PostgreSQL 密码、manager token、内部代理 key、XXL MySQL 密码和 XXL access token 这 5 个同名 `REPLACE_...` 替换为同一组现场值。模板按 Redis 无密码、平台 API token 为空填写；如果现网这两项非空，必须保留现网值。替换前先备份原文件：

```bash
install -d -m 0755 /data/testagent/config
cp -a /data/testagent/config/backend.env \
  /data/testagent/config/backend.env.bak.$(date +%Y%m%d%H%M%S) 2>/dev/null || true
```

两台 Java 共享数据库，必须部署同一交付 JAR，并统一使用其中的 `BOOT-INF/classes/rsa-private.key`。两台 `backend.env` 都不得配置 `TEST_AGENT_SSH_RSA_PRIVATE_KEY_PATH`；JAR SHA 或内置 RSA 不同时禁止同时接入流量。

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

TEST_AGENT_XXL_JOB_ENABLED=true
TEST_AGENT_XXL_JOB_MYSQL_URL=jdbc:mysql://122.233.30.147:3306/xxl_job?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
TEST_AGENT_XXL_JOB_MYSQL_USERNAME=xxl_job
TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=REPLACE_XXL_JOB_MYSQL_PASSWORD
TEST_AGENT_XXL_JOB_ACCESS_TOKEN=REPLACE_XXL_JOB_ACCESS_TOKEN
TEST_AGENT_XXL_JOB_ADMIN_PORT=18080
TEST_AGENT_XXL_JOB_EXECUTOR_PORT=9999

TEST_AGENT_REDIS_HOST=122.233.30.20
TEST_AGENT_REDIS_PORT=6379
TEST_AGENT_REDIS_PASSWORD=
TEST_AGENT_REDIS_TIMEOUT=1s

TEST_AGENT_CORS_ALLOWED_ORIGINS=http://mimo.sdc.cs.icbc:9996,http://122.233.30.2:9996
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
TEST_AGENT_MAX_PREVIEW_BYTES=5242880
TEST_AGENT_UPLOAD_CHUNK_BYTES=262144
TEST_AGENT_MAX_DIRECTORY_ENTRIES=1000

TEST_AGENT_BACKEND_HEARTBEAT_INTERVAL=5s
TEST_AGENT_BACKEND_STALE_AFTER=10s
TEST_AGENT_OPENCODE_MANAGER_COMMAND_TIMEOUT=10s
TEST_AGENT_BACKEND_DISCOVERY_LIMIT=100

TEST_AGENT_SERVER_TERMINAL_ENABLED=true
TEST_AGENT_SERVER_TERMINAL_WORKING_DIRECTORY=/data/testagent
TEST_AGENT_SERVER_TERMINAL_PUBLIC_WEBSOCKET_BASE_URL=
TEST_AGENT_SERVER_TERMINAL_ALLOW_INSECURE_WEBSOCKET=true

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

TEST_AGENT_XXL_JOB_ENABLED=true
TEST_AGENT_XXL_JOB_MYSQL_URL=jdbc:mysql://122.233.30.147:3306/xxl_job?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
TEST_AGENT_XXL_JOB_MYSQL_USERNAME=xxl_job
TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=REPLACE_XXL_JOB_MYSQL_PASSWORD
TEST_AGENT_XXL_JOB_ACCESS_TOKEN=REPLACE_XXL_JOB_ACCESS_TOKEN
TEST_AGENT_XXL_JOB_ADMIN_PORT=18080
TEST_AGENT_XXL_JOB_EXECUTOR_PORT=9999

TEST_AGENT_REDIS_HOST=122.233.30.20
TEST_AGENT_REDIS_PORT=6379
TEST_AGENT_REDIS_PASSWORD=
TEST_AGENT_REDIS_TIMEOUT=1s

TEST_AGENT_CORS_ALLOWED_ORIGINS=http://mimo.sdc.cs.icbc:9996,http://122.233.30.2:9996
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
TEST_AGENT_MAX_PREVIEW_BYTES=5242880
TEST_AGENT_UPLOAD_CHUNK_BYTES=262144
TEST_AGENT_MAX_DIRECTORY_ENTRIES=1000

TEST_AGENT_BACKEND_HEARTBEAT_INTERVAL=5s
TEST_AGENT_BACKEND_STALE_AFTER=10s
TEST_AGENT_OPENCODE_MANAGER_COMMAND_TIMEOUT=10s
TEST_AGENT_BACKEND_DISCOVERY_LIMIT=100

TEST_AGENT_SERVER_TERMINAL_ENABLED=true
TEST_AGENT_SERVER_TERMINAL_WORKING_DIRECTORY=/data/testagent
TEST_AGENT_SERVER_TERMINAL_PUBLIC_WEBSOCKET_BASE_URL=
TEST_AGENT_SERVER_TERMINAL_ALLOW_INSECURE_WEBSOCKET=true

```

`TEST_AGENT_MAX_PREVIEW_BYTES=5242880` 是一次性 UTF-8 读取和可编辑阈值，不会截断完整渐进预览，也不限制分片上传总大小；`TEST_AGENT_UPLOAD_CHUNK_BYTES=262144` 只控制每条上传分片的解码内存与 frame 大小。两台后端必须保持一致。

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

VITE_TEST_AGENT_API_BASE_URL=

OPENCODE_WORKER_BACKEND_PORT=8080
OPENCODE_WORKER_PORT_START=4096
OPENCODE_WORKER_PORT_END=4115

OPENCODE_ALLOWED_CORS=http://mimo.sdc.cs.icbc:9996,http://122.233.30.2:9996
OPENCODE_MANAGER_HEARTBEAT_INTERVAL=5s
OPENCODE_MANAGER_RECONNECT_INTERVAL=10s

OPENCODE_VERSION=1.18.4
OPENCODE_RELEASE_COMMIT=49c69c5ed3ccf706b61b3febb43c8aaff7f8325e
OPENCODE_ASSET_NAME=opencode-linux-x64-baseline.tar.gz
OPENCODE_ASSET_SIZE=59265643
OPENCODE_ASSET_SHA256=4d87e414607b77fef940256021e42fbbf37b8c62b06ced76b69e26c5dcbfbabc
OPENCODE_BINARY_SHA256=6ce6570e7db9a40e7bd3304ebdfff607920bde8cafd2eb5587bd7a26f89ba0b5
OPENCODE_RELEASE_BASE_URL=https://github.com/anomalyco/opencode/releases/download
GO_IMAGE=golang@sha256:e87b2a5f6df2dff71ea330d55d54f4979eb380ae58a7e3aabc9d53121243e689
NODE_IMAGE=node@sha256:b042c6d46a90773b82ea3f95b05457ea93ee127a73b1b47ad5ebbb1a08ec3df8

NPM_REGISTRY=https://registry.npmmirror.com
COREPACK_NPM_REGISTRY=https://registry.npmmirror.com
GOPROXY=https://goproxy.cn,direct
DEBIAN_MIRROR=https://mirrors.ustc.edu.cn/debian
DEBIAN_SECURITY_MIRROR=https://mirrors.ustc.edu.cn/debian-security

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
TEST_AGENT_NGINX_SERVER_ROUTES=test-agent-backend-122-233-30-4=122.233.30.4:8080,test-agent-backend-122-233-30-114=122.233.30.114:8080
TEST_AGENT_NGINX_XXL_JOB_ADMINS=122.233.30.4:18080,122.233.30.114:18080
TEST_AGENT_NGINX_LISTEN_PORT=80
TEST_AGENT_NGINX_ADDITIONAL_LISTEN_PORTS=9996
TEST_AGENT_NGINX_TLS_ENABLED=false
TEST_AGENT_FRONTEND_ROOT=/data/testagent/frontend
TEST_AGENT_NGINX_CONF_PATH=/data/apps/nginx/conf/test-agent.conf
TEST_AGENT_NGINX_BIN=/data/apps/nginx/sbin/nginx
TEST_AGENT_NGINX_PREFIX=/data/apps/nginx
TEST_AGENT_NGINX_MAIN_CONF=/data/apps/nginx/conf/nginx.conf
TEST_AGENT_NGINX_RELOAD_MODE=binary
```

`TEST_AGENT_NGINX_SERVER_ROUTES` 是 `linuxServerId -> Java endpoint` 的一对一静态白名单。每个 ID 和 endpoint 都只能出现一次，而且 endpoint 必须已列在 `TEST_AGENT_NGINX_BACKENDS`；脚本会拒绝非法值，绝不会把客户端头直接拼成地址。扩容或变更 binding 对应服务器时，必须同步更新这张表。

`TEST_AGENT_NGINX_CONF_PATH` 必须是当前 Nginx 主配置实际 include 的 `.conf` 文件。前端部署脚本统一调用 [configure-nginx.sh](configure-nginx.sh)，由同一个 [gateway.conf.template](nginx/gateway.conf.template) 生成平台与 XXL 两组 `least_conn` upstream、逐节点故障参数、WebSocket Upgrade、SSE 禁缓冲和 `/xxl-job-admin/` 同源代理；配置失败自动恢复旧文件。

两台 `backend.env` 必须使用相同 MySQL URL/账号密码/access token。Java 自动用本机 loopback Admin 做启动门控，并从 `TEST_AGENT_SERVER_ADVERTISED_HOST`（缺失时自动探测内网 IPv4）派生 executor 注册地址（`.4:9999`、`.114:9999`）。Admin/MySQL 故障不影响平台 readiness，但必须在部署验收中单独检查 `xxlJobAdmin` health 和六条初始化任务。

`TEST_AGENT_NGINX_XXL_JOB_ADMINS` 只维护中央 Nginx upstream，不会进入 Java。新增第三台 Linux 时，启动新 Java/worker，把它同时加入 `TEST_AGENT_NGINX_BACKENDS` 和 `TEST_AGENT_NGINX_XXL_JOB_ADMINS`，再无停机 reload Nginx；已有两台 Java 不修改配置、不重启。

`TEST_AGENT_NGINX_CONF_PATH` 使用已经由主配置显式 include 的 `/data/apps/nginx/conf/test-agent.conf`，不要另建一个未加载的同级文件。前端部署脚本统一调用 [configure-nginx.sh](configure-nginx.sh)，由同一个 [gateway.conf.template](nginx/gateway.conf.template) 生成：

- 缺失或未知 ID 使用原 `least_conn` upstream；已知 ID 使用对应服务器的专用 upstream。
- 专用 upstream 以对应 Java 为 primary，其余 Java 为 backup；只在无法连接或连接超时时尝试 backup。
- 普通 HTTP、SSE 和服务器终端 WebSocket 共用一张静态路由表。
- 发往 Java 前删除 `X-Test-Agent-Linux-Server-Id` 和外部伪造的 `X-Test-Agent-Backend-Routed`。
- `proxy_next_upstream` 只包含 `error timeout`，不启用 `non_idempotent`，避免已发送的 Session/Run POST 被重复执行。
- 继续生成 `listen 80`、`listen 9996`、WebSocket Upgrade、SSE 禁缓冲和长连接超时。

配置失败时脚本会恢复旧文件，不再手工维护另一份多节点 Nginx 配置。普通共享控制面 API 不携带路由头，继续使用 `least_conn`；只有用户 OpenCode、会话、Run、SSE 和本地工作区请求采用精确首跳。一次性 WebSocket ticket 仍是 JVM 内存状态，但路由已经闭合：

- PTY ticket 请求按用户进程归属转发，响应返回实际签发 Java 的绝对 WebSocket 地址。
- Workspace/Agent 配置文件 route 返回目标 Java `baseUrl`，浏览器在目标 Java 申请 ticket 并连接同一 Java。
- Agent 配置进度 ticket 返回当前签发 Java 的绝对 WebSocket 地址；跨节点进度由既有服务器广播汇入。

因此不需要 `ip_hash`、cookie sticky、共享 ticket 或 Java 间文件/WebSocket 代理。当前现场明确不启用 HTTPS，两台 Java 都以空的公开 WSS 基址和 `ALLOW_INSECURE=true` 返回本节点 `ws://.4:8080` 或 `ws://.114:8080`；浏览器网段必须能访问两个 Java `:8080`，两台 Java 和 worker 的 Origin 白名单也都必须同时包含域名和 IP 两个 `:9996` origin。HTTP/WS 会明文传输登录信息和终端内容，只能在已接受风险的可信内网使用。

### 6.1 已有 `nginx.env` 升级

旧变量 `TEST_AGENT_NGINX_TERMINAL_ROUTES` 只作为一次升级兼容入口；新包必须改成统一变量，不能同时保留两个键。右侧映射值不变：

```bash
cp -a /data/testagent/config/nginx.env \
  /data/testagent/config/nginx.env.bak.$(date +%Y%m%d%H%M%S)
sed -i 's/^TEST_AGENT_NGINX_TERMINAL_ROUTES=/TEST_AGENT_NGINX_SERVER_ROUTES=/' \
  /data/testagent/config/nginx.env

bash /data/testagent/deploy/internal/configure-nginx.sh \
  --env-file /data/testagent/config/nginx.env \
  --validate-only
```

预期输出 `backend count: 2`、`server route count: 2`。正式安装必须使用本次发布包中的前端部署入口；它会更新前端和部署脚本、渲染候选配置、执行实体 Nginx `-t/-T` 并 reload，失败自动回滚：

若 Mac 重新封装时复用的前端节点包仍是旧键，`package-two-backend-complete.sh` 会只在外层包的临时副本中自动改名；源敏感节点包不会被修改。节点包缺少该映射、重复定义或同时含新旧键时，封装会失败，不能继续交付。

```bash
bash /tmp/deploy-internal-frontend.sh \
  --archive /data/0709/test-agent-internal-release.zip \
  --validate-only
bash /tmp/deploy-internal-frontend.sh \
  --archive /data/0709/test-agent-internal-release.zip
```

## 7. 部署与启动顺序

### 7.1 使用逐机配置包

后续 U 盘交付物固定为 `test-agent-two-backend-complete.zip` 和配套
`test-agent-two-backend-complete.zip.sha256`，ZIP 内顶层目录固定为
`test-agent-two-backend-complete/`，不再在文件名或目录名中添加日期、`v2`、`v3`。企业内部中转机和
三台应用服务器和 `.147` 数据库服务器可以长期复用同一组校验、解压和 `scp` 命令。

四台服务器都上传并校验同一个外层 ZIP 后，用无参数入口执行。入口脚本从本机网卡识别 IP，自动选择并
校验节点包、解压节点配置，然后连续完成 `--validate-only`、正式部署和 `--verify-only`。任一步失败都会
返回非零；Java、Docker、Nginx 和最终校验输出统一写入 `/data/0709/deploy-<本机IP>.log`，不会再出现
只执行了一个空 `bash`、但实际服务没有重启的情况。

先在 PostgreSQL 所在的 `.147` 部署独立 MySQL 容器：

```bash
cd /data/0709
sha256sum -c test-agent-two-backend-complete.zip.sha256
unzip -oq test-agent-two-backend-complete.zip
cd /data/0709/test-agent-two-backend-complete
bash deploy-mysql-node.sh
```

该命令离线导入包内 `mysql:8.4` linux/amd64 镜像，在 `/data/testagent/mysql` 持久化数据，初始化
`xxl_job` 库和 `xxl_job` 账号。root 密码和应用密码已由打包阶段分别生成；应用密码同时写入 `.4`、
`.114` 的敏感 `backend.env`，脚本不会打印。已有数据目录不会被删除，若现场曾用另一组密码初始化，
验证会失败并要求先核对旧凭据，不会自动重建数据库。

企业上午已经部署过旧包时，PostgreSQL 中可能已有 `V20260721213000`。本包把尚未交付的夜间 XXL
迁移固定为更晚的 `V20260722130000`，Flyway 会正常顺序执行；不要在企业环境添加
`SPRING_FLYWAY_OUT_OF_ORDER=true`，也不要手工修改 `flyway_schema_history`。

MySQL 验证通过后，在 `.4` 执行：

```bash
cd /data/0709
sha256sum -c test-agent-two-backend-complete.zip.sha256
unzip -oq test-agent-two-backend-complete.zip
cd /data/0709/test-agent-two-backend-complete
bash deploy-backend-node.sh
```

升级前应先停止 `.4`、`.114` 的旧 Java。`.4` 是固定首节点，入口会完整验证本机 Java、XXL Admin、
worker、RSA 和身份文件，但把 peer 探测延后；随后 `.114` 会反查 `.4`，最后 `.2` 会同时检查两个
Java 和两个 XXL Admin，因此不会再因首节点等待尚未启动的 peer 而产生 `verify_exit=1`。

再在 `.114` 执行：

```bash
cd /data/0709
sha256sum -c test-agent-two-backend-complete.zip.sha256
unzip -oq test-agent-two-backend-complete.zip
cd /data/0709/test-agent-two-backend-complete
bash deploy-backend-node.sh
```

两台后台全部通过后，最后在 `.2` 执行：

```bash
cd /data/0709
sha256sum -c test-agent-two-backend-complete.zip.sha256
unzip -oq test-agent-two-backend-complete.zip
cd /data/0709/test-agent-two-backend-complete
bash deploy-frontend-node.sh
```

正式部署必须由 `root` 执行。外层包内已有完整发布 ZIP，四台服务器不再另外复制内层 ZIP 或逐机包。
后台节点包包含真实数据库密码和 token，权限与传输方式按敏感交付物处理；RSA 只使用发布 JAR 内的
`BOOT-INF/classes/rsa-private.key`，不会生成 RSA env 或外置私钥路径。

当前 manager 配置下发成功日志为 `event=manager_config_update status=applied`。逐机验证脚本同时兼容
旧版 `manager config update applied`；不能只按旧文本判断当前结构化日志失败。

### 7.2 后续增加一台全新后台

把同一个外层 ZIP 和 SHA 上传到新后台并解压。初始化脚本从新服务器网卡自动取
`122.233.30.x`，以包内 `.4` 的真实配置为基线，只替换本机 `TEST_AGENT_SERVER_ADVERTISED_HOST` 和
`TEST_AGENT_LINUX_SERVER_ID`；`docker.env` 沿用集群相同 manager token，整个过程不打印密码或 token，
生成的节点配置包仍强制不超过 `1 MiB`：

```bash
cd /data/0709
sha256sum -c test-agent-two-backend-complete.zip.sha256
unzip -oq test-agent-two-backend-complete.zip
cd /data/0709/test-agent-two-backend-complete
bash init-backend-node-config.sh
bash deploy-backend-node.sh
```

这一步自动初始化新后台的两个文件：

```text
backend.env：复用数据库、Redis、CORS、manager token、内部代理 key，只改本机 IP 和稳定 server ID
docker.env：复用数据目录、programs、worker 镜像、端口池及与 backend.env 相同的 manager token
```

新后台 readiness 通过后，在前端 `.2` 的已解压外层目录登记一次新后台 IP，再重新运行前端入口。
远端后台 IP 不能从前端本机网卡推断，所以这是扩容中唯一需要手工填写的 IP；脚本会先访问新后台
readiness，再把 backend 和统一 server route 同步追加到打包的 `nginx.env`，重复登记不会产生重复项：

```bash
cd /data/0709/test-agent-two-backend-complete
bash register-backend-on-frontend.sh 122.233.30.新后台末段
bash deploy-frontend-node.sh
```

如果以后再加后台，每台新后台重复上面的初始化和后台部署；最后在 `.2` 对每个新 IP 登记一次，再只
执行一次 `deploy-frontend-node.sh`。

### 7.3 使用完整发布包中的标准脚本

先部署后台 A `.4`：

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

再部署后台 B `.114`：

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

两台后台都通过 health/readiness、身份文件和 worker 检查后，最后在前端 `.2` 部署一次，避免 Nginx 提前把流量分到尚未就绪的 `.4`：

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

首次部署时先让两台 Java 都通过 health/readiness 并写对身份文件，再分别启动本机 worker。已有环境升级用户绑定端口复用版本时，先逐台更新并重启 worker/manager，再滚动更新 Java，全部后端就绪后最后部署一次前端；不要在同一用户初始化期间跨节点切换版本。每台已有节点按：

```text
替换 worker 镜像/programs -> 重启 manager 并确认 stopOwned capability
-> 替换 JAR/lib -> 重启 Java -> 检查 health/readiness 与 .serverid/.serverhost
```

混合版本遇到未知命令或启动错误时必须保留原 binding，只报错、不迁移端口。不得清理 `/data/testagent/data`，也不要把 A 的数据目录复制覆盖到 B；存量无主进程由超级管理员在运行管理核对 UCID/PID/端口后手工处理。

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
enterprise-qwen/Qwen3.6-27B                 -> qwen-prod
enterprise-deepseek/DeepSeek-V4-Flash-W8A8 -> deepseek-prod
```

共享数据库的“内部模型供应商”页面完整填写如下；只需把两个 token 替换为现场已有值：

| Provider ID | 名称 | Base URL | Token | 启用 | 排序 |
|---|---|---|---|---|---:|
| `qwen-prod` | `企业通义` | `http://ai-code.sdc.icbc:9070/enterprise/jdt/model/api/openai/v1` | `REPLACE_QWEN_UPSTREAM_TOKEN` | 是 | `1` |
| `deepseek-prod` | `企业 DeepSeek` | `http://ai-code.sdc.icbc:9070/enterprise/jdt/model/api/openai/v1` | `REPLACE_DEEPSEEK_UPSTREAM_TOKEN` | 是 | `2` |

公共配置必须包含 `includeUsage=false`，避免 OpenCode 1.18.4 默认添加企业内部接口不支持的 `stream_options.include_usage`。供应商地址、启用状态和上游 token 来自共享数据库：`qwen-prod`、`deepseek-prod` 均启用，`baseUrl` 为 `http://ai-code.sdc.icbc:9070/enterprise/jdt/model/api/openai/v1`。公共配置工作树位于各后台本机，因此数据库已经配置供应商并不等于另一台服务器已经初始化公共配置。

`enterprise-qwen` / `enterprise-deepseek` 是 OpenCode provider key；`qwen-prod` / `deepseek-prod` 是数据库和 `X-Enterprise-Model-Provider` 使用的 Java 路由键，不能混用。上游 token 只在共享数据库维护，由 Java 以 `Authorization: Bearer <token>` 注入。用户 UCID 由拥有该用户进程的 Java 从用户表读取并逐进程注入，不使用全局 UCID env 文件。

变更生效规则：

1. 修改某台服务器公共 `opencode.jsonc` 后，只重启该服务器上已有的用户 OpenCode 进程；新进程直接读取。
2. 修改共享数据库供应商或 token 后点击“刷新 Java 内存”；广播启用时所有 Java 会分别重载同一数据库快照。
3. 在两台后台分别查询 refresh-status，必须都包含 `qwen-prod`、`deepseek-prod` 且 `tokenConfigured=true`；广播失败时逐台重启 Java 重新加载。
4. 只重启 Java 不会让已经运行的用户 OpenCode 重新读取公共配置或重新注入 UCID，涉及进程配置时仍要重启对应用户进程。

公共 `tools/*.ts` 随各服务器公共配置仓库更新，项目专用 Tool 位于工作区 `.opencode/tools/*.ts`。企业 programs 已离线内置 `@opencode-ai/plugin`、`@opencode-ai/sdk`、`effect`、`zod` 及传递依赖；仅更新 Tool 文件时，在每台目标服务器同步公共仓库后重启该节点相关用户 OpenCode 进程。Tool 新增基线外第三方 import 时，必须重新打完整企业包，并在每台后台同时更新 programs、worker 镜像和 worker，不能只向一台服务器复制 `node_modules`。

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

1. 在 `.2` 执行下面的 Nginx 检查。预期配置只由已加载的 `test-agent.conf` 承载，同时出现两个监听端口、默认 `least_conn`、两组服务器专用 upstream、静态 `map`、primary/backup，以及两种入站路由头清理；不得出现 `non_idempotent`：

   ```bash
   /data/apps/nginx/sbin/nginx -p /data/apps/nginx/ \
     -c /data/apps/nginx/conf/nginx.conf -t
   /data/apps/nginx/sbin/nginx -p /data/apps/nginx/ \
     -c /data/apps/nginx/conf/nginx.conf -T \
     > /tmp/test-agent-nginx-effective.conf 2>&1
    grep -E 'configuration file /data/apps/nginx/conf/test-agent.conf|listen (80|9996);|least_conn|map \$http_x_test_agent_linux_server_id|test_agent_server_route_[01]|server 122\.233\.30\.(4|114):8080|backup|proxy_set_header X-Test-Agent-(Linux-Server-Id|Backend-Routed) ""' \
      /tmp/test-agent-nginx-effective.conf
    if grep -q 'proxy_next_upstream.*non_idempotent' /tmp/test-agent-nginx-effective.conf; then
      echo '错误：禁止为非幂等请求启用重试' >&2
      exit 1
    fi
    ss -lntp | grep -E ':(80|9996)[[:space:]]'
   ```

2. 从实际浏览器网段分别执行 `curl -fsS http://mimo.sdc.cs.icbc:9996/health` 和 `curl -fsS http://122.233.30.2:9996/health`，两条都返回 `ok`；浏览器 Network 中 API 都是当前地址下的相对 `/api/...`，不能固定请求另一个 origin。
3. 登录后打开浏览器开发者工具的 Network，刷新页面并按时间排序：第一次 `GET /api/internal/agent/opencode/processes/me` 不应携带 `X-Test-Agent-Linux-Server-Id`；未绑定用户可能先进入任一 Java、再由后端转发到当前进程总数最少的可初始化服务器，因此两台 Java 都可能出现同一 traceId，但只有目标 Java 执行本地状态逻辑。响应给出 `linuxServerId` 后，进程健康、Session、Run、permission/question、工作区请求和两个 fetch SSE 请求应携带该值。用户管理、应用列表、登录和共享查询不应携带它；刷新后仍应重新从第一次 `/processes/me` 获取，不能从 localStorage/sessionStorage 恢复。
4. 用已登录浏览器中复制的 JWT 对两个静态映射做可追踪验证。下面先验证绑定在 `.4` 的用户；把 `REPLACE_LOGIN_JWT` 替换为该用户当前 token：

   ```bash
   ACCESS_TOKEN='REPLACE_LOGIN_JWT'
   TRACE_ID="trace_ngroute_a_$(date +%Y%m%d%H%M%S)"
   curl -fsS -D /tmp/ngroute-a.headers \
     'http://122.233.30.2:9996/api/internal/agent/opencode/processes/me' \
     -H "Authorization: Bearer ${ACCESS_TOKEN}" \
     -H 'X-Test-Agent-Linux-Server-Id: test-agent-backend-122-233-30-4' \
     -H "X-Trace-Id: ${TRACE_ID}" \
     -o /tmp/ngroute-a.json
   grep -i '^X-Trace-Id:' /tmp/ngroute-a.headers
   grep -F "${TRACE_ID}" /tmp/ngroute-a.json

   # 分别在 .4 和 .114 执行；正常精确首跳只应在 .4 出现 api_entry。
   journalctl -u test-agent-backend --since '-5 min' --no-pager | \
     grep -F "${TRACE_ID}"
   ```

   对绑定在 `.114` 的用户把路由 ID 改为 `test-agent-backend-122-233-30-114` 重复一次，日志只应在 `.114` 出现。再分别删除路由头、改成 `unknown-server`，请求仍应成功并按 `least_conn` 进入任一健康 Java。用与真实 binding 不一致的已知 ID 重试时，首跳 Java 会根据真实 binding 转发到正确 Java，响应契约不变；这证明头是提示而不是事实源。
5. 做故障兜底演练前停止所有业务写入，只使用上面的只读 `/processes/me`。在维护窗口停止 `.4` Java 后，继续带 `.4` 路由 ID 请求：Nginx 应连接 `.114` backup，最终返回平台 JSON，`data.status=UNAVAILABLE` 或统一 `OPENCODE_UNAVAILABLE`，不能返回 Nginx HTML 502。演练后立即恢复并检查 readiness：

   ```bash
   # 在 .4
   systemctl stop test-agent-backend

   # 在 .2 或浏览器网段，重复上面的 curl；检查 HTTP 正文和 Content-Type。
   curl -sS -D /tmp/ngroute-failover.headers \
     'http://122.233.30.2:9996/api/internal/agent/opencode/processes/me' \
     -H "Authorization: Bearer ${ACCESS_TOKEN}" \
     -H 'X-Test-Agent-Linux-Server-Id: test-agent-backend-122-233-30-4' \
     -o /tmp/ngroute-failover.json
   grep -i '^Content-Type:.*application/json' /tmp/ngroute-failover.headers
   sed -n '1,5p' /tmp/ngroute-failover.json

   # 在 .4
   systemctl start test-agent-backend
   curl -fsS http://127.0.0.1:8080/actuator/health/readiness
   ```

   不要用创建 Session/Run 的 POST 做断网演练。配置没有 `non_idempotent`，Nginx 不会在请求已经发往 primary 后重放非幂等请求；只有建立连接失败或超时才可能选择 backup。
6. 运行管理中出现两个不同 `linuxServerId` 的 Java、manager 和容器，连接均在线。
7. 从 `.4` curl `.114:8080/actuator/health`，从 `.114` curl `.4:8080/actuator/health`。
8. 两个服务器都能初始化用户进程，动态端口的 `/global/health`、`/api/provider`、`/api/model` 正常。
9. 做两级调度验收：先让 `.114` 的进程总数低于 `.4`，直接向 `.4` 对一个未绑定用户调用初始化，确认 binding 落到 `.114` 且只有 `.114` manager 收到 start；反转负载后让新用户落到 `.4`；再次请求已有 binding 用户，确认仍留在原服务器。
10. 分别用 Qwen 和 DeepSeek 验证普通正文、think/reasoning、工具调用、持续 SSE 和 `[DONE]`。
11. 两台后台都确认 9070 直连；正式链路中没有监听 19070 的 relay。
12. 分别打开终端、Workspace/Agent 文件编辑和 Agent 配置 Git 进度，浏览器 WebSocket URL 应直连 ticket 响应中的 `.4:8080` 或 `.114:8080`，连接不出现 ticket 无效。

两个入口是不同浏览器 origin，Cookie、`localStorage` 和登录态不会天然共享；首次分别打开时需要各自登录。这不影响同一套后台数据和会话路由。

模型验收不能只在其中一台执行。在 `.4` 和 `.114` 分别用本机 `127.0.0.1:8080` 执行 [单后台文档的两条 Java 代理 curl](SINGLE-BACKEND.md#8-验收)，分别验证 `qwen-prod + Qwen3.6-27B` 和 `deepseek-prod + DeepSeek-V4-Flash-W8A8`。两台都应持续返回单层 `data:`、正确的 `reasoning_content` 和单层 `[DONE]`；这样才能同时覆盖每台 Java 的内存快照、内部代理 key、UCID 转发和本机 9070 出站网络。

## 10. 故障定位与回滚

| 现象 | 排查顺序 |
|---|---|
| 已知路由 ID 仍命中错误节点 | 检查浏览器请求头、`TEST_AGENT_NGINX_SERVER_ROUTES`、`nginx -T` 中的 `map` 和专用 upstream；确认映射 endpoint 与该服务器唯一 Java 一致。binding 已变化但静态表未变时先修正表并重新部署前端。 |
| Nginx 返回 502 | `.2` 分别 curl 两个 health；检查专用 upstream 的 primary/backup、`nginx -t` 和后台防火墙。两台 Java 都不可达时才允许出现 Nginx 502。 |
| 请求头被伪造或使用旧 binding | 后端必须仍按真实 binding、Session 和运行上下文校验并转发；若伪造头能绕过权限或归属校验，立即回滚本次 Nginx 配置并按安全缺陷处理。 |
| 域名正常但 IP `:9996` 拒绝连接 | 检查渲染结果是否同时包含 `listen 80;` 和 `listen 9996;`，再检查 `.2` 防火墙是否放行 TCP 9996；不要删除域名链路仍依赖的 `listen 80`。 |
| 一个入口的 API 请求跑到另一个入口或 `127.0.0.1` | 前端不是以显式空的 `VITE_TEST_AGENT_API_BASE_URL` 构建；重新部署本次双入口包，不能只改服务器 `docker.env`。 |
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
