import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { BackendApiClient } from "@test-agent/backend-api";
import { useMemoryAccessStore } from "../src/stores/memoryAccessStore";

function api(enabled: boolean) {
  return {
    getQaMemoryAvailability: vi.fn().mockResolvedValue({ enabled })
  } as Partial<BackendApiClient> as BackendApiClient;
}

describe("memoryAccessStore", () => {
  beforeEach(() => setActivePinia(createPinia()));

  it("reuses one availability result for the same login token", async () => {
    const backendApi = api(true);
    const store = useMemoryAccessStore();

    await expect(store.ensure(backendApi, "token-a")).resolves.toBe(true);
    await expect(store.ensure(backendApi, "token-a")).resolves.toBe(true);

    expect(backendApi.getQaMemoryAvailability).toHaveBeenCalledTimes(1);
    expect(store.allowed).toBe(true);
    expect(store.resolved).toBe(true);
  });

  it("fails closed when availability cannot be confirmed", async () => {
    const backendApi = api(true);
    vi.mocked(backendApi.getQaMemoryAvailability).mockRejectedValue(new Error("network unavailable"));
    const store = useMemoryAccessStore();

    await expect(store.refresh(backendApi, "token-a")).resolves.toBe(false);

    expect(store.allowed).toBe(false);
    expect(store.resolved).toBe(true);
  });

  it("does not reuse another user's cached authorization", async () => {
    const backendApi = api(true);
    const store = useMemoryAccessStore();
    await store.ensure(backendApi, "token-a");
    vi.mocked(backendApi.getQaMemoryAvailability).mockResolvedValue({ enabled: false });

    await expect(store.ensure(backendApi, "token-b")).resolves.toBe(false);

    expect(backendApi.getQaMemoryAvailability).toHaveBeenCalledTimes(2);
    expect(store.allowed).toBe(false);
  });
});
