import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it } from "vitest";
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
});
