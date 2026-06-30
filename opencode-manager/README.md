# opencode-manager

## 工程定位

`opencode-manager` 是运行在每个 opencode 容器内的本地进程管理器，负责启动、停止、重启、健康检测容器内的用户专属 opencode server 进程。它提供本地 CLI 和 `run` 长运行模式；`run` 只连接当前服务器上的 Java 后端 WebSocket，由该后端控制本服务器 manager。

## 构建与运行

```bash
cd opencode-manager
go test ./...
go build -o bin/opencode-manager ./cmd/opencode-manager
```

目标容器内必须能通过 `OPENCODE_BIN` 找到 opencode CLI，默认命令为 `opencode`。启动用户进程时会执行：

```bash
XDG_DATA_HOME={common_parameters.OPENCODE_SESSION_DIR}/{port} \
OPENCODE_CONFIG_DIR={common_parameters.OPENCODE_PUBLIC_CONFIG_DIR} \
opencode serve --hostname 0.0.0.0 --port {port} --print-logs
```

## 配置

opencode-manager 不允许随意新增环境变量。新增前必须先确认该值无法通过 Java 后端 `common_parameters`、控制面 `configUpdate`、既有 manager 环境变量或进程启动参数表达；只有 manager 启动前必须由宿主环境提供的身份、端口池、token、二进制路径、状态目录或连接引导参数才允许作为环境变量。确需新增时，必须同步更新本 README、`docs/deployment/backend.md`、配置解析测试和本地启动脚本/示例，说明默认值、缺失时行为以及是否敏感。

必填环境变量：

| 变量 | 说明 |
|---|---|
| `OPENCODE_MANAGER_PORT_START` | 当前容器可管理端口池起始端口。 |
| `OPENCODE_MANAGER_PORT_END` | 当前容器可管理端口池结束端口。 |
| `OPENCODE_MANAGER_TOKEN` | 独立 manager token，用于 WebSocket 控制面鉴权。 |

可选环境变量：

| 变量 | 默认值/说明 |
|---|---|
| `OPENCODE_MANAGER_CONTAINER_ID` | 仅作为非 Windows 容器 ID 的最后兜底。非 Windows 先读系统 hostname，再读 `/etc/hostname`，两者为空时才读该环境变量；不再读取 `HOSTNAME` 环境变量。Windows 直接读机器名，不读取该变量。最终为空则启动失败。 |
| `OPENCODE_MANAGER_BACKEND_PORT` | `8080`，manager 按服务器 IPv4 派生初始 WebSocket 入口时使用。 |
| `OPENCODE_BIN` | `opencode` |
| `OPENCODE_MANAGER_STATE_DIR` | `/data/opencode/manager` |
| `OPENCODE_ALLOWED_CORS` | 空，多个 origin 用逗号分隔 |
| `OPENCODE_MANAGER_HEARTBEAT_INTERVAL` | `5s` |
| `OPENCODE_MANAGER_RECONNECT_INTERVAL` | `10s` |

`OPENCODE_MANAGER_LINUX_SERVER_ID` 和 `OPENCODE_MANAGER_SERVER_IP_FILE` 不再作为生产路径使用。非 Windows 环境的服务器身份必须来自 `SYS_DATA_ROOT_DIR/.serverip` 文件；Go manager 启动前无法连接 Java 查询数据库，因此按系统通用参数的内置平台默认值派生：Linux `/data/.testagent/.serverip`，macOS `$HOME/.testagent/.serverip`。`containerId` 优先来自系统 hostname，其次来自 `/etc/hostname`，最后才使用 `OPENCODE_MANAGER_CONTAINER_ID`。Windows 本机开发态跳过文件等待，直接探测本机非回环 IPv4，并用机器名作为 `containerId`。

`OPENCODE_MANAGER_ID` 不再作为环境变量配置。manager 启动时会按 `mgr_<normalized_container_id>_<normalized_manager_process_name>` 派生内部 `managerId`，其中管理进程逻辑名固定为 `opencode-manager`，例如容器名 `kakadeMacBook-Pro.local` 会派生为 `mgr_kakadeMacBook_Pro_local_opencode_manager`。运行管理、Redis 快照和数据库拓扑中的 `managerId` 仍是内部协议 ID，唯一性来自同一共享 Redis 集群内的容器名称加管理进程名称；一个容器只运行一个 `opencode-manager`。

## CLI

所有命令输出稳定 JSON，包含 `status`、`port`、`pid`、`baseUrl`、`sessionPath`、`configPath`、`message`、`traceId` 等字段。

```bash
opencode-manager start --port 4096 --trace-id trace_1234567890abcdef
opencode-manager health --port 4096 --trace-id trace_1234567890abcdef
opencode-manager stop --port 4096 --trace-id trace_1234567890abcdef --timeout 5s
opencode-manager restart --port 4096 --trace-id trace_1234567890abcdef
opencode-manager list --trace-id trace_1234567890abcdef
```

`health` 先检查 PID 是否存在，再请求 `http://127.0.0.1:{port}/global/health`，失败时回退 `/doc`。

`start` 对已经写入本地 state 且健康的端口保持幂等：重复启动同一端口会返回 `STARTED` 和既有 PID，不会再拉起第二个 opencode server。若该端口已有 state 但健康检查失败，`start` 返回 `FAILED`，需要先用 `health`、`restart` 或 `stop` 排查/清理。`stop` 对 state 存在但 OS 进程已结束的端口按幂等成功处理，会删除本地 state 并返回 `STOPPED`；`list` 和心跳会过滤并清理 PID 已不存在的 stale state，避免死进程继续占用容量或展示在运行管理明细中。

## WebSocket 控制面

长运行模式：

```bash
opencode-manager run
```

`run` 会先解析服务器 IPv4：非 Windows 读取 `SYS_DATA_ROOT_DIR/.serverip`，Windows 直接探测本机非回环 IPv4。manager 使用该服务器 IPv4 和 `OPENCODE_MANAGER_BACKEND_PORT`（默认 `8080`）派生本服务器 Java WebSocket：`ws://{serverIp}:{port}/api/internal/platform/opencode-runtime/manager/ws`。Go manager 不再通过 HTTP 与 Java 后端交互，所有注册、配置拉取、心跳和命令控制都走这个 WebSocket，并使用 `Authorization: Bearer <OPENCODE_MANAGER_TOKEN>`；不得使用用户 JWT、普通 API token 或 opencode server 密钥。

多后端 Java 实例部署时，一个 manager 仍只连接 `.serverip + OPENCODE_MANAGER_BACKEND_PORT` 推导出的本服务器 Java；同一服务器上多个 manager 会分别连接该服务器 Java。连接异常断开后，manager 按 `OPENCODE_MANAGER_RECONNECT_INTERVAL` 间隔无限重连，不设置总超时；重连成功并收到 `registered` 后会重新发送 `configRequest` 拉取运行配置。

WebSocket 文本帧为 JSON，协议版本固定 `opencode-manager.v1`。manager 会发送：

- `register`：连接建立后注册容器、端口池、容量和能力。
- `configRequest`：收到 `registered` 后主动请求 Java 从 `common_parameters` 返回当前运行配置。后端 `configUpdate` 必须包含 `maxProcesses`、`sessionRoot`（来自 `OPENCODE_SESSION_DIR`）和 `configDir`（来自 `OPENCODE_PUBLIC_CONFIG_DIR`）；收到完整配置前，manager 拒绝 `start`/`restart`，不会用本地默认路径启动用户进程。
- `managerHeartbeat`：每 5 秒通过本服务器 Java socket 上报当前进程数、已连接后端 ID、端口池、容器 CPU/内存/磁盘 IO 指标、`metricsSource` 和本地 opencode server 进程明细，Java 写入 Redis latest snapshot，TTL 为 10 秒；资源指标同时追加到 Redis 48 小时历史 ZSET。进程明细包含安全展示用 `startCommand`，只拼出 `XDG_DATA_HOME`、`OPENCODE_CONFIG_DIR` 和 `opencode serve` 固定参数；旧 state 缺字段时按当前配置和端口派生。心跳生成前会清理 PID 已不存在的 stale state；`configUpdate` 成功应用以及 `start`、`stop`、`restart` 成功后还会立即补发一次心跳，加速 Redis latest snapshot 收敛。
- `commandResult`：执行后端 `command` 后返回状态、端口、PID、路径、traceId 和可选 `errorCode`。`start` 发现目标服务器 `OPENCODE_PUBLIC_CONFIG_DIR` 未初始化时返回 `FAILED`、`errorCode=OPENCODE_UNAVAILABLE`，`message` 包含目标服务器和 manager 实际检查的配置目录，并提示联系超级管理员进入“系统管理 → 配置管理 → opencode公共配置管理”完成初始化。

manager 当前接受的命令为 `start`、`health`、`stop`、`restart`。命令最终复用 `internal/process`，不会重新实现 opencode 生命周期逻辑。超级管理员运行管理页的“重启/停止”按钮先调用 Java 后端 HTTP API，再由后端通过已认证的 manager WebSocket 转发 `restart`/`stop`，浏览器不直连 manager 或 opencode server。

## 状态与日志

- 本地状态文件：`{OPENCODE_MANAGER_STATE_DIR}/processes/{port}.json`。
- opencode server 日志：`{OPENCODE_MANAGER_STATE_DIR}/logs/{port}.log`。
- 日志只承载 opencode server stdout/stderr，不额外写入 Authorization、token 或用户完整输入。
- 资源采集使用 `gopsutil/v4` 与 `opencontainers/cgroups`。Linux 生产态优先按当前进程所在 cgroup v2/v1 子路径采集容器 CPU、内存和磁盘 IO，`metricsSource=cgroup`；cgroup 不可读时降级采集当前 manager 进程 CPU/内存，`metricsSource=process`。macOS/Windows 开发态使用 gopsutil 采集当前 manager 进程 CPU/内存，磁盘 IO 为空；其他系统或完全不可采集时 `metricsSource=unavailable`。采集失败只影响指标字段，不阻断 WebSocket 心跳或控制命令。
- 引入 `opencontainers/cgroups` 后 Go module 基线为 Go 1.23；构建环境需使用 Go 1.23 或更高版本。

端口池要求：

- 同一 Linux 服务器上的多个 opencode 容器端口范围不能重叠，因为后端按 `http://{linuxServerIp}:{port}` 访问用户进程。
- 最大进程数来自通用参数表中的全局 `OPENCODE_MANAGER_MAX_PROCESSES`（`platform=all`），manager 收到后按自身端口池容量 clamp（`<1` 拒绝、超上限 clamp 到容量），并通过即时心跳把生效值写回运行管理 Redis 快照。该值缺失或非法时后端不下发可启动配置，manager 保持未 ready 并拒绝启动用户进程。
- 用户进程 session/config 路径来自 `common_parameters.OPENCODE_SESSION_DIR` 和 `common_parameters.OPENCODE_PUBLIC_CONFIG_DIR`。首次完整 `configUpdate` 必须带齐路径；后续最大进程数刷新允许只带 `maxProcesses`，路径字段为空表示沿用已生效路径。非 Windows manager 使用 Java 当前平台解析到的 Linux 参数；Windows 本机开发态使用 Windows 参数。`OPENCODE_SESSION_ROOT`、`OPENCODE_CONFIG_DIR`、`OPENCODE_MANAGER_MAX_PROCESSES` 不再是 `run` 模式主路径。manager 执行 `start` 时会在本机检查 `OPENCODE_PUBLIC_CONFIG_DIR` 必须已存在、是目录且非空；缺失、空目录、非目录或不可读都会失败，返回消息会写明当前 `linuxServerId` 和实际检查目录，不会自动创建 config 目录、不会创建 session、不会启动 opencode server。`OPENCODE_SESSION_DIR` 对应端口 session 目录仍由 manager 自动创建。
- Java 后端写入的 `.serverip` 和 manager 注册上报的 `linuxServerId` 必须是同一服务器 IPv4；`containerId` 只标识容器或本机开发实例。

## 边界

- manager 不访问后端数据库；在线心跳只经 Java 写入 Redis，数据库只保留持久拓扑和用户进程业务数据。
- manager 日志不得输出 token、Authorization、Cookie、用户完整输入或完整 prompt。
- opencode server 默认监听 `0.0.0.0:{port}`，不设置 `OPENCODE_SERVER_PASSWORD`；生产必须依赖容器网络、主机防火墙或网关限制访问面。

## 部署验收

启动后可从仓库根目录执行只读 smoke check：

```bash
tools/verify-opencode-process-deployment.sh \
  --backend-url http://<backend-or-lb>:8080 \
  --manager-token <manager-control-token> \
  --auth-token <super-admin-user-jwt>
```

该脚本只检查后端 health、manager 诊断端点和超级管理员运行管理 overview，不会向 manager 下发 `start`、`stop`、`restart` 或 `health` 命令。完整多服务器验收清单见 `docs/deployment/backend.md`。
