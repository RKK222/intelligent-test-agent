<script setup lang="ts">
import { computed } from "vue";
import type { FileTreeEntry } from "@test-agent/shared-types";
import { useTeamManagementView } from "./useTeamManagementView";

const { controller, state } = useTeamManagementView();

const contribution = computed(() =>
  state.value.contributions.find((item) => item.userId === state.value.selectedUserId) ?? null);
const worktrees = computed(() => contribution.value?.personalWorkspaces ?? []);

type VisibleRow = FileTreeEntry & { depth: number; expanded: boolean };

const visibleRows = computed<VisibleRow[]>(() => {
  const rows: VisibleRow[] = [];
  const walk = (path: string, depth: number) => {
    for (const entry of state.value.entriesByDirectory[path] ?? []) {
      const expanded = state.value.expandedPaths.includes(entry.path);
      rows.push({ ...entry, depth, expanded });
      if (entry.type === "directory" && expanded) walk(entry.path, depth + 1);
    }
  };
  walk("", 0);
  return rows;
});

function onMemberChange(event: Event) {
  void controller.selectMember((event.target as HTMLSelectElement).value);
}

function onWorktreeChange(event: Event) {
  void controller.selectWorktree((event.target as HTMLSelectElement).value);
}

function onSearch(event: Event) {
  controller.setFileSearch((event.target as HTMLInputElement).value);
}
</script>

<template>
  <section class="team-files" aria-label="团队工作区只读文件">
    <header class="team-files-toolbar">
      <label class="team-files-field">
        <span>成员</span>
        <select
          aria-label="成员"
          :value="state.selectedUserId"
          :disabled="!state.contributions.length"
          @change="onMemberChange"
        >
          <option v-if="!state.contributions.length" value="">暂无成员</option>
          <option v-for="item in state.contributions" :key="item.userId" :value="item.userId">
            {{ item.username }}
          </option>
        </select>
      </label>
      <label class="team-files-field">
        <span>worktree</span>
        <select
          aria-label="个人 worktree"
          :value="state.selectedPersonalWorkspaceId"
          :disabled="!worktrees.length"
          @change="onWorktreeChange"
        >
          <option v-if="!worktrees.length" value="">没有个人 worktree</option>
          <option v-for="item in worktrees" :key="item.personalWorkspaceId" :value="item.personalWorkspaceId">
            {{ item.workspaceName }} · {{ item.branch }}
          </option>
        </select>
      </label>
      <label class="team-files-search">
        <span class="team-files-search-label">搜索</span>
        <input
          type="search"
          aria-label="搜索文件"
          placeholder="搜索文件名"
          :value="state.searchQuery"
          :disabled="!state.selectedPersonalWorkspaceId"
          @input="onSearch"
        >
      </label>
    </header>
    <div class="team-files-tree" role="tree">
      <p v-if="!state.selectedPersonalWorkspaceId" class="team-files-empty">选择成员的个人 worktree 后查看只读文件。</p>
      <template v-else-if="state.searchResults">
        <p v-if="!state.searchResults.length" class="team-files-empty">没有匹配的文件。</p>
        <button
          v-for="result in state.searchResults"
          :key="result.path"
          type="button"
          class="team-files-row"
          role="treeitem"
          @click="controller.openEntry(result.path, false)"
        >
          <span class="team-files-name">{{ result.name }}</span>
          <span class="team-files-path">{{ result.path }}</span>
        </button>
      </template>
      <template v-else>
        <p v-if="!visibleRows.length && !state.detailLoading" class="team-files-empty">这个 worktree 还没有可查看的文件。</p>
        <button
          v-for="row in visibleRows"
          :key="row.path"
          type="button"
          class="team-files-row"
          role="treeitem"
          :aria-expanded="row.type === 'directory' ? row.expanded : undefined"
          :style="{ paddingLeft: `${8 + row.depth * 14}px` }"
          @click="controller.openEntry(row.path, row.type === 'directory')"
        >
          <span v-if="row.type === 'directory'" class="team-files-twist" aria-hidden="true">{{ row.expanded ? "▾" : "▸" }}</span>
          <span v-else class="team-files-twist" aria-hidden="true" />
          <span class="team-files-name">{{ row.name }}</span>
        </button>
      </template>
    </div>
  </section>
</template>

<style scoped>
.team-files {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--ta-tree-bg, #fff);
  color: var(--ta-tree-text, #333a48);
  font-family: var(--ta-tree-font-family, inherit);
  font-size: var(--ta-tree-font-size, 13px);
}
.team-files-toolbar {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  border-bottom: 1px solid var(--ta-tree-border, #e5e7eb);
}
.team-files-field,
.team-files-search {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: var(--ta-tree-muted, #6b7280);
  font-size: 12px;
}
.team-files-search-label {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
.team-files select,
.team-files input {
  width: 100%;
  height: 28px;
  border: 1px solid var(--ta-tree-border-strong, #d1d5db);
  border-radius: 6px;
  background: #fff;
  color: var(--ta-tree-text, #111);
  font: inherit;
  font-size: 13px;
}
.team-files select:focus-visible,
.team-files input:focus-visible,
.team-files-row:focus-visible {
  outline: 2px solid var(--ta-shell-accent, #c8161d);
  outline-offset: 1px;
}
.team-files-tree {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 4px 0 8px;
}
.team-files-row {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  min-height: var(--ta-tree-row-height, 22px);
  border: 0;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}
.team-files-row:hover {
  background: var(--ta-tree-hover, rgba(22, 119, 255, 0.06));
}
.team-files-twist {
  width: 12px;
  flex: none;
  color: var(--ta-tree-muted, #6b7280);
}
.team-files-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.team-files-path {
  min-width: 0;
  overflow: hidden;
  color: var(--ta-tree-muted, #6b7280);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.team-files-empty {
  margin: 12px;
  color: var(--ta-tree-muted, #6b7280);
  line-height: 1.5;
}
</style>
