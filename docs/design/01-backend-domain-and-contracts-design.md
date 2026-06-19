# Phase 01 后端领域模型和契约详细设计

## 目标

建立后端核心领域模型、统一响应/错误模型、traceId 入口规则、Run 状态机和 RunEvent SSE 契约草案，为后续持久化、执行调度和前端联调提供稳定边界。

## 模块设计

- `test-agent-common` 提供跨模块基础契约：`ApiResponse<T>`、`ApiErrorResponse`、`ErrorCode`、`PlatformException`、`PageRequest`、`PageResponse<T>`。
- `test-agent-domain` 提供纯领域模型：Workspace、Session、Run、RunEvent、ExecutionNode、RoutingDecision 及对应 ID 值对象，不依赖 Spring Web、Persistence 或 generated SDK。
- `test-agent-observability` 提供 `TraceConstants` 和 `TraceIdSupport`，固定 `X-Trace-Id` 头、请求 attribute 和 Reactor context key。
- `test-agent-app` 只提供入口适配：`TraceIdWebFilter` 和 `GlobalExceptionHandler`，真实 Workspace/Session/Run Controller 留到 Phase 04。

## 状态机

Run 状态固定为 `PENDING`、`RUNNING`、`CANCELLING`、`SUCCEEDED`、`FAILED`、`CANCELLED`。

- `PENDING -> RUNNING|CANCELLED|FAILED`
- `RUNNING -> CANCELLING|SUCCEEDED|FAILED`
- `CANCELLING -> CANCELLED|FAILED`
- `SUCCEEDED|FAILED|CANCELLED` 为终态，不能继续流转。

`PENDING` 收到取消请求时直接进入 `CANCELLED`，因为任务尚未占用执行节点，不需要中间确认态。

## API 与事件契约

所有 HTTP 成功响应统一为：

```json
{"success":true,"data":{},"traceId":"trace_xxx"}
```

所有 HTTP 错误响应统一为：

```json
{"success":false,"code":"VALIDATION_ERROR","message":"请求参数无效","traceId":"trace_xxx","details":{}}
```

RunEvent 基础字段为 `eventId`、`runId`、`seq`、`type`、`traceId`、`occurredAt`、`payload`。SSE `id` 使用同一 run 内单调递增的 `seq`，`Last-Event-ID` 续传返回 `seq > Last-Event-ID` 的事件。

## 验收

- `mvn test` 覆盖 common、domain、observability、app 的契约行为。
- `mvn clean package -DskipTests` 验证全量编译。
- `mvn -pl test-agent-domain dependency:tree -Dscope=compile` 确认 domain 不引入 Web、Persistence 或 generated SDK。
