<script setup lang="ts">
import { computed } from "vue";
import { ChevronRight, Loader2 } from "lucide-vue-next";
import type {
  PublicAgentConfigRolloutServerStatus,
  PublicAgentConfigRolloutTargetStatus
} from "@test-agent/shared-types";

const props = defineProps<{
  server: PublicAgentConfigRolloutServerStatus;
  actionMode: "stop" | "restart";
  busyTargetId: string | null;
}>();

const emit = defineEmits<{
  action: [target: PublicAgentConfigRolloutTargetStatus];
}>();

const pendingTargets = computed(() => props.server.pendingTargets ?? []);
const omittedTargetCount = computed(() => Math.max(0, props.server.targetPending - pendingTargets.value.length));
const disclosureTitle = computed(() => props.actionMode === "stop" ? "未排空用户" : "尚未重启 / dispose 的用户");

function targetOwner(target: PublicAgentConfigRolloutTargetStatus) {
  return target.username?.trim() || target.userId?.trim() || "无法识别用户";
}

function targetStatusText(target: PublicAgentConfigRolloutTargetStatus) {
  if (target.forceStop) {
    return "等待强制停止";
  }
  return ({
    PROCESSING: "正在检查",
    RETRY_WAIT: "等待重试"
  } as Record<string, string>)[target.status] ?? target.status;
}
</script>

<template>
  <details class="ta-rollout-target-disclosure">
    <summary class="ta-rollout-target-summary">
      <ChevronRight class="ta-rollout-target-chevron" :stroke-width="1.8" aria-hidden="true" />
      <strong>{{ disclosureTitle }}</strong>
      <span class="ta-rollout-target-count">{{ server.targetPending }}</span>
      <span class="ta-rollout-target-hint">点击查看用户与进程明细</span>
      <span v-if="omittedTargetCount > 0" class="ta-rollout-target-hint">
        当前返回 {{ pendingTargets.length }} 个，另有 {{ omittedTargetCount }} 个目标
      </span>
    </summary>

    <div v-if="pendingTargets.length" class="ta-rollout-target-list">
      <article
        v-for="target in pendingTargets"
        :key="target.targetId"
        class="ta-rollout-target"
      >
        <div class="ta-rollout-target-owner">
          <strong>{{ targetOwner(target) }}</strong>
          <span class="ta-rollout-target-muted">{{ target.userId?.trim() || "-" }}</span>
        </div>
        <div class="ta-rollout-target-state">
          <span>{{ targetStatusText(target) }}</span>
          <span>重试 {{ target.retryCount }}</span>
          <span v-if="target.lastError" class="ta-rollout-target-error">{{ target.lastError }}</span>
        </div>
        <div class="ta-rollout-target-process">
          {{ target.containerId }}:{{ target.port }} · PID {{ target.processPid ?? "-" }}
        </div>
        <button
          type="button"
          class="ta-rollout-target-action"
          :aria-label="`${actionMode === 'stop' ? '关闭' : '重启'} ${targetOwner(target)} 的 OpenCode`"
          :disabled="busyTargetId !== null"
          @click="emit('action', target)"
        >
          <Loader2 v-if="busyTargetId === target.targetId" class="ta-rollout-target-spinner" />
          {{ actionMode === "stop" ? "关闭该用户 OpenCode" : "立即受管重启" }}
        </button>
      </article>
    </div>
    <div v-else class="ta-rollout-target-empty">目标明细尚未返回，请等待下一轮定时巡检。</div>
  </details>
</template>

<style scoped>
.ta-rollout-target-disclosure {
  overflow: hidden;
  border: 1px solid #dbe3ee;
  border-radius: 6px;
  background: #fff;
}

.ta-rollout-target-summary {
  display: flex;
  min-height: 34px;
  align-items: center;
  gap: 7px;
  padding: 0 9px;
  color: #334155;
  cursor: pointer;
  font-size: 12px;
  list-style: none;
  user-select: none;
}

.ta-rollout-target-summary::-webkit-details-marker {
  display: none;
}

.ta-rollout-target-summary:hover,
.ta-rollout-target-summary:focus-visible {
  background: #f1f5f9;
  outline: none;
}

.ta-rollout-target-chevron {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
  color: #64748b;
  transition: transform 150ms ease;
}

.ta-rollout-target-disclosure[open] .ta-rollout-target-chevron {
  transform: rotate(90deg);
}

.ta-rollout-target-count {
  min-width: 18px;
  border-radius: 999px;
  background: #fff7ed;
  color: #b45309;
  font-variant-numeric: tabular-nums;
  line-height: 18px;
  text-align: center;
}

.ta-rollout-target-hint,
.ta-rollout-target-muted {
  color: #64748b;
}

.ta-rollout-target-hint:last-child {
  margin-left: auto;
}

.ta-rollout-target-list {
  display: grid;
  gap: 6px;
  border-top: 1px solid #e5e7eb;
  background: #f8fafc;
  padding: 8px;
}

.ta-rollout-target {
  display: grid;
  grid-template-columns: minmax(140px, 0.9fr) minmax(220px, 1.5fr) minmax(180px, 1fr) auto;
  gap: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
  padding: 8px;
}

.ta-rollout-target-owner,
.ta-rollout-target-state {
  display: flex;
  min-width: 0;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px 8px;
}

.ta-rollout-target-owner {
  flex-direction: column;
  align-items: flex-start;
}

.ta-rollout-target-state > span:not(.ta-rollout-target-error) {
  white-space: nowrap;
}

.ta-rollout-target-error {
  width: 100%;
  color: #b45309;
  overflow-wrap: anywhere;
}

.ta-rollout-target-process {
  align-self: center;
  color: #4b5563;
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  overflow-wrap: anywhere;
}

.ta-rollout-target-action {
  display: inline-flex;
  min-height: 28px;
  align-items: center;
  justify-content: center;
  align-self: center;
  gap: 6px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  background: #fff7f7;
  color: #b91c1c;
  cursor: pointer;
  font-size: 12px;
  padding: 0 10px;
}

.ta-rollout-target-action:hover:not(:disabled),
.ta-rollout-target-action:focus-visible:not(:disabled) {
  border-color: #ef4444;
  color: #991b1b;
  outline: none;
}

.ta-rollout-target-action:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.ta-rollout-target-spinner {
  width: 14px;
  height: 14px;
  animation: ta-rollout-target-spin 0.9s linear infinite;
}

.ta-rollout-target-empty {
  border-top: 1px solid #e5e7eb;
  background: #f8fafc;
  color: #64748b;
  padding: 9px;
}

@media (max-width: 1080px) {
  .ta-rollout-target {
    grid-template-columns: minmax(140px, 1fr) minmax(220px, 1.5fr);
  }
}

@media (prefers-reduced-motion: reduce) {
  .ta-rollout-target-chevron {
    transition: none;
  }
}

@keyframes ta-rollout-target-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
