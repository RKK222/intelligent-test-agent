# 前端编码规范

本规范适用于完全自研的 `frontend/` 工程。

## 基本原则

1. 先读 `AGENTS.md`、`docs/frontend/*.md`、`docs/api/*.md` 和目标 package README。
2. 只改与任务相关的最小范围，不顺手重构无关组件、样式或状态。
3. Web IDE 能力按 package 边界沉淀，避免把业务逻辑堆到页面入口。
4. 组件、状态、API client、RunEvent SSE client 和 UI kit 边界必须清晰。
5. 人工维护的复杂逻辑必须有中文注释，说明业务意图、边界和异常分支。

## API 访问

1. 只能通过 `packages/backend-api` 访问平台后端服务，当前由 `test-agent-app` 装配运行。
2. 不得直连 opencode server。
3. 不得在组件中直接拼接后端 URL。
4. API 请求、响应、错误类型必须与 `docs/api/backend-api.md` 一致。
5. 新增或变更 API 必须同步更新 `docs/api/backend-api.md` 和 `docs/frontend/frontend-backend-contract.md`。

## RunEvent SSE

1. 只能通过 `packages/event-stream-client` 订阅平台 `RunEvent SSE`。
2. SSE client 必须处理连接、断线、重连、`Last-Event-ID`、重复事件和取消订阅。
3. 高频事件不得逐条触发重型渲染，必须合并、节流或按面板局部更新。
4. 事件类型和字段变更必须同步 `docs/api/event-stream-api.md`。

## 组件与状态

1. API 远端状态优先放在 TanStack Query。
2. 跨面板 UI 状态和工作台状态可放在 Zustand。
3. 单组件内部临时状态优先使用 React 本地状态。
4. 不把密钥、token 或敏感内容放入可持久化前端状态。
5. Dockview 面板恢复必须使用稳定 id，避免刷新后丢失上下文。

## Web IDE 包边界

1. `workbench-shell` 只负责布局和面板生命周期，不写具体业务请求。
2. `file-explorer` 负责文件树和文件状态，不直接保存编辑器内容。
3. `editor` 负责 Monaco 编辑体验，不直接启动智能体任务。
4. `diff-viewer` 负责变更预览和接受/拒绝，不直接调用 opencode server。
5. `agent-chat` 负责对话和卡片呈现，任务执行请求必须走 `backend-api`。
6. `test-runner` 负责测试运行视图，测试状态来源必须是后端 API 或 RunEvent SSE。
7. `report-viewer` 负责报告和失败分析，不直接读取数据库或后端内部存储。
8. `skill-studio` 负责技能编辑和调试 UI，运行请求必须走平台后端。

## UI 与交互

1. 使用 Tailwind 和 `packages/ui-kit` 建立统一设计语言。
2. 工具按钮优先使用图标和 tooltip。
3. 工作台、编辑器、文件树、Diff、报告等固定格式区域必须有稳定尺寸和响应式约束。
4. loading、empty、error、retry、cancel 状态必须完整。
5. 文案必须面向测试智能体工作流，避免把内部实现细节暴露给最终用户。
6. Phase 07 文件搜索只过滤已加载文件树的文件名，不在前端自行扫描工作区，也不绕过后端新增搜索能力。
7. Phase 08 Diff 的后端接受/拒绝是 Run 级语义；当前文件按钮只能作为选择和反馈，不得暗示 per-file 后端回滚。

## 中文注释

1. 人工维护的复杂组件、hook、事件流处理、Diff 应用逻辑、状态同步逻辑必须写中文注释。
2. 注释说明业务意图和边界，不重复描述 JSX 或普通赋值。
3. generated、自动生成或第三方复制源码不为了补注释而修改。

## 文档同步

前端修改必须检查并同步：

- `docs/frontend/*.md`。
- `docs/api/backend-api.md`。
- `docs/api/event-stream-api.md`。
- `frontend/README.md`。
- 目标 app/package README。
- 目标源码包 PACKAGE.md。
