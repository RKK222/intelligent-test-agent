<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type PatchBlockProps = {
  part: Extract<MessagePart, { type: "patch" }>;
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import { FileDiff } from "lucide-vue-next";
import ProcessDisclosure from "./ProcessDisclosure.vue";
import { PART_META } from "./part-meta";
import { normalizeProcessStatus } from "./process-status";

const props = defineProps<PatchBlockProps>();
const meta = PART_META.patch;
const status = computed(() => normalizeProcessStatus("completed"));
const summary = computed(() => `${part.files.length} 个文件`);
const part = props.part;
</script>

<template>
  <ProcessDisclosure
    :id="part.partId"
    :test-id="`patch-part-${part.partId}`"
    :title="meta.label"
    :status="status"
    status-kind="task"
    accent="ok"
    :summary="summary"
    :default-open="false"
  >
    <div class="space-y-1.5 text-[12px] leading-5 text-[var(--ta-chat-muted)]">
      <div class="flex items-center gap-2">
        <FileDiff class="h-3.5 w-3.5 text-[var(--ta-chat-subtle)]" />
        <span
          class="rounded border border-[var(--ta-chat-border-strong)] bg-[var(--ta-chat-chip-bg)] px-1.5 py-0.5 font-mono text-[11px] text-[var(--ta-chat-text)]"
        >
          {{ part.hash.slice(0, 8) }}
        </span>
      </div>
      <ul class="space-y-0.5">
        <li v-for="file in part.files" :key="file" class="truncate font-mono text-[11px] text-[var(--ta-chat-text)]">
          {{ file }}
        </li>
      </ul>
    </div>
  </ProcessDisclosure>
</template>
