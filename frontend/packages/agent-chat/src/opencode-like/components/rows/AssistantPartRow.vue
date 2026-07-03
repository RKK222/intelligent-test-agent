<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type AssistantPartRowProps = {
  part: MessagePart;
  streamingTextByPartId?: Record<string, string>;
  previousAssistantPart?: boolean;
};
</script>

<script setup lang="ts">
import TextPartView from "../parts/TextPartView.vue";
import ReasoningPartView from "../parts/ReasoningPartView.vue";
import ToolPartView from "../parts/ToolPartView.vue";
import FilePartView from "../parts/FilePartView.vue";
import UnknownPartView from "../parts/UnknownPartView.vue";

defineProps<AssistantPartRowProps>();
</script>

<template>
  <div :class="['oc-assistant-part', previousAssistantPart ? 'is-continuation' : '']">
    <TextPartView
      v-if="part.type === 'text'"
      :part="part"
      :streaming-text-by-part-id="streamingTextByPartId"
    />
    <ReasoningPartView
      v-else-if="part.type === 'reasoning'"
      :part="part"
      :streaming-text-by-part-id="streamingTextByPartId"
    />
    <ToolPartView v-else-if="part.type === 'tool'" :part="part" />
    <FilePartView v-else-if="part.type === 'file'" :part="part" />
    <UnknownPartView v-else :part="part" />
  </div>
</template>
