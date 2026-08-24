import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import CreateWorkspaceVersionDialog from "../src/components/CreateWorkspaceVersionDialog.vue";

describe("CreateWorkspaceVersionDialog", () => {
  afterEach(() => {
    document.body.innerHTML = "";
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
