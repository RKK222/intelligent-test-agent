# 删除主时间线对话头像实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删除 opencode-like 主时间线中的用户头像和智能体头像，并回收原头像占用的横向空间。

**Architecture:** 只修改 `@test-agent/agent-chat` 的主时间线行组件与共用行样式，保留现有消息投影、状态 class 和组件 props。测试直接渲染 `OpencodeTimeline`，从用户与智能体多种时间线状态验证头像节点不再存在。

**Tech Stack:** Vue 3、TypeScript 6、Vitest 4、Testing Library Vue、CSS

## Global Constraints

- 只修改 `frontend/packages/agent-chat` 主时间线、对应测试和包 README，不修改旧 `FigmaChatPanel.vue` 消息循环。
- 用户气泡、复制、上下文 chip、智能体过程顺序、折叠状态和状态文案保持不变。
- 不修改 API、RunEvent、DTO、数据库、后端、安全策略或环境配置。
- 生产代码修改前必须先运行新增断言并确认因头像仍存在而失败。

---

### Task 1: 删除主时间线角色头像

**Files:**
- Modify: `frontend/packages/agent-chat/tests/opencode-timeline.test.ts`
- Modify: `frontend/packages/agent-chat/src/opencode-like/components/rows/UserMessageRow.vue`
- Modify: `frontend/packages/agent-chat/src/opencode-like/components/rows/AssistantMessageFrame.vue`
- Modify: `frontend/packages/agent-chat/src/opencode-like/components/rows/WorkingStatusRow.vue`
- Modify: `frontend/packages/agent-chat/src/opencode-like/components/rows/ThinkingRow.vue`
- Modify: `frontend/packages/agent-chat/src/opencode-like/styles/rows.css`
- Modify: `frontend/packages/agent-chat/README.md`

**Interfaces:**
- Consumes: `OpencodeTimeline` 的既有 `state` prop，以及行组件现有 `message`、`continuation`、`showHeader` props。
- Produces: DOM 中不再出现 `.oc-user-message__avatar` 或 `.oc-assistant-frame__avatar`；其他组件接口不变。

- [ ] **Step 1: 先把现有头像断言改为无头像回归断言**

在 `frontend/packages/agent-chat/tests/opencode-timeline.test.ts` 中将初始思考、普通用户/助手组合、拆分 assistant、step-start 和重复 reasoning 场景的头像断言改为：

```ts
expect(container.querySelector(".oc-user-message__avatar")).toBeNull();
expect(container.querySelector(".oc-assistant-frame__avatar")).toBeNull();
```

不删除这些场景已有的正文、过程行数量、折叠状态与 `思考状态` 断言，确保此次变更只影响头像。

- [ ] **Step 2: 运行定向测试并确认 RED**

Run:

```bash
cd frontend
corepack pnpm exec vitest run packages/agent-chat/tests/opencode-timeline.test.ts
```

Expected: FAIL；失败信息显示 `.oc-user-message__avatar` 或 `.oc-assistant-frame__avatar` 仍是 `HTMLDivElement`，证明回归断言命中当前行为。

- [ ] **Step 3: 删除用户头像节点与图标依赖**

把 `UserMessageRow.vue` 的图标导入收敛为：

```ts
import { FileText, Scissors } from "lucide-vue-next";
```

删除模板末尾以下节点：

```vue
<div class="oc-user-message__avatar" aria-hidden="true">
  <User class="oc-user-message__avatar-icon" />
</div>
```

- [ ] **Step 4: 删除三类智能体头像节点与图标依赖**

在 `AssistantMessageFrame.vue` 和 `WorkingStatusRow.vue` 中删除 `Bot` import，并删除各自模板中的：

```vue
<div v-if="showHeader" class="oc-assistant-frame__avatar" aria-hidden="true">
  <Bot class="oc-assistant-frame__avatar-icon" />
</div>
```

在 `ThinkingRow.vue` 中删除仅包含 `Bot` import 的 `<script setup>`，并删除模板中的：

```vue
<div class="oc-assistant-frame__avatar" aria-hidden="true">
  <Bot class="oc-assistant-frame__avatar-icon" />
</div>
```

保留 `showHeader`、`continuation` props 和现有状态 class，避免改变调用接口与时间线投影。

- [ ] **Step 5: 清理头像样式并回收空白**

在 `rows.css` 中：

```css
.oc-user-message {
  display: flex;
  justify-content: flex-end;
  margin: 4px 0 6px;
  align-items: flex-start;
}
```

删除 `.oc-user-message__avatar`、`.oc-user-message__avatar-icon`、`.oc-assistant-frame__avatar` 和 `.oc-assistant-frame__avatar-icon` 规则；从 `.oc-assistant-frame` 删除 `--oc-avatar-size` 与 `gap`，从 `.oc-assistant-frame.has-header` 删除 `gap`，并把 continuation/headerless 规则改为不保留左侧 padding：

```css
.oc-assistant-frame.is-continuation,
.oc-assistant-frame.is-headerless {
  display: flex;
  flex-direction: row;
  margin-top: 0;
  max-width: 100%;
}
```

- [ ] **Step 6: 同步包 README**

把 `frontend/packages/agent-chat/README.md` 第一条职责中的头像描述改为：

```markdown
用户与助手侧均不展示头像或名称/时间行，以极简消息来源布局匹配主时间线；用户问题仍保持右对齐，助手过程与回答使用完整可用宽度。
```

- [ ] **Step 7: 运行定向测试并确认 GREEN**

Run:

```bash
cd frontend
corepack pnpm exec vitest run packages/agent-chat/tests/opencode-timeline.test.ts
corepack pnpm --filter @test-agent/agent-chat typecheck
```

Expected: `opencode-timeline.test.ts` 全部 PASS，agent-chat typecheck 退出码为 0。

- [ ] **Step 8: 检查差异并提交实现**

Run:

```bash
git diff --check
git diff -- frontend/packages/agent-chat
git status --short
git add frontend/packages/agent-chat
git commit -m "前端：删除对话用户和智能体头像"
```

Expected: 只暂存本任务相关的 agent-chat 代码、测试和 README；提交成功。

---

### Task 2: 完整验证与会话交接

**Files:**
- Modify if reusable evidence exists: `.agents/session-log.md`

**Interfaces:**
- Consumes: Task 1 已提交的无头像主时间线。
- Produces: 前端完整校验证据，以及按需记录的会话级交接信息。

- [ ] **Step 1: 执行前端完整校验**

Run:

```bash
cd frontend
corepack pnpm lint
corepack pnpm typecheck
corepack pnpm test -- --maxWorkers=1
corepack pnpm build
```

Expected: 四条命令均退出码为 0；Vitest 无失败测试；build 成功。已有且不影响退出码的 CSS `@import` 或 chunk size 警告如仍出现，应在交付说明中如实记录。

- [ ] **Step 2: 按自检清单核对影响面**

逐项确认：

```text
- 只改 agent-chat 主时间线、测试和稳定 README
- 未修改 generated SDK 或环境配置
- 未修改 API、RunEvent、DTO、数据库或安全边界
- 用户与智能体头像节点均由自动化测试覆盖为不存在
- README 描述与实现一致
```

- [ ] **Step 3: 判断并更新会话记录**

重新阅读 `.agents/session-log.md`。仅当本次产生对后续开发者有复用价值的新结论时，以单条 `Why / What / How / Result` 记录；如果只是按既定主路径删除头像且无新坑，不为机械变更追加日志。

- [ ] **Step 4: 提交必要的交接补充**

如果 Step 3 修改了 `.agents/session-log.md`：

```bash
git diff --check
git add .agents/session-log.md
git commit -m "文档：记录对话头像调整结果"
```

如果未修改会话记录，不创建空提交。

- [ ] **Step 5: 最终检查提交与工作区**

Run:

```bash
git status --short
git log -3 --oneline
```

Expected: 本任务文件均已提交；不覆盖或暂存其他开发者文件；最终回复列出代码、测试、文档、影响面、风险与 session-log 判断。
