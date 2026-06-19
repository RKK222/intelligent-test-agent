import { create } from "zustand";

export type EditorTab = {
  id: string;
  path: string;
  title: string;
  content: string;
  savedContent: string;
  readonly?: boolean;
};

export type WorkbenchState = {
  activePath?: string;
  tabs: EditorTab[];
  selectedDiffPath?: string;
  setActivePath: (path?: string) => void;
  setSelectedDiffPath: (path?: string) => void;
  openTab: (tab: EditorTab) => void;
  closeTab: (path: string) => void;
  updateTabContent: (path: string, content: string) => void;
  markTabSaved: (path: string, content: string) => void;
};

export const useWorkbenchStore = create<WorkbenchState>((set) => ({
  tabs: [],
  setActivePath: (path) => set({ activePath: path }),
  setSelectedDiffPath: (path) => set({ selectedDiffPath: path }),
  openTab: (tab) =>
    set((state) => {
      const exists = state.tabs.some((item) => item.path === tab.path);
      return {
        activePath: tab.path,
        tabs: exists ? state.tabs.map((item) => (item.path === tab.path ? { ...item, ...tab } : item)) : [...state.tabs, tab]
      };
    }),
  closeTab: (path) =>
    set((state) => {
      const tabs = state.tabs.filter((item) => item.path !== path);
      return {
        tabs,
        activePath: state.activePath === path ? tabs.at(-1)?.path : state.activePath
      };
    }),
  updateTabContent: (path, content) =>
    set((state) => ({
      tabs: state.tabs.map((item) => (item.path === path ? { ...item, content } : item))
    })),
  markTabSaved: (path, content) =>
    set((state) => ({
      tabs: state.tabs.map((item) => (item.path === path ? { ...item, content, savedContent: content } : item))
    }))
}));
