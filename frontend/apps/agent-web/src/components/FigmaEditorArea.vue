<script setup lang="ts">
import tabCloseUrl from "../assets/figma/tab-close.svg";
import { computed, onBeforeUnmount, ref, watch, nextTick } from "vue";
import { FileIcon } from "@test-agent/file-explorer";
import type { EditorTab as WorkbenchTab } from "@test-agent/workbench-shell";
import { languageFromPath } from "@test-agent/editor";
import WorkbenchFooter, { type AppWorkspaceTemplate, type AppWorkspaceVersion, type PreviewMode } from "./WorkbenchFooter.vue";

const props = withDefaults(
  defineProps<{
    tabs: WorkbenchTab[];
    activePath?: string;
    breadcrumbPath?: string;
    /** 写入路径（编辑器模式显示） */
    writePath?: string;
    /** 最近一次更新时间（秒或 ISO 字符串） */
    updatedAt?: string | number;
    dirty?: boolean;
    readonly?: boolean;
    saving?: boolean;
    appName?: string;
    templates?: AppWorkspaceTemplate[];
    selectedVersionId?: string;
    personalWorkspaceBranch?: string;
    loadingTemplates?: boolean;
    loadingVersions?: boolean;
    creatingVersion?: boolean;
    showServerWorkspaceSwitch?: boolean;
    serverWorkspaceSwitchDisabled?: boolean;
    /** Markdown 预览开关（受控），保持向下兼容 */
    markdownPreview?: boolean;
    /** Markdown 预览模式：off | full | split */
    markdownPreviewMode?: PreviewMode;
  }>(),
  { markdownPreview: false, markdownPreviewMode: "off" }
);

const emit = defineEmits<{
  activate: [path: string];
  close: [path: string];
  closeMany: [paths: string[]];
  addFileContext: [path: string];
  editorAction: [];
  save: [];
  "select-version": [payload: { template: AppWorkspaceTemplate; version: AppWorkspaceVersion }];
  "load-versions": [templateId: string];
  "create-version": [payload: { template: AppWorkspaceTemplate; version: string; branch?: string }];
  "open-server-workspace-picker": [];
  "update:markdownPreview": [enabled: boolean];
  "update:markdownPreviewMode": [mode: PreviewMode];
}>();

// 当前激活 tab 是否是 Markdown 文件：是的话才在 tab 表头最右侧显示预览开关。
// 使用与 CodeEditor 完全相同的判定规则（@test-agent/editor 的 languageFromPath），
// 避免出现"按钮可见但点击后编辑器无反应"的不一致。
const activeIsMarkdown = computed(() => {
  if (!props.activePath) return false;
  return languageFromPath(props.activePath) === "markdown";
});

const tabMenu = ref<{ path: string; x: number; y: number } | null>(null);

const tabMenuIndex = computed(() =>
  tabMenu.value ? props.tabs.findIndex((tab) => tab.path === tabMenu.value?.path) : -1
);

const leftTabPaths = computed(() => {
  if (tabMenuIndex.value <= 0) return [];
  return props.tabs.slice(0, tabMenuIndex.value).map((tab) => tab.path);
});

const rightTabPaths = computed(() => {
  if (tabMenuIndex.value < 0) return [];
  return props.tabs.slice(tabMenuIndex.value + 1).map((tab) => tab.path);
});

const allTabPaths = computed(() => props.tabs.map((tab) => tab.path));

function openTabMenu(event: MouseEvent, path: string) {
  event.preventDefault();
  tabMenu.value = { path, x: event.clientX, y: event.clientY };
}

function closeTabMenu() {
  tabMenu.value = null;
}

function emitCloseMany(paths: string[]) {
  if (paths.length === 0) return;
  emit("closeMany", paths);
  closeTabMenu();
}

onBeforeUnmount(() => {
  closeTabMenu();
});

const tabsContainer = ref<HTMLElement | null>(null);

// 监听激活的 tab 路径和 tabs 长度，当新打开了文件或者当前激活的是最后一个 tab 时直接滚到最右侧展示
watch(
  [() => props.tabs.length, () => props.activePath],
  ([newLength, newActivePath], [oldLength, oldActivePath]) => {
    nextTick(() => {
      if (!tabsContainer.value) return;
      const isLastTabActive =
        props.tabs.length > 0 &&
        props.tabs[props.tabs.length - 1].path === props.activePath;
      if (newLength > (oldLength ?? 0) || isLastTabActive) {
        tabsContainer.value.scrollLeft = tabsContainer.value.scrollWidth;
      }
      
      // 焦点聚焦到当前激活的 tab 元素上
      if (props.activePath) {
        const activeTabEl = tabsContainer.value.querySelector(
          ".figma-editor-tab--active"
        ) as HTMLElement | null;
        if (activeTabEl) {
          activeTabEl.focus();
        }
      }
    });
  },
  { immediate: true }
);
</script>

<template>
  <div class="figma-editor-area">
    <div ref="tabsContainer" class="figma-editor-tabs">
      <div
        v-for="tab in tabs"
        :key="tab.path"
        :class="['figma-editor-tab', { 'figma-editor-tab--active': activePath === tab.path }]"
        role="tab"
        :aria-selected="activePath === tab.path"
        tabindex="0"
        @click="emit('activate', tab.path)"
        @contextmenu="openTabMenu($event, tab.path)"
      >
        <div class="figma-editor-tab-inner">
          <FileIcon :entry="{ name: tab.title, path: tab.path, type: 'file' }" class="figma-editor-tab-icon" />
          <span
            v-if="!tab.livePreview && tab.content !== tab.savedContent"
            class="figma-editor-tab-dirty-star"
          >*</span>
          <span class="figma-editor-tab-title">{{ tab.title }}</span>
          <button
            type="button"
            class="figma-editor-tab-close"
            aria-label="关闭标签"
            @click.stop="emit('close', tab.path)"
          >
            <img :src="tabCloseUrl" alt="close" class="figma-icon-14" />
          </button>
        </div>
      </div>
    </div>

    <Teleport to="body">
      <div
        v-if="tabMenu"
        class="figma-editor-tab-menu-backdrop"
        @click="closeTabMenu"
        @contextmenu.prevent="closeTabMenu"
      />
      <div
        v-if="tabMenu"
        class="figma-editor-tab-menu"
        role="menu"
        :style="{ left: `${tabMenu.x}px`, top: `${tabMenu.y}px` }"
      >
        <button
          type="button"
          role="menuitem"
          class="figma-editor-tab-menu-item"
          @click="emit('addFileContext', tabMenu.path); closeTabMenu()"
        >
          添加当前文件到对话
        </button>
        <button
          type="button"
          role="menuitem"
          class="figma-editor-tab-menu-item"
          :disabled="rightTabPaths.length === 0"
          @click="emitCloseMany(rightTabPaths)"
        >
          关闭右侧所有
        </button>
        <button
          type="button"
          role="menuitem"
          class="figma-editor-tab-menu-item"
          :disabled="leftTabPaths.length === 0"
          @click="emitCloseMany(leftTabPaths)"
        >
          关闭左侧所有
        </button>
        <button
          type="button"
          role="menuitem"
          class="figma-editor-tab-menu-item"
          :disabled="allTabPaths.length === 0"
          @click="emitCloseMany(allTabPaths)"
        >
          关闭所有
        </button>
      </div>
    </Teleport>

    <div class="figma-editor-content">
      <slot />
    </div>

    <WorkbenchFooter
      :write-path="writePath"
      :updated-at="updatedAt"
      :dirty="dirty"
      :readonly="readonly"
      :saving="saving"
      :app-name="appName"
      :templates="templates"
      :selected-version-id="selectedVersionId"
      :personal-workspace-branch="personalWorkspaceBranch"
      :loading-templates="loadingTemplates"
      :loading-versions="loadingVersions"
      :creating-version="creatingVersion"
      :show-server-workspace-switch="showServerWorkspaceSwitch"
      :server-workspace-switch-disabled="serverWorkspaceSwitchDisabled"
      :show-preview-button="activeIsMarkdown"
      :markdown-preview-mode="markdownPreviewMode"
      show-save
      @save="emit('save')"
      @update:markdown-preview-mode="(mode) => { emit('update:markdownPreviewMode', mode); emit('update:markdownPreview', mode !== 'off'); }"
      @select-version="(payload) => emit('select-version', payload)"
      @load-versions="(templateId) => emit('load-versions', templateId)"
      @create-version="(payload) => emit('create-version', payload)"
      @open-server-workspace-picker="emit('open-server-workspace-picker')"
    />
  </div>
</template>

<style scoped>
.figma-editor-area {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: #fff;
}

/* ---- Tabs ---- */
.figma-editor-tabs {
  display: flex;
  height: 30px;
  flex-shrink: 0;
  overflow-x: auto;
  overflow-y: hidden !important;
  background: #fafafa;
  scrollbar-width: none;
}

.figma-editor-tabs::-webkit-scrollbar {
  height: 3px;
}

.figma-editor-tabs::-webkit-scrollbar-thumb {
  background: transparent;
  border-radius: 3px;
}

.figma-editor-tabs::-webkit-scrollbar-track {
  background: transparent;
}

.figma-editor-tabs:hover {
  scrollbar-width: thin;
  scrollbar-color: rgba(0, 0, 0, 0.2) transparent;
}

.figma-editor-tabs:hover::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.2);
}

.figma-editor-tabs:hover::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.4);
}

.figma-editor-tab {
  display: flex;
  align-items: center;
  height: 100%;
  flex-shrink: 0;
  cursor: pointer;
  background: #eaeaea;
  border-top: 2px solid transparent;
  border-right: 1px solid rgba(0, 0, 0, 0.08);
}

.figma-editor-tab--active {
  background: #fff;
  border-top-color: #555;
  border-top-width: 2px;
}

.figma-editor-tab:focus {
  outline: none;
}

.figma-editor-tab:focus-visible {
  outline: none;
  box-shadow: inset 0 0 0 1.5px rgba(29, 63, 176, 0.4);
}

.figma-editor-tab-inner {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 100%;
  padding: 0 10px 0 16px;
}

.figma-editor-tab-icon {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.figma-editor-tab-dirty-star {
  color: #f97316; /* 橙色 */
  font-weight: bold;
  font-size: 14px;
  margin-right: -2px; /* 拉近与标题的距离 */
  margin-left: -2px; /* 拉近与图标的距离 */
  line-height: 1;
}

.figma-editor-tab-title {
  font-family: Inter, sans-serif;
  font-size: 14px;
  font-weight: 400;
  line-height: 20px;
  letter-spacing: -0.0107em;
  color: #666;
  white-space: nowrap;
}

.figma-editor-tab--active .figma-editor-tab-title {
  color: #111;
}

.figma-editor-tab-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: none;
  border-radius: 4px;
  background: transparent;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.12s ease, background-color 0.12s ease;
}

.figma-editor-tab:hover .figma-editor-tab-close,
.figma-editor-tab--active .figma-editor-tab-close {
  opacity: 1;
}

.figma-editor-tab-close:hover {
  background: #ddd;
}

.figma-icon-14 {
  width: 14px;
  height: 14px;
}



.figma-editor-tab-menu-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1200;
  background: transparent;
}

.figma-editor-tab-menu {
  position: fixed;
  z-index: 1201;
  min-width: 148px;
  padding: 4px;
  border: 1px solid #d8d8d8;
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.16);
}

.figma-editor-tab-menu-item {
  display: flex;
  width: 100%;
  align-items: center;
  height: 28px;
  padding: 0 10px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: #242424;
  cursor: pointer;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: 12px;
  text-align: left;
}

.figma-editor-tab-menu-item:hover:not(:disabled) {
  background: #f1f1f1;
}

.figma-editor-tab-menu-item:disabled {
  color: #a3a3a3;
  cursor: not-allowed;
}

/* ---- Content ---- */
.figma-editor-content {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
</style>
