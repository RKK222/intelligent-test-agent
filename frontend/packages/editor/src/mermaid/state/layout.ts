import ELK from "elkjs/lib/elk.bundled.js";
import {
  cloneMermaidStateDiagram,
  type MermaidStateDiagram,
  type MermaidStateDirection,
  type MermaidStateNode,
  type MermaidStateRegion,
  type MermaidStateScope
} from "./model";

const elk = new ELK();
const NODE_MARGIN = 24;
const REGION_GAP = 44;

export type MermaidStateSize = { width: number; height: number };
export type MermaidStateRegionFrame = MermaidStateSize & { id: string; x: number; y: number };

function visualTextWidth(value: string): number {
  return [...value].reduce((width, character) => width + (character.charCodeAt(0) > 0xff ? 13 : 7), 0);
}

export function getMermaidStateNodeSize(
  node: MermaidStateNode,
  direction: MermaidStateDirection
): MermaidStateSize {
  if (node.kind === "start" || node.kind === "end") return { width: 30, height: 30 };
  if (node.kind === "choice") return { width: 44, height: 44 };
  if (node.kind === "fork" || node.kind === "join") {
    return direction === "TB" || direction === "BT"
      ? { width: 112, height: 16 }
      : { width: 16, height: 112 };
  }
  const lines = [node.label, ...node.descriptions].flatMap((value) => value.split("\n"));
  const width = Math.min(320, Math.max(node.childScope ? 184 : 132, ...lines.map(visualTextWidth)) + 38);
  const descriptionRows = Math.max(0, lines.length - 1);
  return {
    width,
    height: node.childScope ? Math.max(78, 64 + descriptionRows * 16) : Math.max(54, 48 + descriptionRows * 17)
  };
}

function elkDirection(direction: MermaidStateDirection): "DOWN" | "UP" | "RIGHT" | "LEFT" {
  if (direction === "BT") return "UP";
  if (direction === "LR") return "RIGHT";
  if (direction === "RL") return "LEFT";
  return "DOWN";
}

function fallbackLayout(region: MermaidStateRegion, direction: MermaidStateDirection): void {
  let cursor = NODE_MARGIN;
  for (const node of region.nodes) {
    node.position = direction === "LR" || direction === "RL"
      ? { x: cursor, y: NODE_MARGIN }
      : { x: NODE_MARGIN, y: cursor };
    const size = getMermaidStateNodeSize(node, direction);
    cursor += (direction === "LR" || direction === "RL" ? size.width : size.height) + 64;
  }
}

/** 单个 Region 使用 ELK 分层布局；失败时采用确定性线性布局，保证编辑器仍可打开。 */
async function layoutRegion(region: MermaidStateRegion, direction: MermaidStateDirection): Promise<void> {
  if (region.nodes.length === 0) return;
  const nodeIds = new Set(region.nodes.map((node) => node.id));
  try {
    const result = await elk.layout({
      id: region.id,
      layoutOptions: {
        "elk.algorithm": "layered",
        "elk.direction": elkDirection(direction),
        "elk.edgeRouting": "ORTHOGONAL",
        "elk.spacing.nodeNode": "48",
        "elk.layered.spacing.nodeNodeBetweenLayers": "72"
      },
      children: region.nodes.map((node) => ({
        id: node.id,
        ...getMermaidStateNodeSize(node, direction)
      })),
      edges: region.transitions
        .filter((transition) => nodeIds.has(transition.source) && nodeIds.has(transition.target))
        .map((transition) => ({ id: transition.id, sources: [transition.source], targets: [transition.target] }))
    });
    const byId = new Map(result.children?.map((child) => [child.id, child]) ?? []);
    for (const node of region.nodes) {
      const child = byId.get(node.id);
      if (!child || !Number.isFinite(child.x) || !Number.isFinite(child.y)) throw new Error("ELK 未返回节点坐标");
      node.position = {
        x: Math.round(((child.x ?? 0) + NODE_MARGIN) * 10) / 10,
        y: Math.round(((child.y ?? 0) + NODE_MARGIN) * 10) / 10
      };
    }
  } catch {
    fallbackLayout(region, direction);
  }

  const defaultHandles = direction === "TB"
    ? ["source-2", "target-0"]
    : direction === "BT"
      ? ["source-0", "target-2"]
      : direction === "LR" ? ["source-1", "target-3"] : ["source-3", "target-1"];
  for (const transition of region.transitions) {
    if (transition.source === transition.target) {
      transition.sourceHandle ??= "source-1";
      transition.targetHandle ??= "target-2";
    } else {
      transition.sourceHandle ??= defaultHandles[0];
      transition.targetHandle ??= defaultHandles[1];
    }
  }
}

function regionSize(region: MermaidStateRegion, direction: MermaidStateDirection): MermaidStateSize {
  let width = 220;
  let height = 160;
  for (const node of region.nodes) {
    const size = getMermaidStateNodeSize(node, direction);
    width = Math.max(width, node.position.x + size.width + NODE_MARGIN);
    height = Math.max(height, node.position.y + size.height + NODE_MARGIN);
  }
  return { width, height };
}

/** 返回聚焦画布中的 Region 外框；节点坐标本身始终保持 Region 局部坐标。 */
export function layoutMermaidStateRegions(scope: MermaidStateScope): MermaidStateRegionFrame[] {
  let crossCursor = NODE_MARGIN;
  return scope.regions.map((region) => {
    const size = regionSize(region, scope.direction);
    const horizontal = scope.direction === "TB" || scope.direction === "BT";
    const frame = {
      id: region.id,
      x: horizontal ? crossCursor : NODE_MARGIN,
      y: horizontal ? NODE_MARGIN : crossCursor,
      ...size
    };
    crossCursor += (horizontal ? size.width : size.height) + REGION_GAP;
    return frame;
  });
}

async function layoutScope(scope: MermaidStateScope): Promise<void> {
  // 子 Scope 先布局，使父层摘要尺寸和后续 Region 打包基于稳定的递归结果。
  for (const region of scope.regions) {
    for (const node of region.nodes) if (node.childScope) await layoutScope(node.childScope);
  }
  await Promise.all(scope.regions.map((region) => layoutRegion(region, scope.direction)));
}

export async function autoLayoutMermaidState(diagram: MermaidStateDiagram): Promise<MermaidStateDiagram> {
  const next = cloneMermaidStateDiagram(diagram);
  await layoutScope(next.root);
  return next;
}
