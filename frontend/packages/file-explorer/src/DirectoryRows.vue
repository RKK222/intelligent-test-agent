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
import { cn } from "@test-agent/ui-kit";
import FileIcon from "./FileIcon.vue";

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
          'ta-file-tree-row',
          activePath === entry.path && 'is-active'
        )"
        :style="{ paddingLeft: depth * 16 + 6 + 'px' }"
        @click="onRowClick(entry)"
      >
        <template v-if="entry.type === 'directory'">
          <i
            v-if="!isKnownEmptyDirectory(entry.path)"
            :class="cn('codicon codicon-chevron-right ta-file-tree-twistie', expandedDirectories.has(entry.path) && 'is-open')"
            aria-hidden="true"
          />
          <span v-else class="ta-file-tree-spacer" />
        </template>
        <template v-else>
          <span class="ta-file-tree-spacer" />
          <FileIcon :entry="entry" />
        </template>
        <span class="min-w-0 flex-1 truncate">{{ entry.name }}</span>
        <template v-if="entry.type === 'file' && changeStats?.[entry.path]">
          <span class="ta-file-tree-badge is-added">+{{ changeStats[entry.path].additions }}</span>
          <span class="ta-file-tree-badge is-deleted">-{{ changeStats[entry.path].deletions }}</span>
        </template>
        <!-- 加载指示：使用旋转图标，避免行尾细小的 "..." 难以被发现而引起重复点击。 -->
        <i v-if="loadingPath?.has(entry.path)" class="codicon codicon-loading codicon-modifier-spin ta-file-tree-loading" aria-hidden="true" />
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
