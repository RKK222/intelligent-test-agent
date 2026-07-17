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
  getPublicAgentGitConflictFiles: vi.fn(),
  getPublicAgentDiff: vi.fn(),
  getWorkspaceAgentDiff: vi.fn(),
  stagePublicAgentFiles: vi.fn(),
  stageWorkspaceAgentFiles: vi.fn(),
  unstagePublicAgentFiles: vi.fn(),
  unstageWorkspaceAgentFiles: vi.fn(),
  getPublicAgentGitConflict: vi.fn(),
  resolvePublicAgentGitConflict: vi.fn(),
  resolveAllPublicAgentGitConflicts: vi.fn(),
  abortPublicAgentGitConflict: vi.fn(),
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
  const store = vue.reactive({
    publicWorktree: null,
    workspaceWorktree: null,
    publicConfigLinuxServerId: null,
    tabs: [],
    activePath: undefined,
    closeTab: (_path: string) => undefined
  }) as WorkbenchStoreMock;
  store.closeTab = vi.fn((path: string) => {
    store.tabs = store.tabs.filter((tab) => tab.path !== path);
    if (store.activePath === path) {
      store.activePath = store.tabs.at(-1)?.path;
    }
  });
  workbenchMock.store = store;
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
    workbench.tabs = [];
    workbench.activePath = undefined;
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
    apiClientMock.getPublicAgentGitConflictFiles.mockResolvedValue({ files: [] });
    apiClientMock.getPublicAgentDiff.mockResolvedValue({ files: [] });
    apiClientMock.getWorkspaceAgentDiff.mockResolvedValue({ files: [] });
    apiClientMock.stagePublicAgentFiles.mockResolvedValue(undefined);
    apiClientMock.stageWorkspaceAgentFiles.mockResolvedValue(undefined);
    apiClientMock.unstagePublicAgentFiles.mockResolvedValue(undefined);
    apiClientMock.unstageWorkspaceAgentFiles.mockResolvedValue(undefined);
    apiClientMock.getPublicAgentGitConflict.mockResolvedValue({
      path: "opencode/agents/review.md",
      rawStatus: "UU",
      baseContent: "",
      currentContent: "local",
      incomingContent: "remote",
      resultContent: ""
    });
    apiClientMock.resolvePublicAgentGitConflict.mockResolvedValue(undefined);
    apiClientMock.resolveAllPublicAgentGitConflicts.mockResolvedValue(undefined);
    apiClientMock.abortPublicAgentGitConflict.mockResolvedValue(undefined);
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
    expect(await view.findByText("没有已初始化服务器，请到系统管理 > 配置管理 > TestAgent公共配置管理初始化。")).toBeTruthy();
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

  it("loads public conflict file names without fetching full public diff on startup", async () => {
    apiClientMock.getPublicAgentGitConflictFiles.mockResolvedValueOnce({
      files: ["opencode/agents/test-design-orchestrator.md"]
    });

    const { view } = renderPanel();

    await waitFor(() => expect(apiClientMock.getPublicAgentGitConflictFiles).toHaveBeenCalledWith(undefined, "linux-1"));
    expect(apiClientMock.getPublicAgentDiff).not.toHaveBeenCalled();
    expect(await view.findByText("公共级存在 1 个冲突文件")).toBeTruthy();
    expect(await view.findByText("opencode/agents/test-design-orchestrator.md")).toBeTruthy();
  });

  it("renders agent files with VS Code codicons and compact tree rows", async () => {
    apiClientMock.listPublicAgentFiles.mockResolvedValue([
      { path: "agents", name: "agents", type: "directory" },
      { path: "README.md", name: "README.md", type: "file" }
    ]);

    const { view } = renderPanel();

    await waitFor(() => expect(apiClientMock.listPublicAgentFiles).toHaveBeenCalled());
    expect(await view.findByText("agents")).toBeTruthy();
    expect(await view.findByText("README.md")).toBeTruthy();
    expect(view.container.querySelector(".ta-file-tree-row")).toBeTruthy();
    expect(view.container.querySelector(".codicon-folder")).toBeFalsy();
    expect(view.container.querySelector("svg.ta-file-tree-icon")).toBeTruthy();
    expect(view.container.querySelector("use")?.getAttribute("href")).toContain("#Readme");
  });

  it("shows the direct public server and physical config directory", async () => {
    const { view } = renderPanel();

    expect(await view.findByText("直接目录")).toBeTruthy();
    expect(view.getByText("直接 · 测试服务器")).toBeTruthy();
    expect(view.getByText("/data/opencode-public-config/opencode")).toBeTruthy();
  });

  it("shows the selected public worktree server and physical config directory", async () => {
    const { view } = renderPanel((store) => {
      store.publicWorktree = publicWorktreeOption();
      store.publicConfigLinuxServerId = "linux-1";
    });

    expect(await view.findByText("worktree")).toBeTruthy();
    expect(view.getByText("worktree · change-agent-md")).toBeTruthy();
    expect(view.getByText("/data/opencode-public-worktrees/change-agent-md/opencode")).toBeTruthy();
  });

  it("clears stale expanded entries and reloads the current disk tree", async () => {
    let diskChanged = false;
    apiClientMock.listPublicAgentFiles.mockImplementation(async (path: string) => {
      if (path === "") {
        return diskChanged
          ? [{ path: "opencode.jsonc", name: "opencode.jsonc", type: "file" }]
          : [{ path: "agents", name: "agents", type: "directory" }];
      }
      if (path === "agents") {
        return [{ path: "agents/legacy.bak", name: "legacy.bak", type: "file" }];
      }
      return [];
    });

    const { view } = renderPanel(undefined, { hideHeader: false });
    await fireEvent.click(await view.findByText("agents"));
    expect(await view.findByText("legacy.bak")).toBeTruthy();

    diskChanged = true;
    await fireEvent.click(view.getByRole("button", { name: "刷新" }));

    expect(await view.findByText("opencode.jsonc")).toBeTruthy();
    await waitFor(() => expect(view.queryByText("legacy.bak")).toBeNull());
  });

  it("requests a clean active Agent editor refresh without reading content in the panel", async () => {
    const activePath = "agent-public::linux-1:agents/review.md";
    const { view } = renderPanel((store) => {
      store.tabs = [{
        id: "public-agent",
        path: activePath,
        title: "review.md",
        content: "old content",
        savedContent: "old content"
      }];
      store.activePath = activePath;
    }, { hideHeader: false, activePath });
    await waitFor(() => expect(view.emitted("openFile")?.length).toBeGreaterThan(0));
    const refreshRequestsBeforeClick = view.emitted("openFile")?.length ?? 0;

    await fireEvent.click(view.getByRole("button", { name: "刷新" }));

    await waitFor(() => {
      const events = (view.emitted("openFile") ?? []) as unknown[][];
      expect(events.length).toBeGreaterThan(refreshRequestsBeforeClick);
      expect(events.at(-1)?.[0]).toMatchObject({
        scope: "PUBLIC",
        path: "agents/review.md",
        workspaceId: undefined,
        worktreeId: undefined,
        linuxServerId: "linux-1",
        readonly: false,
        activate: false,
        closeOnNotFound: true
      });
    });
    expect(apiClientMock.readPublicAgentFile).not.toHaveBeenCalled();
  });

  it("does not overwrite an active Agent editor with unsaved changes", async () => {
    const activePath = "agent-public::linux-1:agents/review.md";
    const { view } = renderPanel((store) => {
      store.tabs = [{
        id: "public-agent",
        path: activePath,
        title: "review.md",
        content: "unsaved draft",
        savedContent: "old content"
      }];
      store.activePath = activePath;
    }, { hideHeader: false, activePath });

    await fireEvent.click(view.getByRole("button", { name: "刷新" }));

    await waitFor(() => expect(notifyMock.notifyInfo).toHaveBeenCalledWith(
      "未覆盖未保存的 Agent 文件",
      "agents/review.md"
    ));
    expect(apiClientMock.readPublicAgentFile).not.toHaveBeenCalled();
  });

  it("keeps agents and skills visible for normal users while hiding repository root noise", async () => {
    apiClientMock.listPublicAgentFiles.mockResolvedValue([
      { path: ".DS_Store", name: ".DS_Store", type: "file" },
      { path: ".gitignore", name: ".gitignore", type: "file" },
      { path: ".keep", name: ".keep", type: "file" },
      { path: "agents", name: "agents", type: "directory" },
      { path: "node_modules", name: "node_modules", type: "directory" },
      { path: "opencode.jsonc", name: "opencode.jsonc", type: "file" },
      { path: "package-lock.json", name: "package-lock.json", type: "file" },
      { path: "package.json", name: "package.json", type: "file" },
      { path: "skills", name: "skills", type: "directory" }
    ]);

    const { view } = renderPanel(undefined, { canWrite: false });

    await waitFor(() => expect(apiClientMock.listPublicAgentFiles).toHaveBeenCalled());
    expect(await view.findByText("agents")).toBeTruthy();
    expect(await view.findByText("skills")).toBeTruthy();
    expect(view.queryByText(".DS_Store")).toBeNull();
    expect(view.queryByText(".gitignore")).toBeNull();
    expect(view.queryByText(".keep")).toBeNull();
    expect(view.queryByText("node_modules")).toBeNull();
    expect(view.queryByText("opencode.jsonc")).toBeNull();
    expect(view.queryByText("package-lock.json")).toBeNull();
    expect(view.queryByText("package.json")).toBeNull();
  });

  it("emits a public Agent load request without reading content in the panel", async () => {
    apiClientMock.listPublicAgentFiles.mockResolvedValue([
      { path: ".gitignore", name: ".gitignore", type: "file" }
    ]);
    const { view } = renderPanel();

    await fireEvent.click(await view.findByText(".gitignore"));

    await waitFor(() => {
      const events = (view.emitted("openFile") ?? []) as unknown[][];
      const payload = events.at(-1)?.[0] as Record<string, unknown> | undefined;
      expect(payload).toMatchObject({
        scope: "PUBLIC",
        path: ".gitignore",
        workspaceId: undefined,
        worktreeId: undefined,
        linuxServerId: "linux-1",
        readonly: false,
        activate: true,
        closeOnNotFound: false
      });
      expect(payload).not.toHaveProperty("content");
      const fileRow = view.getByText(".gitignore").closest("button");
      expect(fileRow?.classList.contains("is-active")).toBe(true);
    });
    expect(apiClientMock.readPublicAgentFile).not.toHaveBeenCalled();
    expect(view.container.querySelector(".agent-root-row.active")).toBeNull();
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

  it("initializes an OpenCode-compatible workspace agent and skill package", async () => {
    const { view } = renderPanel();

    await waitFor(() => expect(apiClientMock.getWorkspaceAgentConfigStatus).toHaveBeenCalled());
    await fireEvent.click(view.getByRole("button", { name: "初始化应用 Agent/Skill 配置包" }));
    await fireEvent.update(await view.findByLabelText("配置包名称"), "支付测试技能");
    await fireEvent.click(view.getByRole("button", { name: "创建" }));

    await waitFor(() => expect(apiClientMock.writeWorkspaceAgentFile).toHaveBeenCalledTimes(4));
    expect(apiClientMock.writeWorkspaceAgentFile.mock.calls.map((call) => call.slice(0, 2))).toEqual([
      ["wrk_1234567890abcdef", "agents/zhi-fu-ce-shi-ji-neng.md"],
      ["wrk_1234567890abcdef", "skills/zhi-fu-ce-shi-ji-neng/SKILL.md"],
      ["wrk_1234567890abcdef", "skills/zhi-fu-ce-shi-ji-neng/rules/README.md"],
      ["wrk_1234567890abcdef", "skills/zhi-fu-ce-shi-ji-neng/templates/README.md"]
    ]);
    const agentContent = String(apiClientMock.writeWorkspaceAgentFile.mock.calls[0]?.[2]);
    expect(agentContent).toContain("name: zhi-fu-ce-shi-ji-neng");
    expect(agentContent).toContain("mode: primary");
    expect(agentContent).toContain("skills/zhi-fu-ce-shi-ji-neng/SKILL.md");
    const skillContent = String(apiClientMock.writeWorkspaceAgentFile.mock.calls[1]?.[2]);
    expect(skillContent).toContain("name: zhi-fu-ce-shi-ji-neng");
    expect(skillContent).toContain("description: 支付测试技能 application workspace skill");
    expect(skillContent).toContain("compatibility: opencode");
    expect(skillContent).toContain("metadata:");
    expect(skillContent).not.toContain("version:");
    expect(skillContent).toContain("## What I do");
    expect(skillContent).toContain("## When to use me");
    expect(skillContent).toContain("## Resources");
    await waitFor(() => expect(apiClientMock.listWorkspaceAgentFiles).toHaveBeenCalledWith("wrk_1234567890abcdef", "", undefined));
  });

  it("submits dirty public repository changes without forcing discard", async () => {
    apiClientMock.listPublicAgentRepositories.mockResolvedValue([{
      ...initializedRepository(),
      status: "CONFLICT",
      message: "Git 工作树存在未提交变更"
    }]);
    const { view } = renderPanel();

    await waitFor(() => expect(apiClientMock.getPublicAgentConfigStatus).toHaveBeenCalled());
    await fireEvent.click(view.getByText("更新公共配置"));

    expect(await view.findByText("Git 工作树存在未提交变更")).toBeTruthy();
    const confirmButton = view.getByRole("button", { name: "提交并推送" }) as HTMLButtonElement;
    // 提交信息未填写时按钮应禁用
    expect(confirmButton.disabled).toBe(true);

    // 输入提交信息后按钮可点
    await fireEvent.update(view.getByLabelText("提交信息 *"), "chore: sync public config");
    expect(confirmButton.disabled).toBe(false);
    await fireEvent.click(confirmButton);

    await waitFor(() => expect(apiClientMock.updatePublicAgentConfigAndPush).toHaveBeenCalledWith({
      branch: "main",
      commitMessage: "chore: sync public config",
      operationId: expect.stringMatching(/^aco_/),
      discardLocalChanges: false
    }));
  });

  it("allows submit after user explicitly confirms discard for dirty public repo", async () => {
    apiClientMock.listPublicAgentRepositories.mockResolvedValue([{
      ...initializedRepository(),
      status: "CONFLICT",
      message: "Git 工作树存在未提交变更"
    }]);
    const { view } = renderPanel();

    await waitFor(() => expect(apiClientMock.getPublicAgentConfigStatus).toHaveBeenCalled());
    await fireEvent.click(view.getByText("更新公共配置"));
    await view.findByText("Git 工作树存在未提交变更");
    await fireEvent.update(view.getByLabelText("提交信息 *"), "chore: sync public config");
    await fireEvent.click(view.getByLabelText("放弃已跟踪本地修改后再提交"));
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

    await waitFor(() => expect(apiClientMock.getPublicAgentConfigStatus).toHaveBeenCalled());
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

  it("shows public update-and-push Git progress commands", async () => {
    apiClientMock.connectAgentConfigProgress.mockImplementationOnce(async (_operationId: string, onEvent: (event: unknown) => void) => {
      onEvent({
        type: "step",
        status: "RUNNING",
        currentStep: "PUSHING",
        command: "git -C /repo push origin main"
      });
      return { close: vi.fn() };
    });
    const { view } = renderPanel();

    await waitFor(() => expect(apiClientMock.getPublicAgentConfigStatus).toHaveBeenCalled());
    await fireEvent.click(view.getByText("更新公共配置"));
    await view.findByLabelText("提交信息 *");
    await fireEvent.update(view.getByLabelText("提交信息 *"), "feat: update agent docs");
    await fireEvent.click(view.getByRole("button", { name: "提交并推送" }));

    expect(await view.findByRole("dialog", { name: "公共 Agent 提交并推送进度" })).toBeTruthy();
    expect(await view.findByText("推送到远端仓库")).toBeTruthy();
    expect(await view.findByText("git -C /repo push origin main")).toBeTruthy();
  });

  it("does not report success when update-and-push operation status is failed", async () => {
    apiClientMock.updatePublicAgentConfigAndPush.mockResolvedValueOnce({
      status: "FAILED",
      currentStep: "PUSHING",
      errorMessage: "远端拒绝推送"
    });
    const { view } = renderPanel();

    await waitFor(() => expect(apiClientMock.getPublicAgentConfigStatus).toHaveBeenCalled());
    await fireEvent.click(view.getByText("更新公共配置"));
    await view.findByLabelText("提交信息 *");
    await fireEvent.update(view.getByLabelText("提交信息 *"), "feat: try push");
    await fireEvent.click(view.getByRole("button", { name: "提交并推送" }));

    await waitFor(() => expect(notifyMock.notifyError).toHaveBeenCalledWith(
      "公共 Agent 提交并推送失败",
      expect.stringContaining("远端拒绝推送")
    ));
    expect(notifyMock.notifySuccess).not.toHaveBeenCalled();
    expect(await view.findAllByText(/远端拒绝推送/)).not.toHaveLength(0);
  });

  it("shows error toast when update-and-push fails", async () => {
    apiClientMock.updatePublicAgentConfigAndPush.mockRejectedValueOnce(new Error("远端 push 失败：non-fast-forward"));
    const { view } = renderPanel();

    await waitFor(() => expect(apiClientMock.getPublicAgentConfigStatus).toHaveBeenCalled());
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

  it("shows conflict files and opens public conflict editor after update-and-push conflict", async () => {
    apiClientMock.updatePublicAgentConfigAndPush.mockResolvedValueOnce({
      status: "FAILED",
      currentStep: "MERGING",
      errorMessage: "合并冲突，请先处理 opencode/agents/test-design-orchestrator.md 后重试"
    });
    apiClientMock.getPublicAgentDiff.mockResolvedValue({
      files: [
        {
          path: "opencode/agents/test-design-orchestrator.md",
          status: "conflict",
          rawStatus: "UU",
          staged: false,
          patch: "",
          additions: 0,
          deletions: 0
        }
      ]
    });
    apiClientMock.getPublicAgentGitConflict.mockResolvedValueOnce({
        path: "opencode/agents/test-design-orchestrator.md",
      rawStatus: "UU",
      baseContent: "base",
      currentContent: "local",
      incomingContent: "remote",
      resultContent: "<<<<<<< HEAD\nlocal\n=======\nremote\n>>>>>>> origin/master"
    });
    const { view } = renderPanel();

    await waitFor(() => expect(apiClientMock.getPublicAgentConfigStatus).toHaveBeenCalled());
    await fireEvent.click(view.getByText("更新公共配置"));
    await view.findByLabelText("提交信息 *");
    await fireEvent.update(view.getByLabelText("提交信息 *"), "feat: resolve conflict");
    await fireEvent.click(view.getByRole("button", { name: "提交并推送" }));

    expect(await view.findAllByText("opencode/agents/test-design-orchestrator.md")).not.toHaveLength(0);
    const conflictButtons = await view.findAllByText("处理冲突");
    await fireEvent.click(conflictButtons[0]);

    await waitFor(() => expect(apiClientMock.getPublicAgentGitConflict).toHaveBeenCalledWith(
      "opencode/agents/test-design-orchestrator.md",
      undefined,
      "linux-1"
    ));
    expect(await view.findByText("合并编辑器")).toBeTruthy();
    expect(await view.findByText("合并结果（可编辑）")).toBeTruthy();
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
  tabs: Array<{
    id: string;
    path: string;
    title: string;
    content: string;
    savedContent: string;
    readonly?: boolean;
    livePreview?: boolean;
  }>;
  activePath?: string;
  closeTab: (path: string) => void;
};

function renderPanel(
  setup?: (workbench: WorkbenchStoreMock) => void,
  options?: { canWrite?: boolean; hideHeader?: boolean; activePath?: string }
) {
  const pinia = createPinia();
  const workbench = currentWorkbenchStore();
  setup?.(workbench);
  const view = render(AgentConfigPanel, {
    props: {
      baseUrl: "http://api",
      workspaceId: "wrk_1234567890abcdef",
      canWrite: options?.canWrite ?? true,
      canManageWorkspaceConfig: options?.canWrite ?? true,
      hideHeader: options?.hideHeader ?? true,
      activePath: options?.activePath
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
