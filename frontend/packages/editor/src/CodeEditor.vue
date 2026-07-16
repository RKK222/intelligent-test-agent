<script lang="ts">
import type * as monaco from "monaco-editor";

export type EditorSelectionContext = {
  startLineNumber: number;
  startColumn: number;
  endLineNumber: number;
  endColumn: number;
  text: string;
};

export type PreviewMode = "off" | "full" | "split";

export type CodeEditorProps = {
  path?: string;
  content?: string;
  dirty?: boolean;
  readonly?: boolean;
  saving?: boolean;
  /**
   * Markdown 预览开关（受控）。开启后按 previewMode 渲染全屏或分屏预览区，
   * 关闭后回到全屏编辑。组件本身不维护该状态，由父级（通常是底部 footer）
   * 提供按钮并双向绑定。
   */
  showPreview?: boolean;
  /** 预览模式：off(关闭) | full(整体预览) | split(分上下) */
  previewMode?: PreviewMode;
};

export type CodeEditorEmits = {
  change: [content: string];
  save: [];
  addSelectionContext: [];
  selectionChange: [selection: EditorSelectionContext | undefined];
  "update:showPreview": [enabled: boolean];
  "update:previewMode": [mode: PreviewMode];
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

// 当前文件是否为 Markdown：决定是否展示预览与分屏能力
const isMarkdown = computed(() => !!props.path && languageFromPath(props.path) === "markdown");

// 规范化的预览模式：off | full | split
const effectivePreviewMode = computed<PreviewMode>(() => {
  if (!isMarkdown.value) return "off";
  if (props.previewMode) return props.previewMode;
  return props.showPreview ? "full" : "off";
});

// 保持向下兼容 showPreview boolean 计算
const showPreview = computed(() => effectivePreviewMode.value !== "off");
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

let containerResizeObserver: ResizeObserver | null = null;
let ensureEditorSequence = 0;

function layoutEditor() {
  if (editor.value && typeof editor.value.layout === "function") {
    const width = containerEl.value?.clientWidth ?? 0;
    const height = containerEl.value?.clientHeight ?? 0;
    // Markdown 预览分屏会改变源码宿主高度；显式传入实际尺寸，
    // 避免 Monaco 在 Dockview/百分比高度变更后沿用 0 尺寸而显示空白。
    if (width > 0 && height > 0) {
      editor.value.layout({ width, height });
      return;
    }
    editor.value.layout();
  }
}

function observeContainerResize() {
  if (containerResizeObserver) {
    containerResizeObserver.disconnect();
    containerResizeObserver = null;
  }
  if (containerEl.value && typeof ResizeObserver !== "undefined") {
    containerResizeObserver = new ResizeObserver(() => {
      layoutEditor();
    });
    containerResizeObserver.observe(containerEl.value);
  }
}

function buildModel(path: string, content: string): monaco.editor.ITextModel {
  const m = monacoLib!;
  const uri = typeof m.Uri.file === "function" ? m.Uri.file(path) : m.Uri.parse(`file:///${path}`);
  const existing = m.editor.getModel(uri);
  if (existing) {
    if (existing.getValue() !== content) {
      existing.setValue(content);
    }
    return existing;
  }
  return m.editor.createModel(content, languageFromPath(path), uri);
}

function modelMatchesPath(candidate: monaco.editor.ITextModel, path: string): boolean {
  if (!monacoLib) {
    return false;
  }
  const expectedUri = typeof monacoLib.Uri.file === "function"
    ? monacoLib.Uri.file(path)
    : monacoLib.Uri.parse(`file:///${path}`);
  return candidate.uri.toString() === expectedUri.toString();
}

async function ensureMonacoEditor(path: string) {
  const sequence = ++ensureEditorSequence;
  if (!containerEl.value || !containerEl.value.parentNode) {
    return;
  }
  if (!monacoLib) {
    const mod = await import("./monaco-env");
    monacoLib = await mod.loadMonaco();
  }
  // Monaco 按需加载期间可能连续切换文件；旧调用完成后不能覆盖当前文件模型。
  if (sequence !== ensureEditorSequence || props.path !== path) {
    return;
  }
  if (!monacoLib) {
    console.error("Failed to load Monaco Editor");
    return;
  }
  model = buildModel(path, props.content);
  if (editor.value) {
    editor.value.setModel(model);
    emitSelection(editor.value);
    await nextTick();
    layoutEditor();
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
    // 工作台中间编辑区默认按可视宽度换行，长日志、JSON 和说明文档无需横向滚动才能阅读。
    wordWrap: "on",
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
  observeContainerResize();
  await nextTick();
  layoutEditor();

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
  inst.addAction({
    id: "test-agent-add-selection-to-chat-context",
    label: "添加选中内容到对话",
    contextMenuGroupId: "navigation",
    contextMenuOrder: 1.5,
    run: () => {
      emit("addSelectionContext");
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
  await nextTick();
  if (!props.path || !containerEl.value) {
    return;
  }
  await ensureMonacoEditor(props.path);
});

// 路径与预览分屏状态变化：重新触发 Monaco layout，确保容器尺寸变动后渲染正常
watch(
  () => [effectivePreviewMode.value, splitPct.value, props.path],
  async () => {
    await nextTick();
    layoutEditor();
  }
);

// 路径变化：切换到新文件模型
watch(
  () => props.path,
  async (path) => {
    if (!path) {
      return;
    }
    // 切换文件时由父级决定是否关闭预览；这里不主动改写 props。
    await nextTick();
    await ensureMonacoEditor(path);
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
  ensureEditorSequence += 1;
  if (containerResizeObserver) {
    containerResizeObserver.disconnect();
    containerResizeObserver = null;
  }
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
    // path/content 可能在同一 tick 更新；旧模型 URI 未切换完成时禁止写入新文件内容。
    if (!model || !props.path || !modelMatchesPath(model, props.path) || content === model.getValue()) {
      return;
    }
    syncing = true;
    model.setValue(content);
    syncing = false;
    if (editor.value) {
      editor.value.layout();
    }
  }
);

watch(
  () => props.readonly,
  (readonly) => {
    editor.value?.updateOptions({ readOnly: readonly ?? false });
  }
);

onBeforeUnmount(() => {
  if (containerResizeObserver) {
    containerResizeObserver.disconnect();
    containerResizeObserver = null;
  }
  editor.value?.dispose();
  editor.value = null;
  model = null;
});

function revealSelection(payload: { startLine: number; endLine: number; text: string }) {
  const inst = editor.value;
  if (!inst || !monacoLib || !model) return;
  
  let range: monaco.Range | null = null;
  
  if (payload.text) {
    const matches = model.findMatches(payload.text, false, false, false, null, true);
    if (matches.length > 0) {
      let bestMatch = matches[0];
      let minDiff = Math.abs(bestMatch.range.startLineNumber - payload.startLine);
      for (const m of matches) {
        const diff = Math.abs(m.range.startLineNumber - payload.startLine);
        if (diff < minDiff) {
          minDiff = diff;
          bestMatch = m;
        }
      }
      range = bestMatch.range;
    }
  }
  
  if (!range && payload.startLine && payload.endLine) {
    const lineContent = model.getLineContent(payload.startLine);
    range = new monacoLib.Range(
      payload.startLine,
      1,
      payload.endLine,
      lineContent.length + 1
    );
  }
  
  if (range) {
    inst.setSelection(range);
    inst.revealRangeInCenter(range, 1);
    inst.focus();
  }
}

defineExpose({
  revealSelection
});
</script>

<template>
  <div v-if="!path" class="flex h-full min-h-0 flex-col items-center justify-center bg-[var(--ta-panel-2)] text-slate-500 px-6">
    <div class="text-center max-w-[280px] flex flex-col items-center">
      <div class="code-editor-empty-icon">
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
      <slot name="empty-actions" />
    </div>
  </div>
  <div v-else class="flex h-full min-h-0 flex-col bg-[var(--ta-surface)]">
    <!-- 编辑器主体始终保留同一个容器，避免 v-if 切换销毁 Monaco 已挂载的 DOM；
         Markdown 预览整体(full)/分屏(split)受控于 effectivePreviewMode。 -->
    <div ref="splitContainerEl" class="flex min-h-0 flex-1 flex-col">
      <div
        v-show="effectivePreviewMode !== 'full'"
        ref="containerEl"
        class="min-h-0 w-full shrink-0 overflow-hidden"
        data-testid="code-editor-source"
        :style="effectivePreviewMode === 'split' ? { height: splitPct + '%', flex: 'none' } : { flex: 1 }"
      />
      <template v-if="effectivePreviewMode === 'split'">
        <div
          class="relative h-[8px] w-full shrink-0 cursor-row-resize border-t border-b border-[var(--ta-border)] bg-[var(--ta-bg-2)] hover:bg-[var(--ta-hover)] transition-colors duration-150 flex items-center justify-center"
          @pointerdown="onSashDown"
        >
          <!-- 扩大 sash 命中区域，便于拖拽 -->
          <div class="absolute inset-x-0 -top-[4px] -bottom-[4px] z-10" />
          <!-- 拖拽手柄 -->
          <div class="h-[2px] w-8 rounded-full bg-[var(--ta-border-strong)] z-20" />
        </div>
      </template>
      <MarkdownPreview
        v-if="effectivePreviewMode !== 'off'"
        ref="previewRef"
        :content="content"
        class="min-h-0 flex-1"
        @scroll="onPreviewScroll"
        @ready="syncFromEditor"
        @change="emit('change', $event)"
      />
    </div>
  </div>
</template>

<style scoped>
.code-editor-empty-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  margin-bottom: 16px;
  border: 1px solid var(--ta-border, #eaeaea);
  border-radius: 999px;
  background: #f8f8f8;
  box-shadow: none;
}

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
