# 包说明：com.icbc.testagent.api

## 职责

API 定义包，承载 HTTP/SSE/WebSocket 入口、请求响应 DTO、统一响应包装、错误处理、traceId、鉴权、限流和 CORS/WebSocket 配置。

## 不负责

- 不实现 Workspace、Session、Run、Terminal 等业务流程。
- 不直接访问持久化实现或 JDBC 实现。
- 不直接调用 generated SDK 或 opencode facade。

## 主要程序清单

- `web.platform.WorkspaceController`、`web.platform.SessionController`、`web.platform.RunController`、`web.platform.TerminalController`：平台协议入口，旧 `/api/...`、`/api/internal/platform/...` 和 Run 相关 `/api/internal/agent/{agentId}/...` URL 并行映射；SessionController 暴露消息列表和 active-run 恢复入口。
- `web.platform.PlatformOpencodeRuntimeController`：平台侧 opencode runtime 代理入口，只承载旧 `/api/...` 与 `/api/internal/platform/...` 路径，并把可选用户主体交给业务层决定用户进程或固定节点 fallback。
- `web.platform.UserOpencodeBackendRoutingWebFilter` / `UserOpencodeBackendRoutingService`：用户已有 ACTIVE opencode binding 属于远端服务器时，在 Controller 前把用户进程状态、初始化、Run 启动和 opencode runtime 代理请求转发到 binding 所属服务器 Java；透传用户 Authorization/traceId/body，并用内部路由头防止循环。
- `web.platform.RuntimeManagementController`：超级管理员运行管理入口，校验 `SUPER_ADMIN` 后把筛选、分页、命令参数和 traceId 交给 runtime 查询/命令服务；API 层不实现 opencode server 启动、停止、状态查询或健康确认。
- `web.platform.SchedulerManagementController`：超级管理员定时任务管理入口，校验 `SUPER_ADMIN` 后把任务定义、手动触发和运行记录查询交给 scheduler 管理服务。
- `web.platform.UserManagementController`：超级管理员用户管理入口，校验 `SUPER_ADMIN` 后把用户查询、创建和单角色调整请求交给 system-management 服务。
- `web.platform.ConfigurationManagementController`：应用配置管理入口，代码库 DTO 包含 `englishName`；设置页创建应用工作空间时根据当前用户 READY opencode 进程确定目标 Linux 服务器，并提供 `workspace-create-operations/{operationId}` 轮询接口。
- `web.platform.InternalModelProviderManagementController`：超级管理员维护内部模型供应商地址和全局 token 的入口；`InternalModelProxyController` 是仅供 opencode 子进程调用的内部模型代理入口。
- `web.platform.AgentConfigController`：Agent 配置 HTTP 元数据、Git 操作和进度 ticket 入口；公共仓库初始化和显式拉取按 `linuxServerId` 路由到目标后端，公共 update-and-push 合并冲突读取/解决/取消接口复用工作区冲突协议，公共 worktree 列表只返回指定服务器 `ACTIVE/PUBLIC` 元数据和创建人字段，文件内容操作继续走平台文件 WebSocket。
- `web.agent.AgentOpencodeRuntimeController`：agent 侧 opencode 兼容代理入口，承载 `/api/internal/agent/{agentId}/...` 路径并把 agentId 与可选用户主体交给业务层选择 runtime。
- `web.platform.RuntimeDtos`、`web.platform.AuthDtos`：平台 API 请求/响应 DTO。
- `web.common.TraceIdWebFilter`、`web.common.JwtAuthWebFilter`、`web.common.ApiTokenWebFilter`、`web.common.InMemoryRateLimitWebFilter`、`web.common.GlobalExceptionHandler`：入口公共处理。
- `web.common.RuntimeApiSupport`、`web.common.AuthWebSupport`：Controller 与 WebFilter 共用的 HTTP 边界工具。
- `web.platform.WorkspaceFileWebSocketHandler`：受控平台文件 WebSocket upgrade 入口，覆盖 workspace 文件、服务器目录选择和 Agent 配置文件 RPC。
- `web.platform.TerminalWebSocketHandler`：受控 PTY WebSocket upgrade 入口。
- `config.RuntimeSecurityConfig`、`config.TerminalWebSocketConfig`：API 层安全和 WebSocket mapping。
- 本地默认 CORS 覆盖主前端和 `frontend-opencode` 的 Vite dev/preview/real E2E 端口；生产必须由部署配置显式指定。

`RunController` 的 RunEvent SSE 入口只做协议合流：先通过当前 agent runtime 输出 projected messages snapshot，再输出 `test-agent-event` 提供的 durable replay/live bus/可选 Redis SSE；不直接访问 Repository 或 generated SDK。

## 允许依赖

- `test-agent-common`、`test-agent-domain`、`test-agent-observability`、`test-agent-event`。
- `test-agent-workspace-management`、`test-agent-opencode-runtime` 的公开 service/DTO。
- `test-agent-scheduler` 的管理服务。
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
- RunEvent SSE 测试必须覆盖 Last-Event-ID/query resume 和 opencode projected messages snapshot 合流；Session API 测试必须覆盖 active-run 和新增 token/cost DTO 字段。
- Scheduler 管理 API 测试必须覆盖 `SUPER_ADMIN` 成功、非超级管理员拒绝、非法筛选参数和统一错误格式。
- 用户管理 API 测试必须覆盖 `SUPER_ADMIN` 查询、创建、角色调整、角色列表和非超级管理员/匿名拒绝。
- Configuration 管理 API 测试必须覆盖代码库英文名 DTO、创建应用工作空间的用户 opencode 服务器透传、进度查询鉴权和统一错误格式。
- Terminal WebSocket 测试必须覆盖 ticket 消费、origin 校验、单会话互斥、限流和超时。
- Agent 配置 Controller 测试必须覆盖公共仓库初始化/显式拉取的 `SUPER_ADMIN` 权限与目标服务器路由，公共 worktree 列表的 `SUPER_ADMIN` 权限、`linuxServerId` 缺参校验和响应字段。
- 入口公共工具测试覆盖 traceId、分页参数、统一错误、鉴权和限流。

## 修改时必须同步更新

- `backend/test-agent-api/README.md`。
- `docs/api/http-api.md`。
- `docs/api/event-stream.md`，如果涉及 SSE。
- `docs/standards/security.md`，如果涉及鉴权、限流、CORS 或 WebSocket 安全。
