import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import FigmaShell from "../src/components/FigmaShell.vue";

describe("FigmaShell", () => {
  it("shows unknown instead of unassigned when process status query has no data", async () => {
    const wrapper = mount(FigmaShell, {
      props: {
        currentUserName: "888888888",
        opencodeProcessStatus: null,
        opencodeProcessLoading: false
      }
    });

    await wrapper.get(".figma-user-avatar-btn").trigger("click");

    expect(wrapper.get(".figma-user-menu-service-text").text()).toBe("状态未知");
    expect(wrapper.text()).not.toContain("待分配专属进程");
  });
});
