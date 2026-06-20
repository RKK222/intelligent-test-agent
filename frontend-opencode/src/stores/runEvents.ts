import { defineStore } from "pinia";
import { computed, reactive, ref } from "vue";
import { subscribeRunEvents, type RunEventSubscription, type RunEventStreamStatus } from "@test-agent/event-stream-client";
import type {
  MessagePart,
  PermissionRequest,
  QuestionRequest,
  RunEvent,
  RunEventType,
  SessionDiff,
  TodoItem
} from "@test-agent/shared-types";

export type RunMessageProjection = {
  messageId: string;
  sessionId?: string;
  role: string;
  text: string;
  parts: Record<string, MessagePart & { text?: string }>;
  updatedAt?: string;
};

export type RunEventState = {
  seenEventIds: string[];
  messages: Record<string, RunMessageProjection>;
  todos: Record<string, TodoItem[]>;
  permissions: Record<string, PermissionRequest[]>;
  questions: Record<string, QuestionRequest[]>;
  diffs: Record<string, SessionDiff>;
  statuses: Record<string, string>;
  latestType?: RunEventType;
};

export function createRunEventState(): RunEventState {
  return {
    seenEventIds: [],
    messages: {},
    todos: {},
    permissions: {},
    questions: {},
    diffs: {},
    statuses: {}
  };
}

export function reduceRunEvent(state: RunEventState, event: RunEvent) {
  if (state.seenEventIds.includes(event.eventId)) {
    return;
  }
  state.seenEventIds.push(event.eventId);
  state.latestType = event.type;

  if (event.type === "message.updated") {
    mergeMessage(state, event.payload);
    return;
  }
  if (event.type === "message.removed") {
    const messageId = text(event.payload.messageId) ?? text(event.payload.messageID);
    if (messageId) {
      delete state.messages[messageId];
    }
    return;
  }
  if (event.type === "message.part.updated") {
    mergePart(state, event.payload, false);
    return;
  }
  if (event.type === "message.part.delta") {
    mergePart(state, event.payload, true);
    return;
  }
  if (event.type === "message.part.removed") {
    removePart(state, event.payload);
    return;
  }
  if (event.type === "todo.updated") {
    const sessionId = sessionKey(event.payload);
    state.todos[sessionId] = arrayOfRecords(event.payload.todos ?? event.payload.items).map(toTodo);
    return;
  }
  if (event.type === "permission.asked") {
    const request = toPermission(event.payload);
    upsertById(state.permissions, request.sessionId, request, "requestId");
    return;
  }
  if (event.type === "permission.replied") {
    removeByRequestId(state.permissions, sessionKey(event.payload), text(event.payload.requestId) ?? text(event.payload.requestID));
    return;
  }
  if (event.type === "question.asked") {
    const request = toQuestion(event.payload);
    upsertById(state.questions, request.sessionId, request, "requestId");
    return;
  }
  if (event.type === "question.replied" || event.type === "question.rejected") {
    removeByRequestId(state.questions, sessionKey(event.payload), text(event.payload.requestId) ?? text(event.payload.requestID));
    return;
  }
  if (event.type === "session.diff") {
    const sessionId = sessionKey(event.payload);
    state.diffs[sessionId] = {
      sessionId,
      messageId: text(event.payload.messageId) ?? text(event.payload.messageID),
      files: arrayOfRecords(event.payload.files).map((item) => ({
        path: text(item.path) ?? "",
        patch: text(item.patch) ?? text(item.diff) ?? "",
        additions: number(item.additions) ?? 0,
        deletions: number(item.deletions) ?? 0,
        status: text(item.status) ?? "modified"
      }))
    };
    return;
  }
  if (event.type === "session.status") {
    state.statuses[sessionKey(event.payload)] = text(event.payload.status) ?? "unknown";
  }
}

export const useRunEventStore = defineStore("run-events", () => {
  const state = reactive(createRunEventState());
  const streamStatus = ref<RunEventStreamStatus>("closed");
  const activeRunId = ref<string>();
  let subscription: RunEventSubscription | undefined;

  const timelineMessages = computed(() =>
    Object.values(state.messages).sort((a, b) => (a.updatedAt ?? "").localeCompare(b.updatedAt ?? ""))
  );

  function apply(event: RunEvent) {
    reduceRunEvent(state, event);
  }

  function subscribe(runId: string, baseUrl = "") {
    close();
    activeRunId.value = runId;
    subscription = subscribeRunEvents({
      runId,
      baseUrl: resolveEventBaseUrl(baseUrl),
      onEvent: apply,
      onStatus: (status) => {
        streamStatus.value = status;
      }
    });
  }

  function close() {
    subscription?.close();
    subscription = undefined;
    streamStatus.value = "closed";
  }

  return { state, streamStatus, activeRunId, timelineMessages, apply, subscribe, close };
});

function resolveEventBaseUrl(baseUrl: string) {
  if (baseUrl.trim()) {
    return baseUrl;
  }
  // Vite 本地 mock 与同源部署场景下，EventSource 需要可解析的绝对 origin。
  return globalThis.location?.origin ?? "http://127.0.0.1:8080";
}

function mergeMessage(state: RunEventState, payload: Record<string, unknown>) {
  const raw = record(payload.message) ?? payload;
  const messageId = text(raw.messageId) ?? text(raw.messageID) ?? text(raw.id);
  if (!messageId) {
    return;
  }
  const current = ensureMessage(state, messageId);
  current.sessionId = text(raw.sessionId) ?? text(raw.sessionID) ?? current.sessionId;
  current.role = text(raw.role) ?? current.role;
  current.text = text(raw.content) ?? text(raw.text) ?? current.text;
  current.updatedAt = text(raw.updatedAt) ?? text(raw.time) ?? current.updatedAt;
}

function mergePart(state: RunEventState, payload: Record<string, unknown>, append: boolean) {
  const raw = record(payload.part) ?? payload;
  const messageId = text(payload.messageId) ?? text(payload.messageID) ?? text(raw.messageId) ?? text(raw.messageID);
  const partId = text(raw.partId) ?? text(raw.partID) ?? text(raw.id);
  if (!messageId || !partId) {
    return;
  }
  const message = ensureMessage(state, messageId);
  const existing = message.parts[partId] as (MessagePart & { text?: string }) | undefined;
  const nextText = text(raw.text) ?? text(raw.content) ?? "";
  message.parts[partId] = {
    partId,
    type: (text(raw.type) as MessagePart["type"]) ?? existing?.type ?? "text",
    status: text(raw.status) ?? partStatus(existing),
    text: append ? `${existing?.text ?? ""}${nextText}` : nextText || existing?.text || "",
    ...(raw.toolName ? { toolName: text(raw.toolName) ?? "" } : {}),
    ...(record(raw.input) ? { input: record(raw.input) } : {}),
    ...(raw.output !== undefined ? { output: raw.output } : {})
  } as MessagePart & { text?: string };
  message.text = Object.values(message.parts)
    .map((part) => ("text" in part ? part.text : ""))
    .filter(Boolean)
    .join("");
  message.updatedAt = new Date().toISOString();
}

function removePart(state: RunEventState, payload: Record<string, unknown>) {
  const messageId = text(payload.messageId) ?? text(payload.messageID);
  const partId = text(payload.partId) ?? text(payload.partID);
  if (messageId && partId && state.messages[messageId]) {
    delete state.messages[messageId].parts[partId];
  }
}

function ensureMessage(state: RunEventState, messageId: string): RunMessageProjection {
  state.messages[messageId] ??= {
    messageId,
    role: "assistant",
    text: "",
    parts: {}
  };
  return state.messages[messageId];
}

function toTodo(value: Record<string, unknown>): TodoItem {
  return {
    id: text(value.id) ?? text(value.todoId) ?? "todo",
    text: text(value.text) ?? text(value.content) ?? text(value.title) ?? "",
    status: text(value.status) ?? "pending",
    priority: text(value.priority)
  };
}

function toPermission(payload: Record<string, unknown>): PermissionRequest {
  return {
    requestId: text(payload.requestId) ?? text(payload.requestID) ?? text(payload.id) ?? "permission",
    sessionId: sessionKey(payload),
    type: text(payload.type) ?? "permission",
    title: text(payload.title),
    description: text(payload.description),
    pattern: text(payload.pattern),
    createdAt: text(payload.createdAt) ?? new Date().toISOString()
  };
}

function toQuestion(payload: Record<string, unknown>): QuestionRequest {
  const requestId = text(payload.requestId) ?? text(payload.requestID) ?? text(payload.id) ?? "question";
  return {
    requestId,
    sessionId: sessionKey(payload),
    questions: arrayOfRecords(payload.questions).map((item, index) => ({
      questionId: text(item.questionId) ?? text(item.questionID) ?? text(item.id) ?? `${requestId}:${index}`,
      text: text(item.text) ?? text(item.question) ?? "",
      kind: text(item.kind) ?? text(item.type) ?? "text",
      options: arrayOfRecords(item.options).map((option) => ({
        id: text(option.id) ?? text(option.value) ?? text(option.label) ?? "option",
        label: text(option.label) ?? text(option.value) ?? text(option.id) ?? "option",
        description: text(option.description)
      })),
      required: typeof item.required === "boolean" ? item.required : undefined
    })),
    createdAt: text(payload.createdAt) ?? new Date().toISOString()
  };
}

function upsertById<T extends Record<string, unknown>, K extends keyof T>(
  map: Record<string, T[]>,
  key: string,
  item: T,
  idKey: K
) {
  map[key] ??= [];
  const index = map[key].findIndex((entry) => entry[idKey] === item[idKey]);
  if (index >= 0) {
    map[key][index] = item;
  } else {
    map[key].push(item);
  }
}

function removeByRequestId<T extends { requestId: string }>(map: Record<string, T[]>, key: string, requestId?: string) {
  if (!requestId || !map[key]) {
    return;
  }
  map[key] = map[key].filter((item) => item.requestId !== requestId);
}

function sessionKey(payload: Record<string, unknown>) {
  return text(payload.sessionId) ?? text(payload.sessionID) ?? "global";
}

function arrayOfRecords(value: unknown) {
  return Array.isArray(value) ? value.filter((item): item is Record<string, unknown> => typeof item === "object" && item !== null) : [];
}

function record(value: unknown) {
  return typeof value === "object" && value !== null && !Array.isArray(value) ? (value as Record<string, unknown>) : undefined;
}

function text(value: unknown) {
  return typeof value === "string" ? value : undefined;
}

function number(value: unknown) {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

function partStatus(part: (MessagePart & { text?: string }) | undefined) {
  return part && "status" in part ? part.status : undefined;
}
