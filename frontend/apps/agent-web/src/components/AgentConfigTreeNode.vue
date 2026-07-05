<script setup lang="ts">
import { computed } from "vue";
import { cn } from "@test-agent/ui-kit";
import { FileIcon } from "@test-agent/file-explorer";
import type { FileTreeEntry } from "@test-agent/shared-types";

/**
 * Agent 配置文件树节点（递归组件）：
 * <ul>
 *   <li>渲染单行：目录带 chevron + folder，文件不带 chevron</li>
 *   <li>展开时递归渲染子目录，支持任意层级（修复之前模板只支持两层的 bug）</li>
 * </ul>
 *
 * 数据来源由父组件 AgentConfigPanel 统一管理（entriesByDirectory / expandedDirectories / loadingPath），
 * 本组件只负责根据当前节点路径读取其子项并向外发出 toggle / openFile 事件。
 */

// 显式声明组件名，允许在自身模板内通过 PascalCase 名称递归引用。
defineOptions({ name: "AgentConfigTreeNode" });

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
  /** 当前被编辑器选中的 Agent 文件路径 */
  activePath?: string;
}>();

const emit = defineEmits<{
  /** 用户点击目录行（折叠/展开） */
  toggle: [path: string];
  /** 用户点击文件行（父组件去拉内容并打开 tab） */
  openFile: [path: string];
}>();

// 严格区分"未加载"和"已加载为空数组"：
// - children 为 undefined：尚未请求过该目录的子项，需要渲染 chevron + 允许点击
// - children 为 []：已经请求过且后端返回为空，渲染空白占位、不再发请求
// 不能用 `?? []` 把 undefined 兜底成空数组，否则 isKnownEmpty 会在未加载时误判为 true，
// 导致 chevron 不渲染且 onRowClick 直接 return，目录永远打不开。
const children = computed<FileTreeEntry[] | undefined>(() => props.entriesByDirectory[props.entry.path]);
const isExpanded = computed(() => props.expandedDirectories.has(props.entry.path));
const isLoading = computed(() => props.loadingPath.has(props.entry.path));
const isDirectory = computed(() => props.entry.type === "directory");
const isActiveFile = computed(() => !isDirectory.value && props.activePath === props.entry.path);
const isKnownEmpty = computed(
  () => isDirectory.value && Array.isArray(children.value) && children.value.length === 0
);
const indentPx = computed(() => 6 + props.depth * 16);

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
        'ta-file-tree-row',
        isActiveFile && 'is-active'
      )"
      :style="{ paddingLeft: `${indentPx}px` }"
      @click="onRowClick"
    >
      <span
        v-for="i in depth"
        :key="i"
        class="ta-file-tree-indent-guide"
        :style="{ left: 13 + (i - 1) * 16 + 'px' }"
      />
      <template v-if="isDirectory">
        <i
          v-if="!isKnownEmpty"
          :class="cn('codicon codicon-chevron-right ta-file-tree-twistie', isExpanded && 'is-open')"
          aria-hidden="true"
        />
        <span v-else class="ta-file-tree-spacer" />
      </template>
      <template v-else>
        <span class="ta-file-tree-file-spacer" />
        <FileIcon :entry="entry" />
      </template>
      <span class="min-w-0 flex-1 truncate">{{ entry.name }}</span>
      <i v-if="isLoading" class="codicon codicon-loading codicon-modifier-spin ta-file-tree-loading" aria-hidden="true" />
    </button>
    <div v-if="isDirectory && isExpanded">
      <AgentConfigTreeNode
        v-for="child in children ?? []"
        :key="child.path"
        :entry="child"
        :depth="depth + 1"
        :entries-by-directory="entriesByDirectory"
        :expanded-directories="expandedDirectories"
        :loading-path="loadingPath"
        :active-path="activePath"
        @toggle="(path: string) => emit('toggle', path)"
        @open-file="(path: string) => emit('openFile', path)"
      />
    </div>
  </div>
</template>
