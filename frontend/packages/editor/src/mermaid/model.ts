export type MermaidGraphKind = "flowchart" | "graph";
export type MermaidDirection = "TD" | "TB" | "BT" | "LR" | "RL";
export type MermaidNodeType = "rectangle" | "rounded" | "stadium" | "diamond" | "circle";
export type MermaidEdgeRelation = "arrow" | "line" | "dotted" | "thick";

export type MermaidPosition = {
  x: number;
  y: number;
};

export type MermaidNode = {
  id: string;
  text: string;
  type: MermaidNodeType;
  position: MermaidPosition;
};

export type MermaidEdge = {
  id: string;
  source: string;
  target: string;
  label: string;
  relation: MermaidEdgeRelation;
};

export type MermaidPreservedSegment = {
  /** 在第几条可编辑边/消息之前写回；超出范围时追加到末尾。 */
  beforeEditableIndex: number;
  lines: string[];
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
    nodes: graph.nodes.map((node) => ({ ...node, position: { ...node.position } })),
    edges: graph.edges.map((edge) => ({ ...edge })),
    preservedLines: [...graph.preservedLines],
    preservedSegments: graph.preservedSegments?.map((segment) => ({
      beforeEditableIndex: segment.beforeEditableIndex,
      lines: [...segment.lines]
    }))
  };
}
