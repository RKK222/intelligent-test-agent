import { shallowMount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import FigmaFileExplorer from "../src/components/FigmaFileExplorer.vue";
import GitChangesPanel from "../src/components/GitChangesPanel.vue";
import WorkbenchFooter from "../src/components/WorkbenchFooter.vue";

vi.mock("@test-agent/workbench-shell", async () =>
  vi.importActual("../../../packages/workbench-shell/src/workbenchStore")
);

describe("FigmaFileExplorer", () => {
  it("keeps Agents collapsed at the bottom when entering the file view", () => {
    const wrapper = shallowMount(FigmaFileExplorer, {
      props: {
        workspaceId: "wrk_personal",
        entriesByDirectory: { "": [] },
        expandedDirectories: new Set<string>(),
        changedFiles: []
      }
    });

    const sections = wrapper.findAll(".figma-fe-section");
    expect(sections).toHaveLength(2);
    expect(sections[0].attributes("style")).toContain("flex: 1");
    expect(sections[1].classes()).not.toContain("is-expanded");
    expect(sections[1].text()).toContain("Agents");
  });

  it("shows the total diff count reported by all three change scopes", async () => {
    const wrapper = shallowMount(FigmaFileExplorer, {
      props: {
        workspaceId: "wrk_personal",
        entriesByDirectory: { "": [] },
        expandedDirectories: new Set<string>(),
        changedFiles: [{
          path: "docs/guide.md",
          status: "modified",
          additions: 1,
          deletions: 0,
          patch: ""
        }]
      }
    });

    const changesEntry = wrapper.get('button[aria-label="变更"]');
    expect(changesEntry.text()).toContain("1");

    // GitChangesPanel 常驻加载三类 diff，回传的总量包含 spec、应用 Agent 与公共 Agent。
    wrapper.findComponent(GitChangesPanel).vm.$emit("changes-refreshed", { totalCount: 4, files: [] });
    await wrapper.vm.$nextTick();

    expect(changesEntry.text()).toContain("4");
  });

  it("passes reference visibility to the footer and forwards its open event", async () => {
    const wrapper = shallowMount(FigmaFileExplorer, {
      props: {
        workspaceId: "wrk_personal",
        entriesByDirectory: { "": [] },
        expandedDirectories: new Set<string>(),
        changedFiles: [],
        showReferenceConfiguration: true
      }
    });

    const footer = wrapper.getComponent(WorkbenchFooter);
    expect(footer.props("showReferenceConfiguration")).toBe(true);
    footer.vm.$emit("open-reference-configuration");
    await wrapper.vm.$nextTick();

    expect(wrapper.emitted("openReferenceConfiguration")).toHaveLength(1);
  });
});
