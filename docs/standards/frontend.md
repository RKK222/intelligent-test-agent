# 前端规范

本规范适用于完全自研的 `frontend/` 工程，以及独立的 `frontend-opencode` Vue/Vite opencode 复刻工程。技术栈版本以各自 README 为单一来源；包职责与访问边界见 `docs/architecture/module-map.md` 和 `docs/architecture/dependency-rules.md`；前后端契约见 `docs/api/`。

## 基本原则

1. 先读 `AGENTS.md`、`docs/standards/frontend.md`、`docs/api/` 和目标 package README。
2. 只改与任务相关的最小范围，不顺手重构无关组件、样式或状态。
3. Web IDE 能力按 package 边界沉淀，避免把业务逻辑堆到页面入口。
4. 人工维护的复杂逻辑必须有中文注释，说明业务意图、边界和异常分支。

## API 访问

1. 只能通过 `packages/backend-api` 访问平台后端服务（当前由 `test-agent-app` 装配运行），不得直连 opencode server，不得在组件中直接拼接后端 URL。
2. API 请求、响应、错误类型必须与 `docs/api/http-api.md` 一致；新增或变更 API 必须同步 `docs/api/http-api.md` 和 `docs/architecture/module-map.md`。

## RunEvent SSE

1. 只能通过 `packages/event-stream-client` 订阅平台 `RunEvent SSE`，必须处理连接、断线、重连、`Last-Event-ID`、重复事件和取消订阅。
2. 高频事件不得逐条触发重型渲染，必须合并、节流或按面板局部更新。
3. 事件类型和字段变更必须同步 `docs/api/event-stream.md`。SSE 契约以该文件为单一事实源。

## 组件与状态

1. `frontend/` 主 workspace 的 API 远端状态优先放在 `@tanstack/vue-query`，工作台级 UI 状态放在 Pinia store；`frontend-opencode` 同样使用 Pinia 承载 opencode parity 状态；单组件内部临时状态用 Vue 组合式 `ref`/`reactive` 保持。
2. 不把密钥、token 或敏感内容放入可持久化前端状态。
3. Dockview 面板恢复必须使用稳定 id，避免刷新后丢失上下文。
4. 当前事件流应优先按 `eventId` 去重，兼容旧事件时才回退 `runId + seq`；`seq=0` transient 文本事件不能因为相同 seq 被错误丢弃。

## 包边界

包职责与依赖方向见 `docs/architecture/module-map.md`。核心红线：

1. `workbench-shell` 只负责布局和面板生命周期，不写业务请求。
2. `file-explorer` 负责文件树和文件状态，不直接保存编辑器内容。
3. `editor` 负责 Monaco 编辑体验，不直接启动智能体任务。
4. `diff-viewer` 负责变更预览和接受/拒绝，不直接调用 opencode server；Diff 接受/拒绝是 Run 级语义，当前文件按钮只作为选择和反馈，不承诺 per-file 后端回滚。
5. `agent-chat` 负责对话和卡片呈现，任务执行请求必须走 `backend-api`。
6. `test-runner` 负责测试运行视图，测试状态来源必须是后端 API 或 RunEvent SSE。
7. `terminal` 负责 ticket WebSocket 连接、输入、resize、关闭和输出渲染，不创建 ticket、不直连 opencode server。
8. 文件搜索只过滤已加载文件树的文件名，不在前端自行扫描工作区，也不绕过后端新增搜索能力。

## UI 与交互

1. 使用 Tailwind 和 `packages/ui-kit` 建立统一设计语言，工具按钮优先使用图标和 tooltip。
2. 工作台、编辑器、文件树、Diff、报告等固定格式区域必须有稳定尺寸和响应式约束。
3. loading、empty、error、retry、cancel 状态必须完整；文案面向测试智能体工作流，避免暴露内部实现细节。

## 性能

### 首屏与加载

1. 首屏只加载工作台启动必需数据；大列表、大文件树、长日志和长报告必须分页、虚拟化或懒加载。
2. 非首屏能力按需加载（报告详情、Skill Studio、截图预览）；避免启动阶段并发请求无关接口。
3. Vite/路由/package 拆分必须控制首屏 bundle；Monaco 编辑器和 Monaco Diff 必须懒加载，不进入首屏同步 bundle。

### SSE 渲染

1. 高频事件批量合并、节流或局部状态更新；断开 SSE 时释放订阅、定时器和缓存引用。
2. `Last-Event-ID` 恢复不能导致重复渲染不可幂等 UI；日志类输出必须限制 DOM 节点数量，必要时用虚拟列表。

### 工作台与编辑器

1. Dockview 面板恢复不能阻塞首屏交互；Monaco 编辑器和 Diff 组件按需加载。
2. 大文件打开前应有大小检查和只读策略；Diff 展示避免一次性渲染超大变更；面板切换时不得重复初始化重型实例。

### 请求与缓存

1. 相同资源避免重复请求；请求必须支持取消或过期保护；页面切换时释放无用请求和订阅。
2. 缓存必须有失效策略；`@tanstack/vue-query` 的 query key 必须稳定。

### Bundle

1. 功能包按 Web IDE 能力拆分；大型依赖必须评估包体影响。
2. `ui-kit` 不引入与现有设计系统冲突的重量级依赖；`shared-types` 只放类型和轻量常量。

## 测试

在 `frontend/` 目录执行（本机 pnpm 可能不在 PATH，统一通过 Corepack 调用）：

```bash
corepack pnpm install
corepack pnpm lint
corepack pnpm typecheck
corepack pnpm test
corepack pnpm build
corepack pnpm e2e
corepack pnpm e2e:real
```

### 测试范围

至少覆盖：

1. `backend-api` client 的请求、响应、错误、超时和取消。
2. `event-stream-client` 的 RunEvent SSE 连接、重连、去重和断点恢复。
3. `workbench-shell` 的面板注册、布局恢复和关闭行为。
4. `file-explorer` 的文件树展示、搜索、文件状态和打开文件。
5. `editor` 的文件加载、编辑、保存、只读状态和错误状态。
6. `diff-viewer` 的 Diff 展示、接受、拒绝和结果反馈。
7. `agent-chat` 的消息发送、实时输出、用户气泡、reasoning/text 分离、任务分解、Skill/Tool 分类展示、sticky scroll、TimelineCard 折叠卡片和默认展开规则。
8. `test-runner` 的启动、取消、重试和状态变化。

### 改动对应测试

- 改 API client：补请求、响应、错误、超时、取消和鉴权头测试。
- 改 RunEvent SSE：补连接、断线、`Last-Event-ID`、重复事件、乱序事件和取消订阅测试。
- 改工作台/文件树/编辑器/Diff/对话/测试面板：按对应交互场景补回归测试。

### Mock 原则

1. 前端测试 mock `test-agent-app` API，不 mock 内部组件行为；组件测试优先从用户交互出发。
2. 事件流测试必须模拟多事件、断线、重连、重复事件和最后事件 id。
3. E2E mock 必须使用 `docs/api/http-api.md` 中记录的后端 DTO 字段（例如文件列表使用 `directory` 而不是前端展示态 `type`）。
4. 真实 E2E 必须通过 `backend-api` 和平台 WebSocket/SSE 入口验证，不得让前端或测试代码直连 opencode 公网 share API。
5. `frontend-opencode` 的测试属于 opencode Vue/Vite 复刻工程验收，但不能替代 `frontend/` 主 workspace 的 Vitest、mock E2E 或 real E2E；`opencode-source/` 下的测试仍只作为参考。
6. `frontend/playwright.real.config.ts` 只匹配 `*.real-spec.ts`，`corepack pnpm e2e:real` 必须配合真实 `test-agent-app`、前端和 opencode server 使用，不能用 mock E2E 替代；`tools/dev-phase11-real-e2e.sh` 是主 `frontend/` 真实三服务验收入口。
7. `frontend-opencode/playwright.real.config.ts` 只匹配 `tests/e2e-real/*.real-spec.ts`，`cd frontend-opencode && corepack pnpm e2e:real` 是 opencode Vue/Vite 复刻工程自己的真实三服务 smoke 验收入口；该测试仍只能访问平台 `/api`、RunEvent SSE 和平台 PTY WebSocket。

## 完成标准

前端任务完成前必须说明：跑了哪些前端测试命令、覆盖了哪些交互场景、是否影响 API 或事件流文档、是否影响性能/安全/兼容性、哪些 README 已同步。
