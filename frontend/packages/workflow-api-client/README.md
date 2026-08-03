# workflow-api-client

浏览器直连同源 Python `workflow-service` 的类型化客户端。该包不依赖 `backend-api`，并用 fetch 流实现可携带 `Authorization` 与 `Last-Event-ID` 的 AG-UI SSE。JSON 接口会校验成功响应的 `{data: ...}` 信封；SPA HTML fallback、反向代理纯文本错误或畸形 JSON 只返回脱敏的路由/服务诊断，不把响应正文或原生解析异常显示给用户。网络中断、429 和 5xx 会按最后一个 durable id 重连；认证、权限、协议及消费端 reducer 错误立即终止，不做无效重试并显式取消响应流。重连等待会主动移除 AbortSignal listener，长期连接不会按重连次数累积监听器。

测试：`corepack pnpm vitest run packages/workflow-api-client/tests`。
