import { applyMermaidCompactSequence, extractMermaidCompactMarker } from "../compact-metadata";
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
  const compactMetadata = extractMermaidCompactMarker(lines);
  const participants = new Map<string, MermaidSequenceParticipant & { explicit: boolean }>();
  const messages: MermaidSequenceDiagram["messages"] = [];
  // 与 Flow 一致先保留元数据原文，避免新 marker 损坏时误吞可用于回退的旧坐标。
  const preservedRecords: Array<{ sourceIndex: number; beforeEditableIndex: number; line: string }> = [];
  let preservedBlockDepth = 0;

  const preserveLine = (line: string, sourceIndex: number) => {
    preservedRecords.push({ sourceIndex, beforeEditableIndex: messages.length, line });
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
    if (index === headerIndex) return;
    const trimmed = line.trim();
    const startsComplexBlock = /^(?:loop|alt|opt|par|critical|break|rect|box)\b/i.test(trimmed);
    if (preservedBlockDepth > 0) {
      preserveLine(line, index);
      if (startsComplexBlock) preservedBlockDepth += 1;
      if (/^end\s*$/i.test(trimmed)) preservedBlockDepth -= 1;
      return;
    }
    if (!trimmed) return;
    if (startsComplexBlock) {
      preservedBlockDepth = 1;
      preserveLine(line, index);
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
    preserveLine(line, index);
  });

  const diagram: MermaidSequenceDiagram = {
    kind: "sequenceDiagram",
    participants: Array.from(participants.values(), ({ explicit: _explicit, ...participant }) => participant),
    messages,
    preservedLines: preservedRecords.map((record) => record.line),
    preservedSegments: []
  };
  const compactApplied = compactMetadata.encoded !== null
    ? applyMermaidCompactSequence(diagram, compactMetadata.encoded)
    : false;
  const compactConflict = compactMetadata.markerLineIndexes.size > 0 && !compactApplied;
  const removedIndexes = new Set<number>();
  if (!compactConflict) {
    for (const index of consumedLineIndexes) removedIndexes.add(index);
  }
  if (compactApplied) {
    for (const index of compactMetadata.markerLineIndexes) removedIndexes.add(index);
  }
  const remainingRecords = preservedRecords.filter((record) => !removedIndexes.has(record.sourceIndex));
  diagram.preservedLines = remainingRecords.map((record) => record.line);
  for (const record of remainingRecords) {
    const current = diagram.preservedSegments!.at(-1);
    if (current?.beforeEditableIndex === record.beforeEditableIndex) current.lines.push(record.line);
    else diagram.preservedSegments!.push({ beforeEditableIndex: record.beforeEditableIndex, lines: [record.line] });
  }
  return diagram;
}
