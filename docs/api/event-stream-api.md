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
2. 每个 runId 内 seq 单调递增。
3. SSE event id 使用 seq 或可恢复的稳定 ID。
4. 前端断线后通过 Last-Event-ID 续传。
5. 不把 opencode raw event 原样透传给前端。
6. generated SDK 事件必须在 `test-agent-event` 或 `test-agent-opencode-client` 映射为平台事件。

## RunEvent 基础字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `eventId` | string | 平台事件 ID，使用 `evt_` 前缀。 |
| `runId` | string | 所属 Run ID，使用 `run_` 前缀。 |
| `seq` | number | 同一 runId 内从 1 开始单调递增。 |
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
| `assistant.message.delta` | 助手消息增量。 |
| `tool.started` | 工具调用开始。 |
| `tool.finished` | 工具调用结束，payload 使用 `status` 区分 success/failed。 |
| `diff.proposed` | 智能体提出文件 Diff。 |
| `test.finished` | 测试执行结束。 |
| `opencode.event.unknown` | 未识别 opencode raw event 的兼容兜底。 |

## SSE 续传

- SSE `event` 使用 RunEvent 的 `type`。
- Phase 02/03 后，SSE `event` 使用 `RunEventType.wireName()`，例如 `tool.finished`。
- SSE `id` 使用 RunEvent 的 `seq` 字符串。
- 客户端断线后携带 `Last-Event-ID`，后端按当前 runId 返回 `seq > Last-Event-ID` 的事件。
- 如果 `Last-Event-ID` 缺失，默认从当前订阅策略允许的起点开始返回。
- 如果 `Last-Event-ID` 非数字或小于 0，后端返回统一错误格式，错误码为 `VALIDATION_ERROR`。

## Phase 04 Runtime SSE

`GET /api/runs/{runId}/events` 是前端消费平台 RunEvent 的唯一实时入口，返回 `text/event-stream`。

示例：

```text
id: 2
event: assistant.message.delta
data: {"eventId":"evt_...","runId":"run_...","seq":2,"type":"assistant.message.delta","traceId":"trace_...","occurredAt":"2026-06-19T00:00:00Z","payload":{"text":"hello"}}
```

实现策略：

- 初版使用 Repository polling 增量读取，避免只依赖本机内存广播。
- 每次按 `runId + lastSeq` 查询增量事件，默认批量上限 100。
- 客户端断开时释放 Flux 订阅。
- `Last-Event-ID` 解析委托 `RunEventReplayService`；非法值映射为 `VALIDATION_ERROR`。
- SSE body 使用 `RunEventSsePayload`，不返回 generated SDK DTO 或 opencode raw event。

订阅建议：

- 首次订阅不传 `Last-Event-ID`。
- 断线重连传上次成功处理的 SSE `id`。
- 客户端必须按 `seq` 去重，允许同一事件重复投递。
- 客户端必须忽略未知 payload 字段和未知 event name。

## 兼容性

1. 新增事件字段必须保持旧前端可忽略。
2. 删除或重命名字段属于破坏性变更，必须提供迁移说明。
3. 新事件类型必须有默认忽略策略。
4. opencode raw event 不直接透传；已知事件映射为平台稳定类型，未知事件映射为 `opencode.event.unknown` 并保留安全的 `rawType`、`rawEventId`、`rawPayload`。
5. RunEvent payload 当前以 JSON 文本持久化；后续切换 PostgreSQL JSONB 时必须保持读取兼容。
