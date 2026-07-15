<script lang="ts">
import type { Component } from "vue";
import type { MessagePart } from "@test-agent/shared-types";
import type { OpencodeLikeConversationState, TimelineRow, WorkStatusEventGroup } from "../../state/types";

export type WorkStatusRowProps = {
  row: Extract<TimelineRow, { type: "work-status" }>;
  state: OpencodeLikeConversationState;
  openEventKey?: string;
};
</script>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import {
  Diff,
  FilePlus,
  Globe,
  ListTodo,
  Pencil,
  Search,
  Sparkles,
  SquareTerminal,
  Wrench
} from "lucide-vue-next";
import { ShimmerDivider } from "@test-agent/ui-kit";
import ReasoningPartGroup from "../parts/ReasoningPartGroup.vue";
import ToolPartView from "../parts/ToolPartView.vue";
import TodoPanel from "../TodoPanel.vue";

const props = defineProps<WorkStatusRowProps>();
const emit = defineEmits<{
  toggleEvent: [eventKey: string];
  closeEvent: [];
}>();

const rootRef = ref<HTMLElement | null>(null);
const placement = ref<"above" | "below">("below");

const reasoningParts = computed(() =>
  props.row.reasoningRefs
    .map((ref) => props.state.partsByMessageId[ref.messageId]?.find((part) => part.partId === ref.partId))
    .filter((part): part is Extract<MessagePart, { type: "reasoning" }> => part?.type === "reasoning")
);

const selectedEvent = computed(() => props.row.events.find((event) => event.key === props.openEventKey));
const selectedToolParts = computed(() => toolPartsForEvent(selectedEvent.value));
const popoverId = computed(() => `oc-work-status-popover-${props.row.key.replace(/[^a-zA-Z0-9_-]/g, "-")}`);
const animatedDivider = computed(() => props.row.isLatest && (props.row.status === "running" || props.row.status === "retry"));
// 任何新用户轮次都会改变该值，让所有历史 reasoning 展开态一起回到收起状态。
const detailResetKey = computed(() => {
  const latestUserMessage = props.state.userMessages.at(-1);
  return latestUserMessage?.messageId ?? latestUserMessage?.id ?? "__empty__";
});

const eventIcons: Record<string, Component> = {
  explore: Search,
  skill: Sparkles,
  shell: SquareTerminal,
  edit: Pencil,
  write: FilePlus,
  patch: Diff,
  web: Globe,
  todo: ListTodo
};

function iconFor(event: WorkStatusEventGroup): Component {
  return eventIcons[event.key] ?? Wrench;
}

function toolPartsForEvent(event: WorkStatusEventGroup | undefined): Array<Extract<MessagePart, { type: "tool" }>> {
  if (!event) return [];
  return event.refs
    .map((ref) => props.state.partsByMessageId[ref.messageId]?.find((part) => part.partId === ref.partId))
    .filter((part): part is Extract<MessagePart, { type: "tool" }> => part?.type === "tool");
}

function eventAriaLabel(event: WorkStatusEventGroup): string {
  return `${event.label}，${event.refs.length} 次`;
}

function updatePlacement(): void {
  const root = rootRef.value;
  if (!root || !selectedEvent.value) return;
  const rect = root.getBoundingClientRect();
  const viewport = root.closest(".figma-chat-scroll, .ta-thread-viewport");
  const viewportRect = viewport?.getBoundingClientRect();
  const top = viewportRect?.top ?? 0;
  const bottom = viewportRect?.bottom ?? window.innerHeight;
  const availableAbove = Math.max(0, rect.top - top);
  const availableBelow = Math.max(0, bottom - rect.bottom);
  placement.value = availableBelow >= Math.min(360, window.innerHeight * 0.5) || availableBelow >= availableAbove
    ? "below"
    : "above";
}

function onDocumentPointerDown(event: PointerEvent): void {
  if (selectedEvent.value && !rootRef.value?.contains(event.target as Node)) {
    emit("closeEvent");
  }
}

function onDocumentKeyDown(event: KeyboardEvent): void {
  if (event.key === "Escape" && selectedEvent.value) {
    emit("closeEvent");
  }
}

watch(
  selectedEvent,
  (event) => {
    if (event) {
      nextTick(updatePlacement);
      document.addEventListener("pointerdown", onDocumentPointerDown);
      document.addEventListener("keydown", onDocumentKeyDown);
      return;
    }
    document.removeEventListener("pointerdown", onDocumentPointerDown);
    document.removeEventListener("keydown", onDocumentKeyDown);
  },
  { immediate: true }
);

onBeforeUnmount(() => {
  document.removeEventListener("pointerdown", onDocumentPointerDown);
  document.removeEventListener("keydown", onDocumentKeyDown);
});
</script>

<template>
  <section ref="rootRef" class="oc-work-status" :data-latest="row.isLatest" :data-status="row.status">
    <div class="oc-work-status__divider" aria-hidden="true">
      <ShimmerDivider orientation="vertical" :height="2" :animated="animatedDivider" :fade="true" />
    </div>

    <div class="oc-work-status__line oc-work-status__reasoning-line">
      <ReasoningPartGroup
        :key="`${row.key}:${detailResetKey}`"
        :parts="reasoningParts"
        :streaming-text-by-part-id="state.streamingTextByPartId"
        :status-override="row.status"
        :show-empty-status="true"
      />
    </div>

    <div class="oc-work-status__line oc-work-status__event-line" aria-label="工作事件">
      <div class="oc-work-status__event-strip">
        <button
          v-for="event in row.events"
          :key="event.key"
          type="button"
          class="oc-work-status__event-button"
          :class="{ 'is-active': openEventKey === event.key }"
          :data-testid="`oc-work-status-event-${event.key}`"
          :title="eventAriaLabel(event)"
          :aria-label="eventAriaLabel(event)"
          :aria-expanded="openEventKey === event.key"
          :aria-controls="openEventKey === event.key ? popoverId : undefined"
          @click="emit('toggleEvent', event.key)"
        >
          <component :is="iconFor(event)" class="oc-work-status__event-icon" aria-hidden="true" />
          <span v-if="event.refs.length > 1" class="oc-work-status__event-count">{{ event.refs.length }}</span>
        </button>
      </div>
    </div>

    <div v-if="row.todos.length" class="oc-work-status__line oc-work-status__todo-line">
      <TodoPanel :todos="row.todos" embedded />
    </div>

    <div
      v-if="selectedEvent"
      :id="popoverId"
      data-testid="oc-work-status-popover"
      role="dialog"
      :aria-label="`${selectedEvent.label}详情`"
      :class="['oc-work-status__popover', `is-${placement}`]"
    >
      <div class="oc-work-status__popover-title">{{ selectedEvent.label }}详情</div>
      <div class="oc-work-status__popover-body">
        <ToolPartView v-for="part in selectedToolParts" :key="part.partId" :part="part" :nested="true" />
      </div>
    </div>
  </section>
</template>
