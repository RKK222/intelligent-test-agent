# 工作空间文件名搜索过滤修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复工作空间搜索面板因父目录命中关键字而展示文件名未命中文件的问题，并让服务端结果与本地回退保持文件名匹配语义一致。

**Architecture:** 保留 `workspace.search` 按完整相对路径搜索的后端能力，在 `packages/file-explorer` 展示边界对结果执行最终文件名过滤。`filterLoadedFiles` 与服务端结果共用一个文件名匹配函数，确保去除首尾空白、不区分大小写和空关键字规则完全一致。

**Tech Stack:** Vue 3、TypeScript 6、Vitest、Testing Library Vue、pnpm workspace。

## Global Constraints

- 只修改 `frontend/packages/file-explorer` 的搜索过滤、测试、README，以及本机 `.agents/session-log.huangzhenren.md`。
- 不修改当前工作区已有的 `frontend/apps/agent-web/src/components/AgentWorkbench.vue` 与 `frontend/apps/agent-web/src/components/BatchTestCaseGenerationDialog.vue` 未提交改动。
- 不修改 `workspace.search` WebSocket RPC、HTTP API、RunEvent、DTO、数据库、权限、安全模型或 OpenCode 源码。
- 搜索词使用 `trim().toLowerCase()` 标准化；空搜索词不返回结果；文件名使用同样的小写转换后执行 `includes`。
- 人工维护的新增或复杂逻辑使用中文注释说明边界；不新增依赖、不修改 `.env*` 或 generated SDK。
- 按测试先行顺序执行：先观察回归测试在旧实现上因路径误报失败，再写最小生产代码使其通过。

---

### Task 1: 在文件浏览包边界收口文件名搜索语义

**Files:**
- Modify: `frontend/packages/file-explorer/tests/FileExplorer.test.ts`
- Modify: `frontend/packages/file-explorer/tests/filterLoadedFiles.test.ts`
- Modify: `frontend/packages/file-explorer/src/filterLoadedFiles.ts`
- Modify: `frontend/packages/file-explorer/src/FileExplorer.vue`
- Modify: `frontend/packages/file-explorer/README.md`
- Modify: `.agents/session-log.huangzhenren.md`

**Interfaces:**
- Consumes: `FileSearchResult.name/path`、`FileTreeEntry.name/path/type`、`FileExplorerProps.searchResults/searchKeyword/searchLoading`。
- Produces: `fileNameIncludesKeyword(fileName: string, keyword: string): boolean`，供本地回退和 `FileExplorer` 的服务端结果展示过滤共用。
- Preserves: `FileExplorer` 的现有 props、emit、高亮、打开文件、加入对话和 loading/empty 状态接口。

- [ ] **Step 1: 增加服务端结果路径误报的组件失败测试**

在 `frontend/packages/file-explorer/tests/FileExplorer.test.ts` 的 `describe("FileExplorer", ...)` 中增加：

```ts
it("only renders server search results whose file name contains the keyword", () => {
  const view = render(FileExplorer, {
    props: {
      entriesByDirectory: {},
      expandedDirectories: new Set<string>(),
      changedFiles: [],
      activeTab: "search",
      searchKeyword: "需求",
      searchResults: [
        {
          path: "需求资料/贷款申请.md",
          name: "贷款申请.md",
          directory: "需求资料",
          size: 10
        },
        {
          path: "docs/需求说明.md",
          name: "需求说明.md",
          directory: "docs",
          size: 20
        }
      ]
    }
  });

  expect(view.queryByText("贷款申请.md")).toBeNull();
  expect(view.getByText("需求").closest("button")?.textContent).toContain("需求说明.md");
});
```

- [ ] **Step 2: 增加本地回退路径误报的单元失败测试**

将 `frontend/packages/file-explorer/tests/filterLoadedFiles.test.ts` 的首个用例改为明确的文件名匹配场景：

```ts
it("filters loaded files by file name instead of parent directory path", () => {
  const result = filterLoadedFiles(
    {
      "": [{ type: "directory", path: "需求资料", name: "需求资料" }],
      需求资料: [
        { type: "file", path: "需求资料/贷款申请.md", name: "贷款申请.md" },
        { type: "file", path: "需求资料/需求说明.md", name: "需求说明.md" }
      ]
    },
    "  需求  "
  );

  expect(result).toEqual([
    { type: "file", path: "需求资料/需求说明.md", name: "需求说明.md" }
  ]);
});
```

- [ ] **Step 3: 运行聚焦测试并确认旧实现按预期失败**

Run:

```bash
cd frontend
corepack pnpm exec vitest run packages/file-explorer/tests/FileExplorer.test.ts packages/file-explorer/tests/filterLoadedFiles.test.ts
```

Expected: 两个新增/修改用例失败；失败输出分别显示“贷款申请.md”仍存在，以及本地回退额外返回“贷款申请.md”。其它既有用例通过。

- [ ] **Step 4: 实现共用的文件名匹配函数并修正本地回退**

将 `frontend/packages/file-explorer/src/filterLoadedFiles.ts` 调整为：

```ts
import type { FileTreeEntry, WorkspaceViewEntry } from "@test-agent/shared-types";

/**
 * 工作区搜索面板只按文件名匹配；父目录命中不能把无关文件带入结果。
 */
export function fileNameIncludesKeyword(fileName: string, keyword: string): boolean {
  const normalized = keyword.trim().toLowerCase();
  return normalized.length > 0 && fileName.toLowerCase().includes(normalized);
}

export function filterLoadedFiles(entriesByDirectory: Record<string, FileTreeEntry[]>, keyword: string) {
  if (!keyword.trim()) {
    return [];
  }
  return Object.values(entriesByDirectory)
    .flat()
    .filter((entry) => entry.type === "file")
    // 工作区 view 会把引用节点一并加载到树中，本地搜索仍只能覆盖物理 workspace。
    .filter((entry) => (entry as Partial<WorkspaceViewEntry>).source !== "REFERENCE")
    .filter((entry) => fileNameIncludesKeyword(entry.name, keyword));
}
```

- [ ] **Step 5: 在组件展示边界过滤服务端搜索结果**

在 `frontend/packages/file-explorer/src/FileExplorer.vue` 中把导入改为：

```ts
import { fileNameIncludesKeyword, filterLoadedFiles } from "./filterLoadedFiles";
```

把 `displaySearchResults` 改为：

```ts
// 服务端路径搜索仍供其它业务复用；文件搜索面板在展示前最终收口为文件名匹配。
const displaySearchResults = computed(() =>
  (props.searchResults ?? localSearchResults.value)
    .filter((entry) => fileNameIncludesKeyword(entry.name, displayKeyword.value))
);
```

- [ ] **Step 6: 运行聚焦测试并确认修复转绿**

Run:

```bash
cd frontend
corepack pnpm exec vitest run packages/file-explorer/tests/FileExplorer.test.ts packages/file-explorer/tests/filterLoadedFiles.test.ts
```

Expected: 两个测试文件全部通过，输出无失败。

- [ ] **Step 7: 同步文件浏览包稳定说明**

在 `frontend/packages/file-explorer/README.md` 的搜索职责中明确：

```markdown
- 搜索可使用 app 层传入的服务端结果；`file-explorer` 在渲染前仍按当前关键字对 `name` 做不区分大小写的子串过滤，父目录路径命中不会展示文件名未命中的文件。app 未提供结果时，本地回退同样只过滤已加载物理工作区的文件名。
```

保留后端调用由 app 层发起、`file-explorer` 不直接调用后端以及不实现内容搜索 API 的既有边界。

- [ ] **Step 8: 执行包级和前端验证**

依次运行，避免共享构建缓存并发竞争：

```bash
cd frontend
corepack pnpm --filter @test-agent/file-explorer typecheck
corepack pnpm test
corepack pnpm typecheck
corepack pnpm build
```

Expected: `file-explorer` 类型检查、前端全量 Vitest、workspace 类型检查和生产构建全部退出码为 0；若存在既有警告，记录原始警告但不得把警告描述为失败。

- [ ] **Step 9: 更新本机会话日志并执行提交前检查**

在 `.agents/session-log.huangzhenren.md` 追加一条会话级记录，使用 `Why / What / How / Result` 说明根因、文件名边界过滤、红绿测试和最终验证，不写入其它提交者日志。

提交前执行：

```bash
git diff --check
git status --short
git diff -- frontend/packages/file-explorer .agents/session-log.huangzhenren.md
```

确认没有冲突标记，没有改动 `opencode-source/`、`.env*`、API、事件、数据库或当前工作区的两处既有未提交文件。

- [ ] **Step 10: 只提交本任务文件**

```bash
git add frontend/packages/file-explorer/src/filterLoadedFiles.ts \
  frontend/packages/file-explorer/src/FileExplorer.vue \
  frontend/packages/file-explorer/tests/FileExplorer.test.ts \
  frontend/packages/file-explorer/tests/filterLoadedFiles.test.ts \
  frontend/packages/file-explorer/README.md \
  .agents/session-log.huangzhenren.md \
  docs/superpowers/plans/2026-08-08-workspace-file-name-search-filter.md
git diff --cached --check
git diff --cached --name-only
git commit -m "修复工作空间文件名搜索误报"
```

Expected: 提交只包含上述本任务文件；`AgentWorkbench.vue` 与 `BatchTestCaseGenerationDialog.vue` 仍保持未暂存且内容不变。
