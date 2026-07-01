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
  /**
   * Markdown 预览开关（受控）。开启后会在编辑器下方追加分屏预览区，
   * 关闭后回到全屏编辑。组件本身不维护该状态，由父级（通常是 tab 表头）
   * 提供按钮并双向绑定，避免同一状态出现两处入口。
   */
  showPreview?: boolean;
};

export type CodeEditorEmits = {
  change: [content: string];
  save: [];
  selectionChange: [selection: EditorSelectionContext | undefined];
  "update:showPreview": [enabled: boolean];
};
</script>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from "vue";
import { languageFromPath } from "./language";
import MarkdownPreview from "./MarkdownPreview.vue";

const props = withDefaults(defineProps<CodeEditorProps>(), { content: "", showPreview: false });
const displayName = computed(() => {
  if (!props.path) return "";
  const parts = props.path.split(/[\\/]+/).filter(Boolean);
  return parts.at(-1) ?? props.path;
});
const emit = defineEmits<CodeEditorEmits>();

// 当前文件是否为 Markdown：决定是否展示预览开关与分屏能力
const isMarkdown = computed(() => !!props.path && languageFromPath(props.path) === "markdown");
// 预览开关：受控模式，状态完全由父级驱动（tab 表头上的预览按钮）。
// 不再保留组件内部副本，避免出现"两处入口不同步"的问题。
const showPreview = computed(() => props.showPreview ?? false);
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
  if (!containerEl.value || !containerEl.value.parentNode) {
    return;
  }
  if (!monacoLib) {
    const mod = await import("./monaco-env");
    monacoLib = await mod.loadMonaco();
  }
  if (!monacoLib) {
    console.error("Failed to load Monaco Editor");
    return;
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
    wordWrap: "off",
    // 滚动条细线化：Monaco 内部使用自定义控件，垂直与水平都用 6px
    scrollbar: {
      vertical: "visible",
      horizontal: "visible",
      verticalScrollbarSize: 6,
      horizontalScrollbarSize: 6,
      useShadows: false
    }
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
  // 注册 Ctrl/Cmd+S 快捷键：编辑器失焦/聚焦情况下都能拦截浏览器的「保存网页」行为，
  // 改为向父级 emit('save')，由父级决定是否真的落盘（与右下角保存按钮同款逻辑）。
  // 只读文件不响应，避免和 Monaco 内置提示产生冲突。
  if (!props.readonly) {
    const KeyMod = monacoLib.KeyMod;
    const KeyCode = monacoLib.KeyCode;
    inst.addCommand(KeyMod.CtrlCmd | KeyCode.KeyS, () => {
      emit("save");
    });
  }
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
  await nextTick();
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
    // 切换文件时由父级决定是否关闭预览；这里不主动改写 props。
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
  <div v-if="!path" class="flex h-full min-h-0 flex-col items-center justify-center bg-[var(--ta-panel-2)] text-slate-500 px-6">
    <div class="text-center max-w-[280px] flex flex-col items-center">
      <div class="mb-4 flex items-center justify-center w-16 h-16 rounded-full bg-slate-50 shadow-sm border border-slate-100/50">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="32" height="32" class="text-indigo-500">
          <defs>
            <linearGradient id="folderGrad" x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" stop-color="#818cf8" />
              <stop offset="100%" stop-color="#4f46e5" />
            </linearGradient>
            <linearGradient id="folderBackGrad" x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" stop-color="#c7d2fe" />
              <stop offset="100%" stop-color="#a5b4fc" />
            </linearGradient>
          </defs>
          <path d="M4 4h5l2 3h9a2 2 0 0 1 2 2v1H2V6a2 2 0 0 1 2-2z" fill="url(#folderBackGrad)" />
          <path d="M6 8h12v6H6z" fill="#ffffff" opacity="0.9" rx="1" />
          <path d="M8 10h8v1H8zm0 2h5v1H8z" fill="#e2e8f0" />
          <path d="M2 10h20l-2 10H4z" fill="url(#folderGrad)" />
        </svg>
      </div>
      <div class="text-[16px] font-bold text-slate-700 tracking-tight">开始您的探索</div>
      <div class="mt-2 text-[13px] text-slate-400 leading-relaxed">
        请从左侧文件树选择一个测试脚本或配置文件，或者直接在右侧向智能体发起提问。
      </div>
    </div>
  </div>
  <div v-else class="flex h-full min-h-0 flex-col bg-[var(--ta-surface)]">
    <!-- 编辑器主体始终保留同一个容器，避免 v-if 切换销毁 Monaco 已挂载的 DOM；
         Markdown 预览开启时在下方追加 sash + 预览，形成上下分屏。
         预览开关已上提到 tab 表头，受控于 showPreview prop。 -->
    <div ref="splitContainerEl" class="flex min-h-0 flex-1 flex-col">
      <div
        ref="containerEl"
        class="min-h-0"
        :style="isMarkdown && showPreview ? { height: splitPct + '%' } : { flex: 1 }"
      />
      <template v-if="isMarkdown && showPreview">
        <div
          class="relative h-[8px] w-full shrink-0 cursor-row-resize border-t border-b border-[var(--ta-border)] bg-[var(--ta-bg-2)] hover:bg-[var(--ta-hover)] transition-colors duration-150 flex items-center justify-center"
          @pointerdown="onSashDown"
        >
          <!-- 扩大 sash 命中区域，便于拖拽 -->
          <div class="absolute inset-x-0 -top-[4px] -bottom-[4px] z-10" />
          <!-- 拖拽手柄 -->
          <div class="h-[2px] w-8 rounded-full bg-[var(--ta-border-strong)] z-20" />
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
  </div>
</template>

<style scoped>
/* Monaco 编辑器内部滚动条细线化（同时影响水平与竖向） */
:deep(.monaco-scrollable-element)::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}
:deep(.monaco-scrollable-element)::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.25);
  border-radius: 3px;
}
:deep(.monaco-scrollable-element)::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.4);
}
:deep(.monaco-scrollable-element)::-webkit-scrollbar-track {
  background: transparent;
}
/* 覆盖最小尺寸，防止 Monaco 在极小宽度下出现粗滚动条 */
:deep(.monaco-scrollable-element) .slider {
  border-radius: 3px !important;
}
</style>
