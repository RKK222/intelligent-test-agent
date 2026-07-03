<!-- 作废说明：旧气泡消息 part 渲染路径已被 opencode-like/OpencodeTimeline 取代；仅为历史兼容保留，不再扩展新能力。 -->
<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type StepFinishMarkerProps = {
  part: Extract<MessagePart, { type: "step-finish" }>;
};
</script>

<script setup lang="ts">
// 步骤完成 marker：
// - 头部把 part.tokens 拆成 input/output/reasoning 让用户看清消耗来源
// - cost 按数量级动态调整精度，避免小金额被四舍五入抹零
// - snapshot 非空时默认折叠展示，避免大段 JSON 把时间线撑开
import { computed, ref } from "vue";
import { Camera, CheckCircle2, ChevronDown, ChevronRight } from "lucide-vue-next";
import PartMarker from "./PartMarker.vue";

const props = defineProps<StepFinishMarkerProps>();
const openSnapshot = ref(false);

const tokenInput = computed(() => props.part.tokens?.input);
const tokenOutput = computed(() => props.part.tokens?.output);
const tokenReasoning = computed(() => props.part.tokens?.reasoning);
const tokenTotal = computed(() => props.part.tokens?.total);

// 至少有两类 token 拆开展示才有意义，否则只显示 total 一个 chip
const showTokenBreakdown = computed(
  () =>
    [tokenInput.value, tokenOutput.value, tokenReasoning.value].filter(
      (value) => typeof value === "number" && value > 0
    ).length >= 2
);

// 按成本量级动态选精度：< 0.01 用 6 位、< 1 用 4 位、否则 2 位
const cost = computed(() => {
  if (typeof props.part.cost !== "number" || !Number.isFinite(props.part.cost)) {
    return undefined;
  }
  const value = props.part.cost;
  const digits = value < 0.01 ? 6 : value < 1 ? 4 : 2;
  return `$${value.toFixed(digits)}`;
});

const snapshot = computed(() => props.part.snapshot ?? "");
const hasSnapshot = computed(() => snapshot.value.length > 0);
</script>

<template>
  <PartMarker :icon="CheckCircle2" accent="ok" :test-id="`step-finish-${part.partId}`">
    <span class="text-[var(--ta-chat-subtle)]">步骤完成</span>
    <span v-if="part.reason" class="text-[var(--ta-chat-muted)]">· {{ part.reason }}</span>
    <button
      v-if="hasSnapshot"
      type="button"
      class="ml-1 inline-flex items-center gap-0.5 text-[var(--ta-chat-muted)] hover:text-[var(--ta-chat-text)]"
      :title="openSnapshot ? '收起快照' : '查看快照'"
      @click="openSnapshot = !openSnapshot"
    >
      <Camera class="h-3 w-3" />
      快照
      <ChevronDown v-if="openSnapshot" class="h-3 w-3" />
      <ChevronRight v-else class="h-3 w-3" />
    </button>
    <template #chips>
      <!-- 优先展示拆分明细；缺数据时退回 total -->
      <span
        v-if="showTokenBreakdown"
        :data-testid="`step-finish-tokens-${part.partId}`"
        class="rounded border border-[var(--ta-chat-border-strong)] bg-[var(--ta-chat-chip-bg)] px-1.5 py-0.5 text-[10px] text-[var(--ta-chat-muted)]"
        :title="`input ${tokenInput ?? 0} · output ${tokenOutput ?? 0} · reasoning ${tokenReasoning ?? 0}`"
      >
        in {{ tokenInput ?? 0 }} / out {{ tokenOutput ?? 0 }} / rea {{ tokenReasoning ?? 0 }}
      </span>
      <span
        v-else-if="tokenTotal !== undefined"
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
  <div
    v-if="openSnapshot && hasSnapshot"
    :data-testid="`step-finish-snapshot-${part.partId}`"
    class="ml-2.5 border-l-2 border-[var(--ta-chat-border)] pl-2.5"
  >
    <div class="mb-1 text-[10px] uppercase tracking-wide text-[var(--ta-chat-muted)]">snapshot · {{ snapshot.length }} 字符</div>
    <pre
      class="max-h-44 overflow-auto whitespace-pre-wrap rounded border border-[var(--ta-chat-border)] bg-[var(--ta-chat-detail-bg)] p-2 font-mono text-[11px] leading-[1.55] text-[var(--ta-chat-muted)]"
    >{{ snapshot }}</pre>
  </div>
</template>
