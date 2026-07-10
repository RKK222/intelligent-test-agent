<script setup lang="ts">
import { computed, ref } from "vue";
import {
  VueFlow,
  type Connection,
  type NodeDragEvent,
  type NodeMouseEvent
} from "@vue-flow/core";
import { autoLayoutMermaidGraph } from "../layout";
import { cloneMermaidGraph, type MermaidEdge, type MermaidGraph, type MermaidNode, type MermaidNodeType } from "../model";
import MermaidFlowNode from "./MermaidFlowNode.vue";
import {
  appendMermaidEdge,
  applyVueFlowPositions,
  toVueFlowEdges,
  toVueFlowNodes
} from "./vue-flow-adapter";

const props = defineProps<{ modelValue: MermaidGraph }>();
const emit = defineEmits<{ "update:modelValue": [graph: MermaidGraph] }>();

const selectedNodeId = ref<string>();
const flowNodes = computed(() => toVueFlowNodes(props.modelValue));
const flowEdges = computed(() => toVueFlowEdges(props.modelValue));
const selectedNode = computed(() => props.modelValue.nodes.find((node) => node.id === selectedNodeId.value));

function updateGraph(updater: (draft: MermaidGraph) => void) {
  const draft = cloneMermaidGraph(props.modelValue);
  updater(draft);
  emit("update:modelValue", draft);
}

function onNodeDragStop(event: NodeDragEvent) {
  emit("update:modelValue", applyVueFlowPositions(props.modelValue, [event.node]));
}

function onConnect(connection: Connection) {
  const next = appendMermaidEdge(props.modelValue, connection);
  if (next !== props.modelValue) emit("update:modelValue", next);
}

function onNodeClick(event: NodeMouseEvent) {
  selectedNodeId.value = event.node.id;
}

function updateSelectedNode(patch: Partial<Pick<MermaidNode, "text" | "type">>) {
  if (!selectedNodeId.value) return;
  updateGraph((draft) => {
    const node = draft.nodes.find((item) => item.id === selectedNodeId.value);
    if (node) Object.assign(node, patch);
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

function addNode() {
  updateGraph((draft) => {
    const used = new Set(draft.nodes.map((node) => node.id));
    let sequence = draft.nodes.length + 1;
    while (used.has(`N${sequence}`)) sequence += 1;
    const id = `N${sequence}`;
    draft.nodes.push({ id, text: "新节点", type: "rectangle", position: { x: 80, y: 70 } });
    selectedNodeId.value = id;
  });
}

function addEdge() {
  const [source, target] = props.modelValue.nodes;
  if (!source || !target) return;
  const next = appendMermaidEdge(props.modelValue, { source: source.id, target: target.id });
  if (next !== props.modelValue) emit("update:modelValue", next);
}

function updateEdge(edgeId: string, patch: Partial<Omit<MermaidEdge, "id">>) {
  updateGraph((draft) => {
    const edge = draft.edges.find((item) => item.id === edgeId);
    if (edge) Object.assign(edge, patch);
  });
}

function swapEdge(edge: MermaidEdge) {
  updateEdge(edge.id, { source: edge.target, target: edge.source });
}

function deleteEdge(edgeId: string) {
  updateGraph((draft) => {
    draft.edges = draft.edges.filter((edge) => edge.id !== edgeId);
  });
}

function applyAutoLayout() {
  emit("update:modelValue", autoLayoutMermaidGraph(props.modelValue));
}

function updateDirection(event: Event) {
  const direction = (event.target as HTMLSelectElement).value as MermaidGraph["direction"];
  updateGraph((draft) => { draft.direction = direction; });
}
</script>

<template>
  <div class="ta-mermaid-visual-editor">
    <div class="ta-mermaid-toolbar">
      <button type="button" @click="addNode">新增节点</button>
      <button type="button" :disabled="modelValue.nodes.length < 2" @click="addEdge">新增连线</button>
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
      <span class="ta-mermaid-toolbar__hint">拖动节点；从节点边缘 Handle 拉出连线</span>
    </div>

    <div class="ta-mermaid-workspace">
      <div class="ta-mermaid-canvas" aria-label="Mermaid 可视化画布">
        <VueFlow
          :nodes="flowNodes"
          :edges="flowEdges"
          :min-zoom="0.35"
          :max-zoom="2"
          fit-view-on-init
          :nodes-connectable="true"
          @node-drag-stop="onNodeDragStop"
          @node-click="onNodeClick"
          @connect="onConnect"
        >
          <template #node-mermaid="nodeProps">
            <MermaidFlowNode v-bind="nodeProps" />
          </template>
        </VueFlow>
      </div>

      <aside class="ta-mermaid-inspector" aria-label="图属性">
        <section>
          <h3>节点</h3>
          <div v-if="selectedNode" class="ta-mermaid-fields">
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
          <p v-else class="ta-mermaid-empty">选择画布中的节点后编辑。</p>
        </section>

        <section>
          <div class="ta-mermaid-section-title"><h3>连线</h3><span>{{ modelValue.edges.length }}</span></div>
          <div v-if="modelValue.edges.length" class="ta-mermaid-edge-list">
            <article v-for="edge in modelValue.edges" :key="edge.id" class="ta-mermaid-edge-card">
              <div class="ta-mermaid-edge-route"><code>{{ edge.source }}</code><span>→</span><code>{{ edge.target }}</code></div>
              <label><span>标签</span><input :value="edge.label" @input="updateEdge(edge.id, { label: ($event.target as HTMLInputElement).value })" /></label>
              <label>
                <span>类型</span>
                <select :value="edge.relation" @change="updateEdge(edge.id, { relation: ($event.target as HTMLSelectElement).value as MermaidEdge['relation'] })">
                  <option value="arrow">箭头</option><option value="line">直线</option><option value="dotted">虚线箭头</option><option value="thick">粗箭头</option>
                </select>
              </label>
              <div class="ta-mermaid-edge-actions">
                <button type="button" @click="swapEdge(edge)">交换方向</button>
                <button type="button" class="is-danger" @click="deleteEdge(edge.id)">删除</button>
              </div>
            </article>
          </div>
          <p v-else class="ta-mermaid-empty">从节点 Handle 拉出连线，或使用顶部新增连线。</p>
        </section>
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
.ta-mermaid-canvas { min-height: 420px; background-color: var(--ta-surface, #fff); background-image: radial-gradient(circle, color-mix(in srgb, var(--ta-border, #dbe2ea) 75%, transparent) 1px, transparent 1px); background-size: 18px 18px; }
.ta-mermaid-canvas :deep(.vue-flow) { height: 100%; min-height: 420px; }
.ta-mermaid-inspector { min-height: 0; overflow: auto; border-left: 1px solid var(--ta-border, #e2e8f0); background: var(--ta-panel-2, #f8fafc); }
.ta-mermaid-inspector section { padding: 13px; border-bottom: 1px solid var(--ta-border, #e2e8f0); }
.ta-mermaid-inspector h3 { margin: 0 0 10px; color: var(--ta-ink, #172033); font-size: 12px; font-weight: 700; }
.ta-mermaid-fields, .ta-mermaid-edge-card { display: grid; gap: 8px; }
.ta-mermaid-fields label, .ta-mermaid-edge-card label { display: grid; gap: 3px; color: var(--ta-muted, #64748b); font-size: 10px; }
.ta-mermaid-fields input, .ta-mermaid-fields select, .ta-mermaid-edge-card input, .ta-mermaid-edge-card select { width: 100%; padding: 4px 7px; }
.ta-mermaid-section-title { display: flex; align-items: center; justify-content: space-between; }
.ta-mermaid-section-title span { color: var(--ta-muted, #64748b); font-family: Menlo, Monaco, Consolas, monospace; font-size: 10px; }
.ta-mermaid-edge-list { display: grid; gap: 9px; }
.ta-mermaid-edge-card { border: 1px solid var(--ta-border, #e2e8f0); border-radius: 6px; padding: 9px; background: var(--ta-surface, #fff); }
.ta-mermaid-edge-route { display: flex; align-items: center; gap: 6px; font-size: 11px; }
.ta-mermaid-edge-route code { color: var(--primary, #4f46e5); }
.ta-mermaid-edge-actions { display: flex; justify-content: flex-end; gap: 5px; }
.ta-mermaid-inspector button.is-danger { color: #b42318; }
.ta-mermaid-empty { margin: 0; color: var(--ta-muted, #64748b); font-size: 11px; line-height: 1.5; }
@media (max-width: 820px) { .ta-mermaid-workspace { grid-template-columns: 1fr; grid-template-rows: minmax(360px, 1fr) 240px; } .ta-mermaid-inspector { border-top: 1px solid var(--ta-border, #e2e8f0); border-left: 0; } .ta-mermaid-toolbar__hint { display: none; } }
@media (prefers-reduced-motion: reduce) { .ta-mermaid-visual-editor *, .ta-mermaid-visual-editor *::before, .ta-mermaid-visual-editor *::after { scroll-behavior: auto !important; transition-duration: 0.01ms !important; } }
</style>
