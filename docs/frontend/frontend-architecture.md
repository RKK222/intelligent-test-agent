# 前端架构规范

当前仓库尚未创建真实 `frontend/` 工程。本规范先定义未来完全自研 Web IDE 前端的 workspace、包职责和访问边界。

## 技术栈

前端统一使用：

- Next.js
- React
- TypeScript
- shadcn/ui
- Tailwind
- assistant-ui
- Dockview
- Monaco
- TanStack Query
- Zustand
- pnpm workspace

## 规划结构

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

## 包职责

- `apps/agent-web`：自研主应用，负责路由、整体布局、认证态入口、全局错误边界和页面组合。
- `packages/backend-api`：访问 `test-agent-app` 的唯一前端 HTTP client。
- `packages/event-stream-client`：RunEvent SSE client，负责连接、重连、去重、断点恢复和取消订阅。
- `packages/workbench-shell`：Dockview 工作台布局、面板注册、面板恢复和快捷入口。
- `packages/file-explorer`：文件树、文件状态、搜索、打开文件入口。
- `packages/editor`：Monaco 编辑器、文件编辑、保存、只读模式和编辑器状态。
- `packages/diff-viewer`：Monaco Diff、接受/拒绝修改、变更预览和应用结果。
- `packages/agent-chat`：assistant-ui 对话、PlanCard、ToolCallCard、TestRunCard、DiffActionCard。
- `packages/test-runner`：测试执行面板、状态轮询或事件更新、取消和重试。
- `packages/report-viewer`：报告详情、失败分析、Trace、截图、日志。
- `packages/skill-studio`：Python 技能编辑、调试、参数配置和运行结果。
- `packages/ui-kit`：平台通用 UI 组件、主题、图标、反馈组件。
- `packages/shared-types`：跨包共享 TypeScript 类型。

## 架构红线

1. 前端完全自研，不引入外部 Web 项目作为页面基础。
2. 前端不得直连 opencode server。
3. 所有后端请求必须通过 `packages/backend-api`。
4. 所有事件订阅必须通过 `packages/event-stream-client` 消费平台 `RunEvent SSE`。
5. 页面组件不得直接拼接后端 URL，不得绕过 API client。
6. Web IDE 业务能力必须沉淀到对应 package，不能全部堆在 `apps/agent-web`。
7. 前端 DTO、事件类型和错误格式必须与 `docs/api/` 和 `docs/frontend/frontend-backend-contract.md` 同步。

## 访问关系

允许方向：

```text
apps/agent-web
  -> packages/workbench-shell
  -> packages/agent-chat
  -> packages/file-explorer
  -> packages/editor
  -> packages/diff-viewer
  -> packages/test-runner
  -> packages/report-viewer
  -> packages/skill-studio
  -> packages/backend-api
  -> packages/event-stream-client
  -> packages/ui-kit
  -> packages/shared-types

feature packages
  -> packages/backend-api
  -> packages/event-stream-client
  -> packages/ui-kit
  -> packages/shared-types
```

禁止方向：

- `packages/backend-api` 不得依赖页面、工作台或具体业务组件。
- `packages/event-stream-client` 不得依赖页面、工作台或具体业务组件。
- `packages/shared-types` 不得依赖任何业务包。
- `packages/ui-kit` 不得依赖业务 API、事件流或页面状态。

## 文档要求

未来创建前端工程后必须补：

- `frontend/README.md`。
- 每个 app/package 的 `README.md`。
- 关键源码包的 `PACKAGE.md`。
- 与后端 API、RunEvent SSE 有关的契约变更必须同步 `docs/api/` 和 `docs/frontend/frontend-backend-contract.md`。
