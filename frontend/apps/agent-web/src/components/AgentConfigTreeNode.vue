<script setup lang="ts">
import { computed, nextTick, ref } from "vue";
import { cn } from "@test-agent/ui-kit";
import { FileEntryContextMenu, FileIcon } from "@test-agent/file-explorer";
import { Plus, Trash2 } from "lucide-vue-next";
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
  /** 当前作用域是否允许在目录下新建 Agent 配置条目。 */
  canWrite?: boolean;
  /** 应用级只允许在 Git Diff 白名单目录内新增，公共级可直接返回 true。 */
  canCreateInDirectory?: (path: string) => boolean;
  /** 应用级只允许删除 Git Diff 白名单条目，公共级可直接返回 true。 */
  canDeleteEntry?: (path: string) => boolean;
  /** 当前节点是否允许复用行内重命名交互。 */
  canRenameEntry?: (path: string) => boolean;
  /** 当前作用域由父组件统一持有的 Ctrl/Cmd 多选文件。 */
  selectedEntries?: FileTreeEntry[];
  /** 当前作用域正在拖动的文件路径。 */
  dragSourcePaths?: string[];
  /** 当前作用域是否已有可粘贴的文件剪贴板。 */
  clipboardAvailable?: boolean;
}>();

const emit = defineEmits<{
  /** 用户点击目录行（折叠/展开） */
  toggle: [path: string];
  /** 用户点击文件行（父组件去拉内容并打开 tab） */
  openFile: [path: string];
  /** 复用父组件的新建面板，以当前目录作为目标路径。 */
  createEntry: [path: string];
  /** 复用父组件的递归删除确认面板。 */
  deleteEntry: [entry: FileTreeEntry];
  /** 复用父组件的 Agent 配置文件重命名链路。 */
  renameEntry: [path: string, name: string];
  selectionChange: [entries: FileTreeEntry[]];
  deleteEntries: [entries: FileTreeEntry[]];
  setClipboard: [entries: FileTreeEntry[], mode: "copy" | "move"];
  pasteEntries: [targetDirectory: string];
  moveEntries: [sourcePaths: string[], targetDirectory: string];
  dragSourceChange: [paths: string[] | undefined];
}>();

const renameInput = ref<HTMLInputElement | null>(null);
const renaming = ref(false);
const renameName = ref("");
const renameOriginalName = ref("");
const contextMenu = ref<{ x: number; y: number; selection: FileTreeEntry[] } | null>(null);
const dragOver = ref(false);

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
const treeLabel = computed(() => props.entry.displayName?.trim() || props.entry.name);
const treeSecondaryLabel = computed(() => {
  const english = props.entry.displayNameEn?.trim();
  return english && english !== treeLabel.value ? english : "";
});
const isKnownEmpty = computed(
  () => isDirectory.value && Array.isArray(children.value) && children.value.length === 0
);
const indentPx = computed(() => 6 + props.depth * 16);
const isSelected = computed(() => props.selectedEntries?.some((entry) => entry.path === props.entry.path) ?? false);
const canMutateFile = computed(() => !isDirectory.value
  && Boolean(props.canWrite)
  && (props.canDeleteEntry?.(props.entry.path) ?? true)
  && (props.canRenameEntry?.(props.entry.path) ?? true));
const canReceiveFiles = computed(() => isDirectory.value
  && Boolean(props.canWrite)
  && (props.canCreateInDirectory?.(props.entry.path) ?? true));
const canPasteHere = computed(() => canReceiveFiles.value || canMutateFile.value);

function onRowClick(event: MouseEvent) {
  if ((event.ctrlKey || event.metaKey) && canMutateFile.value) {
    const current = props.selectedEntries ?? [];
    emit("selectionChange", isSelected.value
      ? current.filter((entry) => entry.path !== props.entry.path)
      : [...current, props.entry]);
    return;
  }
  if (isDirectory.value) {
    // 已知为空的目录：点击不展开，也不发请求，避免无意义的 UI 抖动。
    if (isKnownEmpty.value) return;
    emit("toggle", props.entry.path);
  } else {
    emit("openFile", props.entry.path);
  }
}

function selectedForEntry(): FileTreeEntry[] {
  if (!canMutateFile.value) return [];
  return isSelected.value ? [...(props.selectedEntries ?? [])] : [props.entry];
}

function openContextMenu(event: MouseEvent) {
  event.preventDefault();
  const selection = selectedForEntry();
  // Agents 目录只在已有文件剪贴板时提供粘贴；否则不渲染空白右键菜单。
  if (selection.length === 0 && !(props.clipboardAvailable && canPasteHere.value)) {
    closeContextMenu();
    return;
  }
  if (selection.length > 0 && !isSelected.value) emit("selectionChange", selection);
  contextMenu.value = { x: event.clientX, y: event.clientY, selection };
}

function closeContextMenu() {
  contextMenu.value = null;
}

function contextTargetDirectory(): string {
  if (isDirectory.value) return props.entry.path;
  const index = props.entry.path.lastIndexOf("/");
  return index >= 0 ? props.entry.path.slice(0, index) : "";
}

function deleteContextSelection() {
  const selection = contextMenu.value?.selection ?? [];
  closeContextMenu();
  if (selection.length > 0) emit("deleteEntries", selection);
}

function setContextClipboard(mode: "copy" | "move") {
  const selection = contextMenu.value?.selection ?? [];
  closeContextMenu();
  if (selection.length > 0) emit("setClipboard", selection, mode);
}

function pasteContextEntries() {
  const target = contextTargetDirectory();
  closeContextMenu();
  emit("pasteEntries", target);
}

function renameContextEntry() {
  closeContextMenu();
  startRename();
}

function dragSources(event: DragEvent): string[] {
  const transferred = event.dataTransfer?.getData("application/x-test-agent-config-files") ?? "";
  if (transferred) {
    try {
      const parsed = JSON.parse(transferred);
      if (Array.isArray(parsed)) return parsed.map(String).filter(Boolean);
    } catch {
      // 非法拖动数据按非内部拖动处理。
    }
  }
  return props.dragSourcePaths ?? [];
}

function onDragStart(event: DragEvent) {
  if (!canMutateFile.value || !event.dataTransfer) {
    event.preventDefault();
    return;
  }
  const selection = selectedForEntry();
  const paths = selection.map((entry) => entry.path);
  if (!isSelected.value) emit("selectionChange", selection);
  event.dataTransfer.effectAllowed = "move";
  event.dataTransfer.setData("application/x-test-agent-config-files", JSON.stringify(paths));
  event.dataTransfer.setData("text/plain", paths[0] ?? "");
  emit("dragSourceChange", paths);
}

function canDrop(paths: string[]): boolean {
  return canReceiveFiles.value && paths.length > 0 && paths.every((path) => {
    const parent = path.includes("/") ? path.slice(0, path.lastIndexOf("/")) : "";
    return parent !== props.entry.path;
  });
}

function onDragOver(event: DragEvent) {
  const paths = dragSources(event);
  if (!canDrop(paths)) return;
  event.preventDefault();
  event.stopPropagation();
  if (event.dataTransfer) event.dataTransfer.dropEffect = "move";
  dragOver.value = true;
}

function onDrop(event: DragEvent) {
  const paths = dragSources(event);
  if (!canDrop(paths)) return;
  event.preventDefault();
  event.stopPropagation();
  dragOver.value = false;
  emit("moveEntries", paths, props.entry.path);
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
  <div class="agent-config-tree-node">
    <button
      type="button"
      :class="cn(
        'ta-file-tree-row',
        isActiveFile && 'is-active',
        isConflictFile && 'is-conflict',
        isSelected && 'is-selected',
        dragOver && 'is-drop-target',
        dragSourcePaths?.includes(entry.path) && 'is-dragging'
      )"
      :draggable="canMutateFile"
      :style="{ paddingLeft: `${indentPx}px` }"
      :title="props.entry.displayName ? `${props.entry.path} · ${treeLabel}` : props.entry.path"
      @click="onRowClick"
      @contextmenu="openContextMenu"
      @dragstart="onDragStart"
      @dragend="emit('dragSourceChange', undefined)"
      @dragover="onDragOver"
      @dragleave="dragOver = false"
      @drop="onDrop"
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
      <span v-if="!renaming" class="min-w-0 flex-1 truncate">
        <span>{{ treeLabel }}</span>
        <span v-if="treeSecondaryLabel" class="agent-tree-secondary-name"> · {{ treeSecondaryLabel }}</span>
      </span>
      <input
        v-else
        ref="renameInput"
        v-model="renameName"
        type="text"
        class="ta-file-tree-rename-input min-w-0 flex-1"
        aria-label="重命名 Agent 文件"
        @click.stop
        @keydown.enter.stop.prevent="submitRename"
        @keydown.esc.stop.prevent="cancelRename"
        @blur="submitRename"
      />
      <span v-if="isConflictFile" class="agent-tree-conflict-badge">冲突</span>
      <i v-if="isLoading" class="codicon codicon-loading codicon-modifier-spin ta-file-tree-loading" aria-hidden="true" />
    </button>
    <div v-if="canWrite" class="agent-tree-actions">
      <button
        v-if="isDirectory && (canCreateInDirectory?.(entry.path) ?? true)"
        type="button"
        class="agent-tree-action-btn is-add"
        :aria-label="`在 ${entry.name} 中新建或上传文件`"
        title="新建或上传文件"
        @click.stop="emit('createEntry', entry.path)"
      >
        <Plus class="h-3.5 w-3.5" :stroke-width="1.5" />
      </button>
      <button
        v-if="canDeleteEntry?.(entry.path) ?? true"
        type="button"
        class="agent-tree-action-btn is-delete"
        :aria-label="`删除 ${entry.name}`"
        title="删除"
        @click.stop="emit('deleteEntry', entry)"
      >
        <Trash2 class="h-3.5 w-3.5" :stroke-width="1.5" />
      </button>
    </div>
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
        :can-create-in-directory="canCreateInDirectory"
        :can-delete-entry="canDeleteEntry"
        :can-rename-entry="canRenameEntry"
        :selected-entries="selectedEntries"
        :drag-source-paths="dragSourcePaths"
        :clipboard-available="clipboardAvailable"
        @toggle="(path: string) => emit('toggle', path)"
        @open-file="(path: string) => emit('openFile', path)"
        @create-entry="(path: string) => emit('createEntry', path)"
        @delete-entry="(child: FileTreeEntry) => emit('deleteEntry', child)"
        @rename-entry="(path: string, name: string) => emit('renameEntry', path, name)"
        @selection-change="(entries: FileTreeEntry[]) => emit('selectionChange', entries)"
        @delete-entries="(entries: FileTreeEntry[]) => emit('deleteEntries', entries)"
        @set-clipboard="(entries: FileTreeEntry[], mode: 'copy' | 'move') => emit('setClipboard', entries, mode)"
        @paste-entries="(targetDirectory: string) => emit('pasteEntries', targetDirectory)"
        @move-entries="(sourcePaths: string[], targetDirectory: string) => emit('moveEntries', sourcePaths, targetDirectory)"
        @drag-source-change="(paths: string[] | undefined) => emit('dragSourceChange', paths)"
      />
    </div>
    <FileEntryContextMenu
      v-if="contextMenu"
      :x="contextMenu.x"
      :y="contextMenu.y"
      @close="closeContextMenu"
    >
        <button
          v-if="contextMenu.selection.length === 1 && canRenameEntry?.(entry.path)"
          type="button"
          role="menuitem"
          class="ta-file-context-menu-item"
          @click="renameContextEntry"
        >重命名</button>
        <button
          v-if="contextMenu.selection.length > 0"
          type="button"
          role="menuitem"
          class="ta-file-context-menu-item"
          @click="setContextClipboard('copy')"
        >复制</button>
        <button
          v-if="contextMenu.selection.length > 0"
          type="button"
          role="menuitem"
          class="ta-file-context-menu-item"
          @click="setContextClipboard('move')"
        >剪切</button>
        <button
          v-if="clipboardAvailable && canPasteHere"
          type="button"
          role="menuitem"
          class="ta-file-context-menu-item"
          @click="pasteContextEntries"
        >粘贴到此处</button>
        <button
          v-if="contextMenu.selection.length > 0"
          type="button"
          role="menuitem"
          class="ta-file-context-menu-item is-danger"
          @click="deleteContextSelection"
        >{{ contextMenu.selection.length > 1 ? `删除 ${contextMenu.selection.length} 个文件` : '删除' }}</button>
    </FileEntryContextMenu>
  </div>
</template>

<style scoped>
.agent-config-tree-node {
  position: relative;
}

.agent-config-tree-node > .ta-file-tree-row {
  padding-right: 48px;
}

.ta-file-tree-row.is-dragging {
  opacity: 0.55;
}

.ta-file-tree-row.is-selected,
.ta-file-tree-row.is-drop-target {
  outline: 1px solid var(--ta-accent, #2563eb);
  outline-offset: -1px;
  background: rgb(37 99 235 / 12%);
}

.agent-tree-actions {
  position: absolute;
  top: 2px;
  right: 4px;
  display: flex;
  height: 18px;
  align-items: center;
  gap: 2px;
  opacity: 0;
}

.agent-tree-action-btn {
  display: inline-flex;
  width: 18px;
  height: 18px;
  padding: 0;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--ta-tree-muted, #8b949e);
  cursor: pointer;
  transition: background-color 0.12s ease, color 0.12s ease;
}

.agent-config-tree-node:hover > .agent-tree-actions,
.agent-tree-actions:focus-within {
  opacity: 1;
}

.agent-tree-action-btn.is-add:hover {
  background: var(--ta-hover, #f1f5f9);
  color: var(--ta-tree-text, #3b3b3b);
}

.agent-tree-action-btn.is-delete:hover {
  background: rgba(239, 68, 68, 0.1);
  color: var(--ta-danger, #dc2626);
}

.agent-tree-action-btn:focus-visible {
  outline: 2px solid var(--ta-accent, #3366ff);
  outline-offset: 1px;
}

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

.agent-tree-secondary-name {
  color: var(--ta-tree-muted, #8b949e);
  font-size: 11px;
}
</style>
