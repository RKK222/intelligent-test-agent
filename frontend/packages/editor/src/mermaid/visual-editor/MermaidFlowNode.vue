<script setup lang="ts">
import { computed, onBeforeUnmount, ref, type CSSProperties } from "vue";
import { Handle, Position } from "@vue-flow/core";
import {
  MERMAID_SOURCE_HIT_RADIUS,
  findNearestConnectionPort,
  type MermaidConnectionPortGeometry
} from "./mermaid-connection-geometry";
import { findEdgePort, getMermaidNodePorts } from "./node-port-layout";
import MermaidNodeShape from "./MermaidNodeShape.vue";
import type { MermaidConnectionStart } from "./use-mermaid-connection-drag";
import type { MermaidFlowNodeData } from "./vue-flow-adapter";

import type { MermaidNodeType } from "../model";
import { getMermaidNodeSize, MERMAID_NODE_SHAPES } from "../node-shapes";

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
  quickConnect: [payload: { nodeId: string; portId: string; position: Position; shapeType: MermaidNodeType }];
}>();

const quickShapes = MERMAID_NODE_SHAPES;

/** 节点真实尺寸直接使用领域目录，与 ELK 的包围盒计算保持一致。 */
const nodeStyle = computed<CSSProperties>(() => {
  const size = getMermaidNodeSize({ type: props.data.nodeType, text: props.data.text });
  return { width: `${size.width}px`, height: `${size.height}px` };
});

/** 鼠标悬浮在节点上时才显示四向快捷箭头，与是否选中无关；离开后隐藏。 */
const hovered = ref(false);
const nodeHovered = ref(false);
const nodeFocused = ref(false);
const activeQuickArrow = ref<{ dir: Position; portId: string }>();
const quickMenuPlacement = ref<Position>(Position.Bottom);
const quickMenuStyle = ref<CSSProperties>({ position: "fixed" });
let quickMenuCloseTimer: ReturnType<typeof setTimeout> | undefined;
// 悬浮延时定时器，用于防误触
let quickMenuOpenTimer: ReturnType<typeof setTimeout> | undefined;

const QUICK_MENU_WIDTH = 248;
const QUICK_MENU_HEIGHT = 276;
const QUICK_MENU_GAP = 8;
const QUICK_MENU_VIEWPORT_MARGIN = 8;

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
    { dir: Position.Top, portId: portOnEdge(Position.Top), ariaLabel: "上方快捷建连", style: { left: "50%", top: "0%" } },
    { dir: Position.Bottom, portId: portOnEdge(Position.Bottom), ariaLabel: "下方快捷建连", style: { left: "50%", top: "100%" } },
    { dir: Position.Left, portId: portOnEdge(Position.Left), ariaLabel: "左侧快捷建连", style: { left: "0%", top: "50%" } },
    { dir: Position.Right, portId: portOnEdge(Position.Right), ariaLabel: "右侧快捷建连", style: { left: "100%", top: "50%" } }
  ];
});

function clearQuickMenuCloseTimer() {
  if (quickMenuCloseTimer !== undefined) {
    clearTimeout(quickMenuCloseTimer);
    quickMenuCloseTimer = undefined;
  }
}

// 清理延时打开的定时器
function clearQuickMenuOpenTimer() {
  if (quickMenuOpenTimer !== undefined) {
    clearTimeout(quickMenuOpenTimer);
    quickMenuOpenTimer = undefined;
  }
}

function keepQuickConnectorsOpen() {
  clearQuickMenuCloseTimer();
  clearQuickMenuOpenTimer();
  hovered.value = true;
}

function onNodeMouseEnter() {
  nodeHovered.value = true;
  clearQuickMenuCloseTimer();
  // 如果当前已经显示（比如聚焦状态下），不需要重复计时
  if (hovered.value) {
    return;
  }
  clearQuickMenuOpenTimer();
  // 鼠标移入时，延迟 0.3 秒再显示四向快捷箭头，防止误触
  quickMenuOpenTimer = setTimeout(() => {
    if (nodeHovered.value || nodeFocused.value) {
      hovered.value = true;
    }
    quickMenuOpenTimer = undefined;
  }, 300);
}

function onNodeMouseLeave() {
  nodeHovered.value = false;
  // 鼠标离开时立即清理打开定时器，并触发关闭逻辑
  clearQuickMenuOpenTimer();
  scheduleQuickConnectorsClose();
}

function onNodeFocusIn() {
  nodeFocused.value = true;
  keepQuickConnectorsOpen();
}

function onNodeFocusOut(event: FocusEvent) {
  const root = event.currentTarget as HTMLElement;
  if (event.relatedTarget instanceof Node && root.contains(event.relatedTarget)) return;
  nodeFocused.value = false;
  scheduleQuickConnectorsClose();
}

/** 延迟关闭为 Teleport 浮层与箭头之间预留鼠标跨越间隙。 */
function scheduleQuickConnectorsClose() {
  clearQuickMenuCloseTimer();
  if (!activeQuickArrow.value) {
    if (!nodeHovered.value && !nodeFocused.value) hovered.value = false;
    return;
  }
  quickMenuCloseTimer = setTimeout(() => {
    activeQuickArrow.value = undefined;
    if (!nodeHovered.value && !nodeFocused.value) hovered.value = false;
    quickMenuCloseTimer = undefined;
  }, 160);
}

function oppositeQuickMenuPlacement(position: Position): Position {
  if (position === Position.Top) return Position.Bottom;
  if (position === Position.Bottom) return Position.Top;
  if (position === Position.Left) return Position.Right;
  return Position.Left;
}

/**
 * 菜单挂到 body 并使用屏幕坐标定位，使其不受 Vue Flow 缩放影响；首选箭头方向，
 * 空间不足时翻到对侧，最后夹在视口安全边距内，避免四周节点的选项被裁剪。
 */
function positionQuickMenu(anchor: DOMRect, preferred: Position) {
  const viewportWidth = Math.max(window.innerWidth, QUICK_MENU_VIEWPORT_MARGIN * 2 + 1);
  const viewportHeight = Math.max(window.innerHeight, QUICK_MENU_VIEWPORT_MARGIN * 2 + 1);
  const menuWidth = Math.min(QUICK_MENU_WIDTH, viewportWidth - QUICK_MENU_VIEWPORT_MARGIN * 2);
  const menuHeight = Math.min(QUICK_MENU_HEIGHT, viewportHeight - QUICK_MENU_VIEWPORT_MARGIN * 2);
  const fits = (placement: Position) => {
    if (placement === Position.Top) {
      return anchor.top - QUICK_MENU_GAP - menuHeight >= QUICK_MENU_VIEWPORT_MARGIN;
    }
    if (placement === Position.Bottom) {
      return anchor.bottom + QUICK_MENU_GAP + menuHeight <= viewportHeight - QUICK_MENU_VIEWPORT_MARGIN;
    }
    if (placement === Position.Left) {
      return anchor.left - QUICK_MENU_GAP - menuWidth >= QUICK_MENU_VIEWPORT_MARGIN;
    }
    return anchor.right + QUICK_MENU_GAP + menuWidth <= viewportWidth - QUICK_MENU_VIEWPORT_MARGIN;
  };

  const opposite = oppositeQuickMenuPlacement(preferred);
  const placement = fits(preferred) || !fits(opposite) ? preferred : opposite;
  let left = anchor.left + anchor.width / 2 - menuWidth / 2;
  let top = anchor.top + anchor.height / 2 - menuHeight / 2;
  if (placement === Position.Top) top = anchor.top - QUICK_MENU_GAP - menuHeight;
  if (placement === Position.Bottom) top = anchor.bottom + QUICK_MENU_GAP;
  if (placement === Position.Left) left = anchor.left - QUICK_MENU_GAP - menuWidth;
  if (placement === Position.Right) left = anchor.right + QUICK_MENU_GAP;
  left = Math.min(Math.max(left, QUICK_MENU_VIEWPORT_MARGIN), viewportWidth - menuWidth - QUICK_MENU_VIEWPORT_MARGIN);
  top = Math.min(Math.max(top, QUICK_MENU_VIEWPORT_MARGIN), viewportHeight - menuHeight - QUICK_MENU_VIEWPORT_MARGIN);

  quickMenuPlacement.value = placement;
  quickMenuStyle.value = {
    position: "fixed",
    left: `${Math.round(left)}px`,
    top: `${Math.round(top)}px`
  };
}

function openQuickMenu(event: MouseEvent | FocusEvent, arrow: { dir: Position; portId: string }) {
  keepQuickConnectorsOpen();
  activeQuickArrow.value = arrow;
  positionQuickMenu((event.currentTarget as HTMLElement).getBoundingClientRect(), arrow.dir);
}

function emitQuickConnect(shapeType: MermaidNodeType) {
  const arrow = activeQuickArrow.value;
  if (!arrow) return;
  emit("quickConnect", { nodeId: props.id, portId: arrow.portId, position: arrow.dir, shapeType });
}

onBeforeUnmount(() => {
  clearQuickMenuCloseTimer();
  clearQuickMenuOpenTimer();
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
    :aria-label="`${id}：${data.text}`"
    tabindex="0"
    :class="[
      'ta-mermaid-flow-node',
      `is-${data.nodeType}`,
      {
        'is-selected': selected,
        'is-connection-source': connectionSourceHandleId,
        'is-connection-target': isConnectionTarget
      }
    ]"
    :style="nodeStyle"
    @pointerdown="onPointerDown"
    @mousedown.capture="preventNodeDragFromPort"
    @mouseenter="onNodeMouseEnter"
    @mouseleave="onNodeMouseLeave"
    @focus="onNodeFocusIn"
    @blur="onNodeFocusOut"
    @focusin="onNodeFocusIn"
    @focusout="onNodeFocusOut"
  >
    <MermaidNodeShape
      class="ta-mermaid-flow-node__shape"
      :type="data.nodeType"
      :selected="selected"
    />
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

    <!-- 快捷四向连接器：鼠标悬浮节点时显示（半透明），离开后隐藏，与是否选中无关 -->
    <template v-if="hovered">
      <div
        v-for="arrow in quickArrowDirs"
        :key="'quick-' + arrow.dir"
        class="ta-mermaid-quick-connector-wrapper"
        :class="[`is-${arrow.dir}`]"
        :style="arrow.style"
      >
        <!-- 阻止 pointerdown 冒泡到根元素，避免点击箭头/菜单时误触发端口连线拖拽（其 preventDefault 会吞掉 click） -->
        <button
          type="button"
          class="ta-mermaid-quick-arrow"
          :aria-label="arrow.ariaLabel"
          @mouseenter="openQuickMenu($event, arrow)"
          @mouseleave="scheduleQuickConnectorsClose"
          @focus="openQuickMenu($event, arrow)"
          @blur="scheduleQuickConnectorsClose"
          @pointerdown.stop
        >
          <svg class="ta-quick-arrow-icon" viewBox="0 0 24 24" width="12" height="12">
            <path d="M5 12h14M12 5l7 7-7 7" stroke="currentColor" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" fill="none" />
          </svg>
        </button>
      </div>
    </template>

    <Teleport to="body">
      <div
        v-if="activeQuickArrow"
        class="ta-mermaid-quick-menu is-two-column is-screen-overlay"
        :class="`is-placement-${quickMenuPlacement}`"
        :style="quickMenuStyle"
        @mouseenter="keepQuickConnectorsOpen"
        @mouseleave="scheduleQuickConnectorsClose"
        @focusin="keepQuickConnectorsOpen"
        @focusout="scheduleQuickConnectorsClose"
        @pointerdown.stop
      >
        <button
          v-for="shape in quickShapes"
          :key="shape.type"
          type="button"
          :title="`在此方向添加${shape.label}`"
          @click.stop="emitQuickConnect(shape.type)"
        >
          <MermaidNodeShape class="ta-quick-menu-shape" :type="shape.type" thumbnail />
          <span class="ta-quick-menu-label">{{ shape.label }}</span>
        </button>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.ta-mermaid-flow-node {
  position: relative;
  box-sizing: border-box;
  display: flex;
  min-width: 0;
  padding: 9px 14px;
  flex-direction: column;
  align-items: stretch;
  justify-content: center;
  border: 0;
  background: transparent;
  color: var(--ta-ink, #172033);
  text-align: center;
  cursor: move;
}

.ta-mermaid-flow-node__shape {
  position: absolute;
  z-index: 0;
  inset: 0;
  width: 100%;
  height: 100%;
}

.ta-mermaid-flow-node.is-stadium,
.ta-mermaid-flow-node.is-subroutine,
.ta-mermaid-flow-node.is-database,
.ta-mermaid-flow-node.is-doc,
.ta-mermaid-flow-node.is-docs { padding-inline: 24px; }
.ta-mermaid-flow-node.is-diamond,
.ta-mermaid-flow-node.is-hexagon,
.ta-mermaid-flow-node.is-parallelogram,
.ta-mermaid-flow-node.is-trapezoid { padding-inline: 36px; }
.ta-mermaid-flow-node.is-circle,
.ta-mermaid-flow-node.is-double-circle { padding-inline: 15px; }

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
  position: relative;
  z-index: 1;
  margin-bottom: 3px;
  color: var(--ta-muted, #64748b);
  font-family: Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  font-size: 10px;
  letter-spacing: 0.04em;
}

.ta-mermaid-flow-node__label {
  position: relative;
  z-index: 1;
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

/* 快捷建连大箭头按钮（改为粗的蓝色箭头） */
.ta-mermaid-quick-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: color-mix(in srgb, #2563eb 15%, #fff);
  color: #2563eb;
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.16);
  cursor: pointer;
  z-index: 2;
}
.ta-mermaid-quick-arrow:hover {
  opacity: 1;
  background: #2563eb;
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
  z-index: 2000;
  box-sizing: border-box;
  display: grid;
  width: min(248px, calc(100vw - 16px));
  max-height: calc(100vh - 16px);
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px;
  padding: 6px;
  border: 1px solid var(--ta-border, #e2e8f0);
  border-radius: 6px;
  background: var(--ta-surface, #fff);
  box-shadow: 0 10px 20px -3px rgba(15, 23, 42, 0.12), 0 4px 6px -2px rgba(15, 23, 42, 0.08);
  overflow-y: auto;
  pointer-events: auto;
}

/* 图形选择按钮 */
.ta-mermaid-quick-menu button {
  display: flex;
  align-items: center;
  min-width: 0;
  height: 34px;
  gap: 7px;
  justify-content: flex-start;
  border: 1px solid var(--ta-border, #e2e8f0);
  border-radius: 4px;
  background: var(--ta-surface, #fff);
  cursor: pointer;
  transition: background 120ms ease, border-color 120ms ease;
  padding: 4px 7px;
  color: var(--ta-ink, #172033);
  font-size: 10px;
  line-height: 1.2;
  white-space: nowrap;
}
.ta-mermaid-quick-menu button:hover {
  border-color: var(--primary, #4f46e5);
  background: color-mix(in srgb, var(--primary, #4f46e5) 8%, transparent);
}

.ta-quick-menu-shape {
  width: 28px;
  height: 18px;
  flex: 0 0 28px;
  color: var(--ta-muted, #94a3b8);
}
.ta-quick-menu-shape[data-mermaid-shape="circle"],
.ta-quick-menu-shape[data-mermaid-shape="double-circle"] {
  width: 18px;
  height: 18px;
  margin-inline: 5px;
  flex-basis: 18px;
}
.ta-mermaid-quick-menu button:hover .ta-quick-menu-shape {
  color: var(--primary, #4f46e5);
}

.ta-quick-menu-label {
  overflow: hidden;
  text-overflow: ellipsis;
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

</style>
