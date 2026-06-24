# 包说明：com.icbc.testagent.domain

## 职责

纯领域模型包，表达 Workspace、Session、AgentSessionBinding、Run、RunEvent、ExecutionNode、RoutingDecision、opencode 用户进程管理拓扑等核心业务概念和状态规则。

## 不负责

- 不处理 HTTP 请求和响应。
- 不访问数据库、Redis、文件系统或 opencode server。
- 不依赖 Spring Web、JPA、JDBC、Flyway。

## 主要程序清单

- `package-info.java`：说明 domain 包是纯领域模型和状态机边界。
- `workspace.Workspace`、`workspace.WorkspaceId`、`workspace.WorkspaceStatus`、`workspace.WorkspaceRepository`：工作区领域对象和值对象、持久化端口。
- `session.Session`、`session.SessionId`、`session.SessionStatus`、`session.SessionRepository`：会话领域对象和值对象、持久化端口；Session 保存平台置顶状态和后端内部 opencode session/node 映射，软删除使用 `ARCHIVED` 状态。
- `agent.AgentSessionBinding`、`agent.AgentSessionBindingRepository`：平台 session 到远端 agent session/node 的通用绑定模型和持久化端口。
- `session.SessionMessage`、`session.SessionMessageId`、`session.SessionMessageRole`、`session.SessionMessageRepository`：会话消息领域对象、角色和值对象、持久化端口。
- `run.Run`、`run.RunId`、`run.RunStatus`、`run.RunRepository`：运行聚合和值对象、状态机、持久化端口。
- `event.RunEvent`、`event.RunEventDraft`、`event.RunEventId`、`event.RunEventType`、`event.RunEventRepository`：平台运行事件模型和 append-only 端口；RunEventType 覆盖基础 `run.*`、`tool.*`、`diff.*` 事件以及 Web App 的 `message.*`、`permission.*`、`question.*`、`todo.updated`、`vcs.branch.updated`、`lsp.updated`、`mcp.tools.changed` 等运行态事件。
- `node.ExecutionNode`、`node.ExecutionNodeId`、`node.ExecutionNodeStatus`、`node.ExecutionNodeRepository`：执行节点模型和查询端口。
- `routing.RoutingDecision`、`routing.RoutingReason`、`routing.ExecutionNodeRouter`、`routing.RoutingDecisionRepository`：路由决策值对象、纯路由策略和持久化端口。
- `opencodeprocess.*`：Linux 服务器、后端 Java 进程、opencode 容器、容器管理进程、管理进程连接、用户专属 opencode server 进程和用户绑定模型；`OpencodeProcessManagementRepository` 作为持久化端口。
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
- Workspace、Session、AgentSessionBinding、Run、RunEvent、ExecutionNode、RoutingDecision、opencode 用户进程管理拓扑等值对象约束必须覆盖成功和失败场景。
- 状态机、路由决策、通用 agent binding 和内部 opencode session/node 兼容映射必须覆盖成功和冲突场景。
- Repository 端口不直接测试数据库，实现测试放在 persistence 模块。

## 修改时必须同步更新

- `backend/test-agent-domain/README.md`。
- 本文件。
- `docs/architecture/dependency-rules.md`，如果领域边界变化。
- `docs/api/http-api.md` 或 `docs/api/event-stream.md`，如果领域字段对外暴露。
