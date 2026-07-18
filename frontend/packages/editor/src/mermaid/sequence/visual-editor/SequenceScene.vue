<script setup lang="ts">
import { computed, nextTick, ref } from "vue";
import { flattenMermaidSequenceStatements, type MermaidSequenceDiagram } from "../model";
import type { MermaidSequenceLayout, MermaidSequencePoint } from "../layout";

const props = defineProps<{
  data: { diagram: MermaidSequenceDiagram; layout: MermaidSequenceLayout };
  selectedId?: string;
}>();

const emit = defineEmits<{
  selectGroup: [groupId: string];
  selectParticipant: [participantId: string];
  selectStatement: [statementId: string];
  editStatement: [statementId: string];
  editText: [kind: "participant" | "statement", id: string, value: string];
  moveParticipant: [participantId: string, targetIndex: number];
  rebindMessage: [messageId: string, endpoint: "source" | "target", participantId: string];
  createMessage: [source: string, target: string];
}>();

type InlineEditor = {
  kind: "participant" | "statement";
  id: string;
  value: string;
  x: number;
  y: number;
  width: number;
};

const inlineEditor = ref<InlineEditor>();
const inlineInput = ref<HTMLTextAreaElement>();
const sceneSvg = ref<SVGSVGElement>();
type SceneDrag =
  | { kind: "participant"; participantId: string }
  | { kind: "endpoint"; messageId: string; endpoint: "source" | "target" }
  | { kind: "lifeline"; source: string };
const sceneDrag = ref<SceneDrag>();
const dragPreview = ref<{ x1: number; y1: number; x2: number; y2: number }>();

const participantById = computed(() => new Map(props.data.diagram.participants.map((participant) => [participant.id, participant])));
const statementById = computed(() => new Map(flattenMermaidSequenceStatements(props.data.diagram.statements).map((statement) => [statement.id, statement])));

function path(points: MermaidSequencePoint[]): string {
  return points.map((point, index) => `${index === 0 ? "M" : "L"} ${point.x} ${point.y}`).join(" ");
}

function dashed(arrow: string): boolean {
  return arrow.startsWith("--") || arrow === "<<-->>";
}

function endMarker(arrow: string): string {
  if (arrow.endsWith("x")) return "url(#sequence-cross)";
  if (arrow === "->" || arrow === "-->" || arrow.endsWith(")")) return "url(#sequence-open)";
  return "url(#sequence-closed)";
}

function startMarker(arrow: string): string | undefined {
  return arrow.startsWith("<<") ? "url(#sequence-closed-start)" : undefined;
}

function selectStatement(id: string) {
  emit("selectStatement", id);
}

function scenePoint(event: PointerEvent): MermaidSequencePoint {
  const bounds = sceneSvg.value?.getBoundingClientRect();
  if (bounds?.width && bounds.height) {
    return {
      x: (event.clientX - bounds.left) * props.data.layout.width / bounds.width,
      y: (event.clientY - bounds.top) * props.data.layout.height / bounds.height
    };
  }
  return { x: event.offsetX || event.clientX, y: event.offsetY || event.clientY };
}

function nearestParticipant(x: number) {
  return props.data.layout.participants.reduce((nearest, participant) =>
    Math.abs(participant.centerX - x) < Math.abs(nearest.centerX - x) ? participant : nearest
  );
}

function startParticipantDrag(event: PointerEvent, participantId: string) {
  if (event.button !== 0) return;
  const participant = props.data.layout.participants.find((item) => item.id === participantId);
  if (!participant) return;
  event.stopPropagation();
  sceneDrag.value = { kind: "participant", participantId };
  dragPreview.value = { x1: participant.centerX, y1: participant.y, x2: participant.centerX, y2: participant.y };
}

function startEndpointDrag(event: PointerEvent, messageId: string, endpoint: "source" | "target") {
  if (event.button !== 0) return;
  const message = props.data.layout.messages.find((item) => item.id === messageId);
  const point = endpoint === "source" ? message?.points[0] : message?.points.at(-1);
  if (!point) return;
  event.stopPropagation();
  sceneDrag.value = { kind: "endpoint", messageId, endpoint };
  dragPreview.value = { x1: point.x, y1: point.y, x2: point.x, y2: point.y };
}

function startLifelineMessage(event: PointerEvent, source: string) {
  if (event.button !== 0) return;
  const lifeline = props.data.layout.lifelines.find((item) => item.participantId === source);
  if (!lifeline) return;
  event.stopPropagation();
  const point = scenePoint(event);
  sceneDrag.value = { kind: "lifeline", source };
  dragPreview.value = { x1: lifeline.x, y1: point.y, x2: lifeline.x, y2: point.y };
}

function onScenePointerMove(event: PointerEvent) {
  if (!sceneDrag.value || !dragPreview.value) return;
  const point = scenePoint(event);
  dragPreview.value = { ...dragPreview.value, x2: point.x, y2: point.y };
}

function onScenePointerUp(event: PointerEvent) {
  const drag = sceneDrag.value;
  if (!drag) return;
  const point = scenePoint(event);
  const nearest = nearestParticipant(point.x);
  if (drag.kind === "participant") {
    const currentIndex = props.data.layout.participants.findIndex((item) => item.id === drag.participantId);
    const targetIndex = props.data.layout.participants.findIndex((item) => item.id === nearest.id);
    if (targetIndex !== currentIndex) emit("moveParticipant", drag.participantId, targetIndex);
  } else if (drag.kind === "endpoint") {
    emit("rebindMessage", drag.messageId, drag.endpoint, nearest.id);
  } else {
    emit("createMessage", drag.source, nearest.id);
  }
  sceneDrag.value = undefined;
  dragPreview.value = undefined;
}

function cancelSceneDrag() {
  sceneDrag.value = undefined;
  dragPreview.value = undefined;
}

function activationRowLabel(id: string): string {
  const statement = statementById.value.get(id);
  return statement?.kind === "activation"
    ? `${statement.active ? "activate" : "deactivate"} ${statement.participantId}`
    : "activation";
}

function activationRowX(id: string): number {
  const statement = statementById.value.get(id);
  if (statement?.kind !== "activation") return 18;
  return props.data.layout.participants.find((participant) => participant.id === statement.participantId)?.centerX ?? 18;
}

function activateByKeyboard(event: KeyboardEvent, callback: () => void) {
  if (event.key !== "Enter" && event.key !== " ") return;
  event.preventDefault();
  callback();
}

function sourceRowLabel(id: string): string {
  const statement = statementById.value.get(id);
  if (statement?.kind === "locked") return `锁定 · ${statement.reason}`;
  if (statement?.kind === "comment") return `%% ${statement.text}`;
  return "";
}

function openInlineEditor(kind: "participant" | "statement", id: string) {
  let value = "";
  let x = 24;
  let y = 24;
  let width = 220;
  if (kind === "participant") {
    const participant = participantById.value.get(id);
    const geometry = props.data.layout.participants.find((item) => item.id === id);
    if (!participant || !geometry) return;
    value = participant.text;
    x = geometry.x + 6;
    y = geometry.y + 8;
    width = geometry.width - 12;
    emit("selectParticipant", id);
  } else {
    const statement = statementById.value.get(id);
    if (!statement || statement.kind === "locked" || statement.kind === "activation") return;
    value = statement.kind === "block" ? statement.branches[0]?.label ?? "" : statement.text;
    const message = props.data.layout.messages.find((item) => item.id === id);
    const note = props.data.layout.notes.find((item) => item.id === id);
    const block = props.data.layout.blocks.find((item) => item.id === id);
    const row = props.data.layout.rows.find((item) => item.id === id);
    if (message) {
      width = Math.min(280, Math.max(180, props.data.layout.width - 32));
      x = message.labelX - width / 2;
      y = message.labelY - 22;
    } else if (note) {
      x = note.x + 6;
      y = note.y + 6;
      width = note.width - 12;
    } else if (block) {
      x = block.x + 98;
      y = block.y + 3;
      width = Math.min(250, block.width - 110);
    } else if (row) {
      x = 28;
      y = row.y + 5;
      width = props.data.layout.width - 56;
    }
    emit("selectStatement", id);
    emit("editStatement", id);
  }
  width = Math.max(100, width);
  x = Math.max(8, Math.min(x, props.data.layout.width - width - 8));
  inlineEditor.value = { kind, id, value, x, y, width };
  void nextTick(() => {
    inlineInput.value?.focus();
    inlineInput.value?.select();
  });
}

function commitInlineEditor() {
  const editor = inlineEditor.value;
  if (!editor) return;
  inlineEditor.value = undefined;
  emit("editText", editor.kind, editor.id, editor.value);
}

function onInlineKeydown(event: KeyboardEvent) {
  if (event.key === "Escape") {
    event.preventDefault();
    inlineEditor.value = undefined;
    return;
  }
  if (event.key === "Enter" && (event.ctrlKey || event.metaKey)) {
    event.preventDefault();
    commitInlineEditor();
  }
}
</script>

<template>
  <svg
    ref="sceneSvg"
    class="ta-sequence-scene"
    :width="data.layout.width"
    :height="data.layout.height"
    :viewBox="`0 0 ${data.layout.width} ${data.layout.height}`"
    role="img"
    aria-label="Sequence diagram 结构化场景"
    @pointermove="onScenePointerMove"
    @pointerup="onScenePointerUp"
    @pointercancel="cancelSceneDrag"
  >
    <defs>
      <marker id="sequence-closed" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M 0 0 L 8 4 L 0 8 Z" /></marker>
      <marker id="sequence-closed-start" markerWidth="8" markerHeight="8" refX="1" refY="4" orient="auto-start-reverse"><path d="M 8 0 L 0 4 L 8 8 Z" /></marker>
      <marker id="sequence-open" markerWidth="9" markerHeight="9" refX="8" refY="4.5" orient="auto"><path d="M 0 0 L 8 4.5 L 0 9" fill="none" /></marker>
      <marker id="sequence-cross" markerWidth="10" markerHeight="10" refX="8" refY="5" orient="auto"><path d="M 1 1 L 9 9 M 9 1 L 1 9" fill="none" /></marker>
    </defs>

    <g
      v-for="group in data.layout.groups"
      :key="group.id"
      class="ta-sequence-group"
      :class="{ 'is-selected': selectedId === group.id }"
      role="button"
      tabindex="0"
      :aria-label="`选择 box ${group.label || group.id}`"
      @click.stop="emit('selectGroup', group.id)"
      @keydown="activateByKeyboard($event, () => emit('selectGroup', group.id))"
    >
      <rect :x="group.x" :y="group.y" :width="group.width" :height="group.height" rx="8" :style="group.color ? { '--group-color': group.color } : undefined" />
      <text :x="group.x + 10" :y="group.y + 16">{{ group.label || "box" }}</text>
    </g>

    <g
      v-for="block in data.layout.blocks"
      :key="block.id"
      class="ta-sequence-block"
      :class="[{ 'is-selected': selectedId === block.id }, `is-${block.blockType}`]"
      :style="block.color ? { '--rect-color': block.color } : undefined"
      role="button"
      tabindex="0"
      :aria-label="`选择 ${block.blockType} 片段`"
      @click.stop="selectStatement(block.id)"
      @dblclick.stop="openInlineEditor('statement', block.id)"
      @keydown="activateByKeyboard($event, () => selectStatement(block.id))"
    >
      <rect :x="block.x" :y="block.y" :width="block.width" :height="block.height" rx="7" />
      <path class="ta-sequence-block-tab" :d="`M ${block.x} ${block.y + 25} H ${block.x + 82} L ${block.x + 94} ${block.y} H ${block.x}`" />
      <text :x="block.x + 8" :y="block.y + 17" class="ta-sequence-block-kind">{{ block.blockType }}</text>
      <text :x="block.x + 102" :y="block.y + 18" class="ta-sequence-block-label">{{ block.label }}</text>
      <template v-for="(branch, index) in block.branches" :key="branch.id">
        <line v-if="index > 0" :x1="block.x" :x2="block.x + block.width" :y1="branch.y - 14" :y2="branch.y - 14" />
        <text v-if="index > 0" :x="block.x + 9" :y="branch.y - 18" class="ta-sequence-branch-label">{{ branch.label }}</text>
      </template>
    </g>

    <line
      v-for="lifeline in data.layout.lifelines"
      :key="lifeline.participantId"
      class="ta-sequence-lifeline"
      :x1="lifeline.x"
      :x2="lifeline.x"
      :y1="lifeline.startY"
      :y2="lifeline.endY"
    />

    <line
      v-for="lifeline in data.layout.lifelines"
      :key="`hit-${lifeline.participantId}`"
      class="ta-sequence-lifeline-hit"
      :x1="lifeline.x"
      :x2="lifeline.x"
      :y1="lifeline.startY"
      :y2="lifeline.endY"
      role="button"
      tabindex="0"
      :aria-label="`从 ${participantById.get(lifeline.participantId)?.text ?? lifeline.participantId} 生命线创建消息`"
      @pointerdown="startLifelineMessage($event, lifeline.participantId)"
    />

    <rect
      v-for="activation in data.layout.activations"
      :key="`${activation.statementId}-${activation.depth}`"
      class="ta-sequence-activation"
      :x="activation.x"
      :y="activation.y"
      :width="activation.width"
      :height="activation.height"
      :class="{ 'is-selected': selectedId === activation.statementId }"
      role="button"
      tabindex="0"
      :aria-label="`选择激活条 ${activation.participantId}`"
      @click.stop="selectStatement(activation.statementId)"
      @keydown="activateByKeyboard($event, () => selectStatement(activation.statementId))"
    />

    <g
      v-for="message in data.layout.messages"
      :key="message.id"
      class="ta-sequence-message"
      :class="{ 'is-selected': selectedId === message.id, 'is-dashed': dashed(message.arrow) }"
      role="button"
      tabindex="0"
      :aria-label="`选择消息 ${message.text || `${message.source} 到 ${message.target}`}`"
      @click.stop="selectStatement(message.id)"
      @dblclick.stop="openInlineEditor('statement', message.id)"
      @keydown="activateByKeyboard($event, () => selectStatement(message.id))"
    >
      <path class="ta-sequence-message-hit" :d="path(message.points)" />
      <path
        class="ta-sequence-message-line"
        :d="path(message.points)"
        :marker-end="endMarker(message.arrow)"
        :marker-start="startMarker(message.arrow)"
      />
      <text :x="message.labelX" :y="message.labelY" text-anchor="middle">
        <tspan v-for="(line, index) in message.text.split('\n')" :key="index" :x="message.labelX" :dy="index === 0 ? 0 : 14">{{ index === 0 && message.sequenceNumber !== undefined ? `${message.sequenceNumber}. ${line}` : line }}</tspan>
      </text>
      <template v-if="selectedId === message.id">
        <circle
          class="ta-sequence-endpoint-handle"
          :cx="message.points[0]?.x"
          :cy="message.points[0]?.y"
          r="6"
          role="button"
          tabindex="0"
          :aria-label="`拖动消息来源 ${message.text || message.id}`"
          @pointerdown="startEndpointDrag($event, message.id, 'source')"
        />
        <circle
          class="ta-sequence-endpoint-handle"
          :cx="message.points.at(-1)?.x"
          :cy="message.points.at(-1)?.y"
          r="6"
          role="button"
          tabindex="0"
          :aria-label="`拖动消息目标 ${message.text || message.id}`"
          @pointerdown="startEndpointDrag($event, message.id, 'target')"
        />
      </template>
    </g>

    <g
      v-for="row in data.layout.rows.filter((item) => item.kind === 'activation')"
      :key="`activation-row-${row.id}`"
      class="ta-sequence-activation-row"
      :class="{ 'is-selected': selectedId === row.id }"
      role="button"
      tabindex="0"
      :aria-label="`选择 ${activationRowLabel(row.id)}`"
      @click.stop="selectStatement(row.id)"
      @keydown="activateByKeyboard($event, () => selectStatement(row.id))"
    >
      <rect :x="activationRowX(row.id) - 34" :y="row.y + 4" width="68" :height="row.height - 8" rx="10" />
      <text :x="activationRowX(row.id)" :y="row.y + 19" text-anchor="middle">{{ activationRowLabel(row.id) }}</text>
    </g>

    <g
      v-for="note in data.layout.notes"
      :key="note.id"
      class="ta-sequence-note"
      :class="{ 'is-selected': selectedId === note.id }"
      role="button"
      tabindex="0"
      :aria-label="`选择 Note ${note.text}`"
      @click.stop="selectStatement(note.id)"
      @dblclick.stop="openInlineEditor('statement', note.id)"
      @keydown="activateByKeyboard($event, () => selectStatement(note.id))"
    >
      <path :d="`M ${note.x} ${note.y} H ${note.x + note.width - 13} L ${note.x + note.width} ${note.y + 13} V ${note.y + note.height} H ${note.x} Z`" />
      <path class="ta-sequence-note-fold" :d="`M ${note.x + note.width - 13} ${note.y} V ${note.y + 13} H ${note.x + note.width}`" />
      <text :x="note.x + 10" :y="note.y + 20">
        <tspan v-for="(line, index) in note.text.split('\n')" :key="index" :x="note.x + 10" :dy="index === 0 ? 0 : 17">{{ line }}</tspan>
      </text>
    </g>

    <g
      v-for="row in data.layout.rows.filter((item) => item.kind === 'comment' || item.kind === 'locked')"
      :key="row.id"
      class="ta-sequence-source-row"
      :class="{ 'is-locked': statementById.get(row.id)?.kind === 'locked', 'is-selected': selectedId === row.id }"
      role="button"
      tabindex="0"
      :aria-label="`选择${statementById.get(row.id)?.kind === 'locked' ? '锁定源码' : '注释'}`"
      @click.stop="selectStatement(row.id)"
      @dblclick.stop="openInlineEditor('statement', row.id)"
      @keydown="activateByKeyboard($event, () => selectStatement(row.id))"
    >
      <rect x="22" :y="row.y + 3" :width="data.layout.width - 44" :height="row.height - 6" rx="6" />
      <text x="34" :y="row.y + 22">{{ sourceRowLabel(row.id) }}</text>
    </g>

    <g v-for="destroy in data.layout.destroys" :key="destroy.statementId" class="ta-sequence-destroy">
      <path :d="`M ${destroy.x - 8} ${destroy.y - 8} L ${destroy.x + 8} ${destroy.y + 8} M ${destroy.x + 8} ${destroy.y - 8} L ${destroy.x - 8} ${destroy.y + 8}`" />
    </g>

    <g
      v-for="participant in data.layout.participants"
      :key="participant.id"
      class="ta-sequence-participant"
      :class="[{ 'is-selected': selectedId === participant.id }, `is-${participantById.get(participant.id)?.type}`]"
      role="button"
      tabindex="0"
      :aria-label="`选择参与者 ${participantById.get(participant.id)?.text}`"
      @click.stop="emit('selectParticipant', participant.id)"
      @pointerdown="startParticipantDrag($event, participant.id)"
      @dblclick.stop="openInlineEditor('participant', participant.id)"
      @keydown="activateByKeyboard($event, () => emit('selectParticipant', participant.id))"
    >
      <rect
        v-if="participantById.get(participant.id)?.type === 'collections'"
        class="ta-sequence-participant-back"
        :x="participant.x + 5"
        :y="participant.y - 5"
        :width="participant.width"
        :height="participant.height"
        rx="7"
      />
      <rect class="ta-sequence-participant-body" :x="participant.x" :y="participant.y" :width="participant.width" :height="participant.height" rx="7" />
      <path v-if="participantById.get(participant.id)?.type === 'boundary'" class="ta-sequence-participant-symbol" :d="`M ${participant.x + 17} ${participant.y + 9} V ${participant.y + participant.height - 9} M ${participant.x + 8} ${participant.y + participant.height / 2} H ${participant.x + 17}`" />
      <template v-else-if="participantById.get(participant.id)?.type === 'control'">
        <circle class="ta-sequence-participant-symbol" :cx="participant.x + 17" :cy="participant.y + participant.height / 2" r="8" />
        <path class="ta-sequence-participant-symbol" :d="`M ${participant.x + 17} ${participant.y + 14} l 4 -5 M ${participant.x + 17} ${participant.y + 14} l -5 -2`" />
      </template>
      <path v-else-if="participantById.get(participant.id)?.type === 'entity'" class="ta-sequence-participant-symbol" :d="`M ${participant.x + 8} ${participant.y + participant.height - 8} H ${participant.x + participant.width - 8}`" />
      <template v-else-if="participantById.get(participant.id)?.type === 'database'">
        <ellipse class="ta-sequence-participant-symbol" :cx="participant.centerX" :cy="participant.y + 9" :rx="participant.width / 2 - 1" ry="8" />
        <path class="ta-sequence-participant-symbol" :d="`M ${participant.x + 1} ${participant.y + participant.height - 8} A ${participant.width / 2 - 1} 8 0 0 0 ${participant.x + participant.width - 1} ${participant.y + participant.height - 8}`" />
      </template>
      <path v-else-if="participantById.get(participant.id)?.type === 'queue'" class="ta-sequence-participant-symbol" :d="`M ${participant.x + 13} ${participant.y + 8} Q ${participant.x + 3} ${participant.y + participant.height / 2} ${participant.x + 13} ${participant.y + participant.height - 8}`" />
      <text :x="participant.centerX" :y="participant.y + 23" text-anchor="middle" class="ta-sequence-participant-name">{{ participantById.get(participant.id)?.text }}</text>
      <text :x="participant.centerX" :y="participant.y + 41" text-anchor="middle" class="ta-sequence-participant-type">{{ participantById.get(participant.id)?.type }}</text>
    </g>

    <line
      v-if="dragPreview"
      class="ta-sequence-drag-preview"
      :x1="dragPreview.x1"
      :y1="dragPreview.y1"
      :x2="dragPreview.x2"
      :y2="dragPreview.y2"
    />

    <foreignObject
      v-if="inlineEditor"
      class="ta-sequence-inline-editor nodrag nowheel"
      :x="inlineEditor.x"
      :y="inlineEditor.y"
      :width="inlineEditor.width"
      height="56"
    >
      <div xmlns="http://www.w3.org/1999/xhtml">
        <textarea
          ref="inlineInput"
          v-model="inlineEditor.value"
          aria-label="就地编辑文本"
          rows="2"
          @keydown="onInlineKeydown"
          @blur="commitInlineEditor"
          @pointerdown.stop
          @dblclick.stop
        />
      </div>
    </foreignObject>
  </svg>
</template>

<style scoped>
.ta-sequence-scene { display: block; overflow: visible; background: #fff; color: #172033; font: 11px ui-sans-serif, system-ui, sans-serif; }
.ta-sequence-group rect { fill: #f5f8fc; stroke: #b7c2d0; fill: color-mix(in srgb, var(--group-color, #dbeafe) 12%, #fff); stroke: color-mix(in srgb, var(--group-color, #94a3b8) 50%, #cbd5e1); }
.ta-sequence-group.is-selected rect { stroke: #315bd6; stroke-width: 2; }
.ta-sequence-group text { fill: #64748b; font-size: 10px; font-weight: 600; }
.ta-sequence-block { cursor: pointer; }
.ta-sequence-block > rect { fill: rgba(248, 250, 252, .44); stroke: #94a3b8; stroke-dasharray: 4 3; }
.ta-sequence-block.is-rect > rect { fill: rgba(219, 234, 254, .35); stroke-dasharray: none; }
.ta-sequence-block.is-rect > rect { fill: var(--rect-color, rgba(219, 234, 254, .35)); fill-opacity: .28; }
.ta-sequence-block.is-selected > rect { stroke: #4f6bed; stroke-width: 2; }
.ta-sequence-block-tab { fill: #edf2ff; stroke: #94a3b8; }
.ta-sequence-block-kind { fill: #3b5ccc; font-size: 10px; font-weight: 700; }
.ta-sequence-block-label, .ta-sequence-branch-label { fill: #64748b; font-size: 10px; }
.ta-sequence-block line { stroke: #b8c2d1; stroke-dasharray: 5 4; }
.ta-sequence-lifeline { stroke: #a8b3c3; stroke-dasharray: 5 4; }
.ta-sequence-lifeline-hit { stroke: transparent; stroke-width: 16; cursor: crosshair; }
.ta-sequence-activation { fill: #dbe6ff; stroke: #6280d9; }
.ta-sequence-activation.is-selected { fill: #c9d8ff; stroke: #315bd6; stroke-width: 2; }
.ta-sequence-activation-row { cursor: pointer; }
.ta-sequence-activation-row rect { fill: #f3f6fb; stroke: #b7c2d0; }
.ta-sequence-activation-row text { fill: #667085; font-size: 8px; }
.ta-sequence-activation-row.is-selected rect { fill: #edf2ff; stroke: #315bd6; }
.ta-sequence-message { cursor: pointer; }
.ta-sequence-message-line { fill: none; stroke: #57657a; stroke-width: 1.4; }
.ta-sequence-message.is-dashed .ta-sequence-message-line { stroke-dasharray: 5 4; }
.ta-sequence-message.is-selected .ta-sequence-message-line { stroke: #315bd6; stroke-width: 2.4; }
.ta-sequence-message-hit { fill: none; stroke: transparent; stroke-width: 14; }
.ta-sequence-message text { fill: #344054; paint-order: stroke; stroke: #fff; stroke-width: 3px; stroke-linejoin: round; }
.ta-sequence-endpoint-handle { fill: #fff; stroke: #22a06b; stroke-width: 2; cursor: crosshair; }
.ta-sequence-scene :deep(marker path) { stroke: #57657a; fill: #57657a; }
.ta-sequence-note { cursor: pointer; }
.ta-sequence-note path { fill: #fff8d8; stroke: #c9a94e; }
.ta-sequence-note .ta-sequence-note-fold { fill: none; }
.ta-sequence-note.is-selected path { stroke: #315bd6; stroke-width: 2; }
.ta-sequence-note text { fill: #5f4d1e; }
.ta-sequence-source-row rect { fill: #f8fafc; stroke: #cbd5e1; }
.ta-sequence-source-row.is-locked rect { fill: #f4f4f5; stroke-dasharray: 4 3; }
.ta-sequence-source-row.is-selected rect { stroke: #315bd6; stroke-width: 2; }
.ta-sequence-source-row text { fill: #667085; font-family: ui-monospace, monospace; font-size: 10px; }
.ta-sequence-destroy path { fill: none; stroke: #c2413b; stroke-width: 2; }
.ta-sequence-participant { cursor: ew-resize; }
.ta-sequence-participant rect { fill: #f8fbff; stroke: #718096; }
.ta-sequence-participant .ta-sequence-participant-back { fill: #eef4ff; }
.ta-sequence-participant .ta-sequence-participant-symbol { fill: none; stroke: #718096; stroke-width: 1.3; }
.ta-sequence-participant.is-selected .ta-sequence-participant-body { fill: #edf2ff; stroke: #315bd6; stroke-width: 2; }
.ta-sequence-participant.is-selected .ta-sequence-participant-symbol { stroke: #315bd6; }
.ta-sequence-participant-name { fill: #172033; font-weight: 650; }
.ta-sequence-participant-type { fill: #667085; font-size: 9px; }
.ta-sequence-participant.is-actor rect { rx: 24px; }
.ta-sequence-inline-editor { overflow: visible; }
.ta-sequence-inline-editor div { box-sizing: border-box; width: 100%; height: 100%; border: 1px solid #315bd6; border-radius: 6px; padding: 3px; background: #fff; box-shadow: 0 8px 20px rgba(30, 50, 90, .2); }
.ta-sequence-inline-editor textarea { box-sizing: border-box; width: 100%; height: 100%; resize: none; border: 0; outline: 0; padding: 3px 5px; background: transparent; color: #172033; font: 11px ui-sans-serif, system-ui, sans-serif; }
.ta-sequence-drag-preview { pointer-events: none; stroke: #315bd6; stroke-width: 2; stroke-dasharray: 5 4; }
</style>
