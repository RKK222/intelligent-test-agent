<script lang="ts">
import type { TodoItem } from "@test-agent/shared-types";

export type TodoPanelProps = {
  todos: TodoItem[];
  embedded?: boolean;
};
</script>

<script setup lang="ts">
import { computed, ref } from "vue";
import { ChevronDown, ChevronRight, ListTodo } from "lucide-vue-next";

type TodoStatusKey = "pending" | "in_progress" | "completed" | "cancelled" | "unknown";

const props = defineProps<TodoPanelProps>();
const open = ref(false);

const statusLabels: Record<TodoStatusKey, string> = {
  pending: "待处理",
  in_progress: "进行中",
  completed: "已完成",
  cancelled: "已取消",
  unknown: "其他"
};

const priorityLabels: Record<string, string> = {
  high: "高优先级",
  medium: "中优先级",
  low: "低优先级"
};

const counts = computed(() => {
  const next = { pending: 0, inProgress: 0, completed: 0, cancelled: 0, unknown: 0 };
  for (const item of props.todos) {
    switch (normalizeStatus(item.status)) {
      case "pending":
        next.pending += 1;
        break;
      case "in_progress":
        next.inProgress += 1;
        break;
      case "completed":
        next.completed += 1;
        break;
      case "cancelled":
        next.cancelled += 1;
        break;
      default:
        next.unknown += 1;
        break;
    }
  }
  return next;
});

function normalizeStatus(status?: string): TodoStatusKey {
  const value = status?.toLowerCase();
  if (value === "pending" || value === "in_progress" || value === "completed" || value === "cancelled") {
    return value;
  }
  return "unknown";
}

function statusText(status?: string): string {
  const normalized = normalizeStatus(status);
  return normalized === "unknown" ? status || statusLabels.unknown : statusLabels[normalized];
}

function statusClass(status?: string): string {
  return `is-${normalizeStatus(status).replace("_", "-")}`;
}

function priorityText(priority?: string): string | undefined {
  if (!priority) {
    return undefined;
  }
  return priorityLabels[priority.toLowerCase()] ?? priority;
}

function todoTitle(item: TodoItem): string {
  return item.title ?? item.text;
}

function todoDetail(item: TodoItem): string | undefined {
  return item.description ?? item.summary ?? item.result ?? item.error;
}
</script>

<template>
  <section
    v-if="todos.length"
    class="oc-todo-panel"
    :class="{ 'is-embedded': embedded }"
    data-testid="oc-todo-panel"
  >
    <button
      type="button"
      class="oc-todo-panel__header"
      :aria-expanded="open"
      @click="open = !open"
    >
      <span class="oc-todo-panel__title">
        <ListTodo class="oc-todo-panel__icon" />
        <span>任务</span>
      </span>
      <span class="oc-todo-panel__summary">
        <span>待处理 {{ counts.pending }}</span>
        <span>进行中 {{ counts.inProgress }}</span>
        <span>已完成 {{ counts.completed }}</span>
        <span>已取消 {{ counts.cancelled }}</span>
        <span v-if="counts.unknown">其他 {{ counts.unknown }}</span>
        <span>共 {{ todos.length }}</span>
      </span>
      <ChevronDown v-if="open" class="oc-todo-panel__chevron" />
      <ChevronRight v-else class="oc-todo-panel__chevron" />
    </button>

    <ol v-if="open" class="oc-todo-panel__list">
      <li
        v-for="item in todos"
        :key="item.id"
        :class="['oc-todo-panel__item', statusClass(item.status)]"
      >
        <span class="oc-todo-panel__marker" aria-hidden="true" />
        <div class="oc-todo-panel__content">
          <div class="oc-todo-panel__item-main">
            <span class="oc-todo-panel__item-title">{{ todoTitle(item) }}</span>
            <span :class="['oc-todo-panel__status', statusClass(item.status)]">{{ statusText(item.status) }}</span>
            <span v-if="priorityText(item.priority)" class="oc-todo-panel__priority">{{ priorityText(item.priority) }}</span>
          </div>
          <p v-if="todoDetail(item)" class="oc-todo-panel__detail">
            {{ todoDetail(item) }}
          </p>
          <ol v-if="Array.isArray(item.steps) && item.steps.length" class="oc-todo-panel__steps">
            <li v-for="(step, index) in item.steps" :key="`${item.id}:${index}`">
              <span>{{ index + 1 }}.</span>
              <span>{{ step }}</span>
            </li>
          </ol>
        </div>
      </li>
    </ol>
  </section>
</template>
