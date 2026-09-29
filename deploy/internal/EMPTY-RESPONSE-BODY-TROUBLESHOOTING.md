# 企业部署空报文体排查手册

本手册用于企业内单后台或 `.4 + .114` 双后台部署后，前端“原始输出”出现“（空报文体）”、对话没有可见回答、HTTP 响应正文为空，或 RunEvent SSE 没有有效 `data` 的场景。排查必须先保全证据，再判断浏览器、Nginx、入口 Java、Run 生产 Java、用户 OpenCode 或企业模型上游中的责任层；不要先重启、反复重发 Run 或重新打包。

## 1. 先判断是不是故障

“（空报文体）”只是前端对空字符串的显示占位，不代表所有记录都是同一种故障。先展开该条记录并按下表分类：

| 原始输出记录 | 是否可接受 | 判定 |
|---|---|---|
| `请求 GET ...`，报文体为空 | 通常正常 | GET 本来可以没有请求体。 |
| `请求 POST .../cancel`，报文体为空 | 正常 | Cancel 请求不需要业务请求体。 |
| 创建 Session、创建 Run、回复 question/permission 的请求体为空 | 异常 | 这些入口需要 JSON 请求体。 |
| 对话相关 HTTP `响应`正文为空 | 异常 | 当前平台 JSON API 使用统一响应；即使 active Run 不存在，也应返回 `success=true,data=null`，不应是零字节。 |
| SSE 记录存在，但报文体为空 | 异常 | 前端解析器会忽略纯注释心跳；能进入原始输出但为空，表示收到过空 `data:`。 |
| SSE 有 `run.succeeded`，但没有 assistant 文本或 message part | 异常，但不是 HTTP 空响应 | Run 已到终态，重点查用户 OpenCode、模型流和消息投影。 |
| 用户 OpenCode 出现 `prompt_async failed` 和 `errors building .../opencode/tools/*.ts` | 异常，且不是模型空响应 | 自定义工具在主模型调用前构建失败；先根据失败路径和底层异常区分公共配置工具、应用工具或离线依赖解析，不能用重发 Run 掩盖。 |
| `SSE connection error` | 连接诊断，不是上游响应正文 | 继续查 Network、Nginx、SSE 路由和最终 `run.*` 事件。 |

只看到一条正常的空 GET 请求时可以停止；其余情况继续执行本手册。

## 2. 请求链路

正式对话链路为：

```text
浏览器
  -> 122.233.30.2 实体 Nginx :80/:9996
  -> 入口 Java 122.233.30.4:8080 或 122.233.30.114:8080
  -> 按用户 binding / Run producer 转发到权威 Java
  -> 本机 opencode-worker / manager
  -> 用户 OpenCode 动态端口 14096-15095
  -> 同节点 Java 内部模型代理 :8080
  -> 企业模型服务 :9070
```

创建 Run 的 HTTP 响应和后续 RunEvent SSE 是两条连接。创建 Run 返回完整 JSON，只能证明 Run 已创建；页面最终回答还依赖 SSE、用户 OpenCode 和模型流。

## 3. 浏览器立即保全证据

在出现问题的浏览器执行，完成前不要刷新页面：

1. 打开对话右上角“原始输出”，点击下载。该缓存只保留当前页面、当前 Session 最近 2000 条，刷新页面后清空；下载内容按时间倒序。
2. 记录异常条目的类型、完整标题、发生时间、HTTP 状态、`Content-Type`、`traceId`、`runId` 和 `sessionId`。不要只截图“（空报文体）”。
3. 打开开发者工具 Network，启用 `Preserve log`，只复现一次。依次筛选 `runs`、`events`、`messages`、`active-run`，记录请求 URL、状态、响应头 `X-Trace-Id`、`Content-Type`、响应字节数和 Timing。
4. 在设置菜单左下角记录前端构建版本；由超级管理员在运行管理记录两台 Java 和两个 manager 的 `buildVersion`，以及故障用户的 `linuxServerId`、`containerId`、动态端口、`baseUrl`、`BOUND/UNBOUND`、`managerStatus` 和 `healthStatus`。

现场记录使用以下字段：

```text
发生时间（含时区）：
浏览器入口（域名或 IP:端口）：
异常记录类型和标题：
HTTP 状态 / Content-Type / 响应字节数：
traceId：
runId：
sessionId：
前端 buildVersion：
用户绑定 linuxServerId / containerId / 动态端口：
Java buildVersion / manager buildVersion：
影响范围：单用户 / 单节点 / 双节点 / 所有用户：
```

原始输出已经对常见 token 字段脱敏，但仍可能包含用户问题、路径和业务数据，按敏感材料保管。HAR、手工 `curl` 输出和 shell 历史可能包含 Authorization、Cookie、上游 token 和一次性 ticket，不要直接外发；只提供脱敏后的上述字段和必要响应片段。若诊断材料已经暴露完整 Bearer/token，立即按企业密钥流程吊销或轮换，并按企业审计要求清理 shell 历史和诊断副本；不要继续复用该凭据，也不要让接收方回传它。

## 4. 先证明实际部署的是同一批包

包已通过 U 盘进入企业内部中转机时，Mac 打包阶段已经结束。先从中转机和目标机校验现有包，不要求重新在 Mac 构建。只有发现包不是预期批次，才返回 Mac 重新打包。

### 4.1 企业内部中转机

在企业内部中转机执行：

```bash
cd ~/Desktop/mimoagent/0709
sha256sum -c test-agent-two-backend-complete.zip.sha256
unzip -tq test-agent-two-backend-complete.zip
```

两条命令都必须成功。失败时停止，不向 `.4/.114/.2` 继续分发。

### 4.2 122.233.30.4 后台

```bash
cd /data/0709
sha256sum -c test-agent-two-backend-complete.zip.sha256
unzip -tq test-agent-two-backend-complete.zip

cd /data/0709/test-agent-two-backend-complete
sha256sum -c test-agent-internal-release.zip.sha256

unzip -p test-agent-internal-release.zip \
  dist/backend/test-agent-app.jar | sha256sum
sha256sum /data/testagent/dist/backend/test-agent-app.jar

unzip -p test-agent-internal-release.zip \
  'dist/backend/lib/test-agent-api-*.jar' | sha256sum
sha256sum /data/testagent/dist/backend/lib/test-agent-api-*.jar

unzip -p test-agent-internal-release.zip \
  'dist/backend/lib/test-agent-opencode-runtime-*.jar' | sha256sum
sha256sum /data/testagent/dist/backend/lib/test-agent-opencode-runtime-*.jar

unzip -p test-agent-internal-release.zip \
  'dist/backend/lib/test-agent-event-*.jar' | sha256sum
sha256sum /data/testagent/dist/backend/lib/test-agent-event-*.jar

unzip -p test-agent-internal-release.zip \
  deploy/internal/release-components.env
grep -E '^TEST_AGENT_RELEASE_(WORKER_RUNTIME|TOOLBOX)_FINGERPRINT=' \
  /data/testagent/config/release-component-state.env

systemctl show test-agent-backend \
  -p ActiveState -p MainPID -p ExecMainStartTimestamp \
  -p ExecStart -p EnvironmentFiles
ss -lntp 'sport = :8080'
```

四组 ZIP 内/已安装 SHA 必须分别一致；systemd 的 `ExecStart`、`EnvironmentFiles` 必须指向 `/data/testagent/dist/backend/test-agent-app.jar` 和 `/data/testagent/config/backend.env`，`MainPID` 必须是 `8080` 监听者。任一不一致都表示部署未完整替换，不能用 health 为 `UP` 证明升级成功。

### 4.3 122.233.30.114 后台

```bash
cd /data/0709
sha256sum -c test-agent-two-backend-complete.zip.sha256
unzip -tq test-agent-two-backend-complete.zip

cd /data/0709/test-agent-two-backend-complete
sha256sum -c test-agent-internal-release.zip.sha256

unzip -p test-agent-internal-release.zip \
  dist/backend/test-agent-app.jar | sha256sum
sha256sum /data/testagent/dist/backend/test-agent-app.jar

unzip -p test-agent-internal-release.zip \
  'dist/backend/lib/test-agent-api-*.jar' | sha256sum
sha256sum /data/testagent/dist/backend/lib/test-agent-api-*.jar

unzip -p test-agent-internal-release.zip \
  'dist/backend/lib/test-agent-opencode-runtime-*.jar' | sha256sum
sha256sum /data/testagent/dist/backend/lib/test-agent-opencode-runtime-*.jar

unzip -p test-agent-internal-release.zip \
  'dist/backend/lib/test-agent-event-*.jar' | sha256sum
sha256sum /data/testagent/dist/backend/lib/test-agent-event-*.jar

unzip -p test-agent-internal-release.zip \
  deploy/internal/release-components.env
grep -E '^TEST_AGENT_RELEASE_(WORKER_RUNTIME|TOOLBOX)_FINGERPRINT=' \
  /data/testagent/config/release-component-state.env

systemctl show test-agent-backend \
  -p ActiveState -p MainPID -p ExecMainStartTimestamp \
  -p ExecStart -p EnvironmentFiles
ss -lntp 'sport = :8080'
```

成功条件与 `.4` 相同；另外 `.4` 和 `.114` 的四组已安装 SHA 必须一致。

### 4.4 122.233.30.2 前端

```bash
cd /data/0709
sha256sum -c test-agent-two-backend-complete.zip.sha256
unzip -tq test-agent-two-backend-complete.zip

cd /data/0709/test-agent-two-backend-complete
sha256sum -c test-agent-internal-release.zip.sha256

unzip -p test-agent-internal-release.zip \
  dist/test-agent-frontend-dist.tar.gz | sha256sum
sha256sum /data/testagent/dist/test-agent-frontend-dist.tar.gz

tar -xOzf /data/testagent/dist/test-agent-frontend-dist.tar.gz \
  frontend/index.html | sha256sum
sha256sum /data/testagent/frontend/index.html

/data/apps/nginx/sbin/nginx \
  -p /data/apps/nginx/ \
  -c /data/apps/nginx/conf/nginx.conf \
  -t

/data/apps/nginx/sbin/nginx \
  -p /data/apps/nginx/ \
  -c /data/apps/nginx/conf/nginx.conf \
  -T 2>&1 | grep -E \
  'configuration file /data/apps/nginx/conf/test-agent.conf|listen (80|9996);|least_conn|map \$http_x_test_agent_linux_server_id|server 122\.233\.30\.(4|114):8080|proxy_buffering off|proxy_next_upstream'
```

前端 tar 的 ZIP 内/已安装 SHA 必须一致，tar 内 `frontend/index.html` 与已部署 `index.html` 的 SHA 也必须一致。有效 Nginx 配置必须加载 `/data/apps/nginx/conf/test-agent.conf`、包含两个 Java upstream、SSE 禁缓冲，且 `proxy_next_upstream` 不得包含 `non_idempotent`。

## 5. 不重启的基础健康检查

先在 `.4` 执行：

```bash
date -Is
timedatectl status
systemctl status test-agent-backend --no-pager
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS http://127.0.0.1:8080/actuator/health/readiness
cat /data/testagent/data/.serverid
cat /data/testagent/data/.serverhost
curl -fsS http://122.233.30.114:8080/actuator/health/readiness

cd /data/testagent/deploy/internal
./opencode-worker-docker.sh \
  --env-file /data/testagent/config/docker.env status
docker logs --tail 200 test-agent-opencode-worker
```

`.serverid/.serverhost` 应分别是 `test-agent-backend-122-233-30-4` 和 `122.233.30.4`；worker 日志应出现 `event=manager_config_update status=applied` 或兼容旧文本 `manager config update applied`，不应连续出现 WebSocket 断连、身份文件等待或 `OPENCODE_UNAVAILABLE`。

再在 `.114` 执行：

```bash
date -Is
timedatectl status
systemctl status test-agent-backend --no-pager
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS http://127.0.0.1:8080/actuator/health/readiness
cat /data/testagent/data/.serverid
cat /data/testagent/data/.serverhost
curl -fsS http://122.233.30.4:8080/actuator/health/readiness

cd /data/testagent/deploy/internal
./opencode-worker-docker.sh \
  --env-file /data/testagent/config/docker.env status
docker logs --tail 200 test-agent-opencode-worker
```

`.serverid/.serverhost` 应分别是 `test-agent-backend-122-233-30-114` 和 `122.233.30.114`。两台机器时间若相差明显，先记录偏差；不要在本次排查中擅自修改企业时间同步策略。

最后在 `.2` 执行：

```bash
date -Is
curl -fsS http://127.0.0.1:9996/health
curl -fsS http://122.233.30.4:8080/actuator/health/readiness
curl -fsS http://122.233.30.114:8080/actuator/health/readiness
tail -n 100 /data/apps/nginx/logs/error.log
```

任一 readiness、跨机访问或 worker 连接失败时，先解决该基础故障，不继续把问题归因到模型。

## 6. 用 traceId 和 runId 锁定责任节点

把浏览器记录的真实值替换到以下命令。先在 `.4` 执行：

```bash
grep -F 'REPLACE_TRACE_ID' \
  /data/testagent/logs/backend.log \
  /data/testagent/logs/sse.log \
  /data/testagent/logs/error.log

grep -F 'REPLACE_RUN_ID' \
  /data/testagent/logs/backend.log \
  /data/testagent/logs/sse.log \
  /data/testagent/logs/error.log

journalctl -u test-agent-backend \
  --since 'REPLACE_START_TIME' \
  --until 'REPLACE_END_TIME' \
  --no-pager | grep -E 'REPLACE_TRACE_ID|REPLACE_RUN_ID'
```

再在 `.114` 原样执行，仍替换为同一 `traceId/runId/时间窗口`：

```bash
grep -F 'REPLACE_TRACE_ID' \
  /data/testagent/logs/backend.log \
  /data/testagent/logs/sse.log \
  /data/testagent/logs/error.log

grep -F 'REPLACE_RUN_ID' \
  /data/testagent/logs/backend.log \
  /data/testagent/logs/sse.log \
  /data/testagent/logs/error.log

journalctl -u test-agent-backend \
  --since 'REPLACE_START_TIME' \
  --until 'REPLACE_END_TIME' \
  --no-pager | grep -E 'REPLACE_TRACE_ID|REPLACE_RUN_ID'
```

重点识别以下日志：

- `event=api_entry`：请求已进入该 Java Controller。
- `event=api_exit ... status=success responseBody=...`：Controller 已形成统一响应对象。
- `event=api_exit ... status=error`：按 `errorCode` 查首因。
- `event=api_stream_start/api_stream_end`、`SSE stream started/cancelled/error`：SSE 是否在该 Java 建立、取消或异常。
- `Run starting/Run started/Run failed to start`：Run 是否完成派发。
- `Failed to handle opencode stream event`、`RunEvent SSE target backend unavailable`、`Redis SSE stream error`：OpenCode 事件、跨 Java 路由或 Redis 数据面异常。

判定规则：

| 证据 | 责任层 |
|---|---|
| 两台 Java 都没有 `api_entry` | 浏览器、DNS、`.2` Nginx 或 Nginx 前的企业入口。 |
| 入口 Java 有 trace，权威 Java 没有，同期有路由错误 | Redis 在线快照、binding/Run producer 路由或两台 Java 互访。 |
| 权威 Java 有 `api_exit responseBody=...`，浏览器收到零字节 | Java 响应写出之后到浏览器之间，继续比较直连 Java 与 Nginx。 |
| Java `api_exit` 的响应本身为 `null`/空 | Controller 或业务层，保留日志和对应版本后交研发。 |
| Run 创建响应正常，但没有 SSE start | 浏览器订阅、Nginx SSE 或 Run producer 路由。 |
| SSE 已建立，有 `run.succeeded`，但没有 assistant/message part | 用户 OpenCode、模型流或消息投影，不是 Nginx HTTP 空响应。 |
| 只有 `.4` 或只有 `.114` 上的用户失败 | 该节点 JAR/lib、公共配置、Java 模型快照、worker 或用户进程环境。 |
| 两台、多个新旧用户同时失败 | 优先查 `.2` Nginx、共享数据库模型配置、Redis或企业模型上游。 |

## 7. 用只读 GET 比较 Nginx 与直连 Java

不要用创建 Session/Run 的 POST 做探针，避免重复创建业务数据。以下使用故障 Session 的只读 `active-run`。

在 `.2` 执行：

```bash
read -r -s -p '输入当前登录 JWT（不会回显）: ' TEST_AGENT_DIAG_JWT
echo
TEST_AGENT_DIAG_TRACE="trace_empty_body_gateway_$(date +%Y%m%d%H%M%S)"

curl -sS --connect-timeout 10 --max-time 30 \
  -D /tmp/empty-body-gateway.headers \
  -o /tmp/empty-body-gateway.body \
  -w 'http_code=%{http_code} size_download=%{size_download} time_total=%{time_total}\n' \
  'http://127.0.0.1:9996/api/internal/platform/opencode-runtime/sessions/REPLACE_SESSION_ID/active-run' \
  -H "Authorization: Bearer ${TEST_AGENT_DIAG_JWT}" \
  -H 'X-Test-Agent-Linux-Server-Id: REPLACE_LINUX_SERVER_ID' \
  -H "X-Trace-Id: ${TEST_AGENT_DIAG_TRACE}"

grep -Ei '^(HTTP/|content-type:|content-length:|transfer-encoding:|x-trace-id:)' \
  /tmp/empty-body-gateway.headers
wc -c /tmp/empty-body-gateway.body
sed -n '1,5p' /tmp/empty-body-gateway.body
unset TEST_AGENT_DIAG_JWT TEST_AGENT_DIAG_TRACE
```

在故障用户实际绑定的后台执行。绑定 `.4` 就在 `.4` 执行；绑定 `.114` 就在 `.114` 执行：

```bash
read -r -s -p '输入当前登录 JWT（不会回显）: ' TEST_AGENT_DIAG_JWT
echo
TEST_AGENT_DIAG_TRACE="trace_empty_body_direct_$(date +%Y%m%d%H%M%S)"

curl -sS --connect-timeout 10 --max-time 30 \
  -D /tmp/empty-body-direct.headers \
  -o /tmp/empty-body-direct.body \
  -w 'http_code=%{http_code} size_download=%{size_download} time_total=%{time_total}\n' \
  'http://127.0.0.1:8080/api/internal/platform/opencode-runtime/sessions/REPLACE_SESSION_ID/active-run' \
  -H "Authorization: Bearer ${TEST_AGENT_DIAG_JWT}" \
  -H "X-Trace-Id: ${TEST_AGENT_DIAG_TRACE}"

grep -Ei '^(HTTP/|content-type:|content-length:|transfer-encoding:|x-trace-id:)' \
  /tmp/empty-body-direct.headers
wc -c /tmp/empty-body-direct.body
sed -n '1,5p' /tmp/empty-body-direct.body
unset TEST_AGENT_DIAG_JWT TEST_AGENT_DIAG_TRACE
```

两次都应返回 `application/json` 且正文大于 0 字节。即使没有 active Run，也应看到统一 JSON 的 `data:null`。

- 直连 Java 有正文、Nginx 为零字节：查 `.2` 有效配置、error/access log 和 Nginx 前置企业代理。
- 两条都为零字节：查对应 Java 的新 traceId；若请求被转发，还要在另一台 Java 查同一 traceId。
- 两条都正常、浏览器仍为空：查浏览器实际 URL、缓存中的前端 buildVersion、Network 响应和是否观察错了请求记录。

## 8. 单独验证 RunEvent SSE

在 `.2` 通过 Nginx 执行：

```bash
read -r -s -p '输入当前登录 JWT（不会回显）: ' TEST_AGENT_DIAG_JWT
echo

curl -sS -N --max-time 20 \
  -D /tmp/empty-body-sse-gateway.headers \
  -o /tmp/empty-body-sse-gateway.body \
  'http://127.0.0.1:9996/api/internal/agent/opencode/runs/REPLACE_RUN_ID/events' \
  -H "Authorization: Bearer ${TEST_AGENT_DIAG_JWT}" \
  -H 'Accept: text/event-stream' \
  -H 'X-Test-Agent-Linux-Server-Id: REPLACE_LINUX_SERVER_ID'

grep -Ei '^(HTTP/|content-type:|transfer-encoding:|x-trace-id:|x-accel-buffering:)' \
  /tmp/empty-body-sse-gateway.headers
sed -n '1,80p' /tmp/empty-body-sse-gateway.body
grep -n '^data:[[:space:]]*$' /tmp/empty-body-sse-gateway.body
unset TEST_AGENT_DIAG_JWT
```

`curl` 因 20 秒到期返回 28 可以接受；关键是响应应为 `200 text/event-stream`，有效帧使用 `event:` 和非空 `data:{...}`。最后一条空 `data:` 检查应无输出。

再在 Run 实际 producer 后台直连执行：

```bash
read -r -s -p '输入当前登录 JWT（不会回显）: ' TEST_AGENT_DIAG_JWT
echo

curl -sS -N --max-time 20 \
  -D /tmp/empty-body-sse-direct.headers \
  -o /tmp/empty-body-sse-direct.body \
  'http://127.0.0.1:8080/api/internal/agent/opencode/runs/REPLACE_RUN_ID/events' \
  -H "Authorization: Bearer ${TEST_AGENT_DIAG_JWT}" \
  -H 'Accept: text/event-stream'

grep -Ei '^(HTTP/|content-type:|transfer-encoding:|x-trace-id:|x-accel-buffering:)' \
  /tmp/empty-body-sse-direct.headers
sed -n '1,80p' /tmp/empty-body-sse-direct.body
grep -n '^data:[[:space:]]*$' /tmp/empty-body-sse-direct.body
unset TEST_AGENT_DIAG_JWT
```

判断：

- 直连有帧、Nginx 无帧：查 `.2` 的 `proxy_buffering off`、长连接超时和前置网络设备。
- 两者都有完整帧，但页面无回答：查 SSE payload 是否出现 assistant/message part，再执行第 9 节。
- 两者都只有 `run.succeeded`、没有任何 assistant 文本：查用户 OpenCode 日志和模型代理。
- 直连返回 JSON 错误而不是 SSE：按响应中的统一错误码处理 Run producer、权限或 Redis 状态。

## 9. 验证用户 OpenCode 和企业模型链路

> 已部署带「内部模型调用可观测」的版本时，先看系统管理 → 内部模型可观测页面：调用记录 tab 默认只列真实用户调用（可切换探活），展示时间、模型、来源、结果、总耗时和首 token；其中 `firstByteMillis` 是响应头时间，`firstTokenMillis` 是首个包含模型输出字段的 SSE data 时间。聚合统计 tab 默认按 `source=USER_CALL` 汇总小时成功率、准确平均/最大耗时和平均/最大首 token，不展示无法从小时 sum/max 还原的 P90/P95。探活状态卡片显示每个 provider 的最近探活结果与连续失败次数。先按页面的结果分类和时间窗口定位本次失败属于哪类（`UPSTREAM_CONNECT_FAILED` / `UPSTREAM_FIRST_RESPONSE_TIMEOUT` / `UPSTREAM_HTTP_ERROR` / `UPSTREAM_STREAM_INTERRUPTED` 等），再按分类进入下方对应层排查；明细 API 仍保留 `providerId/httpStatus/traceId` 等结构化字段供后台检索，也可以在页面手动触发「全部探活」主动确认当前端点可达性，避免先登机器 grep 日志。

先在运行管理确认故障用户为 `BOUND`，记录实际动态端口和所属后台。只在所属后台执行：

```bash
curl -fsS http://127.0.0.1:REPLACE_PORT/global/health
curl -fsS http://127.0.0.1:REPLACE_PORT/api/provider
curl -fsS http://127.0.0.1:REPLACE_PORT/api/model

tail -n 200 \
  /data/testagent/data/agent-opencode/manager/worker/logs/manager.log
tail -n 200 \
  /data/testagent/data/agent-opencode/manager/worker/logs/manager-error.log

find /data/testagent/data/agent-opencode/manager/worker/logs \
  -maxdepth 1 -type f -name '*-REPLACE_PORT.log' \
  -printf '%TY-%Tm-%Td %TH:%TM:%TS %p\n' | sort -r | head -n 5
```

从最后一条命令中选择与当前进程启动时间匹配的精确日志，再执行：

```bash
tail -n 300 REPLACE_EXACT_USER_OPENCODE_LOG
grep -nE \
  'prompt_async failed|errors building|failed to add snapshot files|unknown option.*sparse|statusCode=' \
  REPLACE_EXACT_USER_OPENCODE_LOG
```

日志文件名包含统一认证号，外发前必须脱敏。先按当前 Session、消息 ID 和分钟级时间窗口精确过滤，不要用 provider 名称全盘搜索后把历史会话错误当成当前故障。重点查模型 HTTP 状态、上游正文、`data:data:`、`[DONE]` 缺失、连接超时、provider/model 不存在、配置读取失败和自定义工具构建失败。

若同一当前会话出现下面的组合，可直接判定为“自定义工具构建失败”，不再把它归类为模型返回空报文：

```text
prompt_async failed
AggregateError: ... errors building ".../opencode/tools/REPLACE_TOOL.ts"
```

OpenCode 1.18.4 会扫描配置目录下的 `tool/tools` JavaScript 和 TypeScript 文件，并在提示处理时加载全部匹配工具；任一文件存在语法、导入、依赖或导出结构错误，都可能在主模型调用前中止整个提示。此时常见伴随现象是 assistant 消息 token 全为 0、没有 assistant part，随后根 Session 又进入 idle，平台只观察终态后可能产生误导性的 `run.succeeded`。

若底层异常明确为 `Cannot find module '@opencode/plugin'` 或 `Cannot find module '@opencode/client'`，且失败路径位于 `personalworktree/.../workspace/.opencode/tools/`，先检查 OpenCode 进程工作目录和祖先依赖投影。超级管理员通常使用公共配置工具，或者现场已存在历史链接，因此可能未触发；这是工作区路径与模块解析差异，不是角色鉴权差异。同一用户进入相同的深层应用 workspace 仍会失败。只有切换到 1.18.4 回滚包时，才检查对应的 V1 `@opencode-ai/plugin`/`@opencode-ai/sdk`。

正式 V2 版本由 OpenCode 启动器在进程工作目录的 `node_modules` 自动非覆盖式链接 `@opencode/plugin`、`@opencode/client`、`effect` 和 `zod`。部署包含该修复的新 worker/programs 并重启用户 OpenCode 进程后，无需为每个个人 worktree 单独建链接；验收时从失败工具目录执行模块导入探测，并确认它解析到 `/data/testagent/programs/opencode/node_modules/`。临时手工链接只能用于已保全证据后的现场恢复，不能代替正式发包。V1 回滚进程由同一 launcher 按 1.18.4 合约投影旧 SDK 依赖。

处置规则：

1. 记录失败文件相对路径、公共配置提交/版本、文件 SHA-256 和完整的两条构建诊断；不得把源文件中的密钥或业务内容带入上报材料。
2. 通过平台公共配置管理定位权威 Git 中的失败工具，回退到最近一次已验证版本，或在开发环境修复并通过工具加载验证后再发布。
3. `/data/testagent/data/agent-opencode/.configdev/public-<UCID>/` 是逐用户生成投影，不是权威编辑源；禁止在生产机直接删除、改名或修改其中的工具文件。发布公共配置后，按运行管理的标准流程更新或重启受影响用户进程。
4. 新建 Session 只复现一次，验收日志中不再出现 `errors building`，并且在 `run.succeeded` 前能看到非零 token 或 assistant message part。

如果同一日志还出现 `failed to add snapshot files` 和 `git add --sparse` 的 `unknown option`，这是企业旧版 Git 不支持该参数的独立兼容告警。保留告警并安排 Git 兼容治理，但不能用它替代上面的工具构建首因，也不要因此绕过公共配置发布流程。

再从同一 Java 宿主机检查企业模型网络：

```bash
nc -vz ai-code.sdc.icbc 9070
```

然后绕过用户 OpenCode，直接验证“本机 Java → 9070”正式模型链路。下方 `Authorization: Bearer` 携带的是 OpenCode → Java 的内部代理 Key，只用于通过 Java 代理鉴权；Java 转发时会删除它，并以数据库中的供应商 Token 生成上游 `Auth-Token`。不要把这里的代理 Bearer 误改成供应商 Bearer：企业上游使用供应商 Bearer 时虽然可鉴权，但 `ucid` 不生效，只有 `Auth-Token` 才会让 `ucid` 生效。代理 key 从 `backend.env` 只读加载到 shell 变量，不打印；UCID 交互输入，不写入命令历史：

```bash
TEST_AGENT_DIAG_PROXY_KEY="$(sed -n 's/^TEST_AGENT_INTERNAL_PROXY_API_KEY=//p' \
  /data/testagent/config/backend.env)"
read -r -s -p '输入测试用户 UCID（不会回显）: ' TEST_AGENT_DIAG_UCID
echo

curl -sS -N --max-time 180 \
  -D /tmp/empty-body-model.headers \
  -o /tmp/empty-body-model.sse \
  'http://127.0.0.1:8080/api/internal/platform/opencode-runtime/internal-model-proxy/v1/chat/completions' \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -H "Authorization: Bearer ${TEST_AGENT_DIAG_PROXY_KEY}" \
  -H 'X-Enterprise-Model-Provider: REPLACE_PROVIDER_ID' \
  -H "ucid: ${TEST_AGENT_DIAG_UCID}" \
  --data 'REPLACE_JSON_REQUEST_BODY'

grep -Ei '^(HTTP/|content-type:|content-length:|transfer-encoding:|retry-after:|x-trace-id:)' \
  /tmp/empty-body-model.headers
wc -c /tmp/empty-body-model.sse
sed -n '1,80p' /tmp/empty-body-model.sse
grep -n '^data:[[:space:]]*$' /tmp/empty-body-model.sse
grep -n '^data:data:' /tmp/empty-body-model.sse
grep -n '^data: \[DONE\]$' /tmp/empty-body-model.sse
unset TEST_AGENT_DIAG_PROXY_KEY TEST_AGENT_DIAG_UCID
```

Qwen 替换值：

```text
REPLACE_PROVIDER_ID = qwen-prod
REPLACE_JSON_REQUEST_BODY = {"model":"Qwen3.6-27B","messages":[{"role":"user","content":"只回复OK"}],"stream":true}
```

DeepSeek 替换值：

```text
REPLACE_PROVIDER_ID = deepseek-prod
REPLACE_JSON_REQUEST_BODY = {"model":"DeepSeek-V4-Flash-W8A8","messages":[{"role":"user","content":"只回复OK"}],"stream":true}
```

先测故障会话使用的 provider，再用另一 provider 做对照；`.4` 和 `.114` 都要各自执行。成功条件是持续收到单层 `data:`、无空 `data:`、无 `data:data:`，最后收到单层 `[DONE]`。当前 Java 对非 2xx 和非 SSE 响应会原样透传状态、Content-Type 和错误正文；如果这里收到 4xx/5xx 且正文仍为零字节，保留 headers、字节数和 Java traceId，交 Java 代理/企业模型负责人联合排查。

若 Java 代理返回 `400` 且正文为 0 字节，而同宿主机使用企业批准方式直连同一 9070 上游能够得到 `200 text/event-stream`、有效 chunk 和 `[DONE]`，只能证明网络和上游模型服务可用；同时证明 Java 正式代理链仍有独立配置问题。依次核对：

1. `internal_model_providers` 中故障 `provider_id` 的启用状态、准确模型 ID 和 `base_url`。`base_url` 应停在 OpenAI 兼容 API 根路径，不能已包含 `/chat/completions`，因为 Java 还会追加该 path。
2. provider 关联的数据库 token 是否为当前有效版本；不得从 shell 历史或诊断文件回填已经暴露的旧 token。
3. 两台 Java 的供应商内存快照是否已在配置保存后刷新，故障节点实际使用的 provider/token 版本是否一致。
4. Java 注入的 UCID 和企业上游要求的 header 是否齐全且值域正确。只记录“存在/缺失”和脱敏摘要，不打印真实值。

修正后重新执行 Java 代理测试；只有返回 `200 text/event-stream`、正文大于 0 字节、存在有效单层 `data:` 和 `[DONE]` 才算通过。直连 9070 成功不能替代这项验收。

判断：

- Java 代理测试正常、用户 OpenCode 仍为空：用户进程读取的是旧公共配置或旧注入环境，检查 `/api/provider`、`/api/model` 和用户端口日志；证据确认后只重启受影响用户进程。
- 同节点两个 provider 都失败：检查该 Java 的供应商内存快照、内部代理 key、UCID 注入和到 9070 的网络。
- 仅一个 provider 失败：检查该 `provider_id` 的启用状态、准确模型 ID、关联 token 和 base URL；公共配置必须保留 `includeUsage=false`。
- `.4` 正常、`.114` 失败或反之：节点安装、Java 内存刷新、公共配置或出站网络不一致。
- 两台 Java 代理都正常，但页面所有用户都失败：回到 RunEvent、用户 OpenCode 和前端投影，不要改企业模型配置。

## 10. 处置决策表

| 最终证据 | 处置 |
|---|---|
| 只有无请求体的 GET/Cancel 被显示为空 | 正常现象，不重启、不重部署。 |
| `.2` 前端 tar 或 `index.html` SHA 不一致 | 用当前固定外层包重新执行 `.2` 的 `deploy-frontend-node.sh`，成功后浏览器强制刷新并复核 buildVersion。 |
| `.4/.114` 的 app、api、runtime 或 event JAR SHA 不一致 | 停止业务写入，按 `.4 → .114 → .2` 的标准入口重新部署完整包；禁止只复制单个 JAR。 |
| systemd MainPID 不是 8080 监听者 | 说明旧手工进程或错误 unit 仍在提供服务；按标准部署脚本处理，不盲目 kill 未确认进程。 |
| 直连 Java 有正文，Nginx 为零字节 | 修复 `.2` 实际加载的 `test-agent.conf` 或前置网络设备；只在配置校验通过后 reload Nginx。 |
| Java API 日志已有完整 responseBody，直连客户端仍为零字节 | 保留 traceId 和 exact JAR SHA，交响应序列化/路由转发负责人；不要用重复 POST 继续探测。 |
| SSE 直连正常、Nginx 断流 | 查 SSE 禁缓冲、长连接超时和中间网络设备。 |
| SSE 到达 `run.succeeded` 但没有文本事件 | 查用户 OpenCode 日志、模型流、消息投影和 `[DONE]`。 |
| 当前会话出现 `prompt_async failed` 和 `errors building .../opencode/tools/*.ts` | 先看失败路径和底层异常：公共工具在权威公共配置 Git 中修复；应用工具按应用发布流程修复；深层应用工具缺少 V2 `@opencode/plugin`/`@opencode/client` 时部署已包含祖先依赖投影的新 worker/programs，再重启用户进程；只有 V1 回滚包按旧 SDK 依赖排查。 |
| 同时出现 `git add --sparse` 的 `unknown option` | 作为旧 Git 兼容告警单独治理；只有同时出现工具构建错误时，提示中止首因仍是失败工具。 |
| Java 模型代理正常，只有旧用户 OpenCode 失败 | 在运行管理重启该用户进程，使其重新读取公共配置和逐用户注入环境；不需要重启所有 Java。 |
| Java 模型代理 `400` 且 0 字节，批准的同机 9070 直连正常 | 核对 provider API 根路径、关联 token、Java 内存快照及 UCID/header；修复后必须以 Java 代理 `200 text/event-stream` 复验。 |
| 一台模型代理失败 | 修复该节点 Java 快照、公共配置或 9070 网络；用户 binding 不自动迁移，不通过切换 Nginx 掩盖。 |
| 两台模型代理都返回同一空响应 | 携带两台 headers、traceId、时间和零字节证据交企业模型上游；不提交 token、UCID 或用户提示词。 |

## 11. 需要重新部署时

只有第 4 节证明包或安装不一致，或者研发提供了包含明确修复的新包时才重部署。当前现场包已经进入企业内部后，从中转机校验和 `scp` 开始，不要求重跑 Mac 打包。三台应用服务器都接收下面这一对固定文件到 `/data/0709`：

```text
test-agent-two-backend-complete.zip
test-agent-two-backend-complete.zip.sha256
```

外层包内包含内层完整发布 ZIP 和 `.4/.114/.2` 节点包；内层发布包含：

```text
dist/backend/test-agent-app.jar
dist/backend/lib/
dist/test-agent-frontend-dist.tar.gz
dist/test-agent-programs.tar.gz
dist/test-agent-opencode-worker_internal-linux-amd64.tar
dist/frontend/
deploy/internal/
```

部署前只检查配置是否完整，不打印值：

- `.4/.114`：`/data/testagent/config/backend.env`、`/data/testagent/config/docker.env`；两者数据根目录一致，manager token 一致，`backend.env` 的 advertised host/server ID 分别属于本机。
- `.2`：`/data/testagent/config/nginx.env`；有效配置包含 `.4/.114` 两个 backend 和两个静态 server route。
- 不修改根目录 `.env.local/.env.test`，不把 token、密码、Cookie、RSA 私钥写入排查材料。

执行顺序固定：

1. `.4` 执行外层 `deploy-backend-node.sh`；脚本先启动 Java，确认 health/readiness 和 `.serverid/.serverhost`，再处理 worker。失败立即停止。
2. `.114` 执行外层 `deploy-backend-node.sh`；按同样条件通过后再继续。
3. `.2` 执行外层 `deploy-frontend-node.sh`，由实体 Nginx `-t/-T` 后 reload。
4. 浏览器重新执行第 3、7、8、9 节的业务验收。

如果新包包含 Flyway 变更，停机前必须只读留存目标 PostgreSQL 的 `installed_rank/version/description/checksum/success`；任一未知 checksum、失败记录或未知更高版本都停止。只允许首台 `.4` 执行升级并确认 history/readiness 后再启动 `.114`；禁止 `repair`、`outOfOrder` 或手工修改 `flyway_schema_history`。

manager 或动态端口异常时，优先检查：

1. Java 已先写出本机 `.serverid/.serverhost`。
2. `SYS_DATA_ROOT_DIR` 与 `TEST_AGENT_DATA_ROOT` 都是 `/data/testagent/data`。
3. worker 由 `opencode-worker-docker.sh` 管理，端口池为 `14096-15095`，不手工直接运行 manager。
4. manager 配置已应用、容器健康、动态端口监听，用户进程日志对应当前启动时间。
5. `.4/.114` 到对端 Java、Redis 和企业模型网络均可达。

## 12. 最小上报材料

向研发或企业模型团队上报时，只提供：

- 发生时间、入口 URL 的 path、HTTP 状态、Content-Type、响应字节数。
- 脱敏的 `traceId/runId/sessionId` 和用户绑定服务器/动态端口。
- 前端、两台 Java、两台 manager buildVersion。
- 外层/内层校验结果，以及第 4 节相关安装 SHA 是否一致。
- Nginx 与直连 Java 的 headers、字节数对比。
- 两台 Java 按 traceId/runId 过滤后的必要日志。
- 用户 OpenCode 和 Java 模型代理的状态、是否有 `[DONE]`、是否出现空 `data:`。
- 影响范围是单用户、单节点、单 provider 还是全局。

不得上报真实数据库密码、JWT、Cookie、内部代理 key、上游 token、RSA 私钥、完整 `backend.env/docker.env`、未脱敏 HAR、完整用户问题或包含统一认证号的原始日志文件名。

相关完整部署与协议说明见 [多后台部署](MULTI-BACKEND.md)、[单后台部署](SINGLE-BACKEND.md)、[HTTP API](../../docs/api/http-api.md) 和 [RunEvent SSE](../../docs/api/event-stream.md)。
