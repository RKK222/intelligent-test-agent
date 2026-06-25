<script setup lang="ts">
import { computed, onUnmounted, ref } from "vue";
import { ChevronDown, LogOut, ShieldCheck, UserRound } from "lucide-vue-next";
import logoUrl from "../assets/figma/logo.svg";
import panelCloseUrl from "../assets/figma/panel-close.svg";
import folderIconUrl from "../assets/figma/folder-icon.svg";

export type AppItem = {
  id: string;
  name: string;
  description?: string;
  icon?: string;
};

const props = withDefaults(
  defineProps<{
    workspaceName?: string;
    bottomOpen?: boolean;
    apps?: AppItem[];
    selectedAppId?: string;
    currentUserName?: string;
    currentUserRoleLabels?: string[];
    showRightPanel?: boolean;
  }>(),
  {
    apps: () => [
      { id: "fgcms-psn", name: "F-GCMS-PSN", description: "色谱质谱联用平台" },
      { id: "lcs-test", name: "LCS-Test", description: "液相色谱测试套件" },
      { id: "gcms-2024", name: "GCMS-2024", description: "气相色谱质谱年度测试" },
      { id: "ms-runner", name: "MS-Runner", description: "质谱批量回归任务" }
    ],
    selectedAppId: "fgcms-psn",
    showRightPanel: true
  }
);

const leftPanelOpen = ref(true);
const leftPanelWidth = ref(262);
const rightPanelWidth = ref(320);
const resizing = ref<"left" | "right" | null>(null);
let resizeStartX = 0;
let resizeStartWidth = 0;

const MIN_LEFT_WIDTH = 200;
const MAX_LEFT_WIDTH = 600;
const MIN_RIGHT_WIDTH = 240;
const MAX_RIGHT_WIDTH = 1200;

const emit = defineEmits<{
  (e: "toggle-left-panel"): void;
  (e: "toggle-right-panel"): void;
  (e: "open-folder"): void;
  (e: "select-app", appId: string): void;
  (e: "logout"): void;
}>();

const appMenuOpen = ref(false);
const userMenuOpen = ref(false);

function toggleLeftPanel() {
  leftPanelOpen.value = !leftPanelOpen.value;
  emit("toggle-left-panel");
}

function toggleRightPanel() {
  emit("toggle-right-panel");
}

function toggleAppMenu() {
  appMenuOpen.value = !appMenuOpen.value;
  userMenuOpen.value = false;
}

function closeAppMenu() {
  appMenuOpen.value = false;
}

function toggleUserMenu() {
  userMenuOpen.value = !userMenuOpen.value;
  appMenuOpen.value = false;
}

function closeUserMenu() {
  userMenuOpen.value = false;
}

function closeHeaderMenus() {
  closeAppMenu();
  closeUserMenu();
}

function logout() {
  closeUserMenu();
  emit("logout");
}

function selectApp(app: AppItem) {
  emit("select-app", app.id);
  closeAppMenu();
}

const selectedApp = computed(
  () => props.apps.find((a) => a.id === props.selectedAppId) ?? props.apps[0] ?? { id: "", name: "未选择应用" }
);
const userName = computed(() => props.currentUserName?.trim() || "未登录");
// 右上角用户菜单顶部的「角色」灰显行：来自后端 /api/auth/me 的 roleLabels（dictionaries.dict_label）。
// 多个角色用「、」拼接；roleLabels 为空或缺失时整行不渲染，避免在未登录或字典缺失时出现 "角色：" 空文案。
const userRoleText = computed(() => {
  const labels = props.currentUserRoleLabels?.filter((label) => !!label && label.trim().length > 0) ?? [];
  if (labels.length === 0) {
    return "";
  }
  return labels.join("、");
});
const userInitial = computed(() => {
  const first = userName.value.trim().charAt(0);
  return first ? first.toUpperCase() : "?";
});

function onAppMenuBlur(event: FocusEvent) {
  const next = event.relatedTarget as Node | null;
  if (next && (event.currentTarget as Node).contains(next)) return;
  setTimeout(closeAppMenu, 120);
}

function onUserMenuBlur(event: FocusEvent) {
  const next = event.relatedTarget as Node | null;
  if (next && (event.currentTarget as Node).contains(next)) return;
  setTimeout(closeUserMenu, 120);
}

function onResizeStart(side: "left" | "right", event: MouseEvent) {
  resizing.value = side;
  resizeStartX = event.clientX;
  resizeStartWidth = side === "left" ? leftPanelWidth.value : rightPanelWidth.value;
  document.addEventListener("mousemove", onResizeMove);
  document.addEventListener("mouseup", onResizeEnd);
  document.body.style.cursor = "col-resize";
  document.body.style.userSelect = "none";
}

function onResizeMove(event: MouseEvent) {
  if (!resizing.value) return;
  const delta = event.clientX - resizeStartX;
  // 左侧把手：向右拖 → 左侧更宽；右侧把手：向左拖 → 右侧更宽。
  if (resizing.value === "left") {
    const nextWidth = Math.min(MAX_LEFT_WIDTH, Math.max(MIN_LEFT_WIDTH, resizeStartWidth + delta));
    leftPanelWidth.value = nextWidth;
  } else {
    const nextWidth = Math.min(MAX_RIGHT_WIDTH, Math.max(MIN_RIGHT_WIDTH, resizeStartWidth - delta));
    rightPanelWidth.value = nextWidth;
  }
}

function onResizeEnd() {
  resizing.value = null;
  document.removeEventListener("mousemove", onResizeMove);
  document.removeEventListener("mouseup", onResizeEnd);
  document.body.style.cursor = "";
  document.body.style.userSelect = "";
}

onUnmounted(() => {
  document.removeEventListener("mousemove", onResizeMove);
  document.removeEventListener("mouseup", onResizeEnd);
  document.body.style.cursor = "";
  document.body.style.userSelect = "";
});
</script>

<template>
  <div class="figma-app" @click="closeHeaderMenus">
    <header class="figma-header">
      <div class="figma-header-left">
        <div class="figma-sidebar-toggle">
          <button
            type="button"
            :class="['figma-icon-btn figma-icon-btn-ghost', !leftPanelOpen && 'figma-icon-btn-ghost--collapsed']"
            aria-label="切换侧边栏"
            @click.stop="toggleLeftPanel"
          >
            <img :src="panelCloseUrl" alt="toggle panel" class="figma-icon-16" />
          </button>
        </div>
        <div class="figma-logo-group">
          <img :src="logoUrl" alt="logo" class="figma-logo" />
          <div class="figma-logo-margin" />
          <span class="figma-title">MIMO测试智能体</span>
        </div>
      </div>
      <div class="figma-header-right">
        <button type="button" class="figma-icon-btn figma-icon-btn-secondary" aria-label="打开文件夹" @click="emit('open-folder')">
          <img :src="folderIconUrl" alt="folder" class="figma-icon-16" />
        </button>
        <div class="figma-app-menu-wrapper" @click.stop>
          <button
            type="button"
            :class="['figma-app-menu-trigger', appMenuOpen && 'is-open']"
            aria-haspopup="listbox"
            :aria-expanded="appMenuOpen"
            @click="toggleAppMenu"
            @blur="onAppMenuBlur"
          >
            <span class="figma-app-menu-name">{{ selectedApp?.name || "F-GCMS-PSN" }}</span>
            <ChevronDown class="figma-app-menu-chevron" :class="{ 'is-open': appMenuOpen }" />
          </button>
          <ul v-if="appMenuOpen" class="figma-app-menu-dropdown" role="listbox">
            <li
              v-for="app in apps"
              :key="app.id"
              :class="['figma-app-menu-item', app.id === selectedApp?.id && 'is-active']"
              role="option"
              :aria-selected="app.id === selectedApp?.id"
              tabindex="0"
              @mousedown.prevent="selectApp(app)"
            >
              <div class="figma-app-menu-item-main">
                <span class="figma-app-menu-item-name">{{ app.name }}</span>
                <span v-if="app.description" class="figma-app-menu-item-desc">{{ app.description }}</span>
              </div>
              <span v-if="app.id === selectedApp?.id" class="figma-app-menu-item-check">✓</span>
            </li>
          </ul>
        </div>
        <div class="figma-user-menu-wrapper" @click.stop @blur="onUserMenuBlur">
          <button
            type="button"
            class="figma-user-avatar-btn"
            :class="{ 'is-open': userMenuOpen }"
            :aria-label="`当前用户 ${userName}`"
            aria-haspopup="menu"
            :aria-expanded="userMenuOpen"
            @click="toggleUserMenu"
          >
            <span class="figma-user-avatar">{{ userInitial }}</span>
          </button>
          <div v-if="userMenuOpen" class="figma-user-menu-dropdown" role="menu">
            <div v-if="userRoleText" class="figma-user-menu-role" role="presentation" aria-label="当前用户角色">
              <ShieldCheck class="figma-user-menu-icon" />
              <span class="figma-user-menu-role-text" :title="userRoleText">{{ userRoleText }}</span>
            </div>
            <div class="figma-user-menu-summary">
              <UserRound class="figma-user-menu-icon" />
              <span class="figma-user-menu-name">{{ userName }}</span>
            </div>
            <button type="button" class="figma-user-menu-item" role="menuitem" @mousedown.prevent="logout">
              <LogOut class="figma-user-menu-icon" />
              <span>退出登录</span>
            </button>
          </div>
        </div>
      </div>
    </header>

    <div class="figma-body">
      <aside class="figma-activity-bar">
        <slot name="activity" />
      </aside>

      <div class="figma-panel-group">
        <div v-if="leftPanelOpen" class="figma-panel-left" :style="{ width: `${leftPanelWidth}px` }">
          <slot name="files" />
        </div>
        <div
          v-if="leftPanelOpen"
          class="figma-files-resize-handle"
          @mousedown="onResizeStart('left', $event)"
          aria-label="拖拽调整工作目录宽度"
          role="separator"
          aria-orientation="vertical"
        />
        <div class="figma-panel-center">
          <slot name="editor" />
        </div>
        <div v-if="showRightPanel" class="figma-chat-panel-wrapper">
          <div
            class="figma-chat-resize-handle"
            @mousedown="onResizeStart('right', $event)"
            aria-label="拖拽调整对话窗口宽度"
            role="separator"
            aria-orientation="vertical"
          />
          <div class="figma-panel-right" :style="{ width: `${rightPanelWidth}px` }">
            <div class="figma-chat-body">
              <slot name="chat" />
            </div>
          </div>
        </div>
      </div>

      <div class="figma-bottom-drawer" :class="{ 'figma-bottom-drawer--open': bottomOpen }" role="region" aria-label="运行与终端">
        <slot name="bottom" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.figma-app {
  display: grid;
  grid-template-rows: 52px 1fr;
  grid-template-columns: minmax(0, 1fr);
  width: 100%;
  height: 100vh;
  background: #f5f5f5;
  overflow: hidden;
}

/* ---- Header ---- */
.figma-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 52px;
  background: #fff;
  border-bottom: 1px solid #ddd;
  padding: 0 5px;
  flex-shrink: 0;
  z-index: 30;
  position: relative;
}

.figma-header-left {
  display: flex;
  align-items: center;
  gap: 0;
  height: 100%;
}

.figma-sidebar-toggle {
  display: flex;
  align-items: center;
  padding: 0 14px 0 5px;
}

.figma-logo-group {
  display: flex;
  align-items: center;
  padding-left: 0;
}

.figma-logo {
  width: 32px;
  height: 24.8px;
  flex-shrink: 0;
}

.figma-logo-margin {
  width: 8px;
  flex-shrink: 0;
}

.figma-title {
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  font-weight: 600;
  font-size: 14px;
  line-height: 20px;
  letter-spacing: 0.0143em;
  color: #333;
  white-space: nowrap;
}

/* ---- Header Right ---- */
.figma-header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* ---- App Dropdown ---- */
.figma-app-menu-wrapper {
  position: relative;
}

.figma-app-menu-trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 8px;
  border: 0.8px solid transparent;
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  transition: background-color 0.12s ease, border-color 0.12s ease;
}

.figma-app-menu-trigger:hover,
.figma-app-menu-trigger.is-open {
  background: #f0f0f0;
  border-color: #dfdfdf;
}

.figma-app-menu-name {
  font-weight: 600;
  font-size: 13px;
  line-height: 20px;
  letter-spacing: 0.0154em;
  color: #18181b;
}

.figma-app-menu-chevron {
  width: 10px;
  height: 10px;
  color: #565656;
  transition: transform 0.16s ease;
}

.figma-app-menu-chevron.is-open {
  transform: rotate(180deg);
}

.figma-app-menu-dropdown {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  min-width: 240px;
  background: #fff;
  border: 1px solid #e4e4e7;
  border-radius: 8px;
  padding: 4px;
  margin: 0;
  list-style: none;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  z-index: 40;
}

.figma-app-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.1s ease;
  outline: none;
}

.figma-app-menu-item:hover,
.figma-app-menu-item:focus {
  background: #f4f4f5;
}

.figma-app-menu-item.is-active {
  background: #fafafa;
}

.figma-app-menu-item-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.figma-app-menu-item-name {
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: 13px;
  font-weight: 500;
  line-height: 18px;
  color: #18181b;
}

.figma-app-menu-item-desc {
  font-size: 11px;
  line-height: 14px;
  color: #999;
}

.figma-app-menu-item-check {
  color: #18a978;
  font-size: 14px;
  font-weight: 600;
}

/* ---- User Menu ---- */
.figma-user-menu-wrapper {
  position: relative;
}

.figma-user-avatar-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  cursor: pointer;
  transition: background-color 0.14s ease, border-color 0.14s ease;
}

.figma-user-avatar-btn:hover,
.figma-user-avatar-btn.is-open {
  background: #f0f0f0;
  border-color: #dfdfdf;
}

.figma-user-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: #18181b;
  color: #fff;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
}

.figma-user-menu-dropdown {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  min-width: 168px;
  background: #fff;
  border: 1px solid #e4e4e7;
  border-radius: 8px;
  padding: 4px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  z-index: 40;
}

.figma-user-menu-summary,
.figma-user-menu-item,
.figma-user-menu-role {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 34px;
  padding: 7px 9px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #18181b;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: 13px;
  line-height: 18px;
  text-align: left;
}

/* 顶部「角色」灰显行：与 summary 共用一行的视觉重量，但用次要色 + 更小字号
   暗示它不是可点击项；roleLabels 为空时整行 v-if 不渲染。 */
.figma-user-menu-role {
  color: #9ca3af;
  font-size: 12px;
  cursor: default;
  border-bottom: 1px solid #f0f0f0;
  border-radius: 6px 6px 0 0;
}

.figma-user-menu-role .figma-user-menu-icon {
  color: #b8b8b8;
}

.figma-user-menu-role-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.figma-user-menu-summary {
  color: #666;
  border-bottom: 1px solid #f0f0f0;
  border-radius: 6px 6px 0 0;
}

.figma-user-menu-item {
  margin-top: 4px;
  cursor: pointer;
}

.figma-user-menu-item:hover,
.figma-user-menu-item:focus-visible {
  background: #f4f4f5;
  outline: none;
}

.figma-user-menu-icon {
  width: 14px;
  height: 14px;
  color: #666;
  flex-shrink: 0;
}

.figma-user-menu-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---- Icon Buttons ---- */
.figma-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  border: none;
  cursor: pointer;
  transition: background-color 0.14s ease;
}

.figma-icon-btn-ghost {
  width: 28px;
  height: 28px;
  background: #f9f9f9;
  border: 0.8px solid #dfdfdf;
}

.figma-icon-btn-ghost:hover {
  background: #eee;
}

.figma-icon-btn-ghost--collapsed {
  background: #e8e8e8;
}

.figma-icon-btn-secondary {
  width: 28px;
  height: 28px;
  background: #f4f4f5;
}

.figma-icon-btn-secondary:hover {
  background: #e8e8e8;
}

.figma-icon-16 {
  width: 16px;
  height: 16px;
}

/* ---- Body ---- */
.figma-body {
  display: flex;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
  position: relative;
}

.figma-activity-bar {
  width: 48px;
  flex-shrink: 0;
  background: #fff;
  border-right: 1px solid #ddd;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.figma-panel-group {
  display: flex;
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.figma-panel-left {
  /* 宽度由 :style="width: ${leftPanelWidth}px" 动态控制 */
  flex-shrink: 0;
  background: #fafafa;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.figma-files-resize-handle {
  width: 4px;
  flex-shrink: 0;
  cursor: col-resize;
  position: relative;
  z-index: 5;
  background: transparent;
  transition: background-color 0.14s ease;
}

.figma-files-resize-handle::after {
  content: "";
  position: absolute;
  top: 0;
  bottom: 0;
  left: 50%;
  width: 1px;
  margin-left: -0.5px;
  background: #e4e4e7;
  transition: background-color 0.14s ease;
}

.figma-files-resize-handle:hover {
  background: rgba(0, 0, 0, 0.04);
}

.figma-files-resize-handle:hover::after {
  background: #bbb;
}

.figma-files-resize-handle:active {
  background: rgba(0, 0, 0, 0.06);
}

.figma-panel-center {
  flex: 1;
  min-width: 100px;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ---- Right Chat Panel ---- */
.figma-chat-panel-wrapper {
  flex-shrink: 0;
  display: flex;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}

.figma-panel-right {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}

.figma-chat-resize-handle {
  width: 4px;
  flex-shrink: 0;
  cursor: col-resize;
  position: relative;
  z-index: 5;
  background: transparent;
  transition: background-color 0.14s ease;
}

.figma-chat-resize-handle::after {
  content: "";
  position: absolute;
  top: 0;
  bottom: 0;
  left: 50%;
  width: 1px;
  margin-left: -0.5px;
  background: #e4e4e7;
  transition: background-color 0.14s ease;
}

.figma-chat-resize-handle:hover {
  background: rgba(0, 0, 0, 0.04);
}

.figma-chat-resize-handle:hover::after {
  background: #bbb;
}

.figma-chat-resize-handle:active {
  background: rgba(0, 0, 0, 0.06);
}

.figma-chat-body {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
}

/* ---- Bottom Drawer ---- */
.figma-bottom-drawer {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 20;
  height: 0;
  overflow: hidden;
  transition: height 0.2s ease;
  border-top: 1px solid #ddd;
  background: #f5f5f5;
  box-shadow: 0 -12px 28px rgba(17, 24, 39, 0.08);
}

.figma-bottom-drawer--open {
  height: 190px;
}

/* ---- Activity Bar (used by slot content) ---- */
:deep(.figma-activity-nav) {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: space-between;
  height: 100%;
  width: 100%;
  padding: 8px 0;
}

:deep(.figma-activity-top),
:deep(.figma-activity-bottom) {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

:deep(.figma-activity-btn) {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border: none;
  border-radius: 12px;
  background: transparent;
  color: #444;
  cursor: pointer;
  transition: background-color 0.14s ease, color 0.14s ease;
}

:deep(.figma-activity-btn:hover) {
  background: #e8e8e8;
  color: #333;
}

:deep(.figma-activity-btn--active) {
  color: #333;
}

:deep(.figma-activity-btn--active::before) {
  content: "";
  position: absolute;
  left: 0;
  top: 7px;
  width: 2px;
  height: 24px;
  border-radius: 0 999px 999px 0;
  background: #333;
}

:deep(.figma-activity-icon) {
  width: 20px;
  height: 20px;
}
</style>
