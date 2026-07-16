# 删除对话合成工作状态行设计

## 目标

右侧对话时间线不再展示前端兜底生成的“思考中”和“正在工作 / 等待后续输出”状态行，只在收到真实对话内容后展示对应的 reasoning、工具或正文。

## 范围

- 停止在 `agent-chat` 时间线投影中生成 `thinking` 与 `working-status` 行。
- 删除这两种时间线行的类型、渲染分支、专用组件和专用样式。
- 保留真实 reasoning 对应的“思考状态”行。
- 保留探索、更新待办、其他工具、流式正文、重试、错误和 Diff 摘要。
- 保留流式正文共用的加载圆点样式，避免影响真实文本生成提示。

不修改 RunEvent、HTTP API、后端、数据库或对话 reducer 的事件归并逻辑。

## 行为

1. 用户消息已经进入时间线、但尚未收到任何 assistant part 时，时间线只显示用户消息，不追加“思考中”。
2. 当前轮次已经收到工具或 reasoning、但尚未收到正文时，只展示已有的真实过程内容，不追加“正在工作 / 等待后续输出”。
3. 收到 `reasoning` part 时，继续展示可展开的“思考状态”。
4. 收到运行重试、失败或 Diff 状态时，继续展示既有对应行。
5. 没有用户消息但运行状态已经开始时，不再生成孤立的“思考中”行。

## 实现边界

- 在 `opencode-like/state/projection.ts` 移除两个合成行的投影分支。
- 在 `opencode-like/state/types.ts` 移除对应联合类型成员。
- 在 `TimelineRow.vue` 移除对应组件导入和渲染分支，并删除两个不再使用的行组件。
- 在 `opencode-like/styles/rows.css` 只删除两个行组件专用样式；其他组件仍使用的 `.oc-thinking-dot` 动画样式不删除。
- 不调整 reducer、工具注册表、消息 part 数据结构或后端事件契约。

## 测试

- 投影测试覆盖：空 assistant 输出的运行中轮次不生成 `thinking`。
- 投影测试覆盖：已有过程 part、没有正文时不生成 `working-status`。
- 组件测试覆盖：时间线不出现“思考中”“正在工作”“等待后续输出”。
- 既有 reasoning、工具、重试、失败与流式正文测试继续通过。

## 文档与兼容性

- 更新 `frontend/packages/agent-chat/README.md` 和 `frontend/apps/agent-web/README.md`，删除合成工作状态行的行为说明。
- 这是纯前端展示收敛，不改变 API、事件、数据库、安全和权限行为。
- 历史消息与实时消息使用相同投影逻辑，因此均不再显示这两种合成行；真实 part 和运行终态保持兼容。
