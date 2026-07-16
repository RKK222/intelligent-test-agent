import { Position } from "@vue-flow/core";
import type { MermaidNodeType } from "../model";

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

/** 矩形：4 个顶点 + 每条边 2 个点，共 12 个端口。 */
const RECTANGLE_PORTS: RawPort[] = [
  { x: 0, y: 0, pos: Position.Top },
  { x: 100, y: 0, pos: Position.Top },
  { x: 0, y: 100, pos: Position.Bottom },
  { x: 100, y: 100, pos: Position.Bottom },
  { x: 33.3, y: 0, pos: Position.Top },
  { x: 66.7, y: 0, pos: Position.Top },
  { x: 33.3, y: 100, pos: Position.Bottom },
  { x: 66.7, y: 100, pos: Position.Bottom },
  { x: 0, y: 33.3, pos: Position.Left },
  { x: 0, y: 66.7, pos: Position.Left },
  { x: 100, y: 33.3, pos: Position.Right },
  { x: 100, y: 66.7, pos: Position.Right }
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

const RAW_PORTS: Record<MermaidNodeType, RawPort[]> = {
  diamond: DIAMOND_PORTS,
  circle: CIRCLE_PORTS,
  rectangle: RECTANGLE_PORTS,
  rounded: ROUNDED_PORTS,
  stadium: ROUNDED_PORTS
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
