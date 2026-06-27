# test-agent-api

## 工程定位

后端 HTTP/SSE/WebSocket API 定义模块，只做协议入口、请求响应 DTO、统一响应、错误、traceId、鉴权、限流和受控 WebSocket 适配。

## 主要职责

- 暴露旧 `/api/...` 兼容 URL。
- 暴露新增 `/api/internal/platform/...`、`/api/internal/agent/{agentId}/...` 和预留 `/api/public/...` URL。
- `web.platform` 承载平台自身接口和旧兼容入口，`web.agent` 承载 agent runtime 兼容代理入口，`web.common` 承载 traceId、鉴权、限流和统一异常等入口支撑。
- 暴露 Workspace 受控目录选择接口，Controller 只委托 workspace-management 目录服务。
- 暴露应用版本工作区、版本 `git pull` 和个人工作区运行接口，Controller 只解析登录主体、traceId、当前用户 opencode agent 服务器并委托 workspace-management；应用成员权限由业务服务校验。
- 暴露配置管理接口，Controller 只委托 configuration-management 业务服务；应用与工作区接口统一校验 `APP_ADMIN`，`SUPER_ADMIN` 继承该能力。设置页创建应用工作空间接口会读取当前用户 READY opencode 进程的 Linux 服务器并委托 workspace-management 创建初始版本工作区，进度通过 `workspace-create-operations/{operationId}` HTTP 轮询查询。
- Controller 只调用业务模块 service，不直接访问 Repository、generated SDK 或 JDBC 实现。
- 维护 `RuntimeDtos` 等平台 DTO，不返回 generated SDK DTO。
- runtime Controller 只读取可选认证主体并传入 `test-agent-opencode-runtime`，有用户主体时由业务层使用用户专属 opencode 进程，无用户主体时保持 static-token 兼容 fallback。
- RunEvent SSE 建连时先委托 runtime 恢复 opencode projected messages，再进入 durable replay 与 live bus 合流。
- 暴露 Workspace 文件 WebSocket 路由、ticket 和 WebSocket RPC 入口：Controller/Handler 只做鉴权、`SUPER_ADMIN` 校验、ticket、Origin、traceId、协议 envelope 和统一错误包装，文件系统操作继续委托 `test-agent-workspace-management`。
- 暴露 Agent 配置管理 HTTP 和进度 WebSocket 入口：Controller 只做认证、`SUPER_ADMIN` 写权限、DTO 和 traceId 转换；进度 WebSocket 使用一次性 ticket、Origin 白名单和 `snapshot/step/completed/failed` envelope，业务逻辑委托 `test-agent-workspace-management`。
- 暴露 opencode-manager 兼容诊断 API 和 WebSocket 控制面入口，入口只做 manager token 鉴权、DTO/消息适配和 traceId 处理；Go manager 运行路径不通过 HTTP 与 Java 交互，后端列表发现走 WebSocket `backendListRequest/backendListResponse`。
- 暴露超级管理员只读运行管理 overview 与容器/后端指标历史 API，Controller 只做 `SUPER_ADMIN` 鉴权、分页/筛选/历史查询参数校验、用户名筛选参数透传、DTO 映射和 traceId 处理；指标历史主参数为 `windowMinutes`，`hours` 仅兼容旧客户端。
- 暴露超级管理员定时任务管理 API，Controller 只做 `SUPER_ADMIN` 鉴权、分页/筛选参数校验、DTO 映射和 traceId 处理。
- Session 消息查询优先委托 runtime 刷新 agent projected messages，失败时返回数据库快照；active-run API 供前端刷新后恢复 SSE。
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
- `test-agent-scheduler`。
- Spring WebFlux、Validation、Security。

## 禁止依赖

- `test-agent-persistence`。
- `test-agent-opencode-sdk-generated`。
- Repository 实现类。
- 业务规则、文件系统操作、opencode 调用编排。

## 测试覆盖

- `RuntimeControllerTest` 覆盖 Workspace、目录选择、Session、Run、Diff、agent-scoped Run URL、RunEvent SSE 恢复快照和内部平台兼容 URL。
- `RuntimeManagementControllerTest` 覆盖运行管理 overview 与指标历史 API 的 `SUPER_ADMIN` 成功、用户名筛选/响应映射、`windowMinutes` 预设窗口、`hours` 兼容、历史参数默认值与上限、非超级管理员拒绝、未认证、非法分页/状态参数和 traceId。
- `SchedulerManagementControllerTest` 覆盖定时任务管理 API 的 `SUPER_ADMIN` 成功、`APP_ADMIN`/匿名拒绝、非法状态参数、任务 patch、手动触发和运行记录查询。
- `ManagerBackendDiscoveryControllerTest` 覆盖 manager token 鉴权、统一响应、traceId 和 Redis 在线后端实例 DTO；该接口仅作兼容诊断。
- `ManagerControlWebSocketHandlerTest` 覆盖 `register`、`managerHeartbeat`、`backendListRequest`、命令结果和错误 envelope 的 WebSocket 入口适配。
- `PlatformOpencodeRuntimeControllerTest` 覆盖旧 `/api/...` 与 `/api/internal/platform/...` 的 opencode runtime 代理入口、MCP tools、permission reply、session share、traceId 和可选用户主体透传。
- `AgentOpencodeRuntimeControllerTest` 覆盖 `/api/internal/agent/{agentId}/...` 原始 opencode 路径兼容、agentId、traceId 和可选用户主体透传。
- `AuthWebSupportTest` 覆盖可选认证主体读取，确保 static-token 兼容入口不会因缺少用户主体抛错。
- `TerminalControllerTest`、`TerminalWebSocketHandlerTest` 覆盖 PTY ticket、内部平台 WebSocket URL、origin 拒绝、单会话互斥、输入限流、关闭和超时。
- Workspace 文件 WebSocket 入口应覆盖 route、ticket、Origin、同服务器校验、RPC 成功/错误 envelope 和目录删除拒绝；对应 HTTP/协议契约同步维护在 `docs/api/http-api.md` 与 `docs/api/event-stream.md`。
- Agent 配置入口应覆盖公共/工作空间 status、文件读写权限、Git 操作鉴权、operation ticket、Origin 拒绝和进度 envelope；对应 HTTP/协议契约同步维护在 `docs/api/http-api.md` 与 `docs/api/event-stream.md`。
- `RuntimeApiSupportTest` 覆盖分页默认值和非法分页参数转换为统一 `VALIDATION_ERROR`。
- `ManagedWorkspaceControllerTest` 覆盖应用版本工作区入口的认证主体、traceId、当前用户 opencode 服务器透传、请求体转换、版本 `git pull` 和最近使用接口。
- `RuntimeSecurityConfigTest` 覆盖本地 `frontend-opencode` real E2E Origin 白名单。
- `AuthControllerRolesTest`、`ConfigurationManagementControllerTest` 覆盖认证响应 roles、`APP_ADMIN`/`SUPER_ADMIN` 鉴权、代码库英文名 DTO、工作空间创建进度轮询和 SSH key 不回显私钥。
- `ApiTokenWebFilterTest`、`InMemoryRateLimitWebFilterTest`、`TraceIdWebFilterTest`、`GlobalExceptionHandlerTest` 覆盖鉴权、限流、traceId 和统一错误响应。

## 后续 AI 编码指引

新增 API 时先确认业务实现应落在哪个业务模块；本模块只新增 Controller/DTO/协议转换。平台自身接口放 `web.platform`，agent 兼容代理入口放 `web.agent`，横切入口支撑放 `web.common`。旧 URL 不删除，新 URL 必须同步记录到 `docs/api/http-api.md`。
