<script setup lang="ts">
defineOptions({ inheritAttrs: false });

import { computed } from "vue";
import { BaseEdge, getSmoothStepPath, type EdgeProps, type Position } from "@vue-flow/core";
import type { StateTransitionData } from "./adapter";

type StateTransitionEdgeProps = {
  id: string;
  source: string;
  target: string;
  sourceX: number;
  sourceY: number;
  targetX: number;
  targetY: number;
  sourcePosition: Position;
  targetPosition: Position;
  sourceHandleId?: string | null;
  targetHandleId?: string | null;
  markerEnd?: EdgeProps<StateTransitionData>["markerEnd"];
  label?: EdgeProps<StateTransitionData>["label"];
  selected?: boolean;
  data?: StateTransitionData;
};

// 自定义边只声明实际消费的 Vue Flow 属性，避免把内部节点实例等属性耦合到状态图组件。
const props = defineProps<StateTransitionEdgeProps>();
const emit = defineEmits<{
  editRequest: [payload: { transitionId: string; clientX: number; clientY: number }];
  reconnectStart: [payload: {
    transitionId: string;
    end: "source" | "target";
    pointerId: number;
    fixedNodeId: string;
    fixedHandleId: string;
    fixedPosition: Position;
  }];
}>();

const path = computed(() => getSmoothStepPath({
  sourceX: props.sourceX,
  sourceY: props.sourceY,
  sourcePosition: props.sourcePosition,
  targetX: props.targetX,
  targetY: props.targetY,
  targetPosition: props.targetPosition,
  borderRadius: 10
})[0]);
const labelX = computed(() => (props.sourceX + props.targetX) / 2);
const labelY = computed(() => (props.sourceY + props.targetY) / 2);
const ariaLabel = computed(() => `转换 ${props.source} 到 ${props.target}${props.label ? `：${String(props.label)}` : ""}`);

function requestEdit(clientX: number, clientY: number) {
  emit("editRequest", {
    transitionId: props.data?.transitionId ?? props.id,
    clientX,
    clientY
  });
}

function edit(event: MouseEvent) {
  event.preventDefault();
  event.stopPropagation();
  requestEdit(event.clientX, event.clientY);
}

function editFromKeyboard(event: KeyboardEvent) {
  event.preventDefault();
  event.stopPropagation();
  requestEdit(0, 0);
}

function reconnect(event: PointerEvent, end: "source" | "target") {
  if (event.button !== 0) return;
  event.preventDefault();
  event.stopPropagation();
  const fixed = end === "target"
    ? { nodeId: props.source, handleId: props.sourceHandleId ?? "", position: props.sourcePosition }
    : { nodeId: props.target, handleId: props.targetHandleId ?? "", position: props.targetPosition };
  emit("reconnectStart", {
    transitionId: props.data?.transitionId ?? props.id,
    end,
    pointerId: event.pointerId,
    fixedNodeId: fixed.nodeId,
    fixedHandleId: fixed.handleId,
    fixedPosition: fixed.position
  });
}
</script>

<template>
  <BaseEdge :path="path" :marker-end="props.markerEnd" />
  <path
    :d="path"
    fill="none"
    stroke="transparent"
    stroke-width="20"
    pointer-events="stroke"
    tabindex="0"
    role="button"
    :aria-label="ariaLabel"
    @dblclick="edit"
    @keydown.enter="editFromKeyboard"
  />
  <text
    v-if="props.label"
    :x="labelX"
    :y="labelY"
    class="ta-state-transition-label"
    text-anchor="middle"
    dominant-baseline="central"
  >{{ props.label }}</text>
  <template v-if="props.selected">
    <circle
      :cx="props.sourceX"
      :cy="props.sourceY"
      r="6"
      class="ta-state-transition-handle"
      aria-label="拖动状态转换起点重连"
      @pointerdown="reconnect($event, 'source')"
    />
    <circle
      :cx="props.targetX"
      :cy="props.targetY"
      r="6"
      class="ta-state-transition-handle"
      aria-label="拖动状态转换终点重连"
      @pointerdown="reconnect($event, 'target')"
    />
  </template>
</template>

<style scoped>
.ta-state-transition-label { fill: #334155; font-size: 11px; font-weight: 600; paint-order: stroke; stroke: #fff; stroke-width: 4px; pointer-events: none; }
.ta-state-transition-handle { fill: #22c55e; stroke: #fff; stroke-width: 2px; cursor: grab; }
</style>
