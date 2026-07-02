import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, waitFor } from "@testing-library/vue";
import { createPinia } from "pinia";
import GitChangesPanel from "../src/components/GitChangesPanel.vue";

const apiClientMock = vi.hoisted(() => ({
  getVcsDiffFiles: vi.fn(),
  getWorkspaceGitDiff: vi.fn(),
  discardWorkspaceGitFiles: vi.fn(),
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
  publishPersonalWorkspace: vi.fn(),
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
    apiClientMock.discardWorkspaceGitFiles.mockResolvedValue(undefined);
    apiClientMock.getPublicAgentDiff.mockResolvedValue({ files: [] });
    apiClientMock.getWorkspaceAgentDiff.mockResolvedValue({ files: [] });
    apiClientMock.publishPersonalWorkspace.mockResolvedValue({
      status: "MERGED",
      personalWorkspaceId: "psw_default",
      versionId: "awv_1",
      conflictFiles: [],
      message: "合并成功"
    });
    apiClientMock.connectAgentConfigProgress.mockResolvedValue({ close: vi.fn() });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("does not expose mock data button and loads workspace plus application agent changes", async () => {
    apiClientMock.getWorkspaceGitDiff.mockResolvedValue({
      files: [
        {
          path: "需求/登录测试.md",
          status: "modified",
          staged: false,
          patch: "@@ -1 +1 @@\n-旧\n+新",
          additions: 1,
          deletions: 1
        }
      ]
    });
    apiClientMock.getWorkspaceAgentDiff.mockResolvedValue({
      files: [
        {
          path: "agents/payment-test.md",
          status: "M",
          staged: false,
          patch: "@@ -1 +1 @@\n-old\n+new"
        },
        {
          path: "skills/payment-case-design/SKILL.md",
          status: "M",
          staged: false,
          patch: "@@ -1 +1 @@\n-old\n+new"
        },
        {
          path: "F-COSS/workspace/02-设计/Test Material.md",
          status: "M",
          staged: false,
          patch: "@@ -1 +1 @@\n-old\n+new"
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

    await waitFor(() => expect(apiClientMock.getWorkspaceAgentDiff).toHaveBeenCalled());

    expect(view.queryByRole("button", { name: "加载测试数据" })).toBeNull();
    expect(await view.findByText("需求/登录测试.md")).toBeTruthy();
    expect(await view.findByText("agents/payment-test.md", { exact: false })).toBeTruthy();
    expect(await view.findByText("skills/payment-case-design/SKILL.md", { exact: false })).toBeTruthy();
    expect(view.queryByText("F-COSS/workspace/02-设计/Test Material.md", { exact: false })).toBeNull();
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

  it("discards an application workspace file and refreshes the diff list", async () => {
    apiClientMock.getWorkspaceGitDiff
      .mockResolvedValueOnce({
        files: [
          {
            path: "workspace/02-设计/Test Material.md",
            status: "modified",
            staged: false,
            patch: "@@ -1 +1 @@\n-old\n+new",
            additions: 1,
            deletions: 0
          }
        ]
      })
      .mockResolvedValueOnce({ files: [] });
    const changesRefreshed = vi.fn();

    const view = render(GitChangesPanel, {
      props: {
        workspaceId: "wrk_1234567890abcdef",
        apiBaseUrl: "http://api",
        canWrite: true,
        "onChanges-refreshed": changesRefreshed
      },
      global: {
        plugins: [createPinia()]
      }
    });

    expect(await view.findByText("workspace/02-设计/Test Material.md")).toBeTruthy();

    const discardButton = view.getByTitle("回退文件改动");
    await fireEvent.click(discardButton);

    await waitFor(() => expect(apiClientMock.discardWorkspaceGitFiles)
      .toHaveBeenCalledWith("wrk_1234567890abcdef", ["workspace/02-设计/Test Material.md"]));
    await waitFor(() => expect(view.queryByText("workspace/02-设计/Test Material.md")).toBeNull());
    await waitFor(() => expect(changesRefreshed).toHaveBeenCalledWith({
      paths: ["workspace/02-设计/Test Material.md"]
    }));
  });

  it("publishes only staged application workspace files", async () => {
    apiClientMock.getWorkspaceGitDiff
      .mockResolvedValueOnce({
        files: [
          {
            path: "src/selected.ts",
            status: "modified",
            staged: false,
            patch: "@@ -1 +1 @@\n-old\n+selected",
            additions: 1,
            deletions: 1
          },
          {
            path: "src/unselected.ts",
            status: "modified",
            staged: false,
            patch: "@@ -1 +1 @@\n-old\n+unselected",
            additions: 1,
            deletions: 1
          }
        ]
      })
      .mockResolvedValueOnce({
        files: [
          {
            path: "src/unselected.ts",
            status: "modified",
            staged: false,
            patch: "@@ -1 +1 @@\n-old\n+unselected",
            additions: 1,
            deletions: 1
          }
        ]
      });

    const view = render(GitChangesPanel, {
      props: {
        workspaceId: "wrk_1234567890abcdef",
        personalWorkspaceId: "psw_default",
        apiBaseUrl: "http://api",
        canWrite: true
      },
      global: {
        plugins: [createPinia()]
      }
    });

    expect(await view.findByText("src/selected.ts")).toBeTruthy();
    expect(await view.findByText("src/unselected.ts")).toBeTruthy();

    await fireEvent.click(view.getAllByTitle("暂存文件")[0]);
    await fireEvent.update(view.getByPlaceholderText("输入提交说明。首行为主题，空行后为详细描述..."), "fix: selected only");
    await fireEvent.click(view.getByRole("button", { name: "提交并推送" }));

    await waitFor(() => expect(apiClientMock.publishPersonalWorkspace).toHaveBeenCalledWith("psw_default", {
      commitMessage: "fix: selected only",
      files: ["src/selected.ts"]
    }));
    await waitFor(() => expect(view.queryByText("src/selected.ts")).toBeNull());
    expect(await view.findByText("src/unselected.ts")).toBeTruthy();
  });
});
