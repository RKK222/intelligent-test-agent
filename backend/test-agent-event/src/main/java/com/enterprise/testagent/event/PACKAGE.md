# 包说明：com.enterprise.testagent.event

## 职责

平台事件包，负责 RunEvent 模型、SSE 输出、事件转换、事件回放和断线续传边界。

## 不负责

- 不直接暴露 generated SDK DTO 给前端。
- 不实现数据库细节。
- 不承载 Controller 入口。

## 主要程序清单

- `package-info.java`：说明 event 包是平台事件模型、SSE 和回放边界。
- `RunEventAppender`：按 `RunRuntimeStore` manifest 的 `RunStorageMode` 追加事件草稿；legacy 写 Repository 并可 best-effort 写 Redis hot tail，新模式 durable 写 seq/runtimeVersion 双 Stream、transient 写 runtimeVersion Stream，二者都物化 snapshot；live bus 只作为新模式 SSE 唤醒信号。
- `RunEventLiveBus`、`RunEventLiveEvent`：当前进程内按 runId 广播 durable/transient 实时事件；背压溢出时按 best-effort 丢弃当前 live 帧，保持全局通道可继续发布。
- `RunEventReplayService`：处理 Last-Event-ID；legacy 按 Repository 增量回放，新模式读取 Redis manifest/物化 snapshot 并判定初始 reset reason，同时按 `runtimeVersion` 提供 durable/transient 分页有序 tail；旧 Session 历史状态仍可按 root session 从 Repository 读取。
- `RunEventSseMapper`：将 durable RunEvent 映射为带 `id=seq` 的 `ServerSentEvent`，将 transient payload 映射为不带 SSE `id` 的 `ServerSentEvent`。
- `RunEventSseStreamService`：legacy 合并 durable polling replay 和本机 live bus；新模式首帧总发送完整物化 `run.snapshot.reset`，之后最短 5 秒的安全扫描/live 即时唤醒只按 runtimeVersion 分页读取 Redis tail，容量换代时再次 reset，并重放运行数据面保留的 USER、最新 assistant/可见 text part 和 run-status；不执行数据库轮询，snapshot events 不推进 durable `Last-Event-ID`。
- `RunEventSsePayload`：SSE body 的稳定平台事件载荷。
- `ServerBroadcastPublisher`、`NoopServerBroadcastPublisher`、`RedisServerBroadcastPublisher`：后端实例间通用广播端口、默认空实现和 Redis pub/sub 实现，供应用版本工作区副本同步等内部业务使用。
- 单 Run 跨 Java 实时 SSE 由 API 层按 Run 生产 Java 流式转发，本包只负责目标 Java 内的事件回放和本机实时推送。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- Reactor。
- Spring WebFlux 的事件流类型。
- Spring Data Redis，可选。

## 禁止依赖

- generated SDK 直接返回给前端。
- `test-agent-api` Controller。
- persistence 实现细节。

## 上游调用方

- `test-agent-api` 的事件 API。
- opencode client facade 的事件转换流程。
- 前端 SSE 客户端。

## 下游依赖

- domain 事件模型。
- persistence 提供的事件查询和追加接口。
- observability 提供的 trace 和指标能力。

## 测试位置

- event 模块单元测试，覆盖 durable SSE id、transient SSE 无 id、live bus 并发背压不终止通道、legacy live/replay 合流、Redis 新模式首帧 reset、runtimeVersion 分页超过 batch limit 不丢事件、0 次 Repository 调用和 Last-Event-ID。
- SSE 集成测试。
- Last-Event-ID、seq 单调递增、断线续传和事件映射测试。

## 修改时必须同步更新

- `backend/test-agent-event/README.md`。
- `docs/api/event-stream.md`。
- `docs/standards/backend.md`，如果事件测试策略变化。
- 前端 adapter 契约文档，如果事件形态影响前端。
