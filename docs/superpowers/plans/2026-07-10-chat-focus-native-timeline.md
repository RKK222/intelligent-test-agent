# 原生 Timeline 外层专注对话 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改动 OpenCode 原生 Timeline 的前提下，为主工作台对话增加可响应式展开的“本轮活动”外层入口，减少过程信息对消息流的干扰。

**Architecture:** `AgentWorkbench` 向 `FigmaChatPanel` 显式下传当前 `sessionId`、`runId`、`runStatus`；`FigmaChatPanel` 在 Timeline 宿主层新增一个只读活动摘要与浮层/底部抽屉，不向 `OpencodeTimeline`、行投影或 part renderer 传入新 props，也不通过 CSS 覆盖其内部布局。活动摘要由一个独立的纯函数根据既有 Todo、子 Agent、Ask、Permission、当前会话/Run 状态生成；浮层只展示摘要，既有的 `figma-chat-question-dock` 保持原位置、原组件与原 action handler。

**Tech Stack:** Vue 3、TypeScript、Vitest、Vue Test Utils、现有 Tailwind/CSS、Container Queries。

---

## Chunk 1: 活动摘要的纯数据边界

### Task 1: 用失败测试锁定活动摘要规则

**Files:**
- Create: `frontend/apps/agent-web/src/components/chat-activity-summary.ts`
- Create: `frontend/apps/agent-web/tests/chat-activity-summary.test.ts`

- [ ] **Step 1: 写入失败的活动摘要测试**

```ts
import { describe, expect, it } from "vitest";
import { buildChatActivitySummary } from "../src/components/chat-activity-summary";

describe("buildChatActivitySummary", () => {
  it("仅有 Timeline 内容时不创建外层活动入口", () => {
    expect(buildChatActivitySummary({ sessionId: "ses-root", run: null, todos: [], subagentsBySessionId: {}, permissions: [], questions: [] })).toBeNull();
  });

  it("在 Run 结束后仍保留当前会话未处理的 Ask 摘要", () => {
    const summary = buildChatActivitySummary({
      sessionId: "ses-root",
      run: { runId: "run-ended", status: "SUCCEEDED" },
      todos: [],
      subagentsBySessionId: {},
      permissions: [],
      questions: [{ requestId: "ask-1", sessionId: "ses-root", createdAt: "2026-07-10T00:00:00Z", questions: [{ questionId: "q-1", text: "继续吗？", kind: "text" }] }],
    });

    expect(summary?.pendingConfirmationCount).toBe(1);
    expect(summary?.items[0]).toMatchObject({ kind: "confirmation" });
  });

  it("只汇总已有 Todo、子 Agent、Ask 和 Permission，不重新投影工具 Timeline", () => {
    const summary = buildChatActivitySummary({
      sessionId: "ses-root",
      run: { runId: "run-1", status: "RUNNING" },
      todos: [{ id: "todo-1", text: "检查日志", status: "in_progress" }],
      subagentsBySessionId: { child: { sessionId: "child", parentSessionId: "ses-root", agentName: "log-agent", title: "日志分析", status: "running", updatedAt: "2026-07-10T00:00:00Z" } },
      permissions: [],
      questions: [],
    });

    expect(summary?.items.map((item) => item.kind)).toEqual(["subagent", "todo"]);
  });
});
```

- [ ] **Step 2: 运行测试确认 RED**

Run: `cd frontend && corepack pnpm test --run apps/agent-web/tests/chat-activity-summary.test.ts`

Expected: FAIL，因为 `chat-activity-summary` 尚不存在。

- [ ] **Step 3: 最小实现纯函数与类型**

```ts
export type ChatActivitySummary = {
  pendingConfirmationCount: number;
  items: ChatActivityItem[];
};

export function buildChatActivitySummary(input: ChatActivityInput): ChatActivitySummary | null {
  // 只读取宿主已有运行态，不读取或复制 OpencodeTimeline 的行/part 投影。
}
```

实现规则：确认项优先；仅保留 `sessionId` 匹配当前 session 的 Ask/Permission 和 `parentSessionId` 匹配的子 Agent；Todo 没有 sessionId/runId，故只使用父级当前 session 已归一化的 `chatState.todos`，不在 helper 中伪造跨 Run 过滤；其次是运行中的子 Agent、运行中的 Todo。当前 Run 失败时只显示“本轮运行失败”的只读状态（不虚构错误详情）；新 RunId 出现时由父级传入的新 run 覆盖。不得新增工具完成记录、重试操作或原生 Ask/Permission 操作；没有可展示项返回 `null`。

- [ ] **Step 4: 运行测试确认 GREEN**

Run: `cd frontend && corepack pnpm test --run apps/agent-web/tests/chat-activity-summary.test.ts`

Expected: PASS。

- [ ] **Step 5: 复查类型与注释**

Run: `cd frontend && corepack pnpm typecheck`

Expected: PASS；新类型、聚合顺序和“不得读取 Timeline 投影”的边界均有中文注释。

## Chunk 2: 只读活动入口与原生 Timeline 隔离

### Task 2: 在 Timeline 宿主旁增加可访问的活动面板组件

**Files:**
- Create: `frontend/apps/agent-web/src/components/ChatActivityPanel.vue`
- Modify: `frontend/apps/agent-web/src/components/AgentWorkbench.vue: 现有 FigmaChatPanel 调用处，仅下传 currentSessionId/currentRunId/currentRunStatus`
- Modify: `frontend/apps/agent-web/src/components/FigmaChatPanel.vue: import 区、活动状态、header 入口、Timeline 外层 overlay host、局部样式`
- Modify: `frontend/apps/agent-web/tests/FigmaChatPanel.test.ts: 新增活动入口交互用例`

- [ ] **Step 1: 写入失败的组件测试**

```ts
it("打开和关闭本轮活动时不替换原生 Timeline，也不移动原生问题 dock", async () => {
  const wrapper = mount(FigmaChatPanel, {
    props: {
      messages: [],
      currentSessionId: "ses-root",
      todos: [{ id: "todo-1", text: "检查日志", status: "in_progress" }],
      questions: [{ requestId: "ask-1", sessionId: "ses-root", createdAt: "2026-07-10T00:00:00Z", questions: [{ questionId: "q-1", text: "继续吗？", kind: "text" }] }],
    } as any,
  });

  await wrapper.get('[data-testid="chat-activity-trigger"]').trigger("click");
  expect(wrapper.get('[data-testid="chat-activity-panel"]').isVisible()).toBe(true);
  expect(wrapper.findComponent(OpencodeTimeline).exists()).toBe(true);
  expect(wrapper.get(".figma-chat-question-dock").isVisible()).toBe(true);

  await window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
  expect(wrapper.find('[data-testid="chat-activity-panel"]').exists()).toBe(false);
});
```

另加断言：入口含 `aria-expanded`；面板不含原生 Ask/Permission 的确认按钮；既有 `figma-chat-question-*` 与 `figma-chat-permission-*` 用例继续通过；调用现有 reply/reject 后 dock 与摘要计数同步消失。

- [ ] **Step 2: 运行定向测试确认 RED**

Run: `cd frontend && corepack pnpm test --run apps/agent-web/tests/FigmaChatPanel.test.ts`

Expected: 新入口/面板选择器不存在而失败；既有用例不应先失败。

- [ ] **Step 3: 最小实现外层组件**

`ChatActivityPanel.vue` 只接收 `summary`、`open`、响应式 mode 和关闭事件。它：

- 以 `button` 暴露入口，提供 `aria-expanded`、可访问名称与活动数量；
- 只渲染确认、子 Agent、Todo 和错误摘要；
- 对确认项使用“请在对话中处理”文案，不复制原生处理动作；
- 桌面作为非模态 popover，点击外部或 Escape 关闭；
- 不读取、包裹、选择或改写 `OpencodeTimeline` 内节点。

`AgentWorkbench.vue` 只把 `session.value?.sessionId`、`run.value?.runId`、`run.value?.status` 作为只读 props 下传，不迁移状态或修改 RunEvent reducer。

`FigmaChatPanel.vue` 把入口放入现有 `.figma-chat-header`，把 `ChatActivityPanel` 放入 `.figma-chat-root` 的绝对定位 overlay host（与 `.figma-chat-scroll` 同级，不能插入滚动容器）。host 必须不占滚动高度，且只使用独立 `figma-chat-activity-*` 选择器。入口/面板在 attachment、raw output、history、changes drawer、`negativeFeedbackOpen`、模型下拉 `dropdownOpen`、Agent 下拉 `agentDropdownOpen`、Skill 面板 `showSkillPanel` 或 @Agent 面板 `showAgentPanel` 为真时禁用或关闭，避免覆盖已有浮层。Escape 顺序保持既有 attachment → raw output → history → changes drawer；这些都未打开时才关闭活动面板，其他下拉/面板继续由其既有事件处理逻辑关闭。必须为新开关、ref、ResizeObserver 和 focus restore 使用独立状态变量。

- [ ] **Step 4: 运行定向测试确认 GREEN**

Run: `cd frontend && corepack pnpm test --run apps/agent-web/tests/chat-activity-summary.test.ts apps/agent-web/tests/FigmaChatPanel.test.ts`

Expected: PASS；既有 Timeline、问题 dock、权限操作、进程状态卡、输入框、附件、原始输出、历史抽屉、文件变更抽屉和负反馈弹框测试均通过。

- [ ] **Step 5: 进行本 chunk 提交**

```bash
git add frontend/apps/agent-web/src/components/chat-activity-summary.ts \
  frontend/apps/agent-web/src/components/ChatActivityPanel.vue \
  frontend/apps/agent-web/src/components/AgentWorkbench.vue \
  frontend/apps/agent-web/src/components/FigmaChatPanel.vue \
  frontend/apps/agent-web/tests/chat-activity-summary.test.ts \
  frontend/apps/agent-web/tests/FigmaChatPanel.test.ts
git commit -m "新增对话本轮活动入口"
```

## Chunk 3: 容器响应式、焦点与回归保护

### Task 3: 为活动面板补齐容器响应式与可访问性测试

**Files:**
- Modify: `frontend/apps/agent-web/src/components/ChatActivityPanel.vue: container-query 样式和焦点管理`
- Modify: `frontend/apps/agent-web/src/components/FigmaChatPanel.vue: ResizeObserver 驱动的活动面板 mode`
- Modify: `frontend/apps/agent-web/tests/FigmaChatPanel.test.ts: 响应式、焦点和既有覆盖层回归用例`
- Modify: `frontend/apps/agent-web/README.md: 聊天面板交互说明`
- Modify: `frontend/packages/agent-chat/README.md: 原生 Timeline 不受外层活动入口影响的边界说明（按需）`

- [ ] **Step 1: 写入失败的响应式和焦点测试**

```ts
it("窄聊天容器中的活动面板以底部抽屉语义打开并在关闭后恢复焦点", async () => {
  stubResizeObserver({ width: 719 });
  const wrapper = mount(FigmaChatPanel, { attachTo: document.body, props: runningProps as any });
  const trigger = wrapper.get('[data-testid="chat-activity-trigger"]');
  await trigger.trigger("click");

  const panel = wrapper.get('[data-testid="chat-activity-panel"]');
  expect(panel.attributes("role")).toBe("dialog");
  await panel.get('[aria-label="关闭本轮活动"]').trigger("click");
  expect(document.activeElement).toBe(trigger.element);
});
```

补充 RED 用例：mock `ResizeObserver` 在 719px / 720px 回调时分别断言 `dialog` / 非模态 `region` 语义；活动面板打开时 Escape 先关闭活动面板；attachment、raw output、history、changes drawer、负反馈弹框、模型下拉、Agent 下拉、Skill 面板或 @Agent 面板已打开时，活动入口不能开启/抢焦点，Escape 继续遵循既有关闭顺序。补充样式级回归断言：面板宿主声明 `container-type: inline-size`，720px 使用 `@container` 切换；窄抽屉包含安全区 padding，不建立横向滚动。JSDOM 不可靠的布局断言交由浏览器人工验收。

- [ ] **Step 2: 运行测试确认 RED**

Run: `cd frontend && corepack pnpm test --run apps/agent-web/tests/FigmaChatPanel.test.ts`

Expected: 缺少角色、焦点恢复或响应式语义而失败。

- [ ] **Step 3: 最小实现响应式与焦点管理**

实现细则：

- 仅活动面板宿主声明 `container-type: inline-size`；不得向 `.oc-timeline-root` 或任何 `opencode-like` 选择器写样式；
- `FigmaChatPanel` 对自己的 root 使用 `ResizeObserver`，将 720px container breakpoint 映射为显式 `activityPresentation: "popover" | "drawer"` prop；测试 mock 该 observer，CSS 只负责视觉布局；
- `@container (max-width: 719px)` 使用全宽底部抽屉；对应 `activityPresentation === "drawer"` 时设置 `role="dialog" aria-modal="true"`，捕获焦点并关闭后恢复入口；
- `@container (min-width: 720px)` 使用非模态 popover；对应 `activityPresentation === "popover"` 时设置 `role="region"`，不捕获焦点；
- 浮层定位可翻转、最大高度可滚动；页面滚动、Timeline 自动跟随与 Composer 不受影响；
- 处理 `prefers-reduced-motion`，并为 `env(safe-area-inset-bottom)` 留出底部空间。

- [ ] **Step 4: 运行全部前端验证**

Run:

```bash
cd frontend
corepack pnpm test --run apps/agent-web/tests/chat-activity-summary.test.ts apps/agent-web/tests/FigmaChatPanel.test.ts apps/agent-web/tests/FigmaShell.test.ts
corepack pnpm test --run packages/agent-chat/tests/opencode-timeline.test.ts
corepack pnpm lint
corepack pnpm typecheck
corepack pnpm build
```

Expected: 全部 exit 0；构建若仅保留现有 CSS import 顺序/大包提示，需在交付中如实说明。

- [ ] **Step 5: 启动并执行人工验收**

Run: `cd frontend && corepack pnpm --filter @test-agent/agent-web dev -- --host 127.0.0.1 --port 3010`

Check:

1. 正常宽度打开/关闭活动浮层，不影响 Timeline 原有头像、工具展开、子 Agent 跳转、Ask/Permission、历史抽屉、原始输出、进程状态卡和 Composer；
2. 拖窄右侧聊天面板至 719px 以下，确认抽屉全宽单列、可关闭、不遮挡 Composer；
3. 键盘验证 Escape、焦点恢复、抽屉焦点捕获；
4. 流式输出期间打开/关闭活动面板，确认不触发 Timeline 强制滚动或自动重开。
5. 运行 `git diff --exit-code -- frontend/packages/agent-chat/src/opencode-like/`，确认本计划没有改动 Huangzhenren 的原生 Timeline 文件。

- [ ] **Step 6: 更新文档与会话记录并提交**

```bash
git add frontend/apps/agent-web/README.md frontend/packages/agent-chat/README.md \
  frontend/apps/agent-web/src/components/ChatActivityPanel.vue \
  frontend/apps/agent-web/tests/FigmaChatPanel.test.ts .agents/session-log.md
git commit -m "完善对话活动入口响应式体验"
```

实施前先回顾 `.agents/session-log.md` 和工作区状态；只暂存本计划涉及的文件，绝不覆盖当前其他人对设计文档和 README 的未提交修改。
