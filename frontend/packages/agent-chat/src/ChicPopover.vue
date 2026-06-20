<script lang="ts">
export type ChicPopoverOption = { value: string; label: string; badge?: string };
export type ChicPopoverProps = {
  label: string;
  placeholder: string;
  value: string;
  options: ChicPopoverOption[];
  searchable?: boolean;
};
</script>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import { Check, ChevronDown, Search, X } from "lucide-vue-next";

const props = withDefaults(defineProps<ChicPopoverProps>(), { searchable: false });
const emit = defineEmits<{ change: [value: string] }>();

const open = ref(false);
const query = ref("");
const rootRef = ref<HTMLElement | null>(null);
const inputRef = ref<HTMLInputElement | null>(null);

const selectedLabel = computed(() => props.options.find((o) => o.value === props.value)?.label ?? props.placeholder);
const filtered = computed(() => {
  if (!props.searchable || !query.value.trim()) return props.options;
  const q = query.value.toLowerCase();
  return props.options.filter((o) => o.label.toLowerCase().includes(q));
});

function onDocMouseDown(e: MouseEvent) {
  if (!rootRef.value?.contains(e.target as Node)) {
    open.value = false;
  }
}
watch(open, (isOpen) => {
  if (isOpen) {
    document.addEventListener("mousedown", onDocMouseDown);
    if (props.searchable) {
      nextTick(() => inputRef.value?.focus());
    }
  } else {
    document.removeEventListener("mousedown", onDocMouseDown);
  }
});
onBeforeUnmount(() => document.removeEventListener("mousedown", onDocMouseDown));

function pick(option: ChicPopoverOption) {
  emit("change", option.value);
  open.value = false;
  query.value = "";
}
</script>

<template>
  <div ref="rootRef" class="ta-chic-popover">
    <button
      type="button"
      :aria-label="label"
      aria-haspopup="listbox"
      :aria-expanded="open"
      class="ta-chic-trigger"
      @click="open = !open"
    >
      <span class="ta-chic-trigger-icon"><slot name="icon" /></span>
      <span class="ta-chic-trigger-label">{{ selectedLabel }}</span>
      <ChevronDown class="ta-chic-trigger-chevron h-3.5 w-3.5" />
    </button>

    <div
      v-if="open"
      role="listbox"
      class="absolute bottom-full left-0 z-50 mb-2 w-[260px] overflow-hidden rounded-lg border border-[var(--ta-chat-border)] bg-[var(--ta-chat-surface)] shadow-xl"
    >
      <div v-if="searchable" class="flex items-center gap-1.5 border-b border-[var(--ta-chat-border)] px-2.5 py-2 text-[12px]">
        <Search class="h-3.5 w-3.5 text-[var(--ta-chat-muted)]" />
        <input
          ref="inputRef"
          v-model="query"
          :placeholder="`搜索${label}`"
          class="min-w-0 flex-1 bg-transparent text-[var(--ta-chat-text)] placeholder:text-[var(--ta-chat-muted)] outline-none"
        />
        <button v-if="query" type="button" aria-label="清空" class="text-[var(--ta-chat-muted)] hover:text-[var(--ta-chat-text)]" @click="query = ''">
          <X class="h-3.5 w-3.5" />
        </button>
      </div>
      <div class="max-h-56 overflow-auto py-1">
        <div v-if="filtered.length === 0" class="px-3 py-2 text-[12px] text-[var(--ta-chat-muted)]">无匹配项</div>
        <button
          v-for="o in filtered"
          v-else
          :key="o.value"
          type="button"
          role="option"
          :aria-selected="o.value === value"
          class="flex w-full items-center justify-between gap-2 px-2.5 py-1.5 text-left text-[12px] text-[var(--ta-chat-text)] hover:bg-[var(--ta-chat-hover)]"
          @click="pick(o)"
        >
          <span class="truncate">{{ o.label }}</span>
          <Check v-if="o.value === value" class="h-3.5 w-3.5 text-[var(--ta-chat-subtle)]" />
        </button>
      </div>
    </div>
  </div>
</template>
