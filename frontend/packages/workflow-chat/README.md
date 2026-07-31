# workflow-chat

独立长程任务对话界面。页面按路由异步加载 TDesign Chat 样式，直接通过 `workflow-api-client` 访问 Python HTTP/AG-UI SSE，不复用 OpenCode 或 LobeHub 对话状态。

切换会话或 `SUPER_ADMIN` 查询的 owner 前必须先关闭旧 SSE，避免迟到事件跨 owner 污染当前视图。

测试：`corepack pnpm vitest run packages/workflow-chat/tests`。
