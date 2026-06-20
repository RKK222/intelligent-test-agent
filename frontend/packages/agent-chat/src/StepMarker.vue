<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type StepMarkerProps = {
  part: Extract<MessagePart, { type: "step-start" }>;
};
</script>

<script setup lang="ts">
import { PlayCircle } from "lucide-vue-next";
import PartMarker from "./PartMarker.vue";

defineProps<StepMarkerProps>();
</script>

<template>
  <PartMarker :icon="PlayCircle" accent="muted" :test-id="`step-start-${part.partId}`">
    <span class="text-[var(--ta-chat-subtle)]">步骤开始</span>
    <span v-if="part.snapshot" class="text-[var(--ta-chat-muted)]">· 含快照</span>
  </PartMarker>
</template>
