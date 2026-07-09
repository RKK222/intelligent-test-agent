# 包说明：com.icbc.testagent.event

## 职责

平台事件包，负责 RunEvent 模型、SSE 输出、事件转换、事件回放和断线续传边界。

## 不负责

- 不直接暴露 generated SDK DTO 给前端。
- 不实现数据库细节。
- 不承载 Controller 入口。

## 主要程序清单

- `package-info.java`：说明 event 包是平台事件模型、SSE 和回放边界。
- `RunEventAppender`：通过 domain 端口追加事件草稿。
- `RunEventLiveBus`、`RunEventLiveEvent`：当前进程内按 runId 广播 durable/transient 实时事件；背压溢出时按 best-effort 丢弃当前 live 帧，保持全局通道可继续发布。
- `RunEventReplayService`：处理 Last-Event-ID、按 Run 增量回放和按 root session 回放历史状态事件。
- `RunEventSseMapper`：将 durable RunEvent 映射为带 `id=seq` 的 `ServerSentEvent`，将 transient payload 映射为不带 SSE `id` 的 `ServerSentEvent`。
- `RunEventSseStreamService`：合并 durable polling replay 和本机 live bus 的 RunEvent SSE 输出服务，并提供 HTTP 历史接口复用的 durable payload snapshot；阻塞式回放查询 offload 到 `boundedElastic`，单次回放失败跳过本轮轮询并保持订阅。
- `RunEventSsePayload`：SSE body 的稳定平台事件载荷。
- `ServerBroadcastPublisher`、`NoopServerBroadcastPublisher`、`RedisServerBroadcastPublisher`：后端实例间通用广播端口、默认空实现和 Redis pub/sub 实现，供应用版本工作区副本同步等内部业务使用。
- 单 Run 跨 Java实时 SSE 由 API 层按 Run 生产 Java 流式转发，本包只负责目标 Java 内的事件回放和本机实时推送。

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

- event 模块单元测试，覆盖 durable SSE id、transient SSE 无 id、live bus 并发背压不终止通道、本机 live bus 与 durable replay 合流和 Last-Event-ID。
- SSE 集成测试。
- Last-Event-ID、seq 单调递增、断线续传和事件映射测试。

## 修改时必须同步更新

- `backend/test-agent-event/README.md`。
- `docs/api/event-stream.md`。
- `docs/standards/backend.md`，如果事件测试策略变化。
- 前端 adapter 契约文档，如果事件形态影响前端。
