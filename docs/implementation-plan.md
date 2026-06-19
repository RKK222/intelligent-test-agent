# test-agent 总体实施方案

本文档是项目总体方案。后续 Codex、Claude 或人工开发在拆分任务、定位代码和验收功能时，应优先阅读本文档，再进入 `docs/plan/` 中对应阶段计划。

## 目标

`test-agent` 是面向测试智能体的 Web IDE 平台，提供工作区管理、智能体对话、文件浏览与编辑、代码 Diff、测试执行、报告分析、技能编排和执行观测能力。

当前总体决策如下：

1. 后端是 Maven 多模块工程，最终只交付一个可部署 Spring Boot 服务包：`test-agent-app`。
2. 后端通过 generated Java SDK 和 `OpencodeClientFacade` 调用 opencode server，generated SDK 只能重新生成，不能手改源码。
3. 前端完全自研，不引入外部 Web 项目作为页面基础。
4. 前端所有 HTTP 请求必须通过 `packages/backend-api` 调用 `test-agent-app`。
5. 前端事件订阅必须通过 `packages/event-stream-client` 消费平台 `RunEvent SSE`，不得直连 opencode server。

## 总体架构

```text
Browser
  -> frontend/apps/agent-web
      -> packages/backend-api
      -> packages/event-stream-client
  -> test-agent-app
      -> domain / persistence / event / observability
      -> test-agent-opencode-client
          -> test-agent-opencode-sdk-generated
              -> opencode server pool
```

关键边界：

- 浏览器只认识平台后端 API 和平台事件流。
- `test-agent-app` 统一承载 API、鉴权、限流、路由、任务入口、事件出口和错误处理。
- `test-agent-opencode-client` 是业务代码访问 opencode server 的唯一门面。
- `test-agent-opencode-sdk-generated` 只保存生成代码，不承载业务逻辑。

## 后端工程

后端位于 `backend/`，采用 Spring Boot 4、Java 21、Maven multi-module。

```text
backend/
  pom.xml
  test-agent-common/
  test-agent-domain/
  test-agent-observability/
  test-agent-opencode-sdk-generated/
  test-agent-opencode-client/
  test-agent-persistence/
  test-agent-event/
  test-agent-test-support/
  test-agent-app/
```

模块职责：

- `test-agent-common`：公共异常、统一响应、分页、校验、时间工具。
- `test-agent-domain`：Workspace、Session、Run、RunEvent、ExecutionNode、RoutingDecision 等纯领域模型。
- `test-agent-observability`：traceId、结构化日志、Micrometer 指标、观测性工具。
- `test-agent-opencode-sdk-generated`：从 `tools/opencode-sdk-generator` 复制的 generated SDK 源码。
- `test-agent-opencode-client`：封装 generated SDK，提供 `OpencodeClientFacade`。
- `test-agent-persistence`：数据库、Flyway、Repository、Redis 可选能力。
- `test-agent-event`：RunEvent、SSE、事件转换、事件回放。
- `test-agent-test-support`：测试 fixture、mock server、集成测试支撑。
- `test-agent-app`：唯一 Spring Boot 启动入口，承载全部对外后端能力。

最终包形态：

- 只有 `test-agent-app` 生成可执行 Spring Boot jar。
- 其他模块只生成普通 library jar。
- 不创建独立 gateway 或 control-plane 可部署包。

## 前端工程

前端位于未来的 `frontend/`，采用 pnpm workspace、Next.js、React、TypeScript、shadcn/ui、Tailwind、assistant-ui、Dockview、Monaco、TanStack Query、Zustand。

```text
frontend/
  README.md
  apps/
    agent-web/
  packages/
    backend-api/
    event-stream-client/
    workbench-shell/
    file-explorer/
    editor/
    diff-viewer/
    agent-chat/
    test-runner/
    report-viewer/
    skill-studio/
    ui-kit/
    shared-types/
```

包职责：

- `apps/agent-web`：自研 Web IDE 主应用，负责路由、布局组合、认证态入口和全局错误边界。
- `packages/backend-api`：访问 `test-agent-app` 的唯一 HTTP client。
- `packages/event-stream-client`：RunEvent SSE client，负责连接、重连、去重、断点恢复和取消订阅。
- `packages/workbench-shell`：Dockview 工作台布局、面板注册和面板生命周期。
- `packages/file-explorer`：文件树、文件状态、搜索、打开文件入口。
- `packages/editor`：Monaco 编辑器、只读/编辑态、保存、语言能力入口。
- `packages/diff-viewer`：Monaco Diff、接受/拒绝修改、变更预览。
- `packages/agent-chat`：assistant-ui 对话、PlanCard、ToolCallCard、TestRunCard、DiffActionCard。
- `packages/test-runner`：测试执行面板、任务状态、取消和重试。
- `packages/report-viewer`：测试报告、失败详情、Trace、截图、日志。
- `packages/skill-studio`：Python 技能编辑、调试、参数和运行记录。
- `packages/ui-kit`：平台通用组件、主题、图标、反馈组件。
- `packages/shared-types`：跨包共享 TypeScript 类型。

## 核心功能清单

后端必须逐步提供：

1. Workspace API：工作区创建、打开、文件树、文件内容、保存、变更状态。
2. Session API：会话创建、历史会话、会话详情、消息追加。
3. Run API：启动任务、取消任务、查询运行状态、记录运行结果。
4. Event API：RunEvent append-only 存储、SSE 推送、断线续传。
5. Opencode client：统一调用 opencode server，映射错误、超时、重试和事件。
6. Routing：根据 workspace、session、负载和健康状态选择执行节点。
7. Persistence：Flyway migration、Repository、事务边界和数据兼容策略。
8. Observability：traceId、结构化日志、指标、关键链路观测。

Phase 04/05 已固化的后端运行时边界：

- `test-agent-app` 提供 Workspace、Session、Run、Cancel 和 RunEvent SSE Runtime API。
- Run 启动通过 `OpencodeClientFacade.createSession` 懒创建远端 opencode session，再由 `OpencodeClientFacade.startRun` 调用 `prompt_async`；后续 Run 复用内部映射，generated SDK 不越过 opencode-client 模块。
- 本地鉴权默认免 token，配置 `TEST_AGENT_API_TOKEN` 后启用 Bearer token 占位鉴权。
- 研发测试和生产部署只将 `test-agent-app` Java 进程放入 Docker；PostgreSQL、Redis 和 opencode server 均通过外部地址注入。
- `deploy/local/docker-compose.yml` 仅作为个人离线开发备用入口，不作为研发测试或生产部署主路径。

前端必须逐步提供：

1. Web IDE 基座：Dockview 工作台、侧边栏、状态栏、通知、快捷操作。
2. 文件能力：文件树、文件打开、编辑、保存、脏状态、搜索。
3. 智能体对话：消息流、任务计划卡、工具调用卡、测试运行卡、Diff 操作卡。
4. 实时事件：RunEvent SSE 输出、断线恢复、事件去重、状态同步。
5. Diff 工作流：查看变更、接受/拒绝、应用结果反馈。
6. 测试面板：运行测试、查看状态、取消、重试、定位失败。
7. 报告分析：失败详情、Trace、截图、日志、历史运行对比。
8. Skill Studio：Python 技能编辑、调试、参数配置、运行结果。

## API 与事件契约

所有 HTTP API 必须记录在 `docs/api/backend-api.md`，至少包含：

- 路径和方法。
- 请求参数和请求体。
- 响应体和统一错误格式。
- 权限、限流和 traceId 规则。
- 兼容性说明。

所有 SSE 和事件类型必须记录在 `docs/api/event-stream-api.md`，至少包含：

- 事件名称。
- 事件字段。
- 事件顺序和幂等规则。
- `Last-Event-ID` 断线续传行为。
- 新增字段、废弃字段和向后兼容策略。

前端与后端的调用边界见 `docs/frontend/frontend-backend-contract.md`。

## 数据与兼容性

1. 数据库结构变更必须新增 Flyway migration。
2. 不能只改实体、Repository 或 DTO。
3. API、DTO、事件类型、数据库字段变更必须考虑向后兼容。
4. 删除字段、重命名字段、事件语义变化必须先给过渡策略。
5. generated SDK 的 spec 变更必须通过 `tools/generate-opencode-java-sdk.sh` 重新生成并验证编译。

## 质量要求

修改任务必须遵守：

- `AGENTS.md`
- `docs/development/ai-coding-rules.md`
- `docs/development/task-workflow.md`
- `docs/development/ai-self-checklist.md`
- `docs/architecture/dependency-rules.md`
- `docs/security/security-standards.md`

测试要求：

- 后端改什么补什么测试，详见 `docs/backend/backend-testing-standards.md`。
- 前端改什么补什么测试，详见 `docs/frontend/frontend-testing-standards.md`。
- API、事件、数据、安全、性能变化必须同步更新对应文档。

性能要求：

- 后端分页、超时、重试、SSE 背压、日志量和大对象传输必须按 `docs/backend/backend-performance-standards.md` 执行。
- 前端首屏、列表虚拟化、SSE 渲染节流、请求缓存和 bundle 边界必须按 `docs/frontend/frontend-performance-standards.md` 执行。

## 分阶段计划

阶段计划放在 `docs/plan/`：

- `00-roadmap.md`：总体路线图。
- `01-backend-domain-and-contracts.md`：领域模型和契约。
- `02-persistence-and-routing.md`：持久化和路由。
- `03-opencode-client-and-events.md`：opencode client 与事件。
- `04-backend-api-runtime.md`：后端 API 运行时。
- `05-local-integration-and-devops.md`：本地集成和开发环境。
- `06-frontend-foundation.md`：前端基础工程。
- `07-workbench-shell-and-files.md`：工作台、文件树和编辑器。
- `08-agent-chat-diff-and-run-mvp.md`：对话、Diff 和运行 MVP。
- `09-test-reports-and-skill-studio.md`：报告和 Skill Studio。
- `10-e2e-hardening-and-release.md`：端到端加固和发布验收。

每个阶段都必须包含可验收功能清单、修改项目、实现功能和验收方式。

## 当前完成定义

一个阶段完成必须同时满足：

1. 对应功能可运行或可被自动化测试验证。
2. 相关后端、前端、API、事件、数据和安全文档已同步。
3. 相关 README 或 PACKAGE.md 已同步。
4. 测试命令通过，无法运行的测试必须说明原因和风险。
5. AI 自检清单全部完成。
