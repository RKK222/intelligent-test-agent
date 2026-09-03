<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { Check, Circle, GitMerge, LoaderCircle, X } from "lucide-vue-next";
import { ElCheckbox, ElDialog } from "element-plus";
import type { PersonalWorkspaceGitPullResult } from "@test-agent/shared-types";
import { Button } from "@test-agent/ui-kit";

export type PersonalWorkspacePullDialogPhase =
  | "CONFIRM"
  | "FORCE_CONFIRM"
  | "PULLING"
  | "FINALIZING"
  | "SUCCEEDED"
  | "FAILED";

export type PersonalWorkspacePullDisposeStatus =
  | "NOT_REQUIRED"
  | "DISPOSED"
  | "WAITING_IDLE"
  | "NOT_RUNNING"
  | "FAILED";

export type PersonalWorkspacePullDialogResult = PersonalWorkspaceGitPullResult & {
  disposeStatus: PersonalWorkspacePullDisposeStatus;
  disposeMessage: string;
};

const props = defineProps<{
  open: boolean;
  phase: PersonalWorkspacePullDialogPhase;
  appName?: string;
  branch?: string;
  pullResult?: PersonalWorkspaceGitPullResult | null;
  result?: PersonalWorkspacePullDialogResult | null;
  conflictingFiles?: string[];
  errorTitle?: string;
  errorDescription?: string;
  errorTraceId?: string;
}>();

const emit = defineEmits<{
  confirm: [doNotShowAgain: boolean];
  confirmForce: [];
  cancel: [];
  close: [];
}>();

const doNotShowAgain = ref(false);
const busy = computed(() => props.phase === "PULLING" || props.phase === "FINALIZING");
const visiblePullResult = computed(() => props.result ?? props.pullResult ?? null);
const shortCommit = computed(() => visiblePullResult.value?.commitHash?.slice(0, 8) || "");
const changedFiles = computed(() => props.result?.changedFiles ?? []);
const conflictingFiles = computed(() => (props.conflictingFiles ?? []).filter((file) => file.trim()));

watch(
  () => props.open,
  (open) => {
    if (open && props.phase === "CONFIRM") doNotShowAgain.value = false;
  }
);

function requestClose() {
  if (!busy.value) emit("close");
}

function stepClass(step: "FETCH" | "MERGE" | "REFRESH" | "RUNTIME") {
  if (props.phase === "FAILED") return step === "FETCH" ? "is-failed" : "is-pending";
  if (props.phase === "PULLING") return step === "FETCH" ? "is-running" : "is-pending";
  if (props.phase === "FINALIZING") {
    if (step === "FETCH" || step === "MERGE") return "is-completed";
    return step === "REFRESH" ? "is-running" : "is-pending";
  }
  if (props.phase === "SUCCEEDED") return "is-completed";
  return "is-pending";
}

function stepStatus(step: "FETCH" | "MERGE" | "REFRESH" | "RUNTIME") {
  if (props.phase === "FAILED") return step === "FETCH" ? "失败" : "未执行";
  if (props.phase === "PULLING") return step === "FETCH" ? "执行中" : "等待";
  if (props.phase === "FINALIZING") {
    if (step === "FETCH") return "完成";
    if (step === "MERGE") return visiblePullResult.value?.updated ? "完成" : "无需合并";
    return step === "REFRESH" ? "执行中" : "等待";
  }
  if (props.phase === "SUCCEEDED") {
    if (step === "MERGE" && !props.result?.updated) return "无需合并";
    if (step === "RUNTIME") {
      if (props.result?.disposeStatus === "NOT_REQUIRED") return "无需 dispose";
      if (props.result?.disposeStatus === "NOT_RUNNING") return "下次启动生效";
      if (props.result?.disposeStatus === "WAITING_IDLE") return "等待空闲";
      if (props.result?.disposeStatus === "FAILED") return "处理失败";
    }
    return "完成";
  }
  return "等待";
}

function stepIcon(step: "FETCH" | "MERGE" | "REFRESH" | "RUNTIME") {
  const state = stepClass(step);
  if (state === "is-running") return LoaderCircle;
  if (state === "is-completed") return Check;
  if (state === "is-failed") return X;
  return Circle;
}
</script>

<template>
  <ElDialog
    :model-value="open"
    class="personal-pull-dialog"
    width="min(620px, calc(100vw - 32px))"
    :show-close="!busy"
    :close-on-click-modal="!busy"
    :close-on-press-escape="!busy"
    :destroy-on-close="true"
    append-to-body
    @update:model-value="(visible: boolean) => { if (!visible) requestClose(); }"
  >
    <template #header>
      <div class="personal-pull-header">
        <span class="personal-pull-icon" aria-hidden="true"><GitMerge :size="17" /></span>
        <div>
          <h3>{{ phase === "CONFIRM" ? "确认拉取远程更新" : phase === "FORCE_CONFIRM" ? "确认采用远端冲突文件" : "拉取远程更新" }}</h3>
          <p>{{ appName || "当前应用" }}<template v-if="branch"> · {{ branch }}</template></p>
        </div>
      </div>
    </template>

    <section v-if="phase === 'CONFIRM'" class="personal-pull-confirm" aria-label="拉取远程说明">
      <div>
        <strong>应用 Agent 也会一起更新</strong>
        <p>这次拉取作用于你在当前应用下的整棵个人 worktree，包括应用 workspace、应用 Agent 和其它 workspace。</p>
      </div>
      <div>
        <strong>系统会直接执行 Git merge</strong>
        <p>不冲突的本地改动会保留；如果 Git 判断文件会被覆盖或产生冲突，请到“变更”中处理。</p>
      </div>
      <ElCheckbox v-model="doNotShowAgain">以后不再显示此确认</ElCheckbox>
    </section>

    <section v-else-if="phase === 'FORCE_CONFIRM'" class="personal-pull-force-confirm" aria-label="强制拉取确认">
      <div>
        <strong>这些文件的本地修改将被放弃</strong>
        <p>确认后只处理 Git 本次明确列出的冲突或覆盖文件，并采用远端版本；其它本地文件不会被回退、暂存或提交。</p>
      </div>
      <ul v-if="conflictingFiles.length > 0" aria-label="将采用远端版本的文件">
        <li v-for="file in conflictingFiles" :key="file"><code :title="file">{{ file }}</code></li>
      </ul>
    </section>

    <section v-else class="personal-pull-progress" aria-live="polite">
      <p v-if="phase === 'PULLING'" class="personal-pull-running-copy">
        正在按顺序执行 fetch → 比较提交 → merge，请保持窗口打开。
      </p>

      <ol class="personal-pull-steps" aria-label="拉取与合并流程">
        <li :class="stepClass('FETCH')">
          <span class="personal-pull-step-icon" aria-hidden="true">
            <component :is="stepIcon('FETCH')" :size="14" :class="{ 'animate-spin': stepClass('FETCH') === 'is-running' }" />
          </span>
          <div><strong>获取并比较远程提交</strong><small>读取 origin/{{ visiblePullResult?.remoteBranch || branch || "当前分支" }}</small></div>
          <span>{{ stepStatus('FETCH') }}</span>
        </li>
        <li :class="stepClass('MERGE')">
          <span class="personal-pull-step-icon" aria-hidden="true">
            <component :is="stepIcon('MERGE')" :size="14" :class="{ 'animate-spin': stepClass('MERGE') === 'is-running' }" />
          </span>
          <div>
            <strong>自动 merge 到个人 worktree</strong>
            <small v-if="shortCommit">目标提交 {{ shortCommit }}</small>
            <small v-else>保留 Git 能够安全保留的本地修改</small>
          </div>
          <span>{{ stepStatus('MERGE') }}</span>
        </li>
        <li :class="stepClass('REFRESH')">
          <span class="personal-pull-step-icon" aria-hidden="true">
            <component :is="stepIcon('REFRESH')" :size="14" :class="{ 'animate-spin': stepClass('REFRESH') === 'is-running' }" />
          </span>
          <div><strong>刷新文件树与变更区</strong><small>重新读取当前个人工作区的文件和 Git 状态</small></div>
          <span>{{ stepStatus('REFRESH') }}</span>
        </li>
        <li :class="stepClass('RUNTIME')">
          <span class="personal-pull-step-icon" aria-hidden="true">
            <component :is="stepIcon('RUNTIME')" :size="14" :class="{ 'animate-spin': stepClass('RUNTIME') === 'is-running' }" />
          </span>
          <div>
            <strong>检查应用 Agent 运行态</strong>
            <small>{{ result?.disposeMessage || "只在本次更新包含应用 Agent 时处理当前用户运行态" }}</small>
          </div>
          <span>{{ stepStatus('RUNTIME') }}</span>
        </li>
      </ol>

      <div v-if="phase === 'SUCCEEDED'" class="personal-pull-result" data-testid="personal-pull-result">
        <header>
          <strong>{{ result?.updated ? `已更新 ${changedFiles.length} 个文件` : "当前已是远程最新版本" }}</strong>
          <code v-if="shortCommit">{{ shortCommit }}</code>
        </header>
        <ul v-if="changedFiles.length > 0" aria-label="本次远程更新文件">
          <li v-for="file in changedFiles" :key="file"><code :title="file">{{ file }}</code></li>
        </ul>
        <p v-else>没有文件需要更新。</p>
      </div>

      <div v-if="phase === 'FAILED'" class="personal-pull-error" role="alert">
        <strong>{{ errorTitle || "拉取远程失败" }}</strong>
        <p>{{ errorDescription || "请检查提示后重试。" }}</p>
        <code v-if="errorTraceId">traceId: {{ errorTraceId }}</code>
      </div>
    </section>

    <template #footer>
      <div class="personal-pull-actions">
        <template v-if="phase === 'CONFIRM'">
          <Button type="button" variant="ghost" @click="emit('cancel')">取消</Button>
          <Button type="button" variant="primary" @click="emit('confirm', doNotShowAgain)">开始拉取</Button>
        </template>
        <template v-else-if="phase === 'FORCE_CONFIRM'">
          <Button type="button" variant="ghost" @click="emit('cancel')">取消</Button>
          <Button type="button" variant="primary" @click="emit('confirmForce')">放弃这些本地修改并拉取远程</Button>
        </template>
        <Button v-else-if="!busy" type="button" variant="primary" @click="emit('close')">关闭</Button>
        <span v-else>拉取和 merge 完成前不能关闭</span>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.personal-pull-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.personal-pull-header h3,
.personal-pull-header p,
.personal-pull-confirm p,
.personal-pull-running-copy,
.personal-pull-result p,
.personal-pull-error p {
  margin: 0;
}

.personal-pull-header h3 {
  color: var(--ta-text);
  font-size: 15px;
  font-weight: 600;
}

.personal-pull-header p {
  margin-top: 2px;
  color: var(--ta-muted);
  font-size: 12px;
}

.personal-pull-icon {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border: 1px solid var(--ta-border);
  border-radius: 8px;
  background: var(--ta-surface);
  color: var(--ta-text);
}

.personal-pull-confirm {
  display: grid;
  gap: 12px;
}

.personal-pull-confirm > div,
.personal-pull-force-confirm > div {
  border: 1px solid var(--ta-border);
  border-radius: 8px;
  padding: 12px;
  background: var(--ta-surface);
}

.personal-pull-confirm strong,
.personal-pull-force-confirm strong,
.personal-pull-result strong,
.personal-pull-error strong {
  color: var(--ta-text);
  font-size: 13px;
}

.personal-pull-confirm p,
.personal-pull-force-confirm p,
.personal-pull-error p {
  margin-top: 5px;
  color: var(--ta-muted);
  font-size: 12px;
  line-height: 1.6;
}

.personal-pull-force-confirm {
  display: grid;
  gap: 12px;
}

.personal-pull-force-confirm ul {
  max-height: 180px;
  margin: 0;
  padding: 6px 12px 8px 30px;
  overflow-y: auto;
  border: 1px solid var(--ta-border);
  border-radius: 8px;
  background: var(--ta-surface);
}

.personal-pull-force-confirm li {
  padding: 3px 0;
}

.personal-pull-force-confirm code {
  display: block;
  overflow: hidden;
  color: var(--ta-text);
  font-family: "Geist Mono", monospace;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.personal-pull-progress {
  display: grid;
  gap: 12px;
}

.personal-pull-running-copy {
  color: var(--ta-muted);
  font-size: 12px;
}

.personal-pull-steps {
  display: grid;
  gap: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}

.personal-pull-steps li {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  min-height: 52px;
  border-bottom: 1px solid var(--ta-border);
}

.personal-pull-steps li:last-child {
  border-bottom: 0;
}

.personal-pull-step-icon {
  display: grid;
  width: 22px;
  height: 22px;
  place-items: center;
  border-radius: 999px;
  color: var(--ta-muted);
}

.personal-pull-steps li.is-running .personal-pull-step-icon {
  background: var(--ta-hover);
  color: var(--ta-text);
}

.personal-pull-steps li.is-completed .personal-pull-step-icon {
  background: var(--ta-add);
  color: var(--ta-ok);
}

.personal-pull-steps li.is-failed .personal-pull-step-icon {
  background: var(--ta-del);
  color: var(--ta-error);
}

.personal-pull-steps strong,
.personal-pull-steps small {
  display: block;
}

.personal-pull-steps strong {
  color: var(--ta-text);
  font-size: 12px;
  font-weight: 600;
}

.personal-pull-steps small,
.personal-pull-steps li > span:last-child {
  margin-top: 3px;
  color: var(--ta-muted);
  font-size: 11px;
}

.personal-pull-result {
  overflow: hidden;
  border: 1px solid var(--ta-border);
  border-radius: 8px;
  background: var(--ta-surface);
}

.personal-pull-result header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid var(--ta-border);
  padding: 10px 12px;
}

.personal-pull-result code {
  color: var(--ta-text);
  font-family: "Geist Mono", monospace;
  font-size: 11px;
}

.personal-pull-result ul {
  max-height: 180px;
  margin: 0;
  padding: 6px 12px 8px 30px;
  overflow-y: auto;
}

.personal-pull-result li {
  padding: 3px 0;
}

.personal-pull-result li code {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.personal-pull-result p {
  padding: 12px;
  color: var(--ta-muted);
  font-size: 12px;
}

.personal-pull-error {
  border: 1px solid var(--ta-error);
  border-radius: 8px;
  padding: 12px;
  background: var(--ta-del);
}

.personal-pull-actions {
  display: flex;
  min-height: 28px;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.personal-pull-actions > span {
  color: var(--ta-muted);
  font-size: 11px;
}
</style>
