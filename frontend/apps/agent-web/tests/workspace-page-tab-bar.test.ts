import { fireEvent, render } from "@testing-library/vue";
import { describe, expect, it } from "vitest";
import WorkspacePageTabBar from "../src/components/WorkspacePageTabBar.vue";
import { workspacePageTab } from "../src/components/workspace-page-tabs";

const tabs = ["toolbox", "memories", "hub"].map((id) => workspacePageTab(id as "toolbox" | "memories" | "hub"));

describe("WorkspacePageTabBar", () => {
  it("activates and closes pages through accessible tab controls", async () => {
    const view = render(WorkspacePageTabBar, {
      props: { tabs, activeId: "memories" }
    });

    expect(view.getByRole("tab", { name: "记忆" }).getAttribute("aria-selected")).toBe("true");
    await fireEvent.click(view.getByRole("tab", { name: "工具箱" }));
    await fireEvent.click(view.getByRole("button", { name: "关闭 记忆" }));

    expect(view.emitted().activate?.[0]).toEqual(["toolbox"]);
    expect(view.emitted().close?.[0]).toEqual(["memories", "current"]);
  });

  it("offers the complete close menu for the context tab", async () => {
    const view = render(WorkspacePageTabBar, {
      props: { tabs, activeId: "toolbox" }
    });

    await fireEvent.contextMenu(view.getByRole("tab", { name: "记忆" }), { clientX: 100, clientY: 80 });
    expect(view.getByRole("menu", { name: "记忆 Tab 操作" }).hidden).toBe(false);
    expect((view.getByRole("menuitem", { name: "关闭当前" }) as HTMLButtonElement).disabled).toBe(false);
    expect((view.getByRole("menuitem", { name: "关闭其他" }) as HTMLButtonElement).disabled).toBe(false);
    expect((view.getByRole("menuitem", { name: "关闭左侧" }) as HTMLButtonElement).disabled).toBe(false);
    expect((view.getByRole("menuitem", { name: "关闭右侧" }) as HTMLButtonElement).disabled).toBe(false);
    expect((view.getByRole("menuitem", { name: "关闭全部" }) as HTMLButtonElement).disabled).toBe(false);

    await fireEvent.click(view.getByRole("menuitem", { name: "关闭其他" }));
    expect(view.emitted().close?.[0]).toEqual(["memories", "others"]);
  });

  it("supports keyboard activation, deletion and reordering", async () => {
    const view = render(WorkspacePageTabBar, {
      props: { tabs, activeId: "memories" }
    });
    const memoryTab = view.getByRole("tab", { name: "记忆" });

    await fireEvent.keyDown(memoryTab, { key: "Enter" });
    await fireEvent.keyDown(memoryTab, { key: "Delete" });
    await fireEvent.keyDown(memoryTab, { key: "ArrowRight", ctrlKey: true, shiftKey: true });

    expect(view.emitted().activate?.[0]).toEqual(["memories"]);
    expect(view.emitted().close?.[0]).toEqual(["memories", "current"]);
    expect(view.emitted().move?.[0]).toEqual(["memories", 3]);
  });

  it("emits a native drag insertion before the hovered tab", async () => {
    const view = render(WorkspacePageTabBar, {
      props: { tabs, activeId: "toolbox" }
    });
    const source = view.getByRole("tab", { name: "工具箱" }).closest("[draggable='true']")!;
    const target = view.getByRole("tab", { name: "能力库" }).closest("[draggable='true']")!;
    Object.defineProperty(target, "getBoundingClientRect", {
      value: () => ({ left: 100, right: 200, width: 100, top: 0, bottom: 36, height: 36, x: 100, y: 0, toJSON: () => ({}) })
    });

    await fireEvent.dragStart(source);
    await fireEvent.dragOver(target, { clientX: 120 });
    await fireEvent.drop(target, { clientX: 120 });

    expect(view.emitted().move?.[0]).toEqual(["toolbox", 2]);
  });

  it("lists all open pages and exposes a fixed close-all action", async () => {
    const view = render(WorkspacePageTabBar, {
      props: { tabs, activeId: "hub" }
    });

    await fireEvent.click(view.getByRole("button", { name: "查看已打开功能页" }));
    expect(view.getByRole("menu", { name: "已打开功能页" }).hidden).toBe(false);
    await fireEvent.click(view.getByRole("menuitem", { name: "切换到 工具箱" }));
    await fireEvent.click(view.getByRole("button", { name: "关闭全部功能页" }));

    expect(view.emitted().activate?.[0]).toEqual(["toolbox"]);
    expect(view.emitted().close?.[0]).toEqual(["hub", "all"]);
  });
});
