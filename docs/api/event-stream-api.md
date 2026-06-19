# 事件流 API 文档

本文档是 SSE 和平台事件流的稳定入口。新增或修改事件类型必须更新本文件。

## 文档模板

每个事件类型必须记录：

- event name。
- event id 规则。
- 字段说明。
- 是否可重复投递。
- 兼容性说明。
- 前端展示或处理方式。
- 对应测试。

## 通用规则

1. RunEvent 只追加，不更新。
2. durable RunEvent 写入 `run_events`，每个 runId 内 seq 单调递增。
3. transient live output 不写入 `run_events`，payload `seq=0`，SSE 不设置 `id`。
4. durable SSE event id 使用 seq；transient event 不参与 `Last-Event-ID` 恢复。
5. 前端断线后通过 Last-Event-ID 续传 durable RunEvent；消息内容恢复只从 opencode session projected messages 获取。
6. 不把 opencode raw event 原样透传给前端，也不把大段日志、bash/tool output 或高频文本 delta 作为平台持久化事件保存。
7. generated SDK 事件必须在 `test-agent-event` 或 `test-agent-opencode-client` 映射为平台事件。

## RunEvent 基础字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `eventId` | string | 平台事件 ID，使用 `evt_` 前缀。 |
| `runId` | string | 所属 Run ID，使用 `run_` 前缀。 |
| `seq` | number | durable 事件为同一 runId 内从 1 开始单调递增；transient 事件固定为 0，不参与恢复。 |
| `type` | string | 平台事件类型 wire name，例如 `run.started`、`assistant.message.delta`。 |
| `traceId` | string | 产生该事件的请求或异步链路 traceId。 |
| `occurredAt` | string | ISO-8601 时间。 |
| `payload` | object | 事件业务载荷，前端必须允许新增字段。 |

稳定事件类型使用 wire name，Java enum 只作为后端内部表达：

| wire name | 说明 |
|---|---|
| `run.created` | Run 已创建。 |
| `run.started` | Run 已开始执行。 |
| `run.cancelling` | Run 正在取消。 |
| `run.succeeded` | Run 成功结束。 |
| `run.failed` | Run 失败结束。 |
| `run.cancelled` | Run 已取消。 |
| `assistant.message.delta` | 助手消息增量，只实时发送，不写入 `run_events`。 |
| `message.updated` | opencode Web App message projection 更新；实时发送，断线后由 opencode session messages snapshot 恢复。 |
| `message.removed` | opencode Web App message projection 移除。 |
| `message.part.updated` | message part 内容或状态更新；实时发送，断线后由 opencode session messages snapshot 恢复。 |
| `message.part.removed` | message part 移除。 |
| `message.part.delta` | message part 流式增量，只实时发送，不写入 `run_events`。 |
| `session.diff` | session 级 Diff 状态更新。 |
| `session.status` | session busy/idle/status 更新。 |
| `todo.updated` | Todo 列表更新。 |
| `tool.started` | 工具调用开始。 |
| `tool.finished` | 工具调用结束，payload 使用 `status` 区分 success/failed。 |
| `diff.proposed` | 智能体提出文件 Diff。 |
| `diff.accepted` | 用户已接受 Run 级 Diff。 |
| `diff.rejected` | 用户已拒绝 Run 级 Diff，后端已提交 opencode revert。 |
| `test.finished` | 测试执行结束。 |
| `permission.asked` | 权限请求。 |
| `permission.replied` | 权限回复。 |
| `question.asked` | 提问请求。 |
| `question.replied` | 提问回复。 |
| `question.rejected` | 提问拒绝。 |
| `vcs.branch.updated` | VCS 分支更新。 |
| `lsp.updated` | LSP 状态更新。 |
| `mcp.tools.changed` | MCP 工具目录更新。 |
| `opencode.event.unknown` | 未识别 opencode raw event 的兼容兜底。 |

## SSE 续传

- SSE `event` 使用 RunEvent 的 `type`。
- Phase 02/03 后，SSE `event` 使用 `RunEventType.wireName()`，例如 `tool.finished`。
- durable SSE `id` 使用 RunEvent 的 `seq` 字符串。
- transient SSE 不设置 `id`，避免浏览器把非持久化事件作为 `Last-Event-ID`。
- 客户端断线后携带 `Last-Event-ID`，后端按当前 runId 返回 `seq > Last-Event-ID` 的 durable 事件。
- 浏览器原生 `EventSource` 不能设置自定义请求头；前端首次续传可使用 `GET /api/runs/{runId}/events?lastEventId={seq}` 或 `GET /api/internal/platform/opencode-runtime/runs/{runId}/events?lastEventId={seq}`。后端 header 优先，query 参数作为浏览器兼容入口。
- 如果 `Last-Event-ID` 缺失，默认从当前订阅策略允许的起点开始返回。
- 如果 `Last-Event-ID` 非数字或小于 0，后端返回统一错误格式，错误码为 `VALIDATION_ERROR`。
- 消息内容、文本增量和日志/tool output 不从本地 `run_events` 恢复；SSE 建连时后端会先尝试从 opencode `GET /api/session/{sessionID}/message` 拉取 projected messages，并转换为 transient `message.updated` / `message.part.updated` snapshot 事件。

## Phase 04 Runtime SSE

`GET /api/runs/{runId}/events` 是旧兼容入口，`GET /api/internal/platform/opencode-runtime/runs/{runId}/events` 是新平台入口。两者都是前端消费平台 RunEvent 的实时入口，返回 `text/event-stream`，共享同一续传、traceId、错误格式和事件模型。

示例：

```text
id: 2
event: run.started
data: {"eventId":"evt_...","runId":"run_...","seq":2,"type":"run.started","traceId":"trace_...","occurredAt":"2026-06-19T00:00:00Z","payload":{"status":"RUNNING"}}
```

transient 示例没有 SSE `id`：

```text
event: message.part.delta
data: {"eventId":"evt_live_...","runId":"run_...","seq":0,"type":"message.part.delta","traceId":"trace_...","occurredAt":"2026-06-19T00:00:00Z","payload":{"messageID":"msg_...","partID":"part_...","delta":"hello"}}
```

实现策略：

- SSE 合并两个来源：durable replay 继续按 `run_events` 查询可恢复事件；live bus 即时发送当前进程新产生的 durable 和 transient 事件。
- durable replay 每次按 `runId + lastSeq` 查询增量事件，默认批量上限 100。
- **durable 事件可能重复投递**：落库的 durable 事件既经 live bus 即时下发，又可能在下一轮 replay 轮询中被查出（live 推送与轮询游标推进存在竞态）。同一 durable 事件携带稳定的 `evt_` 前缀 `eventId`，前端必须按 `eventId` 去重；transient 事件 `eventId` 为 `evt_live_` 前缀且 `seq=0`，同样按 `eventId` 去重。
- **live bus 为单机进程内通道**：`RunEventLiveBus` 基于 Reactor `Sinks`，只服务当前进程已连接的 SSE 订阅，不跨进程广播。单实例部署下实时性最佳；多实例部署下，落在其他实例的 durable 事件由 replay 轮询兜底（延迟回升到轮询间隔），transient 消息内容事件由建连时的 opencode session snapshot 恢复兜底，不会丢数据但实时性下降。多实例场景需引入 Redis pub/sub 等跨进程通道，新增前必须先补架构与安全文档例外。
- polling 查询必须 offload 阻塞式 Repository；单次回放查询失败不改变 Run 状态，后端跳过本轮轮询并在下一轮继续尝试，客户端仍按既有 SSE 续传规则处理。
- 客户端断开时释放 Flux 订阅。
- `Last-Event-ID` 解析委托 `RunEventReplayService`；非法值映射为 `VALIDATION_ERROR`。
- SSE body 使用 `RunEventSsePayload`，不返回 generated SDK DTO 或 opencode raw event。
- `message.updated`、`message.part.updated`、`message.part.delta`、`assistant.message.delta` 等消息内容投影事件只进入 live bus；`run.*`、`diff.*`、`permission.*`、`question.*`、`todo.updated` 和关键 tool 状态继续入库。
- `tool.finished` 入库前会移除 `rawPayload`、`output`、`input`、`metadata` 等大字段，只保留 tool/call/message/part/status/title/error 等摘要。

Phase 08 后，opencode raw event 的终态映射为：

- `session.next.step.ended` -> `run.succeeded`，应用服务同时把 Run 状态更新为 `SUCCEEDED`。
- `session.status` 且 `payload.status.type=idle` -> `run.succeeded`，兼容 opencode 1.17.8 的完成信号。
- `session.idle` -> `run.succeeded`，兼容 opencode 1.17.8 的完成信号。
- `session.next.step.failed` -> `run.failed`，应用服务同时把 Run 状态更新为 `FAILED`。
- `session.error` -> `run.failed`，应用服务同时把 Run 状态更新为 `FAILED`。
- `message.updated`、`message.part.updated`、`message.part.delta` 等 opencode App 事件进入同名 transient SSE，用于 Phase 11 message timeline；不写入 `run_events`。
- `permission.*`、`question.*`、`todo.updated`、`vcs.branch.updated`、`lsp.updated`、`mcp.tools.changed` 进入同名平台 RunEvent，用于 Phase 11 运行态同步。

本地 RunEvent 持久化异常不是 opencode 终态事件，不能单独生成 `run.failed` 或把 Run 状态改为 `FAILED`；前端可通过 SSE 重连和后续事件继续恢复视图。

Diff 动作事件：

```text
id: 12
event: diff.accepted
data: {"eventId":"evt_...","runId":"run_...","seq":12,"type":"diff.accepted","traceId":"trace_...","occurredAt":"2026-06-19T00:00:00Z","payload":{"action":"accept","status":"accepted","fileCount":2}}
```

```text
id: 13
event: diff.rejected
data: {"eventId":"evt_...","runId":"run_...","seq":13,"type":"diff.rejected","traceId":"trace_...","occurredAt":"2026-06-19T00:00:00Z","payload":{"action":"reject","status":"rejected","fileCount":2}}
```

前端处理：

- `diff.proposed` 更新 Changed Files 和 DiffActionCard。
- `diff.accepted` / `diff.rejected` 展示动作结果并保留 traceId。
- 终态 `run.succeeded` / `run.failed` / `run.cancelled` 必须停止“运行中”状态。

订阅建议：

- 首次订阅不传 `Last-Event-ID`。
- 断线重连传上次成功处理的 SSE `id`。
- 客户端必须优先按 `eventId` 去重；缺失 `eventId` 的旧事件才回退按 `runId + seq` 去重，允许同一事件重复投递。
- 客户端必须忽略未知 payload 字段和未知 event name。

## 兼容性

1. 新增事件字段必须保持旧前端可忽略。
2. 删除或重命名字段属于破坏性变更，必须提供迁移说明。
3. 新事件类型必须有默认忽略策略。
4. opencode raw event 不直接透传；已知事件映射为平台稳定类型，未知事件映射为 `opencode.event.unknown` 并保留安全的 `rawType`、`rawEventId`、`rawPayload`。
5. RunEvent payload 当前以 JSON 文本持久化；后续切换 PostgreSQL JSONB 时必须保持读取兼容。
