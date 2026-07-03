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
- `RunEventLiveBus`、`RunEventLiveEvent`：当前进程内按 runId 广播 durable/transient 实时事件，并把本机事件交给可选远端广播端口；背压溢出时按 best-effort 丢弃当前 live 帧，保持全局通道可继续发布。
- `RunEventRemotePublisher`、`NoopRunEventRemotePublisher`、`RedisRunEventRemotePublisher`：跨实例广播端口、默认空实现和 Redis pub/sub 实现。
- `RunEventReplayService`：处理 Last-Event-ID、按 Run 增量回放和按 root session 回放历史状态事件。
- `RunEventSseMapper`：将 durable RunEvent 映射为带 `id=seq` 的 `ServerSentEvent`，将 transient payload 映射为不带 SSE `id` 的 `ServerSentEvent`。
- `RunEventSseStreamService`：合并 durable polling replay、本机 live bus 和可选 Redis 远端事件的 RunEvent SSE 输出服务，并提供 HTTP 历史接口复用的 durable payload snapshot；阻塞式回放查询 offload 到 `boundedElastic`，单次回放失败跳过本轮轮询并保持订阅。
- `RunEventSsePayload`：SSE body 的稳定平台事件载荷。
- `ServerBroadcastPublisher`、`NoopServerBroadcastPublisher`、`RedisServerBroadcastPublisher`：后端实例间通用广播端口、默认空实现和 Redis pub/sub 实现，供应用版本工作区副本同步等内部业务使用。
- 后续可新增跨实例订阅恢复和订阅管理器。

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

- event 模块单元测试，覆盖 durable SSE id、transient SSE 无 id、live bus 并发背压不终止通道、Redis 远端事件合流和 Last-Event-ID。
- SSE 集成测试。
- Last-Event-ID、seq 单调递增、断线续传和事件映射测试。

## 修改时必须同步更新

- `backend/test-agent-event/README.md`。
- `docs/api/event-stream.md`。
- `docs/standards/backend.md`，如果事件测试策略变化。
- 前端 adapter 契约文档，如果事件形态影响前端。
