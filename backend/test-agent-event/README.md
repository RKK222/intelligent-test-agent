# test-agent-event

## 工程定位

事件模型与事件流模块，负责平台 RunEvent 的追加、转换、SSE 输出和回放。

## 技术栈

- Java 21
- Spring WebFlux
- Reactor
- Maven library jar

## 主要职责

- RunEvent append-only 模型和 seq 规则。
- opencode raw event 到平台事件的转换边界。
- SSE stream、断线续传、事件回放和单机 live bus。

## 已有实现

- `RunEventAppender`：追加 `RunEventDraft` 并返回持久化后的 `RunEvent`。
- `RunEventLiveBus`：按 runId 发布/订阅当前进程内实时事件，durable 事件带 seq，transient 事件 `seq=0`。
- `RunEventReplayService`：解析 `Last-Event-ID` 并按 `runId + seq` 增量回放。
- `RunEventSseMapper`：将 durable RunEvent 映射为带 `id=seq` 的 SSE，将 transient live output 映射为不带 SSE `id` 的 SSE。
- `RunEventSseStreamService`：合并 Repository durable replay 与 `RunEventLiveBus` 实时事件；阻塞式回放查询 offload 到 `boundedElastic`，单次 Repository 异常跳过本轮轮询并继续保持订阅，客户端断开时释放订阅。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- Reactor。
- Spring WebFlux 的事件流类型。

## 禁止依赖

- generated SDK 直接暴露给前端。
- `test-agent-api` Controller。
- Persistence 实现细节。

## 后续 AI 编码指引

新增事件类型、SSE 序列化、事件回放或断线续传逻辑时改这里。数据库写入接口可定义在这里，实现放到 persistence。
事件模块只依赖 domain 的 RunEvent 端口，不直接依赖 JDBC Repository 实现。
SSE polling 不得在 Reactor interval/event-loop 线程上直接执行阻塞式 Repository 查询；回放瞬时失败不能改变 Run 终态，只能等待后续轮询或客户端重连恢复。transient 事件不写入数据库、不设置 SSE `id`，不能作为 `Last-Event-ID` 恢复点。
