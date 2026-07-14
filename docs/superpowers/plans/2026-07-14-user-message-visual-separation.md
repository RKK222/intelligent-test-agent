# 用户消息浅灰背景与视觉间距实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 opencode-like 主时间线用户消息增加语义浅灰背景，并在用户消息下方保留 12px 空白。

**Architecture:** 只调整 `@test-agent/agent-chat` 的用户消息 CSS，继续复用现有 `--oc-surface-muted` 与 `--oc-space-3` token，不增加 DOM、组件 props 或主题变量。Vitest 通过读取现有 `rows.css` 建立两条独立样式契约，验证背景与下边距不会回退。

**Tech Stack:** Vue 3、TypeScript 6、CSS、Vitest 4、Node.js `fs/path`

## Global Constraints

- 只修改 `frontend/packages/agent-chat` 主时间线样式、对应测试和包 README。
- 用户消息继续右对齐，气泡宽度、文字、复制按钮、圆角、边框和上下文 chip 不变。
- 用户气泡背景必须使用 `var(--oc-surface-muted)`，不得新增或写死另一套灰色。
- 用户消息行必须使用 `margin: 4px 0 var(--oc-space-3)`，只增加下方间距。
- 不修改旧 `FigmaChatPanel.vue` 消息循环、API、RunEvent、DTO、数据库、后端、安全策略或环境配置。

---

### Task 1: 调整用户消息视觉分隔

**Files:**
- Modify: `frontend/packages/agent-chat/tests/opencode-timeline.test.ts`
- Modify: `frontend/packages/agent-chat/src/opencode-like/styles/rows.css`
- Modify: `frontend/packages/agent-chat/README.md`

**Interfaces:**
- Consumes: `rows.css` 现有 `.oc-user-message`、`.oc-user-message__bubble` 选择器，以及 `tokens.css` 提供的 `--oc-surface-muted`、`--oc-space-3`。
- Produces: 用户气泡使用语义浅灰背景，用户消息行下方间距为 12px；DOM 和 TypeScript 接口不变。

- [ ] **Step 1: 增加两条失败的样式契约测试**

在 `frontend/packages/agent-chat/tests/opencode-timeline.test.ts` 顶部增加：

```ts
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
```

在 `describe("OpencodeTimeline", ...)` 前增加：

```ts
const rowsCss = readFileSync(
  resolve(process.cwd(), "packages/agent-chat/src/opencode-like/styles/rows.css"),
  "utf8"
);
```

在 `OpencodeTimeline` describe 内增加两条独立测试：

```ts
it("uses the muted surface token for user message bubbles", () => {
  expect(rowsCss).toMatch(
    /\.oc-user-message__bubble\s*\{[^}]*background: var\(--oc-surface-muted\);/s
  );
});

it("keeps a small gap below user messages", () => {
  expect(rowsCss).toMatch(
    /\.oc-user-message\s*\{[^}]*margin: 4px 0 var\(--oc-space-3\);/s
  );
});
```

- [ ] **Step 2: 运行定向测试并确认 RED**

Run:

```bash
cd frontend
corepack pnpm exec vitest run packages/agent-chat/tests/opencode-timeline.test.ts
```

Expected: 新增两条测试 FAIL；输出分别显示现有 `background: #ffffff` 与 `margin: 4px 0 6px` 不匹配目标正则。其余既有时间线测试保持通过。

- [ ] **Step 3: 写入最小 CSS 实现**

把 `rows.css` 的用户消息规则调整为：

```css
.oc-user-message {
  display: flex;
  justify-content: flex-end;
  margin: 4px 0 var(--oc-space-3);
  align-items: flex-start;
}
```

把用户气泡背景调整为：

```css
.oc-user-message__bubble {
  position: relative;
  width: fit-content;
  max-width: 100%;
  border: 1px solid #e3e8f7;
  background: var(--oc-surface-muted);
```

该步骤只替换 `margin` 与 `background` 两行，其他声明保持原样。

- [ ] **Step 4: 同步 agent-chat README**

在 `frontend/packages/agent-chat/README.md` 第一条职责中，将用户消息视觉说明补充为：

```markdown
用户问题保持右对齐，使用语义浅灰气泡，并通过 12px 下间距与助手过程和回答分隔；助手过程与回答继续使用完整可用宽度。
```

- [ ] **Step 5: 运行定向测试并确认 GREEN**

Run:

```bash
cd frontend
corepack pnpm exec vitest run packages/agent-chat/tests/opencode-timeline.test.ts
corepack pnpm --filter @test-agent/agent-chat typecheck
```

Expected: `opencode-timeline.test.ts` 全部 PASS；agent-chat typecheck 退出码为 0。

- [ ] **Step 6: 检查差异并提交实现**

Run:

```bash
git diff --check
git status --short
git add frontend/packages/agent-chat/tests/opencode-timeline.test.ts frontend/packages/agent-chat/src/opencode-like/styles/rows.css frontend/packages/agent-chat/README.md
git commit -m "前端：优化用户消息视觉分隔"
```

Expected: 只提交三项任务文件，提交成功。

---

### Task 2: 完整验证与交接

**Files:**
- Modify only when reusable evidence exists: `.agents/session-log.md`

**Interfaces:**
- Consumes: Task 1 已提交的用户消息样式契约与 CSS。
- Produces: 完整前端验证结果和按需的会话交接记录。

- [ ] **Step 1: 串行执行完整前端校验**

Run:

```bash
cd frontend
corepack pnpm lint
corepack pnpm typecheck
set -o pipefail
corepack pnpm exec vitest run --maxWorkers=1 2>&1 | tail -n 50
corepack pnpm build
```

Expected: lint、typecheck、Vitest 和 build 均退出码为 0；Vitest 汇总无失败测试。既有 CSS `@import` 或 chunk size 警告如仍出现，在交付说明中如实记录。

- [ ] **Step 2: 按自检清单复核影响面**

确认以下结果：

```text
- 只修改 agent-chat 用户消息样式、测试和稳定 README
- 未修改 generated SDK 或环境配置
- 未修改 API、RunEvent、DTO、数据库、安全或性能边界
- 两条样式契约分别覆盖浅灰背景与 12px 下间距
- README 描述与实现一致
```

- [ ] **Step 3: 复核 session-log 并完成最终状态检查**

重新阅读 `.agents/session-log.md`。仅当本次产生对后续开发者有复用价值的新结论时，追加一条包含 `Why / What / How / Result` 的记录并中文提交；纯视觉参数调整没有新坑时不追加机械记录。

Run:

```bash
git status --short
git log -4 --oneline
```

Expected: 本任务文件均已提交，工作区不包含本任务残留修改，未覆盖或暂存其他开发者文件。
