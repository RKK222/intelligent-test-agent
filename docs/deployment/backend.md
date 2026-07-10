# 后端 Docker 部署说明

## 部署边界

生产和研发测试环境只将 `test-agent-app` 后端 Java 进程放入 Docker 容器。PostgreSQL、Redis 和 opencode server 都是外部服务，通过环境变量或配置中心注入地址和凭据；后端镜像不包含也不启动这些依赖。

研发测试环境的 PG/PostgreSQL 数据库由远端环境启动和维护，不在后端容器或企业内部署 worker 容器中启动；后端只通过 `TEST_AGENT_TEST_DB_*` 或生产 `TEST_AGENT_DB_*` 配置连接该远端数据库。

个人离线开发备用依赖只能通过本地开发脚本启动，不能作为研发测试或生产部署拓扑。

## opencode-manager 容器进程管理

用户专属 opencode server 进程由每个 opencode 容器内的 `opencode-manager` 管理。`opencode-manager` 是与 `backend/` 平级的 Go 单二进制工程，不打包进后端 Java 镜像；它既提供本地 CLI，也提供 `run` 长运行模式，通过 WebSocket JSON 控制面只连接本服务器 Java 实例。

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
OPENCODE_MANAGER_PORT_START=4096
OPENCODE_MANAGER_PORT_END=4100
OPENCODE_MANAGER_BACKEND_PORT=8080
OPENCODE_MANAGER_TOKEN=<manager-control-token>
OPENCODE_BIN=opencode
OPENCODE_MANAGER_STATE_DIR=/data/.testagent/agent-opencode/manager
```

`OPENCODE_MANAGER_CONTAINER_ID` 仅作为非 Windows 下的最后兜底值；生产容器应优先设置容器 hostname，manager 会先读系统 hostname，再读 `/etc/hostname`，最后才读该环境变量。

生产和本地都不再配置 `OPENCODE_MANAGER_ID`。manager 启动时按容器名称和固定管理进程逻辑名 `opencode-manager` 派生内部 `managerId`，形如 `mgr_<normalized_container_id>_opencode_manager`；运行管理、Redis 快照和数据库拓扑中的 `managerId` 仍只作为内部协议 ID，唯一性来自同一共享 Redis 集群内的容器名称加管理进程名称。

长运行模式启动：

```bash
opencode-manager run
```

`run` 不再通过 HTTP discovery 与 Java 后端交互。manager 会先从 `SYS_DATA_ROOT_DIR/.serverid` 读取稳定服务器身份，从 `SYS_DATA_ROOT_DIR/.serverhost` 读取本服务器 Java 可访问主机地址，再结合 `OPENCODE_MANAGER_BACKEND_PORT`（默认 `8080`）派生本服务器 Java WebSocket：`ws://{serverHost}:{port}/api/internal/platform/opencode-runtime/manager/ws`，并用 `Authorization: Bearer <OPENCODE_MANAGER_TOKEN>` 建立控制连接。连接异常断开后，manager 按 `OPENCODE_MANAGER_RECONNECT_INTERVAL` 间隔无限重连，不设置总超时；重连成功并收到 `registered` 后重新发送 `configRequest` 拉取运行配置。

非 Windows 环境下，manager 启动时不再探测容器网卡 IP，也不再依赖 `OPENCODE_MANAGER_LINUX_SERVER_ID` 或 `OPENCODE_MANAGER_SERVER_IP_FILE`。Java 后端先解析 `TEST_AGENT_LINUX_SERVER_ID`（缺失时使用主机名）和 `TEST_AGENT_SERVER_ADVERTISED_HOST`（缺失时复用内网 IPv4 探测），再写入通用参数 `SYS_DATA_ROOT_DIR` 派生的 `.serverid/.serverhost`。Go manager 启动前无法连接 Java 查询数据库，因此按同一系统参数的内置平台默认值读取：Linux `/data/.testagent/.serverid`、`/data/.testagent/.serverhost`，macOS `$HOME/.testagent/.serverid`、`$HOME/.testagent/.serverhost`。文件不存在时每 1 秒重试，最多 30 秒。`.serverid` 必须是 1-128 位稳定 ID，`.serverhost` 必须是无 scheme/port 的主机名或 IPv4；非法内容或超时会让 manager 安全失败。Windows 本机开发态跳过文件等待，使用机器名作为 `linuxServerId`，使用本机非回环 IPv4 作为可访问地址。

启动单个用户进程时，manager 会执行：

```bash
XDG_DATA_HOME=/data/.testagent/agent-opencode/.session/{port} \
OPENCODE_CONFIG_DIR=/data/.testagent/agent-opencode/.config/opencode/ \
opencode serve --hostname 0.0.0.0 --port {port} --print-logs
```

`XDG_DATA_HOME` 和 `OPENCODE_CONFIG_DIR` 的真实值不再由 manager 环境变量传入，而是 manager 通过 WebSocket `configRequest` 从 Java 后端获取通用参数：`OPENCODE_SESSION_DIR`、`OPENCODE_PUBLIC_CONFIG_DIR`。最大进程数同样来自 `OPENCODE_MANAGER_MAX_PROCESSES`（`platform=all`）。OpenCode 会合并用户全局配置和 `OPENCODE_CONFIG_DIR` 自定义目录，企业部署必须保证运行 `opencode serve` 的系统用户 `~/.config/opencode/config.json`、`opencode.json`、`opencode.jsonc` 不维护模型或供应商配置，最多保留只含 `$schema` 的空配置；模型和供应商只写入 `OPENCODE_PUBLIC_CONFIG_DIR` 指向的公共配置目录。收到完整 `configUpdate` 前，manager 会拒绝 `start`/`restart` 命令，不会用容器内默认路径启动用户进程；成功应用 `configUpdate` 后，manager 会立即补发 heartbeat，把端口池裁剪后的生效容量写入运行管理 Redis 快照。后续前端只允许修改最大进程数，Java 只向本服务器 manager 热推 max-only `configUpdate`，路径参数不做运行中刷新。通用参数值不会经过 shell；`$NAME` 直接按 Java 后端进程环境变量展开，`${NAME}` 先按通用参数引用解析、未命中时再按环境变量展开，路径开头的 `$HOME` 和 `~/` 会解析为当前用户主目录后再下发给 manager。`OPENCODE_PUBLIC_CONFIG_DIR` 的存在性和非空检查发生在目标 manager 执行 `start` 时，检查的是目标 opencode server 所在服务器的实际文件系统。

opencode server 默认不设置 `OPENCODE_SERVER_PASSWORD`，后端和前端展示的 `baseUrl/serviceAddress` 使用 `.serverhost` / `TEST_AGENT_SERVER_ADVERTISED_HOST` 拼接端口，不再使用 `linuxServerId` 拼地址。生产部署必须通过容器网络、主机防火墙或网关限制端口池访问面，不得把用户进程端口暴露到不可信网络。

后端创建用户进程、应用版本工作区和个人 worktree 时读取数据库 `common_parameters` 中当前平台的 opencode 路径参数：`OPENCODE_SESSION_DIR`、`OPENCODE_PUBLIC_CONFIG_DIR`、`OPENCODE_APP_WORKSPACE_ROOT`、`OPENCODE_PERSONAL_WORKTREE_ROOT`。`common_parameters` 为唯一事实源，缺失或值为空时抛 `INTERNAL_ERROR` 业务异常，不在 yaml 或代码常量预留 fallback；Windows 默认值在迁移中按 `D:/data/.testagent/agent-opencode/...` 初始化。macOS/Linux 本地开发可把路径写为 `$HOME/.testagent/...` 或 `$TEST_AGENT_ROOT/...`，加载后的 `resolvedValue` 会变为实际用户目录或环境变量值。除 `OPENCODE_MANAGER_MAX_PROCESSES` 外，通用参数不允许通过前端直接修改；路径类参数应通过部署配置、数据库迁移或公共配置初始化流程调整。真实创建用户进程时，后端先按健康容器和空闲端口选择目标容器，再向该容器对应 manager 下发 `start`；manager 使用已通过 `configUpdate` 同步的 `OPENCODE_PUBLIC_CONFIG_DIR`，并在所在服务器检查该目录必须存在且非空。缺失、为空、非目录或不可读时返回 `OPENCODE_UNAVAILABLE`，错误消息包含目标服务器和 manager 实际检查目录，并提示联系超级管理员进入“系统管理 → 配置管理 → opencode公共配置管理”完成初始化；不会创建 session、不会启动 opencode server。

公共 Agent/Skill 配置额外读取公共 Git 地址和 `OPENCODE_PUBLIC_CONFIG_GIT_ROOT`、`OPENCODE_PUBLIC_CONFIG_DIR`、`OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT`。公共 Git 地址按 `TEST_AGENT_DEPLOYMENT_MODE` / `test-agent.deployment.mode` 解释同一个 `OPENCODE_PUBLIC_AGENT_GIT_URL`：外部部署直接使用完整 SSH/HTTPS Git URL，内部部署只保存 `host[:port]/path`，Git 操作时按当前管理员统一认证号拼接 `ssh://{unifiedAuthId}@...`；远端分支查询同样在执行 `git ls-remote` 前拼接该有效 URL。Git 地址默认为 `UNCONFIGURED`，未配置前公共 Agent 只读 status 可用，更新、worktree、commit、publish 均被禁用。公共配置 Git 仓库按服务器本地盘初始化到 `OPENCODE_PUBLIC_CONFIG_GIT_ROOT`，内部部署接管已有仓库时比较 origin 会忽略 `ssh://任意用户@` 前缀，避免不同管理员使用各自 SSH key 操作同一内部库时误判不一致；初始化完成后必须校验 `OPENCODE_PUBLIC_CONFIG_DIR` 指向的 opencode 配置目录存在且非空。该目录应来自公共配置 Git 仓库内容，例如默认布局为 `{OPENCODE_PUBLIC_CONFIG_GIT_ROOT}/opencode/`；manager 启动用户进程时不会创建空配置目录，目录缺失时应先完成目标服务器公共配置初始化。公共配置文件树根为 `{OPENCODE_PUBLIC_CONFIG_GIT_ROOT}/opencode/`，其中 `opencode.jsonc` 是平台模型、供应商和内部代理 provider 的事实源，`agents/` 放 opencode agent Markdown，`skills/<skill-name>/` 直接放各自包含 `SKILL.md` 的实际技能包，不增加中间包装目录或符号链接。公共仓库有未提交修改时仍视为已初始化并允许浏览，但状态为 `CONFLICT`；更新默认拒绝覆盖，只有超级管理员明确确认后才恢复已跟踪文件再拉取，未跟踪文件不删除。公共 worktree 由管理员在前端显式选择一台已初始化服务器后创建，目录在该服务器 `{OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT}/{worktreeName-yyyymmdd}` 下创建，创建成功后记录 `worktreeId -> linuxServerId`，后续公共 Agent/Skill 文件、diff、stage、commit、publish 都由当前后端代理到该服务器执行，浏览器不直连目标后端。远端分支/目录只读查询最长等待 60 秒；SSH Git 命令使用非交互模式、10 秒连接超时和 keepalive，避免私钥错误或端口不通时长时间卡住。

公共配置初始化排障优先按前端提示的 `traceId` 搜索 Java 日志：`event=agent_config_public_branches_failed` 表示初始化弹窗加载远端分支失败，`event=agent_config_public_repository_initialize_failed` 表示已选分支后的 clone/fetch/pull/目录校验失败；底层 Git 命令还会输出 `event=git_command_timeout`、`event=git_command_failed` 或 `event=git_command_slow`，包含目标服务器、脱敏后的 Git URL/命令、耗时、`gitFailureType` 和 `gitFailureHint`。日志不会输出 SSH 私钥、Authorization 或 URL 中的用户名/token。

启用用户进程模型后，已登录用户的 Run 和 opencode runtime 代理都会优先使用当前用户绑定的 `READY` 进程；用户未初始化或健康检测失败时返回平台 `OPENCODE_UNAVAILABLE`，由前端提示初始化。若请求落到任意 Java 且用户 ACTIVE binding 属于其他服务器，当前 Java 会通过 Redis Java 快照查找目标服务器 `listenUrl` 并把用户进程状态、初始化、Run 启动和 opencode runtime 代理请求转发到目标 Java，目标 Java 再控制本服务器 managers；目标后端不在线时返回 `OPENCODE_UNAVAILABLE`，不自动迁移 binding。无用户主体的 static-token 兼容调用仍可使用数据库中已有的固定 `execution_nodes`，用于旧集成或本地探测；应用不再从 yml 自动 seed 固定节点。Session 级 runtime 代理发现绑定节点不是当前用户进程节点时，会在当前进程上创建新的远端 session 并覆盖绑定，不会删除旧远端 session。

## 多服务器用户进程拓扑规划

一个生产集群由以下角色组成：

| 角色 | 部署数量 | 关键配置 | 说明 |
|---|---:|---|---|
| 后端 Java 实例 | 每台 Linux 服务器 1 个或多个 | `server.port`、`TEST_AGENT_LINUX_SERVER_ID`、`TEST_AGENT_SERVER_ADVERTISED_HOST`、`TEST_AGENT_OPENCODE_MANAGER_TOKEN`、`common_parameters.SYS_DATA_ROOT_DIR` | 同一服务器上的多个 Java 共享同一个稳定 `linuxServerId`，各自用可访问 `listenUrl` 写入 Redis 快照；目标 Java 优先选择与 manager 有连接的实例，其次选同服务器最新心跳实例。 |
| opencode 容器 | 每台 Linux 服务器多个 | 容器 hostname、`OPENCODE_MANAGER_CONTAINER_ID` 兜底值、端口池、挂载目录 | 每个容器运行 1 个 `opencode-manager run`；`containerId` 标识容器，非 Windows 先取系统 hostname，再取 `/etc/hostname`，最后才取 `OPENCODE_MANAGER_CONTAINER_ID`，`managerId` 由 `containerId + opencode-manager` 派生，`linuxServerId` 来自 `.serverid`。 |
| 用户 opencode server 进程 | 每个用户 1 个当前绑定 | 由 manager 按端口启动 | `baseUrl` 使用 `.serverhost` / advertised host 拼接端口，session 持久化在对应 Linux 服务器。 |
| 前端访问入口 | 1 个负载均衡域名 | `VITE_TEST_AGENT_API_BASE_URL` | 浏览器只访问平台后端，不直连 opencode server 或 manager。 |

后端实例之间需要在可信内网内互相访问各自自动注册的 `listenUrl`，用于用户 opencode 进程相关 HTTP 请求按 binding 所属服务器转发；该路由会透传用户 `Authorization` 和 `X-Trace-Id`，目标 Java 仍执行正常鉴权。每个 manager 只通过 WebSocket 控制面与本服务器 Java 通信，不再连接其他服务器 Java。后端扩容时，新实例启动后每 5 秒按 `backendProcessId` 写入 Java 后端快照，并按稳定 `linuxServerId` 分组参与路由；对应服务器上的 managers 只需连接本服务器任一可访问 Java。应用版本工作区副本的实时同步通过共享 Redis pub/sub 广播触发，所有后端需要连接同一个 Redis，并显式开启 `TEST_AGENT_SERVER_BROADCAST_ENABLED=true`（对应配置 `test-agent.server-broadcast.enabled=true`）；未开启时退化为单机 Noop 广播，只依赖本机副本记录和补偿扫描。

端口池规划必须满足：

- 同一 Linux 服务器上所有 opencode 容器的主机可访问端口范围不能重叠，数据库约束 `opencode_server_processes(linux_server_id, port)` 会拒绝同一服务器端口重复。
- `common_parameters.OPENCODE_MANAGER_MAX_PROCESSES` 不得超过容器端口池容量；超过时 manager 会按 `OPENCODE_MANAGER_PORT_END - OPENCODE_MANAGER_PORT_START + 1` 裁剪，并通过即时 heartbeat 回报裁剪后的生效容量。
- 建议每个容器预留 1 到 2 个端口作为故障排查或滚动扩容缓冲，不要把端口池全部按理论最大值打满。
- `.serverid` 固定语义是“稳定服务器身份”，不是访问地址；`.serverhost` 固定语义是“本服务器可访问主机地址”，不承载 token、URL 或 JSON。
- 本地或测试环境执行 `./restart-dev-services.sh` 时，不再注入 server-ip-file 或后端 listen-url 环境变量；Java 写入、Go 读取的路径都遵循 `SYS_DATA_ROOT_DIR/.serverid` 和 `.serverhost` 约定，后端直连地址由 Java 按 advertised host 和 `server.port` 派生。脚本只在局域网访问前端时把 `TEST_AGENT_BASE_URL` 补成同一内网地址。

目录和日志规划：

| 路径 | 所属节点 | 用途 | 运维要求 |
|---|---|---|---|
| `${SYS_DATA_ROOT_DIR}/.serverid` | Java 后端本地盘并挂载到同服务器 manager 容器 | 稳定服务器身份，manager 注册 `linuxServerId` 的来源 | Java 启动时写入，manager 启动前读取；企业内 `/data/testagent/data/.serverid` 应与 `TEST_AGENT_LINUX_SERVER_ID` 一致。 |
| `${SYS_DATA_ROOT_DIR}/.serverhost` | Java 后端本地盘并挂载到同服务器 manager 容器 | 本服务器 Java 可访问 host，manager WebSocket seed 和用户进程 baseUrl host 的来源 | Java 启动时写入，manager 启动前读取；企业内 `/data/testagent/data/.serverhost` 应与 `TEST_AGENT_SERVER_ADVERTISED_HOST` 一致，不能带 scheme、端口或 JSON。 |
| `/data/.testagent/agent-opencode/.session/{port}` | Linux 服务器本地盘并挂载到容器 | 用户进程 `XDG_DATA_HOME` | 不能跨 Linux 服务器共享；备份/清理必须按端口和用户绑定关系执行。 |
| `/data/.testagent/agent-opencode/.config/opencode/` | Linux 服务器本地盘并挂载到容器 | 公共 agent、插件、skill 配置 | 多容器共享只读或受控写入；变更前先备份。 |
| `/data/.testagent/agent-opencode/.config/` | Linux 服务器本地盘 | 公共 Agent 配置 Git 根目录 | 由 `OPENCODE_PUBLIC_CONFIG_GIT_ROOT` 控制；每台在线后端所在服务器需要在系统管理中初始化，Git origin 必须与参数一致；工作树有修改时更新需显式确认恢复。 |
| `/data/.testagent/agent-opencode/.configdev/` | Linux 服务器本地盘 | 公共 Agent 配置 Git worktree 根目录 | 由 `OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT` 控制；worktree 记录服务器归属，发布时由当前后端代理到目标服务器，先 merge 回公共配置当前分支再 push。 |
| `/data/.testagent/agent-opencode/workspace/appworkspace/` | Linux 服务器本地盘 | 应用版本工作区根目录 | 默认由 `common_parameters.OPENCODE_APP_WORKSPACE_ROOT` 控制；目录片段为版本 + 代码库英文名。 |
| `/data/.testagent/agent-opencode/workspace/personalworktree/` | Linux 服务器本地盘 | 个人 git worktree 根目录 | 默认由 `common_parameters.OPENCODE_PERSONAL_WORKTREE_ROOT` 控制；目录片段包含版本、统一认证号、代码库英文名和个人空间 ID。 |
| `/data/.testagent/agent-opencode/manager/processes/{port}.json` | 容器挂载目录 | manager 本地进程状态 | 用于 stop/list/restart；容器重启后继续识别已有 state。 |
| `/data/.testagent/agent-opencode/manager/logs/{port}.log` | 容器挂载目录 | opencode server stdout/stderr | 日志不得输出 token、Authorization、Cookie 或完整 prompt。 |

生产日志处理：

- Java 后端只输出 console 结构化日志；systemd 部署时用 `journalctl -u <service> -f` 查看并由 journald 负责保留策略，非 systemd 部署应把 stdout/stderr 重定向到运维目录并配置 logrotate。
- `opencode-manager run` 的自身日志走容器 stdout，可用 `docker logs -f <worker-container>` 查看；重点搜索 `config update applied`、`websocket disconnected`、`.serverhost`、`.serverid` 和 `OPENCODE_UNAVAILABLE`。
- 用户 opencode server 日志在 `OPENCODE_MANAGER_STATE_DIR/logs/{port}.log`；企业内纯 Docker worker 当前为 `/data/testagent/data/agent-opencode/manager/worker/logs/{port}.log`。
- `OPENCODE_MANAGER_STATE_DIR/processes/{port}.json` 是 manager 运行态 state，不是普通日志；不要在 worker 运行中直接删除。需要清理坏 state 时，先通过运行管理停止对应用户进程，或停 worker 后再处理。
- 对外排障日志必须脱敏 token、Authorization、Cookie、完整 prompt、私钥和用户完整输入；优先提供 traceId、linuxServerId、containerId、port、错误码和最近 200 行上下文。

企业内双后端升级见 `deploy/internal/README-two-backend-122-233-30-114.md`。交付 zip 放到各节点 `/data/0709/internal.zip` 后，`122.233.30.4` 执行 `deploy-internal-release.sh --backend-host 122.233.30.4 --skip-frontend`，`122.233.30.114` 执行 `deploy-internal-release.sh --backend-host 122.233.30.114 --skip-frontend`；如果前端机允许直连 scp，可只让一个后端节点不加 `--skip-frontend` 顺带更新前端。统一登录策略阻止直连时，在 `122.233.30.2` 本地执行 `deploy-internal-frontend.sh --archive /data/0709/internal.zip`。

容量与心跳参数建议：

| 参数 | 默认/建议 | 说明 |
|---|---|---|
| `TEST_AGENT_DEPLOYMENT_MODE` | `external` | 部署模式：`external`（外部部署，默认）或 `internal`（企业内部部署），绑定到 `test-agent.deployment.mode`。 |
| `TEST_AGENT_REDIS_HOST` / `TEST_AGENT_REDIS_PORT` / `TEST_AGENT_REDIS_PASSWORD` | 部署 Redis 地址，绑定到 Spring 标准 `spring.data.redis.*` | Redis 是系统必需依赖；用户进程运行管理、manager 控制面、Token 存储和 scheduler 均使用同一 Redis。 |
| `TEST_AGENT_BACKEND_HEARTBEAT_INTERVAL` | `5s` | 后端实例写入 Redis Java 快照的间隔。 |
| `TEST_AGENT_BACKEND_STALE_AFTER` | `10s` | Java/manager Redis 快照 TTL；不再作为数据库心跳回退窗口使用。 |
| `TEST_AGENT_BACKEND_DISCOVERY_LIMIT` | `100` | 兼容诊断端点和 Java 间路由读取在线后端快照时的上限。 |
| `TEST_AGENT_OPENCODE_MANAGER_COMMAND_TIMEOUT` | `10s` | 后端等待 manager 命令结果的超时。 |
| `TEST_AGENT_SCHEDULER_ENABLED` | `false` / 需要后台任务时 `true` | 控制 scheduler 后台扫描和 pending run 执行；任务定义在应用启动时仍会同步到 `scheduled_tasks`，管理页可展示。opencode stale active Run 收敛任务复用该框架。 |
| opencode stale active Run 收敛 cron | `0 */5 * * * *` | 业务任务每 5 分钟扫描一次，启动 catch-up 不扫描；只跳过该业务任务的启动补跑，不改变 scheduler 框架扫描行为。 |
| opencode active Run 超时阈值 | `2h` | `runs.updated_at` 超过 2 小时且仍为 `PENDING/RUNNING/CANCELLING` 才进入候选。 |
| opencode 输出活跃 Redis TTL | `30m` | `test-agent:run-output-activity:{runId}` 存在表示 30 分钟内仍有用户可见输出，收敛时跳过。 |
| opencode pending ask Redis 状态 | 无固定 TTL | `test-agent:run-pending-ask:{runId}` 存在表示最新状态仍等待用户处理 `permission.asked/question.asked`，不通过数据库 RunEvent 反查；收到 reply/reject 或 Run 终态后清理。 |
| opencode stale active Run 单轮 limit | `200` | 控制单轮数据库候选读取量，避免一次扫描处理过多历史脏 Run。 |
| `TEST_AGENT_SERVER_BROADCAST_ENABLED` | `false` / 多机 `true` | 开启 Redis 服务器广播，应用版本创建、同步和 git pull 后广播目标 commit 给其他后端；公共 Agent 配置长操作也用它广播安全进度字段。 |
| `TEST_AGENT_SERVER_BROADCAST_CHANNEL` | `test-agent:server-broadcast` | 服务器广播 Redis channel；同一集群必须一致。 |
| `TEST_AGENT_MANAGED_WORKSPACE_REPLICA_RECONCILER_ENABLED` | `true` | 启用应用版本工作区本机副本补偿扫描，补齐漏消息或落后 commit。 |
| `TEST_AGENT_MANAGED_WORKSPACE_REPLICA_RECONCILER_INTERVAL` | `60s` | 副本补偿扫描间隔，最小按 10 秒执行。 |
| `OPENCODE_MANAGER_HEARTBEAT_INTERVAL` | `5s` | manager 通过本服务器 Java socket 发送 `managerHeartbeat` 的间隔；心跳同时携带容器 CPU、内存、磁盘 IO 和本地 opencode 进程明细。 |
| `OPENCODE_MANAGER_RECONNECT_INTERVAL` | `10s` | 本服务器 Java WebSocket 断开后重连的间隔，不设总超时。 |

## 扩容、故障处理与回滚

后端 Java 扩容流程：

1. 为新实例配置唯一且可信内网可访问的 `server.port`，同一服务器上的多个 Java 使用同一个 `TEST_AGENT_LINUX_SERVER_ID`。
2. 使用同一个 `TEST_AGENT_OPENCODE_MANAGER_TOKEN`，并确认自动派生的 `http://<advertised-host>:<server.port>`、`SYS_DATA_ROOT_DIR/.serverid` 和 `.serverhost` 所在挂载路径可从同服务器 opencode 容器访问。
3. 启动新后端，检查 `/actuator/health` 返回 `UP`。
4. 等待 5 到 10 秒，确认超级管理员运行管理页能看到新的 Java 后端 Redis 快照。
5. 确认该服务器上的 manager 已连接本服务器 Java；运行管理页中对应 manager-backend 连接应出现并为 `CONNECTED`，点击后端 Java 进程可看到 Redis 中保留的近 48 小时服务器/JVM 监控趋势。若该后端承接其他 Java 转发来的用户进程请求，需确认其它后端可访问运行管理中展示的后端 `listenUrl`。

opencode 容器扩容流程：

1. 在同一 Linux 服务器上分配不与既有容器重叠的端口池。
2. 按上文挂载 `/data/.testagent/agent-opencode/.session/`、`/data/.testagent/agent-opencode/.config/opencode/`、`/data/.testagent/agent-opencode/workspace/` 和 `/data/.testagent/agent-opencode/manager`。
3. 配置新的容器 hostname 和端口池环境变量；不要再配置 `OPENCODE_MANAGER_ID`、`OPENCODE_MANAGER_LINUX_SERVER_ID` 或 `OPENCODE_MANAGER_SERVER_IP_FILE`，manager 会由容器名称和固定进程名派生内部 ID，并按 `SYS_DATA_ROOT_DIR/.serverid/.serverhost` 读取服务器身份与地址；`OPENCODE_MANAGER_CONTAINER_ID` 仅作为非 Windows 最后兜底。非 Windows 解析顺序固定为系统 hostname、`/etc/hostname`、`OPENCODE_MANAGER_CONTAINER_ID`；Windows 直接使用机器名。
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
| 后端扩容后本服务器 manager 未连接 Java | 检查新后端是否写入 `.serverid/.serverhost`、manager 是否能访问 `ws://{serverHost}:{OPENCODE_MANAGER_BACKEND_PORT}/api/internal/platform/opencode-runtime/manager/ws`、manager token 是否一致 | 修正 `TEST_AGENT_SERVER_ADVERTISED_HOST`、`OPENCODE_MANAGER_BACKEND_PORT` 或 token 后等待 manager 按重连间隔恢复。 |
| manager 日志持续连接旧 IP 或旧 Java | 对比 Java 服务器 `cat ${SYS_DATA_ROOT_DIR}/.serverhost`、worker 容器内 `cat ${SYS_DATA_ROOT_DIR}/.serverhost`、纯 Docker worker 的 `SYS_DATA_ROOT_DIR` 和后端 `SYS_DATA_ROOT_DIR` 是否指向同一挂载目录 | 先启动或重启 Java 让它写入正确 `.serverhost`，确认 worker 读到同一文件后再重启 worker；不要用 `OPENCODE_MANAGER_SERVER_IP_FILE` 或手工环境变量绕过。 |
| 历史会话长时间显示运行中但没有输出 | 确认 `TEST_AGENT_SCHEDULER_ENABLED=true`、Redis 可用、`scheduled_tasks` 中 `opencode-runtime.stale-active-run-reconcile` 已注册且有运行记录；检查 Run 是否最近 30 分钟仍有输出活跃 key，或是否存在未处理 ask key | 无 pending ask 且无近期输出时，任务会把超过 2 小时的 active Run 标记为 `FAILED` 并追加固定 `run.failed`；该机制只修复平台 Run 状态，不主动 cancel/abort 远端 opencode 会话。 |
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
5. 后端扩容：新增后端实例后，Redis 出现新 Java 快照，本服务器 manager 连接该 Java，运行管理页出现新的 manager-backend `CONNECTED` 连接；其它 Java 能通过 `listenUrl` 把 remote binding 请求转发到该后端。
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

个人离线开发备用脚本默认启动备用 Postgres，映射到 `127.0.0.1:15432`；Redis 是可选 profile，默认映射到 `127.0.0.1:16379`。脚本只读取环境变量，不生成或写入密钥。

仓库根目录的 `restart-dev-services.sh` 是 macOS/Linux/WSL/Git Bash 三服务一键重启入口，Windows PowerShell 使用同级 `restart-dev-services.ps1`：二者默认读取 `.env.test` 并以 `test` profile 启动，按「后端 → opencode-manager → 前端」的依赖顺序，**逐个先 kill 原进程再启动**。脚本启动后端 Java 进程时同样清空 JVM 代理系统属性，确保测试库和 Redis 使用直连网络。test profile 下脚本默认启动本机 Go `opencode-manager`；其它 profile 在 `TEST_AGENT_OPENCODE_BASE_URL` 指向 loopback 或默认路由网卡探测到的本机 IPv4 时默认启动 manager。manager 以 `run` 长运行模式启动，不再单独启动 standalone `opencode serve`——用户进程由 manager 自行派生，避免 4096 端口冲突。脚本会导出 `TEST_AGENT_ROOT`，并把早期本地测试库使用的兼容别名 `TESTAGENT` 默认设置为相同项目根目录，确保 `$TEST_AGENT_ROOT/...` 与既有 `$TESTAGENT/...` 通用参数路径都能在 Java 进程中展开后再下发给 manager。停止 manager 时，脚本会读取 `.tmp/dev-services/opencode-manager-state/processes/*.json` 中的 pid，并扫描端口池 `4096..4105` 内的 `opencode serve --port ...` 监听，统一停止残留用户进程后删除 state JSON，避免重启后旧进程或旧 state 导致端口被判定为已托管。脚本不再注入 server-ip-file 路径；Java 和 Go manager 都按 `SYS_DATA_ROOT_DIR/.serverid/.serverhost` 约定写读服务器身份与可访问地址。manager 与后端共享的 `TEST_AGENT_OPENCODE_MANAGER_TOKEN` 未设置时默认 `local-manager-token`（与 `application-guo.yml` 一致），本地无需手配 manager token；设 `TEST_AGENT_START_OPENCODE_MANAGER=false` 可跳过 manager。需要使用本地离线或个人调试配置时，Bash 显式传入 `--profile local --env-file .env.local` 或 `--profile guo --env-file .env.guo`，PowerShell 对应传入 `-Profile local -EnvFile .env.local` 或 `-Profile guo -EnvFile .env.guo`。

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
TEST_AGENT_MODEL_CATALOG_SOURCE=external
TEST_AGENT_EXTERNAL_MODEL_PROVIDER_ID=deepseek
TEST_AGENT_EXTERNAL_MODEL_PROVIDER_NAME=DeepSeek
TEST_AGENT_EXTERNAL_MODEL_BASE_URL=https://api.deepseek.com
TEST_AGENT_EXTERNAL_MODEL_API_KEY_ENV=EXTERNAL_API_KEY
EXTERNAL_API_KEY=<external-api-key>
TEST_AGENT_EXTERNAL_MODEL_DEFAULT_MODEL=deepseek-v4-pro
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

`local` / `guo` profile 不再支持 `test-agent.opencode.manager-control.gateway-mode=local`、`test-agent.opencode.local-direct` 或本地直连 `baseUrl` 绕过。用户进程状态、初始化、Run 启动、运行管理 restart/stop 都统一通过 `SocketOpencodeProcessManagerGateway` 发送本服务器 manager WebSocket 命令。

本地开发也必须启动 Go manager，并确保 Java 启动时写入的 `SYS_DATA_ROOT_DIR/.serverid` 和 `.serverhost` 能被 manager 读取。前端请求可以落到任意 Java；入口 Java 先用统一 Java 路由解析器定位目标 `linuxServerId/containerId` 对应的 Java，必要时通过后端 HTTP 转发到目标 Java，只有目标 Java 控制本服务器 manager。

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
TEST_AGENT_DB_URL=jdbc:postgresql://<pg-host>:5432/<database>
TEST_AGENT_DB_USERNAME=<username>
TEST_AGENT_DB_PASSWORD=<password>
# 可选，默认 org.postgresql.Driver；指定类必须随后端 jar 或启动 classpath 一起交付。
TEST_AGENT_DB_DRIVER_CLASS_NAME=org.postgresql.Driver
TEST_AGENT_API_TOKEN=<api-token>
TEST_AGENT_CORS_ALLOWED_ORIGINS=https://<frontend-origin>
TEST_AGENT_MODEL_CATALOG_SOURCE=internal
ICBC_OPENAI_AUTH_TOKEN=<icbc-openai-token>
TEST_AGENT_OPENCODE_MANAGER_TOKEN=<manager-control-token>
```

可选运行参数：

```bash
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
TEST_AGENT_SCHEDULER_ENABLED=false
TEST_AGENT_SCHEDULER_SCAN_INTERVAL=30s
TEST_AGENT_SCHEDULER_DUE_TASK_LIMIT=50
TEST_AGENT_SCHEDULER_MANUAL_RUN_LIMIT=50
```

运行管理在线态和监控历史都只使用 Redis，不写入关系型数据库。Java/manager latest snapshot TTL 固定为 10 秒；指标历史使用 ZSET key `test-agent:runtime-metrics:server:{linuxServerId}`、`test-agent:runtime-metrics:backend:{linuxServerId}` 与 `test-agent:runtime-metrics:container:{containerId}`，每 5 秒追加原始样本，保留近 48 小时，key 过期兜底约 49 小时；旧 `test-agent:runtime-metrics:backend:{backendProcessId}` 仅供兼容 API 在无法解析稳定服务器身份时回退读取。运行管理 API 默认查询近 1 小时，前端提供 1 分钟、30 分钟、1 小时、6 小时、12 小时、24 小时、48 小时预设窗口。Java latest snapshot、在线心跳、服务器 CPU/内存/磁盘容量和 JVM 内存/GC/线程都按 `linuxServerId` 连续保存，同一稳定服务器身份下 Java 后端重启后会覆盖 latest snapshot 并连续追加历史。Redis 历史只保证同一稳定服务器身份的 Java 后端重启后连续；若 Redis 自身重启且未启用 AOF/RDB，历史样本会丢失。Java 指标来自 JDK MXBean 和当前工作目录所在文件系统；Go manager 使用 `gopsutil/v4` 与 `opencontainers/cgroups`，Linux 生产态优先按当前进程 cgroup v2/v1 子路径采集容器 CPU、内存和磁盘 IO，`metricsSource=cgroup`；cgroup 不可读时降级当前 manager 进程 CPU/内存，`metricsSource=process`；macOS/Windows 开发态同样使用进程指标；完全不可采集时 `metricsSource=unavailable`。采集失败只影响指标字段，不阻断心跳。

RunEvent 实时 SSE 不再提供 Redis Pub/Sub fan-out 配置。单个 Run 的跨 Java 实时 SSE 按 Run 原始归属路由到生产 Java，并由目标 Java 的本机 live bus 推送；数据库 `run_events` replay、`Last-Event-ID` 和 `session_messages` 快照仍是恢复基线。非生产 Java 收到 RunEvent SSE 会流式转发到生产 Java；目标 Java 不在线时只能退回本机 snapshot/DB replay。用户级运行态跨 Java 即时刷新保留既有低频轮询兜底；用户进程运行管理、manager 心跳、Token 存储、scheduler 和运行指标历史仍直接依赖 Redis。

`TEST_AGENT_SCHEDULER_ENABLED` 默认 `false`。环境变量存在但值为空时按 `false` 处理，必须显式设置为 `true` 才会启用后台扫描。应用启动时会先同步 `ScheduledTaskHandler` 代码注册任务，确保超级管理员定时任务管理页能看到任务定义；只有启用 scheduler 后才启动后台扫描线程并执行 due task 或 pending manual run。关闭时管理端手动触发会返回 `CONFLICT`，不会再创建无法执行的 `PENDING` 运行记录；历史已存在的 `PENDING` 记录需要启用 scheduler 后由后台扫描消费，或由管理员按排障流程显式处理。启用后会使用 Redis `SET NX PX` + Lua token 校验作为唯一分布式互斥实现，不降级为本机锁或数据库锁。

## 运行示例

```bash
docker run --rm -p 8080:8080 \
  -e TEST_AGENT_DB_URL=jdbc:postgresql://pg.example.internal:5432/test_agent \
  -e TEST_AGENT_DB_USERNAME=test_agent \
  -e TEST_AGENT_DB_PASSWORD=change-me \
  -e TEST_AGENT_API_TOKEN=change-me \
  -e TEST_AGENT_CORS_ALLOWED_ORIGINS=https://agent.example.com \
  -e TEST_AGENT_MODEL_CATALOG_SOURCE=internal \
  -e ICBC_OPENAI_AUTH_TOKEN=change-me \
  -e TEST_AGENT_OPENCODE_MANAGER_TOKEN=change-me-manager-token \
  test-agent-backend:local
```

启动后检查：

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
```

`DatabaseMigrationRunner` 会在启动时执行 Flyway migration；固定 opencode node yml 配置已作废，应用不再从配置自动写入 `execution_nodes`，历史兼容节点需由数据库已有数据或后续专门初始化流程维护。启用 `TEST_AGENT_MODEL_CATALOG_SOURCE=internal` 时，`ModelCatalogApplicationService` 会把企业内模型清单 seed 到 `ai_model_configs`，后续可通过改表控制模型显示、启停和默认值。
应用启动时，`ScheduledTaskRegistry` 会同步代码注册任务；启用 `TEST_AGENT_SCHEDULER_ENABLED=true` 时，`ScheduledTaskRunner` 后台线程才会扫描 due task 和管理员手动触发 pending run。超级管理员可在系统管理的定时任务管理页查看任务状态、历史运行记录、调整 Cron、手工启动非 active 任务，并对 `RUNNING` 运行记录发起协作式停止；scheduler 未启用时手工启动返回冲突错误，不会写入新的 pending run。停止请求会先写入 `STOPPING`，具体 handler 需在长循环或外部调用间隙检查 `ScheduledTaskContext.stopRequested()` / `throwIfStopRequested()` 后退出，最终由 runner 保存 `MANUALLY_STOPPED`。

## 内部模型代理与模型目录配置

前端对话框模型和供应商目录始终来自用户 opencode server 配置文件中的 `/api/model`、`/api/provider`，不再从数据库 `ai_model_configs` 或 `ModelCatalogApplicationService` 返回托管目录。Run 启动前也不再 `PATCH /global/config` 写入供应商、UCID 或供应商地址。

Java 进程需要配置 `TEST_AGENT_INTERNAL_PROXY_API_KEY`，用于校验 opencode 子进程访问内部代理。用户 opencode server 启动时，Java 通过 manager `start` command 的 `environment` 注入：

```bash
TEST_AGENT_INTERNAL_PROXY_API_KEY=<proxy-api-key>
TEST_AGENT_INTERNAL_PROXY_BASE_URL=http://<same-node-java>/api/internal/platform/opencode-runtime/internal-model-proxy/v1
ICBC_UCID=<current-user-unified-auth-id>
```

`ICBC_OPENAI_AUTH_TOKEN` 不再要求通过 Java 环境变量提供；超级管理员在前端“系统管理 → 内部模型供应商”维护内部供应商 `providerId/name/baseUrl/enabled/sortOrder` 和全局 token，token 明文保存在 `internal_model_proxy_settings`，前端只展示已配置/未配置。opencode 公共配置文件中应配置内部代理地址和 provider header，完整样例见 `docs/api/http-api.md` 的“opencode 配置样例”。

| 变量 | 默认值 | 说明 |
|---|---|---|
| `TEST_AGENT_MODEL_CATALOG_SOURCE` | local: `external`；test/prod: `internal` | 历史兼容项。模型/供应商展示已统一以 opencode 配置文件为准。 |
| `TEST_AGENT_EXTERNAL_MODEL_PROVIDER_ID` | `external-openai` | 外部 OpenAI-compatible provider ID，例如 DeepSeek 可设为 `deepseek`。 |
| `TEST_AGENT_EXTERNAL_MODEL_PROVIDER_NAME` | `External OpenAI Compatible` | 外部 OpenAI-compatible provider 展示名。 |
| `TEST_AGENT_EXTERNAL_MODEL_BASE_URL` | 空 | 外部 OpenAI-compatible base URL，例如 `https://api.deepseek.com`。旧 `TEST_AGENT_BAILIAN_BASE_URL` 仍作为兼容兜底。 |
| `TEST_AGENT_EXTERNAL_MODEL_API_KEY_ENV` | `EXTERNAL_API_KEY` | 外部模型密钥所在环境变量名。旧 `TEST_AGENT_BAILIAN_API_KEY_ENV` 仍作为兼容兜底。 |
| `test-agent.model-catalog.external.api-key` | 空 | 外部模型密钥的 yml 直配值；本地 IDEA 启动优先使用该值，未配置时回退到 `TEST_AGENT_EXTERNAL_MODEL_API_KEY_ENV` 指向的环境变量。 |
| `TEST_AGENT_EXTERNAL_MODEL_DEFAULT_MODEL` | 空 | 外部模式同步给 opencode 的默认模型，例如 `deepseek-v4-pro`。旧 `TEST_AGENT_BAILIAN_DEFAULT_MODEL` 仍作为兼容兜底。 |
| `MODELSTUDIO_API_KEY` | 空 | `TEST_AGENT_MODEL_CATALOG_SOURCE=bailian` 时使用的 Model Studio API Key；该模式使用代码内置的 `modelstudio` provider、`https://coding.dashscope.aliyuncs.com/v1` base URL 和 `qwen3.5-plus` 默认模型。 |
| `TEST_AGENT_INTERNAL_PROXY_API_KEY` | 空 | 内部模型代理鉴权 apikey，Java 校验 opencode 子进程请求并注入用户 opencode server 环境；敏感，不得写入日志或 startCommand 明文。 |
| `TEST_AGENT_ICBC_OPENAI_BASE_URL` | `http://ai-code.sdc.icbc:9070/icbc/jdt/model/api/openai/v1` | 企业内 OpenAI-compatible base URL，与 openclaw 企业 patch 保持一致。 |
| `TEST_AGENT_ICBC_OPENAI_TOKEN_ENV` | `ICBC_OPENAI_AUTH_TOKEN` | 历史兼容项。新实现从数据库 `internal_model_proxy_settings` 读取全局 token。 |
| `test-agent.model-catalog.internal.api-key` | 空 | 企业内 token 的 yml 直配值；未配置时回退到 `TEST_AGENT_ICBC_OPENAI_TOKEN_ENV` 指向的环境变量。 |
| `TEST_AGENT_ICBC_OPENAI_UCID_HEADER_NAME` | `ucid` | 企业内 API 接收当前登录人统一认证号的 header 名；`internal` 模式 Run 前会把当前 `User.unifiedAuthId` 写入用户专属 opencode 进程的 provider headers，避免共用进程串号。 |
| `TEST_AGENT_ICBC_OPENAI_AUTH_MODE` | `auth-token` | 企业内调用鉴权头模式，默认写入 `Auth-Token`。 |
| `TEST_AGENT_INTERNAL_DEFAULT_MODEL` | `DeepSeek-V4-Flash-W8A8` | 企业内默认模型，前端模型切换会优先选中该模型。 |
`DatabaseMigrationRunner` 会在启动时执行 Flyway migration；固定 opencode node yml 配置已作废，应用不再从配置自动写入 `execution_nodes` 作为兼容 Run 路由来源。启用用户进程模型后，`BackendJavaProcessLifecycleRunner` 会在启动和拓扑变化时写入 `linux_servers`、`backend_java_processes`，并每 5 秒按 `linuxServerId` 写入 Redis Java 快照、服务器资源指标历史和 JVM 指标历史；`backendProcessId` 仅表示当前 Java 实例和拓扑连接字段，不再作为 Java 心跳或 JVM 历史的唯一键；`opencode-manager` WebSocket 注册会保留容器、manager 和连接持久拓扑，`managerHeartbeat` 每 5 秒经 WebSocket 写入 Redis manager 快照和容器资源指标历史，latest snapshot TTL 为 10 秒，历史指标保留近 48 小时。
