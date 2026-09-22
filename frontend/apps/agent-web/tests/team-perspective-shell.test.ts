import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import FigmaShell from "../src/components/FigmaShell.vue";

describe("FigmaShell team perspective", () => {
  it("hides the perspective switch from users who cannot manage a team", async () => {
    const wrapper = mount(FigmaShell, {
      props: { currentUserName: "普通用户", canEnterTeamManagement: false }
    });
    await wrapper.get(".figma-user-avatar-btn").trigger("click");
    expect(wrapper.find('[data-testid="switch-workbench-perspective"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it("switches the label and context rail for a system administrator", async () => {
    const wrapper = mount(FigmaShell, {
      props: {
        currentUserName: "系统管理员",
        canEnterTeamManagement: true,
        perspective: "WORK"
      }
    });
    await wrapper.get(".figma-user-avatar-btn").trigger("click");
    await wrapper.get('[data-testid="switch-workbench-perspective"]').trigger("click");
    expect(wrapper.emitted("switch-workbench-perspective")).toHaveLength(1);

    await wrapper.setProps({
      perspective: "TEAM_MANAGEMENT",
      teamScopeLocked: false,
      teamScopeMode: "GLOBAL",
      teamScopeLabel: "全平台只读",
      teamApplications: [{ id: "app-1", label: "应用一" }],
      teamApplicationId: "app-1"
    });
    expect(wrapper.get('[data-testid="team-context-rail"]').text()).toContain("管理范围");
    expect(wrapper.get('[data-testid="team-scope-selector"]').text()).toContain("全平台只读");
    expect(wrapper.find('[data-testid="header-context-rail"]').exists()).toBe(false);
    await wrapper.get(".figma-user-avatar-btn").trigger("click");
    expect(wrapper.get('[data-testid="switch-workbench-perspective"]').text()).toContain("返回工作视角");
    wrapper.unmount();
  });
});
