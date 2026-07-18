import type { MermaidPosition } from "../model";

export type MermaidStateHeader = "stateDiagram" | "stateDiagram-v2";
export type MermaidStateDirection = "TB" | "BT" | "LR" | "RL";
export type MermaidStateNodeKind = "state" | "start" | "end" | "choice" | "fork" | "join";
export type MermaidStateNotePlacement = "left" | "right";

export type MermaidStateStyle = {
  textColor?: string;
  fillColor?: string;
  strokeColor?: string;
};

export type MermaidStateNode = {
  id: string;
  kind: MermaidStateNodeKind;
  label: string;
  descriptions: string[];
  position: MermaidPosition;
  style?: MermaidStateStyle;
  childScope?: MermaidStateScope;
  /** parser 用于区分隐式引用和显式 state 声明，不参与对外序列化。 */
  declared?: boolean;
};

export type MermaidStateTransition = {
  id: string;
  source: string;
  target: string;
  label: string;
  sourceHandle?: string;
  targetHandle?: string;
};

export type MermaidStateNote = {
  id: string;
  target: string;
  placement: MermaidStateNotePlacement;
  text: string;
};

export type MermaidStateRegion = {
  id: string;
  nodes: MermaidStateNode[];
  transitions: MermaidStateTransition[];
  notes: MermaidStateNote[];
};

export type MermaidStateScope = {
  id: string;
  direction: MermaidStateDirection;
  regions: MermaidStateRegion[];
  /** 不影响拓扑且当前编辑器不接管的 Mermaid 指令。 */
  preservedLines: string[];
};

export type MermaidStateSourceFormat = {
  source: string;
  headerRaw: string;
  eol: "\n" | "\r\n";
  trailingNewline: boolean;
  indentUnit: string;
  fingerprint: string;
  metadataConflict?: boolean;
};

export type MermaidStateDiagram = {
  kind: "stateDiagram";
  header: MermaidStateHeader;
  root: MermaidStateScope;
  sourceFormat?: MermaidStateSourceFormat;
};

export function createMermaidStateRegion(id: string): MermaidStateRegion {
  return { id, nodes: [], transitions: [], notes: [] };
}

export function createMermaidStateScope(
  id: string,
  direction: MermaidStateDirection = "TB"
): MermaidStateScope {
  return {
    id,
    direction,
    regions: [createMermaidStateRegion(`${id}-region-1`)],
    preservedLines: []
  };
}

/** 深度优先返回所有 Scope，顺序同时作为 metadata 拓扑协议的一部分。 */
export function flattenMermaidStateScopes(diagram: MermaidStateDiagram): MermaidStateScope[] {
  const scopes: MermaidStateScope[] = [];
  const visit = (scope: MermaidStateScope) => {
    scopes.push(scope);
    for (const region of scope.regions) {
      for (const node of region.nodes) if (node.childScope) visit(node.childScope);
    }
  };
  visit(diagram.root);
  return scopes;
}

export function flattenMermaidStateRegions(diagram: MermaidStateDiagram): MermaidStateRegion[] {
  return flattenMermaidStateScopes(diagram).flatMap((scope) => scope.regions);
}

export function flattenMermaidStateNodes(diagram: MermaidStateDiagram): MermaidStateNode[] {
  return flattenMermaidStateRegions(diagram).flatMap((region) => region.nodes);
}

export function flattenMermaidStateTransitions(diagram: MermaidStateDiagram): MermaidStateTransition[] {
  return flattenMermaidStateRegions(diagram).flatMap((region) => region.transitions);
}

function cloneScope(scope: MermaidStateScope): MermaidStateScope {
  return {
    ...scope,
    preservedLines: [...scope.preservedLines],
    regions: scope.regions.map((region) => ({
      ...region,
      nodes: region.nodes.map((node) => ({
        ...node,
        descriptions: [...node.descriptions],
        position: { ...node.position },
        style: node.style ? { ...node.style } : undefined,
        childScope: node.childScope ? cloneScope(node.childScope) : undefined
      })),
      transitions: region.transitions.map((transition) => ({ ...transition })),
      notes: region.notes.map((note) => ({ ...note }))
    }))
  };
}

/** 对话框草稿必须与 Markdown 当前快照完全隔离，包括任意深度的复合状态。 */
export function cloneMermaidStateDiagram(diagram: MermaidStateDiagram): MermaidStateDiagram {
  return {
    ...diagram,
    root: cloneScope(diagram.root),
    sourceFormat: diagram.sourceFormat ? { ...diagram.sourceFormat } : undefined
  };
}

function scopeFingerprint(scope: MermaidStateScope): unknown {
  return [
    scope.id,
    scope.direction,
    scope.preservedLines,
    scope.regions.map((region) => [
      region.id,
      region.nodes.map((node) => [
        node.id,
        node.kind,
        node.label,
        node.descriptions,
        node.position.x,
        node.position.y,
        node.style ?? null,
        node.childScope ? scopeFingerprint(node.childScope) : null
      ]),
      region.notes.map((note) => [note.id, note.target, note.placement, note.text]),
      region.transitions.map((transition) => [
        transition.id,
        transition.source,
        transition.target,
        transition.label,
        transition.sourceHandle ?? null,
        transition.targetHandle ?? null
      ])
    ])
  ];
}

export function mermaidStateDiagramFingerprint(diagram: MermaidStateDiagram): string {
  return JSON.stringify([diagram.header, scopeFingerprint(diagram.root)]);
}

/** 查找节点所属 Scope/Region，供校验和可视化连接策略共享。 */
export function indexMermaidStateNodes(diagram: MermaidStateDiagram): Map<
  string,
  { node: MermaidStateNode; scope: MermaidStateScope; region: MermaidStateRegion }
> {
  const result = new Map<string, { node: MermaidStateNode; scope: MermaidStateScope; region: MermaidStateRegion }>();
  for (const scope of flattenMermaidStateScopes(diagram)) {
    for (const region of scope.regions) {
      for (const node of region.nodes) result.set(node.id, { node, scope, region });
    }
  }
  return result;
}
