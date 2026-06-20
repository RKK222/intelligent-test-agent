# Phase 06 前端基础工程详细设计

## 目标

Phase 06 创建完全自研前端工程，作为后续 Web IDE 能力的唯一前端入口。工程采用 pnpm workspace、Next.js、React、TypeScript、Tailwind、shadcn 风格基础组件、assistant-ui、Dockview、Monaco、TanStack Query 和 Zustand。`frontend/interaction-visual-demo` 只作为布局和交互参考，不进入真实 workspace 构建。

## 工程边界

- `frontend/` 是独立 pnpm workspace，根脚本统一提供 `dev`、`lint`、`typecheck`、`test` 和 `e2e`。
- `apps/agent-web` 是主应用，只负责路由、全局 provider、工作台组合和错误边界。
- `packages/backend-api` 是 HTTP 访问唯一入口，负责 base URL、`X-Trace-Id`、Bearer token、超时、统一成功/错误响应解析。
- `packages/event-stream-client` 是 RunEvent SSE 唯一入口，负责连接、断开、`Last-Event-ID`、事件去重和未知事件降级。
- `packages/shared-types` 放置 Workspace、Session、Run、RunEvent、Diff 等前端共享类型，不依赖任何业务包。
- `packages/ui-kit` 提供基础按钮、输入、面板、状态提示、错误提示和 Toast，不访问后端。

## 技术策略

- Next.js 使用 App Router，工作台页面作为客户端组件承载复杂交互。
- Tailwind 定义暗色 Web IDE 主题，视觉参考 demo 的三栏结构、紧凑 tab、卡片和状态色，但不复制 demo 的原生 DOM 实现。
- TanStack Query 管理 Workspace、Session、Run、文件内容和 Diff 等远端数据。
- Zustand 管理当前 workspace、打开文件、脏状态、当前 run、当前 diff、Dockview 面板状态等 UI 状态。
- Monaco、Monaco Diff 和 Dockview 按需加载，避免首屏无关大依赖阻塞。

## 验收

- `corepack pnpm install` 可安装依赖。
- `corepack pnpm lint`、`corepack pnpm typecheck`、`corepack pnpm test` 可运行。
- `apps/agent-web` 本地启动后显示基础工作台壳、全局错误边界和空状态。
- `backend-api` 测试覆盖成功响应、统一错误响应、traceId 和请求取消。
- `event-stream-client` 测试覆盖连接、断开、重复事件去重和 `Last-Event-ID` 更新。
