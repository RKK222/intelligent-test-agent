import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, waitFor } from "@testing-library/vue";
import { createPinia } from "pinia";
import AgentConfigPanel from "../src/components/AgentConfigPanel.vue";

const apiClientMock = vi.hoisted(() => ({
  getPublicAgentConfigStatus: vi.fn(),
  getWorkspaceAgentConfigStatus: vi.fn(),
  listPublicAgentFiles: vi.fn(),
  listWorkspaceAgentFiles: vi.fn(),
  listPublicAgentBranches: vi.fn(),
  listPublicAgentRepositories: vi.fn(),
  listPublicAgentWorktrees: vi.fn(),
  readPublicAgentFile: vi.fn(),
  readWorkspaceAgentFile: vi.fn()
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
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("loads remote branches and disables public worktree creation when no server is initialized", async () => {
    apiClientMock.listPublicAgentRepositories.mockResolvedValue([uninitializedRepository()]);

    const { view } = renderPanel();

    await waitFor(() => expect(apiClientMock.getPublicAgentConfigStatus).toHaveBeenCalled());
    await fireEvent.click(view.getByTitle("创建公共 worktree"));

    expect(await view.findByText("远端分支")).toBeTruthy();
    expect(await view.findByText("没有已初始化服务器，请到系统管理 > 配置管理 > opencode公共配置管理初始化。")).toBeTruthy();
    expect((view.getByRole("button", { name: "确定" }) as HTMLButtonElement).disabled).toBe(true);
  });

  it("switches public level to a selected worktree and reloads files with worktree context", async () => {
    apiClientMock.listPublicAgentWorktrees.mockResolvedValue([publicWorktreeOption()]);

    const { view, workbench } = renderPanel();

    await waitFor(() => expect(apiClientMock.listPublicAgentFiles).toHaveBeenCalledWith("", undefined, "linux-1"));
    await fireEvent.click(view.getByTitle("切换公共 worktree"));

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
    await fireEvent.click(view.getByTitle("切换公共 worktree"));
    await view.findByText("change-agent-md / main / usr_admin / admin");
    await fireEvent.update(view.getByLabelText("公共 worktree"), "__direct_public_config__");
    await fireEvent.click(view.getByRole("button", { name: "确定" }));

    await waitFor(() => expect(workbench.publicWorktree).toBeNull());
    expect(workbench.publicConfigLinuxServerId).toBe("linux-1");
    await waitFor(() => expect(apiClientMock.listPublicAgentFiles).toHaveBeenLastCalledWith("", undefined, "linux-1"));
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
