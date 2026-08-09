// @vitest-environment jsdom

import { cleanup, fireEvent, render, waitFor } from "@testing-library/vue";
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
    expect(view.getByTestId("memory-health-queue").textContent).toContain("2 待处理");
    expect(view.getByText("usr_tester")).toBeTruthy();
  });

  it("updates the fixed model and fallback with optimistic version", async () => {
    const backendApi = api();
    const view = render(MemoryAdminPanel, { global: { provide: { api: backendApi } } });
    const input = await view.findByPlaceholderText("例如：internal-provider/model-id");

    await fireEvent.update(input, "enterprise/chat-model-v2");
    await fireEvent.click(view.getByText("允许当前 Run 内部模型回退"));
    await fireEvent.click(view.getByRole("button", { name: "保存策略" }));

    await waitFor(() => expect(backendApi.updateQaMemorySettings).toHaveBeenCalledWith({
      primaryChatModelId: "enterprise/chat-model-v2",
      currentRunModelFallbackEnabled: true,
      expectedVersion: 3
    }));
  });
});
