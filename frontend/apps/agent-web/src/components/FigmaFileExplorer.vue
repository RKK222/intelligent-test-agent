<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { FileExplorer, type FileExplorerProps, type ExplorerTab } from "@test-agent/file-explorer";
import type { FileContent, FileSearchResult } from "@test-agent/shared-types";
import type { AppWorkspaceTemplate, AppWorkspaceVersion } from "./WorkbenchFooter.vue";
import WorkbenchFooter from "./WorkbenchFooter.vue";
import AgentConfigPanel from "./AgentConfigPanel.vue";
import GitChangesPanel from "./GitChangesPanel.vue";
import { ChevronDown, ChevronRight, FolderTree, GitBranch, Globe, Plus, RefreshCw, Search } from "lucide-vue-next";

const props = defineProps<FileExplorerProps & {
  workspaceRootPath?: string;
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
  /** 是否允许当前个人工作区执行普通文件写操作 */
  canWrite?: boolean;
  /** 是否允许编辑应用级 Agent/Skill/Rules/Templates 配置 */
  canManageAgentConfig?: boolean;
  /** 是否允许编辑公共 Git 中的 Agent/Skill 配置（仅超级管理员） */
  canManagePublicConfig?: boolean;
  /** 后端 base url，透传给 AgentConfigPanel/GitChangesPanel */
  apiBaseUrl?: string;
  /** 当前运行态 Workspace ID，透传给 AgentConfigPanel */
  workspaceId?: string;
  /** 应用 Agent 配置固定使用应用 feature 工作区，不随个人 worktree 切换。 */
  agentConfigWorkspaceId?: string;
  /** 当前默认个人工作区 ID，透传给 GitChangesPanel 用于提交并推送 */
  personalWorkspaceId?: string;
  /** 当前默认个人 worktree 分支，透传给底部工作空间切换入口展示 */
  personalWorkspaceBranch?: string;
  /** 是否显示超级管理员服务器工作空间切换入口 */
  showServerWorkspaceSwitch?: boolean;
  /** 搜索结果列表 */
  searchResults?: FileSearchResult[];
  /** 搜索加载中 */
  searchLoading?: boolean;
  /** 搜索关键字 */
  searchKeyword?: string;
  /** 文件树面板内错误（根目录加载失败时不覆盖全局反馈） */
  fileTreeError?: string | null;
  /** 当前用户 ID，用于拼接 iframe URL */
  userId?: string;
  /** 后端 Java 服务器 IP 地址，用于构建 iframe URL */
  backendJavaServerIp?: string;
}>();

const emit = defineEmits<{
  toggleDirectory: [path: string];
  openFile: [path: string];
  addFileContext: [path: string];
  openDiff: [payload: string | { path: string; source: "vcs" | "agent"; scope?: "PUBLIC" | "WORKSPACE" }];
  "changes-refreshed": [payload?: {
    paths?: string[];
    reloadOpenFiles?: boolean;
    files?: import("@test-agent/shared-types").WorkspaceGitDiffFile[];
  }];
  refresh: [];
  // 选择某个应用版本后由父组件切换运行态 Workspace
  selectVersion: [payload: { template: AppWorkspaceTemplate; version: AppWorkspaceVersion }];
  // 要求按需懒加载某模板下的版本列表
  loadVersions: [templateId: string];
  // 「+新增版本」弹窗确认后由父组件调用 createWorkspaceVersion。
  createVersion: [payload: { template: AppWorkspaceTemplate; version: string; branch?: string }];
  openAgentFile: [payload: { scope: "PUBLIC" | "WORKSPACE"; path: string; content: FileContent; readonly: boolean; worktreeId?: string | null; linuxServerId?: string | null }];
  openServerWorkspacePicker: [];
  // 搜索事件
  search: [keyword: string];
  // 创建文件或文件夹
  createEntry: [directory: string, name: string, type: "file" | "directory"];
  // 删除文件或文件夹
  deleteEntry: [path: string, type: "file" | "directory"];
  // 双击文件或目录后重命名
  renameEntry: [path: string, name: string];
  copyEntry: [sourcePath: string, targetDirectory: string];
  moveEntry: [sourcePath: string, targetDirectory: string];
  uploadFiles: [directory: string, files: File[]];
  undoEntry: [];
  // 缓存并跳转
  cacheAndNavigate: [path: string, type: "file" | "directory"];
}>();

const workspaceExpanded = ref(true);
const agentsExpanded = ref(true);
const agentConfigPanelRef = ref<InstanceType<typeof AgentConfigPanel> | null>(null);
const gitChangesPanelRef = ref<InstanceType<typeof GitChangesPanel> | null>(null);

const tab = ref<ExplorerTab>("explorer");
const workspaceHeight = ref<number | null>(null);
const resizing = ref(false);
let dragStartY = 0;
let dragStartHeight = 0;

const iframeDialogVisible = ref(false);
const iframeRef = ref<HTMLIFrameElement | null>(null);
const fileExplorerRef = ref<InstanceType<typeof FileExplorer> | null>(null);

function openRootActions() {
  if (!props.canWrite) return;
  fileExplorerRef.value?.openRootActions();
}

const iframeUrl = computed(() => {
  const baseUrl = import.meta.env.VITE_IFRAME_URL ?? "";
  if (!baseUrl) return "";
  
  const now = new Date();
  const version = `${now.getFullYear()}年${now.getMonth() + 1}月`;
  
  const params = new URLSearchParams();
  if (props.userId) {
    params.append("userId", props.userId);
  }
  if (props.appName) {
    params.append("appName", props.appName);
  }
  params.append("version", version);
  if (props.workspaceRootPath) {
    params.append("workspacePath", props.workspaceRootPath);
  }

  let backendUrl = props.backendJavaServerIp ?? null;
  if (!backendUrl) {
    backendUrl = import.meta.env.VITE_TEST_AGENT_API_BASE_URL ?? null;
  }
  if(backendUrl){
    params.append("backendUrl", backendUrl);
  }
  
  const paramsStr = params.toString();
  if (!paramsStr) return baseUrl;
  
  const hashIndex = baseUrl.indexOf("#");
  if (hashIndex === -1) {
    const url = new URL(baseUrl);
    url.search = paramsStr;
    return url.toString();
  }
  
  const baseWithoutHash = baseUrl.substring(0, hashIndex);
  let hash = baseUrl.substring(hashIndex);
  const trimmedHash = hash.replace(/\?$/, "");
  const hashHasQuery = trimmedHash.includes("?");
  const separator = hashHasQuery ? "&" : "?";
  return `${baseWithoutHash}${trimmedHash}${separator}${paramsStr}`;
});

function openIframeDialog() {
  iframeDialogVisible.value = true;
}

function closeIframeDialog() {
  iframeDialogVisible.value = false;
}

function handleIframeMessage(event: MessageEvent) {
  try {
    const data = event.data;
    if (data && typeof data.type === "string") {
      console.log("[FigmaFileExplorer] Received postMessage:", data);
      
      if (data.type === "FUNC_DISPATCH" && data.payload === "workspace_reload") {
        console.log('更新工作空间：',data)
        emit("refresh");
        closeIframeDialog();
      }
    }
  } catch (e) {
    console.error("[FigmaFileExplorer] Failed to parse postMessage:", e);
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
  agentConfigPanelRef.value?.refreshAll();
}

function refreshChanges() {
  gitChangesPanelRef.value?.refreshChanges();
}

function refreshAll() {
  refreshAgents();
  refreshChanges();
}

defineExpose({
  refreshAll,
  refreshChanges
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
        <FolderTree class="h-4 w-4" :stroke-width="1.5" />
      </button>
      <button
        type="button"
        :class="['ta-icon-tab', tab === 'search' && 'is-active']"
        title="搜索"
        aria-label="搜索"
        @click="tab = 'search'"
      >
        <Search class="h-4 w-4" :stroke-width="1.5" />
      </button>
      <button
        type="button"
        :class="['ta-icon-tab', tab === 'changes' && 'is-active']"
        title="变更"
        aria-label="变更"
        @click="tab = 'changes'"
      >
        <GitBranch class="h-4 w-4" :stroke-width="1.5" />
        <span v-if="changedFiles.length" class="ml-1 text-[10px]">{{ changedFiles.length }}</span>
      </button>
    </div>

    <!-- Sibling collapsible sections under the body -->
    <div class="figma-fe-body">
      <GitChangesPanel
        v-if="tab === 'changes'"
        ref="gitChangesPanelRef"
        :workspace-id="workspaceId"
        :agent-config-workspace-id="agentConfigWorkspaceId ?? ''"
        :personal-workspace-id="personalWorkspaceId"
        :api-base-url="apiBaseUrl"
        :can-write="!!canWrite"
        :can-manage-agent-config="canManageAgentConfig ?? !!canWrite"
        :can-manage-public-config="canManagePublicConfig ?? !!canWrite"
        @open-diff="(payload) => emit('openDiff', payload)"
        @changes-refreshed="(payload) => emit('changes-refreshed', payload)"
      />
      <template v-else>
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
                v-if="tab === 'explorer' && canWrite"
                type="button"
                class="figma-fe-section-action-btn"
                title="新建或上传到工作区根目录"
                aria-label="新建或上传到工作区根目录"
                :disabled="!workspaceId"
                @click="openRootActions"
              >
                <Plus class="h-3.5 w-3.5" :stroke-width="1.5" />
              </button>
              <button
                v-if="tab === 'explorer'"
                type="button"
                class="figma-fe-section-action-btn"
                title="打开外部页面"
                aria-label="打开外部页面"
                :disabled="!workspaceId"
                @click="openIframeDialog"
              >
                <Globe class="h-3.5 w-3.5" :stroke-width="1.5" />
              </button>
               <button
                v-if="tab === 'explorer'"
                type="button"
                class="figma-fe-section-action-btn"
                title="刷新文件树"
                aria-label="刷新文件树"
                :disabled="!workspaceId || loadingPath?.has('')"
                @click="emit('refresh')"
              >
                <RefreshCw class="h-3.5 w-3.5" :class="{ 'animate-spin': loadingPath?.has('') }" :stroke-width="1.5" />
              </button>
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
              :workspace-root-path="workspaceRootPath"
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
              @toggle-directory="emit('toggleDirectory', $event)"
              @open-file="emit('openFile', $event)"
              @add-file-context="emit('addFileContext', $event)"
              @open-diff="emit('openDiff', $event)"
              @refresh="emit('refresh')"
              @search="emit('search', $event)"
              @create-entry="(directory, name, type) => emit('createEntry', directory, name, type)"
              @delete-entry="(path, type) => emit('deleteEntry', path, type)"
              @rename-entry="(path, name) => emit('renameEntry', path, name)"
              @copy-entry="(sourcePath, targetDirectory) => emit('copyEntry', sourcePath, targetDirectory)"
              @move-entry="(sourcePath, targetDirectory) => emit('moveEntry', sourcePath, targetDirectory)"
              @upload-files="(directory, files) => emit('uploadFiles', directory, files)"
              @undo-entry="emit('undoEntry')"
              @cache-and-navigate="(path, type) => emit('cacheAndNavigate', path, type)"
            />
          </div>
        </div>

        <!-- Resizer divider: only show if both sections are expanded -->
        <div
          v-if="workspaceExpanded && agentsExpanded"
          class="figma-fe-resize-handle"
          @mousedown="onResizeStart"
          role="separator"
          aria-orientation="horizontal"
        />

        <!-- Section 2: agents -->
        <div class="figma-fe-section" :class="{ 'is-expanded': agentsExpanded }">
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
              :workspace-id="agentConfigWorkspaceId"
              :can-write="canManagePublicConfig ?? !!canWrite"
              :can-manage-workspace-config="canManageAgentConfig ?? !!canWrite"
              :hide-header="true"
              :hide-git-ops="true"
              :active-path="activePath"
              @open-file="emit('openAgentFile', $event)"
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
      @select-version="(payload) => emit('selectVersion', payload)"
      @load-versions="(templateId: string) => emit('loadVersions', templateId)"
      @create-version="(payload) => emit('createVersion', payload)"
      @open-server-workspace-picker="emit('openServerWorkspacePicker')"
    />

    <Teleport to="body">
      <div v-if="iframeDialogVisible" class="figma-fe-iframe-overlay" @click="closeIframeDialog">
        <div class="figma-fe-iframe-dialog" @click.stop>
          <div class="figma-fe-iframe-header">
            <span class="figma-fe-iframe-title">外部页面</span>
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
              title="外部页面"
              sandbox="allow-scripts allow-same-origin allow-forms"
            />
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
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
