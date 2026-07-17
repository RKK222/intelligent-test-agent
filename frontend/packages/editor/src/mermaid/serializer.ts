import { serializeMermaidCompactFlow } from "./compact-metadata";
import type { MermaidEdgeRelation, MermaidGraph, MermaidNode } from "./model";
import { MERMAID_MODERN_SHAPE_BY_TYPE } from "./node-shapes";
import { serializeMermaidStyleDirectives } from "./style-directives";

function quoteLabel(value: string): string {
  return JSON.stringify(value);
}

function serializeNode(node: MermaidNode): string {
  const label = quoteLabel(node.text);
  const shape = MERMAID_MODERN_SHAPE_BY_TYPE[node.type];
  return `${node.id}@{ shape: ${shape}, label: ${label} }`;
}

function operatorForRelation(relation: MermaidEdgeRelation): string {
  if (relation === "line") return "---";
  if (relation === "dotted") return "-.->";
  if (relation === "thick") return "==>";
  return "-->";
}

/** 生成格式稳定、可由 Mermaid 官方 parser 接受的 DSL。 */
export function serializeMermaidGraph(graph: MermaidGraph): string {
  const lines = [
    `${graph.kind} ${graph.direction}`,
    ...serializeMermaidCompactFlow(graph)
  ];
  lines.push(...graph.nodes.map(serializeNode));
  const serializeEdge = (edge: MermaidGraph["edges"][number]) => {
    const rawLabel = edge.label.trim().replaceAll("|", "&#124;").replaceAll("\n", "<br>");
    const label = rawLabel ? `|${rawLabel}|` : "";
    return `${edge.source} ${operatorForRelation(edge.relation)}${label} ${edge.target}`;
  };
  if (graph.preservedSegments?.length) {
    for (let index = 0; index <= graph.edges.length; index += 1) {
      for (const segment of graph.preservedSegments) {
        if (Math.min(segment.beforeEditableIndex, graph.edges.length) === index) lines.push(...segment.lines);
      }
      const edge = graph.edges[index];
      if (edge) lines.push(serializeEdge(edge));
    }
  } else {
    lines.push(...graph.edges.map(serializeEdge), ...graph.preservedLines);
  }
  lines.push(...serializeMermaidStyleDirectives(graph));
  return `${lines.join("\n").trimEnd()}\n`;
}
