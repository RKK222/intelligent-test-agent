# 企业内单后台部署

本文用于一台 Java 后台同时承载本机 `opencode-worker` 的正式部署。以下以当前现场为例：

| 角色 | 地址 |
|---|---|
| 前端实体 Nginx | `122.233.30.2` |
| Java 后台 + worker | `122.233.30.114` |
| Redis | `122.233.30.20:6379` |
| PostgreSQL | `122.233.30.147:5432/postgres` |
| 企业内部模型 | `ai-code.sdc.enterprise:9070` |

## 1. 拓扑与端口

```text
浏览器
  -> 122.233.30.2:80 Nginx
  -> 122.233.30.114:8080 Java
       -> PostgreSQL / Redis

122.233.30.114 opencode-worker
  <-> 122.233.30.114:8080 Java manager WebSocket
  -> 4096-4105 用户 OpenCode 进程
  -> 122.233.30.114:8080 Java 内部模型代理
       -> ai-code.sdc.enterprise:9070
```

网络要求：

- `.2` 能访问 `.114:8080`。
- worker 容器能访问 `.114:8080`。
- `.114` 能访问 PostgreSQL、Redis 和 `ai-code.sdc.enterprise:9070`。
- `4096-4105` 的宿主机端口与容器端口必须同号映射。
- `9070` 只需要 Java 宿主机出站可达，不对外发布。
- 不启用 `--network host`，不部署 `19070` relay，不修改 worker 网络模式。

## 2. Mac 打包与分发

在 Mac 仓库根目录执行：

```bash
cd /Users/kaka/Desktop/intelligent-test-agent
deploy/internal/package-release.sh --output-dir deploy/internal/dist
```

必须交付：

```text
deploy/internal/dist/test-agent-internal-release.zip
deploy/internal/dist/test-agent-internal-release.zip.sha256
```

zip 内包含 Java JAR、`backend/lib/`、前端静态包、programs、worker 镜像、部署脚本和配置模板。将 zip 和校验文件分别复制到：

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

在 `.114` 创建 `/data/testagent/config/backend.env`。下面是可整文件替换的完整生产配置；IP、端口、目录、模型模式和超时已按当前现场填写，只需要替换 3 个 `REPLACE_...` 值。模板按 Redis 无密码、平台 API token 为空填写；如果现网这两项非空，必须保留现网值。替换前先备份：

```bash
install -d -m 0755 /data/testagent/config
cp -a /data/testagent/config/backend.env \
  /data/testagent/config/backend.env.bak.$(date +%Y%m%d%H%M%S) 2>/dev/null || true
```

首次升级到持久 SSH 加密密钥时，在 `.114` 生成一次并永久备份；已有文件绝不能覆盖：

```bash
umask 077
if [ ! -s /data/testagent/config/ssh-rsa-private.key ]; then
  openssl genpkey -algorithm RSA \
    -pkeyopt rsa_keygen_bits:3072 \
    -out /data/testagent/config/ssh-rsa-private.key
fi
chmod 0600 /data/testagent/config/ssh-rsa-private.key
openssl pkey -in /data/testagent/config/ssh-rsa-private.key -check -noout
```

旧版本曾使用启动时临时 RSA key；部署持久文件并重启后，旧密文无法迁移，现有用户需要在“个人设置 → SSH key”删除并重新添加一次。此后升级必须一直保留同一私钥文件。

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
TEST_AGENT_SSH_RSA_PRIVATE_KEY_PATH=/data/testagent/config/ssh-rsa-private.key

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

关键约束：

- `TEST_AGENT_SERVER_ADVERTISED_HOST` 必须是 worker 和其他服务器可访问的真实地址，不能写 `127.0.0.1`。
- `TEST_AGENT_LINUX_SERVER_ID` 是服务器长期稳定身份，升级时不得改变。
- `TEST_AGENT_SSH_RSA_PRIVATE_KEY_PATH` 指向权限 0600 的持久文件，升级 JAR 时不得删除、覆盖或重新生成。
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

`TEST_AGENT_DATA_ROOT` 必须与 Java 的 `SYS_DATA_ROOT_DIR` 完全一致；每个稳定服务器身份只运行一个 worker。当前 worker 不读取旧的 `TEST_AGENT_BACKEND`，而是读取 Java 写出的 `.serverhost` 再结合 `OPENCODE_WORKER_BACKEND_PORT` 连接本机 Java，因此不要恢复旧变量。

## 5. 配置前端 Nginx

在前端 `.2` 创建 `/data/testagent/config/nginx.env`。以下内容可以整文件替换：

```dotenv
TEST_AGENT_NGINX_MODE=single
TEST_AGENT_NGINX_BACKENDS=122.233.30.114:8080
TEST_AGENT_NGINX_LISTEN_PORT=80
TEST_AGENT_FRONTEND_ROOT=/data/testagent/frontend
TEST_AGENT_NGINX_CONF_PATH=/etc/nginx/conf.d/test-agent-gateway.conf
```

`TEST_AGENT_NGINX_CONF_PATH` 必须是当前 Nginx 主配置实际 include 的 `.conf` 文件；如果现场配置目录不同，改成现场路径。前端部署脚本会调用 [configure-nginx.sh](configure-nginx.sh)，自动渲染 [nginx/gateway.conf.template](nginx/gateway.conf.template)、备份旧配置、执行 `nginx -t`、确认该文件已被 include，并 reload；失败会恢复旧配置。

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
  --archive /data/0709/test-agent-internal-release.zip
```

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

## 8. 验收

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

预期 worker 日志出现 `manager config update applied`。再在管理页面确认一个 Java、一个 manager、一个容器均在线；初始化一个用户 OpenCode 进程后，用其实际动态端口检查：

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

## 9. 故障检查

| 现象 | 检查 |
|---|---|
| 前端 502/进不去 | `.2` 执行 `nginx -t`，再从 `.2` curl `.114:8080/actuator/health`。 |
| 部署提示 systemd unit 不匹配 | 执行 `systemctl show test-agent-backend -p ExecStart -p EnvironmentFiles`；必须分别指向 `/data/testagent/dist/backend/test-agent-app.jar` 和 `/data/testagent/config/backend.env`，不要让脚本覆盖未知 unit。 |
| 部署提示 8080 被其他进程占用 | 执行 `lsof -nP -iTCP:8080 -sTCP:LISTEN` 和 `tr '\0' ' ' </proc/<PID>/cmdline`；同一交付 JAR 的遗留进程会被部署脚本安全清理，其他进程需人工确认归属。 |
| worker 一直断连 | 比对两份 env 的 manager token；检查 `.serverhost`、8080 和 worker 日志。 |
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
