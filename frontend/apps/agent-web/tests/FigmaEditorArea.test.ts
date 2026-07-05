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
});
