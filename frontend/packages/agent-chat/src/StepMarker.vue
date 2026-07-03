<!-- 作废说明：旧气泡消息 part 渲染路径已被 opencode-like/OpencodeTimeline 取代；仅为历史兼容保留，不再扩展新能力。 -->
<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type StepMarkerProps = {
  part: Extract<MessagePart, { type: "step-start" }>;
};
</script>

<script setup lang="ts">
// 步骤开始 marker：
// - 内联一行 PartMarker 即可表达"回合起点"
// - snapshot 非空时默认折叠展示，避免大段 JSON 把时间线撑开；用户可点开查看
import { computed, ref } from "vue";
import { Camera, ChevronDown, ChevronRight, PlayCircle } from "lucide-vue-next";
import PartMarker from "./PartMarker.vue";

const props = defineProps<StepMarkerProps>();
const open = ref(false);
const snapshot = computed(() => props.part.snapshot ?? "");
const hasSnapshot = computed(() => snapshot.value.length > 0);
</script>

<template>
  <PartMarker :icon="PlayCircle" accent="muted" :test-id="`step-start-${part.partId}`">
    <span class="text-[var(--ta-chat-subtle)]">步骤开始</span>
    <button
      v-if="hasSnapshot"
      type="button"
      class="ml-1 inline-flex items-center gap-0.5 text-[var(--ta-chat-muted)] hover:text-[var(--ta-chat-text)]"
      :title="open ? '收起快照' : '查看快照'"
      @click="open = !open"
    >
      <Camera class="h-3 w-3" />
      快照
      <ChevronDown v-if="open" class="h-3 w-3" />
      <ChevronRight v-else class="h-3 w-3" />
    </button>
  </PartMarker>
  <div
    v-if="open && hasSnapshot"
    :data-testid="`step-start-snapshot-${part.partId}`"
    class="ml-2.5 border-l-2 border-[var(--ta-chat-border)] pl-2.5"
  >
    <div class="mb-1 text-[10px] uppercase tracking-wide text-[var(--ta-chat-muted)]">snapshot · {{ snapshot.length }} 字符</div>
    <pre
      class="max-h-44 overflow-auto whitespace-pre-wrap rounded border border-[var(--ta-chat-border)] bg-[var(--ta-chat-detail-bg)] p-2 font-mono text-[11px] leading-[1.55] text-[var(--ta-chat-muted)]"
    >{{ snapshot }}</pre>
  </div>
</template>
