<script setup lang="ts">
import { computed } from "vue";
import { CheckCircle2, Circle, Loader2, X, XCircle } from "lucide-vue-next";
import type { OpencodeProcessStartOperation, OpencodeProcessStartOperationStep } from "@test-agent/shared-types";

type StartupOperationView = Partial<OpencodeProcessStartOperation> & {
  steps?: OpencodeProcessStartOperationStep[];
};

const props = defineProps<{
  open: boolean;
  actionLabel?: string;
  operation?: StartupOperationView | null;
}>();

const emit = defineEmits<{
  (e: "close"): void;
}>();

const title = computed(() => props.actionLabel ?? "启动进程");
const statusText = computed(() => {
  if (!props.operation) return "准备中";
  if (props.operation.status === "SUCCEEDED") return "已完成";
  if (props.operation.status === "FAILED") return "启动失败";
  return "启动中";
});
const failed = computed(() => props.operation?.status === "FAILED");

function stepCode(step: OpencodeProcessStartOperationStep): string {
  return step.step ?? step.code ?? "";
}

function stepClass(step: OpencodeProcessStartOperationStep) {
  return [
    "ta-process-startup-step",
    `is-${step.status.toLowerCase()}`
  ];
}

function statusIcon(step: OpencodeProcessStartOperationStep) {
  if (step.status === "SUCCEEDED") return CheckCircle2;
  if (step.status === "FAILED") return XCircle;
  if (step.status === "RUNNING") return Loader2;
  return Circle;
}
</script>

<template>
  <div v-if="open" class="ta-process-startup-backdrop" role="presentation">
    <section class="ta-process-startup-dialog" role="dialog" aria-modal="true" :aria-label="title">
      <header class="ta-process-startup-header">
        <div>
          <h2>{{ title }}</h2>
          <p>{{ statusText }}</p>
        </div>
        <button type="button" class="ta-process-startup-close" aria-label="关闭" @click="emit('close')">
          <X :size="16" />
        </button>
      </header>

      <ol class="ta-process-startup-steps">
        <li
          v-for="step in operation?.steps ?? []"
          :key="stepCode(step) || step.name"
          :class="stepClass(step)"
        >
          <component :is="statusIcon(step)" :size="18" class="ta-process-startup-step-icon" />
          <div class="ta-process-startup-step-copy">
            <span>{{ step.name }}</span>
            <small>{{ step.status }}</small>
          </div>
        </li>
      </ol>

      <div v-if="failed" class="ta-process-startup-error">
        <strong>{{ operation?.errorCode ?? "INTERNAL_ERROR" }}</strong>
        <p>{{ operation?.errorMessage ?? "初始化 opencode 进程失败" }}</p>
        <small v-if="operation?.traceId">traceId: {{ operation.traceId }}</small>
      </div>

      <footer class="ta-process-startup-footer">
        <span v-if="operation?.serviceAddress">{{ operation.serviceAddress }}</span>
        <span v-else>{{ operation?.operationId ?? "" }}</span>
        <button type="button" :disabled="operation?.status === 'RUNNING'" @click="emit('close')">关闭</button>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.ta-process-startup-backdrop {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgb(15 23 42 / 42%);
}

.ta-process-startup-dialog {
  width: min(520px, 100%);
  max-height: min(680px, calc(100vh - 40px));
  overflow: auto;
  border: 1px solid var(--ta-border, #d8dee9);
  border-radius: 8px;
  background: var(--ta-panel, #ffffff);
  color: var(--ta-text, #18202f);
  box-shadow: 0 22px 55px rgb(15 23 42 / 22%);
}

.ta-process-startup-header,
.ta-process-startup-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--ta-border, #d8dee9);
}

.ta-process-startup-footer {
  border-top: 1px solid var(--ta-border, #d8dee9);
  border-bottom: 0;
  color: var(--ta-muted, #667085);
  font-size: 12px;
}

.ta-process-startup-header h2 {
  margin: 0;
  font-size: 15px;
  line-height: 22px;
  font-weight: 650;
}

.ta-process-startup-header p {
  margin: 2px 0 0;
  color: var(--ta-muted, #667085);
  font-size: 12px;
}

.ta-process-startup-close,
.ta-process-startup-footer button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 30px;
  border: 1px solid var(--ta-border, #d8dee9);
  border-radius: 6px;
  background: var(--ta-surface, #f8fafc);
  color: inherit;
}

.ta-process-startup-close {
  width: 30px;
  padding: 0;
}

.ta-process-startup-footer button {
  padding: 0 12px;
}

.ta-process-startup-footer button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.ta-process-startup-steps {
  display: grid;
  gap: 0;
  margin: 0;
  padding: 8px 16px;
  list-style: none;
}

.ta-process-startup-step {
  display: grid;
  grid-template-columns: 24px 1fr;
  min-height: 44px;
  align-items: center;
  gap: 8px;
  border-bottom: 1px solid var(--ta-border-subtle, #edf1f7);
}

.ta-process-startup-step:last-child {
  border-bottom: 0;
}

.ta-process-startup-step-icon {
  color: var(--ta-muted, #667085);
}

.ta-process-startup-step.is-running .ta-process-startup-step-icon {
  color: #2563eb;
  animation: ta-process-spin 1.1s linear infinite;
}

.ta-process-startup-step.is-succeeded .ta-process-startup-step-icon {
  color: #15803d;
}

.ta-process-startup-step.is-failed .ta-process-startup-step-icon {
  color: #b42318;
}

.ta-process-startup-step-copy {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.ta-process-startup-step-copy span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.ta-process-startup-step-copy small {
  color: var(--ta-muted, #667085);
  font-size: 11px;
}

.ta-process-startup-error {
  margin: 4px 16px 14px;
  padding: 10px 12px;
  border: 1px solid #f3b7b0;
  border-radius: 6px;
  background: #fff5f3;
  color: #7a271a;
}

.ta-process-startup-error strong,
.ta-process-startup-error p,
.ta-process-startup-error small {
  display: block;
}

.ta-process-startup-error p {
  margin: 5px 0;
  line-height: 18px;
  word-break: break-word;
}

@keyframes ta-process-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
