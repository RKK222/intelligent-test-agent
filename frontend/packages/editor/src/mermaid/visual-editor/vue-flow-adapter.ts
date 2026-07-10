import { MarkerType, type Connection, type Edge, type Node, type XYPosition } from "@vue-flow/core";
import { cloneMermaidGraph, type MermaidGraph, type MermaidNodeType } from "../model";

export type MermaidFlowNodeData = {
  text: string;
  nodeType: MermaidNodeType;
  direction: MermaidGraph["direction"];
};

export type MermaidFlowNode = Node<MermaidFlowNodeData, Record<string, never>, "mermaid">;
export type MermaidFlowEdge = Edge<Record<string, never>>;

export function toVueFlowNodes(graph: MermaidGraph): MermaidFlowNode[] {
  return graph.nodes.map((node) => ({
    id: node.id,
    type: "mermaid",
    position: { ...node.position },
    data: { text: node.text, nodeType: node.type, direction: graph.direction },
    ariaLabel: `${node.id} ${node.text}`
  }));
}

export function toVueFlowEdges(graph: MermaidGraph): MermaidFlowEdge[] {
  return graph.edges.map((edge) => ({
    id: edge.id,
    source: edge.source,
    target: edge.target,
    label: edge.label || undefined,
    type: "smoothstep",
    markerEnd: edge.relation === "line" ? undefined : MarkerType.ArrowClosed,
    animated: false,
    style:
      edge.relation === "dotted"
        ? { strokeDasharray: "5 4" }
        : edge.relation === "thick"
          ? { strokeWidth: 2.5 }
          : undefined,
    ariaLabel: `${edge.source} 到 ${edge.target}${edge.label ? `：${edge.label}` : ""}`
  }));
}

/** 把 Vue Flow 产生的位置变化隔离回领域模型副本。 */
export function applyVueFlowPositions(
  graph: MermaidGraph,
  positions: ReadonlyArray<{ id: string; position: XYPosition }>
): MermaidGraph {
  const next = cloneMermaidGraph(graph);
  const byId = new Map(positions.map((item) => [item.id, item.position]));
  next.nodes = next.nodes.map((node) => {
    const position = byId.get(node.id);
    return position ? { ...node, position: { x: position.x, y: position.y } } : node;
  });
  return next;
}

/** Vue Flow Handle 连接仅转换为当前支持的有向 Mermaid 边，重复连接保持幂等。 */
export function appendMermaidEdge(graph: MermaidGraph, connection: Pick<Connection, "source" | "target">): MermaidGraph {
  if (!connection.source || !connection.target) return graph;
  if (graph.edges.some((edge) => edge.source === connection.source && edge.target === connection.target)) {
    return graph;
  }
  const next = cloneMermaidGraph(graph);
  const usedIds = new Set(next.edges.map((edge) => edge.id));
  let sequence = next.edges.length + 1;
  while (usedIds.has(`edge-${sequence}`)) sequence += 1;
  next.edges.push({
    id: `edge-${sequence}`,
    source: connection.source,
    target: connection.target,
    label: "",
    relation: "arrow"
  });
  return next;
}
