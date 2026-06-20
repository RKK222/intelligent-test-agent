<script lang="ts">
import type { AgentMessage, RunDiffFile } from "@test-agent/shared-types";

export type AgentCardProps = {
  message: Extract<AgentMessage, { role: "card" }>;
  defaultOpen?: boolean;
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import { Brain, CheckCircle2, FileText, FolderOpen, Terminal } from "lucide-vue-next";
import { Badge, Button } from "@test-agent/ui-kit";
import TimelineCard from "./TimelineCard.vue";
import ToolPayloadBlock from "./ToolPayloadBlock.vue";
import { arrayOfRecords, lineChange, text } from "./chat-utils";

const props = withDefaults(defineProps<AgentCardProps>(), { defaultOpen: false });
const emit = defineEmits<{ openDiff: [] }>();

const steps = computed(() => arrayOfRecords(props.message.payload.steps));
const isPlan = computed(() => props.message.cardType === "plan");
const isTool = computed(() => props.message.cardType === "tool");
const isTest = computed(() => props.message.cardType === "test");
const isDiff = computed(() => props.message.cardType === "diff");

const toolPayload = computed(() => {
  const p = props.message.payload;
  return {
    ...p,
    toolName: text(p.toolName) ?? text(p.rawType) ?? text(p.tool) ?? "tool",
    path: text(p.path),
    summary: text(p.summary) ?? text(p.message) ?? text(p.status),
    output: p.output ?? p.rawOutput
  } as Record<string, unknown>;
});

const diffFiles = computed(() => (props.message.payload.files as RunDiffFile[] | undefined) ?? []);
const eventSummary = computed(() => text(props.message.payload.summary) ?? text(props.message.payload.message) ?? text(props.message.payload.status));
</script>

<template>
  <TimelineCard v-if="isPlan" :id="message.id" title="规划步骤" :default-open="defaultOpen">
    <template #icon><Brain class="h-4 w-4 text-[var(--ta-chat-subtle)]" /></template>
    <div class="space-y-1">
      <div
        v-for="(step, index) in steps"
        :key="`${step.title}-${index}`"
        class="rounded-md border border-[var(--ta-chat-border)] bg-[var(--ta-chat-process-bg)] px-2 py-1.5"
      >
        <div class="flex items-center gap-2">
          <span class="text-[11px] text-[var(--ta-chat-muted)]">{{ index + 1 }}</span>
          <span class="min-w-0 flex-1 text-[12px] text-[var(--ta-chat-text)]">{{ String(step.title ?? "执行步骤") }}</span>
          <Badge :tone="step.status === 'done' ? 'success' : step.status === 'active' ? 'info' : 'neutral'">{{ String(step.status ?? "pending") }}</Badge>
        </div>
      </div>
    </div>
  </TimelineCard>

  <ToolPayloadBlock
    v-else-if="isTool"
    :id="message.id"
    :title="message.title"
    :payload="toolPayload"
    :default-open="defaultOpen"
  />

  <TimelineCard v-else-if="isTest" :id="message.id" :title="message.title" :default-open="defaultOpen">
    <template #icon><Terminal class="h-4 w-4 text-[var(--ta-chat-subtle)]" /></template>
    <div class="flex items-center gap-2">
      <Badge :tone="message.payload.status === 'failed' ? 'danger' : 'success'">{{ String(message.payload.status ?? "finished") }}</Badge>
      <span class="font-mono text-[12px] text-[var(--ta-chat-muted)]">{{ String(message.payload.command ?? "test run") }}</span>
    </div>
  </TimelineCard>

  <TimelineCard
    v-else-if="isDiff"
    :id="message.id"
    :title="message.title"
    :default-open="defaultOpen"
  >
    <template #icon><FolderOpen class="h-4 w-4 text-[var(--ta-chat-subtle)]" /></template>
    <div class="overflow-hidden rounded-md bg-[var(--ta-chat-process-bg)]">
      <div class="grid grid-cols-[minmax(0,1.5fr)_minmax(96px,.6fr)_minmax(96px,.45fr)] border-b border-[var(--ta-chat-border)] px-4 py-2 text-[12px] font-semibold text-[var(--ta-chat-subtle)]">
        <div>文件</div>
        <div>状态</div>
        <div>行变更</div>
      </div>
      <div
        v-for="file in diffFiles"
        :key="file.path"
        class="grid grid-cols-[minmax(0,1.5fr)_minmax(96px,.6fr)_minmax(96px,.45fr)] border-b border-dashed border-[var(--ta-chat-border)] px-4 py-2 last:border-b-0"
      >
        <div class="min-w-0 truncate font-mono text-[12px] text-[var(--ta-chat-text)]">{{ file.path }}</div>
        <div>
          <Badge tone="warning" class="bg-[rgba(245,158,11,.18)] font-bold uppercase tracking-wide">{{ file.status.toUpperCase() }}</Badge>
        </div>
        <div class="font-semibold text-[var(--ta-chat-text)]">{{ lineChange(file) }}</div>
      </div>
    </div>
    <Button class="mt-2" size="sm" variant="primary" @click="emit('openDiff')">查看 Diff</Button>
  </TimelineCard>

  <TimelineCard v-else :id="message.id" :title="message.title" :default-open="defaultOpen">
    <template #icon>
      <CheckCircle2 v-if="message.cardType === 'event'" class="h-4 w-4 text-[var(--ta-chat-subtle)]" />
      <FileText v-else class="h-4 w-4 text-[var(--ta-chat-subtle)]" />
    </template>
    <div v-if="eventSummary" class="rounded-md border border-[var(--ta-chat-border)] bg-[var(--ta-chat-process-bg)] px-3 py-2 text-[12px] leading-6 text-[var(--ta-chat-text)]">
      {{ eventSummary }}
    </div>
    <pre v-else class="max-h-40 overflow-auto whitespace-pre-wrap rounded-md bg-[var(--ta-chat-detail-bg)] p-3 text-[12px] text-[var(--ta-chat-muted)]">{{ JSON.stringify(message.payload, null, 2) }}</pre>
  </TimelineCard>
</template>
