import { Position } from "@vue-flow/core";
import { describe, expect, it } from "vitest";
import type { MermaidPosition } from "../src/mermaid/model";
import { autoLayoutMermaidGraph } from "../src/mermaid/layout";
import { parseMermaidFlowchart } from "../src/mermaid/parser";
import {
  reattachMermaidEdgeRoutePoints
} from "../src/mermaid/visual-editor/edge-path";
import { getMermaidNodePorts } from "../src/mermaid/visual-editor/node-port-layout";

function delta(from: MermaidPosition, to: MermaidPosition): MermaidPosition {
  return { x: to.x - from.x, y: to.y - from.y };
}

function expectOutwardSourceTangent(points: MermaidPosition[], position: Position): void {
  const tangent = delta(points[0]!, points[1]!);
  if (position === Position.Top || position === Position.Bottom) {
    expect(tangent.x).toBe(0);
    if (position === Position.Top) expect(tangent.y).toBeLessThan(0);
    else expect(tangent.y).toBeGreaterThan(0);
  } else {
    expect(tangent.y).toBe(0);
    if (position === Position.Left) expect(tangent.x).toBeLessThan(0);
    else expect(tangent.x).toBeGreaterThan(0);
  }
}

function expectInwardTargetTangent(points: MermaidPosition[], position: Position): void {
  const tangent = delta(points.at(-2)!, points.at(-1)!);
  if (position === Position.Top || position === Position.Bottom) {
    expect(tangent.x).toBe(0);
    if (position === Position.Top) expect(tangent.y).toBeGreaterThan(0);
    else expect(tangent.y).toBeLessThan(0);
  } else {
    expect(tangent.y).toBe(0);
    if (position === Position.Left) expect(tangent.x).toBeGreaterThan(0);
    else expect(tangent.x).toBeLessThan(0);
  }
}

describe("Mermaid edge route endpoint reattachment", () => {
  it.each([
    {
      name: "Bottom → Top",
      source: { x: 0, y: 0 },
      sourcePosition: Position.Bottom,
      target: { x: 40, y: 100 },
      targetPosition: Position.Top,
      stored: [{ x: 1, y: -8 }, { x: 1, y: 30 }, { x: 41, y: 30 }, { x: 41, y: 108 }]
    },
    {
      name: "Top → Bottom",
      source: { x: 0, y: 100 },
      sourcePosition: Position.Top,
      target: { x: 40, y: 0 },
      targetPosition: Position.Bottom,
      stored: [{ x: 1, y: 108 }, { x: 1, y: 70 }, { x: 41, y: 70 }, { x: 41, y: -8 }]
    },
    {
      name: "Right → Left",
      source: { x: 0, y: 0 },
      sourcePosition: Position.Right,
      target: { x: 100, y: 40 },
      targetPosition: Position.Left,
      stored: [{ x: -8, y: 1 }, { x: 30, y: 1 }, { x: 30, y: 41 }, { x: 108, y: 41 }]
    },
    {
      name: "Left → Right",
      source: { x: 100, y: 0 },
      sourcePosition: Position.Left,
      target: { x: 0, y: 40 },
      targetPosition: Position.Right,
      stored: [{ x: 108, y: 1 }, { x: 70, y: 1 }, { x: 70, y: 41 }, { x: -8, y: 41 }]
    }
  ])("$name 的首尾切线均朝正确方向", ({ source, sourcePosition, target, targetPosition, stored }) => {
    const attached = reattachMermaidEdgeRoutePoints(stored, {
      source,
      sourcePosition,
      target,
      targetPosition
    });

    expect(attached.length).toBeGreaterThanOrEqual(2);
    expect(attached.slice(1).every((point, index) => {
      const previous = attached[index]!;
      return point.x === previous.x || point.y === previous.y;
    })).toBe(true);
    expectOutwardSourceTangent(attached, sourcePosition);
    expectInwardTargetTangent(attached, targetPosition);
  });

  it.each([0.1, 0.5, 1, 8])("兼容实际 Handle 与 ELK 终点相差 %spx", (offset) => {
    const target = { x: 520 - offset, y: 414 - offset };
    const attached = reattachMermaidEdgeRoutePoints([
      { x: 507.6, y: 303.3 },
      { x: 507.6, y: 318 },
      { x: 520, y: 318 },
      { x: 520, y: 414 }
    ], {
      source: { x: 507.6, y: 303.3 },
      sourcePosition: Position.Bottom,
      target,
      targetPosition: Position.Top
    });

    expect(attached.at(-1)).toEqual(target);
    expectInwardTargetTangent(attached, Position.Top);
  });

  it("代表图 N4/N5/N6/N7 的箭头均从上方进入目标节点", async () => {
    const graph = parseMermaidFlowchart(`flowchart TD
N2(新节点) --> N3{新节点}
N2 --> N4(新节点)
N3 --> N5(新节点)
N3 --> N6(新节点)
N3 --> N7(新节点)`);
    const laidOut = await autoLayoutMermaidGraph(graph);

    for (const targetId of ["N4", "N5", "N6", "N7"]) {
      const edge = laidOut.edges.find((candidate) => candidate.target === targetId)!;
      const sourceNode = laidOut.nodes.find((node) => node.id === edge.source)!;
      const targetNode = laidOut.nodes.find((node) => node.id === edge.target)!;
      const sourcePort = getMermaidNodePorts(sourceNode.type)
        .find((port) => port.handleId === edge.sourceHandle)!;
      const targetPort = getMermaidNodePorts(targetNode.type)
        .find((port) => port.handleId === edge.targetHandle)!;
      const stored = edge.route!.points;
      // 普通节点 CSS 最小宽度为 118px，Top Handle 的实际连线点还会比节点边界外移 8px。
      const actualTarget = {
        x: targetNode.position.x + 118 * targetPort.x / 100,
        y: targetNode.position.y - 8
      };
      const attached = reattachMermaidEdgeRoutePoints(stored, {
        source: stored[0]!,
        sourcePosition: sourcePort.position,
        target: actualTarget,
        targetPosition: targetPort.position
      });

      expect(targetPort.position).toBe(Position.Top);
      expect(attached.at(-1)).toEqual(actualTarget);
      expectInwardTargetTangent(attached, Position.Top);
    }
  });

  it("用 0.5px 容差拒绝贴着端点的伪引导轨道", () => {
    const route = (guideY: number) => reattachMermaidEdgeRoutePoints([
      { x: 0, y: -8 },
      { x: 0, y: guideY },
      { x: 40, y: guideY },
      { x: 40, y: 108 }
    ], {
      source: { x: 0, y: 0 },
      sourcePosition: Position.Bottom,
      target: { x: 40, y: 100 },
      targetPosition: Position.Top
    });

    expect(route(0.5)).toEqual([]);
    expect(route(0.6).length).toBeGreaterThan(0);
  });

  it.each([
    {
      name: "只有两个端点",
      stored: [{ x: 0, y: 0 }, { x: 40, y: 100 }]
    },
    {
      name: "包含对角线",
      stored: [{ x: 0, y: 0 }, { x: 20, y: 20 }, { x: 40, y: 100 }]
    },
    {
      name: "没有源端外侧轨道",
      stored: [{ x: 0, y: 0 }, { x: 0, y: -10 }, { x: 40, y: -10 }, { x: 40, y: 100 }]
    }
  ])("$name 时返回空路径交给 SmoothStep", ({ stored }) => {
    expect(reattachMermaidEdgeRoutePoints(stored, {
      source: { x: 0, y: 0 },
      sourcePosition: Position.Bottom,
      target: { x: 40, y: 100 },
      targetPosition: Position.Top
    })).toEqual([]);
  });
});
