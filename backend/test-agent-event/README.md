# test-agent-event

## 工程定位

事件模型与事件流模块，负责平台 RunEvent 的追加、转换、SSE 输出和回放。

## 技术栈

- Java 21
- Spring WebFlux
- Reactor
- Spring Data Redis（可选，用于后端内部服务器广播）
- Maven library jar

## 主要职责

- 按 Run 固定 `RunStorageMode` 分流的 RunEvent 追加和 seq 规则。
- opencode raw event 到平台事件的转换边界。
- SSE stream、断线续传、事件回放、本机 live bus，以及后端内部服务器广播实现。

## 已有实现

- `RunEventAppender`：按 Redis manifest 固定的 `RunStorageMode` 追加 `RunEventDraft`。`LEGACY_FULL` 先写 `RunEventRepository`，存在 shadow manifest 时 best-effort 写 Redis hot tail；`REDIS_SUMMARY` durable 事件原子写 durable seq Stream、全事件 runtimeVersion Stream 和物化 snapshot，不写 PostgreSQL；transient 事件写 runtimeVersion Stream 与 snapshot。成功后发布的本机 live bus 帧只作为 SSE 低延迟唤醒信号，事实帧仍从 Redis 有序读取。
- `RunEventLiveBus`：按 runId 发布/订阅当前进程内实时事件，并提供 `streamAll()` 全局事件触发流供用户级运行态摘要刷新使用；durable 事件带 seq，transient 事件 `seq=0`；慢客户端或并发背压溢出时按 best-effort 丢弃当前 live 帧，不能终止全局实时通道。
- `ServerBroadcastPublisher` / `NoopServerBroadcastPublisher` / `RedisServerBroadcastPublisher`：通用服务器广播端口的 event 模块实现，当前用于应用版本工作区副本同步；默认关闭，开启 `test-agent.server-broadcast.enabled=true` 后走 Redis channel `test-agent:server-broadcast`。
- `RunEventReplayService`：解析 `Last-Event-ID`；legacy 按 `runId + seq` 从 Repository 增量回放；新模式读取 manifest、当前物化 snapshot 与 durable 保留起点以判定初始 reset reason，并提供按 `runtimeVersion` 分页读取 durable + transient 有序尾部的能力；Session 历史树的旧数据继续按 root session 读取数据库 durable 状态事件。
- `RunEventSseMapper`：将 durable RunEvent 映射为带 `id=seq` 的 SSE，将 transient live output 映射为不带 SSE `id` 的 SSE。
- `RunEventSseStreamService`：legacy 合并 Repository durable polling replay 和 `RunEventLiveBus`；新模式首帧总发送完整物化 `run.snapshot.reset`，以 snapshot `runtimeVersion` 为游标，随后由最短 5 秒的 Redis 安全扫描与本机 live bus 共同唤醒、按 runtimeVersion 从 Redis 分页读取 durable + transient 尾部。live 事件仍即时唤醒，但帧本身不直接输出；慢订阅、回放并发和 live 丢帧都由 Redis 顺序补偿，容量换代导致游标落后时再次发送 reset，reset 关键状态至少包含 USER 输入、最新 assistant message、对应可见 text part 和 run-status。整个新模式不创建 PostgreSQL 轮询，也不触发兼容远端消息 snapshot Flux。

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
legacy SSE polling 不得在 Reactor interval/event-loop 线程上直接执行阻塞式 Repository 查询；回放瞬时失败不能改变 Run 终态，只能等待后续轮询或客户端重连恢复。新模式安全扫描只唤醒 Redis tail，即使传入 500ms 的 legacy 轮询值也必须提升到最短 5 秒，不得重新引入数据库 polling；Redis 详情缺失时返回稳定错误而不是降级读库。live bus 必须保持 best-effort：背压溢出的唤醒帧可以丢弃，但不能把 Reactor sink 置为 error/complete，5 秒安全扫描会继续补偿读取。transient 事件（含 `run.snapshot.reset`）不写数据库、不设置 SSE `id`，不能作为 `Last-Event-ID` 恢复点。单 Run 跨 Java 实时 SSE 由 API 层路由到 Run 生产 Java 后订阅本机唤醒总线。
