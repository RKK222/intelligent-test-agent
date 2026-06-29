<script setup lang="ts">
import { computed, ref, watch } from "vue";
import {
  AlertTriangle,
  Check,
  FolderGit2,
  GitBranch,
  GitCompare,
  ArrowUpFromLine,
  Globe2,
  Loader2,
  Plus,
  RefreshCw,
  Upload,
  Users
} from "lucide-vue-next";
import { createBackendApiClient } from "@test-agent/backend-api";
import { useWorkbenchStore } from "@test-agent/workbench-shell";
import { Button, Input } from "@test-agent/ui-kit";
import type {
  AgentConfigDiffFile,
  AgentConfigProgressEvent,
  AgentConfigStatus,
  AgentConfigWorktree,
  AgentConfigWorktreeOption,
  FileContent,
  FileTreeEntry,
  PublicAgentRepositoryStatus
} from "@test-agent/shared-types";
import { formatAgentConfigError } from "./agentConfigErrors";
import PublicDirectoryNode from "./PublicDirectoryNode.vue";

const props = defineProps<{
  baseUrl: string;
  workspaceId?: string;
  canWrite: boolean;
  hideHeader?: boolean;
  hideGitOps?: boolean;
}>();

const emit = defineEmits<{
  openFile: [payload: { scope: "PUBLIC" | "WORKSPACE"; path: string; content: FileContent; readonly: boolean; worktreeId?: string | null; linuxServerId?: string | null }];
}>();

const workbench = useWorkbenchStore();
const api = createBackendApiClient({ baseUrl: props.baseUrl });

type Scope = "PUBLIC" | "WORKSPACE";

const status = ref<{ PUBLIC?: AgentConfigStatus; WORKSPACE?: AgentConfigStatus }>({});
const entriesByScope = ref<Record<Scope, Record<string, FileTreeEntry[]>>>({ PUBLIC: {}, WORKSPACE: {} });
const rootExpanded = ref<Set<Scope>>(new Set(["PUBLIC"]));
const expandedByScope = ref<Record<Scope, Set<string>>>({ PUBLIC: new Set(), WORKSPACE: new Set() });
const loadingByScope = ref<Record<Scope, Set<string>>>({ PUBLIC: new Set(), WORKSPACE: new Set() });
const errorMessage = ref("");
const activeScope = ref<Scope>("PUBLIC");
const diffFiles = ref<AgentConfigDiffFile[]>([]);
const selectedDiffPath = ref("");
const commitMessage = ref("");
const progressEvents = ref<AgentConfigProgressEvent[]>([]);

const publicWorktree = computed<AgentConfigWorktree | null>({
  get: () => workbench.publicWorktree,
  set: (val) => { workbench.publicWorktree = val; }
});
const workspaceWorktree = computed<AgentConfigWorktree | null>({
  get: () => workbench.workspaceWorktree,
  set: (val) => { workbench.workspaceWorktree = val; }
});
const publicConfigLinuxServerId = computed<string | null>({
  get: () => workbench.publicConfigLinuxServerId,
  set: (val) => { workbench.publicConfigLinuxServerId = val; }
});

const busy = ref(false);
const activeWorktree = computed(() => activeScope.value === "PUBLIC" ? publicWorktree.value : workspaceWorktree.value);
const selectedDiff = computed(() => diffFiles.value.find((file) => file.path === selectedDiffPath.value) ?? diffFiles.value[0]);

void refreshAll();

watch(
  () => props.workspaceId,
  () => {
    entriesByScope.value = { PUBLIC: entriesByScope.value.PUBLIC, WORKSPACE: {} };
    if (workbench.workspaceWorktree) {
      workbench.workspaceWorktree = null;
    }
    void refreshStatus();
    if (rootExpanded.value.has("WORKSPACE")) {
      void loadDirectory("WORKSPACE", "");
    }
  }
);

async function refreshAll() {
  await refreshStatus();
  if (status.value.PUBLIC?.enabled !== false) {
    await loadDirectory("PUBLIC", "");
  }
  if (props.workspaceId) {
    await loadDirectory("WORKSPACE", "");
  }
}

async function refreshStatus() {
  try {
    const next: { PUBLIC?: AgentConfigStatus; WORKSPACE?: AgentConfigStatus } = {
      PUBLIC: await api.getPublicAgentConfigStatus()
    };
    if (props.workspaceId) {
      next.WORKSPACE = await api.getWorkspaceAgentConfigStatus(props.workspaceId);
    }
    status.value = next;
  } catch (error) {
    errorMessage.value = formatAgentConfigError(error, "加载 Agent 状态失败");
  }
}

function worktreeId(scope: Scope) {
  return scope === "PUBLIC" ? publicWorktree.value?.worktreeId : workspaceWorktree.value?.worktreeId;
}

async function loadDirectory(scope: Scope, path: string) {
  if (scope === "WORKSPACE" && !props.workspaceId) return;
  if (scope === "PUBLIC" && status.value.PUBLIC?.enabled === false) return;
  if (entriesByScope.value[scope][path] !== undefined || loadingByScope.value[scope].has(path)) return;
  loadingByScope.value = { ...loadingByScope.value, [scope]: new Set([...loadingByScope.value[scope], path]) };
  errorMessage.value = "";
  try {
    const linuxServerId = scope === "PUBLIC" ? await publicFileLinuxServerId() : undefined;
    const entries = scope === "PUBLIC"
      ? await api.listPublicAgentFiles(path, worktreeId(scope), linuxServerId)
      : await api.listWorkspaceAgentFiles(props.workspaceId!, path, worktreeId(scope));
    entriesByScope.value = {
      ...entriesByScope.value,
      [scope]: { ...entriesByScope.value[scope], [path]: entries }
    };
  } catch (error) {
    errorMessage.value = formatAgentConfigError(error, "加载 Agent 文件失败");
    const nextExpanded = new Set(expandedByScope.value[scope]);
    nextExpanded.delete(path);
    expandedByScope.value = { ...expandedByScope.value, [scope]: nextExpanded };
  } finally {
    const next = new Set(loadingByScope.value[scope]);
    next.delete(path);
    loadingByScope.value = { ...loadingByScope.value, [scope]: next };
  }
}

function toggleRoot(scope: Scope) {
  activeScope.value = scope;
  const next = new Set(rootExpanded.value);
  if (next.has(scope)) {
    next.delete(scope);
  } else {
    next.add(scope);
    void loadDirectory(scope, "");
  }
  rootExpanded.value = next;
}

function toggleDirectory(scope: Scope, path: string) {
  activeScope.value = scope;
  if (loadingByScope.value[scope].has(path)) return;
  const next = new Set(expandedByScope.value[scope]);
  if (next.has(path)) {
    next.delete(path);
  } else {
    next.add(path);
    void loadDirectory(scope, path);
  }
  expandedByScope.value = { ...expandedByScope.value, [scope]: next };
}

async function openFile(scope: Scope, path: string) {
  activeScope.value = scope;
  try {
    const linuxServerId = scope === "PUBLIC" ? await publicFileLinuxServerId() : undefined;
    const file = scope === "PUBLIC"
      ? await api.readPublicAgentFile(path, worktreeId(scope), linuxServerId)
      : await api.readWorkspaceAgentFile(props.workspaceId!, path, worktreeId(scope));
    emit("openFile", { scope, path, content: file, readonly: !props.canWrite, worktreeId: worktreeId(scope), linuxServerId });
  } catch (error) {
    errorMessage.value = formatAgentConfigError(error, "读取 Agent 文件失败");
  }
}

async function refreshScope(scope: Scope) {
  entriesByScope.value = { ...entriesByScope.value, [scope]: {} };
  await refreshStatus();
  if (scope !== "PUBLIC" || status.value.PUBLIC?.enabled !== false) {
    await loadDirectory(scope, "");
  }
}

// “更新公共配置”操作的正在进行状态标记，用以控制按钮禁用和加载动效
const updatingPublicConfig = ref(false);
// 正在创建 worktree 的作用域（PUBLIC 或 WORKSPACE），用以控制各创建按钮的加载动效
const creatingWorktreeScope = ref<Scope | null>(null);

// 更新公共配置弹窗的控制状态
const showUpdatePublicConfigModal = ref(false);
const updatePublicConfigBranch = ref("main");
const updatePublicConfigBranches = ref<string[]>([]);
const loadingUpdatePublicConfigBranches = ref(false);
const updatePublicConfigError = ref("");

/**
 * 触发更新公共配置流程，初始化弹窗状态，加载远端分支列表并打开弹窗
 */
async function updatePublicConfig() {
  if (!props.canWrite) return;
  showUpdatePublicConfigModal.value = true;
  loadingUpdatePublicConfigBranches.value = true;
  updatePublicConfigError.value = "";
  try {
    const branches = await api.listPublicAgentBranches();
    updatePublicConfigBranches.value = branches;
    updatePublicConfigBranch.value = status.value.PUBLIC?.currentBranch ?? branches[0] ?? "main";
  } catch (error) {
    updatePublicConfigError.value = formatAgentConfigError(error, "获取远端分支列表失败");
    updatePublicConfigBranches.value = [];
  } finally {
    loadingUpdatePublicConfigBranches.value = false;
  }
}

// 关闭更新公共配置弹窗并重置状态
function closeUpdatePublicConfigModal() {
  showUpdatePublicConfigModal.value = false;
  updatePublicConfigError.value = "";
}

/**
 * 提交更新公共配置请求
 */
async function submitUpdatePublicConfig() {
  const branch = updatePublicConfigBranch.value;
  if (!branch) return;

  closeUpdatePublicConfigModal();

  updatingPublicConfig.value = true;
  try {
    const operationId = newOperationId();
    await runOperation(() => api.updatePublicAgentConfig(branch, operationId), "公共 Agent 更新", operationId);
    await refreshScope("PUBLIC");
  } finally {
    updatingPublicConfig.value = false;
  }
}

// 创建 worktree 弹窗的控制状态
const showCreateWorktreeModal = ref(false);
const createWorktreeScope = ref<Scope | null>(null);
const newWorktreeName = ref("change-agent-md");
const createWorktreeOptionsLoading = ref(false);
const createWorktreeOptionsError = ref("");
const publicBranches = ref<string[]>([]);
const publicRepositories = ref<PublicAgentRepositoryStatus[]>([]);
const selectedPublicBranch = ref("");
const selectedPublicLinuxServerId = ref("");
const DIRECT_PUBLIC_CONFIG_OPTION = "__direct_public_config__";
const showSwitchWorktreeModal = ref(false);
const switchWorktreeOptionsLoading = ref(false);
const switchWorktreeOptionsError = ref("");
const switchPublicLinuxServerId = ref("");
const switchPublicWorktreeId = ref(DIRECT_PUBLIC_CONFIG_OPTION);
const switchPublicWorktrees = ref<AgentConfigWorktreeOption[]>([]);

const initializedPublicRepositories = computed(() =>
  publicRepositories.value.filter((repository) => repository.initialized)
);

const publicRootBadge = computed(() => {
  if (publicWorktree.value) {
    return publicWorktree.value.worktreeName;
  }
  const serverId = publicConfigLinuxServerId.value;
  if (!serverId) {
    return "";
  }
  return publicRepositories.value.find((repository) => repository.linuxServerId === serverId)?.serverName ?? serverId;
});

// 获取当前作用域下的 Git 库分支名称
const currentRepoBranch = computed(() => {
  if (!createWorktreeScope.value) return "main";
  if (createWorktreeScope.value === "PUBLIC") return selectedPublicBranch.value || publicBranches.value[0] || "main";
  return status.value[createWorktreeScope.value]?.currentBranch ?? "main";
});

const selectedPublicRepository = computed(() =>
  initializedPublicRepositories.value.find((repository) => repository.linuxServerId === selectedPublicLinuxServerId.value) ?? null
);

const selectedSwitchRepository = computed(() =>
  initializedPublicRepositories.value.find((repository) => repository.linuxServerId === switchPublicLinuxServerId.value) ?? null
);

const selectedSwitchWorktree = computed(() =>
  switchPublicWorktrees.value.find((worktree) => worktree.worktreeId === switchPublicWorktreeId.value) ?? null
);

const canSubmitCreateWorktree = computed(() => {
  if (!newWorktreeName.value.trim() || busy.value || createWorktreeOptionsLoading.value) return false;
  if (createWorktreeScope.value !== "PUBLIC") return true;
  return !!selectedPublicBranch.value && !!selectedPublicLinuxServerId.value;
});

const canSubmitSwitchWorktree = computed(() =>
  !busy.value &&
  !switchWorktreeOptionsLoading.value &&
  !!switchPublicLinuxServerId.value &&
  initializedPublicRepositories.value.length > 0
);

// 弹窗的标题
const createModalTitle = computed(() => {
  return createWorktreeScope.value === "PUBLIC" ? "创建公共 worktree" : "创建工作空间 worktree";
});

/**
 * 触发创建 worktree 流程，初始化弹窗状态并打开弹窗
 * @param scope 作用域 (PUBLIC 或 WORKSPACE)
 */
async function createWorktree(scope: Scope) {
  if (!props.canWrite) return;
  if (scope === "WORKSPACE" && !props.workspaceId) return;
  createWorktreeScope.value = scope;
  newWorktreeName.value = "change-agent-md";
  createWorktreeOptionsError.value = "";
  showCreateWorktreeModal.value = true;
  if (scope === "PUBLIC") {
    await loadPublicCreateOptions();
  }
}

// 关闭创建 worktree 弹窗并重置状态
function closeCreateWorktreeModal() {
  showCreateWorktreeModal.value = false;
  createWorktreeScope.value = null;
  createWorktreeOptionsError.value = "";
}

async function loadPublicCreateOptions() {
  createWorktreeOptionsLoading.value = true;
  createWorktreeOptionsError.value = "";
  try {
    const [branches, repositories] = await Promise.all([
      api.listPublicAgentBranches(),
      api.listPublicAgentRepositories()
    ]);
    publicBranches.value = branches;
    publicRepositories.value = repositories;
    selectedPublicBranch.value = preferredPublicBranch(branches);
    selectedPublicLinuxServerId.value = preferredPublicServer(repositories);
  } catch (error) {
    createWorktreeOptionsError.value = formatAgentConfigError(error, "加载公共配置仓库选项失败");
    publicBranches.value = [];
    publicRepositories.value = [];
    selectedPublicBranch.value = "";
    selectedPublicLinuxServerId.value = "";
  } finally {
    createWorktreeOptionsLoading.value = false;
  }
}

function preferredPublicBranch(branches: string[]) {
  const current = status.value.PUBLIC?.currentBranch?.trim();
  if (current && branches.includes(current)) {
    return current;
  }
  return branches[0] ?? current ?? "main";
}

function preferredPublicServer(repositories: PublicAgentRepositoryStatus[]) {
  const activeServer = publicWorktree.value?.linuxServerId ?? publicConfigLinuxServerId.value;
  const initialized = repositories.filter((repository) => repository.initialized);
  if (activeServer && initialized.some((repository) => repository.linuxServerId === activeServer)) {
    return activeServer;
  }
  return initialized[0]?.linuxServerId ?? "";
}

async function publicFileLinuxServerId() {
  if (publicWorktree.value?.worktreeId) {
    return publicWorktree.value.linuxServerId ?? undefined;
  }
  if (publicRepositories.value.length === 0) {
    publicRepositories.value = await api.listPublicAgentRepositories();
  }
  const initialized = initializedPublicRepositories.value;
  const rememberedServer = publicConfigLinuxServerId.value ?? selectedPublicLinuxServerId.value;
  if (rememberedServer && initialized.some((repository) => repository.linuxServerId === rememberedServer)) {
    publicConfigLinuxServerId.value = rememberedServer;
    return rememberedServer;
  }
  const nextServer = preferredPublicServer(publicRepositories.value);
  selectedPublicLinuxServerId.value = nextServer;
  publicConfigLinuxServerId.value = nextServer || null;
  if (!nextServer) {
    throw new Error("没有已初始化服务器，请到系统管理 > 配置管理 > opencode公共配置管理初始化。");
  }
  return nextServer;
}

async function openSwitchWorktreeModal() {
  if (!props.canWrite || status.value.PUBLIC?.enabled === false) return;
  switchWorktreeOptionsError.value = "";
  switchPublicWorktreeId.value = DIRECT_PUBLIC_CONFIG_OPTION;
  switchPublicWorktrees.value = [];
  showSwitchWorktreeModal.value = true;
  await loadPublicSwitchOptions();
}

function closeSwitchWorktreeModal() {
  showSwitchWorktreeModal.value = false;
  switchWorktreeOptionsError.value = "";
}

async function loadPublicSwitchOptions() {
  switchWorktreeOptionsLoading.value = true;
  switchWorktreeOptionsError.value = "";
  try {
    publicRepositories.value = await api.listPublicAgentRepositories();
    switchPublicLinuxServerId.value = preferredPublicServer(publicRepositories.value);
    if (!switchPublicLinuxServerId.value) {
      switchWorktreeOptionsError.value = "没有已初始化服务器，请到系统管理 > 配置管理 > opencode公共配置管理初始化。";
      switchPublicWorktrees.value = [];
      switchPublicWorktreeId.value = DIRECT_PUBLIC_CONFIG_OPTION;
      return;
    }
  } catch (error) {
    switchWorktreeOptionsError.value = formatAgentConfigError(error, "加载公共配置服务器失败");
    publicRepositories.value = [];
    switchPublicLinuxServerId.value = "";
    switchPublicWorktrees.value = [];
    switchPublicWorktreeId.value = DIRECT_PUBLIC_CONFIG_OPTION;
    return;
  } finally {
    switchWorktreeOptionsLoading.value = false;
  }
  await loadSwitchWorktreesForSelectedServer();
}

async function loadSwitchWorktreesForSelectedServer() {
  const serverId = switchPublicLinuxServerId.value;
  switchPublicWorktrees.value = [];
  switchPublicWorktreeId.value = DIRECT_PUBLIC_CONFIG_OPTION;
  if (!serverId) return;
  switchWorktreeOptionsLoading.value = true;
  switchWorktreeOptionsError.value = "";
  try {
    const worktrees = await api.listPublicAgentWorktrees(serverId);
    switchPublicWorktrees.value = worktrees;
    if (publicWorktree.value?.linuxServerId === serverId && worktrees.some((item) => item.worktreeId === publicWorktree.value?.worktreeId)) {
      switchPublicWorktreeId.value = publicWorktree.value.worktreeId;
    }
  } catch (error) {
    switchWorktreeOptionsError.value = formatAgentConfigError(error, "加载公共 worktree 列表失败");
  } finally {
    switchWorktreeOptionsLoading.value = false;
  }
}

async function submitSwitchWorktree() {
  const serverId = switchPublicLinuxServerId.value;
  if (!serverId || !canSubmitSwitchWorktree.value) return;
  publicConfigLinuxServerId.value = serverId;
  selectedPublicLinuxServerId.value = serverId;
  publicWorktree.value = selectedSwitchWorktree.value ? { ...selectedSwitchWorktree.value } : null;
  resetPublicFileTree();
  if (rootExpanded.value.has("PUBLIC")) {
    await loadDirectory("PUBLIC", "");
  }
  closeSwitchWorktreeModal();
}

function resetPublicFileTree() {
  entriesByScope.value = { ...entriesByScope.value, PUBLIC: {} };
  expandedByScope.value = { ...expandedByScope.value, PUBLIC: new Set() };
  loadingByScope.value = { ...loadingByScope.value, PUBLIC: new Set() };
  if (activeScope.value === "PUBLIC") {
    diffFiles.value = [];
    selectedDiffPath.value = "";
  }
}

function worktreeOptionLabel(worktree: AgentConfigWorktreeOption) {
  const creator = worktree.createdByUsername
    ? `${worktree.createdByUserId} / ${worktree.createdByUsername}`
    : `${worktree.createdByUserId} / 未知用户`;
  return `${worktree.worktreeName} / ${worktree.branch} / ${creator}`;
}

/**
 * 提交创建 worktree 请求到后端，并更新本地文件树
 */
async function submitCreateWorktree() {
  const scope = createWorktreeScope.value;
  if (!scope) return;
  const baseName = newWorktreeName.value.trim();
  if (!baseName) return;
  const branch = currentRepoBranch.value;
  const targetLinuxServerId = selectedPublicLinuxServerId.value;
  if (scope === "PUBLIC" && (!selectedPublicBranch.value || !selectedPublicLinuxServerId.value)) {
    createWorktreeOptionsError.value = "没有已初始化服务器，请到系统管理 > 配置管理 > opencode公共配置管理初始化。";
    return;
  }

  closeCreateWorktreeModal();

  creatingWorktreeScope.value = scope;
  try {
    const operationId = newOperationId();
    const created = await runOperation(
      () =>
        scope === "PUBLIC"
          ? api.createPublicAgentWorktree({ baseName, branch, linuxServerId: targetLinuxServerId, operationId })
          : api.createWorkspaceAgentWorktree(props.workspaceId!, { baseName, branch, operationId }),
      "创建 Agent worktree",
      operationId
    );
    if (!created) return;
    if (scope === "PUBLIC") {
      publicWorktree.value = created;
      publicConfigLinuxServerId.value = created.linuxServerId ?? targetLinuxServerId ?? null;
    } else {
      workspaceWorktree.value = created;
    }
    entriesByScope.value = { ...entriesByScope.value, [scope]: {} };
    await loadDirectory(scope, "");
  } finally {
    creatingWorktreeScope.value = null;
  }
}

async function loadDiff(scope = activeScope.value) {
  if (scope === "WORKSPACE" && !props.workspaceId) return;
  activeScope.value = scope;
  busy.value = true;
  try {
    const diff = scope === "PUBLIC"
      ? await api.getPublicAgentDiff(worktreeId(scope))
      : await api.getWorkspaceAgentDiff(props.workspaceId!, worktreeId(scope));
    diffFiles.value = diff.files;
    selectedDiffPath.value = diff.files[0]?.path ?? "";
  } catch (error) {
    errorMessage.value = formatAgentConfigError(error, "加载 Agent Diff 失败");
  } finally {
    busy.value = false;
  }
}

async function stage(file: AgentConfigDiffFile) {
  if (!props.canWrite) return;
  busy.value = true;
  try {
    if (activeScope.value === "PUBLIC") {
      await api.stagePublicAgentFiles([file.path], worktreeId(activeScope.value));
    } else {
      await api.stageWorkspaceAgentFiles(props.workspaceId!, [file.path], worktreeId(activeScope.value));
    }
    await loadDiff(activeScope.value);
  } catch (error) {
    errorMessage.value = formatAgentConfigError(error, "暂存 Agent 文件失败");
  } finally {
    busy.value = false;
  }
}

async function commit() {
  if (!props.canWrite || !commitMessage.value.trim()) return;
  const message = commitMessage.value.trim();
  const operationId = newOperationId();
  await runOperation(
    () =>
      activeScope.value === "PUBLIC"
        ? api.commitPublicAgentConfig({ message, worktreeId: worktreeId(activeScope.value), operationId })
        : api.commitWorkspaceAgentConfig(props.workspaceId!, { message, worktreeId: worktreeId(activeScope.value), operationId }),
    "提交 Agent 配置",
    operationId
  );
  commitMessage.value = "";
  await loadDiff(activeScope.value);
}

async function publish() {
  if (!props.canWrite) return;
  const operationId = newOperationId();
  await runOperation(
    () =>
      activeScope.value === "PUBLIC"
        ? api.publishPublicAgentConfig(worktreeId(activeScope.value), operationId)
        : api.publishWorkspaceAgentConfig(props.workspaceId!, worktreeId(activeScope.value), operationId),
    "发布 Agent 配置",
    operationId
  );
  await refreshScope(activeScope.value);
}

async function runOperation<T>(action: () => Promise<T>, label: string, knownOperationId?: string): Promise<T | null> {
  busy.value = true;
  errorMessage.value = "";
  const operationId = knownOperationId ?? newOperationId();
  progressEvents.value = [{ type: "snapshot", operationId, currentStep: label, status: "RUNNING" }];
  let socket: { close: () => void } | null = null;
  try {
    socket = await api.connectAgentConfigProgress(operationId, (event) => {
      progressEvents.value = [...progressEvents.value.slice(-8), event];
    });
  } catch {
    socket = null;
  }
  try {
    const result = await action();
    return result;
  } catch (error) {
    errorMessage.value = formatAgentConfigError(error, `${label}失败`);
    return null;
  } finally {
    busy.value = false;
    setTimeout(() => socket?.close(), 1200);
  }
}

function newOperationId() {
  const random = typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID().replaceAll("-", "")
    : `${Date.now().toString(36)}${Math.random().toString(36).slice(2)}`;
  return `aco_${random}`;
}

defineExpose({
  refreshAll,
  busy
});
</script>

<template>
  <div class="agent-config-panel">
    <div v-if="!hideHeader" class="agent-config-header">
      <span>Agent</span>
      <button type="button" class="agent-icon-btn" title="刷新" aria-label="刷新" :disabled="busy" @click="refreshAll">
        <RefreshCw class="h-3.5 w-3.5" :class="{ 'animate-spin': busy }" :stroke-width="1.5" />
      </button>
    </div>
    <div v-if="errorMessage" class="agent-error">
      <AlertTriangle class="mt-[2px] h-3.5 w-3.5 shrink-0" :stroke-width="1.5" />
      <span>{{ errorMessage }}</span>
    </div>

    <div class="agent-tree">
      <div class="agent-root-row" :class="{ active: activeScope === 'PUBLIC' }">
        <button type="button" class="agent-root-main" @click="toggleRoot('PUBLIC')">
          <Globe2 class="h-3.5 w-3.5" :stroke-width="1.5" />
          <span>公共级</span>
          <span v-if="publicRootBadge" class="agent-root-badge">{{ publicRootBadge }}</span>
        </button>
        <button
          v-if="canWrite"
          type="button"
          class="agent-icon-btn"
          title="更新公共配置"
          aria-label="更新公共配置"
          :disabled="busy || status.PUBLIC?.enabled === false"
          @click="updatePublicConfig"
        >
          <Loader2 v-if="updatingPublicConfig" class="h-3.5 w-3.5 animate-spin" />
          <ArrowUpFromLine v-else class="h-3.5 w-3.5" :stroke-width="1.5" />
        </button>
        <button
          v-if="canWrite"
          type="button"
          class="agent-icon-btn"
          title="切换公共 worktree"
          aria-label="切换公共 worktree"
          :disabled="busy || status.PUBLIC?.enabled === false"
          @click="openSwitchWorktreeModal"
        >
          <GitBranch class="h-3.5 w-3.5" :stroke-width="1.5" />
        </button>
        <button
          v-if="canWrite"
          type="button"
          class="agent-icon-btn"
          title="创建公共 worktree"
          aria-label="创建公共 worktree"
          :disabled="busy || status.PUBLIC?.enabled === false"
          @click="createWorktree('PUBLIC')"
        >
          <Loader2 v-if="creatingWorktreeScope === 'PUBLIC'" class="h-3.5 w-3.5 animate-spin" />
          <Plus v-else class="h-3.5 w-3.5" :stroke-width="1.5" />
        </button>
      </div>
      <div v-if="rootExpanded.has('PUBLIC')" class="agent-node-list">
        <div v-if="loadingByScope.PUBLIC.has('')" class="agent-loading"><Loader2 class="h-3.5 w-3.5 animate-spin" />加载中</div>
        <PublicDirectoryNode
          v-for="entry in entriesByScope.PUBLIC[''] ?? []"
          :key="`PUBLIC:${entry.path}`"
          :entry="entry"
          :depth="0"
          :entries-by-directory="entriesByScope.PUBLIC"
          :expanded-directories="expandedByScope.PUBLIC"
          :loading-path="loadingByScope.PUBLIC"
          @toggle="(path) => toggleDirectory('PUBLIC', path)"
          @open-file="(path) => openFile('PUBLIC', path)"
        />
      </div>

      <div class="agent-root-row" :class="{ active: activeScope === 'WORKSPACE' }">
        <button type="button" class="agent-root-main" :disabled="!workspaceId" @click="toggleRoot('WORKSPACE')">
          <Users class="h-3.5 w-3.5" :stroke-width="1.5" />
          <span>工作空间级</span>
          <span v-if="workspaceWorktree" class="agent-root-badge">{{ workspaceWorktree.worktreeName }}</span>
        </button>
        <button
          v-if="canWrite"
          type="button"
          class="agent-icon-btn"
          title="创建工作空间 worktree"
          aria-label="创建工作空间 worktree"
          :disabled="busy || !workspaceId"
          @click="createWorktree('WORKSPACE')"
        >
          <Loader2 v-if="creatingWorktreeScope === 'WORKSPACE'" class="h-3.5 w-3.5 animate-spin" />
          <Plus v-else class="h-3.5 w-3.5" :stroke-width="1.5" />
        </button>
      </div>
      <div v-if="rootExpanded.has('WORKSPACE')" class="agent-node-list">
        <div v-if="loadingByScope.WORKSPACE.has('')" class="agent-loading"><Loader2 class="h-3.5 w-3.5 animate-spin" />加载中</div>
        <PublicDirectoryNode
          v-for="entry in entriesByScope.WORKSPACE[''] ?? []"
          :key="`WORKSPACE:${entry.path}`"
          :entry="entry"
          :depth="0"
          :entries-by-directory="entriesByScope.WORKSPACE"
          :expanded-directories="expandedByScope.WORKSPACE"
          :loading-path="loadingByScope.WORKSPACE"
          @toggle="(path) => toggleDirectory('WORKSPACE', path)"
          @open-file="(path) => openFile('WORKSPACE', path)"
        />
      </div>
    </div>

    <div v-if="canWrite && !hideGitOps" class="agent-diff">
      <div class="agent-diff-toolbar">
        <button type="button" class="agent-action-btn" :disabled="busy" @click="loadDiff()">
          <GitCompare class="h-3.5 w-3.5" :stroke-width="1.5" />
          Diff
        </button>
        <button type="button" class="agent-action-btn" :disabled="busy" @click="publish">
          <Upload class="h-3.5 w-3.5" :stroke-width="1.5" />
          发布
        </button>
      </div>
      <div class="agent-diff-body">
        <div class="agent-diff-files">
          <button
            v-for="file in diffFiles"
            :key="file.path"
            type="button"
            class="agent-diff-file"
            :class="{ active: selectedDiff?.path === file.path }"
            @click="selectedDiffPath = file.path"
            @dblclick="stage(file)"
          >
            <span>{{ file.status || 'M' }}</span>
            <span>{{ file.path }}</span>
            <Check v-if="file.staged" class="h-3 w-3" :stroke-width="1.5" />
          </button>
        </div>
        <pre v-if="selectedDiff" class="agent-diff-preview">{{ selectedDiff.patch || '无文本差异' }}</pre>
      </div>
      <div class="agent-commit-row">
        <input v-model="commitMessage" class="agent-commit-input" placeholder="commit message" :disabled="busy" />
        <button type="button" class="agent-action-btn" :disabled="busy || !commitMessage.trim()" @click="commit">
          <FolderGit2 class="h-3.5 w-3.5" :stroke-width="1.5" />
          提交
        </button>
      </div>
      <div v-if="progressEvents.length" class="agent-progress">
        <div v-for="(event, index) in progressEvents" :key="`${event.type}-${index}`">
          {{ event.type }} · {{ event.currentStep ?? event.operation?.currentStep ?? '-' }} · {{ event.status ?? event.operation?.status ?? '' }}
        </div>
      </div>
    </div>
    <Teleport to="body">
      <div
        v-if="showSwitchWorktreeModal"
        class="fixed inset-0 z-[1000] flex items-center justify-center bg-black/35 px-4 py-6"
        @keydown.esc="closeSwitchWorktreeModal"
      >
        <section
          role="dialog"
          aria-modal="true"
          aria-label="切换公共 worktree"
          class="flex w-[min(440px,calc(100vw-24px))] flex-col rounded-lg border border-[var(--ta-border)] bg-[var(--ta-panel)] shadow-xl p-4 gap-4"
        >
          <header class="flex items-center justify-between border-b border-[var(--ta-border)] pb-2">
            <h2 class="text-[14px] font-semibold text-[var(--ta-text)]">切换公共 worktree</h2>
          </header>

          <div class="flex flex-col gap-3">
            <div v-if="switchWorktreeOptionsError" class="agent-modal-alert">
              <AlertTriangle class="h-3.5 w-3.5 shrink-0" :stroke-width="1.5" />
              <span>{{ switchWorktreeOptionsError }}</span>
            </div>

            <div v-if="switchWorktreeOptionsLoading" class="agent-modal-loading">
              <Loader2 class="h-3.5 w-3.5 animate-spin" />
              <span>加载公共配置服务器和 worktree</span>
            </div>

            <div class="flex flex-col gap-1.5">
              <label for="public-switch-server" class="text-[11px] text-[var(--ta-muted)] font-medium">服务器</label>
              <div class="agent-modal-select">
                <Globe2 class="h-3.5 w-3.5 text-[var(--ta-muted)]" :stroke-width="1.5" />
                <select
                  id="public-switch-server"
                  v-model="switchPublicLinuxServerId"
                  :disabled="switchWorktreeOptionsLoading || initializedPublicRepositories.length === 0"
                  @change="loadSwitchWorktreesForSelectedServer"
                >
                  <option
                    v-for="repository in initializedPublicRepositories"
                    :key="repository.linuxServerId"
                    :value="repository.linuxServerId"
                  >
                    {{ repository.serverName || repository.linuxServerId }}
                  </option>
                </select>
              </div>
              <span v-if="selectedSwitchRepository" class="agent-modal-help">
                {{ selectedSwitchRepository.gitRootPath }}
              </span>
              <span v-else-if="!switchWorktreeOptionsLoading" class="agent-modal-help">
                没有已初始化服务器，请到系统管理 &gt; 配置管理 &gt; opencode公共配置管理初始化。
              </span>
            </div>

            <div class="flex flex-col gap-1.5">
              <label for="public-switch-worktree" class="text-[11px] text-[var(--ta-muted)] font-medium">公共 worktree</label>
              <div class="agent-modal-select">
                <GitBranch class="h-3.5 w-3.5 text-[var(--ta-muted)]" :stroke-width="1.5" />
                <select
                  id="public-switch-worktree"
                  v-model="switchPublicWorktreeId"
                  :disabled="switchWorktreeOptionsLoading || !switchPublicLinuxServerId"
                >
                  <option :value="DIRECT_PUBLIC_CONFIG_OPTION">直接公共配置目录</option>
                  <option
                    v-for="worktree in switchPublicWorktrees"
                    :key="worktree.worktreeId"
                    :value="worktree.worktreeId"
                  >
                    {{ worktreeOptionLabel(worktree) }}
                  </option>
                </select>
              </div>
              <span v-if="selectedSwitchWorktree" class="agent-modal-help">
                分支：{{ selectedSwitchWorktree.branch }} · 创建人：{{ selectedSwitchWorktree.createdByUserId }} / {{ selectedSwitchWorktree.createdByUsername ?? '未知用户' }}
              </span>
              <span v-else class="agent-modal-help">
                选择直接公共配置目录时，文件操作会绑定到当前服务器的公共配置目录。
              </span>
            </div>
          </div>

          <footer class="flex justify-end gap-2 pt-2 border-t border-[var(--ta-border)]">
            <Button variant="ghost" size="sm" @click="closeSwitchWorktreeModal">取消</Button>
            <Button variant="primary" size="sm" :disabled="!canSubmitSwitchWorktree" @click="submitSwitchWorktree">确定</Button>
          </footer>
        </section>
      </div>
    </Teleport>
    <Teleport to="body">
      <div
        v-if="showUpdatePublicConfigModal"
        class="fixed inset-0 z-[1000] flex items-center justify-center bg-black/35 px-4 py-6"
        @keydown.esc="closeUpdatePublicConfigModal"
      >
        <section
          role="dialog"
          aria-modal="true"
          aria-label="更新公共配置"
          class="flex w-[min(380px,calc(100vw-24px))] flex-col rounded-lg border border-[var(--ta-border)] bg-[var(--ta-panel)] shadow-xl p-4 gap-4"
        >
          <header class="flex items-center justify-between border-b border-[var(--ta-border)] pb-2">
            <h2 class="text-[14px] font-semibold text-[var(--ta-text)]">更新公共配置</h2>
          </header>

          <div class="flex flex-col gap-3">
            <div v-if="updatePublicConfigError" class="agent-modal-alert">
              <AlertTriangle class="h-3.5 w-3.5 shrink-0" :stroke-width="1.5" />
              <span>{{ updatePublicConfigError }}</span>
            </div>

            <div v-if="loadingUpdatePublicConfigBranches" class="agent-modal-loading">
              <Loader2 class="h-3.5 w-3.5 animate-spin" />
              <span>加载远端分支中...</span>
            </div>

            <div v-else class="flex flex-col gap-1.5">
              <label for="update-public-branch" class="text-[11px] text-[var(--ta-muted)] font-medium">选择公共配置分支</label>
              <div class="agent-modal-select">
                <GitBranch class="h-3.5 w-3.5 text-[var(--ta-muted)]" :stroke-width="1.5" />
                <select id="update-public-branch" v-model="updatePublicConfigBranch">
                  <option v-for="branch in updatePublicConfigBranches" :key="branch" :value="branch">{{ branch }}</option>
                </select>
              </div>
            </div>
          </div>

          <footer class="flex justify-end gap-2 pt-2 border-t border-[var(--ta-border)]">
            <Button variant="ghost" size="sm" @click="closeUpdatePublicConfigModal">取消</Button>
            <Button variant="primary" size="sm" :disabled="loadingUpdatePublicConfigBranches || !updatePublicConfigBranch || busy" @click="submitUpdatePublicConfig">确定</Button>
          </footer>
        </section>
      </div>
    </Teleport>
    <Teleport to="body">
      <div
        v-if="showCreateWorktreeModal"
        class="fixed inset-0 z-[1000] flex items-center justify-center bg-black/35 px-4 py-6"
        @keydown.esc="closeCreateWorktreeModal"
      >
        <section
          role="dialog"
          aria-modal="true"
          :aria-label="createModalTitle"
          class="flex w-[min(380px,calc(100vw-24px))] flex-col rounded-lg border border-[var(--ta-border)] bg-[var(--ta-panel)] shadow-xl p-4 gap-4"
        >
          <header class="flex items-center justify-between border-b border-[var(--ta-border)] pb-2">
            <h2 class="text-[14px] font-semibold text-[var(--ta-text)]">{{ createModalTitle }}</h2>
          </header>

          <div class="flex flex-col gap-3">
            <div v-if="createWorktreeOptionsError" class="agent-modal-alert">
              <AlertTriangle class="h-3.5 w-3.5 shrink-0" :stroke-width="1.5" />
              <span>{{ createWorktreeOptionsError }}</span>
            </div>

            <div v-if="createWorktreeScope === 'PUBLIC'" class="flex flex-col gap-3">
              <div v-if="createWorktreeOptionsLoading" class="agent-modal-loading">
                <Loader2 class="h-3.5 w-3.5 animate-spin" />
                <span>加载远端分支和服务器状态</span>
              </div>

              <div class="flex flex-col gap-1.5">
                <label for="public-worktree-branch" class="text-[11px] text-[var(--ta-muted)] font-medium">远端分支</label>
                <div class="agent-modal-select">
                  <GitBranch class="h-3.5 w-3.5 text-[var(--ta-muted)]" :stroke-width="1.5" />
                  <select id="public-worktree-branch" v-model="selectedPublicBranch" :disabled="createWorktreeOptionsLoading">
                    <option v-for="branch in publicBranches" :key="branch" :value="branch">{{ branch }}</option>
                  </select>
                </div>
              </div>

              <div class="flex flex-col gap-1.5">
                <label for="public-worktree-server" class="text-[11px] text-[var(--ta-muted)] font-medium">目标服务器</label>
                <div class="agent-modal-select">
                  <Globe2 class="h-3.5 w-3.5 text-[var(--ta-muted)]" :stroke-width="1.5" />
                  <select id="public-worktree-server" v-model="selectedPublicLinuxServerId" :disabled="createWorktreeOptionsLoading || initializedPublicRepositories.length === 0">
                    <option
                      v-for="repository in initializedPublicRepositories"
                      :key="repository.linuxServerId"
                      :value="repository.linuxServerId"
                    >
                      {{ repository.serverName || repository.linuxServerId }}
                    </option>
                  </select>
                </div>
                <span v-if="selectedPublicRepository" class="agent-modal-help">
                  {{ selectedPublicRepository.gitRootPath }}
                </span>
                <span v-else-if="!createWorktreeOptionsLoading" class="agent-modal-help">
                  没有已初始化服务器，请到系统管理 &gt; 配置管理 &gt; opencode公共配置管理初始化。
                </span>
              </div>
            </div>

            <div v-else class="flex flex-col gap-1.5">
              <span class="text-[11px] text-[var(--ta-muted)] font-medium">当前 git 库分支</span>
              <div class="flex items-center gap-1.5 text-[13px] text-[var(--ta-text)] bg-[var(--ta-hover)] px-2.5 py-2 rounded border border-[var(--ta-border)]">
                <GitBranch class="h-3.5 w-3.5 text-[var(--ta-muted)]" :stroke-width="1.5" />
                <span class="font-mono font-medium truncate">{{ currentRepoBranch }}</span>
              </div>
            </div>

            <div class="flex flex-col gap-1.5">
              <label for="worktree-branch-input" class="text-[11px] text-[var(--ta-muted)] font-medium">worktree 名称</label>
              <Input
                id="worktree-branch-input"
                v-model="newWorktreeName"
                placeholder="请输入 worktree 名称"
                class="h-8 text-[13px]"
                autofocus
                @keydown.enter="submitCreateWorktree"
              />
            </div>
          </div>

          <footer class="flex justify-end gap-2 pt-2 border-t border-[var(--ta-border)]">
            <Button variant="ghost" size="sm" @click="closeCreateWorktreeModal">取消</Button>
            <Button variant="primary" size="sm" :disabled="!canSubmitCreateWorktree" @click="submitCreateWorktree">确定</Button>
          </footer>
        </section>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.agent-config-panel {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  background: var(--ta-panel, #fafafa);
  color: var(--ta-text, #18181b);
}
.agent-config-header,
.agent-root-row,
.agent-diff-toolbar,
.agent-commit-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.agent-config-header {
  height: 28px;
  justify-content: space-between;
  border-bottom: 1px solid var(--ta-border, #e4e4e7);
  padding: 0 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--ta-muted, #6b7280);
}
.agent-error {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--ta-border, #e4e4e7);
  background: #fff7ed;
  padding: 5px 8px;
  font-size: 12px;
  color: #9a3412;
}
.agent-modal-alert,
.agent-modal-loading {
  display: flex;
  align-items: center;
  gap: 6px;
  border-radius: 6px;
  padding: 7px 9px;
  font-size: 12px;
}
.agent-modal-alert {
  background: #fff7ed;
  color: #9a3412;
}
.agent-modal-loading {
  background: var(--ta-hover, #f4f4f5);
  color: var(--ta-muted, #6b7280);
}
.agent-modal-select {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 32px;
  border: 1px solid var(--ta-border, #e4e4e7);
  border-radius: 6px;
  background: var(--ta-panel, #fff);
  padding: 0 9px;
}
.agent-modal-select select {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: none;
  background: transparent;
  color: var(--ta-text, #18181b);
  font-size: 13px;
}
.agent-modal-help {
  overflow-wrap: anywhere;
  color: var(--ta-muted, #6b7280);
  font-size: 11px;
}
.agent-tree {
  min-height: 160px;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 8px;
}
.agent-root-row {
  min-height: 28px;
  border-radius: 6px;
}
.agent-root-row.active {
  background: #f4f4f5;
}
.agent-root-main {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 6px;
  border: 0;
  background: transparent;
  color: inherit;
  font-size: 13px;
  cursor: pointer;
}
.agent-root-badge {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ta-muted, #6b7280);
  font-size: 11px;
}
.agent-icon-btn,
.agent-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 24px;
  border: 1px solid transparent;
  border-radius: 5px;
  background: transparent;
  color: var(--ta-muted, #6b7280);
  font-size: 12px;
  cursor: pointer;
}
.agent-icon-btn {
  width: 24px;
  padding: 0;
  transition: background-color 0.1s, color 0.1s;
}
.agent-icon-btn:hover {
  background: var(--ta-hover, #f4f4f5);
  color: var(--ta-text, #18181b);
}
.agent-icon-btn:active {
  background: var(--ta-active, #e4e4e7);
}
.agent-action-btn {
  padding: 0 8px;
  border-color: var(--ta-border, #e4e4e7);
  background: #fff;
}
.agent-icon-btn:disabled,
.agent-action-btn:disabled,
.agent-root-main:disabled {
  pointer-events: none;
  opacity: 0.45;
}
.agent-node-list {
  padding-left: 8px;
}
.agent-loading {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  color: var(--ta-muted, #6b7280);
  font-size: 12px;
}
.agent-diff {
  display: flex;
  min-height: 260px;
  flex: 1;
  flex-direction: column;
  border-top: 1px solid var(--ta-border, #e4e4e7);
  padding: 8px;
  gap: 8px;
}
.agent-diff-body {
  display: grid;
  min-height: 0;
  flex: 1;
  grid-template-columns: minmax(120px, 38%) 1fr;
  gap: 8px;
}
.agent-diff-files {
  min-height: 0;
  overflow: auto;
  border: 1px solid var(--ta-border, #e4e4e7);
  border-radius: 6px;
  background: #fff;
}
.agent-diff-file {
  display: grid;
  width: 100%;
  grid-template-columns: 22px minmax(0, 1fr) 14px;
  gap: 4px;
  border: 0;
  border-bottom: 1px solid #f4f4f5;
  background: transparent;
  padding: 5px 6px;
  text-align: left;
  font-size: 12px;
  cursor: pointer;
}
.agent-diff-file.active {
  background: #eef2ff;
}
.agent-diff-file span:nth-child(2) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.agent-diff-preview {
  min-height: 0;
  overflow: auto;
  margin: 0;
  border: 1px solid var(--ta-border, #e4e4e7);
  border-radius: 6px;
  background: #fff;
  padding: 8px;
  font-size: 11px;
  line-height: 1.45;
  white-space: pre-wrap;
}
.agent-commit-input {
  min-width: 0;
  flex: 1;
  height: 26px;
  border: 1px solid var(--ta-border, #e4e4e7);
  border-radius: 5px;
  padding: 0 8px;
  font-size: 12px;
}
.agent-progress {
  max-height: 76px;
  overflow: auto;
  border: 1px solid var(--ta-border, #e4e4e7);
  border-radius: 6px;
  background: #fff;
  padding: 6px;
  color: var(--ta-muted, #6b7280);
  font-size: 11px;
}
</style>
