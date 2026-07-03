<script lang="ts">
export type OcCodeBlockProps = {
  label?: string;
  code: string;
};
</script>

<script setup lang="ts">
import OcCopyButton from "./OcCopyButton.vue";

defineProps<OcCodeBlockProps>();
</script>

<template>
  <div class="oc-code-block">
    <div v-if="label" class="oc-code-block__header">
      <span>{{ label }}</span>
      <OcCopyButton :value="code" />
    </div>
    <pre class="oc-code-block__pre"><code>{{ code }}</code></pre>
  </div>
</template>
