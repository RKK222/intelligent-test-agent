# 包边界

- 只访问同源 `/workflow-api/v1/**`。
- 不访问 Java 工作流接口，也不复用平台 RunEvent/EventSource 客户端。
- Bearer Token 只通过调用方提供的内存函数读取，不写日志、不持久化副本。
