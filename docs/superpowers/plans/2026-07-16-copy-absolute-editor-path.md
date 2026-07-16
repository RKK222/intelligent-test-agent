# Copy Absolute Editor Path Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在文本编辑器底部同时提供“复制相对路径”和“复制绝对路径”两行操作。

**Architecture:** `AgentWorkbench` 将当前工作区根目录传给 `FigmaEditorArea`，再透传给 `WorkbenchFooter`。页脚保留现有相对路径复制链路，并在前端基于工作区根目录和文件相对路径计算绝对路径，不新增 API 或全局状态依赖。

**Tech Stack:** Vue 3、TypeScript 6、Vitest、Vue Test Utils、CSS

## Global Constraints

- 只修改与编辑器底部路径复制直接相关的最小范围。
- 两个复制操作纵向排列，保持现有 30px 底部状态栏高度。
- 绝对路径由当前工作区根目录与当前文件相对路径生成。
- 路径统一使用 `/` 分隔，并避免根目录尾部与文件路径头部产生重复 `/`。
- 不新增或修改 HTTP API、RunEvent SSE、数据库和后端代码。

---

### Task 1: 路径复制组件行为

**Files:**
- Modify: `frontend/apps/agent-web/tests/WorkbenchFooter.test.ts`
- Modify: `frontend/apps/agent-web/src/components/WorkbenchFooter.vue`

**Interfaces:**
- Consumes: `writePath?: string` 与新增 `workspaceRootPath?: string` props。
- Produces: `.ta-workbench-footer-copy-relative-path` 和 `.ta-workbench-footer-copy-absolute-path` 两个按钮，以及计算后的绝对路径复制行为。

- [x] **Step 1: Write the failing test**

新增组件测试，传入 `writePath: "src/components/WorkbenchFooter.vue"` 与 `workspaceRootPath: "/workspace/project/"`，断言两行按钮分别复制 `src/components/WorkbenchFooter.vue` 和 `/workspace/project/src/components/WorkbenchFooter.vue`；再覆盖 Windows 分隔符归一化。

- [x] **Step 2: Run test to verify it fails**

Run: `cd frontend && corepack pnpm test apps/agent-web/tests/WorkbenchFooter.test.ts`

Expected: FAIL，因为绝对路径 prop 和第二个复制按钮尚不存在。

- [x] **Step 3: Write minimal implementation**

在 `WorkbenchFooter.vue` 中新增 `workspaceRootPath` prop、绝对路径 computed、接收待复制文本的通用复制函数，以及两行按钮；沿用现有 clipboard 与 textarea fallback 行为。

- [x] **Step 4: Run test to verify it passes**

Run: `cd frontend && corepack pnpm test apps/agent-web/tests/WorkbenchFooter.test.ts`

Expected: PASS。

### Task 2: 工作区根目录透传与文档

**Files:**
- Modify: `frontend/apps/agent-web/src/components/FigmaEditorArea.vue`
- Modify: `frontend/apps/agent-web/src/components/AgentWorkbench.vue`
- Modify: `frontend/apps/agent-web/README.md`

**Interfaces:**
- Consumes: `selectedWorkspace.rootPath`。
- Produces: `workspaceRootPath?: string` 从工作台到编辑器页脚的单向 prop 链路。

- [x] **Step 1: Wire the workspace root path**

给 `FigmaEditorArea` 增加 `workspaceRootPath?: string`，传给内部 `WorkbenchFooter`；普通编辑器和 Diff 页脚均由 `AgentWorkbench` 传入 `selectedWorkspace?.rootPath`。

- [x] **Step 2: Document the stable behavior**

在 `frontend/apps/agent-web/README.md` 的工作台 UI 说明中记录编辑器底部可分别复制相对路径与绝对路径。

- [x] **Step 3: Verify focused and package checks**

Run:

```bash
cd frontend
corepack pnpm test apps/agent-web/tests/WorkbenchFooter.test.ts
corepack pnpm --filter @test-agent/agent-web typecheck
```

Expected: 两条命令均成功。

- [x] **Step 4: Review before commit**

回顾 `.agents/session-log.md` 与 `git diff --check`，只暂存本任务文件，使用中文提交信息：`前端：支持复制编辑器绝对路径`。
