<script lang="ts">
import type { ProcessStatusKind } from "./process-status";

export type ToolDetailProps = {
  label: string;
  status: unknown;
  purpose?: string;
  input?: Record<string, unknown>;
  output?: unknown;
  metadata?: Record<string, unknown>;
  path?: string;
  statusKind: "tool" | "skill";
  startedAt?: string;
  endedAt?: string;
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import { normalizeProcessStatus, statusLabel, statusToneClass, textValue } from "./process-status";
import { formatTime } from "./chat-utils";

const props = defineProps<ToolDetailProps>();
const normalizedStatus = computed(() => normalizeProcessStatus(props.status));
const metaPurpose = computed(
  () => textValue(props.metadata?.purpose) ?? textValue(props.metadata?.summary) ?? textValue(props.metadata?.description)
);
const hasInput = computed(() => Boolean(props.input && Object.keys(props.input).length));
const outputDisplay = computed(() => {
  if (props.output === undefined || props.output === null) return undefined;
  if (typeof props.output === "string") return props.output;
  try {
    return JSON.stringify(props.output, null, 2);
  } catch {
    return String(props.output);
  }
});
</script>

<template>
  <div class="space-y-2 text-[12px] leading-5 text-[var(--ta-chat-muted)]">
    <div class="flex flex-wrap items-center gap-2">
      <span class="rounded border border-[var(--ta-chat-border-strong)] bg-[var(--ta-chat-chip-bg)] px-2 py-0.5 font-mono text-[11px] text-[var(--ta-chat-text)]">
        {{ label }}
      </span>
      <span :class="['rounded-full border px-1.5 py-0.5 text-[10px]', statusToneClass(normalizedStatus)]">
        {{ statusLabel(normalizedStatus, statusKind as ProcessStatusKind) }}
      </span>
      <span v-if="path">路径: {{ path }}</span>
      <span v-if="startedAt">开始: {{ formatTime(startedAt) }}</span>
      <span v-if="endedAt">结束: {{ formatTime(endedAt) }}</span>
    </div>
    <div v-if="purpose || metaPurpose" class="whitespace-pre-wrap">{{ purpose ?? metaPurpose }}</div>
    <pre
      v-if="hasInput"
      class="max-h-28 overflow-auto rounded border border-[var(--ta-chat-border)] bg-[var(--ta-chat-detail-bg)] p-2 text-[11px]"
    >{{ JSON.stringify(input, null, 2) }}</pre>
    <pre
      v-if="outputDisplay"
      class="max-h-36 overflow-auto whitespace-pre-wrap rounded border border-[var(--ta-chat-border)] bg-[var(--ta-chat-detail-bg)] p-2 text-[11px]"
    >{{ outputDisplay }}</pre>
  </div>
</template>
