<!-- 作废说明：旧气泡消息 part 渲染路径已被 opencode-like/OpencodeTimeline 取代；仅为历史兼容保留，不再扩展新能力。 -->
<script lang="ts">
import type { Component } from "vue";
import type { MessagePart } from "@test-agent/shared-types";

export type MessagePartsProps = {
  parts: NonNullable<Extract<import("@test-agent/shared-types").AgentMessage, { role: "assistant" }>["parts"]>;
  fallbackText: string;
  running?: boolean;
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import AnswerPart from "./AnswerPart.vue";
import PlainAnswer from "./PlainAnswer.vue";
import ReasoningPartBlock from "./ReasoningPartBlock.vue";
import ToolPartBlock from "./ToolPartBlock.vue";
import FilePartBlock from "./FilePartBlock.vue";
import SubtaskPartBlock from "./SubtaskPartBlock.vue";
import PatchBlock from "./PatchBlock.vue";
import SnapshotBlock from "./SnapshotBlock.vue";
import StepMarker from "./StepMarker.vue";
import StepFinishMarker from "./StepFinishMarker.vue";
import AgentChip from "./AgentChip.vue";
import RetryBlock from "./RetryBlock.vue";
import CompactionMarker from "./CompactionMarker.vue";
import { normalizeProcessStatus } from "./process-status";

const props = defineProps<MessagePartsProps>();
const hasAnswer = computed(() => props.parts.some((part) => part.type === "text" && part.text.trim().length > 0));
const showFallback = computed(() => props.parts.length === 0);

// 合并所有 reasoning 类型的零件，放到渲染列表的最上方展示
const processedParts = computed(() => {
  const reasoningParts = props.parts.filter((p) => p.type === "reasoning") as Array<Extract<MessagePart, { type: "reasoning" }>>;
  const otherParts = props.parts.filter((p) => p.type !== "reasoning");

  if (reasoningParts.length === 0) {
    return otherParts;
  }

  const mergedText = reasoningParts
    .map((p) => p.text)
    .filter((t) => typeof t === "string" && t.trim().length > 0)
    .join("\n\n")
    .trim();

  let mergedStatus = "completed";
  if (props.running) {
    const hasRunning = reasoningParts.some((p) => normalizeProcessStatus(p.status) === "running");
    if (hasRunning) {
      mergedStatus = "running";
    }
  }

  let totalDurationMs = 0;
  for (const p of reasoningParts) {
    if (typeof p.durationMs === "number" && Number.isFinite(p.durationMs)) {
      totalDurationMs += p.durationMs;
    }
  }

  const mergedReasoning: Extract<MessagePart, { type: "reasoning" }> = {
    partId: "merged-reasoning",
    type: "reasoning",
    text: mergedText,
    status: mergedStatus,
    durationMs: totalDurationMs > 0 ? totalDurationMs : undefined
  };

  return [mergedReasoning, ...otherParts];
});

// 按 part.type 查表分发到对应展示组件，新增类型只需在此注册，不再堆叠 v-else-if
const PART_COMPONENTS: Partial<Record<MessagePart["type"], Component>> = {
  text: AnswerPart,
  reasoning: ReasoningPartBlock,
  tool: ToolPartBlock,
  file: FilePartBlock,
  subtask: SubtaskPartBlock,
  patch: PatchBlock,
  snapshot: SnapshotBlock,
  "step-start": StepMarker,
  "step-finish": StepFinishMarker,
  agent: AgentChip,
  retry: RetryBlock,
  compaction: CompactionMarker
};

// reasoning 需要依据是否已有最终回答决定默认展开，其余组件只接收 part 极其运行状态
function partProps(part: MessagePart): Record<string, unknown> {
  if (part.type === "reasoning") {
    return {
      part,
      openByDefault: part.status === "running" || !hasAnswer.value,
      running: props.running
    };
  }
  if (part.type === "tool") {
    return {
      part,
      running: props.running
    };
  }
  return { part };
}
</script>

<template>
  <PlainAnswer v-if="showFallback" :text="fallbackText" />
  <div v-else class="space-y-2">
    <template v-for="part in processedParts" :key="part.partId">
      <component :is="PART_COMPONENTS[part.type]" v-if="PART_COMPONENTS[part.type]" v-bind="partProps(part)" />
      <pre
        v-else
        class="max-h-32 overflow-auto rounded border border-[var(--ta-chat-border)] bg-[var(--ta-chat-detail-bg)] p-2 whitespace-pre-wrap text-[11px] text-[var(--ta-chat-muted)]"
      >{{ JSON.stringify((part as { payload?: unknown }).payload ?? part, null, 2) }}</pre>
    </template>
  </div>
</template>
