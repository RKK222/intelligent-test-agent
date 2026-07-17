import { Position } from "@vue-flow/core";
import type { MermaidGraph, MermaidNodeType } from "../model";

/**
 * 可视化节点的连接点布局。`x`/`y` 为相对节点尺寸的百分比，`position` 是 Vue Flow
 * 用来决定连线出入方向的边。句柄 ID 沿用 `target-${k}`/`source-${k}` 约定，`k` 按端口
 * 索引成对递增（偶数索引为 target、奇数索引为 source），与 MermaidFlowNode 渲染顺序
 * 保持一致，确保序列化与可视化端口一一对应。
 */
export type MermaidNodePort = {
  handleId: string;
  position: Position;
  x: number;
  y: number;
};

type RawPort = { x: number; y: number; pos: Position };

/** 菱形/判断：4 个顶点 + 每条斜边 2 个点，共 12 个端口。 */
const DIAMOND_PORTS: RawPort[] = [
  { x: 50, y: 0, pos: Position.Top },
  { x: 100, y: 50, pos: Position.Right },
  { x: 50, y: 100, pos: Position.Bottom },
  { x: 0, y: 50, pos: Position.Left },
  { x: 16.7, y: 33.3, pos: Position.Top },
  { x: 33.3, y: 16.7, pos: Position.Top },
  { x: 83.3, y: 33.3, pos: Position.Top },
  { x: 66.7, y: 16.7, pos: Position.Top },
  { x: 16.7, y: 66.7, pos: Position.Bottom },
  { x: 33.3, y: 83.3, pos: Position.Bottom },
  { x: 83.3, y: 66.7, pos: Position.Bottom },
  { x: 66.7, y: 83.3, pos: Position.Bottom }
];

/** 圆形：均匀分布 8 个端口。 */
const CIRCLE_PORTS: RawPort[] = [
  { x: 100, y: 50, pos: Position.Right },
  { x: 85.4, y: 85.4, pos: Position.Bottom },
  { x: 50, y: 100, pos: Position.Bottom },
  { x: 14.6, y: 85.4, pos: Position.Bottom },
  { x: 0, y: 50, pos: Position.Left },
  { x: 14.6, y: 14.6, pos: Position.Top },
  { x: 50, y: 0, pos: Position.Top },
  { x: 85.4, y: 14.6, pos: Position.Top }
];

/** 矩形：算上顶点，长边上 5 个点，短边上 3 个点，每边中心均有一个点，共 12 个端口。 */
const RECTANGLE_PORTS: RawPort[] = [
  // 4 corners to maintain maximum compatibility with original layout
  { x: 0, y: 0, pos: Position.Top },
  { x: 100, y: 0, pos: Position.Top },
  { x: 0, y: 100, pos: Position.Bottom },
  { x: 100, y: 100, pos: Position.Bottom },

  // Top edge non-corners: x: 25, 50, 75
  { x: 25, y: 0, pos: Position.Top },
  { x: 50, y: 0, pos: Position.Top },
  { x: 75, y: 0, pos: Position.Top },

  // Bottom edge non-corners: x: 25, 50, 75
  { x: 25, y: 100, pos: Position.Bottom },
  { x: 50, y: 100, pos: Position.Bottom },
  { x: 75, y: 100, pos: Position.Bottom },

  // Left edge midpoint: y: 50
  { x: 0, y: 50, pos: Position.Left },

  // Right edge midpoint: y: 50
  { x: 100, y: 50, pos: Position.Right }
];

/**
 * 圆角/胶囊：左右各 1 个、上下各 3 个，共 8 个端口。
 * 外侧两点用 35%/65% 而非 25%/75%，确保落在直线边上而非弯角弧内。
 */
const ROUNDED_PORTS: RawPort[] = [
  { x: 0, y: 50, pos: Position.Left },
  { x: 100, y: 50, pos: Position.Right },
  { x: 35, y: 0, pos: Position.Top },
  { x: 50, y: 0, pos: Position.Top },
  { x: 65, y: 0, pos: Position.Top },
  { x: 35, y: 100, pos: Position.Bottom },
  { x: 50, y: 100, pos: Position.Bottom },
  { x: 65, y: 100, pos: Position.Bottom }
];

/** 数据库圆柱：上下中心各 1 个，左右曲面各 3 个，共 8 个端口。 */
const DATABASE_PORTS: RawPort[] = [
  { x: 50, y: 0, pos: Position.Top },
  { x: 100, y: 25, pos: Position.Right },
  { x: 100, y: 50, pos: Position.Right },
  { x: 100, y: 75, pos: Position.Right },
  { x: 50, y: 100, pos: Position.Bottom },
  { x: 0, y: 75, pos: Position.Left },
  { x: 0, y: 50, pos: Position.Left },
  { x: 0, y: 25, pos: Position.Left }
];

/** 六边形：6 个顶点 + 6 条边中点，共 12 个端口。 */
const HEXAGON_PORTS: RawPort[] = [
  { x: 20, y: 0, pos: Position.Top },
  { x: 80, y: 0, pos: Position.Top },
  { x: 100, y: 50, pos: Position.Right },
  { x: 80, y: 100, pos: Position.Bottom },
  { x: 20, y: 100, pos: Position.Bottom },
  { x: 0, y: 50, pos: Position.Left },
  { x: 50, y: 0, pos: Position.Top },
  { x: 90, y: 25, pos: Position.Right },
  { x: 90, y: 75, pos: Position.Right },
  { x: 50, y: 100, pos: Position.Bottom },
  { x: 10, y: 75, pos: Position.Left },
  { x: 10, y: 25, pos: Position.Left }
];

/** 输入输出平行四边形：4 个顶点 + 每条边 2 个点，共 12 个端口。 */
const PARALLELOGRAM_PORTS: RawPort[] = [
  { x: 20, y: 0, pos: Position.Top },
  { x: 100, y: 0, pos: Position.Top },
  { x: 0, y: 100, pos: Position.Bottom },
  { x: 80, y: 100, pos: Position.Bottom },
  { x: 46.7, y: 0, pos: Position.Top },
  { x: 73.3, y: 0, pos: Position.Top },
  { x: 26.7, y: 100, pos: Position.Bottom },
  { x: 53.3, y: 100, pos: Position.Bottom },
  { x: 13.3, y: 33.3, pos: Position.Left },
  { x: 6.7, y: 66.7, pos: Position.Left },
  { x: 93.3, y: 33.3, pos: Position.Right },
  { x: 86.7, y: 66.7, pos: Position.Right }
];

/** 人工处理梯形：4 个顶点 + 每条边 2 个点，共 12 个端口。 */
const TRAPEZOID_PORTS: RawPort[] = [
  { x: 20, y: 0, pos: Position.Top },
  { x: 80, y: 0, pos: Position.Top },
  { x: 0, y: 100, pos: Position.Bottom },
  { x: 100, y: 100, pos: Position.Bottom },
  { x: 40, y: 0, pos: Position.Top },
  { x: 60, y: 0, pos: Position.Top },
  { x: 33.3, y: 100, pos: Position.Bottom },
  { x: 66.7, y: 100, pos: Position.Bottom },
  { x: 13.3, y: 33.3, pos: Position.Left },
  { x: 6.7, y: 66.7, pos: Position.Left },
  { x: 86.7, y: 33.3, pos: Position.Right },
  { x: 93.3, y: 66.7, pos: Position.Right }
];

/** 无边框文本按隐形包围盒布点，四角与四边中点共 8 个。 */
const TEXT_PORTS: RawPort[] = [
  { x: 0, y: 0, pos: Position.Top },
  { x: 50, y: 0, pos: Position.Top },
  { x: 100, y: 0, pos: Position.Top },
  { x: 100, y: 50, pos: Position.Right },
  { x: 100, y: 100, pos: Position.Bottom },
  { x: 50, y: 100, pos: Position.Bottom },
  { x: 0, y: 100, pos: Position.Bottom },
  { x: 0, y: 50, pos: Position.Left }
];

/** 文档端口落在正面文档外轮廓；多文档的后层仅作为视觉堆叠，不承载连线。 */
const DOCUMENT_PORTS: RawPort[] = [
  { x: 0, y: 0, pos: Position.Top },
  { x: 50, y: 0, pos: Position.Top },
  { x: 100, y: 0, pos: Position.Top },
  { x: 100, y: 50, pos: Position.Right },
  { x: 85, y: 77, pos: Position.Bottom },
  { x: 50, y: 100, pos: Position.Bottom },
  { x: 15, y: 77, pos: Position.Bottom },
  { x: 0, y: 50, pos: Position.Left }
];

/** 多文档连接到最前层页面，顶部端口随前层下移，避免落在后层装饰轮廓上。 */
const DOCUMENTS_PORTS: RawPort[] = [
  { x: 0, y: 15, pos: Position.Top },
  { x: 50, y: 15, pos: Position.Top },
  { x: 100, y: 15, pos: Position.Top },
  { x: 100, y: 50, pos: Position.Right },
  { x: 85, y: 77, pos: Position.Bottom },
  { x: 50, y: 100, pos: Position.Bottom },
  { x: 15, y: 77, pos: Position.Bottom },
  { x: 0, y: 50, pos: Position.Left }
];

const RAW_PORTS: Record<MermaidNodeType, RawPort[]> = {
  diamond: DIAMOND_PORTS,
  circle: CIRCLE_PORTS,
  rectangle: RECTANGLE_PORTS,
  rounded: ROUNDED_PORTS,
  stadium: ROUNDED_PORTS,
  subroutine: RECTANGLE_PORTS,
  database: DATABASE_PORTS,
  hexagon: HEXAGON_PORTS,
  parallelogram: PARALLELOGRAM_PORTS,
  trapezoid: TRAPEZOID_PORTS,
  "double-circle": CIRCLE_PORTS,
  text: TEXT_PORTS,
  doc: DOCUMENT_PORTS,
  docs: DOCUMENTS_PORTS
};

/** 返回某类节点的全部端口，顺序与句柄 ID 与 MermaidFlowNode 渲染完全一致。 */
export function getMermaidNodePorts(nodeType: MermaidNodeType): MermaidNodePort[] {
  const raw = RAW_PORTS[nodeType] ?? RAW_PORTS.rectangle;
  return raw.map((port, index) => {
    const k = Math.floor(index / 2);
    const handleId = index % 2 === 0 ? `target-${k}` : `source-${k}`;
    return { handleId, position: port.pos, x: port.x, y: port.y };
  });
}

/** 返回与给定方向相反的边，用于在新节点上选择朝向起点的端口。 */
export function oppositePosition(position: Position): Position {
  if (position === Position.Left) return Position.Right;
  if (position === Position.Right) return Position.Left;
  if (position === Position.Top) return Position.Bottom;
  return Position.Top;
}

/**
 * 在指定边上选择最接近该边中点的端口：顶/底边按 x 接近 50%，左/右边按 y 接近 50%。
 * 平局时保留端口数组顺序，保证选择稳定。这样快捷建连的起始点始终落在箭头所在边上，
 * 而不是退化到某个角落端口。
 */
export function findEdgePort(nodeType: MermaidNodeType, edge: Position): MermaidNodePort | undefined {
  const ports = getMermaidNodePorts(nodeType).filter((port) => port.position === edge);
  if (ports.length === 0) return undefined;
  const alongEdgeCenter = (port: MermaidNodePort) =>
    edge === Position.Top || edge === Position.Bottom ? Math.abs(port.x - 50) : Math.abs(port.y - 50);
  return ports.reduce((best, port) =>
    alongEdgeCenter(port) < alongEdgeCenter(best) ? port : best
  );
}

function distanceSquared(left: MermaidNodePort, right: MermaidNodePort): number {
  return (left.x - right.x) ** 2 + (left.y - right.y) ** 2;
}

/** 在整个新轮廓寻找相对坐标最近的端口；自环迁移目标端时排除已占用的起点端口。 */
function findNearestPort(
  previous: MermaidNodePort,
  candidates: MermaidNodePort[],
  excludedHandleId?: string
): MermaidNodePort | undefined {
  const available = candidates.filter((candidate) => candidate.handleId !== excludedHandleId);
  return available.reduce<MermaidNodePort | undefined>((nearest, candidate) =>
    !nearest || distanceSquared(previous, candidate) < distanceSquared(previous, nearest)
      ? candidate
      : nearest
  , undefined);
}

/**
 * 节点换形时把固定端口迁移到新轮廓的最近位置。自环先迁移 source，再为 target 排除
 * 已占端口；旧 metadata 若引用不存在的端口则删除该端固定值，让适配层安全回退。
 */
export function remapMermaidNodeEdgePorts(
  graph: MermaidGraph,
  nodeId: string,
  previousType: MermaidNodeType,
  nextType: MermaidNodeType
): void {
  if (previousType === nextType) return;
  const previousPorts = getMermaidNodePorts(previousType);
  const nextPorts = getMermaidNodePorts(nextType);
  const remapHandle = (handleId: string | undefined, excludedHandleId?: string) => {
    if (!handleId) return undefined;
    const previous = previousPorts.find((port) => port.handleId === handleId);
    return previous ? findNearestPort(previous, nextPorts, excludedHandleId)?.handleId : undefined;
  };

  for (const edge of graph.edges) {
    if (edge.source === nodeId) {
      const nextSourceHandle = remapHandle(edge.sourceHandle);
      if (nextSourceHandle) edge.sourceHandle = nextSourceHandle;
      else delete edge.sourceHandle;
    }
    if (edge.target === nodeId) {
      const excluded = edge.source === nodeId ? edge.sourceHandle : undefined;
      const nextTargetHandle = remapHandle(edge.targetHandle, excluded);
      if (nextTargetHandle) edge.targetHandle = nextTargetHandle;
      else delete edge.targetHandle;
    }
  }
}
