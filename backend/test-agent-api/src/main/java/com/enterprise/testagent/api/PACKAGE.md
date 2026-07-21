# 包说明：com.enterprise.testagent.api

## 职责

API 定义包，承载 HTTP/SSE/WebSocket 入口、请求响应 DTO、统一响应包装、错误处理、traceId、鉴权、限流和 CORS/WebSocket 配置。

## 不负责

- 不实现 Workspace、Session、Run、Terminal 等业务流程。
- 不直接访问持久化实现或 JDBC 实现。
- 不直接调用 generated SDK 或 opencode facade。

## 主要程序清单

- `web.platform.WorkspaceController`、`web.platform.SessionController`、`web.platform.RunController`、`web.platform.TerminalController`：平台协议入口，旧 `/api/...`、`/api/internal/platform/...` 和 Run 相关 `/api/internal/agent/{agentId}/...` URL 并行映射；SessionController 暴露消息列表和 active-run 恢复入口。
- `web.platform.RunEventSseBackendRoutingWebFilter`、`web.platform.BackendSseForwarder`：RunEvent SSE 建连前按 Run 原始生产 Java 流式转发，保留 Authorization、trace、Last-Event-ID、query 和 `text/event-stream`，并复用 `X-Test-Agent-Backend-Routed` 防循环。
- `web.platform.RunControlBackendRoutingWebFilter`、`web.platform.BackendRoutingErrorWriter`：两个 Run cancel 写入口严格路由到生产 Java；归属解析或普通 HTTP 转发失败时直接写统一平台错误，禁止降级执行本机副作用。
- `web.platform.PlatformOpencodeRuntimeController`：平台侧 opencode runtime 代理入口，只承载旧 `/api/...` 与 `/api/internal/platform/...` 路径，并把可选用户主体交给业务层决定用户进程或固定节点 fallback。
- `web.platform.UserOpencodeBackendRoutingWebFilter` / `UserOpencodeBackendRoutingService`：用户已有 ACTIVE opencode binding 属于远端服务器时，在 Controller 前把用户进程状态、初始化、Run 启动和 opencode runtime 代理请求转发到 binding 所属服务器 Java；透传用户 Authorization/traceId/body，并用内部路由头防止循环。
- `web.platform.RuntimeManagementController`：超级管理员运行管理入口，校验 `SUPER_ADMIN` 后把筛选、分页、命令参数和 traceId 交给 runtime 查询/命令服务；API 层不实现 opencode server 启动、停止、状态查询或健康确认。
- `web.platform.SchedulerManagementController`：超级管理员定时任务管理入口，校验 `SUPER_ADMIN` 后把任务定义、手动触发和运行记录查询交给 scheduler 管理服务。
- `web.platform.CommonParameterMemoryController` / `CommonParameterMemoryBackendRoutingService`：超级管理员显式 JVM 内存参数查询与手工刷新入口；按 `backendProcessId` 精确聚合全部或单个在线 Java，跨 Java 复用公共 resolver/forwarder，部分失败保留逐进程结果。
- `web.platform.NightExecutionController`、`web.platform.NightExecutionDtos`：当前用户夜间时段和任务创建/查询/改期/取消/失败卡关闭入口；完整 Run 输入不进入响应 DTO。
- `web.platform.UserManagementController`：超级管理员用户管理入口，校验 `SUPER_ADMIN` 后把用户查询、创建和单角色调整请求交给 system-management 服务。
- `web.platform.ConfigurationManagementController`：应用配置管理入口，代码库 DTO 包含 `englishName`；设置页创建应用工作空间时根据当前用户 READY opencode 进程确定目标 Linux 服务器，并提供 `workspace-create-operations/{operationId}` 轮询接口。
- `web.platform.ReferenceRepositoryController`、`web.platform.ReferenceRepositoryDtos`：应用引用资产库列表、初始化、同步、受控分支切换、只读指针核验、含可空 `repositoryPath` 的状态和单层树内部入口；只负责 `APP_ADMIN`（含 `SUPER_ADMIN`）鉴权、分支请求、traceId 和阻塞任务调度。
- `web.platform.InternalModelProviderManagementController`：超级管理员维护内部模型供应商地址和全局 token 的入口；`InternalModelProxyController` 是仅供 opencode 子进程调用的内部模型代理入口。
- `web.platform.AgentConfigController`：Agent 配置 HTTP 元数据、Git 操作和进度 ticket 入口；公共仓库初始化和显式拉取按 `linuxServerId` 路由到目标后端，公共 update-and-push 合并冲突读取/解决/取消接口复用工作区冲突协议，公共 worktree 列表只返回指定服务器 `ACTIVE/PUBLIC` 元数据和创建人字段，文件内容操作继续走平台文件 WebSocket。
- `web.agent.AgentOpencodeRuntimeController`：agent 侧 opencode 兼容代理入口，承载 `/api/internal/agent/{agentId}/...` 路径并把 agentId 与可选用户主体交给业务层选择 runtime。
- `web.platform.RuntimeDtos`、`web.platform.AuthDtos`：平台 API 请求/响应 DTO；Session、SessionMessage、Run 可选暴露 `sourceType/sourceRefId`，Run、SessionMessage、Run 历史与 Session 历史响应的新存储/摘要元数据保持 nullable，并通过显式映射重载接入新模式投影，旧领域对象不会被误标记。
- `web.common.TraceIdWebFilter`、`web.common.JwtAuthWebFilter`、`web.common.ApiTokenWebFilter`、`web.common.InMemoryRateLimitWebFilter`、`web.common.GlobalExceptionHandler`：入口公共处理。
- `web.common.RuntimeApiSupport`、`web.common.AuthWebSupport`：Controller 与 WebFilter 共用的 HTTP 边界工具。
- `web.platform.WorkspaceFileWebSocketHandler`：受控平台文件 WebSocket upgrade 入口，覆盖 workspace 原始文件、引用组合视图、服务器目录选择和 Agent 配置文件 RPC；每条 workspace RPC 使用 ticket 用户重新执行当前成员校验，非托管 Workspace 仅放行 ticket 中的 `SUPER_ADMIN` 兼容访问。
- `web.platform.TerminalWebSocketHandler`：受控 PTY WebSocket upgrade 入口。
- `config.RuntimeSecurityConfig`、`config.TerminalWebSocketConfig`：API 层安全和 WebSocket mapping；CORS 允许可选 `X-Test-Agent-Linux-Server-Id` 首跳提示头，但不把它作为后端鉴权或路由事实源。
- 本地默认 CORS 覆盖主前端和 `frontend-opencode` 的 Vite dev/preview/real E2E 端口；生产必须由部署配置显式指定。

`RunController` 的 RunEvent SSE 入口只做目标 Java 上的协议合流：legacy 通过当前 agent runtime 输出 projected messages snapshot，再输出 `test-agent-event` 提供的 durable replay/live bus；Redis 新模式不触发远端 snapshot，首帧输出物化 reset，再由最短 5 秒安全扫描/live 即时唤醒 Redis runtime 尾流。Controller 不直接访问 Repository 或 generated SDK。

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

- `backend/test-agent-api/src/test/java/com/enterprise/testagent/api/web/platform`。
- `backend/test-agent-api/src/test/java/com/enterprise/testagent/api/web/agent`。
- `backend/test-agent-api/src/test/java/com/enterprise/testagent/api/web/common`。
- `backend/test-agent-api/src/test/java/com/enterprise/testagent/api/config`。
- 平台 Controller 测试必须覆盖公开 `/api/...`、内部 `/api/internal/platform/...` 和 Run 相关 `/api/internal/agent/{agentId}/...` 路径；agent Controller 测试必须覆盖需要兼容的 `/api/internal/agent/opencode/...` 路径、agentId 透传和可选用户主体透传。
- RunEvent SSE 测试必须覆盖 Last-Event-ID/query resume、opencode projected messages snapshot 合流、按 Run 生产 Java 路由、流式转发 header/query 保留和目标缺失时本机 DB replay 降级；Session API 测试必须覆盖 active-run 和新增 token/cost DTO 字段。
- Scheduler 管理 API 测试必须覆盖 `SUPER_ADMIN` 成功、非超级管理员拒绝、非法筛选参数和统一错误格式。
- JVM 内存通用参数 API 测试必须覆盖四个 `SUPER_ADMIN` 接口、同服务器多 Java、当前/远端进程、部分失败、离线、超时和防二次转发。
- 夜间任务 API 测试必须覆盖认证、owner 隔离、输入校验、完整输入不回显、写入口用户 binding 路由和统一错误格式。
- 用户管理 API 测试必须覆盖 `SUPER_ADMIN` 查询、创建、角色调整、角色列表和非超级管理员/匿名拒绝。
- Configuration 管理 API 测试必须覆盖代码库英文名 DTO、创建应用工作空间的用户 opencode 服务器透传、进度查询鉴权和统一错误格式。
- 引用资产库 API 测试必须覆盖 7 个端点、请求/响应 DTO、traceId、`APP_ADMIN` 与 `SUPER_ADMIN` 成功和普通用户拒绝。
- Terminal WebSocket 测试必须覆盖 ticket 消费、origin 校验、单会话互斥、限流和超时。
- Agent 配置 Controller 测试必须覆盖公共仓库初始化/显式拉取的 `SUPER_ADMIN` 权限与目标服务器路由，公共 worktree 列表的 `SUPER_ADMIN` 权限、`linuxServerId` 缺参校验和响应字段。
- 入口公共工具测试覆盖 traceId、分页参数、统一错误、鉴权和限流。

## 修改时必须同步更新

- `backend/test-agent-api/README.md`。
- `docs/api/http-api.md`。
- `docs/api/event-stream.md`，如果涉及 SSE。
- `docs/standards/security.md`，如果涉及鉴权、限流、CORS 或 WebSocket 安全。
