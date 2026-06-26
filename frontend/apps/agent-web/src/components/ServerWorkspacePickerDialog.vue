<script setup lang="ts">
import { computed, ref, watch } from "vue";
import type { WorkspaceBackendServer, WorkspaceDirectoryList } from "@test-agent/shared-types";
import { AlertTriangle, ChevronLeft, ChevronRight, Folder, Home, Server } from "lucide-vue-next";
import { Button } from "@test-agent/ui-kit";
import ServerWorkspaceDirectoryNode from "./ServerWorkspaceDirectoryNode.vue";

const props = defineProps<{
  open: boolean;
  servers: WorkspaceBackendServer[];
  selectedServerId?: string;
  directory: WorkspaceDirectoryList | null;
  loading: boolean;
  currentAgentLinuxServerId?: string;
}>();

const emit = defineEmits<{
  close: [];
  selectServer: [server: WorkspaceBackendServer];
  navigate: [path: string];
  select: [payload: { server: WorkspaceBackendServer; path: string }];
}>();

const selectedServer = computed(() => props.servers.find((server) => server.linuxServerId === props.selectedServerId));
const serverMismatch = computed(
  () => Boolean(selectedServer.value && props.currentAgentLinuxServerId && selectedServer.value.linuxServerId !== props.currentAgentLinuxServerId)
);
const disabledReason = computed(() => (serverMismatch.value ? "工作空间与 agent 不在同一服务器" : ""));

// macOS Finder-style navigation history stack
const historyStack = ref<string[]>([]);
const historyIndex = ref(-1);

watch(
  () => props.directory?.path,
  (newPath) => {
    if (!newPath) return;
    if (historyIndex.value === -1 || historyStack.value[historyIndex.value] !== newPath) {
      // Truncate any forward history and append the new path
      historyStack.value = historyStack.value.slice(0, historyIndex.value + 1);
      historyStack.value.push(newPath);
      historyIndex.value = historyStack.value.length - 1;
    }
  },
  { immediate: true }
);

watch(
  () => props.selectedServerId,
  () => {
    historyStack.value = [];
    historyIndex.value = -1;
  }
);

function goBack() {
  if (historyIndex.value > 0) {
    historyIndex.value--;
    emit("navigate", historyStack.value[historyIndex.value]);
  }
}

function goForward() {
  if (historyIndex.value < historyStack.value.length - 1) {
    historyIndex.value++;
    emit("navigate", historyStack.value[historyIndex.value]);
  }
}

const canGoBack = computed(() => historyIndex.value > 0);
const canGoForward = computed(() => historyIndex.value < historyStack.value.length - 1);

// Parse path into breadcrumbs with safety for Linux/Windows structures
const breadcrumbs = computed(() => {
  const pathStr = props.directory?.path || selectedServer.value?.defaultDirectory || "";
  if (!pathStr) return [];

  const normalizedPath = pathStr.replace(/\\/g, "/");
  const isWindows = pathStr.includes(":") || pathStr.startsWith("\\\\");
  const parts = normalizedPath.split("/").filter(Boolean);
  const list: { name: string; path: string; isHome: boolean }[] = [];

  let currentPath = "";

  if (isWindows && parts.length > 0 && parts[0].includes(":")) {
    currentPath = parts[0];
    list.push({
      name: parts[0],
      path: currentPath,
      isHome: false
    });
    for (let i = 1; i < parts.length; i++) {
      currentPath += "/" + parts[i];
      const isHome = i === 2 && (parts[1] === "Users" || parts[1] === "home");
      list.push({
        name: parts[i],
        path: currentPath,
        isHome
      });
    }
  } else {
    for (let i = 0; i < parts.length; i++) {
      currentPath += "/" + parts[i];
      const isHome = i === 1 && (parts[0] === "Users" || parts[0] === "home");
      list.push({
        name: parts[i],
        path: currentPath,
        isHome
      });
    }
  }
  return list;
});
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="fixed inset-0 z-[1000] flex items-center justify-center bg-black/35 px-4 py-6">
      <section
        role="dialog"
        aria-modal="true"
        aria-label="选择服务器工作空间"
        class="flex h-[580px] w-[840px] max-h-[calc(100vh-48px)] max-w-[calc(100vw-24px)] flex-col rounded-lg border border-[var(--ta-border)] bg-[var(--ta-panel)] shadow-xl"
      >
        <header class="flex h-12 shrink-0 items-center justify-between border-b border-[var(--ta-border)] px-4">
          <h2 class="text-[14px] font-semibold text-[var(--ta-text)]">选择服务器工作空间</h2>
          <Button variant="ghost" size="sm" @click="emit('close')">取消</Button>
        </header>

        <div class="grid min-h-0 flex-1 grid-cols-[260px_minmax(0,1fr)] bg-[#f3f4f6]">
          <!-- Sidebar: Backend Servers -->
          <aside class="min-h-0 border-r border-[var(--ta-border)] bg-white p-2">
            <div class="px-2 pb-2 text-[11px] font-semibold text-gray-400 select-none tracking-wider">设备 / 服务器</div>
            <button
              v-for="server in servers"
              :key="server.linuxServerId"
              type="button"
              :class="[
                'flex h-11 w-full items-center gap-2 rounded-md px-2.5 text-left text-[12px] transition-colors duration-150',
                server.linuxServerId === selectedServerId
                  ? 'bg-[#e8f2ff] text-[#0063e1] font-semibold'
                  : 'text-gray-700 hover:bg-gray-100'
              ]"
              @click="emit('selectServer', server)"
            >
              <Server :class="['h-4 w-4 shrink-0', server.linuxServerId === selectedServerId ? 'text-[#0063e1]' : 'text-gray-400']" />
              <span class="min-w-0 flex-1">
                <span class="block truncate">{{ server.name || server.linuxServerId }}</span>
                <span :class="['block truncate text-[10px]', server.linuxServerId === selectedServerId ? 'text-[#0063e1]/80' : 'text-gray-400']">
                  {{ server.baseUrl }}
                </span>
              </span>
            </button>
            <div v-if="!servers.length" class="px-2 py-4 text-[12px] text-gray-400">暂无可用后端服务器</div>
          </aside>

          <!-- Main Content: Directory Explorer -->
          <main class="flex flex-col min-h-0 p-3 bg-[#fafafa]">
            <!-- Warning banner -->
            <div v-if="disabledReason" class="mb-2 flex items-center gap-2 rounded-md bg-red-50 border border-red-100 px-3 py-1.5 text-[12px] text-red-600 shrink-0 select-none">
              <AlertTriangle class="h-3.5 w-3.5 text-red-500 shrink-0" />
              <span>{{ disabledReason }}</span>
            </div>

            <!-- macOS Finder-style Navigation & Location Bar -->
            <div class="flex items-center gap-3 mb-3 bg-white border border-[var(--ta-border)] rounded-md p-1.5 shrink-0 select-none shadow-sm">
              <!-- Back / Forward Buttons -->
              <div class="flex items-center gap-0.5 shrink-0">
                <button
                  type="button"
                  class="flex items-center justify-center w-7 h-7 rounded-md text-gray-600 hover:bg-gray-100 disabled:opacity-30 disabled:hover:bg-transparent"
                  :disabled="!canGoBack || loading || serverMismatch"
                  title="后退"
                  @click="goBack"
                >
                  <ChevronLeft class="h-4 w-4" />
                </button>
                <button
                  type="button"
                  class="flex items-center justify-center w-7 h-7 rounded-md text-gray-600 hover:bg-gray-100 disabled:opacity-30 disabled:hover:bg-transparent"
                  :disabled="!canGoForward || loading || serverMismatch"
                  title="前进"
                  @click="goForward"
                >
                  <ChevronRight class="h-4 w-4" />
                </button>
              </div>

              <!-- Location breadcrumbs -->
              <div class="flex flex-1 min-w-0 items-center gap-0.5 px-2.5 py-1 bg-white border border-[var(--ta-border)] rounded h-7 overflow-x-auto scrollbar-none">
                <span v-if="breadcrumbs.length === 0" class="text-gray-400 select-none text-[12px]">未选择目录</span>
                <template v-else>
                  <div
                    v-for="(item, idx) in breadcrumbs"
                    :key="item.path"
                    class="flex items-center shrink-0"
                  >
                    <span v-if="idx > 0" class="mx-0.5 text-[10px] text-gray-400 select-none shrink-0">▶</span>
                    <button
                      type="button"
                      class="flex items-center gap-1 text-[12px] hover:text-[#0063e1] text-gray-700 hover:underline shrink-0"
                      :disabled="loading || serverMismatch"
                      @click="emit('navigate', item.path)"
                    >
                      <Home v-if="item.isHome" class="h-3.5 w-3.5 text-[#3b82f6] shrink-0" />
                      <Folder v-else-if="idx > 0" class="h-3.5 w-3.5 text-gray-400 shrink-0" />
                      <span :class="[idx === breadcrumbs.length - 1 ? 'font-semibold text-gray-900' : 'text-gray-500']">
                        {{ item.name }}
                      </span>
                    </button>
                  </div>
                </template>
              </div>

              <!-- Select Directory Button -->
              <Button
                size="sm"
                class="h-7 shrink-0 text-[12px] font-medium"
                :disabled="loading || serverMismatch || !directory || !selectedServer"
                @click="directory && selectedServer && emit('select', { server: selectedServer, path: directory.path })"
              >
                选择此目录
              </Button>
            </div>

            <!-- macOS Finder list-view container -->
            <div class="flex-1 flex flex-col min-h-0 bg-white border border-[var(--ta-border)] rounded-md overflow-hidden shadow-sm">
              <!-- Content -->
              <div class="flex-1 overflow-y-auto min-h-0 py-1">
                <div v-if="loading" class="px-3 py-8 text-[13px] text-[var(--ta-muted)] text-center">正在加载目录</div>
                <div v-else-if="serverMismatch" class="px-3 py-8 text-[13px] text-[var(--ta-muted)] text-center">请选择与当前 agent 相同的服务器后继续。</div>
                <div v-else-if="!directory?.entries.length" class="px-3 py-8 text-[13px] text-[var(--ta-muted)] text-center">没有可进入的子目录</div>
                <template v-else>
                  <ServerWorkspaceDirectoryNode
                    v-for="entry in directory.entries"
                    :key="entry.path"
                    :entry="entry"
                    :level="0"
                    :disabled="serverMismatch"
                    :selected-server="selectedServer"
                    @navigate="(path) => emit('navigate', path)"
                  />
                </template>
              </div>
            </div>
          </main>
        </div>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
/* Hide scrollbar for Chrome, Safari and Opera */
.scrollbar-none::-webkit-scrollbar {
  display: none;
}
/* Hide scrollbar for IE, Edge and Firefox */
.scrollbar-none {
  -ms-overflow-style: none;  /* IE and Edge */
  scrollbar-width: none;  /* Firefox */
}
</style>
