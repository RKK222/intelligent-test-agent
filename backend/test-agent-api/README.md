# test-agent-api

## 工程定位

后端 HTTP/SSE/WebSocket API 定义模块，只做协议入口、请求响应 DTO、统一响应、错误、traceId、鉴权、限流和受控 WebSocket 适配。

## 主要职责

- 暴露 `/api/internal/platform/...`、`/api/internal/agent/{agentId}/...` 和预留 `/api/public/...` URL。
- 旧 runtime/workspace `/api/...` 兼容 URL 由 `LegacyApiGoneWebFilter` 在进入 Controller 前统一返回 `410 API_GONE`；登录认证 `/api/auth/login|logout|me|refresh` 保留为稳定入口。
- `web.platform` 承载平台自身接口，`web.agent` 承载 agent runtime 代理入口，`web.common` 承载 traceId、鉴权、限流、旧接口作废拦截和统一异常等入口支撑。
- 普通 Workspace HTTP 入口只保留查询和文件路由；服务器目录选择与创建仅通过超级管理员文件 WebSocket ticket 执行。
- 暴露应用版本工作区、版本 `git pull` 和个人工作区运行接口，Controller 只解析登录主体、traceId、当前用户 opencode agent 服务器并委托 workspace-management；应用成员权限由业务服务校验。
- 工作区 Git 入口包含 diff/discard、真实 stage/unstage、三方冲突读取、单文件解决、取消 merge、个人 worktree 本地提交和 feature 发布；发布只把个人 `HEAD` 中允许发布的非 `spec/**` 文件投影到应用 feature worktree，不 merge 个人分支，`SUPER_ADMIN` 也不能绕过目录规则。冲突内容、index 操作、解决规则与发布路径校验由 workspace-management 负责，Controller 只转换 DTO。个人工作区提交/发布 DTO 透传可选 `operationId`，供业务层复用 Agent 配置进度 WebSocket 推送当前 Git 命令。
- 暴露配置管理接口，Controller 只委托 configuration-management 业务服务；新建应用只允许 `SUPER_ADMIN`，应用成员、版本库和工作区管理校验 `APP_ADMIN` 且 `SUPER_ADMIN` 继承该能力。版本库类型下拉、部署模式选项接口、新增代码库的 `repositoryType/deploymentMode` DTO、应用版本库远端树接口和工作空间创建 `directoryNew` DTO 仅做协议转换，旧 `standard` 兼容派生、内部模式 SSH 前缀、远端树过滤和别名唯一校验由业务服务处理。设置页保存应用工作空间接口会读取当前用户 READY opencode 进程的 Linux 服务器并委托 workspace-management 创建初始版本工作区，进度通过 `workspace-create-operations/{operationId}` HTTP 轮询查询；分支和远端树加载接口不触发 clone。
- Controller 只调用业务模块 service，不直接访问 Repository、generated SDK 或 JDBC 实现。
- 维护 `RuntimeDtos` 等平台 DTO，不返回 generated SDK DTO。
- runtime Controller 只读取可选认证主体并传入 `test-agent-opencode-runtime`，有用户主体时由业务层使用用户专属 opencode 进程，无用户主体时保持 static-token 兼容 fallback。
- `POST /api/internal/agent/{agentId}/sessions/{sessionId}/run-context` 只读取必需认证主体、agentId、sessionId 和 traceId，委托 runtime 在签发 fence 内从权威数据构造会话运行上下文；托管 Workspace 会实时校验应用启用、有效成员及个人 Workspace owner，`SUPER_ADMIN` 不旁路成员规则。响应只返回 `contextToken/contextVersion/expiresAt`。Run 请求 DTO 可选接收 `contextToken/clientRequestId`，不接受客户端传入可信工作区路径、进程、节点或服务器快照。有效 token 由 runtime resolver 复用完整服务端快照，并通过公共 `querySnapshot` 动态健康探测；已有远端 session 时，Session、Workspace、进程、ExecutionNode 和 binding 均为 0 次 Repository SELECT。稳定 `RUNNING` 为 0 次数据库写入，只有稳定状态、PID 或服务地址变化时写一次；`STALE` 拒绝当前 Run 但保留 token，`NOT_STARTED` 才失效进程上下文。
- `DELETE /api/internal/platform/opencode-runtime/sessions/{sessionId}` 把当前认证用户传入 Session 归档服务，并在归档写库前建立 Redis revoke gate；数据库失败只回滚本次撤销 token，并发归档 gate 不受影响。Workspace root/server 变化、可信路径参数重载、成员和全局角色撤权也已分别接入 Workspace、全局或用户维度失效。
- 当前用户 opencode 进程接口包含 `/processes/me` 强状态查询、`/processes/me/health` 弱健康检查、初始化和初始化进度查询。弱健康检查只做协议适配，业务逻辑委托 `OpencodeProcessStatusQueryService.weakHealth`，跨 Java 路由由专用过滤器按 query 中的 `linuxServerId` 和 Redis 后端快照处理，不读取用户 binding 数据库；初始化接口支持可选 `operationId` 请求体，`initialize-operations/{operationId}` 只读查询不触发 manager health/start 或 RunEvent。
- RunEvent SSE 建连前由 `RunEventSseBackendRoutingWebFilter` 按 Run 原始归属定位生产 Java，目标不是当前 Java 时使用 `BackendSseForwarder` 流式转发 `text/event-stream` 并保留 Authorization、trace、Last-Event-ID 和 query；SSE 路由不可用时仍允许本机只读恢复。两个 Run cancel 写入口由 `RunControlBackendRoutingWebFilter` 使用同一生产端解析和普通 `BackendHttpForwarder` 转发；cancel 每一跳都重新执行 strict owner 解析，不信任浏览器可伪造的 `X-Test-Agent-Backend-Routed`，到达当前被选中的生产 Java 后解析器自然放行。路由归属缺失、目标后端不可用或转发失败时直接写统一平台错误，禁止落到当前 Java 执行副作用。目标 Java 的 `RunController` 在详情、取消、Diff、SSE 和 Run 级 session-tree 读取或副作用前统一校验认证用户归属，再委托 runtime 按固定 storageMode 建流或取消；新模式只比较 Redis manifest `userId`，legacy/manifest 缺失才回查 Run 与 Session。两个 agent-scoped `session-tree/messages` HTTP 历史入口统一按 Redis 详情 → OpenCode 完整会话 → PostgreSQL 双摘要恢复，并返回可选 `historyRepresentation/replayAvailable/detailsAvailableUntil`；Redis 与摘要来源不查询旧 `run_events`，只有 legacy OpenCode 来源补充 durable 状态。旧 `/api/runs/...` 与 `/api/sessions/...` 已作废。
- 暴露 Workspace/Agent 配置文件 WebSocket 路由、ticket 和 WebSocket RPC 入口：Controller/Handler 只做鉴权、`APP_ADMIN`/`SUPER_ADMIN` 权限校验、ticket、Origin、traceId、协议 envelope 和统一错误包装，工作区上传/复制/移动的源路径与目标路径分别鉴权，文件系统操作继续委托 `test-agent-workspace-management`。WebSocket 单帧上限按上传文件 Base64 膨胀量配置，解码后仍由业务层执行单文件大小限制。普通成员的应用版本副本只读，个人 worktree 普通文件可写；`.opencode/agents/**`、`.opencode/skills/**` 及其 rules/templates 仅 APP_ADMIN 可写。Workspace 路由优先使用用户进程服务器归属；Agent 配置文件路由按 scope/workspace/worktree 的服务器归属定位目标后端，不新增跨服务器 HTTP 文件代理。
- 暴露 Agent 配置管理 HTTP 和进度 WebSocket 入口：Controller 只做认证、角色校验、目标服务器路由、DTO 和 traceId 转换；公共 Git 仍仅 `SUPER_ADMIN`，应用级 Agent/Skill Git 由 `APP_ADMIN`（含 `SUPER_ADMIN`）操作。个人/应用工作区 Git 接口通过统一用户 binding 路由到目标 Java，进度 WebSocket 使用一次性 ticket、Origin 白名单和 `snapshot/step/completed/failed` envelope；ticket 响应使用当前 Java 身份生成绝对 WebSocket URL，确保多后台 upgrade 回到签发 JVM。
- 暴露 opencode-manager WebSocket 控制面入口，入口只做 manager token 鉴权、DTO/消息适配和 traceId 处理；同一连接的 health/start/restart/stop 等出站控制消息在连接级串行 emission，并检查 Reactor sink 结果，发送失败立即进入统一错误链路并取消 pending command，禁止静默等待 command timeout；旧 manager-backends HTTP 诊断入口已作废，Go manager 运行路径不通过 HTTP 与 Java 交互，只连接本服务器 Java，`backendListRequest/backendListResponse` 仅保留为兼容诊断协议。
- 后端 Java 路由统一使用 runtime 的 `BackendJavaRouteResolver` 解析当前服务器、`linuxServerId -> BackendJavaProcess` 和 `containerId -> linuxServerId`；API 层普通 Java->Java HTTP 转发走 `BackendHttpForwarder`，RunEvent SSE 长连接走 `BackendSseForwarder` 流式转发，两者都设置 `X-Test-Agent-Backend-Routed` 防循环并透传 Authorization、traceId 和 query。两个 start-run 入口携带 `contextToken` 时，路由过滤器在 32 MiB 上限内缓存请求体并通过 Redis 只读解析 token 绑定的生产服务器，不查询用户进程 assignment，也不刷新 token TTL；字段已出现但为空、非字符串或失效时 fail-closed 返回 409，不回退 assignment。远端转发和本地 Controller 均可再次读取完整 body。无 token 的兼容请求仍走 assignment 路由。后续新增任何 opencode-manager 路由或 Java->manager 控制入口，都必须复用这套公共程序。
- `web.aop.ApiLoggingAspect` 按目标 Controller logger 记录前端 HTTP 操作入口、出口、耗时、状态和脱敏请求/响应摘要；`contextToken` 与 Authorization、Cookie 等敏感字段同样强制掩码。`web.aop.WebSocketLoggingAspect` 按目标 WebSocket handler logger 记录前端长连接入口、结束信号和异常；`web.aop.ServiceLoggingAspect` 按目标 Service logger 记录业务服务入口、出口、耗时、状态和轻量参数摘要。三者统一进入 `logs/backend.log`，ERROR 级别同时进入 `logs/error.log`；SSE 相关 Controller/Service/logger 还会额外进入 `logs/sse.log`。
- 暴露超级管理员运行管理 overview、容器/按稳定服务器身份的后端指标历史和有主/无主 opencode server 重启/停止 API；旧后端进程指标入口已作废。Controller 只做 `SUPER_ADMIN` 鉴权、分页/筛选/历史/容器/端口参数校验、用户名筛选参数透传、manager 下属 opencode server 明细和 `BOUND/UNBOUND` 归属 DTO 映射、命令结果 DTO 映射、后端指标 DTO 映射和 traceId 处理；后端指标 DTO 按可空字段透传服务器 CPU/load/内存/swap/磁盘、Java 进程 CPU/RSS/FD、JVM heap/non-heap/direct/mapped/GC/线程字段，并保留旧别名 `memoryMaxBytes`、`jvmGcPauseMillis`。重启/停止命令先按 `containerId` 的 Redis manager 快照定位容器所属 `linuxServerId`，目标不是当前 Java 或同服务器选中 Java 时透传用户 JWT 和 traceId 转发到目标 Java，由目标 Java 控制本服务器 manager。API 层不实现 opencode server 启动、停止、状态查询或健康确认；用户进程初始化、STOPPED 进程重启和 `port ... is not managed` 后重新拉起由 `test-agent-opencode-runtime` 的 `OpencodeProcessStartupService` 完成，平台已有进程记录的停止确认和 `STOPPED` 回写由 `OpencodeProcessStopService` 完成，状态查询、健康探测和 heartbeat 刷新由 `OpencodeProcessStatusQueryService` 完成。指标历史主参数为 `windowMinutes`，`hours` 仅兼容旧客户端。
- 暴露超级管理员定时任务管理 API，Controller 只做 `SUPER_ADMIN` 鉴权、分页/筛选参数校验、DTO 映射和 traceId 处理。
- 暴露超级管理员用户管理 API，Controller 只做 `SUPER_ADMIN` 鉴权、分页参数、创建用户请求和单角色调整请求转换；用户创建、角色替换和 ROLE 字典校验委托 `test-agent-system-management`。
- 暴露 AI Run 整体回复反馈 API：单查/写入按 `runId`，批量查询每次最多 100 个 Run；Controller 只读取当前登录用户和 traceId，成功状态、主对话与归属校验由 runtime 服务完成。旧 messageId API 保留兼容。
- 暴露超级管理员运营分析 API，Controller 只做 `SUPER_ADMIN` 鉴权、ISO 时间参数解析、通用筛选参数传递、CSV 响应头和统一错误转换；查询服务只读 rollup。
- `GET /api/internal/platform/opencode-runtime/sessions` 是当前登录用户历史会话分页接口，支持 `page/size/q`，返回 `workspaceContext` 且按 `updatedAt desc` 排序。Session 历史正文恢复主入口是 agent-scoped session tree messages；内部平台 messages 接口的 `refresh=false` 只读数据库快照用于只读 transcript、Run ID 恢复和旧消息反馈兼容，不再为新反馈寻找 assistant messageId。`RunResponse` 可选携带 `storageMode/clientRequestId/detailsAvailableUntil`；active-run API 供前端刷新后恢复 SSE。
- `GET /api/internal/platform/opencode-runtime/sessions/runtime-state` 和 `/runtime-state/events` 暴露当前登录用户历史会话运行态摘要和 fetch SSE 状态通道；Controller 只读取登录主体、traceId、映射 DTO 和输出 SSE，运行计数、question 待关注状态和事件触发刷新委托 `test-agent-opencode-runtime`。用户已有 Redis 运行态 marker 时，摘要和 active-run fallback 均只读 Redis 索引/manifest，不由 API 层回查 Repository。
- `POST /api/internal/platform/opencode-runtime/sessions/{sessionId}/side-question` 保留同步兼容路径；`.../side-question/runs` 与 agent-scoped 等价路径立即返回旁路 Run。`POST /api/internal/platform/opencode-runtime/manual-question/runs` 在无主对话时按工作区创建归档内部 Session 和远端临时会话。两种流式路径都复用 RunEvent SSE、禁用工具、等待自然语言最终回答并删除临时会话，不追加或创建普通主会话历史。
- RunEvent SSE 路由由 runtime 服务优先使用 Redis manifest 的生产服务器，manifest 缺失的 legacy/旧 Run 才读取 routing/process 兼容数据。目标 Java 的 `REDIS_SUMMARY` 流首帧总发送 Redis 物化 `run.snapshot.reset`，随后按 `runtimeVersion` 分页读取 durable/transient 全事件尾流；最短 5 秒的 Redis 安全扫描负责丢唤醒补偿，本机 live 事件仍即时唤醒尾流读取。API 层只负责流式转发，不实现数据库轮询或运行态降级。
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
- 业务规则、文件系统操作、opencode 调用编排；尤其不得在 API 层实现 opencode server 启动/停止/状态查询、启动后或停止后 health、binding/heartbeat/ExecutionNode/进程状态回写。

## 测试覆盖

- `RuntimeControllerTest` 覆盖 Workspace 查询、Session、Run、Diff、agent-scoped Run URL、当前用户 opencode 进程强状态、弱健康和初始化进度 GET、RunEvent SSE 恢复快照、Run/Session session-tree messages 和内部平台 URL；`RunControllerAuthorizationTest` 覆盖他人 Run 在详情、取消、Diff、SSE 与 Run 级 session-tree 入口统一返回 `FORBIDDEN`，且不触发后续读取或副作用。
- `ConversationContextControllerTest` 覆盖已登录用户签发会话运行上下文、opaque 响应 DTO、traceId 和匿名拒绝；`RuntimeControllerTest` 同时覆盖 Run 请求 `contextToken/clientRequestId` 的 DTO 透传。
- `SessionRuntimeStateControllerTest` 覆盖当前登录用户运行态摘要、匿名拒绝、fetch SSE 首帧 snapshot 和 Run/question 事件后的 updated 推送。
- `RuntimeManagementControllerTest` 覆盖运行管理 overview、按 `linuxServerId` 的后端指标历史主 API 和进程重启/停止 API 的 `SUPER_ADMIN` 成功、扩展后的服务器/Java/JVM 指标字段响应、跨 Java 后端路由优先于本地 manager gateway、manager 下属 opencode server 明细与归属字段响应映射、命令结果响应映射、用户名筛选/响应映射、`windowMinutes` 预设窗口、`hours` 兼容、历史参数默认值与上限、非超级管理员拒绝、未认证、非法分页/状态参数和 traceId；`RuntimeManagementBackendRoutingServiceTest` 覆盖按容器归属服务器转发命令和路由头防循环。
- `SchedulerManagementControllerTest` 覆盖定时任务管理 API 的 `SUPER_ADMIN` 成功、`APP_ADMIN`/匿名拒绝、非法状态参数、任务 patch、手动触发和运行记录查询。
- `UserManagementControllerTest` 覆盖用户管理 API 的 `SUPER_ADMIN` 查询、创建、角色调整、角色列表和非超管/匿名拒绝。
- `AiRunFeedbackControllerTest` 覆盖登录用户提交、查询和批量读取 Run 反馈；`AiMessageFeedbackControllerTest` 覆盖旧消息接口兼容与匿名拒绝。
- `AnalyticsControllerTest` 覆盖运营分析 API 的 `SUPER_ADMIN` 成功、非超级管理员/匿名拒绝和非法时间参数统一校验错误。
- `ManagerControlWebSocketHandlerTest` 覆盖 `register`、`managerHeartbeat`、兼容 `backendListRequest` 忽略、命令结果、错误 envelope 和多线程并发控制命令完整送达的 WebSocket 入口适配。
- `UserOpencodeBackendRoutingWebFilterTest` 覆盖用户 opencode 进程请求按已有 binding 所属服务器转发、工作区个人 Git/应用配置操作跨服务器转发、公共配置聚合入口留在本地、内部路由头防循环、只读状态 GET 的降级，以及 agent/platform 两个 start-run 入口通过 context 只读路由时零 assignment 调用、远端/本地 body 可复读、过期或显式非法 token 统一 409 且不回显 token、请求体超限统一 400。
- `UserOpencodeWeakHealthRoutingWebFilterTest` 覆盖 `/processes/me/health` 按 query `linuxServerId` 随机转发到目标服务器在线 Java、目标后端缺失返回 `healthy=false`、路由头防循环和本服务器请求放行。
- `BackendHttpForwarderTest` 覆盖 Java->Java HTTP 转发对 Authorization、`X-Trace-Id`、query、body、content-type 和 `X-Test-Agent-Backend-Routed` 的统一透传。
- `RunEventSseBackendRoutingWebFilterTest` 覆盖 RunEvent SSE 按 Run 生产 Java 路由、平台/agent URL、防循环头和目标缺失时本机处理；`RunControlBackendRoutingWebFilterTest` 覆盖两个 cancel URL 的远端转发、本机放行、外部伪造 routed header 仍强制解析 owner，以及路由或转发失败时禁止执行本机 Controller；`RunEventSseRouteServiceTest` 固化 manifest 路由 0 次 routing/process Repository 查询，并区分 SSE 可回退解析与写操作严格解析；`BackendSseForwarderTest` 覆盖 SSE 流式转发保留 Authorization、`X-Trace-Id`、`Last-Event-ID`、query、`text/event-stream` 和 `X-Test-Agent-Backend-Routed`。
- `PlatformOpencodeRuntimeControllerTest` 覆盖 `/api/internal/platform/...` 的 opencode runtime 代理入口、MCP tools、permission reply、session share、traceId 和可选用户主体透传。
- `AgentOpencodeRuntimeControllerTest` 覆盖 `/api/internal/agent/{agentId}/...` 原始 opencode 路径兼容、agentId、traceId 和可选用户主体透传。
- `AuthWebSupportTest` 覆盖可选认证主体读取，确保 static-token 兼容入口不会因缺少用户主体抛错。
- `CurrentBackendWebSocketUrlFactoryTest`、`TerminalControllerTest`、`TerminalWebSocketHandlerTest` 覆盖当前 Java 绝对 WebSocket URL、PTY ticket、origin 拒绝、单会话互斥、输入限流、关闭和超时。
- Workspace 文件 WebSocket 入口应覆盖 route、ticket、Origin、同服务器校验、ticket 在归属未 READY 时复查强状态、RPC 成功/错误 envelope、上传/复制/移动、普通文件/目录树删除和受保护 `.opencode` 根目录拒绝；对应 HTTP/协议契约同步维护在 `docs/api/http-api.md` 与 `docs/api/event-stream.md`。
- Agent 配置入口应覆盖公共/工作空间 status、公共仓库列表、公共仓库初始化、当前用户公共 worktree 的服务器路由和所有权校验、文件 WebSocket route/ticket/op、文件读写权限、Git 操作鉴权、operation ticket、Origin 拒绝和进度 envelope；对应契约同步维护在 `docs/api/http-api.md` 与 `docs/api/event-stream.md`。
- `RuntimeApiSupportTest` 覆盖分页默认值和非法分页参数转换为统一 `VALIDATION_ERROR`。
- `ManagedWorkspaceControllerTest` 覆盖应用版本工作区入口的认证主体、traceId、当前用户 opencode 服务器透传、请求体转换、版本 `git pull`、工作区 Git stage/unstage、冲突解决和最近使用接口。
- `RuntimeSecurityConfigTest` 覆盖本地 `frontend-opencode` real E2E Origin 白名单。
- `AuthControllerRolesTest`、`ConfigurationManagementControllerTest` 覆盖认证响应 roles、`APP_ADMIN`/`SUPER_ADMIN` 鉴权、代码库英文名、版本库类型与部署模式 DTO、版本库类型/部署模式下拉接口、应用版本库远端树接口、工作空间创建进度轮询和 SSH key 不回显私钥。
- `ApiTokenWebFilterTest`、`InMemoryRateLimitWebFilterTest`、`TraceIdWebFilterTest`、`GlobalExceptionHandlerTest`、`LegacyApiGoneWebFilterTest` 覆盖鉴权、限流、traceId、旧接口 410 和统一错误响应。
- `ApiLoggingAspectTest` / `ServiceLoggingAspectTest` / `WebSocketLoggingAspectTest` 覆盖 Controller、Service 与 WebSocket 日志切面在同步、响应式和错误路径下保留原调用语义；`SensitiveDataMaskerTest` 覆盖 `contextToken` 请求/响应字段脱敏。

## 后续 AI 编码指引

新增 API 时先确认业务实现应落在哪个业务模块；本模块只新增 Controller/DTO/协议转换。平台自身接口放 `web.platform`，agent 代理入口放 `web.agent`，横切入口支撑放 `web.common`。不得新增旧 `/api/...` runtime/workspace 入口；新 URL 必须同步记录到 `docs/api/http-api.md`。
