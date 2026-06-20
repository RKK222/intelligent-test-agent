<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import { Archive, GitBranch, PanelRight, RotateCcw, Square } from "lucide-vue-next";
import SessionForkDialog from "@/components/SessionForkDialog.vue";
import SessionShareButton from "@/components/SessionShareButton.vue";
import { useSessionStore } from "@/stores/session";

const session = useSessionStore();
const router = useRouter();
const forkOpen = ref(false);
const emit = defineEmits<{
  panel: [];
}>();

const forkMessages = computed(() => [...session.userMessages].reverse());
const hasUserMessages = computed(() => forkMessages.value.length > 0);
const busy = computed(() => Boolean(session.actionBusy));
const canCompact = computed(() => Boolean(session.activeSession?.model?.id && session.activeSession?.model?.providerId));

// 工具栏只触发 store action，避免组件直接拼接 opencode server 请求。
async function selectFork(messageId: string) {
  const forked = await session.forkFromMessage(messageId);
  forkOpen.value = false;
  if (forked) {
    await router.push({ name: "session", params: { workspaceId: forked.workspaceId, sessionId: forked.sessionId } });
  }
}
</script>

<template>
  <div class="toolbar-buttons">
    <SessionShareButton />
    <button class="icon-text" type="button" aria-label="Fork session" :disabled="!hasUserMessages || busy" @click="forkOpen = true">
      <GitBranch :size="15" />Fork
    </button>
    <button class="icon-text" type="button" aria-label="Compact session" :disabled="!canCompact || busy" @click="session.compactSession">
      <Archive :size="15" />Compact
    </button>
    <button
      class="icon-text"
      type="button"
      aria-label="Revert latest user message"
      :disabled="!hasUserMessages || busy"
      @click="session.revertLatestUserMessage"
    >
      <RotateCcw :size="15" />Revert
    </button>
    <button class="icon-button" type="button" aria-label="Abort session" :disabled="busy" @click="session.abort">
      <Square :size="15" />
    </button>
    <button class="icon-button mobile-only" type="button" aria-label="Panel" @click="emit('panel')">
      <PanelRight :size="15" />
    </button>
  </div>

  <SessionForkDialog v-if="forkOpen" :messages="forkMessages" :busy="session.actionBusy === 'fork'" @close="forkOpen = false" @select="selectFork" />
</template>
