# 包说明：com.enterprise.testagent.opencode.runtime

## 职责

agent 运行态业务根包，负责平台 Session/Run 与远端 agent 能力之间的业务编排；当前真实适配器为 opencode。

## 不负责

- 不定义 HTTP Controller 或 API URL。
- 不直接访问 generated SDK。
- 不直接依赖 JDBC Repository 实现。

## 主要程序清单

- `session.SessionApplicationService`：会话创建、查询、消息和归档；消息列表会优先触发 projected messages 刷新，失败回退数据库快照。
- `night.*`：`NightExecutionCapacityRegistry` 实现显式 JVM 内存参数 SPI，由配置管理注册表在启动、匹配广播和手工刷新时读取 `NIGHT_EXECUTION_SLOT_CAPACITY` 并原子替换快照；其余夜间窗口、提交查询/改期/取消、会话锁、USER_PLAN 投递和 5 分钟补偿复用该快照。任务到期后按当前 `scheduledTaskRunId` 条件认领，进程启动和远端 Run 创建位于认领事务之外，再以短事务写回终态和解锁；投递复用 `UserOpencodeProcessAssignmentService.initialize`、会话上下文及 `RunApplicationService.startScheduledRun`，不直接调用 manager gateway，任务成功启动后沿用既有 RunEvent SSE。
- `process.OpencodeScheduledTaskExecutionAffinityProvider`：把 scheduler USER_PLAN 亲和标识固定为当前稳定 Linux 服务器 ID。
- `process.BackendJavaRouteResolver`：统一按服务器、容器归属或稳定 `backendProcessId` 解析在线 Java；进程级诊断使用精确 ID，同服务器多个 Java 不合并。
- `run.RunApplicationService`：Run 启动、路由、通用 agent binding 创建/复用、root session scope 记录、事件订阅、active-run 查询和取消；自动 dispatch 锚点由当前 runtime 生成并在远端 command、平台 USER、scope、manifest 和持久化锚点间复用，Legacy 显式旧 ID 保持透传。所有带 runId 的用户入口先通过该服务校验 Run 归属，新模式只读 Redis manifest 用户字段，legacy/manifest 缺失才回查 Run 与 Session。active-run 对已有 Redis user marker 的用户只读 Session active 索引。携带有效上下文的新 Run 由 `RunStorageModeSelector` 按 userId 稳定灰度固定为 `LEGACY_FULL` 或 `REDIS_SUMMARY`，活动期间不得切换；容量 reset 继续由同一运行数据面保留 USER、最新 assistant/可见 text part 和 run-status，不从数据库补原文。
- `run.RunSessionScopeRouter`：在订阅级状态中维护当前 Run root/child known sessions 和 scopeVersion，负责 child discovery、pending drain、raw event dedup、child 终态过滤和无 session 全局 unknown 噪声过滤；新模式的 root/child scope、dedup 和 pending 全部走 `RunRuntimeStore` 同一 `{runId}` 数据面，禁止读写 scope 表；legacy 仅在 cache miss/新 child 时兼容访问 Repository。
- `run.RunSessionScopeRuntimeCache`：只服务 legacy 的 Redis 热 cache，维护 `test-agent:run-scope:{runId}:pending:{sessionId}` 与 `test-agent:run-scope:{runId}:dedup:{sessionId}:{rawEventId}`，TTL 30 分钟，Redis 不可用时 legacy 可按数据库事实源继续处理；新模式不得调用该旧 cache。
- `run.RunActivityStateStore`：维护 stale active Run 收敛所需 Redis 轻量状态，`test-agent:run-output-activity:{runId}` 保存 30 分钟内用户可见输出活跃标记，`test-agent:run-pending-ask:{runId}` 保存最新未处理 ask 状态；pending ask 只从实时 RunEvent 写入，不反查数据库。
- `run.StaleActiveRunReconcileTaskHandler` / `run.StaleActiveRunReconcileService`：只服务 `LEGACY_FULL`，复用 scheduler 框架每 5 分钟扫描超过 2 小时的 active Run；无近期输出且无 pending ask 时 CAS 标记 `FAILED` 并追加数据库 `run.failed`，不处理 `REDIS_SUMMARY`。
- `run.RunDiffApplicationService`：Run 级 Diff 查询、接受和拒绝；新模式先读 Redis snapshot，远端 ID 必要时读非原文 Run 锚点，过期返回 410，动作成功后以单 SQL 更新 Run 计数。
- `run.RunEventPersistencePolicy`：区分 durable RunEvent 与 transient live output，并清洗 tool 大字段。
- `run.RunMessageRecoveryService` / `run.RunTurnMessageSelector`：SSE 建连时按稳定 dispatch user 锚点从 agent projected messages 生成当前 Run 的 transient message snapshot；只选择该 user 的直接 assistant，锚点冲突、缺失或分页歧义时 fail-closed，已记录 child scope 可恢复但新 child 只能由选中 root 发现。
- `run.RunEventSseRouteService`：优先按 Redis Run manifest 的 `producerLinuxServerId` 解析目标 Java，manifest 缺失的 legacy/旧 Run 才按 routing decision 和生产 opencode 进程兼容解析；分别提供 SSE 可回退读取和 cancel 写操作严格路由语义。
- `run.RunSessionMessageSnapshotService`：Run 终态/取消后先按稳定 USER 锚点裁剪，再持久化本轮 assistant 快照、parts 和最后一条 assistant 的 token/cost；消息列表刷新 fallback 保留既有 Run 归属，新消息不猜测 `runId`。
- `run.summary.RunConversationSummarizer`：为新存储模式生成确定性 USER/ASSISTANT 双摘要，负责敏感内容清洗、Unicode code-point 截断和安全 fallback；不读取数据库、不调用外部模型，也不把原文作为失败降级结果。
- `run.RunStorageModeSelector` / `run.RunTerminalProjectionService` / `run.RunTerminalProjectionRecoveryCoordinator`：前者按已校验上下文和 userId 稳定哈希为新 Run 固定存储模式；终态服务从 Redis 物化状态生成双摘要、usage、Diff 与远端定位投影，并通过领域端口执行三语句关系型事务；恢复协调器在启动和 5 秒周期中只由公共路由选中的同服务器 Java消费 terminal Lua 原子发布的 versioned outbox，APPLIED/版本冲突后 ack，数据库失败保留 `TERMINAL_PENDING_DB`。
- `run.RunTerminalProjectionRetryService` / `run.RunTerminalProjectionRetryTaskHandler`：按绝对 due 时间批量重试安全终态投影，由 XXL 每 5 秒调用并通过统一 adapter 取得 scheduler Redis 全局锁；本模块不再启动本地 ticker。严格执行六档退避、5 分钟封顶和 24 小时清理。
- `run.RunRuntimeLossConvergenceService` / `run.RunRuntimeLossConvergenceScheduler`：由调用方在 Redis 连续故障 30 秒后触发；复查 manifest 仍失败时使用启动期安全快照 best-effort 取消已创建的远端 Session（未创建则跳过），并写入不含原文的 `RUNTIME_STATE_LOST` 终态与固定 fallback 双摘要。启动锚点已写但 prompt 未派发时，Redis 若在 grace 内恢复会执行条件接管回调，用新 fencing token 关闭未派发 Run；回调中 Redis 再次中断会立即重做安全收敛探测。数据库失败时把同一安全投影写入独立终态重试队列，Redis 也仍不可写时返回显式失败结果并告警。
- `run.RunPendingAskExpiryCoordinator` / `run.RunPendingAskExpiryScheduler`：启动时和每 5 秒扫描本服务器 active manifest，只接收 attention 满 7 天的 `REDIS_SUMMARY` Run；通过公共 Java 路由选择与 owner lease fencing 保证同服务器单 Java 执行。待交互物理 TTL 必须比 7 天业务边界至少多一个扫描窗口；实际取消/终态由 `RunPendingAskExpiryExecutor` 接管，未装配时安全 no-op。
- `run.RunInactiveExpiryCoordinator` / `run.RunInactiveExpiryScheduler`：启动时和每 30 秒扫描本服务器 Redis active manifest；无 attention 且两小时无活动的新模式 Run 复用 fencing-safe 远端取消与终态摘要程序，整个运行态收敛不写 `run_events`。
- `run.RunRuntimeSchedulingConfiguration`：owner lease 续租独占单线程调度器，其余恢复/到期/重试任务使用独立 4 线程维护调度器，避免阻塞 Boot 默认调度线程或饿死 5 秒续租。
- `run.RunOwnerLeaseSupervisor`：统一维护本机 owner handle 的 5 秒续租信号；fencing 被其它 owner 取得时正常完成 `lost` 只停止旧订阅，Redis/运行态异常时以原错误终止 `lost`，让 Run 启动订阅和恢复订阅调度 30 秒安全收敛。
- `run.RunMessageRecoveryService`：为 Run/Session HTTP 历史按 Redis → OpenCode → PostgreSQL 双摘要恢复，并携带完整度、可回放性和详情到期时间；Run 级 OpenCode 来源因果裁剪到目标轮，Session 级来源保持全量多轮，legacy SSE 兼容方法只输出目标轮 assistant。
- `runtime.OpencodeRuntimeApplicationService`：opencode Web App runtime API 到 `AgentRuntime` 的映射。
- `runtime.SideQuestionStreamingApplicationService` / `runtime.SideQuestionTerminalService`：以归档内部 Session 启动 `SIDE_QUESTION` Run；临时 fork 仅接收用户问题并禁用工具，通过本轮 assistant 事件流输出增量，消息快照补偿漏失终态，最后以事务 CAS 写唯一终态。
- `runtime.SideQuestionOrphanCleanupTaskHandler` / `runtime.SideQuestionOrphanCleanupService`：复用 scheduler 每 5 分钟回收超过 10 分钟的旁路 fork；按内部映射使用原节点，404 幂等，无映射时记录潜在泄漏窗口并收敛平台 Run。
- `process.*`：当前用户 opencode 进程分配、公共状态查询、公共启动/停止健康确认、通用参数 session/config 路径读取、启动时可选注入当前平台 `OPENCODE_REFERENCES_DIR`、manager WebSocket 控制面网关、后端实例生命周期和超级管理员运行管理快照/命令编排。引用目录参数缺失不阻断滚动升级中的进程启动，既有进程不热更新环境。
- `process.WorkspaceFileRoutingService`：复用公共 Java 路由程序定位 workspace 文件 WebSocket 的目标后端，并在路由阶段通过 `ConversationWorkspaceAccessAuthorizer` 校验实时应用成员关系；非托管 Workspace 仅接受 `SUPER_ADMIN` 服务器工作空间兼容访问，ticket 和具体 RPC 的再次校验由 API/业务入口共同完成。
- `terminal.*`：PTY ticket、限流、WebSocket 背后的业务状态和本地进程适配。

## 允许依赖

- `test-agent-common`、`test-agent-domain`、`test-agent-event`。
- `test-agent-agent-runtime`。
- `test-agent-scheduler`，仅用于业务 `ScheduledTaskHandler` 接入。
- Reactor、Jackson、Spring Context、Spring Data Redis。

## 禁止依赖

- `test-agent-api`。
- `test-agent-app`。
- `test-agent-opencode-sdk-generated`。
- `test-agent-persistence` 实现细节。

## 测试位置

- `backend/test-agent-opencode-runtime/src/test/java/com/enterprise/testagent/opencode/runtime`。
- `run.*` 测试必须覆盖 Run 创建、通用 agent binding 保存/复用、远端 session 懒创建/复用、事件持久化策略、Redis manifest 优先的 RunEvent SSE 生产 Java 路由、Run 用户归属与新模式鉴权零 PostgreSQL、new/legacy scope 数据库访问边界和新模式 root/scope/dedup/pending 统一端口、终态快照/token 回写、确定性双摘要的净化/Unicode 截断/fallback、Redis active-run、Redis 连续故障 30 秒后的无原文收敛、待交互 7 天与普通无活动 2 小时精确边界/同服务器 Java 路由/owner lease、Diff fallback、按 dispatch user 的跨 Run 消息/Todo 隔离、Session 全量历史，以及 legacy stale active Run 收敛任务。
- `night.*` 测试必须覆盖北京时间夜间窗口、15 分钟推荐/容量、幂等提交、单会话锁、服务器亲和重路由、公共进程启动、稳定 Run 恢复、窗口内顺延、07:00 最终失败和 30 天清理。
- `session.*` 测试必须覆盖 Workspace 校验、归档隐藏、局部更新、消息追加默认 role 和消息列表数据库 fallback。
- `runtime.*` 测试必须覆盖 opencode runtime path、workspace directory 透传、query 过滤、permission/question body 兼容、旁路事件隔离、终态竞态和孤儿清理。
- `process.*` 测试必须覆盖用户进程分配、公共状态查询、公共启动/停止健康确认、通用参数路径读取、引用目录启动环境的目标平台解析/覆盖/缺失兼容、workspace 文件路由的实时应用成员校验、manager 控制面命令路由、后端心跳注册和运行管理快照聚合。
- `terminal.*` 测试必须覆盖 ticket 签发/消费/过期、active session 互斥、输入输出限流、WebSocket envelope 和进程适配。

## 修改时必须同步更新

- `backend/test-agent-opencode-runtime/README.md`。
- `docs/api/http-api.md` 或 `docs/api/event-stream.md`，如果影响 API、DTO 或事件。
- `docs/architecture/dependency-rules.md`，如果依赖边界变化。
