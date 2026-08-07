# Batch Schedule Action Clarity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将批量弹层调整为 `70vw × 90vh`，为首次定时选择增加关闭入口，并让立即执行与定时执行在底部主操作位互斥显示。

**Architecture:** 只修改 `BatchTestCaseGenerationDialog` 的首次提交前局部状态和模板条件，不改变进度页、容量冲突重试或编排数据流。现有 `scheduleOpen` 继续作为唯一模式开关，`selectedScheduleTimes` 与 `scheduleAllocation` 共同决定是否展示底部“定时执行”。

**Tech Stack:** Vue 3 Composition API、TypeScript、Vue Test Utils、Vitest、Playwright、pnpm workspace。

## Global Constraints

- 直接在 `codex/release-enterprise-20260801` 分支修改，不创建新分支或 worktree。
- 弹层宽度保持 `70vw`，高度严格改为 `90vh`。
- 定时选择打开时不展示“选择定时”或“立刻执行”。
- 只有至少选中一个时间且现有容量分配成功时，才在原主操作位置展示“定时执行”。
- 定时选择关闭后保留子条目选择和生成要求，清空定时时间与校验提示。
- 不修改 `ExecutionTimePicker`、进度页、批次幂等、HTTP API、RunEvent、数据库/Flyway、后端、环境配置、generated SDK 或 OpenCode 源码。
- 人工维护的复杂交互边界使用中文注释，只暂存本功能文件，提交信息使用中文，不自动 push。

---

### Task 1: Make scheduling a reversible, mutually exclusive action mode

**Files:**
- Modify: `frontend/apps/agent-web/src/components/BatchTestCaseGenerationDialog.vue`
- Test: `frontend/apps/agent-web/tests/BatchTestCaseGenerationDialog.test.ts`

**Interfaces:**
- Consumes: existing `scheduleOpen`, `selectedScheduleTimes`, `scheduleAllocation`, `executeImmediate()` and `executeScheduled()`.
- Produces: local `closeSchedule(): void`; stable test IDs `batch-close-schedule` and `batch-execute-scheduled`.

- [x] **Step 1: Change the existing size assertion and add a failing scheduling choreography test**

Update the first component test to assert:

```ts
expect(dialog.attributes("style")).toContain("width: 70vw");
expect(dialog.attributes("style")).toContain("height: 90vh");
```

Add this test using two references and one available slot with capacity 2:

```ts
it("uses one primary action slot and can close scheduling without retaining old times", async () => {
  const nightSlots = {
    timeZone: "Asia/Shanghai",
    windowStart: "2026-08-08T13:00:00Z",
    windowEnd: "2026-08-08T23:00:00Z",
    capacity: 2,
    slots: [{
      slotStart: "2026-08-08T13:00:00Z",
      slotEnd: "2026-08-08T13:15:00Z",
      reservedCount: 0,
      capacity: 2,
      available: true,
      recommended: false
    }]
  };
  const wrapper = mount(BatchTestCaseGenerationDialog, {
    props: { open: true, references: references.slice(0, 2), loading: false, nightSlots }
  });
  for (const checkbox of wrapper.findAll('input[data-testid="batch-item-checkbox"]')) {
    await checkbox.setValue(true);
  }

  await wrapper.get('[data-testid="batch-open-schedule"]').trigger("click");
  expect(wrapper.find('[data-testid="batch-open-schedule"]').exists()).toBe(false);
  expect(wrapper.find('[data-testid="batch-execute-now"]').exists()).toBe(false);
  expect(wrapper.find('[data-testid="batch-execute-scheduled"]').exists()).toBe(false);
  expect(wrapper.get('[data-testid="batch-close-schedule"]')).toBeTruthy();

  await wrapper.get('[data-testid="batch-night-slot"]').trigger("click");
  expect(wrapper.find('[data-testid="batch-execute-scheduled"]').exists()).toBe(true);

  await wrapper.get('[data-testid="batch-close-schedule"]').trigger("click");
  expect(wrapper.find('[data-testid="batch-schedule-panel"]').exists()).toBe(false);
  expect(wrapper.find('[data-testid="batch-execute-now"]').exists()).toBe(true);
  expect(wrapper.find('[data-testid="batch-open-schedule"]').exists()).toBe(true);

  await wrapper.get('[data-testid="batch-open-schedule"]').trigger("click");
  expect(wrapper.find('[data-testid="batch-execute-scheduled"]').exists()).toBe(false);
  expect(wrapper.get('[data-testid="batch-night-slot"]').classes()).not.toContain("is-selected");
});
```

- [x] **Step 2: Run the focused component test and verify RED**

Run:

```bash
cd frontend
corepack pnpm exec vitest run apps/agent-web/tests/BatchTestCaseGenerationDialog.test.ts
```

Expected: the size assertion reports `70vh`, the close control is missing, and immediate/scheduled actions remain in the old locations.

- [x] **Step 3: Add the schedule close boundary**

Add immediately after `openSchedule()`:

```ts
/** 返回立即执行模式时丢弃未提交的定时时间，避免再次打开误用旧选择。 */
function closeSchedule() {
  if (executionLocked.value) return;
  scheduleOpen.value = false;
  scheduleMode.value = "NIGHT_WINDOW";
  selectedNightTimes.value = [];
  customScheduleInput.value = "";
  customScheduleError.value = "";
  customTimes.value = [];
}
```

- [x] **Step 4: Move the scheduled submit action to the shared footer slot**

Change the dialog inline size to:

```vue
style="width: 70vw; height: 90vh"
```

Add `data-testid="batch-schedule-panel"` to the first-submit schedule section. Render this header:

```vue
<div class="batch-schedule-head">
  <strong>选择定时执行时间</strong>
  <button
    type="button"
    class="batch-schedule-close"
    aria-label="关闭定时选择"
    data-testid="batch-close-schedule"
    :disabled="executionLocked"
    @click="closeSchedule"
  ><X :size="14" /></button>
</div>
```

Remove `batch-execute-scheduled` from `.batch-schedule-foot`; keep only these status messages there:

```vue
<span v-if="selectedScheduleTimes.length === 0">请选择至少一个执行时间。</span>
<span v-else-if="!scheduleAllocation.ok" class="batch-error">所选时段总余量 {{ scheduleAllocation.remainingCapacity }}，不足以安排 {{ selectedIds.length }} 个子条目。</span>
<span v-else>将按时间升序轮询分配 {{ selectedScheduleTimes.length }} 个时间段。</span>
```

Replace the selection footer actions with this exact mutually exclusive structure:

```vue
<button
  v-if="!scheduleOpen"
  type="button"
  class="batch-secondary"
  data-testid="batch-open-schedule"
  :disabled="running || selectedIds.length === 0"
  @click="openSchedule"
><Clock3 :size="15" /> 选择定时</button>
<button
  v-if="!scheduleOpen"
  type="button"
  class="batch-primary"
  data-testid="batch-execute-now"
  :disabled="running || selectedIds.length === 0 || !requirement.trim()"
  @click="executeImmediate()"
><Play :size="15" /> 立刻执行</button>
<button
  v-else-if="selectedScheduleTimes.length > 0 && scheduleAllocation.ok"
  type="button"
  class="batch-primary"
  data-testid="batch-execute-scheduled"
  :disabled="running || selectedIds.length === 0 || !requirement.trim()"
  @click="executeScheduled()"
><Clock3 :size="15" /> 定时执行</button>
```

Add compact close-button styling without changing the progress-page close button:

```css
.batch-schedule-close {
  display: grid;
  width: 26px;
  height: 26px;
  place-items: center;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: #66778a;
  cursor: pointer;
}
.batch-schedule-close:hover:not(:disabled) { background: #dfe7ef; color: #8f2731; }
.batch-schedule-close:disabled { cursor: not-allowed; opacity: .45; }
```

- [x] **Step 5: Run focused tests and agent-web typecheck**

Run:

```bash
cd frontend
corepack pnpm exec vitest run apps/agent-web/tests/BatchTestCaseGenerationDialog.test.ts
corepack pnpm --filter @test-agent/agent-web typecheck
```

Expected: all component tests pass and `vue-tsc` exits 0.

- [x] **Step 6: Commit the independently testable component change**

Before committing, review all `.agents/session-log*.md`, then run:

```bash
git add frontend/apps/agent-web/src/components/BatchTestCaseGenerationDialog.vue \
  frontend/apps/agent-web/tests/BatchTestCaseGenerationDialog.test.ts
git diff --cached --check
git commit -m "优化批量定时选择与执行入口"
```

---

### Task 2: Update stable documentation and verify the browser flow

**Files:**
- Modify: `frontend/README.md`
- Modify: `frontend/apps/agent-web/README.md`
- Modify: `frontend/apps/agent-web/src/PACKAGE.md`
- Modify: `frontend/apps/user-manual/docs/guide/conversation.md`
- Modify: `frontend/apps/user-manual/docs/guide/feature-overview.md`
- Modify: `frontend/apps/agent-web/tests/workbench.spec.ts`
- Modify: `.agents/session-log.huangzhenren.md`

**Interfaces:**
- Consumes: `batch-close-schedule`, `batch-open-schedule`, `batch-execute-now`, `batch-execute-scheduled` and the `70vw × 90vh` dialog contract from Task 1.
- Produces: stable user instructions and browser regression evidence; no public interface change.

- [x] **Step 1: Extend the existing Playwright batch test**

In `batch test cases start isolated runs, retry failures, and create isolated scheduled tasks`, after opening the schedule picker and before selecting a slot, assert:

```ts
await scheduledDialog.getByTestId("batch-open-schedule").click();
await expect(scheduledDialog.getByTestId("batch-close-schedule")).toBeVisible();
await expect(scheduledDialog.getByTestId("batch-execute-now")).toHaveCount(0);
await expect(scheduledDialog.getByTestId("batch-execute-scheduled")).toHaveCount(0);
await scheduledDialog.getByTestId("batch-night-slot").click();
await expect(scheduledDialog.getByTestId("batch-execute-scheduled")).toBeVisible();
await scheduledDialog.getByTestId("batch-execute-scheduled").click();
```

Do not alter the existing request and `batchId` assertions.

- [x] **Step 2: Update stable documentation**

Replace every user-facing `70vw × 70vh` batch-dialog statement in the scoped front-end documents with `70vw × 90vh`. Document that opening scheduling hides immediate execution, selecting a valid time reveals scheduled execution in the same primary-action position, and the schedule close icon returns to immediate mode while discarding unsubmitted times.

- [x] **Step 3: Run focused unit and browser tests**

Run:

```bash
cd frontend
corepack pnpm exec vitest run apps/agent-web/tests/BatchTestCaseGenerationDialog.test.ts
corepack pnpm exec playwright test apps/agent-web/tests/workbench.spec.ts --project=chromium --grep "batch test cases start isolated"
```

If the pinned Playwright cache is unavailable, create `frontend/playwright.local-chrome.config.ts` with this exact temporary content, run the same test with `--config=playwright.local-chrome.config.ts`, then delete the file before staging:

```ts
import { defineConfig, devices } from "@playwright/test";

const chrome = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";

export default defineConfig({
  testDir: "./apps/agent-web/tests",
  testMatch: "**/*.spec.ts",
  workers: 1,
  retries: 1,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  webServer: {
    command: "corepack pnpm --filter @test-agent/agent-web dev --host 127.0.0.1 --port 3000",
    url: "http://127.0.0.1:3000",
    reuseExistingServer: true,
    timeout: 120_000
  },
  use: {
    baseURL: "http://127.0.0.1:3000",
    launchOptions: { executablePath: chrome },
    trace: "retain-on-failure"
  },
  projects: [{
    name: "chromium",
    use: {
      ...devices["Desktop Chrome"],
      launchOptions: { executablePath: chrome }
    }
  }]
});
```

- [x] **Step 4: Run the full front-end release checks sequentially**

Run from `frontend/`:

```bash
corepack pnpm test
corepack pnpm typecheck
corepack pnpm build
```

Expected: Vitest, all workspace typechecks and production build exit 0. Record the existing jsdom Canvas and chunk-size warnings separately if they remain non-failing.

- [x] **Step 5: Review scope and update the contributor session log**

Review all `.agents/session-log*.md`, run `git diff --check`, and verify no backend, API, RunEvent, migration, `.env*`, generated SDK or OpenCode source file changed. Add one concise `Why / What / How / Result` entry to `.agents/session-log.huangzhenren.md` with the verified test counts and compatibility scope.

- [x] **Step 6: Create the final documentation and verification commit**

```bash
git add .agents/session-log.huangzhenren.md \
  docs/superpowers/plans/2026-08-08-batch-schedule-action-clarity.md \
  frontend/README.md \
  frontend/apps/agent-web/README.md \
  frontend/apps/agent-web/src/PACKAGE.md \
  frontend/apps/agent-web/tests/workbench.spec.ts \
  frontend/apps/user-manual/docs/guide/conversation.md \
  frontend/apps/user-manual/docs/guide/feature-overview.md
git diff --cached --check
git commit -m "完善批量定时选择说明与回归验证"
```

Do not push.
