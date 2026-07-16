import { describe, expect, it, vi } from "vitest";
import type { MermaidEdge, MermaidGraph, MermaidPosition } from "../src/mermaid/model";
import { cloneMermaidGraph } from "../src/mermaid/model";
import { autoLayoutMermaidGraph, syncAutoLayoutMermaidGraph } from "../src/mermaid/layout";
import { parseMermaidFlowchart } from "../src/mermaid/parser";
import { serializeMermaidGraph } from "../src/mermaid/serializer";

type EdgeWithRoute = MermaidEdge & { route?: { points: MermaidPosition[] } };

function graphWithRoute(): MermaidGraph {
  const graph = parseMermaidFlowchart("flowchart TD\nA --> B");
  (graph.edges[0] as EdgeWithRoute).route = {
    points: [
      { x: 120, y: 80 },
      { x: 120, y: 160 },
      { x: 260, y: 160 }
    ]
  };
  return graph;
}

function isOrthogonal(points: MermaidPosition[]): boolean {
  return points.slice(1).every((point, index) => {
    const previous = points[index]!;
    return point.x === previous.x || point.y === previous.y;
  });
}

function countProperCrossings(graph: MermaidGraph): number {
  const routed = graph.edges.flatMap((edge) => {
    if (!edge.route) return [];
    return [{
      edge,
      segments: edge.route.points.slice(1).map((point, index) => [edge.route!.points[index]!, point] as const)
    }];
  });
  let crossings = 0;
  for (let leftIndex = 0; leftIndex < routed.length; leftIndex += 1) {
    const left = routed[leftIndex]!;
    for (let rightIndex = leftIndex + 1; rightIndex < routed.length; rightIndex += 1) {
      const right = routed[rightIndex]!;
      const sharesNode = [left.edge.source, left.edge.target].some(
        (nodeId) => nodeId === right.edge.source || nodeId === right.edge.target
      );
      if (sharesNode) continue;
      for (const [a, b] of left.segments) {
        for (const [c, d] of right.segments) {
          const firstVertical = a.x === b.x && a.y !== b.y;
          const secondVertical = c.x === d.x && c.y !== d.y;
          if (firstVertical === secondVertical) continue;
          const vertical = firstVertical ? [a, b] as const : [c, d] as const;
          const horizontal = firstVertical ? [c, d] as const : [a, b] as const;
          const x = vertical[0].x;
          const y = horizontal[0].y;
          const insideVertical = y > Math.min(vertical[0].y, vertical[1].y) && y < Math.max(vertical[0].y, vertical[1].y);
          const insideHorizontal = x > Math.min(horizontal[0].x, horizontal[1].x) && x < Math.max(horizontal[0].x, horizontal[1].x);
          if (insideVertical && insideHorizontal) crossings += 1;
        }
      }
    }
  }
  return crossings;
}

function segmentEntersUnrelatedNode(
  start: MermaidPosition,
  end: MermaidPosition,
  node: MermaidGraph["nodes"][number]
): boolean {
  const width = node.type === "circle"
    ? 92
    : node.type === "diamond"
      ? 150
      : Math.max(120, Math.min(190, node.text.length * 12 + (node.type === "stadium" ? 48 : 40)));
  const height = node.type === "circle" ? 92 : node.type === "diamond" ? 88 : 52;
  const left = node.position.x;
  const right = left + width;
  const top = node.position.y;
  const bottom = top + height;
  if (start.x === end.x) {
    return start.x > left && start.x < right
      && Math.max(Math.min(start.y, end.y), top) < Math.min(Math.max(start.y, end.y), bottom);
  }
  return start.y > top && start.y < bottom
    && Math.max(Math.min(start.x, end.x), left) < Math.min(Math.max(start.x, end.x), right);
}

function routesAvoidUnrelatedNodes(graph: MermaidGraph): boolean {
  return graph.edges.every((edge) => edge.route?.points.slice(1).every((point, index) => {
    const start = edge.route!.points[index]!;
    return graph.nodes
      .filter((node) => node.id !== edge.source && node.id !== edge.target)
      .every((node) => !segmentEntersUnrelatedNode(start, point, node));
  }) ?? false);
}

describe("Mermaid edge route metadata", () => {
  it("序列化并重新解析后保留自动布局的边路由", () => {
    const serialized = serializeMermaidGraph(graphWithRoute());
    const reparsed = parseMermaidFlowchart(serialized);

    expect(serialized.split("\n").filter((line) => /^%%@(?!\+)/.test(line))).toHaveLength(1);
    expect(serialized).not.toContain("editor-edge-routes");
    expect((reparsed.edges[0] as EdgeWithRoute | undefined)?.route?.points).toEqual([
      { x: 120, y: 80 },
      { x: 120, y: 160 },
      { x: 260, y: 160 }
    ]);
  });

  it("深拷贝图模型时不共享边路由坐标", () => {
    const graph = graphWithRoute();
    const cloned = cloneMermaidGraph(graph);
    const clonedRoute = (cloned.edges[0] as EdgeWithRoute | undefined)?.route;

    expect(clonedRoute).toBeDefined();
    clonedRoute!.points[0]!.x = 999;
    expect(((graph.edges[0] as EdgeWithRoute).route?.points[0]?.x)).toBe(120);
  });

  it("同步预布局改变节点坐标时也会清除旧路由", () => {
    const graph = graphWithRoute();
    const laidOut = syncAutoLayoutMermaidGraph(graph);

    expect(laidOut.edges[0]!.route).toBeUndefined();
    expect(graph.edges[0]!.route).toBeDefined();
  });

});

describe("Mermaid ELK orthogonal routing", () => {
  it.each(["TD", "BT", "LR", "RL"] as const)("%s 自动布局为每条边生成有限的正交路径", async (direction) => {
    const graph = parseMermaidFlowchart(`flowchart ${direction}\nA[入口] --> B{判断}\nA --> C[旁路]\nB --> D[结束]\nC --> D`);
    const laidOut = await autoLayoutMermaidGraph(graph);

    for (const edge of laidOut.edges) {
      expect(edge.route?.points.length).toBeGreaterThanOrEqual(2);
      expect(edge.route?.points.every((point) => Number.isFinite(point.x) && Number.isFinite(point.y))).toBe(true);
      expect(edge.route?.points.every((point) =>
        Number.isInteger(point.x * 10) && Number.isInteger(point.y * 10)
      )).toBe(true);
      expect(isOrthogonal(edge.route?.points ?? [])).toBe(true);
    }
    expect(routesAvoidUnrelatedNodes(laidOut)).toBe(true);
    expect(graph.edges.every((edge) => edge.route === undefined)).toBe(true);
  });

  it("按判断节点真实高度和正交轨道间距分层", async () => {
    const graph = parseMermaidFlowchart("flowchart TD\nA{判断} --> B[结束]");
    const laidOut = await autoLayoutMermaidGraph(graph);
    const a = laidOut.nodes.find((node) => node.id === "A")!;
    const b = laidOut.nodes.find((node) => node.id === "B")!;

    expect(b.position.y - a.position.y).toBeGreaterThanOrEqual(180);
  });

  it("在分支汇合与跨层连线图中不产生非相邻边十字交叉", async () => {
    const graph = parseMermaidFlowchart(`flowchart TD
A[入口] --> B{条件一}
A --> C{条件二}
B --> D[任务一]
B --> E[任务二]
C --> F[任务三]
D --> G[汇总]
E --> G
F --> G
B --> F`);
    const laidOut = await autoLayoutMermaidGraph(graph);

    expect(laidOut.edges.every((edge) => edge.route && edge.route.points.length >= 2)).toBe(true);
    expect(countProperCrossings(laidOut)).toBe(0);
  });

  it("ELK 无法为异常边生成 section 时清除旧路由并回退普通连线", async () => {
    const graph = parseMermaidFlowchart("flowchart TD\nA[入口]");
    graph.edges.push({
      id: "edge-1",
      source: "A",
      target: "missing",
      label: "",
      relation: "arrow",
      route: { points: [{ x: 80, y: 70 }, { x: 80, y: 160 }] }
    });
    const errorSpy = vi.spyOn(console, "error").mockImplementation(() => undefined);

    try {
      const laidOut = await autoLayoutMermaidGraph(graph);
      expect(laidOut.edges[0]!.route).toBeUndefined();
      expect(graph.edges[0]!.route).toBeDefined();
    } finally {
      errorSpy.mockRestore();
    }
  });
});
