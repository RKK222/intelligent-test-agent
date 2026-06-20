<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import PromptComposer from "@/components/PromptComposer.vue";
import { usePromptStore } from "@/stores/prompt";
import { useSessionStore } from "@/stores/session";
import { useWorkspaceStore } from "@/stores/workspace";

const route = useRoute();
const router = useRouter();
const prompt = usePromptStore();
const session = useSessionStore();
const workspace = useWorkspaceStore();
const workspaceId = computed(() => String(route.query.workspaceId ?? workspace.selectedWorkspaceId ?? workspace.workspaces[0]?.workspaceId ?? ""));

async function submit() {
  if (!workspaceId.value) {
    return;
  }
  const created = await session.createDraftSession(workspaceId.value, prompt.snapshot());
  await session.sendPrompt(prompt.snapshot());
  prompt.remember();
  prompt.reset();
  router.push(`/w/${created.workspaceId}/session/${created.sessionId}`);
}
</script>

<template>
  <main class="session-grid new-session-grid">
    <section class="conversation-pane">
      <div class="new-session-copy">
        <p class="eyebrow">New draft</p>
        <h1>Start from the composer</h1>
        <p>The draft route mirrors opencode: choose context, attach files, then submit into a real session.</p>
      </div>
      <PromptComposer :busy="session.sending" @submit="submit" />
    </section>
  </main>
</template>
