# 企业内单后台部署

本文用于一台 Java 后台同时承载本机 `opencode-worker` 的正式部署。以下以当前现场为例：

| 角色 | 地址 |
|---|---|
| 前端实体 Nginx | `122.233.30.2` |
| Java 后台 + worker | `122.233.30.114` |
| Redis | `122.233.30.20:6379` |
| PostgreSQL | `122.42.203.103:8000/testagent` |
| 行内模型 | `ai-code.sdc.icbc:9070` |

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
       -> ai-code.sdc.icbc:9070
```

网络要求：

- `.2` 能访问 `.114:8080`。
- worker 容器能访问 `.114:8080`。
- `.114` 能访问 PostgreSQL、Redis 和 `ai-code.sdc.icbc:9070`。
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

在 `.114` 创建 `/data/testagent/config/backend.env`。密码和 key 用现场真实值替换：

```dotenv
SERVER_PORT=8080
TEST_AGENT_DEPLOYMENT_MODE=internal
TEST_AGENT_SERVER_ADVERTISED_HOST=122.233.30.114
TEST_AGENT_LINUX_SERVER_ID=test-agent-backend-122-233-30-114
SYS_DATA_ROOT_DIR=/data/testagent/data

TEST_AGENT_DB_URL=jdbc:postgresql://122.42.203.103:8000/testagent
TEST_AGENT_DB_USERNAME=testagent
TEST_AGENT_DB_PASSWORD=<数据库密码>
TEST_AGENT_DB_DRIVER_CLASS_NAME=org.postgresql.Driver

TEST_AGENT_REDIS_HOST=122.233.30.20
TEST_AGENT_REDIS_PORT=6379
TEST_AGENT_REDIS_PASSWORD=<Redis 密码；无密码留空>

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

关键约束：

- `TEST_AGENT_SERVER_ADVERTISED_HOST` 必须是 worker 和其他服务器可访问的真实地址，不能写 `127.0.0.1`。
- `TEST_AGENT_LINUX_SERVER_ID` 是服务器长期稳定身份，升级时不得改变。
- 企业模型供应商地址和上游 token 在“内部模型供应商”页面维护，不写入 `backend.env` 或 `docker.env`。

## 4. 配置 worker

在 `.114` 创建 `/data/testagent/config/docker.env`：

```dotenv
TEST_AGENT_BASE_DIR=/data/testagent
TEST_AGENT_NGINX_LISTEN_PORT=80
TEST_AGENT_FRONTEND_ROOT=/data/testagent/frontend
TEST_AGENT_BACKEND=122.233.30.114:8080

TEST_AGENT_OPENCODE_MANAGER_TOKEN=<与 backend.env 完全一致>
TEST_AGENT_DATA_ROOT=/data/testagent/data
TEST_AGENT_PROGRAM_ROOT=/data/testagent/programs
TEST_AGENT_OPENCODE_WORKER_IMAGE=test-agent-opencode-worker:internal

VITE_TEST_AGENT_API_BASE_URL=http://122.233.30.2

OPENCODE_WORKER_BACKEND_PORT=8080
OPENCODE_WORKER_PORT_START=4096
OPENCODE_WORKER_PORT_END=4105
```

其余镜像版本和构建镜像摘要直接保留 [env.example](env.example) 的值。`TEST_AGENT_DATA_ROOT` 必须与 Java 的 `SYS_DATA_ROOT_DIR` 完全一致；每个稳定服务器身份只运行一个 worker。

## 5. 配置前端 Nginx

单后台可直接使用 [nginx/gateway.conf.template](nginx/gateway.conf.template)，渲染变量为：

```dotenv
TEST_AGENT_NGINX_LISTEN_PORT=80
TEST_AGENT_FRONTEND_ROOT=/data/testagent/frontend
TEST_AGENT_BACKEND=122.233.30.114:8080
```

确认 `/api`、SSE 和 WebSocket 都转发到 `.114:8080`，并保留 `proxy_buffering off`、`proxy_read_timeout 3600s` 和 Upgrade 头。

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

```text
icbc-qwen/Qwen3.6-27B
icbc-deepseek/DeepSeek-V4-Flash-W8A8
```

数据库供应商路由固定为：

```text
qwen-prod
deepseek-prod
```

三层配置必须同时正确：

| 配置层 | 正确内容 | 生效方式 |
|---|---|---|
| 本服务器公共 `opencode.jsonc` | `icbc-qwen/Qwen3.6-27B`、`icbc-deepseek/DeepSeek-V4-Flash-W8A8`，并包含 `includeUsage=false` | 重启已有用户 OpenCode 进程；新进程直接读取 |
| 共享数据库内部供应商 | `qwen-prod`、`deepseek-prod` 均启用，`baseUrl=http://ai-code.sdc.icbc:9070/icbc/jdt/model/api/openai/v1`，全局 token 已配置 | 保存或点击“刷新 Java 内存”；不需要重启用户进程 |
| 用户进程环境 | Java 启动进程时注入内部代理 key、同节点 Java 代理地址和该用户的 `ICBC_UCID` | 停止并通过运行管理重新启动用户进程 |

`icbc-qwen` / `icbc-deepseek` 只用于 OpenCode 模型目录；`qwen-prod` / `deepseek-prod` 只用于 Java 路由，二者不能互换。上游模型 token 由 Java 作为 `Authorization: Bearer <token>` 注入，不在 `backend.env`、`docker.env` 或 `opencode.jsonc` 中再配 `Auth-Token`。每个用户的 UCID 来自用户表，由 Java 逐进程注入，不需要也不能为所有用户共用一个 env 文件。

更新公共配置后，要在运行管理中重启已有用户 OpenCode 进程；只重启 Java 不会让已运行的 OpenCode 重新读取公共配置。供应商地址、启用状态或 token 变化后先点击“刷新 Java 内存”；单后台广播异常时可直接重启 Java 重新加载数据库快照。

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

nc -vz ai-code.sdc.icbc 9070
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
  -H 'X-ICBC-Model-Provider: qwen-prod' \
  -H 'ucid: <现场用户 UCID>' \
  --data '{"model":"Qwen3.6-27B","messages":[{"role":"user","content":"你好"}],"stream":true}'

curl -iN --max-time 180 \
  http://127.0.0.1:8080/api/internal/platform/opencode-runtime/internal-model-proxy/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -H 'Authorization: Bearer <backend.env 中的 TEST_AGENT_INTERNAL_PROXY_API_KEY>' \
  -H 'X-ICBC-Model-Provider: deepseek-prod' \
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
| worker 一直断连 | 比对两份 env 的 manager token；检查 `.serverhost`、8080 和 worker 日志。 |
| 模型不显示 | 检查本服务器公共配置是否初始化；`/api/provider` 必须出现 `icbc-qwen/icbc-deepseek`，`/api/model` 必须出现两个准确模型 ID；更新后必须重启该用户 OpenCode。 |
| `内部模型供应商未启用或不存在` | 请求头必须为 `qwen-prod` / `deepseek-prod`，数据库同名 `provider_id` 必须启用；点击“刷新 Java 内存”后再查 refresh-status。 |
| Java 代理 400 | 读取响应正文，依次核对准确模型 ID、数据库 token、UCID、供应商 base URL；公共配置必须保留 `includeUsage=false`。当前 Java 会原样返回上游 4xx 正文，不应只剩空响应。 |
| 前端 400、Java 代理 curl 正常 | 用户 OpenCode 仍在使用旧配置或旧环境；在运行管理停止并重启该用户进程，再检查其 `/api/model`。 |
| 模型连接超时 | 必须从 Java 宿主机检查 `ai-code.sdc.icbc:9070`；OpenCode 的 `baseURL` 应是同节点 Java `:8080`，不能直接写 9070，也不能用其他容器的 curl 代替 Java 宿主机检查。 |
| Java 调用卡住或原生输出为空 | 确认部署的是包含 SSE 修复的新 JAR；日志中不应出现重复 `data:data:`，首事件最长 30 秒、相邻事件空闲最长 120 秒。直接绕过 Java 能通不能证明 Java SSE 代理正常。 |
| 用户初始化失败 | 在运行管理检查 Java、manager、容器连接和端口池；查看用户端口日志。 |

回滚时恢复旧 JAR、`backend/lib/`、programs 和 worker 镜像，再按“Java → 身份文件 → worker”重启。不要删除 `/data/testagent/data`。
