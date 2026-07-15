/// <reference types="vite/client" />
import { Position } from "@vue-flow/core";
import { describe, expect, it } from "vitest";
import {
  findEdgePort,
  getMermaidNodePorts,
  oppositePosition
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
    // 圆角与胶囊共用同一套端口坐标
    expect(getMermaidNodePorts("stadium").map((p) => `${p.x},${p.y}`)).toEqual(
      getMermaidNodePorts("rounded").map((p) => `${p.x},${p.y}`)
    );
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
