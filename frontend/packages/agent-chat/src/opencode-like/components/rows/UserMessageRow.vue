<script lang="ts">
import type { AgentMessage } from "@test-agent/shared-types";

export type UserMessageRowProps = {
  message: Extract<AgentMessage, { role: "user" }>;
  resendable?: boolean;
};
</script>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { Clock3, FileText, RotateCcw, Scissors } from "lucide-vue-next";
import {
  displayTextFromUserPrompt,
  workspaceContextAttachmentsFromPromptParts,
  workspaceContextAttachmentsFromUserPrompt
} from "../../../user-message-display";
import OcCopyButton from "../primitives/OcCopyButton.vue";

const props = defineProps<UserMessageRowProps>();
const emit = defineEmits<{ resend: [] }>();
const nowMs = ref(Date.now());
let countdownTimer: ReturnType<typeof setInterval> | null = null;
const displayText = computed(() => displayTextFromUserPrompt(props.message.text));
const scheduledAtFormatter = new Intl.DateTimeFormat("zh-CN", {
  timeZone: "Asia/Shanghai",
  month: "numeric",
  day: "numeric",
  hour: "2-digit",
  minute: "2-digit",
  hour12: false
});
const scheduledAt = computed(() => {
  if (!props.message.createdAt) return "";
  const createdAt = new Date(props.message.createdAt);
  if (Number.isNaN(createdAt.getTime())) return "";
  return scheduledAtFormatter.format(createdAt);
});
const workspaceContexts = computed(() => {
  const partContexts = workspaceContextAttachmentsFromPromptParts(props.message.parts);
  return partContexts.length ? partContexts : workspaceContextAttachmentsFromUserPrompt(props.message.text);
});
const resendWaiting = computed(() => props.message.resend?.status === "WAITING");
const resendActive = computed(() => ["WAITING", "REVERTING", "REVERTED"].includes(props.message.resend?.status ?? ""));
const resendCountdown = computed(() => {
  const executeAt = props.message.resend?.executeAt;
  if (!executeAt || !resendWaiting.value) return 0;
  return Math.max(0, Math.ceil((Date.parse(executeAt) - nowMs.value) / 1000));
});
const sourceBadge = computed(() => {
  const resend = props.message.resend;
  if (!resend) return "夜间定时执行";
  if (resend.trigger === "AUTOMATIC") {
    return `夜间定时执行 · 自动重发 ${resend.automaticAttempt}/${resend.automaticLimit}`;
  }
  return "夜间定时执行 · 手动重发";
});
onMounted(() => {
  countdownTimer = setInterval(() => { nowMs.value = Date.now(); }, 1000);
});
onBeforeUnmount(() => {
  if (countdownTimer) clearInterval(countdownTimer);
});
</script>

<template>
  <div
    class="oc-user-message"
    data-testid="oc-user-message"
    data-oc-turn-row="true"
    :data-oc-turn-id="message.messageId ?? message.id"
  >
    <div class="oc-user-message__content">
      <div v-if="message.sourceType === 'SCHEDULED_TASK'" class="oc-user-message__source-badge">
        <Clock3 aria-hidden="true" />
        <span>{{ sourceBadge }}<span v-if="resendWaiting"> · {{ resendCountdown }} 秒后</span><span v-else-if="scheduledAt"> · {{ scheduledAt }}</span></span>
      </div>
      <div class="oc-user-message__bubble">
        <p>{{ displayText }}</p>
      </div>
      <div class="oc-user-message__actions">
        <button
          v-if="resendable && !resendActive"
          type="button"
          class="oc-user-message__resend oc-icon-button"
          aria-label="撤销重发最后一条消息"
          title="撤销重发"
          @click="emit('resend')"
        >
          <RotateCcw class="oc-icon-button__icon" aria-hidden="true" />
        </button>
        <OcCopyButton :value="message.text" />
      </div>
      <div v-if="workspaceContexts.length" class="oc-user-message__contexts" aria-label="本轮关联的工作区上下文">
        <span
          v-for="context in workspaceContexts"
          :key="`${context.type}:${context.path}:${context.lines ?? ''}`"
          class="oc-user-message__context-chip"
          :title="context.path"
        >
          <component :is="context.type === 'selection' ? Scissors : FileText" class="oc-user-message__context-icon" />
          <span class="oc-user-message__context-type">{{ context.type === 'selection' ? '选区' : '文件' }}</span>
          <span class="oc-user-message__context-name">{{ context.fileName }}</span>
          <span v-if="context.lines" class="oc-user-message__context-lines">L{{ context.lines }}</span>
        </span>
      </div>
    </div>
  </div>
</template>
