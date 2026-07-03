<script lang="ts">
export type ErrorRowProps = {
  message: string;
};
</script>

<script setup lang="ts">
defineProps<ErrorRowProps>();
</script>

<template>
  <div class="oc-error-row" role="alert">{{ message }}</div>
</template>
