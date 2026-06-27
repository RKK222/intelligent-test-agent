# opencode-manager

## 工程定位

`opencode-manager` 是运行在每个 opencode 容器内的本地进程管理器，负责启动、停止、重启、健康检测容器内的用户专属 opencode server 进程。它提供本地 CLI 和 `run` 长运行模式；`run` 会发现所有 READY 后端实例，并与每个实例建立 WebSocket JSON 控制面连接。

## 构建与运行

```bash
cd opencode-manager
go test ./...
go build -o bin/opencode-manager ./cmd/opencode-manager
```

目标容器内必须能通过 `OPENCODE_BIN` 找到 opencode CLI，默认命令为 `opencode`。启动用户进程时会执行：

```bash
XDG_DATA_HOME=/data/opencode/session/{port} \
OPENCODE_CONFIG_DIR=/data/opencode/.config/opencode/ \
opencode serve --hostname 0.0.0.0 --port {port} --print-logs
```

## 配置

必填环境变量：

| 变量 | 说明 |
|---|---|
| `OPENCODE_MANAGER_PORT_START` | 当前容器可管理端口池起始端口。 |
| `OPENCODE_MANAGER_PORT_END` | 当前容器可管理端口池结束端口。 |
| `OPENCODE_MANAGER_MAX_PROCESSES` | 当前容器最大 opencode server 进程数，不得超过端口数量。 |
| `OPENCODE_MANAGER_ID` | 当前 manager ID，必须以 `mgr_` 开头。 |
| `OPENCODE_MANAGER_TOKEN` | 独立 manager token，用于 WebSocket 控制面鉴权。 |

可选环境变量：

| 变量 | 默认值/说明 |
|---|---|
| `OPENCODE_MANAGER_CONTAINER_ID` | 未配置时 Linux/Unix 优先读 `/etc/hostname`，再读 `HOSTNAME`；Windows 读机器名。最终为空则启动失败。 |
| `OPENCODE_MANAGER_SERVER_IP_FILE` | 非 Windows 默认 `/data/.testagent/.serverip`。启动时读取单行服务器 IPv4，文件不存在每 1 秒重试，最多 30 秒。 |
| `OPENCODE_MANAGER_BACKEND_PORT` | `8080`，manager 按服务器 IPv4 派生初始 WebSocket 入口时使用。 |
| `OPENCODE_BIN` | `opencode` |
| `OPENCODE_MANAGER_STATE_DIR` | `/data/opencode/manager` |
| `OPENCODE_SESSION_ROOT` | `/data/opencode/session` |
| `OPENCODE_CONFIG_DIR` | `/data/opencode/.config/opencode/` |
| `OPENCODE_ALLOWED_CORS` | 空，多个 origin 用逗号分隔 |
| `OPENCODE_MANAGER_DISCOVERY_INTERVAL` | `10s`，通过已连接 socket 询问存活 Java 后端列表的间隔。 |
| `OPENCODE_MANAGER_HEARTBEAT_INTERVAL` | `5s` |
| `OPENCODE_MANAGER_RECONNECT_INTERVAL` | `10s` |

`OPENCODE_MANAGER_LINUX_SERVER_ID` 不再作为生产路径使用。非 Windows 环境的服务器身份必须来自 `.serverip` 文件；Windows 本机开发态跳过文件等待，直接探测本机非回环 IPv4，并用机器名作为 `containerId`。

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

`start` 对已经写入本地 state 且健康的端口保持幂等：重复启动同一端口会返回 `STARTED` 和既有 PID，不会再拉起第二个 opencode server。若该端口已有 state 但健康检查失败，`start` 返回 `FAILED`，需要先用 `health`、`restart` 或 `stop` 排查/清理。

## WebSocket 控制面

长运行模式：

```bash
opencode-manager run
```

`run` 会先解析服务器 IPv4：非 Windows 读取 `OPENCODE_MANAGER_SERVER_IP_FILE`，Windows 直接探测本机非回环 IPv4。manager 使用该服务器 IPv4 和 `OPENCODE_MANAGER_BACKEND_PORT`（默认 `8080`）派生初始 WebSocket：`ws://{serverIp}:{port}/api/internal/platform/opencode-runtime/manager/ws`。Go manager 不再通过 HTTP 与 Java 后端交互，所有注册、心跳、后端列表发现和命令控制都走 WebSocket，并使用 `Authorization: Bearer <OPENCODE_MANAGER_TOKEN>`；不得使用用户 JWT、普通 API token 或 opencode server 密钥。

多后端 Java 实例部署时，manager 先连接 seed WebSocket；有连接后每 10 秒随机选择一个已连接 socket 发送 `backendListRequest`，Java 从 Redis 返回当前存活后端实例的 `webSocketUrl`，manager 自动连接尚未连接的实例。当所有 Java 连接都断开时，manager 每 10 秒按启动时的 `.serverip + backend port` 方式重连 seed WebSocket，不设总超时。

WebSocket 文本帧为 JSON，协议版本固定 `opencode-manager.v1`。manager 会发送：

- `register`：连接建立后注册容器、端口池、容量和能力。
- `managerHeartbeat`：每 5 秒通过任一已连接 socket 上报当前进程数、已连接后端 ID、端口池、容器 CPU/内存/磁盘 IO 指标、`metricsSource` 和本地 opencode server 进程明细，Java 写入 Redis latest snapshot，TTL 为 10 秒；资源指标同时追加到 Redis 48 小时历史 ZSET。
- `backendListRequest`：每 10 秒通过任一已连接 socket 请求当前存活 Java 后端列表。
- `commandResult`：执行后端 `command` 后返回状态、端口、PID、路径和 traceId。

manager 当前接受的命令为 `start`、`health`、`stop`、`restart`。命令最终复用 `internal/process`，不会重新实现 opencode 生命周期逻辑。

## 状态与日志

- 本地状态文件：`{OPENCODE_MANAGER_STATE_DIR}/processes/{port}.json`。
- opencode server 日志：`{OPENCODE_MANAGER_STATE_DIR}/logs/{port}.log`。
- 日志只承载 opencode server stdout/stderr，不额外写入 Authorization、token 或用户完整输入。
- 资源采集使用 `gopsutil/v4` 与 `opencontainers/cgroups`。Linux 生产态优先按当前进程所在 cgroup v2/v1 子路径采集容器 CPU、内存和磁盘 IO，`metricsSource=cgroup`；cgroup 不可读时降级采集当前 manager 进程 CPU/内存，`metricsSource=process`。macOS/Windows 开发态使用 gopsutil 采集当前 manager 进程 CPU/内存，磁盘 IO 为空；其他系统或完全不可采集时 `metricsSource=unavailable`。采集失败只影响指标字段，不阻断 WebSocket 心跳或控制命令。
- 引入 `opencontainers/cgroups` 后 Go module 基线为 Go 1.23；构建环境需使用 Go 1.23 或更高版本。

端口池要求：

- 同一 Linux 服务器上的多个 opencode 容器端口范围不能重叠，因为后端按 `http://{linuxServerIp}:{port}` 访问用户进程。
- `OPENCODE_MANAGER_MAX_PROCESSES` 不能超过端口数量；容量不足时应新增容器或扩展端口池，而不是复用已被其他容器占用的端口。
- `OPENCODE_MANAGER_MAX_PROCESSES` 仅作启动兜底默认；manager 连上后端后，后端通过 `configUpdate` 控制帧下发通用参数表中的全局 `OPENCODE_MANAGER_MAX_PROCESSES`（`platform=all`）并热更新，下发值按本容器端口池容量 clamp（`<1` 拒绝、超上限 clamp 到容量）。前端修改该参数后后端经控制面广播给所有 manager；后端不可达时继续使用 env 兜底值。
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
