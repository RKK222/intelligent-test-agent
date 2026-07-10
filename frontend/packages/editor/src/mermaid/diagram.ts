import type { MermaidGraph } from "./model";
import { cloneMermaidGraph } from "./model";
import { parseMermaidFlowchart } from "./parser";
import { serializeMermaidGraph } from "./serializer";
import type { MermaidSequenceDiagram } from "./sequence/model";
import { cloneMermaidSequence } from "./sequence/model";
import { parseMermaidSequence } from "./sequence/parser";
import { serializeMermaidSequence } from "./sequence/serializer";

export type MermaidEditableDiagram = MermaidGraph | MermaidSequenceDiagram;

export function cloneMermaidDiagram(diagram: MermaidEditableDiagram): MermaidEditableDiagram {
  return diagram.kind === "sequenceDiagram"
    ? cloneMermaidSequence(diagram)
    : cloneMermaidGraph(diagram);
}

export function parseMermaidDiagram(source: string): MermaidEditableDiagram {
  if (/^\s*sequenceDiagram\b/im.test(source)) return parseMermaidSequence(source);
  if (/^\s*(?:flowchart|graph)\b/im.test(source)) return parseMermaidFlowchart(source);
  throw new Error("仅支持 flowchart、graph 或 sequenceDiagram 可视化编辑");
}

export function serializeMermaidDiagram(diagram: MermaidEditableDiagram): string {
  return diagram.kind === "sequenceDiagram"
    ? serializeMermaidSequence(diagram)
    : serializeMermaidGraph(diagram);
}
