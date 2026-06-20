# Phase 06 前端基础工程

## 阶段目标

创建完全自研前端基础工程，落地 Next.js、React、TypeScript、shadcn/ui、Tailwind、assistant-ui、Dockview、Monaco、TanStack Query、Zustand 和 pnpm workspace。

## 可验收功能清单

1. 创建 `frontend/` pnpm workspace。
2. 创建 `apps/agent-web` 自研主应用。
3. 创建 `packages/backend-api` 和 `packages/event-stream-client`。
4. 创建 `packages/ui-kit` 和 `packages/shared-types`。
5. 配置 lint、typecheck、unit test 和基础 e2e test。

## 修改项目

- `frontend/*`
- `docs/frontend/*`
- `docs/api/backend-api.md`
- `docs/api/event-stream-api.md`
- `tools/*`

## 实现功能

- `agent-web` 显示基础工作台壳、全局错误边界和空状态。
- `backend-api` 提供 base URL、traceId、错误解析和示例 API 方法。
- `event-stream-client` 提供 RunEvent SSE 连接、断开和 mock 测试。
- `ui-kit` 提供按钮、输入、对话框、Toast、错误提示等基础组件。
- `shared-types` 提供 Workspace、Session、Run、RunEvent 的前端共享类型。

## 验收方式

- `pnpm install`、`pnpm lint`、`pnpm typecheck`、`pnpm test` 通过。
- `agent-web` 可本地启动并显示基础工作台。
- `backend-api` 测试覆盖成功响应和统一错误响应。
- `event-stream-client` 测试覆盖连接、断开和重复事件。
- 前端 README 和各 package README 已创建。
