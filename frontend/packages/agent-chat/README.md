# @test-agent/agent-chat

## 工程定位

Agent 对话和结构化卡片展示包。

## 主要职责

- 展示用户/助手消息。
- 展示 message part timeline（text、reasoning、tool、file、event）。
- `reasoning` 与最终 `text` 回答必须分块展示，避免把思考过程和答复混在同一个气泡里。
- 展示 PlanCard、ToolCallCard、TestRunCard、DiffActionCard。
- 提供 Phase 11 Agent/Provider/Model/Mode selector、runtime status bar、slash command palette、`@` context picker、permission dock、question dock 和 Todo 展示。
- Prompt composer 支持文本、文件附件、图片附件和附件 chips；文件读取后只向 app 层返回平台 `PromptPart`，不直接提交后端。
- History tab 支持受控搜索、选择会话、置顶/取消置顶和删除回调；实际 API 调用由 app 层完成。
- 提供纯 RunEvent reducer，把 `message.part.delta`、permission/question、todo、diff 等事件归并为对话展示状态。
- 提供发送、取消、重试、打开 Diff 回调。
- 通过 assistant-ui 类型适配未来完整对话运行时。
- `assistant.message.delta` 旧事件继续作为兼容输入；新 `message.part.delta` 优先按 messageId/partId 合并，避免流式输出重复。
- Agent/Provider/Model/Mode selector、slash command、`@` context、permission/question dock、Todo 和 runtime status 只暴露受控回调，HTTP 提交与 SSE 订阅仍由 app 层负责。
- Timeline、dock、附件 chips 和 tool cards 必须使用固定区域和换行策略，Agent 对话线程必须有独立滚动区域，避免长命令、长路径、图片名或 streaming 文本撑开工作台。

## 禁止事项

- 不直接启动 Run。
- 不订阅 SSE。
- 不调用 opencode server。
- 不创建 terminal ticket，不调用 backend-api。
