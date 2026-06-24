# 包说明：com.icbc.testagent.api

## 职责

API 定义包，承载 HTTP/SSE/WebSocket 入口、请求响应 DTO、统一响应包装、错误处理、traceId、鉴权、限流和 CORS/WebSocket 配置。

## 不负责

- 不实现 Workspace、Session、Run、Terminal 等业务流程。
- 不直接访问持久化实现或 JDBC 实现。
- 不直接调用 generated SDK 或 opencode facade。

## 主要程序清单

- `web.platform.WorkspaceController`、`web.platform.SessionController`、`web.platform.RunController`、`web.platform.TerminalController`：平台协议入口，旧 `/api/...`、`/api/internal/platform/...` 和 Run 相关 `/api/internal/agent/{agentId}/...` URL 并行映射。
- `web.platform.PlatformOpencodeRuntimeController`：平台侧 opencode runtime 代理入口，只承载旧 `/api/...` 与 `/api/internal/platform/...` 路径，并把可选用户主体交给业务层决定用户进程或固定节点 fallback。
- `web.agent.AgentOpencodeRuntimeController`：agent 侧 opencode 兼容代理入口，承载 `/api/internal/agent/{agentId}/...` 路径并把 agentId 与可选用户主体交给业务层选择 runtime。
- `web.platform.RuntimeDtos`、`web.platform.AuthDtos`：平台 API 请求/响应 DTO。
- `web.common.TraceIdWebFilter`、`web.common.JwtAuthWebFilter`、`web.common.ApiTokenWebFilter`、`web.common.InMemoryRateLimitWebFilter`、`web.common.GlobalExceptionHandler`：入口公共处理。
- `web.common.RuntimeApiSupport`、`web.common.AuthWebSupport`：Controller 与 WebFilter 共用的 HTTP 边界工具。
- `web.platform.TerminalWebSocketHandler`：受控 PTY WebSocket upgrade 入口。
- `config.RuntimeSecurityConfig`、`config.TerminalWebSocketConfig`：API 层安全和 WebSocket mapping。
- 本地默认 CORS 覆盖主前端和 `frontend-opencode` 的 Vite dev/preview/real E2E 端口；生产必须由部署配置显式指定。

`RunController` 的 RunEvent SSE 入口只做协议合流：先通过当前 agent runtime 输出 projected messages snapshot，再输出 `test-agent-event` 提供的 durable replay/live bus SSE；不直接访问 Repository 或 generated SDK。

## 允许依赖

- `test-agent-common`、`test-agent-domain`、`test-agent-observability`、`test-agent-event`。
- `test-agent-workspace-management`、`test-agent-opencode-runtime` 的公开 service/DTO。
- Spring WebFlux、Validation、Security。

## 禁止依赖

- `test-agent-persistence`。
- `test-agent-opencode-sdk-generated`。
- 持久化实现类。
- `test-agent-app`。

## 测试位置

- `backend/test-agent-api/src/test/java/com/icbc/testagent/api/web/platform`。
- `backend/test-agent-api/src/test/java/com/icbc/testagent/api/web/agent`。
- `backend/test-agent-api/src/test/java/com/icbc/testagent/api/web/common`。
- `backend/test-agent-api/src/test/java/com/icbc/testagent/api/config`。
- 平台 Controller 测试必须覆盖公开 `/api/...`、内部 `/api/internal/platform/...` 和 Run 相关 `/api/internal/agent/{agentId}/...` 路径；agent Controller 测试必须覆盖需要兼容的 `/api/internal/agent/opencode/...` 路径、agentId 透传和可选用户主体透传。
- RunEvent SSE 测试必须覆盖 Last-Event-ID/query resume 和 opencode projected messages snapshot 合流。
- Terminal WebSocket 测试必须覆盖 ticket 消费、origin 校验、单会话互斥、限流和超时。
- 入口公共工具测试覆盖 traceId、分页参数、统一错误、鉴权和限流。

## 修改时必须同步更新

- `backend/test-agent-api/README.md`。
- `docs/api/http-api.md`。
- `docs/api/event-stream.md`，如果涉及 SSE。
- `docs/standards/security.md`，如果涉及鉴权、限流、CORS 或 WebSocket 安全。
