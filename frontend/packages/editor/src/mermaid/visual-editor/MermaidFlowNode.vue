<script setup lang="ts">
import { computed, type CSSProperties } from "vue";
import { Handle, Position } from "@vue-flow/core";
import { getMermaidNodePortId, getMermaidNodePortLayout } from "./node-ports";
import type { MermaidFlowNodeData } from "./vue-flow-adapter";

const props = defineProps<{
  id: string;
  data: MermaidFlowNodeData;
  selected?: boolean;
}>();

type FlowPort = {
  id: string;
  position: Position;
  style: CSSProperties;
};

const portLayout = computed(() => getMermaidNodePortLayout(props.data.direction));

function createPortStyle(position: Position, offset: number, index: number): CSSProperties {
  const axisStyle: CSSProperties =
    position === Position.Top || position === Position.Bottom
      ? { left: `${offset}%` }
      : { top: `${offset}%` };
  if (props.data.nodeType !== "diamond") return axisStyle;

  // 菱形没有平直边：中间端口位于尖端，两侧端口向内 25% 后落在对应斜边上。
  return {
    ...axisStyle,
    [position]: index === 1 ? "0%" : "25%"
  };
}

/** 三个端口沿入口边或出口边均匀分布，方向切换时只改变所在边和偏移轴。 */
function createPorts(type: "target" | "source"): FlowPort[] {
  const position = portLayout.value[type];
  return portLayout.value.offsets.map((offset, index) => ({
    id: getMermaidNodePortId(type, index),
    position,
    style: createPortStyle(position, offset, index)
  }));
}

const targetPorts = computed(() => createPorts("target"));
const sourcePorts = computed(() => createPorts("source"));
</script>

<template>
  <div :class="['ta-mermaid-flow-node', `is-${data.nodeType}`, { 'is-selected': selected }]">
    <Handle
      v-for="port in targetPorts"
      :id="port.id"
      :key="port.id"
      type="target"
      :position="port.position"
      :style="port.style"
    />
    <div class="ta-mermaid-flow-node__id">{{ id }}</div>
    <div class="ta-mermaid-flow-node__label">{{ data.text }}</div>
    <Handle
      v-for="port in sourcePorts"
      :id="port.id"
      :key="port.id"
      type="source"
      :position="port.position"
      :style="port.style"
    />
  </div>
</template>

<style scoped>
.ta-mermaid-flow-node {
  position: relative;
  box-sizing: border-box;
  min-width: 118px;
  max-width: 190px;
  padding: 10px 14px;
  border: 1px solid var(--ta-border-strong, #94a3b8);
  border-radius: 5px;
  background: var(--ta-surface, #fff);
  color: var(--ta-ink, #172033);
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
  text-align: center;
}

.ta-mermaid-flow-node.is-selected {
  border-color: var(--primary, #4f46e5);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--primary, #4f46e5) 20%, transparent);
}

.ta-mermaid-flow-node.is-rounded { border-radius: 999px; }
.ta-mermaid-flow-node.is-stadium { border-radius: 999px; padding-inline: 24px; }
.ta-mermaid-flow-node.is-circle { min-width: 92px; min-height: 92px; border-radius: 999px; display: grid; place-content: center; }
.ta-mermaid-flow-node.is-diamond {
  display: flex;
  width: 150px;
  height: 88px;
  min-width: 150px;
  min-height: 88px;
  padding: 20px 38px;
  flex-direction: column;
  justify-content: center;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  isolation: isolate;
}

/* 根元素保持水平，两层多边形分别承担边框和背景，避免文字与端口随菱形旋转。 */
.ta-mermaid-flow-node.is-diamond::before,
.ta-mermaid-flow-node.is-diamond::after {
  position: absolute;
  content: "";
  clip-path: polygon(50% 0, 100% 50%, 50% 100%, 0 50%);
  pointer-events: none;
}

.ta-mermaid-flow-node.is-diamond::before {
  z-index: -2;
  inset: 0;
  background: var(--ta-border-strong, #94a3b8);
  filter: drop-shadow(0 2px 4px rgba(15, 23, 42, 0.12));
}

.ta-mermaid-flow-node.is-diamond::after {
  z-index: -1;
  inset: 1.5px;
  background: var(--ta-surface, #fff);
}

.ta-mermaid-flow-node.is-diamond.is-selected {
  border-color: transparent;
  box-shadow: none;
}

.ta-mermaid-flow-node.is-diamond.is-selected::before {
  background: var(--primary, #4f46e5);
  filter: drop-shadow(0 0 4px color-mix(in srgb, var(--primary, #4f46e5) 30%, transparent));
}

.ta-mermaid-flow-node :deep(.vue-flow__handle) {
  z-index: 3;
  width: 8px;
  height: 8px;
  border: 1.5px solid var(--ta-surface, #fff);
  background: var(--ta-border-strong, #64748b);
}

.ta-mermaid-flow-node__id {
  margin-bottom: 3px;
  color: var(--ta-muted, #64748b);
  font-family: Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  font-size: 10px;
  letter-spacing: 0.04em;
}

.ta-mermaid-flow-node__label {
  overflow: hidden;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
