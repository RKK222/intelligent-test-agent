# 独立工作流 AG-UI SSE

## 与平台 RunEvent 的边界

`GET /workflow-api/v1/conversations/{conversationId}/events` 由 Python 原生输出 AG-UI，Nginx 直达 Python。它不使用 `RunEvent`、`event-stream-client`、Java SSE 转发或 OpenCode/LobeHub会话状态。

前端必须使用 fetch 流客户端，以便设置：

- `Authorization: Bearer <平台Token>`
- `Accept: text/event-stream`
- 重连时的 `Last-Event-ID: <durable sequence>`

原生 `EventSource` 不能设置 Authorization，禁止使用。

## 认证与重连

- 建连时执行与普通 HTTP 相同的 Redis 精确 Token 校验。
- 长连接每 30 秒重新读取 Token；Token 删除、过期、用户登出或角色调整导致 Token 撤销时立即结束流。
- 每次连接先发送无 SSE `id` 的 `STATE_SNAPSHOT` 和 `MESSAGES_SNAPSHOT`，随后补发 sequence 大于 `Last-Event-ID` 的 durable 事件。
- durable sequence 在每个 conversation 内从 1 单调递增；客户端只用 SSE `id` 推进游标，不能从 payload 猜测。
- 代理必须关闭缓冲；服务发送 keepalive，客户端断线后使用最后 durable id 重连。

帧示例：

```text
id: 17
event: RUN_FINISHED
data: {"type":"RUN_FINISHED","runId":"run_...","taskId":"task_...","status":"SUCCEEDED"}
```

## 快照

`STATE_SNAPSHOT.snapshot` 可包含：

```text
conversationId, title, runId, taskId, runStatus, workspaceStatus,
requiredInput, scopeInput, baselineInput, currentInput, tools, reportPublished
```

`MESSAGES_SNAPSHOT.messages[]` 包含 `id/role/content/createdAt`。前端收到快照后替换对应投影，再消费补发事件；快照没有 durable id，不改变游标。

## Durable 事件

| 类型 | 关键字段 | 语义 |
|---|---|---|
| `RUN_STARTED` | `runId/taskId/workflowId/status/runKind?` | run 已排队或被 Worker 认领。 |
| `RUN_FINISHED` | `runId/taskId/status` | 成功、部分成功或取消终态。 |
| `RUN_ERROR` | `runId/taskId/code/message` | 安全清洗后的失败；不含下游异常正文。 |
| `TEXT_MESSAGE_START` | `messageId/role` | assistant 消息开始。 |
| `TEXT_MESSAGE_CONTENT` | `messageId/delta` | 消息正文；当前可持久化完整 delta。 |
| `TEXT_MESSAGE_END` | `messageId` | 消息完成。 |
| `TOOL_CALL_START` | `toolCallId/toolCallName/runId/taskId` | 固定图节点开始。 |
| `TOOL_CALL_END` | `toolCallId/status/runId/taskId` | 节点成功或失败。 |
| `CUSTOM` | `name/value` | 工作流扩展事件，见下表。 |

持久事件支持 `dedupKey`，LangGraph 重试或 Worker 接管不会重复发布不可幂等生命周期。

## Custom 事件

| `name` | `value` |
|---|---|
| `workflow.input_required` | `workflowId/requiredInput/inputSchema/uiSchema`，或等待 run 的 `runId/taskId/kind/baselines/scope/currentInput`。 |
| `workflow.worker_claimed` | `runId/taskId`。 |
| `workflow.workspace_state` | `taskId/runId/status/expiresAt`。 |
| `workflow.report_published` | `taskId/runId/reportVersionId/version/operationKey`。 |

`kind` 当前为 `BASELINE_SELECTION` 或 `SCOPE_DISAMBIGUATION`。输入完整后通过消息 API 恢复原 run；不得在前端创建伪 run。

## 前端约束

- `workflow-api-client` 负责 fetch SSE、Authorization、Last-Event-ID 和重连。
- `workflow-chat` reducer 必须对重复 `toolCallId` 幂等，快照替换消息和状态，未知事件安全忽略。
- TDesign Chat 及样式只在 `/workflow-chat` 路由懒加载，不影响现有 Element Plus、OpenCode 或 LobeHub 页面。
- Markdown 报告渲染必须先消毒；Token、grant、SSH 内容和原始工具日志不得展示。
