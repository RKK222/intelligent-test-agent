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

// 工作台级状态：打开的编辑器 tab、活动文件、选中的 Diff 文件
export const useWorkbenchStore = defineStore("workbench", () => {
  const tabs = ref<EditorTab[]>([]);
  const activePath = ref<string | undefined>(undefined);
  const selectedDiffPath = ref<string | undefined>(undefined);

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
  }

  return {
    tabs,
    activePath,
    selectedDiffPath,
    setActivePath,
    setSelectedDiffPath,
    openTab,
    closeTab,
    updateTabContent,
    markTabSaved,
    resetWorkspaceView
  };
});
