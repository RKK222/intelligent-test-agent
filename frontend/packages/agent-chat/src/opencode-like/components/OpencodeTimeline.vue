<script lang="ts">
import type { OpencodeLikeConversationState } from "../state/types";

export type OpencodeTimelineProps = {
  state: OpencodeLikeConversationState;
};
</script>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import TimelineRow from "./TimelineRow.vue";
import ConversationLocator from "./ConversationLocator.vue";
import { createTimelineRows } from "../state/projection";

const props = defineProps<OpencodeTimelineProps>();
const emit = defineEmits<{ openDiff: []; openFile: [path: string]; selectSubagent: [sessionId: string] }>();
const rows = computed(() => createTimelineRows(props.state));
const openWorkStatusDetail = ref<{ rowKey: string; eventKey: string } | null>(null);

function toggleWorkStatusDetail(rowKey: string, eventKey: string): void {
  const current = openWorkStatusDetail.value;
  openWorkStatusDetail.value = current?.rowKey === rowKey && current.eventKey === eventKey
    ? null
    : { rowKey, eventKey };
}

// 新用户轮次出现后，旧轮由 latest 变为 history，必须自动关闭已经打开的详情。
watch(rows, (nextRows) => {
  const open = openWorkStatusDetail.value;
  if (!open) return;
  const row = nextRows.find((candidate) => candidate.type === "work-status" && candidate.key === open.rowKey);
  if (!row || row.type !== "work-status" || !row.isLatest) {
    openWorkStatusDetail.value = null;
  }
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
        v-for="row in rows"
        :key="row.key"
        :row="row"
        :state="state"
        :open-work-status-event-key="openWorkStatusDetail?.rowKey === row.key ? openWorkStatusDetail.eventKey : undefined"
        @open-diff="emit('openDiff')"
        @open-file="(path) => emit('openFile', path)"
        @select-subagent="(sessionId) => emit('selectSubagent', sessionId)"
        @toggle-work-status-event="(eventKey) => toggleWorkStatusDetail(row.key, eventKey)"
        @close-work-status-event="openWorkStatusDetail = null"
      />
    </div>
  </div>
</template>
