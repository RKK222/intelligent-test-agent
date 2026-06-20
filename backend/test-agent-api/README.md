# test-agent-api

## 工程定位

后端 HTTP/SSE/WebSocket API 定义模块，只做协议入口、请求响应 DTO、统一响应、错误、traceId、鉴权、限流和受控 WebSocket 适配。

## 主要职责

- 暴露旧 `/api/...` 兼容 URL。
- 暴露新增 `/api/internal/platform/...`、`/api/internal/agent/opencode/...` 和预留 `/api/public/...` URL。
- 暴露 Workspace 受控目录选择接口，Controller 只委托 workspace-management 目录服务。
- Controller 只调用业务模块 service，不直接访问 Repository、generated SDK 或 JDBC 实现。
- 维护 `RuntimeDtos` 等平台 DTO，不返回 generated SDK DTO。
- RunEvent SSE 建连时先委托 runtime 恢复 opencode projected messages，再进入 durable replay 与 live bus 合流。
- 本地 CORS 默认允许主前端和 `frontend-opencode` Vite/Preview/E2E 端口；生产必须通过 `TEST_AGENT_CORS_ALLOWED_ORIGINS` 显式收敛。

## 允许依赖

- `test-agent-common`。
- `test-agent-domain`。
- `test-agent-observability`。
- `test-agent-event`。
- `test-agent-workspace-management`。
- `test-agent-opencode-runtime`。
- Spring WebFlux、Validation、Security。

## 禁止依赖

- `test-agent-persistence`。
- `test-agent-opencode-sdk-generated`。
- Repository 实现类。
- 业务规则、文件系统操作、opencode 调用编排。

## 测试覆盖

- `RuntimeControllerTest` 覆盖 Workspace、目录选择、Session、Run、Diff、RunEvent SSE 恢复快照和内部平台兼容 URL。
- `OpencodeRuntimeControllerTest` 覆盖 opencode runtime 代理入口、MCP tools、permission reply、session share 和原始 opencode 路径兼容。
- `TerminalControllerTest`、`TerminalWebSocketHandlerTest` 覆盖 PTY ticket、内部平台 WebSocket URL、origin 拒绝、单会话互斥、输入限流、关闭和超时。
- `RuntimeApiSupportTest` 覆盖分页默认值和非法分页参数转换为统一 `VALIDATION_ERROR`。
- `RuntimeSecurityConfigTest` 覆盖本地 `frontend-opencode` real E2E Origin 白名单。
- `ApiTokenWebFilterTest`、`InMemoryRateLimitWebFilterTest`、`TraceIdWebFilterTest`、`GlobalExceptionHandlerTest` 覆盖鉴权、限流、traceId 和统一错误响应。

## 后续 AI 编码指引

新增 API 时先确认业务实现应落在哪个业务模块；本模块只新增 Controller/DTO/协议转换。旧 URL 不删除，新 URL 必须同步记录到 `docs/api/http-api.md`。
