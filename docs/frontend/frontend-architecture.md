# 前端架构规范

`frontend/` 已在 Phase 06 创建为完全自研 Web IDE 工程。`frontend/interaction-visual-demo` 仅作为布局和交互参考资料保留，不纳入 pnpm workspace 构建，不作为真实应用代码复用。

## 技术栈

前端统一使用：

- Next.js
- React
- TypeScript
- Tailwind
- assistant-ui
- Dockview
- Monaco
- TanStack Query
- Zustand
- pnpm workspace

Phase 06-08 不引入外部 Web 项目作为页面基础，通用组件先沉淀在 `packages/ui-kit`。

Phase 11 复刻 opencode Web App 运行态能力时，行为参考以 `opencode-source/opencode-1.17.8/packages/app` 为主；不得迁移 Solid 技术栈，也不得让前端直连 opencode server。

## 当前结构

```text
frontend/
  README.md
  apps/
    agent-web/
      README.md
      src/PACKAGE.md
  packages/
    backend-api/
    event-stream-client/
    workbench-shell/
    file-explorer/
    editor/
    diff-viewer/
    agent-chat/
    terminal/
    test-runner/
    ui-kit/
    shared-types/
  interaction-visual-demo/
```

## 包职责

- `apps/agent-web`：自研主应用，负责页面组合、TanStack Query Provider、工作空间选择、Run 启动、SSE 订阅编排和全局错误提示。
- `packages/backend-api`：访问平台后端服务的唯一前端 HTTP client，负责统一响应、错误和 traceId 映射；当前后端由 `test-agent-app` 装配运行。
- `packages/event-stream-client`：RunEvent SSE client，负责连接、自动重连、事件解析、去重和取消订阅。
- `packages/workbench-shell`：Dockview 工作台布局、顶部栏、左中右底面板和工作台级 Zustand 状态。
- `packages/file-explorer`：文件树、已加载文件名过滤、变更列表和打开文件入口。
- `packages/editor`：Monaco 编辑器、语言识别、内容编辑和只读展示。
- `packages/diff-viewer`：Monaco Diff、变更文件列表、Run/Session/VCS 来源切换、split/unified 视图、Run 级接受/拒绝按钮和当前文件反馈。
- `packages/agent-chat`：assistant-ui 集成点、用户消息、message part timeline、运行卡片、PlanCard、ToolCallCard、TestRunCard、DiffActionCard、Phase 11 runtime selector/status、slash command、`@` context、permission/question/Todo dock 和纯 RunEvent reducer。
- `packages/terminal`：Phase 11 P2 受控 PTY 前端包，负责 ticket WebSocket 连接、输入、resize、关闭和输出渲染，不创建 ticket、不直连 opencode server。
- `packages/test-runner`：底部 Run 状态、取消、重试和事件日志面板。
- `packages/ui-kit`：平台通用 UI 组件、基础样式组合和反馈组件。
- `packages/shared-types`：跨包共享 TypeScript 类型和事件/DTO 模型。
- Phase 11 当前已把 session history、permission/question、Agent/Provider/Model/Mode selector、command、context、Diff hunk 和 terminal 能力沉淀到既有 packages；后续只有当单个能力继续膨胀到可独立复用时，才新增 `session-manager`、`permission-prompt`、`question-prompt`、`agent-model-selector`、`command-palette` 或 `context-picker` 等 feature package。

## 阶段边界

1. Phase 07 搜索只过滤前端已加载文件树的文件名，不新增后端全文搜索 API。
2. Phase 08 Diff 接受/拒绝是 Run 级语义；当前文件按钮只改变当前选择和交互反馈，不承诺 per-file 后端回滚。
3. 前端不直接访问 opencode server；真实 opencode 能力只能通过 `test-agent-api -> test-agent-opencode-runtime -> test-agent-opencode-client` 调用，并由 `test-agent-app` 装配运行。
4. Monaco 编辑器和 Diff 按需加载，固定尺寸面板必须避免文本和控件重叠。
5. Phase 11 不实现 settings/config/provider/server 配置页；只保留 Agent/Provider/Model 等运行态选择和只读状态目录。
6. PTY WebSocket 属于 P2，只能按架构和安全文档的 ticket + WebSocket 例外实现；前端 ticket 创建走 `backend-api`，WebSocket 生命周期下沉到 `packages/terminal`。
7. 顶层 `frontend-opencode` 和 `opencode-source/opencode-1.17.8/packages/frontend-opencode` 目前不属于平台前端 workspace、构建或测试范围；除非后续正式转正，否则只作为 opencode 行为参考资料。

## 架构红线

1. 前端完全自研，不引入外部 Web 项目作为页面基础。
2. 前端不得直连 opencode server。
3. 所有后端请求必须通过 `packages/backend-api`。
4. 所有事件订阅必须通过 `packages/event-stream-client` 消费平台 `RunEvent SSE`。
5. 页面组件不得直接拼接后端 URL，不得绕过 API client。
6. Web IDE 业务能力必须沉淀到对应 package，不能全部堆在 `apps/agent-web`。
7. 前端 DTO、事件类型和错误格式必须与 `docs/api/` 和 `docs/frontend/frontend-backend-contract.md` 同步。
8. Phase 11 运行态列表、permission/question、session 操作、fs/vcs/lsp/mcp 等 HTTP 调用只能新增到 `packages/backend-api`，页面和组件不得直接拼接平台 URL。

## 访问关系

允许方向：

```text
apps/agent-web
  -> packages/workbench-shell
  -> packages/agent-chat
  -> packages/file-explorer
  -> packages/editor
  -> packages/diff-viewer
  -> packages/terminal
  -> packages/test-runner
  -> packages/backend-api
  -> packages/event-stream-client
  -> packages/ui-kit
  -> packages/shared-types

feature packages
  -> packages/ui-kit
  -> packages/shared-types

packages/backend-api
  -> packages/shared-types

packages/event-stream-client
  -> packages/shared-types
```

禁止方向：

- `packages/backend-api` 不得依赖页面、工作台或具体业务组件。
- `packages/event-stream-client` 不得依赖页面、工作台或具体业务组件。
- `packages/shared-types` 不得依赖任何业务包。
- `packages/ui-kit` 不得依赖业务 API、事件流或页面状态。
- `packages/editor`、`packages/diff-viewer` 不得启动 Run 或直连 opencode server。

## 文档要求

前端工程变更必须同步：

- `frontend/README.md`。
- 每个 app/package 的 `README.md`。
- 关键源码包的 `PACKAGE.md`。
- 与后端 API、RunEvent SSE 有关的契约变更必须同步 `docs/api/` 和 `docs/frontend/frontend-backend-contract.md`。
