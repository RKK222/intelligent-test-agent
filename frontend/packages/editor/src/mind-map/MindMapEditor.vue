<script setup lang="ts">
import {
  Bold,
  ChevronRight,
  CircleMinus,
  CirclePlus,
  FoldVertical,
  Maximize2,
  Redo2,
  RotateCcw,
  Trash2,
  Undo2,
  ZoomIn,
  ZoomOut
} from "lucide-vue-next";
import { computed, ref, shallowRef } from "vue";
import MindMapCanvas from "./MindMapCanvas.vue";
import { parseMindMapMarkdown, prepareMindMapDocument } from "./markdown";
import {
  canAllocateMindMapNodeIds,
  type MindMapApplyResult,
  type MindMapCanvasSelection,
  type MindMapDocument,
  type MindMapNode,
  type MindMapNodeStyle,
  type MindMapUnknownTlv
} from "./model";

const props = withDefaults(defineProps<{
  document: MindMapDocument;
  readonly?: boolean;
  generationKey?: string | number;
  showActions?: boolean;
}>(), {
  readonly: false,
  generationKey: "default",
  showActions: true
});

const emit = defineEmits<{
  apply: [result: MindMapApplyResult];
  cancel: [];
  "draft-change": [document: MindMapDocument];
}>();

type CanvasExposed = InstanceType<typeof MindMapCanvas> & {
  addChild(): void;
  addSibling(): void;
  removeSelected(): void;
  setSelectedText(text: string): void;
  setSelectedStyle(style: MindMapNodeStyle): void;
  restoreSelectedStyle(): void;
  toggleSelectedCollapse(): void;
  undo(): void;
  redo(): void;
  zoomIn(): void;
  zoomOut(): void;
  fit(): void;
};

function cloneUnknownFields(fields: MindMapUnknownTlv[] | undefined): MindMapUnknownTlv[] | undefined {
  return fields?.map((field) => ({ type: field.type, value: field.value.slice() }));
}

function cloneNode(node: MindMapNode): MindMapNode {
  return {
    id: node.id,
    text: node.text,
    collapsed: node.collapsed,
    style: node.style ? { ...node.style } : undefined,
    unknownFields: cloneUnknownFields(node.unknownFields),
    children: node.children.map(cloneNode)
  };
}

function cloneDocument(document: MindMapDocument): MindMapDocument {
  return {
    root: cloneNode(document.root),
    nextId: document.nextId,
    lineEnding: document.lineEnding,
    metadataPresent: document.metadataPresent,
    unknownMetadataFields: cloneUnknownFields(document.unknownMetadataFields) ?? []
  };
}

const canvasRef = shallowRef<CanvasExposed>();
const draft = ref(cloneDocument(props.document));
const selection = ref<MindMapCanvasSelection>();
const error = ref("");

const editDisabled = computed(() => props.readonly || !selection.value);
const nodeIdExhausted = computed(() => !canAllocateMindMapNodeIds(draft.value, 1));
const insertDisabled = computed(() => editDisabled.value || nodeIdExhausted.value);
const siblingDisabled = computed(() => insertDisabled.value || selection.value?.isRoot === true);
const deleteDisabled = computed(() => editDisabled.value || selection.value?.isRoot === true);
const collapseDisabled = computed(() => (
  editDisabled.value
  || selection.value?.isRoot === true
  || selection.value?.hasChildren !== true
));

const textValue = computed(() => selection.value?.text ?? "");
const textColor = computed(() => selection.value?.style.textColor ?? "#333333");
const fillColor = computed(() => selection.value?.style.fillColor ?? "#FFFFFF");
const borderColor = computed(() => selection.value?.style.borderColor ?? "#549688");
const lineColor = computed(() => selection.value?.style.lineColor ?? "#549688");
const borderWidth = computed(() => selection.value?.style.borderWidth ?? 1);
const lineWidth = computed(() => selection.value?.style.lineWidth ?? 1);
const fontSize = computed(() => selection.value?.style.fontSize ?? 14);
const shape = computed(() => selection.value?.style.shape ?? "rectangle");
const lineDash = computed(() => selection.value?.style.lineDash ?? "solid");
const isBold = computed(() => selection.value?.style.bold === true);

function updateDraft(document: MindMapDocument): void {
  draft.value = document;
  error.value = "";
  emit("draft-change", cloneDocument(document));
}

function updateSelection(next?: MindMapCanvasSelection): void {
  selection.value = next;
}

function updateSelectionStyle(style: MindMapNodeStyle): void {
  if (!selection.value || props.readonly) return;
  canvasRef.value?.setSelectedStyle(style);
  selection.value = {
    ...selection.value,
    style: { ...selection.value.style, ...style }
  };
}

function updateText(event: Event): void {
  if (!selection.value || props.readonly) return;
  const text = (event.target as HTMLInputElement).value;
  canvasRef.value?.setSelectedText(text);
  selection.value = { ...selection.value, text };
}

function updateColor(key: "textColor" | "fillColor" | "borderColor" | "lineColor", event: Event): void {
  updateSelectionStyle({ [key]: (event.target as HTMLInputElement).value });
}

function updateNumber(key: "borderWidth" | "lineWidth" | "fontSize", event: Event): void {
  updateSelectionStyle({ [key]: Number((event.target as HTMLInputElement).value) });
}

function restoreStyle(): void {
  if (!selection.value || props.readonly) return;
  canvasRef.value?.restoreSelectedStyle();
  selection.value = { ...selection.value, style: {} };
}

function toggleBold(event: Event): void {
  updateSelectionStyle({ bold: (event.target as HTMLInputElement).checked });
}

function apply(): void {
  if (props.readonly) return;
  error.value = "";
  try {
    const prepared = prepareMindMapDocument(draft.value);
    const reparsed = parseMindMapMarkdown(prepared.content);
    if (!reparsed.status.canEdit || !reparsed.document) {
      throw new Error(reparsed.status.message ?? "应用后的思维导图校验失败");
    }
    emit("apply", { content: prepared.content, document: reparsed.document });
  } catch (value) {
    error.value = value instanceof Error ? value.message : "思维导图应用失败";
  }
}
</script>

<template>
  <section class="ta-mind-map-editor">
    <div class="ta-mind-map-editor__toolbar" role="toolbar" aria-label="思维导图工具栏">
      <div class="ta-mind-map-editor__tool-group">
        <button type="button" aria-label="撤销" title="撤销" :disabled="readonly" @click="canvasRef?.undo()">
          <Undo2 :size="16" />
        </button>
        <button type="button" aria-label="重做" title="重做" :disabled="readonly" @click="canvasRef?.redo()">
          <Redo2 :size="16" />
        </button>
      </div>
      <span class="ta-mind-map-editor__divider" />
      <div class="ta-mind-map-editor__tool-group">
        <button type="button" aria-label="新增同级节点" :title="nodeIdExhausted ? '节点编号空间已耗尽，不能新增节点' : '新增同级节点'" :disabled="siblingDisabled" @click="canvasRef?.addSibling()">
          <CirclePlus :size="16" />
          <span>同级</span>
        </button>
        <button type="button" aria-label="新增子节点" :title="nodeIdExhausted ? '节点编号空间已耗尽，不能新增节点' : '新增子节点'" :disabled="insertDisabled" @click="canvasRef?.addChild()">
          <ChevronRight :size="16" />
          <span>子级</span>
        </button>
        <button type="button" aria-label="删除节点" title="删除节点" :disabled="deleteDisabled" @click="canvasRef?.removeSelected()">
          <Trash2 :size="16" />
        </button>
        <button
          type="button"
          :aria-label="selection?.collapsed ? '展开节点' : '收起节点'"
          :title="selection?.collapsed ? '展开节点' : '收起节点'"
          :disabled="collapseDisabled"
          @click="canvasRef?.toggleSelectedCollapse()"
        >
          <FoldVertical :size="16" />
        </button>
      </div>
      <span class="ta-mind-map-editor__spacer" />
      <div class="ta-mind-map-editor__tool-group">
        <button type="button" aria-label="缩小" title="缩小" @click="canvasRef?.zoomOut()"><ZoomOut :size="16" /></button>
        <button type="button" aria-label="放大" title="放大" @click="canvasRef?.zoomIn()"><ZoomIn :size="16" /></button>
        <button type="button" aria-label="适应画布" title="适应画布" @click="canvasRef?.fit()"><Maximize2 :size="16" /></button>
      </div>
    </div>

    <div class="ta-mind-map-editor__body">
      <MindMapCanvas
        ref="canvasRef"
        :document="draft"
        :readonly="readonly"
        :generation-key="generationKey"
        @change="updateDraft"
        @selection-change="updateSelection"
      />

      <aside class="ta-mind-map-editor__inspector" aria-label="节点属性">
        <div class="ta-mind-map-editor__inspector-title">节点属性</div>
        <p v-if="!selection" class="ta-mind-map-editor__hint">选择一个节点后可修改文字和样式。</p>
        <template v-else>
          <label class="ta-mind-map-editor__field ta-mind-map-editor__field--wide">
            <span>节点文字</span>
            <input aria-label="节点文字" type="text" :value="textValue" :disabled="readonly" maxlength="4096" @change="updateText" />
          </label>
          <div class="ta-mind-map-editor__color-grid">
            <label class="ta-mind-map-editor__field"><span>文字</span><input aria-label="文字颜色" type="color" :value="textColor" :disabled="readonly" @input="updateColor('textColor', $event)" /></label>
            <label class="ta-mind-map-editor__field"><span>填充</span><input aria-label="填充颜色" type="color" :value="fillColor" :disabled="readonly" @input="updateColor('fillColor', $event)" /></label>
            <label class="ta-mind-map-editor__field"><span>边框</span><input aria-label="边框颜色" type="color" :value="borderColor" :disabled="readonly" @input="updateColor('borderColor', $event)" /></label>
            <label class="ta-mind-map-editor__field"><span>分支线</span><input aria-label="分支线颜色" type="color" :value="lineColor" :disabled="readonly" @input="updateColor('lineColor', $event)" /></label>
          </div>
          <label class="ta-mind-map-editor__field ta-mind-map-editor__field--wide">
            <span>形状</span>
            <select aria-label="节点形状" :value="shape" :disabled="readonly" @change="updateSelectionStyle({ shape: ($event.target as HTMLSelectElement).value as MindMapNodeStyle['shape'] })">
              <option value="rectangle">矩形</option>
              <option value="roundedRectangle">圆角矩形</option>
              <option value="ellipse">椭圆</option>
              <option value="diamond">菱形</option>
            </select>
          </label>
          <div class="ta-mind-map-editor__number-grid">
            <label class="ta-mind-map-editor__field"><span>字号</span><input aria-label="字号" type="number" min="10" max="72" :value="fontSize" :disabled="readonly" @change="updateNumber('fontSize', $event)" /></label>
            <label class="ta-mind-map-editor__field"><span>边框宽</span><input aria-label="边框宽度" type="number" min="0" max="10" :value="borderWidth" :disabled="readonly" @change="updateNumber('borderWidth', $event)" /></label>
            <label class="ta-mind-map-editor__field"><span>线宽</span><input aria-label="分支线宽度" type="number" min="1" max="10" :value="lineWidth" :disabled="readonly" @change="updateNumber('lineWidth', $event)" /></label>
          </div>
          <label class="ta-mind-map-editor__check"><input aria-label="粗体" type="checkbox" :checked="isBold" :disabled="readonly" @change="toggleBold" /><Bold :size="15" /><span>粗体</span></label>
          <label class="ta-mind-map-editor__field ta-mind-map-editor__field--wide"><span>分支线型</span><select aria-label="分支线型" :value="lineDash" :disabled="readonly" @change="updateSelectionStyle({ lineDash: ($event.target as HTMLSelectElement).value as MindMapNodeStyle['lineDash'] })"><option value="solid">实线</option><option value="dashed">虚线</option></select></label>
          <button type="button" class="ta-mind-map-editor__restore" aria-label="恢复默认样式" :disabled="readonly" @click="restoreStyle"><RotateCcw :size="14" />恢复默认样式</button>
        </template>
      </aside>
    </div>

    <div v-if="error" class="ta-mind-map-editor__error" role="alert">{{ error }}</div>
    <footer v-if="showActions" class="ta-mind-map-editor__actions">
      <button type="button" class="ta-mind-map-editor__cancel" aria-label="取消编辑" @click="emit('cancel')">取消</button>
      <button type="button" class="ta-mind-map-editor__apply" aria-label="应用思维导图" :disabled="readonly" @click="apply">应用</button>
    </footer>
  </section>
</template>

<style scoped>
.ta-mind-map-editor {
  display: flex;
  min-height: 0;
  height: 100%;
  flex-direction: column;
  overflow: hidden;
  color: #1f2937;
  background: #fff;
  font-family: "Geist Sans", "Noto Sans SC", sans-serif;
}

.ta-mind-map-editor__toolbar {
  display: flex;
  min-height: 42px;
  align-items: center;
  gap: 8px;
  padding: 5px 10px;
  border-bottom: 1px solid #e5e7eb;
  background: #f8fafc;
}

.ta-mind-map-editor__tool-group { display: flex; align-items: center; gap: 3px; }
.ta-mind-map-editor__tool-group button,
.ta-mind-map-editor__restore {
  display: inline-flex;
  height: 30px;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 0 8px;
  border: 1px solid transparent;
  border-radius: 5px;
  color: #374151;
  background: transparent;
  font-size: 12px;
  cursor: pointer;
}
.ta-mind-map-editor__tool-group button:hover:not(:disabled),
.ta-mind-map-editor__restore:hover:not(:disabled) { border-color: #d1d5db; background: #fff; }
.ta-mind-map-editor button:focus-visible,
.ta-mind-map-editor input:focus-visible,
.ta-mind-map-editor select:focus-visible { outline: 2px solid #7f1e2b; outline-offset: 1px; }
.ta-mind-map-editor button:disabled { cursor: not-allowed; opacity: .38; }
.ta-mind-map-editor__divider { width: 1px; height: 22px; background: #dfe3e8; }
.ta-mind-map-editor__spacer { flex: 1; }

.ta-mind-map-editor__body { display: grid; min-height: 0; flex: 1; grid-template-columns: minmax(0, 1fr) 224px; }
.ta-mind-map-editor__inspector { overflow: auto; padding: 14px; border-left: 1px solid #e5e7eb; background: #fcfcfd; }
.ta-mind-map-editor__inspector-title { margin-bottom: 12px; color: #111827; font-size: 13px; font-weight: 600; }
.ta-mind-map-editor__hint { margin: 0; color: #6b7280; font-size: 12px; line-height: 1.6; }
.ta-mind-map-editor__field { display: grid; gap: 5px; color: #6b7280; font-size: 11px; }
.ta-mind-map-editor__field--wide { margin-bottom: 12px; }
.ta-mind-map-editor__field input[type="text"],
.ta-mind-map-editor__field input[type="number"],
.ta-mind-map-editor__field select {
  box-sizing: border-box;
  width: 100%;
  height: 30px;
  padding: 0 8px;
  border: 1px solid #d8dde5;
  border-radius: 5px;
  color: #1f2937;
  background: #fff;
  font-size: 12px;
}
.ta-mind-map-editor__field input[type="color"] { width: 100%; height: 30px; padding: 2px; border: 1px solid #d8dde5; border-radius: 5px; background: #fff; }
.ta-mind-map-editor__color-grid,
.ta-mind-map-editor__number-grid { display: grid; gap: 8px; margin-bottom: 12px; grid-template-columns: 1fr 1fr; }
.ta-mind-map-editor__number-grid { grid-template-columns: repeat(3, 1fr); }
.ta-mind-map-editor__check { display: flex; align-items: center; gap: 6px; margin-bottom: 12px; color: #374151; font-size: 12px; }
.ta-mind-map-editor__restore { width: 100%; margin-top: 2px; border-color: #d8dde5; background: #fff; }

.ta-mind-map-editor__error { padding: 8px 12px; border-top: 1px solid #fecdd3; color: #9f1239; background: #fff1f2; font-size: 12px; }
.ta-mind-map-editor__actions { display: flex; justify-content: flex-end; gap: 8px; padding: 10px 14px; border-top: 1px solid #e5e7eb; }
.ta-mind-map-editor__actions button { min-width: 72px; height: 32px; border-radius: 5px; font-size: 13px; cursor: pointer; }
.ta-mind-map-editor__cancel { border: 1px solid #d1d5db; color: #374151; background: #fff; }
.ta-mind-map-editor__apply { border: 1px solid #7f1e2b; color: #fff; background: #7f1e2b; }
.ta-mind-map-editor__apply:disabled { cursor: not-allowed; opacity: .45; }

@media (max-width: 860px) {
  .ta-mind-map-editor__body { grid-template-columns: minmax(0, 1fr); }
  .ta-mind-map-editor__inspector { max-height: 210px; border-top: 1px solid #e5e7eb; border-left: 0; }
}
</style>
