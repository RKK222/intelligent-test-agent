<script lang="ts">
import type { OpencodeLikeConversationState } from "../state/types";

export type OpencodeTimelineProps = {
  state: OpencodeLikeConversationState;
  workStatusDockTarget?: string | HTMLElement | null;
};
</script>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import TimelineRow from "./TimelineRow.vue";
import ConversationLocator from "./ConversationLocator.vue";
import { createTimelineRows } from "../state/projection";

const props = defineProps<OpencodeTimelineProps>();
const emit = defineEmits<{ openDiff: []; openFile: [path: string]; selectSubagent: [sessionId: string] }>();
defineSlots<{
  "completed-status-actions"?: () => unknown;
}>();
const rows = computed(() => createTimelineRows(props.state));
const resolvedDockTarget = computed(() => {
  const target = props.workStatusDockTarget;
  if (!target || typeof document === "undefined") return null;
  return typeof target === "string" ? document.querySelector(target) : target;
});
// 运行过程固定在输入区便于持续观察；成功完成后状态回到该轮最后输出下方，Diff 仍留在输入区。
const dockRows = computed(() => resolvedDockTarget.value
  ? rows.value.filter((row) => row.type === "diff-summary"
    || (row.type === "work-status" && row.isLatest && row.status !== "completed"))
  : []);
const inlineRows = computed(() => resolvedDockTarget.value
  ? rows.value.filter((row) => !dockRows.value.includes(row))
  : rows.value);
const openWorkStatusDetail = ref<{ rowKey: string; eventKey: string } | null>(null);
const expandedHistoricalStatusKey = ref<string | null>(null);
const expandedCompletedStatusKey = ref<string | null>(null);
const latestUserMessageKey = computed(() => {
  const message = props.state.userMessages.at(-1);
  return message?.messageId ?? message?.id ?? "__empty__";
});
const latestCompletedStatusKey = computed(() => rows.value.find(
  (row) => row.type === "work-status" && row.isLatest && row.status === "completed"
)?.key ?? null);

function toggleWorkStatusDetail(rowKey: string, eventKey: string): void {
  const current = openWorkStatusDetail.value;
  openWorkStatusDetail.value = current?.rowKey === rowKey && current.eventKey === eventKey
    ? null
    : { rowKey, eventKey };
}

function toggleHistoricalStatus(rowKey: string): void {
  expandedHistoricalStatusKey.value = expandedHistoricalStatusKey.value === rowKey ? null : rowKey;
  openWorkStatusDetail.value = null;
}

function toggleCompletedStatus(rowKey: string): void {
  expandedCompletedStatusKey.value = expandedCompletedStatusKey.value === rowKey ? null : rowKey;
  openWorkStatusDetail.value = null;
}

// 行被移除时关闭悬浮详情；历史状态本身仍可在用户主动展开后查看工具详情。
watch(rows, (nextRows) => {
  const open = openWorkStatusDetail.value;
  if (!open) return;
  const row = nextRows.find((candidate) => candidate.type === "work-status" && candidate.key === open.rowKey);
  if (!row || row.type !== "work-status") {
    openWorkStatusDetail.value = null;
  }
});

// 同一轮从运行态进入成功完成态时默认收起，并清理运行期间打开的事件气泡。
watch(latestCompletedStatusKey, (nextKey, previousKey) => {
  if (nextKey && nextKey !== previousKey) {
    expandedCompletedStatusKey.value = null;
    openWorkStatusDetail.value = null;
  }
});

// 新用户消息进入时间线后，上一轮立即收为图标，并关闭所有跨轮详情。
watch(latestUserMessageKey, () => {
  expandedHistoricalStatusKey.value = null;
  expandedCompletedStatusKey.value = null;
  openWorkStatusDetail.value = null;
});
</script>

<template>
  <div class="oc-timeline-root">
    <ConversationLocator :state="state" />
    <div class="oc-timeline">
      <div v-if="rows.length === 0" class="oc-empty-state">
        <div class="oc-empty-state__title">等待任务输入</div>
      </div>
      <TimelineRow
        v-for="row in inlineRows"
        :key="row.key"
        :row="row"
        :state="state"
        :historical-work-status-expanded="expandedHistoricalStatusKey === row.key"
        :completed-work-status-expanded="expandedCompletedStatusKey === row.key"
        :open-work-status-event-key="openWorkStatusDetail?.rowKey === row.key ? openWorkStatusDetail.eventKey : undefined"
        @open-diff="emit('openDiff')"
        @open-file="(path) => emit('openFile', path)"
        @select-subagent="(sessionId) => emit('selectSubagent', sessionId)"
        @toggle-work-status-event="(eventKey) => toggleWorkStatusDetail(row.key, eventKey)"
        @toggle-historical-work-status="toggleHistoricalStatus(row.key)"
        @toggle-completed-work-status="toggleCompletedStatus(row.key)"
        @close-work-status-event="openWorkStatusDetail = null"
      >
        <template #completed-status-actions>
          <slot name="completed-status-actions" />
        </template>
      </TimelineRow>
    </div>
    <Teleport v-if="resolvedDockTarget" :to="resolvedDockTarget">
      <div class="oc-timeline-root oc-work-status-dock" data-testid="oc-work-status-dock">
        <TimelineRow
          v-for="row in dockRows"
          :key="row.key"
          :row="row"
          :state="state"
          :completed-work-status-expanded="expandedCompletedStatusKey === row.key"
          :open-work-status-event-key="openWorkStatusDetail?.rowKey === row.key ? openWorkStatusDetail.eventKey : undefined"
          @open-diff="emit('openDiff')"
          @open-file="(path) => emit('openFile', path)"
          @select-subagent="(sessionId) => emit('selectSubagent', sessionId)"
          @toggle-work-status-event="(eventKey) => toggleWorkStatusDetail(row.key, eventKey)"
          @toggle-completed-work-status="toggleCompletedStatus(row.key)"
          @close-work-status-event="openWorkStatusDetail = null"
        >
          <template #completed-status-actions>
            <slot name="completed-status-actions" />
          </template>
        </TimelineRow>
      </div>
    </Teleport>
  </div>
</template>
