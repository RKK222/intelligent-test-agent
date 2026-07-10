import { MarkerType, type Connection, type Edge, type Node, type XYPosition } from "@vue-flow/core";
import { cloneMermaidSequence, type MermaidSequenceDiagram, type MermaidSequenceMessageType, type MermaidSequenceParticipantType } from "../model";

export type SequenceFlowNodeData = {
  text: string;
  participantType: MermaidSequenceParticipantType;
};

export type SequenceFlowEdgeData = {
  order: number;
  text: string;
  messageType: MermaidSequenceMessageType;
};

export type SequenceFlowNode = Node<SequenceFlowNodeData, Record<string, never>, "sequence-participant">;
export type SequenceFlowEdge = Edge<SequenceFlowEdgeData, Record<string, never>, "sequence-message">;

export function toSequenceFlowNodes(diagram: MermaidSequenceDiagram): SequenceFlowNode[] {
  return diagram.participants.map((participant) => ({
    id: participant.id,
    type: "sequence-participant",
    position: { ...participant.position },
    data: { text: participant.text, participantType: participant.type },
    ariaLabel: `${participant.type === "actor" ? "角色" : "参与者"} ${participant.text}`
  }));
}

export function toSequenceFlowEdges(diagram: MermaidSequenceDiagram): SequenceFlowEdge[] {
  return diagram.messages.map((message, order) => ({
    id: message.id,
    type: "sequence-message",
    source: message.source,
    target: message.target,
    label: message.text,
    markerEnd:
      message.type === "open" || message.type === "dotted-open"
        ? MarkerType.Arrow
        : MarkerType.ArrowClosed,
    style: message.type === "dotted" || message.type === "dotted-open" ? { strokeDasharray: "5 4" } : undefined,
    data: { order, text: message.text, messageType: message.type },
    ariaLabel: `消息 ${order + 1}：${message.source} 到 ${message.target} ${message.text}`
  }));
}

export function applySequenceFlowPositions(
  diagram: MermaidSequenceDiagram,
  positions: ReadonlyArray<{ id: string; position: XYPosition }>
): MermaidSequenceDiagram {
  const next = cloneMermaidSequence(diagram);
  const byId = new Map(positions.map((item) => [item.id, item.position]));
  next.participants = next.participants.map((participant) => {
    const position = byId.get(participant.id);
    return position ? { ...participant, position: { x: position.x, y: position.y } } : participant;
  });
  return next;
}

/** Sequence 允许同一参与者之间出现多条消息，因此连接事件始终追加到有序列表末尾。 */
export function appendSequenceMessage(
  diagram: MermaidSequenceDiagram,
  connection: Pick<Connection, "source" | "target">
): MermaidSequenceDiagram {
  if (!connection.source || !connection.target) return diagram;
  const next = cloneMermaidSequence(diagram);
  const usedIds = new Set(next.messages.map((message) => message.id));
  let sequence = next.messages.length + 1;
  while (usedIds.has(`message-${sequence}`)) sequence += 1;
  next.messages.push({
    id: `message-${sequence}`,
    source: connection.source,
    target: connection.target,
    text: "新消息",
    type: "solid"
  });
  return next;
}
