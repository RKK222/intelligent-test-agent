<script lang="ts">
import type { AgentMessage } from "@test-agent/shared-types";

export type UserMessageRowProps = {
  message: Extract<AgentMessage, { role: "user" }>;
};
</script>

<script setup lang="ts">
defineProps<UserMessageRowProps>();
</script>

<template>
  <div class="oc-user-message" data-testid="oc-user-message">
    <div class="oc-user-message__bubble">
      <p>{{ message.text }}</p>
    </div>
  </div>
</template>
