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
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from "vue";
import { Eye, EyeOff } from "lucide-vue-next";
import { Button, FeedbackBanner } from "@test-agent/ui-kit";
import { languageFromPath } from "./language";
import MarkdownPreview from "./MarkdownPreview.vue";

const props = withDefaults(defineProps<CodeEditorProps>(), { content: "" });
const displayName = computed(() => {
  if (!props.path) return "";
  const parts = props.path.split(/[\\/]+/).filter(Boolean);
  return parts.at(-1) ?? props.path;
});
const emit = defineEmits<{
  change: [content: string];
  save: [];
  selectionChange: [selection: EditorSelectionContext | undefined];
}>();

// 当前文件是否为 Markdown：决定是否展示预览开关与分屏能力
const isMarkdown = computed(() => !!props.path && languageFromPath(props.path) === "markdown");
// 预览开关：文件打开时默认不预览，点击工具栏眼睛图标后开启
const showPreview = ref(false);
// 上下分屏比例（上=编辑器百分比），仅组件内状态，不持久化
const splitPct = ref(50);

// 上下分屏滚动联动锁：防止编辑器↔预览双向同步形成回环（非响应式，命令式读取）
const scrollLock = { value: false };
// 预览组件句柄，用于调用 scrollToSourceLine
const previewRef = shallowRef<InstanceType<typeof MarkdownPreview> | null>(null);
let editorScrollRaf = 0;

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

async function ensureMonacoEditor(path: string, content: string) {
  if (!containerEl.value) {
    return;
  }
  if (!monacoLib) {
    const mod = await import("./monaco-env");
    monacoLib = mod.monaco;
  }
  model = buildModel(path, content);
  if (editor.value) {
    editor.value.setModel(model);
    emitSelection(editor.value);
    return;
  }
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
  // 编辑器滚动时联动预览到对应源码行（仅预览开启时有意义，处理内早退）
  inst.onDidScrollChange((e) => {
    if (e.scrollTopChanged) {
      onEditorScroll();
    }
  });
  emitSelection(inst);
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
  await ensureMonacoEditor(props.path, props.content);
});

// 路径变化：切换到新文件模型
watch(
  () => props.path,
  async (path) => {
    if (!path) {
      return;
    }
    // 切换文件时重置预览开关，满足「打开时默认不预览」
    showPreview.value = false;
    await nextTick();
    await ensureMonacoEditor(path, props.content);
  }
);

// 上下分屏 sash 拖拽：按容器高度百分比调整，限制在 20%~80%
const splitContainerEl = ref<HTMLElement | null>(null);
let dragging = false;

function onSashDown(e: PointerEvent) {
  e.preventDefault();
  dragging = true;
  window.addEventListener("pointermove", onSashMove);
  window.addEventListener("pointerup", onSashUp);
}

function onSashMove(e: PointerEvent) {
  if (!dragging || !splitContainerEl.value) {
    return;
  }
  const rect = splitContainerEl.value.getBoundingClientRect();
  const pct = ((e.clientY - rect.top) / rect.height) * 100;
  splitPct.value = Math.min(80, Math.max(20, pct));
}

function onSashUp() {
  dragging = false;
  window.removeEventListener("pointermove", onSashMove);
  window.removeEventListener("pointerup", onSashUp);
}

onBeforeUnmount(() => {
  // 拖拽中卸载时清理全局监听，避免泄漏
  window.removeEventListener("pointermove", onSashMove);
  window.removeEventListener("pointerup", onSashUp);
});

// 编辑器滚动 → 预览：取编辑器顶部可见行，让对应源码行块顶端对齐预览顶部
function onEditorScroll() {
  if (!showPreview.value || scrollLock.value) {
    return;
  }
  if (editorScrollRaf) {
    return;
  }
  editorScrollRaf = requestAnimationFrame(() => {
    editorScrollRaf = 0;
    const inst = editor.value;
    const preview = previewRef.value;
    if (!inst || !preview || scrollLock.value) {
      return;
    }
    const ranges = inst.getVisibleRanges();
    const line = ranges?.[0]?.startLineNumber ?? 1;
    scrollLock.value = true;
    preview.scrollToSourceLine(line);
    // 下一帧释放锁：程序触发的滚动事件已在同步阶段发完，避免回环
    requestAnimationFrame(() => {
      scrollLock.value = false;
    });
  });
}

// 预览滚动 → 编辑器：把对应源码行滚到编辑器顶部（setScrollTop 同步无动画，避免回环）
function onPreviewScroll(line: number) {
  if (scrollLock.value) {
    return;
  }
  const inst = editor.value;
  if (!inst) {
    return;
  }
  scrollLock.value = true;
  inst.setScrollTop(inst.getTopForLineNumber(line));
  requestAnimationFrame(() => {
    scrollLock.value = false;
  });
}

// 预览首次渲染完成后，按编辑器当前位置对齐一次
function syncFromEditor() {
  const inst = editor.value;
  const preview = previewRef.value;
  if (!inst || !preview) {
    return;
  }
  const ranges = inst.getVisibleRanges();
  const line = ranges?.[0]?.startLineNumber ?? 1;
  scrollLock.value = true;
  preview.scrollToSourceLine(line);
  requestAnimationFrame(() => {
    scrollLock.value = false;
  });
}

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
      <div class="min-w-0 flex-1 truncate text-[12px] text-[var(--ta-muted)]">{{ displayName }}</div>
      <span v-if="dirty" class="rounded-full bg-[rgba(245,158,11,.15)] px-2 py-0.5 text-[11px] text-[#946015]">未保存</span>
      <span v-if="readonly" class="rounded-full bg-[var(--ta-control)] px-2 py-0.5 text-[11px] text-[var(--ta-muted)]">只读</span>
      <!-- 仅 Markdown 文件展示预览开关，默认不预览 -->
      <Button
        v-if="isMarkdown"
        size="icon"
        variant="ghost"
        :title="showPreview ? '关闭预览' : '预览'"
        :aria-label="showPreview ? '关闭预览' : '预览'"
        :aria-pressed="showPreview"
        :class="showPreview ? 'text-[var(--ta-ink)]' : 'text-[var(--ta-muted)]'"
        @click="showPreview = !showPreview"
      >
        <EyeOff v-if="showPreview" class="h-4 w-4" />
        <Eye v-else class="h-4 w-4" />
      </Button>
    </div>
    <!-- 编辑器主体始终保留同一个容器，避免 v-if 切换销毁 Monaco 已挂载的 DOM；
         Markdown 预览开启时在下方追加 sash + 预览，形成上下分屏 -->
    <div ref="splitContainerEl" class="flex min-h-0 flex-1 flex-col">
      <div
        ref="containerEl"
        class="min-h-0"
        :style="isMarkdown && showPreview ? { height: splitPct + '%' } : { flex: 1 }"
      />
      <template v-if="isMarkdown && showPreview">
        <div
          class="relative h-[4px] w-full shrink-0 cursor-row-resize bg-[var(--ta-border)] hover:bg-[var(--ta-hover)]"
          @pointerdown="onSashDown"
        >
          <!-- 扩大 sash 命中区域，便于拖拽 -->
          <div class="absolute inset-x-0 -top-[3px] -bottom-[3px]" />
        </div>
        <MarkdownPreview
          ref="previewRef"
          :content="content"
          class="min-h-0 flex-1"
          @scroll="onPreviewScroll"
          @ready="syncFromEditor"
        />
      </template>
    </div>
    <FeedbackBanner :feedback="feedback" />
  </div>
</template>
