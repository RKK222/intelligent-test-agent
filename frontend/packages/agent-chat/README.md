# @test-agent/agent-chat

## 工程定位

Agent 对话和结构化卡片展示包。

## 主要职责

- 展示用户/助手消息。
- 展示 PlanCard、ToolCallCard、TestRunCard、DiffActionCard。
- 提供发送、取消、重试、打开 Diff 回调。
- 通过 assistant-ui 类型适配未来完整对话运行时。

## 禁止事项

- 不直接启动 Run。
- 不订阅 SSE。
- 不调用 opencode server。
