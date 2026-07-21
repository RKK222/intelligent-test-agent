<script setup lang="ts">
import { computed, nextTick, ref } from "vue";
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
  /** Git 冲突文件路径集合，用于在文件树中直接标识冲突文件。 */
  conflictPaths?: Set<string>;
  /** 当前作用域是否可写；只读用户不能进入行内改名。 */
  canWrite?: boolean;
  /** 当前文件是否允许复用行内重命名交互。 */
  canRenameEntry?: (path: string) => boolean;
}>();

const emit = defineEmits<{
  /** 用户点击目录行（折叠/展开） */
  toggle: [path: string];
  /** 用户点击文件行（父组件去拉内容并打开 tab） */
  openFile: [path: string];
  /** 复用父组件的 Agent 配置文件重命名链路。 */
  renameEntry: [path: string, name: string];
}>();

const renameInput = ref<HTMLInputElement | null>(null);
const renaming = ref(false);
const renameName = ref("");
const renameOriginalName = ref("");

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
const isConflictFile = computed(() => !isDirectory.value && props.conflictPaths?.has(props.entry.path));
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

function startRename() {
  if (isDirectory.value || !props.canWrite || !(props.canRenameEntry?.(props.entry.path) ?? false)) return;
  renaming.value = true;
  renameName.value = props.entry.name;
  renameOriginalName.value = props.entry.name;
  void nextTick(() => {
    renameInput.value?.focus();
    renameInput.value?.select();
  });
}

function cancelRename() {
  renaming.value = false;
  renameName.value = "";
  renameOriginalName.value = "";
}

/** 与普通工作区文件树保持相同的名称校验，实际路径安全继续由后端文件服务兜底。 */
function submitRename() {
  const name = renameName.value.trim();
  if (!renaming.value || !props.canWrite) return;
  if (!name || name.includes("/") || name.includes("\\") || name === "." || name === "..") {
    void nextTick(() => renameInput.value?.focus());
    return;
  }
  if (name !== renameOriginalName.value) {
    emit("renameEntry", props.entry.path, name);
  }
  cancelRename();
}
</script>

<template>
  <div>
    <button
      type="button"
      :class="cn(
        'ta-file-tree-row',
        isActiveFile && 'is-active',
        isConflictFile && 'is-conflict'
      )"
      :style="{ paddingLeft: `${indentPx}px` }"
      @click="onRowClick"
      @dblclick.stop="startRename"
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
      <span v-if="!renaming" class="min-w-0 flex-1 truncate">{{ entry.name }}</span>
      <input
        v-else
        ref="renameInput"
        v-model="renameName"
        type="text"
        class="ta-file-tree-rename-input min-w-0 flex-1"
        aria-label="重命名应用 Agent 文件"
        @click.stop
        @keydown.enter.stop.prevent="submitRename"
        @keydown.esc.stop.prevent="cancelRename"
        @blur="submitRename"
      />
      <span v-if="isConflictFile" class="agent-tree-conflict-badge">冲突</span>
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
        :conflict-paths="conflictPaths"
        :can-write="canWrite"
        :can-rename-entry="canRenameEntry"
        @toggle="(path: string) => emit('toggle', path)"
        @open-file="(path: string) => emit('openFile', path)"
        @rename-entry="(path: string, name: string) => emit('renameEntry', path, name)"
      />
    </div>
  </div>
</template>

<style scoped>
.ta-file-tree-row.is-conflict {
  color: #b91c1c;
  background: #fff1f2;
}

.agent-tree-conflict-badge {
  flex: none;
  border: 1px solid #fecaca;
  border-radius: 999px;
  background: #fff;
  color: #b91c1c;
  font-size: 10px;
  font-weight: 600;
  line-height: 16px;
  padding: 0 5px;
}
</style>
