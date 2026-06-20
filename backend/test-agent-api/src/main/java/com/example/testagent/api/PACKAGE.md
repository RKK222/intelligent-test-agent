# 包说明：com.example.testagent.api

## 职责

API 定义包，承载 HTTP/SSE/WebSocket 入口、请求响应 DTO、统一响应包装、错误处理、traceId、鉴权、限流和 CORS/WebSocket 配置。

## 不负责

- 不实现 Workspace、Session、Run、Terminal 等业务流程。
- 不直接访问持久化实现或 JDBC 实现。
- 不直接调用 generated SDK 或 opencode facade。

## 主要程序清单

- `web.WorkspaceController`、`web.SessionController`、`web.RunController`、`web.OpencodeRuntimeController`、`web.TerminalController`：协议入口，旧 URL 与新 URL 并行映射。
- `web.RuntimeDtos`：平台 API 请求/响应 DTO。
- `web.TraceIdWebFilter`、`web.ApiTokenWebFilter`、`web.InMemoryRateLimitWebFilter`、`web.GlobalExceptionHandler`：入口公共处理。
- `web.TerminalWebSocketHandler`：受控 PTY WebSocket upgrade 入口。
- `config.RuntimeSecurityConfig`、`config.TerminalWebSocketConfig`：API 层安全和 WebSocket mapping。

`RunController` 的 RunEvent SSE 入口只做协议合流：先输出 opencode projected messages snapshot，再输出 `test-agent-event` 提供的 durable replay/live bus SSE；不直接访问 Repository 或 generated SDK。

## 允许依赖

- `test-agent-common`、`test-agent-domain`、`test-agent-observability`、`test-agent-event`。
- `test-agent-workspace-management`、`test-agent-opencode-runtime` 的公开 service/DTO。
- Spring WebFlux、Validation、Security。

## 禁止依赖

- `test-agent-persistence`。
- `test-agent-opencode-sdk-generated`。
- 持久化实现类。
- `test-agent-app`。

## 修改时必须同步更新

- `backend/test-agent-api/README.md`。
- `docs/api/http-api.md`。
- `docs/api/event-stream.md`，如果涉及 SSE。
- `docs/standards/security.md`，如果涉及鉴权、限流、CORS 或 WebSocket 安全。
