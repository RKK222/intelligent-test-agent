<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { FileText, Folder, RefreshCw, Search } from "lucide-vue-next";
import { usePlatformStore } from "@/stores/platform";
import { useWorkspaceStore } from "@/stores/workspace";

type RuntimeFileEntry = {
  path: string;
  name: string;
  directory: boolean;
  size?: number;
};

const platform = usePlatformStore();
const workspace = useWorkspaceStore();
const currentPath = ref(".");
const query = ref("");
const entries = ref<RuntimeFileEntry[]>([]);
const loading = ref(false);
const error = ref<string>();

const currentPathLabel = computed(() => (currentPath.value === "." ? "workspace root" : currentPath.value));
const canGoUp = computed(() => !query.value.trim() && currentPath.value !== ".");

onMounted(() => {
  void loadFiles();
});

watch(
  () => workspace.selectedWorkspaceId,
  () => {
    currentPath.value = ".";
    void loadFiles();
  }
);

async function loadFiles() {
  loading.value = true;
  error.value = undefined;
  try {
    const search = query.value.trim();
    const data = search
      ? await platform.api.findRuntimeFiles(workspace.selectedWorkspaceId, search)
      : await platform.api.listRuntimeFiles(workspace.selectedWorkspaceId, currentPath.value);
    entries.value = normalizeRuntimeFiles(data);
  } catch (cause) {
    entries.value = [];
    error.value = cause instanceof Error ? cause.message : "文件树加载失败";
  } finally {
    loading.value = false;
  }
}

function openEntry(entry: RuntimeFileEntry) {
  if (!entry.directory) {
    return;
  }
  query.value = "";
  currentPath.value = entry.path || ".";
  void loadFiles();
}

function goParent() {
  if (!canGoUp.value) {
    return;
  }
  const parts = currentPath.value.split("/").filter(Boolean);
  parts.pop();
  currentPath.value = parts.length ? parts.join("/") : ".";
  void loadFiles();
}

function formatSize(entry: RuntimeFileEntry) {
  if (entry.directory) {
    return "dir";
  }
  if (typeof entry.size !== "number") {
    return "file";
  }
  if (entry.size < 1024) {
    return `${entry.size} B`;
  }
  return `${(entry.size / 1024).toFixed(1)} KB`;
}

// 运行态 fs 接口历史上返回过 data/items/裸数组，目录字段也可能是 directory 或 type。
function normalizeRuntimeFiles(value: unknown): RuntimeFileEntry[] {
  const source = record(value);
  const raw = Array.isArray(source?.data) ? source.data : Array.isArray(source?.items) ? source.items : Array.isArray(value) ? value : [];
  return raw
    .flatMap((item) => {
      const next = record(item);
      const path = text(next?.path) ?? text(next?.id) ?? text(next?.name);
      if (!next || !path) {
        return [];
      }
      const directory = bool(next.directory) ?? text(next.type) === "directory";
      return [
        {
          path,
          name: text(next.name) ?? path.split("/").filter(Boolean).pop() ?? path,
          directory,
          size: number(next.size)
        }
      ];
    })
    .sort((left, right) => Number(right.directory) - Number(left.directory) || left.path.localeCompare(right.path));
}

function record(value: unknown): Record<string, unknown> | undefined {
  return typeof value === "object" && value !== null ? (value as Record<string, unknown>) : undefined;
}

function text(value: unknown) {
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

function bool(value: unknown) {
  return typeof value === "boolean" ? value : undefined;
}

function number(value: unknown) {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}
</script>

<template>
  <section class="file-tree-panel" aria-label="Workspace files">
    <header class="file-tree-header">
      <div>
        <div class="section-label">Workspace files</div>
        <small>{{ currentPathLabel }}</small>
      </div>
      <button type="button" class="icon-button" aria-label="Refresh files" @click="loadFiles">
        <RefreshCw :size="14" />
      </button>
    </header>

    <label class="slash-search">
      <Search :size="14" />
      <input v-model="query" aria-label="Search workspace tree" autocomplete="off" placeholder="Search files" @input="loadFiles" />
    </label>

    <button v-if="canGoUp" type="button" class="file-tree-row file-tree-parent" aria-label="Go to parent directory" @click="goParent">
      <Folder :size="15" />
      <span>
        <strong>..</strong>
        <small>parent</small>
      </span>
    </button>

    <div v-if="loading" class="empty-note">Loading files...</div>
    <div v-else-if="error" class="inline-alert">{{ error }}</div>
    <div v-else-if="!entries.length" class="empty-note">No files found</div>
    <div v-else class="file-tree-list">
      <button
        v-for="entry in entries"
        :key="entry.path"
        type="button"
        class="file-tree-row"
        :aria-label="`${entry.directory ? 'Open' : 'Select'} ${entry.path} ${entry.directory ? 'directory' : 'file'}`"
        @click="openEntry(entry)"
      >
        <Folder v-if="entry.directory" :size="15" />
        <FileText v-else :size="15" />
        <span>
          <strong>{{ entry.path }}</strong>
          <small>{{ formatSize(entry) }}</small>
        </span>
      </button>
    </div>
  </section>
</template>
