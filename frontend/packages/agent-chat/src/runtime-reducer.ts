import type {
  AgentMessage,
  MessagePart,
  PermissionRequest,
  PromptPart,
  QuestionRequest,
  RunDiffFile,
  RunEvent,
  SessionDiff,
  TodoItem
} from "@test-agent/shared-types";

export type AgentChatRuntimeState = {
  messages: AgentMessage[];
  permissions: PermissionRequest[];
  questions: QuestionRequest[];
  todos: TodoItem[];
  diff?: SessionDiff;
  status?: string;
};

export type AgentChatRuntimeAction =
  | { type: "event"; event: RunEvent }
  | { type: "user.submitted"; prompt: string; parts?: PromptPart[]; createdAt?: string }
  | { type: "permission.replied"; requestId: string }
  | { type: "question.replied"; requestId: string }
  | { type: "reset"; messages?: AgentMessage[] };

export function createInitialAgentChatRuntimeState(messages: AgentMessage[] = []): AgentChatRuntimeState {
  return {
    messages,
    permissions: [],
    questions: [],
    todos: []
  };
}

// 将 RunEvent 归并为对话展示状态，保持 reducer 纯函数，页面只负责订阅和发起 API 调用。
export function reduceAgentChatRuntime(
  state: AgentChatRuntimeState,
  action: AgentChatRuntimeAction
): AgentChatRuntimeState {
  if (action.type === "reset") {
    return createInitialAgentChatRuntimeState(action.messages ?? []);
  }
  if (action.type === "permission.replied") {
    return { ...state, permissions: state.permissions.filter((item) => item.requestId !== action.requestId) };
  }
  if (action.type === "question.replied") {
    return { ...state, questions: state.questions.filter((item) => item.requestId !== action.requestId) };
  }
  if (action.type === "user.submitted") {
    const now = action.createdAt ?? new Date().toISOString();
    return {
      ...state,
      messages: [...state.messages, { id: `user-${Date.now()}`, role: "user", text: action.prompt, parts: action.parts, createdAt: now }]
    };
  }

  const event = action.event;
  if (event.type === "assistant.message.delta") {
    return { ...state, messages: appendAssistantDelta(state.messages, text(event.payload.text) ?? text(event.payload.delta) ?? "", event) };
  }
  if (event.type === "message.updated") {
    return { ...state, messages: upsertMessage(state.messages, event.payload, event) };
  }
  if (event.type === "message.removed") {
    const messageId = text(event.payload.messageId) ?? text(event.payload.messageID) ?? text(event.payload.id);
    return messageId
      ? {
          ...state,
          messages: state.messages.filter(
            (message) => message.id !== messageId && (message.role === "card" || message.messageId !== messageId)
          )
        }
      : state;
  }
  if (event.type === "message.part.delta") {
    return { ...state, messages: mergePartDelta(state.messages, event) };
  }
  if (event.type === "message.part.updated") {
    return { ...state, messages: upsertPart(state.messages, event) };
  }
  if (event.type === "message.part.removed") {
    return { ...state, messages: removePart(state.messages, event) };
  }
  if (event.type === "tool.started" || event.type === "tool.finished") {
    return {
      ...state,
      messages: upsertToolCard(
        state.messages,
        event.type === "tool.started" ? "工具调用开始" : "工具调用完成",
        event
      )
    };
  }
  if (event.type === "diff.proposed" || event.type === "session.diff") {
    const files = diffFilesFromPayload(event.payload);
    const diff = {
      sessionId: text(event.payload.sessionId) ?? text(event.payload.sessionID) ?? "",
      messageId: text(event.payload.messageId) ?? text(event.payload.messageID),
      files
    };
    return {
      ...state,
      diff,
      messages: files.length > 0 ? appendCard(state.messages, "diff", "Agent 提出了文件修改", { files }, event) : state.messages
    };
  }
  if (event.type === "diff.accepted" || event.type === "diff.rejected" || event.type === "test.finished") {
    return {
      ...state,
      messages: appendCard(
        state.messages,
        event.type === "test.finished" ? "test" : "event",
        event.type === "diff.accepted" ? "Diff 已接受" : event.type === "diff.rejected" ? "Diff 已拒绝" : "测试运行完成",
        event.payload,
        event
      )
    };
  }
  if (event.type === "permission.asked") {
    return { ...state, permissions: upsertById(state.permissions, toPermissionRequest(event.payload, event)) };
  }
  if (event.type === "permission.replied") {
    const requestId = text(event.payload.requestId) ?? text(event.payload.requestID) ?? text(event.payload.id);
    return requestId ? { ...state, permissions: state.permissions.filter((item) => item.requestId !== requestId) } : state;
  }
  if (event.type === "question.asked") {
    return { ...state, questions: upsertById(state.questions, toQuestionRequest(event.payload, event)) };
  }
  if (event.type === "question.replied" || event.type === "question.rejected") {
    const requestId = text(event.payload.requestId) ?? text(event.payload.requestID) ?? text(event.payload.id);
    return requestId ? { ...state, questions: state.questions.filter((item) => item.requestId !== requestId) } : state;
  }
  if (event.type === "todo.updated") {
    const raw = Array.isArray(event.payload.todo) ? event.payload.todo : Array.isArray(event.payload.items) ? event.payload.items : [];
    return {
      ...state,
      todos: raw
        .filter((item): item is Record<string, unknown> => typeof item === "object" && item !== null)
        .map(toTodoItem)
    };
  }
  if (event.type === "session.status") {
    return { ...state, status: text(event.payload.status) ?? state.status };
  }
  if (event.type === "run.started" || event.type === "run.created" || event.type === "run.cancelling" || event.type === "run.succeeded" || event.type === "run.failed" || event.type === "run.cancelled") {
    return { ...state, status: runStatusFromEvent(event) };
  }
  return state;
}

function appendAssistantDelta(messages: AgentMessage[], delta: string, event: RunEvent) {
  if (!delta) {
    return messages;
  }
  const last = messages.at(-1);
  if (last?.role === "assistant") {
    return [...messages.slice(0, -1), { ...last, text: `${last.text}${delta}` }];
  }
  return [
    ...messages,
    { id: `assistant-${event.seq}`, role: "assistant", text: delta, createdAt: event.occurredAt }
  ] satisfies AgentMessage[];
}

function mergePartDelta(messages: AgentMessage[], event: RunEvent) {
  const messageId = text(event.payload.messageId) ?? text(event.payload.messageID) ?? `assistant-${event.runId}`;
  const partId = text(event.payload.partId) ?? text(event.payload.partID) ?? `part-${event.seq}`;
  const partType = text(event.payload.partType) ?? text(event.payload.partKind);
  const delta = text(event.payload.delta) ?? text(event.payload.text) ?? "";
  const existing = findAssistantMessage(messages, messageId);
  const assistant =
    existing.message ??
    ({
      id: messageId,
      role: "assistant",
      messageId,
      text: "",
      createdAt: event.occurredAt,
      parts: []
    } satisfies Extract<AgentMessage, { role: "assistant" }>);

  const parts = [...(assistant.parts ?? [])];
  const index = parts.findIndex((part) => part.partId === partId);
  const current = index >= 0 ? parts[index] : undefined;
  const nextPart = mergeTextualPart(current, partId, partType, delta);
  if (index >= 0) {
    parts[index] = nextPart;
  } else {
    parts.push(nextPart);
  }
  const nextAssistant = {
    ...assistant,
    text: nextPart.type === "text" ? `${assistant.text}${delta}` : assistant.text,
    parts
  };
  return replaceOrAppendMessage(messages, existing.index, nextAssistant);
}

function mergeTextualPart(current: MessagePart | undefined, partId: string, partType: string | undefined, delta: string): MessagePart {
  const currentTextType = current?.type === "text" || current?.type === "reasoning" ? current.type : undefined;
  const type = partType === "reasoning" || partType === "text" ? partType : currentTextType ?? "text";
  const textValue =
    current && (current.type === "text" || current.type === "reasoning") ? `${current.text}${delta}` : delta;
  if (type === "reasoning") {
    return {
      ...(current?.type === "reasoning" ? current : {}),
      partId,
      type: "reasoning",
      text: textValue,
      status: "running"
    };
  }
  return { partId, type: "text", text: textValue, status: "running" };
}

function upsertPart(messages: AgentMessage[], event: RunEvent) {
  const raw = record(event.payload.part) ?? event.payload;
  const messageId = text(event.payload.messageId) ?? text(event.payload.messageID) ?? text(raw.messageId) ?? text(raw.messageID);
  const partId = text(raw.partId) ?? text(raw.partID) ?? text(raw.id);
  if (!messageId || !partId) {
    return messages;
  }
  const existing = findAssistantMessage(messages, messageId);
  const assistant =
    existing.message ??
    ({
      id: messageId,
      role: "assistant",
      messageId,
      text: "",
      createdAt: event.occurredAt,
      parts: []
    } satisfies Extract<AgentMessage, { role: "assistant" }>);
  const part = toMessagePart(raw, partId);
  const parts = [...(assistant.parts ?? [])];
  const index = parts.findIndex((item) => item.partId === partId);
  if (index >= 0) {
    parts[index] = part;
  } else {
    parts.push(part);
  }
  return replaceOrAppendMessage(messages, existing.index, {
    ...assistant,
    text: part.type === "text" ? part.text : assistant.text,
    parts
  });
}

function removePart(messages: AgentMessage[], event: RunEvent) {
  const messageId = text(event.payload.messageId) ?? text(event.payload.messageID);
  const partId = text(event.payload.partId) ?? text(event.payload.partID) ?? text(event.payload.id);
  if (!messageId || !partId) {
    return messages;
  }
  return messages.map((message) => {
    if (message.role !== "assistant" || message.messageId !== messageId) {
      return message;
    }
    return { ...message, parts: (message.parts ?? []).filter((part) => part.partId !== partId) };
  });
}

function upsertMessage(messages: AgentMessage[], payload: Record<string, unknown>, event: RunEvent) {
  const raw = record(payload.message) ?? payload;
  const messageId = text(raw.messageId) ?? text(raw.messageID) ?? text(raw.id) ?? `message-${event.seq}`;
  const role = text(raw.role) === "user" ? "user" : "assistant";
  const message = {
    id: messageId,
    role,
    messageId,
    text: text(raw.text) ?? text(raw.content) ?? "",
    createdAt: text(raw.createdAt) ?? event.occurredAt
  } satisfies Extract<AgentMessage, { role: "assistant" | "user" }>;
  const index = messages.findIndex((item) => item.id === messageId || (item.role !== "card" && item.messageId === messageId));
  return replaceOrAppendMessage(messages, index, message);
}

function findAssistantMessage(messages: AgentMessage[], messageId: string) {
  const index = messages.findIndex((message) => message.role === "assistant" && (message.messageId === messageId || message.id === messageId));
  return {
    index,
    message: index >= 0 ? (messages[index] as Extract<AgentMessage, { role: "assistant" }>) : undefined
  };
}

function replaceOrAppendMessage(messages: AgentMessage[], index: number, message: AgentMessage) {
  if (index < 0) {
    return [...messages, message];
  }
  return [...messages.slice(0, index), message, ...messages.slice(index + 1)];
}

function appendCard(
  messages: AgentMessage[],
  cardType: Extract<AgentMessage, { role: "card" }>["cardType"],
  title: string,
  payload: Record<string, unknown>,
  event: RunEvent
) {
  return [
    ...messages,
    {
      id: `${cardType}-${event.eventId}`,
      role: "card",
      cardType,
      title,
      payload,
      createdAt: event.occurredAt
    }
  ] satisfies AgentMessage[];
}

// 同一次工具调用会先后收到 started/finished；按调用标识原地更新，避免时间线出现重复卡片。
function upsertToolCard(messages: AgentMessage[], title: string, event: RunEvent) {
  const key = toolCardKey(event.payload) ?? event.eventId;
  const index = messages.findIndex((message) => message.role === "card" && message.cardType === "tool" && toolCardKey(message.payload) === key);
  const nextCard = {
    id: index >= 0 ? messages[index].id : `tool-${event.eventId}`,
    role: "card",
    cardType: "tool",
    title,
    payload: {
      ...(index >= 0 && messages[index].role === "card" ? messages[index].payload : {}),
      ...event.payload
    },
    createdAt: index >= 0 ? messages[index].createdAt : event.occurredAt
  } satisfies Extract<AgentMessage, { role: "card" }>;
  if (index < 0) {
    return [...messages, nextCard] satisfies AgentMessage[];
  }
  return [...messages.slice(0, index), nextCard, ...messages.slice(index + 1)];
}

function toolCardKey(payload: Record<string, unknown>) {
  return (
    text(payload.callId) ??
    text(payload.callID) ??
    text(payload.partId) ??
    text(payload.partID) ??
    text(payload.rawEventId)
  );
}

function toMessagePart(raw: Record<string, unknown>, partId: string): MessagePart {
  const partType = text(raw.type) ?? text(raw.partType) ?? "text";
  if (partType === "tool") {
    const state = record(raw.state);
    const stateTime = record(state?.time);
    return {
      partId,
      type: "tool",
      toolName: text(raw.toolName) ?? text(raw.tool) ?? text(raw.name) ?? "tool",
      callId: text(raw.callId) ?? text(raw.callID),
      status: text(raw.status) ?? text(state?.status) ?? "completed",
      input: record(raw.input) ?? record(state?.input),
      output: raw.output ?? state?.output,
      metadata: record(raw.metadata) ?? record(state?.metadata),
      startedAt: text(raw.startedAt) ?? text(stateTime?.start),
      endedAt: text(raw.endedAt) ?? text(stateTime?.end)
    };
  }
  if (partType === "reasoning") {
    return { partId, type: "reasoning", text: text(raw.text) ?? text(raw.content) ?? "", status: text(raw.status) };
  }
  if (partType === "file") {
    return {
      partId,
      type: "file",
      path: text(raw.path),
      name: text(raw.name),
      mimeType: text(raw.mimeType),
      url: text(raw.url)
    };
  }
  if (partType === "subtask") {
    return {
      partId,
      type: "subtask",
      prompt: text(raw.prompt) ?? "",
      description: text(raw.description) ?? "",
      agent: text(raw.agent) ?? text(raw.name) ?? "",
      model: text(raw.model),
      command: text(raw.command),
      status: text(raw.status)
    };
  }
  if (partType === "step-start") {
    return { partId, type: "step-start", snapshot: text(raw.snapshot) };
  }
  if (partType === "step-finish") {
    const tokens = record(raw.tokens);
    return {
      partId,
      type: "step-finish",
      reason: text(raw.reason) ?? "",
      snapshot: text(raw.snapshot),
      cost: number(raw.cost),
      tokens: tokens
        ? {
            total: number(tokens.total),
            input: number(tokens.input),
            output: number(tokens.output),
            reasoning: number(tokens.reasoning)
          }
        : undefined
    };
  }
  if (partType === "snapshot") {
    return { partId, type: "snapshot", snapshot: text(raw.snapshot) ?? "" };
  }
  if (partType === "patch") {
    return {
      partId,
      type: "patch",
      hash: text(raw.hash) ?? "",
      files: Array.isArray(raw.files) ? raw.files.filter((item): item is string => typeof item === "string") : []
    };
  }
  if (partType === "agent") {
    const source = record(raw.source);
    return {
      partId,
      type: "agent",
      name: text(raw.name) ?? "",
      source: source
        ? { value: text(source.value) ?? "", start: number(source.start), end: number(source.end) }
        : undefined
    };
  }
  if (partType === "retry") {
    const error = record(raw.error);
    const time = record(raw.time);
    return {
      partId,
      type: "retry",
      attempt: number(raw.attempt) ?? 0,
      error: { name: text(error?.name), message: text(error?.message) ?? text(error?.value) },
      time: time ? { created: number(time.created) } : undefined
    };
  }
  if (partType === "compaction") {
    return {
      partId,
      type: "compaction",
      auto: typeof raw.auto === "boolean" ? raw.auto : false,
      overflow: typeof raw.overflow === "boolean" ? raw.overflow : undefined,
      tailStartId: text(raw.tailStartId) ?? text(raw.tail_start_id)
    };
  }
  return { partId, type: "text", text: text(raw.text) ?? text(raw.content) ?? "", status: text(raw.status) };
}

function toPermissionRequest(payload: Record<string, unknown>, event: RunEvent): PermissionRequest {
  const requestId = text(payload.requestId) ?? text(payload.requestID) ?? text(payload.id) ?? `permission-${event.seq}`;
  return {
    requestId,
    sessionId: text(payload.sessionId) ?? text(payload.sessionID) ?? "",
    type: text(payload.type) ?? text(payload.permission) ?? text(payload.action) ?? "permission",
    title: text(payload.title),
    description: text(payload.description) ?? text(payload.pattern),
    pattern: text(payload.pattern),
    createdAt: text(payload.createdAt) ?? event.occurredAt
  };
}

function toQuestionRequest(payload: Record<string, unknown>, event: RunEvent): QuestionRequest {
  const requestId = text(payload.requestId) ?? text(payload.requestID) ?? text(payload.id) ?? `question-${event.seq}`;
  const rawQuestions = Array.isArray(payload.questions) ? payload.questions : [payload];
  return {
    requestId,
    sessionId: text(payload.sessionId) ?? text(payload.sessionID) ?? "",
    questions: rawQuestions
      .filter((item): item is Record<string, unknown> => typeof item === "object" && item !== null)
      .map((item, index) => ({
        questionId: text(item.questionId) ?? text(item.questionID) ?? text(item.id) ?? `${requestId}:${index}`,
        text: text(item.text) ?? text(item.prompt) ?? text(item.question) ?? "",
        kind: text(item.kind) ?? text(item.type) ?? "text",
        options: Array.isArray(item.options)
          ? item.options
              .filter((option): option is Record<string, unknown> => typeof option === "object" && option !== null)
              .map((option) => ({
                id: text(option.id) ?? text(option.value) ?? text(option.label) ?? "option",
                label: text(option.label) ?? text(option.value) ?? text(option.id) ?? "option",
                description: text(option.description)
              }))
          : undefined,
        required: typeof item.required === "boolean" ? item.required : undefined
      })),
    createdAt: text(payload.createdAt) ?? event.occurredAt
  };
}

function toTodoItem(value: Record<string, unknown>): TodoItem {
  const id = text(value.id) ?? "todo";
  return {
    id,
    text: text(value.text) ?? text(value.content) ?? text(value.title) ?? id,
    status: text(value.status) ?? "pending",
    priority: text(value.priority),
    title: text(value.title),
    description: text(value.description),
    summary: text(value.summary),
    result: text(value.result),
    error: text(value.error),
    steps: Array.isArray(value.steps) ? value.steps.filter((item): item is string => typeof item === "string") : undefined,
    updatedAt: text(value.updatedAt)
  };
}

function runStatusFromEvent(event: RunEvent) {
  const explicit = text(event.payload.status);
  if (explicit) {
    return explicit;
  }
  return {
    "run.created": "PENDING",
    "run.started": "RUNNING",
    "run.cancelling": "CANCELLING",
    "run.succeeded": "SUCCEEDED",
    "run.failed": "FAILED",
    "run.cancelled": "CANCELLED"
  }[event.type] ?? event.type;
}

function diffFilesFromPayload(payload: Record<string, unknown>): RunDiffFile[] {
  const raw = Array.isArray(payload.diff) ? payload.diff : Array.isArray(payload.files) ? payload.files : [];
  return raw
    .filter((item): item is Record<string, unknown> => typeof item === "object" && item !== null)
    .map((item) => ({
      path: text(item.path) ?? text(item.file) ?? "",
      patch: text(item.patch) ?? "",
      additions: number(item.additions) ?? 0,
      deletions: number(item.deletions) ?? 0,
      status: text(item.status) ?? "modified"
    }))
    .filter((item) => item.path.length > 0);
}

function upsertById<T extends { requestId: string }>(items: T[], item: T) {
  const index = items.findIndex((current) => current.requestId === item.requestId);
  if (index < 0) {
    return [...items, item];
  }
  return [...items.slice(0, index), item, ...items.slice(index + 1)];
}

function record(value: unknown) {
  return typeof value === "object" && value !== null && !Array.isArray(value) ? (value as Record<string, unknown>) : undefined;
}

function text(value: unknown) {
  return typeof value === "string" && value.length > 0 ? value : undefined;
}

function number(value: unknown) {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}
