import { cloneMermaidSequence, type MermaidSequenceDiagram } from "./model";

/** Sequence 参与者按声明顺序水平排布，消息顺序由边层单独表达。 */
export function autoLayoutMermaidSequence(diagram: MermaidSequenceDiagram): MermaidSequenceDiagram {
  const result = cloneMermaidSequence(diagram);
  result.participants.forEach((participant, index) => {
    participant.position = { x: 80 + index * 220, y: 70 };
  });
  return result;
}
