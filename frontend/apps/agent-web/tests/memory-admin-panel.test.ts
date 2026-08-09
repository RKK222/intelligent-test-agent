// @vitest-environment jsdom

import { cleanup, fireEvent, render, waitFor, within } from "@testing-library/vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { BackendApiClient } from "@test-agent/backend-api";
import MemoryAdminPanel from "../src/components/system/MemoryAdminPanel.vue";

function api() {
  return {
    getQaMemoryAdminHealth: vi.fn().mockResolvedValue({
      enabled: true,
      memoryService: { available: true, status: "UP", version: "0.1.0" },
      embedding: {
        provider: "LOCAL_BGE",
        model: "BAAI/bge-small-zh-v1.5",
        revision: "fixed-revision",
        dimension: 512,
        device: "cpu",
        normalized: true,
        collectionVersion: "v1"
      },
      primaryChatModelId: "enterprise/chat-model",
      currentRunModelFallbackEnabled: false,
      queuePending: 2,
      queueProcessing: 1,
      queueDead: 0
    }),
    getQaMemorySettings: vi.fn().mockResolvedValue({
      primaryChatModelId: "enterprise/chat-model",
      currentRunModelFallbackEnabled: false,
      version: 3,
      updatedByUserId: "usr_admin",
      updatedAt: "2026-08-09T00:00:00Z"
    }),
    listQaMemoryWhitelist: vi.fn().mockResolvedValue({
      items: [{
        userId: "usr_tester",
        enabled: true,
        updatedByUserId: "usr_admin",
        createdAt: "2026-08-09T00:00:00Z",
        updatedAt: "2026-08-09T00:00:00Z"
      }],
      page: 1,
      size: 100,
      total: 1
    }),
    getInternalModelProviders: vi.fn().mockResolvedValue({
      providers: [{
        providerId: "enterprise",
        name: "企业内部模型",
        baseUrl: "https://models.example.test",
        enabled: true,
        sortOrder: 1,
        tokenConfigured: true
      }],
      tokenConfigured: true
    }),
    getInternalModelProviderModels: vi.fn().mockResolvedValue([
      {
        providerId: "enterprise",
        modelId: "enterprise/chat-model",
        upstreamModelId: "chat-model",
        displayName: "企业聊天模型 V1",
        enabled: true,
        declaredCapabilities: ["CHAT"],
        probedCapabilities: ["CHAT"]
      },
      {
        providerId: "enterprise",
        modelId: "enterprise/chat-model-v2",
        upstreamModelId: "chat-model-v2",
        displayName: "企业聊天模型 V2",
        enabled: true,
        declaredCapabilities: ["CHAT"],
        probedCapabilities: ["CHAT"]
      }
    ]),
    listUsers: vi.fn().mockResolvedValue({
      items: [{
        userId: "usr_88",
        username: "测试用户",
        unifiedAuthId: "AUTH88",
        status: "ACTIVE",
        roles: ["USER"],
        createdAt: "2026-08-09T00:00:00Z"
      }],
      page: 1,
      size: 30,
      total: 1
    }),
    enableQaMemoryUser: vi.fn().mockResolvedValue({
      userId: "usr_88",
      enabled: true,
      updatedByUserId: "usr_admin",
      createdAt: "2026-08-09T00:00:00Z",
      updatedAt: "2026-08-09T00:00:00Z"
    }),
    updateQaMemorySettings: vi.fn().mockResolvedValue({
      primaryChatModelId: "enterprise/chat-model-v2",
      currentRunModelFallbackEnabled: true,
      version: 4,
      updatedByUserId: "usr_admin",
      updatedAt: "2026-08-09T01:00:00Z"
    })
  } as Partial<BackendApiClient> as BackendApiClient;
}

describe("MemoryAdminPanel", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("shows readiness, embedding profile, extraction policy, queue and whitelist", async () => {
    const backendApi = api();
    const view = render(MemoryAdminPanel, { global: { provide: { api: backendApi } } });

    expect((await view.findByTestId("memory-health-mem0")).textContent).toContain("就绪");
    expect(view.getByTestId("memory-health-embedding").textContent).toContain("512 维");
    expect(view.getByTestId("memory-health-chat").textContent).toContain("enterprise/chat-model");
    expect(view.getByTestId("memory-health-chat").textContent).toContain("备用模型：关闭");
    expect(view.getByTestId("memory-health-queue").textContent).toContain("2 待处理");
    expect(view.getByText("usr_tester")).toBeTruthy();
    expect(view.queryByText("fixed-revision")).toBeNull();

    await fireEvent.click(view.getByRole("button", { name: "查看技术信息" }));
    expect(view.getByText("fixed-revision")).toBeTruthy();
    expect(view.getByText("锁定同一套模型权重，避免检索结果无意变化。")).toBeTruthy();
  });

  it("updates the fixed model and fallback with optimistic version", async () => {
    const backendApi = api();
    const view = render(MemoryAdminPanel, { global: { provide: { api: backendApi } } });
    const modelSelect = await view.findByRole("combobox", { name: "选择固定内部 CHAT 模型" });

    await fireEvent.click(modelSelect);
    const modelOption = await waitFor(() => view.getByRole("option", { name: /企业聊天模型 V2.*enterprise\/chat-model-v2/ }));
    await fireEvent.click(modelOption);
    await fireEvent.click(view.getByText("固定模型不可用时，使用当前任务的内部模型"));
    await fireEvent.click(view.getByRole("button", { name: "保存策略" }));

    await waitFor(() => expect(backendApi.updateQaMemorySettings).toHaveBeenCalledWith({
      primaryChatModelId: "enterprise/chat-model-v2",
      currentRunModelFallbackEnabled: true,
      expectedVersion: 3
    }));
  });

  it("adds a whitelist entry by selecting a searchable platform user", async () => {
    const backendApi = api();
    const view = render(MemoryAdminPanel, { global: { provide: { api: backendApi } } });
    await view.findByTestId("memory-health-mem0");

    await fireEvent.click(view.getByTestId("add-memory-whitelist-user"));
    const dialog = await view.findByRole("dialog", { name: "添加白名单用户" });
    const userSelect = within(dialog).getByRole("combobox", { name: "选择白名单用户" });
    await fireEvent.update(userSelect, "88");
    await waitFor(() => expect(backendApi.listUsers).toHaveBeenCalledWith({ keyword: "88", page: 1, size: 30 }));
    const userOption = await waitFor(() => view.getByRole("option", { name: /测试用户.*AUTH88.*usr_88/ }));
    await fireEvent.click(userOption);
    await fireEvent.click(within(dialog).getByRole("button", { name: "确认添加" }));

    await waitFor(() => expect(backendApi.enableQaMemoryUser).toHaveBeenCalledWith("usr_88"));
  });
});
