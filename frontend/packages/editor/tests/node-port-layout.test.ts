/// <reference types="vite/client" />
import { Position } from "@vue-flow/core";
import { describe, expect, it } from "vitest";
import type { MermaidGraph, MermaidNodeType } from "../src/mermaid/model";
import {
  findEdgePort,
  getMermaidNodePorts,
  oppositePosition,
  remapMermaidNodeEdgePorts
} from "../src/mermaid/visual-editor/node-port-layout";

describe("Mermaid 节点端口布局", () => {
  it("各类节点渲染固定数量与 ID 的端口", () => {
    const rectangle = getMermaidNodePorts("rectangle");
    expect(rectangle).toHaveLength(12);
    expect(rectangle.filter((p) => p.handleId.startsWith("target-")).map((p) => p.handleId)).toEqual([
      "target-0", "target-1", "target-2", "target-3", "target-4", "target-5"
    ]);
    expect(rectangle.filter((p) => p.handleId.startsWith("source-")).map((p) => p.handleId)).toEqual([
      "source-0", "source-1", "source-2", "source-3", "source-4", "source-5"
    ]);

    expect(getMermaidNodePorts("diamond")).toHaveLength(12);
    expect(getMermaidNodePorts("circle")).toHaveLength(8);
    expect(getMermaidNodePorts("rounded")).toHaveLength(8);
    expect(getMermaidNodePorts("stadium")).toHaveLength(8);
    expect(getMermaidNodePorts("subroutine")).toHaveLength(12);
    expect(getMermaidNodePorts("database")).toHaveLength(8);
    expect(getMermaidNodePorts("hexagon")).toHaveLength(12);
    expect(getMermaidNodePorts("parallelogram")).toHaveLength(12);
    expect(getMermaidNodePorts("trapezoid")).toHaveLength(12);
    expect(getMermaidNodePorts("double-circle")).toHaveLength(8);
    expect(getMermaidNodePorts("text")).toHaveLength(8);
    expect(getMermaidNodePorts("doc")).toHaveLength(8);
    expect(getMermaidNodePorts("docs")).toHaveLength(8);
    // 圆角与胶囊共用同一套端口坐标
    expect(getMermaidNodePorts("stadium").map((p) => `${p.x},${p.y}`)).toEqual(
      getMermaidNodePorts("rounded").map((p) => `${p.x},${p.y}`)
    );
  });

  it("十四种节点都提供上下左右快捷建连端口", () => {
    const types: MermaidNodeType[] = [
      "stadium", "rectangle", "rounded", "subroutine", "database", "circle", "diamond",
      "hexagon", "parallelogram", "trapezoid", "double-circle", "text", "doc", "docs"
    ];

    for (const type of types) {
      for (const position of [Position.Top, Position.Right, Position.Bottom, Position.Left]) {
        expect(findEdgePort(type, position), `${type} 缺少 ${position} 端口`).toMatchObject({ position });
      }
    }
  });

  it("文档与多文档底部端口贴合正面页面的波浪轮廓", () => {
    for (const type of ["doc", "docs"] as const) {
      const bottomPorts = getMermaidNodePorts(type).filter((port) => port.position === Position.Bottom);
      expect(bottomPorts.map(({ x, y }) => ({ x, y }))).toEqual([
        { x: 85, y: 77 },
        { x: 50, y: 100 },
        { x: 15, y: 77 }
      ]);
    }
  });

  it("切换形状时按相对位置迁移端口并保持自环端口不同", () => {
    const graph: MermaidGraph = {
      kind: "flowchart",
      direction: "LR",
      nodes: [
        { id: "A", text: "判断", type: "diamond", position: { x: 0, y: 0 } },
        { id: "B", text: "结果", type: "rectangle", position: { x: 200, y: 0 } }
      ],
      edges: [
        {
          id: "edge-1",
          source: "A",
          target: "B",
          sourceHandle: "source-0",
          targetHandle: "target-0",
          label: "",
          relation: "arrow"
        },
        {
          id: "edge-2",
          source: "A",
          target: "A",
          sourceHandle: "target-0",
          targetHandle: "source-2",
          label: "",
          relation: "arrow"
        }
      ],
      preservedLines: []
    };

    remapMermaidNodeEdgePorts(graph, "A", "diamond", "database");

    expect(graph.edges[0]!.sourceHandle).toBe("target-1");
    expect(graph.edges[0]!.targetHandle).toBe("target-0");
    expect(graph.edges[1]!.sourceHandle).not.toBe(graph.edges[1]!.targetHandle);
    const databaseHandles = new Set(getMermaidNodePorts("database").map((port) => port.handleId));
    expect(databaseHandles.has(graph.edges[1]!.sourceHandle!)).toBe(true);
    expect(databaseHandles.has(graph.edges[1]!.targetHandle!)).toBe(true);
  });

  it("换形时在整个新轮廓选择欧氏距离最近的端口", () => {
    const graph: MermaidGraph = {
      kind: "flowchart",
      direction: "LR",
      nodes: [
        { id: "A", text: "判断", type: "diamond", position: { x: 0, y: 0 } },
        { id: "B", text: "结果", type: "rectangle", position: { x: 200, y: 0 } }
      ],
      edges: [{
        id: "edge-1",
        source: "A",
        target: "B",
        // diamond target-2 位于左上斜边 (16.7, 33.3)。
        sourceHandle: "target-2",
        targetHandle: "target-0",
        label: "",
        relation: "arrow"
      }],
      preservedLines: []
    };

    remapMermaidNodeEdgePorts(graph, "A", "diamond", "database");

    // database source-3 位于左侧 (0, 25)，比顶部 (50, 0) 更接近旧位置。
    expect(graph.edges[0]?.sourceHandle).toBe("source-3");
  });

  it("oppositePosition 返回对边方向", () => {
    expect(oppositePosition(Position.Top)).toBe(Position.Bottom);
    expect(oppositePosition(Position.Bottom)).toBe(Position.Top);
    expect(oppositePosition(Position.Left)).toBe(Position.Right);
    expect(oppositePosition(Position.Right)).toBe(Position.Left);
  });

  it.each([
    ["rectangle", Position.Top, "target-2"],
    ["rectangle", Position.Bottom, "target-3"],
    ["rectangle", Position.Left, "target-4"],
    ["rectangle", Position.Right, "target-5"],
    ["diamond", Position.Top, "target-0"],
    ["diamond", Position.Right, "source-0"],
    ["diamond", Position.Bottom, "target-1"],
    ["diamond", Position.Left, "source-1"],
    ["circle", Position.Top, "target-3"],
    ["circle", Position.Bottom, "target-1"],
    ["circle", Position.Left, "target-2"],
    ["circle", Position.Right, "target-0"],
    ["rounded", Position.Top, "source-1"],
    ["rounded", Position.Bottom, "target-3"],
    ["rounded", Position.Left, "target-0"],
    ["rounded", Position.Right, "source-0"]
  ] as const)("findEdgePort(%s, %s) 选中该边最接近中点的端口 %s", (nodeType, edge, expected) => {
    const port = findEdgePort(nodeType, edge);
    expect(port?.handleId).toBe(expected);
    // 选中的端口必须真的位于该边上
    expect(port?.position).toBe(edge);
  });
});
