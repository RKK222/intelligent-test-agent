import { onBeforeUnmount, ref } from "vue";
import { cloneMermaidGraph, type MermaidGraph, type MermaidNode, type MermaidPosition } from "../model";
import { getMermaidNodeBaseSize, getMermaidNodeSize } from "../node-shapes";

export type MermaidResizeCorner = "nw" | "ne" | "sw" | "se";

export type MermaidNodeResizeStart = {
  nodeId: string;
  corner: MermaidResizeCorner;
  pointerId: number;
};

type ResizeControllerOptions = {
  getGraph: () => MermaidGraph;
  screenToFlowCoordinate: (point: MermaidPosition) => MermaidPosition;
  onPreview: (graph: MermaidGraph | undefined) => void;
  onCommit: (graph: MermaidGraph) => void;
  requestAnimationFrame?: (callback: FrameRequestCallback) => number;
  cancelAnimationFrame?: (handle: number) => void;
  windowTarget?: Window;
};

function roundTenth(value: number): number {
  return Math.round(value * 10) / 10;
}

/**
 * 根据当前节点对角点和指针位置求绝对 scale。拖动角的对角始终固定；宽高两个方向中
 * 变化更大的一个决定比例，随后统一夹在 50%–300%。
 */
export function calculateMermaidNodeResize(
  node: MermaidNode,
  corner: MermaidResizeCorner,
  pointer: MermaidPosition
): { scale: number; position: MermaidPosition } {
  const base = getMermaidNodeBaseSize(node);
  const current = getMermaidNodeSize(node);
  const fixed = {
    x: corner.endsWith("e") ? node.position.x : node.position.x + current.width,
    y: corner.startsWith("s") ? node.position.y : node.position.y + current.height
  };
  const rawScale = Math.max(
    Math.abs(pointer.x - fixed.x) / base.width,
    Math.abs(pointer.y - fixed.y) / base.height
  );
  const scale = Math.round(Math.min(3, Math.max(0.5, rawScale)) * 1000) / 1000;
  const width = base.width * scale;
  const height = base.height * scale;
  return {
    scale,
    position: {
      x: roundTenth(corner.endsWith("e") ? fixed.x : fixed.x - width),
      y: roundTenth(corner.startsWith("s") ? fixed.y : fixed.y - height)
    }
  };
}

/** 独立管理窗口级 pointer 监听和单帧预览，避免 Vue Flow 节点拖动与缩放互相抢事件。 */
export function createMermaidNodeResizeController(options: ResizeControllerOptions) {
  const windowTarget = options.windowTarget ?? window;
  const requestFrame = options.requestAnimationFrame ?? window.requestAnimationFrame.bind(window);
  const cancelFrame = options.cancelAnimationFrame ?? window.cancelAnimationFrame.bind(window);
  const isResizing = ref(false);
  let start: MermaidNodeResizeStart | undefined;
  let originalNode: MermaidNode | undefined;
  let frameId: number | undefined;
  let pendingPoint: MermaidPosition | undefined;

  function graphAt(point: MermaidPosition): MermaidGraph | undefined {
    if (!start || !originalNode) return undefined;
    const draft = cloneMermaidGraph(options.getGraph());
    const node = draft.nodes.find((item) => item.id === start!.nodeId);
    if (!node) return undefined;
    const resized = calculateMermaidNodeResize(originalNode, start.corner, point);
    node.position = resized.position;
    if (resized.scale === 1) delete node.scale;
    else node.scale = resized.scale;
    for (const edge of draft.edges) delete edge.route;
    return draft;
  }

  function preview(point: MermaidPosition): MermaidGraph | undefined {
    const graph = graphAt(point);
    if (graph) options.onPreview(graph);
    return graph;
  }

  function flushPendingPoint() {
    frameId = undefined;
    if (!pendingPoint) return;
    const point = pendingPoint;
    pendingPoint = undefined;
    preview(point);
  }

  function onPointerMove(event: PointerEvent) {
    if (!start || event.pointerId !== start.pointerId) return;
    pendingPoint = options.screenToFlowCoordinate({ x: event.clientX, y: event.clientY });
    if (frameId === undefined) frameId = requestFrame(flushPendingPoint);
  }

  function removeListeners() {
    windowTarget.removeEventListener("pointermove", onPointerMove);
    windowTarget.removeEventListener("pointerup", onPointerUp);
    windowTarget.removeEventListener("pointercancel", cancelResize);
    windowTarget.removeEventListener("keydown", onKeyDown, true);
    windowTarget.removeEventListener("blur", cancelResize);
  }

  function clearState() {
    if (frameId !== undefined) cancelFrame(frameId);
    frameId = undefined;
    pendingPoint = undefined;
    start = undefined;
    originalNode = undefined;
    isResizing.value = false;
    removeListeners();
  }

  function onPointerUp(event: PointerEvent) {
    if (!start || event.pointerId !== start.pointerId) return;
    if (frameId !== undefined) cancelFrame(frameId);
    frameId = undefined;
    pendingPoint = undefined;
    // pointerup 可能早于最后一帧，必须用最终屏幕坐标同步计算后再提交。
    const graph = graphAt(options.screenToFlowCoordinate({ x: event.clientX, y: event.clientY }));
    clearState();
    options.onPreview(undefined);
    if (graph) options.onCommit(graph);
  }

  function cancelResize() {
    if (!isResizing.value) return;
    clearState();
    options.onPreview(undefined);
  }

  function onKeyDown(event: KeyboardEvent) {
    if (event.key !== "Escape") return;
    // 捕获阶段先消费 Esc，避免外层 Mermaid 对话框把“取消缩放”误解为“关闭整个编辑器”。
    event.preventDefault();
    event.stopImmediatePropagation();
    cancelResize();
  }

  function startResize(next: MermaidNodeResizeStart) {
    cancelResize();
    const node = options.getGraph().nodes.find((item) => item.id === next.nodeId);
    if (!node) return;
    start = next;
    originalNode = { ...node, position: { ...node.position } };
    isResizing.value = true;
    windowTarget.addEventListener("pointermove", onPointerMove);
    windowTarget.addEventListener("pointerup", onPointerUp);
    windowTarget.addEventListener("pointercancel", cancelResize);
    windowTarget.addEventListener("keydown", onKeyDown, true);
    windowTarget.addEventListener("blur", cancelResize);
  }

  return { isResizing, startResize, cancelResize, dispose: cancelResize };
}

export function useMermaidNodeResize(options: ResizeControllerOptions) {
  const controller = createMermaidNodeResizeController(options);
  onBeforeUnmount(controller.dispose);
  return controller;
}
