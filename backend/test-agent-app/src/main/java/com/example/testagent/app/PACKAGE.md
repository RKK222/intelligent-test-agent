# 包说明：com.example.testagent.app

## 职责

唯一 Spring Boot 应用入口包，承载 `test-agent-app` 的启动类、入口配置和后续 Controller/application service 编排入口。

## 不负责

- 不直接调用 generated SDK。
- 不直接访问 Repository 实现。
- 不承载纯领域规则。
- 不存放测试 fixture。

## 主要程序清单

- `TestAgentApplication`：Spring Boot 启动类，扫描 `com.example.testagent` 下的后端组件。
- `web.TraceIdWebFilter`：入口请求 traceId 透传、生成和响应头写入。
- `web.GlobalExceptionHandler`：统一异常到平台错误响应的转换。
- `web.WorkspaceController`、`web.SessionController`、`web.RunController`：Runtime HTTP/SSE 协议转换入口。
- `web.ApiTokenWebFilter`、`web.InMemoryRateLimitWebFilter`：API token 鉴权占位和内存限流占位。
- `workspace.WorkspaceApplicationService`、`workspace.WorkspaceFileService`：工作区注册、文件路径归一化和 UTF-8 文件读写。
- `session.SessionApplicationService`：会话创建、查询和消息追加/分页。
- `run.RunApplicationService`：Run 启动、路由、opencode session 懒创建/复用、opencode start/cancel 和事件订阅编排。
- `config.TestAgentRuntimeProperties`、`config.ExecutionNodeSeeder`：运行时配置绑定和 opencode node seed。
- `config.OpencodeNodesHealthIndicator`、`config.RedisOptionalHealthIndicator`：本地集成健康检查。
- `support.RuntimeIdGenerator`：应用层业务 ID 生成入口。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- `test-agent-observability`。
- `test-agent-opencode-client`。
- `test-agent-persistence`。
- `test-agent-event`。
- Spring Boot WebFlux、Security、Actuator。

## 禁止依赖

- 直接依赖 `test-agent-opencode-sdk-generated`。
- Controller 直接依赖 Repository。
- Controller 直接调用 opencode SDK API 类。

## 上游调用方

- HTTP/SSE/WebSocket 客户端。
- 未来前端 `backend-api` package。
- 运维健康检查。

## 下游依赖

- opencode client facade。
- persistence 模块提供的持久化能力。
- event 模块提供的事件流能力。
- observability 模块提供的 trace 和指标能力。

## 测试位置

- 模块内 Controller/API 测试。
- 端到端测试。
- 需要公共 fixture 时使用 `test-agent-test-support`。

## 修改时必须同步更新

- `backend/test-agent-app/README.md`。
- `docs/api/backend-api.md`。
- `docs/api/event-stream-api.md`，如果涉及事件流。
- `docs/security/security-standards.md`，如果涉及鉴权、限流或安全响应头。
- `docs/architecture/dependency-rules.md`，如果依赖边界变化。
