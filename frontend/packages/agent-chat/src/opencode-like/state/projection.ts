import { canonicalMessageId, groupRenderableParts } from "./part-utils";
import type { OpencodeLikeConversationState, TimelineRow } from "./types";

export function createTimelineRows(state: OpencodeLikeConversationState): TimelineRow[] {
  const rows: TimelineRow[] = [];

  if (state.userMessages.length === 0 && state.orphanAssistantMessages.length === 0 && state.running) {
    rows.push({ type: "thinking", key: "thinking:pending", userMessageId: "__pending__" });
  }

  let orphanHasAssistantHeader = false;
  for (const assistantMessage of state.orphanAssistantMessages) {
    const assistantMessageId = canonicalMessageId(assistantMessage);
    const groups = groupRenderableParts(state.partsByMessageId[assistantMessageId] ?? [], {
      showReasoningSummaries: state.showReasoningSummaries
    });
    let partIndex = 0;
    for (const group of groups) {
      const showAssistantHeader = !orphanHasAssistantHeader;
      if (group.type === "context-tool-group") {
        rows.push({
          type: "context-tool-group",
          key: `${group.key}:${assistantMessageId}`,
          userMessageId: "__orphan__",
          messageId: assistantMessageId,
          refs: group.refs.map((ref) => ({ messageId: assistantMessageId, partId: ref.partId })),
          busy: state.running,
          previousAssistantPart: partIndex > 0 || orphanHasAssistantHeader,
          showAssistantHeader
        });
      } else {
        rows.push({
          type: "assistant-part",
          key: `part:${assistantMessageId}:${group.partId}`,
          userMessageId: "__orphan__",
          messageId: assistantMessageId,
          partId: group.partId,
          previousAssistantPart: partIndex > 0 || orphanHasAssistantHeader,
          showAssistantHeader
        });
      }
      orphanHasAssistantHeader = true;
      partIndex += 1;
    }
  }

  for (const [index, userMessage] of state.userMessages.entries()) {
    const userMessageId = canonicalMessageId(userMessage);
    if (index > 0) {
      rows.push({ type: "turn-gap", key: `gap:${userMessageId}`, userMessageId });
    }

    rows.push({ type: "user-message", key: `user:${userMessageId}`, userMessageId });

    const assistantMessages = state.assistantMessagesByParent[userMessageId] ?? [];
    let hasAssistantHeader = false;
    for (const assistantMessage of assistantMessages) {
      const assistantMessageId = canonicalMessageId(assistantMessage);
      const groups = groupRenderableParts(state.partsByMessageId[assistantMessageId] ?? [], {
        showReasoningSummaries: state.showReasoningSummaries
      });
      let partIndex = 0;

      for (const group of groups) {
        const showAssistantHeader = !hasAssistantHeader;
        if (group.type === "context-tool-group") {
          rows.push({
            type: "context-tool-group",
            key: `${group.key}:${assistantMessageId}`,
            userMessageId,
            messageId: assistantMessageId,
            refs: group.refs.map((ref) => ({ messageId: assistantMessageId, partId: ref.partId })),
            busy: state.running,
            previousAssistantPart: partIndex > 0 || hasAssistantHeader,
            showAssistantHeader
          });
        } else {
          rows.push({
            type: "assistant-part",
            key: `part:${assistantMessageId}:${group.partId}`,
            userMessageId,
            messageId: assistantMessageId,
            partId: group.partId,
            previousAssistantPart: partIndex > 0 || hasAssistantHeader,
            showAssistantHeader
          });
        }
        hasAssistantHeader = true;
        partIndex += 1;
      }
    }

    if (isActiveTurn(userMessageId, state) && state.running) {
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

function isActiveTurn(userMessageId: string, state: OpencodeLikeConversationState): boolean {
  return isLatestTurn(userMessageId, state);
}

function isLatestTurn(userMessageId: string, state: OpencodeLikeConversationState): boolean {
  const latest = state.userMessages.at(-1);
  return Boolean(latest && canonicalMessageId(latest) === userMessageId);
}
