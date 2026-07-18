<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import {
  BackendApiError,
  type BackendApiClient,
  type ReferenceRepositoryStatus,
  type ReferenceRepositoryTreeNode
} from "@test-agent/backend-api";
import { Button, copyTextToClipboard, Input, Spinner, Textarea } from "@test-agent/ui-kit";
import { Check, ChevronDown, ChevronRight, Copy, File, Folder, GitBranch, LibraryBig, RefreshCw, X } from "lucide-vue-next";
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
const PENDING_REFRESH_CONFIRMATION_WINDOW_MS = 30_000;
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
type PendingWorkspaceRefresh = {
  targetBranch: string;
  minGeneration: number;
  requestToken: number;
  confirmationDeadlineMs: number | null;
  paused: boolean;
};
type RepositoryProgressOperation = "SYNCHRONIZE" | "VERIFY_POINTERS";
type RepositoryOperationTrigger = "repository-card" | "verify-button";
type RepositoryOperationRequestState = "REQUESTING" | "ACCEPTED" | "FAILED";
type RepositoryOperationStepState = "waiting" | "running" | "completed" | "failed";
type RepositoryOperationProgress = {
  repositoryId: string;
  requestToken: number;
  operation: RepositoryProgressOperation;
  trigger: RepositoryOperationTrigger;
  requestState: RepositoryOperationRequestState;
  generation: number | null;
  error: Notice | null;
};

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
const operationDialogElement = ref<HTMLElement | null>(null);

const branchPopoverRepositoryId = ref<string | null>(null);
const branchPopoverMode = ref<"initialize" | "switch" | null>(null);
const branches = ref<string[]>([]);
const selectedBranch = ref("");
const branchesLoading = ref(false);
const branchError = ref<Notice | null>(null);
const branchSwitchConfirmation = ref<{ repositoryId: string; repositoryName: string; from: string; to: string } | null>(null);
const pendingWorkspaceRefreshes = ref<Map<string, PendingWorkspaceRefresh>>(new Map());
const operationProgress = ref<RepositoryOperationProgress | null>(null);

let dialogGeneration = 0;
let selectionGeneration = 0;
let pollTimer: ReturnType<typeof setTimeout> | null = null;
let pendingWorkspaceRefreshPollTimer: ReturnType<typeof setTimeout> | null = null;
let pendingWorkspaceRefreshSequence = 0;
let operationRequestSequence = 0;
let repositoryRequestSequence = 0;
const repositoryResponseTokens = new Map<string, number>();
let restoreFocusTo: HTMLElement | null = null;
let workspaceRefreshContextKey = "";

/** v-if 创建弹窗内容后直接聚焦关闭按钮，避免依赖父组件更新时序。 */
const vInitialFocus = {
  mounted(element: HTMLElement) {
    element.focus();
  }
};

const selectedRepository = computed(() =>
  repositories.value.find((repository) => repository.repositoryId === selectedRepositoryId.value) ?? null
);

const operationRepository = computed(() => {
  const progress = operationProgress.value;
  if (!progress) return null;
  return repositories.value.find((repository) => repository.repositoryId === progress.repositoryId) ?? null;
});

/** 只有本次请求已接受且响应代次、操作类型一致时，才允许驱动后续步骤，避免旧 READY 快照提前完成弹层。 */
const acceptedOperationRepository = computed(() => {
  const progress = operationProgress.value;
  const repository = operationRepository.value;
  if (!progress || progress.requestState !== "ACCEPTED" || progress.generation === null || !repository) return null;
  if (repository.generation < progress.generation || repository.operation !== progress.operation) return null;
  return repository;
});

const operationCanClose = computed(() => {
  const progress = operationProgress.value;
  if (!progress) return false;
  if (progress.requestState === "FAILED") return true;
  return ["READY", "FAILED"].includes(acceptedOperationRepository.value?.status ?? "");
});

const operationCanRetry = computed(() => {
  const progress = operationProgress.value;
  if (!progress || progress.requestState === "REQUESTING") return false;
  return progress.requestState === "FAILED" || acceptedOperationRepository.value?.status === "FAILED";
});

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

function beginRepositoryRequest() {
  return ++repositoryRequestSequence;
}

/** generation 优先，同 generation 再按请求发起顺序 fencing，迟到快照不得回滚新状态。 */
function replaceRepository(next: ReferenceRepositoryStatus, responseToken: number) {
  const index = repositories.value.findIndex((item) => item.repositoryId === next.repositoryId);
  const lastResponseToken = repositoryResponseTokens.get(next.repositoryId) ?? 0;
  if (index >= 0) {
    const current = repositories.value[index]!;
    if (current.generation > next.generation) return false;
    if (current.generation === next.generation && lastResponseToken > responseToken) return false;
    repositories.value = repositories.value.map((item, itemIndex) => itemIndex === index ? next : item);
  } else {
    repositories.value = [...repositories.value, next];
  }
  repositoryResponseTokens.set(next.repositoryId, Math.max(lastResponseToken, responseToken));
  return true;
}

function replaceRepositoryList(result: ReferenceRepositoryStatus[], responseToken: number) {
  const resultIds = new Set(result.map((repository) => repository.repositoryId));
  const accepted: ReferenceRepositoryStatus[] = [];
  for (const repository of result) {
    if (replaceRepository(repository, responseToken)) accepted.push(repository);
  }
  repositories.value = repositories.value.filter((repository) =>
    resultIds.has(repository.repositoryId)
    || (repositoryResponseTokens.get(repository.repositoryId) ?? 0) > responseToken);
  return accepted;
}

function markWorkspaceRefreshPending(repositoryId: string, targetBranch: string, minGeneration: number) {
  const requestToken = ++pendingWorkspaceRefreshSequence;
  const next = new Map(pendingWorkspaceRefreshes.value);
  next.set(repositoryId, {
    targetBranch,
    minGeneration,
    requestToken,
    confirmationDeadlineMs: Date.now() + PENDING_REFRESH_CONFIRMATION_WINDOW_MS,
    paused: false
  });
  pendingWorkspaceRefreshes.value = next;
  schedulePendingWorkspaceRefreshPoll(dialogGeneration);
  return requestToken;
}

function dropPendingWorkspaceRefresh(repositoryId: string, expectedRequestToken?: number) {
  const current = pendingWorkspaceRefreshes.value.get(repositoryId);
  if (!current || (expectedRequestToken !== undefined && current.requestToken !== expectedRequestToken)) return;
  const next = new Map(pendingWorkspaceRefreshes.value);
  next.delete(repositoryId);
  pendingWorkspaceRefreshes.value = next;
  if (next.size === 0) clearPendingWorkspaceRefreshPoll();
}

/** 先确认目标分支/代次真实存在；明确失败暂停轮询，READY 才消费刷新。 */
function observeWorkspaceRefresh(repository: ReferenceRepositoryStatus) {
  const pending = pendingWorkspaceRefreshes.value.get(repository.repositoryId);
  if (!pending) return false;
  const targetObserved = repository.branch === pending.targetBranch
    && repository.generation >= pending.minGeneration;
  if (targetObserved && repository.status === "READY") {
    dropPendingWorkspaceRefresh(repository.repositoryId, pending.requestToken);
    return true;
  }
  if (targetObserved) {
    const next = new Map(pendingWorkspaceRefreshes.value);
    next.set(repository.repositoryId, {
      ...pending,
      confirmationDeadlineMs: null,
      paused: repository.status === "FAILED"
    });
    pendingWorkspaceRefreshes.value = next;
  } else if (pending.confirmationDeadlineMs !== null && Date.now() >= pending.confirmationDeadlineMs) {
    dropPendingWorkspaceRefresh(repository.repositoryId, pending.requestToken);
  }
  return false;
}

function pruneExpiredPendingWorkspaceRefreshes() {
  for (const [repositoryId, pending] of pendingWorkspaceRefreshes.value) {
    if (pending.confirmationDeadlineMs !== null && Date.now() >= pending.confirmationDeadlineMs) {
      dropPendingWorkspaceRefresh(repositoryId, pending.requestToken);
    }
  }
}

function hasPollablePendingWorkspaceRefresh() {
  pruneExpiredPendingWorkspaceRefreshes();
  return Array.from(pendingWorkspaceRefreshes.value.values()).some((pending) => !pending.paused);
}

function consumeReadyWorkspaceRefreshes(result: ReferenceRepositoryStatus[]) {
  let shouldRefresh = false;
  for (const repository of result) {
    if (observeWorkspaceRefresh(repository)) shouldRefresh = true;
  }
  if (shouldRefresh) emit("saved");
}

function clearPendingWorkspaceRefreshPoll() {
  if (pendingWorkspaceRefreshPollTimer !== null) {
    clearTimeout(pendingWorkspaceRefreshPollTimer);
    pendingWorkspaceRefreshPollTimer = null;
  }
}

/** 请求结果可能因弹窗关闭被丢弃；重开后用轻量列表轮询继续等待目标分支/代次 READY。 */
function schedulePendingWorkspaceRefreshPoll(dialogToken: number) {
  clearPendingWorkspaceRefreshPoll();
  if (!props.open || !hasPollablePendingWorkspaceRefresh()) return;
  pendingWorkspaceRefreshPollTimer = setTimeout(async () => {
    pendingWorkspaceRefreshPollTimer = null;
    if (!contextIsCurrent(dialogToken)) return;
    try {
      const responseToken = beginRepositoryRequest();
      const result = await api.listReferenceRepositories(props.appId);
      if (!contextIsCurrent(dialogToken)) return;
      const accepted = replaceRepositoryList(result, responseToken);
      consumeReadyWorkspaceRefreshes(accepted);
    } catch {
      // 后台刷新失败不覆盖页面主错误；未确认请求只在有限窗口内继续核对。
      pruneExpiredPendingWorkspaceRefreshes();
    }
    if (contextIsCurrent(dialogToken) && hasPollablePendingWorkspaceRefresh()) {
      schedulePendingWorkspaceRefreshPoll(dialogToken);
    }
  }, POLL_INTERVAL_MS);
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
  operationProgress.value = null;
  branchSwitchConfirmation.value = null;
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
    const responseToken = beginRepositoryRequest();
    const result = await api.listReferenceRepositories(props.appId);
    if (!contextIsCurrent(dialogToken)) return;
    const accepted = replaceRepositoryList(result, responseToken);
    consumeReadyWorkspaceRefreshes(accepted);
  } catch (error) {
    if (contextIsCurrent(dialogToken)) listError.value = notice(error, "加载引用资产库失败");
  } finally {
    if (contextIsCurrent(dialogToken)) {
      listLoading.value = false;
      // 关闭后重开时首轮列表即使失败，也要恢复尚未确认切换结果的有限补偿轮询。
      if (hasPollablePendingWorkspaceRefresh()) schedulePendingWorkspaceRefreshPoll(dialogToken);
    }
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
      const responseToken = beginRepositoryRequest();
      const next = await api.getReferenceRepositoryStatus(props.appId, repositoryId);
      if (!contextIsCurrent(dialogToken, selectionToken, repositoryId)) return;
      actionError.value = null;
      if (!replaceRepository(next, responseToken)) {
        if (selectedRepository.value && ACTIVE_STATUSES.has(selectedRepository.value.status)) {
          scheduleStatusPoll(repositoryId, dialogToken, selectionToken);
        }
        return;
      }
      const shouldRefreshWorkspace = observeWorkspaceRefresh(next);
      if (next.status === "READY") {
        if (shouldRefreshWorkspace) emit("saved");
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
  selectionToken: number,
  responseToken: number
) {
  if (!contextIsCurrent(dialogToken, selectionToken, next.repositoryId)) return;
  if (!replaceRepository(next, responseToken)) {
    scheduleStatusPoll(next.repositoryId, dialogToken, selectionToken);
    return;
  }
  const shouldRefreshWorkspace = observeWorkspaceRefresh(next);
  if (next.status === "READY") {
    if (shouldRefreshWorkspace) emit("saved");
    await loadTreeLevel("", dialogToken, selectionToken, next.repositoryId);
  } else if (ACTIVE_STATUSES.has(next.status)) {
    scheduleStatusPoll(next.repositoryId, dialogToken, selectionToken);
  }
}

function isProgressOperation(operation: ReferenceRepositoryStatus["operation"]): operation is RepositoryProgressOperation {
  return operation === "SYNCHRONIZE" || operation === "VERIFY_POINTERS";
}

/**
 * 操作弹层必须先于远端请求出现；请求序号与后端 generation 共同隔离迟到响应。
 */
function beginOperationProgress(
  repository: ReferenceRepositoryStatus,
  operation: RepositoryProgressOperation,
  trigger: RepositoryOperationTrigger,
  requestState: RepositoryOperationRequestState = "REQUESTING",
  generation: number | null = null
) {
  const requestToken = ++operationRequestSequence;
  operationProgress.value = {
    repositoryId: repository.repositoryId,
    requestToken,
    operation,
    trigger,
    requestState,
    generation,
    error: null
  };
  actionError.value = null;
  void nextTick(() => operationDialogElement.value?.focus());
  return requestToken;
}

async function selectRepository(repository: ReferenceRepositoryStatus, synchronize = true) {
  const repairsFailedSwitch = repository.operation === "SWITCH_BRANCH"
    && repository.status === "FAILED"
    && Boolean(repository.branch);
  resetSelectionState();
  selectedRepositoryId.value = repository.repositoryId;
  let pendingRefreshRequestToken: number | undefined;
  if (repairsFailedSwitch) {
    pendingRefreshRequestToken = markWorkspaceRefreshPending(
      repository.repositoryId,
      repository.branch!,
      repository.generation + 1);
  }
  const dialogToken = dialogGeneration;
  const selectionToken = selectionGeneration;
  if (!synchronize || !repository.initialized) return;
  if (ACTIVE_STATUSES.has(repository.status)) {
    if (isProgressOperation(repository.operation)) {
      beginOperationProgress(repository, repository.operation, "repository-card", "ACCEPTED", repository.generation);
    }
    scheduleStatusPoll(repository.repositoryId, dialogToken, selectionToken);
    return;
  }
  const requestToken = beginOperationProgress(repository, "SYNCHRONIZE", "repository-card");
  selectionBusy.value = true;
  try {
    const responseToken = beginRepositoryRequest();
    const next = await api.synchronizeReferenceRepository(props.appId, repository.repositoryId);
    const progress = operationProgress.value;
    if (!contextIsCurrent(dialogToken, selectionToken, repository.repositoryId)
      || !progress
      || progress.requestToken !== requestToken
      || progress.operation !== "SYNCHRONIZE") return;
    operationProgress.value = {
      ...progress,
      requestState: "ACCEPTED",
      generation: next.generation,
      error: null
    };
    await applyOperationStatus(next, dialogToken, selectionToken, responseToken);
  } catch (error) {
    if (repairsFailedSwitch && error instanceof BackendApiError && !error.retryable) {
      dropPendingWorkspaceRefresh(repository.repositoryId, pendingRefreshRequestToken);
    }
    const progress = operationProgress.value;
    if (contextIsCurrent(dialogToken, selectionToken, repository.repositoryId)
      && progress
      && progress.requestToken === requestToken
      && progress.operation === "SYNCHRONIZE") {
      operationProgress.value = {
        ...progress,
        requestState: "FAILED",
        error: notice(error, "同步引用资产库失败")
      };
    }
  } finally {
    const progress = operationProgress.value;
    if (contextIsCurrent(dialogToken, selectionToken, repository.repositoryId)
      && progress
      && progress.requestToken === requestToken) selectionBusy.value = false;
  }
}

async function openBranchPopover(repository: ReferenceRepositoryStatus) {
  await selectRepository(repository, false);
  const dialogToken = dialogGeneration;
  const selectionToken = selectionGeneration;
  branchPopoverRepositoryId.value = repository.repositoryId;
  branchPopoverMode.value = "initialize";
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

async function openSwitchBranchPopover(repository: ReferenceRepositoryStatus) {
  if (selectedRepositoryId.value !== repository.repositoryId) {
    await selectRepository(repository, false);
  }
  const dialogToken = dialogGeneration;
  const selectionToken = selectionGeneration;
  branchPopoverRepositoryId.value = repository.repositoryId;
  branchPopoverMode.value = "switch";
  branches.value = [];
  selectedBranch.value = "";
  branchesLoading.value = true;
  branchError.value = null;
  try {
    const result = await api.listRepositoryBranches(repository.repositoryId);
    if (!contextIsCurrent(dialogToken, selectionToken, repository.repositoryId)) return;
    branches.value = result.filter((branch) => branch !== repository.branch);
    selectedBranch.value = branches.value[0] ?? "";
  } catch (error) {
    if (contextIsCurrent(dialogToken, selectionToken, repository.repositoryId)) {
      branchError.value = notice(error, "加载分支失败");
    }
  } finally {
    if (contextIsCurrent(dialogToken, selectionToken, repository.repositoryId)) branchesLoading.value = false;
  }
}

function continueSwitchBranch(repository: ReferenceRepositoryStatus) {
  if (!selectedBranch.value || !repository.branch || selectedBranch.value === repository.branch) return;
  branchSwitchConfirmation.value = {
    repositoryId: repository.repositoryId,
    repositoryName: repository.name,
    from: repository.branch,
    to: selectedBranch.value
  };
}

async function confirmSwitchBranch() {
  const confirmation = branchSwitchConfirmation.value;
  if (!confirmation || confirmation.repositoryId !== selectedRepositoryId.value) return;
  const minGeneration = (selectedRepository.value?.generation ?? 0) + 1;
  const dialogToken = dialogGeneration;
  // 新分支操作提升选择代次，隔离旧分支尚未返回的目录、配置和状态响应。
  const selectionToken = ++selectionGeneration;
  clearPoll();
  treeLoadingPaths.value = new Set();
  configLoading.value = false;
  const pendingRefreshRequestToken = markWorkspaceRefreshPending(
    confirmation.repositoryId,
    confirmation.to,
    minGeneration);
  selectionBusy.value = true;
  actionError.value = null;
  try {
    const responseToken = beginRepositoryRequest();
    const next = await api.switchReferenceRepositoryBranch(props.appId, confirmation.repositoryId, confirmation.to);
    if (!contextIsCurrent(dialogToken, selectionToken, confirmation.repositoryId)) return;
    branchSwitchConfirmation.value = null;
    closeBranchPopover();
    treeByParent.value = {};
    expandedPaths.value = new Set();
    selectedFolderPath.value = null;
    configTarget.value = null;
    configMode.value = null;
    baseline.value = null;
    await applyOperationStatus(next, dialogToken, selectionToken, responseToken);
  } catch (error) {
    if (error instanceof BackendApiError && !error.retryable) {
      dropPendingWorkspaceRefresh(confirmation.repositoryId, pendingRefreshRequestToken);
    }
    if (contextIsCurrent(dialogToken, selectionToken, confirmation.repositoryId)) {
      actionError.value = notice(error, "切换引用资产库分支失败");
      closeBranchSwitchConfirmation();
    }
  } finally {
    if (contextIsCurrent(dialogToken, selectionToken, confirmation.repositoryId)) selectionBusy.value = false;
  }
}

function isSynchronizationProgress() {
  return operationProgress.value?.operation === "SYNCHRONIZE";
}

function operationDialogLabel() {
  return isSynchronizationProgress() ? "资产库同步进度" : "Git 指针核验进度";
}

function operationCloseLabel() {
  return isSynchronizationProgress() ? "关闭资产库同步进度" : "关闭 Git 指针核验进度";
}

function operationRetryLabel() {
  return isSynchronizationProgress() ? "重试资产库同步" : "重试 Git 指针核验";
}

function operationStepState(step: 1 | 2 | 3): RepositoryOperationStepState {
  const progress = operationProgress.value;
  const repository = acceptedOperationRepository.value;
  if (!progress) return "waiting";
  if (step === 1) {
    if (progress.requestState === "REQUESTING") return "running";
    return progress.requestState === "FAILED" ? "failed" : "completed";
  }
  if (progress.requestState !== "ACCEPTED") return "waiting";
  if (repository?.status === "FAILED") return "failed";
  if (repository?.status === "READY") return "completed";
  return step === 2 ? "running" : "waiting";
}

function operationStepText(step: 1 | 2 | 3) {
  const state = operationStepState(step);
  const synchronization = isSynchronizationProgress();
  if (step === 1) {
    return state === "running" ? "正在创建" : state === "failed" ? "创建失败" : "任务已创建";
  }
  if (step === 2) {
    return state === "running"
      ? synchronization ? "同步中" : "核验中"
      : state === "completed"
        ? "已完成"
        : state === "failed"
          ? synchronization ? "同步失败" : "核验失败"
          : "等待";
  }
  return state === "completed"
    ? synchronization ? "同步完成" : "核验完成"
    : state === "failed"
      ? synchronization ? "同步失败" : "核验失败"
      : "等待服务器";
}

function operationStepTitle(step: 1 | 2 | 3) {
  const synchronization = isSynchronizationProgress();
  if (step === 1) return synchronization ? "创建同步任务" : "创建核验任务";
  if (step === 2) return synchronization ? "各服务器同步" : "各服务器核验";
  return synchronization ? "汇总同步结果" : "汇总核验结果";
}

function operationStepDescription(step: 1 | 2 | 3) {
  const synchronization = isSynchronizationProgress();
  if (step === 1) return synchronization ? "向多节点协调器提交同步代次" : "向多节点协调器提交只读核验代次";
  if (step === 2) return synchronization ? "同步固定分支与目标 HEAD 到各服务器" : "读取本地分支、HEAD、origin 和工作树状态";
  return "按当前在线服务器判断本轮是否收敛";
}

function operationHeadline() {
  const progress = operationProgress.value;
  const repository = acceptedOperationRepository.value;
  const synchronization = isSynchronizationProgress();
  if (!progress || progress.requestState === "REQUESTING") return synchronization ? "正在创建同步任务" : "正在创建核验任务";
  if (progress.requestState === "FAILED") return synchronization ? "同步任务创建失败" : "核验任务创建失败";
  if (repository?.status === "READY") return synchronization ? "同步完成" : "核验完成";
  if (repository?.status === "FAILED") return synchronization ? "同步失败" : "核验失败";
  return synchronization ? "正在同步各服务器资产副本" : "正在核验服务器 Git 指针";
}

function operationServerStatusText(server: ReferenceRepositoryStatus["servers"][number]) {
  if (isSynchronizationProgress()) {
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
    case "READY":
      return server.matchesTarget === true ? "已一致" : server.matchesTarget === false ? "不一致" : "已核验";
    case "BLOCKED": return "核验失败";
    case "RETRY_WAIT": return "等待重试";
    case "DEFERRED": return "离线延后";
    default: return server.status;
  }
}

function operationServerStatusClass(server: ReferenceRepositoryStatus["servers"][number]) {
  if (server.status === "READY") return "is-completed";
  if (server.status === "BLOCKED") return "is-failed";
  if (["PENDING", "PROCESSING", "RETRY_WAIT"].includes(server.status)) return "is-running";
  return "is-waiting";
}

async function verifyPointers(repository: ReferenceRepositoryStatus) {
  const dialogToken = dialogGeneration;
  const selectionToken = selectionGeneration;
  const requestToken = beginOperationProgress(repository, "VERIFY_POINTERS", "verify-button");
  selectionBusy.value = true;
  try {
    const responseToken = beginRepositoryRequest();
    const next = await api.verifyReferenceRepositoryPointers(props.appId, repository.repositoryId);
    const progress = operationProgress.value;
    if (!contextIsCurrent(dialogToken, selectionToken, repository.repositoryId)
      || !progress
      || progress.requestToken !== requestToken
      || progress.operation !== "VERIFY_POINTERS") return;
    operationProgress.value = {
      ...progress,
      requestState: "ACCEPTED",
      generation: next.generation,
      error: null
    };
    await applyOperationStatus(next, dialogToken, selectionToken, responseToken);
  } catch (error) {
    const progress = operationProgress.value;
    if (contextIsCurrent(dialogToken, selectionToken, repository.repositoryId)
      && progress
      && progress.requestToken === requestToken
      && progress.operation === "VERIFY_POINTERS") {
      operationProgress.value = {
        ...progress,
        requestState: "FAILED",
        error: notice(error, "核验服务器 Git 指针失败")
      };
    }
  } finally {
    const progress = operationProgress.value;
    if (contextIsCurrent(dialogToken, selectionToken, repository.repositoryId)
      && progress
      && progress.requestToken === requestToken) selectionBusy.value = false;
  }
}

function retryOperation() {
  const repository = operationRepository.value;
  const progress = operationProgress.value;
  if (!repository || !progress || !operationCanRetry.value) return;
  if (progress.operation === "SYNCHRONIZE") {
    void selectRepository(repository);
    return;
  }
  void verifyPointers(repository);
}

function closeOperationProgress() {
  const progress = operationProgress.value;
  if (!progress || !operationCanClose.value) return;
  operationProgress.value = null;
  actionError.value = null;
  void nextTick(() => {
    if (progress.trigger === "verify-button") {
      dialogElement.value?.querySelector<HTMLElement>('button[data-reference-verify="true"]')?.focus();
      return;
    }
    const repositoryButton = Array.from(
      dialogElement.value?.querySelectorAll<HTMLElement>("button[data-reference-repository-select]") ?? []
    ).find((element) => element.dataset.referenceRepositorySelect === progress.repositoryId);
    repositoryButton?.focus();
  });
}

function closeBranchPopover() {
  branchPopoverRepositoryId.value = null;
  branchPopoverMode.value = null;
  branches.value = [];
  selectedBranch.value = "";
  branchesLoading.value = false;
  branchError.value = null;
}

function shortCommit(commitHash?: string | null) {
  return commitHash ? commitHash.slice(0, 12) : "—";
}

function formattedTime(value?: string | null) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

function serverOnline(server: ReferenceRepositoryStatus["servers"][number]) {
  return server.online === true ? true : server.online === false ? false : null;
}

function serverMatchesTarget(server: ReferenceRepositoryStatus["servers"][number]) {
  return server.matchesTarget === true ? true : server.matchesTarget === false ? false : null;
}

function copyCommit(commitHash?: string | null) {
  if (commitHash) void copyTextToClipboard(commitHash);
}

function closeBranchSwitchConfirmation() {
  branchSwitchConfirmation.value = null;
  void nextTick(() => {
    dialogElement.value
      ?.querySelector<HTMLElement>('button[data-reference-switch-continue="true"]')
      ?.focus();
  });
}

async function confirmInitialize(repository: ReferenceRepositoryStatus) {
  if (!selectedBranch.value) return;
  const dialogToken = dialogGeneration;
  const selectionToken = selectionGeneration;
  selectionBusy.value = true;
  actionError.value = null;
  try {
    const responseToken = beginRepositoryRequest();
    const next = await api.initializeReferenceRepository(props.appId, repository.repositoryId, selectedBranch.value);
    if (!contextIsCurrent(dialogToken, selectionToken, repository.repositoryId)) return;
    closeBranchPopover();
    await applyOperationStatus(next, dialogToken, selectionToken, responseToken);
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
  const scope = operationProgress.value
    ? dialog.querySelector<HTMLElement>(".reference-verification-progress") ?? dialog
    : branchSwitchConfirmation.value
      ? dialog.querySelector<HTMLElement>(".reference-confirmation") ?? dialog
      : dialog;
  return Array.from(scope.querySelectorAll<HTMLElement>(
    'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [href], [tabindex]:not([tabindex="-1"])'
  )).filter((element) => !element.hasAttribute("hidden"));
}

function handleWindowKeydown(event: KeyboardEvent) {
  if (!props.open) return;
  if (event.key === "Escape") {
    event.preventDefault();
    if (operationProgress.value) {
      if (operationCanClose.value) closeOperationProgress();
      return;
    }
    if (branchSwitchConfirmation.value) {
      if (selectionBusy.value) return;
      closeBranchSwitchConfirmation();
      return;
    }
    emit("close");
    return;
  }
  if (event.key !== "Tab") return;
  const focusable = focusableElements();
  const first = focusable[0];
  const last = focusable.at(-1);
  if (!first || !last) {
    event.preventDefault();
    (operationProgress.value ? operationDialogElement.value : dialogElement.value)?.focus();
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
  ([open, appId, workspaceId]) => {
    const nextContextKey = `${appId}\u0000${workspaceId}`;
    if (workspaceRefreshContextKey !== nextContextKey) {
      workspaceRefreshContextKey = nextContextKey;
      pendingWorkspaceRefreshes.value = new Map();
    }
    clearPendingWorkspaceRefreshPoll();
    dialogGeneration++;
    resetSelectionState();
    repositories.value = [];
    repositoryResponseTokens.clear();
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
  clearPendingWorkspaceRefreshPoll();
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
        <header
          class="reference-dialog-header"
          :aria-hidden="branchSwitchConfirmation || operationProgress ? 'true' : undefined"
          :inert="branchSwitchConfirmation || operationProgress ? true : undefined"
        >
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
            :disabled="Boolean(operationProgress)"
            @click="emit('close')"
          >
            <X class="h-4 w-4" />
          </Button>
        </header>

        <div
          class="reference-dialog-body"
          :aria-hidden="branchSwitchConfirmation || operationProgress ? 'true' : undefined"
          :inert="branchSwitchConfirmation || operationProgress ? true : undefined"
        >
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
                  :data-reference-repository-select="repository.repositoryId"
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
                  <button
                    v-else
                    type="button"
                    class="reference-inline-action"
                    :aria-label="`切换${repository.name}分支`"
                    :disabled="configSaving || selectionBusy || ACTIVE_STATUSES.has(repository.status)"
                    @click="openSwitchBranchPopover(repository)"
                  >
                    切换分支
                  </button>
                </div>
                <div
                  v-if="branchPopoverRepositoryId === repository.repositoryId"
                  class="reference-branch-popover"
                  role="dialog"
                  :aria-label="branchPopoverMode === 'switch' ? `切换${repository.name}分支` : `初始化${repository.name}`"
                >
                  <div class="reference-branch-title">
                    <GitBranch class="h-3.5 w-3.5" />
                    {{ branchPopoverMode === "switch" ? "选择目标分支" : "选择初始化分支" }}
                  </div>
                  <div v-if="branchesLoading" class="reference-compact-state">正在加载分支…</div>
                  <div v-else-if="branchError" class="reference-compact-state is-error">
                    {{ branchError.message }}
                    <code v-if="branchError.traceId">traceId: {{ branchError.traceId }}</code>
                  </div>
                  <template v-else>
                    <select
                      v-model="selectedBranch"
                      :aria-label="branchPopoverMode === 'switch' ? '目标分支' : '初始化分支'"
                      class="reference-select"
                    >
                      <option v-for="branch in branches" :key="branch" :value="branch">{{ branch }}</option>
                    </select>
                    <div v-if="branchPopoverMode === 'switch' && branches.length === 0" class="reference-compact-state">
                      没有可切换的其它分支。
                    </div>
                    <div class="reference-popover-actions">
                      <Button size="sm" variant="ghost" @click="closeBranchPopover">取消</Button>
                      <Button
                        size="sm"
                        :disabled="!selectedBranch || selectionBusy"
                        :aria-label="branchPopoverMode === 'switch' ? `继续切换${repository.name}分支` : `确认初始化${repository.name}`"
                        :data-reference-switch-continue="branchPopoverMode === 'switch' ? 'true' : undefined"
                        @click="branchPopoverMode === 'switch' ? continueSwitchBranch(repository) : confirmInitialize(repository)"
                      >
                        {{ branchPopoverMode === "switch" ? "继续" : selectionBusy ? "初始化中…" : "确认初始化" }}
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
                <div class="reference-selected-actions">
                  <div
                    v-if="selectedRepository.initialized"
                    class="reference-repository-path"
                    data-reference-repository-path="true"
                    :title="selectedRepository.repositoryPath || undefined"
                  >
                    <span>服务器路径</span>
                    <code>{{ selectedRepository.repositoryPath || "服务器路径暂不可用" }}</code>
                  </div>
                  <Button
                    v-if="selectedRepository.initialized"
                    size="sm"
                    variant="ghost"
                    :aria-label="`刷新${selectedRepository.name} Git 指针`"
                    data-reference-verify="true"
                    :disabled="selectionBusy || configSaving || ACTIVE_STATUSES.has(selectedRepository.status)"
                    @click="verifyPointers(selectedRepository)"
                  >
                    <RefreshCw class="h-3.5 w-3.5" :class="{ 'animate-spin': selectedRepository.operation === 'VERIFY_POINTERS' && ACTIVE_STATUSES.has(selectedRepository.status) }" />
                    刷新 Git 指针
                  </Button>
                  <RefreshCw v-if="selectionBusy || ACTIVE_STATUSES.has(selectedRepository.status)" class="h-4 w-4 animate-spin" />
                </div>
              </div>
              <section v-if="selectedRepository.initialized" class="reference-pointer-panel" aria-label="服务器 Git 指针">
                <div class="reference-pointer-target">
                  <span>目标 Git 指针</span>
                  <strong>{{ selectedRepository.branch || "—" }}</strong>
                  <code
                    :title="selectedRepository.targetCommitHash || undefined"
                    :data-full-commit="selectedRepository.targetCommitHash || undefined"
                  >{{ shortCommit(selectedRepository.targetCommitHash) }}</code>
                  <button
                    v-if="selectedRepository.targetCommitHash"
                    type="button"
                    class="reference-copy-action"
                    aria-label="复制目标 Git HEAD"
                    @click="copyCommit(selectedRepository.targetCommitHash)"
                  >
                    <Copy class="h-3 w-3" />
                  </button>
                </div>
                <div v-if="selectedRepository.servers.length === 0" class="reference-compact-state">暂无服务器副本。</div>
                <div v-else class="reference-pointer-table-wrap">
                  <table class="reference-pointer-table">
                    <thead>
                      <tr>
                        <th>服务器</th>
                        <th>状态</th>
                        <th>实际分支</th>
                        <th>实际 HEAD</th>
                        <th>目标</th>
                        <th>最近同步 / 核验</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="server in selectedRepository.servers" :key="server.linuxServerId">
                        <td>
                          <strong>{{ server.linuxServerId }}</strong>
                          <small :class="serverOnline(server) === true ? 'is-online' : 'is-offline'">
                            {{ serverOnline(server) === true
                              ? "在线"
                              : serverOnline(server) === false ? "离线 · 非实时" : "在线状态未知 · 非实时" }}
                          </small>
                        </td>
                        <td><span class="reference-pointer-status">{{ server.status }}</span></td>
                        <td><code>{{ server.currentBranch || "—" }}</code></td>
                        <td>
                          <span class="reference-commit-cell">
                            <code
                              :title="server.currentCommitHash || undefined"
                              :data-full-commit="server.currentCommitHash || undefined"
                            >{{ shortCommit(server.currentCommitHash) }}</code>
                            <button
                              v-if="server.currentCommitHash"
                              type="button"
                              class="reference-copy-action"
                              :aria-label="`复制 ${server.linuxServerId} Git HEAD`"
                              @click="copyCommit(server.currentCommitHash)"
                            >
                              <Copy class="h-3 w-3" />
                            </button>
                          </span>
                        </td>
                        <td>
                          <span
                            class="reference-pointer-match"
                            :class="{
                              'is-match': serverMatchesTarget(server) === true,
                              'is-mismatch': serverMatchesTarget(server) === false
                            }"
                          >
                            <Check v-if="serverMatchesTarget(server) === true" class="h-3 w-3" />
                            {{ serverMatchesTarget(server) === true
                              ? "一致"
                              : serverMatchesTarget(server) === false ? "不一致" : "未核验" }}
                          </span>
                        </td>
                        <td>
                          <small>
                            同步
                            <time
                              :datetime="server.syncedAt || undefined"
                              :data-reference-synced-at="server.syncedAt || undefined"
                            >{{ formattedTime(server.syncedAt) }}</time>
                          </small>
                          <small>
                            核验
                            <time
                              :datetime="server.verifiedAt || undefined"
                              :data-reference-verified-at="server.verifiedAt || undefined"
                            >{{ formattedTime(server.verifiedAt) }}</time>
                          </small>
                          <small v-if="server.error" class="is-error">{{ server.error }}</small>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </section>
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

        <div v-if="operationProgress" class="reference-confirmation-backdrop">
          <section
            ref="operationDialogElement"
            class="reference-verification-progress"
            role="dialog"
            aria-modal="true"
            :aria-label="operationDialogLabel()"
            :aria-busy="operationCanClose ? undefined : 'true'"
            tabindex="-1"
          >
            <header class="reference-verification-header">
              <div>
                <h3>{{ isSynchronizationProgress() ? "同步资产库" : "刷新 Git 指针" }}</h3>
                <p aria-live="polite">{{ operationHeadline() }}</p>
              </div>
              <Button
                size="sm"
                variant="ghost"
                :aria-label="operationCloseLabel()"
                :disabled="!operationCanClose"
                @click="closeOperationProgress"
              >关闭</Button>
            </header>

            <div v-if="operationRepository" class="reference-verification-target">
              <div>
                <span>版本库</span>
                <strong>{{ operationRepository.name }}（{{ operationRepository.englishName }}）</strong>
              </div>
              <div>
                <span>目标指针</span>
                <code>{{ operationRepository.branch || "—" }} · {{ shortCommit(operationRepository.targetCommitHash) }}</code>
              </div>
              <div>
                <span>服务器</span>
                <strong>{{ operationRepository.readyServerCount }}/{{ operationRepository.targetServerCount }} 台就绪</strong>
              </div>
            </div>

            <ol class="reference-verification-steps">
              <li :class="`is-${operationStepState(1)}`">
                <span class="reference-verification-marker" aria-hidden="true">
                  <Check v-if="operationStepState(1) === 'completed'" class="h-3.5 w-3.5" />
                  <X v-else-if="operationStepState(1) === 'failed'" class="h-3.5 w-3.5" />
                  <RefreshCw v-else-if="operationStepState(1) === 'running'" class="h-3.5 w-3.5 animate-spin" />
                  <span v-else>1</span>
                </span>
                <div>
                  <strong>{{ operationStepTitle(1) }}</strong>
                  <small>{{ operationStepDescription(1) }}</small>
                </div>
                <span class="reference-verification-step-status">{{ operationStepText(1) }}</span>
              </li>
              <li :class="`is-${operationStepState(2)}`">
                <span class="reference-verification-marker" aria-hidden="true">
                  <Check v-if="operationStepState(2) === 'completed'" class="h-3.5 w-3.5" />
                  <X v-else-if="operationStepState(2) === 'failed'" class="h-3.5 w-3.5" />
                  <RefreshCw v-else-if="operationStepState(2) === 'running'" class="h-3.5 w-3.5 animate-spin" />
                  <span v-else>2</span>
                </span>
                <div>
                  <strong>{{ operationStepTitle(2) }}</strong>
                  <small>{{ operationStepDescription(2) }}</small>
                </div>
                <span class="reference-verification-step-status">{{ operationStepText(2) }}</span>
                <div v-if="operationProgress.requestState === 'ACCEPTED'" class="reference-verification-servers">
                  <div
                    v-for="server in acceptedOperationRepository?.servers || []"
                    :key="server.linuxServerId"
                    class="reference-verification-server"
                  >
                    <div>
                      <strong>{{ server.linuxServerId }}</strong>
                      <small>{{ serverOnline(server) === true ? "在线" : serverOnline(server) === false ? "离线" : "在线状态未知" }}</small>
                    </div>
                    <span :class="operationServerStatusClass(server)">{{ operationServerStatusText(server) }}</span>
                    <code>{{ server.currentBranch || "—" }} · {{ shortCommit(server.currentCommitHash) }}</code>
                    <small v-if="server.error" class="is-error">{{ server.error }}</small>
                  </div>
                  <div v-if="(acceptedOperationRepository?.servers.length || 0) === 0" class="reference-verification-server-empty">
                    正在等待服务器领取{{ isSynchronizationProgress() ? "同步" : "核验" }}任务…
                  </div>
                </div>
              </li>
              <li :class="`is-${operationStepState(3)}`">
                <span class="reference-verification-marker" aria-hidden="true">
                  <Check v-if="operationStepState(3) === 'completed'" class="h-3.5 w-3.5" />
                  <X v-else-if="operationStepState(3) === 'failed'" class="h-3.5 w-3.5" />
                  <RefreshCw v-else-if="operationStepState(3) === 'running'" class="h-3.5 w-3.5 animate-spin" />
                  <span v-else>3</span>
                </span>
                <div>
                  <strong>{{ operationStepTitle(3) }}</strong>
                  <small>{{ operationStepDescription(3) }}</small>
                </div>
                <span class="reference-verification-step-status">{{ operationStepText(3) }}</span>
              </li>
            </ol>

            <div v-if="operationProgress.error" class="reference-verification-error" role="alert">
              <strong>{{ operationProgress.error.message }}</strong>
              <code v-if="operationProgress.error.traceId">traceId: {{ operationProgress.error.traceId }}</code>
            </div>
            <div v-else-if="actionError" class="reference-verification-error is-retrying" role="status">
              <strong>{{ actionError.message }}</strong>
              <span>正在自动重试状态读取…</span>
              <code v-if="actionError.traceId">traceId: {{ actionError.traceId }}</code>
            </div>
            <div
              v-else-if="acceptedOperationRepository?.status === 'FAILED'"
              class="reference-verification-error"
              role="alert"
            >
              <strong>{{ acceptedOperationRepository.message || (isSynchronizationProgress() ? "服务器资产副本同步失败" : "服务器指针核验失败") }}</strong>
              <code v-if="acceptedOperationRepository.traceId">traceId: {{ acceptedOperationRepository.traceId }}</code>
            </div>

            <footer class="reference-verification-actions">
              <Button
                v-if="operationCanRetry"
                size="sm"
                variant="ghost"
                :aria-label="operationRetryLabel()"
                @click="retryOperation"
              >重试</Button>
              <span v-if="!operationCanClose">{{ isSynchronizationProgress() ? "同步" : "核验" }}期间请保持此窗口打开</span>
            </footer>
          </section>
        </div>

        <div v-if="branchSwitchConfirmation" class="reference-confirmation-backdrop">
          <section
            class="reference-confirmation"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="reference-switch-title"
            :aria-busy="selectionBusy ? 'true' : undefined"
          >
            <h3 id="reference-switch-title">确认切换引用分支</h3>
            <p>
              {{ branchSwitchConfirmation.repositoryName }} 将从
              <code>{{ branchSwitchConfirmation.from }}</code>
              切换到 <code>{{ branchSwitchConfirmation.to }}</code>。
            </p>
            <p>此操作将更新所有服务器，现有引用路径中的内容也会随之变化。</p>
            <div class="reference-popover-actions">
              <Button
                v-initial-focus
                size="sm"
                variant="ghost"
                aria-label="取消切换引用分支"
                :disabled="selectionBusy"
                @click="closeBranchSwitchConfirmation"
              >取消</Button>
              <Button
                size="sm"
                :disabled="selectionBusy"
                :aria-label="`确认将${branchSwitchConfirmation.repositoryName}切换到 ${branchSwitchConfirmation.to}`"
                @click="confirmSwitchBranch"
              >
                {{ selectionBusy ? "切换中…" : "确认切换" }}
              </Button>
            </div>
          </section>
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
  position: relative;
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

.reference-selected-actions,
.reference-commit-cell,
.reference-pointer-match,
.reference-pointer-target {
  display: flex;
  align-items: center;
}

.reference-selected-actions {
  min-width: 0;
  max-width: 72%;
  gap: 6px;
}

.reference-repository-path {
  display: flex;
  min-width: 0;
  max-width: 430px;
  align-items: center;
  gap: 6px;
  border-right: 1px solid var(--ta-border);
  padding-right: 10px;
}

.reference-repository-path span {
  flex-shrink: 0;
  margin: 0;
  color: var(--ta-muted);
  font-family: inherit;
  font-size: 10px;
}

.reference-repository-path code {
  min-width: 0;
  overflow: hidden;
  color: var(--ta-text);
  font-family: "Geist Mono", monospace;
  font-size: 10px;
  font-weight: 400;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reference-pointer-panel {
  flex-shrink: 0;
  border-bottom: 1px solid var(--ta-border);
  background: var(--ta-surface);
}

.reference-pointer-target {
  min-height: 34px;
  gap: 8px;
  border-bottom: 1px solid var(--ta-border);
  padding: 0 12px;
  color: var(--ta-muted);
  font-size: 10px;
}

.reference-pointer-target strong,
.reference-pointer-target code,
.reference-pointer-table code,
.reference-pointer-table time {
  color: var(--ta-text);
  font-family: "Geist Mono", monospace;
  font-size: 10px;
}

.reference-pointer-table-wrap {
  max-height: 148px;
  overflow: auto;
}

.reference-pointer-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 10px;
  text-align: left;
}

.reference-pointer-table th,
.reference-pointer-table td {
  border-bottom: 1px solid var(--ta-border);
  padding: 5px 8px;
  vertical-align: top;
  white-space: nowrap;
}

.reference-pointer-table th {
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--ta-panel-2);
  color: var(--ta-muted);
  font-weight: 600;
}

.reference-pointer-table td:first-child strong,
.reference-pointer-table td:first-child small,
.reference-pointer-table td:last-child small {
  display: block;
}

.reference-pointer-table small {
  margin-top: 2px;
  color: var(--ta-muted);
  font-size: 9px;
}

.reference-pointer-table small.is-online,
.reference-pointer-match.is-match {
  color: var(--ta-ok);
}

.reference-pointer-table small.is-offline {
  color: var(--ta-muted);
}

.reference-pointer-table small.is-error,
.reference-pointer-match.is-mismatch {
  color: var(--ta-error);
}

.reference-pointer-status {
  color: var(--ta-muted);
  font-family: "Geist Mono", monospace;
}

.reference-commit-cell,
.reference-pointer-match {
  gap: 3px;
}

.reference-copy-action {
  display: inline-grid;
  width: 18px;
  height: 18px;
  place-items: center;
  border: 0;
  border-radius: 3px;
  padding: 0;
  background: transparent;
  color: var(--ta-muted);
  cursor: pointer;
}

.reference-copy-action:hover,
.reference-copy-action:focus-visible {
  background: var(--ta-hover);
  color: var(--ta-text);
}

.reference-confirmation-backdrop {
  position: absolute;
  inset: 0;
  z-index: 5;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.48);
}

.reference-confirmation {
  width: min(430px, 100%);
  border: 1px solid var(--ta-border-strong);
  border-radius: 8px;
  padding: 16px;
  background: var(--ta-panel);
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.24);
}

.reference-confirmation h3,
.reference-confirmation p {
  margin: 0;
}

.reference-confirmation h3 {
  font-size: 14px;
}

.reference-confirmation p {
  margin-top: 10px;
  color: var(--ta-muted);
  font-size: 12px;
  line-height: 1.6;
}

.reference-confirmation code {
  color: var(--ta-text);
  font-family: "Geist Mono", monospace;
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
.reference-verification-header p {
  margin: 0;
}

.reference-verification-header h3 {
  font-size: 14px;
  font-weight: 600;
}

.reference-verification-header p {
  margin-top: 3px;
  color: var(--ta-muted);
  font-size: 11px;
}

.reference-verification-target {
  display: grid;
  flex-shrink: 0;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr) auto;
  gap: 12px;
  border-bottom: 1px solid var(--ta-border);
  padding: 9px 14px;
  background: var(--ta-surface);
}

.reference-verification-target div {
  min-width: 0;
}

.reference-verification-target span,
.reference-verification-target strong,
.reference-verification-target code {
  display: block;
}

.reference-verification-target span {
  margin-bottom: 3px;
  color: var(--ta-muted);
  font-size: 9px;
  text-transform: uppercase;
}

.reference-verification-target strong,
.reference-verification-target code {
  overflow: hidden;
  color: var(--ta-text);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reference-verification-target code {
  font-family: "Geist Mono", monospace;
}

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

.reference-verification-steps > li.is-running {
  border-color: var(--ta-cyan);
  background: rgba(79, 111, 122, 0.07);
}

.reference-verification-steps > li.is-completed {
  border-color: var(--ta-ok);
  background: rgba(63, 122, 90, 0.07);
}

.reference-verification-steps > li.is-failed {
  border-color: var(--ta-error);
  background: rgba(158, 59, 52, 0.07);
}

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

.reference-verification-steps > li.is-running .reference-verification-marker {
  border-color: var(--ta-cyan);
  color: var(--ta-cyan);
}

.reference-verification-steps > li.is-completed .reference-verification-marker {
  border-color: var(--ta-ok);
  color: var(--ta-ok);
}

.reference-verification-steps > li.is-failed .reference-verification-marker {
  border-color: var(--ta-error);
  color: var(--ta-error);
}

.reference-verification-steps strong,
.reference-verification-steps small {
  display: block;
}

.reference-verification-steps strong {
  color: var(--ta-text);
  font-size: 11px;
  font-weight: 600;
}

.reference-verification-steps small {
  margin-top: 2px;
  color: var(--ta-muted);
  font-size: 9px;
}

.reference-verification-step-status {
  color: var(--ta-muted);
  font-size: 10px;
  white-space: nowrap;
}

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
.reference-verification-server > div small {
  display: inline;
}

.reference-verification-server > div small {
  margin-left: 5px;
}

.reference-verification-server > span {
  color: var(--ta-muted);
  font-size: 10px;
  font-weight: 600;
}

.reference-verification-server > span.is-completed {
  color: var(--ta-ok);
}

.reference-verification-server > span.is-failed,
.reference-verification-server > small.is-error {
  color: var(--ta-error);
}

.reference-verification-server code {
  overflow: hidden;
  color: var(--ta-text);
  font-family: "Geist Mono", monospace;
  font-size: 9px;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reference-verification-server > small.is-error {
  grid-column: 1 / -1;
  margin: 0;
}

.reference-verification-server-empty {
  padding: 4px 6px;
  color: var(--ta-muted);
  font-size: 10px;
}

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

.reference-verification-error.is-retrying {
  border-color: rgba(79, 111, 122, 0.35);
  background: rgba(79, 111, 122, 0.07);
  color: var(--ta-cyan);
}

.reference-verification-error code {
  font-family: "Geist Mono", monospace;
  font-size: 9px;
}

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

  .reference-selected-heading {
    min-height: 72px;
    align-items: flex-start;
    flex-direction: column;
    justify-content: center;
    gap: 6px;
    padding-top: 7px;
    padding-bottom: 7px;
  }

  .reference-selected-actions {
    width: 100%;
    max-width: none;
  }

  .reference-repository-path {
    max-width: none;
    flex: 1;
  }

  .reference-verification-target {
    grid-template-columns: 1fr;
    gap: 7px;
  }

  .reference-verification-server {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .reference-verification-server code {
    grid-column: 1 / -1;
    text-align: left;
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
