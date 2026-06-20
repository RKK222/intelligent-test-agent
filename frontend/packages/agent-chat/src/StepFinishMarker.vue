<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type StepFinishMarkerProps = {
  part: Extract<MessagePart, { type: "step-finish" }>;
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import { CheckCircle2 } from "lucide-vue-next";
import PartMarker from "./PartMarker.vue";

const props = defineProps<StepFinishMarkerProps>();
const tokenTotal = computed(() => props.part.tokens?.total);
const cost = computed(() => (typeof props.part.cost === "number" ? `$${props.part.cost.toFixed(4)}` : undefined));
</script>

<template>
  <PartMarker :icon="CheckCircle2" accent="ok" :test-id="`step-finish-${part.partId}`">
    <span class="text-[var(--ta-chat-subtle)]">步骤完成</span>
    <span v-if="part.reason" class="text-[var(--ta-chat-muted)]">· {{ part.reason }}</span>
    <template #chips>
      <span
        v-if="tokenTotal !== undefined"
        class="rounded border border-[var(--ta-chat-border-strong)] bg-[var(--ta-chat-chip-bg)] px-1.5 py-0.5 text-[10px] text-[var(--ta-chat-muted)]"
      >
        {{ tokenTotal }} tokens
      </span>
      <span
        v-if="cost"
        class="rounded border border-[var(--ta-chat-border-strong)] bg-[var(--ta-chat-chip-bg)] px-1.5 py-0.5 text-[10px] text-[var(--ta-chat-muted)]"
      >
        {{ cost }}
      </span>
    </template>
  </PartMarker>
</template>
