<script setup lang="ts">
import { computed } from "vue";
import type { FileTreeEntry } from "@test-agent/shared-types";
import spriteUrl from "./assets/sprite.svg";
import { getMaterialFileIconName } from "./fileIcons";

const props = defineProps<{
  entry: Pick<FileTreeEntry, "name" | "path" | "type">;
}>();

const iconName = computed(() => getMaterialFileIconName(props.entry));
</script>

<template>
  <svg
    v-if="iconName"
    class="ta-file-tree-icon shrink-0"
    aria-hidden="true"
    viewBox="0 0 32 32"
  >
    <use :href="`${spriteUrl}#${iconName}`" />
  </svg>
</template>
