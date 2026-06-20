<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type SnapshotBlockProps = {
  part: Extract<MessagePart, { type: "snapshot" }>;
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import ProcessDisclosure from "./ProcessDisclosure.vue";
import { PART_META } from "./part-meta";
import { normalizeProcessStatus } from "./process-status";

const props = defineProps<SnapshotBlockProps>();
const meta = PART_META.snapshot;
const status = computed(() => normalizeProcessStatus("completed"));
const summary = computed(() => `快照 ${props.part.snapshot.length} 字符`);
</script>

<template>
  <ProcessDisclosure
    :id="part.partId"
    :test-id="`snapshot-part-${part.partId}`"
    :title="meta.label"
    :status="status"
    status-kind="task"
    accent="muted"
    :summary="summary"
    :default-open="false"
  >
    <pre class="max-h-44 overflow-auto whitespace-pre-wrap rounded border border-[var(--ta-chat-border)] bg-[var(--ta-chat-detail-bg)] p-2 text-[11px] text-[var(--ta-chat-muted)]">{{ part.snapshot }}</pre>
  </ProcessDisclosure>
</template>
