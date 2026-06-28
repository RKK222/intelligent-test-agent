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
    additions: 15,
    deletions: 8,
    patch: `--- a/frontend/apps/agent-web/src/components/FigmaFileExplorer.vue
+++ b/frontend/apps/agent-web/src/components/FigmaFileExplorer.vue
 <script setup lang="ts">
 import { ref, computed, onMounted } from "vue";
 import { Folder, File, ChevronDown, ChevronRight, GitBranch } from "lucide-vue-next";
 import { useWorkbenchStore } from "@test-agent/workbench-shell";
 
 const props = defineProps<{
   workspaceId: string;
   apiBaseUrl: string;
 }>();
 
 const workbench = useWorkbenchStore();
 const workspaceExpanded = ref(true);
 const agentsExpanded = ref(true);
 
-const sidebarWidth = ref(260);
-const minWidth = ref(150);
-const activeTab = ref("files");
+const sidebarMinWidth = 200;
+const sidebarMaxWidth = 600;
+const activeTab = ref("changes");
+const isDragging = ref(false);
 
 function handleTabChange(tab: string) {
-  activeTab.value = tab;
+  if (tab === "changes") {
+    workbench.setSelectedDiffPath("frontend/apps/agent-web/src/components/FigmaFileExplorer.vue");
+  }
+  activeTab.value = tab;
 }
 
 onMounted(() => {
   console.log("FigmaFileExplorer mounted");
 });
 </script>
 
 <template>
   <div class="figma-fe-root">
     <div class="ta-icon-tabbar flex items-center border-b border-slate-800 bg-slate-950 px-2 py-1">
       <button
         type="button"
-        :class="['ta-icon-tab', activeTab === 'files' && 'is-active']"
-        @click="handleTabChange('files')"
+        :class="['ta-icon-tab', activeTab === 'explorer' && 'is-active']"
+        @click="handleTabChange('explorer')"
       >
         <Folder class="h-4 w-4" />
       </button>
       <button
         type="button"
         :class="['ta-icon-tab', activeTab === 'changes' && 'is-active']"
         @click="handleTabChange('changes')"
       >
         <GitBranch class="h-4 w-4" />
       </button>
     </div>
     
     <div class="figma-fe-body flex-1 overflow-auto">
       <div v-if="activeTab === 'changes'" class="h-full">
         <div class="changes-title px-3 py-2 text-[11px] uppercase font-semibold text-slate-500">Source Control</div>
         <div class="px-3 py-1 text-[12px] text-slate-300">Click a file below to edit and save:</div>
       </div>
       <div v-else class="workspace-files">
         <div class="flex items-center px-3 py-1.5 hover:bg-slate-800 cursor-pointer">
           <ChevronDown class="h-4 w-4 text-slate-400 mr-1" />
           <span class="text-[12px] font-semibold text-slate-200">应用工作空间</span>
         </div>
       </div>
     </div>
   </div>
 </template>
 
 <style scoped>
 .figma-fe-root {
   display: flex;
   flex-direction: column;
   height: 100%;
   background: #020617;
   border-right: 1px solid #1e293b;
 }
 .ta-icon-tab {
   height: 32px;
   padding: 0 12px;
   color: #64748b;
   border-bottom: 2px solid transparent;
 }
 .ta-icon-tab.is-active {
   color: #3b82f6;
   border-bottom-color: #3b82f6;
 }
 </style>`
  },
  {
    path: "frontend/packages/workbench-shell/src/workbenchStore.ts",
    status: "modified",
    additions: 12,
    deletions: 4,
    patch: `--- a/frontend/packages/workbench-shell/src/workbenchStore.ts
+++ b/frontend/packages/workbench-shell/src/workbenchStore.ts
 import { defineStore } from "pinia";
 import { ref, computed } from "vue";
 
 export interface EditorTab {
   path: string;
   content: string;
   savedContent: string;
   readonly?: boolean;
 }
 
 export const useWorkbenchStore = defineStore("workbench", () => {
   const tabs = ref<EditorTab[]>([]);
   const activePath = ref<string | undefined>(undefined);
   const selectedDiffPath = ref<string | undefined>(undefined);
-  const useMockTestData = ref(false);
-  
-  function toggleMock() {
-    useMockTestData.value = !useMockTestData.value;
-  }
+  const useMockTestData = ref(true);
+  const gitStatusMessage = ref("");
+  const changesCount = computed(() => mockVcsDiffFiles.length);
+
+  function toggleMockTestData() {
+    useMockTestData.value = !useMockTestData.value;
+  }
 
   function openTab(tab: EditorTab) {
     const exists = tabs.value.some((t) => t.path === tab.path);
     if (!exists) {
       tabs.value.push(tab);
     }
     activePath.value = tab.path;
   }
 
   function closeTab(path: string) {
     tabs.value = tabs.value.filter((t) => t.path !== path);
     if (activePath.value === path) {
       activePath.value = tabs.value[tabs.value.length - 1]?.path;
     }
   }
 
   return {
     tabs,
     activePath,
     selectedDiffPath,
     useMockTestData,
+    gitStatusMessage,
+    changesCount,
+    toggleMockTestData,
     openTab,
     closeTab
   };
 });`
  },
  {
    path: "frontend/apps/agent-web/src/components/GitChangesPanel.vue",
    status: "untracked",
    additions: 45,
    deletions: 0,
    patch: `--- /dev/null
+++ b/frontend/apps/agent-web/src/components/GitChangesPanel.vue
+<script setup lang="ts">
+import { ref, computed } from "vue";
+import { ChevronDown, Play, Plus, Check } from "lucide-vue-next";
+import { useWorkbenchStore } from "@test-agent/workbench-shell";
+
+const workbench = useWorkbenchStore();
+const commitMessage = ref("");
+const unstagedExpanded = ref(true);
+const stagedExpanded = ref(true);
+
+function handleCommit() {
+  if (!commitMessage.value.trim()) return;
+  console.log("Committing changes:", commitMessage.value);
+  commitMessage.value = "";
+}
+</script>
+
+<template>
+  <div class="git-changes-panel flex flex-col h-full bg-[#020617] text-slate-300">
+    <div class="panel-section border-b border-slate-800 p-3">
+      <div class="section-title flex items-center justify-between mb-2">
+        <span class="text-[11px] font-bold uppercase tracking-wider text-slate-500">Unstaged Changes</span>
+      </div>
+      <div class="file-list space-y-1">
+        <div class="file-item flex items-center justify-between p-1.5 rounded hover:bg-slate-900 cursor-pointer">
+          <span class="font-mono text-[12px]">FigmaFileExplorer.vue</span>
+          <span class="text-[11px] text-amber-500">M</span>
+        </div>
+      </div>
+    </div>
+    
+    <div class="commit-form p-3 border-t border-slate-800 mt-auto bg-slate-950">
+      <textarea 
+        v-model="commitMessage"
+        class="w-full bg-slate-900 border border-slate-800 rounded p-2 text-[12px] text-white focus:outline-none focus:border-blue-500"
+        placeholder="Commit message (Chinese)..."
+        rows="3"
+      ></textarea>
+      <button 
+        class="btn-commit w-full bg-blue-600 hover:bg-blue-700 text-white rounded py-2 mt-2 text-[12px] font-semibold"
+        @click="handleCommit"
+      >
+        Commit to main
+      </button>
+    </div>
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
 {
   "id": "public_agent",
-  "name": "Old Public Agent Name",
-  "description": "This is the original description of the public agent config.",
-  "version": "1.0.0",
+  "name": "Premium Public Agent (MIMO)",
+  "description": "This is the updated premium description of the public agent config.",
+  "version": "2.0.0",
   "parameters": {
     "temperature": 0.7,
     "maxTokens": 2048,
-    "stream": false
+    "stream": true
   },
   "system_prompt": "You are a professional coding assistant.",
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
 {
   "id": "workspace_agent",
-  "name": "Old Workspace Agent Name",
-  "description": "This is the original description of the workspace agent config.",
-  "version": "1.0.0",
+  "name": "Premium Workspace Agent (MIMO)",
+  "description": "This is the updated premium description of the workspace agent config.",
+  "version": "2.0.0",
   "parameters": {
     "temperature": 0.5,
     "maxTokens": 4096,
-    "stream": false
+    "stream": true
   },
   "system_prompt": "You are a professional test agent working on workspaces.",
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
    publicConfigLinuxServerId.value = null;
  }

  return {
    tabs,
    activePath,
    selectedDiffPath,
    publicWorktree,
    workspaceWorktree,
    publicConfigLinuxServerId,
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
