# Chat Activity and History State Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让对话活动流可控折叠、历史切换有即时加载反馈，并确保任务终态立即停止动画。

**Architecture:** 复用 `FigmaChatPanel` 现有文件摘要与展开状态，删除文件工具的重复通用渲染；由 `AgentWorkbench` 统一维护历史切换和运行 busy 状态。消息正文作为历史切换的首要结果，反馈状态异步补齐。

**Tech Stack:** Vue 3、TypeScript、Vitest、Vue Test Utils

---

## Chunk 1: 对话活动流

### Task 1: 文件活动折叠与去重

**Files:**
- Modify: `frontend/apps/agent-web/tests/FigmaChatPanel.test.ts`
- Modify: `frontend/apps/agent-web/src/components/FigmaChatPanel.vue`

- [ ] **Step 1: Write failing component tests**

增加断言：探索区默认收起并可展开/收起；write 只出现一份文件摘要；目录型 read 输出不显示独立目录卡片，文件型 read 输出仍保留。

- [ ] **Step 2: Run tests and verify RED**

Run: `cd frontend && corepack pnpm vitest run apps/agent-web/tests/FigmaChatPanel.test.ts`

Expected: 新增测试因探索区强制展开、write 重复渲染和目录卡片存在而失败。

- [ ] **Step 3: Implement minimal rendering changes**

恢复探索区对 `expandedFileKeys` 的使用；让所有文件读写工具退出 `messageOtherParts`；过滤 `readOutputs` 中的目录结果。

- [ ] **Step 4: Run focused tests and verify GREEN**

Run: `cd frontend && corepack pnpm vitest run apps/agent-web/tests/FigmaChatPanel.test.ts`

Expected: PASS。

## Chunk 2: 历史加载与终态

### Task 2: 历史正文优先显示

**Files:**
- Modify: `frontend/apps/agent-web/tests/FigmaChatPanel.test.ts`
- Modify: `frontend/apps/agent-web/tests/workbench.spec.ts`
- Modify: `frontend/apps/agent-web/src/components/AgentWorkbench.vue`
- Modify: `frontend/apps/agent-web/src/components/FigmaChatPanel.vue`

- [ ] **Step 1: Write failing tests**

增加断言：点击历史会话后立即出现加载提示；正文请求完成后提示消失；反馈请求未完成时正文已经可见。

- [ ] **Step 2: Run tests and verify RED**

Run: `cd frontend && corepack pnpm playwright test apps/agent-web/tests/workbench.spec.ts --grep "history loading"`

Expected: 新增测试因缺少历史切换状态而失败。

- [ ] **Step 3: Implement minimal loading state**

在 `AgentWorkbench` 增加会话切换状态并传给 `FigmaChatPanel`；消息返回后立即更新正文，反馈状态使用非阻塞异步加载。

- [ ] **Step 4: Run focused tests and verify GREEN**

Run: `cd frontend && corepack pnpm playwright test apps/agent-web/tests/workbench.spec.ts --grep "history loading"`

Expected: PASS。

### Task 3: 终态优先停止动画

**Files:**
- Modify: `frontend/apps/agent-web/tests/follow-up-queue.test.ts`
- Modify: `frontend/apps/agent-web/src/components/follow-up-queue.ts`
- Modify: `frontend/apps/agent-web/src/components/AgentWorkbench.vue`

- [ ] **Step 1: Write failing status tests**

为 busy 判定增加覆盖：Run 仍为运行中但 chat reducer 已终态时返回 false；chat reducer 未终态且 Run 运行中时返回 true；启动请求 pending 时返回 true。

- [ ] **Step 2: Run tests and verify RED**

Run: `cd frontend && corepack pnpm vitest run apps/agent-web/tests/follow-up-queue.test.ts`

Expected: 新 helper 尚不存在或旧 OR 逻辑返回错误结果。

- [ ] **Step 3: Implement terminal-precedence helper**

在已有工作台工具模块中增加纯函数并由 `AgentWorkbench` 复用，避免组件内继续叠加互相矛盾的状态条件。

- [ ] **Step 4: Run focused tests and verify GREEN**

Run: `cd frontend && corepack pnpm vitest run apps/agent-web/tests/follow-up-queue.test.ts`

Expected: PASS。

## Chunk 3: 文档、回归与运行

### Task 4: 文档和验证

**Files:**
- Modify: `frontend/apps/agent-web/README.md`
- Modify: `frontend/packages/agent-chat/README.md`
- Modify if needed: `.agents/session-log.md`

- [ ] **Step 1: Update stable documentation**

记录文件活动折叠、历史加载态和终态优先规则；不记录已移除的对话内 Diff 能力。

- [ ] **Step 2: Run frontend verification**

Run:

```bash
cd frontend
corepack pnpm test
corepack pnpm typecheck
corepack pnpm build
```

Expected: 全部 exit 0。

- [ ] **Step 3: Start the real development services**

Run: `./restart-dev-services.sh`

Expected: 后端、manager、前端启动成功，并输出前端访问地址。

- [ ] **Step 4: Verify the running UI**

在真实页面验证探索和 write 可折叠、历史加载提示出现、任务完成后无动画。

- [ ] **Step 5: Review session log and commit only scoped files**

检查 `.agents/session-log.md` 和 `git diff`，仅暂存本次相关文件，使用中文 commit 信息提交。
