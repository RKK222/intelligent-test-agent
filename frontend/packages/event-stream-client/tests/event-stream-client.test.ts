import { describe, expect, it, vi } from "vitest";
import { parseRunEvent, subscribeRunEvents, subscribeSessionRuntimeState, type EventSourceLike } from "../src";

class FakeEventSource implements EventSourceLike {
  onopen: ((event: Event) => void) | null = null;
  onerror: ((event: Event) => void) | null = null;
  closed = false;
  listeners = new Map<string, EventListener[]>();

  addEventListener(type: string, listener: EventListener) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener]);
  }

  removeEventListener(type: string, listener: EventListener) {
    this.listeners.set(
      type,
      (this.listeners.get(type) ?? []).filter((item) => item !== listener)
    );
  }

  close() {
    this.closed = true;
  }

  emit(type: string, data: unknown, lastEventId = "") {
    const event = new MessageEvent(type, { data: JSON.stringify(data), lastEventId });
    this.listeners.get(type)?.forEach((listener) => listener(event));
  }

  emitRaw(type: string, data: string, lastEventId = "") {
    const event = new MessageEvent(type, { data, lastEventId });
    this.listeners.get(type)?.forEach((listener) => listener(event));
  }
}

describe("event-stream-client", () => {
  it("parses run event payloads defensively", () => {
    expect(parseRunEvent("{bad")).toBeNull();
    expect(
      parseRunEvent(
        JSON.stringify({
          eventId: "evt_1",
          runId: "run_1",
          seq: 1,
          type: "run.started",
          payload: { status: "RUNNING" }
        })
      )
    ).toMatchObject({ eventId: "evt_1", type: "run.started" });
  });

  it("deduplicates all events by event id first and allows distinct transient seq zero events", () => {
    const source = new FakeEventSource();
    const received: string[] = [];
    const statuses: string[] = [];

    const subscription = subscribeRunEvents({
      baseUrl: "http://api",
      runId: "run_1",
      eventSourceFactory: () => source,
      onStatus: (status) => statuses.push(status),
      onEvent: (event) => received.push(event.eventId)
    });

    source.onopen?.(new Event("open"));
    source.emit("run.started", { eventId: "evt_1", runId: "run_1", seq: 1, type: "run.started", payload: {} });
    source.emit("run.started", { eventId: "evt_1", runId: "run_1", seq: 99, type: "run.started", payload: {} });
    source.emit("message.part.delta", {
      eventId: "evt_live_1",
      runId: "run_1",
      seq: 0,
      type: "message.part.delta",
      payload: { delta: "hel" }
    });
    source.emit("message.part.delta", {
      eventId: "evt_live_2",
      runId: "run_1",
      seq: 0,
      type: "message.part.delta",
      payload: { delta: "lo" }
    });
    source.emit("assistant.message.delta", {
      eventId: "same_evt_id",
      runId: "run_1",
      seq: 1,
      type: "assistant.message.delta",
      payload: { delta: "a" }
    });
    source.emit("assistant.message.delta", {
      eventId: "same_evt_id",
      runId: "run_1",
      seq: 2,
      type: "assistant.message.delta",
      payload: { delta: "b" }
    });

    expect(received).toEqual(["evt_1", "evt_live_1", "evt_live_2", "same_evt_id"]);
    expect(statuses).toEqual(["connecting", "open"]);

    subscription.close();
    expect(source.closed).toBe(true);
    expect(statuses.at(-1)).toBe("closed");
  });

  it("subscribes to Phase 11 opencode app events and resumes with lastEventId query", () => {
    const source = new FakeEventSource();
    const received: string[] = [];
    let openedUrl = "";

    subscribeRunEvents({
      baseUrl: "http://api",
      runId: "run_1",
      lastEventId: "4",
      eventSourceFactory: (url) => {
        openedUrl = url;
        return source;
      },
      onEvent: (event) => received.push(event.type)
    });

    source.emit("message.part.delta", {
      eventId: "evt_5",
      runId: "run_1",
      seq: 5,
      type: "message.part.delta",
      payload: { delta: "hello" }
    });

    expect(openedUrl).toBe("http://api/api/internal/agent/opencode/runs/run_1/events?lastEventId=4");
    expect(received).toEqual(["message.part.delta"]);
  });

  it("subscribes to session.updated named SSE events", () => {
    const source = new FakeEventSource();
    const received: string[] = [];

    subscribeRunEvents({
      baseUrl: "http://api",
      runId: "run_1",
      eventSourceFactory: () => source,
      onEvent: (event) => received.push(event.type)
    });

    source.emit("session.updated", {
      eventId: "evt_session_title",
      runId: "run_1",
      seq: 6,
      type: "session.updated",
      payload: { info: { title: "自动标题" } }
    });

    expect(source.listeners.get("session.updated")).toHaveLength(1);
    expect(received).toEqual(["session.updated"]);
  });

  it("ignores queued messages after close and events from other runs", () => {
    const source = new FakeEventSource();
    const received: string[] = [];

    const subscription = subscribeRunEvents({
      baseUrl: "http://api",
      runId: "run_1",
      eventSourceFactory: () => source,
      onEvent: (event) => received.push(event.runId)
    });

    source.emit("run.started", { eventId: "evt_1", runId: "run_other", seq: 1, type: "run.started", payload: {} });
    subscription.close();
    const closedListeners = [...(source.listeners.get("run.started") ?? [])];
    closedListeners.forEach((listener) =>
      listener(new MessageEvent("run.started", {
        data: JSON.stringify({ eventId: "evt_2", runId: "run_1", seq: 2, type: "run.started", payload: {} })
      }))
    );

    expect(received).toEqual([]);
  });

  it("reports raw SSE message data before attempting to parse run events", () => {
    const source = new FakeEventSource();
    const rawMessages: Array<Record<string, string | undefined>> = [];
    const received: string[] = [];

    subscribeRunEvents({
      baseUrl: "http://api",
      runId: "run_1",
      eventSourceFactory: () => source,
      onRawMessage: (message: Record<string, string | undefined>) => rawMessages.push(message),
      onEvent: (event) => received.push(event.type)
    } as Parameters<typeof subscribeRunEvents>[0] & {
      onRawMessage: (message: Record<string, string | undefined>) => void;
    });

    source.emitRaw("message.part.delta", "{bad json", "evt_raw_bad");
    source.emit("message.part.delta", {
      eventId: "evt_1",
      runId: "run_1",
      seq: 1,
      type: "message.part.delta",
      payload: { delta: "hello" }
    }, "evt_raw_good");

    expect(rawMessages).toHaveLength(2);
    expect(rawMessages[0]).toMatchObject({
      runId: "run_1",
      eventName: "message.part.delta",
      data: "{bad json",
      lastEventId: "evt_raw_bad"
    });
    expect(rawMessages[0].receivedAt).toEqual(expect.any(String));
    expect(rawMessages[1].data).toContain('"delta":"hello"');
    expect(received).toEqual(["message.part.delta"]);
  });

  it("uses custom agent id when opening the SSE stream", () => {
    const source = new FakeEventSource();
    let openedUrl = "";

    subscribeRunEvents({
      baseUrl: "http://api",
      agentId: "OtherAgent",
      runId: "run_1",
      eventSourceFactory: (url) => {
        openedUrl = url;
        return source;
      },
      onEvent: () => undefined
    });

    expect(openedUrl).toBe("http://api/api/internal/agent/otheragent/runs/run_1/events");
  });

  it("does not incorrectly discard seq zero transient events when eventId is missing", () => {
    const source = new FakeEventSource();
    const received: string[] = [];

    subscribeRunEvents({
      baseUrl: "http://api",
      runId: "run_1",
      eventSourceFactory: () => source,
      onEvent: (event) => received.push((event.payload as { delta: string }).delta)
    });

    source.emit("message.part.delta", {
      runId: "run_1",
      seq: 0,
      type: "message.part.delta",
      payload: { delta: "hel" }
    });
    source.emit("message.part.delta", {
      runId: "run_1",
      seq: 0,
      type: "message.part.delta",
      payload: { delta: "lo" }
    });

    expect(received).toEqual(["hel", "lo"]);
  });

  it("subscribes to session runtime state with bearer token and parses snapshot plus updates", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      sseResponse([
        "event: session-runtime.snapshot\n",
        'data: {"runningCount":0,"questionCount":0,"sessions":[],"generatedAt":"2026-07-08T08:00:00Z"}\n\n',
        "event: session-runtime.updated\n",
        'data: {"runningCount":1,"questionCount":1,"sessions":[{"sessionId":"ses_1","runId":"run_1","runStatus":"RUNNING","attention":"QUESTION","attentionEventId":"evt_1","attentionAt":"2026-07-08T08:01:00Z","updatedAt":"2026-07-08T08:01:02Z"}],"generatedAt":"2026-07-08T08:01:03Z"}\n\n'
      ])
    );
    const received: Array<{ runningCount: number; questionCount: number }> = [];
    const statuses: string[] = [];

    subscribeSessionRuntimeState({
      baseUrl: "http://api",
      token: "token_secret",
      fetcher,
      onStatus: (status) => statuses.push(status),
      onEvent: (event) => received.push(event)
    });

    await waitFor(() => received.length === 2);

    expect(fetcher).toHaveBeenCalledWith(
      "http://api/api/internal/platform/opencode-runtime/sessions/runtime-state/events",
      expect.objectContaining({ headers: expect.any(Headers), signal: expect.any(AbortSignal) })
    );
    const headers = fetcher.mock.calls[0]?.[1]?.headers as Headers;
    expect(headers.get("Authorization")).toBe("Bearer token_secret");
    expect(headers.get("Accept")).toBe("text/event-stream");
    expect(received).toEqual([
      expect.objectContaining({ runningCount: 0, questionCount: 0 }),
      expect.objectContaining({ runningCount: 1, questionCount: 1 })
    ]);
    expect(statuses).toContain("open");
    expect(statuses.at(-1)).toBe("closed");
  });

  it("aborts the session runtime state fetch when closed", async () => {
    let capturedSignal: AbortSignal | undefined;
    const fetcher = vi.fn<typeof fetch>().mockImplementation((_url, init) => {
      capturedSignal = init?.signal as AbortSignal;
      return Promise.resolve(new Response(new ReadableStream(), { headers: { "content-type": "text/event-stream" } }));
    });
    const statuses: string[] = [];

    const subscription = subscribeSessionRuntimeState({
      baseUrl: "http://api",
      token: "token_secret",
      fetcher,
      onStatus: (status) => statuses.push(status),
      onEvent: () => undefined
    });

    await waitFor(() => capturedSignal !== undefined);
    subscription.close();

    expect(capturedSignal?.aborted).toBe(true);
    expect(statuses.at(-1)).toBe("closed");
  });
});

function sseResponse(chunks: string[]) {
  const encoder = new TextEncoder();
  return new Response(
    new ReadableStream({
      start(controller) {
        chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
        controller.close();
      }
    }),
    { headers: { "content-type": "text/event-stream" } }
  );
}

async function waitFor(predicate: () => boolean) {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    if (predicate()) return;
    await new Promise((resolve) => setTimeout(resolve, 0));
  }
  throw new Error("condition was not met");
}
