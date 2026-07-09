import { defineComponent, h, inject, provide } from "vue";
import { QueryClient, VueQueryPlugin } from "@tanstack/vue-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, waitFor } from "@testing-library/vue";
import { BackendApiError, type BackendApiClient } from "@test-agent/backend-api";
import type { OpencodeRuntimeManagementOverview } from "@test-agent/shared-types";
import RuntimeManagementPanel from "../src/components/settings/RuntimeManagementPanel.vue";
import { formatMetricSampleTime } from "../src/components/settings/runtimeMetricFormatting";
import SettingsMenu from "../src/components/settings/SettingsMenu.vue";
import SettingsPanel from "../src/components/settings/SettingsPanel.vue";

const radioGroupKey = Symbol("radio-group");

const ElRadioGroupStub = defineComponent({
  props: ["modelValue"],
  emits: ["update:modelValue"],
  setup(_props, { emit, slots }) {
    provide(radioGroupKey, (value: any) => emit("update:modelValue", value));
    return () => h("div", slots.default?.());
  }
});

const ElRadioButtonStub = defineComponent({
  props: ["value"],
  setup(props, { slots }) {
    const selectRadio = inject<(value: any) => void>(radioGroupKey);
    return () => h("button", { type: "button", onClick: () => selectRadio?.(props.value) }, slots.default?.());
  }
});

function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false }
    }
  });
}

function renderRuntimePanel(api: Partial<BackendApiClient>) {
  const queryClient = createQueryClient();
  const view = render(RuntimeManagementPanel, {
    props: {
      currentUser: {
        userId: "usr_admin",
        username: "admin",
        unifiedAuthId: "AUTH_1",
        roles: ["SUPER_ADMIN"]
      }
    },
    global: {
      plugins: [[VueQueryPlugin, { queryClient }]],
      stubs: {
        ElInput: {
          props: ["modelValue", "placeholder"],
          emits: ["update:modelValue"],
          template: `<input :placeholder="placeholder" :value="modelValue" @input="$emit('update:modelValue', $event.target.value)" />`
        },
        ElButton: {
          emits: ["click"],
          template: `<button type="button" @click="$emit('click')"><slot /></button>`
        },
        ElSelect: {
          template: `<select><slot /></select>`
        },
        ElOption: {
          props: ["label", "value"],
          template: `<option :value="value">{{ label }}</option>`
        },
        ElRadioGroup: ElRadioGroupStub,
        ElRadioButton: ElRadioButtonStub,
        RuntimeMetricChart: {
          props: ["title"],
          template: `<div class="runtime-metric-chart-stub"><h6>{{ title }}</h6></div>`
        },
        RuntimeTopologyGraph: {
          template: `<div class="runtime-topology-graph-stub">网络拓扑图</div>`
        }
      },
      provide: {
        api: api as BackendApiClient
      }
    }
  });
  return { ...view, queryClient };
}

const emptyOverview: OpencodeRuntimeManagementOverview = {
  generatedAt: "2026-06-24T08:00:00Z",
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
  opencodeProcesses: {
    items: [],
    page: 1,
    size: 20,
    total: 0
  }
};

describe("runtime management settings", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("formats metric sample timestamps in local time", () => {
    expect(formatMetricSampleTime("2026-06-26T17:28:00Z")).toBe("06/27 01:28");
    expect(formatMetricSampleTime("not-a-time")).toBe("-");
  });

  it("keeps runtime management out of the settings menu", () => {
    const superAdmin = render(SettingsMenu, {
      props: {
        activeKey: "appWorkspace",
        currentUser: {
          userId: "usr_admin",
          username: "admin",
          unifiedAuthId: "AUTH_1",
          roles: ["SUPER_ADMIN"]
        }
      }
    });

    expect(superAdmin.getByText("应用管理")).toBeTruthy();
    expect(superAdmin.getByText("版本库管理")).toBeTruthy();
    expect(superAdmin.getByText("用户管理")).toBeTruthy();
    expect(superAdmin.queryByText("用户管理（测试）")).toBeNull();
    expect(superAdmin.queryByText("应用与工作区")).toBeNull();
    expect(superAdmin.queryByText("运行管理")).toBeNull();
    superAdmin.unmount();

    const appAdmin = render(SettingsMenu, {
      props: {
        activeKey: "appWorkspace",
        currentUser: {
          userId: "usr_app",
          username: "app",
          unifiedAuthId: "AUTH_2",
          roles: ["APP_ADMIN"]
        }
      }
    });
    expect(appAdmin.queryByText("应用管理")).toBeNull();
    expect(appAdmin.queryByText("版本库管理")).toBeNull();
    expect(appAdmin.getByText("个人设置")).toBeTruthy();
    expect(appAdmin.queryByText("运行管理")).toBeNull();
  });

  it("renders the application workspace management panel title", () => {
    const view = render(SettingsPanel, {
      props: {
        activeKey: "appWorkspace",
        currentUser: {
          userId: "usr_admin",
          username: "admin",
          unifiedAuthId: "AUTH_1",
          roles: ["SUPER_ADMIN"]
        }
      },
      global: {
        stubs: {
          SettingsAppWorkspacePanel: { template: "<div />" },
          SettingsPersonalPanel: { template: "<div />" }
        }
      }
    });

    expect(view.getByText("应用管理")).toBeTruthy();
    expect(view.queryByText("应用与工作区")).toBeNull();
  });

  it("falls back to personal settings when a non-super-admin receives a hidden settings key", () => {
    const view = render(SettingsPanel, {
      props: {
        activeKey: "appWorkspace",
        currentUser: {
          userId: "usr_app",
          username: "app",
          unifiedAuthId: "AUTH_2",
          roles: ["APP_ADMIN"]
        }
      },
      global: {
        stubs: {
          SettingsAppWorkspacePanel: { template: "<div>app workspace panel</div>" },
          SettingsPersonalPanel: { template: "<div>personal panel</div>" }
        }
      }
    });

    expect(view.getByText("个人设置")).toBeTruthy();
    expect(view.getByText("personal panel")).toBeTruthy();
    expect(view.queryByText("app workspace panel")).toBeNull();
  });

  it("loads runtime management overview and renders empty state", async () => {
    const api = {
      getOpencodeRuntimeManagementOverview: vi.fn().mockResolvedValue(emptyOverview),
      getOpencodeRuntimeManagementUserProcesses: vi.fn()
    };
    const { findByText, queryClient } = renderRuntimePanel(api);

    expect(await findByText("请输入用户关键字查询 TestAgent 进程")).toBeTruthy();
    expect(api.getOpencodeRuntimeManagementOverview).toHaveBeenCalledWith(
      expect.objectContaining({ page: 1, size: 20 })
    );
    expect(api.getOpencodeRuntimeManagementUserProcesses).not.toHaveBeenCalled();

    queryClient.clear();
  });

  it("merges Linux servers and backend Java processes by linuxServerId", async () => {
    const overview: OpencodeRuntimeManagementOverview = {
      ...emptyOverview,
      summary: {
        ...emptyOverview.summary,
        linuxServers: 1,
        readyLinuxServers: 1,
        backendProcesses: 1,
        readyBackendProcesses: 1
      },
      linuxServers: [
        {
          linuxServerId: "10.8.0.12",
          name: "server-a",
          status: "READY",
          capacitySummary: { containers: 1 },
          lastHeartbeatAt: "2026-06-24T08:00:00Z",
          createdAt: "2026-06-24T08:00:00Z",
          updatedAt: "2026-06-24T08:00:00Z",
          traceId: "trace_server"
        }
      ],
      backendProcesses: [
        {
          backendProcessId: "bjp_1234567890abcdef",
          linuxServerId: "10.8.0.12",
          listenUrl: "http://10.8.0.12:8080",
          status: "READY",
          startedAt: "2026-06-24T08:00:00Z",
          lastHeartbeatAt: "2026-06-24T08:00:00Z",
          cpuUsagePercent: 12.5,
          memoryUsagePercent: 50,
          memoryUsedBytes: 512,
          diskUsagePercent: 62.5,
          diskUsedBytes: 1024,
          jvmMemoryUsedBytes: 268435456,
          jvmThreadsLive: 42,
          createdAt: "2026-06-24T08:00:00Z",
          updatedAt: "2026-06-24T08:00:00Z",
          traceId: "trace_backend"
        }
      ]
    };
    const api = {
      getOpencodeRuntimeManagementOverview: vi.fn().mockResolvedValue(overview)
    };
    const { findAllByText, findByText, queryByText, queryClient } = renderRuntimePanel(api);

    expect(await findByText("服务器 / Java 进程")).toBeTruthy();
    expect(await findByText("bjp_1234567890abcdef")).toBeTruthy();
    expect((await findAllByText("10.8.0.12")).length).toBeGreaterThanOrEqual(2);
    expect(await findByText("整机 12.5% / -核")).toBeTruthy();
    expect(await findByText((_content, element) =>
      element?.tagName === "TD" && Boolean(element.textContent?.includes("线程 42/-"))
    )).toBeTruthy();
    expect(queryByText("Linux 服务器")).toBeNull();
    expect(queryByText("后端 Java 进程")).toBeNull();

    queryClient.clear();
  });

  it("renders advertised host as a separate address column when stable server id is not an IP", async () => {
    const overview: OpencodeRuntimeManagementOverview = {
      ...emptyOverview,
      summary: {
        ...emptyOverview.summary,
        linuxServers: 1,
        readyLinuxServers: 1,
        backendProcesses: 1,
        readyBackendProcesses: 1,
        containers: 1,
        readyContainers: 1,
        managers: 1,
        connectedManagers: 1
      },
      linuxServers: [
        {
          linuxServerId: "linux-prod-a",
          name: "linux-prod-a",
          status: "READY",
          capacitySummary: {},
          lastHeartbeatAt: "2026-06-24T08:00:00Z",
          createdAt: "2026-06-24T08:00:00Z",
          updatedAt: "2026-06-24T08:00:00Z",
          traceId: "trace_server"
        }
      ],
      backendProcesses: [
        {
          backendProcessId: "bjp_1234567890abcdef",
          linuxServerId: "linux-prod-a",
          listenUrl: "http://10.8.0.21:8080",
          status: "READY",
          startedAt: "2026-06-24T08:00:00Z",
          lastHeartbeatAt: "2026-06-24T08:00:00Z",
          createdAt: "2026-06-24T08:00:00Z",
          updatedAt: "2026-06-24T08:00:00Z",
          traceId: "trace_backend"
        }
      ],
      containers: [
        {
          containerId: "ctr_01",
          linuxServerId: "linux-prod-a",
          containerName: "opencode-a",
          portStart: 4096,
          portEnd: 4100,
          maxProcesses: 4,
          currentProcesses: 0,
          availableCapacity: 4,
          status: "READY",
          lastHeartbeatAt: "2026-06-24T08:00:00Z",
          createdAt: "2026-06-24T08:00:00Z",
          updatedAt: "2026-06-24T08:00:00Z",
          traceId: "trace_container"
        }
      ],
      managers: [
        {
          managerId: "mgr_1234567890abcdef",
          containerId: "ctr_01",
          linuxServerId: "linux-prod-a",
          protocolVersion: "opencode-manager.v1",
          connectionStatus: "CONNECTED",
          capabilities: {},
          lastHeartbeatAt: "2026-06-24T08:00:00Z",
          createdAt: "2026-06-24T08:00:00Z",
          updatedAt: "2026-06-24T08:00:00Z",
          traceId: "trace_manager",
          managedProcesses: []
        }
      ]
    };
    const api = {
      getOpencodeRuntimeManagementOverview: vi.fn().mockResolvedValue(overview)
    };
    const { findByText, getAllByText, queryClient } = renderRuntimePanel(api);

    expect(await findByText("服务器 / Java 进程")).toBeTruthy();
    expect(await findByText("容器 / 管理进程")).toBeTruthy();
    await findByText("服务器 / Java 进程");
    expect(getAllByText("linux-prod-a").length).toBeGreaterThanOrEqual(2);
    expect(getAllByText("IP地址").length).toBeGreaterThanOrEqual(2);
    expect(getAllByText("10.8.0.21").length).toBeGreaterThanOrEqual(2);

    queryClient.clear();
  });

  it("queries and renders opencode processes by user keyword", async () => {
    const userProcessPage = {
        items: [
          {
            processId: "ocp_1234567890abcdef",
            userId: "usr_1234567890abcdef",
            username: "wr",
            linuxServerId: "10.8.0.12",
            containerId: "ctr_01",
            port: 4096,
            pid: 12345,
            baseUrl: "http://10.8.0.12:4096",
            status: "STOPPED",
            managerStatus: "NOT_RUNNING",
            healthStatus: "NOT_RUNNING",
            restartable: true,
            sessionPath: "/data/opencode/session/4096",
            configPath: "/data/opencode/.config/opencode/",
            lastHealthCheckAt: "2026-06-24T08:00:00Z",
            healthMessage: "process pid is not alive",
            createdAt: "2026-06-24T08:00:00Z",
            updatedAt: "2026-06-24T08:00:00Z",
            traceId: "trace_1234567890abcdef",
            bindingAgentId: "opencode",
            bindingStatus: "ACTIVE",
            bindingUpdatedAt: "2026-06-24T08:00:00Z"
          }
        ],
        page: 1,
        size: 20,
        total: 1
    };
    const api = {
      getOpencodeRuntimeManagementOverview: vi.fn().mockResolvedValue(emptyOverview),
      getOpencodeRuntimeManagementUserProcesses: vi.fn().mockResolvedValue(userProcessPage),
      restartOpencodeRuntimeManagedProcess: vi.fn().mockResolvedValue({ command: "restart", status: "STARTED", port: 4096 })
    };
    const { findByText, getByPlaceholderText, getByText, queryClient } = renderRuntimePanel(api);

    expect(await findByText("请输入用户关键字查询 TestAgent 进程")).toBeTruthy();
    await fireEvent.update(getByPlaceholderText("用户名 / userId / 统一认证号"), "wr");
    await fireEvent.click(getByText("查询用户进程"));

    await waitFor(() => expect(api.getOpencodeRuntimeManagementUserProcesses).toHaveBeenCalledWith(
      expect.objectContaining({ keyword: "wr", page: 1, size: 20 })
    ));
    expect(await findByText("wr")).toBeTruthy();
    expect(await findByText("NOT_RUNNING / NOT_RUNNING")).toBeTruthy();
    expect(await findByText("process pid is not alive")).toBeTruthy();

    await fireEvent.click(getByText("重启"));
    await waitFor(() => expect(api.restartOpencodeRuntimeManagedProcess).toHaveBeenCalledWith("ctr_01", 4096));
    await waitFor(() => expect(api.getOpencodeRuntimeManagementUserProcesses).toHaveBeenCalledTimes(2));

    queryClient.clear();
  });

  it("refreshes user process list after restart failure", async () => {
    const stoppedPage = {
      items: [
        {
          processId: "ocp_1234567890abcdef",
          userId: "usr_1234567890abcdef",
          username: "wr",
          linuxServerId: "10.8.0.12",
          containerId: "ctr_01",
          port: 4096,
          pid: 12345,
          baseUrl: "http://10.8.0.12:4096",
          status: "STOPPED",
          managerStatus: "NOT_RUNNING",
          healthStatus: "NOT_RUNNING",
          restartable: true,
          sessionPath: "/data/opencode/session/4096",
          configPath: "/data/opencode/.config/opencode/",
          lastHealthCheckAt: "2026-06-24T08:00:00Z",
          healthMessage: "process pid is not alive",
          createdAt: "2026-06-24T08:00:00Z",
          updatedAt: "2026-06-24T08:00:00Z",
          traceId: "trace_1234567890abcdef",
          bindingAgentId: "opencode",
          bindingStatus: "ACTIVE",
          bindingUpdatedAt: "2026-06-24T08:00:00Z"
        }
      ],
      page: 1,
      size: 20,
      total: 1
    };
    const unhealthyPage = {
      ...stoppedPage,
      items: [
        {
          ...stoppedPage.items[0],
          status: "UNHEALTHY",
          managerStatus: "UNHEALTHY",
          healthStatus: "UNHEALTHY",
          healthMessage: "opencode health endpoints are not reachable",
          updatedAt: "2026-06-24T08:00:10Z"
        }
      ]
    };
    const api = {
      getOpencodeRuntimeManagementOverview: vi.fn().mockResolvedValue(emptyOverview),
      getOpencodeRuntimeManagementUserProcesses: vi.fn()
        .mockResolvedValueOnce(stoppedPage)
        .mockResolvedValueOnce(unhealthyPage),
      restartOpencodeRuntimeManagedProcess: vi.fn().mockRejectedValue(new BackendApiError(503, {
        success: false,
        code: "OPENCODE_UNAVAILABLE",
        message: "启动后 10 秒内未通过健康检查：opencode health endpoints are not reachable",
        traceId: "trace_1234567890abcdef"
      }))
    };
    const { findByText, getByPlaceholderText, getByText, queryClient } = renderRuntimePanel(api);

    expect(await findByText("请输入用户关键字查询 TestAgent 进程")).toBeTruthy();
    await fireEvent.update(getByPlaceholderText("用户名 / userId / 统一认证号"), "wr");
    await fireEvent.click(getByText("查询用户进程"));
    expect(await findByText("process pid is not alive")).toBeTruthy();

    await fireEvent.click(getByText("重启"));

    await waitFor(() => expect(api.restartOpencodeRuntimeManagedProcess).toHaveBeenCalledWith("ctr_01", 4096));
    await waitFor(() => expect(api.getOpencodeRuntimeManagementUserProcesses).toHaveBeenCalledTimes(2));
    expect(await findByText("UNHEALTHY / UNHEALTHY")).toBeTruthy();
    expect(await findByText("opencode health endpoints are not reachable")).toBeTruthy();
    expect(await findByText(/OPENCODE_UNAVAILABLE/)).toBeTruthy();

    queryClient.clear();
  });

  it("expands a merged container manager row and groups owned and ghost processes", async () => {
    const startCommand =
      "XDG_DATA_HOME=/data/opencode/session/4096 OPENCODE_CONFIG_DIR=/data/opencode/.config/opencode/ opencode serve --hostname 0.0.0.0 --port 4096 --print-logs";
    const overview: OpencodeRuntimeManagementOverview = {
      ...emptyOverview,
      summary: {
        ...emptyOverview.summary,
        containers: 1,
        readyContainers: 1,
        managers: 1,
        connectedManagers: 1
      },
      containers: [
        {
          containerId: "ctr_01",
          linuxServerId: "10.8.0.12",
          containerName: "opencode-a",
          portStart: 4096,
          portEnd: 4100,
          maxProcesses: 4,
          currentProcesses: 3,
          availableCapacity: 1,
          metricsSource: "cgroup",
          status: "READY",
          lastHeartbeatAt: "2026-06-24T08:00:00Z",
          createdAt: "2026-06-24T08:00:00Z",
          updatedAt: "2026-06-24T08:00:00Z",
          traceId: "trace_1234567890abcdef"
        }
      ],
      managers: [
        {
          managerId: "mgr_1234567890abcdef",
          containerId: "ctr_01",
          linuxServerId: "10.8.0.12",
          protocolVersion: "opencode-manager.v1",
          connectionStatus: "CONNECTED",
          capabilities: { commands: ["start", "health"] },
          lastHeartbeatAt: "2026-06-24T08:00:00Z",
          createdAt: "2026-06-24T08:00:00Z",
          updatedAt: "2026-06-24T08:00:00Z",
          traceId: "trace_1234567890abcdef",
          managedProcesses: [
            {
              port: 4096,
              pid: 12345,
              baseUrl: "http://10.8.0.12:4096",
              sessionPath: "/data/opencode/session/4096",
              configPath: "/data/opencode/.config/opencode/",
              startedAt: "2026-06-24T08:00:00Z",
              startCommand,
              traceId: "trace_process",
              ownership: "BOUND",
              processId: "ocp_1234567890abcdef",
              processStatus: "RUNNING",
              healthMessage: "ok",
              userId: "usr_1234567890abcdef",
              username: "wr",
              bindingAgentId: "opencode",
              bindingStatus: "ACTIVE",
              bindingUpdatedAt: "2026-06-24T08:00:00Z"
            },
            {
              port: 4097,
              pid: 22345,
              baseUrl: "http://10.8.0.12:4097",
              sessionPath: "/data/opencode/session/4097",
              configPath: "/data/opencode/.config/opencode/",
              startedAt: "2026-06-24T08:05:00Z",
              traceId: "trace_ghost",
              ownership: "UNBOUND",
              processId: "ocp_2234567890abcdef",
              processStatus: "UNHEALTHY",
              healthMessage: "process is not alive"
            }
          ]
        }
      ]
    };
    const api = {
      getOpencodeRuntimeManagementOverview: vi.fn().mockResolvedValue(overview),
      restartOpencodeRuntimeManagedProcess: vi.fn().mockResolvedValue({
        command: "restart",
        status: "STARTED",
        port: 4096,
        message: "opencode server started"
      }),
      stopOpencodeRuntimeManagedProcess: vi.fn().mockResolvedValue({
        command: "stop",
        status: "STOPPED",
        port: 4097,
        message: "opencode server stopped"
      })
    };
    const { findAllByRole, findByText, queryByText, queryClient } = renderRuntimePanel(api);

    expect(await findByText("mgr_1234567890abcdef")).toBeTruthy();
    expect(queryByText(startCommand)).toBeNull();
    await fireEvent.click(await findByText("ctr_01"));

    expect(await findByText("有主进程")).toBeTruthy();
    expect(await findByText("无主进程")).toBeTruthy();
    expect(await findByText("wr")).toBeTruthy();
    expect(await findByText("process is not alive")).toBeTruthy();
    expect(await findByText(/容量计数来自 manager state/)).toBeTruthy();
    expect(await findByText("启动命令")).toBeTruthy();
    expect(await findByText(startCommand)).toBeTruthy();
    expect(await findByText("http://10.8.0.12:4096")).toBeTruthy();
    const restartButtons = await findAllByRole("button", { name: "重启" });
    const stopButtons = await findAllByRole("button", { name: "停止" });

    await fireEvent.click(restartButtons[0]);
    await waitFor(() => expect(api.restartOpencodeRuntimeManagedProcess).toHaveBeenCalledWith("ctr_01", 4096));
    await waitFor(() => expect(api.getOpencodeRuntimeManagementOverview).toHaveBeenCalledTimes(2));

    await fireEvent.click(stopButtons[1]);
    await waitFor(() => expect(api.stopOpencodeRuntimeManagedProcess).toHaveBeenCalledWith("ctr_01", 4097));
    await waitFor(() => expect(queryByText("process is not alive")).toBeNull());
    expect(await findByText("暂无无主进程")).toBeTruthy();
    expect(api.getOpencodeRuntimeManagementOverview).toHaveBeenCalledTimes(2);

    queryClient.clear();
  });

  it("shows container latest metrics and loads metric history from the trend action", async () => {
    const overview: OpencodeRuntimeManagementOverview = {
      ...emptyOverview,
      summary: {
        ...emptyOverview.summary,
        containers: 1,
        readyContainers: 1,
        managers: 1,
        connectedManagers: 1
      },
      containers: [
        {
          containerId: "ctr_01",
          linuxServerId: "10.8.0.12",
          containerName: "opencode-a",
          portStart: 4096,
          portEnd: 4100,
          maxProcesses: 4,
          currentProcesses: 2,
          availableCapacity: 2,
          status: "READY",
          lastHeartbeatAt: "2026-06-24T08:00:00Z",
          createdAt: "2026-06-24T08:00:00Z",
          updatedAt: "2026-06-24T08:00:00Z",
          traceId: "trace_1234567890abcdef",
          cpuUsagePercent: 12.5,
          memoryUsagePercent: 50,
          memoryUsedBytes: 512
        }
      ],
      managers: [
        {
          managerId: "mgr_1234567890abcdef",
          containerId: "ctr_01",
          linuxServerId: "10.8.0.12",
          protocolVersion: "opencode-manager.v1",
          connectionStatus: "CONNECTED",
          capabilities: { commands: ["start", "health"] },
          lastHeartbeatAt: "2026-06-24T08:00:00Z",
          createdAt: "2026-06-24T08:00:00Z",
          updatedAt: "2026-06-24T08:00:00Z",
          traceId: "trace_1234567890abcdef",
          managedProcesses: []
        }
      ]
    };
    const api = {
      getOpencodeRuntimeManagementOverview: vi.fn().mockResolvedValue(overview),
      getOpencodeRuntimeContainerMetrics: vi.fn().mockResolvedValue({
        generatedAt: "2026-06-24T08:00:00Z",
        containerId: "ctr_01",
        from: "2026-06-22T08:00:00Z",
        to: "2026-06-24T08:00:00Z",
        samples: [{ sampledAt: "2026-06-26T17:28:00Z", cpuUsagePercent: 12.5, memoryUsagePercent: 50 }]
      })
    };
    const { findByRole, findByText, queryClient } = renderRuntimePanel(api);

    expect(await findByText("12.5%")).toBeTruthy();
    expect(await findByText("512 B")).toBeTruthy();
    await fireEvent.click(await findByRole("button", { name: "查看 ctr_01 容器监控趋势" }));

    await waitFor(() => expect(api.getOpencodeRuntimeContainerMetrics).toHaveBeenLastCalledWith(
      "ctr_01",
      expect.objectContaining({ windowMinutes: 60, maxPoints: 720 })
    ));
    expect(await findByText("容器监控趋势")).toBeTruthy();

    // Change time range to 30 minutes
    const thirtyMinutesBtn = await findByText("30分钟");
    await fireEvent.click(thirtyMinutesBtn);

    await waitFor(() => expect(api.getOpencodeRuntimeContainerMetrics).toHaveBeenLastCalledWith(
      "ctr_01",
      expect.objectContaining({ windowMinutes: 30, maxPoints: 720 })
    ));

    // Change time range to 6 hours
    const sixHoursBtn = await findByText("6小时");
    await fireEvent.click(sixHoursBtn);

    await waitFor(() => expect(api.getOpencodeRuntimeContainerMetrics).toHaveBeenLastCalledWith(
      "ctr_01",
      expect.objectContaining({ windowMinutes: 360, maxPoints: 720 })
    ));

    // Change time range to 48 hours
    const fortyEightHoursBtn = await findByText("48小时");
    await fireEvent.click(fortyEightHoursBtn);

    await waitFor(() => expect(api.getOpencodeRuntimeContainerMetrics).toHaveBeenLastCalledWith(
      "ctr_01",
      expect.objectContaining({ windowMinutes: 2880, maxPoints: 720 })
    ));

    queryClient.clear();
  });

  it("explains container metric source values on hover", async () => {
    const overview: OpencodeRuntimeManagementOverview = {
      ...emptyOverview,
      summary: {
        ...emptyOverview.summary,
        containers: 1,
        readyContainers: 1
      },
      containers: [
        {
          containerId: "ctr_01",
          linuxServerId: "10.8.0.12",
          containerName: "opencode-a",
          portStart: 4096,
          portEnd: 4100,
          maxProcesses: 4,
          currentProcesses: 2,
          availableCapacity: 2,
          metricsSource: "cgroup",
          status: "READY",
          lastHeartbeatAt: "2026-06-24T08:00:00Z",
          createdAt: "2026-06-24T08:00:00Z",
          updatedAt: "2026-06-24T08:00:00Z",
          traceId: "trace_1234567890abcdef"
        }
      ]
    };
    const api = {
      getOpencodeRuntimeManagementOverview: vi.fn().mockResolvedValue(overview)
    };
    const { findByTitle, queryClient } = renderRuntimePanel(api);

    const sourceCell = await findByTitle(/“cgroup”/);

    expect(sourceCell.textContent).toBe("cgroup");
    expect(sourceCell.getAttribute("title")).toContain("“process”: 降级为当前 Go manager 进程指标");
    expect(sourceCell.getAttribute("title")).toContain("“不可采集”: 当前环境无法安全采集");
    expect(sourceCell.getAttribute("title")).toContain("“-”: 旧数据或未上报");

    queryClient.clear();
  });

  it("loads backend metric charts by stable server id and labels Java service JVM scope", async () => {
    const overview: OpencodeRuntimeManagementOverview = {
      ...emptyOverview,
      summary: {
        ...emptyOverview.summary,
        backendProcesses: 1,
        readyBackendProcesses: 1
      },
      backendProcesses: [
        {
          backendProcessId: "bjp_1234567890abcdef",
          linuxServerId: "10.8.0.12",
          listenUrl: "http://10.8.0.12:8080",
          status: "READY",
          startedAt: "2026-06-24T08:00:00Z",
          lastHeartbeatAt: "2026-06-24T08:00:00Z",
          createdAt: "2026-06-24T08:00:00Z",
          updatedAt: "2026-06-24T08:00:00Z",
          traceId: "trace_1234567890abcdef",
          cpuUsagePercent: 22.5,
          cpuCoreCount: 8,
          loadAverage1m: 1.5,
          loadAverage5m: 1.2,
          loadAverage15m: 0.8,
          memoryTotalBytes: 2048,
          memoryAvailableBytes: 1536,
          memoryUsedBytes: 1024,
          memoryUsagePercent: 50,
          swapUsagePercent: 25,
          diskAvailableBytes: 3072,
          diskUsedBytes: 4096,
          diskUsagePercent: 25,
          jvmProcessCpuUsagePercent: 7.5,
          jvmProcessCpuCoreUsage: 0.6,
          jvmProcessResidentMemoryBytes: 700,
          jvmOpenFileDescriptorCount: 50,
          jvmMaxFileDescriptorCount: 1024,
          jvmMemoryUsedBytes: 300,
          jvmHeapUsedBytes: 200,
          jvmHeapMaxBytes: 500,
          jvmGcCollectionCountDelta: 3,
          jvmThreadsDaemon: 12,
          jvmThreadsPeak: 48,
          jvmThreadsLive: 42
        }
      ]
    };
    const api = {
      getOpencodeRuntimeManagementOverview: vi.fn().mockResolvedValue(overview),
      getOpencodeRuntimeBackendServerMetrics: vi.fn().mockResolvedValue({
        generatedAt: "2026-06-24T08:00:00Z",
        linuxServerId: "10.8.0.12",
        backendProcessId: "bjp_1234567890abcdef",
        from: "2026-06-24T07:00:00Z",
        to: "2026-06-24T08:00:00Z",
        samples: [
          {
            sampledAt: "2026-06-24T07:59:40Z",
            cpuUsagePercent: 22.5,
            loadAverage1m: 1.5,
            memoryUsagePercent: 50,
            swapUsagePercent: 25,
            diskUsagePercent: 25
          },
          {
            sampledAt: "2026-06-24T07:59:45Z",
            jvmProcessCpuUsagePercent: 7.5,
            jvmProcessCpuCoreUsage: 0.6,
            jvmProcessResidentMemoryBytes: 700,
            jvmHeapUsedBytes: 200,
            jvmNonHeapUsedBytes: 100,
            jvmDirectBufferUsedBytes: 16,
            jvmGcCollectionTimeDeltaMillis: 7,
            jvmGcCollectionCountDelta: 3,
            jvmOpenFileDescriptorCount: 50,
            jvmThreadsLive: 42
          }
        ]
      })
    };
    const { findAllByText, findByText, queryClient } = renderRuntimePanel(api);

    await fireEvent.click((await findAllByText("10.8.0.12"))[0]);

    await waitFor(() => expect(api.getOpencodeRuntimeBackendServerMetrics).toHaveBeenLastCalledWith(
      "10.8.0.12",
      expect.objectContaining({ windowMinutes: 60, maxPoints: 720 })
    ));
    expect(await findByText("Java 7.5% / 0.60核")).toBeTruthy();
    expect(await findByText("Heap 200 B / 500 B")).toBeTruthy();
    expect(await findByText("服务器 CPU / Load")).toBeTruthy();
    expect(await findByText("服务器内存 / Swap / 磁盘")).toBeTruthy();
    expect(await findByText("Java 进程 CPU")).toBeTruthy();
    expect(await findByText("Java 进程内存 / RSS")).toBeTruthy();
    expect(await findByText("JVM Heap / Non-Heap / Direct")).toBeTruthy();
    expect(await findByText("GC / 线程 / FD")).toBeTruthy();

    queryClient.clear();
  });

  it("shows runtime management errors and retries on refresh", async () => {
    const api = {
      getOpencodeRuntimeManagementOverview: vi.fn().mockRejectedValue(new Error("加载失败"))
    };
    const { findByRole, getByText, queryClient } = renderRuntimePanel(api);

    expect((await findByRole("alert")).textContent).toContain("加载失败");
    await fireEvent.click(getByText("刷新"));
    await waitFor(() => expect(api.getOpencodeRuntimeManagementOverview).toHaveBeenCalledTimes(2));

    queryClient.clear();
  });
});
