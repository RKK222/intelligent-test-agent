<script lang="ts">
export type RetryRowProps = {
  attempt?: number;
  maxAttempts?: number;
  retryAfterSeconds?: number;
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
    <span>重试中 {{ retryAfterSeconds ?? 60 }} 秒后<span v-if="attempt"> - 第 {{ attempt }} 次<span v-if="maxAttempts"> / 共 {{ maxAttempts }} 次</span></span></span>
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
