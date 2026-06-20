import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { createPlatformApi, type PlatformApi } from "@/api/platform";

export const usePlatformStore = defineStore("platform", () => {
  const baseUrl = ref((import.meta.env.VITE_TEST_AGENT_API_BASE_URL ?? "").replace(/\/$/, ""));
  const apiToken = ref(import.meta.env.VITE_TEST_AGENT_API_TOKEN ?? "");
  const api = computed<PlatformApi>(() =>
    createPlatformApi({
      baseUrl: baseUrl.value,
      apiToken: apiToken.value || undefined
    })
  );
  const status = ref<"idle" | "checking" | "ready" | "error">("idle");
  const error = ref<string>();

  async function check() {
    status.value = "checking";
    error.value = undefined;
    try {
      await api.value.listWorkspaces(1, 1);
      status.value = "ready";
    } catch (cause) {
      status.value = "error";
      error.value = cause instanceof Error ? cause.message : "平台 API 不可用";
    }
  }

  return { baseUrl, apiToken, api, status, error, check };
});
