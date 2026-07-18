<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import {
  BackendApiError,
  type BackendApiClient,
  type ReferenceRepositoryStatus,
  type ReferenceRepositoryTreeNode
} from "@test-agent/backend-api";
import { Button, Input, Spinner, Textarea } from "@test-agent/ui-kit";
import { ChevronDown, ChevronRight, File, Folder, GitBranch, LibraryBig, RefreshCw, X } from "lucide-vue-next";
import {
  ReferenceConfigValidationError,
  inspectReferenceConfig,
  patchReferenceConfig,
  type ReferenceConfigInspection,
  type ReferenceConfigTarget,
  type ReferenceConfigValue
} from "./reference-config-jsonc";

const OPENCODE_CONFIG_PATH = ".opencode/opencode.jsonc";
const POLL_INTERVAL_MS = 2_000;
const ACTIVE_STATUSES = new Set(["INITIALIZING", "SYNCHRONIZING", "VERIFYING"]);

const props = defineProps<{
  open: boolean;
  appId: string;
  workspaceId: string;
}>();

const emit = defineEmits<{ close: []; saved: [] }>();
const api = inject<BackendApiClient>("api")!;

type Notice = { message: string; traceId?: string };
type VisibleTreeNode = ReferenceRepositoryTreeNode & { depth: number; parentPath: string };

const repositories = ref<ReferenceRepositoryStatus[]>([]);
const listLoading = ref(false);
const listError = ref<Notice | null>(null);
const selectedRepositoryId = ref<string | null>(null);
const selectionBusy = ref(false);
const actionError = ref<Notice | null>(null);
const treeByParent = ref<Record<string, ReferenceRepositoryTreeNode[]>>({});
const treeLoadingPaths = ref<Set<string>>(new Set());
const treeErrors = ref<Record<string, Notice>>({});
const expandedPaths = ref<Set<string>>(new Set());
const selectedFolderPath = ref<string | null>(null);
const configLoading = ref(false);
const configSaving = ref(false);
const configMode = ref<ReferenceConfigInspection["mode"] | null>(null);
const configTarget = ref<ReferenceConfigTarget | null>(null);
const form = ref<ReferenceConfigValue>({ path: "", merge: true, sddFolderName: "", description: "" });
const baseline = ref<ReferenceConfigValue | null>(null);
const configNotice = ref<(Notice & { kind: "error" | "success" }) | null>(null);
const dialogElement = ref<HTMLElement | null>(null);

const branchPopoverRepositoryId = ref<string | null>(null);
const branches = ref<string[]>([]);
const selectedBranch = ref("");
const branchesLoading = ref(false);
const branchError = ref<Notice | null>(null);

let dialogGeneration = 0;
let selectionGeneration = 0;
let pollTimer: ReturnType<typeof setTimeout> | null = null;
let restoreFocusTo: HTMLElement | null = null;

/** v-if 创建弹窗内容后直接聚焦关闭按钮，避免依赖父组件更新时序。 */
const vInitialFocus = {
  mounted(element: HTMLElement) {
    element.focus();
  }
};

const selectedRepository = computed(() =>
  repositories.value.find((repository) => repository.repositoryId === selectedRepositoryId.value) ?? null
);

const visibleTreeNodes = computed<VisibleTreeNode[]>(() => {
  const result: VisibleTreeNode[] = [];
  const append = (parentPath: string, depth: number) => {
    for (const node of treeByParent.value[parentPath] ?? []) {
      result.push({ ...node, depth, parentPath });
      if (node.directory && expandedPaths.value.has(node.path)) append(node.path, depth + 1);
    }
  };
  append("", 0);
  return result;
});

const normalizedForm = computed<ReferenceConfigValue>(() => ({
  path: form.value.path,
  merge: form.value.merge,
  sddFolderName: form.value.sddFolderName,
  description: form.value.description.trim()
}));

const mergeModel = computed({
  get: () => String(form.value.merge),
  set: (value: string) => {
    form.value = { ...form.value, merge: value === "true" };
  }
});

const formModified = computed(() => {
  const initial = baseline.value;
  if (!initial) return false;
  return form.value.path !== initial.path
    || form.value.merge !== initial.merge
    || form.value.sddFolderName !== initial.sddFolderName
    || form.value.description.trim() !== initial.description.trim();
});

const submitEnabled = computed(() => {
  if (!configTarget.value || configLoading.value || configSaving.value || !normalizedForm.value.description) return false;
  return configMode.value === "create" || (configMode.value === "update" && formModified.value);
});

function notice(error: unknown, fallback: string): Notice {
  if (error instanceof BackendApiError) {
    return { message: error.message || fallback, traceId: error.traceId || undefined };
  }
  if (error instanceof ReferenceConfigValidationError) {
    return { message: error.message };
  }
  return { message: error instanceof Error ? error.message : fallback };
}

function isFileMissing(error: unknown) {
  return error instanceof BackendApiError && (error.code === "FILE_NOT_FOUND" || error.code === "NOT_FOUND");
}

function replaceRepository(next: ReferenceRepositoryStatus) {
  const index = repositories.value.findIndex((item) => item.repositoryId === next.repositoryId);
  if (index < 0) return;
  repositories.value = repositories.value.map((item, itemIndex) => itemIndex === index ? next : item);
}

function clearPoll() {
  if (pollTimer !== null) {
    clearTimeout(pollTimer);
    pollTimer = null;
  }
}

function resetSelectionState() {
  selectionGeneration++;
  clearPoll();
  selectionBusy.value = false;
  configLoading.value = false;
  configSaving.value = false;
  actionError.value = null;
  treeByParent.value = {};
  treeLoadingPaths.value = new Set();
  treeErrors.value = {};
  expandedPaths.value = new Set();
  selectedFolderPath.value = null;
  configTarget.value = null;
  configMode.value = null;
  baseline.value = null;
  configNotice.value = null;
  closeBranchPopover();
}

function contextIsCurrent(dialogToken: number, selectionToken?: number, repositoryId?: string) {
  return props.open
    && dialogToken === dialogGeneration
    && (selectionToken === undefined || selectionToken === selectionGeneration)
    && (repositoryId === undefined || repositoryId === selectedRepositoryId.value);
}

async function loadRepositories(dialogToken: number) {
  listLoading.value = true;
  listError.value = null;
  try {
    const result = await api.listReferenceRepositories(props.appId);
    if (!contextIsCurrent(dialogToken)) return;
    repositories.value = result;
  } catch (error) {
    if (contextIsCurrent(dialogToken)) listError.value = notice(error, "加载引用资产库失败");
  } finally {
    if (contextIsCurrent(dialogToken)) listLoading.value = false;
  }
}

function retryLoadRepositories() {
  if (listLoading.value) return;
  void loadRepositories(dialogGeneration);
}

function scheduleStatusPoll(repositoryId: string, dialogToken: number, selectionToken: number) {
  clearPoll();
  pollTimer = setTimeout(async () => {
    pollTimer = null;
    if (!contextIsCurrent(dialogToken, selectionToken, repositoryId)) return;
    try {
      const next = await api.getReferenceRepositoryStatus(props.appId, repositoryId);
      if (!contextIsCurrent(dialogToken, selectionToken, repositoryId)) return;
      actionError.value = null;
      replaceRepository(next);
      if (next.status === "READY") {
        await loadTreeLevel("", dialogToken, selectionToken, repositoryId);
        return;
      }
      if (ACTIVE_STATUSES.has(next.status)) scheduleStatusPoll(repositoryId, dialogToken, selectionToken);
    } catch (error) {
      if (!contextIsCurrent(dialogToken, selectionToken, repositoryId)) return;
      actionError.value = notice(error, "读取引用资产库状态失败");
      scheduleStatusPoll(repositoryId, dialogToken, selectionToken);
    }
  }, POLL_INTERVAL_MS);
}

async function applyOperationStatus(
  next: ReferenceRepositoryStatus,
  dialogToken: number,
  selectionToken: number
) {
  if (!contextIsCurrent(dialogToken, selectionToken, next.repositoryId)) return;
  replaceRepository(next);
  if (next.status === "READY") {
    await loadTreeLevel("", dialogToken, selectionToken, next.repositoryId);
  } else if (ACTIVE_STATUSES.has(next.status)) {
    scheduleStatusPoll(next.repositoryId, dialogToken, selectionToken);
  }
}

async function selectRepository(repository: ReferenceRepositoryStatus, synchronize = true) {
  resetSelectionState();
  selectedRepositoryId.value = repository.repositoryId;
  const dialogToken = dialogGeneration;
  const selectionToken = selectionGeneration;
  if (!synchronize || !repository.initialized) return;
  selectionBusy.value = true;
  try {
    const next = await api.synchronizeReferenceRepository(props.appId, repository.repositoryId);
    await applyOperationStatus(next, dialogToken, selectionToken);
  } catch (error) {
    if (contextIsCurrent(dialogToken, selectionToken, repository.repositoryId)) {
      actionError.value = notice(error, "同步引用资产库失败");
    }
  } finally {
    if (contextIsCurrent(dialogToken, selectionToken, repository.repositoryId)) selectionBusy.value = false;
  }
}

async function openBranchPopover(repository: ReferenceRepositoryStatus) {
  await selectRepository(repository, false);
  const dialogToken = dialogGeneration;
  const selectionToken = selectionGeneration;
  branchPopoverRepositoryId.value = repository.repositoryId;
  branches.value = [];
  selectedBranch.value = "";
  branchesLoading.value = true;
  branchError.value = null;
  try {
    const result = await api.listRepositoryBranches(repository.repositoryId);
    if (!contextIsCurrent(dialogToken, selectionToken, repository.repositoryId)) return;
    branches.value = result;
    selectedBranch.value = result[0] ?? "";
  } catch (error) {
    if (contextIsCurrent(dialogToken, selectionToken, repository.repositoryId)) {
      branchError.value = notice(error, "加载分支失败");
    }
  } finally {
    if (contextIsCurrent(dialogToken, selectionToken, repository.repositoryId)) branchesLoading.value = false;
  }
}

function closeBranchPopover() {
  branchPopoverRepositoryId.value = null;
  branches.value = [];
  selectedBranch.value = "";
  branchesLoading.value = false;
  branchError.value = null;
}

async function confirmInitialize(repository: ReferenceRepositoryStatus) {
  if (!selectedBranch.value) return;
  const dialogToken = dialogGeneration;
  const selectionToken = selectionGeneration;
  selectionBusy.value = true;
  actionError.value = null;
  try {
    const next = await api.initializeReferenceRepository(props.appId, repository.repositoryId, selectedBranch.value);
    if (!contextIsCurrent(dialogToken, selectionToken, repository.repositoryId)) return;
    closeBranchPopover();
    await applyOperationStatus(next, dialogToken, selectionToken);
  } catch (error) {
    if (contextIsCurrent(dialogToken, selectionToken, repository.repositoryId)) {
      branchError.value = notice(error, "初始化引用资产库失败");
    }
  } finally {
    if (contextIsCurrent(dialogToken, selectionToken, repository.repositoryId)) selectionBusy.value = false;
  }
}

async function loadTreeLevel(path: string, dialogToken: number, selectionToken: number, repositoryId: string) {
  if (!contextIsCurrent(dialogToken, selectionToken, repositoryId)) return;
  treeLoadingPaths.value = new Set(treeLoadingPaths.value).add(path);
  const nextErrors = { ...treeErrors.value };
  delete nextErrors[path];
  treeErrors.value = nextErrors;
  try {
    const nodes = await api.listReferenceRepositoryTree(props.appId, repositoryId, path);
    if (!contextIsCurrent(dialogToken, selectionToken, repositoryId)) return;
    treeByParent.value = { ...treeByParent.value, [path]: nodes };
  } catch (error) {
    if (contextIsCurrent(dialogToken, selectionToken, repositoryId)) {
      treeErrors.value = { ...treeErrors.value, [path]: notice(error, "读取引用目录失败") };
    }
  } finally {
    if (contextIsCurrent(dialogToken, selectionToken, repositoryId)) {
      const loading = new Set(treeLoadingPaths.value);
      loading.delete(path);
      treeLoadingPaths.value = loading;
    }
  }
}

async function toggleDirectory(node: VisibleTreeNode) {
  if (!node.directory) return;
  const expanded = new Set(expandedPaths.value);
  if (expanded.has(node.path)) {
    expanded.delete(node.path);
    expandedPaths.value = expanded;
    return;
  }
  expanded.add(node.path);
  expandedPaths.value = expanded;
  if (treeByParent.value[node.path] || !selectedRepositoryId.value) return;
  await loadTreeLevel(node.path, dialogGeneration, selectionGeneration, selectedRepositoryId.value);
}

async function retryTreeLevel(path: string) {
  if (!selectedRepositoryId.value) return;
  await loadTreeLevel(path, dialogGeneration, selectionGeneration, selectedRepositoryId.value);
}

function nodeSelectable(node: VisibleTreeNode) {
  return node.depth === 0 && node.directory && node.highlighted && node.selectable;
}

async function readWorkspaceConfig(workspaceId = props.workspaceId): Promise<string> {
  try {
    return (await api.readFile(workspaceId, OPENCODE_CONFIG_PATH)).content;
  } catch (error) {
    if (isFileMissing(error)) return "";
    throw error;
  }
}

async function selectFolder(node: VisibleTreeNode) {
  if (!nodeSelectable(node) || !selectedRepository.value) return;
  const repository = selectedRepository.value;
  const target: ReferenceConfigTarget = {
    alias: `${node.name}-${repository.englishName}`,
    path: `{env:OPENCODE_REFERENCES_DIR}/${repository.englishName}/${node.name}`,
    folder: node.name
  };
  const dialogToken = dialogGeneration;
  const selectionToken = selectionGeneration;
  selectedFolderPath.value = node.path;
  configTarget.value = target;
  configLoading.value = true;
  configMode.value = null;
  baseline.value = null;
  configNotice.value = null;
  try {
    const content = await readWorkspaceConfig();
    if (!contextIsCurrent(dialogToken, selectionToken, repository.repositoryId) || selectedFolderPath.value !== node.path) return;
    const inspection = inspectReferenceConfig(content, target);
    configMode.value = inspection.mode;
    form.value = { ...inspection.value };
    baseline.value = { ...inspection.baseline };
  } catch (error) {
    if (contextIsCurrent(dialogToken, selectionToken, repository.repositoryId) && selectedFolderPath.value === node.path) {
      configNotice.value = { ...notice(error, "读取引用配置失败"), kind: "error" };
    }
  } finally {
    if (contextIsCurrent(dialogToken, selectionToken, repository.repositoryId) && selectedFolderPath.value === node.path) {
      configLoading.value = false;
    }
  }
}

async function submitConfig() {
  const repository = selectedRepository.value;
  const target = configTarget.value;
  if (!repository || !target || !submitEnabled.value) return;
  const dialogToken = dialogGeneration;
  const selectionToken = selectionGeneration;
  const folderPath = selectedFolderPath.value;
  const workspaceId = props.workspaceId;
  // 提交快照与响应式表单彻底解耦；迟到输入只能保持 dirty，不能改变已发出的磁盘内容。
  const submittedTarget = { ...target };
  const submitted: ReferenceConfigValue = {
    path: form.value.path,
    merge: form.value.merge,
    sddFolderName: target.folder,
    description: form.value.description.trim()
  };
  configSaving.value = true;
  configNotice.value = null;
  try {
    // 保存前重新读取磁盘正文，再由 helper 对最新 JSONC 做字段级补丁，避免覆盖并发写入的未知配置。
    const latest = await readWorkspaceConfig(workspaceId);
    if (!contextIsCurrent(dialogToken, selectionToken, repository.repositoryId) || selectedFolderPath.value !== folderPath) return;
    const output = patchReferenceConfig(latest, {
      ...submittedTarget,
      merge: submitted.merge,
      sddFolderName: submitted.sddFolderName,
      description: submitted.description
    });
    await api.writeFile(workspaceId, OPENCODE_CONFIG_PATH, output);
    if (!contextIsCurrent(dialogToken, selectionToken, repository.repositoryId) || selectedFolderPath.value !== folderPath) return;
    baseline.value = { ...submitted };
    configMode.value = "update";
    configNotice.value = { kind: "success", message: "引用配置已保存" };
    emit("saved");
  } catch (error) {
    if (contextIsCurrent(dialogToken, selectionToken, repository.repositoryId) && selectedFolderPath.value === folderPath) {
      configNotice.value = { ...notice(error, "保存引用配置失败"), kind: "error" };
    }
  } finally {
    if (contextIsCurrent(dialogToken, selectionToken, repository.repositoryId) && selectedFolderPath.value === folderPath) {
      configSaving.value = false;
    }
  }
}

function focusableElements() {
  const dialog = dialogElement.value;
  if (!dialog) return [];
  return Array.from(dialog.querySelectorAll<HTMLElement>(
    'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [href], [tabindex]:not([tabindex="-1"])'
  )).filter((element) => !element.hasAttribute("hidden"));
}

function handleWindowKeydown(event: KeyboardEvent) {
  if (!props.open) return;
  if (event.key === "Escape") {
    event.preventDefault();
    emit("close");
    return;
  }
  if (event.key !== "Tab") return;
  const focusable = focusableElements();
  const first = focusable[0];
  const last = focusable.at(-1);
  if (!first || !last) {
    event.preventDefault();
    dialogElement.value?.focus();
    return;
  }
  const active = document.activeElement;
  const focusEscaped = !dialogElement.value?.contains(active);
  if (event.shiftKey && (active === first || focusEscaped)) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && (active === last || focusEscaped)) {
    event.preventDefault();
    first.focus();
  }
}

watch(
  () => [props.open, props.appId, props.workspaceId] as const,
  ([open]) => {
    dialogGeneration++;
    resetSelectionState();
    repositories.value = [];
    listError.value = null;
    selectedRepositoryId.value = null;
    if (open) {
      void loadRepositories(dialogGeneration);
    }
  },
  { immediate: true }
);

watch(
  () => props.open,
  (open, previousOpen) => {
    if (open && !previousOpen) {
      restoreFocusTo = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    } else if (!open && previousOpen) {
      const target = restoreFocusTo;
      restoreFocusTo = null;
      void nextTick(() => target?.focus());
    }
  },
  { immediate: true }
);

onMounted(() => {
  window.addEventListener("keydown", handleWindowKeydown);
});

onBeforeUnmount(() => {
  dialogGeneration++;
  selectionGeneration++;
  clearPoll();
  window.removeEventListener("keydown", handleWindowKeydown);
});
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="reference-dialog-overlay">
      <section
        ref="dialogElement"
        class="reference-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="reference-dialog-title"
        tabindex="-1"
      >
        <header class="reference-dialog-header">
          <div>
            <h2 id="reference-dialog-title">引用配置</h2>
            <p>从应用资产库选择首层 SDD 目录，并写入当前个人工作区的 OpenCode 配置。</p>
          </div>
          <Button
            size="icon"
            variant="ghost"
            title="关闭引用配置"
            aria-label="关闭引用配置"
            data-reference-initial-focus
            v-initial-focus
            @click="emit('close')"
          >
            <X class="h-4 w-4" />
          </Button>
        </header>

        <div class="reference-dialog-body">
          <aside class="reference-repository-column" aria-label="应用资产库">
            <div class="reference-column-heading">
              <span>应用资产库</span>
              <Spinner v-if="listLoading" class="h-3.5 w-3.5" />
            </div>
            <div v-if="listError" class="reference-state is-error" role="alert">
              <span>{{ listError.message }}</span>
              <code v-if="listError.traceId">traceId: {{ listError.traceId }}</code>
              <button
                type="button"
                class="reference-inline-action"
                aria-label="重试加载应用资产库"
                :disabled="listLoading"
                @click="retryLoadRepositories"
              >
                重试
              </button>
            </div>
            <div v-else-if="listLoading" class="reference-state" role="status">正在加载引用资产库…</div>
            <div v-else-if="repositories.length === 0" class="reference-state">当前应用未关联资产版本库。</div>
            <div v-else class="reference-repository-list">
              <article
                v-for="repository in repositories"
                :key="repository.repositoryId"
                class="reference-repository-card"
                :class="{ 'is-selected': selectedRepositoryId === repository.repositoryId }"
              >
                <button
                  type="button"
                  class="reference-repository-main"
                  :aria-label="`选择${repository.name}`"
                  :aria-pressed="selectedRepositoryId === repository.repositoryId"
                  :disabled="configSaving || (selectionBusy && selectedRepositoryId === repository.repositoryId)"
                  @click="selectRepository(repository)"
                >
                  <LibraryBig class="h-4 w-4 shrink-0" />
                  <span class="min-w-0">
                    <strong>{{ repository.name }}（{{ repository.englishName }}）</strong>
                    <small :title="repository.gitUrl">{{ repository.gitUrl }}</small>
                  </span>
                  <span class="reference-status">{{ repository.status }}</span>
                </button>
                <div class="reference-repository-meta">
                  <span>{{ repository.readyServerCount }}/{{ repository.targetServerCount }} 台就绪</span>
                  <button
                    v-if="!repository.initialized"
                    type="button"
                    class="reference-inline-action"
                    :aria-label="`初始化${repository.name}`"
                    :disabled="configSaving"
                    @click="openBranchPopover(repository)"
                  >
                    初始化
                  </button>
                </div>
                <div
                  v-if="branchPopoverRepositoryId === repository.repositoryId"
                  class="reference-branch-popover"
                  role="dialog"
                  :aria-label="`初始化${repository.name}`"
                >
                  <div class="reference-branch-title"><GitBranch class="h-3.5 w-3.5" />选择初始化分支</div>
                  <div v-if="branchesLoading" class="reference-compact-state">正在加载分支…</div>
                  <div v-else-if="branchError" class="reference-compact-state is-error">
                    {{ branchError.message }}
                    <code v-if="branchError.traceId">traceId: {{ branchError.traceId }}</code>
                  </div>
                  <template v-else>
                    <select v-model="selectedBranch" aria-label="初始化分支" class="reference-select">
                      <option v-for="branch in branches" :key="branch" :value="branch">{{ branch }}</option>
                    </select>
                    <div class="reference-popover-actions">
                      <Button size="sm" variant="ghost" @click="closeBranchPopover">取消</Button>
                      <Button
                        size="sm"
                        :disabled="!selectedBranch || selectionBusy"
                        :aria-label="`确认初始化${repository.name}`"
                        @click="confirmInitialize(repository)"
                      >
                        {{ selectionBusy ? "初始化中…" : "确认初始化" }}
                      </Button>
                    </div>
                  </template>
                </div>
                <div v-if="repository.message" class="reference-repository-error" role="alert">{{ repository.message }}</div>
                <code v-if="repository.traceId && (repository.message || repository.status === 'FAILED')" class="reference-trace">
                  traceId: {{ repository.traceId }}
                </code>
                <ul v-if="repository.servers.length" class="reference-server-list">
                  <li v-for="server in repository.servers" :key="server.linuxServerId">
                    <span>{{ server.linuxServerId }}</span>
                    <span>{{ server.status }}</span>
                    <small v-if="server.error">{{ server.error }}</small>
                  </li>
                </ul>
              </article>
            </div>
          </aside>

          <main class="reference-configuration-column">
            <div v-if="!selectedRepository" class="reference-state is-centered">
              选择一个已初始化资产库开始同步。
            </div>
            <template v-else>
              <div class="reference-selected-heading">
                <div>
                  <strong>{{ selectedRepository.name }}</strong>
                  <span>{{ selectedRepository.branch || "未选择分支" }}</span>
                </div>
                <RefreshCw v-if="selectionBusy || ACTIVE_STATUSES.has(selectedRepository.status)" class="h-4 w-4 animate-spin" />
              </div>
              <div v-if="actionError" class="reference-state is-error" role="alert">
                <span>{{ actionError.message }}</span>
                <code v-if="actionError.traceId">traceId: {{ actionError.traceId }}</code>
              </div>
              <div v-if="!selectedRepository.initialized" class="reference-state is-centered">
                先在左侧选择分支并初始化该资产库。
              </div>
              <div v-else-if="selectedRepository.status !== 'READY'" class="reference-state is-centered" role="status">
                {{ selectedRepository.status === "FAILED" ? "同步失败，请重新选择仓库后重试。" : "正在同步所有服务器副本…" }}
              </div>
              <div v-else class="reference-ready-layout">
                <section class="reference-tree-panel" aria-label="引用目录树">
                  <div class="reference-panel-title">目录</div>
                  <div v-if="treeLoadingPaths.has('')" class="reference-compact-state">正在读取目录…</div>
                  <div v-else-if="treeErrors['']" class="reference-compact-state is-error">
                    {{ treeErrors[""]?.message }}
                    <code v-if="treeErrors['']?.traceId">traceId: {{ treeErrors[""]?.traceId }}</code>
                    <button type="button" class="reference-inline-action" aria-label="重试根目录" @click="retryTreeLevel('')">
                      重试
                    </button>
                  </div>
                  <div v-else-if="visibleTreeNodes.length === 0" class="reference-compact-state">仓库根目录为空。</div>
                  <div v-else class="reference-tree" role="list">
                    <div
                      v-for="node in visibleTreeNodes"
                      :key="node.path"
                      class="reference-tree-node"
                      role="listitem"
                    >
                      <div
                        class="reference-tree-row"
                        :class="{
                          'is-reference-selectable': nodeSelectable(node),
                          'is-selected': selectedFolderPath === node.path
                        }"
                        :style="{ paddingLeft: `${8 + node.depth * 16}px` }"
                      >
                        <button
                          v-if="node.directory"
                          type="button"
                          class="reference-tree-toggle"
                          :aria-label="`${expandedPaths.has(node.path) ? '收起' : '展开'} ${node.name}`"
                          :aria-expanded="expandedPaths.has(node.path)"
                          @click="toggleDirectory(node)"
                        >
                          <ChevronDown v-if="expandedPaths.has(node.path)" class="h-3.5 w-3.5" />
                          <ChevronRight v-else class="h-3.5 w-3.5" />
                        </button>
                        <span v-else class="reference-tree-spacer" />
                        <Folder v-if="node.directory" class="reference-tree-icon" />
                        <File v-else class="reference-tree-icon" />
                        <button
                          v-if="nodeSelectable(node)"
                          type="button"
                          class="reference-tree-name"
                          data-reference-selectable="true"
                          :aria-label="`配置目录 ${node.name}`"
                          :disabled="configSaving"
                          @click="selectFolder(node)"
                        >
                          {{ node.name }}
                        </button>
                        <span v-else class="reference-tree-name">{{ node.name }}</span>
                        <Spinner v-if="treeLoadingPaths.has(node.path)" class="ml-auto h-3 w-3" />
                      </div>
                      <div
                        v-if="treeErrors[node.path]"
                        class="reference-tree-level-error"
                        :style="{ paddingLeft: `${28 + node.depth * 16}px` }"
                        role="alert"
                      >
                        <span>{{ treeErrors[node.path]?.message }}</span>
                        <code v-if="treeErrors[node.path]?.traceId">traceId: {{ treeErrors[node.path]?.traceId }}</code>
                        <button
                          type="button"
                          class="reference-inline-action"
                          :aria-label="`重试 ${node.name}`"
                          @click="retryTreeLevel(node.path)"
                        >
                          重试
                        </button>
                      </div>
                    </div>
                  </div>
                </section>

                <section class="reference-form-panel" aria-label="引用表单">
                  <div class="reference-panel-title">配置</div>
                  <div v-if="!configTarget" class="reference-compact-state is-centered">
                    选择橙色首层目录后配置引用。
                  </div>
                  <div v-else-if="configLoading" class="reference-compact-state is-centered">正在读取工作区配置…</div>
                  <form v-else class="reference-form" @submit.prevent="submitConfig">
                    <label>
                      <span>参考别名（alias）</span>
                      <Input :model-value="configTarget.alias" readonly aria-label="参考别名（alias）" />
                    </label>
                    <label>
                      <span>路径（path）</span>
                      <Input :model-value="form.path" readonly aria-label="路径（path）" />
                    </label>
                    <label>
                      <span>是否合并（merge）</span>
                      <select v-model="mergeModel" aria-label="是否合并（merge）" class="reference-select" :disabled="configSaving">
                        <option value="true">是</option>
                        <option value="false">否</option>
                      </select>
                    </label>
                    <label>
                      <span>规格驱动目录名称（sdd-folder-name）</span>
                      <Input :model-value="form.sddFolderName" readonly aria-label="规格驱动目录名称（sdd-folder-name）" />
                    </label>
                    <label>
                      <span>描述（description） <b aria-hidden="true">*</b></span>
                      <Textarea v-model="form.description" rows="4" aria-label="描述（description）" placeholder="说明何时使用这组引用资料" :disabled="configSaving" />
                    </label>
                    <div v-if="configNotice" class="reference-form-notice" :class="`is-${configNotice.kind}`" role="status">
                      {{ configNotice.message }}
                      <code v-if="configNotice.traceId">traceId: {{ configNotice.traceId }}</code>
                    </div>
                    <div class="reference-form-actions">
                      <Button
                        type="button"
                        :disabled="!submitEnabled"
                        :aria-label="configMode === 'update' ? '更新引用配置' : '保存引用配置'"
                        @click="submitConfig"
                      >
                        {{ configSaving ? "保存中…" : configMode === "update" ? "更新" : "保存" }}
                      </Button>
                    </div>
                  </form>
                </section>
              </div>
            </template>
          </main>
        </div>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.reference-dialog-overlay {
  position: fixed;
  inset: 0;
  z-index: 11000;
  display: grid;
  place-items: center;
  padding: 16px;
  overflow-y: auto;
  background: rgba(15, 23, 42, 0.58);
}

.reference-dialog {
  --reference-folder-accent: #d97706;
  display: flex;
  width: min(1120px, calc(100vw - 32px));
  height: min(760px, calc(100vh - 32px));
  min-height: min(520px, calc(100vh - 32px));
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--ta-border-strong);
  border-radius: 10px;
  background: var(--ta-panel);
  color: var(--ta-text);
  box-shadow: 0 24px 72px rgba(15, 23, 42, 0.28);
}

.reference-dialog-header {
  display: flex;
  min-height: 62px;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid var(--ta-border);
  padding: 10px 14px 10px 18px;
  background: var(--ta-panel-2);
}

.reference-dialog-header h2,
.reference-dialog-header p {
  margin: 0;
}

.reference-dialog-header h2 {
  font-size: 16px;
  font-weight: 600;
}

.reference-dialog-header p {
  margin-top: 3px;
  color: var(--ta-muted);
  font-size: 12px;
}

.reference-dialog-body {
  display: grid;
  min-height: 0;
  flex: 1;
  grid-template-columns: minmax(290px, 34%) minmax(0, 1fr);
}

.reference-repository-column,
.reference-configuration-column {
  min-height: 0;
  overflow: auto;
}

.reference-repository-column {
  border-right: 1px solid var(--ta-border);
  background: var(--ta-panel-2);
}

.reference-column-heading,
.reference-selected-heading,
.reference-panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--ta-muted);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.reference-column-heading {
  position: sticky;
  top: 0;
  z-index: 2;
  height: 34px;
  border-bottom: 1px solid var(--ta-border);
  padding: 0 12px;
  background: var(--ta-panel-2);
}

.reference-repository-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
}

.reference-repository-card {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--ta-border);
  border-radius: 7px;
  background: var(--ta-surface);
}

.reference-repository-card.is-selected {
  border-color: var(--ta-border-strong);
  box-shadow: inset 3px 0 0 var(--ta-ink);
}

.reference-repository-main {
  display: grid;
  width: 100%;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  border: 0;
  padding: 9px 10px 6px;
  background: transparent;
  color: var(--ta-text);
  text-align: left;
  cursor: pointer;
}

.reference-repository-main:hover {
  background: var(--ta-hover);
}

.reference-repository-main strong,
.reference-repository-main small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reference-repository-main strong {
  font-size: 12px;
  font-weight: 600;
}

.reference-repository-main small {
  margin-top: 3px;
  color: var(--ta-muted);
  font-family: "Geist Mono", monospace;
  font-size: 10px;
}

.reference-status {
  color: var(--ta-muted);
  font-family: "Geist Mono", monospace;
  font-size: 10px;
}

.reference-repository-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 10px 7px 34px;
  color: var(--ta-muted);
  font-size: 10px;
}

.reference-inline-action {
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--ta-text);
  font-size: 11px;
  font-weight: 600;
  text-decoration: underline;
  text-underline-offset: 2px;
  cursor: pointer;
}

.reference-branch-popover {
  margin: 0 8px 8px;
  border: 1px solid var(--ta-border-strong);
  border-radius: 6px;
  padding: 8px;
  background: var(--ta-panel);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.12);
}

.reference-branch-title {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-bottom: 7px;
  font-size: 11px;
  font-weight: 600;
}

.reference-popover-actions,
.reference-form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 8px;
}

.reference-server-list {
  margin: 0;
  border-top: 1px solid var(--ta-border);
  padding: 5px 10px 7px 34px;
  list-style: none;
}

.reference-server-list li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 4px 8px;
  padding: 2px 0;
  color: var(--ta-muted);
  font-family: "Geist Mono", monospace;
  font-size: 10px;
}

.reference-server-list small {
  grid-column: 1 / -1;
  color: var(--ta-error);
}

.reference-repository-error,
.reference-trace {
  display: block;
  padding: 0 10px 4px 34px;
  color: var(--ta-error);
  font-size: 10px;
}

.reference-trace {
  color: var(--ta-muted);
  font-family: "Geist Mono", monospace;
}

.reference-configuration-column {
  display: flex;
  flex-direction: column;
  background: var(--ta-panel);
}

.reference-selected-heading {
  min-height: 44px;
  flex-shrink: 0;
  border-bottom: 1px solid var(--ta-border);
  padding: 0 14px;
  background: var(--ta-panel-2);
  color: var(--ta-text);
  letter-spacing: normal;
  text-transform: none;
}

.reference-selected-heading strong,
.reference-selected-heading span {
  display: block;
}

.reference-selected-heading strong {
  font-size: 12px;
}

.reference-selected-heading span {
  margin-top: 2px;
  color: var(--ta-muted);
  font-family: "Geist Mono", monospace;
  font-size: 10px;
  font-weight: 400;
}

.reference-ready-layout {
  display: grid;
  min-height: 0;
  flex: 1;
  grid-template-columns: minmax(210px, 42%) minmax(280px, 1fr);
}

.reference-tree-panel,
.reference-form-panel {
  min-height: 0;
  overflow: auto;
}

.reference-tree-panel {
  border-right: 1px solid var(--ta-border);
  background: var(--ta-tree-bg);
  font-family: var(--ta-tree-font-family);
  font-size: var(--ta-tree-font-size);
}

.reference-form-panel {
  background: var(--ta-panel-2);
}

.reference-panel-title {
  position: sticky;
  top: 0;
  z-index: 1;
  height: 32px;
  border-bottom: 1px solid var(--ta-border);
  padding: 0 10px;
  background: inherit;
}

.reference-tree {
  padding: 5px 0;
}

.reference-tree-row {
  display: flex;
  height: var(--ta-tree-row-height);
  align-items: center;
  gap: 4px;
  padding-right: 8px;
  color: var(--ta-tree-text);
}

.reference-tree-row:hover,
.reference-tree-row.is-selected {
  background: var(--ta-tree-hover);
}

.reference-tree-toggle {
  display: inline-grid;
  width: 16px;
  height: 18px;
  flex-shrink: 0;
  place-items: center;
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--ta-tree-muted);
  cursor: pointer;
}

.reference-tree-spacer {
  width: 16px;
  flex-shrink: 0;
}

.reference-tree-icon {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
  color: var(--ta-tree-muted);
}

.reference-tree-name {
  min-width: 0;
  overflow: hidden;
  border: 0;
  padding: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  line-height: var(--ta-tree-row-height);
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 唯一橙色语义：后端明确标记 highlighted + selectable 的仓库首层 SDD 目录。 */
.reference-tree-row.is-reference-selectable .reference-tree-icon,
.reference-tree-row.is-reference-selectable .reference-tree-name {
  color: var(--reference-folder-accent);
}

.reference-tree-level-error {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 8px;
  padding-top: 4px;
  padding-right: 8px;
  padding-bottom: 6px;
  color: var(--ta-error);
  font-size: 10px;
}

.reference-tree-level-error code {
  font-family: "Geist Mono", monospace;
}

.reference-tree-row.is-reference-selectable .reference-tree-name {
  font-weight: 600;
  cursor: pointer;
}

.reference-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
}

.reference-form label > span {
  display: block;
  margin-bottom: 5px;
  color: var(--ta-muted);
  font-family: "Geist Mono", monospace;
  font-size: 10px;
}

.reference-form label b {
  color: var(--ta-error);
}

.reference-select {
  width: 100%;
  height: 32px;
  border: 1px solid var(--ta-border);
  border-radius: 5px;
  padding: 0 8px;
  outline: none;
  background: var(--ta-surface);
  color: var(--ta-text);
  font-size: 12px;
}

.reference-select:focus {
  border-color: var(--ta-border-strong);
}

.reference-state,
.reference-compact-state {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 16px 12px;
  color: var(--ta-muted);
  font-size: 12px;
}

.reference-state.is-centered,
.reference-compact-state.is-centered {
  min-height: 120px;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.reference-state.is-error,
.reference-compact-state.is-error,
.reference-form-notice.is-error {
  color: var(--ta-error);
}

.reference-state code,
.reference-compact-state code,
.reference-form-notice code {
  font-family: "Geist Mono", monospace;
  font-size: 10px;
}

.reference-form-notice {
  display: flex;
  flex-direction: column;
  gap: 3px;
  border: 1px solid var(--ta-border);
  border-radius: 5px;
  padding: 7px 8px;
  color: var(--ta-muted);
  font-size: 11px;
}

.reference-form-notice.is-success {
  color: var(--ta-ok);
}

@media (max-width: 780px) {
  .reference-dialog-body {
    grid-template-columns: 1fr;
    overflow: auto;
  }

  .reference-repository-column {
    max-height: 38vh;
    border-right: 0;
    border-bottom: 1px solid var(--ta-border);
  }

  .reference-ready-layout {
    grid-template-columns: 1fr;
  }

  .reference-tree-panel {
    min-height: 180px;
    border-right: 0;
    border-bottom: 1px solid var(--ta-border);
  }
}

@media (max-height: 560px) {
  .reference-dialog-overlay {
    place-items: start center;
  }

  .reference-dialog {
    min-height: 0;
    height: calc(100vh - 32px);
  }
}
</style>
