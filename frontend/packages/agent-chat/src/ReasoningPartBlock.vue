<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type ReasoningPartBlockProps = {
  part: Extract<MessagePart, { type: "reasoning" }>;
  openByDefault: boolean;
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import ProcessDisclosure from "./ProcessDisclosure.vue";
import { normalizeProcessStatus } from "./process-status";

const props = defineProps<ReasoningPartBlockProps>();
const status = computed(() => normalizeProcessStatus(props.part.status ?? "not_started"));
const summary = computed(() => props.part.title ?? (status.value === "running" ? "正在整理信息" : "思考状态"));
</script>

<template>
  <ProcessDisclosure
    :id="part.partId"
    :test-id="`reasoning-part-${part.partId}`"
    title="思考状态"
    :status="status"
    status-kind="thinking"
    :summary="summary"
    :default-open="openByDefault"
  >
    <div class="max-h-44 overflow-auto whitespace-pre-wrap pr-1 text-[12px] leading-6 text-[var(--ta-chat-muted)]">
      {{ part.text || "暂无详细思考内容" }}
    </div>
  </ProcessDisclosure>
</template>
