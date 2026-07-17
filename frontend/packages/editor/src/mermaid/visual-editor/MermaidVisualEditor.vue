<script setup lang="ts">
import { computed, ref, type CSSProperties } from "vue";
import {
  ConnectionMode,
  Position,
  VueFlow,
  type EdgeMouseEvent,
  type NodeDragEvent,
  type NodeMouseEvent,
  type NodeChange,
  type EdgeChange
} from "@vue-flow/core";
import { autoLayoutMermaidGraph } from "../layout";
import {
  cloneMermaidGraph,
  type MermaidGraph,
  type MermaidNode,
  type MermaidNodeType,
  type MermaidPosition
} from "../model";
import { getMermaidNodeSize, MERMAID_NODE_SHAPES } from "../node-shapes";
import MermaidFlowNode from "./MermaidFlowNode.vue";
import MermaidFlowEdge from "./MermaidFlowEdge.vue";
import MermaidNodeShape from "./MermaidNodeShape.vue";
import MermaidColorField from "./MermaidColorField.vue";
import MermaidInlineEditor from "./MermaidInlineEditor.vue";
import { findEdgePort, oppositePosition, remapMermaidNodeEdgePorts } from "./node-port-layout";
import {
  useMermaidConnectionDrag,
  type MermaidConnectionStart
} from "./use-mermaid-connection-drag";
import { useMermaidNodeResize, type MermaidNodeResizeStart } from "./use-mermaid-node-resize";
import {
  appendMermaidEdge,
  applyVueFlowPositions,
  updateMermaidEdge,
  type MermaidPortConnection,
  toVueFlowEdges,
  toVueFlowNodes
} from "./vue-flow-adapter";

const props = defineProps<{ modelValue: MermaidGraph }>();
const emit = defineEmits<{ "update:modelValue": [graph: MermaidGraph] }>();

const nodeTypes = MERMAID_NODE_SHAPES;
const nodeGroups = [
  { key: "flowchart", label: "流程图", items: nodeTypes.filter((item) => item.group === "flowchart") },
  { key: "document", label: "文档与显示", items: nodeTypes.filter((item) => item.group === "document") }
] as const;
const nodeDragMime = "application/x-test-agent-mermaid-node";
const vueFlowRef = ref<{ screenToFlowCoordinate: (position: MermaidPosition) => MermaidPosition }>();
const canvasRef = ref<HTMLElement>();
const selectedNodeId = ref<string>();
const selectedEdgeId = ref<string>();
type InlineEditorState = {
  kind: "node" | "edge";
  id: string;
  text: string;
  textColor?: string;
  position: CSSProperties;
};
const inlineEditor = ref<InlineEditorState>();
const isCanvasDragOver = ref(false);
const resizePreviewGraph = ref<MermaidGraph>();
const displayedGraph = computed(() => resizePreviewGraph.value ?? props.modelValue);
const flowNodes = computed(() => toVueFlowNodes(displayedGraph.value));
/** 选中边需要高于 Vue Flow 节点层，端点圆圈才能接收重锚拖拽。 */
const flowEdges = computed(() => toVueFlowEdges(displayedGraph.value).map((edge) => {
  const selected = edge.id === selectedEdgeId.value;
  return { ...edge, selected, zIndex: selected ? 1001 : 0 };
}));
const selectedNode = computed(() => displayedGraph.value.nodes.find((node) => node.id === selectedNodeId.value));
const selectedEdge = computed(() => props.modelValue.edges.find((edge) => edge.id === selectedEdgeId.value));
const selectedNodeSize = computed(() => selectedNode.value ? getMermaidNodeSize(selectedNode.value) : undefined);
const selectedNodeScaleLabel = computed(() => `${Math.round((selectedNode.value?.scale ?? 1) * 1000) / 10}%`);

function inlineEditorPosition(clientX: number, clientY: number): CSSProperties {
  const width = Math.min(286, Math.max(1, window.innerWidth - 16));
  const height = 176;
  return {
    left: `${Math.round(Math.min(Math.max(clientX + 8, 8), window.innerWidth - width - 8))}px`,
    top: `${Math.round(Math.min(Math.max(clientY + 8, 8), window.innerHeight - height - 8))}px`
  };
}

function closeInlineEditor() {
  inlineEditor.value = undefined;
}

function updateGraph(updater: (draft: MermaidGraph) => void, options?: { preserveRoutes?: boolean }) {
  const draft = cloneMermaidGraph(props.modelValue);
  updater(draft);
  if (!options?.preserveRoutes) {
    for (const edge of draft.edges) delete edge.route;
  }
  emit("update:modelValue", draft);
}

function onNodeDragStop(event: NodeDragEvent) {
  emit("update:modelValue", applyVueFlowPositions(props.modelValue, [event.node]));
}

function commitConnection(connection: MermaidPortConnection) {
  const next = appendMermaidEdge(props.modelValue, connection);
  if (next !== props.modelValue) emit("update:modelValue", next);
}

/** 拖动已存在连线的端点重连到新节点/端口后，更新该边的对应端。 */
function commitReconnect(edgeId: string, end: "source" | "target", connection: MermaidPortConnection) {
  const next = updateMermaidEdge(props.modelValue, edgeId, end, connection);
  if (next !== props.modelValue) emit("update:modelValue", next);
}

const {
  isDragging,
  isReconnecting,
  sourceNodeId,
  sourceHandleId,
  targetNodeId,
  targetHandleId,
  targetStatus,
  dragPath,
  invalidReason,
  dragEndPoint,
  startConnection
} = useMermaidConnectionDrag({
  getCanvasElement: () => canvasRef.value,
  getGraph: () => props.modelValue,
  onConnect: commitConnection,
  onReconnect: commitReconnect
});

const { isResizing, startResize, cancelResize } = useMermaidNodeResize({
  getGraph: () => props.modelValue,
  screenToFlowCoordinate: (position) => vueFlowRef.value?.screenToFlowCoordinate(position) ?? position,
  onPreview: (graph) => { resizePreviewGraph.value = graph; },
  onCommit: (graph) => emit("update:modelValue", graph)
});

function onResizeStart(start: MermaidNodeResizeStart) {
  if (isDragging.value || inlineEditor.value) return;
  startResize(start);
}

function onNodeEditRequest(payload: { nodeId: string; clientX: number; clientY: number }) {
  if (isDragging.value) return;
  const node = props.modelValue.nodes.find((item) => item.id === payload.nodeId);
  if (!node) return;
  cancelResize();
  selectedNodeId.value = node.id;
  selectedEdgeId.value = undefined;
  inlineEditor.value = {
    kind: "node",
    id: node.id,
    text: node.text,
    textColor: node.style?.textColor,
    position: inlineEditorPosition(payload.clientX, payload.clientY)
  };
}

function onEdgeEditRequest(payload: { edgeId: string; clientX: number; clientY: number }) {
  if (isDragging.value) return;
  const edge = props.modelValue.edges.find((item) => item.id === payload.edgeId);
  if (!edge) return;
  cancelResize();
  selectedEdgeId.value = edge.id;
  selectedNodeId.value = undefined;
  inlineEditor.value = {
    kind: "edge",
    id: edge.id,
    text: edge.label,
    textColor: edge.style?.textColor,
    position: inlineEditorPosition(payload.clientX, payload.clientY)
  };
}

function cleanNodeStyle(node: MermaidNode) {
  if (node.style && !node.style.textColor && !node.style.fillColor && !node.style.strokeColor) delete node.style;
}

function commitInlineEditor(value: { text: string; textColor?: string }) {
  const state = inlineEditor.value;
  if (!state) return;
  closeInlineEditor();
  if (state.kind === "node") {
    const current = props.modelValue.nodes.find((node) => node.id === state.id);
    updateGraph((draft) => {
      const node = draft.nodes.find((item) => item.id === state.id);
      if (!node) return;
      node.text = value.text;
      node.style = { ...node.style, textColor: value.textColor };
      cleanNodeStyle(node);
    }, { preserveRoutes: current?.text === value.text });
    return;
  }
  updateGraph((draft) => {
    const edge = draft.edges.find((item) => item.id === state.id);
    if (!edge) return;
    edge.label = value.text;
    edge.style = { ...edge.style, textColor: value.textColor };
    if (!edge.style.textColor) delete edge.style;
  }, { preserveRoutes: true });
}

function onConnectionStart(start: MermaidConnectionStart) {
  startConnection(start);
}

/** 选中连线后拖动端点圆圈：测量固定端端口的屏幕坐标，再以该端为锚点启动重连拖拽。 */
function onReconnectStart(payload: {
  edgeId: string;
  end: "source" | "target";
  pointerId: number;
  fixedNodeId: string;
  fixedHandleId: string;
  fixedPosition: Position;
}) {
  const canvas = canvasRef.value;
  if (!canvas) return;
  const handleEl = canvas.querySelector<HTMLElement>(
    `[data-mermaid-node-id="${payload.fixedNodeId}"] [data-mermaid-handle="${payload.fixedHandleId}"]`
  );
  if (!handleEl) return;
  const rect = handleEl.getBoundingClientRect();
  startConnection(
    {
      pointerId: payload.pointerId,
      nodeId: payload.fixedNodeId,
      handleId: payload.fixedHandleId,
      position: payload.fixedPosition,
      point: { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 }
    },
    { reconnect: { edgeId: payload.edgeId, end: payload.end } }
  );
}

function onQuickConnect(payload: { nodeId?: string; portId: string; position: Position; shapeType: MermaidNodeType }) {
  const { nodeId: payloadNodeId, portId, position, shapeType } = payload;
  // nodeId 来自 MermaidFlowNode（被悬浮的节点）；mock 路径不带 nodeId 时回退到 selectedNodeId
  const nodeId = payloadNodeId ?? selectedNodeId.value;
  if (!nodeId) return;
  const sourceNode = props.modelValue.nodes.find((n) => n.id === nodeId);
  if (!sourceNode) return;

  let dx = 0;
  let dy = 0;
  if (position === Position.Right) dx = 190;
  else if (position === Position.Left) dx = -190;
  else if (position === Position.Bottom) dy = 140;
  else if (position === Position.Top) dy = -140;

  const newPosition = {
    x: sourceNode.position.x + dx,
    y: sourceNode.position.y + dy
  };

  updateGraph((draft) => {
    const used = new Set(draft.nodes.map((node) => node.id));
    let sequence = draft.nodes.length + 1;
    while (used.has(`N${sequence}`)) sequence += 1;
    const newId = `N${sequence}`;

    draft.nodes.push({
      id: newId,
      text: "新节点",
      type: shapeType,
      position: newPosition
    });

    // 起始点固定为被选中节点（箭头所在边的端口），新节点为目标，使箭头方向朝外、
    // 与快捷箭头指向一致。目标端口取新节点上朝向起点的对边端口，连线从起点边直达对边。
    const sourceHandle = portId || findEdgePort(sourceNode.type, position)?.handleId || "source-0";
    const targetHandle = findEdgePort(shapeType, oppositePosition(position))?.handleId ?? "target-0";

    const usedEdgeIds = new Set(draft.edges.map((e) => e.id));
    let edgeSeq = draft.edges.length + 1;
    while (usedEdgeIds.has(`edge-${edgeSeq}`)) edgeSeq += 1;
    const edgeId = `edge-${edgeSeq}`;

    draft.edges.push({
      id: edgeId,
      source: nodeId,
      target: newId,
      sourceHandle,
      targetHandle,
      label: "",
      relation: "arrow"
    });

    selectedNodeId.value = newId;
  });
}

function onNodeClick(event: NodeMouseEvent) {
  closeInlineEditor();
  selectedNodeId.value = event.node.id;
  selectedEdgeId.value = undefined;
}

/** 点击空白画布时取消选中，使半透明快捷箭头随选中态消失。 */
function onPaneClick() {
  cancelResize();
  closeInlineEditor();
  selectedNodeId.value = undefined;
  selectedEdgeId.value = undefined;
}

/** 选中连线时记录连线、取消节点选中，便于在属性面板编辑连线文字。 */
function onEdgeClick(event: EdgeMouseEvent) {
  closeInlineEditor();
  selectedEdgeId.value = event.edge.id;
  selectedNodeId.value = undefined;
}

function updateSelectedNode(patch: Partial<Pick<MermaidNode, "text" | "type">>) {
  if (!selectedNodeId.value) return;
  updateGraph((draft) => {
    const node = draft.nodes.find((item) => item.id === selectedNodeId.value);
    if (!node) return;
    if (patch.type && patch.type !== node.type) {
      remapMermaidNodeEdgePorts(draft, node.id, node.type, patch.type);
    }
    Object.assign(node, patch);
  });
}

function resetSelectedNodeScale() {
  if (!selectedNodeId.value) return;
  updateGraph((draft) => {
    const node = draft.nodes.find((item) => item.id === selectedNodeId.value);
    if (node) delete node.scale;
  });
}

function updateSelectedNodeColor(
  key: "textColor" | "fillColor" | "strokeColor",
  value: string | undefined
) {
  if (!selectedNodeId.value) return;
  updateGraph((draft) => {
    const node = draft.nodes.find((item) => item.id === selectedNodeId.value);
    if (!node || (node.type === "text" && key !== "textColor")) return;
    node.style = { ...node.style, [key]: value };
    cleanNodeStyle(node);
  }, { preserveRoutes: true });
}

/** 编辑选中连线的文字：输入即新增/修改，清空即删除文字（连线不再显示标签）。 */
function updateSelectedEdgeLabel(text: string) {
  if (!selectedEdgeId.value) return;
  updateGraph((draft) => {
    const edge = draft.edges.find((item) => item.id === selectedEdgeId.value);
    if (edge) edge.label = text;
  }, { preserveRoutes: true });
}

function updateSelectedEdgeTextColor(value: string | undefined) {
  if (!selectedEdgeId.value) return;
  updateGraph((draft) => {
    const edge = draft.edges.find((item) => item.id === selectedEdgeId.value);
    if (!edge) return;
    edge.style = { ...edge.style, textColor: value };
    if (!edge.style.textColor) delete edge.style;
  }, { preserveRoutes: true });
}

function deleteSelectedNode() {
  const nodeId = selectedNodeId.value;
  if (!nodeId) return;
  closeInlineEditor();
  updateGraph((draft) => {
    draft.nodes = draft.nodes.filter((node) => node.id !== nodeId);
    draft.edges = draft.edges.filter((edge) => edge.source !== nodeId && edge.target !== nodeId);
  });
  selectedNodeId.value = undefined;
}

function deleteSelectedEdge() {
  const edgeId = selectedEdgeId.value;
  if (!edgeId) return;
  closeInlineEditor();
  updateGraph((draft) => {
    draft.edges = draft.edges.filter((edge) => edge.id !== edgeId);
  });
  selectedEdgeId.value = undefined;
}

/** 在当前可见画布中心提供键盘和点击创建的确定性落点。 */
function getDefaultNodePosition(): MermaidPosition {
  const bounds = canvasRef.value?.getBoundingClientRect();
  if (!bounds?.width || !bounds.height) return { x: 80, y: 70 };
  return vueFlowRef.value?.screenToFlowCoordinate({
    x: bounds.left + bounds.width / 2,
    y: bounds.top + bounds.height / 2
  }) ?? { x: 80, y: 70 };
}

/** 统一创建图形库节点，确保 ID 唯一且新增后立即进入属性编辑态。 */
function createNode(type: MermaidNodeType, position = getDefaultNodePosition()) {
  updateGraph((draft) => {
    const used = new Set(draft.nodes.map((node) => node.id));
    let sequence = draft.nodes.length + 1;
    while (used.has(`N${sequence}`)) sequence += 1;
    const id = `N${sequence}`;
    draft.nodes.push({ id, text: "新节点", type, position });
    selectedNodeId.value = id;
  });
}

function isMermaidNodeType(value: string): value is MermaidNodeType {
  return nodeTypes.some((item) => item.type === value);
}

function getAddNodeAriaLabel(label: string): string {
  return `添加${label}${label.endsWith("节点") ? "" : "节点"}`;
}

function onPaletteDragStart(event: DragEvent, type: MermaidNodeType) {
  if (!event.dataTransfer) return;
  event.dataTransfer.effectAllowed = "copy";
  event.dataTransfer.setData(nodeDragMime, type);
  event.dataTransfer.setData("text/plain", type);
}

function onCanvasDragOver(event: DragEvent) {
  event.preventDefault();
  if (event.dataTransfer) event.dataTransfer.dropEffect = "copy";
  isCanvasDragOver.value = true;
}

function onCanvasDragLeave(event: DragEvent) {
  const canvas = event.currentTarget as HTMLElement;
  if (event.relatedTarget instanceof Node && canvas.contains(event.relatedTarget)) return;
  isCanvasDragOver.value = false;
}

/** 只接受图形库声明的节点类型，其他页面拖放内容不会污染 Mermaid 草稿。 */
function onCanvasDrop(event: DragEvent) {
  event.preventDefault();
  isCanvasDragOver.value = false;
  const type = event.dataTransfer?.getData(nodeDragMime) || event.dataTransfer?.getData("text/plain") || "";
  if (!isMermaidNodeType(type)) return;
  const position = vueFlowRef.value?.screenToFlowCoordinate({ x: event.clientX, y: event.clientY });
  if (position) createNode(type, position);
}

async function applyAutoLayout() {
  const nextGraph = await autoLayoutMermaidGraph(props.modelValue);
  emit("update:modelValue", nextGraph);
}

function updateDirection(event: Event) {
  const direction = (event.target as HTMLSelectElement).value as MermaidGraph["direction"];
  updateGraph((draft) => { draft.direction = direction; });
}

function onNodesChange(changes: NodeChange[]) {
  const removes = changes.filter((change) => change.type === "remove");
  if (removes.length === 0) return;
  closeInlineEditor();
  const removeIds = new Set(removes.map((change) => change.id));
  updateGraph((draft) => {
    draft.nodes = draft.nodes.filter((node) => !removeIds.has(node.id));
    draft.edges = draft.edges.filter((edge) => !removeIds.has(edge.source) && !removeIds.has(edge.target));
  });
  if (selectedNodeId.value && removeIds.has(selectedNodeId.value)) {
    selectedNodeId.value = undefined;
  }
  if (selectedEdgeId.value) {
    const edge = props.modelValue.edges.find((item) => item.id === selectedEdgeId.value);
    if (edge && (removeIds.has(edge.source) || removeIds.has(edge.target))) {
      selectedEdgeId.value = undefined;
    }
  }
}

function onEdgesChange(changes: EdgeChange[]) {
  const removes = changes.filter((change) => change.type === "remove");
  if (removes.length === 0) return;
  closeInlineEditor();
  const removeIds = new Set(removes.map((change) => change.id));
  updateGraph((draft) => {
    draft.edges = draft.edges.filter((edge) => !removeIds.has(edge.id));
  });
  if (selectedEdgeId.value && removeIds.has(selectedEdgeId.value)) {
    selectedEdgeId.value = undefined;
  }
}
</script>

<template>
  <div class="ta-mermaid-visual-editor">
    <div class="ta-mermaid-toolbar">
      <button type="button" @click="applyAutoLayout">自动布局</button>
      <label>
        <span>图方向</span>
        <select aria-label="图方向" :value="modelValue.direction" @change="updateDirection">
          <option value="TD">从上到下</option>
          <option value="BT">从下到上</option>
          <option value="LR">从左到右</option>
          <option value="RL">从右到左</option>
        </select>
      </label>
      <span class="ta-mermaid-toolbar__hint">悬浮节点显示连接点；按住连接点拖到目标节点</span>
    </div>

    <div class="ta-mermaid-workspace">
      <div
        ref="canvasRef"
        :class="['ta-mermaid-canvas', { 'is-drag-over': isCanvasDragOver, 'is-connection-dragging': isDragging, 'is-node-resizing': isResizing }]"
        aria-label="Mermaid 可视化画布"
        @dragover="onCanvasDragOver"
        @dragleave="onCanvasDragLeave"
        @drop="onCanvasDrop"
      >
        <VueFlow
          ref="vueFlowRef"
          :nodes="flowNodes"
          :edges="flowEdges"
          :min-zoom="0.35"
          :max-zoom="2"
          fit-view-on-init
          :nodes-connectable="false"
          :connect-on-click="false"
          :connection-mode="ConnectionMode.Loose"
          @nodes-change="onNodesChange"
          @edges-change="onEdgesChange"
          @node-drag-stop="onNodeDragStop"
          @node-click="onNodeClick"
          @edge-click="onEdgeClick"
          @pane-click="onPaneClick"
          @quick-connect-test="onQuickConnect"
        >
          <template #node-mermaid="nodeProps">
            <MermaidFlowNode
              v-bind="nodeProps"
              :selected="selectedNodeId === nodeProps.id"
              :connection-source-handle-id="sourceNodeId === nodeProps.id ? sourceHandleId : undefined"
              :is-connection-target="isDragging && targetNodeId === nodeProps.id"
              :snapped-handle-id="targetNodeId === nodeProps.id ? targetHandleId : undefined"
              :connection-status="targetNodeId === nodeProps.id ? targetStatus : undefined"
              :resize-enabled="!isDragging && !inlineEditor"
              @connection-start="onConnectionStart"
              @quick-connect="onQuickConnect"
              @resize-start="onResizeStart"
              @edit-request="onNodeEditRequest"
            />
          </template>
          <template #edge-mermaid-edge="edgeProps">
            <MermaidFlowEdge
              v-bind="edgeProps"
              @reconnect-start="onReconnectStart"
              @edit-request="onEdgeEditRequest"
            />
          </template>
        </VueFlow>
        <svg
          v-if="isDragging"
          class="ta-mermaid-connection-preview"
          aria-hidden="true"
        >
          <defs>
            <marker id="ta-mermaid-preview-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
              <path d="M0,0 L8,4 L0,8 Z" />
            </marker>
            <marker id="ta-mermaid-preview-arrow-invalid" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
              <path d="M0,0 L8,4 L0,8 Z" />
            </marker>
          </defs>
          <path
            :d="dragPath"
            :class="['ta-mermaid-connection-preview__path', { 'is-invalid': targetStatus === 'invalid', 'is-reconnect': isReconnecting }]"
            :marker-end="isReconnecting ? undefined : targetStatus === 'invalid' ? 'url(#ta-mermaid-preview-arrow-invalid)' : 'url(#ta-mermaid-preview-arrow)'"
          />
        </svg>
        <div
          v-if="isDragging && targetStatus === 'invalid' && invalidReason && dragEndPoint"
          class="ta-mermaid-connection-tooltip"
          :style="{
            left: `${dragEndPoint.x + 12}px`,
            top: `${dragEndPoint.y + 12}px`
          }"
        >
          {{ invalidReason }}
        </div>
      </div>

      <aside class="ta-mermaid-inspector" aria-label="图属性">
        <section class="ta-mermaid-palette">
          <h3>节点类型</h3>
          <div v-for="group in nodeGroups" :key="group.key" class="ta-mermaid-palette__group">
            <h4>{{ group.label }}</h4>
            <div class="ta-mermaid-palette__grid">
              <button
                v-for="item in group.items"
                :key="item.type"
                type="button"
                class="ta-mermaid-palette__item"
                :aria-label="getAddNodeAriaLabel(item.label)"
                draggable="true"
                @dragstart="onPaletteDragStart($event, item.type)"
                @click="createNode(item.type)"
              >
                <span class="ta-mermaid-palette__preview">
                  <MermaidNodeShape class="ta-mermaid-palette__shape" :type="item.type" />
                  <span class="ta-mermaid-palette__label">{{ item.label }}</span>
                </span>
              </button>
            </div>
          </div>
        </section>

        <section v-if="selectedNode" class="ta-mermaid-node-properties">
          <h3>当前节点</h3>
          <div class="ta-mermaid-fields">
            <div class="ta-mermaid-node-id-display">
              节点ID：{{ selectedNode.id }}
            </div>
            <label class="ta-mermaid-field-horizontal">
              <span>节点类型</span>
              <select aria-label="节点类型" :value="selectedNode.type" @change="updateSelectedNode({ type: ($event.target as HTMLSelectElement).value as MermaidNodeType })">
                <option v-for="item in nodeTypes" :key="item.type" :value="item.type">{{ item.label }}</option>
              </select>
            </label>
            <div class="ta-mermaid-size-row">
              <div class="ta-mermaid-size-summary" aria-label="节点尺寸">
                <span>缩放: <strong>{{ selectedNodeScaleLabel }}</strong></span>
                <span>尺寸: <strong>{{ selectedNodeSize?.width }} × {{ selectedNodeSize?.height }} px</strong></span>
              </div>
              <button type="button" class="ta-mermaid-reset-size-btn" :disabled="!selectedNode.scale" @click="resetSelectedNodeScale">恢复默认尺寸</button>
            </div>
            <MermaidColorField
              label="填充颜色"
              :model-value="selectedNode.style?.fillColor"
              :disabled="selectedNode.type === 'text'"
              @update:model-value="updateSelectedNodeColor('fillColor', $event)"
            />
            <MermaidColorField
              label="边框颜色"
              :model-value="selectedNode.style?.strokeColor"
              :disabled="selectedNode.type === 'text'"
              @update:model-value="updateSelectedNodeColor('strokeColor', $event)"
            />
            <p v-if="selectedNode.type === 'text'" class="ta-mermaid-field-hint">文本块没有可见填充或边框</p>
            <button type="button" class="is-danger" @click="deleteSelectedNode">删除节点</button>
          </div>
        </section>
        <section v-else-if="selectedEdge" class="ta-mermaid-edge-properties">
          <h3>当前连线</h3>
          <div class="ta-mermaid-fields">
            <div class="ta-mermaid-node-id-display">
              连线ID：{{ selectedEdge.id }}
            </div>
            <label>
              <span>连线文字</span>
              <textarea aria-label="连线文字" :value="selectedEdge.label" placeholder="为空则不显示文字" @input="updateSelectedEdgeLabel(($event.target as HTMLTextAreaElement).value)"></textarea>
            </label>
            <MermaidColorField
              label="连线文字颜色"
              :model-value="selectedEdge.style?.textColor"
              @update:model-value="updateSelectedEdgeTextColor"
            />
            <button type="button" class="is-danger" @click="deleteSelectedEdge">删除连线</button>
          </div>
        </section>
        <p v-else class="ta-mermaid-empty">选择画布中的节点或连线后编辑。</p>
      </aside>
    </div>
    <MermaidInlineEditor
      v-if="inlineEditor"
      :kind="inlineEditor.kind"
      :text="inlineEditor.text"
      :text-color="inlineEditor.textColor"
      :position="inlineEditor.position"
      @commit="commitInlineEditor"
      @cancel="closeInlineEditor"
    />
  </div>
</template>

<style>
@import "@vue-flow/core/dist/style.css";
@import "@vue-flow/core/dist/theme-default.css";
</style>

<style scoped>
.ta-mermaid-visual-editor { display: flex; min-height: 0; flex: 1; flex-direction: column; background: var(--ta-surface, #fff); color: var(--ta-text, #334155); }
.ta-mermaid-toolbar { display: flex; min-height: 44px; align-items: center; gap: 7px; border-bottom: 1px solid var(--ta-border, #e2e8f0); padding: 6px 10px; background: var(--ta-panel-2, #f8fafc); }
.ta-mermaid-toolbar button, .ta-mermaid-toolbar select, .ta-mermaid-inspector button, .ta-mermaid-inspector input, .ta-mermaid-inspector select, .ta-mermaid-inspector textarea { min-height: 28px; border: 1px solid var(--ta-border, #dbe2ea); border-radius: 5px; background: var(--ta-surface, #fff); color: var(--ta-ink, #172033); font: inherit; font-size: 12px; }
.ta-mermaid-toolbar button, .ta-mermaid-inspector button { padding: 0 9px; cursor: pointer; }
.ta-mermaid-toolbar button:hover, .ta-mermaid-inspector button:hover { border-color: var(--ta-border-strong, #94a3b8); background: var(--ta-hover, #f1f5f9); }
.ta-mermaid-toolbar button:focus-visible, .ta-mermaid-toolbar select:focus-visible, .ta-mermaid-inspector button:focus-visible, .ta-mermaid-inspector input:focus-visible, .ta-mermaid-inspector select:focus-visible { outline: 2px solid color-mix(in srgb, var(--primary, #4f46e5) 55%, transparent); outline-offset: 1px; }
.ta-mermaid-toolbar label { display: inline-flex; align-items: center; gap: 5px; font-size: 11px; color: var(--ta-muted, #64748b); }
.ta-mermaid-toolbar select { padding: 0 6px; }
.ta-mermaid-toolbar__hint { margin-left: auto; color: var(--ta-muted, #64748b); font-size: 11px; }
.ta-mermaid-workspace { display: grid; min-height: 0; flex: 1; grid-template-columns: minmax(0, 1fr) 280px; }
.ta-mermaid-canvas { position: relative; min-height: 420px; background-color: var(--ta-surface, #fff); background-image: radial-gradient(circle, color-mix(in srgb, var(--ta-border, #dbe2ea) 75%, transparent) 1px, transparent 1px); background-size: 18px 18px; transition: box-shadow 120ms ease; }
.ta-mermaid-canvas :deep(.vue-flow__edge-path) {
  stroke: #7e89a0;
}
.ta-mermaid-canvas :deep(.vue-flow__arrowhead path) {
  fill: #7e89a0;
  stroke: #7e89a0;
}
.ta-mermaid-canvas.is-drag-over { box-shadow: inset 0 0 0 2px color-mix(in srgb, var(--primary, #4f46e5) 65%, transparent); }
.ta-mermaid-canvas.is-connection-dragging :deep(.vue-flow__handle) { transition: none !important; }
.ta-mermaid-canvas :deep(.vue-flow) { height: 100%; min-height: 420px; }
.ta-mermaid-connection-preview { position: absolute; z-index: 4; inset: 0; width: 100%; height: 100%; overflow: visible; pointer-events: none; }
.ta-mermaid-connection-preview__path { fill: none; stroke: var(--primary, #4f46e5); stroke-width: 2.25; }
.ta-mermaid-connection-preview__path.is-invalid { stroke: #d92d20; }
.ta-mermaid-connection-preview__path.is-reconnect { stroke: #22c55e; stroke-dasharray: 5 4; }
.ta-mermaid-connection-preview #ta-mermaid-preview-arrow path { fill: var(--primary, #4f46e5); }
.ta-mermaid-connection-preview #ta-mermaid-preview-arrow-invalid path { fill: #d92d20; }
.ta-mermaid-connection-tooltip {
  position: absolute;
  z-index: 5;
  padding: 4px 8px;
  border: 1px solid #fda29b;
  border-radius: 4px;
  background: #fef3f2;
  color: #b42318;
  font-size: 11px;
  line-height: 1.4;
  white-space: nowrap;
  pointer-events: none;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.08);
}
.ta-mermaid-inspector { min-height: 0; overflow: auto; border-left: 1px solid var(--ta-border, #e2e8f0); background: var(--ta-panel-2, #f8fafc); }
.ta-mermaid-inspector section { padding: 13px; border-bottom: 1px solid var(--ta-border, #e2e8f0); }
.ta-mermaid-inspector h3 { margin: 0 0 10px; color: var(--ta-ink, #172033); font-size: 12px; font-weight: 700; }
.ta-mermaid-palette__group + .ta-mermaid-palette__group { margin-top: 12px; }
.ta-mermaid-palette__group h4 { margin: 0 0 6px; color: var(--ta-muted, #64748b); font-size: 10px; font-weight: 700; letter-spacing: 0.04em; }
.ta-mermaid-palette__grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 5px; }
.ta-mermaid-inspector button.ta-mermaid-palette__item { display: flex; min-width: 0; min-height: 42px; padding: 3px; align-items: center; justify-content: center; color: var(--ta-border-strong, #94a3b8); }
.ta-mermaid-inspector button.ta-mermaid-palette__item:hover { color: var(--ta-ink, #172033); }
.ta-mermaid-palette__preview { position: relative; display: grid; width: 70px; height: 34px; place-items: center; pointer-events: none; }
.ta-mermaid-palette__shape { position: absolute; left: 0; top: 4px; width: 70px; height: 26px; color: currentColor; --ta-mermaid-stroke-default: currentColor; }
.ta-mermaid-palette__shape[data-mermaid-shape="database"],
.ta-mermaid-palette__shape[data-mermaid-shape="doc"],
.ta-mermaid-palette__shape[data-mermaid-shape="docs"] { top: 1px; height: 32px; }
.ta-mermaid-palette__shape[data-mermaid-shape="diamond"],
.ta-mermaid-palette__shape[data-mermaid-shape="hexagon"],
.ta-mermaid-palette__shape[data-mermaid-shape="parallelogram"],
.ta-mermaid-palette__shape[data-mermaid-shape="trapezoid"] { top: 0; height: 34px; }
.ta-mermaid-palette__shape[data-mermaid-shape="circle"],
.ta-mermaid-palette__shape[data-mermaid-shape="double-circle"] { left: 18px; top: 0; width: 34px; height: 34px; }
.ta-mermaid-palette__label { position: relative; z-index: 1; overflow: hidden; max-width: 66px; color: var(--ta-muted, #64748b); font-size: 10px; line-height: 1.2; text-align: center; text-overflow: ellipsis; white-space: nowrap; transition: color 0.15s ease; }
.ta-mermaid-inspector button.ta-mermaid-palette__item:hover .ta-mermaid-palette__label { color: var(--ta-ink, #172033); }
.ta-mermaid-node-properties { min-height: 132px; }
.ta-mermaid-edge-properties { min-height: 132px; }
.ta-mermaid-fields { display: grid; gap: 8px; }
.ta-mermaid-node-id-display { color: var(--ta-muted, #64748b); font-size: 11px; font-weight: 500; }
.ta-mermaid-fields label { display: grid; gap: 3px; color: var(--ta-muted, #64748b); font-size: 10px; }
.ta-mermaid-field-horizontal { display: flex !important; flex-direction: row !important; align-items: center; justify-content: space-between; gap: 8px; }
.ta-mermaid-field-horizontal select { width: auto !important; flex: 1; min-width: 0; }
.ta-mermaid-fields input, .ta-mermaid-fields select, .ta-mermaid-fields textarea { width: 100%; padding: 4px 7px; }
.ta-mermaid-fields textarea { min-height: 48px; resize: vertical; padding: 6px 7px; }
.ta-mermaid-size-row { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.ta-mermaid-size-summary { display: flex; gap: 8px; color: var(--ta-muted, #64748b); font-size: 10px; }
.ta-mermaid-size-summary strong { color: var(--ta-ink, #172033); font-size: 11px; font-weight: 600; }
.ta-mermaid-reset-size-btn { padding: 0 6px !important; font-size: 10px !important; min-height: 20px !important; white-space: nowrap; border: 1px solid var(--ta-border, #dbe2ea); border-radius: 5px; background: var(--ta-surface, #fff); color: var(--ta-ink, #172033); cursor: pointer; }
.ta-mermaid-reset-size-btn:hover { border-color: var(--ta-border-strong, #94a3b8); background: var(--ta-hover, #f1f5f9); }
.ta-mermaid-reset-size-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.ta-mermaid-field-hint { margin: -2px 0 0; color: var(--ta-muted, #64748b); font-size: 10px; line-height: 1.3; }
.ta-mermaid-inspector button.is-danger { color: #b42318; }
.ta-mermaid-empty { margin: 0; color: var(--ta-muted, #64748b); font-size: 11px; line-height: 1.5; }
@media (max-width: 820px) { .ta-mermaid-workspace { grid-template-columns: 1fr; grid-template-rows: minmax(360px, 1fr) 240px; } .ta-mermaid-inspector { border-top: 1px solid var(--ta-border, #e2e8f0); border-left: 0; } .ta-mermaid-toolbar__hint { display: none; } }
@media (prefers-reduced-motion: reduce) { .ta-mermaid-visual-editor *, .ta-mermaid-visual-editor *::before, .ta-mermaid-visual-editor *::after { scroll-behavior: auto !important; transition-duration: 0.01ms !important; } }
</style>
