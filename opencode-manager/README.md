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
| `OPENCODE_MANAGER_CONTAINER_ID` | 当前容器 ID，用于注册和命令路由。 |
| `OPENCODE_MANAGER_LINUX_SERVER_ID` | Linux 服务器 ID，当前约定为服务器 IP。 |
| `OPENCODE_MANAGER_PORT_START` | 当前容器可管理端口池起始端口。 |
| `OPENCODE_MANAGER_PORT_END` | 当前容器可管理端口池结束端口。 |
| `OPENCODE_MANAGER_MAX_PROCESSES` | 当前容器最大 opencode server 进程数，不得超过端口数量。 |
| `OPENCODE_MANAGER_ID` | 当前 manager ID，必须以 `mgr_` 开头。 |
| `OPENCODE_MANAGER_BACKEND_DISCOVERY_URL` | 后端 discovery API，例如 `http://10.8.0.21:8080/api/internal/platform/opencode-runtime/manager-backends`。 |
| `OPENCODE_MANAGER_TOKEN` | 独立 manager token，用于 discovery API 和 WebSocket 鉴权。 |

可选环境变量：

| 变量 | 默认值 |
|---|---|
| `OPENCODE_BIN` | `opencode` |
| `OPENCODE_MANAGER_STATE_DIR` | `/data/opencode/manager` |
| `OPENCODE_SESSION_ROOT` | `/data/opencode/session` |
| `OPENCODE_CONFIG_DIR` | `/data/opencode/.config/opencode/` |
| `OPENCODE_ALLOWED_CORS` | 空，多个 origin 用逗号分隔 |
| `OPENCODE_MANAGER_DISCOVERY_INTERVAL` | `10s` |
| `OPENCODE_MANAGER_HEARTBEAT_INTERVAL` | `10s` |
| `OPENCODE_MANAGER_RECONNECT_INTERVAL` | `5s` |

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

## WebSocket 控制面

长运行模式：

```bash
opencode-manager run
```

`run` 会使用 `OPENCODE_MANAGER_BACKEND_DISCOVERY_URL` 拉取后端实例列表，并对每个实例连接其 `webSocketUrl`。HTTP 和 WebSocket 都使用 `Authorization: Bearer <OPENCODE_MANAGER_TOKEN>`，不得使用用户 JWT、普通 API token 或 opencode server 密钥。

多后端 Java 实例部署时，`OPENCODE_MANAGER_BACKEND_DISCOVERY_URL` 可以指向任一 manager 可访问的后端直连地址或内部发现入口。discovery 返回的每个 `webSocketUrl` 必须是容器网络可达的实例直连地址；如果后端只配置负载均衡地址，manager 无法与所有实例建立全连接。

WebSocket 文本帧为 JSON，协议版本固定 `opencode-manager.v1`。manager 会发送：

- `register`：连接建立后注册容器、端口池、容量和能力。
- `heartbeat`：按间隔刷新当前进程数和连接状态。
- `commandResult`：执行后端 `command` 后返回状态、端口、PID、路径和 traceId。

manager 当前接受的命令为 `start`、`health`、`stop`、`restart`。命令最终复用 `internal/process`，不会重新实现 opencode 生命周期逻辑。

## 状态与日志

- 本地状态文件：`{OPENCODE_MANAGER_STATE_DIR}/processes/{port}.json`。
- opencode server 日志：`{OPENCODE_MANAGER_STATE_DIR}/logs/{port}.log`。
- 日志只承载 opencode server stdout/stderr，不额外写入 Authorization、token 或用户完整输入。

端口池要求：

- 同一 Linux 服务器上的多个 opencode 容器端口范围不能重叠，因为后端按 `http://{linuxServerIp}:{port}` 访问用户进程。
- `OPENCODE_MANAGER_MAX_PROCESSES` 不能超过端口数量；容量不足时应新增容器或扩展端口池，而不是复用已被其他容器占用的端口。
- `OPENCODE_MANAGER_LINUX_SERVER_ID` 必须与后端 `TEST_AGENT_LINUX_SERVER_ID` 使用同一 IPv4 字符串，确保健康异常重建仍发生在用户 session 所在 Linux 服务器。

## 边界

- manager 不访问后端数据库；拓扑和进程状态持久化由后端 Java 写入数据库。
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

该脚本只检查后端 health、manager discovery 和超级管理员运行管理 overview，不会向 manager 下发 `start`、`stop`、`restart` 或 `health` 命令。完整多服务器验收清单见 `docs/deployment/backend.md`。
