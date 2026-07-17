import { applyMermaidCompactFlow, extractMermaidCompactMarker } from "./compact-metadata";
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
import { findMermaidNodeTypeByModernShape } from "./node-shapes";
import { consumeMermaidStyleDirectives, type MermaidPreservedRecord } from "./style-directives";

const HEADER_PATTERN = /^\s*(flowchart|graph)(?:\s+(TD|TB|BT|LR|RL))?\s*;?\s*$/i;
const NODE_ID_PATTERN = "[A-Za-z_][A-Za-z0-9_-]*";

type ParsedNode = Pick<MermaidNode, "id" | "text" | "type"> & { explicit: boolean };

type ModernNodeExpression =
  | { status: "supported"; node: ParsedNode }
  | { status: "preserved"; id: string };

function unquoteLabel(label: string): string {
  const trimmed = label.trim();
  const value = trimmed.startsWith('"') && trimmed.endsWith('"') ? trimmed.slice(1, -1) : trimmed;
  return value.replaceAll("&quot;", '"').replaceAll("&#124;", "|");
}

/**
 * 现代属性只接管可无损解码的 YAML 标量：shape 可使用已知裸短名，label 必须是单引号
 * 或 JSON/YAML 共有双引号转义子集。其他值返回 null，由上层整句保留，避免可见标签变化。
 */
function unquoteModernProperty(value: string, allowPlain: boolean): string | null {
  const trimmed = value.trim();
  if (trimmed.startsWith('"') && trimmed.endsWith('"')) {
    try {
      const decoded = JSON.parse(trimmed);
      if (typeof decoded === "string") {
        return decoded.replaceAll("&quot;", '"').replaceAll("&#124;", "|");
      }
    } catch {
      return null;
    }
  }
  if (trimmed.startsWith("'") && trimmed.endsWith("'")) {
    return trimmed.slice(1, -1).replaceAll("''", "'")
      .replaceAll("&quot;", '"').replaceAll("&#124;", "|");
  }
  if (/^["']|["']$/.test(trimmed)) return null;
  // Mermaid 通过 YAML 解析裸标量，false/null/日期/十六进制数等并不保持原始字符串语义。
  // shape 只会再与已知短名匹配，可以接管；label 为避免可见内容变化，只接管引号字符串。
  return allowPlain ? unquoteLabel(trimmed) : null;
}

/** 按引号边界拆分现代节点属性，标签中的逗号不会被误当成字段分隔符。 */
function splitModernNodeProperties(source: string): string[] | null {
  const properties: string[] = [];
  let start = 0;
  let quote: '"' | "'" | undefined;
  let escaped = false;
  for (let index = 0; index < source.length; index += 1) {
    const character = source[index]!;
    if (escaped) {
      escaped = false;
      continue;
    }
    if (character === "\\" && quote === '"') {
      escaped = true;
      continue;
    }
    if (character === '"' || character === "'") {
      quote = quote === character ? undefined : quote ?? character;
      continue;
    }
    if (character === "," && !quote) {
      properties.push(source.slice(start, index));
      start = index + 1;
    }
  }
  if (quote || escaped) return null;
  properties.push(source.slice(start));
  return properties;
}

/**
 * 解析 `ID@{ shape: ..., label: ... }`。只有 shape/label 两个字段可被无损接管；
 * 出现未知字段、重复字段或未知 shape 时保留原文，避免保存后丢失 Mermaid 信息。
 */
function parseModernNodeExpression(value: string): ModernNodeExpression | null {
  const match = value.match(new RegExp(`^(${NODE_ID_PATTERN})@\\{([\\s\\S]*)\\}$`));
  if (!match) return null;
  const id = match[1] ?? "";
  const parts = splitModernNodeProperties(match[2] ?? "");
  if (!parts) return { status: "preserved", id };

  const properties = new Map<string, string>();
  for (const part of parts) {
    const property = part.match(/^\s*([A-Za-z][A-Za-z0-9_-]*)\s*:\s*([\s\S]*?)\s*$/);
    if (!property) return { status: "preserved", id };
    const key = property[1]?.toLowerCase() ?? "";
    if ((key !== "shape" && key !== "label") || properties.has(key)) {
      return { status: "preserved", id };
    }
    properties.set(key, property[2] ?? "");
  }

  const rawShape = properties.get("shape");
  if (!rawShape) return { status: "preserved", id };
  const decodedShape = unquoteModernProperty(rawShape, true);
  if (decodedShape === null) return { status: "preserved", id };
  const type = findMermaidNodeTypeByModernShape(decodedShape);
  if (!type) return { status: "preserved", id };
  const rawLabel = properties.get("label");
  const decodedLabel = rawLabel === undefined ? id : unquoteModernProperty(rawLabel, false);
  if (decodedLabel === null) return { status: "preserved", id };
  return {
    status: "supported",
    node: {
      id,
      text: decodedLabel,
      type,
      explicit: true
    }
  };
}

/**
 * 扫描一行中独立或内联的现代节点表达式。未知节点即使出现在边声明里，也要先登记其
 * ID，避免后续 `A --> B` 把同一个未知形状重新推断成矩形。
 */
function scanModernNodeExpressions(line: string): { ids: string[]; hasPreserved: boolean } {
  const ids: string[] = [];
  let hasPreserved = false;
  const isIdentifierPart = (character: string) => /[A-Za-z0-9_-]/.test(character);
  const isIdentifierStart = (character: string) => /[A-Za-z_]/.test(character);
  let searchFrom = 0;
  while (searchFrom < line.length) {
    const markerIndex = line.indexOf("@{", searchFrom);
    if (markerIndex < 0) break;
    let expressionStart = markerIndex;
    while (expressionStart > 0 && isIdentifierPart(line[expressionStart - 1]!)) expressionStart -= 1;
    if (expressionStart === markerIndex || !isIdentifierStart(line[expressionStart]!)) {
      searchFrom = markerIndex + 2;
      continue;
    }
    let quote: '"' | "'" | undefined;
    let escaped = false;
    let end = -1;
    for (let index = markerIndex + 2; index < line.length; index += 1) {
      const character = line[index]!;
      if (escaped) {
        escaped = false;
        continue;
      }
      if (character === "\\" && quote === '"') {
        escaped = true;
        continue;
      }
      if (character === '"' || character === "'") {
        quote = quote === character ? undefined : quote ?? character;
        continue;
      }
      if (character === "}" && !quote) {
        end = index;
        break;
      }
    }
    if (end < 0) {
      ids.push(line.slice(expressionStart, markerIndex));
      hasPreserved = true;
      break;
    }
    const modern = parseModernNodeExpression(line.slice(expressionStart, end + 1));
    if (modern) {
      ids.push(modern.status === "supported" ? modern.node.id : modern.id);
      if (modern.status === "preserved") hasPreserved = true;
    } else {
      hasPreserved = true;
    }
    searchFrom = end + 1;
  }
  return { ids, hasPreserved };
}

function parseNodeExpression(expression: string): ParsedNode | null {
  const value = expression.trim().replace(/;$/, "").trim();
  const modern = parseModernNodeExpression(value);
  if (modern) return modern.status === "supported" ? modern.node : null;
  const shapes: Array<{ pattern: RegExp; type: MermaidNodeType }> = [
    { pattern: /^([A-Za-z_][A-Za-z0-9_-]*)\(\(\((.*)\)\)\)$/, type: "double-circle" },
    { pattern: new RegExp(`^(${NODE_ID_PATTERN})\\(\\((.*)\\)\\)$`), type: "circle" },
    { pattern: new RegExp(`^(${NODE_ID_PATTERN})\\(\\[(.*)\\]\\)$`), type: "stadium" },
    { pattern: /^([A-Za-z_][A-Za-z0-9_-]*)\[\[(.*)\]\]$/, type: "subroutine" },
    { pattern: /^([A-Za-z_][A-Za-z0-9_-]*)\[\((.*)\)\]$/, type: "database" },
    { pattern: /^([A-Za-z_][A-Za-z0-9_-]*)\{\{(.*)\}\}$/, type: "hexagon" },
    { pattern: new RegExp(`^(${NODE_ID_PATTERN})\\{(.*)\\}$`), type: "diamond" },
    { pattern: /^([A-Za-z_][A-Za-z0-9_-]*)\[\/(.*)\/\]$/, type: "parallelogram" },
    { pattern: /^([A-Za-z_][A-Za-z0-9_-]*)\[\/(.*)\\\]$/, type: "trapezoid" },
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

const PRESERVED_EDGE_OPERATORS = [
  "<-.->", "<==>", "<-->", "o--o", "o--x", "x--o", "x--x",
  "-.->", "==>", "<==", "-->", "<--", "--o", "--x", "---", "~~~"
] as const;

/** 扫描最外层边操作符，节点标签、现代属性和连线标签中的符号不会被误计为 link。 */
function scanPreservedEdgeOperators(line: string): Array<{ start: number; end: number }> {
  const operators: Array<{ start: number; end: number }> = [];
  const closingByOpening: Record<string, string> = { "[": "]", "(": ")", "{": "}" };
  const closings: string[] = [];
  let quote: "\"" | "'" | undefined;
  let escaped = false;
  let pipeLabel = false;
  for (let index = 0; index < line.length; index += 1) {
    const character = line[index]!;
    if (escaped) {
      escaped = false;
      continue;
    }
    if (character === "\\" && quote === "\"") {
      escaped = true;
      continue;
    }
    if (character === "\"" || character === "'") {
      quote = quote === character ? undefined : quote ?? character;
      continue;
    }
    if (quote) continue;
    if (closingByOpening[character]) {
      closings.push(closingByOpening[character]!);
      continue;
    }
    if (closings.at(-1) === character) {
      closings.pop();
      continue;
    }
    if (closings.length > 0) continue;
    if (character === "|") {
      pipeLabel = !pipeLabel;
      continue;
    }
    if (pipeLabel) continue;
    const operator = PRESERVED_EDGE_OPERATORS.find((candidate) => line.startsWith(candidate, index));
    if (!operator) continue;
    operators.push({ start: index, end: index + operator.length });
    index += operator.length - 1;
  }
  return operators;
}

function endpointAlternativeCount(source: string, side: "source" | "target"): number {
  const statement = side === "source"
    ? source.slice(source.lastIndexOf(";") + 1)
    : source.slice(0, source.indexOf(";") < 0 ? source.length : source.indexOf(";"));
  let count = 1;
  let depth = 0;
  let quote: "\"" | "'" | undefined;
  let escaped = false;
  let pipeLabel = false;
  for (const character of statement) {
    if (escaped) {
      escaped = false;
      continue;
    }
    if (character === "\\" && quote === "\"") {
      escaped = true;
      continue;
    }
    if (character === "\"" || character === "'") {
      quote = quote === character ? undefined : quote ?? character;
      continue;
    }
    if (quote) continue;
    if ("[({".includes(character)) depth += 1;
    else if ("])}".includes(character)) depth = Math.max(0, depth - 1);
    else if (character === "|" && depth === 0) pipeLabel = !pipeLabel;
    else if (character === "&" && depth === 0 && !pipeLabel) count += 1;
  }
  return count;
}

/** 多目标和链式语句会展开成多条 Mermaid link，linkStyle 的全局索引必须按展开数偏移。 */
function countPreservedMermaidLinks(line: string): number {
  const trimmed = line.trim();
  if (
    !trimmed ||
    trimmed.startsWith("%%") ||
    /^(?:accTitle|accDescr)\s*[:{]/i.test(trimmed)
  ) return 0;
  const operators = scanPreservedEdgeOperators(line);
  return operators.reduce((count, operator, index) => {
    const previousEnd = operators[index - 1]?.end ?? 0;
    const nextStart = operators[index + 1]?.start ?? line.length;
    const sourceCount = endpointAlternativeCount(line.slice(previousEnd, operator.start), "source");
    const targetCount = endpointAlternativeCount(line.slice(operator.end, nextStart), "target");
    return count + sourceCount * targetCount;
  }, 0);
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
  const compactMetadata = extractMermaidCompactMarker(lines);
  const compactMarkerIndexes = [...compactMetadata.markerLineIndexes].sort((left, right) => left - right);
  const interruptedCompactContinuations = new Set(compactMarkerIndexes.filter((index, position) =>
    position > 0 && lines[index]?.startsWith("%%@+") && compactMarkerIndexes[position - 1] !== index - 1
  ));
  const nodes = new Map<string, MermaidNode & { explicit: boolean }>();
  const edges: MermaidGraph["edges"] = [];
  const preservedModernNodeIds = new Set<string>();
  let scanPreservedBlockDepth = 0;
  let scanAccessibilityDescriptionBlock = false;
  for (const line of lines) {
    const trimmed = line.trim();
    if (scanAccessibilityDescriptionBlock) {
      if (trimmed.includes("}")) scanAccessibilityDescriptionBlock = false;
      continue;
    }
    if (/^accDescr\s*\{/i.test(trimmed)) {
      if (!trimmed.includes("}")) scanAccessibilityDescriptionBlock = true;
      continue;
    }
    const startsPreservedBlock = /^subgraph\b/i.test(trimmed);
    const insidePreservedBlock = scanPreservedBlockDepth > 0 || startsPreservedBlock;
    if (startsPreservedBlock) scanPreservedBlockDepth += 1;
    if (!trimmed.startsWith("%%")) {
      const scan = scanModernNodeExpressions(line);
      if (scan.ids.length > 0) {
        const normalized = trimmed.replace(/;$/, "").trim();
        const standalone = parseModernNodeExpression(normalized);
        const editableStatement = !insidePreservedBlock && !scan.hasPreserved && (
          standalone?.status === "supported" || parseEdgeLine(line) !== null
        );
        if (!editableStatement) {
          for (const id of scan.ids) preservedModernNodeIds.add(id);
        }
      }
    }
    if (/^end\s*;?$/i.test(trimmed) && scanPreservedBlockDepth > 0) scanPreservedBlockDepth -= 1;
  }
  // 私有注释先带源码索引按普通行保留，只有整个新/旧 metadata 验证成功后才统一消费。
  const preservedRecords: MermaidPreservedRecord[] = [];
  let preservedBlockDepth = 0;
  let accessibilityDescriptionBlock = false;

  const preserveLine = (line: string, sourceIndex: number, countLinks = true) => {
    const linkCount = countLinks ? countPreservedMermaidLinks(line) : 0;
    preservedRecords.push({
      sourceIndex,
      beforeEditableIndex: edges.length,
      line,
      linkCount: linkCount > 0 ? linkCount : undefined
    });
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
    if (index === headerIndex) return;
    // 节点声明或空行不会进入 preservedLines；用空占位保住原中断，避免保存后把坏续行重新拼成有效 marker。
    if (interruptedCompactContinuations.has(index)) preserveLine("", index - 0.5);
    const trimmed = line.trim();
    if (accessibilityDescriptionBlock) {
      preserveLine(line, index, false);
      if (trimmed.includes("}")) accessibilityDescriptionBlock = false;
      return;
    }
    if (/^accDescr\s*\{/i.test(trimmed)) {
      preserveLine(line, index, false);
      if (!trimmed.includes("}")) accessibilityDescriptionBlock = true;
      return;
    }
    if (preservedBlockDepth > 0) {
      preserveLine(line, index);
      if (/^subgraph\b/i.test(trimmed)) preservedBlockDepth += 1;
      if (/^end\s*;?$/i.test(trimmed)) preservedBlockDepth -= 1;
      return;
    }
    if (!trimmed) return;
    if (/^subgraph\b/i.test(trimmed)) {
      preservedBlockDepth = 1;
      preserveLine(line, index);
      return;
    }
    const edge = parseEdgeLine(line);
    if (edge) {
      // 未知/带额外属性的现代节点不能被后续裸 ID 连线重新推断为矩形。
      if (preservedModernNodeIds.has(edge.source.id) || preservedModernNodeIds.has(edge.target.id)) {
        preserveLine(line, index);
        return;
      }
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
      if (preservedModernNodeIds.has(node.id)) {
        preserveLine(line, index);
        return;
      }
      upsertNode(node);
      return;
    }
    preserveLine(line, index);
  });

  const edgeCounts = new Map<string, number>();
  for (const edge of edges) {
    const key = `${edge.source}\u0000${edge.target}`;
    edgeCounts.set(key, (edgeCounts.get(key) ?? 0) + 1);
  }
  const ambiguousMetadata = edgePortMetadata.entries.some(
    (entry) => (edgeCounts.get(`${entry.source}\u0000${entry.target}`) ?? 0) > 1
  );
  if (!ambiguousMetadata) {
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

  const graph: MermaidGraph = {
    kind,
    direction,
    nodes: Array.from(nodes.values(), ({ explicit: _explicit, ...node }) => node),
    edges,
    preservedLines: preservedRecords.map((record) => record.line),
    preservedSegments: []
  };
  const consumedStyleIndexes = consumeMermaidStyleDirectives(graph, preservedRecords);
  const compactApplied = compactMetadata.encoded !== null
    ? applyMermaidCompactFlow(graph, compactMetadata.encoded)
    : false;
  const compactConflict = compactMetadata.markerLineIndexes.size > 0 && !compactApplied;
  const removedIndexes = new Set<number>();
  if (!compactConflict) {
    for (const index of consumedLineIndexes) removedIndexes.add(index);
    if (!ambiguousMetadata) {
      for (const index of edgePortMetadata.consumedLineIndexes) removedIndexes.add(index);
    }
  }
  if (compactApplied) {
    for (const index of compactMetadata.markerLineIndexes) removedIndexes.add(index);
  }
  for (const index of consumedStyleIndexes) removedIndexes.add(index);

  const remainingRecords = preservedRecords.filter((record) => !removedIndexes.has(record.sourceIndex));
  graph.preservedLines = remainingRecords.map((record) => record.line);
  for (const record of remainingRecords) {
    const current = graph.preservedSegments!.at(-1);
    if (current?.beforeEditableIndex === record.beforeEditableIndex) {
      current.lines.push(record.line);
      current.linkCount = (current.linkCount ?? 0) + (record.linkCount ?? 0);
    } else {
      graph.preservedSegments!.push({
        beforeEditableIndex: record.beforeEditableIndex,
        lines: [record.line],
        ...(record.linkCount ? { linkCount: record.linkCount } : {})
      });
    }
  }
  return graph;
}
