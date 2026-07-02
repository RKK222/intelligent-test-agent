<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from "vue";
import type * as monaco from "monaco-editor";
import type {
  WorkspaceGitConflict,
  WorkspaceGitConflictResolution
} from "@test-agent/shared-types";
import { AlertTriangle, Check, GitMerge, RotateCcw, Trash2 } from "lucide-vue-next";
import { Button } from "@test-agent/ui-kit";

const props = defineProps<{
  conflict: WorkspaceGitConflict;
  resolving?: boolean;
}>();

const emit = defineEmits<{
  resolve: [payload: { resolution: WorkspaceGitConflictResolution; content?: string | null }];
  abort: [];
  close: [];
}>();

const currentEl = ref<HTMLElement | null>(null);
const incomingEl = ref<HTMLElement | null>(null);
const resultEl = ref<HTMLElement | null>(null);
const currentEditor = shallowRef<monaco.editor.IStandaloneCodeEditor | null>(null);
const incomingEditor = shallowRef<monaco.editor.IStandaloneCodeEditor | null>(null);
const resultEditor = shallowRef<monaco.editor.IStandaloneCodeEditor | null>(null);
let monacoLib: typeof monaco | null = null;
let currentModel: monaco.editor.ITextModel | null = null;
let incomingModel: monaco.editor.ITextModel | null = null;
let resultModel: monaco.editor.ITextModel | null = null;

const resultDeleted = ref(false);
const resultTouched = ref(false);
const conflictMarkersRemain = computed(() => {
  const content = resultModel?.getValue() ?? props.conflict.resultContent ?? "";
  return /^(<<<<<<<|=======|>>>>>>>)(?: .*)?$/m.test(content);
});

function languageFromPath(path: string) {
  const extension = path.split(".").pop()?.toLowerCase();
  if (extension === "java") return "java";
  if (extension === "py") return "python";
  if (extension === "json") return "json";
  if (extension === "md") return "markdown";
  if (extension === "html") return "html";
  if (extension === "css") return "css";
  return "typescript";
}

function editorOptions(readOnly: boolean): monaco.editor.IStandaloneEditorConstructionOptions {
  return {
    theme: "ta-merge-light",
    readOnly,
    minimap: { enabled: false },
    automaticLayout: true,
    scrollBeyondLastLine: false,
    fontSize: 13,
    lineHeight: 20,
    wordWrap: "off",
    scrollbar: {
      verticalScrollbarSize: 4,
      horizontalScrollbarSize: 4,
      useShadows: false
    }
  };
}

async function initializeEditors() {
  if (!currentEl.value || !incomingEl.value || !resultEl.value || currentEditor.value) return;
  const module = await import("./monaco-env");
  monacoLib = await module.loadMonaco();
  monacoLib.editor.defineTheme("ta-merge-light", {
    base: "vs",
    inherit: true,
    rules: [],
    colors: {
      "editor.background": "#ffffff",
      "editor.lineHighlightBackground": "#f4f4f5",
      "editorLineNumber.foreground": "#a1a1aa"
    }
  });
  const language = languageFromPath(props.conflict.path);
  currentModel = monacoLib.editor.createModel(props.conflict.currentContent ?? "", language);
  incomingModel = monacoLib.editor.createModel(props.conflict.incomingContent ?? "", language);
  resultModel = monacoLib.editor.createModel(props.conflict.resultContent ?? "", language);
  currentEditor.value = monacoLib.editor.create(currentEl.value, editorOptions(true));
  incomingEditor.value = monacoLib.editor.create(incomingEl.value, editorOptions(true));
  resultEditor.value = monacoLib.editor.create(resultEl.value, editorOptions(false));
  currentEditor.value.setModel(currentModel);
  incomingEditor.value.setModel(incomingModel);
  resultEditor.value.setModel(resultModel);
  resultModel.onDidChangeContent(() => {
    resultTouched.value = true;
    resultDeleted.value = false;
  });
}

function disposeEditors() {
  currentEditor.value?.dispose();
  incomingEditor.value?.dispose();
  resultEditor.value?.dispose();
  currentModel?.dispose();
  incomingModel?.dispose();
  resultModel?.dispose();
  currentEditor.value = null;
  incomingEditor.value = null;
  resultEditor.value = null;
  currentModel = null;
  incomingModel = null;
  resultModel = null;
}

function applyContent(content: string | null | undefined) {
  resultTouched.value = true;
  resultDeleted.value = content == null;
  resultModel?.setValue(content ?? "");
}

function keepBoth() {
  const current = props.conflict.currentContent;
  const incoming = props.conflict.incomingContent;
  if (current == null || incoming == null) return;
  applyContent(current.endsWith("\n") || incoming.length === 0 ? current + incoming : `${current}\n${incoming}`);
}

function markResolved() {
  if (resultDeleted.value) {
    emit("resolve", { resolution: "DELETE" });
    return;
  }
  emit("resolve", { resolution: "MANUAL", content: resultModel?.getValue() ?? "" });
}

watch(
  () => props.conflict,
  async (conflict) => {
    resultDeleted.value = false;
    resultTouched.value = false;
    if (!currentModel) {
      await nextTick();
      await initializeEditors();
      return;
    }
    const language = languageFromPath(conflict.path);
    currentModel.setValue(conflict.currentContent ?? "");
    incomingModel?.setValue(conflict.incomingContent ?? "");
    resultModel?.setValue(conflict.resultContent ?? "");
    if (monacoLib && currentModel && incomingModel && resultModel) {
      monacoLib.editor.setModelLanguage(currentModel, language);
      monacoLib.editor.setModelLanguage(incomingModel, language);
      monacoLib.editor.setModelLanguage(resultModel, language);
    }
  },
  { immediate: true }
);

onBeforeUnmount(disposeEditors);
</script>

<template>
  <div class="merge-editor">
    <header class="merge-header">
      <div>
        <div class="merge-title"><GitMerge class="h-4 w-4" /> 合并编辑器</div>
        <div class="merge-path">{{ conflict.path }} · {{ conflict.rawStatus }}</div>
      </div>
      <div class="merge-header-actions">
        <Button size="sm" variant="secondary" :disabled="resolving" @click="emit('abort')">
          <RotateCcw class="h-3.5 w-3.5" /> 取消本次合并
        </Button>
        <Button size="sm" variant="ghost" :disabled="resolving" @click="emit('close')">关闭</Button>
      </div>
    </header>

    <div class="merge-inputs">
      <section class="merge-pane">
        <div class="merge-pane-title current">
          <span>当前个人版本</span>
          <Button size="sm" variant="ghost" :disabled="resolving" @click="applyContent(conflict.currentContent)">
            {{ conflict.currentContent == null ? "采用删除" : "保留当前" }}
          </Button>
        </div>
        <div v-if="conflict.currentContent == null" class="merge-missing"><Trash2 class="h-4 w-4" /> 此版本中不存在该文件</div>
        <div v-show="conflict.currentContent != null" ref="currentEl" class="merge-monaco"></div>
      </section>
      <section class="merge-pane">
        <div class="merge-pane-title incoming">
          <span>应用版本</span>
          <Button size="sm" variant="ghost" :disabled="resolving" @click="applyContent(conflict.incomingContent)">
            {{ conflict.incomingContent == null ? "采用删除" : "采用应用" }}
          </Button>
        </div>
        <div v-if="conflict.incomingContent == null" class="merge-missing"><Trash2 class="h-4 w-4" /> 此版本中不存在该文件</div>
        <div v-show="conflict.incomingContent != null" ref="incomingEl" class="merge-monaco"></div>
      </section>
    </div>

    <section class="merge-result">
      <div class="merge-pane-title result">
        <span>合并结果（可编辑）</span>
        <div class="merge-result-actions">
          <Button
            size="sm"
            variant="ghost"
            :disabled="resolving || conflict.currentContent == null || conflict.incomingContent == null"
            @click="keepBoth"
          >保留两者</Button>
          <Button
            size="sm"
            :disabled="resolving || !resultTouched || conflictMarkersRemain"
            @click="markResolved"
          ><Check class="h-3.5 w-3.5" /> 标记已解决</Button>
        </div>
      </div>
      <div v-if="resultDeleted" class="merge-missing result-deleted"><Trash2 class="h-4 w-4" /> 结果将删除此文件</div>
      <div v-show="!resultDeleted" ref="resultEl" class="merge-monaco result-editor"></div>
      <div v-if="conflictMarkersRemain" class="merge-warning">
        <AlertTriangle class="h-3.5 w-3.5" /> 合并结果仍含 Git 冲突标记，请编辑完成后再标记已解决。
      </div>
    </section>
  </div>
</template>

<style scoped>
.merge-editor { display:flex; height:100%; min-height:0; flex-direction:column; background:#fff; color:#27272a; }
.merge-header { display:flex; min-height:48px; align-items:center; justify-content:space-between; gap:12px; border-bottom:1px solid #e4e4e7; padding:6px 12px; background:#fafafa; }
.merge-title,.merge-header-actions,.merge-pane-title,.merge-result-actions,.merge-missing,.merge-warning { display:flex; align-items:center; gap:6px; }
.merge-title { font-size:13px; font-weight:650; }
.merge-path { margin-top:2px; font-size:11px; color:#71717a; }
.merge-inputs { display:grid; min-height:0; flex:1; grid-template-columns:1fr 1fr; border-bottom:2px solid #d4d4d8; }
.merge-pane { display:flex; min-width:0; min-height:0; flex-direction:column; border-right:1px solid #e4e4e7; }
.merge-pane-title { min-height:34px; justify-content:space-between; border-bottom:1px solid #e4e4e7; padding:3px 8px; font-size:11px; font-weight:650; background:#f4f4f5; }
.merge-pane-title.current { background:#eff6ff; }
.merge-pane-title.incoming { background:#fff7ed; }
.merge-pane-title.result { background:#f0fdf4; }
.merge-monaco { min-height:0; flex:1; }
.merge-result { display:flex; min-height:180px; flex:1; flex-direction:column; }
.result-editor { min-height:140px; }
.merge-missing { flex:1; justify-content:center; color:#71717a; font-size:12px; background:#fafafa; }
.result-deleted { min-height:120px; }
.merge-warning { border-top:1px solid #fed7aa; padding:5px 8px; background:#fff7ed; color:#9a3412; font-size:11px; }
</style>
