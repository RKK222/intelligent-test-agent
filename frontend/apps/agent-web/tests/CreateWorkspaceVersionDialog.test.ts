import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import CreateWorkspaceVersionDialog from "../src/components/CreateWorkspaceVersionDialog.vue";

describe("CreateWorkspaceVersionDialog", () => {
  afterEach(() => {
    document.body.innerHTML = "";
  });

  it("creates a standard workspace version directly from a selected remote branch", async () => {
    const listRepositoryBranches = vi.fn().mockResolvedValue([
      "main",
      "feature_testagent_20260824",
      "feature_testagent_20260230",
      "feature_testagent_20260707"
    ]);
    const template = {
      workspaceId: "workspace-standard",
      appId: "app-a",
      workspaceName: "测试工作空间",
      repositoryId: "repo-standard",
      directoryPath: "workspace",
      branch: "main",
      enabled: true,
      standard: true,
      createdAt: "2026-08-24T00:00:00Z",
      updatedAt: "2026-08-24T00:00:00Z"
    };
    const wrapper = mount(CreateWorkspaceVersionDialog, {
      attachTo: document.body,
      global: { provide: { api: { listRepositoryBranches } } },
      props: { modelValue: true, template }
    });
    await flushPromises();

    expect(listRepositoryBranches).toHaveBeenCalledWith("repo-standard");
    expect(document.body.textContent).toContain("无需再选择月和日");
    expect(document.body.querySelector(".el-date-editor")).toBeNull();
    const state = (wrapper.vm.$ as unknown as { setupState: Record<string, unknown> }).setupState as {
      branch: string;
      branches: string[];
      confirmCreateVersion: () => void;
    };
    expect(state.branches).toEqual(["feature_testagent_20260824", "feature_testagent_20260707"]);
    expect(state.branch).toBe("feature_testagent_20260824");
    state.confirmCreateVersion();

    expect(wrapper.emitted("submit")?.[0]?.[0]).toEqual({
      template,
      branch: "feature_testagent_20260824"
    });
  });

  it("loads a non-standard repository branch and submits the existing yyyyMMdd contract", async () => {
    const listRepositoryBranches = vi.fn().mockResolvedValue(["release/2026.08", "main"]);
    const template = {
      workspaceId: "workspace-legacy",
      appId: "app-a",
      workspaceName: "兼容工作空间",
      repositoryId: "repo-legacy",
      repositoryName: "兼容版本库",
      directoryPath: "workspace",
      branch: "main",
      enabled: true,
      standard: false,
      createdAt: "2026-08-24T00:00:00Z",
      updatedAt: "2026-08-24T00:00:00Z"
    };
    const wrapper = mount(CreateWorkspaceVersionDialog, {
      attachTo: document.body,
      global: { provide: { api: { listRepositoryBranches } } },
      props: { modelValue: true, template }
    });
    await flushPromises();

    expect(listRepositoryBranches).toHaveBeenCalledWith("repo-legacy");
    const state = (wrapper.vm.$ as unknown as { setupState: Record<string, unknown> }).setupState as {
      versionValue: string;
      branch: string;
      confirmCreateVersion: () => void;
    };
    expect(state.branch).toBe("release/2026.08");
    state.versionValue = "20260824";
    state.confirmCreateVersion();

    expect(wrapper.emitted("submit")?.[0]?.[0]).toEqual({
      template,
      version: "20260824",
      branch: "release/2026.08"
    });
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([false]);
  });
});
