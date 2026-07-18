import { shallowMount } from "@vue/test-utils";
import { defineComponent, h } from "vue";
import { describe, expect, it, vi } from "vitest";
import FigmaFileExplorer from "../src/components/FigmaFileExplorer.vue";
import GitChangesPanel from "../src/components/GitChangesPanel.vue";
import WorkbenchFooter from "../src/components/WorkbenchFooter.vue";
import { FileExplorer } from "@test-agent/file-explorer";

vi.mock("@test-agent/workbench-shell", async () =>
  vi.importActual("../../../packages/workbench-shell/src/workbenchStore")
);

describe("FigmaFileExplorer", () => {
  it("refreshes changes immediately and continuously while the changes panel is visible", async () => {
    vi.useFakeTimers();
    const refreshChanges = vi.fn();
    const GitChangesPanelStub = defineComponent({
      name: "GitChangesPanel",
      setup(_, { expose }) {
        expose({ refreshChanges });
        return () => h("div", { "data-testid": "git-changes-panel" });
      }
    });
    const wrapper = shallowMount(FigmaFileExplorer, {
      props: {
        workspaceId: "wrk_personal",
        entriesByDirectory: { "": [] },
        expandedDirectories: new Set<string>(),
        changedFiles: []
      },
      global: {
        stubs: { GitChangesPanel: GitChangesPanelStub }
      }
    });

    try {
      await wrapper.get('button[aria-label="变更"]').trigger("click");
      await wrapper.vm.$nextTick();
      expect(refreshChanges).toHaveBeenCalledTimes(1);

      await vi.advanceTimersByTimeAsync(5000);
      expect(refreshChanges).toHaveBeenCalledTimes(2);

      await wrapper.get('button[aria-label="文件树"]').trigger("click");
      await vi.advanceTimersByTimeAsync(5000);
      expect(refreshChanges).toHaveBeenCalledTimes(2);
    } finally {
      wrapper.unmount();
      vi.useRealTimers();
    }
  });

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
    expect(wrapper.get('[data-onboarding="workspace-reference"]').attributes("aria-label")).toBe("打开外部页面");
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

  it("forwards workspace view node navigation without collapsing it to a path", async () => {
    const wrapper = shallowMount(FigmaFileExplorer, {
      props: {
        workspaceId: "wrk_personal",
        entriesByDirectory: { "": [] },
        expandedDirectories: new Set<string>(),
        changedFiles: []
      }
    });
    const node = {
      id: "reference:requirements:guide",
      type: "file" as const,
      path: "docs/guide.md",
      name: "guide.md",
      locator: { kind: "REFERENCE" as const, path: "docs/guide.md", referenceAlias: "requirements" },
      source: "REFERENCE" as const,
      merged: true,
      collision: false,
      readonly: true,
      referenceAliases: ["requirements"]
    };

    wrapper.findComponent(FileExplorer).vm.$emit("open-view-file", node);
    await wrapper.vm.$nextTick();

    expect(wrapper.emitted("openViewFile")).toEqual([[node]]);
  });

  it("keeps partial reference warnings visible with a refresh action", async () => {
    const wrapper = shallowMount(FigmaFileExplorer, {
      props: {
        workspaceId: "wrk_personal",
        entriesByDirectory: { "": [] },
        expandedDirectories: new Set<string>(),
        changedFiles: [],
        workspaceViewWarnings: [{ alias: "legacy", code: "REFERENCE_UNAVAILABLE", message: "引用副本不可用" }]
      }
    });

    expect(wrapper.text()).toContain("legacy：引用副本不可用");
    await wrapper.get('button[aria-label="刷新引用文件树"]').trigger("click");
    expect(wrapper.emitted("refresh")).toHaveLength(1);
  });
});
