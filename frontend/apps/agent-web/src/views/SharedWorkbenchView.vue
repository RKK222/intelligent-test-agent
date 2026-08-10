<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { BackendApiError, createBackendApiClient, createSessionShareApiClient } from "@test-agent/backend-api";
import type { SessionShareAccess } from "@test-agent/shared-types";
import AgentWorkbench from "../components/AgentWorkbench.vue";
import { resolveSessionShareEntry } from "../session-share-route";

const props = defineProps<{ shareId: string }>();
const router = useRouter();
const apiBaseUrl = import.meta.env.VITE_TEST_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
const access = ref<SessionShareAccess | null>(null);
const loading = ref(true);
const invalidReason = ref("");

onMounted(async () => {
  const ordinaryApi = createBackendApiClient({ baseUrl: apiBaseUrl });
  const sharedApi = createSessionShareApiClient({ baseUrl: apiBaseUrl, shareId: props.shareId });
  const entry = await resolveSessionShareEntry(props.shareId, sharedApi, ordinaryApi);
  if (entry.kind === "owner") {
    await router.replace({ name: "workbench", query: { sessionId: entry.sessionId } });
    return;
  }
  if (entry.kind === "shared") {
    access.value = entry.access;
    loading.value = false;
    return;
  }
  invalidReason.value = invalidShareReason(entry.error);
  loading.value = false;
});

function invalidShareReason(error: unknown): string {
  if (error instanceof BackendApiError) {
    const reason = typeof error.details.reason === "string" ? error.details.reason : "";
    if (reason === "REVOKED") return "该分享已被会话所属人取消";
    if (reason === "REMOVED") return "你已不在该分享会话中";
    if (reason === "SESSION_ARCHIVED") return "该分享会话已归档";
    if (reason === "EXPIRED" || error.code === "SESSION_SHARE_EXPIRED") return "该分享链接已过期";
  }
  return "分享链接无效、已失效或你没有访问权限";
}
</script>

<template>
  <main v-if="loading" class="share-entry-state" aria-busy="true">
    <div class="share-entry-state__spinner" />
    <p>正在校验分享权限…</p>
  </main>
  <main v-else-if="!access" class="share-entry-state share-entry-state--invalid">
    <div class="share-entry-state__icon">!</div>
    <h1>无法打开分享会话</h1>
    <p>{{ invalidReason }}</p>
    <button type="button" @click="router.replace({ name: 'workbench' })">返回我的工作台</button>
  </main>
  <AgentWorkbench
    v-else
    :session-share-id="shareId"
    :initial-share-access="access"
  />
</template>

<style scoped>
.share-entry-state {
  display: grid;
  min-height: 100vh;
  place-content: center;
  justify-items: center;
  gap: 12px;
  background: #f5f7fb;
  color: #23304a;
  text-align: center;
}

.share-entry-state__spinner {
  width: 30px;
  height: 30px;
  border: 3px solid #dbe3f4;
  border-top-color: #466ee8;
  border-radius: 50%;
  animation: share-spin .8s linear infinite;
}

.share-entry-state__icon {
  display: grid;
  width: 44px;
  height: 44px;
  place-content: center;
  border-radius: 50%;
  background: #fff0f0;
  color: #b42318;
  font-size: 24px;
  font-weight: 750;
}

.share-entry-state h1,
.share-entry-state p { margin: 0; }
.share-entry-state p { color: #667085; }
.share-entry-state button {
  margin-top: 8px;
  border: 0;
  border-radius: 8px;
  background: #315ed8;
  color: white;
  padding: 9px 16px;
  cursor: pointer;
}

@keyframes share-spin { to { transform: rotate(360deg); } }
</style>
