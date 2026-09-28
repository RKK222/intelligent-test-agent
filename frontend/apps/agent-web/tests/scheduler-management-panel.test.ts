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
    getApplicationAgentConfigRollouts: vi.fn().mockResolvedValue([]),
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
    restartOpencodeRuntimeManagedProcess: vi.fn().mockResolvedValue({
      command: "restart",
      status: "STARTED",
      port: 4096,
      healthy: true,
      traceId: "trace-restart"
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
    listLocalClientRolloutUsers: vi.fn().mockResolvedValue({ items: [], page: 1, size: 200, total: 0 }),
    ...overrides
  } as Partial<BackendApiClient> as BackendApiClient;
}

function renderWithApi(
  component: Component,
  backendApi: BackendApiClient,
  user: CurrentUser | null = currentUser,
  props: Record<string, unknown> = {}
) {
  const client = queryClient();
  const view = render(component, {
    props: {
      currentUser: user,
      activeKey: user?.roles?.includes("APP_ADMIN") ? "config" : "scheduler",
      pageActive: true,
      supportRevealed: false,
      supportActivationSequence: 0,
      ...props
    },
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
          props: ["currentUser", "activationSequence"],
          template: `<div data-testid="support-access-panel" :data-activation-sequence="activationSequence">只读排查授权面板</div>`
        },
        SettingsUserManagementPanel: {
          template: `<div data-testid="unified-user-management-panel">账号、权限与用户能力灰度</div>`
        },
        LocalClientVersionManagementPanel: {
          props: ["currentUser", "pageActive"],
          template: `<div data-testid="local-client-version-panel" :data-page-active="pageActive">客户端版本管理面板</div>`
        },
        TeamManagementPanel: {
          props: ["currentUser", "pageActive"],
          template: `<div data-testid="team-management-panel" :data-page-active="pageActive">团队管理面板</div>`
        }
      },
      provide: { api: backendApi }
    }
  });
  return { ...view, queryClient: client };
}

describe("scheduler management panel", () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it("system management requests runtime management and renders the selected controlled page", async () => {
    const backendApi = api();
    const view = renderWithApi(SystemManagementPanel, backendApi);

    expect(await view.findByText("定时任务管理", { selector: ".ta-system-menu-text" })).toBeTruthy();
    const traceMenuButton = view.getByText("Trace 可观测", { selector: ".ta-system-menu-text" }).closest("button");
    expect(traceMenuButton?.querySelector(".lucide-waypoints")).toBeTruthy();
    expect(view.getByTitle("XXL-JOB 定时任务管理")).toBeTruthy();
    await fireEvent.click(view.getByText("运行管理", { selector: ".ta-system-menu-text" }));

    expect(view.emitted().selectMenu?.[0]).toEqual(["runtime"]);
    await view.rerender({
      currentUser,
      activeKey: "runtime",
      pageActive: true,
      supportRevealed: false,
      supportActivationSequence: 0
    });
    await waitFor(() => expect(backendApi.getOpencodeRuntimeManagementOverview).toHaveBeenCalled());
    expect(await view.findByText("暂无服务器 / Java 进程")).toBeTruthy();
    view.queryClient.clear();
  });

  it("emits a page-tab request without mutating the controlled system page", async () => {
    const backendApi = api();
    const view = renderWithApi(SystemManagementPanel, backendApi, currentUser, {
      activeKey: "scheduler",
      pageActive: true,
      supportRevealed: false,
      supportActivationSequence: 0
    });

    expect(view.getByTitle("XXL-JOB 定时任务管理")).toBeTruthy();
    await fireEvent.click(view.getByText("运行管理", { selector: ".ta-system-menu-text" }));

    expect(view.emitted().selectMenu?.[0]).toEqual(["runtime"]);
    expect(view.getByTitle("XXL-JOB 定时任务管理")).toBeTruthy();
    expect(backendApi.getOpencodeRuntimeManagementOverview).not.toHaveBeenCalled();

    await view.rerender({
      currentUser,
      activeKey: "runtime",
      pageActive: true,
      supportRevealed: false,
      supportActivationSequence: 0
    });
    await waitFor(() => expect(backendApi.getOpencodeRuntimeManagementOverview).toHaveBeenCalled());
    view.queryClient.clear();
  });

  it("mounts the temporary support page only while its page tab is active", async () => {
    const view = renderWithApi(SystemManagementPanel, api(), currentUser, {
      activeKey: "support",
      pageActive: true,
      supportRevealed: true,
      supportActivationSequence: 7
    });

    expect(view.getByTestId("support-access-panel").getAttribute("data-activation-sequence")).toBe("7");

    await view.rerender({
      currentUser,
      activeKey: "support",
      pageActive: false,
      supportRevealed: true,
      supportActivationSequence: 7
    });
    expect(view.queryByTestId("support-access-panel")).toBeNull();
    view.queryClient.clear();
  });

  it("exposes local-client version management only through the controlled super-admin page", async () => {
    const view = renderWithApi(SystemManagementPanel, api(), currentUser, {
      activeKey: "localClientVersions",
      pageActive: true
    });

    expect(view.getByText("本地客户端版本", { selector: ".ta-system-menu-text" })).toBeTruthy();
    expect(view.getByTestId("local-client-version-panel").getAttribute("data-page-active")).toBe("true");
    await fireEvent.click(view.getByText("运行管理", { selector: ".ta-system-menu-text" }));
    expect(view.emitted().selectMenu?.[0]).toEqual(["runtime"]);
    view.queryClient.clear();
  });

  it("restores the super-admin scheduler default after the current user loads asynchronously", async () => {
    const backendApi = api();
    const view = renderWithApi(SystemManagementPanel, backendApi, null);

    expect(await view.findByText("当前账号无系统管理权限")).toBeTruthy();
    await view.rerender({ currentUser });

    expect(await view.findByText("定时任务管理", { selector: ".ta-system-menu-text" })).toBeTruthy();
    expect(view.getByTitle("XXL-JOB 定时任务管理")).toBeTruthy();
    view.queryClient.clear();
  });

  it("opens the generic memory health and rollout panel from system management", async () => {
    const backendApi = api({
      getQaMemoryAdminHealth: vi.fn().mockResolvedValue({
        enabled: true,
        memoryService: {
          available: true,
          status: "UP",
          version: "2.0.17",
          profiles: [{
            profileKey: "cpu:bge-small-zh-v1.5:512:fixed",
            provider: "CPU",
            model: "memory-bge-small-zh-v1.5",
            dimension: 512,
            fingerprint: "fixed",
            collection: "memory_cpu_v1",
            primary: true,
            available: true
          }],
          projectionBacklog: { pending: 0, processing: 0, dead: 0 }
        },
        primaryChatModelId: "enterprise/chat",
        primaryEmbeddingModelId: null,
        cpuEmbeddingModelId: "memory-bge-small-zh-v1.5",
        queuePending: 0,
        queueProcessing: 0,
        queueDead: 0
      }),
      getQaMemorySettings: vi.fn().mockResolvedValue({
        primaryChatModelId: "enterprise/chat",
        primaryEmbeddingModelId: null,
        cpuEmbeddingModelId: "memory-bge-small-zh-v1.5",
        version: 1,
        updatedByUserId: "usr_admin",
        updatedAt: "2026-08-09T00:00:00Z"
      }),
      listQaMemoryWhitelist: vi.fn().mockResolvedValue({ items: [], page: 1, size: 100, total: 0 })
    });
    const view = renderWithApi(SystemManagementPanel, backendApi, currentUser, { activeKey: "memory" });

    expect(await view.findByTestId("memory-admin-panel")).toBeTruthy();
    expect((await view.findByTestId("memory-health-mem0")).textContent).toContain("就绪");
    view.queryClient.clear();
  });

  it("exposes one user-management page for account, permission and rollout controls", async () => {
    const backendApi = api();
    const view = renderWithApi(SystemManagementPanel, backendApi);

    await fireEvent.click(view.getByText("用户管理", { selector: ".ta-system-menu-text" }));
    // 系统二级页由工作台 Tab 容器持有选中态；菜单点击只发出受控切换事件。
    await view.rerender({
      currentUser,
      activeKey: "users",
      pageActive: true,
      supportRevealed: false,
      supportActivationSequence: 0
    });

    expect(await view.findByTestId("unified-user-management-panel")).toBeTruthy();
    expect(view.queryByText("本地客户端灰度", { selector: ".ta-system-menu-text" })).toBeNull();
    view.queryClient.clear();
  });

  it("reveals the support page only from controlled shortcut state without changing identity", async () => {
    const backendApi = api();
    const view = renderWithApi(SystemManagementPanel, backendApi);

    expect(view.queryByText("问题排查只读访问", { selector: ".ta-system-menu-text" })).toBeNull();
    await view.rerender({
      currentUser,
      activeKey: "support",
      pageActive: true,
      supportRevealed: true,
      supportActivationSequence: 1
    });

    expect(await view.findByText("问题排查只读访问", { selector: ".ta-system-menu-text" })).toBeTruthy();
    expect(view.getByTestId("support-access-panel")).toBeTruthy();
    expect(view.getByTestId("support-access-panel").getAttribute("data-activation-sequence")).toBe("1");
    expect(currentUser.userId).toBe("usr_admin");
    expect(currentUser.roles).toEqual(["SUPER_ADMIN"]);

    await view.rerender({
      currentUser,
      activeKey: "support",
      pageActive: true,
      supportRevealed: true,
      supportActivationSequence: 2
    });
    await waitFor(() => expect(view.getByTestId("support-access-panel").getAttribute("data-activation-sequence")).toBe("2"));
    view.queryClient.clear();
  });

  it("system management exposes config management and initializes public opencode repository", async () => {
    const backendApi = api();
    const view = renderWithApi(SystemManagementPanel, backendApi, currentUser, { activeKey: "config" });

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

  it("places application runtime updates in a dedicated tab between public Agent and application Git", async () => {
    const backendApi = api();
    const view = renderWithApi(SystemManagementPanel, backendApi, currentUser, { activeKey: "config" });

    const navigation = await view.findByRole("navigation", { name: "配置管理导航" });
    expect(Array.from(navigation.querySelectorAll("button")).map((button) => button.textContent?.trim())).toEqual([
      "TestAgent公共配置管理",
      "应用运行态更新",
      "应用 Git 刷新"
    ]);
    expect(view.queryByText("应用 Agent / Tool 运行态更新")).toBeNull();
    expect(backendApi.getApplicationAgentConfigRollouts).not.toHaveBeenCalled();

    await fireEvent.click(view.getByRole("button", { name: "应用运行态更新" }));

    expect(await view.findByText("应用 Agent / Tool 运行态更新")).toBeTruthy();
    expect(view.queryByRole("button", { name: "刷新公共 Agent Git" })).toBeNull();
    await waitFor(() => expect(backendApi.getApplicationAgentConfigRollouts).toHaveBeenCalledTimes(1));
    expect(backendApi.listPublicAgentRepositories).toHaveBeenCalledTimes(1);
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

  it("lets application administrators refresh only the member applications returned by the backend", async () => {
    const backendApi = api();
    const appAdmin: CurrentUser = { ...currentUser, roles: ["APP_ADMIN"] };
    const view = renderWithApi(ApplicationGitRefreshManagementPanel, backendApi, appAdmin);

    expect(await view.findByText("F-GCMS")).toBeTruthy();
    expect(backendApi.listApplicationGitRefreshScopes).toHaveBeenCalledTimes(1);
    expect(view.queryByText("当前账号无配置管理权限")).toBeNull();
    view.queryClient.clear();
  });

  it("limits the application administrator console to application Git refresh", async () => {
    const backendApi = api();
    const appAdmin: CurrentUser = { ...currentUser, roles: ["APP_ADMIN"] };
    const view = renderWithApi(SystemManagementPanel, backendApi, appAdmin);

    expect(await view.findByText("配置管理", { selector: ".ta-system-menu-text" })).toBeTruthy();
    expect(await view.findByRole("heading", { name: "应用 Git 刷新" })).toBeTruthy();
    expect(view.queryByText("定时任务管理", { selector: ".ta-system-menu-text" })).toBeNull();
    expect(view.queryByText("运行管理", { selector: ".ta-system-menu-text" })).toBeNull();
    expect(view.queryByText("本地客户端灰度", { selector: ".ta-system-menu-text" })).toBeNull();
    expect(view.queryByText("用户管理", { selector: ".ta-system-menu-text" })).toBeNull();
    expect(view.queryByText("TestAgent公共配置管理")).toBeNull();
    expect(backendApi.listApplicationGitRefreshScopes).toHaveBeenCalledTimes(1);
    view.queryClient.clear();
  });

  it("gives system administrators configuration only after team review moves to the workbench", async () => {
    const backendApi = api();
    const systemAdmin: CurrentUser = { ...currentUser, roles: ["SYSTEM_ADMIN"] };
    const view = renderWithApi(SystemManagementPanel, backendApi, systemAdmin, { activeKey: "config" });

    expect(view.queryByText("团队管理", { selector: ".ta-system-menu-text" })).toBeNull();
    expect(view.getByText("配置管理", { selector: ".ta-system-menu-text" })).toBeTruthy();
    expect(view.queryByText("运行管理", { selector: ".ta-system-menu-text" })).toBeNull();
    expect(view.queryByText("用户管理", { selector: ".ta-system-menu-text" })).toBeNull();
    expect(await view.findByRole("heading", { name: "应用 Git 刷新" })).toBeTruthy();
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
    const view = renderWithApi(SystemManagementPanel, backendApi, currentUser, { activeKey: "config" });

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
    const view = renderWithApi(SystemManagementPanel, backendApi, currentUser, { activeKey: "config" });

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

  it("resumes the same paused publish without a personal push and confirms shared discard separately", async () => {
    const backendApi = api({
      resumePublicAgentConfigSync: vi.fn().mockResolvedValue(undefined),
      getPublicAgentConfigRollout: vi.fn().mockResolvedValue({
        rolloutId: "acr_paused", status: "DRAINING", branch: "main", commitHash: "commit_target",
        failureReason: null, createdAt: "2026-09-28T00:00:00Z", updatedAt: "2026-09-28T00:00:01Z", completedAt: null,
        servers: [{ linuxServerId: "linux-2", syncStatus: "AWAITING_ACTION", retryCount: 0,
          targetTotal: 3, targetPending: 3, targetDisposed: 0, targetAbandoned: 0,
          worktreeTotal: 0, worktreePending: 0, worktreeSynced: 0, lastError: "SKILL.md 有本地修改",
          syncedAt: null, updatedAt: "2026-09-28T00:00:01Z" }]
      })
    });
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
    const view = renderWithApi(OpencodePublicConfigManagementPanel, backendApi);
    expect(await view.findByText("等待处理共享副本变更")).toBeTruthy();
    expect(await view.findByText(/个人草稿无需提交或推送/)).toBeTruthy();
    await fireEvent.click(view.getByRole("button", { name: "重新检查并继续同步" }));
    await waitFor(() => expect(backendApi.resumePublicAgentConfigSync).toHaveBeenCalledWith({
      rolloutId: "acr_paused", discardLocalChanges: false
    }));
    expect(confirm).not.toHaveBeenCalled();
    await waitFor(() => expect(view.getByRole("button", { name: "放弃共享副本变更并继续" }).hasAttribute("disabled")).toBe(false));
    await fireEvent.click(view.getByRole("button", { name: "放弃共享副本变更并继续" }));
    expect(backendApi.resumePublicAgentConfigSync).toHaveBeenCalledTimes(1);
    confirm.mockReturnValue(true);
    await fireEvent.click(view.getByRole("button", { name: "放弃共享副本变更并继续" }));
    await waitFor(() => expect(backendApi.resumePublicAgentConfigSync).toHaveBeenCalledWith({
      rolloutId: "acr_paused", discardLocalChanges: true
    }));
    expect(confirm).toHaveBeenLastCalledWith(expect.stringContaining("所有尚未同步成功的共享运行副本"));
    expect(backendApi.supersedePublicAgentConfigRollout).not.toHaveBeenCalled();
    expect(backendApi.stopOpencodeRuntimeManagedProcess).not.toHaveBeenCalled();
    view.queryClient.clear();
  });

  it("pauses public configuration rollout polling while its page tab is inactive and refreshes on return", async () => {
    vi.useFakeTimers();
    const activeRollout = {
      rolloutId: "acr_poll",
      status: "DRAINING" as const,
      branch: "main",
      commitHash: "commit_target",
      failureReason: null,
      createdAt: "2026-07-28T00:00:00Z",
      updatedAt: "2026-07-28T00:00:01Z",
      completedAt: null,
      servers: []
    };
    const backendApi = api({
      getPublicAgentConfigRollout: vi.fn().mockResolvedValue(activeRollout)
    });
    const view = renderWithApi(OpencodePublicConfigManagementPanel, backendApi, currentUser, { pageActive: true });
    await vi.waitFor(() => expect(backendApi.getPublicAgentConfigRollout).toHaveBeenCalledTimes(1));

    await vi.advanceTimersByTimeAsync(2_000);
    await vi.waitFor(() => expect(backendApi.getPublicAgentConfigRollout).toHaveBeenCalledTimes(2));

    await view.rerender({ currentUser, pageActive: false });
    await vi.advanceTimersByTimeAsync(6_000);
    expect(backendApi.getPublicAgentConfigRollout).toHaveBeenCalledTimes(2);

    await view.rerender({ currentUser, pageActive: true });
    await vi.waitFor(() => expect(backendApi.getPublicAgentConfigRollout).toHaveBeenCalledTimes(3));
    view.queryClient.clear();
  });

  it("keeps blocking rollout users collapsed until requested and reuses the existing managed-process stop API", async () => {
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

    const targetDisclosure = (await view.findByText("未排空用户")).closest("details") as HTMLDetailsElement;
    expect(targetDisclosure.open).toBe(false);
    await fireEvent.click(targetDisclosure.querySelector("summary")!);

    expect(targetDisclosure.open).toBe(true);
    expect(await view.findByText("张三")).toBeTruthy();
    expect(view.getAllByText("SESSION_RUNNING").length).toBeGreaterThan(0);
    await fireEvent.click(view.getByRole("button", { name: "关闭 张三 的 OpenCode" }));

    await waitFor(() => expect(backendApi.stopOpencodeRuntimeManagedProcess)
      .toHaveBeenCalledWith("container-1", 4096));
    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining("可能中断正在执行的任务"));
    expect(await view.findByText("已关闭 张三 的 OpenCode，正在等待排空任务确认")).toBeTruthy();
    view.queryClient.clear();
  });

  it("opens application history by branch and keeps its user details collapsed by default", async () => {
    const matchingApplicationScope: ApplicationGitRefreshScope = {
      ...applicationScope,
      appName: "F-BASE",
      groups: [{
        ...applicationScope.groups[0]!,
        version: "20260820",
        branch: "feature_testagent_20260820",
        workspaces: [{
          ...applicationScope.groups[0]!.workspaces[0]!,
          versionId: "awv_20260820",
          workspaceName: "接口测试"
        }]
      }]
    };
    const applicationRollout = {
      rolloutId: "acr_application_tool",
      configScope: "APPLICATION",
      scopeKey: "awv_20260820",
      status: "DRAINING",
      branch: "feature_testagent_20260820",
      commitHash: "commit_tool",
      failureReason: null,
      createdAt: "2026-08-20T00:00:00Z",
      updatedAt: "2026-08-20T00:00:01Z",
      completedAt: null,
      servers: [{
        linuxServerId: "linux-1",
        syncStatus: "SYNCED",
        retryCount: 0,
        targetTotal: 1,
        targetPending: 1,
        targetDisposed: 0,
        targetAbandoned: 0,
        worktreeTotal: 0,
        worktreePending: 0,
        worktreeSynced: 0,
        lastError: "TOOL_CATALOG_INVALID",
        syncedAt: "2026-08-20T00:00:01Z",
        updatedAt: "2026-08-20T00:00:01Z",
        pendingTargets: [{
          targetId: "act_application_tool",
          userId: "usr_lisi",
          username: "李四",
          linuxServerId: "linux-1",
          containerId: "container-1",
          port: 4096,
          processPid: 2345,
          processStartedAt: "2026-08-19T23:50:00Z",
          status: "RETRY_WAIT",
          retryCount: 2,
          nextRetryAt: "2026-08-20T00:00:05Z",
          lastError: "TOOL_CATALOG_INVALID",
          forceStop: false,
          updatedAt: "2026-08-20T00:00:01Z"
        }]
      }]
    };
    const backendApi = api({
      getApplicationAgentConfigRollouts: vi.fn().mockResolvedValue([applicationRollout]),
      listApplicationGitRefreshScopes: vi.fn().mockResolvedValue([matchingApplicationScope])
    });
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const view = renderWithApi(SystemManagementPanel, backendApi, currentUser, { activeKey: "config" });

    await fireEvent.click(view.getByRole("button", { name: "应用运行态更新" }));

    expect(await view.findByText("应用 Agent / Tool 运行态更新")).toBeTruthy();
    expect(await view.findByText("F-BASE")).toBeTruthy();
    expect(await view.findByText("接口测试")).toBeTruthy();
    expect(await view.findByText("版本 20260820")).toBeTruthy();
    expect(view.queryByText("awv_20260820")).toBeNull();
    const historyDisclosure = (await view.findByText("F-BASE")).closest("details") as HTMLDetailsElement;
    expect(historyDisclosure.open).toBe(false);
    expect(await view.findByText("待处理用户 1")).toBeTruthy();

    await fireEvent.click(historyDisclosure.querySelector("summary")!);
    expect(historyDisclosure.open).toBe(true);

    const targetDisclosure = (await view.findByText("尚未重启 / dispose 的用户")).closest("details") as HTMLDetailsElement;
    expect(targetDisclosure.open).toBe(false);
    await fireEvent.click(targetDisclosure.querySelector("summary")!);
    expect(targetDisclosure.open).toBe(true);
    expect(await view.findByText("李四")).toBeTruthy();
    await fireEvent.click(view.getByRole("button", { name: "重启 李四 的 OpenCode" }));

    await waitFor(() => expect(backendApi.restartOpencodeRuntimeManagedProcess)
      .toHaveBeenCalledWith("container-1", 4096));
    expect(await view.findByText(/后台将继续核验 Tool 目录/)).toBeTruthy();
    view.queryClient.clear();
  });

  it("keeps the application update section discoverable when no rollout is pending", async () => {
    const view = renderWithApi(SystemManagementPanel, api(), currentUser, { activeKey: "config" });

    await fireEvent.click(view.getByRole("button", { name: "应用运行态更新" }));

    expect(await view.findByText("应用 Agent / Tool 运行态更新")).toBeTruthy();
    expect(await view.findByText(/会在这里逐人显示“立即受管重启”/)).toBeTruthy();
    expect(view.queryByRole("button", { name: /重启 .* 的 OpenCode/ })).toBeNull();
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
