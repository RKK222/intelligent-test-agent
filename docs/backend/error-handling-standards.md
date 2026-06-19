# 错误处理规范

所有后端错误必须转换为平台统一错误格式。

## 基本原则

1. 不把任意 Java 异常直接返回给前端。
2. 不泄露堆栈、SQL、密钥、token、内部路径和第三方原始敏感错误。
3. 对外错误稳定，对内日志保留可排查信息。
4. generated SDK 异常必须在 `test-agent-opencode-client` 转换为平台异常。

## 错误响应字段

平台错误响应至少包含：

- `code`：稳定错误码。
- `message`：面向用户或调用方的错误说明。
- `traceId`：请求追踪 ID。
- `details`：可选，放安全的结构化详情。

当前实现类型为 `ApiErrorResponse`，成功响应对应 `ApiResponse<T>`。业务代码优先抛出 `PlatformException` 并携带 `ErrorCode`，入口层由 `GlobalExceptionHandler` 统一转换。

## 初版错误码

| code | HTTP 状态 | 默认说明 |
|---|---:|---|
| `VALIDATION_ERROR` | 400 | 请求参数无效 |
| `UNAUTHENTICATED` | 401 | 未认证 |
| `FORBIDDEN` | 403 | 无权限 |
| `NOT_FOUND` | 404 | 资源不存在 |
| `CONFLICT` | 409 | 状态冲突 |
| `RATE_LIMITED` | 429 | 请求过于频繁 |
| `INTERNAL_ERROR` | 500 | 服务器内部错误 |
| `OPENCODE_BAD_GATEWAY` | 502 | opencode 服务响应异常 |
| `OPENCODE_UNAVAILABLE` | 503 | opencode 服务不可用 |
| `OPENCODE_TIMEOUT` | 504 | opencode 服务超时 |

## HTTP 状态

- `400`：参数错误、请求格式错误。
- `401`：未认证。
- `403`：无权限。
- `404`：资源不存在。
- `409`：状态冲突、幂等冲突。
- `429`：限流。
- `500`：未预期内部错误。
- `502/503/504`：opencode server 或外部依赖异常。

## 文档要求

新增或修改错误码必须同步：

- `docs/api/backend-api.md`。
- 相关模块 README 或 PACKAGE.md。
- 对应测试。
