<script setup lang="ts">
import { computed, inject, onBeforeUnmount, ref, watch } from "vue";
import {
  BackendApiError,
  type AutomationReferenceConfiguration,
  type AutomationReferenceRepositoryStatus,
  type BackendApiClient
} from "@test-agent/backend-api";
import type { RepositoryTreeNode } from "@test-agent/shared-types";
import { Button, copyTextToClipboard, Input, Spinner, Textarea } from "@test-agent/ui-kit";
import { Check, ChevronDown, ChevronRight, Copy, File, Folder, GitBranch, LibraryBig, RefreshCw } from "lucide-vue-next";
import RepositoryOperationProgressDialog from "./RepositoryOperationProgressDialog.vue";
import { reconcileAutomationReferenceWorkspace } from "./automation-reference-config-reconciliation";

const POLL_INTERVAL_MS = 1_000;
const ACTIVE_STATUSES = new Set(["INITIALIZING", "SYNCHRONIZING", "VERIFYING"]);

const props = defineProps<{
  open: boolean;
  appId: string;
  workspaceId: string;
  canManage: boolean;
}>();

const emit = defineEmits<{
  changed: [];
  saved: [];
  "operation-state": [state: { open: boolean; canClose: boolean }];
}>();

type Notice = { message: string; traceId?: string };
type VisibleTreeNode = RepositoryTreeNode & { depth: number };
type Draft = { branch: string; directoryPath: string; description: string };
type OperationProgress = {
  token: number;
  repositoryId: string;
  operation: "SYNCHRONIZE" | "VERIFY_POINTERS";
  retryAction: "SAVE_CONFIGURATION" | "SYNCHRONIZE" | "VERIFY_POINTERS";
  requestState: "REQUESTING" | "ACCEPTED" | "FAILED";
  generation: number | null;
  error: Notice | null;
  pollingError: Notice | null;
};

const api = inject<BackendApiClient>("api")!;
const repositories = ref<AutomationReferenceRepositoryStatus[]>([]);
const loading = ref(false);
const loadError = ref<Notice | null>(null);
const selectedRepositoryId = ref<string | null>(null);
const branches = ref<string[]>([]);
const branchesLoading = ref(false);
const branchError = ref<Notice | null>(null);
const treeByParent = ref<Record<string, RepositoryTreeNode[]>>({});
const treeLoadingPaths = ref<Set<string>>(new Set());
const treeErrors = ref<Record<string, Notice>>({});
const expandedPaths = ref<Set<string>>(new Set());
const draft = ref<Draft>({ branch: "", directoryPath: "", description: "" });
const baseline = ref<Draft | null>(null);
const saving = ref(false);
const actionError = ref<Notice | null>(null);
const configNotice = ref<(Notice & { kind: "error" | "success" | "info" }) | null>(null);
const progress = ref<OperationProgress | null>(null);
const terminating = ref(false);
const terminationError = ref<Notice | null>(null);
const branchPopoverRepositoryId = ref<string | null>(null);
const selectedBranch = ref("");

let viewToken = 0;
let selectionToken = 0;
let operationSequence = 0;
let pollTimer: ReturnType<typeof setTimeout> | null = null;

const selectedRepository = computed(() =>
  repositories.value.find((repository) => repository.repositoryId === selectedRepositoryId.value) ?? null
);
const selectedConfiguration = computed(() => selectedRepository.value?.currentConfiguration ?? null);
const targetConfiguration = computed(() =>
  selectedRepository.value?.pendingConfiguration ?? selectedRepository.value?.currentConfiguration ?? null
);
const visibleTreeNodes = computed<VisibleTreeNode[]>(() => {
  const result: VisibleTreeNode[] = [];
  const append = (path: string, depth: number) => {
    for (const node of treeByParent.value[path] ?? []) {
      result.push({ ...node, depth });
      if (node.type === "directory" && expandedPaths.value.has(node.path)) append(node.path, depth + 1);
    }
  };
  append("", 0);
  return result;
});
const modified = computed(() => {
  if (!baseline.value) return Boolean(draft.value.branch || draft.value.directoryPath || draft.value.description.trim());
  return draft.value.branch !== baseline.value.branch
    || draft.value.directoryPath !== baseline.value.directoryPath
    || draft.value.description.trim() !== baseline.value.description.trim();
});
const displayedLogicalPath = computed(() => modified.value
  ? "保存并同步后生成逻辑代次路径"
  : selectedConfiguration.value?.logicalPath || "保存并同步后生成逻辑代次路径");
const canSave = computed(() => Boolean(
  props.canManage
  && selectedRepository.value
  && draft.value.branch
  && draft.value.description.trim()
  && modified.value
  && !saving.value
  && !branchesLoading.value
  && !ACTIVE_STATUSES.has(selectedRepository.value.status)
));
const progressRepository = computed(() => {
  const current = progress.value;
  if (!current) return null;
  return repositories.value.find((repository) => repository.repositoryId === current.repositoryId) ?? null;
});
const progressTarget = computed(() => operationTarget(progressRepository.value));
const acceptedProgressTarget = computed(() => {
  const current = progress.value;
  const repository = progressRepository.value;
  if (!current || current.requestState !== "ACCEPTED" || !repository) return null;
  if (repository.status === "FAILED") return operationTarget(repository);
  const observedGeneration = repository.pendingGeneration ?? repository.activeGeneration ?? 0;
  return current.generation !== null && observedGeneration >= current.generation
    ? operationTarget(repository)
    : null;
});
const progressCanClose = computed(() => Boolean(
  progress.value?.requestState === "FAILED"
  || ["READY", "FAILED"].includes(acceptedProgressTarget.value?.status ?? "")
));
const progressCanRetry = computed(() => Boolean(
  progress.value?.requestState === "FAILED"
  || acceptedProgressTarget.value?.status === "FAILED"
  || acceptedProgressTarget.value?.servers.some((server) => server.status === "RETRY_WAIT")
));
const progressCanTerminate = computed(() => Boolean(
  acceptedProgressTarget.value && ACTIVE_STATUSES.has(acceptedProgressTarget.value.status)
));

function notice(error: unknown, fallback: string): Notice {
  if (error instanceof BackendApiError) {
    return { message: error.message || fallback, traceId: error.traceId || undefined };
  }
  return { message: error instanceof Error ? error.message : fallback };
}

function createOperationId() {
  const value = typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
    ? crypto.randomUUID().replaceAll("-", "")
    : `${Date.now()}${Math.random().toString(16).slice(2)}`;
  return `aar_${value.replace(/[^A-Za-z0-9_-]/g, "").slice(0, 96)}`;
}

function currentGeneration(repository: AutomationReferenceRepositoryStatus) {
  return repository.activeGeneration ?? 0;
}

function configurationDraft(configuration: AutomationReferenceConfiguration | null | undefined): Draft {
  return configuration
    ? { branch: configuration.branch, directoryPath: configuration.directoryPath, description: configuration.description }
    : { branch: "", directoryPath: "", description: "" };
}

function defaultDescription(
  directoryPath = draft.value.directoryPath,
  branch = draft.value.branch
) {
  const repository = selectedRepository.value;
  if (!repository || !branch) return "";
  return `${repository.name} / ${branch} / ${directoryPath || "."}，只读自动化引用`;
}

function replaceRepository(next: AutomationReferenceRepositoryStatus) {
  const index = repositories.value.findIndex((item) => item.repositoryId === next.repositoryId);
  repositories.value = index < 0
    ? [...repositories.value, next]
    : repositories.value.map((item, itemIndex) => itemIndex === index ? next : item);
}

function operationTarget(repository: AutomationReferenceRepositoryStatus | null) {
  if (!repository) return null;
  const configuration = repository.pendingConfiguration ?? repository.currentConfiguration;
  return {
    name: repository.name,
    englishName: repository.englishName,
    branch: configuration?.branch ?? null,
    targetCommitHash: configuration?.targetCommitHash ?? null,
    status: repository.status,
    targetServerCount: repository.targetServerCount,
    readyServerCount: repository.readyServerCount,
    servers: repository.servers,
    traceId: repository.traceId,
    message: repository.message
  };
}

function shortCommit(commitHash?: string | null) {
  return commitHash ? commitHash.slice(0, 12) : "—";
}

function formattedTime(value?: string | null) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

function directoryName(path: string) {
  return path.split("/").filter(Boolean).at(-1) ?? ".";
}

/** 当前工作树只由应用当前配置集合生成自动化引用；不会写入任何服务器物理路径。 */
async function reconcileWorkspace(items = repositories.value, announce = false) {
  const result = await reconcileAutomationReferenceWorkspace(api, props.appId, props.workspaceId, items);
  if (result.changed) {
    emit("changed");
    emit("saved");
  }
  if (announce) {
    configNotice.value = { kind: "success", message: "应用当前自动化引用已写入工作树，运行态将在空闲后重新加载" };
  }
}

async function loadRepositories() {
  const token = ++viewToken;
  loading.value = true;
  loadError.value = null;
  try {
    const result = await api.listAutomationReferenceRepositories(props.appId);
    if (!props.open || token !== viewToken) return;
    repositories.value = result;
    const repository = result.find((item) => item.repositoryId === selectedRepositoryId.value) ?? result[0] ?? null;
    if (repository) await selectRepository(repository);
    await reconcileWorkspace(result);
  } catch (error) {
    if (props.open && token === viewToken) loadError.value = notice(error, "加载自动化代码库失败");
  } finally {
    if (token === viewToken) loading.value = false;
  }
}

async function selectRepository(repository: AutomationReferenceRepositoryStatus, keepBranchPopover = false) {
  const token = ++selectionToken;
  clearPoll();
  selectedRepositoryId.value = repository.repositoryId;
  actionError.value = null;
  configNotice.value = null;
  treeByParent.value = {};
  treeErrors.value = {};
  treeLoadingPaths.value = new Set();
  expandedPaths.value = new Set();
  if (!keepBranchPopover) branchPopoverRepositoryId.value = null;
  const nextDraft = configurationDraft(repository.currentConfiguration);
  draft.value = nextDraft;
  baseline.value = repository.currentConfiguration ? { ...nextDraft } : null;
  branches.value = nextDraft.branch ? [nextDraft.branch] : [];
  if (nextDraft.branch && repository.currentConfiguration?.status === "READY") {
    await loadTreeLevel("", token, repository.repositoryId);
  }
}

async function openBranchPopover(repository: AutomationReferenceRepositoryStatus) {
  if (selectedRepositoryId.value !== repository.repositoryId) {
    await selectRepository(repository, true);
  }
  branchPopoverRepositoryId.value = repository.repositoryId;
  selectedBranch.value = repository.currentConfiguration?.branch ?? "";
  await loadBranches(repository, selectionToken);
  if (branchPopoverRepositoryId.value === repository.repositoryId) {
    selectedBranch.value = draft.value.branch || branches.value[0] || "";
  }
}

function closeBranchPopover() {
  branchPopoverRepositoryId.value = null;
  selectedBranch.value = "";
}

async function applyBranchDraft(repository: AutomationReferenceRepositoryStatus) {
  if (repository.repositoryId !== selectedRepositoryId.value || !selectedBranch.value) return;
  const previous = draft.value;
  const useDefaultDescription = !previous.description.trim()
    || previous.description === defaultDescription(previous.directoryPath, previous.branch);
  draft.value = {
    branch: selectedBranch.value,
    directoryPath: "",
    description: useDefaultDescription
      ? defaultDescription("", selectedBranch.value)
      : previous.description
  };
  closeBranchPopover();
  await changeBranch();
}

async function loadBranches(repository: AutomationReferenceRepositoryStatus, token: number) {
  branchesLoading.value = true;
  branchError.value = null;
  try {
    const result = await api.listAutomationReferenceRepositoryBranches(props.appId, repository.repositoryId);
    if (token !== selectionToken || repository.repositoryId !== selectedRepositoryId.value) return;
    branches.value = result;
    if (!draft.value.branch) draft.value = { ...draft.value, branch: result[0] ?? "" };
    if (draft.value.branch) await loadTreeLevel("", token, repository.repositoryId);
  } catch (error) {
    if (token === selectionToken) branchError.value = notice(error, "读取自动化代码库分支失败");
  } finally {
    if (token === selectionToken) branchesLoading.value = false;
  }
}

async function changeBranch() {
  const repository = selectedRepository.value;
  if (!repository) return;
  const token = ++selectionToken;
  treeByParent.value = {};
  treeErrors.value = {};
  treeLoadingPaths.value = new Set();
  expandedPaths.value = new Set();
  configNotice.value = null;
  if (draft.value.branch) await loadTreeLevel("", token, repository.repositoryId);
}

async function loadTreeLevel(path: string, token = selectionToken, repositoryId = selectedRepositoryId.value) {
  if (!repositoryId || !draft.value.branch) return;
  treeLoadingPaths.value = new Set(treeLoadingPaths.value).add(path);
  const nextErrors = { ...treeErrors.value };
  delete nextErrors[path];
  treeErrors.value = nextErrors;
  try {
    const nodes = await api.listAutomationReferenceRepositoryTree(props.appId, repositoryId, draft.value.branch, path);
    if (token !== selectionToken || repositoryId !== selectedRepositoryId.value) return;
    treeByParent.value = { ...treeByParent.value, [path]: nodes };
    if (path === "") expandConfiguredPath();
  } catch (error) {
    if (token === selectionToken) treeErrors.value = { ...treeErrors.value, [path]: notice(error, "读取自动化代码库目录失败") };
  } finally {
    if (token === selectionToken) {
      const loadingPaths = new Set(treeLoadingPaths.value);
      loadingPaths.delete(path);
      treeLoadingPaths.value = loadingPaths;
    }
  }
}

function expandConfiguredPath() {
  const parts = draft.value.directoryPath.split("/").filter(Boolean);
  const next = new Set(expandedPaths.value);
  for (let index = 1; index <= parts.length; index += 1) next.add(parts.slice(0, index).join("/"));
  expandedPaths.value = next;
}

async function toggleDirectory(node: VisibleTreeNode) {
  if (node.type !== "directory") return;
  const next = new Set(expandedPaths.value);
  if (next.has(node.path)) {
    next.delete(node.path);
    expandedPaths.value = next;
    return;
  }
  next.add(node.path);
  expandedPaths.value = next;
  if (!treeByParent.value[node.path]) await loadTreeLevel(node.path);
}

function selectDirectory(path: string) {
  if (!props.canManage || saving.value) return;
  const previousDefault = defaultDescription();
  const shouldDefaultDescription = !draft.value.description.trim() || draft.value.description === previousDefault;
  draft.value = {
    ...draft.value,
    directoryPath: path,
    description: shouldDefaultDescription ? defaultDescription(path) : draft.value.description
  };
  configNotice.value = null;
}

function beginProgress(
  repository: AutomationReferenceRepositoryStatus,
  operation: OperationProgress["operation"],
  retryAction: OperationProgress["retryAction"]
) {
  const token = ++operationSequence;
  progress.value = {
    token,
    repositoryId: repository.repositoryId,
    operation,
    retryAction,
    requestState: "REQUESTING",
    generation: null,
    error: null,
    pollingError: null
  };
  emit("operation-state", { open: true, canClose: false });
  return token;
}

function acceptProgress(token: number, next: AutomationReferenceRepositoryStatus) {
  if (progress.value?.token !== token) return;
  const generation = next.pendingGeneration ?? next.activeGeneration ?? 0;
  progress.value = { ...progress.value, requestState: "ACCEPTED", generation, error: null };
  replaceRepository(next);
  pollOperation(token);
}

function failProgress(token: number, error: unknown, fallback: string) {
  if (progress.value?.token !== token) return;
  progress.value = { ...progress.value, requestState: "FAILED", error: notice(error, fallback) };
  emit("operation-state", { open: true, canClose: true });
}

function clearPoll() {
  if (pollTimer !== null) clearTimeout(pollTimer);
  pollTimer = null;
}

function pollOperation(token: number) {
  clearPoll();
  pollTimer = setTimeout(async () => {
    pollTimer = null;
    const current = progress.value;
    if (!props.open || !current || current.token !== token) return;
    try {
      const next = await api.getAutomationReferenceRepositoryStatus(props.appId, current.repositoryId);
      if (progress.value?.token !== token) return;
      replaceRepository(next);
      progress.value = { ...progress.value, pollingError: null };
      if (next.status === "READY") {
        if (current.operation === "SYNCHRONIZE") {
          const latest = await api.listAutomationReferenceRepositories(props.appId);
          repositories.value = latest;
          await reconcileWorkspace(latest, true);
          const selected = latest.find((item) => item.repositoryId === selectedRepositoryId.value);
          if (selected) {
            const nextDraft = configurationDraft(selected.currentConfiguration);
            draft.value = nextDraft;
            baseline.value = { ...nextDraft };
          }
        }
        emit("operation-state", { open: true, canClose: true });
        return;
      }
      if (next.status === "FAILED") {
        emit("operation-state", { open: true, canClose: true });
        return;
      }
    } catch (error) {
      if (progress.value?.token === token) {
        progress.value = { ...progress.value, pollingError: notice(error, "读取自动化引用状态失败，正在重试") };
      }
    }
    if (progress.value?.token === token) pollOperation(token);
  }, POLL_INTERVAL_MS);
}

async function saveConfiguration() {
  const repository = selectedRepository.value;
  if (!repository || !canSave.value) return;
  saving.value = true;
  actionError.value = null;
  configNotice.value = null;
  const token = beginProgress(repository, "SYNCHRONIZE", "SAVE_CONFIGURATION");
  try {
    const next = await api.configureAutomationReferenceRepository(props.appId, repository.repositoryId, {
      branch: draft.value.branch,
      directoryPath: draft.value.directoryPath,
      description: draft.value.description.trim(),
      merge: false,
      expectedGeneration: currentGeneration(repository),
      operationId: createOperationId()
    });
    acceptProgress(token, next);
  } catch (error) {
    failProgress(token, error, "保存自动化引用配置失败");
  } finally {
    saving.value = false;
  }
}

async function synchronizeRepository(repository: AutomationReferenceRepositoryStatus) {
  if (!props.canManage || !repository.currentConfiguration || ACTIVE_STATUSES.has(repository.status)) return;
  const token = beginProgress(repository, "SYNCHRONIZE", "SYNCHRONIZE");
  try {
    const next = await api.synchronizeAutomationReferenceRepository(
      props.appId, repository.repositoryId, currentGeneration(repository), createOperationId()
    );
    acceptProgress(token, next);
  } catch (error) {
    failProgress(token, error, "更新自动化代码库副本失败");
  }
}

async function verifyPointers(repository: AutomationReferenceRepositoryStatus) {
  if (!props.canManage || !repository.currentConfiguration || ACTIVE_STATUSES.has(repository.status)) return;
  const token = beginProgress(repository, "VERIFY_POINTERS", "VERIFY_POINTERS");
  try {
    const next = await api.verifyAutomationReferenceRepository(
      props.appId, repository.repositoryId, currentGeneration(repository)
    );
    acceptProgress(token, next);
  } catch (error) {
    failProgress(token, error, "刷新自动化代码库 Git 指针失败");
  }
}

async function terminateOperation() {
  const current = progress.value;
  if (!current || current.generation === null || terminating.value) return;
  terminating.value = true;
  terminationError.value = null;
  clearPoll();
  try {
    const next = await api.terminateAutomationReferenceRepository(props.appId, current.repositoryId, current.generation);
    if (progress.value?.token !== current.token) return;
    replaceRepository(next);
    emit("operation-state", { open: true, canClose: true });
  } catch (error) {
    if (progress.value?.token === current.token) {
      terminationError.value = notice(error, "终止自动化引用操作失败");
      pollOperation(current.token);
    }
  } finally {
    terminating.value = false;
  }
}

function retryOperation() {
  const repository = progressRepository.value;
  const retryAction = progress.value?.retryAction;
  closeProgress();
  if (!repository || !retryAction) return;
  if (retryAction === "VERIFY_POINTERS") void verifyPointers(repository);
  else if (retryAction === "SYNCHRONIZE") void synchronizeRepository(repository);
  else void saveConfiguration();
}

function closeProgress() {
  if (!progressCanClose.value) return;
  clearPoll();
  progress.value = null;
  terminationError.value = null;
  emit("operation-state", { open: false, canClose: true });
}

watch(
  () => props.open,
  (open) => {
    if (open) void loadRepositories();
    else {
      viewToken += 1;
      selectionToken += 1;
      clearPoll();
      progress.value = null;
      emit("operation-state", { open: false, canClose: true });
    }
  },
  { immediate: true }
);

onBeforeUnmount(() => {
  viewToken += 1;
  selectionToken += 1;
  clearPoll();
  emit("operation-state", { open: false, canClose: true });
});
</script>

<template>
  <div class="reference-dialog-body reference-automation-layout">
    <aside class="reference-repository-column" aria-label="自动化代码库">
      <div class="reference-column-heading"><span>自动化代码库</span><Spinner v-if="loading" class="h-3.5 w-3.5" /></div>
      <div v-if="loadError" class="reference-state is-error" role="alert"><span>{{ loadError.message }}</span><code v-if="loadError.traceId">traceId: {{ loadError.traceId }}</code><button type="button" class="reference-inline-action" @click="loadRepositories">重试</button></div>
      <div v-else-if="loading" class="reference-state" role="status">正在加载自动化代码库…</div>
      <div v-else-if="repositories.length === 0" class="reference-state">当前应用未关联自动化代码库。</div>
      <div v-else class="reference-repository-list">
        <article v-for="repository in repositories" :key="repository.repositoryId" class="reference-repository-card" :class="{ 'is-selected': selectedRepositoryId === repository.repositoryId }">
          <button type="button" class="reference-repository-main" :aria-label="`选择${repository.name}`" :aria-pressed="selectedRepositoryId === repository.repositoryId" :disabled="saving" @click="selectRepository(repository)">
            <LibraryBig class="h-4 w-4 shrink-0" /><span class="min-w-0"><strong>{{ repository.name }}（{{ repository.englishName }}）</strong><small :title="repository.gitUrl">{{ repository.gitUrl }}</small></span><span class="reference-status">{{ repository.status }}</span>
          </button>
          <div class="reference-repository-meta"><span>{{ repository.readyServerCount }}/{{ repository.targetServerCount }} 台就绪</span><button v-if="canManage" type="button" class="reference-inline-action" :disabled="saving || ACTIVE_STATUSES.has(repository.status)" @click="openBranchPopover(repository)">{{ repository.currentConfiguration ? "切换分支" : "初始化" }}</button></div>
          <div v-if="branchPopoverRepositoryId === repository.repositoryId" class="reference-branch-popover" role="dialog" :aria-label="repository.currentConfiguration ? `切换${repository.name}分支` : `初始化${repository.name}`">
            <div class="reference-branch-title"><GitBranch class="h-3.5 w-3.5" />{{ repository.currentConfiguration ? "选择目标分支" : "选择初始化分支" }}</div>
            <div v-if="branchesLoading" class="reference-compact-state">正在加载分支…</div>
            <div v-else-if="branchError" class="reference-compact-state is-error">{{ branchError.message }}<code v-if="branchError.traceId">traceId: {{ branchError.traceId }}</code></div>
            <template v-else>
              <select v-model="selectedBranch" class="reference-select" :aria-label="repository.currentConfiguration ? '目标分支' : '初始化分支'"><option v-for="branch in branches" :key="branch" :value="branch">{{ branch }}</option></select>
              <div class="reference-popover-actions"><Button size="sm" variant="ghost" @click="closeBranchPopover">取消</Button><Button size="sm" :disabled="!selectedBranch" @click="applyBranchDraft(repository)">继续</Button></div>
            </template>
          </div>
          <div v-if="repository.message" class="reference-repository-error" role="alert">{{ repository.message }}</div>
          <code v-if="repository.traceId && repository.message" class="reference-trace">traceId: {{ repository.traceId }}</code>
          <ul v-if="repository.servers.length" class="reference-server-list"><li v-for="server in repository.servers" :key="server.linuxServerId"><span>{{ server.linuxServerId }}</span><span>{{ server.status }}</span><small v-if="server.error">{{ server.error }}</small></li></ul>
        </article>
      </div>
    </aside>

    <main class="reference-configuration-column">
      <div v-if="!selectedRepository" class="reference-state is-centered">选择一个自动化代码库查看当前配置；点选版本库不会创建或更新共享副本。</div>
      <template v-else>
        <div class="reference-selected-heading">
          <div><strong>{{ selectedRepository.name }}</strong><span>{{ draft.branch ? `${draft.branch} · ${draft.directoryPath || "."}` : "未选择分支" }}</span></div>
          <div class="reference-selected-actions">
            <div class="reference-repository-path" :title="displayedLogicalPath"><span>逻辑路径</span><code>{{ displayedLogicalPath }}</code></div>
            <Button v-if="canManage && selectedConfiguration" size="sm" variant="ghost" :disabled="saving || ACTIVE_STATUSES.has(selectedRepository.status)" :aria-label="`更新${selectedRepository.name}副本`" @click="synchronizeRepository(selectedRepository)"><RefreshCw class="h-3.5 w-3.5" />更新副本</Button>
            <Button v-if="canManage && selectedConfiguration" size="sm" variant="ghost" data-reference-verify="true" :disabled="saving || ACTIVE_STATUSES.has(selectedRepository.status)" :aria-label="`刷新${selectedRepository.name} Git 指针`" @click="verifyPointers(selectedRepository)"><RefreshCw class="h-3.5 w-3.5" />刷新 Git 指针</Button>
            <RefreshCw v-if="ACTIVE_STATUSES.has(selectedRepository.status)" class="h-4 w-4 animate-spin" />
          </div>
        </div>

        <section v-if="targetConfiguration" class="reference-pointer-panel" aria-label="服务器 Git 指针">
          <div class="reference-pointer-target"><span>目标 Git 指针</span><strong>{{ targetConfiguration.branch }}</strong><code :title="targetConfiguration.targetCommitHash">{{ shortCommit(targetConfiguration.targetCommitHash) }}</code><button type="button" class="reference-copy-action" aria-label="复制目标 Git HEAD" @click="copyTextToClipboard(targetConfiguration.targetCommitHash)"><Copy class="h-3 w-3" /></button></div>
          <div v-if="selectedRepository.servers.length === 0" class="reference-compact-state">暂无服务器副本。</div>
          <div v-else class="reference-pointer-table-wrap"><table class="reference-pointer-table"><thead><tr><th>服务器</th><th>状态</th><th>实际分支</th><th>实际 HEAD</th><th>目标</th><th>最近同步 / 核验</th></tr></thead><tbody>
            <tr v-for="server in selectedRepository.servers" :key="server.linuxServerId"><td><strong>{{ server.linuxServerId }}</strong><small :class="server.online ? 'is-online' : 'is-offline'">{{ server.online ? "在线" : "离线 · 非实时" }}</small></td><td><span class="reference-pointer-status">{{ server.status }}</span></td><td><code>{{ server.currentBranch || "—" }}</code></td><td><span class="reference-commit-cell"><code :title="server.currentCommitHash || undefined">{{ shortCommit(server.currentCommitHash) }}</code><button v-if="server.currentCommitHash" type="button" class="reference-copy-action" :aria-label="`复制 ${server.linuxServerId} Git HEAD`" @click="copyTextToClipboard(server.currentCommitHash)"><Copy class="h-3 w-3" /></button></span></td><td><span class="reference-pointer-match" :class="{ 'is-match': server.matchesTarget === true, 'is-mismatch': server.matchesTarget === false }"><Check v-if="server.matchesTarget" class="h-3 w-3" />{{ server.matchesTarget === true ? "一致" : server.matchesTarget === false ? "不一致" : "未核验" }}</span></td><td><small>同步 <time>{{ formattedTime(server.syncedAt) }}</time></small><small>核验 <time>{{ formattedTime(server.verifiedAt) }}</time></small><small v-if="server.error" class="is-error">{{ server.error }}</small></td></tr>
          </tbody></table></div>
        </section>

        <div v-if="actionError" class="reference-state is-error" role="alert"><span>{{ actionError.message }}</span><code v-if="actionError.traceId">traceId: {{ actionError.traceId }}</code></div>
        <div v-if="!draft.branch" class="reference-state is-centered">先在左侧选择分支初始化该自动化代码库；选择分支只更新草稿，保存后才同步并生效。</div>
        <div v-else-if="ACTIVE_STATUSES.has(selectedRepository.status)" class="reference-state is-centered" role="status">正在同步所有在线服务器副本，全部就绪后自动整体生效…</div>
        <div v-else class="reference-ready-layout">
          <section class="reference-tree-panel" aria-label="引用目录树">
            <div class="reference-panel-title">目录</div>
            <button type="button" class="reference-root-directory" :class="{ 'is-selected': draft.directoryPath === '' }" :disabled="!canManage || saving" aria-label="选择仓库根目录" @click="selectDirectory('')"><Folder class="h-3.5 w-3.5" />仓库根目录（.）</button>
            <div v-if="branchesLoading || treeLoadingPaths.has('')" class="reference-compact-state">正在读取目录…</div>
            <div v-else-if="branchError" class="reference-compact-state is-error">{{ branchError.message }}</div>
            <div v-else-if="treeErrors['']" class="reference-compact-state is-error">{{ treeErrors['']?.message }}<button type="button" class="reference-inline-action" @click="loadTreeLevel('')">重试</button></div>
            <div v-else-if="visibleTreeNodes.length === 0" class="reference-compact-state">当前分支没有子目录，可选择仓库根目录。</div>
            <div v-else class="reference-tree" role="list">
              <div v-for="node in visibleTreeNodes" :key="node.path" class="reference-tree-node" role="listitem">
                <div class="reference-tree-row" :class="{ 'is-reference-selectable': node.type === 'directory', 'is-selected': draft.directoryPath === node.path }" :style="{ paddingLeft: `${8 + node.depth * 16}px` }"><button v-if="node.type === 'directory'" type="button" class="reference-tree-toggle" :aria-label="`${expandedPaths.has(node.path) ? '收起' : '展开'} ${node.name}`" @click="toggleDirectory(node)"><ChevronDown v-if="expandedPaths.has(node.path)" class="h-3.5 w-3.5" /><ChevronRight v-else class="h-3.5 w-3.5" /></button><span v-else class="reference-tree-spacer" /><Folder v-if="node.type === 'directory'" class="reference-tree-icon" /><File v-else class="reference-tree-icon" /><button v-if="node.type === 'directory'" type="button" class="reference-tree-name" :aria-label="`选择目录 ${node.path}`" :disabled="!canManage || saving" @click="selectDirectory(node.path)">{{ node.name }}</button><span v-else class="reference-tree-name">{{ node.name }}</span><Spinner v-if="treeLoadingPaths.has(node.path)" class="ml-auto h-3 w-3" /></div>
                <div v-if="treeErrors[node.path]" class="reference-tree-level-error" :style="{ paddingLeft: `${28 + node.depth * 16}px` }">{{ treeErrors[node.path]?.message }}<button type="button" class="reference-inline-action" @click="loadTreeLevel(node.path)">重试</button></div>
              </div>
            </div>
          </section>

          <section class="reference-form-panel" aria-label="自动化引用配置">
            <div class="reference-panel-title">配置</div>
            <form class="reference-form" @submit.prevent="saveConfiguration">
              <label><span>参考别名（alias）</span><Input :model-value="selectedConfiguration?.alias || `automation-${selectedRepository.englishName}`" readonly aria-label="参考别名（alias）" /></label>
              <label><span>路径（path）</span><Input :model-value="displayedLogicalPath" readonly aria-label="路径（path）" /></label>
              <label><span>目录名称（sdd-folder-name）</span><Input :model-value="directoryName(draft.directoryPath)" readonly aria-label="目录名称（sdd-folder-name）" /></label>
              <label><span>是否合并（merge）</span><Input model-value="否" readonly aria-label="是否合并（merge）" /></label>
              <label><span>描述（description） <b aria-hidden="true">*</b></span><Textarea v-model="draft.description" rows="4" aria-label="描述（description）" :readonly="!canManage" :disabled="saving" placeholder="说明何时使用这组只读自动化资料" /></label>
              <div v-if="configNotice" class="reference-form-notice" :class="`is-${configNotice.kind}`" role="status"><span>{{ configNotice.message }}</span><code v-if="configNotice.traceId">traceId: {{ configNotice.traceId }}</code></div>
              <div v-if="canManage" class="reference-form-actions"><Button type="submit" :disabled="!canSave" aria-label="保存自动化配置并生效"><Spinner v-if="saving" class="mr-1.5 h-3.5 w-3.5" />{{ saving ? "保存中…" : selectedConfiguration ? "更新" : "保存" }}</Button></div>
            </form>
          </section>
        </div>
      </template>
    </main>

    <RepositoryOperationProgressDialog v-if="progress" :open="true" :operation="progress.operation" :request-state="progress.requestState" :target="progressTarget" :accepted-target="acceptedProgressTarget" :error="progress.error" :polling-error="progress.pollingError" :termination-error="terminationError" :can-close="progressCanClose" :can-retry="progressCanRetry" :can-terminate="progressCanTerminate" :terminating="terminating" resource-label="自动化代码库" replica-label="自动化" @close="closeProgress" @retry="retryOperation" @terminate="terminateOperation" />
  </div>
</template>

<style scoped>
.reference-automation-layout {
  --reference-folder-accent: #7c3aed;
  --automation-reference-accent: #7c3aed;
}
.reference-dialog-body { display: grid; min-height: 0; flex: 1; grid-template-columns: minmax(290px, 34%) minmax(0, 1fr); }
.reference-repository-column, .reference-configuration-column { min-height: 0; overflow: auto; }
.reference-repository-column { border-right: 1px solid var(--ta-border); background: var(--ta-panel-2); }
.reference-column-heading, .reference-selected-heading, .reference-panel-title { display: flex; align-items: center; justify-content: space-between; color: var(--ta-muted); font-size: 11px; font-weight: 600; letter-spacing: .04em; text-transform: uppercase; }
.reference-column-heading { position: sticky; top: 0; z-index: 2; height: 34px; border-bottom: 1px solid var(--ta-border); padding: 0 12px; background: var(--ta-panel-2); }
.reference-repository-list { display: flex; flex-direction: column; gap: 6px; padding: 8px; }
.reference-repository-card { position: relative; overflow: hidden; border: 1px solid var(--ta-border); border-radius: 7px; background: var(--ta-surface); }
.reference-repository-card.is-selected { border-color: #c4b5fd; box-shadow: inset 3px 0 0 var(--automation-reference-accent); }
.reference-repository-main { display: grid; width: 100%; grid-template-columns: auto minmax(0,1fr) auto; align-items: center; gap: 8px; border: 0; padding: 9px 10px 6px; background: transparent; color: var(--ta-text); text-align: left; cursor: pointer; }
.reference-repository-main > svg { color: var(--automation-reference-accent); }
.reference-repository-main:hover { background: var(--ta-hover); }
.reference-repository-main strong, .reference-repository-main small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.reference-repository-main strong { font-size: 12px; font-weight: 600; }
.reference-repository-main small { margin-top: 3px; color: var(--ta-muted); font-family: "Geist Mono", monospace; font-size: 10px; }
.reference-status { color: var(--ta-muted); font-family: "Geist Mono", monospace; font-size: 10px; }
.reference-repository-meta { display: flex; align-items: center; justify-content: space-between; padding: 0 10px 7px 34px; color: var(--ta-muted); font-size: 10px; }
.reference-inline-action { border: 0; padding: 0; background: transparent; color: var(--ta-text); font-size: 11px; font-weight: 600; text-decoration: underline; text-underline-offset: 2px; cursor: pointer; }
.reference-inline-action:disabled { opacity: .45; cursor: default; }
.reference-branch-popover { margin: 0 8px 8px; border: 1px solid var(--ta-border-strong); border-radius: 6px; padding: 8px; background: var(--ta-panel); box-shadow: 0 8px 20px rgba(15,23,42,.12); }
.reference-branch-title { display: flex; align-items: center; gap: 5px; margin-bottom: 7px; font-size: 11px; font-weight: 600; }
.reference-popover-actions { display: flex; justify-content: flex-end; gap: 6px; margin-top: 8px; }
.reference-server-list { margin: 0; border-top: 1px solid var(--ta-border); padding: 5px 10px 7px 34px; list-style: none; }
.reference-server-list li { display: grid; grid-template-columns: minmax(0,1fr) auto; gap: 4px 8px; padding: 2px 0; color: var(--ta-muted); font-family: "Geist Mono", monospace; font-size: 10px; }
.reference-server-list small { grid-column: 1/-1; color: var(--ta-error); }
.reference-repository-error, .reference-trace { display: block; padding: 0 10px 4px 34px; color: var(--ta-error); font-size: 10px; }
.reference-trace { color: var(--ta-muted); font-family: "Geist Mono", monospace; }
.reference-configuration-column { display: flex; flex-direction: column; background: var(--ta-panel); }
.reference-selected-heading { min-height: 44px; flex-shrink: 0; border-bottom: 1px solid var(--ta-border); padding: 0 14px; background: var(--ta-panel-2); color: var(--ta-text); letter-spacing: normal; text-transform: none; }
.reference-selected-heading strong, .reference-selected-heading span { display: block; }
.reference-selected-heading strong { font-size: 12px; }
.reference-selected-heading span { margin-top: 2px; color: var(--ta-muted); font-family: "Geist Mono", monospace; font-size: 10px; font-weight: 400; }
.reference-selected-actions, .reference-commit-cell, .reference-pointer-match, .reference-pointer-target { display: flex; align-items: center; }
.reference-selected-actions { min-width: 0; max-width: 72%; gap: 6px; }
.reference-repository-path { display: flex; min-width: 0; max-width: 430px; align-items: center; gap: 6px; border-right: 1px solid var(--ta-border); padding-right: 10px; }
.reference-repository-path span { flex-shrink: 0; margin: 0; color: var(--ta-muted); font-family: inherit; font-size: 10px; }
.reference-repository-path code { min-width: 0; overflow: hidden; color: var(--ta-text); font-family: "Geist Mono", monospace; font-size: 10px; font-weight: 400; text-overflow: ellipsis; white-space: nowrap; }
.reference-pointer-panel { flex-shrink: 0; border-bottom: 1px solid var(--ta-border); background: var(--ta-surface); }
.reference-pointer-target { min-height: 34px; gap: 8px; border-bottom: 1px solid var(--ta-border); padding: 0 12px; color: var(--ta-muted); font-size: 10px; }
.reference-pointer-target strong, .reference-pointer-target code, .reference-pointer-table code, .reference-pointer-table time { color: var(--ta-text); font-family: "Geist Mono", monospace; font-size: 10px; }
.reference-pointer-table-wrap { max-height: 148px; overflow: auto; }
.reference-pointer-table { width: 100%; border-collapse: collapse; font-size: 10px; text-align: left; }
.reference-pointer-table th, .reference-pointer-table td { border-bottom: 1px solid var(--ta-border); padding: 5px 8px; vertical-align: top; white-space: nowrap; }
.reference-pointer-table th { position: sticky; top: 0; z-index: 1; background: var(--ta-panel-2); color: var(--ta-muted); font-weight: 600; }
.reference-pointer-table td:first-child strong, .reference-pointer-table td:first-child small, .reference-pointer-table td:last-child small { display: block; }
.reference-pointer-table small { margin-top: 2px; color: var(--ta-muted); font-size: 9px; }
.reference-pointer-table small.is-online, .reference-pointer-match.is-match { color: var(--ta-ok); }
.reference-pointer-table small.is-offline { color: var(--ta-muted); }
.reference-pointer-table small.is-error, .reference-pointer-match.is-mismatch { color: var(--ta-error); }
.reference-pointer-status { color: var(--ta-muted); font-family: "Geist Mono", monospace; }
.reference-commit-cell, .reference-pointer-match { gap: 3px; }
.reference-copy-action { display: inline-grid; width: 18px; height: 18px; place-items: center; border: 0; border-radius: 3px; padding: 0; background: transparent; color: var(--ta-muted); cursor: pointer; }
.reference-copy-action:hover, .reference-copy-action:focus-visible { background: var(--ta-hover); color: var(--ta-text); }
.reference-ready-layout { display: grid; min-height: 0; flex: 1; grid-template-columns: minmax(210px,42%) minmax(280px,1fr); }
.reference-tree-panel, .reference-form-panel { min-height: 0; overflow: auto; }
.reference-tree-panel { border-right: 1px solid var(--ta-border); background: var(--ta-tree-bg); font-family: var(--ta-tree-font-family); font-size: var(--ta-tree-font-size); }
.reference-form-panel { background: var(--ta-panel-2); }
.reference-panel-title { position: sticky; top: 0; z-index: 1; height: 32px; border-bottom: 1px solid var(--ta-border); padding: 0 10px; background: inherit; }
.reference-root-directory { display: flex; width: 100%; height: var(--ta-tree-row-height); align-items: center; gap: 5px; border: 0; padding: 0 10px; background: transparent; color: var(--reference-folder-accent); font: inherit; font-weight: 600; cursor: pointer; }
.reference-root-directory:hover, .reference-root-directory.is-selected { background: var(--ta-tree-hover); }
.reference-tree { padding: 5px 0; }
.reference-tree-row { display: flex; height: var(--ta-tree-row-height); align-items: center; gap: 4px; padding-right: 8px; color: var(--ta-tree-text); }
.reference-tree-row:hover, .reference-tree-row.is-selected { background: var(--ta-tree-hover); }
.reference-tree-toggle { display: inline-grid; width: 16px; height: 18px; flex-shrink: 0; place-items: center; border: 0; padding: 0; background: transparent; color: var(--ta-tree-muted); cursor: pointer; }
.reference-tree-spacer { width: 16px; flex-shrink: 0; }
.reference-tree-icon { width: 14px; height: 14px; flex-shrink: 0; color: var(--ta-tree-muted); }
.reference-tree-name { min-width: 0; overflow: hidden; border: 0; padding: 0; background: transparent; color: inherit; font: inherit; line-height: var(--ta-tree-row-height); text-align: left; text-overflow: ellipsis; white-space: nowrap; }
.reference-tree-row.is-reference-selectable .reference-tree-icon, .reference-tree-row.is-reference-selectable .reference-tree-name { color: var(--reference-folder-accent); }
.reference-tree-row.is-reference-selectable .reference-tree-name { font-weight: 600; cursor: pointer; }
.reference-tree-level-error { display: flex; flex-wrap: wrap; align-items: center; gap: 4px 8px; padding: 4px 8px 6px; color: var(--ta-error); font-size: 10px; }
.reference-form { display: flex; flex-direction: column; gap: 10px; padding: 14px; }
.reference-form label > span { display: block; margin-bottom: 5px; color: var(--ta-muted); font-family: "Geist Mono", monospace; font-size: 10px; }
.reference-form label b { color: var(--ta-error); }
.reference-select { width: 100%; height: 32px; border: 1px solid var(--ta-border); border-radius: 5px; padding: 0 8px; outline: none; background: var(--ta-surface); color: var(--ta-text); font-size: 12px; }
.reference-state, .reference-compact-state { display: flex; flex-direction: column; gap: 4px; padding: 16px 12px; color: var(--ta-muted); font-size: 12px; }
.reference-state.is-centered, .reference-compact-state.is-centered { min-height: 120px; align-items: center; justify-content: center; text-align: center; }
.reference-state.is-error, .reference-compact-state.is-error, .reference-form-notice.is-error { color: var(--ta-error); }
.reference-state code, .reference-compact-state code, .reference-form-notice code { font-family: "Geist Mono", monospace; font-size: 10px; }
.reference-form-notice { display: flex; flex-direction: column; gap: 3px; border: 1px solid var(--ta-border); border-radius: 5px; padding: 7px 8px; color: var(--ta-muted); font-size: 11px; }
.reference-form-notice.is-success { color: var(--ta-ok); }
.reference-form-actions { display: flex; justify-content: flex-end; gap: 6px; margin-top: 8px; }
@media (max-width: 780px) {
  .reference-dialog-body { grid-template-columns: 1fr; overflow: auto; }
  .reference-repository-column { max-height: 38vh; border-right: 0; border-bottom: 1px solid var(--ta-border); }
  .reference-ready-layout { grid-template-columns: 1fr; }
  .reference-tree-panel { min-height: 180px; border-right: 0; border-bottom: 1px solid var(--ta-border); }
  .reference-selected-heading { min-height: 72px; align-items: flex-start; flex-direction: column; justify-content: center; gap: 6px; padding-top: 7px; padding-bottom: 7px; }
  .reference-selected-actions { width: 100%; max-width: none; }
  .reference-repository-path { max-width: none; flex: 1; }
}
</style>
