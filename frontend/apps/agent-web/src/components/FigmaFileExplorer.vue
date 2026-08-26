<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { FileExplorer, type FileExplorerProps, type ExplorerTab } from "@test-agent/file-explorer";
import type {
  AppSourceRepositorySummary,
  FileSearchResult,
  FileTreeEntry,
  RequirementImportResult,
  RunDiffFile,
  WorkspaceViewEntry
} from "@test-agent/shared-types";
import type { AppWorkspaceTemplate, AppWorkspaceVersion } from "./WorkbenchFooter.vue";
import WorkbenchFooter from "./WorkbenchFooter.vue";
import AgentConfigPanel from "./AgentConfigPanel.vue";
import type {
  AgentConfigMutation,
  AgentFileLoadRequest,
  AgentQuickCommitRequest,
  PublicWorktreeMountRequest
} from "./agentFileLoad";
import GitChangesPanel from "./GitChangesPanel.vue";
import { ChevronDown, ChevronRight, CloudDownload, FolderTree, GitBranch, Globe, MoreHorizontal, Plane, Plus, RefreshCw, Search } from "lucide-vue-next";
import type { AppSourceWorkspaceContext, SelectedWorkspaceKind } from "./app-source-workspace";

const props = withDefaults(defineProps<FileExplorerProps & {
  /** 当前应用名，传递给 WorkbenchFooter 作为两级菜单首行提示 */
  appName?: string;
  /** 归属当前应用的工作空间模板列表（应用→工作空间级） */
  appTemplates?: AppWorkspaceTemplate[];
  /** 当前选中的应用版本 ID（用于高亮两级菜单中的版本） */
  selectedVersionId?: string;
  /** 工作空间模板加载中标记 */
  loadingAppTemplates?: boolean;
  /** 工作空间版本加载中标记 */
  loadingAppVersions?: boolean;
  /** 「+新增版本」提交中标记（父组件控制 WorkbenchFooter 弹窗按钮的禁用与文案） */
  creatingVersion?: boolean;
  /** 当前个人工作区正在拉取远端；与提交、推送及其它用户无关。 */
  pullingPersonalWorkspace?: boolean;
  /** 是否允许当前个人工作区执行普通文件写操作 */
  canWrite?: boolean;
  /** 是否允许修改 Git index、回退、提交或发布；体验区与普通文件写权限分离。 */
  canMutateGit?: boolean;
  /** 是否允许编辑应用级 Agent/Skill/Rules/Templates 配置 */
  canManageAgentConfig?: boolean;
  /** 是否允许编辑公共 Git 中的 Agent/Skill 配置（仅超级管理员） */
  canManagePublicConfig?: boolean;
  /** 后端 base url，透传给 AgentConfigPanel/GitChangesPanel */
  apiBaseUrl?: string;
  /** 当前用户 OpenCode 进程绑定服务器，也用于公共个人 worktree 自动选服。 */
  routeLinuxServerId?: string;
  /** 当前用户 OpenCode 进程归属是否已完成查询。 */
  routeLinuxServerResolved?: boolean;
  /** 初始化进程后，后端已准备完成的公共个人 worktree 精确挂载请求。 */
  publicWorktreeMountRequest?: PublicWorktreeMountRequest | null;
  /** 当前运行态 Workspace ID，透传给 AgentConfigPanel */
  workspaceId?: string;
  /** 应用 Agent 配置使用当前版本的个人 worktree，与普通 workspace 文件共用 Git 根。 */
  agentConfigWorkspaceId?: string;
  /** 当前默认个人工作区 ID，透传给 GitChangesPanel 用于提交并推送 */
  personalWorkspaceId?: string;
  /** 本次个人拉取被本地文件阻止；透传给 Diff 区展示具体文件。 */
  personalPullBlockingFiles?: import("@test-agent/shared-types").WorkspaceGitUpdateBlocker[];
  /** 当前默认个人 worktree 分支，透传给底部工作空间切换入口展示 */
  personalWorkspaceBranch?: string;
  /** Agent 配置保存修订号；变化时通知 GitChangesPanel 立即重新统计变更。 */
  agentConfigRevision?: number;
  /** 当前正在手动重载的 Agent 运行态作用域。 */
  personalRuntimeReloading?: "PUBLIC" | "WORKSPACE" | null;
  /** 运行中任务不允许 dispose。 */
  runtimeBusy?: boolean;
  /** 是否显示超级管理员服务器工作空间切换入口 */
  showServerWorkspaceSwitch?: boolean;
  /** 是否显示当前应用的引用配置入口；普通成员也可只读查看自动化引用。 */
  showReferenceConfiguration?: boolean;
  /** 搜索结果列表 */
  searchResults?: FileSearchResult[];
  /** 搜索加载中 */
  searchLoading?: boolean;
  /** 搜索关键字 */
  searchKeyword?: string;
  /** 文件树面板内错误（根目录加载失败时不覆盖全局反馈） */
  fileTreeError?: string | null;
  /** 源码快照模式只关闭 Git/Agent 发布能力，普通文件 WebSocket 写入继续开放。 */
  workspaceKind?: SelectedWorkspaceKind;
  appSourceContext?: AppSourceWorkspaceContext | null;
  /** 当前应用源码版本库状态，供底部统一菜单直接列出并打开。 */
  appSourceRepositories?: AppSourceRepositorySummary[];
  loadingAppSourceRepositories?: boolean;
  appSourceRepositoriesError?: string | null;
}>(), {
  // 旧调用方未传该新增能力时继续继承 canWrite，不能被 Boolean prop 的缺省 false 改成只读。
  canMutateGit: undefined
});

const emit = defineEmits<{
  toggleDirectory: [path: string];
  toggleViewDirectory: [entry: WorkspaceViewEntry];
  openFile: [path: string];
  openViewFile: [entry: WorkspaceViewEntry];
  addFileContext: [path: string];
  addViewFileContext: [entry: WorkspaceViewEntry];
  openDiff: [payload: string | {
    path: string;
    source: "vcs" | "agent";
    scope?: "PUBLIC" | "WORKSPACE";
    file?: RunDiffFile;
  }];
  "changes-refreshed": [payload?: {
    paths?: string[];
    reloadOpenFiles?: boolean;
    files?: import("@test-agent/shared-types").WorkspaceGitDiffFile[];
    totalCount?: number;
  }];
  "agent-files-discarded": [payload: { scope: "PUBLIC" | "WORKSPACE"; paths: string[] }];
  "agent-config-mutated": [payload: AgentConfigMutation];
  "personal-runtime-reload": [payload: {
    scope: "PUBLIC" | "WORKSPACE";
    worktreeId?: string;
    linuxServerId?: string;
    workspaceId?: string;
  }];
  refresh: [];
  "requirement-import-complete": [payload: {
    workspaceId: string;
    requestId: string;
    result: RequirementImportResult;
  }];
  // 选择某个应用版本后由父组件切换运行态 Workspace
  selectVersion: [payload: { template: AppWorkspaceTemplate; version: AppWorkspaceVersion }];
  // 要求按需懒加载某模板下的版本列表
  loadVersions: [templateId: string];
  // 「+新增版本」弹窗确认后由父组件调用 createWorkspaceVersion。
  createVersion: [payload: { template: AppWorkspaceTemplate; version: string; branch?: string }];
  // 工作空间标题栏“更多操作”菜单中的拉取动作只处理当前用户的个人 worktree。
  pullPersonalWorkspace: [personalWorkspaceId: string];
  openAgentFile: [payload: AgentFileLoadRequest];
  openServerWorkspacePicker: [];
  openReferenceConfiguration: [];
  openAppSource: [];
  loadAppSourceRepositories: [];
  openAppSourceRepository: [repository: AppSourceRepositorySummary];
  manageAppSourceRepository: [repository: AppSourceRepositorySummary];
  returnManagedWorkspace: [];
  // 搜索事件
  search: [keyword: string];
  // 创建文件或文件夹
  createEntry: [directory: string, name: string, type: "file" | "directory"];
  // 删除文件或文件夹
  deleteEntry: [path: string, type: "file" | "directory"];
  deleteEntries: [entries: { path: string; type: "file" | "directory" }[]];
  // 通过右键菜单重命名文件或目录
  renameEntry: [path: string, name: string];
  copyEntry: [sourcePath: string, targetDirectory: string];
  copyEntries: [sourcePaths: string[], targetDirectory: string];
  moveEntry: [sourcePath: string, targetDirectory: string];
  moveEntries: [sourcePaths: string[], targetDirectory: string];
  uploadFiles: [directory: string, files: File[]];
  undoEntry: [];
  // 缓存并跳转
  cacheAndNavigate: [path: string, type: "file" | "directory"];
  cacheAndNavigateEntries: [entries: import("@test-agent/file-explorer").WorkspaceSelectionEntry[]];
  downloadEntry: [entry: FileTreeEntry];
}>();

const workspaceExpanded = ref(true);
// 文件页优先完整展示工作空间；Agents 默认收起并固定在面板底部，用户需要时再展开。
const agentsExpanded = ref(false);
const agentConfigPanelRef = ref<InstanceType<typeof AgentConfigPanel> | null>(null);
const gitChangesPanelRef = ref<InstanceType<typeof GitChangesPanel> | null>(null);
const selectedEntries = ref<import("@test-agent/file-explorer").WorkspaceSelectionEntry[]>([]);

const tab = ref<ExplorerTab>("explorer");
const totalChangedFileCount = ref<number | null>(null);
const displayedChangedFileCount = computed(() => totalChangedFileCount.value ?? props.changedFiles.length);
const managedWorkspaceMode = computed(() => (props.workspaceKind ?? "MANAGED") === "MANAGED");
const experienceWorkspaceMode = computed(() => props.workspaceKind === "EXPERIENCE");
// Git diff 文件是当前目录内路径；把当前版本所属目录下传，才能与仓库级阻塞路径做无歧义映射。
const selectedWorkspaceDirectoryPath = computed(() => {
  const selectedVersionId = props.selectedVersionId;
  if (!selectedVersionId) return undefined;
  return props.appTemplates?.find((template) =>
    template.initialVersion?.versionId === selectedVersionId
    || template.versions?.some((version) => version.versionId === selectedVersionId)
  )?.directoryPath;
});
const workspaceHeight = ref<number | null>(null);
const resizing = ref(false);
let dragStartY = 0;
let dragStartHeight = 0;

const iframeDialogVisible = ref(false);
const iframeRef = ref<HTMLIFrameElement | null>(null);
const iframeRequestId = ref("");
const fileExplorerRef = ref<InstanceType<typeof FileExplorer> | null>(null);
const workspaceMoreMenuRef = ref<HTMLDetailsElement | null>(null);

function closeWorkspaceMoreMenu() {
  if (workspaceMoreMenuRef.value) {
    workspaceMoreMenuRef.value.open = false;
  }
}

function pullCurrentPersonalWorkspace() {
  closeWorkspaceMoreMenu();
  if (!props.personalWorkspaceId || props.pullingPersonalWorkspace) return;
  emit("pullPersonalWorkspace", props.personalWorkspaceId);
}

function refreshWorkspaceFileTree() {
  closeWorkspaceMoreMenu();
  if (!props.workspaceId || props.loadingPath?.has("")) return;
  emit("refresh");
}

function openRootActions() {
  if (!props.canWrite) return;
  fileExplorerRef.value?.openRootActions();
}

// 独立 HTML 入口只加载需求导入所需的 Vue 与 backend-api，避免 iframe 再启动整套工作台。
const iframeUrl = computed(() => new URL("/workspace-requirement-import/", window.location.origin).toString());
const requirementImportAvailable = computed(() => Boolean(
  props.workspaceId && props.canWrite && managedWorkspaceMode.value
));

function openIframeDialog() {
  if (!requirementImportAvailable.value) return;
  iframeRequestId.value = typeof crypto.randomUUID === "function"
    ? crypto.randomUUID()
    : `requirement-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  iframeDialogVisible.value = true;
}

function closeIframeDialog() {
  iframeDialogVisible.value = false;
}

/** 文件树定向刷新完成后再释放 iframe 蒙版；完整成功只在此时关闭弹窗。 */
function completeRequirementImportRefresh(payload: {
  requestId: string;
  status: RequirementImportResult["status"];
  success: boolean;
}) {
  if (payload.requestId !== iframeRequestId.value) return;
  iframeRef.value?.contentWindow?.postMessage({
    type: "ITA_REQUIREMENT_IMPORT_TREE_REFRESHED",
    requestId: payload.requestId,
    success: payload.success
  }, window.location.origin);
  if (payload.success && payload.status === "SUCCEEDED") closeIframeDialog();
}

function handleIframeMessage(event: MessageEvent) {
  if (event.origin !== window.location.origin || event.source !== iframeRef.value?.contentWindow) return;
  const data = event.data as Record<string, unknown> | null;
  if (data?.type === "ITA_REQUIREMENT_IMPORT_READY" && props.workspaceId) {
    const now = new Date();
    iframeRef.value?.contentWindow?.postMessage({
      type: "ITA_REQUIREMENT_IMPORT_CONTEXT",
      workspaceId: props.workspaceId,
      defaultAppName: props.appName,
      defaultVersion: `${now.getFullYear()}年${now.getMonth() + 1}月`,
      requestId: iframeRequestId.value
    }, window.location.origin);
    return;
  }
  if (data?.type === "ITA_REQUIREMENT_IMPORT_COMPLETE" && data.requestId === iframeRequestId.value) {
    const result = data.result as RequirementImportResult | undefined;
    if (
      props.workspaceId
      && (result?.status === "SUCCEEDED" || result?.status === "PARTIAL")
    ) {
      emit("requirement-import-complete", {
        workspaceId: props.workspaceId,
        requestId: iframeRequestId.value,
        result
      });
    }
    return;
  }
  if (data?.type === "ITA_REQUIREMENT_IMPORT_AUTH_REQUIRED") {
    closeIframeDialog();
    const handler = (window as unknown as Record<string, unknown>).__handleUnauthorized;
    if (typeof handler === "function") handler();
  }
}

onMounted(() => {
  window.addEventListener("message", handleIframeMessage);
});

onUnmounted(() => {
  window.removeEventListener("message", handleIframeMessage);
});

const workspaceStyle = computed(() => {
  if (!workspaceExpanded.value) return {};
  if (!agentsExpanded.value) return { flex: "1", minHeight: "0" };
  return {
    height: workspaceHeight.value ? `${workspaceHeight.value}px` : "50%",
    flex: "0 0 auto"
  };
});

function onResizeStart(event: MouseEvent) {
  resizing.value = true;
  dragStartY = event.clientY;
  const el = document.querySelector(".figma-fe-section-workspace") as HTMLElement;
  dragStartHeight = el ? el.offsetHeight : 300;
  
  document.addEventListener("mousemove", onResizeMove);
  document.addEventListener("mouseup", onResizeEnd);
  document.body.style.cursor = "row-resize";
  document.body.style.userSelect = "none";
}

function onResizeMove(event: MouseEvent) {
  if (!resizing.value) return;
  const deltaY = event.clientY - dragStartY;
  workspaceHeight.value = Math.max(100, dragStartHeight + deltaY);
}

function onResizeEnd() {
  resizing.value = false;
  document.removeEventListener("mousemove", onResizeMove);
  document.removeEventListener("mouseup", onResizeEnd);
  document.body.style.cursor = "";
  document.body.style.userSelect = "";
}

function refreshAgents() {
  if (!managedWorkspaceMode.value) return;
  agentConfigPanelRef.value?.refreshAll();
}

function refreshChanges() {
  if (props.workspaceKind === "APP_SOURCE" || props.workspaceKind === "LOCAL_CLIENT") return;
  gitChangesPanelRef.value?.refreshChanges();
}

/** Git 面板常驻挂载，快捷弹框通过 Teleport 展示，成功时因此可以保留当前工作区。 */
async function openAgentQuickCommit(request: AgentQuickCommitRequest) {
  await gitChangesPanelRef.value?.openQuickAgentCommit(request);
}

/** 只有快捷暂存、提交或推送失败时才进入 Diff，保留原有排障入口。 */
function handleAgentQuickCommitFailed() {
  tab.value = "changes";
}

/** 回退 Agent 删除后先复用目录全量刷新恢复树节点，再让工作台重读对应已打开标签。 */
async function handleAgentFilesDiscarded(payload: { scope: "PUBLIC" | "WORKSPACE"; paths: string[] }) {
  try {
    await agentConfigPanelRef.value?.refreshAll(false);
  } finally {
    emit("agent-files-discarded", payload);
  }
}

const DIFF_AUTO_REFRESH_INTERVAL_MS = 5000;
let diffAutoRefreshTimer: number | undefined;

function stopDiffAutoRefresh() {
  if (diffAutoRefreshTimer !== undefined) {
    window.clearInterval(diffAutoRefreshTimer);
    diffAutoRefreshTimer = undefined;
  }
}

watch(tab, (nextTab) => {
  stopDiffAutoRefresh();
  if (nextTab !== "changes") return;
  // Git Diff 没有服务端推送事件；进入面板先查一次，停留期间持续复用同一刷新程序感知磁盘变化。
  refreshChanges();
  diffAutoRefreshTimer = window.setInterval(refreshChanges, DIFF_AUTO_REFRESH_INTERVAL_MS);
});

watch(() => props.workspaceKind, (kind) => {
  if ((kind === "APP_SOURCE" || kind === "LOCAL_CLIENT") && tab.value === "changes") {
    tab.value = "explorer";
  }
});

onUnmounted(stopDiffAutoRefresh);

function refreshAll() {
  refreshAgents();
  refreshChanges();
}

function handleChangesRefreshed(payload?: {
  paths?: string[];
  reloadOpenFiles?: boolean;
  files?: import("@test-agent/shared-types").WorkspaceGitDiffFile[];
  totalCount?: number;
}) {
  if (payload?.totalCount !== undefined) {
    totalChangedFileCount.value = payload.totalCount;
  }
  emit("changes-refreshed", payload);
}

watch(
  () => props.workspaceId,
  () => {
    // 切换工作区时先回退到已知 workspace 数量，等待三类 diff 刷新后再展示新总数。
    totalChangedFileCount.value = null;
  }
);

defineExpose({
  refreshAll,
  refreshChanges,
  completeRequirementImportRefresh
});
</script>

<template>
  <div class="figma-file-explorer">
    <!-- Tabbar is at the very top of the entire sidebar pane -->
    <div class="ta-icon-tabbar" role="tablist" aria-label="工作区面板">
      <button
        type="button"
        :class="['ta-icon-tab', tab === 'explorer' && 'is-active']"
        title="文件树"
        aria-label="文件树"
        @click="tab = 'explorer'"
      >
        <FolderTree class="h-4 w-4 figma-fe-tab-icon--explorer" :stroke-width="1.5" />
      </button>
      <button
        type="button"
        :class="['ta-icon-tab', tab === 'search' && 'is-active']"
        title="搜索"
        aria-label="搜索"
        @click="tab = 'search'"
      >
        <Search class="h-4 w-4 figma-fe-tab-icon--search" :stroke-width="1.5" />
      </button>
      <button
        v-if="workspaceKind !== 'APP_SOURCE' && workspaceKind !== 'LOCAL_CLIENT'"
        type="button"
        :class="['ta-icon-tab', tab === 'changes' && 'is-active']"
        title="变更"
        aria-label="变更"
        @click="tab = 'changes'"
      >
        <GitBranch class="h-4 w-4 figma-fe-tab-icon--changes" :stroke-width="1.5" />
        <span v-if="displayedChangedFileCount" class="ml-1 text-[10px]">{{ displayedChangedFileCount }}</span>
      </button>
    </div>

    <div v-if="workspaceKind === 'APP_SOURCE' && appSourceContext" class="app-source-mode-banner" role="status">
      <div>
        <strong>源码快照</strong>
        <span>无 Git</span>
        <span>到期时间 {{ appSourceContext.expiresAt }}</span>
        <span v-if="appSourceContext.purpose === 'TEAM'">同机成员共享</span>
      </div>
      <button type="button" aria-label="返回应用工作区" @click="emit('returnManagedWorkspace')">返回应用工作区</button>
    </div>
    <div v-else-if="experienceWorkspaceMode" class="experience-mode-banner" role="status">
      <strong>体验工作区</strong>
      <span>多人共享，可能同时修改相同文件</span>
      <span>本地 Git 可提交，不提供推送</span>
      <span class="is-warning">请勿存放敏感数据</span>
    </div>

    <!-- Sibling collapsible sections under the body -->
    <div class="figma-fe-body">
      <GitChangesPanel
        v-if="workspaceKind !== 'APP_SOURCE' && workspaceKind !== 'LOCAL_CLIENT'"
        v-show="tab === 'changes'"
        ref="gitChangesPanelRef"
        :workspace-id="workspaceId"
        :agent-config-workspace-id="agentConfigWorkspaceId"
        :personal-workspace-id="personalWorkspaceId"
        :personal-pull-blocking-files="personalPullBlockingFiles"
        :personal-workspace-branch="personalWorkspaceBranch"
        :workspace-directory-path="selectedWorkspaceDirectoryPath"
        :agent-config-revision="agentConfigRevision"
        :api-base-url="apiBaseUrl"
        :route-linux-server-id="routeLinuxServerId"
        :can-write="!!canWrite"
        :can-mutate-git="canMutateGit ?? !!canWrite"
        :local-only-git="experienceWorkspaceMode"
        :include-agent-scopes="managedWorkspaceMode"
        :can-manage-agent-config="managedWorkspaceMode && (canManageAgentConfig ?? !!canWrite)"
        :can-manage-public-config="managedWorkspaceMode && (canManagePublicConfig ?? !!canWrite)"
        @open-diff="(payload) => emit('openDiff', payload)"
        @changes-refreshed="handleChangesRefreshed"
        @agent-files-discarded="handleAgentFilesDiscarded"
        @quick-agent-commit-failed="handleAgentQuickCommitFailed"
      />
      <template v-if="tab !== 'changes'">
        <!-- Section 1: 应用工作空间 -->
        <div
          class="figma-fe-section figma-fe-section-workspace"
          :class="{ 'is-expanded': workspaceExpanded }"
          :style="workspaceStyle"
        >
          <div class="figma-fe-section-header">
            <button
              type="button"
              class="figma-fe-section-header-trigger"
              @click="workspaceExpanded = !workspaceExpanded"
            >
              <ChevronDown v-if="workspaceExpanded" class="h-3.5 w-3.5" :stroke-width="1.5" />
              <ChevronRight v-else class="h-3.5 w-3.5" :stroke-width="1.5" />
              <span class="figma-fe-section-title" :title="workspaceName">工作空间</span>
              <el-tooltip
                v-if="personalWorkspaceBranch"
                :content="`当前 worktree: ${personalWorkspaceBranch}`"
                placement="top"
                :show-after="100"
              >
                <span class="figma-fe-section-worktree">
                  / worktree: {{ personalWorkspaceBranch }}
                </span>
              </el-tooltip>
            </button>
            <div class="figma-fe-section-actions" v-if="workspaceExpanded">
              <button
                v-if="tab === 'explorer' && selectedEntries.length > 0"
                type="button"
                class="figma-fe-section-action-btn figma-fe-plane-multi-btn"
                :title="`缓存并跳转选中的 ${selectedEntries.length} 个文件`"
                aria-label="缓存并跳转选中的文件"
                :disabled="!workspaceId"
                @click="emit('cacheAndNavigateEntries', selectedEntries)"
              >
                <Plane class="h-3.5 w-3.5" :stroke-width="1.5" />
                <span class="figma-fe-plane-badge">{{ selectedEntries.length }}</span>
              </button>
              <button
                v-if="tab === 'explorer' && canWrite"
                type="button"
                class="figma-fe-section-action-btn"
                title="新建或上传到工作区根目录"
                aria-label="新建或上传到工作区根目录"
                :disabled="!workspaceId"
                @click="openRootActions"
              >
                <Plus class="h-3.5 w-3.5 figma-fe-action-icon--plus" :stroke-width="1.5" />
              </button>
              <button
                v-if="tab === 'explorer' && managedWorkspaceMode"
                type="button"
                class="figma-fe-section-action-btn"
                :title="requirementImportAvailable ? '从 TCDS 导入需求' : '当前工作区不可导入需求'"
                aria-label="从 TCDS 导入需求"
                data-onboarding="workspace-reference"
                :disabled="!requirementImportAvailable"
                @click="openIframeDialog"
              >
                <Globe class="h-3.5 w-3.5 figma-fe-action-icon--globe" :stroke-width="1.5" />
              </button>
              <details v-if="tab === 'explorer'" ref="workspaceMoreMenuRef" class="figma-fe-more-menu">
                <summary
                  class="figma-fe-section-action-btn"
                  title="更多工作空间操作"
                  aria-label="更多工作空间操作"
                >
                  <MoreHorizontal class="h-3.5 w-3.5 figma-fe-action-icon--more" :stroke-width="1.5" />
                </summary>
                <div class="figma-fe-more-menu-dropdown">
                  <button
                    type="button"
                    class="figma-fe-more-menu-item"
                    aria-label="刷新文件树"
                    :disabled="!workspaceId"
                    @click="refreshWorkspaceFileTree"
                  >
                    <RefreshCw
                      class="h-3.5 w-3.5 figma-fe-action-icon--refresh"
                      :class="{ 'animate-spin': loadingPath?.has('') }"
                      :stroke-width="1.5"
                    />
                    <span>刷新文件树</span>
                  </button>
                  <button
                    v-if="managedWorkspaceMode"
                    type="button"
                    class="figma-fe-more-menu-item"
                    aria-label="拉取远程"
                    :disabled="!personalWorkspaceId || pullingPersonalWorkspace"
                    @click="pullCurrentPersonalWorkspace"
                  >
                    <CloudDownload
                      class="h-3.5 w-3.5 figma-fe-action-icon--pull"
                      :class="{ 'animate-pulse': pullingPersonalWorkspace }"
                      :stroke-width="1.5"
                    />
                    <span>{{ pullingPersonalWorkspace ? "正在拉取远程" : "拉取远程" }}</span>
                  </button>
                </div>
              </details>
            </div>
          </div>
          <div v-show="workspaceExpanded" class="figma-fe-section-content">
            <!-- 文件树面板内错误：根目录加载失败时显示，不覆盖全局反馈 -->
            <div v-if="fileTreeError" class="figma-fe-error-banner">
              <span class="figma-fe-error-text">{{ fileTreeError }}</span>
              <button type="button" class="figma-fe-error-retry" @click="emit('refresh')">重试</button>
            </div>
            <div v-else-if="!workspaceId" class="figma-fe-empty-workspace">
              当前应用尚未切换到可用工作区。
            </div>
            <FileExplorer
              v-else
              ref="fileExplorerRef"
              :key="workspaceId"
              :workspace-name="workspaceName"
              :entries-by-directory="entriesByDirectory"
              :expanded-directories="expandedDirectories"
              :active-path="activePath"
              :changed-files="changedFiles"
              :loading-path="loadingPath"
              :hide-header="true"
              :hide-tabbar="true"
              :can-write="!!canWrite"
              :can-undo="canUndo"
              :active-tab="tab"
              :search-results="searchResults"
              :search-loading="searchLoading"
              :search-keyword="searchKeyword"
              :downloading-entry-id="downloadingEntryId"
              @toggle-directory="emit('toggleDirectory', $event)"
              @toggle-view-directory="emit('toggleViewDirectory', $event)"
              @open-file="emit('openFile', $event)"
              @open-view-file="emit('openViewFile', $event)"
              @add-file-context="emit('addFileContext', $event)"
              @add-view-file-context="emit('addViewFileContext', $event)"
              @open-diff="emit('openDiff', $event)"
              @refresh="emit('refresh')"
              @search="emit('search', $event)"
              @create-entry="(directory, name, type) => emit('createEntry', directory, name, type)"
              @delete-entry="(path, type) => emit('deleteEntry', path, type)"
              @delete-entries="emit('deleteEntries', $event)"
              @rename-entry="(path, name) => emit('renameEntry', path, name)"
              @copy-entry="(sourcePath, targetDirectory) => emit('copyEntry', sourcePath, targetDirectory)"
              @copy-entries="(sourcePaths, targetDirectory) => emit('copyEntries', sourcePaths, targetDirectory)"
              @move-entry="(sourcePath, targetDirectory) => emit('moveEntry', sourcePath, targetDirectory)"
              @move-entries="(sourcePaths, targetDirectory) => emit('moveEntries', sourcePaths, targetDirectory)"
              @upload-files="(directory, files) => emit('uploadFiles', directory, files)"
              @undo-entry="emit('undoEntry')"
              @cache-and-navigate="(path, type) => emit('cacheAndNavigate', path, type)"
              @cache-and-navigate-entries="emit('cacheAndNavigateEntries', $event)"
              @selection-change="selectedEntries = $event"
              @download-entry="emit('downloadEntry', $event)"
            />
          </div>
        </div>

        <!-- Resizer divider: only show if both sections are expanded -->
        <div
          v-if="managedWorkspaceMode && workspaceExpanded && agentsExpanded"
          class="figma-fe-resize-handle"
          @mousedown="onResizeStart"
          role="separator"
          aria-orientation="horizontal"
        />

        <!-- Section 2: agents -->
        <div v-if="managedWorkspaceMode" class="figma-fe-section" :class="{ 'is-expanded': agentsExpanded }">
          <div class="figma-fe-section-header">
            <button
              type="button"
              class="figma-fe-section-header-trigger"
              @click="agentsExpanded = !agentsExpanded"
            >
              <ChevronDown v-if="agentsExpanded" class="h-3.5 w-3.5" :stroke-width="1.5" />
              <ChevronRight v-else class="h-3.5 w-3.5" :stroke-width="1.5" />
              <span class="figma-fe-section-title">Agents</span>
            </button>
            <div class="figma-fe-section-actions" v-if="agentsExpanded">
               <button
                type="button"
                class="figma-fe-section-action-btn"
                title="刷新"
                aria-label="刷新"
                :disabled="agentConfigPanelRef?.busy"
                @click="refreshAgents"
              >
                <RefreshCw class="h-3.5 w-3.5" :class="{ 'animate-spin': agentConfigPanelRef?.busy }" :stroke-width="1.5" />
              </button>
            </div>
          </div>
          <div v-show="agentsExpanded" class="figma-fe-section-content">
            <AgentConfigPanel
              ref="agentConfigPanelRef"
              :base-url="apiBaseUrl ?? ''"
              :route-linux-server-id="routeLinuxServerId"
              :route-linux-server-resolved="routeLinuxServerResolved"
              :public-worktree-mount-request="publicWorktreeMountRequest"
              :workspace-id="agentConfigWorkspaceId"
              :can-write="canManagePublicConfig ?? !!canWrite"
              :can-manage-workspace-config="canManageAgentConfig ?? !!canWrite"
              :personal-runtime-reloading="personalRuntimeReloading"
              :runtime-busy="runtimeBusy"
              :hide-header="true"
              :hide-git-ops="true"
              :active-path="activePath"
              @open-file="emit('openAgentFile', $event)"
              @files-mutated="emit('agent-config-mutated', $event)"
              @personal-runtime-reload="emit('personal-runtime-reload', $event)"
              @request-git-commit="openAgentQuickCommit"
            />
          </div>
        </div>
      </template>
    </div>
    <WorkbenchFooter
      :app-name="appName"
      :templates="appTemplates"
      :selected-version-id="selectedVersionId"
      :personal-workspace-branch="personalWorkspaceBranch"
      :loading-templates="loadingAppTemplates"
      :loading-versions="loadingAppVersions"
      :creating-version="creatingVersion"
      :show-server-workspace-switch="showServerWorkspaceSwitch"
      :show-reference-configuration="showReferenceConfiguration"
      :show-app-source="Boolean(appName)"
      :workspace-kind="workspaceKind"
      :app-source-repositories="appSourceRepositories"
      :loading-app-source-repositories="loadingAppSourceRepositories"
      :app-source-repositories-error="appSourceRepositoriesError"
      :selected-app-source-repository-id="appSourceContext?.repositoryId"
      @select-version="(payload) => emit('selectVersion', payload)"
      @load-versions="(templateId: string) => emit('loadVersions', templateId)"
      @create-version="(payload) => emit('createVersion', payload)"
      @open-server-workspace-picker="emit('openServerWorkspacePicker')"
      @open-reference-configuration="emit('openReferenceConfiguration')"
      @open-app-source="emit('openAppSource')"
      @load-app-source-repositories="emit('loadAppSourceRepositories')"
      @open-app-source-repository="emit('openAppSourceRepository', $event)"
      @manage-app-source-repository="emit('manageAppSourceRepository', $event)"
      @return-managed-workspace="emit('returnManagedWorkspace')"
    />

    <Teleport to="body">
      <div v-if="iframeDialogVisible" class="figma-fe-iframe-overlay" @click="closeIframeDialog">
        <div class="figma-fe-iframe-dialog" @click.stop>
          <div class="figma-fe-iframe-header">
            <span class="figma-fe-iframe-title">导入 TCDS 需求</span>
            <button
              type="button"
              class="figma-fe-iframe-close"
              title="关闭"
              aria-label="关闭"
              @click="closeIframeDialog"
            >
              <span class="figma-fe-iframe-close-icon">×</span>
            </button>
          </div>
          <div class="figma-fe-iframe-content">
            <iframe
              ref="iframeRef"
              :src="iframeUrl"
              class="figma-fe-iframe"
              title="导入 TCDS 需求"
              sandbox="allow-scripts allow-same-origin allow-forms"
            />
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.app-source-mode-banner {
  display: flex;
  min-height: 36px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 8px;
  border-bottom: 1px solid var(--ta-border);
  background: #f5f3ff;
  color: #5b21b6;
  font-size: 10px;
}

.app-source-mode-banner > div {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  align-items: center;
  gap: 5px 9px;
}

.app-source-mode-banner strong { color: #4c1d95; font-size: 11px; }
.app-source-mode-banner button {
  flex-shrink: 0;
  border: 1px solid #c4b5fd;
  border-radius: 4px;
  background: #fff;
  padding: 3px 6px;
  color: #5b21b6;
  font-size: 10px;
  cursor: pointer;
}

.experience-mode-banner {
  display: flex;
  flex-wrap: wrap;
  gap: 5px 8px;
  align-items: center;
  padding: 8px 10px;
  border-bottom: 1px solid #cddbec;
  background: #f3f7fc;
  color: #52647b;
  font-size: 11px;
  line-height: 1.45;
}

.experience-mode-banner strong {
  color: #294f82;
  font-size: 12px;
}

.experience-mode-banner span + span::before {
  margin-right: 8px;
  color: #a8b5c5;
  content: "·";
}

.experience-mode-banner .is-warning {
  color: #8a5a13;
  font-weight: 600;
}

.figma-file-explorer {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background: var(--ta-tree-bg);
  color: var(--ta-tree-text);
  font-family: var(--ta-tree-font-family);
}

.figma-file-explorer > .ta-icon-tabbar {
  padding-right: 36px; /* Make space for the absolutely-positioned sidebar toggle button */
  border-radius: 0;
  background: var(--ta-shell-header, #ffffff);
  border-bottom: 1px solid var(--ta-shell-border, #e5e7eb);
}

.figma-fe-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: var(--ta-tree-bg);
}

.figma-fe-section {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.figma-fe-section.is-expanded {
  flex: 1;
}

.figma-fe-section:not(.is-expanded) {
  flex: 0 0 auto;
}

/* Border separator when resizer is NOT present */
.figma-fe-section + .figma-fe-section .figma-fe-section-header {
  border-top: 1px solid var(--ta-tree-border);
}

.figma-fe-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 24px;
  padding: 0 6px;
  background: var(--ta-tree-bg);
  user-select: none;
  flex-shrink: 0;
}

.figma-fe-section-header-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
  padding: 0;
  color: var(--ta-tree-text);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  min-width: 0;
  flex: 1;
  text-align: left;
}

.figma-fe-section-header-trigger:hover {
  color: var(--ta-tree-text);
}

.figma-fe-section-title {
  flex-shrink: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.figma-fe-section-worktree {
  font-size: 11px;
  color: var(--ta-tree-muted);
  font-weight: normal;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
  flex: 1;
}

.figma-fe-section-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.figma-fe-section-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--ta-tree-muted);
  cursor: pointer;
  transition: background-color 0.1s, color 0.1s;
}

.figma-fe-section-action-btn:hover {
  background: var(--ta-tree-hover);
  color: var(--ta-tree-text);
}

.figma-fe-section-action-btn:active {
  background: var(--ta-tree-active);
}

.figma-fe-section-action-btn:disabled {
  opacity: 0.5;
  pointer-events: none;
}

.figma-fe-plane-multi-btn {
  position: relative;
  color: #d97706;
}

.figma-fe-plane-multi-btn:hover {
  background: #fef3c7;
  color: #b45309;
}

.figma-fe-plane-badge {
  position: absolute;
  top: -2px;
  right: -2px;
  min-width: 14px;
  height: 14px;
  padding: 0 3px;
  border-radius: 7px;
  background: #d97706;
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  line-height: 14px;
  text-align: center;
  pointer-events: none;
}

.figma-fe-more-menu {
  position: relative;
  display: inline-flex;
}

.figma-fe-more-menu > summary {
  list-style: none;
}

.figma-fe-more-menu > summary::-webkit-details-marker {
  display: none;
}

.figma-fe-more-menu[open] > summary {
  background: var(--ta-tree-hover);
  color: var(--ta-tree-text);
}

.figma-fe-more-menu-dropdown {
  position: absolute;
  top: calc(100% + 4px);
  right: 0;
  z-index: 50;
  display: flex;
  min-width: 148px;
  flex-direction: column;
  gap: 2px;
  padding: 4px;
  border: 1px solid var(--ta-tree-border);
  border-radius: 6px;
  background: var(--ta-tree-bg);
  box-shadow: 0 6px 18px rgb(0 0 0 / 14%);
}

.figma-fe-more-menu-item {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--ta-tree-text);
  font-size: 12px;
  text-align: left;
  white-space: nowrap;
  cursor: pointer;
}

.figma-fe-more-menu-item:hover:not(:disabled) {
  background: var(--ta-tree-hover);
}

.figma-fe-more-menu-item:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.figma-fe-section-content {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.figma-fe-resize-handle {
  height: 4px;
  flex-shrink: 0;
  cursor: row-resize;
  position: relative;
  z-index: 5;
  background: transparent;
  transition: background-color 0.14s ease;
}

.figma-fe-resize-handle::after {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  height: 1px;
  margin-top: -0.5px;
  background: var(--ta-tree-border);
  transition: background-color 0.14s ease;
}

.figma-fe-resize-handle:hover {
  background: rgba(0, 0, 0, 0.04);
}

.figma-fe-resize-handle:hover::after {
  background: var(--ta-tree-border-strong);
}

.figma-fe-resize-handle:active {
  background: rgba(0, 0, 0, 0.06);
}

.figma-fe-body :deep(.bg-\[var\(--ta-panel\)\]) {
  background: var(--ta-tree-bg);
}

/* 文件树面板内错误提示 */
.figma-fe-error-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 12px;
  background: #fef2f2;
  border-bottom: 1px solid #fecaca;
  flex-shrink: 0;
}

.figma-fe-error-text {
  font-size: 12px;
  color: #dc2626;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.figma-fe-empty-workspace {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  min-height: 96px;
  padding: 16px;
  color: var(--ta-tree-muted);
  font-size: 12px;
  text-align: center;
}

.figma-fe-error-retry {
  flex-shrink: 0;
  padding: 2px 8px;
  font-size: 12px;
  color: #dc2626;
  background: transparent;
  border: 1px solid #fecaca;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.14s ease;
}

.figma-fe-error-retry:hover {
  background: #fee2e2;
}

.figma-fe-iframe-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  animation: figma-fe-iframe-fade-in 0.2s ease;
}

@keyframes figma-fe-iframe-fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

.figma-fe-iframe-dialog {
  background: #ffffff;
  border-radius: 8px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.16);
  width: 90%;
  max-width: 1000px;
  height: 80%;
  max-height: 600px;
  display: flex;
  flex-direction: column;
  animation: figma-fe-iframe-slide-up 0.2s ease;
}

@keyframes figma-fe-iframe-slide-up {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.figma-fe-iframe-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}

.figma-fe-iframe-title {
  font-size: 14px;
  font-weight: 600;
  color: #18181b;
}

.figma-fe-iframe-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.1s;
}

.figma-fe-iframe-close:hover {
  background: #f3f4f6;
}

.figma-fe-iframe-close-icon {
  font-size: 20px;
  color: #6b7280;
  line-height: 1;
}

.figma-fe-iframe-content {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.figma-fe-iframe {
  width: 100%;
  height: 100%;
  border: none;
}
</style>
