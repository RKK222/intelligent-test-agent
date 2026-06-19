# @test-agent/agent-chat

## 工程定位

Agent 对话和结构化卡片展示包。

## 主要职责

- 展示用户/助手消息。
- 展示 message part timeline（text、reasoning、tool、file、event）。
- 展示 PlanCard、ToolCallCard、TestRunCard、DiffActionCard。
- 提供 Phase 11 Agent/Provider/Model/Mode selector、runtime status bar、slash command palette、`@` context picker、permission dock、question dock 和 Todo 展示。
- Prompt composer 支持文本、文件附件、图片附件和附件 chips；文件读取后只向 app 层返回平台 `PromptPart`，不直接提交后端。
- History tab 支持受控搜索、选择会话、置顶/取消置顶和删除回调；实际 API 调用由 app 层完成。
- 提供纯 RunEvent reducer，把 `message.part.delta`、permission/question、todo、diff 等事件归并为对话展示状态。
- 提供发送、取消、重试、打开 Diff 回调。
- 通过 assistant-ui 类型适配未来完整对话运行时。

## 禁止事项

- 不直接启动 Run。
- 不订阅 SSE。
- 不调用 opencode server。
