import { canonicalMessageId, groupRenderableParts } from "./part-utils";
import type { OpencodeLikeConversationState, TimelineRow } from "./types";

type AssistantRowAccumulator = {
  hasAssistantHeader: boolean;
  partIndex: number;
  contextGroupIndex?: number;
  reasoningGroupIndex?: number;
};

export function createTimelineRows(state: OpencodeLikeConversationState): TimelineRow[] {
  const rows: TimelineRow[] = [];

  if (state.userMessages.length === 0 && state.orphanAssistantMessages.length === 0 && state.running) {
    rows.push({ type: "thinking", key: "thinking:pending", userMessageId: "__pending__" });
  }

  const orphanAccumulator: AssistantRowAccumulator = {
    hasAssistantHeader: false,
    partIndex: 0
  };
  for (const assistantMessage of state.orphanAssistantMessages) {
    const assistantMessageId = canonicalMessageId(assistantMessage);
    const groups = groupRenderableParts(state.partsByMessageId[assistantMessageId] ?? [], {
      showReasoningSummaries: state.showReasoningSummaries
    });
    for (const group of groups) {
      appendAssistantGroupRow(rows, state, orphanAccumulator, {
        group,
        assistantMessageId,
        userMessageId: "__orphan__"
      });
    }
  }

  for (const [index, userMessage] of state.userMessages.entries()) {
    const userMessageId = canonicalMessageId(userMessage);
    if (index > 0) {
      rows.push({ type: "turn-gap", key: `gap:${userMessageId}`, userMessageId });
    }

    rows.push({ type: "user-message", key: `user:${userMessageId}`, userMessageId });

    const assistantMessages = state.assistantMessagesByParent[userMessageId] ?? [];
    const accumulator: AssistantRowAccumulator = {
      hasAssistantHeader: false,
      partIndex: 0
    };
    for (const assistantMessage of assistantMessages) {
      const assistantMessageId = canonicalMessageId(assistantMessage);
      const groups = groupRenderableParts(state.partsByMessageId[assistantMessageId] ?? [], {
        showReasoningSummaries: state.showReasoningSummaries
      });

      for (const group of groups) {
        appendAssistantGroupRow(rows, state, accumulator, {
          group,
          assistantMessageId,
          userMessageId
        });
      }
    }

    if (isActiveTurn(userMessageId, state) && state.running && accumulator.partIndex === 0) {
      rows.push({ type: "thinking", key: `thinking:${userMessageId}`, userMessageId });
    }

    if (isLatestTurn(userMessageId, state) && state.diffFiles.length > 0) {
      rows.push({ type: "diff-summary", key: `diff:${userMessageId}`, userMessageId, files: state.diffFiles });
    }
  }

  if (state.runtimeStatus.type === "failed") {
    rows.push({ type: "error", key: "runtime:error", message: state.runtimeStatus.message ?? "运行失败" });
  }

  return rows;
}

// 当前后端会把同一次回答拆成多条 assistant message/part。
// 这里只在前端 timeline 行层合并同类过程项，避免重复头像和重复“思考状态/已探索”标题。
function appendAssistantGroupRow(
  rows: TimelineRow[],
  state: OpencodeLikeConversationState,
  accumulator: AssistantRowAccumulator,
  params: {
    group: ReturnType<typeof groupRenderableParts>[number];
    assistantMessageId: string;
    userMessageId: string;
  }
): void {
  const { group, assistantMessageId, userMessageId } = params;
  if (group.type === "context-tool-group") {
    const refs = group.refs.map((ref) => ({ messageId: assistantMessageId, partId: ref.partId }));
    if (typeof accumulator.contextGroupIndex === "number") {
      const existing = rows[accumulator.contextGroupIndex];
      if (existing?.type === "context-tool-group") {
        existing.refs.push(...refs);
      }
      return;
    }
    const showAssistantHeader = !accumulator.hasAssistantHeader;
    rows.push({
      type: "context-tool-group",
      key: `${group.key}:${assistantMessageId}`,
      userMessageId,
      messageId: assistantMessageId,
      refs,
      busy: state.running,
      previousAssistantPart: accumulator.partIndex > 0 || accumulator.hasAssistantHeader,
      showAssistantHeader
    });
    accumulator.contextGroupIndex = rows.length - 1;
    markAssistantRowAdded(accumulator);
    return;
  }

  const part = state.partsByMessageId[assistantMessageId]?.find((candidate) => candidate.partId === group.partId);
  if (part?.type === "reasoning") {
    const ref = { messageId: assistantMessageId, partId: group.partId };
    if (typeof accumulator.reasoningGroupIndex === "number") {
      const existing = rows[accumulator.reasoningGroupIndex];
      if (existing?.type === "reasoning-group") {
        existing.refs.push(ref);
      }
      return;
    }
    const showAssistantHeader = !accumulator.hasAssistantHeader;
    rows.push({
      type: "reasoning-group",
      key: `reasoning:${userMessageId}:${assistantMessageId}:${group.partId}`,
      userMessageId,
      messageId: assistantMessageId,
      refs: [ref],
      busy: state.running,
      previousAssistantPart: accumulator.partIndex > 0 || accumulator.hasAssistantHeader,
      showAssistantHeader
    });
    accumulator.reasoningGroupIndex = rows.length - 1;
    markAssistantRowAdded(accumulator);
    return;
  }

  const showAssistantHeader = !accumulator.hasAssistantHeader;
  rows.push({
    type: "assistant-part",
    key: `part:${assistantMessageId}:${group.partId}`,
    userMessageId,
    messageId: assistantMessageId,
    partId: group.partId,
    previousAssistantPart: accumulator.partIndex > 0 || accumulator.hasAssistantHeader,
    showAssistantHeader
  });
  markAssistantRowAdded(accumulator);
}

function markAssistantRowAdded(accumulator: AssistantRowAccumulator): void {
  accumulator.hasAssistantHeader = true;
  accumulator.partIndex += 1;
}

function isActiveTurn(userMessageId: string, state: OpencodeLikeConversationState): boolean {
  return isLatestTurn(userMessageId, state);
}

function isLatestTurn(userMessageId: string, state: OpencodeLikeConversationState): boolean {
  const latest = state.userMessages.at(-1);
  return Boolean(latest && canonicalMessageId(latest) === userMessageId);
}
