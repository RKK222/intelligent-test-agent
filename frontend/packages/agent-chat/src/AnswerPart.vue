<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type AnswerPartProps = {
  part: Extract<MessagePart, { type: "text" }>;
};
</script>

<script setup lang="ts">
defineProps<AnswerPartProps>();
</script>

<template>
  <div
    data-testid="answer-part"
    class="rounded-md border border-[var(--ta-chat-answer-border)] bg-[var(--ta-chat-answer-bg)] px-3 py-2.5"
  >
    <div class="mb-1.5 text-[11px] font-semibold text-[var(--ta-chat-subtle)]">最终回答</div>
    <div class="whitespace-pre-wrap text-[13px] leading-6 text-[var(--ta-chat-text)]">{{ part.text }}</div>
  </div>
</template>
