# Historical Work Status Toggle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让非最后一轮的历史工作状态在展开后保留原状态按钮，并把状态块显示在按钮下方，再次点击同一按钮即可收起。

**Architecture:** 保留 `OpencodeTimeline.vue` 现有的 `expandedHistoricalStatusKey` 单开状态管理，只统一 `TimelineRow.vue` 的历史状态 DOM 结构。历史状态固定渲染摘要行，按 `historicalWorkStatusExpanded` 条件渲染下方 `WorkStatusRow`，不改时间线投影、Run 归属或反馈数据流。

**Tech Stack:** Vue 3、TypeScript、Vitest、Testing Library Vue、pnpm workspace。

## Global Constraints

- 只修改历史工作状态交互、对应测试和稳定文档，不重构时间线状态管理。
- 历史状态按钮和反馈入口始终位于同一摘要行。
- 展开后状态块位于摘要行下方，按钮不消失；再次点击同一按钮收起。
- 同一时间只允许展开一个历史工作状态。
- 不修改 API、RunEvent、DTO、数据库、后端、安全策略或兼容性契约，不引入新依赖。
- 不暂存或提交工作区中既有的 Mermaid 编辑器未提交改动。

---

## File Map

- `frontend/packages/agent-chat/tests/opencode-timeline.test.ts`：从用户点击入口验证历史状态展开、收起、DOM 顺序和单开规则。
- `frontend/packages/agent-chat/src/opencode-like/components/TimelineRow.vue`：统一历史状态摘要行与状态块结构。
- `frontend/packages/agent-chat/src/opencode-like/styles/work-status.css`：为历史摘要行和下方状态块提供与完成态一致的纵向布局，删除旧右上角收起按钮样式。
- `frontend/packages/agent-chat/README.md`、`frontend/packages/agent-chat/src/PACKAGE.md`：记录 agent-chat 稳定交互。
- `frontend/apps/agent-web/README.md`、`frontend/apps/agent-web/src/PACKAGE.md`：记录工作台组合层可见行为。
- `.agents/session-log.md`：在最终验证后记录根因、修改方式和结果。

### Task 1: 用 TDD 统一历史状态展开与收起

**Files:**
- Modify: `frontend/packages/agent-chat/tests/opencode-timeline.test.ts:196-223`
- Modify: `frontend/packages/agent-chat/src/opencode-like/components/TimelineRow.vue:15-16,159-213`
- Modify: `frontend/packages/agent-chat/src/opencode-like/styles/work-status.css:69-93`

**Interfaces:**
- Consumes: `historicalWorkStatusExpanded?: boolean` 与既有 `toggleHistoricalWorkStatus` 事件。
- Produces: 始终存在的 `.oc-work-status-history-trigger`、动态 `aria-expanded`、`.oc-work-status-history-summary` 和条件渲染的下方 `WorkStatusRow`。

- [x] **Step 1: 先扩展组件测试，锁定同一按钮往返展开行为**

将 `collapses historical work status to one icon and allows only one inline expansion` 用例的首次展开断言改为：

```ts
const historicalRow = container.querySelector(".oc-work-status-history") as HTMLElement;
const collapsedTrigger = getByRole("button", { name: "展开历史工作状态" });
expect(collapsedTrigger.getAttribute("aria-expanded")).toBe("false");
expect(historicalRow.querySelector(".oc-work-status")).toBeNull();

await fireEvent.click(collapsedTrigger);

const expandedTrigger = getByRole("button", { name: "收起历史工作状态" });
const historicalSummary = historicalRow.querySelector(".oc-work-status-history-summary") as HTMLElement;
const historicalStatus = historicalRow.querySelector(".oc-work-status") as HTMLElement;
expect(expandedTrigger).toBe(collapsedTrigger);
expect(expandedTrigger.getAttribute("aria-expanded")).toBe("true");
expect(historicalStatus).toBeTruthy();
expect(historicalSummary.compareDocumentPosition(historicalStatus) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();

await fireEvent.click(expandedTrigger);

expect(getByRole("button", { name: "展开历史工作状态" })).toBe(collapsedTrigger);
expect(collapsedTrigger.getAttribute("aria-expanded")).toBe("false");
expect(historicalRow.querySelector(".oc-work-status")).toBeNull();
```

保留用例后半段新增第三轮后的断言，继续证明新用户消息会关闭历史展开且两个历史按钮都保留；随后增加两个历史入口之间的切换断言：

```ts
const historicalTriggers = container.querySelectorAll(".oc-work-status-history-trigger");
await fireEvent.click(historicalTriggers[0] as HTMLElement);
expect(container.querySelectorAll(".oc-work-status-history .oc-work-status")).toHaveLength(1);

await fireEvent.click(historicalTriggers[1] as HTMLElement);
expect(container.querySelectorAll(".oc-work-status-history .oc-work-status")).toHaveLength(1);
expect((historicalTriggers[0] as HTMLElement).getAttribute("aria-expanded")).toBe("false");
expect((historicalTriggers[1] as HTMLElement).getAttribute("aria-expanded")).toBe("true");
```

- [x] **Step 2: 运行定向用例并确认它因旧 DOM 分支失败**

Run:

```bash
cd frontend
corepack pnpm exec vitest run packages/agent-chat/tests/opencode-timeline.test.ts -t "collapses historical work status to one icon and allows only one inline expansion"
```

Expected: FAIL；旧实现的历史按钮没有 `aria-expanded="false"`，且展开后 `.oc-work-status-history-trigger` 被独立收起箭头替换。

- [x] **Step 3: 最小修改历史状态模板**

把图标导入改为：

```ts
import { Activity } from "lucide-vue-next";
```

用以下结构替换历史状态的收起分支，并让后续通用 `work-status` 分支只负责最新非完成状态：

```vue
<div
  v-else-if="row.type === 'work-status' && !row.isLatest"
  class="oc-row oc-work-status-history"
>
  <div class="oc-work-status-history-summary">
    <OcIconButton
      class="oc-work-status-history-trigger"
      :label="historicalWorkStatusExpanded ? '收起历史工作状态' : '展开历史工作状态'"
      :aria-expanded="historicalWorkStatusExpanded"
      @click="emit('toggleHistoricalWorkStatus')"
    >
      <Activity aria-hidden="true" />
    </OcIconButton>
    <slot name="completed-status-actions" :row="row" />
  </div>
  <WorkStatusRow
    v-if="historicalWorkStatusExpanded"
    :row="row"
    :state="state"
    :open-event-key="openWorkStatusEventKey"
    @toggle-event="(eventKey) => emit('toggleWorkStatusEvent', eventKey)"
    @close-event="emit('closeWorkStatusEvent')"
  />
</div>
```

通用分支保留最新运行态状态块，不再渲染历史专用收起按钮或历史反馈插槽：

```vue
<div v-else-if="row.type === 'work-status'" class="oc-row oc-work-status-container">
  <WorkStatusRow
    :row="row"
    :state="state"
    :open-event-key="openWorkStatusEventKey"
    @toggle-event="(eventKey) => emit('toggleWorkStatusEvent', eventKey)"
    @close-event="emit('closeWorkStatusEvent')"
  />
</div>
```

- [x] **Step 4: 调整历史状态布局并删除旧箭头样式**

将历史样式改为：

```css
.oc-work-status-history {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
}

.oc-work-status-history-summary {
  display: flex;
  min-width: 0;
  min-height: 28px;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.oc-work-status-history-trigger {
  width: 28px;
  height: 28px;
  color: var(--oc-muted);
}

.oc-work-status-history-trigger svg {
  width: 15px;
  height: 15px;
}
```

完整删除 `.oc-work-status-history-collapse` 的选择器和绝对定位规则。

- [x] **Step 5: 运行定向用例与 agent-chat 时间线测试**

Run:

```bash
cd frontend
corepack pnpm exec vitest run packages/agent-chat/tests/opencode-timeline.test.ts
corepack pnpm --filter @test-agent/agent-chat typecheck
```

Expected: `opencode-timeline.test.ts` 全部 PASS；agent-chat `vue-tsc` 退出码为 0。

- [x] **Step 6: 提交交互实现**

```bash
git add frontend/packages/agent-chat/tests/opencode-timeline.test.ts \
  frontend/packages/agent-chat/src/opencode-like/components/TimelineRow.vue \
  frontend/packages/agent-chat/src/opencode-like/styles/work-status.css
git diff --cached --check
git commit -m "前端：统一历史工作状态展开交互"
```

### Task 2: 同步稳定文档并完成前端回归

**Files:**
- Modify: `frontend/packages/agent-chat/README.md:21`
- Modify: `frontend/packages/agent-chat/src/PACKAGE.md:15`
- Modify: `frontend/apps/agent-web/README.md:49`
- Modify: `frontend/apps/agent-web/src/PACKAGE.md:34`
- Modify: `.agents/session-log.md`

**Interfaces:**
- Consumes: Task 1 已验证的历史状态交互。
- Produces: 与实现一致的稳定文档和后续开发者可复用的验证记录。

- [x] **Step 1: 更新四处稳定文档**

在现有历史状态描述中统一加入以下语义：

```text
历史轮默认收为状态图标；点击后按钮保持在摘要行，状态块显示在按钮下方，再次点击同一按钮收起，且同一时间只展开一个历史状态。
```

保持各文件原有段落结构，不改与本任务无关的功能说明。

- [x] **Step 2: 串行执行前端回归，避免 VitePress 临时目录竞争**

Run:

```bash
cd frontend
corepack pnpm test
corepack pnpm lint
corepack pnpm typecheck
corepack pnpm build
```

Expected: Vitest 全量通过（允许仓库既有显式 skipped 用例）；lint、typecheck、agent-web production build 均退出码为 0。命令必须串行，避免 `user-manual/.vitepress/.temp` 竞争。

- [x] **Step 3: 检查变更边界与空白错误**

Run:

```bash
git diff --check
git status --short
git diff -- frontend/packages/agent-chat frontend/apps/agent-web/README.md frontend/apps/agent-web/src/PACKAGE.md .agents/session-log.md
```

Expected: `git diff --check` 无输出；任务差异仅包含历史工作状态交互、测试、四处稳定文档和一条会话日志，既有 Mermaid 改动保持未暂存。

- [x] **Step 4: 在会话日志顶部追加本次交接记录**

追加：

```markdown
#### 2026-07-17 - 统一历史工作状态展开交互

- Why:
  - 非最后一轮历史状态展开后会移除原 Activity 按钮并换成状态块右上角收起箭头，与最后一轮完成态的固定摘要行交互不一致。
- What:
  - 历史状态固定保留摘要行和同一 Activity 按钮，按钮动态切换展开/收起语义；状态块在摘要行下方按需显示，反馈入口保持同排，独立收起箭头删除。
- How:
  - 保留 `expandedHistoricalStatusKey` 单开状态，只调整 `TimelineRow` 模板和样式；组件测试覆盖按钮身份、`aria-expanded`、DOM 顺序、再次点击收起和新增轮次自动关闭。
- Result:
  - agent-chat 定向测试、全量前端 Vitest、lint、typecheck、production build 和 `git diff --check` 均通过；未修改 API、RunEvent、DTO、数据库、后端、安全或兼容性契约。
```

- [x] **Step 5: 回顾会话日志、只暂存本任务文档并提交**

```bash
sed -n '1,220p' .agents/session-log.md
git add docs/superpowers/plans/2026-07-17-historical-work-status-toggle.md \
  frontend/packages/agent-chat/README.md \
  frontend/packages/agent-chat/src/PACKAGE.md \
  frontend/apps/agent-web/README.md \
  frontend/apps/agent-web/src/PACKAGE.md \
  .agents/session-log.md
git diff --cached --check
git diff --cached --stat
git commit -m "文档：同步历史工作状态交互说明"
```

- [x] **Step 6: 确认最终提交和工作区隔离状态**

Run:

```bash
git log -3 --oneline
git status --short
```

Expected: 设计、实现、文档提交均存在；只剩用户既有 Mermaid 编辑器改动，且这些文件没有进入本任务提交。
