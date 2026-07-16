<script setup lang="ts">
import { computed, ref } from "vue";
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
import MermaidFlowNode from "./MermaidFlowNode.vue";
import MermaidFlowEdge from "./MermaidFlowEdge.vue";
import { findEdgePort, oppositePosition } from "./node-port-layout";
import {
  useMermaidConnectionDrag,
  type MermaidConnectionStart
} from "./use-mermaid-connection-drag";
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

const nodeTypes: ReadonlyArray<{ type: MermaidNodeType; label: string }> = [
  { type: "rectangle", label: "矩形" },
  { type: "rounded", label: "圆角" },
  { type: "stadium", label: "胶囊" },
  { type: "diamond", label: "判断" },
  { type: "circle", label: "圆形" }
];
const nodeDragMime = "application/x-test-agent-mermaid-node";
const vueFlowRef = ref<{ screenToFlowCoordinate: (position: MermaidPosition) => MermaidPosition }>();
const canvasRef = ref<HTMLElement>();
const selectedNodeId = ref<string>();
const selectedEdgeId = ref<string>();
const isCanvasDragOver = ref(false);
const flowNodes = computed(() => toVueFlowNodes(props.modelValue));
const flowEdges = computed(() => toVueFlowEdges(props.modelValue));
const selectedNode = computed(() => props.modelValue.nodes.find((node) => node.id === selectedNodeId.value));
const selectedEdge = computed(() => props.modelValue.edges.find((edge) => edge.id === selectedEdgeId.value));

function updateGraph(updater: (draft: MermaidGraph) => void) {
  const draft = cloneMermaidGraph(props.modelValue);
  updater(draft);
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
  selectedNodeId.value = event.node.id;
  selectedEdgeId.value = undefined;
}

/** 点击空白画布时取消选中，使半透明快捷箭头随选中态消失。 */
function onPaneClick() {
  selectedNodeId.value = undefined;
  selectedEdgeId.value = undefined;
}

/** 选中连线时记录连线、取消节点选中，便于在属性面板编辑连线文字。 */
function onEdgeClick(event: EdgeMouseEvent) {
  selectedEdgeId.value = event.edge.id;
  selectedNodeId.value = undefined;
}

function updateSelectedNode(patch: Partial<Pick<MermaidNode, "text" | "type">>) {
  if (!selectedNodeId.value) return;
  updateGraph((draft) => {
    const node = draft.nodes.find((item) => item.id === selectedNodeId.value);
    if (node) Object.assign(node, patch);
  });
}

/** 编辑选中连线的文字：输入即新增/修改，清空即删除文字（连线不再显示标签）。 */
function updateSelectedEdgeLabel(text: string) {
  if (!selectedEdgeId.value) return;
  updateGraph((draft) => {
    const edge = draft.edges.find((item) => item.id === selectedEdgeId.value);
    if (edge) edge.label = text;
  });
}

function deleteSelectedNode() {
  const nodeId = selectedNodeId.value;
  if (!nodeId) return;
  updateGraph((draft) => {
    draft.nodes = draft.nodes.filter((node) => node.id !== nodeId);
    draft.edges = draft.edges.filter((edge) => edge.source !== nodeId && edge.target !== nodeId);
  });
  selectedNodeId.value = undefined;
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

function applyAutoLayout() {
  emit("update:modelValue", autoLayoutMermaidGraph(props.modelValue));
}

function updateDirection(event: Event) {
  const direction = (event.target as HTMLSelectElement).value as MermaidGraph["direction"];
  updateGraph((draft) => { draft.direction = direction; });
}

function onNodesChange(changes: NodeChange[]) {
  const removes = changes.filter((change) => change.type === "remove");
  if (removes.length === 0) return;
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
        :class="['ta-mermaid-canvas', { 'is-drag-over': isCanvasDragOver, 'is-connection-dragging': isDragging }]"
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
              @connection-start="onConnectionStart"
              @quick-connect="onQuickConnect"
            />
          </template>
          <template #edge-mermaid-edge="edgeProps">
            <MermaidFlowEdge v-bind="edgeProps" @reconnect-start="onReconnectStart" />
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
          <p class="ta-mermaid-palette__hint">拖到画布创建节点，也可点击添加。</p>
          <div class="ta-mermaid-palette__grid">
            <button
              v-for="item in nodeTypes"
              :key="item.type"
              type="button"
              class="ta-mermaid-palette__item"
              :aria-label="`添加${item.label}节点`"
              draggable="true"
              @dragstart="onPaletteDragStart($event, item.type)"
              @click="createNode(item.type)"
            >
              <span :class="['ta-mermaid-palette__shape', `is-${item.type}`]" aria-hidden="true"></span>
              <span>{{ item.label }}</span>
            </button>
          </div>
        </section>

        <section v-if="selectedNode" class="ta-mermaid-node-properties">
          <h3>当前节点</h3>
          <div class="ta-mermaid-fields">
            <label>
              <span>节点 ID</span>
              <input :value="selectedNode.id" disabled />
            </label>
            <label>
              <span>节点名称</span>
              <input aria-label="节点名称" :value="selectedNode.text" @input="updateSelectedNode({ text: ($event.target as HTMLInputElement).value })" />
            </label>
            <label>
              <span>节点类型</span>
              <select aria-label="节点类型" :value="selectedNode.type" @change="updateSelectedNode({ type: ($event.target as HTMLSelectElement).value as MermaidNodeType })">
                <option value="rectangle">矩形</option>
                <option value="rounded">圆角</option>
                <option value="stadium">胶囊</option>
                <option value="diamond">判断</option>
                <option value="circle">圆形</option>
              </select>
            </label>
            <button type="button" class="is-danger" @click="deleteSelectedNode">删除节点</button>
          </div>
        </section>
        <section v-else-if="selectedEdge" class="ta-mermaid-edge-properties">
          <h3>当前连线</h3>
          <div class="ta-mermaid-fields">
            <label>
              <span>连线 ID</span>
              <input :value="selectedEdge.id" disabled />
            </label>
            <label>
              <span>连线文字</span>
              <input aria-label="连线文字" :value="selectedEdge.label" placeholder="为空则不显示文字" @input="updateSelectedEdgeLabel(($event.target as HTMLInputElement).value)" />
            </label>
          </div>
        </section>
        <p v-else class="ta-mermaid-empty">选择画布中的节点或连线后编辑。</p>
      </aside>
    </div>
  </div>
</template>

<style>
@import "@vue-flow/core/dist/style.css";
@import "@vue-flow/core/dist/theme-default.css";
</style>

<style scoped>
.ta-mermaid-visual-editor { display: flex; min-height: 0; flex: 1; flex-direction: column; background: var(--ta-surface, #fff); color: var(--ta-text, #334155); }
.ta-mermaid-toolbar { display: flex; min-height: 44px; align-items: center; gap: 7px; border-bottom: 1px solid var(--ta-border, #e2e8f0); padding: 6px 10px; background: var(--ta-panel-2, #f8fafc); }
.ta-mermaid-toolbar button, .ta-mermaid-toolbar select, .ta-mermaid-inspector button, .ta-mermaid-inspector input, .ta-mermaid-inspector select { min-height: 28px; border: 1px solid var(--ta-border, #dbe2ea); border-radius: 5px; background: var(--ta-surface, #fff); color: var(--ta-ink, #172033); font: inherit; font-size: 12px; }
.ta-mermaid-toolbar button, .ta-mermaid-inspector button { padding: 0 9px; cursor: pointer; }
.ta-mermaid-toolbar button:hover, .ta-mermaid-inspector button:hover { border-color: var(--ta-border-strong, #94a3b8); background: var(--ta-hover, #f1f5f9); }
.ta-mermaid-toolbar button:focus-visible, .ta-mermaid-toolbar select:focus-visible, .ta-mermaid-inspector button:focus-visible, .ta-mermaid-inspector input:focus-visible, .ta-mermaid-inspector select:focus-visible { outline: 2px solid color-mix(in srgb, var(--primary, #4f46e5) 55%, transparent); outline-offset: 1px; }
.ta-mermaid-toolbar label { display: inline-flex; align-items: center; gap: 5px; font-size: 11px; color: var(--ta-muted, #64748b); }
.ta-mermaid-toolbar select { padding: 0 6px; }
.ta-mermaid-toolbar__hint { margin-left: auto; color: var(--ta-muted, #64748b); font-size: 11px; }
.ta-mermaid-workspace { display: grid; min-height: 0; flex: 1; grid-template-columns: minmax(0, 1fr) 280px; }
.ta-mermaid-canvas { position: relative; min-height: 420px; background-color: var(--ta-surface, #fff); background-image: radial-gradient(circle, color-mix(in srgb, var(--ta-border, #dbe2ea) 75%, transparent) 1px, transparent 1px); background-size: 18px 18px; transition: box-shadow 120ms ease; }
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
.ta-mermaid-palette__hint { margin: -4px 0 10px; color: var(--ta-muted, #64748b); font-size: 10px; line-height: 1.45; }
.ta-mermaid-palette__grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 7px; }
.ta-mermaid-inspector button.ta-mermaid-palette__item { display: grid; min-height: 76px; place-items: center; align-content: center; gap: 7px; padding: 8px 5px; color: var(--ta-muted, #64748b); }
.ta-mermaid-inspector button.ta-mermaid-palette__item:hover { color: var(--ta-ink, #172033); }
.ta-mermaid-palette__shape { display: block; width: 72px; height: 30px; border: 1.5px solid currentColor; border-radius: 2px; background: var(--ta-surface, #fff); color: var(--ta-border-strong, #94a3b8); pointer-events: none; }
.ta-mermaid-palette__shape.is-rounded { border-radius: 10px; }
.ta-mermaid-palette__shape.is-stadium { width: 80px; border-radius: 999px; }
.ta-mermaid-palette__shape.is-diamond {
  position: relative;
  width: 72px;
  height: 38px;
  border: 0;
  background: transparent;
}
.ta-mermaid-palette__shape.is-diamond::before,
.ta-mermaid-palette__shape.is-diamond::after {
  position: absolute;
  content: "";
  clip-path: polygon(50% 0, 100% 50%, 50% 100%, 0 50%);
}
.ta-mermaid-palette__shape.is-diamond::before { inset: 0; background: currentColor; }
.ta-mermaid-palette__shape.is-diamond::after { inset: 1.5px; background: var(--ta-surface, #fff); }
.ta-mermaid-palette__shape.is-circle { width: 38px; height: 38px; border-radius: 50%; }
.ta-mermaid-node-properties { min-height: 132px; }
.ta-mermaid-edge-properties { min-height: 132px; }
.ta-mermaid-fields { display: grid; gap: 8px; }
.ta-mermaid-fields label { display: grid; gap: 3px; color: var(--ta-muted, #64748b); font-size: 10px; }
.ta-mermaid-fields input, .ta-mermaid-fields select { width: 100%; padding: 4px 7px; }
.ta-mermaid-inspector button.is-danger { color: #b42318; }
.ta-mermaid-empty { margin: 0; color: var(--ta-muted, #64748b); font-size: 11px; line-height: 1.5; }
@media (max-width: 820px) { .ta-mermaid-workspace { grid-template-columns: 1fr; grid-template-rows: minmax(360px, 1fr) 240px; } .ta-mermaid-inspector { border-top: 1px solid var(--ta-border, #e2e8f0); border-left: 0; } .ta-mermaid-toolbar__hint { display: none; } }
@media (prefers-reduced-motion: reduce) { .ta-mermaid-visual-editor *, .ta-mermaid-visual-editor *::before, .ta-mermaid-visual-editor *::after { scroll-behavior: auto !important; transition-duration: 0.01ms !important; } }
</style>
