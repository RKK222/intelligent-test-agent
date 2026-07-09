import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, waitFor } from "@testing-library/vue";
import { createPinia } from "pinia";
import GitChangesPanel from "../src/components/GitChangesPanel.vue";

const apiClientMock = vi.hoisted(() => ({
  getVcsDiffFiles: vi.fn(),
  getWorkspaceGitDiff: vi.fn(),
  discardWorkspaceGitFiles: vi.fn(),
  stageWorkspaceGitFiles: vi.fn(),
  unstageWorkspaceGitFiles: vi.fn(),
  getWorkspaceGitConflict: vi.fn(),
  resolveWorkspaceGitConflict: vi.fn(),
  abortWorkspaceGitConflict: vi.fn(),
  resolveAllWorkspaceGitConflicts: vi.fn(),
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
  previewPersonalWorkspacePublish: vi.fn(),
  connectAgentConfigProgress: vi.fn()
}));

vi.mock("@test-agent/backend-api", () => ({
  BackendApiError: class BackendApiError extends Error {
    code: string;
    traceId: string;
    details: Record<string, unknown>;
    retryable: boolean;
    status: number;

    constructor(
      status: number,
      failure: {
        code: string;
        message: string;
        traceId: string;
        details?: Record<string, unknown>;
        retryable?: boolean;
      }
    ) {
      super(failure.message);
      this.code = failure.code;
      this.traceId = failure.traceId;
      this.details = failure.details ?? {};
      this.retryable = failure.retryable ?? false;
      this.status = status;
    }
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
    vi.resetAllMocks();
    apiClientMock.getVcsDiffFiles.mockResolvedValue({ files: [] });
    apiClientMock.getWorkspaceGitDiff.mockResolvedValue({ files: [] });
    apiClientMock.discardWorkspaceGitFiles.mockResolvedValue(undefined);
    apiClientMock.stageWorkspaceGitFiles.mockResolvedValue(undefined);
    apiClientMock.unstageWorkspaceGitFiles.mockResolvedValue(undefined);
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
    apiClientMock.previewPersonalWorkspacePublish.mockResolvedValue({
      applicationHead: "application_head",
      personalHead: "personal_head",
      incomingCommitCount: 0,
      changedFileCount: 0,
      addedCount: 0,
      modifiedCount: 0,
      deletedCount: 0,
      renamedCount: 0,
      samplePaths: []
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
    apiClientMock.resolveAllWorkspaceGitConflicts.mockResolvedValue(undefined);
    apiClientMock.connectAgentConfigProgress.mockResolvedValue({ close: vi.fn() });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    vi.restoreAllMocks();
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
    expect(await view.findByText("登录测试.md")).toBeTruthy();
    expect(await view.findByText("payment-test.md", { exact: false })).toBeTruthy();
    expect(await view.findByText("SKILL.md", { exact: false })).toBeTruthy();
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

    expect(await view.findByText("Test Material.md")).toBeTruthy();

    const discardButton = view.getByTitle("回退文件改动");
    await fireEvent.click(discardButton);

    await waitFor(() => expect(apiClientMock.discardWorkspaceGitFiles)
      .toHaveBeenCalledWith("wrk_1234567890abcdef", ["workspace/02-设计/Test Material.md"]));
    await waitFor(() => expect(view.queryByText("Test Material.md")).toBeNull());
    await waitFor(() => expect(changesRefreshed).toHaveBeenCalledWith(expect.objectContaining({
      paths: ["workspace/02-设计/Test Material.md"]
    })));
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
            path: "src/selected.ts",
            status: "added",
            rawStatus: "A ",
            staged: true,
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

    expect(await view.findByText("selected.ts")).toBeTruthy();
    expect(await view.findByText("unselected.ts")).toBeTruthy();

    await fireEvent.click(view.getAllByTitle("暂存文件")[0]);
    await waitFor(() => expect(apiClientMock.stageWorkspaceGitFiles)
      .toHaveBeenCalledWith("wrk_1234567890abcdef", ["src/selected.ts"]));
    await fireEvent.update(view.getByPlaceholderText("输入提交说明。首行为主题，空行后为详细描述..."), "fix: selected only");
    await fireEvent.click(view.getByRole("button", { name: "提交并推送" }));

    await waitFor(() => expect(apiClientMock.publishPersonalWorkspace).toHaveBeenCalledWith("psw_default", expect.objectContaining({
      commitMessage: "fix: selected only",
      files: ["src/selected.ts"],
      operationId: expect.stringMatching(/^aco_/)
    })));
    expect(apiClientMock.publishPersonalWorkspace.mock.calls[0][1]).not.toHaveProperty("expectedApplicationHead");
    expect(apiClientMock.previewPersonalWorkspacePublish).not.toHaveBeenCalled();
    await waitFor(() => expect(view.queryByText("selected.ts")).toBeNull());
    expect(await view.findByText("unselected.ts")).toBeTruthy();
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
            path: "workspace/docs/selected.md",
            status: "added",
            rawStatus: "A ",
            staged: true,
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

    expect(await view.findByText("selected.md")).toBeTruthy();

    await fireEvent.click(view.getByTitle("暂存文件"));
    await waitFor(() => expect(apiClientMock.stageWorkspaceGitFiles)
      .toHaveBeenCalledWith("wrk_1234567890abcdef", ["workspace/docs/selected.md"]));
    await fireEvent.update(view.getByPlaceholderText("输入提交说明。首行为主题，空行后为详细描述..."), "fix: conflict prompt");
    await fireEvent.click(view.getByRole("button", { name: "提交并推送" }));

    await waitFor(() => expect(apiClientMock.publishPersonalWorkspace).toHaveBeenCalledWith("psw_default", expect.objectContaining({
      commitMessage: "fix: conflict prompt",
      files: ["workspace/docs/selected.md"],
      operationId: expect.stringMatching(/^aco_/)
    })));
    expect(apiClientMock.publishPersonalWorkspace.mock.calls[0][1]).not.toHaveProperty("expectedApplicationHead");
    expect(apiClientMock.previewPersonalWorkspacePublish).not.toHaveBeenCalled();
    expect(await view.findByText(/合并产生 1 个冲突文件/)).toBeTruthy();
    expect(await view.findByText("CONFLICT")).toBeTruthy();
    expect(await view.findByText("AU")).toBeTruthy();
    expect(await view.findByText("auto-merged-delete.md")).toBeTruthy();
    expect(await view.findByText("可继续取消暂存普通文件；解决全部冲突后 Git 才允许提交")).toBeTruthy();
    expect(view.getByText("STAGED (已暂存) (1)")).toBeTruthy();
    expect((view.getByRole("button", { name: "提交并推送" }) as HTMLButtonElement).disabled).toBe(true);
    expect(view.queryByText("提交并推送成功！")).toBeNull();
  });

  it("does not show success when backend cannot confirm remote push", async () => {
    apiClientMock.getWorkspaceGitDiff
      .mockResolvedValueOnce({
        files: [{ path: "src/selected.ts", status: "modified", staged: false, patch: "", additions: 1, deletions: 0 }]
      })
      .mockResolvedValueOnce({
        files: [{ path: "src/selected.ts", status: "modified", rawStatus: "M ", staged: true, patch: "", additions: 1, deletions: 0 }]
      })
      .mockResolvedValue({ files: [] });
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

    expect(await view.findByText("selected.ts")).toBeTruthy();
    await fireEvent.click(view.getByTitle("暂存文件"));
    await waitFor(() => expect(apiClientMock.stageWorkspaceGitFiles)
      .toHaveBeenCalledWith("wrk_1234567890abcdef", ["src/selected.ts"]));
    await fireEvent.update(view.getByPlaceholderText("输入提交说明。首行为主题，空行后为详细描述..."), "fix: push");
    await fireEvent.click(view.getByRole("button", { name: "提交并推送" }));

    expect(await view.findByText(/远端推送结果未确认/)).toBeTruthy();
    expect(view.queryByText("提交并推送成功！")).toBeNull();
  });

  it("does not run preview again after conflicts were resolved in the current merge", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const conflict = { path: "src/conflict.ts", status: "conflict", rawStatus: "UU", staged: true, patch: "", additions: 0, deletions: 0 };
    const resolved = { path: "src/conflict.ts", status: "modified", rawStatus: "M ", staged: true, patch: "@@ -1 +1 @@\n-old\n+resolved", additions: 1, deletions: 1 };
    apiClientMock.getWorkspaceGitDiff
      .mockResolvedValueOnce({ files: [conflict] })
      .mockResolvedValueOnce({ files: [resolved] })
      .mockResolvedValueOnce({ files: [] });

    const view = render(GitChangesPanel, {
      props: { workspaceId: "wrk_1234567890abcdef", personalWorkspaceId: "psw_default", apiBaseUrl: "http://api", canWrite: true },
      global: { plugins: [createPinia()] }
    });

    await fireEvent.click(await view.findByRole("button", { name: /保留个人/ }));
    await waitFor(() => expect(apiClientMock.resolveAllWorkspaceGitConflicts)
      .toHaveBeenCalledWith("wrk_1234567890abcdef", { resolution: "CURRENT" }));
    await fireEvent.update(view.getByPlaceholderText("输入提交说明。首行为主题，空行后为详细描述..."), "fix: finish merge");
    await fireEvent.click(view.getByRole("button", { name: "提交并推送" }));

    await waitFor(() => expect(apiClientMock.publishPersonalWorkspace).toHaveBeenCalledWith("psw_default", expect.objectContaining({
      commitMessage: "fix: finish merge",
      files: ["src/conflict.ts"],
      operationId: expect.stringMatching(/^aco_/)
    })));
    expect(apiClientMock.previewPersonalWorkspacePublish).not.toHaveBeenCalled();
  });

  it("shows failed step commands from backend error details instead of stale preview commands", async () => {
    const { BackendApiError } = await import("@test-agent/backend-api");
    apiClientMock.getWorkspaceGitDiff
      .mockResolvedValueOnce({
        files: [{ path: "src/selected.ts", status: "modified", staged: false, patch: "", additions: 1, deletions: 0 }]
      })
      .mockResolvedValueOnce({
        files: [{ path: "src/selected.ts", status: "modified", rawStatus: "M ", staged: true, patch: "", additions: 1, deletions: 0 }]
      });
    apiClientMock.publishPersonalWorkspace.mockRejectedValueOnce(new BackendApiError(502, {
      success: false,
      code: "GIT_UNAVAILABLE",
      message: "Git 远程读取失败",
      traceId: "trace_publish",
      details: {
        failedStep: "PREPARE_REMOTE",
        executedCommands: ["git -C /repo fetch origin", "git -C /repo pull --ff-only feature_x"]
      }
    }));

    const view = render(GitChangesPanel, {
      props: { workspaceId: "wrk_1234567890abcdef", personalWorkspaceId: "psw_default", apiBaseUrl: "http://api", canWrite: true },
      global: { plugins: [createPinia()] }
    });

    expect(await view.findByText("selected.ts")).toBeTruthy();
    await fireEvent.click(view.getByTitle("暂存文件"));
    await fireEvent.update(view.getByPlaceholderText("输入提交说明。首行为主题，空行后为详细描述..."), "fix: remote");
    await fireEvent.click(view.getByRole("button", { name: "提交并推送" }));

    expect(await view.findByText(/提交失败：Git 远程读取失败/)).toBeTruthy();
    expect(await view.findByText("git -C /repo pull --ff-only feature_x")).toBeTruthy();
    expect(view.queryByText("git fetch origin")).toBeNull();
    expect(view.getByText("FAILED")).toBeTruthy();
  });

  it("shows the currently running git command from publish progress events", async () => {
    let progressHandler: ((event: { currentStep?: string; command?: string; status?: string }) => void) | undefined;
    apiClientMock.connectAgentConfigProgress.mockImplementationOnce(async (_operationId: string, handler: typeof progressHandler) => {
      progressHandler = handler;
      return { close: vi.fn() };
    });
    apiClientMock.getWorkspaceGitDiff.mockResolvedValue({
      files: [{ path: "src/selected.ts", status: "modified", rawStatus: "M ", staged: true, patch: "", additions: 1, deletions: 0 }]
    });
    apiClientMock.publishPersonalWorkspace.mockReturnValueOnce(new Promise(() => {}));

    const view = render(GitChangesPanel, {
      props: { workspaceId: "wrk_1234567890abcdef", personalWorkspaceId: "psw_default", apiBaseUrl: "http://api", canWrite: true },
      global: { plugins: [createPinia()] }
    });

    expect(await view.findByText("selected.ts")).toBeTruthy();
    await fireEvent.update(view.getByPlaceholderText("输入提交说明。首行为主题，空行后为详细描述..."), "fix: remote");
    await fireEvent.click(view.getByRole("button", { name: "提交并推送" }));

    await waitFor(() => expect(apiClientMock.connectAgentConfigProgress).toHaveBeenCalled());
    progressHandler?.({
      currentStep: "COMMIT_LOCAL",
      command: "git -C /repo commit -m fix: remote",
      status: "RUNNING"
    });

    expect(await view.findByText("git -C /repo commit -m fix: remote")).toBeTruthy();
    expect(view.getByText("RUNNING")).toBeTruthy();
    expect(apiClientMock.publishPersonalWorkspace.mock.calls[0][1].operationId).toMatch(/^aco_/);
  });

  it("keeps the latest live git command when publish result contains command history", async () => {
    let progressHandler: ((event: { currentStep?: string; command?: string; status?: string }) => void) | undefined;
    let resolvePublish: ((value: unknown) => void) | undefined;
    apiClientMock.connectAgentConfigProgress.mockImplementationOnce(async (_operationId: string, handler: typeof progressHandler) => {
      progressHandler = handler;
      return { close: vi.fn() };
    });
    apiClientMock.getWorkspaceGitDiff.mockResolvedValue({
      files: [{ path: "src/selected.ts", status: "modified", rawStatus: "M ", staged: true, patch: "", additions: 1, deletions: 0 }]
    });
    apiClientMock.publishPersonalWorkspace.mockReturnValueOnce(new Promise((resolve) => {
      resolvePublish = resolve;
    }));

    const view = render(GitChangesPanel, {
      props: { workspaceId: "wrk_1234567890abcdef", personalWorkspaceId: "psw_default", apiBaseUrl: "http://api", canWrite: true },
      global: { plugins: [createPinia()] }
    });

    expect(await view.findByText("selected.ts")).toBeTruthy();
    await fireEvent.update(view.getByPlaceholderText("输入提交说明。首行为主题，空行后为详细描述..."), "fix: remote");
    await fireEvent.click(view.getByRole("button", { name: "提交并推送" }));

    await waitFor(() => expect(apiClientMock.connectAgentConfigProgress).toHaveBeenCalled());
    progressHandler?.({
      currentStep: "COMMIT_LOCAL",
      command: "git -C /repo commit -m fix: remote",
      status: "RUNNING"
    });
    progressHandler?.({
      currentStep: "PUSH_REMOTE",
      command: "git -C /repo push origin feature_test",
      status: "RUNNING"
    });
    resolvePublish?.({
      status: "MERGED",
      personalWorkspaceId: "psw_default",
      versionId: "awv_1",
      conflictFiles: [],
      message: "合并成功",
      remotePushed: true,
      currentStep: "COMPLETED",
      executedCommands: [
        "git -C /repo fetch origin",
        "git -C /repo commit -m fix: remote",
        "git -C /repo push origin feature_test"
      ],
      headCommit: "commit_merged"
    });

    expect(await view.findByText("git -C /repo push origin feature_test")).toBeTruthy();
    expect(view.queryByText("git -C /repo fetch origin")).toBeNull();
    expect(view.queryByText("git -C /repo commit -m fix: remote")).toBeNull();
  });

  it("does not regress completed publish progress when a late running command event arrives", async () => {
    let progressHandler: ((event: { currentStep?: string; command?: string; status?: string }) => void) | undefined;
    let resolvePublish: ((value: unknown) => void) | undefined;
    apiClientMock.connectAgentConfigProgress.mockImplementationOnce(async (_operationId: string, handler: typeof progressHandler) => {
      progressHandler = handler;
      return { close: vi.fn() };
    });
    apiClientMock.getWorkspaceGitDiff
      .mockResolvedValueOnce({
        files: [{ path: "src/selected.ts", status: "modified", staged: false, patch: "", additions: 1, deletions: 0 }]
      })
      .mockResolvedValueOnce({
        files: [{ path: "src/selected.ts", status: "modified", rawStatus: "M ", staged: true, patch: "", additions: 1, deletions: 0 }]
      })
      .mockResolvedValue({ files: [] });
    apiClientMock.publishPersonalWorkspace.mockReturnValueOnce(new Promise((resolve) => {
      resolvePublish = resolve;
    }));

    const view = render(GitChangesPanel, {
      props: { workspaceId: "wrk_1234567890abcdef", personalWorkspaceId: "psw_default", apiBaseUrl: "http://api", canWrite: true },
      global: { plugins: [createPinia()] }
    });

    expect(await view.findByText("selected.ts")).toBeTruthy();
    await fireEvent.click(view.getByTitle("暂存文件"));
    await fireEvent.update(view.getByPlaceholderText("输入提交说明。首行为主题，空行后为详细描述..."), "fix: remote");
    await fireEvent.click(view.getByRole("button", { name: "提交并推送" }));
    await waitFor(() => expect(apiClientMock.connectAgentConfigProgress).toHaveBeenCalled());
    progressHandler?.({
      currentStep: "PUSH_REMOTE",
      command: "git -C /repo push origin feature_test",
      status: "RUNNING"
    });

    resolvePublish?.({
      status: "MERGED",
      personalWorkspaceId: "psw_default",
      versionId: "awv_1",
      conflictFiles: [],
      message: "合并成功",
      remotePushed: true,
      currentStep: "COMPLETED",
      executedCommands: ["git -C /repo push origin feature_test"],
      headCommit: "commit_merged"
    });
    await waitFor(() => expect(view.getAllByText("SUCCEEDED")).toHaveLength(4));

    progressHandler?.({
      currentStep: "PUSH_REMOTE",
      command: "git -C /repo rev-parse HEAD",
      status: "RUNNING"
    });

    await waitFor(() => expect(view.getAllByText("SUCCEEDED")).toHaveLength(4));
    expect(view.queryByText("RUNNING")).toBeNull();
    expect(view.queryByText("git -C /repo rev-parse HEAD")).toBeNull();
  });

  it("opens the three-way editor for a conflict row", async () => {
    apiClientMock.getWorkspaceGitDiff.mockResolvedValue({
      files: [{ path: "src/conflict.ts", status: "conflict", rawStatus: "UU", staged: true, patch: "", additions: 0, deletions: 0 }]
    });
    const view = render(GitChangesPanel, {
      props: { workspaceId: "wrk_1234567890abcdef", personalWorkspaceId: "psw_default", apiBaseUrl: "http://api", canWrite: true },
      global: { plugins: [createPinia()] }
    });

    await fireEvent.click(await view.findByText("conflict.ts"));

    await waitFor(() => expect(apiClientMock.getWorkspaceGitConflict)
      .toHaveBeenCalledWith("wrk_1234567890abcdef", "src/conflict.ts"));
    expect(await view.findByText("合并编辑器")).toBeTruthy();
    expect(await view.findByText("当前个人版本")).toBeTruthy();
    expect(await view.findByText("应用版本")).toBeTruthy();
    expect(await view.findByText("合并结果（可编辑）")).toBeTruthy();
  });

  it("stages and unstages a regular file while a conflict remains", async () => {
    const conflict = {
      path: "src/conflict.ts",
      status: "conflict",
      rawStatus: "UU",
      staged: true,
      patch: "",
      additions: 0,
      deletions: 0
    };
    const unstaged = {
      path: "src/very-long-regular-change-name.ts",
      status: "untracked",
      rawStatus: "??",
      staged: false,
      patch: "",
      additions: 1,
      deletions: 0
    };
    const staged = { ...unstaged, status: "added", rawStatus: "A ", staged: true };
    apiClientMock.getWorkspaceGitDiff
      .mockResolvedValueOnce({ files: [conflict, unstaged] })
      .mockResolvedValueOnce({ files: [conflict, staged] })
      .mockResolvedValueOnce({ files: [conflict, unstaged] });

    const view = render(GitChangesPanel, {
      props: {
        workspaceId: "wrk_1234567890abcdef",
        personalWorkspaceId: "psw_default",
        apiBaseUrl: "http://api",
        canWrite: true
      },
      global: { plugins: [createPinia()] }
    });

    const fileName = await view.findByText(unstaged.path.split("/").pop() || unstaged.path);
    expect(fileName.parentElement?.getAttribute("title")).toBe(unstaged.path);
    await fireEvent.click(view.getByTitle("暂存文件"));
    await waitFor(() => expect(apiClientMock.stageWorkspaceGitFiles)
      .toHaveBeenCalledWith("wrk_1234567890abcdef", [unstaged.path]));
    const stagedName = await view.findByText(unstaged.path.split("/").pop() || unstaged.path);
    const unstage = stagedName.parentElement?.querySelector<HTMLButtonElement>('button[title="取消暂存"]');
    expect(unstage).toBeTruthy();
    if (!unstage) throw new Error("取消暂存按钮不存在");
    await waitFor(() => expect(unstage.disabled).toBe(false));
    await fireEvent.click(unstage);
    await waitFor(() => expect(apiClientMock.unstageWorkspaceGitFiles)
      .toHaveBeenCalledWith("wrk_1234567890abcdef", [unstaged.path]));
    expect((view.getByRole("button", { name: "提交并推送" }) as HTMLButtonElement).disabled).toBe(true);
  });

  it("emits the already loaded vcs diff when opening a workspace file", async () => {
    const file = {
      path: "src/fast-open.ts",
      status: "modified",
      rawStatus: " M",
      staged: false,
      patch: "@@ -1 +1 @@\n-old\n+new",
      additions: 1,
      deletions: 1
    };
    apiClientMock.getWorkspaceGitDiff.mockResolvedValue({ files: [file] });
    const view = render(GitChangesPanel, {
      props: { workspaceId: "wrk_1234567890abcdef", apiBaseUrl: "http://api", canWrite: true },
      global: { plugins: [createPinia()] }
    });

    await fireEvent.click(await view.findByText(file.path.split("/").pop() || file.path));

    expect(view.emitted("openDiff")).toEqual([[{
      path: file.path,
      source: "vcs",
      file: {
        path: file.path,
        status: file.status,
        rawStatus: file.rawStatus,
        patch: file.patch,
        additions: file.additions,
        deletions: file.deletions
      }
    }]]);
  });

  it("resolves all conflicts with one native side selection", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true);
    apiClientMock.getWorkspaceGitDiff
      .mockResolvedValueOnce({
        files: [
          { path: "冲突-A.md", status: "conflict", rawStatus: "UD", staged: true, patch: "", additions: 0, deletions: 0 },
          { path: "冲突-B.md", status: "conflict", rawStatus: "AU", staged: true, patch: "", additions: 0, deletions: 0 }
        ]
      })
      .mockResolvedValue({ files: [] });
    const view = render(GitChangesPanel, {
      props: { workspaceId: "wrk_1234567890abcdef", personalWorkspaceId: "psw_default", apiBaseUrl: "http://api", canWrite: true },
      global: { plugins: [createPinia()] }
    });

    await fireEvent.click(await view.findByRole("button", { name: /保留个人/ }));

    await waitFor(() => expect(apiClientMock.resolveAllWorkspaceGitConflicts)
      .toHaveBeenCalledWith("wrk_1234567890abcdef", { resolution: "CURRENT" }));
  });

  it("opens publish progress immediately without blocking on preview", async () => {
    apiClientMock.getWorkspaceGitDiff
      .mockResolvedValueOnce({
        files: [{ path: "src/selected.ts", status: "modified", staged: false, patch: "", additions: 1, deletions: 0 }]
      })
      .mockResolvedValue({
        files: [{ path: "src/selected.ts", status: "modified", staged: true, patch: "", additions: 1, deletions: 0 }]
      });
    apiClientMock.publishPersonalWorkspace.mockReturnValueOnce(new Promise(() => {}));
    const view = render(GitChangesPanel, {
      props: { workspaceId: "wrk_1234567890abcdef", personalWorkspaceId: "psw_default", apiBaseUrl: "http://api", canWrite: true },
      global: { plugins: [createPinia()] }
    });

    await fireEvent.click(await view.findByTitle("暂存文件"));
    await fireEvent.update(view.getByPlaceholderText("输入提交说明。首行为主题，空行后为详细描述..."), "fix: publish");
    await fireEvent.click(view.getByRole("button", { name: "提交并推送" }));

    expect(await view.findByText("提交并推送进度")).toBeTruthy();
    expect(apiClientMock.previewPersonalWorkspacePublish).not.toHaveBeenCalled();
    await waitFor(() => expect(apiClientMock.publishPersonalWorkspace).toHaveBeenCalledWith("psw_default", expect.objectContaining({
      commitMessage: "fix: publish",
      files: ["src/selected.ts"],
      operationId: expect.stringMatching(/^aco_/)
    })));
    expect(apiClientMock.publishPersonalWorkspace.mock.calls[0][1]).not.toHaveProperty("expectedApplicationHead");
  });
});
