<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type UnknownPartViewProps = {
  part: MessagePart;
};
</script>

<script setup lang="ts">
defineProps<UnknownPartViewProps>();
</script>

<template>
  <div v-if="part.type === 'retry'" class="oc-retry-row">
    重试第 {{ part.attempt }} 次
    <span v-if="part.error.message">{{ part.error.message }}</span>
  </div>
  <pre v-else class="oc-unknown-part">{{ JSON.stringify(part, null, 2) }}</pre>
</template>
