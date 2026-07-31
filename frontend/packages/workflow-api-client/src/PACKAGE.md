# 包边界

- 只访问同源 `/workflow-api/v1/**`。
- 不访问 Java 工作流接口，也不复用平台 RunEvent/EventSource 客户端。
- Bearer Token 只通过调用方提供的内存函数读取，不写日志、不持久化副本。
- fetch SSE 只为网络错误、429 和 5xx 保留 durable 游标并重连；认证、权限、不可解析协议和消费端 reducer 错误直接上抛。
