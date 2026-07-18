import { applyMermaidCompactSequence, extractMermaidCompactMarker } from "../compact-metadata";
import { extractMermaidLayout } from "../metadata";
import {
  MERMAID_SEQUENCE_PARTICIPANT_TYPES,
  autonumberFingerprint,
  blockHeaderFingerprint,
  branchHeaderFingerprint,
  groupFingerprint,
  participantFingerprint,
  sequenceDiagramFingerprint,
  sequenceMessages,
  statementFingerprint,
  type MermaidSequenceArrow,
  type MermaidSequenceAutonumber,
  type MermaidSequenceBlock,
  type MermaidSequenceBlockType,
  type MermaidSequenceBranch,
  type MermaidSequenceDiagram,
  type MermaidSequenceGroup,
  type MermaidSequenceLifecycleCreate,
  type MermaidSequenceLifecycleDestroy,
  type MermaidSequenceParticipant,
  type MermaidSequenceParticipantType,
  type MermaidSequenceSourceOrigin,
  type MermaidSequenceSourceOrderItem,
  type MermaidSequenceStatement
} from "./model";

const PARTICIPANT_ID = "[A-Za-z_](?:[A-Za-z0-9_]|-(?=[A-Za-z0-9_]))*";
const PARTICIPANT_PATTERN = new RegExp(
  `^\\s*(participant|actor)\\s+(${PARTICIPANT_ID})(?:@\\{(.*?)\\})?(?:\\s+as\\s+(.+?))?\\s*$`,
  "i"
);
const MESSAGE_PATTERN = new RegExp(
  `^\\s*(${PARTICIPANT_ID})\\s*(<<-->>|<<->>|-->>|->>|--x|--\\)|-->|->|-x|-\\))\\s*([+-])?\\s*(${PARTICIPANT_ID})\\s*:\\s*(.*?)\\s*$`
);
const NOTE_PATTERN = new RegExp(
  `^\\s*note\\s+(left\\s+of|right\\s+of|over)\\s+(${PARTICIPANT_ID})(?:\\s*,\\s*(${PARTICIPANT_ID}))?\\s*:\\s*(.*?)\\s*$`,
  "i"
);
const ACTIVATE_PATTERN = new RegExp(`^\\s*(activate|deactivate)\\s+(${PARTICIPANT_ID})\\s*$`, "i");
const DESTROY_PATTERN = new RegExp(`^\\s*destroy\\s+(${PARTICIPANT_ID})\\s*$`, "i");
const CREATE_PATTERN = new RegExp(
  `^\\s*create\\s+(participant|actor)\\s+(${PARTICIPANT_ID})(?:@\\{(.*?)\\})?(?:\\s+as\\s+(.+?))?\\s*$`,
  "i"
);
const BLOCK_PATTERN = /^\s*(loop|alt|opt|par|critical|break|rect)\b\s*(.*?)\s*$/i;
const ADVANCED_BLOCK_PATTERN = /^\s*par_over\b/i;
const ANY_BLOCK_START_PATTERN = /^\s*(?:loop|alt|opt|par|par_over|critical|break|rect)\b/i;
const STANDARD_PARTICIPANT_TYPES = new Set<string>(MERMAID_SEQUENCE_PARTICIPANT_TYPES);
const CSS_NAMED_COLORS = new Set(`aliceblue antiquewhite aqua aquamarine azure beige bisque black blanchedalmond blue blueviolet brown burlywood cadetblue chartreuse chocolate coral cornflowerblue cornsilk crimson cyan darkblue darkcyan darkgoldenrod darkgray darkgreen darkgrey darkkhaki darkmagenta darkolivegreen darkorange darkorchid darkred darksalmon darkseagreen darkslateblue darkslategray darkslategrey darkturquoise darkviolet deeppink deepskyblue dimgray dimgrey dodgerblue firebrick floralwhite forestgreen fuchsia gainsboro ghostwhite gold goldenrod gray green greenyellow grey honeydew hotpink indianred indigo ivory khaki lavender lavenderblush lawngreen lemonchiffon lightblue lightcoral lightcyan lightgoldenrodyellow lightgray lightgreen lightgrey lightpink lightsalmon lightseagreen lightskyblue lightslategray lightslategrey lightsteelblue lightyellow lime limegreen linen magenta maroon mediumaquamarine mediumblue mediumorchid mediumpurple mediumseagreen mediumslateblue mediumspringgreen mediumturquoise mediumvioletred midnightblue mintcream mistyrose moccasin navajowhite navy oldlace olive olivedrab orange orangered orchid palegoldenrod palegreen paleturquoise palevioletred papayawhip peachpuff peru pink plum powderblue purple rebeccapurple red rosybrown royalblue saddlebrown salmon sandybrown seagreen seashell sienna silver skyblue slateblue slategray slategrey snow springgreen steelblue tan teal thistle tomato transparent turquoise violet wheat white whitesmoke yellow yellowgreen currentcolor`.split(" "));

type ParserContainer = {
  block: MermaidSequenceBlock;
  branch: MermaidSequenceBranch;
};

function lineIndent(line: string): string {
  return line.match(/^\s*/)?.[0] ?? "";
}

function makeOrigin(raw: string, parentId: string, fingerprint: string): MermaidSequenceSourceOrigin {
  return { raw, indent: lineIndent(raw), parentId, fingerprint };
}

function decodeText(value: string): string {
  return value.replace(/<br\s*\/?\s*>/gi, "\n");
}

function participantType(
  declarationKind: string,
  config: string | undefined
): MermaidSequenceParticipantType | null {
  if (!config) return declarationKind.toLowerCase() === "actor" ? "actor" : "participant";
  const keys = [...config.matchAll(/["']?([A-Za-z_][A-Za-z0-9_]*)["']?\s*:/g)].map((match) => match[1]);
  if (keys.some((key) => key?.toLowerCase() !== "type")) return null;
  const match = config.match(/["']?type["']?\s*:\s*["']?([A-Za-z]+)["']?/i);
  const type = match?.[1]?.toLowerCase();
  return type && STANDARD_PARTICIPANT_TYPES.has(type) ? type as MermaidSequenceParticipantType : null;
}

function arrowFromOperator(operator: string): MermaidSequenceArrow {
  return operator as MermaidSequenceArrow;
}

function inferIndentUnit(lines: readonly string[], headerIndex: number): string {
  for (let index = headerIndex + 1; index < lines.length; index += 1) {
    const indent = lineIndent(lines[index] ?? "");
    if (indent) return indent;
  }
  return "  ";
}

function isCssColor(value: string): boolean {
  const normalized = value.trim().toLowerCase();
  if (CSS_NAMED_COLORS.has(normalized) || /^#[0-9a-f]{3,8}$/i.test(normalized)) return true;
  if (/^(?:rgba?|hsla?|hwb|lab|lch|oklab|oklch|color|color-mix)\(/i.test(normalized)) return true;
  try {
    return globalThis.CSS?.supports?.("color", value) === true;
  } catch {
    return false;
  }
}

function parseBox(value: string, sequence: number): MermaidSequenceGroup {
  const rest = value.replace(/^\s*box\b/i, "").trim();
  if (!rest) return { id: `box-${sequence}`, label: "", participantIds: [], sourceOrder: [] };
  const colorAndLabel = rest.match(/^((?:rgba?|hsla?|hwb|lab|lch|oklab|oklch|color|color-mix)\([^)]*\)|#[0-9a-fA-F]{3,8}|[A-Za-z]+)\s+(.+)$/);
  if (colorAndLabel && isCssColor(colorAndLabel[1] ?? "")) {
    return {
      id: `box-${sequence}`,
      color: colorAndLabel[1],
      label: colorAndLabel[2]?.trim() ?? "",
      participantIds: [],
      sourceOrder: []
    };
  }
  return { id: `box-${sequence}`, label: rest, participantIds: [], sourceOrder: [] };
}

/** tokenizer + 显式容器栈：未知高级块整体锁定，避免其内部 end 污染外层 AST。 */
export function parseMermaidSequence(source: string): MermaidSequenceDiagram {
  const eol: "\n" | "\r\n" = source.includes("\r\n") ? "\r\n" : "\n";
  const trailingNewline = source.endsWith("\n");
  const lines = source.split(/\r?\n/);
  const headerIndex = lines.findIndex((line) => /^\s*sequenceDiagram\s*$/i.test(line));
  if (headerIndex < 0) throw new Error("缺少 sequenceDiagram 图头");

  const { layout, consumedLineIndexes } = extractMermaidLayout(lines);
  const compactMetadata = extractMermaidCompactMarker(lines);
  const participants = new Map<string, MermaidSequenceParticipant>();
  const groups: MermaidSequenceGroup[] = [];
  const statements: MermaidSequenceStatement[] = [];
  const sourceOrder: MermaidSequenceSourceOrderItem[] = [];
  const stack: ParserContainer[] = [];
  const skippedMetadataLines: Array<{ index: number; raw: string }> = [];
  let currentGroup: MermaidSequenceGroup | undefined;
  let autonumber: MermaidSequenceAutonumber | undefined;
  let pendingCreate: MermaidSequenceLifecycleCreate | undefined;
  let pendingDestroy: MermaidSequenceLifecycleDestroy | undefined;
  let statementSequence = 0;
  let branchSequence = 0;
  let groupSequence = 0;

  const nextId = (kind: string) => `${kind}-${++statementSequence}`;
  const parentId = () => stack.at(-1)?.branch.id ?? currentGroup?.id ?? "root";
  const currentStatements = () => stack.at(-1)?.branch.statements ?? statements;

  const appendStatement = (statement: MermaidSequenceStatement) => {
    const atRoot = stack.length === 0;
    currentStatements().push(statement);
    if (atRoot && currentGroup) currentGroup.sourceOrder?.push({ kind: "statement", id: statement.id });
    else if (atRoot) sourceOrder.push({ kind: "statement", id: statement.id });
  };

  const appendLocked = (raw: string, reason: string, hidden = false) => {
    const statement: MermaidSequenceStatement = {
      id: nextId("locked"),
      kind: "locked",
      locked: true,
      reason,
      raw,
      hidden
    };
    statement.origin = makeOrigin(raw, parentId(), statementFingerprint(statement));
    appendStatement(statement);
  };

  const upsertParticipant = (
    id: string,
    text = id,
    type: MermaidSequenceParticipantType = "participant",
    declared = false,
    created = false,
    raw?: string
  ) => {
    const existing = participants.get(id);
    if (!existing) {
      const participant: MermaidSequenceParticipant = {
        id,
        text,
        type,
        position: layout[id] ? { ...layout[id] } : { x: 0, y: 0 },
        ...(currentGroup ? { groupId: currentGroup.id } : {}),
        declared,
        ...(created ? { created: true } : {})
      };
      if (raw) participant.origin = makeOrigin(raw, currentGroup?.id ?? "root", participantFingerprint(participant));
      participants.set(id, participant);
      if (currentGroup) currentGroup.participantIds.push(id);
      return participant;
    }
    if (declared && !existing.declared) {
      existing.text = text;
      existing.type = type;
      existing.declared = true;
      existing.groupId = currentGroup?.id;
      existing.created = created || existing.created;
      if (raw) existing.origin = makeOrigin(raw, currentGroup?.id ?? "root", participantFingerprint(existing));
      if (currentGroup && !currentGroup.participantIds.includes(id)) currentGroup.participantIds.push(id);
    } else if (created) {
      existing.text = text;
      existing.type = type;
      existing.created = true;
    }
    return existing;
  };

  const flushPendingLifecycle = () => {
    if (pendingCreate?.origin) appendLocked(pendingCreate.origin.raw, "create 必须紧邻兼容消息");
    if (pendingDestroy?.origin) appendLocked(pendingDestroy.origin.raw, "destroy 必须紧邻兼容消息");
    pendingCreate = undefined;
    pendingDestroy = undefined;
  };

  const parseMessage = (line: string): MermaidSequenceStatement | null => {
    const match = line.match(MESSAGE_PATTERN);
    if (!match) return null;
    const sourceId = match[1] ?? "";
    const operator = match[2] ?? "->>";
    const shortcut = match[3];
    const targetId = match[4] ?? "";
    upsertParticipant(sourceId);
    upsertParticipant(targetId);
    const message: MermaidSequenceStatement = {
      id: nextId("message"),
      kind: "message",
      source: sourceId,
      target: targetId,
      text: decodeText(match[5] ?? ""),
      arrow: arrowFromOperator(operator),
      ...(shortcut === "+" ? { activation: "activate-target" as const } : {}),
      ...(shortcut === "-" ? { activation: "deactivate-source" as const } : {}),
      ...(pendingCreate ? { create: pendingCreate } : {}),
      ...(pendingDestroy ? { destroy: pendingDestroy } : {})
    };
    message.origin = makeOrigin(line, parentId(), statementFingerprint(message));
    pendingCreate = undefined;
    pendingDestroy = undefined;
    return message;
  };

  const sourceLineCount = trailingNewline ? lines.length - 1 : lines.length;
  for (let index = 0; index < sourceLineCount; index += 1) {
    const line = lines[index] ?? "";
    if (index === headerIndex) continue;
    if (consumedLineIndexes.has(index) || compactMetadata.markerLineIndexes.has(index)) {
      skippedMetadataLines.push({ index, raw: line });
      continue;
    }
    const trimmed = line.trim();

    if (!trimmed) {
      appendLocked(line, "空白分隔", true);
      continue;
    }

    if (ADVANCED_BLOCK_PATTERN.test(line)) {
      flushPendingLifecycle();
      let depth = 1;
      let endIndex = index;
      for (let cursor = index + 1; cursor < sourceLineCount; cursor += 1) {
        const candidate = lines[cursor] ?? "";
        if (ANY_BLOCK_START_PATTERN.test(candidate)) depth += 1;
        if (/^\s*end\s*$/i.test(candidate)) depth -= 1;
        endIndex = cursor;
        if (depth === 0) break;
      }
      appendLocked(
        lines.slice(index, endIndex + 1).join(eol),
        "暂不支持 par_over 创建与结构化编辑"
      );
      index = endIndex;
      continue;
    }

    if (trimmed.includes(";")) {
      flushPendingLifecycle();
      appendLocked(line, "暂不支持分号串联语句");
      continue;
    }

    const message = parseMessage(line);
    if (message) {
      appendStatement(message);
      continue;
    }

    if (pendingCreate || pendingDestroy) flushPendingLifecycle();

    const createMatch = line.match(CREATE_PATTERN);
    if (createMatch) {
      const type = participantType(createMatch[1] ?? "participant", createMatch[3]);
      if (!type) {
        appendLocked(line, "参与者配置包含首批未支持字段");
        continue;
      }
      const id = createMatch[2] ?? "";
      const text = decodeText((createMatch[4] ?? id).trim());
      const create: MermaidSequenceLifecycleCreate = { participantId: id, type, text };
      create.origin = makeOrigin(line, parentId(), JSON.stringify({ participantId: id, type, text }));
      pendingCreate = create;
      upsertParticipant(id, text, type, false, true);
      continue;
    }

    const destroyMatch = line.match(DESTROY_PATTERN);
    if (destroyMatch) {
      const participantId = destroyMatch[1] ?? "";
      const destroy: MermaidSequenceLifecycleDestroy = { participantId };
      destroy.origin = makeOrigin(line, parentId(), JSON.stringify({ participantId }));
      pendingDestroy = destroy;
      upsertParticipant(participantId);
      continue;
    }

    if (/^\s*box\b/i.test(line) && stack.length === 0 && !currentGroup) {
      const group = parseBox(line, ++groupSequence);
      group.openOrigin = makeOrigin(line, "root", groupFingerprint(group));
      groups.push(group);
      sourceOrder.push({ kind: "group", id: group.id });
      currentGroup = group;
      continue;
    }

    if (/^\s*end\s*$/i.test(line) && currentGroup && stack.length === 0) {
      currentGroup.closeOrigin = makeOrigin(line, "root", groupFingerprint(currentGroup));
      currentGroup = undefined;
      continue;
    }

    const participantMatch = line.match(PARTICIPANT_PATTERN);
    if (participantMatch) {
      const type = participantType(participantMatch[1] ?? "participant", participantMatch[3]);
      if (!type) {
        appendLocked(line, "参与者配置包含首批未支持字段");
        continue;
      }
      const id = participantMatch[2] ?? "";
      const wasDeclared = participants.get(id)?.declared === true;
      if (wasDeclared) {
        appendLocked(line, "重复参与者声明暂以源码锁定保留");
        continue;
      }
      upsertParticipant(id, decodeText((participantMatch[4] ?? id).trim()), type, true, false, line);
      if (currentGroup && !wasDeclared) currentGroup.sourceOrder?.push({ kind: "participant", id });
      else if (!currentGroup && !wasDeclared) sourceOrder.push({ kind: "participant", id });
      continue;
    }

    const autonumberMatch = line.match(/^\s*autonumber(?:\s+(off|\d+))?(?:\s+(\d+))?\s*$/i);
    if (autonumberMatch) {
      if (autonumber) {
        appendLocked(line, "额外 autonumber 开关暂以源码锁定保留");
        continue;
      }
      autonumber = autonumberMatch[1]?.toLowerCase() === "off"
        ? { enabled: false }
        : {
            enabled: true,
            ...(autonumberMatch[1] ? { start: Number(autonumberMatch[1]) } : {}),
            ...(autonumberMatch[2] ? { step: Number(autonumberMatch[2]) } : {})
          };
      autonumber.origin = makeOrigin(line, "root", autonumberFingerprint(autonumber));
      sourceOrder.push({ kind: "autonumber" });
      continue;
    }

    const blockMatch = line.match(BLOCK_PATTERN);
    if (blockMatch) {
      const blockType = (blockMatch[1]?.toLowerCase() ?? "loop") as MermaidSequenceBlockType;
      const label = decodeText(blockMatch[2] ?? "");
      const branch: MermaidSequenceBranch = {
        id: `branch-${++branchSequence}`,
        label,
        keyword: "body",
        statements: []
      };
      const block: MermaidSequenceBlock = {
        id: nextId("block"),
        kind: "block",
        blockType,
        branches: [branch]
      };
      block.origin = makeOrigin(line, parentId(), blockHeaderFingerprint(block));
      appendStatement(block);
      stack.push({ block, branch });
      continue;
    }

    const branchMatch = line.match(/^\s*(else|and|option)\b\s*(.*?)\s*$/i);
    if (branchMatch && stack.length > 0) {
      const container = stack.at(-1)!;
      const keyword = branchMatch[1]?.toLowerCase() as "else" | "and" | "option";
      const compatible = (container.block.blockType === "alt" && keyword === "else")
        || (container.block.blockType === "par" && keyword === "and")
        || (container.block.blockType === "critical" && keyword === "option");
      if (!compatible) {
        appendLocked(line, `${keyword} 与当前片段不匹配`);
        continue;
      }
      const branch: MermaidSequenceBranch = {
        id: `branch-${++branchSequence}`,
        label: decodeText(branchMatch[2] ?? ""),
        keyword,
        statements: []
      };
      branch.origin = makeOrigin(line, container.block.id, branchHeaderFingerprint(branch));
      container.block.branches.push(branch);
      container.branch = branch;
      continue;
    }

    if (/^\s*end\s*$/i.test(line)) {
      const container = stack.pop();
      if (!container) {
        appendLocked(line, "没有匹配起始片段的 end");
        continue;
      }
      container.block.endOrigin = makeOrigin(line, container.block.origin?.parentId ?? "root", "end");
      continue;
    }

    const noteMatch = line.match(NOTE_PATTERN);
    if (noteMatch) {
      const first = noteMatch[2] ?? "";
      const second = noteMatch[3];
      upsertParticipant(first);
      if (second) upsertParticipant(second);
      const placementToken = noteMatch[1]?.toLowerCase().replace(/\s+/g, " ");
      const note: MermaidSequenceStatement = {
        id: nextId("note"),
        kind: "note",
        placement: placementToken === "left of" ? "left" : placementToken === "right of" ? "right" : "over",
        participants: second ? [first, second] : [first],
        text: decodeText(noteMatch[4] ?? "")
      };
      note.origin = makeOrigin(line, parentId(), statementFingerprint(note));
      appendStatement(note);
      continue;
    }

    const activationMatch = line.match(ACTIVATE_PATTERN);
    if (activationMatch) {
      const participantId = activationMatch[2] ?? "";
      upsertParticipant(participantId);
      const activation: MermaidSequenceStatement = {
        id: nextId("activation"),
        kind: "activation",
        participantId,
        active: activationMatch[1]?.toLowerCase() === "activate"
      };
      activation.origin = makeOrigin(line, parentId(), statementFingerprint(activation));
      appendStatement(activation);
      continue;
    }

    if (/^\s*%%(?!@)/.test(line)) {
      const comment: MermaidSequenceStatement = {
        id: nextId("comment"),
        kind: "comment",
        text: line.replace(/^\s*%%\s?/, "")
      };
      comment.origin = makeOrigin(line, parentId(), statementFingerprint(comment));
      appendStatement(comment);
      continue;
    }

    if (line.includes("()")) {
      appendLocked(line, "暂不支持中央连接创建与结构化编辑");
      continue;
    }
    if (/[-<>()|\\/]{2,}/.test(line) && line.includes(":")) {
      appendLocked(line, "暂不支持半箭头创建与结构化编辑");
      continue;
    }
    appendLocked(line, "当前语法仅支持源码无损保留");
  }

  flushPendingLifecycle();
  while (stack.length > 0) {
    // 损坏结构保留已解析草稿，由官方 parser 在打开/应用边界给出错误；这里不臆造 end。
    stack.pop();
  }

  const diagram: MermaidSequenceDiagram = {
    kind: "sequenceDiagram",
    participants: [...participants.values()],
    groups,
    ...(autonumber ? { autonumber } : {}),
    statements,
    messages: sequenceMessages(statements),
    preservedLines: [],
    preservedSegments: [],
    sourceOrder
  };

  // box 在起始行解析时尚未收集成员；完成扫描后再固定语义指纹，避免编辑相邻消息时误重写 box。
  for (const group of groups) {
    const fingerprint = groupFingerprint(group);
    if (group.openOrigin) group.openOrigin.fingerprint = fingerprint;
    if (group.closeOrigin) group.closeOrigin.fingerprint = fingerprint;
  }

  const compactApplied = compactMetadata.encoded !== null
    ? applyMermaidCompactSequence(diagram, compactMetadata.encoded)
    : false;
  const compactConflict = compactMetadata.markerLineIndexes.size > 0 && !compactApplied;
  if (compactConflict) {
    const raw = skippedMetadataLines
      .sort((left, right) => left.index - right.index)
      .map(({ raw: marker }) => marker)
      .join(eol);
    if (raw) {
      const locked: MermaidSequenceStatement = {
        id: nextId("locked"),
        kind: "locked",
        locked: true,
        reason: "紧凑坐标 metadata 无法校验，已原样保留",
        raw
      };
      locked.origin = makeOrigin(raw, "root", statementFingerprint(locked));
      statements.unshift(locked);
      sourceOrder.unshift({ kind: "statement", id: locked.id });
    }
  }
  diagram.messages = sequenceMessages(statements);
  diagram.preservedLines = diagram.statements
    .filter((statement) => statement.kind === "locked" && !statement.hidden)
    .flatMap((statement) => statement.kind === "locked" ? statement.raw.split(/\r?\n/) : []);
  diagram.sourceFormat = {
    source,
    headerRaw: lines[headerIndex] ?? "sequenceDiagram",
    eol,
    trailingNewline,
    indentUnit: inferIndentUnit(lines, headerIndex),
    fingerprint: "",
    ...(consumedLineIndexes.size > 0 && !compactApplied && !compactConflict ? { metadataNeedsRewrite: true } : {}),
    ...(compactConflict ? { metadataConflict: true } : {})
  };
  diagram.sourceFormat.fingerprint = sequenceDiagramFingerprint(diagram);
  return diagram;
}
