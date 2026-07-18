import type { MermaidGraph } from "./model";
import { cloneMermaidGraph } from "./model";
import { parseMermaidFlowchart } from "./parser";
import { serializeMermaidGraph } from "./serializer";
import type { MermaidSequenceDiagram } from "./sequence/model";
import { cloneMermaidSequence } from "./sequence/model";
import { parseMermaidSequence } from "./sequence/parser";
import { serializeMermaidSequence } from "./sequence/serializer";
import type { MermaidStateDiagram } from "./state/model";
import { cloneMermaidStateDiagram } from "./state/model";
import { parseMermaidState } from "./state/parser";
import { serializeMermaidState } from "./state/serializer";

export type MermaidEditableDiagram = MermaidGraph | MermaidSequenceDiagram | MermaidStateDiagram;

export function cloneMermaidDiagram(diagram: MermaidEditableDiagram): MermaidEditableDiagram {
  if (diagram.kind === "sequenceDiagram") return cloneMermaidSequence(diagram);
  if (diagram.kind === "stateDiagram") return cloneMermaidStateDiagram(diagram);
  return cloneMermaidGraph(diagram);
}

export function parseMermaidDiagram(source: string): MermaidEditableDiagram {
  if (/^\s*sequenceDiagram\b/im.test(source)) return parseMermaidSequence(source);
  if (/^\s*stateDiagram(?:-v2)?\b/im.test(source)) return parseMermaidState(source);
  if (/^\s*(?:flowchart|graph)\b/im.test(source)) return parseMermaidFlowchart(source);
  throw new Error("仅支持 flowchart、graph、sequenceDiagram 或 stateDiagram 可视化编辑");
}

export function serializeMermaidDiagram(diagram: MermaidEditableDiagram): string {
  if (diagram.kind === "sequenceDiagram") return serializeMermaidSequence(diagram);
  if (diagram.kind === "stateDiagram") return serializeMermaidState(diagram);
  return serializeMermaidGraph(diagram);
}
