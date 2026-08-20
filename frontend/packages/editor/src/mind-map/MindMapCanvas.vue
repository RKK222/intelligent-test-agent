<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, shallowRef, toRaw, watch } from "vue";
import {
  MIND_MAP_MAX_DEPTH,
  MIND_MAP_MAX_NODES,
  canAllocateMindMapNodeIds,
  type MindMapCanvasSelection,
  type MindMapDocument,
  type MindMapNodeStyle
} from "./model";
import {
  fromSimpleMindMapData,
  styleFromSimpleMindMapData,
  toSimpleMindMapData
} from "./simple-mind-map-adapter";
import {
  loadSimpleMindMapRuntime,
  type SimpleMindMapInstance,
  type SimpleMindMapNodeInstance
} from "./simple-mind-map-runtime";

const props = withDefaults(defineProps<{
  document: MindMapDocument;
  readonly?: boolean;
  generationKey?: string | number;
}>(), {
  readonly: false,
  generationKey: "default"
});

const emit = defineEmits<{
  change: [document: MindMapDocument];
  "selection-change": [selection?: MindMapCanvasSelection];
  status: [status: { loading: boolean; error?: string }];
}>();

const containerRef = ref<HTMLElement>();
const instance = shallowRef<SimpleMindMapInstance>();
const selectedNode = shallowRef<SimpleMindMapNodeInstance>();
const loading = ref(true);
const error = ref("");
let latestDocument = toRaw(props.document);
let observer: ResizeObserver | undefined;
let generation = 0;
let disposed = false;

const SINGLE_NODE_INSERT_COMMANDS = new Set([
  "INSERT_CHILD_NODE",
  "INSERT_NODE"
]);
const BLOCKED_NODE_COMMANDS = new Set([
  "SELECT_ALL",
  "INSERT_PARENT_NODE",
  "INSERT_MULTI_CHILD_NODE",
  "INSERT_MULTI_NODE"
]);

function safeError(value: unknown): string {
  return value instanceof Error && value.message
    ? `思维导图画布加载失败：${value.message}`
    : "思维导图画布加载失败，请重试";
}

function destroyInstance(): void {
  selectedNode.value = undefined;
  emit("selection-change", undefined);
  if (instance.value) {
    instance.value.destroy();
    instance.value = undefined;
  }
}

function emitDocumentChange(): void {
  if (!instance.value) return;
  try {
    latestDocument = fromSimpleMindMapData(instance.value.getData(), latestDocument);
    emit("change", latestDocument);
  } catch (value) {
    error.value = safeError(value);
    emit("status", { loading: false, error: error.value });
  }
}

function countDocumentNodes(document: MindMapDocument): number {
  let count = 0;
  const stack = [document.root];
  while (stack.length > 0) {
    const node = stack.pop()!;
    count += 1;
    stack.push(...node.children);
  }
  return count;
}

function insertionTargets(
  mindMap: SimpleMindMapInstance,
  command: string,
  args: unknown[]
): SimpleMindMapNodeInstance[] {
  const appointed = args[1];
  const candidates = Array.isArray(appointed)
    ? appointed.length > 0 ? appointed : mindMap.renderer.activeNodeList
    : appointed && typeof appointed === "object"
      ? [appointed]
      : mindMap.renderer.activeNodeList;
  return candidates.filter((value): value is SimpleMindMapNodeInstance => (
    Boolean(value)
    && typeof value === "object"
    && typeof (value as SimpleMindMapNodeInstance).getData === "function"
    && (command !== "INSERT_NODE" || !(value as SimpleMindMapNodeInstance).isRoot)
  ));
}

/**
 * 第三方快捷键和内部控件同样通过 execCommand 新增节点；统一在适配边界校验
 * 实时画布数据，避免它们绕过上层工具栏的 ID 容量门禁。
 */
function installNodeInsertGuard(mindMap: SimpleMindMapInstance): void {
  const execute = mindMap.execCommand.bind(mindMap);
  mindMap.execCommand = (command: string, ...args: unknown[]) => {
    // 首版不支持节点全选、批量插入和新增父节点，避免上游隐藏入口扩张产品能力。
    if (BLOCKED_NODE_COMMANDS.has(command)) return;
    if (SINGLE_NODE_INSERT_COMMANDS.has(command)) {
      try {
        const currentDocument = fromSimpleMindMapData(mindMap.getData(), latestDocument);
        const targets = insertionTargets(mindMap, command, args);
        if (targets.length === 0) return;
        // appointChildren 会一次插入额外子树，首版没有该入口，必须失败关闭。
        if (Array.isArray(args[3]) && args[3].length > 0) return;
        if (!canAllocateMindMapNodeIds(currentDocument, targets.length)) return;
        if (countDocumentNodes(currentDocument) + targets.length > MIND_MAP_MAX_NODES) return;
        if (
          command === "INSERT_CHILD_NODE"
          && targets.some((node) => !Number.isInteger(node.layerIndex) || node.layerIndex >= MIND_MAP_MAX_DEPTH)
        ) {
          return;
        }
      } catch {
        // 无法安全读取第三方数据时失败关闭，其他命令仍由既有错误边界处理。
        return;
      }
    }
    execute(command, ...args);
  };
}

function selectionFromNode(node: SimpleMindMapNodeInstance): MindMapCanvasSelection {
  const data = node.getData() as Record<string, unknown>;
  return {
    id: typeof data.uid === "string" ? data.uid : undefined,
    text: typeof data.text === "string" ? data.text : "",
    isRoot: node.isRoot,
    collapsed: !node.isRoot && data.expand === false,
    hasChildren: Boolean((node as unknown as { nodeData?: { children?: unknown[] } }).nodeData?.children?.length),
    style: styleFromSimpleMindMapData(data)
  };
}

function handleSelection(_node: unknown, list: unknown): void {
  const nodes = Array.isArray(list) ? list as SimpleMindMapNodeInstance[] : [];
  selectedNode.value = nodes.length === 1 ? nodes[0] : undefined;
  emit("selection-change", selectedNode.value ? selectionFromNode(selectedNode.value) : undefined);
}

async function initialize(): Promise<void> {
  const currentGeneration = ++generation;
  destroyInstance();
  loading.value = true;
  error.value = "";
  emit("status", { loading: true });
  await nextTick();
  try {
    const runtime = await loadSimpleMindMapRuntime();
    if (disposed || currentGeneration !== generation || !containerRef.value) return;
    latestDocument = toRaw(props.document);
    const mindMap = new runtime.MindMap({
      el: containerRef.value,
      data: toSimpleMindMapData(props.document),
      layout: "logicalStructure",
      theme: "default",
      readonly: props.readonly,
      disabledClipboard: true,
      enableCtrlKeyNodeSelection: false,
      enableFreeDrag: false,
      openPerformance: true,
      fit: true,
      richText: false,
      isShowCreateChildBtnIcon: false,
      createNewNodeBehavior: "activeOnly",
      defaultInsertSecondLevelNodeText: "新节点",
      defaultInsertBelowSecondLevelNodeText: "新节点",
      customHyperlinkJump: () => undefined,
      errorHandler: (_code: unknown, value: unknown) => {
        error.value = safeError(value);
        emit("status", { loading: false, error: error.value });
      }
    });
    if (disposed || currentGeneration !== generation) {
      mindMap.destroy();
      return;
    }
    installNodeInsertGuard(mindMap);
    mindMap.on("data_change", emitDocumentChange);
    mindMap.on("node_active", handleSelection);
    instance.value = mindMap;
    loading.value = false;
    emit("status", { loading: false });
  } catch (value) {
    if (disposed || currentGeneration !== generation) return;
    loading.value = false;
    error.value = safeError(value);
    emit("status", { loading: false, error: error.value });
  }
}

function runSelectedCommand(command: string, ...args: unknown[]): void {
  if (props.readonly || !instance.value || !selectedNode.value) return;
  instance.value.execCommand(command, ...args);
}

function addChild(): void {
  runSelectedCommand("INSERT_CHILD_NODE", false, [selectedNode.value], {
    text: "新节点",
    richText: false
  });
}

function addSibling(): void {
  if (selectedNode.value?.isRoot) return;
  runSelectedCommand("INSERT_NODE", false, [selectedNode.value], {
    text: "新节点",
    richText: false
  });
}

function removeSelected(): void {
  if (selectedNode.value?.isRoot) return;
  runSelectedCommand("REMOVE_NODE", [selectedNode.value]);
}

function setSelectedText(text: string): void {
  runSelectedCommand("SET_NODE_TEXT", selectedNode.value, text, false, true);
}

function setSelectedStyle(style: MindMapNodeStyle): void {
  if (!selectedNode.value) return;
  const canvasStyle: Record<string, unknown> = {};
  if (style.textColor !== undefined) canvasStyle.color = style.textColor;
  if (style.fillColor !== undefined) canvasStyle.fillColor = style.fillColor;
  if (style.borderColor !== undefined) canvasStyle.borderColor = style.borderColor;
  if (style.borderWidth !== undefined) canvasStyle.borderWidth = style.borderWidth;
  if (style.shape !== undefined) canvasStyle.shape = style.shape;
  if (style.fontSize !== undefined) canvasStyle.fontSize = style.fontSize;
  if (style.bold !== undefined) canvasStyle.fontWeight = style.bold ? "bold" : "normal";
  if (style.lineColor !== undefined) canvasStyle.lineColor = style.lineColor;
  if (style.lineWidth !== undefined) canvasStyle.lineWidth = style.lineWidth;
  if (style.lineDash !== undefined) canvasStyle.lineDasharray = style.lineDash === "dashed" ? "6,4" : "none";
  runSelectedCommand("SET_NODE_STYLES", selectedNode.value, canvasStyle);
}

function restoreSelectedStyle(): void {
  runSelectedCommand("REMOVE_CUSTOM_STYLES", selectedNode.value);
}

function toggleSelectedCollapse(): void {
  if (!selectedNode.value || selectedNode.value.isRoot) return;
  const data = selectedNode.value.getData() as Record<string, unknown>;
  runSelectedCommand("SET_NODE_EXPAND", selectedNode.value, data.expand === false);
}

function undo(): void {
  if (!props.readonly) instance.value?.execCommand("BACK");
}

function redo(): void {
  if (!props.readonly) instance.value?.execCommand("FORWARD");
}

function zoomIn(): void {
  instance.value?.view.enlarge();
}

function zoomOut(): void {
  instance.value?.view.narrow();
}

function fit(): void {
  instance.value?.view.fit();
}

defineExpose({
  addChild,
  addSibling,
  removeSelected,
  setSelectedText,
  setSelectedStyle,
  restoreSelectedStyle,
  toggleSelectedCollapse,
  undo,
  redo,
  zoomIn,
  zoomOut,
  fit
});

onMounted(() => {
  if (containerRef.value && typeof ResizeObserver !== "undefined") {
    observer = new ResizeObserver(() => instance.value?.resize());
    observer.observe(containerRef.value);
  }
  void initialize();
});

watch(
  () => [props.generationKey, props.document] as const,
  ([generationKey, document], [previousGenerationKey]) => {
    // data_change 上报的对象会经父级 ref 包装为响应式代理；toRaw 后仍是同一对象，
    // 因此可跳过画布自己的回声，只为文件代次或外部磁盘快照重建实例。
    if (generationKey === previousGenerationKey && toRaw(document) === latestDocument) return;
    void initialize();
  }
);
watch(() => props.readonly, (value) => instance.value?.setMode(value ? "readonly" : "edit"));

onBeforeUnmount(() => {
  disposed = true;
  generation += 1;
  observer?.disconnect();
  destroyInstance();
});
</script>

<template>
  <div class="ta-mind-map-canvas" :aria-busy="loading">
    <div ref="containerRef" class="ta-mind-map-canvas__surface" data-testid="mind-map-canvas" />
    <div v-if="loading" class="ta-mind-map-canvas__state">正在加载思维导图…</div>
    <div v-else-if="error" class="ta-mind-map-canvas__state ta-mind-map-canvas__state--error" role="alert">
      {{ error }}
    </div>
  </div>
</template>

<style scoped>
.ta-mind-map-canvas {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 240px;
  overflow: hidden;
  background: #fafafa;
}

.ta-mind-map-canvas__surface {
  width: 100%;
  height: 100%;
  min-height: inherit;
}

.ta-mind-map-canvas__state {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  color: #666;
  background: rgb(250 250 250 / 88%);
  font-size: 14px;
}

.ta-mind-map-canvas__state--error {
  color: #9f1239;
}
</style>
