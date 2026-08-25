import { fireEvent, render, waitFor } from "@testing-library/vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { BackendApiClient } from "@test-agent/backend-api";
import type { CurrentUser, LocalClientInstance } from "@test-agent/shared-types";
import { ElMessageBox } from "element-plus";
import SettingsPersonalPanel from "../src/components/settings/SettingsPersonalPanel.vue";

const user: CurrentUser = {
  userId: "usr_user",
  username: "user",
  unifiedAuthId: "AUTH_USER",
  roles: ["USER"]
};

const clientInstance: LocalClientInstance = {
  clientInstanceId: "lci_device",
  clientName: "麒麟工作站",
  platform: "linux",
  architecture: "arm64",
  clientVersion: "20260820183000",
  opencodeVersion: "1.18.4",
  online: true,
  connectionGeneration: 11,
  reportedAddresses: ["10.0.0.20"],
  observedRemoteAddress: "10.0.0.20",
  opencodePort: 14096,
  processStatus: "RUNNING",
  opencodeHealthy: true,
  processId: 1234,
  capabilities: { SELF_UPDATE_V1: true },
  selfUpdateSupported: true,
  targetClientVersion: "20260819183000",
  updateDirection: "ROLLBACK",
  lastUpdateStatus: "AUTO_ROLLED_BACK",
  lastUpdateAt: "2026-08-20T11:00:00Z"
};

function api(instance: LocalClientInstance = clientInstance): BackendApiClient {
  return {
    ...({} as BackendApiClient),
    listPersonalSshKeys: vi.fn().mockResolvedValue([]),
    getMyLocalClientCredential: vi.fn().mockResolvedValue({
      exists: true,
      maskedKey: "tack_v1_****WXYZ",
      version: 1,
      status: "ACTIVE"
    }),
    listMyLocalClientInstances: vi.fn().mockResolvedValue([instance]),
    listWorkspaces: vi.fn().mockResolvedValue({ items: [], page: 1, size: 100, total: 0 }),
    commandLocalClientOpencode: vi.fn(),
    requestLocalClientPublicCapabilityUpdate: vi.fn().mockResolvedValue({
      commandId: "lcpc_test",
      status: "PENDING",
      targetDigest: "b".repeat(64)
    }),
    listLocalClientDirectories: vi.fn().mockResolvedValue([])
  };
}

function renderPanel(client: BackendApiClient, pageActive: boolean, localClientVisible = true) {
  return render(SettingsPersonalPanel, {
    props: { currentUser: user, pageActive, localClientVisible },
    global: { provide: { api: client } }
  });
}

describe("SettingsPersonalPanel local-client version state", () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("loads and polls local-client state only while the settings page is active", async () => {
    vi.useFakeTimers();
    const client = api();
    const view = renderPanel(client, false);

    await Promise.resolve();
    expect(client.listMyLocalClientInstances).not.toHaveBeenCalled();

    await view.rerender({ currentUser: user, pageActive: true });
    await waitFor(() => expect(client.listMyLocalClientInstances).toHaveBeenCalledTimes(1));
    await vi.advanceTimersByTimeAsync(5_000);
    await waitFor(() => expect(client.listMyLocalClientInstances).toHaveBeenCalledTimes(2));

    await view.rerender({ currentUser: user, pageActive: false });
    await vi.advanceTimersByTimeAsync(15_000);
    expect(client.listMyLocalClientInstances).toHaveBeenCalledTimes(2);
  });

  it("hides client settings without disconnecting or mutating the client when rollout visibility is disabled", async () => {
    const client = api();
    const view = renderPanel(client, true, false);

    await waitFor(() => expect(client.listPersonalSshKeys).toHaveBeenCalledTimes(1));
    expect(view.queryByRole("region", { name: "本地 OpenCode 客户端" })).toBeNull();
    expect(client.getMyLocalClientCredential).not.toHaveBeenCalled();
    expect(client.listMyLocalClientInstances).not.toHaveBeenCalled();
    expect(client.commandLocalClientOpencode).not.toHaveBeenCalled();
  });

  it("keeps a manual latest-package download in personal settings even while a client is online", async () => {
    const view = renderPanel(api(), true);

    const download = await view.findByRole("link", { name: "下载最新客户端包" });
    expect(download.getAttribute("href")).toBe("/downloads/local-opencode-client/installer");
    expect(await view.findByText("麒麟工作站", { selector: ".ta-item-title" })).toBeTruthy();
  });

  it.each([
    ["UPDATE", "更新", "20260821183000"],
    ["ROLLBACK", "回退", "20260819183000"],
    ["SAME", "已一致", "20260820183000"]
  ] as const)("shows %s update direction, exact version state and the latest terminal result/time", async (direction, label, targetVersion) => {
    const view = renderPanel(api({ ...clientInstance, updateDirection: direction, targetClientVersion: targetVersion }), true);

    expect(await view.findByText("麒麟工作站", { selector: ".ta-item-title" })).toBeTruthy();
    expect(view.getByText("支持静默自更新")).toBeTruthy();
    expect(view.getByText(label)).toBeTruthy();
    expect(view.getByText(`20260820183000 → ${targetVersion}`)).toBeTruthy();
    expect(view.getByText("最近结果 AUTO_ROLLED_BACK · 08/20 19:00:00")).toBeTruthy();
  });

  it("fails closed when an old client omits self-update fields", async () => {
    const legacy: LocalClientInstance = {
      ...clientInstance,
      clientInstanceId: "lci_legacy",
      clientName: "旧版客户端",
      clientVersion: "0.1.0",
      capabilities: {},
      selfUpdateSupported: undefined,
      targetClientVersion: undefined,
      updateDirection: undefined,
      lastUpdateStatus: undefined,
      lastUpdateAt: undefined
    };
    const client = api(legacy);
    const view = renderPanel(client, true);

    expect(await view.findByText("旧版客户端", { selector: ".ta-item-title" })).toBeTruthy();
    expect(view.getByText("不支持自更新，请重新下载用户包")).toBeTruthy();
    expect(view.queryByText("用户更新")).toBeNull();
    await waitFor(() => expect(client.listMyLocalClientInstances).toHaveBeenCalledTimes(1));
    await fireEvent.click(view.getByRole("button", { name: "刷新" }));
    await waitFor(() => expect(client.listMyLocalClientInstances).toHaveBeenCalledTimes(2));
  });

  it("shows the pending public capability summary and updates only after user confirmation", async () => {
    vi.spyOn(ElMessageBox, "confirm").mockResolvedValue(
      { action: "confirm" } as Awaited<ReturnType<typeof ElMessageBox.confirm>>
    );
    const digest = "b".repeat(64);
    const client = api({
      ...clientInstance,
      publicCapabilities: {
        supported: true,
        activeCommit: "a".repeat(40),
        activeDigest: "a".repeat(64),
        pendingCommit: "b".repeat(40),
        pendingDigest: digest,
        status: "UPDATE_AVAILABLE",
        agentCount: 2,
        skillCount: 3,
        toolCount: 1,
        requiresRestart: true
      }
    });
    const view = renderPanel(client, true);

    expect(await view.findByText("公共 Agent / Skill / Tool")).toBeTruthy();
    expect(view.getByText(/待更新版本 bbbbbbbbbbbb · Agent 2 \/ Skill 3 \/ Tool 1/)).toBeTruthy();
    await fireEvent.click(view.getByRole("button", { name: "更新公共能力" }));

    await waitFor(() => expect(ElMessageBox.confirm).toHaveBeenCalledWith(
      expect.stringContaining("当前 macOS 登录账号的本机权限"),
      "更新本地公共能力",
      expect.any(Object)
    ));
    await waitFor(() => expect(client.requestLocalClientPublicCapabilityUpdate)
      .toHaveBeenCalledWith("lci_device", digest));
  });

  it("lets the web fallback select one directory without navigating on single click", async () => {
    const client = api();
    vi.mocked(client.listLocalClientDirectories).mockResolvedValue([{
      name: "web-project",
      absolutePath: "/Users/test/web-project",
      directory: true,
      symbolicLink: false,
      readable: true
    }]);
    const view = renderPanel(client, true);

    await view.findByText("麒麟工作站", { selector: ".ta-item-title" });
    await fireEvent.click(view.getByRole("button", { name: "网页浏览" }));
    const directory = await view.findByRole("button", { name: "web-project" });
    await fireEvent.click(directory);

    expect(client.listLocalClientDirectories).toHaveBeenCalledTimes(1);
    expect(view.getByText("已选择：/Users/test/web-project")).toBeTruthy();
    await fireEvent.click(view.getByRole("button", { name: "使用所选目录" }));
    expect((view.getByPlaceholderText("绝对路径，例如 /Users/me/project") as HTMLInputElement).value)
      .toBe("/Users/test/web-project");
    expect((view.getByPlaceholderText("工作区名称") as HTMLInputElement).value)
      .toBe("web-project");
  });
});
