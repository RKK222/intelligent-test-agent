# 模块与包速查

本文件是“按功能找模块/包”的速查表，合并原前端架构、前后端契约和总体方案的模块职责。依赖边界与禁止关系见 `docs/architecture/dependency-rules.md`；HTTP/SSE 契约见 `docs/api/`。

## 总体架构

```text
Browser
  -> frontend/apps/agent-web
      -> packages/backend-api
      -> packages/event-stream-client
  -> frontend-opencode
      -> packages/backend-api (source alias)
      -> packages/event-stream-client (source alias)
  -> test-agent-app
      -> test-agent-api
          -> workspace-management / opencode-runtime / system-management / integration
      -> persistence / event / observability
      -> test-agent-opencode-client
          -> test-agent-opencode-sdk-generated
              -> opencode server pool
```

关键边界：

- 浏览器只认识平台后端 API 和平台事件流。
- `test-agent-api` 统一承载 API、鉴权、限流、traceId、任务入口、事件出口和错误处理。
- `test-agent-app` 只承载启动、装配、profile、migration、health 和日志等运行入口，不承载业务逻辑。
- `test-agent-opencode-client` 是业务代码访问 opencode server 的唯一门面。
- `test-agent-opencode-sdk-generated` 只保存生成代码，不承载业务逻辑。

## 后端模块职责

| 模块 | 职责 |
|---|---|
| `test-agent-common` | 公共异常、统一响应 `ApiResponse`/`ApiErrorResponse`、TraceId、分页、校验、时间工具。 |
| `test-agent-domain` | Workspace、Session、Run、RunEvent、ExecutionNode、RoutingDecision 等纯领域模型与状态机，不依赖 Spring Web/Persistence/generated SDK。 |
| `test-agent-observability` | traceId、结构化日志、Micrometer 指标、观测性工具。 |
| `test-agent-opencode-sdk-generated` | 从 opencode OpenAPI spec 生成的 Java SDK，禁止手改。 |
| `test-agent-opencode-client` | 封装 generated SDK，提供 `OpencodeClientFacade`，是业务访问 opencode 的唯一门面。 |
| `test-agent-workspace-management` | Workspace、文件查看/新增/修改/删除、git/diff、agent 和 skill 管理业务。 |
| `test-agent-opencode-runtime` | Session、Run、RunEvent 编排、opencode runtime、Diff/revert、terminal ticket/PTY 业务。 |
| `test-agent-system-management` | 用户、角色、权限等平台内部管理业务边界（当前为空骨架）。 |
| `test-agent-integration` | 非 opencode 外部系统联动业务边界（当前为空骨架）。 |
| `test-agent-api` | Controller、WebSocket 入口适配、请求/响应 DTO、统一异常、鉴权、限流和 trace Web 入口。 |
| `test-agent-persistence` | 数据库、Flyway、Repository、Redis 可选适配。 |
| `test-agent-event` | RunEvent、SSE、事件转换、事件回放。 |
| `test-agent-test-support` | 测试 fixture、mock server、集成测试支撑。 |
| `test-agent-app` | 唯一启动入口和可部署服务包，只放启动、装配、profile、migration、health 和日志。 |

新增后端文件前先按上表归属；没有合适工程时按业务边界新建 Maven module。

## 前端包职责

| 包 | 职责 |
|---|---|
| `apps/agent-web` | 自研 Vue 3 + Vite 主应用，负责页面组合、Vue Query Provider、Pinia、工作空间选择、Run 启动、SSE 订阅编排和全局错误提示。 |
| `packages/backend-api` | 访问平台后端服务的唯一前端 HTTP client，负责统一响应、错误和 traceId 映射。 |
| `packages/event-stream-client` | RunEvent SSE client，负责连接、自动重连、事件解析、去重和取消订阅。 |
| `packages/workbench-shell` | dockview-vue 工作台布局、顶部栏、面板和工作台级 Pinia 状态。 |
| `packages/file-explorer` | 文件树、已加载文件名过滤、变更列表和打开文件入口。 |
| `packages/editor` | Monaco 编辑器（原生 `monaco-editor`）、语言识别、内容编辑和只读展示。 |
| `packages/diff-viewer` | Monaco Diff、变更文件列表、Run/Session/VCS 来源切换、split/unified 视图、Run 级接受/拒绝按钮和当前文件反馈。 |
| `packages/agent-chat` | 自建最小 chat 运行时、用户消息、message part timeline、运行卡片（plan/tool/test/diff/event）、runtime selector/status、slash command、`@` context、permission/question/Todo dock 和纯 RunEvent reducer。 |
| `packages/terminal` | 受控 PTY 前端包，负责 ticket WebSocket 连接、输入、resize、关闭和输出渲染，不创建 ticket、不直连 opencode server。 |
| `packages/test-runner` | 底部 Run 状态、取消、重试和事件日志面板。 |
| `packages/ui-kit` | 平台通用 UI 组件、基础样式组合和反馈组件。 |
| `packages/shared-types` | 跨包共享 TypeScript 类型和事件/DTO 模型。 |
| `../frontend-opencode` | 独立 Vue/TypeScript/Vite opencode IDE App 复刻工程；不加入 `frontend/pnpm-workspace.yaml`，通过 alias 复用 `backend-api`、`event-stream-client`、`shared-types` 源码。 |

## 前端访问关系

允许方向：

```text
apps/agent-web
  -> packages/workbench-shell / agent-chat / file-explorer / editor
  -> packages/diff-viewer / terminal / test-runner
  -> packages/backend-api / event-stream-client
  -> packages/ui-kit / shared-types

feature packages -> packages/ui-kit / shared-types
packages/backend-api -> packages/shared-types
packages/event-stream-client -> packages/shared-types
```

禁止方向：

- `backend-api`、`event-stream-client` 不得依赖页面、工作台或具体业务组件。
- `shared-types` 不得依赖任何业务包。
- `ui-kit` 不得依赖业务 API、事件流或页面状态。
- `editor`、`diff-viewer` 不得启动 Run 或直连 opencode server。
- 前端不得直连 opencode server；所有 HTTP 请求经 `backend-api`，所有实时事件经 `event-stream-client`。

## 前后端调用边界

1. `packages/backend-api` 是前端访问后端的唯一入口，负责统一 base URL、鉴权头、traceId、请求超时、统一解析成功/错误响应、将后端统一错误格式转换为前端错误对象，并为 `@tanstack/vue-query` 提供稳定 query key 和 mutation 方法；不得直连 opencode server、不得保存 UI 状态、不得吞掉后端错误。
2. `packages/event-stream-client` 是前端消费实时事件的唯一入口，负责建立/关闭连接、断线续传（首次续传 `?lastEventId=`，后端保留 `Last-Event-ID` header 兼容）、重复事件幂等保护、向上层输出类型化事件；不得直接修改 Vue 组件状态、不得访问 opencode server。
3. 后端 HTTP DTO 映射到 `shared-types` 或 `backend-api` 内部类型；RunEvent 事件类型映射到 `shared-types`；页面展示模型必须由 API DTO 或 RunEvent 明确转换而来。
4. 新增字段必须默认可选，前端能处理旧响应缺字段；废弃字段必须保留过渡期；新事件类型前端必须有安全展示或忽略策略。
5. 后端统一错误响应转换为前端错误对象，至少包含 `traceId`、`code`、`message`、`retryable`、`details`；可重试错误提供重试入口，权限错误引导重新登录，限流错误展示等待语义，系统错误展示 traceId。

## 参考/实验目录

`frontend/interaction-visual-demo` 和 `opencode-source/opencode-1.17.8/` 仅作为 opencode Web 行为参考或交互资料；顶层 `frontend-opencode` 是独立 Vue/Vite 复刻工程，验收命令在该目录执行，不替代 `frontend/` 主 workspace 的检查；`requirements/` 下的历史文档不作为编码依据。
