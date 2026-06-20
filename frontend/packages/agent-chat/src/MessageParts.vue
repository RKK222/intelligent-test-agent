<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type MessagePartsProps = {
  parts: NonNullable<Extract<import("@test-agent/shared-types").AgentMessage, { role: "assistant" }>["parts"]>;
  fallbackText: string;
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import AnswerPart from "./AnswerPart.vue";
import PlainAnswer from "./PlainAnswer.vue";
import ReasoningPartBlock from "./ReasoningPartBlock.vue";
import ToolPartBlock from "./ToolPartBlock.vue";

const props = defineProps<MessagePartsProps>();
const hasAnswer = computed(() => props.parts.some((part) => part.type === "text" && part.text.trim().length > 0));
const showFallback = computed(() => props.parts.length === 0);
</script>

<template>
  <PlainAnswer v-if="showFallback" :text="fallbackText" />
  <div v-else class="space-y-2">
    <template v-for="part in parts" :key="part.partId">
      <AnswerPart v-if="part.type === 'text'" :part="part" />
      <ReasoningPartBlock
        v-else-if="part.type === 'reasoning'"
        :part="part"
        :open-by-default="part.status === 'running' || !hasAnswer"
      />
      <ToolPartBlock v-else-if="part.type === 'tool'" :part="part" />
      <div
        v-else-if="part.type === 'file'"
        class="rounded border border-[var(--ta-chat-border)] bg-[var(--ta-chat-process-bg)] p-2 font-mono text-[12px] text-[var(--ta-chat-muted)]"
      >
        {{ part.path ?? part.name ?? part.partId }}
      </div>
      <pre
        v-else
        class="max-h-32 overflow-auto rounded border border-[var(--ta-chat-border)] bg-[var(--ta-chat-detail-bg)] p-2 whitespace-pre-wrap text-[11px] text-[var(--ta-chat-muted)]"
      >{{ JSON.stringify((part as { payload?: unknown }).payload, null, 2) }}</pre>
    </template>
  </div>
</template>
