<script lang="ts">
export type WorkingStatusRowProps = {
  continuation?: boolean;
  showHeader?: boolean;
};
</script>

<script setup lang="ts">
import { Bot } from "lucide-vue-next";

withDefaults(defineProps<WorkingStatusRowProps>(), {
  continuation: true,
  showHeader: false
});
</script>

<template>
  <div :class="['oc-working-frame', 'oc-assistant-frame', continuation ? 'is-continuation' : '', showHeader ? 'has-header' : 'is-headerless']">
    <div v-if="showHeader" class="oc-assistant-frame__avatar" aria-hidden="true">
      <Bot class="oc-assistant-frame__avatar-icon" />
    </div>
    <div class="oc-assistant-frame__content">
      <div class="oc-working-status" role="status" aria-live="polite">
        <span class="oc-thinking-dot" aria-hidden="true" />
        <span class="oc-working-status__title">正在工作</span>
        <span class="oc-working-status__subtitle">等待后续输出</span>
      </div>
    </div>
  </div>
</template>
