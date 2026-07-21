import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, waitFor, within } from "@testing-library/vue";
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
  createPublicAgentWorktree: vi.fn(),
  readPublicAgentFile: vi.fn(),
  readWorkspaceAgentFile: vi.fn(),
  writeWorkspaceAgentFile: vi.fn(),
  renameWorkspaceAgentFile: vi.fn(),
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
  disposeGlobal: vi.fn(),
  reloadPublicPersonalAgentRuntime: vi.fn(),
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
    workbench.publicConfigLinuxServerId = null;
    workbench.tabs = [];
    workbench.activePath = undefined;
    apiClientMock.getPublicAgentConfigStatus.mockResolvedValue(publicStatus());
    apiClientMock.getWorkspaceAgentConfigStatus.mockResolvedValue(publicStatus("WORKSPACE"));
    apiClientMock.listPublicAgentFiles.mockResolvedValue([]);
    apiClientMock.listWorkspaceAgentFiles.mockResolvedValue([]);
    apiClientMock.listPublicAgentBranches.mockResolvedValue(["main", "develop"]);
    apiClientMock.listPublicAgentRepositories.mockResolvedValue([initializedRepository()]);
    apiClientMock.listPublicAgentWorktrees.mockResolvedValue([publicWorktreeOption()]);
    apiClientMock.createPublicAgentWorktree.mockResolvedValue(publicWorktreeOption());
    apiClientMock.readPublicAgentFile.mockResolvedValue({ path: "agent.md", content: "", encoding: "utf-8" });
    apiClientMock.readWorkspaceAgentFile.mockResolvedValue({ path: "agent.md", content: "", encoding: "utf-8" });
    apiClientMock.writeWorkspaceAgentFile.mockResolvedValue(undefined);
    apiClientMock.renameWorkspaceAgentFile.mockResolvedValue(undefined);
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
    apiClientMock.disposeGlobal.mockResolvedValue(true);
    apiClientMock.reloadPublicPersonalAgentRuntime.mockResolvedValue({ reloaded: true, message: "reloaded" });
    apiClientMock.connectAgentConfigProgress.mockResolvedValue({ close: vi.fn() });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("does not create a public worktree when no server is initialized", async () => {
    apiClientMock.listPublicAgentRepositories.mockResolvedValue([uninitializedRepository()]);

    const { view } = renderPanel();

    await waitFor(() => expect(apiClientMock.getPublicAgentConfigStatus).toHaveBeenCalled());
    expect(apiClientMock.listPublicAgentWorktrees).not.toHaveBeenCalled();
    expect(apiClientMock.createPublicAgentWorktree).not.toHaveBeenCalled();
    expect(view.getByText("创建公共 worktree")).toBeTruthy();
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

  it("leaves public conflict interaction to the shared Git changes panel", async () => {
    apiClientMock.getPublicAgentGitConflictFiles.mockResolvedValueOnce({
      files: ["opencode/agents/test-design-orchestrator.md"]
    });

    const { view } = renderPanel();

    await waitFor(() => expect(apiClientMock.listPublicAgentWorktrees).toHaveBeenCalledWith("linux-1"));
    expect(apiClientMock.getPublicAgentGitConflictFiles).not.toHaveBeenCalled();
    expect(apiClientMock.getPublicAgentDiff).not.toHaveBeenCalled();
    expect(view.queryByText("公共级存在 1 个冲突文件")).toBeNull();
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

  it("automatically mounts the current user's public worktree", async () => {
    const { view } = renderPanel();

    expect(await view.findByText("worktree · change-agent-md · 测试服务器")).toBeTruthy();
    expect(view.queryByText("/data/opencode-public-worktrees/change-agent-md/opencode")).toBeNull();
    expect(view.getByRole("button", { name: "更多操作" })).toBeTruthy();
    expect(view.queryByText("更新公共配置")).toBeNull();
    expect(view.getByText("创建公共 worktree")).toBeTruthy();
    expect(view.getByText("切换公共 worktree")).toBeTruthy();
  });

  it("keeps Agent config update buttons in the left tree for authorized scopes", async () => {
    const { view } = renderPanel();

    await view.findByText("worktree · change-agent-md · 测试服务器");
    const rootRows = view.container.querySelectorAll(".agent-root-row");
    expect(rootRows).toHaveLength(2);
    expect([...rootRows[0].querySelectorAll(".agent-root-actions > button, .agent-root-actions > .agent-more-menu-container > button")]
      .map((button) => button.getAttribute("aria-label")))
      .toEqual(["更多操作", "Agent 配置更新（公共）"]);
    expect([...rootRows[1].querySelectorAll(".agent-root-actions > button")]
      .map((button) => button.getAttribute("aria-label")))
      .toEqual(["初始化应用 Agent/Skill 配置包", "Agent 配置更新（应用）"]);
    await fireEvent.click(view.getByRole("button", { name: "Agent 配置更新（公共）" }));
    await waitFor(() => {
      const events = (view.emitted("personal-runtime-reload") ?? []) as unknown[][];
      expect(events.at(-1)?.[0]).toMatchObject({
        scope: "PUBLIC",
        worktreeId: "agw_1234567890abcdef",
        linuxServerId: "linux-1"
      });
    });

    await fireEvent.click(view.getByRole("button", { name: "Agent 配置更新（应用）" }));
    await waitFor(() => {
      const events = (view.emitted("personal-runtime-reload") ?? []) as unknown[][];
      expect(events.at(-1)?.[0]).toMatchObject({
        scope: "WORKSPACE",
        workspaceId: "wrk_1234567890abcdef"
      });
    });
  });

  it("disables both scope update buttons while any user runtime is busy", async () => {
    const { view } = renderPanel(undefined, { runtimeBusy: true });

    await view.findByText("worktree · change-agent-md · 测试服务器");
    expect((view.getByRole("button", { name: "Agent 配置更新（公共）" }) as HTMLButtonElement).disabled).toBe(true);
    expect((view.getByRole("button", { name: "Agent 配置更新（应用）" }) as HTMLButtonElement).disabled).toBe(true);
  });


  it("explicitly creates and mounts the current user's stable public worktree", async () => {
    const secondRepository = {
      ...initializedRepository(),
      linuxServerId: "linux-2",
      serverName: "备用服务器",
      gitRootPath: "/data/public-config-2",
      configDirPath: "/data/public-config-2/opencode",
      worktreeRootPath: "/data/public-worktrees-2",
      currentBranch: "release"
    };
    const createdWorktree = {
      ...publicWorktreeOption(),
      worktreeId: "agw_public_linux_2",
      linuxServerId: "linux-2",
      worktreeName: "public-usr_admin",
      branch: "public-usr_admin",
      rootPath: "/data/public-worktrees-2/public-usr_admin"
    };
    apiClientMock.listPublicAgentRepositories.mockResolvedValue([initializedRepository(), secondRepository]);
    apiClientMock.createPublicAgentWorktree.mockResolvedValue(createdWorktree);
    const { view, workbench } = renderPanel();

    await waitFor(() => expect(apiClientMock.listPublicAgentWorktrees).toHaveBeenCalledWith("linux-1"));
    await fireEvent.click(view.getByText("创建公共 worktree"));
    const dialog = await view.findByRole("dialog", { name: "创建公共 worktree" });
    await fireEvent.update(within(dialog).getByLabelText("服务器"), "linux-2");
    await fireEvent.click(within(dialog).getByRole("button", { name: "创建并切换" }));

    await waitFor(() => expect(apiClientMock.createPublicAgentWorktree).toHaveBeenCalledWith(expect.objectContaining({
      baseName: "public-personal",
      branch: "release",
      linuxServerId: "linux-2",
      operationId: expect.stringMatching(/^aco_/)
    })));
    await waitFor(() => expect(workbench.publicWorktree?.worktreeId).toBe("agw_public_linux_2"));
    expect(workbench.publicConfigLinuxServerId).toBe("linux-2");
    await waitFor(() => expect(notifyMock.notifySuccess).toHaveBeenCalledWith(
      "公共 worktree 已就绪",
      "分支 public-usr_admin"
    ));
  });

  it("switches to the current user's existing stable public worktree on another server", async () => {
    const secondRepository = {
      ...initializedRepository(),
      linuxServerId: "linux-2",
      serverName: "备用服务器",
      gitRootPath: "/data/public-config-2",
      configDirPath: "/data/public-config-2/opencode",
      worktreeRootPath: "/data/public-worktrees-2"
    };
    const secondWorktree = {
      ...publicWorktreeOption(),
      worktreeId: "agw_public_linux_2",
      linuxServerId: "linux-2",
      worktreeName: "public-usr_admin",
      branch: "public-usr_admin",
      rootPath: "/data/public-worktrees-2/public-usr_admin"
    };
    apiClientMock.listPublicAgentRepositories.mockResolvedValue([initializedRepository(), secondRepository]);
    apiClientMock.listPublicAgentWorktrees.mockImplementation(async (linuxServerId: string) =>
      linuxServerId === "linux-1" ? [publicWorktreeOption()] : [secondWorktree]
    );
    const { view, workbench } = renderPanel();

    await waitFor(() => expect(apiClientMock.listPublicAgentWorktrees).toHaveBeenCalledWith("linux-1"));
    await fireEvent.click(view.getByText("切换公共 worktree"));
    const dialog = await view.findByRole("dialog", { name: "切换公共 worktree" });
    await fireEvent.update(within(dialog).getByLabelText("服务器"), "linux-2");
    await waitFor(() => expect(apiClientMock.listPublicAgentWorktrees).toHaveBeenCalledWith("linux-2"));
    await fireEvent.click(within(dialog).getByRole("button", { name: "确定" }));

    await waitFor(() => expect(workbench.publicWorktree?.worktreeId).toBe("agw_public_linux_2"));
    expect(workbench.publicConfigLinuxServerId).toBe("linux-2");
    expect(apiClientMock.createPublicAgentWorktree).not.toHaveBeenCalled();
  });

  it("shows the selected public worktree server and physical config directory", async () => {
    const { view } = renderPanel((store) => {
      store.publicWorktree = publicWorktreeOption();
      store.publicConfigLinuxServerId = "linux-1";
    });

    expect(await view.findByText("worktree · change-agent-md · 测试服务器")).toBeTruthy();
    expect(view.queryByText("/data/opencode-public-worktrees/change-agent-md/opencode")).toBeNull();
    expect(view.container.querySelector(".agent-root-main")?.getAttribute("title"))
      .toBe("worktree · change-agent-md · 测试服务器 · /data/opencode-public-worktrees/change-agent-md/opencode");
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
    const activePath = "agent-public:agw_1234567890abcdef:linux-1:agents/review.md";
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
        worktreeId: "agw_1234567890abcdef",
        linuxServerId: "linux-1",
        readonly: false,
        activate: false,
        closeOnNotFound: true
      });
    });
    expect(apiClientMock.readPublicAgentFile).not.toHaveBeenCalled();
  });

  it("does not overwrite an active Agent editor with unsaved changes", async () => {
    const activePath = "agent-public:agw_1234567890abcdef:linux-1:agents/review.md";
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
    expect(view.queryByRole("button", { name: "Agent 配置更新（公共）" })).toBeNull();
    expect(view.queryByRole("button", { name: "Agent 配置更新（应用）" })).toBeNull();
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
        absolutePath: "/data/opencode-public-worktrees/change-agent-md/opencode/.gitignore",
        workspaceId: undefined,
        worktreeId: "agw_1234567890abcdef",
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

  it("emits the real workspace Agent absolute path instead of the synthetic tab route", async () => {
    apiClientMock.listWorkspaceAgentFiles.mockResolvedValue([
      {
        path: "agents/git-worktree-opencode-baseline-20260717.md",
        name: "git-worktree-opencode-baseline-20260717.md",
        type: "file"
      }
    ]);
    const { view } = renderPanel();

    await waitFor(() => expect(apiClientMock.listWorkspaceAgentFiles).toHaveBeenCalled());
    await fireEvent.click(view.getByText("应用级"));
    await fireEvent.click(await view.findByText("git-worktree-opencode-baseline-20260717.md"));

    await waitFor(() => {
      const events = (view.emitted("openFile") ?? []) as unknown[][];
      expect(events.at(-1)?.[0]).toMatchObject({
        scope: "WORKSPACE",
        path: "agents/git-worktree-opencode-baseline-20260717.md",
        absolutePath: "/workspace/F-COSS/workspace/.opencode/agents/git-worktree-opencode-baseline-20260717.md",
        workspaceId: "wrk_1234567890abcdef"
      });
    });
  });


  it("creates an OpenCode workspace Agent without inserting hyphens between English letters", async () => {
    const { view } = renderPanel();

    await waitFor(() => expect(apiClientMock.getWorkspaceAgentConfigStatus).toHaveBeenCalled());
    await fireEvent.click(view.getByRole("button", { name: "初始化应用 Agent/Skill 配置包" }));
    const dialog = await view.findByRole("dialog", { name: "初始化应用 Agent/Skill 配置包" });
    expect(within(dialog).getByRole("radio", { name: "Agent" }).getAttribute("aria-checked")).toBe("true");
    await fireEvent.update(within(dialog).getByLabelText("Agent 名称"), "Payment Agent");
    await fireEvent.click(within(dialog).getByRole("button", { name: "创建" }));

    await waitFor(() => expect(apiClientMock.writeWorkspaceAgentFile).toHaveBeenCalledTimes(1));
    expect(apiClientMock.writeWorkspaceAgentFile.mock.calls.map((call) => call.slice(0, 2))).toEqual([
      ["wrk_1234567890abcdef", "agents/payment-agent.md"]
    ]);
    const agentContent = String(apiClientMock.writeWorkspaceAgentFile.mock.calls[0]?.[2]);
    expect(agentContent).toContain("description: Payment Agent application workspace agent");
    expect(agentContent).toContain("mode: primary");
    expect(agentContent).not.toContain("SKILL.md");
  });

  it("creates an OpenCode workspace Skill with its own template and resource directories", async () => {
    const { view } = renderPanel();

    await waitFor(() => expect(apiClientMock.getWorkspaceAgentConfigStatus).toHaveBeenCalled());
    await fireEvent.click(view.getByRole("button", { name: "初始化应用 Agent/Skill 配置包" }));
    const dialog = await view.findByRole("dialog", { name: "初始化应用 Agent/Skill 配置包" });
    await fireEvent.click(within(dialog).getByRole("radio", { name: "Skill" }));
    expect(within(dialog).getByRole("radio", { name: "Skill" }).getAttribute("aria-checked")).toBe("true");
    await fireEvent.update(within(dialog).getByLabelText("Skill 名称"), "支付测试技能");
    await fireEvent.click(within(dialog).getByRole("button", { name: "创建" }));

    await waitFor(() => expect(apiClientMock.writeWorkspaceAgentFile).toHaveBeenCalledTimes(3));
    expect(apiClientMock.writeWorkspaceAgentFile.mock.calls.map((call) => call.slice(0, 2))).toEqual([
      ["wrk_1234567890abcdef", "skills/zhi-fu-ce-shi-ji-neng/SKILL.md"],
      ["wrk_1234567890abcdef", "skills/zhi-fu-ce-shi-ji-neng/rules/README.md"],
      ["wrk_1234567890abcdef", "skills/zhi-fu-ce-shi-ji-neng/templates/README.md"]
    ]);
    const skillContent = String(apiClientMock.writeWorkspaceAgentFile.mock.calls[0]?.[2]);
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

  it("renames an application Agent file on double click and reports both Git paths", async () => {
    apiClientMock.listWorkspaceAgentFiles.mockImplementation(async (_workspaceId: string, path: string) => path === ""
      ? [{ path: "agents", name: "agents", type: "directory" }]
      : [{ path: "agents/review.md", name: "review.md", type: "file" }]);
    const { view } = renderPanel();

    await fireEvent.click(view.getByRole("button", { name: /^应用级/ }));
    await fireEvent.click(await view.findByRole("button", { name: "agents" }));
    const fileRow = await view.findByRole("button", { name: "review.md" });
    await fireEvent.dblClick(fileRow);
    const input = view.getByLabelText("重命名应用 Agent 文件");
    await fireEvent.update(input, "payment-review.md");
    await fireEvent.keyDown(input, { key: "Enter" });

    await waitFor(() => expect(apiClientMock.renameWorkspaceAgentFile).toHaveBeenCalledWith(
      "wrk_1234567890abcdef",
      "agents/review.md",
      "payment-review.md",
      undefined
    ));
    await waitFor(() => expect(view.emitted("files-mutated")).toEqual([[
      {
        scope: "WORKSPACE",
        paths: ["agents/review.md", "agents/payment-review.md"],
        renamed: {
          path: "agents/review.md",
          nextPath: "agents/payment-review.md",
          type: "file"
        }
      }
    ]]));
  });

  it("keeps public and application Agent files read-only for users without write permission", async () => {
    apiClientMock.listPublicAgentFiles.mockImplementation(async (path: string) => path === ""
      ? [{ path: "agents", name: "agents", type: "directory" }]
      : [{ path: "agents/public.md", name: "public.md", type: "file" }]);
    apiClientMock.listWorkspaceAgentFiles.mockImplementation(async (_workspaceId: string, path: string) => path === ""
      ? [{ path: "agents", name: "agents", type: "directory" }]
      : [{ path: "agents/app.md", name: "app.md", type: "file" }]);
    const { view } = renderPanel(undefined, { canWrite: false });

    await fireEvent.click(await view.findByRole("button", { name: "agents" }));
    const publicFile = await view.findByRole("button", { name: "public.md" });
    await fireEvent.click(publicFile);
    await fireEvent.dblClick(publicFile);
    await fireEvent.click(view.getByRole("button", { name: /^应用级/ }));
    const agentDirectories = await view.findAllByRole("button", { name: "agents" });
    await fireEvent.click(agentDirectories.at(-1)!);
    const applicationFile = await view.findByRole("button", { name: "app.md" });
    await fireEvent.click(applicationFile);
    await fireEvent.dblClick(applicationFile);

    expect(view.queryByLabelText("重命名应用 Agent 文件")).toBeNull();
    expect(apiClientMock.renameWorkspaceAgentFile).not.toHaveBeenCalled();
    const openedFiles = ((view.emitted("openFile") ?? []) as unknown[][]).map((event) => event[0]);
    expect(openedFiles).toEqual(expect.arrayContaining([
      expect.objectContaining({ scope: "PUBLIC", path: "agents/public.md", readonly: true }),
      expect.objectContaining({ scope: "WORKSPACE", path: "agents/app.md", readonly: true })
    ]));
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
  options?: { canWrite?: boolean; hideHeader?: boolean; activePath?: string; runtimeBusy?: boolean }
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
      activePath: options?.activePath,
      runtimeBusy: options?.runtimeBusy ?? false
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
    agentDirectory: scope === "WORKSPACE"
      ? "/workspace/F-COSS/workspace/.opencode"
      : "/data/opencode-public-config/opencode",
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
