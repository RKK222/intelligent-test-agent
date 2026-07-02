import { describe, expect, it } from "vitest";
import { parseRunEvent, subscribeRunEvents, type EventSourceLike } from "../src";

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
});
