# 分层依赖与访问关系

本文档定义后端和前端的依赖边界。

## 后端依赖方向

允许方向：

```text
test-agent-app
  -> test-agent-api
  -> test-agent-system-management
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

test-agent-workspace-management
  -> test-agent-common
  -> test-agent-domain

test-agent-opencode-runtime
  -> test-agent-common
  -> test-agent-domain
  -> test-agent-event
  -> test-agent-opencode-client

test-agent-system-management
  -> test-agent-common

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

`test-agent-app` 仍是唯一可部署 Spring Boot jar，但不承载业务逻辑。它可以为了启动、profile、migration、health 和 seed 依赖基础运行模块；HTTP/SSE/WebSocket 入口属于 `test-agent-api`，具体业务属于对应业务模块。

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

## 业务工程归属

新增后端文件前必须先分析并列出现有合适工程：

- Workspace、文件查看/新增/修改/删除、git 操作、差异比对、agent 和 skill 管理：`test-agent-workspace-management`。
- Session、Run、RunEvent 编排、opencode runtime、Diff/revert、terminal ticket/PTY：`test-agent-opencode-runtime`。
- 用户、角色、权限等平台内部管理：`test-agent-system-management`。
- 非 opencode 的外部系统联动：`test-agent-integration`。
- Controller、WebSocket 入口适配、请求/响应 DTO、统一异常、鉴权、限流、trace Web 入口：`test-agent-api`。
- 启动、profile、migration、health、日志和运行装配：`test-agent-app`。

如果没有合适工程，按业务边界新建 Maven module，并同步 `backend/README.md`、模块 README、包级说明和本文件。

## API URL 边界

- 旧 `/api/...` URL 全部保留，作为兼容入口，不在本次删除或重定向。
- 前端调用平台自身能力优先使用 `/api/internal/platform/{business-project}/{business}/...`。
- 与 opencode 交互的兼容代理入口使用 `/api/internal/agent/opencode/{原 opencode path}`。
- 给其他系统调用的公开 API 使用 `/api/public/...`，新增前必须先完成鉴权、限流和兼容性设计。

`test-agent-api` 可以为同一能力同时暴露旧 URL 和新 URL；两者必须共享 DTO、鉴权、traceId、错误格式和同一业务实现。

## 前端访问规则

1. 前端不得直接访问 opencode server。
2. 所有后端调用必须通过 `backend-api`。
3. 所有实时事件必须通过 `event-stream-client` 消费平台 RunEvent SSE。
4. `backend-api` 不得依赖页面、工作台、Monaco、Dockview 或具体业务组件。
5. `event-stream-client` 不得直接修改 React 组件状态。
6. `ui-kit` 和 `shared-types` 不得依赖业务 API 或事件流。
7. 自研 Web IDE 功能必须按 package 边界沉淀，不能把全部逻辑堆到 `apps/agent-web`。
8. Phase 07 搜索只过滤已加载文件树的文件名；Phase 08 Diff 接受/拒绝只能通过平台 Run 级 API。
9. Phase 11 P2 交互式 PTY 只能作为平台后端的受控 WebSocket 例外暴露；前端 terminal package 不得直连 opencode server、SSH、sidecar 或任意主机。

## 文档要求

每个 PACKAGE.md 必须写清：

- 上游调用方。
- 下游依赖。
- 允许依赖。
- 禁止依赖。
- 违反边界时应改到哪个模块。
