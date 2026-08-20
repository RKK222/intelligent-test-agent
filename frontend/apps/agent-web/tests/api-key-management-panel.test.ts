import { QueryClient, VueQueryPlugin } from "@tanstack/vue-query";
import { fireEvent, render, waitFor } from "@testing-library/vue";
import { ElMessageBox } from "element-plus";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { BackendApiClient } from "@test-agent/backend-api";
import type { CurrentUser, ExternalApiCredential, PageResponse } from "@test-agent/shared-types";
import ApiKeyManagementPanel from "../src/components/system/ApiKeyManagementPanel.vue";

const admin: CurrentUser = {
  userId: "usr_admin",
  username: "admin",
  unifiedAuthId: "AUTH_1",
  roles: ["SUPER_ADMIN"]
};

const credential: ExternalApiCredential = {
  credentialId: "eac_one",
  toolCode: "deploy.bot",
  toolName: "部署工具",
  scopes: ["USER_SSH_KEY_READ"],
  keyHint: "taak_v1_ABC...WXYZ",
  enabled: true,
  createdAt: "2026-08-09T04:00:00Z",
  updatedAt: "2026-08-09T04:00:00Z"
};

const page: PageResponse<ExternalApiCredential> = {
  items: [credential],
  page: 1,
  size: 20,
  total: 1
};

function api(overrides: Partial<BackendApiClient> = {}): BackendApiClient {
  return {
    listExternalApiScopes: vi.fn().mockResolvedValue([
      { code: "USER_SSH_KEY_READ", name: "读取用户 SSH 私钥" }
    ]),
    listExternalApiCredentials: vi.fn().mockResolvedValue(page),
    createExternalApiCredential: vi.fn().mockResolvedValue({
      credential,
      apiKey: "taak_v1_created-secret"
    }),
    updateExternalApiCredential: vi.fn().mockResolvedValue(credential),
    revealExternalApiCredential: vi.fn().mockResolvedValue({
      credentialId: "eac_one",
      toolCode: "deploy.bot",
      apiKey: "taak_v1_revealed-secret"
    }),
    rotateExternalApiCredential: vi.fn().mockResolvedValue({
      credentialId: "eac_one",
      toolCode: "deploy.bot",
      apiKey: "taak_v1_rotated-secret"
    }),
    deleteExternalApiCredential: vi.fn().mockResolvedValue(null),
    ...overrides
  } as Partial<BackendApiClient> as BackendApiClient;
}

function renderPanel(client: BackendApiClient, currentUser: CurrentUser = admin, pageActive = true) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } }
  });
  const view = render(ApiKeyManagementPanel, {
    props: { currentUser, pageActive },
    global: {
      plugins: [[VueQueryPlugin, { queryClient }]],
      provide: { api: client }
    }
  });
  return { ...view, queryClient };
}

describe("ApiKeyManagementPanel", () => {
  afterEach(() => vi.restoreAllMocks());

  it("keeps the panel unavailable to non-super-admin users", () => {
    const view = renderPanel(api(), { ...admin, roles: ["APP_ADMIN"] });

    expect(view.getByText("当前账号无 API Key 管理权限")).toBeTruthy();
    expect(view.queryByRole("button", { name: "新增 API Key" })).toBeNull();
    view.queryClient.clear();
  });

  it("creates a credential, shows the one-time key, and clears every cached or visible copy", async () => {
    const client = api();
    const view = renderPanel(client);

    expect(await view.findByText("taak_v1_ABC...WXYZ")).toBeTruthy();
    await fireEvent.click(view.getByRole("button", { name: "新增 API Key" }));
    await fireEvent.update(view.getByLabelText("工具编码"), "report.bot");
    await fireEvent.update(view.getByLabelText("工具名称"), "报表工具");
    await fireEvent.click(view.getByRole("checkbox", { name: "读取用户 SSH 私钥" }));
    await fireEvent.click(view.getByRole("button", { name: "保存 API Key" }));

    await waitFor(() => expect(client.createExternalApiCredential).toHaveBeenCalledWith({
      toolCode: "report.bot",
      toolName: "报表工具",
      scopes: ["USER_SSH_KEY_READ"],
      enabled: true
    }));
    expect(await view.findByDisplayValue("taak_v1_created-secret")).toBeTruthy();
    await waitFor(() => expect(view.queryClient.isMutating()).toBe(0));
    expect(JSON.stringify(view.queryClient.getMutationCache().getAll()))
      .not.toContain("taak_v1_created-secret");

    await fireEvent.click(view.getByRole("button", { name: "关闭明文 API Key" }));
    expect(view.queryByDisplayValue("taak_v1_created-secret")).toBeNull();
    expect(JSON.stringify(view.queryClient.getQueryCache().getAll())).not.toContain("taak_v1_created-secret");
    view.queryClient.clear();
  });

  it("supports reveal, edit, enable, rotate and delete with confirmations", async () => {
    const client = api();
    vi.spyOn(ElMessageBox, "confirm").mockResolvedValue("confirm" as never);
    const view = renderPanel(client);
    await view.findByText("deploy.bot");

    await fireEvent.click(view.getByRole("button", { name: "查看 deploy.bot" }));
    expect(await view.findByDisplayValue("taak_v1_revealed-secret")).toBeTruthy();
    await waitFor(() => expect(view.queryClient.isMutating()).toBe(0));
    expect(JSON.stringify(view.queryClient.getMutationCache().getAll())).not.toContain("taak_v1_revealed-secret");
    expect(JSON.stringify(view.queryClient.getQueryCache().getAll())).not.toContain("taak_v1_revealed-secret");
    await fireEvent.click(view.getByRole("button", { name: "关闭明文 API Key" }));

    await fireEvent.click(view.getByRole("button", { name: "编辑 deploy.bot" }));
    await fireEvent.update(view.getByLabelText("工具名称"), "部署平台");
    await fireEvent.click(view.getByRole("button", { name: "保存 API Key" }));
    await waitFor(() => expect(client.updateExternalApiCredential).toHaveBeenCalledWith(
      "eac_one",
      expect.objectContaining({ toolName: "部署平台", scopes: ["USER_SSH_KEY_READ"] })
    ));

    await fireEvent.click(view.getByRole("checkbox", { name: "启用 deploy.bot" }));
    await waitFor(() => expect(client.updateExternalApiCredential).toHaveBeenCalledWith(
      "eac_one", expect.objectContaining({ enabled: false })
    ));

    await fireEvent.click(view.getByRole("button", { name: "轮换 deploy.bot" }));
    await waitFor(() => expect(client.rotateExternalApiCredential).toHaveBeenCalledWith("eac_one"));
    expect(await view.findByDisplayValue("taak_v1_rotated-secret")).toBeTruthy();
    await waitFor(() => expect(view.queryClient.isMutating()).toBe(0));
    expect(JSON.stringify(view.queryClient.getMutationCache().getAll())).not.toContain("taak_v1_rotated-secret");
    expect(JSON.stringify(view.queryClient.getQueryCache().getAll())).not.toContain("taak_v1_rotated-secret");
    await fireEvent.click(view.getByRole("button", { name: "关闭明文 API Key" }));

    await fireEvent.click(view.getByRole("button", { name: "删除 deploy.bot" }));
    await waitFor(() => expect(client.deleteExternalApiCredential).toHaveBeenCalledWith("eac_one"));
    view.queryClient.clear();
  });

  it("does not write a revealed key after the component is unmounted", async () => {
    let resolveReveal!: (value: { credentialId: string; toolCode: string; apiKey: string }) => void;
    let apiKeyRead = false;
    const revealPromise = new Promise<{ credentialId: string; toolCode: string; apiKey: string }>((resolve) => {
      resolveReveal = resolve;
    });
    const client = api({ revealExternalApiCredential: vi.fn().mockReturnValue(revealPromise) });
    const view = renderPanel(client);
    await view.findByText("deploy.bot");

    await fireEvent.click(view.getByRole("button", { name: "查看 deploy.bot" }));
    await waitFor(() => expect(client.revealExternalApiCredential).toHaveBeenCalledWith("eac_one"));
    view.unmount();
    resolveReveal({
      credentialId: "eac_one",
      toolCode: "deploy.bot",
      get apiKey() {
        apiKeyRead = true;
        return "taak_v1_late-secret";
      }
    });

    await revealPromise;
    await Promise.resolve();
    expect(apiKeyRead).toBe(false);
    expect(JSON.stringify(view.queryClient.getMutationCache().getAll())).not.toContain("taak_v1_late-secret");
    view.queryClient.clear();
  });

  it("clears a visible key immediately when its page tab becomes inactive", async () => {
    const view = renderPanel(api());
    await view.findByText("deploy.bot");

    await fireEvent.click(view.getByRole("button", { name: "查看 deploy.bot" }));
    expect(await view.findByDisplayValue("taak_v1_revealed-secret")).toBeTruthy();

    await view.rerender({ currentUser: admin, pageActive: false });
    expect(view.queryByDisplayValue("taak_v1_revealed-secret")).toBeNull();
    view.queryClient.clear();
  });

  it("ignores a revealed key response that arrives after its page tab becomes inactive", async () => {
    let resolveReveal!: (value: { credentialId: string; toolCode: string; apiKey: string }) => void;
    let apiKeyRead = false;
    const revealPromise = new Promise<{ credentialId: string; toolCode: string; apiKey: string }>((resolve) => {
      resolveReveal = resolve;
    });
    const client = api({ revealExternalApiCredential: vi.fn().mockReturnValue(revealPromise) });
    const view = renderPanel(client);
    await view.findByText("deploy.bot");

    await fireEvent.click(view.getByRole("button", { name: "查看 deploy.bot" }));
    await waitFor(() => expect(client.revealExternalApiCredential).toHaveBeenCalledWith("eac_one"));
    await view.rerender({ currentUser: admin, pageActive: false });
    resolveReveal({
      credentialId: "eac_one",
      toolCode: "deploy.bot",
      get apiKey() {
        apiKeyRead = true;
        return "taak_v1_inactive-secret";
      }
    });

    await revealPromise;
    await Promise.resolve();
    expect(apiKeyRead).toBe(false);
    expect(view.queryByDisplayValue("taak_v1_inactive-secret")).toBeNull();
    view.queryClient.clear();
  });
});
