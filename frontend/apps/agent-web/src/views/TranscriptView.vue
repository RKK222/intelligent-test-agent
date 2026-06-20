<script setup lang="ts">
import ReadonlyTranscript from "../components/ReadonlyTranscript.vue";

defineProps<{ sessionId: string }>();
</script>

<template>
  <ReadonlyTranscript :session-id="sessionId" />
</template>
