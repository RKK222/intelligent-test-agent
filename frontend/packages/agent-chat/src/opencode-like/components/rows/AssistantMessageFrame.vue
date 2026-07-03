<script lang="ts">
import type { AgentMessage } from "@test-agent/shared-types";

export type AssistantMessageFrameProps = {
  message: Extract<AgentMessage, { role: "assistant" }>;
  continuation?: boolean;
  showHeader?: boolean;
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import { Bot } from "lucide-vue-next";

const props = withDefaults(defineProps<AssistantMessageFrameProps>(), {
  continuation: false,
  showHeader: true
});

const timeText = computed(() => {
  try {
    return new Date(props.message.createdAt).toLocaleTimeString("zh-CN", {
      hour: "2-digit",
      minute: "2-digit"
    });
  } catch {
    return "";
  }
});
</script>

<template>
  <div :class="['oc-assistant-frame', continuation ? 'is-continuation' : '', showHeader ? 'has-header' : 'is-headerless']">
    <div v-if="showHeader" class="oc-assistant-frame__avatar" aria-hidden="true">
      <Bot class="oc-assistant-frame__avatar-icon" />
    </div>
    <div class="oc-assistant-frame__content">
      <div v-if="showHeader" class="oc-assistant-frame__meta">
        <span>测试智能体</span>
        <span v-if="timeText">·</span>
        <span v-if="timeText">{{ timeText }}</span>
      </div>
      <slot />
    </div>
  </div>
</template>
