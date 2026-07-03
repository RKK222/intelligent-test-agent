# @test-agent/shared-types

## 工程定位

跨前端包共享的轻量 TypeScript 类型集合。

## 主要职责

- 定义 API 响应、Workspace、WorkspaceDirectoryList、Session、SessionMessage、Run、RunEvent、Diff、AgentMessage 类型；Workspace 可选携带 `linuxServerId`，用于前端文件 WebSocket 同服务器路由。
- `SessionMessage` 保留旧 `content` 字段，并可选承载 `runId`、`remoteMessageId`、`parts`、`tokens`、`costUsd`、`updatedAt`，用于展示后端持久化的 Run 快照和 opencode message part projection；旧响应缺失这些字段时前端继续按纯文本展示。
- `Run` 可选承载 `tokens`、`costUsd`，统计口径为单次 Run；缺失字段必须按未知消耗处理。
- 新增 PromptPart、MessagePart、ToolPart、PermissionRequest、QuestionRequest、AgentInfo、ModelInfo、ProviderInfo、CommandInfo、RuntimeResourceInfo、RuntimeToolInfo、SessionDiff、TodoItem、RuntimeStatus、TerminalTicketRequest、TerminalTicketResponse 等 Web App 运行态 projection 类型。
- `CommandInfo` 的 `source/hints` 为可选字段，用于保留 opencode command catalog 的来源和参数提示；旧 payload 不提供时前端必须兼容。
- `TodoItem` 保留旧 `text/status/priority` 字段，`status` 已知兼容 `pending/in_progress/completed/cancelled` 并允许未知字符串；可选承载 `title/description/summary/result/error/steps/updatedAt` 等对话框任务分解展示字段。
- `MessageScope` 和 `SubagentSession` 只描述前端消费 RunEvent scope 后的运行期展示索引，用于主 Agent / 子 Agent 时间线切换；不代表新增后端持久化模型或数据库契约。
- `CurrentUser`、`LoginResponse` 增加可选 `roles`，旧响应缺字段时按空角色兼容。
- 定义用户管理（测试）DTO：`UserManagementUser`、`CreateUserPayload`、`RoleOption`，供 `backend-api` 和设置页超级管理员造号面板复用。
- 定义 opencode 用户进程状态、初始化进度与超级管理员运行管理 DTO：当前用户进程状态（含头像菜单用 `serviceStatus` / `serviceAddress` 展示字段，`linuxServerId` 表示稳定服务器身份，`serviceAddress` 表示当前解析出的网络地址且允许为空）、`OpencodeProcessStartOperation` 轮询进度快照、运行管理 overview、拓扑列表、manager 可选下属 `OpencodeRuntimeManagedProcess` 明细、manager-backend 连接、用户 opencode server 进程分页、`OpencodeRuntimeManagedProcessCommandResult` 重启/停止命令结果，以及容器/后端 Java 进程的 Redis 指标历史响应类型；`OpencodeRuntimeManagedProcess` 可选承载 `ownership`、候选进程、健康和用户绑定字段，供前端把 manager 本地托管进程分成有主/无主；指标历史查询主参数为 `windowMinutes`，`hours` 仅保留兼容；新增监控、manager 下属进程、初始化进度和命令结果字段均保持可选，旧后端缺字段时前端必须兼容。
- 定义 AI 回复反馈 DTO：`AiFeedbackRating`、`AiFeedbackReasonCode`、`AiMessageFeedback`、`AiMessageFeedbackPayload`。
- 定义运营分析 DTO：`AnalyticsQueryParams`、`AnalyticsOverview`、`AnalyticsTimeSeriesPoint`、`AnalyticsPeaks`、用户/组织/满意度/异常明细行和 freshness；类型只表达 token 使用，不新增费用字段。
- 定义 Workspace/Agent 配置文件 WebSocket 路由、目标后端服务器、ticket 请求/响应 DTO，供 `backend-api` 和 agent-web 复用；`AgentConfigWorktreeOption` 在公共 worktree 切换列表中补充 `createdByUserId/createdByUsername`。
- 定义应用配置管理 DTO：`ApplicationDefinition`、`ApplicationMember`、`CodeRepositoryConfig`、`RepositoryTypeOption`、`RepositoryDeploymentOptions`、`ApplicationWorkspaceConfig`、`WorkspaceCreateOperation`、`SshKeyMetadata` 和对应请求 payload；`CodeRepositoryConfig.englishName` 对历史数据保持可空，新增/编辑 payload 必填；`deploymentMode` 对旧响应保持可选，新增 payload 可携带 `EXTERNAL/INTERNAL`；`repositoryType` / `repositoryTypeLabel` 对旧响应保持可选，新增 payload 可携带 `repositoryType` 并继续保留兼容 `standard`。
- 定义应用版本工作区 DTO：`ManagedApplication`、`ApplicationWorkspaceTemplate`、`ApplicationWorkspaceVersion`、`PersonalWorkspace`、`WorkspaceDiff`、`WorkspaceSyncResult` 和对应请求 payload；`ApplicationWorkspaceVersion` 的 `targetCommitHash`、`replicaCommitHash`、`replicaLinuxServerId`、`replicaStatus` 均为可选字段，兼容旧后端和历史版本。
- 不引入运行时依赖。

## 禁止事项

- 不依赖 UI、API client 或事件 client。
- 不存放组件逻辑。
