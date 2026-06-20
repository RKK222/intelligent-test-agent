<script lang="ts">
import type { Component } from "vue";
import type { PartAccent } from "./part-meta";

// 内联标记行的统一外壳：单行、左侧 2px 色条 + 图标 + 文案 + 可选尾部 slot（chip 等）。
// 用于 step-start/step-finish/agent/compaction/retry 等克制的元信息展示，不占整块高度。
export type PartMarkerProps = {
  icon: Component;
  accent?: PartAccent;
  testId?: string;
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import { accentColorVar } from "./part-meta";

const props = withDefaults(defineProps<PartMarkerProps>(), { accent: "muted" });
const borderColor = computed(() => accentColorVar(props.accent));
</script>

<template>
  <div
    :data-testid="testId"
    class="flex items-center gap-2 border-l-2 py-1 pl-2.5 text-[11px] text-[var(--ta-chat-muted)]"
    :style="{ borderLeftColor: borderColor }"
  >
    <component :is="icon" class="h-3.5 w-3.5 shrink-0" />
    <div class="min-w-0 flex-1 truncate"><slot /></div>
    <div v-if="$slots.chips" class="flex shrink-0 items-center gap-1.5"><slot name="chips" /></div>
  </div>
</template>
