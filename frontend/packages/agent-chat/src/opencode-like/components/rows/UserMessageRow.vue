<script lang="ts">
import type { AgentMessage } from "@test-agent/shared-types";

export type UserMessageRowProps = {
  message: Extract<AgentMessage, { role: "user" }>;
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import { User } from "lucide-vue-next";
import { displayTextFromUserPrompt } from "../../../user-message-display";

const props = defineProps<UserMessageRowProps>();
const displayText = computed(() => displayTextFromUserPrompt(props.message.text));
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
        <p>{{ displayText }}</p>
      </div>
    </div>
    <div class="oc-user-message__avatar" aria-hidden="true">
      <User class="oc-user-message__avatar-icon" />
    </div>
  </div>
</template>
