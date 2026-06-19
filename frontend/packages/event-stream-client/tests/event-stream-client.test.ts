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

  emit(type: string, data: unknown) {
    const event = new MessageEvent(type, { data: JSON.stringify(data) });
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

  it("deduplicates by event id first and allows transient seq zero events", () => {
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

    expect(received).toEqual(["evt_1", "evt_live_1", "evt_live_2"]);
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

    expect(openedUrl).toBe("http://api/api/runs/run_1/events?lastEventId=4");
    expect(received).toEqual(["message.part.delta"]);
  });
});
