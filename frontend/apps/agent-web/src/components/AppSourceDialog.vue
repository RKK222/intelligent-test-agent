<script setup lang="ts">
import { computed, ref, watch } from "vue";
import type {
  AppSourceMaterializationPayload,
  AppSourceOperation,
  AppSourcePathType,
  AppSourcePurpose,
  AppSourceRemoteTreeNode,
  AppSourceRepositorySummary,
  AppSourceTreeSnapshot
} from "@test-agent/shared-types";
import { AlertTriangle, Check, ChevronRight, Clock3, LoaderCircle, RotateCcw, Server, X } from "lucide-vue-next";

type MaterializationRequest = Omit<AppSourceMaterializationPayload, "operationId">;

const props = defineProps<{
  open: boolean;
  repository?: AppSourceRepositorySummary | null;
  branches?: string[];
  branchesLoading?: boolean;
  treeSnapshot?: AppSourceTreeSnapshot | null;
  treeLoading?: boolean;
  operation?: AppSourceOperation | null;
  submitting?: boolean;
  progressError?: string | null;
}>();

const emit = defineEmits<{
  close: [];
  "load-branches": [repository: AppSourceRepositorySummary];
  "load-tree": [branch: string, path: string];
  materialize: [payload: MaterializationRequest];
  retry: [operation: AppSourceOperation];
}>();

const step = ref(1);
const branch = ref("");
const selectedPathKeys = ref<string[]>([]);
const purpose = ref<AppSourcePurpose>("TEAM");
const retentionHours = ref(24);
const confirmReplace = ref(false);

const currentOperation = computed(() => props.operation ?? props.repository?.latestOperation ?? null);
const needsReplaceConfirmation = computed(() => Boolean(props.repository?.generation));
const flattenedTree = computed(() => flattenTree(props.treeSnapshot?.nodes ?? []));
const indexedSelectionPaths = computed(() => new Set(
  (props.repository?.selectedPaths ?? []).map((item) => item.path)
));
const selectedPaths = computed(() => {
  const typeByPath = new Map(flattenedTree.value.map((item) => [item.node.path, item.node.type]));
  const authorityTypes = new Map((props.repository?.selectedPaths ?? []).map((item) => [item.path, item.type]));
  return selectedPathKeys.value.map((path) => ({
    path,
    type: (typeByPath.get(path) === "directory" ? "DIRECTORY" : typeByPath.has(path) ? "FILE" : authorityTypes.get(path)) as AppSourcePathType
  })).filter((item) => Boolean(item.type));
});

watch(
  () => [props.open, props.repository?.repositoryId, props.repository?.generation] as const,
  ([open]) => {
    if (!open || !props.repository) return;
    branch.value = props.repository.branch ?? props.branches?.[0] ?? "";
    selectedPathKeys.value = props.repository.selectedPaths.map((item) => item.path);
    purpose.value = props.repository.purpose ?? "TEAM";
    retentionHours.value = 24;
    confirmReplace.value = false;
    step.value = currentOperation.value ? 4 : 1;
  },
  { immediate: true }
);

watch(() => props.operation, (operation) => {
  if (props.open && operation) step.value = 4;
});

watch(() => props.branches, (branches) => {
  // 首次下载的仓库没有历史 branch；异步分支列表到达后必须显式选中第一项。
  if (props.open && !branch.value && branches?.[0]) branch.value = branches[0];
});

function flattenTree(nodes: AppSourceRemoteTreeNode[], depth = 0): Array<{ node: AppSourceRemoteTreeNode; depth: number }> {
  return nodes.flatMap((node) => [{ node, depth }, ...flattenTree(node.children ?? [], depth + 1)]);
}

function moveNext() {
  if (!props.repository) return;
  if (step.value === 1) {
    step.value = 2;
    emit("load-branches", props.repository);
    return;
  }
  if (step.value === 2) step.value = 3;
}

function changeBranch() {
  // 历史 exact set 是服务端权威镜像；切分支时先保留，待新树回来后由可见节点与提交校验共同约束。
  if (branch.value) emit("load-tree", branch.value, "");
}

function togglePath(path: string, checked: boolean) {
  const next = new Set(selectedPathKeys.value);
  if (checked) next.add(path); else next.delete(path);
  selectedPathKeys.value = [...next];
}

function expandDirectory(node: AppSourceRemoteTreeNode) {
  if (node.type === "directory" && node.children.length === 0 && branch.value) {
    emit("load-tree", branch.value, node.path);
  }
}

function clampRetention() {
  const numeric = Number(retentionHours.value);
  retentionHours.value = Number.isFinite(numeric) ? Math.min(72, Math.max(1, Math.round(numeric))) : 24;
}

function submitMaterialization() {
  if (!props.repository || !branch.value || !props.treeSnapshot?.targetCommit || selectedPaths.value.length === 0) return;
  clampRetention();
  if (needsReplaceConfirmation.value && !confirmReplace.value) return;
  emit("materialize", {
    expectedGeneration: props.repository.generation ?? null,
    branch: branch.value,
    expectedTreeCommit: props.treeSnapshot.targetCommit,
    selectedPaths: selectedPaths.value,
    purpose: purpose.value,
    retentionHours: retentionHours.value,
    confirmReplace: confirmReplace.value
  });
}

function durationLabel(milliseconds?: number | null) {
  if (milliseconds === null || milliseconds === undefined) return "—";
  if (milliseconds < 1000) return `${milliseconds} 毫秒`;
  return `${(milliseconds / 1000).toFixed(2)} 秒`;
}

function operationRunning(operation: AppSourceOperation | null) {
  return operation?.status === "PENDING" || operation?.status === "RUNNING";
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="app-source-dialog-layer">
      <section class="app-source-dialog" role="dialog" aria-modal="true" aria-label="下载应用源码">
        <header class="app-source-dialog-header">
          <div>
            <h2>应用源码快照</h2>
            <p>{{ repository ? `${repository.name} · ${repository.englishName}` : "选择要下载的版本库" }}</p>
          </div>
          <button type="button" aria-label="关闭源码弹窗" @click="emit('close')"><X /></button>
        </header>

        <ol class="app-source-steps" aria-label="源码下载步骤">
          <li v-for="item in 4" :key="item" :class="{ 'is-current': step === item, 'is-complete': step > item }">
            <span>{{ item }}</span>
            {{ ["版本库状态", "分支与目录", "用途与保留", "服务器进度"][item - 1] }}
          </li>
        </ol>

        <main class="app-source-dialog-body">
          <section v-if="step === 1" class="app-source-step-panel">
            <h3>版本库状态</h3>
            <div v-if="repository" class="app-source-status-card">
              <div><strong>{{ repository.name }}</strong><span>{{ repository.downloadState }}</span></div>
              <dl>
                <div><dt>当前分支</dt><dd>{{ repository.branch || "未选择" }}</dd></div>
                <div><dt>当前 generation</dt><dd>{{ repository.generation ?? "首次下载" }}</dd></div>
                <div><dt>用途</dt><dd>{{ repository.purpose || "未设置" }}</dd></div>
                <div><dt>到期时间</dt><dd>{{ repository.expiresAt || "—" }}</dd></div>
              </dl>
              <p v-if="repository.ownerName || repository.ownerUnifiedAuthId" class="app-source-owner">
                个人占用：{{ [repository.ownerName, repository.ownerUnifiedAuthId].filter(Boolean).join(" · ") }}
              </p>
            </div>
          </section>

          <section v-else-if="step === 2" class="app-source-step-panel">
            <h3>分支与精确目录</h3>
            <label class="app-source-field">
              <span>源码分支</span>
              <select v-model="branch" aria-label="源码分支" :disabled="branchesLoading" @change="changeBranch">
                <option v-for="item in branches ?? []" :key="item" :value="item">{{ item }}</option>
              </select>
            </label>
            <p class="app-source-help">历史路径已按服务端索引镜像默认勾选；本次勾选会作为完整 exact set 提交。</p>
            <div class="app-source-tree" :aria-busy="treeLoading">
              <div v-if="treeLoading" class="app-source-tree-state"><LoaderCircle class="app-source-spin" />加载固定提交目录树…</div>
              <label
                v-for="item in flattenedTree"
                v-else
                :key="item.node.path"
                :class="['app-source-tree-row', { 'is-indexed-selection': indexedSelectionPaths.has(item.node.path) }]"
                :style="{ paddingLeft: `${8 + item.depth * 18}px` }"
              >
                <button
                  v-if="item.node.type === 'directory'"
                  type="button"
                  :aria-label="`展开路径 ${item.node.path}`"
                  @click.prevent="expandDirectory(item.node)"
                ><ChevronRight /></button>
                <span v-else class="app-source-tree-spacer" />
                <input
                  type="checkbox"
                  :aria-label="`选择路径 ${item.node.path}`"
                  :checked="selectedPathKeys.includes(item.node.path)"
                  @change="togglePath(item.node.path, ($event.target as HTMLInputElement).checked)"
                />
                <span>{{ item.node.name }}</span>
                <small>{{ item.node.type === "directory" ? "目录" : "文件" }}</small>
              </label>
            </div>
            <div v-if="treeSnapshot?.targetCommit" class="app-source-fixed-commit">固定提交：{{ treeSnapshot.targetCommit }}</div>
          </section>

          <section v-else-if="step === 3" class="app-source-step-panel">
            <h3>用途与保留时间</h3>
            <div class="app-source-purpose">
              <label><input v-model="purpose" type="radio" value="TEAM" aria-label="团队源码" />团队源码</label>
              <p>同一台服务器上的应用成员共享 READY 副本。</p>
              <label><input v-model="purpose" type="radio" value="PERSONAL" aria-label="个人源码" />个人源码</label>
              <p>仅当前用户占用，列表会展示姓名与 UCID。</p>
            </div>
            <label class="app-source-field">
              <span>保留小时数（1–72）</span>
              <input v-model.number="retentionHours" aria-label="保留小时数" type="number" min="1" max="72" @change="clampRetention" />
            </label>
            <label v-if="needsReplaceConfirmation" class="app-source-replace-confirm">
              <input v-model="confirmReplace" type="checkbox" aria-label="确认覆盖当前源码" />
              我已确认用本次完整选择覆盖当前 generation
            </label>
          </section>

          <section v-else class="app-source-step-panel app-source-timeline-panel">
            <div class="app-source-timeline-heading">
              <div><h3>服务器步骤轨迹</h3><p>关闭弹窗只停止观察，后台任务继续执行；重新打开会恢复当前 operation。</p></div>
              <span v-if="currentOperation" :class="['app-source-operation-status', `is-${currentOperation.status.toLowerCase()}`]">
                {{ currentOperation.status }}
              </span>
            </div>
            <div v-if="progressError" class="app-source-progress-error"><AlertTriangle />{{ progressError }}</div>
            <div v-if="!currentOperation" class="app-source-tree-state">等待提交源码任务。</div>
            <template v-else>
              <div class="app-source-operation-summary">
                <span>commit {{ currentOperation.targetCommit || "—" }}</span>
                <span>traceId {{ currentOperation.traceId }}</span>
              </div>
              <article v-if="currentOperation.globalSteps.length" class="app-source-server-card app-source-global-card">
                <header>
                  <Check />
                  <strong>全局安全步骤</strong>
                </header>
                <ol>
                  <li v-for="globalStep in [...currentOperation.globalSteps].sort((a, b) => a.sequence - b.sequence)" :key="`${globalStep.sequence}:${globalStep.stepCode}`">
                    <span class="app-source-timeline-node">
                      <LoaderCircle v-if="globalStep.status === 'RUNNING'" class="app-source-spin" />
                      <Check v-else-if="globalStep.status === 'SUCCEEDED'" />
                      <AlertTriangle v-else-if="globalStep.status === 'FAILED'" />
                      <Clock3 v-else />
                    </span>
                    <div><strong>{{ globalStep.stepCode }}</strong><p>{{ globalStep.safeSummary || "等待安全步骤摘要" }}</p></div>
                    <time>{{ durationLabel(globalStep.elapsedMillis) }}</time>
                  </li>
                </ol>
              </article>
              <article v-for="serverSummary in currentOperation.serverSummaries" :key="serverSummary.linuxServerId" class="app-source-server-card">
                <header>
                  <Server />
                  <strong>{{ serverSummary.linuxServerId }}</strong>
                  <span>{{ serverSummary.replicaStatus || "PENDING" }}</span>
                  <small>尝试 {{ serverSummary.attemptCount }} 次</small>
                </header>
                <ol>
                  <li v-for="serverStep in [...serverSummary.steps].sort((a, b) => a.sequence - b.sequence)" :key="`${serverStep.sequence}:${serverStep.stepCode}`">
                    <span class="app-source-timeline-node">
                      <LoaderCircle v-if="serverStep.status === 'RUNNING'" class="app-source-spin" />
                      <Check v-else-if="serverStep.status === 'SUCCEEDED'" />
                      <AlertTriangle v-else-if="serverStep.status === 'FAILED'" />
                      <Clock3 v-else />
                    </span>
                    <div><strong>{{ serverStep.stepCode }}</strong><p>{{ serverStep.safeSummary || "等待安全步骤摘要" }}</p></div>
                    <time>{{ durationLabel(serverStep.elapsedMillis) }}</time>
                  </li>
                </ol>
                <p v-if="serverSummary.safeErrorMessage" class="app-source-server-error">
                  {{ serverSummary.safeErrorCode }} · {{ serverSummary.safeErrorMessage }}
                </p>
              </article>
            </template>
          </section>
        </main>

        <footer class="app-source-dialog-footer">
          <button v-if="step > 1 && step < 4" type="button" class="is-secondary" @click="step -= 1">上一步</button>
          <span class="app-source-dialog-footer-space" />
          <button
            v-if="step === 1"
            type="button"
            aria-label="下一步：选择分支与目录"
            :disabled="!repository"
            @click="moveNext"
          >下一步</button>
          <button
            v-else-if="step === 2"
            type="button"
            aria-label="下一步：用途与保留时间"
            :disabled="!branch || selectedPaths.length === 0"
            @click="moveNext"
          >下一步</button>
          <button
            v-else-if="step === 3"
            type="button"
            aria-label="提交源码物化"
            :disabled="submitting || !treeSnapshot?.targetCommit || selectedPaths.length === 0 || (needsReplaceConfirmation && !confirmReplace)"
            @click="submitMaterialization"
          >{{ submitting ? "正在提交…" : "开始下载" }}</button>
          <button
            v-else-if="currentOperation && ['PARTIAL_FAILED', 'FAILED'].includes(currentOperation.status)"
            type="button"
            aria-label="重试失败或缺失副本"
            @click="emit('retry', currentOperation)"
          ><RotateCcw />重试失败或缺失副本</button>
          <span v-else-if="operationRunning(currentOperation)" class="app-source-running-note">后台任务执行中，不提供取消操作</span>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.app-source-dialog-layer { position: fixed; inset: 0; z-index: 3700; display: grid; place-items: center; padding: 20px; background: rgb(15 23 42 / 0.38); }
.app-source-dialog { display: flex; width: min(980px, calc(100vw - 32px)); height: min(720px, calc(100vh - 32px)); flex-direction: column; overflow: hidden; border: 1px solid var(--ta-border, #e4e4e7); border-radius: 10px; background: var(--ta-panel-bg, #fff); box-shadow: 0 24px 70px rgb(15 23 42 / 0.26); color: var(--ta-text, #27272a); font-family: inherit; }
.app-source-dialog-header { display: flex; align-items: center; justify-content: space-between; padding: 13px 16px 11px; border-bottom: 1px solid var(--ta-border, #e4e4e7); }
.app-source-dialog-header h2 { margin: 0; font-size: 15px; font-weight: 600; }
.app-source-dialog-header p { margin: 3px 0 0; color: #71717a; font-size: 11px; }
.app-source-dialog-header button { display: inline-flex; width: 26px; height: 26px; align-items: center; justify-content: center; border: 0; background: transparent; cursor: pointer; }
.app-source-dialog-header svg { width: 15px; }
.app-source-steps { display: grid; grid-template-columns: repeat(4, 1fr); gap: 0; margin: 0; padding: 9px 16px; border-bottom: 1px solid var(--ta-border, #e4e4e7); background: #fafafa; list-style: none; }
.app-source-steps li { display: flex; align-items: center; gap: 7px; color: #a1a1aa; font-size: 11px; }
.app-source-steps li::after { height: 1px; flex: 1; margin: 0 8px; background: #e4e4e7; content: ""; }
.app-source-steps li:last-child::after { display: none; }
.app-source-steps li span { display: grid; width: 20px; height: 20px; place-items: center; border: 1px solid #d4d4d8; border-radius: 50%; background: #fff; font-size: 10px; }
.app-source-steps .is-current { color: #3f3f46; font-weight: 600; }
.app-source-steps .is-current span { border-color: #6366f1; background: #6366f1; color: #fff; }
.app-source-steps .is-complete span { border-color: #059669; background: #ecfdf5; color: #047857; }
.app-source-dialog-body { min-height: 0; flex: 1; overflow: auto; padding: 16px; }
.app-source-step-panel { max-width: 820px; margin: 0 auto; }
.app-source-step-panel h3 { margin: 0 0 12px; font-size: 14px; font-weight: 600; }
.app-source-status-card, .app-source-server-card { border: 1px solid var(--ta-border, #e4e4e7); border-radius: 7px; background: #fff; }
.app-source-status-card { padding: 14px; }
.app-source-status-card > div:first-child { display: flex; align-items: center; justify-content: space-between; }
.app-source-status-card > div:first-child span { color: #047857; font-size: 11px; font-weight: 600; }
.app-source-status-card dl { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px 20px; margin: 14px 0 0; }
.app-source-status-card dl div { min-width: 0; }
.app-source-status-card dt { color: #71717a; font-size: 11px; }
.app-source-status-card dd { margin: 3px 0 0; font-family: var(--ta-font-mono, monospace); font-size: 12px; }
.app-source-owner { color: #7c3aed; font-size: 11px; }
.app-source-field { display: grid; gap: 6px; margin-bottom: 12px; color: #52525b; font-size: 11px; }
.app-source-field select, .app-source-field input { height: 32px; border: 1px solid #d4d4d8; border-radius: 5px; background: #fff; padding: 0 9px; color: #27272a; font-size: 12px; }
.app-source-help { margin: 0 0 8px; color: #71717a; font-size: 11px; }
.app-source-tree { min-height: 180px; max-height: 330px; overflow: auto; border: 1px solid var(--ta-border, #e4e4e7); border-radius: 6px; }
.app-source-tree-state { display: flex; min-height: 120px; align-items: center; justify-content: center; gap: 7px; color: #71717a; font-size: 12px; }
.app-source-tree-row { display: flex; height: 28px; align-items: center; gap: 6px; border-bottom: 1px solid #f4f4f5; font-size: 12px; }
.app-source-tree-row.is-indexed-selection { background: #f4f7f5; box-shadow: inset 2px 0 #16a34a; }
.app-source-tree-row button, .app-source-tree-spacer { display: inline-flex; width: 16px; height: 16px; align-items: center; justify-content: center; border: 0; background: transparent; padding: 0; }
.app-source-tree-row button svg { width: 12px; }
.app-source-tree-row small { margin-left: auto; margin-right: 10px; color: #a1a1aa; font-size: 10px; }
.app-source-fixed-commit, .app-source-operation-summary { margin-top: 8px; color: #71717a; font-family: var(--ta-font-mono, monospace); font-size: 11px; }
.app-source-purpose { display: grid; grid-template-columns: max-content 1fr; gap: 8px 14px; margin-bottom: 14px; }
.app-source-purpose label { display: flex; align-items: center; gap: 6px; font-size: 12px; font-weight: 600; }
.app-source-purpose p { margin: 0; color: #71717a; font-size: 11px; }
.app-source-replace-confirm { display: flex; align-items: center; gap: 7px; padding: 10px; border: 1px solid #f59e0b; border-radius: 6px; background: #fffbeb; color: #92400e; font-size: 11px; }
.app-source-timeline-panel { max-width: none; }
.app-source-timeline-heading { display: flex; align-items: start; justify-content: space-between; gap: 16px; }
.app-source-timeline-heading h3 { margin-bottom: 3px; }
.app-source-timeline-heading p { margin: 0; color: #71717a; font-size: 11px; }
.app-source-operation-status { border-radius: 999px; background: #f4f4f5; padding: 4px 8px; font-size: 10px; font-weight: 700; }
.app-source-operation-status.is-succeeded { background: #ecfdf5; color: #047857; }
.app-source-operation-status.is-partial_failed, .app-source-operation-status.is-failed { background: #fff7ed; color: #c2410c; }
.app-source-progress-error, .app-source-server-error { display: flex; align-items: center; gap: 6px; color: #b91c1c; font-size: 11px; }
.app-source-progress-error svg { width: 14px; }
.app-source-operation-summary { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px; margin: 12px 0 8px; }
.app-source-server-card { padding: 12px; }
.app-source-server-card + .app-source-server-card { margin-top: 10px; }
.app-source-server-card > header { display: flex; align-items: center; gap: 8px; }
.app-source-server-card > header svg { width: 16px; color: #6366f1; }
.app-source-server-card > header span { margin-left: auto; color: #71717a; font-size: 10px; font-weight: 700; }
.app-source-server-card > header small { color: #a1a1aa; font-size: 10px; }
.app-source-server-card ol { margin: 12px 0 0; padding: 0; list-style: none; }
.app-source-server-card li { position: relative; display: grid; grid-template-columns: 24px minmax(0, 1fr) auto; gap: 8px; min-height: 48px; }
.app-source-server-card li:not(:last-child)::before { position: absolute; top: 20px; bottom: -2px; left: 9px; width: 1px; background: #d4d4d8; content: ""; }
.app-source-timeline-node { z-index: 1; display: grid; width: 20px; height: 20px; place-items: center; border: 1px solid #d4d4d8; border-radius: 50%; background: #fff; }
.app-source-timeline-node svg { width: 12px; height: 12px; }
.app-source-server-card li strong { font-family: var(--ta-font-mono, monospace); font-size: 11px; }
.app-source-server-card li p { margin: 3px 0 0; color: #71717a; font-size: 11px; }
.app-source-server-card time { color: #a1a1aa; font-size: 10px; }
.app-source-dialog-footer { display: flex; align-items: center; gap: 8px; padding: 10px 16px; border-top: 1px solid var(--ta-border, #e4e4e7); background: #fafafa; }
.app-source-dialog-footer-space { flex: 1; }
.app-source-dialog-footer button { display: inline-flex; min-height: 30px; align-items: center; gap: 6px; border: 1px solid #4f46e5; border-radius: 5px; background: #4f46e5; padding: 0 12px; color: #fff; font-size: 11px; font-weight: 600; cursor: pointer; }
.app-source-dialog-footer button.is-secondary { border-color: #d4d4d8; background: #fff; color: #3f3f46; }
.app-source-dialog-footer button:disabled { cursor: not-allowed; opacity: 0.45; }
.app-source-dialog-footer button svg { width: 13px; }
.app-source-running-note { color: #71717a; font-size: 11px; }
.app-source-spin { animation: source-dialog-spin 1s linear infinite; }
@keyframes source-dialog-spin { to { transform: rotate(360deg); } }
@media (max-width: 720px) { .app-source-dialog-layer { padding: 8px; } .app-source-dialog { width: 100%; height: 100%; } .app-source-steps li { font-size: 0; } .app-source-status-card dl { grid-template-columns: 1fr; } }
</style>
