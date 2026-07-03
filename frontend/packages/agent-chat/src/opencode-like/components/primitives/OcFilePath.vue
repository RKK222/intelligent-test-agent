<script lang="ts">
export type OcFilePathProps = {
  path: string;
};
</script>

<script setup lang="ts">
import { FileText } from "lucide-vue-next";

defineProps<OcFilePathProps>();
</script>

<template>
  <span class="oc-file-path" :title="path">
    <FileText class="oc-file-path__icon" />
    <span>{{ path }}</span>
  </span>
</template>
