import {
  cloneMermaidSequence,
  flattenMermaidSequenceStatements,
  sequenceMessages,
  type MermaidSequenceBlock,
  type MermaidSequenceDiagram,
  type MermaidSequenceMessage,
  type MermaidSequenceParticipant,
  type MermaidSequenceStatement
} from "./model";

export type MermaidSequenceValidationIssue = {
  code: string;
  message: string;
  statementId?: string;
};

export type MermaidSequenceCommandResult =
  | { ok: true; diagram: MermaidSequenceDiagram }
  | { ok: false; reason: string };

export type MermaidSequenceDeletionImpact = {
  total: number;
  messages: number;
  notes: number;
  activations: number;
  locked: number;
};

type StatementContainer = {
  id: string;
  statements: MermaidSequenceStatement[];
  ancestorBlockIds: string[];
};

function allContainers(diagram: MermaidSequenceDiagram): StatementContainer[] {
  const containers: StatementContainer[] = [{ id: "root", statements: diagram.statements, ancestorBlockIds: [] }];
  const visit = (statements: MermaidSequenceStatement[], ancestors: string[]) => {
    for (const statement of statements) {
      if (statement.kind !== "block") continue;
      for (const branch of statement.branches) {
        containers.push({ id: branch.id, statements: branch.statements, ancestorBlockIds: [...ancestors, statement.id] });
        visit(branch.statements, [...ancestors, statement.id]);
      }
    }
  };
  visit(diagram.statements, []);
  return containers;
}

function finish(diagram: MermaidSequenceDiagram): MermaidSequenceCommandResult {
  diagram.messages = sequenceMessages(diagram.statements);
  diagram.preservedLines = flattenMermaidSequenceStatements(diagram.statements)
    .filter((statement) => statement.kind === "locked" && !statement.hidden)
    .flatMap((statement) => statement.kind === "locked" ? statement.raw.split(/\r?\n/) : []);
  return { ok: true, diagram };
}

function issueKey(issue: MermaidSequenceValidationIssue): string {
  return `${issue.code}\u0000${issue.statementId ?? ""}\u0000${issue.message}`;
}

/** 结构命令只拒绝本次新引入的问题，避免历史锁定/损坏源码阻塞无关的安全编辑。 */
function finishValidated(
  previous: MermaidSequenceDiagram,
  next: MermaidSequenceDiagram
): MermaidSequenceCommandResult {
  const previousIssues = new Set(validateMermaidSequence(previous).map(issueKey));
  const introduced = validateMermaidSequence(next).find((issue) => !previousIssues.has(issueKey(issue)));
  if (introduced) return { ok: false, reason: introduced.message };
  return finish(next);
}

function removeStatementFromSourceOrder(diagram: MermaidSequenceDiagram, statementId: string): void {
  diagram.sourceOrder = diagram.sourceOrder?.filter((item) => item.kind !== "statement" || item.id !== statementId);
  for (const group of diagram.groups) {
    group.sourceOrder = group.sourceOrder?.filter((item) => item.kind !== "statement" || item.id !== statementId);
  }
}

function insertRootStatementSourceOrder(
  diagram: MermaidSequenceDiagram,
  statementId: string,
  statementIndex: number
): void {
  const order = diagram.sourceOrder ?? (diagram.sourceOrder = []);
  const followingIds = new Set(diagram.statements.slice(statementIndex + 1).map((statement) => statement.id));
  const followingIndex = order.findIndex((item) => item.kind === "statement" && followingIds.has(item.id));
  if (followingIndex >= 0) {
    order.splice(followingIndex, 0, { kind: "statement", id: statementId });
    return;
  }
  const previousIds = new Set(diagram.statements.slice(0, statementIndex).map((statement) => statement.id));
  let previousIndex = -1;
  order.forEach((item, index) => {
    if (item.kind === "statement" && previousIds.has(item.id)) previousIndex = index;
  });
  order.splice(previousIndex + 1 || order.length, 0, { kind: "statement", id: statementId });
}

function insertParticipantSourceOrder(diagram: MermaidSequenceDiagram, participantId: string, groupId?: string): void {
  if (groupId) {
    const group = diagram.groups.find((item) => item.id === groupId);
    if (!group) return;
    const order = group.sourceOrder ?? (group.sourceOrder = []);
    const firstStatement = order.findIndex((item) => item.kind === "statement");
    order.splice(firstStatement >= 0 ? firstStatement : order.length, 0, { kind: "participant", id: participantId });
    return;
  }
  const order = diagram.sourceOrder ?? (diagram.sourceOrder = []);
  const firstStatement = order.findIndex((item) => item.kind === "statement");
  order.splice(firstStatement >= 0 ? firstStatement : order.length, 0, { kind: "participant", id: participantId });
}

function removeParticipantFromSourceOrder(diagram: MermaidSequenceDiagram, participantId: string): void {
  diagram.sourceOrder = diagram.sourceOrder?.filter((item) => item.kind !== "participant" || item.id !== participantId);
  for (const group of diagram.groups) {
    group.sourceOrder = group.sourceOrder?.filter((item) => item.kind !== "participant" || item.id !== participantId);
  }
}

function synchronizeParticipantSourceOrder(diagram: MermaidSequenceDiagram): void {
  const groupById = new Map(diagram.groups.map((group) => [group.id, group]));
  const desired: NonNullable<MermaidSequenceDiagram["sourceOrder"]> = [];
  const seenGroups = new Set<string>();
  for (const participant of diagram.participants) {
    if (!participant.declared) continue;
    if (participant.groupId && groupById.has(participant.groupId)) {
      if (!seenGroups.has(participant.groupId)) {
        seenGroups.add(participant.groupId);
        desired.push({ kind: "group", id: participant.groupId });
      }
    } else {
      desired.push({ kind: "participant", id: participant.id });
    }
  }
  for (const group of diagram.groups) {
    if (!seenGroups.has(group.id)) desired.push({ kind: "group", id: group.id });
    const members = diagram.participants
      .filter((participant) => participant.groupId === group.id && participant.declared)
      .map((participant) => participant.id);
    group.participantIds = members;
    const memberSlots = (group.sourceOrder ?? []).flatMap((item, index) => item.kind === "participant" ? [index] : []);
    if (memberSlots.length === members.length) {
      memberSlots.forEach((slot, index) => { group.sourceOrder![slot] = { kind: "participant", id: members[index]! }; });
    }
  }
  const order = diagram.sourceOrder ?? (diagram.sourceOrder = []);
  const declarationSlots = order.flatMap((item, index) => item.kind === "participant" || item.kind === "group" ? [index] : []);
  if (declarationSlots.length === desired.length) {
    declarationSlots.forEach((slot, index) => { order[slot] = desired[index]!; });
  }
}

function groupedParticipantsRemainContiguous(diagram: MermaidSequenceDiagram): boolean {
  const indexes = new Map(diagram.participants.map((participant, index) => [participant.id, index]));
  return diagram.groups.every((group) => {
    const memberIndexes = group.participantIds
      .map((participantId) => indexes.get(participantId))
      .filter((index): index is number => index !== undefined)
      .sort((left, right) => left - right);
    return memberIndexes.length <= 1
      || memberIndexes.at(-1)! - memberIndexes[0]! + 1 === memberIndexes.length;
  });
}

function pruneSourceOrders(diagram: MermaidSequenceDiagram): void {
  const statementIds = new Set(flattenMermaidSequenceStatements(diagram.statements).map((statement) => statement.id));
  diagram.sourceOrder = diagram.sourceOrder?.filter((item) => item.kind !== "statement" || statementIds.has(item.id));
  for (const group of diagram.groups) {
    group.sourceOrder = group.sourceOrder?.filter((item) => item.kind !== "statement" || statementIds.has(item.id));
  }
}

function lockedReferencesParticipant(statement: MermaidSequenceStatement, participantId: string): boolean {
  if (statement.kind !== "locked") return false;
  return new RegExp(`(^|[^A-Za-z0-9_-])${participantId.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}([^A-Za-z0-9_-]|$)`)
    .test(statement.raw);
}

function visitStatements(
  statements: MermaidSequenceStatement[],
  visitor: (statement: MermaidSequenceStatement) => void
): void {
  for (const statement of statements) {
    visitor(statement);
    if (statement.kind === "block") {
      for (const branch of statement.branches) visitStatements(branch.statements, visitor);
    }
  }
}

export function nextSequenceId(diagram: MermaidSequenceDiagram, prefix: string): string {
  const used = new Set(flattenMermaidSequenceStatements(diagram.statements).map((statement) => statement.id));
  let index = 1;
  while (used.has(`${prefix}-${index}`)) index += 1;
  return `${prefix}-${index}`;
}

export function renameSequenceParticipant(
  diagram: MermaidSequenceDiagram,
  participantId: string,
  nextId: string
): MermaidSequenceCommandResult {
  const normalized = nextId.trim();
  if (!/^[A-Za-z_](?:[A-Za-z0-9_]|-(?=[A-Za-z0-9_]))*$/.test(normalized)) {
    return { ok: false, reason: "参与者 ID 只能由字母、数字、下划线和中划线组成，且不能以数字开头" };
  }
  if (normalized !== participantId && diagram.participants.some((participant) => participant.id === normalized)) {
    return { ok: false, reason: `参与者 ID“${normalized}”已存在` };
  }
  if (flattenMermaidSequenceStatements(diagram.statements)
    .some((statement) => lockedReferencesParticipant(statement, participantId))) {
    return { ok: false, reason: "锁定源码中仍引用该参与者，不能安全重命名" };
  }
  const next = cloneMermaidSequence(diagram);
  const participant = next.participants.find((item) => item.id === participantId);
  if (!participant) return { ok: false, reason: `找不到参与者“${participantId}”` };
  const hadDefaultText = participant.text === participantId;
  participant.id = normalized;
  if (hadDefaultText) participant.text = normalized;
  next.sourceOrder = next.sourceOrder?.map((item) =>
    item.kind === "participant" && item.id === participantId ? { ...item, id: normalized } : item
  );
  for (const group of next.groups) {
    group.participantIds = group.participantIds.map((id) => id === participantId ? normalized : id);
    group.sourceOrder = group.sourceOrder?.map((item) =>
      item.kind === "participant" && item.id === participantId ? { ...item, id: normalized } : item
    );
  }
  visitStatements(next.statements, (statement) => {
    if (statement.kind === "message") {
      if (statement.source === participantId) statement.source = normalized;
      if (statement.target === participantId) statement.target = normalized;
      if (statement.create?.participantId === participantId) statement.create.participantId = normalized;
      if (statement.create?.participantId === normalized && hadDefaultText && statement.create.text === participantId) {
        statement.create.text = normalized;
      }
      if (statement.destroy?.participantId === participantId) statement.destroy.participantId = normalized;
    } else if (statement.kind === "note") {
      statement.participants = statement.participants.map((id) => id === participantId ? normalized : id);
    } else if (statement.kind === "activation" && statement.participantId === participantId) {
      statement.participantId = normalized;
    }
  });
  return finish(next);
}

export function rebindSequenceMessage(
  diagram: MermaidSequenceDiagram,
  messageId: string,
  source: string,
  target: string
): MermaidSequenceCommandResult {
  const ids = new Set(diagram.participants.map((participant) => participant.id));
  if (!ids.has(source) || !ids.has(target)) return { ok: false, reason: "消息端点必须引用现有参与者" };
  const next = cloneMermaidSequence(diagram);
  const message = sequenceMessages(next.statements).find((item) => item.id === messageId);
  if (!message) return { ok: false, reason: "找不到待重绑的消息" };
  if (message.create && message.create.participantId !== target) {
    return { ok: false, reason: "create 消息的目标必须是被创建参与者" };
  }
  if (message.destroy && message.destroy.participantId !== source && message.destroy.participantId !== target) {
    return { ok: false, reason: "destroy 消息必须连接被销毁参与者" };
  }
  message.source = source;
  message.target = target;
  return finishValidated(diagram, next);
}

export function setSequenceMessageLifecycle(
  diagram: MermaidSequenceDiagram,
  messageId: string,
  kind: "create" | "destroy",
  enabled: boolean,
  destroyParticipantId?: string
): MermaidSequenceCommandResult {
  const next = cloneMermaidSequence(diagram);
  const message = sequenceMessages(next.statements).find((item) => item.id === messageId);
  if (!message) return { ok: false, reason: "找不到待更新的生命周期消息" };
  const participantId = kind === "create"
    ? message.target
    : destroyParticipantId ?? message.destroy?.participantId ?? message.source;
  if (kind === "destroy" && participantId !== message.source && participantId !== message.target) {
    return { ok: false, reason: "destroy 参与者必须是消息来源或目标" };
  }
  const participant = next.participants.find((item) => item.id === participantId);
  if (!participant) return { ok: false, reason: "生命周期端点必须引用现有参与者" };
  if (kind === "create") {
    message.create = enabled
      ? { participantId, type: participant.type, text: participant.text }
      : undefined;
    participant.created = enabled;
    participant.declared = !enabled;
    removeParticipantFromSourceOrder(next, participantId);
    if (!enabled) insertParticipantSourceOrder(next, participantId, participant.groupId);
  } else {
    message.destroy = enabled ? { participantId } : undefined;
  }
  return finishValidated(diagram, next);
}

export function updateSequenceParticipant(
  diagram: MermaidSequenceDiagram,
  participantId: string,
  patch: Partial<Pick<MermaidSequenceParticipant, "text" | "type" | "groupId">>
): MermaidSequenceCommandResult {
  const next = cloneMermaidSequence(diagram);
  const participant = next.participants.find((item) => item.id === participantId);
  if (!participant) return { ok: false, reason: "找不到参与者" };
  const previousGroupId = participant.groupId;
  Object.assign(participant, patch);
  for (const group of next.groups) {
    group.participantIds = group.participantIds.filter((id) => id !== participantId);
  }
  if (participant.groupId) {
    const group = next.groups.find((item) => item.id === participant.groupId);
    if (!group) return { ok: false, reason: "目标 box 分组不存在" };
    group.participantIds.push(participantId);
  }
  if (previousGroupId !== participant.groupId) {
    removeParticipantFromSourceOrder(next, participantId);
    insertParticipantSourceOrder(next, participantId, participant.groupId);
  }
  visitStatements(next.statements, (statement) => {
    if (statement.kind !== "message" || statement.create?.participantId !== participantId) return;
    if (patch.text !== undefined) statement.create.text = patch.text;
    if (patch.type !== undefined) statement.create.type = patch.type;
  });
  return finish(next);
}

export function moveSequenceParticipant(
  diagram: MermaidSequenceDiagram,
  participantId: string,
  targetIndex: number
): MermaidSequenceCommandResult {
  const sourceIndex = diagram.participants.findIndex((participant) => participant.id === participantId);
  if (sourceIndex < 0) return { ok: false, reason: "找不到待移动参与者" };
  if (targetIndex < 0 || targetIndex >= diagram.participants.length) return { ok: false, reason: "参与者目标位置无效" };
  const next = cloneMermaidSequence(diagram);
  const [participant] = next.participants.splice(sourceIndex, 1);
  if (!participant) return { ok: false, reason: "参与者顺序已变化" };
  next.participants.splice(targetIndex, 0, participant);
  if (!groupedParticipantsRemainContiguous(next)) {
    return { ok: false, reason: "box 分组成员必须保持连续，不能把其他参与者插入分组内部" };
  }
  synchronizeParticipantSourceOrder(next);
  return finishValidated(diagram, next);
}

export function updateSequenceStatement(
  diagram: MermaidSequenceDiagram,
  statementId: string,
  patch: Record<string, unknown>
): MermaidSequenceCommandResult {
  const next = cloneMermaidSequence(diagram);
  const statement = flattenMermaidSequenceStatements(next.statements).find((item) => item.id === statementId);
  if (!statement) return { ok: false, reason: "找不到待更新的时序元素" };
  if (statement.kind === "locked") return { ok: false, reason: "锁定源码不能结构化编辑" };
  Object.assign(statement, patch);
  return finishValidated(diagram, next);
}

export function appendSequenceStatement(
  diagram: MermaidSequenceDiagram,
  containerId: string,
  statement: MermaidSequenceStatement,
  index?: number
): MermaidSequenceCommandResult {
  if (statement.kind === "locked") return { ok: false, reason: "不能通过元素库创建锁定源码" };
  const next = cloneMermaidSequence(diagram);
  const container = allContainers(next).find((item) => item.id === containerId);
  if (!container) return { ok: false, reason: "目标分支不存在" };
  if (flattenMermaidSequenceStatements(next.statements).some((item) => item.id === statement.id)) {
    return { ok: false, reason: `元素 ID“${statement.id}”已存在` };
  }
  const insertion = index === undefined ? container.statements.length : Math.max(0, Math.min(index, container.statements.length));
  container.statements.splice(insertion, 0, statement);
  if (container.id === "root") insertRootStatementSourceOrder(next, statement.id, insertion);
  return finishValidated(diagram, next);
}

export function moveSequenceStatement(
  diagram: MermaidSequenceDiagram,
  statementId: string,
  targetContainerId: string,
  targetIndex: number
): MermaidSequenceCommandResult {
  const sourceContainer = allContainers(diagram).find((container) =>
    container.statements.some((statement) => statement.id === statementId)
  );
  const targetContainer = allContainers(diagram).find((container) => container.id === targetContainerId);
  if (!sourceContainer || !targetContainer) return { ok: false, reason: "找不到移动源或目标分支" };
  const sourceIndex = sourceContainer.statements.findIndex((statement) => statement.id === statementId);
  const sourceStatement = sourceContainer.statements[sourceIndex];
  if (!sourceStatement) return { ok: false, reason: "找不到待移动元素" };
  if (sourceStatement.kind === "locked") return { ok: false, reason: "锁定源码不能移动" };
  if (sourceStatement.kind === "block" && targetContainer.ancestorBlockIds.includes(sourceStatement.id)) {
    return { ok: false, reason: "片段不能移动到自己的后代分支" };
  }

  if (sourceContainer.id === targetContainer.id) {
    const lower = Math.min(sourceIndex, targetIndex);
    const upper = Math.max(sourceIndex, targetIndex);
    if (sourceContainer.statements.slice(lower, upper).some((statement) => statement.kind === "locked" && !statement.hidden)) {
      return { ok: false, reason: "不能跨越锁定源码调整顺序" };
    }
  } else if (
    sourceContainer.statements.some((statement) => statement.kind === "locked" && !statement.hidden)
    || targetContainer.statements.some((statement) => statement.kind === "locked" && !statement.hidden)
  ) {
    return { ok: false, reason: "包含锁定源码的分支不能执行跨分支移动" };
  }

  const next = cloneMermaidSequence(diagram);
  const nextSource = allContainers(next).find((container) => container.id === sourceContainer.id)!;
  const nextTarget = allContainers(next).find((container) => container.id === targetContainer.id)!;
  const [moved] = nextSource.statements.splice(sourceIndex, 1);
  if (!moved) return { ok: false, reason: "移动源已变化" };
  const adjustedIndex = nextSource.id === nextTarget.id && targetIndex > sourceIndex ? targetIndex - 1 : targetIndex;
  const insertion = Math.max(0, Math.min(adjustedIndex, nextTarget.statements.length));
  nextTarget.statements.splice(insertion, 0, moved);
  removeStatementFromSourceOrder(next, statementId);
  if (nextTarget.id === "root") insertRootStatementSourceOrder(next, statementId, insertion);
  return finishValidated(diagram, next);
}

export function deleteSequenceStatement(
  diagram: MermaidSequenceDiagram,
  statementId: string
): MermaidSequenceCommandResult {
  const container = allContainers(diagram).find((item) => item.statements.some((statement) => statement.id === statementId));
  const statement = container?.statements.find((item) => item.id === statementId);
  if (!container || !statement) return { ok: false, reason: "找不到待删除元素" };
  if (statement.kind === "locked") return { ok: false, reason: "锁定源码不能删除" };
  const next = cloneMermaidSequence(diagram);
  const nextContainer = allContainers(next).find((item) => item.id === container.id)!;
  const nextIndex = nextContainer.statements.findIndex((item) => item.id === statementId);
  const [removed] = nextContainer.statements.splice(nextIndex, 1);
  if (removed) {
    for (const nested of flattenMermaidSequenceStatements([removed])) {
      if (nested.kind === "message" && nested.create) {
        const participant = next.participants.find((item) => item.id === nested.create?.participantId);
        if (participant) {
          participant.created = false;
          participant.declared = true;
          insertParticipantSourceOrder(next, participant.id, participant.groupId);
        }
      }
    }
  }
  removeStatementFromSourceOrder(next, statementId);
  pruneSourceOrders(next);
  return finishValidated(diagram, next);
}

export function analyzeSequenceParticipantDeletion(
  diagram: MermaidSequenceDiagram,
  participantId: string
): MermaidSequenceDeletionImpact {
  const impact: MermaidSequenceDeletionImpact = { total: 0, messages: 0, notes: 0, activations: 0, locked: 0 };
  visitStatements(diagram.statements, (statement) => {
    if (statement.kind === "message" && (
      statement.source === participantId
      || statement.target === participantId
      || statement.create?.participantId === participantId
      || statement.destroy?.participantId === participantId
    )) impact.messages += 1;
    if (statement.kind === "note" && statement.participants.includes(participantId)) impact.notes += 1;
    if (statement.kind === "activation" && statement.participantId === participantId) impact.activations += 1;
    if (lockedReferencesParticipant(statement, participantId)) impact.locked += 1;
  });
  impact.total = impact.messages + impact.notes + impact.activations + impact.locked;
  return impact;
}

function cascadeStatements(
  statements: MermaidSequenceStatement[],
  participantId: string
): MermaidSequenceStatement[] {
  const result: MermaidSequenceStatement[] = [];
  for (const statement of statements) {
    if (statement.kind === "message" && (
      statement.source === participantId
      || statement.target === participantId
      || statement.create?.participantId === participantId
      || statement.destroy?.participantId === participantId
    )) continue;
    if (statement.kind === "activation" && statement.participantId === participantId) continue;
    if (statement.kind === "note") {
      statement.participants = statement.participants.filter((id) => id !== participantId);
      if (statement.participants.length === 0) continue;
    }
    if (statement.kind === "block") {
      for (const branch of statement.branches) {
        branch.statements = cascadeStatements(branch.statements, participantId);
      }
    }
    result.push(statement);
  }
  return result;
}

export function deleteSequenceParticipant(
  diagram: MermaidSequenceDiagram,
  participantId: string,
  confirmed: boolean
): MermaidSequenceCommandResult {
  if (!diagram.participants.some((participant) => participant.id === participantId)) {
    return { ok: false, reason: "找不到待删除参与者" };
  }
  const impact = analyzeSequenceParticipantDeletion(diagram, participantId);
  if (impact.locked > 0) return { ok: false, reason: "锁定源码仍引用该参与者，无法安全级联删除" };
  if (impact.total > 0 && !confirmed) {
    return { ok: false, reason: `删除会影响 ${impact.total} 个时序元素，请先确认级联清理` };
  }
  const next = cloneMermaidSequence(diagram);
  next.participants = next.participants.filter((participant) => participant.id !== participantId);
  removeParticipantFromSourceOrder(next, participantId);
  for (const group of next.groups) {
    group.participantIds = group.participantIds.filter((id) => id !== participantId);
  }
  next.statements = cascadeStatements(next.statements, participantId);
  pruneSourceOrders(next);
  return finish(next);
}

function referencedParticipants(statement: MermaidSequenceStatement): string[] {
  if (statement.kind === "message") return [statement.source, statement.target];
  if (statement.kind === "note") return statement.participants;
  if (statement.kind === "activation") return [statement.participantId];
  return [];
}

/** 本地校验给出立即可展示的中文原因；官方 Mermaid parser 仍是应用前最终语法门槛。 */
export function validateMermaidSequence(diagram: MermaidSequenceDiagram): MermaidSequenceValidationIssue[] {
  const issues: MermaidSequenceValidationIssue[] = [];
  const ids = new Set<string>();
  for (const participant of diagram.participants) {
    if (ids.has(participant.id)) issues.push({ code: "duplicate-participant", message: `参与者 ID“${participant.id}”重复` });
    ids.add(participant.id);
  }
  const statementIds = new Set<string>();
  for (const statement of flattenMermaidSequenceStatements(diagram.statements)) {
    if (statementIds.has(statement.id)) issues.push({ code: "duplicate-statement", message: `元素 ID“${statement.id}”重复`, statementId: statement.id });
    statementIds.add(statement.id);
    for (const participantId of referencedParticipants(statement)) {
      if (!ids.has(participantId)) issues.push({ code: "missing-participant", message: `元素引用了不存在的参与者“${participantId}”`, statementId: statement.id });
    }
    if (statement.kind === "note") {
      const validParticipantCount = statement.placement === "over"
        ? statement.participants.length >= 1 && statement.participants.length <= 2
        : statement.participants.length === 1;
      if (!validParticipantCount) {
        issues.push({
          code: "invalid-note-participants",
          message: statement.placement === "over" ? "Note over 需要一到两个参与者" : "Note left/right 只能绑定一个参与者",
          statementId: statement.id
        });
      }
    }
  }

  const initialAlive = new Map(diagram.participants.map((participant) => [participant.id, !participant.created]));
  const initialActivations = new Map<string, number>();
  const created = new Set<string>();
  const destroyed = new Set<string>();

  const validateStatements = (
    statements: MermaidSequenceStatement[],
    alive: Map<string, boolean>,
    activations: Map<string, number>,
    created: Set<string>,
    destroyed: Set<string>
  ) => {
    for (const statement of statements) {
      if (statement.kind === "block") {
        for (const branch of statement.branches) {
          validateStatements(
            branch.statements,
            new Map(alive),
            new Map(activations),
            // create 声明在 Mermaid 源码层面必须全图唯一，即使位于互斥分支；
            // destroy/存活状态才按分支隔离，允许各分支分别结束同一生命线。
            created,
            new Set(destroyed)
          );
        }
        continue;
      }
      if (statement.kind === "message") {
        if (statement.create) {
          if (statement.create.participantId !== statement.target) {
            issues.push({ code: "invalid-create-target", message: "create 的参与者必须是紧邻消息的目标", statementId: statement.id });
          }
          if (created.has(statement.create.participantId)) {
            issues.push({ code: "duplicate-create", message: `参与者“${statement.create.participantId}”被重复 create`, statementId: statement.id });
          }
          created.add(statement.create.participantId);
          alive.set(statement.create.participantId, true);
        }
        for (const participantId of [statement.source, statement.target]) {
          if (alive.get(participantId) === false) {
            issues.push({ code: "lifecycle-reference", message: `参与者“${participantId}”在 create 前或 destroy 后被引用`, statementId: statement.id });
          }
        }
        if (statement.activation === "activate-target") {
          activations.set(statement.target, (activations.get(statement.target) ?? 0) + 1);
        }
        if (statement.activation === "deactivate-source") {
          const count = activations.get(statement.source) ?? 0;
          if (count === 0) issues.push({ code: "inactive-deactivate", message: `参与者“${statement.source}”尚未激活，不能停用`, statementId: statement.id });
          else activations.set(statement.source, count - 1);
        }
        if (statement.destroy) {
          if (statement.destroy.participantId !== statement.source && statement.destroy.participantId !== statement.target) {
            issues.push({ code: "invalid-destroy-endpoint", message: "destroy 的参与者必须连接紧邻消息", statementId: statement.id });
          }
          if (destroyed.has(statement.destroy.participantId)) {
            issues.push({ code: "duplicate-destroy", message: `参与者“${statement.destroy.participantId}”被重复 destroy`, statementId: statement.id });
          }
          destroyed.add(statement.destroy.participantId);
          alive.set(statement.destroy.participantId, false);
        }
      } else if (statement.kind === "activation") {
        const count = activations.get(statement.participantId) ?? 0;
        if (statement.active) activations.set(statement.participantId, count + 1);
        else if (count === 0) issues.push({ code: "inactive-deactivate", message: `参与者“${statement.participantId}”尚未激活，不能停用`, statementId: statement.id });
        else activations.set(statement.participantId, count - 1);
      }
    }
  };
  validateStatements(diagram.statements, initialAlive, initialActivations, created, destroyed);
  return issues;
}
