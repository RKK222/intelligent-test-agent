<script lang="ts">
export type RetryRowProps = {
  attempt?: number;
  message?: string;
  action?: {
    title?: string;
    message?: string;
    label?: string;
    link?: string;
  };
};
</script>

<script setup lang="ts">
defineProps<RetryRowProps>();
</script>

<template>
  <div class="oc-retry-row">
    <span>正在等待重试<span v-if="attempt"> #{{ attempt }}</span></span>
    <span v-if="message" class="oc-retry-row__message">{{ message }}</span>
    <span v-if="action?.message" class="oc-retry-row__message">{{ action.message }}</span>
    <a
      v-if="action?.link"
      class="oc-retry-row__link"
      :href="action.link"
      target="_blank"
      rel="noreferrer"
    >
      {{ action.label ?? action.title ?? "查看处理方式" }}
    </a>
  </div>
</template>
