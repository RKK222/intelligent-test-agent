<script lang="ts">
import type { MessagePart, SubagentSession } from "@test-agent/shared-types";

export type ToolPartGroupProps = {
  parts: Array<Extract<MessagePart, { type: "tool" }>>;
  busy?: boolean;
  subagentsBySessionId?: Record<string, SubagentSession>;
  subagentByTaskPartId?: Record<string, string>;
};
</script>

<script setup lang="ts">
import { computed, ref } from "vue";
import { ChevronDown, ChevronRight } from "lucide-vue-next";
import ToolPartView from "../parts/ToolPartView.vue";
import { getToolInfo } from "../../state/tool-registry";

const props = defineProps<ToolPartGroupProps>();
const emit = defineEmits<{ selectSubagent: [sessionId: string] }>();

const open = ref(false);

const firstInfo = computed(() => (props.parts[0] ? getToolInfo(props.parts[0]) : undefined));
const titleText = computed(() => firstInfo.value?.title ?? "Tool");
const subtitleText = computed(() => {
  if (props.parts.length > 1) {
    return `${props.parts.length} 次`;
  }
  return firstInfo.value?.subtitle ?? "";
});
const subtitleTitle = computed(() => firstInfo.value?.fullSubtitle ?? firstInfo.value?.subtitle ?? titleText.value);

const aggregateStatus = computed(() => {
  if (props.busy) return "running";
  const statuses = props.parts.map((part) => part.status ?? "").map((status) => status.toLowerCase());
  if (statuses.some((status) => status === "running" || status === "pending")) return "running";
  if (statuses.some((status) => status === "failed" || status === "error")) return "failed";
  if (statuses.some((status) => status === "completed" || status === "success")) return "completed";
  return props.parts.at(-1)?.status ?? "completed";
});

const statusText = computed(() => {
  if (aggregateStatus.value === "running") return "进行中";
  if (aggregateStatus.value === "failed" || aggregateStatus.value === "error") return "失败";
  return "已读取";
});
</script>

<template>
  <section class="oc-tool-group" data-testid="oc-tool-group">
    <button type="button" class="oc-tool-group__trigger" @click="open = !open">
      <span class="oc-tool__title">{{ titleText }}</span>
      <span v-if="subtitleText" class="oc-tool__subtitle" :title="subtitleTitle">{{ subtitleText }}</span>
      <span :class="['oc-tool__status', `is-${aggregateStatus}`]">{{ statusText }}</span>
      <ChevronDown v-if="open" class="oc-tool__chevron" />
      <ChevronRight v-else class="oc-tool__chevron" />
    </button>
    <div v-if="open" class="oc-tool-group__body">
      <ToolPartView
        v-for="part in parts"
        :key="part.partId"
        :part="part"
        :subagents-by-session-id="subagentsBySessionId"
        :subagent-by-task-part-id="subagentByTaskPartId"
        :nested="true"
        @select-subagent="(sessionId) => emit('selectSubagent', sessionId)"
      />
    </div>
  </section>
</template>
