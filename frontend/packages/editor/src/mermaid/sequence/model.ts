import type { MermaidPosition, MermaidPreservedSegment } from "../model";

export type MermaidSequenceParticipantType = "participant" | "actor";
export type MermaidSequenceMessageType = "solid" | "dotted" | "open" | "dotted-open";

export type MermaidSequenceParticipant = {
  id: string;
  text: string;
  type: MermaidSequenceParticipantType;
  position: MermaidPosition;
};

export type MermaidSequenceMessage = {
  id: string;
  source: string;
  target: string;
  text: string;
  type: MermaidSequenceMessageType;
};

export type MermaidSequenceDiagram = {
  kind: "sequenceDiagram";
  participants: MermaidSequenceParticipant[];
  messages: MermaidSequenceMessage[];
  /** 暂不参与可视化的 Note、activate、loop、alt 等语句原样保留。 */
  preservedLines: string[];
  preservedSegments?: MermaidPreservedSegment[];
};

export function cloneMermaidSequence(diagram: MermaidSequenceDiagram): MermaidSequenceDiagram {
  return {
    kind: "sequenceDiagram",
    participants: diagram.participants.map((participant) => ({
      ...participant,
      position: { ...participant.position }
    })),
    messages: diagram.messages.map((message) => ({ ...message })),
    preservedLines: [...diagram.preservedLines],
    preservedSegments: diagram.preservedSegments?.map((segment) => ({
      beforeEditableIndex: segment.beforeEditableIndex,
      lines: [...segment.lines]
    }))
  };
}
