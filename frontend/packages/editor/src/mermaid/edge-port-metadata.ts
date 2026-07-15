import type { MermaidEdge } from "./model";

const EDGE_PORTS_MARKER = "%% editor-edge-ports:";
const PORT_HANDLE_PATTERN = /^(?:source|target)-[0-2]$/;

export type MermaidEdgePortMetadataEntry = Required<
  Pick<MermaidEdge, "source" | "target" | "sourceHandle" | "targetHandle">
>;

export type MermaidEdgePortExtraction = {
  entries: MermaidEdgePortMetadataEntry[];
  consumedLineIndexes: Set<number>;
  rawLines: string[];
};

export function isMermaidPortHandle(value: unknown): value is string {
  return typeof value === "string" && PORT_HANDLE_PATTERN.test(value);
}

function isEntry(value: unknown): value is MermaidEdgePortMetadataEntry {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.source === "string" &&
    candidate.source.length > 0 &&
    typeof candidate.target === "string" &&
    candidate.target.length > 0 &&
    isMermaidPortHandle(candidate.sourceHandle) &&
    isMermaidPortHandle(candidate.targetHandle)
  );
}

/**
 * 读取固定端口注释。只有完整、结构合法且每条边键唯一的 JSON 才会被消费；
 * 损坏内容继续由 parser 当作普通 Mermaid 注释原样保留。
 */
export function extractMermaidEdgePorts(lines: string[]): MermaidEdgePortExtraction {
  const markerIndex = lines.findIndex((line) => line.trim() === EDGE_PORTS_MARKER);
  if (markerIndex < 0) return { entries: [], consumedLineIndexes: new Set(), rawLines: [] };

  const jsonLines: string[] = [];
  const candidateIndexes = [markerIndex];
  for (let index = markerIndex + 1; index < lines.length; index += 1) {
    const match = lines[index]?.match(/^\s*%%\s?(.*)$/);
    if (!match) break;
    jsonLines.push(match[1] ?? "");
    candidateIndexes.push(index);
    try {
      const parsed: unknown = JSON.parse(jsonLines.join("\n"));
      if (!Array.isArray(parsed) || !parsed.every(isEntry)) continue;
      const keys = parsed.map((entry) => `${entry.source}\u0000${entry.target}`);
      if (new Set(keys).size !== keys.length) {
        return { entries: [], consumedLineIndexes: new Set(), rawLines: [] };
      }
      return {
        entries: parsed.map((entry) => ({ ...entry })),
        consumedLineIndexes: new Set(candidateIndexes),
        rawLines: candidateIndexes.map((candidateIndex) => lines[candidateIndex] ?? "")
      };
    } catch {
      // JSON 尚未收集完整，继续读取相邻 Mermaid 注释行。
    }
  }
  return { entries: [], consumedLineIndexes: new Set(), rawLines: [] };
}

/** 只为可唯一匹配且同时具有两个合法固定端口的边生成私有注释。 */
export function serializeMermaidEdgePorts(edges: ReadonlyArray<MermaidEdge>): string[] {
  const counts = new Map<string, number>();
  for (const edge of edges) {
    const key = `${edge.source}\u0000${edge.target}`;
    counts.set(key, (counts.get(key) ?? 0) + 1);
  }
  const entries: MermaidEdgePortMetadataEntry[] = edges.flatMap((edge) => {
    const key = `${edge.source}\u0000${edge.target}`;
    if (
      counts.get(key) !== 1 ||
      !isMermaidPortHandle(edge.sourceHandle) ||
      !isMermaidPortHandle(edge.targetHandle)
    ) return [];
    return [{
      source: edge.source,
      target: edge.target,
      sourceHandle: edge.sourceHandle,
      targetHandle: edge.targetHandle
    }];
  });
  if (!entries.length) return [];
  return [EDGE_PORTS_MARKER, ...JSON.stringify(entries, null, 2).split("\n").map((line) => `%% ${line}`)];
}
