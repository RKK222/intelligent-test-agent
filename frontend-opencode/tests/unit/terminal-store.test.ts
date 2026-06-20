import { createPinia, setActivePinia } from "pinia";
import { usePlatformStore } from "@/stores/platform";
import { useTerminalStore } from "@/stores/terminal";

class FakeSocket {
  static instances: FakeSocket[] = [];

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

    Object.defineProperty(platform, "api", {
      value: {
        createTerminalTicket: async (...args: unknown[]) => {
          calls.push(args);
          return { ticket: "ticket_1", expiresAt: "2026-06-20T00:10:00Z", webSocketUrl: "ws://terminal.local/ses_1" };
        }
      }
    });

    await terminal.open("ses_1", { cols: 80, rows: 24 });

    expect(calls).toEqual([["ses_1", { cols: 80, rows: 24 }]]);
    expect(terminal.status).toBe("connecting");
    expect(FakeSocket.instances[0]?.url).toBe("ws://terminal.local/ses_1");

    FakeSocket.instances[0]?.onopen?.();
    expect(terminal.status).toBe("ready");

    FakeSocket.instances[0]?.onmessage?.({ data: "hello from pty\n" });
    expect(terminal.output.join("")).toContain("hello from pty");

    terminal.input = "pwd";
    terminal.sendInput();
    expect(FakeSocket.instances[0]?.sent).toEqual(["pwd\n"]);
    expect(terminal.input).toBe("");

    terminal.close();
    expect(terminal.status).toBe("closed");
  });
});
