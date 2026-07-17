# 前端规范

本规范适用于完全自研的 `frontend/` 工程，以及独立的 `frontend-opencode` Vue/Vite opencode 复刻工程。技术栈版本以各自 README 为单一来源；包职责与访问边界见 `docs/architecture/module-map.md` 和 `docs/architecture/dependency-rules.md`；前后端契约见 `docs/api/`。

## 基本原则

1. 先读 `AGENTS.md`、`docs/standards/frontend.md`、`docs/api/` 和目标 package README。
2. 只改与任务相关的最小范围，不顺手重构无关组件、样式或状态。
3. Web IDE 能力按 package 边界沉淀，避免把业务逻辑堆到页面入口。
4. 人工维护的复杂逻辑必须有中文注释，说明业务意图、边界和异常分支。

## API 访问

1. 只能通过 `packages/backend-api` 访问平台后端服务（当前由 `test-agent-app` 装配运行），不得直连 opencode server，不得在组件中直接拼接后端 URL。
2. Run、Diff 和 runtime 相关请求默认使用 `agentId=opencode` 的 `/api/internal/agent/{agentId}/...` URL；切换 agent 只能通过 `backend-api` 配置，不得在页面组件中手拼旧 runtime URL。
3. 工作区文件和 Agent 配置文件的目录列表、读取、写入、上传、复制和移动只能通过 `backend-api` 的文件 WebSocket route/ticket/RPC helper；页面组件不得回退到 HTTP 文件接口或自行拼接 WebSocket URL。公共 Agent worktree/直接目录切换只能更新 `worktreeId/linuxServerId` 上下文，后续文件操作仍由 `backend-api` 申请 route 和 ticket。
4. API 请求、响应、错误类型必须与 `docs/api/http-api.md` 一致；新增或变更 API 必须同步 `docs/api/http-api.md` 和 `docs/architecture/module-map.md`。
5. 前端调试用原始报文查看器只能通过 `backend-api` 的可选 observer 捕获浏览器可访问的请求体和响应文本，不得记录 `Authorization`、Cookie 等敏感请求头，不得新增后端持久化或绕过平台后端直连 opencode。

## RunEvent SSE

1. 只能通过 `packages/event-stream-client` 订阅平台 `RunEvent SSE`，必须处理连接、断线、重连、`Last-Event-ID`、重复事件和取消订阅。
2. RunEvent SSE 默认使用 `agentId=opencode` 的 `/api/internal/agent/{agentId}/runs/{runId}/events` URL；旧 `/api/runs/{runId}/events` 只作为后端兼容入口。
3. 高频事件不得逐条触发重型渲染，必须合并、节流或按面板局部更新。
4. 事件类型和字段变更必须同步 `docs/api/event-stream.md`。SSE 契约以该文件为单一事实源。
5. 原始 SSE 调试回调只能保存浏览器 `EventSource` 暴露的 `MessageEvent.data`、事件名和 `lastEventId` 等前端可见字段；它不是完整 HTTP wire bytes，也不得替代 RunEvent 契约文档。
6. `run.snapshot.reset` 是 transient 恢复事件，不设置 SSE `id`。event client 不得从 payload `seq/eventId` 或 `snapshot.runtimeVersion` 推导 durable 游标；reducer 必须先清空当前 Run 运行投影，再按 `snapshot.events` 顺序重放，空/缺失/未知字段安全兼容。页面若维护 reducer 外的 Diff、通知或工具跟随状态，必须在 reset 时同步清理并重放，且不得重复触发用户通知。

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

## 字体与字号

前端全局排版必须使用统一字体族：英文使用 `Geist Sans`，中文使用 `Noto Sans SC`，代码与等宽内容使用 `Geist Mono`。新增或调整组件样式时，应优先复用全局 token 和 `ui-kit` 组件尺寸，避免在业务组件中散落不一致的字号和字重。

文件浏览区例外：工作区文件树、搜索结果、变更列表和 Agent 配置树可使用 `--ta-tree-*` 局部 token 模拟 VS Code Workbench 信息密度，字体栈限定为系统 UI 字体，字号为 13px，行高为 22px。该例外只能用于文件浏览区，不得扩散到聊天正文、设置页、编辑器正文或普通表单。

| 场景 | 字号 | 字重 |
| --- | ---: | --- |
| 页面标题 | 30-36px | 700 |
| 一级标题（H1） | 28-32px | 700 |
| 二级标题（H2） | 24px | 600 |
| 三级标题（H3） | 20px | 600 |
| **正文（默认聊天内容）** | **16px** | 400 |
| 次要说明文字 | 14px | 400 |
| Caption | 12px | 400 |
| 按钮文字 | 14px | 500 |
| 输入框文字 | **16px** | 400 |
| 代码块 | 14px | 400（`Geist Mono`） |

## 样式与组件编码规范

### 必须遵守

1. **基础 UI 必须优先使用 `packages/ui-kit`**：Button、Input、Badge、Tabs、Toast、Dialog、Tooltip、Select 等通用组件不得在业务页面重复实现。
2. **重复出现的 UI 必须抽组件**：相同 class 组合、DOM 结构或状态样式出现 2 次以上，应抽取为组件、variant 或公共布局。
3. **组件 class 合并必须统一使用 `cn`**：不允许手动字符串拼接 class；`cn` 内部应统一组合 `clsx` 和 `tailwind-merge`。
4. **基础组件变体必须使用 `cva` 管理**：`variant`、`size`、`state` 等稳定变体应集中定义，不得散落在业务页面。
5. **业务页面应组合组件，不应堆 Tailwind**：页面可以写布局 class，但不应重复编写复杂组件样式。
6. **颜色必须优先使用语义 token**：优先使用 `primary`、`secondary`、`muted`、`accent`、`destructive`、`background`、`foreground`、`border` 等语义 token；不得随意写死品牌色、灰阶或十六进制颜色。
7. **尺寸必须遵循统一体系**：基础组件应统一使用 `sm`、`md`、`lg`、`icon` 等尺寸语义；不得随意使用任意尺寸值，除非是 Monaco、Dockview、Terminal 等第三方集成需要。
8. **`ui-kit` 和业务组件必须分层**：通用基础组件放入 `ui-kit`；Web IDE / 工作台专用组件不得混入 `ui-kit`，应放入独立工作台组件层或业务模块。
9. **状态 UI 必须统一组件化**：Loading、Empty、Error、Disabled、Selected、Active 等通用状态不得在各页面重复实现。
10. **图标按钮必须统一封装**：所有图标按钮应使用统一组件，并提供可访问名称。
11. **复杂业务组件不得滥用 `cva`**：`cva` 只用于稳定、有限的组件变体；复杂业务状态应通过拆分组件和清晰的状态逻辑处理。
12. **长列表中不得进行复杂 class 计算**：虚拟列表、消息流、日志流、文件列表等高频渲染场景中，应避免重复执行复杂 `cn` 或变体计算。
13. **Tailwind class 顺序必须保持一致**：应使用统一排序规则或格式化工具，避免 class 顺序混乱。
14. **组件 API 不得过度暴露内部 class**：优先通过 props、variant、slot 和组合组件扩展；不得随意增加大量 `xxxClass` 属性。
15. **新增样式前必须先检查是否已有组件或 variant**：已存在的样式能力不得重复实现；可复用能力应沉淀到 `ui-kit`、工作台组件层或公共工具中。

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
3. 中间 Monaco 源码区默认按可视宽度自动换行；文件树复制/剪切/粘贴/撤销、拖动和上传必须在 `canWrite=false` 时同时隐藏并在事件处理层阻断。上传入口必须由工作区根或目标目录的 `+` 打开并展示目标路径，拖放结束必须清理全部目标高亮；撤销历史不得跨个人 worktree 保留。

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

1. `backend-api` client 的请求、响应、错误、超时、取消和默认/自定义 agent URL。
2. `event-stream-client` 的 RunEvent SSE 连接、agent URL、重连、去重和断点恢复。
3. `workbench-shell` 的面板注册、布局恢复和关闭行为。
4. `file-explorer` 的文件树展示、搜索、文件状态和打开文件。
5. `editor` 的文件加载、编辑、保存、只读状态和错误状态。
6. `diff-viewer` 的 Diff 展示、接受、拒绝和结果反馈。
7. `agent-chat` 的消息发送、实时输出、用户气泡、reasoning/text 分离、任务分解、Skill/Tool 分类展示、sticky scroll、TimelineCard 折叠卡片和默认展开规则。
8. `test-runner` 的启动、取消、重试和状态变化。

### 改动对应测试

- 改 API client：补请求、响应、错误、超时、取消、鉴权头和 agentId URL 测试。
- 改 RunEvent SSE：补 agent-scoped URL、连接、断线、`Last-Event-ID`、重复事件、乱序事件和取消订阅测试。
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
