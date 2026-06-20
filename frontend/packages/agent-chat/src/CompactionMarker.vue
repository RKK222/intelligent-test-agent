<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type CompactionMarkerProps = {
  part: Extract<MessagePart, { type: "compaction" }>;
};
</script>

<script setup lang="ts">
import { Minimize2 } from "lucide-vue-next";
import PartMarker from "./PartMarker.vue";

defineProps<CompactionMarkerProps>();
</script>

<template>
  <PartMarker :icon="Minimize2" accent="muted" :test-id="`compaction-part-${part.partId}`">
    <span class="text-[var(--ta-chat-subtle)]">上下文已压缩</span>
    <span v-if="part.auto" class="text-[var(--ta-chat-muted)]">· 自动</span>
    <span v-if="part.overflow" class="text-[var(--ta-warn)]">· 溢出</span>
  </PartMarker>
</template>
