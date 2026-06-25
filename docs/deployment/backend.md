# 后端 Docker 部署说明

## 部署边界

生产和研发测试环境只将 `test-agent-app` 后端 Java 进程放入 Docker 容器。PostgreSQL、Redis 和 opencode server 都是外部服务，通过环境变量或配置中心注入地址和凭据；后端镜像不包含也不启动这些依赖。

研发测试环境的 PG/PostgreSQL 数据库由远端环境启动和维护，不在后端容器或本仓库 Docker Compose 中启动；后端只通过 `TEST_AGENT_TEST_DB_*` 或生产 `TEST_AGENT_DB_*` 配置连接该远端数据库。

`deploy/local/docker-compose.yml` 只作为个人离线开发备用入口，不能作为研发测试或生产部署拓扑。

## opencode-manager 容器进程管理

用户专属 opencode server 进程由每个 opencode 容器内的 `opencode-manager` 管理。`opencode-manager` 是与 `backend/` 平级的 Go 单二进制工程，不打包进后端 Java 镜像；它既提供本地 CLI，也提供 `run` 长运行模式，通过 WebSocket JSON 控制面连接所有 READY 后端 Java 实例。

容器内必须挂载以下目录：

```text
/data/opencode/session              # 用户进程 XDG_DATA_HOME 根目录，按端口分目录
/data/opencode/.config/opencode/    # 公共 agent、插件、skill 等配置
/data/opencode/manager              # manager 本地 state 和日志
```

容器环境变量示例：

```dotenv
OPENCODE_MANAGER_CONTAINER_ID=ctr_01
OPENCODE_MANAGER_LINUX_SERVER_ID=10.8.0.12
OPENCODE_MANAGER_PORT_START=4096
OPENCODE_MANAGER_PORT_END=4100
OPENCODE_MANAGER_MAX_PROCESSES=5
OPENCODE_MANAGER_ID=mgr_1234567890abcdef
OPENCODE_MANAGER_BACKEND_DISCOVERY_URL=http://10.8.0.21:8080/api/internal/platform/opencode-runtime/manager-backends
OPENCODE_MANAGER_TOKEN=<manager-control-token>
OPENCODE_BIN=opencode
OPENCODE_MANAGER_STATE_DIR=/data/opencode/manager
OPENCODE_SESSION_ROOT=/data/opencode/session
OPENCODE_CONFIG_DIR=/data/opencode/.config/opencode/
```

长运行模式启动：

```bash
opencode-manager run
```

`run` 会按 `OPENCODE_MANAGER_BACKEND_DISCOVERY_URL` 周期发现后端实例，并用 `Authorization: Bearer <OPENCODE_MANAGER_TOKEN>` 与每个实例建立 `/api/internal/platform/opencode-runtime/manager/ws` WebSocket 长连接。后端扩容后，manager 在下一轮 discovery 中自动连接新实例。生产必须把 discovery URL 指向 manager 可访问的某个后端直连地址或内部服务发现地址；后端返回给 manager 的 `webSocketUrl` 由各实例自己的直连 `listen-url` 生成，不能只配置负载均衡地址。

启动单个用户进程时，manager 会执行：

```bash
XDG_DATA_HOME=/data/opencode/session/{port} \
OPENCODE_CONFIG_DIR=/data/opencode/.config/opencode/ \
opencode serve --hostname 0.0.0.0 --port {port} --print-logs
```

opencode server 默认不设置 `OPENCODE_SERVER_PASSWORD`，后端仍按 `http://{linuxServerIp}:{port}` 访问。生产部署必须通过容器网络、主机防火墙或网关限制端口池访问面，不得把用户进程端口暴露到不可信网络。

启用用户进程模型后，已登录用户的 Run 和 opencode runtime 代理都会优先使用当前用户绑定的 `READY` 进程；用户未初始化或健康检测失败时返回平台 `OPENCODE_UNAVAILABLE`，由前端提示初始化。无用户主体的 static-token 兼容调用仍可使用配置 seed 写入的固定 `execution_nodes`，用于旧集成或本地探测。Session 级 runtime 代理发现绑定节点不是当前用户进程节点时，会在当前进程上创建新的远端 session 并覆盖绑定，不会删除旧远端 session。

## 多服务器用户进程拓扑规划

一个生产集群由以下角色组成：

| 角色 | 部署数量 | 关键配置 | 说明 |
|---|---:|---|---|
| 后端 Java 实例 | 每台 Linux 服务器 1 个或按容量水平扩展 | `TEST_AGENT_BACKEND_LISTEN_URL`、`TEST_AGENT_LINUX_SERVER_ID`、`TEST_AGENT_OPENCODE_MANAGER_TOKEN` | `listen-url` 必须是 manager 可直连的实例地址，不能只填负载均衡地址；`linux-server-id` 当前使用 IPv4。 |
| opencode 容器 | 每台 Linux 服务器多个 | `OPENCODE_MANAGER_CONTAINER_ID`、端口池、挂载目录 | 每个容器运行 1 个 `opencode-manager run`。 |
| 用户 opencode server 进程 | 每个用户 1 个当前绑定 | 由 manager 按端口启动 | `baseUrl` 固定为 `http://{linuxServerIp}:{port}`，session 持久化在对应 Linux 服务器。 |
| 前端访问入口 | 1 个负载均衡域名 | `VITE_TEST_AGENT_API_BASE_URL` | 浏览器只访问平台后端，不直连 opencode server 或 manager。 |

后端实例之间不需要互连；每个 manager 会通过 discovery API 获得所有 READY 后端实例，并与每个后端实例建立 WebSocket 控制连接。后端扩容时，新实例启动并写入 `backend_java_processes` 后，manager 下一轮 discovery 会自动连接它。

端口池规划必须满足：

- 同一 Linux 服务器上所有 opencode 容器的主机可访问端口范围不能重叠，数据库约束 `opencode_server_processes(linux_server_id, port)` 会拒绝同一服务器端口重复。
- `OPENCODE_MANAGER_MAX_PROCESSES` 不得超过 `OPENCODE_MANAGER_PORT_END - OPENCODE_MANAGER_PORT_START + 1`。
- 建议每个容器预留 1 到 2 个端口作为故障排查或滚动扩容缓冲，不要把端口池全部按理论最大值打满。
- `OPENCODE_MANAGER_LINUX_SERVER_ID` 必须与后端写入的 `TEST_AGENT_LINUX_SERVER_ID` 使用同一 IPv4 表达，否则用户进程 `baseUrl` 和同服务器重建规则会不一致。
- 本地 `./restart-dev-services.sh --env-file .env.local` 在未显式配置 `TEST_AGENT_LINUX_SERVER_ID` / `TEST_AGENT_BACKEND_LISTEN_URL` / `OPENCODE_MANAGER_LINUX_SERVER_ID` 时，会使用默认路由网卡的 IPv4 自动填充；生产和多机部署仍建议显式配置，避免网卡切换造成实例标识变化。

目录和日志规划：

| 路径 | 所属节点 | 用途 | 运维要求 |
|---|---|---|---|
| `/data/opencode/session/{port}` | Linux 服务器本地盘并挂载到容器 | 用户进程 `XDG_DATA_HOME` | 不能跨 Linux 服务器共享；备份/清理必须按端口和用户绑定关系执行。 |
| `/data/opencode/.config/opencode/` | Linux 服务器本地盘并挂载到容器 | 公共 agent、插件、skill 配置 | 多容器共享只读或受控写入；变更前先备份。 |
| `/data/opencode/manager/processes/{port}.json` | 容器挂载目录 | manager 本地进程状态 | 用于 stop/list/restart；容器重启后继续识别已有 state。 |
| `/data/opencode/manager/logs/{port}.log` | 容器挂载目录 | opencode server stdout/stderr | 日志不得输出 token、Authorization、Cookie 或完整 prompt。 |

容量与心跳参数建议：

| 参数 | 默认/建议 | 说明 |
|---|---|---|
| `TEST_AGENT_BACKEND_HEARTBEAT_INTERVAL` | `10s` | 后端实例刷新 `linux_servers` 和 `backend_java_processes` 的间隔。 |
| `TEST_AGENT_BACKEND_STALE_AFTER` | `30s` | discovery 只返回未过期 READY 后端实例；应至少大于心跳间隔 3 倍。 |
| `TEST_AGENT_BACKEND_DISCOVERY_LIMIT` | `100` | manager discovery 返回后端实例上限。 |
| `TEST_AGENT_OPENCODE_MANAGER_COMMAND_TIMEOUT` | `10s` | 后端等待 manager 命令结果的超时。 |
| `OPENCODE_MANAGER_DISCOVERY_INTERVAL` | `10s` | manager 发现新增后端实例的间隔。 |
| `OPENCODE_MANAGER_HEARTBEAT_INTERVAL` | `10s` | manager 刷新容器、manager 和连接状态的间隔。 |
| `OPENCODE_MANAGER_RECONNECT_INTERVAL` | `5s` | manager WebSocket 断线重连间隔。 |

## 扩容、故障处理与回滚

后端 Java 扩容流程：

1. 为新实例配置唯一的 `TEST_AGENT_BACKEND_LISTEN_URL=http://<backend-ip>:<port>` 和 `TEST_AGENT_LINUX_SERVER_ID=<backend-ip>`。
2. 使用同一个 `TEST_AGENT_OPENCODE_MANAGER_TOKEN`，并确认该地址可从所有 opencode 容器访问。
3. 启动新后端，检查 `/actuator/health` 返回 `UP`。
4. 使用 manager token 调 `GET /api/internal/platform/opencode-runtime/manager-backends`，确认返回包含新 `backendProcessId` 和 `webSocketUrl`。
5. 等待一个 discovery 周期后，在超级管理员运行管理页确认 manager-backend 连接出现并为 `CONNECTED`。

opencode 容器扩容流程：

1. 在同一 Linux 服务器上分配不与既有容器重叠的端口池。
2. 按上文挂载 `/data/opencode/session`、`/data/opencode/.config/opencode/` 和 `/data/opencode/manager`。
3. 配置新的 `OPENCODE_MANAGER_CONTAINER_ID`、`OPENCODE_MANAGER_ID` 和端口池环境变量。
4. 启动 `opencode-manager run`，检查运行管理页中 `containers`、`managers` 和 `managerBackendConnections` 均出现对应记录。

常见故障处理：

| 现象 | 排查顺序 | 处理 |
|---|---|---|
| 用户初始化返回 `OPENCODE_UNAVAILABLE` | 运行管理页查看是否有 `READY` 容器和 `CONNECTED` manager；检查 manager discovery 是否成功 | 恢复 manager WebSocket 连接或启动有空余端口的容器。 |
| 用户初始化返回 `OPENCODE_TIMEOUT` | 查看 `{stateDir}/logs/{port}.log`、后端命令超时配置、opencode CLI 是否卡住 | 先保留日志，再 stop/restart 目标端口或扩容新容器。 |
| 进程健康异常后没有同服务器重建 | 检查原 `linuxServerId` 下是否还有 `READY` 且有容量的容器 | 在同一 Linux 服务器上恢复或扩容容器；不要把该用户迁移到其他服务器，否则 session 目录不可用。 |
| 后端扩容后 manager 未连接新实例 | 检查新后端 `listenUrl/webSocketUrl` 是否是容器可达直连地址；检查 manager token | 修正 `TEST_AGENT_BACKEND_LISTEN_URL` 或 token 后等待下一轮 discovery。 |
| 管理页看不到数据 | 确认登录用户有 `SUPER_ADMIN`；检查 V10 表是否存在；检查后端/manager 心跳 | 非超管前端菜单隐藏且后端返回 `FORBIDDEN` 是预期行为。 |

回滚策略：

- V10 opencode 用户进程管理表是新增表，不修改旧 `execution_nodes`、`sessions.opencode_*` 或 `agent_session_bindings` 的兼容字段；数据库结构可以保留，不需要在应用回滚时删除。
- 无用户主体的 static-token 兼容调用仍可走固定 `execution_nodes`；已登录 Web 用户在当前版本会优先要求用户专属进程。如果要把 Web 对话完整回退到固定节点模式，应回滚后端和前端镜像到引入用户进程模型之前的版本。
- 回滚前不要清理 `/data/opencode/session/{port}`，否则再恢复用户进程模型时会丢失远端 session 状态。
- 回滚后若继续保留 manager 容器，应停止 `opencode-manager run` 或撤销 manager token，避免旧版本无法识别的控制面连接持续重试。

## 真实环境验收

只读 smoke check：

```bash
tools/verify-opencode-process-deployment.sh \
  --backend-url http://<backend-or-lb>:8080 \
  --manager-token <manager-control-token> \
  --auth-token <super-admin-user-jwt>
```

该脚本只检查 `/actuator/health`、manager discovery 和超级管理员 overview，不会启动、停止、重启或健康检测用户进程。未提供 token 时对应高权限接口会被跳过；生产验收建议传入两个 token，并在 shell history 策略中避免保存真实值。

手工验收清单：

1. 首次登录：普通用户进入工作台后进程状态为 `NEEDS_INITIALIZATION`，点击初始化后创建 1 条 `user_opencode_process_bindings` 当前绑定和 1 条 `opencode_server_processes` 记录。
2. 复用绑定：同一用户退出再登录，状态为 `READY`，`processId/linuxServerId/port/baseUrl` 不变化。
3. Run 防绕过：用户未初始化或进程不可用时，前端禁用发送；直接调用 Run API 返回 `OPENCODE_UNAVAILABLE`，不创建本地 Run。
4. 原服务器重建：停止当前用户进程或让 health 失败后重新初始化，新的进程仍位于原 `linuxServerId` 下的可用容器。
5. 后端扩容：新增后端实例后，manager discovery 返回新实例，运行管理页出现新的 manager-backend `CONNECTED` 连接。
6. 管理页展示：`SUPER_ADMIN` 能看到 Linux 服务器、后端进程、容器、manager、连接、opencode 进程和绑定状态；`APP_ADMIN` 和普通用户菜单不可见，直接访问 API 返回 `FORBIDDEN`。
7. 固定节点兼容：使用无用户主体的 static-token 兼容调用时，旧 `execution_nodes` fallback 仍可用于本地探测或旧集成。
8. 日志脱敏：后端日志、manager 日志和 opencode server 日志不出现 `Authorization`、token、Cookie 或完整 prompt。

## 构建镜像

```bash
docker build -f backend/Dockerfile -t test-agent-backend:local backend
```

该镜像使用 multi-stage build 构建 `test-agent-app` executable jar，最终运行层只包含 JRE、应用 jar 和非 root 用户。

## 本地脚本启动

本地开发和测试优先使用仓库根目录的未跟踪 dotenv 文件启动后端：

```bash
tools/dev-backend-run.sh
tools/dev-backend-run.sh --profile test
tools/dev-backend-run.sh --profile guo
```

脚本默认读取 `.env.local`，`--profile test` 读取 `.env.test`，`--profile guo` 读取 `.env.guo`，也可以通过 `--env-file <path>` 覆盖。脚本只解析 `KEY=VALUE` 行，不执行 dotenv 文件内容；生产容器仍通过外部环境变量或配置中心注入配置。

`tools/dev-backend-run.sh` 是本地启动后端的统一入口：默认读取仓库根目录未跟踪的 `.env.local` 并启动 `local` profile；传入 `--profile test` 时读取 `.env.test` 并启动 `test` profile，传入 `--profile guo` 时读取 `.env.guo` 并启动 `guo` profile。`.env.local`、`.env.test` 和 `.env.guo` 已被 `.gitignore` 排除，真实数据库密码只允许写入这些本机文件。

其他本地脚本：

```bash
tools/dev-local-up.sh            # 启用备用 Postgres；--redis 额外启动 Redis
tools/dev-health-check.sh --api
tools/dev-backend-check.sh
tools/verify-opencode-process-deployment.sh --backend-url http://127.0.0.1:8080
```

`deploy/local/docker-compose.yml` 默认启动备用 Postgres，映射到 `127.0.0.1:15432`；Redis 是可选 profile，默认映射到 `127.0.0.1:16379`。脚本只读取环境变量，不生成或写入密钥。

## dotenv 示例

`.env.local`（local profile）：

```dotenv
SPRING_PROFILES_ACTIVE=local
TEST_AGENT_LOCAL_DB_HOST=<dev-pg-host>
TEST_AGENT_LOCAL_DB_PORT=5432
TEST_AGENT_LOCAL_DB_NAME=<database>
TEST_AGENT_LOCAL_DB_USERNAME=<username>
TEST_AGENT_LOCAL_DB_PASSWORD=<password>
TEST_AGENT_REDIS_HOST=<redis-host>
TEST_AGENT_REDIS_PORT=6379
TEST_AGENT_REDIS_PASSWORD=<redis-password>
TEST_AGENT_OPENCODE_BASE_URL=http://127.0.0.1:4096
TEST_AGENT_MODEL_CATALOG_SOURCE=bailian
MODELSTUDIO_API_KEY=<bailian-api-key>
```

`.env.test`（test profile）：

```dotenv
SPRING_PROFILES_ACTIVE=test
TEST_AGENT_TEST_DB_HOST=<test-pg-host>
TEST_AGENT_TEST_DB_PORT=5432
TEST_AGENT_TEST_DB_NAME=<database>
TEST_AGENT_TEST_DB_USERNAME=<username>
TEST_AGENT_TEST_DB_PASSWORD=<password>
TEST_AGENT_REDIS_HOST=<redis-host>
TEST_AGENT_REDIS_PORT=6379
TEST_AGENT_REDIS_PASSWORD=<redis-password>
TEST_AGENT_OPENCODE_BASE_URL=http://127.0.0.1:4096
TEST_AGENT_MODEL_CATALOG_SOURCE=internal
ICBC_OPENAI_AUTH_TOKEN=<icbc-openai-token>
```

配置 `TEST_AGENT_API_TOKEN` 后，`/api/**` 要求 `Authorization: Bearer <token>`；未配置时本地默认放行。

本地 profile 默认允许主前端和 `frontend-opencode` 的 Vite dev/preview/real E2E origin。`guo` profile 同样支持通过 `TEST_AGENT_CORS_ALLOWED_ORIGINS` 覆盖；使用根目录 `restart-dev-services.sh` 并设置 `TEST_AGENT_FRONTEND_URL=http://<lan-ip>:3000` 时，脚本会把该局域网前端 origin 追加进 CORS 白名单。生产必须设置 `TEST_AGENT_CORS_ALLOWED_ORIGINS`，不要沿用本地端口白名单。

## 测试环境 profile

`test-agent-app` 提供 `test` profile 连接外部 PostgreSQL 测试库和外部 opencode server。真实主机、账号和密码必须通过环境变量注入，仓库内配置文件不保存密钥：

```bash
export SPRING_PROFILES_ACTIVE=test
export TEST_AGENT_TEST_DB_HOST=<test-pg-host>
export TEST_AGENT_TEST_DB_PORT=5432
export TEST_AGENT_TEST_DB_NAME=<database>
export TEST_AGENT_TEST_DB_USERNAME=<username>
export TEST_AGENT_TEST_DB_PASSWORD=<password>
export TEST_AGENT_OPENCODE_BASE_URL=http://<opencode-host>:4096
export TEST_AGENT_MODEL_CATALOG_SOURCE=internal
export ICBC_OPENAI_AUTH_TOKEN=<icbc-openai-token>
export TEST_AGENT_OPENCODE_MANAGER_TOKEN=<manager-control-token>
export TEST_AGENT_BACKEND_LISTEN_URL=http://<this-backend-ip>:8080
export TEST_AGENT_LINUX_SERVER_ID=<this-backend-ip>
```

启用该 profile 后，Spring Boot 通过 Druid 管理 JDBC 连接池，并使用 `test-agent-persistence` 中的 Flyway migration 初始化或校验数据库结构；Actuator `health` 包含数据库健康检查；Druid Web 控制台默认关闭，不提供 `/druid/*` 管理入口。

## 本地开发 opencode 机器预置

V17 migration（`backend/test-agent-persistence/src/main/resources/db/migration/V17__seed_local_opencode_machine_for_default_user.sql`）为 `local`/`test` profile 自动在数据库中种入：

- `linux_servers`：一条 `127.0.0.1` 的本机服务器，状态 `READY`。
- `opencode_containers`：`ctr_local_4096`，端口范围 `4096..4096`，容量 `1`，状态 `READY`。
- `opencode_container_managers`：`mgr_local_4096`，状态 `CONNECTED`。
- `opencode_server_processes`：`ocp_local_user_dev`，绑用户 `usr_test_dev`（默认开发用户 `888888888`），端口 `4096`，`base_url = http://127.0.0.1:4096`。
- `user_opencode_process_bindings`：`(usr_test_dev, opencode) -> ocp_local_user_dev`，状态 `ACTIVE`。

`opencode_manager_backend_connections` 的 `backend_process_id` 形如 `bjp_xxx`，是后端 Java 实例 ID；后端启动时由 `BackendJavaProcessLifecycleService.registerHeartbeat` 在为本实例写心跳时补齐 `(mgr_local_4096, bjp_xxx)` 这一行，状态 `CONNECTED`。该自举仅在 (manager, backend) 组合尚无连接行时插入；后续 manager WebSocket 真正连上后由 `ManagerControlApplicationService` 继续维护。

`local` profile 默认 `test-agent.opencode.manager-control.gateway-mode=local`（受 `TEST_AGENT_OPENCODE_GATEWAY_MODE` 覆盖），加载 `LocalOpencodeProcessManagerGateway`：

- `checkHealth` 直接对 `opencode_server_processes.baseUrl` 跑 HTTP GET，返回 2xx/3xx 视为健康，因此只要本机 127.0.0.1:4096 真的在跑 opencode server，前台用户进程状态即可落到 `READY`。
- `startProcess` 走占位返回 `pid=0, status=local-skip`，不实际拉起进程，假设本地手动启动的 127.0.0.1:4096 已就绪。

切换生产请把 `gateway-mode` 显式设回 `socket`（默认），加载 `SocketOpencodeProcessManagerGateway` 走 manager WebSocket；切回 `local` 仅作为没有 opencode-manager 容器时的开发态占位，不替代生产部署。

## 本地开发 opencode 短路模式

`local` / `guo`（开发常用 profile）默认启用 `test-agent.opencode.local-direct=true`（受 `TEST_AGENT_OPENCODE_LOCAL_DIRECT` 覆盖，默认 `true`）。该开关在 `UserOpencodeProcessAssignmentService` 的 `status` / `initialize` / `requireReadyProcess` 三个入口短路整个 topology / binding / health 校验链路，直接合成一个指向 `test-agent.opencode.local-direct-base-url`（默认 `http://127.0.0.1:4096`）的 READY 进程对象给前端。

行为说明：

- `status`：不查 `user_opencode_process_bindings`、不调用 `LocalOpencodeProcessManagerGateway.checkHealth`，返回 `本地开发模式：直连 http://127.0.0.1:4096`。
- `initialize`：不查容器、不调用 `gateway.startProcess`，直接返回上述合成响应。
- `requireReadyProcess`：不校验 binding / process 健康状态，直接返回 `node_ocp_local_direct` 的兼容 `ExecutionNode` 给 Run 启动链路。
- 合成进程对象走 `OpencodeServerProcess` 完整校验：host/port 从 baseUrl 解析，构造的 `linuxServerId=127.0.0.1, port=4096, baseUrl=http://127.0.0.1:4096`，因此能通过 V15 CHECK 约束（`base_url = 'http://' || linux_server_id || ':' || port`）。
- baseUrl 无法解析（空 / 非法）时回退到默认 `http://127.0.0.1:4096`，避免状态接口 500。

生产部署务必保持 `local-direct=false`（也是 Java 字段的默认值），避免跳过 topology 校验引入误判。如果生产环境临时无法访问 manager，可单独把 `gateway-mode` 切到 `local`（仍走实际 HTTP 探测），但 `local-direct` 只能保留 `false`。

## 连接池配置

连接池大小和借出校验可通过以下环境变量覆盖，默认值适合轻量测试和本地集成；远端 PostgreSQL 断开 idle 连接后，默认在借出连接时执行 `SELECT 1`，避免首个业务请求拿到 stale connection 后返回 500：

```bash
export TEST_AGENT_DB_POOL_INITIAL_SIZE=1
export TEST_AGENT_DB_POOL_MIN_IDLE=1
export TEST_AGENT_DB_POOL_MAX_ACTIVE=10
export TEST_AGENT_DB_POOL_MAX_WAIT_MILLIS=30000
export TEST_AGENT_DB_POOL_TEST_ON_BORROW=true
```

## 生产必填环境变量

```bash
SPRING_PROFILES_ACTIVE=prod
TEST_AGENT_DB_URL=jdbc:postgresql://<pg-host>:5432/<database>
TEST_AGENT_DB_USERNAME=<username>
TEST_AGENT_DB_PASSWORD=<password>
TEST_AGENT_API_TOKEN=<api-token>
TEST_AGENT_CORS_ALLOWED_ORIGINS=https://<frontend-origin>
TEST_AGENT_OPENCODE_BASE_URL=http://<opencode-host>:4096
TEST_AGENT_MODEL_CATALOG_SOURCE=internal
ICBC_OPENAI_AUTH_TOKEN=<icbc-openai-token>
TEST_AGENT_OPENCODE_MANAGER_TOKEN=<manager-control-token>
TEST_AGENT_BACKEND_LISTEN_URL=http://<this-backend-ip>:8080
TEST_AGENT_LINUX_SERVER_ID=<this-backend-ip>
```

可选运行参数：

```bash
TEST_AGENT_OPENCODE_NODE_ID=node_prod_opencode
TEST_AGENT_OPENCODE_MAX_RUNS=4
TEST_AGENT_OPENCODE_WEIGHT=100
TEST_AGENT_BACKEND_HEARTBEAT_INTERVAL=10s
TEST_AGENT_BACKEND_STALE_AFTER=30s
TEST_AGENT_OPENCODE_MANAGER_COMMAND_TIMEOUT=10s
TEST_AGENT_BACKEND_DISCOVERY_LIMIT=100
TEST_AGENT_DB_POOL_INITIAL_SIZE=1
TEST_AGENT_DB_POOL_MIN_IDLE=1
TEST_AGENT_DB_POOL_MAX_ACTIVE=10
TEST_AGENT_DB_POOL_MAX_WAIT_MILLIS=30000
TEST_AGENT_REDIS_ENABLED=false
TEST_AGENT_INTERNAL_DEFAULT_MODEL=DeepSeek-V4-Flash-W8A8
TEST_AGENT_ICBC_OPENAI_BASE_URL=http://ai-code.sdc.icbc:9070/icbc/jdt/model/api/openai/v1
```

Redis 只有在启用时才需要提供外部地址：

```bash
TEST_AGENT_REDIS_ENABLED=true
TEST_AGENT_REDIS_HOST=<redis-host>
TEST_AGENT_REDIS_PORT=6379
TEST_AGENT_RUN_EVENT_REDIS_BUS_ENABLED=true
TEST_AGENT_RUN_EVENT_REDIS_BUS_CHANNEL=test-agent:run-events
TEST_AGENT_SCHEDULER_ENABLED=false
TEST_AGENT_SCHEDULER_SCAN_INTERVAL=30s
TEST_AGENT_SCHEDULER_DUE_TASK_LIMIT=50
TEST_AGENT_SCHEDULER_MANUAL_RUN_LIMIT=50
```

`TEST_AGENT_RUN_EVENT_REDIS_BUS_ENABLED` 只控制 RunEvent 跨实例实时 fan-out；数据库 `run_events` replay、`Last-Event-ID` 和 `session_messages` 快照仍是恢复基线。Redis 不可用或该开关为 `false` 时，后端自动退回本机 live bus + DB replay。

`TEST_AGENT_SCHEDULER_ENABLED` 默认 `false`。启用 scheduler 后必须同时设置 `TEST_AGENT_REDIS_ENABLED=true` 并提供可用 Redis；scheduler 使用 Redis `SET NX PX` + Lua token 校验作为唯一分布式互斥实现，不降级为本机锁或数据库锁。首版只提供框架和超级管理员管理 API，不内置具体业务任务。

## 运行示例

```bash
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e TEST_AGENT_DB_URL=jdbc:postgresql://pg.example.internal:5432/test_agent \
  -e TEST_AGENT_DB_USERNAME=test_agent \
  -e TEST_AGENT_DB_PASSWORD=change-me \
  -e TEST_AGENT_API_TOKEN=change-me \
  -e TEST_AGENT_CORS_ALLOWED_ORIGINS=https://agent.example.com \
  -e TEST_AGENT_OPENCODE_BASE_URL=http://opencode.example.internal:4096 \
  -e TEST_AGENT_MODEL_CATALOG_SOURCE=internal \
  -e ICBC_OPENAI_AUTH_TOKEN=change-me \
  -e TEST_AGENT_OPENCODE_MANAGER_TOKEN=change-me-manager-token \
  -e TEST_AGENT_BACKEND_LISTEN_URL=http://10.8.0.21:8080 \
  -e TEST_AGENT_LINUX_SERVER_ID=10.8.0.21 \
  test-agent-backend:local
```

启动后检查：

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
```

`DatabaseMigrationRunner` 会在启动时执行 Flyway migration；`ExecutionNodeSeeder` 会把配置中的 opencode node 写入 `execution_nodes` 作为 Run 路由来源。启用 `TEST_AGENT_MODEL_CATALOG_SOURCE=internal` 时，`ModelCatalogApplicationService` 会把企业内模型清单 seed 到 `ai_model_configs`，后续可通过改表控制模型显示、启停和默认值。
启用 `TEST_AGENT_SCHEDULER_ENABLED=true` 时，`ScheduledTaskRegistry` 会同步代码注册任务，`ScheduledTaskRunner` 后台线程会扫描 due task 和管理员手动触发 pending run。

## 模型目录配置

| 变量 | 默认值 | 说明 |
|---|---|---|
| `TEST_AGENT_MODEL_CATALOG_SOURCE` | local: `bailian`；test/prod: `internal` | 模型目录来源。`opencode` 保持旧代理，`bailian` 直连百炼 `/models`，`internal` 从数据库读取企业内模型。 |
| `TEST_AGENT_BAILIAN_BASE_URL` | `https://coding.dashscope.aliyuncs.com/v1` | 外网百炼 OpenAI-compatible base URL。 |
| `TEST_AGENT_BAILIAN_API_KEY_ENV` | `MODELSTUDIO_API_KEY` | 外网百炼密钥所在环境变量名。 |
| `TEST_AGENT_BAILIAN_DEFAULT_MODEL` | `qwen3.5-plus` | 外网模式同步给 opencode 的默认模型。 |
| `TEST_AGENT_ICBC_OPENAI_BASE_URL` | `http://ai-code.sdc.icbc:9070/icbc/jdt/model/api/openai/v1` | 企业内 OpenAI-compatible base URL，与 openclaw 企业 patch 保持一致。 |
| `TEST_AGENT_ICBC_OPENAI_TOKEN_ENV` | `ICBC_OPENAI_AUTH_TOKEN` | 企业内 token 所在环境变量名。 |
| `TEST_AGENT_ICBC_OPENAI_AUTH_MODE` | `auth-token` | 企业内调用鉴权头模式，默认写入 `Auth-Token`。 |
| `TEST_AGENT_INTERNAL_DEFAULT_MODEL` | `DeepSeek-V4-Flash-W8A8` | 企业内默认模型，前端模型切换会优先选中该模型。 |
`DatabaseMigrationRunner` 会在启动时执行 Flyway migration；`ExecutionNodeSeeder` 会把配置中的 opencode node 写入 `execution_nodes` 作为兼容 Run 路由来源。启用用户进程模型后，`BackendJavaProcessLifecycleRunner` 会在启动和心跳时写入 `linux_servers`、`backend_java_processes`，`opencode-manager` WebSocket 注册和心跳会写入 `opencode_containers`、`opencode_container_managers` 和 `opencode_manager_backend_connections`。
