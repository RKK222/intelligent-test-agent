<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type FilePartViewProps = {
  part: Extract<MessagePart, { type: "file" }>;
};
</script>

<script setup lang="ts">
import OcFilePath from "../primitives/OcFilePath.vue";

defineProps<FilePartViewProps>();
</script>

<template>
  <div class="oc-file-part">
    <OcFilePath :path="part.path ?? part.name ?? 'file'" />
  </div>
</template>
