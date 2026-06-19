# Phase 08 Agent 对话、Diff 和运行 MVP

## 阶段目标

完成可演示的智能体运行闭环：用户发起任务，后端启动 Run，前端通过 RunEvent SSE 展示实时输出、工具调用、测试运行和 DiffActionCard。

## 可验收功能清单

1. Agent 对话区可发送任务消息。
2. 后端可创建 Session 和 Run。
3. 前端可实时展示 RunEvent SSE 输出。
4. 支持 PlanCard、ToolCallCard、TestRunCard、DiffActionCard。
5. Diff 可查看、接受和拒绝。
6. Run 可取消和重试。

## 修改项目

- `frontend/apps/agent-web`
- `frontend/packages/agent-chat`
- `frontend/packages/diff-viewer`
- `frontend/packages/test-runner`
- `frontend/packages/event-stream-client`
- `frontend/packages/backend-api`
- `backend/test-agent-app`
- `backend/test-agent-event`
- `backend/test-agent-opencode-client`
- `docs/api/*`
- `docs/frontend/*`

## 实现功能

- assistant-ui 承载消息列表、输入框和结构化卡片。
- RunEvent SSE 驱动消息增量、工具状态、测试状态和 Diff 提案。
- Diff 使用 Monaco Diff Editor，支持接受当前文件、拒绝当前文件、接受全部、拒绝全部。
- 取消 Run 后前后端状态一致。
- 重试 Run 时保留原 Session 上下文。

## 验收方式

- 前端测试覆盖消息发送、事件展示、卡片渲染、Diff 操作、取消和重试。
- 后端测试覆盖创建 Run、取消 Run、事件推送和错误映射。
- 浏览器端到端验证一次任务从发送到结果展示的闭环。
- API 和事件文档同步新增任务、取消、Diff 和卡片事件。
