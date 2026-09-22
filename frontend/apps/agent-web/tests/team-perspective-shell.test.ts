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
      apps: [
        { id: "app-1", name: "应用一", historical: true },
        { id: "app-2", name: "应用二" }
      ],
      selectedAppId: "app-1",
      localWorkspaces: [],
      showAppSource: false,
      workspaceKind: "MANAGED"
    });
    expect(wrapper.find('[data-testid="team-context-rail"]').exists()).toBe(false);
    const rail = wrapper.get('[data-testid="header-context-rail"]');
    expect(rail.text()).toContain("应用");
    expect(rail.text()).toContain("工作空间");
    expect(rail.text()).toContain("版本");
    await wrapper.get('[aria-label="应用：应用一"]').trigger("click");
    expect(wrapper.text()).toContain("历史");
    expect(wrapper.text()).not.toContain("加入其他应用");
    expect(wrapper.text()).not.toContain("worktree");
    await wrapper.get(".figma-user-avatar-btn").trigger("click");
    expect(wrapper.get('[data-testid="switch-workbench-perspective"]').text()).toContain("返回工作视角");
    wrapper.unmount();
  });
});
