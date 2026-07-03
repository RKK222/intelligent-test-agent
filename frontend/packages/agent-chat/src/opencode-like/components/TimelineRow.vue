<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";
import type { OpencodeLikeConversationState, TimelineRow } from "../state/types";

export type TimelineRowProps = {
  row: TimelineRow;
  state: OpencodeLikeConversationState;
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import UserMessageRow from "./rows/UserMessageRow.vue";
import AssistantPartRow from "./rows/AssistantPartRow.vue";
import ThinkingRow from "./rows/ThinkingRow.vue";
import RetryRow from "./rows/RetryRow.vue";
import DiffSummaryRow from "./rows/DiffSummaryRow.vue";
import ErrorRow from "./rows/ErrorRow.vue";
import AssistantMessageFrame from "./rows/AssistantMessageFrame.vue";
import ReasoningPartGroup from "./parts/ReasoningPartGroup.vue";
import ContextToolGroup from "./tools/ContextToolGroup.vue";

const props = defineProps<TimelineRowProps>();
const emit = defineEmits<{ openDiff: [] }>();

const userMessage = computed(() => props.state.messageById[props.row.type === "error" ? "" : props.row.userMessageId]);

const assistantPart = computed<MessagePart | undefined>(() => {
  const row = props.row;
  if (row.type !== "assistant-part") {
    return undefined;
  }
  return props.state.partsByMessageId[row.messageId]?.find((part) => part.partId === row.partId);
});

const assistantMessage = computed(() => {
  const row = props.row;
  if (row.type !== "assistant-part" && row.type !== "context-tool-group" && row.type !== "reasoning-group") {
    return undefined;
  }
  const message = props.state.messageById[row.messageId];
  return message?.role === "assistant" ? message : undefined;
});

const contextParts = computed(() => {
  const row = props.row;
  if (row.type !== "context-tool-group") {
    return [];
  }
  return row.refs
    .map((ref) => props.state.partsByMessageId[ref.messageId]?.find((part) => part.partId === ref.partId))
    .filter((part): part is Extract<MessagePart, { type: "tool" }> => part?.type === "tool");
});

const reasoningParts = computed(() => {
  const row = props.row;
  if (row.type !== "reasoning-group") {
    return [];
  }
  return row.refs
    .map((ref) => props.state.partsByMessageId[ref.messageId]?.find((part) => part.partId === ref.partId))
    .filter((part): part is Extract<MessagePart, { type: "reasoning" }> => part?.type === "reasoning");
});
</script>

<template>
  <div v-if="row.type === 'turn-gap'" class="oc-row oc-turn-gap" aria-hidden="true" />
  <UserMessageRow
    v-else-if="row.type === 'user-message' && userMessage?.role === 'user'"
    class="oc-row"
    :message="userMessage"
  />
  <AssistantMessageFrame
    v-else-if="row.type === 'context-tool-group' && assistantMessage"
    class="oc-row"
    :message="assistantMessage"
    :continuation="row.previousAssistantPart"
    :show-header="row.showAssistantHeader"
  >
    <ContextToolGroup :parts="contextParts" :busy="row.busy" />
  </AssistantMessageFrame>
  <AssistantMessageFrame
    v-else-if="row.type === 'assistant-part' && assistantPart && assistantMessage"
    class="oc-row"
    :message="assistantMessage"
    :continuation="row.previousAssistantPart"
    :show-header="row.showAssistantHeader"
  >
    <AssistantPartRow
      :part="assistantPart"
      :streaming-text-by-part-id="state.streamingTextByPartId"
      :previous-assistant-part="row.previousAssistantPart"
    />
  </AssistantMessageFrame>
  <AssistantMessageFrame
    v-else-if="row.type === 'reasoning-group' && assistantMessage"
    class="oc-row"
    :message="assistantMessage"
    :continuation="row.previousAssistantPart"
    :show-header="row.showAssistantHeader"
  >
    <ReasoningPartGroup
      :parts="reasoningParts"
      :busy="row.busy"
      :streaming-text-by-part-id="state.streamingTextByPartId"
    />
  </AssistantMessageFrame>
  <ThinkingRow v-else-if="row.type === 'thinking'" class="oc-row" />
  <RetryRow v-else-if="row.type === 'retry'" class="oc-row" :attempt="row.attempt" />
  <DiffSummaryRow
    v-else-if="row.type === 'diff-summary'"
    class="oc-row"
    :files="row.files"
    @open-diff="emit('openDiff')"
  />
  <ErrorRow v-else-if="row.type === 'error'" class="oc-row" :message="row.message" />
</template>
