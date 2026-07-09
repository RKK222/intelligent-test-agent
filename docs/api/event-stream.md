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
8. root `run.succeeded/run.failed/run.cancelled` 是 Run 终态事实源；`Streaming response failed` 等 prompt_async 提交错误、SSE 订阅 transport error 或浏览器连接错误没有独立业务终态含义，先到时只可延迟收敛为失败，窗口内若收到 root 终态则不得补写旧 `run.failed`；若临时失败已经先落库，后到 root 终态仍可按最后 root 终态纠正 Run 状态和最终快照。
9. stale active Run 后台收敛也会追加既有 `run.failed` 事件。该事件只表示平台侧长时间未收到有效运行输出且本地后台订阅可能失效，不代表后端主动取消或中止远端 opencode 会话。

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
| `run.failed` | Run 失败结束，payload 可携带安全的 `message` 或 `error.message/name` 供前端失败卡片展示。 |
| `run.cancelled` | Run 已取消。 |
| `assistant.message.delta` | 助手消息增量，只实时发送，不写入 `run_events`。 |
| `message.updated` | opencode Web App message projection 更新；实时发送，断线后由 opencode session messages snapshot 恢复。 |
| `message.removed` | opencode Web App message projection 移除。 |
| `message.part.updated` | message part 内容或状态更新；实时发送，断线后由 opencode session messages snapshot 恢复。 |
| `message.part.removed` | message part 移除。 |
| `message.part.delta` | message part 流式增量，只实时发送，不写入 `run_events`。 |
| `session.diff` | session 级 Diff 状态更新。 |
| `session.status` | session busy/idle/status 更新。 |
| `session.error` | session 错误事件；root session error 额外派生 `run.failed`，child error 不改变 Run 终态。 |
| `session.created` | opencode session 创建事件，payload 可携带 `parentID`。 |
| `session.updated` | opencode session 更新事件。 |
| `session.deleted` | opencode session 删除事件。 |
| `session.child.discovered` | 平台发现 child session 并纳入当前 Run scope。 |
| `session.scope.updated` | 当前 Run session scope 更新。 |
| `todo.updated` | Todo 列表更新。 |
| `tool.started` | 工具调用开始。 |
| `tool.finished` | 工具调用结束，payload 使用 `status` 区分 success/failed。 |
| `diff.proposed` | 智能体提出文件 Diff；Run 运行中也会从完成的写文件工具派生实时 Diff。 |
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
| `reference.updated` | reference 目录或状态更新。 |
| `file.edited` | 文件编辑事件。 |
| `file.watcher.updated` | 文件 watcher 状态更新。 |
| `opencode.event.unknown` | 未识别 opencode raw event 的兼容兜底。 |

## `todo.updated`

`todo.updated` 表示当前 opencode session 的 Todo 列表整体刷新。平台后端只把 opencode raw event 映射为同名 RunEvent，不改写 Todo 结构；前端必须把每次事件视为列表快照，而不是单项增量。

payload 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `sessionID` / `sessionId` | string | Todo 所属 opencode session。后端可能同时补充 lower camel alias。 |
| `todos` | array | opencode 原生 Todo 列表。兼容旧前端测试和历史 payload 时也允许读取 `todo` 或 `items`。 |

`todos[]` 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `content` | string | 任务简述，前端映射为 `TodoItem.text`。 |
| `status` | string | 当前状态。已知值：`pending`、`in_progress`、`completed`、`cancelled`。 |
| `priority` | string | 优先级。已知值：`high`、`medium`、`low`。 |

前端展示：

- `pending` 显示为“待处理”，`in_progress` 显示为“进行中”，`completed` 显示为“已完成”，`cancelled` 显示为“已取消”。
- 未知 `status` 保留原始字符串展示，并归入“其他”数量。
- opencode 原生 Todo 没有稳定 `id` 字段，前端在缺少 `id/todoId/todoID` 时按数组位置和内容生成展示用 key。
- 右侧对话面板在输入框上方显示 Todo 面板：收起态展示各状态数量和总数，展开态展示完整 Todo 列表。

## `question.asked`

`question.asked` 表示当前 Run 需要用户回答一个或多个问题。平台事件保留 opencode 原生问题字段，不要求后端把字段提前改写成前端展示模型；前端 reducer 负责兼容归一化。`message.part.updated` 中 `part.type=tool`、`part.tool=question` 只作为时间线工具过程展示，不得生成提问面板。

payload 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` / `requestId` / `requestID` | string | 提问请求 ID，前端归一化为 `QuestionRequest.requestId`。 |
| `sessionID` / `sessionId` | string | 提问所属 opencode session。 |
| `questions` | array | opencode 原生问题数组；兼容历史 payload 时，缺少数组也允许把 payload 自身视为一个问题。 |
| `tool` | object | 可选工具调用上下文，例如 `messageID/callID`，用于调试和时间线关联。 |

`questions[]` 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `question` / `text` / `prompt` | string | 问题正文，前端归一化为 `QuestionRequest.questions[].text`。 |
| `header` | string | 可选问题短标题，前端作为辅助信息展示。 |
| `options` | array | 可选选项列表。 |
| `multiple` | boolean | `true` 表示多选；`false` 且存在 `options` 时表示单选。 |
| `custom` | boolean | 是否允许自定义答案的兼容字段；当前前端分页面板始终提供“输入自己的答案”。 |
| `required` | boolean | 可选必答标记；缺失时前端按现有待答问题处理。 |

`options[]` 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `label` | string | 展示和提交使用的选项文本。 |
| `description` | string | 可选选项说明。 |

前端归一化规则：

- `multiple:true` 归一化为 `kind:"multiple"`。
- 存在 `options` 且不是 `multiple:true` 时归一化为 `kind:"single"`。
- 没有 `options` 时归一化为 `kind:"text"`。
- `options[].label/description` 必须保留；提交答案时使用用户选择的 `label` 文本，而不是本地展示用 id。
- 自定义答案作为该问题的有效答案：单选题中与选项互斥，多选题中作为附加答案提交。

回复接口仍使用 HTTP `POST /api/internal/agent/{agentId}/sessions/{sessionId}/questions/{requestId}/reply`，body 为：

```json
{
  "answers": [["需求文档"], ["需要更轻量"]]
}
```

`answers` 外层数组顺序必须与 `questions[]` 顺序一致，内层数组为该问题的一组答案文本。

## 用户会话运行态 fetch SSE

`GET /api/internal/platform/opencode-runtime/sessions/runtime-state/events` 是用户级历史运行状态提醒通道，用于历史按钮运行计数、旋转图标和 `question.asked` 铃铛。该通道使用 fetch SSE，而不是浏览器原生 `EventSource`，因为请求必须携带当前用户的 `Authorization: Bearer ...`。

事件类型：

| event name | 说明 |
|---|---|
| `session-runtime.snapshot` | 建连后的首帧当前快照。 |
| `session-runtime.updated` | 后续摘要发生变化时推送。 |

SSE `id` 使用本次摘要的 `generatedAt` 字符串，只用于客户端调试和日志关联，不作为可持久续传游标。客户端断线重连后应直接重新建立连接并接收新的 `session-runtime.snapshot`。

data 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `runningCount` | number | 当前用户可见 ACTIVE 历史会话中，最近 Run 为 `PENDING/RUNNING/CANCELLING` 的会话数。 |
| `questionCount` | number | 上述运行中会话里，最新 question 状态仍为 `question.asked` 的会话数。 |
| `sessions` | array | 单会话运行态列表，同一会话最多一条最近非终态 Run。 |
| `generatedAt` | string | 后端生成本次摘要的 ISO-8601 时间。 |

`sessions[]` 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `sessionId` | string | 平台 session id。 |
| `runId` | string | 最近非终态 Run id。 |
| `runStatus` | string | `PENDING/RUNNING/CANCELLING`。 |
| `attention` | string/null | 当前仅支持 `"QUESTION"`，没有待关注事项时为 `null`。 |
| `attentionEventId` | string/null | 触发待答提醒的 `question.asked` 事件 id，仅用于展示/去重，不是续传游标。 |
| `attentionAt` | string/null | 触发待答提醒的事件时间。 |
| `updatedAt` | string | Run 更新时间。 |

服务端触发规则：

- `run.created/run.started/run.cancelling/run.succeeded/run.failed/run.cancelled` 会触发摘要刷新。
- `question.asked/question.replied/question.rejected` 会触发摘要刷新。
- 低频轮询作为兜底，避免本机或 Redis 实时触发丢失时状态长期不更新。
- 该通道只推送摘要，不推送消息正文、工具输出或单 Run durable replay；点击历史会话后仍使用 session-tree/messages 和 active-run 恢复。

## stale active `run.failed`

当 `StaleActiveRunReconcileTaskHandler` 扫描到超过 2 小时仍处于 `PENDING/RUNNING/CANCELLING` 的 Run 时，服务端会先检查 Redis 运行态：

- `test-agent:run-output-activity:{runId}`：30 分钟 TTL，存在代表最近仍有用户可见输出。
- `test-agent:run-pending-ask:{runId}`：无固定 TTL，存在代表最新状态仍停在未处理 ask；当前 ask 类事件包括 `permission.asked` 和 `question.asked`，收到 `permission.replied/question.replied/question.rejected` 或 Run 终态后清理。

只有两个 Redis 状态都不存在，且 CAS 确认 Run 仍是 active 状态时，后端才把 Run 标记为 `FAILED` 并追加 `run.failed`。该判断不通过数据库 RunEvent 反查 ask 状态，避免历史事件与当前运行态不一致。

payload 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `message` | string | 固定为“运行超时，后台订阅已失效或长时间无输出”。 |
| `error.name` | string | 固定为 `StaleActiveRunTimeout`。 |
| `error.message` | string | 同 `message`。 |
| `reason` | string | 固定为 `STALE_ACTIVE_RUN_TIMEOUT`。 |
| `activeTimeoutSeconds` | number | 默认 7200。 |
| `recentOutputWindowSeconds` | number | 默认 1800。 |

兼容性说明：这是已有 `run.failed` 事件的新增 payload 来源，前端继续按失败终态展示即可；旧客户端会忽略新增的 `reason/activeTimeoutSeconds/recentOutputWindowSeconds` 字段。

## `session.status`

`session.status` 表示 opencode session 的运行状态变化。平台会保留 opencode 原生 `status` 对象，前端必须兼容 `payload.status` 为字符串或对象两种形态。

当 `payload.status.type="retry"` 时，表示当前 Run 因模型供应商临时限制、网络或服务端重试策略进入等待重试状态。典型 payload：

```json
{
  "status": {
    "type": "retry",
    "attempt": 1,
    "message": "Free usage exceeded, subscribe to Go",
    "action": {
      "label": "subscribe",
      "link": "https://opencode.ai/go"
    },
    "next": 1783296000963
  }
}
```

retry 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `status.type` | string | `retry` 表示等待重试。 |
| `status.attempt` | number | 当前重试次数，从 1 开始。 |
| `status.message` | string | 面向用户展示的重试原因。 |
| `status.action` | object | 可选操作建议，例如订阅入口。前端当前只保留字段，不主动跳转。 |
| `status.next` | number | opencode 原生下一次重试时间戳；前端展示统一按 60 秒倒计时处理。 |

前端展示处理：

- `session.status.retry` 在右侧时间线展示原因和“重试中 N 秒后 - 第 X 次 / 共 3 次”。
- 等待 retry 时前端运行态仍视为运行中，不出队 busy follow-up，不关闭 RunEvent SSE，也不显示失败卡。
- 前端按固定 60 秒倒计时展示每次 retry；最多等待 3 次。第 1/2 次倒计时结束后可 best-effort 取消当前等待 Run，并用最近一次 Run 草稿自动新建 Run；第 3 次倒计时结束前若收到后续消息、非 retry 状态或 `run.*` 终态，以后续事件为准；若倒计时结束后仍没有新状态，前端本地把对话收敛为失败并展示最近一次 retry message。
- 后端 `run.succeeded/run.failed/run.cancelled` 仍是持久 Run 终态事实源；前端 retry 失败兜底只用于避免浏览器一直停留在运行中。

## SSE 续传

- SSE `event` 使用 RunEvent 的 `type`。
- Phase 02/03 后，SSE `event` 使用 `RunEventType.wireName()`，例如 `tool.finished`。
- durable SSE `id` 使用 RunEvent 的 `seq` 字符串。
- transient SSE 不设置 `id`，避免浏览器把非持久化事件作为 `Last-Event-ID`。
- 客户端断线后携带 `Last-Event-ID`，后端按当前 runId 返回 `seq > Last-Event-ID` 的 durable 事件。
- 浏览器原生 `EventSource` 不能设置自定义请求头；前端首次续传优先使用 `GET /api/internal/agent/{agentId}/runs/{runId}/events?lastEventId={seq}`，默认 `agentId=opencode`。内部平台入口 `GET /api/internal/platform/opencode-runtime/runs/{runId}/events?lastEventId={seq}` 继续有效；旧 `GET /api/runs/{runId}/events?lastEventId={seq}` 已作废，返回 `410 API_GONE`。后端 header 优先，query 参数作为浏览器兼容入口。
- 如果 `Last-Event-ID` 缺失，默认从当前订阅策略允许的起点开始返回。
- 如果 `Last-Event-ID` 非数字或小于 0，后端返回统一错误格式，错误码为 `VALIDATION_ERROR`。
- 消息内容、文本增量和日志/tool output 不从本地 `run_events` 恢复；SSE 建连时后端通过当前 `AgentRuntime.messages` 拉取 projected messages，并转换为 transient `message.updated` / `message.part.updated` snapshot 事件。快照恢复与 durable replay、本机 live bus、远端广播并发订阅，不能让较慢的快照查询阻塞实时 delta 下发。当前 `opencode` 实现适配 opencode 标准 `GET /session/{sessionID}/message`，并通过远端 cursor 分页恢复。opencode workspace 级事件流由 opencode-client 保留 raw/mapped DTO 边界，事件是否属于当前 Run 的 root/child scope 由 runtime `RunSessionScopeRouter` 判定；显式属于未知 session 的事件不会按 root 入库。当前 Run 收到 root 成功/失败终态后结束远端订阅，避免同一会话后续轮次串流。前端刷新恢复时用 `GET /api/internal/agent/{agentId}/sessions/{sessionId}/session-tree/messages` 加载历史树；如需平台 messageId 映射，再用 `GET /api/internal/platform/opencode-runtime/sessions/{sessionId}/messages?refresh=false` 只读数据库快照，并用 `GET /api/internal/platform/opencode-runtime/sessions/{sessionId}/active-run` 判断是否重新订阅本 Run SSE。

## Run Session Scope

RunEvent payload 可包含以下 scope 字段，前端必须允许这些字段缺失或新增：

| 字段 | 说明 |
|---|---|
| `rootSessionId` | 当前 Run scope 的 root opencode session ID。 |
| `sessionId` | 事件所属 opencode session ID。 |
| `parentSessionId` | child session 的父 session ID，root 为空。 |
| `isChildSession` | 是否 child session。 |
| `taskMessageId` | 发现 child session 的任务消息 ID。 |
| `taskPartId` | 发现 child session 的任务 part ID。 |
| `taskCallId` | 发现 child session 的任务调用 ID。 |
| `scopeVersion` | 事件映射时的 Run scope 版本。 |

scope 发现与缓存规则：

- child discovery 来源包括 task/tool part metadata 中的 `sessionID/sessionId`、`session.created/session.updated` 的 `parentID/parentId`，以及 `session.children(root)` bootstrap 候选。
- 原生 opencode task 子 Agent 是两阶段事件：先出现 root `message.part.updated` 的 `part.type=tool`、`part.tool=task`、`state.status=pending`，此时通常没有 child session；随后 `session.created/session.updated` 带 `parentID/parentId` 创建 child session。runtime `RunSessionScopeRouter` 会按 `runId + parentSessionId` 维护 pending task FIFO 队列，在 child session 事件没有 task 字段时补齐最早未绑定 task 的 `taskMessageId/taskPartId/taskCallId`。
- `message.part.updated` 的 task metadata 也可能直接携带 child `sessionId/sessionID`；这只用于发现 child 和补齐 `session.child.discovered/session.scope.updated`，原始 task part 仍属于 root assistant message，payload scope 必须保持 `sessionId=rootSessionId`、`isChildSession=false`。
- child session metadata 会从 `info.agent` 和 `info.title` 提取展示信息；`title` 会去掉形如 `(@explore subagent)` 的 opencode 原生后缀后写入 `session.child.discovered/session.scope.updated` payload。
- opencode `payload.type=sync`、`payload.syncEvent.type=message.part.updated.1` 这类包装会在 mapper 层还原为内层事件类型、事件 ID 和 data；direct 事件与 sync 包装共享同一个 raw event id 时由 runtime dedup 过滤，避免重复 pending task 或重复 child discovery。
- 当前 Run 只纳入本 Run 启动后发现，或能绑定到本 Run task part 的 child；历史 root 下已有 child 不会无条件进入新 Run scope。
- `session.child.discovered` 是 durable 事件，表示 child 已纳入当前 Run scope；payload 会携带 `source=TASK_PART/SESSION_EVENT/BOOTSTRAP` 和 scope metadata。
- `session.scope.updated` 是 durable 事件，当前用于 `SESSION_ADDED`，紧随 `session.child.discovered` 输出，便于前端或历史投影更新 session tree 索引。
- child 事件早于 discovery 到达时会进入运行态 Redis pending list：`test-agent:run-scope:{runId}:pending:{sessionId}`，TTL 30 分钟；child 纳入 scope 后先发送 `session.child.discovered` 和 `session.scope.updated`，再按原始发生顺序 drain pending 事件，最后处理触发 discovery 的当前事件。
- Redis dedup key 为 `test-agent:run-scope:{runId}:dedup:{sessionId}:{rawEventId}`，TTL 30 分钟；`rawEventId` 缺失时不写 Redis dedup。Redis 不可用时降级为 DB-only，不阻断 Run 主链路。
- Redis 还会缓存运行中 scope 和反向索引：`test-agent:run-scope:{runId}:active`、`test-agent:run-scope:{runId}:sessions`、`test-agent:run-scope:{runId}:session:{sessionId}`、`test-agent:run-scope:session:{sessionId}:runs`，TTL 30 分钟；DB 仍是恢复事实源。
- opencode raw event id 缺失时 payload 不应包含 `rawEventId`，数据库 `run_events.raw_event_id` 保持 `NULL`。由 root session 事件派生的 `run.succeeded/run.failed` 不复用原始 `rawEventId`，避免与对应 `session.status/session.error` 误去重；派生事件 payload 会带 `derived=true`、`derivedFromRawType`，有原始事件 ID 时还带 `derivedFromRawEventId`。
- payload 对常见 opencode 大写 ID 字段保留原字段并补充 lower camel alias：`sessionID -> sessionId`、`messageID -> messageId`、`partID -> partId`、`callID -> callId`、`requestID -> requestId`。前端必须允许两种字段并存。
- `heartbeat`、`server.heartbeat`、`tui.*`、`pty.*`、`workspace.*`、`worktree.*`、`installation.*`、`plugin.*`、`catalog.*` 等不带 session 归属的全局 opencode unknown 事件默认不进入 Run 对话事件流；已知 root/child session 的未知事件仍以 `opencode.event.unknown` 保留。

前端展示处理：

- `@test-agent/agent-chat` 的 RunEvent reducer 会把 `message.updated`、`message.part.updated`、`message.part.delta` payload 中的 `sessionId/sessionID`、`rootSessionId`、`parentSessionId`、`isChildSession/childSession`、`taskMessageId`、`taskPartId`、`taskCallId` 归入运行期 `messageScopesById` 索引，key 使用消息 ID。
- 为兼容历史调试或 raw opencode 事件包装，前端 reducer 在归并前会展开 `payload.properties`，再按同一套 `messageID/partID/sessionID` 和 lower camel 字段读取消息、part 与 scope；标准 RunEvent 仍以扁平 payload 为主。
- `message.part.updated` 的 `part.type=tool` 且 `part.tool=task` 时，前端从 `part.state.metadata.sessionId/sessionID` 或 payload scope 识别子会话，并生成 `SubagentSession`：标题优先取 `state.title`，再取 `state.input.description`、`state.input.prompt` 首行；Agent 名称优先取 `state.input.subagent_type`，再取 `metadata.agent`，缺失时展示 `Task`；状态优先取 `part.state.status`。
- 若历史 live payload 同时携带 `sessionId=child`、`sessionID=root`、`isChildSession=true` 和 root `part.sessionID`，前端按 root task part 兼容处理，避免同一个 root message scope 被覆盖成 child。
- `session.child.discovered` 和 `session.scope.updated` 到达时，前端用 payload 中的 `sessionId`、`parentSessionId`、`taskMessageId`、`taskPartId`、`taskCallId` 补全子会话索引和 `taskPartId -> sessionId` 映射。
- `session.status` 的 `payload.status` 可能是字符串，也可能是 opencode 原生对象。当前已知对象形态包含 `type`、`attempt`、`message`、`action` 和 `next`；当 `status.type=retry` 时，前端必须把它归一为运行期 `runtimeStatus.type=retry`，用平台 `eventId` 作为本地 retry key，并在第一次收到该 retry 事件时启动固定 60 秒倒计时。时间线展示“重试中 N 秒后 - 第 X 次 / 共 3 次”、上游 `message` 和可选 `action.link`，等待期间不能继续只显示普通“思考中”。
- 原生两阶段场景下，未绑定的 root task part 会先显示为不可点击“智能体 / 准备中”；收到带 `taskPartId` 的 child discovery 后，同一个入口转为 `Explore + title` 并可点击。
- 主 Agent 视图过滤 `messageScopesById[messageId].isChildSession=true` 的 user/assistant 输出，只保留 root 输出和 root task tool part 卡片；task 子 Agent 卡片始终独立展示，不参与普通 `tool-group` 折叠；点击 task 卡片后切到对应 child session 视图。若后续 `message.part.removed`、`message.removed` 或 snapshot 缺少原始 task part，但 `subagentsBySessionId/subagentByTaskPartId` 仍有绑定索引，前端会在主视图合成一个导航入口，避免子 Agent 卡片短暂出现后消失。
- 子 Agent 视图只展示 `messageScopesById[messageId].sessionId` 等于当前 child session 的完整时间线，不展示 composer、Todo、permission/question 输入区。缺少 scope 的历史消息按 root 消息兼容处理。

终态派生规则：

- `session.status` 的 `status.type=idle` 和 `session.idle` 均规范化为 `session.status`。
- `session.status` 的 `status.type=retry` 不派生 `run.failed` 或 `run.succeeded`，也不更新 Run 终态；它表示上游仍在等待重试或需要用户处理限额/订阅等 action，前端应作为非终态运行状态展示。前端不使用 opencode 原生 `next` 作为展示时间，第 1/2 次本地 60 秒倒计时结束后可 best-effort 取消当前等待 Run 并用同一请求草稿新建 Run；第 3 次倒计时结束仍无后续事件时，本地收敛为失败以避免页面永久运行中。
- root session idle 额外派生 `run.succeeded`；child session idle 只发送 `session.status`。
- root `session.error` 额外派生 `run.failed`；child `session.error` 只发送 `session.error`。
- `session.next.step.ended` 不再派生 `run.succeeded`，只作为兼容未知事件保留上下文。
- 后端处理 root 终态时必须先读取当前 Run，并把 root 终态作为最终事实保存；后到 root 终态可以纠正先到的 transport error 临时失败并刷新终态快照。`Streaming response failed` 等 transport error 没有独立业务终态含义，应给 root 终态短暂到达窗口；窗口内没有 root 终态时才在 Run 仍非终态的前提下收敛为 `run.failed`，并在 payload 中保留单行、长度受限的安全错误说明，供前端解释为什么停止输出。

## Phase 04 Runtime SSE

`GET /api/internal/agent/{agentId}/runs/{runId}/events` 是 agent-scoped RunEvent 实时入口，前端默认使用 `agentId=opencode`。`GET /api/internal/platform/opencode-runtime/runs/{runId}/events` 是内部平台入口。旧 `GET /api/runs/{runId}/events` 已作废，返回 `410 API_GONE`。有效入口返回 `text/event-stream`，共享同一续传、traceId、错误格式和事件模型，payload 格式不随 agentId 改变。

应用配置管理、版本库部署模式配置和个人 SSH key 管理不产生 RunEvent，也不新增 SSE 事件类型。`/api/internal/platform/configuration-management/**` 的版本库创建/编辑/列表、部署模式选项查询和个人 SSH key 维护均通过 HTTP 同步返回；设置页创建应用工作空间接口虽然会触发初始版本工作区 clone/checkout 和运行态 Workspace 创建，但进度写入 `workspace_create_operations` 并由 `GET /api/internal/platform/configuration-management/workspace-create-operations/{operationId}` HTTP 轮询读取；不通过 RunEvent SSE 发布“校验、保存配置、解析版本、下载代码、创建运行态工作区、完成/失败”等步骤。

应用版本工作区和个人工作区管理接口也不产生 RunEvent/SSE。`/api/internal/platform/workspace-management/applications/**`、`/workspace-versions/**`、`/personal-workspaces/**` 会执行 Git clone/worktree/diff/push 并创建或切换运行态 `Workspace` 配置，但不会启动 Session/Run；后续 opencode 对话仍只通过 Run API 产生 RunEvent。多服务器下应用版本工作区同步使用后端内部服务器广播，不暴露给浏览器 SSE。

Agent 配置管理接口不产生 RunEvent/SSE。`/api/internal/platform/workspace-management/agent-config/**` 的公共级/工作空间级 Git 更新、worktree、commit、publish 进度通过 ticket 保护的 WebSocket `/operations/{operationId}/ws?ticket=...` 推送 `snapshot`、`step`、`completed`、`failed`，也可通过 `GET /operations/{operationId}` 查询快照；公共级 worktree 切换只改变后续文件 WebSocket route/ticket 的 `worktreeId/linuxServerId` 绑定，不产生 RunEvent。该进度不写入 `run_events`，不参与 RunEvent `Last-Event-ID` 续传。

当前用户 opencode 进程初始化进度不产生 RunEvent/SSE。`POST /api/internal/agent/{agentId}/processes/me/initialize` 传入 `operationId` 时，后端把校验、确认分配、选择容器、准备参数、进程启动、记录候选进程、检查进程、健康检查、写入绑定和完成/失败写入 `opencode_process_start_operations`；前端通过 `GET /api/internal/agent/{agentId}/processes/me/initialize-operations/{operationId}` HTTP 轮询读取。该只读查询不触发 manager health/start，不写 RunEvent，也不参与 `Last-Event-ID` 续传。

旧 opencode-manager 诊断 HTTP API 已作废，返回 `410 API_GONE`；`/api/internal/platform/opencode-runtime/manager/ws` 控制面 WebSocket 不产生 RunEvent/SSE，不向前端广播注册、Redis 心跳、后端列表发现或命令结果。manager 只连接本服务器 Java；`backendListRequest/backendListResponse` 仅作为兼容协议保留，Java 收到 `backendListRequest` 会忽略。控制面除 `command`/`commandResult` 等帧外，还包含 manager→后端的 `configRequest` 和后端→manager 的 `configUpdate` 控制帧：manager 注册成功或重连后主动发送一次 `configRequest`，后端经完整 `configUpdate` 下发 `common_parameters` 中的 `OPENCODE_MANAGER_MAX_PROCESSES`、`OPENCODE_SESSION_DIR`、`OPENCODE_PUBLIC_CONFIG_DIR`；`OPENCODE_PUBLIC_CONFIG_DIR` 下的公共 `opencode.jsonc` 是模型和供应商事实源，企业部署需保持运行用户 `~/.config/opencode` 不维护模型或供应商，避免 OpenCode 合并全局配置污染公共目录；后续前端只允许修改最大进程数，后端收到跨 Java 参数刷新广播后只向本服务器 managers 下发 max-only `configUpdate`。该帧不进入 RunEvent 流，不向前端推送。

超级管理员运行管理页调用的 `GET /api/internal/platform/opencode-runtime/management/overview` 读取 Redis 中仍在线的 Java/manager 运行快照和 manager 管理的本地 opencode server 明细；“容器 / 管理进程”合并表、展开后的“有主进程 / 无主进程”分组、启动时间、端口、PID、baseUrl、启动命令和归属字段都来自该 HTTP overview。底部用户 opencode server 列表改为通过 `GET /api/internal/platform/opencode-runtime/management/user-processes?keyword=...` 按用户查询，只在进程所属服务器就是当前 Java 时通过公共状态查询服务调用本机 manager health；远端进程返回 `REMOTE_SERVER/CHECK_SKIPPED`，避免随机 Java 控制其他服务器 manager。容器行内“趋势”按钮调用容器 metrics history HTTP API，后端 Java 行按 `linuxServerId` 调用 `GET /api/internal/platform/opencode-runtime/management/linux-servers/{linuxServerId}/backend-metrics` 读取 Redis 48 小时指标历史；旧 `backendProcessId` history API 已作废，返回 `410 API_GONE`。有主/无主进程明细和底部用户进程列表中的“重启/停止”或“重启”按钮调用运行管理 HTTP POST 命令端点，入口后端会通过统一 Java 路由解析器把请求路由到容器所属服务器 Java，再由目标 Java 控制本服务器 manager；已有平台用户进程记录的端口必须通过公共启动服务完成 restart/start 后公共状态查询确认为运行中才同步返回成功，停止命令必须通过公共停止服务完成 manager stop 后公共状态查询确认为未启动才同步返回成功。该结果不写入 RunEvent，也不新增 SSE 事件类型。运行管理不向 RunEvent 流发布拓扑、连接、进程状态、启动命令、归属状态、控制命令结果或监控指标变化。

超级管理员定时任务管理页调用的 `/api/internal/platform/scheduler-management/**` 只维护 scheduler 任务定义和运行记录，不新增 SSE 事件类型，也不向 RunEvent 流发布任务状态变化；页面刷新通过 HTTP 查询完成。

AI 回复满意度反馈接口 `/api/internal/platform/opencode-runtime/messages/{messageId}/feedback` 只写入 `ai_message_feedbacks` 事实表，不产生 RunEvent，不通过 SSE 推送反馈状态；当前用户刷新或重新进入会话时通过 `GET .../feedback/me` 查询自己的反馈。运营分析页 `/api/internal/platform/analytics/**` 只读取 hourly/daily rollup、水位和明细查询接口，不订阅 RunEvent，也不新增 SSE 事件类型。反馈、Diff、Run 状态和 token 等运营指标由后台 rollup runner 定期从事实表聚合，主链路不在 RunEvent 里补发统计事件。

## Internal Server Broadcast

内部服务器广播不是浏览器事件流。它用于一台后端把跨服务器业务事件 fan-out 到其他后端实例，当前稳定事件为应用版本工作区副本同步和公共 Agent 配置同步。

传输：

- 默认空实现；多服务器部署通过 `test-agent.server-broadcast.enabled=true` 开启 Redis pub/sub。
- 默认 channel：`test-agent:server-broadcast`，可通过 `test-agent.server-broadcast.channel` 覆盖。
- Redis 广播是实时增强通道，不保证持久投递；应用版本工作区通过数据库 `target_commit_hash` 和本机补偿扫描恢复漏消息。

事件 envelope：

```json
{
  "eventId": "sbe_...",
  "type": "workspace.version.sync-requested",
  "originInstanceId": "10.8.0.11-...",
  "originLinuxServerId": "10.8.0.11",
  "traceId": "trace_...",
  "occurredAt": "2026-06-26T00:00:00Z",
  "payload": {
    "reason": "CREATED",
    "versionId": "awv_...",
    "applicationWorkspaceId": "awp_...",
    "appId": "app_...",
    "repositoryId": "repo_...",
    "version": "20260707",
    "branch": "feature_testagent_20260707",
    "userId": "usr_...",
    "targetCommitHash": "abc123..."
  }
}
```

`workspace.version.sync-requested` 的 `reason` 当前包括 `CREATED`、`EXISTING_VERSION`、`SYNC_TO_APPLICATION`、`GIT_PULL_REQUESTED`、`GIT_PULLED`。payload 不允许携带 SSH 私钥、token、Authorization、Cookie 或文件内容；远端节点使用 `userId` 在本机业务服务内读取该用户已加密保存的 SSH key，并在当前服务器上 clone/fetch/reset 到目标 commit。消费者必须跳过 `originLinuxServerId` 与本机相同的事件，避免本机重复执行。

`common-parameter.refresh-requested` 用于通用参数 `value` 修改后的跨实例联动。某实例 `PATCH` 修改参数后，本地广播器发布该广播并发布本地 `CommonParameterReloadedEvent`；其他实例收到后发布本地 `CommonParameterReloadedEvent`，监听方直接从数据库读取最新参数并向本实例持有的 opencode manager 下发最新运行配置。远端处理不再转发广播，避免循环；消费者跳过 `originInstanceId` 与本机相同的事件。payload 只携带参数标识，不携带参数值（各实例自行从库读取，避免值在总线明文）：

```json
{
  "englishName": "OPENCODE_MANAGER_MAX_PROCESSES",
  "platform": "all",
  "parameterId": "param_opencode_manager_max_processes",
  "traceId": "trace_..."
}
```

`agent-config.public-sync-requested` 用于公共 Agent 配置更新或发布后的多服务器同步，payload 只允许包含：
```json
{
  "branch": "main",
  "commitHash": "abc123...",
  "reason": "publish"
}
```

消费者在本机公共配置 Git 根目录工作树 clean 时 fetch/checkout/reset 到指定 commit；dirty、未配置或非 Git 仓库时跳过，不覆盖本机修改。该广播不暴露给浏览器，也不通过 RunEvent SSE 下发。

## Platform File WebSocket

平台文件 WebSocket 不产生 RunEvent/SSE，属于前端工作区文件和 Agent 配置文件操作的受控双向 RPC 通道。工作区文件先调用 `POST /api/internal/platform/workspace-management/workspaces/{workspaceId}/file-ws-route` 定位目标后端；Agent 配置文件先调用 `POST /api/internal/platform/workspace-management/agent-config/file-ws-route` 定位目标后端。旧 `/api/workspaces/{workspaceId}/file-ws-route` 和 HTTP 文件接口已作废，返回 `410 API_GONE`。公共 Agent 直接目录模式必须在 route 请求中提供已初始化服务器 `linuxServerId`，公共 worktree 模式由 `worktreeId` 落库服务器决定目标。随后都在目标后端调用 `POST /api/internal/platform/workspace-management/file-ws/tickets` 创建一次性 ticket，最后连接：

```text
/api/internal/platform/workspace-management/file/ws?ticket=wft_...
```

客户端请求 envelope：

```json
{
  "id": "req_1",
  "op": "workspace.read",
  "params": {
    "workspaceId": "wrk_...",
    "path": "src/App.java"
  }
}
```

成功响应：

```json
{
  "id": "req_1",
  "type": "result",
  "data": {},
  "traceId": "trace_..."
}
```

错误响应：

```json
{
  "id": "req_1",
  "type": "error",
  "code": "FORBIDDEN",
  "message": "路径越权",
  "traceId": "trace_...",
  "details": {}
}
```

当前稳定操作：

| `op` | `params` | 响应 |
|---|---|---|
| `workspace.list` | `workspaceId`, `path?` | `FileTreeEntryResponse[]` |
| `workspace.search` | `workspaceId`, `query` | `FileSearchResultResponse[]`；递归搜索文件名（不区分大小写子串匹配），跳过黑名单目录，结果按文件名排序并限制数量 |
| `workspace.read` | `workspaceId`, `path` | `FileContentResponse` |
| `workspace.write` | `workspaceId`, `path`, `content` | `null` |
| `workspace.status` | `workspaceId`, `path` | `FileStatusResponse` |
| `workspace.delete` | `workspaceId`, `path` | `null`；仅删除普通文件，目录删除返回统一错误 |
| `agent-config.list` | `scope`, `workspaceId?`, `worktreeId?`, `path?` | `FileTreeEntryResponse[]`；用于公共级/工作空间级 Agent 配置文件 |
| `agent-config.read` | `scope`, `workspaceId?`, `worktreeId?`, `path` | `FileContentResponse` |
| `agent-config.write` | `scope`, `workspaceId?`, `worktreeId?`, `path`, `content` | `null`；仅 `SUPER_ADMIN` |
| `directory.list` | `path?` | `WorkspaceDirectoryListResponse`；用于服务器工作空间选择器 |
| `workspace.create` | `name`, `rootPath` | `WorkspaceResponse`；仅 `SUPER_ADMIN` 且目标服务器与当前 agent 同服务器时允许 |

服务端必须按 ticket 绑定的 workspace、服务器和模式校验请求：workspace 操作的 `workspaceId` 必须等于 ticket 绑定值；Agent 配置操作的 `scope/workspaceId/worktreeId` 必须等于 ticket 绑定值，公共直接目录模式还必须受 ticket 绑定的 `linuxServerId` 约束；`directory.list` 仅 `directory-picker` ticket 可用；`workspace.create` 必须由 `SUPER_ADMIN` 创建，并且工作空间服务器与当前用户 opencode 进程服务器一致。客户端必须按 `id` 匹配响应，允许未知字段，收到错误 envelope 后按统一错误码处理。

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

- SSE 合并三个来源：durable replay 继续按 `run_events` 查询可恢复事件；本机 live bus 即时发送当前进程新产生的 durable 和 transient 事件；Redis run-event bus 开启时接收其他实例发布的 live event。
- durable replay 每次按 `runId + lastSeq` 查询增量事件，默认批量上限 100。
- **durable 事件可能重复投递**：落库的 durable 事件既经 live bus 即时下发，又可能在下一轮 replay 轮询中被查出（live 推送与轮询游标推进存在竞态）。同一 durable 事件携带稳定的 `evt_` 前缀 `eventId`，前端必须按 `eventId` 去重；transient 事件 `eventId` 为 `evt_live_` 前缀且 `seq=0`，同样按 `eventId` 去重。
- `RunEventLiveBus` 基于 Reactor `Sinks`，默认只服务当前进程已连接的 SSE 订阅。live bus 是 best-effort 实时通道：客户端消费过慢、断开或并发发布产生背压时，后端可以丢弃当前 live 帧，但不得让全局 live bus 进入 error/complete；durable 事件仍可通过 `run_events` replay 恢复，transient 消息内容仍依赖消息快照恢复。设置 `TEST_AGENT_RUN_EVENT_REDIS_BUS_ENABLED=true` 或 `test-agent.run-event.redis-bus.enabled=true` 后，后端会把 durable/transient `RunEventSsePayload` 发布到 Redis channel `test-agent:run-events`，消息包含 `originInstanceId`；本机收到自己发布的消息会忽略，其他实例收到后转发给本机 SSE 客户端。Redis 不可用、未启用或发布/订阅失败时自动降级为本机 live bus + durable replay。
- polling 查询必须 offload 阻塞式 Repository；单次回放查询失败不改变 Run 状态，后端跳过本轮轮询并在下一轮继续尝试，客户端仍按既有 SSE 续传规则处理。
- 客户端断开时释放 Flux 订阅。
- `Last-Event-ID` 解析委托 `RunEventReplayService`；非法值映射为 `VALIDATION_ERROR`。
- SSE body 使用 `RunEventSsePayload`，不返回 generated SDK DTO 或 opencode raw event。
- `message.updated`、`message.part.updated`、`message.part.delta`、`assistant.message.delta` 等消息内容投影事件只进入 live bus；`run.*`、`diff.*`、`permission.*`、`question.*`、`todo.updated` 和关键 tool 状态继续入库。
- `tool.finished` 入库前会移除 `rawPayload`、`output`、`input`、`metadata` 等大字段，只保留 tool/call/message/part/status/title/error 等摘要。
- `message.part.updated` 本身仍是 transient；当其中的 `write`、`edit`、`apply_patch` tool part 进入 `completed` 状态时，后端会额外派生 durable `diff.proposed`。payload 只保留 `source=tool`、`tool`、`messageID/messageId`、`partID/partId` 和 `files[]`，不会持久化原始 `rawPayload`、完整 `input/output` 或 tool `metadata`。
- Skill 调用不新增 `skill.*` wire name；opencode 中的 Skill 仍作为 tool 上报，前端仅在 `tool.started`、`tool.finished`、`message.part.updated`、`message.part.delta` 中根据 `payload.tool`、`payload.toolName` 或 ToolPart `toolName=skill` 分类为 Skill 调用展示。

Phase 08 后，opencode raw event 的终态映射为：

- `session.next.step.ended` -> `opencode.event.unknown`，不再派生 `run.succeeded`。
- root `session.status` 且 `payload.status.type=idle` -> `session.status + run.succeeded`，应用服务同时把 Run 状态更新为 `SUCCEEDED`；child idle 只保留 `session.status`。
- root `session.idle` -> `session.status + run.succeeded`，应用服务同时把 Run 状态更新为 `SUCCEEDED`；child idle 只保留 `session.status`。
- root `session.next.step.failed` / `session.error` -> `run.failed` 或 `session.error + run.failed`，应用服务同时把 Run 状态更新为 `FAILED`；child error 只保留 `session.error`。
- `message.updated`、`message.part.updated`、`message.part.delta` 等 opencode App 事件进入同名 transient SSE，用于 message timeline；不写入 `run_events`。
- `session.diff` 保持映射为 `session.diff`；`diff.proposed` 只由写入类 tool part 完成等平台派生路径产生。
- `permission.*`、`question.*`、`todo.updated`、`vcs.branch.updated`、`lsp.updated`、`mcp.tools.changed`、`reference.updated`、`file.edited`、`file.watcher.updated` 进入同名平台 RunEvent，用于运行态同步。

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

- `diff.proposed` 更新 Changed Files 和 DiffActionCard；前端始终按运行中的 `diff.proposed.files[].path` 强制刷新变更文件父目录和工作区 Git Diff，开启实时追踪时再使用 `additions/deletions/status` 刷新文件树行数并跟随打开最近变化文件。`write` 工具事件只承担实时变更通知，不调用 opencode 不支持的 `mode=working` Diff；精确 patch/行数由工作区 Git Diff 返回。
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

## manager 控制面补充

manager WebSocket `command` 帧支持可选 `environment` 字段。Java 启动用户 opencode server 时通过该字段注入 `TEST_AGENT_INTERNAL_PROXY_API_KEY`、`TEST_AGENT_INTERNAL_PROXY_BASE_URL` 和 `ICBC_UCID`；manager 生成的 `startCommand` 只允许展示代理 base URL 和 UCID，`TEST_AGENT_INTERNAL_PROXY_API_KEY` 必须显示为 `<redacted>`。
