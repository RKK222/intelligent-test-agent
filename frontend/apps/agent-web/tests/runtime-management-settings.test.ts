import { defineComponent, h, inject, provide } from "vue";
import { QueryClient, VueQueryPlugin } from "@tanstack/vue-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, waitFor } from "@testing-library/vue";
import type { BackendApiClient } from "@test-agent/backend-api";
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
        ElRadioButton: ElRadioButtonStub
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

    expect(superAdmin.getByText("应用与工作空间管理")).toBeTruthy();
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

    expect(view.getByText("应用与工作空间管理")).toBeTruthy();
    expect(view.queryByText("应用与工作区")).toBeNull();
  });

  it("loads runtime management overview and renders empty state", async () => {
    const api = {
      getOpencodeRuntimeManagementOverview: vi.fn().mockResolvedValue(emptyOverview)
    };
    const { findByText, queryClient } = renderRuntimePanel(api);

    expect(await findByText("暂无 opencode 进程")).toBeTruthy();
    expect(api.getOpencodeRuntimeManagementOverview).toHaveBeenCalledWith(
      expect.objectContaining({ page: 1, size: 20 })
    );

    queryClient.clear();
  });

  it("queries and renders opencode processes by username", async () => {
    const overview: OpencodeRuntimeManagementOverview = {
      ...emptyOverview,
      summary: {
        ...emptyOverview.summary,
        opencodeProcesses: 1,
        runningOpencodeProcesses: 1
      },
      opencodeProcesses: {
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
            status: "RUNNING",
            sessionPath: "/data/opencode/session/4096",
            configPath: "/data/opencode/.config/opencode/",
            lastHealthCheckAt: "2026-06-24T08:00:00Z",
            healthMessage: "ok",
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
      }
    };
    const api = {
      getOpencodeRuntimeManagementOverview: vi.fn().mockResolvedValue(overview)
    };
    const { findByText, getByPlaceholderText, getByText, queryClient } = renderRuntimePanel(api);

    expect(await findByText("wr")).toBeTruthy();
    await fireEvent.update(getByPlaceholderText("用户名"), "wr");
    await fireEvent.click(getByText("查询"));

    await waitFor(() => expect(api.getOpencodeRuntimeManagementOverview).toHaveBeenLastCalledWith(
      expect.objectContaining({ username: "wr", page: 1, size: 20 })
    ));

    queryClient.clear();
  });

  it("shows container latest metrics and loads metric history after row click", async () => {
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
          status: "READY",
          lastHeartbeatAt: "2026-06-24T08:00:00Z",
          createdAt: "2026-06-24T08:00:00Z",
          updatedAt: "2026-06-24T08:00:00Z",
          traceId: "trace_1234567890abcdef",
          cpuUsagePercent: 12.5,
          memoryUsagePercent: 50,
          memoryUsedBytes: 512
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
    const { findByText, queryClient } = renderRuntimePanel(api);

    expect(await findByText("12.5%")).toBeTruthy();
    expect(await findByText("512 B")).toBeTruthy();
    await fireEvent.click(await findByText("ctr_01"));

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

  it("labels backend metric charts by server and current JVM scope", async () => {
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
          memoryUsedBytes: 1024,
          memoryUsagePercent: 50,
          diskUsedBytes: 4096,
          diskUsagePercent: 25,
          jvmMemoryUsedBytes: 300,
          jvmThreadsLive: 42
        }
      ]
    };
    const api = {
      getOpencodeRuntimeManagementOverview: vi.fn().mockResolvedValue(overview),
      getOpencodeRuntimeBackendProcessMetrics: vi.fn().mockResolvedValue({
        generatedAt: "2026-06-24T08:00:00Z",
        backendProcessId: "bjp_1234567890abcdef",
        from: "2026-06-24T07:00:00Z",
        to: "2026-06-24T08:00:00Z",
        samples: [
          { sampledAt: "2026-06-24T07:59:40Z", cpuUsagePercent: 22.5, diskUsagePercent: 25 },
          { sampledAt: "2026-06-24T07:59:45Z", jvmMemoryUsedBytes: 300, jvmThreadsLive: 42 }
        ]
      })
    };
    const { findByText, queryClient } = renderRuntimePanel(api);

    await fireEvent.click(await findByText("bjp_1234567890abcdef"));

    await waitFor(() => expect(api.getOpencodeRuntimeBackendProcessMetrics).toHaveBeenLastCalledWith(
      "bjp_1234567890abcdef",
      expect.objectContaining({ windowMinutes: 60, maxPoints: 720 })
    ));
    expect(await findByText("服务器 CPU / 内存 / 磁盘")).toBeTruthy();
    expect(await findByText("JVM 内存（当前进程）")).toBeTruthy();
    expect(await findByText("JVM GC / 线程（当前进程）")).toBeTruthy();

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
