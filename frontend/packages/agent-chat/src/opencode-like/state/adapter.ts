import type { AgentMessage, MessagePart, RunDiffFile } from "@test-agent/shared-types";
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
    running: input.running ?? runtimeStatus.type === "busy",
    showReasoningSummaries: input.showReasoningSummaries ?? true,
    messageScopesById: input.messageScopesById ?? {},
    subagentsBySessionId: input.subagentsBySessionId ?? {},
    subagentByTaskPartId: input.subagentByTaskPartId ?? {},
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
    return scope?.sessionId === activeSubagentSessionId;
  }
  return scope?.isChildSession !== true;
}

function partsForAssistant(message: Extract<AgentMessage, { role: "assistant" }>): MessagePart[] {
  if (message.parts?.length) {
    return message.parts;
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

function runtimeStatusFromLegacy(status: string | undefined, running: boolean | undefined): OpencodeLikeRuntimeStatus {
  const normalized = status?.toLowerCase();
  if (normalized === "failed") {
    return { type: "failed" };
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
