import { fireEvent, render, waitFor } from "@testing-library/vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const xtermHarness = vi.hoisted(() => {
  const instances: any[] = [];
  class FakeTerminal {
    cols = 80;
    rows = 24;
    focus = vi.fn();
    clear = vi.fn();
    write = vi.fn();
    writeln = vi.fn();
    dispose = vi.fn();
    dataListener?: (data: string) => void;
    resizeListener?: (size: { cols: number; rows: number }) => void;

    constructor() {
      instances.push(this);
    }

    loadAddon() {}
    open() {}
    onData(listener: (data: string) => void) {
      this.dataListener = listener;
      return { dispose() {} };
    }
    onResize(listener: (size: { cols: number; rows: number }) => void) {
      this.resizeListener = listener;
      return { dispose() {} };
    }
  }
  return { instances, FakeTerminal };
});

const fitHarness = vi.hoisted(() => {
  const fits = vi.fn();
  class FakeFitAddon {
    fit = fits;
  }
  return { fits, FakeFitAddon };
});

vi.mock("@xterm/xterm", () => ({ Terminal: xtermHarness.FakeTerminal }));
vi.mock("@xterm/addon-fit", () => ({ FitAddon: fitHarness.FakeFitAddon }));

import TerminalPanel from "../src/TerminalPanel.vue";

class FakeWebSocket {
  static instances: FakeWebSocket[] = [];
  readyState = 0;
  sent: string[] = [];
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onerror: (() => void) | null = null;
  onclose: (() => void) | null = null;

  constructor() {
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
}

describe("TerminalPanel", () => {
  let resizeCallback: ResizeObserverCallback;
  let nextFrameId = 1;
  let frameCallbacks = new Map<number, FrameRequestCallback>();

  beforeEach(() => {
    xtermHarness.instances.length = 0;
    fitHarness.fits.mockClear();
    FakeWebSocket.instances = [];
    frameCallbacks = new Map();
    vi.stubGlobal("requestAnimationFrame", (callback: FrameRequestCallback) => {
      const id = nextFrameId++;
      frameCallbacks.set(id, callback);
      return id;
    });
    vi.stubGlobal("cancelAnimationFrame", (id: number) => frameCallbacks.delete(id));
    vi.stubGlobal("ResizeObserver", class {
      constructor(callback: ResizeObserverCallback) {
        resizeCallback = callback;
      }
      observe() {}
      disconnect() {}
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function flushFrames() {
    const pending = [...frameCallbacks.entries()];
    frameCallbacks.clear();
    for (const [, callback] of pending) callback(0);
  }

  it("合并重复尺寸通知，并在连接后聚焦和转发键盘输入", async () => {
    const view = render(TerminalPanel, {
      props: {
        baseUrl: "https://console.example",
        createTicket: vi.fn().mockResolvedValue({
          ticket: "pty_server",
          expiresAt: "2026-07-18T14:00:00Z",
          webSocketUrl: "wss://console.example/terminal/ws?ticket=pty_server"
        }),
        WebSocketCtor: FakeWebSocket as any
      }
    });

    const entry = { contentRect: { width: 800, height: 360 } } as ResizeObserverEntry;
    resizeCallback([entry], {} as ResizeObserver);
    resizeCallback([entry], {} as ResizeObserver);
    flushFrames();
    expect(fitHarness.fits).toHaveBeenCalledTimes(1);

    await fireEvent.click(view.getByRole("button", { name: "连接终端" }));
    await waitFor(() => expect(FakeWebSocket.instances).toHaveLength(1));
    FakeWebSocket.instances[0].open();

    const terminal = xtermHarness.instances[0];
    expect(terminal.focus).toHaveBeenCalledTimes(1);
    terminal.dataListener?.("whoami\r");
    expect(FakeWebSocket.instances[0].sent.map((item) => JSON.parse(item))).toEqual([
      { type: "resize", cols: 80, rows: 24 },
      { type: "input", data: "whoami\r" }
    ]);
  });

  it("用户取消连接确认时恢复 idle 且不展示 ticket 失败", async () => {
    const aborted = new Error("用户取消连接");
    aborted.name = "AbortError";
    const view = render(TerminalPanel, {
      props: {
        baseUrl: "https://console.example",
        createTicket: vi.fn().mockRejectedValue(aborted),
        WebSocketCtor: FakeWebSocket as any
      }
    });

    await fireEvent.click(view.getByRole("button", { name: "连接终端" }));
    await waitFor(() => expect(view.getByText("idle")).toBeTruthy());
    expect(view.queryByText(/PTY_TICKET_FAILED/)).toBeNull();
    expect(FakeWebSocket.instances).toHaveLength(0);
  });
});
