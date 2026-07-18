import type { MermaidPosition, MermaidPreservedSegment } from "../model";

export const MERMAID_SEQUENCE_PARTICIPANT_TYPES = [
  "participant",
  "actor",
  "boundary",
  "control",
  "entity",
  "database",
  "collections",
  "queue"
] as const;

export const MERMAID_SEQUENCE_ARROWS = [
  "->",
  "-->",
  "->>",
  "-->>",
  "<<->>",
  "<<-->>",
  "-x",
  "--x",
  "-)",
  "--)"
] as const;

export type MermaidSequenceParticipantType = typeof MERMAID_SEQUENCE_PARTICIPANT_TYPES[number];
export type MermaidSequenceArrow = typeof MERMAID_SEQUENCE_ARROWS[number];
/** @deprecated 使用 MermaidSequenceArrow；保留别名避免破坏包内旧调用。 */
export type MermaidSequenceMessageType = MermaidSequenceArrow;
export type MermaidSequenceActivationShortcut = "activate-target" | "deactivate-source";
export type MermaidSequenceBlockType = "loop" | "alt" | "opt" | "par" | "critical" | "break" | "rect";

/**
 * 可编辑实体的源码锚点。语义指纹未变化且父容器未移动时，serializer 直接复用 raw，
 * 从而保留原大小写、空格与快捷写法。
 */
export type MermaidSequenceSourceOrigin = {
  raw: string;
  indent: string;
  parentId: string;
  fingerprint: string;
};

export type MermaidSequenceParticipant = {
  id: string;
  text: string;
  type: MermaidSequenceParticipantType;
  position: MermaidPosition;
  groupId?: string;
  /** 隐式参与者不生成顶层声明；create 参与者由消息生命周期语句生成。 */
  declared: boolean;
  created?: boolean;
  origin?: MermaidSequenceSourceOrigin;
};

export type MermaidSequenceGroup = {
  id: string;
  label: string;
  color?: string;
  participantIds: string[];
  /** box 起止行之间的声明、空行和注释顺序。 */
  sourceOrder?: MermaidSequenceSourceOrderItem[];
  openOrigin?: MermaidSequenceSourceOrigin;
  closeOrigin?: MermaidSequenceSourceOrigin;
};

export type MermaidSequenceAutonumber = {
  enabled: boolean;
  start?: number;
  step?: number;
  origin?: MermaidSequenceSourceOrigin;
};

export type MermaidSequenceLifecycleCreate = {
  participantId: string;
  type: MermaidSequenceParticipantType;
  text: string;
  origin?: MermaidSequenceSourceOrigin;
};

export type MermaidSequenceLifecycleDestroy = {
  participantId: string;
  origin?: MermaidSequenceSourceOrigin;
};

type MermaidSequenceStatementBase = {
  id: string;
  origin?: MermaidSequenceSourceOrigin;
};

export type MermaidSequenceMessage = MermaidSequenceStatementBase & {
  kind: "message";
  source: string;
  target: string;
  text: string;
  arrow: MermaidSequenceArrow;
  activation?: MermaidSequenceActivationShortcut;
  create?: MermaidSequenceLifecycleCreate;
  destroy?: MermaidSequenceLifecycleDestroy;
};

export type MermaidSequenceNote = MermaidSequenceStatementBase & {
  kind: "note";
  placement: "left" | "right" | "over";
  participants: string[];
  text: string;
};

export type MermaidSequenceActivation = MermaidSequenceStatementBase & {
  kind: "activation";
  participantId: string;
  active: boolean;
};

export type MermaidSequenceComment = MermaidSequenceStatementBase & {
  kind: "comment";
  text: string;
};

export type MermaidSequenceLocked = MermaidSequenceStatementBase & {
  kind: "locked";
  locked: true;
  reason: string;
  /** 仅作为纯文本展示和回写，界面不得用 v-html 渲染。 */
  raw: string;
  hidden?: boolean;
};

export type MermaidSequenceBranch = {
  id: string;
  label: string;
  keyword: "body" | "else" | "and" | "option";
  statements: MermaidSequenceStatement[];
  origin?: MermaidSequenceSourceOrigin;
};

export type MermaidSequenceBlock = MermaidSequenceStatementBase & {
  kind: "block";
  blockType: MermaidSequenceBlockType;
  branches: MermaidSequenceBranch[];
  endOrigin?: MermaidSequenceSourceOrigin;
};

export type MermaidSequenceStatement =
  | MermaidSequenceMessage
  | MermaidSequenceNote
  | MermaidSequenceActivation
  | MermaidSequenceComment
  | MermaidSequenceLocked
  | MermaidSequenceBlock;

export type MermaidSequenceSourceFormat = {
  source: string;
  headerRaw: string;
  eol: "\n" | "\r\n";
  trailingNewline: boolean;
  indentUnit: string;
  fingerprint: string;
  /** 旧 editor-layout 注释需在下一次保存迁移为紧凑 marker。 */
  metadataNeedsRewrite?: boolean;
  /** 新 marker 损坏时保留新旧 metadata，禁止生成可能意外生效的第三份 marker。 */
  metadataConflict?: boolean;
};

/** 仅记录顶层源码实体的原相对位置；递归语句顺序仍以 statements AST 为准。 */
export type MermaidSequenceSourceOrderItem =
  | { kind: "participant"; id: string }
  | { kind: "group"; id: string }
  | { kind: "autonumber" }
  | { kind: "statement"; id: string };

export type MermaidSequenceDiagram = {
  kind: "sequenceDiagram";
  participants: MermaidSequenceParticipant[];
  groups: MermaidSequenceGroup[];
  autonumber?: MermaidSequenceAutonumber;
  statements: MermaidSequenceStatement[];
  /**
   * 兼容旧的包内读取方式；它始终是 statements 中消息对象的扁平视图。
   * 新命令和组件必须以 statements 为唯一顺序来源。
   */
  messages: MermaidSequenceMessage[];
  /** 兼容紧凑 metadata 的旧字段；锁定语句同时由 statements 精确定位。 */
  preservedLines: string[];
  preservedSegments?: MermaidPreservedSegment[];
  sourceFormat?: MermaidSequenceSourceFormat;
  sourceOrder?: MermaidSequenceSourceOrderItem[];
};

function stable(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(stable).join(",")}]`;
  if (value && typeof value === "object") {
    return `{${Object.entries(value as Record<string, unknown>)
      .filter(([, item]) => item !== undefined)
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([key, item]) => `${JSON.stringify(key)}:${stable(item)}`)
      .join(",")}}`;
  }
  return JSON.stringify(value);
}

export function participantFingerprint(participant: MermaidSequenceParticipant): string {
  return stable({
    id: participant.id,
    text: participant.text,
    type: participant.type,
    groupId: participant.groupId,
    declared: participant.declared,
    created: participant.created
  });
}

export function statementFingerprint(statement: MermaidSequenceStatement): string {
  switch (statement.kind) {
    case "message":
      return stable({
        kind: statement.kind,
        source: statement.source,
        target: statement.target,
        text: statement.text,
        arrow: statement.arrow,
        activation: statement.activation,
        create: statement.create && {
          participantId: statement.create.participantId,
          type: statement.create.type,
          text: statement.create.text
        },
        destroy: statement.destroy && { participantId: statement.destroy.participantId }
      });
    case "note":
      return stable({
        kind: statement.kind,
        placement: statement.placement,
        participants: statement.participants,
        text: statement.text
      });
    case "activation":
      return stable({ kind: statement.kind, participantId: statement.participantId, active: statement.active });
    case "comment":
      return stable({ kind: statement.kind, text: statement.text });
    case "locked":
      return stable({ kind: statement.kind, raw: statement.raw });
    case "block":
      return stable({
        kind: statement.kind,
        blockType: statement.blockType,
        branches: statement.branches.map((branch) => ({
          label: branch.label,
          keyword: branch.keyword,
          statements: branch.statements.map(statementFingerprint)
        }))
      });
  }
}

export function groupFingerprint(group: MermaidSequenceGroup): string {
  return stable({ label: group.label, color: group.color });
}

export function autonumberFingerprint(autonumber: MermaidSequenceAutonumber): string {
  return stable({ enabled: autonumber.enabled, start: autonumber.start, step: autonumber.step });
}

export function blockHeaderFingerprint(block: MermaidSequenceBlock): string {
  return stable({ blockType: block.blockType, label: block.branches[0]?.label ?? "" });
}

export function branchHeaderFingerprint(branch: MermaidSequenceBranch): string {
  return stable({ keyword: branch.keyword, label: branch.label });
}

export function sequenceDiagramFingerprint(diagram: MermaidSequenceDiagram): string {
  return stable({
    participants: diagram.participants.map((participant) => ({
      fingerprint: participantFingerprint(participant),
      position: participant.position
    })),
    groups: diagram.groups.map((group) => ({
      fingerprint: groupFingerprint(group),
      participantIds: group.participantIds
    })),
    autonumber: diagram.autonumber && autonumberFingerprint(diagram.autonumber),
    statements: diagram.statements.map(statementFingerprint)
  });
}

/** 深度优先展开，供搜索、校验、布局和旧消息视图共用。 */
export function flattenMermaidSequenceStatements(
  statements: readonly MermaidSequenceStatement[]
): MermaidSequenceStatement[] {
  const flattened: MermaidSequenceStatement[] = [];
  for (const statement of statements) {
    flattened.push(statement);
    if (statement.kind === "block") {
      for (const branch of statement.branches) {
        flattened.push(...flattenMermaidSequenceStatements(branch.statements));
      }
    }
  }
  return flattened;
}

export function sequenceMessages(statements: readonly MermaidSequenceStatement[]): MermaidSequenceMessage[] {
  return flattenMermaidSequenceStatements(statements)
    .filter((statement): statement is MermaidSequenceMessage => statement.kind === "message");
}

function cloneOrigin(origin: MermaidSequenceSourceOrigin | undefined): MermaidSequenceSourceOrigin | undefined {
  return origin ? { ...origin } : undefined;
}

function cloneStatement(statement: MermaidSequenceStatement): MermaidSequenceStatement {
  switch (statement.kind) {
    case "message":
      return {
        ...statement,
        origin: cloneOrigin(statement.origin),
        create: statement.create ? { ...statement.create, origin: cloneOrigin(statement.create.origin) } : undefined,
        destroy: statement.destroy ? { ...statement.destroy, origin: cloneOrigin(statement.destroy.origin) } : undefined
      };
    case "note":
      return { ...statement, participants: [...statement.participants], origin: cloneOrigin(statement.origin) };
    case "activation":
    case "comment":
    case "locked":
      return { ...statement, origin: cloneOrigin(statement.origin) };
    case "block":
      return {
        ...statement,
        origin: cloneOrigin(statement.origin),
        endOrigin: cloneOrigin(statement.endOrigin),
        branches: statement.branches.map((branch) => ({
          ...branch,
          origin: cloneOrigin(branch.origin),
          statements: branch.statements.map(cloneStatement)
        }))
      };
  }
}

/** 深拷贝编辑模型，且让兼容 messages 视图继续指向克隆后的 statement 对象。 */
export function cloneMermaidSequence(diagram: MermaidSequenceDiagram): MermaidSequenceDiagram {
  const statements = diagram.statements.map(cloneStatement);
  return {
    ...diagram,
    participants: diagram.participants.map((participant) => ({
      ...participant,
      position: { ...participant.position },
      origin: cloneOrigin(participant.origin)
    })),
    groups: diagram.groups.map((group) => ({
      ...group,
      participantIds: [...group.participantIds],
      sourceOrder: group.sourceOrder?.map((item) => ({ ...item })),
      openOrigin: cloneOrigin(group.openOrigin),
      closeOrigin: cloneOrigin(group.closeOrigin)
    })),
    autonumber: diagram.autonumber
      ? { ...diagram.autonumber, origin: cloneOrigin(diagram.autonumber.origin) }
      : undefined,
    statements,
    messages: sequenceMessages(statements),
    preservedLines: [...diagram.preservedLines],
    preservedSegments: diagram.preservedSegments?.map((segment) => ({
      ...segment,
      lines: [...segment.lines]
    })),
    sourceFormat: diagram.sourceFormat ? { ...diagram.sourceFormat } : undefined,
    sourceOrder: diagram.sourceOrder?.map((item) => ({ ...item }))
  };
}
