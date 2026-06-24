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

  it("shows runtime management menu only for super admins", async () => {
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

    expect(superAdmin.getByText("运行管理")).toBeTruthy();
    await fireEvent.click(superAdmin.getByText("运行管理"));
    expect(superAdmin.emitted("select")?.[0]).toEqual(["runtimeManagement"]);
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
