import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, waitFor } from "@testing-library/vue";
import { createPinia } from "pinia";
import GitChangesPanel from "../src/components/GitChangesPanel.vue";

const apiClientMock = vi.hoisted(() => ({
  getVcsDiffFiles: vi.fn(),
  getWorkspaceGitDiff: vi.fn(),
  getPublicAgentDiff: vi.fn(),
  getWorkspaceAgentDiff: vi.fn(),
  stagePublicAgentFiles: vi.fn(),
  stageWorkspaceAgentFiles: vi.fn(),
  unstagePublicAgentFiles: vi.fn(),
  unstageWorkspaceAgentFiles: vi.fn(),
  commitPublicAgentConfig: vi.fn(),
  commitWorkspaceAgentConfig: vi.fn(),
  publishPublicAgentConfig: vi.fn(),
  publishWorkspaceAgentConfig: vi.fn(),
  connectAgentConfigProgress: vi.fn()
}));

vi.mock("@test-agent/backend-api", () => ({
  BackendApiError: class BackendApiError extends Error {
    code = "UNKNOWN";
    traceId = "trace_fixed";
    details = {};
    retryable = false;
    status = 500;
  },
  createBackendApiClient: () => apiClientMock
}));

vi.mock("@test-agent/workbench-shell", async () => {
  const store = await vi.importActual<typeof import("../../../packages/workbench-shell/src/workbenchStore")>(
    "../../../packages/workbench-shell/src/workbenchStore"
  );
  return store;
});

describe("GitChangesPanel", () => {
  beforeEach(() => {
    apiClientMock.getVcsDiffFiles.mockResolvedValue({ files: [] });
    apiClientMock.getWorkspaceGitDiff.mockResolvedValue({ files: [] });
    apiClientMock.getPublicAgentDiff.mockResolvedValue({ files: [] });
    apiClientMock.getWorkspaceAgentDiff.mockResolvedValue({ files: [] });
    apiClientMock.connectAgentConfigProgress.mockResolvedValue({ close: vi.fn() });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("loads mock workspace changes and application-level agent and skill changes", async () => {
    const view = render(GitChangesPanel, {
      props: {
        workspaceId: "wrk_1234567890abcdef",
        apiBaseUrl: "http://api",
        canWrite: true
      },
      global: {
        plugins: [createPinia()]
      }
    });

    await waitFor(() => expect(apiClientMock.getWorkspaceAgentDiff).toHaveBeenCalled());
    await waitFor(() => expect(view.getByRole("button", { name: "加载测试数据" })).toBeTruthy());
    await fireEvent.click(view.getByRole("button", { name: "加载测试数据" }));

    expect(await view.findByText("src/App.vue")).toBeTruthy();
    expect(await view.findByText("agents/payment-test.md", { exact: false })).toBeTruthy();
    expect(await view.findByText("skills/payment-case-design/SKILL.md", { exact: false })).toBeTruthy();
    expect(view.queryByText("[公共]", { exact: false })).toBeNull();
    expect(view.queryByText("opencode/agents/public_agent_test.json", { exact: false })).toBeNull();
  });

  it("loads application workspace changes from platform git diff instead of opencode vcs diff", async () => {
    apiClientMock.getWorkspaceGitDiff.mockResolvedValue({
      files: [
        {
          path: "package.json",
          status: "modified",
          staged: false,
          patch: "@@ -1 +1 @@\n-old\n+new",
          additions: 1,
          deletions: 1
        }
      ]
    });

    const view = render(GitChangesPanel, {
      props: {
        workspaceId: "wrk_1234567890abcdef",
        apiBaseUrl: "http://api",
        canWrite: true
      },
      global: {
        plugins: [createPinia()]
      }
    });

    expect(await view.findByText("package.json")).toBeTruthy();
    expect(apiClientMock.getWorkspaceGitDiff).toHaveBeenCalledWith("wrk_1234567890abcdef");
    expect(apiClientMock.getVcsDiffFiles).not.toHaveBeenCalled();
  });
});
