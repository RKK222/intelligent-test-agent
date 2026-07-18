import { serializeMermaidCompactSequence } from "../compact-metadata";
import {
  autonumberFingerprint,
  blockHeaderFingerprint,
  branchHeaderFingerprint,
  groupFingerprint,
  participantFingerprint,
  sequenceDiagramFingerprint,
  statementFingerprint,
  type MermaidSequenceAutonumber,
  type MermaidSequenceBlock,
  type MermaidSequenceBranch,
  type MermaidSequenceDiagram,
  type MermaidSequenceGroup,
  type MermaidSequenceLifecycleCreate,
  type MermaidSequenceLifecycleDestroy,
  type MermaidSequenceParticipant,
  type MermaidSequenceSourceOrigin,
  type MermaidSequenceStatement
} from "./model";

function text(value: string): string {
  return value.replaceAll("\r", "").split("\n").map((line) => line.trim()).join("<br/>").trim();
}

function reusable(
  origin: MermaidSequenceSourceOrigin | undefined,
  parentId: string,
  fingerprint: string
): origin is MermaidSequenceSourceOrigin {
  return Boolean(origin && origin.parentId === parentId && origin.fingerprint === fingerprint);
}

function participantDeclaration(participant: MermaidSequenceParticipant, indent = ""): string {
  const declaration = participant.type === "actor"
    ? `actor ${participant.id}`
    : participant.type === "participant"
      ? `participant ${participant.id}`
      : `participant ${participant.id}@{ "type": "${participant.type}" }`;
  const label = text(participant.text);
  return `${indent}${declaration}${label && label !== participant.id ? ` as ${label}` : ""}`;
}

function createFingerprint(create: MermaidSequenceLifecycleCreate): string {
  return JSON.stringify({ participantId: create.participantId, type: create.type, text: create.text });
}

function destroyFingerprint(destroy: MermaidSequenceLifecycleDestroy): string {
  return JSON.stringify({ participantId: destroy.participantId });
}

function createDeclaration(create: MermaidSequenceLifecycleCreate, indent: string): string {
  const participant: MermaidSequenceParticipant = {
    id: create.participantId,
    text: create.text,
    type: create.type,
    position: { x: 0, y: 0 },
    declared: false,
    created: true
  };
  return `${indent}create ${participantDeclaration(participant).trimStart()}`;
}

function indentFor(
  statement: MermaidSequenceStatement,
  parentId: string,
  depth: number,
  indentUnit: string
): string {
  if (statement.origin?.parentId === parentId) return statement.origin.indent;
  return indentUnit.repeat(depth);
}

function serializeBranchHeader(
  branch: MermaidSequenceBranch,
  block: MermaidSequenceBlock,
  depth: number,
  indentUnit: string
): string {
  if (reusable(branch.origin, block.id, branchHeaderFingerprint(branch))) return branch.origin.raw;
  return `${indentUnit.repeat(depth)}${branch.keyword}${branch.label ? ` ${text(branch.label)}` : ""}`;
}

function serializeStatement(
  statement: MermaidSequenceStatement,
  parentId: string,
  depth: number,
  indentUnit: string
): string[] {
  if (statement.kind === "locked") return [statement.raw];
  const indent = indentFor(statement, parentId, depth, indentUnit);

  if (statement.kind === "message") {
    const lines: string[] = [];
    if (statement.create) {
      lines.push(reusable(statement.create.origin, parentId, createFingerprint(statement.create))
        ? statement.create.origin.raw
        : createDeclaration(statement.create, indent));
    }
    if (statement.destroy) {
      lines.push(reusable(statement.destroy.origin, parentId, destroyFingerprint(statement.destroy))
        ? statement.destroy.origin.raw
        : `${indent}destroy ${statement.destroy.participantId}`);
    }
    if (reusable(statement.origin, parentId, statementFingerprint(statement))) {
      lines.push(statement.origin.raw);
    } else {
      const shortcut = statement.activation === "activate-target"
        ? "+"
        : statement.activation === "deactivate-source" ? "-" : "";
      lines.push(`${indent}${statement.source}${statement.arrow}${shortcut}${statement.target}: ${text(statement.text)}`);
    }
    return lines;
  }

  if (reusable(statement.origin, parentId, statementFingerprint(statement))) return [statement.origin.raw];

  if (statement.kind === "note") {
    const placement = statement.placement === "left"
      ? "left of"
      : statement.placement === "right" ? "right of" : "over";
    return [`${indent}Note ${placement} ${statement.participants.join(",")}: ${text(statement.text)}`];
  }
  if (statement.kind === "activation") {
    return [`${indent}${statement.active ? "activate" : "deactivate"} ${statement.participantId}`];
  }
  if (statement.kind === "comment") return [`${indent}%%${statement.text ? ` ${statement.text}` : ""}`];

  const block = statement;
  const lines: string[] = [];
  const label = block.branches[0]?.label ?? "";
  if (reusable(block.origin, parentId, blockHeaderFingerprint(block))) lines.push(block.origin.raw);
  else lines.push(`${indent}${block.blockType}${label ? ` ${text(label)}` : ""}`);

  block.branches.forEach((branch, branchIndex) => {
    if (branchIndex > 0) lines.push(serializeBranchHeader(branch, block, depth, indentUnit));
    for (const child of branch.statements) {
      lines.push(...serializeStatement(child, branch.id, depth + 1, indentUnit));
    }
  });
  // 原源码缺少 end 时保持损坏结构，交给打开/应用边界的官方 parser 拒绝；仅新建片段补 canonical end。
  if (block.endOrigin) lines.push(block.endOrigin.raw);
  else if (!block.origin) lines.push(`${indent}end`);
  return lines;
}

function serializeGroupOpen(group: MermaidSequenceGroup): string {
  if (reusable(group.openOrigin, "root", groupFingerprint(group))) return group.openOrigin.raw;
  const prefix = group.color ? `${group.color}${group.label ? " " : ""}` : "";
  return `box${prefix || group.label ? ` ${prefix}${text(group.label)}` : ""}`;
}

function serializeGroupClose(group: MermaidSequenceGroup): string {
  return reusable(group.closeOrigin, "root", groupFingerprint(group)) ? group.closeOrigin.raw : "end";
}

function serializeAutonumber(autonumber: MermaidSequenceAutonumber): string {
  if (reusable(autonumber.origin, "root", autonumberFingerprint(autonumber))) return autonumber.origin.raw;
  if (!autonumber.enabled) return "autonumber off";
  return `autonumber${autonumber.start !== undefined ? ` ${autonumber.start}` : ""}${autonumber.step !== undefined ? ` ${autonumber.step}` : ""}`;
}

export function serializeMermaidSequence(diagram: MermaidSequenceDiagram): string {
  if (
    diagram.sourceFormat
    && !diagram.sourceFormat.metadataNeedsRewrite
    && diagram.sourceFormat.fingerprint === sequenceDiagramFingerprint(diagram)
  ) return diagram.sourceFormat.source;

  const eol = diagram.sourceFormat?.eol ?? "\n";
  const indentUnit = diagram.sourceFormat?.indentUnit || "  ";
  const lines = [
    diagram.sourceFormat?.headerRaw ?? "sequenceDiagram",
    ...(diagram.sourceFormat?.metadataConflict ? [] : serializeMermaidCompactSequence(diagram))
  ];
  const participantById = new Map(diagram.participants.map((participant) => [participant.id, participant]));
  const groupById = new Map(diagram.groups.map((group) => [group.id, group]));
  const statementById = new Map(diagram.statements.map((statement) => [statement.id, statement]));
  const emittedParticipants = new Set<string>();
  const emittedGroups = new Set<string>();
  const emittedStatements = new Set<string>();
  let emittedAutonumber = false;

  const emitParticipant = (participant: MermaidSequenceParticipant | undefined) => {
    if (!participant || emittedParticipants.has(participant.id) || !participant.declared) return;
    const group = participant.groupId ? groupById.get(participant.groupId) : undefined;
    if (group) return;
    emittedParticipants.add(participant.id);
    const participantIndent = participant.origin?.indent;
    lines.push(reusable(participant.origin, "root", participantFingerprint(participant))
      ? participant.origin.raw
      : participantDeclaration(participant, participantIndent));
  };

  const emitGroup = (group: MermaidSequenceGroup | undefined) => {
    if (!group || emittedGroups.has(group.id)) return;
    emittedGroups.add(group.id);
    lines.push(serializeGroupOpen(group));
    const emitGroupMember = (participantId: string) => {
      const member = participantById.get(participantId);
      if (!member || member.groupId !== group.id || !member.declared || emittedParticipants.has(member.id)) return;
      emittedParticipants.add(member.id);
      const memberIndent = member.origin?.indent ?? indentUnit;
      lines.push(reusable(member.origin, group.id, participantFingerprint(member))
        ? member.origin.raw
        : participantDeclaration(member, memberIndent));
    };
    const emitGroupStatement = (statementId: string) => {
      const statement = statementById.get(statementId);
      if (!statement || emittedStatements.has(statement.id)) return;
      emittedStatements.add(statement.id);
      lines.push(...serializeStatement(statement, group.id, 0, indentUnit));
    };
    const groupOrder = group.sourceOrder ?? [];
    if (groupOrder.length > 0) {
      const listedMembers = new Set(groupOrder.flatMap((item) => item.kind === "participant" ? [item.id] : []));
      let insertedNewMembers = false;
      const emitNewMembers = () => {
        if (insertedNewMembers) return;
        insertedNewMembers = true;
        for (const participantId of group.participantIds) {
          if (!listedMembers.has(participantId)) emitGroupMember(participantId);
        }
      };
      for (const item of groupOrder) {
        if (item.kind === "participant") emitGroupMember(item.id);
        else if (item.kind === "statement") {
          emitNewMembers();
          emitGroupStatement(item.id);
        }
      }
      emitNewMembers();
    } else {
      for (const participantId of group.participantIds) emitGroupMember(participantId);
    }
    // 新建的空 box 仍需写回，不能因为暂时没有参与者而丢失。
    lines.push(serializeGroupClose(group));
  };

  const emitAutonumber = () => {
    if (!diagram.autonumber || emittedAutonumber) return;
    emittedAutonumber = true;
    lines.push(serializeAutonumber(diagram.autonumber));
  };

  const emitStatement = (statement: MermaidSequenceStatement | undefined) => {
    if (!statement || emittedStatements.has(statement.id)) return;
    emittedStatements.add(statement.id);
    lines.push(...serializeStatement(statement, "root", 0, indentUnit));
  };

  const sourceOrder = diagram.sourceOrder ?? [];
  const groupStatementIds = new Set(diagram.groups.flatMap((group) =>
    group.sourceOrder?.flatMap((item) => item.kind === "statement" ? [item.id] : []) ?? []
  ));
  const orderedRootStatements = diagram.statements.filter((statement) => !groupStatementIds.has(statement.id));
  const rootIds = new Set(orderedRootStatements.map((statement) => statement.id));
  const orderedExistingStatementIds = sourceOrder
    .filter((item): item is Extract<typeof item, { kind: "statement" }> => item.kind === "statement" && rootIds.has(item.id))
    .map((item) => item.id);
  const preserveSourceOrder = sourceOrder.length > 0
    && orderedExistingStatementIds.length === orderedRootStatements.length
    && orderedExistingStatementIds.every((id, index) => id === orderedRootStatements[index]?.id);

  if (preserveSourceOrder) {
    const listedParticipants = new Set(sourceOrder
      .filter((item): item is Extract<typeof item, { kind: "participant" }> => item.kind === "participant")
      .map((item) => item.id));
    const listedGroups = new Set(sourceOrder
      .filter((item): item is Extract<typeof item, { kind: "group" }> => item.kind === "group")
      .map((item) => item.id));
    const hasListedAutonumber = sourceOrder.some((item) => item.kind === "autonumber");
    let insertedNewDeclarations = false;
    const emitNewDeclarations = () => {
      if (insertedNewDeclarations) return;
      insertedNewDeclarations = true;
      for (const group of diagram.groups) {
        if (!listedGroups.has(group.id)) emitGroup(group);
      }
      for (const participant of diagram.participants) {
        if (!listedParticipants.has(participant.id)) emitParticipant(participant);
      }
      if (!hasListedAutonumber) emitAutonumber();
    };

    for (const item of sourceOrder) {
      if (item.kind === "participant") emitParticipant(participantById.get(item.id));
      else if (item.kind === "group") emitGroup(groupById.get(item.id));
      else if (item.kind === "autonumber") emitAutonumber();
      else {
        emitNewDeclarations();
        emitStatement(statementById.get(item.id));
      }
    }
    emitNewDeclarations();
  } else {
    // 结构发生根级新增或移动时使用确定性的 canonical 顺序，避免源码锚点产生歧义。
    for (const participant of diagram.participants) {
      if (participant.groupId) emitGroup(groupById.get(participant.groupId));
      else emitParticipant(participant);
    }
    for (const group of diagram.groups) emitGroup(group);
    emitAutonumber();
    for (const statement of diagram.statements) emitStatement(statement);
  }

  // 删除、分组调整等操作可能让旧 sourceOrder 条目失效；在末尾补齐仍未写出的实体。
  for (const group of diagram.groups) emitGroup(group);
  for (const participant of diagram.participants) emitParticipant(participant);
  emitAutonumber();
  for (const statement of diagram.statements) emitStatement(statement);

  const result = lines.join(eol);
  return diagram.sourceFormat?.trailingNewline === false ? result : `${result}${eol}`;
}
