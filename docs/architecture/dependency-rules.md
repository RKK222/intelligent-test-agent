# 分层依赖与访问关系

本文档定义后端和前端的依赖边界。

## 后端依赖方向

允许方向：

```text
test-agent-app
  -> test-agent-common
  -> test-agent-domain
  -> test-agent-observability
  -> test-agent-opencode-client
  -> test-agent-persistence
  -> test-agent-event

test-agent-opencode-client
  -> test-agent-common
  -> test-agent-domain
  -> test-agent-observability
  -> test-agent-opencode-sdk-generated

test-agent-persistence
  -> test-agent-domain
  -> test-agent-common
```

## 后端禁止关系

1. Controller 不得直接访问 Repository。
2. Controller 不得直接调用 generated SDK。
3. `test-agent-app` 不得直接依赖 `test-agent-opencode-sdk-generated`。
4. `test-agent-domain` 不得依赖 Spring Web、Persistence、generated SDK。
5. `test-agent-persistence` 不得反向依赖 `test-agent-app`。
6. generated SDK DTO 不得进入 domain，不得直接返回给前端。
7. `test-agent-test-support` 不得被生产代码依赖。
8. 除 `test-agent-opencode-client` 外，业务模块不得 import `com.example.opencode.sdk.*`。

Phase 01 后，`test-agent-common` 持有统一响应、错误、分页和基础异常；`test-agent-domain` 只能依赖 `test-agent-common` 和 JDK，不允许引入 Spring Web、Persistence 或 `com.example.opencode.sdk`。

Phase 02/03 后，`test-agent-domain` 持有 Repository 端口、RunEventDraft 和路由决策逻辑；`test-agent-persistence` 实现这些端口；`test-agent-event` 只依赖 RunEvent 端口做追加、回放和 SSE 映射；`test-agent-opencode-client` 通过 `OpencodeClientFacade` 输出平台 command/result 和 `RunEventDraft`。

Phase 04/05 后，`test-agent-app` 承载唯一 Runtime API、WebFilter、安全占位、application service、本地 profile 和 health contributor。Controller 只能依赖 application service 或 event service，不能直接依赖 Repository 实现或 generated SDK。`test-agent-app -> test-agent-opencode-client -> test-agent-opencode-sdk-generated` 是唯一允许的 opencode 调用链；`test-agent-app` 不能 import `com.example.opencode.sdk.*`。

`test-agent-app` 可以直接依赖 Flyway Core 和数据库 support 包，因为 `DatabaseMigrationRunner` 是运行态 migration 入口；migration 脚本和 Repository 实现仍归属 `test-agent-persistence`。

Phase 06 前置修复后，平台 Session 与远端 opencode Session 通过 persistence 内部字段映射。前端仍只访问平台 API；后端按功能需要通过 `OpencodeClientFacade` 封装 opencode 能力，不一次性代理完整 opencode server API。

Phase 06-08 后，Diff 查看、接受和拒绝能力由 `test-agent-app` 的 application service 编排。`RunController` 只能调用 `RunDiffApplicationService`，不得直接访问 Repository、generated SDK 或 opencode server；`sessionDiff` 和 `sessionRevert` 只能封装在 `test-agent-opencode-client`，generated SDK DTO 不得进入前端响应或 domain。

## 前端访问规则

1. 前端不得直接访问 opencode server。
2. 所有后端调用必须通过 `backend-api`。
3. 所有实时事件必须通过 `event-stream-client` 消费平台 RunEvent SSE。
4. `backend-api` 不得依赖页面、工作台、Monaco、Dockview 或具体业务组件。
5. `event-stream-client` 不得直接修改 React 组件状态。
6. `ui-kit` 和 `shared-types` 不得依赖业务 API 或事件流。
7. 自研 Web IDE 功能必须按 package 边界沉淀，不能把全部逻辑堆到 `apps/agent-web`。
8. Phase 07 搜索只过滤已加载文件树的文件名；Phase 08 Diff 接受/拒绝只能通过平台 Run 级 API。

## 文档要求

每个 PACKAGE.md 必须写清：

- 上游调用方。
- 下游依赖。
- 允许依赖。
- 禁止依赖。
- 违反边界时应改到哪个模块。
