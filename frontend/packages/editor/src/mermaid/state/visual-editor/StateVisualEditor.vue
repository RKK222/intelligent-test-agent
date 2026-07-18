<script setup lang="ts">
import { computed, ref, watch, type CSSProperties } from "vue";
import { ConnectionMode, Position, VueFlow, type EdgeMouseEvent, type NodeDragEvent } from "@vue-flow/core";
import { autoLayoutMermaidState } from "../layout";
import {
  cloneMermaidStateDiagram,
  createMermaidStateRegion,
  createMermaidStateScope,
  flattenMermaidStateNodes,
  flattenMermaidStateScopes,
  flattenMermaidStateTransitions,
  indexMermaidStateNodes,
  type MermaidStateDiagram,
  type MermaidStateNode,
  type MermaidStateNodeKind,
  type MermaidStateRegion,
  type MermaidStateScope,
  type MermaidStateStyle
} from "../model";
import { isMermaidStateId } from "../parser";
import {
  getMermaidStateConnectionInvalidReason,
  validateMermaidState
} from "../validator";
import MermaidColorField from "../../visual-editor/MermaidColorField.vue";
import {
  useMermaidConnectionDrag,
  type MermaidConnectionStart
} from "../../visual-editor/use-mermaid-connection-drag";
import type { MermaidPortConnection } from "../../visual-editor/vue-flow-adapter";
import { toStateFlowScene } from "./adapter";
import StateNode from "./StateNode.vue";
import StateNoteNode from "./StateNoteNode.vue";
import StateRegionNode from "./StateRegionNode.vue";
import StateTransitionEdge from "./StateTransitionEdge.vue";

const props = defineProps<{ modelValue: MermaidStateDiagram }>();
const emit = defineEmits<{ "update:modelValue": [diagram: MermaidStateDiagram] }>();

type PaletteKind = MermaidStateNodeKind | "composite" | "note";
type InlineEdit = {
  kind: "node" | "transition";
  id: string;
  text: string;
  position: CSSProperties;
};

const NODE_DRAG_MIME = "application/x-test-agent-mermaid-state-node";
const working = ref(cloneMermaidStateDiagram(props.modelValue));
const focusScopeId = ref(working.value.root.id);
const selectedRegionId = ref(working.value.root.regions[0]?.id);
const selectedNodeId = ref<string>();
const selectedNoteId = ref<string>();
const selectedTransitionId = ref<string>();
const inlineEdit = ref<InlineEdit>();
const canvasRef = ref<HTMLElement>();
const vueFlowRef = ref<{ fitView?: () => void; screenToFlowCoordinate?: (point: { x: number; y: number }) => { x: number; y: number } }>();
const isCanvasDragOver = ref(false);
const draggedPaletteKind = ref<PaletteKind>();

watch(() => props.modelValue, (value) => {
  working.value = cloneMermaidStateDiagram(value);
  if (!flattenMermaidStateScopes(working.value).some((scope) => scope.id === focusScopeId.value)) {
    focusScopeId.value = working.value.root.id;
  }
});

function findScope(diagram: MermaidStateDiagram, scopeId: string): MermaidStateScope | undefined {
  return flattenMermaidStateScopes(diagram).find((scope) => scope.id === scopeId);
}

function findRegion(scope: MermaidStateScope, regionId: string | undefined): MermaidStateRegion {
  return scope.regions.find((region) => region.id === regionId) ?? scope.regions[0]!;
}

function scopeTrail(diagram: MermaidStateDiagram, targetId: string) {
  const visit = (
    scope: MermaidStateScope,
    trail: Array<{ id: string; label: string }>
  ): Array<{ id: string; label: string }> | undefined => {
    if (scope.id === targetId) return trail;
    for (const region of scope.regions) {
      for (const node of region.nodes) {
        if (!node.childScope) continue;
        const nested = visit(node.childScope, [...trail, { id: node.childScope.id, label: node.label || node.id }]);
        if (nested) return nested;
      }
    }
    return undefined;
  };
  return visit(diagram.root, [{ id: diagram.root.id, label: "根状态图" }]) ?? [{ id: diagram.root.id, label: "根状态图" }];
}

const currentScope = computed(() => findScope(working.value, focusScopeId.value) ?? working.value.root);
const breadcrumbs = computed(() => scopeTrail(working.value, currentScope.value.id));
const scene = computed(() => toStateFlowScene(currentScope.value, selectedRegionId.value));
const flowNodes = computed(() => scene.value.nodes.map((node) => ({
  ...node,
  selected: node.id === selectedNodeId.value
})));
const flowEdges = computed(() => scene.value.edges.map((edge) => ({
  ...edge,
  selected: edge.id === selectedTransitionId.value,
  zIndex: edge.id === selectedTransitionId.value ? 1001 : edge.zIndex
})));
const validationIssues = computed(() => validateMermaidState(working.value));
const selectedNode = computed(() => selectedNodeId.value
  ? indexMermaidStateNodes(working.value).get(selectedNodeId.value)?.node
  : undefined);
const selectedNote = computed(() => currentScope.value.regions
  .flatMap((region) => region.notes)
  .find((note) => note.id === selectedNoteId.value));
const selectedTransition = computed(() => currentScope.value.regions
  .flatMap((region) => region.transitions)
  .find((transition) => transition.id === selectedTransitionId.value));
const selectedNoteRegion = computed(() => currentScope.value.regions.find((region) =>
  region.notes.some((note) => note.id === selectedNoteId.value)
));

const paletteGroups = [
  {
    label: "状态",
    items: [
      { kind: "state" as const, label: "新增普通状态" },
      { kind: "composite" as const, label: "新增复合状态" }
    ]
  },
  {
    label: "伪状态",
    items: [
      { kind: "start" as const, label: "新增开始" },
      { kind: "end" as const, label: "新增结束" },
      { kind: "choice" as const, label: "新增 Choice" },
      { kind: "fork" as const, label: "新增 Fork" },
      { kind: "join" as const, label: "新增 Join" }
    ]
  },
  { label: "说明", items: [{ kind: "note" as const, label: "新增 Note" }] }
];

function publish(next: MermaidStateDiagram) {
  working.value = next;
  emit("update:modelValue", cloneMermaidStateDiagram(next));
}

function updateDiagram(updater: (draft: MermaidStateDiagram) => void) {
  const draft = cloneMermaidStateDiagram(working.value);
  updater(draft);
  publish(draft);
}

function focusScope(scopeId: string) {
  const scope = findScope(working.value, scopeId);
  if (!scope) return;
  focusScopeId.value = scope.id;
  selectedRegionId.value = scope.regions[0]?.id;
  selectedNodeId.value = undefined;
  selectedNoteId.value = undefined;
  selectedTransitionId.value = undefined;
  inlineEdit.value = undefined;
}

function selectNode(nodeId: string) {
  const entry = indexMermaidStateNodes(working.value).get(nodeId);
  if (!entry) return;
  selectedNodeId.value = nodeId;
  selectedRegionId.value = entry.region.id;
  selectedNoteId.value = undefined;
  selectedTransitionId.value = undefined;
}

function selectNote(noteId: string) {
  const region = currentScope.value.regions.find((item) => item.notes.some((note) => note.id === noteId));
  if (!region) return;
  selectedNoteId.value = noteId;
  selectedRegionId.value = region.id;
  selectedNodeId.value = undefined;
  selectedTransitionId.value = undefined;
}

function selectTransition(transitionId: string) {
  const region = currentScope.value.regions.find((item) => item.transitions.some((transition) => transition.id === transitionId));
  if (!region) return;
  selectedTransitionId.value = transitionId;
  selectedRegionId.value = region.id;
  selectedNodeId.value = undefined;
  selectedNoteId.value = undefined;
}

function nextNamedId(diagram: MermaidStateDiagram, prefix: string): string {
  const used = new Set(flattenMermaidStateNodes(diagram).map((node) => node.id));
  let sequence = 1;
  while (used.has(`${prefix}${sequence}`)) sequence += 1;
  return `${prefix}${sequence}`;
}

function nextEntityId(existing: readonly string[], prefix: string): string {
  const used = new Set(existing);
  let sequence = 1;
  while (used.has(`${prefix}-${sequence}`)) sequence += 1;
  return `${prefix}-${sequence}`;
}

function addPaletteElement(kind: PaletteKind, position?: { x: number; y: number }) {
  updateDiagram((draft) => {
    const scope = findScope(draft, focusScopeId.value) ?? draft.root;
    const region = findRegion(scope, selectedRegionId.value);
    selectedRegionId.value = region.id;
    if (kind === "note") {
      const selectedTarget = selectedNodeId.value
        ? region.nodes.find((node) => node.id === selectedNodeId.value && node.kind === "state")
        : undefined;
      const target = selectedTarget ?? region.nodes.find((node) => node.kind === "state");
      if (!target) return;
      const id = nextEntityId(scope.regions.flatMap((item) => item.notes.map((note) => note.id)), "note");
      region.notes.push({ id, target: target.id, placement: "right", text: "新说明" });
      selectedNoteId.value = id;
      selectedNodeId.value = undefined;
      return;
    }

    const namedPrefix = kind === "state" ? "State"
      : kind === "composite" ? "Composite"
        : kind === "choice" ? "Choice"
          : kind === "fork" ? "Fork"
            : kind === "join" ? "Join" : kind === "start" ? "Start" : "End";
    const id = nextNamedId(draft, namedPrefix);
    const nodeKind: MermaidStateNodeKind = kind === "composite" ? "state" : kind;
    const node: MermaidStateNode = {
      id,
      kind: nodeKind,
      label: nodeKind === "state" ? (kind === "composite" ? "新复合状态" : "新状态") : nodeKind === "start" || nodeKind === "end" ? "[*]" : id,
      descriptions: [],
      position: position ?? { x: 52 + region.nodes.length * 26, y: 62 + region.nodes.length * 20 },
      declared: true,
      ...(kind === "composite" ? { childScope: createMermaidStateScope(`${id}-scope`) } : {})
    };
    region.nodes.push(node);
    selectedNodeId.value = id;
    selectedNoteId.value = undefined;
  });
}

function addConcurrentRegion() {
  updateDiagram((draft) => {
    const scope = findScope(draft, focusScopeId.value) ?? draft.root;
    const region = createMermaidStateRegion(`${scope.id}-region-${scope.regions.length + 1}`);
    scope.regions.push(region);
    selectedRegionId.value = region.id;
  });
}

function updateDirection(value: string) {
  if (!/^(?:TB|BT|LR|RL)$/.test(value)) return;
  updateDiagram((draft) => {
    const scope = findScope(draft, focusScopeId.value) ?? draft.root;
    scope.direction = value as MermaidStateScope["direction"];
  });
}

async function autoLayout() {
  const snapshot = working.value;
  const laidOut = await autoLayoutMermaidState(snapshot);
  if (working.value === snapshot) publish(laidOut);
}

function onNodeDragStop(event: NodeDragEvent) {
  updateDiagram((draft) => {
    const entry = indexMermaidStateNodes(draft).get(event.node.id);
    if (!entry || entry.scope.id !== focusScopeId.value) return;
    entry.node.position = { x: event.node.position.x, y: event.node.position.y };
  });
}

function onDragStart(event: DragEvent, kind: PaletteKind) {
  draggedPaletteKind.value = kind;
  event.dataTransfer?.setData(NODE_DRAG_MIME, kind);
  event.dataTransfer?.setData("text/plain", kind);
  if (event.dataTransfer) event.dataTransfer.effectAllowed = "copy";
}

function onDrop(event: DragEvent) {
  isCanvasDragOver.value = false;
  const kind = (event.dataTransfer?.getData(NODE_DRAG_MIME)
    || event.dataTransfer?.getData("text/plain")
    || draggedPaletteKind.value) as PaletteKind | undefined;
  draggedPaletteKind.value = undefined;
  if (!kind || !["state", "composite", "start", "end", "choice", "fork", "join", "note"].includes(kind)) return;
  const point = vueFlowRef.value?.screenToFlowCoordinate?.({ x: event.clientX, y: event.clientY });
  addPaletteElement(kind, point ? { x: Math.max(20, point.x), y: Math.max(20, point.y) } : undefined);
}

function commitConnection(connection: MermaidPortConnection) {
  updateDiagram((draft) => {
    if (!connection.source || !connection.target) return;
    const entry = indexMermaidStateNodes(draft).get(connection.source);
    if (!entry) return;
    const id = nextEntityId(flattenMermaidStateTransitions(draft).map((transition) => transition.id), "transition");
    entry.region.transitions.push({
      id,
      source: connection.source,
      target: connection.target,
      sourceHandle: connection.sourceHandle ?? undefined,
      targetHandle: connection.targetHandle ?? undefined,
      label: ""
    });
    selectedTransitionId.value = id;
  });
}

function commitReconnect(
  transitionId: string,
  end: "source" | "target",
  connection: MermaidPortConnection
) {
  updateDiagram((draft) => {
    const transition = flattenMermaidStateTransitions(draft).find((item) => item.id === transitionId);
    if (!transition) return;
    if (end === "target") {
      transition.target = connection.target ?? transition.target;
      transition.targetHandle = connection.targetHandle ?? transition.targetHandle;
    } else {
      transition.source = connection.source ?? transition.source;
      transition.sourceHandle = connection.sourceHandle ?? transition.sourceHandle;
    }
  });
}

const {
  isDragging,
  invalidReason,
  dragPath,
  startConnection
} = useMermaidConnectionDrag<MermaidStateDiagram>({
  getCanvasElement: () => canvasRef.value,
  getGraph: () => working.value,
  getConnectionInvalidReason: (connection, excludeTransitionId) => {
    const source = connection.source ? indexMermaidStateNodes(working.value).get(connection.source) : undefined;
    if (!source) return connection.source ? "连接端点不存在" : undefined;
    return getMermaidStateConnectionInvalidReason(
      working.value,
      source.region.id,
      connection,
      excludeTransitionId
    );
  },
  onConnect: commitConnection,
  onReconnect: commitReconnect
});

function onConnectionStart(start: MermaidConnectionStart) {
  startConnection(start);
}

function onReconnectStart(payload: {
  transitionId: string;
  end: "source" | "target";
  pointerId: number;
  fixedNodeId: string;
  fixedHandleId: string;
  fixedPosition: Position;
}) {
  const fixedHandle = Array.from(canvasRef.value?.querySelectorAll<HTMLElement>("[data-mermaid-handle]") ?? [])
    .find((element) => element.dataset.mermaidHandle === payload.fixedHandleId
      && element.closest<HTMLElement>("[data-mermaid-node-id]")?.dataset.mermaidNodeId === payload.fixedNodeId);
  if (!fixedHandle) return;
  const rect = fixedHandle.getBoundingClientRect();
  startConnection({
    pointerId: payload.pointerId,
    nodeId: payload.fixedNodeId,
    handleId: payload.fixedHandleId,
    position: payload.fixedPosition,
    point: { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 }
  }, { reconnect: { edgeId: payload.transitionId, end: payload.end } });
}

function inlinePosition(clientX: number, clientY: number): CSSProperties {
  return {
    left: `${Math.max(8, Math.min(clientX + 8, window.innerWidth - 278))}px`,
    top: `${Math.max(8, Math.min(clientY + 8, window.innerHeight - 150))}px`
  };
}

function openNodeEditor(payload: { nodeId: string; clientX: number; clientY: number }) {
  const node = indexMermaidStateNodes(working.value).get(payload.nodeId)?.node;
  if (!node) return;
  selectNode(node.id);
  inlineEdit.value = { kind: "node", id: node.id, text: node.label, position: inlinePosition(payload.clientX, payload.clientY) };
}

function openTransitionEditor(payload: { transitionId: string; clientX: number; clientY: number }) {
  const transition = flattenMermaidStateTransitions(working.value).find((item) => item.id === payload.transitionId);
  if (!transition) return;
  selectTransition(transition.id);
  inlineEdit.value = {
    kind: "transition",
    id: transition.id,
    text: transition.label,
    position: inlinePosition(payload.clientX, payload.clientY)
  };
}

function commitInlineEdit() {
  const state = inlineEdit.value;
  if (!state) return;
  updateDiagram((draft) => {
    if (state.kind === "node") {
      const node = indexMermaidStateNodes(draft).get(state.id)?.node;
      if (node) node.label = state.text.trim() || node.id;
    } else {
      const transition = flattenMermaidStateTransitions(draft).find((item) => item.id === state.id);
      if (transition) transition.label = state.text.trim();
    }
  });
  inlineEdit.value = undefined;
}

function onInlineKeydown(event: KeyboardEvent) {
  if (event.key === "Escape") inlineEdit.value = undefined;
  else if (event.key === "Enter" && (event.ctrlKey || event.metaKey)) {
    event.preventDefault();
    commitInlineEdit();
  }
}

function updateNodeId(value: string) {
  const current = selectedNode.value;
  if (!current || current.kind === "start" || current.kind === "end" || !isMermaidStateId(value)) return;
  const duplicate = flattenMermaidStateNodes(working.value).some((node) => node.id === value && node.id !== current.id);
  if (duplicate) return;
  const previous = current.id;
  updateDiagram((draft) => {
    const node = indexMermaidStateNodes(draft).get(previous)?.node;
    if (!node) return;
    node.id = value;
    for (const scope of flattenMermaidStateScopes(draft)) {
      for (const region of scope.regions) {
        for (const transition of region.transitions) {
          if (transition.source === previous) transition.source = value;
          if (transition.target === previous) transition.target = value;
        }
        for (const note of region.notes) if (note.target === previous) note.target = value;
      }
    }
  });
  selectedNodeId.value = value;
}

function updateSelectedNode(updater: (node: MermaidStateNode) => void) {
  const id = selectedNodeId.value;
  if (!id) return;
  updateDiagram((draft) => {
    const node = indexMermaidStateNodes(draft).get(id)?.node;
    if (node) updater(node);
  });
}

function updateDescriptions(value: string) {
  updateSelectedNode((node) => {
    node.descriptions = value.replaceAll("\r", "").split("\n").map((line) => line.trim()).filter(Boolean);
  });
}

function updateNodeStyle(key: keyof MermaidStateStyle, value: string | undefined) {
  updateSelectedNode((node) => {
    node.style = { ...node.style, [key]: value };
    if (!node.style.textColor && !node.style.fillColor && !node.style.strokeColor) delete node.style;
  });
}

function updateNote(field: "target" | "placement" | "text", value: string) {
  const id = selectedNoteId.value;
  if (!id) return;
  updateDiagram((draft) => {
    const scope = findScope(draft, focusScopeId.value) ?? draft.root;
    const note = scope.regions.flatMap((region) => region.notes).find((item) => item.id === id);
    if (!note) return;
    if (field === "placement" && (value === "left" || value === "right")) note.placement = value;
    else if (field !== "placement") note[field] = value;
  });
}

function deleteSelection() {
  if (selectedNodeId.value) {
    const id = selectedNodeId.value;
    updateDiagram((draft) => {
      const entry = indexMermaidStateNodes(draft).get(id);
      if (!entry) return;
      entry.region.nodes = entry.region.nodes.filter((node) => node.id !== id);
      entry.region.transitions = entry.region.transitions.filter((transition) => transition.source !== id && transition.target !== id);
      entry.region.notes = entry.region.notes.filter((note) => note.target !== id);
    });
    selectedNodeId.value = undefined;
  } else if (selectedNoteId.value) {
    const id = selectedNoteId.value;
    updateDiagram((draft) => {
      for (const scope of flattenMermaidStateScopes(draft)) {
        for (const region of scope.regions) region.notes = region.notes.filter((note) => note.id !== id);
      }
    });
    selectedNoteId.value = undefined;
  } else if (selectedTransitionId.value) {
    const id = selectedTransitionId.value;
    updateDiagram((draft) => {
      for (const scope of flattenMermaidStateScopes(draft)) {
        for (const region of scope.regions) {
          region.transitions = region.transitions.filter((transition) => transition.id !== id);
        }
      }
    });
    selectedTransitionId.value = undefined;
  }
}

function onEdgeClick(event: EdgeMouseEvent) {
  selectTransition(event.edge.id);
}
</script>

<template>
  <div class="ta-state-editor">
    <aside class="ta-state-palette" aria-label="State diagram 元素库">
      <section v-for="group in paletteGroups" :key="group.label">
        <h3>{{ group.label }}</h3>
        <button
          v-for="item in group.items"
          :key="item.kind"
          type="button"
          draggable="true"
          :aria-label="item.label"
          @click="addPaletteElement(item.kind)"
          @dragstart="onDragStart($event, item.kind)"
          @dragend="draggedPaletteKind = undefined"
        >
          <span class="ta-state-palette__icon" :class="`is-${item.kind}`" />
          {{ item.label.replace('新增 ', '') }}
        </button>
      </section>
      <button type="button" class="ta-state-palette__region" aria-label="新增并发区域" @click="addConcurrentRegion">
        ＋ 并发区域
      </button>
    </aside>

    <main class="ta-state-workspace">
      <header class="ta-state-toolbar">
        <nav aria-label="状态层级面包屑">
          <template v-for="(crumb, index) in breadcrumbs" :key="crumb.id">
            <span v-if="index > 0">/</span>
            <button
              type="button"
              :aria-current="crumb.id === currentScope.id ? 'page' : undefined"
              @click="focusScope(crumb.id)"
            >{{ crumb.label }}</button>
          </template>
        </nav>
        <label>
          <span>方向</span>
          <select aria-label="当前层级方向" :value="currentScope.direction" @change="updateDirection(($event.target as HTMLSelectElement).value)">
            <option value="TB">从上到下</option>
            <option value="BT">从下到上</option>
            <option value="LR">从左到右</option>
            <option value="RL">从右到左</option>
          </select>
        </label>
        <button type="button" @click="autoLayout">自动布局</button>
        <span v-if="validationIssues.length" class="ta-state-toolbar__warning" :title="validationIssues.map(issue => issue.message).join('；')">
          草稿待完善 {{ validationIssues.length }} 项
        </span>
      </header>

      <div
        ref="canvasRef"
        class="ta-state-canvas"
        :class="{ 'is-drag-over': isCanvasDragOver }"
        aria-label="State diagram 可视化画布"
        @dragover.prevent="isCanvasDragOver = true"
        @dragleave="isCanvasDragOver = false"
        @drop.prevent="onDrop"
      >
        <VueFlow
          :nodes="flowNodes"
          :edges="flowEdges"
          :connection-mode="ConnectionMode.Loose"
          :nodes-connectable="false"
          :fit-view-on-init="true"
          :min-zoom="0.25"
          :max-zoom="2"
          @init="vueFlowRef = $event"
          @node-drag-stop="onNodeDragStop"
          @edge-click="onEdgeClick"
        >
          <template #node-state-region="nodeProps">
            <StateRegionNode v-bind="nodeProps" @select="selectedRegionId = $event" />
          </template>
          <template #node-state="nodeProps">
            <StateNode
              v-bind="nodeProps"
              @select="selectNode"
              @focus-request="focusScope"
              @edit-request="openNodeEditor"
              @connection-start="onConnectionStart"
            />
          </template>
          <template #node-state-note="nodeProps">
            <StateNoteNode v-bind="nodeProps" @select="selectNote" />
          </template>
          <template #edge-state-transition="edgeProps">
            <StateTransitionEdge
              v-bind="edgeProps"
              @edit-request="openTransitionEditor"
              @reconnect-start="onReconnectStart"
            />
          </template>
        </VueFlow>
        <svg v-if="isDragging" class="ta-state-drag-line" aria-hidden="true">
          <path :d="dragPath" />
        </svg>
        <div v-if="invalidReason" class="ta-state-connection-error" role="status">{{ invalidReason }}</div>
      </div>
    </main>

    <aside class="ta-state-properties">
      <section v-if="selectedNode" role="region" aria-label="状态属性">
        <h3>状态属性</h3>
        <label v-if="selectedNode.kind !== 'start' && selectedNode.kind !== 'end'">
          <span>状态 ID</span>
          <input aria-label="状态 ID" :value="selectedNode.id" @input="updateNodeId(($event.target as HTMLInputElement).value)" />
        </label>
        <template v-if="selectedNode.kind === 'state'">
          <label>
            <span>状态名称</span>
            <input aria-label="状态名称" :value="selectedNode.label" @input="updateSelectedNode(node => node.label = ($event.target as HTMLInputElement).value)" />
          </label>
          <label>
            <span>状态说明</span>
            <textarea aria-label="状态说明" :value="selectedNode.descriptions.join('\n')" @input="updateDescriptions(($event.target as HTMLTextAreaElement).value)" />
          </label>
          <MermaidColorField label="文字颜色" :model-value="selectedNode.style?.textColor" @update:model-value="updateNodeStyle('textColor', $event)" />
        </template>
        <template v-if="selectedNode.kind === 'state' || selectedNode.kind === 'choice' || selectedNode.kind === 'fork' || selectedNode.kind === 'join'">
          <MermaidColorField label="填充颜色" :model-value="selectedNode.style?.fillColor" @update:model-value="updateNodeStyle('fillColor', $event)" />
          <MermaidColorField label="边框颜色" :model-value="selectedNode.style?.strokeColor" @update:model-value="updateNodeStyle('strokeColor', $event)" />
        </template>
        <button v-if="selectedNode.childScope" type="button" @click="focusScope(selectedNode.childScope.id)">进入复合状态</button>
        <button type="button" class="is-danger" @click="deleteSelection">删除状态</button>
      </section>

      <section v-else-if="selectedNote" role="region" aria-label="Note 属性">
        <h3>Note 属性</h3>
        <label>
          <span>目标状态</span>
          <select aria-label="Note 目标" :value="selectedNote.target" @change="updateNote('target', ($event.target as HTMLSelectElement).value)">
            <option v-for="node in selectedNoteRegion?.nodes.filter(node => node.kind === 'state')" :key="node.id" :value="node.id">{{ node.label }}</option>
          </select>
        </label>
        <label>
          <span>位置</span>
          <select aria-label="Note 位置" :value="selectedNote.placement" @change="updateNote('placement', ($event.target as HTMLSelectElement).value)">
            <option value="left">左侧</option>
            <option value="right">右侧</option>
          </select>
        </label>
        <label>
          <span>内容</span>
          <textarea aria-label="Note 内容" :value="selectedNote.text" @input="updateNote('text', ($event.target as HTMLTextAreaElement).value)" />
        </label>
        <button type="button" class="is-danger" @click="deleteSelection">删除 Note</button>
      </section>

      <section v-else-if="selectedTransition" role="region" aria-label="转换属性">
        <h3>转换属性</h3>
        <p>{{ selectedTransition.source }} → {{ selectedTransition.target }}</p>
        <p>{{ selectedTransition.label || "无标签" }}</p>
        <small>双击画布转换可编辑标签；转换样式由 Mermaid 主题统一管理。</small>
        <button type="button" class="is-danger" @click="deleteSelection">删除转换</button>
      </section>

      <section v-else class="ta-state-properties__empty">
        <h3>属性</h3>
        <p>选择状态、Note 或转换后编辑。</p>
      </section>
    </aside>

    <Teleport to="body">
      <form
        v-if="inlineEdit"
        class="ta-state-inline-editor"
        :style="inlineEdit.position"
        role="dialog"
        :aria-label="inlineEdit.kind === 'node' ? '编辑状态名称' : '编辑转换标签'"
        @submit.prevent="commitInlineEdit"
        @keydown="onInlineKeydown"
      >
        <label>
          <span>{{ inlineEdit.kind === 'node' ? '状态名称' : '转换标签' }}</span>
          <textarea v-model="inlineEdit.text" :aria-label="inlineEdit.kind === 'node' ? '状态名称' : '转换标签'" rows="3" />
        </label>
        <div>
          <button type="button" @click="inlineEdit = undefined">取消</button>
          <button type="submit" class="is-primary">完成</button>
        </div>
      </form>
    </Teleport>
  </div>
</template>

<style scoped>
.ta-state-editor { display: grid; min-height: 0; flex: 1; grid-template-columns: 178px minmax(0, 1fr) 244px; color: var(--ta-ink, #172033); }
.ta-state-palette, .ta-state-properties { overflow: auto; padding: 10px; background: var(--ta-surface, #fff); }
.ta-state-palette { border-right: 1px solid var(--ta-border, #e2e8f0); }
.ta-state-properties { border-left: 1px solid var(--ta-border, #e2e8f0); }
.ta-state-palette section { display: grid; gap: 5px; margin-bottom: 12px; }
.ta-state-palette h3, .ta-state-properties h3 { margin: 0 0 6px; color: var(--ta-muted, #64748b); font-size: 11px; text-transform: uppercase; }
.ta-state-palette button, .ta-state-properties button, .ta-state-toolbar button, .ta-state-toolbar select { min-height: 30px; border: 1px solid var(--ta-border, #dbe2ea); border-radius: 5px; background: #fff; color: inherit; font: inherit; font-size: 11px; cursor: pointer; }
.ta-state-palette button { display: flex; align-items: center; gap: 8px; padding: 4px 8px; text-align: left; }
.ta-state-palette button:hover { border-color: #818cf8; background: #eef2ff; }
.ta-state-palette__icon { width: 16px; height: 12px; border: 1.5px solid #475569; border-radius: 3px; }
.ta-state-palette__icon.is-start { width: 12px; height: 12px; border-radius: 50%; background: #172033; }
.ta-state-palette__icon.is-end { width: 8px; height: 8px; border: 3px double #172033; border-radius: 50%; }
.ta-state-palette__icon.is-choice { width: 10px; height: 10px; transform: rotate(45deg); border-radius: 1px; }
.ta-state-palette__icon.is-fork, .ta-state-palette__icon.is-join { height: 4px; border-radius: 1px; background: #172033; }
.ta-state-palette__icon.is-note { border-color: #d4a72c; border-radius: 1px; background: #fff8c5; }
.ta-state-palette__region { width: 100%; justify-content: center; border-style: dashed !important; }
.ta-state-workspace { display: flex; min-width: 0; min-height: 0; flex-direction: column; }
.ta-state-toolbar { display: flex; min-height: 42px; flex: none; align-items: center; gap: 9px; border-bottom: 1px solid var(--ta-border, #e2e8f0); padding: 5px 10px; }
.ta-state-toolbar nav { display: flex; min-width: 0; flex: 1; align-items: center; gap: 4px; overflow: hidden; }
.ta-state-toolbar nav button { min-height: 26px; border: 0; padding: 0 6px; background: transparent; }
.ta-state-toolbar nav button[aria-current="page"] { color: #4f46e5; font-weight: 650; }
.ta-state-toolbar label { display: flex; align-items: center; gap: 5px; color: #64748b; font-size: 11px; }
.ta-state-toolbar select { min-height: 28px; }
.ta-state-toolbar__warning { color: #b45309; font-size: 10px; }
.ta-state-canvas { position: relative; min-height: 0; flex: 1; overflow: hidden; background-color: #f8fafc; background-image: radial-gradient(#cbd5e1 1px, transparent 1px); background-size: 18px 18px; }
.ta-state-canvas.is-drag-over { box-shadow: inset 0 0 0 2px #6366f1; }
.ta-state-canvas :deep(.vue-flow) { width: 100%; height: 100%; }
.ta-state-drag-line { position: absolute; z-index: 1005; inset: 0; width: 100%; height: 100%; pointer-events: none; }
.ta-state-drag-line path { fill: none; stroke: #6366f1; stroke-width: 2; stroke-dasharray: 5 4; }
.ta-state-connection-error { position: absolute; z-index: 1100; bottom: 14px; left: 50%; transform: translateX(-50%); border-radius: 5px; padding: 6px 9px; background: #b42318; color: #fff; font-size: 11px; }
.ta-state-properties section { display: grid; gap: 9px; }
.ta-state-properties label { display: grid; gap: 4px; color: #64748b; font-size: 11px; }
.ta-state-properties input, .ta-state-properties textarea, .ta-state-properties select { box-sizing: border-box; width: 100%; border: 1px solid var(--ta-border, #dbe2ea); border-radius: 5px; padding: 6px 7px; background: #fff; color: #172033; font: inherit; font-size: 12px; }
.ta-state-properties textarea { min-height: 72px; resize: vertical; }
.ta-state-properties p, .ta-state-properties small { margin: 0; color: #64748b; font-size: 11px; line-height: 1.5; }
.ta-state-properties button.is-danger { margin-top: 6px; border-color: #fecaca; color: #b42318; }
.ta-state-properties__empty { color: #94a3b8; }
@media (max-width: 900px) { .ta-state-editor { grid-template-columns: 150px minmax(0, 1fr); } .ta-state-properties { position: absolute; z-index: 20; right: 8px; bottom: 8px; width: 220px; max-height: 58%; border: 1px solid #e2e8f0; border-radius: 7px; box-shadow: 0 8px 24px rgba(15, 23, 42, .16); } }
</style>

<style>
.ta-state-inline-editor { position: fixed; z-index: 3100; box-sizing: border-box; display: grid; width: 270px; gap: 8px; border: 1px solid #dbe2ea; border-radius: 7px; padding: 10px; background: #fff; box-shadow: 0 14px 30px rgba(15, 23, 42, .18); }
.ta-state-inline-editor label { display: grid; gap: 4px; color: #64748b; font-size: 11px; }
.ta-state-inline-editor textarea { min-height: 58px; border: 1px solid #dbe2ea; border-radius: 5px; padding: 6px; color: #172033; font: inherit; font-size: 12px; resize: vertical; }
.ta-state-inline-editor > div { display: flex; justify-content: flex-end; gap: 6px; }
.ta-state-inline-editor button { min-height: 28px; border: 1px solid #dbe2ea; border-radius: 5px; padding: 0 10px; background: #fff; cursor: pointer; }
.ta-state-inline-editor button.is-primary { border-color: #4f46e5; background: #4f46e5; color: #fff; }
</style>
