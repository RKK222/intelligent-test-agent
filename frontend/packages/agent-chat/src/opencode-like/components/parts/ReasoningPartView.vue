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
import { readPartText } from "../../state/part-text";

const props = defineProps<ReasoningPartViewProps>();
const source = computed(() => readPartText(props.part, props.streamingTextByPartId));
</script>

<template>
  <OcDisclosure
    class="oc-reasoning-part"
    title="思考状态"
    :subtitle="part.status"
    :default-open="part.status === 'running'"
  >
    <div class="oc-reasoning-part__body">
      <MarkdownView :source="source" body-class="oc-markdown" />
    </div>
  </OcDisclosure>
</template>
