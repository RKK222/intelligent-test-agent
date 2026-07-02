import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, waitFor } from "@testing-library/vue";
import { createPinia } from "pinia";
import GitChangesPanel from "../src/components/GitChangesPanel.vue";

const apiClientMock = vi.hoisted(() => ({
  getVcsDiffFiles: vi.fn(),
  getWorkspaceGitDiff: vi.fn(),
  discardWorkspaceGitFiles: vi.fn(),
  getWorkspaceGitConflict: vi.fn(),
  resolveWorkspaceGitConflict: vi.fn(),
  abortWorkspaceGitConflict: vi.fn(),
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
      message: "合并成功",
      remotePushed: true,
      headCommit: "commit_merged"
    });
    apiClientMock.getWorkspaceGitConflict.mockResolvedValue({
      path: "src/conflict.ts",
      rawStatus: "UU",
      baseContent: "base",
      currentContent: "current",
      incomingContent: "incoming",
      resultContent: "<<<<<<< HEAD\ncurrent\n=======\nincoming\n>>>>>>> app"
    });
    apiClientMock.resolveWorkspaceGitConflict.mockResolvedValue(undefined);
    apiClientMock.abortWorkspaceGitConflict.mockResolvedValue(undefined);
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

  it("keeps conflict prompt after publish refresh and separates unmerged files from staged files", async () => {
    apiClientMock.getWorkspaceGitDiff
      .mockResolvedValueOnce({
        files: [
          {
            path: "workspace/docs/selected.md",
            status: "untracked",
            rawStatus: "??",
            staged: false,
            patch: "--- /dev/null\n+++ b/workspace/docs/selected.md\n@@ -0,0 +1 @@\n+selected",
            additions: 1,
            deletions: 0
          }
        ]
      })
      .mockResolvedValueOnce({
        files: [
          {
            path: "workspace/docs/conflict.md",
            status: "conflict",
            rawStatus: "AU",
            staged: true,
            patch: "",
            additions: 0,
            deletions: 0
          },
          {
            path: "workspace/docs/auto-merged-delete.md",
            status: "deleted",
            rawStatus: "D ",
            staged: true,
            patch: "diff --git a/workspace/docs/auto-merged-delete.md b/workspace/docs/auto-merged-delete.md\n--- a/workspace/docs/auto-merged-delete.md\n+++ /dev/null\n@@ -1 +0,0 @@\n-old",
            additions: 0,
            deletions: 1
          }
        ]
      });
    apiClientMock.publishPersonalWorkspace.mockResolvedValueOnce({
      status: "CONFLICT",
      personalWorkspaceId: "psw_default",
      versionId: "awv_1",
      conflictFiles: ["workspace/docs/conflict.md"],
      message: "合并冲突，请在个人工作区中解决冲突后重新提交并推送",
      remotePushed: false,
      headCommit: null
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

    expect(await view.findByText("workspace/docs/selected.md")).toBeTruthy();

    await fireEvent.click(view.getByTitle("暂存文件"));
    await fireEvent.update(view.getByPlaceholderText("输入提交说明。首行为主题，空行后为详细描述..."), "fix: conflict prompt");
    await fireEvent.click(view.getByRole("button", { name: "提交并推送" }));

    await waitFor(() => expect(apiClientMock.publishPersonalWorkspace).toHaveBeenCalledWith("psw_default", {
      commitMessage: "fix: conflict prompt",
      files: ["workspace/docs/selected.md"]
    }));
    expect(await view.findByText(/合并冲突：请在个人工作区中解决 workspace\/docs\/conflict.md/)).toBeTruthy();
    expect(await view.findByText("CONFLICT")).toBeTruthy();
    expect(await view.findByText("AU")).toBeTruthy();
    expect(await view.findByText("workspace/docs/auto-merged-delete.md")).toBeTruthy();
    expect(await view.findByText("以下暂存项来自未完成合并自动应用的变更，解决冲突后会随 merge 一起提交")).toBeTruthy();
    expect(view.getByText("STAGED (已暂存) (1)")).toBeTruthy();
    expect((view.getByRole("button", { name: "提交并推送" }) as HTMLButtonElement).disabled).toBe(true);
    expect(view.queryByText("提交并推送成功！")).toBeNull();
  });

  it("does not show success when backend cannot confirm remote push", async () => {
    apiClientMock.getWorkspaceGitDiff.mockResolvedValue({
      files: [{ path: "src/selected.ts", status: "modified", staged: false, patch: "", additions: 1, deletions: 0 }]
    });
    apiClientMock.publishPersonalWorkspace.mockResolvedValueOnce({
      status: "MERGED",
      personalWorkspaceId: "psw_default",
      versionId: "awv_1",
      conflictFiles: [],
      message: "本地合并完成",
      remotePushed: false,
      headCommit: null
    });
    const view = render(GitChangesPanel, {
      props: { workspaceId: "wrk_1234567890abcdef", personalWorkspaceId: "psw_default", apiBaseUrl: "http://api", canWrite: true },
      global: { plugins: [createPinia()] }
    });

    expect(await view.findByText("src/selected.ts")).toBeTruthy();
    await fireEvent.click(view.getByTitle("暂存文件"));
    await fireEvent.update(view.getByPlaceholderText("输入提交说明。首行为主题，空行后为详细描述..."), "fix: push");
    await fireEvent.click(view.getByRole("button", { name: "提交并推送" }));

    expect(await view.findByText(/远端推送结果未确认/)).toBeTruthy();
    expect(view.queryByText("提交并推送成功！")).toBeNull();
  });

  it("opens the three-way editor for a conflict row", async () => {
    apiClientMock.getWorkspaceGitDiff.mockResolvedValue({
      files: [{ path: "src/conflict.ts", status: "conflict", rawStatus: "UU", staged: true, patch: "", additions: 0, deletions: 0 }]
    });
    const view = render(GitChangesPanel, {
      props: { workspaceId: "wrk_1234567890abcdef", personalWorkspaceId: "psw_default", apiBaseUrl: "http://api", canWrite: true },
      global: { plugins: [createPinia()] }
    });

    await fireEvent.click(await view.findByText("src/conflict.ts"));

    await waitFor(() => expect(apiClientMock.getWorkspaceGitConflict)
      .toHaveBeenCalledWith("wrk_1234567890abcdef", "src/conflict.ts"));
    expect(await view.findByText("合并编辑器")).toBeTruthy();
    expect(await view.findByText("当前个人版本")).toBeTruthy();
    expect(await view.findByText("应用版本")).toBeTruthy();
    expect(await view.findByText("合并结果（可编辑）")).toBeTruthy();
  });
});
