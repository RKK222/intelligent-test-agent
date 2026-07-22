# opencode-manager

## 工程定位

`opencode-manager` 是运行在 opencode worker 容器内的本地进程管理器，负责启动、停止、重启、健康检测容器内的用户专属 opencode server 进程。每个稳定 `linuxServerId` 只部署一个 worker，worker 容器名称只用于展示，不参与运行身份。manager 提供本地 CLI 和 `run` 长运行模式；`run` 只连接当前服务器上的 Java 后端 WebSocket，由该后端控制本服务器 manager。

## 构建与运行

```bash
cd opencode-manager
go test ./...
BUILD_VERSION="$(TZ=Asia/Shanghai date '+V%Y%m%d.%H%M%S')"
go build -ldflags "-X github.com/enterprise/test-agent/opencode-manager/internal/control.buildVersion=${BUILD_VERSION}" \
  -o bin/opencode-manager ./cmd/opencode-manager
```

`buildVersion` 必须在二进制构建时按北京时间注入，格式为 `VyyyyMMdd.HHmmss`；运行时环境变量不得覆盖。普通 `go run` 或未带 linker flag 的旧二进制保持空值，以兼容开发和滚动升级。

目标容器内必须能通过 `OPENCODE_BIN` 找到 opencode CLI，默认命令为 `opencode`。Windows 启动用户进程时执行：

```bash
XDG_DATA_HOME={common_parameters.OPENCODE_SESSION_DIR}/users/{unifiedAuthId} \
OPENCODE_CONFIG_DIR={XDG_DATA_HOME}/.testagent-runtime/current-public-config \
OPENCODE_REFERENCES_DIR={common_parameters.OPENCODE_REFERENCES_DIR} \
opencode serve --hostname 0.0.0.0 --port {port} --print-logs
```

Java 在启动命令下发前创建固定 `current-public-config` 软链接并默认指向本服务器 `OPENCODE_PUBLIC_CONFIG_DIR`；公共个人保存只切换本人链接，公共发布排空时再恢复共享链接。manager 只接收并保存显式 `command.configPath`，不创建或复制配置；健康旧进程的已有路径与本次显式路径不一致时拒绝幂等复用，要求平台停止后重新启动。`OPENCODE_REFERENCES_DIR` 不是 manager 自身环境变量。Java 后端在公共进程启动程序中按目标平台解析通用参数，并通过 WebSocket `command.environment` 传给 manager；manager 将收到的非空键值合并进子进程环境。滚动升级期间参数缺失不会阻断 opencode server 启动。该变量只对新启动的进程，以及由平台公共停止/启动程序完成的受管重启生效；已经运行的进程不会因引用资产初始化、同步或参数变化自动重启。

企业 Linux worker 中该兼容 CLI 由 Node 22 启动 OpenCode `1.17.8` server bundle，只实现 manager 实际依赖的 `--version` 与 `serve --hostname/--port/--cors/--print-logs` 接口，不使用上游 npm 包内嵌的 Bun 可执行文件。manager 的启动、健康探测、state 和停止语义保持不变。

> Windows 平台上若 `OPENCODE_BIN` 指向 PowerShell 包装脚本（`*.ps1`），manager 会自动改写为
> `powershell.exe -NoProfile -ExecutionPolicy Bypass -File <ps1> serve --hostname 0.0.0.0 --port {port} --print-logs`，
> 避免 `os/exec` 直接 fork `.ps1` 触发 `%1 is not a valid Win32 application`。该解析对扩展名大小写不敏感。

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
| `OPENCODE_MANAGER_BACKEND_PORT` | `8080`，manager 按 `.serverhost` 派生初始 WebSocket 入口时使用。 |
| `SYS_DATA_ROOT_DIR` | 可选；覆盖 manager 启动前读取 `.serverid/.serverhost` 的系统数据根目录。企业部署建议与 Java 通用参数保持一致，例如 `/data/testagent/data`。 |
| `OPENCODE_BIN` | `opencode` |
| `OPENCODE_MANAGER_STATE_DIR` | `/data/opencode/manager` |
| `OPENCODE_ALLOWED_CORS` | 空，多个 origin 用逗号分隔 |
| `OPENCODE_MANAGER_HEARTBEAT_INTERVAL` | `5s` |
| `OPENCODE_MANAGER_RECONNECT_INTERVAL` | `10s` |

`OPENCODE_MANAGER_LINUX_SERVER_ID` 和 `OPENCODE_MANAGER_SERVER_IP_FILE` 不再作为生产路径使用。非 Windows 环境的服务器身份必须来自 `SYS_DATA_ROOT_DIR/.serverid`，后端连接地址必须来自 `SYS_DATA_ROOT_DIR/.serverhost`；Go manager 启动前无法连接 Java 查询数据库，因此未配置 `SYS_DATA_ROOT_DIR` 环境变量时按系统通用参数的内置平台默认值派生：Linux `/data/.testagent/.serverid`、`/data/.testagent/.serverhost`，macOS `$HOME/.testagent/.serverid`、`$HOME/.testagent/.serverhost`。企业部署如果把系统数据根迁到 `/data/testagent/data`，需要同时设置 Java 通用参数和 manager 环境变量 `SYS_DATA_ROOT_DIR=/data/testagent/data`。展示名称 `containerName` 非 Windows 优先来自系统 hostname，其次来自 `/etc/hostname`；Windows 使用机器名。名称解析失败时启动失败，不接受人工 ID 兜底。Windows 本机开发态跳过 `.serverid` 文件等待，以机器名作为 `linuxServerId`，并探测本机非回环 IPv4 作为 `.serverhost` 等价地址。

`OPENCODE_MANAGER_CONTAINER_ID` 和 `OPENCODE_MANAGER_ID` 均不支持配置。manager 使用完整小写十六进制 SHA-256 自动生成稳定内部 ID：

```text
containerId = "ctr_" + SHA256("test-agent/opencode-container/v1\0" + linuxServerId)
managerId   = "mgr_" + SHA256("test-agent/opencode-manager/v1\0" + containerId)
```

两个 ID 都是 68 字符，前端、数据库、Redis 和路由均应把它们当作不透明字符串。只要 `.serverid` 不变，manager 重启、worker 改名或容器重建都不会改变身份；不同服务器即使 worker hostname 相同，只要 `TEST_AGENT_LINUX_SERVER_ID` 不同也会得到不同 ID。共享 PostgreSQL/Redis 范围内的 `TEST_AGENT_LINUX_SERVER_ID` 必须全局唯一且长期稳定，误配相同值会被视为同一台服务器。

## CLI

所有命令输出稳定 JSON，包含 `status`、`port`、`pid`、`baseUrl`、`sessionPath`、`configPath`、`message`、`traceId` 等字段。

`baseUrl` 的 host 固定来自 `.serverhost`，不会使用 `.serverid` 对应的 `linuxServerId` 拼接；例如 `.serverid=test-agent-backend-10-8-0-12`、`.serverhost=10.8.0.12`、端口 `4096` 时，结果必须是 `http://10.8.0.12:4096`。该地址会写入本地 state、命令结果和 manager 心跳，供 Java 间用户进程路由使用。

```bash
opencode-manager start --port 4096 --trace-id trace_1234567890abcdef
opencode-manager health --port 4096 --trace-id trace_1234567890abcdef
opencode-manager stop --port 4096 --trace-id trace_1234567890abcdef --timeout 5s
opencode-manager restart --port 4096 --trace-id trace_1234567890abcdef
opencode-manager list --trace-id trace_1234567890abcdef
```

`health` 先检查 PID 是否存在，再依次请求 `http://127.0.0.1:{port}/global/health` 和 `/global/config`。前者确认进程 HTTP 存活，后者确认 `OPENCODE_CONFIG_DIR` 中的公共配置符合当前 OpenCode 原生 schema；任一失败都返回 `UNHEALTHY`，不回退只能证明 HTTP 可访问的 `/doc`。例如 OpenCode 1.17.7 的额外技能目录配置必须写成 `"skills": {"paths": ["./skills"]}`，不能使用旧数组形式。

`start`、`stop` 和 `restart` 在 manager 生命周期锁内串行执行。`start` 对已经写入本地 state 且健康、端口/UCID/session/config 均一致的进程保持幂等：重复启动返回 `STARTED`、既有 PID 和 `processCreated=false`，不会再拉起第二个 opencode server；本次实际新建进程时返回 `processCreated=true`。已有平台 binding 的 Java 启动命令额外携带可选 `bindingRecovery=true`，表示按数据库原端口恢复而非新增调度，因此不受 `maxProcesses` 容量过滤；首次分配和端口迁移不携带该字段，仍执行原容量限制。旧 Java 缺字段时按首次分配处理。同一 UCID 已在其它端口托管时返回 `IDENTITY_ALREADY_MANAGED`，身份或路径配置不一致返回 `IDENTITY_CONFIG_MISMATCH`；目标端口超出当前池返回 `PORT_OUT_OF_RANGE`，被其它 state/身份或外部监听器占用返回 `PORT_CONFLICT`。若该端口已有匹配 state 但健康检查失败，`start` 返回普通失败，Java 必须先通过公共停止服务确认退出后再在原端口启动；除 `PORT_CONFLICT/PORT_OUT_OF_RANGE` 外的错误都不允许触发端口迁移。`stop` 对 state 存在但 OS 进程已结束的端口按幂等成功处理；SIGTERM 超时并发送 SIGKILL 后仍须再次确认 PID 已退出，未确认时保留 state 并返回失败。`list` 和心跳只清理端口与 PID 仍匹配当前 state 的陈旧记录，不能用旧心跳快照删除同端口 restart 刚写入的新 PID。

## WebSocket 控制面

长运行模式：

```bash
opencode-manager run
```

`run` 会先解析服务器身份和地址：非 Windows 读取 `SYS_DATA_ROOT_DIR/.serverid` 与 `.serverhost`，Windows 使用机器名和本机非回环 IPv4。manager 使用 `.serverhost` 和 `OPENCODE_MANAGER_BACKEND_PORT`（默认 `8080`）派生本服务器 Java WebSocket：`ws://{serverHost}:{port}/api/internal/platform/opencode-runtime/manager/ws`。Go manager 不再通过 HTTP 与 Java 后端交互，所有注册、配置拉取、心跳和命令控制都走这个 WebSocket，并使用 `Authorization: Bearer <OPENCODE_MANAGER_TOKEN>`；不得使用用户 JWT、普通 API token 或 opencode server 密钥。

多后端 Java 实例部署时，一个 manager 只连接 `.serverhost + OPENCODE_MANAGER_BACKEND_PORT` 推导出的本服务器 Java；同一服务器多个 Java 共享同一个 `.serverid`，Java 间路由会优先选择与 manager 已连接的实例。连接异常断开后，manager 按 `OPENCODE_MANAGER_RECONNECT_INTERVAL` 间隔无限重连，不设置总超时；重连成功并收到 `registered` 后会重新发送 `configRequest` 拉取运行配置。

WebSocket 文本帧为 JSON，协议版本固定 `opencode-manager.v1`。manager 会发送：

- `register`：连接建立后注册容器、端口池、容量、能力和 linker 注入的 `buildVersion`；新版能力清单显式声明 `stopOwned`。
- `configRequest`：收到 `registered` 后主动请求 Java 从 `common_parameters` 返回当前运行配置。后端 `configUpdate` 必须包含 `maxProcesses`、`sessionRoot`（来自 `OPENCODE_SESSION_DIR`）和 `configDir`（来自 `OPENCODE_PUBLIC_CONFIG_DIR`）；收到完整配置前，manager 拒绝 `start`/`restart`，不会用本地默认路径启动用户进程。Java 下发 `start` 命令时还会携带按用户生成的显式 `sessionPath`、可选 `unifiedAuthId`，已有 binding 恢复时再携带可选 `bindingRecovery=true`；manager 优先使用该路径作为 `XDG_DATA_HOME`，并校验显式统一认证号与稳定的 `.../users/{unifiedAuthId}` session 路径一致。旧 Java 未下发身份时，manager 会从该稳定 session 路径派生；本地 CLI 或旧命令帧连显式路径也未携带时，才按 `{sessionRoot}/{port}` 兼容派生。
- `managerHeartbeat`：每 5 秒通过本服务器 Java socket 上报当前进程数、已连接后端 ID、端口池、linker 注入的 `buildVersion`、容器 CPU/内存/磁盘 IO 指标、`metricsSource` 和本地 opencode server 进程明细，Java 写入 Redis latest snapshot，TTL 为 10 秒；资源指标同时追加到 Redis 48 小时历史 ZSET。进程明细可选携带 state 中的 `unifiedAuthId` 和 PID 存活标记 `managerStatus=PID_ALIVE`，旧 state/旧 manager 缺字段时省略；Java 只在 `SUPER_ADMIN` 运行管理 overview 中透传，不从启动命令解析身份。明细还包含安全展示用 `startCommand`，会展示 `XDG_DATA_HOME`、`OPENCODE_CONFIG_DIR`、`OPENCODE_REFERENCES_DIR`、`TEST_AGENT_INTERNAL_PROXY_BASE_URL`、`ENTERPRISE_UCID` 和 `opencode serve` 固定参数；值按 shell 单参数规则安全引用。`TEST_AGENT_INTERNAL_PROXY_API_KEY` 只能显示为 `<redacted>`，其它未列入展示白名单的透传环境变量完全不进入 `startCommand`，但仍会进入实际子进程环境。旧 state 缺字段时优先使用 state 内保存的 `sessionPath` 派生，仍缺失时才按当前配置和端口派生。心跳生成前会清理 PID 已不存在的 stale state；`configUpdate` 成功应用以及 `start`、`stop`、`restart` 成功后还会立即补发一次心跳，加速 Redis latest snapshot 收敛。
- `commandResult`：执行后端 `command` 后返回状态、端口、PID、路径、traceId 和可选 `errorCode`。`start` 结果始终显式携带 `processCreated`，fresh start 为 `true`、健康幂等复用为 `false`，避免 Java 在 assignment 冲突后误清理复用实例；旧 manager 缺字段时 Java 按未知处理且不补偿。`start` 发现目标服务器 `OPENCODE_PUBLIC_CONFIG_DIR` 未初始化时返回 `FAILED`、`errorCode=OPENCODE_UNAVAILABLE`，`message` 包含目标服务器和 manager 实际检查的配置目录，并提示联系超级管理员进入“系统管理 → 配置管理 → opencode公共配置管理”完成初始化。

manager 当前接受的命令为 `start`、`health`、`stop`、`restart`、`stopOwned`。命令最终复用 `internal/process`，不会重新实现 opencode 生命周期逻辑。`stopOwned` 供所有 tracked 停止与启动冲突补偿使用：manager 在同一个生命周期锁内读取端口 state，只有 state 的统一认证号和 PID 同时等于命令携带的 expected UCID/PID 才执行 terminate/kill/delete；旧 state 仅允许从规范 `users/{ucid}` session 路径安全恢复身份，无法验证或任一不匹配时返回稳定 `PROCESS_OWNERSHIP_MISMATCH`，且在拒绝前不发送信号、不删除 state。该错误与日志不包含 UCID。UCID+PID 栅栏无法区分极窄窗口内同一 UCID、同一 PID 的新代次（包括 PID 数值复用或同实例并发接管）；彻底区分仍需后续协议增加不可复用的 incarnation token。`restart` 会先读取本地 state 中保存的 `sessionPath` 和统一认证号，停止后再用同一路径、同一身份启动，避免重启时退回按端口派生目录；每次重新拉起仍使用新的启动时间创建独立日志文件。总命令超时同时覆盖停止、重新启动和回包，停止阶段最多使用一半预算，避免新进程实际已启动但 Java 等待端先返回超时。超级管理员运行管理页的“重启/停止”按钮先调用 Java 后端 HTTP API，再由后端通过已认证的 manager WebSocket 转发；有平台记录的 tracked 进程由 Java 使用 `stopOwned`，只有无平台记录的管理员操作保留端口 `restart/stop`，浏览器不直连 manager 或 opencode server。

## 状态与日志

- 本地状态文件：`{OPENCODE_MANAGER_STATE_DIR}/processes/{port}.json`。
- manager 自身日志：`{OPENCODE_MANAGER_STATE_DIR}/logs/manager.log`；失败、拒绝、断连等异常行额外写入 `{OPENCODE_MANAGER_STATE_DIR}/logs/manager-error.log`。
- 受管用户 opencode server 日志：`{OPENCODE_MANAGER_STATE_DIR}/logs/{safeUnifiedAuthId}-{yyyyMMddTHHmmss.nnnnnnnnnZ}-{port}.log`。例如 `/data/testagent/data/agent-opencode/manager/worker/logs/DEV_888888888-20260721T081530.123456789Z-4096.log`。启动时间使用 UTC，stdout/stderr 共同追加到本次启动的同一个文件；停止后重启会生成新文件，不覆盖同端口的历史启动日志。
- `safeUnifiedAuthId` 对 ASCII 字母、数字、`_`、`-` 原样保留，其它 UTF-8 字节编码为大写 `%HH`；编码结果过长时保留有界前缀并追加完整 SHA-256，避免路径穿越、名称冲突和文件名超限。该转换只保证文件名安全，不代表匿名化。
- 未携带统一认证号且无法从稳定 `.../users/{unifiedAuthId}` session 路径派生的本地 CLI/历史命令仍写 `{port}.log`。升级不会迁移或删除已有 `{port}.log`，排查历史进程时应继续保留该兼容路径。
- 日志只承载 manager 生命周期、控制命令摘要和 opencode server stdout/stderr，不额外写入 Authorization、token 或用户完整输入；manager 控制命令日志按 `traceId/command/port/status/durationMs` 记录，便于和 Java 后端日志关联。
- 资源采集使用 `gopsutil/v4` 与 `opencontainers/cgroups`。Linux 生产态优先按当前进程所在 cgroup v2/v1 子路径采集容器 CPU、内存和磁盘 IO，`metricsSource=cgroup`；cgroup 不可读时降级采集当前 manager 进程 CPU/内存，`metricsSource=process`。macOS/Windows 开发态使用 gopsutil 采集当前 manager 进程 CPU/内存，磁盘 IO 为空；其他系统或完全不可采集时 `metricsSource=unavailable`。采集失败只影响指标字段，不阻断 WebSocket 心跳或控制命令。
- 引入 `opencontainers/cgroups` 后 Go module 基线为 Go 1.23；构建环境需使用 Go 1.23 或更高版本。

端口池要求：

- 每个稳定 `linuxServerId` 只部署一个 worker；动态扩缩容通过增加或减少服务器完成，不在同一服务器并行启动多个对等 worker。
- 最大进程数来自通用参数表中的全局 `OPENCODE_MANAGER_MAX_PROCESSES`（`platform=all`），manager 收到后按自身端口池容量 clamp（`<1` 拒绝、超上限 clamp 到容量），并通过即时心跳把生效值写回运行管理 Redis 快照。该值缺失或非法时后端不下发可启动配置，manager 保持未 ready 并拒绝启动用户进程。
- 用户进程 session 与公共配置源来自 `common_parameters.OPENCODE_SESSION_DIR` 和 `common_parameters.OPENCODE_PUBLIC_CONFIG_DIR`。Java 通过用户仓储解析统一认证号，把 session 固定为 `{OPENCODE_SESSION_DIR}/users/{unifiedAuthId}`，把有效配置路径固定为 `{sessionPath}/.testagent-runtime/current-public-config`，先维护软链接再分别通过 `start.sessionPath/start.configPath/start.unifiedAuthId` 下发给 manager；manager 自动创建显式 session 目录但不改配置链接。首次完整 `configUpdate` 必须带齐公共源路径；后续最大进程数刷新允许只带 `maxProcesses`，路径字段为空表示沿用已生效路径。非 Windows manager 使用 Java 当前平台解析到的 Linux 参数；Windows 若无法创建受管软链接会由 Java 明确拒绝，不降级复制。`OPENCODE_SESSION_ROOT`、`OPENCODE_CONFIG_DIR`、`OPENCODE_MANAGER_MAX_PROCESSES` 不再是 `run` 模式主路径。OpenCode 会合并用户全局配置、`OPENCODE_CONFIG_DIR` 和请求工作区 `.opencode`，企业部署必须保证运行用户 `~/.config/opencode/config.json`、`opencode.json`、`opencode.jsonc` 不维护模型或供应商，最多保留只含 `$schema` 的空配置；公共配置 Git 库 `{OPENCODE_PUBLIC_CONFIG_GIT_ROOT}/opencode/opencode.jsonc` 是模型、供应商和内部代理 provider 的事实源。manager 执行 `start` 时检查本次实际 `configPath` 必须可解析为存在、非空且可读的目录；失败消息写明当前 `linuxServerId` 和实际路径，不会自动创建配置。未携带 `sessionPath/configPath` 的本地 CLI 或旧命令帧仍分别按 `{OPENCODE_SESSION_DIR}/{port}` 和公共共享目录兼容。
- 企业 Node launcher 禁止 OpenCode 启动期联网安装配置依赖，并把 programs 内锁定的 Tool runtime 暴露给公共 `tools/` 与工作区 `.opencode/tools/`。基线包含 `@opencode-ai/plugin`、`@opencode-ai/sdk`、`effect`、`zod` 及其传递依赖；新增其它第三方 import 需要重新构建和部署 programs/worker，manager 不负责 npm 下载或业务依赖安装。
- Java 后端写入的 `.serverid` 和 manager 注册上报的 `linuxServerId` 必须是同一稳定服务器身份；`.serverhost` 只用于连接 Java 和生成用户进程访问地址。`containerId/managerId` 均由该稳定身份自动派生，`containerName` 才是容器或本机的可读展示名称。

## 边界

- manager 不访问后端数据库；在线心跳只经 Java 写入 Redis，数据库只保留持久拓扑和用户进程业务数据。
- manager 日志不得输出 token、Authorization、Cookie、统一认证号、用户完整输入或完整 prompt。用户进程日志文件名虽然经过路径安全编码，仍属于含身份信息的数据；不得把完整文件名复制到普通生命周期日志、错误响应或未脱敏的外部工单。
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
