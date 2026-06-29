# 后端 Docker 部署说明

## 部署边界

生产和研发测试环境只将 `test-agent-app` 后端 Java 进程放入 Docker 容器。PostgreSQL、Redis 和 opencode server 都是外部服务，通过环境变量或配置中心注入地址和凭据；后端镜像不包含也不启动这些依赖。

研发测试环境的 PG/PostgreSQL 数据库由远端环境启动和维护，不在后端容器或本仓库 Docker Compose 中启动；后端只通过 `TEST_AGENT_TEST_DB_*` 或生产 `TEST_AGENT_DB_*` 配置连接该远端数据库。

`deploy/local/docker-compose.yml` 只作为个人离线开发备用入口，不能作为研发测试或生产部署拓扑。

## opencode-manager 容器进程管理

用户专属 opencode server 进程由每个 opencode 容器内的 `opencode-manager` 管理。`opencode-manager` 是与 `backend/` 平级的 Go 单二进制工程，不打包进后端 Java 镜像；它既提供本地 CLI，也提供 `run` 长运行模式，通过 WebSocket JSON 控制面连接所有 READY 后端 Java 实例。

容器内必须挂载以下目录：

```text
/data/.testagent/agent-opencode/.session/            # 用户进程 XDG_DATA_HOME 根目录，按端口分目录
/data/.testagent/agent-opencode/.config/opencode/    # 公共 agent、插件、skill 等配置
/data/.testagent/agent-opencode/.config/             # 公共 Agent Git 仓库根目录，由 OPENCODE_PUBLIC_CONFIG_GIT_ROOT 控制
/data/.testagent/agent-opencode/.configdev/          # 公共 Agent Git worktree 根目录
/data/.testagent/agent-opencode/workspace/           # 应用版本工作区和个人 worktree 根目录
/data/.testagent/agent-opencode/manager              # manager 本地 state 和日志
```

容器环境变量示例：

opencode-manager 环境变量只用于启动前必须由宿主环境提供的身份、端口池、token、二进制路径、状态目录或连接引导参数。不要为运行期业务配置随意新增 `OPENCODE_*` 环境变量；用户进程 session/config/maxProcesses 等运行配置必须优先通过 Java 后端 `common_parameters` 和控制面 `configUpdate` 下发。确需新增 manager 环境变量时，必须同步更新 opencode-manager README、本文档、配置解析测试和本地启动脚本/示例。

```dotenv
OPENCODE_MANAGER_CONTAINER_ID=ctr_01
OPENCODE_MANAGER_SERVER_IP_FILE=/data/.testagent/.serverip
OPENCODE_MANAGER_PORT_START=4096
OPENCODE_MANAGER_PORT_END=4100
OPENCODE_MANAGER_ID=mgr_1234567890abcdef
OPENCODE_MANAGER_BACKEND_PORT=8080
OPENCODE_MANAGER_TOKEN=<manager-control-token>
OPENCODE_BIN=opencode
OPENCODE_MANAGER_STATE_DIR=/data/.testagent/agent-opencode/manager
```

`OPENCODE_MANAGER_CONTAINER_ID` 仅作为非 Windows 下的最后兜底值；生产容器应优先设置容器 hostname，manager 会先读系统 hostname，再读 `/etc/hostname`，最后才读该环境变量。

长运行模式启动：

```bash
opencode-manager run
```

`run` 不再通过 HTTP discovery 与 Java 后端交互。manager 会先用 `OPENCODE_MANAGER_SERVER_IP_FILE` 解析出的服务器 IPv4 和 `OPENCODE_MANAGER_BACKEND_PORT`（默认 `8080`）派生 seed WebSocket：`ws://{serverIp}:{port}/api/internal/platform/opencode-runtime/manager/ws`，并用 `Authorization: Bearer <OPENCODE_MANAGER_TOKEN>` 建立控制连接。后端扩容后，manager 每 10 秒通过任一已连接 socket 发送 `backendListRequest`，Java 从 Redis 当前存活后端快照返回 `backendListResponse`，manager 自动补连尚未连接的后端实例；所有 socket 断开时，manager 每 10 秒按 seed 地址无限重连。

非 Windows 环境下，manager 启动时不再探测容器网卡 IP，也不再依赖 `OPENCODE_MANAGER_LINUX_SERVER_ID`。Java 后端先把当前服务器 IPv4 写入 `/data/.testagent/.serverip`（可用 `TEST_AGENT_SERVER_IP_FILE` / `test-agent.opencode.manager-control.server-ip-file` 覆盖），manager 读取同一路径；文件不存在时每 1 秒重试，最多 30 秒。文件内容必须是单行 IPv4，非法内容或超时会让 manager 安全失败。Windows 本机开发态跳过 `.serverip` 等待，直接使用本机非回环 IPv4，并用机器名作为容器标识。

启动单个用户进程时，manager 会执行：

```bash
XDG_DATA_HOME=/data/.testagent/agent-opencode/.session/{port} \
OPENCODE_CONFIG_DIR=/data/.testagent/agent-opencode/.config/opencode/ \
opencode serve --hostname 0.0.0.0 --port {port} --print-logs
```

`XDG_DATA_HOME` 和 `OPENCODE_CONFIG_DIR` 的真实值不再由 manager 环境变量传入，而是 manager 通过 WebSocket `configRequest` 从 Java 后端获取通用参数：`OPENCODE_SESSION_DIR`、`OPENCODE_PUBLIC_CONFIG_DIR`。最大进程数同样来自 `OPENCODE_MANAGER_MAX_PROCESSES`（`platform=all`）。收到完整 `configUpdate` 前，manager 会拒绝 `start`/`restart` 命令，不会用容器内默认路径启动用户进程；成功应用 `configUpdate` 后，manager 会立即补发 heartbeat，把端口池裁剪后的生效容量写入运行管理 Redis 快照。通用参数值不会经过 shell；`$NAME` 直接按 Java 后端进程环境变量展开，`${NAME}` 先按通用参数引用解析、未命中时再按环境变量展开，路径开头的 `$HOME` 和 `~/` 会解析为当前用户主目录后再下发给 manager。`OPENCODE_PUBLIC_CONFIG_DIR` 的存在性和非空检查发生在目标 manager 执行 `start` 时，检查的是目标 opencode server 所在服务器的实际文件系统。

opencode server 默认不设置 `OPENCODE_SERVER_PASSWORD`，后端仍按 `http://{linuxServerIp}:{port}` 访问。生产部署必须通过容器网络、主机防火墙或网关限制端口池访问面，不得把用户进程端口暴露到不可信网络。

后端创建用户进程、应用版本工作区和个人 worktree 时读取数据库 `common_parameters` 中当前平台的 opencode 路径参数：`OPENCODE_SESSION_DIR`、`OPENCODE_PUBLIC_CONFIG_DIR`、`OPENCODE_APP_WORKSPACE_ROOT`、`OPENCODE_PERSONAL_WORKTREE_ROOT`。`common_parameters` 为唯一事实源，缺失或值为空时抛 `INTERNAL_ERROR` 业务异常，不在 yaml 或代码常量预留 fallback；Windows 默认值在迁移中按 `D:/data/.testagent/agent-opencode/...` 初始化。macOS/Linux 本地开发可把路径写为 `$HOME/.testagent/...` 或 `$TEST_AGENT_ROOT/...`，加载后的 `resolvedValue` 会变为实际用户目录或环境变量值。真实创建用户进程时，后端先按健康容器和空闲端口选择目标容器，再向该容器对应 manager 下发 `start`；manager 使用已通过 `configUpdate` 同步的 `OPENCODE_PUBLIC_CONFIG_DIR`，并在所在服务器检查该目录必须存在且非空。缺失、为空、非目录或不可读时返回 `OPENCODE_UNAVAILABLE`，错误消息包含目标服务器和 manager 实际检查目录，并提示联系超级管理员进入“系统管理 → 配置管理 → opencode公共配置管理”完成初始化；不会创建 session、不会启动 opencode server。

公共 Agent/Skill 配置额外读取 `OPENCODE_PUBLIC_AGENT_GIT_URL`、`OPENCODE_PUBLIC_CONFIG_GIT_ROOT`、`OPENCODE_PUBLIC_CONFIG_DIR`、`OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT`。Git 地址默认为 `UNCONFIGURED`，未配置前公共 Agent 只读 status 可用，更新、worktree、commit、publish 均被禁用。公共配置 Git 仓库按服务器本地盘初始化到 `OPENCODE_PUBLIC_CONFIG_GIT_ROOT`，初始化完成后必须校验 `OPENCODE_PUBLIC_CONFIG_DIR` 指向的 opencode 配置目录存在且非空；公共配置文件树根为 `{OPENCODE_PUBLIC_CONFIG_GIT_ROOT}/opencode/`，其中 `agents/` 放 opencode agent Markdown，`skills/<skill-name>/` 直接放各自包含 `SKILL.md` 的实际技能包，不增加中间包装目录或符号链接。公共仓库有未提交修改时仍视为已初始化并允许浏览，但状态为 `CONFLICT`；更新默认拒绝覆盖，只有超级管理员明确确认后才恢复已跟踪文件再拉取，未跟踪文件不删除。公共 worktree 由管理员在前端显式选择一台已初始化服务器后创建，目录在该服务器 `{OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT}/{worktreeName-yyyymmdd}` 下创建，创建成功后记录 `worktreeId -> linuxServerId`，后续公共 Agent/Skill 文件、diff、stage、commit、publish 都由当前后端代理到该服务器执行，浏览器不直连目标后端。

启用用户进程模型后，已登录用户的 Run 和 opencode runtime 代理都会优先使用当前用户绑定的 `READY` 进程；用户未初始化或健康检测失败时返回平台 `OPENCODE_UNAVAILABLE`，由前端提示初始化。无用户主体的 static-token 兼容调用仍可使用配置 seed 写入的固定 `execution_nodes`，用于旧集成或本地探测。Session 级 runtime 代理发现绑定节点不是当前用户进程节点时，会在当前进程上创建新的远端 session 并覆盖绑定，不会删除旧远端 session。

## 多服务器用户进程拓扑规划

一个生产集群由以下角色组成：

| 角色 | 部署数量 | 关键配置 | 说明 |
|---|---:|---|---|
| 后端 Java 实例 | 每台 Linux 服务器 1 个或按容量水平扩展 | `TEST_AGENT_BACKEND_LISTEN_URL`、`TEST_AGENT_SERVER_IP_FILE`、`TEST_AGENT_OPENCODE_MANAGER_TOKEN` | `listen-url` 必须是 manager 可直连的实例地址；非回环 IPv4 会作为服务器身份并写入 `.serverip`。 |
| opencode 容器 | 每台 Linux 服务器多个 | 容器 hostname、`OPENCODE_MANAGER_SERVER_IP_FILE`、`OPENCODE_MANAGER_CONTAINER_ID` 兜底值、端口池、挂载目录 | 每个容器运行 1 个 `opencode-manager run`；`containerId` 标识容器，非 Windows 先取系统 hostname，再取 `/etc/hostname`，最后才取 `OPENCODE_MANAGER_CONTAINER_ID`，`linuxServerId` 来自 `.serverip`。 |
| 用户 opencode server 进程 | 每个用户 1 个当前绑定 | 由 manager 按端口启动 | `baseUrl` 固定为 `http://{linuxServerIp}:{port}`，session 持久化在对应 Linux 服务器。 |
| 前端访问入口 | 1 个负载均衡域名 | `VITE_TEST_AGENT_API_BASE_URL` | 浏览器只访问平台后端，不直连 opencode server 或 manager。 |

后端实例之间不需要直接 HTTP 互连；每个 manager 只通过 WebSocket 控制面与 Java 通信。后端扩容时，新实例启动后每 5 秒把 Java 后端快照写入 Redis，已连接 manager 下一轮 `backendListRequest` 会收到新实例的 `webSocketUrl` 并自动连接。应用版本工作区副本的实时同步通过共享 Redis pub/sub 广播触发，所有后端需要连接同一个 Redis，并显式开启 `TEST_AGENT_SERVER_BROADCAST_ENABLED=true`（对应配置 `test-agent.server-broadcast.enabled=true`）；未开启时退化为单机 Noop 广播，只依赖本机副本记录和补偿扫描。

端口池规划必须满足：

- 同一 Linux 服务器上所有 opencode 容器的主机可访问端口范围不能重叠，数据库约束 `opencode_server_processes(linux_server_id, port)` 会拒绝同一服务器端口重复。
- `common_parameters.OPENCODE_MANAGER_MAX_PROCESSES` 不得超过容器端口池容量；超过时 manager 会按 `OPENCODE_MANAGER_PORT_END - OPENCODE_MANAGER_PORT_START + 1` 裁剪，并通过即时 heartbeat 回报裁剪后的生效容量。
- 建议每个容器预留 1 到 2 个端口作为故障排查或滚动扩容缓冲，不要把端口池全部按理论最大值打满。
- `.serverip` 固定语义是“当前服务器 IPv4”，不是容器 IP，不承载 token、URL 或 JSON；用户进程 `baseUrl` 和同服务器重建规则都使用该 IPv4。
- 本地或测试环境执行 `./restart-dev-services.sh` 时，脚本默认把 Java 和 manager 的 server IP 文件都指向 `.tmp/dev-services/.serverip`，避免 mac/Linux 本地写 `/data` 权限问题；未显式配置 `TEST_AGENT_BACKEND_LISTEN_URL` 时，会使用默认路由网卡 IPv4 补全后端直连地址。

目录和日志规划：

| 路径 | 所属节点 | 用途 | 运维要求 |
|---|---|---|---|
| `/data/.testagent/agent-opencode/.session/{port}` | Linux 服务器本地盘并挂载到容器 | 用户进程 `XDG_DATA_HOME` | 不能跨 Linux 服务器共享；备份/清理必须按端口和用户绑定关系执行。 |
| `/data/.testagent/agent-opencode/.config/opencode/` | Linux 服务器本地盘并挂载到容器 | 公共 agent、插件、skill 配置 | 多容器共享只读或受控写入；变更前先备份。 |
| `/data/.testagent/agent-opencode/.config/` | Linux 服务器本地盘 | 公共 Agent 配置 Git 根目录 | 由 `OPENCODE_PUBLIC_CONFIG_GIT_ROOT` 控制；每台在线后端所在服务器需要在系统管理中初始化，Git origin 必须与参数一致；工作树有修改时更新需显式确认恢复。 |
| `/data/.testagent/agent-opencode/.configdev/` | Linux 服务器本地盘 | 公共 Agent 配置 Git worktree 根目录 | 由 `OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT` 控制；worktree 记录服务器归属，发布时由当前后端代理到目标服务器，先 merge 回公共配置当前分支再 push。 |
| `/data/.testagent/agent-opencode/workspace/appworkspace/` | Linux 服务器本地盘 | 应用版本工作区根目录 | 默认由 `common_parameters.OPENCODE_APP_WORKSPACE_ROOT` 控制；目录片段为版本 + 代码库英文名。 |
| `/data/.testagent/agent-opencode/workspace/personalworktree/` | Linux 服务器本地盘 | 个人 git worktree 根目录 | 默认由 `common_parameters.OPENCODE_PERSONAL_WORKTREE_ROOT` 控制；目录片段包含版本、统一认证号、代码库英文名和个人空间 ID。 |
| `/data/.testagent/agent-opencode/manager/processes/{port}.json` | 容器挂载目录 | manager 本地进程状态 | 用于 stop/list/restart；容器重启后继续识别已有 state。 |
| `/data/.testagent/agent-opencode/manager/logs/{port}.log` | 容器挂载目录 | opencode server stdout/stderr | 日志不得输出 token、Authorization、Cookie 或完整 prompt。 |

容量与心跳参数建议：

| 参数 | 默认/建议 | 说明 |
|---|---|---|
| `TEST_AGENT_REDIS_HOST` / `TEST_AGENT_REDIS_PORT` / `TEST_AGENT_REDIS_PASSWORD` | 部署 Redis 地址 | Redis 是系统必需依赖；用户进程运行管理、manager 控制面、Token 存储和 scheduler 均使用同一 Redis。 |
| `TEST_AGENT_BACKEND_HEARTBEAT_INTERVAL` | `5s` | 后端实例写入 Redis Java 快照的间隔。 |
| `TEST_AGENT_BACKEND_STALE_AFTER` | `10s` | Java/manager Redis 快照 TTL；不再作为数据库心跳回退窗口使用。 |
| `TEST_AGENT_BACKEND_DISCOVERY_LIMIT` | `100` | `backendListResponse` 和兼容诊断端点返回后端实例上限。 |
| `TEST_AGENT_OPENCODE_MANAGER_COMMAND_TIMEOUT` | `10s` | 后端等待 manager 命令结果的超时。 |
| `TEST_AGENT_SERVER_BROADCAST_ENABLED` | `false` / 多机 `true` | 开启 Redis 服务器广播，应用版本创建、同步和 git pull 后广播目标 commit 给其他后端；公共 Agent 配置长操作也用它广播安全进度字段。 |
| `TEST_AGENT_SERVER_BROADCAST_CHANNEL` | `test-agent:server-broadcast` | 服务器广播 Redis channel；同一集群必须一致。 |
| `TEST_AGENT_MANAGED_WORKSPACE_REPLICA_RECONCILER_ENABLED` | `true` | 启用应用版本工作区本机副本补偿扫描，补齐漏消息或落后 commit。 |
| `TEST_AGENT_MANAGED_WORKSPACE_REPLICA_RECONCILER_INTERVAL` | `60s` | 副本补偿扫描间隔，最小按 10 秒执行。 |
| `OPENCODE_MANAGER_DISCOVERY_INTERVAL` | `10s` | manager 通过已连接 socket 发送 `backendListRequest` 的间隔。 |
| `OPENCODE_MANAGER_HEARTBEAT_INTERVAL` | `5s` | manager 通过任一已连接 socket 发送 `managerHeartbeat` 的间隔；心跳同时携带容器 CPU、内存、磁盘 IO 和本地 opencode 进程明细。 |
| `OPENCODE_MANAGER_RECONNECT_INTERVAL` | `10s` | 所有 socket 断开后重连 seed WebSocket 的间隔，不设总超时。 |

## 扩容、故障处理与回滚

后端 Java 扩容流程：

1. 为新实例配置唯一的 `TEST_AGENT_BACKEND_LISTEN_URL=http://<backend-ip>:<port>`；如果该地址是非回环 IPv4，后端会优先使用它作为服务器身份并写入 `.serverip`。
2. 使用同一个 `TEST_AGENT_OPENCODE_MANAGER_TOKEN`，并确认该地址和 `TEST_AGENT_SERVER_IP_FILE` 所在挂载路径可从同服务器 opencode 容器访问。
3. 启动新后端，检查 `/actuator/health` 返回 `UP`。
4. 等待 5 到 10 秒，确认超级管理员运行管理页能看到新的 Java 后端 Redis 快照。
5. 已连接 manager 会在下一次 `backendListRequest` 后自动补连新实例；运行管理页中对应 manager-backend 连接应出现并为 `CONNECTED`，点击后端 Java 进程可看到 Redis 中保留的近 48 小时服务器/JVM 监控趋势。

opencode 容器扩容流程：

1. 在同一 Linux 服务器上分配不与既有容器重叠的端口池。
2. 按上文挂载 `/data/.testagent/agent-opencode/.session/`、`/data/.testagent/agent-opencode/.config/opencode/`、`/data/.testagent/agent-opencode/workspace/` 和 `/data/.testagent/agent-opencode/manager`。
3. 配置新的容器 hostname、`OPENCODE_MANAGER_ID`、`OPENCODE_MANAGER_SERVER_IP_FILE` 和端口池环境变量；`OPENCODE_MANAGER_CONTAINER_ID` 仅作为非 Windows 最后兜底。非 Windows 解析顺序固定为系统 hostname、`/etc/hostname`、`OPENCODE_MANAGER_CONTAINER_ID`；Windows 直接使用机器名。
4. 启动 `opencode-manager run`，检查运行管理页中 `containers`、`managers` 和 `managerBackendConnections` 均出现对应记录，容器行展示最新 CPU、内存和已用内存。

常见故障处理：

| 现象 | 排查顺序 | 处理 |
|---|---|---|
| 用户初始化返回 `OPENCODE_UNAVAILABLE` | 运行管理页查看是否有 Redis 在线的 `READY` 容器和 `CONNECTED` manager；检查 Redis、manager WebSocket 连接和 `managerHeartbeat` | 恢复 Redis/manager WebSocket 连接或启动有空余端口的容器。 |
| 用户初始化返回 `OPENCODE_UNAVAILABLE` 且提示公共配置尚未初始化 | 先读取错误消息中的目标服务器和公共配置目录，再进入“系统管理 → 配置管理 → opencode公共配置管理”确认该服务器状态；必要时结合运行管理页或日志确认目标容器/manager，检查错误消息中 manager 实际检查的目录是否存在且非空；确认公共配置 Git 根目录已经 clone/pull 并包含 `opencode/` 配置内容 | 由超级管理员在目标服务器初始化公共配置目录后重试；不要在空目录状态下启动用户进程。 |
| 创建公共 Agent worktree 返回 `CONFLICT` 且提示“公共配置仓库未初始化” | 在系统管理 > 配置管理 > opencode公共配置管理中查看对应 `linuxServerId` 的 `OPENCODE_PUBLIC_CONFIG_GIT_ROOT`、`OPENCODE_PUBLIC_CONFIG_DIR` 和状态；确认当前管理员 SSH key 有公共配置仓库读取权限 | 对该服务器执行初始化；不要在创建 worktree 时手工拷贝半初始化目录。 |
| 用户初始化返回 `OPENCODE_TIMEOUT` | 查看 `{stateDir}/logs/{port}.log`、后端命令超时配置、opencode CLI 是否卡住 | 先保留日志，再 stop/restart 目标端口或扩容新容器。 |
| 用户初始化返回 `OPENCODE_BAD_GATEWAY` 且包含 `already managed but unhealthy` | 目标端口已有 manager 本地 state，但 PID 或 HTTP 健康检查失败 | 先查看 `{stateDir}/processes/{port}.json` 和 `{stateDir}/logs/{port}.log`；确认无业务流量后用 manager `restart` 或 `stop` 清理该端口。健康的已托管端口会被幂等复用，不会再因 `already managed` 初始化失败。 |
| 进程健康异常后没有同服务器重建 | 检查原 `linuxServerId` 下是否还有 `READY` 且有容量的容器 | 在同一 Linux 服务器上恢复或扩容容器；不要把该用户迁移到其他服务器，否则 session 目录不可用。 |
| 后端扩容后 manager 未连接新实例 | 检查新后端是否写入 Redis Java 快照；检查 `listenUrl/webSocketUrl` 是否是容器可达直连地址；检查 manager token | 修正 Redis、`TEST_AGENT_BACKEND_LISTEN_URL` 或 token 后等待下一轮 `backendListRequest`。 |
| 管理页看不到数据 | 确认登录用户有 `SUPER_ADMIN`；检查 Redis 是否启用可用；检查 Java/manager Redis 心跳快照 | 非超管前端菜单隐藏且后端返回 `FORBIDDEN` 是预期行为。 |

回滚策略：

- V10 opencode 用户进程管理表是新增表，不修改旧 `execution_nodes`、`sessions.opencode_*` 或 `agent_session_bindings` 的兼容字段；数据库结构可以保留，不需要在应用回滚时删除。
- 无用户主体的 static-token 兼容调用仍可走固定 `execution_nodes`；已登录 Web 用户在当前版本会优先要求用户专属进程。如果要把 Web 对话完整回退到固定节点模式，应回滚后端和前端镜像到引入用户进程模型之前的版本。
- 回滚前不要清理 `/data/.testagent/agent-opencode/.session/{port}`，否则再恢复用户进程模型时会丢失远端 session 状态。
- 回滚后若继续保留 manager 容器，应停止 `opencode-manager run` 或撤销 manager token，避免旧版本无法识别的控制面连接持续重试。

## 真实环境验收

只读 smoke check：

```bash
tools/verify-opencode-process-deployment.sh \
  --backend-url http://<backend-or-lb>:8080 \
  --manager-token <manager-control-token> \
  --auth-token <super-admin-user-jwt>
```

该脚本只检查 `/actuator/health`、manager 兼容诊断端点和超级管理员 overview，不会启动、停止、重启或健康检测用户进程。未提供 token 时对应高权限接口会被跳过；生产验收建议传入两个 token，并在 shell history 策略中避免保存真实值。

手工验收清单：

1. 首次登录：确认系统管理 > 配置管理 > opencode公共配置管理中每台计划承载公共 Agent worktree 的服务器均已初始化；同时确认每个目标 manager 所在服务器上 `OPENCODE_PUBLIC_CONFIG_DIR` 解析后的公共配置目录存在且非空；普通用户进入工作台后进程状态为 `NEEDS_INITIALIZATION`，点击初始化后创建 1 条 `user_opencode_process_bindings` 当前绑定和 1 条 `opencode_server_processes` 记录。
2. 复用绑定：同一用户退出再登录，状态为 `READY`，`processId/linuxServerId/port/baseUrl` 不变化。
3. Run 防绕过：用户未初始化或进程不可用时，前端禁用发送；直接调用 Run API 返回 `OPENCODE_UNAVAILABLE`，不创建本地 Run。
4. 原服务器重建：停止当前用户进程或让 health 失败后重新初始化，新的进程仍位于原 `linuxServerId` 下的可用容器。
5. 后端扩容：新增后端实例后，Redis 出现新 Java 快照，manager 下一次 `backendListRequest` 自动补连，运行管理页出现新的 manager-backend `CONNECTED` 连接。
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

`tools/dev-backend-run.sh` 是本地启动后端的统一入口：默认读取仓库根目录未跟踪的 `.env.local` 并启动 `local` profile；传入 `--profile test` 时读取 `.env.test` 并启动 `test` profile，传入 `--profile guo` 时读取 `.env.guo` 并启动 `guo` profile。脚本会清空后端 JVM 的 HTTP/HTTPS/FTP/SOCKS 代理系统属性，避免 macOS 或本机代理影响 JDBC 与 Redis 直连；需要代理的外部 HTTP 调用应在应用配置层显式处理。`.env.local`、`.env.test` 和 `.env.guo` 已被 `.gitignore` 排除，真实数据库密码只允许写入这些本机文件。

其他本地脚本：

```bash
tools/dev-local-up.sh            # 启用备用 Postgres；--redis 额外启动 Redis
tools/dev-health-check.sh --api
tools/dev-backend-check.sh
tools/verify-opencode-process-deployment.sh --backend-url http://127.0.0.1:8080
```

`deploy/local/docker-compose.yml` 默认启动备用 Postgres，映射到 `127.0.0.1:15432`；Redis 是可选 profile，默认映射到 `127.0.0.1:16379`。脚本只读取环境变量，不生成或写入密钥。

仓库根目录的 `restart-dev-services.sh` 是 macOS/Linux/WSL/Git Bash 三服务一键重启入口，Windows PowerShell 使用同级 `restart-dev-services.ps1`：二者默认读取 `.env.test` 并以 `test` profile 启动，按「后端 → opencode-manager → 前端」的依赖顺序，**逐个先 kill 原进程再启动**。脚本启动后端 Java 进程时同样清空 JVM 代理系统属性，确保测试库和 Redis 使用直连网络。当 `TEST_AGENT_OPENCODE_BASE_URL` 指向 loopback 或默认路由网卡探测到的本机 IPv4 时，脚本默认启动 Go `opencode-manager`（`run` 长运行模式），不再单独启动 standalone `opencode serve`——用户进程由 manager 自行派生，避免 4096 端口冲突。停止 manager 时，脚本会读取 `.tmp/dev-services/opencode-manager-state/processes/*.json` 中的 pid，并扫描端口池 `4096..4105` 内的 `opencode serve --port ...` 监听，统一停止残留用户进程后删除 state JSON，避免重启后旧进程或旧 state 导致端口被判定为已托管。脚本会把 `TEST_AGENT_SERVER_IP_FILE` 和 `OPENCODE_MANAGER_SERVER_IP_FILE` 默认指向 `.tmp/dev-services/.serverip`，后端先写入服务器 IPv4，manager 再读取同一路径；不再注入 `OPENCODE_MANAGER_LINUX_SERVER_ID`。manager 与后端共享的 `TEST_AGENT_OPENCODE_MANAGER_TOKEN` 未设置时默认 `local-manager-token`（与 `application-guo.yml` 一致），本地无需手配 manager token；设 `TEST_AGENT_START_OPENCODE_MANAGER=false` 可跳过 manager。需要使用本地离线或个人调试配置时，Bash 显式传入 `--profile local --env-file .env.local` 或 `--profile guo --env-file .env.guo`，PowerShell 对应传入 `-Profile local -EnvFile .env.local` 或 `-Profile guo -EnvFile .env.guo`。

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

本地和测试 profile 默认允许主前端和 `frontend-opencode` 的 Vite dev/preview/real E2E origin。`guo` profile 同样支持通过 `TEST_AGENT_CORS_ALLOWED_ORIGINS` 覆盖；使用根目录 `restart-dev-services.sh` 并设置 `TEST_AGENT_FRONTEND_URL=http://<lan-ip>:3000` 时，脚本会把该局域网前端 origin 追加进 CORS 白名单。生产必须设置 `TEST_AGENT_CORS_ALLOWED_ORIGINS`，不要沿用本地端口白名单。

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
export TEST_AGENT_SERVER_IP_FILE=/data/.testagent/.serverip
```

启用该 profile 后，Spring Boot 通过 Druid 管理 JDBC 连接池，并使用 `test-agent-persistence` 中的 Flyway migration 初始化或校验数据库结构；Actuator `health` 包含数据库健康检查；Druid Web 控制台默认关闭，不提供 `/druid/*` 管理入口。

## 本地开发 opencode 机器预置

V17 migration（`backend/test-agent-persistence/src/main/resources/db/migration/V17__seed_local_opencode_machine_for_default_user.sql`）为 `local`/`test` profile 自动在数据库中种入：

- `linux_servers`：一条 `127.0.0.1` 的本机服务器，状态 `READY`。
- `opencode_containers`：`ctr_local_4096`，端口范围 `4096..4096`，容量 `1`，状态 `READY`。
- `opencode_container_managers`：`mgr_local_4096`，状态 `CONNECTED`。
- `opencode_server_processes`：`ocp_local_user_dev`，绑用户 `usr_test_dev`（默认开发用户 `888888888`），端口 `4096`，`base_url = http://127.0.0.1:4096`。
- `user_opencode_process_bindings`：`(usr_test_dev, opencode) -> ocp_local_user_dev`，状态 `ACTIVE`。

`opencode_manager_backend_connections` 的 `backend_process_id` 形如 `bjp_xxx`，是后端 Java 实例 ID；后端启动时由 `BackendJavaProcessLifecycleService.registerHeartbeat` 在拓扑落库阶段补齐 `(mgr_local_4096, bjp_xxx)` 这一行，状态 `CONNECTED`。该自举仅在 (manager, backend) 组合尚无连接行时插入；真实 manager WebSocket 连上后由 `ManagerControlApplicationService.register` 维护持久连接行，在线连接视图由 Redis manager 快照表达。

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

后端环境变量只用于部署期密钥、外部端点、进程身份、启动引导路径或资源容量。不要为了临时绕过配置或适配个人环境而随意新增 `TEST_AGENT_*` 环境变量；新增前必须优先评估 `common_parameters`、Spring 配置项、数据库配置和既有 dotenv 变量，并同步更新后端规范、README、部署文档、启动脚本或 dotenv 示例以及配置绑定测试。

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
TEST_AGENT_SERVER_IP_FILE=/data/.testagent/.serverip
```

可选运行参数：

```bash
TEST_AGENT_OPENCODE_NODE_ID=node_prod_opencode
TEST_AGENT_OPENCODE_MAX_RUNS=4
TEST_AGENT_OPENCODE_WEIGHT=100
TEST_AGENT_BACKEND_HEARTBEAT_INTERVAL=5s
TEST_AGENT_BACKEND_STALE_AFTER=10s
TEST_AGENT_OPENCODE_MANAGER_COMMAND_TIMEOUT=10s
TEST_AGENT_BACKEND_DISCOVERY_LIMIT=100
TEST_AGENT_DB_POOL_INITIAL_SIZE=1
TEST_AGENT_DB_POOL_MIN_IDLE=1
TEST_AGENT_DB_POOL_MAX_ACTIVE=10
TEST_AGENT_DB_POOL_MAX_WAIT_MILLIS=30000
TEST_AGENT_INTERNAL_DEFAULT_MODEL=DeepSeek-V4-Flash-W8A8
TEST_AGENT_ICBC_OPENAI_BASE_URL=http://ai-code.sdc.icbc:9070/icbc/jdt/model/api/openai/v1
```

Redis 是系统必需依赖，生产部署必须提供外部地址：

```bash
TEST_AGENT_REDIS_HOST=<redis-host>
TEST_AGENT_REDIS_PORT=6379
TEST_AGENT_RUN_EVENT_REDIS_BUS_ENABLED=true
TEST_AGENT_RUN_EVENT_REDIS_BUS_CHANNEL=test-agent:run-events
TEST_AGENT_SCHEDULER_ENABLED=false
TEST_AGENT_SCHEDULER_SCAN_INTERVAL=30s
TEST_AGENT_SCHEDULER_DUE_TASK_LIMIT=50
TEST_AGENT_SCHEDULER_MANUAL_RUN_LIMIT=50
```

运行管理在线态和监控历史都只使用 Redis，不写入关系型数据库。Java/manager latest snapshot TTL 固定为 10 秒；指标历史使用 ZSET key `test-agent:runtime-metrics:server:{linuxServerId}`、`test-agent:runtime-metrics:backend:{linuxServerId}` 与 `test-agent:runtime-metrics:container:{containerId}`，每 5 秒追加原始样本，保留近 48 小时，key 过期兜底约 49 小时；旧 `test-agent:runtime-metrics:backend:{backendProcessId}` 仅供兼容 API 在无法解析 IP 时回退读取。运行管理 API 默认查询近 1 小时，前端提供 1 分钟、30 分钟、1 小时、6 小时、12 小时、24 小时、48 小时预设窗口。Java latest snapshot、在线心跳、服务器 CPU/内存/磁盘容量和 JVM 内存/GC/线程都按 `linuxServerId` 连续保存，同一 IP 上 Java 后端重启后会覆盖 latest snapshot 并连续追加历史。Redis 历史只保证同一 IP 的 Java 后端重启后连续；若 Redis 自身重启且未启用 AOF/RDB，历史样本会丢失。Java 指标来自 JDK MXBean 和当前工作目录所在文件系统；Go manager 使用 `gopsutil/v4` 与 `opencontainers/cgroups`，Linux 生产态优先按当前进程 cgroup v2/v1 子路径采集容器 CPU、内存和磁盘 IO，`metricsSource=cgroup`；cgroup 不可读时降级当前 manager 进程 CPU/内存，`metricsSource=process`；macOS/Windows 开发态同样使用进程指标；完全不可采集时 `metricsSource=unavailable`。采集失败只影响指标字段，不阻断心跳。

`TEST_AGENT_RUN_EVENT_REDIS_BUS_ENABLED` 只控制 RunEvent 跨实例实时 fan-out；数据库 `run_events` replay、`Last-Event-ID` 和 `session_messages` 快照仍是恢复基线。该开关关闭时 RunEvent 自动退回本机 live bus + DB replay，但用户进程运行管理、manager 心跳、Token 存储、scheduler 和运行指标历史仍直接依赖 Redis。

`TEST_AGENT_SCHEDULER_ENABLED` 默认 `false`。启用 scheduler 后会使用 Redis `SET NX PX` + Lua token 校验作为唯一分布式互斥实现，不降级为本机锁或数据库锁。当前只提供框架和超级管理员管理 API，不内置具体业务任务。

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
  -e TEST_AGENT_SERVER_IP_FILE=/data/.testagent/.serverip \
  test-agent-backend:local
```

启动后检查：

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
```

`DatabaseMigrationRunner` 会在启动时执行 Flyway migration；`ExecutionNodeSeeder` 会把配置中的 opencode node 写入 `execution_nodes` 作为 Run 路由来源。启用 `TEST_AGENT_MODEL_CATALOG_SOURCE=internal` 时，`ModelCatalogApplicationService` 会把企业内模型清单 seed 到 `ai_model_configs`，后续可通过改表控制模型显示、启停和默认值。
启用 `TEST_AGENT_SCHEDULER_ENABLED=true` 时，`ScheduledTaskRegistry` 会同步代码注册任务，`ScheduledTaskRunner` 后台线程会扫描 due task 和管理员手动触发 pending run。超级管理员可在系统管理的定时任务管理页查看任务状态、历史运行记录、调整 Cron、手工启动非 active 任务，并对 `RUNNING` 运行记录发起协作式停止；停止请求会先写入 `STOPPING`，具体 handler 需在长循环或外部调用间隙检查 `ScheduledTaskContext.stopRequested()` / `throwIfStopRequested()` 后退出，最终由 runner 保存 `MANUALLY_STOPPED`。

## 模型目录配置

| 变量 | 默认值 | 说明 |
|---|---|---|
| `TEST_AGENT_MODEL_CATALOG_SOURCE` | local: `bailian`；test/prod: `internal` | 模型目录来源。`opencode` 保持旧代理，`bailian` 直连百炼 `/models`，`internal` 从数据库读取企业内模型。 |
| `TEST_AGENT_BAILIAN_BASE_URL` | `https://coding.dashscope.aliyuncs.com/v1` | 外网百炼 OpenAI-compatible base URL。 |
| `TEST_AGENT_BAILIAN_API_KEY_ENV` | `MODELSTUDIO_API_KEY` | 外网百炼密钥所在环境变量名。 |
| `test-agent.model-catalog.external.api-key` | 空 | 外网百炼密钥的 yml 直配值；本地 IDEA 启动优先使用该值，未配置时回退到 `TEST_AGENT_BAILIAN_API_KEY_ENV` 指向的环境变量。 |
| `TEST_AGENT_BAILIAN_DEFAULT_MODEL` | `qwen3.5-plus` | 外网模式同步给 opencode 的默认模型。 |
| `TEST_AGENT_ICBC_OPENAI_BASE_URL` | `http://ai-code.sdc.icbc:9070/icbc/jdt/model/api/openai/v1` | 企业内 OpenAI-compatible base URL，与 openclaw 企业 patch 保持一致。 |
| `TEST_AGENT_ICBC_OPENAI_TOKEN_ENV` | `ICBC_OPENAI_AUTH_TOKEN` | 企业内 token 所在环境变量名。 |
| `test-agent.model-catalog.internal.api-key` | 空 | 企业内 token 的 yml 直配值；未配置时回退到 `TEST_AGENT_ICBC_OPENAI_TOKEN_ENV` 指向的环境变量。 |
| `TEST_AGENT_ICBC_OPENAI_AUTH_MODE` | `auth-token` | 企业内调用鉴权头模式，默认写入 `Auth-Token`。 |
| `TEST_AGENT_INTERNAL_DEFAULT_MODEL` | `DeepSeek-V4-Flash-W8A8` | 企业内默认模型，前端模型切换会优先选中该模型。 |
`DatabaseMigrationRunner` 会在启动时执行 Flyway migration；`ExecutionNodeSeeder` 会把配置中的 opencode node 写入 `execution_nodes` 作为兼容 Run 路由来源。启用用户进程模型后，`BackendJavaProcessLifecycleRunner` 会在启动和拓扑变化时写入 `linux_servers`、`backend_java_processes`，并每 5 秒按 `linuxServerId` 写入 Redis Java 快照、服务器资源指标历史和 JVM 指标历史；`backendProcessId` 仅表示当前 Java 实例和拓扑连接字段，不再作为 Java 心跳或 JVM 历史的唯一键；`opencode-manager` WebSocket 注册会保留容器、manager 和连接持久拓扑，`managerHeartbeat` 每 5 秒经 WebSocket 写入 Redis manager 快照和容器资源指标历史，latest snapshot TTL 为 10 秒，历史指标保留近 48 小时。
