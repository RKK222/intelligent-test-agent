<script setup lang="ts">
import { computed, ref } from "vue";
import { FileExplorer, type FileExplorerProps } from "@test-agent/file-explorer";
import type { FileContent } from "@test-agent/shared-types";
import type { AppWorkspaceTemplate, AppWorkspaceVersion } from "./WorkbenchFooter.vue";
import WorkbenchFooter from "./WorkbenchFooter.vue";
import AgentConfigPanel from "./AgentConfigPanel.vue";
import { ChevronDown, ChevronRight, RefreshCw } from "lucide-vue-next";

defineProps<FileExplorerProps & {
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
  /** 是否允许编辑公共目录（仅 SUPER_ADMIN 传 true） */
  publicDirectoryWritable?: boolean;
  /** 后端 base url，透传给 PublicDirectoryPanel */
  apiBaseUrl?: string;
  /** 当前运行态 Workspace ID，透传给 AgentConfigPanel */
  workspaceId?: string;
  /** 是否显示超级管理员服务器工作空间切换入口 */
  showServerWorkspaceSwitch?: boolean;
}>();

const emit = defineEmits<{
  toggleDirectory: [path: string];
  openFile: [path: string];
  openDiff: [path: string];
  refresh: [];
  // 选择某个应用版本后由父组件切换运行态 Workspace
  selectVersion: [payload: { template: AppWorkspaceTemplate; version: AppWorkspaceVersion }];
  // 要求按需懒加载某模板下的版本列表
  loadVersions: [templateId: string];
  // 「+新增版本」弹窗确认后由父组件调用 createWorkspaceVersion。
  createVersion: [payload: { template: AppWorkspaceTemplate; version: string; branch?: string }];
  // 公共目录下打开文件：path + 只读/可写 由父组件决定如何渲染 tab
  openPublicFile: [payload: { path: string; content: FileContent; readonly: boolean }];
  openAgentFile: [payload: { scope: "PUBLIC" | "WORKSPACE"; path: string; content: FileContent; readonly: boolean; worktreeId?: string | null }];
  openServerWorkspacePicker: [];
}>();

const workspaceExpanded = ref(true);
const agentsExpanded = ref(true);
const agentConfigPanelRef = ref<InstanceType<typeof AgentConfigPanel> | null>(null);

const workspaceHeight = ref<number | null>(null);
const resizing = ref(false);
let dragStartY = 0;
let dragStartHeight = 0;

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
</script>

<template>
  <div class="figma-file-explorer">
    <div class="figma-fe-body">
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
            <span class="figma-fe-section-title" :title="workspaceName">应用工作空间</span>
          </button>
          <div class="figma-fe-section-actions" v-if="workspaceExpanded">
            <button
              type="button"
              class="figma-fe-section-action-btn"
              title="刷新文件树"
              aria-label="刷新文件树"
              @click="emit('refresh')"
            >
              <RefreshCw class="h-3.5 w-3.5" :stroke-width="1.5" />
            </button>
          </div>
        </div>
        <div v-show="workspaceExpanded" class="figma-fe-section-content">
          <FileExplorer
            :workspace-name="workspaceName"
            :workspace-root-path="workspaceRootPath"
            :entries-by-directory="entriesByDirectory"
            :expanded-directories="expandedDirectories"
            :active-path="activePath"
            :changed-files="changedFiles"
            :loading-path="loadingPath"
            :hide-header="true"
            @toggle-directory="emit('toggleDirectory', $event)"
            @open-file="emit('openFile', $event)"
            @open-diff="emit('openDiff', $event)"
            @refresh="emit('refresh')"
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
            <span class="figma-fe-section-title">agents</span>
          </button>
          <div class="figma-fe-section-actions" v-if="agentsExpanded">
            <button
              type="button"
              class="figma-fe-section-action-btn"
              title="刷新"
              aria-label="刷新"
              @click="refreshAgents"
            >
              <RefreshCw class="h-3.5 w-3.5" :stroke-width="1.5" />
            </button>
          </div>
        </div>
        <div v-show="agentsExpanded" class="figma-fe-section-content">
          <AgentConfigPanel
            ref="agentConfigPanelRef"
            :base-url="apiBaseUrl ?? ''"
            :workspace-id="workspaceId"
            :can-write="!!publicDirectoryWritable"
            :hide-header="true"
            :hide-git-ops="true"
            @open-file="emit('openAgentFile', $event)"
          />
        </div>
      </div>
    </div>
    <WorkbenchFooter
      :app-name="appName"
      :templates="appTemplates"
      :selected-version-id="selectedVersionId"
      :loading-templates="loadingAppTemplates"
      :loading-versions="loadingAppVersions"
      :creating-version="creatingVersion"
      :show-server-workspace-switch="showServerWorkspaceSwitch"
      @select-version="(payload) => emit('selectVersion', payload)"
      @load-versions="(templateId: string) => emit('loadVersions', templateId)"
      @create-version="(payload) => emit('createVersion', payload)"
      @open-server-workspace-picker="emit('openServerWorkspacePicker')"
    />
  </div>
</template>

<style scoped>
.figma-file-explorer {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.figma-fe-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: #fafafa;
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
.figma-fe-section + .figma-fe-section {
  border-top: 1px solid #e4e4e7;
}

.figma-fe-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 32px;
  padding: 0 8px;
  background: #fafafa;
  border-bottom: 1px solid #e4e4e7;
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
  color: #555;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  min-width: 0;
  flex: 1;
  text-align: left;
}

.figma-fe-section-header-trigger:hover {
  color: #18181b;
}

.figma-fe-section-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.figma-fe-section-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* Avoid overlapping with the absolutely-positioned sidebar floating toggle button in the first header */
.figma-fe-section:first-child .figma-fe-section-actions {
  margin-right: 32px;
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
  color: #71717a;
  cursor: pointer;
  transition: background-color 0.1s, color 0.1s;
}

.figma-fe-section-action-btn:hover {
  background: #e4e4e7;
  color: #18181b;
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
  background: #e4e4e7;
  transition: background-color 0.14s ease;
}

.figma-fe-resize-handle:hover {
  background: rgba(0, 0, 0, 0.04);
}

.figma-fe-resize-handle:hover::after {
  background: #bbb;
}

.figma-fe-resize-handle:active {
  background: rgba(0, 0, 0, 0.06);
}

.figma-fe-body :deep(.ta-icon-tabbar) {
  height: 38px;
  background: #fafafa;
}

.figma-fe-body :deep(.ta-icon-tab) {
  font-size: 11px;
}

.figma-fe-body :deep(.bg-\[var\(--ta-panel\)\]) {
  background: #fafafa;
}
</style>
