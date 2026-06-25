import { QueryClient, VueQueryPlugin } from "@tanstack/vue-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, waitFor } from "@testing-library/vue";
import type { Component } from "vue";
import type { BackendApiClient } from "@test-agent/backend-api";
import type {
  CurrentUser,
  OpencodeRuntimeManagementOverview,
  PageResponse,
  ScheduledTaskManagementRun,
  ScheduledTaskManagementTask
} from "@test-agent/shared-types";
import ScheduledTaskManagementPanel from "../src/components/system/ScheduledTaskManagementPanel.vue";
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

const task: ScheduledTaskManagementTask = {
  taskKey: "daily.cleanup",
  name: "每日清理",
  cronExpression: "0 0 2 * * *",
  enabled: true,
  lockTtlSeconds: 300,
  nextFireAt: "2026-06-25T02:00:00Z",
  registrationStatus: "REGISTERED",
  registrationStatusLabel: "已注册",
  currentRun: null,
  latestRun: null,
  traceId: "trace_task",
  createdAt: "2026-06-25T00:00:00Z",
  updatedAt: "2026-06-25T00:00:00Z"
};

const runningRun: ScheduledTaskManagementRun = {
  taskRunId: "str_1234567890abcdef",
  taskKey: "daily.cleanup",
  planId: null,
  triggerType: "MANUAL",
  triggerTypeLabel: "手工触发",
  status: "RUNNING",
  statusLabel: "运行中",
  requestedByUserId: "usr_admin",
  scheduledFireAt: "2026-06-25T00:00:00Z",
  startedAt: "2026-06-25T00:00:01Z",
  endedAt: null,
  ownerInstanceId: "backend-a",
  stopRequestedAt: null,
  stopRequestedByUserId: null,
  stopReason: null,
  skipReason: null,
  errorCode: null,
  errorMessage: null,
  result: {},
  traceId: "trace_run",
  createdAt: "2026-06-25T00:00:00Z",
  updatedAt: "2026-06-25T00:00:01Z"
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

function pageOf<T>(items: T[]): PageResponse<T> {
  return { items, page: 1, size: 20, total: items.length };
}

function api(overrides: Partial<BackendApiClient> = {}) {
  return {
    listScheduledTasks: vi.fn().mockResolvedValue(pageOf([task])),
    updateScheduledTask: vi.fn().mockResolvedValue(task),
    triggerScheduledTask: vi.fn().mockResolvedValue(runningRun),
    listScheduledTaskRuns: vi.fn().mockResolvedValue(pageOf([runningRun])),
    getScheduledTaskRun: vi.fn().mockResolvedValue(runningRun),
    stopScheduledTaskRun: vi.fn().mockResolvedValue({ ...runningRun, status: "STOPPING", statusLabel: "停止中" }),
    getOpencodeRuntimeManagementOverview: vi.fn().mockResolvedValue(emptyRuntimeOverview),
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

  it("loads tasks and supports cron update plus manual trigger", async () => {
    const backendApi = api();
    const view = renderWithApi(ScheduledTaskManagementPanel, backendApi);

    expect(await view.findByText("每日清理")).toBeTruthy();
    await fireEvent.update(view.getByPlaceholderText("Cron 表达式"), "0 0 3 * * *");
    await fireEvent.click(view.getByText("保存 Cron"));
    await fireEvent.click(view.getByText("手工启动"));

    await waitFor(() => expect(backendApi.updateScheduledTask).toHaveBeenCalledWith("daily.cleanup", {
      cronExpression: "0 0 3 * * *"
    }));
    expect(backendApi.triggerScheduledTask).toHaveBeenCalledWith("daily.cleanup");
    view.queryClient.clear();
  });

  it("filters runs and stops a running task run", async () => {
    const backendApi = api();
    const view = renderWithApi(ScheduledTaskManagementPanel, backendApi);

    expect(await view.findByText("str_1234567890abcdef")).toBeTruthy();
    await fireEvent.click(view.getByText("停止"));

    await waitFor(() => expect(backendApi.stopScheduledTaskRun).toHaveBeenCalledWith("str_1234567890abcdef"));
    view.queryClient.clear();
  });

  it("system management switches between scheduler and runtime management", async () => {
    const backendApi = api();
    const view = renderWithApi(SystemManagementPanel, backendApi);

    expect(await view.findByText("定时任务管理")).toBeTruthy();
    expect(await view.findByText("每日清理")).toBeTruthy();
    await fireEvent.click(view.getByText("运行管理"));

    await waitFor(() => expect(backendApi.getOpencodeRuntimeManagementOverview).toHaveBeenCalled());
    expect(await view.findByText("暂无 opencode 进程")).toBeTruthy();
    view.queryClient.clear();
  });
});
