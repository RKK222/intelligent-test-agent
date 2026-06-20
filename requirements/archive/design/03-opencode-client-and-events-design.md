# Phase 03 opencode client 与事件体系详细设计

## 目标

Phase 03 通过 `OpencodeClientFacade` 收敛 generated SDK 调用，并把 opencode server 的响应、错误和事件转换为平台稳定模型。业务模块不得直接依赖 `test-agent-opencode-sdk-generated`，前端也不会看到 generated DTO 或 opencode raw event。

## Facade 边界

- `test-agent-opencode-client` 是唯一允许 import `com.example.opencode.sdk.*` 的业务模块。
- 对上暴露 `OpencodeClientFacade`，初版支持 `health`、`cancelSession` 和 `streamRunEvents`。
- Facade 输入输出只使用平台 command/result、domain ID、`ExecutionNode` 和 `RunEventDraft`。
- `GeneratedOpencodeSdkGateway` 是 generated SDK 适配器；测试可用 gateway stub 覆盖成功、错误、超时、重试和事件映射。

## 调用策略

- 每次 opencode 调用按路由选中的 `ExecutionNode.baseUrl` 创建 SDK `ApiClient`，并透传 `X-Trace-Id`。
- 所有外部调用使用 Reactor `timeout`，只对连接失败、503、502 和 5xx 执行有限重试。
- `WebClientResponseException`、超时和连接失败统一转换为 `PlatformException`：
  - 超时、408、504 -> `OPENCODE_TIMEOUT`
  - 503 或连接不可达 -> `OPENCODE_UNAVAILABLE`
  - 其他远端异常 -> `OPENCODE_BAD_GATEWAY`
- 错误 details 只包含安全字段，例如 status、nodeId、baseUrl、operation，不包含 token、请求体或堆栈。

## 事件转换

- opencode SSE 通过 generated `EventApi.eventSubscribeWithResponseSpec(...).bodyToFlux(JsonNode.class)` 读取，避免 generated anyOf DTO 丢失未知字段。
- `OpencodeRunEventMapper` 把 raw type 转为平台 `RunEventDraft`：
  - `session.next.prompted` -> `run.started`
  - `session.next.step.ended`、`session.status` 的 `idle` 状态、`session.idle` -> `run.succeeded`
  - `session.next.text.delta`、`message.part.delta` -> `assistant.message.delta`
  - `session.next.tool.called` -> `tool.started`
  - `session.next.tool.success`、`session.next.tool.failed` -> `tool.finished`
  - `session.diff` -> `diff.proposed`
  - `test.finished` -> `test.finished`
  - 未知事件 -> `opencode.event.unknown`
- 已知事件 payload 保留前端稳定字段，并附带 `rawType`、`rawEventId` 和安全 raw payload，便于排查兼容问题。
- 未知事件不得中断运行；平台事件类型固定为 `opencode.event.unknown`，前端默认忽略但可在调试视图显示。

## 事件模块协作

- `test-agent-event` 提供 `RunEventAppender`、`RunEventReplayService` 和 `RunEventSseMapper`。
- `RunEventAppender` 调用 domain 的 `RunEventRepository` 追加事件并获得正式 `eventId/seq`。
- `RunEventSseMapper` 输出 SSE：`id` 为 seq 字符串，`event` 为 `RunEventType.wireName()`，body 为平台 RunEvent 基础字段。
- `Last-Event-ID` 解析失败时由回放服务抛出 `VALIDATION_ERROR`，Phase 04 Controller 统一转换为错误响应。

## 验收

- 后端编译通过，且除 `test-agent-opencode-client` 外没有业务模块 import generated SDK。
- facade 测试覆盖成功、取消、超时、远端错误、重试和 traceId 透传。
- 事件转换测试覆盖已知事件和未知事件。
- 事件文档记录稳定 wire name、payload 兼容策略和未知事件降级规则。
