<script setup lang="ts">
import { computed, watch } from "vue";
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

watch(
  () => route.query.prompt,
  (value) => {
    const text = readQueryString(value);
    if (!text) {
      return;
    }
    // opencode deep link 会把 prompt 查询参数消费进新会话 composer，随后清掉 URL，避免返回草稿页时覆盖用户后续编辑。
    prompt.text = text;
    const query = { ...route.query };
    delete query.prompt;
    void router.replace({ path: route.path, query });
  },
  { immediate: true }
);

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

function readQueryString(value: unknown) {
  if (typeof value === "string") {
    return value.trim();
  }
  return Array.isArray(value) && typeof value[0] === "string" ? value[0].trim() : "";
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
