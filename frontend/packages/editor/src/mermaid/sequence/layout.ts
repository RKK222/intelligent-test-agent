import {
  cloneMermaidSequence,
  type MermaidSequenceArrow,
  type MermaidSequenceBlockType,
  type MermaidSequenceDiagram,
  type MermaidSequenceStatement
} from "./model";

export type MermaidSequencePoint = { x: number; y: number };

export type MermaidSequenceParticipantGeometry = {
  id: string;
  x: number;
  y: number;
  width: number;
  height: number;
  centerX: number;
};

export type MermaidSequenceLifelineGeometry = {
  participantId: string;
  x: number;
  startY: number;
  endY: number;
};

export type MermaidSequenceMessageGeometry = {
  id: string;
  source: string;
  target: string;
  arrow: MermaidSequenceArrow;
  text: string;
  sequenceNumber?: number;
  y: number;
  points: MermaidSequencePoint[];
  selfCall: boolean;
  labelX: number;
  labelY: number;
};

export type MermaidSequenceActivationGeometry = {
  statementId: string;
  participantId: string;
  x: number;
  y: number;
  width: number;
  height: number;
  depth: number;
};

export type MermaidSequenceNoteGeometry = {
  id: string;
  x: number;
  y: number;
  width: number;
  height: number;
  text: string;
  participants: string[];
};

export type MermaidSequenceBranchGeometry = {
  id: string;
  label: string;
  y: number;
  height: number;
};

export type MermaidSequenceBlockGeometry = {
  id: string;
  blockType: MermaidSequenceBlockType;
  x: number;
  y: number;
  width: number;
  height: number;
  depth: number;
  label: string;
  color?: string;
  branches: MermaidSequenceBranchGeometry[];
};

export type MermaidSequenceGroupGeometry = {
  id: string;
  x: number;
  y: number;
  width: number;
  height: number;
  label: string;
  color?: string;
};

export type MermaidSequenceDestroyGeometry = {
  statementId: string;
  participantId: string;
  x: number;
  y: number;
};

export type MermaidSequenceRowGeometry = {
  id: string;
  kind: MermaidSequenceStatement["kind"];
  y: number;
  height: number;
  depth: number;
};

export type MermaidSequenceLayout = {
  width: number;
  height: number;
  participants: MermaidSequenceParticipantGeometry[];
  lifelines: MermaidSequenceLifelineGeometry[];
  messages: MermaidSequenceMessageGeometry[];
  activations: MermaidSequenceActivationGeometry[];
  notes: MermaidSequenceNoteGeometry[];
  blocks: MermaidSequenceBlockGeometry[];
  groups: MermaidSequenceGroupGeometry[];
  destroys: MermaidSequenceDestroyGeometry[];
  rows: MermaidSequenceRowGeometry[];
};

const SIDE_MARGIN = 68;
const HEADER_TOP = 34;
const HEADER_HEIGHT = 54;
const HEADER_MIN_WIDTH = 112;
const CONTENT_TOP = 126;
const DEFAULT_LANE_WIDTH = 190;
const BOTTOM_MARGIN = 54;

function visualTextWidth(value: string): number {
  return [...value].reduce((width, character) => width + (character.charCodeAt(0) > 0xff ? 14 : 7), 0);
}

function requiredLaneWidth(diagram: MermaidSequenceDiagram): number {
  let required = DEFAULT_LANE_WIDTH;
  const participantIndexes = new Map(diagram.participants.map((participant, index) => [participant.id, index]));
  const visit = (statements: readonly MermaidSequenceStatement[]) => {
    for (const statement of statements) {
      if (statement.kind === "message" && statement.source !== statement.target) {
        const distance = Math.max(1, Math.abs(
          (participantIndexes.get(statement.source) ?? 0) - (participantIndexes.get(statement.target) ?? 0)
        ));
        required = Math.max(required, (visualTextWidth(statement.text) + 88) / distance);
      } else if (statement.kind === "note") {
        const lines = statement.text.split("\n");
        required = Math.max(required, Math.max(...lines.map(visualTextWidth), 0) + 74);
      } else if (statement.kind === "block") {
        for (const branch of statement.branches) visit(branch.statements);
      }
    }
  };
  visit(diagram.statements);
  return Math.min(420, Math.ceil(required));
}

function maximumBlockDepth(statements: readonly MermaidSequenceStatement[], depth = 0): number {
  let maximum = -1;
  for (const statement of statements) {
    if (statement.kind !== "block") continue;
    maximum = Math.max(maximum, depth);
    for (const branch of statement.branches) {
      maximum = Math.max(maximum, maximumBlockDepth(branch.statements, depth + 1));
    }
  }
  return maximum;
}

/**
 * 纯时序布局：顺序扫描 AST，纵轴只由语义行推进，横轴只由参与者声明顺序决定。
 * 同一模型始终得到同一几何结果，且复杂度与参与者数和语句数线性相关。
 */
export function layoutMermaidSequence(diagram: MermaidSequenceDiagram): MermaidSequenceLayout {
  const laneWidth = requiredLaneWidth(diagram);
  const headerWidth = Math.min(laneWidth - 24, Math.max(
    HEADER_MIN_WIDTH,
    ...diagram.participants.map((participant) => visualTextWidth(participant.text) + 38)
  ));
  const centers = new Map(diagram.participants.map((participant, index) => [
    participant.id,
    SIDE_MARGIN + headerWidth / 2 + index * laneWidth
  ]));
  const deepestBlock = maximumBlockDepth(diagram.statements);
  const width = Math.max(
    360,
    SIDE_MARGIN * 2 + headerWidth + Math.max(0, diagram.participants.length - 1) * laneWidth,
    deepestBlock >= 0 ? 160 + 2 * (18 + deepestBlock * 12) : 0
  );
  const participants: MermaidSequenceParticipantGeometry[] = diagram.participants.map((participant) => ({
    id: participant.id,
    x: (centers.get(participant.id) ?? SIDE_MARGIN) - headerWidth / 2,
    y: HEADER_TOP,
    width: headerWidth,
    height: HEADER_HEIGHT,
    centerX: centers.get(participant.id) ?? SIDE_MARGIN
  }));
  const lifelines: MermaidSequenceLifelineGeometry[] = diagram.participants.map((participant) => ({
    participantId: participant.id,
    x: centers.get(participant.id) ?? SIDE_MARGIN,
    startY: participant.created ? CONTENT_TOP : HEADER_TOP + HEADER_HEIGHT,
    endY: CONTENT_TOP
  }));
  const lifelineById = new Map(lifelines.map((lifeline) => [lifeline.participantId, lifeline]));
  const messages: MermaidSequenceMessageGeometry[] = [];
  const activations: MermaidSequenceActivationGeometry[] = [];
  const notes: MermaidSequenceNoteGeometry[] = [];
  const blocks: MermaidSequenceBlockGeometry[] = [];
  const destroys: MermaidSequenceDestroyGeometry[] = [];
  const rows: MermaidSequenceRowGeometry[] = [];
  const activationStacks = new Map<string, Array<{ statementId: string; y: number; depth: number }>>();
  let nextMessageNumber = diagram.autonumber?.enabled ? diagram.autonumber.start ?? 1 : undefined;
  const messageNumberStep = diagram.autonumber?.step ?? 1;
  let cursorY = CONTENT_TOP;

  const openActivation = (participantId: string, statementId: string, y: number) => {
    const stack = activationStacks.get(participantId) ?? [];
    stack.push({ statementId, y, depth: stack.length });
    activationStacks.set(participantId, stack);
  };
  const closeActivation = (participantId: string, y: number) => {
    const stack = activationStacks.get(participantId);
    const open = stack?.pop();
    if (!open) return;
    const x = (centers.get(participantId) ?? SIDE_MARGIN) + open.depth * 7 - 5;
    activations.push({
      statementId: open.statementId,
      participantId,
      x,
      y: open.y,
      width: 10,
      height: Math.max(18, y - open.y),
      depth: open.depth
    });
  };

  const layoutStatements = (statements: readonly MermaidSequenceStatement[], depth: number) => {
    for (const statement of statements) {
      if (statement.kind === "message") {
        const sourceX = centers.get(statement.source) ?? SIDE_MARGIN;
        const targetX = centers.get(statement.target) ?? sourceX;
        const selfCall = statement.source === statement.target;
        const lineCount = Math.max(1, statement.text.split("\n").length);
        const rowHeight = Math.max(selfCall ? 76 : 54, 54 + (lineCount - 1) * 14);
        const y = cursorY + 24;
        const points = selfCall
          ? [
              { x: sourceX, y },
              { x: sourceX + 48, y },
              { x: sourceX + 48, y: y + 30 },
              { x: sourceX, y: y + 30 }
            ]
          : [{ x: sourceX, y }, { x: targetX, y }];
        messages.push({
          id: statement.id,
          source: statement.source,
          target: statement.target,
          arrow: statement.arrow,
          text: statement.text,
          ...(nextMessageNumber !== undefined ? { sequenceNumber: nextMessageNumber } : {}),
          y,
          points,
          selfCall,
          labelX: selfCall ? sourceX + 54 : (sourceX + targetX) / 2,
          labelY: y - 8
        });
        if (nextMessageNumber !== undefined) nextMessageNumber += messageNumberStep;
        rows.push({ id: statement.id, kind: statement.kind, y: cursorY, height: rowHeight, depth });
        if (statement.create) {
          const lifeline = lifelineById.get(statement.create.participantId);
          const header = participants.find((participant) => participant.id === statement.create?.participantId);
          if (header) header.y = y - HEADER_HEIGHT / 2;
          if (lifeline) lifeline.startY = y + HEADER_HEIGHT / 2;
        }
        if (statement.activation === "activate-target") openActivation(statement.target, statement.id, y);
        if (statement.activation === "deactivate-source") closeActivation(statement.source, y);
        if (statement.destroy) {
          const lifeline = lifelineById.get(statement.destroy.participantId);
          if (lifeline) lifeline.endY = y;
          destroys.push({
            statementId: statement.id,
            participantId: statement.destroy.participantId,
            x: centers.get(statement.destroy.participantId) ?? SIDE_MARGIN,
            y
          });
        }
        cursorY += rowHeight;
        continue;
      }

      if (statement.kind === "note") {
        const lineCount = Math.max(1, statement.text.split("\n").length);
        const noteHeight = 38 + (lineCount - 1) * 18;
        const participantXs = statement.participants.map((id) => centers.get(id) ?? SIDE_MARGIN);
        const minX = Math.min(...participantXs);
        const maxX = Math.max(...participantXs);
        const noteWidth = statement.placement === "over"
          ? Math.max(126, maxX - minX + 112)
          : Math.min(laneWidth - 26, Math.max(126, visualTextWidth(statement.text) + 38));
        const x = statement.placement === "left"
          ? minX - noteWidth - 18
          : statement.placement === "right" ? maxX + 18 : (minX + maxX - noteWidth) / 2;
        notes.push({
          id: statement.id,
          x: Math.max(12, Math.min(x, width - noteWidth - 12)),
          y: cursorY + 8,
          width: noteWidth,
          height: noteHeight,
          text: statement.text,
          participants: [...statement.participants]
        });
        rows.push({ id: statement.id, kind: statement.kind, y: cursorY, height: noteHeight + 18, depth });
        cursorY += noteHeight + 18;
        continue;
      }

      if (statement.kind === "activation") {
        const y = cursorY + 12;
        if (statement.active) openActivation(statement.participantId, statement.id, y);
        else closeActivation(statement.participantId, y);
        rows.push({ id: statement.id, kind: statement.kind, y: cursorY, height: 28, depth });
        cursorY += 28;
        continue;
      }

      if (statement.kind === "comment") {
        rows.push({ id: statement.id, kind: statement.kind, y: cursorY, height: 32, depth });
        cursorY += 32;
        continue;
      }

      if (statement.kind === "locked") {
        if (statement.hidden) continue;
        const height = Math.max(38, statement.raw.split(/\r?\n/).length * 20 + 14);
        rows.push({ id: statement.id, kind: statement.kind, y: cursorY, height, depth });
        cursorY += height;
        continue;
      }

      const blockStart = cursorY;
      const inset = 18 + depth * 12;
      const geometry: MermaidSequenceBlockGeometry = {
        id: statement.id,
        blockType: statement.blockType,
        x: inset,
        y: blockStart,
        width: width - inset * 2,
        height: 0,
        depth,
        label: statement.blockType === "rect" ? "" : statement.branches[0]?.label ?? "",
        ...(statement.blockType === "rect" && statement.branches[0]?.label
          ? { color: statement.branches[0].label }
          : {}),
        branches: []
      };
      blocks.push(geometry);
      cursorY += 38;
      statement.branches.forEach((branch, branchIndex) => {
        if (branchIndex > 0) cursorY += 28;
        const branchStart = cursorY;
        layoutStatements(branch.statements, depth + 1);
        if (cursorY === branchStart) cursorY += 24;
        geometry.branches.push({
          id: branch.id,
          label: branch.label,
          y: branchStart,
          height: cursorY - branchStart
        });
      });
      cursorY += 18;
      geometry.height = cursorY - blockStart;
      rows.push({ id: statement.id, kind: statement.kind, y: blockStart, height: geometry.height, depth });
    }
  };

  layoutStatements(diagram.statements, 0);
  const height = Math.max(260, cursorY + BOTTOM_MARGIN);
  for (const [participantId, stack] of activationStacks) {
    while (stack.length > 0) closeActivation(participantId, height - BOTTOM_MARGIN / 2);
  }
  for (const lifeline of lifelines) {
    if (lifeline.endY <= CONTENT_TOP) lifeline.endY = height - BOTTOM_MARGIN / 2;
  }

  const groups: MermaidSequenceGroupGeometry[] = diagram.groups.map((group) => {
    const memberXs = group.participantIds.map((id) => centers.get(id)).filter((x): x is number => x !== undefined);
    const minX = memberXs.length ? Math.min(...memberXs) : SIDE_MARGIN;
    const maxX = memberXs.length ? Math.max(...memberXs) : minX + headerWidth;
    return {
      id: group.id,
      x: Math.max(10, minX - headerWidth / 2 - 18),
      y: 12,
      width: Math.min(width - 20, maxX - minX + headerWidth + 36),
      height: HEADER_TOP + HEADER_HEIGHT + 18,
      label: group.label,
      ...(group.color ? { color: group.color } : {})
    };
  });

  return {
    width,
    height,
    participants,
    lifelines,
    messages,
    activations,
    notes,
    blocks,
    groups,
    destroys,
    rows
  };
}

/** 兼容旧坐标 metadata：只更新参与者坐标，不改变递归时序语义。 */
export function autoLayoutMermaidSequence(diagram: MermaidSequenceDiagram): MermaidSequenceDiagram {
  const result = cloneMermaidSequence(diagram);
  result.participants.forEach((participant, index) => {
    participant.position = { x: 80 + index * 220, y: 70 };
  });
  return result;
}
