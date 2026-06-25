<script setup lang="ts">
import { computed } from "vue";
import { ChevronRight, FileText, Folder, Loader2 } from "lucide-vue-next";
import { cn } from "@test-agent/ui-kit";
import type { FileTreeEntry } from "@test-agent/shared-types";

/**
 * 公共目录树节点（递归组件）：
 * <ul>
 *   <li>渲染单行：目录带 chevron + folder，文件不带 chevron</li>
 *   <li>展开时递归渲染子目录，支持任意层级（修复之前模板只支持两层的 bug）</li>
 * </ul>
 *
 * 数据来源由父组件 PublicDirectoryPanel 统一管理（entriesByDirectory / expandedDirectories / loadingPath），
 * 本组件只负责根据当前节点路径读取其子项并向外发出 toggle / openFile 事件。
 */

// 显式声明组件名，允许在自身模板内通过 PascalCase 名称递归引用。
defineOptions({ name: "PublicDirectoryNode" });

const props = defineProps<{
  /** 当前节点 */
  entry: FileTreeEntry;
  /** 当前节点深度（顶级 = 0），用于控制左侧缩进 */
  depth: number;
  /** 父组件持有的目录 -> 子项映射；模板内 Vue 会自动 unwrap ref */
  entriesByDirectory: Record<string, FileTreeEntry[]>;
  /** 父组件持有的已展开目录集合 */
  expandedDirectories: Set<string>;
  /** 父组件持有的正在加载的目录集合 */
  loadingPath: Set<string>;
}>();

const emit = defineEmits<{
  /** 用户点击目录行（折叠/展开） */
  toggle: [path: string];
  /** 用户点击文件行（父组件去拉内容并打开 tab） */
  openFile: [path: string];
}>();

const children = computed<FileTreeEntry[]>(() => props.entriesByDirectory[props.entry.path] ?? []);
const isExpanded = computed(() => props.expandedDirectories.has(props.entry.path));
const isLoading = computed(() => props.loadingPath.has(props.entry.path));
const isDirectory = computed(() => props.entry.type === "directory");
const isKnownEmpty = computed(
  () => isDirectory.value && Array.isArray(children.value) && children.value.length === 0
);
const indentPx = computed(() => 4 + props.depth * 14);

function onRowClick() {
  if (isDirectory.value) {
    // 已知为空的目录：点击不展开，也不发请求，避免无意义的 UI 抖动。
    if (isKnownEmpty.value) return;
    emit("toggle", props.entry.path);
  } else {
    emit("openFile", props.entry.path);
  }
}
</script>

<template>
  <div>
    <button
      type="button"
      :class="cn(
        'flex h-7 w-full items-center gap-1 rounded text-left text-[14px] leading-5 text-[var(--ta-subtle)] hover:bg-[var(--ta-hover)]'
      )"
      :style="{ paddingLeft: `${indentPx}px`, paddingRight: '4px' }"
      @click="onRowClick"
    >
      <template v-if="isDirectory">
        <ChevronRight
          v-if="!isKnownEmpty"
          :class="cn('h-3.5 w-3.5 text-[var(--ta-muted)] transition', isExpanded && 'rotate-90')"
        />
        <span v-else class="w-3.5" />
        <Folder class="h-4 w-4 text-[var(--ta-muted)]" />
      </template>
      <template v-else>
        <span class="w-3.5" />
        <FileText class="h-4 w-4 text-[var(--ta-muted)]" />
      </template>
      <span class="min-w-0 flex-1 truncate">{{ entry.name }}</span>
      <Loader2 v-if="isLoading" class="h-3.5 w-3.5 animate-spin text-[var(--ta-muted)]" />
    </button>
    <div v-if="isDirectory && isExpanded" class="space-y-px">
      <PublicDirectoryNode
        v-for="child in children"
        :key="child.path"
        :entry="child"
        :depth="depth + 1"
        :entries-by-directory="entriesByDirectory"
        :expanded-directories="expandedDirectories"
        :loading-path="loadingPath"
        @toggle="(path: string) => emit('toggle', path)"
        @open-file="(path: string) => emit('openFile', path)"
      />
    </div>
  </div>
</template>
