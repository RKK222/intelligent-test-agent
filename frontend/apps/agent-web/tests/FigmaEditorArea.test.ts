import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
import FigmaEditorArea from "../src/components/FigmaEditorArea.vue";

const tabs = [
  { id: "src/a.ts", path: "src/a.ts", title: "a.ts", content: "a", savedContent: "a" },
  { id: "src/b.ts", path: "src/b.ts", title: "b.ts", content: "b", savedContent: "b" },
  { id: "src/c.ts", path: "src/c.ts", title: "c.ts", content: "c", savedContent: "c" }
];

describe("FigmaEditorArea", () => {
  afterEach(() => {
    document.body.innerHTML = "";
  });

  it("emits batch close paths from the tab context menu", async () => {
    const wrapper = mount(FigmaEditorArea, {
      attachTo: document.body,
      props: {
        tabs,
        activePath: "src/b.ts"
      },
      global: {
        stubs: {
          WorkbenchFooter: true
        }
      }
    });

    const tabEls = wrapper.findAll(".figma-editor-tab");

    await tabEls[1].trigger("contextmenu", { clientX: 40, clientY: 20 });
    const rightButton = Array.from(document.body.querySelectorAll<HTMLButtonElement>(".figma-editor-tab-menu-item"))
      .find((button) => button.textContent?.includes("关闭右侧所有"));
    expect(rightButton).toBeTruthy();
    rightButton!.click();
    await wrapper.vm.$nextTick();
    expect(wrapper.emitted("closeMany")).toEqual([[["src/c.ts"]]]);

    await tabEls[1].trigger("contextmenu", { clientX: 40, clientY: 20 });
    const leftButton = Array.from(document.body.querySelectorAll<HTMLButtonElement>(".figma-editor-tab-menu-item"))
      .find((button) => button.textContent?.includes("关闭左侧所有"));
    expect(leftButton).toBeTruthy();
    leftButton!.click();
    await wrapper.vm.$nextTick();
    expect(wrapper.emitted("closeMany")?.at(-1)).toEqual([["src/a.ts"]]);

    await tabEls[1].trigger("contextmenu", { clientX: 40, clientY: 20 });
    const allButton = Array.from(document.body.querySelectorAll<HTMLButtonElement>(".figma-editor-tab-menu-item"))
      .find((button) => button.textContent?.includes("关闭所有"));
    expect(allButton).toBeTruthy();
    allButton!.click();
    await wrapper.vm.$nextTick();
    expect(wrapper.emitted("closeMany")?.at(-1)).toEqual([["src/a.ts", "src/b.ts", "src/c.ts"]]);

    wrapper.unmount();
  });

  it("scrolls to the end when tabs are added or the last tab is activated", async () => {
    const wrapper = mount(FigmaEditorArea, {
      attachTo: document.body,
      props: {
        tabs: [
          { id: "src/a.ts", path: "src/a.ts", title: "a.ts", content: "a", savedContent: "a" }
        ],
        activePath: "src/a.ts"
      },
      global: {
        stubs: {
          WorkbenchFooter: true
        }
      }
    });

    const containerEl = wrapper.find(".figma-editor-tabs").element as HTMLElement;
    Object.defineProperty(containerEl, "scrollWidth", {
      configurable: true,
      value: 1000
    });

    // 1. 增加 tabs 长度，应该滚动到最后
    await wrapper.setProps({
      tabs: [
        { id: "src/a.ts", path: "src/a.ts", title: "a.ts", content: "a", savedContent: "a" },
        { id: "src/b.ts", path: "src/b.ts", title: "b.ts", content: "b", savedContent: "b" }
      ],
      activePath: "src/a.ts"
    });
    await wrapper.vm.$nextTick();
    expect(containerEl.scrollLeft).toBe(1000);
    // 应该聚焦到 a.ts (索引 0)
    expect(document.activeElement).toBe(wrapper.findAll(".figma-editor-tab")[0].element);

    // 重置 scrollLeft
    containerEl.scrollLeft = 0;

    // 2. 激活最后一个 tab (b.ts)，应该滚动到最后
    await wrapper.setProps({
      activePath: "src/b.ts"
    });
    await wrapper.vm.$nextTick();
    expect(containerEl.scrollLeft).toBe(1000);
    // 应该聚焦到 b.ts (索引 1)
    expect(document.activeElement).toBe(wrapper.findAll(".figma-editor-tab")[1].element);

    // 重置 scrollLeft
    containerEl.scrollLeft = 0;

    // 3. 激活一个不是最后的 tab (a.ts)，不应该触发滚动到最后
    await wrapper.setProps({
      activePath: "src/a.ts"
    });
    await wrapper.vm.$nextTick();
    expect(containerEl.scrollLeft).toBe(0);
    // 应该聚焦到 a.ts (索引 0)
    expect(document.activeElement).toBe(wrapper.findAll(".figma-editor-tab")[0].element);

    wrapper.unmount();
  });
});
