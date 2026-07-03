<!-- 作废说明：旧气泡消息 part 渲染路径已被 opencode-like/OpencodeTimeline 取代；仅为历史兼容保留，不再扩展新能力。 -->
<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type FilePartBlockProps = {
  part: Extract<MessagePart, { type: "file" }>;
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import { FileText } from "lucide-vue-next";
import { accentBorderClass } from "./part-meta";

const props = defineProps<FilePartBlockProps>();
const displayName = computed(() => props.part.name ?? props.part.path ?? props.part.partId);
const accentClass = accentBorderClass("ok");
</script>

<template>
  <div
    :data-testid="`file-part-${part.partId}`"
    :class="['flex items-center gap-2 rounded-md border border-[var(--ta-chat-border)] bg-[var(--ta-chat-process-bg)] px-2.5 py-1.5 font-mono text-[12px] text-[var(--ta-chat-text)]', accentClass]"
  >
    <FileText class="h-3.5 w-3.5 shrink-0 text-[var(--ta-chat-muted)]" />
    <span class="min-w-0 flex-1 truncate">{{ displayName }}</span>
    <span
      v-if="part.mimeType"
      class="shrink-0 rounded border border-[var(--ta-chat-border-strong)] bg-[var(--ta-chat-chip-bg)] px-1.5 py-0.5 text-[10px] text-[var(--ta-chat-muted)]"
    >
      {{ part.mimeType }}
    </span>
    <a
      v-if="part.url"
      :href="part.url"
      target="_blank"
      rel="noreferrer"
      class="shrink-0 text-[var(--ta-chat-subtle)] underline-offset-2 hover:underline"
      >打开</a
    >
  </div>
</template>
