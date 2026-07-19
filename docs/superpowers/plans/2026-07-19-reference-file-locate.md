# Reference File Locate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让编辑器中的只读引用文件能够通过“定位到当前文件”或双击标签精确展开左侧组合文件树并滚动到实际引用节点。

**Architecture:** 保留现有 `tab.path` 事件协议和普通工作区路径定位；引用 tab 通过已经保存的稳定节点 ID，在当前“父目录 ID → 子节点”缓存中反向恢复祖先链，再从根到叶展开。树关系计算放在 `workspaceViewState.ts` 纯函数中，`AgentWorkbench.vue` 只负责选择定位策略、加载目录和滚动。

**Tech Stack:** Vue 3、TypeScript 6、Pinia、Vitest 4、pnpm。

## Global Constraints

- 只修改引用文件定位直接相关的前端代码、测试、稳定文档和本机 session log。
- 不修改 backend、HTTP API、RunEvent、数据库、OpenCode manager、generated SDK 或环境配置。
- 普通工作区文件继续使用现有相对路径定位；引用文件不得按展示路径猜测来源。
- 合并引用、非合并引用和同名工作区/引用节点必须按稳定节点 ID 精确定位。
- 保留工作区中现有 Mermaid 未提交改动，不暂存、不覆盖。
- 在当前 `main` 工作区实施，不新建分支或 worktree。

---

### Task 1: 用稳定节点 ID 计算引用文件祖先目录

**Files:**
- Modify: `frontend/apps/agent-web/tests/workspaceViewState.test.ts`
- Modify: `frontend/apps/agent-web/src/components/workspaceViewState.ts`

**Interfaces:**
- Consumes: `entriesByDirectory: Readonly<Record<string, readonly (Pick<FileTreeEntry, "type"> & { id?: string })[]>>`，键为父目录稳定 ID，空字符串为组合树根。
- Produces: `workspaceViewAncestorDirectoryIds(targetNodeId, entriesByDirectory): string[] | undefined`，成功时返回从根到叶的祖先目录稳定 ID；目标不存在、父链不完整或循环时返回 `undefined`。

- [ ] **Step 1: Write the failing tests**

在 `workspaceViewState.test.ts` 导入 `workspaceViewAncestorDirectoryIds`，增加以下用例；fixture 只保留 `id/type/name/path` 等必要字段：

```ts
it("restores merged reference ancestors from stable node ids", () => {
  const entries = {
    "": [{ id: "mixed:docs", type: "directory" }],
    "mixed:docs": [{ id: "reference:assets:docs/guide.md", type: "file" }]
  };

  expect(workspaceViewAncestorDirectoryIds("reference:assets:docs/guide.md", entries)).toEqual([
    "mixed:docs"
  ]);
});

it("restores alias and nested directories for non-merged references", () => {
  const entries = {
    "": [{ id: "reference-root:assets", type: "directory" }],
    "reference-root:assets": [{ id: "reference:assets:docs", type: "directory" }],
    "reference:assets:docs": [{ id: "reference:assets:docs/guide.md", type: "file" }]
  };

  expect(workspaceViewAncestorDirectoryIds("reference:assets:docs/guide.md", entries)).toEqual([
    "reference-root:assets",
    "reference:assets:docs"
  ]);
});

it("does not confuse same-name workspace and reference nodes", () => {
  const entries = {
    "": [
      { id: "workspace:docs", type: "directory" },
      { id: "reference-root:assets", type: "directory" }
    ],
    "workspace:docs": [{ id: "workspace:docs/guide.md", type: "file" }],
    "reference-root:assets": [{ id: "reference:assets:docs", type: "directory" }],
    "reference:assets:docs": [{ id: "reference:assets:docs/guide.md", type: "file" }]
  };

  expect(workspaceViewAncestorDirectoryIds("reference:assets:docs/guide.md", entries)).toEqual([
    "reference-root:assets",
    "reference:assets:docs"
  ]);
});

it("rejects missing, incomplete and cyclic parent chains", () => {
  expect(workspaceViewAncestorDirectoryIds("missing", { "": [] })).toBeUndefined();
  expect(workspaceViewAncestorDirectoryIds("reference:file", {
    "orphan": [{ id: "reference:file", type: "file" }]
  })).toBeUndefined();
  expect(workspaceViewAncestorDirectoryIds("reference:file", {
    "loop-a": [{ id: "reference:file", type: "file" }, { id: "loop-b", type: "directory" }],
    "loop-b": [{ id: "loop-a", type: "directory" }]
  })).toBeUndefined();
});
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
cd frontend && corepack pnpm vitest run apps/agent-web/tests/workspaceViewState.test.ts
```

Expected: FAIL because `workspaceViewAncestorDirectoryIds` is not exported.

- [ ] **Step 3: Implement the minimal pure helper**

在 `workspaceViewState.ts` 增加中文注释与实现：

```ts
type WorkspaceViewIdentityEntry = Pick<FileTreeEntry, "type"> & { id?: string };

/** 按当前已加载的稳定节点关系反向恢复祖先；链路异常时不按展示路径猜测。 */
export function workspaceViewAncestorDirectoryIds(
  targetNodeId: string,
  entriesByDirectory: Readonly<Record<string, readonly WorkspaceViewIdentityEntry[]>>
): string[] | undefined {
  if (!targetNodeId) return undefined;
  const parentByNodeId = new Map<string, string>();
  const directoryIds = new Set<string>();
  for (const [parentId, entries] of Object.entries(entriesByDirectory)) {
    for (const entry of entries) {
      if (!entry.id) continue;
      parentByNodeId.set(entry.id, parentId);
      if (entry.type === "directory") directoryIds.add(entry.id);
    }
  }
  if (!parentByNodeId.has(targetNodeId)) return undefined;

  const ancestors: string[] = [];
  const visited = new Set([targetNodeId]);
  let current = targetNodeId;
  while (parentByNodeId.has(current)) {
    const parentId = parentByNodeId.get(current)!;
    if (parentId === "") return ancestors.reverse();
    if (visited.has(parentId) || !directoryIds.has(parentId)) return undefined;
    visited.add(parentId);
    ancestors.push(parentId);
    current = parentId;
  }
  return undefined;
}
```

- [ ] **Step 4: Run focused tests and verify GREEN**

Run:

```bash
cd frontend && corepack pnpm vitest run apps/agent-web/tests/workspaceViewState.test.ts
```

Expected: all `workspace view state` tests PASS.

- [ ] **Step 5: Commit the pure helper and tests**

```bash
git add frontend/apps/agent-web/src/components/workspaceViewState.ts frontend/apps/agent-web/tests/workspaceViewState.test.ts
git diff --cached --check
git commit -m "修复：支持解析引用文件树祖先节点"
```

---

### Task 2: 将编辑器定位动作接入引用节点展开

**Files:**
- Modify: `frontend/apps/agent-web/tests/workspaceViewState.test.ts`
- Modify: `frontend/apps/agent-web/src/components/AgentWorkbench.vue`
- Modify: `frontend/apps/agent-web/README.md`
- Modify: `frontend/packages/file-explorer/README.md`

**Interfaces:**
- Consumes: Task 1 的 `workspaceViewAncestorDirectoryIds(...)`、现有 `workspaceViewNodeIdByTabPath`、`workspaceViewDirectoryById`、`entriesByDirectory`、`loadDirectory` 与 `isReferenceFilePath`。
- Produces: `expandWorkspaceViewNodeToFile(tabPath: string): Promise<boolean>`；`handleLocateFile(path: string): Promise<void>` 在目录展开完成后统一滚动。

- [ ] **Step 1: Write the failing integration source assertion**

在 `workspaceViewState.test.ts` 导入 `AgentWorkbench.vue?raw`，增加：

```ts
it("routes reference tab location through stable node expansion before scrolling", () => {
  expect(agentWorkbenchSource).toMatch(
    /if \(isReferenceFilePath\(path\)\) \{\s*await expandWorkspaceViewNodeToFile\(path\);\s*\} else \{\s*await expandPathToFile\(path\);/
  );
  expect(agentWorkbenchSource).toMatch(
    /async function expandWorkspaceViewNodeToFile\(tabPath: string\)[\s\S]*workspaceViewAncestorDirectoryIds\(nodeId, entriesByDirectory\.value\)/
  );
});
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
cd frontend && corepack pnpm vitest run apps/agent-web/tests/workspaceViewState.test.ts
```

Expected: FAIL because `AgentWorkbench.vue` has no stable-node locate branch.

- [ ] **Step 3: Implement stable-node expansion and await it before scrolling**

在 `AgentWorkbench.vue` 的 `workspaceViewState` imports 中加入 `workspaceViewAncestorDirectoryIds`，并在 `expandPathToFile` 后增加：

```ts
/** 引用 tab 使用精确叶子节点反向展开，避免合并路径或同名文件定位到工作区副本。 */
async function expandWorkspaceViewNodeToFile(tabPath: string): Promise<boolean> {
  const nodeId = workspaceViewNodeIdByTabPath.get(tabPath);
  if (!nodeId) return false;
  const ancestorIds = workspaceViewAncestorDirectoryIds(nodeId, entriesByDirectory.value);
  if (!ancestorIds) return false;

  const next = new Set(expandedDirectories.value);
  for (const ancestorId of ancestorIds) {
    const target = workspaceViewDirectoryById.get(ancestorId);
    if (!target) return false;
    next.add(ancestorId);
    expandedDirectories.value = new Set(next);
    if (entriesByDirectory.value[ancestorId] === undefined) {
      await loadDirectory(target);
    }
  }
  expandedDirectories.value = next;
  return true;
}
```

把定位处理改为：

```ts
// 编辑器定位：引用文件按稳定节点展开，普通文件沿用相对路径；完成后再滚动。
async function handleLocateFile(path: string) {
  if (!path) return;
  workbench.setActivePath(path);
  if (isReferenceFilePath(path)) {
    await expandWorkspaceViewNodeToFile(path);
  } else {
    await expandPathToFile(path);
  }
  await nextTick();
  scrollToActiveFileTreeRow();
  setTimeout(scrollToActiveFileTreeRow, 100);
  setTimeout(scrollToActiveFileTreeRow, 300);
}
```

- [ ] **Step 4: Update stable documentation**

在 `frontend/apps/agent-web/README.md` 的组合文件树说明中补充：编辑器页脚定位按钮和标签双击对引用文件使用 tab 到稳定节点 ID 的映射，先展开祖先再高亮滚动，不会在同名冲突时定位到工作区文件。

在 `frontend/packages/file-explorer/README.md` 的稳定节点身份说明中补充：app 层定位已打开的引用文件时应传稳定节点 ID 作为 activePath，并按父子缓存展开祖先；本包不解析引用展示路径。

- [ ] **Step 5: Run focused and package verification**

Run:

```bash
cd frontend && corepack pnpm vitest run apps/agent-web/tests/workspaceViewState.test.ts apps/agent-web/tests/FigmaEditorArea.test.ts apps/agent-web/tests/WorkbenchFooter.test.ts
corepack pnpm --filter @test-agent/agent-web typecheck
corepack pnpm --filter @test-agent/agent-web lint
```

Expected: all focused tests PASS; typecheck and lint exit 0.

- [ ] **Step 6: Commit integration and documentation**

```bash
git add frontend/apps/agent-web/src/components/AgentWorkbench.vue frontend/apps/agent-web/tests/workspaceViewState.test.ts frontend/apps/agent-web/README.md frontend/packages/file-explorer/README.md
git diff --cached --check
git commit -m "修复：引用文件可定位到文件树当前节点"
```

---

### Task 3: 全量验证、会话记录与交付提交

**Files:**
- Modify: `.agents/session-log.huangzhenren.md`

**Interfaces:**
- Consumes: Task 1–2 的提交和仓库自检规范。
- Produces: 可追溯的验证记录；不暂存 Mermaid 或其它并发改动。

- [ ] **Step 1: Run full frontend verification**

```bash
cd frontend
corepack pnpm test
corepack pnpm typecheck
corepack pnpm lint
corepack pnpm build
cd ..
git diff --check
```

Expected: all commands exit 0；若全量基线被与本功能无关的现有改动阻断，记录准确失败命令和首个错误，不扩大范围修改。

- [ ] **Step 2: Review all recent session logs and inspect scope**

```bash
for file in $(rg --files .agents | rg 'session-log.*\.md$' | sort); do tail -n 120 "$file"; done
git status --short --branch
git log --oneline --decorate -8
```

Expected: no conflict markers; this feature does not stage or overwrite unrelated Mermaid changes.

- [ ] **Step 3: Record the durable result**

在 `.agents/session-log.huangzhenren.md` 顶部追加一条 `Why / What / How / Result / Verification` 记录，说明根因、稳定节点祖先链方案、测试结果、文档同步，以及未变更 API/事件/数据库/安全/环境配置。

- [ ] **Step 4: Commit only the session log**

```bash
git add .agents/session-log.huangzhenren.md
git diff --cached --check
git commit -m "文档：记录引用文件定位修复"
```

- [ ] **Step 5: Final scope audit**

```bash
git status --short --branch
git show --stat --oneline HEAD~3..HEAD
git diff --cached --name-only
```

Expected: no staged files; unrelated working-tree files remain untouched and unstaged.
