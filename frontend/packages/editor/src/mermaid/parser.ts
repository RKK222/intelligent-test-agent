import { extractMermaidLayout } from "./metadata";
import { extractMermaidEdgePorts } from "./edge-port-metadata";
import type {
  MermaidDirection,
  MermaidEdgeRelation,
  MermaidGraph,
  MermaidGraphKind,
  MermaidNode,
  MermaidNodeType
} from "./model";

const HEADER_PATTERN = /^\s*(flowchart|graph)(?:\s+(TD|TB|BT|LR|RL))?\s*;?\s*$/i;
const NODE_ID_PATTERN = "[A-Za-z_][A-Za-z0-9_-]*";

type ParsedNode = Pick<MermaidNode, "id" | "text" | "type"> & { explicit: boolean };

function unquoteLabel(label: string): string {
  const trimmed = label.trim();
  const value = trimmed.startsWith('"') && trimmed.endsWith('"') ? trimmed.slice(1, -1) : trimmed;
  return value.replaceAll("&quot;", '"').replaceAll("&#124;", "|");
}

function parseNodeExpression(expression: string): ParsedNode | null {
  const value = expression.trim().replace(/;$/, "").trim();
  const shapes: Array<{ pattern: RegExp; type: MermaidNodeType }> = [
    { pattern: new RegExp(`^(${NODE_ID_PATTERN})\\(\\((.*)\\)\\)$`), type: "circle" },
    { pattern: new RegExp(`^(${NODE_ID_PATTERN})\\(\\[(.*)\\]\\)$`), type: "stadium" },
    { pattern: new RegExp(`^(${NODE_ID_PATTERN})\\{(.*)\\}$`), type: "diamond" },
    { pattern: new RegExp(`^(${NODE_ID_PATTERN})\\((.*)\\)$`), type: "rounded" },
    { pattern: new RegExp(`^(${NODE_ID_PATTERN})\\[(.*)\\]$`), type: "rectangle" }
  ];
  for (const shape of shapes) {
    const match = value.match(shape.pattern);
    if (match) {
      return { id: match[1] ?? "", text: unquoteLabel(match[2] ?? ""), type: shape.type, explicit: true };
    }
  }
  if (new RegExp(`^${NODE_ID_PATTERN}$`).test(value)) {
    return { id: value, text: value, type: "rectangle", explicit: false };
  }
  return null;
}

function relationFromOperator(operator: string): MermaidEdgeRelation {
  if (operator === "---") return "line";
  if (operator === "-.->") return "dotted";
  if (operator === "==>") return "thick";
  return "arrow";
}

function parseEdgeLine(line: string): {
  source: ParsedNode;
  target: ParsedNode;
  label: string;
  relation: MermaidEdgeRelation;
} | null {
  const legacyLabel = line.match(/^\s*(.+?)\s+--\s+(.+?)\s+-->\s+(.+?)\s*;?\s*$/);
  if (legacyLabel) {
    const source = parseNodeExpression(legacyLabel[1] ?? "");
    const target = parseNodeExpression(legacyLabel[3] ?? "");
    if (source && target) {
      return { source, target, label: unquoteLabel(legacyLabel[2] ?? ""), relation: "arrow" };
    }
  }

  const match = line.match(/^\s*(.+?)\s*(-->|---|-\.->|==>)\s*(?:\|([^|]*)\|\s*)?(.+?)\s*;?\s*$/);
  if (!match) return null;
  const source = parseNodeExpression(match[1] ?? "");
  const target = parseNodeExpression(match[4] ?? "");
  if (!source || !target) return null;
  return {
    source,
    target,
    label: unquoteLabel(match[3] ?? ""),
    relation: relationFromOperator(match[2] ?? "-->")
  };
}

/** 将当前支持范围内的 Mermaid flowchart/graph 转为可视化中间模型。 */
export function parseMermaidFlowchart(source: string): MermaidGraph {
  const lines = source.replaceAll("\r\n", "\n").split("\n");
  const headerIndex = lines.findIndex((line) => HEADER_PATTERN.test(line));
  if (headerIndex < 0) {
    const firstStatement = lines.find((line) => line.trim() && !line.trim().startsWith("%%"))?.trim() ?? "";
    if (/^(sequenceDiagram|classDiagram|stateDiagram|gantt|mindmap|pie)\b/i.test(firstStatement)) {
      throw new Error("仅支持 flowchart 或 graph 可视化编辑");
    }
    throw new Error("缺少 flowchart 或 graph 图头");
  }
  const header = lines[headerIndex]?.match(HEADER_PATTERN);
  if (!header) throw new Error("Mermaid 图头无法解析");

  const kind = (header[1]?.toLowerCase() ?? "flowchart") as MermaidGraphKind;
  const direction = (header[2]?.toUpperCase() ?? "TD") as MermaidDirection;
  const { layout, consumedLineIndexes } = extractMermaidLayout(lines);
  const edgePortMetadata = extractMermaidEdgePorts(lines);
  const nodes = new Map<string, MermaidNode & { explicit: boolean }>();
  const edges: MermaidGraph["edges"] = [];
  const preservedLines: string[] = [];
  const preservedSegments: NonNullable<MermaidGraph["preservedSegments"]> = [];
  let preservedBlockDepth = 0;

  const preserveLine = (line: string) => {
    preservedLines.push(line);
    const anchor = edges.length;
    const current = preservedSegments.at(-1);
    if (current?.beforeEditableIndex === anchor) current.lines.push(line);
    else preservedSegments.push({ beforeEditableIndex: anchor, lines: [line] });
  };

  const upsertNode = (parsed: ParsedNode) => {
    const existing = nodes.get(parsed.id);
    if (!existing || (parsed.explicit && !existing.explicit)) {
      nodes.set(parsed.id, {
        id: parsed.id,
        text: parsed.text,
        type: parsed.type,
        position: layout[parsed.id] ? { ...layout[parsed.id] } : { x: 0, y: 0 },
        explicit: parsed.explicit
      });
    }
  };

  lines.forEach((line, index) => {
    if (
      index === headerIndex ||
      consumedLineIndexes.has(index) ||
      edgePortMetadata.consumedLineIndexes.has(index)
    ) return;
    const trimmed = line.trim();
    if (preservedBlockDepth > 0) {
      preserveLine(line);
      if (/^subgraph\b/i.test(trimmed)) preservedBlockDepth += 1;
      if (/^end\s*;?$/i.test(trimmed)) preservedBlockDepth -= 1;
      return;
    }
    if (!trimmed) return;
    if (/^subgraph\b/i.test(trimmed)) {
      preservedBlockDepth = 1;
      preserveLine(line);
      return;
    }
    const edge = parseEdgeLine(line);
    if (edge) {
      upsertNode(edge.source);
      upsertNode(edge.target);
      edges.push({
        id: `edge-${edges.length + 1}`,
        source: edge.source.id,
        target: edge.target.id,
        label: edge.label,
        relation: edge.relation
      });
      return;
    }
    const node = parseNodeExpression(line);
    if (node) {
      upsertNode(node);
      return;
    }
    preserveLine(line);
  });

  const edgeCounts = new Map<string, number>();
  for (const edge of edges) {
    const key = `${edge.source}\u0000${edge.target}`;
    edgeCounts.set(key, (edgeCounts.get(key) ?? 0) + 1);
  }
  const ambiguousMetadata = edgePortMetadata.entries.some(
    (entry) => (edgeCounts.get(`${entry.source}\u0000${entry.target}`) ?? 0) > 1
  );
  if (ambiguousMetadata) {
    preservedLines.push(...edgePortMetadata.rawLines);
    preservedSegments.unshift({ beforeEditableIndex: 0, lines: [...edgePortMetadata.rawLines] });
  } else {
    const metadataByEdge = new Map(
      edgePortMetadata.entries.map((entry) => [`${entry.source}\u0000${entry.target}`, entry])
    );
    for (const edge of edges) {
      const metadata = metadataByEdge.get(`${edge.source}\u0000${edge.target}`);
      if (metadata) {
        edge.sourceHandle = metadata.sourceHandle;
        edge.targetHandle = metadata.targetHandle;
      }
    }
  }

  return {
    kind,
    direction,
    nodes: Array.from(nodes.values(), ({ explicit: _explicit, ...node }) => node),
    edges,
    preservedLines,
    preservedSegments
  };
}
