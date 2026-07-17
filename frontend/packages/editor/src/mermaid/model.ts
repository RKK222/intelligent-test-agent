export type MermaidGraphKind = "flowchart" | "graph";
export type MermaidDirection = "TD" | "TB" | "BT" | "LR" | "RL";
export type MermaidNodeType =
  | "rectangle"
  | "rounded"
  | "stadium"
  | "diamond"
  | "circle"
  | "subroutine"
  | "database"
  | "hexagon"
  | "parallelogram"
  | "trapezoid"
  | "double-circle"
  | "text"
  | "doc"
  | "docs";
export type MermaidEdgeRelation = "arrow" | "line" | "dotted" | "thick";

export type MermaidPosition = {
  x: number;
  y: number;
};

/** 自动布局派生的边路由；坐标与节点 position 使用同一 Vue Flow 画布坐标系。 */
export type MermaidEdgeRoute = {
  points: MermaidPosition[];
};

export type MermaidNodeStyle = {
  textColor?: string;
  fillColor?: string;
  strokeColor?: string;
};

export type MermaidEdgeStyle = {
  textColor?: string;
};

export type MermaidNode = {
  id: string;
  text: string;
  type: MermaidNodeType;
  position: MermaidPosition;
  scale?: number;
  style?: MermaidNodeStyle;
};

export type MermaidEdge = {
  id: string;
  source: string;
  target: string;
  sourceHandle?: string;
  targetHandle?: string;
  route?: MermaidEdgeRoute;
  label: string;
  relation: MermaidEdgeRelation;
  style?: MermaidEdgeStyle;
};

export type MermaidPreservedSegment = {
  /** 在第几条可编辑边/消息之前写回；超出范围时追加到末尾。 */
  beforeEditableIndex: number;
  lines: string[];
  /** 片段内 Mermaid 实际 link 数，用于把可编辑边映射到全局 linkStyle 索引。 */
  linkCount?: number;
};

export type MermaidGraph = {
  kind: MermaidGraphKind;
  direction: MermaidDirection;
  nodes: MermaidNode[];
  edges: MermaidEdge[];
  /** 当前可视化范围外的 Mermaid 语句，序列化时必须原样写回。 */
  preservedLines: string[];
  preservedSegments?: MermaidPreservedSegment[];
};

export type MermaidBlock = {
  index: number;
  source: string;
  sourceStart: number;
  sourceEnd: number;
};

/** 深拷贝编辑模型，避免对话框草稿反向修改 Markdown 当前快照。 */
export function cloneMermaidGraph(graph: MermaidGraph): MermaidGraph {
  return {
    ...graph,
    nodes: graph.nodes.map((node) => ({
      ...node,
      position: { ...node.position },
      style: node.style ? { ...node.style } : undefined
    })),
    edges: graph.edges.map((edge) => ({
      ...edge,
      style: edge.style ? { ...edge.style } : undefined,
      route: edge.route ? { points: edge.route.points.map((point) => ({ ...point })) } : undefined
    })),
    preservedLines: [...graph.preservedLines],
    preservedSegments: graph.preservedSegments?.map((segment) => ({
      beforeEditableIndex: segment.beforeEditableIndex,
      lines: [...segment.lines],
      ...(segment.linkCount ? { linkCount: segment.linkCount } : {})
    }))
  };
}

/** 几何或拓扑变化后清除全部派生路径，避免继续渲染已经不再贴合节点的旧轨道。 */
export function clearMermaidEdgeRoutes(graph: MermaidGraph): MermaidGraph {
  const next = cloneMermaidGraph(graph);
  for (const edge of next.edges) delete edge.route;
  return next;
}
