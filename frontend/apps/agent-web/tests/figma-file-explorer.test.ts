import { shallowMount } from "@vue/test-utils";
import { defineComponent, h } from "vue";
import { describe, expect, it, vi } from "vitest";
import FigmaFileExplorer from "../src/components/FigmaFileExplorer.vue";
import AgentConfigPanel from "../src/components/AgentConfigPanel.vue";
import GitChangesPanel from "../src/components/GitChangesPanel.vue";
import WorkbenchFooter from "../src/components/WorkbenchFooter.vue";
import agentWorkbenchSource from "../src/components/AgentWorkbench.vue?raw";
import fileExplorerSource from "../src/components/FigmaFileExplorer.vue?raw";
import { FileExplorer } from "@test-agent/file-explorer";

vi.mock("@test-agent/workbench-shell", async () =>
  vi.importActual("../../../packages/workbench-shell/src/workbenchStore")
);

describe("FigmaFileExplorer", () => {
  it("describes the complete personal pull scope without implying an application-wide rollout", () => {
    expect(agentWorkbenchSource).toContain(
      "每次仍展示 fetch → merge → 刷新 → 当前用户运行态处理的真实结果。"
    );
    expect(agentWorkbenchSource).toContain("hasDismissedPersonalPullConfirm(authStore.currentUser?.userId)");
    expect(agentWorkbenchSource).toContain("dismissPersonalPullConfirm(authStore.currentUser?.userId)");
    expect(agentWorkbenchSource).not.toContain("personal-pull-runtime-reload");
    expect(agentWorkbenchSource).toContain('response.runtimeReloadStatus === "SCHEDULED"');
    expect(agentWorkbenchSource).toContain("新版后端返回 runtimeReloadStatus 后由持久化 rollout 接管");
    expect(agentWorkbenchSource).toContain("<PersonalWorkspacePullDialog");
  });

  it("consumes a successful manual public reload and keeps conflict retries silent", () => {
    expect(agentWorkbenchSource).toContain("function consumePendingPublicRuntimeReload(");
    expect(agentWorkbenchSource).toContain(
      "consumePendingPublicRuntimeReload(pendingPublicReloadRevision, publicRuntimeRoute!)"
    );
    const consumeIndex = agentWorkbenchSource.indexOf(
      "consumePendingPublicRuntimeReload(pendingPublicReloadRevision, publicRuntimeRoute!)"
    );
    const catalogRefetchIndex = agentWorkbenchSource.indexOf(
      "await Promise.all([agentsQuery.refetch(), commandsQuery.refetch()])",
      consumeIndex
    );
    expect(consumeIndex).toBeGreaterThan(-1);
    expect(catalogRefetchIndex).toBeGreaterThan(consumeIndex);

    const resumeStart = agentWorkbenchSource.indexOf("function resumeRuntimeReloadAfterConflict()");
    const scheduleStart = agentWorkbenchSource.indexOf("function scheduleRuntimeReloadConflictRetry()", resumeStart);
    expect(resumeStart).toBeGreaterThan(-1);
    expect(scheduleStart).toBeGreaterThan(resumeStart);
    expect(agentWorkbenchSource.slice(resumeStart, scheduleStart))
      .toContain("reloadReferenceRuntimeIfIdle({ quiet: true })");
  });

  it("binds the command catalog request to the workspace captured by its query key", () => {
    expect(agentWorkbenchSource).toContain(
      'queryKey: computed(() => ["runtime", "commands", selectedWorkspaceIdRef.value ?? ""] as const)'
    );
    expect(agentWorkbenchSource).toContain(
      "queryFn: ({ queryKey }) => api.listCommands(String(queryKey[2]))"
    );
    expect(agentWorkbenchSource).not.toContain(
      "queryFn: () => api.listCommands(selectedWorkspaceIdRef.value!)"
    );
  });

  it("groups refresh and remote pull in one workspace more menu while keeping Git changes independent", async () => {
    const wrapper = shallowMount(FigmaFileExplorer, {
      props: {
        workspaceId: "wrk_personal",
        selectedVersionId: "awv_selected",
        personalWorkspaceId: "pws_current",
        entriesByDirectory: { "": [] },
        expandedDirectories: new Set<string>(),
        changedFiles: []
      }
    });

    expect(wrapper.find('summary[aria-label="更多工作空间操作"]').exists()).toBe(true);
    const menu = wrapper.get("details.figma-fe-more-menu");
    (menu.element as HTMLDetailsElement).open = true;
    await wrapper.get('button[aria-label="刷新文件树"]').trigger("click");
    expect(wrapper.emitted("refresh")).toHaveLength(1);
    expect((menu.element as HTMLDetailsElement).open).toBe(false);

    (menu.element as HTMLDetailsElement).open = true;
    const pullButton = wrapper.get('button[aria-label="拉取远程"]');
    await pullButton.trigger("click");

    expect(wrapper.emitted("pullPersonalWorkspace")).toEqual([["pws_current"]]);
    expect((menu.element as HTMLDetailsElement).open).toBe(false);
    expect(wrapper.findComponent(GitChangesPanel).exists()).toBe(true);
  });

  it("closes the workspace more menu even when refresh is already running", async () => {
    const wrapper = shallowMount(FigmaFileExplorer, {
      props: {
        workspaceId: "wrk_personal",
        entriesByDirectory: { "": [] },
        expandedDirectories: new Set<string>(),
        changedFiles: [],
        loadingPath: new Set([""])
      }
    });
    const menu = wrapper.get("details.figma-fe-more-menu");
    (menu.element as HTMLDetailsElement).open = true;

    await wrapper.get('button[aria-label="刷新文件树"]').trigger("click");

    expect(wrapper.emitted("refresh")).toBeUndefined();
    expect((menu.element as HTMLDetailsElement).open).toBe(false);
  });

  it("disables remote pull in the workspace more menu while a pull is running", () => {
    const wrapper = shallowMount(FigmaFileExplorer, {
      props: {
        workspaceId: "wrk_personal",
        selectedVersionId: "awv_selected",
        personalWorkspaceId: "pws_current",
        pullingPersonalWorkspace: true,
        entriesByDirectory: { "": [] },
        expandedDirectories: new Set<string>(),
        changedFiles: []
      }
    });

    expect(wrapper.get('button[aria-label="拉取远程"]').attributes("disabled")).toBeDefined();
  });

  it("passes the selected application workspace directory to Git changes", () => {
    const wrapper = shallowMount(FigmaFileExplorer, {
      props: {
        workspaceId: "wrk_personal",
        selectedVersionId: "awv_selected",
        appTemplates: [{
          workspaceId: "awp_default",
          directoryPath: "F-GCMS/workspace",
          initialVersion: { versionId: "awv_selected" }
        } as never],
        entriesByDirectory: { "": [] },
        expandedDirectories: new Set<string>(),
        changedFiles: []
      }
    });

    expect(wrapper.findComponent(GitChangesPanel).props("workspaceDirectoryPath"))
      .toBe("F-GCMS/workspace");
  });

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
    expect(wrapper.get('[data-onboarding="workspace-reference"]').attributes("aria-label")).toBe("从 TCDS 导入需求");
  });

  it("enables same-origin requirement import from workspace id and write access without a physical root", async () => {
    const wrapper = shallowMount(FigmaFileExplorer, {
      props: {
        workspaceId: "wrk_personal",
        canWrite: false,
        entriesByDirectory: { "": [] },
        expandedDirectories: new Set<string>(),
        changedFiles: []
      }
    });
    const button = wrapper.get('button[aria-label="从 TCDS 导入需求"]');

    expect(button.attributes("disabled")).toBeDefined();
    await wrapper.setProps({ canWrite: true });
    expect(button.attributes("disabled")).toBeUndefined();
    expect(button.attributes("title")).toBe("从 TCDS 导入需求");
    expect(fileExplorerSource).not.toContain("VITE_IFRAME_URL");
    expect(fileExplorerSource).toContain('new URL("/workspace-requirement-import/", window.location.origin)');
    expect(fileExplorerSource).toContain("event.origin !== window.location.origin");
    expect(fileExplorerSource).toContain("event.source !== iframeRef.value?.contentWindow");
    expect(fileExplorerSource).not.toContain("postMessage({type:'FUNC_DISPATCH'");
  });

  it("waits for the targeted workspace refresh before closing a successful requirement import", async () => {
    const wrapper = shallowMount(FigmaFileExplorer, {
      props: {
        workspaceId: "wrk_personal",
        canWrite: true,
        entriesByDirectory: { "": [] },
        expandedDirectories: new Set<string>(),
        changedFiles: []
      },
      global: {
        stubs: { Teleport: false }
      }
    });

    try {
      await wrapper.get('button[aria-label="从 TCDS 导入需求"]').trigger("click");
      const iframe = document.body.querySelector("iframe.figma-fe-iframe") as HTMLIFrameElement;
      expect(iframe).not.toBeNull();
      const iframePostMessage = vi.spyOn(iframe.contentWindow!, "postMessage");
      window.dispatchEvent(new MessageEvent("message", {
        origin: window.location.origin,
        source: iframe.contentWindow,
        data: { type: "ITA_REQUIREMENT_IMPORT_READY" }
      }));
      await wrapper.vm.$nextTick();
      const context = iframePostMessage.mock.calls
        .map(([message]) => message as Record<string, unknown>)
        .find((message) => message.type === "ITA_REQUIREMENT_IMPORT_CONTEXT")!;
      const requestId = String(context.requestId);
      window.dispatchEvent(new MessageEvent("message", {
        origin: window.location.origin,
        source: iframe.contentWindow,
        data: {
          type: "ITA_REQUIREMENT_IMPORT_COMPLETE",
          requestId,
          result: {
            status: "SUCCEEDED",
            createdDirectories: 6,
            importedFiles: 1,
            overwrittenFiles: 0,
            failedFiles: 0,
            failures: [],
            workspaceRelativeDisplayPaths: ["spec/I-01-登录需求"]
          }
        }
      }));
      await wrapper.vm.$nextTick();

      expect(wrapper.emitted("requirement-import-complete")).toEqual([[
        expect.objectContaining({
          workspaceId: "wrk_personal",
          requestId,
          result: expect.objectContaining({ workspaceRelativeDisplayPaths: ["spec/I-01-登录需求"] })
        })
      ]]);
      expect(document.body.querySelector("iframe.figma-fe-iframe")).not.toBeNull();

      wrapper.vm.completeRequirementImportRefresh({ requestId, status: "SUCCEEDED", success: true });
      await wrapper.vm.$nextTick();
      expect(iframePostMessage).toHaveBeenCalledWith({
        type: "ITA_REQUIREMENT_IMPORT_TREE_REFRESHED",
        requestId,
        success: true
      }, window.location.origin);
      expect(document.body.querySelector("iframe.figma-fe-iframe")).toBeNull();
    } finally {
      wrapper.unmount();
    }
  });

  it("refreshes only the imported parent display paths instead of replaying the expanded spec tree", () => {
    expect(agentWorkbenchSource).toContain(
      "await refreshWorkspaceView(payload.workspaceId, { targets: [ROOT_WORKSPACE_VIEW_TARGET] })"
    );
    expect(agentWorkbenchSource).toContain("if (!await expandPathToFile(path, true))");
    expect(agentWorkbenchSource).toContain('@requirement-import-complete="handleRequirementImportComplete"');
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

  it("keeps source files writable while hiding Git, Agent publication, and application versions", async () => {
    const wrapper = shallowMount(FigmaFileExplorer, {
      props: {
        workspaceId: "wrk_source",
        appName: "F-COSS",
        entriesByDirectory: { "": [] },
        expandedDirectories: new Set<string>(),
        changedFiles: [],
        canWrite: true,
        workspaceKind: "APP_SOURCE",
        appSourceContext: {
          appId: "app_fcoss",
          repositoryId: "repo_code",
          generation: 9,
          purpose: "TEAM",
          workspaceId: "wrk_source",
          linuxServerId: "linux-a",
          expiresAt: "2026-07-30T00:00:00Z"
        }
      }
    });

    expect(wrapper.text()).toContain("源码快照");
    expect(wrapper.text()).toContain("无 Git");
    expect(wrapper.text()).toContain("同机成员共享");
    expect(wrapper.find('button[aria-label="变更"]').exists()).toBe(false);
    expect(wrapper.findComponent(GitChangesPanel).exists()).toBe(false);
    expect(wrapper.findComponent(AgentConfigPanel).exists()).toBe(false);
    expect(wrapper.findComponent(FileExplorer).props("canWrite")).toBe(true);
    expect(wrapper.findComponent(WorkbenchFooter).props("workspaceKind")).toBe("APP_SOURCE");

    await wrapper.get('button[aria-label="返回应用工作区"]').trigger("click");
    expect(wrapper.emitted("returnManagedWorkspace")).toHaveLength(1);
  });

  it("keeps experience files and local Git writable while disabling remote publication", () => {
    const wrapper = shallowMount(FigmaFileExplorer, {
      props: {
        workspaceId: "wrk_exp_shared",
        workspaceName: "体验工作区",
        entriesByDirectory: { "": [] },
        expandedDirectories: new Set<string>(),
        changedFiles: [],
        canWrite: true,
        canMutateGit: true,
        workspaceKind: "EXPERIENCE"
      }
    });

    expect(wrapper.text()).toContain("多人共享");
    expect(wrapper.text()).toContain("请勿存放敏感数据");
    expect(wrapper.find('button[aria-label="变更"]').exists()).toBe(true);
    expect(wrapper.findComponent(FileExplorer).props("canWrite")).toBe(true);
    expect(wrapper.text()).toContain("本地 Git 可提交，不提供推送");
    expect(wrapper.findComponent(GitChangesPanel).props("canMutateGit")).toBe(true);
    expect(wrapper.findComponent(GitChangesPanel).props("localOnlyGit")).toBe(true);
    expect(wrapper.findComponent(GitChangesPanel).props("includeAgentScopes")).toBe(false);
    expect(wrapper.findComponent(AgentConfigPanel).exists()).toBe(false);
    expect(wrapper.find('button[aria-label="拉取远程"]').exists()).toBe(false);
    expect(wrapper.findComponent(WorkbenchFooter).props("workspaceKind")).toBe("EXPERIENCE");
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

  it("forwards Agent tree mutations to the existing revision-based diff refresh owner", async () => {
    const wrapper = shallowMount(FigmaFileExplorer, {
      props: {
        workspaceId: "wrk_personal",
        entriesByDirectory: { "": [] },
        expandedDirectories: new Set<string>(),
        changedFiles: []
      }
    });
    const mutation = {
      scope: "WORKSPACE" as const,
      workspaceId: "wrk_personal",
      paths: ["agents/obsolete"],
      deleted: { path: "agents/obsolete", type: "directory" as const }
    };

    wrapper.findComponent(AgentConfigPanel).vm.$emit("files-mutated", mutation);
    await wrapper.vm.$nextTick();

    expect(wrapper.emitted("agent-config-mutated")).toEqual([[mutation]]);
  });

  it("forwards the initialized public worktree remount request to the Agent config panel", () => {
    const request = {
      revision: 3,
      worktreeId: "agw_prepared",
      linuxServerId: "linux-2"
    };
    const wrapper = shallowMount(FigmaFileExplorer, {
      props: {
        workspaceId: "wrk_personal",
        entriesByDirectory: { "": [] },
        expandedDirectories: new Set<string>(),
        changedFiles: [],
        publicWorktreeMountRequest: request
      }
    });

    expect(wrapper.findComponent(AgentConfigPanel).props("publicWorktreeMountRequest")).toEqual(request);
  });
});
