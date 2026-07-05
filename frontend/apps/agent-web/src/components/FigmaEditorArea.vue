<script setup lang="ts">
import tabCloseUrl from "../assets/figma/tab-close.svg";
import fileIconUrl from "../assets/figma/file-icon.svg";
import { computed } from "vue";
import { Eye, EyeOff } from "lucide-vue-next";
import type { EditorTab as WorkbenchTab } from "@test-agent/workbench-shell";
import { languageFromPath } from "@test-agent/editor";
import WorkbenchFooter, { type AppWorkspaceTemplate, type AppWorkspaceVersion } from "./WorkbenchFooter.vue";

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
    /** Markdown 预览开关（受控），由父级双向绑定到 CodeEditor 的 showPreview。 */
    markdownPreview?: boolean;
  }>(),
  { markdownPreview: false }
);

const emit = defineEmits<{
  activate: [path: string];
  close: [path: string];
  editorAction: [];
  save: [];
  "select-version": [payload: { template: AppWorkspaceTemplate; version: AppWorkspaceVersion }];
  "load-versions": [templateId: string];
  "create-version": [payload: { template: AppWorkspaceTemplate; version: string; branch?: string }];
  "open-server-workspace-picker": [];
  "update:markdownPreview": [enabled: boolean];
}>();

// 当前激活 tab 是否是 Markdown 文件：是的话才在 tab 表头最右侧显示预览开关。
// 使用与 CodeEditor 完全相同的判定规则（@test-agent/editor 的 languageFromPath），
// 避免出现"按钮可见但点击后编辑器无反应"的不一致。
const activeIsMarkdown = computed(() => {
  if (!props.activePath) return false;
  return languageFromPath(props.activePath) === "markdown";
});

function toggleMarkdownPreview() {
  if (!activeIsMarkdown.value) return;
  emit("update:markdownPreview", !props.markdownPreview);
}
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
      <!--
        Markdown 预览开关：放在 tab 行最右侧（margin-left: auto 推右）。
        只在当前激活 tab 是 Markdown 文件时显示，避免其他文件类型出现无效按钮。
        状态完全受控于父级：与 CodeEditor 的 showPreview 双向绑定。
      -->
      <button
        v-if="activeIsMarkdown"
        type="button"
        :class="['figma-editor-tab-preview', { 'is-active': markdownPreview }]"
        :aria-label="markdownPreview ? '关闭 Markdown 预览' : '打开 Markdown 预览'"
        :title="markdownPreview ? '关闭预览' : '预览'"
        :aria-pressed="markdownPreview"
        data-testid="editor-tab-markdown-preview"
        @click.stop="toggleMarkdownPreview"
      >
        <component :is="markdownPreview ? EyeOff : Eye" :size="14" />
        <span>{{ markdownPreview ? "关闭预览" : "预览" }}</span>
      </button>
    </div>

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
      show-save
      @save="emit('save')"
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

/* Markdown 预览开关按钮：放在 tab 行最右侧，视觉上与 tab 区分（无下边线、紧凑按钮）。
   active 态用与活动 tab 接近的高亮色，方便用户感知"预览已开启"。*/
.figma-editor-tab-preview {
  margin-left: auto;
  align-self: center;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 8px;
  margin-right: 8px;
  border: 0.8px solid #dfdfdf;
  border-radius: 6px;
  background: #fff;
  color: #555;
  font-size: 11px;
  font-weight: 500;
  line-height: 1;
  cursor: pointer;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  transition: background-color 0.12s ease, border-color 0.12s ease, color 0.12s ease;
}

.figma-editor-tab-preview:hover {
  background: #f0f0f0;
  border-color: #cfcfcf;
  color: #333;
}

.figma-editor-tab-preview.is-active {
  background: #eaf0ff;
  border-color: #b9c8ff;
  color: #1d3fb0;
}

.figma-editor-tab-preview.is-active:hover {
  background: #dde7ff;
}

/* ---- Content ---- */
.figma-editor-content {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
</style>
