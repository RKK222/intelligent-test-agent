import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, waitFor, within } from "@testing-library/vue";
import { createPinia } from "pinia";
import { BackendApiError } from "@test-agent/backend-api";
import GitChangesPanel from "../src/components/GitChangesPanel.vue";
import { useWorkbenchStore } from "@test-agent/workbench-shell";
import { applicationWorkspaceRestrictionsFixture as fixture } from "../../../tests/fixtures/application-workspace-restrictions";

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
  completeWorkspaceGitMerge: vi.fn(),
  getPublicAgentGitConflict: vi.fn(),
  resolvePublicAgentGitConflict: vi.fn(),
  resolveAllPublicAgentGitConflicts: vi.fn(),
  abortPublicAgentGitConflict: vi.fn(),
  getPublicAgentDiff: vi.fn(),
  getWorkspaceAgentDiff: vi.fn(),
  stagePublicAgentFiles: vi.fn(),
  stageWorkspaceAgentFiles: vi.fn(),
  unstagePublicAgentFiles: vi.fn(),
  unstageWorkspaceAgentFiles: vi.fn(),
  discardPublicAgentFiles: vi.fn(),
  discardWorkspaceAgentFiles: vi.fn(),
  commitPublicAgentConfig: vi.fn(),
  commitWorkspaceAgentConfig: vi.fn(),
  commitPersonalWorkspace: vi.fn(),
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
    apiClientMock.completeWorkspaceGitMerge.mockResolvedValue({
      status: "MERGED",
      headCommit: "personal_merge_head",
      applicationTargetCommit: "feature_target"
    });
    apiClientMock.getPublicAgentDiff.mockResolvedValue({ files: [] });
    apiClientMock.getWorkspaceAgentDiff.mockResolvedValue({ files: [] });
    apiClientMock.discardPublicAgentFiles.mockResolvedValue(undefined);
    apiClientMock.discardWorkspaceAgentFiles.mockResolvedValue(undefined);
    apiClientMock.commitPersonalWorkspace.mockResolvedValue({
      status: "LOCAL_COMMITTED",
      personalWorkspaceId: "psw_default",
      versionId: "awv_1",
      conflictFiles: [],
      message: "个人 worktree 已提交",
      remotePushed: false,
      headCommit: "personal_head"
    });
    apiClientMock.publishPersonalWorkspace.mockResolvedValue({
      status: "PUBLISHED",
      personalWorkspaceId: "psw_default",
      versionId: "awv_1",
      conflictFiles: [],
      message: "已从个人 HEAD 投影并推送 feature 分支",
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
    apiClientMock.getPublicAgentGitConflict.mockResolvedValue({
      path: "opencode/agents/public-review.md",
      rawStatus: "UU",
      baseContent: "base",
      currentContent: "local",
      incomingContent: "remote",
      resultContent: ""
    });
    apiClientMock.resolvePublicAgentGitConflict.mockResolvedValue(undefined);
    apiClientMock.resolveAllPublicAgentGitConflicts.mockResolvedValue(undefined);
    apiClientMock.abortPublicAgentGitConflict.mockResolvedValue(undefined);
    apiClientMock.connectAgentConfigProgress.mockResolvedValue({ close: vi.fn() });
    apiClientMock.commitPublicAgentConfig.mockResolvedValue({ status: "SUCCEEDED", currentStep: "COMPLETED" });
    apiClientMock.commitWorkspaceAgentConfig.mockResolvedValue({ status: "SUCCEEDED", currentStep: "COMPLETED" });
    apiClientMock.publishPublicAgentConfig.mockResolvedValue({ status: "SUCCEEDED", currentStep: "COMPLETED" });
    apiClientMock.publishWorkspaceAgentConfig.mockResolvedValue({ status: "SUCCEEDED", currentStep: "COMPLETED" });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    vi.restoreAllMocks();
  });

  it("refreshes public Agent diff when a saved Agent revision changes", async () => {
    apiClientMock.getPublicAgentDiff
      .mockResolvedValueOnce({ files: [] })
      .mockResolvedValue({
        files: [{
          path: "opencode/agents/public-review.md",
          status: "M",
          rawStatus: " M",
          staged: false,
          patch: "@@ -1 +1 @@\n-old\n+new"
        }]
      });
    const pinia = createPinia();
    const workbench = useWorkbenchStore(pinia);
    workbench.publicWorktree = {
      worktreeId: "agw_public",
      scope: "PUBLIC",
      workspaceId: null,
      linuxServerId: "linux-1",
      worktreeName: "public-usr_admin",
      branch: "public-usr_admin",
      rootPath: "/data/public-usr_admin",
      agentDirectory: "/data/public-usr_admin/opencode",
      status: "ACTIVE",
      createdAt: "2026-07-17T00:00:00Z",
      updatedAt: "2026-07-17T00:00:00Z"
    };
    const view = render(GitChangesPanel, {
      props: {
        apiBaseUrl: "http://api",
        canWrite: true,
        canManagePublicConfig: true,
        agentConfigRevision: 0
      },
      global: { plugins: [pinia] }
    });
    await waitFor(() => expect(apiClientMock.getPublicAgentDiff).toHaveBeenCalledTimes(1));

    await view.rerender({
      apiBaseUrl: "http://api",
      canWrite: true,
      canManagePublicConfig: true,
      agentConfigRevision: 1
    });

    expect(await view.findByText("public-review.md", { exact: false })).toBeTruthy();
    expect(apiClientMock.getPublicAgentDiff).toHaveBeenCalledTimes(2);
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
        },
        {
          path: ".opencode/skills/leaked-from-personal-worktree/SKILL.md",
          status: "untracked",
          staged: false,
          patch: "+wrong-scope",
          additions: 1,
          deletions: 0
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
    expect(view.queryByText("leaked-from-personal-worktree", { exact: false })).toBeNull();
    expect(view.queryByText("payment-test.md", { exact: false })).toBeNull();
    await fireEvent.click(view.getByRole("tab", { name: /^应用Agent/ }));
    expect(await view.findByText("payment-test.md", { exact: false })).toBeTruthy();
    expect(await view.findByText("SKILL.md", { exact: false })).toBeTruthy();
    expect(view.queryByText("登录测试.md")).toBeNull();
    expect(view.queryByText("F-COSS/workspace/02-设计/Test Material.md", { exact: false })).toBeNull();
    expect(view.queryByText("[公共]", { exact: false })).toBeNull();
    expect(view.queryByText("opencode/agents/public_agent_test.json", { exact: false })).toBeNull();
  });

  it("switches among workspace, application Agent/Skill, and public Agent scopes without mixing files", async () => {
    apiClientMock.getWorkspaceGitDiff.mockResolvedValue({
      files: [
        { path: fixture.files.docs, status: "modified", staged: false, patch: "" },
        { path: fixture.files.spec, status: "added", staged: false, patch: "" }
      ]
    });
    apiClientMock.getWorkspaceAgentDiff.mockResolvedValue({
      files: [{ path: fixture.files.applicationSkill, status: "M", staged: false, patch: "" }]
    });
    apiClientMock.getPublicAgentDiff.mockResolvedValue({
      files: [{ path: fixture.files.publicAgent, status: "M", staged: false, patch: "" }]
    });

    const view = render(GitChangesPanel, {
      props: {
        workspaceId: fixture.application.personalRuntimeWorkspaceId,
        apiBaseUrl: "http://api",
        canWrite: true,
        canManageAgentConfig: true,
        canManagePublicConfig: true
      },
      global: { plugins: [createPinia()] }
    });

    expect(await view.findByText("publish-guide.md", { exact: false })).toBeTruthy();
    await waitFor(() => {
      const events = view.emitted("changes-refreshed") as unknown as Array<[{ totalCount?: number }]>;
      expect(events.at(-1)?.[0]).toMatchObject({ totalCount: 4 });
    });
    expect(view.queryByText("SKILL.md", { exact: false })).toBeNull();
    expect(view.queryByText("public-review.md", { exact: false })).toBeNull();
    expect(view.container.querySelector(".git-scope-meta")).toBeNull();
    expect(view.container.querySelector(".git-sub-header")).toBeNull();

    await fireEvent.click(view.getByRole("tab", { name: /^应用Agent/ }));
    expect(await view.findByText("SKILL.md", { exact: false })).toBeTruthy();
    expect(view.queryByText("publish-guide.md", { exact: false })).toBeNull();
    expect(view.queryByText("public-review.md", { exact: false })).toBeNull();
    expect(view.container.querySelector(".git-scope-meta")).toBeNull();
    expect(view.container.querySelector(".git-sub-header")).toBeNull();

    await fireEvent.click(view.getByRole("tab", { name: /^公共Agent/ }));
    expect(await view.findByText("public-review.md", { exact: false })).toBeTruthy();
    expect(view.queryByText("publish-guide.md", { exact: false })).toBeNull();
    expect(view.queryByText("SKILL.md", { exact: false })).toBeNull();
    expect(view.container.querySelector(".git-scope-meta")).toBeNull();
    expect(view.container.querySelector(".git-sub-header")).toBeNull();
  });

  it("commits only the selected Agent scope when workspace files are also staged", async () => {
    apiClientMock.getWorkspaceGitDiff.mockResolvedValue({
      files: [{ path: fixture.files.docs, status: "modified", staged: true, patch: "" }]
    });
    apiClientMock.getWorkspaceAgentDiff
      .mockResolvedValueOnce({
        files: [{ path: fixture.files.applicationSkill, status: "M", staged: true, patch: "" }]
      })
      .mockResolvedValue({ files: [] });

    const view = render(GitChangesPanel, {
      props: {
        workspaceId: fixture.application.personalRuntimeWorkspaceId,
        agentConfigWorkspaceId: fixture.application.personalRuntimeWorkspaceId,
        personalWorkspaceId: fixture.application.personalWorkspaceId,
        personalWorkspaceBranch: fixture.application.personalBranch,
        apiBaseUrl: "http://api",
        canWrite: true,
        canManageAgentConfig: true,
        canManagePublicConfig: true
      },
      global: { plugins: [createPinia()] }
    });

    await view.findByText("publish-guide.md", { exact: false });
    await fireEvent.click(view.getByRole("tab", { name: /^应用Agent/ }));
    await fireEvent.update(view.getByPlaceholderText("输入提交说明。首行为主题，空行后为详细描述..."), "agent: 只提交应用配置");
    await fireEvent.click(view.getByRole("button", { name: "提交" }));

    await waitFor(() => expect(apiClientMock.commitPersonalWorkspace).toHaveBeenCalledWith(
      fixture.application.personalWorkspaceId,
      expect.objectContaining({
        commitMessage: "agent: 只提交应用配置",
        files: [`.opencode/${fixture.files.applicationSkill}`]
      })
    ));
    expect(apiClientMock.commitWorkspaceAgentConfig).not.toHaveBeenCalled();
    expect(apiClientMock.getWorkspaceAgentDiff).toHaveBeenCalledWith(fixture.application.personalRuntimeWorkspaceId);
  });

  it("shows public Agent changes and uses the shared conflict interaction", async () => {
    apiClientMock.getPublicAgentDiff.mockResolvedValue({
      files: [
        {
          path: "opencode/agents/public-review.md",
          status: "modified",
          rawStatus: " M",
          staged: false,
          patch: "@@ -1 +1 @@\n-old\n+new"
        },
        {
          path: "opencode/agents/public-conflict.md",
          status: "conflict",
          rawStatus: "UU",
          staged: false,
          patch: ""
        }
      ]
    });
    const pinia = createPinia();
    const workbench = useWorkbenchStore(pinia);
    workbench.publicWorktree = {
      worktreeId: "agw_public",
      scope: "PUBLIC",
      workspaceId: null,
      linuxServerId: "linux-1",
      worktreeName: "public-usr_admin",
      branch: "public-usr_admin",
      rootPath: "/data/public-usr_admin",
      agentDirectory: "/data/public-usr_admin/opencode",
      status: "ACTIVE",
      createdAt: "2026-07-17T00:00:00Z",
      updatedAt: "2026-07-17T00:00:00Z"
    };

    const view = render(GitChangesPanel, {
      props: {
        workspaceId: "wrk_1234567890abcdef",
        apiBaseUrl: "http://api",
        canWrite: true,
        canManagePublicConfig: true
      },
      global: { plugins: [pinia] }
    });

    expect(await view.findByText("public-review.md", { exact: false })).toBeTruthy();
    expect((await view.findAllByText("[公共]", { exact: false })).length).toBeGreaterThan(0);
    expect(await view.findByText("检测到 1 个公共 Agent 冲突")).toBeTruthy();
    await fireEvent.click(view.getByText("public-conflict.md", { exact: false }));
    await waitFor(() => expect(apiClientMock.getPublicAgentGitConflict).toHaveBeenCalledWith(
      "opencode/agents/public-conflict.md",
      "agw_public",
      "linux-1"
    ));
    expect(await view.findByText("合并编辑器")).toBeTruthy();
  });

  it("commits and publishes staged public Agent files through the shared progress dialog", async () => {
    apiClientMock.getPublicAgentDiff.mockResolvedValue({
      files: [{
        path: "opencode/agents/public-review.md",
        status: "modified",
        rawStatus: "M ",
        staged: true,
        patch: "@@ -1 +1 @@\n-old\n+new"
      }]
    });
    const pinia = createPinia();
    const workbench = useWorkbenchStore(pinia);
    workbench.publicWorktree = {
      worktreeId: "agw_public",
      scope: "PUBLIC",
      workspaceId: null,
      linuxServerId: "linux-1",
      worktreeName: "public-usr_admin",
      branch: "public-usr_admin",
      rootPath: "/data/public-usr_admin",
      agentDirectory: "/data/public-usr_admin/opencode",
      status: "ACTIVE",
      createdAt: "2026-07-17T00:00:00Z",
      updatedAt: "2026-07-17T00:00:00Z"
    };
    const view = render(GitChangesPanel, {
      props: {
        workspaceId: "wrk_1234567890abcdef",
        apiBaseUrl: "http://api",
        canWrite: true,
        canManagePublicConfig: true
      },
      global: { plugins: [pinia] }
    });

    expect(await view.findByText("public-review.md", { exact: false })).toBeTruthy();
    await fireEvent.update(view.getByPlaceholderText("输入提交说明。首行为主题，空行后为详细描述..."), "更新公共 Agent");
    await fireEvent.click(view.getByRole("button", { name: "提交并推送" }));

    await waitFor(() => expect(apiClientMock.commitPublicAgentConfig).toHaveBeenCalledWith(expect.objectContaining({
      message: "更新公共 Agent",
      worktreeId: "agw_public"
    })));
    await waitFor(() => expect(apiClientMock.publishPublicAgentConfig).toHaveBeenCalledWith(
      "agw_public",
      expect.stringMatching(/^aco_/)
    ));
    expect(await view.findByText("提交并推送进度")).toBeTruthy();
  });

  it("allows closing the progress dialog while public Agent publish continues", async () => {
    let finishPublish: ((value: { status: string; currentStep: string }) => void) | undefined;
    apiClientMock.publishPublicAgentConfig.mockImplementationOnce(() => new Promise((resolve) => {
      finishPublish = resolve;
    }));
    apiClientMock.getPublicAgentDiff.mockResolvedValue({
      files: [{
        path: "opencode/agents/public-review.md",
        status: "modified",
        rawStatus: "M ",
        staged: true,
        patch: "@@ -1 +1 @@\n-old\n+new"
      }]
    });
    const pinia = createPinia();
    const workbench = useWorkbenchStore(pinia);
    workbench.publicWorktree = {
      worktreeId: "agw_public",
      scope: "PUBLIC",
      workspaceId: null,
      linuxServerId: "linux-1",
      worktreeName: "public-usr_admin",
      branch: "public-usr_admin",
      rootPath: "/data/public-usr_admin",
      agentDirectory: "/data/public-usr_admin/opencode",
      status: "ACTIVE",
      createdAt: "2026-07-17T00:00:00Z",
      updatedAt: "2026-07-17T00:00:00Z"
    };
    const view = render(GitChangesPanel, {
      props: {
        workspaceId: "wrk_1234567890abcdef",
        apiBaseUrl: "http://api",
        canWrite: true,
        canManagePublicConfig: true
      },
      global: { plugins: [pinia] }
    });

    await view.findByText("public-review.md", { exact: false });
    await fireEvent.update(view.getByPlaceholderText("输入提交说明。首行为主题，空行后为详细描述..."), "更新公共 Agent");
    await fireEvent.click(view.getByRole("button", { name: "提交并推送" }));
    await waitFor(() => expect(apiClientMock.publishPublicAgentConfig).toHaveBeenCalledTimes(1));

    expect(view.getByText("创建后台同步任务")).toBeTruthy();
    expect(view.getAllByRole("button", { name: "关闭" })
      .every((button) => !(button as HTMLButtonElement).disabled)).toBe(true);
    await fireEvent.click(view.getAllByRole("button", { name: "关闭" })[0]);
    expect(view.queryByText("提交并推送进度")).toBeNull();
    expect(view.getByText("正在发布公共 Agent 配置...")).toBeTruthy();

    finishPublish?.({ status: "SUCCEEDED", currentStep: "COMPLETED" });
    await waitFor(() => expect(view.getByText("提交并推送成功！")).toBeTruthy());
  });

  it("shows that public Agent local commit was retained when remote publish is rejected", async () => {
    apiClientMock.publishPublicAgentConfig.mockRejectedValueOnce(new BackendApiError(409, {
      success: false,
      code: "GIT_UNAVAILABLE",
      message: "Git 远端拒绝推送",
      traceId: "trace_publish_rejected",
      details: {
        failedStep: "PUSHING",
        gitFailureHint: "远端拒绝接收提交，请确认提交人已在企业 Git 平台登记"
      }
    }));
    apiClientMock.getPublicAgentDiff
      .mockResolvedValueOnce({
        files: [{
          path: "opencode/opencode.jsonc",
          status: "modified",
          rawStatus: "M ",
          staged: true,
          patch: "@@ -1 +1 @@\n-old\n+new"
        }]
      })
      .mockResolvedValue({ files: [] });
    const pinia = createPinia();
    const workbench = useWorkbenchStore(pinia);
    workbench.publicWorktree = {
      worktreeId: "agw_public",
      scope: "PUBLIC",
      workspaceId: null,
      linuxServerId: "linux-114",
      worktreeName: "public-001177621",
      branch: "public-001177621",
      rootPath: "/data/testagent/data/agent-opencode/.configdev/public-001177621",
      agentDirectory: "/data/testagent/data/agent-opencode/.configdev/public-001177621/opencode",
      status: "ACTIVE",
      createdAt: "2026-07-22T00:00:00Z",
      updatedAt: "2026-07-22T00:00:00Z"
    };
    const view = render(GitChangesPanel, {
      props: {
        workspaceId: "wrk_1234567890abcdef",
        apiBaseUrl: "http://api",
        canWrite: true,
        canManagePublicConfig: true
      },
      global: { plugins: [pinia] }
    });

    await view.findByText("opencode.jsonc", { exact: false });
    await fireEvent.update(view.getByPlaceholderText("输入提交说明。首行为主题，空行后为详细描述..."), "更新公共 Agent");
    await fireEvent.click(view.getByRole("button", { name: "提交并推送" }));

    expect(await view.findByText(/个人 worktree 已完成本地提交，但远端公共仓库及其他服务器尚未更新/)).toBeTruthy();
    expect(view.getByText(/远端拒绝接收提交/)).toBeTruthy();
    expect(view.getByText("执行失败")).toBeTruthy();
    expect(view.queryByText("提交并推送成功！")).toBeNull();
    await waitFor(() => expect(view.getByText("待推送")).toBeTruthy());
    await fireEvent.click(view.getByRole("button", { name: "重新推送" }));
    await waitFor(() => expect(apiClientMock.publishPublicAgentConfig).toHaveBeenCalledTimes(2));
    expect(apiClientMock.commitPublicAgentConfig).toHaveBeenCalledTimes(1);
  });

  it("restores the public Agent retry action from backend state after page reload", async () => {
    apiClientMock.getPublicAgentDiff
      .mockResolvedValueOnce({ files: [], publishPending: true })
      .mockResolvedValue({ files: [], publishPending: false });
    const pinia = createPinia();
    const workbench = useWorkbenchStore(pinia);
    workbench.publicWorktree = {
      worktreeId: "agw_public_pending",
      scope: "PUBLIC",
      workspaceId: null,
      linuxServerId: "linux-114",
      worktreeName: "public-001177621",
      branch: "public-001177621",
      rootPath: "/data/testagent/data/agent-opencode/.configdev/public-001177621",
      agentDirectory: "/data/testagent/data/agent-opencode/.configdev/public-001177621/opencode",
      status: "ACTIVE",
      createdAt: "2026-07-22T00:00:00Z",
      updatedAt: "2026-07-22T00:00:00Z"
    };
    const view = render(GitChangesPanel, {
      props: {
        apiBaseUrl: "http://api",
        canWrite: true,
        canManagePublicConfig: true
      },
      global: { plugins: [pinia] }
    });

    expect(await view.findByText(/个人 worktree 中存在已完成的本地提交/)).toBeTruthy();
    await fireEvent.click(view.getByRole("button", { name: "重新推送" }));

    await waitFor(() => expect(apiClientMock.publishPublicAgentConfig).toHaveBeenCalledWith(
      "agw_public_pending",
      expect.stringMatching(/^aco_/)
    ));
    expect(apiClientMock.commitPublicAgentConfig).not.toHaveBeenCalled();
    await waitFor(() => expect(view.queryByRole("button", { name: "重新推送" })).toBeNull());
  });

  it("discards an unstaged application Agent file and reloads its open editor route", async () => {
    apiClientMock.getWorkspaceAgentDiff
      .mockResolvedValueOnce({
        files: [{
          path: "agents/application-review.md",
          status: "modified",
          rawStatus: " M",
          staged: false,
          patch: "@@ -1 +1 @@\n-old\n+new"
        }]
      })
      .mockResolvedValueOnce({ files: [] });
    const agentFilesDiscarded = vi.fn();
    const view = render(GitChangesPanel, {
      props: {
        workspaceId: "wrk_personal_runtime",
        agentConfigWorkspaceId: "wrk_application_feature",
        apiBaseUrl: "http://api",
        canWrite: true,
        canManageAgentConfig: true,
        "onAgent-files-discarded": agentFilesDiscarded
      },
      global: { plugins: [createPinia()] }
    });

    await fireEvent.click(view.getByRole("tab", { name: /^应用Agent/ }));
    const row = await view.findByLabelText("agents/application-review.md");
    await fireEvent.click(within(row).getByTitle("回退文件改动"));

    await waitFor(() => expect(apiClientMock.discardWorkspaceAgentFiles).toHaveBeenCalledWith(
      "wrk_application_feature",
      ["agents/application-review.md"]
    ));
    await waitFor(() => expect(agentFilesDiscarded).toHaveBeenCalledWith({
      scope: "WORKSPACE",
      paths: ["agents/application-review.md"]
    }));
    await waitFor(() => expect(view.queryByLabelText("agents/application-review.md")).toBeNull());
  });

  it("discards all staged and unstaged public Agent files from the current personal worktree", async () => {
    apiClientMock.getPublicAgentDiff
      .mockResolvedValueOnce({
        files: [
          { path: "opencode/agents/public-review.md", status: "modified", rawStatus: " M", staged: false, patch: "unstaged" },
          { path: "opencode/skills/public-case/SKILL.md", status: "modified", rawStatus: "M ", staged: true, patch: "staged" }
        ]
      })
      .mockResolvedValueOnce({ files: [] });
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(true);
    const pinia = createPinia();
    const workbench = useWorkbenchStore(pinia);
    workbench.publicWorktree = {
      worktreeId: "agw_public",
      scope: "PUBLIC",
      workspaceId: null,
      linuxServerId: "linux-1",
      worktreeName: "public-usr_admin",
      branch: "public-usr_admin",
      rootPath: "/data/public-usr_admin",
      agentDirectory: "/data/public-usr_admin/opencode",
      status: "ACTIVE",
      createdAt: "2026-07-17T00:00:00Z",
      updatedAt: "2026-07-17T00:00:00Z"
    };
    const view = render(GitChangesPanel, {
      props: {
        workspaceId: "wrk_personal_runtime",
        apiBaseUrl: "http://api",
        canWrite: true,
        canManagePublicConfig: true
      },
      global: { plugins: [pinia] }
    });

    await fireEvent.click(view.getByRole("tab", { name: /^公共Agent/ }));
    const discardAllButton = await view.findByRole("button", { name: "丢弃全部公共Agent改动" });
    await fireEvent.click(discardAllButton);

    expect(confirm).toHaveBeenCalledWith("将丢弃 公共 Agent 的 2 个文件改动，此操作无法撤销，是否继续？");
    await waitFor(() => expect(apiClientMock.discardPublicAgentFiles).toHaveBeenCalledWith(
      ["opencode/agents/public-review.md", "opencode/skills/public-case/SKILL.md"],
      "agw_public"
    ));
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

  it("stages all unstaged application workspace files in one request", async () => {
    apiClientMock.getWorkspaceGitDiff
      .mockResolvedValueOnce({
        files: [
          { path: "src/first.ts", status: "modified", staged: false, patch: "first", additions: 1, deletions: 0 },
          { path: "src/second.ts", status: "untracked", staged: false, patch: "second", additions: 1, deletions: 0 },
          { path: "src/already-staged.ts", status: "modified", staged: true, patch: "staged", additions: 1, deletions: 1 }
        ]
      })
      .mockResolvedValueOnce({
        files: [
          { path: "src/first.ts", status: "modified", staged: true, patch: "first", additions: 1, deletions: 0 },
          { path: "src/second.ts", status: "added", staged: true, patch: "second", additions: 1, deletions: 0 },
          { path: "src/already-staged.ts", status: "modified", staged: true, patch: "staged", additions: 1, deletions: 1 }
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

    const stageAllButton = await view.findByRole("button", { name: "全部暂存应用工作空间变更" });
    await fireEvent.click(stageAllButton);

    await waitFor(() => expect(apiClientMock.stageWorkspaceGitFiles).toHaveBeenCalledWith(
      "wrk_1234567890abcdef",
      ["src/first.ts", "src/second.ts"]
    ));
    await waitFor(() => expect((stageAllButton as HTMLButtonElement).disabled).toBe(true));
  });

  it("stages all unstaged application Agent files in one request", async () => {
    apiClientMock.getWorkspaceAgentDiff
      .mockResolvedValueOnce({
        files: [
          { path: "agents/payment-test.md", status: "modified", staged: false, patch: "first" },
          { path: "skills/payment-case-design/SKILL.md", status: "untracked", staged: false, patch: "second" },
          { path: "opencode.jsonc", status: "modified", staged: true, patch: "staged" }
        ]
      })
      .mockResolvedValueOnce({
        files: [
          { path: "agents/payment-test.md", status: "modified", staged: true, patch: "first" },
          { path: "skills/payment-case-design/SKILL.md", status: "added", staged: true, patch: "second" },
          { path: "opencode.jsonc", status: "modified", staged: true, patch: "staged" }
        ]
      });

    const view = render(GitChangesPanel, {
      props: {
        workspaceId: fixture.application.personalRuntimeWorkspaceId,
        agentConfigWorkspaceId: fixture.application.personalRuntimeWorkspaceId,
        apiBaseUrl: "http://api",
        canWrite: true,
        canManageAgentConfig: true
      },
      global: { plugins: [createPinia()] }
    });

    await fireEvent.click(await view.findByRole("tab", { name: /^应用Agent/ }));
    const stageAllButton = await view.findByRole("button", { name: "全部暂存应用 Agent 变更" });
    await fireEvent.click(stageAllButton);

    await waitFor(() => expect(apiClientMock.stageWorkspaceAgentFiles).toHaveBeenCalledWith(
      fixture.application.personalRuntimeWorkspaceId,
      ["agents/payment-test.md", "skills/payment-case-design/SKILL.md"]
    ));
    expect(apiClientMock.stageWorkspaceAgentFiles).toHaveBeenCalledTimes(1);
    await waitFor(() => expect((stageAllButton as HTMLButtonElement).disabled).toBe(true));
  });

  it("stages all unstaged public Agent files in one request", async () => {
    apiClientMock.getPublicAgentDiff
      .mockResolvedValueOnce({
        files: [
          { path: "opencode/agents/public-review.md", status: "modified", staged: false, patch: "first" },
          { path: "opencode/skills/public-case/SKILL.md", status: "untracked", staged: false, patch: "second" },
          { path: "opencode/agents/already-staged.md", status: "modified", staged: true, patch: "staged" }
        ]
      })
      .mockResolvedValueOnce({
        files: [
          { path: "opencode/agents/public-review.md", status: "modified", staged: true, patch: "first" },
          { path: "opencode/skills/public-case/SKILL.md", status: "added", staged: true, patch: "second" },
          { path: "opencode/agents/already-staged.md", status: "modified", staged: true, patch: "staged" }
        ]
      });
    const pinia = createPinia();
    const workbench = useWorkbenchStore(pinia);
    workbench.publicWorktree = {
      worktreeId: "agw_public",
      scope: "PUBLIC",
      workspaceId: null,
      linuxServerId: "linux-1",
      worktreeName: "public-usr_admin",
      branch: "public-usr_admin",
      rootPath: "/data/public-usr_admin",
      agentDirectory: "/data/public-usr_admin/opencode",
      status: "ACTIVE",
      createdAt: "2026-07-17T00:00:00Z",
      updatedAt: "2026-07-17T00:00:00Z"
    };

    const view = render(GitChangesPanel, {
      props: {
        workspaceId: fixture.application.personalRuntimeWorkspaceId,
        apiBaseUrl: "http://api",
        canWrite: true,
        canManagePublicConfig: true
      },
      global: { plugins: [pinia] }
    });

    await fireEvent.click(await view.findByRole("tab", { name: /^公共Agent/ }));
    const stageAllButton = await view.findByRole("button", { name: "全部暂存公共 Agent 变更" });
    await fireEvent.click(stageAllButton);

    await waitFor(() => expect(apiClientMock.stagePublicAgentFiles).toHaveBeenCalledWith(
      ["opencode/agents/public-review.md", "opencode/skills/public-case/SKILL.md"],
      "agw_public"
    ));
    expect(apiClientMock.stagePublicAgentFiles).toHaveBeenCalledTimes(1);
    await waitFor(() => expect((stageAllButton as HTMLButtonElement).disabled).toBe(true));
  });

  it("rolls all staged application workspace files back to unstaged in one request", async () => {
    apiClientMock.getWorkspaceGitDiff
      .mockResolvedValueOnce({
        files: [
          { path: "src/first-staged.ts", status: "modified", staged: true, patch: "first", additions: 1, deletions: 0 },
          { path: "src/second-staged.ts", status: "added", staged: true, patch: "second", additions: 1, deletions: 0 },
          { path: "src/already-unstaged.ts", status: "modified", staged: false, patch: "unstaged", additions: 1, deletions: 1 }
        ]
      })
      .mockResolvedValueOnce({
        files: [
          { path: "src/first-staged.ts", status: "modified", staged: false, patch: "first", additions: 1, deletions: 0 },
          { path: "src/second-staged.ts", status: "untracked", staged: false, patch: "second", additions: 1, deletions: 0 },
          { path: "src/already-unstaged.ts", status: "modified", staged: false, patch: "unstaged", additions: 1, deletions: 1 }
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

    const unstageAllButton = await view.findByRole("button", { name: "全部回退到未暂存" });
    await fireEvent.click(unstageAllButton);

    await waitFor(() => expect(apiClientMock.unstageWorkspaceGitFiles).toHaveBeenCalledWith(
      "wrk_1234567890abcdef",
      ["src/first-staged.ts", "src/second-staged.ts"]
    ));
    expect(apiClientMock.discardWorkspaceGitFiles).not.toHaveBeenCalled();
    await waitFor(() => expect((unstageAllButton as HTMLButtonElement).disabled).toBe(true));
  });

  it("discards all staged and unstaged application workspace files only from the unstaged group", async () => {
    apiClientMock.getWorkspaceGitDiff
      .mockResolvedValueOnce({
        files: [
          { path: "src/unstaged.ts", status: "modified", staged: false, patch: "unstaged", additions: 1, deletions: 1 },
          { path: "src/staged.ts", status: "modified", staged: true, patch: "staged", additions: 1, deletions: 1 }
        ]
      })
      .mockResolvedValueOnce({ files: [] });
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(true);
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

    const discardAllButton = await view.findByRole("button", { name: "丢弃全部应用工作空间改动" });
    await fireEvent.click(discardAllButton);

    expect(confirm).toHaveBeenCalledWith("将丢弃应用工作空间的 2 个文件改动，此操作无法撤销，是否继续？");
    await waitFor(() => expect(apiClientMock.discardWorkspaceGitFiles).toHaveBeenCalledWith(
      "wrk_1234567890abcdef",
      ["src/unstaged.ts", "src/staged.ts"]
    ));
    await waitFor(() => expect(changesRefreshed).toHaveBeenCalledWith(expect.objectContaining({
      paths: ["src/unstaged.ts", "src/staged.ts"]
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

  it("commits spec locally but excludes it from a mixed feature publish", async () => {
    apiClientMock.getWorkspaceGitDiff
      .mockResolvedValueOnce({
        files: [
          { path: "spec/payment/design.md", status: "added", rawStatus: "A ", staged: true, patch: "", additions: 1, deletions: 0 },
          { path: "docs/payment.md", status: "added", rawStatus: "A ", staged: true, patch: "", additions: 1, deletions: 0 }
        ]
      })
      .mockResolvedValue({ files: [] });

    const view = render(GitChangesPanel, {
      props: {
        workspaceId: "wrk_1234567890abcdef",
        personalWorkspaceId: "psw_default",
        apiBaseUrl: "http://api",
        canWrite: true
      },
      global: { plugins: [createPinia()] }
    });

    expect(await view.findByText("design.md")).toBeTruthy();
    expect(view.queryByText("应用工作空间")).toBeNull();
    expect(view.queryByText("普通文件、docs、spec")).toBeNull();
    expect(view.getByText("选择“提交并推送”时：提交 2 个文件、推送 1 个文件；其中 1 个 spec 文件只提交到个人 worktree。")).toBeTruthy();
    await fireEvent.update(view.getByPlaceholderText("输入提交说明。首行为主题，空行后为详细描述..."), "docs: 更新支付说明");
    await fireEvent.click(view.getByRole("button", { name: "提交并推送" }));

    await waitFor(() => expect(apiClientMock.commitPersonalWorkspace).toHaveBeenCalledWith("psw_default", expect.objectContaining({
      files: ["spec/payment/design.md", "docs/payment.md"]
    })));
    expect(apiClientMock.publishPersonalWorkspace).toHaveBeenCalledWith("psw_default", expect.objectContaining({
      files: ["docs/payment.md"]
    }));
    expect(await view.findByText("可发布文件已推送；1 个 spec 文件仅提交到个人 worktree。")).toBeTruthy();
    expect(view.getByLabelText("本轮累计结果").textContent).toContain("本地提交 2 个文件");
    expect(view.getByLabelText("本轮累计结果").textContent).toContain("远端推送 1 个文件");
    expect(view.getByLabelText("本轮累计结果").textContent).toContain("仅本地 1 个 spec 文件");
  });

  it("only commits when all selected workspace files are under spec", async () => {
    apiClientMock.getWorkspaceGitDiff
      .mockResolvedValueOnce({
        files: [
          { path: "spec/payment/design.md", status: "added", rawStatus: "A ", staged: true, patch: "", additions: 1, deletions: 0 }
        ]
      })
      .mockResolvedValue({ files: [] });

    const view = render(GitChangesPanel, {
      props: {
        workspaceId: "wrk_1234567890abcdef",
        personalWorkspaceId: "psw_default",
        apiBaseUrl: "http://api",
        canWrite: true
      },
      global: { plugins: [createPinia()] }
    });

    expect(await view.findByText("design.md")).toBeTruthy();
    expect(view.getByText("仅本地")).toBeTruthy();
    expect(view.getByText("1 个 spec 文件只提交到个人 worktree，不会推送。")).toBeTruthy();
    await fireEvent.update(view.getByPlaceholderText("输入提交说明。首行为主题，空行后为详细描述..."), "spec: 保存本地设计");
    expect(view.queryByRole("button", { name: "提交并推送" })).toBeNull();
    await fireEvent.click(view.getByRole("button", { name: "提交" }));

    await waitFor(() => expect(apiClientMock.commitPersonalWorkspace).toHaveBeenCalledWith("psw_default", expect.objectContaining({
      files: ["spec/payment/design.md"]
    })));
    expect(apiClientMock.publishPersonalWorkspace).not.toHaveBeenCalled();
    expect(await view.findByText("提交成功！")).toBeTruthy();
    expect(view.getByLabelText("本轮累计结果").textContent).toContain("本地提交 1 个文件");
    expect(view.getByLabelText("本轮累计结果").textContent).toContain("仅本地 1 个 spec 文件");
  });

  it("accumulates workspace, application Agent, and public Agent results in one batch", async () => {
    let workspaceFiles = [
      { path: "docs/payment.md", status: "added", rawStatus: "A ", staged: true, patch: "", additions: 1, deletions: 0 },
      { path: "spec/payment/design.md", status: "added", rawStatus: "A ", staged: true, patch: "", additions: 1, deletions: 0 }
    ];
    let applicationAgentFiles = [
      { path: "agents/payment-review.md", status: "M", rawStatus: "M ", staged: true, patch: "" }
    ];
    let publicAgentFiles = [
      { path: "opencode/agents/public-review.md", status: "M", rawStatus: "M ", staged: true, patch: "" }
    ];
    apiClientMock.getWorkspaceGitDiff.mockImplementation(async () => ({ files: workspaceFiles }));
    apiClientMock.getWorkspaceAgentDiff.mockImplementation(async () => ({ files: applicationAgentFiles }));
    apiClientMock.getPublicAgentDiff.mockImplementation(async () => ({ files: publicAgentFiles }));

    const pinia = createPinia();
    const workbench = useWorkbenchStore(pinia);
    workbench.publicWorktree = {
      worktreeId: "agw_public",
      scope: "PUBLIC",
      workspaceId: null,
      linuxServerId: "linux-1",
      worktreeName: "public-usr_admin",
      branch: "public-usr_admin",
      rootPath: "/data/public-usr_admin",
      agentDirectory: "/data/public-usr_admin/opencode",
      status: "ACTIVE",
      createdAt: "2026-07-17T00:00:00Z",
      updatedAt: "2026-07-17T00:00:00Z"
    };
    const view = render(GitChangesPanel, {
      props: {
        workspaceId: "wrk_1234567890abcdef",
        personalWorkspaceId: "psw_default",
        apiBaseUrl: "http://api",
        canWrite: true,
        canManageAgentConfig: true,
        canManagePublicConfig: true
      },
      global: { plugins: [pinia] }
    });

    expect(await view.findByText("payment.md", { exact: false })).toBeTruthy();
    workspaceFiles = [];
    await fireEvent.update(view.getByPlaceholderText("输入提交说明。首行为主题，空行后为详细描述..."), "docs: 提交应用文件");
    await fireEvent.click(view.getByRole("button", { name: "提交并推送" }));
    await waitFor(() => expect(apiClientMock.publishPersonalWorkspace).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(view.getByLabelText("本轮累计结果").textContent).toContain("本地提交 2 个文件"));
    await fireEvent.click(view.getByText("关闭", { selector: "button" }));

    await fireEvent.click(view.getByRole("tab", { name: /^应用Agent/ }));
    expect(await view.findByText("payment-review.md", { exact: false })).toBeTruthy();
    applicationAgentFiles = [];
    await fireEvent.update(view.getByPlaceholderText("输入提交说明。首行为主题，空行后为详细描述..."), "agent: 提交应用 Agent");
    await fireEvent.click(view.getByRole("button", { name: "提交并推送" }));
    await waitFor(() => expect(apiClientMock.publishPersonalWorkspace).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(view.getByLabelText("本轮累计结果").textContent).toContain("本地提交 3 个文件"));
    await fireEvent.click(view.getByText("关闭", { selector: "button" }));

    await fireEvent.click(view.getByRole("tab", { name: /^公共Agent/ }));
    expect(await view.findByText("public-review.md", { exact: false })).toBeTruthy();
    publicAgentFiles = [];
    await fireEvent.update(view.getByPlaceholderText("输入提交说明。首行为主题，空行后为详细描述..."), "agent: 提交公共 Agent");
    await fireEvent.click(view.getByRole("button", { name: "提交并推送" }));
    await waitFor(() => expect(apiClientMock.publishPublicAgentConfig).toHaveBeenCalledTimes(1));

    const summary = await view.findByLabelText("本轮累计结果");
    expect((view.getByPlaceholderText("输入提交说明。首行为主题，空行后为详细描述...") as HTMLInputElement).value).toBe("");
    expect(summary.textContent).toContain("本地提交 4 个文件");
    expect(summary.textContent).toContain("远端推送 3 个文件");
    expect(summary.textContent).toContain("仅本地 1 个 spec 文件");
    expect(summary.textContent).toContain("workspace提交 2远端 1仅本地 spec 1");
    expect(summary.textContent).toContain("应用 Agent提交 1远端 1");
    expect(summary.textContent).toContain("公共 Agent提交 1远端 1");
  });

  it("keeps spec local for a super administrator too", async () => {
    apiClientMock.getWorkspaceGitDiff
      .mockResolvedValueOnce({
        files: [
          { path: fixture.files.spec, status: "added", rawStatus: "A ", staged: true, patch: "", additions: 1, deletions: 0 }
        ]
      })
      .mockResolvedValue({ files: [] });

    const view = render(GitChangesPanel, {
      props: {
        workspaceId: fixture.application.personalRuntimeWorkspaceId,
        personalWorkspaceId: fixture.application.personalWorkspaceId,
        apiBaseUrl: "http://api",
        canWrite: true,
        canManageAgentConfig: true,
        canManagePublicConfig: true
      },
      global: { plugins: [createPinia()] }
    });

    expect(await view.findByText("design.md")).toBeTruthy();
    await fireEvent.update(view.getByPlaceholderText("输入提交说明。首行为主题，空行后为详细描述..."), "spec: 超管本地提交设计");
    expect(view.queryByRole("button", { name: "提交并推送" })).toBeNull();
    await fireEvent.click(view.getByRole("button", { name: "提交" }));

    await waitFor(() => expect(apiClientMock.commitPersonalWorkspace).toHaveBeenCalledWith(
      fixture.application.personalWorkspaceId,
      expect.objectContaining({ files: [fixture.files.spec] })
    ));
    expect(apiClientMock.publishPersonalWorkspace).not.toHaveBeenCalled();
  });

  it("shows application agent changes as readonly to a regular member", async () => {
    apiClientMock.getWorkspaceAgentDiff.mockResolvedValue({
      files: [{ path: fixture.files.applicationAgent, status: "M", staged: false, patch: "" }]
    });

    const view = render(GitChangesPanel, {
      props: {
        workspaceId: fixture.application.personalRuntimeWorkspaceId,
        personalWorkspaceId: fixture.application.personalWorkspaceId,
        apiBaseUrl: "http://api",
        canWrite: true,
        canManageAgentConfig: false,
        canManagePublicConfig: false
      },
      global: { plugins: [createPinia()] }
    });

    const row = await view.findByLabelText(fixture.files.applicationAgent);
    expect(within(row).queryByTitle("暂存文件")).toBeNull();
    expect((view.getByRole("button", { name: "全部暂存应用 Agent 变更" }) as HTMLButtonElement).disabled).toBe(true);
    expect((view.getByRole("button", { name: "提交" }) as HTMLButtonElement).disabled).toBe(true);
    expect(apiClientMock.stageWorkspaceAgentFiles).not.toHaveBeenCalled();
  });

  it("commits application Agent config in the current version personal worktree", async () => {
    apiClientMock.getWorkspaceAgentDiff
      .mockResolvedValueOnce({
        files: [
          { path: "opencode.jsonc", status: "M", staged: true, patch: "" },
          { path: fixture.files.applicationSkill, status: "M", staged: true, patch: "" }
        ]
      })
      .mockResolvedValue({ files: [] });

    const view = render(GitChangesPanel, {
      props: {
        workspaceId: fixture.application.personalRuntimeWorkspaceId,
        agentConfigWorkspaceId: fixture.application.personalRuntimeWorkspaceId,
        personalWorkspaceId: fixture.application.personalWorkspaceId,
        personalWorkspaceBranch: fixture.application.personalBranch,
        apiBaseUrl: "http://api",
        canWrite: true,
        canManageAgentConfig: true,
        canManagePublicConfig: false
      },
      global: { plugins: [createPinia()] }
    });

    expect(await view.findByText("opencode.jsonc", { exact: false })).toBeTruthy();
    expect(await view.findByText("SKILL.md", { exact: false })).toBeTruthy();
    await fireEvent.update(view.getByPlaceholderText("输入提交说明。首行为主题，空行后为详细描述..."), "agent: 更新支付案例技能");
    await fireEvent.click(view.getByRole("button", { name: "提交" }));

    await waitFor(() => expect(apiClientMock.commitPersonalWorkspace).toHaveBeenCalledWith(
      fixture.application.personalWorkspaceId,
      expect.objectContaining({
        commitMessage: "agent: 更新支付案例技能",
        files: [".opencode/opencode.jsonc", `.opencode/${fixture.files.applicationSkill}`]
      })
    ));
    expect(apiClientMock.commitWorkspaceAgentConfig).not.toHaveBeenCalled();
    expect(view.queryByText(`个人 worktree · ${fixture.application.personalBranch}`)).toBeNull();
  });

  it("publishes only the selected application Agent paths from personal HEAD", async () => {
    apiClientMock.getWorkspaceAgentDiff
      .mockResolvedValueOnce({
        files: [
          { path: fixture.files.applicationAgent, status: "M", staged: true, patch: "" },
          { path: fixture.files.applicationSkill, status: "M", staged: false, patch: "" }
        ]
      })
      .mockResolvedValue({ files: [] });

    const view = render(GitChangesPanel, {
      props: {
        workspaceId: fixture.application.personalRuntimeWorkspaceId,
        personalWorkspaceId: fixture.application.personalWorkspaceId,
        personalWorkspaceBranch: fixture.application.personalBranch,
        apiBaseUrl: "http://api",
        canWrite: true,
        canManageAgentConfig: true,
        canManagePublicConfig: false
      },
      global: { plugins: [createPinia()] }
    });

    await view.findByText("payment-test.md", { exact: false });
    await fireEvent.update(view.getByPlaceholderText("输入提交说明。首行为主题，空行后为详细描述..."), "agent: 发布支付 Agent");
    await fireEvent.click(view.getByRole("button", { name: "提交并推送" }));

    const expectedFiles = [`.opencode/${fixture.files.applicationAgent}`];
    await waitFor(() => expect(apiClientMock.commitPersonalWorkspace).toHaveBeenCalledWith(
      fixture.application.personalWorkspaceId,
      expect.objectContaining({ files: expectedFiles })
    ));
    expect(apiClientMock.publishPersonalWorkspace).toHaveBeenCalledWith(
      fixture.application.personalWorkspaceId,
      expect.objectContaining({ files: expectedFiles })
    );
    expect(apiClientMock.commitWorkspaceAgentConfig).not.toHaveBeenCalled();
    expect(apiClientMock.publishWorkspaceAgentConfig).not.toHaveBeenCalled();
  });

  it("opens application Agent conflicts through the shared personal-worktree merge editor", async () => {
    apiClientMock.getWorkspaceAgentDiff.mockResolvedValue({
      files: [{ path: fixture.files.applicationAgent, status: "UU", staged: false, patch: "" }]
    });

    const view = render(GitChangesPanel, {
      props: {
        workspaceId: fixture.application.personalRuntimeWorkspaceId,
        personalWorkspaceId: fixture.application.personalWorkspaceId,
        apiBaseUrl: "http://api",
        canWrite: true,
        canManageAgentConfig: true,
        canManagePublicConfig: false
      },
      global: { plugins: [createPinia()] }
    });

    await fireEvent.click(view.getByRole("tab", { name: /^应用Agent/ }));
    const conflictRow = await view.findByLabelText(fixture.files.applicationAgent);
    const stageAllButton = view.getByRole("button", { name: "全部暂存应用 Agent 变更" });
    expect((stageAllButton as HTMLButtonElement).disabled).toBe(true);
    expect(stageAllButton.getAttribute("title")).toBe("存在未解决冲突，请先处理或取消合并");
    await fireEvent.click(conflictRow);

    await waitFor(() => expect(apiClientMock.getWorkspaceGitConflict).toHaveBeenCalledWith(
      fixture.application.personalRuntimeWorkspaceId,
      `.opencode/${fixture.files.applicationAgent}`
    ));
    expect(await view.findByText("合并编辑器")).toBeTruthy();
    expect(within(conflictRow).queryByTitle("回退文件改动")).toBeNull();
  });

  it("keeps feature workspace git actions readonly when the user has no write permission", async () => {
    apiClientMock.getWorkspaceGitDiff.mockResolvedValue({
      files: [{ path: fixture.files.docs, status: "modified", staged: false, patch: "", additions: 1, deletions: 0 }]
    });

    const view = render(GitChangesPanel, {
      props: {
        workspaceId: fixture.application.featureWorkspaceId,
        apiBaseUrl: "http://api",
        canWrite: false,
        canManageAgentConfig: false,
        canManagePublicConfig: false
      },
      global: { plugins: [createPinia()] }
    });

    const row = await view.findByLabelText(fixture.files.docs);
    expect(within(row).queryByTitle("回退文件改动")).toBeNull();
    expect(within(row).queryByTitle("暂存文件")).toBeNull();
    expect((view.getByRole("button", { name: "全部暂存应用工作空间变更" }) as HTMLButtonElement).disabled).toBe(true);
    expect(apiClientMock.stageWorkspaceGitFiles).not.toHaveBeenCalled();
  });

  it("shows pending feature update and completes a resolved native merge from the diff area", async () => {
    apiClientMock.getWorkspaceGitDiff
      .mockResolvedValueOnce({
        files: [],
        mergeInProgress: false,
        applicationUpdatePending: true,
        applicationTargetCommit: "1234567890abcdef"
      })
      .mockResolvedValue({
        files: [],
        mergeInProgress: true,
        applicationUpdatePending: true,
        applicationTargetCommit: "1234567890abcdef"
      });

    const view = render(GitChangesPanel, {
      props: {
        workspaceId: fixture.application.personalRuntimeWorkspaceId,
        personalWorkspaceId: fixture.application.personalWorkspaceId,
        apiBaseUrl: "http://api",
        canWrite: true,
        canManageAgentConfig: true,
        canManagePublicConfig: false
      },
      global: { plugins: [createPinia()] }
    });

    expect(await view.findByText(/应用 feature 有待同步更新/)).toBeTruthy();
    await fireEvent.click(view.getByRole("tab", { name: /^应用Agent/ }));
    expect(await view.findByText(/应用 feature 有待同步更新/)).toBeTruthy();
    await fireEvent.click(view.getByRole("tab", { name: /^workspace/ }));
    await fireEvent.click(view.getByTitle("刷新变更列表"));
    const complete = await view.findByRole("button", { name: "完成合并" });
    await fireEvent.click(complete);

    await waitFor(() => expect(apiClientMock.completeWorkspaceGitMerge).toHaveBeenCalledWith(
      fixture.application.personalRuntimeWorkspaceId
    ));
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
    expect(await view.findByText(/feature 分支推送结果未确认/)).toBeTruthy();
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
      status: "PUBLISHED",
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

    expect(await view.findByText(/feature 分支推送结果未确认/)).toBeTruthy();
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

  it("finishes the progress step after a successful local workspace commit", async () => {
    apiClientMock.getWorkspaceGitDiff
      .mockResolvedValueOnce({
        files: [{ path: "src/local-only.ts", status: "modified", rawStatus: "M ", staged: true, patch: "", additions: 1, deletions: 0 }]
      })
      .mockResolvedValue({ files: [] });
    const view = render(GitChangesPanel, {
      props: {
        workspaceId: "wrk_1234567890abcdef",
        personalWorkspaceId: "psw_default",
        apiBaseUrl: "http://api",
        canWrite: true
      },
      global: { plugins: [createPinia()] }
    });

    expect(await view.findByText("local-only.ts")).toBeTruthy();
    await fireEvent.update(view.getByPlaceholderText("输入提交说明。首行为主题，空行后为详细描述..."), "fix: local commit");
    await fireEvent.click(view.getByRole("button", { name: "提交" }));

    expect(await view.findByText("提交成功！")).toBeTruthy();
    expect(view.queryByText("RUNNING")).toBeNull();
    expect(view.getAllByText("SUCCEEDED")).toHaveLength(2);
    expect(view.getAllByRole("button", { name: "关闭" })
      .every((button) => !(button as HTMLButtonElement).disabled)).toBe(true);
  });

  it("keeps failed application Agent files pending and retries without another local commit", async () => {
    const { BackendApiError } = await import("@test-agent/backend-api");
    apiClientMock.getWorkspaceAgentDiff
      .mockResolvedValueOnce({
        files: [{
          path: "opencode.jsonc",
          status: "modified",
          staged: true,
          patch: "@@ -1 +1 @@\n-old\n+new"
        }]
      })
      .mockResolvedValue({ files: [] });
    apiClientMock.publishPersonalWorkspace.mockRejectedValueOnce(new BackendApiError(502, {
      success: false,
      code: "GIT_UNAVAILABLE",
      message: "Git 远端拒绝推送",
      traceId: "trace_rejected",
      details: {
        failedStep: "PUSH_REMOTE",
        gitFailureType: "REMOTE_REJECTED",
        executedCommands: ["git -C /repo push origin feature_testagent_20260717"]
      }
    }));
    const view = render(GitChangesPanel, {
      props: {
        workspaceId: "wrk_personal_runtime",
        agentConfigWorkspaceId: "wrk_personal_runtime",
        personalWorkspaceId: "psw_default",
        apiBaseUrl: "http://api",
        canWrite: true,
        canManageAgentConfig: true
      },
      global: { plugins: [createPinia()] }
    });

    await fireEvent.click(await view.findByRole("tab", { name: /^应用Agent/ }));
    expect(await view.findByText("opencode.jsonc")).toBeTruthy();
    await fireEvent.update(view.getByPlaceholderText("输入提交说明。首行为主题，空行后为详细描述..."), "更新opencode配置");
    await fireEvent.click(view.getByRole("button", { name: "提交并推送" }));

    expect(await view.findByText(/提交失败：Git 远端拒绝推送/)).toBeTruthy();
    await waitFor(() => expect(apiClientMock.getWorkspaceAgentDiff.mock.calls.length).toBeGreaterThan(1));
    expect(await view.findByText("待推送")).toBeTruthy();
    expect(view.getByLabelText("opencode.jsonc")).toBeTruthy();
    expect(view.queryByText("RUNNING")).toBeNull();
    expect(view.getByText("FAILED")).toBeTruthy();
    expect(view.getAllByRole("button", { name: "关闭" })
      .every((button) => !(button as HTMLButtonElement).disabled)).toBe(true);

    await fireEvent.click(view.getByRole("button", { name: "重新推送" }));

    await waitFor(() => expect(apiClientMock.publishPersonalWorkspace).toHaveBeenCalledTimes(2));
    expect(apiClientMock.commitPersonalWorkspace).toHaveBeenCalledTimes(1);
    expect(await view.findByText("提交并推送成功！")).toBeTruthy();
    await waitFor(() => expect(view.queryByLabelText("opencode.jsonc")).toBeNull());
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
      currentStep: "COMMIT_FEATURE",
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
      currentStep: "COMMIT_FEATURE",
      command: "git -C /repo commit -m fix: remote",
      status: "RUNNING"
    });
    progressHandler?.({
      currentStep: "PUSH_REMOTE",
      command: "git -C /repo push origin feature_test",
      status: "RUNNING"
    });
    resolvePublish?.({
      status: "PUBLISHED",
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
      status: "PUBLISHED",
      personalWorkspaceId: "psw_default",
      versionId: "awv_1",
      conflictFiles: [],
      message: "合并成功",
      remotePushed: true,
      currentStep: "COMPLETED",
      executedCommands: ["git -C /repo push origin feature_test"],
      headCommit: "commit_merged"
    });
    await waitFor(() => expect(view.getAllByText("SUCCEEDED")).toHaveLength(5));

    progressHandler?.({
      currentStep: "PUSH_REMOTE",
      command: "git -C /repo rev-parse HEAD",
      status: "RUNNING"
    });

    await waitFor(() => expect(view.getAllByText("SUCCEEDED")).toHaveLength(5));
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
    expect(view.queryByRole("button", { name: "提交并推送" })).toBeNull();
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
