import { extractMermaidLayout } from "../metadata";
import type {
  MermaidSequenceDiagram,
  MermaidSequenceMessageType,
  MermaidSequenceParticipant,
  MermaidSequenceParticipantType
} from "./model";

// 连字符只允许出现在标识字符之间，避免把 `A-->>B` 的第一个 `-` 吞进参与者 ID。
const PARTICIPANT_ID = "[A-Za-z_](?:[A-Za-z0-9_]|-(?=[A-Za-z0-9_]))*";
const PARTICIPANT_PATTERN = new RegExp(`^\\s*(participant|actor)\\s+(${PARTICIPANT_ID})(?:\\s+as\\s+(.+?))?\\s*$`, "i");
const MESSAGE_PATTERN = new RegExp(`^\\s*(${PARTICIPANT_ID})\\s*(-->>|-->|->>|->)\\s*(${PARTICIPANT_ID})\\s*:\\s*(.*?)\\s*$`);

function messageType(operator: string): MermaidSequenceMessageType {
  if (operator === "-->>") return "dotted";
  if (operator === "->") return "open";
  if (operator === "-->") return "dotted-open";
  return "solid";
}

/** 解析基础 sequenceDiagram，同时保留复杂控制语句供序列化原样写回。 */
export function parseMermaidSequence(source: string): MermaidSequenceDiagram {
  const lines = source.replaceAll("\r\n", "\n").split("\n");
  const headerIndex = lines.findIndex((line) => /^\s*sequenceDiagram\s*$/i.test(line));
  if (headerIndex < 0) throw new Error("缺少 sequenceDiagram 图头");

  const { layout, consumedLineIndexes } = extractMermaidLayout(lines);
  const participants = new Map<string, MermaidSequenceParticipant & { explicit: boolean }>();
  const messages: MermaidSequenceDiagram["messages"] = [];
  const preservedLines: string[] = [];
  const preservedSegments: NonNullable<MermaidSequenceDiagram["preservedSegments"]> = [];
  let preservedBlockDepth = 0;

  const preserveLine = (line: string) => {
    preservedLines.push(line);
    const anchor = messages.length;
    const current = preservedSegments.at(-1);
    if (current?.beforeEditableIndex === anchor) current.lines.push(line);
    else preservedSegments.push({ beforeEditableIndex: anchor, lines: [line] });
  };

  const upsertParticipant = (
    id: string,
    text = id,
    type: MermaidSequenceParticipantType = "participant",
    explicit = false
  ) => {
    const existing = participants.get(id);
    if (!existing || (explicit && !existing.explicit)) {
      participants.set(id, {
        id,
        text,
        type,
        position: layout[id] ? { ...layout[id] } : { x: 0, y: 0 },
        explicit
      });
    }
  };

  lines.forEach((line, index) => {
    if (index === headerIndex || consumedLineIndexes.has(index)) return;
    const trimmed = line.trim();
    const startsComplexBlock = /^(?:loop|alt|opt|par|critical|break|rect|box)\b/i.test(trimmed);
    if (preservedBlockDepth > 0) {
      preserveLine(line);
      if (startsComplexBlock) preservedBlockDepth += 1;
      if (/^end\s*$/i.test(trimmed)) preservedBlockDepth -= 1;
      return;
    }
    if (!trimmed) return;
    if (startsComplexBlock) {
      preservedBlockDepth = 1;
      preserveLine(line);
      return;
    }
    const participant = line.match(PARTICIPANT_PATTERN);
    if (participant) {
      const id = participant[2] ?? "";
      upsertParticipant(
        id,
        (participant[3] ?? id).trim(),
        (participant[1]?.toLowerCase() ?? "participant") as MermaidSequenceParticipantType,
        true
      );
      return;
    }
    const message = line.match(MESSAGE_PATTERN);
    if (message) {
      const sourceId = message[1] ?? "";
      const targetId = message[3] ?? "";
      upsertParticipant(sourceId);
      upsertParticipant(targetId);
      messages.push({
        id: `message-${messages.length + 1}`,
        source: sourceId,
        target: targetId,
        text: message[4] ?? "",
        type: messageType(message[2] ?? "->>")
      });
      return;
    }
    preserveLine(line);
  });

  return {
    kind: "sequenceDiagram",
    participants: Array.from(participants.values(), ({ explicit: _explicit, ...participant }) => participant),
    messages,
    preservedLines,
    preservedSegments
  };
}
