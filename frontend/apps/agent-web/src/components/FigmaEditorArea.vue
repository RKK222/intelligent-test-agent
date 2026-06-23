<script setup lang="ts">
import tabCloseUrl from "../assets/figma/tab-close.svg";
import fileIconUrl from "../assets/figma/file-icon.svg";
import type { EditorTab as WorkbenchTab } from "@test-agent/workbench-shell";
import WorkbenchFooter from "./WorkbenchFooter.vue";

type VcsBranch = { name: string; isCurrent?: boolean };

defineProps<{
  tabs: WorkbenchTab[];
  activePath?: string;
  breadcrumbPath?: string;
  /** VCS 分支列表（来自 /vcs/status） */
  branches?: VcsBranch[];
  /** 当前分支名 */
  currentBranch?: string;
  /** 写入路径（编辑器模式显示） */
  writePath?: string;
  /** 最近一次更新时间（秒或 ISO 字符串） */
  updatedAt?: string | number;
  dirty?: boolean;
  readonly?: boolean;
  saving?: boolean;
}>();

const emit = defineEmits<{
  activate: [path: string];
  close: [path: string];
  editorAction: [];
  changeBranch: [branch: string];
  save: [];
}>();
</script>

<template>
  <div class="figma-editor-area">
    <div class="figma-editor-tabs">
      <div
        v-for="tab in tabs"
        :key="tab.path"
        :class="['figma-editor-tab', { 'figma-editor-tab--active': activePath === tab.path }]"
        role="tab"
        :aria-selected="activePath === tab.path"
        @click="emit('activate', tab.path)"
      >
        <div class="figma-editor-tab-inner">
          <img :src="fileIconUrl" alt="file" class="figma-editor-tab-icon" />
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

    <div class="figma-editor-content">
      <slot />
    </div>

    <WorkbenchFooter
      :branch="currentBranch"
      :branches="branches"
      :write-path="writePath"
      :updated-at="updatedAt"
      :dirty="dirty"
      :readonly="readonly"
      :saving="saving"
      :show-branch="false"
      show-save
      @change-branch="(name: string) => emit('changeBranch', name)"
      @save="emit('save')"
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
  border: 1px solid #ddd;
}

/* ---- Tabs ---- */
.figma-editor-tabs {
  display: flex;
  height: 38px;
  flex-shrink: 0;
  overflow-x: auto;
  background: #fafafa;
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

.figma-editor-tab-inner {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 100%;
  padding: 0 10px 0 16px;
}

.figma-editor-tab-icon {
  width: 24px;
  height: 16px;
  opacity: 0.6;
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

/* ---- Content ---- */
.figma-editor-content {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
</style>
