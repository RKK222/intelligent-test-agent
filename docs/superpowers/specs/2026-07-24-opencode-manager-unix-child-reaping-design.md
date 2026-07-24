# OpenCode Manager Unix 子进程回收设计

## 背景与根因

超级管理员在运行管理中停止平台已登记的 OpenCode 进程时，Java 按既有安全链路调用 `OpencodeProcessStopService`，先取得 manager 最新 PID 和权威 UCID，再下发 `stopOwned`。本地真实故障链路显示：

- manager 对 PID `54494` 发送终止和强制终止信号后，4098 端口已不再监听，HTTP health 也不可达；
- 该 PID 仍以 `Z`（defunct/zombie）状态存在，父进程是当前 opencode-manager；
- Unix `DefaultProcessAlive` 仅用 signal 0 判断 PID 是否存在，zombie 仍被视为存活；
- manager 因而在强制终止后的确认阶段等待至命令超时，保留 state 并返回 `FAILED`，Java 将其安全映射为 `OPENCODE_BAD_GATEWAY / TestAgent 管理进程实例校验停止失败`。

根因位于 Unix `OSStarter`：`exec.Cmd.Start()` 成功后丢弃了命令句柄，从未调用 `Wait()` 回收直属子进程。停止信号实际已生效，但子进程退出后成为 zombie，破坏了既有“PID 消失后才删除 state”的确认条件。

## 方案选择

采用 Unix 启动器异步回收方案：`command.Start()` 成功后启动一个 goroutine 调用 `command.Wait()`。manager 不等待子进程结束才返回启动结果，现有启动时序、PID 持久化、进程组信号和生命周期锁均保持不变；子进程最终退出时由 Go 运行进程完成 OS 级回收，随后 signal 0 返回进程已结束，公共停止流程可继续删除 manager state 并返回 `STOPPED`。

不采用以下方案：

- 只在存活检测中识别 zombie：这只能掩盖停止确认症状，不能回收 manager 自己创建的直属子进程，并引入 macOS/Linux 不同的进程状态读取实现。
- 在 Manager 内新增命令句柄注册表：可以提供更复杂的退出通知，但当前需求只需要保证每个 Unix 子进程都被回收，额外状态和并发协调超出最小修复范围。

Windows 启动器不变。Windows 的 PowerShell 包装脚本会很快退出并把真实 OpenCode 进程交给系统，现有实现已明确禁止等待包装进程，本次不得套用 Unix 行为。

## 代码与测试设计

只修改 `opencode-manager/internal/process/process_unix.go`，在启动成功后异步执行 `command.Wait()`，并用中文注释说明回收 zombie 与保持非阻塞启动语义的原因。`Wait()` 的结果不改变已经返回的启动结果；真实进程状态仍由现有 health、state 和停止确认链路负责。

新增仅 Unix 编译的真实子进程测试，使用 `OSStarter` 启动会快速退出的系统 shell 子进程，并轮询 `health.DefaultProcessAlive(pid)`：

1. 修改生产代码前，测试应因退出子进程未被回收、signal 0 仍成功而失败；
2. 最小实现后，测试应观察到 PID 在有界时间内不再存活；
3. 保留现有 `Manager.Stop` 单元测试，继续验证确认存活时不得删除 state、确认结束后才返回成功的 fail-closed 语义。

同步更新 `opencode-manager/README.md`，明确非 Windows manager 会异步回收自己创建的 OpenCode 子进程，SIGTERM/SIGKILL 后的 PID 消失确认不会被 zombie 阻断。

## 验证与验收

实施阶段按以下顺序验证：

1. 运行新增 Unix 定向测试并记录 RED，再实施最小代码改动并记录 GREEN。
2. 执行 `cd opencode-manager && go test ./...` 和 `git diff --check`。
3. 使用项目既有重启脚本完整重启 backend、opencode-manager、frontend；重启会使当前旧 manager 遗留的 zombie 被系统回收，并清理其陈旧本地 state。
4. 验证 backend health/readiness、manager WebSocket 连接和前端可访问。
5. 通过真实运行管理链路启动或选取一个受管进程，再执行停止；确认 API 返回成功、manager 回包为 `STOPPED`、对应端口无监听、进程 state 文件删除且日志不再出现 `process did not exit after force kill`。

## 影响与兼容性

- 不修改 Java 公共启动、停止、状态查询或跨 Java 路由程序，不绕过 `stopOwned` 的 UCID + PID 校验。
- 不修改 HTTP API、RunEvent、DTO、数据库、Flyway、SQL、generated SDK、权限或环境配置。
- 不降低停止确认的安全边界：只有 PID 经 OS 回收后才视为退出，仍存活的进程继续保留 state 并返回失败。
- Unix 每个受管子进程增加一个等待其退出的轻量 goroutine；goroutine 生命周期与子进程一致，不进行轮询，也不增加外部调用。
- 修复部署后需要重启 manager 才能使用新的启动器行为；重启前已经成为 zombie 且其 `exec.Cmd` 已丢失的进程无法由新代码补领句柄，但 manager 重启会使其交由系统回收。
