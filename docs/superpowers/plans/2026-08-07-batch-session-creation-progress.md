# Batch Session Creation Progress Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将批量子条目弹层改为“选择配置 → 会话创建进度”两阶段流程，阻止同批次重复发起，并支持失败项单笔或批量幂等重试及主动关闭确认。

**Architecture:** `BatchTestCaseGenerationDialog` 在本地同步切换交互阶段，避免等待父组件 `running` 回写形成重复点击窗口；`useBatchTestCaseGeneration` 保持批次幂等身份并提供显式重置。`FigmaChatPanel` 负责未创建会话的关闭确认，`AgentWorkbench` 在确认关闭后重置编排状态；HTTP API、RunEvent 和数据库保持不变。

**Tech Stack:** Vue 3 Composition API、TypeScript、Vitest、Vue Test Utils、Element Plus、Playwright、pnpm workspace。

## Global Constraints

- 不创建新分支或 worktree，直接在用户指定的 `codex/release-enterprise-20260801` 上修改。
- 不修改 OpenCode 源码、generated SDK、HTTP API、RunEvent、数据库、Flyway 或环境配置。
- 点击立即或定时提交时必须同步进入进度状态，同一弹层生命周期只允许首次提交创建新批次身份。
- 失败重试必须复用原 `batchId`、`itemRequestId`、Run/任务 `clientRequestId` 和已创建 Session。
- 创建或重试运行中禁止关闭；空闲时若存在未取得 `sessionId` 的条目，关闭必须二次确认。
- 关闭后清空弹层和编排身份；再次打开恢复默认要求并创建全新批次。
- 人工维护的复杂状态和边界逻辑补充中文注释。
- 只暂存本功能文件，提交信息使用中文，不自动 push。

---

### Task 1: Preserve session identity and reset a completed batch

**Files:**
- Modify: `frontend/apps/agent-web/src/components/useBatchTestCaseGeneration.ts`
- Test: `frontend/apps/agent-web/tests/useBatchTestCaseGeneration.test.ts`

**Interfaces:**
- Consumes: existing `execute(request: BatchGenerationRequest): Promise<BatchExecutionSummary>` and stable `BatchIdentity`.
- Produces: `reset(): void` on the composable return value; failed immediate states retain `sessionId` after Session creation.

- [ ] **Step 1: Extract exact test helpers, then write a failing test for preserving an already-created Session**

Add these helpers immediately below the existing `references` fixture so later test cases do not depend on undefined shorthand:

```ts
function batchApi(overrides: Record<string, unknown> = {}) {
  return {
    readFile: vi.fn(async () => ({ content: "需求正文" })),
    createBatchItemSession: vi.fn(async () => ({ sessionId: "ses_default" })),
    startRun: vi.fn(async () => ({ runId: "run_default" })),
    createNightExecutionTask: vi.fn(),
    ...overrides
  } as any;
}

function createGeneration(api: ReturnType<typeof batchApi>) {
  return useBatchTestCaseGeneration({
    api,
    conversationContexts: {
      get: vi.fn(async () => ({ contextToken: "ctx", contextVersion: 1, expiresAt: "2026-08-07T13:00:00Z" })),
      invalidate: vi.fn(),
      clear: vi.fn()
    },
    references: () => references,
    workspaceId: () => "wrk_batch",
    agent: () => "build",
    model: () => "provider/model",
    mode: () => "build",
    nightSlots: () => null
  });
}

function immediateRequest(referenceIds: string[]) {
  return {
    referenceIds,
    requirement: "请生成子条目测试案例。",
    executionMode: "immediate" as const
  };
}
```

Refactor the existing tests to use these helpers where doing so is mechanical, then add:

```ts
it("keeps the created session and reuses all request ids when run start retry is needed", async () => {
  const api = batchApi({
    createBatchItemSession: vi.fn(async () => ({ sessionId: "ses_login" })),
    startRun: vi.fn()
      .mockRejectedValueOnce(new Error("run start failed"))
      .mockResolvedValueOnce({ runId: "run_login" })
  });
  const generation = createGeneration(api);
  const request = immediateRequest([references[0]!.id]);

  await generation.execute(request);
  expect(generation.itemStates.value[references[0]!.id]).toEqual(expect.objectContaining({
    status: "failed",
    sessionId: "ses_login"
  }));

  await generation.execute({ ...request, retry: true });

  expect(api.createBatchItemSession.mock.calls[1]![2])
    .toEqual(api.createBatchItemSession.mock.calls[0]![2]);
  expect(api.startRun.mock.calls[1]![0].clientRequestId)
    .toBe(api.startRun.mock.calls[0]![0].clientRequestId);
  expect(generation.itemStates.value[references[0]!.id]).toEqual(expect.objectContaining({
    status: "succeeded",
    sessionId: "ses_login",
    runId: "run_login"
  }));
});
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
cd frontend
corepack pnpm exec vitest run apps/agent-web/tests/useBatchTestCaseGeneration.test.ts
```

Expected: FAIL because the first failed state does not retain `sessionId`.

- [ ] **Step 3: Implement Session preservation with a local item boundary**

Inside each concurrency worker, retain the created Session before starting the Run:

```ts
let createdSessionId: string | undefined;
try {
  // existing context preparation
  const created = await options.api.createBatchItemSession(workspaceId, title, batchContext);
  createdSessionId = created.sessionId;
  update(reference.id, { status: "starting-run", sessionId: createdSessionId });
  // existing stable Run start
} catch (error) {
  const failure = safeFailure(error);
  update(reference.id, {
    status: "failed",
    ...(createdSessionId ? { sessionId: createdSessionId } : {}),
    ...failure
  });
}
```

Do not change `itemRequestId` or `run_${itemRequestId}` generation.

- [ ] **Step 4: Write a failing test for ending one batch and starting a new identity**

```ts
it("clears item state and creates a fresh batch identity after reset", async () => {
  const api = batchApi();
  const generation = createGeneration(api);
  const request = immediateRequest([references[0]!.id]);

  await generation.execute(request);
  const firstContext = api.createBatchItemSession.mock.calls[0]![2];
  generation.reset();

  expect(generation.itemStates.value).toEqual({});
  await generation.execute(request);

  const secondContext = api.createBatchItemSession.mock.calls[1]![2];
  expect(secondContext.batchId).not.toBe(firstContext.batchId);
  expect(secondContext.itemRequestId).not.toBe(firstContext.itemRequestId);
});
```

- [ ] **Step 5: Run the focused test and verify RED**

Expected: TypeScript/test failure because `reset` is not returned by the composable.

- [ ] **Step 6: Add the guarded reset API**

Extend the existing inferred return object with `reset`; do not replace the real function parameter list with a shorthand signature. The resulting returned members are exactly:

```ts
return { running, itemStates, execute, reset };
```

Implementation:

```ts
/** 用户关闭进度页后结束本批次；运行中拒绝清理，避免在途请求失去幂等身份。 */
function reset() {
  if (running.value) return;
  identity = null;
  itemStates.value = {};
}
```

- [ ] **Step 7: Run the focused composable tests and verify GREEN**

Run the focused Vitest command again. Expected: all tests in `useBatchTestCaseGeneration.test.ts` pass.

- [ ] **Step 8: Commit the independently testable composable change**

```bash
git add frontend/apps/agent-web/src/components/useBatchTestCaseGeneration.ts \
  frontend/apps/agent-web/tests/useBatchTestCaseGeneration.test.ts
git commit -m "修复批量会话重试与批次身份重置"
```

---

### Task 2: Convert the dialog into selection and creation-progress states

**Files:**
- Modify: `frontend/apps/agent-web/src/components/BatchTestCaseGenerationDialog.vue`
- Test: `frontend/apps/agent-web/tests/BatchTestCaseGenerationDialog.test.ts`

**Interfaces:**
- Consumes: `BatchGenerationRequest`, `BatchItemExecutionState`, `props.running`, `props.itemStates`.
- Produces: `close` event payload `{ incompleteCount: number }`; retry requests use `{ retry: true, referenceIds: [...] }` and the original execution configuration.

- [ ] **Step 1: Write a failing test for synchronous submit locking**

Mount two selected references, click the immediate button twice without changing props, and assert:

```ts
await selectFirstTwo(wrapper);
const execute = wrapper.get('[data-testid="batch-execute-now"]');
await execute.trigger("click");
await execute.trigger("click");

expect(wrapper.emitted("execute")).toHaveLength(1);
expect(wrapper.get('[data-testid="batch-creation-progress"]')).toBeTruthy();
expect(wrapper.find('[data-testid="batch-requirement-input"]').exists()).toBe(false);
expect(wrapper.get('[data-testid="batch-dialog-close"]').attributes("disabled")).toBeDefined();
```

The progress close button remains disabled while `running=true`; set `running=true` in the same test after the first click.

- [ ] **Step 2: Run the dialog tests and verify RED**

Run:

```bash
cd frontend
corepack pnpm exec vitest run apps/agent-web/tests/BatchTestCaseGenerationDialog.test.ts
```

Expected: FAIL because the selection form remains visible and two execute events are emitted before the parent prop updates.

- [ ] **Step 3: Add an explicit local dialog state**

Use these exact state members:

```ts
type BatchDialogStage = "selection" | "progress";
const dialogStage = ref<BatchDialogStage>("selection");
const activeRequest = ref<BatchGenerationRequest | null>(null);
const activeReferenceIds = ref<string[]>([]);

const activeReferences = computed(() => {
  const active = new Set(activeReferenceIds.value);
  return props.references.filter((reference) => active.has(reference.id));
});
```

All first submissions pass through one synchronous function:

```ts
/** 先切换到进度页再通知父层，关闭同一渲染帧内的重复点击窗口。 */
function beginExecution(request: BatchGenerationRequest) {
  if (dialogStage.value !== "selection" || props.running) return;
  activeRequest.value = request;
  activeReferenceIds.value = [...request.referenceIds];
  lastRequest.value = request;
  dialogStage.value = "progress";
  emit("execute", request);
}
```

`executeImmediate` and `executeScheduled` only build requests and call `beginExecution`.

- [ ] **Step 4: Render a dedicated progress page**

When `dialogStage === "progress"`:

- render only `activeReferences`;
- display selected count, created Session count, failed count and current running label;
- keep the status icons and file counts;
- show `sessionId` when present;
- show `会话已创建，执行启动失败` for a failed state with `sessionId`;
- hide search, checkbox, requirement and first-submit controls;
- add `data-testid="batch-creation-progress"`.

Do not use CSS-only hiding for form controls; remove them with `v-if` so keyboard focus cannot reach them.

- [ ] **Step 5: Write failing tests for single and bulk retry**

After entering progress, set props so one row succeeds and one fails:

```ts
await wrapper.setProps({
  running: false,
  itemStates: {
    [references[0]!.id]: { status: "succeeded", sessionId: "ses_1", runId: "run_1" },
    [references[1]!.id]: { status: "failed", errorCode: "FILE_READ_FAILED", message: "读取失败" }
  }
});

await wrapper.get(`[data-testid="batch-retry-item-${references[1]!.id}"]`).trigger("click");
expect(wrapper.emitted("execute")?.at(-1)?.[0]).toEqual(expect.objectContaining({
  retry: true,
  referenceIds: [references[1]!.id]
}));

await wrapper.get('[data-testid="batch-retry-all"]').trigger("click");
expect(wrapper.emitted("execute")?.at(-1)?.[0].referenceIds).toEqual([references[1]!.id]);
```

Use a stable encoded test id helper if raw reference IDs contain `/`; the rendered and test helper must call the same `batchReferenceTestId(reference.id)` function from `batch-test-case-generation.ts`.

Add the exact shared helper:

```ts
/** 将工作区路径型候选 ID 转为可稳定查询的 data-testid 片段。 */
export function batchReferenceTestId(id: string): string {
  return encodeURIComponent(id);
}
```

- [ ] **Step 6: Implement retry requests without generating a new local request**

```ts
function retryReferences(referenceIds: string[]) {
  const request = activeRequest.value;
  if (!request || props.running || referenceIds.length === 0) return;
  emit("execute", {
    ...request,
    referenceIds: [...referenceIds],
    retry: true,
    ...(request.executionMode === "scheduled" ? {
      slotStarts: [...selectedScheduleTimes.value].sort()
    } : {})
  });
}
```

Single-row retry calls `retryReferences([reference.id])`; bulk retry calls `retryReferences(failedIds.value)`.

- [ ] **Step 7: Add capacity-conflict time reselection tests and implementation**

Test a scheduled active request whose failed row has `errorCode="SLOT_CAPACITY_CONFLICT"`:

- progress renders `ExecutionTimePicker` with `data-testid="batch-retry-schedule"`;
- refreshed slots are selectable;
- retry remains disabled until `allocateBatchSchedule` can cover only the capacity-conflict failed rows;
- ordinary failed rows retain their original assigned time.

Add:

```ts
const capacityConflictIds = computed(() => failedIds.value.filter(
  (id) => props.itemStates[id]?.errorCode === "SLOT_CAPACITY_CONFLICT"
));
```

The progress time picker is rendered only for a scheduled request with `capacityConflictIds.length > 0`. Its allocation count is `capacityConflictIds.length`, not all failed rows.

- [ ] **Step 8: Add close-result tests**

Verify all three boundaries:

```ts
await wrapper.setProps({ running: true });
await wrapper.get('[data-testid="batch-dialog-close"]').trigger("click");
expect(wrapper.emitted("close")).toBeUndefined();

await wrapper.setProps({ running: false, itemStates: successStates });
await wrapper.get('[data-testid="batch-dialog-close"]').trigger("click");
expect(wrapper.emitted("close")?.at(-1)?.[0]).toEqual({ incompleteCount: 0 });

await wrapper.setProps({ itemStates: oneMissingSessionState });
await wrapper.get('[data-testid="batch-dialog-close"]').trigger("click");
expect(wrapper.emitted("close")?.at(-1)?.[0]).toEqual({ incompleteCount: 1 });
```

- [ ] **Step 9: Reset all local form state when the dialog closes**

Add a single `resetDialogState()` that restores:

```ts
search.value = "";
selectedIds.value = [];
requirement.value = DEFAULT_REQUIREMENT;
scheduleOpen.value = false;
scheduleMode.value = "NIGHT_WINDOW";
selectedNightTimes.value = [];
customScheduleInput.value = "";
customScheduleError.value = "";
customTimes.value = [];
lastRequest.value = null;
activeRequest.value = null;
activeReferenceIds.value = [];
dialogStage.value = "selection";
```

Call it from the `props.open` watcher when `open` changes to `false`. Add a test that closes, reopens, and observes zero selections and the exact default requirement.

- [ ] **Step 10: Run dialog tests and commit**

Expected: all `BatchTestCaseGenerationDialog.test.ts` tests pass.

```bash
git add frontend/apps/agent-web/src/components/BatchTestCaseGenerationDialog.vue \
  frontend/apps/agent-web/src/components/batch-test-case-generation.ts \
  frontend/apps/agent-web/tests/BatchTestCaseGenerationDialog.test.ts
git commit -m "实现批量会话创建进度与失败重试"
```

---

### Task 3: Wire confirmed close, reset orchestration, and update browser flow

**Files:**
- Modify: `frontend/apps/agent-web/src/components/FigmaChatPanel.vue`
- Modify: `frontend/apps/agent-web/src/components/AgentWorkbench.vue`
- Test: `frontend/apps/agent-web/tests/FigmaChatPanel.test.ts`
- Test: `frontend/apps/agent-web/tests/workbench.spec.ts`

**Interfaces:**
- Consumes: dialog `close` payload `{ incompleteCount: number }` and composable `reset()`.
- Produces: `reset-batch-test-cases` component event; closing an accepted dialog always ends the current front-end batch.

- [ ] **Step 1: Add an explicit submission-rejection control before close-confirmation tests**

Define this shared type in `batch-test-case-generation.ts`:

```ts
export type BatchExecutionControls = {
  reject: () => void;
};
```

`BatchTestCaseGenerationDialog` emits `execute(request, controls)`. `beginExecution` creates a per-submit `reject` closure that restores the selection stage only when that same first submission is still active and no execution is running. `FigmaChatPanel` forwards both arguments. `AgentWorkbench.handleBatchTestCaseGeneration(request, controls)` calls `controls.reject()` on every top-level guard return and when the orchestration call itself rejects. Per-item failures returned in a normal `BatchExecutionSummary` remain on the progress page.

Add a dialog/integration unit test that simulates `controls.reject()` and verifies the original selections, requirement and scheduling choices remain available in the selection stage.

- [ ] **Step 2: Write failing FigmaChatPanel tests for close confirmation**

Mock `ElMessageBox.confirm` and cover:

```ts
expect(confirm).not.toHaveBeenCalled(); // incompleteCount=0
expect(wrapper.emitted("reset-batch-test-cases")).toHaveLength(1);
expect(wrapper.find('[data-testid="batch-test-case-dialog"]').exists()).toBe(false);
```

For `incompleteCount=2`, assert the message contains `仍有 2 个子条目未创建会话` and cancellation keeps the dialog open without emitting reset. Confirming closes and emits reset exactly once.

- [ ] **Step 3: Run FigmaChatPanel tests and verify RED**

Run:

```bash
cd frontend
corepack pnpm exec vitest run apps/agent-web/tests/FigmaChatPanel.test.ts
```

Expected: FAIL because the close payload and reset event do not exist.

- [ ] **Step 4: Implement confirmed close in FigmaChatPanel**

Import `ElMessageBox` and add this component event:

```ts
(e: "reset-batch-test-cases"): void
```

Use one closing function:

```ts
async function closeBatchTestCaseDialog(result: { incompleteCount: number }) {
  if (props.batchRunning) return;
  if (result.incompleteCount > 0) {
    try {
      await ElMessageBox.confirm(
        `仍有 ${result.incompleteCount} 个子条目未创建会话，关闭后本次批量创建将结束。是否关闭？`,
        "确认关闭批量创建",
        { confirmButtonText: "仍然关闭", cancelButtonText: "继续处理", type: "warning" }
      );
    } catch {
      return;
    }
  }
  batchDialogOpen.value = false;
  emit("reset-batch-test-cases");
}
```

The dialog `@close` passes its payload to this function. Do not auto-close from a `running` watcher.

- [ ] **Step 5: Wire orchestration reset in AgentWorkbench**

Add:

```ts
/** 用户主动结束进度页后清理前端批次身份；已创建的服务端对象不受影响。 */
function resetBatchTestCaseGeneration() {
  batchTestCaseGeneration.reset();
}
```

Bind `@reset-batch-test-cases="resetBatchTestCaseGeneration"` on `FigmaChatPanel`.

- [ ] **Step 6: Update the Playwright batch flow before implementation assertions pass**

Revise `batch test cases start isolated runs, retry failures, and create isolated scheduled tasks`:

1. after immediate submit, assert selection controls disappear and `batch-creation-progress` appears;
2. assert only the failed row has a retry button and use the single-row retry;
3. after both rows have `sessionId`, close without confirmation;
4. reopen the batch entry and assert the default requirement and no selection;
5. create the scheduled batch from the new selection state;
6. assert the scheduled progress page remains open after task creation;
7. close it manually;
8. assert immediate and scheduled batches use different `batchId` values, while retries inside the immediate batch reuse its original IDs.

- [ ] **Step 7: Run focused unit and browser tests**

Run:

```bash
cd frontend
corepack pnpm exec vitest run \
  apps/agent-web/tests/BatchTestCaseGenerationDialog.test.ts \
  apps/agent-web/tests/FigmaChatPanel.test.ts \
  apps/agent-web/tests/useBatchTestCaseGeneration.test.ts
corepack pnpm exec playwright test apps/agent-web/tests/workbench.spec.ts \
  --project=chromium \
  --grep "batch test cases start isolated"
```

Expected: all focused tests pass; the browser test reports one passed test.

- [ ] **Step 8: Commit the page integration**

```bash
git add frontend/apps/agent-web/src/components/FigmaChatPanel.vue \
  frontend/apps/agent-web/src/components/AgentWorkbench.vue \
  frontend/apps/agent-web/tests/FigmaChatPanel.test.ts \
  frontend/apps/agent-web/tests/workbench.spec.ts
git commit -m "接入批量创建关闭确认与全新批次"
```

---

### Task 4: Synchronize user documentation and run release verification

**Files:**
- Modify: `frontend/README.md`
- Modify: `frontend/apps/agent-web/README.md`
- Modify: `frontend/apps/agent-web/src/PACKAGE.md`
- Modify: `frontend/apps/user-manual/README.md`
- Modify: `frontend/apps/user-manual/docs/guide/conversation.md`
- Modify: `frontend/apps/user-manual/docs/guide/feature-overview.md`
- Modify when new handoff information exists: `.agents/session-log.huangzhenren.md`

**Interfaces:**
- Consumes: completed two-stage UI behavior and verified tests from Tasks 1-3.
- Produces: stable documentation and final Chinese Git commit; no API/event/database documentation change.

- [ ] **Step 1: Update stable documentation**

Document these exact user-visible rules:

- first click switches to a non-editable creation-progress page;
- progress is not auto-closed;
- failed rows support single and bulk retry;
- closing with missing Sessions requires confirmation;
- confirmed close ends the batch, and reopening starts with empty selection and default requirement;
- retry reuses the current batch identity and already-created Session.

Do not describe HTTP, RunEvent or database changes because none are made.

- [ ] **Step 2: Run the full front-end verification sequentially**

Run from `frontend/` in this exact order to avoid VitePress temporary-directory races:

```bash
corepack pnpm test
corepack pnpm typecheck
corepack pnpm build
```

Expected: Vitest exits 0, all workspace typechecks exit 0, and the production build exits 0. Record any existing non-failing chunk-size or jsdom canvas warnings separately.

- [ ] **Step 3: Run focused Playwright on the correct worktree server**

Before testing, verify the listener working directory:

```bash
lsof -nP -iTCP:3000 -sTCP:LISTEN
listener_pid=$(lsof -tiTCP:3000 -sTCP:LISTEN | head -n 1)
test -n "$listener_pid"
lsof -a -p "$listener_pid" -d cwd
```

Only reuse port 3000 when its `cwd` belongs to this worktree. Otherwise start this worktree on an unused port with a temporary Playwright config, run the focused batch test, and remove only that temporary config after the run.

- [ ] **Step 4: Review diffs, session logs, and scope**

Run:

```bash
git diff --check
git status --short
find .agents -maxdepth 1 -type f -name 'session-log*.md' -print | sort
```

Review recent entries in every session log. Confirm no `.env*`, generated SDK, OpenCode source, backend, migration, API or RunEvent file is staged.

- [ ] **Step 5: Update the current contributor session log when warranted**

Add one `Why / What / How / Result` entry to `.agents/session-log.huangzhenren.md` summarizing the duplicate-submit root cause, two-stage progress page, stable retry identity, focused/full test evidence, and compatibility scope.

- [ ] **Step 6: Stage only this feature and create the final Chinese commit**

```bash
git add .agents/session-log.huangzhenren.md \
  frontend/README.md \
  frontend/apps/agent-web/README.md \
  frontend/apps/agent-web/src/PACKAGE.md \
  frontend/apps/agent-web/src/components/AgentWorkbench.vue \
  frontend/apps/agent-web/src/components/BatchTestCaseGenerationDialog.vue \
  frontend/apps/agent-web/src/components/FigmaChatPanel.vue \
  frontend/apps/agent-web/src/components/batch-test-case-generation.ts \
  frontend/apps/agent-web/src/components/useBatchTestCaseGeneration.ts \
  frontend/apps/agent-web/tests/BatchTestCaseGenerationDialog.test.ts \
  frontend/apps/agent-web/tests/FigmaChatPanel.test.ts \
  frontend/apps/agent-web/tests/useBatchTestCaseGeneration.test.ts \
  frontend/apps/agent-web/tests/workbench.spec.ts \
  frontend/apps/user-manual/README.md \
  frontend/apps/user-manual/docs/guide/conversation.md \
  frontend/apps/user-manual/docs/guide/feature-overview.md \
  docs/superpowers/plans/2026-08-07-batch-session-creation-progress.md
git diff --cached --check
git commit -m "修复批量会话重复提交并增加创建进度"
```

Do not push.
