import { MarkerType, type Edge, type Node, type XYPosition } from "@vue-flow/core";
import { isMermaidPortHandle } from "../edge-port-metadata";
import { cloneMermaidGraph, type MermaidGraph, type MermaidNodeType } from "../model";
import { getMermaidNodePortId } from "./node-ports";

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
  const sourceCounts = new Map<string, number>();
  const targetCounts = new Map<string, number>();

  return graph.edges.map((edge) => {
    const sourceIndex = sourceCounts.get(edge.source) ?? 0;
    const targetIndex = targetCounts.get(edge.target) ?? 0;
    sourceCounts.set(edge.source, sourceIndex + 1);
    targetCounts.set(edge.target, targetIndex + 1);

    return {
      id: edge.id,
      source: edge.source,
      target: edge.target,
      sourceHandle: edge.sourceHandle ?? getMermaidNodePortId("source", sourceIndex),
      targetHandle: edge.targetHandle ?? getMermaidNodePortId("target", targetIndex),
      label: edge.label || undefined,
      type: "mermaid-edge",
      markerEnd: edge.relation === "line" ? undefined : MarkerType.ArrowClosed,
      animated: false,
      style:
        edge.relation === "dotted"
          ? { strokeDasharray: "5 4" }
          : edge.relation === "thick"
            ? { strokeWidth: 2.5 }
            : undefined,
      ariaLabel: `${edge.source} 到 ${edge.target}${edge.label ? `：${edge.label}` : ""}`
    };
  });
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

export type MermaidPortConnection = {
  source: string | null;
  target: string | null;
  sourceHandle: string | null;
  targetHandle: string | null;
};

/** 自研拖线控制器与写入层共用同一约束，避免视觉状态和最终落盘不一致。
 *  excludeEdgeId 用于重连：判重时排除被更新的边本身，允许在同一节点对上更换端口。 */
export function canAppendMermaidEdge(
  graph: MermaidGraph,
  connection: MermaidPortConnection,
  excludeEdgeId?: string
): boolean {
  if (
    !connection.source ||
    !connection.target ||
    !isMermaidPortHandle(connection.sourceHandle) ||
    !isMermaidPortHandle(connection.targetHandle)
  ) return false;
  if (graph.edges.some((edge) => edge.id !== excludeEdgeId && edge.source === connection.source && edge.target === connection.target)) {
    return false;
  }
  return !(
    connection.source === connection.target &&
    connection.sourceHandle === connection.targetHandle
  );
}

/** 获取连接被拒绝的具体文字原因。 */
export function getMermaidConnectionInvalidReason(
  graph: MermaidGraph,
  connection: MermaidPortConnection,
  excludeEdgeId?: string
): string | undefined {
  if (!connection.source || !connection.target) {
    return undefined;
  }
  if (!isMermaidPortHandle(connection.sourceHandle) || !isMermaidPortHandle(connection.targetHandle)) {
    return "连接点无效，请对齐连接端口";
  }
  if (graph.edges.some((edge) => edge.id !== excludeEdgeId && edge.source === connection.source && edge.target === connection.target)) {
    return "节点间已存在相同方向的连线";
  }
  if (connection.source === connection.target && connection.sourceHandle === connection.targetHandle) {
    return "不能在同一个端口上建立自环连接";
  }
  return undefined;
}

/** 只把带完整固定端口的有效拖线转换为 Mermaid 边。 */
export function appendMermaidEdge(graph: MermaidGraph, connection: MermaidPortConnection): MermaidGraph {
  if (!canAppendMermaidEdge(graph, connection)) return graph;
  const next = cloneMermaidGraph(graph);
  const usedIds = new Set(next.edges.map((edge) => edge.id));
  let sequence = next.edges.length + 1;
  while (usedIds.has(`edge-${sequence}`)) sequence += 1;
  next.edges.push({
    id: `edge-${sequence}`,
    source: connection.source!,
    target: connection.target!,
    sourceHandle: connection.sourceHandle!,
    targetHandle: connection.targetHandle!,
    label: "",
    relation: "arrow"
  });
  return next;
}

/** 把已有边的一端重连到新节点/端口：end='target' 更新目标端，end='source' 更新起点端。 */
export function updateMermaidEdge(
  graph: MermaidGraph,
  edgeId: string,
  end: "source" | "target",
  connection: MermaidPortConnection
): MermaidGraph {
  const next = cloneMermaidGraph(graph);
  const edge = next.edges.find((item) => item.id === edgeId);
  if (!edge) return graph;
  if (end === "target") {
    edge.target = connection.target ?? edge.target;
    edge.targetHandle = connection.targetHandle ?? edge.targetHandle;
  } else {
    edge.source = connection.source ?? edge.source;
    edge.sourceHandle = connection.sourceHandle ?? edge.sourceHandle;
  }
  return next;
}
