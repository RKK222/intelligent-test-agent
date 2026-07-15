import type { AgentMessage, MessagePart, RunDiffFile, SubagentSession } from "@test-agent/shared-types";
import { createModelCatalog } from "./model-catalog";
import { canonicalMessageId } from "./part-utils";
import type { OpencodeLikeConversationInput, OpencodeLikeConversationState, OpencodeLikeRuntimeStatus } from "./types";

export function createOpencodeLikeState(input: OpencodeLikeConversationInput): OpencodeLikeConversationState {
  const messageById: Record<string, AgentMessage> = {};
  const userMessages: Extract<AgentMessage, { role: "user" }>[] = [];
  const orphanAssistantMessages: Extract<AgentMessage, { role: "assistant" }>[] = [];
  const assistantMessagesByParent: Record<string, Extract<AgentMessage, { role: "assistant" }>[]> = {};
  const partsByMessageId: Record<string, MessagePart[]> = {};
  let currentUserId: string | undefined;
  const activeSubagentSessionId = input.activeSubagentSessionId ?? null;

  for (const message of input.messages) {
    if (message.role === "card") {
      continue;
    }
    const id = canonicalMessageId(message);
    if (!messageVisibleInView(id, input, activeSubagentSessionId)) {
      continue;
    }
    messageById[message.id] = message;
    messageById[id] = message;

    if (message.role === "user") {
      userMessages.push(message);
      currentUserId = id;
      continue;
    }

    partsByMessageId[id] = partsForAssistant(message);
    if (currentUserId) {
      assistantMessagesByParent[currentUserId] = [...(assistantMessagesByParent[currentUserId] ?? []), message];
    } else {
      orphanAssistantMessages.push(message);
    }
  }

  const runtimeStatus = input.runtimeStatus ?? runtimeStatusFromLegacy(input.status, input.running);
  const diffFiles = input.diffFiles ?? input.diff?.files ?? diffFilesFromCards(input.messages);
  const subagentByTaskPartId = { ...(input.subagentByTaskPartId ?? {}) };
  if (!activeSubagentSessionId) {
    appendSyntheticSubagentEntries({
      subagents: input.subagentsBySessionId ?? {},
      subagentByTaskPartId,
      partsByMessageId,
      messageById,
      userMessages,
      assistantMessagesByParent,
      orphanAssistantMessages
    });
  }

  return {
    messages: input.messages,
    messageById,
    userMessages,
    orphanAssistantMessages,
    assistantMessagesByParent,
    partsByMessageId,
    streamingTextByPartId: input.streamingTextByPartId ?? {},
    modelCatalog: createModelCatalog(input.providers, input.models),
    runtimeStatus,
    diffFiles,
    permissions: input.permissions ?? [],
    questions: input.questions ?? [],
    todos: input.todos ?? [],
    todoSnapshotsByUserMessageId: input.todoSnapshotsByUserMessageId ?? {},
    running: input.running ?? (runtimeStatus.type === "busy" || runtimeStatus.type === "retry"),
    showReasoningSummaries: input.showReasoningSummaries ?? true,
    messageScopesById: input.messageScopesById ?? {},
    subagentsBySessionId: input.subagentsBySessionId ?? {},
    subagentByTaskPartId,
    activeSubagentSessionId
  };
}

function messageVisibleInView(
  messageId: string,
  input: OpencodeLikeConversationInput,
  activeSubagentSessionId: string | null
): boolean {
  const scope = input.messageScopesById?.[messageId];
  if (activeSubagentSessionId) {
    if (scope?.sessionId === activeSubagentSessionId) {
      return true;
    }
    const activeSubagent = input.subagentsBySessionId?.[activeSubagentSessionId];
    if (scope?.isChildSession && activeSubagent?.taskPartId && scope.taskPartId === activeSubagent.taskPartId) {
      return true;
    }
    if (scope?.isChildSession && activeSubagent?.taskCallId && scope.taskCallId === activeSubagent.taskCallId) {
      return true;
    }
    return false;
  }
  return scope?.isChildSession !== true;
}

function partsForAssistant(message: Extract<AgentMessage, { role: "assistant" }>): MessagePart[] {
  if (message.parts?.length) {
    return [...message.parts];
  }
  if (!message.text.trim()) {
    return [];
  }
  return [
    {
      partId: `${message.messageId ?? message.id}:text`,
      type: "text",
      text: message.text,
      status: "completed"
    }
  ];
}

function appendSyntheticSubagentEntries(params: {
  subagents: Record<string, SubagentSession>;
  subagentByTaskPartId: Record<string, string>;
  partsByMessageId: Record<string, MessagePart[]>;
  messageById: Record<string, AgentMessage>;
  userMessages: Extract<AgentMessage, { role: "user" }>[];
  assistantMessagesByParent: Record<string, Extract<AgentMessage, { role: "assistant" }>[]>;
  orphanAssistantMessages: Extract<AgentMessage, { role: "assistant" }>[];
}): void {
  for (const subagent of Object.values(params.subagents)) {
    const partId = subagent.taskPartId ?? `subagent:${subagent.sessionId}`;
    if (hasTaskPart(params.partsByMessageId, partId)) {
      params.subagentByTaskPartId[partId] = subagent.sessionId;
      continue;
    }
    const messageId = chooseSyntheticMessageId(subagent, params.partsByMessageId);
    if (!messageId) {
      continue;
    }
    const targetMessageId = messageId;
    const targetMessage = ensureSyntheticAssistantMessage(params, targetMessageId, subagent);
    const canonicalId = canonicalMessageId(targetMessage);
    params.partsByMessageId[canonicalId] = [
      ...(params.partsByMessageId[canonicalId] ?? []),
      syntheticSubagentPart(partId, subagent)
    ];
    params.subagentByTaskPartId[partId] = subagent.sessionId;
  }
}

function hasTaskPart(partsByMessageId: Record<string, MessagePart[]>, partId: string): boolean {
  return Object.values(partsByMessageId).some((parts) =>
    parts.some((part) => part.partId === partId && part.type === "tool" && part.toolName === "task")
  );
}

function chooseSyntheticMessageId(
  subagent: SubagentSession,
  partsByMessageId: Record<string, MessagePart[]>
): string | undefined {
  if (subagent.taskMessageId && Object.prototype.hasOwnProperty.call(partsByMessageId, subagent.taskMessageId)) {
    return subagent.taskMessageId;
  }
  // 子 Agent 入口只能补回原始 task message；原消息已移除时不猜测最新轮次，避免旧子 Agent 串到后续对话。
  return undefined;
}

function ensureSyntheticAssistantMessage(
  params: {
    messageById: Record<string, AgentMessage>;
    userMessages: Extract<AgentMessage, { role: "user" }>[];
    assistantMessagesByParent: Record<string, Extract<AgentMessage, { role: "assistant" }>[]>;
    orphanAssistantMessages: Extract<AgentMessage, { role: "assistant" }>[];
    partsByMessageId: Record<string, MessagePart[]>;
  },
  messageId: string,
  subagent: SubagentSession
): Extract<AgentMessage, { role: "assistant" }> {
  const existing = params.messageById[messageId];
  if (existing?.role === "assistant") {
    return existing;
  }
  const synthetic: Extract<AgentMessage, { role: "assistant" }> = {
    id: messageId,
    messageId,
    role: "assistant",
    text: "",
    createdAt: subagent.updatedAt,
    parts: []
  };
  params.messageById[messageId] = synthetic;
  params.partsByMessageId[messageId] = params.partsByMessageId[messageId] ?? [];
  const latestUser = params.userMessages.at(-1);
  if (latestUser) {
    const userId = canonicalMessageId(latestUser);
    params.assistantMessagesByParent[userId] = [...(params.assistantMessagesByParent[userId] ?? []), synthetic];
  } else {
    params.orphanAssistantMessages.push(synthetic);
  }
  return synthetic;
}

function syntheticSubagentPart(partId: string, subagent: SubagentSession): Extract<MessagePart, { type: "tool" }> {
  return {
    partId,
    type: "tool",
    toolName: "task",
    callId: subagent.taskCallId,
    status: subagent.status || "running",
    input: {
      description: subagent.title,
      subagent_type: subagent.agentName
    },
    metadata: {
      sessionId: subagent.sessionId,
      parentSessionId: subagent.parentSessionId,
      synthetic: true
    },
    startedAt: subagent.updatedAt
  };
}

function runtimeStatusFromLegacy(status: string | undefined, running: boolean | undefined): OpencodeLikeRuntimeStatus {
  const normalized = status?.toLowerCase();
  if (normalized === "failed") {
    return { type: "failed" };
  }
  if (normalized === "retry") {
    return { type: "retry" };
  }
  if (normalized === "cancelled" || normalized === "canceled") {
    return { type: "cancelled" };
  }
  if (running || normalized === "pending" || normalized === "running" || normalized === "cancelling") {
    return { type: "busy" };
  }
  return { type: "idle" };
}

function diffFilesFromCards(messages: AgentMessage[]): RunDiffFile[] {
  const files: RunDiffFile[] = [];
  for (const message of messages) {
    if (message.role !== "card" || message.cardType !== "diff") {
      continue;
    }
    const rawFiles = Array.isArray(message.payload.files) ? message.payload.files : [];
    for (const file of rawFiles) {
      if (!isRecord(file) || typeof file.path !== "string") {
        continue;
      }
      files.push({
        path: file.path,
        patch: typeof file.patch === "string" ? file.patch : "",
        additions: typeof file.additions === "number" ? file.additions : 0,
        deletions: typeof file.deletions === "number" ? file.deletions : 0,
        status: typeof file.status === "string" ? file.status : "modified"
      });
    }
  }
  return files;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
