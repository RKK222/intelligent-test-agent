import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
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

  it("shows server workspace switch button only when requested", async () => {
    const hidden = mount(WorkbenchFooter, {
      props: {
        appName: "F-COSS",
        templates: [template],
        showSave: false
      }
    });

    expect(hidden.find('[aria-label="切换服务器工作空间"]').exists()).toBe(false);

    const shown = mount(WorkbenchFooter, {
      props: {
        appName: "F-COSS",
        templates: [template],
        showServerWorkspaceSwitch: true,
        showSave: false
      }
    });

    await shown.find('[aria-label="切换服务器工作空间"]').trigger("click");

    expect(shown.emitted("open-server-workspace-picker")).toHaveLength(1);
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

  it("displays filename in path value, sets writePath as title, and copies path on copy-path click", async () => {
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
        writePath: "src/components/WorkbenchFooter.vue"
      }
    });

    // 应该显示文件名
    const pathVal = wrapper.find(".ta-workbench-footer-path-value");
    expect(pathVal.text()).toBe("WorkbenchFooter.vue");
    expect(pathVal.attributes("title")).toBe("src/components/WorkbenchFooter.vue");

    // 点击复制路径按钮
    const copyBtn = wrapper.find(".ta-workbench-footer-copy-path");
    expect(copyBtn.exists()).toBe(true);
    await copyBtn.trigger("click");
    expect(mockWriteText).toHaveBeenCalledWith("src/components/WorkbenchFooter.vue");
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
});
