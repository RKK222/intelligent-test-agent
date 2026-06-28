<script lang="ts">
export type PlainAnswerProps = { text: string };
</script>

<script setup lang="ts">
defineProps<PlainAnswerProps>();
</script>

<template>
  <div
    data-testid="answer-part"
    class="rounded-md border border-[var(--ta-chat-answer-border)] bg-[var(--ta-chat-answer-bg)] px-3 py-2.5"
  >
    <div class="whitespace-pre-wrap text-[13px] leading-[1.4] tracking-[-0.01em] text-[var(--ta-chat-text)]">{{ text }}</div>
  </div>
</template>
