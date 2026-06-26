<script setup lang="ts">
import { computed, ref } from "vue";
import { FileExplorer, type FileExplorerProps } from "@test-agent/file-explorer";
import type { FileContent } from "@test-agent/shared-types";
import type { AppWorkspaceTemplate, AppWorkspaceVersion } from "./WorkbenchFooter.vue";
import WorkbenchFooter from "./WorkbenchFooter.vue";
import PublicDirectoryPanel from "./PublicDirectoryPanel.vue";
import AgentConfigPanel from "./AgentConfigPanel.vue";
import { Bot, FolderTree, Layers } from "lucide-vue-next";

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
  createVersion: [payload: { template: AppWorkspaceTemplate; version: string }];
  // 公共目录下打开文件：path + 只读/可写 由父组件决定如何渲染 tab
  openPublicFile: [payload: { path: string; content: FileContent; readonly: boolean }];
  openAgentFile: [payload: { scope: "PUBLIC" | "WORKSPACE"; path: string; content: FileContent; readonly: boolean; worktreeId?: string | null }];
  openServerWorkspacePicker: [];
}>();

// 视图模式：workspace（默认）展示 FileExplorer；public 展示 PublicDirectoryPanel。
// 切换时通过 v-if 卸载不活跃的组件，避免两个面板相互竞争滚动区域。
type ViewMode = "workspace" | "public" | "agent";
const viewMode = ref<ViewMode>("workspace");
const showPublicTab = computed(() => true); // 后端未配置时面板自身会展示空态，这里始终可点。
</script>

<template>
  <div class="figma-file-explorer">
    <div class="figma-fe-toolbar" role="tablist" aria-label="文件视图切换">
      <button
        type="button"
        :class="['figma-fe-toolbar-tab', viewMode === 'workspace' && 'is-active']"
        title="工作区文件"
        aria-label="工作区文件"
        @click="viewMode = 'workspace'"
      >
        <FolderTree class="h-3.5 w-3.5" :stroke-width="1.5" />
        <span>工作区</span>
      </button>
      <button
        v-if="showPublicTab"
        type="button"
        :class="['figma-fe-toolbar-tab', viewMode === 'public' && 'is-active']"
        title="公共目录（后端固定路径）"
        aria-label="公共目录"
        @click="viewMode = 'public'"
      >
        <Layers class="h-3.5 w-3.5" :stroke-width="1.5" />
        <span>公共目录</span>
      </button>
      <button
        type="button"
        :class="['figma-fe-toolbar-tab', viewMode === 'agent' && 'is-active']"
        title="Agent"
        aria-label="Agent"
        @click="viewMode = 'agent'"
      >
        <Bot class="h-3.5 w-3.5" :stroke-width="1.5" />
        <span>Agent</span>
      </button>
    </div>
    <div class="figma-fe-body">
      <FileExplorer
        v-if="viewMode === 'workspace'"
        :workspace-name="workspaceName"
        :workspace-root-path="workspaceRootPath"
        :entries-by-directory="entriesByDirectory"
        :expanded-directories="expandedDirectories"
        :active-path="activePath"
        :changed-files="changedFiles"
        :loading-path="loadingPath"
        @toggle-directory="emit('toggleDirectory', $event)"
        @open-file="emit('openFile', $event)"
        @open-diff="emit('openDiff', $event)"
        @refresh="emit('refresh')"
      />
      <PublicDirectoryPanel
        v-else-if="viewMode === 'public'"
        :can-write="!!publicDirectoryWritable"
        :base-url="apiBaseUrl ?? ''"
        @open-file="emit('openPublicFile', $event)"
      />
      <AgentConfigPanel
        v-else
        :base-url="apiBaseUrl ?? ''"
        :workspace-id="workspaceId"
        :can-write="!!publicDirectoryWritable"
        @open-file="emit('openAgentFile', $event)"
      />
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

.figma-fe-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 32px;
  padding: 0 8px;
  border-bottom: 1px solid #e4e4e7;
  background: #fafafa;
  flex-shrink: 0;
}

.figma-fe-toolbar-tab {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 22px;
  padding: 0 8px;
  border: 0.8px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: #555;
  font-size: 12px;
  cursor: pointer;
  transition: background-color 0.12s ease, color 0.12s ease, border-color 0.12s ease;
}

.figma-fe-toolbar-tab:hover {
  background: #f0f0f0;
  color: #18181b;
}

.figma-fe-toolbar-tab.is-active {
  background: #fff;
  border-color: #dfdfdf;
  color: #18181b;
}

.figma-fe-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
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
