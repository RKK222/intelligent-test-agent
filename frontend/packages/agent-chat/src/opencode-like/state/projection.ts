import { canonicalMessageId, groupRenderableParts } from "./part-utils";
import { normalizeToolName } from "./tool-registry";
import type { OpencodeLikeConversationState, TimelineRow } from "./types";

type AssistantRowAccumulator = {
  hasAssistantHeader: boolean;
  partIndex: number;
  hasVisibleTextOutput: boolean;
  contextGroupIndex?: number;
  reasoningGroupIndex?: number;
  toolPartIndices: Record<string, number>;
  toolGroupIndices: Record<string, number>;
};

export function createTimelineRows(state: OpencodeLikeConversationState): TimelineRow[] {
  const rows: TimelineRow[] = [];

  if (state.userMessages.length === 0 && state.orphanAssistantMessages.length === 0 && state.running && state.runtimeStatus.type !== "retry") {
    rows.push({ type: "thinking", key: "thinking:pending", userMessageId: "__pending__" });
  }

  const orphanAccumulator: AssistantRowAccumulator = {
    hasAssistantHeader: false,
    partIndex: 0,
    hasVisibleTextOutput: false,
    toolPartIndices: {},
    toolGroupIndices: {}
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
      partIndex: 0,
      hasVisibleTextOutput: false,
      toolPartIndices: {},
      toolGroupIndices: {}
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

    if (isActiveTurn(userMessageId, state) && state.running && state.runtimeStatus.type !== "retry" && accumulator.partIndex === 0) {
      rows.push({ type: "thinking", key: `thinking:${userMessageId}`, userMessageId });
    }
    // 已出现工具/思考过程但文本尚未开始时，只追加一个轻量工作态行，避免恢复空 text 占位。
    if (
      isActiveTurn(userMessageId, state) &&
      state.running &&
      state.runtimeStatus.type !== "retry" &&
      accumulator.partIndex > 0 &&
      !accumulator.hasVisibleTextOutput
    ) {
      rows.push({
        type: "working-status",
        key: `working:${userMessageId}`,
        userMessageId,
        previousAssistantPart: accumulator.partIndex > 0 || accumulator.hasAssistantHeader,
        showAssistantHeader: !accumulator.hasAssistantHeader
      });
    }

    if (isLatestTurn(userMessageId, state) && state.diffFiles.length > 0) {
      rows.push({ type: "diff-summary", key: `diff:${userMessageId}`, userMessageId, files: state.diffFiles });
    }
  }

  if (state.runtimeStatus.type === "failed") {
    rows.push({ type: "error", key: "runtime:error", message: state.runtimeStatus.message ?? "运行失败" });
  }
  if (state.runtimeStatus.type === "retry") {
    rows.push({
      type: "retry",
      key: "runtime:retry",
      userMessageId: latestUserMessageId(state) ?? "__pending__",
      attempt: state.runtimeStatus.attempt,
      maxAttempts: state.runtimeStatus.maxAttempts,
      retryAfterSeconds: state.runtimeStatus.retryAfterSeconds,
      message: state.runtimeStatus.message,
      action: state.runtimeStatus.action
    });
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
  // 会话级 running 只属于最新用户轮次，不能把已结束历史轮次重新投影为进行中。
  const busy = isActiveTurn(userMessageId, state) && state.running;
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
      busy,
      previousAssistantPart: accumulator.partIndex > 0 || accumulator.hasAssistantHeader,
      showAssistantHeader
    });
    accumulator.contextGroupIndex = rows.length - 1;
    markAssistantRowAdded(accumulator);
    return;
  }

  const part = state.partsByMessageId[assistantMessageId]?.find((candidate) => candidate.partId === group.partId);
  if (part?.type === "text") {
    accumulator.hasVisibleTextOutput = true;
  }
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
      busy,
      previousAssistantPart: accumulator.partIndex > 0 || accumulator.hasAssistantHeader,
      showAssistantHeader
    });
    accumulator.reasoningGroupIndex = rows.length - 1;
    markAssistantRowAdded(accumulator);
    return;
  }

  if (part?.type === "tool") {
    const toolKey = normalizeToolName(part);
    // task 是子 Agent 导航入口，必须直接可见，不能并入普通工具折叠组。
    if (toolKey === "task") {
      appendSingleAssistantPartRow(rows, accumulator, {
        userMessageId,
        messageId: assistantMessageId,
        partId: group.partId
      });
      return;
    }
    const ref = { messageId: assistantMessageId, partId: group.partId };
    const existingIndex = accumulator.toolGroupIndices[toolKey];
    if (typeof existingIndex === "number") {
      const existing = rows[existingIndex];
      if (existing?.type === "tool-group") {
        existing.refs.push(ref);
      }
      return;
    }

    const firstPartIndex = accumulator.toolPartIndices[toolKey];
    const firstRow = typeof firstPartIndex === "number" ? rows[firstPartIndex] : undefined;
    if (firstRow?.type === "assistant-part") {
      rows[firstPartIndex] = {
        type: "tool-group",
        key: `tool:${toolKey}:${firstRow.userMessageId}:${firstRow.messageId}:${firstRow.partId}`,
        userMessageId: firstRow.userMessageId,
        messageId: firstRow.messageId,
        refs: [
          { messageId: firstRow.messageId, partId: firstRow.partId },
          ref
        ],
        busy,
        previousAssistantPart: firstRow.previousAssistantPart,
        showAssistantHeader: firstRow.showAssistantHeader
      };
      accumulator.toolGroupIndices[toolKey] = firstPartIndex;
      return;
    }

    appendSingleAssistantPartRow(rows, accumulator, {
      userMessageId,
      messageId: assistantMessageId,
      partId: group.partId
    });
    accumulator.toolPartIndices[toolKey] = rows.length - 1;
    return;
  }

  appendSingleAssistantPartRow(rows, accumulator, {
    userMessageId,
    messageId: assistantMessageId,
    partId: group.partId
  });
}

function appendSingleAssistantPartRow(
  rows: TimelineRow[],
  accumulator: AssistantRowAccumulator,
  params: {
    userMessageId: string;
    messageId: string;
    partId: string;
  }
): void {
  const showAssistantHeader = !accumulator.hasAssistantHeader;
  rows.push({
    type: "assistant-part",
    key: `part:${params.messageId}:${params.partId}`,
    userMessageId: params.userMessageId,
    messageId: params.messageId,
    partId: params.partId,
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

function latestUserMessageId(state: OpencodeLikeConversationState): string | undefined {
  const latest = state.userMessages.at(-1);
  return latest ? canonicalMessageId(latest) : undefined;
}
