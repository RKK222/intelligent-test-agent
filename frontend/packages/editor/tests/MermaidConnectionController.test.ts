import { Position } from "@vue-flow/core";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { MermaidGraph } from "../src/mermaid/model";
import {
  MERMAID_PORT_SNAP_RADIUS,
  MERMAID_SOURCE_HIT_RADIUS,
  MERMAID_TARGET_ACTIVATION_MARGIN,
  findConnectionTargetNode,
  findNearestConnectionPort,
  isPointWithinPortRadius,
  type MermaidConnectionNodeGeometry,
  type MermaidConnectionPortGeometry
} from "../src/mermaid/visual-editor/mermaid-connection-geometry";
import { createMermaidConnectionDragController } from "../src/mermaid/visual-editor/use-mermaid-connection-drag";

const nodes: MermaidConnectionNodeGeometry[] = [
  { nodeId: "back", rect: { left: 20, top: 20, right: 120, bottom: 100 } },
  { nodeId: "front", rect: { left: 40, top: 30, right: 140, bottom: 110 } }
];

const ports: MermaidConnectionPortGeometry[] = [
  { nodeId: "front", handleId: "source-0", position: Position.Right, x: 100, y: 100 },
  { nodeId: "front", handleId: "target-0", position: Position.Left, x: 120, y: 100 }
];

describe("Mermaid 连线屏幕几何", () => {
  it("起线命中半径包含 18px 边界并拒绝边界外", () => {
    expect(MERMAID_SOURCE_HIT_RADIUS).toBe(18);
    expect(isPointWithinPortRadius({ x: 118, y: 100 }, ports[0]!, MERMAID_SOURCE_HIT_RADIUS)).toBe(true);
    expect(isPointWithinPortRadius({ x: 118.01, y: 100 }, ports[0]!, MERMAID_SOURCE_HIT_RADIUS)).toBe(false);
  });

  it("重叠节点优先选择指针正下方的最上层节点", () => {
    expect(findConnectionTargetNode({ x: 60, y: 60 }, nodes, "front")?.nodeId).toBe("front");
    expect(findConnectionTargetNode({ x: 60, y: 60 }, nodes, "back")?.nodeId).toBe("back");
  });

  it("没有直接命中时选择 24px 内最近外框并稳定处理平局", () => {
    expect(MERMAID_TARGET_ACTIVATION_MARGIN).toBe(24);
    expect(findConnectionTargetNode({ x: 164, y: 70 }, nodes)?.nodeId).toBe("front");
    expect(findConnectionTargetNode({ x: 164.01, y: 70 }, nodes)).toBeUndefined();

    const tied: MermaidConnectionNodeGeometry[] = [
      { nodeId: "first", rect: { left: 0, top: 0, right: 20, bottom: 20 } },
      { nodeId: "second", rect: { left: 40, top: 0, right: 60, bottom: 20 } }
    ];
    expect(findConnectionTargetNode({ x: 30, y: 10 }, tied)?.nodeId).toBe("first");
  });

  it("最近端口在 28px 边界内吸附并稳定处理平局", () => {
    expect(MERMAID_PORT_SNAP_RADIUS).toBe(28);
    expect(findNearestConnectionPort({ x: 128, y: 100 }, ports)?.handleId).toBe("target-0");
    expect(findNearestConnectionPort({ x: 100, y: 128 }, ports)?.handleId).toBe("source-0");
    expect(findNearestConnectionPort({ x: 100, y: 128.01 }, [ports[0]!])).toBeUndefined();

    const tied = [
      { ...ports[0]!, handleId: "source-1", x: 90 },
      { ...ports[0]!, handleId: "source-2", x: 110 }
    ];
    expect(findNearestConnectionPort({ x: 100, y: 100 }, tied)?.handleId).toBe("source-1");
  });
});

function setRect(element: Element, rect: { left: number; top: number; right: number; bottom: number }) {
  Object.defineProperty(element, "getBoundingClientRect", {
    configurable: true,
    value: () => ({
      ...rect,
      x: rect.left,
      y: rect.top,
      width: rect.right - rect.left,
      height: rect.bottom - rect.top,
      toJSON: () => ({})
    })
  });
}

function createControllerFixture(existingEdge = false) {
  const canvas = document.createElement("div");
  const sourceNode = document.createElement("div");
  const targetNode = document.createElement("div");
  const sourcePort = document.createElement("span");
  const targetPort = document.createElement("span");
  sourceNode.dataset.mermaidNodeId = "A";
  targetNode.dataset.mermaidNodeId = "B";
  sourcePort.dataset.mermaidHandle = "source-0";
  sourcePort.dataset.mermaidPosition = "right";
  targetPort.dataset.mermaidHandle = "target-1";
  targetPort.dataset.mermaidPosition = "left";
  sourceNode.append(sourcePort);
  targetNode.append(targetPort);
  canvas.append(sourceNode, targetNode);
  document.body.append(canvas);
  setRect(canvas, { left: 0, top: 0, right: 500, bottom: 300 });
  setRect(sourceNode, { left: 20, top: 30, right: 120, bottom: 90 });
  setRect(sourcePort, { left: 113, top: 53, right: 127, bottom: 67 });
  setRect(targetNode, { left: 250, top: 30, right: 350, bottom: 90 });
  setRect(targetPort, { left: 243, top: 53, right: 257, bottom: 67 });

  const graph: MermaidGraph = {
    kind: "flowchart",
    direction: "LR",
    nodes: [
      { id: "A", text: "A", type: "rectangle", position: { x: 0, y: 0 } },
      { id: "B", text: "B", type: "rectangle", position: { x: 0, y: 0 } }
    ],
    edges: existingEdge
      ? [{ id: "edge-1", source: "A", target: "B", label: "", relation: "arrow" }]
      : [],
    preservedLines: []
  };
  const onConnect = vi.fn();
  const frames: FrameRequestCallback[] = [];
  const cancelAnimationFrame = vi.fn();
  const controller = createMermaidConnectionDragController({
    getCanvasElement: () => canvas,
    getGraph: () => graph,
    onConnect,
    requestAnimationFrame: (callback) => {
      frames.push(callback);
      return frames.length;
    },
    cancelAnimationFrame
  });
  Object.defineProperty(document, "elementsFromPoint", {
    configurable: true,
    value: vi.fn(() => [targetPort, targetNode, canvas])
  });
  return { canvas, controller, frames, onConnect, cancelAnimationFrame };
}

function pointerEvent(type: string, x: number, y: number): Event {
  const event = new MouseEvent(type, { bubbles: true, clientX: x, clientY: y });
  Object.defineProperty(event, "pointerId", { value: 7 });
  return event;
}

afterEach(() => {
  document.body.innerHTML = "";
  vi.restoreAllMocks();
});

describe("Mermaid 拖线控制器", () => {
  it("合并移动帧并在有效端口松开时创建固定连接", () => {
    const { controller, frames, onConnect } = createControllerFixture();
    controller.startConnection({
      pointerId: 7,
      nodeId: "A",
      handleId: "source-0",
      position: Position.Right,
      point: { x: 120, y: 60 }
    });

    window.dispatchEvent(pointerEvent("pointermove", 240, 60));
    window.dispatchEvent(pointerEvent("pointermove", 250, 60));
    expect(frames).toHaveLength(1);
    frames[0]!(0);
    expect(controller.targetNodeId.value).toBe("B");
    expect(controller.targetHandleId.value).toBe("target-1");
    expect(controller.targetStatus.value).toBe("valid");
    expect(controller.dragPath.value).toContain("M");

    window.dispatchEvent(pointerEvent("pointerup", 250, 60));
    expect(onConnect).toHaveBeenCalledWith({
      source: "A",
      target: "B",
      sourceHandle: "source-0",
      targetHandle: "target-1"
    });
    expect(controller.isDragging.value).toBe(false);
  });

  it("重复边显示无效吸附且松开不写入", () => {
    const { controller, frames, onConnect } = createControllerFixture(true);
    controller.startConnection({
      pointerId: 7,
      nodeId: "A",
      handleId: "source-0",
      position: Position.Right,
      point: { x: 120, y: 60 }
    });

    window.dispatchEvent(pointerEvent("pointermove", 250, 60));
    frames[0]!(0);
    expect(controller.targetStatus.value).toBe("invalid");
    window.dispatchEvent(pointerEvent("pointerup", 250, 60));
    expect(onConnect).not.toHaveBeenCalled();
  });

  it.each(["pointercancel", "blur"])("%s 取消拖线并清理待执行动画帧", (eventName) => {
    const { controller, frames, onConnect, cancelAnimationFrame } = createControllerFixture();
    controller.startConnection({
      pointerId: 7,
      nodeId: "A",
      handleId: "source-0",
      position: Position.Right,
      point: { x: 120, y: 60 }
    });
    window.dispatchEvent(pointerEvent("pointermove", 250, 60));
    expect(frames).toHaveLength(1);

    window.dispatchEvent(new Event(eventName));
    expect(controller.isDragging.value).toBe(false);
    expect(cancelAnimationFrame).toHaveBeenCalledWith(1);
    window.dispatchEvent(pointerEvent("pointerup", 250, 60));
    expect(onConnect).not.toHaveBeenCalled();
  });

  it("Escape 和 dispose 均取消拖线且移除窗口监听", () => {
    const { controller, onConnect } = createControllerFixture();
    controller.startConnection({
      pointerId: 7,
      nodeId: "A",
      handleId: "source-0",
      position: Position.Right,
      point: { x: 120, y: 60 }
    });
    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
    expect(controller.isDragging.value).toBe(false);

    controller.startConnection({
      pointerId: 7,
      nodeId: "A",
      handleId: "source-0",
      position: Position.Right,
      point: { x: 120, y: 60 }
    });
    controller.dispose();
    window.dispatchEvent(pointerEvent("pointerup", 250, 60));
    expect(onConnect).not.toHaveBeenCalled();
  });
});
