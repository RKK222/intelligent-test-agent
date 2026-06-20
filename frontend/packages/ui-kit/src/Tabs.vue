<script lang="ts">
export type TabItem<T extends string> = {
  id: T;
  label: string;
  count?: number;
};
</script>

<script setup lang="ts" generic="T extends string">
import { cn } from "./lib";

defineProps<{ items: TabItem<T>[] }>();
const value = defineModel<T>({ required: true });
</script>

<template>
  <div :class="cn('flex gap-1 border-b border-[var(--ta-border)] bg-[#0d1628] px-2 py-1')">
    <button
      v-for="item in items"
      :key="item.id"
      type="button"
      :class="cn(
        'rounded-md px-2 py-1 text-[12px] text-[var(--ta-muted)] transition hover:bg-[#122044] hover:text-[var(--ta-text)]',
        value === item.id && 'bg-[#17244a] text-[var(--ta-text)] shadow-[inset_0_-2px_0_var(--ta-accent)]'
      )"
      @click="value = item.id"
    >
      {{ item.label }}
      <span v-if="item.count !== undefined" class="ml-1 text-slate-500">{{ item.count }}</span>
    </button>
  </div>
</template>
