<script setup lang="ts">
import { computed } from "vue";
import type { MessagePart, SubagentSession } from "@test-agent/shared-types";

const props = defineProps<{
  part: Extract<MessagePart, { type: "tool" }>;
  subagent?: SubagentSession;
}>();
const emit = defineEmits<{ selectSubagent: [sessionId: string] }>();

const title = computed(() =>
  props.subagent?.title ??
  text(props.part.metadata?.title) ??
  text(props.part.input?.description) ??
  text(props.part.input?.prompt) ??
  "准备子 Agent"
);
const agentName = computed(() => {
  const raw =
    props.subagent?.agentName ??
    text(props.part.input?.subagent_type) ??
    text(props.part.metadata?.agentName) ??
    text(props.part.metadata?.agent);
  return raw ? displayName(raw) : "智能体";
});
const status = computed(() => props.subagent?.status ?? props.part.status);
const clickable = computed(() => Boolean(props.subagent?.sessionId));

function selectSubagent() {
  if (props.subagent?.sessionId) {
    emit("selectSubagent", props.subagent.sessionId);
  }
}

function text(value: unknown): string | undefined {
  return typeof value === "string" && value.trim().length > 0 ? value.trim() : undefined;
}

function displayName(value: string): string {
  const trimmed = value.trim();
  return trimmed ? `${trimmed.charAt(0).toUpperCase()}${trimmed.slice(1)}` : "智能体";
}

function formatStatus(value: string | undefined): string {
  const normalized = value?.toLowerCase();
  if (normalized === "completed" || normalized === "success") return "已完成";
  if (normalized === "running") return "进行中";
  if (normalized === "pending") return "准备中";
  if (normalized === "failed" || normalized === "error") return "失败";
  return value ?? "";
}
</script>

<template>
  <button
    type="button"
    :class="['oc-subagent-card', clickable ? 'is-clickable' : 'is-disabled']"
    :disabled="!clickable"
    @click="selectSubagent"
  >
    <span class="oc-subagent-card__agent">{{ agentName }}</span>
    <span class="oc-subagent-card__title">{{ title }}</span>
    <span v-if="status" :class="['oc-subagent-card__status', `is-${status.toLowerCase()}`]">{{ formatStatus(status) }}</span>
  </button>
</template>
