# test-agent-domain

## 工程定位

纯领域模型模块，表达测试智能体平台的核心业务概念和状态规则。

## 技术栈

- Java 21
- Maven library jar
- 依赖 `test-agent-common`

## 主要职责

- Workspace、Session、AgentSessionBinding、Run、ConversationRunContext、Run 运行数据面、RunEvent、ExecutionNode、RoutingDecision、opencode 用户进程管理拓扑、应用源码快照、夜间执行任务、AI 回复反馈、运营分析、应用配置管理、应用版本工作区、应用版本服务器副本、个人工作区、服务器广播和定时任务框架等领域对象。
- Run 状态机、路由决策值对象、领域服务接口。
- 保持业务规则与基础设施分离。
- 认证领域端口 `TokenSessionMarkerStore` 只定义平台 Token 的 SHA-256 session marker 写入、删除、校验与摘要规则，供平台 Token 生命周期和 XXL 会话联动复用；不暴露 Redis key。
- `WorkflowCapabilityStore` 只定义一次性 checkout ticket、HMAC nonce、短期模型 grant、run撤销墓碑的原子端口；payload绑定用户/session/task/run/runner/分支或analyzer，不包含Python工作流会话、任务、报告和事件。
- `supportaccess` 定义限时排查授权、内存态授权摘要、审计事件/查询与 Repository/Redis store 端口；领域对象禁止包含平台 Token、授权 Token、消息/文件正文和文件路径明文。`UserWorkspaceQueryRepository` 与 `SessionHistoryRepository` 提供按目标用户归因的工作区/会话只读端口，供普通归属校验和受审排查入口共同复用；会话端口保留默认 ACTIVE 方法，并提供排查显式包含 ARCHIVED 的兼容重载。

## 已有模型

- Workspace：`Workspace`、`WorkspaceId`。
- Agent & Skill Hub：`AgentSkillHubModels`、`AgentSkillHubRepository`、`AgentSkillHubPushIndexer`；领域层只表达不可变制品、逻辑资产、提交修订、精确依赖、应用级引用（含待推送/生效/取消待推送状态）和三方更新操作，不依赖压缩/Git/SQL 实现。
- 会话 Workspace 权限：`ConversationWorkspaceAccessAuthorizer` 隔离 runtime 与托管应用/个人 Workspace 权威成员查询；`TrustedWorkspaceResolver` 负责当前节点可信 root/server 解析，两者职责分离。
- Session：`Session`、`SessionId`、`SessionStatus`、`SessionMessage`、`SessionMessageId`、`SessionMessageRole`；`Session` 内含平台置顶状态和后端内部 opencode session/node 映射字段，软删除使用 `ARCHIVED` 状态。
- 会话运行态摘要：`SessionRuntimeState`、`SessionRuntimeStateSummary`、`SessionRuntimeAttention`；attention 支持 `QUESTION/PERMISSION`，摘要分别提供 `questionCount/permissionCount`，计数均表示存在对应待关注状态的会话数。
- AgentSessionBinding：`AgentSessionBinding`、`AgentSessionBindingRepository`；按 `(sessionId, agentId)` 表达平台 session 到远端 agent session/node 的通用绑定，旧 opencode 字段只作兼容。
- Run：`Run`、`RunId`、`RunStatus`、`TokenUsage`；Run 可保存单次对话 token/cost 快照。`RunRepository.saveIfStatus` 提供按当前状态条件保存语义，用于终态事件与异步 transport error 并发到达时避免旧快照覆盖已落库终态；`Run.applyTerminalFact` 只接受 root `run.succeeded/run.failed/run.cancelled` 等终态事实，用于以后到 root 终态纠正先到的 transport error 临时失败。
- Run 运行数据面：`RunStorageMode`、`RunRuntimeManifest`、`RunRuntimeInput`、`RunRuntimeSnapshot`、`RunRuntimeReplay`、`RunRuntimeStreamEvent`、`RunRuntimeTail`、`RunOwnerLease`、`RunTerminalProjectionPending`、`RunRuntimeStore`；领域端口定义 manifest、可信工作区/节点快照、durable seq 回放、durable/transient `runtimeVersion` 有序尾部、物化快照、scope、去重、pending、active/服务器恢复索引、状态 CAS 和带 fencing token 的 owner lease。终态事件在同 Run 原子边界发布带 version 的关系型投影 outbox，端口提供按服务器查询和 version CAS ack，避免 Redis 已终态而 PostgreSQL 锚点永久停在 `RUNNING`。恢复、超时或取消接管使用 `claimOwnerLeaseIfUnchanged` 原子校验活跃 manifest 扫描快照并提升 token，事件、远端 Session 绑定及 scope/dedup/pending 写入口提供强制 fenced 重载，不暴露 Redis key、Lua 或序列化细节。`RunDetailsLocator` 只承载恢复/显式 Diff 低频动作所需的 `dispatchMessageId`、远端 ID 和详情到期时间，禁止加入原文。`LEGACY_FULL` 保持旧数据库事实源，`REDIS_SUMMARY` 的运行中详情只允许通过 Redis 实现承载，禁止自动回退 PostgreSQL 或 JVM 内存。
- Run 摘要控制面：`RunPersistenceAnchor`、`RunTerminalProjection`、`RunConversationSummary`、`RunDetailsLocator`、`RunDiffCounts`、`RunSummaryStatus`、`RunSummaryPersistencePort`；启动锚点与低频定位对象禁止包含 prompt、回答、parts 或原始事件。Redis 摘要 Run 与 legacy Scheduled Run 共用 `(sessionId, clientRequestId)` 唯一锚点；legacy 锚点允许启动时尚未知的路由摘要字段为空，并用 Scheduled dispatch attempt、租约和 durable handoff 时间区分“仅锚点”与“Run 已受理”。端口的 claim/mark SQL 必须按 attempt fencing，旧 JVM 无权提交或完成新认领。新锚点只允许活动状态写入，但同一行被终态更新后仍可按幂等键读取终态快照，供响应丢失与夜间补偿恢复稳定 runId。终态投影最多包含 USER/ASSISTANT 各一条定长摘要并以 statusVersion CAS 写入。`RunTerminalRetry`、`RunTerminalRetryState` 和 `RunTerminalRetryStore` 只表达已清洗终态投影的 Redis 待落库状态，并关联可空的终态 outbox version；重试 APPLIED/版本冲突后只确认同一 version，旧执行者不得删除晚到的新终态。退避固定为 5 秒、15 秒、30 秒、1 分钟、2 分钟、5 分钟后封顶 5 分钟；未来的 Run 详情期限是更早上限，原始详情已丢失时安全投影仍可独立保留最多 24 小时。
- 会话运行上下文：`ConversationContextStore`、`ConversationContextIssueLease`、Session revoke 凭证及 user/workspace mutation 凭证定义签发 fence、生命周期撤销和跨 Redis/关系型写入窗口的 fail-closed gate；基础设施 key/Lua 不进入领域层。
- ConversationRunContext：`ConversationRunContext` 保存认证用户、agent、完整用户进程、Linux 服务器，以及完整的 Session、Workspace、ExecutionNode 和可空 AgentSessionBinding 服务端快照；`ConversationContextStore` 定义签发租约、代次 CAS 保存、路由只读解析、校验后原子续期、Session revoke gate，以及按用户+Session、用户、Session、Workspace、进程和全局代次失效的领域端口，不暴露 Redis key、Lua 或序列化细节。`TrustedWorkspaceResolver` 负责在可访问真实路径的当前节点安全解析或回填历史 Workspace 服务器归属。
- User：`User.renameUsername` 规范化并手工修正用户名，保留统一认证号、权限及业务关联；`User.refreshExternalProfile` 只刷新外部身份源提供的姓名、研发部门和部门，保留 `userId`、统一认证号、组织、密码、状态与创建时间；`UserManagementQuery` / `UserManagementQueryRepository` 定义角色（含未分配）、组织和部门组合分页检索，以及按同一筛选返回有界用户 ID 并排除当前操作者的端口；`UserDeletionRepository` 定义批量锁定、受保护业务引用检查和账号附属数据删除端口，不把表结构泄露给业务层。
- RunEvent：`RunEvent`、`RunEventDraft`、`RunEventId`、`RunEventType`、`RunEventScopeContext`；RunEventRepository 支持按 Run 回放和按 root session 回放历史状态事件。RunEventType 覆盖基础 `run.*`（含 transient `run.snapshot.reset`）、旁路 `side_question.*`、`tool.*`、`diff.*`、`session.*` 事件以及 Web App 的 `message.*`、`permission.*`、`question.*`、`todo.updated`、`vcs.branch.updated`、`lsp.updated`、`mcp.tools.changed`、`reference.updated`、`file.edited`、`file.watcher.updated`；`ConversationSourceType.SIDE_QUESTION` 用于归档内部 Session 和旁路 Run。
- RunSessionScope：`RunSessionScope`、`RunSessionScopeSession`、`RunSessionScopeRepository`；表达当前 Run root/child opencode session scope，root metadata 可保存与平台 USER/远端 command 一致的 `dispatchMessageId` 因果锚点。`LEGACY_FULL` 继续以数据库作为恢复事实源，`REDIS_SUMMARY` 只使用订阅级已知 session 状态和 `RunRuntimeStore`，不读写 scope 表。
- ExecutionNode：`ExecutionNode`、`ExecutionNodeId`、`ExecutionNodeStatus`。
- RoutingDecision：`RoutingDecision`、`RoutingReason`、`ExecutionNodeRouter`。
- OpencodeProcess：`LinuxServer`、`BackendJavaProcess`、`BackendRuntimeSnapshot`、`BackendRuntimeMetrics`、`ServerRuntimeMetricSample`、`BackendRuntimeMetricSample`、`OpencodeContainer`、`OpencodeContainerManager`、`ManagerRuntimeSnapshot`、`ManagedOpencodeProcessSnapshot`、`OpencodeManagerBackendConnection`、`OpencodeServerProcess`、`OpencodeServerProcessFilter`、`UserOpencodeProcessBinding`、`OpencodeProcessManagementRepository`、`OpencodeProcessReservationLockPort`、`OpencodeProcessAtomicMutationPort` 和 `OpencodeProcessHeartbeatStore`；只表达 Linux 服务器、容器、管理进程、用户专属 opencode 进程拓扑、Redis 运行快照、查询筛选、事务预留/代次 CAS、服务器级/Java 进程/JVM/容器指标样本和运行心跳端口，不直接发起进程操作或 socket 通信。`OpencodeProcessAssignmentConflictException` 表达 process/binding 原子写竞争，调用方必须重读权威绑定而不能继续覆盖。后端运行指标按可空字段兼容扩展，`memoryMaxBytes` 是 `memoryTotalBytes` 旧别名，`jvmGcPauseMillis` 是 `jvmGcCollectionTimeDeltaMillis` 旧别名。
- Configuration：`ApplicationDefinition`、`ApplicationMember`、`CodeRepository`（含可空 `englishName`）、`ApplicationRepositoryLink`、`ApplicationWorkspace`、`UserSshKey`、`CommonParameter`、`CommonParameterReferenceResolver`、`CommonParameterMemoryEntry`、`CommonParameterMemoryKey/State`、`WorkspaceCreateOperation`、`InternalModelProvider`、`InternalModelToken`、`InternalModelProviderRuntimeConfig` 与公共配置发布状态模型；`PublicAgentConfigRolloutTargetStatus` 只表达未排空目标的内部用户、进程身份、重试和安全错误诊断，不承载统一认证号或 Session 内容。配置领域与运行态 Workspace/Session/Run 解耦；内部模型 Token 领域响应只含安全元数据，明文仅通过运行配置端口进入 JVM 快照。通用参数支持 `${englishName}` 互相引用，`${NAME}` 未命中通用参数时回退进程环境变量，`$NAME` 直接读取环境变量，并在路径开头支持 `$HOME` / `~/` 展开为用户主目录。内存参数 SPI 只描述显式注册项的查库重载契约和安全诊断状态，不把普通通用参数改为缓存读取。
- ManagedWorkspace：`ApplicationWorkspaceVersion`、`ApplicationWorkspaceVersionReplica`、`PersonalWorkspace`、`UserWorkspacePreference`、`WorkspaceSyncRecord`，把应用工作空间模板落为运行态 Workspace，记录每服务器副本 commit/status、个人 worktree 与同步审计。
- AppSource：`AppSourceOperationId` 统一物化、重试、查询、ticket 和 WebSocket 的 1–128 字符可路由标识；首尾规范化显式固定为 ECMAScript WhiteSpace + LineTerminator 集合（包括 ASCII 空白、NBSP、Unicode Zs、行分隔符和 BOM），不依赖 locale 或 Java `trim/strip` 差异。不限定业务前缀并拒绝控制字符、路径分隔符及规范化后精确的 `.`/`..` 路径段，普通内部双点如 `release..1` 仍合法；`AppSourceRepositorySlot` 以 repositoryId 唯一分配 active/pending generation 并通过 `lockVersion` 乐观并发；`AppSourceSnapshot` 冻结仓库英文名、`PERSONAL/TEAM` 用途、分支、提交和结构化路径选择，并严格校验 `expiresAt = acceptedAt + 1..168` 整小时，续期只改变相对首次受理时间的总保留期；`AppSourceReplica`、`AppSourceOperation`、全局/服务器步骤、绝对 `deleteAt` 清理任务和每用户 recent selection 分别表达 generation/lease fencing、操作进度、延迟删除和最近入口。`AppSourceRepository` 还定义精确副本活租约下的步骤推进/attempt 重置、共享槽位行锁、保留期与 cleanup 计划同步更新，以及全副本终态但操作未终结的有界恢复扫描；领域端口仍不暴露 JSONB、SQL 或物理目录。`AppSourceRetention` 只接受 1–168 小时且默认 48 小时。
- AppSource 副本认领必须原子绑定精确 operationId、非终态 operation 和同服务器时间线；普通 `PENDING/FAILED/STALE` 副本还必须存在 `PENDING/RUNNING` 步骤，过期 `RUNNING` 副本则以自身旧 attempt 作为恢复锚点，认领后统一重置整条步骤时间线。
- Broadcast：`ServerBroadcastEvent`、`ServerBroadcastPublisher`、`ServerBroadcastHandler`，定义后端实例之间广播事件的领域端口，不绑定 Redis 或其他传输。
- Scheduler：`ScheduledTask`、`ScheduledTaskPlan`、`ScheduledTaskRun`、状态枚举和值对象只保留旧数据兼容和运行记录清理端口；生产调度不再创建或执行 `USER_PLAN`。
- NightExecution：`NightExecutionTask`、`NightExecutionScheduleMode`、`NightExecutionTaskStatus`、`NightExecutionTaskRepository` 表达任务状态机、完整输入短期持有、固定目标服务器、会话锁、15 分钟时段容量以及 attempt/owner/租约 fencing；`NIGHT_WINDOW` 预留夜间容量，`ADMIN_CUSTOM` 使用精确分钟且不产生容量释放标记。`SCHEDULED/DISPATCHING` 为待执行，普通 Run 锚点受理后进入 `DISPATCHED`，Run 后续终态不反向修改调度状态。
- Analytics：`AiRunFeedback` 是新反馈事实，按 `(userId, runId)` 定位整轮回复；`AiMessageFeedback` 仅保留旧消息兼容。反馈评分/原因枚举、`AnalyticsModels` 和 `AnalyticsRepository` 不暴露 prompt/assistant 原文或 cost 字段。
- Repository 端口：Workspace、Session、SessionTitleUpdate、AgentSessionBinding、SessionMessage、Run、RunEvent、RunSessionScope、ExecutionNode、RoutingDecision、UserManagementQuery、UserDeletion、OpencodeProcessManagement、ConfigurationManagement、CommonParameter、InternalModelProvider、InternalModelToken、WorkspaceCreateOperation、ManagedWorkspace、ScheduledTask、ScheduledTaskRunRetention、NightExecutionTask、AiMessageFeedback、Analytics 持久化端口。`InternalModelProviderRepository.findEnabledRuntimeConfigs()` 定义一次联表运行快照读取，Token 明文不得进入普通 Provider 或 Token 元数据响应。`SessionTitleUpdateRepository` 仅在当前标题与预期临时标题一致时更新，用于避免异步标题覆盖原生或人工标题；RunRepository 的条件保存端口要求成功时返回本次快照，条件不匹配时返回数据库当前 Run。
- `RunRepository.findStaleActiveSideQuestionRuns` 只查询有上限的 stale active `SIDE_QUESTION` Run，供旁路临时会话孤儿回收，不影响普通 stale Run 收敛查询。

## Run 状态机

- `PENDING -> RUNNING|CANCELLED|FAILED`。
- `RUNNING -> CANCELLING|SUCCEEDED|FAILED`。
- `CANCELLING -> CANCELLED|FAILED`。
- `SUCCEEDED`、`FAILED`、`CANCELLED` 为终态。
- pending Run 收到取消请求时直接进入 `CANCELLED`。
- 普通领域状态机不允许终态继续流转；但 root RunEvent 终态是远端事实源，应用层可通过 `applyTerminalFact` 记录后到终态事实，以支持 `Streaming response failed` 等 transport error 先到、root 成功/失败后到时按最后 root 终态校正 Run 结果。

## 测试覆盖

- `WorkspaceTest` 覆盖工作区默认状态、traceId 占位和更新时间边界。
- `RunStatusTest`、`RunTest` 覆盖 Run 状态机、终态、取消请求、非法流转、时间边界和 token/cost 快照兼容。
- 终态摘要领域对象由 runtime/persistence 集成测试覆盖角色唯一性、两条上限、Unicode 长度、状态版本和无原文 SQL 边界；`RunTerminalRetryTest` 覆盖严格退避、5 分钟封顶和 24 小时保留边界。
- `ConversationRunContextTest` 覆盖 Session/Workspace/ExecutionNode/AgentSessionBinding 快照一致性、agent 规范化、可空远端 session、版本和滑动过期副本边界。
- `SessionMessageTest`、`SessionTest` 覆盖消息约束、parts/token/cost 可选快照、会话归档、置顶和内部 opencode session/node 映射边界。
- `AgentSessionBindingTest` 覆盖 agentId 规范化、远端 session/node 绑定和 traceId 边界。
- `ExecutionNodeRouterTest`、`ExecutionNodeTest` 覆盖执行节点容量、可路由状态和路由冲突错误。
- `OpencodeProcessDomainTest` 覆盖稳定 Linux 服务器身份、容器端口范围、用户进程 baseUrl 和用户绑定边界。
- `RunEventTest`、`RunEventTypeTest`、`DomainValidationTest` 覆盖事件模型、事件 wireName 映射和值对象公共校验。
- `ConfigurationDomainTest`、`CommonParameterReferenceResolverTest` 覆盖应用成员逻辑删除、代码库 URL 不可编辑、英文名称兼容、应用工作空间目录约束、通用参数互相引用、环境变量回退和 `$HOME` 路径展开等配置领域规则。
- `AppSourceDomainTest` 覆盖用途、状态前向流转、结构化安全相对路径、1–168 小时边界、默认 48 小时和 generation/乐观版本/租约 fencing；`ManagedWorkspacePathResolverTest` 覆盖 `appsource:` 逻辑路径及旧物理路径兼容。
- `SchedulerDomainTest` 覆盖任务定义、用户计划、运行记录状态和会话来源默认值。

## 允许依赖

- `test-agent-common`。
- JDK 标准库。

## 禁止依赖

- Spring Web。
- JPA、JDBC、Redis、Flyway。
- generated SDK。
- `test-agent-app`。

## 后续 AI 编码指引

新增业务概念、状态枚举、领域命令和值对象时改这里；如果需要访问数据库、HTTP 或 opencode server，应定义接口或模型后交给其他模块实现。
Repository 端口只定义在 domain，具体 JDBC/Flyway 实现必须放在 `test-agent-persistence`。
平台 Session ID 与远端 agent Session ID 不可混用；需要调用 agent 时应通过 domain 端口读取 `AgentSessionBinding`，并由业务模块选择 `AgentRuntime` 完成协议转换。

## LobeHub 与模型网关领域端口

- `LobehubSsoStore` 只表达一次性 ticket、nonce 和单用户模型 grant 的原子保存/消费/轮换/撤销；payload 只保存
  用户、scope、client 和过期时间，不暴露 Redis key、Lua 或原始 opaque 值。
- `InternalModelProviderModelRepository` 管理供应商公开模型、声明能力和最近探测结果；公开 `modelId` 跨供应商
  唯一，`upstreamModelId` 只供模型网关解析。
- `ModelGatewayUsageDailyRepository` 接受无正文的 `ModelGatewayUsageDelta`，只按稳定聚合维度累加计数、token
  和耗时。

LobeHub 自身用户、Session、部门 Workspace、资源与审计是独立 fork 的领域，不在本模块建模。
