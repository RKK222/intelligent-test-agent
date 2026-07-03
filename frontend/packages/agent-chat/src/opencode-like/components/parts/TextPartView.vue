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
import { readPartText } from "../../state/part-text";

const props = defineProps<TextPartViewProps>();
const source = computed(() => readPartText(props.part, props.streamingTextByPartId));
</script>

<template>
  <div class="oc-text-part">
    <MarkdownView :source="source" body-class="oc-markdown oc-text-part__body" />
  </div>
</template>
