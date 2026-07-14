# 删除主时间线用户与智能体头像设计

## 背景与目标

主对话时间线当前在用户气泡右侧展示用户头像，并在智能体首个过程行或回答行左侧展示机器人头像。根据浏览器批注，需要删除这两类头像，并回收头像原先占用的横向空间。

## 范围

- 修改 `frontend/packages/agent-chat` 的 opencode-like 主时间线渲染，不修改 `FigmaChatPanel.vue` 中已作废的旧消息循环。
- 用户消息保持右对齐，气泡、复制按钮和工作区上下文 chip 行为不变。
- 智能体过程、工作中、思考中和最终文本保持现有顺序、折叠状态与状态文案，只移除头像及其 40px continuation 占位。
- 不修改 API、RunEvent、DTO、数据库、鉴权、安全策略或后端代码。

## 实现设计

从 `UserMessageRow.vue` 删除用户头像节点及 `User` 图标导入。从 `AssistantMessageFrame.vue`、`WorkingStatusRow.vue` 和 `ThinkingRow.vue` 删除机器人头像节点及 `Bot` 图标导入。同步清理 `rows.css` 中仅服务头像的选择器、尺寸变量、gap 和 continuation 左侧 padding，使消息正文自然使用完整可用宽度。

不采用仅通过 CSS 隐藏的方案，因为该方案会保留无意义 DOM，并容易遗漏智能体 continuation 的空白占位。

## 测试与验收

- 先修改 `frontend/packages/agent-chat/tests/opencode-timeline.test.ts`，断言用户、初始思考、拆分智能体消息、step-start 和重复 reasoning 场景均不渲染头像；在实现前运行并确认测试失败。
- 完成最小实现后运行 agent-chat 定向 Vitest、前端 typecheck、lint、全量 Vitest 与生产 build。
- 浏览器验收确认用户气泡右侧、智能体过程/回答左侧均无头像，且原头像位置不留下空白。

## 文档与兼容性

同步更新 `frontend/packages/agent-chat/README.md`，将“助手侧保留头像”改为“用户与助手均不展示头像”。这是一项纯展示变更，不影响数据结构、事件兼容性、性能边界或安全边界。
