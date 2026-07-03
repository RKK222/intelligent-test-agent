<script setup lang="ts">
import type { MessagePart } from "@test-agent/shared-types";
import GenericToolView from "./GenericToolView.vue";

defineProps<{ part: Extract<MessagePart, { type: "tool" }> }>();
</script>

<template>
  <GenericToolView :part="part" />
</template>
