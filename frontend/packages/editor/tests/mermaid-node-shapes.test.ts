import { describe, expect, it } from "vitest";
import type { MermaidNode } from "../src/mermaid/model";
import { getMermaidNodeSize, MERMAID_NODE_SHAPES } from "../src/mermaid/node-shapes";

describe("Mermaid Flowchart 图形目录", () => {
  it("按约定顺序提供十四种名称、分组、现代短名和端口数量", () => {
    expect(MERMAID_NODE_SHAPES).toEqual([
      { type: "stadium", label: "开始/结束", group: "flowchart", modernShape: "stadium", portCount: 8 },
      { type: "rectangle", label: "普通处理步骤", group: "flowchart", modernShape: "rect", portCount: 12 },
      { type: "rounded", label: "圆角处理节点", group: "flowchart", modernShape: "rounded", portCount: 8 },
      { type: "subroutine", label: "子程序", group: "flowchart", modernShape: "fr-rect", portCount: 12 },
      { type: "database", label: "数据库", group: "flowchart", modernShape: "cyl", portCount: 8 },
      { type: "circle", label: "连接点", group: "flowchart", modernShape: "circle", portCount: 8 },
      { type: "diamond", label: "条件判断", group: "flowchart", modernShape: "diam", portCount: 12 },
      { type: "hexagon", label: "准备步骤", group: "flowchart", modernShape: "hex", portCount: 12 },
      { type: "parallelogram", label: "输入或输出", group: "flowchart", modernShape: "lean-r", portCount: 12 },
      { type: "trapezoid", label: "人工处理", group: "flowchart", modernShape: "trap-b", portCount: 12 },
      { type: "double-circle", label: "终止节点", group: "flowchart", modernShape: "dbl-circ", portCount: 8 },
      { type: "text", label: "文本块", group: "document", modernShape: "text", portCount: 8 },
      { type: "doc", label: "文档", group: "document", modernShape: "doc", portCount: 8 },
      { type: "docs", label: "多文档", group: "document", modernShape: "docs", portCount: 8 }
    ]);
  });

  it("用同一尺寸函数约束画布节点和 ELK 包围盒", () => {
    const sizeOf = (type: MermaidNode["type"], text = "节点") => getMermaidNodeSize({ type, text });

    expect(sizeOf("rectangle")).toEqual({ width: 120, height: 52 });
    expect(sizeOf("rectangle", "这是一个非常非常长的普通处理步骤名称")).toEqual({ width: 190, height: 52 });
    expect(sizeOf("stadium")).toEqual({ width: 120, height: 52 });
    expect(sizeOf("subroutine")).toEqual({ width: 128, height: 52 });
    expect(sizeOf("database")).toEqual({ width: 132, height: 68 });
    expect(sizeOf("circle")).toEqual({ width: 92, height: 92 });
    expect(sizeOf("diamond")).toEqual({ width: 150, height: 88 });
    expect(sizeOf("hexagon")).toEqual({ width: 140, height: 72 });
    expect(sizeOf("parallelogram")).toEqual({ width: 140, height: 64 });
    expect(sizeOf("trapezoid")).toEqual({ width: 140, height: 64 });
    expect(sizeOf("double-circle")).toEqual({ width: 100, height: 100 });
    expect(sizeOf("text")).toEqual({ width: 100, height: 44 });
    expect(sizeOf("doc")).toEqual({ width: 132, height: 68 });
    expect(sizeOf("docs")).toEqual({ width: 140, height: 76 });
  });
});
