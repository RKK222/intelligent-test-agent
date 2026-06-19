import type { RunEvent, RunEventType } from "@test-agent/shared-types";

export type RunEventStreamStatus = "connecting" | "open" | "closed" | "error";

export type RunEventSubscription = {
  close: () => void;
};

export type RunEventSubscribeOptions = {
  runId: string;
  baseUrl?: string;
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
  "tool.started",
  "tool.finished",
  "diff.proposed",
  "diff.accepted",
  "diff.rejected",
  "test.finished",
  "opencode.event.unknown"
];

export function subscribeRunEvents(options: RunEventSubscribeOptions): RunEventSubscription {
  const baseUrl = (options.baseUrl ?? "http://127.0.0.1:8080").replace(/\/$/, "");
  const factory = options.eventSourceFactory ?? ((url: string) => new EventSource(url));
  const url = `${baseUrl}/api/runs/${options.runId}/events`;
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
    const key = `${parsed.runId}:${parsed.seq}`;
    if (seen.has(key)) {
      return;
    }
    seen.add(key);
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

export function parseRunEvent(data: string): RunEvent | null {
  try {
    const value = JSON.parse(data) as Partial<RunEvent>;
    if (!value.eventId || !value.runId || typeof value.seq !== "number" || !value.type) {
      return null;
    }
    return {
      eventId: value.eventId,
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
