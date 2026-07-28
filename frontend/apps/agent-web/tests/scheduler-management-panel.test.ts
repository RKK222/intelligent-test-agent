import { QueryClient, VueQueryPlugin } from "@tanstack/vue-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, waitFor } from "@testing-library/vue";
import type { Component } from "vue";
import { BackendApiError, type BackendApiClient } from "@test-agent/backend-api";
import type {
  ApplicationDefinition,
  ApplicationGitRefreshResult,
  CurrentUser,
  OpencodeRuntimeManagementOverview,
  PublicAgentRepositoryStatus
} from "@test-agent/shared-types";
import SystemManagementPanel from "../src/components/system/SystemManagementPanel.vue";
import OpencodePublicConfigManagementPanel from "../src/components/system/OpencodePublicConfigManagementPanel.vue";
import ApplicationGitRefreshManagementPanel from "../src/components/system/ApplicationGitRefreshManagementPanel.vue";

function queryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

const currentUser: CurrentUser = {
  userId: "usr_admin",
  username: "admin",
  unifiedAuthId: "AUTH_1",
  roles: ["SUPER_ADMIN"]
};

const emptyRuntimeOverview: OpencodeRuntimeManagementOverview = {
  generatedAt: "2026-06-25T00:00:00Z",
  summary: {
    linuxServers: 0,
    readyLinuxServers: 0,
    backendProcesses: 0,
    readyBackendProcesses: 0,
    containers: 0,
    readyContainers: 0,
    managers: 0,
    connectedManagers: 0,
    managerBackendConnections: 0,
    opencodeProcesses: 0,
    runningOpencodeProcesses: 0,
    userBindings: 0
  },
  linuxServers: [],
  backendProcesses: [],
  containers: [],
  managers: [],
  managerBackendConnections: [],
  opencodeProcesses: { items: [], page: 1, size: 20, total: 0 }
};

const publicRepository: PublicAgentRepositoryStatus = {
  linuxServerId: "linux-1",
  serverName: "linux-1",
  gitRootPath: "/data/opencode-public-config",
  configDirPath: "/data/opencode-public-config/opencode",
  worktreeRootPath: "/data/opencode-public-worktrees",
  status: "UNINITIALIZED",
  initialized: false,
  initializationAllowed: true,
  currentBranch: null,
  commitHash: null,
  message: "未初始化"
};

const application: ApplicationDefinition = {
  appId: "app_gcms",
  appName: "F-GCMS",
  enabled: true
};

const applicationRefreshResult: ApplicationGitRefreshResult = {
  appId: "app_gcms",
  appName: "F-GCMS",
  totalGroups: 1,
  updatedGroups: 1,
  unchangedGroups: 0,
  failedGroups: 0,
  groups: [{
    versionId: "awv_1",
    repositoryId: "repo_1",
    repositoryName: "GCMS",
    version: "20260707",
    branch: "feature_testagent_20260707",
    workspaceCount: 2,
    previousCommitHash: "commit_before",
    commitHash: "commit_after",
    status: "UPDATED",
    errorCode: null,
    message: "已刷新 feature，并触发相关 worktree 收敛"
  }]
};

function api(overrides: Partial<BackendApiClient> = {}) {
  return {
    createXxlJobSsoTicket: vi.fn().mockRejectedValue(new Error("XXL-JOB unavailable in navigation test")),
    getOpencodeRuntimeManagementOverview: vi.fn().mockResolvedValue(emptyRuntimeOverview),
    listPublicAgentRepositories: vi.fn().mockResolvedValue([publicRepository]),
    listPublicAgentBranches: vi.fn().mockResolvedValue(["main", "develop"]),
    pullPublicAgentRepository: vi.fn().mockResolvedValue({
      ...publicRepository,
      status: "READY",
      initialized: true,
      currentBranch: "main",
      commitHash: "def5678",
      message: "已拉取"
    }),
    initializePublicAgentRepository: vi.fn().mockResolvedValue({
      ...publicRepository,
      status: "READY",
      initialized: true,
      currentBranch: "main",
      commitHash: "abc1234",
      message: "已初始化"
    }),
    listApplications: vi.fn().mockResolvedValue([application]),
    refreshApplicationGit: vi.fn().mockResolvedValue(applicationRefreshResult),
    ...overrides
  } as Partial<BackendApiClient> as BackendApiClient;
}

function renderWithApi(component: Component, backendApi: BackendApiClient, user: CurrentUser = currentUser) {
  const client = queryClient();
  const view = render(component, {
    props: { currentUser: user },
    global: {
      plugins: [[VueQueryPlugin, { queryClient: client }]],
      stubs: {
        ElButton: { emits: ["click"], template: `<button type="button" @click="$emit('click')"><slot /></button>` },
        ElInput: {
          props: ["modelValue", "placeholder"],
          emits: ["update:modelValue"],
          template: `<input :placeholder="placeholder" :value="modelValue" @input="$emit('update:modelValue', $event.target.value)" />`
        },
        ElSelect: {
          props: ["modelValue", "placeholder"],
          emits: ["update:modelValue"],
          template: `<select :aria-label="placeholder" :value="modelValue" @change="$emit('update:modelValue', $event.target.value)"><slot /></select>`
        },
        ElOption: { props: ["label", "value"], template: `<option :value="value">{{ label }}</option>` }
      },
      provide: { api: backendApi }
    }
  });
  return { ...view, queryClient: client };
}

describe("scheduler management panel", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("system management switches between scheduler and runtime management", async () => {
    const backendApi = api();
    const view = renderWithApi(SystemManagementPanel, backendApi);

    expect(await view.findByText("定时任务管理", { selector: ".ta-system-menu-text" })).toBeTruthy();
    expect(view.getByTitle("XXL-JOB 定时任务管理")).toBeTruthy();
    await fireEvent.click(view.getByText("运行管理", { selector: ".ta-system-menu-text" }));

    await waitFor(() => expect(backendApi.getOpencodeRuntimeManagementOverview).toHaveBeenCalled());
    expect(await view.findByText("暂无服务器 / Java 进程")).toBeTruthy();
    view.queryClient.clear();
  });

  it("system management exposes config management and initializes public opencode repository", async () => {
    const backendApi = api();
    const view = renderWithApi(SystemManagementPanel, backendApi);

    await fireEvent.click(view.getByText("配置管理", { selector: ".ta-system-menu-text" }));

    expect(await view.findByText("TestAgent公共配置管理")).toBeTruthy();
    expect(await view.findByText("/data/opencode-public-config")).toBeTruthy();
    await fireEvent.click(view.getByRole("button", { name: "初始化" }));

    await waitFor(() => expect(backendApi.listPublicAgentBranches).toHaveBeenCalled());
    await fireEvent.click(view.getByRole("button", { name: "确定" }));

    await waitFor(() =>
      expect(backendApi.initializePublicAgentRepository).toHaveBeenCalledWith("linux-1", "main", expect.stringMatching(/^aco_/))
    );
    expect(await view.findByText("服务器 linux-1 公共配置仓库已初始化")).toBeTruthy();
    view.queryClient.clear();
  });

  it("keeps public repository management unavailable to non-super-admin users", async () => {
    const backendApi = api();
    const appAdmin: CurrentUser = {
      ...currentUser,
      userId: "usr_app_admin",
      username: "app-admin",
      roles: ["APP_ADMIN"]
    };
    const view = renderWithApi(OpencodePublicConfigManagementPanel, backendApi, appAdmin);

    expect(await view.findByText("当前账号无配置管理权限")).toBeTruthy();
    expect(view.queryByRole("button", { name: "初始化" })).toBeNull();
    expect(view.queryByRole("button", { name: "拉取更新" })).toBeNull();
    expect(backendApi.listPublicAgentRepositories).not.toHaveBeenCalled();
    view.queryClient.clear();
  });

  it("lets super admin refresh every feature and related worktree for one application", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const backendApi = api();
    const view = renderWithApi(ApplicationGitRefreshManagementPanel, backendApi);

    expect(await view.findByText("F-GCMS")).toBeTruthy();
    await fireEvent.click(view.getByRole("button", { name: "刷新应用 Git：F-GCMS" }));

    await waitFor(() => expect(backendApi.refreshApplicationGit).toHaveBeenCalledWith("app_gcms"));
    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining("全部 feature 仓库组"));
    expect(await view.findByText("共 1 组：成功 1，更新 1，已是最新 0，失败 0")).toBeTruthy();
    await fireEvent.click(view.getByText("查看仓库组明细"));
    expect(await view.findByText(/GCMS · 20260707 · feature_testagent_20260707 · 2 个 workspace/)).toBeTruthy();
    view.queryClient.clear();
  });

  it("keeps application Git refresh unavailable to non-super-admin users", async () => {
    const backendApi = api();
    const appAdmin: CurrentUser = { ...currentUser, roles: ["APP_ADMIN"] };
    const view = renderWithApi(ApplicationGitRefreshManagementPanel, backendApi, appAdmin);

    expect(await view.findByText("当前账号无配置管理权限")).toBeTruthy();
    expect(backendApi.listApplications).not.toHaveBeenCalled();
    expect(backendApi.refreshApplicationGit).not.toHaveBeenCalled();
    view.queryClient.clear();
  });

  it("system management lets super admin pull initialized public opencode repository", async () => {
    const initializedPublicRepository = {
      ...publicRepository,
      status: "READY",
      initialized: true,
      currentBranch: "master",
      commitHash: "abc1234",
      message: "已初始化"
    };
    const backendApi = api({
      listPublicAgentRepositories: vi.fn().mockResolvedValue([initializedPublicRepository])
    });
    const view = renderWithApi(SystemManagementPanel, backendApi);

    await fireEvent.click(view.getByText("配置管理", { selector: ".ta-system-menu-text" }));

    expect(await view.findByText("TestAgent公共配置管理")).toBeTruthy();
    await fireEvent.click(view.getByRole("button", { name: "拉取更新" }));

    await waitFor(() =>
      expect(backendApi.pullPublicAgentRepository).toHaveBeenCalledWith("linux-1", "master", expect.stringMatching(/^aco_/), false)
    );
    expect(await view.findByText("服务器 linux-1 公共配置仓库已拉取到最新")).toBeTruthy();
    view.queryClient.clear();
  });

  it("shows dirty public repository files and retries pull only after explicit discard confirmation", async () => {
    const dirtyRepository = {
      ...publicRepository,
      status: "CONFLICT",
      initialized: true,
      currentBranch: "main",
      message: "Git 工作树存在未提交变更：opencode/agents/review.md"
    };
    const backendApi = api({
      listPublicAgentRepositories: vi.fn().mockResolvedValue([dirtyRepository])
    });
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const view = renderWithApi(SystemManagementPanel, backendApi);

    await fireEvent.click(view.getByText("配置管理", { selector: ".ta-system-menu-text" }));

    expect(await view.findByText("存在本地变更")).toBeTruthy();
    expect(await view.findByText(/opencode\/agents\/review\.md/)).toBeTruthy();
    expect(await view.findByText(/这是该服务器的共享运行副本/)).toBeTruthy();
    await fireEvent.click(view.getByRole("button", { name: "放弃本地变更并拉取" }));

    await waitFor(() => expect(backendApi.pullPublicAgentRepository).toHaveBeenCalledWith(
      "linux-1",
      "main",
      expect.stringMatching(/^aco_/),
      true
    ));
    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining("当前管理员个人公共 worktree和共享运行副本"));
    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining("其他管理员的个人 worktree不受影响"));
    view.queryClient.clear();
  });

  it("identifies a dirty current-admin public worktree even when the shared repository is clean", async () => {
    const initializedPublicRepository = {
      ...publicRepository,
      status: "READY",
      initialized: true,
      currentBranch: "main",
      commitHash: "abc1234",
      message: "已初始化"
    };
    const personalPath = "/data/testagent/data/agent-opencode/.configdev/public-usr_admin";
    const pull = vi.fn()
      .mockRejectedValueOnce(new BackendApiError(409, {
        success: false,
        code: "CONFLICT",
        message: `当前管理员公共 Agent 个人 worktree 存在未提交变更：opencode/agents/review.md；仓库路径：${personalPath}`,
        traceId: "trace_dirty_personal",
        retryable: false,
        details: {
          path: personalPath,
          repositoryKind: "PERSONAL_WORKTREE",
          dirtyFiles: ["opencode/agents/review.md"],
          discardLocalChangesAllowed: true
        }
      }))
      .mockResolvedValueOnce(initializedPublicRepository);
    const backendApi = api({
      listPublicAgentRepositories: vi.fn().mockResolvedValue([initializedPublicRepository]),
      pullPublicAgentRepository: pull
    });
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const view = renderWithApi(SystemManagementPanel, backendApi);

    await fireEvent.click(view.getByText("配置管理", { selector: ".ta-system-menu-text" }));
    await fireEvent.click(await view.findByRole("button", { name: "拉取更新" }));

    expect((await view.findAllByText(new RegExp(personalPath.replaceAll("/", "\\/")))).length).toBeGreaterThan(0);
    expect(await view.findByText(/当前管理员个人公共 worktree 存在本地变更/)).toBeTruthy();
    await fireEvent.click(view.getByRole("button", { name: "放弃本地变更并拉取" }));

    await waitFor(() => expect(pull).toHaveBeenLastCalledWith(
      "linux-1",
      "main",
      expect.stringMatching(/^aco_/),
      true
    ));
    view.queryClient.clear();
  });
});
