import type { MermaidEdgeStyle, MermaidGraph, MermaidNodeStyle } from "./model";

const HEX_COLOR_PATTERN = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i;
const NODE_STYLE_PROPERTY_MAP = {
  color: "textColor",
  fill: "fillColor",
  stroke: "strokeColor"
} as const;

export type MermaidPreservedRecord = {
  sourceIndex: number;
  beforeEditableIndex: number;
  line: string;
  linkCount?: number;
};

type MermaidLinkOffsetRecord = Pick<MermaidPreservedRecord, "beforeEditableIndex" | "linkCount">;

/** Mermaid 的 linkStyle 使用全部 link 的全局索引，保留语句中的 link 也必须计入偏移。 */
function editableEdgeGlobalIndexes(
  graph: MermaidGraph,
  records: readonly MermaidLinkOffsetRecord[] = graph.preservedSegments ?? []
): number[] {
  return graph.edges.map((_, editableIndex) => editableIndex + records.reduce(
    (offset, record) => offset + (record.beforeEditableIndex <= editableIndex ? record.linkCount ?? 0 : 0),
    0
  ));
}

/** 只接管 Mermaid 直接样式中的不透明 HEX，其他 CSS 值必须继续原样保存。 */
export function normalizeMermaidHexColor(value: string): string | undefined {
  const match = value.trim().match(HEX_COLOR_PATTERN);
  if (!match) return undefined;
  const digits = match[1]!.toUpperCase();
  const expanded = digits.length === 3
    ? digits.split("").map((character) => character.repeat(2)).join("")
    : digits;
  return `#${expanded}`;
}

function parseProperties(
  source: string,
  allowed: ReadonlySet<string>
): Map<string, string> | null {
  const properties = new Map<string, string>();
  for (const rawProperty of source.split(",")) {
    const match = rawProperty.match(/^\s*([A-Za-z][A-Za-z-]*)\s*:\s*([^\s,]+)\s*$/);
    if (!match) return null;
    const name = match[1]!.toLowerCase();
    const value = normalizeMermaidHexColor(match[2]!);
    if (!allowed.has(name) || !value || properties.has(name)) return null;
    properties.set(name, value);
  }
  return properties.size > 0 ? properties : null;
}

/**
 * 完全识别的 style/linkStyle 才从 preserved 区接管。按源码顺序应用，使重复声明的后者
 * 覆盖前者；任何额外属性、无效索引或文本节点表面样式都会整句保留。
 */
export function consumeMermaidStyleDirectives(
  graph: MermaidGraph,
  records: readonly MermaidPreservedRecord[]
): Set<number> {
  const consumed = new Set<number>();
  const nodesById = new Map(graph.nodes.map((node) => [node.id, node]));
  const edgeByGlobalIndex = new Map(
    editableEdgeGlobalIndexes(graph, records).map((globalIndex, editableIndex) => [globalIndex, graph.edges[editableIndex]!])
  );

  for (const record of records) {
    const nodeMatch = record.line.match(/^\s*style\s+([A-Za-z_][A-Za-z0-9_-]*)\s+(.+?)\s*;?\s*$/i);
    if (nodeMatch) {
      const node = nodesById.get(nodeMatch[1]!);
      const properties = parseProperties(nodeMatch[2]!, new Set(["color", "fill", "stroke"]));
      if (!node || !properties || (node.type === "text" && (properties.has("fill") || properties.has("stroke")))) {
        continue;
      }
      const style: MermaidNodeStyle = { ...node.style };
      for (const [name, value] of properties) style[NODE_STYLE_PROPERTY_MAP[name as keyof typeof NODE_STYLE_PROPERTY_MAP]] = value;
      node.style = style;
      consumed.add(record.sourceIndex);
      continue;
    }

    const edgeMatch = record.line.match(/^\s*linkStyle\s+([0-9]+(?:\s*,\s*[0-9]+)*)\s+(.+?)\s*;?\s*$/i);
    if (!edgeMatch) continue;
    const properties = parseProperties(edgeMatch[2]!, new Set(["color"]));
    const indexes = edgeMatch[1]!.split(",").map((value) => Number(value.trim()));
    if (!properties || indexes.some((index) => !Number.isSafeInteger(index) || !edgeByGlobalIndex.has(index))) continue;
    const textColor = properties.get("color")!;
    for (const index of indexes) {
      const edge = edgeByGlobalIndex.get(index)!;
      edge.style = { ...edge.style, textColor } satisfies MermaidEdgeStyle;
    }
    consumed.add(record.sourceIndex);
  }
  return consumed;
}

/** 规范化样式始终在保留语句之后输出，确保本次可视化修改具有最终覆盖权。 */
export function serializeMermaidStyleDirectives(graph: MermaidGraph): string[] {
  const lines: string[] = [];
  for (const node of graph.nodes) {
    const properties: string[] = [];
    const fillColor = node.style?.fillColor && normalizeMermaidHexColor(node.style.fillColor);
    const strokeColor = node.style?.strokeColor && normalizeMermaidHexColor(node.style.strokeColor);
    const textColor = node.style?.textColor && normalizeMermaidHexColor(node.style.textColor);
    if (node.type !== "text" && fillColor) properties.push(`fill:${fillColor}`);
    if (node.type !== "text" && strokeColor) properties.push(`stroke:${strokeColor}`);
    if (textColor) properties.push(`color:${textColor}`);
    if (properties.length > 0) lines.push(`style ${node.id} ${properties.join(",")}`);
  }
  const edgeGlobalIndexes = editableEdgeGlobalIndexes(graph);
  graph.edges.forEach((edge, index) => {
    const textColor = edge.style?.textColor && normalizeMermaidHexColor(edge.style.textColor);
    if (textColor) lines.push(`linkStyle ${edgeGlobalIndexes[index]} color:${textColor}`);
  });
  return lines;
}
