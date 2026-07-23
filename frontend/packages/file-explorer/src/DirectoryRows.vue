<script lang="ts">
import type { FileTreeEntry, WorkspaceViewEntry } from "@test-agent/shared-types";

export type DirectoryRowsProps = {
  directory: string;
  entriesByDirectory: Record<string, FileTreeEntry[]>;
  expandedDirectories: Set<string>;
  activePath?: string;
  loadingPath?: Set<string>;
  depth?: number;
  /** 只读工作区隐藏并阻断所有文件系统写入口。 */
  canWrite?: boolean;
  /** 当前个人 worktree 是否存在可撤销的文件操作。 */
  canUndo?: boolean;
  /** 根组件递增该值，统一清理递归目录残留的拖放高亮。 */
  dragResetToken?: number;
  /** 根组件保存的内部拖源相对路径，递归行共享它以统一校验落点。 */
  dragSourcePaths?: string[];
  /** 兼容旧调用方的单项拖源；新交互统一使用 dragSourcePaths。 */
  dragSourcePath?: string;
  /** Ctrl/Cmd 多选的可写工作区条目，由文件树根组件统一持有。 */
  selectedEntries?: WorkspaceSelectionEntry[];
  /** 文件路径 → 行变更统计，用于在文件名后展示 +N -N。 */
  changeStats?: Record<string, { additions: number; deletions: number }>;
  /** 文件树内部剪贴板，仅保存当前工作区的普通文件引用。 */
  clipboardEntry?: WorkspaceClipboardEntry;
};

export type WorkspaceClipboardEntry = {
  /** 新版多选剪贴板；path 仅保留给旧调用方兼容。 */
  entries?: WorkspaceSelectionEntry[];
  path?: string;
  mode: "copy" | "move";
};

export type WorkspaceSelectionEntry = {
  path: string;
  type: "file" | "directory";
};
</script>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { Plane, Plus, Trash2 } from "lucide-vue-next";
import { cn } from "@test-agent/ui-kit";
import FileEntryCreateDialog from "./FileEntryCreateDialog.vue";
import FileEntryContextMenu from "./FileEntryContextMenu.vue";
import FileEntryDeleteDialog from "./FileEntryDeleteDialog.vue";
import FileIcon from "./FileIcon.vue";

const props = withDefaults(defineProps<DirectoryRowsProps>(), { depth: 0, canWrite: true });
const emit = defineEmits<{
  toggleDirectory: [path: string];
  toggleViewDirectory: [entry: WorkspaceViewEntry];
  openFile: [path: string];
  openViewFile: [entry: WorkspaceViewEntry];
  addFileContext: [path: string];
  addViewFileContext: [entry: WorkspaceViewEntry];
  createEntry: [directory: string, name: string, type: "file" | "directory"];
  deleteEntry: [path: string, type: "file" | "directory"];
  deleteEntries: [entries: WorkspaceSelectionEntry[]];
  renameEntry: [path: string, name: string];
  setClipboard: [path: string, mode: "copy" | "move"];
  setClipboardEntries: [entries: WorkspaceSelectionEntry[], mode: "copy" | "move"];
  pasteEntry: [directory: string];
  undoEntry: [];
  moveEntry: [sourcePath: string, targetDirectory: string];
  moveEntries: [sourcePaths: string[], targetDirectory: string];
  uploadFiles: [directory: string, files: File[]];
  requestUpload: [directory: string];
  cacheAndNavigate: [path: string, type: "file" | "directory"];
  dragSourceChange: [paths: string[] | undefined];
  selectionChange: [entries: WorkspaceSelectionEntry[]];
}>();

const entries = computed(() => {
  const list = props.entriesByDirectory[props.directory] ?? [];
  return [...list].sort((a, b) => {
    if (a.type === b.type) return 0;
    return a.type === "directory" ? -1 : 1;
  });
});

type MaybeWorkspaceViewEntry = FileTreeEntry & Partial<WorkspaceViewEntry>;

function nodeId(entry: MaybeWorkspaceViewEntry): string {
  return entry.id ?? entry.path;
}

function isWorkspaceViewEntry(entry: MaybeWorkspaceViewEntry): entry is WorkspaceViewEntry {
  return Boolean(entry.id && entry.locator && entry.source);
}

function canMutateEntry(entry: MaybeWorkspaceViewEntry): boolean {
  return props.canWrite && entry.readonly !== true && entry.source !== "REFERENCE" && entry.source !== "MIXED";
}

function canWriteChildren(entry: MaybeWorkspaceViewEntry): boolean {
  if (!props.canWrite || entry.type !== "directory" || entry.readonly === true || entry.source === "REFERENCE") return false;
  return entry.source !== "MIXED" || Boolean(entry.workspacePath);
}

function canPasteIntoEntry(entry: MaybeWorkspaceViewEntry): boolean {
  return entry.type === "directory" ? canWriteChildren(entry) : canMutateEntry(entry);
}

function canUndoFromEntry(entry: MaybeWorkspaceViewEntry): boolean {
  return props.canWrite && Boolean(props.canUndo) && entry.readonly !== true && entry.source !== "REFERENCE";
}

function workspaceEntryPath(entry: MaybeWorkspaceViewEntry): string {
  return entry.workspacePath ?? entry.path;
}

function sourceDescription(entry: MaybeWorkspaceViewEntry): string | undefined {
  const aliases = entry.referenceAliases?.join("、") || entry.locator?.referenceAlias;
  if (entry.source === "REFERENCE" && entry.collision) return `引用冲突：${aliases || "未知来源"}`;
  if (entry.source === "REFERENCE") return `引用来源：${aliases || "未知来源"}`;
  if (entry.source === "MIXED") return aliases ? `工作区与引用合并目录；引用来源：${aliases}` : "工作区与引用合并目录";
  return undefined;
}

function semanticClass(entry: MaybeWorkspaceViewEntry): string | undefined {
  if (entry.source !== "REFERENCE") return undefined;
  if (entry.collision) return "is-reference-collision";
  return entry.merged ? "is-reference-merged" : undefined;
}

function showsWorkspaceChangeStats(entry: MaybeWorkspaceViewEntry): boolean {
  return entry.source === undefined || entry.source === "WORKSPACE";
}

const entryContextMenu = ref<{
  entry: FileTreeEntry;
  selection: WorkspaceSelectionEntry[];
  x: number;
  y: number;
} | null>(null);
const dragOverDirectory = ref<string | null>(null);
const createDialog = ref<InstanceType<typeof FileEntryCreateDialog> | null>(null);
const deleteDialog = ref<InstanceType<typeof FileEntryDeleteDialog> | null>(null);
const renamingPath = ref<string | null>(null);
const renameName = ref("");
const renameOriginalName = ref("");
const renameError = ref("");
const renameInput = ref<HTMLInputElement | null>(null);

function focusRenameInput() {
  // ref 位于递归 v-for 行内，Vue 运行时可能返回元素数组；这里统一取当前编辑行。
  const candidate = renameInput.value as HTMLInputElement | HTMLInputElement[] | null;
  const input = Array.isArray(candidate) ? candidate[0] : candidate;
  input?.focus();
  input?.select();
}

function openFileContextMenu(event: MouseEvent, entry: FileTreeEntry) {
  event.preventDefault();
  const selection = selectedForEntry(entry);
  // 组合/引用目录没有变更权限且剪贴板为空时，不创建只剩边框的空白菜单。
  const hasAction = selection.length > 0
    || entry.type === "file"
    || Boolean(props.clipboardEntry && canPasteIntoEntry(entry))
    || canUndoFromEntry(entry);
  if (!hasAction) {
    closeFileContextMenu();
    return;
  }
  if (!isSelected(entry)) emit("selectionChange", selection);
  entryContextMenu.value = { entry, selection, x: event.clientX, y: event.clientY };
}

function closeFileContextMenu() {
  entryContextMenu.value = null;
}

function emitAddFileContext() {
  const entry = entryContextMenu.value?.entry;
  if (!entry || entry.type !== "file") {
    return;
  }
  if (isWorkspaceViewEntry(entry)) emit("addViewFileContext", entry);
  else emit("addFileContext", entry.path);
  closeFileContextMenu();
}

function parentDirectory(path: string): string {
  const segments = normalizePath(path).split("/");
  segments.pop();
  return segments.join("/");
}

/** 拖放路径仅按完整目录段比较，统一兼容服务端返回的 Windows 分隔符。 */
function normalizePath(path: string): string {
  return path.split(/[\\/]+/).filter(Boolean).join("/");
}

function selectionEntry(entry: FileTreeEntry): WorkspaceSelectionEntry {
  return { path: normalizePath(workspaceEntryPath(entry)), type: entry.type };
}

function isSelected(entry: FileTreeEntry): boolean {
  const path = normalizePath(workspaceEntryPath(entry));
  return props.selectedEntries?.some((item) => normalizePath(item.path) === path) ?? false;
}

/** 父目录已被选中时忽略其后代，避免批量移动或删除重复操作同一棵子树。 */
function topLevelSelection(items: WorkspaceSelectionEntry[]): WorkspaceSelectionEntry[] {
  const normalized = items.map((item) => ({ ...item, path: normalizePath(item.path) }));
  return normalized.filter((item) => !normalized.some((parent) =>
    parent.type === "directory" && parent.path !== item.path && isDescendantPath(parent.path, item.path)
  ));
}

function selectedForEntry(entry: FileTreeEntry): WorkspaceSelectionEntry[] {
  if (!canMutateEntry(entry)) return [];
  if (isSelected(entry)) return topLevelSelection(props.selectedEntries ?? []);
  return [selectionEntry(entry)];
}

function isDescendantPath(path: string, candidate: string): boolean {
  const sourceSegments = normalizePath(path).split("/").filter(Boolean);
  const targetSegments = normalizePath(candidate).split("/").filter(Boolean);
  return targetSegments.length > sourceSegments.length && sourceSegments.every((segment, index) => segment === targetSegments[index]);
}

function internalDragSources(event: DragEvent): string[] {
  const transfer = event.dataTransfer;
  const transferredPaths = transfer && typeof transfer.getData === "function"
    ? transfer.getData("application/x-test-agent-workspace-files")
    : "";
  if (transferredPaths) {
    try {
      const parsed = JSON.parse(transferredPaths);
      if (Array.isArray(parsed)) return parsed.map(String).map(normalizePath).filter(Boolean);
    } catch {
      // 兼容旧单项拖动数据，解析失败后继续读取单路径 MIME。
    }
  }
  const transferredPath = transfer && typeof transfer.getData === "function"
    ? transfer.getData("application/x-test-agent-workspace-file")
    : "";
  const paths = props.dragSourcePaths?.length ? props.dragSourcePaths : [props.dragSourcePath ?? transferredPath];
  return paths.map(normalizePath).filter(Boolean);
}

/** 同目录、自身或目录后代都会让服务端移动语义失效，必须在树内提前阻断。 */
function canMoveIntoDirectory(sourcePaths: string[], entry: MaybeWorkspaceViewEntry): boolean {
  if (sourcePaths.length === 0 || !canWriteChildren(entry)) return false;
  const targetPath = normalizePath(workspaceEntryPath(entry));
  return sourcePaths.every((sourcePath) =>
    targetPath !== sourcePath && parentDirectory(sourcePath) !== targetPath && !isDescendantPath(sourcePath, targetPath)
  );
}

function isDraggingEntry(entry: MaybeWorkspaceViewEntry): boolean {
  const path = normalizePath(workspaceEntryPath(entry));
  return (props.dragSourcePaths ?? [props.dragSourcePath ?? ""])
    .some((source) => normalizePath(source) === path);
}

function targetDirectory(entry: FileTreeEntry): string {
  return entry.type === "directory" ? workspaceEntryPath(entry) : parentDirectory(workspaceEntryPath(entry));
}

function emitSetClipboard(mode: "copy" | "move") {
  const context = entryContextMenu.value;
  if (!context || context.selection.length === 0) return;
  if (mode === "copy" && context.selection.some((entry) => entry.type !== "file")) return;
  if (context.selection.length === 1) emit("setClipboard", context.selection[0]!.path, mode);
  emit("setClipboardEntries", context.selection, mode);
  closeFileContextMenu();
}

function emitPasteEntry() {
  const entry = entryContextMenu.value?.entry;
  if (!props.clipboardEntry || !entry || !canPasteIntoEntry(entry)) return;
  emit("pasteEntry", targetDirectory(entry));
  closeFileContextMenu();
}

/** 文件树行聚焦后支持 Delete 和 Ctrl/Cmd+C、X、V、Z，所有写操作复用现有确认或事件链路。 */
function onRowKeydown(event: KeyboardEvent, entry: FileTreeEntry) {
  if (event.target instanceof HTMLInputElement) return;
  const key = event.key.toLowerCase();
  if (key === "delete" || key === "del") {
    event.preventDefault();
    if (canMutateEntry(entry)) openDeleteDialog(entry, selectedForEntry(entry));
    return;
  }
  if (!event.ctrlKey && !event.metaKey) return;
  if ((key === "c" || key === "x") && canMutateEntry(entry)) {
    const selection = selectedForEntry(entry);
    if (key === "c" && selection.some((item) => item.type !== "file")) return;
    event.preventDefault();
    if (selection.length === 1) emit("setClipboard", selection[0]!.path, key === "c" ? "copy" : "move");
    emit("setClipboardEntries", selection, key === "c" ? "copy" : "move");
    return;
  }
  if (key === "v" && props.clipboardEntry && canPasteIntoEntry(entry)) {
    event.preventDefault();
    emit("pasteEntry", targetDirectory(entry));
    return;
  }
  if (key === "z" && canUndoFromEntry(entry)) {
    event.preventDefault();
    emit("undoEntry");
  }
}

watch(() => props.dragResetToken, () => {
  dragOverDirectory.value = null;
});

/** 可写工作区文件和目录都使用 HTML5 drag data 传递相对路径。 */
function onDragStart(event: DragEvent, entry: FileTreeEntry) {
  if (!canMutateEntry(entry) || !event.dataTransfer) {
    event.preventDefault();
    return;
  }
  const selected = selectedForEntry(entry);
  const sourcePaths = selected.map((item) => item.path);
  const sourcePath = sourcePaths[0]!;
  if (!isSelected(entry)) emit("selectionChange", selected);
  event.dataTransfer.effectAllowed = "move";
  event.dataTransfer.setData("application/x-test-agent-workspace-files", JSON.stringify(sourcePaths));
  event.dataTransfer.setData("application/x-test-agent-workspace-file", sourcePath);
  event.dataTransfer.setData("text/plain", sourcePath);
  emit("dragSourceChange", sourcePaths);
}

function onDirectoryDragOver(event: DragEvent, entry: FileTreeEntry) {
  const sourcePaths = internalDragSources(event);
  if (entry.type !== "directory" || !canWriteChildren(entry) || (sourcePaths.length > 0 && !canMoveIntoDirectory(sourcePaths, entry))) {
    // 非法树内落点必须吞掉事件，避免错误冒泡到工作区根目录触发移动或上传。
    event.preventDefault();
    event.stopPropagation();
    if (event.dataTransfer) event.dataTransfer.dropEffect = "none";
    dragOverDirectory.value = null;
    return;
  }
  event.preventDefault();
  event.stopPropagation();
  if (event.dataTransfer) event.dataTransfer.dropEffect = sourcePaths.length > 0 ? "move" : "copy";
  dragOverDirectory.value = nodeId(entry);
}

function onDirectoryDrop(event: DragEvent, entry: FileTreeEntry) {
  const sourcePaths = internalDragSources(event);
  if (entry.type !== "directory" || !canWriteChildren(entry) || (sourcePaths.length > 0 && !canMoveIntoDirectory(sourcePaths, entry)) || !event.dataTransfer) {
    event.preventDefault();
    event.stopPropagation();
    if (event.dataTransfer) event.dataTransfer.dropEffect = "none";
    dragOverDirectory.value = null;
    return;
  }
  event.preventDefault();
  event.stopPropagation();
  const files = Array.from(event.dataTransfer.files ?? []);
  if (files.length > 0) {
    dragOverDirectory.value = null;
    emit("uploadFiles", workspaceEntryPath(entry), files);
    return;
  }
  dragOverDirectory.value = null;
  if (sourcePaths.length > 1) emit("moveEntries", sourcePaths, workspaceEntryPath(entry));
  else if (sourcePaths[0]) emit("moveEntry", sourcePaths[0], workspaceEntryPath(entry));
}

function onDragEnd() {
  emit("dragSourceChange", undefined);
}

function isKnownEmptyDirectory(entry: FileTreeEntry): boolean {
  const children = props.entriesByDirectory[nodeId(entry)];
  return Array.isArray(children) && children.length === 0;
}

function onRowClick(event: MouseEvent, entry: FileTreeEntry) {
  if ((event.ctrlKey || event.metaKey) && canMutateEntry(entry)) {
    const current = props.selectedEntries ?? [];
    const path = normalizePath(workspaceEntryPath(entry));
    const next = isSelected(entry)
      ? current.filter((item) => normalizePath(item.path) !== path)
      : [...current, selectionEntry(entry)];
    emit("selectionChange", next);
    return;
  }
  if (props.selectedEntries?.length) emit("selectionChange", []);
  if (entry.type === "directory") {
    if (isKnownEmptyDirectory(entry)) {
      return;
    }
    if (isWorkspaceViewEntry(entry)) emit("toggleViewDirectory", entry);
    else emit("toggleDirectory", entry.path);
  } else {
    if (isWorkspaceViewEntry(entry)) emit("openViewFile", entry);
    else emit("openFile", entry.path);
  }
}

function openCreateDialog(directory: string) {
  if (!props.canWrite) return;
  createDialog.value?.open(directory);
}

/** 根目录标题与目录行的“+”共用同一个明确目标路径的操作面板。 */
defineExpose({ openCreateDialog });

function openDeleteDialog(entry: FileTreeEntry, selection: WorkspaceSelectionEntry[] = [selectionEntry(entry)]) {
  if (!canMutateEntry(entry)) return;
  const targets = topLevelSelection(selection);
  if (targets.length > 1) deleteDialog.value?.openMany(targets);
  else if (targets[0]) deleteDialog.value?.open(targets[0]);
}

function deleteContextSelection() {
  const context = entryContextMenu.value;
  if (!context) return;
  closeFileContextMenu();
  openDeleteDialog(context.entry, context.selection);
}

function renameContextEntry() {
  const entry = entryContextMenu.value?.entry;
  closeFileContextMenu();
  if (entry) startRename(entry);
}

function startRename(entry: FileTreeEntry) {
  if (!canMutateEntry(entry)) return;
  renamingPath.value = workspaceEntryPath(entry);
  renameName.value = entry.name;
  renameOriginalName.value = entry.name;
  renameError.value = "";
  void nextTick(focusRenameInput);
}

function cancelRename() {
  renamingPath.value = null;
  renameName.value = "";
  renameOriginalName.value = "";
  renameError.value = "";
}

function submitRename() {
  if (!props.canWrite) return;
  const path = renamingPath.value;
  if (!path) {
    return;
  }
  const name = renameName.value.trim();
  if (!name) {
    renameError.value = "请输入名称";
    void nextTick(focusRenameInput);
    return;
  }
  if (name.includes("/") || name.includes("\\") || name === "." || name === "..") {
    renameError.value = "名称不能包含路径分隔符";
    void nextTick(focusRenameInput);
    return;
  }
  if (name === renameOriginalName.value) {
    cancelRename();
    return;
  }
  emit("renameEntry", path, name);
  cancelRename();
}
</script>

<template>
  <div>
    <div v-for="entry in entries" :key="nodeId(entry)" class="ta-file-tree-row-wrapper">
      <button
        type="button"
        :class="cn(
          'ta-file-tree-row',
          (isWorkspaceViewEntry(entry) ? activePath === nodeId(entry) : activePath === entry.path) && 'is-active',
          isSelected(entry) && 'is-selected',
          dragOverDirectory === nodeId(entry) && 'is-drop-target',
          canMutateEntry(entry) && 'is-draggable',
          isDraggingEntry(entry) && 'is-dragging',
          semanticClass(entry)
        )"
        :draggable="canMutateEntry(entry)"
        :title="sourceDescription(entry)"
        :style="{
          paddingLeft: depth * 16 + 6 + 'px',
          paddingRight: canWrite
            ? (entry.type === 'directory' ? (entry.name.includes('测试执行') ? '68px' : '48px') : '26px')
            : (entry.type === 'directory' && entry.name.includes('测试执行') ? '26px' : '6px')
        }"
        @click="onRowClick($event, entry)"
        @contextmenu="openFileContextMenu($event, entry)"
        @keydown="onRowKeydown($event, entry)"
        @dragstart="onDragStart($event, entry)"
        @dragend="onDragEnd"
        @dragover="onDirectoryDragOver($event, entry)"
        @dragleave="dragOverDirectory === nodeId(entry) && (dragOverDirectory = null)"
        @drop="onDirectoryDrop($event, entry)"
      >
        <span
          v-for="i in depth"
          :key="i"
          class="ta-file-tree-indent-guide"
          :style="{ left: 13 + (i - 1) * 16 + 'px' }"
        />
        <template v-if="entry.type === 'directory'">
          <i
            v-if="!isKnownEmptyDirectory(entry)"
            :class="cn('codicon codicon-chevron-right ta-file-tree-twistie', expandedDirectories.has(nodeId(entry)) && 'is-open')"
            aria-hidden="true"
          />
          <span v-else class="ta-file-tree-spacer" />
          <i class="codicon codicon-folder ta-file-tree-source-icon" aria-hidden="true" />
        </template>
        <template v-else>
          <span class="ta-file-tree-file-spacer" />
          <FileIcon :entry="entry" />
        </template>
        <span v-if="renamingPath !== workspaceEntryPath(entry)" class="min-w-0 flex-1 truncate">{{ entry.name }}</span>
        <input
          v-else
          ref="renameInput"
          v-model="renameName"
          type="text"
          class="ta-file-tree-rename-input min-w-0 flex-1"
          aria-label="重命名工作区条目"
          @click.stop
          @keydown.enter.stop.prevent="submitRename"
          @keydown.esc.stop.prevent="cancelRename"
          @blur="submitRename"
        />
        <template v-if="entry.type === 'file' && showsWorkspaceChangeStats(entry) && changeStats?.[workspaceEntryPath(entry)]">
          <span class="ta-file-tree-badge is-added">+{{ changeStats[workspaceEntryPath(entry)]?.additions }}</span>
          <span class="ta-file-tree-badge is-deleted">-{{ changeStats[workspaceEntryPath(entry)]?.deletions }}</span>
        </template>
        <i v-if="loadingPath?.has(nodeId(entry))" class="codicon codicon-loading codicon-modifier-spin ta-file-tree-loading" aria-hidden="true" />
      </button>
      <div class="ta-file-tree-actions">
        <button
          v-if="canWriteChildren(entry)"
          type="button"
          class="ta-file-tree-add-btn"
          title="新建或上传到此目录"
          aria-label="新建或上传到此目录"
          @click.stop="openCreateDialog(workspaceEntryPath(entry))"
        >
          <Plus class="h-3.5 w-3.5" :stroke-width="1.5" />
        </button>
        <button
          v-if="canMutateEntry(entry)"
          type="button"
          class="ta-file-tree-delete-btn"
          title="删除"
          :aria-label="`删除 ${entry.name}`"
          @click.stop="openDeleteDialog(entry)"
        >
          <Trash2 class="h-3.5 w-3.5" :stroke-width="1.5" />
        </button>
        <button
          v-if="canMutateEntry(entry) && entry.type === 'directory' && entry.name.includes('测试执行')"
          type="button"
          class="ta-file-tree-plane-btn"
          title="缓存并跳转"
          aria-label="缓存并跳转"
          @click.stop="emit('cacheAndNavigate', entry.path, entry.type)"
        >
          <Plane class="h-3.5 w-3.5" :stroke-width="1.5" />
        </button>
      </div>
      <div v-if="renamingPath === workspaceEntryPath(entry) && renameError" class="ta-file-tree-rename-error">
        {{ renameError }}
      </div>
      <DirectoryRows
        v-if="entry.type === 'directory' && expandedDirectories.has(nodeId(entry))"
        :directory="nodeId(entry)"
        :entries-by-directory="entriesByDirectory"
        :expanded-directories="expandedDirectories"
        :active-path="activePath"
        :loading-path="loadingPath"
        :change-stats="changeStats"
        :can-write="canWrite"
        :can-undo="canUndo"
        :drag-reset-token="dragResetToken"
        :drag-source-paths="dragSourcePaths"
        :selected-entries="selectedEntries"
        :clipboard-entry="clipboardEntry"
        :depth="depth + 1"
        @toggle-directory="emit('toggleDirectory', $event)"
        @toggle-view-directory="emit('toggleViewDirectory', $event)"
        @open-file="emit('openFile', $event)"
        @open-view-file="emit('openViewFile', $event)"
        @add-file-context="emit('addFileContext', $event)"
        @add-view-file-context="emit('addViewFileContext', $event)"
        @create-entry="(directory, name, type) => emit('createEntry', directory, name, type)"
        @delete-entry="(path, type) => emit('deleteEntry', path, type)"
        @delete-entries="emit('deleteEntries', $event)"
        @rename-entry="(path, name) => emit('renameEntry', path, name)"
        @set-clipboard="(path, mode) => emit('setClipboard', path, mode)"
        @set-clipboard-entries="(entries, mode) => emit('setClipboardEntries', entries, mode)"
        @paste-entry="emit('pasteEntry', $event)"
        @undo-entry="emit('undoEntry')"
        @move-entry="(sourcePath, targetDirectory) => emit('moveEntry', sourcePath, targetDirectory)"
        @move-entries="(sourcePaths, targetDirectory) => emit('moveEntries', sourcePaths, targetDirectory)"
        @upload-files="(directory, files) => emit('uploadFiles', directory, files)"
        @request-upload="emit('requestUpload', $event)"
        @cache-and-navigate="(path, type) => emit('cacheAndNavigate', path, type)"
        @drag-source-change="emit('dragSourceChange', $event)"
        @selection-change="emit('selectionChange', $event)"
      />
    </div>
    <FileEntryContextMenu
      v-if="entryContextMenu"
      :x="entryContextMenu.x"
      :y="entryContextMenu.y"
      @close="closeFileContextMenu"
    >
        <button
          v-if="entryContextMenu.selection.length === 1 && canMutateEntry(entryContextMenu.entry)"
          type="button"
          role="menuitem"
          class="ta-file-context-menu-item"
          @click="renameContextEntry"
        >
          重命名
        </button>
        <button
          v-if="entryContextMenu.selection.length > 0"
          type="button"
          role="menuitem"
          class="ta-file-context-menu-item is-danger"
          @click="deleteContextSelection"
        >
          {{ entryContextMenu.selection.length > 1 ? `删除 ${entryContextMenu.selection.length} 个条目` : '删除' }}
        </button>
        <button
          v-if="entryContextMenu.entry.type === 'file'"
          type="button"
          role="menuitem"
          class="ta-file-context-menu-item"
          @click="emitAddFileContext"
        >
          添加文件到对话
        </button>
        <button
          v-if="entryContextMenu.selection.length > 0 && entryContextMenu.selection.every((entry) => entry.type === 'file')"
          type="button"
          role="menuitem"
          class="ta-file-context-menu-item"
          @click="emitSetClipboard('copy')"
        >
          复制
          <span>Ctrl/Cmd+C</span>
        </button>
        <button
          v-if="entryContextMenu.selection.length > 0"
          type="button"
          role="menuitem"
          class="ta-file-context-menu-item"
          @click="emitSetClipboard('move')"
        >
          剪切
          <span>Ctrl/Cmd+X</span>
        </button>
        <button
          v-if="clipboardEntry && canPasteIntoEntry(entryContextMenu.entry)"
          type="button"
          role="menuitem"
          class="ta-file-context-menu-item"
          @click="emitPasteEntry"
        >
          粘贴到此处
          <span>Ctrl/Cmd+V</span>
        </button>
        <button
          v-if="canUndoFromEntry(entryContextMenu.entry)"
          type="button"
          role="menuitem"
          class="ta-file-context-menu-item"
          @click="emit('undoEntry'); closeFileContextMenu()"
        >
          撤销上一步
          <span>Ctrl/Cmd+Z</span>
        </button>
    </FileEntryContextMenu>
    <FileEntryCreateDialog
      ref="createDialog"
      @create-entry="(directory, name, type) => emit('createEntry', directory, name, type)"
      @request-upload="emit('requestUpload', $event)"
    />
    <FileEntryDeleteDialog
      ref="deleteDialog"
      @confirm="(path, type) => emit('deleteEntry', path, type)"
      @confirm-many="emit('deleteEntries', $event)"
    />
  </div>
</template>

<style scoped>
.ta-file-tree-row.is-draggable {
  cursor: grab;
}

.ta-file-tree-row.is-dragging {
  cursor: grabbing;
  opacity: 0.55;
}

.ta-file-tree-row.is-selected {
  outline: 1px solid var(--ta-accent, #2563eb);
  outline-offset: -1px;
  background: rgb(37 99 235 / 12%);
}
</style>

<style scoped>
.ta-file-dialog-overlay {
  position: fixed;
  inset: 0;
  z-index: 2700;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.4);
  backdrop-filter: blur(8px);
  animation: ta-fade-in 200ms cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes ta-fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

.ta-file-dialog {
  width: min(440px, calc(100vw - 28px));
  overflow: hidden;
  border: 1px solid var(--ta-border-strong, #cfcfcf);
  border-radius: 12px;
  background: var(--ta-panel-2, #ffffff);
  box-shadow: 0 24px 48px -12px rgba(0, 0, 0, 0.18), 0 0 0 1px rgba(0, 0, 0, 0.02);
  color: var(--ta-text, #333333);
  animation: ta-file-dialog-enter 220ms cubic-bezier(0.16, 1, 0.3, 1);
  display: flex;
  flex-direction: column;
}

.ta-file-dialog-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 20px;
  border-bottom: 1px solid var(--ta-border, #eaeaea);
  background: var(--ta-surface, #ffffff);
}

.ta-file-dialog-heading {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 12px;
}

.ta-file-dialog-heading h2 {
  margin: 0;
  color: var(--ta-text, #333333);
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
}

.ta-file-dialog-heading p {
  margin: 4px 0 0;
  color: var(--ta-muted, #7a7a7a);
  font-size: 12px;
  line-height: 1.4;
}

.ta-file-dialog-icon {
  display: inline-flex;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border: 1px solid #cce0ff;
  border-radius: 8px;
  background: #ecf3fe;
  color: #2563eb;
  transition: transform 0.2s ease;
}

.ta-file-dialog--danger .ta-file-dialog-icon {
  border-color: #fecaca;
  background: #fef2f2;
  color: var(--ta-error, #9e3b34);
}

.ta-file-dialog-close {
  display: inline-flex;
  width: 28px;
  height: 28px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--ta-muted, #7a7a7a);
  cursor: pointer;
  transition: all 0.15s ease;
  margin-top: -2px;
  margin-right: -4px;
}

.ta-file-dialog-close:hover {
  background: var(--ta-hover, #eef1f5);
  color: var(--ta-text, #333333);
}

.ta-file-dialog-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px;
  background: var(--ta-panel-2, #ffffff);
}

.ta-file-dialog-path {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ta-file-dialog-path span,
.ta-file-dialog-field > label {
  color: var(--ta-muted, #7a7a7a);
  font-size: 12px;
  font-weight: 500;
}

.ta-file-dialog-path code {
  overflow: hidden;
  border: 1px solid var(--ta-border, #eaeaea);
  border-radius: 8px;
  background: var(--ta-bg, #f0f4fa);
  padding: 8px 12px;
  color: var(--ta-subtle, #444444);
  font-family: var(--font-mono, "Geist Mono", monospace);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
  letter-spacing: -0.01em;
}

.ta-file-dialog-field {
  display: grid;
  gap: 6px;
}

.ta-file-dialog-segments {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 4px;
  border: 1px solid var(--ta-border, #eaeaea);
  border-radius: 8px;
  background: var(--ta-bg, #f0f4fa);
  padding: 4px;
}

.ta-file-dialog-segments button {
  min-height: 30px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--ta-muted, #7a7a7a);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s cubic-bezier(0.16, 1, 0.3, 1);
}

.ta-file-dialog-segments button:hover {
  color: var(--ta-text, #333333);
  background: rgba(0, 0, 0, 0.03);
}

.ta-file-dialog-segments button.is-active {
  background: var(--ta-surface, #ffffff);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  color: var(--ta-text, #333333);
  font-weight: 600;
}

.ta-file-dialog-input {
  width: 100%;
  height: 38px;
  border: 1px solid var(--ta-border-strong, #cfcfcf);
  border-radius: 8px;
  outline: none;
  background: var(--ta-surface, #fff);
  padding: 0 12px;
  color: var(--ta-text, #333333);
  font-size: 13px;
  transition: all 0.15s ease;
}

.ta-file-dialog-input:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
}

.ta-file-dialog-input::placeholder {
  color: #9aa3b2;
}

.ta-file-dialog-error {
  color: var(--ta-error, #9e3b34);
  font-size: 12px;
  margin-top: 2px;
}

.ta-file-dialog-upload-note {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  gap: 10px;
  border: 2px dashed #bfdbfe;
  border-radius: 10px;
  background: #f0f7ff;
  padding: 24px 16px;
  color: #2563eb;
  cursor: pointer;
  transition: all 0.2s ease;
}

.ta-file-dialog-upload-note:hover {
  border-color: #3b82f6;
  background: #eff6ff;
  transform: translateY(-1px);
}

.ta-file-dialog-upload-note svg {
  color: #3b82f6;
  animation: ta-bounce 1s infinite alternate;
}

@keyframes ta-bounce {
  from { transform: translateY(0); }
  to { transform: translateY(-4px); }
}

.ta-file-dialog-upload-note div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ta-file-dialog-upload-note strong {
  color: #1e40af;
  font-size: 13px;
  font-weight: 600;
}

.ta-file-dialog-upload-note span {
  color: #60a5fa;
  font-size: 11px;
  line-height: 1.4;
  max-width: 240px;
  margin: 0 auto;
}

.ta-file-dialog-danger-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  border: 1px solid #fee2e2;
  border-radius: 10px;
  background: #fef2f2;
  padding: 14px 16px;
}

.ta-file-dialog-danger-card span {
  color: #ef4444;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.ta-file-dialog-danger-card strong {
  overflow-wrap: anywhere;
  color: #991b1b;
  font-family: var(--font-mono, "Geist Mono", monospace);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
}

.ta-file-dialog-warning {
  margin: 0;
  color: var(--ta-muted, #7a7a7a);
  font-size: 12px;
  line-height: 1.5;
}

.ta-file-dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 20px;
  border-top: 1px solid var(--ta-border, #eaeaea);
  background: var(--ta-surface, #ffffff);
}

.ta-file-dialog-button {
  min-width: 80px;
  height: 34px;
  border: 1px solid var(--ta-border-strong, #cfcfcf);
  border-radius: 8px;
  background: var(--ta-surface, #fff);
  color: var(--ta-text, #333333);
  padding: 0 16px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}

.ta-file-dialog-button:hover {
  border-color: var(--ta-text, #333333);
  background: var(--ta-hover, #f1f3f6);
}

.ta-file-dialog-button.is-primary {
  border-color: #2563eb;
  background: #2563eb;
  color: #fff;
  font-weight: 500;
  box-shadow: 0 2px 4px rgba(37, 99, 235, 0.15);
}

.ta-file-dialog-button.is-primary:hover {
  border-color: #1d4ed8;
  background: #1d4ed8;
  box-shadow: 0 4px 6px rgba(29, 78, 216, 0.2);
}

.ta-file-dialog-button.is-danger {
  border-color: #dc2626;
  background: #dc2626;
  color: #fff;
  font-weight: 500;
  box-shadow: 0 2px 4px rgba(220, 38, 38, 0.15);
}

.ta-file-dialog-button.is-danger:hover {
  border-color: #b91c1c;
  background: #b91c1c;
  box-shadow: 0 4px 6px rgba(185, 28, 28, 0.2);
}

.ta-file-dialog-button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
  box-shadow: none !important;
}

.ta-file-dialog-button:focus-visible,
.ta-file-dialog-close:focus-visible,
.ta-file-dialog-segments button:focus-visible {
  outline: 2px solid var(--ta-accent, #3366ff);
  outline-offset: 1px;
}

@keyframes ta-file-dialog-enter {
  from { opacity: 0; transform: scale(0.96) translateY(8px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}

@media (prefers-reduced-motion: reduce) {
  .ta-file-dialog { animation: none; }
}

:deep(.ta-file-tree-row.is-drop-target) {
  outline: 1px solid var(--ta-accent, #2563eb);
  outline-offset: -1px;
  background: rgb(37 99 235 / 10%);
}

.ta-file-tree-row.is-reference-merged {
  color: var(--ta-reference-merged, #2563eb);
}

.ta-file-tree-row.is-reference-collision {
  color: var(--ta-reference-collision, #dc2626);
}

.ta-file-tree-row.is-reference-merged:focus-visible,
.ta-file-tree-row.is-reference-merged.is-active {
  color: var(--ta-reference-merged-active, #1d4ed8);
}

.ta-file-tree-row.is-reference-collision:focus-visible,
.ta-file-tree-row.is-reference-collision.is-active {
  color: var(--ta-reference-collision-active, #b91c1c);
}

.ta-file-tree-source-icon {
  width: 16px;
  flex: 0 0 16px;
  color: currentColor;
}

/* sprite 内部带固定 fill，语义来源色通过整枚图标滤镜统一覆盖，不改变图标和行布局。 */
.ta-file-tree-row.is-reference-merged :deep(.ta-file-tree-icon) {
  filter: brightness(0) saturate(100%) invert(35%) sepia(89%) saturate(1719%) hue-rotate(207deg) brightness(93%) contrast(94%);
}

.ta-file-tree-row.is-reference-collision :deep(.ta-file-tree-icon) {
  filter: brightness(0) saturate(100%) invert(24%) sepia(83%) saturate(3165%) hue-rotate(347deg) brightness(93%) contrast(87%);
}

.ta-file-tree-row-wrapper {
  position: relative;
}

.ta-file-tree-actions {
  position: absolute;
  top: 2px;
  right: 4px;
  display: flex;
  height: 18px;
  align-items: center;
  gap: 2px;
  pointer-events: none;
}

.ta-file-tree-actions > button {
  margin-left: 0;
  pointer-events: auto;
}

.ta-file-tree-add-btn {
  display: none;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  padding: 0;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--ta-tree-muted, #8b949e);
  cursor: pointer;
  transition: all 0.15s ease;
  margin-left: 4px;
}

.ta-file-tree-row-wrapper:hover > .ta-file-tree-actions .ta-file-tree-add-btn {
  display: inline-flex;
}

.ta-file-tree-add-btn:hover {
  background: var(--ta-hover, #f1f5f9);
  color: var(--ta-tree-text, #3b3b3b);
}

.ta-file-tree-delete-btn {
  display: none;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  padding: 0;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--ta-tree-muted, #8b949e);
  cursor: pointer;
  transition: all 0.15s ease;
  margin-left: 4px;
}

.ta-file-tree-row-wrapper:hover > .ta-file-tree-actions .ta-file-tree-delete-btn,
.ta-file-tree-delete-btn:focus-visible {
  display: inline-flex;
}

.ta-file-tree-delete-btn:hover {
  background: rgba(239, 68, 68, 0.1);
  color: var(--ta-danger, #dc2626);
}

.ta-file-tree-plane-btn {
  display: none;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  padding: 0;
  border: 0;
  border-radius: 3px;
  background: transparent;
  color: var(--ta-tree-muted, #8b949e);
  cursor: pointer;
  transition: background-color 0.12s ease, color 0.12s ease;
  margin-left: 4px;
}

.ta-file-tree-row-wrapper:hover > .ta-file-tree-actions .ta-file-tree-plane-btn {
  display: inline-flex;
}

.ta-file-tree-plane-btn:hover {
  background: var(--ta-hover, #f1f5f9);
  color: var(--ta-accent, #3366ff);
}

.ta-file-tree-rename-input {
  min-width: 0;
  height: 20px;
  border: 1px solid var(--ta-accent, #3366ff);
  border-radius: 2px;
  background: var(--ta-tree-bg, #fff);
  padding: 0 4px;
  color: var(--ta-tree-text, #3b3b3b);
  font: inherit;
  outline: none;
}

.ta-file-tree-rename-error {
  padding: 2px 8px 3px;
  color: var(--ta-danger, #b91c1c);
  font-size: 11px;
}
</style>
