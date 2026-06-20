<script setup lang="ts">
import { computed, onMounted, watch } from "vue";
import { useRoute } from "vue-router";
import { GitBranch, PanelRight, RotateCcw, Square } from "lucide-vue-next";
import PromptComposer from "@/components/PromptComposer.vue";
import SessionShareButton from "@/components/SessionShareButton.vue";
import SessionDockStack from "@/components/SessionDockStack.vue";
import SessionTimeline from "@/components/SessionTimeline.vue";
import SidePanel from "@/components/SidePanel.vue";
import { usePromptStore } from "@/stores/prompt";
import { useSessionStore } from "@/stores/session";
import { useWorkspaceStore } from "@/stores/workspace";

const route = useRoute();
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
        <div class="toolbar-buttons">
          <SessionShareButton />
          <button class="icon-text" type="button"><GitBranch :size="15" />Fork</button>
          <button class="icon-text" type="button"><RotateCcw :size="15" />Revert</button>
          <button class="icon-button" type="button" aria-label="Abort session" @click="session.abort"><Square :size="15" /></button>
          <button class="icon-button mobile-only" type="button" aria-label="Panel"><PanelRight :size="15" /></button>
        </div>
      </div>
      <div v-if="session.error" class="inline-alert">{{ session.error }}</div>
      <SessionTimeline :messages="session.timeline" />
      <SessionDockStack />
      <PromptComposer :busy="session.sending" @submit="submit" />
    </section>

    <SidePanel />
  </main>
</template>
