<script lang="ts">
export type ReadonlyTranscriptProps = { sessionId: string };
</script>

<script setup lang="ts">
import { ref, shallowRef, watch } from "vue";
import { createBackendApiClient } from "@test-agent/backend-api";
import type { Session, SessionMessage } from "@test-agent/shared-types";
import { FeedbackBanner, type Feedback } from "@test-agent/ui-kit";

const props = defineProps<ReadonlyTranscriptProps>();

const apiBaseUrl = import.meta.env.VITE_TEST_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
const api = createBackendApiClient({ baseUrl: apiBaseUrl });
const session = shallowRef<Session | null>(null);
const messages = ref<SessionMessage[]>([]);
const feedback = ref<Feedback | null>(null);

async function load() {
  try {
    const [nextSession, page] = await Promise.all([api.getSession(props.sessionId), api.listSessionMessages(props.sessionId, 1, 200)]);
    session.value = nextSession;
    messages.value = page.items;
  } catch (error) {
    feedback.value = {
      kind: "error",
      title: "Transcript 加载失败",
      description: error instanceof Error ? error.message : "未知错误"
    };
  }
}

watch(() => props.sessionId, () => void load(), { immediate: true });
</script>

<template>
  <main class="min-h-screen bg-[var(--ta-bg)] text-slate-100">
    <section class="mx-auto flex min-h-screen w-full max-w-4xl flex-col px-4 py-6">
      <header class="border-b border-slate-800 pb-4">
        <div class="text-[12px] uppercase tracking-wide text-slate-500">Readonly transcript</div>
        <h1 class="ta-display mt-2 text-2xl">{{ session?.title ?? sessionId }}</h1>
        <div class="mt-2 flex flex-wrap gap-2 text-[12px] text-slate-500">
          <span>{{ session?.status ?? "loading" }}</span>
          <span>{{ session?.updatedAt ? new Date(session.updatedAt).toLocaleString("zh-CN", { hour12: false }) : "" }}</span>
        </div>
      </header>
      <div class="min-h-0 flex-1 space-y-3 py-4">
        <article v-for="message in messages" :key="message.messageId" class="rounded-[10px] border border-[var(--ta-border)] bg-[#f4f5f7] p-3">
          <div class="mb-2 flex items-center justify-between gap-2 text-[11px] text-slate-500">
            <span>{{ message.role }}</span>
            <span>{{ new Date(message.createdAt).toLocaleString("zh-CN", { hour12: false }) }}</span>
          </div>
          <div class="whitespace-pre-wrap text-[13px] leading-6 text-slate-100">{{ message.content }}</div>
        </article>
        <div v-if="!messages.length && !feedback" class="py-12 text-center text-[12px] text-slate-500">暂无消息</div>
      </div>
      <FeedbackBanner :feedback="feedback" />
    </section>
  </main>
</template>
