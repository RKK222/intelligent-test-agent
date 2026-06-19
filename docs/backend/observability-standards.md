# 日志与可观测性规范

本规范适用于后端日志、trace 和指标。

## TraceId

1. 所有入口请求必须携带或生成 traceId。
2. traceId 必须贯穿 Controller、application service、opencode client、persistence 和 event。
3. SSE、异步任务、重试和回放流程必须保留 traceId 或生成关联 ID。
4. 错误响应必须包含 traceId。

当前 HTTP 入口使用请求/响应头 `X-Trace-Id`。合法 traceId 以 `trace_` 开头，只包含字母、数字、下划线和短横线；缺失或非法值由 `TraceIdSupport` 生成新值。`TraceIdWebFilter` 将 traceId 写入 WebExchange attribute、响应头和 Reactor context。

## 日志

1. 使用正式日志框架，禁止 `System.out.println`。
2. 日志必须结构化，至少包含 traceId、模块、关键业务 ID 和结果。
3. 不记录密钥、token、认证头、个人敏感信息和大段用户输入。
4. 热路径避免高频 info 日志，调试细节使用 debug。

## 指标

1. 关键 API、opencode 调用、SSE 连接、事件处理、数据库访问应有 Micrometer 指标。
2. 指标标签必须低基数。
3. 失败计数、耗时分布、队列长度、活跃 SSE 数量应可观测。

## 告警基础

后续接入监控时优先关注：

- opencode server 调用失败率。
- SSE 连接异常断开率。
- Run 失败率和超时率。
- 数据库 migration 和 Repository 异常。
- 限流命中率。
