import { serializeMermaidLayout } from "./metadata";
import { serializeMermaidEdgePorts } from "./edge-port-metadata";
import type { MermaidEdgeRelation, MermaidGraph, MermaidNode, MermaidNodeType } from "./model";

function escapeLabel(value: string): string {
  return value.replaceAll("\n", " ").replaceAll('"', "&quot;");
}

function serializeNode(node: MermaidNode): string {
  const label = `"${escapeLabel(node.text || node.id)}"`;
  const wrappers: Record<MermaidNodeType, [string, string]> = {
    rectangle: ["[", "]"],
    rounded: ["(", ")"],
    stadium: ["([", "])"],
    diamond: ["{", "}"],
    circle: ["((", "))"]
  };
  const [open, close] = wrappers[node.type];
  return `${node.id}${open}${label}${close}`;
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
    ...serializeMermaidLayout(graph.nodes),
    ...serializeMermaidEdgePorts(graph.edges)
  ];
  lines.push(...graph.nodes.map(serializeNode));
  const serializeEdge = (edge: MermaidGraph["edges"][number]) => {
    const rawLabel = edge.label.trim().replaceAll("|", "&#124;").replaceAll("\n", " ");
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
  return `${lines.join("\n").trimEnd()}\n`;
}
