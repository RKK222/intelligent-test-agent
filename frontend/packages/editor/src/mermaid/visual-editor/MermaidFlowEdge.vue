<script setup lang="ts">
import { computed } from "vue";
import { BaseEdge, getSmoothStepPath, Position, type EdgeProps } from "@vue-flow/core";

const props = defineProps<EdgeProps>();
const emit = defineEmits<{
  reconnectStart: [
    payload: {
      edgeId: string;
      end: "source" | "target";
      pointerId: number;
      fixedNodeId: string;
      fixedHandleId: string;
      fixedPosition: Position;
    }
  ];
}>();

const path = computed(() =>
  getSmoothStepPath({
    sourceX: props.sourceX,
    sourceY: props.sourceY,
    sourcePosition: props.sourcePosition,
    targetX: props.targetX,
    targetY: props.targetY,
    targetPosition: props.targetPosition
  })[0]
);

/** 自定义边不会从 Vue Flow 拿到 labelX/labelY，这里取边中点放标签。 */
const labelPos = computed(() => ({
  x: (props.sourceX + props.targetX) / 2,
  y: (props.sourceY + props.targetY) / 2
}));

/** 按下端点圆圈开始重连：被拖动端的另一端作为固定端。end='target' 时固定 source，end='source' 时固定 target。 */
function onHandlePointerDown(event: PointerEvent, end: "source" | "target") {
  if (event.button !== 0) return;
  event.preventDefault();
  event.stopPropagation();
  const fixed =
    end === "target"
      ? { nodeId: props.source, handleId: props.sourceHandleId, position: props.sourcePosition }
      : { nodeId: props.target, handleId: props.targetHandleId, position: props.targetPosition };
  emit("reconnectStart", {
    edgeId: props.id,
    end,
    pointerId: event.pointerId,
    fixedNodeId: fixed.nodeId,
    fixedHandleId: fixed.handleId ?? "",
    fixedPosition: fixed.position
  });
}
</script>

<template>
  <BaseEdge :path="path" :style="props.style" :marker-end="props.markerEnd" :marker-start="props.markerStart" />
  <text
    v-if="props.label"
    :x="labelPos.x"
    :y="labelPos.y"
    text-anchor="middle"
    dominant-baseline="central"
    class="ta-mermaid-edge-label"
  >{{ props.label }}</text>
  <template v-if="props.selected">
    <circle
      :cx="props.sourceX"
      :cy="props.sourceY"
      r="6"
      class="ta-mermaid-edge-handle"
      aria-label="拖动起点重连"
      @pointerdown="onHandlePointerDown($event, 'source')"
    />
    <circle
      :cx="props.targetX"
      :cy="props.targetY"
      r="6"
      class="ta-mermaid-edge-handle"
      aria-label="拖动终点重连"
      @pointerdown="onHandlePointerDown($event, 'target')"
    />
  </template>
</template>

<style scoped>
.ta-mermaid-edge-label {
  fill: var(--ta-ink, #172033);
  font-size: 11px;
  font-weight: 600;
  /* 白色描边光晕，保证文字压在连线上也可读 */
  paint-order: stroke;
  stroke: #ffffff;
  stroke-width: 4;
  stroke-linejoin: round;
  pointer-events: none;
  user-select: none;
}
.ta-mermaid-edge-handle {
  fill: #22c55e;
  stroke: #ffffff;
  stroke-width: 2;
  cursor: grab;
  filter: drop-shadow(0 1px 2px rgba(15, 23, 42, 0.35));
}
.ta-mermaid-edge-handle:active {
  cursor: grabbing;
}
</style>
