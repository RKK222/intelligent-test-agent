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

## 业务工程归属

新增后端文件前必须先分析并列出现有合适工程：

- Workspace、文件查看/新增/修改/删除、git 操作、差异比对、应用版本工作区、个人工作区、agent 和 skill 管理：`test-agent-workspace-management`。
- 多 agent 运行时接口、agentId registry、统一日志/指标包装、opencode/otheragent 适配骨架：`test-agent-agent-runtime`。
- Session、Run、RunEvent 编排、agent runtime 调用、Diff/revert、terminal ticket/PTY、opencode runtime 业务定时任务：`test-agent-opencode-runtime`。
- 用户、角色、权限等平台内部管理：`test-agent-system-management`。
- 应用定义只读消费、应用成员、代码库配置、应用工作空间模板、个人 SSH key 和 Git 远端只读目录查询：`test-agent-configuration-management`。
- 周期任务 Admin/executor/SSO/MySQL Flyway 与统一 handler adapter：`test-agent-xxl-job-integration`；未修改的上游代码只放 `test-agent-xxl-job-admin-upstream`。业务 handler 仍放所属业务模块。
- `ScheduledTaskHandler`、`ScheduledTaskContext`、结果协议、Redis 锁和旧运行记录清理：`test-agent-scheduler`；不得恢复 PostgreSQL runner、`USER_PLAN` 服务或 scheduler worker 配置。
- 非 opencode 的外部系统联动：`test-agent-integration`。
- Controller、WebSocket 入口适配、请求/响应 DTO、统一异常、鉴权、限流、trace Web 入口：`test-agent-api`。
- 启动、profile、migration、health、日志和运行装配：`test-agent-app`。
- 平台 PostgreSQL 关系型 SQL：`test-agent-persistence` 的 MyBatis XML mapper；XXL 独立 MySQL 的平台扩展 SQL：`test-agent-xxl-job-integration` 的 MyBatis XML 与独立 Flyway location。存量 `Jdbc*Repository` 只保留迁移窗口，不承接新 SQL。

如果没有合适工程，按业务边界新建 Maven module，并同步 `backend/README.md`、模块 README、包级说明和本文件。

## API URL 边界

- 旧 `/api/...` URL 默认保留，作为兼容入口；已明确作废的入口除外，不得无计划删除或重定向。
- 前端调用平台自身能力优先使用 `/api/internal/platform/{business-project}/{business}/...`。
- 与 agent 交互的新入口使用 `/api/internal/agent/{agentId}/...`；当前默认可用 agent 为 `opencode`，opencode 原 path 兼容形态为 `/api/internal/agent/opencode/{原 opencode path}`。
- 给其他系统调用的公开 API 使用 `/api/public/...`，新增前必须先完成鉴权、限流和兼容性设计。

`test-agent-api` 可以为同一能力同时暴露旧 URL 和新 URL；两者必须共享 DTO、鉴权、traceId、错误格式和同一业务实现。

## 前端访问规则

1. 前端不得直接访问 opencode server。
2. 所有后端调用必须通过 `backend-api`。
3. 所有实时事件必须通过 `event-stream-client` 消费平台 RunEvent SSE。
4. `backend-api` 不得依赖页面、工作台、Monaco、Dockview 或具体业务组件。
5. `event-stream-client` 不得直接修改 Vue 组件状态。
6. `ui-kit` 和 `shared-types` 不得依赖业务 API 或事件流。
7. 自研 Web IDE 功能必须按 package 边界沉淀，不能把全部逻辑堆到 `apps/agent-web`。
8. Phase 07 搜索只过滤已加载文件树的文件名；Phase 08 Diff 接受/拒绝只能通过平台 Run 级 API。
9. 交互式 PTY 只能作为平台后端的受控 WebSocket 例外暴露；前端 terminal package 不得直连 opencode server、SSH、sidecar 或任意主机。
10. XXL 管理页只允许同源 `/xxl-job-admin/` iframe；前端先经 `backend-api` 签发一次性票据，再以表单 POST，禁止把票据放入 URL、router 或持久存储。

## 文档要求

每个模块 README 必须写清：

- 上游调用方。
- 下游依赖。
- 允许依赖。
- 禁止依赖。
- 违反边界时应改到哪个模块。
