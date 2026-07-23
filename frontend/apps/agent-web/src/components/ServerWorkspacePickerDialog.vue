<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import type { TerminalTicketResponse, WorkspaceBackendServer, WorkspaceDirectoryList } from "@test-agent/shared-types";
import { TerminalPanel } from "@test-agent/terminal";
import {
  AlertTriangle,
  ChevronLeft,
  ChevronRight,
  ExternalLink,
  Folder,
  Home,
  Maximize2,
  Minimize2,
  Server,
  Terminal as TerminalIcon
} from "lucide-vue-next";
import { Button } from "@test-agent/ui-kit";
import ServerWorkspaceDirectoryNode from "./ServerWorkspaceDirectoryNode.vue";
import { writeServerWorkspacePickerTabState } from "./server-workspace-picker-tab";

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
  newTabUrl?: string;
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
const isFullscreen = ref(false);
const isResizing = ref(false);

const DIALOG_MARGIN = 12;
const MIN_DIALOG_WIDTH = 720;
const MIN_DIALOG_HEIGHT = 480;
const dialogBounds = reactive({
  left: 0,
  top: 0,
  width: 1000,
  height: 640,
  initialized: false
});

const dialogLayoutMode = computed(() => (isFullscreen.value ? "fullscreen" : "window"));
const dialogStyle = computed(() => {
  if (isFullscreen.value) {
    return { left: "0px", top: "0px", width: "100vw", height: "100vh" };
  }
  return {
    left: `${dialogBounds.left}px`,
    top: `${dialogBounds.top}px`,
    width: `${dialogBounds.width}px`,
    height: `${dialogBounds.height}px`
  };
});

/**
 * 把普通窗口限制在当前页面视口内；浏览器变窄时允许最小尺寸跟随视口收缩，
 * 避免固定最小宽高把取消按钮或右下角缩放手柄推出屏幕。
 */
function clampDialogBounds() {
  const viewportWidth = window.innerWidth;
  const viewportHeight = window.innerHeight;
  const horizontalMargin = viewportWidth > MIN_DIALOG_WIDTH ? DIALOG_MARGIN : 0;
  const verticalMargin = viewportHeight > MIN_DIALOG_HEIGHT ? DIALOG_MARGIN : 0;
  const maxWidth = Math.max(320, viewportWidth - horizontalMargin * 2);
  const maxHeight = Math.max(320, viewportHeight - verticalMargin * 2);
  const minWidth = Math.min(MIN_DIALOG_WIDTH, maxWidth);
  const minHeight = Math.min(MIN_DIALOG_HEIGHT, maxHeight);

  dialogBounds.width = Math.min(maxWidth, Math.max(minWidth, dialogBounds.width));
  dialogBounds.height = Math.min(maxHeight, Math.max(minHeight, dialogBounds.height));
  dialogBounds.left = Math.min(
    Math.max(horizontalMargin, dialogBounds.left),
    Math.max(horizontalMargin, viewportWidth - horizontalMargin - dialogBounds.width)
  );
  dialogBounds.top = Math.min(
    Math.max(verticalMargin, dialogBounds.top),
    Math.max(verticalMargin, viewportHeight - verticalMargin - dialogBounds.height)
  );
}

/** 首次打开按既有 1000px × 75vh 视觉规格居中，后续打开保留用户在本次页面中的缩放结果。 */
function initializeDialogBounds() {
  if (!dialogBounds.initialized) {
    const availableWidth = Math.max(320, window.innerWidth - DIALOG_MARGIN * 2);
    const availableHeight = Math.max(320, window.innerHeight - DIALOG_MARGIN * 2);
    dialogBounds.width = Math.min(1000, availableWidth);
    dialogBounds.height = Math.min(Math.round(window.innerHeight * 0.75), availableHeight);
    dialogBounds.left = Math.max(0, Math.round((window.innerWidth - dialogBounds.width) / 2));
    dialogBounds.top = Math.max(0, Math.round((window.innerHeight - dialogBounds.height) / 2));
    dialogBounds.initialized = true;
  }
  clampDialogBounds();
}

type ResizeSnapshot = {
  pointerId: number;
  startX: number;
  startY: number;
  width: number;
  height: number;
  bodyCursor: string;
  bodyUserSelect: string;
};

let resizeSnapshot: ResizeSnapshot | null = null;

/** 右下角缩放使用全局 Pointer Events，指针离开手柄后仍保持 1:1 连续跟随。 */
function startResize(event: PointerEvent) {
  if (isFullscreen.value) return;
  // pointerdown 被模板 preventDefault 后浏览器不会自动聚焦按钮，需显式保留后续方向键调整能力。
  (event.currentTarget as HTMLElement | null)?.focus();
  resizeSnapshot = {
    pointerId: event.pointerId,
    startX: event.clientX,
    startY: event.clientY,
    width: dialogBounds.width,
    height: dialogBounds.height,
    bodyCursor: document.body.style.cursor,
    bodyUserSelect: document.body.style.userSelect
  };
  isResizing.value = true;
  document.body.style.cursor = "nwse-resize";
  document.body.style.userSelect = "none";
  window.addEventListener("pointermove", resizeDialog);
  window.addEventListener("pointerup", stopResize);
  window.addEventListener("pointercancel", stopResize);
}

function resizeDialog(event: PointerEvent) {
  if (!resizeSnapshot || event.pointerId !== resizeSnapshot.pointerId) return;
  dialogBounds.width = resizeSnapshot.width + event.clientX - resizeSnapshot.startX;
  dialogBounds.height = resizeSnapshot.height + event.clientY - resizeSnapshot.startY;
  clampDialogBounds();
}

function stopResize(event?: PointerEvent) {
  if (event && resizeSnapshot && event.pointerId !== resizeSnapshot.pointerId) return;
  if (resizeSnapshot) {
    document.body.style.cursor = resizeSnapshot.bodyCursor;
    document.body.style.userSelect = resizeSnapshot.bodyUserSelect;
  }
  resizeSnapshot = null;
  isResizing.value = false;
  window.removeEventListener("pointermove", resizeDialog);
  window.removeEventListener("pointerup", stopResize);
  window.removeEventListener("pointercancel", stopResize);
}

/** 键盘聚焦缩放手柄后可用方向键调整尺寸，Shift 将单次步长从 16px 提升到 48px。 */
function resizeDialogByKeyboard(event: KeyboardEvent) {
  const step = event.shiftKey ? 48 : 16;
  if (event.key === "ArrowRight") dialogBounds.width += step;
  else if (event.key === "ArrowLeft") dialogBounds.width -= step;
  else if (event.key === "ArrowDown") dialogBounds.height += step;
  else if (event.key === "ArrowUp") dialogBounds.height -= step;
  else return;
  event.preventDefault();
  clampDialogBounds();
}

function toggleFullscreen() {
  isFullscreen.value = !isFullscreen.value;
}

/** 使用真实应用 URL 打开普通标签页，避免 about:blank，也支持地址栏识别和刷新恢复。 */
function openInNewTab() {
  const targetUrl = new URL(props.newTabUrl || window.location.href, window.location.href).href;
  writeServerWorkspacePickerTabState({
    serverId: props.selectedServerId,
    path: props.directory?.path
  });
  // 不传 popup/尺寸参数，行为与用户手册一致，由 Chrome 作为普通新标签页打开。
  if (!window.open(targetUrl, "_blank")) {
    ElMessage.warning("浏览器阻止了新标签页，请允许此站点打开弹窗后重试");
  }
}

/** 关闭弹窗时返回目录视图；切换服务器由 TerminalPanel key 负责关闭旧会话。 */
watch(
  () => props.open,
  (open) => {
    if (open) {
      initializeDialogBounds();
      return;
    }
    activeView.value = "workspace";
    isFullscreen.value = false;
  }
);

onMounted(() => {
  window.addEventListener("resize", clampDialogBounds);
  if (props.open) initializeDialogBounds();
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", clampDialogBounds);
  stopResize();
});

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
    <div v-if="open" :class="['fixed inset-0 z-[1000]', isFullscreen ? 'bg-[var(--ta-panel)]' : 'bg-black/35']">
      <section
        role="dialog"
        aria-modal="true"
        aria-label="选择服务器工作空间"
        :data-layout-mode="dialogLayoutMode"
        :style="dialogStyle"
        :class="[
          'fixed flex flex-col overflow-hidden bg-[var(--ta-panel)]',
          isFullscreen
            ? 'rounded-none border-0 shadow-none'
            : 'rounded-lg border border-[var(--ta-border)] shadow-xl'
        ]"
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
            <div class="flex items-center gap-0.5 border-l border-[var(--ta-border)] pl-2">
              <Button variant="ghost" size="icon" class="h-7 w-7" type="button" aria-label="在新标签页打开" title="在新标签页打开" @click="openInNewTab">
                <ExternalLink class="h-3.5 w-3.5" />
              </Button>
              <Button
                variant="ghost"
                size="icon"
                class="h-7 w-7"
                type="button"
                :aria-label="isFullscreen ? '退出全屏' : '进入全屏'"
                :title="isFullscreen ? '退出全屏' : '进入全屏'"
                @click="toggleFullscreen"
              >
                <Minimize2 v-if="isFullscreen" class="h-3.5 w-3.5" />
                <Maximize2 v-else class="h-3.5 w-3.5" />
              </Button>
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

        <button
          v-if="!isFullscreen"
          type="button"
          class="dialog-resize-handle absolute bottom-0 right-0 z-20 h-5 w-5 cursor-nwse-resize rounded-tl focus-visible:outline-2 focus-visible:outline-offset-[-3px] focus-visible:outline-[#2563eb]"
          :class="{ 'is-resizing': isResizing }"
          aria-label="调整窗口大小"
          title="拖动调整窗口大小；聚焦后可使用方向键"
          @pointerdown.prevent="startResize"
          @keydown="resizeDialogByKeyboard"
        />
      </section>
    </div>
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

.dialog-resize-handle {
  background:
    linear-gradient(135deg, transparent 50%, rgba(100, 116, 139, 0.45) 50% 58%, transparent 58%) 4px 4px / 12px 12px no-repeat,
    linear-gradient(135deg, transparent 50%, rgba(100, 116, 139, 0.7) 50% 58%, transparent 58%) 8px 8px / 8px 8px no-repeat;
}

.dialog-resize-handle:hover,
.dialog-resize-handle.is-resizing {
  background-color: var(--ta-hover);
}
</style>
