import type { MermaidPosition } from "./model";

const LAYOUT_MARKER = "%% editor-layout:";

export type MermaidLayoutExtraction = {
  layout: Record<string, MermaidPosition>;
  consumedLineIndexes: Set<number>;
};

function isPosition(value: unknown): value is MermaidPosition {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Record<string, unknown>;
  return Number.isFinite(candidate.x) && Number.isFinite(candidate.y);
}

/**
 * 读取编辑器私有坐标注释。损坏的 JSON 不抛错也不吞行，交由 preservedLines 原样保留。
 */
export function extractMermaidLayout(lines: string[]): MermaidLayoutExtraction {
  const markerIndex = lines.findIndex((line) => line.trim() === LAYOUT_MARKER);
  if (markerIndex < 0) {
    return { layout: {}, consumedLineIndexes: new Set() };
  }

  const jsonLines: string[] = [];
  const candidateIndexes: number[] = [markerIndex];
  for (let index = markerIndex + 1; index < lines.length; index += 1) {
    const match = lines[index]?.match(/^\s*%%\s?(.*)$/);
    if (!match) break;
    jsonLines.push(match[1] ?? "");
    candidateIndexes.push(index);
    try {
      const parsed: unknown = JSON.parse(jsonLines.join("\n"));
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) continue;
      const layout: Record<string, MermaidPosition> = {};
      for (const [id, position] of Object.entries(parsed)) {
        if (isPosition(position)) {
          layout[id] = { x: position.x, y: position.y };
        }
      }
      return { layout, consumedLineIndexes: new Set(candidateIndexes) };
    } catch {
      // JSON 尚未收集完整，继续读取相邻 Mermaid 注释行。
    }
  }
  return { layout: {}, consumedLineIndexes: new Set() };
}

/** 生成稳定、可读且可被 Mermaid 忽略的坐标 metadata。 */
export function serializeMermaidLayout(nodes: ReadonlyArray<{ id: string; position: MermaidPosition }>): string[] {
  if (!nodes.length) return [];
  const layout = Object.fromEntries(
    nodes.map((node) => [node.id, { x: Math.round(node.position.x), y: Math.round(node.position.y) }])
  );
  return [LAYOUT_MARKER, ...JSON.stringify(layout, null, 2).split("\n").map((line) => `%% ${line}`)];
}
