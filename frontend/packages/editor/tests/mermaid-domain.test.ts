import { describe, expect, it } from "vitest";
import { autoLayoutMermaidGraph } from "../src/mermaid/layout";
import { findMermaidBlocks, replaceMermaidBlock } from "../src/mermaid/markdown-blocks";
import { parseMermaidFlowchart } from "../src/mermaid/parser";
import { serializeMermaidGraph } from "../src/mermaid/serializer";

describe("Mermaid flowchart 领域层", () => {
  it("解析节点、边、标签、方向、布局 metadata 并保留未知语句", () => {
    const graph = parseMermaidFlowchart(`flowchart LR
%% editor-layout:
%% {
%%   "A": { "x": 120, "y": 80 },
%%   "B": { "x": 360, "y": 80 }
%% }
A[开始] -->|通过| B{检查}
classDef important fill:red`);

    expect(graph.kind).toBe("flowchart");
    expect(graph.direction).toBe("LR");
    expect(graph.nodes).toEqual([
      { id: "A", text: "开始", type: "rectangle", position: { x: 120, y: 80 } },
      { id: "B", text: "检查", type: "diamond", position: { x: 360, y: 80 } }
    ]);
    expect(graph.edges).toMatchObject([
      { source: "A", target: "B", label: "通过", relation: "arrow" }
    ]);
    expect(graph.preservedLines).toEqual(["classDef important fill:red"]);
  });

  it("支持 graph、独立节点声明和常用节点类型", () => {
    const graph = parseMermaidFlowchart(`graph TD
A([开始])
B((结束))
C[普通]
A -.-> B
B --- C`);

    expect(graph.kind).toBe("graph");
    expect(graph.nodes.map((node) => [node.id, node.type])).toEqual([
      ["A", "stadium"],
      ["B", "circle"],
      ["C", "rectangle"]
    ]);
    expect(graph.edges.map((edge) => edge.relation)).toEqual(["dotted", "line"]);
  });

  it("序列化结果可被 Mermaid 官方 parser 接受并稳定 round trip", async () => {
    const original = `flowchart TD
A[开始] --> B(处理)
B ==>|完成| C((结束))
classDef important fill:red`;
    const serialized = serializeMermaidGraph(parseMermaidFlowchart(original));
    const roundTrip = parseMermaidFlowchart(serialized);
    const mermaid = (await import("mermaid")).default;

    await expect(mermaid.parse(serialized)).resolves.toBeTruthy();
    expect(roundTrip.nodes.map(({ id, text, type }) => ({ id, text, type }))).toEqual([
      { id: "A", text: "开始", type: "rectangle" },
      { id: "B", text: "处理", type: "rounded" },
      { id: "C", text: "结束", type: "circle" }
    ]);
    expect(roundTrip.preservedLines).toContain("classDef important fill:red");
    expect(serializeMermaidGraph(roundTrip)).toBe(serialized);
  });

  it("只替换指定 Mermaid fence 内容并检测外部刷新冲突", () => {
    const markdown = `# 设计

\`\`\`mermaid
flowchart TD
A --> B
\`\`\`

正文

~~~mermaid
graph LR
X --> Y
~~~`;
    const blocks = findMermaidBlocks(markdown);

    expect(blocks.map((block) => block.source)).toEqual([
      "flowchart TD\nA --> B\n",
      "graph LR\nX --> Y\n"
    ]);
    const next = replaceMermaidBlock(markdown, 1, "graph LR\nX[新] --> Y\n", blocks[1]?.source);
    expect(next).toContain("flowchart TD\nA --> B");
    expect(next).toContain("graph LR\nX[新] --> Y");
    expect(() => replaceMermaidBlock(next, 1, "graph TD", blocks[1]?.source)).toThrow(
      "Mermaid 代码块已发生变化"
    );
  });

  it("自动布局按图方向生成确定性坐标且不改变输入模型", () => {
    const graph = parseMermaidFlowchart("flowchart LR\nA --> B\nA --> C\nC --> D");
    const laidOut = autoLayoutMermaidGraph(graph);

    expect(laidOut).not.toBe(graph);
    expect(laidOut.nodes.find((node) => node.id === "A")?.position.x).toBeLessThan(
      laidOut.nodes.find((node) => node.id === "B")?.position.x ?? 0
    );
    expect(laidOut.nodes.find((node) => node.id === "A")?.position.x).toBeLessThan(
      laidOut.nodes.find((node) => node.id === "C")?.position.x ?? 0
    );
    expect(graph.nodes.every((node) => node.position.x === 0 && node.position.y === 0)).toBe(true);
    expect(autoLayoutMermaidGraph(graph)).toEqual(laidOut);
  });

  it("拒绝非 flowchart/graph 和缺少图头的内容", () => {
    expect(() => parseMermaidFlowchart("sequenceDiagram\nA->>B: hi")).toThrow(
      "仅支持 flowchart 或 graph"
    );
    expect(() => parseMermaidFlowchart("A --> B")).toThrow("缺少 flowchart 或 graph 图头");
  });

  it("subgraph 整块不参与可视化并保持内部语句连续", () => {
    const serialized = serializeMermaidGraph(parseMermaidFlowchart(`flowchart TD
subgraph Cluster[内部]
  A --> B
end
C --> D`));

    expect(serialized).toContain("subgraph Cluster[内部]\n  A --> B\nend");
    expect(serialized.indexOf("subgraph Cluster")).toBeLessThan(serialized.indexOf("C --> D"));
    expect(parseMermaidFlowchart(serialized).edges).toHaveLength(1);
    expect(parseMermaidFlowchart(serialized).edges[0]).toMatchObject({ source: "C", target: "D" });
  });
});
