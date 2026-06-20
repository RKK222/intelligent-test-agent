<script lang="ts">
import type { FileStatus, FileTreeEntry, RunDiffFile } from "@test-agent/shared-types";

export type FileExplorerProps = {
  workspaceName?: string;
  entriesByDirectory: Record<string, FileTreeEntry[]>;
  expandedDirectories: Set<string>;
  activePath?: string;
  changedFiles: RunDiffFile[];
  statuses?: Record<string, FileStatus>;
  loadingPath?: string | null;
};

type ExplorerTab = "explorer" | "search" | "changes";
</script>

<script setup lang="ts">
import { computed, ref } from "vue";
import { FileText, RefreshCw, Search } from "lucide-vue-next";
import { Badge, Button, Input, SegmentedTabs, cn } from "@test-agent/ui-kit";
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
</script>

<template>
  <div class="flex h-full min-h-0 flex-col bg-[var(--ta-panel)]">
    <div class="flex h-9 items-center justify-between border-b border-slate-800 px-2">
      <div class="min-w-0 truncate text-[12px] font-semibold text-slate-200">{{ workspaceName }}</div>
      <Button size="icon" variant="ghost" title="刷新文件树" @click="emit('refresh')">
        <RefreshCw class="h-4 w-4" />
      </Button>
    </div>
    <SegmentedTabs
      v-model="tab"
      :items="[
        { id: 'explorer', label: '工作空间' },
        { id: 'search', label: '搜索' },
        { id: 'changes', label: '变更', count: changedFiles.length }
      ]"
    />
    <div v-if="tab === 'explorer'" class="min-h-0 flex-1 overflow-auto p-2 font-mono text-[12px]">
      <DirectoryRows
        directory=""
        :entries-by-directory="entriesByDirectory"
        :expanded-directories="expandedDirectories"
        :active-path="activePath"
        :loading-path="loadingPath"
        :depth="0"
        @toggle-directory="emit('toggleDirectory', $event)"
        @open-file="emit('openFile', $event)"
      />
    </div>
    <div v-else-if="tab === 'search'" class="min-h-0 flex-1 overflow-auto p-2">
      <div class="relative">
        <Search class="pointer-events-none absolute left-2 top-2 h-4 w-4 text-slate-500" />
        <Input v-model="keyword" class="pl-7" placeholder="过滤已加载文件名" />
      </div>
      <div class="mt-2 space-y-1">
        <button
          v-for="entry in searchResults"
          :key="entry.path"
          type="button"
          :class="cn('flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left font-mono text-[12px] text-slate-300 hover:bg-slate-800')"
          @click="emit('openFile', entry.path)"
        >
          <FileText class="h-4 w-4 text-slate-500" />
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
          class="flex w-full items-center gap-2 rounded-md border border-[var(--ta-border)] bg-[#101b33] px-2 py-2 text-left hover:border-[#2a3a63]"
          @click="emit('openDiff', file.path)"
        >
          <Badge :tone="file.status === 'deleted' ? 'danger' : file.status === 'added' ? 'success' : 'warning'">{{ file.status }}</Badge>
          <span class="min-w-0 flex-1 truncate font-mono text-[12px] text-slate-200">{{ file.path }}</span>
          <span class="text-[11px] text-[#86efac]">+{{ file.additions }}</span>
          <span class="text-[11px] text-[#fca5a5]">-{{ file.deletions }}</span>
        </button>
      </div>
    </div>
  </div>
</template>
