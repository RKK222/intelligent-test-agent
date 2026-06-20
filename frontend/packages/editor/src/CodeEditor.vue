<script lang="ts">
import type * as monaco from "monaco-editor";

export type EditorSelectionContext = {
  startLineNumber: number;
  startColumn: number;
  endLineNumber: number;
  endColumn: number;
  text: string;
};

export type CodeEditorProps = {
  path?: string;
  content?: string;
  dirty?: boolean;
  readonly?: boolean;
  saving?: boolean;
  feedback?: import("@test-agent/ui-kit").Feedback | null;
};
</script>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from "vue";
import { Save } from "lucide-vue-next";
import { Button, FeedbackBanner } from "@test-agent/ui-kit";
import { languageFromPath } from "./language";

const props = withDefaults(defineProps<CodeEditorProps>(), { content: "" });
const emit = defineEmits<{
  change: [content: string];
  save: [];
  selectionChange: [selection: EditorSelectionContext | undefined];
}>();

const containerEl = ref<HTMLElement | null>(null);
// 编辑器实例用 shallowRef 避免 Vue 深度代理 Monaco 内部对象
const editor = shallowRef<monaco.editor.IStandaloneCodeEditor | null>(null);
let model: monaco.editor.ITextModel | null = null;
let monacoLib: typeof monaco | null = null;
// 内部正在用外部 content 同步模型时，跳过 change 事件，避免回环
let syncing = false;

function buildModel(path: string, content: string): monaco.editor.ITextModel {
  const m = monacoLib!;
  const uri = m.Uri.parse(`file:///${encodeURIComponent(path)}`);
  const existing = m.editor.getModel(uri);
  if (existing) {
    if (existing.getValue() !== content) {
      existing.setValue(content);
    }
    return existing;
  }
  return m.editor.createModel(content, languageFromPath(path), uri);
}

function emitSelection(inst: monaco.editor.IStandaloneCodeEditor) {
  const selection = inst.getSelection();
  const m = inst.getModel();
  if (!selection || !m || selection.isEmpty()) {
    emit("selectionChange", undefined);
    return;
  }
  emit("selectionChange", {
    startLineNumber: selection.startLineNumber,
    startColumn: selection.startColumn,
    endLineNumber: selection.endLineNumber,
    endColumn: selection.endColumn,
    text: m.getValueInRange(selection)
  });
}

onMounted(async () => {
  if (!props.path || !containerEl.value) {
    return;
  }
  const mod = await import("./monaco-env");
  monacoLib = mod.monaco;
  model = buildModel(props.path, props.content);
  const inst = monacoLib.editor.create(containerEl.value, {
    model,
    theme: "vs",
    readOnly: props.readonly ?? false,
    minimap: { enabled: false },
    fontFamily: "Menlo, Monaco, Consolas, 'Liberation Mono', monospace",
    fontSize: 14,
    lineHeight: 20,
    scrollBeyondLastLine: false,
    automaticLayout: true,
    wordWrap: "off"
  });
  editor.value = inst;
  inst.onDidChangeModelContent(() => {
    if (syncing) {
      return;
    }
    emit("change", inst.getValue());
  });
  inst.onDidChangeCursorSelection(() => emitSelection(inst));
  emitSelection(inst);
});

// 路径变化：切换到新文件模型
watch(
  () => props.path,
  async (path) => {
    if (!path || !monacoLib || !editor.value) {
      return;
    }
    model = buildModel(path, props.content);
    editor.value.setModel(model);
    emitSelection(editor.value);
  }
);

// 外部 content 变化：同步到模型，避免回环
watch(
  () => props.content,
  (content) => {
    if (!model || !editor.value || content === model.getValue()) {
      return;
    }
    syncing = true;
    model.setValue(content);
    syncing = false;
  }
);

watch(
  () => props.readonly,
  (readonly) => {
    editor.value?.updateOptions({ readOnly: readonly ?? false });
  }
);

onBeforeUnmount(() => {
  editor.value?.dispose();
  editor.value = null;
  model = null;
});
</script>

<template>
  <div v-if="!path" class="flex h-full min-h-0 items-center justify-center bg-[var(--ta-panel-2)] text-slate-500">
    <div class="text-center">
      <div class="text-[14px] font-semibold text-slate-300">未打开文件</div>
      <div class="mt-1 text-[12px]">从左侧文件树选择一个测试脚本或配置文件</div>
    </div>
  </div>
  <div v-else class="flex h-full min-h-0 flex-col bg-[var(--ta-surface)]">
    <div class="flex h-[41px] items-center gap-2 border-b border-[var(--ta-border)] bg-[var(--ta-surface)] px-4">
      <div class="min-w-0 flex-1 truncate text-[12px] text-[var(--ta-muted)]">{{ path }}</div>
      <span v-if="dirty" class="rounded-full bg-[rgba(245,158,11,.15)] px-2 py-0.5 text-[11px] text-[#946015]">未保存</span>
      <span v-if="readonly" class="rounded-full bg-[var(--ta-control)] px-2 py-0.5 text-[11px] text-[var(--ta-muted)]">只读</span>
      <Button size="icon" variant="ghost" :disabled="!dirty || readonly || saving" title="保存" aria-label="保存" @click="emit('save')">
        <Save class="h-4 w-4" />
      </Button>
    </div>
    <div ref="containerEl" class="min-h-0 flex-1" />
    <FeedbackBanner :feedback="feedback" />
  </div>
</template>
