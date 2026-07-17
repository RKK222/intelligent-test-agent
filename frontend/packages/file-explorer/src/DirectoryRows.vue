<script lang="ts">
import type { FileTreeEntry } from "@test-agent/shared-types";

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
  /** 文件路径 → 行变更统计，用于在文件名后展示 +N -N。 */
  changeStats?: Record<string, { additions: number; deletions: number }>;
  /** 文件树内部剪贴板，仅保存当前工作区的普通文件引用。 */
  clipboardEntry?: WorkspaceClipboardEntry;
};

export type WorkspaceClipboardEntry = {
  path: string;
  mode: "copy" | "move";
};
</script>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { AlertTriangle, FilePlus2, Plane, Plus, Trash2, Upload, X } from "lucide-vue-next";
import { cn } from "@test-agent/ui-kit";
import FileIcon from "./FileIcon.vue";

const props = withDefaults(defineProps<DirectoryRowsProps>(), { depth: 0, canWrite: true });
const emit = defineEmits<{
  toggleDirectory: [path: string];
  openFile: [path: string];
  addFileContext: [path: string];
  createEntry: [directory: string, name: string, type: "file" | "directory"];
  deleteEntry: [path: string, type: "file" | "directory"];
  renameEntry: [path: string, name: string];
  setClipboard: [path: string, mode: "copy" | "move"];
  pasteEntry: [directory: string];
  undoEntry: [];
  moveEntry: [sourcePath: string, targetDirectory: string];
  uploadFiles: [directory: string, files: File[]];
  requestUpload: [directory: string];
  cacheAndNavigate: [path: string, type: "file" | "directory"];
}>();

const entries = computed(() => {
  const list = props.entriesByDirectory[props.directory] ?? [];
  return [...list].sort((a, b) => {
    if (a.type === b.type) return 0;
    return a.type === "directory" ? -1 : 1;
  });
});

const entryContextMenu = ref<{ entry: FileTreeEntry; x: number; y: number } | null>(null);
const dragOverDirectory = ref<string | null>(null);
const showCreateDialog = ref(false);
const createDialogParentDirectory = ref("");
const createDialogType = ref<"file" | "directory" | "upload">("file");
const createDialogName = ref("");
const createDialogError = ref("");
const showDeleteDialog = ref(false);
const deleteDialogEntry = ref<{ path: string; name: string; type: "file" | "directory" } | null>(null);
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
  entryContextMenu.value = { entry, x: event.clientX, y: event.clientY };
}

function closeFileContextMenu() {
  entryContextMenu.value = null;
}

function emitAddFileContext() {
  const entry = entryContextMenu.value?.entry;
  if (!entry || entry.type !== "file") {
    return;
  }
  emit("addFileContext", entry.path);
  closeFileContextMenu();
}

function parentDirectory(path: string): string {
  const index = Math.max(path.lastIndexOf("/"), path.lastIndexOf("\\"));
  return index >= 0 ? path.slice(0, index) : "";
}

function targetDirectory(entry: FileTreeEntry): string {
  return entry.type === "directory" ? entry.path : parentDirectory(entry.path);
}

function emitSetClipboard(mode: "copy" | "move") {
  const entry = entryContextMenu.value?.entry;
  if (!props.canWrite || !entry || entry.type !== "file") return;
  emit("setClipboard", entry.path, mode);
  closeFileContextMenu();
}

function emitPasteEntry() {
  const entry = entryContextMenu.value?.entry;
  if (!props.canWrite || !props.clipboardEntry || !entry) return;
  emit("pasteEntry", targetDirectory(entry));
  closeFileContextMenu();
}

/** 文件树行聚焦后支持 Delete 和 Ctrl/Cmd+C、X、V、Z，所有写操作复用现有确认或事件链路。 */
function onRowKeydown(event: KeyboardEvent, entry: FileTreeEntry) {
  if (!props.canWrite || event.target instanceof HTMLInputElement) return;
  const key = event.key.toLowerCase();
  if (key === "delete" || key === "del") {
    event.preventDefault();
    openDeleteDialog(entry);
    return;
  }
  if (!event.ctrlKey && !event.metaKey) return;
  if ((key === "c" || key === "x") && entry.type === "file") {
    event.preventDefault();
    emit("setClipboard", entry.path, key === "c" ? "copy" : "move");
    return;
  }
  if (key === "v" && props.clipboardEntry) {
    event.preventDefault();
    emit("pasteEntry", targetDirectory(entry));
    return;
  }
  if (key === "z" && props.canUndo) {
    event.preventDefault();
    emit("undoEntry");
  }
}

watch(() => props.dragResetToken, () => {
  dragOverDirectory.value = null;
});

/** 普通文件使用 HTML5 drag data 传递相对路径，目录仅作为工作区内移动目标。 */
function onDragStart(event: DragEvent, entry: FileTreeEntry) {
  if (!props.canWrite || entry.type !== "file" || !event.dataTransfer) {
    event.preventDefault();
    return;
  }
  event.dataTransfer.effectAllowed = "move";
  event.dataTransfer.setData("application/x-test-agent-workspace-file", entry.path);
  event.dataTransfer.setData("text/plain", entry.path);
}

function onDirectoryDragOver(event: DragEvent, entry: FileTreeEntry) {
  if (!props.canWrite || entry.type !== "directory") return;
  event.preventDefault();
  event.stopPropagation();
  if (event.dataTransfer) event.dataTransfer.dropEffect = "move";
  dragOverDirectory.value = entry.path;
}

function onDirectoryDrop(event: DragEvent, entry: FileTreeEntry) {
  if (!props.canWrite || entry.type !== "directory" || !event.dataTransfer) return;
  event.preventDefault();
  event.stopPropagation();
  const files = Array.from(event.dataTransfer.files ?? []);
  if (files.length > 0) {
    dragOverDirectory.value = null;
    emit("uploadFiles", entry.path, files);
    return;
  }
  const sourcePath = event.dataTransfer.getData("application/x-test-agent-workspace-file");
  dragOverDirectory.value = null;
  if (sourcePath) emit("moveEntry", sourcePath, entry.path);
}

function isKnownEmptyDirectory(path: string): boolean {
  const children = props.entriesByDirectory[path];
  return Array.isArray(children) && children.length === 0;
}

function onRowClick(entry: FileTreeEntry) {
  if (entry.type === "directory") {
    if (isKnownEmptyDirectory(entry.path)) {
      return;
    }
    emit("toggleDirectory", entry.path);
  } else {
    emit("openFile", entry.path);
  }
}

function openCreateDialog(directory: string) {
  if (!props.canWrite) return;
  createDialogParentDirectory.value = directory;
  createDialogType.value = "file";
  createDialogName.value = "";
  createDialogError.value = "";
  showCreateDialog.value = true;
}

/** 根目录标题与目录行的“+”共用同一个明确目标路径的操作面板。 */
defineExpose({ openCreateDialog });

function closeCreateDialog() {
  showCreateDialog.value = false;
  createDialogError.value = "";
}

function submitCreateDialog() {
  if (!props.canWrite) return;
  if (createDialogType.value === "upload") {
    emit("requestUpload", createDialogParentDirectory.value);
    closeCreateDialog();
    return;
  }
  const name = createDialogName.value.trim();
  if (!name) {
    createDialogError.value = "请输入名称";
    return;
  }
  if (name.includes("/") || name.includes("\\")) {
    createDialogError.value = "名称不能包含路径分隔符";
    return;
  }
  emit("createEntry", createDialogParentDirectory.value, name, createDialogType.value);
  closeCreateDialog();
}

function openDeleteDialog(entry: FileTreeEntry) {
  if (!props.canWrite) return;
  deleteDialogEntry.value = { path: entry.path, name: entry.name, type: entry.type };
  showDeleteDialog.value = true;
}

function closeDeleteDialog() {
  showDeleteDialog.value = false;
  deleteDialogEntry.value = null;
}

function submitDeleteDialog() {
  if (!props.canWrite) return;
  if (!deleteDialogEntry.value) {
    return;
  }
  emit("deleteEntry", deleteDialogEntry.value.path, deleteDialogEntry.value.type);
  closeDeleteDialog();
}

function startRename(entry: FileTreeEntry) {
  if (!props.canWrite) return;
  renamingPath.value = entry.path;
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
    <div v-for="entry in entries" :key="entry.path" class="ta-file-tree-row-wrapper">
      <button
        type="button"
        :class="cn(
          'ta-file-tree-row',
          activePath === entry.path && 'is-active',
          dragOverDirectory === entry.path && 'is-drop-target'
        )"
        :draggable="canWrite && entry.type === 'file'"
        :style="{
          paddingLeft: depth * 16 + 6 + 'px',
          paddingRight: canWrite
            ? (entry.type === 'directory' ? (entry.name.includes('测试执行') ? '68px' : '48px') : '26px')
            : (entry.type === 'directory' && entry.name.includes('测试执行') ? '26px' : '6px')
        }"
        @click="onRowClick(entry)"
        @contextmenu="openFileContextMenu($event, entry)"
        @dblclick.stop="canWrite && startRename(entry)"
        @keydown="onRowKeydown($event, entry)"
        @dragstart="onDragStart($event, entry)"
        @dragover="onDirectoryDragOver($event, entry)"
        @dragleave="dragOverDirectory === entry.path && (dragOverDirectory = null)"
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
            v-if="!isKnownEmptyDirectory(entry.path)"
            :class="cn('codicon codicon-chevron-right ta-file-tree-twistie', expandedDirectories.has(entry.path) && 'is-open')"
            aria-hidden="true"
          />
          <span v-else class="ta-file-tree-spacer" />
        </template>
        <template v-else>
          <span class="ta-file-tree-file-spacer" />
          <FileIcon :entry="entry" />
        </template>
        <span v-if="renamingPath !== entry.path" class="min-w-0 flex-1 truncate">{{ entry.name }}</span>
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
        <template v-if="entry.type === 'file' && changeStats?.[entry.path]">
          <span class="ta-file-tree-badge is-added">+{{ changeStats[entry.path].additions }}</span>
          <span class="ta-file-tree-badge is-deleted">-{{ changeStats[entry.path].deletions }}</span>
        </template>
        <i v-if="loadingPath?.has(entry.path)" class="codicon codicon-loading codicon-modifier-spin ta-file-tree-loading" aria-hidden="true" />
      </button>
      <div class="ta-file-tree-actions">
        <button
          v-if="canWrite && entry.type === 'directory'"
          type="button"
          class="ta-file-tree-add-btn"
          title="新建或上传到此目录"
          aria-label="新建或上传到此目录"
          @click.stop="openCreateDialog(entry.path)"
        >
          <Plus class="h-3.5 w-3.5" :stroke-width="1.5" />
        </button>
        <button
          v-if="canWrite"
          type="button"
          class="ta-file-tree-delete-btn"
          title="删除"
          :aria-label="`删除 ${entry.name}`"
          @click.stop="openDeleteDialog(entry)"
        >
          <Trash2 class="h-3.5 w-3.5" :stroke-width="1.5" />
        </button>
        <button
          v-if="entry.type === 'directory' && entry.name.includes('测试执行')"
          type="button"
          class="ta-file-tree-plane-btn"
          title="缓存并跳转"
          aria-label="缓存并跳转"
          @click.stop="emit('cacheAndNavigate', entry.path, entry.type)"
        >
          <Plane class="h-3.5 w-3.5" :stroke-width="1.5" />
        </button>
      </div>
      <div v-if="renamingPath === entry.path && renameError" class="ta-file-tree-rename-error">
        {{ renameError }}
      </div>
      <DirectoryRows
        v-if="entry.type === 'directory' && expandedDirectories.has(entry.path)"
        :directory="entry.path"
        :entries-by-directory="entriesByDirectory"
        :expanded-directories="expandedDirectories"
        :active-path="activePath"
        :loading-path="loadingPath"
        :change-stats="changeStats"
        :can-write="canWrite"
        :can-undo="canUndo"
        :drag-reset-token="dragResetToken"
        :clipboard-entry="clipboardEntry"
        :depth="depth + 1"
        @toggle-directory="emit('toggleDirectory', $event)"
        @open-file="emit('openFile', $event)"
        @add-file-context="emit('addFileContext', $event)"
        @create-entry="(directory, name, type) => emit('createEntry', directory, name, type)"
        @delete-entry="(path, type) => emit('deleteEntry', path, type)"
        @rename-entry="(path, name) => emit('renameEntry', path, name)"
        @set-clipboard="(path, mode) => emit('setClipboard', path, mode)"
        @paste-entry="emit('pasteEntry', $event)"
        @undo-entry="emit('undoEntry')"
        @move-entry="(sourcePath, targetDirectory) => emit('moveEntry', sourcePath, targetDirectory)"
        @upload-files="(directory, files) => emit('uploadFiles', directory, files)"
        @request-upload="emit('requestUpload', $event)"
        @cache-and-navigate="(path, type) => emit('cacheAndNavigate', path, type)"
      />
    </div>
    <Teleport to="body">
      <div
        v-if="entryContextMenu"
        class="ta-file-context-menu-backdrop"
        @click="closeFileContextMenu"
        @contextmenu.prevent="closeFileContextMenu"
      />
      <div
        v-if="entryContextMenu"
        class="ta-file-context-menu"
        role="menu"
        :style="{ left: `${entryContextMenu.x}px`, top: `${entryContextMenu.y}px` }"
      >
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
          v-if="canWrite && entryContextMenu.entry.type === 'file'"
          type="button"
          role="menuitem"
          class="ta-file-context-menu-item"
          @click="emitSetClipboard('copy')"
        >
          复制
          <span>Ctrl/Cmd+C</span>
        </button>
        <button
          v-if="canWrite && entryContextMenu.entry.type === 'file'"
          type="button"
          role="menuitem"
          class="ta-file-context-menu-item"
          @click="emitSetClipboard('move')"
        >
          剪切
          <span>Ctrl/Cmd+X</span>
        </button>
        <button
          v-if="canWrite && clipboardEntry"
          type="button"
          role="menuitem"
          class="ta-file-context-menu-item"
          @click="emitPasteEntry"
        >
          粘贴到此处
          <span>Ctrl/Cmd+V</span>
        </button>
        <button
          v-if="canWrite && canUndo"
          type="button"
          role="menuitem"
          class="ta-file-context-menu-item"
          @click="emit('undoEntry'); closeFileContextMenu()"
        >
          撤销上一步
          <span>Ctrl/Cmd+Z</span>
        </button>
      </div>
    </Teleport>
    <Teleport to="body">
      <div
        v-if="showCreateDialog"
        class="ta-file-dialog-overlay"
        @keydown.esc="closeCreateDialog"
        @click.self="closeCreateDialog"
      >
        <section
          role="dialog"
          aria-modal="true"
          aria-label="新建或上传文件"
          class="ta-file-dialog"
        >
          <header class="ta-file-dialog-header">
            <div class="ta-file-dialog-heading">
              <span class="ta-file-dialog-icon"><FilePlus2 :size="16" :stroke-width="1.7" /></span>
              <div>
                <h2>新建或上传</h2>
                <p>选择要在当前目录执行的操作</p>
              </div>
            </div>
            <button type="button" class="ta-file-dialog-close" aria-label="关闭" @click="closeCreateDialog">
              <X :size="15" :stroke-width="1.7" />
            </button>
          </header>
          <div class="ta-file-dialog-body">
            <div class="ta-file-dialog-path">
              <span>目标目录</span>
              <code>{{ createDialogParentDirectory || '工作区根目录' }}</code>
            </div>
            <div class="ta-file-dialog-field">
              <label>操作类型</label>
              <div class="ta-file-dialog-segments">
                <button
                  type="button"
                  :class="{ 'is-active': createDialogType === 'file' }"
                  @click="createDialogType = 'file'"
                >
                  文件
                </button>
                <button
                  type="button"
                  :class="{ 'is-active': createDialogType === 'directory' }"
                  @click="createDialogType = 'directory'"
                >
                  文件夹
                </button>
                <button
                  type="button"
                  :class="{ 'is-active': createDialogType === 'upload' }"
                  @click="createDialogType = 'upload'"
                >
                  上传
                </button>
              </div>
            </div>
            <div v-if="createDialogType !== 'upload'" class="ta-file-dialog-field">
              <label>
                {{ createDialogType === 'file' ? '文件名' : '文件夹名' }}
              </label>
              <input
                v-model="createDialogName"
                type="text"
                :placeholder="createDialogType === 'file' ? '例如：README.md' : '例如：docs'"
                class="ta-file-dialog-input"
                @keydown.enter="submitCreateDialog"
                autofocus
              />
              <span v-if="createDialogError" class="ta-file-dialog-error">
                {{ createDialogError }}
              </span>
            </div>
            <div v-else class="ta-file-dialog-upload-note">
              <Upload :size="17" :stroke-width="1.6" />
              <div>
                <strong>从本机选择文件</strong>
                <span>支持一次选择多个文件，上传时不会覆盖同名内容。</span>
              </div>
            </div>
          </div>
          <footer class="ta-file-dialog-footer">
            <button type="button" class="ta-file-dialog-button" @click="closeCreateDialog">
              取消
            </button>
            <button
              type="button"
              class="ta-file-dialog-button is-primary"
              :disabled="createDialogType !== 'upload' && !createDialogName.trim()"
              @click="submitCreateDialog"
            >
              {{ createDialogType === 'upload' ? '选择文件' : '创建' }}
            </button>
          </footer>
        </section>
      </div>
    </Teleport>
    <Teleport to="body">
      <div
        v-if="showDeleteDialog"
        class="ta-file-dialog-overlay"
        @keydown.esc="closeDeleteDialog"
        @click.self="closeDeleteDialog"
      >
        <section
          role="dialog"
          aria-modal="true"
          :aria-label="deleteDialogEntry?.type === 'directory' ? '删除文件夹' : '删除文件'"
          class="ta-file-dialog ta-file-dialog--danger"
        >
          <header class="ta-file-dialog-header">
            <div class="ta-file-dialog-heading">
              <span class="ta-file-dialog-icon"><AlertTriangle :size="16" :stroke-width="1.8" /></span>
              <div>
                <h2>确认删除</h2>
                <p>此操作会立即写入当前个人 worktree</p>
              </div>
            </div>
            <button type="button" class="ta-file-dialog-close" aria-label="关闭" @click="closeDeleteDialog">
              <X :size="15" :stroke-width="1.7" />
            </button>
          </header>
          <div class="ta-file-dialog-body">
            <div class="ta-file-dialog-danger-card">
              <span>{{ deleteDialogEntry?.type === 'directory' ? '文件夹' : '文件' }}</span>
              <strong>{{ deleteDialogEntry?.path }}</strong>
            </div>
            <p class="ta-file-dialog-warning">
              {{ deleteDialogEntry?.type === 'directory' ? '文件夹及其中的全部内容都会被删除。' : '文件删除后无法恢复。' }}
            </p>
          </div>
          <footer class="ta-file-dialog-footer">
            <button type="button" class="ta-file-dialog-button" @click="closeDeleteDialog">
              取消
            </button>
            <button type="button" class="ta-file-dialog-button is-danger" @click="submitDeleteDialog">
              确认删除
            </button>
          </footer>
        </section>
      </div>
    </Teleport>
  </div>
</template>

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

.ta-file-context-menu-backdrop {
  position: fixed;
  inset: 0;
  z-index: 2600;
  background: transparent;
}

.ta-file-context-menu {
  position: fixed;
  z-index: 2601;
  min-width: 140px;
  padding: 4px;
  border: 1px solid #d4d4d8;
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 12px 28px rgb(15 23 42 / 18%);
}

.ta-file-context-menu-item {
  display: flex;
  width: 100%;
  align-items: center;
  border: 0;
  border-radius: 4px;
  background: transparent;
  padding: 6px 8px;
  color: #1f2937;
  font-size: 12px;
  text-align: left;
  cursor: pointer;
  justify-content: space-between;
  gap: 16px;
}

.ta-file-context-menu-item span {
  color: #8b949e;
  font-size: 11px;
}

:deep(.ta-file-tree-row.is-drop-target) {
  outline: 1px solid var(--ta-accent, #2563eb);
  outline-offset: -1px;
  background: rgb(37 99 235 / 10%);
}

.ta-file-context-menu-item:hover {
  background: #f1f5f9;
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
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  padding: 0;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--ta-tree-muted, #8b949e);
  opacity: 0.58;
  cursor: pointer;
  transition: all 0.15s ease;
  margin-left: 4px;
}

.ta-file-tree-row-wrapper:hover > .ta-file-tree-actions .ta-file-tree-delete-btn,
.ta-file-tree-delete-btn:focus-visible {
  opacity: 1;
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
