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

function formatTime(val?: string | number) {
  if (!val) return "刚刚";
  try {
    const d = new Date(val);
    if (isNaN(d.getTime())) return "刚刚";
    return d.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" });
  } catch {
    return "刚刚";
  }
}
</script>

<template>
  <div :class="['oc-assistant-frame', continuation ? 'is-continuation' : '', showHeader ? 'has-header' : 'is-headerless']">
    <div v-if="showHeader" class="oc-assistant-frame__avatar" aria-hidden="true" style="display: none !important;">
      <Bot class="oc-assistant-frame__avatar-icon" />
    </div>
    <div v-if="showHeader" class="oc-assistant-who">
      <b>TestAgent</b>
      <span>{{ formatTime(message.createdAt || (message as any).timestamp) }}</span>
    </div>
    <div class="oc-assistant-frame__content">
      <slot />
    </div>
  </div>
</template>


