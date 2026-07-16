# 删除对话合成工作状态行实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删除右侧对话时间线中前端合成的“思考中”和“正在工作 / 等待后续输出”行，同时保留真实 reasoning、工具、正文与运行终态。

**Architecture:** 仅收敛 `agent-chat` 的时间线投影和渲染层：投影不再生成两种合成行，联合类型、组件和专用样式随之删除。消息 reducer、RunEvent、HTTP API 和后端保持不变，历史与实时消息继续复用同一投影。

**Tech Stack:** Vue 3、TypeScript 6、Vitest、Testing Library Vue、pnpm workspace。

## Global Constraints

- 只删除 `thinking` 与 `working-status` 两种合成时间线行。
- 保留真实 reasoning 对应的“思考状态”行。
- 保留探索、更新待办、其他工具、流式正文、重试、错误和 Diff 摘要。
- 保留流式正文共用的 `.oc-thinking-dot` 动画样式。
- 不修改 RunEvent、HTTP API、后端、数据库、环境配置或 reducer 事件归并逻辑。
- 不创建 Git 分支，不暂存或修改工作区中已有的无关后端改动。

---

### Task 1: 删除合成状态行投影与渲染

**Files:**
- Modify: `frontend/packages/agent-chat/tests/opencode-like-state.test.ts`
- Modify: `frontend/packages/agent-chat/tests/opencode-timeline.test.ts`
- Modify: `frontend/packages/agent-chat/src/opencode-like/state/projection.ts`
- Modify: `frontend/packages/agent-chat/src/opencode-like/state/types.ts`
- Modify: `frontend/packages/agent-chat/src/opencode-like/components/TimelineRow.vue`
- Delete: `frontend/packages/agent-chat/src/opencode-like/components/rows/ThinkingRow.vue`
- Delete: `frontend/packages/agent-chat/src/opencode-like/components/rows/WorkingStatusRow.vue`
- Modify: `frontend/packages/agent-chat/src/opencode-like/styles/rows.css`

**Interfaces:**
- Consumes: `createTimelineRows(state: OpencodeLikeConversationState): TimelineRow[]` 与既有 `MessagePart` 投影。
- Produces: 不含 `thinking`、`working-status` 联合成员的 `TimelineRow`；运行中无真实 assistant part 时不产生助手占位行。

- [ ] **Step 1: 先修改投影测试，表达不再生成合成行**

将下一轮运行中的断言改为只保留用户消息：

```ts
expect(rows.at(-1)).toMatchObject({ type: "user-message", userMessageId: "msg_user_2" });
```

将 process part 场景改为只投影真实探索行：

```ts
it("does not add a synthetic working row when process parts have no text output", () => {
  const state = createOpencodeLikeState({
    messages: [
      userMessage("msg_user_1", "读取项目结构"),
      assistantMessage("msg_assistant_1", [
        toolPart("part_read_1", "read", { filePath: "README.md" }),
        toolPart("part_read_2", "read", { filePath: "frontend/README.md" })
      ])
    ],
    running: true
  });

  const rows = createTimelineRows(state);

  expect(rows.map((row) => row.type)).toEqual(["user-message", "context-tool-group"]);
});
```

- [ ] **Step 2: 修改组件测试，表达两个文案和专用 DOM 均不存在**

将初始运行态用例改为：

```ts
it("does not render a synthetic assistant row before the first real event", () => {
  const state = createOpencodeLikeState({ messages: [], running: true });
  const { container, queryByText } = render(OpencodeTimeline, { props: { state } });

  expect(queryByText("思考中")).toBeNull();
  expect(container.querySelector(".oc-thinking-row")).toBeNull();
  expect(container.querySelector(".oc-assistant-frame__avatar")).toBeNull();
});
```

将子 Agent 运行态用例末尾改为：

```ts
expect(getByText("探索")).toBeTruthy();
expect(queryByText("正在工作")).toBeNull();
expect(queryByText("等待后续输出")).toBeNull();
expect(container.querySelector(".oc-working-status")).toBeNull();
```

- [ ] **Step 3: 运行定向测试并确认 RED**

Run:

```bash
cd frontend
corepack pnpm exec vitest run packages/agent-chat/tests/opencode-like-state.test.ts packages/agent-chat/tests/opencode-timeline.test.ts
```

Expected: FAIL；旧实现仍生成 `thinking`、`working-status` 并渲染“思考中”“正在工作”。

- [ ] **Step 4: 删除投影中的合成行逻辑**

在 `projection.ts` 删除无消息时追加 `thinking` 的分支、最新轮次无 part 时追加 `thinking` 的分支，以及有过程 part 无正文时追加 `working-status` 的分支；同时从 `AssistantRowAccumulator` 删除仅服务于该逻辑的 `hasVisibleTextOutput` 字段及 text part 赋值。保留以下真实分组投影入口：

```ts
for (const group of groups) {
  appendAssistantGroupRow(rows, state, accumulator, {
    group,
    assistantMessageId,
    userMessageId
  });
}
```

- [ ] **Step 5: 删除类型与渲染组件**

从 `TimelineRow` 联合类型中删除：

```ts
{ type: "thinking"; key: string; userMessageId: string }
```

以及整个 `working-status` 对象成员。从 `TimelineRow.vue` 删除 `ThinkingRow`、`WorkingStatusRow` 导入和对应 `v-else-if` 分支，再删除两个 Vue 文件。

- [ ] **Step 6: 删除专用样式但保留共享动画**

将公共状态行选择器收敛为：

```css
.oc-retry-row,
.oc-error-row {
  display: flex;
  align-items: center;
  gap: var(--oc-space-2);
  color: var(--oc-muted);
  font-size: var(--oc-text-sm);
  padding: 4px 0;
}
```

删除 `.oc-thinking-frame`、`.oc-working-frame`、`.oc-thinking-row` 和 `.oc-working-status*` 专用规则；不得删除 `styles/animations.css` 中的 `.oc-thinking-dot`，因为 `TextPartView.vue` 仍使用它。

- [ ] **Step 7: 运行定向测试与包类型检查并确认 GREEN**

Run:

```bash
cd frontend
corepack pnpm exec vitest run packages/agent-chat/tests/opencode-like-state.test.ts packages/agent-chat/tests/opencode-timeline.test.ts
corepack pnpm --filter @test-agent/agent-chat typecheck
```

Expected: 两个测试文件全部 PASS，`agent-chat` 类型检查退出码为 0。

- [ ] **Step 8: 回顾会话日志并提交代码与测试**

Run:

```bash
tail -n 420 .agents/session-log.md
git diff --check
git add frontend/packages/agent-chat/tests/opencode-like-state.test.ts frontend/packages/agent-chat/tests/opencode-timeline.test.ts frontend/packages/agent-chat/src/opencode-like/state/projection.ts frontend/packages/agent-chat/src/opencode-like/state/types.ts frontend/packages/agent-chat/src/opencode-like/components/TimelineRow.vue frontend/packages/agent-chat/src/opencode-like/components/rows/ThinkingRow.vue frontend/packages/agent-chat/src/opencode-like/components/rows/WorkingStatusRow.vue frontend/packages/agent-chat/src/opencode-like/styles/rows.css
git diff --cached --check
git commit -m "前端：删除对话合成工作状态行"
```

Expected: 暂存区仅包含本任务的 `agent-chat` 代码和测试，提交成功。

### Task 2: 同步稳定文档并完成全量验证

**Files:**
- Modify: `frontend/packages/agent-chat/README.md`
- Modify: `frontend/apps/agent-web/README.md`
- Modify: `.agents/session-log.md`

**Interfaces:**
- Consumes: Task 1 已收敛的时间线展示行为。
- Produces: 与实现一致的包级、应用级稳定说明和会话交接记录。

- [ ] **Step 1: 更新 agent-chat 包说明**

把运行态说明改为：

```md
- 运行中状态只展示已经收到的真实 reasoning、工具状态和 running 文本 live preview；不为尚无 assistant part 的轮次追加“思考中”，也不在只有过程 part、尚无正文时追加“正在工作”兜底行。会话级 `running` 只覆盖最新用户轮次，历史轮次的 context/reasoning/tool 分组始终保留各 part 已收敛的完成或失败状态；`runtimeStatus.type=retry` 时展示 retry 行和倒计时文案，失败运行追加统一错误行。
```

- [ ] **Step 2: 更新 agent-web 应用说明**

在右侧对话行为段明确：

```md
运行中的工具事件、reasoning 和 running 文本 live preview 统一由 `OpencodeTimeline` 展示；尚未收到真实 assistant part 时不显示“思考中”占位，只有过程 part、尚无正文时也不追加“正在工作 / 等待后续输出”兜底行。
```

删除“初始思考行”和“状态列与思考中对齐”等已经失效的描述，但保留真实 reasoning 在运行期间显示“思考中”状态的说明。

- [ ] **Step 3: 运行前端全量验证**

Run:

```bash
cd frontend
corepack pnpm typecheck
corepack pnpm test
corepack pnpm build
cd ..
git diff --check
```

Expected: typecheck、Vitest 全量测试、生产构建和 diff 检查全部通过；仅允许记录仓库既有且不影响退出码的构建警告。

- [ ] **Step 4: 更新会话日志**

在 `.agents/session-log.md` 追加一条会话级记录，包含：

```md
### 2026-07-15 - 删除对话合成工作状态行

- Why: 用户希望过程时间线只展示真实事件，不再出现前端兜底生成的“思考中”和“正在工作 / 等待后续输出”。
- What: `agent-chat` 停止投影并删除 `thinking`、`working-status` 行，保留真实 reasoning、工具、流式正文、重试、错误和 Diff。
- How: 通过投影与组件测试先复现旧占位行，再删除对应类型、组件和专用样式；同步包与应用 README，未修改 reducer、API、RunEvent、后端、数据库或环境配置。
- Result: 记录定向测试、全量 typecheck、Vitest 和生产构建的实际结果，以及仍存在的无关风险。
```

- [ ] **Step 5: 只暂存文档和会话日志并提交**

Run:

```bash
git add frontend/packages/agent-chat/README.md frontend/apps/agent-web/README.md .agents/session-log.md
git diff --cached --check
git diff --cached --name-only
git commit -m "文档：同步对话真实事件展示约定"
```

Expected: 暂存区只包含两个 README 和 `.agents/session-log.md`，提交成功；工作区原有无关后端修改仍保持未暂存。
