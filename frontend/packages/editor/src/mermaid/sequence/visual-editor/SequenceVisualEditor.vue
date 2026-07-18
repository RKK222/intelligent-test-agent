<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { VueFlow } from "@vue-flow/core";
import {
  analyzeSequenceParticipantDeletion,
  appendSequenceStatement,
  deleteSequenceParticipant,
  deleteSequenceStatement,
  moveSequenceParticipant,
  moveSequenceStatement,
  nextSequenceId,
  rebindSequenceMessage,
  renameSequenceParticipant,
  setSequenceMessageLifecycle,
  updateSequenceParticipant,
  updateSequenceStatement,
  validateMermaidSequence,
  type MermaidSequenceCommandResult
} from "../commands";
import { autoLayoutMermaidSequence, layoutMermaidSequence } from "../layout";
import {
  MERMAID_SEQUENCE_ARROWS,
  MERMAID_SEQUENCE_PARTICIPANT_TYPES,
  cloneMermaidSequence,
  flattenMermaidSequenceStatements,
  sequenceMessages,
  type MermaidSequenceActivation,
  type MermaidSequenceBlock,
  type MermaidSequenceBlockType,
  type MermaidSequenceComment,
  type MermaidSequenceDiagram,
  type MermaidSequenceMessage,
  type MermaidSequenceNote,
  type MermaidSequenceParticipantType,
  type MermaidSequenceStatement
} from "../model";
import SequenceScene from "./SequenceScene.vue";
import SequenceStructureTree from "./SequenceStructureTree.vue";

const props = defineProps<{ modelValue: MermaidSequenceDiagram }>();
const emit = defineEmits<{ "update:modelValue": [diagram: MermaidSequenceDiagram] }>();

type InspectorTab = "elements" | "structure" | "properties";
type FlowController = { fitView: (options?: { padding?: number; duration?: number }) => void };
type PaletteToken = `participant:${MermaidSequenceParticipantType}`
  | `block:${MermaidSequenceBlockType}`
  | "box" | "message" | "async-message" | "note" | "activate" | "deactivate" | "comment" | "create" | "destroy";

const workingDiagram = ref(cloneMermaidSequence(props.modelValue));
const activeTab = ref<InspectorTab>("elements");
const insertionContainerId = ref("root");
const insertionContainerLabel = ref("根流程");
const selectedParticipantId = ref<string>();
const selectedGroupId = ref<string>();
const selectedStatementId = ref<string>();
const statusMessage = ref("");
const destroyEndpointDrafts = ref<Record<string, string>>({});
const flowController = ref<FlowController>();

watch(() => props.modelValue, (diagram) => {
  workingDiagram.value = cloneMermaidSequence(diagram);
}, { deep: false });

const layout = computed(() => layoutMermaidSequence(workingDiagram.value));
const sceneNodes = computed(() => [{
  id: "sequence-scene",
  type: "sequence-scene",
  position: { x: 0, y: 0 },
  draggable: false,
  selectable: true,
  connectable: false,
  style: { width: `${layout.value.width}px`, height: `${layout.value.height}px`, padding: "0", border: "0", background: "transparent" },
  data: { diagram: workingDiagram.value, layout: layout.value }
}]);
const selectedParticipant = computed(() => workingDiagram.value.participants.find((participant) => participant.id === selectedParticipantId.value));
const selectedGroup = computed(() => workingDiagram.value.groups.find((group) => group.id === selectedGroupId.value));
const selectedStatement = computed(() => flattenMermaidSequenceStatements(workingDiagram.value.statements)
  .find((statement) => statement.id === selectedStatementId.value));
const selectedCanvasId = computed(() => selectedGroupId.value ?? selectedParticipantId.value ?? selectedStatementId.value);
const validationIssues = computed(() => validateMermaidSequence(workingDiagram.value));

const participantNames: Record<MermaidSequenceParticipantType, string> = {
  participant: "新参与者",
  actor: "新角色",
  boundary: "新边界",
  control: "新控制器",
  entity: "新实体",
  database: "新数据库",
  collections: "新集合",
  queue: "新队列"
};

function commit(diagram: MermaidSequenceDiagram) {
  workingDiagram.value = diagram;
  statusMessage.value = "";
  emit("update:modelValue", diagram);
}

function commitResult(result: MermaidSequenceCommandResult): boolean {
  if (!result.ok) {
    statusMessage.value = result.reason;
    return false;
  }
  commit(result.diagram);
  return true;
}

function updateDraft(updater: (diagram: MermaidSequenceDiagram) => void) {
  const next = cloneMermaidSequence(workingDiagram.value);
  updater(next);
  next.messages = sequenceMessages(next.statements);
  commit(next);
}

function nextParticipantId(): string {
  const used = new Set(workingDiagram.value.participants.map((participant) => participant.id));
  let index = workingDiagram.value.participants.length + 1;
  while (used.has(`P${index}`)) index += 1;
  return `P${index}`;
}

function nextGroupId(): string {
  const used = new Set(workingDiagram.value.groups.map((group) => group.id));
  let index = 1;
  while (used.has(`box-${index}`)) index += 1;
  return `box-${index}`;
}

function participantText(participantId: string): string {
  return workingDiagram.value.participants.find((participant) => participant.id === participantId)?.text ?? participantId;
}

function nextBranchId(diagram: MermaidSequenceDiagram): string {
  const used = new Set(flattenMermaidSequenceStatements(diagram.statements)
    .filter((statement): statement is MermaidSequenceBlock => statement.kind === "block")
    .flatMap((statement) => statement.branches.map((branch) => branch.id)));
  let index = 1;
  while (used.has(`branch-new-${index}`)) index += 1;
  return `branch-new-${index}`;
}

function selectParticipant(id: string) {
  selectedParticipantId.value = id;
  selectedGroupId.value = undefined;
  selectedStatementId.value = undefined;
  activeTab.value = "properties";
}

function selectStatement(id: string) {
  selectedStatementId.value = id;
  selectedParticipantId.value = undefined;
  selectedGroupId.value = undefined;
  activeTab.value = "properties";
}

function selectGroup(id: string) {
  selectedGroupId.value = id;
  selectedParticipantId.value = undefined;
  selectedStatementId.value = undefined;
  activeTab.value = "properties";
}

function addParticipant(type: MermaidSequenceParticipantType) {
  const id = nextParticipantId();
  updateDraft((diagram) => {
    diagram.participants.push({
      id,
      text: participantNames[type],
      type,
      position: { x: 80 + diagram.participants.length * 220, y: 70 },
      declared: true
    });
  });
  selectParticipant(id);
}

function appendAtInsertionTarget(statement: MermaidSequenceStatement) {
  const result = appendSequenceStatement(workingDiagram.value, insertionContainerId.value, statement);
  if (commitResult(result)) selectStatement(statement.id);
}

function selectInsertionContainer(containerId: string, label: string) {
  insertionContainerId.value = containerId;
  insertionContainerLabel.value = label;
  activeTab.value = "elements";
}

function addMessage(arrow: MermaidSequenceMessage["arrow"] = "->>") {
  const source = workingDiagram.value.participants[0];
  const target = workingDiagram.value.participants[1] ?? source;
  if (!source || !target) {
    statusMessage.value = "请先创建至少一个参与者";
    return;
  }
  appendAtInsertionTarget({
    id: nextSequenceId(workingDiagram.value, "message"),
    kind: "message",
    source: source.id,
    target: target.id,
    text: arrow.endsWith(")") ? "异步消息" : "新消息",
    arrow
  });
}

function createCanvasMessage(source: string, target: string) {
  appendAtInsertionTarget({
    id: nextSequenceId(workingDiagram.value, "message"),
    kind: "message",
    source,
    target,
    text: source === target ? "自调用" : "新消息",
    arrow: "->>"
  });
}

function addNote() {
  const first = workingDiagram.value.participants[0];
  const second = workingDiagram.value.participants[1];
  if (!first) {
    statusMessage.value = "请先创建参与者";
    return;
  }
  const note: MermaidSequenceNote = {
    id: nextSequenceId(workingDiagram.value, "note"),
    kind: "note",
    placement: second ? "over" : "right",
    participants: second ? [first.id, second.id] : [first.id],
    text: "新说明"
  };
  appendAtInsertionTarget(note);
}

function addActivation(active: boolean) {
  const participant = workingDiagram.value.participants[0];
  if (!participant) {
    statusMessage.value = "请先创建参与者";
    return;
  }
  const activation: MermaidSequenceActivation = {
    id: nextSequenceId(workingDiagram.value, active ? "activate" : "deactivate"),
    kind: "activation",
    participantId: participant.id,
    active
  };
  appendAtInsertionTarget(activation);
}

function addComment() {
  const comment: MermaidSequenceComment = {
    id: nextSequenceId(workingDiagram.value, "comment"),
    kind: "comment",
    text: "新注释"
  };
  appendAtInsertionTarget(comment);
}

function addBlock(blockType: MermaidSequenceBlockType) {
  const diagram = workingDiagram.value;
  const primary = nextBranchId(diagram);
  const block: MermaidSequenceBlock = {
    id: nextSequenceId(diagram, "block"),
    kind: "block",
    blockType,
    branches: [{ id: primary, label: blockType === "rect" ? "rgba(79, 107, 237, 0.08)" : "条件", keyword: "body", statements: [] }]
  };
  if (blockType === "alt" || blockType === "par" || blockType === "critical") {
    const secondary = `${primary}-2`;
    block.branches.push({
      id: secondary,
      label: blockType === "alt" ? "其他" : blockType === "par" ? "并行分支" : "备选",
      keyword: blockType === "alt" ? "else" : blockType === "par" ? "and" : "option",
      statements: []
    });
  }
  appendAtInsertionTarget(block);
}

function addBox() {
  const id = nextGroupId();
  updateDraft((diagram) => diagram.groups.push({ id, label: "新分组", color: "Aqua", participantIds: [] }));
  selectGroup(id);
}

function updateSelectedGroup(patch: { label?: string; color?: string }) {
  const id = selectedGroupId.value;
  if (!id) return;
  updateDraft((diagram) => {
    const group = diagram.groups.find((item) => item.id === id);
    if (!group) return;
    if (patch.label !== undefined) group.label = patch.label;
    if (patch.color !== undefined) group.color = patch.color || undefined;
  });
}

function addCreateLifecycle() {
  const caller = workingDiagram.value.participants.find((participant) => !participant.created);
  if (!caller) {
    statusMessage.value = "create 生命周期需要一个已存在参与者作为调用方";
    return;
  }
  const id = nextParticipantId();
  const next = cloneMermaidSequence(workingDiagram.value);
  next.participants.push({
      id,
      text: "新建参与者",
      type: "participant",
      position: { x: 80 + next.participants.length * 220, y: 70 },
      declared: false,
      created: true
  });
  const message: MermaidSequenceMessage = {
      id: nextSequenceId(next, "message"),
      kind: "message",
      source: caller.id,
      target: id,
      text: "创建",
      arrow: "->>",
      create: { participantId: id, type: "participant", text: "新建参与者" }
  };
  const result = appendSequenceStatement(next, insertionContainerId.value, message);
  if (commitResult(result)) selectStatement(message.id);
}

function addDestroyLifecycle() {
  const participant = workingDiagram.value.participants.at(-1);
  const target = workingDiagram.value.participants[0];
  if (!participant || !target) {
    statusMessage.value = "请先创建参与者";
    return;
  }
  const message: MermaidSequenceMessage = {
    id: nextSequenceId(workingDiagram.value, "message"),
    kind: "message",
    source: participant.id,
    target: target.id,
    text: "销毁",
    arrow: "-x",
    destroy: { participantId: participant.id }
  };
  appendAtInsertionTarget(message);
}

function createPaletteElement(token: PaletteToken) {
  if (token.startsWith("participant:")) {
    addParticipant(token.slice("participant:".length) as MermaidSequenceParticipantType);
  } else if (token.startsWith("block:")) {
    addBlock(token.slice("block:".length) as MermaidSequenceBlockType);
  } else if (token === "box") addBox();
  else if (token === "message") addMessage();
  else if (token === "async-message") addMessage("-)");
  else if (token === "note") addNote();
  else if (token === "activate") addActivation(true);
  else if (token === "deactivate") addActivation(false);
  else if (token === "comment") addComment();
  else if (token === "create") addCreateLifecycle();
  else addDestroyLifecycle();
}

function startPaletteDrag(event: DragEvent, token: PaletteToken) {
  event.dataTransfer?.setData("application/x-sequence-palette", token);
  if (event.dataTransfer) event.dataTransfer.effectAllowed = "copy";
}

function dropPaletteOnCanvas(event: DragEvent) {
  const token = event.dataTransfer?.getData("application/x-sequence-palette") as PaletteToken | undefined;
  if (!token) return;
  event.preventDefault();
  createPaletteElement(token);
}

function renameSelectedParticipant(value: string) {
  const previous = selectedParticipantId.value;
  if (!previous || value === previous) return;
  const result = renameSequenceParticipant(workingDiagram.value, previous, value);
  if (commitResult(result)) selectedParticipantId.value = value.trim();
}

function updateSelectedParticipant(patch: Parameters<typeof updateSequenceParticipant>[2]) {
  const id = selectedParticipantId.value;
  if (!id) return;
  commitResult(updateSequenceParticipant(workingDiagram.value, id, patch));
}

function deleteSelectedParticipant() {
  const id = selectedParticipantId.value;
  if (!id) return;
  const impact = analyzeSequenceParticipantDeletion(workingDiagram.value, id);
  const confirmed = impact.total === 0 || window.confirm(`删除参与者会级联清理 ${impact.total} 个关联元素，是否继续？`);
  if (!confirmed) return;
  if (commitResult(deleteSequenceParticipant(workingDiagram.value, id, true))) {
    selectedParticipantId.value = undefined;
    activeTab.value = "structure";
  }
}

function moveParticipant(id: string, offset: number) {
  const index = workingDiagram.value.participants.findIndex((participant) => participant.id === id);
  if (index < 0) return;
  commitResult(moveSequenceParticipant(workingDiagram.value, id, index + offset));
}

function moveParticipantToIndex(id: string, targetIndex: number) {
  commitResult(moveSequenceParticipant(workingDiagram.value, id, targetIndex));
}

function updateSelectedStatement(patch: Record<string, unknown>) {
  const id = selectedStatementId.value;
  if (!id) return;
  commitResult(updateSequenceStatement(workingDiagram.value, id, patch));
}

function updateSelectedNotePlacement(placement: MermaidSequenceNote["placement"]) {
  const statement = selectedStatement.value;
  if (!statement || statement.kind !== "note") return;
  const participants = placement === "over" ? statement.participants : statement.participants.slice(0, 1);
  commitResult(updateSequenceStatement(workingDiagram.value, statement.id, { placement, participants }));
}

function updateSelectedNoteParticipant(index: number, participantId: string) {
  const statement = selectedStatement.value;
  if (!statement || statement.kind !== "note") return;
  const participants = [...statement.participants];
  if (participantId) participants[index] = participantId;
  else participants.splice(index, 1);
  commitResult(updateSequenceStatement(workingDiagram.value, statement.id, { participants }));
}

function rebindSelectedMessage(endpoint: "source" | "target", participantId: string) {
  const statement = selectedStatement.value;
  if (!statement || statement.kind !== "message") return;
  const source = endpoint === "source" ? participantId : statement.source;
  const target = endpoint === "target" ? participantId : statement.target;
  commitResult(rebindSequenceMessage(workingDiagram.value, statement.id, source, target));
}

function rebindCanvasMessage(messageId: string, endpoint: "source" | "target", participantId: string) {
  const message = workingDiagram.value.messages.find((item) => item.id === messageId);
  if (!message) return;
  commitResult(rebindSequenceMessage(
    workingDiagram.value,
    messageId,
    endpoint === "source" ? participantId : message.source,
    endpoint === "target" ? participantId : message.target
  ));
}

function swapSelectedMessage() {
  const statement = selectedStatement.value;
  if (!statement || statement.kind !== "message") return;
  commitResult(rebindSequenceMessage(workingDiagram.value, statement.id, statement.target, statement.source));
}

function toggleMessageLifecycle(kind: "create" | "destroy", checked: boolean) {
  const statement = selectedStatement.value;
  if (!statement || statement.kind !== "message") return;
  if (kind === "destroy" && !checked && statement.destroy) {
    destroyEndpointDrafts.value[statement.id] = statement.destroy.participantId;
  }
  commitResult(setSequenceMessageLifecycle(
    workingDiagram.value,
    statement.id,
    kind,
    checked,
    kind === "destroy" ? destroyEndpointDrafts.value[statement.id] : undefined
  ));
}

function updateDestroyEndpoint(participantId: string) {
  const statement = selectedStatement.value;
  if (!statement || statement.kind !== "message") return;
  destroyEndpointDrafts.value[statement.id] = participantId;
  if (statement.destroy) {
    commitResult(setSequenceMessageLifecycle(workingDiagram.value, statement.id, "destroy", true, participantId));
  }
}

function updateBlockLabel(value: string) {
  const statement = selectedStatement.value;
  if (!statement || statement.kind !== "block") return;
  updateDraft((diagram) => {
    const block = flattenMermaidSequenceStatements(diagram.statements)
      .find((item): item is MermaidSequenceBlock => item.id === statement.id && item.kind === "block");
    if (block?.branches[0]) block.branches[0].label = value;
  });
}

function updateBlockBranchLabel(branchId: string, value: string) {
  const statement = selectedStatement.value;
  if (!statement || statement.kind !== "block") return;
  updateDraft((diagram) => {
    const block = flattenMermaidSequenceStatements(diagram.statements)
      .find((item): item is MermaidSequenceBlock => item.id === statement.id && item.kind === "block");
    const branch = block?.branches.find((item) => item.id === branchId);
    if (branch) branch.label = value;
  });
}

function commitInlineText(kind: "participant" | "statement", id: string, value: string) {
  if (kind === "participant") {
    commitResult(updateSequenceParticipant(workingDiagram.value, id, { text: value }));
    return;
  }
  const statement = flattenMermaidSequenceStatements(workingDiagram.value.statements).find((item) => item.id === id);
  if (!statement) return;
  if (statement.kind === "block") {
    selectedStatementId.value = id;
    updateBlockLabel(value);
  } else if (statement.kind === "message" || statement.kind === "note" || statement.kind === "comment") {
    commitResult(updateSequenceStatement(workingDiagram.value, id, { text: value }));
  }
}

function addSelectedBlockBranch() {
  const statement = selectedStatement.value;
  if (!statement || statement.kind !== "block" || !["alt", "par", "critical"].includes(statement.blockType)) return;
  updateDraft((diagram) => {
    const block = flattenMermaidSequenceStatements(diagram.statements)
      .find((item): item is MermaidSequenceBlock => item.id === statement.id && item.kind === "block");
    if (!block) return;
    block.branches.push({
      id: nextBranchId(diagram),
      label: "新分支",
      keyword: block.blockType === "alt" ? "else" : block.blockType === "par" ? "and" : "option",
      statements: []
    });
  });
}

function deleteSelectedStatement() {
  const id = selectedStatementId.value;
  if (!id) return;
  if (commitResult(deleteSequenceStatement(workingDiagram.value, id))) {
    selectedStatementId.value = undefined;
    activeTab.value = "structure";
  }
}

function moveStatement(statementId: string, containerId: string, targetIndex: number) {
  commitResult(moveSequenceStatement(workingDiagram.value, statementId, containerId, targetIndex));
}

function updateAutonumber(enabled: boolean) {
  updateDraft((diagram) => {
    diagram.autonumber = enabled ? { enabled: true, start: 1, step: 1 } : undefined;
  });
}

function updateAutonumberNumber(field: "start" | "step", value: string) {
  const number = Number(value);
  if (!Number.isInteger(number) || number < 1) return;
  updateDraft((diagram) => {
    if (!diagram.autonumber) diagram.autonumber = { enabled: true };
    diagram.autonumber[field] = number;
  });
}

function runValidation() {
  statusMessage.value = validationIssues.value.length
    ? validationIssues.value[0]?.message ?? "时序语义校验失败"
    : "本地时序语义校验通过";
}

function onFlowInit(controller: FlowController) {
  flowController.value = controller;
}
</script>

<template>
  <div class="ta-sequence-editor">
    <header class="ta-sequence-toolbar">
      <div class="ta-sequence-toolbar-title">
        <strong>Sequence 结构化编辑</strong>
        <span>{{ workingDiagram.participants.length }} 参与者 · {{ workingDiagram.messages.length }} 消息</span>
      </div>
      <button type="button" @click="flowController?.fitView({ padding: 0.12, duration: 180 })">适应视图</button>
      <button type="button" @click="commit(autoLayoutMermaidSequence(workingDiagram))">重排参与者</button>
      <button type="button" @click="runValidation">校验</button>
      <span v-if="validationIssues.length" class="ta-sequence-validation-count">{{ validationIssues.length }} 个语义提示</span>
    </header>

    <p v-if="statusMessage" class="ta-sequence-status" role="alert">{{ statusMessage }}</p>

    <div class="ta-sequence-workspace">
      <div class="ta-sequence-canvas" aria-label="Sequence diagram 可视化画布" @dragover.prevent @drop="dropPaletteOnCanvas">
        <VueFlow
          :nodes="sceneNodes"
          :edges="[]"
          :nodes-draggable="false"
          :nodes-connectable="false"
          :min-zoom="0.25"
          :max-zoom="2.5"
          fit-view-on-init
          @init="onFlowInit"
        >
          <template #node-sequence-scene="nodeProps">
            <SequenceScene
              :data="nodeProps.data"
              :selected-id="selectedCanvasId"
              @select-group="selectGroup"
              @select-participant="selectParticipant"
              @select-statement="selectStatement"
              @edit-statement="selectStatement"
              @edit-text="commitInlineText"
              @move-participant="moveParticipantToIndex"
              @rebind-message="rebindCanvasMessage"
              @create-message="createCanvasMessage"
            />
          </template>
        </VueFlow>
      </div>

      <aside class="ta-sequence-inspector" aria-label="Sequence 编辑侧栏">
        <div class="ta-sequence-tabs" role="tablist" aria-label="Sequence 编辑模式">
          <button type="button" role="tab" :aria-selected="activeTab === 'elements'" @click="activeTab = 'elements'">元素</button>
          <button type="button" role="tab" :aria-selected="activeTab === 'structure'" @click="activeTab = 'structure'">结构</button>
          <button type="button" role="tab" :aria-selected="activeTab === 'properties'" @click="activeTab = 'properties'">属性</button>
        </div>

        <div v-if="activeTab === 'elements'" class="ta-sequence-panel ta-sequence-elements" role="tabpanel">
          <section>
            <h3>参与者</h3>
            <div class="ta-sequence-palette-grid">
              <button v-for="type in MERMAID_SEQUENCE_PARTICIPANT_TYPES" :key="type" type="button" draggable="true" :aria-label="`新增 ${type}`" @dragstart="startPaletteDrag($event, `participant:${type}`)" @click="addParticipant(type)">
                <span>{{ type }}</span><small>{{ participantNames[type] }}</small>
              </button>
            </div>
            <button type="button" draggable="true" class="ta-sequence-wide-action" @dragstart="startPaletteDrag($event, 'box')" @click="addBox">新增 box 分组</button>
          </section>
          <section>
            <div class="ta-sequence-section-heading"><h3>时序元素</h3><span>插入：{{ insertionContainerLabel }}</span></div>
            <div class="ta-sequence-action-grid">
              <button type="button" draggable="true" aria-label="新增消息" @dragstart="startPaletteDrag($event, 'message')" @click="addMessage()">消息</button>
              <button type="button" draggable="true" aria-label="新增异步消息" @dragstart="startPaletteDrag($event, 'async-message')" @click="addMessage('-)')">异步</button>
              <button type="button" draggable="true" aria-label="新增 Note" @dragstart="startPaletteDrag($event, 'note')" @click="addNote">Note</button>
              <button type="button" draggable="true" aria-label="新增激活" @dragstart="startPaletteDrag($event, 'activate')" @click="addActivation(true)">激活</button>
              <button type="button" draggable="true" aria-label="新增停用" @dragstart="startPaletteDrag($event, 'deactivate')" @click="addActivation(false)">停用</button>
              <button type="button" draggable="true" aria-label="新增注释" @dragstart="startPaletteDrag($event, 'comment')" @click="addComment">注释</button>
              <button type="button" draggable="true" aria-label="新增 create 生命周期" @dragstart="startPaletteDrag($event, 'create')" @click="addCreateLifecycle">create</button>
              <button type="button" draggable="true" aria-label="新增 destroy 生命周期" @dragstart="startPaletteDrag($event, 'destroy')" @click="addDestroyLifecycle">destroy</button>
            </div>
          </section>
          <section>
            <h3>组合片段</h3>
            <div class="ta-sequence-action-grid">
              <button v-for="type in ['loop', 'alt', 'opt', 'par', 'critical', 'break', 'rect'] as MermaidSequenceBlockType[]" :key="type" type="button" draggable="true" :aria-label="`新增 ${type} 片段`" @dragstart="startPaletteDrag($event, `block:${type}`)" @click="addBlock(type)">{{ type }}</button>
            </div>
          </section>
          <section class="ta-sequence-fields">
            <h3>自动编号</h3>
            <label class="ta-sequence-check"><input type="checkbox" aria-label="自动编号" :checked="Boolean(workingDiagram.autonumber?.enabled)" @change="updateAutonumber(($event.target as HTMLInputElement).checked)" />启用 autonumber</label>
            <div v-if="workingDiagram.autonumber?.enabled" class="ta-sequence-inline-fields">
              <label><span>起始</span><input aria-label="自动编号起始值" type="number" min="1" :value="workingDiagram.autonumber.start ?? 1" @input="updateAutonumberNumber('start', ($event.target as HTMLInputElement).value)" /></label>
              <label><span>步长</span><input aria-label="自动编号步长" type="number" min="1" :value="workingDiagram.autonumber.step ?? 1" @input="updateAutonumberNumber('step', ($event.target as HTMLInputElement).value)" /></label>
            </div>
          </section>
        </div>

        <div v-else-if="activeTab === 'structure'" class="ta-sequence-panel" role="tabpanel">
          <section>
            <h3>参与者顺序</h3>
            <div class="ta-sequence-participant-order">
              <article v-for="(participant, index) in workingDiagram.participants" :key="participant.id">
                <button type="button" class="ta-sequence-order-name" @click="selectParticipant(participant.id)">{{ participant.text }} <code>{{ participant.id }}</code></button>
                <button type="button" :aria-label="`左移 ${participant.text}`" :disabled="index === 0" @click="moveParticipant(participant.id, -1)">←</button>
                <button type="button" :aria-label="`右移 ${participant.text}`" :disabled="index === workingDiagram.participants.length - 1" @click="moveParticipant(participant.id, 1)">→</button>
              </article>
            </div>
          </section>
          <section v-if="workingDiagram.groups.length">
            <h3>box 分组</h3>
            <button v-for="group in workingDiagram.groups" :key="group.id" type="button" :aria-label="`选择 box ${group.label || group.id}`" @click="selectGroup(group.id)">{{ group.label || group.id }}</button>
          </section>
          <section>
            <h3>时序结构 <small>Alt + ↑/↓ 排序</small></h3>
            <button type="button" class="ta-sequence-wide-action" aria-label="选择根流程作为插入位置" @click="selectInsertionContainer('root', '根流程')">元素插入到根流程</button>
            <SequenceStructureTree
              :statements="workingDiagram.statements"
              :selected-id="selectedStatementId"
              @select="selectStatement"
              @select-container="selectInsertionContainer"
              @move="moveStatement"
            />
          </section>
        </div>

        <div v-else class="ta-sequence-panel" role="tabpanel">
          <section v-if="selectedGroup" class="ta-sequence-fields">
            <div class="ta-sequence-section-heading"><h3>box 属性</h3><span>{{ selectedGroup.id }}</span></div>
            <label><span>标题</span><input aria-label="box 标题" :value="selectedGroup.label" @input="updateSelectedGroup({ label: ($event.target as HTMLInputElement).value })" /></label>
            <label><span>颜色</span><input aria-label="box 颜色" :value="selectedGroup.color ?? ''" placeholder="Aqua / #dbeafe / rgb(...)" @input="updateSelectedGroup({ color: ($event.target as HTMLInputElement).value })" /></label>
            <small>{{ selectedGroup.participantIds.length }} 个参与者；在参与者属性中调整归属。</small>
          </section>

          <section v-else-if="selectedParticipant" class="ta-sequence-fields">
            <div class="ta-sequence-section-heading"><h3>参与者属性</h3><span>{{ selectedParticipant.type }}</span></div>
            <label><span>ID</span><input aria-label="参与者 ID" :value="selectedParticipant.id" @input="renameSelectedParticipant(($event.target as HTMLInputElement).value)" /></label>
            <label><span>名称</span><textarea aria-label="参与者名称" rows="2" :value="selectedParticipant.text" @input="updateSelectedParticipant({ text: ($event.target as HTMLTextAreaElement).value })" /></label>
            <label><span>类型</span><select aria-label="参与者类型" :value="selectedParticipant.type" @change="updateSelectedParticipant({ type: ($event.target as HTMLSelectElement).value as MermaidSequenceParticipantType })"><option v-for="type in MERMAID_SEQUENCE_PARTICIPANT_TYPES" :key="type" :value="type">{{ type }}</option></select></label>
            <label><span>box</span><select aria-label="参与者分组" :value="selectedParticipant.groupId ?? ''" @change="updateSelectedParticipant({ groupId: ($event.target as HTMLSelectElement).value || undefined })"><option value="">不分组</option><option v-for="group in workingDiagram.groups" :key="group.id" :value="group.id">{{ group.label || group.id }}</option></select></label>
            <button type="button" class="is-danger" @click="deleteSelectedParticipant">删除参与者</button>
          </section>

          <section v-else-if="selectedStatement?.kind === 'message'" class="ta-sequence-fields">
            <div class="ta-sequence-section-heading"><h3>消息属性</h3><code>{{ selectedStatement.source }} → {{ selectedStatement.target }}</code></div>
            <label><span>文本</span><textarea aria-label="消息文本" rows="3" :value="selectedStatement.text" @input="updateSelectedStatement({ text: ($event.target as HTMLTextAreaElement).value })" /></label>
            <label><span>来源</span><select aria-label="消息来源" :value="selectedStatement.source" @change="rebindSelectedMessage('source', ($event.target as HTMLSelectElement).value)"><option v-for="participant in workingDiagram.participants" :key="participant.id" :value="participant.id">{{ participant.text }}</option></select></label>
            <label><span>目标</span><select aria-label="消息目标" :value="selectedStatement.target" @change="rebindSelectedMessage('target', ($event.target as HTMLSelectElement).value)"><option v-for="participant in workingDiagram.participants" :key="participant.id" :value="participant.id">{{ participant.text }}</option></select></label>
            <label><span>箭头</span><select aria-label="消息箭头" :value="selectedStatement.arrow" @change="updateSelectedStatement({ arrow: ($event.target as HTMLSelectElement).value })"><option v-for="arrow in MERMAID_SEQUENCE_ARROWS" :key="arrow" :value="arrow">{{ arrow }}</option></select></label>
            <label><span>快捷激活</span><select aria-label="消息快捷激活" :value="selectedStatement.activation ?? ''" @change="updateSelectedStatement({ activation: ($event.target as HTMLSelectElement).value || undefined })"><option value="">无</option><option value="activate-target">+ 激活目标</option><option value="deactivate-source">- 停用来源</option></select></label>
            <label class="ta-sequence-check"><input type="checkbox" aria-label="create 目标" :checked="Boolean(selectedStatement.create)" @change="toggleMessageLifecycle('create', ($event.target as HTMLInputElement).checked)" />create 目标参与者</label>
            <label class="ta-sequence-check"><input type="checkbox" aria-label="destroy 生命周期" :checked="Boolean(selectedStatement.destroy)" @change="toggleMessageLifecycle('destroy', ($event.target as HTMLInputElement).checked)" />destroy 生命周期</label>
            <label><span>destroy 参与者</span><select aria-label="destroy 参与者" :value="selectedStatement.destroy?.participantId ?? destroyEndpointDrafts[selectedStatement.id] ?? selectedStatement.source" @change="updateDestroyEndpoint(($event.target as HTMLSelectElement).value)"><option :value="selectedStatement.source">来源 · {{ participantText(selectedStatement.source) }}</option><option v-if="selectedStatement.target !== selectedStatement.source" :value="selectedStatement.target">目标 · {{ participantText(selectedStatement.target) }}</option></select></label>
            <div class="ta-sequence-inline-actions"><button type="button" @click="swapSelectedMessage">交换端点</button><button type="button" class="is-danger" @click="deleteSelectedStatement">删除消息</button></div>
          </section>

          <section v-else-if="selectedStatement?.kind === 'note'" class="ta-sequence-fields">
            <h3>Note 属性</h3>
            <label><span>文本</span><textarea aria-label="Note 文本" rows="4" :value="selectedStatement.text" @input="updateSelectedStatement({ text: ($event.target as HTMLTextAreaElement).value })" /></label>
            <label><span>位置</span><select aria-label="Note 位置" :value="selectedStatement.placement" @change="updateSelectedNotePlacement(($event.target as HTMLSelectElement).value as MermaidSequenceNote['placement'])"><option value="left">left of</option><option value="right">right of</option><option value="over">over</option></select></label>
            <label><span>参与者</span><select aria-label="Note 第一参与者" :value="selectedStatement.participants[0]" @change="updateSelectedNoteParticipant(0, ($event.target as HTMLSelectElement).value)"><option v-for="participant in workingDiagram.participants" :key="participant.id" :value="participant.id">{{ participant.text }}</option></select></label>
            <label v-if="selectedStatement.placement === 'over'"><span>第二参与者</span><select aria-label="Note 第二参与者" :value="selectedStatement.participants[1] ?? ''" @change="updateSelectedNoteParticipant(1, ($event.target as HTMLSelectElement).value)"><option value="">无</option><option v-for="participant in workingDiagram.participants" :key="participant.id" :value="participant.id">{{ participant.text }}</option></select></label>
            <button type="button" class="is-danger" @click="deleteSelectedStatement">删除 Note</button>
          </section>

          <section v-else-if="selectedStatement?.kind === 'activation'" class="ta-sequence-fields">
            <h3>激活属性</h3>
            <label><span>参与者</span><select aria-label="激活参与者" :value="selectedStatement.participantId" @change="updateSelectedStatement({ participantId: ($event.target as HTMLSelectElement).value })"><option v-for="participant in workingDiagram.participants" :key="participant.id" :value="participant.id">{{ participant.text }}</option></select></label>
            <label class="ta-sequence-check"><input type="checkbox" aria-label="激活状态" :checked="selectedStatement.active" @change="updateSelectedStatement({ active: ($event.target as HTMLInputElement).checked })" />激活（取消为停用）</label>
            <button type="button" class="is-danger" @click="deleteSelectedStatement">删除激活语句</button>
          </section>

          <section v-else-if="selectedStatement?.kind === 'comment'" class="ta-sequence-fields">
            <h3>注释属性</h3>
            <label><span>文本</span><textarea aria-label="注释文本" rows="4" :value="selectedStatement.text" @input="updateSelectedStatement({ text: ($event.target as HTMLTextAreaElement).value })" /></label>
            <button type="button" class="is-danger" @click="deleteSelectedStatement">删除注释</button>
          </section>

          <section v-else-if="selectedStatement?.kind === 'block'" class="ta-sequence-fields">
            <div class="ta-sequence-section-heading"><h3>片段属性</h3><span>{{ selectedStatement.blockType }}</span></div>
            <label v-for="(branch, index) in selectedStatement.branches" :key="branch.id"><span>{{ index === 0 && selectedStatement.blockType === 'rect' ? '颜色' : `${branch.keyword} 条件/标题` }}</span><textarea :aria-label="`片段分支标题 ${branch.keyword}`" rows="2" :value="branch.label" @input="updateBlockBranchLabel(branch.id, ($event.target as HTMLTextAreaElement).value)" /></label>
            <div class="ta-sequence-branch-summary" v-for="branch in selectedStatement.branches" :key="branch.id"><code>{{ branch.keyword }}</code><span>{{ branch.label || "空分支" }}</span><small>{{ branch.statements.length }} 项</small></div>
            <button v-if="['alt', 'par', 'critical'].includes(selectedStatement.blockType)" type="button" @click="addSelectedBlockBranch">新增分支</button>
            <button type="button" class="is-danger" @click="deleteSelectedStatement">删除片段</button>
          </section>

          <section v-else-if="selectedStatement?.kind === 'locked'" class="ta-sequence-fields ta-sequence-locked-panel">
            <h3>锁定源码</h3>
            <p>{{ selectedStatement.reason }}</p>
            <pre>{{ selectedStatement.raw }}</pre>
            <small>该语句会无损写回，但首批不提供创建、移动或结构化编辑。</small>
          </section>

          <section v-else class="ta-sequence-empty-properties">
            <h3>属性</h3>
            <p>从画布或结构树选择元素后编辑。</p>
          </section>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.ta-sequence-editor { display: flex; min-height: 0; flex: 1; flex-direction: column; color: var(--ta-text, #334155); background: var(--ta-surface, #fff); }
.ta-sequence-toolbar { display: flex; min-height: 46px; align-items: center; gap: 7px; border-bottom: 1px solid var(--ta-border, #e2e8f0); padding: 6px 10px; background: var(--ta-panel-2, #f8fafc); }
.ta-sequence-toolbar-title { display: grid; margin-right: auto; gap: 1px; }
.ta-sequence-toolbar-title strong { color: var(--ta-ink, #172033); font-size: 12px; }
.ta-sequence-toolbar-title span { color: var(--ta-muted, #64748b); font-size: 9px; }
.ta-sequence-toolbar button, .ta-sequence-inspector button, .ta-sequence-inspector input, .ta-sequence-inspector select, .ta-sequence-inspector textarea { border: 1px solid var(--ta-border, #dbe2ea); border-radius: 5px; background: var(--ta-surface, #fff); color: var(--ta-ink, #172033); font: inherit; font-size: 11px; }
.ta-sequence-toolbar button, .ta-sequence-inspector button { min-height: 28px; padding: 0 8px; cursor: pointer; }
.ta-sequence-toolbar button:disabled, .ta-sequence-inspector button:disabled { cursor: not-allowed; opacity: .45; }
.ta-sequence-validation-count { border-radius: 99px; padding: 2px 7px; background: #fff1d6; color: #8a5a09; font-size: 9px; }
.ta-sequence-status { margin: 0; border-bottom: 1px solid #f1d7a8; padding: 6px 10px; background: #fff8e8; color: #7a5310; font-size: 10px; }
.ta-sequence-workspace { display: grid; min-height: 0; flex: 1; grid-template-columns: minmax(0, 1fr) 320px; }
.ta-sequence-canvas { min-height: 470px; overflow: hidden; background-color: #f9fbfd; background-image: radial-gradient(circle at 1px 1px, #d7dee8 1px, transparent 0); background-size: 18px 18px; }
.ta-sequence-canvas :deep(.vue-flow) { height: 100%; min-height: 470px; }
.ta-sequence-canvas :deep(.vue-flow__node-sequence-scene) { cursor: default; box-shadow: 0 14px 36px rgba(40, 55, 80, .08); }
.ta-sequence-inspector { display: flex; min-height: 0; flex-direction: column; overflow: hidden; border-left: 1px solid var(--ta-border, #e2e8f0); background: var(--ta-panel-2, #f8fafc); }
.ta-sequence-tabs { display: grid; grid-template-columns: repeat(3, 1fr); border-bottom: 1px solid var(--ta-border, #e2e8f0); background: #fff; }
.ta-sequence-tabs button { min-height: 37px; border: 0; border-bottom: 2px solid transparent; border-radius: 0; background: transparent; color: #667085; }
.ta-sequence-tabs button[aria-selected="true"] { border-bottom-color: var(--primary, #4f6bed); color: #315bd6; font-weight: 650; }
.ta-sequence-panel { min-height: 0; overflow: auto; }
.ta-sequence-panel section { display: grid; gap: 8px; padding: 12px; border-bottom: 1px solid var(--ta-border, #e2e8f0); }
.ta-sequence-panel h3 { margin: 0; color: var(--ta-ink, #172033); font-size: 11px; }
.ta-sequence-panel h3 small { margin-left: 6px; color: var(--ta-muted, #64748b); font-size: 9px; font-weight: 400; }
.ta-sequence-palette-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 6px; }
.ta-sequence-palette-grid button { display: grid; min-height: 44px; align-content: center; gap: 2px; text-align: left; }
.ta-sequence-palette-grid button span { color: #315bd6; font-family: ui-monospace, monospace; font-size: 10px; font-weight: 650; }
.ta-sequence-palette-grid button small { overflow: hidden; color: #667085; font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }
.ta-sequence-action-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 5px; }
.ta-sequence-action-grid button { padding: 0 4px; font-family: ui-monospace, monospace; font-size: 9px; }
.ta-sequence-wide-action { width: 100%; }
.ta-sequence-fields { display: grid; gap: 8px; }
.ta-sequence-fields > label, .ta-sequence-inline-fields label { display: grid; gap: 3px; color: var(--ta-muted, #64748b); font-size: 9px; }
.ta-sequence-fields input, .ta-sequence-fields select, .ta-sequence-fields textarea { box-sizing: border-box; width: 100%; min-height: 29px; padding: 5px 7px; resize: vertical; }
.ta-sequence-fields .ta-sequence-check { display: flex; align-items: center; gap: 6px; color: #475467; }
.ta-sequence-fields .ta-sequence-check input { width: 14px; min-height: 14px; }
.ta-sequence-inline-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 7px; }
.ta-sequence-inline-actions { display: flex; justify-content: flex-end; gap: 6px; }
.ta-sequence-section-heading { display: flex; align-items: center; justify-content: space-between; }
.ta-sequence-section-heading span, .ta-sequence-section-heading code { border-radius: 4px; padding: 2px 5px; background: #edf2ff; color: #315bd6; font-size: 9px; }
.ta-sequence-participant-order { display: grid; gap: 5px; }
.ta-sequence-participant-order article { display: grid; grid-template-columns: minmax(0, 1fr) 28px 28px; gap: 4px; }
.ta-sequence-participant-order .ta-sequence-order-name { overflow: hidden; text-align: left; text-overflow: ellipsis; white-space: nowrap; }
.ta-sequence-participant-order code { color: #667085; font-size: 9px; }
.ta-sequence-branch-summary { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: 6px; border: 1px solid var(--ta-border, #e2e8f0); border-radius: 5px; padding: 6px; font-size: 9px; }
.ta-sequence-branch-summary code { color: #315bd6; }
.ta-sequence-branch-summary small { color: #667085; }
.ta-sequence-locked-panel p, .ta-sequence-empty-properties p { margin: 0; color: #667085; font-size: 10px; }
.ta-sequence-locked-panel pre { max-height: 210px; overflow: auto; margin: 0; border: 1px dashed #c7ced8; border-radius: 5px; padding: 8px; background: #f4f4f5; color: #475467; font-size: 9px; white-space: pre-wrap; }
.ta-sequence-locked-panel small { color: #667085; font-size: 9px; }
.ta-sequence-inspector .is-danger { color: #b42318; }
@media (max-width: 880px) { .ta-sequence-workspace { grid-template-columns: 1fr; grid-template-rows: minmax(380px, 1fr) 300px; } .ta-sequence-inspector { border-top: 1px solid var(--ta-border, #e2e8f0); border-left: 0; } .ta-sequence-toolbar-title span, .ta-sequence-validation-count { display: none; } }
</style>
