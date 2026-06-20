<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import PromptComposer from "@/components/PromptComposer.vue";
import SessionDockStack from "@/components/SessionDockStack.vue";
import SessionTimeline from "@/components/SessionTimeline.vue";
import SessionToolbarActions from "@/components/SessionToolbarActions.vue";
import SidePanel from "@/components/SidePanel.vue";
import { usePromptStore } from "@/stores/prompt";
import { useSessionStore } from "@/stores/session";
import { useWorkspaceStore } from "@/stores/workspace";

const route = useRoute();
const router = useRouter();
const prompt = usePromptStore();
const session = useSessionStore();
const workspace = useWorkspaceStore();
const mobilePanelOpen = ref(false);

const routeWorkspaceId = computed(() => String(route.params.workspaceId ?? workspace.selectedWorkspaceId ?? ""));
const sessionId = computed(() => String(route.params.sessionId ?? ""));
const workspaceSessions = computed(() => {
  const currentWorkspaceId = routeWorkspaceId.value;
  return workspace.sessions.filter((item) => !currentWorkspaceId || item.workspaceId === currentWorkspaceId);
});

watch(
  routeWorkspaceId,
  (workspaceId) => {
    if (workspaceId) {
      workspace.selectWorkspace(workspaceId);
    }
  },
  { immediate: true }
);

watch(sessionId, (value) => {
  if (value) {
    void load(value);
  }
}, { immediate: true });

watch([sessionId, workspaceSessions], ([value]) => {
  if (value) {
    return;
  }
  const fallback = workspaceSessions.value[0];
  if (!fallback) {
    return;
  }
  // 路由允许省略 sessionId；深链首屏时会话列表异步到达，因此用 replace 补齐当前 workspace 的首个会话。
  void router.replace(`/w/${fallback.workspaceId}/session/${fallback.sessionId}`);
}, { immediate: true });

async function load(value: string) {
  await session.load(value);
}

function openSession(item: { workspaceId: string; sessionId: string }) {
  if (item.sessionId === sessionId.value) {
    return;
  }
  mobilePanelOpen.value = false;
  void router.push(`/w/${item.workspaceId}/session/${item.sessionId}`);
}

async function submit() {
  await session.sendPrompt(prompt.snapshot());
  prompt.remember();
  prompt.reset();
}
</script>

<template>
  <main class="session-grid">
    <aside class="workspace-rail session-rail" aria-label="Session navigation">
      <div class="section-label">Sessions</div>
      <button
        v-for="item in workspaceSessions"
        :key="item.sessionId"
        class="workspace-row"
        :class="{ active: item.sessionId === sessionId }"
        type="button"
        :aria-current="item.sessionId === sessionId ? 'page' : undefined"
        @click="openSession(item)"
      >
        <span class="mini-dot" />
        <span>
          <strong>{{ item.title }}</strong>
          <small>{{ item.status }}</small>
        </span>
      </button>
    </aside>

    <section class="conversation-pane">
      <div class="session-toolbar">
        <div>
          <p class="eyebrow">{{ session.activeSession?.status ?? "Session" }}</p>
          <h1>{{ session.activeSession?.title ?? "Untitled session" }}</h1>
        </div>
        <SessionToolbarActions @panel="mobilePanelOpen = true" />
      </div>
      <div v-if="session.error || session.actionError" class="inline-alert">{{ session.error ?? session.actionError }}</div>
      <SessionTimeline :messages="session.timeline" />
      <SessionDockStack />
      <PromptComposer :busy="session.sending" @submit="submit" />
    </section>

    <button v-if="mobilePanelOpen" class="mobile-panel-backdrop" type="button" aria-label="Dismiss panel overlay" @click="mobilePanelOpen = false" />
    <SidePanel :class="{ 'mobile-panel-open': mobilePanelOpen }" @close="mobilePanelOpen = false" />
  </main>
</template>
