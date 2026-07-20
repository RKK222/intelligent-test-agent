# 企业内单后台部署

本文用于一台 Java 后台同时承载本机 `opencode-worker` 的正式部署。以下以当前现场为例：

| 角色 | 地址 |
|---|---|
| 浏览器入口 | `http://mimo.sdc.cs.icbc:9996` |
| 企业入口到实体 Nginx | `mimo.sdc.cs.icbc:9996 -> 122.233.30.2:80` |
| 前端实体 Nginx | `122.233.30.2:80`，安装目录 `/data/apps/nginx` |
| Java 后台 + worker | `122.233.30.114` |
| Redis | `122.233.30.20:6379` |
| PostgreSQL | `122.233.30.147:5432/postgres` |
| XXL MySQL | `122.233.30.148:3306/xxl_job`（外部共享 MySQL 8.4） |
| 企业内部模型 | `ai-code.sdc.enterprise:9070` |

## 当前现场问题结论

| 现象 | 根因/边界 | 正确处理 | 重启范围 |
|---|---|---|---|
| Tool 调外部接口时容器 `connect timeout`，宿主机可达 | Docker bridge 转发或源地址伪装缺失；目标 IP/端口会变化 | 按 Docker 源网段配置持久 `FORWARD + MASQUERADE`，再用同一 URL 对比宿主机/容器 | 规则即时生效，通常不重启服务 |
| 域名访问出现 `ERR_NAME_NOT_RESOLVED` | 浏览器终端没有解析到企业入口 | 由 DNS/入口管理方修复解析；应用方先用 `nslookup` 验证 | 不重启应用 |
| 登录仍请求 `http://122.233.30.2/api/...` 并报 CORS | 旧前端包固化了 IP 基址，后端 Origin 也未对齐 | 以 `http://mimo.sdc.cs.icbc:9996` 重打前端，CORS 精确配置同一 origin | reload Nginx；CORS 变更需重启 Java |
| 不使用 HTTPS 但服务器终端不可用 | 通用模板安全默认只接受 WSS | 当前现场显式允许不安全 WebSocket，并保证浏览器直达 `.114:8080` | 重启 Java |
| 前端部署报 Nginx 未 include 新 `.conf` | 主配置显式加载单个 `test-agent.conf`，不是目录通配 | 使用已加载的 `/data/apps/nginx/conf/test-agent.conf`；实体 Nginx 保持 `80` | 前端脚本 reload Nginx |
| 执行 restart 后启动时间/JAR 时间仍旧 | unit、JAR 路径、端口监听 PID 或文件替换没有形成同一证据链 | 同时核对 systemd `MainPID`、`ExecMainStartTimestamp`、8080 PID 和 ZIP/安装 JAR SHA | 按实际未生效组件重新部署 |
| 内部模型 token 或供应商标识报错 | 上游 token 属于数据库供应商配置，`qwen-prod/deepseek-prod` 属于 Java 路由；都不属于 `docker.env` | 在“内部模型供应商”维护并刷新 Java 内存；公共 JSONC 保留准确 provider header | 通常不重启；广播失败时重启 Java |

以下各节给出可复制的最终配置、操作和预期结果。

## 1. 拓扑与端口

```text
浏览器
  -> http://mimo.sdc.cs.icbc:9996 企业入口
  -> 122.233.30.2:80 实体 Nginx
  -> 122.233.30.114:8080 Java
       -> PostgreSQL / Redis / XXL MySQL
  -> /xxl-job-admin/ -> 122.233.30.114:18080 Admin

122.233.30.114:9999 XXL executor
  <- 122.233.30.114:18080 Admin

122.233.30.114 opencode-worker
  <-> 122.233.30.114:8080 Java manager WebSocket
  -> 4096-4115 用户 OpenCode 进程（20 个端口）
  -> 122.233.30.114:8080 Java 内部模型代理
       -> ai-code.sdc.enterprise:9070
```

网络要求：

- 浏览器所在终端必须能解析 `mimo.sdc.cs.icbc`，并能访问该入口的 `9996` 端口；DNS 只负责名称解析，`9996 -> 122.233.30.2:80` 由企业入口或网络转发层负责。
- `.2` 能访问 `.114:8080`。
- `.2` 能访问 `.114:18080`，用于同源 `/xxl-job-admin/` 代理；该端口不直接暴露给浏览器。
- `.114:18080` 能访问 `.114:9999`；executor 端口只对白名单 Admin 网络开放。
- worker 容器能访问 `.114:8080`。
- `.114` 能访问 PostgreSQL、Redis、外部 XXL MySQL 和 `ai-code.sdc.enterprise:9070`。
- 自定义 Tool 访问任意企业外部接口时，若宿主机可达而 worker 容器超时，必须为 Docker bridge 源网段配置 `FORWARD` 和 `MASQUERADE`，不能按会变化的目标 IP 或端口逐条放行。
- `4096-4115` 的宿主机端口与容器端口必须同号映射。
- `9070` 只需要 Java 宿主机出站可达，不对外发布。
- 不启用 `--network host`，不部署 `19070` relay，不修改 worker 网络模式。

当前现场明确不使用 HTTPS。浏览器登录请求和服务器终端内容会分别通过 HTTP、`ws://` 明文传输，只能在已接受该风险的可信内网使用；服务器终端还要求浏览器网段可以直达 `122.233.30.114:8080`。

## 2. Mac 打包与分发

在 Mac 仓库根目录执行：

```bash
cd /Users/kaka/Desktop/intelligent-test-agent
VITE_TEST_AGENT_API_BASE_URL= \
  deploy/internal/package-release.sh --output-dir deploy/internal/dist
```

Mac 本地会生成以下产物：

```text
deploy/internal/dist/test-agent-internal-release.zip
deploy/internal/dist/test-agent-internal-release.zip.sha256
deploy/internal/dist/backend/test-agent-app.jar
deploy/internal/dist/backend/lib/
deploy/internal/dist/test-agent-frontend-dist.tar.gz
deploy/internal/dist/test-agent-programs.tar.gz
deploy/internal/dist/test-agent-opencode-worker_internal-linux-amd64.tar
deploy/internal/dist/frontend/
```

完整 ZIP 已包含 Java JAR、`backend/lib/`、前端静态包、programs、worker 镜像、`deploy/internal/` 部署脚本和配置模板。内网只需传完整 ZIP 与 SHA 文件，分别复制到：

```text
122.233.30.2:/data/0709/
122.233.30.114:/data/0709/
```

两台服务器都先校验：

```bash
cd /data/0709
sha256sum -c test-agent-internal-release.zip.sha256
unzip -t test-agent-internal-release.zip
```

后续命令假定文件名为：

```text
/data/0709/test-agent-internal-release.zip
```

## 3. 配置后台

在 `.114` 创建 `/data/testagent/config/backend.env`。下面是可整文件替换的完整生产配置；只需要替换 PostgreSQL 密码、manager token、内部代理 key、XXL MySQL 密码和 XXL access token 这 5 个 `REPLACE_...` 值。模板按 Redis 无密码、平台 API token 为空填写；如果现网这两项非空，必须保留现网值。替换前先备份：

```bash
install -d -m 0755 /data/testagent/config
cp -a /data/testagent/config/backend.env \
  /data/testagent/config/backend.env.bak.$(date +%Y%m%d%H%M%S) 2>/dev/null || true
```

平台 RSA 私钥固定内置在交付 JAR 的 `BOOT-INF/classes/rsa-private.key`；不要在 `backend.env` 配置 `TEST_AGENT_SSH_RSA_PRIVATE_KEY_PATH`。升级必须使用本交付包完整替换 JAR 与依赖，替换内置密钥会让既有数据库 SSH key 密文无法解密。

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
TEST_AGENT_XXL_JOB_MYSQL_URL=jdbc:mysql://122.233.30.148:3306/xxl_job?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
TEST_AGENT_XXL_JOB_MYSQL_USERNAME=xxl_job
TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=REPLACE_XXL_JOB_MYSQL_PASSWORD
TEST_AGENT_XXL_JOB_ACCESS_TOKEN=REPLACE_XXL_JOB_ACCESS_TOKEN
TEST_AGENT_XXL_JOB_ADMIN_PORT=18080
TEST_AGENT_XXL_JOB_ADMIN_ADDRESSES=http://122.233.30.114:18080/xxl-job-admin
TEST_AGENT_XXL_JOB_EXECUTOR_PORT=9999
TEST_AGENT_XXL_JOB_EXECUTOR_ADDRESS=http://122.233.30.114:9999

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
TEST_AGENT_MAX_FILE_BYTES=1048576
TEST_AGENT_MAX_DIRECTORY_ENTRIES=1000

TEST_AGENT_BACKEND_HEARTBEAT_INTERVAL=5s
TEST_AGENT_BACKEND_STALE_AFTER=10s
TEST_AGENT_OPENCODE_MANAGER_COMMAND_TIMEOUT=10s
TEST_AGENT_BACKEND_DISCOVERY_LIMIT=100

TEST_AGENT_SERVER_TERMINAL_ENABLED=true
TEST_AGENT_SERVER_TERMINAL_WORKING_DIRECTORY=/data/testagent
TEST_AGENT_SERVER_TERMINAL_PUBLIC_WEBSOCKET_BASE_URL=
TEST_AGENT_SERVER_TERMINAL_ALLOW_INSECURE_WEBSOCKET=true

TEST_AGENT_SCHEDULER_ENABLED=true
TEST_AGENT_SCHEDULER_SCAN_INTERVAL=30s
TEST_AGENT_SCHEDULER_USER_PLAN_RUN_LIMIT=50
TEST_AGENT_SCHEDULER_USER_PLAN_WORKER_COUNT=4
TEST_AGENT_SCHEDULER_USER_PLAN_QUEUE_CAPACITY=100
```

关键约束：

- `TEST_AGENT_SERVER_ADVERTISED_HOST` 必须是 worker 和其他服务器可访问的真实地址，不能写 `127.0.0.1`。
- `TEST_AGENT_LINUX_SERVER_ID` 是服务器长期稳定身份，升级时不得改变。
- `backend.env` 不得包含 `TEST_AGENT_SSH_RSA_PRIVATE_KEY_PATH`；Java 日志必须显示从 `classpath:rsa-private.key` 加载。
- XXL executor 固定使用 `.114:9999` 可达地址，不写 `TEST_AGENT_LINUX_SERVER_ID` 或其它亲和字段；稳定服务器亲和只属于旧 scheduler 的夜间 `USER_PLAN`。
- 当前 HTTP 现场必须同时保留空的 `TEST_AGENT_SERVER_TERMINAL_PUBLIC_WEBSOCKET_BASE_URL` 和显式的 `TEST_AGENT_SERVER_TERMINAL_ALLOW_INSECURE_WEBSOCKET=true`；缺一项都会按安全默认拒绝不安全终端。签票后浏览器直连 `ws://122.233.30.114:8080`，不是经 `mimo.sdc.cs.icbc:9996` 转发。
- 企业模型供应商地址和上游 token 在“内部模型供应商”页面维护，不写入 `backend.env` 或 `docker.env`。

保存后先确认没有遗留占位符：

```bash
if grep -n 'REPLACE_' /data/testagent/config/backend.env; then
  echo 'backend.env 仍有未替换配置' >&2
  exit 1
fi
```

## 4. 配置 worker

在 `.114` 创建 `/data/testagent/config/docker.env`。下面是可整文件替换的完整配置；只需把 `REPLACE_MANAGER_TOKEN` 替换成 `backend.env` 中的同一个值：

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

`TEST_AGENT_DATA_ROOT` 必须与 Java 的 `SYS_DATA_ROOT_DIR` 完全一致；每个稳定服务器身份只运行一个 worker。当前 worker 不读取旧的 `TEST_AGENT_BACKEND`，而是读取 Java 写出的 `.serverhost` 再结合 `OPENCODE_WORKER_BACKEND_PORT` 连接本机 Java，因此不要恢复旧变量。

端口池扩容后还要由超级管理员在“系统管理 → 通用参数”把 `OPENCODE_MANAGER_MAX_PROCESSES` 调整为 `20`。该参数是实际并发上限；如果仍为 `8`，即使已经映射 20 个端口，manager 也只允许 8 个进程。保存后会热推给在线 manager，无需重启 Java；运行管理中的 manager `maxProcesses` 应显示 `20`。

### 4.1 worker 容器访问动态外部接口

只有“`.114` 宿主机访问同一 URL 成功、worker 容器访问超时”时才执行本节。目标 IP 和端口会变化，因此规则只按 Docker bridge 源网段放行出站并做源地址伪装，不绑定某一个目标地址。该规则扩大了 worker 的出站范围，生产执行前应由网络安全管理员确认。

在 `.114` 写入持久脚本：

```bash
install -d -m 0755 /usr/local/sbin
install -m 0755 /dev/stdin /usr/local/sbin/test-agent-docker-egress.sh <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

network=bridge
subnet="$(docker network inspect "${network}" --format '{{(index .IPAM.Config 0).Subnet}}')"
bridge="$(docker network inspect "${network}" --format '{{index .Options "com.docker.network.bridge.name"}}')"
if [[ -z "${bridge}" || "${bridge}" == '<no value>' ]]; then
  bridge=docker0
fi
egress_if="$(ip -4 route show default | awk 'NR == 1 {print $5}')"

[[ -n "${subnet}" && -n "${egress_if}" ]] || {
  echo '无法识别 Docker bridge 网段或默认出站网卡' >&2
  exit 1
}

sysctl -w net.ipv4.ip_forward=1 >/dev/null
chain=FORWARD
if iptables -w -nL DOCKER-USER >/dev/null 2>&1; then
  chain=DOCKER-USER
fi

iptables -w -C "${chain}" -i "${bridge}" -o "${egress_if}" -s "${subnet}" -j ACCEPT 2>/dev/null || \
  iptables -w -I "${chain}" 1 -i "${bridge}" -o "${egress_if}" -s "${subnet}" -j ACCEPT
iptables -w -C "${chain}" -i "${egress_if}" -o "${bridge}" -d "${subnet}" \
  -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT 2>/dev/null || \
  iptables -w -I "${chain}" 1 -i "${egress_if}" -o "${bridge}" -d "${subnet}" \
    -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT
iptables -w -t nat -C POSTROUTING -s "${subnet}" -o "${egress_if}" -j MASQUERADE 2>/dev/null || \
  iptables -w -t nat -A POSTROUTING -s "${subnet}" -o "${egress_if}" -j MASQUERADE

printf 'docker subnet=%s bridge=%s egress=%s chain=%s\n' \
  "${subnet}" "${bridge}" "${egress_if}" "${chain}"
EOF
```

注册为开机规则；如果现场使用统一防火墙平台，应将等价规则交由该平台持久化，避免 firewalld 策略刷新后覆盖本机规则：

```bash
install -m 0644 /dev/stdin /etc/systemd/system/test-agent-docker-egress.service <<'EOF'
[Unit]
Description=Test Agent Docker egress rules
Wants=network-online.target docker.service
After=network-online.target docker.service firewalld.service

[Service]
Type=oneshot
ExecStart=/usr/local/sbin/test-agent-docker-egress.sh
RemainAfterExit=yes

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable --now test-agent-docker-egress.service
systemctl status test-agent-docker-egress.service --no-pager
```

预期 unit 为 `active (exited)`，日志打印实际 Docker 网段、bridge 和出站网卡。用同一个真实 Tool URL 分别验证宿主机和容器：

```bash
curl -v --connect-timeout 10 'http://<实际工具地址>:<实际端口>/<path>'

docker exec test-agent-opencode-worker node -e '
const url = process.argv[1]
fetch(url, { signal: AbortSignal.timeout(10000) })
  .then((response) => console.log(`HTTP ${response.status}`))
  .catch((error) => { console.error(error); process.exit(1) })
' 'http://<实际工具地址>:<实际端口>/<path>'
```

容器返回任意真实 HTTP 状态（包括 `401`、`403`、`404`）都表示 TCP/HTTP 已经可达；继续出现 `connect timeout` 时检查企业路由、防火墙或目标服务白名单，而不是继续增加固定目标 IP 规则。

## 5. 配置前端 Nginx

当前实体 Nginx 主配置已经确认只显式加载：

```nginx
include /data/apps/nginx/conf/test-agent.conf;
```

这不是 `*.conf` 通配加载。同目录新增 `/data/apps/nginx/conf/test-agent-gateway.conf` 即使 `nginx -t` 成功也不会生效。先确认现有文件只承载本应用并备份；如果其中还有其他系统的配置，停止覆盖并由 Nginx 管理方拆出专用通配 include。

```bash
sed -n '1,260p' /data/apps/nginx/conf/test-agent.conf
cp -a /data/apps/nginx/conf/test-agent.conf \
  /data/apps/nginx/conf/test-agent.conf.before-deploy.$(date +%Y%m%d%H%M%S)
```

当前现场在前端 `.2` 创建 `/data/testagent/config/nginx.env`。以下内容可以整文件替换：

```dotenv
TEST_AGENT_NGINX_MODE=single
TEST_AGENT_NGINX_BACKENDS=122.233.30.114:8080
TEST_AGENT_NGINX_SERVER_ROUTES=test-agent-backend-122-233-30-114=122.233.30.114:8080
TEST_AGENT_NGINX_XXL_JOB_ADMINS=122.233.30.114:18080
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

实体 Nginx 同时监听 `80` 和 `9996`：企业域名入口按现有链路落到 `.2:80`，IP 入口直接使用 `http://122.233.30.2:9996`。`TEST_AGENT_NGINX_SERVER_ROUTES` 是普通 HTTP、SSE 和服务器终端共用的 `linuxServerId -> Java endpoint` 白名单；已知 ID 进入对应专用 upstream，缺失或未知 ID 仍进入默认 upstream。Nginx 发往 Java 前会删除 `X-Test-Agent-Linux-Server-Id` 和外部传入的 `X-Test-Agent-Backend-Routed`。前端部署脚本会调用 [configure-nginx.sh](configure-nginx.sh)，自动渲染 [nginx/gateway.conf.template](nginx/gateway.conf.template)、再次备份旧配置、用实体 Nginx 执行 `-t/-T`、确认该文件确实已被 include，并 reload；失败会恢复旧配置。

从旧包升级时，把 `nginx.env` 中的 `TEST_AGENT_NGINX_TERMINAL_ROUTES` 原键名改为 `TEST_AGENT_NGINX_SERVER_ROUTES`，右侧值不变，不能同时保留两个键。先执行 `bash /data/testagent/deploy/internal/configure-nginx.sh --env-file /data/testagent/config/nginx.env --validate-only`，预期 `server route count: 1`，再按本节前端部署命令正式安装并 reload。

`/xxl-job-admin/` 与静态前端必须保持同一浏览器 origin；Nginx 会保留表单 POST、重定向和安全 Cookie。后台 `backend.env` 还必须配置 XXL MySQL 凭据、共享 access token、`ADMIN_PORT=18080`、`EXECUTOR_PORT=9999`、Admin 地址与 executor 可达地址，具体模板以 `backend.env.example` 为准。

也可从交付包生成同一份环境文件，但必须明确传入已经加载的文件，不能依赖自动目录探测：

```bash
bash /tmp/test-agent-release-config/deploy/internal/configure-single-deployment.sh \
  frontend \
  --nginx-home /data/apps/nginx \
  --gateway-conf /data/apps/nginx/conf/test-agent.conf
```

## 6. 部署与重启

先在前端 `.2` 部署静态文件：

```bash
unzip -p /data/0709/test-agent-internal-release.zip \
  deploy/internal/deploy-internal-frontend.sh \
  > /tmp/deploy-internal-frontend.sh
bash /tmp/deploy-internal-frontend.sh \
  --archive /data/0709/test-agent-internal-release.zip \
  --validate-only
bash /tmp/deploy-internal-frontend.sh \
  --archive /data/0709/test-agent-internal-release.zip \
  --nginx-env /data/testagent/config/nginx.env \
  --frontend-health-url http://127.0.0.1/health \
  --frontend-url http://127.0.0.1/
```

预期看到 `Installed single gateway ... /data/apps/nginx/conf/test-agent.conf` 和 `Frontend deployment finished`。脚本自动产生的 `frontend.bak.<时间>`、`deploy/internal.bak.<时间>` 和 `test-agent.conf.bak.<时间>` 都是回滚备份，不是公共 Agent 配置，也不会被前端展示；完成验收并度过回滚窗口后可按现场保留策略清理旧备份。

再在后台 `.114` 部署。脚本会严格按 Java、身份文件、worker 的顺序执行：

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

后台部署脚本在替换 JAR 前会校验已有 systemd unit 的 `ExecStart` 和 `EnvironmentFile`，执行 `systemctl stop` 后检查 `8080`。若端口仍由同一路径的 `test-agent-app.jar` 占用，脚本会先 TERM、超时后仅对仍匹配该 JAR 的 PID 执行 KILL；若是其他程序占用则拒绝误杀。启动后还会确认 systemd `MainPID` 正是 `8080` 的监听进程，避免旧手工 Java 让 health 误通过。

如果现场此前已经启用了 `.4` 双后台，最后在 `.4` 停止 worker 和 Java，但保留 `/data/testagent` 数据以便回滚：

```bash
if [[ -x /data/testagent/deploy/internal/opencode-worker-docker.sh ]]; then
  /data/testagent/deploy/internal/opencode-worker-docker.sh \
    --env-file /data/testagent/config/docker.env stop
else
  docker rm -f test-agent-opencode-worker 2>/dev/null || true
fi

systemctl disable --now test-agent-backend
systemctl is-enabled test-agent-backend || true
systemctl is-active test-agent-backend || true
docker ps -a --filter 'name=^/test-agent-opencode-worker$'
ss -lntp 'sport = :8080' || true
```

预期 Java 为 `disabled`、`inactive`，worker 容器列表为空，`.4:8080` 不再监听。不要删除 `.4` 的数据、配置或 JAR；确认单后台稳定后再按现场保留策略处理。

需要手工重启时执行：

```bash
systemctl restart test-agent-backend
curl -fsS http://127.0.0.1:8080/actuator/health/readiness
cat /data/testagent/data/.serverid
cat /data/testagent/data/.serverhost

cd /data/testagent/deploy/internal
./opencode-worker-docker.sh \
  --env-file /data/testagent/config/docker.env \
  restart
```

身份文件必须为：

```text
/data/testagent/data/.serverid   = test-agent-backend-122-233-30-114
/data/testagent/data/.serverhost = 122.233.30.114
```

如果不一致，先修 `backend.env` 并重启 Java，不要启动 worker。

## 7. 公共 OpenCode 和模型配置

超级管理员进入“系统管理 → 配置管理 → opencode 公共配置管理”，在 `test-agent-backend-122-233-30-114` 初始化或更新公共配置。公共 `opencode.jsonc` 使用 [opencode.jsonc.example](opencode.jsonc.example)，生产模型固定为：

完整配置已经放进交付 ZIP，不需要手工拼 JSONC。在 `.114` 直接导出全文，然后将 `/tmp/opencode.jsonc` 全文粘贴到公共配置编辑器并保存：

```bash
unzip -p /data/0709/test-agent-internal-release.zip \
  deploy/internal/opencode.jsonc.example \
  >/tmp/opencode.jsonc
sed -n '1,220p' /tmp/opencode.jsonc
```

其中 `{env:TEST_AGENT_INTERNAL_PROXY_BASE_URL}`、`{env:TEST_AGENT_INTERNAL_PROXY_API_KEY}`、`{env:ENTERPRISE_UCID}` 必须原样保留，它们不是待替换占位符，而是 Java 在启动每个用户 OpenCode 进程时动态注入的逐进程环境变量。

```text
enterprise-qwen/Qwen3.6-27B
enterprise-deepseek/DeepSeek-V4-Flash-W8A8
```

数据库供应商路由固定为：

```text
qwen-prod
deepseek-prod
```

“内部模型供应商”页面按下面两行填写；两行可以使用同一个现有上游 token，也可以分别使用供应商实际 token：

| Provider ID | 名称 | Base URL | Token | 启用 | 排序 |
|---|---|---|---|---|---:|
| `qwen-prod` | `企业通义` | `http://ai-code.sdc.icbc:9070/enterprise/jdt/model/api/openai/v1` | `REPLACE_QWEN_UPSTREAM_TOKEN` | 是 | `1` |
| `deepseek-prod` | `企业 DeepSeek` | `http://ai-code.sdc.icbc:9070/enterprise/jdt/model/api/openai/v1` | `REPLACE_DEEPSEEK_UPSTREAM_TOKEN` | 是 | `2` |

三层配置必须同时正确：

| 配置层 | 正确内容 | 生效方式 |
|---|---|---|
| 本服务器公共 `opencode.jsonc` | `enterprise-qwen/Qwen3.6-27B`、`enterprise-deepseek/DeepSeek-V4-Flash-W8A8`，并包含 `includeUsage=false` | 重启已有用户 OpenCode 进程；新进程直接读取 |
| 共享数据库内部供应商 | `qwen-prod`、`deepseek-prod` 均启用，`baseUrl=http://ai-code.sdc.icbc:9070/enterprise/jdt/model/api/openai/v1`，全局 token 已配置 | 保存或点击“刷新 Java 内存”；不需要重启用户进程 |
| 用户进程环境 | Java 启动进程时注入内部代理 key、同节点 Java 代理地址和该用户的 `ENTERPRISE_UCID` | 停止并通过运行管理重新启动用户进程 |

`enterprise-qwen` / `enterprise-deepseek` 只用于 OpenCode 模型目录；`qwen-prod` / `deepseek-prod` 只用于 Java 路由，二者不能互换。上游模型 token 由 Java 作为 `Authorization: Bearer <token>` 注入，不在 `backend.env`、`docker.env` 或 `opencode.jsonc` 中再配 `Auth-Token`。每个用户的 UCID 来自用户表，由 Java 逐进程注入，不需要也不能为所有用户共用一个 env 文件。

更新公共配置后，要在运行管理中重启已有用户 OpenCode 进程；只重启 Java 不会让已运行的 OpenCode 重新读取公共配置。供应商地址、启用状态或 token 变化后先点击“刷新 Java 内存”；单后台广播异常时可直接重启 Java 重新加载数据库快照。

公共自定义 Tool 文件放在本服务器公共配置的 `tools/*.ts`，项目专用 Tool 放在工作区 `.opencode/tools/*.ts`。当前企业包已离线内置 `@opencode-ai/plugin`、`@opencode-ai/sdk`、`effect`、`zod` 及传递依赖；Tool 使用 Node 22 自带 `fetch` 不需要另加包。仅修改 Tool 文件时，由超级管理员保存并重启相关用户 OpenCode 进程即可；若 Tool 新增了上述基线之外的第三方 import，则必须重新打包并部署 programs/worker，不能在内网执行 `npm install`。

## 8. 变更与重启判断

| 变更内容 | 必须执行 | 不需要执行 |
|---|---|---|
| 仅前端静态包、`nginx.env` 或 Nginx conf | 重新执行前端部署脚本；脚本会校验并 reload 实体 Nginx | Java、worker 不重启 |
| `backend.env`、JAR 或 `backend/lib/` | `systemctl restart test-agent-backend`，确认 readiness 和身份文件；完整版本升级再按顺序重启 worker | 无前端变更时不 reload Nginx |
| 仅 `docker.env`、programs 或 worker 镜像 | 用 `opencode-worker-docker.sh ... restart`，不要手工运行 `opencode-manager` | Java 配置未变时不重启 Java |
| 内部模型供应商地址、启用状态或 token | 管理页面保存并“刷新 Java 内存”；广播异常时重启 Java | 不因 token 变化重打前端或 worker |
| 公共 `opencode.jsonc`、Agent、Skill、Tool | 超管保存/推送；公共发布按 rollout 生效，必要时在运行管理重启受影响的用户 OpenCode 进程 | 只重启 Java 不能让已运行用户进程重新读配置 |
| Docker `FORWARD/MASQUERADE` | 启用并验证 `test-agent-docker-egress.service`；对已经运行的容器即时生效 | 通常不需要重启 Java、worker 或 Docker daemon |

`systemctl status` 只显示 unit 当前状态，不能证明新 JAR 已经替换且新进程占有 `8080`。Java 更新后在 `.114` 交叉检查：

```bash
systemctl show test-agent-backend \
  -p ActiveState -p MainPID -p ExecMainStartTimestamp \
  -p ExecStart -p EnvironmentFiles

pid="$(systemctl show test-agent-backend -p MainPID --value)"
tr '\0' ' ' <"/proc/${pid}/cmdline"
ss -lntp 'sport = :8080'
stat /data/testagent/dist/backend/test-agent-app.jar

unzip -p /data/0709/test-agent-internal-release.zip \
  dist/backend/test-agent-app.jar | sha256sum
sha256sum /data/testagent/dist/backend/test-agent-app.jar
```

预期 `ExecMainStartTimestamp` 是本次重启时间，`MainPID` 同时出现在 `8080` 监听结果中，命令行指向 `/data/testagent/dist/backend/test-agent-app.jar`，并且 ZIP 内 JAR 与已安装 JAR 的 SHA-256 一致。JAR 修改时间仍是旧时间或两个 SHA 不同，表示部署脚本没有完成文件替换，不能用 health 返回 200 当作升级成功。

## 9. 验收

在 `.114` 执行：

```bash
systemctl status test-agent-backend --no-pager
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS http://127.0.0.1:8080/actuator/health/readiness
cat /data/testagent/data/.serverid
cat /data/testagent/data/.serverhost

cd /data/testagent/deploy/internal
./opencode-worker-docker.sh --env-file /data/testagent/config/docker.env status
docker logs --tail 200 test-agent-opencode-worker | \
  egrep 'config update applied|websocket|serverhost|serverid|OPENCODE_UNAVAILABLE'

nc -vz ai-code.sdc.enterprise 9070
```

预期 worker 日志出现当前结构化事件 `event=manager_config_update status=applied`；旧版 worker 可能输出 `manager config update applied`，部署脚本兼容两者。再在管理页面确认一个 Java、一个 manager、一个容器均在线；初始化一个用户 OpenCode 进程后，用其实际动态端口检查：

```bash
curl -fsS http://127.0.0.1:<实际端口>/global/health
curl -fsS http://127.0.0.1:<实际端口>/api/provider
curl -fsS http://127.0.0.1:<实际端口>/api/model
```

最后从前端分别新建 Qwen 和 DeepSeek 会话，验证普通回答、think/reasoning、工具调用、持续 SSE 和 `[DONE]`。

如果前端仍报 400，先在 `.114` 绕过 OpenCode、直接调用 Java 代理；将 key 和 UCID 替换为现场值：

```bash
curl -iN --max-time 180 \
  http://127.0.0.1:8080/api/internal/platform/opencode-runtime/internal-model-proxy/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -H 'Authorization: Bearer <backend.env 中的 TEST_AGENT_INTERNAL_PROXY_API_KEY>' \
  -H 'X-Enterprise-Model-Provider: qwen-prod' \
  -H 'ucid: <现场用户 UCID>' \
  --data '{"model":"Qwen3.6-27B","messages":[{"role":"user","content":"你好"}],"stream":true}'

curl -iN --max-time 180 \
  http://127.0.0.1:8080/api/internal/platform/opencode-runtime/internal-model-proxy/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -H 'Authorization: Bearer <backend.env 中的 TEST_AGENT_INTERNAL_PROXY_API_KEY>' \
  -H 'X-Enterprise-Model-Provider: deepseek-prod' \
  -H 'ucid: <现场用户 UCID>' \
  --data '{"model":"DeepSeek-V4-Flash-W8A8","messages":[{"role":"user","content":"你好"}],"stream":true}'
```

Java 代理正常时应持续收到单层 `data:`，思考内容位于 `reasoning_content`，最后收到单层 `data: [DONE]`。不要把测试地址改成 `9070`：该步骤就是验证 `OpenCode -> Java:8080 -> 9070` 的正式链路。

正式链路验证通过后确认临时 relay 已删除：

```bash
docker rm -f test-agent-model-relay 2>/dev/null || true
ss -lntp | grep 19070
```

第二条应无输出。

在 `.2` 执行实体 Nginx 验收：

```bash
/data/apps/nginx/sbin/nginx \
  -p /data/apps/nginx/ \
  -c /data/apps/nginx/conf/nginx.conf \
  -T 2>&1 | grep -F \
  '# configuration file /data/apps/nginx/conf/test-agent.conf:'

/data/apps/nginx/sbin/nginx \
  -p /data/apps/nginx/ \
  -c /data/apps/nginx/conf/nginx.conf \
  -t

ss -lntp | grep -E '(:80[[:space:]]|:80$)'
curl -fsS http://127.0.0.1/health
curl -fsS http://127.0.0.1/
curl -fsS http://122.233.30.114:8080/actuator/health
```

预期 `-T` 明确列出 `test-agent.conf`、语法校验成功、实体 Nginx 监听 `80`、前端与反代健康均成功。再从实际浏览器终端验收外层入口：

```bash
nslookup mimo.sdc.cs.icbc
curl -v --connect-timeout 10 http://mimo.sdc.cs.icbc:9996/health
curl -I http://mimo.sdc.cs.icbc:9996/
```

预期域名能解析，入口返回 `200`。如果 `nslookup` 失败或浏览器报 `ERR_NAME_NOT_RESOLVED`，故障在终端 DNS/企业入口，不在 Java 或实体 Nginx；应用部署方无法通过修改 Nginx 修复客户端名称解析。

最后检查 CORS 和编译期地址。浏览器 Network 中登录 API 应保持 `http://mimo.sdc.cs.icbc:9996/api/...` 同域，不应再请求 `http://122.233.30.2/api/...`：

```bash
curl -i -X OPTIONS \
  http://127.0.0.1/api/auth/login-by-unified-auth \
  -H 'Origin: http://mimo.sdc.cs.icbc:9996' \
  -H 'Access-Control-Request-Method: POST'

if grep -R -E 'http://mimo\.sdc\.cs\.icbc:9996|http://122\.233\.30\.2:9996' \
    /data/testagent/frontend/assets; then
  echo '前端仍固化了 API 基址，需要用空 VITE_TEST_AGENT_API_BASE_URL 重新打包' >&2
else
  echo '前端使用同源 API，可兼容域名和 IP 入口'
fi
```

预期域名预检响应包含 `Access-Control-Allow-Origin: http://mimo.sdc.cs.icbc:9996`；将 `Origin` 改为 `http://122.233.30.2:9996` 也必须返回对应值。静态资源中不应固化任一入口 API 基址。

## 10. 故障检查

| 现象 | 检查 |
|---|---|
| 浏览器 `ERR_NAME_NOT_RESOLVED` | 在实际浏览器终端执行 `nslookup mimo.sdc.cs.icbc`；名称解析失败需由企业 DNS/入口管理方处理。DNS 不负责端口映射，不能靠把实体 Nginx 改成 `9996` 修复。 |
| 登录请求跨域并报 CORS | 前端包不应固化 API 地址；用空 `VITE_TEST_AGENT_API_BASE_URL` 重新打包并部署，同时确认 `backend.env` 的 CORS 包含域名和 IP 两个 `:9996` origin 后重启 Java。不要用 `no-cors` 隐藏错误。 |
| 前端部署提示 Nginx 未 include 新网关文件 | 当前主配置只显式加载 `/data/apps/nginx/conf/test-agent.conf`；把 `TEST_AGENT_NGINX_CONF_PATH` 指向该专用文件，实体端口保持 `80`。不要把同目录新建文件当作已加载，也不要只看 `nginx -t`，必须用 `nginx -T` 核对文件清单。 |
| 前端 502/进不去 | `.2` 用 `/data/apps/nginx/sbin/nginx -p /data/apps/nginx/ -c /data/apps/nginx/conf/nginx.conf -t`，再从 `.2` curl `.114:8080/actuator/health`。禁止使用 PATH 中可能读取 `/root/conf/nginx.conf` 的另一个 Nginx。 |
| 部署提示 systemd unit 不匹配 | 执行 `systemctl show test-agent-backend -p ExecStart -p EnvironmentFiles`；必须分别指向 `/data/testagent/dist/backend/test-agent-app.jar` 和 `/data/testagent/config/backend.env`，不要让脚本覆盖未知 unit。 |
| 部署提示 8080 被其他进程占用 | 执行 `lsof -nP -iTCP:8080 -sTCP:LISTEN` 和 `tr '\0' ' ' </proc/<PID>/cmdline`；同一交付 JAR 的遗留进程会被部署脚本安全清理，其他进程需人工确认归属。 |
| `systemctl status` 启动时间或 JAR 时间仍旧 | 对比 `ExecMainStartTimestamp`、`MainPID`、`8080` 监听 PID 和 ZIP/已安装 JAR SHA；任一不一致都表示没有完成替换或旧手工 Java 仍占端口，重新走标准部署脚本，不要只反复执行 status。 |
| worker 一直断连 | 比对两份 env 的 manager token；检查 `.serverhost`、8080 和 worker 日志。 |
| Tool 外部接口在宿主机可达、容器 `connect timeout` | 按 4.1 节检查 Docker bridge 源网段的 `FORWARD` 与 `MASQUERADE`；目标变化时不要写死某个目标 IP/端口。规则存在仍超时时交由网络侧检查路由、ACL 和服务白名单。 |
| 服务器终端提示未启用或拒绝 `ws://` | 当前 HTTP 现场需 `ENABLED=true`、公开 WSS 基址为空、`ALLOW_INSECURE_WEBSOCKET=true`，修改后重启 Java；浏览器还必须能直达 `.114:8080`。 |
| 模型不显示 | 检查本服务器公共配置是否初始化；`/api/provider` 必须出现 `enterprise-qwen/enterprise-deepseek`，`/api/model` 必须出现两个准确模型 ID；更新后必须重启该用户 OpenCode。 |
| `内部模型供应商未启用或不存在` | 请求头必须为 `qwen-prod` / `deepseek-prod`，数据库同名 `provider_id` 必须启用；点击“刷新 Java 内存”后再查 refresh-status。 |
| Java 代理 400 | 读取响应正文，依次核对准确模型 ID、数据库 token、UCID、供应商 base URL；公共配置必须保留 `includeUsage=false`。当前 Java 会原样返回上游 4xx 正文，不应只剩空响应。 |
| 前端 400、Java 代理 curl 正常 | 用户 OpenCode 仍在使用旧配置或旧环境；在运行管理停止并重启该用户进程，再检查其 `/api/model`。 |
| 模型连接超时 | 必须从 Java 宿主机检查 `ai-code.sdc.enterprise:9070`；OpenCode 的 `baseURL` 应是同节点 Java `:8080`，不能直接写 9070，也不能用其他容器的 curl 代替 Java 宿主机检查。 |
| Java 调用卡住或原生输出为空 | 确认部署的是包含 SSE 修复的新 JAR；日志中不应出现重复 `data:data:`，首事件最长 30 秒、相邻事件空闲最长 120 秒。直接绕过 Java 能通不能证明 Java SSE 代理正常。 |
| 用户初始化失败 | 在运行管理检查 Java、manager、容器连接和端口池；查看用户端口日志。 |
| 公共区显示 `initialized=true/status=CONFLICT` | 这表示目录已经初始化但 Git 有未提交内容，不是磁盘文件缺失；先执行 `git -C /data/testagent/data/agent-opencode/.config status --short`，新版状态 message 会直接列出最多五个待提交路径。 |
| 公共区 fetch/push 报 `Permission denied (publickey)` | 先确认当前登录管理员配置的唯一 SSH key 与其统一认证号在远端匹配。新版会在 fetch 前把共享仓库 origin 刷新为当前管理员；升级前可用 `git -C /data/testagent/data/agent-opencode/.config remote -v` 检查是否仍是上一位管理员用户名，不要改用服务器默认 SSH key。 |

回滚时恢复旧 JAR、`backend/lib/`、programs 和 worker 镜像，再按“Java → 身份文件 → worker”重启。不要删除 `/data/testagent/data`。
