<script setup lang="ts">
import { computed, onMounted, watch } from "vue";
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

const sessionId = computed(() => String(route.params.sessionId ?? ""));

onMounted(load);
watch(sessionId, load);

async function load() {
  if (sessionId.value) {
    await session.load(sessionId.value);
  }
}

function openSession(item: { workspaceId: string; sessionId: string }) {
  if (item.sessionId === sessionId.value) {
    return;
  }
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
        v-for="item in workspace.sessions"
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
        <SessionToolbarActions />
      </div>
      <div v-if="session.error || session.actionError" class="inline-alert">{{ session.error ?? session.actionError }}</div>
      <SessionTimeline :messages="session.timeline" />
      <SessionDockStack />
      <PromptComposer :busy="session.sending" @submit="submit" />
    </section>

    <SidePanel />
  </main>
</template>
