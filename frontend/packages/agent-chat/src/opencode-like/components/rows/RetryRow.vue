<script lang="ts">
export type RetryRowProps = {
  attempt?: number;
};
</script>

<script setup lang="ts">
defineProps<RetryRowProps>();
</script>

<template>
  <div class="oc-retry-row">
    正在重试<span v-if="attempt"> #{{ attempt }}</span>
  </div>
</template>
