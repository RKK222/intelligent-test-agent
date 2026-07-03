<script lang="ts">
import type { OpencodeLikeConversationState } from "../state/types";

export type OpencodeTimelineProps = {
  state: OpencodeLikeConversationState;
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import TimelineRow from "./TimelineRow.vue";
import { createTimelineRows } from "../state/projection";

const props = defineProps<OpencodeTimelineProps>();
const emit = defineEmits<{ openDiff: []; selectSubagent: [sessionId: string] }>();
const rows = computed(() => createTimelineRows(props.state));
</script>

<template>
  <div class="oc-timeline-root">
    <div class="oc-timeline">
      <div v-if="rows.length === 0" class="oc-empty-state">
        <div class="oc-empty-state__title">等待任务输入</div>
      </div>
      <TimelineRow
        v-for="row in rows"
        :key="row.key"
        :row="row"
        :state="state"
        @open-diff="emit('openDiff')"
        @select-subagent="(sessionId) => emit('selectSubagent', sessionId)"
      />
    </div>
  </div>
</template>
