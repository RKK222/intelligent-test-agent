<script setup lang="ts">
import { computed } from "vue";
import type { RunDiffFile } from "@test-agent/shared-types";
import FigmaFileExplorer from "../FigmaFileExplorer.vue";
import { useTeamManagementView } from "./useTeamManagementView";

const { controller, state } = useTeamManagementView();
const expandedDirectories = computed(() => new Set(state.value.expandedPaths));
const changedFiles = computed<RunDiffFile[]>(() => (state.value.gitStatus?.files ?? []).map((file) => ({
  path: file.path,
  patch: file.patch,
  additions: file.additions,
  deletions: file.deletions,
  status: file.status
})));
const activePath = computed(() =>
  state.value.tabs.find((tab) => tab.id === state.value.activeTabId)?.path ?? "");
const runtimeWorkspaceId = computed(() => {
  const personalWorkspaceId = state.value.selectedPersonalWorkspaceId;
  const contribution = state.value.contributions.find((item) => item.userId === state.value.selectedUserId);
  return contribution?.personalWorkspaces.find((item) => item.personalWorkspaceId === personalWorkspaceId)?.workspaceId ?? "";
});
const workspaceName = computed(() =>
  state.value.templates.find((item) => item.workspaceId === state.value.selectedTemplateId)?.workspaceName
  || "工作空间");
const treeError = computed(() => state.value.catalogError || state.value.treeError || null);
const emptyMessage = computed(() =>
  state.value.missingDefaultWorkspace ? "这个版本还没有名为 default 的个人工作空间。" : "");

function refresh() {
  if (state.value.catalogError) void controller.retryCatalog();
  else void controller.reloadTree();
}
</script>

<template>
  <FigmaFileExplorer
    class="team-review-files"
    readonly-review
    workspace-kind="MANAGED"
    :workspace-id="runtimeWorkspaceId"
    :workspace-name="workspaceName"
    :entries-by-directory="state.entriesByDirectory"
    :expanded-directories="expandedDirectories"
    :active-path="activePath"
    :changed-files="changedFiles"
    :can-write="false"
    :search-results="state.searchResults ?? undefined"
    :search-keyword="state.searchQuery"
    :file-tree-error="treeError"
    :empty-workspace-message="emptyMessage"
    @toggle-directory="controller.toggleDirectory"
    @open-file="controller.openEntry($event, false)"
    @open-diff="controller.openChange(typeof $event === 'string' ? $event : $event.path)"
    @search="controller.setFileSearch"
    @refresh="refresh"
  />
</template>

<style scoped>
.team-review-files {
  height: 100%;
  min-height: 0;
}
</style>
