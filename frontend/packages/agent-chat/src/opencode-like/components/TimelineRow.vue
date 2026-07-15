<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";
import type { OpencodeLikeConversationState, TimelineRow } from "../state/types";

export type TimelineRowProps = {
  row: TimelineRow;
  state: OpencodeLikeConversationState;
  openWorkStatusEventKey?: string;
  historicalWorkStatusExpanded?: boolean;
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import { Activity, ChevronUp } from "lucide-vue-next";
import UserMessageRow from "./rows/UserMessageRow.vue";
import AssistantPartRow from "./rows/AssistantPartRow.vue";
import RetryRow from "./rows/RetryRow.vue";
import DiffSummaryRow from "./rows/DiffSummaryRow.vue";
import ErrorRow from "./rows/ErrorRow.vue";
import AssistantMessageFrame from "./rows/AssistantMessageFrame.vue";
import ReasoningPartGroup from "./parts/ReasoningPartGroup.vue";
import ContextToolGroup from "./tools/ContextToolGroup.vue";
import ToolPartGroup from "./tools/ToolPartGroup.vue";
import WorkStatusRow from "./rows/WorkStatusRow.vue";
import OcIconButton from "./primitives/OcIconButton.vue";

const props = defineProps<TimelineRowProps>();
const emit = defineEmits<{
  openDiff: [];
  openFile: [path: string];
  selectSubagent: [sessionId: string];
  toggleWorkStatusEvent: [eventKey: string];
  toggleHistoricalWorkStatus: [];
  closeWorkStatusEvent: [];
}>();

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
  if (
    row.type !== "assistant-part" &&
    row.type !== "context-tool-group" &&
    row.type !== "reasoning-group" &&
    row.type !== "tool-group"
  ) {
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

const toolGroupParts = computed(() => {
  const row = props.row;
  if (row.type !== "tool-group") {
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
    v-else-if="row.type === 'tool-group' && assistantMessage"
    class="oc-row"
    :message="assistantMessage"
    :continuation="row.previousAssistantPart"
    :show-header="row.showAssistantHeader"
  >
    <ToolPartGroup
      :parts="toolGroupParts"
      :busy="row.busy"
      :subagents-by-session-id="state.subagentsBySessionId"
      :subagent-by-task-part-id="state.subagentByTaskPartId"
      @select-subagent="(sessionId) => emit('selectSubagent', sessionId)"
    />
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
      :subagents-by-session-id="state.subagentsBySessionId"
      :subagent-by-task-part-id="state.subagentByTaskPartId"
      @select-subagent="(sessionId) => emit('selectSubagent', sessionId)"
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
  <div
    v-else-if="row.type === 'work-status' && !row.isLatest && !historicalWorkStatusExpanded"
    class="oc-row oc-work-status-history"
  >
    <OcIconButton
      class="oc-work-status-history-trigger"
      label="展开历史工作状态"
      @click="emit('toggleHistoricalWorkStatus')"
    >
      <Activity aria-hidden="true" />
    </OcIconButton>
  </div>
  <div v-else-if="row.type === 'work-status'" class="oc-row oc-work-status-container">
    <WorkStatusRow
      :row="row"
      :state="state"
      :open-event-key="openWorkStatusEventKey"
      @toggle-event="(eventKey) => emit('toggleWorkStatusEvent', eventKey)"
      @close-event="emit('closeWorkStatusEvent')"
    />
    <OcIconButton
      v-if="!row.isLatest"
      class="oc-work-status-history-collapse"
      label="收起历史工作状态"
      @click="emit('toggleHistoricalWorkStatus')"
    >
      <ChevronUp aria-hidden="true" />
    </OcIconButton>
  </div>
  <RetryRow
    v-else-if="row.type === 'retry'"
    class="oc-row"
    :attempt="row.attempt"
    :max-attempts="row.maxAttempts"
    :retry-after-seconds="row.retryAfterSeconds"
    :message="row.message"
    :action="row.action"
  />
  <DiffSummaryRow
    v-else-if="row.type === 'diff-summary'"
    class="oc-row"
    :files="row.files"
    @open-diff="emit('openDiff')"
    @open-file="(path) => emit('openFile', path)"
  />
  <ErrorRow v-else-if="row.type === 'error'" class="oc-row" :message="row.message" />
</template>
