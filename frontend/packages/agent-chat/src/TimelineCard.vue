<script lang="ts">
export type TimelineCardProps = {
  id: string;
  title: string;
  defaultOpen: boolean;
};
</script>

<script setup lang="ts">
import { ref, watch } from "vue";
import { ChevronDown, ChevronRight } from "lucide-vue-next";

const props = defineProps<TimelineCardProps>();

const open = ref(props.defaultOpen);
watch(
  () => props.id,
  () => {
    open.value = props.defaultOpen;
  }
);
</script>

<template>
  <details
    :data-testid="`timeline-card-${id}`"
    :open="open"
    class="overflow-hidden rounded-md border border-[var(--ta-chat-border)] bg-[var(--ta-chat-surface)]"
  >
    <summary
      class="flex list-none items-center gap-2 border-b border-[var(--ta-chat-border)] bg-[var(--ta-chat-process-bg)] px-3 py-2"
      @click.prevent
    >
      <slot name="icon" />
      <div class="min-w-0 flex-1 truncate text-[12px] font-semibold text-[var(--ta-chat-text)]">{{ title }}</div>
      <button
        type="button"
        :aria-label="`${open ? '收起' : '展开'} ${title}`"
        class="inline-flex items-center gap-1 rounded px-2 py-1 text-[11px] font-medium text-[var(--ta-chat-muted)] hover:bg-[var(--ta-chat-hover)] hover:text-[var(--ta-chat-text)]"
        @click.stop="open = !open"
      >
        {{ open ? "收起" : "展开" }}
        <ChevronDown v-if="open" class="h-3.5 w-3.5" />
        <ChevronRight v-else class="h-3.5 w-3.5" />
      </button>
    </summary>
    <div v-if="open" class="p-3"><slot /></div>
  </details>
</template>
