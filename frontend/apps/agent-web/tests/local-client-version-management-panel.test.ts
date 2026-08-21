import { QueryClient, VueQueryPlugin } from "@tanstack/vue-query";
import { fireEvent, render, waitFor } from "@testing-library/vue";
import { ElMessageBox } from "element-plus";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { BackendApiClient } from "@test-agent/backend-api";
import type {
  CurrentUser,
  LocalClientRelease,
  LocalClientRollout,
  LocalClientUpdateAttempt,
  LocalClientUserPolicy
} from "@test-agent/shared-types";
import LocalClientVersionManagementPanel from "../src/components/system/LocalClientVersionManagementPanel.vue";

const admin: CurrentUser = {
  userId: "usr_admin",
  username: "admin",
  unifiedAuthId: "AUTH_ADMIN",
  roles: ["SUPER_ADMIN"]
};

const release: LocalClientRelease = {
  version: "20260820183000",
  platform: "linux",
  architecture: "arm64",
  launcherVersionMin: 1,
  launcherVersionMax: 1,
  protocolVersion: "local-opencode-client.v1",
  manifestSha256: "a".repeat(64),
  compatible: true,
  publishedAt: "2026-08-20T10:30:00Z",
  syncedAt: "2026-08-20T10:31:00Z",
  artifacts: [
    { kind: "CLIENT_JAR", url: "releases/20260820183000/client.jar", size: 1024, sha256: "b".repeat(64) },
    { kind: "JDK", url: "releases/20260820183000/jdk.tar.gz", size: 2048, sha256: "c".repeat(64) },
    { kind: "OPENCODE", url: "releases/20260820183000/opencode.tar.gz", size: 4096, sha256: "d".repeat(64) }
  ]
};

const userPolicy: LocalClientUserPolicy = {
  userId: "usr_gray",
  targetVersion: "20260819183000",
  revision: 7,
  updatedBy: "usr_admin",
  updatedAt: "2026-08-20T10:35:00Z"
};

const rollout: LocalClientRollout = {
  rolloutId: "lcr_rollout",
  scope: "ALL_ONLINE",
  requestedUserId: null,
  status: "RUNNING",
  createdBy: "usr_admin",
  createdAt: "2026-08-20T10:40:00Z",
  completedAt: null,
  attemptCount: 1
};

const attempt: LocalClientUpdateAttempt = {
  commandId: "lcc_command",
  rolloutId: "lcr_rollout",
  clientInstanceId: "lci_device",
  userId: "usr_gray",
  connectionGeneration: 11,
  policyRevision: 7,
  currentVersion: "20260820183000",
  targetVersion: "20260819183000",
  direction: "ROLLBACK",
  status: "PREPARING",
  releaseDigest: "a".repeat(64),
  errorCode: null,
  createdAt: "2026-08-20T10:40:00Z",
  updatedAt: "2026-08-20T10:41:00Z",
  completedAt: null
};

function api(overrides: Partial<BackendApiClient> = {}): BackendApiClient {
  return {
    syncLocalClientReleases: vi.fn().mockResolvedValue({ synced: 1, unchanged: 0, discovered: 1 }),
    listLocalClientReleases: vi.fn().mockResolvedValue([release]),
    getLocalClientGlobalPolicy: vi.fn().mockResolvedValue({
      targetVersion: release.version,
      revision: 6,
      updatedBy: "usr_admin",
      updatedAt: "2026-08-20T10:32:00Z"
    }),
    setLocalClientGlobalPolicy: vi.fn().mockResolvedValue({
      targetVersion: release.version,
      revision: 7,
      updatedBy: "usr_admin",
      updatedAt: "2026-08-20T10:42:00Z"
    }),
    listLocalClientUserPolicies: vi.fn().mockResolvedValue([userPolicy]),
    setLocalClientUserPolicy: vi.fn().mockResolvedValue(userPolicy),
    clearLocalClientUserPolicy: vi.fn().mockResolvedValue({ ...userPolicy, targetVersion: null }),
    createLocalClientRollout: vi.fn().mockResolvedValue(rollout),
    listLocalClientRollouts: vi.fn().mockResolvedValue([rollout]),
    listLocalClientRolloutAttempts: vi.fn().mockResolvedValue([attempt]),
    ...overrides
  } as Partial<BackendApiClient> as BackendApiClient;
}

function renderPanel(client: BackendApiClient, currentUser: CurrentUser = admin, pageActive = true) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } }
  });
  const view = render(LocalClientVersionManagementPanel, {
    props: { currentUser, pageActive },
    global: {
      plugins: [[VueQueryPlugin, { queryClient }]],
      provide: { api: client }
    }
  });
  return { ...view, queryClient };
}

describe("LocalClientVersionManagementPanel", () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("keeps every version-management action unavailable to non-super-admin users", () => {
    const client = api();
    const view = renderPanel(client, { ...admin, roles: ["APP_ADMIN"] });

    expect(view.getByText("当前账号无本地客户端版本管理权限")).toBeTruthy();
    expect(client.listLocalClientReleases).not.toHaveBeenCalled();
    expect(view.queryByRole("button", { name: "同步版本" })).toBeNull();
    view.queryClient.clear();
  });

  it("loads only while active and renders signed releases, policies, rollouts and attempt direction", async () => {
    const client = api();
    const view = renderPanel(client, admin, false);

    await Promise.resolve();
    expect(client.listLocalClientReleases).not.toHaveBeenCalled();

    await view.rerender({ currentUser: admin, pageActive: true });
    expect(await view.findByText("签名发布版本")).toBeTruthy();
    await waitFor(() => expect(client.listLocalClientReleases).toHaveBeenCalledTimes(1));
    expect(view.getByText("3 个制品")).toBeTruthy();
    expect(view.getByText("usr_gray")).toBeTruthy();
    expect(view.getByText("lcr_rollout")).toBeTruthy();

    await fireEvent.click(view.getByRole("button", { name: "查看 rollout lcr_rollout" }));
    await waitFor(() => expect(client.listLocalClientRolloutAttempts).toHaveBeenCalledWith("lcr_rollout"));
    expect(await view.findByText("回退")).toBeTruthy();
    expect(view.getByText("20260820183000 → 20260819183000")).toBeTruthy();
    view.queryClient.clear();
  });

  it("syncs releases, changes global and user targets, and creates scoped rollouts", async () => {
    const client = api();
    vi.spyOn(ElMessageBox, "confirm").mockResolvedValue("confirm" as never);
    const view = renderPanel(client);
    await view.findByText("签名发布版本");

    await fireEvent.click(view.getByRole("button", { name: "同步版本" }));
    await waitFor(() => expect(client.syncLocalClientReleases).toHaveBeenCalledTimes(1));

    await fireEvent.update(view.getByLabelText("全局目标版本"), release.version);
    await fireEvent.click(view.getByRole("button", { name: "保存全局目标" }));
    await waitFor(() => expect(client.setLocalClientGlobalPolicy).toHaveBeenCalledWith(release.version));

    await fireEvent.update(view.getByLabelText("用户 ID"), "usr_gray_2");
    await fireEvent.update(view.getByLabelText("用户目标版本"), release.version);
    await fireEvent.click(view.getByRole("button", { name: "保存用户覆盖" }));
    await waitFor(() => expect(client.setLocalClientUserPolicy).toHaveBeenCalledWith("usr_gray_2", release.version));

    await fireEvent.click(view.getByRole("button", { name: "清除 usr_gray 覆盖" }));
    await waitFor(() => expect(client.clearLocalClientUserPolicy).toHaveBeenCalledWith("usr_gray"));

    await fireEvent.click(view.getByRole("button", { name: "更新全部在线客户端" }));
    await waitFor(() => expect(client.createLocalClientRollout).toHaveBeenCalledWith({ scope: "ALL_ONLINE" }));

    await fireEvent.update(view.getByLabelText("立即更新用户 ID"), "usr_gray");
    await fireEvent.click(view.getByRole("button", { name: "立即更新指定用户" }));
    await waitFor(() => expect(client.createLocalClientRollout).toHaveBeenCalledWith({
      scope: "USER",
      userId: "usr_gray"
    }));
    view.queryClient.clear();
  });
});
