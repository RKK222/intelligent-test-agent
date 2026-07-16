import { Position } from "@vue-flow/core";
import type { MermaidPosition } from "../model";

const ENDPOINT_DIRECTION_EPSILON = 0.5;

function isFinitePoint(point: MermaidPosition | undefined): point is MermaidPosition {
  return !!point && Number.isFinite(point.x) && Number.isFinite(point.y);
}

function samePoint(left: MermaidPosition, right: MermaidPosition): boolean {
  return left.x === right.x && left.y === right.y;
}

function isOutsideEndpoint(
  point: MermaidPosition,
  endpoint: MermaidPosition,
  position: Position
): boolean {
  if (position === Position.Top) return point.y < endpoint.y - ENDPOINT_DIRECTION_EPSILON;
  if (position === Position.Bottom) return point.y > endpoint.y + ENDPOINT_DIRECTION_EPSILON;
  if (position === Position.Left) return point.x < endpoint.x - ENDPOINT_DIRECTION_EPSILON;
  return point.x > endpoint.x + ENDPOINT_DIRECTION_EPSILON;
}

function endpointBridge(
  endpoint: MermaidPosition,
  guide: MermaidPosition,
  position: Position
): MermaidPosition {
  return position === Position.Top || position === Position.Bottom
    ? { x: endpoint.x, y: guide.y }
    : { x: guide.x, y: endpoint.y };
}

function leavesSourceOutward(points: MermaidPosition[], position: Position): boolean {
  const source = points[0];
  const next = points[1];
  if (!source || !next) return false;
  if (position === Position.Top) return next.x === source.x && next.y < source.y - ENDPOINT_DIRECTION_EPSILON;
  if (position === Position.Bottom) return next.x === source.x && next.y > source.y + ENDPOINT_DIRECTION_EPSILON;
  if (position === Position.Left) return next.y === source.y && next.x < source.x - ENDPOINT_DIRECTION_EPSILON;
  return next.y === source.y && next.x > source.x + ENDPOINT_DIRECTION_EPSILON;
}

function entersTargetInward(points: MermaidPosition[], position: Position): boolean {
  const previous = points.at(-2);
  const target = points.at(-1);
  if (!previous || !target) return false;
  if (position === Position.Top) return previous.x === target.x && previous.y < target.y - ENDPOINT_DIRECTION_EPSILON;
  if (position === Position.Bottom) return previous.x === target.x && previous.y > target.y + ENDPOINT_DIRECTION_EPSILON;
  if (position === Position.Left) return previous.y === target.y && previous.x < target.x - ENDPOINT_DIRECTION_EPSILON;
  return previous.y === target.y && previous.x > target.x + ENDPOINT_DIRECTION_EPSILON;
}

/** 清理重复点与共线折点，并拒绝任何对角线段，确保自定义 path 始终是正交轨道。 */
export function normalizeMermaidEdgeRoutePoints(points: ReadonlyArray<MermaidPosition>): MermaidPosition[] {
  if (points.length < 2 || !points.every(isFinitePoint)) return [];
  const unique = points.filter((point, index) => index === 0 || !samePoint(point, points[index - 1]!));
  const normalized: MermaidPosition[] = [];
  for (const point of unique) {
    const previous = normalized.at(-1);
    if (previous && previous.x !== point.x && previous.y !== point.y) return [];
    const beforePrevious = normalized.at(-2);
    if (
      previous &&
      beforePrevious &&
      ((beforePrevious.x === previous.x && previous.x === point.x) ||
        (beforePrevious.y === previous.y && previous.y === point.y))
    ) {
      normalized[normalized.length - 1] = { ...point };
    } else {
      normalized.push({ ...point });
    }
  }
  return normalized.length >= 2 ? normalized : [];
}

/**
 * 把 ELK 节点边界端点重接到 Vue Flow 实际 Handle。坐标校正在节点外侧轨道完成，避免
 * “先到边界、再折返到 Handle”让 markerEnd 沿最后一小段反向；无法证明首尾方向安全时回退。
 */
export function reattachMermaidEdgeRoutePoints(
  storedPoints: ReadonlyArray<MermaidPosition>,
  endpoints: {
    source: MermaidPosition;
    sourcePosition: Position;
    target: MermaidPosition;
    targetPosition: Position;
  }
): MermaidPosition[] {
  const stored = normalizeMermaidEdgeRoutePoints(storedPoints);
  if (stored.length < 3) return [];

  let sourceGuideIndex = -1;
  for (let index = 1; index < stored.length - 1; index += 1) {
    if (isOutsideEndpoint(stored[index]!, endpoints.source, endpoints.sourcePosition)) {
      sourceGuideIndex = index;
      break;
    }
  }
  let targetGuideIndex = -1;
  for (let index = stored.length - 2; index > 0; index -= 1) {
    if (isOutsideEndpoint(stored[index]!, endpoints.target, endpoints.targetPosition)) {
      targetGuideIndex = index;
      break;
    }
  }
  if (sourceGuideIndex < 0 || targetGuideIndex < sourceGuideIndex) return [];

  const corridor = stored.slice(sourceGuideIndex, targetGuideIndex + 1);
  const sourceBridge = endpointBridge(endpoints.source, corridor[0]!, endpoints.sourcePosition);
  const targetBridge = endpointBridge(endpoints.target, corridor.at(-1)!, endpoints.targetPosition);
  const attached = normalizeMermaidEdgeRoutePoints([
    endpoints.source,
    sourceBridge,
    ...corridor,
    targetBridge,
    endpoints.target
  ]);
  return leavesSourceOutward(attached, endpoints.sourcePosition)
    && entersTargetInward(attached, endpoints.targetPosition)
    ? attached
    : [];
}

function moveToward(from: MermaidPosition, to: MermaidPosition, distance: number): MermaidPosition {
  if (from.x === to.x) return { x: from.x, y: from.y + Math.sign(to.y - from.y) * distance };
  return { x: from.x + Math.sign(to.x - from.x) * distance, y: from.y };
}

function formatCoordinate(value: number): string {
  const rounded = Math.round(value * 1000) / 1000;
  return String(Object.is(rounded, -0) ? 0 : rounded);
}

/** 把正交折线转换为小圆角 SVG path；非法或不足两点时返回空字符串供调用方回退。 */
export function buildRoundedOrthogonalPath(points: ReadonlyArray<MermaidPosition>, radius = 6): string {
  const normalized = normalizeMermaidEdgeRoutePoints(points);
  if (normalized.length < 2) return "";
  const commands = [`M${formatCoordinate(normalized[0]!.x)} ${formatCoordinate(normalized[0]!.y)}`];
  for (let index = 1; index < normalized.length - 1; index += 1) {
    const previous = normalized[index - 1]!;
    const corner = normalized[index]!;
    const next = normalized[index + 1]!;
    const incomingLength = Math.abs(previous.x - corner.x) + Math.abs(previous.y - corner.y);
    const outgoingLength = Math.abs(next.x - corner.x) + Math.abs(next.y - corner.y);
    const cornerRadius = Math.min(Math.max(0, radius), incomingLength / 2, outgoingLength / 2);
    const beforeCorner = moveToward(corner, previous, cornerRadius);
    const afterCorner = moveToward(corner, next, cornerRadius);
    commands.push(
      `L${formatCoordinate(beforeCorner.x)} ${formatCoordinate(beforeCorner.y)}`,
      `Q${formatCoordinate(corner.x)} ${formatCoordinate(corner.y)} ${formatCoordinate(afterCorner.x)} ${formatCoordinate(afterCorner.y)}`
    );
  }
  const last = normalized.at(-1)!;
  commands.push(`L${formatCoordinate(last.x)} ${formatCoordinate(last.y)}`);
  return commands.join(" ");
}

/** 沿实际折线路程取中点，避免长绕行边的标签仍落在起终点几何中点。 */
export function getPolylineMidpoint(points: ReadonlyArray<MermaidPosition>): MermaidPosition | undefined {
  const normalized = normalizeMermaidEdgeRoutePoints(points);
  if (normalized.length < 2) return undefined;
  const lengths = normalized.slice(1).map((point, index) => {
    const previous = normalized[index]!;
    return Math.abs(point.x - previous.x) + Math.abs(point.y - previous.y);
  });
  const target = lengths.reduce((sum, length) => sum + length, 0) / 2;
  let travelled = 0;
  for (let index = 0; index < lengths.length; index += 1) {
    const length = lengths[index]!;
    if (travelled + length >= target) {
      const start = normalized[index]!;
      const end = normalized[index + 1]!;
      const remaining = target - travelled;
      if (start.x === end.x) return { x: start.x, y: start.y + Math.sign(end.y - start.y) * remaining };
      return { x: start.x + Math.sign(end.x - start.x) * remaining, y: start.y };
    }
    travelled += length;
  }
  return { ...normalized.at(-1)! };
}
