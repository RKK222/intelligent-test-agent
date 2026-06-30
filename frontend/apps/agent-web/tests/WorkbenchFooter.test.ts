import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import WorkbenchFooter from "../src/components/WorkbenchFooter.vue";

describe("WorkbenchFooter", () => {
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
        templates: [template]
      }
    });

    expect(hidden.find('[aria-label="切换服务器工作空间"]').exists()).toBe(false);

    const shown = mount(WorkbenchFooter, {
      props: {
        appName: "F-COSS",
        templates: [template],
        showServerWorkspaceSwitch: true
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

    expect(wrapper.text()).toContain("feature_testagent_20260618_usr_888888888_default");
    expect(wrapper.find(".ta-workbench-footer-branch").attributes("title"))
      .toContain("feature_testagent_20260618_usr_888888888_default");
  });
});
