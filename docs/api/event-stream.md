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

1. RunEvent 只追加，不更新；物化 snapshot 是恢复投影，不是对历史事件的原地修改。
2. durable RunEvent 在每个 runId 内 seq 单调递增。`LEGACY_FULL` 写入 `run_events`；`REDIS_SUMMARY` 把 durable 事件同时写入 `${seq}-0` 的 `events` Stream、`${runtimeVersion}-0` 的 `runtime-events` Stream 和物化 snapshot，不写 `run_events`。
3. transient live output 不写入 `run_events` 或 Redis durable `events` Stream，payload `seq=0`，SSE 不设置 `id`；新模式会把 transient 事件写入 `${runtimeVersion}-0` 的 `runtime-events` Stream 并更新物化 snapshot，本机 live bus 只作为 SSE 低延迟唤醒信号。
4. durable SSE event id 使用 seq；transient event 不参与 `Last-Event-ID` 恢复。
5. 前端断线后通过 Last-Event-ID 续传 durable RunEvent。legacy 从数据库事件和按稳定 dispatch user 因果裁剪后的 opencode projected messages 恢复；新模式首先用 Redis 物化 snapshot reset 恢复当前可见状态，再按 `runtimeVersion` 从 `runtime-events` Stream 连续读取 durable/transient 尾流，不触发兼容远端消息 snapshot。
6. 不把 opencode raw event 原样透传给前端，也不把大段日志、bash/tool output 或高频文本 delta 作为平台持久化事件保存。
7. generated SDK 事件必须在 `test-agent-event` 或 `test-agent-opencode-client` 映射为平台事件。
8. root `run.succeeded/run.failed/run.cancelled` 是 Run 终态事实源；`prompt_async` 与 `/session/{sessionID}/command` 的调用完成异常不论错误语言或包装类型都只是候选失败，统一等待 300ms 根终态窗口。窗口内若收到 root `idle/session.error` 派生终态则不得补写旧 `run.failed`；窗口结束仍无 root 终态时才追加一次失败。真正的 SSE 订阅 transport error 或浏览器连接错误没有独立业务终态含义，继续按运行态丢失/重连规则处理；若旧服务已先落临时失败，后到 root 终态仍可纠正 Run 状态和最终快照。
9. stale active Run 后台收敛也会追加既有 `run.failed` 事件。该事件只表示平台侧长时间未收到有效运行输出且本地后台订阅可能失效，不代表后端主动取消或中止远端 opencode 会话。
10. `SIDE_QUESTION` Run 使用同一 RunEvent SSE：`side_question.started/progress` 为 durable，`side_question.delta` 为 transient；最终完整答案只以 `run.succeeded.payload.answer` 为权威结果，供客户端校准断线期间遗漏的 delta。
11. 前端 `onRawMessage` 捕获的 RunEvent SSE `MessageEvent.data` 只用于页面“原始输出”观察副本；它与 HTTP 原始报文共用预缓存安全处理，递归脱敏所有层级、大小写不敏感的 `contextToken` 后再截断和缓存。该处理不改写交给 RunEvent reducer 的实际事件，但禁止原始 SSE 数据绕过脱敏直接进入调试缓存。
12. Run 的 `storageMode` 由创建时 manifest 固定，活动期间禁止切换。manifest 缺失表示 legacy/旧数据；Redis 新模式运行态缺失或不可用时返回 `RUN_DETAILS_EXPIRED` / `RUNTIME_STATE_UNAVAILABLE`，不得回退数据库或 JVM 内存读取原始详情。
13. 已认证前端以标量 `(runId, sessionId, token)` 标识单 Run fetch SSE，同一逻辑 Run 同时最多保留一条应用层订阅；Run 对象的 status 投影、冲突终态纠正和标题等待不得重建连接。终态先保留 500ms 稳定窗口，普通终态随后关闭，标题待定则复用原连接直到标题同步或 watch closed；连接内游标、事件去重与 transport reconnect 仍由公共 event-stream client 维护。
14. 企业同源部署将前端 API base URL 显式配置为空，RunEvent 与用户级运行态 SSE 客户端必须保留 `/api/...` 相对地址交给浏览器按当前 origin 解析；不得用缺少 origin 的 `new URL("/api/...")` 构造地址。前后端分离部署仍使用配置的绝对 base URL。
15. 已认证 fetch SSE 可携带页面内存中的 `X-Test-Agent-Linux-Server-Id`，RunEvent 与用户级运行态 SSE 使用同一动态值；空值不发送。它只供 Nginx 做静态白名单首跳，Nginx 转发前删除，后端仍按 Run/用户归属执行权威校验和跨 Java 兜底。旧客户端或无法自定义 header 的原生 EventSource 不发送时继续使用默认 upstream，事件格式和恢复语义不变。

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
| `run.created` | Run 已创建；前端据其 `runId` 绑定当前根用户消息。`REDIS_SUMMARY` 仍可额外携带 `storageMode/clientRequestId/assistantSummaryMessageId` 供摘要定位兼容。 |
| `run.started` | Run 已开始执行。 |
| `run.cancelling` | Run 正在取消。 |
| `run.succeeded` | Run 成功结束。 |
| `run.failed` | Run 失败结束，payload 可携带安全的 `message` 或 `error.message/name` 供前端失败卡片展示。 |
| `run.cancelled` | Run 已取消。 |
| `run.snapshot.reset` | Redis 新模式 SSE 首帧及容量换代后的 transient 物化快照重置；无 SSE `id`，不推进 `Last-Event-ID`。 |
| `side_question.started` | 宠物旁路问答已开始，durable；主对话模式 payload 的 `sessionId` 指向主平台 Session，无主对话的手册模式则携带 `knowledgeBase=user-manual`，均不暴露临时远端 Session。 |
| `side_question.progress` | 宠物旁路问答真实阶段，durable；主对话模式使用 `forking/reading/composing`，手册模式使用 `preparing_context/reading/composing`。旧客户端仍可兼容历史 `compacting/tool` 值，新流程不会启动工具链。 |
| `side_question.delta` | 宠物旁路答案文本增量，transient，不写 `run_events`、不设置 SSE `id`。 |
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
| `session.updated` | opencode session 更新事件；root session 标题成功同步到平台 Session 时，payload 会带平台确认标记和标题。 |
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
| `permission.asked` | 权限请求；payload 保留 OpenCode 请求标识、权限类型和 `patterns[]`。 |
| `permission.replied` | 权限回复；payload 的 `requestID/requestId` 与对应 asked 请求收敛。 |
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

`permission.asked` 的原生请求标识可能位于顶层 `id`，回复可能使用 `requestID`；平台按顶层 `requestId/requestID/id/permissionId/questionId` 兼容匹配，不能误取嵌套 option 的 `id`。前端 `PermissionRequest` 优先保留 `patterns[]`，回退旧 `pattern`；展示标题默认“需要权限”，已知权限说明与 OpenCode 1.18.4 中文文案一致，未知权限仍只展示通用标题和路径，不暴露内部 permission type 或 request id。路径只出现在已授权用户的交互卡中，不进入铃铛通知文案或日志。

task part 指向新 session 时，`parentSessionId` 必须等于发起该 task 的 session，而不是固定 root。root task 建立 child，child task 建立 grandchild；task part 本身仍归属于发起它的 scope。当前运行基线强制 `subagent_depth=2`，前端按精确 `sessionId` 隔离 root、child 和 grandchild 时间线，SSE 字段结构不变。

## `run.snapshot.reset`

`run.snapshot.reset` 只用于 Redis 运行数据面恢复。新模式每次 SSE 建连首帧都发送当前完整物化 snapshot；snapshot 从 Redis input 的专用保护投影生成本轮 USER 消息，并包含后续最终可见投影，因此不依赖浏览器仍保留乐观输入。前端在重放前逐条把 root `permission.asked/question.asked` 的远端 session ID 投影为当前订阅的平台 Session ID，明确带 `isChildSession=true` 的事件保持 child session ID，避免重连后根交互卡被 scope 过滤。建连后以 `snapshot.runtimeVersion` 为 Redis 内部尾流游标；最短 5 秒的 Redis 安全扫描和 live bus 只唤醒按 `runtimeVersion` 分页读取 `runtime-events`，live 事件仍即时唤醒。若连接期间容量换代使游标早于 `earliestRuntimeVersion`，服务端再次发送完整 reset，而不静默跳过事件；容量裁剪至少保留 USER 输入、JSON role 为 assistant 的最新 message、与其 messageId 对应的最新可见 text part（delta 仅 `field=text`，full part 缺 type 时保守保留）和 run-status，tool/reasoning 或非 assistant 投影不得替代这些关键状态。本事件是 transient：payload `seq=0`，SSE 不设置 `id`，不能写回或替代 durable 游标。Run 已终态后晚到的 `run.created/run.started/run.cancelling` 会在 Lua 入口被丢弃，不进入 runtime Stream、run-status snapshot 或 live bus，避免在线与重连视图回退为运行中。

payload 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `reason` | string | 初始状态恢复为 `TRANSIENT_SNAPSHOT_RECOVERY`；durable 游标早于详情保留起点为 `CURSOR_BEFORE_EARLIEST_OR_DETAILS_TRUNCATED`；活跃连接的 runtime 游标落后为 `RUNTIME_STREAM_TRUNCATED`。 |
| `resetGeneration` | number | 每次显式详情截断递增；用于区分不同快照代次。 |
| `earliestSeq` | number | 当前 Redis Stream 可恢复区间的最早 seq。 |
| `detailsAvailableUntil` | string | 当前 Run 详情预计失效时间，ISO-8601。 |
| `snapshot.barrierSeq` | number | 物化 snapshot 对应的 durable 屏障；snapshot events 本身不推进游标。 |
| `snapshot.runtimeVersion` | number | snapshot 对应的 durable/transient 全事件版本，仅供服务端尾流恢复语义和诊断，不是 `Last-Event-ID`。 |
| `snapshot.events` | array | 按 reducer 应用顺序排列的 `RunEventSsePayload[]`；内部事件均按 transient 处理。 |

示例：

```text
event: run.snapshot.reset
data: {"eventId":"evt_snapshot_reset_run_x_2_10520","runId":"run_x","seq":0,"type":"run.snapshot.reset","traceId":"trace_runtime_snapshot","occurredAt":"2026-07-10T08:00:00Z","payload":{"reason":"RUNTIME_STREAM_TRUNCATED","resetGeneration":2,"earliestSeq":10002,"detailsAvailableUntil":"2026-07-11T08:00:00Z","snapshot":{"barrierSeq":10001,"runtimeVersion":10520,"events":[]}}}
```

`REDIS_SUMMARY` 的首个 `run.created` payload 示例：

```json
{
  "status": "PENDING",
  "storageMode": "REDIS_SUMMARY",
  "clientRequestId": "request-uuid",
  "assistantSummaryMessageId": "msg_0123456789abcdef0123456789abcdef"
}
```

`assistantSummaryMessageId` 在 Run 开始时确定，并与终态 PostgreSQL ASSISTANT 摘要行复用同一 ID；反馈已改用 `runId`，该字段只用于摘要消息定位兼容。

前端处理顺序固定为：

1. 只清空该 Run 的 reducer 运行投影（消息/part、工具、Todo、Diff、permission/question、scope 索引和 transient 状态），保留当前订阅的 Run 身份。
2. 按数组顺序应用 `snapshot.events`；数组缺失、为空或含未知事件时按空快照/默认忽略兼容。
3. 再应用服务端随后发送的 durable/transient 尾流事件；只以带 SSE `id` 的 durable 事件更新 `Last-Event-ID`，不得把 reset、snapshot 内部事件或 transient 事件的 `seq=0` 写成游标。

## 宠物旁路问答 RunEvent

旁路 Run 的 durable 顺序为 `run.created → run.started → side_question.started → side_question.progress* → run.succeeded|run.failed`；`side_question.delta` 可穿插在阶段事件之间，但不参与 durable seq 或 `Last-Event-ID` 回放。主对话模式对主会话做临时 fork；无主对话的手册模式创建归档内部 Session 及对应远端临时会话，不 fork 普通 Session。两种模式都只提交本轮问题和可选模型并显式禁用工具，终态后删除远端临时会话。发送前记录临时会话已有 message ID，发送后只接收新增 assistant，不能依赖 OpenCode fork 后可能仍指向历史 assistant 的 `parentID`。正常终态来自新增 assistant finish，漏失事件时以低频消息快照补偿。`run.succeeded.payload` 至少包含 `sideQuestion:true`、完整 `answer`、`compacted:false`，答案因 64 KiB 上限截断时还包含 `truncated:true`；`run.failed.payload.message` 只携带安全错误说明。旁路 SSE 不输出原始 `message.*`、`tool.*`、`session.scope.*` 或临时远端 Session ID。

前端断线重连后会回放 durable progress/terminal，按 `eventId` 去重；最终终态 answer 覆盖当前 delta 缓冲，避免 transient 丢帧或重投导致缺字、重复。关闭浮层只释放该旁路订阅，不取消主 Run，也不取消后端旁路任务。

后台每 5 分钟扫描超过 10 分钟仍 active 的 `SIDE_QUESTION` Run，按内部 Session 持久化映射在原节点 best-effort 删除临时 fork，并复用终态事务 CAS 追加唯一 `run.failed`。若重启发生在映射持久化前，平台 Run 仍收敛失败并记录潜在远端泄漏审计事件；该极窄窗口不宣称跨系统强一致。

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
- RunEvent 外层 `runId` 是 Todo 归属用户轮次的必要边界；root `todo.updated` 与 root `todowrite` part 只更新该 Run 绑定的用户消息，child session Todo 不进入 root 状态块。新请求替代旧 Run 后，旧 Run 的 Todo 与 `run.snapshot.reset` 不得再投影到当前对话；等待标题同步期间仅 `session.updated` 可以继续被消费。
- 历史恢复只使用可归属到具体 root 用户轮次的 `todo.updated/todowrite` 快照，child user 不参与最新轮判断；同轮显式 `todo.updated` 优先于持久化 `todowrite` fallback。session Todo HTTP 没有 Run/轮次字段，非空结果只能在最新 root 轮已有 Todo owner 证据时校准；无法确认时保持不展示，空结果只清空当前轮。runtime-state 接管若不对应本页显式未决请求，则必须等该 Run 的远端 user message 到达后再接受 Todo。

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

回复 HTTP 成功后，平台会立即追加既有 `question.replied` durable 事件，payload 携带 `requestID`、归一化后的 `answers` 和可选 `source="interaction_reply_ack"`。这是对部分 OpenCode 版本未向原订阅发送原生 `question.replied` 的兼容收敛，不新增事件类型。前端用 `question.asked.payload.tool.messageID/callID` 关联原 question tool part，把状态更新为 `completed` 并将答案写入 `metadata.answers`；模型随后真实生成的 assistant 文本仍按普通 `message.*` 展示，不应被该兼容事件删除或折叠。

## 用户会话运行态 fetch SSE

`GET /api/internal/platform/opencode-runtime/sessions/runtime-state/events` 是用户级历史运行状态提醒通道，用于前端派生历史按钮运行计数、旋转图标以及 `question.asked`/`permission.asked` 铃铛；历史按钮直接使用用户级摘要计数，不受历史抽屉已加载分页范围影响。该通道使用 fetch SSE，而不是浏览器原生 `EventSource`，因为请求必须携带当前用户的 `Authorization: Bearer ...`。

事件类型：

| event name | 说明 |
|---|---|
| `session-runtime.snapshot` | 建连后的首帧当前快照。 |
| `session-runtime.updated` | 后续摘要发生变化时推送。 |

SSE `id` 使用本次摘要的 `generatedAt` 字符串，只用于客户端调试和日志关联，不作为可持久续传游标。客户端断线重连后应直接重新建立连接并接收新的 `session-runtime.snapshot`。

前端恢复规则：

- runtime-state fetch SSE 是用户级运行态恢复主入口；前端不在连接期间并行调用 `GET .../sessions/runtime-state`，也不使用固定间隔 HTTP 热轮询。
- 连接失败或关闭后按 1、2、5、10、30 秒退避重连；连续失败时保持 30 秒上限，收到任一合法 snapshot/updated 后重新从 1 秒开始计算下一次故障窗口。
- 只有 SSE 已判定不可用时，当前 Session 才执行一次 `GET .../sessions/{sessionId}/active-run` fallback；同一故障窗口和同一 Session 不重复查询。短连接反复收到摘要后立即断开仍归入同一故障窗口，连接稳定保持 5 秒后才判定恢复，后续新的故障窗口才允许再次 fallback。fallback 请求必须携带当前 outage lease；任一更新的 runtime-state 摘要、稳定恢复或认证重置都会使旧 lease 失效，迟到 HTTP 结果不得覆盖摘要已接管的 Run。
- runtime-state 摘要中的非终态 `runId/runStatus` 可直接接管单 Run SSE；历史切换先复用当前摘要展示正文，再在后台调用一次 active-run 做终态校准，不阻塞历史首屏。active-run 只有在该 Session 被摘要标记为运行中时才触发，避免已结束历史会话增加额外请求。

data 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `runningCount` | number | 当前用户可见 ACTIVE 历史会话中，最近 Run 为 `PENDING/RUNNING/CANCELLING` 的会话数。 |
| `questionCount` | number | 上述运行中会话里，最新 question 状态仍为 `question.asked` 的会话数。 |
| `permissionCount` | number | 上述运行中会话里，最新 permission 状态仍为 `permission.asked` 的会话数；旧后端缺失时客户端按 `0` 或会话 attention 推导。 |
| `sessions` | array | 单会话运行态列表，同一会话最多一条最近非终态 Run。 |
| `generatedAt` | string | 后端生成本次摘要的 ISO-8601 时间。 |

`sessions[]` 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `sessionId` | string | 平台 session id。 |
| `runId` | string | 最近非终态 Run id。 |
| `runStatus` | string | `PENDING/RUNNING/CANCELLING`。 |
| `attention` | string/null | 支持 `"QUESTION"`、`"PERMISSION"`，没有待关注事项时为 `null`。 |
| `attentionEventId` | string/null | 触发待处理提醒的 asked 事件 id，仅用于展示/去重，不是续传游标。 |
| `attentionAt` | string/null | 触发待处理提醒的事件时间。 |
| `updatedAt` | string | Run 更新时间。 |

服务端触发规则：

- `run.created/run.started/run.cancelling/run.succeeded/run.failed/run.cancelled` 会触发摘要刷新。
- `question.asked/question.replied/question.rejected` 会触发摘要刷新。
- `permission.asked/permission.replied` 会触发摘要刷新。
- 低频触发器作为兜底，避免本机实时触发丢失时状态长期不更新；用户已有 Redis 运行态 marker 时，每次摘要刷新只读取 Redis active 索引和 manifest，不轮询 PostgreSQL。未进入新链路的 legacy 用户继续使用现有只读 Repository。
- 该通道只推送摘要，不推送消息正文、工具输出或单 Run durable replay；点击历史会话后仍使用 session-tree/messages 恢复正文，active-run 只作为上述流不可用时的单次 fallback。

## stale active `run.failed`

本节只适用于 `LEGACY_FULL`。`StaleActiveRunReconcileTaskHandler` 的 MyBatis 查询会排除 `REDIS_SUMMARY`；当它扫描到超过 2 小时仍处于 `PENDING/RUNNING/CANCELLING` 的 legacy Run 时，服务端会先检查 Redis 运行态：

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

`REDIS_SUMMARY` 不写上述数据库事件。无 attention 且 Redis manifest 两小时无活动时，服务端启动时及每 30 秒扫描本服务器 active 索引，由公共后端路由和 owner lease/fencing 程序 best-effort cancel 远端，并把 `reason=STALE_ACTIVE_RUN_TIMEOUT` 的 `run.failed` 终态写入 Redis Stream；随后只持久化安全双摘要和 Run 控制面终态。

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

## `session.updated`

`session.updated` 是既有的 opencode session 更新 RunEvent。runtime 仅在事件已被 `RunSessionScopeRouter` 确认为当前 Run 的 root session、且远端 root session ID 与对应平台 Session 绑定一致时，提取有效标题并回写该平台 Session。标题优先读取 `payload.info.title`；为兼容 OpenCode raw/sync 包装，也读取 `payload.rawPayload.properties.info.title`。标题去除首尾空白后为空，或仍是 OpenCode 默认的 `New session - <timestamp>` / `Child session - <timestamp>` 时不回写。

标题成功持久化后，runtime 才会在同一个既有 `session.updated` payload 增加 `platformSessionTitleSynchronized: true` 和 `platformSessionTitle: <已持久化标题>`。首轮 root Run 成功后，如果原生标题尚未到达，平台继续监听同一远端 root session 的 `title` agent 完成消息；完成后读取该远端 session 的最终标题，并合成为同一种既有 `session.updated` 同步事件。等待期间不会创建临时 session、再次调用 title agent 或根据超时生成替代标题。下一轮对话、手动改名、归档/删除会取消等待；等待流断开时只对同一远端 root session 做一次读取补偿，读取不到标题便关闭等待。child session、未知归属事件、远端 root ID 与平台绑定不一致的事件、默认标题、空标题、已被原生标题或用户手动改名的会话、标题仓储持久化失败都不得改变平台 Session 标题；这些事件仍按既有规则持久化和通过 SSE 发布，但不携带上述标记。

前端按既有 wire name `session.updated` 订阅，只消费 `platformSessionTitleSynchronized=true` 与 `platformSessionTitle`，不再从 `info` 或 `rawPayload` 推断标题。只有该事件所属的订阅 Run 绑定的平台 Session 仍是当前会话，且事件不是 child session、平台确认标题有效时，才更新当前会话及已加载历史会话中的标题，并失效 sessions cache；切换历史会话后到达的旧订阅事件不得覆盖新当前会话标题。

兼容性说明：本行为不新增 API、DTO、事件 wire name、数据库/Flyway migration 或 SDK 变更。旧客户端可以忽略这个既有事件；不识别标题同步语义时仍保持原有标题展示。

## SSE 续传

- SSE `event` 使用 RunEvent 的 `type`。
- SSE `event` 使用 `RunEventType.wireName()`，例如 `tool.finished`。
- durable SSE `id` 使用 RunEvent 的 `seq` 字符串。
- transient SSE 不设置 `id`，避免浏览器把非持久化事件作为 `Last-Event-ID`。
- 客户端断线后携带 `Last-Event-ID`，后端按当前 runId 返回 `seq > Last-Event-ID` 的 durable 事件。
- 浏览器原生 `EventSource` 不能设置自定义请求头；前端首次续传优先使用 `GET /api/internal/agent/{agentId}/runs/{runId}/events?lastEventId={seq}`，默认 `agentId=opencode`。内部平台入口 `GET /api/internal/platform/opencode-runtime/runs/{runId}/events?lastEventId={seq}` 继续有效；旧 `GET /api/runs/{runId}/events?lastEventId={seq}` 已作废，返回 `410 API_GONE`。后端 header 优先，query 参数作为浏览器兼容入口。
- 如果 `Last-Event-ID` 缺失，默认从当前订阅策略允许的起点开始返回。
- 如果 `Last-Event-ID` 非数字或小于 0，后端返回统一错误格式，错误码为 `VALIDATION_ERROR`。
- `LEGACY_FULL` 的消息内容、文本增量和日志/tool output 不从本地 `run_events` 恢复；SSE 建连时后端通过当前 `AgentRuntime.messages` 从最新页沿 `before` cursor 查找本 Run 的稳定 USER dispatch ID，只把该 user 的直接 assistant 转换为 transient `message.updated` / `message.part.updated` snapshot 事件。平台 USER、root scope 与 locator 锚点不一致，明确锚点尚未到达，重复 cursor、20 页超限或旧 Run 时间窗内 user 不唯一时都返回空消息投影，不回退“最后一轮”；因此旧轮 `todowrite` 不会被重新标成当前 Run。快照恢复与 durable replay、本机 live bus 并发订阅。`REDIS_SUMMARY` 不订阅该兼容远端 snapshot Flux；每次建连先发完整 Redis 物化 reset，再按 `runtimeVersion` 读取 durable/transient 尾流，容量换代时按上节再次重置 reducer。opencode workspace 级事件流由 opencode-client 保留 raw/mapped DTO 边界，事件是否属于当前 Run 的 root/child scope 由 runtime `RunSessionScopeRouter` 判定；显式属于未知 session 的事件不会按 root 处理。当前 Run 收到 root 成功/失败终态后结束远端订阅，避免同一会话后续轮次串流。

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
- `RunSessionScopeRouter` 为每个订阅保存 known sessions 和 `scopeVersion`；已知 root/child 的稳定事件只命中订阅态，不反查数据库。终态或启动失败后释放该本机状态。
- `LEGACY_FULL` 的旧 scope cache 继续使用 `test-agent:run-scope:{runId}:...` 30 分钟 key，并只在 cache miss 或发现新 child 时兼容访问 `run_session_scopes`；Redis cache 不可用时数据库仍是 legacy 恢复事实源。
- 新 Run 的 root scope metadata 保存与 agent command、平台 USER `remoteMessageId` 一致的 `dispatchMessageId`；自动值由当前 `AgentRuntime` 生成，opencode 使用与原生消息字典序兼容的 `msg_[0-9a-f]{12}[0-9A-Za-z]{14}`，避免后续 user 被排到上一轮 assistant 之前。Run 消息恢复和终态快照必须与其它锚点来源交叉校验。已记录的 child scope 可以独立恢复，但只有当前 Run 已选择的 root 消息允许发现新的 child，旧轮 task part 不能扩张新 Run 子树。
- `REDIS_SUMMARY` 禁止查询或写入 `run_session_scopes`。Run 运行数据面的 scope、dedup 和 pending 能力由 `RunRuntimeStore` 端口定义，单 Run key 必须使用 `test-agent:run:{runId}:...` hash tag；执行订阅携带 owner fencing token，scope/dedup/pending 和事件投影都在 Redis Lua 的首个副作用前校验 token，旧 owner 返回冲突且不会进入 live bus。pending append/drain 同时原子维护统一详情字节预算与更新时间。跨 slot active/history 索引在初始化 Lua 前按“所有运行态中最长物理 TTL 的两倍”保守登记，覆盖上次已经处于最长 TTL 的刷新与下一次最长 TTL 事件后的完整窗口；服务器/用户恢复读取会修复全部索引，普通读路径回读 manifest 清理悬空成员。Redis 不可用时返回 `RUNTIME_STATE_UNAVAILABLE`，不得降级 DB-only。
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
- 子 Agent 视图只展示 `messageScopesById[messageId].sessionId` 等于当前 child session 的完整时间线，不展示 composer 和 Todo；permission/question 仅展示 `request.sessionId` 精确等于当前 child session 的请求。主视图中的 task 子智能体卡片收到匹配的 pending permission 时，在“进行中”等状态文字前显示动态、可访问的铃铛，回复后随 reducer 清除；并行 child 不得互相串铃铛。缺少 scope 的历史消息按 root 消息兼容处理。

终态派生规则：

- `session.status` 的 `status.type=idle` 和 `session.idle` 均规范化为 `session.status`。
- `session.status` 的 `status.type=retry` 不派生 `run.failed` 或 `run.succeeded`，也不更新 Run 终态；它表示上游仍在等待重试或需要用户处理限额/订阅等 action，前端应作为非终态运行状态展示。前端不使用 opencode 原生 `next` 作为展示时间，第 1/2 次本地 60 秒倒计时结束后可 best-effort 取消当前等待 Run 并用同一请求草稿新建 Run；第 3 次倒计时结束仍无后续事件时，本地收敛为失败以避免页面永久运行中。
- root session idle 额外派生 `run.succeeded`；child session idle 只发送 `session.status`。不得用“idle 前尚无 assistant 输出”过滤真实远端终态；平台通过正确的 OpenCode 时序 dispatch ID 防止消息排序错误导致的无输出 idle。
- root `session.error` 额外派生 `run.failed`；child `session.error` 只发送 `session.error`。
- `session.next.step.ended` 不再派生 `run.succeeded`，只作为兼容未知事件保留上下文。
- 后端处理 root 终态时必须先读取当前 Run，并把 root 终态作为最终事实保存；后到 root 终态可以纠正旧服务先落的临时失败并刷新终态快照。后台 dispatch 的响应异常（包括本地化 `PlatformException`）统一给 root 终态 300ms 到达窗口，不再依赖 `Streaming response failed` 等英文字面量；窗口内没有 root 终态时才在 Run 仍非终态的前提下收敛为一次 `run.failed`，并在 payload 中保留单行、长度受限的安全错误说明。`REDIS_SUMMARY` 在该窗口内继续持有原 owner lease 和原事件订阅；根终态沿既有生命周期释放 owner，无根终态时以原 fencing token 追加失败、完成投影再释放，owner 已转移或 manifest 已终态的旧执行者不得写失败。真正的事件流错误仍走运行态丢失与 owner 重新竞争链路。

## 内部模型代理 SSE

`POST /api/internal/platform/opencode-runtime/internal-model-proxy/v1/**` 仅供用户 OpenCode 进程调用，不是前端 RunEvent SSE。Java 只对 `2xx + text/event-stream` 响应使用 `ServerSentEvent` 语义转换：每个事件的 `id/event/retry/comment/data` 语义保留；没有 `reasoning_content` 时把 `data` 中的 `<think>...</think>` 迁移为 `reasoning_content`，已有 textual `reasoning_content` 时整个 delta 原样保留；`[DONE]` 原样保留。代理不会手工追加 `data:`，因此下游不会出现 `data:data:`。

所有非 `2xx` 响应（包括 `4xx + text/event-stream`）和非 SSE 响应按 `DataBuffer` 原样转发，保留状态码、`Content-Type`、`Content-Encoding`、错误正文、`Retry-After` 和 trace header。连接超时为 10 秒，首个响应和首个事件等待为 30 秒，后续事件空闲为 120 秒，不设置整个 SSE 生命周期超时；下游取消会取消到企业内部模型的上游订阅。

## Runtime SSE

`GET /api/internal/agent/{agentId}/runs/{runId}/events` 是 agent-scoped RunEvent 实时入口，前端默认使用 `agentId=opencode`。`GET /api/internal/platform/opencode-runtime/runs/{runId}/events` 是内部平台入口。旧 `GET /api/runs/{runId}/events` 已作废，返回 `410 API_GONE`。有效入口返回 `text/event-stream`，共享同一续传、traceId、错误格式和事件模型，payload 格式不随 agentId 改变。目标 Java 在发出首帧前校验认证用户拥有该 Run；新模式直接比较 Redis manifest `userId` 且不查询 PostgreSQL，legacy/详情过期才回查 Run 与 Session，越权返回 `403 FORBIDDEN` 且不进入 snapshot/replay/live 流。

应用配置管理、版本库部署模式配置和个人 SSH key 管理不产生 RunEvent，也不新增 SSE 事件类型。`/api/internal/platform/configuration-management/**` 的版本库创建/编辑/列表、部署模式选项查询和个人 SSH key 维护均通过 HTTP 同步返回；设置页创建应用工作空间接口虽然会触发初始版本工作区 clone/checkout 和运行态 Workspace 创建，但进度写入 `workspace_create_operations` 并由 `GET /api/internal/platform/configuration-management/workspace-create-operations/{operationId}` HTTP 轮询读取；不通过 RunEvent SSE 发布“校验、保存配置、解析版本、下载代码、创建运行态工作区、完成/失败”等步骤。

应用版本工作区和个人工作区管理接口也不产生 RunEvent/SSE。`/api/internal/platform/workspace-management/applications/**`、`/workspace-versions/**`、`/personal-workspaces/**` 会执行 Git clone/worktree/diff/push/merge 并创建或切换运行态 `Workspace` 配置，但不会启动 Session/Run；后续 opencode 对话仍只通过 Run API 产生 RunEvent。个人发布只从本地提交后的个人 `HEAD` 按白名单投影到 feature worktree；本地提交不推送。feature push 后，多服务器通过内部广播取得固定 `targetCommitHash`，再向当前服务器相关个人 worktree 执行原生 Git merge；dirty 内容不覆盖，冲突保留在本机 Diff。该分支同步不暴露给浏览器 SSE，已打开的文件树/标签仍按现有刷新或重新进入机制重读磁盘。

应用引用资产库的初始化、同步、状态和目录树接口同样不产生 RunEvent/SSE。多服务器副本通过内部 `reference-repository.sync-requested` 广播低延迟唤醒，并通过数据库 generation、租约和定时补偿收敛；该广播不写入 `run_events`，不进入 RunEvent SSE，也不参与 `Last-Event-ID` 续传。

Agent 配置管理接口不产生 RunEvent/SSE。`/api/internal/platform/workspace-management/agent-config/**` 的公共级/工作空间级 Git 更新、worktree、commit、publish 进度通过 ticket 保护的 WebSocket `/operations/{operationId}/ws?ticket=...` 推送 `snapshot`、`step`、`completed`、`failed`，也可通过 `GET /operations/{operationId}` 查询快照；公共 Git 仅 SUPER_ADMIN，应用级 Agent/Skill Git 由 APP_ADMIN（含 SUPER_ADMIN）执行。保存公共个人 Agent/Skill 目录定义或 JSONC 时，`POST /public/runtime-reload` 只把当前用户的受管公共配置软链接切到本人公共 worktree 并调用本人 `/global/dispose`；保存应用个人同类文件时直接只 dispose 本人。公共推送成功后，rollout 在目标用户空闲时恢复共享公共链接并 dispose；应用 Agent/Skill 推送成功后，只对已经合入目标 feature commit 的用户 dispose，不切换公共链接。脏或冲突的个人 worktree 作为 `AWAITING_USER` 数据库补偿任务继续收敛，不延长主 rollout，也不产生新的 RunEvent/SSE 类型；收敛后仍只 dispose 对应用户。ticket 响应返回签发节点的绝对 `ws://`/`wss://` 地址，保证多后台下 upgrade 回到保存一次性 ticket 的同一 JVM；跨节点进度继续由既有服务器广播汇入该节点。以上保存、发布和 dispose 状态不写入 `run_events`，不参与 RunEvent `Last-Event-ID` 续传。

当前用户 opencode 进程初始化进度不产生 RunEvent/SSE。`POST /api/internal/agent/{agentId}/processes/me/initialize` 传入 `operationId` 时，后端把校验、确认分配、选择容器、准备参数、进程启动、记录候选进程、检查进程、健康检查、写入绑定和完成/失败写入 `opencode_process_start_operations`；前端通过 `GET /api/internal/agent/{agentId}/processes/me/initialize-operations/{operationId}` HTTP 轮询读取。该只读查询不触发 manager health/start，不写 RunEvent，也不参与 `Last-Event-ID` 续传。

旧 opencode-manager 诊断 HTTP API 已作废，返回 `410 API_GONE`；`/api/internal/platform/opencode-runtime/manager/ws` 控制面 WebSocket 不产生 RunEvent/SSE，不向前端广播注册、Redis 心跳、后端列表发现或命令结果。manager 只连接本服务器 Java；`backendListRequest/backendListResponse` 仅作为兼容协议保留，Java 收到 `backendListRequest` 会忽略。控制面除 `command`/`commandResult` 等帧外，还包含 manager→后端的 `configRequest` 和后端→manager 的 `configUpdate` 控制帧：manager 注册成功或重连后主动发送一次 `configRequest`，后端经完整 `configUpdate` 下发 `common_parameters` 中的 `OPENCODE_MANAGER_MAX_PROCESSES`、`OPENCODE_SESSION_DIR`、`OPENCODE_PUBLIC_CONFIG_DIR`；Java 按用户建立 `{sessionPath}/.testagent-runtime/current-public-config` 受管软链接，并在 `start.sessionPath/start.configPath` 显式下发，manager 用该路径设置 `OPENCODE_CONFIG_DIR`。链接默认指向共享公共配置，公共个人预览和公共发布只切换指针，不复制配置。`OPENCODE_PUBLIC_CONFIG_DIR` 下的公共 `opencode.jsonc` 是模型和供应商事实源，企业部署需保持运行用户 `~/.config/opencode` 不维护模型或供应商，避免 OpenCode 合并全局配置污染公共目录；后续前端只允许修改最大进程数，后端收到跨 Java 参数刷新广播后只向本服务器 managers 下发 max-only `configUpdate`。该帧不进入 RunEvent 流，不向前端推送。

manager 复用现有 `sessionPath/configPath/environment` 字段，在合并调用方环境后固定派生 `HOME=sessionPath`、`XDG_DATA_HOME=sessionPath`、`XDG_CACHE_HOME=sessionPath/.cache`、`XDG_STATE_HOME=sessionPath/.local/state`、`TMPDIR=sessionPath/.tmp` 和 `OPENCODE_CONFIG_DIR=configPath`。Linux fork 前用户运行目录必须是 `0755` 普通物理目录，冲突或创建失败时命令失败且不写成功 state；公共配置路径仍是 Java 维护的软链接。该变化不新增控制面或 RunEvent 字段，存量进程需受管重启后生效。

超级管理员运行管理页调用的 `GET /api/internal/platform/opencode-runtime/management/overview` 读取 Redis 中仍在线的 Java/manager 运行快照和 manager 管理的本地 opencode server 明细；“容器 / 管理进程”合并表、展开后的“有主进程 / 无主进程”分组、启动时间、端口、PID、baseUrl、启动命令、可空 `unifiedAuthId/managerStatus` 和归属字段都来自该 HTTP overview。旧 manager 或旧 Redis 快照缺少新增字段时保持为空；无平台记录时页面显示“平台未登记”和“未执行 HTTP 健康检查”，不从启动命令解析 UCID，也不自动认领、停止或改绑。底部用户 opencode server 列表改为通过 `GET /api/internal/platform/opencode-runtime/management/user-processes?keyword=...` 按用户查询，只在进程所属服务器就是当前 Java 时通过公共状态查询服务调用本机 manager health；远端进程返回 `REMOTE_SERVER/CHECK_SKIPPED`，避免随机 Java 控制其他服务器 manager。容器行内“趋势”按钮调用容器 metrics history HTTP API，后端 Java 行按 `linuxServerId` 调用 `GET /api/internal/platform/opencode-runtime/management/linux-servers/{linuxServerId}/backend-metrics` 读取 Redis 48 小时指标历史；旧 `backendProcessId` history API 已作废，返回 `410 API_GONE`。有主/无主进程明细和底部用户进程列表中的“重启/停止”或“重启”按钮调用运行管理 HTTP POST 命令端点，入口后端会通过统一 Java 路由解析器把请求路由到容器所属服务器 Java，再由目标 Java 控制本服务器 manager；已有平台用户进程记录的端口必须通过公共启动服务完成 restart/start 后公共状态查询确认为运行中才同步返回成功，停止命令必须通过公共停止服务完成 owner 校验、manager stop 和停止后状态确认才同步返回成功。该结果不写入 RunEvent，也不新增 SSE 事件类型。运行管理不向 RunEvent 流发布拓扑、连接、进程状态、UCID、manager PID 状态、启动命令、归属状态、控制命令结果或监控指标变化。

超级管理员定时任务管理页只调用 `POST /api/internal/platform/xxl-job/sso-tickets`，随后把 XXL Admin 作为同源 iframe 加载；任务启停、Cron、手动触发、停止和日志都留在 XXL HTML/HTTP 边界。该迁移不新增 SSE 事件类型，也不向 RunEvent 流发布 XXL 任务状态或日志；旧 `/api/internal/platform/scheduler-management/**` 返回 `410 API_GONE`。

夜间任务的提交、时段容量、待执行列表、改期、取消和最终失败卡同样只通过 `/api/internal/platform/opencode-runtime/night-execution/**` HTTP 查询/变更，不新增 RunEvent 类型，也不把 `SCHEDULED/DISPATCHING/DISPATCHED/CANCELLED/FAILED` 调度状态写入 RunEvent。XXL/内部批量接口只负责取得普通 Run 的已受理 runId，不等待或发布 Run 终态；`DISPATCHED` 只表示已交给 Run。此后执行过程与前端即时发送完全复用该 Run 的既有 RunEvent SSE、snapshot/replay、终态和用户级 runtime-state；Session、USER 消息和 Run 的 HTTP DTO 可选携带 `sourceType=SCHEDULED_TASK`、`sourceRefId=net_...` 用于展示来源。旧前端忽略新增字段仍可按普通 Run 展示；待执行任务页面必须继续通过 HTTP 轮询和窗口 focus 刷新，不得从 RunEvent 猜测任务状态。

AI 整轮回复反馈接口 `/api/internal/platform/opencode-runtime/runs/{runId}/feedback` 只写入 `ai_message_feedbacks` 事实表，不产生 RunEvent，不通过 SSE 推送反馈状态；前端用既有 Run 终态事件绑定用户轮次，并通过 HTTP 批量接口恢复历史 Run 状态与当前用户反馈。旧消息反馈接口只作兼容。运营分析页 `/api/internal/platform/analytics/**` 只读取 hourly/daily rollup、水位和明细查询接口，不订阅 RunEvent，也不新增 SSE 事件类型。反馈、Diff、Run 状态和 token 等运营指标由后台 rollup runner 定期从事实表聚合，主链路不在 RunEvent 里补发统计事件。

## Internal Server Broadcast

内部服务器广播不是浏览器事件流。它用于一台后端把跨服务器业务事件 fan-out 到其他后端实例，当前稳定事件包括应用版本工作区副本同步、公共 Agent 配置同步、通用参数刷新和引用资产库副本同步。

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

`workspace.version.sync-requested` 的 `reason` 当前包括 `CREATED`、`EXISTING_VERSION`、`SYNC_TO_APPLICATION`、`PERSONAL_PUBLISHED`、`AGENT_CONFIG_PUBLISHED`、`GIT_PULL_REQUESTED`、`GIT_PULLED`。当前应用 Agent/Skill 与 workspace 共用个人 worktree，按个人 `HEAD` 白名单投影成功后使用 `PERSONAL_PUBLISHED`；兼容的旧工作空间 Agent 直发入口仍使用 `AGENT_CONFIG_PUBLISHED`。两条路径都会先更新应用版本与本机副本 HEAD。payload 不允许携带 SSH 私钥、token、Authorization、Cookie 或文件内容；远端节点使用 `userId` 在本机业务服务内读取该用户已加密保存的 SSH key，并在当前服务器上 clone/fetch/reset feature 副本到目标 commit，然后以 `git merge --no-edit <targetCommit>` 反向同步本机相关个人 worktree。dirty/staged/untracked worktree 保留原状并由 Diff 显示待同步；冲突保留 `MERGE_HEAD` 和三方 index。广播只负责服务器间低延迟唤醒，不进入浏览器 SSE；消费者必须跳过 `originLinuxServerId` 与本机相同的事件，避免本机重复执行。

`common-parameter.refresh-requested` 用于通用参数 `value` 修改后的跨实例联动。某实例 `PATCH` 修改参数后，本地广播器发布该广播并发布本地 `CommonParameterReloadedEvent`；其他实例收到后发布本地 `CommonParameterReloadedEvent`，监听方直接从数据库读取最新参数并向本实例持有的 opencode manager 下发最新运行配置。远端处理不再转发广播，避免循环；消费者跳过 `originInstanceId` 与本机相同的事件。payload 只携带参数标识，不携带参数值（各实例自行从库读取，避免值在总线明文）：

```json
{
  "englishName": "OPENCODE_MANAGER_MAX_PROCESSES",
  "platform": "all",
  "parameterId": "param_opencode_manager_max_processes",
  "traceId": "trace_..."
}
```

`reference-repository.sync-requested` 用于应用资产库首次初始化、同分支同步、受控分支切换或只读指针核验 generation 的低延迟唤醒。事件名称保持不变，具体操作由消费者读取数据库 `operation_type` 判断；payload 固定只包含：

```json
{
  "repositoryId": "repo_assets",
  "generation": 2,
  "traceId": "trace_reference_sync"
}
```

发起 Java 在 generation 目标建档后会直接把本机任务提交给有界异步调度器，因此不依赖接收自身广播；其它 Java 的消费者也只负责排队并立即返回，不在 Redis listener 线程执行 Git。所有 worker 仍需按 `repositoryId + generation + 本机 linuxServerId` 从数据库认领带 fencing token 的租约；重复、乱序或丢失广播均不能绕过数据库状态。payload 不包含操作类型、分支、commit、Git URL、用户 ID、SSH 私钥、token、Authorization、Cookie、文件内容或目录内容。广播发布失败不反转已经提交的数据库目标；瞬时失败按数据库 `nextRetryAt` 定向唤醒，默认 60 秒补偿扫描继续重建当前 generation 的在线及历史副本目标，恢复漏消息、调度拒绝、Java 退出、CAS 后建档中断和节点重新上线后的 `DEFERRED` 副本。`VERIFY_POINTERS` 消费路径只读取本地 Git 元数据，不执行 fetch、checkout 或 reset；该内部广播仍不进入 RunEvent SSE。

`agent-config.public-sync-requested` 用于公共 Agent 配置更新或发布后的多服务器同步，payload 只允许包含：
```json
{
  "branch": "main",
  "commitHash": "abc123...",
  "reason": "publish"
}
```

消费者在本机公共配置 Git 根目录工作树 clean 时 fetch/checkout/reset 到指定 commit；dirty、未配置或非 Git 仓库时跳过，不覆盖本机修改。该广播不暴露给浏览器，也不通过 RunEvent SSE 下发。

发布端在远端提交与持久化 rollout 激活确认后发送该事件，但不在发布 HTTP 请求线程认领本机同步；本机和其它服务器由广播消费者或默认 5 秒的数据库补偿扫描异步推进。广播仅用于降低唤醒延迟，丢失或发布失败不影响持久化任务继续执行。

## Platform File WebSocket

平台文件 WebSocket 不产生 RunEvent/SSE，属于前端工作区文件和 Agent 配置文件操作的受控双向 RPC 通道。工作区文件先调用 `POST /api/internal/platform/workspace-management/workspaces/{workspaceId}/file-ws-route` 定位目标后端；Agent 配置文件先调用 `POST /api/internal/platform/workspace-management/agent-config/file-ws-route` 定位目标后端。旧 `/api/workspaces/{workspaceId}/file-ws-route` 和 HTTP 文件接口已作废，返回 `410 API_GONE`。公共 Agent 直接目录模式必须在 route 请求中提供已初始化服务器 `linuxServerId`，公共 worktree 模式由 `worktreeId` 落库服务器决定目标。随后都在目标后端调用 `POST /api/internal/platform/workspace-management/file-ws/tickets` 创建一次性 ticket，最后连接：

```text
/api/internal/platform/workspace-management/file/ws?ticket=wft_...
```

route 响应已经包含目标 Java `baseUrl`，客户端必须在该目标地址申请 ticket 并建立 WebSocket，因此 ticket 的签发和消费始终位于同一 JVM；多后台部署需要浏览器可访问每台 Java 的 `listenUrl`，不新增 Java 到 Java 的 HTTP 文件代理。

文件 RPC 的每条请求和响应仍是单条 JSON 文本消息，但上传和大文件预览都由多条有界 RPC 组成。目标 Java 的单帧上限同时覆盖 `test-agent.files.max-preview-bytes` 以内的一次性 UTF-8 读写、单个预览分段和单个 Base64 上传分片，并附加 RPC envelope 余量；它只限制单条消息，不代表整个上传文件或最终可预览内容的大小。默认一次性预览/可编辑阈值为 5 MiB，超过后前端改用固定约 512 KiB 的 UTF-8 渐进预览分段；用户可继续加载一段或确认加载到文件末尾，界面必须提示完整加载超大文件可能占用较多内存并导致 Monaco 卡顿，大文件始终只读，避免把部分内容误保存。默认上传分片为 256 KiB、可配置上限为 4 MiB。分片上传和渐进预览都不设置应用层文件总大小上限，实际可处理大小仍受浏览器、网络、磁盘空间和基础设施超时约束。

上传必须在同一条文件 WebSocket 连接上依次执行 `begin → chunk* → complete`；服务端为每条连接最多保留 4 个活动上传，并按连接串行处理 RPC，不同连接可由 WebFlux 并发调度。`begin` 在目标同目录创建隐藏临时文件，`chunk` 只解码当前有界分片，`complete` 校验累计字节数后以不覆盖方式安全发布目标文件；完成前目录和搜索接口看不到目标文件。`abort`、连接关闭或分片失败会删除临时文件，超过 24 小时的残留由后续上传尽力清理。

渐进预览使用 UTF-8 字节偏移：首次请求 `offset=0`，后续使用上次响应的 `nextOffset`，并回传首次响应的 `size/lastModifiedMillis` 作为快照栅栏。服务端每段在 UTF-8 字符边界结束，因此中文等多字节字符不会跨段乱码；文件在加载过程中变化时返回 `CONFLICT` 与 `details.reason=PREVIEW_CHANGED`，客户端停止拼接并提示重新打开。每条预览请求仍重新执行 workspace、Agent scope/worktree 或引用 locator 的权限和安全路径校验。

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
| `workspace.view.list` | `workspaceId`, `locator` | `WorkspaceViewListResponse`；工作台文件树的组合视图，`locator.kind` 为 `COMPOSITE / WORKSPACE / REFERENCE`，返回稳定节点身份、来源、只读能力、局部告警和截断标记 |
| `workspace.search` | `workspaceId`, `query` | `FileSearchResultResponse[]`；递归搜索工作区相对路径（不区分大小写子串匹配），空 query 返回受限文件目录；跳过黑名单目录，结果按文件名排序并限制数量 |
| `workspace.read` | `workspaceId`, `path` | `FileContentResponse` |
| `workspace.read.chunk` | `workspaceId`, `path`, `offset`, `expectedSize?`, `expectedLastModifiedMillis?` | `FilePreviewChunkResponse`；渐进读取完整 UTF-8 文件，响应含 `content/nextOffset/size/eof/warningThresholdBytes/lastModifiedMillis` |
| `workspace.view.read` | `workspaceId`, `locator` | `WorkspaceViewFileContentResponse`；读取工作区或引用视图中的 UTF-8 普通文件，引用内容固定只读 |
| `workspace.view.read.chunk` | `workspaceId`, `locator`, `offset`, `expectedSize?`, `expectedLastModifiedMillis?` | `FilePreviewChunkResponse`；每段重新解析和校验逻辑 locator，不接收物理路径 |
| `workspace.write` | `workspaceId`, `path`, `content` | `null` |
| `workspace.upload.begin` | `workspaceId`, `path`, `size` | `{ uploadId, chunkBytes, totalBytes }`；开始不限制总大小的分片上传，目标已存在返回 `CONFLICT` |
| `workspace.upload.chunk` | `workspaceId`, `uploadId`, `index`, `contentBase64` | `{ uploadedBytes, totalBytes }`；`index` 必须从 0 连续递增，单片不得超过服务端返回的 `chunkBytes` |
| `workspace.upload.complete` | `workspaceId`, `uploadId` | `{ size }`；累计大小必须等于 `begin.size`，成功后目标文件才可见 |
| `workspace.upload.abort` | `workspaceId`, `uploadId` | `null`；中止并删除临时文件 |
| `workspace.upload` | `workspaceId`, `path`, `contentBase64` | `null`；旧客户端兼容操作，仍受预览大小上限约束；新客户端必须使用分片操作 |
| `workspace.copy` | `workspaceId`, `sourcePath`, `targetPath` | `null`；仅复制普通文件，不覆盖已有目标 |
| `workspace.move` | `workspaceId`, `sourcePath`, `targetPath` | `null`；既有文件 WebSocket RPC，不新增 HTTP API 或 RunEvent SSE；在同一工作区用一次原子文件系统重命名整体移动普通文件或普通目录（包括非空目录），不递归拆分、不覆盖目标，并以目录句柄和不覆盖重命名阻断目标父目录替换及目标并发创建；同路径幂等成功，缺失源为 `NOT_FOUND`，目标存在为 `CONFLICT`，根、符号链接/特殊文件、目录自身后代目标为 `VALIDATION_ERROR`，路径越界为 `FORBIDDEN` |
| `workspace.rename` | `workspaceId`, `path`, `name` | `null`；仅支持同一父目录内重命名普通文件或目录，目标已存在返回 `CONFLICT` |
| `workspace.status` | `workspaceId`, `path` | `FileStatusResponse` |
| `workspace.delete` | `workspaceId`, `path` | `null`；删除普通文件或递归删除目录树，不跟随符号链接；工作区根目录和任意层级 `.git` 元数据禁止删除 |
| `agent-config.list` | `scope`, `workspaceId?`, `worktreeId?`, `path?` | `FileTreeEntryResponse[]`；用于公共级/工作空间级 Agent 配置文件；Agent Markdown 和 Skill 目录项可选返回 `displayName/displayNameEn` 供中文主名称、英文辅助名称展示，稳定文件身份仍是英文 `path/name` |
| `agent-config.read` | `scope`, `workspaceId?`, `worktreeId?`, `path` | `FileContentResponse` |
| `agent-config.read.chunk` | `scope`, `workspaceId?`, `worktreeId?`, `path`, `offset`, `expectedSize?`, `expectedLastModifiedMillis?` | `FilePreviewChunkResponse`；公共/应用 Agent 大文件渐进只读预览，权限和 ticket 绑定逐段复核 |
| `agent-config.write` | `scope`, `workspaceId?`, `worktreeId?`, `path`, `content` | `null`；公共 scope 仅 `SUPER_ADMIN`，工作空间 scope 仅 `APP_ADMIN`（`SUPER_ADMIN` 继承） |
| `agent-config.upload.begin` | `scope`, `workspaceId?`, `worktreeId?`, `path`, `size` | `{ uploadId, chunkBytes, totalBytes }`；开始不限制总大小的分片上传；公共 scope 仅 `SUPER_ADMIN`，工作空间 scope 仅 `APP_ADMIN`（`SUPER_ADMIN` 继承）并限制在 Agent 配置 Diff 白名单 |
| `agent-config.upload.chunk` | `scope`, `workspaceId?`, `worktreeId?`, `uploadId`, `index`, `contentBase64` | `{ uploadedBytes, totalBytes }`；同一连接内连续上传有界分片 |
| `agent-config.upload.complete` | `scope`, `workspaceId?`, `worktreeId?`, `uploadId` | `{ size }`；校验大小后安全发布且不覆盖同名内容 |
| `agent-config.upload.abort` | `scope`, `workspaceId?`, `worktreeId?`, `uploadId` | `null`；中止并删除临时文件 |
| `agent-config.upload` | `scope`, `workspaceId?`, `worktreeId?`, `path`, `contentBase64` | `null`；旧客户端兼容操作，仍受预览大小上限约束；权限和白名单与分片上传相同 |
| `agent-config.rename` | `scope`, `workspaceId?`, `worktreeId?`, `path`, `name` | `null`；公共 scope 仅 `SUPER_ADMIN`，工作空间 scope 仅 `APP_ADMIN`（`SUPER_ADMIN` 继承）；仅同目录改名，底层复用工作空间文件服务的路径与重名校验 |
| `agent-config.copy` | `scope`, `workspaceId?`, `worktreeId?`, `sourcePath`, `targetPath` | `null`；复制普通文件且不覆盖同名目标，应用级同时校验源和目标白名单 |
| `agent-config.move` | `scope`, `workspaceId?`, `worktreeId?`, `sourcePath`, `targetPath` | `null`；移动文件或目录且不覆盖目标，拒绝目录移入自身后代，应用级同时校验源和目标白名单 |
| `agent-config.delete` | `scope`, `workspaceId?`, `worktreeId?`, `path` | `null`；公共 scope 仅 `SUPER_ADMIN`，工作空间 scope 仅 `APP_ADMIN`（`SUPER_ADMIN` 继承）；文件直接删除，目录递归删除且不跟随符号链接，复用根目录、`.git` 和越界路径保护 |
| `directory.list` | `path?` | `WorkspaceDirectoryListResponse`；用于服务器工作空间选择器 |
| `workspace.create` | `name`, `rootPath` | `WorkspaceResponse`；仅 `SUPER_ADMIN` 且目标服务器与当前 agent 同服务器时允许 |

组合视图根请求示例：

```json
{
  "id": "req_view_root",
  "op": "workspace.view.list",
  "params": {
    "workspaceId": "wrk_...",
    "locator": {
      "kind": "COMPOSITE",
      "path": ""
    }
  }
}
```

`WorkspaceViewListResponse` 示例：

```json
{
  "entries": [
    {
      "id": "mixed:docs",
      "path": "docs",
      "name": "docs",
      "directory": true,
      "size": 0,
      "locator": { "kind": "COMPOSITE", "path": "docs" },
      "source": "MIXED",
      "merged": true,
      "collision": false,
      "readonly": false,
      "workspacePath": "docs",
      "referenceAliases": ["docs-application-assets"]
    }
  ],
  "warnings": [
    {
      "alias": "spec-legacy-assets",
      "code": "CONFLICT",
      "message": "当前服务器引用资产副本尚未就绪"
    }
  ],
  "truncated": false
}
```

节点 `id` 是展开缓存、打开文件身份和 Vue key 的稳定键，客户端不得退化为只用可重复的展示 `path`。`source=REFERENCE` 表示纯引用节点，`source=MIXED` 表示同一逻辑目录同时存在工作区与引用内容；`merged=true` 只描述 `merge=true` 的合并投影。纯引用节点始终 `readonly=true`；混合目录可通过 `workspacePath` 在工作区侧执行新增、上传或粘贴，但不能重命名或删除整棵混合目录。`merge=false` 的别名根保持普通目录样式，其后代仍是只读引用内容。文件同名冲突不会覆盖工作区版本，冲突节点通过 `collision=true` 和不同 `id/locator` 并列返回。

引用定位示例：

```json
{
  "kind": "REFERENCE",
  "path": "docs/guide.md",
  "referenceAlias": "docs-application-assets"
}
```

服务端不得信任客户端提交的引用路径：每次 list/read 都从当前工作区最新 JSONC 重新建立允许集合，并校验应用关联、仓库类型、本机 READY 副本、平台参数、`.git`、符号链接和 root 越界。某个引用配置非法或副本暂不可用时，只跳过该引用并返回 `warnings`；工作区树本身仍可浏览。每层最多 1000 项，达到上限时 `truncated=true`。

服务端必须按 ticket 绑定的 workspace、服务器和模式校验请求：workspace 操作的 `workspaceId` 必须等于 ticket 绑定值，并在路由、ticket 签发和每条 workspace RPC 时重新校验托管工作区的当前用户仍是应用成员；非托管 Workspace 文件访问默认拒绝，仅 ticket 记录为 `SUPER_ADMIN` 的服务器工作空间兼容入口可放行。应用版本副本对普通成员的 write/rename/delete/mkdir 返回只读错误，个人 worktree 普通文件允许 owner 写入；`.opencode/agents/**`、`.opencode/skills/**` 及其 rules/templates 只允许 APP_ADMIN。Agent 配置操作的 `scope/workspaceId/worktreeId` 必须等于 ticket 绑定值，公共直接目录模式还必须受 ticket 绑定的 `linuxServerId` 约束；`directory.list` 仅 `directory-picker` ticket 可用；`workspace.create` 必须由 `SUPER_ADMIN` 创建，并且工作空间服务器与当前用户 opencode 进程服务器一致。客户端必须按 `id` 匹配响应，允许未知字段，收到错误 envelope 后按统一错误码处理。

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

定时任务的 `NIGHT_WINDOW/ADMIN_CUSTOM` 仅影响 Run 创建前的调度时间和夜间容量；两种模式受理后都复用现有 `SCHEDULED_TASK` Session/Run 来源及本章 RunEvent。`scheduleMode`、分发 attempt 和租约不进入 RunEvent payload，本功能不新增事件类型。

实现策略：

- RunEvent SSE 入口优先从 Redis manifest 读取 Run 创建时固定的 `producerLinuxServerId`；manifest 缺失的 legacy/旧 Run 才按 `routing_decisions -> executionNodeId -> opencode process -> linuxServerId` 定位生产 Java。目标不是当前 Java 时通过 `BackendSseForwarder` 流式转发 `text/event-stream`，并透传 `Authorization`、`X-Trace-Id`、`Last-Event-ID` 和 query。目标 Java 收到内部路由头后跳过二次路由。
- `LEGACY_FULL` 在目标 Java 合并 `run_events` polling replay、按 dispatch user 裁剪的兼容消息 snapshot 与本机 live bus；durable 事件每次按 `runId + lastSeq` 查询，默认批量上限 100；远端消息单页 100、最多 20 页，归属不明确时消息投影为空但 durable 回放继续。
- `REDIS_SUMMARY` 在目标 Java 首帧总发送完整 Hash/ZSET 物化 `run.snapshot.reset`，以 snapshot `runtimeVersion` 为起点，由最短 5 秒的 Redis 安全扫描和本机 live bus 唤醒、分页读取 Redis `runtime-events` 尾流。live 事件仍即时唤醒但帧本身不直接输出，所以慢订阅、并发追加和唤醒丢失都由 Redis 顺序补偿。活跃 SSE 连接不创建 500ms PostgreSQL polling，也不读取 `run_events`、`run_session_scopes` 或 routing/process 表。单个 Run 的跨 Java 实时消息链路不使用 Redis Pub/Sub，而是让 SSE 跟随 manifest 指定的生产 Java。
- **legacy durable 事件可能重复投递**：落库的 durable 事件既经 live bus 即时下发，又可能在下一轮 replay 轮询中被查出（live 推送与轮询游标推进存在竞态）。同一 durable 事件携带稳定的 `evt_` 前缀 `eventId`，前端必须按 `eventId` 去重；transient 事件 `eventId` 为 `evt_live_` 前缀且 `seq=0`，同样按 `eventId` 去重。
- `RunEventLiveBus` 基于 Reactor `Sinks`，只服务当前进程已连接的 SSE 订阅。live bus 是 best-effort 实时通道：客户端消费过慢、断开或并发发布产生背压时，后端可以丢弃当前 live 帧，但不得让全局 live bus 进入 error/complete；legacy durable 事件可由数据库 replay 恢复，legacy transient 内容依赖兼容消息 snapshot；新模式 live bus 丢帧不丢事实，durable/transient 事件由 `runtime-events` 有序尾流恢复，当前可见状态由物化 snapshot 恢复。
- legacy polling 查询必须 offload 阻塞式 Repository；单次回放查询失败不改变 Run 状态，后端跳过本轮轮询并在下一轮继续尝试。新模式 Redis replay 失败按统一运行态错误结束本次连接，不得静默切换数据库轮询。
- 客户端断开时释放 Flux 订阅。
- `Last-Event-ID` 解析委托 `RunEventReplayService`；非法值映射为 `VALIDATION_ERROR`。
- SSE body 使用 `RunEventSsePayload`，不返回 generated SDK DTO 或 opencode raw event。
- `LEGACY_FULL` 中 `message.updated`、`message.part.updated`、`message.part.delta`、`assistant.message.delta` 等消息内容投影事件只进入 live bus；`run.*`、`diff.*`、`permission.*`、`question.*`、`todo.updated` 和关键 tool 状态继续写 `run_events`。`REDIS_SUMMARY` 中 durable 原子写 durable seq Stream、全事件 runtimeVersion Stream 和物化 snapshot，transient 写 runtimeVersion Stream 和 snapshot；追加后的 live bus 帧只用于唤醒 SSE，任何事件都不写 `run_events`。
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
5. `LEGACY_FULL` RunEvent payload 继续以 PostgreSQL JSON 文本读取；`REDIS_SUMMARY` 使用 Redis JSON/Stream。manifest 缺失按 legacy 兼容，旧前端未知 `run.snapshot.reset` 时至少必须安全忽略；支持新模式的前端应按上文清空并重放 snapshot。
6. 新 Run 仅在请求携带已校验的 `contextToken + clientRequestId` 且命中 userId 稳定灰度桶时使用 `REDIS_SUMMARY`；开关默认关闭且 rollout 为 0。storageMode 在创建时固定，回滚比例只影响后续 Run。

## manager 控制面补充

manager WebSocket `command` 帧支持可选 `environment` 和 `configPath` 字段。Java 启动用户 opencode server 时通过 `environment` 注入 `TEST_AGENT_INTERNAL_PROXY_API_KEY`、`TEST_AGENT_INTERNAL_PROXY_BASE_URL` 和 `ENTERPRISE_UCID`，通过 `configPath` 固定传入当前用户的受管公共配置软链接；manager 生成的 `startCommand` 按固定顺序展示 `HOME`、全部 XDG、`TMPDIR`、配置路径、代理 base URL、UCID 等非敏感值，`TEST_AGENT_INTERNAL_PROXY_API_KEY` 必须显示为 `<redacted>`。调用方即使在 `environment` 中提供同名 HOME/XDG/TMP/config 变量，也会被 manager 按 `sessionPath/configPath` 覆盖。
