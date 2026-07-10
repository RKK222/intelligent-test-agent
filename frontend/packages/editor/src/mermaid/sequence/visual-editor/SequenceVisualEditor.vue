<script setup lang="ts">
import { computed, ref } from "vue";
import { VueFlow, type Connection, type NodeDragEvent, type NodeMouseEvent } from "@vue-flow/core";
import { autoLayoutMermaidSequence } from "../layout";
import { cloneMermaidSequence, type MermaidSequenceDiagram, type MermaidSequenceMessage, type MermaidSequenceParticipant, type MermaidSequenceParticipantType } from "../model";
import SequenceMessageEdge from "./SequenceMessageEdge.vue";
import SequenceParticipantNode from "./SequenceParticipantNode.vue";
import { appendSequenceMessage, applySequenceFlowPositions, toSequenceFlowEdges, toSequenceFlowNodes } from "./vue-flow-adapter";

const props = defineProps<{ modelValue: MermaidSequenceDiagram }>();
const emit = defineEmits<{ "update:modelValue": [diagram: MermaidSequenceDiagram] }>();
const selectedParticipantId = ref<string>();
const flowNodes = computed(() => toSequenceFlowNodes(props.modelValue));
const flowEdges = computed(() => toSequenceFlowEdges(props.modelValue));
const selectedParticipant = computed(() => props.modelValue.participants.find((item) => item.id === selectedParticipantId.value));

function updateDiagram(updater: (draft: MermaidSequenceDiagram) => void) {
  const draft = cloneMermaidSequence(props.modelValue);
  updater(draft);
  emit("update:modelValue", draft);
}

function onNodeDragStop(event: NodeDragEvent) {
  emit("update:modelValue", applySequenceFlowPositions(props.modelValue, [event.node]));
}

function onConnect(connection: Connection) {
  emit("update:modelValue", appendSequenceMessage(props.modelValue, connection));
}

function onNodeClick(event: NodeMouseEvent) {
  selectedParticipantId.value = event.node.id;
}

function updateSelectedParticipant(patch: Partial<Pick<MermaidSequenceParticipant, "text" | "type">>) {
  if (!selectedParticipantId.value) return;
  updateDiagram((draft) => {
    const participant = draft.participants.find((item) => item.id === selectedParticipantId.value);
    if (participant) Object.assign(participant, patch);
  });
}

function deleteSelectedParticipant() {
  const id = selectedParticipantId.value;
  if (!id) return;
  updateDiagram((draft) => {
    draft.participants = draft.participants.filter((participant) => participant.id !== id);
    draft.messages = draft.messages.filter((message) => message.source !== id && message.target !== id);
  });
  selectedParticipantId.value = undefined;
}

function addParticipant() {
  updateDiagram((draft) => {
    const used = new Set(draft.participants.map((participant) => participant.id));
    let sequence = draft.participants.length + 1;
    while (used.has(`P${sequence}`)) sequence += 1;
    const id = `P${sequence}`;
    draft.participants.push({ id, text: "新参与者", type: "participant", position: { x: 80 + draft.participants.length * 220, y: 70 } });
    selectedParticipantId.value = id;
  });
}

function addMessage() {
  const [source, target] = props.modelValue.participants;
  if (!source || !target) return;
  emit("update:modelValue", appendSequenceMessage(props.modelValue, { source: source.id, target: target.id }));
}

function updateMessage(id: string, patch: Partial<Omit<MermaidSequenceMessage, "id">>) {
  updateDiagram((draft) => {
    const message = draft.messages.find((item) => item.id === id);
    if (message) Object.assign(message, patch);
  });
}

function swapMessage(message: MermaidSequenceMessage) {
  updateMessage(message.id, { source: message.target, target: message.source });
}

function moveMessage(index: number, offset: number) {
  const target = index + offset;
  if (target < 0 || target >= props.modelValue.messages.length) return;
  updateDiagram((draft) => {
    const [message] = draft.messages.splice(index, 1);
    if (message) draft.messages.splice(target, 0, message);
  });
}

function deleteMessage(id: string) {
  updateDiagram((draft) => { draft.messages = draft.messages.filter((message) => message.id !== id); });
}
</script>

<template>
  <div class="ta-sequence-editor">
    <div class="ta-sequence-toolbar">
      <button type="button" @click="addParticipant">新增参与者</button>
      <button type="button" :disabled="modelValue.participants.length < 2" @click="addMessage">新增消息</button>
      <button type="button" @click="emit('update:modelValue', autoLayoutMermaidSequence(modelValue))">自动布局</button>
      <span>拖动参与者；从 Handle 拉出消息。消息顺序在右侧调整。</span>
    </div>
    <div class="ta-sequence-workspace">
      <div class="ta-sequence-canvas" aria-label="Sequence diagram 可视化画布">
        <VueFlow :nodes="flowNodes" :edges="flowEdges" :min-zoom="0.35" :max-zoom="2" fit-view-on-init @node-drag-stop="onNodeDragStop" @node-click="onNodeClick" @connect="onConnect">
          <template #node-sequence-participant="nodeProps"><SequenceParticipantNode v-bind="nodeProps" /></template>
          <template #edge-sequence-message="edgeProps"><SequenceMessageEdge v-bind="edgeProps" /></template>
        </VueFlow>
      </div>
      <aside class="ta-sequence-inspector" aria-label="Sequence 属性">
        <section>
          <h3>参与者</h3>
          <div v-if="selectedParticipant" class="ta-sequence-fields">
            <label><span>ID</span><input :value="selectedParticipant.id" disabled /></label>
            <label><span>名称</span><input aria-label="参与者名称" :value="selectedParticipant.text" @input="updateSelectedParticipant({ text: ($event.target as HTMLInputElement).value })" /></label>
            <label><span>类型</span><select aria-label="参与者类型" :value="selectedParticipant.type" @change="updateSelectedParticipant({ type: ($event.target as HTMLSelectElement).value as MermaidSequenceParticipantType })"><option value="participant">参与者</option><option value="actor">角色</option></select></label>
            <button type="button" class="is-danger" @click="deleteSelectedParticipant">删除参与者</button>
          </div>
          <p v-else>选择画布中的参与者后编辑。</p>
        </section>
        <section>
          <h3>消息顺序</h3>
          <div class="ta-sequence-messages">
            <article v-for="(message, index) in modelValue.messages" :key="message.id">
              <header><strong>{{ index + 1 }}</strong><code>{{ message.source }} → {{ message.target }}</code></header>
              <label><span>标签</span><input :aria-label="`消息 ${index + 1} 标签`" :value="message.text" @input="updateMessage(message.id, { text: ($event.target as HTMLInputElement).value })" /></label>
              <label><span>箭头</span><select :value="message.type" @change="updateMessage(message.id, { type: ($event.target as HTMLSelectElement).value as MermaidSequenceMessage['type'] })"><option value="solid">同步实线</option><option value="dotted">虚线响应</option><option value="open">开放实线</option><option value="dotted-open">开放虚线</option></select></label>
              <div class="ta-sequence-message-actions">
                <button type="button" aria-label="上移消息" :disabled="index === 0" @click="moveMessage(index, -1)">↑</button>
                <button type="button" aria-label="下移消息" :disabled="index === modelValue.messages.length - 1" @click="moveMessage(index, 1)">↓</button>
                <button type="button" aria-label="交换消息方向" @click="swapMessage(message)">交换</button>
                <button type="button" aria-label="删除消息" class="is-danger" @click="deleteMessage(message.id)">删除</button>
              </div>
            </article>
          </div>
          <p v-if="!modelValue.messages.length">暂无消息。</p>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.ta-sequence-editor { display: flex; min-height: 0; flex: 1; flex-direction: column; color: var(--ta-text, #334155); }
.ta-sequence-toolbar { display: flex; min-height: 44px; align-items: center; gap: 7px; border-bottom: 1px solid var(--ta-border, #e2e8f0); padding: 6px 10px; background: var(--ta-panel-2, #f8fafc); }
.ta-sequence-toolbar span { margin-left: auto; color: var(--ta-muted, #64748b); font-size: 11px; }
.ta-sequence-toolbar button, .ta-sequence-inspector button, .ta-sequence-inspector input, .ta-sequence-inspector select { min-height: 28px; border: 1px solid var(--ta-border, #dbe2ea); border-radius: 5px; background: var(--ta-surface, #fff); color: var(--ta-ink, #172033); font: inherit; font-size: 11px; }
.ta-sequence-toolbar button, .ta-sequence-inspector button { padding: 0 8px; cursor: pointer; }
.ta-sequence-workspace { display: grid; min-height: 0; flex: 1; grid-template-columns: minmax(0, 1fr) 300px; }
.ta-sequence-canvas { min-height: 440px; overflow: hidden; background-color: var(--ta-surface, #fff); background-image: linear-gradient(to right, transparent 17px, color-mix(in srgb, var(--ta-border, #dbe2ea) 45%, transparent) 18px), linear-gradient(to bottom, transparent 17px, color-mix(in srgb, var(--ta-border, #dbe2ea) 45%, transparent) 18px); background-size: 18px 18px; }
.ta-sequence-canvas :deep(.vue-flow) { height: 100%; min-height: 440px; }
.ta-sequence-inspector { min-height: 0; overflow: auto; border-left: 1px solid var(--ta-border, #e2e8f0); background: var(--ta-panel-2, #f8fafc); }
.ta-sequence-inspector section { padding: 12px; border-bottom: 1px solid var(--ta-border, #e2e8f0); }
.ta-sequence-inspector h3 { margin: 0 0 9px; color: var(--ta-ink, #172033); font-size: 12px; }
.ta-sequence-inspector p { margin: 0; color: var(--ta-muted, #64748b); font-size: 11px; }
.ta-sequence-fields, .ta-sequence-messages, .ta-sequence-messages article { display: grid; gap: 7px; }
.ta-sequence-fields label, .ta-sequence-messages label { display: grid; gap: 3px; color: var(--ta-muted, #64748b); font-size: 10px; }
.ta-sequence-fields input, .ta-sequence-fields select, .ta-sequence-messages input, .ta-sequence-messages select { width: 100%; padding: 4px 7px; }
.ta-sequence-messages article { border: 1px solid var(--ta-border, #e2e8f0); border-radius: 6px; padding: 8px; background: var(--ta-surface, #fff); }
.ta-sequence-messages header { display: flex; align-items: center; gap: 7px; }
.ta-sequence-messages header strong { display: grid; width: 20px; height: 20px; place-items: center; border-radius: 99px; background: color-mix(in srgb, var(--primary, #4f46e5) 12%, transparent); color: var(--primary, #4f46e5); font-size: 10px; }
.ta-sequence-messages header code { color: var(--ta-muted, #64748b); font-size: 10px; }
.ta-sequence-message-actions { display: flex; justify-content: flex-end; gap: 4px; }
.ta-sequence-inspector .is-danger { color: #b42318; }
@media (max-width: 820px) { .ta-sequence-workspace { grid-template-columns: 1fr; grid-template-rows: minmax(360px, 1fr) 240px; } .ta-sequence-inspector { border-top: 1px solid var(--ta-border, #e2e8f0); border-left: 0; } .ta-sequence-toolbar span { display: none; } }
</style>
