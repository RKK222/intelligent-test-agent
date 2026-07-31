# 包边界

- 只依赖 `workflow-api-client`，不得依赖 `backend-api` 或 `event-stream-client`。
- TDesign 样式由 `/workflow-chat` 路由组件动态导入，并由 `.workflow-chat-root` 设计变量约束。
- 本期只展示已注册的代码变动影响分析；场景 2～4 不提供执行入口。
