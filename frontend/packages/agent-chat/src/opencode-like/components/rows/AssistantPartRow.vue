<script lang="ts">
import type { MessagePart, PermissionRequest, SubagentSession } from "@test-agent/shared-types";

export type AssistantPartRowProps = {
  part: MessagePart;
  streamingTextByPartId?: Record<string, string>;
  previousAssistantPart?: boolean;
  subagentsBySessionId?: Record<string, SubagentSession>;
  subagentByTaskPartId?: Record<string, string>;
  permissions?: PermissionRequest[];
  forceToolDetailsOpen?: boolean;
  showReasoning?: boolean;
};
</script>

<script setup lang="ts">
import TextPartView from "../parts/TextPartView.vue";
import ReasoningPartView from "../parts/ReasoningPartView.vue";
import ToolPartView from "../parts/ToolPartView.vue";
import FilePartView from "../parts/FilePartView.vue";
import UnknownPartView from "../parts/UnknownPartView.vue";
import CompactionMarker from "../../../CompactionMarker.vue";

withDefaults(defineProps<AssistantPartRowProps>(), {
  forceToolDetailsOpen: false,
  showReasoning: true
});
const emit = defineEmits<{ selectSubagent: [sessionId: string] }>();
</script>

<template>
  <div :class="['oc-assistant-part', previousAssistantPart ? 'is-continuation' : '']">
    <TextPartView
      v-if="part.type === 'text'"
      :part="part"
      :streaming-text-by-part-id="streamingTextByPartId"
    />
    <ReasoningPartView
      v-else-if="part.type === 'reasoning' && showReasoning"
      :part="part"
      :streaming-text-by-part-id="streamingTextByPartId"
    />
    <ToolPartView
      v-else-if="part.type === 'tool'"
      :part="part"
      :subagents-by-session-id="subagentsBySessionId"
      :subagent-by-task-part-id="subagentByTaskPartId"
      :permissions="permissions"
      :force-open="forceToolDetailsOpen"
      @select-subagent="(sessionId) => emit('selectSubagent', sessionId)"
    />
    <FilePartView v-else-if="part.type === 'file'" :part="part" />
    <CompactionMarker v-else-if="part.type === 'compaction'" :part="part" />
    <UnknownPartView v-else-if="part.type !== 'reasoning'" :part="part" />
  </div>
</template>
