<script lang="ts">
import type { AgentMessage } from "@test-agent/shared-types";

export type AssistantMessageFrameProps = {
  message: Extract<AgentMessage, { role: "assistant" }>;
  continuation?: boolean;
  showHeader?: boolean;
};
</script>

<script setup lang="ts">
import { Bot } from "lucide-vue-next";

withDefaults(defineProps<AssistantMessageFrameProps>(), {
  continuation: false,
  showHeader: true
});
</script>

<template>
  <div :class="['oc-assistant-frame', continuation ? 'is-continuation' : '', showHeader ? 'has-header' : 'is-headerless']">
    <div v-if="showHeader" class="oc-assistant-frame__avatar" aria-hidden="true">
      <Bot class="oc-assistant-frame__avatar-icon" />
    </div>
    <div class="oc-assistant-frame__content">
      <slot />
    </div>
  </div>
</template>
