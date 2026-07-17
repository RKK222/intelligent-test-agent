<script lang="ts">
import type { FileStatus, FileTreeEntry, RunDiffFile, FileSearchResult } from "@test-agent/shared-types";

export type FileExplorerProps = {
  workspaceName?: string;
  workspaceRootPath?: string;
  entriesByDirectory: Record<string, FileTreeEntry[]>;
  expandedDirectories: Set<string>;
  activePath?: string;
  changedFiles: RunDiffFile[];
  statuses?: Record<string, FileStatus>;
  loadingPath?: Set<string>;
  hideHeader?: boolean;
  hideTabbar?: boolean;
  /** 当前工作区是否允许文件新增、删除和重命名；只读时仍允许浏览、搜索和加入对话。 */
  canWrite?: boolean;
  /** 当前个人 worktree 是否存在可撤销的复制、移动或上传操作。 */
  canUndo?: boolean;
  activeTab?: ExplorerTab;
  // 搜索相关 props（由应用层传入）
  searchResults?: FileSearchResult[];
  searchLoading?: boolean;
  searchKeyword?: string;
};

export type ExplorerTab = "explorer" | "search" | "changes";
</script>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { FolderTree, GitBranch, Plus, RefreshCw, Search } from "lucide-vue-next";
import { Input, cn } from "@test-agent/ui-kit";
import { filterLoadedFiles } from "./filterLoadedFiles";
import { getVsCodeFileIconClass } from "./fileIcons";
import { highlightKeyword } from "./highlightKeyword";
import DirectoryRows from "./DirectoryRows.vue";
import type { WorkspaceClipboardEntry } from "./DirectoryRows.vue";
import FileIcon from "./FileIcon.vue";

const props = withDefaults(defineProps<FileExplorerProps>(), { workspaceName: "Workspace", canWrite: true });
const computedTab = computed(() => props.activeTab ?? tab.value);
const emit = defineEmits<{
  toggleDirectory: [path: string];
  openFile: [path: string];
  addFileContext: [path: string];
  openDiff: [path: string];
  refresh: [];
  search: [keyword: string];
  createEntry: [directory: string, name: string, type: "file" | "directory"];
  deleteEntry: [path: string, type: "file" | "directory"];
  renameEntry: [path: string, name: string];
  copyEntry: [sourcePath: string, targetDirectory: string];
  moveEntry: [sourcePath: string, targetDirectory: string];
  uploadFiles: [directory: string, files: File[]];
  undoEntry: [];
  cacheAndNavigate: [path: string, type: "file" | "directory"];
}>();

const tab = ref<ExplorerTab>("explorer");
const keyword = ref("");
const clipboardEntry = ref<WorkspaceClipboardEntry>();
const uploadInput = ref<HTMLInputElement | null>(null);
const uploadDirectory = ref("");
const rootDropActive = ref(false);
const dragResetToken = ref(0);
const directoryRowsRef = ref<InstanceType<typeof DirectoryRows> | null>(null);
// 本地过滤结果（备用，当 searchResults prop 未提供时使用），统一映射为 FileSearchResult 形态
const localSearchResults = computed<FileSearchResult[]>(() =>
  filterLoadedFiles(props.entriesByDirectory, keyword.value).map((entry) => ({
    path: entry.path,
    name: entry.name,
    directory: entry.path.includes("/") ? entry.path.slice(0, entry.path.lastIndexOf("/")) : "",
    size: entry.size ?? 0
  }))
);

// 显示用的搜索关键字：优先使用 prop，否则使用本地 keyword
const displayKeyword = computed(() => props.searchKeyword ?? keyword.value);

// 显示用的搜索结果：优先使用 prop，否则使用本地过滤结果
const displaySearchResults = computed(() => props.searchResults ?? localSearchResults.value);

// 处理搜索输入：同时更新本地 keyword 并 emit 事件
function handleSearchInput(value: string) {
  keyword.value = value;
  emit("search", value);
}

// 把 changedFiles 的路径归一化为 workspace 相对路径，用于文件树行匹配 +N -N。
// opencode 的 diff path 可能是绝对路径或带 a//b/ 前缀，需与文件树 entry.path（相对路径）对齐。
const changeStats = computed(() => {
  const root = props.workspaceRootPath ?? "";
  const map: Record<string, { additions: number; deletions: number }> = {};
  for (const f of props.changedFiles) {
    let p = f.path.replace(/^([ab])\//, "");
    if (root && (p === root || p.startsWith(`${root}/`))) {
      p = p.slice(root.length).replace(/^\/+/, "");
    }
    const prev = map[p];
    map[p] = prev
      ? { additions: prev.additions + f.additions, deletions: prev.deletions + f.deletions }
      : { additions: f.additions, deletions: f.deletions };
  }
  return map;
});

function fileNameOf(path: string) {
  return path.split("/").pop() || path;
}

function getStatusLabel(status?: string): string {
  if (!status) return "M";
  const s = status.toLowerCase();
  if (s === "untracked") return "U";
  return s.charAt(0).toUpperCase();
}

function fileIconClass(name: string, path: string) {
  return getVsCodeFileIconClass({ name, path, type: "file" });
}

function setClipboard(path: string, mode: "copy" | "move") {
  clipboardEntry.value = { path, mode };
}

function pasteEntry(directory: string) {
  const entry = clipboardEntry.value;
  if (!entry || !props.canWrite) return;
  if (entry.mode === "copy") {
    emit("copyEntry", entry.path, directory);
  } else {
    emit("moveEntry", entry.path, directory);
    clipboardEntry.value = undefined;
  }
}

function requestUpload(directory: string) {
  if (!props.canWrite) return;
  uploadDirectory.value = directory;
  uploadInput.value?.click();
}

function onUploadInput(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files ?? []);
  if (files.length > 0) emit("uploadFiles", uploadDirectory.value, files);
  input.value = "";
}

function onRootDragOver(event: DragEvent) {
  if (!props.canWrite) return;
  event.preventDefault();
  if (event.dataTransfer) event.dataTransfer.dropEffect = "move";
  rootDropActive.value = true;
}

function onRootDrop(event: DragEvent) {
  if (!props.canWrite || !event.dataTransfer) return;
  event.preventDefault();
  const files = Array.from(event.dataTransfer.files ?? []);
  rootDropActive.value = false;
  if (files.length > 0) {
    emit("uploadFiles", "", files);
    return;
  }
  const sourcePath = event.dataTransfer.getData("application/x-test-agent-workspace-file");
  if (sourcePath) emit("moveEntry", sourcePath, "");
}

function resetDragState() {
  rootDropActive.value = false;
  dragResetToken.value += 1;
}

function openRootActions() {
  if (!props.canWrite) return;
  directoryRowsRef.value?.openCreateDialog("");
}

onMounted(() => {
  // drop 可能在递归子目录或树外结束，统一清理根节点与子目录的蓝色拖放高亮。
  window.addEventListener("dragend", resetDragState, true);
  window.addEventListener("drop", resetDragState, true);
});

onBeforeUnmount(() => {
  window.removeEventListener("dragend", resetDragState, true);
  window.removeEventListener("drop", resetDragState, true);
});

defineExpose({ openRootActions });
</script>

<template>
  <div class="ta-file-browser flex h-full min-h-0 flex-col bg-[var(--ta-tree-bg)]">
    <div v-if="!hideTabbar" class="ta-icon-tabbar" role="tablist" aria-label="工作区面板">
      <button
        type="button"
        :class="['ta-icon-tab', tab === 'explorer' && 'is-active']"
        title="文件树"
        aria-label="文件树"
        @click="tab = 'explorer'"
      >
        <FolderTree class="h-4 w-4" :stroke-width="1.5" />
      </button>
      <button
        type="button"
        :class="['ta-icon-tab', tab === 'search' && 'is-active']"
        title="搜索"
        aria-label="搜索"
        @click="tab = 'search'"
      >
        <Search class="h-4 w-4" :stroke-width="1.5" />
      </button>
      <button
        type="button"
        :class="['ta-icon-tab', tab === 'changes' && 'is-active']"
        title="变更"
        aria-label="变更"
        @click="tab = 'changes'"
      >
        <GitBranch class="h-4 w-4" :stroke-width="1.5" />
        <span v-if="changedFiles.length" class="ml-1 text-[10px]">{{ changedFiles.length }}</span>
      </button>
    </div>
    <div
      v-if="computedTab === 'explorer'"
      :class="['ta-file-tree-scroll', rootDropActive && 'is-root-drop-target']"
      @dragover="onRootDragOver"
      @dragleave.self="rootDropActive = false"
      @drop="onRootDrop"
    >
      <div v-if="!hideHeader" class="ta-file-tree-header">
        <span class="min-w-0 truncate" :title="workspaceName">{{ workspaceName }}</span>
        <div class="flex shrink-0 items-center gap-1">
          <button
            v-if="canWrite"
            type="button"
            class="ta-fe-icon-btn"
            title="新建或上传到工作区根目录"
            aria-label="新建或上传到工作区根目录"
            @click="openRootActions"
          >
            <Plus class="h-3.5 w-3.5" :stroke-width="1.5" />
          </button>
          <button
            type="button"
            class="ta-fe-icon-btn"
            title="刷新文件树"
            aria-label="刷新文件树"
            @click="emit('refresh')"
          >
            <RefreshCw class="h-3.5 w-3.5" :stroke-width="1.5" />
          </button>
        </div>
      </div>
      <DirectoryRows
        ref="directoryRowsRef"
        directory=""
        :entries-by-directory="entriesByDirectory"
        :expanded-directories="expandedDirectories"
        :active-path="activePath"
        :loading-path="loadingPath"
        :change-stats="changeStats"
        :can-write="canWrite"
        :can-undo="canUndo"
        :drag-reset-token="dragResetToken"
        :clipboard-entry="clipboardEntry"
        :depth="0"
        @toggle-directory="emit('toggleDirectory', $event)"
        @open-file="emit('openFile', $event)"
        @add-file-context="emit('addFileContext', $event)"
        @create-entry="(directory, name, type) => emit('createEntry', directory, name, type)"
        @delete-entry="(path, type) => emit('deleteEntry', path, type)"
        @rename-entry="(path, name) => emit('renameEntry', path, name)"
        @set-clipboard="setClipboard"
        @paste-entry="pasteEntry"
        @undo-entry="emit('undoEntry')"
        @move-entry="(sourcePath, targetDirectory) => emit('moveEntry', sourcePath, targetDirectory)"
        @upload-files="(directory, files) => emit('uploadFiles', directory, files)"
        @request-upload="requestUpload"
        @cache-and-navigate="(path, type) => emit('cacheAndNavigate', path, type)"
      />
      <input
        ref="uploadInput"
        type="file"
        class="sr-only"
        multiple
        aria-label="选择要上传到工作区的文件"
        @change="onUploadInput"
      />
    </div>
    <div v-else-if="computedTab === 'search'" class="ta-file-tree-scroll">
      <div class="relative px-2 pb-1 pt-1">
        <Search class="pointer-events-none absolute left-4 top-[7px] h-4 w-4 text-[var(--ta-tree-muted)]" :stroke-width="1.5" />
        <Input
          :model-value="searchKeyword ?? keyword"
          class="ta-file-search-input"
          placeholder="搜索工作区文件"
          @update:model-value="handleSearchInput"
        />
      </div>
      <div v-if="searchLoading" class="ta-file-tree-empty">搜索中...</div>
      <div v-else-if="displayKeyword && (!displaySearchResults || displaySearchResults.length === 0)" class="ta-file-tree-empty">无匹配文件</div>
      <div v-else>
        <button
          v-for="entry in displaySearchResults"
          :key="entry.path"
          type="button"
          :class="cn('ta-file-tree-row')"
          :style="{ paddingLeft: '6px' }"
          :title="entry.path"
          @click="emit('openFile', entry.path)"
          @contextmenu.prevent="emit('addFileContext', entry.path)"
        >
          <span class="ta-file-tree-file-spacer" />
          <FileIcon :entry="{ name: entry.name, path: entry.path, type: 'file' }" />
          <span class="min-w-0 truncate">
            <template v-for="segment in highlightKeyword(entry.name, displayKeyword)" :key="segment.text">
              <mark v-if="segment.match" class="ta-file-tree-mark">{{ segment.text }}</mark>
              <span v-else>{{ segment.text }}</span>
            </template>
          </span>
        </button>
      </div>
    </div>
    <div v-else-if="computedTab === 'changes'" class="ta-file-tree-scroll">
      <div>
        <button
          v-for="file in changedFiles"
          :key="file.path"
          type="button"
          class="ta-file-tree-row"
          :style="{ paddingLeft: '6px' }"
          @click="emit('openDiff', file.path)"
        >
          <span :class="cn('ta-file-tree-status', `is-${file.status}`)">{{ getStatusLabel(file.status) }}</span>
          <FileIcon :entry="{ name: fileNameOf(file.path), path: file.path, type: 'file' }" />
          <span class="min-w-0 flex-1 truncate" :title="file.path">{{ fileNameOf(file.path) }}</span>
          <span class="ta-file-tree-badge is-added">+{{ file.additions }}</span>
          <span class="ta-file-tree-badge is-deleted">-{{ file.deletions }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ta-file-tree-scroll.is-root-drop-target {
  outline: 1px solid var(--ta-accent, #2563eb);
  outline-offset: -1px;
  background: rgb(37 99 235 / 6%);
}
</style>

<style scoped>
.ta-fe-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--ta-tree-muted, #8b949e);
  cursor: pointer;
  transition: background-color 0.12s ease, color 0.12s ease;
}

.ta-fe-icon-btn:hover {
  background: transparent;
  color: var(--ta-tree-text, #3b3b3b);
}

.ta-fe-icon-btn:focus-visible {
  outline: 2px solid var(--ta-accent, #3366ff);
  outline-offset: 1px;
}

.ta-file-search-input {
  height: 24px !important;
  padding-left: 26px !important;
  border-radius: 2px !important;
  font-size: var(--ta-tree-font-size) !important;
  font-family: var(--ta-tree-font-family) !important;
}
</style>
