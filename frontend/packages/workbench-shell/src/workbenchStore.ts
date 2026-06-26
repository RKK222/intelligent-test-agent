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
  /** 实时追踪打开的 agent 改动预览 tab：只读、内容随 diff 事件刷新、不可保存。 */
  livePreview?: boolean;
};

// 预定义测试数据供 UI 演示和 diff 效果展示
export const mockVcsDiffFiles: RunDiffFile[] = [
  {
    path: "frontend/apps/agent-web/src/components/FigmaFileExplorer.vue",
    status: "modified",
    additions: 12,
    deletions: 5,
    patch: `--- a/frontend/apps/agent-web/src/components/FigmaFileExplorer.vue
+++ b/frontend/apps/agent-web/src/components/FigmaFileExplorer.vue
@@ -60,6 +60,12 @@
 let dragStartY = 0;
 let dragStartHeight = 0;
 
+// MIMO Test Agent Layout Config
+const sidebarMinWidth = 200;
+const sidebarMaxWidth = 600;
+const resizableDividerClass = "figma-files-resize-handle";
+
 const workspaceHeight = ref(300);
-const activeTab = ref("files");
+const activeTab = ref("changes");`
  },
  {
    path: "frontend/packages/workbench-shell/src/workbenchStore.ts",
    status: "modified",
    additions: 8,
    deletions: 2,
    patch: `--- a/frontend/packages/workbench-shell/src/workbenchStore.ts
+++ b/frontend/packages/workbench-shell/src/workbenchStore.ts
@@ -17,2 +17,8 @@
 export const useWorkbenchStore = defineStore("workbench", () => {
   const tabs = ref<EditorTab[]>([]);
+  // Mock VCS Diff Data for UI presentation
+  const useMockTestData = ref(false);
+  const toggleMockTestData = () => {
+    useMockTestData.value = !useMockTestData.value;
+  };
   const activePath = ref<string | undefined>(undefined);`
  },
  {
    path: "frontend/apps/agent-web/src/components/GitChangesPanel.vue",
    status: "untracked",
    additions: 831,
    deletions: 0,
    patch: `--- /dev/null
+++ b/frontend/apps/agent-web/src/components/GitChangesPanel.vue
@@ -0,0 +1,5 @@
+<template>
+  <div class="git-changes-panel">
+    <!-- Premium Git Panel implementation -->
+  </div>
+</template>`
  }
];

export interface MockAgentConfigDiffFile {
  path: string;
  status: string;
  staged: boolean;
  patch: string;
}

export const mockPublicAgentDiffs: MockAgentConfigDiffFile[] = [
  {
    path: "opencode/agents/public_agent_test.json",
    status: "modified",
    staged: false,
    patch: `--- a/opencode/agents/public_agent_test.json
+++ b/opencode/agents/public_agent_test.json
@@ -2,4 +2,4 @@
 {
   "id": "public_agent",
-  "name": "Old Public Agent",
+  "name": "Premium Public Agent (MIMO)",
   "enabled": true
 }`
  }
];

export const mockWorkspaceAgentDiffs: MockAgentConfigDiffFile[] = [
  {
    path: ".opencode/agents/workspace_agent_test.json",
    status: "modified",
    staged: false,
    patch: `--- a/.opencode/agents/workspace_agent_test.json
+++ b/.opencode/agents/workspace_agent_test.json
@@ -2,4 +2,4 @@
 {
   "id": "workspace_agent",
-  "name": "Old Workspace Agent",
+  "name": "Premium Workspace Agent (MIMO)",
   "enabled": true
 }`
  }
];

// 工作台级状态：打开的编辑器 tab、活动文件、选中的 Diff 文件
export const useWorkbenchStore = defineStore("workbench", () => {
  const tabs = ref<EditorTab[]>([]);
  const activePath = ref<string | undefined>(undefined);
  const selectedDiffPath = ref<string | undefined>(undefined);
  const publicWorktree = ref<AgentConfigWorktree | null>(null);
  const workspaceWorktree = ref<AgentConfigWorktree | null>(null);
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
    tabs.value = tabs.value.map((item) => (item.path === path ? { ...item, content } : item));
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
    workspaceWorktree.value = null;
  }

  return {
    tabs,
    activePath,
    selectedDiffPath,
    publicWorktree,
    workspaceWorktree,
    useMockTestData,
    setActivePath,
    setSelectedDiffPath,
    openTab,
    closeTab,
    updateTabContent,
    markTabSaved,
    resetWorkspaceView
  };
});
