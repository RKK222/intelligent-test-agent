<script lang="ts">
export type OcIconButtonProps = {
  label: string;
};
</script>

<script setup lang="ts">
defineProps<OcIconButtonProps>();
</script>

<template>
  <button type="button" class="oc-icon-button" :aria-label="label" :title="label">
    <slot />
  </button>
</template>
