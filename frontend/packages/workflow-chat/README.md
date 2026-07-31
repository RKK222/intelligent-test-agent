# workflow-chat

独立长程任务对话界面。页面按路由异步加载 TDesign Chat 样式，直接通过 `workflow-api-client` 访问 Python HTTP/AG-UI SSE，不复用 OpenCode 或 LobeHub 对话状态。

测试：`corepack pnpm vitest run packages/workflow-chat/tests`。
