<script setup lang="ts">
import { computed } from "vue";
import { BaseEdge, type EdgeProps } from "@vue-flow/core";
import type { SequenceFlowEdgeData } from "./vue-flow-adapter";

const props = defineProps<EdgeProps<SequenceFlowEdgeData>>();

// 每条消息按顺序下沉到独立轨道，避免同一参与者之间的多条边完全重叠。
const laneY = computed(() => Math.max(props.sourceY, props.targetY) + 78 + props.data.order * 44);
const path = computed(() => {
  if (props.source === props.target) {
    return `M ${props.sourceX} ${props.sourceY} L ${props.sourceX + 76} ${laneY.value} L ${props.sourceX} ${laneY.value}`;
  }
  return `M ${props.sourceX} ${props.sourceY} L ${props.sourceX} ${laneY.value} L ${props.targetX} ${laneY.value} L ${props.targetX} ${props.targetY}`;
});
const labelX = computed(() => props.source === props.target ? props.sourceX + 38 : (props.sourceX + props.targetX) / 2);
</script>

<template>
  <BaseEdge :id="id" :path="path" :marker-end="markerEnd" :style="style" />
  <text class="ta-sequence-message-label" :x="labelX" :y="laneY - 7" text-anchor="middle">
    {{ data.order + 1 }}. {{ data.text }}
  </text>
</template>

<style scoped>
.ta-sequence-message-label { fill: var(--ta-text, #334155); font-family: inherit; font-size: 10px; pointer-events: none; }
</style>
