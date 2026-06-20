<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type RetryBlockProps = {
  part: Extract<MessagePart, { type: "retry" }>;
};
</script>

<script setup lang="ts">
import { computed, ref } from "vue";
import { RefreshCw } from "lucide-vue-next";
import PartMarker from "./PartMarker.vue";

const props = defineProps<RetryBlockProps>();
const expanded = ref(false);
const hasDetail = computed(() => Boolean(props.part.error?.name || props.part.time?.created));
const message = computed(() => props.part.error?.message || props.part.error?.name || "重试中");
</script>

<template>
  <PartMarker
    :icon="RefreshCw"
    accent="warn"
    :test-id="`retry-part-${part.partId}`"
  >
    <span class="text-[var(--ta-chat-subtle)]">重试 第 {{ part.attempt }} 次</span>
    <span class="text-[var(--ta-chat-muted)]">· {{ message }}</span>
    <button
      v-if="hasDetail"
      type="button"
      class="ml-1 text-[var(--ta-chat-muted)] underline-offset-2 hover:underline"
      @click="expanded = !expanded"
    >
      {{ expanded ? "收起" : "详情" }}
    </button>
    <template #chips />
  </PartMarker>
  <div v-if="expanded && hasDetail" class="ml-2.5 border-l-2 border-[var(--ta-warn)] pl-2.5 text-[11px] text-[var(--ta-chat-muted)]">
    <div v-if="part.error?.name">错误: {{ part.error.name }}</div>
    <div v-if="part.time?.created">时间戳: {{ part.time.created }}</div>
  </div>
</template>
