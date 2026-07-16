import ELK from "elkjs";
import { Position } from "@vue-flow/core";
import { cloneMermaidGraph, type MermaidGraph, type MermaidNode, type MermaidNodeType } from "./model";
import { getMermaidNodePorts, type MermaidNodePort } from "./visual-editor/node-port-layout";

const elk = new ELK();

/**
 * 获取节点的物理中心坐标（基于估算出的节点宽高）。
 */
function getNodeCenter(node: MermaidNode): { x: number; y: number } {
  let width = 150;
  let height = 52;
  if (node.type === "circle") {
    width = 92;
    height = 92;
  } else {
    const textLen = node.text ? node.text.length : 0;
    width = Math.max(120, Math.min(190, textLen * 12 + 40));
    height = 52;
  }
  return {
    x: node.position.x + width / 2,
    y: node.position.y + height / 2,
  };
}

/**
 * 环形极角对齐的端口分配算法。
 * 将出边/入边根据相对几何极角排序，将可用端口也按极角排序。
 * 引入业务偏好惩罚项：一般节点中点距离越近惩罚越小（优先连中点）；判断节点 4 个顶点惩罚为 0，其它斜边端口惩罚较大（优先连 4 个顶点）。
 * 通过最小偏差角与偏好惩罚之和最优来完成分配。
 */
function allocatePorts(
  edges: typeof graph.edges,
  availablePorts: MermaidNodePort[],
  getNeighborCenter: (edge: typeof graph.edges[0]) => { x: number; y: number } | null,
  nodeCenter: { x: number; y: number },
  nodeType: MermaidNodeType,
  applyHandle: (edge: typeof graph.edges[0], handleId: string) => void
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
    .filter((item): item is { edge: typeof graph.edges[0]; angle: number } => item !== null);

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
      // 判断节点：优先连四个顶点（即距离中心为 0 的主端口），其它斜边端口给予中等度数惩罚（0.5 弧度）
      return distFromCenter === 0 ? 0 : 0.5;
    } else {
      // 一般节点：优先连接到边的中间。离中点越近，惩罚值越低。
      return distFromCenter / 150;
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

  // 构造 ELK 的节点列表并估算宽高
  const children = result.nodes.map((node) => {
    let width = 150;
    let height = 52;
    if (node.type === "circle") {
      width = 92;
      height = 92;
    } else {
      // 按照文本长度计算合适宽度，避免文字溢出或节点过窄
      const textLen = node.text ? node.text.length : 0;
      width = Math.max(120, Math.min(190, textLen * 12 + 40));
      height = 52;
    }

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
      // 布局间距优化
      "elk.layered.spacing.nodeNodeBetweenLayers": "80", // 层与层间距
      "elk.spacing.nodeNode": "60", // 同层节点间距
      // 节点放置算法，BRANDES_KOEPF 能生成更对称和居中的布局
      "elk.layered.nodePlacement.strategy": "BRANDES_KOEPF",
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
        node.position = { x: 80 + pos.x, y: 70 + pos.y };
      }
    }
  } catch (err) {
    console.error("ELK layout execution failed, keeping original layout:", err);
  }

  // 重新分布各节点的边连接，防交叉
  redistributeEdgePorts(result);

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
