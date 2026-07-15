<script setup lang="ts">
import { computed, type CSSProperties } from "vue";
import { Handle, Position } from "@vue-flow/core";
import {
  MERMAID_SOURCE_HIT_RADIUS,
  findNearestConnectionPort,
  type MermaidConnectionPortGeometry
} from "./mermaid-connection-geometry";
import { findEdgePort, getMermaidNodePorts } from "./node-port-layout";
import type { MermaidConnectionStart } from "./use-mermaid-connection-drag";
import type { MermaidFlowNodeData } from "./vue-flow-adapter";

import type { MermaidNodeType } from "../model";

const props = defineProps<{
  id: string;
  data: MermaidFlowNodeData;
  selected?: boolean;
  connectionSourceHandleId?: string;
  isConnectionTarget?: boolean;
  snappedHandleId?: string;
  connectionStatus?: "valid" | "invalid";
}>();
const emit = defineEmits<{
  connectionStart: [start: MermaidConnectionStart];
  quickConnect: [payload: { portId: string; position: Position; shapeType: MermaidNodeType }];
}>();

const quickShapes: ReadonlyArray<{ type: MermaidNodeType; label: string }> = [
  { type: "rectangle", label: "矩形" },
  { type: "rounded", label: "圆角" },
  { type: "stadium", label: "胶囊" },
  { type: "diamond", label: "判断" },
  { type: "circle", label: "圆形" }
];

type FlowPort = {
  id: string;
  position: Position;
  style: CSSProperties;
};

const allPorts = computed<FlowPort[]>(() =>
  // 端口坐标与句柄 ID 统一由 node-port-layout 提供，保证序列化与可视化端口一致。
  getMermaidNodePorts(props.data.nodeType).map((port) => ({
    id: port.handleId,
    position: port.position,
    style: { left: `${port.x}%`, top: `${port.y}%` }
  }))
);

const quickArrowDirs = computed(() => {
  const nodeType = props.data.nodeType;
  // 引导大箭头固定落在边中点，连接起始点取该边上最接近中点的端口，
  // 使起始点始终落在箭头所在边上，而不是退化到某个角落端口。
  const fallback = allPorts.value[0]?.id ?? "";
  const portOnEdge = (edge: Position) => findEdgePort(nodeType, edge)?.handleId ?? fallback;
  return [
    { dir: Position.Top, portId: portOnEdge(Position.Top), style: { left: "50%", top: "0%" } },
    { dir: Position.Bottom, portId: portOnEdge(Position.Bottom), style: { left: "50%", top: "100%" } },
    { dir: Position.Left, portId: portOnEdge(Position.Left), style: { left: "0%", top: "50%" } },
    { dir: Position.Right, portId: portOnEdge(Position.Right), style: { left: "100%", top: "50%" } }
  ];
});

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
  if (props.selected) return;
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
  if (props.selected) return;
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
      v-for="port in allPorts"
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

    <!-- 快捷四向连接器，仅在选中状态下渲染 -->
    <template v-if="selected">
      <div
        v-for="arrow in quickArrowDirs"
        :key="'quick-' + arrow.dir"
        class="ta-mermaid-quick-connector-wrapper"
        :class="[`is-${arrow.dir}`]"
        :style="arrow.style"
      >
        <div class="ta-mermaid-quick-arrow" aria-label="快捷建连">
          <svg class="ta-quick-arrow-icon" viewBox="0 0 24 24" width="12" height="12">
            <path d="M5 12h14M12 5l7 7-7 7" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" fill="none" />
          </svg>
          <div class="ta-mermaid-quick-menu">
            <button
              v-for="shape in quickShapes"
              :key="shape.type"
              type="button"
              :title="`在此方向添加${shape.label}`"
              @click.stop="emit('quickConnect', { portId: arrow.portId, position: arrow.dir, shapeType: shape.type })"
            >
              <span :class="['ta-quick-menu-shape', `is-${shape.type}`]"></span>
            </button>
          </div>
        </div>
      </div>
    </template>
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
  cursor: move;
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
  position: absolute !important;
  z-index: 3;
  width: 16px;
  height: 16px;
  border: 0 !important;
  background: transparent !important;
  box-shadow: none !important;
  border-radius: 0 !important;
  opacity: 0;
  pointer-events: auto;
  cursor: default;
  transition: opacity 100ms ease;
  /* 覆盖 Vue Flow 的单轴 translateX/Y，确保 Handle 中心点精确落在 style 中指定的 left/top 坐标上 */
  transform: translate(-50%, -50%) !important;
}

/* 渲染紫色小 x */
.ta-mermaid-flow-node :deep(.vue-flow__handle::after) {
  content: "×";
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  color: #8b5cf6; /* 紫色 */
  font-size: 16px;
  font-weight: bold;
  line-height: 1;
}

.ta-mermaid-flow-node:hover :deep(.vue-flow__handle),
.ta-mermaid-flow-node.is-connection-target :deep(.vue-flow__handle),
.ta-mermaid-flow-node :deep(.vue-flow__handle.is-active-source) {
  opacity: 1;
}

.ta-mermaid-flow-node.is-connection-source:not(.is-connection-target) :deep(.vue-flow__handle:not(.is-active-source)) {
  opacity: 0;
}

.ta-mermaid-flow-node :deep(.vue-flow__handle.is-snapped-valid::after) {
  color: #7c3aed; /* 吸附有效时用更饱满的深紫色 */
  transform: translate(-50%, -50%) scale(1.3);
}

.ta-mermaid-flow-node :deep(.vue-flow__handle.is-snapped-invalid::after) {
  color: #d92d20; /* 无效自环时为红色小 x */
  transform: translate(-50%, -50%) scale(1.3);
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

.ta-mermaid-port-container :deep(.vue-flow__handle) {
  position: absolute !important;
  left: 50% !important;
  top: 50% !important;
  transform: translate(-50%, -50%) !important;
  margin: 0 !important;
}

.ta-mermaid-port-container {
  position: absolute;
  width: 24px;
  height: 24px;
  margin-left: -12px;
  margin-top: -12px;
  display: grid;
  place-items: center;
  z-index: 10;
  pointer-events: auto;
}

.ta-mermaid-quick-connector-wrapper {
  position: absolute;
  width: 24px;
  height: 24px;
  margin-left: -12px;
  margin-top: -12px;
  display: grid;
  place-items: center;
  z-index: 21;
  pointer-events: auto;
}

/* 引导大箭头默认半透明，悬停时变不透明并滑出面板 */
.ta-mermaid-quick-connector-wrapper .ta-mermaid-quick-arrow {
  opacity: 0.4;
  pointer-events: auto;
  transition: opacity 150ms ease, background-color 150ms ease;
}

/* 根据端口位置决定大箭头的偏移方向和定位 */
.ta-mermaid-quick-connector-wrapper.is-right .ta-mermaid-quick-arrow {
  position: absolute !important;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
}
.ta-mermaid-quick-connector-wrapper.is-left .ta-mermaid-quick-arrow {
  position: absolute !important;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
}
.ta-mermaid-quick-connector-wrapper.is-top .ta-mermaid-quick-arrow {
  position: absolute !important;
  bottom: 12px;
  left: 50%;
  transform: translateX(-50%);
}
.ta-mermaid-quick-connector-wrapper.is-bottom .ta-mermaid-quick-arrow {
  position: absolute !important;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
}

/* 大箭头按钮 */
.ta-mermaid-quick-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--primary, #4f46e5) 15%, #fff);
  color: var(--primary, #4f46e5);
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.16);
  cursor: pointer;
  z-index: 2;
}
.ta-mermaid-quick-arrow:hover {
  opacity: 1;
  background: var(--primary, #4f46e5);
  color: #fff;
}

/* 根据方向旋转 SVG 箭头图标 */
.ta-mermaid-quick-connector-wrapper.is-left .ta-quick-arrow-icon {
  transform: rotate(180deg);
}
.ta-mermaid-quick-connector-wrapper.is-top .ta-quick-arrow-icon {
  transform: rotate(-90deg);
}
.ta-mermaid-quick-connector-wrapper.is-bottom .ta-quick-arrow-icon {
  transform: rotate(90deg);
}

/* 快捷可用形状面板 */
.ta-mermaid-quick-menu {
  position: absolute;
  z-index: 30;
  display: flex;
  gap: 5px;
  padding: 5px;
  border: 1px solid var(--ta-border, #e2e8f0);
  border-radius: 6px;
  background: var(--ta-surface, #fff);
  box-shadow: 0 10px 20px -3px rgba(15, 23, 42, 0.12), 0 4px 6px -2px rgba(15, 23, 42, 0.08);
  opacity: 0;
  pointer-events: none;
  transition: opacity 150ms ease, transform 150ms ease;
}

/* 根据位置决定图形面板的横纵排列以及偏移方向 */
.ta-mermaid-quick-connector-wrapper.is-right .ta-mermaid-quick-menu {
  flex-direction: column;
  left: 100%;
  top: 50%;
  transform: translateY(-50%) scale(0.9);
  margin-left: 8px;
}
.ta-mermaid-quick-connector-wrapper.is-left .ta-mermaid-quick-menu {
  flex-direction: column;
  right: 100%;
  top: 50%;
  transform: translateY(-50%) scale(0.9);
  margin-right: 8px;
}
.ta-mermaid-quick-connector-wrapper.is-top .ta-mermaid-quick-menu {
  flex-direction: row;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%) scale(0.9);
  margin-bottom: 8px;
}
.ta-mermaid-quick-connector-wrapper.is-bottom .ta-mermaid-quick-menu {
  flex-direction: row;
  top: 100%;
  left: 50%;
  transform: translateX(-50%) scale(0.9);
  margin-top: 8px;
}

/* 悬停在大箭头上时展开图形面板 */
.ta-mermaid-quick-arrow:hover .ta-mermaid-quick-menu {
  opacity: 1;
  pointer-events: auto;
}
.ta-mermaid-quick-connector-wrapper.is-right .ta-mermaid-quick-arrow:hover .ta-mermaid-quick-menu,
.ta-mermaid-quick-connector-wrapper.is-left .ta-mermaid-quick-arrow:hover .ta-mermaid-quick-menu {
  transform: translateY(-50%) scale(1);
}
.ta-mermaid-quick-connector-wrapper.is-top .ta-mermaid-quick-arrow:hover .ta-mermaid-quick-menu,
.ta-mermaid-quick-connector-wrapper.is-bottom .ta-mermaid-quick-arrow:hover .ta-mermaid-quick-menu {
  transform: translateX(-50%) scale(1);
}

/* 图形选择按钮 */
.ta-mermaid-quick-menu button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: 1px solid var(--ta-border, #e2e8f0);
  border-radius: 4px;
  background: var(--ta-surface, #fff);
  cursor: pointer;
  transition: background 120ms ease, border-color 120ms ease;
  padding: 0;
}
.ta-mermaid-quick-menu button:hover {
  border-color: var(--primary, #4f46e5);
  background: color-mix(in srgb, var(--primary, #4f46e5) 8%, transparent);
}

/* 形状缩略预览图 */
.ta-quick-menu-shape {
  display: block;
  width: 14px;
  height: 8px;
  border: 1.5px solid currentColor;
  border-radius: 1px;
  color: var(--ta-muted, #94a3b8);
  box-sizing: border-box;
}
.ta-mermaid-quick-menu button:hover .ta-quick-menu-shape {
  color: var(--primary, #4f46e5);
}
.ta-quick-menu-shape.is-rounded {
  border-radius: 3px;
}
.ta-quick-menu-shape.is-stadium {
  border-radius: 999px;
}
.ta-quick-menu-shape.is-circle {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.ta-quick-menu-shape.is-diamond {
  width: 10px;
  height: 10px;
  border: 0;
  position: relative;
}
.ta-quick-menu-shape.is-diamond::before {
  position: absolute;
  content: "";
  left: 50%;
  top: 50%;
  width: 7px;
  height: 7px;
  border: 1.5px solid currentColor;
  transform: translate(-50%, -50%) rotate(0.125turn);
  box-sizing: border-box;
}

/* 选中节点时，四向容器激活桥接伪元素扩大 hover 作用域 */
.ta-mermaid-quick-connector-wrapper.is-right::after {
  content: "";
  position: absolute;
  left: 12px;
  width: 24px;
  height: 48px;
  top: -12px;
  background: transparent;
  pointer-events: auto;
}

.ta-mermaid-quick-connector-wrapper.is-left::after {
  content: "";
  position: absolute;
  right: 12px;
  width: 24px;
  height: 48px;
  top: -12px;
  background: transparent;
  pointer-events: auto;
}

.ta-mermaid-quick-connector-wrapper.is-top::after {
  content: "";
  position: absolute;
  bottom: 12px;
  width: 48px;
  height: 24px;
  left: -12px;
  background: transparent;
  pointer-events: auto;
}

.ta-mermaid-quick-connector-wrapper.is-bottom::after {
  content: "";
  position: absolute;
  top: 12px;
  width: 48px;
  height: 24px;
  left: -12px;
  background: transparent;
  pointer-events: auto;
}

/* 为快捷菜单面板提供与大箭头之间的透明连接桥，防鼠标滑过 8px 物理间隙时 hover 中断 */
.ta-mermaid-quick-menu::before {
  content: "";
  position: absolute;
  background: transparent;
}

.ta-mermaid-quick-connector-wrapper.is-right .ta-mermaid-quick-menu::before {
  left: -12px;
  top: 0;
  width: 12px;
  height: 100%;
}

.ta-mermaid-quick-connector-wrapper.is-left .ta-mermaid-quick-menu::before {
  right: -12px;
  top: 0;
  width: 12px;
  height: 100%;
}

.ta-mermaid-quick-connector-wrapper.is-top .ta-mermaid-quick-menu::before {
  bottom: -12px;
  left: 0;
  height: 12px;
  width: 100%;
}

.ta-mermaid-quick-connector-wrapper.is-bottom .ta-mermaid-quick-menu::before {
  top: -12px;
  left: 0;
  height: 12px;
  width: 100%;
}
</style>
