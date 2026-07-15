import { onBeforeUnmount, ref } from "vue";
import { Position, getSmoothStepPath } from "@vue-flow/core";
import type { MermaidGraph } from "../model";
import {
  findConnectionTargetNode,
  findNearestConnectionPort,
  type MermaidConnectionNodeGeometry,
  type MermaidConnectionPortGeometry,
  type MermaidScreenPoint
} from "./mermaid-connection-geometry";
import { canAppendMermaidEdge, type MermaidPortConnection } from "./vue-flow-adapter";

export type MermaidConnectionStart = {
  pointerId: number;
  nodeId: string;
  handleId: string;
  position: Position;
  point: MermaidScreenPoint;
};

type ControllerOptions = {
  getCanvasElement: () => HTMLElement | undefined;
  getGraph: () => MermaidGraph;
  onConnect: (connection: MermaidPortConnection) => void;
  requestAnimationFrame?: (callback: FrameRequestCallback) => number;
  cancelAnimationFrame?: (handle: number) => void;
  windowTarget?: Window;
  documentTarget?: Document;
};

function oppositePosition(position: Position): Position {
  if (position === Position.Left) return Position.Right;
  if (position === Position.Right) return Position.Left;
  if (position === Position.Top) return Position.Bottom;
  return Position.Top;
}

function centerOf(element: Element): MermaidScreenPoint {
  const rect = element.getBoundingClientRect();
  return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 };
}

/**
 * 管理一次拖线的完整窗口生命周期。屏幕坐标测量让命中半径不随 Vue Flow 缩放变化，
 * DOM 仅在合并后的动画帧内读取，避免 pointermove 频繁触发布局计算。
 */
export function createMermaidConnectionDragController(options: ControllerOptions) {
  const windowTarget = options.windowTarget ?? window;
  const documentTarget = options.documentTarget ?? document;
  const requestFrame = options.requestAnimationFrame ?? windowTarget.requestAnimationFrame.bind(windowTarget);
  const cancelFrame = options.cancelAnimationFrame ?? windowTarget.cancelAnimationFrame.bind(windowTarget);
  const isDragging = ref(false);
  const sourceNodeId = ref<string>();
  const sourceHandleId = ref<string>();
  const targetNodeId = ref<string>();
  const targetHandleId = ref<string>();
  const targetStatus = ref<"valid" | "invalid">();
  const dragPath = ref("");
  let source: MermaidConnectionStart | undefined;
  let pendingPoint: MermaidScreenPoint | undefined;
  let frameId: number | undefined;
  let lastSnappedPort: { nodeId: string; handleId: string } | undefined;

  function measureNodes(canvas: HTMLElement): MermaidConnectionNodeGeometry[] {
    return Array.from(canvas.querySelectorAll<HTMLElement>("[data-mermaid-node-id]"), (element) => {
      const rect = element.getBoundingClientRect();
      return {
        nodeId: element.dataset.mermaidNodeId ?? "",
        rect: { left: rect.left, top: rect.top, right: rect.right, bottom: rect.bottom }
      };
    }).filter((node) => node.nodeId.length > 0);
  }

  function measurePorts(nodeElement: HTMLElement): MermaidConnectionPortGeometry[] {
    return Array.from(nodeElement.querySelectorAll<HTMLElement>("[data-mermaid-handle]"), (element) => ({
      nodeId: nodeElement.dataset.mermaidNodeId ?? "",
      handleId: element.dataset.mermaidHandle ?? "",
      position: (element.dataset.mermaidPosition ?? Position.Bottom) as Position,
      ...centerOf(element)
    })).filter((port) => port.handleId.length > 0);
  }

  function topmostNodeAt(point: MermaidScreenPoint): string | undefined {
    const elements = documentTarget.elementsFromPoint?.(point.x, point.y) ?? [];
    for (const element of elements) {
      const node = element.closest<HTMLElement>("[data-mermaid-node-id]");
      if (node?.dataset.mermaidNodeId) return node.dataset.mermaidNodeId;
    }
    return undefined;
  }

  function updatePath(point: MermaidScreenPoint, targetPort?: MermaidConnectionPortGeometry) {
    if (!source) return;
    const canvasRect = options.getCanvasElement()?.getBoundingClientRect();
    if (!canvasRect) return;
    const endpoint = targetPort ?? point;
    
    let targetPosition: Position;
    if (targetPort) {
      targetPosition = targetPort.position;
    } else {
      const dx = endpoint.x - source.point.x;
      const dy = endpoint.y - source.point.y;
      if (Math.abs(dx) > Math.abs(dy)) {
        targetPosition = dx > 0 ? Position.Left : Position.Right;
      } else {
        targetPosition = dy > 0 ? Position.Top : Position.Bottom;
      }
    }

    dragPath.value = getSmoothStepPath({
      sourceX: source.point.x - canvasRect.left,
      sourceY: source.point.y - canvasRect.top,
      sourcePosition: source.position,
      targetX: endpoint.x - canvasRect.left,
      targetY: endpoint.y - canvasRect.top,
      targetPosition
    })[0];
  }

  function updateFromPoint(point: MermaidScreenPoint) {
    if (!source) return;
    const canvas = options.getCanvasElement();
    if (!canvas) return;
    const nodes = measureNodes(canvas);
    const targetNode = findConnectionTargetNode(point, nodes, topmostNodeAt(point));
    targetNodeId.value = targetNode?.nodeId;
    targetHandleId.value = undefined;
    targetStatus.value = undefined;
    let targetPort: MermaidConnectionPortGeometry | undefined;
    if (targetNode) {
      const nodeElement = Array.from(canvas.querySelectorAll<HTMLElement>("[data-mermaid-node-id]"))
        .find((element) => element.dataset.mermaidNodeId === targetNode.nodeId);
      if (nodeElement) {
        const ports = measurePorts(nodeElement);
        if (lastSnappedPort && lastSnappedPort.nodeId === targetNode.nodeId) {
          const matchedPort = ports.find(p => p.handleId === lastSnappedPort?.handleId);
          if (matchedPort) {
            const dist = Math.hypot(point.x - matchedPort.x, point.y - matchedPort.y);
            if (dist <= 42) {
              targetPort = matchedPort;
            }
          }
        }
        if (!targetPort) {
          targetPort = findNearestConnectionPort(point, ports);
        }
      }
    }
    if (targetPort) {
      lastSnappedPort = { nodeId: targetPort.nodeId, handleId: targetPort.handleId };
      targetHandleId.value = targetPort.handleId;
      const connection: MermaidPortConnection = {
        source: source.nodeId,
        target: targetPort.nodeId,
        sourceHandle: source.handleId,
        targetHandle: targetPort.handleId
      };
      targetStatus.value = canAppendMermaidEdge(options.getGraph(), connection) ? "valid" : "invalid";
    } else {
      lastSnappedPort = undefined;
    }
    updatePath(point, targetPort);
  }

  function flushPendingPoint() {
    frameId = undefined;
    if (!pendingPoint) return;
    const point = pendingPoint;
    pendingPoint = undefined;
    updateFromPoint(point);
  }

  function onPointerMove(event: PointerEvent) {
    if (!source || event.pointerId !== source.pointerId) return;
    pendingPoint = { x: event.clientX, y: event.clientY };
    if (frameId === undefined) frameId = requestFrame(flushPendingPoint);
  }

  function removeListeners() {
    windowTarget.removeEventListener("pointermove", onPointerMove);
    windowTarget.removeEventListener("pointerup", onPointerUp);
    windowTarget.removeEventListener("pointercancel", cancelConnection);
    windowTarget.removeEventListener("keydown", onKeyDown);
    windowTarget.removeEventListener("blur", cancelConnection);
  }

  function clearState() {
    if (frameId !== undefined) cancelFrame(frameId);
    frameId = undefined;
    pendingPoint = undefined;
    source = undefined;
    isDragging.value = false;
    sourceNodeId.value = undefined;
    sourceHandleId.value = undefined;
    targetNodeId.value = undefined;
    targetHandleId.value = undefined;
    targetStatus.value = undefined;
    dragPath.value = "";
    lastSnappedPort = undefined;
    removeListeners();
  }

  function onPointerUp(event: PointerEvent) {
    if (!source || event.pointerId !== source.pointerId) return;
    if (frameId !== undefined) cancelFrame(frameId);
    frameId = undefined;
    pendingPoint = undefined;
    const connection: MermaidPortConnection = {
      source: source.nodeId,
      target: targetNodeId.value ?? null,
      sourceHandle: source.handleId,
      targetHandle: targetHandleId.value ?? null
    };
    const shouldConnect = targetStatus.value === "valid";
    clearState();
    if (shouldConnect) options.onConnect(connection);
  }

  function cancelConnection() {
    if (isDragging.value) clearState();
  }

  function onKeyDown(event: KeyboardEvent) {
    if (event.key === "Escape") cancelConnection();
  }

  function startConnection(start: MermaidConnectionStart) {
    clearState();
    source = start;
    isDragging.value = true;
    sourceNodeId.value = start.nodeId;
    sourceHandleId.value = start.handleId;
    updatePath(start.point);
    windowTarget.addEventListener("pointermove", onPointerMove);
    windowTarget.addEventListener("pointerup", onPointerUp);
    windowTarget.addEventListener("pointercancel", cancelConnection);
    windowTarget.addEventListener("keydown", onKeyDown);
    windowTarget.addEventListener("blur", cancelConnection);
  }

  return {
    isDragging,
    sourceNodeId,
    sourceHandleId,
    targetNodeId,
    targetHandleId,
    targetStatus,
    dragPath,
    startConnection,
    cancelConnection,
    dispose: clearState
  };
}

export function useMermaidConnectionDrag(options: ControllerOptions) {
  const controller = createMermaidConnectionDragController(options);
  onBeforeUnmount(controller.dispose);
  return controller;
}
