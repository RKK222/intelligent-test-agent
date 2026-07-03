<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type TextPartViewProps = {
  part: Extract<MessagePart, { type: "text" }>;
  streamingTextByPartId?: Record<string, string>;
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import MarkdownView from "../../../MarkdownView.vue";
import OcDisclosure from "../primitives/OcDisclosure.vue";
import { readPartText } from "../../state/part-text";

const props = defineProps<TextPartViewProps>();
const source = computed(() => readPartText(props.part, props.streamingTextByPartId));
const normalizedStatus = computed(() => (props.part.status ?? "completed").toLowerCase());
const isWorkingOutput = computed(() => normalizedStatus.value === "running" || normalizedStatus.value === "pending");
const titleText = computed(() => (isWorkingOutput.value ? "工作中输出" : "最终输出"));
const subtitleText = computed(() => {
  if (isWorkingOutput.value) return "输出中";
  if (normalizedStatus.value === "failed" || normalizedStatus.value === "error") return "失败";
  return "已完成";
});
</script>

<template>
  <OcDisclosure
    :class="['oc-text-output', isWorkingOutput ? 'is-working-output' : 'is-final-output']"
    :title="titleText"
    :subtitle="subtitleText"
    :status="part.status"
    :default-open="!isWorkingOutput"
  >
    <div class="oc-text-part">
      <MarkdownView :source="source" body-class="oc-markdown oc-text-part__body" />
    </div>
  </OcDisclosure>
</template>
