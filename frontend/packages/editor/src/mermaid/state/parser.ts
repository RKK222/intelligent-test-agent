import {
  applyMermaidCompactState,
  extractMermaidCompactMarker
} from "../compact-metadata";
import { normalizeMermaidHexColor } from "../style-directives";
import {
  createMermaidStateRegion,
  createMermaidStateScope,
  mermaidStateDiagramFingerprint,
  type MermaidStateDiagram,
  type MermaidStateDirection,
  type MermaidStateNode,
  type MermaidStateNodeKind,
  type MermaidStateScope,
  type MermaidStateStyle
} from "./model";

const STATE_ID_SOURCE = "[A-Za-z_][A-Za-z0-9_-]*";
const STATE_ID_PATTERN = new RegExp(`^${STATE_ID_SOURCE}$`);
const TRANSITION_PATTERN = new RegExp(
  `^\\s*(\\[\\*\\]|${STATE_ID_SOURCE})\\s*-->\\s*(\\[\\*\\]|${STATE_ID_SOURCE})(?:\\s*:\\s*(.*?))?\\s*$`
);
const DESCRIPTION_PATTERN = new RegExp(`^\\s*(${STATE_ID_SOURCE})\\s*:\\s*(.*?)\\s*$`);
const NOTE_INLINE_PATTERN = new RegExp(
  `^\\s*note\\s+(left|right)\\s+of\\s+(${STATE_ID_SOURCE})\\s*:\\s*(.*?)\\s*$`,
  "i"
);
const NOTE_BLOCK_PATTERN = new RegExp(
  `^\\s*note\\s+(left|right)\\s+of\\s+(${STATE_ID_SOURCE})\\s*$`,
  "i"
);
const STYLE_PATTERN = new RegExp(`^\\s*style\\s+(${STATE_ID_SOURCE})\\s+(.+?)\\s*;?\\s*$`, "i");

type ScopeFrame = { scope: MermaidStateScope };
type StyleRecord = { scope: MermaidStateScope; line: string; nodeId: string; source: string };

function lineIndent(line: string): string {
  return line.match(/^\s*/)?.[0] ?? "";
}

function decodeText(value: string): string {
  return value
    .replace(/<br\s*\/?\s*>/gi, "\n")
    .replace(/&quot;/gi, "\"")
    .trim();
}

function currentRegion(scope: MermaidStateScope) {
  return scope.regions.at(-1)!;
}

function parseStyleProperties(source: string, allowText: boolean): MermaidStateStyle | undefined {
  const style: MermaidStateStyle = {};
  const seen = new Set<string>();
  for (const part of source.split(",")) {
    const match = part.match(/^\s*(fill|stroke|color)\s*:\s*([^\s,]+)\s*$/i);
    if (!match) return undefined;
    const property = match[1]!.toLowerCase();
    const color = normalizeMermaidHexColor(match[2]!);
    if (!color || seen.has(property) || (property === "color" && !allowText)) return undefined;
    seen.add(property);
    if (property === "fill") style.fillColor = color;
    else if (property === "stroke") style.strokeColor = color;
    else style.textColor = color;
  }
  return seen.size > 0 ? style : undefined;
}

function structuralUnknown(line: string): boolean {
  const value = line.trim();
  return /^(?:state|note|direction)\b/i.test(value)
    || value === "--"
    || value === "}"
    || value.includes("-->")
    || value.includes("{")
    || value.includes("}");
}

/**
 * 以显式 Scope 栈解析 State DSL。任何未知容器或转换语法都会拒绝整图可视化，
 * 防止 serializer 在不理解拓扑的情况下改写用户源码。
 */
export function parseMermaidState(source: string): MermaidStateDiagram {
  const eol: "\n" | "\r\n" = source.includes("\r\n") ? "\r\n" : "\n";
  const trailingNewline = source.endsWith("\n");
  const lines = source.split(/\r?\n/);
  const headerIndex = lines.findIndex((line) => /^\s*stateDiagram(?:-v2)?\s*$/i.test(line));
  if (headerIndex < 0) throw new Error("缺少 stateDiagram 或 stateDiagram-v2 图头");
  const headerMatch = lines[headerIndex]!.match(/^\s*(stateDiagram(?:-v2)?)\s*$/i)!;
  const header = headerMatch[1]!.toLowerCase() === "statediagram-v2" ? "stateDiagram-v2" : "stateDiagram";
  const root = createMermaidStateScope("root");
  const diagram: MermaidStateDiagram = { kind: "stateDiagram", header, root };
  const stack: ScopeFrame[] = [{ scope: root }];
  const nodeById = new Map<string, { node: MermaidStateNode; regionId: string }>();
  const styleRecords: StyleRecord[] = [];
  const compact = extractMermaidCompactMarker(lines);
  let transitionSequence = 0;
  let noteSequence = 0;
  const pseudoCounts = new Map<string, number>();

  const frame = () => stack.at(-1)!;

  const ensureNamedNode = (id: string, declared = false): MermaidStateNode => {
    const region = currentRegion(frame().scope);
    const existing = nodeById.get(id);
    if (existing) {
      if (existing.regionId !== region.id) {
        throw new Error(`无法安全映射：状态 ${id} 跨 Scope 或并发区域重复出现`);
      }
      if (declared) existing.node.declared = true;
      return existing.node;
    }
    const node: MermaidStateNode = {
      id,
      kind: "state",
      label: id,
      descriptions: [],
      position: { x: 0, y: 0 },
      ...(declared ? { declared: true } : {})
    };
    region.nodes.push(node);
    nodeById.set(id, { node, regionId: region.id });
    return node;
  };

  const createPseudoNode = (kind: "start" | "end"): MermaidStateNode => {
    const region = currentRegion(frame().scope);
    const pseudoSequence = (pseudoCounts.get(region.id) ?? 0) + 1;
    pseudoCounts.set(region.id, pseudoSequence);
    const node: MermaidStateNode = {
      id: `${region.id}-${kind}-${pseudoSequence}`,
      kind,
      label: "[*]",
      descriptions: [],
      position: { x: 0, y: 0 },
      declared: true
    };
    region.nodes.push(node);
    nodeById.set(node.id, { node, regionId: region.id });
    return node;
  };

  const declareState = (id: string, label: string | undefined, kind: MermaidStateNodeKind = "state") => {
    const node = ensureNamedNode(id, true);
    if (node.kind !== "state" && node.kind !== kind) {
      throw new Error(`无法安全映射：状态 ${id} 存在冲突类型`);
    }
    node.kind = kind;
    if (label !== undefined) node.label = decodeText(label) || id;
    return node;
  };

  const addTransition = (sourceToken: string, targetToken: string, label: string) => {
    const region = currentRegion(frame().scope);
    const sourceNode = sourceToken === "[*]" ? createPseudoNode("start") : ensureNamedNode(sourceToken);
    const targetNode = targetToken === "[*]" ? createPseudoNode("end") : ensureNamedNode(targetToken);
    region.transitions.push({
      id: `transition-${++transitionSequence}`,
      source: sourceNode.id,
      target: targetNode.id,
      label: decodeText(label)
    });
  };

  const lastIndex = trailingNewline ? lines.length - 1 : lines.length;
  for (let index = 0; index < lastIndex; index += 1) {
    if (index === headerIndex) continue;
    const line = lines[index] ?? "";
    if (compact.markerLineIndexes.has(index)) continue;
    const trimmed = line.trim();
    const scope = frame().scope;
    if (index < headerIndex) {
      root.preservedLines.push(line);
      continue;
    }
    if (!trimmed) {
      scope.preservedLines.push(line);
      continue;
    }

    // Mermaid 注释和初始化指令不参与拓扑，即使正文含花括号也必须原样保留。
    if (trimmed.startsWith("%%")) {
      scope.preservedLines.push(line);
      continue;
    }

    if (/^accDescr\s*\{\s*$/i.test(trimmed)) {
      scope.preservedLines.push(line);
      let closed = false;
      while (++index < lastIndex) {
        const accessibilityLine = lines[index] ?? "";
        scope.preservedLines.push(accessibilityLine);
        if (/^\s*}\s*$/.test(accessibilityLine)) {
          closed = true;
          break;
        }
      }
      if (!closed) throw new Error("无法安全映射：accDescr 缺少 }");
      continue;
    }

    const directionMatch = trimmed.match(/^direction\s+(TB|BT|LR|RL)\s*$/i);
    if (directionMatch) {
      scope.direction = directionMatch[1]!.toUpperCase() as MermaidStateDirection;
      continue;
    }

    if (trimmed === "--") {
      scope.regions.push(createMermaidStateRegion(`${scope.id}-region-${scope.regions.length + 1}`));
      continue;
    }

    if (trimmed === "}") {
      if (stack.length === 1) throw new Error("无法安全映射：存在没有匹配复合状态的 }");
      stack.pop();
      continue;
    }

    const aliasComposite = trimmed.match(/^state\s+"(.*)"\s+as\s+([A-Za-z_][A-Za-z0-9_-]*)\s*\{\s*$/i);
    const simpleComposite = trimmed.match(/^state\s+([A-Za-z_][A-Za-z0-9_-]*)\s*\{\s*$/i);
    if (aliasComposite || simpleComposite) {
      const id = aliasComposite?.[2] ?? simpleComposite?.[1] ?? "";
      const label = aliasComposite?.[1] ?? id;
      const node = declareState(id, label);
      if (node.childScope) throw new Error(`无法安全映射：复合状态 ${id} 重复声明`);
      node.childScope = createMermaidStateScope(`${id}-scope`);
      stack.push({ scope: node.childScope });
      continue;
    }

    const pseudoMatch = trimmed.match(/^state\s+([A-Za-z_][A-Za-z0-9_-]*)\s+(?:<<(choice|fork|join)>>|\[\[(choice|fork|join)\]\])\s*$/i);
    if (pseudoMatch) {
      declareState(pseudoMatch[1]!, pseudoMatch[1], (pseudoMatch[2] ?? pseudoMatch[3])!.toLowerCase() as MermaidStateNodeKind);
      continue;
    }

    const aliasMatch = trimmed.match(/^state\s+"(.*)"\s+as\s+([A-Za-z_][A-Za-z0-9_-]*)\s*$/i);
    if (aliasMatch) {
      declareState(aliasMatch[2]!, aliasMatch[1]);
      continue;
    }
    const stateMatch = trimmed.match(/^state\s+([A-Za-z_][A-Za-z0-9_-]*)\s*$/i);
    if (stateMatch) {
      declareState(stateMatch[1]!, stateMatch[1]);
      continue;
    }

    const transitionMatch = line.match(TRANSITION_PATTERN);
    if (transitionMatch) {
      addTransition(transitionMatch[1]!, transitionMatch[2]!, transitionMatch[3] ?? "");
      continue;
    }

    if (/^(?:accTitle|accDescr|title|classDef|class|click|hide\s+empty\s+description)\b/i.test(trimmed)) {
      scope.preservedLines.push(line);
      continue;
    }

    const descriptionMatch = line.match(DESCRIPTION_PATTERN);
    if (descriptionMatch) {
      const node = ensureNamedNode(descriptionMatch[1]!);
      const description = decodeText(descriptionMatch[2] ?? "");
      if (!node.declared && node.label === node.id && node.descriptions.length === 0) {
        node.label = description || node.id;
        node.declared = true;
      } else if (description) {
        node.descriptions.push(description);
      }
      continue;
    }

    const inlineNote = line.match(NOTE_INLINE_PATTERN);
    if (inlineNote) {
      currentRegion(scope).notes.push({
        id: `note-${++noteSequence}`,
        placement: inlineNote[1]!.toLowerCase() as "left" | "right",
        target: inlineNote[2]!,
        text: decodeText(inlineNote[3] ?? "")
      });
      continue;
    }
    const blockNote = line.match(NOTE_BLOCK_PATTERN);
    if (blockNote) {
      const body: string[] = [];
      let closed = false;
      while (++index < lastIndex) {
        const noteLine = lines[index] ?? "";
        if (/^\s*end\s+note\s*$/i.test(noteLine)) {
          closed = true;
          break;
        }
        body.push(noteLine.trim());
      }
      if (!closed) throw new Error("无法安全映射：Note 缺少 end note");
      currentRegion(scope).notes.push({
        id: `note-${++noteSequence}`,
        placement: blockNote[1]!.toLowerCase() as "left" | "right",
        target: blockNote[2]!,
        text: body.join("\n").trim()
      });
      continue;
    }

    const styleMatch = line.match(STYLE_PATTERN);
    if (styleMatch) {
      styleRecords.push({ scope, line, nodeId: styleMatch[1]!, source: styleMatch[2]! });
      continue;
    }

    if (structuralUnknown(line)) throw new Error(`无法安全映射 State Diagram 结构语法：${trimmed}`);
    scope.preservedLines.push(line);
  }

  if (stack.length !== 1) throw new Error("无法安全映射：复合状态缺少 }");

  for (const record of styleRecords) {
    const node = nodeById.get(record.nodeId)?.node;
    const allowText = node?.kind === "state";
    const style = node && node.kind !== "start" && node.kind !== "end"
      ? parseStyleProperties(record.source, allowText)
      : undefined;
    if (node && style) node.style = { ...node.style, ...style };
    else record.scope.preservedLines.push(record.line);
  }

  const compactApplied = compact.encoded !== null && applyMermaidCompactState(diagram, compact.encoded);
  const metadataConflict = compact.markerLineIndexes.size > 0 && !compactApplied;
  if (metadataConflict) {
    const preservedMarkers = [...compact.markerLineIndexes]
      .sort((left, right) => left - right)
      .map((index) => lines[index] ?? "");
    root.preservedLines.unshift(...preservedMarkers);
  }

  diagram.sourceFormat = {
    source,
    headerRaw: lines[headerIndex] ?? header,
    eol,
    trailingNewline,
    indentUnit: lines.slice(headerIndex + 1).map(lineIndent).find(Boolean) || "  ",
    fingerprint: "",
    ...(metadataConflict ? { metadataConflict: true } : {})
  };
  diagram.sourceFormat.fingerprint = mermaidStateDiagramFingerprint(diagram);
  return diagram;
}

export function isMermaidStateId(value: string): boolean {
  return STATE_ID_PATTERN.test(value);
}
