# 单后台配置脚本执行单

本文只解决当前单后台现场的三个环境文件和一份持久 RSA 私钥的生成、生效与保留问题：

| 服务器 | 配置文件 | 生成方式 |
|---|---|---|
| `122.233.30.114` | `/data/testagent/config/backend.env` | `configure-single-deployment.sh backend` |
| `122.233.30.114` | `/data/testagent/config/docker.env` | 同上，一次同时生成 |
| `122.233.30.114` | `/data/testagent/config/ssh-rsa-private.key` | 首次部署时用 OpenSSL 生成，后续升级永久保留 |
| `122.233.30.2` | `/data/testagent/config/nginx.env` | `configure-single-deployment.sh frontend` |

完整部署、模型配置和验收仍见 [SINGLE-BACKEND.md](SINGLE-BACKEND.md)。

## 1. 两台服务器先解出配置脚本

`.2` 和 `.114` 都执行：

```bash
set -euo pipefail
cd /data/0709
sha256sum -c test-agent-internal-release.zip.sha256

rm -rf /tmp/test-agent-release-config
mkdir -p /tmp/test-agent-release-config
unzip -q /data/0709/test-agent-internal-release.zip \
  'deploy/internal/*' \
  -d /tmp/test-agent-release-config
```

不要进入交互式 `sh` 后分段粘贴检查命令。带 `set -e` 的子 shell 一旦因 `grep` 失败退出，父 shell 中变量会变为空，后续命令可能误打印“检查通过”。本文件中的命令必须整段由 `bash` 执行。

## 2. `.114` 生成 backend.env 和 docker.env

先确保持久 RSA 私钥存在；已有文件绝不能覆盖：

```bash
umask 077
if [ ! -s /data/testagent/config/ssh-rsa-private.key ]; then
  openssl genpkey -algorithm RSA \
    -pkeyopt rsa_keygen_bits:3072 \
    -out /data/testagent/config/ssh-rsa-private.key
fi
chmod 0600 /data/testagent/config/ssh-rsa-private.key
```

```bash
bash /tmp/test-agent-release-config/deploy/internal/configure-single-deployment.sh \
  backend
```

脚本从现有配置中保留以下值，且不会回显：

- 当前 PostgreSQL 密码。
- Redis 密码和平台 API token（包括空值）。
- Java/worker 共用的 manager token。
- Java 内部模型代理 key。

脚本会把 `TEST_AGENT_SSH_RSA_PRIVATE_KEY_PATH=/data/testagent/config/ssh-rsa-private.key` 写入 `backend.env`，但不会创建、覆盖或回显该私钥。

其余值按当前现场固定为：

```text
Java                  122.233.30.114:8080
PostgreSQL            122.233.30.147:5432/postgres
PostgreSQL 用户       postgres
Redis                 122.233.30.20:6379
前端 Origin           http://122.233.30.2
数据目录              /data/testagent/data
OpenCode 端口池       4096-4105
manager command 超时  10s（只保留一项）
```

以下任一情况会在写文件前失败：

- 当前 `backend.env` 没有数据库密码。
- 当前 `backend.env` 没有内部模型代理 key。
- 两份 env 都没有 manager token。
- 两份 env 的 manager token 不一致。

生成后执行：

```bash
grep -E '^(TEST_AGENT_DB_URL|TEST_AGENT_DB_USERNAME|TEST_AGENT_SERVER_ADVERTISED_HOST|SYS_DATA_ROOT_DIR|TEST_AGENT_OPENCODE_MANAGER_COMMAND_TIMEOUT)=' \
  /data/testagent/config/backend.env
grep -E '^TEST_AGENT_SERVER_TERMINAL_(ENABLED|WORKING_DIRECTORY|PUBLIC_WEBSOCKET_BASE_URL)=' \
  /data/testagent/config/backend.env
grep -E '^(TEST_AGENT_DATA_ROOT|OPENCODE_WORKER_BACKEND_PORT|OPENCODE_WORKER_PORT_START|OPENCODE_WORKER_PORT_END)=' \
  /data/testagent/config/docker.env

systemctl restart test-agent-backend
curl -fsS http://127.0.0.1:8080/actuator/health/readiness
cat /data/testagent/data/.serverid
cat /data/testagent/data/.serverhost

cd /data/testagent/deploy/internal
./opencode-worker-docker.sh \
  --env-file /data/testagent/config/docker.env \
  restart
```

身份文件预期为：

```text
test-agent-backend-122-233-30-114
122.233.30.114
```

服务器终端配置预期为 `ENABLED=true`、工作目录 `/data/testagent`、公开地址 `wss://122.233.30.2`；前端 Nginx 必须同时启用 TLS 和对应 `linuxServerId` 的精确 WebSocket 路由。

## 3. `.2` 生成 nginx.env

当前 Nginx 安装目录是 `/data/apps/nginx`。执行：

```bash
bash /tmp/test-agent-release-config/deploy/internal/configure-single-deployment.sh \
  frontend \
  --nginx-home /data/apps/nginx

sed -n '1,40p' /data/testagent/config/nginx.env
```

脚本会在候选目录短暂写入一个仅含注释的探测 `.conf`，并通过实体 Nginx 的 `-T` 确认新文件确实会被加载，再选择网关目录。仅显式 include 单个现有文件时，不再错误地把其同级目录当成 `*.conf` 通配目录。生成的配置至少包含：

```dotenv
TEST_AGENT_NGINX_MODE=single
TEST_AGENT_NGINX_BACKENDS=122.233.30.114:8080
TEST_AGENT_NGINX_LISTEN_PORT=80
TEST_AGENT_FRONTEND_ROOT=/data/testagent/frontend
TEST_AGENT_NGINX_BIN=/data/apps/nginx/sbin/nginx
TEST_AGENT_NGINX_PREFIX=/data/apps/nginx
TEST_AGENT_NGINX_MAIN_CONF=/data/apps/nginx/conf/nginx.conf
TEST_AGENT_NGINX_RELOAD_MODE=binary
```

`TEST_AGENT_NGINX_CONF_PATH` 以探测结果为准。若主配置没有可自动识别的 `*.conf` include，应先在 `http {}` 内增加一个专用通配 include，例如：

```nginx
include /data/apps/nginx/conf/test-agent-enabled/*.conf;
```

创建目录后，再把网关放入这个已确认的通配目录：

```bash
mkdir -p /data/apps/nginx/conf/test-agent-enabled
bash /tmp/test-agent-release-config/deploy/internal/configure-single-deployment.sh \
  frontend \
  --nginx-home /data/apps/nginx \
  --gateway-conf /data/apps/nginx/conf/test-agent-enabled/test-agent-gateway.conf
```

如果现有主配置已经包含 `/data/apps/nginx/conf/vhosts/*.conf` 或 `conf.d/*.conf`，无需修改主配置，直接把 `--gateway-conf` 指到对应目录。禁止只因为某个同级文件被显式 include，就把新网关放在其旁边；`nginx -t` 虽会成功，但 `nginx -T` 不会列出新文件，部署脚本会拒绝 reload。

然后部署前端；部署脚本会使用 `nginx.env` 中的真实二进制、prefix 和主配置执行 `-t/-T/-s reload`：

```bash
unzip -p /data/0709/test-agent-internal-release.zip \
  deploy/internal/deploy-internal-frontend.sh \
  >/tmp/deploy-internal-frontend.sh

bash /tmp/deploy-internal-frontend.sh \
  --archive /data/0709/test-agent-internal-release.zip
```

单独检查 Nginx 时执行：

```bash
/data/apps/nginx/sbin/nginx \
  -p /data/apps/nginx/ \
  -c /data/apps/nginx/conf/nginx.conf \
  -t

curl -fsS http://122.233.30.114:8080/actuator/health
curl -fsS http://127.0.0.1/health
curl -fsS http://127.0.0.1/
```

禁止直接使用当前 PATH 中会读取 `/root/conf/nginx.conf` 的另一个 `nginx`。代码仍保留 PATH nginx + systemd 模式，只作为其他标准系统安装环境没有设置自定义 Nginx 参数时的兼容兜底。
