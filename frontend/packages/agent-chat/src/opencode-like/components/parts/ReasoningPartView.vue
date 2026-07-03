<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type ReasoningPartViewProps = {
  part: Extract<MessagePart, { type: "reasoning" }>;
  streamingTextByPartId?: Record<string, string>;
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import MarkdownView from "../../../MarkdownView.vue";
import OcDisclosure from "../primitives/OcDisclosure.vue";
import { compactPartPreview, readPartText } from "../../state/part-text";

const props = defineProps<ReasoningPartViewProps>();
const source = computed(() => readPartText(props.part, props.streamingTextByPartId));

const subtitleText = computed(() => {
  const status = props.part.status;
  if (status === "completed" || status === "success") {
    return "已完成";
  }
  if (status === "running") {
    return "思考中";
  }
  if (status === "failed" || status === "error") {
    return "失败";
  }
  return status;
});

const detailText = computed(() => compactPartPreview(source.value));
</script>

<template>
  <OcDisclosure
    class="oc-reasoning-part"
    title="思考状态"
    :detail="detailText"
    :subtitle="subtitleText"
    :status="part.status"
    :default-open="false"
  >
    <div class="oc-reasoning-part__body">
      <MarkdownView :source="source" body-class="oc-markdown" />
    </div>
  </OcDisclosure>
</template>
