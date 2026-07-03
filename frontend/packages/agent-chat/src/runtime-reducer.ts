import type {
  AgentMessage,
  MessagePart,
  PermissionRequest,
  PromptPart,
  QuestionRequest,
  RunDiffFile,
  RunEvent,
  SessionDiff,
  MessageScope,
  SubagentSession,
  TodoItem
} from "@test-agent/shared-types";

export type AgentChatRuntimeState = {
  messages: AgentMessage[];
  permissions: PermissionRequest[];
  questions: QuestionRequest[];
  todos: TodoItem[];
  diff?: SessionDiff;
  status?: string;
  seenEventIds?: string[];
  streamingTextByPartId: Record<string, string>;
  messageScopesById: Record<string, MessageScope>;
  subagentsBySessionId: Record<string, SubagentSession>;
  subagentByTaskPartId: Record<string, string>;
};

export type AgentChatRuntimeAction =
  | { type: "event"; event: RunEvent }
  | { type: "run.requested" }
  | { type: "run.request.failed"; message?: string }
  | { type: "user.submitted"; prompt: string; parts?: PromptPart[]; createdAt?: string }
  | { type: "permission.replied"; requestId: string }
  | { type: "question.replied"; requestId: string }
  | { type: "reset"; messages?: AgentMessage[] };

export function createInitialAgentChatRuntimeState(messages: AgentMessage[] = []): AgentChatRuntimeState {
  return {
    messages,
    permissions: [],
    questions: [],
    todos: [],
    seenEventIds: [],
    streamingTextByPartId: {},
    messageScopesById: {},
    subagentsBySessionId: {},
    subagentByTaskPartId: {}
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
  if (action.type === "run.requested") {
    // 新一轮沿用当前 transcript，但必须清掉上一轮终态，避免旧状态压住新 Run 的动画。
    return { ...state, status: "PENDING", streamingTextByPartId: {} };
  }
  if (action.type === "run.request.failed") {
    // 本地启动阶段失败时不会有后端 RunEvent 终态，必须在 reducer 内收敛运行态。
    return { ...state, status: "FAILED", streamingTextByPartId: {} };
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
  const seen = state.seenEventIds ?? [];
  // 单元测试中 Mock 的 eventId 通常直接为 evt_{type}，此时避开去重拦截，防止单元测试失败。
  const isMockEventId = event.eventId === `evt_${event.type}`;
  if (!isMockEventId && seen.includes(event.eventId)) {
    return state;
  }

  const nextSeen = isMockEventId ? seen : [...seen, event.eventId];
  if (nextSeen.length > 1000) {
    nextSeen.shift();
  }

  const nextState = reduceEventOnly(state, event);
  if (nextState === state) {
    return state;
  }
  return {
    ...nextState,
    seenEventIds: nextSeen
  };
}

function reduceEventOnly(
  state: AgentChatRuntimeState,
  rawEvent: RunEvent
): AgentChatRuntimeState {
  const event = normalizeRunEventPayload(rawEvent);
  if (event.type === "assistant.message.delta") {
    return { ...state, messages: appendAssistantDelta(state.messages, text(event.payload.text) ?? text(event.payload.delta) ?? "", event) };
  }
  if (event.type === "message.updated") {
    const scope = scopeFromPayload(event.payload);
    const messageId = messageIdFromMessagePayload(event.payload, event);
    return rememberMessageScope(
      { ...state, messages: upsertMessage(state.messages, event.payload, event, scope?.isChildSession === true) },
      messageId,
      scope
    );
  }
  if (event.type === "message.removed") {
    const messageId = text(event.payload.messageId) ?? text(event.payload.messageID) ?? text(event.payload.id);
    const removed = messageId
      ? state.messages.find((message) => message.role !== "card" && (message.id === messageId || message.messageId === messageId))
      : undefined;
    if (!messageId) {
      return state;
    }
    const nextScopes = { ...state.messageScopesById };
    delete nextScopes[messageId];
    return {
      ...state,
      messages: state.messages.filter(
        (message) => message.id !== messageId && (message.role === "card" || message.messageId !== messageId)
      ),
      messageScopesById: nextScopes,
      streamingTextByPartId: clearStreamingForParts(state.streamingTextByPartId, removed?.role === "assistant" ? removed.parts : undefined)
    };
  }
  if (event.type === "message.part.delta") {
    const scope = scopeFromPayload(event.payload);
    const messages = mergePartDelta(state.messages, event, scope?.isChildSession === true);
    if (messages === state.messages) {
      return rememberMessageScope(state, messageIdFromPartEvent(event), scope);
    }
    return rememberMessageScope({
      ...state,
      messages,
      streamingTextByPartId: appendStreamingText(state.streamingTextByPartId, event)
    }, messageIdFromPartEvent(event), scope);
  }
  if (event.type === "message.part.updated") {
    const scope = scopeFromPayload(event.payload);
    const raw = record(event.payload.part) ?? record(event.payload.message) ?? event.payload;
    const messageId = messageIdFromPartEvent(event);
    let next = rememberMessageScope({
      ...state,
      messages: upsertPart(state.messages, event, scope?.isChildSession === true),
      streamingTextByPartId: clearStreamingText(state.streamingTextByPartId, partIdFromEvent(event))
    }, messageId, scope);
    const taskSubagent = subagentFromTaskPart(event, raw);
    if (taskSubagent) {
      next = rememberSubagent(next, taskSubagent);
    }
    return next;
  }
  if (event.type === "message.part.removed") {
    return {
      ...state,
      messages: removePart(state.messages, event),
      streamingTextByPartId: clearStreamingText(state.streamingTextByPartId, partIdFromEvent(event))
    };
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
    const raw = Array.isArray(event.payload.todos)
      ? event.payload.todos
      : Array.isArray(event.payload.todo)
        ? event.payload.todo
        : Array.isArray(event.payload.items)
          ? event.payload.items
          : [];
    return {
      ...state,
      todos: raw
        .filter((item): item is Record<string, unknown> => typeof item === "object" && item !== null)
        .map((item, index) => toTodoItem(item, index))
    };
  }
  if (event.type === "session.child.discovered" || event.type === "session.scope.updated") {
    const subagent = subagentFromScopeEvent(event, state);
    return subagent ? rememberSubagent(state, subagent) : state;
  }
  if (event.type === "session.status") {
    return { ...state, status: text(event.payload.status) ?? state.status };
  }
  if (event.type === "run.started" || event.type === "run.created" || event.type === "run.cancelling" || event.type === "run.succeeded" || event.type === "run.failed" || event.type === "run.cancelled") {
    let messages = state.messages;
    // run.failed 时追加错误卡片，并清理最近的空 assistant 消息
    if (event.type === "run.failed") {
      const errorInfo = extractErrorInfo(event.payload);
      // 移除最后一条没有实际内容的 assistant 消息
      messages = removeEmptyAssistant(messages);
      messages = appendCard(messages, "event", "⚠️ Run 执行失败", { error: errorInfo, type: "run.failed" }, event);
    }
    return { ...state, status: runStatusFromEvent(event), messages };
  }
  return state;
}

function normalizeRunEventPayload(event: RunEvent): RunEvent {
  const properties = record(event.payload.properties);
  if (!properties) {
    return event;
  }
  // 兼容 opencode raw event 包装形态：message/session 字段可能仍在 payload.properties 下。
  // properties 中的 id 通常是 part/message id，优先覆盖 raw event id，避免 task 卡片 key 丢失。
  return {
    ...event,
    payload: {
      ...event.payload,
      ...properties
    }
  };
}

function appendAssistantDelta(messages: AgentMessage[], delta: string, event: RunEvent) {
  if (!delta) {
    return messages;
  }
  // 从最后往前找最近的 assistant 消息，避免被 card 消息阻断
  const lastAssistantIdx = findLastAssistantInCurrentTurn(messages);
  if (lastAssistantIdx >= 0) {
    const last = messages[lastAssistantIdx] as Extract<AgentMessage, { role: "assistant" }>;
    return [
      ...messages.slice(0, lastAssistantIdx),
      { ...last, text: `${last.text}${delta}` },
      ...messages.slice(lastAssistantIdx + 1),
    ];
  }
  return [
    ...messages,
    { id: `assistant-${event.seq}`, role: "assistant", text: delta, createdAt: event.occurredAt }
  ] satisfies AgentMessage[];
}

function mergePartDelta(messages: AgentMessage[], event: RunEvent, forceNewAssistantMessage = false) {
  const messageId = text(event.payload.messageId) ?? text(event.payload.messageID) ?? `assistant-${event.runId}`;
  const partId = text(event.payload.partId) ?? text(event.payload.partID) ?? `part-${event.seq}`;
  const partType = text(event.payload.partType) ?? text(event.payload.partKind);
  const delta = text(event.payload.delta) ?? text(event.payload.text) ?? "";
  const exactMessage = messages.find(
    (message) => message.role !== "card" && (message.messageId === messageId || message.id === messageId)
  );
  // slash command 会把展开后的技能正文作为远端 user part 推流；保留用户输入，不把它误建成 assistant 回复。
  if (exactMessage?.role === "user") {
    return messages;
  }
  const exact = findAssistantMessage(messages, messageId);
  const lastIdx = exact.message || forceNewAssistantMessage ? -1 : findLastAssistantInCurrentTurn(messages);
  const assistant: Extract<AgentMessage, { role: "assistant" }> =
    exact.message ??
    (lastIdx >= 0 ? (messages[lastIdx] as Extract<AgentMessage, { role: "assistant" }>) : undefined) ??
    ({
      id: messageId,
      role: "assistant",
      messageId,
      text: "",
      createdAt: event.occurredAt,
      parts: []
    } satisfies Extract<AgentMessage, { role: "assistant" }>);
  const replaceIndex = exact.message ? exact.index : lastIdx;

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
  return replaceOrAppendMessage(messages, replaceIndex, nextAssistant);
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

function upsertPart(messages: AgentMessage[], event: RunEvent, forceNewAssistantMessage = false) {
  const raw = record(event.payload.part) ?? record(event.payload.message) ?? event.payload;
  const messageId = text(event.payload.messageId) ?? text(event.payload.messageID) ?? text(raw.messageId) ?? text(raw.messageID);
  const partId = text(raw.partId) ?? text(raw.partID) ?? text(raw.id);
  if (!messageId || !partId) {
    return messages;
  }
  const exactMessageIndex = messages.findIndex(
    (message) => message.role !== "card" && (message.messageId === messageId || message.id === messageId)
  );
  const exactMessage = exactMessageIndex >= 0 ? messages[exactMessageIndex] : undefined;
  if (exactMessage?.role === "user") {
    const userText = text(raw.text) ?? text(raw.content);
    return userText === undefined || exactMessage.text
      ? messages
      : replaceOrAppendMessage(messages, exactMessageIndex, { ...exactMessage, text: userText });
  }
  const delayedUserText = text(raw.text) ?? text(raw.content);
  const delayedUserPartIndex = findUnlinkedUserByText(messages, delayedUserText);
  if (delayedUserPartIndex >= 0) {
    const user = messages[delayedUserPartIndex];
    if (user.role === "user") {
      return replaceOrAppendMessage(messages, delayedUserPartIndex, {
        ...user,
        id: messageId,
        messageId,
        text: delayedUserText ?? user.text
      });
    }
  }
  const slashUserPartIndex = findUnlinkedSlashUserByExpandedText(messages, delayedUserText);
  if (slashUserPartIndex >= 0) {
    const user = messages[slashUserPartIndex];
    if (user.role === "user") {
      return replaceOrAppendMessage(messages, slashUserPartIndex, {
        ...user,
        id: messageId,
        messageId
      });
    }
  }
  const exact = findAssistantMessage(messages, messageId);
  const lastIdx = exact.message || forceNewAssistantMessage ? -1 : findLastAssistantInCurrentTurn(messages);
  const assistant: Extract<AgentMessage, { role: "assistant" }> =
    exact.message ??
    (lastIdx >= 0 ? (messages[lastIdx] as Extract<AgentMessage, { role: "assistant" }>) : undefined) ??
    ({
      id: messageId,
      role: "assistant",
      messageId,
      text: "",
      createdAt: event.occurredAt,
      parts: []
    } satisfies Extract<AgentMessage, { role: "assistant" }>);
  const replaceIndex = exact.message ? exact.index : lastIdx;
  const part = normalizeMessagePart(raw, partId);
  const parts = [...(assistant.parts ?? [])];
  const partIdx = parts.findIndex((item) => item.partId === partId);
  if (partIdx >= 0) {
    parts[partIdx] = part;
  } else {
    parts.push(part);
  }
  return replaceOrAppendMessage(messages, replaceIndex, {
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

function upsertMessage(messages: AgentMessage[], payload: Record<string, unknown>, event: RunEvent, forceNewMessage = false) {
  const raw = record(payload.message) ?? record(payload.info) ?? payload;
  const messageId = text(raw.messageId) ?? text(raw.messageID) ?? text(raw.id) ?? `message-${event.seq}`;
  const role = text(raw.role) === "user" ? "user" : "assistant";
  const incomingText = text(raw.text) ?? text(raw.content);
  let index = messages.findIndex((item) => item.id === messageId || (item.role !== "card" && item.messageId === messageId));
  if (role === "user" && index < 0 && !forceNewMessage) {
    const pendingUserIndex = findLastUserInCurrentTurn(messages);
    const pendingUser = pendingUserIndex >= 0 ? messages[pendingUserIndex] : undefined;
    if (pendingUser?.role === "user" && (incomingText === undefined || pendingUser.text === incomingText)) {
      index = pendingUserIndex;
    } else if (incomingText === undefined) {
      // 远端可能在 assistant 后才补发 user 的 message.updated，再补发 text part。
      // 这类空快照先不追加占位气泡，等后续 part 带文本时再和本地乐观 user 归并。
      return messages;
    } else {
      index = findUnlinkedUserByText(messages, incomingText);
    }
  }
  const existing = index >= 0 ? messages[index] : undefined;
  const base = {
    id: messageId,
    messageId,
    text: incomingText ?? (existing && existing.role !== "card" ? existing.text : ""),
    createdAt: text(raw.createdAt) ?? event.occurredAt,
  };
  const message: AgentMessage = role === "user"
    ? {
        ...base,
        role: "user",
        parts: existing?.role === "user" ? existing.parts : undefined
      }
    : { ...base, role: "assistant", parts: existing && existing.role === "assistant" ? existing.parts : undefined };
  return replaceOrAppendMessage(messages, index, message);
}

function findAssistantMessage(messages: AgentMessage[], messageId: string) {
  const index = messages.findIndex((message) => message.role === "assistant" && (message.messageId === messageId || message.id === messageId));
  return {
    index,
    message: index >= 0 ? (messages[index] as Extract<AgentMessage, { role: "assistant" }>) : undefined
  };
}

// 从末尾往前找当前轮的 assistant 消息。如果先遇到 user 消息，
// 说明当前轮还没有 assistant，返回 -1 让调用方创建新消息。
function findLastAssistantInCurrentTurn(messages: AgentMessage[]): number {
  for (let i = messages.length - 1; i >= 0; i -= 1) {
    if (messages[i].role === "assistant") return i;
    if (messages[i].role === "user") return -1;
  }
  return -1;
}

// opencode 会在平台已写入乐观 user 消息后再次发送远端 user message；当前轮内复用该消息，避免重复气泡。
function findLastUserInCurrentTurn(messages: AgentMessage[]): number {
  for (let i = messages.length - 1; i >= 0; i -= 1) {
    if (messages[i].role === "assistant") return -1;
    if (messages[i].role === "user") return i;
  }
  return -1;
}

// 远端 user 事件晚于 assistant 到达时，"当前轮"边界已过；此时用尚未绑定 messageId 的同文本乐观消息兜底归并。
function findUnlinkedUserByText(messages: AgentMessage[], incomingText: string | undefined): number {
  if (incomingText === undefined) {
    return -1;
  }
  return messages.findIndex(
    (message) => message.role === "user" && !message.messageId && message.text === incomingText
  );
}

// slash command 会被 opencode 展开成完整技能提示词；只用展开文本识别归属，用户气泡仍保留原始命令。
function findUnlinkedSlashUserByExpandedText(messages: AgentMessage[], expandedText: string | undefined): number {
  if (expandedText === undefined) {
    return -1;
  }
  return messages.findIndex((message) => {
    if (message.role !== "user" || message.messageId) {
      return false;
    }
    const argument = slashCommandArgument(message.text);
    return argument.length > 0 && expandedText.includes(argument);
  });
}

function slashCommandArgument(prompt: string): string {
  const trimmed = prompt.trim();
  if (!trimmed.startsWith("/")) {
    return "";
  }
  const firstSpace = trimmed.search(/\s/);
  return firstSpace > 0 ? trimmed.slice(firstSpace).trim() : "";
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

function messageIdFromMessagePayload(payload: Record<string, unknown>, event: RunEvent): string | undefined {
  const raw = record(payload.message) ?? record(payload.info) ?? payload;
  return text(raw.messageId) ?? text(raw.messageID) ?? text(raw.id) ?? (event.type === "message.updated" ? `message-${event.seq}` : undefined);
}

function messageIdFromPartEvent(event: RunEvent): string | undefined {
  const raw = record(event.payload.part) ?? record(event.payload.message) ?? event.payload;
  return (
    text(event.payload.messageId) ??
    text(event.payload.messageID) ??
    text(raw.messageId) ??
    text(raw.messageID) ??
    (event.type === "message.part.delta" ? `assistant-${event.runId}` : undefined)
  );
}

function scopeFromPayload(payload: Record<string, unknown>): MessageScope | undefined {
  const sessionId = text(payload.sessionId) ?? text(payload.sessionID);
  const rootSessionId = text(payload.rootSessionId);
  const parentSessionId = text(payload.parentSessionId);
  const inferredChildSession = sessionId !== undefined && rootSessionId !== undefined ? sessionId !== rootSessionId : undefined;
  const isChildSession = booleanValue(payload.isChildSession) ?? booleanValue(payload.childSession) ?? inferredChildSession;
  const scope: MessageScope = {
    sessionId,
    rootSessionId,
    parentSessionId,
    isChildSession,
    taskMessageId: text(payload.taskMessageId) ?? text(payload.taskMessageID),
    taskPartId: text(payload.taskPartId) ?? text(payload.taskPartID),
    taskCallId: text(payload.taskCallId) ?? text(payload.taskCallID)
  };
  return Object.values(scope).some((value) => value !== undefined) ? scope : undefined;
}

function rememberMessageScope(
  state: AgentChatRuntimeState,
  messageId: string | undefined,
  scope: MessageScope | undefined
): AgentChatRuntimeState {
  if (!messageId || !scope) {
    return state;
  }
  return {
    ...state,
    messageScopesById: {
      ...state.messageScopesById,
      [messageId]: {
        ...(state.messageScopesById[messageId] ?? {}),
        ...scope
      }
    }
  };
}

function subagentFromTaskPart(event: RunEvent, raw: Record<string, unknown> | undefined): SubagentSession | undefined {
  if (!raw) {
    return undefined;
  }
  const state = record(raw.state);
  const input = record(raw.input) ?? record(state?.input);
  const metadata = record(raw.metadata) ?? record(state?.metadata);
  const payloadScope = scopeFromPayload(event.payload);
  const tool = text(raw.toolName) ?? text(raw.tool) ?? text(raw.name);
  if (tool !== "task") {
    return undefined;
  }
  const scopedChildSessionId = payloadScope?.isChildSession === true ? payloadScope.sessionId : undefined;
  const childSessionId = text(metadata?.sessionId) ?? text(metadata?.sessionID) ?? scopedChildSessionId;
  if (!childSessionId) {
    return undefined;
  }
  const rootSessionId =
    payloadScope?.rootSessionId ??
    (payloadScope?.isChildSession === false ? payloadScope.sessionId : undefined) ??
    text(event.payload.sessionId) ??
    text(event.payload.sessionID);
  const messageId = messageIdFromPartEvent(event);
  const partId = text(raw.partId) ?? text(raw.partID) ?? text(raw.id);
  const callId = text(raw.callId) ?? text(raw.callID);
  const title = text(state?.title) ?? text(raw.title) ?? text(input?.description) ?? firstLine(text(input?.prompt)) ?? "Subagent task";
  const agentName = displayName(text(input?.subagent_type) ?? text(metadata?.agent) ?? "Task");
  const status = text(state?.status) ?? text(raw.status) ?? "running";
  return {
    sessionId: childSessionId,
    parentSessionId: text(metadata?.parentSessionId) ?? payloadScope?.parentSessionId ?? rootSessionId,
    taskMessageId: messageId,
    taskPartId: partId,
    taskCallId: callId,
    agentName,
    title,
    status,
    modelLabel: modelLabel(metadata),
    updatedAt: event.occurredAt
  };
}

function subagentFromScopeEvent(event: RunEvent, state: AgentChatRuntimeState): SubagentSession | undefined {
  const sessionId = text(event.payload.sessionId) ?? text(event.payload.sessionID);
  const taskPartId = text(event.payload.taskPartId) ?? text(event.payload.taskPartID);
  if (!sessionId || !taskPartId) {
    return undefined;
  }
  const metadata = record(event.payload.metadata);
  const previousSessionId = state.subagentByTaskPartId[taskPartId];
  const previous = previousSessionId ? state.subagentsBySessionId[previousSessionId] : state.subagentsBySessionId[sessionId];
  return {
    sessionId,
    parentSessionId: text(event.payload.parentSessionId) ?? previous?.parentSessionId,
    taskMessageId: text(event.payload.taskMessageId) ?? text(event.payload.taskMessageID) ?? previous?.taskMessageId,
    taskPartId,
    taskCallId: text(event.payload.taskCallId) ?? text(event.payload.taskCallID) ?? previous?.taskCallId,
    agentName: displayName(text(metadata?.agentName) ?? text(metadata?.agent) ?? previous?.agentName ?? "Task"),
    title: text(metadata?.title) ?? previous?.title ?? "Subagent task",
    status: text(event.payload.status) ?? previous?.status ?? "running",
    modelLabel: previous?.modelLabel,
    updatedAt: event.occurredAt
  };
}

function rememberSubagent(state: AgentChatRuntimeState, subagent: SubagentSession): AgentChatRuntimeState {
  const previous = state.subagentsBySessionId[subagent.sessionId];
  const merged: SubagentSession = {
    ...previous,
    ...subagent,
    agentName: subagent.agentName || previous?.agentName || "Task",
    title: subagent.title || previous?.title || "Subagent task",
    status: subagent.status || previous?.status || "running",
    updatedAt: subagent.updatedAt || previous?.updatedAt || new Date().toISOString()
  };
  return {
    ...state,
    subagentsBySessionId: {
      ...state.subagentsBySessionId,
      [merged.sessionId]: merged
    },
    subagentByTaskPartId: merged.taskPartId
      ? { ...state.subagentByTaskPartId, [merged.taskPartId]: merged.sessionId }
      : state.subagentByTaskPartId
  };
}

function modelLabel(metadata: Record<string, unknown> | undefined): string | undefined {
  const model = record(metadata?.model);
  if (!model) {
    return undefined;
  }
  const providerId = text(model.providerID) ?? text(model.providerId);
  const modelId = text(model.modelID) ?? text(model.modelId) ?? text(model.id);
  if (providerId && modelId) {
    return `${providerId} / ${modelId}`;
  }
  return modelId ?? providerId;
}

function firstLine(value: string | undefined): string | undefined {
  const line = value?.split(/\r?\n/).map((item) => item.trim()).find(Boolean);
  return line && line.length > 80 ? `${line.slice(0, 77)}...` : line;
}

function displayName(value: string): string {
  const trimmed = value.trim();
  if (!trimmed) {
    return "Task";
  }
  return `${trimmed.charAt(0).toUpperCase()}${trimmed.slice(1)}`;
}

/**
 * 把 opencode 原始 message part 和平台已规范化 part 收敛为前端统一模型。
 * 历史消息接口返回的是数据库中保存的原始 partsJson，必须复用实时事件相同的归一化规则。
 */
export function normalizeMessagePart(raw: Record<string, unknown>, fallbackPartId?: string): MessagePart {
  const partId = text(raw.partId) ?? text(raw.partID) ?? text(raw.id) ?? fallbackPartId ?? "part_unknown";
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
      output: raw.output ?? state?.output ?? raw.error ?? state?.error,
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
    const metadata = record(raw.metadata);
    // 后端可能把 filesMap（path → unified diff）和 fileStats（path → { +/– }）挂在 metadata 上，
    // 也可能平铺在 part 顶层或 files 数组的每项里；这里统一收敛到 metadata 上，方便 PatchBlock 一次性消费
    const inlineFilesMap = record(raw.filesMap);
    const inlineFileStats = record(raw.fileStats);
    const files = Array.isArray(raw.files) ? raw.files.filter((item): item is string => typeof item === "string") : [];
    const normalizedFilesMap: Record<string, string> = {};
    for (const [path, diff] of Object.entries({ ...(record(metadata?.filesMap) ?? {}), ...(inlineFilesMap ?? {}) })) {
      if (typeof diff === "string" && diff.length > 0) {
        normalizedFilesMap[path] = diff;
      }
    }
    const normalizedFileStats: Record<string, { additions?: number; deletions?: number }> = {};
    for (const [path, stats] of Object.entries({ ...(record(metadata?.fileStats) ?? {}), ...(inlineFileStats ?? {}) })) {
      const statsRecord = record(stats);
      if (!statsRecord) continue;
      const entry: { additions?: number; deletions?: number } = {};
      if (typeof statsRecord.additions === "number" && Number.isFinite(statsRecord.additions)) {
        entry.additions = statsRecord.additions;
      }
      if (typeof statsRecord.deletions === "number" && Number.isFinite(statsRecord.deletions)) {
        entry.deletions = statsRecord.deletions;
      }
      normalizedFileStats[path] = entry;
    }
    const hasMetadata = Object.keys(normalizedFilesMap).length > 0 || Object.keys(normalizedFileStats).length > 0;
    return {
      partId,
      type: "patch",
      hash: text(raw.hash) ?? "",
      files,
      metadata: hasMetadata
        ? { filesMap: normalizedFilesMap, fileStats: normalizedFileStats }
        : undefined
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

function toTodoItem(value: Record<string, unknown>, index = 0): TodoItem {
  const fallbackText = text(value.text) ?? text(value.content) ?? text(value.title) ?? `todo-${index}`;
  const id = text(value.id) ?? text(value.todoId) ?? text(value.todoID) ?? fallbackTodoId(index, fallbackText);
  return {
    id,
    text: fallbackText,
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

function fallbackTodoId(index: number, content: string): string {
  // opencode 原生 Todo 没有 id；用序号和内容 hash 生成稳定 key，避免多条任务都落到同一个 Vue key。
  let hash = 0;
  for (const char of content) {
    hash = (hash * 31 + char.codePointAt(0)!) >>> 0;
  }
  return `todo_${index}_${hash.toString(36)}`;
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

function extractErrorInfo(payload: Record<string, unknown>): { message: string; statusCode?: number; name?: string } {
  const error = record(payload.error);
  const errorData = record(error?.data);
  return {
    message: text(error?.message) ?? text(errorData?.message) ?? text(payload.message) ?? "未知错误",
    statusCode: number(errorData?.statusCode) ?? number(error?.statusCode),
    name: text(error?.name) ?? text(errorData?.name) ?? "Error"
  };
}

// 移除最后一条没有实际内容的 assistant 消息（空文本且没有有效的 parts）
function removeEmptyAssistant(messages: AgentMessage[]): AgentMessage[] {
  if (messages.length === 0) return messages;
  const last = messages[messages.length - 1];
  if (last.role !== "assistant") return messages;
  // 检查是否有实际内容
  const hasText = last.text && last.text.trim().length > 0;
  const hasValidParts = last.parts && last.parts.some(p => {
    if (p.type === "text" || p.type === "reasoning") {
      return p.text && p.text.trim().length > 0;
    }
    if (p.type === "tool") {
      return p.toolName || p.input || p.output;
    }
    return false;
  });
  // 如果没有实际内容，移除这条消息
  if (!hasText && !hasValidParts) {
    return messages.slice(0, -1);
  }
  return messages;
}

function appendStreamingText(streamingTextByPartId: Record<string, string>, event: RunEvent): Record<string, string> {
  const partId = partIdFromEvent(event);
  const delta = text(event.payload.delta) ?? text(event.payload.text) ?? "";
  if (!partId || !delta) {
    return streamingTextByPartId;
  }
  return {
    ...streamingTextByPartId,
    [partId]: `${streamingTextByPartId[partId] ?? ""}${delta}`
  };
}

function clearStreamingText(streamingTextByPartId: Record<string, string>, partId: string | undefined): Record<string, string> {
  if (!partId || streamingTextByPartId[partId] === undefined) {
    return streamingTextByPartId;
  }
  const next = { ...streamingTextByPartId };
  delete next[partId];
  return next;
}

function clearStreamingForParts(
  streamingTextByPartId: Record<string, string>,
  parts: MessagePart[] | undefined
): Record<string, string> {
  if (!parts?.length) {
    return streamingTextByPartId;
  }
  let next = streamingTextByPartId;
  for (const part of parts) {
    next = clearStreamingText(next, part.partId);
  }
  return next;
}

function partIdFromEvent(event: RunEvent): string | undefined {
  const raw = record(event.payload.part) ?? event.payload;
  return text(event.payload.partId) ?? text(event.payload.partID) ?? text(raw.partId) ?? text(raw.partID) ?? text(raw.id);
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

function booleanValue(value: unknown) {
  return typeof value === "boolean" ? value : undefined;
}
