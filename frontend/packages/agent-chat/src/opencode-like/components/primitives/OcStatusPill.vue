<script lang="ts">
export type OcStatusPillProps = {
  label: string;
};
</script>

<script setup lang="ts">
defineProps<OcStatusPillProps>();
</script>

<template>
  <span class="oc-status-pill">{{ label }}</span>
</template>
