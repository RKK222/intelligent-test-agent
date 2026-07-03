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
  Undo2,
  X,
  GitMerge,
  User,
  CheckCircle2,
  Circle,
  XCircle
} from "lucide-vue-next";
import { createBackendApiClient, BackendApiError } from "@test-agent/backend-api";
import { MergeConflictEditor } from "@test-agent/diff-viewer";
import {
  useWorkbenchStore,
  mockVcsDiffFiles,
  mockPublicAgentDiffs,
  mockWorkspaceAgentDiffs
} from "@test-agent/workbench-shell";
import type {
  AgentConfigDiffFile,
  RunDiffFile,
  WorkspaceGitDiffFile,
  WorkspaceGitConflict,
  WorkspaceGitConflictResolution
} from "@test-agent/shared-types";
import { Badge, Button } from "@test-agent/ui-kit";

type WorkspacePanelDiffFile = RunDiffFile & { rawStatus?: string };

const props = defineProps<{
  workspaceId?: string;
  /** 当前默认个人工作区 ID，用于提交并推送（合并回应用版本分支） */
  personalWorkspaceId?: string;
  apiBaseUrl?: string;
  canWrite: boolean;
}>();

const emit = defineEmits<{
  openDiff: [payload: {
    path: string;
    source: "vcs" | "agent";
    scope?: "PUBLIC" | "WORKSPACE";
    file?: WorkspacePanelDiffFile;
  }];
  "changes-refreshed": [payload?: {
    paths?: string[];
    reloadOpenFiles?: boolean;
    files?: WorkspaceGitDiffFile[];
  }];
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
const activeConflict = ref<WorkspaceGitConflict | null>(null);
const conflictLoading = ref(false);
const conflictResolving = ref(false);
const showCommitProgressDialog = ref(false);
const commitStep = ref(0);
const executedCommands = ref<string[]>([]);

function getCommitStepClass(stepNum: number) {
  if (errorMessage.value && commitStep.value === stepNum) {
    return "is-failed";
  }
  if (commitStep.value > stepNum) {
    return "is-succeeded";
  }
  if (commitStep.value === stepNum) {
    return "is-running";
  }
  return "is-pending";
}

function getCommitStepIcon(stepNum: number) {
  if (errorMessage.value && commitStep.value === stepNum) {
    return XCircle;
  }
  if (commitStep.value > stepNum) {
    return CheckCircle2;
  }
  if (commitStep.value === stepNum) {
    return Loader2;
  }
  return Circle;
}

function getCommitStepStatusText(stepNum: number) {
  if (errorMessage.value && commitStep.value === stepNum) {
    return "FAILED";
  }
  if (commitStep.value > stepNum) {
    return "SUCCEEDED";
  }
  if (commitStep.value === stepNum) {
    return "RUNNING";
  }
  return "PENDING";
}
// 切换测试数据可能发生在真实刷新未完成时，用 token 丢弃旧请求回写，避免列表被清空。
let refreshChangesToken = 0;

// Commit form
const commitMessage = ref("");
const signOff = ref(false);
const noVerify = ref(false);
const amend = ref(false);

// Local arrays for workspace diff
const workspaceDiffFiles = ref<WorkspacePanelDiffFile[]>([]);
const stagedWorkspacePaths = ref<Set<string>>(new Set());
const discardingWorkspacePaths = ref<Set<string>>(new Set());
const updatingWorkspaceIndexPaths = ref<Set<string>>(new Set());

// Workspace diff computed lists
const workspaceUnstaged = computed(() =>
  workspaceDiffFiles.value.filter((f) => !stagedWorkspacePaths.value.has(f.path) && !isConflictFile(f))
);
const workspaceStaged = computed(() =>
  workspaceDiffFiles.value.filter((f) => stagedWorkspacePaths.value.has(f.path) && !isConflictFile(f))
);
const workspaceConflicts = computed(() =>
  workspaceDiffFiles.value.filter((f) => isConflictFile(f))
);
const hasWorkspaceConflicts = computed(() => workspaceConflicts.value.length > 0);

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
const totalUnstagedCount = computed(() => workspaceUnstaged.value.length + workspaceConflicts.value.length + agentsUnstaged.value.length);
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

async function refreshChanges(options: { preserveError?: boolean } = {}) {
  const token = ++refreshChangesToken;
  loading.value = true;
  if (!options.preserveError) {
    errorMessage.value = "";
  }
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
          rawStatus: f.rawStatus,
          status: f.status,
          patch: f.patch,
          additions: f.additions,
          deletions: f.deletions
        }));
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

// 工作区暂存必须落到真实 Git index，刷新后列表状态才不会回退。
async function stageWorkspaceFile(path: string) {
  if (!props.canWrite || !props.workspaceId || updatingWorkspaceIndexPaths.value.has(path)) return;
  errorMessage.value = "";
  updatingWorkspaceIndexPaths.value = new Set([...updatingWorkspaceIndexPaths.value, path]);
  try {
    if (workbench.useMockTestData) {
      stagedWorkspacePaths.value = new Set([...stagedWorkspacePaths.value, path]);
      return;
    }
    await api.stageWorkspaceGitFiles(props.workspaceId, [path]);
    await refreshChanges();
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "暂存工作区文件失败");
  } finally {
    const next = new Set(updatingWorkspaceIndexPaths.value);
    next.delete(path);
    updatingWorkspaceIndexPaths.value = next;
  }
}

async function unstageWorkspaceFile(path: string) {
  if (!props.canWrite || !props.workspaceId || updatingWorkspaceIndexPaths.value.has(path)) return;
  errorMessage.value = "";
  updatingWorkspaceIndexPaths.value = new Set([...updatingWorkspaceIndexPaths.value, path]);
  try {
    if (workbench.useMockTestData) {
      const next = new Set(stagedWorkspacePaths.value);
      next.delete(path);
      stagedWorkspacePaths.value = next;
      return;
    }
    await api.unstageWorkspaceGitFiles(props.workspaceId, [path]);
    await refreshChanges();
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "取消暂存工作区文件失败");
  } finally {
    const next = new Set(updatingWorkspaceIndexPaths.value);
    next.delete(path);
    updatingWorkspaceIndexPaths.value = next;
  }
}

function notifyChangesRefreshed(paths?: string[], reloadOpenFiles?: boolean) {
  const payload: {
    paths?: string[];
    reloadOpenFiles?: boolean;
    files: WorkspaceGitDiffFile[];
  } = {
    files: workspaceDiffFiles.value.map((file) => ({
      path: file.path,
      rawStatus: file.rawStatus,
      status: file.status,
      staged: stagedWorkspacePaths.value.has(file.path),
      patch: file.patch,
      additions: file.additions,
      deletions: file.deletions
    }))
  };
  if (paths) payload.paths = paths;
  if (reloadOpenFiles !== undefined) payload.reloadOpenFiles = reloadOpenFiles;
  emit("changes-refreshed", payload);
}

function isConflictFile(file: { status?: string; rawStatus?: string }): boolean {
  const rawStatus = (file.rawStatus ?? "").trim();
  return file.status === "conflict" || ["DD", "AU", "UD", "UA", "DU", "AA", "UU"].includes(rawStatus);
}

async function openWorkspaceConflict(path: string) {
  if (!props.workspaceId || conflictLoading.value) return;
  conflictLoading.value = true;
  errorMessage.value = "";
  try {
    activeConflict.value = await api.getWorkspaceGitConflict(props.workspaceId, path);
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "读取 Git 冲突失败");
  } finally {
    conflictLoading.value = false;
  }
}

async function resolveWorkspaceConflict(payload: {
  resolution: WorkspaceGitConflictResolution;
  content?: string | null;
}) {
  if (!props.workspaceId || !activeConflict.value || conflictResolving.value) return;
  conflictResolving.value = true;
  errorMessage.value = "";
  try {
    const path = activeConflict.value.path;
    await api.resolveWorkspaceGitConflict(props.workspaceId, { path, ...payload });
    activeConflict.value = null;
    await refreshChanges();
    notifyChangesRefreshed([path], true);
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "解决 Git 冲突失败");
  } finally {
    conflictResolving.value = false;
  }
}

async function abortWorkspaceConflict() {
  if (!props.workspaceId || conflictResolving.value) return;
  conflictResolving.value = true;
  errorMessage.value = "";
  try {
    await api.abortWorkspaceGitConflict(props.workspaceId);
    activeConflict.value = null;
    await refreshChanges();
    notifyChangesRefreshed(undefined, true);
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "取消 Git 合并失败");
  } finally {
    conflictResolving.value = false;
  }
}

async function resolveAllWorkspaceConflicts(resolution: "CURRENT" | "INCOMING") {
  if (!props.workspaceId || conflictResolving.value) return;
  const label = resolution === "CURRENT" ? "个人版本" : "远程应用版本";
  if (!window.confirm(`将 ${workspaceConflicts.value.length} 个冲突文件全部采用${label}，是否继续？`)) return;
  conflictResolving.value = true;
  errorMessage.value = "";
  try {
    await api.resolveAllWorkspaceGitConflicts(props.workspaceId, { resolution });
    activeConflict.value = null;
    await refreshChanges();
    notifyChangesRefreshed(undefined, true);
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "批量解决 Git 冲突失败");
  } finally {
    conflictResolving.value = false;
  }
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
function handleOpenFileDiff(
  path: string,
  source: "vcs" | "agent",
  scope?: "PUBLIC" | "WORKSPACE",
  file?: WorkspacePanelDiffFile
) {
  emit("openDiff", {
    path,
    source,
    ...(scope ? { scope } : {}),
    ...(file ? { file } : {})
  });
}

// Commit changes
async function handleCommit(push = false) {
  if (!props.canWrite || committing.value) return;
  if (hasWorkspaceConflicts.value) {
    errorMessage.value = "当前个人工作区存在合并冲突，请先解决冲突文件后再重新提交并推送。";
    progressMessage.value = "";
    return;
  }
  const msg = commitMessage.value.trim();
  if (!msg) {
    errorMessage.value = "请输入提交说明";
    return;
  }

  committing.value = true;
  errorMessage.value = "";
  progressMessage.value = "";
  executedCommands.value = [];
  showCommitProgressDialog.value = true;
  commitStep.value = 1; // 准备合并并拉取远程分支

  try {
    if (workbench.useMockTestData) {
      progressMessage.value = "正在提交变更 (测试模式)...";
      executedCommands.value.push("git status");
      await new Promise((resolve) => setTimeout(resolve, 800));
      commitStep.value = 2; // 暂存并提交本地变更
      executedCommands.value.push("git add .");
      executedCommands.value.push("git commit -m \"" + msg + "\"");
      await new Promise((resolve) => setTimeout(resolve, 800));
      if (push) {
        commitStep.value = 3; // 合并远程分支代码
        executedCommands.value.push("git fetch origin main");
        executedCommands.value.push("git merge origin/main");
        await new Promise((resolve) => setTimeout(resolve, 800));
        commitStep.value = 4; // 推送合并结果到远程仓库
        executedCommands.value.push("git push origin main");
        await new Promise((resolve) => setTimeout(resolve, 800));
      }
      commitStep.value = 5; // Success
      
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
      const preview = await api.previewPersonalWorkspacePublish(props.personalWorkspaceId);
      executedCommands.value = ["git fetch origin"];
      if (
        preview.incomingCommitCount > 0
        && !window.confirm(
          `远程应用分支有 ${preview.incomingCommitCount} 个新提交，涉及 ${preview.changedFileCount} 个文件`
          + `（新增 ${preview.addedCount}、修改 ${preview.modifiedCount}、删除 ${preview.deletedCount}、重命名 ${preview.renamedCount}）。`
          + "继续后 Git 会先合并这些变化；如有冲突，可一键全部保留个人版本或远程版本。是否继续？"
        )
      ) {
        progressMessage.value = "";
        showCommitProgressDialog.value = false;
        committing.value = false;
        return;
      }
      commitStep.value = 2; // 暂存并提交本地变更
      const result = await api.publishPersonalWorkspace(props.personalWorkspaceId, {
        commitMessage: msg,
        files: workspaceStaged.value.map((file) => file.path),
        expectedApplicationHead: preview.applicationHead
      });
      executedCommands.value = result.executedCommands || [];
      if (result.status === "CONFLICT") {
        const conflictMessage = `合并产生 ${result.conflictFiles.length} 个冲突文件。可全部保留个人版本、全部采用远程版本，或逐个处理。`;
        errorMessage.value = conflictMessage;
        progressMessage.value = "";
        commitStep.value = 3; // Step 3 failed
        await refreshChanges({ preserveError: true });
        errorMessage.value = conflictMessage;
        committing.value = false;
        return;
      }
      if (result.status !== "MERGED" || result.remotePushed !== true) {
        throw new Error("远端推送结果未确认，请刷新变更列表并检查远程分支后重试。");
      }
      commitStep.value = 5; // Success
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
    progressMessage.value = "";
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
    const result = await action();
    if (
      result
      && typeof result === "object"
      && "status" in result
      && (result as { status?: string }).status !== "SUCCEEDED"
    ) {
      const operation = result as { status?: string; errorMessage?: string | null };
      throw new Error(operation.errorMessage || `${label}未成功完成`);
    }
    return result;
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
  if (s === "deleted" || s === "d" || s === "conflict") return "danger";
  if (s === "added" || s === "a" || s === "untracked" || s === "?") return "success";
  return "warning"; // modified, etc.
}

defineExpose({
  refreshChanges
});
</script>

<template>
  <div class="git-changes-panel">
    <div v-if="activeConflict" class="git-merge-overlay">
      <MergeConflictEditor
        :conflict="activeConflict"
        :resolving="conflictResolving"
        @resolve="resolveWorkspaceConflict"
        @abort="abortWorkspaceConflict"
        @close="activeConflict = null"
      />
    </div>
    <!-- Header status / errors -->
    <div v-if="errorMessage && !showCommitProgressDialog" class="git-error">
      <AlertTriangle class="h-3.5 w-3.5 shrink-0 mt-[2px]" :stroke-width="1.5" />
      <span>{{ errorMessage }}</span>
    </div>
    <div v-if="progressMessage && !showCommitProgressDialog" class="git-progress">
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
            @click.stop="refreshChanges()"
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
              <span class="git-sub-badge ml-1">({{ workspaceUnstaged.length + workspaceConflicts.length }})</span>
            </div>
            <div v-show="workspaceUnstagedExpanded" class="git-sub-content pl-2 py-0.5 space-y-0.5">
              <div v-if="workspaceConflicts.length > 0" class="git-conflict-banner bg-amber-50/80 dark:bg-amber-950/15 border border-amber-200/60 dark:border-amber-900/30 rounded p-2 mb-2 text-slate-700 dark:text-slate-300 shadow-sm">
                <div class="flex items-center gap-1 mb-1.5">
                  <AlertTriangle class="h-3.5 w-3.5 shrink-0 text-amber-600 dark:text-amber-500" />
                  <span class="font-semibold text-xs text-amber-900 dark:text-amber-400">检测到 {{ workspaceConflicts.length }} 个冲突</span>
                </div>
                <div class="flex items-center gap-1.5">
                  <Button
                    size="sm"
                    variant="secondary"
                    class="flex-1 h-6 border-amber-200 hover:border-amber-300 dark:border-amber-800 bg-white hover:bg-amber-50 text-amber-900 dark:text-amber-400 dark:bg-zinc-900 dark:hover:bg-amber-950/20 font-medium px-1 flex items-center justify-center shadow-sm"
                    :disabled="conflictResolving"
                    @click.stop="resolveAllWorkspaceConflicts('CURRENT')"
                  >
                    保留个人
                  </Button>
                  <Button
                    size="sm"
                    variant="secondary"
                    class="flex-1 h-6 border-amber-200 hover:border-amber-300 dark:border-amber-800 bg-white hover:bg-amber-50 text-amber-900 dark:text-amber-400 dark:bg-zinc-900 dark:hover:bg-amber-950/20 font-medium px-1 flex items-center justify-center shadow-sm"
                    :disabled="conflictResolving"
                    @click.stop="resolveAllWorkspaceConflicts('INCOMING')"
                  >
                    采用远程
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    class="h-6 px-1.5 text-zinc-500 hover:text-zinc-700 dark:text-zinc-400 dark:hover:text-zinc-200 hover:bg-zinc-100 dark:hover:bg-zinc-800/50 font-normal shrink-0"
                    :disabled="conflictResolving"
                    @click.stop="abortWorkspaceConflict"
                  >
                    取消
                  </Button>
                </div>
              </div>
              <div
                v-for="file in workspaceConflicts"
                :key="file.path"
                class="git-file-row git-conflict-row group"
                :title="file.path"
                :aria-label="file.path"
                @click="openWorkspaceConflict(file.path)"
              >
                <Badge tone="danger" class="mr-1 py-0 px-1 text-[9px] uppercase">CONFLICT</Badge>
                <span class="git-file-name" :title="file.path">{{ file.path }}</span>
                <span v-if="file.rawStatus" class="git-raw-status ml-1">{{ file.rawStatus }}</span>
                <span v-if="file.additions" class="git-additions ml-1">+{{ file.additions }}</span>
                <span v-if="file.deletions" class="git-deletions ml-1">-{{ file.deletions }}</span>
              </div>
              <div v-if="workspaceUnstaged.length === 0 && workspaceConflicts.length === 0" class="git-empty-text">暂无变更</div>
              <div
                v-for="file in workspaceUnstaged"
                :key="file.path"
                class="git-file-row group"
                :title="file.path"
                :aria-label="file.path"
                @click="handleOpenFileDiff(file.path, 'vcs', undefined, file)"
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
                  :disabled="updatingWorkspaceIndexPaths.has(file.path)"
                  @click.stop="stageWorkspaceFile(file.path)"
                >
                  <Loader2 v-if="updatingWorkspaceIndexPaths.has(file.path)" class="h-3.5 w-3.5 animate-spin" :stroke-width="1.5" />
                  <Plus v-else class="h-3.5 w-3.5" :stroke-width="1.5" />
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
                :title="file.path"
                :aria-label="file.path"
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
              <div v-if="hasWorkspaceConflicts && workspaceStaged.length > 0" class="git-conflict-note">
                可继续取消暂存普通文件；解决全部冲突后 Git 才允许提交
              </div>
              <div v-if="workspaceStaged.length === 0" class="git-empty-text">无暂存文件</div>
              <div
                v-for="file in workspaceStaged"
                :key="file.path"
                class="git-file-row group"
                :title="file.path"
                :aria-label="file.path"
                @click="handleOpenFileDiff(file.path, 'vcs', undefined, file)"
              >
                <Badge :tone="getBadgeTone(file.status)" class="mr-1 py-0 px-1 text-[9px] uppercase">{{ file.status || 'M' }}</Badge>
                <span class="git-file-name" :title="file.path">{{ file.path }}</span>
                <span v-if="file.additions" class="git-additions ml-1">+{{ file.additions }}</span>
                <span v-if="file.deletions" class="git-deletions ml-1">-{{ file.deletions }}</span>
                
                <button
                  v-if="!hasWorkspaceConflicts"
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
                  :disabled="updatingWorkspaceIndexPaths.has(file.path)"
                  @click.stop="unstageWorkspaceFile(file.path)"
                >
                  <Loader2 v-if="updatingWorkspaceIndexPaths.has(file.path)" class="h-3.5 w-3.5 animate-spin" :stroke-width="1.5" />
                  <Minus v-else class="h-3.5 w-3.5" :stroke-width="1.5" />
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
                :title="file.path"
                :aria-label="file.path"
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
          :title="hasWorkspaceConflicts ? 'Git 存在未解决冲突，解决全部冲突后才能提交' : '提交已暂存变更'"
          :disabled="committing || hasWorkspaceConflicts || totalStagedCount === 0 || !commitMessage.trim()"
          @click="handleCommit(false)"
        >
          <FolderGit2 class="h-3.5 w-3.5 shrink-0" :stroke-width="1.5" />
          <span>提交</span>
        </button>
        <button
          type="button"
          class="git-action-btn btn-push flex-1"
          :title="hasWorkspaceConflicts ? 'Git 存在未解决冲突，解决全部冲突后才能提交并推送' : '提交并推送已暂存变更'"
          :disabled="committing || hasWorkspaceConflicts || totalStagedCount === 0 || !commitMessage.trim()"
          @click="handleCommit(true)"
        >
          <Upload class="h-3.5 w-3.5 shrink-0" :stroke-width="1.5" />
          <span>提交并推送</span>
        </button>
      </div>
    </div>

    <!-- Git Commit & Push Progress Dialog Overlay -->
    <div v-if="showCommitProgressDialog" class="ta-process-startup-backdrop" role="presentation">
      <section class="ta-process-startup-dialog" role="dialog" aria-modal="true" aria-label="提交并推送">
        <header class="ta-process-startup-header">
          <div>
            <h2 class="text-sm font-bold text-zinc-900 dark:text-zinc-100">提交并推送进度</h2>
            <p v-if="committing" class="text-xs text-zinc-500">正在处理中...</p>
            <p v-else-if="errorMessage" class="text-xs text-red-600 font-medium">执行失败</p>
            <p v-else class="text-xs text-green-600 font-medium">执行成功</p>
          </div>
          <button type="button" class="ta-process-startup-close" aria-label="关闭" :disabled="committing" @click="showCommitProgressDialog = false">
            <X :size="16" />
          </button>
        </header>

        <ol class="ta-process-startup-steps px-4 py-2">
          <li :class="['ta-process-startup-step', getCommitStepClass(1)]">
            <component :is="getCommitStepIcon(1)" :size="18" class="ta-process-startup-step-icon" />
            <div class="ta-process-startup-step-copy">
              <span>准备合并并拉取远程分支</span>
              <small>{{ getCommitStepStatusText(1) }}</small>
            </div>
          </li>
          <li :class="['ta-process-startup-step', getCommitStepClass(2)]">
            <component :is="getCommitStepIcon(2)" :size="18" class="ta-process-startup-step-icon" />
            <div class="ta-process-startup-step-copy">
              <span>暂存并提交本地变更</span>
              <small>{{ getCommitStepStatusText(2) }}</small>
            </div>
          </li>
          <li :class="['ta-process-startup-step', getCommitStepClass(3)]">
            <component :is="getCommitStepIcon(3)" :size="18" class="ta-process-startup-step-icon" />
            <div class="ta-process-startup-step-copy">
              <span>合并远程分支代码</span>
              <small>{{ getCommitStepStatusText(3) }}</small>
            </div>
          </li>
          <li :class="['ta-process-startup-step', getCommitStepClass(4)]">
            <component :is="getCommitStepIcon(4)" :size="18" class="ta-process-startup-step-icon" />
            <div class="ta-process-startup-step-copy">
              <span>推送合并结果到远程仓库</span>
              <small>{{ getCommitStepStatusText(4) }}</small>
            </div>
          </li>
        </ol>

        <!-- Command Log Console -->
        <div class="px-4 py-3 border-t border-zinc-100 dark:border-zinc-800 bg-zinc-50/50 dark:bg-zinc-950/20">
          <div class="text-xs font-semibold text-zinc-500 mb-2 uppercase tracking-wider">执行的 Git 命令日志</div>
          <div class="bg-zinc-950 dark:bg-black text-zinc-300 font-mono text-xs p-3 rounded-lg overflow-y-auto max-h-[160px] space-y-1.5 leading-relaxed border border-zinc-900 shadow-inner">
            <div v-if="executedCommands.length === 0" class="text-zinc-500 italic text-xs">暂无执行的命令</div>
            <div v-for="(cmd, idx) in executedCommands" :key="idx" class="flex items-start gap-1">
              <span class="text-zinc-600 shrink-0 font-bold">$</span>
              <span class="break-all text-left w-full">{{ cmd }}</span>
            </div>
          </div>
        </div>

        <div v-if="errorMessage" class="ta-process-startup-error mx-4 my-3 text-left">
          <strong class="text-xs">错误说明</strong>
          <p class="text-xs leading-normal mt-1">{{ errorMessage }}</p>
        </div>

        <footer class="ta-process-startup-footer">
          <span>{{ props.personalWorkspaceId }}</span>
          <button type="button" :disabled="committing" @click="showCommitProgressDialog = false">关闭</button>
        </footer>
      </section>
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

.git-merge-overlay {
  position: fixed;
  inset: 40px 450px 28px 310px;
  z-index: 60;
  min-width: 640px;
  background: #fff;
  box-shadow: 0 18px 50px rgb(24 24 27 / 18%);
}

@media (max-width: 1180px) {
  .git-merge-overlay {
    inset: 40px 0 28px 310px;
  }
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

.git-conflict-row {
  background: #fef2f2;
  color: #7f1d1d;
}

.git-conflict-row:hover {
  background: #fee2e2;
}

.git-conflict-note {
  padding: 4px 6px;
  font-size: 11px;
  color: #b91c1c;
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

.git-raw-status {
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

/* Progress Dialog Styles matching OpencodeProcessStartupDialog */
.ta-process-startup-backdrop {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgb(15 23 42 / 45%);
  backdrop-filter: blur(2px);
}

.ta-process-startup-dialog {
  width: min(500px, 100%);
  max-height: min(650px, calc(100vh - 40px));
  overflow: auto;
  border: 1px solid var(--ta-border, #d8dee9);
  border-radius: 12px;
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
  padding: 12px 16px;
  border-bottom: 1px solid var(--ta-border, #edf2f7);
  text-align: left;
}

.ta-process-startup-footer {
  border-top: 1px solid var(--ta-border, #edf2f7);
  border-bottom: 0;
  color: var(--ta-muted, #667085);
  font-size: 11px;
}

.ta-process-startup-close,
.ta-process-startup-footer button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 28px;
  border: 1px solid var(--ta-border, #e2e8f0);
  border-radius: 6px;
  background: var(--ta-surface, #f8fafc);
  color: inherit;
  font-size: 12px;
  cursor: pointer;
}

.ta-process-startup-close {
  width: 28px;
  padding: 0;
}

.ta-process-startup-footer button {
  padding: 0 12px;
  font-weight: 500;
}

.ta-process-startup-footer button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.ta-process-startup-steps {
  display: grid;
  gap: 0;
  list-style: none;
}

.ta-process-startup-step {
  display: grid;
  grid-template-columns: 24px 1fr;
  min-height: 40px;
  align-items: center;
  gap: 8px;
  border-bottom: 1px solid var(--ta-border-subtle, #f7fafc);
  text-align: left;
}

.ta-process-startup-step:last-child {
  border-bottom: 0;
}

.ta-process-startup-step-icon {
  color: var(--ta-muted, #94a3b8);
}

.ta-process-startup-step.is-running .ta-process-startup-step-icon {
  color: #2563eb;
  animation: ta-process-spin 1.1s linear infinite;
}

.ta-process-startup-step.is-succeeded .ta-process-startup-step-icon {
  color: #16a34a;
}

.ta-process-startup-step.is-failed .ta-process-startup-step-icon {
  color: #dc2626;
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
  font-size: 14px;
  font-weight: 500;
  color: #334155;
}

.ta-process-startup-step-copy small {
  color: var(--ta-muted, #64748b);
  font-size: 12px;
  font-weight: bold;
}

.ta-process-startup-error {
  padding: 10px 12px;
  border: 1px solid #fca5a5;
  border-radius: 8px;
  background: #fef2f2;
  color: #991b1b;
}

.ta-process-startup-error strong {
  font-size: 14px;
  display: block;
}

@keyframes ta-process-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
