<script lang="ts">
import type { EditorTab } from "@test-agent/workbench-shell";

export type EditorPaneProps = {
  tabs: EditorTab[];
  activePath?: string;
};
</script>

<script setup lang="ts">
import { X } from "lucide-vue-next";

defineProps<EditorPaneProps>();
const emit = defineEmits<{ activate: [path: string]; close: [path: string] }>();
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <div class="flex h-[38px] items-stretch overflow-x-auto border-b border-[var(--ta-border)] bg-[var(--ta-tabbar)]">
      <button
        v-for="tab in tabs"
        :key="tab.path"
        type="button"
        :class="[
          'flex h-full min-w-[100px] max-w-[180px] items-center gap-2 border-r border-[var(--ta-border)] px-4 text-[14px] leading-5',
          activePath === tab.path
            ? 'bg-[var(--ta-surface)] text-[var(--ta-ink)]'
            : 'bg-[var(--ta-tabbar)] text-[var(--ta-muted)] hover:bg-[var(--ta-hover)]'
        ]"
        @click="emit('activate', tab.path)"
      >
        <span class="truncate">{{ tab.title }}</span>
        <span v-if="tab.content !== tab.savedContent" class="h-1.5 w-1.5 rounded-full bg-[#b07a2b]" />
        <span
          role="button"
          tabindex="0"
          class="ml-auto rounded p-0.5 text-[var(--ta-muted)] hover:bg-[var(--ta-hover)] hover:text-[var(--ta-ink)]"
          @click.stop="emit('close', tab.path)"
        >
          <X class="h-3.5 w-3.5" />
        </span>
      </button>
    </div>
    <div class="min-h-0 flex-1"><slot /></div>
  </div>
</template>
