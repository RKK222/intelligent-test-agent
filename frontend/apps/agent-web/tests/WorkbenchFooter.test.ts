import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { AppSourceRepositorySummary } from "@test-agent/shared-types";
import WorkbenchFooter from "../src/components/WorkbenchFooter.vue";

describe("WorkbenchFooter", () => {
  afterEach(() => {
    document.body.innerHTML = "";
  });

  const template = {
    workspaceId: "wks_template",
    appId: "app_fcoss",
    workspaceName: "主服务",
    repositoryId: "repo_1",
    repositoryName: "repo",
    directoryPath: "services/main",
    branch: "main",
    enabled: true,
    standard: true,
    createdAt: "2026-06-26T00:00:00Z",
    updatedAt: "2026-06-26T00:00:00Z",
    versions: []
  };

  const appSourceRepository = {
    repositoryId: "repo-code",
    name: "应用代码库",
    englishName: "app-code",
    downloadState: "DOWNLOADED_ACTIVE",
    generation: 3,
    purpose: "TEAM",
    branch: "main",
    selectedPaths: [],
    occupied: false,
    openable: true,
    manageable: true,
    serverSummaries: []
  } satisfies AppSourceRepositorySummary;

  it("keeps the super-admin server workspace switch as an independent button", async () => {
    const hidden = mount(WorkbenchFooter, {
      props: {
        appName: "F-COSS",
        templates: [template],
        showSave: false
      }
    });

    expect(hidden.find('[aria-label="切换服务器工作空间"]').exists()).toBe(false);
    expect(hidden.get('[data-onboarding="workspace-selector"]').attributes("data-onboarding")).toBe("workspace-selector");

    const shown = mount(WorkbenchFooter, {
      props: {
        appName: "F-COSS",
        templates: [template],
        showServerWorkspaceSwitch: true,
        showSave: false
      }
    });

    const serverSwitch = shown.get('[aria-label="切换服务器工作空间"]');
    await serverSwitch.trigger("click");

    expect(shown.emitted("open-server-workspace-picker")).toHaveLength(1);

    await shown.find('[data-onboarding="workspace-selector"]').trigger("click");
    expect(document.body.querySelector('[aria-label="切换服务器工作空间"]')).toBeNull();
  });

  it("places the accessible reference configuration icon immediately after the workspace switch", async () => {
    const wrapper = mount(WorkbenchFooter, {
      props: {
        appName: "F-COSS",
        templates: [template],
        showReferenceConfiguration: true,
        showSave: false
      }
    });

    const buttons = wrapper.find(".ta-workbench-footer-left").findAll("button");
    expect(buttons[0].classes()).toContain("ta-workbench-footer-branch");
    expect(buttons[1].attributes("aria-label")).toBe("打开引用配置");
    expect(buttons[1].attributes("title")).toBe("打开引用配置");

    await buttons[1].trigger("click");
    expect(wrapper.emitted("open-reference-configuration")).toHaveLength(1);
  });

  it("keeps automation repositories out of the primary workspace menu without moving server/config buttons", async () => {
    const automationTemplate = {
      ...template,
      workspaceId: "wks_automation",
      workspaceName: "接口自动化",
      repositoryType: "AUTOMATION_CODE_REPOSITORY"
    };
    const wrapper = mount(WorkbenchFooter, {
      props: {
        appName: "F-COSS",
        templates: [template, automationTemplate],
        showAppSource: true,
        appSourceRepositories: [appSourceRepository],
        showReferenceConfiguration: true,
        showServerWorkspaceSwitch: true,
        showSave: false
      }
    });

    const buttons = wrapper.find(".ta-workbench-footer-left").findAll("button");
    expect(buttons.map((button) => button.attributes("aria-label") ?? button.attributes("data-onboarding")))
      .toEqual(["切换应用代码库或测试工作空间", "打开引用配置", "切换服务器工作空间"]);

    await buttons[2].trigger("click");
    expect(wrapper.emitted("open-server-workspace-picker")).toHaveLength(1);

    await wrapper.find('[data-onboarding="workspace-selector"]').trigger("click");
    expect(wrapper.emitted("load-app-source-repositories")).toHaveLength(1);
    const appSourceSwitch = document.body.querySelector('[aria-label="打开应用代码库源码"]') as HTMLButtonElement | null;
    expect(appSourceSwitch).not.toBeNull();
    expect(document.body.querySelector('[aria-label="管理应用代码库"]')).not.toBeNull();
    expect(document.body.textContent).not.toContain("自动化代码库");
    expect(document.body.textContent).not.toContain("接口自动化");
    expect(document.body.textContent).toContain("测试工作空间");
    expect(document.body.textContent).toContain("主服务");
    expect(document.body.querySelector(".ta-workbench-cascade-item .ta-workbench-cascade-workspace-icon")).not.toBeNull();
    appSourceSwitch?.click();
    await wrapper.vm.$nextTick();
    expect(wrapper.emitted("open-app-source-repository")?.[0]).toEqual([appSourceRepository]);
    expect(wrapper.emitted("open-app-source")).toBeUndefined();

    await wrapper.find('[data-onboarding="workspace-selector"]').trigger("click");
    (document.body.querySelector('[aria-label="管理应用代码库"]') as HTMLButtonElement).click();
    await wrapper.vm.$nextTick();
    expect(wrapper.emitted("open-app-source")).toHaveLength(1);
  });

  it("shows first-download repositories as gray management links while disabling stale or unavailable rows", async () => {
    const notDownloaded = {
      ...appSourceRepository,
      repositoryId: "repo-new",
      name: "尚未下载",
      downloadState: "NOT_DOWNLOADED" as const,
      generation: null
    };
    const wrapper = mount(WorkbenchFooter, {
      attachTo: document.body,
      props: {
        appName: "F-COSS",
        showAppSource: true,
        showSave: false,
        loadingAppSourceRepositories: true,
        appSourceRepositories: [
          appSourceRepository,
          notDownloaded,
          {
            ...appSourceRepository,
            repositoryId: "repo-unavailable",
            name: "副本未就绪",
            openable: false,
            unavailableReason: "当前服务器副本未就绪"
          }
        ]
      }
    });

    await wrapper.find('[data-onboarding="workspace-selector"]').trigger("click");
    const refreshing = document.body.querySelector('[aria-label="打开应用代码库源码"]') as HTMLButtonElement;
    expect(refreshing.disabled).toBe(true);
    expect(refreshing.title).toBe("正在刷新应用代码库状态…");
    const firstDownload = document.body.querySelector('[aria-label="管理尚未下载源码"]') as HTMLButtonElement;
    expect(firstDownload.disabled).toBe(true);
    expect(firstDownload.classList).toContain("is-not-downloaded");
    expect(firstDownload.title).toBe("正在刷新应用代码库状态…");
    const unavailable = document.body.querySelector('[aria-label="打开副本未就绪源码"]') as HTMLButtonElement;
    expect(unavailable.disabled).toBe(true);
    expect(unavailable.title).toBe("正在刷新应用代码库状态…");
    await wrapper.setProps({ loadingAppSourceRepositories: false });
    expect(refreshing.disabled).toBe(false);
    expect(firstDownload.disabled).toBe(false);
    expect(firstDownload.title).toBe("下载或管理尚未下载源码");
    expect(unavailable.disabled).toBe(true);
    expect(unavailable.title).toBe("当前服务器副本未就绪");
    expect(document.body.querySelector('[aria-label="管理应用代码库"]')).not.toBeNull();
    firstDownload.click();
    await wrapper.vm.$nextTick();
    expect(wrapper.emitted("manage-app-source-repository")?.[0]).toEqual([notDownloaded]);
  });

  it("hides the reference configuration icon unless explicitly authorized by the parent", () => {
    const wrapper = mount(WorkbenchFooter, {
      props: { appName: "F-COSS", templates: [template], showSave: false }
    });

    expect(wrapper.find('button[aria-label="打开引用配置"]').exists()).toBe(false);
  });

  it("switches between the app code repository and its test workspace in one menu", async () => {
    const wrapper = mount(WorkbenchFooter, {
      attachTo: document.body,
      props: {
        appName: "F-COSS",
        showAppSource: true,
        appSourceRepositories: [appSourceRepository],
        showServerWorkspaceSwitch: true,
        showSave: false,
        workspaceKind: "APP_SOURCE",
        selectedAppSourceRepositoryId: "repo-code"
      }
    });

    await wrapper.find('[data-onboarding="workspace-selector"]').trigger("click");
    expect(document.body.querySelector('[aria-label="打开应用代码库源码"]')).not.toBeNull();
    expect(document.body.querySelector(".ta-workbench-cascade-source-button.is-selected")).not.toBeNull();
    expect(document.body.querySelector('[aria-label="测试工作空间"]')).not.toBeNull();
    expect(wrapper.find('[aria-label="切换服务器工作空间"]').exists()).toBe(true);
    expect(document.querySelector('.ta-workbench-cascade-panel [aria-label="切换服务器工作空间"]')).toBeNull();

    (document.body.querySelector('[aria-label="测试工作空间"]') as HTMLButtonElement).click();
    await wrapper.vm.$nextTick();
    expect(wrapper.emitted("return-managed-workspace")).toHaveLength(1);
  });

  it("shows the active personal worktree branch in the switch trigger", () => {
    const wrapper = mount(WorkbenchFooter, {
      props: {
        appName: "F-COSS",
        templates: [{
          ...template,
          versions: [{
            versionId: "awv_1",
            applicationWorkspaceId: "wks_template",
            appId: "app_fcoss",
            repositoryId: "repo_1",
            version: "20260618",
            branch: "feature_testagent_20260618",
            repoRootPath: "/tmp/repo",
            workspaceRootPath: "/tmp/repo/services/main",
            runtimeWorkspace: {
              workspaceId: "wrk_1",
              name: "default",
              rootPath: "/tmp/repo/services/main",
              status: "ACTIVE",
              createdAt: "2026-06-26T00:00:00Z",
              updatedAt: "2026-06-26T00:00:00Z"
            },
            status: "ACTIVE",
            createdAt: "2026-06-26T00:00:00Z",
            updatedAt: "2026-06-26T00:00:00Z"
          }]
        }],
        selectedVersionId: "awv_1",
        personalWorkspaceBranch: "feature_testagent_20260618_usr_888888888_default"
      }
    });

    expect(wrapper.find(".ta-workbench-footer-branch").attributes("title"))
      .toContain("feature_testagent_20260618_usr_888888888_default");
  });

  it("shows the active personal worktree branch inside the version submenu", async () => {
    const wrapper = mount(WorkbenchFooter, {
      attachTo: document.body,
      props: {
        appName: "F-COSS",
        templates: [{
          ...template,
          versions: [{
            versionId: "awv_1",
            applicationWorkspaceId: "wks_template",
            appId: "app_fcoss",
            repositoryId: "repo_1",
            version: "20260618",
            branch: "feature_testagent_20260618",
            repoRootPath: "/tmp/repo",
            workspaceRootPath: "/tmp/repo/services/main",
            runtimeWorkspace: {
              workspaceId: "wrk_1",
              name: "default",
              rootPath: "/tmp/repo/services/main",
              status: "ACTIVE",
              createdAt: "2026-06-26T00:00:00Z",
              updatedAt: "2026-06-26T00:00:00Z"
            },
            status: "ACTIVE",
            createdAt: "2026-06-26T00:00:00Z",
            updatedAt: "2026-06-26T00:00:00Z"
          }]
        }],
        selectedVersionId: "awv_1",
        personalWorkspaceBranch: "feature_testagent_20260618_usr_888888888_default"
      }
    });

    await wrapper.find(".ta-workbench-footer-branch").trigger("click");
    const templateItem = document.body.querySelector(".ta-workbench-cascade-item") as HTMLElement;
    templateItem.dispatchEvent(new MouseEvent("mouseenter", { bubbles: true }));
    await wrapper.vm.$nextTick();

    expect(document.body.textContent).toContain("worktree: feature_testagent_20260618_usr_888888888_default");
  });

  it("hides disabled workspace templates and keeps legacy templates visible", async () => {
    const wrapper = mount(WorkbenchFooter, {
      attachTo: document.body,
      props: {
        appName: "F-COSS",
        templates: [
          { ...template, workspaceId: "wks_enabled", workspaceName: "已启用工作空间", enabled: true },
          { ...template, workspaceId: "wks_disabled", workspaceName: "已停用工作空间", enabled: false },
          { ...template, workspaceId: "wks_legacy", workspaceName: "历史工作空间", enabled: undefined }
        ],
        showSave: false
      }
    });

    await wrapper.find(".ta-workbench-footer-branch").trigger("click");

    expect(document.body.textContent).toContain("已启用工作空间");
    expect(document.body.textContent).toContain("历史工作空间");
    expect(document.body.textContent).not.toContain("已停用工作空间");
  });

  it("keeps Git-inaccessible templates visible but disables their version submenu", async () => {
    const deniedTemplate = {
      ...template,
      workspaceId: "wks_denied",
      workspaceName: "权限失效工作空间",
      gitAccessStatus: "INACCESSIBLE",
      gitAccessReason: "REPOSITORY_PERMISSION_REQUIRED",
      gitAccessMessage: "Git 仓库读取权限已失效",
      gitAccessCheckedAt: "2026-08-23T12:00:00Z"
    };
    const wrapper = mount(WorkbenchFooter, {
      attachTo: document.body,
      props: {
        appName: "F-COSS",
        templates: [deniedTemplate],
        showSave: false
      } as any
    });

    await wrapper.find(".ta-workbench-footer-branch").trigger("click");
    const item = document.body.querySelector(".ta-workbench-cascade-item") as HTMLElement;
    expect(item.classList).toContain("is-disabled");
    expect(item.getAttribute("aria-disabled")).toBe("true");
    expect(item.title).toBe("Git 仓库读取权限已失效");
    expect(item.textContent).toContain("Git 仓库读取权限已失效");
    item.dispatchEvent(new MouseEvent("mouseenter", { bubbles: true }));
    await wrapper.vm.$nextTick();
    expect(document.body.querySelector(".ta-workbench-cascade-submenu")).toBeNull();
    expect(wrapper.emitted("load-versions")).toBeUndefined();
  });

  it("closes an already-open create-version dialog when source mode disables version selection", async () => {
    const wrapper = mount(WorkbenchFooter, {
      attachTo: document.body,
      global: { provide: { api: { listRepositoryBranches: vi.fn() } } },
      props: {
        appName: "F-COSS",
        templates: [template],
        showSave: false,
        workspaceKind: "MANAGED"
      }
    });

    const setupState = (wrapper.vm.$ as unknown as { setupState: Record<string, unknown> }).setupState as {
      openCreateVersionDialog: (value: typeof template) => void;
      confirmCreateVersion: () => void;
      createVersionOpen: boolean;
      createVersionValue: string;
    };
    setupState.openCreateVersionDialog(template);
    setupState.createVersionValue = "20260728";
    await wrapper.vm.$nextTick();
    expect(setupState.createVersionOpen).toBe(true);
    const staleConfirm = setupState.confirmCreateVersion;

    await wrapper.setProps({ workspaceKind: "APP_SOURCE" });

    expect(setupState.createVersionOpen).toBe(false);
    staleConfirm();
    expect(wrapper.emitted("create-version")).toBeUndefined();
  });

  it("handles preview button single click (full) and double click (split)", async () => {
    const wrapper = mount(WorkbenchFooter, {
      props: {
        showSave: true,
        showPreviewButton: true,
        markdownPreviewMode: "off"
      }
    });

    const previewBtn = wrapper.find('[data-testid="footer-markdown-preview"]');
    expect(previewBtn.exists()).toBe(true);

    // 单击
    await previewBtn.trigger("click");
    await new Promise((r) => setTimeout(r, 260));
    expect(wrapper.emitted("update:markdownPreviewMode")).toEqual([["full"]]);

    // 双击
    await previewBtn.trigger("dblclick");
    expect(wrapper.emitted("update:markdownPreviewMode")?.at(-1)).toEqual(["split"]);
  });

  it("handles preview button transitions from split or full preview back to off", async () => {
    const wrapper = mount(WorkbenchFooter, {
      props: {
        showSave: true,
        showPreviewButton: true,
        markdownPreviewMode: "split"
      }
    });

    const previewBtn = wrapper.find('[data-testid="footer-markdown-preview"]');

    // From split state, single click should transition to off
    await previewBtn.trigger("click");
    await new Promise((r) => setTimeout(r, 260));
    expect(wrapper.emitted("update:markdownPreviewMode")).toEqual([["off"]]);

    const wrapper2 = mount(WorkbenchFooter, {
      props: {
        showSave: true,
        showPreviewButton: true,
        markdownPreviewMode: "full"
      }
    });

    const previewBtn2 = wrapper2.find('[data-testid="footer-markdown-preview"]');

    // From full state, single click should transition to off
    await previewBtn2.trigger("click");
    await new Promise((r) => setTimeout(r, 260));
    expect(wrapper2.emitted("update:markdownPreviewMode")).toEqual([["off"]]);
  });

  it("copies only the resolved absolute path from one button", async () => {
    // mock window.isSecureContext and navigator.clipboard
    Object.defineProperty(window, "isSecureContext", {
      value: true,
      writable: true,
      configurable: true
    });
    const mockWriteText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", {
      value: {
        writeText: mockWriteText,
      },
      writable: true,
      configurable: true
    });

    const wrapper = mount(WorkbenchFooter, {
      props: {
        showSave: true,
        writePath: "src/components/WorkbenchFooter.vue",
        workspaceId: "wrk_1"
      },
      global: { provide: { api: { resolveWorkspacePhysicalPath: vi.fn().mockResolvedValue("/workspace/project/src/components/WorkbenchFooter.vue") } } }
    });

    const copyButtons = wrapper.findAll(".ta-workbench-footer-copy-path");

    expect(copyButtons).toHaveLength(1);
    expect(copyButtons[0].text()).toBe("复制路径");
    expect(copyButtons[0].attributes("title")).toBe("复制绝对文件路径");

    await copyButtons[0].trigger("click");
    expect(mockWriteText).toHaveBeenCalledOnce();
    expect(mockWriteText)
      .toHaveBeenCalledWith("/workspace/project/src/components/WorkbenchFooter.vue");
  });

  it("normalizes Windows separators when copying an absolute path", async () => {
    Object.defineProperty(window, "isSecureContext", {
      value: true,
      writable: true,
      configurable: true
    });
    const mockWriteText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", {
      value: { writeText: mockWriteText },
      writable: true,
      configurable: true
    });

    const wrapper = mount(WorkbenchFooter, {
      props: {
        showSave: true,
        writePath: "src\\components\\WorkbenchFooter.vue",
        workspaceId: "wrk_1"
      },
      global: { provide: { api: { resolveWorkspacePhysicalPath: vi.fn().mockResolvedValue("C:/workspace/project/src/components/WorkbenchFooter.vue") } } }
    });

    await wrapper.find(".ta-workbench-footer-copy-path").trigger("click");

    expect(mockWriteText)
      .toHaveBeenCalledWith("C:/workspace/project/src/components/WorkbenchFooter.vue");
  });

  it("copies an explicit Agent absolute path instead of the synthetic tab route", async () => {
    const mockWriteText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", {
      value: { writeText: mockWriteText },
      writable: true,
      configurable: true
    });

    const wrapper = mount(WorkbenchFooter, {
      props: {
        showSave: true,
        writePath: "agent-workspace:wrk_850cccb889474f4a84cf04fd90584134:::agents%2Fgit-worktree-opencode-baseline-20260717.md",
        copyPath: "/workspace/F-COSS/workspace/.opencode/agents/git-worktree-opencode-baseline-20260717.md"
      }
    });

    const copyButton = wrapper.get(".ta-workbench-footer-copy-path");
    expect(copyButton.attributes("title")).toBe("复制绝对文件路径");

    await copyButton.trigger("click");

    expect(mockWriteText).toHaveBeenCalledWith(
      "/workspace/F-COSS/workspace/.opencode/agents/git-worktree-opencode-baseline-20260717.md"
    );
    expect(mockWriteText.mock.calls[0]?.[0]).not.toContain(":::");
  });

  it("hides copy path when only a logical root or relative Agent path is available", () => {
    const logicalWorkspace = mount(WorkbenchFooter, {
      props: {
        showSave: true,
        writePath: "src/main.ts"
      }
    });
    const relativeAgent = mount(WorkbenchFooter, {
      props: {
        showSave: true,
        writePath: "agent-workspace:wrk_1:::agents%2Freview.md",
        copyPath: "agents/review.md"
      }
    });

    expect(logicalWorkspace.find(".ta-workbench-footer-copy-path").exists()).toBe(false);
    expect(relativeAgent.find(".ta-workbench-footer-copy-path").exists()).toBe(false);
  });

  it("does not expose physical path resolution for a workspace without an authorized runtime id", () => {
    const wrapper = mount(WorkbenchFooter, {
      props: {
        showSave: true,
        writePath: "src/main.ts",
        workspaceId: "wrk-source",
        workspaceKind: "APP_SOURCE"
      }
    });

    expect(wrapper.find(".ta-workbench-footer-copy-path").exists()).toBe(false);
  });

  it("renders locate button when writePath is defined, and emits locate on click", async () => {
    const wrapper = mount(WorkbenchFooter, {
      props: {
        showSave: true,
        writePath: "src/components/WorkbenchFooter.vue"
      }
    });

    const locateBtn = wrapper.find(".ta-workbench-footer-locate");
    expect(locateBtn.exists()).toBe(true);

    await locateBtn.trigger("click");
    expect(wrapper.emitted("locate")).toEqual([["src/components/WorkbenchFooter.vue"]]);
  });

  it("renders save button only when dirty or saving is true", async () => {
    // Case 1: not dirty, not saving
    const wrapper1 = mount(WorkbenchFooter, {
      props: {
        showSave: true,
        dirty: false,
        saving: false
      }
    });
    expect(wrapper1.find(".ta-workbench-footer-save").exists()).toBe(false);

    // Case 2: dirty = true
    const wrapper2 = mount(WorkbenchFooter, {
      props: {
        showSave: true,
        dirty: true,
        saving: false
      }
    });
    expect(wrapper2.find(".ta-workbench-footer-save").exists()).toBe(true);

    // Case 3: saving = true
    const wrapper3 = mount(WorkbenchFooter, {
      props: {
        showSave: true,
        dirty: false,
        saving: true
      }
    });
    expect(wrapper3.find(".ta-workbench-footer-save").exists()).toBe(true);
  });

  it("提供独立 .mind 编辑入口并在不可编辑时保留原因", async () => {
    const editable = mount(WorkbenchFooter, {
      props: { showSave: true, showMindMapEditButton: true }
    });
    const edit = editable.get('[data-testid="footer-mind-map-edit"]');
    expect(edit.attributes("aria-label")).toBe("编辑思维导图");
    await edit.trigger("click");
    expect(editable.emitted("editMindMap")).toHaveLength(1);

    const blocked = mount(WorkbenchFooter, {
      props: {
        showSave: true,
        showMindMapEditButton: true,
        mindMapEditDisabled: true,
        mindMapEditDisabledReason: "元数据校验失败"
      }
    });
    const blockedEdit = blocked.get('[data-testid="footer-mind-map-edit"]');
    expect(blockedEdit.attributes("disabled")).toBeDefined();
    expect(blockedEdit.attributes("title")).toBe("元数据校验失败");
  });

  it("待应用思维导图草稿禁用保存并提示先应用或取消", () => {
    const wrapper = mount(WorkbenchFooter, {
      props: {
        showSave: true,
        dirty: true,
        saveBlockedReason: "请先应用或取消思维导图编辑"
      }
    });

    const save = wrapper.get(".ta-workbench-footer-save");
    expect(save.attributes("disabled")).toBeDefined();
    expect(save.attributes("title")).toBe("请先应用或取消思维导图编辑");
  });
});
