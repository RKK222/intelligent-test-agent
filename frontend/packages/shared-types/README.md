# @test-agent/shared-types

## 工程定位

跨前端包共享的轻量 TypeScript 类型集合。

## 主要职责

- 提供 `XxlJobSsoTicket`，只表达短期 `ticket/expiresAt/formAction` 响应；原始票据不得进入持久化状态、URL 或日志。
- 提供 `LobehubSsoTicket`，只表达当前用户签票得到的 `ticket/expiresAt/consumeUrl`；`consumeUrl` 必须由 launch
  helper 再校验，类型本身不表达任意 return URL、模型委托或 LobeHub Session。
- 定义 `ToolboxCatalogResponse`、`ToolboxTool` 与 `ToolboxClickResponse`，表达版本化离线目录、双语名称/说明、固定分类、来源版本、同源深链接、累计点击和可空热门排名；点击请求的 `eventId` 由页面每次打开动作生成，不包含客户端时间或用户身份。
- 定义应用源码列表、固定提交选择、物化/保留期调整/重试/打开、持久化操作和独立进度 WebSocket DTO；列表以可选 additive 字段携带 `acceptedAt/maxRetentionHours`，续期 payload 使用 expected generation。`AppSourceProgressEvent` 是严格判别联合，`snapshot` 可承载任一合法状态，`step` 只承载 `PENDING/RUNNING`，`completed` 只承载 `SUCCEEDED/PARTIAL_FAILED`，持久化 `failed` 的内外状态都固定为 `FAILED`，失败分支必须携带非空安全错误。

- 定义 API 响应、Workspace、WorkspaceDirectoryList、Session、SessionMessage、Run、RunEvent、Diff、AgentMessage 类型；Session、SessionMessage、Run 和 AgentMessage 可选携带 `sourceType/sourceRefId`，用于兼容并展示 `SCHEDULED_TASK` 来源。Workspace 可选携带 `linuxServerId`，用于前端文件 WebSocket 同服务器路由。`Session.workspaceContext` 可选携带历史会话所属 `appId/appName/applicationWorkspaceId/workspaceName/versionId/version`，旧后端或单会话详情缺失时前端必须兼容 `null/undefined`。
- 定义 `NightExecutionScheduleMode`、`NightExecutionSlotsResponse`、`NightExecutionTask`、`NightExecutionTaskQueryResponse`，表达 `NIGHT_WINDOW/ADMIN_CUSTOM`、北京时间夜间窗口、15 分钟容量时段、待执行任务和当前会话可见失败卡；任务的 `scheduleMode` 保持可选以兼容旧后端响应，响应不包含完整 prompt/parts。
- 定义 `CommonParameterMemoryValue`、`CommonParameterMemoryProcess`、`CommonParameterMemoryCluster` 及状态联合类型，表达显式注册参数的数据库源值、JVM 生效值、加载/刷新时间，以及按 `backendProcessId` 独立返回的集群成功、部分失败、失败和不可用结果。
- 定义兼容同步 `SideQuestionRequest/Response`、主会话流式 `SideQuestionRunRequest/Response` 和无主对话 `ManualQuestionRunRequest`；旁路 Run 的问题和回答不进入普通主会话时间线。配置管理类型包含超级管理员新建应用的 `CreateApplicationPayload`。
- `SessionMessage` 保留旧 `content` 字段，并可选承载 `runId`、`remoteMessageId`、`parts`、`tokens`、`costUsd`、`updatedAt` 及 `contentKind/summaryStatus/summaryVersion`，用于区分旧原文与新模式终态摘要；旧响应缺失这些字段时前端继续按纯文本展示。
- assistant `AgentMessage` 可选携带 `tokens: TokenUsage` 与 `model: ModelRef`，供历史/实时消息保留最近完整用量和模型；所有 `AgentMessage` 继续可选携带 `runId` 以绑定整轮状态与反馈，`platformMessageId`/`remoteMessageId` 分别用于平台摘要和运行期消息归并，不作为新反馈业务键。旧投影缺少新可选字段时保持兼容。
- `Run` 可选承载 `tokens`、`costUsd`、`storageMode`、`clientRequestId`、`detailsAvailableUntil`，统计口径为单次 Run；缺失消耗字段必须按未知处理，缺失 `clientRequestId` 时调用方只能在同认证、Session、Workspace、交互代次且 runtime-state 已接管 busy Run 的条件下兼容判断 HTTP 歧义结果。`SessionTreeMessagesResponse` 的 `historyRepresentation/replayAvailable/detailsAvailableUntil` 同样可选，旧响应缺失时按完整历史兼容展示。
- `RunEventType` 包含 transient `run.snapshot.reset`；`RunRuntimeSnapshot` 与 `RunSnapshotResetPayload` 的 `barrierSeq/runtimeVersion/events/reason/resetGeneration/earliestSeq/detailsAvailableUntil` 均保持可选，兼容旧后端、空物化快照和新增字段。`runtimeVersion` 是后端 Redis durable/transient 尾流版本，不是 durable SSE 游标；snapshot 内部事件只用于 reducer 重放，不推进 `Last-Event-ID`。
- 定义 `SessionRuntimeStateSummary` / `SessionRuntimeState` / `SessionRuntimeAttention`，表达当前用户历史会话中的运行中 Run 数、待回答 question 数和单会话运行态；字段需兼容旧后端缺失场景，由消费方降级为空摘要。
- 新增 PromptPart、MessagePart、ToolPart、PermissionRequest、QuestionRequest、AgentInfo、ModelInfo、ProviderInfo、CommandInfo、RuntimeResourceInfo、RuntimeToolInfo、SessionDiff、TodoItem、RuntimeStatus、TerminalTicketRequest、ServerTerminalTicketRequest、TerminalTicketResponse 等 Web App 运行态 projection 类型；服务器 ticket 请求显式承载逐次确认文本，不包含 SSH 用户名、密码或私钥。`PermissionRequest.patterns` 可选承载 OpenCode 原生多路径并保留旧 `pattern/title/description`，`SessionRuntimeStateSummary.permissionCount` 保持可选以兼容旧后端，`sessions[].attention` 接受 `PERMISSION`；`PromptPart.type=file` 的 `source` 可选携带 `startLine/endLine/contextType`，用于工作区选区上下文在前端展示和 opencode prompt parts 透传时保留来源元数据；`QuestionRequest.tool` 可选保留原 question 工具的 `messageId/callId`，用于回复后精确收敛工具卡并兼容旧事件缺失该字段。
- `CommandInfo` 的 `source/hints` 为可选字段，用于保留 opencode command catalog 的来源和参数提示；旧 payload 不提供时前端必须兼容。
- `TodoItem` 保留旧 `text/status/priority` 字段，`status` 已知兼容 `pending/in_progress/completed/cancelled` 并允许未知字符串；可选承载 `title/description/summary/result/error/steps/updatedAt` 等对话框任务分解展示字段。
- `MessageScope` 和 `SubagentSession` 只描述前端消费 RunEvent scope 后的运行期展示索引，用于主 Agent / 子 Agent 时间线切换；不代表新增后端持久化模型或数据库契约。
- `CurrentUser`、`LoginResponse` 增加可选 `roles`，旧响应缺字段时按空角色兼容。
- 定义用户管理 DTO：`UserManagementUser`、`UserManagementQuery`、`CreateUserPayload`、`UpdateUserRolePayload`、`UserIdsPayload`、`DeleteUsersResult`、`SyncUsersFromTcdsResult`、`RoleOption`，供 `backend-api` 和设置页超级管理员组合筛选、造号、批量选择后调整角色、安全删除及 TCDS 信息同步面板复用。
- 定义 opencode 用户进程状态、独立 `UserOpencodeMessageGate`、初始化进度与超级管理员运行管理 DTO：当前用户进程状态（含头像菜单用 `serviceStatus` / `serviceAddress` 展示字段，`linuxServerId` 表示稳定服务器身份，`serviceAddress` 表示当前解析出的网络地址且允许为空；初始化响应可选 `publicWorktreePreparation` 表达同服公共个人 worktree 的附加准备结果）、公共配置消息门禁、`OpencodeProcessStartOperation` 轮询进度快照、运行管理 overview、拓扑列表、manager 可选下属 `OpencodeRuntimeManagedProcess` 明细、manager-backend 连接、用户 opencode server 进程分页、`OpencodeRuntimeManagedProcessCommandResult` 重启/停止命令结果，以及容器/后端 Java 进程的 Redis 指标历史响应类型；`OpencodeRuntimeManagedProcess` 可选承载 `unifiedAuthId`、`managerStatus`、`ownership`、候选进程、健康和用户绑定字段，供前端展示 UCID/manager PID 状态并把 manager 本地托管进程分成有主/无主；`OpencodeRuntimeBackendProcess` 和 `OpencodeRuntimeBackendMetricSample` 可选承载服务器 CPU/load/内存/swap/磁盘、Java 进程 CPU/RSS/FD、JVM heap/non-heap/direct/mapped/GC/线程字段，并保留 `memoryMaxBytes`、`jvmGcPauseMillis` 等旧字段；指标历史查询主参数为 `windowMinutes`，`hours` 仅保留兼容；新增监控、manager 下属进程、初始化进度和命令结果字段均保持可选，旧后端缺字段时前端必须兼容。
- 定义 Run 整体回复反馈 DTO：`AiRunFeedback`、`AiRunFeedbackPayload`、`RunFeedbackState`、`RunFeedbackQuery`；旧 `AiMessageFeedback` 类型保留兼容。
- 定义运营分析 DTO：`AnalyticsQueryParams`、`AnalyticsOverview`、`AnalyticsTimeSeriesPoint`、`AnalyticsPeaks`、用户/组织/满意度/异常明细行和 freshness；类型只表达 token 使用，不新增费用字段。
- 定义 Workspace/Agent 配置文件 WebSocket 路由、目标后端服务器、ticket 请求/响应 DTO，供 `backend-api` 和 agent-web 复用；`FileBinaryChunk` / `FileBinaryChunkRequest` 表达 Base64 原始字节分段及大小/修改时间快照；`FileTreeEntry` 的可选 `displayName/displayNameEn` 只用于 Agent/Skill 配置树双语展示，稳定文件身份仍是 `path/name`；`AgentConfigWorktreeOption` 在公共 worktree 切换列表中补充 `createdByUserId/createdByUsername`。
- `PublicAgentRepositoryStatus.localChangesPresent` 可选表达共享运行副本是否需要全局恢复确认；`PublicAgentConfigRolloutStatus` 及服务器明细表达公共全局刷新的同步、排空、个人 worktree 补偿任务总数/待处理/已收敛和 `lastError`，并以可选 `supersedesRolloutId/supersededByRolloutId/supersedeReason` 展示纠错替换审计链。新增字段按 additive 兼容，旧后端缺失替换字段或 worktree 计数时调用方分别按无替换关系和 0 展示。
- 定义 `AgentSkillHubAsset/Detail/Reference/ReferenceConsumer/Update/UpdateOperation` 等 DTO；新增状态或字段应保持 additive，`referenceCount` 表示有效引用应用数，详情 `consumers` 表示可见引用方；`builtin` 明确区分平台公共配置的只读虚拟资产，`referenceStatus` 区分 `PENDING_PUSH/ACTIVE/PENDING_REMOVE/UPDATE_CONFLICT`。
- 定义 `WorkspaceViewLocator`、稳定 `WorkspaceViewEntry.id`、`WORKSPACE/REFERENCE/MIXED` 来源、节点只读/冲突/工作区写入路径、局部 warning 和组合视图读取结果；展示 `path` 允许重复，调用方必须使用 `id/locator` 区分工作区与引用文件。
- 定义应用配置管理 DTO：`ApplicationDefinition`、`ApplicationMember`、`CodeRepositoryConfig`、`RepositoryTypeOption`、`RepositoryDeploymentOptions`、`RepositoryTreeNode`、`RepositoryTreeResponse`、`ApplicationWorkspaceConfig`、`WorkspaceCreateOperation`、`SshKeyMetadata` 和对应请求 payload；`CodeRepositoryConfig.englishName` 对历史数据保持可空，新增/编辑 payload 必填；`deploymentMode` 对旧响应保持可选，新增 payload 可携带 `EXTERNAL/INTERNAL`；`repositoryType` / `repositoryTypeLabel` 对旧响应保持可选，新增 payload 可携带 `repositoryType` 并继续保留兼容 `standard`；`CreateApplicationWorkspacePayload.directoryNew` 可选，仅表示设置页保存时需要在 clone 后创建前端内存新增的工作空间目录。
- 定义内部模型 `InternalModelTokenDefinition` 与新增/更新/删除 payload，以及带可选 `tokenId/tokenName/tokenConfigured` 的 Provider DTO；Token 定义响应没有明文值，Provider 更新通过 `tokenId/clearToken` 表达关联并保留顶层 `authToken` 旧请求兼容。
- 定义应用版本工作区 DTO：`ManagedApplication`、`ApplicationWorkspaceTemplate`、`ApplicationWorkspaceVersion`、`PersonalWorkspace`、`PersonalWorkspaceGitPullResult`、`ApplicationGitRefreshScope`、`ApplicationGitRefreshScopeGroup`、`ApplicationGitRefreshScopeWorkspace`、`ApplicationGitRefreshGroupSelector`、`ApplicationGitRefreshResult`、`ApplicationGitRefreshGroup`、`WorkspaceDiff`、`WorkspaceSyncResult` 和对应请求 payload；个人拉取结果区分是否实际更新、是否包含应用 Agent 配置及变更文件，并以可选 `runtimeReloadStatus/runtimeReloadId` 表达新版后端的单用户持久化运行态重载登记结果；应用级刷新范围明确映射工作空间、版本和实际 feature 分支，selector 支持精确刷新单个物理组，执行结果按物理 feature 仓库组返回成功、失败、个人 worktree 合并与应用 Agent 重载统计。字段保持可选以兼容滚动升级旧后端。`ApplicationWorkspaceVersion` 的 `targetCommitHash`、`replicaCommitHash`、`replicaLinuxServerId`、`replicaStatus` 均为可选字段，兼容旧后端和历史版本。
- `ApplicationWorkspaceConfig.enabled` 对旧响应可选且缺失时按启用处理；`UpdateApplicationWorkspacePayload` 支持部分更新工作空间名称或启用状态。
- 不引入运行时依赖。

## 禁止事项

- 不依赖 UI、API client 或事件 client。
- 不存放组件逻辑。
