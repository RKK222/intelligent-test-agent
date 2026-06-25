<script setup lang="ts">
import chevronUrl from "../assets/figma/chevron-folder.svg";
import fileIconUrl from "../assets/figma/file-icon.svg";
import { FileExplorer, type FileExplorerProps } from "@test-agent/file-explorer";
import type { AppWorkspaceTemplate, AppWorkspaceVersion } from "./WorkbenchFooter.vue";
import WorkbenchFooter from "./WorkbenchFooter.vue";

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
}>();
</script>

<template>
  <div class="figma-file-explorer">
    <div class="figma-fe-body">
      <FileExplorer
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
    </div>
    <WorkbenchFooter
      :app-name="appName"
      :templates="appTemplates"
      :selected-version-id="selectedVersionId"
      :loading-templates="loadingAppTemplates"
      :loading-versions="loadingAppVersions"
      :creating-version="creatingVersion"
      @select-version="(payload) => emit('selectVersion', payload)"
      @load-versions="(templateId: string) => emit('loadVersions', templateId)"
      @create-version="(payload) => emit('createVersion', payload)"
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
