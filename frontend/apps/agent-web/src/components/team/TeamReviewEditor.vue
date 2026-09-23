<script setup lang="ts">
import { computed } from "vue";
import { CodeEditor } from "@test-agent/editor";
import { DiffViewer } from "@test-agent/diff-viewer";
import type { RunDiffFile } from "@test-agent/shared-types";
import { formatPreviewBytes } from "../fileProgressivePreview";
import { useTeamManagementView } from "./useTeamManagementView";

const { controller, state } = useTeamManagementView();
const activeTab = computed(() => state.value.tabs.find((tab) => tab.id === state.value.activeTabId) ?? null);
const diffFiles = computed<RunDiffFile[]>(() => {
  const tab = activeTab.value;
  if (!tab || tab.kind !== "diff") return [];
  return [{
    path: tab.path,
    patch: tab.patch,
    additions: tab.additions,
    deletions: tab.deletions,
    status: tab.status || "modified"
  }];
});
</script>

<template>
  <section class="team-editor" aria-label="管理视角只读编辑区">
    <div v-if="state.tabs.length" class="team-editor-tabs" role="tablist" aria-label="管理视角文件">
      <div
        v-for="tab in state.tabs"
        :key="tab.id"
        :class="['team-editor-tab', tab.id === state.activeTabId && 'is-active']"
      >
        <button type="button" role="tab" :aria-selected="tab.id === state.activeTabId" @click="controller.activateTab(tab.id)">
          {{ tab.title }}
        </button>
        <button type="button" :aria-label="`关闭 ${tab.title}`" @click="controller.closeTab(tab.id)">×</button>
      </div>
    </div>
    <div v-if="!activeTab" class="team-editor-empty">
      <p>选择左侧文件或变更，或从团队审阅打开提交。</p>
    </div>
    <div v-else-if="activeTab.loadState === 'error'" class="team-editor-body">
      <p class="team-editor-error" role="alert">{{ activeTab.errorMessage || "内容无法读取" }}</p>
      <button type="button" @click="controller.retryTab(activeTab.id)">重试</button>
    </div>
    <div v-else-if="activeTab.kind === 'diff'" class="team-editor-body">
      <p v-if="activeTab.loadState === 'loading'" class="team-editor-status">正在读取差异…</p>
      <DiffViewer
        review-mode="team"
        :files="diffFiles"
        :selected-path="activeTab.path"
        :writable="false"
      />
    </div>
    <div v-else class="team-editor-body">
      <div v-if="activeTab.progressive" class="team-editor-preview" role="status">
        <span>已加载 {{ formatPreviewBytes(activeTab.progressive.loadedBytes) }} / {{ formatPreviewBytes(activeTab.progressive.size) }}。继续加载可能占用较多内存。</span>
        <span v-if="activeTab.errorMessage">{{ activeTab.errorMessage }}</span>
        <button
          v-if="activeTab.errorMessage"
          type="button"
          @click="controller.loadMorePreview(activeTab.id, false)"
        >重试</button>
        <button
          v-if="!activeTab.progressive.eof"
          type="button"
          :disabled="activeTab.progressive.loading"
          @click="controller.loadMorePreview(activeTab.id, false)"
        >继续加载一段</button>
        <button
          v-if="!activeTab.progressive.eof"
          type="button"
          :disabled="activeTab.progressive.loading"
          @click="controller.loadMorePreview(activeTab.id, true)"
        >加载全部（可能卡顿）</button>
      </div>
      <p v-if="activeTab.loadState === 'loading'" class="team-editor-status">正在读取文件…</p>
      <CodeEditor
        :path="activeTab.path"
        :content="activeTab.content"
        readonly
        :progressive-append="Boolean(activeTab.progressive)"
      />
    </div>
  </section>
</template>

<style scoped>
.team-editor {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: #fff;
}
.team-editor-tabs {
  display: flex;
  flex: none;
  gap: 0;
  overflow-x: auto;
  border-bottom: 1px solid var(--ta-shell-border, #e5e7eb);
  background: #fff;
}
.team-editor-tab {
  display: flex;
  align-items: stretch;
  border-right: 1px solid var(--ta-shell-border, #e5e7eb);
  color: var(--ta-shell-text, #111);
}
.team-editor-tab.is-active {
  box-shadow: inset 0 2px 0 var(--ta-shell-accent, #c8161d);
}
.team-editor-tab button {
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
}
.team-editor-tab button[role="tab"] {
  max-width: 220px;
  padding: 8px 10px;
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.team-editor-tab button:focus-visible {
  outline: 2px solid var(--ta-shell-accent, #c8161d);
  outline-offset: -2px;
}
.team-editor-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}
.team-editor-empty,
.team-editor-status,
.team-editor-error {
  margin: 0;
  padding: 28px 20px;
  color: var(--ta-shell-muted, #6b7280);
  font-size: 14px;
}
.team-editor-error {
  color: var(--ta-shell-accent, #c8161d);
}
.team-editor-body > button {
  align-self: flex-start;
  margin: 0 20px 16px;
  border: 1px solid var(--ta-shell-border-strong, #d1d5db);
  border-radius: 6px;
  background: #fff;
  color: var(--ta-shell-text, #111);
  padding: 4px 8px;
  cursor: pointer;
}
.team-editor-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding: 8px 12px;
  border-bottom: 1px solid var(--ta-shell-border, #e5e7eb);
  color: var(--ta-shell-text, #111);
  font-size: 13px;
}
.team-editor-preview button {
  border: 1px solid var(--ta-shell-border-strong, #d1d5db);
  border-radius: 6px;
  background: #fff;
  padding: 4px 8px;
  cursor: pointer;
}
.team-editor-preview button:focus-visible {
  outline: 2px solid var(--ta-shell-accent, #c8161d);
}
</style>
