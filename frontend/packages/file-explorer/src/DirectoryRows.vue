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
import { computed, nextTick, ref } from "vue";
import { Plane, Plus, Trash2 } from "lucide-vue-next";
import { cn } from "@test-agent/ui-kit";
import FileIcon from "./FileIcon.vue";

const props = withDefaults(defineProps<DirectoryRowsProps>(), { depth: 0 });
const emit = defineEmits<{
  toggleDirectory: [path: string];
  openFile: [path: string];
  addFileContext: [path: string];
  createEntry: [directory: string, name: string, type: "file" | "directory"];
  deleteEntry: [path: string, type: "file" | "directory"];
  renameEntry: [path: string, name: string];
  cacheAndNavigate: [path: string, type: "file" | "directory"];
}>();

const entries = computed(() => {
  const list = props.entriesByDirectory[props.directory] ?? [];
  return [...list].sort((a, b) => {
    if (a.type === b.type) return 0;
    return a.type === "directory" ? -1 : 1;
  });
});

const fileContextMenu = ref<{ path: string; x: number; y: number } | null>(null);
const showCreateDialog = ref(false);
const createDialogParentDirectory = ref("");
const createDialogType = ref<"file" | "directory">("file");
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
  if (entry.type !== "file") {
    return;
  }
  event.preventDefault();
  fileContextMenu.value = { path: entry.path, x: event.clientX, y: event.clientY };
}

function closeFileContextMenu() {
  fileContextMenu.value = null;
}

function emitAddFileContext() {
  if (!fileContextMenu.value) {
    return;
  }
  emit("addFileContext", fileContextMenu.value.path);
  closeFileContextMenu();
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
  createDialogParentDirectory.value = directory;
  createDialogType.value = "file";
  createDialogName.value = "";
  createDialogError.value = "";
  showCreateDialog.value = true;
}

function closeCreateDialog() {
  showCreateDialog.value = false;
  createDialogError.value = "";
}

function submitCreateDialog() {
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
  deleteDialogEntry.value = { path: entry.path, name: entry.name, type: entry.type };
  showDeleteDialog.value = true;
}

function closeDeleteDialog() {
  showDeleteDialog.value = false;
  deleteDialogEntry.value = null;
}

function submitDeleteDialog() {
  if (!deleteDialogEntry.value) {
    return;
  }
  emit("deleteEntry", deleteDialogEntry.value.path, deleteDialogEntry.value.type);
  closeDeleteDialog();
}

function startRename(entry: FileTreeEntry) {
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
          activePath === entry.path && 'is-active'
        )"
        :style="{ paddingLeft: depth * 16 + 6 + 'px' }"
        @click="onRowClick(entry)"
        @contextmenu="openFileContextMenu($event, entry)"
        @dblclick.stop="startRename(entry)"
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
        <button
          v-if="entry.type === 'directory'"
          type="button"
          class="ta-file-tree-add-btn"
          title="新建文件或文件夹"
          aria-label="新建文件或文件夹"
          @click.stop="openCreateDialog(entry.path)"
        >
          <Plus class="h-3.5 w-3.5" :stroke-width="1.5" />
        </button>
        <button
          v-if="entry.type === 'file'"
          type="button"
          class="ta-file-tree-delete-btn"
          title="删除"
          aria-label="删除"
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
      </button>
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
        :depth="depth + 1"
        @toggle-directory="emit('toggleDirectory', $event)"
        @open-file="emit('openFile', $event)"
        @add-file-context="emit('addFileContext', $event)"
        @create-entry="(directory, name, type) => emit('createEntry', directory, name, type)"
        @delete-entry="(path, type) => emit('deleteEntry', path, type)"
        @rename-entry="(path, name) => emit('renameEntry', path, name)"
        @cache-and-navigate="(path, type) => emit('cacheAndNavigate', path, type)"
      />
    </div>
    <Teleport to="body">
      <div
        v-if="fileContextMenu"
        class="ta-file-context-menu-backdrop"
        @click="closeFileContextMenu"
        @contextmenu.prevent="closeFileContextMenu"
      />
      <div
        v-if="fileContextMenu"
        class="ta-file-context-menu"
        role="menu"
        :style="{ left: `${fileContextMenu.x}px`, top: `${fileContextMenu.y}px` }"
      >
        <button type="button" role="menuitem" class="ta-file-context-menu-item" @click="emitAddFileContext">
          添加文件到对话
        </button>
      </div>
    </Teleport>
    <Teleport to="body">
      <div
        v-if="showCreateDialog"
        class="fixed inset-0 z-[1000] flex items-center justify-center bg-black/35 px-4 py-6"
        @keydown.esc="closeCreateDialog"
        @click.self="closeCreateDialog"
      >
        <section
          role="dialog"
          aria-modal="true"
          aria-label="新建文件或文件夹"
          class="flex w-[min(360px,calc(100vw-24px))] flex-col rounded-lg border border-[var(--ta-border)] bg-[var(--ta-panel)] shadow-xl p-4 gap-4"
        >
          <header class="flex items-center justify-between border-b border-[var(--ta-border)] pb-2">
            <h2 class="text-[14px] font-semibold text-[var(--ta-text)]">新建文件或文件夹</h2>
          </header>
          <div class="flex flex-col gap-3">
            <div class="flex flex-col gap-1.5">
              <label class="text-[11px] text-[var(--ta-muted)] font-medium">类型</label>
              <div class="flex gap-2">
                <button
                  type="button"
                  :class="cn(
                    'flex-1 rounded border px-3 py-1.5 text-[12px] transition',
                    createDialogType === 'file'
                      ? 'border-[var(--ta-ink)] bg-[var(--ta-ink)] text-white'
                      : 'border-[var(--ta-border)] bg-transparent text-[var(--ta-text)] hover:border-[var(--ta-border-strong)]'
                  )"
                  @click="createDialogType = 'file'"
                >
                  新建文件
                </button>
                <button
                  type="button"
                  :class="cn(
                    'flex-1 rounded border px-3 py-1.5 text-[12px] transition',
                    createDialogType === 'directory'
                      ? 'border-[var(--ta-ink)] bg-[var(--ta-ink)] text-white'
                      : 'border-[var(--ta-border)] bg-transparent text-[var(--ta-text)] hover:border-[var(--ta-border-strong)]'
                  )"
                  @click="createDialogType = 'directory'"
                >
                  新建文件夹
                </button>
              </div>
            </div>
            <div class="flex flex-col gap-1.5">
              <label class="text-[11px] text-[var(--ta-muted)] font-medium">
                {{ createDialogType === 'file' ? '文件名' : '文件夹名' }}
                <span class="text-[var(--ta-danger,#b91c1c)]">*</span>
              </label>
              <input
                v-model="createDialogName"
                type="text"
                :placeholder="createDialogType === 'file' ? '请输入文件名' : '请输入文件夹名'"
                class="h-8 w-full rounded border border-[var(--ta-border)] bg-[var(--ta-surface)] px-2 text-[12px] outline-none transition placeholder:text-[var(--ta-muted)] focus:border-[var(--ta-border-strong)]"
                @keydown.enter="submitCreateDialog"
                autofocus
              />
              <span v-if="createDialogError" class="text-[11px] text-[var(--ta-danger,#b91c1c)]">
                {{ createDialogError }}
              </span>
            </div>
          </div>
          <footer class="flex justify-end gap-2 pt-2 border-t border-[var(--ta-border)]">
            <button
              type="button"
              class="inline-flex h-7 shrink-0 items-center justify-center gap-2 rounded border border-[var(--ta-border)] bg-transparent px-3 text-[12px] font-medium text-[var(--ta-muted)] transition hover:bg-[var(--ta-hover)] hover:text-[var(--ta-text)]"
              @click="closeCreateDialog"
            >
              取消
            </button>
            <button
              type="button"
              class="inline-flex h-7 shrink-0 items-center justify-center gap-2 rounded border border-[var(--ta-ink)] bg-[var(--ta-ink)] px-3 text-[12px] font-medium text-white transition hover:bg-[#111111]"
              :disabled="!createDialogName.trim()"
              @click="submitCreateDialog"
            >
              确定
            </button>
          </footer>
        </section>
      </div>
    </Teleport>
    <Teleport to="body">
      <div
        v-if="showDeleteDialog"
        class="fixed inset-0 z-[1000] flex items-center justify-center bg-black/35 px-4 py-6"
        @keydown.esc="closeDeleteDialog"
        @click.self="closeDeleteDialog"
      >
        <section
          role="dialog"
          aria-modal="true"
          aria-label="删除文件"
          class="flex w-[min(360px,calc(100vw-24px))] flex-col rounded-lg border border-[var(--ta-border)] bg-[var(--ta-panel)] shadow-xl p-4 gap-4"
        >
          <header class="flex items-center justify-between border-b border-[var(--ta-border)] pb-2">
            <h2 class="text-[14px] font-semibold text-[var(--ta-text)]">确认删除</h2>
          </header>
          <div class="flex flex-col gap-3">
            <p class="text-[12px] text-[var(--ta-text)]">
              确定要删除{{ deleteDialogEntry?.type === 'directory' ? '文件夹' : '文件' }}
              <strong class="text-[var(--ta-danger,#b91c1c)]">{{ deleteDialogEntry?.name }}</strong>
              吗？删除后无法恢复。
            </p>
          </div>
          <footer class="flex justify-end gap-2 pt-2 border-t border-[var(--ta-border)]">
            <button
              type="button"
              class="inline-flex h-7 shrink-0 items-center justify-center gap-2 rounded border border-[var(--ta-border)] bg-transparent px-3 text-[12px] font-medium text-[var(--ta-muted)] transition hover:bg-[var(--ta-hover)] hover:text-[var(--ta-text)]"
              @click="closeDeleteDialog"
            >
              取消
            </button>
            <button
              type="button"
              class="inline-flex h-7 shrink-0 items-center justify-center gap-2 rounded border border-[var(--ta-danger,#b91c1c)] bg-[var(--ta-danger,#b91c1c)] px-3 text-[12px] font-medium text-white transition hover:bg-[#991b1b]"
              @click="submitDeleteDialog"
            >
              删除
            </button>
          </footer>
        </section>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
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
}

.ta-file-context-menu-item:hover {
  background: #f1f5f9;
}

.ta-file-tree-row-wrapper {
  position: relative;
}

.ta-file-tree-add-btn {
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

.ta-file-tree-row:hover .ta-file-tree-add-btn {
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
  border-radius: 3px;
  background: transparent;
  color: var(--ta-tree-muted, #8b949e);
  cursor: pointer;
  transition: background-color 0.12s ease, color 0.12s ease;
  margin-left: 4px;
}

.ta-file-tree-row:hover .ta-file-tree-delete-btn {
  display: inline-flex;
}

.ta-file-tree-delete-btn:hover {
  background: var(--ta-hover, #f1f5f9);
  color: var(--ta-danger, #b91c1c);
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

.ta-file-tree-row:hover .ta-file-tree-plane-btn {
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
