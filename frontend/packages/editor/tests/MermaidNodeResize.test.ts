import { afterEach, describe, expect, it, vi } from "vitest";
import type { MermaidGraph } from "../src/mermaid/model";
import {
  calculateMermaidNodeResize,
  createMermaidNodeResizeController,
  type MermaidResizeCorner
} from "../src/mermaid/visual-editor/use-mermaid-node-resize";

const graph = (): MermaidGraph => ({
  kind: "flowchart",
  direction: "LR",
  nodes: [{ id: "A", text: "节点", type: "rectangle", position: { x: 100, y: 100 } }],
  edges: [{
    id: "edge-1",
    source: "A",
    target: "A",
    label: "",
    relation: "arrow",
    route: { points: [{ x: 100, y: 100 }, { x: 220, y: 100 }] }
  }],
  preservedLines: []
});

describe("Mermaid 节点等比缩放几何", () => {
  it.each([
    ["se", { x: 280, y: 178 }, { x: 100, y: 100 }],
    ["nw", { x: 40, y: 74 }, { x: 40, y: 74 }],
    ["ne", { x: 280, y: 74 }, { x: 100, y: 74 }],
    ["sw", { x: 40, y: 178 }, { x: 40, y: 100 }]
  ] as const)("%s 固定对角拖到 150%", (corner, pointer, position) => {
    expect(calculateMermaidNodeResize(graph().nodes[0]!, corner, pointer)).toEqual({
      scale: 1.5,
      position
    });
  });

  it("把比例限制在 50%–300% 并精确到千分位", () => {
    expect(calculateMermaidNodeResize(graph().nodes[0]!, "se", { x: 101, y: 101 }).scale).toBe(0.5);
    expect(calculateMermaidNodeResize(graph().nodes[0]!, "se", { x: 9999, y: 9999 }).scale).toBe(3);
    expect(calculateMermaidNodeResize(graph().nodes[0]!, "se", { x: 248.08, y: 164.168 }).scale).toBe(1.234);
  });
});

function pointerEvent(type: string, x: number, y: number): Event {
  const event = new MouseEvent(type, { bubbles: true, clientX: x, clientY: y });
  Object.defineProperty(event, "pointerId", { value: 7 });
  return event;
}

function fixture(corner: MermaidResizeCorner = "se") {
  const model = graph();
  const previews: Array<MermaidGraph | undefined> = [];
  const commits: MermaidGraph[] = [];
  const frames: FrameRequestCallback[] = [];
  const cancelAnimationFrame = vi.fn();
  const controller = createMermaidNodeResizeController({
    getGraph: () => model,
    screenToFlowCoordinate: (point) => ({ x: point.x / 2, y: point.y / 2 }),
    onPreview: (value) => previews.push(value),
    onCommit: (value) => commits.push(value),
    requestAnimationFrame: (callback) => {
      frames.push(callback);
      return frames.length;
    },
    cancelAnimationFrame
  });
  controller.startResize({ nodeId: "A", corner, pointerId: 7 });
  return { controller, previews, commits, frames, cancelAnimationFrame };
}

afterEach(() => vi.restoreAllMocks());

describe("Mermaid 节点缩放控制器", () => {
  it("按画布 2x 缩放换算指针，合并移动帧并只在松手提交一次", () => {
    const { controller, previews, commits, frames } = fixture();

    window.dispatchEvent(pointerEvent("pointermove", 520, 330));
    window.dispatchEvent(pointerEvent("pointermove", 560, 356));
    expect(frames).toHaveLength(1);
    frames[0]!(0);
    expect(previews.at(-1)?.nodes[0]).toMatchObject({ scale: 1.5, position: { x: 100, y: 100 } });
    expect(previews.at(-1)?.edges[0]?.route).toBeUndefined();
    expect(commits).toHaveLength(0);

    window.dispatchEvent(pointerEvent("pointerup", 560, 356));
    expect(commits).toHaveLength(1);
    expect(commits[0]?.nodes[0]).toMatchObject({ scale: 1.5, position: { x: 100, y: 100 } });
    expect(controller.isResizing.value).toBe(false);
  });

  it("最终动画帧未执行时仍按 pointerup 坐标提交", () => {
    const { commits, frames, cancelAnimationFrame } = fixture();
    window.dispatchEvent(pointerEvent("pointermove", 520, 330));
    expect(frames).toHaveLength(1);

    window.dispatchEvent(pointerEvent("pointerup", 560, 356));
    expect(cancelAnimationFrame).toHaveBeenCalledWith(1);
    expect(commits[0]?.nodes[0]?.scale).toBe(1.5);
  });

  it.each(["pointercancel", "blur"])("%s 丢弃草稿且不提交", (eventName) => {
    const { controller, previews, commits } = fixture();
    window.dispatchEvent(pointerEvent("pointermove", 560, 356));
    window.dispatchEvent(new Event(eventName));

    expect(controller.isResizing.value).toBe(false);
    expect(commits).toHaveLength(0);
    expect(previews.at(-1)).toBeUndefined();
  });

  it("Esc 优先取消缩放，不继续冒泡关闭外层编辑器", () => {
    const outerKeydown = vi.fn();
    window.addEventListener("keydown", outerKeydown);
    const { controller, commits, previews } = fixture();
    const event = new KeyboardEvent("keydown", { key: "Escape", bubbles: true, cancelable: true });

    window.dispatchEvent(event);

    expect(event.defaultPrevented).toBe(true);
    expect(outerKeydown).not.toHaveBeenCalled();
    expect(controller.isResizing.value).toBe(false);
    expect(commits).toHaveLength(0);
    expect(previews.at(-1)).toBeUndefined();
    window.removeEventListener("keydown", outerKeydown);
  });
});
