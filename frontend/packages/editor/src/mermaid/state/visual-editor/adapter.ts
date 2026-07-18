import { MarkerType, type Edge, type Node } from "@vue-flow/core";
import { getMermaidStateNodeSize, layoutMermaidStateRegions } from "../layout";
import type {
  MermaidStateNote,
  MermaidStateNode,
  MermaidStateRegion,
  MermaidStateScope,
  MermaidStateStyle
} from "../model";

export type StateNodeData = {
  node: MermaidStateNode;
  direction: MermaidStateScope["direction"];
  style?: MermaidStateStyle;
};

export type StateRegionData = {
  label: string;
  regionId: string;
  selected: boolean;
};

export type StateNoteData = {
  note: MermaidStateNote;
};

export type StateTransitionData = {
  transitionId: string;
};

export type StateFlowNode = Node<StateNodeData | StateRegionData | StateNoteData>;
export type StateFlowEdge = Edge<StateTransitionData>;

function notePosition(note: MermaidStateNote, region: MermaidStateRegion, scope: MermaidStateScope) {
  const target = region.nodes.find((node) => node.id === note.target);
  if (!target) return { x: 24, y: 24 };
  const size = getMermaidStateNodeSize(target, scope.direction);
  return {
    x: note.placement === "left" ? Math.max(8, target.position.x - 172) : target.position.x + size.width + 20,
    y: target.position.y
  };
}

/** 把当前聚焦 Scope 映射为一个 Vue Flow 场景；Region 作为父节点，状态坐标保持局部。 */
export function toStateFlowScene(scope: MermaidStateScope, selectedRegionId?: string): {
  nodes: StateFlowNode[];
  edges: StateFlowEdge[];
} {
  const frames = layoutMermaidStateRegions(scope);
  const nodes: StateFlowNode[] = [];
  const edges: StateFlowEdge[] = [];
  scope.regions.forEach((region, index) => {
    const frame = frames[index]!;
    const parentNode = `state-region:${region.id}`;
    nodes.push({
      id: parentNode,
      type: "state-region",
      position: { x: frame.x, y: frame.y },
      data: {
        label: scope.regions.length > 1 ? `并发区域 ${index + 1}` : "状态区域",
        regionId: region.id,
        selected: region.id === selectedRegionId
      },
      style: { width: `${frame.width}px`, height: `${frame.height}px` },
      draggable: false,
      selectable: false,
      zIndex: -1
    });
    for (const node of region.nodes) {
      nodes.push({
        id: node.id,
        type: "state",
        parentNode,
        position: { ...node.position },
        data: { node, direction: scope.direction, style: node.style },
        zIndex: 2
      });
    }
    for (const note of region.notes) {
      nodes.push({
        id: `state-note:${note.id}`,
        type: "state-note",
        parentNode,
        position: notePosition(note, region, scope),
        data: { note },
        draggable: false,
        selectable: false,
        zIndex: 3
      });
    }
    for (const transition of region.transitions) {
      edges.push({
        id: transition.id,
        source: transition.source,
        target: transition.target,
        sourceHandle: transition.sourceHandle ?? "source-2",
        targetHandle: transition.targetHandle ?? "target-0",
        label: transition.label || undefined,
        type: "state-transition",
        markerEnd: MarkerType.ArrowClosed,
        data: { transitionId: transition.id },
        ariaLabel: `转换 ${transition.source} 到 ${transition.target}${transition.label ? `：${transition.label}` : ""}`,
        zIndex: 1
      });
    }
  });
  return { nodes, edges };
}
