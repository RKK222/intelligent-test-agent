<script lang="ts">
import type { EditorTab } from "@test-agent/workbench-shell";

export type EditorPaneProps = {
  tabs: EditorTab[];
  activePath?: string;
};
</script>

<script setup lang="ts">
defineProps<EditorPaneProps>();
const emit = defineEmits<{ activate: [path: string]; close: [path: string] }>();
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <div class="flex h-9 items-end gap-1 overflow-x-auto border-b border-[var(--ta-border)] bg-[#0d1628] px-2">
      <button
        v-for="tab in tabs"
        :key="tab.path"
        type="button"
        :class="[
          'flex h-8 max-w-[240px] items-center gap-2 rounded-t-[6px] border border-b-0 px-2 font-mono text-[12px]',
          activePath === tab.path
            ? 'border-[var(--ta-border)] bg-[#17244a] text-[var(--ta-text)]'
            : 'border-[var(--ta-border)] bg-[#101b33] text-[var(--ta-muted)]'
        ]"
        @click="emit('activate', tab.path)"
      >
        <span class="truncate">{{ tab.title }}</span>
        <span v-if="tab.content !== tab.savedContent" class="h-1.5 w-1.5 rounded-full bg-[#fbbf24]" />
        <span
          role="button"
          tabindex="0"
          class="rounded px-1 text-[var(--ta-muted)] hover:bg-[rgba(239,68,68,.15)] hover:text-[#fca5a5]"
          @click.stop="emit('close', tab.path)"
        >x</span>
      </button>
    </div>
    <div class="min-h-0 flex-1"><slot /></div>
  </div>
</template>
