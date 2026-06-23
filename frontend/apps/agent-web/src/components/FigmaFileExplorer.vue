<script setup lang="ts">
import chevronUrl from "../assets/figma/chevron-folder.svg";
import fileIconUrl from "../assets/figma/file-icon.svg";
import { FileExplorer, type FileExplorerProps } from "@test-agent/file-explorer";
import WorkbenchFooter from "./WorkbenchFooter.vue";

type VcsBranch = { name: string; isCurrent?: boolean };

defineProps<FileExplorerProps & {
  workspaceRootPath?: string;
  branches?: VcsBranch[];
  currentBranch?: string;
}>();

const emit = defineEmits<{
  toggleDirectory: [path: string];
  openFile: [path: string];
  openDiff: [path: string];
  refresh: [];
  addWorkspace: [];
  changeBranch: [branch: string];
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
        @add-workspace="emit('addWorkspace')"
      />
    </div>
    <WorkbenchFooter
      :branch="currentBranch"
      :branches="branches"
      :show-branch="true"
      @change-branch="(name: string) => emit('changeBranch', name)"
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
