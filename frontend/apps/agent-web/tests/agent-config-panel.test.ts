import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, waitFor } from "@testing-library/vue";
import { createPinia } from "pinia";
import AgentConfigPanel from "../src/components/AgentConfigPanel.vue";

const notifyMock = vi.hoisted(() => ({
  notifySuccess: vi.fn(),
  notifyError: vi.fn(),
  notifyWarning: vi.fn(),
  notifyInfo: vi.fn()
}));

vi.mock("../src/components/notify", () => notifyMock);

const apiClientMock = vi.hoisted(() => ({
  getPublicAgentConfigStatus: vi.fn(),
  getWorkspaceAgentConfigStatus: vi.fn(),
  listPublicAgentFiles: vi.fn(),
  listWorkspaceAgentFiles: vi.fn(),
  listPublicAgentBranches: vi.fn(),
  listPublicAgentRepositories: vi.fn(),
  listPublicAgentWorktrees: vi.fn(),
  readPublicAgentFile: vi.fn(),
  readWorkspaceAgentFile: vi.fn(),
  writeWorkspaceAgentFile: vi.fn(),
  updatePublicAgentConfig: vi.fn(),
  updatePublicAgentConfigAndPush: vi.fn(),
  connectAgentConfigProgress: vi.fn()
}));

const workbenchMock = vi.hoisted(() => ({
  store: null as WorkbenchStoreMock | null
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
  const vue = await vi.importActual<typeof import("vue")>("vue");
  workbenchMock.store = vue.reactive({
    publicWorktree: null,
    workspaceWorktree: null,
    publicConfigLinuxServerId: null
  }) as WorkbenchStoreMock;
  return {
    useWorkbenchStore: () => workbenchMock.store
  };
});

describe("AgentConfigPanel", () => {
  beforeEach(() => {
    const workbench = currentWorkbenchStore();
    workbench.publicWorktree = null;
    workbench.workspaceWorktree = null;
    workbench.publicConfigLinuxServerId = null;
    apiClientMock.getPublicAgentConfigStatus.mockResolvedValue(publicStatus());
    apiClientMock.getWorkspaceAgentConfigStatus.mockResolvedValue(publicStatus("WORKSPACE"));
    apiClientMock.listPublicAgentFiles.mockResolvedValue([]);
    apiClientMock.listWorkspaceAgentFiles.mockResolvedValue([]);
    apiClientMock.listPublicAgentBranches.mockResolvedValue(["main", "develop"]);
    apiClientMock.listPublicAgentRepositories.mockResolvedValue([initializedRepository()]);
    apiClientMock.listPublicAgentWorktrees.mockResolvedValue([]);
    apiClientMock.readPublicAgentFile.mockResolvedValue({ path: "agent.md", content: "", encoding: "utf-8" });
    apiClientMock.readWorkspaceAgentFile.mockResolvedValue({ path: "agent.md", content: "", encoding: "utf-8" });
    apiClientMock.writeWorkspaceAgentFile.mockResolvedValue(undefined);
    apiClientMock.updatePublicAgentConfig.mockResolvedValue({ status: "SUCCEEDED" });
    apiClientMock.updatePublicAgentConfigAndPush.mockResolvedValue({ status: "SUCCEEDED", commitHash: "newcommit123" });
    apiClientMock.connectAgentConfigProgress.mockResolvedValue({ close: vi.fn() });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("loads remote branches and disables public worktree creation when no server is initialized", async () => {
    apiClientMock.listPublicAgentRepositories.mockResolvedValue([uninitializedRepository()]);

    const { view } = renderPanel();

    await waitFor(() => expect(apiClientMock.getPublicAgentConfigStatus).toHaveBeenCalled());
    await fireEvent.click(view.getByText("创建公共 worktree"));

    expect(await view.findByText("远端分支")).toBeTruthy();
    expect(await view.findByText("没有已初始化服务器，请到系统管理 > 配置管理 > opencode公共配置管理初始化。")).toBeTruthy();
    expect((view.getByRole("button", { name: "确定" }) as HTMLButtonElement).disabled).toBe(true);
  });

  it("loads public and workspace agent status plus root directories without serial blocking", async () => {
    let resolvePublicStatus!: (value: ReturnType<typeof publicStatus>) => void;
    let resolvePublicFiles!: (value: unknown[]) => void;
    apiClientMock.getPublicAgentConfigStatus.mockReturnValue(new Promise((resolve) => {
      resolvePublicStatus = resolve;
    }));
    apiClientMock.listPublicAgentFiles.mockReturnValue(new Promise((resolve) => {
      resolvePublicFiles = resolve;
    }));

    renderPanel();

    await waitFor(() => expect(apiClientMock.getWorkspaceAgentConfigStatus).toHaveBeenCalled());
    resolvePublicStatus(publicStatus());
    await waitFor(() => expect(apiClientMock.listPublicAgentFiles).toHaveBeenCalled());
    await waitFor(() => expect(apiClientMock.listWorkspaceAgentFiles).toHaveBeenCalledWith("wrk_1234567890abcdef", "", undefined));
    resolvePublicFiles([]);
  });

  it("switches public level to a selected worktree and reloads files with worktree context", async () => {
    apiClientMock.listPublicAgentWorktrees.mockResolvedValue([publicWorktreeOption()]);

    const { view, workbench } = renderPanel();

    await waitFor(() => expect(apiClientMock.listPublicAgentFiles).toHaveBeenCalledWith("", undefined, "linux-1"));
    await fireEvent.click(view.getByText("切换公共 worktree"));

    expect(await view.findByText("change-agent-md / main / usr_admin / admin")).toBeTruthy();
    await fireEvent.update(view.getByLabelText("公共 worktree"), "agw_1234567890abcdef");
    await fireEvent.click(view.getByRole("button", { name: "确定" }));

    await waitFor(() => expect(workbench.publicWorktree?.worktreeId).toBe("agw_1234567890abcdef"));
    expect(workbench.publicConfigLinuxServerId).toBe("linux-1");
    await waitFor(() => expect(apiClientMock.listPublicAgentFiles).toHaveBeenLastCalledWith("", "agw_1234567890abcdef", "linux-1"));
  });

  it("switches public level back to direct public config directory and reloads files with server context", async () => {
    apiClientMock.listPublicAgentWorktrees.mockResolvedValue([publicWorktreeOption()]);

    const { view, workbench } = renderPanel((store) => {
      store.publicWorktree = publicWorktreeOption();
      store.publicConfigLinuxServerId = "linux-1";
    });

    await waitFor(() => expect(apiClientMock.listPublicAgentFiles).toHaveBeenCalledWith("", "agw_1234567890abcdef", "linux-1"));
    await fireEvent.click(view.getByText("切换公共 worktree"));
    await view.findByText("change-agent-md / main / usr_admin / admin");
    await fireEvent.update(view.getByLabelText("公共 worktree"), "__direct_public_config__");
    await fireEvent.click(view.getByRole("button", { name: "确定" }));

    await waitFor(() => expect(workbench.publicWorktree).toBeNull());
    expect(workbench.publicConfigLinuxServerId).toBe("linux-1");
    await waitFor(() => expect(apiClientMock.listPublicAgentFiles).toHaveBeenLastCalledWith("", undefined, "linux-1"));
  });

  it("initializes only a workspace skill package from the workspace plus action", async () => {
    const { view } = renderPanel();

    await waitFor(() => expect(apiClientMock.getWorkspaceAgentConfigStatus).toHaveBeenCalled());
    await fireEvent.click(view.getByRole("button", { name: "初始化应用配置包" }));
    await fireEvent.update(await view.findByLabelText("配置包名称"), "支付测试技能");
    await fireEvent.click(view.getByRole("button", { name: "创建" }));

    await waitFor(() => expect(apiClientMock.writeWorkspaceAgentFile).toHaveBeenCalledTimes(3));
    expect(apiClientMock.writeWorkspaceAgentFile.mock.calls.map((call) => call.slice(0, 2))).toEqual([
      ["wrk_1234567890abcdef", "skills/zhi-fu-ce-shi-ji-neng/SKILL.md"],
      ["wrk_1234567890abcdef", "skills/zhi-fu-ce-shi-ji-neng/rules/README.md"],
      ["wrk_1234567890abcdef", "skills/zhi-fu-ce-shi-ji-neng/templates/README.md"]
    ]);
    const skillContent = String(apiClientMock.writeWorkspaceAgentFile.mock.calls[0]?.[2]);
    expect(skillContent).toContain("name: zhi-fu-ce-shi-ji-neng");
    expect(skillContent).toContain("description: 支付测试技能应用级技能包");
    expect(skillContent).not.toContain("version:");
    expect(skillContent).toContain("## Instructions");
    expect(skillContent).toContain("## Resources");
    await waitFor(() => expect(apiClientMock.listWorkspaceAgentFiles).toHaveBeenCalledWith("wrk_1234567890abcdef", "", undefined));
  });

  it("rejects submit when public repo is dirty and discard is not confirmed, shows error toast", async () => {
    apiClientMock.listPublicAgentRepositories.mockResolvedValue([{
      ...initializedRepository(),
      status: "CONFLICT",
      message: "Git 工作树存在未提交变更"
    }]);
    const { view } = renderPanel();

    await waitFor(() => expect(apiClientMock.listPublicAgentFiles).toHaveBeenCalled());
    await fireEvent.click(view.getByText("更新公共配置"));

    expect(await view.findByText("Git 工作树存在未提交变更")).toBeTruthy();
    const confirmButton = view.getByRole("button", { name: "提交并推送" }) as HTMLButtonElement;
    // 提交信息未填写时按钮应禁用
    expect(confirmButton.disabled).toBe(true);

    // 输入提交信息后按钮可点
    await fireEvent.update(view.getByLabelText("提交信息 *"), "chore: sync public config");
    expect(confirmButton.disabled).toBe(false);
    await fireEvent.click(confirmButton);

    // 等待弹窗内已渲染的错误提示（点击事件不会关闭弹窗）
    expect(await view.findByText("存在本地未提交修改，请先勾选放弃本地修改或先提交/丢弃")).toBeTruthy();
    // 仍然没有真正调用后端
    expect(apiClientMock.updatePublicAgentConfigAndPush).not.toHaveBeenCalled();
  });

  it("allows submit after user explicitly confirms discard for dirty public repo", async () => {
    apiClientMock.listPublicAgentRepositories.mockResolvedValue([{
      ...initializedRepository(),
      status: "CONFLICT",
      message: "Git 工作树存在未提交变更"
    }]);
    const { view } = renderPanel();

    await waitFor(() => expect(apiClientMock.listPublicAgentFiles).toHaveBeenCalled());
    await fireEvent.click(view.getByText("更新公共配置"));
    await view.findByText("Git 工作树存在未提交变更");
    await fireEvent.update(view.getByLabelText("提交信息 *"), "chore: sync public config");
    await fireEvent.click(view.getByLabelText("放弃本地修改并从远端恢复"));
    await fireEvent.click(view.getByRole("button", { name: "提交并推送" }));

    await waitFor(() => expect(apiClientMock.updatePublicAgentConfigAndPush).toHaveBeenCalledWith({
      branch: "main",
      commitMessage: "chore: sync public config",
      operationId: expect.stringMatching(/^aco_/),
      discardLocalChanges: true
    }));
    expect(apiClientMock.updatePublicAgentConfig).not.toHaveBeenCalled();
  });

  it("submits update-and-push with default discard flag when public repo is clean", async () => {
    const { view } = renderPanel();

    await waitFor(() => expect(apiClientMock.listPublicAgentFiles).toHaveBeenCalled());
    await fireEvent.click(view.getByText("更新公共配置"));

    await view.findByLabelText("提交信息 *");
    await fireEvent.update(view.getByLabelText("提交信息 *"), "feat: update agent docs");
    const confirmButton = view.getByRole("button", { name: "提交并推送" }) as HTMLButtonElement;
    expect(confirmButton.disabled).toBe(false);
    await fireEvent.click(confirmButton);

    await waitFor(() => expect(apiClientMock.updatePublicAgentConfigAndPush).toHaveBeenCalledWith({
      branch: "main",
      commitMessage: "feat: update agent docs",
      operationId: expect.stringMatching(/^aco_/),
      discardLocalChanges: false
    }));
    await waitFor(() => expect(notifyMock.notifySuccess).toHaveBeenCalledWith(
      "公共 Agent 已提交并推送",
      expect.stringContaining("newcommit123")
    ));
  });

  it("shows error toast when update-and-push fails", async () => {
    apiClientMock.updatePublicAgentConfigAndPush.mockRejectedValueOnce(new Error("远端 push 失败：non-fast-forward"));
    const { view } = renderPanel();

    await waitFor(() => expect(apiClientMock.listPublicAgentFiles).toHaveBeenCalled());
    await fireEvent.click(view.getByText("更新公共配置"));
    await view.findByLabelText("提交信息 *");
    await fireEvent.update(view.getByLabelText("提交信息 *"), "feat: try push");
    const confirmButton = view.getByRole("button", { name: "提交并推送" }) as HTMLButtonElement;
    await fireEvent.click(confirmButton);

    await waitFor(() => expect(notifyMock.notifyError).toHaveBeenCalledWith(
      "公共 Agent 提交并推送失败",
      expect.stringContaining("non-fast-forward")
    ));
    expect(notifyMock.notifySuccess).not.toHaveBeenCalled();
  });
});

type PublicWorktree = {
  worktreeId: string;
  scope: string;
  workspaceId: string | null;
  linuxServerId: string;
  worktreeName: string;
  branch: string;
  rootPath: string;
  agentDirectory: string;
  status: string;
  createdAt: string;
  updatedAt: string;
  createdByUserId?: string;
  createdByUsername?: string | null;
};

type WorkbenchStoreMock = {
  publicWorktree: PublicWorktree | null;
  workspaceWorktree: PublicWorktree | null;
  publicConfigLinuxServerId: string | null;
};

function renderPanel(setup?: (workbench: WorkbenchStoreMock) => void) {
  const pinia = createPinia();
  const workbench = currentWorkbenchStore();
  setup?.(workbench);
  const view = render(AgentConfigPanel, {
    props: {
      baseUrl: "http://api",
      workspaceId: "wrk_1234567890abcdef",
      canWrite: true,
      hideHeader: true
    },
    global: {
      plugins: [pinia]
    }
  });
  return { view, workbench };
}

function currentWorkbenchStore(): WorkbenchStoreMock {
  if (!workbenchMock.store) {
    throw new Error("workbench store mock is not initialized");
  }
  return workbenchMock.store;
}

function publicStatus(scope = "PUBLIC") {
  return {
    scope,
    enabled: true,
    writable: true,
    gitUrl: "git@gitee.com:org/config.git",
    gitRootPath: "/data/opencode-public-config",
    agentDirectory: "/data/opencode-public-config/opencode/agent",
    currentBranch: "main",
    commitHash: "abc1234"
  };
}

function initializedRepository() {
  return {
    linuxServerId: "linux-1",
    serverName: "测试服务器",
    gitRootPath: "/data/opencode-public-config",
    configDirPath: "/data/opencode-public-config/opencode",
    worktreeRootPath: "/data/opencode-public-worktrees",
    status: "READY",
    initialized: true,
    initializationAllowed: true,
    currentBranch: "main",
    commitHash: "abc1234",
    message: "已初始化"
  };
}

function uninitializedRepository() {
  return {
    ...initializedRepository(),
    status: "UNINITIALIZED",
    initialized: false,
    currentBranch: null,
    commitHash: null,
    message: "未初始化"
  };
}

function publicWorktreeOption(): PublicWorktree {
  return {
    worktreeId: "agw_1234567890abcdef",
    scope: "PUBLIC",
    workspaceId: null,
    linuxServerId: "linux-1",
    worktreeName: "change-agent-md",
    branch: "main",
    rootPath: "/data/opencode-public-worktrees/change-agent-md",
    agentDirectory: "/data/opencode-public-worktrees/change-agent-md/opencode/agent",
    status: "ACTIVE",
    createdAt: "2026-06-28T00:00:00Z",
    updatedAt: "2026-06-28T00:00:00Z",
    createdByUserId: "usr_admin",
    createdByUsername: "admin"
  };
}
