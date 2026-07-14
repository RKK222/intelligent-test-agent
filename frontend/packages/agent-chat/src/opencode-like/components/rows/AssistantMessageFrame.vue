<script lang="ts">
import type { AgentMessage } from "@test-agent/shared-types";

export type AssistantMessageFrameProps = {
  message: Extract<AgentMessage, { role: "assistant" }>;
  continuation?: boolean;
  showHeader?: boolean;
};
</script>

<script setup lang="ts">
withDefaults(defineProps<AssistantMessageFrameProps>(), {
  continuation: false,
  showHeader: true
});
</script>

<template>
  <div :class="['oc-assistant-frame', continuation ? 'is-continuation' : '', showHeader ? 'has-header' : 'is-headerless']">
    <div class="oc-assistant-frame__content">
      <slot />
    </div>
  </div>
</template>
