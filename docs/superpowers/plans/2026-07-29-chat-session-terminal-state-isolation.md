# Chat Session Terminal State Isolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent one failed chat Session from leaking its retry card and failed footer into another Session while preserving failure restoration when returning to the original Session.

**Architecture:** Keep the existing Run terminal-state inference in `FigmaChatPanel` and clear only its three local terminal display flags when an established root Session identity changes. Continue using the newly selected Session's messages and `runtimeStatus` as the authoritative restoration inputs; do not remount the component or change parent runtime orchestration.

**Tech Stack:** Vue 3 Composition API, TypeScript 6, Vitest 4, Vue Test Utils, pnpm workspace.

## Global Constraints

- Do not create a Git branch.
- Do not modify `opencode-source/opencode-1.18.4/` or generated SDK sources.
- Keep the change inside `frontend/apps/agent-web`; do not change HTTP API, RunEvent, DTO, database, backend, security, or environment configuration.
- Treat `currentSessionId` changing from empty to its first real ID as the same draft Run; reset terminal flags only when the previous Session ID is non-empty and changes.
- Add Chinese comments for the Session identity boundary.

---

### Task 1: Isolate terminal display state by Session

**Files:**
- Create: `docs/superpowers/plans/2026-07-29-chat-session-terminal-state-isolation.md`
- Modify: `frontend/apps/agent-web/tests/FigmaChatPanel.test.ts`
- Modify: `frontend/apps/agent-web/src/components/FigmaChatPanel.vue`
- Modify: `frontend/README.md`
- Modify: `frontend/apps/agent-web/README.md`
- Modify: `frontend/apps/agent-web/src/PACKAGE.md`
- Modify: `docs/standards/frontend.md`
- Modify: `docs/architecture/module-map.md`
- Modify: `.agents/session-log.huangzhenren.md`

**Interfaces:**
- Consumes: `FigmaChatPanel` props `currentSessionId?: string`, `messages: ChatMessageInput[]`, `running?: boolean`, and `runtimeStatus?: string`.
- Produces: Session-scoped rendering for `.figma-chat-retry-card` and `.figma-chat-status-failed`; no new exported type, prop, event, or function.

- [x] **Step 1: Add the failing component regression test**

Add this test near the existing task terminal-status tests in `frontend/apps/agent-web/tests/FigmaChatPanel.test.ts`:

```ts
it("does not leak a failed terminal marker into another session", async () => {
  const failedMessages = [{
    id: "u-session-failed",
    messageId: "u-session-failed",
    role: "user" as const,
    text: "触发失败",
    createdAt: "2026-07-29T08:00:00.000Z"
  }];
  const wrapper = mount(FigmaChatPanel, {
    props: {
      currentSessionId: "session_failed",
      messages: failedMessages,
      running: true,
      runtimeStatus: "RUNNING",
      processStatus: { status: "READY", initializable: false, message: "ready" }
    } as any,
    global: { stubs: { MarkdownView: markdownViewStub } }
  });

  await wrapper.setProps({ running: false, runtimeStatus: "FAILED" });
  expect(wrapper.find(".figma-chat-retry-card").exists()).toBe(true);
  expect(wrapper.text()).toContain("任务失败");

  await wrapper.setProps({
    currentSessionId: "session_healthy",
    messages: [{
      id: "u-session-healthy",
      messageId: "u-session-healthy",
      role: "user",
      text: "正常会话",
      createdAt: "2026-07-29T08:01:00.000Z"
    }],
    runtimeStatus: undefined
  });
  expect(wrapper.find(".figma-chat-retry-card").exists()).toBe(false);
  expect(wrapper.text()).not.toContain("任务失败");

  await wrapper.setProps({
    currentSessionId: "session_failed",
    messages: failedMessages,
    runtimeStatus: "FAILED"
  });
  expect(wrapper.find(".figma-chat-retry-card").exists()).toBe(true);
  expect(wrapper.text()).toContain("任务失败");
});
```

Mutation caught: removing the Session-change reset leaves `wasFailed=true`, so the healthy Session still renders the retry card and failed footer.

- [x] **Step 2: Run the regression test and verify RED**

Run from `frontend/`:

```bash
corepack pnpm test apps/agent-web/tests/FigmaChatPanel.test.ts -t "does not leak a failed terminal marker into another session"
```

Expected: FAIL on the healthy Session assertion because `.figma-chat-retry-card` still exists and `任务失败` is still rendered.

- [x] **Step 3: Implement the minimal Session-boundary reset**

Extend the existing `watch(() => props.currentSessionId, ...)` block in `frontend/apps/agent-web/src/components/FigmaChatPanel.vue`:

```ts
watch(
  () => props.currentSessionId,
  (sessionId, previousSessionId) => {
    // 空草稿首次落成真实 Session 仍属于同一 root；已有真实 Session 被替换或清空才重置阅读上下文与终态展示。
    if (previousSessionId && sessionId !== previousSessionId) {
      resetScrollScopesForRootChange()
      wasStopped.value = false
      wasCompleted.value = false
      wasFailed.value = false
    }
  }
)
```

Do not add a component `key`, a per-Session cache, or changes to `AgentWorkbench`.

- [x] **Step 4: Run the focused test and verify GREEN**

Run from `frontend/`:

```bash
corepack pnpm test apps/agent-web/tests/FigmaChatPanel.test.ts -t "does not leak a failed terminal marker into another session"
```

Expected: PASS; the healthy Session has no retry card or failed footer, and returning to the failed Session restores both from `runtimeStatus="FAILED"`.

- [x] **Step 5: Run the complete component test file**

Run from `frontend/`:

```bash
corepack pnpm test apps/agent-web/tests/FigmaChatPanel.test.ts
```

Expected: all `FigmaChatPanel` tests pass, including existing stopped/completed/failed and draft-to-real Session behavior.

- [x] **Step 6: Synchronize stable documentation**

Add the following behavior statement to the chat/runtime responsibility sections of the five stable documents, adapting only surrounding grammar:

```text
聊天面板的失败、停止和完成标记按当前 Session 隔离；切换到另一会话时清理组件本地终态，返回历史会话时只根据该会话恢复出的消息和 Run 状态重建，空草稿首次落成真实 Session 不触发清理。
```

Do not update `docs/api/http-api.md` or `docs/api/event-stream.md` because no wire contract changes.

- [x] **Step 7: Run frontend verification serially**

Run from `frontend/`, serially to avoid the documented VitePress `.temp` race:

```bash
corepack pnpm test
corepack pnpm typecheck
corepack pnpm build
```

Then run from the repository root:

```bash
git diff --check
```

Expected: Vitest, workspace typecheck, production build, and whitespace checks pass; only already-documented non-failing warnings may remain.

- [x] **Step 8: Record the durable session note**

Append one entry to `.agents/session-log.huangzhenren.md` with this content, updating only the numeric test totals to the actual command output:

```md
### 2026-07-29 - 隔离聊天会话终态展示

- Why:
  - `FigmaChatPanel` 在组件级保存失败终态，切换 Session 复用组件时会把失败卡片和页脚泄漏到正常会话。
- What:
  - 已建立 Session 身份变化时清理本地失败、停止和完成标记；空草稿首次生成真实 Session ID 保持同一 Run 语义，返回失败历史会话仍按其持久化状态恢复。
- How:
  - TDD 覆盖失败会话切到正常会话再切回的完整路径，并执行组件、前端全量测试、类型检查、生产构建和文档自检。
- Result:
  - 跨会话错误展示已隔离，全部前端校验通过；未修改 API、RunEvent、数据库、后端、安全、环境配置、generated SDK 或 OpenCode 源码。
```

- [x] **Step 9: Review and commit the implementation**

Review `git status`, the complete diff, all recent `.agents/session-log*.md` entries, and staged scope. Stage only the files listed in this task, then commit:

```bash
git commit -m "修复聊天失败状态跨会话泄漏"
```

Expected: one Chinese implementation commit after the earlier design commit, with no unrelated files staged.
