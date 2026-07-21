# 包说明：com.enterprise.testagent.domain

## 职责

纯领域模型包，表达 Workspace、Session、AgentSessionBinding、Run、Run 运行数据面、RunEvent、ExecutionNode、RoutingDecision、opencode 用户进程管理拓扑、夜间执行任务、应用配置、通用参数、工作空间创建进度、定时任务框架等核心业务概念和状态规则。

## 不负责

- 不处理 HTTP 请求和响应。
- 不访问数据库、Redis、文件系统或 opencode server。
- 不依赖 Spring Web、JPA、JDBC、Flyway。

## 主要程序清单

- `package-info.java`：说明 domain 包是纯领域模型和状态机边界。
- `workspace.Workspace`、`workspace.WorkspaceId`、`workspace.WorkspaceStatus`、`workspace.WorkspaceRepository`：工作区领域对象和值对象、持久化端口。
- `session.Session`、`session.SessionId`、`session.SessionStatus`、`session.SessionRepository`：会话领域对象和值对象、持久化端口；Session 保存平台置顶状态和后端内部 opencode session/node 映射，软删除使用 `ARCHIVED` 状态。
- `agent.AgentSessionBinding`、`agent.AgentSessionBindingRepository`：平台 session 到远端 agent session/node 的通用绑定模型和持久化端口。
- `session.SessionMessage`、`session.SessionMessageId`、`session.SessionMessageRole`、`session.SessionMessageRepository`：会话消息领域对象、角色和值对象、持久化端口；消息可携带 runId、远端 messageId、parts_json、token/cost 快照。
- `run.Run`、`run.RunId`、`run.RunStatus`、`run.TokenUsage`、`run.RunRepository`：运行聚合和值对象、状态机、单次 token 消耗和值对象、持久化端口；`RunRepository.saveIfStatus` 用于按当前状态条件保存，防止终态事件与异步 transport error 竞态时旧状态覆盖新终态；`Run.applyTerminalFact` 只记录 root 终态事实，允许后到 root 终态纠正先到 transport error 临时失败。
- `run.RunStorageMode`、`run.RunRuntimeManifest`、`run.RunRuntimeInput`、`run.RunRuntimeSnapshot`、`run.RunRuntimeReplay`、`run.RunRuntimeStreamEvent`、`run.RunRuntimeTail`、`run.RunOwnerLease`、`run.RunTerminalProjectionPending`、`run.RunRuntimeStore`：Run 运行数据面的模式、manifest、可信恢复快照、物化快照、durable seq 回放、全事件尾部、owner lease/fencing 和带 version 的终态投影 outbox 领域端口；条件接管原子校验活跃 manifest 快照并提升 token，事件、远端 Session 绑定及 scope/dedup/pending 写入都提供 fenced 入口，终态 outbox 支持服务器候选查询与 version CAS ack。Redis key、Lua、TTL 实现留在 persistence，`REDIS_SUMMARY` 不允许回退 PostgreSQL 或 JVM 内存。
- `run.RunPersistenceAnchor`、`run.RunTerminalProjection`、`run.RunConversationSummary`、`run.RunDetailsLocator`、`run.RunSummaryPersistencePort`：新模式 PostgreSQL 控制面锚点、终态双摘要和低频远端定位端口；`RunDetailsLocator.dispatchMessageId` 用于 Redis 详情过期后的目标轮因果恢复，对象约束禁止承载原始输入输出、reasoning、工具内容或事件 payload。
- `run.RunTerminalRetry`、`run.RunTerminalRetryState`、`run.RunTerminalRetryStore`：关系型终态事务故障后的 Redis 安全重试模型与端口；只允许保存已清洗投影并固定退避，未来详情期限是更早上限，详情已丢失时安全投影仍不超过 24 小时。
- `run.RunDetailsLocator`、`run.RunDiffAction`、`run.RunSummaryPersistencePort`：新模式 PostgreSQL 非原文控制面端口；只允许读取远端定位 ID、写终态摘要以及为显式 Diff 动作增加聚合计数。
- `event.RunEvent`、`event.RunEventDraft`、`event.RunEventId`、`event.RunEventType`、`event.RunEventRepository`：平台运行事件模型和 append-only 端口，支持按 Run 和按 root session 回放；RunEventType 覆盖基础 `run.*`（含 transient `run.snapshot.reset`）、`tool.*`、`diff.*` 事件以及 Web App 的 `message.*`、`permission.*`、`question.*`、`todo.updated`、`vcs.branch.updated`、`lsp.updated`、`mcp.tools.changed`、`reference.updated`、`file.edited`、`file.watcher.updated` 等运行态事件。
- `node.ExecutionNode`、`node.ExecutionNodeId`、`node.ExecutionNodeStatus`、`node.ExecutionNodeRepository`：执行节点模型和查询端口。
- `routing.RoutingDecision`、`routing.RoutingReason`、`routing.ExecutionNodeRouter`、`routing.RoutingDecisionRepository`：路由决策值对象、纯路由策略和持久化端口。
- `user.User`、`user.UserId`、`user.UserRepository`、`user.UserDeletionRepository`：用户聚合、常规持久化端口和安全删除端口；外部身份资料刷新保持用户业务 ID 与既有关系不变，删除端口负责锁定目标并区分可清理账号附属数据与受保护业务引用。
- `opencodeprocess.*`：Linux 服务器、后端 Java 进程、opencode 容器、容器管理进程、管理进程连接、用户专属 opencode server 进程、查询筛选和用户绑定模型；`OpencodeProcessManagementRepository` 作为持久化端口。
- `configuration.*`：应用定义、应用成员、代码库配置、应用仓库关联、应用工作空间模板、个人 SSH key、通用参数、显式 JVM 内存参数 SPI/状态和设置页工作空间创建进度；`CommonParameterMemoryEntry` 约束实现先完整查库校验再原子替换，未注册参数仍按需直读数据库；`ConfigurationManagementRepository`、`CommonParameterRepository`、`WorkspaceCreateOperationRepository` 作为持久化端口。
- `scheduler.*`：定时任务定义、用户级计划、带服务器执行亲和的运行记录、触发来源和状态枚举；`ScheduledTaskRepository`、`ScheduledTaskRunRetentionRepository` 作为持久化端口。
- `nightexecution.*`：夜间任务聚合、状态和值对象；完整 Run 输入仅在待执行期短期保存，仓储端口统一暴露任务状态 CAS、会话写锁和 15 分钟时段容量。
- 后续可新增领域命令、领域服务接口和更多状态规则。

## 允许依赖

- `test-agent-common`。
- JDK 标准库。

## 禁止依赖

- Spring Web。
- JPA、JDBC、Redis、Flyway。
- generated SDK。
- `test-agent-app`。
- `test-agent-persistence`。

## 上游调用方

- workspace-management、opencode-runtime 等业务模块。
- `test-agent-persistence` 映射实现。
- `test-agent-event` 事件模型。
- `test-agent-opencode-client` 转换后的平台模型。

## 下游依赖

- `test-agent-common`。

## 测试位置

- domain 模块单元测试。
- Workspace、Session、AgentSessionBinding、Run、TokenUsage、RunEvent、ExecutionNode、RoutingDecision、opencode 用户进程管理拓扑、应用配置、通用参数、工作空间创建进度、定时任务等值对象约束必须覆盖成功和失败场景。
- 状态机、路由决策、通用 agent binding 和内部 opencode session/node 兼容映射必须覆盖成功和冲突场景。
- Repository 端口不直接测试数据库，实现测试放在 persistence 模块。

## 修改时必须同步更新

- `backend/test-agent-domain/README.md`。
- 本文件。
- `docs/architecture/dependency-rules.md`，如果领域边界变化。
- `docs/api/http-api.md` 或 `docs/api/event-stream.md`，如果领域字段对外暴露。
