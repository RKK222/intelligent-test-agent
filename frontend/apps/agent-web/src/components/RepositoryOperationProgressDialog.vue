<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { Button } from "@test-agent/ui-kit";
import { Check, RefreshCw, X } from "lucide-vue-next";

type Notice = { message: string; traceId?: string };
type Operation = "SYNCHRONIZE" | "VERIFY_POINTERS";
type RequestState = "REQUESTING" | "ACCEPTED" | "FAILED";
type StepState = "waiting" | "running" | "completed" | "failed";
type OperationServer = {
  linuxServerId: string;
  serverName?: string | null;
  status: string;
  online?: boolean;
  currentBranch?: string | null;
  currentCommitHash?: string | null;
  matchesTarget?: boolean | null;
  error?: string | null;
};
type OperationTarget = {
  name: string;
  englishName?: string | null;
  branch?: string | null;
  targetCommitHash?: string | null;
  status: string;
  targetServerCount: number;
  readyServerCount: number;
  servers: OperationServer[];
  traceId?: string | null;
  message?: string | null;
};

const props = withDefaults(defineProps<{
  open: boolean;
  operation: Operation;
  requestState: RequestState;
  target?: OperationTarget | null;
  acceptedTarget?: OperationTarget | null;
  error?: Notice | null;
  pollingError?: Notice | null;
  canClose: boolean;
  canRetry: boolean;
  resourceLabel?: string;
  replicaLabel?: string;
}>(), {
  target: null,
  acceptedTarget: null,
  error: null,
  pollingError: null,
  resourceLabel: "资产库",
  replicaLabel: "资产"
});

const emit = defineEmits<{ close: []; retry: [] }>();
const dialogElement = ref<HTMLElement | null>(null);
const steps = [1, 2, 3] as const;
const synchronization = computed(() => props.operation === "SYNCHRONIZE");

watch(
  () => props.open,
  (open) => {
    if (open) void nextTick(() => dialogElement.value?.focus());
  },
  { immediate: true }
);

function dialogLabel() {
  return synchronization.value ? `${props.resourceLabel}同步进度` : "Git 指针核验进度";
}

function closeLabel() {
  return synchronization.value ? `关闭${props.resourceLabel}同步进度` : "关闭 Git 指针核验进度";
}

function retryLabel() {
  return synchronization.value ? `重试${props.resourceLabel}同步` : "重试 Git 指针核验";
}

function stepState(step: 1 | 2 | 3): StepState {
  if (step === 1) {
    if (props.requestState === "REQUESTING") return "running";
    return props.requestState === "FAILED" ? "failed" : "completed";
  }
  if (props.requestState !== "ACCEPTED") return "waiting";
  if (props.acceptedTarget?.status === "FAILED") return "failed";
  if (props.acceptedTarget?.status === "READY") return "completed";
  return step === 2 ? "running" : "waiting";
}

function stepText(step: 1 | 2 | 3) {
  const state = stepState(step);
  if (step === 1) {
    return state === "running" ? "正在创建" : state === "failed" ? "创建失败" : "任务已创建";
  }
  if (step === 2) {
    return state === "running"
      ? synchronization.value ? "同步中" : "核验中"
      : state === "completed"
        ? "已完成"
        : state === "failed"
          ? synchronization.value ? "同步失败" : "核验失败"
          : "等待";
  }
  return state === "completed"
    ? synchronization.value ? "同步完成" : "核验完成"
    : state === "failed"
      ? synchronization.value ? "同步失败" : "核验失败"
      : "等待服务器";
}

function stepTitle(step: 1 | 2 | 3) {
  if (step === 1) return synchronization.value ? "创建同步任务" : "创建核验任务";
  if (step === 2) return synchronization.value ? "各服务器同步" : "各服务器核验";
  return synchronization.value ? "汇总同步结果" : "汇总核验结果";
}

function stepDescription(step: 1 | 2 | 3) {
  if (step === 1) return synchronization.value ? "向多节点协调器提交同步代次" : "向多节点协调器提交只读核验代次";
  if (step === 2) return synchronization.value ? "同步固定分支与目标 HEAD 到各服务器" : "读取本地分支、HEAD、origin 和工作树状态";
  return "按当前在线服务器判断本轮是否收敛";
}

function headline() {
  if (props.requestState === "REQUESTING") return synchronization.value ? "正在创建同步任务" : "正在创建核验任务";
  if (props.requestState === "FAILED") return synchronization.value ? "同步任务创建失败" : "核验任务创建失败";
  if (props.acceptedTarget?.status === "READY") return synchronization.value ? "同步完成" : "核验完成";
  if (props.acceptedTarget?.status === "FAILED") return synchronization.value ? "同步失败" : "核验失败";
  return synchronization.value ? `正在同步各服务器${props.replicaLabel}副本` : "正在核验服务器 Git 指针";
}

function serverStatusText(server: OperationServer) {
  if (synchronization.value) {
    switch (server.status) {
      case "PENDING": return "等待同步";
      case "PROCESSING": return "同步中";
      case "READY": return "已同步";
      case "BLOCKED": return "同步失败";
      case "RETRY_WAIT": return "等待重试";
      case "DEFERRED": return "离线延后";
      default: return server.status;
    }
  }
  switch (server.status) {
    case "PENDING": return "等待认领";
    case "PROCESSING": return "核验中";
    case "READY": return server.matchesTarget === true ? "已一致" : server.matchesTarget === false ? "不一致" : "已核验";
    case "BLOCKED": return "核验失败";
    case "RETRY_WAIT": return "等待重试";
    case "DEFERRED": return "离线延后";
    default: return server.status;
  }
}

function serverStatusClass(server: OperationServer) {
  if (server.status === "READY") return "is-completed";
  if (server.status === "BLOCKED") return "is-failed";
  if (["PENDING", "PROCESSING", "RETRY_WAIT"].includes(server.status)) return "is-running";
  return "is-waiting";
}

function shortCommit(commitHash?: string | null) {
  return commitHash ? commitHash.slice(0, 12) : "—";
}
</script>

<template>
  <div v-if="open" class="repository-operation-backdrop">
    <section
      ref="dialogElement"
      class="reference-verification-progress"
      role="dialog"
      aria-modal="true"
      :aria-label="dialogLabel()"
      :aria-busy="canClose ? undefined : 'true'"
      tabindex="-1"
    >
      <header class="reference-verification-header">
        <div>
          <h3>{{ synchronization ? `同步${resourceLabel}` : "刷新 Git 指针" }}</h3>
          <p aria-live="polite">{{ headline() }}</p>
        </div>
        <Button size="sm" variant="ghost" :aria-label="closeLabel()" :disabled="!canClose" @click="emit('close')">关闭</Button>
      </header>

      <div v-if="target" class="reference-verification-target">
        <div>
          <span>版本库</span>
          <strong>{{ target.name }}<template v-if="target.englishName">（{{ target.englishName }}）</template></strong>
        </div>
        <div>
          <span>目标指针</span>
          <code>{{ target.branch || "—" }} · {{ shortCommit(target.targetCommitHash) }}</code>
        </div>
        <div>
          <span>服务器</span>
          <strong>{{ target.readyServerCount }}/{{ target.targetServerCount }} 台就绪</strong>
        </div>
      </div>

      <ol class="reference-verification-steps">
        <li v-for="step in steps" :key="step" :class="`is-${stepState(step)}`">
          <span class="reference-verification-marker" aria-hidden="true">
            <Check v-if="stepState(step) === 'completed'" class="h-3.5 w-3.5" />
            <X v-else-if="stepState(step) === 'failed'" class="h-3.5 w-3.5" />
            <RefreshCw v-else-if="stepState(step) === 'running'" class="h-3.5 w-3.5 animate-spin" />
            <span v-else>{{ step }}</span>
          </span>
          <div>
            <strong>{{ stepTitle(step) }}</strong>
            <small>{{ stepDescription(step) }}</small>
          </div>
          <span class="reference-verification-step-status">{{ stepText(step) }}</span>
          <div v-if="step === 2 && requestState === 'ACCEPTED'" class="reference-verification-servers">
            <div v-for="server in acceptedTarget?.servers || []" :key="server.linuxServerId" class="reference-verification-server">
              <div>
                <strong>{{ server.serverName || server.linuxServerId }}</strong>
                <small>{{ server.online === true ? "在线" : server.online === false ? "离线" : "在线状态未知" }}</small>
              </div>
              <span :class="serverStatusClass(server)">{{ serverStatusText(server) }}</span>
              <code>{{ server.currentBranch || "—" }} · {{ shortCommit(server.currentCommitHash) }}</code>
              <small v-if="server.error" class="is-error">{{ server.error }}</small>
            </div>
            <div v-if="(acceptedTarget?.servers.length || 0) === 0" class="reference-verification-server-empty">
              正在等待服务器领取{{ synchronization ? "同步" : "核验" }}任务…
            </div>
          </div>
        </li>
      </ol>

      <div v-if="error" class="reference-verification-error" role="alert">
        <strong>{{ error.message }}</strong>
        <code v-if="error.traceId">traceId: {{ error.traceId }}</code>
      </div>
      <div v-else-if="pollingError" class="reference-verification-error is-retrying" role="status">
        <strong>{{ pollingError.message }}</strong>
        <span>正在自动重试状态读取…</span>
        <code v-if="pollingError.traceId">traceId: {{ pollingError.traceId }}</code>
      </div>
      <div v-else-if="acceptedTarget?.status === 'FAILED'" class="reference-verification-error" role="alert">
        <strong>{{ acceptedTarget.message || (synchronization ? `服务器${replicaLabel}副本同步失败` : "服务器指针核验失败") }}</strong>
        <code v-if="acceptedTarget.traceId">traceId: {{ acceptedTarget.traceId }}</code>
      </div>

      <footer class="reference-verification-actions">
        <Button v-if="canRetry" size="sm" variant="ghost" :aria-label="retryLabel()" @click="emit('retry')">重试</Button>
        <span v-if="!canClose">{{ synchronization ? "同步" : "核验" }}期间请保持此窗口打开</span>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.repository-operation-backdrop {
  position: absolute;
  inset: 0;
  z-index: 8;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.48);
}

.reference-verification-progress {
  display: flex;
  width: min(620px, 100%);
  max-height: min(680px, calc(100vh - 72px));
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--ta-border-strong);
  border-radius: 9px;
  outline: none;
  background: var(--ta-panel-2);
  box-shadow: 0 22px 56px rgba(15, 23, 42, 0.26);
}

.reference-verification-header {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid var(--ta-border);
  padding: 12px 14px;
  background: var(--ta-panel);
}

.reference-verification-header h3,
.reference-verification-header p { margin: 0; }
.reference-verification-header h3 { font-size: 14px; font-weight: 600; }
.reference-verification-header p { margin-top: 3px; color: var(--ta-muted); font-size: 11px; }

.reference-verification-target {
  display: grid;
  flex-shrink: 0;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr) auto;
  gap: 12px;
  border-bottom: 1px solid var(--ta-border);
  padding: 9px 14px;
  background: var(--ta-surface);
}
.reference-verification-target div { min-width: 0; }
.reference-verification-target span,
.reference-verification-target strong,
.reference-verification-target code { display: block; }
.reference-verification-target span { margin-bottom: 3px; color: var(--ta-muted); font-size: 9px; text-transform: uppercase; }
.reference-verification-target strong,
.reference-verification-target code { overflow: hidden; color: var(--ta-text); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.reference-verification-target code { font-family: "Geist Mono", monospace; }

.reference-verification-steps {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 7px;
  overflow: auto;
  margin: 0;
  padding: 12px 14px;
  list-style: none;
}
.reference-verification-steps > li {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) auto;
  align-items: center;
  gap: 4px 9px;
  border: 1px solid var(--ta-border);
  border-radius: 7px;
  padding: 8px 9px;
  background: var(--ta-surface);
}
.reference-verification-steps > li.is-running { border-color: var(--ta-cyan); background: rgba(79, 111, 122, 0.07); }
.reference-verification-steps > li.is-completed { border-color: var(--ta-ok); background: rgba(63, 122, 90, 0.07); }
.reference-verification-steps > li.is-failed { border-color: var(--ta-error); background: rgba(158, 59, 52, 0.07); }
.reference-verification-marker {
  display: inline-grid;
  width: 22px;
  height: 22px;
  place-items: center;
  border: 1px solid var(--ta-border-strong);
  border-radius: 50%;
  color: var(--ta-muted);
  font-family: "Geist Mono", monospace;
  font-size: 9px;
}
.reference-verification-steps > li.is-running .reference-verification-marker { border-color: var(--ta-cyan); color: var(--ta-cyan); }
.reference-verification-steps > li.is-completed .reference-verification-marker { border-color: var(--ta-ok); color: var(--ta-ok); }
.reference-verification-steps > li.is-failed .reference-verification-marker { border-color: var(--ta-error); color: var(--ta-error); }
.reference-verification-steps strong,
.reference-verification-steps small { display: block; }
.reference-verification-steps strong { color: var(--ta-text); font-size: 11px; font-weight: 600; }
.reference-verification-steps small { margin-top: 2px; color: var(--ta-muted); font-size: 9px; }
.reference-verification-step-status { color: var(--ta-muted); font-size: 10px; white-space: nowrap; }
.reference-verification-servers {
  display: flex;
  grid-column: 2 / 4;
  flex-direction: column;
  gap: 4px;
  margin-top: 4px;
  border-top: 1px solid var(--ta-border);
  padding-top: 6px;
}
.reference-verification-server {
  display: grid;
  grid-template-columns: minmax(110px, 1fr) auto minmax(120px, auto);
  align-items: center;
  gap: 6px 10px;
  border-radius: 5px;
  padding: 4px 6px;
  background: var(--ta-panel);
  font-size: 10px;
}
.reference-verification-server > div strong,
.reference-verification-server > div small { display: inline; }
.reference-verification-server > div small { margin-left: 5px; }
.reference-verification-server > span { color: var(--ta-muted); font-size: 10px; font-weight: 600; }
.reference-verification-server > span.is-completed { color: var(--ta-ok); }
.reference-verification-server > span.is-failed,
.reference-verification-server > small.is-error { color: var(--ta-error); }
.reference-verification-server code {
  overflow: hidden;
  color: var(--ta-text);
  font-family: "Geist Mono", monospace;
  font-size: 9px;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.reference-verification-server > small.is-error { grid-column: 1 / -1; margin: 0; }
.reference-verification-server-empty { padding: 4px 6px; color: var(--ta-muted); font-size: 10px; }
.reference-verification-error {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  gap: 3px;
  margin: 0 14px 10px;
  border: 1px solid rgba(158, 59, 52, 0.35);
  border-radius: 6px;
  padding: 7px 9px;
  background: rgba(158, 59, 52, 0.07);
  color: var(--ta-error);
  font-size: 10px;
}
.reference-verification-error.is-retrying { border-color: rgba(79, 111, 122, 0.35); background: rgba(79, 111, 122, 0.07); color: var(--ta-cyan); }
.reference-verification-error code { font-family: "Geist Mono", monospace; font-size: 9px; }
.reference-verification-actions {
  display: flex;
  min-height: 40px;
  flex-shrink: 0;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  border-top: 1px solid var(--ta-border);
  padding: 6px 14px;
  color: var(--ta-muted);
  font-size: 10px;
}

@media (max-width: 720px) {
  .reference-verification-target { grid-template-columns: 1fr; }
  .reference-verification-server { grid-template-columns: minmax(0, 1fr) auto; }
  .reference-verification-server code { grid-column: 1 / -1; text-align: left; }
}
</style>
