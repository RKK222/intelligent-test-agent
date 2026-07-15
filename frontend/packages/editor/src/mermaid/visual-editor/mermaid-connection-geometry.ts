import type { Position } from "@vue-flow/core";

export const MERMAID_SOURCE_HIT_RADIUS = 18;
export const MERMAID_TARGET_ACTIVATION_MARGIN = 24;
export const MERMAID_PORT_SNAP_RADIUS = 28;

export type MermaidScreenPoint = { x: number; y: number };
export type MermaidScreenRect = { left: number; top: number; right: number; bottom: number };
export type MermaidConnectionNodeGeometry = { nodeId: string; rect: MermaidScreenRect };
export type MermaidConnectionPortGeometry = MermaidScreenPoint & {
  nodeId: string;
  handleId: string;
  position: Position;
};

function squaredDistance(a: MermaidScreenPoint, b: MermaidScreenPoint): number {
  return (a.x - b.x) ** 2 + (a.y - b.y) ** 2;
}

export function isPointWithinPortRadius(
  point: MermaidScreenPoint,
  port: MermaidConnectionPortGeometry,
  radius = MERMAID_SOURCE_HIT_RADIUS
): boolean {
  return squaredDistance(point, port) <= radius ** 2;
}

/** 返回屏幕点到矩形外框的最短距离；矩形内部距离为 0。 */
export function distanceToScreenRect(point: MermaidScreenPoint, rect: MermaidScreenRect): number {
  const dx = Math.max(rect.left - point.x, 0, point.x - rect.right);
  const dy = Math.max(rect.top - point.y, 0, point.y - rect.bottom);
  return Math.hypot(dx, dy);
}

/** 最上层直接命中优先，否则按外框距离和输入稳定顺序选取候选节点。 */
export function findConnectionTargetNode(
  point: MermaidScreenPoint,
  nodes: ReadonlyArray<MermaidConnectionNodeGeometry>,
  topmostNodeId?: string,
  margin = MERMAID_TARGET_ACTIVATION_MARGIN
): MermaidConnectionNodeGeometry | undefined {
  if (topmostNodeId) {
    const direct = nodes.find((node) => node.nodeId === topmostNodeId);
    if (direct && distanceToScreenRect(point, direct.rect) === 0) return direct;
  }
  let nearest: MermaidConnectionNodeGeometry | undefined;
  let nearestDistance = Number.POSITIVE_INFINITY;
  for (const node of nodes) {
    const distance = distanceToScreenRect(point, node.rect);
    if (distance <= margin && distance < nearestDistance) {
      nearest = node;
      nearestDistance = distance;
    }
  }
  return nearest;
}

/** 选择吸附半径内最近端口；距离相同时保留 DOM 测量顺序。 */
export function findNearestConnectionPort(
  point: MermaidScreenPoint,
  ports: ReadonlyArray<MermaidConnectionPortGeometry>,
  radius = MERMAID_PORT_SNAP_RADIUS
): MermaidConnectionPortGeometry | undefined {
  let nearest: MermaidConnectionPortGeometry | undefined;
  let nearestDistance = Number.POSITIVE_INFINITY;
  for (const port of ports) {
    const distance = squaredDistance(point, port);
    if (distance <= radius ** 2 && distance < nearestDistance) {
      nearest = port;
      nearestDistance = distance;
    }
  }
  return nearest;
}
