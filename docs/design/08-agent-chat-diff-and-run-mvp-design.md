# Phase 08 Agent 对话、Diff 和运行 MVP 详细设计

## 目标

Phase 08 完成可演示的智能体运行闭环：用户在右侧 Agent 面板发送任务，前端通过平台 API 创建或复用 Session 并启动 Run，后端调用 opencode server，前端通过 RunEvent SSE 展示实时输出、工具调用、测试卡片和 Diff 卡片，并支持 Run 级接受/拒绝 Diff、取消和重试。

## 后端契约

- 复用已有 `POST /api/sessions`、`POST /api/runs`、`GET /api/runs/{runId}`、`POST /api/runs/{runId}/cancel` 和 `GET /api/runs/{runId}/events`。
- 新增 `GET /api/runs/{runId}/diff` 返回当前 Run 可见 Diff 文件列表；优先读取已落库的 `diff.proposed` 事件 payload，必要时通过 opencode facade 查询远端 session diff 或 workspace VCS diff。
- 新增 `POST /api/runs/{runId}/diff/accept`，语义为保留当前工作区变更并追加 `diff.accepted` 事件。
- 新增 `POST /api/runs/{runId}/diff/reject`，语义为使用 opencode `sessionRevert` 回滚本次 Run 对应 opencode message 的变更并追加 `diff.rejected` 事件；缺少 opencode `messageID` 时返回 `CONFLICT`。
- `test-agent-opencode-client` 新增 diff/revert facade command/result，继续隔离 generated SDK DTO；`test-agent-app` 不直接依赖 generated SDK。
- Run 事件消费补齐终态：`session.next.step.ended` 或等价完成事件映射为 `run.succeeded`，`session.error`、`session.next.step.failed` 映射为 `run.failed`；Run 已取消或已终态时不覆盖状态。

## 前端交互

- `packages/agent-chat` 承载消息列表、输入框、PlanCard、ToolCallCard、TestRunCard 和 DiffActionCard。
- SSE 事件按 `seq` 去重并批量更新 UI；未知事件保留安全兜底，不中断连接。
- `assistant.message.delta` 追加到当前 Agent 消息；`tool.started`/`tool.finished` 更新工具卡；`run.*` 更新 TestRunCard；`diff.proposed` 更新 DiffActionCard 和 Changed Files。
- `packages/diff-viewer` 使用 Monaco Diff 展示当前文件 Diff；“接受/拒绝当前文件”只改变当前选择和反馈，真正后端操作为 Run 级接受/拒绝。
- `packages/test-runner` 展示 Run 状态、取消和重试。重试沿用原 Session 重新调用 `POST /api/runs`，保留上下文。

## 错误、兼容和安全

- 所有新增 API 继续使用 `ApiResponse` 和 `ApiErrorResponse`，错误中必须包含 traceId。
- 前端不得展示 opencode raw sensitive payload；只展示平台事件 payload 中安全字段。
- Diff payload 新增字段保持可选，前端忽略未知字段。
- 不新增数据库 migration；Diff 接受/拒绝状态通过 append-only RunEvent 记录。

## 验收

- 前端可从 Agent 输入启动真实 Run，并通过 SSE 展示事件流。
- Run 成功、失败、取消和重试状态在前后端一致。
- Diff 面板可展示文件列表和 patch，接受/拒绝 Run 后追加对应事件并刷新状态。
- 后端测试覆盖 diff facade、Diff API、accept/reject、缺少 messageID、Run 终态更新和 opencode 错误映射。
- 前端测试覆盖消息发送、SSE 事件渲染、卡片展示、Diff 操作、取消和重试。
