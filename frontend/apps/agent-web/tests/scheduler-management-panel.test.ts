import { QueryClient, VueQueryPlugin } from "@tanstack/vue-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, waitFor } from "@testing-library/vue";
import type { Component } from "vue";
import type { BackendApiClient } from "@test-agent/backend-api";
import type {
  ApplicationGitRefreshScope,
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

const applicationScope: ApplicationGitRefreshScope = {
  appId: "app_gcms",
  appName: "F-GCMS",
  enabled: true,
  totalGroups: 2,
  groups: [{
    repositoryId: "repo_1",
    repositoryName: "GCMS",
    version: "20260707",
    branch: "feature_testagent_20260707",
    workspaceCount: 1,
    workspaces: [{
      versionId: "awv_1",
      applicationWorkspaceId: "aws_login",
      workspaceName: "登录测试",
      directoryPath: "F-GCMS/login",
      enabled: true
    }]
  }, {
    repositoryId: "repo_1",
    repositoryName: "GCMS",
    version: "20260708",
    branch: "feature_testagent_20260708",
    workspaceCount: 1,
    workspaces: [{
      versionId: "awv_2",
      applicationWorkspaceId: "aws_order",
      workspaceName: "订单测试",
      directoryPath: "F-GCMS/order",
      enabled: true
    }]
  }]
};

const applicationWithoutFeatureBranch: ApplicationGitRefreshScope = {
  appId: "app_empty",
  appName: "尚未创建分支的应用",
  enabled: true,
  totalGroups: 0,
  groups: []
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
    getPublicAgentConfigRollout: vi.fn().mockResolvedValue(null),
    listPublicAgentBranches: vi.fn().mockResolvedValue(["main", "develop"]),
    updatePublicAgentConfig: vi.fn().mockResolvedValue({
      operationId: "aco_update",
      status: "SUCCEEDED",
      commitHash: "def5678"
    }),
    supersedePublicAgentConfigRollout: vi.fn().mockResolvedValue({
      operationId: "aco_supersede",
      status: "SUCCEEDED",
      commitHash: "commit_fixed"
    }),
    stopOpencodeRuntimeManagedProcess: vi.fn().mockResolvedValue({
      command: "stop",
      status: "STOPPED",
      port: 4096,
      healthy: false,
      traceId: "trace-stop"
    }),
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
    listApplicationGitRefreshScopes: vi.fn().mockResolvedValue([applicationScope]),
    refreshApplicationGit: vi.fn().mockResolvedValue(applicationRefreshResult),
    refreshApplicationGitGroup: vi.fn().mockResolvedValue(applicationRefreshResult),
    ...overrides
  } as Partial<BackendApiClient> as BackendApiClient;
}

function renderWithApi(
  component: Component,
  backendApi: BackendApiClient,
  user: CurrentUser = currentUser,
  props: Record<string, unknown> = {}
) {
  const client = queryClient();
  const view = render(component, {
    props: { currentUser: user, ...props },
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
        ElOption: { props: ["label", "value"], template: `<option :value="value">{{ label }}</option>` },
        SupportAccessPanel: {
          props: ["currentUser"],
          template: `<div data-testid="support-access-panel">只读排查授权面板</div>`
        }
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

  it("reveals the support panel only after the global super-admin gesture requests it without changing identity", async () => {
    const backendApi = api();
    const view = renderWithApi(SystemManagementPanel, backendApi);

    expect(view.queryByText("问题排查只读访问", { selector: ".ta-system-menu-text" })).toBeNull();
    await view.rerender({ currentUser, supportAccessRequested: true });

    expect(await view.findByText("问题排查只读访问", { selector: ".ta-system-menu-text" })).toBeTruthy();
    expect(view.getByTestId("support-access-panel")).toBeTruthy();
    expect(view.emitted().supportAccessOpened).toHaveLength(1);
    expect(currentUser.userId).toBe("usr_admin");
    expect(currentUser.roles).toEqual(["SUPER_ADMIN"]);
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
    expect(await view.findByText("feature_testagent_20260707")).toBeTruthy();
    expect(await view.findByText("feature_testagent_20260708")).toBeTruthy();
    expect(await view.findByText("登录测试")).toBeTruthy();
    expect(await view.findByText("订单测试")).toBeTruthy();
    await fireEvent.click(view.getByRole("button", { name: "刷新应用 Git：F-GCMS" }));

    await waitFor(() => expect(backendApi.refreshApplicationGit).toHaveBeenCalledWith("app_gcms"));
    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining("feature_testagent_20260707、feature_testagent_20260708"));
    expect(await view.findByText("共 1 组：成功 1，更新 1，已是最新 0，失败 0")).toBeTruthy();
    await fireEvent.click(view.getByText("查看仓库组明细"));
    expect(await view.findByText(/GCMS · 20260707 · feature_testagent_20260707 · 2 个 workspace/)).toBeTruthy();
    view.queryClient.clear();
  });

  it("only lists applications that have a concrete feature branch", async () => {
    const backendApi = api({
      listApplicationGitRefreshScopes: vi.fn().mockResolvedValue([
        applicationWithoutFeatureBranch,
        applicationScope
      ])
    });
    const view = renderWithApi(ApplicationGitRefreshManagementPanel, backendApi);

    expect(await view.findByText("F-GCMS")).toBeTruthy();
    expect(view.queryByText("尚未创建分支的应用")).toBeNull();
    view.queryClient.clear();
  });

  it("lets super admin refresh one feature branch without selecting other branches", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const backendApi = api();
    const view = renderWithApi(ApplicationGitRefreshManagementPanel, backendApi);

    await view.findByText("feature_testagent_20260707");
    await fireEvent.click(view.getByRole("button", {
      name: "刷新分支：F-GCMS / feature_testagent_20260707"
    }));

    await waitFor(() => expect(backendApi.refreshApplicationGitGroup).toHaveBeenCalledWith("app_gcms", {
      repositoryId: "repo_1",
      version: "20260707",
      branch: "feature_testagent_20260707"
    }));
    expect(backendApi.refreshApplicationGit).not.toHaveBeenCalled();
    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining("不会影响同应用其它分支"));
    view.queryClient.clear();
  });

  it("keeps application Git refresh unavailable to non-super-admin users", async () => {
    const backendApi = api();
    const appAdmin: CurrentUser = { ...currentUser, roles: ["APP_ADMIN"] };
    const view = renderWithApi(ApplicationGitRefreshManagementPanel, backendApi, appAdmin);

    expect(await view.findByText("当前账号无配置管理权限")).toBeTruthy();
    expect(backendApi.listApplicationGitRefreshScopes).not.toHaveBeenCalled();
    expect(backendApi.refreshApplicationGit).not.toHaveBeenCalled();
    expect(backendApi.refreshApplicationGitGroup).not.toHaveBeenCalled();
    view.queryClient.clear();
  });

  it("starts one global public Agent refresh without selecting a server", async () => {
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
    expect(view.queryByRole("button", { name: "拉取更新" })).toBeNull();
    await fireEvent.click(view.getByRole("button", { name: "刷新公共 Agent Git" }));
    await fireEvent.click(await view.findByRole("button", { name: "开始全局刷新" }));

    await waitFor(() =>
      expect(backendApi.updatePublicAgentConfig).toHaveBeenCalledWith("main", expect.stringMatching(/^aco_/), false)
    );
    expect(await view.findByText(/已发起所有服务器刷新到远端目标 commit def5678/)).toBeTruthy();
    expect(backendApi.pullPublicAgentRepository).not.toHaveBeenCalled();
    view.queryClient.clear();
  });

  it("requires explicit confirmation before resetting dirty shared runtime replicas", async () => {
    const dirtyRepository = {
      ...publicRepository,
      status: "CONFLICT",
      initialized: true,
      currentBranch: "main",
      localChangesPresent: true,
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
    await fireEvent.click(view.getByRole("button", { name: "刷新公共 Agent Git" }));
    await fireEvent.click(await view.findByRole("button", { name: "开始全局刷新" }));

    await waitFor(() => expect(backendApi.updatePublicAgentConfig).toHaveBeenCalledWith(
      "main",
      expect.stringMatching(/^aco_/),
      true
    ));
    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining("共享运行副本存在本地变更"));
    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining("个人 worktree 的 staged、unstaged、untracked 内容不会被清理"));
    view.queryClient.clear();
  });

  it("disables the global refresh during rollout and shows every server's progress and last_error", async () => {
    const backendApi = api({
      getPublicAgentConfigRollout: vi.fn().mockResolvedValue({
        rolloutId: "acr_1",
        status: "DRAINING",
        branch: "main",
        commitHash: "commit_target",
        failureReason: null,
        createdAt: "2026-07-28T00:00:00Z",
        updatedAt: "2026-07-28T00:00:01Z",
        completedAt: null,
        servers: [{
          linuxServerId: "linux-2",
          syncStatus: "RETRY",
          retryCount: 2,
          targetTotal: 3,
          targetPending: 2,
          targetDisposed: 1,
          targetAbandoned: 0,
          worktreeTotal: 2,
          worktreePending: 1,
          worktreeSynced: 1,
          lastError: "git fetch 超时",
          syncedAt: null,
          updatedAt: "2026-07-28T00:00:01Z"
        }]
      })
    });
    const view = renderWithApi(OpencodePublicConfigManagementPanel, backendApi);

    expect(await view.findByText("正在同步并排空")).toBeTruthy();
    expect(await view.findByText("git fetch 超时")).toBeTruthy();
    expect(await view.findByText(/补偿已收敛 1\/2，待用户处理 1/)).toBeTruthy();
    expect(view.getByRole("button", { name: "刷新公共 Agent Git" }).hasAttribute("disabled")).toBe(true);
    view.queryClient.clear();
  });

  it("shows the blocking rollout user and reuses the existing managed-process stop API", async () => {
    const latestRollout = {
      rolloutId: "acr_1",
      status: "DRAINING",
      branch: "main",
      commitHash: "commit_target",
      failureReason: null,
      createdAt: "2026-08-05T00:00:00Z",
      updatedAt: "2026-08-05T00:00:01Z",
      completedAt: null,
      servers: [{
        linuxServerId: "linux-1",
        syncStatus: "SYNCED",
        retryCount: 0,
        targetTotal: 2,
        targetPending: 1,
        targetDisposed: 1,
        targetAbandoned: 0,
        worktreeTotal: 0,
        worktreePending: 0,
        worktreeSynced: 0,
        lastError: "SESSION_RUNNING",
        syncedAt: "2026-08-05T00:00:00Z",
        updatedAt: "2026-08-05T00:00:01Z",
        pendingTargets: [{
          targetId: "act_pending",
          userId: "usr_zhangsan",
          username: "张三",
          linuxServerId: "linux-1",
          containerId: "container-1",
          port: 4096,
          processPid: 1234,
          processStartedAt: "2026-08-04T23:50:00Z",
          status: "RETRY_WAIT",
          retryCount: 3,
          nextRetryAt: "2026-08-05T00:00:05Z",
          lastError: "SESSION_RUNNING",
          forceStop: false,
          updatedAt: "2026-08-05T00:00:01Z"
        }]
      }]
    };
    const backendApi = api({
      getPublicAgentConfigRollout: vi.fn().mockResolvedValue(latestRollout)
    });
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const view = renderWithApi(OpencodePublicConfigManagementPanel, backendApi);

    expect(await view.findByText("张三")).toBeTruthy();
    expect(view.getAllByText("SESSION_RUNNING").length).toBeGreaterThan(0);
    await fireEvent.click(view.getByRole("button", { name: "关闭 张三 的 OpenCode" }));

    await waitFor(() => expect(backendApi.stopOpencodeRuntimeManagedProcess)
      .toHaveBeenCalledWith("container-1", 4096));
    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining("可能中断正在执行的任务"));
    expect(await view.findByText("已关闭 张三 的 OpenCode，正在等待排空任务确认")).toBeTruthy();
    view.queryClient.clear();
  });

  it("requires confirmation and supersedes a draining rollout with forced exact-process stop", async () => {
    const latestRollout = {
      rolloutId: "acr_stuck",
      status: "DRAINING",
      branch: "feature_config",
      commitHash: "commit_bad",
      failureReason: null,
      createdAt: "2026-08-03T04:46:14Z",
      updatedAt: "2026-08-03T04:46:19Z",
      completedAt: null,
      servers: [{
        linuxServerId: "linux-1",
        syncStatus: "SYNCED",
        retryCount: 0,
        targetTotal: 15,
        targetPending: 4,
        targetDisposed: 11,
        targetAbandoned: 0,
        worktreeTotal: 0,
        worktreePending: 0,
        worktreeSynced: 0,
        lastError: "TestAgent 服务响应异常",
        syncedAt: "2026-08-03T04:46:15Z",
        updatedAt: "2026-08-03T04:46:15Z"
      }]
    };
    const backendApi = api({
      getPublicAgentConfigRollout: vi.fn().mockResolvedValue(latestRollout),
      listPublicAgentBranches: vi.fn().mockResolvedValue(["main", "feature_config"])
    });
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const view = renderWithApi(OpencodePublicConfigManagementPanel, backendApi);

    await view.findByText("正在同步并排空");
    await fireEvent.click(view.getByRole("button", { name: "强制终止并替换发布" }));
    await fireEvent.update(
      await view.findByLabelText("操作原因（必填，最多 500 字）"),
      "修复 description 为空"
    );
    await fireEvent.click(view.getByRole("button", { name: "确认强制终止并替换" }));

    await waitFor(() => expect(backendApi.supersedePublicAgentConfigRollout).toHaveBeenCalledWith(
      expect.objectContaining({
        activeRolloutId: "acr_stuck",
        branch: "feature_config",
        reason: "修复 description 为空",
        discardLocalChanges: false
      })
    ));
    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining("强制停止其中仍未排空的 4 个"));
    view.queryClient.clear();
  });
});
