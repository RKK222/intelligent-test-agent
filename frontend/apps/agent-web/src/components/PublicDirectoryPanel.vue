<script setup lang="ts">
import { ref, watch } from "vue";
import { AlertTriangle, Loader2, RefreshCw } from "lucide-vue-next";
import { createBackendApiClient, BackendApiError } from "@test-agent/backend-api";
import type { FileContent, FileTreeEntry } from "@test-agent/shared-types";
import PublicDirectoryNode from "./PublicDirectoryNode.vue";

/**
 * 公共目录面板：浏览 application.yml 中 test-agent.public-directory.path 指定的固定根目录。
 *
 * <p>只读浏览 + 点击文件以可读/可写 tab 打开的最小化实现，不直接调后端以外的逻辑：
 * <ul>
 *   <li>展开/读取：所有登录用户</li>
 *   <li>写入：仅 SUPER_ADMIN（在 canWrite 为 true 时显示）</li>
 * </ul>
 *
 * <p>树形渲染由递归子组件 {@link PublicDirectoryNode} 完成，支持任意层级；
 * 本组件只维护 path -> 子项 / 已展开目录 / 正在加载目录 三份状态。
 */

const props = defineProps<{
  /** 是否允许编辑（通常仅 SUPER_ADMIN 为 true），影响打开的 tab 是否可写 */
  canWrite: boolean;
  /** 后端 base url，从父级 environment 透传 */
  baseUrl: string;
}>();

const emit = defineEmits<{
  /** 打开文件并附带只读/可写标志；由父组件决定如何渲染 tab */
  openFile: [payload: { path: string; content: FileContent; readonly: boolean }];
}>();

const api = createBackendApiClient({ baseUrl: props.baseUrl });

// 目录树：path -> 子项；expandedDirectories 记录已展开的目录集合。
const entriesByDirectory = ref<Record<string, FileTreeEntry[]>>({});
const expandedDirectories = ref<Set<string>>(new Set());
const loadingPath = ref<Set<string>>(new Set());
// 错误：空字符串表示无错误；非空则展示在面板顶部。
const errorMessage = ref("");

// 第一次进入面板时拉取根目录。
const loaded = ref(false);
async function loadRoot() {
  await loadDirectory("");
  loaded.value = true;
}
void loadRoot();

async function loadDirectory(path: string) {
  if (entriesByDirectory.value[path] !== undefined || loadingPath.value.has(path)) {
    return;
  }
  const next = new Set(loadingPath.value);
  next.add(path);
  loadingPath.value = next;
  errorMessage.value = "";
  try {
    const entries = await api.listPublicFiles(path);
    entriesByDirectory.value = { ...entriesByDirectory.value, [path]: entries };
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "加载公共目录失败");
    if (expandedDirectories.value.has(path)) {
      const nextExpanded = new Set(expandedDirectories.value);
      nextExpanded.delete(path);
      expandedDirectories.value = nextExpanded;
    }
  } finally {
    const cleared = new Set(loadingPath.value);
    cleared.delete(path);
    loadingPath.value = cleared;
  }
}

function toggleDirectory(path: string) {
  if (loadingPath.value.has(path)) return;
  const next = new Set(expandedDirectories.value);
  if (next.has(path)) {
    next.delete(path);
  } else {
    next.add(path);
    if (entriesByDirectory.value[path] === undefined) {
      void loadDirectory(path);
    }
  }
  expandedDirectories.value = next;
}

async function openFile(path: string) {
  errorMessage.value = "";
  try {
    const file = await api.readPublicFile(path);
    // readonly 由父组件按 canWrite 决定：普通用户永远只读；超级管理员可写。
    emit("openFile", { path, content: file, readonly: !props.canWrite });
  } catch (error) {
    errorMessage.value = errorMessageFor(error, "读取公共文件失败");
  }
}

function refresh() {
  // 重新拉取所有已展开的目录，保留展开状态。
  const expanded = [...expandedDirectories.value];
  entriesByDirectory.value = {};
  expandedDirectories.value = new Set();
  void loadDirectory("");
  expanded.forEach((path) => {
    expandedDirectories.value.add(path);
  });
}

const headerLabel = "公共目录";
const headerTitle = "公共目录（由后端 test-agent.public-directory.path 配置）";

function errorMessageFor(error: unknown, fallback: string): string {
  if (error instanceof BackendApiError) {
    return `${fallback}：${error.message}`;
  }
  if (error instanceof Error) {
    return `${fallback}：${error.message}`;
  }
  return fallback;
}

// 监听 baseUrl 变化后重新构造 client（开发期 hot reload 也会触发）。
watch(
  () => props.baseUrl,
  () => {
    // 简化处理：仅刷新根目录；不重建 api 闭包（baseUrl 仅在初始化时使用）。
    entriesByDirectory.value = {};
    expandedDirectories.value = new Set();
    void loadDirectory("");
  }
);
</script>

<template>
  <div class="flex h-full min-h-0 flex-col bg-[var(--ta-panel)]">
    <div class="flex h-7 items-center justify-between border-b border-[var(--ta-border)] px-2 text-[12px] font-semibold text-[var(--ta-muted)]">
      <span :title="headerTitle">{{ headerLabel }}</span>
      <div class="flex items-center gap-1">
        <button
          type="button"
          class="ta-pd-icon-btn"
          title="刷新公共目录"
          aria-label="刷新公共目录"
          @click="refresh"
        >
          <RefreshCw class="h-3.5 w-3.5" :stroke-width="1.5" />
        </button>
      </div>
    </div>
    <div v-if="errorMessage" class="flex items-start gap-1 border-b border-[var(--ta-border)] bg-[#fff7ed] px-2 py-1 text-[12px] text-[#9a3412]">
      <AlertTriangle class="mt-[2px] h-3.5 w-3.5 shrink-0" :stroke-width="1.5" />
      <span class="min-w-0 flex-1 break-words">{{ errorMessage }}</span>
    </div>
    <div class="min-h-0 flex-1 overflow-auto px-2 py-2 text-[14px]">
      <div v-if="!loaded" class="flex items-center gap-1 px-2 py-1 text-[12px] text-[var(--ta-muted)]">
        <Loader2 class="h-3.5 w-3.5 animate-spin" :stroke-width="1.5" />
        加载中…
      </div>
      <div v-else>
        <PublicDirectoryNode
          v-for="entry in entriesByDirectory[''] ?? []"
          :key="entry.path"
          :entry="entry"
          :depth="0"
          :entries-by-directory="entriesByDirectory"
          :expanded-directories="expandedDirectories"
          :loading-path="loadingPath"
          @toggle="toggleDirectory"
          @open-file="openFile"
        />
        <div
          v-if="(entriesByDirectory[''] ?? []).length === 0"
          class="px-2 py-3 text-[12px] text-[var(--ta-muted)]"
        >
          公共目录为空或后端未配置。
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ta-pd-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--ta-muted, #6b7280);
  cursor: pointer;
  transition: background-color 0.12s ease, color 0.12s ease;
}
.ta-pd-icon-btn:hover {
  color: var(--ta-text, #18181b);
}
</style>
