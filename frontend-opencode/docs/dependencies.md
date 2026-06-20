# frontend-opencode 依赖记录

## 运行依赖

| 依赖 | 用途 |
|---|---|
| `vue` | 页面与组件运行时。 |
| `vue-router` | `/`、session、new-session 路由。 |
| `pinia` | workspace、session、prompt、RunEvent、settings、terminal 状态。 |
| `lucide-vue-next` | 工具按钮和状态图标。当前版本由 pnpm 安装提示已弃用，后续可迁移到 `@lucide/vue`。 |
| `@tanstack/vue-virtual` | 预留给长 timeline/session list 虚拟滚动；当前首版 UI 未直接启用。 |
| `@xterm/xterm`、`@xterm/addon-fit` | Terminal 面板的浏览器终端渲染和容器尺寸适配；输入仍通过平台 WebSocket JSON envelope。 |

## 开发与验证依赖

| 依赖 | 用途 |
|---|---|
| `vite`、`@vitejs/plugin-vue` | Vue/Vite 开发与构建。 |
| `typescript`、`vue-tsc`、`@types/node` | 类型检查。 |
| `vitest`、`jsdom`、`@testing-library/vue`、`@testing-library/jest-dom`、`@vue/test-utils` | store、工具函数和组件测试。 |
| `@playwright/test` | mock/real E2E 与视觉验收截图入口。 |

## 复用平台包

本工程不把 `frontend/packages/*` 加入本目录 workspace，而是通过 Vite/TS alias 读取源码：

- `@test-agent/backend-api` -> `../frontend/packages/backend-api/src/index.ts`
- `@test-agent/event-stream-client` -> `../frontend/packages/event-stream-client/src/index.ts`
- `@test-agent/shared-types` -> `../frontend/packages/shared-types/src/index.ts`

这样可以保证 opencode 复刻工程仍遵守“前端只能经平台后端和 RunEvent SSE”的访问边界。
