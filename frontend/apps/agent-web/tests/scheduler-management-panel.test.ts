import { QueryClient, VueQueryPlugin } from "@tanstack/vue-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, waitFor } from "@testing-library/vue";
import type { Component } from "vue";
import type { BackendApiClient } from "@test-agent/backend-api";
import type {
  CurrentUser,
  OpencodeRuntimeManagementOverview,
  PublicAgentRepositoryStatus
} from "@test-agent/shared-types";
import SystemManagementPanel from "../src/components/system/SystemManagementPanel.vue";

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
    ...overrides
  } as Partial<BackendApiClient> as BackendApiClient;
}

function renderWithApi(component: Component, backendApi: BackendApiClient) {
  const client = queryClient();
  const view = render(component, {
    props: { currentUser },
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
    await fireEvent.click(view.getByRole("button", { name: "拉取" }));

    await waitFor(() =>
      expect(backendApi.pullPublicAgentRepository).toHaveBeenCalledWith("linux-1", "master", expect.stringMatching(/^aco_/), false)
    );
    expect(await view.findByText("服务器 linux-1 公共配置仓库已拉取到最新")).toBeTruthy();
    view.queryClient.clear();
  });
});
