<script setup lang="ts">
import { ElMessageBox } from "element-plus";
import { computed, nextTick, ref, watch } from "vue";
import type { TerminalTicketResponse, WorkspaceBackendServer, WorkspaceDirectoryList } from "@test-agent/shared-types";
import { TerminalPanel } from "@test-agent/terminal";
import { AlertTriangle, ChevronLeft, ChevronRight, Folder, Home, Server, Terminal as TerminalIcon } from "lucide-vue-next";
import { Button } from "@test-agent/ui-kit";
import ServerWorkspaceDirectoryNode from "./ServerWorkspaceDirectoryNode.vue";

const props = defineProps<{
  open: boolean;
  servers: WorkspaceBackendServer[];
  selectedServerId?: string;
  directory: WorkspaceDirectoryList | null;
  loading: boolean;
  currentAgentLinuxServerId?: string;
  serverTerminalEnabled?: boolean;
  terminalBaseUrl?: string;
  createServerTerminalTicket?: (linuxServerId: string, confirmationText: string) => Promise<TerminalTicketResponse>;
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
const activeView = ref<"workspace" | "terminal">("workspace");

/** 关闭弹窗时返回目录视图；切换服务器由 TerminalPanel key 负责关闭旧会话。 */
watch(
  () => props.open,
  (open) => {
    if (!open) activeView.value = "workspace";
  }
);

function openServerTerminal() {
  if (!props.serverTerminalEnabled || !selectedServer.value) return;
  activeView.value = "terminal";
}

/**
 * 连接前展示目标服务器二次确认；确认后自动组装只用于防止目标串线的绑定值。
 * 用户取消使用 AbortError 收敛为 idle，不在终端中显示伪失败。
 */
async function createSelectedServerTerminalTicket() {
  if (!selectedServer.value || !props.createServerTerminalTicket) {
    throw new Error("当前服务器终端不可用");
  }
  const server = selectedServer.value;
  try {
    await ElMessageBox.confirm(
      `即将连接服务器 ${server.name || server.linuxServerId}（${server.linuxServerId}）。终端权限与启动目标 Java 的系统用户完全一致，不会额外提权。`,
      "确认连接服务器终端",
      {
        type: "warning",
        confirmButtonText: "确认连接",
        cancelButtonText: "取消",
        distinguishCancelAndClose: true,
        autofocus: false
      }
    );
  } catch (error) {
    if (error === "cancel" || error === "close") {
      const aborted = new Error("用户取消连接");
      aborted.name = "AbortError";
      throw aborted;
    }
    throw error;
  }
  return props.createServerTerminalTicket(server.linuxServerId, `SERVER@${server.linuxServerId}`);
}

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

const breadcrumbsRef = ref<HTMLElement | null>(null);

const scrollToRight = async () => {
  await nextTick();
  if (breadcrumbsRef.value) {
    breadcrumbsRef.value.scrollLeft = breadcrumbsRef.value.scrollWidth;
  }
};

watch(() => props.directory?.path, scrollToRight);
watch(() => props.open, (isOpen) => {
  if (isOpen) {
    scrollToRight();
  }
});

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
const selectedDirectoryPath = computed(() => props.directory?.path ?? selectedServer.value?.defaultDirectory ?? "");
const selectedDirectoryName = computed(() => {
  const path = selectedDirectoryPath.value;
  if (!path) return "—";
  const normalizedPath = path.replace(/\\/g, "/").replace(/\/+$/, "");
  const lastSegment = normalizedPath.split("/").filter(Boolean).at(-1);
  return lastSegment || path;
});

// Parse path into breadcrumbs with safety for Linux/Windows structures
const breadcrumbs = computed(() => {
  const pathStr = selectedDirectoryPath.value;
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
        class="flex h-[75vh] w-[1000px] max-h-[calc(100vh-48px)] max-w-[calc(100vw-24px)] flex-col rounded-lg border border-[var(--ta-border)] bg-[var(--ta-panel)] shadow-xl"
      >
        <header class="flex h-12 shrink-0 items-center justify-between border-b border-[var(--ta-border)] px-4">
          <h2 class="text-[14px] font-semibold text-[var(--ta-text)]">选择服务器工作空间</h2>
          <div class="flex items-center gap-2">
            <div v-if="serverTerminalEnabled" class="flex items-center rounded-md border border-[var(--ta-border)] bg-[#f8fafc] p-0.5" aria-label="服务器工具">
              <button
                type="button"
                :class="[
                  'rounded px-2.5 py-1 text-[11px] font-semibold transition-colors',
                  activeView === 'workspace' ? 'bg-white text-[#1d4ed8] shadow-sm' : 'text-gray-500 hover:text-gray-800'
                ]"
                @click="activeView = 'workspace'"
              >浏览目录</button>
              <button
                type="button"
                :class="[
                  'flex items-center gap-1 rounded px-2.5 py-1 text-[11px] font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-40',
                  activeView === 'terminal' ? 'bg-[#7f1d1d] text-white shadow-sm' : 'text-[#b91c1c] hover:bg-red-50'
                ]"
                :disabled="!selectedServer"
                @click="openServerTerminal"
              ><TerminalIcon class="h-3.5 w-3.5" />服务器终端</button>
            </div>
            <Button variant="ghost" size="sm" @click="emit('close')">取消</Button>
          </div>
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
          <main v-if="activeView === 'workspace'" class="flex flex-col min-h-0 p-3 bg-[#fafafa]">
            <!-- Warning banner -->
            <div v-if="disabledReason" class="mb-2 flex items-center gap-2 rounded-md bg-red-50 border border-red-100 px-3 py-1.5 text-[12px] text-red-600 shrink-0 select-none">
              <AlertTriangle class="h-3.5 w-3.5 text-red-500 shrink-0" />
              <span>{{ disabledReason }}</span>
            </div>

            <!-- macOS Finder-style Navigation & Location Bar -->
            <div class="flex items-center gap-3 bg-white border border-[var(--ta-border)] rounded-md p-1.5 shrink-0 select-none shadow-sm">
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
              <div
                ref="breadcrumbsRef"
                class="flex flex-1 min-w-0 items-center gap-0.5 px-2.5 py-1 bg-white border border-[var(--ta-border)] rounded h-7 overflow-x-auto scrollbar-none"
              >
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
            </div>

            <!-- 当前目录才是将被选择的工作空间，下面列表仅用于进入子目录。 -->
            <div class="my-3 flex items-start gap-3 rounded-md border border-[#bfdbfe] bg-[#eff6ff] px-3 py-2 shadow-sm">
              <div class="min-w-0 flex-1">
                <div class="text-[11px] font-semibold text-[#1d4ed8]">将作为工作空间选择</div>
                <div
                  class="mt-1 flex min-w-0 items-start gap-1.5 text-[13px] font-semibold leading-5 text-[#111827]"
                  :title="selectedDirectoryName"
                >
                  <Folder class="mt-0.5 h-3.5 w-3.5 shrink-0 text-[#2563eb]" />
                  <span class="min-w-0 break-all">{{ selectedDirectoryName }}</span>
                </div>
                <div
                  class="mt-0.5 break-all font-mono text-[11px] leading-4 text-[#1e3a8a]"
                  :title="selectedDirectoryPath"
                >
                  {{ selectedDirectoryPath || "—" }}
                </div>
              </div>
              <Button
                size="sm"
                class="h-8 shrink-0 text-[12px] font-medium"
                :disabled="loading || serverMismatch || !directory || !selectedServer"
                @click="directory && selectedServer && emit('select', { server: selectedServer, path: directory.path })"
              >
                使用当前目录
              </Button>
            </div>

            <!-- macOS Finder list-view container -->
            <div class="flex-1 flex flex-col min-h-0 bg-white border border-[var(--ta-border)] rounded-md overflow-hidden shadow-sm">
              <div class="flex h-8 shrink-0 items-center justify-between border-b border-[var(--ta-border)] bg-[#fafafa] px-3 text-[11px] font-semibold text-gray-500">
                <span>子目录（点击进入）</span>
                <span v-if="directory && !loading" class="font-normal text-gray-400">{{ directory.entries.length }} 个</span>
              </div>
              <!-- Content -->
              <div class="flex-1 overflow-y-auto min-h-0 py-1">
                <div v-if="loading" class="px-3 py-8 text-[13px] text-[var(--ta-muted)] text-center">正在加载目录</div>
                <div v-else-if="serverMismatch" class="px-3 py-8 text-[13px] text-[var(--ta-muted)] text-center">请选择与当前 agent 相同的服务器后继续。</div>
                <div v-else-if="!directory?.entries.length" class="px-3 py-8 text-[13px] text-[var(--ta-muted)] text-center">当前目录没有可进入的子目录，可直接使用当前目录。</div>
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
              <!-- macOS Finder-style path status bar -->
              <div class="flex items-center gap-1.5 px-3 py-1.5 bg-[#fafafa] border-t border-[var(--ta-border)] text-[10.5px] text-gray-400 font-mono select-all shrink-0">
                <span class="font-sans font-medium text-gray-500">当前路径:</span>
                <span class="truncate" :title="directory?.path ?? selectedServer?.defaultDirectory ?? ''">
                  {{ directory?.path ?? selectedServer?.defaultDirectory ?? "—" }}
                </span>
              </div>
            </div>
          </main>

          <!-- 服务器终端固定在有明确高度的选择器内容区内，避免再次创建无高度边界的弹窗。 -->
          <main v-else class="flex min-h-0 flex-col gap-3 bg-[#fafafa] p-3">
            <div class="flex shrink-0 items-start gap-2 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-[12px] leading-5 text-red-800" role="alert">
              <AlertTriangle class="mt-0.5 h-4 w-4 shrink-0 text-red-600" />
              <span>
                这是 <strong>{{ selectedServer?.name || selectedServer?.linuxServerId }}</strong> 的部署服务器 shell，命令会直接修改该服务器。
                它不使用 SSH、sudo 或额外授权，权限与启动目标 Java 的系统用户完全一致。
              </span>
            </div>

            <section class="flex shrink-0 items-center justify-between gap-3 rounded-md border border-[var(--ta-border)] bg-white px-3 py-2 text-[12px] text-gray-600 shadow-sm">
              <span>点击“连接服务器终端”后，请在二次确认中核对目标服务器。</span>
              <code v-if="selectedServer" class="shrink-0 font-mono font-semibold text-gray-700">{{ selectedServer.linuxServerId }}</code>
            </section>

            <div class="min-h-0 flex-1">
              <TerminalPanel
                v-if="selectedServer"
                :key="selectedServer.linuxServerId"
                class="h-full min-h-0"
                :base-url="terminalBaseUrl ?? ''"
                :create-ticket="createSelectedServerTerminalTicket"
                :title="`server@${selectedServer.linuxServerId}`"
                connect-label="连接服务器终端"
                danger
              />
              <div v-else class="flex h-full items-center justify-center text-[13px] text-gray-400">请先选择服务器。</div>
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
