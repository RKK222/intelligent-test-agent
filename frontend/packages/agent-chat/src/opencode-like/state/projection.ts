import { canonicalMessageId, groupRenderableParts } from "./part-utils";
import { normalizeToolName } from "./tool-registry";
import { workStatusEventDescriptor } from "./work-status";
import { hasVisibleUserMessageContent } from "../../user-message-display";
import type {
  OpencodeLikeConversationState,
  TimelineRow,
  WorkStatusEventGroup,
  WorkStatusPartRef,
  WorkStatusState
} from "./types";

type WorkStatusAccumulator = {
  reasoningRefs: WorkStatusPartRef[];
  events: WorkStatusEventGroup[];
  eventIndices: Record<string, number>;
};

type AssistantRowAccumulator = {
  hasAssistantHeader: boolean;
  partIndex: number;
  contextGroupIndex?: number;
  reasoningGroupIndex?: number;
  toolPartIndices: Record<string, number>;
  toolGroupIndices: Record<string, number>;
  pendingCompactionIndex?: number;
  workStatus?: WorkStatusAccumulator;
};

export function createTimelineRows(state: OpencodeLikeConversationState): TimelineRow[] {
  const rows: TimelineRow[] = [];
  const aggregateWorkStatus = !state.activeSubagentSessionId;
  let latestWorkStatus: Extract<TimelineRow, { type: "work-status" }> | undefined;
  let latestDiffSummary: Extract<TimelineRow, { type: "diff-summary" }> | undefined;
  let pendingCompactionIndex: number | undefined;

  const orphanAccumulator: AssistantRowAccumulator = {
    hasAssistantHeader: false,
    partIndex: 0,
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
  pendingCompactionIndex = orphanAccumulator.pendingCompactionIndex;

  for (const [index, userMessage] of state.userMessages.entries()) {
    const userMessageId = canonicalMessageId(userMessage);
    // OpenCode 会在 compaction 标记与摘要之间插入一条无正文 user envelope。
    // 仅跨过这种不可见边界；真实用户输入必须终止配对，避免把下一轮回答误当成摘要。
    if (hasVisibleUserMessageContent(userMessage)) {
      pendingCompactionIndex = undefined;
    }
    if (index > 0) {
      rows.push({ type: "turn-gap", key: `gap:${userMessageId}`, userMessageId });
    }

    rows.push({ type: "user-message", key: `user:${userMessageId}`, userMessageId });

    const assistantMessages = state.assistantMessagesByParent[userMessageId] ?? [];
    const accumulator: AssistantRowAccumulator = {
      hasAssistantHeader: false,
      partIndex: 0,
      toolPartIndices: {},
      toolGroupIndices: {},
      pendingCompactionIndex,
      workStatus: aggregateWorkStatus ? createWorkStatusAccumulator() : undefined
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
          userMessageId,
          aggregateWorkStatus
        });
      }
    }
    pendingCompactionIndex = accumulator.pendingCompactionIndex;

    if (isLatestTurn(userMessageId, state) && state.diffFiles.length > 0) {
      latestDiffSummary = { type: "diff-summary", key: `diff:${userMessageId}`, userMessageId, files: state.diffFiles };
    }

    if (aggregateWorkStatus) {
      const runId = turnRunId(userMessage, assistantMessages);
      const runStatus = runId ? state.runStatusesByRunId[runId] : undefined;
      const workStatus: Extract<TimelineRow, { type: "work-status" }> = {
        type: "work-status",
        key: `work-status:${userMessageId}`,
        userMessageId,
        reasoningRefs: accumulator.workStatus?.reasoningRefs ?? [],
        events: accumulator.workStatus?.events ?? [],
        // 显式的分轮空快照同样是所有权证据；只有旧独立消费者完全未提供该键时，
        // 最新轮才回退到兼容字段 todos。
        todos: Object.prototype.hasOwnProperty.call(state.todoSnapshotsByUserMessageId, userMessageId)
          ? state.todoSnapshotsByUserMessageId[userMessageId]
          : isLatestTurn(userMessageId, state) ? state.todos : [],
        status: workStatusState(userMessageId, runStatus, state),
        isLatest: isLatestTurn(userMessageId, state),
        runId,
        runStatus
      };
      if (workStatus.isLatest) {
        latestWorkStatus = workStatus;
      } else {
        rows.push(workStatus);
      }
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
  if (latestWorkStatus) {
    rows.push(latestWorkStatus);
  }
  if (latestDiffSummary) {
    rows.push(latestDiffSummary);
  }

  // 压缩进行态由 composer 上方的专用动效承担；时间线只保留摘要已落稳后的最终结果。
  return rows.filter((row) => row.type !== "compaction-summary" || compactionSummaryReady(row, state));
}

function compactionSummaryReady(
  row: Extract<TimelineRow, { type: "compaction-summary" }>,
  state: OpencodeLikeConversationState
): boolean {
  if (!row.summaryRef) return false;
  const part = state.partsByMessageId[row.summaryRef.messageId]
    ?.find((candidate) => candidate.partId === row.summaryRef?.partId);
  if (part?.type !== "text" || !part.text.trim()) return false;
  const status = part.status?.toLowerCase();
  return status !== "pending" && status !== "running";
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
    aggregateWorkStatus?: boolean;
  }
): void {
  const { group, assistantMessageId, userMessageId, aggregateWorkStatus = false } = params;
  // 会话级 running 只属于最新用户轮次，不能把已结束历史轮次重新投影为进行中。
  const busy = isActiveTurn(userMessageId, state) && state.running;
  if (group.type === "context-tool-group") {
    const refs = group.refs.map((ref) => ({ messageId: assistantMessageId, partId: ref.partId }));
    if (aggregateWorkStatus && accumulator.workStatus) {
      for (const ref of refs) {
        const part = state.partsByMessageId[ref.messageId]?.find((candidate) => candidate.partId === ref.partId);
        if (part?.type === "tool") {
          appendWorkStatusEvent(accumulator.workStatus, ref, part);
        }
      }
      return;
    }
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
  // 原生压缩摘要通常按 step-start → reasoning → text → step-finish 到达。
  // reasoning 等过程 part 不能提前打断配对；待配对状态只由下一段摘要正文消费，或在外层遇到可见用户消息时终止。
  if (part?.type === "text" && typeof accumulator.pendingCompactionIndex === "number") {
    const compactionRow = rows[accumulator.pendingCompactionIndex];
    if (compactionRow?.type === "compaction-summary") {
      compactionRow.summaryRef = { messageId: assistantMessageId, partId: group.partId };
      accumulator.pendingCompactionIndex = undefined;
      return;
    }
    accumulator.pendingCompactionIndex = undefined;
  }
  if (part?.type === "compaction") {
    const showAssistantHeader = !accumulator.hasAssistantHeader;
    rows.push({
      type: "compaction-summary",
      key: `compaction:${assistantMessageId}:${group.partId}`,
      userMessageId,
      messageId: assistantMessageId,
      partId: group.partId,
      previousAssistantPart: accumulator.partIndex > 0 || accumulator.hasAssistantHeader,
      showAssistantHeader
    });
    accumulator.pendingCompactionIndex = rows.length - 1;
    markAssistantRowAdded(accumulator);
    return;
  }
  if (part?.type === "reasoning") {
    const ref = { messageId: assistantMessageId, partId: group.partId };
    if (aggregateWorkStatus && accumulator.workStatus) {
      accumulator.workStatus.reasoningRefs.push(ref);
      return;
    }
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
    // task 是子 Agent 导航入口，question 需要保留每次提问的时间线位置，二者都不能并入普通工具折叠组。
    if (toolKey === "task" || toolKey === "question") {
      appendSingleAssistantPartRow(rows, accumulator, {
        userMessageId,
        messageId: assistantMessageId,
        partId: group.partId,
        busy
      });
      return;
    }
    if (aggregateWorkStatus && accumulator.workStatus) {
      appendWorkStatusEvent(accumulator.workStatus, { messageId: assistantMessageId, partId: group.partId }, part);
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
      partId: group.partId,
      busy
    });
    accumulator.toolPartIndices[toolKey] = rows.length - 1;
    return;
  }

  appendSingleAssistantPartRow(rows, accumulator, {
    userMessageId,
    messageId: assistantMessageId,
    partId: group.partId,
    busy
  });
}

function createWorkStatusAccumulator(): WorkStatusAccumulator {
  return { reasoningRefs: [], events: [], eventIndices: {} };
}

/** 同一类别只占一个图标，并按第一次出现顺序稳定追加后续引用。 */
function appendWorkStatusEvent(
  accumulator: WorkStatusAccumulator,
  ref: WorkStatusPartRef,
  part: Parameters<typeof workStatusEventDescriptor>[0]
): void {
  const descriptor = workStatusEventDescriptor(part);
  const existingIndex = accumulator.eventIndices[descriptor.key];
  if (typeof existingIndex === "number") {
    accumulator.events[existingIndex]?.refs.push(ref);
    return;
  }
  accumulator.eventIndices[descriptor.key] = accumulator.events.length;
  accumulator.events.push({ ...descriptor, refs: [ref] });
}

function workStatusState(userMessageId: string, runStatus: string | undefined, state: OpencodeLikeConversationState): WorkStatusState {
  const normalizedRunStatus = runStatus?.toUpperCase();
  if (normalizedRunStatus === "SUCCEEDED" || normalizedRunStatus === "COMPLETED") return "completed";
  if (normalizedRunStatus === "FAILED") return "failed";
  if (normalizedRunStatus === "CANCELLED" || normalizedRunStatus === "CANCELED") return "cancelled";
  if (!isLatestTurn(userMessageId, state)) return "completed";
  const runtimeType = state.runtimeStatus.type.toLowerCase();
  if (runtimeType === "retry") return "retry";
  if (runtimeType === "failed") return "failed";
  if (runtimeType === "cancelled" || runtimeType === "canceled") return "cancelled";
  if (state.running || runtimeType === "busy" || runtimeType === "running") return "running";
  return "completed";
}

function turnRunId(
  userMessage: Extract<OpencodeLikeConversationState["messages"][number], { role: "user" }>,
  assistantMessages: Extract<OpencodeLikeConversationState["messages"][number], { role: "assistant" }>[]
): string | undefined {
  return userMessage.runId ?? assistantMessages.find((message) => Boolean(message.runId))?.runId;
}

function appendSingleAssistantPartRow(
  rows: TimelineRow[],
  accumulator: AssistantRowAccumulator,
  params: {
    userMessageId: string;
    messageId: string;
    partId: string;
    busy: boolean;
  }
): void {
  const showAssistantHeader = !accumulator.hasAssistantHeader;
  rows.push({
    type: "assistant-part",
    key: `part:${params.messageId}:${params.partId}`,
    userMessageId: params.userMessageId,
    messageId: params.messageId,
    partId: params.partId,
    busy: params.busy,
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
