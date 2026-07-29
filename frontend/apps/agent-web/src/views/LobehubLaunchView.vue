<script setup lang="ts">
import { createBackendApiClient } from "@test-agent/backend-api";
import { Button, Spinner } from "@test-agent/ui-kit";
import { onMounted, ref } from "vue";
import { launchLobehubInCurrentTab } from "../components/lobehub-launch";

const api = createBackendApiClient({
  baseUrl: import.meta.env.VITE_TEST_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080"
});
const error = ref("");
const loading = ref(false);

async function launch() {
  if (loading.value) return;
  loading.value = true;
  error.value = "";
  try {
    await launchLobehubInCurrentTab(api);
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : "通用问答暂时不可用";
    loading.value = false;
  }
}

onMounted(() => void launch());
</script>

<template>
  <main class="flex min-h-screen items-center justify-center bg-[var(--ta-bg)] px-6">
    <section class="w-full max-w-md rounded-lg border border-[var(--ta-border)] bg-[var(--ta-panel)] p-8 text-center shadow-sm">
      <div v-if="loading" class="flex flex-col items-center gap-4" role="status">
        <Spinner />
        <div>
          <h1 class="text-lg font-semibold text-[var(--ta-text)]">正在进入通用问答</h1>
          <p class="mt-2 text-sm text-[var(--ta-muted)]">正在通过平台登录态完成安全交接…</p>
        </div>
      </div>
      <div v-else class="flex flex-col items-center gap-4" role="alert">
        <div>
          <h1 class="text-lg font-semibold text-[var(--ta-text)]">暂时无法进入通用问答</h1>
          <p class="mt-2 text-sm text-[var(--ta-muted)]">{{ error }}</p>
        </div>
        <Button variant="primary" size="md" @click="launch">重试</Button>
      </div>
    </section>
  </main>
</template>
