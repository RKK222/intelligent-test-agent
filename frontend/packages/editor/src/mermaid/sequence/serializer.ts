import { serializeMermaidCompactSequence } from "../compact-metadata";
import type { MermaidSequenceDiagram, MermaidSequenceMessageType } from "./model";

function operator(type: MermaidSequenceMessageType): string {
  if (type === "dotted") return "-->>";
  if (type === "open") return "->";
  if (type === "dotted-open") return "-->";
  return "->>";
}

function inline(value: string): string {
  return value.replaceAll("\r", " ").replaceAll("\n", " ").trim();
}

export function serializeMermaidSequence(diagram: MermaidSequenceDiagram): string {
  const lines = ["sequenceDiagram", ...serializeMermaidCompactSequence(diagram)];
  for (const participant of diagram.participants) {
    const label = inline(participant.text);
    lines.push(`${participant.type} ${participant.id}${label && label !== participant.id ? ` as ${label}` : ""}`);
  }
  const serializeMessage = (message: MermaidSequenceDiagram["messages"][number]) =>
    `${message.source}${operator(message.type)}${message.target}: ${inline(message.text)}`;
  if (diagram.preservedSegments?.length) {
    for (let index = 0; index <= diagram.messages.length; index += 1) {
      for (const segment of diagram.preservedSegments) {
        if (Math.min(segment.beforeEditableIndex, diagram.messages.length) === index) lines.push(...segment.lines);
      }
      const message = diagram.messages[index];
      if (message) lines.push(serializeMessage(message));
    }
  } else {
    lines.push(...diagram.messages.map(serializeMessage), ...diagram.preservedLines);
  }
  return `${lines.join("\n").trimEnd()}\n`;
}
