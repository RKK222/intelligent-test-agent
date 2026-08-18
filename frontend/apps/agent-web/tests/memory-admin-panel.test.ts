// @vitest-environment jsdom

import { cleanup, fireEvent, render, waitFor } from "@testing-library/vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { BackendApiClient } from "@test-agent/backend-api";
import MemoryAdminPanel from "../src/components/system/MemoryAdminPanel.vue";

function api() {
  return {
    getQaMemoryAdminHealth: vi.fn().mockResolvedValue({
      enabled: true,
      memoryService: {
        available: true,
        status: "UP",
        version: "2.0.17",
        profiles: [{
          profileKey: "enterprise:enterprise/embed-model:768:enterprise-v1",
          provider: "ENTERPRISE",
          model: "enterprise/embed-model",
          dimension: 768,
          fingerprint: "enterprise-v1",
          collection: "memory_enterprise_v1",
          primary: true,
          available: false
        }, {
          profileKey: "cpu:bge-small-zh-v1.5:512:fixed-revision",
          provider: "CPU",
          model: "memory-bge-small-zh-v1.5",
          dimension: 512,
          fingerprint: "fixed-revision",
          collection: "memory_cpu_v1",
          primary: false,
          available: true
        }],
        projectionBacklog: { pending: 3, processing: 1, dead: 0 }
      },
      primaryChatModelId: "enterprise/chat-model",
      primaryEmbeddingModelId: "enterprise/embed-model",
      cpuEmbeddingModelId: "memory-bge-small-zh-v1.5",
      queuePending: 2,
      queueProcessing: 1,
      queueDead: 0
    }),
    getQaMemorySettings: vi.fn().mockResolvedValue({
      primaryChatModelId: "enterprise/chat-model",
      primaryEmbeddingModelId: "enterprise/embed-model",
      cpuEmbeddingModelId: "memory-bge-small-zh-v1.5",
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
      },
      {
        providerId: "enterprise",
        modelId: "enterprise/embed-model",
        upstreamModelId: "embed-model",
        displayName: "企业向量模型",
        embeddingDimension: 768,
        enabled: true,
        declaredCapabilities: ["EMBEDDING"],
        probedCapabilities: ["EMBEDDING"]
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
      primaryEmbeddingModelId: "enterprise/embed-model",
      cpuEmbeddingModelId: "memory-bge-small-zh-v1.5",
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

  it("shows global readiness and model strategy without per-user rollout controls", async () => {
    const backendApi = api();
    const view = render(MemoryAdminPanel, { global: { provide: { api: backendApi } } });

    expect((await view.findByTestId("memory-health-mem0")).textContent).toContain("就绪");
    expect(view.getByTestId("memory-health-embedding").textContent).toContain("1 / 2 可用");
    expect(view.getByTestId("memory-health-embedding").textContent).toContain("memory-bge-small-zh-v1.5");
    expect(view.getByTestId("memory-health-chat").textContent).toContain("enterprise/chat-model");
    expect(view.getByTestId("memory-health-chat").textContent).toContain("infer=true");
    expect(view.getByTestId("memory-health-queue").textContent).toContain("2 待处理");
    expect(view.getByTestId("memory-health-projection").textContent).toContain("3 待投影");
    expect(view.queryByText("usr_tester")).toBeNull();
    expect(view.queryByText("灰度用户")).toBeNull();
    expect(backendApi.listQaMemoryWhitelist).not.toHaveBeenCalled();
    expect(view.queryByText("fixed-revision")).toBeNull();

    await fireEvent.click(view.getByRole("button", { name: "查看技术信息" }));
    expect(view.getByText(/memory_cpu_v1 · fixed-revision/)).toBeTruthy();
  });

  it("updates fixed chat and optional enterprise embedding with optimistic version", async () => {
    const backendApi = api();
    const view = render(MemoryAdminPanel, { global: { provide: { api: backendApi } } });
    const modelSelect = await view.findByRole("combobox", { name: "选择固定内部 CHAT 模型" });

    await fireEvent.click(modelSelect);
    const modelOption = await waitFor(() => view.getByRole("option", { name: /企业聊天模型 V2.*enterprise\/chat-model-v2/ }));
    await fireEvent.click(modelOption);
    const embeddingSelect = view.getByRole("combobox", { name: "选择企业 Embedding 模型" });
    await fireEvent.click(embeddingSelect);
    await fireEvent.click(await waitFor(() => view.getByRole("option", {
      name: /企业向量模型.*768 维.*enterprise\/embed-model/
    })));
    await fireEvent.click(view.getByRole("button", { name: "保存策略" }));

    await waitFor(() => expect(backendApi.updateQaMemorySettings).toHaveBeenCalledWith({
      primaryChatModelId: "enterprise/chat-model-v2",
      primaryEmbeddingModelId: "enterprise/embed-model",
      expectedVersion: 3
    }));
  });

  it("does not allow clearing the mandatory fixed chat model", async () => {
    const view = render(MemoryAdminPanel, { global: { provide: { api: api() } } });
    await view.findByRole("combobox", { name: "选择固定内部 CHAT 模型" });

    const selectRoot = view.container.querySelector<HTMLElement>(".memory-model-select");
    expect(selectRoot).not.toBeNull();
    await fireEvent.mouseEnter(selectRoot!);
    expect(view.container.querySelector(".memory-model-select .el-select__clear")).toBeNull();
  });

  it("offers an in-product configuration path when no probed CHAT model exists", async () => {
    const backendApi = api();
    vi.mocked(backendApi.getInternalModelProviderModels).mockResolvedValue([]);
    const view = render(MemoryAdminPanel, { global: { provide: { api: backendApi } } });

    const configureButton = await view.findByRole("button", { name: "配置并探测 CHAT 模型" });
    await fireEvent.click(configureButton);
    expect(view.emitted("configureModels")).toHaveLength(1);
  });

  it("shows a stable management error and reloads global health and settings on retry", async () => {
    const backendApi = api();
    vi.mocked(backendApi.getQaMemoryAdminHealth)
      .mockRejectedValueOnce(new Error("memory vip timeout"))
      .mockResolvedValue({
        enabled: true,
        memoryService: {
          available: true,
          status: "UP",
          version: "2.0.17",
          profiles: [],
          projectionBacklog: { pending: 0, processing: 0, dead: 0 }
        },
        primaryChatModelId: "enterprise/chat-model",
        primaryEmbeddingModelId: null,
        cpuEmbeddingModelId: "memory-bge-small-zh-v1.5",
        queuePending: 0,
        queueProcessing: 0,
        queueDead: 0
      });
    const view = render(MemoryAdminPanel, { global: { provide: { api: backendApi } } });

    expect(await view.findByText("记忆管理数据加载失败")).toBeTruthy();
    await fireEvent.click(view.getByRole("button", { name: "重试" }));
    expect((await view.findByTestId("memory-health-mem0")).textContent).toContain("就绪");
    expect(backendApi.getQaMemoryAdminHealth).toHaveBeenCalledTimes(2);
    expect(backendApi.getQaMemorySettings).toHaveBeenCalledTimes(2);
    expect(backendApi.listQaMemoryWhitelist).not.toHaveBeenCalled();
  });
});
