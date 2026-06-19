# test-agent-domain

## 工程定位

纯领域模型模块，表达测试智能体平台的核心业务概念和状态规则。

## 技术栈

- Java 21
- Maven library jar
- 依赖 `test-agent-common`

## 主要职责

- Workspace、Session、Run、RunEvent、ExecutionNode、RoutingDecision 等领域对象。
- Run 状态机、路由决策值对象、领域服务接口。
- 保持业务规则与基础设施分离。

## 已有模型

- Workspace：`Workspace`、`WorkspaceId`。
- Session：`Session`、`SessionId`、`SessionMessage`、`SessionMessageId`、`SessionMessageRole`；`Session` 内含后端内部 opencode session/node 映射字段，不对前端 API 暴露。
- Run：`Run`、`RunId`、`RunStatus`。
- RunEvent：`RunEvent`、`RunEventDraft`、`RunEventId`、`RunEventType`；RunEventType 覆盖基础 `run.*`、`tool.*`、`diff.*` 事件以及 Phase 11 Web App 的 `message.*`、`permission.*`、`question.*`、`todo.updated`、`vcs.branch.updated`、`lsp.updated`、`mcp.tools.changed`。
- ExecutionNode：`ExecutionNode`、`ExecutionNodeId`、`ExecutionNodeStatus`。
- RoutingDecision：`RoutingDecision`、`RoutingReason`、`ExecutionNodeRouter`。
- Repository 端口：Workspace、Session、SessionMessage、Run、RunEvent、ExecutionNode、RoutingDecision 持久化端口。

## Run 状态机

- `PENDING -> RUNNING|CANCELLED|FAILED`。
- `RUNNING -> CANCELLING|SUCCEEDED|FAILED`。
- `CANCELLING -> CANCELLED|FAILED`。
- `SUCCEEDED`、`FAILED`、`CANCELLED` 为终态。
- pending Run 收到取消请求时直接进入 `CANCELLED`。

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
平台 Session ID 与远端 opencode Session ID 不可混用；需要调用 opencode 时应通过 domain 端口读取内部映射，并由 `test-agent-opencode-client` 完成协议转换。
