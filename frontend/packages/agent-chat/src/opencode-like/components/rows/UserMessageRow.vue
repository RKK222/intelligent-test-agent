<script lang="ts">
import type { AgentMessage } from "@test-agent/shared-types";

export type UserMessageRowProps = {
  message: Extract<AgentMessage, { role: "user" }>;
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import { FileText, Scissors, User } from "lucide-vue-next";
import {
  displayTextFromUserPrompt,
  workspaceContextAttachmentsFromPromptParts,
  workspaceContextAttachmentsFromUserPrompt
} from "../../../user-message-display";
import OcCopyButton from "../primitives/OcCopyButton.vue";

const props = defineProps<UserMessageRowProps>();
const displayText = computed(() => displayTextFromUserPrompt(props.message.text));
const workspaceContexts = computed(() => {
  const partContexts = workspaceContextAttachmentsFromPromptParts(props.message.parts);
  return partContexts.length ? partContexts : workspaceContextAttachmentsFromUserPrompt(props.message.text);
});
</script>

<template>
  <div
    class="oc-user-message"
    data-testid="oc-user-message"
    data-oc-turn-row="true"
    :data-oc-turn-id="message.messageId ?? message.id"
  >
    <div class="oc-user-message__content">
      <div class="oc-user-message__bubble">
        <div class="oc-user-message__copy">
          <OcCopyButton :value="message.text" />
        </div>
        <p>{{ displayText }}</p>
      </div>
      <div v-if="workspaceContexts.length" class="oc-user-message__contexts" aria-label="本轮关联的工作区上下文">
        <span
          v-for="context in workspaceContexts"
          :key="`${context.type}:${context.path}:${context.lines ?? ''}`"
          class="oc-user-message__context-chip"
          :title="context.path"
        >
          <component :is="context.type === 'selection' ? Scissors : FileText" class="oc-user-message__context-icon" />
          <span class="oc-user-message__context-type">{{ context.type === 'selection' ? '选区' : '文件' }}</span>
          <span class="oc-user-message__context-name">{{ context.fileName }}</span>
          <span v-if="context.lines" class="oc-user-message__context-lines">L{{ context.lines }}</span>
        </span>
      </div>
    </div>
    <div class="oc-user-message__avatar" aria-hidden="true">
      <User class="oc-user-message__avatar-icon" />
    </div>
  </div>
</template>
