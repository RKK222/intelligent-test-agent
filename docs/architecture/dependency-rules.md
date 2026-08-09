# 分层依赖与访问关系

本文档定义后端和前端的依赖边界。

## 后端依赖方向

允许方向：

```text
test-agent-app
  -> test-agent-api
  -> test-agent-xxl-job-integration
  -> test-agent-system-management
  -> test-agent-configuration-management
  -> test-agent-scheduler
  -> test-agent-integration
  -> test-agent-memory
  -> test-agent-common / test-agent-domain / test-agent-observability
  -> test-agent-persistence / test-agent-event / test-agent-opencode-client

test-agent-api
  -> test-agent-common
  -> test-agent-domain
  -> test-agent-observability
  -> test-agent-event
  -> test-agent-workspace-management
  -> test-agent-opencode-runtime
  -> test-agent-system-management
  -> test-agent-configuration-management
  -> test-agent-scheduler
  -> test-agent-xxl-job-integration
  -> test-agent-integration
  -> test-agent-model-gateway
  -> test-agent-memory

test-agent-memory
  -> test-agent-common
  -> test-agent-domain
  -> test-agent-agent-runtime
  -> test-agent-model-gateway

test-agent-xxl-job-integration
  -> test-agent-common / test-agent-domain / test-agent-observability
  -> test-agent-scheduler
  -> test-agent-xxl-job-admin-upstream

test-agent-xxl-job-admin-upstream
  -> Spring MVC / MyBatis / XXL Core（仅上游源码所需）

test-agent-scheduler
  -> test-agent-common
  -> test-agent-domain
  -> test-agent-observability

test-agent-workspace-management
  -> test-agent-common
  -> test-agent-domain
  -> test-agent-scheduler

test-agent-opencode-runtime
  -> test-agent-common
  -> test-agent-domain
  -> test-agent-event
  -> test-agent-agent-runtime
  -> test-agent-scheduler

test-agent-agent-runtime
  -> test-agent-common
  -> test-agent-domain
  -> test-agent-opencode-client

test-agent-system-management
  -> test-agent-common

test-agent-configuration-management
  -> test-agent-common
  -> test-agent-domain

test-agent-integration
  -> test-agent-common
  -> test-agent-domain

test-agent-model-gateway
  -> test-agent-common
  -> test-agent-domain
  -> test-agent-observability

test-agent-opencode-client
  -> test-agent-common
  -> test-agent-domain
  -> test-agent-observability
  -> test-agent-opencode-sdk-generated

test-agent-persistence
  -> test-agent-domain
  -> test-agent-common

test-agent-event
  -> test-agent-common
  -> test-agent-domain
```

`test-agent-app` 仍是唯一可部署 Spring Boot jar，但不承载业务逻辑。它强制启动 WebFlux 主上下文，并可为了启动、profile、migration、health、XXL 子上下文/executor 和 seed 依赖基础运行模块；平台 HTTP/SSE/WebSocket 入口属于 `test-agent-api`，XXL Servlet 页面入口只属于 integration 启动的子上下文。

`workflow-service`、`runner-controller` 和 `analysis-task` 是 Java Maven 图之外的独立 Python/容器边界。Python 只能通过版本化 HTTP 端口调用 Java 的 workflow capabilities 和模型网关；Java 不能依赖 Python 包，也不能代理其浏览器 HTTP/SSE。只有 Runner 可挂 Docker Socket，API/Worker 和任务容器均禁止挂载。平台模型grant可到达Runner控制面，但只能经stdin进入任务容器内独立UID的回环relay；Codex/OpenCode进程只能持有短生命周期本地token，禁止直接依赖平台grant。

## 后端禁止关系

1. Controller 不得直接访问持久化实现。
2. Controller 不得直接调用 generated SDK。
3. `test-agent-api` 不得依赖 `test-agent-persistence`、`test-agent-app` 或 generated SDK。
4. `test-agent-app` 不得新增 Controller、WebFilter、WebSocket handler 或业务包。
5. `test-agent-app` 不得直接依赖 `test-agent-opencode-sdk-generated`。
6. `test-agent-domain` 不得依赖 Spring Web、Persistence、generated SDK。
7. `test-agent-persistence` 不得反向依赖 `test-agent-app`、`test-agent-api` 或业务模块。
8. generated SDK DTO 不得进入 domain，不得直接返回给前端。
9. `test-agent-test-support` 不得被生产代码依赖。
10. 除 `test-agent-opencode-client` 外，人工维护业务模块不得 import `com.example.opencode.sdk.*`。
11. 业务模块不得直接访问 MyBatis mapper、MyBatis 行模型或 `test-agent-persistence` 内部实现；只能通过 domain 端口调用持久化能力。
12. 涉及 opencode-manager 路由、Java 到 manager 控制、用户 opencode 进程服务器归属、运行管理 `containerId` 路由、Agent 配置或文件 WebSocket 目标后端选择时，不得新增自写路由、Redis 快照扫描、防循环 header、本机降级或本地绕过；必须复用 `BackendJavaRouteResolver`、普通 HTTP 的 `BackendHttpForwarder`、RunEvent SSE 的 `BackendSseForwarder` 和目标 Java 的 `OpencodeProcessManagerGateway` 公共链路。`BackendSseForwarder` 只用于 `text/event-stream` 流式转发，必须使用同一 `X-Test-Agent-Backend-Routed` 防循环头。
13. 涉及 opencode server 启动、重启后拉起、端口复用或启动成功状态回写时，不得在业务入口直接调用 `OpencodeProcessManagerGateway.startProcess()` 并自行保存进程/binding/heartbeat/`ExecutionNode`；必须复用 `OpencodeProcessStartupService`，由它统一完成 start、候选快照、manager health、opencode HTTP health、最终状态和兼容投影。
14. 涉及 opencode server 停止、停止后状态回写或运行管理停止命令时，不得在业务入口直接调用 `OpencodeProcessManagerGateway.stopProcess()` 并自行保存 `STOPPED`；必须复用 `OpencodeProcessStopService`，由它统一完成 stop、停止后 manager health 失败确认和最终状态回写。
15. 涉及 opencode server 状态查询、健康探测、状态回写或 heartbeat 刷新时，不得在业务入口直接调用 `OpencodeProcessManagerGateway.checkHealth()` 并自行映射查询结果；必须复用 `OpencodeProcessStatusQueryService`。
16. 禁止修改 `test-agent-xxl-job-admin-upstream` 的上游 Java/资源；平台 SSO、登录禁用、安全头、MySQL migration、executor 和 health 改造必须放在 `test-agent-xxl-job-integration`。
17. XXL executor 注册地址、调度参数和 ROUND 路由不得绑定稳定 Linux 服务器；夜间扫描后的业务分发必须读取任务固化的 `target_linux_server_id`，并复用 `BackendJavaRouteResolver` 与 `BackendHttpForwarder` 调用目标 Java，不能把该目标改造成 executor affinity。
18. `test-agent-model-gateway` 不得依赖 `test-agent-api`、`test-agent-persistence`、`test-agent-app` 或 generated SDK；
    Controller 只做协议适配，不能重新实现模型解析、供应商 Header 注入、流式转发、错误清洗或用量聚合。
19. LobeHub ticket、nonce 和 grant 只能通过 domain `LobehubSsoStore`；业务层不得直接拼 Redis key、执行 Lua 或
    保存原始 opaque 值。LobeHub 独立数据库、Workspace JIT 和资源权限不能写入平台 persistence。
20. LobeHub 浏览器入口不得读取跨域存储、把 ticket 放入 URL 或接受任意 return URL；模型网关不得接受客户端
    供应商选择或把委托传给浏览器/设备。
21. Java workflow capability 代码不得定义 conversation、message、task、run、report、event 或 LangGraph checkpoint；这些对象只属于独立 Python 数据库。服务端接口固定 client ID 为 `workflow`，只能传 Token SHA-256 摘要并实时复核平台 session marker、用户、角色、应用成员与仓库状态。
22. Python workflow 不得读取平台数据库、个人 SSH 私钥或供应商 Token，不得复用平台 RunEvent；普通请求认证只能精确读取当前 Bearer 对应 Redis Token 键，仓库/SSH/模型能力只能走 Java 白名单接口。

## 业务工程归属

新增后端文件前必须先分析并列出现有合适工程：

- Workspace、文件查看/新增/修改/删除、git 操作、差异比对、应用版本工作区、个人工作区、应用源码固定提交物化/多服务器副本/打开/清理、agent 和 skill 管理：`test-agent-workspace-management`。应用源码 XXL handler 只消费 `test-agent-scheduler` 契约，持久化 SQL 仍在 `test-agent-persistence` MyBatis XML。
- 多 agent 运行时接口、agentId registry、统一日志/指标包装、opencode/otheragent 适配骨架：`test-agent-agent-runtime`。
- Session、Run、RunEvent 编排、agent runtime 调用、Diff/revert、terminal ticket/PTY、opencode runtime 业务定时任务：`test-agent-opencode-runtime`。
- 用户、角色、权限等平台内部管理：`test-agent-system-management`。
- 应用定义只读消费、应用成员、代码库配置、应用工作空间模板、个人 SSH key 和 Git 远端只读目录查询：`test-agent-configuration-management`。
- 周期任务 Admin/executor/SSO/MySQL Flyway 与统一 handler adapter：`test-agent-xxl-job-integration`；未修改的上游代码只放 `test-agent-xxl-job-admin-upstream`。业务 handler 仍放所属业务模块。
- `ScheduledTaskHandler`、`ScheduledTaskContext`、结果协议、Redis 锁和旧运行记录清理：`test-agent-scheduler`；不得恢复 PostgreSQL runner、`USER_PLAN` 服务或 scheduler worker 配置。
- 非 opencode 的外部系统联动：`test-agent-integration`。
- Python workflow 所需的平台共享能力：领域端口放 `test-agent-domain`，HMAC/权限/票据编排放 `test-agent-integration`，Redis 原子状态放 `test-agent-persistence`，服务端 Controller 放 `test-agent-api`；工作流业务本身只放根目录 Python 工程。
- LobeHub 平台登录交接、部门身份映射和委托签发：`test-agent-integration`；fork 内 Session、Workspace 和私有
  资源仍属于独立 fork。
- 跨 LobeHub/OpenCode 可复用的模型解析、供应商密钥/可信 Header 注入、能力探测和 OpenAI-compatible 流式
  代理：`test-agent-model-gateway`；管理员目录用例在 `test-agent-configuration-management`，HTTP 在
  `test-agent-api`，SQL/Redis 在 `test-agent-persistence`。
- QA 测试人员长期工作习惯、个人/团队记忆治理、Mem0 窄接口、学习与检索合并、短期模型授权和 Skill
  提案：`test-agent-memory`；治理领域端口在 `test-agent-domain`，SQL/Redis 在 `test-agent-persistence`，HTTP
  在 `test-agent-api`。原始聊天仍归现有 OpenCode Session/恢复链路，项目业务知识不得进入该模块。
- Controller、WebSocket 入口适配、请求/响应 DTO、统一异常、鉴权、限流、trace Web 入口：`test-agent-api`。
- 启动、profile、migration、health、日志和运行装配：`test-agent-app`。
- 平台 PostgreSQL 关系型 SQL：`test-agent-persistence` 的 MyBatis XML mapper；XXL 独立 MySQL 的平台扩展 SQL：`test-agent-xxl-job-integration` 的 MyBatis XML 与独立 Flyway location。存量 `Jdbc*Repository` 只保留迁移窗口，不承接新 SQL。

如果没有合适工程，按业务边界新建 Maven module，并同步 `backend/README.md`、模块 README、包级说明和本文件。

## API URL 边界

- 旧 `/api/...` URL 默认保留，作为兼容入口；已明确作废的入口除外，不得无计划删除或重定向。
- 前端调用平台自身能力优先使用 `/api/internal/platform/{business-project}/{business}/...`。
- 与 agent 交互的新入口使用 `/api/internal/agent/{agentId}/...`；当前默认可用 agent 为 `opencode`，opencode 原 path 兼容形态为 `/api/internal/agent/opencode/{原 opencode path}`。
- 给其他系统调用的公开 API 使用 `/api/public/...`，新增前必须先完成鉴权、限流和兼容性设计。
- `/workflow-api/v1/**` 是 Nginx 同源直达 Python 的独立命名空间；Java 不实现、不转发。Java 给 Python/Runner 的服务端白名单固定为 `/api/internal/workflow-capabilities/v1/**`，不能作为浏览器 API。

`test-agent-api` 可以为同一能力同时暴露旧 URL 和新 URL；两者必须共享 DTO、鉴权、traceId、错误格式和同一业务实现。

## 前端访问规则

1. 前端不得直接访问 opencode server。
2. 所有平台 Java 调用必须通过 `backend-api`。受控例外：`/workflow-chat` 只能通过 `workflow-api-client` 同源访问 Python `/workflow-api/v1/**`，不得由 `backend-api` 或页面组件转发。
3. 所有平台实时事件必须通过 `event-stream-client` 消费平台 RunEvent SSE。受控例外：独立工作流只通过 `workflow-api-client` 的 fetch SSE 消费 Python 原生 AG-UI，不能映射为 RunEvent。
4. `backend-api` 不得依赖页面、工作台、Monaco、Dockview 或具体业务组件。
5. `event-stream-client` 不得直接修改 Vue 组件状态。
6. `ui-kit` 和 `shared-types` 不得依赖业务 API 或事件流。
7. 自研 Web IDE 功能必须按 package 边界沉淀，不能把全部逻辑堆到 `apps/agent-web`。
8. Phase 07 搜索只过滤已加载文件树的文件名；Phase 08 Diff 接受/拒绝只能通过平台 Run 级 API。
9. 交互式 PTY 只能作为平台后端的受控 WebSocket 例外暴露；前端 terminal package 不得直连 opencode server、SSH、sidecar 或任意主机。
10. XXL 管理页只允许同源 `/xxl-job-admin/` iframe；前端先经 `backend-api` 签发一次性票据，再以表单 POST，禁止把票据放入 URL、router 或持久存储。
11. LobeHub 入口只允许先经 `backend-api` 使用平台 Bearer Token 签票，再由 `agent-web` 对服务端固定
    `consumeUrl` 隐藏表单 POST；这是跨域登录交接，不是前端直连模型或 LobeHub API。禁止任意 return URL、
    ticket 持久化或把模型委托交给浏览器。
12. `workflow-chat` 只依赖 `workflow-api-client` 与展示组件，平台 Bearer 仍只保存在既有内存/sessionStorage；fetch SSE 必须支持 Authorization、`Last-Event-ID`、快照替换、durable 去重和取消订阅。

## 文档要求

每个模块 README 必须写清：

- 上游调用方。
- 下游依赖。
- 允许依赖。
- 禁止依赖。
- 违反边界时应改到哪个模块。
