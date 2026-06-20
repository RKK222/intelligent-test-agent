import { createPinia, setActivePinia } from "pinia";
import { usePlatformStore } from "@/stores/platform";
import { useTerminalStore } from "@/stores/terminal";

class FakeSocket {
  static instances: FakeSocket[] = [];

  readyState = 1;
  onopen?: () => void;
  onmessage?: (event: { data: string }) => void;
  onerror?: () => void;
  onclose?: () => void;
  sent: string[] = [];

  constructor(readonly url: string) {
    FakeSocket.instances.push(this);
  }

  send(data: string) {
    this.sent.push(data);
  }

  close() {
    this.onclose?.();
  }
}

describe("terminal store", () => {
  it("opens a terminal ticket, connects websocket, streams output, and sends input", async () => {
    setActivePinia(createPinia());
    FakeSocket.instances = [];
    vi.stubGlobal("WebSocket", FakeSocket);

    const platform = usePlatformStore();
    const terminal = useTerminalStore();
    const calls: unknown[] = [];
    platform.baseUrl = "http://api.local";

    Object.defineProperty(platform, "api", {
      value: {
        createTerminalTicket: async (...args: unknown[]) => {
          calls.push(args);
          return { ticket: "ticket_1", expiresAt: "2026-06-20T00:10:00Z", webSocketUrl: "/api/sessions/ses_1/terminal/ws?ticket=ticket_1" };
        }
      }
    });

    await terminal.open("ses_1", { cols: 80, rows: 24 });

    expect(calls).toEqual([["ses_1", { cols: 80, rows: 24 }]]);
    expect(terminal.cols).toBe(80);
    expect(terminal.rows).toBe(24);
    expect(terminal.status).toBe("connecting");
    expect(FakeSocket.instances[0]?.url).toBe("ws://api.local/api/sessions/ses_1/terminal/ws?ticket=ticket_1");

    FakeSocket.instances[0]?.onopen?.();
    expect(terminal.status).toBe("ready");

    FakeSocket.instances[0]?.onmessage?.({ data: JSON.stringify({ type: "output", data: "hello from pty\n", seq: 1 }) });
    expect(terminal.output.join("")).toContain("hello from pty");
    FakeSocket.instances[0]?.onmessage?.({ data: JSON.stringify({ type: "warning", code: "PTY_OUTPUT_TRUNCATED", message: "truncated" }) });
    expect(terminal.warnings).toEqual([{ code: "PTY_OUTPUT_TRUNCATED", message: "truncated" }]);

    terminal.resize(120, 32);
    terminal.input = "pwd";
    terminal.sendInput();
    expect(FakeSocket.instances[0]?.sent.map((item) => JSON.parse(item))).toEqual([
      { type: "resize", cols: 120, rows: 32 },
      { type: "input", data: "pwd\n" }
    ]);
    expect(terminal.input).toBe("");

    for (let index = 0; index < 505; index += 1) {
      FakeSocket.instances[0]?.onmessage?.({ data: JSON.stringify({ type: "output", data: `${index}\n`, seq: index + 2 }) });
    }
    expect(terminal.output).toHaveLength(500);

    terminal.close();
    expect(FakeSocket.instances[0]?.sent.map((item) => JSON.parse(item)).at(-1)).toEqual({ type: "close", reason: "user" });
    expect(terminal.status).toBe("closed");
  });
});
