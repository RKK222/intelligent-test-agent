import { describe, expect, it, vi } from "vitest";
import { createTerminalSession } from "../src";

class FakeWebSocket {
  static instances: FakeWebSocket[] = [];

  onopen: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onerror: (() => void) | null = null;
  onclose: (() => void) | null = null;
  readonly sent: string[] = [];
  readyState = 0;

  constructor(readonly url: string) {
    FakeWebSocket.instances.push(this);
  }

  send(data: string) {
    this.sent.push(data);
  }

  close() {
    this.readyState = 3;
    this.onclose?.();
  }

  open() {
    this.readyState = 1;
    this.onopen?.();
  }

  emit(value: unknown) {
    this.onmessage?.({ data: JSON.stringify(value) });
  }
}

describe("terminal client", () => {
  it("connects with the ticket URL, captures output, and sends terminal commands", () => {
    FakeWebSocket.instances = [];
    const events: string[] = [];
    const session = createTerminalSession({
      baseUrl: "http://127.0.0.1:8080",
      ticket: {
        ticket: "pty_123",
        expiresAt: "2026-06-19T13:00:00Z",
        webSocketUrl: "/api/internal/platform/opencode-runtime/sessions/ses_1/terminal/ws?ticket=pty_123"
      },
      WebSocketCtor: FakeWebSocket,
      onEvent: (event) => events.push(event.type)
    });

    expect(FakeWebSocket.instances[0]?.url).toBe(
      "ws://127.0.0.1:8080/api/internal/platform/opencode-runtime/sessions/ses_1/terminal/ws?ticket=pty_123"
    );

    FakeWebSocket.instances[0]?.open();
    session.sendInput("npm test\n");
    session.resize(120, 32);
    FakeWebSocket.instances[0]?.emit({ type: "output", data: "hello", seq: 1 });
    FakeWebSocket.instances[0]?.emit({ type: "exit", code: 0, seq: 2 });
    session.close("done");

    expect(session.snapshot()).toMatchObject({
      status: "closed",
      output: "hello",
      exitCode: 0
    });
    expect(FakeWebSocket.instances[0]?.sent.map((item) => JSON.parse(item))).toEqual([
      { type: "input", data: "npm test\n" },
      { type: "resize", cols: 120, rows: 32 },
      { type: "close", reason: "done" }
    ]);
    expect(events).toEqual(["open", "output", "exit", "close"]);
  });

  it("surfaces server error envelopes with traceable state", () => {
    FakeWebSocket.instances = [];
    const onEvent = vi.fn();
    const session = createTerminalSession({
      baseUrl: "http://localhost:3000",
      ticket: {
        ticket: "pty_123",
        expiresAt: "2026-06-19T13:00:00Z",
        webSocketUrl: "ws://localhost:8080/api/internal/platform/opencode-runtime/sessions/ses_1/terminal/ws?ticket=pty_123"
      },
      WebSocketCtor: FakeWebSocket,
      onEvent
    });

    FakeWebSocket.instances[0]?.open();
    FakeWebSocket.instances[0]?.emit({ type: "error", code: "PTY_DENIED", message: "origin denied" });

    expect(session.snapshot()).toMatchObject({
      status: "error",
      error: { code: "PTY_DENIED", message: "origin denied" }
    });
    expect(onEvent).toHaveBeenLastCalledWith({ type: "error", code: "PTY_DENIED", message: "origin denied" });
  });

  it("surfaces server warning envelopes without closing the terminal", () => {
    FakeWebSocket.instances = [];
    const onEvent = vi.fn();
    const session = createTerminalSession({
      baseUrl: "http://localhost:3000",
      ticket: {
        ticket: "pty_123",
        expiresAt: "2026-06-19T13:00:00Z",
        webSocketUrl: "ws://localhost:8080/api/internal/platform/opencode-runtime/sessions/ses_1/terminal/ws?ticket=pty_123"
      },
      WebSocketCtor: FakeWebSocket,
      onEvent
    });

    FakeWebSocket.instances[0]?.open();
    FakeWebSocket.instances[0]?.emit({ type: "warning", code: "PTY_OUTPUT_TRUNCATED", message: "terminal output truncated" });

    expect(session.snapshot()).toMatchObject({
      status: "open",
      warnings: [{ code: "PTY_OUTPUT_TRUNCATED", message: "terminal output truncated" }]
    });
    expect(onEvent).toHaveBeenLastCalledWith({
      type: "warning",
      code: "PTY_OUTPUT_TRUNCATED",
      message: "terminal output truncated"
    });
  });
});
