<script setup lang="ts">
import { computed, ref, watch } from "vue";
import {
  AlertTriangle,
  Check,
  FilePlus2,
  FolderGit2,
  GitBranch,
  GitCompare,
  Globe2,
  Loader2,
  Plus,
  RefreshCw,
  Upload,
  MoreHorizontal
} from "lucide-vue-next";
import { createBackendApiClient } from "@test-agent/backend-api";
import { FileEntryCreateDialog, FileEntryDeleteDialog } from "@test-agent/file-explorer";
import { useWorkbenchStore } from "@test-agent/workbench-shell";
import { Button, Input } from "@test-agent/ui-kit";
import type {
  AgentConfigDiffFile,
  AgentConfigProgressEvent,
  AgentConfigStatus,
  AgentConfigWorktree,
  AgentConfigWorktreeOption,
  FileTreeEntry,
  PublicAgentRepositoryStatus,
  WorkspaceGitConflict,
  WorkspaceGitConflictResolution
} from "@test-agent/shared-types";
import { formatAgentConfigError } from "./agentConfigErrors";
import { agentFileInfo, isAgentFilePath, type AgentFileLoadRequest } from "./agentFileLoad";
import { notifyError, notifyInfo, notifySuccess } from "./notify";
import AgentConfigTreeNode from "./AgentConfigTreeNode.vue";

type Scope = "PUBLIC" | "WORKSPACE";
type AgentConfigMutation = {
  scope: Scope;
  paths: string[];
  deleted?: { path: string; type: "file" | "directory" };
  renamed?: { path: string; nextPath: string; type: "file" };
};

const props = defineProps<{
  baseUrl: string;
  workspaceId?: string;
  /** 公共配置 Git 写权限，仅超级管理员可用。 */
  canWrite: boolean;
  /** 应用级 Agent/Skill/Rules/Templates 写权限，仅应用管理员可用。 */
  canManageWorkspaceConfig?: boolean;
  hideHeader?: boolean;
  hideGitOps?: boolean;
  activePath?: string;
  /** 当前正在手动重载的 Agent 运行态作用域；由工作台统一防止重复操作。 */
  personalRuntimeReloading?: Scope | null;
  /** 运行中任务不允许 dispose，避免释放正在使用的 workspace 实例。 */
  runtimeBusy?: boolean;
}>();

const emit = defineEmits<{
  openFile: [payload: AgentFileLoadRequest];
  "personal-runtime-reload": [payload: {
    scope: Scope;
    worktreeId?: string;
    linuxServerId?: string;
    workspaceId?: string;
  }];
  "files-mutated": [payload: AgentConfigMutation];
}>();

const workbench = useWorkbenchStore();
const api = createBackendApiClient({ baseUrl: props.baseUrl });
const workspaceCanWrite = computed(() => props.canManageWorkspaceConfig ?? props.canWrite);

const status = ref<{ PUBLIC?: AgentConfigStatus; WORKSPACE?: AgentConfigStatus }>({});
const entriesByScope = ref<Record<Scope, Record<string, FileTreeEntry[]>>>({ PUBLIC: {}, WORKSPACE: {} });
const rootExpanded = ref<Set<Scope>>(new Set(["PUBLIC"]));
const expandedByScope = ref<Record<Scope, Set<string>>>({ PUBLIC: new Set(), WORKSPACE: new Set() });
const loadingByScope = ref<Record<Scope, Set<string>>>({ PUBLIC: new Set(), WORKSPACE: new Set() });
const directoryGeneration = ref<Record<Scope, number>>({ PUBLIC: 0, WORKSPACE: 0 });
const errorMessage = ref("");
const activeScope = ref<Scope | null>(null);
const activeFileByScope = ref<Record<Scope, string | null>>({ PUBLIC: null, WORKSPACE: null });
const diffFiles = ref<AgentConfigDiffFile[]>([]);
const createEntryDialog = ref<InstanceType<typeof FileEntryCreateDialog> | null>(null);
const createEntryScope = ref<Scope>("WORKSPACE");
const deleteEntryDialog = ref<InstanceType<typeof FileEntryDeleteDialog> | null>(null);
const deleteEntryScope = ref<Scope>("WORKSPACE");
const publicConflictPathHints = ref<string[]>([]);
const selectedDiffPath = ref("");
const commitMessage = ref("");
const progressEvents = ref<AgentConfigProgressEvent[]>([]);
const REQUEST_TIMEOUT_MS = 15000;

const publicWorktree = computed<AgentConfigWorktree | null>({
  get: () => workbench.publicWorktree,
  set: (val) => { workbench.publicWorktree = val; }
});
const publicConfigLinuxServerId = computed<string | null>({
  get: () => workbench.publicConfigLinuxServerId,
  set: (val) => { workbench.publicConfigLinuxServerId = val; }
});

const busy = ref(false);
const selectedDiff = computed(() => diffFiles.value.find((file) => file.path === selectedDiffPath.value) ?? diffFiles.value[0]);
const publicConflictFiles = computed<AgentConfigDiffFile[]>(() => {
  const byPath = new Map<string, AgentConfigDiffFile>();
  if (activeScope.value === "PUBLIC") {
    diffFiles.value.filter((file) => isConflictFile(file)).forEach((file) => byPath.set(file.path, file));
  }
  publicConflictPathHints.value.forEach((path) => {
    if (!byPath.has(path)) {
      byPath.set(path, { path, status: "conflict", staged: false, patch: "" });
    }
  });
  return Array.from(byPath.values());
});
const publicConflictPathSet = computed(() => new Set(publicConflictFiles.value.map((file) => file.path)));
const firstPublicConflictFile = computed(() => publicConflictFiles.value[0] ?? null);
const activeAgentFile = computed(() => {
  if (props.activePath) {
    const editorFile = activeAgentFileFromEditorPath(props.activePath);
    return editorFile && isCurrentAgentFileContext(editorFile) ? editorFile : null;
  }
  return activeAgentFileFromLocalSelection();
});

let refreshAllToken = 0;
const refreshing = ref(false);
const panelBusy = computed(() => busy.value || refreshing.value);
void refreshAll(false);

watch(
  () => props.workspaceId,
  () => {
    invalidateDirectoryCache("WORKSPACE", true);
    void refreshStatus();
    if (rootExpanded.value.has("WORKSPACE")) {
      void loadDirectory("WORKSPACE", "");
    }
  }
);

async function refreshAll(notifySkippedFile = true) {
  const token = ++refreshAllToken;
  refreshing.value = true;
  errorMessage.value = "";
  const expandedSnapshot: Record<Scope, Set<string>> = {
    PUBLIC: new Set(expandedByScope.value.PUBLIC),
    WORKSPACE: new Set(expandedByScope.value.WORKSPACE)
  };
  // 刷新代次会让旧请求结果失效，避免切换 worktree 或磁盘内容变化后迟到响应重新写回旧树。
  invalidateDirectoryCache("PUBLIC");
  invalidateDirectoryCache("WORKSPACE");
  try {
    await refreshStatus();
    if (token !== refreshAllToken) return;
    const tasks: Promise<void>[] = [];
    if (status.value.PUBLIC?.enabled !== false) tasks.push(loadDirectory("PUBLIC", "", true));
    if (props.workspaceId) tasks.push(loadDirectory("WORKSPACE", "", true));
    await Promise.allSettled(tasks);
    if (token !== refreshAllToken) return;
    await Promise.all([
      reloadExpandedDirectories("PUBLIC", expandedSnapshot.PUBLIC),
      reloadExpandedDirectories("WORKSPACE", expandedSnapshot.WORKSPACE)
    ]);
    if (token !== refreshAllToken) return;
    await refreshActiveEditorFile(undefined, notifySkippedFile);
  } finally {
    if (token === refreshAllToken) {
      refreshing.value = false;
    }
  }
}

async function refreshStatus() {
  const next: { PUBLIC?: AgentConfigStatus; WORKSPACE?: AgentConfigStatus } = {};
  const publicStatusPromise = withTimeout(api.getPublicAgentConfigStatus(), "加载公共 Agent 状态超时");
  const workspaceStatusPromise = props.workspaceId
    ? withTimeout(api.getWorkspaceAgentConfigStatus(props.workspaceId), "加载应用 Agent 状态超时")
    : Promise.resolve<AgentConfigStatus | undefined>(undefined);
  const [publicResult, workspaceResult] = await Promise.allSettled([publicStatusPromise, workspaceStatusPromise]);
  if (publicResult.status === "fulfilled") {
    next.PUBLIC = publicResult.value;
    if (publicResult.value.enabled !== false) {
      try {
        publicRepositories.value = await api.listPublicAgentRepositories();
        const nextServer = preferredPublicServer(publicRepositories.value);
        if (nextServer) {
          selectedPublicLinuxServerId.value = nextServer;
          publicConfigLinuxServerId.value = nextServer;
          if (props.canWrite) {
            await ensureCurrentUserPublicWorktree(nextServer, publicResult.value.currentBranch);
          }
        }
      } catch (error) {
        errorMessage.value = formatAgentConfigError(error, "加载公共配置仓库列表失败");
      }
    }
  } else {
    errorMessage.value = formatAgentConfigError(publicResult.reason, "加载公共 Agent 状态失败");
  }
  if (workspaceResult.status === "fulfilled") {
    next.WORKSPACE = workspaceResult.value;
  } else {
    errorMessage.value = formatAgentConfigError(workspaceResult.reason, "加载应用 Agent 状态失败");
  }
  status.value = next;
}

/**
 * 公共区与应用区保持相同的个人隔离语义：管理员进入后自动挂载自己在当前服务器上的长期 worktree。
 */
async function ensureCurrentUserPublicWorktree(linuxServerId: string, fallbackBranch?: string | null) {
  if (
    publicWorktree.value?.linuxServerId === linuxServerId
    && publicWorktree.value.worktreeId
  ) {
    return;
  }
  const existing = await api.listPublicAgentWorktrees(linuxServerId);
  if (existing[0]) {
    publicWorktree.value = { ...existing[0] };
    return;
  }
  const repository = publicRepositories.value.find((item) => item.linuxServerId === linuxServerId);
  const branch = repository?.currentBranch?.trim() || fallbackBranch?.trim() || "main";
  publicWorktree.value = await api.createPublicAgentWorktree({
    baseName: "public-personal",
    branch,
    linuxServerId,
    operationId: newOperationId()
  });
}

function worktreeId(scope: Scope) {
  // 应用级配置直接使用当前版本个人 workspace 的 Git 根，不再挂载独立 Agent worktree。
  return scope === "PUBLIC" ? publicWorktree.value?.worktreeId : undefined;
}

function activeAgentFileFromLocalSelection() {
  if (!activeScope.value) return null;
  const path = activeFileByScope.value[activeScope.value];
  return path ? { scope: activeScope.value, path } : null;
}

function activeAgentFileFromEditorPath(path?: string) {
  return path && isAgentFilePath(path) ? agentFileInfo(path) : null;
}

function isCurrentAgentFileContext(file: {
  scope: Scope;
  workspaceId?: string;
  worktreeId?: string;
  linuxServerId?: string;
}) {
  const currentWorktreeId = worktreeId(file.scope) ?? "";
  if ((file.worktreeId ?? "") !== currentWorktreeId) {
    return false;
  }
  if (file.scope === "PUBLIC") {
    const currentLinuxServerId = publicWorktree.value?.linuxServerId ?? publicConfigLinuxServerId.value ?? "";
    return (file.linuxServerId ?? "") === currentLinuxServerId;
  }
  return Boolean(file.workspaceId) && file.workspaceId === props.workspaceId;
}

function isRootActive(scope: Scope) {
  if (props.activePath) return false;
  return activeScope.value === scope && activeFileByScope.value[scope] === null;
}

function visibleEntries(scope: Scope, path: string) {
  const entries = entriesByScope.value[scope][path] ?? [];
  if (canWriteScope(scope) || path !== "") {
    return entries;
  }
  // 普通用户只看 opencode 有效的 agents/skills 根目录，隐藏配置仓库工程杂项。
  return entries.filter((entry) => entry.path === "agents" || entry.path === "skills");
}

function canWriteScope(scope: Scope) {
  return scope === "PUBLIC" ? props.canWrite : workspaceCanWrite.value;
}

/** Agent 配置树复用工作空间的新建面板，作用域只负责补齐文件路由上下文。 */
function openCreateEntryDialog(scope: Scope, directory: string) {
  if (!canWriteScope(scope) || busy.value) return;
  if (scope === "PUBLIC" && (!publicWorktree.value?.worktreeId || status.value.PUBLIC?.enabled === false)) return;
  if (scope === "WORKSPACE" && !props.workspaceId) return;
  createEntryScope.value = scope;
  createEntryDialog.value?.open(directory);
}

function agentEntryPath(directory: string, name: string) {
  return directory ? `${directory.replace(/\/+$/, "")}/${name}` : name;
}

function isWorkspaceAgentDiffPath(path: string) {
  const normalized = path.replace(/^\/+/, "");
  return normalized === "opencode.jsonc"
    || normalized.startsWith("agents/")
    || normalized.startsWith("skills/");
}

function canCreateInDirectory(scope: Scope, path: string) {
  return scope === "PUBLIC"
    || path === "agents"
    || path.startsWith("agents/")
    || path === "skills"
    || path.startsWith("skills/");
}

function canDeleteEntry(scope: Scope, path: string) {
  if (scope === "PUBLIC") return true;
  const normalized = path.replace(/^\/+|\/+$/g, "");
  return normalized === "opencode.jsonc"
    || normalized === "agents"
    || normalized.startsWith("agents/")
    || normalized === "skills"
    || normalized.startsWith("skills/");
}

function canRenameEntry(scope: Scope, path: string) {
  const normalized = path.replace(/^\/+|\/+$/g, "");
  return scope === "WORKSPACE"
    && (normalized.startsWith("agents/") || normalized.startsWith("skills/"));
}

/** 文件和目录沿用工作空间删除确认面板，作用域仅负责补齐 Agent 文件路由。 */
function openDeleteEntryDialog(scope: Scope, entry: FileTreeEntry) {
  if (!canWriteScope(scope) || busy.value || !canDeleteEntry(scope, entry.path)) return;
  if (scope === "PUBLIC" && (!publicWorktree.value?.worktreeId || status.value.PUBLIC?.enabled === false)) return;
  if (scope === "WORKSPACE" && !props.workspaceId) return;
  deleteEntryScope.value = scope;
  deleteEntryDialog.value?.open({ path: entry.path, type: entry.type });
}

/**
 * 新文件沿用 Agent 配置 write RPC；Git 不记录空目录，因此文件夹用 `.gitkeep` 形成可提交变更。
 * 创建完成后只刷新目标目录，并把真实 Git 路径交给既有 revision/diff 刷新链路。
 */
async function createAgentEntry(directory: string, name: string, type: "file" | "directory") {
  const scope = createEntryScope.value;
  if (!canWriteScope(scope) || busy.value) return;
  if ((entriesByScope.value[scope][directory] ?? []).some((entry) => entry.name === name)) {
    notifyError(`创建${type === "file" ? "文件" : "文件夹"}失败`, "同名条目已存在");
    return;
  }
  const fullPath = agentEntryPath(directory, name);
  const writtenPath = type === "directory" ? `${fullPath}/.gitkeep` : fullPath;
  if (scope === "WORKSPACE" && !isWorkspaceAgentDiffPath(writtenPath)) {
    notifyError("创建应用 Agent 配置失败", "请在 agents 或 skills 目录内新建；根目录仅支持 opencode.jsonc、agents 和 skills");
    return;
  }
  busy.value = true;
  errorMessage.value = "";
  try {
    if (scope === "PUBLIC") {
      const linuxServerId = await publicFileLinuxServerId();
      await api.writePublicAgentFile(writtenPath, "", worktreeId(scope), linuxServerId);
    } else {
      await api.writeWorkspaceAgentFile(props.workspaceId!, writtenPath, "", worktreeId(scope));
    }
    await loadDirectory(scope, directory, true);
    emit("files-mutated", { scope, paths: [writtenPath] });
    if (type === "file") {
      await openFile(scope, fullPath);
    }
    notifySuccess(type === "file" ? "Agent 文件已创建" : "Agent 文件夹已创建", fullPath);
  } catch (error) {
    errorMessage.value = formatAgentConfigError(error, `创建 Agent ${type === "file" ? "文件" : "文件夹"}失败`);
    notifyError(`创建 Agent ${type === "file" ? "文件" : "文件夹"}失败`, errorMessage.value);
  } finally {
    busy.value = false;
  }
}

/** 应用 Agent 文件沿用普通文件树的双击行内改名交互，落盘仍走专用 Agent 配置 RPC。 */
async function renameAgentEntry(path: string, name: string) {
  const scope: Scope = "WORKSPACE";
  if (!props.workspaceId || !canWriteScope(scope) || busy.value || !canRenameEntry(scope, path)) return;
  const parent = parentDirectory(path);
  const nextPath = agentEntryPath(parent, name);
  busy.value = true;
  errorMessage.value = "";
  try {
    await api.renameWorkspaceAgentFile(props.workspaceId, path, name, worktreeId(scope));
    if (activeFileByScope.value.WORKSPACE === path) {
      activeFileByScope.value = { ...activeFileByScope.value, WORKSPACE: nextPath };
    }
    await loadDirectory(scope, parent, true);
    emit("files-mutated", {
      scope,
      paths: [path, nextPath],
      renamed: { path, nextPath, type: "file" }
    });
    notifySuccess("应用 Agent 文件已重命名", nextPath);
  } catch (error) {
    errorMessage.value = formatAgentConfigError(error, "重命名应用 Agent 文件失败");
    notifyError("重命名应用 Agent 文件失败", errorMessage.value);
  } finally {
    busy.value = false;
  }
}

/**
 * Agent 文件与目录删除复用工作空间的递归删除语义；成功后清理子树缓存、关闭相关 tab 并刷新 Git Diff。
 */
async function deleteAgentEntry(path: string, type: "file" | "directory") {
  const scope = deleteEntryScope.value;
  if (!canWriteScope(scope) || busy.value || !canDeleteEntry(scope, path)) return;
  busy.value = true;
  errorMessage.value = "";
  try {
    if (scope === "PUBLIC") {
      const linuxServerId = await publicFileLinuxServerId();
      await api.deletePublicAgentFile(path, worktreeId(scope), linuxServerId);
    } else {
      await api.deleteWorkspaceAgentFile(props.workspaceId!, path, worktreeId(scope));
    }
    const parent = parentDirectory(path);
    const normalizedPath = path.replace(/\\/g, "/");
    if (type === "directory") {
      const nextEntries = { ...entriesByScope.value[scope] };
      for (const directory of Object.keys(nextEntries)) {
        if (directory === normalizedPath || directory.startsWith(`${normalizedPath}/`)) {
          delete nextEntries[directory];
        }
      }
      entriesByScope.value = { ...entriesByScope.value, [scope]: nextEntries };
      expandedByScope.value = {
        ...expandedByScope.value,
        [scope]: new Set([...expandedByScope.value[scope]].filter(
          (directory) => directory !== normalizedPath && !directory.startsWith(`${normalizedPath}/`)
        ))
      };
    }
    const activePath = activeFileByScope.value[scope];
    if (activePath === normalizedPath || (type === "directory" && activePath?.startsWith(`${normalizedPath}/`))) {
      activeFileByScope.value = { ...activeFileByScope.value, [scope]: null };
    }
    await loadDirectory(scope, parent, true);
    emit("files-mutated", { scope, paths: [path], deleted: { path, type } });
    notifySuccess(type === "file" ? "Agent 文件已删除" : "Agent 文件夹已删除", path);
  } catch (error) {
    errorMessage.value = formatAgentConfigError(error, `删除 Agent ${type === "file" ? "文件" : "文件夹"}失败`);
    notifyError(`删除 Agent ${type === "file" ? "文件" : "文件夹"}失败`, errorMessage.value);
  } finally {
    busy.value = false;
  }
}

async function loadDirectory(scope: Scope, path: string, force = false) {
  if (scope === "WORKSPACE" && !props.workspaceId) return;
  if (scope === "PUBLIC" && status.value.PUBLIC?.enabled === false) return;
  if (!force && (entriesByScope.value[scope][path] !== undefined || loadingByScope.value[scope].has(path))) return;
  const generation = directoryGeneration.value[scope];
  loadingByScope.value = { ...loadingByScope.value, [scope]: new Set([...loadingByScope.value[scope], path]) };
  errorMessage.value = "";
  try {
    const entries = await withTimeout((async () => {
      const linuxServerId = scope === "PUBLIC" ? await publicFileLinuxServerId() : undefined;
      return scope === "PUBLIC"
        ? api.listPublicAgentFiles(path, worktreeId(scope), linuxServerId)
        : api.listWorkspaceAgentFiles(props.workspaceId!, path, worktreeId(scope));
    })(), "加载 Agent 文件超时");
    if (directoryGeneration.value[scope] !== generation) return;
    entriesByScope.value = {
      ...entriesByScope.value,
      [scope]: { ...entriesByScope.value[scope], [path]: entries }
    };
  } catch (error) {
    if (directoryGeneration.value[scope] !== generation) return;
    errorMessage.value = formatAgentConfigError(error, "加载 Agent 文件失败");
    const nextExpanded = new Set(expandedByScope.value[scope]);
    nextExpanded.delete(path);
    expandedByScope.value = { ...expandedByScope.value, [scope]: nextExpanded };
  } finally {
    if (directoryGeneration.value[scope] !== generation) return;
    const next = new Set(loadingByScope.value[scope]);
    next.delete(path);
    loadingByScope.value = { ...loadingByScope.value, [scope]: next };
  }
}

function invalidateDirectoryCache(scope: Scope, clearExpanded = false) {
  directoryGeneration.value = {
    ...directoryGeneration.value,
    [scope]: directoryGeneration.value[scope] + 1
  };
  entriesByScope.value = { ...entriesByScope.value, [scope]: {} };
  loadingByScope.value = { ...loadingByScope.value, [scope]: new Set() };
  if (clearExpanded) {
    expandedByScope.value = { ...expandedByScope.value, [scope]: new Set() };
  }
}

/**
 * 按目录深度恢复刷新前已经展开的节点；父目录已不存在时直接折叠，避免把旧 `.bak` 等条目重新写回树。
 */
async function reloadExpandedDirectories(scope: Scope, expanded: Set<string>) {
  if (scope === "WORKSPACE" && !props.workspaceId) return;
  if (scope === "PUBLIC" && status.value.PUBLIC?.enabled === false) return;
  const paths = [...expanded].sort((left, right) => pathDepth(left) - pathDepth(right));
  for (const path of paths) {
    const parent = parentDirectory(path);
    const stillExists = (entriesByScope.value[scope][parent] ?? [])
      .some((entry) => entry.type === "directory" && entry.path === path);
    if (!stillExists) {
      const next = new Set(expandedByScope.value[scope]);
      next.delete(path);
      expandedByScope.value = { ...expandedByScope.value, [scope]: next };
      continue;
    }
    await loadDirectory(scope, path, true);
  }
}

function parentDirectory(path: string) {
  const separator = path.lastIndexOf("/");
  return separator < 0 ? "" : path.slice(0, separator);
}

function pathDepth(path: string) {
  return path.split("/").filter(Boolean).length;
}

function withTimeout<T>(promise: Promise<T>, message: string, timeoutMs = REQUEST_TIMEOUT_MS): Promise<T> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  const timeout = new Promise<never>((_, reject) => {
    timer = setTimeout(() => reject(new Error(message)), timeoutMs);
  });
  return Promise.race([promise, timeout]).finally(() => {
    if (timer) clearTimeout(timer);
  });
}

function toggleRoot(scope: Scope) {
  activeScope.value = scope;
  activeFileByScope.value = { ...activeFileByScope.value, [scope]: null };
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
  activeFileByScope.value = { ...activeFileByScope.value, [scope]: null };
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
    activeFileByScope.value = { ...activeFileByScope.value, [scope]: path };
    emit("openFile", {
      scope,
      path,
      absolutePath: agentAbsolutePath(scope, path),
      workspaceId: scope === "WORKSPACE" ? props.workspaceId : undefined,
      worktreeId: worktreeId(scope),
      linuxServerId,
      readonly: !canWriteScope(scope),
      activate: true,
      closeOnNotFound: false
    });
  } catch (error) {
    errorMessage.value = formatAgentConfigError(error, "读取 Agent 文件失败");
  }
}

/**
 * 刷新磁盘内容时只覆盖没有未保存修改的活动 Agent 文件；文件已删除则关闭旧标签，避免继续显示不存在的 `.bak`。
 */
async function refreshActiveEditorFile(scope?: Scope, notifySkippedFile = true) {
  const tabPath = props.activePath;
  const activeFile = activeAgentFileFromEditorPath(tabPath);
  if (!tabPath || !activeFile || (scope && activeFile.scope !== scope) || !isCurrentAgentFileContext(activeFile)) return;
  const tab = workbench.tabs.find((item) => item.path === tabPath);
  if (tab && !tab.livePreview && tab.content !== tab.savedContent) {
    if (notifySkippedFile) {
      notifyInfo("未覆盖未保存的 Agent 文件", activeFile.path);
    }
    return;
  }
  try {
    const linuxServerId = activeFile.scope === "PUBLIC" ? await publicFileLinuxServerId() : undefined;
    activeFileByScope.value = { ...activeFileByScope.value, [activeFile.scope]: activeFile.path };
    emit("openFile", {
      scope: activeFile.scope,
      path: activeFile.path,
      absolutePath: agentAbsolutePath(activeFile.scope, activeFile.path),
      workspaceId: activeFile.scope === "WORKSPACE" ? props.workspaceId : undefined,
      worktreeId: worktreeId(activeFile.scope),
      linuxServerId,
      readonly: !canWriteScope(activeFile.scope),
      activate: false,
      closeOnNotFound: true
    });
  } catch (error) {
    errorMessage.value = formatAgentConfigError(error, "刷新 Agent 文件失败");
  }
}

async function refreshScope(scope: Scope) {
  const expandedSnapshot = new Set(expandedByScope.value[scope]);
  invalidateDirectoryCache(scope);
  await refreshStatus();
  if (scope !== "PUBLIC" || status.value.PUBLIC?.enabled !== false) {
    await loadDirectory(scope, "", true);
    await reloadExpandedDirectories(scope, expandedSnapshot);
    await refreshActiveEditorFile(scope);
  }
}

const personalRuntimeReloadDisabled = computed(() =>
  busy.value
  || props.runtimeBusy === true
  || (props.personalRuntimeReloading !== null && props.personalRuntimeReloading !== undefined)
);

/** 从左侧 Agent 区域请求当前用户的 Agent 配置运行态更新。 */
function requestPersonalRuntimeReload(scope: Scope) {
  if (personalRuntimeReloadDisabled.value) return;
  if (scope === "PUBLIC") {
    const currentWorktree = publicWorktree.value;
    if (!currentWorktree?.worktreeId || !currentWorktree.linuxServerId) {
      errorMessage.value = "请先创建或切换到当前用户的公共个人 worktree";
      return;
    }
    emit("personal-runtime-reload", {
      scope,
      worktreeId: currentWorktree.worktreeId,
      linuxServerId: currentWorktree.linuxServerId
    });
    return;
  }
  if (!props.workspaceId) return;
  emit("personal-runtime-reload", { scope, workspaceId: props.workspaceId });
}

// “更新公共配置”操作的正在进行状态标记，用以控制按钮禁用和加载动效
const updatingPublicConfig = ref(false);
// 更新公共配置弹窗的控制状态
const showUpdatePublicConfigModal = ref(false);
const updatePublicConfigBranch = ref("main");
const updatePublicConfigBranches = ref<string[]>([]);
const loadingUpdatePublicConfigBranches = ref(false);
const updatePublicConfigError = ref("");
const updatePublicDiscardLocalChanges = ref(false);
const updatePublicConfigCommitMessage = ref("");
const showUpdatePublicProgressDialog = ref(false);
const updatePublicProgressStep = ref(1);
const updatePublicProgressCommands = ref<string[]>([]);
const updatePublicProgressError = ref("");
const updatePublicProgressBranch = ref("");
const activePublicConflict = ref<WorkspaceGitConflict | null>(null);
const publicConflictResolving = ref(false);
const publicConflictLoading = ref(false);

function resetUpdatePublicProgress(branch: string) {
  showUpdatePublicProgressDialog.value = true;
  updatePublicProgressStep.value = 1;
  updatePublicProgressCommands.value = [];
  updatePublicProgressError.value = "";
  updatePublicProgressBranch.value = branch;
}

function publicUpdateStepNumber(step?: string | null) {
  switch (step) {
    case "STAGING":
    case "COMMITTING":
      return 2;
    case "MERGING":
      return 3;
    case "PUSHING":
      return 4;
    case "BROADCASTING":
      return 5;
    case "COMPLETED":
      return 6;
    case "VALIDATING":
    case "PREPARING_REPOSITORY":
    default:
      return 1;
  }
}

function publicUpdateStepClass(step: number) {
  if (updatePublicProgressError.value && updatePublicProgressStep.value === step) return "failed";
  if (updatePublicProgressStep.value > step || updatePublicProgressStep.value >= 6) return "completed";
  if (updatePublicProgressStep.value === step && updatingPublicConfig.value) return "running";
  return "pending";
}

function publicUpdateStepIcon(step: number) {
  const cls = publicUpdateStepClass(step);
  if (cls === "completed") return Check;
  if (cls === "failed") return AlertTriangle;
  if (cls === "running") return Loader2;
  return GitBranch;
}

function publicUpdateStepStatusText(step: number) {
  const cls = publicUpdateStepClass(step);
  if (cls === "completed") return "已完成";
  if (cls === "failed") return "失败";
  if (cls === "running") return "进行中";
  return "等待中";
}

function applyUpdatePublicProgressEvent(event: AgentConfigProgressEvent) {
  const step = event.currentStep ?? event.operation?.currentStep;
  if (step) {
    updatePublicProgressStep.value = publicUpdateStepNumber(step);
  }
  if (event.command?.trim()) {
    updatePublicProgressCommands.value = [event.command.trim()];
  } else if (event.type === "step" && event.status === "RUNNING") {
    updatePublicProgressCommands.value = [];
  }
  if (event.type === "failed" || event.status === "FAILED") {
    updatePublicProgressError.value = event.errorMessage || event.operation?.errorMessage || "公共 Agent 提交并推送失败";
  }
  if (event.type === "completed" || event.status === "SUCCEEDED") {
    updatePublicProgressStep.value = 6;
  }
}

/**
 * 触发更新公共配置流程，初始化弹窗状态，加载远端分支列表并打开弹窗
 */
async function updatePublicConfig() {
  if (!props.canWrite) return;
  showUpdatePublicConfigModal.value = true;
  loadingUpdatePublicConfigBranches.value = true;
  updatePublicConfigError.value = "";
  updatePublicDiscardLocalChanges.value = false;
  updatePublicConfigCommitMessage.value = "";
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
  updatePublicDiscardLocalChanges.value = false;
  updatePublicConfigCommitMessage.value = "";
}

// 是否可以提交"更新+提交+推送"：分支已选、提交信息非空、未在加载、未在忙
const canSubmitUpdatePublicConfig = computed(() =>
  !loadingUpdatePublicConfigBranches.value
  && !busy.value
  && !updatingPublicConfig.value
  && !!updatePublicConfigBranch.value
  && updatePublicConfigCommitMessage.value.trim().length > 0
);

/**
 * 提交更新公共配置请求：调用后端复合接口 stage → commit → push。
 * 工作区有本地修改时默认提交这些修改；只有用户显式勾选时才放弃已跟踪修改。
 */
async function submitUpdatePublicConfig() {
  const branch = updatePublicConfigBranch.value;
  const message = updatePublicConfigCommitMessage.value.trim();
  if (!branch || !message) return;
  const discardLocalChanges = updatePublicDiscardLocalChanges.value;
  const messageSnapshot = message;

  closeUpdatePublicConfigModal();
  resetUpdatePublicProgress(branch);

  updatingPublicConfig.value = true;
  try {
    const operationId = newOperationId();
    const result = await runOperation(
      () => api.updatePublicAgentConfigAndPush({
        branch,
        commitMessage: messageSnapshot,
        operationId,
        discardLocalChanges
      }),
      "公共 Agent 更新并推送",
      operationId,
      applyUpdatePublicProgressEvent
    );
    if (result) {
      updatePublicProgressStep.value = 6;
      const newHash = (result as { commitHash?: string }).commitHash;
      notifySuccess("公共 Agent 已提交并推送", `分支 ${branch} 最新提交 ${newHash ?? "未知"}`);
    } else if (errorMessage.value) {
      // runOperation 已经把错误写入 errorMessage，这里再以气泡形式通知一次。
      updatePublicProgressError.value = errorMessage.value;
      notifyError("公共 Agent 提交并推送失败", errorMessage.value);
      activeScope.value = "PUBLIC";
      await loadDiff("PUBLIC");
    }
    publicRepositories.value = await api.listPublicAgentRepositories();
    await refreshScope("PUBLIC");
  } finally {
    updatingPublicConfig.value = false;
  }
}

function isConflictFile(file: { status?: string; rawStatus?: string }) {
  const rawStatus = (file.rawStatus ?? "").trim();
  return file.status === "conflict" || ["DD", "AU", "UD", "UA", "DU", "AA", "UU"].includes(rawStatus);
}

function publicConflictLinuxServerId() {
  return publicWorktree.value?.linuxServerId
    ?? publicConfigLinuxServerId.value
    ?? selectedPublicLinuxServerId.value
    ?? null;
}

async function openPublicConflict(path: string) {
  if (publicConflictLoading.value) return;
  publicConflictLoading.value = true;
  errorMessage.value = "";
  try {
    activePublicConflict.value = await api.getPublicAgentGitConflict(path, publicWorktree.value?.worktreeId, publicConflictLinuxServerId());
  } catch (error) {
    errorMessage.value = formatAgentConfigError(error, "读取公共 Agent Git 冲突失败");
  } finally {
    publicConflictLoading.value = false;
  }
}

async function loadPublicConflictFiles() {
  if (!props.canWrite || status.value.PUBLIC?.enabled === false) return;
  try {
    const response = await api.getPublicAgentGitConflictFiles(publicWorktree.value?.worktreeId, publicConflictLinuxServerId());
    publicConflictPathHints.value = response.files;
  } catch (error) {
    errorMessage.value = formatAgentConfigError(error, "加载公共 Agent 冲突文件失败");
  }
}

function openFirstPublicConflict() {
  const first = firstPublicConflictFile.value;
  if (!first) return;
  void openPublicConflict(first.path);
}

async function resolvePublicConflict(payload: { resolution: WorkspaceGitConflictResolution; content?: string | null }) {
  if (!activePublicConflict.value || publicConflictResolving.value) return;
  publicConflictResolving.value = true;
  errorMessage.value = "";
  try {
    const path = activePublicConflict.value.path;
    await api.resolvePublicAgentGitConflict({
      path,
      ...payload,
      worktreeId: publicWorktree.value?.worktreeId,
      linuxServerId: publicConflictLinuxServerId()
    });
    activePublicConflict.value = null;
    await loadDiff("PUBLIC");
    await loadPublicConflictFiles();
  } catch (error) {
    errorMessage.value = formatAgentConfigError(error, "解决公共 Agent Git 冲突失败");
  } finally {
    publicConflictResolving.value = false;
  }
}

async function resolveAllPublicConflicts(resolution: "CURRENT" | "INCOMING") {
  if (publicConflictResolving.value) return;
  publicConflictResolving.value = true;
  errorMessage.value = "";
  try {
    await api.resolveAllPublicAgentGitConflicts({
      resolution,
      worktreeId: publicWorktree.value?.worktreeId,
      linuxServerId: publicConflictLinuxServerId()
    });
    activePublicConflict.value = null;
    await loadDiff("PUBLIC");
    await loadPublicConflictFiles();
  } catch (error) {
    errorMessage.value = formatAgentConfigError(error, "批量解决公共 Agent Git 冲突失败");
  } finally {
    publicConflictResolving.value = false;
  }
}

async function abortPublicConflict() {
  if (publicConflictResolving.value) return;
  publicConflictResolving.value = true;
  errorMessage.value = "";
  try {
    await api.abortPublicAgentGitConflict(publicWorktree.value?.worktreeId, publicConflictLinuxServerId());
    activePublicConflict.value = null;
    await loadDiff("PUBLIC");
    await loadPublicConflictFiles();
  } catch (error) {
    errorMessage.value = formatAgentConfigError(error, "取消公共 Agent Git 合并失败");
  } finally {
    publicConflictResolving.value = false;
  }
}

function handleDiffFileClick(file: AgentConfigDiffFile) {
  if (isConflictFile(file) && activeScope.value === "PUBLIC") {
    void openPublicConflict(file.path);
    return;
  }
  selectedDiffPath.value = file.path;
}

const publicRepositories = ref<PublicAgentRepositoryStatus[]>([]);
const selectedPublicLinuxServerId = ref("");
const showSwitchWorktreeModal = ref(false);
const switchWorktreeOptionsLoading = ref(false);
const switchWorktreeOptionsError = ref("");
const switchPublicLinuxServerId = ref("");
const switchPublicWorktreeId = ref("");
const switchPublicWorktrees = ref<AgentConfigWorktreeOption[]>([]);
const showCreatePublicWorktreeModal = ref(false);
const createPublicWorktreeLinuxServerId = ref("");
const createPublicWorktreeError = ref("");
const showCreateWorkspacePackageModal = ref(false);
const workspacePackageType = ref<"AGENT" | "SKILL">("AGENT");
const workspacePackageName = ref("");
const workspacePackageError = ref("");

const initializedPublicRepositories = computed(() =>
  publicRepositories.value.filter((repository) => repository.initialized)
);

const activePublicRepository = computed(() => {
  const serverId = publicWorktree.value?.linuxServerId ?? publicConfigLinuxServerId.value ?? selectedPublicLinuxServerId.value;
  return publicRepositories.value.find((repository) => repository.linuxServerId === serverId)
    ?? initializedPublicRepositories.value[0]
    ?? null;
});

const publicSource = computed(() => {
  const repository = activePublicRepository.value;
  const serverId = publicWorktree.value?.linuxServerId
    ?? publicConfigLinuxServerId.value
    ?? repository?.linuxServerId
    ?? "";
  const serverName = repository?.serverName || serverId;
  if (publicWorktree.value) {
    return {
      mode: "worktree",
      name: publicWorktree.value.worktreeName,
      serverName,
      serverId,
      path: joinLinuxPath(publicWorktree.value.rootPath, "opencode")
    };
  }
  return {
    mode: "直接目录",
    name: "",
    serverName,
    serverId,
    path: repository?.configDirPath
      ?? (repository?.gitRootPath ? joinLinuxPath(repository.gitRootPath, "opencode") : "")
  };
});

const publicRootBadge = computed(() => {
  const source = publicSource.value;
  if (!source.serverName && !source.name) return "";
  return source.mode === "worktree"
    ? ["worktree", source.name, source.serverName || source.serverId].filter(Boolean).join(" · ")
    : ["直接", source.serverName || source.serverId].filter(Boolean).join(" · ");
});

const publicSourceTooltip = computed(() => {
  const source = publicSource.value;
  return [source.mode, source.name, source.serverName || source.serverId, source.path]
    .filter(Boolean)
    .join(" · ");
});

function joinLinuxPath(root: string, child: string) {
  return `${root.replace(/\/+$/, "")}/${child}`;
}

/**
 * Agent tab 使用合成 path 隔离路由；复制路径必须使用服务端状态返回的真实根目录。
 * 公共个人分支以当前 worktree 为准，应用配置以 workspace 状态中的实际 Agent 目录为准。
 */
function agentAbsolutePath(scope: Scope, path: string): string | undefined {
  const root = scope === "PUBLIC" ? publicSource.value.path : status.value.WORKSPACE?.agentDirectory;
  return root ? joinLinuxPath(root.replace(/\\/g, "/"), path.replace(/\\/g, "/").replace(/^\/+/, "")) : undefined;
}

const publicUpdateConflictMessage = computed(() =>
  activePublicRepository.value?.status === "CONFLICT"
    ? activePublicRepository.value.message ?? "公共配置仓库存在未提交变更"
    : ""
);

const publicUpdateRequiresDiscard = computed(() =>
  publicUpdateConflictMessage.value.includes("未提交变更")
);

const selectedSwitchRepository = computed(() =>
  initializedPublicRepositories.value.find((repository) => repository.linuxServerId === switchPublicLinuxServerId.value) ?? null
);

const selectedSwitchWorktree = computed(() =>
  switchPublicWorktrees.value.find((worktree) => worktree.worktreeId === switchPublicWorktreeId.value) ?? null
);

const canSubmitSwitchWorktree = computed(() =>
  !busy.value &&
  !switchWorktreeOptionsLoading.value &&
  !!switchPublicLinuxServerId.value &&
  !!selectedSwitchWorktree.value &&
  initializedPublicRepositories.value.length > 0
);

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
    throw new Error("没有已初始化服务器，请到系统管理 > 配置管理 > TestAgent公共配置管理初始化。");
  }
  return nextServer;
}

async function openSwitchWorktreeModal() {
  if (!props.canWrite || status.value.PUBLIC?.enabled === false) return;
  switchWorktreeOptionsError.value = "";
  switchPublicWorktreeId.value = "";
  switchPublicWorktrees.value = [];
  showSwitchWorktreeModal.value = true;
  await loadPublicSwitchOptions();
}

async function openCreatePublicWorktreeModal() {
  if (!props.canWrite || status.value.PUBLIC?.enabled === false || busy.value) return;
  createPublicWorktreeError.value = "";
  showCreatePublicWorktreeModal.value = true;
  try {
    publicRepositories.value = await api.listPublicAgentRepositories();
    createPublicWorktreeLinuxServerId.value = preferredPublicServer(publicRepositories.value);
    if (!createPublicWorktreeLinuxServerId.value) {
      createPublicWorktreeError.value = "没有已初始化服务器，请到系统管理 > 配置管理 > TestAgent公共配置管理初始化。";
    }
  } catch (error) {
    createPublicWorktreeLinuxServerId.value = "";
    createPublicWorktreeError.value = formatAgentConfigError(error, "加载公共配置服务器失败");
  }
}

function closeCreatePublicWorktreeModal() {
  showCreatePublicWorktreeModal.value = false;
  createPublicWorktreeError.value = "";
}

async function submitCreatePublicWorktree() {
  const serverId = createPublicWorktreeLinuxServerId.value;
  if (!serverId || busy.value) return;
  const repository = initializedPublicRepositories.value.find((item) => item.linuxServerId === serverId);
  if (!repository) return;
  busy.value = true;
  createPublicWorktreeError.value = "";
  try {
    // 后端保证同一用户在同一服务器只生成 public-{userId} 稳定分支；重复创建会返回已有 worktree。
    const created = await api.createPublicAgentWorktree({
      baseName: "public-personal",
      branch: repository.currentBranch?.trim() || status.value.PUBLIC?.currentBranch?.trim() || "main",
      linuxServerId: serverId,
      operationId: newOperationId()
    });
    publicConfigLinuxServerId.value = serverId;
    selectedPublicLinuxServerId.value = serverId;
    publicWorktree.value = { ...created };
    resetPublicFileTree();
    if (rootExpanded.value.has("PUBLIC")) {
      await loadDirectory("PUBLIC", "");
    }
    closeCreatePublicWorktreeModal();
    notifySuccess("公共 worktree 已就绪", `分支 ${created.branch}`);
  } catch (error) {
    createPublicWorktreeError.value = formatAgentConfigError(error, "创建公共 worktree 失败");
  } finally {
    busy.value = false;
  }
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
      switchWorktreeOptionsError.value = "没有已初始化服务器，请到系统管理 > 配置管理 > TestAgent公共配置管理初始化。";
      switchPublicWorktrees.value = [];
      switchPublicWorktreeId.value = "";
      return;
    }
  } catch (error) {
    switchWorktreeOptionsError.value = formatAgentConfigError(error, "加载公共配置服务器失败");
    publicRepositories.value = [];
    switchPublicLinuxServerId.value = "";
    switchPublicWorktrees.value = [];
    switchPublicWorktreeId.value = "";
    return;
  } finally {
    switchWorktreeOptionsLoading.value = false;
  }
  await loadSwitchWorktreesForSelectedServer();
}

async function loadSwitchWorktreesForSelectedServer() {
  const serverId = switchPublicLinuxServerId.value;
  switchPublicWorktrees.value = [];
  switchPublicWorktreeId.value = "";
  if (!serverId) return;
  switchWorktreeOptionsLoading.value = true;
  switchWorktreeOptionsError.value = "";
  try {
    const worktrees = await api.listPublicAgentWorktrees(serverId);
    switchPublicWorktrees.value = worktrees;
    if (publicWorktree.value?.linuxServerId === serverId && worktrees.some((item) => item.worktreeId === publicWorktree.value?.worktreeId)) {
      switchPublicWorktreeId.value = publicWorktree.value.worktreeId;
    } else {
      switchPublicWorktreeId.value = worktrees[0]?.worktreeId ?? "";
    }
  } catch (error) {
    switchWorktreeOptionsError.value = formatAgentConfigError(error, "加载公共 worktree 列表失败");
  } finally {
    switchWorktreeOptionsLoading.value = false;
  }
}

async function submitSwitchWorktree() {
  const serverId = switchPublicLinuxServerId.value;
  const selected = selectedSwitchWorktree.value;
  if (!serverId || !selected || !canSubmitSwitchWorktree.value) return;
  busy.value = true;
  switchWorktreeOptionsError.value = "";
  try {
    publicConfigLinuxServerId.value = serverId;
    selectedPublicLinuxServerId.value = serverId;
    publicWorktree.value = { ...selected };
    resetPublicFileTree();
    if (rootExpanded.value.has("PUBLIC")) {
      await loadDirectory("PUBLIC", "");
    }
    closeSwitchWorktreeModal();
  } catch (error) {
    switchWorktreeOptionsError.value = formatAgentConfigError(error, "切换公共 worktree 失败");
  } finally {
    busy.value = false;
  }
}

function resetPublicFileTree() {
  invalidateDirectoryCache("PUBLIC", true);
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

function openCreateWorkspacePackageModal() {
  if (!props.workspaceId || !workspaceCanWrite.value || busy.value) return;
  workspacePackageType.value = "AGENT";
  workspacePackageName.value = "";
  workspacePackageError.value = "";
  showCreateWorkspacePackageModal.value = true;
}

function closeCreateWorkspacePackageModal() {
  showCreateWorkspacePackageModal.value = false;
  workspacePackageError.value = "";
}

async function submitCreateWorkspacePackage() {
  if (!props.workspaceId || !workspaceCanWrite.value || busy.value) return;
  const displayName = workspacePackageName.value.trim();
  const packageName = slugifyPackageName(displayName);
  if (!displayName || !packageName) {
    workspacePackageError.value = `请输入${workspacePackageType.value === "AGENT" ? "Agent" : "Skill"} 名称`;
    return;
  }
  closeCreateWorkspacePackageModal();
  busy.value = true;
  errorMessage.value = "";
  try {
    const createdPaths = workspacePackageType.value === "AGENT"
      ? [`agents/${packageName}.md`]
      : [
          `skills/${packageName}/SKILL.md`,
          `skills/${packageName}/rules/README.md`,
          `skills/${packageName}/templates/README.md`
        ];
    if (workspacePackageType.value === "AGENT") {
      await api.writeWorkspaceAgentFile(
        props.workspaceId,
        createdPaths[0],
        workspaceAgentTemplate(displayName),
        worktreeId("WORKSPACE")
      );
    } else {
      await api.writeWorkspaceAgentFile(props.workspaceId, createdPaths[0], workspaceSkillTemplate(displayName, packageName), worktreeId("WORKSPACE"));
      await api.writeWorkspaceAgentFile(props.workspaceId, createdPaths[1], workspaceRulesTemplate(displayName), worktreeId("WORKSPACE"));
      await api.writeWorkspaceAgentFile(props.workspaceId, createdPaths[2], workspaceTemplatesTemplate(displayName), worktreeId("WORKSPACE"));
    }
    rootExpanded.value = new Set([...rootExpanded.value, "WORKSPACE"]);
    entriesByScope.value = { ...entriesByScope.value, WORKSPACE: {} };
    expandedByScope.value = {
      ...expandedByScope.value,
      WORKSPACE: workspacePackageType.value === "AGENT"
        ? new Set(["agents"])
        : new Set(["skills", `skills/${packageName}`])
    };
    await loadDirectory("WORKSPACE", "");
    if (workspacePackageType.value === "AGENT") {
      await loadDirectory("WORKSPACE", "agents");
    } else {
      await loadDirectory("WORKSPACE", "skills");
      await loadDirectory("WORKSPACE", `skills/${packageName}`);
    }
  } catch (error) {
    errorMessage.value = formatAgentConfigError(
      error,
      `新建应用 ${workspacePackageType.value === "AGENT" ? "Agent" : "Skill"} 失败`
    );
  } finally {
    busy.value = false;
  }
}

/** OpenCode Markdown Agent 的名称由文件名决定，模板只写原生支持的配置和提示词。 */
function workspaceAgentTemplate(displayName: string) {
  return `---
description: ${displayName} application workspace agent
mode: primary
hidden: false
---

# ${displayName}

You are the ${displayName} application agent.

Return verifiable results and keep changes scoped to the current personal worktree.
`;
}

function workspaceSkillTemplate(displayName: string, packageName: string) {
  return `---
name: ${packageName}
description: ${displayName} application workspace skill
compatibility: opencode
metadata:
  scope: workspace
  source: test-agent
---

# ${displayName}

## What I do

- Load application-specific testing, design, and delivery instructions for this workspace.
- Use the files under \`rules/\` and \`templates/\` only when they are relevant to the current task.
- Return verifiable output and list unresolved assumptions.

## When to use me

Use this skill for tasks that need ${displayName} application context, reusable rules, or output templates.

Ask clarifying questions when the target application, version, or workspace is ambiguous.

## Resources

- \`rules/\`: application-specific rules.
- \`templates/\`: reusable output templates.
`;
}

function workspaceRulesTemplate(displayName: string) {
  return `# ${displayName} Rules

Add application-specific rule Markdown files here. Reference them from \`../SKILL.md\` only when the skill should load them for a concrete workflow.
`;
}

function workspaceTemplatesTemplate(displayName: string) {
  return `# ${displayName} Templates

Add reusable output templates here. Document the purpose and selection conditions in \`../SKILL.md\`.
`;
}

function slugifyPackageName(value: string) {
  const converted = Array.from(value.trim())
    // 中文拼音保持逐字分段；连续英文和数字不插入额外短横线。
    .map((char) => PINYIN_SEGMENTS[char] ? ` ${PINYIN_SEGMENTS[char]} ` : char)
    .join("");
  return converted
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/-{2,}/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 64);
}

const PINYIN_SEGMENTS: Record<string, string> = {
  "支": "zhi",
  "付": "fu",
  "测": "ce",
  "试": "shi",
  "技": "ji",
  "能": "neng",
  "应": "ying",
  "用": "yong",
  "接": "jie",
  "口": "kou",
  "设": "she",
  "计": "ji",
  "执": "zhi",
  "行": "xing",
  "案": "an",
  "例": "li",
  "对": "dui",
  "象": "xiang",
  "策": "ce",
  "略": "lue",
  "规": "gui",
  "划": "hua",
  "生": "sheng",
  "成": "cheng",
  "审": "shen",
  "查": "cha",
  "工": "gong",
  "作": "zuo",
  "空": "kong",
  "间": "jian",
  "配": "pei",
  "置": "zhi"
};

async function loadDiff(scope = activeScope.value) {
  if (!scope) return;
  if (scope === "WORKSPACE" && !props.workspaceId) return;
  activeScope.value = scope;
  busy.value = true;
  try {
    const diff = scope === "PUBLIC"
      ? await api.getPublicAgentDiff(worktreeId(scope))
      : await api.getWorkspaceAgentDiff(props.workspaceId!, worktreeId(scope));
    diffFiles.value = diff.files;
    if (scope === "PUBLIC") {
      publicConflictPathHints.value = diff.files.filter((file) => isConflictFile(file)).map((file) => file.path);
    }
    selectedDiffPath.value = diff.files[0]?.path ?? "";
  } catch (error) {
    errorMessage.value = formatAgentConfigError(error, "加载 Agent Diff 失败");
  } finally {
    busy.value = false;
  }
}

async function stage(file: AgentConfigDiffFile) {
  const scope = activeScope.value;
  if (!scope) return;
  if (!canWriteScope(scope)) return;
  busy.value = true;
  try {
    if (scope === "PUBLIC") {
      await api.stagePublicAgentFiles([file.path], worktreeId(scope));
    } else {
      await api.stageWorkspaceAgentFiles(props.workspaceId!, [file.path], worktreeId(scope));
    }
    await loadDiff(scope);
  } catch (error) {
    errorMessage.value = formatAgentConfigError(error, "暂存 Agent 文件失败");
  } finally {
    busy.value = false;
  }
}

async function commit() {
  const scope = activeScope.value;
  if (!scope) return;
  if (!canWriteScope(scope)) return;
  if (!commitMessage.value.trim()) return;
  const message = commitMessage.value.trim();
  const operationId = newOperationId();
  await runOperation(
    () =>
      scope === "PUBLIC"
        ? api.commitPublicAgentConfig({ message, worktreeId: worktreeId(scope), operationId })
        : api.commitWorkspaceAgentConfig(props.workspaceId!, { message, worktreeId: worktreeId(scope), operationId }),
    "提交 Agent 配置",
    operationId
  );
  commitMessage.value = "";
  await loadDiff(scope);
}

async function publish() {
  const scope = activeScope.value;
  if (!scope) return;
  if (!canWriteScope(scope)) return;
  const operationId = newOperationId();
  await runOperation(
    () =>
      scope === "PUBLIC"
        ? api.publishPublicAgentConfig(worktreeId(scope), operationId)
        : api.publishWorkspaceAgentConfig(props.workspaceId!, worktreeId(scope), operationId),
    "发布 Agent 配置",
    operationId
  );
  await refreshScope(scope);
}

async function runOperation<T>(
  action: () => Promise<T>,
  label: string,
  knownOperationId?: string,
  onProgress?: (event: AgentConfigProgressEvent) => void
): Promise<T | null> {
  busy.value = true;
  errorMessage.value = "";
  const operationId = knownOperationId ?? newOperationId();
  progressEvents.value = [{ type: "snapshot", operationId, currentStep: label, status: "RUNNING" }];
  let socket: { close: () => void } | null = null;
  try {
    socket = await api.connectAgentConfigProgress(operationId, (event) => {
      progressEvents.value = [...progressEvents.value.slice(-8), event];
      onProgress?.(event);
    });
  } catch {
    socket = null;
  }
  try {
    const result = await action();
    if (result && typeof result === "object" && "status" in result) {
      const operation = result as { status?: string; errorMessage?: string | null };
      if (operation.status && operation.status !== "SUCCEEDED") {
        throw new Error(operation.errorMessage || `${label}未成功完成`);
      }
    }
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
  busy: panelBusy
});
</script>

<template>
  <div class="agent-config-panel">
    <div v-if="!hideHeader" class="agent-config-header">
      <span>Agent</span>
      <button type="button" class="agent-icon-btn" title="刷新" aria-label="刷新" :disabled="refreshing" @click="refreshAll()">
        <RefreshCw class="h-3.5 w-3.5" :class="{ 'animate-spin': refreshing }" :stroke-width="1.5" />
      </button>
    </div>
    <div v-if="errorMessage" class="agent-error">
      <AlertTriangle class="mt-[2px] h-3.5 w-3.5 shrink-0" :stroke-width="1.5" />
      <span>{{ errorMessage }}</span>
    </div>

    <div class="agent-tree">
      <div class="agent-root-row" :class="{ active: isRootActive('PUBLIC') }">
        <el-tooltip :content="publicSourceTooltip" placement="top-start" :show-after="50">
          <button type="button" class="agent-root-main" :title="publicSourceTooltip" @click="toggleRoot('PUBLIC')">
            <i :class="['codicon codicon-chevron-right ta-file-tree-twistie', rootExpanded.has('PUBLIC') && 'is-open']" aria-hidden="true" />
            <span class="agent-root-title">公共级</span>
            <span v-if="publicRootBadge" class="agent-root-badge">{{ publicRootBadge }}</span>
          </button>
        </el-tooltip>
        <div class="agent-root-actions">
          <button
            v-if="canWrite"
            type="button"
            class="agent-icon-btn"
            title="在公共级根目录新建文件或文件夹"
            aria-label="在公共级根目录新建文件或文件夹"
            :disabled="busy || status.PUBLIC?.enabled === false || !publicWorktree?.worktreeId"
            @click="openCreateEntryDialog('PUBLIC', '')"
          >
            <FilePlus2 class="h-3.5 w-3.5" :stroke-width="1.5" />
          </button>
          <div v-if="canWrite" class="agent-more-menu-container">
            <button
              type="button"
              class="agent-icon-btn"
              title="更多操作"
              aria-label="更多操作"
              :disabled="busy || status.PUBLIC?.enabled === false"
            >
              <MoreHorizontal class="h-3.5 w-3.5" :stroke-width="1.5" />
            </button>
            <div class="agent-more-menu-dropdown">
              <button
                type="button"
                class="agent-dropdown-item"
                :disabled="busy || status.PUBLIC?.enabled === false"
                @click="openCreatePublicWorktreeModal"
              >
                <Plus class="h-3.5 w-3.5" :stroke-width="1.5" />
                <span>创建公共 worktree</span>
              </button>
              <button
                type="button"
                class="agent-dropdown-item"
                :disabled="busy || status.PUBLIC?.enabled === false"
                @click="openSwitchWorktreeModal"
              >
                <GitBranch class="h-3.5 w-3.5" :stroke-width="1.5" />
                <span>切换公共 worktree</span>
              </button>
            </div>
          </div>
          <button
            v-if="canWrite"
            type="button"
            class="agent-icon-btn"
            title="Agent 配置更新（公共）"
            aria-label="Agent 配置更新（公共）"
            :disabled="personalRuntimeReloadDisabled || status.PUBLIC?.enabled === false || !publicWorktree?.worktreeId"
            @click="requestPersonalRuntimeReload('PUBLIC')"
          >
            <RefreshCw
              class="h-3.5 w-3.5"
              :class="{ 'animate-spin': personalRuntimeReloading === 'PUBLIC' }"
              :stroke-width="1.5"
            />
          </button>
        </div>
      </div>
      <div v-if="rootExpanded.has('PUBLIC')" class="agent-node-list">
        <div v-if="loadingByScope.PUBLIC.has('')" class="agent-loading"><i class="codicon codicon-loading codicon-modifier-spin ta-file-tree-loading" aria-hidden="true" />加载中</div>
        <AgentConfigTreeNode
          v-for="entry in visibleEntries('PUBLIC', '')"
          :key="`PUBLIC:${entry.path}`"
          :entry="entry"
          :depth="0"
          :entries-by-directory="entriesByScope.PUBLIC"
          :expanded-directories="expandedByScope.PUBLIC"
          :loading-path="loadingByScope.PUBLIC"
          :active-path="activeAgentFile?.scope === 'PUBLIC' ? activeAgentFile.path : undefined"
          :conflict-paths="publicConflictPathSet"
          :can-write="canWrite"
          :can-create-in-directory="(path) => canCreateInDirectory('PUBLIC', path)"
          :can-delete-entry="(path) => canDeleteEntry('PUBLIC', path)"
          :can-rename-entry="() => false"
          @toggle="(path) => toggleDirectory('PUBLIC', path)"
          @open-file="(path) => openFile('PUBLIC', path)"
          @create-entry="(path) => openCreateEntryDialog('PUBLIC', path)"
          @delete-entry="(entry) => openDeleteEntryDialog('PUBLIC', entry)"
        />
      </div>

      <div class="agent-root-row" :class="{ active: isRootActive('WORKSPACE') }">
        <el-tooltip content="应用自定义 agents 及 skills，应用可以自己心中修改和发布" placement="top-start" :show-after="50">
          <button
            type="button"
            class="agent-root-main"
            :disabled="!workspaceId"
            @click="toggleRoot('WORKSPACE')"
          >
            <i :class="['codicon codicon-chevron-right ta-file-tree-twistie', rootExpanded.has('WORKSPACE') && 'is-open']" aria-hidden="true" />
            <span class="agent-root-title">应用级</span>
            <span v-if="workspaceId" class="agent-root-badge">个人 worktree</span>
          </button>
        </el-tooltip>
        <div class="agent-root-actions">
          <button
            v-if="workspaceCanWrite"
            type="button"
            class="agent-icon-btn"
            title="在应用级根目录新建文件或文件夹"
            aria-label="在应用级根目录新建文件或文件夹"
            :disabled="busy || !workspaceId"
            @click="openCreateEntryDialog('WORKSPACE', '')"
          >
            <FilePlus2 class="h-3.5 w-3.5" :stroke-width="1.5" />
          </button>
          <button
            v-if="workspaceCanWrite"
            type="button"
            class="agent-icon-btn"
            title="初始化应用 Agent/Skill 配置包"
            aria-label="初始化应用 Agent/Skill 配置包"
            :disabled="busy || !workspaceId"
            @click="openCreateWorkspacePackageModal"
          >
            <Plus class="h-3.5 w-3.5" :stroke-width="1.5" />
          </button>
          <button
            v-if="workspaceCanWrite"
            type="button"
            class="agent-icon-btn"
            title="Agent 配置更新（应用）"
            aria-label="Agent 配置更新（应用）"
            :disabled="personalRuntimeReloadDisabled || !workspaceId"
            @click="requestPersonalRuntimeReload('WORKSPACE')"
          >
            <RefreshCw
              class="h-3.5 w-3.5"
              :class="{ 'animate-spin': personalRuntimeReloading === 'WORKSPACE' }"
              :stroke-width="1.5"
            />
          </button>
        </div>
      </div>
      <div v-if="rootExpanded.has('WORKSPACE')" class="agent-node-list">
        <div v-if="loadingByScope.WORKSPACE.has('')" class="agent-loading"><i class="codicon codicon-loading codicon-modifier-spin ta-file-tree-loading" aria-hidden="true" />加载中</div>
        <AgentConfigTreeNode
          v-for="entry in visibleEntries('WORKSPACE', '')"
          :key="`WORKSPACE:${entry.path}`"
          :entry="entry"
          :depth="0"
          :entries-by-directory="entriesByScope.WORKSPACE"
          :expanded-directories="expandedByScope.WORKSPACE"
          :loading-path="loadingByScope.WORKSPACE"
          :active-path="activeAgentFile?.scope === 'WORKSPACE' ? activeAgentFile.path : undefined"
          :conflict-paths="new Set()"
          :can-write="workspaceCanWrite"
          :can-create-in-directory="(path) => canCreateInDirectory('WORKSPACE', path)"
          :can-delete-entry="(path) => canDeleteEntry('WORKSPACE', path)"
          :can-rename-entry="(path) => canRenameEntry('WORKSPACE', path)"
          @toggle="(path) => toggleDirectory('WORKSPACE', path)"
          @open-file="(path) => openFile('WORKSPACE', path)"
          @create-entry="(path) => openCreateEntryDialog('WORKSPACE', path)"
          @delete-entry="(entry) => openDeleteEntryDialog('WORKSPACE', entry)"
          @rename-entry="renameAgentEntry"
        />
      </div>
    </div>

    <FileEntryCreateDialog
      ref="createEntryDialog"
      :allow-upload="false"
      :root-label="createEntryScope === 'PUBLIC' ? '公共 Agent 根目录' : '应用 Agent 根目录'"
      @create-entry="createAgentEntry"
    />
    <FileEntryDeleteDialog ref="deleteEntryDialog" @confirm="deleteAgentEntry" />

    <div v-if="activeScope === 'PUBLIC' && canWrite && !hideGitOps" class="agent-diff">
      <div class="agent-diff-toolbar">
        <button type="button" class="agent-action-btn" :disabled="busy" @click="loadDiff()">
          <GitCompare class="h-3.5 w-3.5" :stroke-width="1.5" />
          Diff
        </button>
        <button v-if="activeScope === 'PUBLIC' ? canWrite : workspaceCanWrite" type="button" class="agent-action-btn" :disabled="busy" @click="publish">
          <Upload class="h-3.5 w-3.5" :stroke-width="1.5" />
          发布
        </button>
      </div>
      <div class="agent-diff-body">
        <div class="agent-diff-files">
          <div v-if="publicConflictFiles.length > 0" class="agent-conflict-banner">
            <div class="agent-conflict-title">
              <AlertTriangle class="h-3.5 w-3.5" :stroke-width="1.5" />
              <span>检测到 {{ publicConflictFiles.length }} 个公共 Agent 冲突</span>
            </div>
            <div class="agent-conflict-actions">
              <button type="button" :disabled="publicConflictResolving" @click="resolveAllPublicConflicts('CURRENT')">保留本地</button>
              <button type="button" :disabled="publicConflictResolving" @click="resolveAllPublicConflicts('INCOMING')">保留远程</button>
              <button type="button" :disabled="publicConflictResolving" @click="abortPublicConflict">取消合并</button>
            </div>
          </div>
          <button
            v-for="file in diffFiles"
            :key="file.path"
            type="button"
            class="agent-diff-file"
            :class="{ active: selectedDiff?.path === file.path, conflict: isConflictFile(file) }"
            @click="handleDiffFileClick(file)"
            @dblclick="!isConflictFile(file) && stage(file)"
          >
            <span>{{ isConflictFile(file) ? 'CONFLICT' : file.status || 'M' }}</span>
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
                没有已初始化服务器，请到系统管理 &gt; 配置管理 &gt; TestAgent公共配置管理初始化。
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
                  <option v-if="switchPublicWorktrees.length === 0" value="">当前服务器没有可切换的个人 worktree</option>
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
                当前服务器尚无你的公共 worktree，请先从“更多操作”创建。
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
        v-if="showCreatePublicWorktreeModal"
        class="fixed inset-0 z-[1000] flex items-center justify-center bg-black/35 px-4 py-6"
        @keydown.esc="closeCreatePublicWorktreeModal"
      >
        <section
          role="dialog"
          aria-modal="true"
          aria-label="创建公共 worktree"
          class="flex w-[min(440px,calc(100vw-24px))] flex-col rounded-lg border border-[var(--ta-border)] bg-[var(--ta-panel)] shadow-xl p-4 gap-4"
        >
          <header class="flex items-center justify-between border-b border-[var(--ta-border)] pb-2">
            <h2 class="text-[14px] font-semibold text-[var(--ta-text)]">创建公共 worktree</h2>
          </header>

          <div class="flex flex-col gap-3">
            <div v-if="createPublicWorktreeError" class="agent-modal-alert">
              <AlertTriangle class="h-3.5 w-3.5 shrink-0" :stroke-width="1.5" />
              <span>{{ createPublicWorktreeError }}</span>
            </div>

            <div class="flex flex-col gap-1.5">
              <label for="public-create-server" class="text-[11px] text-[var(--ta-muted)] font-medium">服务器</label>
              <div class="agent-modal-select">
                <Globe2 class="h-3.5 w-3.5 text-[var(--ta-muted)]" :stroke-width="1.5" />
                <select
                  id="public-create-server"
                  v-model="createPublicWorktreeLinuxServerId"
                  :disabled="busy || initializedPublicRepositories.length === 0"
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
            </div>

            <div class="agent-modal-help leading-5">
              系统会按当前用户创建固定的 <code>public-{用户ID}</code> 分支和个人 worktree；如果已经存在，会直接挂载已有 worktree。
            </div>
          </div>

          <footer class="flex justify-end gap-2 pt-2 border-t border-[var(--ta-border)]">
            <Button variant="ghost" size="sm" @click="closeCreatePublicWorktreeModal">取消</Button>
            <Button
              variant="primary"
              size="sm"
              :disabled="busy || !createPublicWorktreeLinuxServerId"
              @click="submitCreatePublicWorktree"
            >
              创建并切换
            </Button>
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
          class="flex w-[min(420px,calc(100vw-24px))] flex-col rounded-lg border border-[var(--ta-border)] bg-[var(--ta-panel)] shadow-xl p-4 gap-4"
        >
          <header class="flex items-center justify-between border-b border-[var(--ta-border)] pb-2">
            <h2 class="text-[14px] font-semibold text-[var(--ta-text)]">更新公共配置</h2>
          </header>

          <div class="flex flex-col gap-3">
            <div v-if="updatePublicConfigError" class="agent-modal-alert">
              <AlertTriangle class="h-3.5 w-3.5 shrink-0" :stroke-width="1.5" />
              <span>{{ updatePublicConfigError }}</span>
            </div>

            <div v-if="publicUpdateConflictMessage" class="agent-modal-alert">
              <AlertTriangle class="h-3.5 w-3.5 shrink-0" :stroke-width="1.5" />
              <span>{{ publicUpdateConflictMessage }}</span>
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

            <div class="flex flex-col gap-1.5">
              <label for="update-public-commit-message" class="text-[11px] text-[var(--ta-muted)] font-medium">
                提交信息 <span class="text-[var(--ta-danger,#b91c1c)]">*</span>
              </label>
              <Input
                id="update-public-commit-message"
                v-model="updatePublicConfigCommitMessage"
                placeholder="请输入本次提交说明，确认后会将本地变更提交并推送到远端"
                class="h-8 text-[13px]"
                :disabled="loadingUpdatePublicConfigBranches"
                @keydown.enter="canSubmitUpdatePublicConfig && submitUpdatePublicConfig()"
              />
              <span class="agent-modal-help">
                流程：拉取远端最新提交 → 暂存并提交本地变更 → 合并远端分支 → 推送到远端
              </span>
            </div>

            <label v-if="publicUpdateRequiresDiscard" class="flex items-start gap-2 text-[12px] text-[var(--ta-text)]">
              <input
                v-model="updatePublicDiscardLocalChanges"
                type="checkbox"
                class="mt-0.5 h-3.5 w-3.5 accent-[var(--ta-primary)]"
              />
              <span>放弃已跟踪本地修改后再提交</span>
            </label>
          </div>

          <footer class="flex justify-end gap-2 pt-2 border-t border-[var(--ta-border)]">
            <Button variant="ghost" size="sm" @click="closeUpdatePublicConfigModal">取消</Button>
            <Button
              variant="primary"
              size="sm"
              :disabled="!canSubmitUpdatePublicConfig"
              @click="submitUpdatePublicConfig"
            >
              提交并推送
            </Button>
          </footer>
        </section>
      </div>
    </Teleport>
    <Teleport to="body">
      <div
        v-if="showUpdatePublicProgressDialog"
        class="fixed inset-0 z-[1000] flex items-center justify-center bg-black/35 px-4 py-6"
      >
        <section
          role="dialog"
          aria-modal="true"
          aria-label="公共 Agent 提交并推送进度"
          class="agent-git-progress-dialog"
        >
          <header class="agent-git-progress-header">
            <div>
              <h2>公共 Agent 提交并推送进度</h2>
              <p v-if="updatingPublicConfig">正在处理 {{ updatePublicProgressBranch }}</p>
              <p v-else-if="updatePublicProgressError" class="agent-git-progress-failed">执行失败</p>
              <p v-else class="agent-git-progress-success">执行成功</p>
            </div>
            <button
              type="button"
              class="agent-git-progress-close"
              :disabled="updatingPublicConfig"
              @click="showUpdatePublicProgressDialog = false"
            >
              关闭
            </button>
          </header>

          <ol class="agent-git-progress-steps">
            <li :class="['agent-git-progress-step', publicUpdateStepClass(1)]">
              <component :is="publicUpdateStepIcon(1)" class="agent-git-progress-step-icon" :size="16" />
              <span>拉取远端最新提交</span>
              <small>{{ publicUpdateStepStatusText(1) }}</small>
            </li>
            <li :class="['agent-git-progress-step', publicUpdateStepClass(2)]">
              <component :is="publicUpdateStepIcon(2)" class="agent-git-progress-step-icon" :size="16" />
              <span>暂存并提交本地变更</span>
              <small>{{ publicUpdateStepStatusText(2) }}</small>
            </li>
            <li :class="['agent-git-progress-step', publicUpdateStepClass(3)]">
              <component :is="publicUpdateStepIcon(3)" class="agent-git-progress-step-icon" :size="16" />
              <span>合并远端分支</span>
              <small>{{ publicUpdateStepStatusText(3) }}</small>
            </li>
            <li :class="['agent-git-progress-step', publicUpdateStepClass(4)]">
              <component :is="publicUpdateStepIcon(4)" class="agent-git-progress-step-icon" :size="16" />
              <span>推送到远端仓库</span>
              <small>{{ publicUpdateStepStatusText(4) }}</small>
            </li>
            <li :class="['agent-git-progress-step', publicUpdateStepClass(5)]">
              <component :is="publicUpdateStepIcon(5)" class="agent-git-progress-step-icon" :size="16" />
              <span>广播公共配置同步</span>
              <small>{{ publicUpdateStepStatusText(5) }}</small>
            </li>
          </ol>

          <div class="agent-git-command-panel">
            <div class="agent-git-command-title">当前步骤执行的 Git 命令</div>
            <div class="agent-git-command-console">
              <div v-if="updatePublicProgressCommands.length === 0" class="agent-git-command-empty">暂无执行的命令</div>
              <div v-for="(command, index) in updatePublicProgressCommands" :key="index" class="agent-git-command-line">
                <span>$</span>
                <code>{{ command }}</code>
              </div>
            </div>
          </div>

          <div v-if="updatePublicProgressError" class="agent-git-progress-error">
            <strong>错误说明</strong>
            <p>{{ updatePublicProgressError }}</p>
            <div v-if="publicConflictFiles.length > 0" class="agent-git-progress-conflicts">
              <div class="agent-git-progress-conflict-title">冲突文件</div>
              <button
                v-for="file in publicConflictFiles"
                :key="`progress-conflict:${file.path}`"
                type="button"
                class="agent-git-progress-conflict-file"
                :disabled="publicConflictLoading"
                @click="openPublicConflict(file.path)"
              >
                <span>{{ file.path }}</span>
                <span>处理冲突</span>
              </button>
              <div class="agent-conflict-actions">
                <button type="button" :disabled="publicConflictResolving" @click="resolveAllPublicConflicts('CURRENT')">全部保留本地</button>
                <button type="button" :disabled="publicConflictResolving" @click="resolveAllPublicConflicts('INCOMING')">全部采用远端</button>
                <button type="button" :disabled="publicConflictResolving" @click="abortPublicConflict">取消合并</button>
              </div>
            </div>
          </div>
        </section>
      </div>
    </Teleport>
    <Teleport to="body">
      <div
        v-if="showCreateWorkspacePackageModal"
        class="fixed inset-0 z-[1000] flex items-center justify-center bg-black/35 px-4 py-6"
        @keydown.esc="closeCreateWorkspacePackageModal"
      >
        <section
          role="dialog"
          aria-modal="true"
          aria-label="初始化应用 Agent/Skill 配置包"
          class="flex w-[min(420px,calc(100vw-24px))] flex-col rounded-lg border border-[var(--ta-border)] bg-[var(--ta-panel)] shadow-xl p-4 gap-4"
        >
          <header class="flex items-center justify-between border-b border-[var(--ta-border)] pb-2">
            <h2 class="text-[14px] font-semibold text-[var(--ta-text)]">初始化应用 Agent/Skill 配置包</h2>
          </header>

          <div class="flex flex-col gap-3">
            <div v-if="workspacePackageError" class="agent-modal-alert">
              <AlertTriangle class="h-3.5 w-3.5 shrink-0" :stroke-width="1.5" />
              <span>{{ workspacePackageError }}</span>
            </div>
            <div class="flex flex-col gap-1.5">
              <span class="text-[11px] text-[var(--ta-muted)] font-medium">配置类型</span>
              <div role="radiogroup" aria-label="配置类型" class="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  role="radio"
                  :aria-checked="workspacePackageType === 'AGENT'"
                  class="h-8 rounded-md border text-[12px] transition-colors"
                  :class="workspacePackageType === 'AGENT'
                    ? 'border-[var(--ta-accent)] bg-[var(--ta-hover)] text-[var(--ta-accent)]'
                    : 'border-[var(--ta-border)] bg-[var(--ta-panel)] text-[var(--ta-muted)] hover:text-[var(--ta-text)]'"
                  @click="workspacePackageType = 'AGENT'"
                >
                  Agent
                </button>
                <button
                  type="button"
                  role="radio"
                  :aria-checked="workspacePackageType === 'SKILL'"
                  class="h-8 rounded-md border text-[12px] transition-colors"
                  :class="workspacePackageType === 'SKILL'
                    ? 'border-[var(--ta-accent)] bg-[var(--ta-hover)] text-[var(--ta-accent)]'
                    : 'border-[var(--ta-border)] bg-[var(--ta-panel)] text-[var(--ta-muted)] hover:text-[var(--ta-text)]'"
                  @click="workspacePackageType = 'SKILL'"
                >
                  Skill
                </button>
              </div>
            </div>
            <div class="flex flex-col gap-1.5">
              <label for="workspace-package-name-input" class="text-[11px] text-[var(--ta-muted)] font-medium">
                {{ workspacePackageType === 'AGENT' ? 'Agent 名称' : 'Skill 名称' }}
              </label>
              <Input
                id="workspace-package-name-input"
                v-model="workspacePackageName"
                :placeholder="workspacePackageType === 'AGENT' ? '例如：支付测试 Agent' : '例如：支付测试技能'"
                class="h-8 text-[13px]"
                autofocus
                @keydown.enter="submitCreateWorkspacePackage"
              />
            </div>
          </div>

          <footer class="flex justify-end gap-2 pt-2 border-t border-[var(--ta-border)]">
            <Button variant="ghost" size="sm" @click="closeCreateWorkspacePackageModal">取消</Button>
            <Button variant="primary" size="sm" :disabled="busy || !workspacePackageName.trim()" @click="submitCreateWorkspacePackage">创建</Button>
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
  background: var(--ta-tree-bg, #f8f8f8);
  color: var(--ta-tree-text, #3b3b3b);
  font-family: var(--ta-tree-font-family);
  font-size: var(--ta-tree-font-size);
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
  height: 24px;
  justify-content: space-between;
  border-bottom: 1px solid var(--ta-tree-border, #e5e5e5);
  padding: 0 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--ta-tree-muted, #8b949e);
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
.agent-public-conflict-panel {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 4px 6px 6px;
  border: 1px solid #fca5a5;
  border-radius: 6px;
  background: #fff7f7;
  padding: 8px;
  color: #991b1b;
}
.agent-public-conflict-heading {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
}
.agent-public-conflict-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.agent-public-conflict-file,
.agent-git-progress-conflict-file {
  display: grid;
  width: 100%;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  border: 1px solid #fecaca;
  border-radius: 5px;
  background: #fff;
  color: #991b1b;
  cursor: pointer;
  font-size: 12px;
  min-height: 26px;
  padding: 4px 7px;
  text-align: left;
}
.agent-public-conflict-file span:first-child,
.agent-git-progress-conflict-file span:first-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.agent-public-conflict-file span:last-child,
.agent-git-progress-conflict-file span:last-child {
  color: #b91c1c;
  font-weight: 600;
}
.agent-public-conflict-file:disabled,
.agent-git-progress-conflict-file:disabled {
  cursor: default;
  opacity: 0.5;
}
.agent-merge-overlay {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.35);
  padding: 24px;
}
.agent-merge-overlay :deep(.merge-editor) {
  width: min(1120px, calc(100vw - 48px));
  height: min(920px, calc(100vh - 48px));
  max-width: calc(100vw - 48px);
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
  padding: 4px 0;
  background: var(--ta-tree-bg, #f8f8f8);
}
.agent-root-row {
  height: var(--ta-tree-row-height, 22px);
  min-height: var(--ta-tree-row-height, 22px);
  border-radius: 0;
  color: var(--ta-tree-text, #3b3b3b);
  font-size: var(--ta-tree-font-size, 13px);
  line-height: var(--ta-tree-row-height, 22px);
}
.agent-root-row:hover,
.agent-root-row.active {
  background: var(--ta-tree-active, #e8e8e8);
}
.agent-root-main {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 4px;
  height: 100%;
  border: 0;
  background: transparent;
  color: inherit;
  font-size: var(--ta-tree-font-size, 13px);
  font-weight: 400;
  line-height: var(--ta-tree-row-height, 22px);
  padding: 0 0 0 6px;
  cursor: pointer;
}
.agent-root-actions {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 2px;
  margin-left: auto;
  height: 100%;
}
.agent-root-title {
  flex-shrink: 0;
  white-space: nowrap;
}
.agent-root-badge {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ta-tree-muted, #8b949e);
  font-size: 11px;
}
.agent-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: 20px;
  height: 20px;
  border: 1px solid transparent;
  border-radius: 0;
  background: transparent;
  color: var(--ta-tree-muted, #8b949e);
  font-size: 12px;
  cursor: pointer;
}
.agent-icon-btn {
  padding: 0;
  transition: background-color 0.1s, color 0.1s;
}
.agent-icon-btn:hover {
  background: var(--ta-tree-hover, #f0f0f0);
  color: var(--ta-tree-text, #3b3b3b);
}
.agent-icon-btn:active {
  background: var(--ta-tree-active, #e8e8e8);
}
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
.agent-more-menu-container {
  position: relative;
  display: inline-flex;
}
.agent-more-menu-dropdown {
  position: absolute;
  top: -4px;
  right: 100%;
  margin-right: 4px;
  background: var(--ta-panel, #fff);
  border: 1px solid var(--ta-border, #e4e4e7);
  border-radius: 6px;
  box-shadow: -4px 4px 12px rgba(0, 0, 0, 0.08);
  padding: 4px;
  display: none;
  flex-direction: column;
  gap: 2px;
  z-index: 50;
  min-width: 140px;
}
.agent-more-menu-dropdown::after {
  content: '';
  position: absolute;
  top: 0;
  right: -8px;
  width: 8px;
  height: 100%;
  background: transparent;
}
.agent-more-menu-container:hover .agent-more-menu-dropdown {
  display: flex;
}
.agent-dropdown-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 6px 8px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--ta-text, #18181b);
  font-size: 12px;
  text-align: left;
  cursor: pointer;
  white-space: nowrap;
  transition: background-color 0.1s;
}
.agent-dropdown-item:hover:not(:disabled) {
  background: var(--ta-hover, #f4f4f5);
}
.agent-dropdown-item:active:not(:disabled) {
  background: var(--ta-active, #e4e4e7);
}
.agent-dropdown-item:disabled {
  opacity: 0.5;
  pointer-events: none;
}
.agent-node-list {
  padding-left: 0;
}
.agent-loading {
  display: flex;
  align-items: center;
  gap: 4px;
  height: var(--ta-tree-row-height, 22px);
  padding: 0 8px;
  color: var(--ta-tree-muted, #8b949e);
  font-size: var(--ta-tree-font-size, 13px);
  line-height: var(--ta-tree-row-height, 22px);
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
.agent-conflict-banner {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 6px;
  border: 1px solid #f59e0b;
  border-radius: 6px;
  background: #fffbeb;
  color: #92400e;
  padding: 8px;
}
.agent-conflict-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
}
.agent-conflict-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.agent-conflict-actions button {
  height: 24px;
  border: 1px solid #f59e0b;
  border-radius: 5px;
  background: #fff;
  color: #92400e;
  font-size: 12px;
  cursor: pointer;
  padding: 0 8px;
}
.agent-conflict-actions button:disabled {
  cursor: default;
  opacity: 0.5;
}
.agent-git-progress-conflicts {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 10px;
}
.agent-git-progress-conflict-title {
  color: #991b1b;
  font-size: 12px;
  font-weight: 700;
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
.agent-diff-file.conflict {
  color: #b91c1c;
}
.agent-diff-file.conflict span:first-child {
  font-size: 9px;
  font-weight: 700;
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
.agent-git-progress-dialog {
  display: flex;
  width: min(460px, calc(100vw - 24px));
  max-height: calc(100vh - 48px);
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--ta-border, #e4e4e7);
  border-radius: 8px;
  background: var(--ta-panel, #fff);
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.18);
}
.agent-git-progress-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid var(--ta-border, #e4e4e7);
  padding: 12px 14px;
}
.agent-git-progress-header h2 {
  margin: 0;
  color: var(--ta-text, #18181b);
  font-size: 14px;
  font-weight: 600;
  line-height: 20px;
}
.agent-git-progress-header p {
  margin: 2px 0 0;
  color: var(--ta-muted, #6b7280);
  font-size: 12px;
  line-height: 16px;
}
.agent-git-progress-header .agent-git-progress-success {
  color: #047857;
}
.agent-git-progress-header .agent-git-progress-failed {
  color: #b91c1c;
}
.agent-git-progress-close {
  height: 28px;
  border: 1px solid var(--ta-border, #e4e4e7);
  border-radius: 6px;
  background: #fff;
  color: var(--ta-text, #18181b);
  font-size: 12px;
  cursor: pointer;
  padding: 0 10px;
}
.agent-git-progress-close:disabled {
  cursor: default;
  opacity: 0.5;
}
.agent-git-progress-steps {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 12px 14px;
  list-style: none;
}
.agent-git-progress-step {
  display: grid;
  grid-template-columns: 20px 1fr auto;
  align-items: center;
  gap: 8px;
  min-height: 32px;
  border: 1px solid var(--ta-border, #e4e4e7);
  border-radius: 6px;
  background: #fff;
  color: var(--ta-muted, #6b7280);
  padding: 6px 8px;
}
.agent-git-progress-step span {
  color: var(--ta-text, #18181b);
  font-size: 12px;
  font-weight: 500;
}
.agent-git-progress-step small {
  font-size: 11px;
}
.agent-git-progress-step.running {
  border-color: #2563eb;
  background: #eff6ff;
}
.agent-git-progress-step.completed {
  border-color: #86efac;
  background: #f0fdf4;
}
.agent-git-progress-step.failed {
  border-color: #fecaca;
  background: #fef2f2;
}
.agent-git-progress-step-icon {
  color: currentColor;
}
.agent-git-progress-step.running .agent-git-progress-step-icon {
  animation: spin 1s linear infinite;
}
.agent-git-command-panel {
  border-top: 1px solid var(--ta-border, #e4e4e7);
  background: #fafafa;
  padding: 10px 14px;
}
.agent-git-command-title {
  margin-bottom: 6px;
  color: var(--ta-muted, #6b7280);
  font-size: 11px;
  font-weight: 600;
}
.agent-git-command-console {
  max-height: 150px;
  overflow: auto;
  border: 1px solid #18181b;
  border-radius: 6px;
  background: #09090b;
  color: #d4d4d8;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  line-height: 16px;
  padding: 8px;
}
.agent-git-command-empty {
  color: #71717a;
  font-style: italic;
}
.agent-git-command-line {
  display: flex;
  gap: 6px;
}
.agent-git-command-line span {
  flex: 0 0 auto;
  color: #71717a;
}
.agent-git-command-line code {
  min-width: 0;
  overflow-wrap: anywhere;
  color: inherit;
}
.agent-git-progress-error {
  margin: 0 14px 14px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  background: #fef2f2;
  color: #991b1b;
  padding: 8px 10px;
}
.agent-git-progress-error strong {
  font-size: 12px;
}
.agent-git-progress-error p {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 18px;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
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
