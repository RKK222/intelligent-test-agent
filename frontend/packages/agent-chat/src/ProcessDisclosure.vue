<script lang="ts">
import type { ProcessStatusKind } from "./process-status";

export type ProcessDisclosureProps = {
  id: string;
  title: string;
  status: unknown;
  statusKind: ProcessStatusKind;
  summary?: string;
  defaultOpen?: boolean;
  testId?: string;
};
</script>

<script setup lang="ts">
import { computed, ref, useSlots, watch } from "vue";
import { ChevronDown, ChevronRight } from "lucide-vue-next";
import { normalizeProcessStatus, statusLabel, statusToneClass } from "./process-status";

const props = withDefaults(defineProps<ProcessDisclosureProps>(), { defaultOpen: false });
const slots = useSlots();

const open = ref(props.defaultOpen);
const normalizedStatus = computed(() => normalizeProcessStatus(props.status));

// id 变化时重置为默认展开态（切换消息/卡片）
watch(
  () => props.id,
  () => {
    open.value = props.defaultOpen;
  }
);

const hasBody = computed(() => Boolean(slots.default));
</script>

<template>
  <details
    :data-testid="testId"
    :open="open"
    class="overflow-hidden rounded-md border border-[var(--ta-chat-border)] bg-[var(--ta-chat-process-bg)]"
  >
    <summary
      class="flex list-none items-center gap-2 px-3 py-2 text-[11px] text-[var(--ta-chat-subtle)]"
      @click.prevent
    >
      <span
        :class="[
          'h-1.5 w-1.5 rounded-full',
          normalizedStatus === 'running' ? 'animate-pulse bg-[var(--ta-chat-status-running)]' : 'bg-[var(--ta-chat-border-strong)]'
        ]"
      />
      <div class="min-w-0 flex-1">
        <div class="flex min-w-0 items-center gap-2">
          <span class="truncate font-semibold text-[var(--ta-chat-text)]">{{ title }}</span>
          <span :class="['shrink-0 rounded-full border px-1.5 py-0.5 text-[10px]', statusToneClass(normalizedStatus)]">
            {{ statusLabel(normalizedStatus, statusKind) }}
          </span>
        </div>
        <div v-if="slots.summary || summary" class="mt-0.5 truncate text-[11px] text-[var(--ta-chat-muted)]">
          <slot name="summary">{{ summary }}</slot>
        </div>
      </div>
      <button
        v-if="hasBody"
        type="button"
        :aria-label="`${open ? '收起' : '展开'}${title}`"
        class="inline-flex h-6 shrink-0 items-center gap-1 rounded px-1.5 text-[11px] text-[var(--ta-chat-muted)] hover:bg-[var(--ta-chat-hover)] hover:text-[var(--ta-chat-text)]"
        @click.stop="open = !open"
      >
        <ChevronDown v-if="open" class="h-3.5 w-3.5" />
        <ChevronRight v-else class="h-3.5 w-3.5" />
      </button>
    </summary>
    <div v-if="open && hasBody" class="border-t border-[var(--ta-chat-border)] px-3 py-2">
      <slot />
    </div>
  </details>
</template>

