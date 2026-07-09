# 企业内双后端部署与排查说明

本文档用于当前企业内拓扑：前端入口 `122.233.30.2`，后端/worker 节点 `122.233.30.4` 和新增 `122.233.30.114`，Redis `122.233.30.20`，PostgreSQL `122.42.203.103:8000/testagent`。

## 目标拓扑

| 角色 | 地址 | 关键目录 | 说明 |
|---|---|---|---|
| 前端 Nginx | `122.233.30.2` | `/data/testagent/frontend` | 托管静态资源，把 `/api`、SSE、WebSocket 反代到两个 Java 后端。 |
| 后端/worker A | `122.233.30.4` | `/data/testagent` | Java 后端 + 本机 `opencode-worker`。 |
| 后端/worker B | `122.233.30.114` | `/data/testagent` | 新增 Java 后端 + 本机 `opencode-worker`。 |
| Redis | `122.233.30.20` | 运维维护 | 两个 Java 共用，用于登录态、运行管理快照、RunEvent bus 和服务器广播。 |
| PostgreSQL | `122.42.203.103:8000` | 运维维护 | 两个 Java 共用同一个 `testagent` 库。 |

双后端要求：

- 两台 Java 都能访问 PostgreSQL、Redis、企业模型服务和前端 Nginx。
- 两台 Java 之间能互相访问 `http://122.233.30.4:8080` 和 `http://122.233.30.114:8080`，用于用户 opencode 进程归属路由。
- 每台后端的 Java 和本机 worker 共享同一台机器上的 `/data/testagent/data`，但不同后端服务器之间不要共享这个目录。
- 每台后端都要单独初始化公共 Agent/Skill 配置目录；系统管理里的“opencode 公共配置管理”需要看到两台服务器均已初始化。

## 打包与传包

在联网 Mac/构建机执行：

```bash
cd /Users/kaka/Desktop/intelligent-test-agent
deploy/internal/package-release.sh
```

产物：

```text
deploy/internal/dist/test-agent-internal-release.zip
```

把完整 zip 放到三台业务服务器的同一路径，推荐：

```bash
/data/0709/internal.zip
```

如果 `122.233.30.4` 不能免密访问 `122.233.30.2`，不要继续纠结 `ssh-copy-id`；当前截图里的 `Permission denied (publickey,gssapi-keyex,gssapi-with-mic)` 表明前端机禁止这类直连。改用“每台机器本地执行脚本”的方式：

- `122.233.30.2`：执行前端本地部署脚本。
- `122.233.30.4`：执行后端/worker 部署脚本，带 `--skip-frontend`。
- `122.233.30.114`：执行后端/worker 部署脚本，带 `--backend-host 122.233.30.114 --skip-frontend`。

## 配置文件差异

### `122.233.30.4:/data/testagent/config/backend.env`

保持或确认这些值：

```dotenv
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
TEST_AGENT_DEPLOYMENT_MODE=internal
TEST_AGENT_SERVER_ADVERTISED_HOST=122.233.30.4
TEST_AGENT_LINUX_SERVER_ID=test-agent-backend-122-233-30-4
SYS_DATA_ROOT_DIR=/data/testagent/data

TEST_AGENT_DB_URL=jdbc:postgresql://122.42.203.103:8000/testagent
TEST_AGENT_DB_USERNAME=testagent
TEST_AGENT_DB_PASSWORD=<真实密码>

TEST_AGENT_REDIS_HOST=122.233.30.20
TEST_AGENT_REDIS_PORT=6379
TEST_AGENT_REDIS_PASSWORD=

TEST_AGENT_CORS_ALLOWED_ORIGINS=http://122.233.30.2
TEST_AGENT_OPENCODE_MANAGER_TOKEN=<集群内统一 manager token>
TEST_AGENT_INTERNAL_PROXY_API_KEY=<随机长 key，仅 Java backend.env 配置>

TEST_AGENT_RUN_EVENT_REDIS_BUS_ENABLED=true
TEST_AGENT_SERVER_BROADCAST_ENABLED=true
TEST_AGENT_MODEL_CATALOG_SOURCE=internal
```

### `122.233.30.114:/data/testagent/config/backend.env`

从 `122.233.30.4` 的 `backend.env` 复制后，至少改这两项：

```dotenv
TEST_AGENT_SERVER_ADVERTISED_HOST=122.233.30.114
TEST_AGENT_LINUX_SERVER_ID=test-agent-backend-122-233-30-114
```

其余数据库、Redis、CORS、模型服务、`TEST_AGENT_INTERNAL_PROXY_API_KEY` 与 manager token 建议与 `122.233.30.4` 保持一致。`SYS_DATA_ROOT_DIR` 仍是本机路径：

```dotenv
SYS_DATA_ROOT_DIR=/data/testagent/data
```

### 两台后端的 `/data/testagent/config/docker.env`

两台机器都需要：

```dotenv
TEST_AGENT_OPENCODE_MANAGER_TOKEN=<必须和本机 backend.env 一致>
TEST_AGENT_DATA_ROOT=/data/testagent/data
TEST_AGENT_PROGRAM_ROOT=/data/testagent/programs
TEST_AGENT_OPENCODE_WORKER_IMAGE=test-agent-opencode-worker:internal

OPENCODE_WORKER_BACKEND_PORT=8080
OPENCODE_WORKER_PORT_START=4096
OPENCODE_WORKER_PORT_END=4105

VITE_TEST_AGENT_API_BASE_URL=http://122.233.30.2
```

`4096-4105` 在两台不同服务器上可以重复，因为端口是在各自宿主机上发布；同一台服务器上如果以后跑多个 worker 容器，端口池才必须错开。

`TEST_AGENT_BACKEND` 只用于单后端 Nginx 模板。双后端时不用依赖它生成配置，直接按下节手写 Nginx upstream。

## 前端 Nginx 配置

在 `122.233.30.2` 写入或替换 `/etc/nginx/conf.d/test-agent.conf`：

```nginx
map $http_upgrade $connection_upgrade {
    default upgrade;
    '' close;
}

upstream test_agent_backend {
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

验证：

```bash
nginx -t
systemctl reload nginx
curl -fsS http://122.233.30.2/health
curl -fsS http://122.233.30.2/
```

## 部署顺序

### 1. 前端服务器 `122.233.30.2`

把同一个 zip 放到：

```bash
/data/0709/internal.zip
```

首次使用脚本：

```bash
unzip -p /data/0709/internal.zip deploy/internal/deploy-internal-frontend.sh > /tmp/deploy-internal-frontend.sh
bash /tmp/deploy-internal-frontend.sh --archive /data/0709/internal.zip --validate-only
bash /tmp/deploy-internal-frontend.sh --archive /data/0709/internal.zip
```

该脚本只更新前端静态资源和 `/data/testagent/deploy/internal`，不处理 Java、worker 或数据库。

### 2. 后端/worker A `122.233.30.4`

首次使用脚本：

```bash
unzip -p /data/0709/internal.zip deploy/internal/deploy-internal-release.sh > /tmp/deploy-internal-release.sh
bash /tmp/deploy-internal-release.sh --archive /data/0709/internal.zip --validate-only
bash /tmp/deploy-internal-release.sh --archive /data/0709/internal.zip --backend-host 122.233.30.4 --skip-frontend
```

预期身份文件：

```bash
cat /data/testagent/data/.serverid    # test-agent-backend-122-233-30-4
cat /data/testagent/data/.serverhost  # 122.233.30.4
```

### 3. 后端/worker B `122.233.30.114`

先准备 `/data/testagent/config/backend.env` 和 `/data/testagent/config/docker.env`，确认 `backend.env` 里已经改成 `122.233.30.114`。

首次使用脚本：

```bash
unzip -p /data/0709/internal.zip deploy/internal/deploy-internal-release.sh > /tmp/deploy-internal-release.sh
bash /tmp/deploy-internal-release.sh --archive /data/0709/internal.zip --validate-only
bash /tmp/deploy-internal-release.sh --archive /data/0709/internal.zip --backend-host 122.233.30.114 --skip-frontend
```

预期身份文件：

```bash
cat /data/testagent/data/.serverid    # test-agent-backend-122-233-30-114
cat /data/testagent/data/.serverhost  # 122.233.30.114
```

## 验收清单

### 网络与基础服务

在 `122.233.30.2`：

```bash
curl -fsS http://122.233.30.4:8080/actuator/health
curl -fsS http://122.233.30.114:8080/actuator/health
curl -fsS http://122.233.30.2/
```

在 `122.233.30.4`：

```bash
curl -fsS http://122.233.30.114:8080/actuator/health
curl -fsS http://122.233.30.20:6379 || true
nc -vz 122.42.203.103 8000
```

在 `122.233.30.114`：

```bash
curl -fsS http://122.233.30.4:8080/actuator/health
curl -fsS http://122.233.30.20:6379 || true
nc -vz 122.42.203.103 8000
```

Redis 不是 HTTP 服务，`curl` 失败不一定代表端口不通；以 `nc -vz 122.233.30.20 6379` 或 Redis 客户端为准。

### Java

两台后端分别执行：

```bash
systemctl status test-agent-backend --no-pager
journalctl -u test-agent-backend -n 120 --no-pager
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS http://127.0.0.1:8080/actuator/health/readiness
cat /data/testagent/data/.serverid
cat /data/testagent/data/.serverhost
```

### worker / manager

两台后端分别执行：

```bash
cd /data/testagent/deploy/internal
./opencode-worker-docker.sh --env-file /data/testagent/config/docker.env status
docker logs --tail 200 test-agent-opencode-worker | egrep 'config update applied|websocket|serverhost|serverid|OPENCODE_UNAVAILABLE'
docker exec test-agent-opencode-worker cat /data/testagent/data/.serverid
docker exec test-agent-opencode-worker cat /data/testagent/data/.serverhost
```

预期日志包含：

```text
manager config update applied
```

### 平台页面

超级管理员进入：

- 系统管理 -> 运行管理：应看到两台 Java 后端、两个 worker 容器、两个 manager 连接。
- 系统管理 -> 配置管理 -> opencode 公共配置管理：确认 `122.233.30.4` 和 `122.233.30.114` 都初始化成功。

## 常见问题排查

### `ssh-copy-id` 不生效或 `Permission denied (publickey,gssapi-keyex,gssapi-with-mic)`

原因：前端机 `122.233.30.2` 受统一登录或堡垒机策略控制，禁止 `122.233.30.4` 直接通过普通公钥登录。

处理：不要从后端机 scp 前端包。把 zip 通过允许的通道放到 `122.233.30.2:/data/0709/internal.zip`，然后在前端机本地执行：

```bash
unzip -p /data/0709/internal.zip deploy/internal/deploy-internal-frontend.sh > /tmp/deploy-internal-frontend.sh
bash /tmp/deploy-internal-frontend.sh --archive /data/0709/internal.zip
```

后端机执行时统一加：

```bash
--skip-frontend
```

### `122.233.30.114` worker 一直连不上 Java

检查顺序：

```bash
grep -E 'TEST_AGENT_SERVER_ADVERTISED_HOST|TEST_AGENT_LINUX_SERVER_ID|SYS_DATA_ROOT_DIR|TEST_AGENT_OPENCODE_MANAGER_TOKEN' /data/testagent/config/backend.env
grep -E 'TEST_AGENT_DATA_ROOT|OPENCODE_WORKER_BACKEND_PORT|TEST_AGENT_OPENCODE_MANAGER_TOKEN' /data/testagent/config/docker.env
cat /data/testagent/data/.serverid
cat /data/testagent/data/.serverhost
docker exec test-agent-opencode-worker cat /data/testagent/data/.serverhost
docker logs --tail 200 test-agent-opencode-worker
```

`backend.env` 的 `SYS_DATA_ROOT_DIR` 必须等于 `docker.env` 的 `TEST_AGENT_DATA_ROOT`。`122.233.30.114` 上 `.serverhost` 必须是 `122.233.30.114`，不能是 `122.233.30.4`。

### 前端 502 或只访问到一台后端

在 `122.233.30.2`：

```bash
nginx -T | sed -n '/upstream test_agent_backend/,/}/p'
curl -fsS http://122.233.30.4:8080/actuator/health
curl -fsS http://122.233.30.114:8080/actuator/health
tail -n 100 /var/log/nginx/error.log
```

如果其中一台后端不可达，先不要怀疑前端包；先修 Java 进程、端口、防火墙或后端间网络。

### 运行管理只有一台服务器

检查：

```bash
# 两台后端都要能连 Redis
nc -vz 122.233.30.20 6379

# 两台后端配置里都要开启
grep -E 'TEST_AGENT_RUN_EVENT_REDIS_BUS_ENABLED|TEST_AGENT_SERVER_BROADCAST_ENABLED' /data/testagent/config/backend.env

# 两台后端健康检查都要通过
curl -fsS http://127.0.0.1:8080/actuator/health/readiness
```

还要确认 `TEST_AGENT_LINUX_SERVER_ID` 不重复；`122.233.30.4` 和 `122.233.30.114` 必须分别是不同值。

### 用户初始化报公共配置未初始化

每台服务器本地都有自己的公共配置目录。新增 `122.233.30.114` 后，需要超级管理员在“系统管理 -> 配置管理 -> opencode 公共配置管理”里对 `test-agent-backend-122-233-30-114` 初始化公共配置。只初始化 `122.233.30.4` 不够。

## 回滚

前端脚本会保留：

```text
/data/testagent/frontend.bak.<timestamp>
/data/testagent/deploy/internal.bak.<timestamp>
```

后端脚本会保留：

```text
/data/testagent/dist/backend/test-agent-app.jar.bak.<timestamp>
/data/testagent/deploy/internal.bak.<timestamp>
```

回滚 Java：

```bash
systemctl stop test-agent-backend
cp /data/testagent/dist/backend/test-agent-app.jar.bak.<timestamp> /data/testagent/dist/backend/test-agent-app.jar
systemctl start test-agent-backend
curl -fsS http://127.0.0.1:8080/actuator/health/readiness
```

回滚前端：

```bash
rm -rf /data/testagent/frontend
cp -a /data/testagent/frontend.bak.<timestamp> /data/testagent/frontend
nginx -t
systemctl reload nginx
```
