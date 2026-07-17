import ELK from "elkjs/lib/elk.bundled.js";
import { Position } from "@vue-flow/core";
import {
  cloneMermaidGraph,
  type MermaidEdge,
  type MermaidGraph,
  type MermaidNode,
  type MermaidNodeType,
  type MermaidPosition
} from "./model";
import { getMermaidNodeSize } from "./node-shapes";
import { getMermaidNodePorts, type MermaidNodePort } from "./visual-editor/node-port-layout";

const elk = new ELK();
const CANVAS_OFFSET = { x: 80, y: 70 } as const;

type ElkRouteSection = {
  startPoint: MermaidPosition;
  endPoint: MermaidPosition;
  bendPoints?: MermaidPosition[];
};
type ElkLayoutEdge = { id?: string; sections?: ElkRouteSection[] };

function roundLayoutCoordinate(value: number): number {
  return Math.round(value * 10) / 10;
}

/**
 * 获取节点的物理中心坐标（基于估算出的节点宽高）。
 */
function getNodeCenter(node: MermaidNode): { x: number; y: number } {
  const { width, height } = getMermaidNodeSize(node);
  return {
    x: node.position.x + width / 2,
    y: node.position.y + height / 2,
  };
}

function samePoint(left: MermaidPosition, right: MermaidPosition): boolean {
  return left.x === right.x && left.y === right.y;
}

function pointDistanceSquared(left: MermaidPosition, right: MermaidPosition): number {
  return (left.x - right.x) ** 2 + (left.y - right.y) ** 2;
}

/** 去掉重复点和共线中间点，保证后续圆角 path 不产生零长度拐角。 */
function normalizeOrthogonalPoints(points: MermaidPosition[]): MermaidPosition[] {
  const unique = points.filter((point, index) => index === 0 || !samePoint(point, points[index - 1]!));
  const normalized: MermaidPosition[] = [];
  for (const point of unique) {
    const previous = normalized.at(-1);
    const beforePrevious = normalized.at(-2);
    if (
      previous &&
      beforePrevious &&
      ((beforePrevious.x === previous.x && previous.x === point.x) ||
        (beforePrevious.y === previous.y && previous.y === point.y))
    ) {
      normalized[normalized.length - 1] = point;
    } else {
      normalized.push(point);
    }
  }
  return normalized;
}

function getAbsolutePortPoint(node: MermaidNode, port: MermaidNodePort): MermaidPosition {
  const size = getMermaidNodeSize(node);
  return {
    x: roundLayoutCoordinate(node.position.x + size.width * port.x / 100),
    y: roundLayoutCoordinate(node.position.y + size.height * port.y / 100)
  };
}

function inferEndpointSide(node: MermaidNode, point: MermaidPosition): Position {
  const size = getMermaidNodeSize(node);
  const distances: Array<{ side: Position; distance: number }> = [
    { side: Position.Top, distance: Math.abs(point.y - node.position.y) },
    { side: Position.Right, distance: Math.abs(point.x - (node.position.x + size.width)) },
    { side: Position.Bottom, distance: Math.abs(point.y - (node.position.y + size.height)) },
    { side: Position.Left, distance: Math.abs(point.x - node.position.x) }
  ];
  return distances.reduce((best, candidate) => candidate.distance < best.distance ? candidate : best).side;
}

type EdgeEndpointRequest = {
  edge: MermaidEdge;
  end: "source" | "target";
  node: MermaidNode;
  side: Position;
  elkPoint: MermaidPosition;
};

type EdgeEndpointAssignment = {
  port: MermaidNodePort;
  point: MermaidPosition;
};

function coordinateAlongSide(point: MermaidPosition, side: Position): number {
  return side === Position.Top || side === Position.Bottom ? point.x : point.y;
}

/**
 * 请求数不超过端口数时用顺序保持的最小位移匹配选择端口子集；高出度节点端口不足时
 * 按沿边比例稳定复用。两种路径都保持 ELK 已决定的边顺序，避免端点再次互换造成交叉。
 */
function matchRequestsToPorts(
  requests: EdgeEndpointRequest[],
  ports: MermaidNodePort[]
): Array<{ request: EdgeEndpointRequest; port: MermaidNodePort }> {
  if (!requests.length || !ports.length) return [];
  const side = requests[0]!.side;
  const sortedRequests = [...requests].sort((left, right) => {
    const delta = coordinateAlongSide(left.elkPoint, side) - coordinateAlongSide(right.elkPoint, side);
    return delta || left.edge.id.localeCompare(right.edge.id) || left.end.localeCompare(right.end);
  });
  const sortedPorts = [...ports].sort((left, right) => {
    const leftPoint = getAbsolutePortPoint(sortedRequests[0]!.node, left);
    const rightPoint = getAbsolutePortPoint(sortedRequests[0]!.node, right);
    return coordinateAlongSide(leftPoint, side) - coordinateAlongSide(rightPoint, side);
  });

  if (sortedRequests.length > sortedPorts.length) {
    return sortedRequests.map((request, index) => ({
      request,
      port: sortedPorts[
        sortedRequests.length === 1
          ? 0
          : Math.round(index * (sortedPorts.length - 1) / (sortedRequests.length - 1))
      ]!
    }));
  }

  const requestCount = sortedRequests.length;
  const portCount = sortedPorts.length;
  const costs = Array.from({ length: requestCount + 1 }, () => Array(portCount + 1).fill(Number.POSITIVE_INFINITY));
  const takes = Array.from({ length: requestCount + 1 }, () => Array(portCount + 1).fill(false));
  for (let portIndex = 0; portIndex <= portCount; portIndex += 1) costs[0]![portIndex] = 0;
  for (let requestIndex = 1; requestIndex <= requestCount; requestIndex += 1) {
    for (let portIndex = 1; portIndex <= portCount; portIndex += 1) {
      const skipCost = costs[requestIndex]![portIndex - 1]!;
      const request = sortedRequests[requestIndex - 1]!;
      const portPoint = getAbsolutePortPoint(request.node, sortedPorts[portIndex - 1]!);
      const takeCost = costs[requestIndex - 1]![portIndex - 1]!
        + Math.abs(coordinateAlongSide(request.elkPoint, side) - coordinateAlongSide(portPoint, side));
      if (takeCost <= skipCost) {
        costs[requestIndex]![portIndex] = takeCost;
        takes[requestIndex]![portIndex] = true;
      } else {
        costs[requestIndex]![portIndex] = skipCost;
      }
    }
  }

  const matches: Array<{ request: EdgeEndpointRequest; port: MermaidNodePort }> = [];
  let requestIndex = requestCount;
  let portIndex = portCount;
  while (requestIndex > 0 && portIndex > 0) {
    if (takes[requestIndex]![portIndex]) {
      matches.push({
        request: sortedRequests[requestIndex - 1]!,
        port: sortedPorts[portIndex - 1]!
      });
      requestIndex -= 1;
    }
    portIndex -= 1;
  }
  return matches.reverse();
}

function endpointKey(edge: MermaidEdge, end: "source" | "target"): string {
  return `${edge.id}\u0000${end}`;
}

function buildPortAdapter(
  assignment: EdgeEndpointAssignment,
  elkPoint: MermaidPosition
): MermaidPosition {
  return assignment.port.position === Position.Top || assignment.port.position === Position.Bottom
    ? { x: assignment.point.x, y: elkPoint.y }
    : { x: elkPoint.x, y: assignment.point.y };
}

/** 把 ELK 的端点顺序映射到真实 Handle，并把完整正交 section 写入领域边。 */
function applyElkEdgeRoutes(graph: MermaidGraph, layoutEdges: ElkLayoutEdge[]): void {
  const edgeById = new Map(graph.edges.map((edge) => [edge.id, edge]));
  const nodeById = new Map(graph.nodes.map((node) => [node.id, node]));
  const routeByEdgeId = new Map<string, MermaidPosition[]>();
  const requestGroups = new Map<string, EdgeEndpointRequest[]>();

  const addRequest = (request: EdgeEndpointRequest) => {
    const key = `${request.node.id}\u0000${request.side}`;
    const group = requestGroups.get(key) ?? [];
    group.push(request);
    requestGroups.set(key, group);
  };

  for (const layoutEdge of layoutEdges) {
    if (!layoutEdge.id) continue;
    const edge = edgeById.get(layoutEdge.id);
    const section = layoutEdge.sections?.[0];
    if (!edge || !section) continue;
    const rawPoints = normalizeOrthogonalPoints([
      section.startPoint,
      ...(section.bendPoints ?? []),
      section.endPoint
    ].map((point) => ({
      x: roundLayoutCoordinate(point.x + CANVAS_OFFSET.x),
      y: roundLayoutCoordinate(point.y + CANVAS_OFFSET.y)
    })));
    if (rawPoints.length < 2) continue;
    const sourceNode = nodeById.get(edge.source);
    const targetNode = nodeById.get(edge.target);
    if (!sourceNode || !targetNode) continue;
    routeByEdgeId.set(edge.id, rawPoints);
    addRequest({
      edge,
      end: "source",
      node: sourceNode,
      side: inferEndpointSide(sourceNode, rawPoints[0]!),
      elkPoint: rawPoints[0]!
    });
    addRequest({
      edge,
      end: "target",
      node: targetNode,
      side: inferEndpointSide(targetNode, rawPoints.at(-1)!),
      elkPoint: rawPoints.at(-1)!
    });
  }

  const assignments = new Map<string, EdgeEndpointAssignment>();
  for (const requests of requestGroups.values()) {
    const first = requests[0];
    if (!first) continue;
    const ports = getMermaidNodePorts(first.node.type).filter((port) => port.position === first.side);
    for (const { request, port } of matchRequestsToPorts(requests, ports)) {
      const assignment = { port, point: getAbsolutePortPoint(request.node, port) };
      assignments.set(endpointKey(request.edge, request.end), assignment);
      if (request.end === "source") request.edge.sourceHandle = port.handleId;
      else request.edge.targetHandle = port.handleId;
    }
  }

  // 同一侧只有一个可用端口时，ELK 会让自环两端复用它；目标端改取整个轮廓上
  // 距离 ELK 终点最近的其他端口，保持编辑器不允许“同节点同端口自环”的不变量。
  for (const edge of graph.edges) {
    if (edge.source !== edge.target || edge.sourceHandle !== edge.targetHandle) continue;
    const node = nodeById.get(edge.source);
    const rawEnd = routeByEdgeId.get(edge.id)?.at(-1);
    const sourceAssignment = assignments.get(endpointKey(edge, "source"));
    if (!node || !rawEnd || !sourceAssignment) continue;
    const alternatePort = getMermaidNodePorts(node.type)
      .filter((port) => port.handleId !== sourceAssignment.port.handleId)
      .reduce<MermaidNodePort | undefined>((nearest, port) => {
        if (!nearest) return port;
        return pointDistanceSquared(getAbsolutePortPoint(node, port), rawEnd)
          < pointDistanceSquared(getAbsolutePortPoint(node, nearest), rawEnd)
          ? port
          : nearest;
      }, undefined);
    if (!alternatePort) continue;
    const targetAssignment = { port: alternatePort, point: getAbsolutePortPoint(node, alternatePort) };
    assignments.set(endpointKey(edge, "target"), targetAssignment);
    edge.targetHandle = alternatePort.handleId;
  }

  for (const edge of graph.edges) {
    const rawPoints = routeByEdgeId.get(edge.id);
    const sourceAssignment = assignments.get(endpointKey(edge, "source"));
    const targetAssignment = assignments.get(endpointKey(edge, "target"));
    if (!rawPoints || !sourceAssignment || !targetAssignment) continue;
    const rawStart = rawPoints[0]!;
    const rawEnd = rawPoints.at(-1)!;
    edge.route = {
      points: normalizeOrthogonalPoints([
        sourceAssignment.point,
        buildPortAdapter(sourceAssignment, rawStart),
        ...rawPoints,
        buildPortAdapter(targetAssignment, rawEnd),
        targetAssignment.point
      ])
    };
  }
}

/**
 * 环形极角对齐的端口分配算法。
 * 将出边/入边根据相对几何极角排序，将可用端口也按极角排序。
 * 引入业务偏好惩罚项：一般节点中点距离越近惩罚越小（优先连中点）；判断节点 4 个顶点惩罚为 0，其它斜边端口惩罚较大（优先连 4 个顶点）。
 * 通过最小偏差角与偏好惩罚之和最优来完成分配。
 */
function allocatePorts(
  edges: MermaidEdge[],
  availablePorts: MermaidNodePort[],
  getNeighborCenter: (edge: MermaidEdge) => { x: number; y: number } | null,
  nodeCenter: { x: number; y: number },
  nodeType: MermaidNodeType,
  applyHandle: (edge: MermaidEdge, handleId: string) => void
): void {
  if (edges.length === 0 || availablePorts.length === 0) return;

  // 1. 计算各连线极角并排序
  const edgesWithAngle = edges
    .map((edge) => {
      const neighborCenter = getNeighborCenter(edge);
      if (!neighborCenter) return null;
      const angle = Math.atan2(neighborCenter.y - nodeCenter.y, neighborCenter.x - nodeCenter.x);
      return { edge, angle };
    })
    .filter((item): item is { edge: MermaidEdge; angle: number } => item !== null);

  if (edgesWithAngle.length === 0) return;

  edgesWithAngle.sort((a, b) => a.angle - b.angle);

  // 2. 计算各可用端口相对节点中心 (50, 50) 的极角并排序
  const portsWithAngle = availablePorts.map((port) => {
    const angle = Math.atan2(port.y - 50, port.x - 50);
    return { port, angle };
  });
  portsWithAngle.sort((a, b) => a.angle - b.angle);

  const N = edgesWithAngle.length;
  const M = portsWithAngle.length;

  let bestK = 0;
  let minCost = Infinity;
  const step = M >= N ? Math.max(1, Math.floor(M / N)) : 1;

  const angleDiff = (a: number, b: number) => {
    const diff = Math.abs(a - b);
    return Math.min(diff, 2 * Math.PI - diff);
  };

  // 偏好惩罚项：决定连接点推荐顺序
  const getPortPenalty = (port: MermaidNodePort) => {
    const isVerticalEdge = port.position === Position.Top || port.position === Position.Bottom;
    const distFromCenter = isVerticalEdge ? Math.abs(port.x - 50) : Math.abs(port.y - 50);

    if (nodeType === "diamond") {
      // 判断节点：优先连四个顶点（即距离中心为 0 的主端口），其它斜边端口给予极大惩罚，确保优先连顶点
      return distFromCenter === 0 ? 0 : 10;
    } else {
      // 一般节点：优先连接到边的中间。让偏好惩罚足够大，从而抑制因连线微小倾斜而拉到角端口的现象。
      // 例如使用 distFromCenter / 10，则中点附近惩罚为 0，而偏离 16.7% 时惩罚 1.67，偏离 50%（角点）时惩罚 5.0（大于最大偏角 pi）
      return distFromCenter / 10;
    }
  };

  // 3. 枚举环形排列的起点，寻找最优几何与偏好对齐
  for (let k = 0; k < M; k++) {
    let cost = 0;
    for (let i = 0; i < N; i++) {
      const edgeAngle = edgesWithAngle[i]!.angle;
      const port = portsWithAngle[(k + i * step) % M]!.port;
      const portAngle = portsWithAngle[(k + i * step) % M]!.angle;
      cost += angleDiff(edgeAngle, portAngle) + getPortPenalty(port);
    }
    if (cost < minCost) {
      minCost = cost;
      bestK = k;
    }
  }

  // 4. 应用最佳分配的句柄 ID
  for (let i = 0; i < N; i++) {
    const edge = edgesWithAngle[i]!.edge;
    const port = portsWithAngle[(bestK + i * step) % M]!.port;
    applyHandle(edge, port.handleId);
  }
}

/**
 * 重新分布整个图的连线端口，以解决自动布局后旧端点固定带来的交叉问题。
 */
function redistributeEdgePorts(graph: MermaidGraph): void {
  const nodeMap = new Map<string, MermaidNode>();
  for (const n of graph.nodes) {
    nodeMap.set(n.id, n);
  }

  for (const n of graph.nodes) {
    const nodeCenter = getNodeCenter(n);
    const ports = getMermaidNodePorts(n.type);

    // 1. 分配当前节点的所有出边 (sourceHandle)
    const outEdges = graph.edges.filter((e) => e.source === n.id);
    const sourcePorts = ports.filter((p) => p.handleId.startsWith("source-"));
    allocatePorts(
      outEdges,
      sourcePorts,
      (edge) => {
        const targetNode = nodeMap.get(edge.target);
        return targetNode ? getNodeCenter(targetNode) : null;
      },
      nodeCenter,
      n.type,
      (edge, handleId) => {
        edge.sourceHandle = handleId;
      }
    );

    // 2. 分配当前节点的所有入边 (targetHandle)
    const inEdges = graph.edges.filter((e) => e.target === n.id);
    const targetPorts = ports.filter((p) => p.handleId.startsWith("target-"));
    allocatePorts(
      inEdges,
      targetPorts,
      (edge) => {
        const sourceNode = nodeMap.get(edge.source);
        return sourceNode ? getNodeCenter(sourceNode) : null;
      },
      nodeCenter,
      n.type,
      (edge, handleId) => {
        edge.targetHandle = handleId;
      }
    );
  }
}

/**
 * 基于 ELK (Eclipse Layout Kernel) Layered 算法对 Mermaid flowchart 节点进行自动布局。
 * 通过 elkjs 计算出最少交叉的层级坐标，然后写回节点的 position 中并重新分配边端口。
 * 
 * @param graph 待布局的 MermaidGraph 原始模型
 * @returns 布局完成后的新 MermaidGraph 实例（不修改原始输入）
 */
export async function autoLayoutMermaidGraph(graph: MermaidGraph): Promise<MermaidGraph> {
  const result = cloneMermaidGraph(graph);
  // 新一轮布局必须从无派生路径状态开始；ELK 缺 section 或失败时由渲染层安全回退 SmoothStep。
  for (const edge of result.edges) delete edge.route;
  if (result.nodes.length === 0) return result;

  // Mermaid 布局方向转换为 ELK 方向（TD/TB -> DOWN, BT -> UP, LR -> RIGHT, RL -> LEFT）
  let elkDirection = "DOWN";
  if (graph.direction === "TD" || graph.direction === "TB") {
    elkDirection = "DOWN";
  } else if (graph.direction === "BT") {
    elkDirection = "UP";
  } else if (graph.direction === "LR") {
    elkDirection = "RIGHT";
  } else if (graph.direction === "RL") {
    elkDirection = "LEFT";
  }

  // 构造 ELK 节点时使用与实际 Vue 节点一致的包围盒，边路由才能可靠避障。
  const children = result.nodes.map((node) => {
    const { width, height } = getMermaidNodeSize(node);

    return {
      id: node.id,
      width,
      height,
    };
  });

  // 构造 ELK 的边列表
  const edges = result.edges.map((edge) => ({
    id: edge.id,
    sources: [edge.source],
    targets: [edge.target],
  }));

  // ELK 布局图输入参数配置
  const elkGraph = {
    id: "root",
    layoutOptions: {
      "elk.algorithm": "layered",
      "elk.direction": elkDirection,
      "elk.edgeRouting": "ORTHOGONAL",
      "elk.layered.crossingMinimization.strategy": "LAYER_SWEEP",
      "elk.layered.nodePlacement.strategy": "BRANDES_KOEPF",
      "elk.layered.nodePlacement.favorStraightEdges": "true",
      "elk.layered.unnecessaryBendpoints": "true",
      "elk.layered.spacing.nodeNodeBetweenLayers": "96",
      "elk.layered.spacing.edgeNodeBetweenLayers": "28",
      "elk.layered.spacing.edgeEdgeBetweenLayers": "16",
      "elk.spacing.nodeNode": "64"
    },
    children,
    edges,
  };

  try {
    const layouted = await elk.layout(elkGraph);
    const posMap = new Map<string, { x: number; y: number }>();
    if (layouted.children) {
      for (const child of layouted.children) {
        posMap.set(child.id, { x: child.x ?? 0, y: child.y ?? 0 });
      }
    }

    // 更新节点坐标，并加偏移量防止节点贴在画布最顶/最左侧
    for (const node of result.nodes) {
      const pos = posMap.get(node.id);
      if (pos) {
        node.position = { x: CANVAS_OFFSET.x + pos.x, y: CANVAS_OFFSET.y + pos.y };
      }
    }
    applyElkEdgeRoutes(result, (layouted.edges ?? []) as ElkLayoutEdge[]);
  } catch (err) {
    console.error("ELK layout execution failed, keeping original layout:", err);
    redistributeEdgePorts(result);
  }

  return result;
}

/**
 * 同步的自动布局实现，基于有向图层级 + Sugiyama 重心法（barycenter）减少边交叉。
 * 用于在异步 ELK 布局计算完成前作为渐进渲染的初版快速布局，以及支持同步单元测试的挂载。
 * 
 * @param graph 待布局 of MermaidGraph 原始模型
 * @returns 布局完成后的新 MermaidGraph 实例（不修改原始输入）
 */
export function syncAutoLayoutMermaidGraph(graph: MermaidGraph): MermaidGraph {
  const result = cloneMermaidGraph(graph);
  for (const edge of result.edges) delete edge.route;
  if (result.nodes.length === 0) return result;
  const nodeOrder = new Map(result.nodes.map((node, index) => [node.id, index]));
  const indegree = new Map(result.nodes.map((node) => [node.id, 0]));
  const outgoing = new Map(result.nodes.map((node) => [node.id, [] as string[]]));
  for (const edge of result.edges) {
    if (!indegree.has(edge.source) || !indegree.has(edge.target)) continue;
    indegree.set(edge.target, (indegree.get(edge.target) ?? 0) + 1);
    outgoing.get(edge.source)?.push(edge.target);
  }

  // 1. 层分配（BFS 拓扑排序，循环节点归零）
  const levels = new Map(result.nodes.map((node) => [node.id, 0]));
  const queue = result.nodes.filter((node) => indegree.get(node.id) === 0).map((node) => node.id);
  for (let cursor = 0; cursor < queue.length; cursor += 1) {
    const source = queue[cursor];
    if (!source) continue;
    for (const target of outgoing.get(source) ?? []) {
      levels.set(target, Math.max(levels.get(target) ?? 0, (levels.get(source) ?? 0) + 1));
      indegree.set(target, (indegree.get(target) ?? 1) - 1);
      if (indegree.get(target) === 0) queue.push(target);
    }
  }

  // 2. 按层分组
  const groups = new Map<number, string[]>();
  for (const node of result.nodes) {
    const level = levels.get(node.id) ?? 0;
    const group = groups.get(level) ?? [];
    group.push(node.id);
    groups.set(level, group);
  }
  const maxLevel = Math.max(0, ...groups.keys());

  // 3. Sugiyama 重心法减少边交叉
  const allLevels = Array.from(groups.keys()).sort((a, b) => a - b);

  function computeBarycenter(nodeId: string, levelIdx: number, direction: -1 | 1): number {
    const neighborLevel = groups.get(levelIdx + direction);
    if (!neighborLevel) return -1;
    const neighbors =
      direction === 1
        ? result.edges.filter((e) => e.source === nodeId).map((e) => e.target)
        : result.edges.filter((e) => e.target === nodeId).map((e) => e.source);
    const inNeighborLevel = neighbors.filter((id) => neighborLevel.includes(id));
    if (inNeighborLevel.length === 0) return -1;
    return inNeighborLevel.reduce((sum, id) => sum + neighborLevel.indexOf(id), 0) / inNeighborLevel.length;
  }

  function sortGroupByBarycenter(nodes: string[], direction: -1 | 1, levelIdx: number) {
    nodes.sort((a, b) => {
      const ba = computeBarycenter(a, levelIdx, direction);
      const bb = computeBarycenter(b, levelIdx, direction);
      if (ba >= 0 && bb >= 0 && ba !== bb) return ba - bb;
      // 无邻居的放后面，有邻居的先排
      if (ba >= 0 && bb < 0) return -1;
      if (ba < 0 && bb >= 0) return 1;
      return (nodeOrder.get(a) ?? 0) - (nodeOrder.get(b) ?? 0);
    });
  }

  // 前向：下层的节点对齐其前驱（上游）
  for (let i = 1; i < allLevels.length; i++) {
    const nodes = groups.get(allLevels[i]);
    if (nodes) sortGroupByBarycenter(nodes, -1, allLevels[i]);
  }
  // 后向：上层的节点对齐其后继（下游）
  for (let i = allLevels.length - 2; i >= 0; i--) {
    const nodes = groups.get(allLevels[i]);
    if (nodes) sortGroupByBarycenter(nodes, 1, allLevels[i]);
  }

  // 4. 计算版面坐标
  for (const node of result.nodes) {
    const level = levels.get(node.id) ?? 0;
    const siblingIndex = groups.get(level)?.indexOf(node.id) ?? 0;
    const displayLevel = graph.direction === "RL" || graph.direction === "BT" ? maxLevel - level : level;
    if (graph.direction === "LR" || graph.direction === "RL") {
      node.position = { x: 80 + displayLevel * 220, y: 70 + siblingIndex * 140 };
    } else {
      node.position = { x: 80 + siblingIndex * 220, y: 70 + displayLevel * 140 };
    }
  }

  // 重新分布各节点的边连接，防交叉
  redistributeEdgePorts(result);

  return result;
}
