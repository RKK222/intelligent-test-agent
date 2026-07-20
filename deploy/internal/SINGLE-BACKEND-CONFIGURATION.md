# 单后台配置脚本执行单

本文只解决当前单后台现场的三个环境文件生成与生效问题；平台 RSA 私钥由交付 JAR 内置，不再部署外置文件：

| 服务器 | 配置文件 | 生成方式 |
|---|---|---|
| `122.233.30.114` | `/data/testagent/config/backend.env` | `configure-single-deployment.sh backend` |
| `122.233.30.114` | `/data/testagent/config/docker.env` | 同上，一次同时生成 |
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

```bash
bash /tmp/test-agent-release-config/deploy/internal/configure-single-deployment.sh \
  backend
```

脚本从现有配置中保留以下值，且不会回显：

- 当前 PostgreSQL 密码。
- Redis 密码和平台 API token（包括空值）。
- Java/worker 共用的 manager token。
- Java 内部模型代理 key。
- XXL MySQL 密码和 Admin/executor 共用 access token。

脚本会清除旧 `TEST_AGENT_SSH_RSA_PRIVATE_KEY_PATH`，Java 固定读取当前交付 JAR 内置 `rsa-private.key`。现存外置文件可作为受控回滚备份保留，但不会被读取。

其余值按当前现场固定为：

```text
Java                  122.233.30.114:8080
PostgreSQL            122.233.30.147:5432/postgres
PostgreSQL 用户       postgres
Redis                 122.233.30.20:6379
XXL MySQL             122.233.30.148:3306/xxl_job
XXL Admin/executor    122.233.30.114:18080 / :9999
前端 Origin           http://mimo.sdc.cs.icbc:9996
数据目录              /data/testagent/data
OpenCode 端口池       4096-4115（20 个端口）
manager command 超时  10s（只保留一项）
```

通用模板保持 HTTPS/WSS 安全默认，而当前现场已经明确选择 HTTP。`backend` 角色会直接生成当前现场的最终值：Java 和 OpenCode CORS 同时允许域名与 IP 的 `:9996` origin，前端 API 基址留空以使用同源路径，终端启用明确的 HTTP/`ws://` 例外；不再需要生成后手工覆盖。

HTTP/`ws://` 会明文传输登录信息和终端内容，只能在已接受风险的可信内网使用。服务器终端签票后由浏览器直连 `122.233.30.114:8080`；浏览器网段不通该端口时，修改 Nginx 或 worker 无法修复。

以下任一情况会在写文件前失败：

- 当前 `backend.env` 没有数据库密码。
- 当前 `backend.env` 没有内部模型代理 key。
- 当前 `backend.env` 没有 XXL MySQL 密码或 XXL access token。
- 两份 env 都没有 manager token。
- 两份 env 的 manager token 不一致。

生成后执行：

```bash
grep -E '^(TEST_AGENT_DB_URL|TEST_AGENT_DB_USERNAME|TEST_AGENT_SERVER_ADVERTISED_HOST|SYS_DATA_ROOT_DIR|TEST_AGENT_OPENCODE_MANAGER_COMMAND_TIMEOUT)=' \
  /data/testagent/config/backend.env
grep -E '^TEST_AGENT_XXL_JOB_(MYSQL_URL|MYSQL_USERNAME|ADMIN_PORT|ADMIN_ADDRESSES|EXECUTOR_PORT|EXECUTOR_ADDRESS)=' \
  /data/testagent/config/backend.env
grep -E '^TEST_AGENT_SERVER_TERMINAL_(ENABLED|WORKING_DIRECTORY|PUBLIC_WEBSOCKET_BASE_URL|ALLOW_INSECURE_WEBSOCKET)=' \
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

服务器终端配置预期为 `ENABLED=true`、工作目录 `/data/testagent`、公开 WSS 基址为空、`ALLOW_INSECURE_WEBSOCKET=true`。这是当前 HTTP 现场例外；通用企业模板仍以 WSS 为安全默认。

## 3. `.2` 生成 nginx.env

当前 Nginx 安装目录是 `/data/apps/nginx`，`nginx -T` 已确认主配置只显式加载 `/data/apps/nginx/conf/test-agent.conf`，没有 `*.conf` 通配 include。先确认该文件只属于本应用并备份：

```bash
sed -n '1,260p' /data/apps/nginx/conf/test-agent.conf
cp -a /data/apps/nginx/conf/test-agent.conf \
  /data/apps/nginx/conf/test-agent.conf.before-deploy.$(date +%Y%m%d%H%M%S)
```

若文件中还有其他系统的配置，停止覆盖并由 Nginx 管理方拆出专用 include；若是本应用专用文件，执行：

```bash
bash /tmp/test-agent-release-config/deploy/internal/configure-single-deployment.sh \
  frontend \
  --nginx-home /data/apps/nginx \
  --gateway-conf /data/apps/nginx/conf/test-agent.conf

sed -n '1,40p' /data/testagent/config/nginx.env
```

脚本会在候选目录短暂写入一个仅含注释的探测 `.conf`，并通过实体 Nginx 的 `-T` 确认新文件确实会被加载，再选择网关目录。仅显式 include 单个现有文件时，不再错误地把其同级目录当成 `*.conf` 通配目录。生成的配置至少包含：

```dotenv
TEST_AGENT_NGINX_MODE=single
TEST_AGENT_NGINX_BACKENDS=122.233.30.114:8080
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

当前实体 Nginx 同时监听 `80` 和 `9996`：企业域名入口继续按现有网络链路转发到 `.2:80`，浏览器也可以直接访问 `http://122.233.30.2:9996`。两种入口都由同一个 `server` 块提供服务，并转发到单后台 `.114:8080`。

只有确认 `/data/apps/nginx/conf/test-agent.conf` 还承载其他系统、不能由应用部署脚本接管时，才由 Nginx 管理方在 `http {}` 内增加专用通配 include，例如：

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
  --archive /data/0709/test-agent-internal-release.zip \
  --nginx-env /data/testagent/config/nginx.env \
  --frontend-health-url http://127.0.0.1/health \
  --frontend-url http://127.0.0.1/
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

预期部署输出包含 `Installed single gateway ... /data/apps/nginx/conf/test-agent.conf` 和 `Frontend deployment finished`。脚本自动创建的 `frontend.bak.<时间>`、`deploy/internal.bak.<时间>`、`test-agent.conf.bak.<时间>` 是回滚备份，不是公共 Agent 配置来源。

禁止直接使用当前 PATH 中会读取 `/root/conf/nginx.conf` 的另一个 `nginx`。代码仍保留 PATH nginx + systemd 模式，只作为其他标准系统安装环境没有设置自定义 Nginx 参数时的兼容兜底。
