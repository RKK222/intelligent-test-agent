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
  /**
   * 浏览器原生 EventSource 不能携带 Authorization；已登录页面传入 token 后改用 fetch 流式读取 SSE。
   * token 仅放在请求头，绝不拼接到 URL，避免被浏览器历史、代理日志或原始报文面板记录。
   */
  token?: string | null;
  /** Nginx 首跳路由提示；只作性能优化，后端仍以 Run 归属为准。 */
  linuxServerId?: string | null;
  fetcher?: typeof fetch;
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
  /** Nginx 首跳路由提示；页面内存中没有绑定服务器时不发送。 */
  linuxServerId?: string | null;
  fetcher?: typeof fetch;
  onEvent: (event: SessionRuntimeStateSummary, meta: { eventName: string }) => void;
  onStatus?: (status: SessionRuntimeStateStreamStatus) => void;
  onError?: (error: unknown) => void;
};

const SESSION_RUNTIME_RECONNECT_DELAYS_MS = [1_000, 2_000, 5_000, 10_000, 30_000] as const;
const RUN_EVENT_RECONNECT_DELAYS_MS = [1_000, 2_000, 5_000, 10_000, 30_000] as const;
const LINUX_SERVER_ROUTE_HEADER = "X-Test-Agent-Linux-Server-Id";

export const KNOWN_RUN_EVENT_TYPES: RunEventType[] = [
  "run.created",
  "run.started",
  "run.cancelling",
  "run.succeeded",
  "run.failed",
  "run.cancelled",
  "run.snapshot.reset",
  "side_question.started",
  "side_question.progress",
  "side_question.delta",
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
  // 测试/嵌入方显式提供 EventSource factory 时保留其传输契约；真实登录页面未提供 factory，必须走带鉴权的 fetch。
  if (options.token?.trim() && !options.eventSourceFactory) {
    return subscribeAuthenticatedRunEvents(options);
  }
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
    // EventSource 与带鉴权的 fetch SSE 必须采用同一去重规则，避免两条入口投影行为分叉。
    if (!shouldDeliverRunEvent(parsed, seen)) {
      return;
    }
    // durable 游标只由浏览器解析 SSE `id` 字段维护；不能从 payload.eventId 或 seq=0 的
    // run.snapshot.reset 推导 Last-Event-ID，否则重连会跳过快照后的 durable 事件。
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

/**
 * 使用 fetch 建立带 Bearer Token 的 RunEvent SSE，并在断流后按固定退避续传最后一个 durable event id。
 */
function subscribeAuthenticatedRunEvents(options: RunEventSubscribeOptions): RunEventSubscription {
  const baseUrl = (options.baseUrl ?? "http://127.0.0.1:8080").replace(/\/$/, "");
  const agentId = normalizeAgentId(options.agentId ?? "opencode");
  const fetcher = options.fetcher ?? fetch;
  const seen = new Set<string>();
  let closed = false;
  let controller: AbortController | null = null;
  let retryTimer: ReturnType<typeof setTimeout> | null = null;
  let resolveRetryWait: (() => void) | null = null;
  let resumeEventId = options.lastEventId;

  const deliver = (message: ParsedSseMessage) => {
    if (closed) {
      return;
    }
    options.onRawMessage?.({
      runId: options.runId,
      eventName: message.eventName,
      lastEventId: message.id,
      data: message.data,
      receivedAt: new Date().toISOString()
    });
    const parsed = parseRunEvent(message.data);
    if (!parsed || parsed.runId !== options.runId) {
      return;
    }
    // 续传游标只能使用 SSE id；seq=0 的 transient 事件不能推进 durable cursor。
    if (message.id?.trim()) {
      resumeEventId = message.id;
    }
    if (!shouldDeliverRunEvent(parsed, seen)) {
      return;
    }
    options.onEvent(parsed);
  };

  void (async () => {
    let failureCount = 0;
    while (!closed) {
      controller = new AbortController();
      options.onStatus?.("connecting");
      try {
        const headers = new Headers();
        headers.set("Accept", "text/event-stream");
        headers.set("Authorization", `Bearer ${options.token!.trim()}`);
        setLinuxServerRouteHeader(headers, options.linuxServerId);
        const response = await fetcher(runEventsUrl(baseUrl, agentId, options.runId, resumeEventId), {
          headers,
          signal: controller.signal
        });
        if (!response.ok) {
          throw new Error(`run event stream failed: ${response.status}`);
        }
        if (!response.body) {
          throw new Error("run event stream has no body");
        }
        if (closed) {
          return;
        }
        options.onStatus?.("open");
        await readSseStream(response.body, deliver);
        if (closed) {
          return;
        }
        throw new Error("run event stream closed");
      } catch (error) {
        if (closed || controller.signal.aborted) {
          return;
        }
        options.onStatus?.("error");
        options.onError?.(error instanceof Event ? error : new Event("error"));
        const delay = RUN_EVENT_RECONNECT_DELAYS_MS[
          Math.min(failureCount, RUN_EVENT_RECONNECT_DELAYS_MS.length - 1)
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

/** 按 eventId/seq 去重，确保 EventSource 与 fetch SSE 两条实现的投影语义完全一致。 */
function shouldDeliverRunEvent(parsed: RunEvent, seen: Set<string>) {
  const isFallback = parsed.eventId === `${parsed.runId}:${parsed.seq}`;
  if (!isFallback) {
    const key = `event:${parsed.eventId}`;
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  }
  if (parsed.seq > 0) {
    const key = `seq:${parsed.runId}:${parsed.seq}`;
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
  }
  return true;
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
        setLinuxServerRouteHeader(headers, options.linuxServerId);
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
  const path = `${baseUrl}/api/internal/agent/${encodeURIComponent(agentId)}/runs/${encodeURIComponent(runId)}/events`;
  const resumeEventId = lastEventId?.trim();
  if (!resumeEventId) {
    return path;
  }
  // 企业同源构建会显式传入空 baseUrl；此时必须保留相对 URL，不能交给缺少 origin 的 new URL()。
  return `${path}?${new URLSearchParams({ lastEventId: resumeEventId }).toString()}`;
}

function sessionRuntimeStateEventsUrl(baseUrl: string) {
  return `${baseUrl}/api/internal/platform/opencode-runtime/sessions/runtime-state/events`;
}

/** 空值时不发头，保持首次进程查询和旧前端的 least_conn 行为。 */
function setLinuxServerRouteHeader(headers: Headers, linuxServerId?: string | null) {
  const normalized = linuxServerId?.trim();
  if (normalized) {
    headers.set(LINUX_SERVER_ROUTE_HEADER, normalized);
  }
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
  id?: string;
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
  let id: string | undefined;
  const dataLines: string[] = [];
  for (const line of block.replace(/\r\n/g, "\n").split("\n")) {
    if (line.startsWith(":")) {
      continue;
    }
    if (line.startsWith("event:")) {
      eventName = line.slice("event:".length).trim();
      continue;
    }
    if (line.startsWith("id:")) {
      id = line.slice("id:".length).trim();
      continue;
    }
    if (line.startsWith("data:")) {
      dataLines.push(line.slice("data:".length).trimStart());
    }
  }
  if (dataLines.length === 0) {
    return null;
  }
  return { eventName, data: dataLines.join("\n"), id };
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
      permissionCount: typeof value.permissionCount === "number"
        ? value.permissionCount
        : value.sessions.filter((item) => item?.attention === "PERMISSION").length,
      sessions: value.sessions,
      generatedAt: typeof value.generatedAt === "string" ? value.generatedAt : new Date().toISOString()
    };
  } catch {
    return null;
  }
}
