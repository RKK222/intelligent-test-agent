# test-agent-api

## 工程定位

后端 HTTP/SSE/WebSocket API 定义模块，只做协议入口、请求响应 DTO、统一响应、错误、traceId、鉴权、限流和受控 WebSocket 适配。

## 主要职责

- 暴露旧 `/api/...` 兼容 URL。
- 暴露新增 `/api/internal/platform/...`、`/api/internal/agent/{agentId}/...` 和预留 `/api/public/...` URL。
- `web.platform` 承载平台自身接口和旧兼容入口，`web.agent` 承载 agent runtime 兼容代理入口，`web.common` 承载 traceId、鉴权、限流和统一异常等入口支撑。
- 暴露 Workspace 受控目录选择接口，Controller 只委托 workspace-management 目录服务。
- 暴露应用版本工作区和个人工作区运行接口，Controller 只委托 workspace-management 业务服务；应用成员权限由业务服务校验。
- 暴露配置管理接口，Controller 只委托 configuration-management 业务服务；应用与工作区接口统一校验 `APP_ADMIN`，`SUPER_ADMIN` 继承该能力。
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
- `test-agent-system-management`。
- `test-agent-configuration-management`。
- Spring WebFlux、Validation、Security。

## 禁止依赖

- `test-agent-persistence`。
- `test-agent-opencode-sdk-generated`。
- Repository 实现类。
- 业务规则、文件系统操作、opencode 调用编排。

## 测试覆盖

- `RuntimeControllerTest` 覆盖 Workspace、目录选择、Session、Run、Diff、agent-scoped Run URL、RunEvent SSE 恢复快照和内部平台兼容 URL。
- `PlatformOpencodeRuntimeControllerTest` 覆盖旧 `/api/...` 与 `/api/internal/platform/...` 的 opencode runtime 代理入口、MCP tools、permission reply 和 session share。
- `AgentOpencodeRuntimeControllerTest` 覆盖 `/api/internal/agent/{agentId}/...` 原始 opencode 路径兼容、agentId 透传和 traceId。
- `TerminalControllerTest`、`TerminalWebSocketHandlerTest` 覆盖 PTY ticket、内部平台 WebSocket URL、origin 拒绝、单会话互斥、输入限流、关闭和超时。
- `RuntimeApiSupportTest` 覆盖分页默认值和非法分页参数转换为统一 `VALIDATION_ERROR`。
- `ManagedWorkspaceControllerTest` 覆盖应用版本工作区入口的认证主体、traceId、请求体转换和最近使用接口。
- `RuntimeSecurityConfigTest` 覆盖本地 `frontend-opencode` real E2E Origin 白名单。
- `AuthControllerRolesTest`、`ConfigurationManagementControllerTest` 覆盖认证响应 roles、`APP_ADMIN`/`SUPER_ADMIN` 鉴权和 SSH key 不回显私钥。
- `ApiTokenWebFilterTest`、`InMemoryRateLimitWebFilterTest`、`TraceIdWebFilterTest`、`GlobalExceptionHandlerTest` 覆盖鉴权、限流、traceId 和统一错误响应。

## 后续 AI 编码指引

新增 API 时先确认业务实现应落在哪个业务模块；本模块只新增 Controller/DTO/协议转换。平台自身接口放 `web.platform`，agent 兼容代理入口放 `web.agent`，横切入口支撑放 `web.common`。旧 URL 不删除，新 URL 必须同步记录到 `docs/api/http-api.md`。
