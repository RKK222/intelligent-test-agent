import { QueryClient, VueQueryPlugin } from "@tanstack/vue-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, waitFor } from "@testing-library/vue";
import type { BackendApiClient } from "@test-agent/backend-api";
import type { OpencodeRuntimeManagementOverview } from "@test-agent/shared-types";
import RuntimeManagementPanel from "../src/components/settings/RuntimeManagementPanel.vue";
import SettingsMenu from "../src/components/settings/SettingsMenu.vue";

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
