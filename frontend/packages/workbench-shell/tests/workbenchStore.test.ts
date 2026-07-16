import { beforeEach, describe, expect, it } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { useWorkbenchStore } from "../src/workbenchStore";

describe("workbenchStore 编辑器 tab 加载状态", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it("updateTab 更新后台响应对应 tab 时不切换当前活动文件", () => {
    const store = useWorkbenchStore();
    store.openTab({
      id: "file:docs/a.md",
      path: "docs/a.md",
      title: "a.md",
      content: "",
      savedContent: "",
      loadState: "loading"
    });
    store.openTab({
      id: "file:docs/b.md",
      path: "docs/b.md",
      title: "b.md",
      content: "B",
      savedContent: "B",
      loadState: "loaded"
    });

    store.updateTab("docs/a.md", {
      content: "A",
      savedContent: "A",
      loadState: "loaded",
      loadError: undefined
    });

    expect(store.activePath).toBe("docs/b.md");
    expect(store.tabs.find((tab) => tab.path === "docs/a.md")).toMatchObject({
      content: "A",
      savedContent: "A",
      loadState: "loaded"
    });
  });

  it("重叠刷新时使用稳定快照身份而不是瞬时 loadState", () => {
    const store = useWorkbenchStore();
    expect(store.tabHasLoadedSnapshot(undefined)).toBe(false);
    expect(store.tabHasLoadedSnapshot({ loadState: undefined, hasLoadedSnapshot: undefined })).toBe(true);
    expect(store.tabHasLoadedSnapshot({ loadState: "loading", hasLoadedSnapshot: true })).toBe(true);
    expect(store.tabHasLoadedSnapshot({ loadState: "loading", hasLoadedSnapshot: false })).toBe(false);
  });

  it("只有用户内容更新递增修订代次，保存和后台 patch 不递增", () => {
    const store = useWorkbenchStore();
    store.openTab({
      id: "file:docs/revision.md",
      path: "docs/revision.md",
      title: "revision.md",
      content: "base",
      savedContent: "base"
    });

    store.updateTabContent("docs/revision.md", "edit-1");
    store.updateTabContent("docs/revision.md", "edit-2");
    expect(store.tabs[0]?.contentRevision).toBe(2);

    store.markTabSaved("docs/revision.md", "edit-2");
    store.updateTab("docs/revision.md", { content: "background", savedContent: "background" });
    expect(store.tabs[0]?.contentRevision).toBe(2);
  });
});
