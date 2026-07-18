<script setup lang="ts">
defineOptions({ inheritAttrs: false });

import { computed } from "vue";
import { Handle, Position } from "@vue-flow/core";
import { getMermaidStateNodeSize } from "../layout";
import type { MermaidConnectionStart } from "../../visual-editor/use-mermaid-connection-drag";
import type { StateNodeData } from "./adapter";

const props = defineProps<{ id: string; data: StateNodeData; selected?: boolean }>();
const emit = defineEmits<{
  select: [nodeId: string];
  focusRequest: [scopeId: string];
  editRequest: [payload: { nodeId: string; clientX: number; clientY: number }];
  connectionStart: [start: MermaidConnectionStart];
}>();

const node = computed(() => props.data.node);
const size = computed(() => getMermaidStateNodeSize(node.value, props.data.direction));
const title = computed(() => {
  if (node.value.kind === "start") return "开始节点";
  if (node.value.kind === "end") return "结束节点";
  if (node.value.kind === "choice") return `Choice ${node.value.id}`;
  if (node.value.kind === "fork") return `Fork ${node.value.id}`;
  if (node.value.kind === "join") return `Join ${node.value.id}`;
  return node.value.childScope
    ? `复合状态 ${node.value.label}，双击进入`
    : `状态 ${node.value.id}`;
});
const nodeStyle = computed(() => ({
  width: `${size.value.width}px`,
  height: `${size.value.height}px`,
  color: node.value.style?.textColor,
  backgroundColor: node.value.style?.fillColor,
  borderColor: node.value.style?.strokeColor
}));

const ports = [
  { index: 0, position: Position.Top },
  { index: 1, position: Position.Right },
  { index: 2, position: Position.Bottom },
  { index: 3, position: Position.Left }
] as const;

function handleStyle(index: number, type: "source" | "target") {
  const first = type === "source" ? "42%" : "58%";
  const second = type === "source" ? "42%" : "58%";
  if (index === 0 || index === 2) return { left: first };
  return { top: second };
}

function startConnection(event: PointerEvent, handleId: string, position: Position) {
  if (event.button !== 0) return;
  event.preventDefault();
  event.stopPropagation();
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
  emit("connectionStart", {
    pointerId: event.pointerId,
    nodeId: props.id,
    handleId,
    position,
    point: { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 }
  });
}

function onDoubleClick(event: MouseEvent) {
  event.preventDefault();
  event.stopPropagation();
  if (node.value.childScope) emit("focusRequest", node.value.childScope.id);
  else if (node.value.kind === "state") {
    emit("editRequest", { nodeId: node.value.id, clientX: event.clientX, clientY: event.clientY });
  }
}
</script>

<template>
  <article
    class="ta-state-node"
    :class="[`is-${node.kind}`, { 'is-composite': !!node.childScope, 'is-selected': selected }]"
    :style="nodeStyle"
    :data-mermaid-node-id="id"
    :aria-label="title"
    tabindex="0"
    @click.stop="emit('select', id)"
    @dblclick="onDoubleClick"
    @keydown.enter.stop="node.childScope ? emit('focusRequest', node.childScope.id) : emit('select', id)"
  >
    <template v-if="node.kind === 'state'">
      <strong>{{ node.label }}</strong>
      <span v-if="node.childScope" class="ta-state-node__summary">
        {{ node.childScope.regions.length }} 个区域 · {{ node.childScope.regions.reduce((count, region) => count + region.nodes.length, 0) }} 个子状态
      </span>
      <span v-for="description in node.descriptions" v-else :key="description">{{ description }}</span>
    </template>
    <span v-else-if="node.kind === 'choice'" class="ta-state-node__choice" />
    <span v-else-if="node.kind === 'end'" class="ta-state-node__end" />

    <template v-for="port in ports" :key="port.index">
      <Handle
        :id="`source-${port.index}`"
        type="source"
        :position="port.position"
        :connectable="false"
        class="ta-state-node__handle is-source"
        :style="handleStyle(port.index, 'source')"
        :data-mermaid-handle="`source-${port.index}`"
        :data-mermaid-position="port.position"
        @pointerdown="startConnection($event, `source-${port.index}`, port.position)"
      />
      <Handle
        :id="`target-${port.index}`"
        type="target"
        :position="port.position"
        :connectable="false"
        class="ta-state-node__handle is-target"
        :style="handleStyle(port.index, 'target')"
        :data-mermaid-handle="`target-${port.index}`"
        :data-mermaid-position="port.position"
      />
    </template>
  </article>
</template>

<style scoped>
.ta-state-node { position: relative; box-sizing: border-box; display: grid; place-content: center; gap: 5px; border: 1.5px solid #475569; border-radius: 9px; padding: 9px 13px; background: #fff; color: #172033; text-align: center; cursor: grab; box-shadow: 0 2px 7px rgba(15, 23, 42, .09); }
.ta-state-node:focus-visible, .ta-state-node.is-selected { outline: 2px solid #6366f1; outline-offset: 3px; }
.ta-state-node strong { font-size: 12px; font-weight: 650; }
.ta-state-node span { max-width: 250px; overflow: hidden; color: currentColor; font-size: 10px; line-height: 1.3; text-overflow: ellipsis; white-space: nowrap; }
.ta-state-node.is-composite { border-radius: 6px; background: #f8fafc; }
.ta-state-node__summary { color: #64748b !important; }
.ta-state-node.is-start { border-radius: 50%; padding: 0; background: #172033 !important; }
.ta-state-node.is-end { border: 2px solid #172033; border-radius: 50%; padding: 0; background: #fff !important; }
.ta-state-node__end { width: 17px; height: 17px; border-radius: 50%; background: #172033; }
.ta-state-node.is-choice { transform: rotate(45deg); border-radius: 2px; padding: 0; }
.ta-state-node__choice { transform: rotate(-45deg); font-size: 0; }
.ta-state-node.is-fork, .ta-state-node.is-join { border-radius: 2px; padding: 0; background: #172033; }
.ta-state-node__handle { width: 9px; height: 9px; border: 1.5px solid #fff; background: #6366f1; opacity: 0; transition: opacity 120ms ease; }
.ta-state-node:hover .ta-state-node__handle, .ta-state-node:focus-within .ta-state-node__handle, .ta-state-node.is-selected .ta-state-node__handle { opacity: 1; }
.ta-state-node__handle.is-target { background: #14b8a6; }
</style>
