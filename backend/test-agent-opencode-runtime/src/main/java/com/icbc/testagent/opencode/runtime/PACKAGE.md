# 包说明：com.icbc.testagent.opencode.runtime

## 职责

agent 运行态业务根包，负责平台 Session/Run 与远端 agent 能力之间的业务编排；当前真实适配器为 opencode。

## 不负责

- 不定义 HTTP Controller 或 API URL。
- 不直接访问 generated SDK。
- 不直接依赖 JDBC Repository 实现。

## 主要程序清单

- `session.SessionApplicationService`：会话创建、查询、消息和归档；消息列表会优先触发 projected messages 刷新，失败回退数据库快照。
- `run.RunApplicationService`：Run 启动、路由、通用 agent binding 创建/复用、root session scope 记录、事件订阅、active-run 查询和取消；active-run 对已有 Redis user marker 的用户只读 Session active 索引。当前创建仍固定使用 `LEGACY_FULL`，`REDIS_SUMMARY` 启用边界留给无原文 Run 锚点/终态摘要链路。
- `run.RunSessionScopeRouter`：在订阅级状态中维护当前 Run root/child known sessions 和 scopeVersion，负责 child discovery、pending drain、raw event dedup、child 终态过滤和无 session 全局 unknown 噪声过滤；新模式的 root/child scope、dedup 和 pending 全部走 `RunRuntimeStore` 同一 `{runId}` 数据面，禁止读写 scope 表；legacy 仅在 cache miss/新 child 时兼容访问 Repository。
- `run.RunSessionScopeRuntimeCache`：只服务 legacy 的 Redis 热 cache，维护 `test-agent:run-scope:{runId}:pending:{sessionId}` 与 `test-agent:run-scope:{runId}:dedup:{sessionId}:{rawEventId}`，TTL 30 分钟，Redis 不可用时 legacy 可按数据库事实源继续处理；新模式不得调用该旧 cache。
- `run.RunActivityStateStore`：维护 stale active Run 收敛所需 Redis 轻量状态，`test-agent:run-output-activity:{runId}` 保存 30 分钟内用户可见输出活跃标记，`test-agent:run-pending-ask:{runId}` 保存最新未处理 ask 状态；pending ask 只从实时 RunEvent 写入，不反查数据库。
- `run.StaleActiveRunReconcileTaskHandler` / `run.StaleActiveRunReconcileService`：复用 scheduler 框架每 5 分钟扫描超过 2 小时的 active Run；无近期输出且无 pending ask 时 CAS 标记 `FAILED` 并追加固定消息的 `run.failed`，只修复平台 Run 状态，不主动取消远端 opencode 会话。
- `run.RunDiffApplicationService`：Run 级 Diff 查询、接受和拒绝，通过当前 agent runtime fallback 查询远端 Diff。
- `run.RunEventPersistencePolicy`：区分 durable RunEvent 与 transient live output，并清洗 tool 大字段。
- `run.RunMessageRecoveryService`：SSE 建连时从 agent projected messages 生成 transient message snapshot；存在 Run scope 时按 root + child session 恢复当前 Run 子树。
- `run.RunEventSseRouteService`：优先按 Redis Run manifest 的 `producerLinuxServerId` 解析目标 Java，manifest 缺失的 legacy/旧 Run 才按 routing decision 和生产 opencode 进程兼容解析。
- `run.RunSessionMessageSnapshotService`：Run 终态/取消后持久化 assistant 快照、parts、token/cost，并支持消息列表刷新 fallback。
- `runtime.OpencodeRuntimeApplicationService`：opencode Web App runtime API 到 `AgentRuntime` 的映射。
- `process.*`：当前用户 opencode 进程分配、公共状态查询、公共启动/停止健康确认、通用参数 session/config 路径读取、manager WebSocket 控制面网关、后端实例生命周期和超级管理员运行管理快照/命令编排。
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

- `backend/test-agent-opencode-runtime/src/test/java/com/icbc/testagent/opencode/runtime`。
- `run.*` 测试必须覆盖 Run 创建、通用 agent binding 保存/复用、远端 session 懒创建/复用、事件持久化策略、Redis manifest 优先的 RunEvent SSE 生产 Java 路由、new/legacy scope 数据库访问边界和新模式 root/scope/dedup/pending 统一端口、终态快照/token 回写、Redis active-run、Diff fallback、消息恢复、Redis 输出活跃/待处理 ask 状态和 stale active Run 收敛任务。
- `session.*` 测试必须覆盖 Workspace 校验、归档隐藏、局部更新、消息追加默认 role 和消息列表数据库 fallback。
- `runtime.*` 测试必须覆盖 opencode runtime path、workspace directory 透传、query 过滤和 permission/question body 兼容。
- `process.*` 测试必须覆盖用户进程分配、公共状态查询、公共启动/停止健康确认、通用参数路径读取、manager 控制面命令路由、后端心跳注册和运行管理快照聚合。
- `terminal.*` 测试必须覆盖 ticket 签发/消费/过期、active session 互斥、输入输出限流、WebSocket envelope 和进程适配。

## 修改时必须同步更新

- `backend/test-agent-opencode-runtime/README.md`。
- `docs/api/http-api.md` 或 `docs/api/event-stream.md`，如果影响 API、DTO 或事件。
- `docs/architecture/dependency-rules.md`，如果依赖边界变化。
