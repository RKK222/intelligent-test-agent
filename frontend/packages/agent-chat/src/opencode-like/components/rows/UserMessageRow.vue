<script lang="ts">
import type { AgentMessage } from "@test-agent/shared-types";

export type UserMessageRowProps = {
  message: Extract<AgentMessage, { role: "user" }>;
};
</script>

<script setup lang="ts">
import { User } from "lucide-vue-next";
defineProps<UserMessageRowProps>();
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
        <p>{{ message.text }}</p>
      </div>
    </div>
    <div class="oc-user-message__avatar" aria-hidden="true">
      <User class="oc-user-message__avatar-icon" />
    </div>
  </div>
</template>
