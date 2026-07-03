<!-- 作废说明：旧气泡消息 part 渲染路径已被 opencode-like/OpencodeTimeline 取代；仅为历史兼容保留，不再扩展新能力。 -->
<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type SubtaskPartBlockProps = {
  part: Extract<MessagePart, { type: "subtask" }>;
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import { GitBranch } from "lucide-vue-next";
import ProcessDisclosure from "./ProcessDisclosure.vue";
import { PART_META } from "./part-meta";
import { normalizeProcessStatus } from "./process-status";

const props = defineProps<SubtaskPartBlockProps>();
const meta = PART_META.subtask;
const status = computed(() => normalizeProcessStatus(props.part.status ?? "running"));
const summary = computed(() => props.part.description || props.part.prompt || "子任务执行中");
const defaultOpen = computed(() => status.value === "running");
</script>

<template>
  <ProcessDisclosure
    :id="part.partId"
    :test-id="`subtask-part-${part.partId}`"
    :title="meta.label"
    :status="status"
    status-kind="task"
    accent="neutral"
    :summary="summary"
    :default-open="defaultOpen"
  >
    <div class="space-y-2 text-[12px] leading-5 text-[var(--ta-chat-muted)]">
      <div class="flex flex-wrap items-center gap-2">
        <component :is="meta.icon" class="h-3.5 w-3.5 text-[var(--ta-chat-subtle)]" />
        <span
          class="rounded border border-[var(--ta-chat-border-strong)] bg-[var(--ta-chat-chip-bg)] px-2 py-0.5 font-mono text-[11px] text-[var(--ta-chat-text)]"
        >
          {{ part.agent || "子 Agent" }}
        </span>
        <span v-if="part.model" class="text-[11px] text-[var(--ta-chat-muted)]">模型: {{ part.model }}</span>
        <span v-if="part.command" class="text-[11px] text-[var(--ta-chat-muted)]">命令: {{ part.command }}</span>
      </div>
      <div v-if="part.prompt" class="whitespace-pre-wrap text-[var(--ta-chat-text)]">{{ part.prompt }}</div>
    </div>
  </ProcessDisclosure>
</template>
