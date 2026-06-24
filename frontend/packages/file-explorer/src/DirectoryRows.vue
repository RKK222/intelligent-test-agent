<script lang="ts">
import type { FileTreeEntry } from "@test-agent/shared-types";

export type DirectoryRowsProps = {
  directory: string;
  entriesByDirectory: Record<string, FileTreeEntry[]>;
  expandedDirectories: Set<string>;
  activePath?: string;
  loadingPath?: Set<string>;
  depth?: number;
  /** 文件路径 → 行变更统计，用于在文件名后展示 +N -N。 */
  changeStats?: Record<string, { additions: number; deletions: number }>;
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import { ChevronRight, FileText, Folder, Loader2 } from "lucide-vue-next";
import { cn } from "@test-agent/ui-kit";

const props = withDefaults(defineProps<DirectoryRowsProps>(), { depth: 0 });
const emit = defineEmits<{
  toggleDirectory: [path: string];
  openFile: [path: string];
}>();

const entries = computed(() => {
  const list = props.entriesByDirectory[props.directory] ?? [];
  // 文件夹排在前面，文件排在后面；同组内保持原顺序。
  return [...list].sort((a, b) => {
    if (a.type === b.type) return 0;
    return a.type === "directory" ? -1 : 1;
  });
});

// 目录是否"已知为空"：子项已加载且为空数组。
// - 未加载：保持 chevron，让用户点击触发懒加载。
// - 已加载且为空：不渲染 chevron，文件夹作为叶子展示。
function isKnownEmptyDirectory(path: string): boolean {
  const children = props.entriesByDirectory[path];
  return Array.isArray(children) && children.length === 0;
}

function onRowClick(entry: FileTreeEntry) {
  if (entry.type === "directory") {
    // 已知为空的目录：没有子项可展开，吞掉点击避免无意义的 toggle。
    if (isKnownEmptyDirectory(entry.path)) {
      return;
    }
    emit("toggleDirectory", entry.path);
  } else {
    emit("openFile", entry.path);
  }
}
</script>

<template>
  <div>
    <div v-for="entry in entries" :key="entry.path">
      <button
        type="button"
        :class="cn(
          'flex h-7 w-full items-center gap-1 rounded px-1 text-left text-[14px] leading-5 text-[var(--ta-subtle)] hover:bg-[var(--ta-hover)]',
          activePath === entry.path && 'bg-[var(--ta-active)] text-[var(--ta-ink)]'
        )"
        :style="{ paddingLeft: depth * 14 + 4 + 'px' }"
        @click="onRowClick(entry)"
      >
        <template v-if="entry.type === 'directory'">
          <ChevronRight
            v-if="!isKnownEmptyDirectory(entry.path)"
            :class="cn('h-3.5 w-3.5 text-[var(--ta-muted)] transition', expandedDirectories.has(entry.path) && 'rotate-90')"
          />
          <span v-else class="w-3.5" />
          <Folder class="h-4 w-4 text-[var(--ta-muted)]" />
        </template>
        <template v-else>
          <span class="w-3.5" />
          <FileText class="h-4 w-4 text-[var(--ta-muted)]" />
        </template>
        <span class="min-w-0 flex-1 truncate">{{ entry.name }}</span>
        <template v-if="entry.type === 'file' && changeStats?.[entry.path]">
          <span class="shrink-0 text-[10px] leading-5 text-[#3f7a5a]">+{{ changeStats[entry.path].additions }}</span>
          <span class="shrink-0 text-[10px] leading-5 text-[#9e3b34]">-{{ changeStats[entry.path].deletions }}</span>
        </template>
        <!-- 加载指示：使用旋转图标，避免行尾细小的 "..." 难以被发现而引起重复点击。 -->
        <Loader2 v-if="loadingPath?.has(entry.path)" :class="cn('h-3.5 w-3.5 shrink-0 animate-spin text-[var(--ta-muted)]')" />
      </button>
      <DirectoryRows
        v-if="entry.type === 'directory' && expandedDirectories.has(entry.path)"
        :directory="entry.path"
        :entries-by-directory="entriesByDirectory"
        :expanded-directories="expandedDirectories"
        :active-path="activePath"
        :loading-path="loadingPath"
        :change-stats="changeStats"
        :depth="depth + 1"
        @toggle-directory="emit('toggleDirectory', $event)"
        @open-file="emit('openFile', $event)"
      />
    </div>
  </div>
</template>
