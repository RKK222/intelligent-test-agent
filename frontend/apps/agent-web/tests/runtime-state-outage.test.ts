import { afterEach, describe, expect, it, vi } from "vitest";

import { createRuntimeStateOutageTracker } from "../src/components/runtime-state-outage";

describe("runtime state outage tracker", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("keeps repeated short SSE connections in one outage", () => {
    vi.useFakeTimers();
    const tracker = createRuntimeStateOutageTracker(5_000);

    tracker.onError();
    expect(tracker.takeFallback("ses_1")?.outageGeneration).toBe(1);

    tracker.onOpen();
    vi.advanceTimersByTime(4_999);
    tracker.onError();
    expect(tracker.takeFallback("ses_1")).toBeNull();

    tracker.onOpen();
    tracker.onError();
    expect(tracker.takeFallback("ses_1")).toBeNull();
  });

  it("allows a new fallback after the SSE connection stays stable", () => {
    vi.useFakeTimers();
    const tracker = createRuntimeStateOutageTracker(5_000);

    tracker.onError();
    expect(tracker.takeFallback("ses_1")?.outageGeneration).toBe(1);

    tracker.onOpen();
    vi.advanceTimersByTime(5_000);
    tracker.onError();

    expect(tracker.takeFallback("ses_1")?.outageGeneration).toBe(2);
  });

  it("invalidates an in-flight fallback after a newer snapshot or reset", () => {
    const tracker = createRuntimeStateOutageTracker(5_000);

    tracker.onError();
    const snapshotLease = tracker.takeFallback("ses_snapshot");
    expect(tracker.isCurrent(snapshotLease!)).toBe(true);
    tracker.onSnapshot();
    expect(tracker.isCurrent(snapshotLease!)).toBe(false);

    tracker.reset();
    tracker.onError();
    const authLease = tracker.takeFallback("ses_auth");
    expect(tracker.isCurrent(authLease!)).toBe(true);
    tracker.reset();
    expect(tracker.isCurrent(authLease!)).toBe(false);
  });
});
