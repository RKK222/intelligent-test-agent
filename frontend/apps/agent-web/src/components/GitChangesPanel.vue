<script setup lang="ts">
import { computed, ref, watch } from "vue";
import {
  AlertTriangle,
  ChevronDown,
  ChevronRight,
  GitBranch,
  FolderGit2,
  Upload,
  Plus,
  Minus,
  RefreshCw,
  Loader2,
  Undo2
} from "lucide-vue-next";
import { createBackendApiClient, BackendApiError } from "@test-agent/backend-api";
import {
  useWorkbenchStore,
  mockVcsDiffFiles,
  mockPublicAgentDiffs,
  mockWorkspaceAgentDiffs
} from "@test-agent/workbench-shell";
import type {
  AgentConfigDiffFile,
  AgentConfigProgressEvent,
  RunDiffFile
} from "@test-agent/shared-types";
import { Badge } from "@test-agent/ui-kit";

const props = defineProps<{
  workspaceId?: string;
  /** 当前默认个人工作区 ID，用于提交并推送（合并回应用版本分支） */
  personalWorkspaceId?: string;
  apiBaseUrl?: string;
  canWrite: boolean;
}>();

const emit = defineEmits<{
  openDiff: [payload: { path: string; source: "vcs" | "agent"; scope?: "PUBLIC" | "WORKSPACE" }];
  "changes-refreshed": [payload?: { paths?: string[]; reloadOpenFiles?: boolean }];
}>();

const workbench = useWorkbenchStore();
const api = createBackendApiClient({ baseUrl: props.apiBaseUrl ?? "" });

// Resizing unstaged / staged boundary
const unstagedHeight = ref<number | null>(null);
const resizingUnstaged = ref(false);
let dragStartY = 0;
let dragStartHeight = 0;

function onUnstagedResizeStart(event: MouseEvent) {
  resizingUnstaged.value = true;
  dragStartY = event.clientY;
  const el = document.querySelector(".git-unstaged-section") as HTMLElement;
  dragStartHeight = el ? el.offsetHeight : 250;
  
  document.addEventListener("mousemove", onUnstagedResizeMove);
  document.addEventListener("mouseup", onUnstagedResizeEnd);
  document.body.style.cursor = "row-resize";
  document.body.style.userSelect = "none";
}

function onUnstagedResizeMove(event: MouseEvent) {
  if (!resizingUnstaged.value) return;
  const deltaY = event.clientY - dragStartY;
  unstagedHeight.value = Math.max(80, dragStartHeight + deltaY);
}

function onUnstagedResizeEnd() {
  resizingUnstaged.value = false;
  document.removeEventListener("mousemove", onUnstagedResizeMove);
  document.removeEventListener("mouseup", onUnstagedResizeEnd);
  document.body.style.cursor = "";
  document.body.style.userSelect = "";
}

// Section Expand States
const unstagedExpanded = ref(true);
const stagedExpanded = ref(true);
const workspaceUnstagedExpanded = ref(true);
const workspaceStagedExpanded = ref(true);
const agentsUnstagedExpanded = ref(true);
const agentsStagedExpanded = ref(true);

const unstagedStyle = computed(() => {
  if (!unstagedExpanded.value) return {};
  if (!stagedExpanded.value) return { flex: "1", minHeight: "0" };
  return {
    height: unstagedHeight.value ? `${unstagedHeight.value}px` : "50%",
    flex: "0 0 auto"
  };
});

// Loading & Status
const loading = ref(false);
const committing = ref(false);
const errorMessage = ref("");
const progressMessage = ref("");
// 切换测试数据可能发生在真实刷新未完成时，用 token 丢弃旧请求回写，避免列表被清空。
let refreshChangesToken = 0;

// Commit form
const commitMessage = ref("");
const signOff = ref(false);
const noVerify = ref(false);
const amend = ref(false);

// Local arrays for workspace diff
const workspaceDiffFiles = ref<RunDiffFile[]>([]);
const stagedWorkspacePaths = ref<Set<string>>(new Set());
const discardingWorkspacePaths = ref<Set<string>>(new Set());

// Workspace diff computed lists
const workspaceUnstaged = computed(() =>
  workspaceDiffFiles.value.filter((f) => !stagedWorkspacePaths.value.has(f.path))
);
const workspaceStaged = computed(() =>
  workspaceDiffFiles.value.filter((f) => stagedWorkspacePaths.value.has(f.path))
);

// Agent diff lists (Public + Workspace)
const publicAgentDiffs = ref<AgentConfigDiffFile[]>([]);
const workspaceAgentDiffs = ref<AgentConfigDiffFile[]>([]);

const agentsUnstaged = computed(() => {
  const list: (AgentConfigDiffFile & { scope: "PUBLIC" | "WORKSPACE" })[] = [];
  workspaceAgentDiffs.value.forEach((f) => {
    const path = normalizeWorkspaceAgentDiffPath(f.path);
    if (!f.staged && path) list.push({ ...f, path, scope: "WORKSPACE" });
  });
  return list;
});

const agentsStaged = computed(() => {
  const list: (AgentConfigDiffFile & { scope: "PUBLIC" | "WORKSPACE" })[] = [];
  workspaceAgentDiffs.value.forEach((f) => {
    const path = normalizeWorkspaceAgentDiffPath(f.path);
    if (f.staged && path) list.push({ ...f, path, scope: "WORKSPACE" });
  });
  return list;
});

// Overall counts
const totalUnstagedCount = computed(() => workspaceUnstaged.value.length + agentsUnstaged.value.length);
const totalStagedCount = computed(() => workspaceStaged.value.length + agentsStaged.value.length);

// Watch for workspace change
watch(
  () => props.workspaceId,
  () => {
    stagedWorkspacePaths.value.clear();
    void refreshChanges();
  },
  { immediate: true }
);

async function refreshChanges() {
  if (loading.value) return;
  const token = ++refreshChangesToken;
  loading.value = true;
  errorMessage.value = "";
  try {
    if (workbench.useMockTestData) {
      applyMockChanges();
      return;
    }

    // 1. 获取应用工作空间变更（使用本地 Git，不依赖 opencode /vcs/diff，避免 opencode 异常导致刷新失败）
    if (props.workspaceId) {
      try {
        const gitDiff = await api.getWorkspaceGitDiff(props.workspaceId);
        if (token !== refreshChangesToken) return;
        workspaceDiffFiles.value = gitDiff.files.map((f) => ({
          path: f.path,
          status: f.status,
          patch: f.patch,
          additions: f.additions,
          deletions: f.deletions
        })) as RunDiffFile[];
        // 同步后端 staged 状态到前端 Set
        const stagedPaths = new Set<string>();
        gitDiff.files.forEach((f) => { if (f.staged) stagedPaths.add(f.path); });
        stagedWorkspacePaths.value = stagedPaths;
      } catch {
        if (token !== refreshChangesToken) return;
        workspaceDiffFiles.value = [];
      }
    } else {
      workspaceDiffFiles.value = [];
    }

    // 2. Fetch public agent changes
    try {
      const pubDiff = await api.getPublicAgentDiff(workbench.publicWorktree?.worktreeId);
      if (token !== refreshChangesToken) return;
      publicAgentDiffs.value = pubDiff.files;
    } catch {
      if (token !== refreshChangesToken) return;
      publicAgentDiffs.value = [];
    }

    // 3. Fetch workspace agent changes
    if (props.workspaceId) {
      try {
        const wksDiff = await api.getWorkspaceAgentDiff(props.workspaceId, workbench.workspaceWorktree?.worktreeId);
        if (token !== refreshChangesToken) return;
        workspaceAgentDiffs.value = wksDiff.files;
      } catch {
        if (token !== refreshChangesToken) return;
        workspaceAgentDiffs.value = [];
      }
    } else {
      workspaceAgentDiffs.value = [];
    }
  } catch (error) {
    if (token !== refreshChangesToken) return;
    errorMessage.value = errorMessageFor(error, "刷新变更列表失败");
  } finally {
    if (token === refreshChangesToken) {
      loading.value = false;
      notifyChangesRefreshed(undefined, false);
    }
  }
}

function applyMockChanges() {
  workspaceDiffFiles.value = JSON.parse(JSON.stringify(mockVcsDiffFiles));
  publicAgentDiffs.value = JSON.parse(JSON.stringify(mockPublicAgentDiffs));
  workspaceAgentDiffs.value = JSON.parse(JSON.stringify(mockWorkspaceAgentDiffs));
}

function clearChanges() {
  workspaceDiffFiles.value = [];
  publicAgentDiffs.value = [];
  workspaceAgentDiffs.value = [];
}

function normalizeWorkspaceAgentDiffPath(path: string): string | null {
  let value = path.replaceAll("\\", "/").trim();
  if (value.startsWith('"') && value.endsWith('"')) {
    value = value.slice(1, -1);
  }
  const marker = ".opencode/";
  const markerIndex = value.indexOf(marker);
  if (markerIndex >= 0) {
    value = value.slice(markerIndex + marker.length);
  }
  return value.startsWith("agents/") || value.startsWith("skills/") ? value : null;
}

// Stage workspace file (simulate)
function stageWorkspaceFile(path: string) {
  stagedWorkspacePaths.value = new Set([...stagedWorkspacePaths.value, path]);
}

// Unstage workspace file (simulate)
function unstageWorkspaceFile(path: string) {
  const next = new Set(stagedWorkspacePaths.value);
  next.delete(path);
  stagedWorkspacePaths.value = next;
}

function notifyChangesRefreshed(paths?: string[], reloadOpenFiles?: boolean) {
  const payload: { paths?: string[]; reloadOpenFiles?: boolean } = {};
  if (paths) payload.paths = paths;
  if (reloadOpenFiles !== undefined) payload.reloadOpenFiles = reloadOpenFiles;
  emit("changes-refreshed", payload);
}

async function discardWorkspaceFile(path: string) {
  if (!props.canWrite || !props.workspaceId || discardingWorkspacePaths.value.has(path)) return;
  errorMessage.value = "";
  discardingWorkspacePaths.value = new Set([...discardingWorkspacePaths.value, path]);
  try {
    if (workbench.useMockTestData) {
      workspaceDiffFiles.value = workspaceDiffFiles.value.filter((file) => file.path !== path);
      const next = new Set(stagedWorkspacePaths.value);
      next.delete(path);
      stagedWorkspacePaths.value = next;
      notifyChangesRefreshed([path]);
      return;
    }
    await api.discardWorkspaceGitFiles(props.workspaceId, [path]);
    const next = new Set(stagedWorkspacePaths.value);
    next.delete(path);
    stagedWorkspacePaths.value = next;
    await refreshChanges();
    notifyChangesRefreshed([path]);
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "回退工作区文件失败");
  } finally {
    const next = new Set(discardingWorkspacePaths.value);
    next.delete(path);
    discardingWorkspacePaths.value = next;
  }
}

// Stage agent file (real / mock)
async function stageAgentFile(file: AgentConfigDiffFile & { scope: "PUBLIC" | "WORKSPACE" }) {
  if (!props.canWrite) return;
  errorMessage.value = "";
  try {
    if (workbench.useMockTestData) {
      if (file.scope === "PUBLIC") {
        const found = publicAgentDiffs.value.find((f) => f.path === file.path);
        if (found) found.staged = true;
      } else {
        const found = workspaceAgentDiffs.value.find((f) => f.path === file.path);
        if (found) found.staged = true;
      }
      return;
    }

    if (file.scope === "PUBLIC") {
      await api.stagePublicAgentFiles([file.path], workbench.publicWorktree?.worktreeId);
    } else {
      await api.stageWorkspaceAgentFiles(props.workspaceId!, [file.path], workbench.workspaceWorktree?.worktreeId);
    }
    await refreshChanges();
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "暂存 Agent 文件失败");
  }
}

// Unstage agent file (real / mock)
async function unstageAgentFile(file: AgentConfigDiffFile & { scope: "PUBLIC" | "WORKSPACE" }) {
  if (!props.canWrite) return;
  errorMessage.value = "";
  try {
    if (workbench.useMockTestData) {
      if (file.scope === "PUBLIC") {
        const found = publicAgentDiffs.value.find((f) => f.path === file.path);
        if (found) found.staged = false;
      } else {
        const found = workspaceAgentDiffs.value.find((f) => f.path === file.path);
        if (found) found.staged = false;
      }
      return;
    }

    if (file.scope === "PUBLIC") {
      await api.unstagePublicAgentFiles([file.path], workbench.publicWorktree?.worktreeId);
    } else {
      await api.unstageWorkspaceAgentFiles(props.workspaceId!, [file.path], workbench.workspaceWorktree?.worktreeId);
    }
    await refreshChanges();
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "取消暂存 Agent 文件失败");
  }
}

// Open Diff in right Monaco Editor workspace
function handleOpenFileDiff(path: string, source: "vcs" | "agent", scope?: "PUBLIC" | "WORKSPACE") {
  emit("openDiff", { path, source, scope });
}

// Commit changes
async function handleCommit(push = false) {
  if (!props.canWrite || committing.value) return;
  const msg = commitMessage.value.trim();
  if (!msg) {
    errorMessage.value = "请输入提交说明";
    return;
  }

  committing.value = true;
  errorMessage.value = "";
  progressMessage.value = "";

  try {
    if (workbench.useMockTestData) {
      progressMessage.value = "正在提交变更 (测试模式)...";
      await new Promise((resolve) => setTimeout(resolve, 800));
      if (push) {
        progressMessage.value = "正在推送变更到远程分支 (测试模式)...";
        await new Promise((resolve) => setTimeout(resolve, 800));
      }
      // Clear all staged paths
      stagedWorkspacePaths.value.clear();
      publicAgentDiffs.value.forEach((f) => {
        f.staged = false;
      });
      workspaceAgentDiffs.value.forEach((f) => {
        f.staged = false;
      });
      
      commitMessage.value = "";
      progressMessage.value = push ? "提交并推送成功！(测试数据)" : "提交成功！(测试数据)";
      setTimeout(() => {
        progressMessage.value = "";
      }, 2000);
      committing.value = false;
      return;
    }

    // 1. 应用工作空间提交并推送（通过个人工作区合并回应用版本分支）
    if (push && workspaceStaged.value.length > 0) {
      if (!props.personalWorkspaceId) {
        errorMessage.value = "当前未进入个人 worktree，无法合并推送到应用版本分支。请重新进入应用版本工作区后再试。";
        progressMessage.value = "";
        committing.value = false;
        return;
      }
      progressMessage.value = "正在合并推送到应用版本分支...";
      const result = await api.publishPersonalWorkspace(props.personalWorkspaceId, { commitMessage: msg });
      if (result.status === "CONFLICT") {
        const conflictList = result.conflictFiles.length > 0
          ? result.conflictFiles.join("、")
          : "未知文件";
        errorMessage.value = `合并冲突：请在个人工作区中解决 ${conflictList} 的冲突后重新「提交并推送」。当前仍停留在个人工作区，应用版本副本不受影响。`;
        progressMessage.value = "";
        await refreshChanges();
        committing.value = false;
        return;
      }
      // 推送成功：清除暂存状态
      stagedWorkspacePaths.value.clear();
      progressMessage.value = "已提交并推送到应用版本！";
      await new Promise((resolve) => setTimeout(resolve, 500));
    } else if (!push && workspaceStaged.value.length > 0) {
      // 仅提交（不推送）：用户应使用「提交并推送」完成整个发布流程
      progressMessage.value = "请点击右侧「提交并推送」按钮完成合并与发布。";
      await new Promise((resolve) => setTimeout(resolve, 2000));
    }

    // 2. Commit Agent PUBLIC changes
    const publicStagedCount = publicAgentDiffs.value.filter((f) => f.staged).length;
    if (publicStagedCount > 0) {
      progressMessage.value = "正在提交公共 Agent 配置...";
      const opId = newOperationId();
      await runAgentOperation(
        () => api.commitPublicAgentConfig({ message: msg, worktreeId: workbench.publicWorktree?.worktreeId, operationId: opId }),
        "提交公共 Agent 配置",
        opId
      );
      if (push) {
        progressMessage.value = "正在发布公共 Agent 配置...";
        const pushOpId = newOperationId();
        await runAgentOperation(
          () => api.publishPublicAgentConfig(workbench.publicWorktree?.worktreeId, pushOpId),
          "发布公共 Agent 配置",
          pushOpId
        );
      }
    }

    // 3. Commit Agent WORKSPACE changes
    const workspaceStagedCount = workspaceAgentDiffs.value.filter((f) => f.staged).length;
    if (workspaceStagedCount > 0 && props.workspaceId) {
      progressMessage.value = "正在提交工作空间 Agent 配置...";
      const opId = newOperationId();
      await runAgentOperation(
        () => api.commitWorkspaceAgentConfig(props.workspaceId!, { message: msg, worktreeId: workbench.workspaceWorktree?.worktreeId, operationId: opId }),
        "提交工作空间 Agent 配置",
        opId
      );
      if (push) {
        progressMessage.value = "正在发布工作空间 Agent 配置...";
        const pushOpId = newOperationId();
        await runAgentOperation(
          () => api.publishWorkspaceAgentConfig(props.workspaceId!, workbench.workspaceWorktree?.worktreeId, pushOpId),
          "发布工作空间 Agent 配置",
          pushOpId
        );
      }
    }

    commitMessage.value = "";
    progressMessage.value = push ? "提交并推送成功！" : "提交成功！";
    await refreshChanges();
    setTimeout(() => {
      progressMessage.value = "";
    }, 2000);
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "提交失败");
  } finally {
    committing.value = false;
  }
}

async function runAgentOperation<T>(action: () => Promise<T>, label: string, operationId: string) {
  let socket: { close: () => void } | null = null;
  try {
    socket = await api.connectAgentConfigProgress(operationId, (event) => {
      if (event.currentStep) {
        progressMessage.value = `${label}: ${event.currentStep} (${event.status || ''})`;
      }
    });
  } catch {
    socket = null;
  }
  try {
    await action();
  } finally {
    setTimeout(() => socket?.close(), 1000);
  }
}

function newOperationId() {
  const random = typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID().replaceAll("-", "")
    : `${Date.now().toString(36)}${Math.random().toString(36).slice(2)}`;
  return `aco_${random}`;
}

function errorMessageFor(error: unknown, fallback: string): string {
  if (error instanceof BackendApiError) return `${fallback}：${error.message}`;
  if (error instanceof Error) return `${fallback}：${error.message}`;
  return fallback;
}

function getBadgeTone(status: string) {
  const s = status.toLowerCase();
  if (s === "deleted" || s === "d") return "danger";
  if (s === "added" || s === "a" || s === "untracked" || s === "?") return "success";
  return "warning"; // modified, etc.
}

defineExpose({
  refreshChanges
});
</script>

<template>
  <div class="git-changes-panel">
    <!-- Header status / errors -->
    <div v-if="errorMessage" class="git-error">
      <AlertTriangle class="h-3.5 w-3.5 shrink-0 mt-[2px]" :stroke-width="1.5" />
      <span>{{ errorMessage }}</span>
    </div>
    <div v-if="progressMessage" class="git-progress">
      <Loader2 class="h-3.5 w-3.5 shrink-0 animate-spin" :stroke-width="1.5" />
      <span>{{ progressMessage }}</span>
    </div>

    <!-- Scrollable file list area -->
    <div class="git-lists-container">
      <!-- 1. UNSTAGED SECTION -->
      <div class="git-section git-unstaged-section" :style="unstagedStyle" :class="{ 'is-collapsed': !unstagedExpanded }">
        <div class="git-section-header" @click="unstagedExpanded = !unstagedExpanded">
          <ChevronDown v-if="unstagedExpanded" class="h-3.5 w-3.5" :stroke-width="1.5" />
          <ChevronRight v-else class="h-3.5 w-3.5" :stroke-width="1.5" />
          <span class="git-section-title">UNSTAGED (未暂存) ({{ totalUnstagedCount }})</span>
          <button
            type="button"
            class="git-refresh-btn ml-auto"
            title="刷新变更列表"
            @click.stop="refreshChanges"
            :disabled="loading"
          >
            <RefreshCw class="h-3 w-3" :class="{ 'animate-spin': loading }" :stroke-width="1.5" />
          </button>
        </div>

        <div v-show="unstagedExpanded" class="git-section-content pl-2">
          <!-- 1a. Application Workspace -->
          <div class="git-sub-section">
            <div class="git-sub-header" @click.stop="workspaceUnstagedExpanded = !workspaceUnstagedExpanded">
              <ChevronDown v-if="workspaceUnstagedExpanded" class="h-3 w-3" :stroke-width="1.5" />
              <ChevronRight v-else class="h-3 w-3" :stroke-width="1.5" />
              <span>应用工作空间</span>
              <span class="git-sub-badge ml-1">({{ workspaceUnstaged.length }})</span>
            </div>
            <div v-show="workspaceUnstagedExpanded" class="git-sub-content pl-2 py-0.5 space-y-0.5">
              <div v-if="workspaceUnstaged.length === 0" class="git-empty-text">暂无变更</div>
              <div
                v-for="file in workspaceUnstaged"
                :key="file.path"
                class="git-file-row group"
                @click="handleOpenFileDiff(file.path, 'vcs')"
              >
                <Badge :tone="getBadgeTone(file.status)" class="mr-1 py-0 px-1 text-[9px] uppercase">{{ file.status || 'M' }}</Badge>
                <span class="git-file-name" :title="file.path">{{ file.path }}</span>
                <span v-if="file.additions" class="git-additions ml-1">+{{ file.additions }}</span>
                <span v-if="file.deletions" class="git-deletions ml-1">-{{ file.deletions }}</span>
                
                <button
                  type="button"
                  class="git-row-action hidden group-hover:inline-flex"
                  title="回退文件改动"
                  :disabled="discardingWorkspacePaths.has(file.path)"
                  @click.stop="discardWorkspaceFile(file.path)"
                >
                  <Loader2 v-if="discardingWorkspacePaths.has(file.path)" class="h-3.5 w-3.5 animate-spin" :stroke-width="1.5" />
                  <Undo2 v-else class="h-3.5 w-3.5" :stroke-width="1.5" />
                </button>
                <button
                  type="button"
                  class="git-row-action hidden group-hover:inline-flex"
                  title="暂存文件"
                  @click.stop="stageWorkspaceFile(file.path)"
                >
                  <Plus class="h-3.5 w-3.5" :stroke-width="1.5" />
                </button>
              </div>
            </div>
          </div>

          <!-- 1b. Agents -->
          <div class="git-sub-section">
            <div class="git-sub-header" @click.stop="agentsUnstagedExpanded = !agentsUnstagedExpanded">
              <ChevronDown v-if="agentsUnstagedExpanded" class="h-3 w-3" :stroke-width="1.5" />
              <ChevronRight v-else class="h-3 w-3" :stroke-width="1.5" />
              <span>agents</span>
              <span class="git-sub-badge ml-1">({{ agentsUnstaged.length }})</span>
            </div>
            <div v-show="agentsUnstagedExpanded" class="git-sub-content pl-2 py-0.5 space-y-0.5">
              <div v-if="agentsUnstaged.length === 0" class="git-empty-text">暂无变更</div>
              <div
                v-for="file in agentsUnstaged"
                :key="file.path"
                class="git-file-row group"
                @click="handleOpenFileDiff(file.path, 'agent', file.scope)"
              >
                <Badge :tone="getBadgeTone(file.status)" class="mr-1 py-0 px-1 text-[9px] uppercase">{{ file.status || 'M' }}</Badge>
                <span class="git-file-name" :title="file.path">
                  <span class="git-scope-label">[{{ file.scope === 'PUBLIC' ? '公共' : '应用级' }}]</span>
                  {{ file.path }}
                </span>
                
                <button
                  type="button"
                  class="git-row-action hidden group-hover:inline-flex"
                  title="暂存文件"
                  @click.stop="stageAgentFile(file)"
                >
                  <Plus class="h-3.5 w-3.5" :stroke-width="1.5" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Resizer divider between UNSTAGED and STAGED -->
      <div
        v-if="unstagedExpanded && stagedExpanded"
        class="git-pane-resize-handle"
        @mousedown="onUnstagedResizeStart"
        role="separator"
        aria-orientation="horizontal"
      />

      <!-- 2. STAGED SECTION -->
      <div class="git-section staged-section border-t border-[#e4e4e7]" :class="{ 'is-collapsed': !stagedExpanded }">
        <div class="git-section-header" @click="stagedExpanded = !stagedExpanded">
          <ChevronDown v-if="stagedExpanded" class="h-3.5 w-3.5" :stroke-width="1.5" />
          <ChevronRight v-else class="h-3.5 w-3.5" :stroke-width="1.5" />
          <span class="git-section-title">STAGED (已暂存) ({{ totalStagedCount }})</span>
        </div>

        <div v-show="stagedExpanded" class="git-section-content pl-2">
          <!-- 2a. Application Workspace -->
          <div class="git-sub-section">
            <div class="git-sub-header" @click.stop="workspaceStagedExpanded = !workspaceStagedExpanded">
              <ChevronDown v-if="workspaceStagedExpanded" class="h-3 w-3" :stroke-width="1.5" />
              <ChevronRight v-else class="h-3 w-3" :stroke-width="1.5" />
              <span>应用工作空间</span>
              <span class="git-sub-badge ml-1">({{ workspaceStaged.length }})</span>
            </div>
            <div v-show="workspaceStagedExpanded" class="git-sub-content pl-2 py-0.5 space-y-0.5">
              <div v-if="workspaceStaged.length === 0" class="git-empty-text">无暂存文件</div>
              <div
                v-for="file in workspaceStaged"
                :key="file.path"
                class="git-file-row group"
                @click="handleOpenFileDiff(file.path, 'vcs')"
              >
                <Badge :tone="getBadgeTone(file.status)" class="mr-1 py-0 px-1 text-[9px] uppercase">{{ file.status || 'M' }}</Badge>
                <span class="git-file-name" :title="file.path">{{ file.path }}</span>
                <span v-if="file.additions" class="git-additions ml-1">+{{ file.additions }}</span>
                <span v-if="file.deletions" class="git-deletions ml-1">-{{ file.deletions }}</span>
                
                <button
                  type="button"
                  class="git-row-action hidden group-hover:inline-flex"
                  title="回退文件改动"
                  :disabled="discardingWorkspacePaths.has(file.path)"
                  @click.stop="discardWorkspaceFile(file.path)"
                >
                  <Loader2 v-if="discardingWorkspacePaths.has(file.path)" class="h-3.5 w-3.5 animate-spin" :stroke-width="1.5" />
                  <Undo2 v-else class="h-3.5 w-3.5" :stroke-width="1.5" />
                </button>
                <button
                  type="button"
                  class="git-row-action hidden group-hover:inline-flex"
                  title="取消暂存"
                  @click.stop="unstageWorkspaceFile(file.path)"
                >
                  <Minus class="h-3.5 w-3.5" :stroke-width="1.5" />
                </button>
              </div>
            </div>
          </div>

          <!-- 2b. Agents -->
          <div class="git-sub-section">
            <div class="git-sub-header" @click.stop="agentsStagedExpanded = !agentsStagedExpanded">
              <ChevronDown v-if="agentsStagedExpanded" class="h-3 w-3" :stroke-width="1.5" />
              <ChevronRight v-else class="h-3 w-3" :stroke-width="1.5" />
              <span>agents</span>
              <span class="git-sub-badge ml-1">({{ agentsStaged.length }})</span>
            </div>
            <div v-show="agentsStagedExpanded" class="git-sub-content pl-2 py-0.5 space-y-0.5">
              <div v-if="agentsStaged.length === 0" class="git-empty-text">无暂存文件</div>
              <div
                v-for="file in agentsStaged"
                :key="file.path"
                class="git-file-row group"
                @click="handleOpenFileDiff(file.path, 'agent', file.scope)"
              >
                <Badge :tone="getBadgeTone(file.status)" class="mr-1 py-0 px-1 text-[9px] uppercase">{{ file.status || 'M' }}</Badge>
                <span class="git-file-name" :title="file.path">
                  <span class="git-scope-label">[{{ file.scope === 'PUBLIC' ? '公共' : '应用级' }}]</span>
                  {{ file.path }}
                </span>
                
                <button
                  type="button"
                  class="git-row-action hidden group-hover:inline-flex"
                  title="取消暂存"
                  @click.stop="unstageAgentFile(file)"
                >
                  <Minus class="h-3.5 w-3.5" :stroke-width="1.5" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Commit Footer form -->
    <div class="git-commit-form">
      <textarea
        v-model="commitMessage"
        class="git-commit-textarea"
        placeholder="输入提交说明。首行为主题，空行后为详细描述..."
        :disabled="committing"
        rows="2"
      ></textarea>


      <!-- Action buttons -->
      <div class="git-actions-row">
        <button
          type="button"
          class="git-action-btn btn-commit flex-1"
          :disabled="committing || totalStagedCount === 0 || !commitMessage.trim()"
          @click="handleCommit(false)"
        >
          <FolderGit2 class="h-3.5 w-3.5 shrink-0" :stroke-width="1.5" />
          <span>提交</span>
        </button>
        <button
          type="button"
          class="git-action-btn btn-push flex-1"
          :disabled="committing || totalStagedCount === 0 || !commitMessage.trim()"
          @click="handleCommit(true)"
        >
          <Upload class="h-3.5 w-3.5 shrink-0" :stroke-width="1.5" />
          <span>提交并推送</span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.git-changes-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: #fafafa;
}

.git-error {
  display: flex;
  gap: 4px;
  background: #fff7ed;
  border-bottom: 1px solid #fed7aa;
  padding: 6px 8px;
  font-size: 11px;
  color: #9a3412;
}

.git-progress {
  display: flex;
  gap: 6px;
  align-items: center;
  background: #f0fdf4;
  border-bottom: 1px solid #bbf7d0;
  padding: 6px 8px;
  font-size: 11px;
  color: #15803d;
}

.git-lists-container {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.git-section {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.git-section.staged-section {
  flex: 1;
  min-height: 0;
}

.git-pane-resize-handle {
  height: 4px;
  background: transparent;
  cursor: row-resize;
  position: relative;
  z-index: 5;
  flex-shrink: 0;
  margin: -2px 0;
}

.git-pane-resize-handle::after {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  height: 1px;
  margin-top: -0.5px;
  background: #e4e4e7;
  transition: background-color 0.14s ease;
}

.git-pane-resize-handle:hover::after {
  background: #bbb;
}

.git-section-header {
  display: flex;
  align-items: center;
  height: 28px;
  padding: 0 8px;
  background: #f4f4f5;
  cursor: pointer;
  user-select: none;
  font-size: 11px;
  font-weight: 600;
  color: #71717a;
  transition: background-color 0.1s;
}

.git-section-header:hover {
  background: #e4e4e7;
  color: #18181b;
}

.git-refresh-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: 0;
  background: transparent;
  color: #71717a;
  border-radius: 4px;
  cursor: pointer;
}

.git-refresh-btn:hover {
  background: #d4d4d8;
  color: #18181b;
}

.git-section-content {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.git-sub-section {
  display: flex;
  flex-direction: column;
  margin-top: 4px;
  margin-bottom: 8px;
}

.git-sub-header {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 600;
  color: #52525b;
  cursor: pointer;
  user-select: none;
  padding: 2px 4px;
  border-radius: 4px;
}

.git-sub-header:hover {
  background: #f4f4f5;
  color: #18181b;
}

.git-sub-badge {
  font-weight: 400;
  color: #a1a1aa;
}

.git-empty-text {
  font-size: 11px;
  color: #a1a1aa;
  font-style: italic;
  padding: 4px 6px;
}

.git-file-row {
  position: relative;
  display: flex;
  align-items: center;
  height: 26px;
  padding: 0 6px;
  border-radius: 4px;
  cursor: pointer;
  user-select: none;
  font-size: 12px;
  color: #27272a;
}

.git-file-row:hover {
  background: #f4f4f5;
}

.git-file-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: monospace;
}

.git-scope-label {
  color: #a1a1aa;
  font-weight: normal;
  margin-right: 2px;
}

.git-additions {
  font-size: 10px;
  font-weight: 600;
  color: #15803d;
}

.git-deletions {
  font-size: 10px;
  font-weight: 600;
  color: #b91c1c;
}

.git-row-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: 0;
  background: transparent;
  color: #71717a;
  border-radius: 4px;
  cursor: pointer;
  margin-left: 4px;
}

.git-row-action:hover {
  background: #e4e4e7;
  color: #18181b;
}

/* Commit footer form styles */
.git-commit-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
  border-top: 1px solid #e4e4e7;
  padding: 8px;
  background: #fafafa;
}

.git-commit-textarea {
  width: 100%;
  border: 1px solid #d4d4d8;
  border-radius: 4px;
  padding: 6px;
  font-size: 12px;
  color: #18181b;
  background: #ffffff;
  resize: none;
  transition: border-color 0.12s ease;
}

.git-commit-textarea:focus {
  outline: none;
  border-color: #a1a1aa;
}

.git-commit-textarea:disabled {
  background: #f4f4f5;
  color: #a1a1aa;
  cursor: not-allowed;
}

.git-options-row {
  display: flex;
  gap: 12px;
}

.git-option-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #52525b;
  cursor: pointer;
  user-select: none;
}

.git-option-label input {
  cursor: pointer;
}

.git-actions-row {
  display: flex;
  gap: 8px;
}

.git-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 28px;
  border: 0;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.12s ease, opacity 0.12s ease;
}

.git-action-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.git-action-btn.btn-commit {
  background: #e4e4e7;
  color: #18181b;
}

.git-action-btn.btn-commit:hover:not(:disabled) {
  background: #d4d4d8;
}

.git-action-btn.btn-push {
  background: #2563eb;
  color: #ffffff;
}

.git-action-btn.btn-push:hover:not(:disabled) {
  background: #1d4ed8;
}
</style>
