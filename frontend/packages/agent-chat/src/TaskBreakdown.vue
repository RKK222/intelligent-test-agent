<script lang="ts">
import type { TodoItem } from "@test-agent/shared-types";

export type TaskBreakdownProps = { todos: TodoItem[] };
</script>

<script setup lang="ts">
import ProcessDisclosure from "./ProcessDisclosure.vue";
import { isActiveStatus, normalizeProcessStatus, todoTitle } from "./process-status";

defineProps<TaskBreakdownProps>();
</script>

<template>
  <section v-if="todos.length" class="rounded-md border border-[var(--ta-chat-border)] bg-[var(--ta-chat-surface)] p-2.5">
    <div class="mb-2 flex items-center justify-between gap-2">
      <div class="text-[12px] font-semibold text-[var(--ta-chat-text)]">任务分解</div>
      <div class="text-[11px] text-[var(--ta-chat-muted)]">{{ todos.length }} 项</div>
    </div>
    <div class="space-y-1.5">
      <ProcessDisclosure
        v-for="item in todos"
        :key="item.id"
        :id="item.id"
        :test-id="`task-item-${item.id}`"
        :title="todoTitle(item)"
        :status="item.status"
        status-kind="task"
        :summary="item.summary ?? item.description ?? item.result ?? item.error"
        :default-open="isActiveStatus(normalizeProcessStatus(item.status))"
      >
        <div class="space-y-2 text-[12px] leading-5 text-[var(--ta-chat-muted)]">
          <div v-if="item.description ?? item.summary ?? item.result ?? item.error" class="whitespace-pre-wrap">
            {{ item.description ?? item.summary ?? item.result ?? item.error }}
          </div>
          <ol v-if="Array.isArray(item.steps) && item.steps.length" class="space-y-1">
            <li v-for="(step, index) in item.steps" :key="`${item.id}-${index}`" class="flex gap-2">
              <span class="mt-0.5 text-[10px] text-[var(--ta-chat-subtle)]">{{ index + 1 }}</span>
              <span class="min-w-0 flex-1 whitespace-pre-wrap">{{ step }}</span>
            </li>
          </ol>
          <div v-if="item.error" class="text-[var(--ta-chat-status-error)]">{{ item.error }}</div>
        </div>
      </ProcessDisclosure>
    </div>
  </section>
</template>
