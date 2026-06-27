# test-agent-domain

## 工程定位

纯领域模型模块，表达测试智能体平台的核心业务概念和状态规则。

## 技术栈

- Java 21
- Maven library jar
- 依赖 `test-agent-common`

## 主要职责

- Workspace、Session、AgentSessionBinding、Run、RunEvent、ExecutionNode、RoutingDecision、opencode 用户进程管理拓扑、应用配置管理、应用版本工作区、应用版本服务器副本、个人工作区、服务器广播和定时任务框架等领域对象。
- Run 状态机、路由决策值对象、领域服务接口。
- 保持业务规则与基础设施分离。

## 已有模型

- Workspace：`Workspace`、`WorkspaceId`。
- Session：`Session`、`SessionId`、`SessionStatus`、`SessionMessage`、`SessionMessageId`、`SessionMessageRole`；`Session` 内含平台置顶状态和后端内部 opencode session/node 映射字段，软删除使用 `ARCHIVED` 状态。
- AgentSessionBinding：`AgentSessionBinding`、`AgentSessionBindingRepository`；按 `(sessionId, agentId)` 表达平台 session 到远端 agent session/node 的通用绑定，旧 opencode 字段只作兼容。
- Run：`Run`、`RunId`、`RunStatus`、`TokenUsage`；Run 可保存单次对话 token/cost 快照。
- RunEvent：`RunEvent`、`RunEventDraft`、`RunEventId`、`RunEventType`；RunEventType 覆盖基础 `run.*`、`tool.*`、`diff.*` 事件以及 Web App 的 `message.*`、`permission.*`、`question.*`、`todo.updated`、`vcs.branch.updated`、`lsp.updated`、`mcp.tools.changed`。
- ExecutionNode：`ExecutionNode`、`ExecutionNodeId`、`ExecutionNodeStatus`。
- RoutingDecision：`RoutingDecision`、`RoutingReason`、`ExecutionNodeRouter`。
- OpencodeProcess：`LinuxServer`、`BackendJavaProcess`、`BackendRuntimeSnapshot`、`ServerRuntimeMetricSample`、`OpencodeContainer`、`OpencodeContainerManager`、`ManagerRuntimeSnapshot`、`OpencodeManagerBackendConnection`、`OpencodeServerProcess`、`OpencodeServerProcessFilter`、`UserOpencodeProcessBinding`、`OpencodeProcessManagementRepository` 和 `OpencodeProcessHeartbeatStore`；只表达 Linux 服务器、容器、管理进程、用户专属 opencode 进程拓扑、Redis 运行快照、查询筛选、服务器级/JVM/容器指标样本和运行心跳端口，不直接发起进程操作或 socket 通信。
- Configuration：`ApplicationDefinition`、`ApplicationMember`、`CodeRepository`（含可空 `englishName`）、`ApplicationRepositoryLink`、`ApplicationWorkspace`、`UserSshKey`、`CommonParameter`、`WorkspaceCreateOperation`，与运行态 Workspace/Session/Run 解耦。
- ManagedWorkspace：`ApplicationWorkspaceVersion`、`ApplicationWorkspaceVersionReplica`、`PersonalWorkspace`、`UserWorkspacePreference`、`WorkspaceSyncRecord`，把应用工作空间模板落为运行态 Workspace，记录每服务器副本 commit/status、个人 worktree 与同步审计。
- Broadcast：`ServerBroadcastEvent`、`ServerBroadcastPublisher`、`ServerBroadcastHandler`，定义后端实例之间广播事件的领域端口，不绑定 Redis 或其他传输。
- Scheduler：`ScheduledTask`、`ScheduledTaskPlan`、`ScheduledTaskRun`、状态枚举和值对象；用户级计划仅作为后续定时会话能力预留。
- Repository 端口：Workspace、Session、AgentSessionBinding、SessionMessage、Run、RunEvent、ExecutionNode、RoutingDecision、OpencodeProcessManagement、ConfigurationManagement、CommonParameter、WorkspaceCreateOperation、ManagedWorkspace、ScheduledTask 持久化端口。

## Run 状态机

- `PENDING -> RUNNING|CANCELLED|FAILED`。
- `RUNNING -> CANCELLING|SUCCEEDED|FAILED`。
- `CANCELLING -> CANCELLED|FAILED`。
- `SUCCEEDED`、`FAILED`、`CANCELLED` 为终态。
- pending Run 收到取消请求时直接进入 `CANCELLED`。

## 测试覆盖

- `WorkspaceTest` 覆盖工作区默认状态、traceId 占位和更新时间边界。
- `RunStatusTest`、`RunTest` 覆盖 Run 状态机、终态、取消请求、非法流转、时间边界和 token/cost 快照兼容。
- `SessionMessageTest`、`SessionTest` 覆盖消息约束、parts/token/cost 可选快照、会话归档、置顶和内部 opencode session/node 映射边界。
- `AgentSessionBindingTest` 覆盖 agentId 规范化、远端 session/node 绑定和 traceId 边界。
- `ExecutionNodeRouterTest`、`ExecutionNodeTest` 覆盖执行节点容量、可路由状态和路由冲突错误。
- `OpencodeProcessDomainTest` 覆盖 Linux 服务器 IP、容器端口范围、用户进程 baseUrl 和用户绑定边界。
- `RunEventTest`、`RunEventTypeTest`、`DomainValidationTest` 覆盖事件模型、事件 wireName 映射和值对象公共校验。
- `ConfigurationDomainTest` 覆盖应用成员逻辑删除、代码库 URL 不可编辑、英文名称兼容、应用工作空间目录约束等配置领域规则。
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
