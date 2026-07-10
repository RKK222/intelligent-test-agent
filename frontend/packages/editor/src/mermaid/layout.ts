import { cloneMermaidGraph, type MermaidGraph } from "./model";

/**
 * 基于有向边层级生成确定性布局；环路节点回退到首层，确保任何合法模型都可编辑。
 */
export function autoLayoutMermaidGraph(graph: MermaidGraph): MermaidGraph {
  const result = cloneMermaidGraph(graph);
  const nodeOrder = new Map(result.nodes.map((node, index) => [node.id, index]));
  const indegree = new Map(result.nodes.map((node) => [node.id, 0]));
  const outgoing = new Map(result.nodes.map((node) => [node.id, [] as string[]]));
  for (const edge of result.edges) {
    if (!indegree.has(edge.source) || !indegree.has(edge.target)) continue;
    indegree.set(edge.target, (indegree.get(edge.target) ?? 0) + 1);
    outgoing.get(edge.source)?.push(edge.target);
  }

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

  const groups = new Map<number, string[]>();
  for (const node of result.nodes) {
    const level = levels.get(node.id) ?? 0;
    const group = groups.get(level) ?? [];
    group.push(node.id);
    group.sort((left, right) => (nodeOrder.get(left) ?? 0) - (nodeOrder.get(right) ?? 0));
    groups.set(level, group);
  }
  const maxLevel = Math.max(0, ...groups.keys());
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
