<script setup lang="ts">
import { computed, ref, watch } from "vue";
import {
  AlertTriangle,
  ChevronDown,
  ChevronRight,
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
  AgentConfigProgressEvent,
  RunDiffFile,
  WorkspaceGitDiffFile,
  WorkspaceGitConflict,
  WorkspaceGitConflictResolution
} from "@test-agent/shared-types";
import { Badge, Button } from "@test-agent/ui-kit";
import { isWorkspaceAgentConfigPath } from "./workbench-utils";

type WorkspacePanelDiffFile = RunDiffFile & { rawStatus?: string };
type DiffScope = "WORKSPACE" | "AGENT_WORKSPACE" | "PUBLIC";
type WorkspaceAgentDiffFile = AgentConfigDiffFile & { pendingPublish?: boolean };
type AgentPanelDiffFile = WorkspaceAgentDiffFile & { scope: "PUBLIC" | "WORKSPACE" };

type PendingWorkspaceAgentPublish = {
  personalWorkspaceId: string;
  agentConfigWorkspaceId: string;
  files: string[];
  diffFiles: WorkspaceAgentDiffFile[];
};

const props = defineProps<{
  workspaceId?: string;
  /** 应用 Agent 配置所属的个人 workspace；与普通文件共用同一 Git worktree。 */
  agentConfigWorkspaceId?: string;
  /** 当前默认个人 worktree ID，用于本地提交和 feature 发布 */
  personalWorkspaceId?: string;
  /** 当前默认个人 worktree 分支，用于在变更面板标识提交目标。 */
  personalWorkspaceBranch?: string;
  /** Agent 文件成功落盘后递增，用于在面板隐藏时也主动刷新 diff。 */
  agentConfigRevision?: number;
  apiBaseUrl?: string;
  /** 当前页面内存中的用户绑定服务器。 */
  routeLinuxServerId?: string;
  canWrite: boolean;
  /** 应用级 Agent/Skill/Rules/Templates 的独立写权限。 */
  canManageAgentConfig?: boolean;
  /** 公共 Git Agent/Skill 的独立写权限，仅超级管理员可用。 */
  canManagePublicConfig?: boolean;
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
    totalCount?: number;
  }];
  "agent-files-discarded": [payload: {
    scope: "PUBLIC" | "WORKSPACE";
    paths: string[];
  }];
}>();

const workbench = useWorkbenchStore();
const api = createBackendApiClient({
  baseUrl: props.apiBaseUrl ?? "",
  routeLinuxServerId: () => props.routeLinuxServerId
});
const effectiveAgentConfigWorkspaceId = computed(() =>
  props.agentConfigWorkspaceId === undefined ? props.workspaceId : (props.agentConfigWorkspaceId || undefined)
);
const pendingWorkspaceAgentPublish = ref<PendingWorkspaceAgentPublish | null>(null);

/**
 * 应用 Agent 的“提交并推送”由两个 HTTP 请求组成。本地提交成功、远端发布失败时，
 * Git status 已经 clean，但这些文件仍是本轮需要重试的发布白名单，不能被轮询刷新清掉。
 */
function currentPendingWorkspaceAgentPublish(): PendingWorkspaceAgentPublish | null {
  const pending = pendingWorkspaceAgentPublish.value;
  if (
    !pending
    || !props.personalWorkspaceId
    || pending.personalWorkspaceId !== props.personalWorkspaceId
    || pending.agentConfigWorkspaceId !== effectiveAgentConfigWorkspaceId.value
  ) return null;
  return pending;
}

function rememberPendingWorkspaceAgentPublish(
  personalWorkspaceId: string,
  files: string[],
  diffFiles: AgentPanelDiffFile[]
) {
  const agentConfigWorkspaceId = effectiveAgentConfigWorkspaceId.value;
  if (!agentConfigWorkspaceId) return;
  pendingWorkspaceAgentPublish.value = {
    personalWorkspaceId,
    agentConfigWorkspaceId,
    files: [...files],
    diffFiles: diffFiles.map((file) => ({
      path: file.path,
      status: file.status,
      staged: true,
      patch: file.patch,
      pendingPublish: true
    }))
  };
  const pendingPaths = new Set(diffFiles.map((file) => file.path));
  workspaceAgentDiffs.value = workspaceAgentDiffs.value.map((file) => {
    const path = normalizeWorkspaceAgentDiffPath(file.path);
    return path && pendingPaths.has(path)
      ? { ...file, path, staged: true, pendingPublish: true }
      : file;
  });
}

function clearPendingWorkspaceAgentPublish() {
  pendingWorkspaceAgentPublish.value = null;
}

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
const activeConflictScope = ref<"WORKSPACE" | "PUBLIC" | null>(null);
const conflictLoading = ref(false);
const conflictResolving = ref(false);
const showCommitProgressDialog = ref(false);
const commitStep = ref(0);
const executedCommands = ref<string[]>([]);
const hasLivePublishCommand = ref(false);
const publishResultConfirmed = ref(false);
const workspaceMergeInProgress = ref(false);
const workspaceApplicationUpdatePending = ref(false);
const workspaceApplicationTargetCommit = ref<string | null>(null);
const commitRequestedPush = ref(false);
type CommitResultSummary = {
  committedFiles: number;
  pushedFiles: number;
  localOnlySpecFiles: number;
  hadRemotePush: boolean;
  scopes: Array<{
    scope: DiffScope;
    label: string;
    committedFiles: number;
    pushedFiles: number;
    localOnlySpecFiles: number;
    hadRemotePush: boolean;
  }>;
};
const commitResultSummary = ref<CommitResultSummary | null>(null);
const commitBatchCompleted = ref(true);

type PublishGitStep =
  | "PREPARE_REMOTE"
  | "PREPARING_REPOSITORY"
  | "COMMITTING"
  | "MERGING"
  | "PUSHING"
  | "BROADCASTING"
  | "PROJECT_HEAD"
  | "COMMIT_FEATURE"
  | "PUSH_REMOTE"
  | "COMPLETED";

function getCommitStepClass(stepNum: number) {
  if (errorMessage.value && commitStep.value === stepNum) {
    return "is-failed";
  }
  if (
    commitStep.value > stepNum
    || (commitStep.value === stepNum && !committing.value && !errorMessage.value)
  ) {
    return "is-succeeded";
  }
  if (commitStep.value === stepNum && committing.value) {
    return "is-running";
  }
  return "is-pending";
}

function getCommitStepIcon(stepNum: number) {
  if (errorMessage.value && commitStep.value === stepNum) {
    return XCircle;
  }
  if (
    commitStep.value > stepNum
    || (commitStep.value === stepNum && !committing.value && !errorMessage.value)
  ) {
    return CheckCircle2;
  }
  if (commitStep.value === stepNum && committing.value) {
    return Loader2;
  }
  return Circle;
}

function getCommitStepStatusText(stepNum: number) {
  if (errorMessage.value && commitStep.value === stepNum) {
    return "FAILED";
  }
  if (
    commitStep.value > stepNum
    || (commitStep.value === stepNum && !committing.value && !errorMessage.value)
  ) {
    return "SUCCEEDED";
  }
  if (commitStep.value === stepNum && committing.value) {
    return "RUNNING";
  }
  return "PENDING";
}

function commitStepNumber(step?: string | null): number {
  switch (step) {
    case "COMMITTING":
      return 2;
    case "MERGING":
    case "PROJECT_HEAD":
      return 3;
    case "PUSHING":
    case "COMMIT_FEATURE":
    case "PUSH_REMOTE":
      return 4;
    case "BROADCASTING":
    case "COMPLETED":
      return 5;
    case "PREPARING_REPOSITORY":
    case "PREPARE_REMOTE":
    default:
      return 1;
  }
}

function commandsForStep(commands: string[] | undefined, step?: string | null): string[] {
  if (!commands?.length) return [];
  if (!step || step === "COMPLETED") return commands;
  const matches = commands.filter((command) => {
    const normalized = ` ${command.toLowerCase()} `;
    switch (step as PublishGitStep) {
      case "PREPARE_REMOTE":
        return normalized.includes(" fetch ") || normalized.includes(" pull ");
      case "PREPARING_REPOSITORY":
        return normalized.includes(" status ") || normalized.includes(" fetch ") || normalized.includes(" pull ");
      case "PROJECT_HEAD":
        return normalized.includes(" checkout ") || normalized.includes(" rm ");
      case "MERGING":
        return normalized.includes(" merge ");
      case "COMMITTING":
      case "COMMIT_FEATURE":
        return normalized.includes(" commit ");
      case "PUSHING":
      case "PUSH_REMOTE":
        return normalized.includes(" push ");
      default:
        return true;
    }
  });
  return matches.length > 0 ? matches : commands;
}

function applyPublishExecution(step: string | null | undefined, commands?: string[]) {
  commitStep.value = commitStepNumber(step);
  const currentStepCommands = commandsForStep(commands, step);
  // 发布接口返回的是整条 Git 流程的历史命令。进度弹框只展示当前步骤的具体命令，
  // 因此兜底展示当前/失败步骤最后一条，避免成功后一次性把所有命令刷到面板。
  executedCommands.value = currentStepCommands.length > 0
    ? [currentStepCommands[currentStepCommands.length - 1]]
    : [];
}

function applyPublishProgressEvent(event: AgentConfigProgressEvent) {
  // HTTP 发布响应已经确认终态后，进度 WebSocket 可能仍有延迟 command 事件到达；
  // 此时不能再让旧 RUNNING 事件把成功弹框回退到运行中。
  if (publishResultConfirmed.value) {
    return;
  }
  if (event.currentStep) {
    commitStep.value = commitStepNumber(event.currentStep);
    if (!event.command?.trim() && event.status === "RUNNING") {
      executedCommands.value = [];
    }
  }
  if (event.command && event.command.trim()) {
    hasLivePublishCommand.value = true;
    executedCommands.value = [event.command];
  }
  if (event.type === "failed") {
    errorMessage.value = event.errorMessage ? `提交失败：${event.errorMessage}` : "提交失败";
  }
}

function publishErrorExecution(error: unknown) {
  if (!(error instanceof BackendApiError)) return;
  const failedStep = typeof error.details?.failedStep === "string"
    ? error.details.failedStep
    : null;
  const commands = Array.isArray(error.details?.executedCommands)
    ? error.details.executedCommands.filter((command): command is string => typeof command === "string")
    : [];
  if (failedStep || commands.length > 0) {
    applyPublishExecution(failedStep, commands);
  }
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
const stagingAllWorkspaceFiles = ref(false);
const unstagingAllWorkspaceFiles = ref(false);
const discardingAllWorkspaceFiles = ref(false);
const updatingAgentIndexPaths = ref<Set<string>>(new Set());
const stagingAllAgentFiles = ref(false);
const discardingAgentPaths = ref<Set<string>>(new Set());
const discardingAllAgentFiles = ref(false);

// Workspace diff computed lists
const workspaceUnstaged = computed(() =>
  workspaceDiffFiles.value.filter((f) => !stagedWorkspacePaths.value.has(f.path) && !isConflictFile(f))
);
const workspaceStaged = computed(() =>
  workspaceDiffFiles.value.filter((f) => stagedWorkspacePaths.value.has(f.path) && !isConflictFile(f))
);

// spec 是个人研发过程资产：允许编辑、暂存和本地提交，但任何角色都不得发布到 feature。
function isLocalOnlySpecPath(path: string): boolean {
  const segments = path.replace(/\\/g, "/").split("/").filter((segment) => segment && segment !== ".");
  return segments[0] === "spec";
}

/** AgentConfig API 使用 `.opencode` 内相对路径，个人 worktree 提交/发布使用 workspace 相对路径。 */
function workspaceAgentPersonalPath(path: string): string {
  const normalized = path.replaceAll("\\", "/").replace(/^\/+/, "");
  return normalized.startsWith(".opencode/") ? normalized : `.opencode/${normalized}`;
}
const workspaceConflicts = computed(() =>
  workspaceDiffFiles.value.filter((f) => isConflictFile(f))
);
const hasWorkspaceConflicts = computed(() => workspaceConflicts.value.length > 0);
const hasBlockingWorkspaceConflicts = computed(() => props.canWrite && hasWorkspaceConflicts.value);
const workspaceGitMutationPending = computed(() =>
  updatingWorkspaceIndexPaths.value.size > 0 || discardingWorkspacePaths.value.size > 0
);

function agentMutationKey(scope: "PUBLIC" | "WORKSPACE", path: string): string {
  return `${scope}:${path}`;
}

const agentGitMutationPending = computed(() =>
  updatingAgentIndexPaths.value.size > 0 || discardingAgentPaths.value.size > 0
);

// Agent diff lists (Public + Workspace)
const publicAgentDiffs = ref<AgentConfigDiffFile[]>([]);
const workspaceAgentDiffs = ref<WorkspaceAgentDiffFile[]>([]);
const publicAgentConflicts = computed(() =>
  publicAgentDiffs.value
    .filter((file) => isConflictFile(file))
    .map((file) => ({ ...file, scope: "PUBLIC" as const }))
);
const hasPublicAgentConflicts = computed(() => publicAgentConflicts.value.length > 0);
const hasBlockingAgentConflicts = computed(() =>
  canWriteAgentScope("PUBLIC") && hasPublicAgentConflicts.value
);

const publicAgentUnstaged = computed<AgentPanelDiffFile[]>(() =>
  publicAgentDiffs.value
    .filter((file) => !file.staged && !isConflictFile(file))
    .map((file) => ({ ...file, scope: "PUBLIC" }))
);
const publicAgentStaged = computed<AgentPanelDiffFile[]>(() =>
  publicAgentDiffs.value
    .filter((file) => file.staged && !isConflictFile(file))
    .map((file) => ({ ...file, scope: "PUBLIC" }))
);
const workspaceAgentUnstaged = computed<AgentPanelDiffFile[]>(() =>
  workspaceAgentDiffs.value.flatMap((file) => {
    const path = normalizeWorkspaceAgentDiffPath(file.path);
    return !file.staged && path && !isConflictFile(file)
      ? [{ ...file, path, scope: "WORKSPACE" as const }]
      : [];
  })
);
const workspaceAgentStaged = computed<AgentPanelDiffFile[]>(() =>
  workspaceAgentDiffs.value.flatMap((file) => {
    const path = normalizeWorkspaceAgentDiffPath(file.path);
    return file.staged && path && !isConflictFile(file)
      ? [{ ...file, path, scope: "WORKSPACE" as const }]
      : [];
  })
);
const workspaceAgentConflicts = computed(() =>
  workspaceAgentDiffs.value
    .filter((file) => isConflictFile(file))
    .map((file) => {
      const path = normalizeWorkspaceAgentDiffPath(file.path);
      return path ? { ...file, path, scope: "WORKSPACE" as const } : null;
    })
    .filter((file): file is Exclude<typeof file, null> => file !== null)
);
const hasAnyPersonalWorkspaceConflicts = computed(() =>
  workspaceConflicts.value.length + workspaceAgentConflicts.value.length > 0
);
const canCompleteWorkspaceMerge = computed(() =>
  props.canWrite && workspaceMergeInProgress.value && !hasAnyPersonalWorkspaceConflicts.value
);

const activeDiffScope = ref<DiffScope>("WORKSPACE");
const hasSelectedDiffScope = ref(false);
const diffScopes = computed(() => [
  {
    key: "WORKSPACE" as const,
    label: "workspace",
    count: workspaceUnstaged.value.length + workspaceStaged.value.length + workspaceConflicts.value.length
  },
  {
    key: "AGENT_WORKSPACE" as const,
    label: "应用Agent",
    count: workspaceAgentUnstaged.value.length + workspaceAgentStaged.value.length + workspaceAgentConflicts.value.length
  },
  {
    key: "PUBLIC" as const,
    label: "公共Agent",
    count: publicAgentUnstaged.value.length + publicAgentStaged.value.length + publicAgentConflicts.value.length
  }
]);
// 外层“变更”入口展示三个作用域的文件总量；分类 Tab 只负责分开展示，不改变总数口径。
const totalChangedFileCount = computed(() =>
  diffScopes.value.reduce((total, scope) => total + scope.count, 0)
);

function commitScopeLabel(scope: DiffScope): string {
  if (scope === "AGENT_WORKSPACE") return "应用 Agent";
  if (scope === "PUBLIC") return "公共 Agent";
  return "workspace";
}

function resetCommitBatch() {
  commitResultSummary.value = null;
  commitBatchCompleted.value = true;
}

function recordCommitResult(
  scope: DiffScope,
  result: Pick<CommitResultSummary, "committedFiles" | "pushedFiles" | "localOnlySpecFiles" | "hadRemotePush">
) {
  const scopes = [...(commitResultSummary.value?.scopes ?? [])];
  const index = scopes.findIndex((item) => item.scope === scope);
  const previous = index >= 0 ? scopes[index] : null;
  const nextScope = {
    scope,
    label: commitScopeLabel(scope),
    committedFiles: (previous?.committedFiles ?? 0) + result.committedFiles,
    pushedFiles: (previous?.pushedFiles ?? 0) + result.pushedFiles,
    localOnlySpecFiles: (previous?.localOnlySpecFiles ?? 0) + result.localOnlySpecFiles,
    hadRemotePush: (previous?.hadRemotePush ?? false) || result.hadRemotePush
  };
  if (index >= 0) scopes[index] = nextScope;
  else scopes.push(nextScope);
  commitResultSummary.value = {
    committedFiles: scopes.reduce((total, item) => total + item.committedFiles, 0),
    pushedFiles: scopes.reduce((total, item) => total + item.pushedFiles, 0),
    localOnlySpecFiles: scopes.reduce((total, item) => total + item.localOnlySpecFiles, 0),
    hadRemotePush: scopes.some((item) => item.hadRemotePush),
    scopes
  };
}
const activeScopeItem = computed(() =>
  diffScopes.value.find((scope) => scope.key === activeDiffScope.value) ?? diffScopes.value[0]
);
const activeScopeMeta = computed(() => {
  if (activeDiffScope.value !== "PUBLIC") {
    return props.personalWorkspaceBranch
      ? `个人 worktree · ${props.personalWorkspaceBranch}`
      : "个人 worktree";
  }
  return workbench.publicWorktree?.branch
    ? `公共个人 worktree · ${workbench.publicWorktree.branch}`
    : "公共个人 worktree";
});
const activeAgentUnstaged = computed<AgentPanelDiffFile[]>(() =>
  activeDiffScope.value === "PUBLIC" ? publicAgentUnstaged.value : workspaceAgentUnstaged.value
);
const activeAgentStaged = computed<AgentPanelDiffFile[]>(() =>
  activeDiffScope.value === "PUBLIC" ? publicAgentStaged.value : workspaceAgentStaged.value
);
const activeAgentConflicts = computed(() =>
  activeDiffScope.value === "PUBLIC" ? publicAgentConflicts.value : workspaceAgentConflicts.value
);
const activeUnstagedCount = computed(() => activeDiffScope.value === "WORKSPACE"
  ? workspaceUnstaged.value.length + workspaceConflicts.value.length
  : activeAgentUnstaged.value.length + activeAgentConflicts.value.length);
const activeStagedCount = computed(() => activeDiffScope.value === "WORKSPACE"
  ? workspaceStaged.value.length
  : activeAgentStaged.value.length);

function canWriteAgentScope(scope: "PUBLIC" | "WORKSPACE"): boolean {
  return scope === "PUBLIC"
    ? (props.canManagePublicConfig ?? props.canWrite)
    : (props.canManageAgentConfig ?? props.canWrite);
}

const hasWritableStagedChanges = computed(() =>
  activeDiffScope.value === "WORKSPACE"
    ? props.canWrite && workspaceStaged.value.length > 0
    : activeAgentStaged.value.some((file) => canWriteAgentScope(file.scope))
);
const hasPublishableStagedChanges = computed(() =>
  activeDiffScope.value === "WORKSPACE"
    ? props.canWrite && workspaceStaged.value.some((file) => !isLocalOnlySpecPath(file.path))
    : activeAgentStaged.value.some((file) => canWriteAgentScope(file.scope))
);
const workspaceStagedSpecCount = computed(() =>
  workspaceStaged.value.filter((file) => isLocalOnlySpecPath(file.path)).length
);
const workspaceStagedPublishableCount = computed(() =>
  workspaceStaged.value.length - workspaceStagedSpecCount.value
);
const workspaceCommitHint = computed(() => {
  if (activeDiffScope.value !== "WORKSPACE" || workspaceStagedSpecCount.value === 0) return "";
  if (workspaceStagedPublishableCount.value === 0) {
    return `${workspaceStagedSpecCount.value} 个 spec 文件只提交到个人 worktree，不会推送。`;
  }
  return `选择“提交并推送”时：提交 ${workspaceStaged.value.length} 个文件、推送 ${workspaceStagedPublishableCount.value} 个文件；其中 ${workspaceStagedSpecCount.value} 个 spec 文件只提交到个人 worktree。`;
});
const activeHasBlockingConflicts = computed(() =>
  activeDiffScope.value === "WORKSPACE"
    ? hasBlockingWorkspaceConflicts.value
    : activeDiffScope.value === "PUBLIC"
      ? hasBlockingAgentConflicts.value
      : canWriteAgentScope("WORKSPACE") && workspaceAgentConflicts.value.length > 0
);
const activeWorkspaceAgentPublishPending = computed(() =>
  activeDiffScope.value === "AGENT_WORKSPACE" && currentPendingWorkspaceAgentPublish() !== null
);

function selectInitialDiffScope() {
  if (hasSelectedDiffScope.value) return;
  if (diffScopes.value.some((scope) => scope.key === activeDiffScope.value && scope.count > 0)) return;
  const firstChangedScope = diffScopes.value.find((scope) => scope.count > 0);
  if (firstChangedScope) activeDiffScope.value = firstChangedScope.key;
}

function selectDiffScope(scope: DiffScope) {
  hasSelectedDiffScope.value = true;
  activeDiffScope.value = scope;
}

// Watch for workspace change
watch(
  () => props.workspaceId,
  () => {
    resetCommitBatch();
    clearPendingWorkspaceAgentPublish();
    workspaceMergeInProgress.value = false;
    workspaceApplicationUpdatePending.value = false;
    workspaceApplicationTargetCommit.value = null;
    stagedWorkspacePaths.value.clear();
    void refreshChanges();
  },
  { immediate: true }
);

watch(
  () => [props.personalWorkspaceId, effectiveAgentConfigWorkspaceId.value] as const,
  ([personalWorkspaceId, agentConfigWorkspaceId], previous) => {
    if (!previous) return;
    if (personalWorkspaceId !== previous[0] || agentConfigWorkspaceId !== previous[1]) {
      clearPendingWorkspaceAgentPublish();
    }
  }
);

watch(
  () => workbench.publicWorktree?.worktreeId,
  (_worktreeId, previousWorktreeId) => {
    if (previousWorktreeId) resetCommitBatch();
    // 公共个人 worktree 可能晚于面板挂载才准备完成，切换后重新统计公共 Agent 变更。
    void refreshChanges();
  }
);

watch(
  () => props.agentConfigRevision,
  (revision, previousRevision) => {
    if (revision !== previousRevision) {
      void refreshChanges();
    }
  }
);

/**
 * 轮询仍以真实 Git status 为主；只有 status 已 clean 的待重试发布文件才叠加回列表。
 * 如果同一路径再次出现真实改动，说明用户已继续编辑，旧发布快照立即失效并回到正常提交流程。
 */
function applyWorkspaceAgentDiffRefresh(files: AgentConfigDiffFile[]) {
  const pending = currentPendingWorkspaceAgentPublish();
  if (!pending) {
    workspaceAgentDiffs.value = files;
    return;
  }
  const refreshedPaths = new Set(
    files.flatMap((file) => {
      const path = normalizeWorkspaceAgentDiffPath(file.path);
      return path ? [path] : [];
    })
  );
  if (pending.diffFiles.some((file) => refreshedPaths.has(file.path))) {
    clearPendingWorkspaceAgentPublish();
    workspaceAgentDiffs.value = files;
    return;
  }
  workspaceAgentDiffs.value = [...files, ...pending.diffFiles];
}

async function refreshChanges(options: { preserveError?: boolean } = {}) {
  const token = ++refreshChangesToken;
  loading.value = true;
  if (!options.preserveError) {
    errorMessage.value = "";
  }
  try {
    if (workbench.useMockTestData) {
      applyMockChanges();
      selectInitialDiffScope();
      return;
    }

    // 1. 获取应用工作空间变更（使用本地 Git，不依赖 opencode /vcs/diff，避免 opencode 异常导致刷新失败）
    if (props.workspaceId) {
      try {
        const gitDiff = await api.getWorkspaceGitDiff(props.workspaceId);
        if (token !== refreshChangesToken) return;
        workspaceMergeInProgress.value = gitDiff.mergeInProgress === true;
        workspaceApplicationUpdatePending.value = gitDiff.applicationUpdatePending === true;
        workspaceApplicationTargetCommit.value = gitDiff.applicationTargetCommit ?? null;
        // `.opencode` 与普通文件同属个人 worktree，但在“应用Agent”视图单独展示和提交。
        const workspaceFiles = gitDiff.files
          .filter((f) => !isWorkspaceAgentConfigPath(f.path))
          .map((f) => ({
            path: f.path,
            rawStatus: f.rawStatus,
            status: f.status,
            patch: f.patch,
            additions: f.additions,
            deletions: f.deletions
          }));
        workspaceDiffFiles.value = workspaceFiles;
        // 同步后端 staged 状态到前端 Set
        const stagedPaths = new Set<string>();
        workspaceFiles.forEach((f) => {
          const source = gitDiff.files.find((candidate) => candidate.path === f.path);
          if (source?.staged) stagedPaths.add(f.path);
        });
        stagedWorkspacePaths.value = stagedPaths;
      } catch {
        if (token !== refreshChangesToken) return;
        workspaceDiffFiles.value = [];
        workspaceMergeInProgress.value = false;
        workspaceApplicationUpdatePending.value = false;
        workspaceApplicationTargetCommit.value = null;
      }
    } else {
      workspaceDiffFiles.value = [];
      workspaceMergeInProgress.value = false;
      workspaceApplicationUpdatePending.value = false;
      workspaceApplicationTargetCommit.value = null;
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
    if (effectiveAgentConfigWorkspaceId.value) {
      try {
        const wksDiff = await api.getWorkspaceAgentDiff(effectiveAgentConfigWorkspaceId.value);
        if (token !== refreshChangesToken) return;
        applyWorkspaceAgentDiffRefresh(wksDiff.files);
      } catch {
        if (token !== refreshChangesToken) return;
        applyWorkspaceAgentDiffRefresh([]);
      }
    } else {
      workspaceAgentDiffs.value = [];
    }
    selectInitialDiffScope();
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
  return value === "opencode.jsonc" || value.startsWith("agents/") || value.startsWith("skills/") ? value : null;
}

// 单文件和批量暂存复用同一真实 Git index 链路，避免批量操作产生第二套状态语义。
async function stageWorkspaceFiles(paths: string[]) {
  if (!props.canWrite || !props.workspaceId || paths.length === 0) return;
  const pendingPaths = paths.filter((path) => !updatingWorkspaceIndexPaths.value.has(path));
  if (pendingPaths.length === 0) return;
  errorMessage.value = "";
  updatingWorkspaceIndexPaths.value = new Set([...updatingWorkspaceIndexPaths.value, ...pendingPaths]);
  try {
    if (workbench.useMockTestData) {
      stagedWorkspacePaths.value = new Set([...stagedWorkspacePaths.value, ...pendingPaths]);
      return;
    }
    await api.stageWorkspaceGitFiles(props.workspaceId, pendingPaths);
    await refreshChanges();
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "暂存工作区文件失败");
  } finally {
    const next = new Set(updatingWorkspaceIndexPaths.value);
    pendingPaths.forEach((path) => next.delete(path));
    updatingWorkspaceIndexPaths.value = next;
  }
}

async function stageWorkspaceFile(path: string) {
  await stageWorkspaceFiles([path]);
}

async function stageAllWorkspaceChanges() {
  if (workspaceGitMutationPending.value || hasWorkspaceConflicts.value) return;
  const paths = workspaceUnstaged.value.map((file) => file.path);
  if (paths.length === 0) return;
  stagingAllWorkspaceFiles.value = true;
  try {
    await stageWorkspaceFiles(paths);
  } finally {
    stagingAllWorkspaceFiles.value = false;
  }
}

// 单文件和批量取消暂存也复用同一 index 更新链路，确保两个分组的 all 操作完全对称。
async function unstageWorkspaceFiles(paths: string[]) {
  if (!props.canWrite || !props.workspaceId || paths.length === 0) return;
  const pendingPaths = paths.filter((path) => !updatingWorkspaceIndexPaths.value.has(path));
  if (pendingPaths.length === 0) return;
  errorMessage.value = "";
  updatingWorkspaceIndexPaths.value = new Set([...updatingWorkspaceIndexPaths.value, ...pendingPaths]);
  try {
    if (workbench.useMockTestData) {
      const next = new Set(stagedWorkspacePaths.value);
      pendingPaths.forEach((path) => next.delete(path));
      stagedWorkspacePaths.value = next;
      return;
    }
    await api.unstageWorkspaceGitFiles(props.workspaceId, pendingPaths);
    await refreshChanges();
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "取消暂存工作区文件失败");
  } finally {
    const next = new Set(updatingWorkspaceIndexPaths.value);
    pendingPaths.forEach((path) => next.delete(path));
    updatingWorkspaceIndexPaths.value = next;
  }
}

async function unstageWorkspaceFile(path: string) {
  await unstageWorkspaceFiles([path]);
}

async function unstageAllWorkspaceChanges() {
  if (workspaceGitMutationPending.value) return;
  const paths = workspaceStaged.value.map((file) => file.path);
  if (paths.length === 0) return;
  unstagingAllWorkspaceFiles.value = true;
  try {
    await unstageWorkspaceFiles(paths);
  } finally {
    unstagingAllWorkspaceFiles.value = false;
  }
}

function notifyChangesRefreshed(paths?: string[], reloadOpenFiles?: boolean) {
  const payload: {
    paths?: string[];
    reloadOpenFiles?: boolean;
    files: WorkspaceGitDiffFile[];
    totalCount: number;
  } = {
    totalCount: totalChangedFileCount.value,
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
  const rawStatus = (file.rawStatus ?? file.status ?? "").trim().toUpperCase();
  return file.status === "conflict" || ["DD", "AU", "UD", "UA", "DU", "AA", "UU"].includes(rawStatus);
}

async function openWorkspaceConflict(path: string) {
  if (!props.canWrite || !props.workspaceId || conflictLoading.value) return;
  conflictLoading.value = true;
  errorMessage.value = "";
  try {
    activeConflict.value = await api.getWorkspaceGitConflict(props.workspaceId, path);
    activeConflictScope.value = "WORKSPACE";
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
  if (!props.canWrite || !props.workspaceId || !activeConflict.value || conflictResolving.value) return;
  conflictResolving.value = true;
  errorMessage.value = "";
  try {
    const path = activeConflict.value.path;
    await api.resolveWorkspaceGitConflict(props.workspaceId, { path, ...payload });
    activeConflict.value = null;
    activeConflictScope.value = null;
    await refreshChanges();
    notifyChangesRefreshed([path], true);
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "解决 Git 冲突失败");
  } finally {
    conflictResolving.value = false;
  }
}

async function abortWorkspaceConflict() {
  if (!props.canWrite || !props.workspaceId || conflictResolving.value) return;
  conflictResolving.value = true;
  errorMessage.value = "";
  try {
    await api.abortWorkspaceGitConflict(props.workspaceId);
    activeConflict.value = null;
    activeConflictScope.value = null;
    await refreshChanges();
    notifyChangesRefreshed(undefined, true);
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "取消 Git 合并失败");
  } finally {
    conflictResolving.value = false;
  }
}

async function resolveAllWorkspaceConflicts(resolution: "CURRENT" | "INCOMING") {
  if (!props.canWrite || !props.workspaceId || conflictResolving.value) return;
  const label = resolution === "CURRENT" ? "个人版本" : "远程应用版本";
  if (!window.confirm(`将 ${workspaceConflicts.value.length} 个冲突文件全部采用${label}，是否继续？`)) return;
  conflictResolving.value = true;
  errorMessage.value = "";
  try {
    await api.resolveAllWorkspaceGitConflicts(props.workspaceId, { resolution });
    activeConflict.value = null;
    activeConflictScope.value = null;
    await refreshChanges();
    notifyChangesRefreshed(undefined, true);
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "批量解决 Git 冲突失败");
  } finally {
    conflictResolving.value = false;
  }
}

async function completeWorkspaceMerge() {
  if (!canCompleteWorkspaceMerge.value || !props.workspaceId || conflictResolving.value) return;
  conflictResolving.value = true;
  errorMessage.value = "";
  try {
    await api.completeWorkspaceGitMerge(props.workspaceId);
    await refreshChanges();
    notifyChangesRefreshed(undefined, true);
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "完成 Git 合并失败");
  } finally {
    conflictResolving.value = false;
  }
}

async function openPublicAgentConflict(path: string) {
  if (!canWriteAgentScope("PUBLIC") || !workbench.publicWorktree?.worktreeId || conflictLoading.value) return;
  conflictLoading.value = true;
  errorMessage.value = "";
  try {
    activeConflict.value = await api.getPublicAgentGitConflict(
      path,
      workbench.publicWorktree.worktreeId,
      workbench.publicWorktree.linuxServerId ?? undefined
    );
    activeConflictScope.value = "PUBLIC";
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "读取公共 Agent Git 冲突失败");
  } finally {
    conflictLoading.value = false;
  }
}

async function resolvePublicAgentConflict(payload: {
  resolution: WorkspaceGitConflictResolution;
  content?: string | null;
}) {
  if (!activeConflict.value || !workbench.publicWorktree?.worktreeId || conflictResolving.value) return;
  conflictResolving.value = true;
  errorMessage.value = "";
  try {
    await api.resolvePublicAgentGitConflict({
      path: activeConflict.value.path,
      ...payload,
      worktreeId: workbench.publicWorktree.worktreeId,
      linuxServerId: workbench.publicWorktree.linuxServerId
    });
    activeConflict.value = null;
    activeConflictScope.value = null;
    await refreshChanges();
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "解决公共 Agent Git 冲突失败");
  } finally {
    conflictResolving.value = false;
  }
}

async function abortPublicAgentConflict() {
  if (!workbench.publicWorktree?.worktreeId || conflictResolving.value) return;
  conflictResolving.value = true;
  errorMessage.value = "";
  try {
    await api.abortPublicAgentGitConflict(
      workbench.publicWorktree.worktreeId,
      workbench.publicWorktree.linuxServerId ?? undefined
    );
    activeConflict.value = null;
    activeConflictScope.value = null;
    await refreshChanges();
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "取消公共 Agent Git 合并失败");
  } finally {
    conflictResolving.value = false;
  }
}

async function resolveAllPublicAgentConflicts(resolution: "CURRENT" | "INCOMING") {
  if (!workbench.publicWorktree?.worktreeId || conflictResolving.value) return;
  const label = resolution === "CURRENT" ? "本地个人版本" : "远端公共版本";
  if (!window.confirm(`将 ${publicAgentConflicts.value.length} 个公共 Agent 冲突文件全部采用${label}，是否继续？`)) return;
  conflictResolving.value = true;
  errorMessage.value = "";
  try {
    await api.resolveAllPublicAgentGitConflicts({
      resolution,
      worktreeId: workbench.publicWorktree.worktreeId,
      linuxServerId: workbench.publicWorktree.linuxServerId
    });
    activeConflict.value = null;
    activeConflictScope.value = null;
    await refreshChanges();
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "批量解决公共 Agent Git 冲突失败");
  } finally {
    conflictResolving.value = false;
  }
}

function resolveActiveConflict(payload: { resolution: WorkspaceGitConflictResolution; content?: string | null }) {
  return activeConflictScope.value === "PUBLIC"
    ? resolvePublicAgentConflict(payload)
    : resolveWorkspaceConflict(payload);
}

function abortActiveConflict() {
  return activeConflictScope.value === "PUBLIC"
    ? abortPublicAgentConflict()
    : abortWorkspaceConflict();
}

// 批量丢弃与单文件回退共用后端多路径 API，并一次刷新文件树和 Diff 状态。
async function discardWorkspaceFiles(paths: string[]) {
  if (!props.canWrite || !props.workspaceId || paths.length === 0) return;
  const pendingPaths = paths.filter((path) => !discardingWorkspacePaths.value.has(path));
  if (pendingPaths.length === 0) return;
  errorMessage.value = "";
  discardingWorkspacePaths.value = new Set([...discardingWorkspacePaths.value, ...pendingPaths]);
  try {
    if (workbench.useMockTestData) {
      const pendingPathSet = new Set(pendingPaths);
      workspaceDiffFiles.value = workspaceDiffFiles.value.filter((file) => !pendingPathSet.has(file.path));
      const next = new Set(stagedWorkspacePaths.value);
      pendingPaths.forEach((path) => next.delete(path));
      stagedWorkspacePaths.value = next;
      notifyChangesRefreshed(pendingPaths);
      return;
    }
    await api.discardWorkspaceGitFiles(props.workspaceId, pendingPaths);
    const next = new Set(stagedWorkspacePaths.value);
    pendingPaths.forEach((path) => next.delete(path));
    stagedWorkspacePaths.value = next;
    await refreshChanges();
    notifyChangesRefreshed(pendingPaths);
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "回退工作区文件失败");
  } finally {
    const next = new Set(discardingWorkspacePaths.value);
    pendingPaths.forEach((path) => next.delete(path));
    discardingWorkspacePaths.value = next;
  }
}

async function discardWorkspaceFile(path: string) {
  await discardWorkspaceFiles([path]);
}

async function discardAllWorkspaceChanges() {
  if (workspaceGitMutationPending.value || hasWorkspaceConflicts.value) return;
  const paths = [...workspaceUnstaged.value, ...workspaceStaged.value].map((file) => file.path);
  if (paths.length === 0) return;
  if (!window.confirm(`将丢弃应用工作空间的 ${paths.length} 个文件改动，此操作无法撤销，是否继续？`)) return;
  discardingAllWorkspaceFiles.value = true;
  try {
    await discardWorkspaceFiles(paths);
  } finally {
    discardingAllWorkspaceFiles.value = false;
  }
}

// 单文件和批量暂存共用同一 Agent Git index 链路，批量操作只向后端发送一次请求。
async function stageAgentFiles(files: AgentPanelDiffFile[]) {
  const scope = files[0]?.scope;
  if (!scope || !canWriteAgentScope(scope) || (scope === "WORKSPACE" && !effectiveAgentConfigWorkspaceId.value)) return;
  const paths = [...new Set(files.filter((file) => file.scope === scope).map((file) => file.path))];
  const pendingPaths = paths.filter((path) => !updatingAgentIndexPaths.value.has(agentMutationKey(scope, path)));
  if (pendingPaths.length === 0) return;
  errorMessage.value = "";
  updatingAgentIndexPaths.value = new Set([
    ...updatingAgentIndexPaths.value,
    ...pendingPaths.map((path) => agentMutationKey(scope, path))
  ]);
  try {
    if (workbench.useMockTestData) {
      if (scope === "PUBLIC") {
        publicAgentDiffs.value.forEach((file) => {
          if (pendingPaths.includes(file.path)) file.staged = true;
        });
      } else {
        workspaceAgentDiffs.value.forEach((file) => {
          if (pendingPaths.includes(file.path)) file.staged = true;
        });
      }
      return;
    }

    if (scope === "PUBLIC") {
      await api.stagePublicAgentFiles(pendingPaths, workbench.publicWorktree?.worktreeId);
    } else {
      await api.stageWorkspaceAgentFiles(effectiveAgentConfigWorkspaceId.value!, pendingPaths);
    }
    await refreshChanges();
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "暂存 Agent 文件失败");
  } finally {
    const next = new Set(updatingAgentIndexPaths.value);
    pendingPaths.forEach((path) => next.delete(agentMutationKey(scope, path)));
    updatingAgentIndexPaths.value = next;
  }
}

async function stageAgentFile(file: AgentPanelDiffFile) {
  await stageAgentFiles([file]);
}

async function stageAllAgentChanges() {
  if (agentGitMutationPending.value || activeAgentConflicts.value.length > 0) return;
  const files = [...activeAgentUnstaged.value];
  if (files.length === 0) return;
  stagingAllAgentFiles.value = true;
  try {
    await stageAgentFiles(files);
  } finally {
    stagingAllAgentFiles.value = false;
  }
}

// Unstage agent file (real / mock)
async function unstageAgentFile(file: AgentPanelDiffFile) {
  if (!canWriteAgentScope(file.scope)) return;
  if (file.pendingPublish) {
    errorMessage.value = "该文件已完成本地提交，当前等待重新推送，不能取消暂存。";
    return;
  }
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
      await api.unstageWorkspaceAgentFiles(effectiveAgentConfigWorkspaceId.value!, [file.path]);
    }
    await refreshChanges();
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "取消暂存 Agent 文件失败");
  }
}

/**
 * 两类 Agent 文件共用同一回退交互；后端分别落到应用版本个人 worktree 与公共个人 worktree。
 */
async function discardAgentFiles(files: AgentPanelDiffFile[]) {
  const scope = activeDiffScope.value === "PUBLIC" ? "PUBLIC" : "WORKSPACE";
  if (
    !canWriteAgentScope(scope)
    || files.length === 0
    || activeAgentConflicts.value.length > 0
    || (scope === "WORKSPACE" && !effectiveAgentConfigWorkspaceId.value)
  ) return;
  if (files.some((file) => file.pendingPublish)) {
    errorMessage.value = "待推送文件已完成本地提交，不能按工作树改动回退；请先重新推送。";
    return;
  }
  const paths = [...new Set(files.filter((file) => file.scope === scope).map((file) => file.path))];
  const pendingPaths = paths.filter((path) => !discardingAgentPaths.value.has(agentMutationKey(scope, path)));
  if (pendingPaths.length === 0) return;
  errorMessage.value = "";
  discardingAgentPaths.value = new Set([
    ...discardingAgentPaths.value,
    ...pendingPaths.map((path) => agentMutationKey(scope, path))
  ]);
  try {
    if (workbench.useMockTestData) {
      if (scope === "PUBLIC") {
        publicAgentDiffs.value = publicAgentDiffs.value.filter((file) => !pendingPaths.includes(file.path));
      } else {
        workspaceAgentDiffs.value = workspaceAgentDiffs.value.filter((file) => !pendingPaths.includes(file.path));
      }
    } else if (scope === "PUBLIC") {
      await api.discardPublicAgentFiles(pendingPaths, workbench.publicWorktree?.worktreeId);
      await refreshChanges();
    } else {
      await api.discardWorkspaceAgentFiles(effectiveAgentConfigWorkspaceId.value!, pendingPaths);
      await refreshChanges();
    }
    emit("agent-files-discarded", { scope, paths: pendingPaths });
  } catch (error) {
    errorMessage.value = errorMessageFor(error, `回退${scope === "PUBLIC" ? "公共" : "应用"} Agent 文件失败`);
  } finally {
    const next = new Set(discardingAgentPaths.value);
    pendingPaths.forEach((path) => next.delete(agentMutationKey(scope, path)));
    discardingAgentPaths.value = next;
  }
}

async function discardAgentFile(file: AgentPanelDiffFile) {
  await discardAgentFiles([file]);
}

async function discardAllAgentChanges() {
  if (agentGitMutationPending.value || activeAgentConflicts.value.length > 0) return;
  const files = [...activeAgentUnstaged.value, ...activeAgentStaged.value];
  if (files.length === 0) return;
  const scopeLabel = activeDiffScope.value === "PUBLIC" ? "公共 Agent" : "应用 Agent";
  if (!window.confirm(`将丢弃 ${scopeLabel} 的 ${files.length} 个文件改动，此操作无法撤销，是否继续？`)) return;
  discardingAllAgentFiles.value = true;
  try {
    await discardAgentFiles(files);
  } finally {
    discardingAllAgentFiles.value = false;
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

function agentRunDiffFile(file: AgentPanelDiffFile): WorkspacePanelDiffFile {
  return {
    path: file.path,
    status: file.status,
    patch: file.patch,
    additions: 0,
    deletions: 0
  };
}

// Commit changes
async function handleCommit(push = false) {
  if (committing.value || !hasWritableStagedChanges.value) return;
  const operationScope = activeDiffScope.value;
  const retryingWorkspaceAgentPublish = operationScope === "AGENT_WORKSPACE"
    ? currentPendingWorkspaceAgentPublish()
    : null;
  if (!push && retryingWorkspaceAgentPublish) {
    errorMessage.value = "应用 Agent 文件已完成本地提交，请使用“重新推送”继续发布。";
    progressMessage.value = "";
    return;
  }
  if (activeDiffScope.value === "WORKSPACE" && props.canWrite && hasWorkspaceConflicts.value) {
    errorMessage.value = "当前个人工作区存在合并冲突，请先解决冲突文件后再重新提交并推送。";
    progressMessage.value = "";
    return;
  }
  if (activeDiffScope.value === "PUBLIC" && hasBlockingAgentConflicts.value) {
    errorMessage.value = "公共 Agent 个人 worktree 存在合并冲突，请先解决冲突文件后再提交并推送。";
    progressMessage.value = "";
    return;
  }
  if (activeDiffScope.value === "AGENT_WORKSPACE" && workspaceAgentConflicts.value.length > 0) {
    errorMessage.value = "应用 Agent 所在的个人 worktree 存在合并冲突，请先解决后再提交并推送。";
    progressMessage.value = "";
    return;
  }
  const msg = commitMessage.value.trim();
  if (!msg) {
    errorMessage.value = "请输入提交说明";
    return;
  }

  const plannedCommittedFileCount = activeDiffScope.value === "WORKSPACE"
    ? workspaceStaged.value.length
    : retryingWorkspaceAgentPublish
      ? retryingWorkspaceAgentPublish.diffFiles.length
      : activeAgentStaged.value.filter((file) => canWriteAgentScope(file.scope)).length;
  const plannedLocalOnlySpecFileCount = activeDiffScope.value === "WORKSPACE"
    ? workspaceStagedSpecCount.value
    : 0;
  const plannedPushedFileCount = push
    ? activeDiffScope.value === "WORKSPACE"
      ? workspaceStagedPublishableCount.value
      : plannedCommittedFileCount
    : 0;

  committing.value = true;
  errorMessage.value = "";
  progressMessage.value = "";
  executedCommands.value = [];
  hasLivePublishCommand.value = false;
  publishResultConfirmed.value = false;
  commitRequestedPush.value = push;
  if (commitBatchCompleted.value) {
    commitResultSummary.value = null;
    commitBatchCompleted.value = false;
  }
  showCommitProgressDialog.value = false;
  commitStep.value = 0;
  let publishAttempted = false;
  let remotePublishCompleted = false;
  let localOnlySpecFileCount = 0;

  try {
    if (workbench.useMockTestData) {
      showCommitProgressDialog.value = true;
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
      
      if (activeDiffScope.value === "WORKSPACE") {
        stagedWorkspacePaths.value.clear();
      } else if (activeDiffScope.value === "PUBLIC") {
        publicAgentDiffs.value.forEach((file) => { file.staged = false; });
      } else {
        workspaceAgentDiffs.value.forEach((file) => { file.staged = false; });
      }
      
      commitMessage.value = "";
      recordCommitResult(operationScope, {
        committedFiles: plannedCommittedFileCount,
        pushedFiles: plannedPushedFileCount,
        localOnlySpecFiles: plannedLocalOnlySpecFileCount,
        hadRemotePush: push && plannedPushedFileCount > 0
      });
      progressMessage.value = push ? "提交并推送成功！(测试数据)" : "提交成功！(测试数据)";
      setTimeout(() => {
        progressMessage.value = "";
      }, 2000);
      committing.value = false;
      return;
    }

    // 1. 应用工作空间先提交个人 worktree；推送时再从个人 HEAD 投影到 feature worktree。
    if (activeDiffScope.value === "WORKSPACE" && props.canWrite && workspaceStaged.value.length > 0) {
      if (!props.personalWorkspaceId) {
        errorMessage.value = "当前不是个人 worktree，不能提交或发布应用变更。";
        progressMessage.value = "";
        committing.value = false;
        return;
      }
      const personalWorkspaceId = props.personalWorkspaceId;
      progressMessage.value = "正在提交个人 worktree...";
      showCommitProgressDialog.value = true;
      commitStep.value = 2;
      const files = workspaceStaged.value.map((file) => file.path);
      const publishableFiles = files.filter((file) => !isLocalOnlySpecPath(file));
      localOnlySpecFileCount = files.length - publishableFiles.length;
      await api.commitPersonalWorkspace(personalWorkspaceId, {
        commitMessage: msg,
        files,
        operationId: newOperationId()
      });
      if (push && publishableFiles.length > 0) {
        publishAttempted = true;
        progressMessage.value = "正在从个人 HEAD 投影并推送 feature 分支...";
        commitStep.value = 3;
        const publishOperationId = newOperationId();
        let publishProgressSocket: { close: () => void } | null = null;
        try {
          publishProgressSocket = await api.connectAgentConfigProgress(publishOperationId, applyPublishProgressEvent);
        } catch {
          publishProgressSocket = null;
        }
        const result = await (async () => {
          try {
            return await api.publishPersonalWorkspace(personalWorkspaceId, {
              commitMessage: msg,
              files: publishableFiles,
              operationId: publishOperationId
            });
          } finally {
            setTimeout(() => publishProgressSocket?.close(), 1000);
          }
        })();
        if (!hasLivePublishCommand.value) {
          applyPublishExecution(result.currentStep, result.executedCommands);
        } else if (result.currentStep) {
          commitStep.value = commitStepNumber(result.currentStep);
        }
        if (result.status !== "PUBLISHED" || result.remotePushed !== true) {
          throw new Error("feature 分支推送结果未确认，请刷新变更列表后重试。");
        }
        publishResultConfirmed.value = true;
        remotePublishCompleted = true;
        commitStep.value = 5;
        progressMessage.value = "已从个人 HEAD 投影并推送到应用 feature 分支！";
      } else if (push && localOnlySpecFileCount > 0) {
        commitStep.value = 2;
        progressMessage.value = "spec 文件已提交到个人 worktree，按权限规则不推送。";
      } else {
        // 仅本地提交时，应用 feature 投影和远端推送保持未执行，进度只到本地提交。
        commitStep.value = 2;
        progressMessage.value = "个人 worktree 提交成功（尚未推送）。";
      }
      stagedWorkspacePaths.value.clear();
      await new Promise((resolve) => setTimeout(resolve, 500));
    }

    // 2. Commit Agent PUBLIC changes
    const publicStagedCount = canWriteAgentScope("PUBLIC")
      ? publicAgentDiffs.value.filter((f) => f.staged).length
      : 0;
    if (activeDiffScope.value === "PUBLIC" && publicStagedCount > 0) {
      progressMessage.value = "正在提交公共 Agent 配置...";
      showCommitProgressDialog.value = true;
      commitStep.value = 2;
      const opId = newOperationId();
      await runAgentOperation(
        () => api.commitPublicAgentConfig({ message: msg, worktreeId: workbench.publicWorktree?.worktreeId, operationId: opId }),
        "提交公共 Agent 配置",
        opId
      );
      commitStep.value = 2;
      if (push) {
        publishAttempted = true;
        progressMessage.value = "正在发布公共 Agent 配置...";
        const pushOpId = newOperationId();
        await runAgentOperation(
          () => api.publishPublicAgentConfig(workbench.publicWorktree?.worktreeId, pushOpId),
          "发布公共 Agent 配置",
          pushOpId,
          true
        );
        commitStep.value = 5;
        remotePublishCompleted = true;
      }
    }

    // 3. 应用 Agent 与普通文件共用个人 worktree，只是按 `.opencode` 路径隔离提交范围。
    const workspaceStagedPanelFiles = canWriteAgentScope("WORKSPACE")
      ? [...workspaceAgentStaged.value]
      : [];
    const workspaceStagedFiles = retryingWorkspaceAgentPublish?.files
      ?? workspaceStagedPanelFiles.map((file) => workspaceAgentPersonalPath(file.path));
    if (activeDiffScope.value === "AGENT_WORKSPACE" && workspaceStagedFiles.length > 0) {
      if (!props.personalWorkspaceId) {
        errorMessage.value = "当前不是个人 worktree，不能提交或发布应用 Agent。";
        progressMessage.value = "";
        committing.value = false;
        return;
      }
      progressMessage.value = retryingWorkspaceAgentPublish
        ? "正在重新推送已完成本地提交的应用 Agent 配置..."
        : "正在提交个人 worktree 中的应用 Agent 配置...";
      showCommitProgressDialog.value = true;
      commitStep.value = 2;
      if (!retryingWorkspaceAgentPublish) {
        await api.commitPersonalWorkspace(props.personalWorkspaceId, {
          commitMessage: msg,
          files: workspaceStagedFiles,
          operationId: newOperationId()
        });
        if (push) {
          rememberPendingWorkspaceAgentPublish(
            props.personalWorkspaceId,
            workspaceStagedFiles,
            workspaceStagedPanelFiles
          );
        }
      }
      commitStep.value = 2;
      if (push) {
        publishAttempted = true;
        progressMessage.value = "正在从个人 HEAD 投影并推送应用 Agent 配置...";
        commitStep.value = 3;
        const pushOpId = newOperationId();
        let publishProgressSocket: { close: () => void } | null = null;
        try {
          publishProgressSocket = await api.connectAgentConfigProgress(pushOpId, applyPublishProgressEvent);
        } catch {
          publishProgressSocket = null;
        }
        const result = await (async () => {
          try {
            return await api.publishPersonalWorkspace(props.personalWorkspaceId!, {
              commitMessage: msg,
              files: workspaceStagedFiles,
              operationId: pushOpId
            });
          } finally {
            setTimeout(() => publishProgressSocket?.close(), 1000);
          }
        })();
        if (!hasLivePublishCommand.value) {
          applyPublishExecution(result.currentStep, result.executedCommands);
        }
        if (result.status !== "PUBLISHED" || result.remotePushed !== true) {
          throw new Error("应用 Agent 的 feature 分支推送结果未确认，请刷新后重试。");
        }
        publishResultConfirmed.value = true;
        remotePublishCompleted = true;
        clearPendingWorkspaceAgentPublish();
        commitStep.value = 5;
      }
    }

    commitMessage.value = "";
    recordCommitResult(operationScope, {
      committedFiles: plannedCommittedFileCount,
      pushedFiles: remotePublishCompleted ? plannedPushedFileCount : 0,
      localOnlySpecFiles: plannedLocalOnlySpecFileCount,
      hadRemotePush: remotePublishCompleted
    });
    if (push && localOnlySpecFileCount > 0) {
      progressMessage.value = remotePublishCompleted
        ? `可发布文件已推送；${localOnlySpecFileCount} 个 spec 文件仅提交到个人 worktree。`
        : `${localOnlySpecFileCount} 个 spec 文件已提交到个人 worktree，未推送。`;
    } else {
      progressMessage.value = push ? "提交并推送成功！" : "提交成功！";
    }
    await refreshChanges();
    commitBatchCompleted.value = totalChangedFileCount.value === 0;
    setTimeout(() => {
      progressMessage.value = "";
    }, 2000);
  } catch (error) {
    publishErrorExecution(error);
    // 失败响应已经给出终态；阻断延迟到达的 RUNNING WebSocket 事件把失败步骤重新改成转圈。
    publishResultConfirmed.value = true;
    progressMessage.value = "";
    const publishError = errorMessageFor(error, "提交失败");
    errorMessage.value = publishError;
    if (publishAttempted) {
      // 错误弹框先立即结束；后台刷新用于补充冲突状态，应用 Agent 的待重试快照会跨轮询保留。
      void refreshChanges({ preserveError: true });
    }
  } finally {
    committing.value = false;
  }
}

async function runAgentOperation<T>(
  action: () => Promise<T>,
  label: string,
  operationId: string,
  trackPublishProgress = false
) {
  if (trackPublishProgress) {
    hasLivePublishCommand.value = false;
    publishResultConfirmed.value = false;
  }
  let socket: { close: () => void } | null = null;
  try {
    socket = await api.connectAgentConfigProgress(operationId, (event) => {
      if (event.currentStep) {
        progressMessage.value = `${label}: ${event.currentStep} (${event.status || ''})`;
      }
      if (trackPublishProgress) {
        applyPublishProgressEvent(event);
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
    if (trackPublishProgress && result && typeof result === "object") {
      const operation = result as { currentStep?: string | null; executedCommands?: string[] };
      if (!hasLivePublishCommand.value) {
        applyPublishExecution(operation.currentStep, operation.executedCommands);
      }
      publishResultConfirmed.value = true;
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

function getFileName(path: string): string {
  if (!path) return "";
  const normalized = path.replace(/\\/g, "/");
  return normalized.split("/").filter(Boolean).pop() || path;
}

function getStatusLabel(status?: string): string {
  if (!status) return "M";
  const s = status.toLowerCase();
  if (s === "untracked") return "U";
  return s.charAt(0).toUpperCase();
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
        @resolve="resolveActiveConflict"
        @abort="abortActiveConflict"
        @close="activeConflict = null; activeConflictScope = null"
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

    <div class="git-scope-switcher" role="tablist" aria-label="Git 变更作用域">
      <button
        v-for="scope in diffScopes"
        :key="scope.key"
        type="button"
        role="tab"
        :aria-selected="activeDiffScope === scope.key"
        :class="['git-scope-tab', { 'is-active': activeDiffScope === scope.key }]"
        @click="selectDiffScope(scope.key)"
      >
        <span class="git-scope-tab-label">{{ scope.label }}</span>
        <span class="git-scope-tab-count">{{ scope.count }}</span>
      </button>
    </div>

    <!-- Scrollable file list area -->
    <div class="git-lists-container">
      <!-- 1. UNSTAGED SECTION -->
      <div class="git-section git-unstaged-section" :style="unstagedStyle" :class="{ 'is-collapsed': !unstagedExpanded }">
        <div class="git-section-header" @click="unstagedExpanded = !unstagedExpanded">
          <ChevronDown v-if="unstagedExpanded" class="h-3.5 w-3.5" :stroke-width="1.5" />
          <ChevronRight v-else class="h-3.5 w-3.5" :stroke-width="1.5" />
          <span class="git-section-title">UNSTAGED (未暂存) ({{ activeUnstagedCount }})</span>
          <div class="git-section-actions ml-auto">
            <button
              type="button"
              class="git-refresh-btn"
              title="刷新变更列表"
              @click.stop="refreshChanges()"
              :disabled="loading"
            >
              <RefreshCw class="h-3 w-3" :class="{ 'animate-spin': loading }" :stroke-width="1.5" />
            </button>
            <template v-if="activeDiffScope === 'WORKSPACE'">
              <Button
                size="icon"
                variant="ghost"
                class="git-bulk-action"
                aria-label="丢弃全部应用工作空间改动"
                :title="hasWorkspaceConflicts ? '存在未解决冲突，请先处理或取消合并' : '丢弃全部应用工作空间改动'"
                :disabled="!props.canWrite || hasWorkspaceConflicts || workspaceDiffFiles.length === 0 || workspaceGitMutationPending"
                @click.stop="discardAllWorkspaceChanges"
              >
                <Loader2 v-if="discardingAllWorkspaceFiles" class="h-3.5 w-3.5 animate-spin" :stroke-width="1.5" />
                <Undo2 v-else class="h-3.5 w-3.5" :stroke-width="1.5" />
              </Button>
              <Button
                size="icon"
                variant="ghost"
                class="git-bulk-action"
                aria-label="全部暂存应用工作空间变更"
                :title="hasWorkspaceConflicts ? '存在未解决冲突，请先处理或取消合并' : '全部暂存应用工作空间变更'"
                :disabled="!props.canWrite || hasWorkspaceConflicts || workspaceUnstaged.length === 0 || workspaceGitMutationPending"
                @click.stop="stageAllWorkspaceChanges"
              >
                <Loader2 v-if="stagingAllWorkspaceFiles" class="h-3.5 w-3.5 animate-spin" :stroke-width="1.5" />
                <Plus v-else class="h-3.5 w-3.5" :stroke-width="1.5" />
              </Button>
            </template>
            <template v-else>
              <Button
                size="icon"
                variant="ghost"
                class="git-bulk-action"
                :aria-label="`丢弃全部${activeScopeItem.label}改动`"
                :title="activeAgentConflicts.length > 0 ? '存在未解决冲突，请先处理或取消合并' : `丢弃全部${activeScopeItem.label}改动`"
                :disabled="!canWriteAgentScope(activeDiffScope === 'PUBLIC' ? 'PUBLIC' : 'WORKSPACE') || activeAgentConflicts.length > 0 || (activeAgentUnstaged.length === 0 && activeAgentStaged.length === 0) || agentGitMutationPending || activeWorkspaceAgentPublishPending"
                @click.stop="discardAllAgentChanges"
              >
                <Loader2 v-if="discardingAllAgentFiles" class="h-3.5 w-3.5 animate-spin" :stroke-width="1.5" />
                <Undo2 v-else class="h-3.5 w-3.5" :stroke-width="1.5" />
              </Button>
              <Button
                size="icon"
                variant="ghost"
                class="git-bulk-action"
                :aria-label="`全部暂存${commitScopeLabel(activeDiffScope)} 变更`"
                :title="activeAgentConflicts.length > 0 ? '存在未解决冲突，请先处理或取消合并' : `全部暂存${commitScopeLabel(activeDiffScope)} 变更`"
                :disabled="!canWriteAgentScope(activeDiffScope === 'PUBLIC' ? 'PUBLIC' : 'WORKSPACE') || activeAgentConflicts.length > 0 || activeAgentUnstaged.length === 0 || agentGitMutationPending"
                @click.stop="stageAllAgentChanges"
              >
                <Loader2 v-if="stagingAllAgentFiles" class="h-3.5 w-3.5 animate-spin" :stroke-width="1.5" />
                <Plus v-else class="h-3.5 w-3.5" :stroke-width="1.5" />
              </Button>
            </template>
          </div>
        </div>

        <div v-show="unstagedExpanded" class="git-section-content">
          <!-- 1a. Application Workspace -->
          <div v-if="activeDiffScope === 'WORKSPACE'" class="git-sub-section">
            <div class="git-sub-content px-2 py-0.5 space-y-0.5">
              <div v-if="canCompleteWorkspaceMerge" class="git-conflict-banner">
                <div class="git-conflict-header">
                  <CheckCircle2 class="h-3.5 w-3.5 text-emerald-600 dark:text-emerald-500 shrink-0" />
                  <span>冲突已全部解决，等待完成 feature 合并</span>
                </div>
                <div class="git-conflict-actions">
                  <Button
                    size="sm"
                    variant="ghost"
                    class="git-conflict-action-btn"
                    :disabled="conflictResolving"
                    @click.stop="completeWorkspaceMerge"
                  >
                    完成合并
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    class="git-conflict-action-btn btn-abort"
                    :disabled="conflictResolving"
                    @click.stop="abortWorkspaceConflict"
                  >
                    取消合并
                  </Button>
                </div>
              </div>
              <div
                v-else-if="workspaceApplicationUpdatePending && !workspaceMergeInProgress"
                class="git-conflict-note"
              >
                应用 feature 有待同步更新<span v-if="workspaceApplicationTargetCommit">（{{ workspaceApplicationTargetCommit.slice(0, 12) }}）</span>；
                请先提交或回退当前个人变更，系统随后自动重试 Git 合并。
              </div>
              <div
                v-else-if="workspaceMergeInProgress && workspaceAgentConflicts.length > 0 && workspaceConflicts.length === 0"
                class="git-conflict-note"
              >
                当前合并仍有应用 Agent 冲突，请切换到“应用Agent”处理。
              </div>
              <div v-if="workspaceConflicts.length > 0" class="git-conflict-banner">
                <div class="git-conflict-header">
                  <AlertTriangle class="h-3.5 w-3.5 text-amber-600 dark:text-amber-500 shrink-0" />
                  <span>检测到 {{ workspaceConflicts.length }} 个冲突</span>
                </div>
                <div v-if="props.canWrite" class="git-conflict-actions">
                  <Button
                    size="sm"
                    variant="ghost"
                    class="git-conflict-action-btn"
                    aria-label="保留个人"
                    title="保留个人 (Mine)"
                    :disabled="conflictResolving"
                    @click.stop="resolveAllWorkspaceConflicts('CURRENT')"
                  >
                    保留本地
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    class="git-conflict-action-btn"
                    aria-label="采用远程"
                    title="采用远程 (Theirs)"
                    :disabled="conflictResolving"
                    @click.stop="resolveAllWorkspaceConflicts('INCOMING')"
                  >
                    保留远程
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    class="git-conflict-action-btn btn-abort"
                    aria-label="取消"
                    title="取消本次合并 (Abort)"
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
                @click="props.canWrite && openWorkspaceConflict(file.path)"
              >
                <Badge tone="danger" class="mr-1 py-0 px-1 text-[9px] uppercase">CONFLICT</Badge>
                <span class="git-file-name" :title="file.path">{{ getFileName(file.path) }}</span>
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
                <Badge :tone="getBadgeTone(file.status)" class="mr-1 py-0 px-1 text-[9px] uppercase">{{ getStatusLabel(file.status) }}</Badge>
                <span class="git-file-name" :title="file.path">{{ getFileName(file.path) }}</span>
                <Badge v-if="isLocalOnlySpecPath(file.path)" tone="neutral" class="ml-1 py-0 px-1 text-[9px]">仅本地</Badge>
                <span v-if="file.additions" class="git-additions ml-1">+{{ file.additions }}</span>
                <span v-if="file.deletions" class="git-deletions ml-1">-{{ file.deletions }}</span>
                
                <button
                  v-if="props.canWrite"
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
                  v-if="props.canWrite"
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

          <!-- 1b. Agent/Skill scope; one scope is shown at a time to keep the panel readable. -->
          <div v-else class="git-sub-section">
            <div class="git-sub-content px-2 py-0.5 space-y-0.5">
              <div v-if="activeDiffScope === 'PUBLIC' && publicAgentConflicts.length > 0" class="git-conflict-banner">
                <div class="git-conflict-header">
                  <AlertTriangle class="h-3.5 w-3.5 text-amber-600 dark:text-amber-500 shrink-0" />
                  <span>检测到 {{ publicAgentConflicts.length }} 个公共 Agent 冲突</span>
                </div>
                <div class="git-conflict-actions">
                  <Button
                    size="sm"
                    variant="ghost"
                    class="git-conflict-action-btn"
                    :disabled="conflictResolving"
                    @click.stop="resolveAllPublicAgentConflicts('CURRENT')"
                  >
                    保留本地
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    class="git-conflict-action-btn"
                    :disabled="conflictResolving"
                    @click.stop="resolveAllPublicAgentConflicts('INCOMING')"
                  >
                    保留远程
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    class="git-conflict-action-btn btn-abort"
                    :disabled="conflictResolving"
                    @click.stop="abortPublicAgentConflict"
                  >
                    取消
                  </Button>
                </div>
              </div>
              <div v-if="activeDiffScope === 'AGENT_WORKSPACE' && workspaceAgentConflicts.length > 0" class="git-conflict-note">
                检测到 {{ workspaceAgentConflicts.length }} 个应用 Agent 冲突，点击文件后使用与 workspace 相同的合并编辑器处理。
              </div>
              <div v-else-if="activeDiffScope === 'AGENT_WORKSPACE' && canCompleteWorkspaceMerge" class="git-conflict-banner">
                <div class="git-conflict-header">
                  <CheckCircle2 class="h-3.5 w-3.5 text-emerald-600 dark:text-emerald-500 shrink-0" />
                  <span>应用配置冲突已解决，等待完成 feature 合并</span>
                </div>
                <div class="git-conflict-actions">
                  <Button
                    size="sm"
                    variant="ghost"
                    class="git-conflict-action-btn"
                    :disabled="conflictResolving"
                    @click.stop="completeWorkspaceMerge"
                  >
                    完成合并
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    class="git-conflict-action-btn btn-abort"
                    :disabled="conflictResolving"
                    @click.stop="abortWorkspaceConflict"
                  >
                    取消合并
                  </Button>
                </div>
              </div>
              <div
                v-else-if="activeDiffScope === 'AGENT_WORKSPACE' && workspaceApplicationUpdatePending && !workspaceMergeInProgress"
                class="git-conflict-note"
              >
                应用 feature 有待同步更新<span v-if="workspaceApplicationTargetCommit">（{{ workspaceApplicationTargetCommit.slice(0, 12) }}）</span>；
                请先提交或回退当前个人变更，系统随后自动重试 Git 合并。
              </div>
              <div
                v-for="file in activeAgentConflicts"
                :key="`${activeDiffScope.toLowerCase()}-conflict:${file.path}`"
                class="git-file-row git-conflict-row group"
                :title="file.path"
                :aria-label="file.path"
                @click="activeDiffScope === 'PUBLIC'
                  ? openPublicAgentConflict(file.path)
                  : openWorkspaceConflict(workspaceAgentPersonalPath(file.path))"
              >
                <Badge tone="danger" class="mr-1 py-0 px-1 text-[9px] uppercase">CONFLICT</Badge>
                <span class="git-file-name" :title="file.path">
                  <span class="git-scope-label">[{{ activeDiffScope === 'PUBLIC' ? '公共' : '应用级' }}]</span>
                  {{ getFileName(file.path) }}
                </span>
              </div>
              <div v-if="activeAgentUnstaged.length === 0 && activeAgentConflicts.length === 0" class="git-empty-text">暂无变更</div>
              <div
                v-for="file in activeAgentUnstaged"
                :key="file.path"
                class="git-file-row group"
                :title="file.path"
                :aria-label="file.path"
                @click="handleOpenFileDiff(file.path, 'agent', file.scope, agentRunDiffFile(file))"
              >
                <Badge :tone="getBadgeTone(file.status)" class="mr-1 py-0 px-1 text-[9px] uppercase">{{ getStatusLabel(file.status) }}</Badge>
                <span class="git-file-name" :title="file.path">
                  {{ getFileName(file.path) }}
                </span>
                
                <button
                  v-if="canWriteAgentScope(file.scope)"
                  type="button"
                  class="git-row-action hidden group-hover:inline-flex"
                  title="回退文件改动"
                  :disabled="discardingAgentPaths.has(agentMutationKey(file.scope, file.path))"
                  @click.stop="discardAgentFile(file)"
                >
                  <Loader2 v-if="discardingAgentPaths.has(agentMutationKey(file.scope, file.path))" class="h-3.5 w-3.5 animate-spin" :stroke-width="1.5" />
                  <Undo2 v-else class="h-3.5 w-3.5" :stroke-width="1.5" />
                </button>
                <button
                  v-if="canWriteAgentScope(file.scope)"
                  type="button"
                  class="git-row-action hidden group-hover:inline-flex"
                  title="暂存文件"
                  :disabled="updatingAgentIndexPaths.has(agentMutationKey(file.scope, file.path))"
                  @click.stop="stageAgentFile(file)"
                >
                  <Loader2 v-if="updatingAgentIndexPaths.has(agentMutationKey(file.scope, file.path))" class="h-3.5 w-3.5 animate-spin" :stroke-width="1.5" />
                  <Plus v-else class="h-3.5 w-3.5" :stroke-width="1.5" />
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
      <div class="git-section staged-section" :class="[{ 'border-t border-[#e4e4e7]': !(unstagedExpanded && stagedExpanded) }, { 'is-collapsed': !stagedExpanded }]">
        <div class="git-section-header" @click="stagedExpanded = !stagedExpanded">
          <ChevronDown v-if="stagedExpanded" class="h-3.5 w-3.5" :stroke-width="1.5" />
          <ChevronRight v-else class="h-3.5 w-3.5" :stroke-width="1.5" />
          <span class="git-section-title">STAGED (已暂存) ({{ activeStagedCount }})</span>
          <Button
            v-if="activeDiffScope === 'WORKSPACE'"
            size="icon"
            variant="ghost"
            class="git-bulk-action ml-auto"
            aria-label="全部回退到未暂存"
            title="全部回退到未暂存"
            :disabled="!props.canWrite || workspaceStaged.length === 0 || workspaceGitMutationPending"
            @click.stop="unstageAllWorkspaceChanges"
          >
            <Loader2 v-if="unstagingAllWorkspaceFiles" class="h-3.5 w-3.5 animate-spin" :stroke-width="1.5" />
            <Undo2 v-else class="h-3.5 w-3.5" :stroke-width="1.5" />
          </Button>
        </div>

        <div v-show="stagedExpanded" class="git-section-content">
          <!-- 2a. Application Workspace -->
          <div v-if="activeDiffScope === 'WORKSPACE'" class="git-sub-section">
            <div class="git-sub-content px-2 py-0.5 space-y-0.5">
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
                <Badge :tone="getBadgeTone(file.status)" class="mr-1 py-0 px-1 text-[9px] uppercase">{{ getStatusLabel(file.status) }}</Badge>
                <span class="git-file-name" :title="file.path">{{ getFileName(file.path) }}</span>
                <Badge v-if="isLocalOnlySpecPath(file.path)" tone="neutral" class="ml-1 py-0 px-1 text-[9px]">仅本地</Badge>
                <span v-if="file.additions" class="git-additions ml-1">+{{ file.additions }}</span>
                <span v-if="file.deletions" class="git-deletions ml-1">-{{ file.deletions }}</span>
                
                <button
                  v-if="props.canWrite && !hasWorkspaceConflicts"
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
                  v-if="props.canWrite"
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

          <!-- 2b. Agent/Skill scope -->
          <div v-else class="git-sub-section">
            <div class="git-sub-content px-2 py-0.5 space-y-0.5">
              <div v-if="activeWorkspaceAgentPublishPending" class="git-conflict-note">
                本地提交已完成，远端推送失败；文件会保留在此处，点击“重新推送”继续发布。
              </div>
              <div v-if="activeAgentStaged.length === 0" class="git-empty-text">无暂存文件</div>
              <div
                v-for="file in activeAgentStaged"
                :key="file.path"
                class="git-file-row group"
                :title="file.path"
                :aria-label="file.path"
                @click="handleOpenFileDiff(file.path, 'agent', file.scope, agentRunDiffFile(file))"
              >
                <Badge :tone="getBadgeTone(file.status)" class="mr-1 py-0 px-1 text-[9px] uppercase">{{ getStatusLabel(file.status) }}</Badge>
                <span class="git-file-name" :title="file.path">
                  {{ getFileName(file.path) }}
                </span>
                <Badge v-if="file.pendingPublish" tone="warning" class="ml-1 py-0 px-1 text-[9px]">待推送</Badge>
                
                <button
                  v-if="canWriteAgentScope(file.scope) && activeAgentConflicts.length === 0 && !file.pendingPublish"
                  type="button"
                  class="git-row-action hidden group-hover:inline-flex"
                  title="回退文件改动"
                  :disabled="discardingAgentPaths.has(agentMutationKey(file.scope, file.path))"
                  @click.stop="discardAgentFile(file)"
                >
                  <Loader2 v-if="discardingAgentPaths.has(agentMutationKey(file.scope, file.path))" class="h-3.5 w-3.5 animate-spin" :stroke-width="1.5" />
                  <Undo2 v-else class="h-3.5 w-3.5" :stroke-width="1.5" />
                </button>
                <button
                  v-if="canWriteAgentScope(file.scope) && !file.pendingPublish"
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
      <div class="git-commit-context">
        <span>提交当前作用域</span>
        <strong>{{ activeScopeItem.label }}</strong>
      </div>
      <textarea
        v-model="commitMessage"
        class="git-commit-textarea"
        placeholder="输入提交说明。首行为主题，空行后为详细描述..."
        :disabled="committing"
        rows="2"
      ></textarea>

      <div v-if="workspaceCommitHint" class="git-commit-hint" role="note">
        {{ workspaceCommitHint }}
      </div>

      <!-- Action buttons -->
      <div class="git-actions-row">
        <button
          type="button"
          class="git-action-btn btn-commit flex-1"
          :title="activeWorkspaceAgentPublishPending
            ? '应用 Agent 已完成本地提交，请重新推送'
            : (activeHasBlockingConflicts ? 'Git 存在未解决冲突，解决全部冲突后才能提交' : '提交已暂存变更')"
          :disabled="committing || activeHasBlockingConflicts || activeWorkspaceAgentPublishPending || !hasWritableStagedChanges || !commitMessage.trim()"
          @click="handleCommit(false)"
        >
          <FolderGit2 class="h-3.5 w-3.5 shrink-0" :stroke-width="1.5" />
          <span>提交</span>
        </button>
        <button
          v-if="hasPublishableStagedChanges"
          type="button"
          class="git-action-btn btn-push flex-1"
          :title="activeWorkspaceAgentPublishPending
            ? '重新推送已完成本地提交的应用 Agent 文件'
            : activeHasBlockingConflicts
            ? 'Git 存在未解决冲突，解决全部冲突后才能提交并推送'
            : (!hasPublishableStagedChanges ? '当前暂存内容仅允许本地提交' : '提交并推送可发布变更')"
          :disabled="committing || activeHasBlockingConflicts || !hasPublishableStagedChanges || !commitMessage.trim()"
          @click="handleCommit(true)"
        >
          <Upload class="h-3.5 w-3.5 shrink-0" :stroke-width="1.5" />
          <span>{{ activeWorkspaceAgentPublishPending ? '重新推送' : '提交并推送' }}</span>
        </button>
      </div>
    </div>

    <!-- Git Commit & Push Progress Dialog Overlay -->
    <div v-if="showCommitProgressDialog" class="ta-process-startup-backdrop" role="presentation">
      <section class="ta-process-startup-dialog" role="dialog" aria-modal="true" :aria-label="commitRequestedPush ? '提交并推送' : '提交'">
        <header class="ta-process-startup-header">
          <div>
            <h2 class="text-sm font-bold text-zinc-900 dark:text-zinc-100">{{ commitRequestedPush ? '提交并推送进度' : '提交进度' }}</h2>
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
              <span>校验并同步目标分支</span>
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
              <span>合并远程分支或投影文件</span>
              <small>{{ getCommitStepStatusText(3) }}</small>
            </div>
          </li>
          <li :class="['ta-process-startup-step', getCommitStepClass(4)]">
            <component :is="getCommitStepIcon(4)" :size="18" class="ta-process-startup-step-icon" />
            <div class="ta-process-startup-step-copy">
              <span>提交并推送目标分支</span>
              <small>{{ getCommitStepStatusText(4) }}</small>
            </div>
          </li>
          <li :class="['ta-process-startup-step', getCommitStepClass(5)]">
            <component :is="getCommitStepIcon(5)" :size="18" class="ta-process-startup-step-icon" />
            <div class="ta-process-startup-step-copy">
              <span>完成并广播更新</span>
              <small>{{ getCommitStepStatusText(5) }}</small>
            </div>
          </li>
        </ol>

        <!-- Command Log Console -->
        <div class="px-4 py-3 border-t border-zinc-100 dark:border-zinc-800 bg-zinc-50/50 dark:bg-zinc-950/20">
          <div class="text-xs font-semibold text-zinc-500 mb-2 uppercase tracking-wider">当前步骤执行的 Git 命令</div>
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
        <div v-else-if="progressMessage && !committing" class="mx-4 my-3 rounded border border-green-200 bg-green-50 px-3 py-2 text-left text-xs text-green-700">
          {{ progressMessage }}
        </div>
        <div v-if="commitResultSummary && !committing && !errorMessage" class="git-result-summary mx-4 my-3" aria-label="本轮累计结果">
          <strong>本轮累计结果</strong>
          <div class="git-result-summary-items">
            <span>本地提交 <b>{{ commitResultSummary.committedFiles }}</b> 个文件</span>
            <span v-if="commitResultSummary.hadRemotePush">远端推送 <b>{{ commitResultSummary.pushedFiles }}</b> 个文件</span>
            <span v-if="commitResultSummary.localOnlySpecFiles > 0">仅本地 <b>{{ commitResultSummary.localOnlySpecFiles }}</b> 个 spec 文件</span>
          </div>
          <p class="git-result-summary-note">连续处理多个 Tab 时自动累计，全部差异清空后结束本轮。</p>
          <div class="git-result-scope-list">
            <div v-for="scope in commitResultSummary.scopes" :key="scope.scope" class="git-result-scope-row">
              <strong>{{ scope.label }}</strong>
              <span>提交 {{ scope.committedFiles }}</span>
              <span v-if="scope.hadRemotePush">远端 {{ scope.pushedFiles }}</span>
              <span v-if="scope.localOnlySpecFiles > 0">仅本地 spec {{ scope.localOnlySpecFiles }}</span>
            </div>
          </div>
        </div>

        <footer class="ta-process-startup-footer">
          <span>{{ activeScopeItem.label }} · {{ activeScopeMeta }}</span>
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

.git-scope-switcher {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 4px;
  padding: 7px 8px 5px;
  border-bottom: 1px solid #e4e4e7;
  background: #fafafa;
}

.git-scope-tab {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  gap: 4px;
  height: 28px;
  padding: 0 7px;
  border: 1px solid transparent;
  border-radius: 5px;
  background: transparent;
  color: #71717a;
  cursor: pointer;
  font-size: 10px;
  text-align: left;
  transition: background-color 0.12s ease, border-color 0.12s ease, color 0.12s ease;
}

.git-scope-tab:hover {
  background: #f4f4f5;
  color: #3f3f46;
}

.git-scope-tab.is-active {
  border-color: #c7d2fe;
  background: #eef2ff;
  color: #3730a3;
  font-weight: 600;
}

.git-scope-tab-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.git-scope-tab-count {
  flex: 0 0 auto;
  min-width: 16px;
  padding: 1px 4px;
  border-radius: 999px;
  background: #e4e4e7;
  color: #71717a;
  font-size: 9px;
  line-height: 14px;
  text-align: center;
}

.git-scope-tab.is-active .git-scope-tab-count {
  background: #c7d2fe;
  color: #3730a3;
}

.git-conflict-banner {
  display: flex;
  flex-direction: column;
  gap: 2px;
  background: #fffbeb;
  border: 1px solid #fef3c7;
  border-radius: 6px;
  padding: 3px 8px;
  margin-bottom: 4px;
}

:global(.dark) .git-conflict-banner {
  background: rgba(245, 158, 11, 0.05);
  border-color: rgba(245, 158, 11, 0.15);
}

.git-conflict-header {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 500;
  color: #9a3412;
}

:global(.dark) .git-conflict-header {
  color: #fbbf24;
}

.git-conflict-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.git-conflict-action-btn,
.git-conflict-banner :deep(.git-conflict-action-btn) {
  height: 18px !important;
  padding: 0 6px !important;
  border: 0 !important;
  background: transparent !important;
  font-size: 11px !important;
  font-weight: 500 !important;
  color: #d97706 !important;
  border-radius: 3px !important;
  cursor: pointer !important;
  box-shadow: none !important;
  transition: background-color 0.12s ease;
}

.git-conflict-action-btn:hover,
.git-conflict-banner :deep(.git-conflict-action-btn:hover) {
  background: #fef3c7 !important;
}

:global(.dark) .git-conflict-action-btn,
:global(.dark) .git-conflict-banner :deep(.git-conflict-action-btn) {
  color: #fbbf24 !important;
}

:global(.dark) .git-conflict-action-btn:hover,
:global(.dark) .git-conflict-banner :deep(.git-conflict-action-btn:hover) {
  background: rgba(251, 191, 36, 0.1) !important;
}

.git-conflict-action-btn.btn-abort,
.git-conflict-banner :deep(.git-conflict-action-btn.btn-abort) {
  color: #71717a !important;
  margin-left: auto;
}

.git-conflict-action-btn.btn-abort:hover,
.git-conflict-banner :deep(.git-conflict-action-btn.btn-abort:hover) {
  background: #e4e4e7 !important;
}

:global(.dark) .git-conflict-action-btn.btn-abort,
:global(.dark) .git-conflict-banner :deep(.git-conflict-action-btn.btn-abort) {
  color: #a1a1aa !important;
}

:global(.dark) .git-conflict-action-btn.btn-abort:hover,
:global(.dark) .git-conflict-banner :deep(.git-conflict-action-btn.btn-abort:hover) {
  background: rgba(255, 255, 255, 0.05) !important;
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

.git-section-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
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

.git-bulk-action {
  width: 20px;
  height: 20px;
  padding: 0;
  border-radius: 4px;
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

.git-commit-context {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: #71717a;
  font-size: 10px;
}

.git-commit-context strong {
  min-width: 0;
  overflow: hidden;
  color: #52525b;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.git-commit-hint {
  padding: 6px 8px;
  border: 1px solid #fde68a;
  border-radius: 4px;
  background: #fffbeb;
  color: #92400e;
  font-size: 10px;
  line-height: 1.4;
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

.git-result-summary {
  padding: 10px 12px;
  border: 1px solid #bbf7d0;
  border-radius: 8px;
  background: #f0fdf4;
  color: #166534;
  text-align: left;
}

.git-result-summary > strong {
  display: block;
  margin-bottom: 6px;
  font-size: 12px;
}

.git-result-summary-items {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.git-result-summary-items span {
  padding: 3px 7px;
  border-radius: 999px;
  background: #dcfce7;
  font-size: 11px;
}

.git-result-summary-note {
  margin: 7px 0 0;
  color: #4b7a5a;
  font-size: 10px;
  line-height: 1.4;
}

.git-result-scope-list {
  display: grid;
  gap: 4px;
  margin-top: 8px;
  padding-top: 7px;
  border-top: 1px solid #bbf7d0;
}

.git-result-scope-row {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  color: #3f6f4e;
  font-size: 10px;
}

.git-result-scope-row strong {
  min-width: 62px;
  color: #166534;
  font-size: 10px;
}

@keyframes ta-process-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
