import type { RunEvent, RunEventType, SessionRuntimeStateSummary } from "@test-agent/shared-types";

export type RunEventStreamStatus = "connecting" | "open" | "closed" | "error";
export type SessionRuntimeStateStreamStatus = "connecting" | "open" | "closed" | "error";

export type RunEventSubscription = {
  close: () => void;
};

export type SessionRuntimeStateSubscription = {
  close: () => void;
};

export type RunEventSubscribeOptions = {
  runId: string;
  baseUrl?: string;
  agentId?: string;
  lastEventId?: string;
  onEvent: (event: RunEvent) => void;
  onRawMessage?: (message: RunEventRawMessage) => void;
  onStatus?: (status: RunEventStreamStatus) => void;
  onError?: (error: Event) => void;
  eventSourceFactory?: EventSourceFactory;
};

export type RunEventRawMessage = {
  runId: string;
  eventName: string;
  lastEventId?: string;
  data: string;
  receivedAt: string;
};

export type EventSourceLike = {
  onopen: ((event: Event) => void) | null;
  onerror: ((event: Event) => void) | null;
  addEventListener: (type: string, listener: EventListener) => void;
  removeEventListener: (type: string, listener: EventListener) => void;
  close: () => void;
};

export type EventSourceFactory = (url: string) => EventSourceLike;

export type SessionRuntimeStateSubscribeOptions = {
  baseUrl?: string;
  token?: string | null;
  fetcher?: typeof fetch;
  onEvent: (event: SessionRuntimeStateSummary, meta: { eventName: string }) => void;
  onStatus?: (status: SessionRuntimeStateStreamStatus) => void;
  onError?: (error: unknown) => void;
};

const SESSION_RUNTIME_RECONNECT_DELAYS_MS = [1_000, 2_000, 5_000, 10_000, 30_000] as const;

export const KNOWN_RUN_EVENT_TYPES: RunEventType[] = [
  "run.created",
  "run.started",
  "run.cancelling",
  "run.succeeded",
  "run.failed",
  "run.cancelled",
  "assistant.message.delta",
  "message.updated",
  "message.removed",
  "message.part.updated",
  "message.part.removed",
  "message.part.delta",
  "session.updated",
  "session.diff",
  "session.status",
  "todo.updated",
  "tool.started",
  "tool.finished",
  "diff.proposed",
  "diff.accepted",
  "diff.rejected",
  "test.finished",
  "permission.asked",
  "permission.replied",
  "question.asked",
  "question.replied",
  "question.rejected",
  "vcs.branch.updated",
  "lsp.updated",
  "mcp.tools.changed",
  "opencode.event.unknown"
];

export function subscribeRunEvents(options: RunEventSubscribeOptions): RunEventSubscription {
  const baseUrl = (options.baseUrl ?? "http://127.0.0.1:8080").replace(/\/$/, "");
  const agentId = normalizeAgentId(options.agentId ?? "opencode");
  const factory = options.eventSourceFactory ?? ((url: string) => new EventSource(url));
  const url = runEventsUrl(baseUrl, agentId, options.runId, options.lastEventId);
  const seen = new Set<string>();
  let closed = false;
  const source = factory(url);

  options.onStatus?.("connecting");

  const handleEvent: EventListener = (event) => {
    if (closed) {
      return;
    }
    const message = event as MessageEvent<string>;
    options.onRawMessage?.({
      runId: options.runId,
      eventName: event.type,
      lastEventId: message.lastEventId || undefined,
      data: message.data,
      receivedAt: new Date().toISOString()
    });
    const parsed = parseRunEvent(message.data);
    if (!parsed) {
      return;
    }
    if (parsed.runId !== options.runId) {
      return;
    }
    // 如果 eventId 是 fallback 的 runId:seq，说明后端未提供显式 eventId
    const isFallback = parsed.eventId === `${parsed.runId}:${parsed.seq}`;
    // 所有带真实 eventId 的事件（包含 transient delta）都按 eventId 去重；
    // 后端为每个增量生成独立 evt_live_ ID，同一 ID 重投必须丢弃，避免正文重复和空行。
    if (!isFallback) {
      const key = `event:${parsed.eventId}`;
      if (seen.has(key)) {
        return;
      }
      seen.add(key);
    } else if (parsed.seq > 0) {
      const key = `seq:${parsed.runId}:${parsed.seq}`;
      if (seen.has(key)) {
        return;
      }
      seen.add(key);
    }
    options.onEvent(parsed);
  };

  KNOWN_RUN_EVENT_TYPES.forEach((type) => source.addEventListener(type, handleEvent));
  source.addEventListener("message", handleEvent);
  source.onopen = () => options.onStatus?.("open");
  source.onerror = (event) => {
    if (!closed) {
      options.onStatus?.("error");
      options.onError?.(event);
    }
  };

  return {
    close: () => {
      closed = true;
      KNOWN_RUN_EVENT_TYPES.forEach((type) => source.removeEventListener(type, handleEvent));
      source.removeEventListener("message", handleEvent);
      source.close();
      options.onStatus?.("closed");
    }
  };
}

export function subscribeSessionRuntimeState(
  options: SessionRuntimeStateSubscribeOptions
): SessionRuntimeStateSubscription {
  const baseUrl = (options.baseUrl ?? "http://127.0.0.1:8080").replace(/\/$/, "");
  const fetcher = options.fetcher ?? fetch;
  let closed = false;
  let controller: AbortController | null = null;
  let retryTimer: ReturnType<typeof setTimeout> | null = null;
  let resolveRetryWait: (() => void) | null = null;

  void (async () => {
    let failureCount = 0;
    while (!closed) {
      controller = new AbortController();
      let receivedEvent = false;
      options.onStatus?.("connecting");
      try {
        const headers = new Headers();
        headers.set("Accept", "text/event-stream");
        if (options.token?.trim()) {
          headers.set("Authorization", `Bearer ${options.token.trim()}`);
        }
        const response = await fetcher(sessionRuntimeStateEventsUrl(baseUrl), {
          headers,
          signal: controller.signal
        });
        if (!response.ok) {
          throw new Error(`session runtime state stream failed: ${response.status}`);
        }
        if (!response.body) {
          throw new Error("session runtime state stream has no body");
        }
        if (closed) {
          return;
        }
        options.onStatus?.("open");
        await readSseStream(response.body, (message) => {
          if (closed || !isSessionRuntimeStateEvent(message.eventName)) {
            return;
          }
          const parsed = parseSessionRuntimeState(message.data);
          if (parsed) {
            receivedEvent = true;
            options.onEvent(parsed, { eventName: message.eventName });
          }
        });
        if (closed) {
          return;
        }
        throw new Error("session runtime state stream closed");
      } catch (error) {
        if (closed || controller.signal.aborted) {
          return;
        }
        options.onStatus?.("error");
        options.onError?.(error);
        if (receivedEvent) {
          failureCount = 0;
        }
        const delay = SESSION_RUNTIME_RECONNECT_DELAYS_MS[
          Math.min(failureCount, SESSION_RUNTIME_RECONNECT_DELAYS_MS.length - 1)
        ];
        failureCount += 1;
        await new Promise<void>((resolve) => {
          resolveRetryWait = resolve;
          retryTimer = setTimeout(() => {
            retryTimer = null;
            resolveRetryWait = null;
            resolve();
          }, delay);
        });
      }
    }
  })();

  return {
    close: () => {
      if (closed) {
        return;
      }
      closed = true;
      controller?.abort();
      if (retryTimer) {
        clearTimeout(retryTimer);
        retryTimer = null;
      }
      resolveRetryWait?.();
      resolveRetryWait = null;
      options.onStatus?.("closed");
    }
  };
}

function runEventsUrl(baseUrl: string, agentId: string, runId: string, lastEventId?: string) {
  const url = new URL(`${baseUrl}/api/internal/agent/${encodeURIComponent(agentId)}/runs/${encodeURIComponent(runId)}/events`);
  if (lastEventId && lastEventId.trim().length > 0) {
    url.searchParams.set("lastEventId", lastEventId.trim());
  }
  return url.toString();
}

function sessionRuntimeStateEventsUrl(baseUrl: string) {
  return `${baseUrl}/api/internal/platform/opencode-runtime/sessions/runtime-state/events`;
}

function normalizeAgentId(agentId: string) {
  const normalized = agentId.trim().toLowerCase();
  return normalized.length > 0 ? normalized : "opencode";
}

export function parseRunEvent(data: string): RunEvent | null {
  try {
    const value = JSON.parse(data) as Partial<RunEvent>;
    if (!value.runId || typeof value.seq !== "number" || !value.type) {
      return null;
    }
    const eventId =
      typeof value.eventId === "string" && value.eventId.trim().length > 0
        ? value.eventId
        : `${value.runId}:${value.seq}`;
    return {
      eventId,
      runId: value.runId,
      seq: value.seq,
      type: value.type,
      traceId: value.traceId ?? "trace_unknown",
      occurredAt: value.occurredAt ?? new Date().toISOString(),
      payload: value.payload ?? {}
    };
  } catch {
    return null;
  }
}

type ParsedSseMessage = {
  eventName: string;
  data: string;
};

async function readSseStream(
  stream: ReadableStream<Uint8Array>,
  onMessage: (message: ParsedSseMessage) => void
) {
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) {
        break;
      }
      buffer += decoder.decode(value, { stream: true });
      buffer = drainSseBuffer(buffer, onMessage);
    }
    buffer += decoder.decode();
    drainSseBuffer(`${buffer}\n\n`, onMessage);
  } finally {
    reader.releaseLock();
  }
}

function drainSseBuffer(buffer: string, onMessage: (message: ParsedSseMessage) => void) {
  let remaining = buffer;
  while (true) {
    const delimiter = findSseDelimiter(remaining);
    if (!delimiter) {
      return remaining;
    }
    const block = remaining.slice(0, delimiter.index);
    remaining = remaining.slice(delimiter.index + delimiter.length);
    const parsed = parseSseBlock(block);
    if (parsed) {
      onMessage(parsed);
    }
  }
}

function findSseDelimiter(value: string): { index: number; length: number } | null {
  const lf = value.indexOf("\n\n");
  const crlf = value.indexOf("\r\n\r\n");
  if (lf === -1 && crlf === -1) {
    return null;
  }
  if (crlf !== -1 && (lf === -1 || crlf < lf)) {
    return { index: crlf, length: 4 };
  }
  return { index: lf, length: 2 };
}

function parseSseBlock(block: string): ParsedSseMessage | null {
  let eventName = "message";
  const dataLines: string[] = [];
  for (const line of block.replace(/\r\n/g, "\n").split("\n")) {
    if (line.startsWith(":")) {
      continue;
    }
    if (line.startsWith("event:")) {
      eventName = line.slice("event:".length).trim();
      continue;
    }
    if (line.startsWith("data:")) {
      dataLines.push(line.slice("data:".length).trimStart());
    }
  }
  if (dataLines.length === 0) {
    return null;
  }
  return { eventName, data: dataLines.join("\n") };
}

function isSessionRuntimeStateEvent(eventName: string) {
  return eventName === "session-runtime.snapshot" || eventName === "session-runtime.updated" || eventName === "message";
}

function parseSessionRuntimeState(data: string): SessionRuntimeStateSummary | null {
  try {
    const value = JSON.parse(data) as Partial<SessionRuntimeStateSummary>;
    if (typeof value.runningCount !== "number" || typeof value.questionCount !== "number" || !Array.isArray(value.sessions)) {
      return null;
    }
    return {
      runningCount: value.runningCount,
      questionCount: value.questionCount,
      sessions: value.sessions,
      generatedAt: typeof value.generatedAt === "string" ? value.generatedAt : new Date().toISOString()
    };
  } catch {
    return null;
  }
}
