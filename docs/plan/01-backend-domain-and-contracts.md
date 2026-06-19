# Phase 01 后端领域模型和契约

## 阶段目标

建立后端核心领域模型、统一响应、错误格式、traceId、Run 状态机和 API/事件契约草案，为后续持久化、执行和前端联调提供稳定边界。

详细设计见 `docs/design/01-backend-domain-and-contracts-design.md`。

## 可验收功能清单

1. 定义 Workspace、Session、Run、RunEvent、ExecutionNode、RoutingDecision 领域模型。
2. 定义 Run 状态机，包括 pending、running、cancelling、succeeded、failed、cancelled 等状态。
3. 定义统一成功响应和统一错误响应。
4. 定义 traceId 生成、透传和响应规则。
5. 补齐 HTTP API 与 RunEvent SSE 的初版文档。

## 修改项目

- `backend/test-agent-domain`
- `backend/test-agent-common`
- `backend/test-agent-observability`
- `backend/test-agent-app`
- `docs/api/backend-api.md`
- `docs/api/event-stream-api.md`
- `docs/backend/error-handling-standards.md`
- `docs/architecture/dependency-rules.md`

## 实现功能

- 领域对象只表达业务概念，不依赖 Spring Web、数据库或 generated SDK。
- 公共模块提供错误码、响应模型、分页模型和基础异常。
- 观测模块提供 traceId 常量、上下文工具和日志字段约定。
- App 模块预留全局异常处理和请求 traceId 过滤器。

## 状态机规则

- `pending -> running|cancelled|failed`。
- `running -> cancelling|succeeded|failed`。
- `cancelling -> cancelled|failed`。
- `succeeded`、`failed`、`cancelled` 为终态。
- `pending` 收到取消请求时直接进入 `cancelled`。

## 验收方式

- 后端编译通过。
- 领域模块无 Web、Persistence、generated SDK 依赖。
- API 文档中存在统一响应和错误格式。
- 事件文档中存在 RunEvent 基础字段和 `Last-Event-ID` 规则。
- 单元测试覆盖 Run 状态合法迁移和错误格式构造。
