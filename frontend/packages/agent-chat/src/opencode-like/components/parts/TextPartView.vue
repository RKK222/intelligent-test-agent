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
import OcCopyButton from "../primitives/OcCopyButton.vue";
import { readPartText } from "../../state/part-text";

const props = defineProps<TextPartViewProps>();
const source = computed(() => readPartText(props.part, props.streamingTextByPartId));
const hasSource = computed(() => source.value.trim().length > 0);
const normalizedStatus = computed(() => (props.part.status ?? "completed").toLowerCase());
const isWorkingOutput = computed(() => normalizedStatus.value === "running" || normalizedStatus.value === "pending");
</script>

<template>
  <div :class="['oc-text-part', isWorkingOutput ? 'is-working-output' : 'is-final-output']">
    <div v-if="hasSource" class="oc-text-part__copy">
      <OcCopyButton :value="source" />
    </div>
    <MarkdownView v-if="hasSource" :source="source" body-class="oc-markdown oc-text-part__body" loading-text="准备输出…" />
    <div v-else class="oc-markdown oc-text-part__body">无内容</div>
  </div>
</template>
