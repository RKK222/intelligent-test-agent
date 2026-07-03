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

const contextParts = computed(() => {
  const row = props.row;
  if (row.type !== "context-tool-group") {
    return [];
  }
  return row.refs
    .map((ref) => props.state.partsByMessageId[ref.messageId]?.find((part) => part.partId === ref.partId))
    .filter((part): part is Extract<MessagePart, { type: "tool" }> => part?.type === "tool");
});
</script>

<template>
  <div v-if="row.type === 'turn-gap'" class="oc-row oc-turn-gap" aria-hidden="true" />
  <UserMessageRow
    v-else-if="row.type === 'user-message' && userMessage?.role === 'user'"
    class="oc-row"
    :message="userMessage"
  />
  <ContextToolGroup
    v-else-if="row.type === 'context-tool-group'"
    class="oc-row"
    :parts="contextParts"
    :busy="row.busy"
  />
  <AssistantPartRow
    v-else-if="row.type === 'assistant-part' && assistantPart"
    class="oc-row"
    :part="assistantPart"
    :streaming-text-by-part-id="state.streamingTextByPartId"
    :previous-assistant-part="row.previousAssistantPart"
  />
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
