# test-agent-domain

## 工程定位

纯领域模型模块，表达测试智能体平台的核心业务概念和状态规则。

## 技术栈

- Java 21
- Maven library jar
- 依赖 `test-agent-common`

## 主要职责

- Workspace、Session、AgentSessionBinding、Run、ConversationRunContext、Run 运行数据面、RunEvent、ExecutionNode、RoutingDecision、opencode 用户进程管理拓扑、夜间执行任务、AI 回复反馈、运营分析、应用配置管理、应用版本工作区、应用版本服务器副本、个人工作区、服务器广播和定时任务框架等领域对象。
- Run 状态机、路由决策值对象、领域服务接口。
- 保持业务规则与基础设施分离。
- 认证领域端口 `TokenSessionMarkerStore` 只定义平台 Token 的 SHA-256 session marker 写入、删除、校验与摘要规则，供平台 Token 生命周期和 XXL 会话联动复用；不暴露 Redis key。

## 已有模型

- Workspace：`Workspace`、`WorkspaceId`。
- 会话 Workspace 权限：`ConversationWorkspaceAccessAuthorizer` 隔离 runtime 与托管应用/个人 Workspace 权威成员查询；`TrustedWorkspaceResolver` 负责当前节点可信 root/server 解析，两者职责分离。
- Session：`Session`、`SessionId`、`SessionStatus`、`SessionMessage`、`SessionMessageId`、`SessionMessageRole`；`Session` 内含平台置顶状态和后端内部 opencode session/node 映射字段，软删除使用 `ARCHIVED` 状态。
- AgentSessionBinding：`AgentSessionBinding`、`AgentSessionBindingRepository`；按 `(sessionId, agentId)` 表达平台 session 到远端 agent session/node 的通用绑定，旧 opencode 字段只作兼容。
- Run：`Run`、`RunId`、`RunStatus`、`TokenUsage`；Run 可保存单次对话 token/cost 快照。`RunRepository.saveIfStatus` 提供按当前状态条件保存语义，用于终态事件与异步 transport error 并发到达时避免旧快照覆盖已落库终态；`Run.applyTerminalFact` 只接受 root `run.succeeded/run.failed/run.cancelled` 等终态事实，用于以后到 root 终态纠正先到的 transport error 临时失败。
- Run 运行数据面：`RunStorageMode`、`RunRuntimeManifest`、`RunRuntimeInput`、`RunRuntimeSnapshot`、`RunRuntimeReplay`、`RunRuntimeStreamEvent`、`RunRuntimeTail`、`RunOwnerLease`、`RunTerminalProjectionPending`、`RunRuntimeStore`；领域端口定义 manifest、可信工作区/节点快照、durable seq 回放、durable/transient `runtimeVersion` 有序尾部、物化快照、scope、去重、pending、active/服务器恢复索引、状态 CAS 和带 fencing token 的 owner lease。终态事件在同 Run 原子边界发布带 version 的关系型投影 outbox，端口提供按服务器查询和 version CAS ack，避免 Redis 已终态而 PostgreSQL 锚点永久停在 `RUNNING`。恢复、超时或取消接管使用 `claimOwnerLeaseIfUnchanged` 原子校验活跃 manifest 扫描快照并提升 token，事件、远端 Session 绑定及 scope/dedup/pending 写入口提供强制 fenced 重载，不暴露 Redis key、Lua 或序列化细节。`RunDetailsLocator` 只承载恢复/显式 Diff 低频动作所需的 `dispatchMessageId`、远端 ID 和详情到期时间，禁止加入原文。`LEGACY_FULL` 保持旧数据库事实源，`REDIS_SUMMARY` 的运行中详情只允许通过 Redis 实现承载，禁止自动回退 PostgreSQL 或 JVM 内存。
- Run 摘要控制面：`RunPersistenceAnchor`、`RunTerminalProjection`、`RunConversationSummary`、`RunDetailsLocator`、`RunDiffCounts`、`RunSummaryStatus`、`RunSummaryPersistencePort`；启动锚点与低频定位对象禁止包含 prompt、回答、parts 或原始事件，终态投影最多包含 USER/ASSISTANT 各一条定长摘要并以 statusVersion CAS 写入。`RunTerminalRetry`、`RunTerminalRetryState` 和 `RunTerminalRetryStore` 只表达已清洗终态投影的 Redis 待落库状态，并关联可空的终态 outbox version；重试 APPLIED/版本冲突后只确认同一 version，旧执行者不得删除晚到的新终态。退避固定为 5 秒、15 秒、30 秒、1 分钟、2 分钟、5 分钟后封顶 5 分钟；未来的 Run 详情期限是更早上限，原始详情已丢失时安全投影仍可独立保留最多 24 小时。
- 会话运行上下文：`ConversationContextStore`、`ConversationContextIssueLease`、Session revoke 凭证及 user/workspace mutation 凭证定义签发 fence、生命周期撤销和跨 Redis/关系型写入窗口的 fail-closed gate；基础设施 key/Lua 不进入领域层。
- ConversationRunContext：`ConversationRunContext` 保存认证用户、agent、完整用户进程、Linux 服务器，以及完整的 Session、Workspace、ExecutionNode 和可空 AgentSessionBinding 服务端快照；`ConversationContextStore` 定义签发租约、代次 CAS 保存、路由只读解析、校验后原子续期、Session revoke gate，以及按用户+Session、用户、Session、Workspace、进程和全局代次失效的领域端口，不暴露 Redis key、Lua 或序列化细节。`TrustedWorkspaceResolver` 负责在可访问真实路径的当前节点安全解析或回填历史 Workspace 服务器归属。
- User：`User.refreshExternalProfile` 只刷新外部身份源提供的姓名、研发部门和部门，保留 `userId`、统一认证号、组织、密码、状态与创建时间；`UserDeletionRepository` 定义批量锁定、受保护业务引用检查和账号附属数据删除端口，不把表结构泄露给业务层。
- RunEvent：`RunEvent`、`RunEventDraft`、`RunEventId`、`RunEventType`、`RunEventScopeContext`；RunEventRepository 支持按 Run 回放和按 root session 回放历史状态事件。RunEventType 覆盖基础 `run.*`（含 transient `run.snapshot.reset`）、旁路 `side_question.*`、`tool.*`、`diff.*`、`session.*` 事件以及 Web App 的 `message.*`、`permission.*`、`question.*`、`todo.updated`、`vcs.branch.updated`、`lsp.updated`、`mcp.tools.changed`、`reference.updated`、`file.edited`、`file.watcher.updated`；`ConversationSourceType.SIDE_QUESTION` 用于归档内部 Session 和旁路 Run。
- RunSessionScope：`RunSessionScope`、`RunSessionScopeSession`、`RunSessionScopeRepository`；表达当前 Run root/child opencode session scope，root metadata 可保存与平台 USER/远端 command 一致的 `dispatchMessageId` 因果锚点。`LEGACY_FULL` 继续以数据库作为恢复事实源，`REDIS_SUMMARY` 只使用订阅级已知 session 状态和 `RunRuntimeStore`，不读写 scope 表。
- ExecutionNode：`ExecutionNode`、`ExecutionNodeId`、`ExecutionNodeStatus`。
- RoutingDecision：`RoutingDecision`、`RoutingReason`、`ExecutionNodeRouter`。
- OpencodeProcess：`LinuxServer`、`BackendJavaProcess`、`BackendRuntimeSnapshot`、`BackendRuntimeMetrics`、`ServerRuntimeMetricSample`、`BackendRuntimeMetricSample`、`OpencodeContainer`、`OpencodeContainerManager`、`ManagerRuntimeSnapshot`、`OpencodeManagerBackendConnection`、`OpencodeServerProcess`、`OpencodeServerProcessFilter`、`UserOpencodeProcessBinding`、`OpencodeProcessManagementRepository` 和 `OpencodeProcessHeartbeatStore`；只表达 Linux 服务器、容器、管理进程、用户专属 opencode 进程拓扑、Redis 运行快照、查询筛选、服务器级/Java 进程/JVM/容器指标样本和运行心跳端口，不直接发起进程操作或 socket 通信。后端运行指标按可空字段兼容扩展，`memoryMaxBytes` 是 `memoryTotalBytes` 旧别名，`jvmGcPauseMillis` 是 `jvmGcCollectionTimeDeltaMillis` 旧别名。
- Configuration：`ApplicationDefinition`、`ApplicationMember`、`CodeRepository`（含可空 `englishName`）、`ApplicationRepositoryLink`、`ApplicationWorkspace`、`UserSshKey`、`CommonParameter`、`CommonParameterReferenceResolver`、`CommonParameterMemoryEntry`、`CommonParameterMemoryKey/State`、`WorkspaceCreateOperation`，与运行态 Workspace/Session/Run 解耦；通用参数支持 `${englishName}` 互相引用，`${NAME}` 未命中通用参数时回退进程环境变量，`$NAME` 直接读取环境变量，并在路径开头支持 `$HOME` / `~/` 展开为用户主目录。内存参数 SPI 只描述显式注册项的查库重载契约和安全诊断状态，不把普通通用参数改为缓存读取。
- ManagedWorkspace：`ApplicationWorkspaceVersion`、`ApplicationWorkspaceVersionReplica`、`PersonalWorkspace`、`UserWorkspacePreference`、`WorkspaceSyncRecord`，把应用工作空间模板落为运行态 Workspace，记录每服务器副本 commit/status、个人 worktree 与同步审计。
- Broadcast：`ServerBroadcastEvent`、`ServerBroadcastPublisher`、`ServerBroadcastHandler`，定义后端实例之间广播事件的领域端口，不绑定 Redis 或其他传输。
- Scheduler：`ScheduledTask`、`ScheduledTaskPlan`、`ScheduledTaskRun`、状态枚举和值对象，以及运行记录保留清理端口；`USER_PLAN` 运行记录携带可空服务器执行亲和并通过状态 CAS 认领。
- NightExecution：`NightExecutionTask`、`NightExecutionTaskStatus`、`NightExecutionTaskRepository` 表达任务状态机、完整输入短期持有、会话锁和 15 分钟时段容量端口；`SCHEDULED/DISPATCHING` 为待执行，成功投递、取消或最终失败后清除完整输入。
- Analytics：`AiRunFeedback` 是新反馈事实，按 `(userId, runId)` 定位整轮回复；`AiMessageFeedback` 仅保留旧消息兼容。反馈评分/原因枚举、`AnalyticsModels` 和 `AnalyticsRepository` 不暴露 prompt/assistant 原文或 cost 字段。
- Repository 端口：Workspace、Session、SessionTitleUpdate、AgentSessionBinding、SessionMessage、Run、RunEvent、RunSessionScope、ExecutionNode、RoutingDecision、UserDeletion、OpencodeProcessManagement、ConfigurationManagement、CommonParameter、WorkspaceCreateOperation、ManagedWorkspace、ScheduledTask、ScheduledTaskRunRetention、NightExecutionTask、AiMessageFeedback、Analytics 持久化端口。`SessionTitleUpdateRepository` 仅在当前标题与预期临时标题一致时更新，用于避免异步标题覆盖原生或人工标题；RunRepository 的条件保存端口要求成功时返回本次快照，条件不匹配时返回数据库当前 Run。
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
