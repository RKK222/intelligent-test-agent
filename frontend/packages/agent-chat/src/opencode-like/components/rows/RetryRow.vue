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
    <span class="oc-retry-row__indicator" aria-hidden="true" />
    <span class="oc-retry-row__glyph" aria-hidden="true" />
    <span class="oc-retry-row__body">
      <span class="oc-retry-row__title">{{ message || action?.title || "请求暂时不可用" }}</span>
      <span class="oc-retry-row__meta">
        重试中 {{ Math.max(0, retryAfterSeconds ?? 60) }} 秒后<span v-if="attempt"> - 第 {{ attempt }} 次</span><span v-if="maxAttempts"> / 共 {{ maxAttempts }} 次</span>
      </span>
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
    </span>
  </div>
</template>
