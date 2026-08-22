<script setup lang="ts">
import { nextTick, ref, watch, type Component } from "vue";
import {
  Activity,
  BarChart3,
  Boxes,
  BrainCircuit,
  CalendarClock,
  Fingerprint,
  KeyRound,
  List,
  Network,
  Radar,
  RefreshCw,
  Settings2,
  SlidersHorizontal,
  Trash2,
  Waypoints,
  Wrench,
  X
} from "lucide-vue-next";
import { workspacePageTab } from "./workspace-page-tabs";
import type {
  WorkspacePageCloseMode,
  WorkspacePageId,
  WorkspacePageTab
} from "./workspace-page-tabs";

const props = defineProps<{
  tabs: WorkspacePageTab[];
  activeId: WorkspacePageId | null;
}>();

const emit = defineEmits<{
  activate: [id: WorkspacePageId];
  close: [id: WorkspacePageId, mode: WorkspacePageCloseMode];
  move: [id: WorkspacePageId, targetIndex: number];
}>();

const tabListRef = ref<HTMLElement | null>(null);
const draggingId = ref<WorkspacePageId | null>(null);
const dropIndex = ref<number | null>(null);
const contextMenu = ref<{ id: WorkspacePageId; x: number; y: number } | null>(null);
const pageListOpen = ref(false);

const PAGE_ICONS: Partial<Record<WorkspacePageId, Component>> = {
  toolbox: Wrench,
  memories: BrainCircuit,
  hub: Boxes,
  "system:scheduler": CalendarClock,
  "system:runtime": Activity,
  "system:localClientVersions": RefreshCw,
  "system:params": SlidersHorizontal,
  "system:apiKeys": Fingerprint,
  "system:internalModels": Network,
  "system:internalModelObservability": Radar,
  "system:memory": BrainCircuit,
  "system:config": Settings2,
  "system:analytics": BarChart3,
  "system:traces": Waypoints,
  "system:support": KeyRound
};

watch(() => props.activeId, async (activeId) => {
  if (!activeId) return;
  await nextTick();
  const activeTab = Array.from(tabListRef.value?.querySelectorAll<HTMLElement>("[role='tab']") ?? [])
    .find((element) => element.dataset.pageId === activeId);
  activeTab?.scrollIntoView?.({ block: "nearest", inline: "nearest" });
});

function pageIcon(id: WorkspacePageId): Component {
  return PAGE_ICONS[id] ?? Boxes;
}

function focusTab(index: number) {
  const elements = Array.from(tabListRef.value?.querySelectorAll<HTMLElement>("[role='tab']") ?? []);
  elements[Math.max(0, Math.min(index, elements.length - 1))]?.focus();
}

function handleTabKeydown(event: KeyboardEvent, tab: WorkspacePageTab, index: number) {
  if (event.ctrlKey && event.shiftKey && (event.key === "ArrowLeft" || event.key === "ArrowRight")) {
    const targetIndex = event.key === "ArrowLeft" ? index - 1 : index + 2;
    if (targetIndex >= 0 && targetIndex <= props.tabs.length) {
      event.preventDefault();
      emit("move", tab.id, targetIndex);
    }
    return;
  }
  if (event.key === "ArrowLeft" || event.key === "ArrowRight") {
    event.preventDefault();
    const offset = event.key === "ArrowLeft" ? -1 : 1;
    focusTab((index + offset + props.tabs.length) % props.tabs.length);
  } else if (event.key === "Home" || event.key === "End") {
    event.preventDefault();
    focusTab(event.key === "Home" ? 0 : props.tabs.length - 1);
  } else if (event.key === "Enter" || event.key === " ") {
    event.preventDefault();
    emit("activate", tab.id);
  } else if (event.key === "Delete") {
    event.preventDefault();
    emit("close", tab.id, "current");
  }
}

function openContextMenu(event: MouseEvent, id: WorkspacePageId) {
  event.preventDefault();
  pageListOpen.value = false;
  contextMenu.value = {
    id,
    x: Math.min(event.clientX, Math.max(8, window.innerWidth - 184)),
    y: Math.min(event.clientY, Math.max(8, window.innerHeight - 230))
  };
}

function closeFromMenu(mode: WorkspacePageCloseMode) {
  const id = contextMenu.value?.id;
  contextMenu.value = null;
  if (id) emit("close", id, mode);
}

function contextIndex() {
  return props.tabs.findIndex((tab) => tab.id === contextMenu.value?.id);
}

function beginDrag(event: DragEvent, id: WorkspacePageId) {
  draggingId.value = id;
  dropIndex.value = null;
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = "move";
    event.dataTransfer.setData("text/plain", id);
  }
}

function resolveDropIndex(event: DragEvent, index: number): number {
  const target = event.currentTarget as HTMLElement | null;
  if (!target) return index;
  const rect = target.getBoundingClientRect();
  const clientX = Number.isFinite(event.clientX) ? event.clientX : rect.left;
  return clientX < rect.left + rect.width / 2 ? index : index + 1;
}

function previewDrop(event: DragEvent, index: number) {
  if (!draggingId.value) return;
  event.preventDefault();
  if (event.dataTransfer) event.dataTransfer.dropEffect = "move";
  dropIndex.value = resolveDropIndex(event, index);
}

function completeDrop(event: DragEvent, index: number) {
  event.preventDefault();
  const id = draggingId.value;
  const targetIndex = dropIndex.value ?? resolveDropIndex(event, index);
  draggingId.value = null;
  dropIndex.value = null;
  if (id) emit("move", id, targetIndex);
}

function endDrag() {
  draggingId.value = null;
  dropIndex.value = null;
}

function activateFromList(id: WorkspacePageId) {
  pageListOpen.value = false;
  emit("activate", id);
}

function closeAll() {
  const target = props.activeId ?? props.tabs[0]?.id;
  if (target) emit("close", target, "all");
}
</script>

<template>
  <div class="workspace-page-tabbar">
    <div
      ref="tabListRef"
      class="workspace-page-tabbar__scroll"
      role="tablist"
      aria-label="已打开功能页"
    >
      <div
        v-for="(tab, index) in tabs"
        :key="tab.id"
        :class="[
          'workspace-page-tabbar__item',
          { 'is-active': activeId === tab.id, 'is-dragging': draggingId === tab.id },
          { 'has-drop-before': dropIndex === index, 'has-drop-after': dropIndex === tabs.length && index === tabs.length - 1 }
        ]"
        draggable="true"
        @dragstart="beginDrag($event, tab.id)"
        @dragover="previewDrop($event, index)"
        @drop="completeDrop($event, index)"
        @dragend="endDrag"
      >
        <div
          role="tab"
          :aria-label="tab.title"
          :aria-selected="activeId === tab.id"
          :tabindex="activeId === tab.id ? 0 : -1"
          :data-page-id="tab.id"
          class="workspace-page-tabbar__tab"
          @click="emit('activate', tab.id)"
          @keydown="handleTabKeydown($event, tab, index)"
          @contextmenu="openContextMenu($event, tab.id)"
        >
          <component :is="pageIcon(tab.id)" class="workspace-page-tabbar__icon" :stroke-width="1.6" aria-hidden="true" />
          <span class="workspace-page-tabbar__title">{{ tab.title }}</span>
          <button
            type="button"
            class="workspace-page-tabbar__close"
            :aria-label="`关闭 ${tab.title}`"
            :title="`关闭 ${tab.title}`"
            @click.stop="emit('close', tab.id, 'current')"
          >
            <X aria-hidden="true" />
          </button>
        </div>
      </div>
    </div>

    <div class="workspace-page-tabbar__actions">
      <button
        type="button"
        class="workspace-page-tabbar__action"
        aria-label="查看已打开功能页"
        title="查看已打开功能页"
        :aria-expanded="pageListOpen"
        @click="pageListOpen = !pageListOpen; contextMenu = null"
      >
        <List aria-hidden="true" />
      </button>
      <button
        type="button"
        class="workspace-page-tabbar__action"
        aria-label="关闭全部功能页"
        title="关闭全部功能页"
        @click="closeAll"
      >
        <Trash2 aria-hidden="true" />
      </button>
    </div>

    <div v-if="pageListOpen" class="workspace-page-tabbar__popover" role="menu" aria-label="已打开功能页">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        type="button"
        role="menuitem"
        :aria-label="`切换到 ${tab.title}`"
        :class="{ 'is-active': tab.id === activeId }"
        @click="activateFromList(tab.id)"
      >
        <component :is="pageIcon(tab.id)" aria-hidden="true" />
        <span>{{ tab.title }}</span>
      </button>
    </div>

    <template v-if="contextMenu">
      <div class="workspace-page-tabbar__backdrop" @pointerdown="contextMenu = null" />
      <div
        class="workspace-page-tabbar__context"
        role="menu"
        :aria-label="`${workspacePageTab(contextMenu.id).title} Tab 操作`"
        :style="{ left: `${contextMenu.x}px`, top: `${contextMenu.y}px` }"
      >
        <button type="button" role="menuitem" @click="closeFromMenu('current')">关闭当前</button>
        <button type="button" role="menuitem" :disabled="tabs.length <= 1" @click="closeFromMenu('others')">关闭其他</button>
        <button type="button" role="menuitem" :disabled="contextIndex() <= 0" @click="closeFromMenu('left')">关闭左侧</button>
        <button type="button" role="menuitem" :disabled="contextIndex() < 0 || contextIndex() >= tabs.length - 1" @click="closeFromMenu('right')">关闭右侧</button>
        <button type="button" role="menuitem" @click="closeFromMenu('all')">关闭全部</button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.workspace-page-tabbar {
  position: relative;
  z-index: 8;
  display: flex;
  min-width: 0;
  height: 36px;
  flex: 0 0 36px;
  border-bottom: 1px solid var(--ta-shell-border);
  background: var(--ta-shell-surface);
  color: var(--ta-shell-text);
}

.workspace-page-tabbar__scroll {
  display: flex;
  min-width: 0;
  flex: 1;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: thin;
}

.workspace-page-tabbar__item {
  position: relative;
  display: flex;
  min-width: 112px;
  max-width: 220px;
  flex: 0 0 auto;
  border-right: 1px solid var(--ta-shell-border);
  background: var(--ta-shell-header);
}

.workspace-page-tabbar__item::before {
  position: absolute;
  z-index: 2;
  top: 0;
  bottom: 0;
  width: 2px;
  background: var(--ta-shell-accent);
  content: "";
  opacity: 0;
}

.workspace-page-tabbar__item.has-drop-before::before { left: 0; opacity: 1; }
.workspace-page-tabbar__item.has-drop-after::before { right: 0; opacity: 1; }
.workspace-page-tabbar__item.is-dragging { opacity: 0.48; }

.workspace-page-tabbar__tab {
  position: relative;
  display: flex;
  min-width: 0;
  width: 100%;
  align-items: center;
  gap: 7px;
  padding: 2px 6px 0 10px;
  border-top: 2px solid transparent;
  cursor: default;
  outline: none;
}

.workspace-page-tabbar__item:hover .workspace-page-tabbar__tab { background: var(--ta-shell-hover); }
.workspace-page-tabbar__item.is-active .workspace-page-tabbar__tab {
  border-top-color: var(--ta-shell-accent);
  background: var(--ta-shell-surface);
  color: var(--ta-shell-accent-strong);
}
.workspace-page-tabbar__tab:focus-visible { box-shadow: inset 0 0 0 2px var(--ta-shell-accent); }

.workspace-page-tabbar__icon { width: 15px; height: 15px; flex: 0 0 auto; }
.workspace-page-tabbar__title {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  color: inherit;
  font-size: 12px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-page-tabbar__close,
.workspace-page-tabbar__action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--ta-shell-muted);
  cursor: pointer;
}
.workspace-page-tabbar__close { width: 20px; height: 20px; opacity: 0; }
.workspace-page-tabbar__close svg { width: 13px; height: 13px; }
.workspace-page-tabbar__item:hover .workspace-page-tabbar__close,
.workspace-page-tabbar__item.is-active .workspace-page-tabbar__close,
.workspace-page-tabbar__close:focus-visible { opacity: 1; }
.workspace-page-tabbar__close:hover,
.workspace-page-tabbar__close:focus-visible,
.workspace-page-tabbar__action:hover,
.workspace-page-tabbar__action:focus-visible { background: var(--ta-shell-hover); color: var(--ta-shell-text); outline: none; }

.workspace-page-tabbar__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 2px;
  padding: 0 5px;
  border-left: 1px solid var(--ta-shell-border);
  background: var(--ta-shell-surface);
}
.workspace-page-tabbar__action { width: 26px; height: 26px; }
.workspace-page-tabbar__action svg { width: 15px; height: 15px; }

.workspace-page-tabbar__popover,
.workspace-page-tabbar__context {
  position: fixed;
  z-index: 2100;
  display: grid;
  min-width: 180px;
  max-width: 280px;
  padding: 5px;
  border: 1px solid var(--ta-shell-border);
  border-radius: 8px;
  background: var(--ta-shell-surface);
  box-shadow: var(--ta-shell-shadow);
}
.workspace-page-tabbar__popover {
  position: absolute;
  top: calc(100% + 4px);
  right: 6px;
  max-height: min(420px, calc(100vh - 96px));
  overflow-y: auto;
}
.workspace-page-tabbar__popover button,
.workspace-page-tabbar__context button {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  min-height: 30px;
  padding: 5px 9px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--ta-shell-text);
  font: inherit;
  font-size: 12px;
  text-align: left;
  cursor: pointer;
}
.workspace-page-tabbar__popover button:hover,
.workspace-page-tabbar__popover button:focus-visible,
.workspace-page-tabbar__context button:hover:not(:disabled),
.workspace-page-tabbar__context button:focus-visible:not(:disabled),
.workspace-page-tabbar__popover button.is-active { background: var(--ta-shell-accent-soft); color: var(--ta-shell-accent-strong); outline: none; }
.workspace-page-tabbar__popover svg { width: 15px; height: 15px; flex: 0 0 auto; }
.workspace-page-tabbar__popover span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.workspace-page-tabbar__context button:disabled { color: var(--ta-shell-muted); cursor: not-allowed; opacity: 0.5; }
.workspace-page-tabbar__backdrop { position: fixed; z-index: 2099; inset: 0; }
</style>
