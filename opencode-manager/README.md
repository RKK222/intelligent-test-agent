# opencode-manager

## 工程定位

`opencode-manager` 是运行在每个 opencode 容器内的本地进程管理器，负责启动、停止、重启、健康检测容器内的用户专属 opencode server 进程。本批次只提供 CLI 和可复用 Go library，不连接后端 Java socket，也不写数据库。

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
| `OPENCODE_MANAGER_CONTAINER_ID` | 当前容器 ID，后续批次用于注册和命令路由。 |
| `OPENCODE_MANAGER_LINUX_SERVER_ID` | Linux 服务器 ID，当前约定为服务器 IP。 |
| `OPENCODE_MANAGER_PORT_START` | 当前容器可管理端口池起始端口。 |
| `OPENCODE_MANAGER_PORT_END` | 当前容器可管理端口池结束端口。 |
| `OPENCODE_MANAGER_MAX_PROCESSES` | 当前容器最大 opencode server 进程数，不得超过端口数量。 |

可选环境变量：

| 变量 | 默认值 |
|---|---|
| `OPENCODE_BIN` | `opencode` |
| `OPENCODE_MANAGER_STATE_DIR` | `/data/opencode/manager` |
| `OPENCODE_SESSION_ROOT` | `/data/opencode/session` |
| `OPENCODE_CONFIG_DIR` | `/data/opencode/.config/opencode/` |
| `OPENCODE_ALLOWED_CORS` | 空，多个 origin 用逗号分隔 |

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

## 状态与日志

- 本地状态文件：`{OPENCODE_MANAGER_STATE_DIR}/processes/{port}.json`。
- opencode server 日志：`{OPENCODE_MANAGER_STATE_DIR}/logs/{port}.log`。
- 日志只承载 opencode server stdout/stderr，不额外写入 Authorization、token 或用户完整输入。

## 边界

- 本批不实现后端 socket、注册、心跳、命令分发或数据库写入。
- opencode server 默认监听 `0.0.0.0:{port}`，不设置 `OPENCODE_SERVER_PASSWORD`；生产必须依赖容器网络、主机防火墙或网关限制访问面。
- 后续批次 4 的 socket 控制面应复用 `internal/process`，而不是重新实现进程生命周期。
