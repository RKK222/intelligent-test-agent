<script lang="ts">
import type { FileStatus, FileTreeEntry, RunDiffFile } from "@test-agent/shared-types";

export type FileExplorerProps = {
  workspaceName?: string;
  workspaceRootPath?: string;
  entriesByDirectory: Record<string, FileTreeEntry[]>;
  expandedDirectories: Set<string>;
  activePath?: string;
  changedFiles: RunDiffFile[];
  statuses?: Record<string, FileStatus>;
  loadingPath?: Set<string>;
};

type ExplorerTab = "explorer" | "search" | "changes";
</script>

<script setup lang="ts">
import { computed, ref } from "vue";
import { FileText, FolderTree, GitBranch, RefreshCw, Search } from "lucide-vue-next";
import { Badge, Input, cn } from "@test-agent/ui-kit";
import { filterLoadedFiles } from "./filterLoadedFiles";
import DirectoryRows from "./DirectoryRows.vue";

const props = withDefaults(defineProps<FileExplorerProps>(), { workspaceName: "Workspace" });
const emit = defineEmits<{
  toggleDirectory: [path: string];
  openFile: [path: string];
  openDiff: [path: string];
  refresh: [];
}>();

const tab = ref<ExplorerTab>("explorer");
const keyword = ref("");
const searchResults = computed(() => filterLoadedFiles(props.entriesByDirectory, keyword.value));

// 把 changedFiles 的路径归一化为 workspace 相对路径，用于文件树行匹配 +N -N。
// opencode 的 diff path 可能是绝对路径或带 a//b/ 前缀，需与文件树 entry.path（相对路径）对齐。
const changeStats = computed(() => {
  const root = props.workspaceRootPath ?? "";
  const map: Record<string, { additions: number; deletions: number }> = {};
  for (const f of props.changedFiles) {
    let p = f.path.replace(/^([ab])\//, "");
    if (root && (p === root || p.startsWith(`${root}/`))) {
      p = p.slice(root.length).replace(/^\/+/, "");
    }
    const prev = map[p];
    map[p] = prev
      ? { additions: prev.additions + f.additions, deletions: prev.deletions + f.deletions }
      : { additions: f.additions, deletions: f.deletions };
  }
  return map;
});
</script>

<template>
  <div class="flex h-full min-h-0 flex-col bg-[var(--ta-panel)]">
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
    <div v-if="tab === 'explorer'" class="min-h-0 flex-1 overflow-auto px-2 py-2 text-[14px]">
      <div class="mb-1 flex h-7 items-center justify-between rounded px-2 text-[12px] font-semibold text-[var(--ta-muted)]">
        <span class="min-w-0 truncate" :title="workspaceName">{{ workspaceName }}</span>
        <div class="flex shrink-0 items-center gap-1">
          <button
            type="button"
            class="ta-fe-icon-btn"
            title="刷新文件树"
            aria-label="刷新文件树"
            @click="emit('refresh')"
          >
            <RefreshCw class="h-3.5 w-3.5" :stroke-width="1.5" />
          </button>
        </div>
      </div>
      <DirectoryRows
        directory=""
        :entries-by-directory="entriesByDirectory"
        :expanded-directories="expandedDirectories"
        :active-path="activePath"
        :loading-path="loadingPath"
        :change-stats="changeStats"
        :depth="0"
        @toggle-directory="emit('toggleDirectory', $event)"
        @open-file="emit('openFile', $event)"
      />
    </div>
    <div v-else-if="tab === 'search'" class="min-h-0 flex-1 overflow-auto p-2">
      <div class="relative">
        <Search class="pointer-events-none absolute left-2 top-2 h-4 w-4 text-[var(--ta-muted)]" :stroke-width="1.5" />
        <Input v-model="keyword" class="pl-7" placeholder="过滤已加载文件名" />
      </div>
      <div class="mt-2 space-y-1">
        <button
          v-for="entry in searchResults"
          :key="entry.path"
          type="button"
          :class="cn('flex h-7 w-full items-center gap-2 rounded px-2 text-left text-[14px] text-[var(--ta-subtle)] hover:bg-[var(--ta-hover)]')"
          @click="emit('openFile', entry.path)"
        >
          <FileText class="h-4 w-4 text-[var(--ta-muted)]" :stroke-width="1.5" />
          <span class="min-w-0 truncate">{{ entry.path }}</span>
        </button>
      </div>
    </div>
    <div v-else-if="tab === 'changes'" class="min-h-0 flex-1 overflow-auto p-2">
      <div class="space-y-1">
        <button
          v-for="file in changedFiles"
          :key="file.path"
          type="button"
          class="flex w-full items-center gap-2 rounded border border-[var(--ta-border)] bg-[var(--ta-surface)] px-2 py-2 text-left hover:border-[var(--ta-border-strong)]"
          @click="emit('openDiff', file.path)"
        >
          <Badge :tone="file.status === 'deleted' ? 'danger' : file.status === 'added' ? 'success' : 'warning'">{{ file.status }}</Badge>
          <span class="min-w-0 flex-1 truncate text-[12px] text-[var(--ta-text)]">{{ file.path }}</span>
          <span class="text-[11px] text-[#3f7a5a]">+{{ file.additions }}</span>
          <span class="text-[11px] text-[#9e3b34]">-{{ file.deletions }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ta-fe-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--ta-muted, #6b7280);
  cursor: pointer;
  transition: background-color 0.12s ease, color 0.12s ease;
}

.ta-fe-icon-btn:hover {
  background: transparent;
  color: var(--ta-text, #18181b);
}

.ta-fe-icon-btn:focus-visible {
  outline: 2px solid var(--ta-accent, #3366ff);
  outline-offset: 1px;
}
</style>
