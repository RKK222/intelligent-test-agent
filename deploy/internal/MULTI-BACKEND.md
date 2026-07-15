# 企业内多后台部署

当前代码正式支持两个或更多后台节点。任意入口 Java 收到请求后，会根据 Redis 中的服务器/manager 快照选择目标服务器；如果目标不在本机，使用公共 Java 路由转发到目标 Java，再由目标 Java 控制本机 manager。无需让 Nginx 固定用户到某个后台，也不得在业务入口实现本机降级或自行扫描 Redis。

本文用两个后台举例：

| 角色 | 地址/身份 |
|---|---|
| 前端实体 Nginx | `122.233.30.2` |
| 后台 A + worker A | `122.233.30.4` / `test-agent-backend-122-233-30-4` |
| 后台 B + worker B | `122.233.30.114` / `test-agent-backend-122-233-30-114` |
| Redis | `122.233.30.20:6379` |
| PostgreSQL | `122.42.203.103:8000/testagent` |
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

两台后台的共同配置：

```dotenv
SERVER_PORT=8080
TEST_AGENT_DEPLOYMENT_MODE=internal
SYS_DATA_ROOT_DIR=/data/testagent/data

TEST_AGENT_DB_URL=jdbc:postgresql://122.42.203.103:8000/testagent
TEST_AGENT_DB_USERNAME=testagent
TEST_AGENT_DB_PASSWORD=<同一个数据库密码>
TEST_AGENT_DB_DRIVER_CLASS_NAME=org.postgresql.Driver

TEST_AGENT_REDIS_HOST=122.233.30.20
TEST_AGENT_REDIS_PORT=6379
TEST_AGENT_REDIS_PASSWORD=<同一个 Redis 密码；无密码留空>

TEST_AGENT_CORS_ALLOWED_ORIGINS=http://122.233.30.2
TEST_AGENT_API_TOKEN=
TEST_AGENT_OPENCODE_MANAGER_TOKEN=<manager 随机 token>
TEST_AGENT_INTERNAL_PROXY_API_KEY=<Java 内部模型代理随机 key>
TEST_AGENT_MODEL_CATALOG_SOURCE=internal

TEST_AGENT_SERVER_BROADCAST_ENABLED=true
TEST_AGENT_SERVER_BROADCAST_CHANNEL=test-agent:server-broadcast

TEST_AGENT_DB_POOL_INITIAL_SIZE=1
TEST_AGENT_DB_POOL_MIN_IDLE=1
TEST_AGENT_DB_POOL_MAX_ACTIVE=10
TEST_AGENT_DB_POOL_MAX_WAIT_MILLIS=30000
TEST_AGENT_SCHEDULER_ENABLED=false
```

后台 A 追加：

```dotenv
TEST_AGENT_SERVER_ADVERTISED_HOST=122.233.30.4
TEST_AGENT_LINUX_SERVER_ID=test-agent-backend-122-233-30-4
```

后台 B 追加：

```dotenv
TEST_AGENT_SERVER_ADVERTISED_HOST=122.233.30.114
TEST_AGENT_LINUX_SERVER_ID=test-agent-backend-122-233-30-114
```

建议集群内 manager token 和内部代理 key 保持一致，降低滚动部署误配风险。无论是否相同，本机 `backend.env` 的 manager token 都必须与本机 `docker.env` 完全一致。

## 5. 每个后台的 docker.env

每台后台本机创建 `/data/testagent/config/docker.env`：

```dotenv
TEST_AGENT_BASE_DIR=/data/testagent
TEST_AGENT_NGINX_LISTEN_PORT=80
TEST_AGENT_FRONTEND_ROOT=/data/testagent/frontend

TEST_AGENT_OPENCODE_MANAGER_TOKEN=<与本机 backend.env 完全一致>
TEST_AGENT_DATA_ROOT=/data/testagent/data
TEST_AGENT_PROGRAM_ROOT=/data/testagent/programs
TEST_AGENT_OPENCODE_WORKER_IMAGE=test-agent-opencode-worker:internal

VITE_TEST_AGENT_API_BASE_URL=http://122.233.30.2

OPENCODE_WORKER_BACKEND_PORT=8080
OPENCODE_WORKER_PORT_START=4096
OPENCODE_WORKER_PORT_END=4105
```

保留 [env.example](env.example) 中其余版本和镜像摘要。`TEST_AGENT_BACKEND` 只用于渲染单后台 Nginx 模板，worker 不读取它；多后台 Nginx 使用下一节的显式 upstream，因此该变量即使保留也不能作为负载均衡配置来源。

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

多后台不要直接渲染单节点 `gateway.conf.template`，在 `.2` 使用显式 upstream：

```nginx
map $http_upgrade $connection_upgrade {
    default upgrade;
    '' close;
}

upstream test_agent_backend {
    least_conn;
    server 122.233.30.4:8080 max_fails=3 fail_timeout=10s;
    server 122.233.30.114:8080 max_fails=3 fail_timeout=10s;
    keepalive 32;
}

server {
    listen 80;
    server_name _;
    root /data/testagent/frontend;
    index index.html;

    location = /health {
        access_log off;
        add_header Content-Type text/plain;
        return 200 "ok\n";
    }

    location = /api {
        proxy_pass http://test_agent_backend;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $connection_upgrade;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
        proxy_buffering off;
        proxy_cache off;
    }

    location /api/ {
        proxy_pass http://test_agent_backend;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $connection_upgrade;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
        proxy_buffering off;
        proxy_cache off;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

应用前执行：

```bash
nginx -t
systemctl reload nginx
```

正确性不依赖 sticky session：用户进程归属由数据库 binding 和 Redis 服务器快照决定，入口 Java 会转发到进程所属服务器。SSE 和 WebSocket 连接建立后由 Nginx 保持当前上游长连接。

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

## 8. 公共配置和模型

超级管理员进入“系统管理 → 配置管理 → opencode 公共配置管理”，分别初始化：

```text
test-agent-backend-122-233-30-4
test-agent-backend-122-233-30-114
```

两个服务器使用同一版本的 [opencode.jsonc.example](opencode.jsonc.example)，模型为：

```text
icbc-qwen/Qwen3.6-27B                 -> qwen-prod
icbc-deepseek/DeepSeek-V4-Flash-W8A8 -> deepseek-prod
```

供应商地址、启用状态和上游 token 来自共享数据库。公共配置工作树位于各后台本机，因此数据库已经配置供应商并不等于另一台服务器已经初始化公共配置。更新后重启对应服务器上的已有用户 OpenCode 进程。

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

## 10. 故障定位与回滚

| 现象 | 排查顺序 |
|---|---|
| Nginx 只命中一台或出现 502 | `.2` 分别 curl 两个 health；检查 upstream、`nginx -t` 和后台防火墙。 |
| 运行管理只显示一个服务器 | 检查两台是否共享同一 Redis、ID 是否唯一、heartbeat 日志和广播 channel 是否一致。 |
| worker 连接到错误 Java | 检查本机 `.serverhost`、两份 env 的数据根目录和本机 manager token；禁止复制另一节点身份文件。 |
| 跨节点请求失败 | 两台互相 curl `advertised-host:8080`；检查 Redis 快照、目标 Java health 和日志中的 traceId。 |
| 某一台模型不通 | 在故障 Java 宿主机检查 9070；共享数据库配置不能替代每台宿主机的网络可达性。 |
| 某一台模型不显示 | 初始化该 `linuxServerId` 的公共配置并重启该服务器上的用户 OpenCode 进程。 |
| 后台下线后已有用户不可用 | 用户 binding 不会自动迁移；恢复所属服务器，或按正式迁移流程停止并重新分配，不能由入口 Java 本机降级。 |

滚动回滚时一次只回滚一台，恢复该节点旧 JAR、`backend/lib/`、programs 和 worker 镜像，并按“Java → 身份文件 → worker”启动。不要删除共享数据库/Redis数据，也不要清空任一节点 `/data/testagent/data`。
