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
  if (!val) {
    const d = new Date();
    return d.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false });
  }
  try {
    let parsedVal: string | number = val;
    if (typeof val === "number" && val < 10000000000) {
      parsedVal = val * 1000;
    } else if (typeof val === "string" && !isNaN(Number(val))) {
      const num = Number(val);
      if (num < 10000000000) {
        parsedVal = num * 1000;
      } else {
        parsedVal = num;
      }
    }
    const d = new Date(parsedVal);
    if (isNaN(d.getTime())) {
      const fallback = new Date();
      return fallback.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false });
    }
    
    const now = new Date();
    const isToday = d.getFullYear() === now.getFullYear() &&
                    d.getMonth() === now.getMonth() &&
                    d.getDate() === now.getDate();
                    
    const timeStr = d.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false });
    if (isToday) {
      return timeStr;
    } else {
      const month = String(d.getMonth() + 1).padStart(2, '0');
      const day = String(d.getDate()).padStart(2, '0');
      return `${month}-${day} ${timeStr}`;
    }
  } catch {
    const fallback = new Date();
    return fallback.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false });
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


