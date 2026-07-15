<script setup lang="ts">
import { computed, type CSSProperties } from "vue";
import { Handle, Position } from "@vue-flow/core";
import {
  MERMAID_SOURCE_HIT_RADIUS,
  findNearestConnectionPort,
  type MermaidConnectionPortGeometry
} from "./mermaid-connection-geometry";
import { getMermaidNodePortId, getMermaidNodePortLayout } from "./node-ports";
import type { MermaidConnectionStart } from "./use-mermaid-connection-drag";
import type { MermaidFlowNodeData } from "./vue-flow-adapter";

const props = defineProps<{
  id: string;
  data: MermaidFlowNodeData;
  selected?: boolean;
  connectionSourceHandleId?: string;
  isConnectionTarget?: boolean;
  snappedHandleId?: string;
  connectionStatus?: "valid" | "invalid";
}>();
const emit = defineEmits<{ connectionStart: [start: MermaidConnectionStart] }>();

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

function portClasses(portId: string) {
  return {
    "is-active-source": props.connectionSourceHandleId === portId,
    "is-snapped-valid": props.snappedHandleId === portId && props.connectionStatus === "valid",
    "is-snapped-invalid": props.snappedHandleId === portId && props.connectionStatus === "invalid"
  };
}

function findPortAtPoint(nodeElement: HTMLElement, point: { x: number; y: number }) {
  const ports: MermaidConnectionPortGeometry[] = Array.from(
    nodeElement.querySelectorAll<HTMLElement>("[data-mermaid-handle]"),
    (element) => {
      const rect = element.getBoundingClientRect();
      return {
        nodeId: props.id,
        handleId: element.dataset.mermaidHandle ?? "",
        position: (element.dataset.mermaidPosition ?? Position.Bottom) as Position,
        x: rect.left + rect.width / 2,
        y: rect.top + rect.height / 2
      };
    }
  );
  return findNearestConnectionPort(point, ports, MERMAID_SOURCE_HIT_RADIUS);
}

/** Handle 本身不接管事件；节点根元素使用更大的 18px 屏幕半径完成易选中的起线命中。 */
function onPointerDown(event: PointerEvent) {
  if (event.button !== 0) return;
  const nodeElement = event.currentTarget as HTMLElement;
  const port = findPortAtPoint(nodeElement, { x: event.clientX, y: event.clientY });
  if (!port) return;
  event.preventDefault();
  event.stopPropagation();
  emit("connectionStart", {
    pointerId: event.pointerId,
    nodeId: props.id,
    handleId: port.handleId,
    position: port.position,
    point: { x: port.x, y: port.y }
  });
}

/** Vue Flow 以 mousedown 启动节点拖拽，端口命中时需在根元素捕获阶段阻断该兼容鼠标事件。 */
function preventNodeDragFromPort(event: MouseEvent) {
  if (event.button !== 0) return;
  const nodeElement = event.currentTarget as HTMLElement;
  if (!findPortAtPoint(nodeElement, { x: event.clientX, y: event.clientY })) return;
  event.preventDefault();
  event.stopPropagation();
}
</script>

<template>
  <div
    :data-mermaid-node-id="id"
    :class="[
      'ta-mermaid-flow-node',
      `is-${data.nodeType}`,
      {
        'is-selected': selected,
        'is-connection-source': connectionSourceHandleId,
        'is-connection-target': isConnectionTarget
      }
    ]"
    @pointerdown="onPointerDown"
    @mousedown.capture="preventNodeDragFromPort"
  >
    <Handle
      v-for="port in targetPorts"
      :id="port.id"
      :key="port.id"
      type="source"
      :connectable="false"
      :position="port.position"
      :style="port.style"
      :class="portClasses(port.id)"
      :data-mermaid-handle="port.id"
      :data-mermaid-position="port.position"
    />
    <div class="ta-mermaid-flow-node__id">{{ id }}</div>
    <div class="ta-mermaid-flow-node__label">{{ data.text }}</div>
    <Handle
      v-for="port in sourcePorts"
      :id="port.id"
      :key="port.id"
      type="source"
      :connectable="false"
      :position="port.position"
      :style="port.style"
      :class="portClasses(port.id)"
      :data-mermaid-handle="port.id"
      :data-mermaid-position="port.position"
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
  width: 14px;
  height: 14px;
  border: 2px solid var(--ta-surface, #fff);
  background: var(--ta-border-strong, #64748b);
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.22);
  opacity: 0;
  pointer-events: none;
  transition: opacity 100ms ease, background-color 100ms ease, transform 100ms ease;
}

.ta-mermaid-flow-node:hover :deep(.vue-flow__handle),
.ta-mermaid-flow-node.is-connection-target :deep(.vue-flow__handle),
.ta-mermaid-flow-node :deep(.vue-flow__handle.is-active-source) {
  opacity: 1;
}

.ta-mermaid-flow-node.is-connection-source:not(.is-connection-target) :deep(.vue-flow__handle:not(.is-active-source)) {
  opacity: 0;
}

.ta-mermaid-flow-node :deep(.vue-flow__handle.is-snapped-valid) {
  background: var(--primary, #4f46e5);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--primary, #4f46e5) 24%, transparent);
  transform: scale(1.16);
}

.ta-mermaid-flow-node :deep(.vue-flow__handle.is-snapped-invalid) {
  background: #d92d20;
  box-shadow: 0 0 0 4px rgba(217, 45, 32, 0.2);
  transform: scale(1.16);
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
