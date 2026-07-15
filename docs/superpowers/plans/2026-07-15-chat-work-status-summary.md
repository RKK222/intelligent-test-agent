# 合并对话工作状态展示实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将主智能体当前轮状态、Todo 和文件修改固定到输入框上方，成功完成后把状态收为与反馈按钮并列的图标，并让事件行按真实事件按需出现。

**Architecture:** `agent-chat` 投影层继续生成根会话专用 `work-status` 行，runtime reducer 维护按 user message ID 的 Todo 快照；`OpencodeTimeline` 把最新状态和 Diff 投送到输入框上方 Dock，并用通用 actions 插槽承载完成摘要旁的业务操作。`agent-web` 只通过该插槽迁移既有反馈按钮，不把反馈 DTO 或提交逻辑下沉到通用包；子智能体投影、问答卡片和网络契约保持不变。

**Tech Stack:** Vue 3、TypeScript 6、Vitest、Testing Library Vue、Tailwind CSS 4、lucide-vue-next、pnpm workspace。

## Global Constraints

- 只修改本任务相关前端代码、测试和稳定文档。
- `task`、`question` 和子智能体视图保持原展示方式。
- 不修改 API、RunEvent、数据库、后端或环境配置。
- 人工维护的复杂逻辑补充中文注释。
- 先写失败测试并确认 RED，再实现最小代码并确认 GREEN。

---

### Task 1: 扩展 ShimmerDivider

- [x] 为纵向布局、静态模式和默认横向兼容补充失败测试。
- [x] 运行 `packages/ui-kit/tests/ShimmerDivider.test.ts` 确认 RED。
- [x] 增加 `orientation?: "horizontal" | "vertical"` 与 `animated?: boolean`，切换渐变、mask 和动画方向。
- [x] 重跑组件测试和 `@test-agent/ui-kit` typecheck 确认 GREEN。

### Task 2: 聚合工作状态投影

- [x] 为无 assistant part、事件分类/计数、末尾排序、多轮保留和 task/question/子智能体不变补充失败测试。
- [x] 运行 `packages/agent-chat/tests/opencode-like-state.test.ts` 确认 RED。
- [x] 新增 `work-status` 类型与事件分类 helper，在根时间线聚合过程 part，并把最新状态块延后到 runtime 行之后。
- [x] 重跑状态测试确认 GREEN。

### Task 3: 实现两行状态块与全宽气泡

- [x] 为两行边框、动态图标、计数、互斥气泡、外部点击/Esc、新轮收起和流光状态补充失败测试。
- [x] 运行 `packages/agent-chat/tests/opencode-timeline.test.ts` 确认 RED。
- [x] 新增工作状态组件与样式，复用 reasoning 和工具详情组件；在时间线根控制全局唯一打开气泡。
- [x] 重跑时间线测试、`FigmaChatPanel` 回归测试和 `@test-agent/agent-chat` typecheck 确认 GREEN。

### Task 4: 文档、全量验证与提交

- [x] 同步 `agent-chat`、`agent-web`、`ui-kit` README/PACKAGE 和 `.agents/session-log.md`。
- [x] 运行 `corepack pnpm typecheck`、`corepack pnpm test`、`corepack pnpm build` 与 `git diff --check`。
- [x] 在桌面与窄屏真实页面验证流式排序、单行图标、气泡定位和历史收起。
- [x] 回顾 `.agents/session-log.md`，检查暂存范围并使用中文 commit 提交。

### Task 5: 合并 Todo 并移动当前状态与文件修改

- [x] 为分轮 Todo 归档、历史恢复、状态在 Diff 前补充失败测试并确认 RED。
- [x] 扩展 runtime/state 输入和 `TimelineRow.work-status`，按 user message ID 保存 Todo 快照。
- [x] 为两行/三行状态、Dock 投送和文件修改顺序补充失败测试并确认 RED。
- [x] 在 `AssistantThread` 与 `FigmaChatPanel` 原 Todo 位置建立 Dock，把 Todo 作为状态第三行，文件修改紧随状态块。

### Task 6: 历史状态单图标与回归

- [x] 为历史单图标、原位展开、单项互斥和新轮自动收起补充失败测试并确认 RED。
- [x] 在时间线根统一管理历史展开与事件气泡，保留 question、task 和子智能体视图原路径。
- [x] 更新历史恢复适配和稳定文档，执行定向测试、全量 typecheck/test/build 与真实页面检查。

### Task 7: 状态行按需渲染与成功完成态摘要

**Files:**
- Modify: `frontend/packages/agent-chat/tests/opencode-timeline.test.ts`
- Modify: `frontend/packages/agent-chat/src/opencode-like/components/rows/WorkStatusRow.vue`
- Modify: `frontend/packages/agent-chat/src/opencode-like/components/TimelineRow.vue`
- Modify: `frontend/packages/agent-chat/src/opencode-like/components/OpencodeTimeline.vue`
- Modify: `frontend/packages/agent-chat/src/opencode-like/styles/work-status.css`

**Interfaces:**
- Consumes: `TimelineRow.work-status.status: "running" | "retry" | "failed" | "stopped" | "completed"`、现有 Dock 行顺序和历史状态展开状态。
- Produces: `TimelineRowProps.completedWorkStatusExpanded?: boolean`、`toggleCompletedWorkStatus` emit，以及 `OpencodeTimeline` 的 `completed-status-actions` 具名插槽；插槽只在最新 `completed` 摘要行中渲染，不定义反馈业务类型。

- [ ] **Step 1: 为单行初始态和按需事件行写失败测试**

```ts
it("renders only the reasoning line before any work event", () => {
  const state = createOpencodeLikeState({
    messages: [userMessage("msg_user_1", "开始分析")],
    running: true
  });
  const { container } = render(OpencodeTimeline, { props: { state } });
  expect(container.querySelectorAll(".oc-work-status__line")).toHaveLength(1);
  expect(container.querySelector(".oc-work-status__event-line")).toBeNull();
});

it("adds the event line after the first tool event", () => {
  const state = createOpencodeLikeState({
    messages: [
      userMessage("msg_user_1", "开始分析"),
      assistantMessage("msg_assistant_1", [toolPart("part_read", "read", { filePath: "README.md" })])
    ],
    running: true
  });
  const { container } = render(OpencodeTimeline, { props: { state } });
  expect(container.querySelectorAll(".oc-work-status__line")).toHaveLength(2);
  expect(container.querySelector("[data-testid='oc-work-status-event-explore']")).toBeTruthy();
});
```

- [ ] **Step 2: 运行测试并确认 RED**

Run: `cd frontend && corepack pnpm vitest run packages/agent-chat/tests/opencode-timeline.test.ts -t "reasoning line|event line"`

Expected: 初始态仍得到 2 行，`oc-work-status__event-line` 仍存在，因此断言失败。

- [ ] **Step 3: 最小化实现事件行条件渲染并确认 GREEN**

```vue
<div
  v-if="row.events.length > 0"
  class="oc-work-status__line oc-work-status__event-line"
  aria-label="工作事件"
>
  <div class="oc-work-status__event-strip">
    <button
      v-for="event in row.events"
      :key="event.key"
      type="button"
      class="oc-work-status__event-button"
      :class="{ 'is-active': openEventKey === event.key }"
      :data-testid="`oc-work-status-event-${event.key}`"
      :title="eventAriaLabel(event)"
      :aria-label="eventAriaLabel(event)"
      :aria-expanded="openEventKey === event.key"
      :aria-controls="openEventKey === event.key ? popoverId : undefined"
      @click="emit('toggleEvent', event.key)"
    >
      <component :is="iconFor(event)" class="oc-work-status__event-icon" aria-hidden="true" />
      <span v-if="event.refs.length > 1" class="oc-work-status__event-count">{{ event.refs.length }}</span>
    </button>
  </div>
</div>
```

同步把无事件但有 Todo 的断言改为“思考行 + Todo 行”两行；有事件和 Todo 时仍为三行。重跑上述测试，Expected: PASS。

- [ ] **Step 4: 为成功完成态摘要、actions 插槽和展开顺序写失败测试**

```ts
it("collapses the latest completed status beside generic actions and expands before diff", async () => {
  const dock = document.createElement("div");
  document.body.appendChild(dock);
  const state = createOpencodeLikeState({
    messages: [userMessage("msg_user_1", "完成任务")],
    runtimeStatus: { type: "completed" },
    diffFiles: [{ path: "src/main.ts", patch: "", additions: 1, deletions: 0, status: "modified" }]
  });
  const { getByRole } = render(OpencodeTimeline, {
    props: { state, workStatusDockTarget: dock },
    slots: { "completed-status-actions": "<button>满意</button>" }
  });
  expect(dock.querySelector(".oc-work-status")).toBeNull();
  expect(dock.querySelector(".oc-work-status-completed-summary")?.textContent).toContain("满意");
  await fireEvent.click(getByRole("button", { name: "展开已完成工作状态" }));
  expect(dock.querySelector(".oc-work-status")).toBeTruthy();
  expect(dock.querySelector(".oc-work-status-completed")?.nextElementSibling?.classList.contains("oc-diff-summary")).toBe(true);
});
```

- [ ] **Step 5: 运行完成态测试并确认 RED**

Run: `cd frontend && corepack pnpm vitest run packages/agent-chat/tests/opencode-timeline.test.ts -t "completed status"`

Expected: 最新完成状态仍完整展示，且 `completed-status-actions` 插槽尚不存在。

- [ ] **Step 6: 实现通用完成摘要和可恢复详情**

```ts
const expandedCompletedStatusKey = ref<string | null>(null);

function toggleCompletedStatus(rowKey: string): void {
  expandedCompletedStatusKey.value = expandedCompletedStatusKey.value === rowKey ? null : rowKey;
  openWorkStatusDetail.value = null;
}

watch(latestUserMessageKey, () => {
  expandedCompletedStatusKey.value = null;
});
```

```vue
<div v-if="row.type === 'work-status' && row.isLatest && row.status === 'completed'" class="oc-row oc-work-status-completed">
  <div class="oc-work-status-completed-summary">
    <OcIconButton
      :label="completedWorkStatusExpanded ? '收起已完成工作状态' : '展开已完成工作状态'"
      :aria-expanded="completedWorkStatusExpanded"
      @click="emit('toggleCompletedWorkStatus')"
    >
      <Activity aria-hidden="true" />
    </OcIconButton>
    <slot name="completed-status-actions" />
  </div>
  <WorkStatusRow v-if="completedWorkStatusExpanded" :row="row" :state="state" />
</div>
```

```ts
export type TimelineRowProps = {
  row: TimelineRow;
  state: OpencodeLikeConversationState;
  openWorkStatusEventKey?: string;
  historicalWorkStatusExpanded?: boolean;
  completedWorkStatusExpanded?: boolean;
};

const emit = defineEmits<{
  toggleCompletedWorkStatus: [];
}>();
```

```vue
<TimelineRow
  v-for="row in dockRows"
  :key="row.key"
  :row="row"
  :state="state"
  :completed-work-status-expanded="expandedCompletedStatusKey === row.key"
  @toggle-completed-work-status="toggleCompletedStatus(row.key)"
>
  <template #completed-status-actions>
    <slot name="completed-status-actions" />
  </template>
</TimelineRow>
```

```css
.oc-work-status-completed {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
}

.oc-work-status-completed-summary {
  display: flex;
  min-width: 0;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
```

摘要与展开详情保持在同一个 Dock row 内，因此下一行始终是 `diff-summary`。运行中、重试、失败、停止和历史状态继续走现有分支。

- [ ] **Step 7: 运行 agent-chat 回归与类型检查并提交**

Run: `cd frontend && corepack pnpm vitest run packages/agent-chat/tests/opencode-timeline.test.ts packages/agent-chat/tests/opencode-like-state.test.ts`

Run: `cd frontend && corepack pnpm --filter @test-agent/agent-chat typecheck`

Expected: 全部 PASS。

```bash
git add frontend/packages/agent-chat
git commit -m "前端：优化工作状态完成态与事件行"
```

### Task 8: 将反馈按钮迁移到完成摘要并完成回归

**Files:**
- Modify: `frontend/apps/agent-web/tests/FigmaChatPanel.test.ts`
- Modify: `frontend/apps/agent-web/src/components/FigmaChatPanel.vue`
- Modify: `frontend/packages/agent-chat/README.md`
- Modify: `frontend/packages/agent-chat/src/PACKAGE.md`
- Modify: `frontend/apps/agent-web/README.md`
- Modify: `frontend/apps/agent-web/src/PACKAGE.md`
- Modify: `docs/superpowers/specs/2026-07-15-chat-work-status-summary-design.md`
- Modify: `.agents/session-log.md`

**Interfaces:**
- Consumes: Task 7 的 `#completed-status-actions` 插槽以及既有 `showTimelineFeedback`、`lastFeedbackableMessage`、`submitPositiveFeedback()`、`openNegativeFeedback()`。
- Produces: 成功完成态 Dock 横排中的反馈入口；`submit-feedback` 事件 payload、反馈 ID 选择和不满意弹窗保持原样。

- [ ] **Step 1: 为反馈位置与既有行为写失败测试**

```ts
it("renders successful feedback beside the collapsed status icon", async () => {
  const platformMessageId = "msg_0123456789abcdef0123456789abcdef";
  const wrapper = mount(FigmaChatPanel, {
    props: {
      messages: [{
        id: platformMessageId,
        messageId: platformMessageId,
        platformMessageId,
        role: "assistant",
        text: "已完成分析"
      }],
      running: false,
      runtimeStatus: "SUCCEEDED",
      processStatus: { status: "READY", initializable: false, message: "ready" }
    },
    global: { stubs: { MarkdownView: markdownViewStub } }
  });
  const summary = wrapper.get(".oc-work-status-completed-summary");
  expect(summary.findAll(".figma-chat-feedback-btn")).toHaveLength(2);
  expect(summary.get('[aria-label="展开已完成工作状态"]')).toBeTruthy();
  expect(wrapper.find(".figma-chat-timeline-actions").exists()).toBe(false);
  await summary.findAll(".figma-chat-feedback-btn")[0].trigger("click");
  expect(wrapper.emitted("submit-feedback")).toEqual([[{
    messageId: "msg_0123456789abcdef0123456789abcdef",
    rating: "POSITIVE"
  }]]);
});
```

补充断言：点击“不满意”仍打开原原因弹窗；提交中和已选评价样式不变；运行中、失败、停止与子智能体视图没有反馈按钮。

- [ ] **Step 2: 运行应用测试并确认 RED**

Run: `cd frontend && corepack pnpm vitest run apps/agent-web/tests/FigmaChatPanel.test.ts -t "feedback"`

Expected: 反馈仍位于 `.figma-chat-timeline-actions`，完成摘要内找不到按钮。

- [ ] **Step 3: 通过通用插槽迁移反馈按钮**

```vue
<OpencodeTimeline
  v-else
  :state="opencodeTimelineState"
  :work-status-dock-target="activeSubagentSessionId ? undefined : workStatusDockRef"
>
  <template #completed-status-actions>
    <div v-if="showTimelineFeedback && lastFeedbackableMessage" class="figma-chat-feedback">
      <button
        type="button"
        :class="[
          'figma-chat-feedback-btn',
          feedbackFor(lastFeedbackableMessage)?.rating === 'POSITIVE' && 'is-selected',
        ]"
        :disabled="feedbackSubmitting(lastFeedbackableMessage)"
        title="满意"
        @click="submitPositiveFeedback(lastFeedbackableMessage)"
      >
        <ThumbsUp :size="12" />
        <span>满意</span>
      </button>
      <button
        type="button"
        :class="[
          'figma-chat-feedback-btn',
          'figma-chat-feedback-btn--negative',
          feedbackFor(lastFeedbackableMessage)?.rating === 'NEGATIVE' && 'is-selected',
        ]"
        :disabled="feedbackSubmitting(lastFeedbackableMessage)"
        title="不满意"
        @click="openNegativeFeedback(lastFeedbackableMessage)"
      >
        <ThumbsDown :size="12" />
        <span>不满意</span>
      </button>
    </div>
  </template>
</OpencodeTimeline>
```

删除滚动时间线末尾的 `.figma-chat-timeline-actions` 结构和专用样式；保留 `.figma-chat-feedback`、按钮选中态、disabled 状态以及不满意弹窗逻辑。完成摘要使用 `flex-wrap: wrap`，保证窄屏按钮可换行且不造成页面横向溢出。

- [ ] **Step 4: 运行应用和跨包回归并确认 GREEN**

Run: `cd frontend && corepack pnpm vitest run apps/agent-web/tests/FigmaChatPanel.test.ts packages/agent-chat/tests/opencode-timeline.test.ts`

Run: `cd frontend && corepack pnpm --filter @test-agent/agent-web typecheck`

Expected: 全部 PASS，反馈提交 payload 与既有测试一致。

- [ ] **Step 5: 同步稳定文档并完成全量验证**

更新 `agent-chat`、`agent-web` README/PACKAGE，说明初始单行、事件按需增加和完成摘要插槽；在 `.agents/session-log.md` 合并记录本轮 Why / What / How / Result。

Run: `cd frontend && corepack pnpm lint`

Run: `cd frontend && corepack pnpm typecheck`

Run: `cd frontend && corepack pnpm test`

Run: `cd frontend && corepack pnpm build`

Run: `git diff --check`

Expected: 所有命令退出码为 0；在桌面和 260px 窄屏真实页面确认完成摘要横排、详情展开、Diff 顺序和反馈弹窗。

- [ ] **Step 6: 回顾并提交最终变更**

```bash
tail -n 160 .agents/session-log.md
git status --short
git add .agents/session-log.md docs/superpowers/specs/2026-07-15-chat-work-status-summary-design.md frontend/apps/agent-web/README.md frontend/apps/agent-web/src/PACKAGE.md frontend/apps/agent-web/src/components/FigmaChatPanel.vue frontend/apps/agent-web/tests/FigmaChatPanel.test.ts frontend/packages/agent-chat/README.md frontend/packages/agent-chat/src/PACKAGE.md
git diff --cached --check
git commit -m "前端：合并完成状态与反馈入口"
```
