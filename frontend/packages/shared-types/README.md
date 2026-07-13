# @test-agent/shared-types

## 工程定位

跨前端包共享的轻量 TypeScript 类型集合。

## 主要职责

- 定义 API 响应、Workspace、WorkspaceDirectoryList、Session、SessionMessage、Run、RunEvent、Diff、AgentMessage 类型；Workspace 可选携带 `linuxServerId`，用于前端文件 WebSocket 同服务器路由。`Session.workspaceContext` 可选携带历史会话所属 `appId/appName/applicationWorkspaceId/workspaceName/versionId/version`，旧后端或单会话详情缺失时前端必须兼容 `null/undefined`。
- 定义兼容同步 `SideQuestionRequest/Response` 以及流式 `SideQuestionRunRequest/Response`；旁路 Run 的问题和回答不进入主会话时间线。
- `SessionMessage` 保留旧 `content` 字段，并可选承载 `runId`、`remoteMessageId`、`parts`、`tokens`、`costUsd`、`updatedAt` 及 `contentKind/summaryStatus/summaryVersion`，用于区分旧原文与新模式终态摘要；旧响应缺失这些字段时前端继续按纯文本展示。
- `AgentMessage` 可选区分 `platformMessageId` 与 `remoteMessageId`：前者表示平台 `session_messages.message_id`，用于反馈等平台 API；后者表示实时 opencode message id，只用于运行期归并和终态后映射。
- `Run` 可选承载 `tokens`、`costUsd`、`storageMode`、`clientRequestId`、`detailsAvailableUntil`，统计口径为单次 Run；缺失消耗字段必须按未知处理，缺失 `clientRequestId` 时调用方只能在同认证、Session、Workspace、交互代次且 runtime-state 已接管 busy Run 的条件下兼容判断 HTTP 歧义结果。`SessionTreeMessagesResponse` 的 `historyRepresentation/replayAvailable/detailsAvailableUntil` 同样可选，旧响应缺失时按完整历史兼容展示。
- `RunEventType` 包含 transient `run.snapshot.reset`；`RunRuntimeSnapshot` 与 `RunSnapshotResetPayload` 的 `barrierSeq/runtimeVersion/events/reason/resetGeneration/earliestSeq/detailsAvailableUntil` 均保持可选，兼容旧后端、空物化快照和新增字段。`runtimeVersion` 是后端 Redis durable/transient 尾流版本，不是 durable SSE 游标；snapshot 内部事件只用于 reducer 重放，不推进 `Last-Event-ID`。
- 定义 `SessionRuntimeStateSummary` / `SessionRuntimeState` / `SessionRuntimeAttention`，表达当前用户历史会话中的运行中 Run 数、待回答 question 数和单会话运行态；字段需兼容旧后端缺失场景，由消费方降级为空摘要。
- 新增 PromptPart、MessagePart、ToolPart、PermissionRequest、QuestionRequest、AgentInfo、ModelInfo、ProviderInfo、CommandInfo、RuntimeResourceInfo、RuntimeToolInfo、SessionDiff、TodoItem、RuntimeStatus、TerminalTicketRequest、TerminalTicketResponse 等 Web App 运行态 projection 类型；`PromptPart.type=file` 的 `source` 可选携带 `startLine/endLine/contextType`，用于工作区选区上下文在前端展示和 opencode prompt parts 透传时保留来源元数据；`QuestionRequest.tool` 可选保留原 question 工具的 `messageId/callId`，用于回复后精确收敛工具卡并兼容旧事件缺失该字段。
- `CommandInfo` 的 `source/hints` 为可选字段，用于保留 opencode command catalog 的来源和参数提示；旧 payload 不提供时前端必须兼容。
- `TodoItem` 保留旧 `text/status/priority` 字段，`status` 已知兼容 `pending/in_progress/completed/cancelled` 并允许未知字符串；可选承载 `title/description/summary/result/error/steps/updatedAt` 等对话框任务分解展示字段。
- `MessageScope` 和 `SubagentSession` 只描述前端消费 RunEvent scope 后的运行期展示索引，用于主 Agent / 子 Agent 时间线切换；不代表新增后端持久化模型或数据库契约。
- `CurrentUser`、`LoginResponse` 增加可选 `roles`，旧响应缺字段时按空角色兼容。
- 定义用户管理 DTO：`UserManagementUser`、`CreateUserPayload`、`UpdateUserRolePayload`、`RoleOption`，供 `backend-api` 和设置页超级管理员造号及角色调整面板复用。
- 定义 opencode 用户进程状态、初始化进度与超级管理员运行管理 DTO：当前用户进程状态（含头像菜单用 `serviceStatus` / `serviceAddress` 展示字段，`linuxServerId` 表示稳定服务器身份，`serviceAddress` 表示当前解析出的网络地址且允许为空）、`OpencodeProcessStartOperation` 轮询进度快照、运行管理 overview、拓扑列表、manager 可选下属 `OpencodeRuntimeManagedProcess` 明细、manager-backend 连接、用户 opencode server 进程分页、`OpencodeRuntimeManagedProcessCommandResult` 重启/停止命令结果，以及容器/后端 Java 进程的 Redis 指标历史响应类型；`OpencodeRuntimeManagedProcess` 可选承载 `ownership`、候选进程、健康和用户绑定字段，供前端把 manager 本地托管进程分成有主/无主；`OpencodeRuntimeBackendProcess` 和 `OpencodeRuntimeBackendMetricSample` 可选承载服务器 CPU/load/内存/swap/磁盘、Java 进程 CPU/RSS/FD、JVM heap/non-heap/direct/mapped/GC/线程字段，并保留 `memoryMaxBytes`、`jvmGcPauseMillis` 等旧字段；指标历史查询主参数为 `windowMinutes`，`hours` 仅保留兼容；新增监控、manager 下属进程、初始化进度和命令结果字段均保持可选，旧后端缺字段时前端必须兼容。
- 定义 AI 回复反馈 DTO：`AiFeedbackRating`、`AiFeedbackReasonCode`、`AiMessageFeedback`、`AiMessageFeedbackPayload`。
- 定义运营分析 DTO：`AnalyticsQueryParams`、`AnalyticsOverview`、`AnalyticsTimeSeriesPoint`、`AnalyticsPeaks`、用户/组织/满意度/异常明细行和 freshness；类型只表达 token 使用，不新增费用字段。
- 定义 Workspace/Agent 配置文件 WebSocket 路由、目标后端服务器、ticket 请求/响应 DTO，供 `backend-api` 和 agent-web 复用；`AgentConfigWorktreeOption` 在公共 worktree 切换列表中补充 `createdByUserId/createdByUsername`。
- 定义应用配置管理 DTO：`ApplicationDefinition`、`ApplicationMember`、`CodeRepositoryConfig`、`RepositoryTypeOption`、`RepositoryDeploymentOptions`、`RepositoryTreeNode`、`RepositoryTreeResponse`、`ApplicationWorkspaceConfig`、`WorkspaceCreateOperation`、`SshKeyMetadata` 和对应请求 payload；`CodeRepositoryConfig.englishName` 对历史数据保持可空，新增/编辑 payload 必填；`deploymentMode` 对旧响应保持可选，新增 payload 可携带 `EXTERNAL/INTERNAL`；`repositoryType` / `repositoryTypeLabel` 对旧响应保持可选，新增 payload 可携带 `repositoryType` 并继续保留兼容 `standard`；`CreateApplicationWorkspacePayload.directoryNew` 可选，仅表示设置页保存时需要在 clone 后创建前端内存新增的工作空间目录。
- 定义应用版本工作区 DTO：`ManagedApplication`、`ApplicationWorkspaceTemplate`、`ApplicationWorkspaceVersion`、`PersonalWorkspace`、`WorkspaceDiff`、`WorkspaceSyncResult` 和对应请求 payload；`ApplicationWorkspaceVersion` 的 `targetCommitHash`、`replicaCommitHash`、`replicaLinuxServerId`、`replicaStatus` 均为可选字段，兼容旧后端和历史版本。
- 不引入运行时依赖。

## 禁止事项

- 不依赖 UI、API client 或事件 client。
- 不存放组件逻辑。
