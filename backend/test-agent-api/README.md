# test-agent-api

管理审阅控制面由 TeamReviewController / TeamReviewProtocolService 适配：HTTP 仅创建 scope 与 ticket，跨 Java 复用公共路由/转发，目录正文走平台文件 WebSocket。协调与来源 RPC 都复核当前权限；Tool ticket 必须使用专用 audience 并绑定活跃 Run，scope 通道不放行通用 workspace/Git/终端操作。

Agent 配置权限补充：公共 Git 的管理、暂存、提交和发布仍仅允许 `SUPER_ADMIN`；公共 `diff/discard` 仅允许已登录用户操作本人公共个人 worktree。应用级 Agent/Skill 的暂存、提交和发布仍由 `APP_ADMIN`（含 `SUPER_ADMIN`）执行，普通成员仅可回退本人个人 worktree 的应用 Agent 本地改动。

外部调用使用独立 `/api/external/v1/**` 命名空间：`ExternalApiKeyWebFilter` 强制校验工具编码/API Key 并建立外部主体，`ExternalUserSshKeyController` 只返回 TAEK1 信封；管理端 `ExternalApiCredentialController` 仅允许 `SUPER_ADMIN`。旧用户 JWT 与静态 API Token 都不能旁路外部认证，API 日志把 actor 记为 `external:{toolCode}` 并脱敏 API Key、私钥和密文。

## 工程定位

后端 HTTP/SSE/WebSocket API 定义模块，只做协议入口、请求响应 DTO、统一响应、错误、traceId、鉴权、限流和受控 WebSocket 适配。

系统管理员团队入口由 `SystemAdminTeamController` 与 `TeamWorkspaceController` 提供；HTTP 每次读取实时角色与团队关系，个人 worktree 请求使用 `BackendJavaRouteResolver` / `BackendHttpForwarder` 定位权威 Java。`TEAM_READ_ONLY` 文件 ticket 复用平台文件 WebSocket 且逐 RPC 复核授权，只开放目录、搜索、预览、分块读取和 diff。整组导出控制面跨 Java 走公共 HTTP 转发，过滤 shard 通过专用一次性 WebSocket 传输，浏览器使用协调节点的一次性路由原生下载；内部 shard 与撤权清理入口仅按精确路径放行给 XXL Token/单次 ticket 专用鉴权。

## 主要职责

- `LocalClientVersionManagementController` 的基址为 `/api/internal/platform/local-opencode-client/version-management`，
  仅暴露受签名 release 同步/查询、全局与用户策略、rollout 与 attempt 查询；每个方法都实时调用
  `AuthWebSupport.requireRole(..., SUPER_ADMIN)`。`LocalClientUpdateController` 是普通用户的独立入口，只接受
  `notificationId/expectedTargetVersion` 和 path 中的实例 ID，业务层再校验通知、owner、在线 generation 与策略；
  不接收制品 URL、签名、策略 revision 或用户 ID。
- `LocalClientAuthenticationRateLimiter` 只用于 WSS 首帧注册认证。它按可信代理解析后的来源 IP，在当前 Java
  内存中执行默认每分钟 5 次固定窗口；成功认证清除该窗口。多 Java 部署不会共享该额度，生产级全局限流必须由
  Redis 或可信网关补齐。所有认证失败保持同一安全消息，不泄露 Key、统一认证号或账号状态。
- `LocalClientConnectionWebSocketHandler` 仅允许持久化注册能力为 `SELF_UPDATE_V1` 的连接收发版本帧。终态
  `UPDATE_STATUS` 由协调器完成关系库幂等提交后才返回 `UPDATE_STATUS_ACK`；ACK 绑定原命令的实例、generation
  和数据库实际终态，不能由一次 WebSocket 写入代替持久化确认；冲突终态返回错误且不发 ACK。版本、更新和公共能力
  数据库通知使用每连接 64 项有界串行队列，`FILE_RESPONSE/LIFECYCLE_RESULT` 等 requestId 回包直接完成 pending request；
  Redis 心跳独立刷新 TTL，三者都不占住 WebSocket 入站 `concatMap`。
- `LocalClientConnectionWebSocketHandler` 完成认证注册时由 runtime 在同一用户行锁内发布新 route，并通过
  `LocalClientConnectionRevoker` 删除其它实例 route/grant、跨 Java 关闭物理连接，保证每用户至多一个实时本地实例。
  `REGISTERED` 入队后异步调用 runtime，优先恢复全局最近项并逐个验真该用户全部历史本地工作区；单个目录校验失败只记录
  可观察告警并继续恢复其它目录，不把历史数据库路径直接视为可信根目录，也不要求页面通过重复选择才能初始化客户端根映射。
  声明 `MANAGED_MODEL_CONFIG_V1` 的企业客户端由 runtime 从公共 `opencode.jsonc` 生成受管配置，API 只负责写入
  `REGISTERED`，不在 handler 中拼接供应商或模型。

- 当前用户 OpenCode 受管启动/重启会在公共启动程序中自动选择同服有效公共个人配置；初始化首次创建 `public-{userId}` worktree 后也会自动加载。API 只返回既有 `publicWorktreePreparation` 结果，不新增轮询接口；准备或加载异常不回滚已健康进程。
- `LOCAL_CLIENT` 的公共 Agent 文件 route/ticket 在请求中使用 `LOCAL_CLIENT_PERSONAL:{clientInstanceId}:{connectionGeneration}` 逻辑引用，后端通过 `BackendJavaRouteResolver` 定位持有连接的 Java；客户端 capability 必须包含 `PUBLIC_CAPABILITY_PERSONAL_EDIT_V1`。每条 `agent-config.*` RPC 重新核对登录用户、在线代次和 capability，浏览器永远不能提交绝对根路径。
- 本地个人公共能力不进入公共 Git、worktree 或发布接口；公共 Git route、提交、推送、冲突和 rollout 操作在客户端模式隐藏且后端仍拒绝。工具代码以本机当前操作系统用户权限运行，日志只记录稳定错误码和 traceId。
- 暴露 `/api/internal/platform/...`、`/api/internal/agent/{agentId}/...` 和预留 `/api/public/...` URL。
- 工作空间列表和应用工作空间模板响应增量返回 `gitAccessStatus/gitAccessReason/gitAccessMessage/gitAccessCheckedAt`。API 只投影持久化巡检事实；没有结果时字段为 `null`，只有明确 `INACCESSIBLE` 由前端置灰，`UNKNOWN` 仍保持可选。为兼容旧客户端历史数据，本地工作区的 `INACCESSIBLE + NOT_GIT_REPOSITORY` 在响应时规范化为 `UNKNOWN`，普通目录不因缺少 Git 元数据而不可用。响应不包含仓库 URL、本地路径、Git 命令或 stderr。
- 旧 runtime/workspace `/api/...` 兼容 URL 由 `LegacyApiGoneWebFilter` 在进入 Controller 前统一返回 `410 API_GONE`；登录认证 `/api/auth/login|login-by-unified-auth|logout|me|refresh` 保留为稳定入口。只有密码登录和 AAM 兑换是精确匿名路径，静态 API Token 兼容边界不扩大；AAM 兑换在 `boundedElastic` 执行阻塞式外部验真和用户仓储编排，只返回平台 Token，不暴露上游地址、响应或错误正文。
- `web.platform` 承载平台自身接口，`web.agent` 承载 agent runtime 代理入口，`web.common` 承载 traceId、鉴权、限流、旧接口作废拦截和统一异常等入口支撑。
- CORS allowed headers 包含前端可选的 `X-Test-Agent-Linux-Server-Id`、会话协作专用 `X-Test-Agent-Session-Share` 和排查专用 `X-Support-Access-Grant`。分享头只解析单个 Session/Workspace 的代操作上下文，不替换真实 `AuthPrincipal`；跨 Java HTTP/SSE 转发保留该头，目标 Java 必须重新鉴权。Linux Server 首跳提示在生产由 Nginx 静态白名单消费并在转发前删除；排查头只绑定限时排查授权，不参与用户路由。API 层仍执行既有权威路由与鉴权。
- 会话消息、Run、夜间任务与重发 DTO additive 返回实际 actor 的用户 ID、可选当前姓名、统一认证号快照和代操作标记；同一响应页按用户 ID 缓存姓名查询，旧节点或目录无法解析时姓名保持可空。
- 会话协作入口提供候选用户、所属人唯一分享设置、“分享给我”历史列表、分享访问上下文和单会话 runtime SSE；运行态帧包含可选 `sessionUpdatedAt` 内容修订时间与不含正文的 `messageChange(sessionId/sourceRunId/replacementRunId/changeType/revision)`。compact 或撤回重发推进修订后，后端已提交的会话消息变化会直接唤醒 SSE，周期检查仅作为鉴权与恢复兜底；重发预约事务提交后即广播替代 USER 的修订，模型执行不参与消息同步时序，取消或失败也会用 `RESEND_RESTORED` 通知按源 Run 恢复完整轮次。普通与分享鉴权均可使用精确 Run USER/完整轮次读取接口，避免扫描历史分页。所属人管理接口不接受分享头授权；分享工作台中的会话、Run、定时任务、文件、Git、终端和反馈入口统一显式传递 `DelegatedOperationContext`，以所属人的进程和工作区执行并记录实际 actor。只读成员只能读取，`canChat=true` 才能写入；撤回并重新发送额外要求 actor 是源消息实际发送人，会话所属人只有在本人就是实际发送人时才能操作。归档、置顶、切换、持久 fork、设置、Agent 配置、源码/Hub、系统管理和服务器终端不会因分享放开。
- `UserNotificationController` 暴露当前用户通知分页、幂等已读和 fetch SSE。入口只从认证主体取得接收人，不接受客户端指定用户；DTO 只返回受控 `actionType/actionTargetId` 和安全标题/摘要，不返回去重键、数据库行 ID 或任意跳转 URL。`NONE` 通知仍可未读但不携带外部动作，dispose 失败只开放当前用户进程重启；SSE 首帧为 `user-notification.snapshot`，后续变化为 `user-notification.updated`，每 25 秒发送 heartbeat comment。
- 普通 Workspace HTTP 入口只保留查询和文件路由；服务器目录选择与创建仅通过超级管理员文件 WebSocket ticket 执行。
- `ExperienceWorkspaceController` 暴露无请求体的 `POST /api/internal/platform/workspace-management/workspaces/experience/open`。入口对任意已登录用户永久开放，只从认证用户的 READY TestAgent 进程取得目标服务器，沿用用户进程路由过滤器和公共 Java 转发链，不接受客户端路径、服务器或 Workspace ID；目标 Java 再委托 workspace-management 校验本服务器身份和当前绑定。体验响应固定返回 `workspace:{workspaceId}` 逻辑根并省略 `physicalRootPath`。
- 普通、最近与排查 Workspace 响应统一隐藏物理根目录：兼容字段 `rootPath` 固定返回 `workspace:{workspaceId}`，`physicalRootPath` 固定为空。文件列表、读写和需求导入继续只凭 `workspaceId` 使用目标后端文件 WebSocket；需求导入页通过 `workspace.requirement-import-items` 读取父子目录及“已导入/未导入”状态，复用逐 RPC 鉴权且不返回物理路径。导入结果可携带后端规范化的 `spec/{父条目}` 相对展示路径，只用于前端有限刷新和展开。仅用户主动复制单个普通文件路径时，才允许调用 `workspace.resolve-physical-path` 即时解析，分享、排查、体验空间和源码快照均不得使用该操作。
- `SupportAccessController` 暴露 `/api/internal/platform/system-management/support-access/**`：签发/撤销绑定当前登录会话的限时只读 grant、返回带来源的排查单号建议、目标切换、目标用户会话/工作区/消息读取、权威文件 route/ticket 和一年期审计查询。当前没有权威工单数据源时每次返回新的 `sai_` 单号；旧 `/grants/recent-incident` 仅为兼容别名，同样不读取历史授权。会话默认仅 ACTIVE，显式筛选才包含 ARCHIVED；工作区列表支持可选名称或 Workspace ID 模糊搜索，响应用公共 `BackendJavaRouteResolver` 标记 `ONLINE/OFFLINE/UNBOUND/UNKNOWN`，但不改写服务器归属。排查会话读取只在其权威工作区 Java ONLINE 时尝试 OpenCode，离线/未绑定/未知时直接使用 Redis/数据库历史，避免等待不可达远端。普通 Workspace/Session/文件入口已按当前用户归属收紧；排查文件 RPC 只允许列表/搜索/读取并拒绝 `.opencode`，成功正文在审计落库后才返回。
- 工作区文件下载新增 `workspace.read.binary.chunk` 与 `workspace.view.read.binary.chunk`：每段仍在目标 Java 重新校验 ticket、成员关系、路径或逻辑 locator，并透传文件大小/修改时间快照；API 层不解码完整文件，也不新增后端到后端 HTTP 文件代理。
- 自动化代码库使用应用级 `/applications/{appId}/automation-reference-repositories` API：列表、状态、分支和目录允许应用成员只读访问，配置、更新副本、核验和终止只允许 `APP_ADMIN/SUPER_ADMIN`。配置入口以 `(appId, repositoryId)` 为唯一维度并接收 `branch/directoryPath/description/merge=false/expectedGeneration/operationId`；响应只返回逻辑代次路径、固定提交和逐服务器状态，不返回物理路径。旧 `workspace-template/version` API 对自动化代码库固定拒绝。文件 WebSocket 接受携带应用、版本库、配置代次、逻辑相对路径和可选服务端只读标签租约的 `AUTOMATION_ROOT/AUTOMATION_REFERENCE` 定位器，并在每次 RPC 重新校验成员、代次、租约绑定和本机副本；非自动化 locator 禁止携带该租约。
- Agent 配置文件 WebSocket 新增 `agent-config.automation-reference.reconcile`：只接受绑定当前 `WORKSPACE`、且未绑定独立 Agent worktree 的 ticket，应用普通成员即可调用。Handler 在目标 Java 重新确认工作区服务器归属后调用后端唯一自动化 JSONC 对账器，响应仅为 `{ changed, warnings }`；客户端不提交应用、版本库、generation、逻辑路径或 JSONC 正文。
- 暴露应用版本工作区和个人工作区运行接口；“拉取远程”调用个人工作区 `git-pull`，更新当前 owner 在该应用下的整棵 worktree（含应用 Agent），并返回 `runtimeReloadStatus/runtimeReloadId` 表达当前用户后台运行态重载是否已登记；版本级 `git-pull` 兼容入口固定拒绝，避免旧客户端触发共享版本或全员同步。Controller 只解析登录主体、traceId、当前用户 opencode agent 服务器并委托 workspace-management；应用成员、owner 和目录权限仍由业务服务校验。个人工作区新建和 default 显式确保/修复在进入业务服务前统一调用 `requireReadyProcess`，未形成 ACTIVE binding 或进程不健康时返回 `OPENCODE_UNAVAILABLE`，不得按入口 Java 本机目录落盘。
- 暴露应用源码仓库列表、分支、远端树、物化、当前 generation 保留期调整、同 generation 副本重试、打开、最近选择，以及持久化操作快照查询；同一 tree URL 默认继续返回节点数组，只有显式 `includeCommit=true` 才返回 `{targetCommit,nodes}`，其中提交和节点来自 workspace-management 的同一次固定提交树快照。仓库列表状态严格使用 `NOT_DOWNLOADED/DOWNLOADED_ACTIVE/DOWNLOADED_EXPIRED/PERSONAL_OCCUPIED`，additive 返回 `acceptedAt/maxRetentionHours`，并按当前用户 READY opencode 进程所在服务器计算是否可打开。Controller 只传递认证主体、`APP_ADMIN` 事实、当前进程服务器和 traceId，业务鉴权、固定 commit、generation、成员关系与安全步骤摘要全部委托 workspace-management。
- 应用源码进度使用独立 WebSocket `app-source-operations/{operationId}/ws`，不复用 RunEvent。HTTP GET、ticket 签发、ticket 消费和后续轮询都按 operation 的 repository 重新检查：TEAM 允许任一当前启用关联应用的有效成员，PERSONAL 仅 owner 或仍满足该成员条件的 `APP_ADMIN`；解除关联、禁用应用或撤销成员立即拒绝。operationId 复用领域值对象，以显式 ECMAScript WhiteSpace + LineTerminator 集合规范化并拒绝精确 `.`/`..` 路径段，保证 NBSP 等输入与前端一致。ticket 短期、并发一次性、有界存储并绑定用户、操作、签发 JVM、精确 canonical Origin、角色事实和 traceId，错误 Origin 不消费正确来源的票。upgrade 同时校验 Origin 白名单和签票 Origin；仅当 CORS 配置恰好为单个 `*` 时与全局 CORS 一致接受任意格式合法的 canonical Origin，ticket 仍绑定实际来源。连接首帧必为数据库快照，随后只轮询数据库中的固定安全 step/server 摘要并发送既有 `step/completed/failed` 判别联合；本轮只改变授权与步骤内容，不改变 API/WebSocket wire。重连必须重新签票并重新取得快照，关闭连接只停止观察，不取消后台物化。
- 工作区 Git 入口包含 diff/discard、真实 stage/unstage、三方冲突读取、单文件解决和取消/完成 merge。个人 worktree 继续支持本地提交和 feature 发布；体验 Workspace 另由 `POST /workspaces/{workspaceId}/git-commit` 只建立本服务器提交，不接受 personal workspace ID，也没有 push/发布入口。应用 `.opencode/**` 与普通文件共用个人 worktree，因此 `ManagedWorkspaceController` 在个人 commit/publish 入口对该目录再次校验 `APP_ADMIN`（含 `SUPER_ADMIN`）；体验 commit 则复用普通路径权限并由业务层再次确认 `wrk_exp_` 当前绑定。个人工作区提交/发布 DTO 透传可选 `operationId`，体验提交 DTO 仅含 `commitMessage/files`。Agent 配置进度 WebSocket 与其它平台 WebSocket 共用 CORS Origin 配置；本地 test profile 显式使用 `*` 时仍要求一次性 ticket，精确白名单模式继续在消费 ticket 前拒绝非法来源。
- `WorkspaceGitToolController` 为公共 OpenCode `workspace-git` Tool 提供 agent-scoped 专用入口；只校验 runtime 签发的窄权限凭据和映射请求 DTO，当前 workspace、owner、路径角色与 Git 动作委托 workspace-management，不接受 Tool 指定 workspace ID。该精确路径由通用 API Token 过滤器放行后在 Controller 内完成专用鉴权，仍受统一限流、traceId 和异常响应约束。
- `CodeKnowledgeToolController` 为公共 OpenCode `code_knowledge` 与 `code_source` Tool 提供两个独立只读入口。Controller
  只接受 Mimo 逻辑版本库 ID、相对路径、查询条件和有界预算；TraceWeave 地址、应用组、图仓库、Workspace ID、
  服务器和物理根均由服务端解析。两个精确路径仅跳过通用用户 Token 过滤，随后必须通过
  `code-knowledge-read` 专用凭据；相邻路径仍按通用鉴权处理。源码调用直达 OpenCode 所在同节点 Java，复用
  workspace-management 的 APP_SOURCE 权威鉴权和安全文件内核，不建立跨 Java 文件 HTTP 代理。图谱适配器内部的
  Jackson 2 树在 HTTP 边界转换为普通对象，确保 Spring Boot 4/Jackson 3 codec 输出真实图谱字段，而非树节点类型标志。
- `CodeKnowledgeScopeController` 为登录态工作台返回当前试点用户可选择的 Mimo 逻辑版本库 ID。前端再与当前应用
  的源码仓库权限取交集；响应不包含 TraceWeave 地址、应用组或图仓库映射，停用和非试点作为不可用状态返回。
- `UiTestToolConfigController` 为 `ui_test_execute` 提供无凭据、窄字段只读入口，只返回当前 `configured/baseUrl` 并把数据库读取调度到 `boundedElastic`；不代理独立 UI 平台请求。OpenCode worker 通过同节点 Java 内网地址调用，公共 Nginx 对该精确路径返回 404。
- 暴露配置管理接口，Controller 只委托 configuration-management 业务服务；新建应用只允许 `SUPER_ADMIN`，应用成员、版本库和工作区管理校验 `APP_ADMIN` 且 `SUPER_ADMIN` 继承该能力。版本库分页列表可选透传 `keyword`，按版本库 ID、中文名、英文名和 Git 地址做服务端模糊检索；版本库类型下拉增量返回 `AUTOMATION_CODE_REPOSITORY`，其它部署模式、`repositoryType`、远端树和工作空间 DTO 仍只做协议转换，旧 `standard` 兼容派生、版本库类型历史守卫、内部模式 SSH 前缀、远端树过滤和别名唯一校验由业务服务处理。工作空间 PATCH DTO 可选透传 `workspaceName/enabled`，至少需要一个字段。设置页保存应用工作空间接口会读取当前用户 READY opencode 进程的 Linux 服务器并委托 workspace-management 创建初始版本工作区，进度通过 `workspace-create-operations/{operationId}` HTTP 轮询查询；分支和远端树加载接口不触发 clone。
- Controller 只调用业务模块 service，不直接访问 Repository、generated SDK 或 JDBC 实现。
- `ToolboxController` 暴露登录用户目录 `GET /api/internal/platform/toolbox/tools` 和点击 `POST /api/internal/platform/toolbox/tools/{toolId}/clicks`；不校验角色，客户端只提供 `eventId`，用户、服务端时间和 traceId 由入口取得后委托 `test-agent-integration`。工具静态页面不经过本 Controller。
- `TcdsCaseMaintenanceController` 暴露登录用户任务类型查询 `GET /api/internal/platform/integration/tcds/task-types` 和案例维护 `POST /api/internal/platform/integration/tcds/test-cases`。查询只返回 TCDS `subItemTypes` 中校验后的 `name/value`；维护请求只提交需求子条目和 Markdown 案例，每个案例的多个任务类型使用实时 `name` 删除末尾“测试任务”后的业务名称并以英文逗号连接。Controller 使用认证主体统一认证号并把两类阻塞内网调用调度到 `boundedElastic`，实时类型校验、统一部署基础地址、全部请求的固定 `toolId` 和上游业务字段委托 `test-agent-integration`。请求 DTO 使用 `ApiRequestLogSummary` 只向访问日志暴露 `itemNo/caseCount`，四列正文不落日志。
- 维护 `RuntimeDtos` 等平台 DTO，不返回 generated SDK DTO；Session、SessionMessage、Run 可选返回 `sourceType/sourceRefId`，用于区分夜间定时执行来源。
- `RuntimeDtos` 的 Session-tree 事件映射保留每个恢复事件的原始 `traceId`，供授权排查页关联日志；字段为响应增量，不改变既有 reducer `payload`。
- runtime Controller 只读取可选认证主体并传入 `test-agent-opencode-runtime`，有用户主体时由业务层使用用户专属 opencode 进程，无用户主体时保持 static-token 兼容 fallback。
- `POST /api/internal/agent/{agentId}/sessions/{sessionId}/run-context` 读取必需认证主体、agentId、sessionId、traceId 和可选分享头，委托 runtime 在签发 fence 内从权威数据构造会话运行上下文；分享请求先以真实 actor 校验有效成员、`canChat` 和精确 Session，再显式使用 `executionOwnerUserId` 签发所属人的进程上下文，不把 actor 改写为所属人。普通托管 Workspace 会实时校验应用启用、有效成员及个人 Workspace owner，`SUPER_ADMIN` 不旁路成员规则。响应只返回 `contextToken/contextVersion/expiresAt`。Run 请求 DTO 可选接收 `contextToken/clientRequestId`，不接受客户端传入可信工作区路径、进程、节点或服务器快照。有效 token 由 runtime resolver 复用完整服务端快照，并通过公共 `querySnapshot` 动态健康探测；已有远端 session 时，Session、Workspace、进程、ExecutionNode 和 binding 均为 0 次 Repository SELECT。稳定 `RUNNING` 为 0 次数据库写入，只有稳定状态、PID 或服务地址变化时写一次；`STALE` 拒绝当前 Run 但保留 token，`NOT_STARTED` 才失效进程上下文。
- `DELETE /api/internal/platform/opencode-runtime/sessions/{sessionId}` 把当前认证用户传入 Session 归档服务，并在归档写库前建立 Redis revoke gate；数据库失败只回滚本次撤销 token，并发归档 gate 不受影响。Workspace root/server 变化、可信路径参数重载、成员和全局角色撤权也已分别接入 Workspace、全局或用户维度失效。
- `POST /api/internal/agent/{agentId}/processes/me/restart` 接受 `confirmRunning`，活动 Run 未确认时返回权威冲突；确认后由 runtime 取消当前用户全部活动 Run 并复用公共停止/启动程序。已有 binding 跨 Java 请求继续由公共 resolver/forwarder 保留认证、traceId 和 body 路由，不新增本机旁路。
- 当前用户 opencode 进程接口包含 `/processes/me` 强状态查询、`/processes/me/message-gate` 轻量门禁、`/processes/me/health` 弱健康检查、初始化和初始化进度查询。未绑定用户只有精确的 `GET /processes/me` 与 `POST /processes/me/initialize` 会通过 `BackendJavaRouteResolver` 按 Redis 集群快照选择进程总数最少且可初始化的服务器；选中远端时复用 `BackendHttpForwarder` 单次转发，目标 Java 继续使用本地候选、原子端口预留和公共启动流程，已有 ACTIVE binding 不参与重新选服。`SUPER_ADMIN` 初始化得到 `READY` 后，目标 Java 复用 workspace-management 幂等准备同服公共个人 worktree，并以可选 `publicWorktreePreparation` 返回附加结果；准备失败不推翻已通过健康检查的进程状态。`/processes/me` 会附加公共 Agent/Skill 发布闸门的 `messageSendAllowed/messageSendBlockedReason/publicConfigRolloutId`，轻量门禁供已打开页面固定轮询且留在当前 Java 读取共享 rollout，不按用户 binding 路由，也不探测 manager/opencode；闸门按当前登录用户查询，其旧 opencode target dispose 后立即恢复。后端 Run、旁路问答以及 legacy command/shell 入口仍执行同一用户级闸门。弱健康检查只做协议适配，业务逻辑委托 `OpencodeProcessStatusQueryService.weakHealth`，跨 Java 路由由专用过滤器按 query 中的 `linuxServerId` 和 Redis 后端快照处理，不读取用户 binding 数据库；初始化接口支持可选 `operationId` 请求体，`initialize-operations/{operationId}` 只读查询不触发 manager health/start 或 RunEvent。
- RunEvent SSE 建连前由 `RunEventSseBackendRoutingWebFilter` 按 Run 原始归属定位生产 Java，目标不是当前 Java 时使用 `BackendSseForwarder` 流式转发 `text/event-stream` 并保留 Authorization、trace、Last-Event-ID 和 query；SSE 路由不可用时仍允许本机只读恢复。两个 Run cancel 入口、Run Diff 读取/接受/拒绝及 Run 级 `session-tree/messages` 由 `RunControlBackendRoutingWebFilter` 使用同一生产端解析和普通 `BackendHttpForwarder` 严格转发；每一跳都重新执行 strict owner 解析，不信任浏览器可伪造的 `X-Test-Agent-Backend-Routed`，到达当前被选中的生产 Java 后解析器自然放行。路由归属缺失、目标后端不可用或转发失败时直接写统一平台错误，禁止落到当前 Java 访问错误运行态或执行副作用；纯数据库 Run 详情仍可本机读取。目标 Java 的 `RunController` 在取消、Diff、SSE 和 Run 级 session-tree 远端读取或副作用前统一校验认证用户归属与当前 Workspace 权限；体验 Run 的 SSE 建连后还会每秒独立复核资格，撤权后即使没有新事件也主动断开。两个 agent-scoped `session-tree/messages` HTTP 历史入口统一按 Redis 详情 → OpenCode 完整会话 → PostgreSQL 双摘要恢复，Session 入口无摘要时再从旧 `session_messages` 恢复正文，并返回可选 `historyRepresentation/replayAvailable/detailsAvailableUntil`；Redis、摘要和旧正文来源不查询旧 `run_events`，只有 legacy OpenCode 来源补充 durable 状态。旧 `/api/runs/...` 与 `/api/sessions/...` 已作废。
- 暴露 Workspace/Agent 配置文件 WebSocket 路由、ticket 和 WebSocket RPC 入口：Controller/Handler 只做鉴权、`APP_ADMIN`/`SUPER_ADMIN` 权限校验、ticket、Origin、traceId、协议 envelope 和统一错误包装，工作区与 Agent 配置复制/移动的源路径与目标路径分别鉴权，文件系统操作继续委托 `test-agent-workspace-management`。工作台文件树通过 `workspace.view.list/read/read.chunk` 读取工作区与引用资产的组合视图，原始 `workspace.list/read/write` 保持兼容；超过一次性读取阈值的工作区和 Agent UTF-8 文件分别通过 `workspace.read.chunk` / `agent-config.read.chunk` 渐进只读预览到 EOF，每段重新校验 ticket、成员关系、scope/worktree 或 locator 及文件快照。路由、ticket 与每条 workspace RPC 都重新校验当前应用成员关系，非托管 Workspace 只对 `SUPER_ADMIN` 的服务器工作空间兼容入口开放，locator 不能替代业务层路径安全校验；体验 Workspace 的普通文件 ticket 继续实时校验资格与当前绑定，根列表和搜索过滤 `.git`/`.opencode`，任意读写、复制、移动、改名或状态路径命中这两个受控命名空间时即使 `APP_ADMIN`/`SUPER_ADMIN` 也固定拒绝，应用引用组合视图与 `agent-config/WORKSPACE` ticket 同样返回 `FORBIDDEN`。新上传通过同一连接上的 `upload.begin/chunk/complete/abort` 有界分片会话完成，不限制应用层总大小；WebSocket 单帧只需覆盖默认 5 MiB 一次性读写、约 512 KiB 预览分段或一个默认 256 KiB Base64 上传分片及 RPC envelope。普通成员的应用版本副本只读，个人 worktree 普通文件可写；公共/应用 Agent 文件上传、改名、复制、移动、删除复用既有文件服务，公共 scope 要求 `SUPER_ADMIN`，应用完整 `.opencode/**` 命名空间要求 `APP_ADMIN`（`SUPER_ADMIN` 继承），普通用户在 handler 层返回 `FORBIDDEN`。Workspace 路由优先使用用户进程服务器归属；Agent 配置文件路由按 scope/workspace/worktree 的服务器归属定位目标后端，不新增跨服务器 HTTP 文件代理。
- 暴露 Agent 配置管理 HTTP 和进度 WebSocket 入口：Controller 只做认证、角色校验、目标服务器路由、DTO 和 traceId 转换；公共 Git 仍仅 `SUPER_ADMIN`，应用级 Agent/Skill Git 由 `APP_ADMIN`（含 `SUPER_ADMIN`）操作。公共全局刷新先聚合所有服务器只读仓库状态；共享运行副本 dirty 且请求未明确确认时，在调用业务更新和修改任何工作树前返回 `CONFLICT`。`GET /public/rollout` 返回最近一次全局任务的逐服务器同步、排空、公共个人 worktree 补偿计数、`lastError`，以及每台服务器最多 200 个未排空目标的内部用户和进程诊断明细，供前端统一禁用重复按钮、定位阻塞用户并轮询；停止动作继续走既有运行管理命令入口。公共与应用 Agent 均提供定点 discard；冲突文件仍必须通过既有合并接口解决。公共个人保存后的 `runtime-reload` 会同步等待本人 OpenCode dispose，因此本地业务调用和跨服务器转发都必须调度到 `boundedElastic`，不得在 WebFlux 事件线程直接等待。个人/应用工作区 Git 接口通过统一用户 binding 路由到目标 Java，进度 WebSocket 使用一次性 ticket、Origin 白名单和 `snapshot/step/completed/failed` envelope；ticket 响应使用当前 Java 身份生成绝对 WebSocket URL，确保多后台 upgrade 回到签发 JVM。
- 暴露 Workspace/Agent 配置文件 WebSocket 路由、ticket 和 WebSocket RPC 入口：Controller/Handler 只做鉴权、`APP_ADMIN`/`SUPER_ADMIN` 权限校验、ticket、Origin、traceId、协议 envelope 和统一错误包装，工作区与 Agent 配置复制/移动的源路径与目标路径分别鉴权，文件系统操作继续委托 `test-agent-workspace-management`。工作台文件树通过 `workspace.view.list/read/read.chunk` 读取工作区与引用资产的组合视图，原始 `workspace.list/read/write` 保持兼容；超过一次性读取阈值的工作区和 Agent UTF-8 文件分别通过 `workspace.read.chunk` / `agent-config.read.chunk` 渐进只读预览到 EOF，每段重新校验 ticket、成员关系、scope/worktree 或 locator 及文件快照。路由、ticket 与每条 workspace RPC 都重新校验当前应用成员关系，非托管 Workspace 只对 `SUPER_ADMIN` 的服务器工作空间兼容入口开放，locator 不能替代业务层路径安全校验。应用源码 Runtime Workspace 必须精确路由到当前 generation 副本所在服务器，历史 Workspace 信息不得触发本机回绑或本机降级；跨 Java HTTP 入口仍复用公共 resolver/forwarder，文件内容不经过后端到后端 HTTP 代理。新上传通过同一连接上的 `upload.begin/chunk/complete/abort` 有界分片会话完成，不限制应用层总大小；WebSocket 单帧只需覆盖默认 5 MiB 一次性读写、约 512 KiB 预览分段或一个默认 256 KiB Base64 上传分片及 RPC envelope。普通成员的应用版本副本只读，个人 worktree 普通文件可写；公共/应用 Agent 文件上传、改名、复制、移动、删除复用既有文件服务，公共 scope 要求 `SUPER_ADMIN`，应用完整 `.opencode/**` 命名空间要求 `APP_ADMIN`（`SUPER_ADMIN` 继承），普通用户在 handler 层返回 `FORBIDDEN`。Workspace 路由优先使用用户进程服务器归属；Agent 配置文件路由按 scope/workspace/worktree 的服务器归属定位目标后端，不新增跨服务器 HTTP 文件代理。
- 暴露 Workspace/Agent 配置文件 WebSocket 路由、ticket 和 WebSocket RPC 入口：Controller/Handler 只做鉴权、`APP_ADMIN`/`SUPER_ADMIN` 权限校验、ticket、Origin、traceId、协议 envelope 和统一错误包装，工作区与 Agent 配置复制/移动的源路径与目标路径分别鉴权，文件系统操作继续委托 `test-agent-workspace-management`。工作台文件树通过 `workspace.view.list/read/read.chunk` 读取工作区与引用资产的组合视图，原始 `workspace.list/read/write` 保持兼容；超过一次性读取阈值的工作区和 Agent UTF-8 文件分别通过 `workspace.read.chunk` / `agent-config.read.chunk` 渐进只读预览到 EOF，每段重新校验 ticket、成员关系、scope/worktree 或 locator 及文件快照。路由、ticket 与每条 workspace RPC 都重新校验当前应用成员关系；应用源码 Runtime Workspace 还会交叉校验 Workspace 行、用户当前 agent server 与当前 generation 的 READY replica server，任一不一致都失败关闭。非托管 Workspace 只对 `SUPER_ADMIN` 的服务器工作空间兼容入口开放，locator 不能替代业务层路径安全校验。应用源码 Runtime Workspace 必须精确路由到当前 generation 副本所在服务器，历史 Workspace 信息不得触发本机回绑或本机降级；跨 Java HTTP 入口仍复用公共 resolver/forwarder，文件内容不经过后端到后端 HTTP 代理。新上传通过同一连接上的 `upload.begin/chunk/complete/abort` 有界分片会话完成，不限制应用层总大小；WebSocket 单帧只需覆盖默认 5 MiB 一次性读写、约 512 KiB 预览分段或一个默认 256 KiB Base64 上传分片及 RPC envelope。普通成员的应用版本副本只读，个人 worktree 普通文件可写；公共/应用 Agent 文件上传、改名、复制、移动、删除复用既有文件服务，公共 scope 要求 `SUPER_ADMIN`，应用完整 `.opencode/**` 命名空间要求 `APP_ADMIN`（`SUPER_ADMIN` 继承），普通用户在 handler 层返回 `FORBIDDEN`。Workspace 路由优先使用用户进程服务器归属；Agent 配置文件路由按 scope/workspace/worktree 的服务器归属定位目标后端，不新增跨服务器 HTTP 文件代理。
- 暴露 Workspace/Agent 配置文件 WebSocket 路由、ticket 和 WebSocket RPC 入口：Controller/Handler 只做鉴权、`APP_ADMIN`/`SUPER_ADMIN` 权限校验、ticket、Origin、traceId、协议 envelope 和统一错误包装，工作区与 Agent 配置复制/移动的源路径与目标路径分别鉴权，文件系统操作继续委托 `test-agent-workspace-management`。工作台文件树通过 `workspace.view.list/read/read.chunk` 读取工作区与引用资产的组合视图，原始 `workspace.list/read/write` 保持兼容；超过一次性读取阈值的工作区和 Agent UTF-8 文件分别通过 `workspace.read.chunk` / `agent-config.read.chunk` 渐进只读预览到 EOF，每段重新校验 ticket、成员关系、scope/worktree 或 locator 及文件快照。路由、ticket 与每条 workspace RPC 都重新校验当前应用成员关系；每条 `workspace.*` RPC 还重新读取当前用户 `opencode` 文件路由 affinity，并要求 affinity、ticket 目标/agent 服务器、Workspace/托管副本服务器和当前 JVM 完全一致，连接建立后的用户 binding 迁移会使旧 socket 的下一条 RPC 立即 `FORBIDDEN`，不会进入文件服务。应用源码 Runtime Workspace 继续复核当前 generation 的 READY replica。非托管 Workspace 只对 `SUPER_ADMIN` 的服务器工作空间兼容入口开放，locator 不能替代业务层路径安全校验。应用源码 Runtime Workspace 必须精确路由到当前 generation 副本所在服务器，历史 Workspace 信息不得触发本机回绑或本机降级；跨 Java HTTP 入口仍复用公共 resolver/forwarder，文件内容不经过后端到后端 HTTP 代理。新上传通过同一连接上的 `upload.begin/chunk/complete/abort` 有界分片会话完成，不限制应用层总大小；WebSocket 单帧只需覆盖默认 5 MiB 一次性读写、约 512 KiB 预览分段或一个默认 256 KiB Base64 上传分片及 RPC envelope。普通成员的应用版本副本只读，个人 worktree 普通文件可写；公共/应用 Agent 文件上传、改名、复制、移动、删除复用既有文件服务，公共 scope 要求 `SUPER_ADMIN`，应用完整 `.opencode/**` 命名空间要求 `APP_ADMIN`（`SUPER_ADMIN` 继承），普通用户在 handler 层返回 `FORBIDDEN`。Workspace 路由优先使用用户进程服务器归属；Agent 配置文件路由按 scope/workspace/worktree 的服务器归属定位目标后端，不新增跨服务器 HTTP 文件代理。
- 暴露 Workspace/Agent 配置文件 WebSocket 路由、ticket 和 WebSocket RPC 入口：Controller/Handler 只做鉴权、`APP_ADMIN`/`SUPER_ADMIN` 权限校验、ticket、Origin、traceId、协议 envelope 和统一错误包装，工作区与 Agent 配置复制/移动的源路径与目标路径分别鉴权，文件系统操作继续委托 `test-agent-workspace-management`。工作台文件树通过 `workspace.view.list/read/read.chunk` 读取工作区与引用资产的组合视图，原始 `workspace.list/read/write` 保持兼容；超过一次性读取阈值的工作区和 Agent UTF-8 文件分别通过 `workspace.read.chunk` / `agent-config.read.chunk` 渐进只读预览到 EOF，每段重新校验 ticket、成员关系、scope/worktree 或 locator 及文件快照。路由、ticket 与每条 workspace RPC 都重新校验当前应用成员关系；每条 `workspace.*` RPC 还重新读取当前用户 `opencode` 文件路由 affinity，并要求 affinity、ticket 目标/agent 服务器、Workspace/托管副本服务器和当前 JVM 完全一致，连接建立后的用户 binding 迁移会使旧 socket 的下一条 RPC 立即 `FORBIDDEN`，不会进入文件服务。签票授权在同一次权威判断中取得 `STANDARD/APP_SOURCE` 分类并写入不可伪造的 JVM 本地 ticket；APP_SOURCE 票的后续每条 RPC 都禁止 unmanaged 回退并要求再次识别为 APP_SOURCE，因此 replica 映射消失时 `SUPER_ADMIN` 也会在文件服务前失败。真正的非托管服务器工作区 ticket 仍保留 `SUPER_ADMIN` 兼容入口。应用源码 Runtime Workspace 必须精确路由到当前 generation 副本所在服务器，历史 Workspace 信息不得触发本机回绑或本机降级；跨 Java HTTP 入口仍复用公共 resolver/forwarder，文件内容不经过后端到后端 HTTP 代理。新上传通过同一连接上的 `upload.begin/chunk/complete/abort` 有界分片会话完成，不限制应用层总大小；WebSocket 单帧只需覆盖默认 5 MiB 一次性读写、约 512 KiB 预览分段或一个默认 256 KiB Base64 上传分片及 RPC envelope。普通成员的应用版本副本只读，个人 worktree 普通文件可写；公共/应用 Agent 文件上传、改名、复制、移动、删除复用既有文件服务，公共 scope 要求 `SUPER_ADMIN`，应用完整 `.opencode/**` 命名空间要求 `APP_ADMIN`（`SUPER_ADMIN` 继承），普通用户在 handler 层返回 `FORBIDDEN`。Workspace 路由优先使用用户进程服务器归属；Agent 配置文件路由按 scope/workspace/worktree 的服务器归属定位目标后端，不新增跨服务器 HTTP 文件代理。
- 个人工作区自动搬迁控制面只开放两个精确内部路径：HTTP ticket 入口使用标准 `XXL-JOB-ACCESS-TOKEN` 常量时间校验，专用 WebSocket 使用目标 JVM 内存中的 60 秒一次性 ticket、固定内部 Origin 和源服务器头；全局 CORS 只为该精确 WebSocket 路径允许内部 Origin，普通浏览器白名单和相邻路径不继承。`BackendJavaRouteResolver` 选择目标 Java，`BackendHttpForwarder` 只转发签票元数据；归档字节以 256 KiB 二进制帧直达同一目标 JVM，不经过 Java→Java HTTP 文件代理。响应只返回 ticket、到期时间、安全状态和提交摘要，不向普通用户暴露 workspaceId、物理路径或文件内容。
- 暴露 Agent 配置管理 HTTP 和进度 WebSocket 入口：Controller 只做认证、角色校验、目标服务器路由、DTO 和 traceId 转换；公共 Git 仍仅 `SUPER_ADMIN`，应用级 Agent/Skill Git 由 `APP_ADMIN`（含 `SUPER_ADMIN`）操作。公共与应用 Agent 均提供定点 discard；冲突文件仍必须通过既有合并接口解决。公共个人保存后的 `runtime-reload` 会同步等待本人 OpenCode dispose，因此本地业务调用和跨服务器转发都必须调度到 `boundedElastic`，不得在 WebFlux 事件线程直接等待。个人/应用工作区 Git 接口通过统一用户 binding 路由到目标 Java，进度 WebSocket 使用一次性 ticket、Origin 白名单和 `snapshot/step/completed/failed` envelope；ticket 响应使用当前 Java 身份生成绝对 WebSocket URL，确保多后台 upgrade 回到签发 JVM。
- 暴露 opencode-manager WebSocket 控制面入口，入口只做 manager token 鉴权、DTO/消息适配、完整运行配置已下发的连接级状态和 traceId 处理；同一连接的 health/start/restart/stop/stopOwned 等出站控制消息在连接级串行 emission，并检查 Reactor sink 结果，发送失败立即进入统一错误链路并取消 pending command，禁止静默等待 command timeout。manager 注册时 runtime 先冻结原 ACTIVE 运行进程候选，入口在完整 `configUpdate` 应用后的首个 `managerHeartbeat` 才把控制连接暴露给业务探测并异步执行恢复，避免空 manager 抢先把待恢复状态写成 `STOPPED`；配置缺失时只返回安全错误且不恢复。旧 manager-backends HTTP 诊断入口已作废，Go manager 运行路径不通过 HTTP 与 Java 交互，只连接本服务器 Java，`backendListRequest/backendListResponse` 仅保留为兼容诊断协议。
- 后端 Java 路由统一使用 runtime 的 `BackendJavaRouteResolver` 解析当前服务器、首次分配的全局最轻可初始化服务器、`linuxServerId -> BackendJavaProcess` 和 `containerId -> linuxServerId`；API 层普通 Java->Java HTTP 转发走 `BackendHttpForwarder`，RunEvent SSE 长连接走 `BackendSseForwarder` 流式转发，两者都设置 `X-Test-Agent-Backend-Routed` 防循环并透传 Authorization、traceId 和 query。本地客户端路由只把以 `ses_` / `wrk_` 开头的路径段识别为资源 ID，`sessions/batch-items`、`sessions/runtime-state`、`workspaces/experience/open` 等固定 action 继续按用户 binding 路由，不进入 ID 校验。两个 start-run 入口携带 `contextToken` 时，路由过滤器在 32 MiB 上限内缓存请求体并通过 Redis 只读解析 token 绑定的生产服务器，不查询用户进程 assignment，也不刷新 token TTL；字段已出现但为空、非字符串或失效时 fail-closed 返回 409，不回退 assignment。远端转发和本地 Controller 均可再次读取完整 body。无 token 的兼容请求仍走 assignment 路由。后续新增任何 opencode-manager 路由或 Java->manager 控制入口，都必须复用这套公共程序。
- `CommonParameterMemoryController` / `CommonParameterMemoryBackendRoutingService`：仅向 `SUPER_ADMIN` 提供显式 JVM 内存参数的全部/单进程查询与手工刷新。集群聚合最多 500 个在线 Java，按 `backendProcessId` 精确保留同服务器多进程，使用公共 resolver/forwarder、并发 8、单进程 10 秒超时和内部路由头防循环；部分失败返回 HTTP 200 逐进程结果，未知或离线单进程统一 503。API 层不读取 Repository、不写修改历史、不发布参数广播。
- `web.aop.ApiLoggingAspect` 按目标 Controller logger 记录前端 HTTP 操作入口、出口、耗时、状态和脱敏请求/响应摘要；`contextToken`、`ticketId/grantId/grant/encryptedPrivateKey`、内存参数 `sourceValue/memoryValue` 与 Authorization、Cookie 等敏感字段同样强制掩码，包含 JSON 转义字符时也不得残留原值。敏感字符串使用无回溯匹配，长加密私钥不得因日志脱敏栈溢出而中断业务请求。精确的 `OPENCODE_UNAVAILABLE + 请先初始化 TestAgent 进程` 属于用户可恢复前置条件，只在 API 边界记录一条无堆栈 WARN，Service 切面不重复记录；同错误码的健康失败、manager 不可用等其它异常仍保留 ERROR 堆栈。`web.aop.WebSocketLoggingAspect` 按目标 WebSocket handler logger 记录前端长连接入口、结束信号和异常；`web.aop.ServiceLoggingAspect` 按目标 Service logger 仅在抛出异常时记录方法、参数摘要、耗时和错误。三者统一进入 `logs/backend.log`，ERROR 级别同时进入 `logs/error.log`；SSE 相关 Controller/Service/logger 还会额外进入 `logs/sse.log`。
- 暴露超级管理员运行管理 overview、容器/按稳定服务器身份的后端指标历史和有主/无主 opencode server 重启/停止 API；旧后端进程指标入口已作废。Controller 只做 `SUPER_ADMIN` 鉴权、分页/筛选/历史/容器/端口参数校验、用户名筛选参数透传、manager 下属 opencode server 明细和 `BOUND/UNBOUND` 归属 DTO 映射、命令结果 DTO 映射、后端指标 DTO 映射和 traceId 处理；manager 明细新增可空 `unifiedAuthId/managerStatus`，旧 manager/旧 Redis 快照缺字段时保持 `null`。UCID 只通过该现有高权限接口返回，不进入普通用户接口、普通错误信息或日志。后端指标 DTO 按可空字段透传服务器 CPU/load/内存/swap/磁盘、Java 进程 CPU/RSS/FD、JVM heap/non-heap/direct/mapped/GC/线程字段，并保留旧别名 `memoryMaxBytes`、`jvmGcPauseMillis`。重启/停止命令先按 `containerId` 的 Redis manager 快照定位容器所属 `linuxServerId`，目标不是当前 Java 或同服务器选中 Java 时透传用户 JWT 和 traceId 转发到目标 Java，由目标 Java 控制本服务器 manager。API 层不实现 opencode server 启动、停止、状态查询或健康确认；用户进程初始化、STOPPED 进程重启和 manager 明确未托管后的原端口拉起由 `test-agent-opencode-runtime` 的 `OpencodeProcessStartupService` 完成，平台已有进程记录的停止确认和 `STOPPED` 回写由 `OpencodeProcessStopService` 完成，状态查询、健康探测和 heartbeat 刷新由 `OpencodeProcessStatusQueryService` 完成。指标历史主参数为 `windowMinutes`，`hours` 仅兼容旧客户端。
- 暴露超级管理员 XXL 一次性 SSO 票据 API，Controller 只做 `SUPER_ADMIN` 鉴权和 traceId；旧 scheduler-management 任意子路径统一返回 `410 API_GONE`。
- 暴露当前用户批量单项 Session 幂等创建接口，以及定时执行时段和任务创建/查询/改期/取消/失败卡关闭 API；批量 Session 请求必须携带 `batchContext`。夜间创建 DTO 的可选 `batchContext` 只允许在省略 `sessionId` 时使用，可选 `scheduleMode` 缺失时按 `NIGHT_WINDOW`，旧请求行为不变；`ADMIN_CUSTOM` 创建和改期由 Controller 基于真实 `AuthPrincipal` 向应用层传递 `SUPER_ADMIN` 权限事实，owner 始终取认证主体。`NightExecutionDtos` 只把完整 prompt/parts 映射到应用命令，任务响应增加模式但仍仅返回安全截断预览，不回显完整输入。精确内部路径 `/api/internal/platform/opencode-runtime/night-execution/internal-dispatch` 仅接收目标 `linuxServerId` 和最多 50 个 `taskId`，使用标准 XXL access token 鉴权；分发网关必须先由公共 resolver 选出目标服务器上的精确 backendProcessId，再决定本机调用或统一 HTTP 转发。
- `InternalModelTokenManagementController` 仅允许 `SUPER_ADMIN` 通过独立 API 记录外部 Token、改名/轮换和删除；响应类型只包含安全元数据。`InternalModelProviderManagementController` 在原供应商字段上返回 `tokenId/tokenName/tokenConfigured` 并接受 `tokenId/clearToken`，旧顶层 `authToken/tokenConfigured` 继续兼容。两类成功变更都复用既有刷新事件和跨 Java 广播。
- `InternalModelProxyForwardingService` 以 `Auth-Token` 向企业 AI 上游注入供应商 Token，使同一请求的 `ucid` 生效；上游另一种 `Authorization: Bearer <供应商关联 Token>` 方式只能完成鉴权，`ucid` 不生效，平台不使用该方式。对 2xx SSE 复用 runtime 的单次解析观测器，只让真实模型输出产生首 Token 延迟（TTFT）和刷新输出空闲截止时间，并把 `[DONE]` 或非空 `finish_reason` 作为正常收尾信号；收到 `[DONE]` 时仍主动取消异常保持的上游连接。调用明细分开记录响应头、TTFT、流完成和端到端耗时。转发前失败尚不能解析真实供应商或模型时使用稳定 `unknown` 维度，显式长度与分块传输的 `2 MiB` 超限都会记录 `REQUEST_INVALID`。`InternalModelObservabilityController` 仅向 `SUPER_ADMIN` 暴露结构化明细、小时聚合、基于明细真实计算的 TTFT、ITL/TPOT 和 Output TPS 分布、探活状态和手动流式探活；明细与分布支持五类 `outcomeGroup` 筛选，明细还支持服务端 `ucid` 过滤并让列表与分页总数使用同一条件；接口不返回正文或 Token。
- 暴露应用引用资产库 8 个内部 API，`ReferenceRepositoryController` 只做 `APP_ADMIN` 鉴权（`SUPER_ADMIN` 继承）、初始化/切换分支/按 generation 终止请求 DTO、包含可空 `repositoryPath` 的状态响应、traceId 和阻塞 Git/文件任务调度；列表、初始化、同步、受控分支切换、只读指针核验、终止、状态、单层树的业务规则全部委托 workspace-management，不在 Controller 访问 Repository 或文件系统。
- `ApplicationAssetReferenceController` 提供应用级资产目录配置列表、保存和删除：有效应用成员可读安全字段，`APP_ADMIN/SUPER_ADMIN` 才可写。成员接口不复用管理员资产库状态响应，不包含 Git URL、凭据或服务器物理路径；版本冲突与目录校验由 workspace-management 处理。
- 暴露超级管理员用户管理 API，Controller 只做 `SUPER_ADMIN` 鉴权、关键字/角色/组织/部门组合筛选与分页参数、创建用户、手工用户名修正、单角色调整及显式/按筛选全选的批量角色请求转换；显式批量请求把缺省或 `null` 的 `allMatching` 兼容为 `false`，避免旧前端请求在反序列化阶段返回 400；改名请求不接收统一认证号，批量操作者从认证主体取得，用户创建、改名、角色替换、目标解析和 ROLE 字典校验委托 `test-agent-system-management`。
- 暴露 AI Run 整体回复反馈 API：单查/写入按 `runId`，批量查询每次最多 100 个 Run；Controller 只读取当前登录用户和 traceId，成功状态、主对话与归属校验由 runtime 服务完成。旧 messageId API 保留兼容。
- 暴露超级管理员运营分析 API，Controller 只做 `SUPER_ADMIN` 鉴权、ISO 时间参数解析、机构/研发部/部门/用户筛选参数传递、热力 metric、CSV 响应头和统一错误转换；用户维度、运营行为事实、明细、汇总和筛选项全部只读 ClickHouse。新增筛选选项、用户漏斗、日期小时热力、Token 运营和 Agent/Skill/Tool 使用率端点；旧 agent/model/workspace 参数非空时统一返回校验错误。唯一例外是 `GET /sessions` 会话消息统计：为复现业务库的存储模式/来源类型/人员归属口径，直连平台 PostgreSQL（`sessions`/`session_messages`/`runs`/`users`）。
- `GET /api/internal/platform/opencode-runtime/sessions` 是当前登录用户历史会话分页接口，支持 `page/size/q`，返回 `workspaceContext` 并按 `pinned desc, updatedAt desc, id desc` 排序；既有 `PATCH /sessions/{sessionId}` 可更新标题和置顶状态，纯置顶更新保留 `updatedAt`，使取消置顶后回到普通组原位置。Session 历史正文恢复主入口是 agent-scoped session tree messages；内部平台 messages 接口的 `refresh=false` 只读数据库快照用于只读 transcript、Run ID 恢复和旧消息反馈兼容，不再为新反馈寻找 assistant messageId。`RunResponse` 可选携带 `storageMode/clientRequestId/detailsAvailableUntil/rtkEnabled/conciseOutputSelected`；后两项历史值可空，分别表示 Run 创建时 RTK 生效快照与 concise-output 选择/观测结果。active-run API 供前端刷新后恢复 SSE。
- `GET /api/internal/platform/opencode-runtime/sessions/runtime-state` 和 `/runtime-state/events` 暴露当前登录用户历史会话运行态摘要和 fetch SSE 状态通道；DTO 包含 `questionCount/permissionCount`，`sessions[].attention` 支持 `QUESTION/PERMISSION`。Controller 只读取登录主体、traceId、映射 DTO 和输出 SSE，运行计数、两类待关注状态及事件触发刷新委托 `test-agent-opencode-runtime`。用户已有 Redis 运行态 marker 时，摘要和 active-run fallback 均只读 Redis 索引/manifest，不由 API 层回查 Repository。
- `POST /api/internal/platform/opencode-runtime/sessions/{sessionId}/side-question` 保留同步兼容路径；`.../side-question/runs` 与 agent-scoped 等价路径立即返回旁路 Run。`POST /api/internal/platform/opencode-runtime/manual-question/runs` 在无主对话时按工作区创建归档内部 Session 和远端临时会话。两种流式路径都复用 RunEvent SSE、禁用工具、等待自然语言最终回答并删除临时会话，不追加或创建普通主会话历史。
- RunEvent SSE 路由由 runtime 服务优先使用 Redis manifest 的生产服务器，manifest 缺失的 legacy/旧 Run 才读取 routing/process 兼容数据。目标 Java 的 `REDIS_SUMMARY` 流首帧总发送 Redis 物化 `run.snapshot.reset`，随后按 `runtimeVersion` 分页读取 durable/transient 全事件尾流；最短 5 秒的 Redis 安全扫描负责丢唤醒补偿，本机 live 事件仍即时唤醒尾流读取。API 层只负责流式转发，不实现数据库轮询或运行态降级。
- 本地 CORS 默认允许主前端和 `frontend-opencode` Vite/Preview/E2E 端口；生产必须通过 `TEST_AGENT_CORS_ALLOWED_ORIGINS` 显式收敛。

### Agent & Skill Hub 入口

- `AgentSkillHubController` 提供全员可读的目录/详情/更新角标、显式外部 Skill 预览、`APP_ADMIN` 的显式发布入口，以及仅 `SUPER_ADMIN` 可调用的外部上传和用户推送 Skill 事项分类入口；SkillHub `/upload`、`/download` 新版必填 `userId` 只取 `AuthPrincipal.unifiedAuthId`，不接受 HTTP multipart/query 覆盖。分类权限在 Controller 强校验，不能以应用管理员身份替代。
- Hub 正文由 `agent-skill-hub/HUB` 独立只读文件 ticket 获取；引用、取消引用与更新复用现有 `agent-config/WORKSPACE` ticket，并校验 `appAdmin`、绑定 workspace 和当前用户。引用或更新触发外部下载时，`WorkspaceFileWebSocketHandler` 只透传 ticket 已冻结的 `unifiedAuthId`。目录和更新 HTTP 查询可携带个人运行 `targetWorkspaceId`；`referencedOnly` 返回当前应用引用清单，详情附带按状态收敛的引用方应用/工作空间。
- Hub 不新增 SSE 或后端间文件 HTTP 代理；跨服务器引用始终由浏览器连接目标工作区所在 Java 的平台文件 WebSocket。

### 公共 Agent 配置发布

- `AgentConfigController` 的 `POST /public/rollout/supersede` 只负责 `SUPER_ADMIN` 鉴权、共享运行副本恢复确认、请求 DTO 和 traceId 透传；`activeRolloutId` 作为业务层 CAS 前置条件，前端不能提交 `forceStop`。响应沿用 Agent 配置 operation DTO，状态轮询通过 `GET /public/rollout` 的可选替换审计字段和 `pendingTargets` 完成；用户明细只含内部 userId/username，不返回统一认证号。
- `AgentConfigController` 的 `POST /public/worktrees/reconcile` 只允许 `SUPER_ADMIN`，按请求的 `linuxServerId` 复用公共后端路由程序，把手工补偿交给目标 Java 上与定时任务相同的服务器级 Redis 锁和幂等创建程序；响应只含内部 userId、worktreeId 与安全结果摘要。

### LobeHub 与企业模型入口

- `LobehubSsoController` 为当前用户签票，并为 LobeHub 服务端提供精确 HMAC 兑换/撤销路径；兑换在 JSON parse
  前读取有界原始 bytes 验签。只有这两个服务路径跳过通用平台 Token filter，其它路径不继承例外。
- `InternalModelCatalogManagementController` 仅允许 `SUPER_ADMIN` 覆盖供应商模型目录和探测单项声明能力。
- `ModelGatewayController` 只接受 Bearer 模型委托，提供 `/models` 和八个固定 OpenAI-compatible POST 端点；
  JSON、SSE 和 multipart 转发都委托 `test-agent-model-gateway`，不在 Controller 解析供应商或访问 Repository。
- API 日志对票据原始 bytes 只输出长度占位，并递归脱敏 `ticket/modelGrant`。LobeHub 模型流不创建 RunEvent。

### OpenCode Observability 与 Trace

- `OpencodeObservabilityPluginController` 只接收绑定用户、进程、服务器与 generation 的短期专用令牌批次；
  `OpencodeObservabilityTraceChunkController` 接收 Java 间幂等分片并委托 runtime 归档，Controller 不操作文件或 ClickHouse mapper。
- `LocalClientConnectionWebSocketHandler` 在既有 WSS 上处理 additive Observability 声明、分片、ACK 和水位；每帧校验
  client instance/connection generation，并把 `pendingChunks` 传入归档目录。模型、控制和文件路径不复用该正文。
- 六个 `/api/internal/platform/traces` 管理入口均强制 `SUPER_ADMIN`；目录和详情 additive 返回 Run 的 `rtkEnabled/conciseOutputSelected` 以及从 ClickHouse 实际 Skill Span 去重得到的 `skills[]`，只包含名称。`/spans` 只查 ClickHouse 无正文语义索引，`/records/{eventId}` 仅在选中事件后按需读取关联正文。正文查看、下载和失败尝试只写无正文、无物理路径的审计事实；正文按稳定 `linuxServerId` 存储节点路由，同节点 JVM 重启不会把已归档正文错误路由给离线的旧 `backendProcessId`。

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
- `test-agent-xxl-job-integration` 的票据服务接口。
- `test-agent-integration` 的 LobeHub SSO 服务接口。
- `test-agent-model-gateway` 的目录、探测与转发接口。
- Spring WebFlux、Validation、Security。

## 禁止依赖

- `test-agent-persistence`。
- `test-agent-opencode-sdk-generated`。
- Repository 实现类。
- 业务规则、文件系统操作、opencode 调用编排；尤其不得在 API 层实现 opencode server 启动/停止/状态查询、启动后或停止后 health、binding/heartbeat/ExecutionNode/进程状态回写。

## 测试覆盖

- `AppSourceApiContextTest` 通过真实 Spring 组件注册和 test profile 的单 `*` CORS 配置验证应用源码 HTTP、ticket 和进度 WebSocket 的完整装配图，防止多构造器 handler 未显式注入或 wildcard Origin 解析不一致导致应用启动失败。
- `RuntimeControllerTest` 覆盖 Workspace 查询、Session、Run、Diff、agent-scoped Run URL、当前用户 opencode 进程强状态、弱健康和初始化进度 GET、RunEvent SSE 恢复快照、Run/Session session-tree messages 和内部平台 URL；`RunControllerAuthorizationTest` 覆盖他人 Run 在详情、取消、Diff、SSE 与 Run 级 session-tree 入口统一返回 `FORBIDDEN`，且不触发后续读取或副作用。
- 个人进程重启测试覆盖请求体缺省、活动 Run 冲突、确认重启和跨 Java 转发时 Authorization、traceId、内部路由头与 JSON body 完整保留。
- `ConversationContextControllerTest` 覆盖已登录用户签发会话运行上下文、分享成员以所属人执行身份签发、opaque 响应 DTO、traceId 和匿名拒绝；`RuntimeControllerTest` 同时覆盖 Run 请求 `contextToken/clientRequestId` 的 DTO 透传。
- `RunResendControllerSessionShareTest` 通过真实 Spring 构造器选择验证撤销重发入口会注入会话分享服务，并在分享头请求进入业务服务前解析 `DelegatedOperationContext`，防止测试兼容构造器被误用为生产装配入口。
- `SessionRuntimeStateControllerTest` 覆盖当前登录用户运行态摘要、`permissionCount/PERMISSION` DTO、匿名拒绝、fetch SSE 首帧 snapshot、Run/question/permission 事件后的 updated 推送和无业务 data 的 comment 心跳；`PlatformErrorLogPolicyTest` 固化只有精确“请先初始化 TestAgent 进程”前置条件才允许日志降级。
- `RuntimeManagementControllerTest` 覆盖运行管理 overview、按 `linuxServerId` 的后端指标历史主 API 和进程重启/停止 API 的 `SUPER_ADMIN` 成功、扩展后的服务器/Java/JVM 指标字段响应、跨 Java 后端路由优先于本地 manager gateway、manager 下属 opencode server 明细与归属及可空 `unifiedAuthId/managerStatus` 响应映射、旧载荷缺字段兼容、命令结果响应映射、用户名筛选/响应映射、`windowMinutes` 预设窗口、`hours` 兼容、历史参数默认值与上限、非超级管理员拒绝、未认证、非法分页/状态参数和 traceId；`PublicAgentConfigRolloutManagementControllerTest` 覆盖离线发布成员退役的超管鉴权；`RuntimeManagementBackendRoutingServiceTest` 覆盖按容器归属服务器转发命令和路由头防循环。
- `CommonParameterMemoryControllerTest`、`CommonParameterMemoryBackendRoutingServiceTest` 覆盖四个超管接口、同服务器多个 Java 精确聚合、当前/远端执行、部分失败、离线、超时、稳定排序和防二次转发；`BackendJavaRouteResolverTest` 覆盖按 `backendProcessId` 精确选择。
- `XxlJobSsoTicketControllerTest` 覆盖票据签发的 `SUPER_ADMIN` 成功、`APP_ADMIN`/匿名拒绝；`SchedulerManagementControllerTest` 覆盖旧路径任意后缀统一返回 `410 API_GONE`。
- `TcdsCaseMaintenanceControllerTest` 覆盖登录用户实时查询任务类型、认证主体统一认证号、两任务类型对象透传、统一成功响应、匿名拒绝和任务类型格式校验。
- `BatchSessionControllerTest` 覆盖批量 Session 的认证、DTO 映射和非法上下文；`NightExecutionControllerTest`、`NightExecutionDtosTest` 覆盖认证、旧请求默认模式、批量上下文与 `sessionId` 互斥、超级管理员权限事实透传、创建/查询 DTO、输入校验、安全响应和统一错误；`UserOpencodeBackendRoutingWebFilterTest` 覆盖定时任务创建、改期、取消和失败卡关闭按用户 binding 路由。
- `UserManagementControllerTest` 覆盖用户管理 API 的 `SUPER_ADMIN` 组合筛选查询、创建、手工用户名修正及统一认证号保持、单人角色调整、旧前端缺省 `allMatching` 的显式批量角色兼容、按筛选全选批量角色命令映射、角色列表和非超管/匿名拒绝。
- `AiRunFeedbackControllerTest` 覆盖登录用户提交、查询和批量读取 Run 反馈；`AiMessageFeedbackControllerTest` 覆盖旧消息接口兼容与匿名拒绝。
- `AnalyticsControllerTest` 覆盖运营分析 API 的 `SUPER_ADMIN` 成功、筛选项/漏斗/热力/Token/能力端点、非超级管理员/匿名拒绝和非法时间参数统一校验错误。
- `ManagerControlWebSocketHandlerTest` 覆盖 `register`、完整配置下发后首个 `managerHeartbeat` 才开放控制连接并触发恢复、未配置连接不触发恢复、兼容 `backendListRequest` 忽略、命令结果、错误 envelope 和多线程并发控制命令完整送达的 WebSocket 入口适配。
- `UserOpencodeBackendRoutingWebFilterTest` 覆盖未绑定状态/初始化请求按全局最轻可初始化服务器转发、当前服务器放行、已有 binding 优先、远端失败不重试、Redis 选服异常统一 503、用户 opencode 进程请求按已有 binding 所属服务器转发、工作区个人 Git/应用配置操作跨服务器转发、公共配置聚合入口留在本地、内部路由头防循环、只读状态 GET 的降级，以及 agent/platform 两个 start-run 入口通过 context 只读路由时零 assignment 调用、远端/本地 body 可复读、过期或显式非法 token 统一 409 且不回显 token、请求体超限统一 400。
- `UserOpencodeWeakHealthRoutingWebFilterTest` 覆盖 `/processes/me/health` 按 query `linuxServerId` 随机转发到目标服务器在线 Java、目标后端缺失返回 `healthy=false`、路由头防循环和本服务器请求放行。
- `BackendHttpForwarderTest` 覆盖 Java->Java HTTP 转发对 Authorization、`X-Trace-Id`、query、body、content-type 和 `X-Test-Agent-Backend-Routed` 的统一透传。
- `RunEventSseBackendRoutingWebFilterTest` 覆盖 RunEvent SSE 按 Run 生产 Java 路由、平台/agent URL、防循环头和目标缺失时本机处理；`RunControlBackendRoutingWebFilterTest` 覆盖两个 cancel URL 的远端转发、本机放行、外部伪造 routed header 仍强制解析 owner，以及路由或转发失败时禁止执行本机 Controller；`RunEventSseRouteServiceTest` 固化 manifest 路由 0 次 routing/process Repository 查询，并区分 SSE 可回退解析与写操作严格解析；`BackendSseForwarderTest` 覆盖 SSE 流式转发保留 Authorization、`X-Trace-Id`、`Last-Event-ID`、query、`text/event-stream` 和 `X-Test-Agent-Backend-Routed`。
- `PlatformOpencodeRuntimeControllerTest` 覆盖 `/api/internal/platform/...` 的 opencode runtime 代理入口、MCP tools、permission reply、session share、traceId 和可选用户主体透传。
- `AgentOpencodeRuntimeControllerTest` 覆盖 `/api/internal/agent/{agentId}/...` 原始 opencode 路径兼容、agentId、traceId 和可选用户主体透传。
- `AuthWebSupportTest` 覆盖可选认证主体读取，确保 static-token 兼容入口不会因缺少用户主体抛错。
- `ReferenceRepositoryControllerTest` 覆盖 8 个内部端点、`repositoryPath` JSON 字段、初始化与终止 body、traceId/当前用户透传、`APP_ADMIN` 成功、`SUPER_ADMIN` 继承和普通用户在调用业务服务前被拒绝。
- `CurrentBackendWebSocketUrlFactoryTest`、`TerminalControllerTest`、`TerminalWebSocketHandlerTest` 覆盖 workspace 绝对 URL、服务器终端强制 WSS、PTY ticket、origin 拒绝、单目标互斥、输入限流、关闭和超时；服务器 ticket POST 复用 `BackendJavaRouteResolver`/`BackendHttpForwarder` 跨 Java 路由。
- Workspace 文件 WebSocket 入口应覆盖 route、ticket、显式 Origin 白名单、单独 wildcard 仅放行合法 Origin、同服务器和实时应用成员校验、ticket 在归属未 READY 时复查强状态、RPC 成功/错误 envelope、组合视图 list/read 与 locator 防伪造、上传/复制/移动、普通文件/目录树删除和受保护 `.opencode` 根目录拒绝；对应 HTTP/协议契约同步维护在 `docs/api/http-api.md` 与 `docs/api/event-stream.md`。
- `PersonalWorkspaceRelocationTransferControllerTest` 覆盖内部 access token、权威搬迁授权、固定 Origin/源服务器绑定和 ticket 一次消费；`PersonalWorkspaceRelocationTransferWebSocketHandlerTest` 覆盖二进制分片、唯一完成帧、成功安全响应、无效 ticket 和连接提前结束时中止临时归档；`ApiTokenWebFilterTest` 固化仅两个精确内部路径豁免普通静态 API token，子路径不继承。
- Agent 配置入口应覆盖公共/工作空间 status、公共仓库列表、公共仓库初始化、当前用户公共 worktree 的服务器路由和所有权校验、公共个人 `runtime-reload` 离开 WebFlux 事件线程执行、文件 WebSocket route/ticket/op、文件读写改名复制移动删除权限、Git stage/unstage/discard/冲突操作鉴权、纠错替换的超管成功与非超管拒绝、operation ticket、Origin 拒绝和进度 envelope；对应契约同步维护在 `docs/api/http-api.md` 与 `docs/api/event-stream.md`。
- `RuntimeApiSupportTest` 覆盖分页默认值和非法分页参数转换为统一 `VALIDATION_ERROR`。
- `UserNotificationControllerTest` 覆盖当前用户分页隔离、幂等已读和通知 SSE 的 snapshot/updated 事件名。
- `ManagedWorkspaceControllerTest` 覆盖应用版本工作区入口的认证主体、traceId、当前用户 opencode 服务器透传、请求体转换、版本 `git pull`、工作区 Git stage/unstage、冲突解决、最近使用接口，以及普通成员绕过 Agent API 提交 `.opencode/**` 时的拒绝。
- `WorkspaceGitToolControllerTest`、`ApiTokenWebFilterTest` 覆盖专用 Tool 凭据入口的身份透传和精确过滤器例外；其它 API 路径仍要求原有用户或静态 Token。
- `UiTestToolConfigControllerTest`、`ApiTokenWebFilterTest` 覆盖 UI Tool 无凭据只读响应、精确过滤器例外和相邻路径仍要求原有 Token。
- `ManagedWorkspaceController` 额外暴露版本选择前的 `GET /workspace-versions/{versionId}/git-access` 只读预检；Controller 只透传当前认证用户，仓库身份、SSH key 和 Git 失败分类由 workspace-management 处理。
- `RuntimeSecurityConfigTest` 覆盖本地 `frontend-opencode` real E2E Origin 白名单、`X-Test-Agent-Linux-Server-Id` / `X-Support-Access-Grant` 的 CORS 预检，以及企业显式浏览器白名单下搬迁内部 Origin 只允许精确 WebSocket 路径、浏览器 Origin 和子路径仍被拒绝。
- `AuthControllerRolesTest`、`ConfigurationManagementControllerTest` 覆盖认证响应 roles、`APP_ADMIN`/`SUPER_ADMIN` 鉴权、代码库英文名、版本库类型与部署模式 DTO、版本库类型/部署模式下拉接口、应用版本库远端树接口、工作空间创建进度轮询和 SSH key 不回显私钥。
- `ApiTokenWebFilterTest`、`InMemoryRateLimitWebFilterTest`、`TraceIdWebFilterTest`、`GlobalExceptionHandlerTest`、`LegacyApiGoneWebFilterTest` 覆盖鉴权、限流、traceId、旧接口 410 和统一错误响应。
- `InternalModelTokenManagementControllerTest` 覆盖 `SUPER_ADMIN` 鉴权、统一冲突错误和响应不泄露 Token；代理测试覆盖按 Provider ID 以 `Auth-Token` 注入不同 Token 且不发送上游 `Authorization`、鉴权先于请求体聚合、`2 MiB` 定长及 chunked 上限、chunked 异步拒绝的单次观测、转发前未知维度，以及流式 JSON 完整性校验、role/伪心跳/畸形 data 不产生首 Token、无收尾信号的流中断、`[DONE]` 完成和 `finish_reason` 后 EOF 的企业网关兼容场景。`InternalModelObservabilityControllerTest` 固化 `outcomeGroup`、UCID 透传、流完成字段和 TTFT/ITL/Output TPS 分布的 API 序列化。`ApiLoggingAspectTest` / `ServiceLoggingAspectTest` / `WebSocketLoggingAspectTest` 覆盖 Controller、Service 与 WebSocket 日志切面在同步、响应式和错误路径下保留原调用语义；`SensitiveDataMaskerTest` 覆盖 `contextToken` 及内部模型 `authToken` 请求/响应字段脱敏。
- `LobehubSsoControllerTest`、`InternalModelCatalogManagementControllerTest`、`ModelGatewayControllerTest` 和
  `ApiLoggingSensitiveBodyTest` 覆盖身份边界、原始 body、固定端点、multipart、错误 envelope 与票据日志脱敏。

## 后续 AI 编码指引

通用长期记忆 HTTP 入口固定在 `/api/internal/platform/memory/v1/**`，系统管理入口固定在同前缀的
`/admin/**`。旧 `/qa-memory/v1/**` 由专用 Controller 返回 `410 API_GONE`。Controller 只做当前用户/角色鉴权、DTO、traceId 和统一响应；
个人/团队范围、Application 成员和 `expectedVersion` 规则由 `test-agent-memory` 执行。Run 使用记录通过批量 HTTP
恢复，不新增或修改 RunEvent SSE。Skill 提案创建、审核、草稿编辑、归档和已发布资产关联均使用该前缀；审核与关联
要求 `APP_ADMIN`，Controller 不直接写 Workspace、不调用 Git 或 Hub 发布协议。
`GET /availability` 是已登录用户的最小灰度探针，只返回“记忆总开关已启用且当前用户位于记忆名单”的布尔结果；其它用户接口仍由业务层强制校验总开关和白名单。前端用该结果隐藏活动栏入口并在 `/memories` 路由挂载前失败关闭，但后端不能依赖前端隐藏完成鉴权。

新增 API 时先确认业务实现应落在哪个业务模块；本模块只新增 Controller/DTO/协议转换。平台自身接口放 `web.platform`，agent 代理入口放 `web.agent`，横切入口支撑放 `web.common`。不得新增旧 `/api/...` runtime/workspace 入口；新 URL 必须同步记录到 `docs/api/http-api.md`。

`RunResendController` 暴露 agent-scoped 最后一条消息撤销重发，只允许源消息实际发送人操作；分享发送人还必须持有 `canChat`，并接受可选、最长 20000 字符的 `editedPrompt`；`RunResendInternalDispatchController` 仅接收带既有 XXL token 的
精确 Java→Java 批量恢复请求，`HttpRunResendDispatchGateway` 固定复用公共路由解析器和转发器。`Run`、Session message 与
runtime-state DTO 的 `resend` 均为可选 additive 字段，分享 runtime-state 额外 additive 返回 `sessionUpdatedAt`；旧客户端缺失时继续按普通运行展示。内部响应与事件不返回 prompt、回答或
供应商正文。共享人工重发的历史 Run/消息若曾被执行链错误写成所属人，DTO 映射以既有 `resend.requester*` 审计字段恢复实际发送人、姓名和代操作标记，不修改协议结构或数据库历史。

## 本地 OpenCode 客户端入口

`LocalClient*Controller` 和 `LocalClientConnectionWebSocketHandler` 承载当前用户 key、实例、生命周期、模型
代理和反向 WSS 入口；`LocalWorkspaceController` 与现有文件 route/ticket/handler 承载网页目录浏览兜底和文件
RPC，客户端托盘的 `WORKSPACE_REGISTER` 则由反向 WSS 入口调用同一个 `LocalWorkspaceApplicationService`。
HTTP 注册/注销会同步等待反向文件 RPC，Controller 必须调度到 `boundedElastic`，不得阻塞 WebFlux event-loop。
`POST /local-workspaces/{workspaceId}/recent` 同时是激活入口：先按原绑定或唯一在线实例路由到连接持有 Java，完成
历史路径校验、同实例根恢复或跨实例安全接管后才保存偏好并返回新实例身份；前端必须在切换 Workspace 前调用。
跨 Java 必须按连接记录的 backendProcessId/generation 复用公共 resolver/forwarder，Controller 不读取
Redis 快照、不直接控制本地 supervisor。Workspace/Session/Run/夜间及统一 OpenCode 实例响应仅追加
runtime/capability 字段，旧服务端路径保持兼容。完整契约见 `docs/api/http-api.md` 与
`docs/api/event-stream.md`。
当前用户主动撤销 Client key 后，实例列表返回空，本地 Workspace 列表与详情按不存在处理；稳定实例、平台绑定
和用户磁盘目录均保留，重新启用凭据后恢复展示。轮换和普通断连仍保留离线状态。
`LocalClientRolloutAdminController` 仅允许 `SUPER_ADMIN` 分页、添加和移出客户端功能可见性灰度用户；
`UserOpencodeEndpointController` 只在灰度启用时追加本地实例，同时继续把当前用户灰度作为服务端实例的
`localClientDownload` capability additive 返回，并提供不跟随进程归属路由的 `download-access/me` 兼容
查询。网页以独立布尔结果控制下载、实例、工作区和个人客户端设置；查询异常时失败关闭为 false。
服务端 binding 的显式关闭只省略 `SERVER_PROCESS`，不得省略仍可见的 `LOCAL_CLIENT`。

`ProtectedAgentMcpController` 仅承载服务器 OpenCode 的精确 stateless MCP JSON-RPC 入口。它从 HTTP exchange
读取短期 Bearer grant，保持 MCP 原始 wire body，不使用平台 `ApiResponse` envelope；notification 显式返回
`202` 空 body。Spring Boot 4 的 HTTP codec 边界使用开放 `Object` 接收 id/params，再显式转换为运行层
Jackson 2 树，不能把 Jackson 2 `JsonNode` 直接声明为 HTTP DTO。请求和响应 DTO 实现安全日志摘要，通用
日志切面遇到 `ResponseEntity` 也只序列化摘要；空响应只记录状态码，不展开框架 Header。禁止
记录 Authorization、文件参数/内容或 Skill 正文。`ApiTokenWebFilter` 只豁免该精确路径，相邻子路径仍按原
鉴权拒绝。`ProtectedAgentMcpControllerTest`、`ApiLoggingAspectTest` 和 `ApiTokenWebFilterTest` 固化上述边界。
