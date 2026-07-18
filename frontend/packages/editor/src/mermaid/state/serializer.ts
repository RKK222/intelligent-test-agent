import { serializeMermaidCompactState } from "../compact-metadata";
import { normalizeMermaidHexColor } from "../style-directives";
import {
  flattenMermaidStateNodes,
  mermaidStateDiagramFingerprint,
  type MermaidStateDiagram,
  type MermaidStateNode,
  type MermaidStateScope
} from "./model";
import { validateMermaidState } from "./validator";

function encodedText(value: string): string {
  return value
    .replaceAll("\r", "")
    .split("\n")
    .map((line) => line.trim())
    .join("<br/>")
    .replaceAll("\"", "&quot;")
    .trim();
}

function serializeStateNode(node: MermaidStateNode, depth: number, indentUnit: string): string[] {
  if (node.kind === "start" || node.kind === "end") return [];
  const indent = indentUnit.repeat(depth);
  if (node.kind === "choice" || node.kind === "fork" || node.kind === "join") {
    return [`${indent}state ${node.id} <<${node.kind}>>`];
  }

  const label = encodedText(node.label) || node.id;
  const declaration = label === node.id
    ? `state ${node.id}`
    : `state "${label}" as ${node.id}`;
  const lines = node.childScope
    ? [`${indent}${declaration} {`, ...serializeScope(node.childScope, depth + 1, indentUnit), `${indent}}`]
    : [`${indent}${declaration}`];
  for (const description of node.descriptions) {
    const value = encodedText(description);
    if (value) lines.push(`${indent}${node.id}: ${value}`);
  }
  return lines;
}

function serializeScope(scope: MermaidStateScope, depth: number, indentUnit: string): string[] {
  const lines: string[] = [];
  const indent = indentUnit.repeat(depth);
  if (scope.direction !== "TB") lines.push(`${indent}direction ${scope.direction}`);
  // preservedLines 来自原始 Scope，保留其空白和多行指令相对缩进，不重排未知语句内容。
  for (const preserved of scope.preservedLines) lines.push(preserved);

  scope.regions.forEach((region, regionIndex) => {
    if (regionIndex > 0) lines.push(`${indent}--`);
    for (const node of region.nodes) lines.push(...serializeStateNode(node, depth, indentUnit));
    for (const note of region.notes) {
      const body = note.text.replaceAll("\r", "").split("\n");
      if (body.length <= 1) {
        lines.push(`${indent}note ${note.placement} of ${note.target}: ${body[0]?.trim() ?? ""}`);
      } else {
        lines.push(`${indent}note ${note.placement} of ${note.target}`);
        for (const line of body) lines.push(`${indent}${indentUnit}${line.trim()}`);
        lines.push(`${indent}end note`);
      }
    }
    for (const transition of region.transitions) {
      const source = region.nodes.find((node) => node.id === transition.source);
      const target = region.nodes.find((node) => node.id === transition.target);
      const sourceToken = source?.kind === "start" ? "[*]" : transition.source;
      const targetToken = target?.kind === "end" ? "[*]" : transition.target;
      const label = encodedText(transition.label);
      lines.push(`${indent}${sourceToken} --> ${targetToken}${label ? `: ${label}` : ""}`);
    }
  });
  return lines;
}

function serializeStyles(diagram: MermaidStateDiagram): string[] {
  const lines: string[] = [];
  for (const node of flattenMermaidStateNodes(diagram)) {
    if (node.kind === "start" || node.kind === "end" || !node.style) continue;
    const properties: string[] = [];
    const fill = node.style.fillColor && normalizeMermaidHexColor(node.style.fillColor);
    const stroke = node.style.strokeColor && normalizeMermaidHexColor(node.style.strokeColor);
    const text = node.kind === "state" && node.style.textColor
      ? normalizeMermaidHexColor(node.style.textColor)
      : undefined;
    if (fill) properties.push(`fill:${fill}`);
    if (stroke) properties.push(`stroke:${stroke}`);
    if (text) properties.push(`color:${text}`);
    if (properties.length > 0) lines.push(`style ${node.id} ${properties.join(",")}`);
  }
  return lines;
}

/** 严格校验后才生成源码；调用方还会使用 Mermaid 官方 parser 做第二道语法门禁。 */
export function serializeMermaidState(diagram: MermaidStateDiagram): string {
  if (
    diagram.sourceFormat
    && diagram.sourceFormat.fingerprint === mermaidStateDiagramFingerprint(diagram)
  ) return diagram.sourceFormat.source;

  const issues = validateMermaidState(diagram);
  if (issues.length > 0) {
    throw new Error(`状态图校验失败：${issues.map((issue) => issue.message).join("；")}`);
  }

  const eol = diagram.sourceFormat?.eol ?? "\n";
  const indentUnit = diagram.sourceFormat?.indentUnit || "  ";
  const header = diagram.sourceFormat?.headerRaw ?? diagram.header;
  const lines = [
    header,
    ...(diagram.sourceFormat?.metadataConflict ? [] : serializeMermaidCompactState(diagram)),
    ...serializeScope(diagram.root, 0, indentUnit),
    ...serializeStyles(diagram)
  ];
  const result = lines.join(eol);
  return diagram.sourceFormat?.trailingNewline === true ? `${result}${eol}` : result;
}
