<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type GenericToolViewProps = {
  part: Extract<MessagePart, { type: "tool" }>;
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import OcCodeBlock from "../primitives/OcCodeBlock.vue";
import OcToolShell from "../primitives/OcToolShell.vue";
import { getToolInfo } from "../../state/tool-registry";

const props = defineProps<GenericToolViewProps>();
const info = computed(() => getToolInfo(props.part));
const inputText = computed(() => stringify(props.part.input));
const outputText = computed(() => stringify(outputValue(props.part)));

function stringify(value: unknown): string | undefined {
  if (value === undefined || value === null) {
    return undefined;
  }
  if (typeof value === "string") {
    return value;
  }
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

function outputValue(part: Extract<MessagePart, { type: "tool" }>): unknown {
  const state = (part as unknown as { state?: { output?: unknown; error?: unknown } }).state;
  if (part.output !== undefined && part.output !== null && part.output !== "") {
    return part.output;
  }
  return state?.output || state?.error;
}
</script>

<template>
  <OcToolShell
    :title="info.title"
    :subtitle="info.subtitle"
    :subtitle-title="info.fullSubtitle"
    :status="part.status"
    :default-open="false"
  >
    <div class="oc-tool-detail">
      <OcCodeBlock v-if="inputText" label="input" :code="inputText" />
      <OcCodeBlock v-if="outputText" label="output" :code="outputText" />
    </div>
  </OcToolShell>
</template>
