import { cloneMermaidGraph, type MermaidGraph } from "./model";

/**
 * 基于有向图层级 + Sugiyama 重心法（barycenter）减少边交叉。
 * 前驱重心（forward pass）让每个节点对齐其上游节点，后继重心（backward pass）
 * 让上游节点对齐其下游节点，从而显著减少从起点到终点的连线交叉。
 * 环路节点回退到首层，确保任何合法模型都可编辑。
 */
export function autoLayoutMermaidGraph(graph: MermaidGraph): MermaidGraph {
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
  return result;
}
