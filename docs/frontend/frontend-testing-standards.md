# 前端测试规范

本规范适用于完全自研的 `frontend/` 工程。

## 当前命令

在 `frontend/` 目录执行：

```bash
corepack pnpm install
corepack pnpm lint
corepack pnpm typecheck
corepack pnpm test
corepack pnpm build
corepack pnpm e2e
corepack pnpm e2e:real
```

本机 `pnpm` 可能不在 PATH，前端命令统一通过 Corepack 调用。

## 测试范围

前端测试至少覆盖：

1. `backend-api` client 的请求、响应、错误、超时和取消。
2. `event-stream-client` 的 RunEvent SSE 连接、重连、去重和断点恢复。
3. `workbench-shell` 的面板注册、布局恢复和关闭行为。
4. `file-explorer` 的文件树展示、搜索、文件状态和打开文件。
5. `editor` 的文件加载、编辑、保存、只读状态和错误状态。
6. `diff-viewer` 的 Diff 展示、接受、拒绝和结果反馈。
7. `agent-chat` 的消息发送、实时输出、用户气泡、reasoning/text 分离、任务分解、Skill/Tool 分类展示、sticky scroll、TimelineCard 折叠卡片、Plan/Tool/Test/Diff/Event 展示和默认展开规则。
8. `test-runner` 的启动、取消、重试和状态变化。
9. 后续引入 `report-viewer` 时覆盖报告详情、失败分析、Trace、截图、日志。
10. 后续引入 `skill-studio` 时覆盖技能编辑、参数配置、调试和运行结果。

## 改动对应测试

- 改 API client：补请求、响应、错误、超时、取消和鉴权头测试。
- 改 RunEvent SSE：补连接、断线、`Last-Event-ID`、重复事件、乱序事件和取消订阅测试。
- 改工作台：补面板打开、关闭、恢复、布局持久化测试。
- 改文件树：补空目录、大目录、搜索、文件状态和权限错误测试。
- 改编辑器：补加载、保存、脏状态、只读状态和保存冲突测试。
- 改 Diff：补 Run 级接受、Run 级拒绝、当前文件反馈、冲突和失败回滚测试。
- 改智能体对话：补消息发送、流式事件、用户气泡、思考/回答分离、任务分解、Skill/Tool 分类展示、sticky scroll、卡片展示、默认展开/收起、错误和重试测试。
- 改测试面板：补运行状态、取消、失败重试和报告入口测试。
- 改报告：补失败详情、Trace、截图、日志和缺失数据测试。
- 改 Skill Studio：补技能参数、调试执行、错误展示和历史记录测试。

Phase 06-08 当前已落地的测试重点：

- Vitest 覆盖 `backend-api` 成功和统一错误解析。
- Vitest 覆盖 `event-stream-client` 事件解析、重复事件去重和关闭订阅。
- Vitest 覆盖文件名过滤、Monaco 语言识别和 unified diff 解析。
- Playwright 覆盖工作台首屏、workspace 加载、目录展开、打开文件和保存入口。

Phase 11 当前已落地的测试重点：

- Vitest 覆盖 prompt 附件到 `PromptPart` 的转换、follow-up FIFO 队列、Diff hunk 解析/导航、编辑器选区上下文、terminal ticket client 和 PTY WebSocket envelope。
- Vitest 覆盖 RunEvent reducer 的旧 `assistant.message.delta` 兼容、新 `message.part.delta` 合并、reasoning delta 类型保留、permission/question/todo/diff/session status 状态归并，以及 event-stream-client 的 `lastEventId` query 续传和 eventId 优先去重。
- Vitest 覆盖 Agent 对话线程 sticky scroll、assistant-ui 文本汇总、用户浅灰气泡、reasoning 折叠块、任务分解、`tool=skill` 的 Skill 调用展示、TimelineCard 标题/展开按钮、Diff 文件表格与默认展开规则，确保 reasoning 思考过程不会混入最终回答文本。
- Playwright 只匹配 `*.spec.ts`，避免误加载 app 目录下的 Vitest `*.test.tsx`。
- Playwright mock 平台后端 API 和 RunEvent SSE，覆盖文件/图片附件提交、permission dock、question dock、Diff 卡片、hunk 导航入口、hunk context 反馈、只读 transcript 和 terminal ticket 创建入口。
- `frontend/playwright.real.config.ts` 只匹配 `*.real-spec.ts`，`corepack pnpm e2e:real` 必须配合真实 `test-agent-app`、前端和 opencode server 使用，不能用 mock E2E 替代。
- `tools/dev-phase11-real-e2e.sh` 是 Phase 11 真实三服务验收入口：默认复用已有服务，`--start-services` 会先启动本地 Postgres、opencode server 和后端，再由 Playwright 管理前端 dev server；脚本日志保留在 `.tmp/phase11-real-e2e/`，不得打印 dotenv 敏感值。
- 当前真实三服务 E2E 尚无最新通过记录；文档、PR 或提交说明不得把 Phase 11 real E2E 标记为完成，直到重新运行 `tools/dev-phase11-real-e2e.sh --start-services` 并记录通过结果。

## Mock 原则

1. 前端测试 mock `test-agent-app` API，不 mock 内部组件行为。
2. 事件流测试必须模拟多事件、断线、重连、重复事件和最后事件 id。
3. 组件测试应优先从用户交互出发，避免只断言内部状态。
4. API 类型测试必须覆盖新增字段和旧字段兼容场景。
5. E2E mock 必须使用 `docs/api/backend-api.md` 中记录的后端 DTO 字段，例如文件列表使用 `directory` 而不是前端展示态 `type`。
6. 真实 E2E 必须通过 `backend-api` 和平台 WebSocket/SSE 入口验证，不得让前端或测试代码直连 opencode 公网 share API。
7. `frontend-opencode` 和 `opencode-source/opencode-1.17.8/packages/frontend-opencode` 的测试不属于平台前端验收；除非后续正式纳入 workspace，否则不能替代 `frontend/` 下的 Vitest、mock E2E 或 real E2E。

## 完成标准

前端任务完成前必须说明：

- 跑了哪些前端测试命令。
- 哪些交互场景已覆盖。
- 是否影响 API 文档或事件流文档。
- 是否影响性能、安全或兼容性。
- 哪些 README 或 PACKAGE.md 已同步。
