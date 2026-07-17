import type { AgentConfigWorktree, RunDiffFile } from "@test-agent/shared-types";
import { defineStore } from "pinia";
import { ref } from "vue";

export type EditorTab = {
  id: string;
  path: string;
  title: string;
  content: string;
  savedContent: string;
  readonly?: boolean;
  loadState?: "loading" | "loaded" | "error";
  loadError?: string;
  /** 已取得可用文件快照（包含合法空文件），用于区分首次加载与后台刷新。 */
  hasLoadedSnapshot?: boolean;
  /** 用户内容修订代次；保存或后台同步不递增，用于识别读取期间发生过的编辑。 */
  contentRevision?: number;
  /** 实时追踪打开的 agent 改动预览 tab：只读、内容随 diff 事件刷新、不可保存。 */
  livePreview?: boolean;
};

/** 兼容旧 tab，并优先使用不会被瞬时 loading 覆盖的快照身份。 */
function editorTabHasLoadedSnapshot(
  tab: Pick<EditorTab, "loadState" | "hasLoadedSnapshot"> | undefined
): boolean {
  if (!tab) {
    return false;
  }
  if (tab.hasLoadedSnapshot !== undefined) {
    return tab.hasLoadedSnapshot;
  }
  return tab.loadState === undefined || tab.loadState === "loaded";
}

// 预定义测试数据供 UI 演示和 diff 效果展示，路径模拟真实应用工作区，而不是平台自身源码。
export const mockVcsDiffFiles: RunDiffFile[] = [
  {
    path: "src/App.vue",
    status: "modified",
    additions: 15,
    deletions: 8,
    patch: `--- a/src/App.vue
+++ b/src/App.vue
 <script setup lang="ts">
-import { ref } from "vue";
+import { computed, ref } from "vue";
 import { RouterView } from "vue-router";

 const appName = ref("Payment Test Workspace");
+const title = computed(() => appName.value.toUpperCase());
 </script>

 <template>
   <main class="app-shell">
-    <h1>{{ appName }}</h1>
+    <h1>{{ title }}</h1>
     <RouterView />
   </main>
 </template>`
  },
  {
    path: "tests/payment-flow.spec.ts",
    status: "modified",
    additions: 12,
    deletions: 4,
    patch: `--- a/tests/payment-flow.spec.ts
+++ b/tests/payment-flow.spec.ts
 import { test, expect } from "@playwright/test";
 
 test("creates a payment order", async ({ page }) => {
-  await page.goto("/checkout");
-  await expect(page.getByText("Success")).toBeVisible();
+  await page.goto("/payments/new");
+  await page.getByLabel("Amount").fill("100");
+  await page.getByRole("button", { name: "Submit" }).click();
+  await expect(page.getByText("Payment submitted")).toBeVisible();
 });`
  }
];

export interface MockAgentConfigDiffFile {
  path: string;
  status: string;
  staged: boolean;
  patch: string;
}

export const mockPublicAgentDiffs: MockAgentConfigDiffFile[] = [];

export const mockWorkspaceAgentDiffs: MockAgentConfigDiffFile[] = [
  {
    path: "agents/payment-test.md",
    status: "modified",
    staged: false,
    patch: `--- a/agents/payment-test.md
+++ b/agents/payment-test.md
 ---
 description: Handles payment-domain test design and execution tasks.
-mode: primary
+mode: all
 temperature: 0.2
 tools:
   write: true
   edit: true
   bash: true
 ---

-You are responsible for payment test assistance.
+You are responsible for payment test assistance. Read relevant skills before drafting or editing test assets.`
  },
  {
    path: "skills/payment-case-design/SKILL.md",
    status: "untracked",
    staged: false,
    patch: `--- /dev/null
+++ b/skills/payment-case-design/SKILL.md
+---
+name: payment-case-design
+description: Generate and review payment-domain test cases for this application.
+---
+
+# Payment Case Design
+
+## Instructions
+
+1. Read the application-specific requirements and payment rules.
+2. Identify payment scenarios, boundary conditions, and failure paths.
+3. Generate test cases with clear preconditions, steps, data, and expected results.
+
+## Resources
+
+- rules/: application-specific testing rules.
+- templates/: reusable output templates.`
  }
];

// 工作台级状态：打开的编辑器 tab、活动文件、选中的 Diff 文件
export const useWorkbenchStore = defineStore("workbench", () => {
  const tabs = ref<EditorTab[]>([]);
  const activePath = ref<string | undefined>(undefined);
  const selectedDiffPath = ref<string | undefined>(undefined);
  const publicWorktree = ref<AgentConfigWorktree | null>(null);
  const publicConfigLinuxServerId = ref<string | null>(null);
  const useMockTestData = ref(false);

  function setActivePath(path?: string) {
    activePath.value = path;
  }

  function setSelectedDiffPath(path?: string) {
    selectedDiffPath.value = path;
  }

  function openTab(tab: EditorTab) {
    const exists = tabs.value.some((item) => item.path === tab.path);
    tabs.value = exists
      ? tabs.value.map((item) => (item.path === tab.path ? { ...item, ...tab } : item))
      : [...tabs.value, tab];
    activePath.value = tab.path;
  }

  function closeTab(path: string) {
    tabs.value = tabs.value.filter((item) => item.path !== path);
    if (activePath.value === path) {
      activePath.value = tabs.value.at(-1)?.path;
    }
  }

  function updateTabContent(path: string, content: string) {
    tabs.value = tabs.value.map((item) => (
      item.path === path
        ? { ...item, content, contentRevision: (item.contentRevision ?? 0) + 1 }
        : item
    ));
  }

  /** 后台响应只更新它所属的 tab，不能因此抢回当前编辑器焦点。 */
  function updateTab(path: string, patch: Partial<Omit<EditorTab, "id" | "path">>) {
    tabs.value = tabs.value.map((item) => (item.path === path ? { ...item, ...patch } : item));
  }

  /**
   * 更新工作区文件重命名后的 tab 路径和标题，保留当前内容与 dirty 状态。
   */
  function renameTab(path: string, nextPath: string, title: string) {
    tabs.value = tabs.value.map((item) =>
      item.path === path
        ? {
            ...item,
            id: item.id.startsWith("file:")
              ? `file:${nextPath}`
              : item.id.startsWith("live:")
                ? `live:${nextPath}`
                : item.id,
            path: nextPath,
            title
          }
        : item
    );
    if (activePath.value === path) {
      activePath.value = nextPath;
    }
  }

  function markTabSaved(path: string, content: string) {
    tabs.value = tabs.value.map((item) =>
      item.path === path ? { ...item, content, savedContent: content } : item
    );
  }

  function resetWorkspaceView() {
    // 切换 Workspace 时清掉与旧根目录绑定的编辑器与 Diff 选择，避免展示过期文件路径。
    tabs.value = [];
    activePath.value = undefined;
    selectedDiffPath.value = undefined;
    publicWorktree.value = null;
    publicConfigLinuxServerId.value = null;
  }

  return {
    tabs,
    activePath,
    selectedDiffPath,
    publicWorktree,
    publicConfigLinuxServerId,
    useMockTestData,
    setActivePath,
    setSelectedDiffPath,
    openTab,
    closeTab,
    tabHasLoadedSnapshot: editorTabHasLoadedSnapshot,
    updateTab,
    updateTabContent,
    renameTab,
    markTabSaved,
    resetWorkspaceView
  };
});
