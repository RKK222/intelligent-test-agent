<script lang="ts">
export type SuggestionItem = { id: string; label: string; detail?: string };
export type SuggestionPanelProps = { title: string; items: SuggestionItem[] };
</script>

<script setup lang="ts">
const props = defineProps<SuggestionPanelProps>();
const emit = defineEmits<{ pick: [item: SuggestionItem] }>();
</script>

<template>
  <div v-if="items.length" class="mt-2 max-h-44 overflow-auto rounded-md border border-[var(--ta-chat-border)] bg-[var(--ta-chat-surface)] p-1">
    <div class="px-2 py-1 text-[11px] uppercase text-[var(--ta-chat-muted)]">{{ title }}</div>
    <button
      v-for="item in items"
      :key="item.id"
      type="button"
      class="flex w-full items-center gap-2 rounded px-2 py-1.5 text-left hover:bg-[var(--ta-chat-hover)]"
      @click="emit('pick', item)"
    >
      <span class="min-w-0 flex-1 truncate font-mono text-[12px] text-[var(--ta-chat-text)]">{{ item.label }}</span>
      <span v-if="item.detail" class="max-w-[45%] truncate text-[11px] text-[var(--ta-chat-muted)]">{{ item.detail }}</span>
    </button>
  </div>
</template>
