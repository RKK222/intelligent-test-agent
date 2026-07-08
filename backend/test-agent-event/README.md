# test-agent-event

## 工程定位

事件模型与事件流模块，负责平台 RunEvent 的追加、转换、SSE 输出和回放。

## 技术栈

- Java 21
- Spring WebFlux
- Reactor
- Spring Data Redis（可选，用于 RunEvent 跨实例 fan-out 和后端内部服务器广播）
- Maven library jar

## 主要职责

- RunEvent append-only 模型和 seq 规则。
- opencode raw event 到平台事件的转换边界。
- SSE stream、断线续传、事件回放、本机 live bus、可选 Redis 跨实例 fan-out，以及后端内部服务器广播实现。

## 已有实现

- `RunEventAppender`：追加 `RunEventDraft` 并返回持久化后的 `RunEvent`。
- `RunEventLiveBus`：按 runId 发布/订阅当前进程内实时事件，并提供 `streamAll()` 全局事件触发流供用户级运行态摘要刷新使用；durable 事件带 seq，transient 事件 `seq=0`；慢客户端或并发背压溢出时按 best-effort 丢弃当前 live 帧，不能终止全局实时通道。
- `RunEventRemotePublisher` / `NoopRunEventRemotePublisher` / `RedisRunEventRemotePublisher`：跨实例实时广播端口、默认空实现和可选 Redis pub/sub 实现；`streamAll()` 会把远端 RunEvent 广播合入全局事件触发流，Redis 不可用时降级为本机 live bus。
- `ServerBroadcastPublisher` / `NoopServerBroadcastPublisher` / `RedisServerBroadcastPublisher`：通用服务器广播端口的 event 模块实现，当前用于应用版本工作区副本同步；默认关闭，开启 `test-agent.server-broadcast.enabled=true` 后走 Redis channel `test-agent:server-broadcast`。
- `RunEventReplayService`：解析 `Last-Event-ID` 并按 `runId + seq` 增量回放；Session 历史树可按 root session 读取 durable 状态事件。
- `RunEventSseMapper`：将 durable RunEvent 映射为带 `id=seq` 的 SSE，将 transient live output 映射为不带 SSE `id` 的 SSE。
- `RunEventSseStreamService`：合并 Repository durable replay、`RunEventLiveBus` 本机实时事件和可选 Redis 远端事件；并为 HTTP 历史接口提供 Run 级和 root-session 级 durable payload snapshot；阻塞式回放查询 offload 到 `boundedElastic`，单次 Repository 异常跳过本轮轮询并继续保持订阅，客户端断开时释放订阅。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- Reactor。
- Spring WebFlux 的事件流类型。
- Spring Data Redis，可选。

## 禁止依赖

- generated SDK 直接暴露给前端。
- `test-agent-api` Controller。
- Persistence 实现细节。

## 后续 AI 编码指引

新增事件类型、SSE 序列化、事件回放、断线续传或通用服务器广播传输逻辑时改这里。数据库写入接口可定义在这里，实现放到 persistence。
事件模块只依赖 domain 的 RunEvent 端口，不直接依赖 JDBC Repository 实现。
SSE polling 不得在 Reactor interval/event-loop 线程上直接执行阻塞式 Repository 查询；回放瞬时失败不能改变 Run 终态，只能等待后续轮询或客户端重连恢复。live bus 必须保持 best-effort：背压溢出的实时帧可以丢弃，但不能把 Reactor sink 置为 error/complete。transient 事件不写入数据库、不设置 SSE `id`，不能作为 `Last-Event-ID` 恢复点。Redis bus 是实时增强通道，不替代 `run_events` replay、消息快照恢复或业务表补偿扫描。
