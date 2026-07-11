# 宠物旁路问答 RunEvent 流式交互设计

## 背景与目标

宠物旁路问答当前通过普通 HTTP 请求完成临时 fork、提问和清理。该方式能保证问题不写入主对话，但前端只能等待最终响应：等待期间没有真实进度，且工作台根节点点击会无条件关闭浮层，导致用户看不到最终结果。

本次目标是：

1. 旁路问答继续基于临时 OpenCode fork，不写入主会话消息历史；
2. 复用平台现有 RunEvent SSE，实时展示上下文准备、只读工具执行、答案增量和终态；
3. 提问后等待期间，点击工作台其他区域不关闭浮层；右上角关闭按钮仍可主动关闭；
4. 保留现有同步旁路接口作为兼容入口，新增流式启动入口供工作台使用；
5. 完成组件测试、API/事件测试、Playwright E2E 和真实三服务验收。

## 方案选择

不新增宠物专用 SSE 协议。流式启动接口只负责创建一次可审计的旁路 Run 并立即返回 `runId`，前端继续使用现有 `subscribeRunEvents(runId)` 订阅 `/api/internal/agent/opencode/runs/{runId}/events`。

为了让 POST 返回后才建立的 SSE 连接仍能回放早期事件，旁路任务使用正常的 `runs` / `run_events` 持久化。它关联一个从创建起即为 `ARCHIVED` 的内部平台 Session；该内部 Session 与主 Session 使用同一 Workspace，但不会进入会话历史和用户级运行态查询。内部 Session 标题固定为“宠物旁路问答（内部）”，创建者为当前用户；Run 的 `sourceType` 使用新增的 `SIDE_QUESTION`，`sourceRefId` 记录主平台 Session ID，便于审计、孤儿清理和后续统计区分。内部 Session 与 Run 保留现有平台审计周期，不做请求结束即删库。

不把旁路 Run 直接挂在主 Session 上，避免主聊天输入、active-run 恢复和历史运行徽标被旁路任务占用。

## 组件边界

| 单元 | 责任 |
| --- | --- |
| `SideQuestionStreamingApplicationService` | 创建归档内部 Session 和旁路 Run；在后台完成上下文预算判断、临时 fork、按需 compact、远端事件订阅、异步 prompt、最终答案读取、Run 终态和 fork 清理。 |
| `SideQuestionAnswerExtractor` | 集中提取最终自然语言答案并过滤伪工具协议，供同步兼容入口与流式入口共同复用。 |
| `RunEventType` / `RunEventPersistencePolicy` | 新增 `side_question.started/progress/delta` 稳定事件；仅 delta 为 transient，阶段事件可回放，终态继续使用既有 `run.succeeded/run.failed`。 |
| `SideQuestionOrphanCleanupTaskHandler` | 周期扫描超时的 `SIDE_QUESTION` active Run，按内部 Session 保存的临时远端映射清理 fork，并用统一终态事件收敛。 |
| 现有 `RunController` / `RunEventSseStreamService` | 原样提供 SSE 路径、持久化回放、本机 live bus 和跨后端路由，不新增第二套流服务。 |
| `backend-api` / `event-stream-client` | `startSideQuestionRun` 调用新增启动入口；现有 `subscribeRunEvents` 识别新增事件类型。 |
| `useSideQuestionRun.ts` / `AgentWorkbench.vue` | 聚焦 composable 管理宠物旁路 Run 的订阅、阶段提示、答案增量、完成/失败和主动关闭清理；Workbench 只提供当前 session/message/model 并把状态传给 Shell，不建立第二套聊天 runtime。 |
| `FigmaShell.vue` | 展示阶段提示和增量答案；只在等待状态阻止工作台外部点击关闭，显式关闭按钮保持有效。 |

## HTTP 与事件契约

### 启动接口

新增且保持 platform/agent 两套路由等价：

```text
POST /api/internal/platform/opencode-runtime/sessions/{sessionId}/side-question/runs
POST /api/internal/agent/{agentId}/session/{sessionId}/side-question/runs
```

流式入口使用独立请求 DTO：`question`、可选 `messageId`、`model`。不接收 OpenCode agent 字段；后端固定使用 `plan`，浏览器不能覆盖只读安全边界。响应为：

```json
{
  "runId": "run_..."
}
```

现有 `POST .../side-question` 同步接口继续返回 `{answer, compacted}`，不改变旧客户端行为。

### RunEvent

- 既有 `run.created` / `run.started` / `run.succeeded` / `run.failed` 是 Run 生命周期和状态的唯一事实源，顺序为：`run.created` → `run.started` → `side_question.started` → 专用阶段/增量 → `run.succeeded|run.failed`。任何 `side_question.*` 事件前 Run 必须已经持久化为 `RUNNING`。
- `side_question.started`：durable；后台任务已进入执行态，payload 含主 `sessionId`。
- `side_question.progress`：durable；payload 的 `stage` 取 `preparing_context`、`forking`、`compacting`、`reading`、`tool`、`composing`，可选 `toolName` 只传工具类型，不传命令、路径或工具参数。
- `side_question.delta`：transient；payload 只含经过角色过滤的助手自然语言 `delta`。
- `run.succeeded`：durable；旁路 Run 的 payload 增加 `sideQuestion:true`、最终 `answer` 与 `compacted`，作为断线恢复和最终显示的事实源；`answer` 最大 64 KiB，超出时按 UTF-8 安全截断并带 `truncated:true`。
- `run.failed`：durable；旁路 Run 的 payload 增加 `sideQuestion:true` 与统一安全错误 `message`，不透传异常堆栈或远端请求体。

前端必须以 `run.succeeded.payload.answer` 覆盖增量拼接结果，避免丢帧、重连或 OpenCode part 更新造成答案不完整。

## 后端数据流与清理

1. `UserOpencodeBackendRoutingWebFilter` 先根据当前用户 ACTIVE binding 判断目标服务器；非目标 Java 必须复用 `BackendJavaRouteResolver` 与 `BackendHttpForwarder` 转发启动 POST，并携带既有防循环 header。只有目标 Java 创建 Run 和持有异步任务，转发入口不得预创建 Run。
2. 目标 Java 解析当前用户、主 Session、Workspace、目标 AgentRuntime 和用户专属执行节点，创建 `ARCHIVED` 内部 Session、`SIDE_QUESTION` PENDING Run、指向该执行节点的 routing decision和 `run.created`，立即返回唯一 `runId`。SSE 继续按该 routing decision 由现有 `RunEventSseBackendRoutingWebFilter` 定位同一目标 Java。
3. bounded-elastic 后台任务开始时先以条件状态保存把 Run 从 `PENDING` 转为 `RUNNING`，依次追加 `run.started` 和 `side_question.started`；只有完成这三个动作后才能读取主远端 Session 消息规模、创建临时 fork和发送其他专用进度。超过既有预算时仅 compact 临时 fork。
4. OpenCode 1.17.8 的 `Session.fork` 实现创建独立 session，不设置 `parentID`，因此不会被主 Run 当作 child 自动纳入 scope。旁路订阅仍必须从 event payload 递归提取实际 `sessionID/sessionId`，只接受精确匹配临时 fork 的事件；不接收无 session 的全局事件。主 Run 与旁路 Run 并发时分别只消费自己的远端 session。
5. 先订阅临时 fork 的 OpenCode 事件，再调用现有 `prompt_async`。旁路安全 system prompt 通过现有 runtime 启动命令的可选 system 字段传递；该字段属于集中安全策略，不复制到 Controller 或前端。
6. OpenCode 普通 message/tool 事件由旁路投影转换成专用 RunEvent。只有已识别为 assistant text 的增量进入 `side_question.delta`；工具参数、用户问题回显、reasoning 和伪工具协议不能进入答案。
7. 收到远端成功终态后，读取临时 fork 最终消息并用共享提取器得到最终答案，把 Run 标记为 `SUCCEEDED` 并追加唯一终态 `run.succeeded`；最终答案直接位于该事件 payload，不存在第二次终态写入窗口。
8. 任一准备、prompt 或事件流错误把 Run 标记为 `FAILED` 并追加唯一终态 `run.failed`；安全错误直接位于该事件 payload。
9. 成功、失败、prompt 启动失败和订阅异常都进入同一幂等清理路径删除临时 fork；前端断开 SSE 不取消后台任务，保证正常进程内清理仍执行。fork 创建成功后立即把临时远端 session ID 和执行节点写入归档内部 Session 映射，后续步骤不得早于该持久化。
10. 新增周期孤儿清理任务：每 5 分钟查询更新时间超过 10 分钟且仍 active 的 `SIDE_QUESTION` Run；按其内部 Session 映射在原执行节点删除临时 fork，再以唯一 `run.failed` 收敛。多实例通过现有 scheduler 分布式锁单实例执行；删除和终态写入必须幂等。进程恰好在远端 fork 已创建但映射尚未持久化的极窄窗口无法跨系统原子恢复，日志必须记录该窗口，规范不宣称分布式强一致。

## 前端交互

- 用户提交后，浮层保持打开，输入按钮显示等待态。
- 在收到首个答案增量前，按真实事件显示最近阶段，例如“正在读取当前上下文”“正在执行只读检查”“正在整理答案”。不使用基于定时器伪造的随机进度。
- 收到 `side_question.delta` 后逐步显示答案；收到带 `sideQuestion:true` 的 `run.succeeded` 后以最终 `answer` 替换增量内容并结束加载态；`run.failed` 则结束加载并显示安全错误。
- 等待期间点击 `.figma-app` 其他区域只关闭头部菜单，不关闭宠物浮层；显式 ×、收起宠物或组件卸载会关闭前端订阅并清理本地显示状态。
- 关闭前端订阅不调用主会话 abort，也不修改主对话历史。
- 失败时保留浮层并显示统一错误，允许用户修改问题后重试。
- 同一浮层只允许一个旁路 Run；按钮在运行中禁用，双击不会创建第二个 Run。完成、失败或显式关闭后才能再次提交。

## 配置与安全边界

- `plan` agent、只读工具边界和禁止修改状态属于安全不变量，流式 DTO 不提供 agent 字段，继续由后端集中注入。
- 模型、消息边界和 compact 预算沿用现有请求和常量，不新增 provider/model 分支。
- 事件 payload 不记录问题正文、工具命令、工具参数、工作区绝对路径或密钥。
- 前端仍只访问 backend-api 和 RunEvent SSE，不直连 OpenCode。

## 兼容性与失败恢复

- 新事件类型对旧前端是可忽略的字符串；现有 RunEvent envelope 不变。
- 新启动接口为增量能力，旧同步接口继续工作。
- `SIDE_QUESTION` 是现有 varchar 来源字段的新枚举值，不改变列结构；新增 Flyway migration 只更新数据库字段注释，并同步数据库文档说明该兼容值。
- durable `run.*` 和 started/progress 允许 SSE 建连稍晚或短暂重连后恢复；delta 不回放，最终 `run.succeeded.payload.answer` 保证答案完整。
- 页面关闭或显式关闭浮层后，后台任务仍收敛 Run 并清理远端 fork。

## 测试与验收

1. 后端单测覆盖：立即返回 runId、归档内部 Session、不占主 active Run、事件顺序、assistant delta 过滤、工具参数不泄漏、compact 分支、成功/失败清理和最终答案覆盖。
2. API 与 event-stream-client 测试覆盖新增启动路由、DTO 和事件类型监听。
3. FigmaShell/AgentWorkbench 测试覆盖等待时外部点击保持、显式关闭、真实阶段提示、增量答案、`run.succeeded.payload.answer` 覆盖和 `run.failed` 展示。
4. 增加主 Run 与旁路 Run 并发测试：两个远端 session 的事件交错到达，主 Run 不接收旁路消息/工具/scope，旁路 Run 也不接收主 Run 事件。
5. Playwright mocked E2E 从唤起宠物、提交问题到接收 progress/delta/run.succeeded，期间点击工作台其他区域并断言浮层仍存在；模拟 SSE 重连携带 `Last-Event-ID`，最终答案只显示一次。
6. 真实三服务 E2E 创建主 Session并记录主消息数，同时启动一个仍在回答的主 Run和旁路 Run；用最长 120 秒事件驱动等待（不使用固定 sleep），验证 durable 事件严格按 `run.created → run.started → side_question.started → ... → run.succeeded`，存在真实 `side_question.progress`、终态 `answer` 非空、两个 Run 的事件 session 归属隔离、主 Session 消息数不因旁路增加。测试通过用户进程状态取得目标 OpenCode 地址，在测试进程内比较远端 session 列表，并以新增 fork ID 最终查询为 404/不存在作为清理证据；该直连仅用于 E2E 验证，不进入浏览器生产代码。
7. 孤儿清理测试持久化一个超时 active `SIDE_QUESTION` Run 和临时 session 映射，触发 scheduler handler 后断言远端 delete、唯一 `run.failed` 和幂等重跑。
8. 重启本地服务后，在真实 UI 再执行一次宠物提问，使用 DOM 条件等待阶段提示和最终答案，确认等待时外部点击保持、显式关闭有效，且答案不含伪工具协议。
