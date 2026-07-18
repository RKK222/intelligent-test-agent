<script setup lang="ts">
defineOptions({ inheritAttrs: false });

import type { StateNoteData } from "./adapter";

const props = defineProps<{ data: StateNoteData }>();
const emit = defineEmits<{ select: [noteId: string] }>();
</script>

<template>
  <button
    type="button"
    class="ta-state-note"
    :aria-label="`Note ${props.data.note.text}`"
    @click.stop="emit('select', props.data.note.id)"
  >
    <span>{{ props.data.note.text }}</span>
  </button>
</template>

<style scoped>
.ta-state-note { position: relative; box-sizing: border-box; width: 150px; min-height: 64px; border: 1px solid #d4a72c; border-radius: 2px; padding: 10px 12px; background: #fff8c5; color: #594a18; font: inherit; font-size: 11px; line-height: 1.45; text-align: left; white-space: pre-wrap; cursor: pointer; box-shadow: 0 2px 5px rgba(15, 23, 42, .1); }
.ta-state-note::after { position: absolute; top: -1px; right: -1px; width: 14px; height: 14px; border-bottom: 1px solid #d4a72c; border-left: 1px solid #d4a72c; background: #f8e59a; content: ""; clip-path: polygon(0 0, 100% 100%, 0 100%); }
</style>
