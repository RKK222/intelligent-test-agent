import type { RunEvent, RunEventType } from "@test-agent/shared-types";

export type RunEventStreamStatus = "connecting" | "open" | "closed" | "error";

export type RunEventSubscription = {
  close: () => void;
};

export type RunEventSubscribeOptions = {
  runId: string;
  baseUrl?: string;
  agentId?: string;
  lastEventId?: string;
  onEvent: (event: RunEvent) => void;
  onStatus?: (status: RunEventStreamStatus) => void;
  onError?: (error: Event) => void;
  eventSourceFactory?: EventSourceFactory;
};

export type EventSourceLike = {
  onopen: ((event: Event) => void) | null;
  onerror: ((event: Event) => void) | null;
  addEventListener: (type: string, listener: EventListener) => void;
  removeEventListener: (type: string, listener: EventListener) => void;
  close: () => void;
};

export type EventSourceFactory = (url: string) => EventSourceLike;

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
    const message = event as MessageEvent<string>;
    const parsed = parseRunEvent(message.data);
    if (!parsed) {
      return;
    }
    // 如果 eventId 是 fallback 的 runId:seq，说明后端未提供显式 eventId
    const isFallback = parsed.eventId === `${parsed.runId}:${parsed.seq}`;
    // 优先按真实 eventId 去重，兼容旧事件回退 runId + seq；seq=0 transient 文本事件不能因为相同 seq 被错误丢弃
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

function runEventsUrl(baseUrl: string, agentId: string, runId: string, lastEventId?: string) {
  const url = new URL(`${baseUrl}/api/internal/agent/${encodeURIComponent(agentId)}/runs/${encodeURIComponent(runId)}/events`);
  if (lastEventId && lastEventId.trim().length > 0) {
    url.searchParams.set("lastEventId", lastEventId.trim());
  }
  return url.toString();
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
