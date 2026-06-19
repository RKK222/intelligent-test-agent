# test-agent-common

## 工程定位

公共基础模块，放置所有后端模块都可以复用的轻量类型和工具。

## 技术栈

- Java 21
- Jackson annotations
- Jakarta Validation API
- Maven library jar

## 主要职责

- 通用异常、错误码、响应模型。
- TraceId、Idempotency-Key、分页、时间、校验相关基础类型。
- 不含业务流程和基础设施访问代码。

## 已有契约

- `ApiResponse<T>`：统一成功响应，字段为 `success`、`data`、`traceId`。
- `ApiErrorResponse`：统一错误响应，字段为 `success=false`、`code`、`message`、`traceId`、`details`。
- `ErrorCode`：平台稳定错误码及 HTTP 状态映射数字。
- `PlatformException`：业务层抛出的平台基础异常。
- `PageRequest`、`PageResponse<T>`：分页请求和响应模型。

## 允许依赖

- JDK 标准库。
- Jackson annotations。
- Jakarta Validation API。

## 禁止依赖

- `test-agent-domain` 及任何业务模块。
- `test-agent-opencode-sdk-generated`。
- Spring Web、Persistence、App 启动入口。

## 后续 AI 编码指引

如果要新增跨模块共享的简单 DTO、异常、工具类，优先放这里；如果类名包含 Workspace、Session、Run 等业务语义，应先考虑 `test-agent-domain`。
