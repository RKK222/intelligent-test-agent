# 对话面板基线回退 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将对话样式与投影恢复到 `976a798211` 行为基线，并证明历史 ask/permission、新会话原生事件和成功/失败/取消终态均可用。

**Architecture:** 使用提交级反向还原，只撤销专注阅读、活动浮层、Timeline 样式、时间戳和最终文本标记。保留 `.agents/session-log.md` 历史以及进程状态卡、宠物旁路问答等无关改动；所有消息继续走既有 RunEvent reducer 和 `OpencodeTimeline`。

**Tech Stack:** Git、Vue 3、TypeScript、Vitest、Playwright、Spring Boot、OpenCode SSE。

---

## Chunk 1: 安全回退

### Task 1: 建立回退前证据与失败测试

**Files:**
- Inspect: `frontend/apps/agent-web/src/components/FigmaChatPanel.vue`
- Inspect: `frontend/packages/agent-chat/src/opencode-like/`
- Inspect: `.agents/session-log.md`

- [ ] 记录当前 HEAD、工作区状态和六个目标提交的文件清单。
- [ ] 保存 `.agents/session-log.md` 当前内容作为冲突恢复依据。
- [ ] 确认 `976a798211` 是当前 HEAD 祖先。
- [ ] 在 `FigmaChatPanel.test.ts` 增加基线行为测试：专注按钮/活动浮层不存在、所有原生 part 始终可见、终态不再显示 running；运行并确认当前实现按预期失败。
- [ ] 运行：`cd frontend && corepack pnpm test --run apps/agent-web/tests/FigmaChatPanel.test.ts`；预期至少因专注按钮仍存在而 FAIL。
- [ ] 记录失败输出后，用 `apply_patch` 暂时移除新增测试，确保回退命令在干净工作树执行；Task 3 再用同一补丁原样恢复测试并验证转绿，不能降低断言。

### Task 2: 反向还原对话提交

**Files:**
- Modify: `frontend/apps/agent-web/src/components/FigmaChatPanel.vue`
- Modify: `frontend/apps/agent-web/src/components/AgentWorkbench.vue`
- Modify/Delete: `frontend/apps/agent-web/src/components/ChatActivityPanel.vue`
- Modify/Delete: `frontend/apps/agent-web/src/components/chat-activity-summary.ts`
- Modify: `frontend/packages/agent-chat/src/opencode-like/`
- Modify/Delete: 对应测试与专注模式设计/计划文档

- [ ] 依次执行 `git revert --no-commit 89fe791e b0723df4 12fa3b16 9cd1af7c a26f354b 32607785`。
- [ ] 冲突时保留当前进程状态卡、宠物旁路问答和无关 README 内容。
- [ ] 将 `.agents/session-log.md` 恢复为回退前内容，不删除历史记录。
- [ ] 检查页面不再包含 `chat-timeline-mode-toggle`、`ChatActivityPanel` 或 focused reading 状态。

## Chunk 2: 自动化回归

### Task 3: 完善并验证回退测试

**Files:**
- Modify: `frontend/apps/agent-web/tests/FigmaChatPanel.test.ts`
- Modify: `frontend/apps/agent-web/tests/workbench-utils.test.ts`
- Test: `frontend/packages/agent-chat/tests/runtime-reducer.test.ts`
- Test: `frontend/packages/agent-chat/tests/opencode-timeline.test.ts`

- [ ] 保留 Task 1 已验证失败的基线测试，按回退后的原生 DOM 调整既有冲突断言，不降低行为要求。
- [ ] 对 text、reasoning、tool、file、task/subagent、retry、diff、todo 分别断言保留原生行与交互。
- [ ] 覆盖当前与历史 `question.asked`、`permission.asked`，以及 reply/reject emit。
- [ ] 覆盖成功、失败、取消终态下 running 行、计时、输入区与历史状态收敛。
- [ ] 运行：`cd frontend && corepack pnpm test --run apps/agent-web/tests/FigmaChatPanel.test.ts apps/agent-web/tests/workbench-utils.test.ts packages/agent-chat/tests/runtime-reducer.test.ts packages/agent-chat/tests/opencode-timeline.test.ts`；预期全部 PASS（仅允许仓库既有 skip）。
- [ ] 运行：`cd frontend && corepack pnpm exec playwright test apps/agent-web/tests/workbench.spec.ts --project=chromium`；预期 ask/permission 和成功/失败/取消状态用例 PASS。

### Task 4: 相邻存量能力回归

**Files:**
- Test: `frontend/apps/agent-web/tests/FigmaShell.test.ts`
- Test: `frontend/packages/file-explorer/tests/DirectoryRows.test.ts`
- Test: `backend/test-agent-opencode-runtime/src/test/java/com/icbc/testagent/opencode/runtime/runtime/OpencodeRuntimeApplicationServiceTest.java`

- [ ] 运行：`cd frontend && corepack pnpm test --run apps/agent-web/tests/FigmaShell.test.ts packages/file-explorer/tests/DirectoryRows.test.ts`。
- [ ] 运行：`cd backend && mvn -pl test-agent-opencode-runtime -am -Dtest=OpencodeRuntimeApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- [ ] 运行：`cd frontend && corepack pnpm typecheck && corepack pnpm lint && corepack pnpm build && corepack pnpm test`；记录全部结果，预期除已确认的非本次基线问题外无新增失败。

## Chunk 3: 真实环境验收与交付

### Task 5: 启动真实服务

- [ ] 使用 `.env.test`、`test` profile、JDK 25 执行 `./restart-dev-services.sh --profile test --env-file .env.test --skip-frontend-build`。
- [ ] 校验 backend health/readiness、frontend HTTP、CORS 和 manager health。

### Task 6: 真实 OpenCode 交互

- [ ] 新建会话发送“必须使用 question 工具询问我选择 A 或 B，收到答案后只回复选择结果”；确认弹框出现、提交答案、工具完成、模型继续、Run 成功结束。
- [ ] 新建会话发送“必须使用 read 工具读取工作区之外的 `/etc/hosts`，不要换别的方法”；利用 OpenCode 原生 `external_directory` 权限稳定触发真实 `permission.asked`，不修改 `.env*` 或用户全局配置；完成一次允许或拒绝回复。若当前 OpenCode 版本明确不再为该路径请求权限，记录原始事件和配置证据并保持目标未完成，不能用 mock 冒充真实验收。
- [ ] question/permission 弹出后先不回答，点击新建对话，再从历史列表切回该运行中会话；确认 pending 控件恢复并完成回复。
- [ ] 成功：使用 question 回合正常完成；取消：运行中点击终止；失败：Playwright 通过既有 mock RunEvent 注入 `run.failed`。三种情况都确认 Timeline、计时、输入区和历史状态收敛。

### Task 7: 文档与提交

- [ ] 同步 agent-web/agent-chat 稳定文档，删除失效专注模式说明。
- [ ] 在 `.agents/session-log.md` 新增本次回退记录。
- [ ] 运行 `git diff --name-status 976a798211 --` 并对 `FigmaChatPanel`、`opencode-like/` 做逐 hunk 审计；只允许进程状态卡、宠物旁路问答、平台标题同步等白名单差异，禁止残留 focused reading、activity overlay、final summary marker 和目标样式覆盖。
- [ ] 执行 `git diff --check`、检查无合并标记及无无关暂存。
- [ ] 使用中文提交信息提交全部回退与测试。
