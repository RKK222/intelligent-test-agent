# 包说明：frontend/apps/agent-web/src

## 职责

承载 Next.js app router 页面和工作台组合层。

## 主要程序清单

- `app/layout.tsx`：全局页面壳与元信息。
- `app/page.tsx`：工作台首页入口。
- `components/AgentWorkbench.tsx`：组合 workspace、文件树、编辑器、Agent、RunEvent SSE 和 Diff 操作。
- `../next.config.ts`：Next 应用配置，允许本地从 `127.0.0.1` 访问开发资源，保持 Run/取消等客户端交互可用。

## 允许依赖

- `@test-agent/*` 前端 workspace packages。
- Next.js、React、TanStack Query。

## 禁止依赖

- opencode server URL 或 SDK。
- 后端内部实现、数据库或 generated SDK。

## 修改时必须同步更新

- `frontend/README.md`。
- `frontend/apps/agent-web/README.md`。
- `docs/frontend/*`。
- `docs/api/*`，如果影响 API 或事件契约。
