import type { TerminalTicketResponse } from "@test-agent/shared-types";

export type TerminalStatus = "idle" | "connecting" | "open" | "closed" | "error";

export type TerminalSnapshot = {
  status: TerminalStatus;
  output: string;
  exitCode?: number;
  error?: { code: string; message: string };
};

export type TerminalClientEvent =
  | { type: "open" }
  | { type: "output"; data: string; seq?: number }
  | { type: "exit"; code: number; seq?: number }
  | { type: "error"; code: string; message: string }
  | { type: "close" };

export type TerminalWebSocketLike = {
  readyState: number;
  onopen: (() => void) | null;
  onmessage: ((event: { data: string }) => void) | null;
  onerror: (() => void) | null;
  onclose: (() => void) | null;
  send: (data: string) => void;
  close: () => void;
};

export type TerminalWebSocketConstructor = new (url: string) => TerminalWebSocketLike;

export type CreateTerminalSessionOptions = {
  baseUrl: string;
  ticket: TerminalTicketResponse;
  WebSocketCtor?: TerminalWebSocketConstructor;
  onEvent?: (event: TerminalClientEvent) => void;
};

export type TerminalSession = {
  snapshot: () => TerminalSnapshot;
  sendInput: (data: string) => void;
  resize: (cols: number, rows: number) => void;
  close: (reason?: string) => void;
};

export function createTerminalSession({
  baseUrl,
  ticket,
  WebSocketCtor,
  onEvent
}: CreateTerminalSessionOptions): TerminalSession {
  const WebSocketImpl = WebSocketCtor ?? defaultWebSocketCtor();
  const socket = new WebSocketImpl(toWebSocketUrl(baseUrl, ticket.webSocketUrl));
  let snapshot: TerminalSnapshot = { status: "connecting", output: "" };

  function emit(event: TerminalClientEvent) {
    onEvent?.(event);
  }

  socket.onopen = () => {
    snapshot = { ...snapshot, status: "open" };
    emit({ type: "open" });
  };
  socket.onmessage = (event) => {
    const message = parseServerMessage(event.data);
    if (!message) {
      return;
    }
    if (message.type === "output") {
      snapshot = { ...snapshot, output: `${snapshot.output}${message.data}` };
      emit({ type: "output", data: message.data, seq: message.seq });
      return;
    }
    if (message.type === "exit") {
      snapshot = { ...snapshot, status: "closed", exitCode: message.code };
      emit({ type: "exit", code: message.code, seq: message.seq });
      return;
    }
    if (message.type === "error") {
      snapshot = { ...snapshot, status: "error", error: { code: message.code, message: message.message } };
      emit({ type: "error", code: message.code, message: message.message });
    }
  };
  socket.onerror = () => {
    snapshot = { ...snapshot, status: "error", error: { code: "PTY_SOCKET_ERROR", message: "terminal socket error" } };
    emit({ type: "error", code: "PTY_SOCKET_ERROR", message: "terminal socket error" });
  };
  socket.onclose = () => {
    snapshot = { ...snapshot, status: snapshot.status === "error" ? "error" : "closed" };
    emit({ type: "close" });
  };

  return {
    snapshot: () => snapshot,
    sendInput: (data: string) => {
      if (data && socket.readyState === 1) {
        socket.send(JSON.stringify({ type: "input", data }));
      }
    },
    resize: (cols: number, rows: number) => {
      if (socket.readyState === 1 && cols > 0 && rows > 0) {
        socket.send(JSON.stringify({ type: "resize", cols, rows }));
      }
    },
    close: (reason = "user") => {
      if (socket.readyState === 1) {
        socket.send(JSON.stringify({ type: "close", reason }));
      }
      socket.close();
    }
  };
}

function toWebSocketUrl(baseUrl: string, webSocketUrl: string) {
  const url = new URL(webSocketUrl, baseUrl);
  if (url.protocol === "https:") {
    url.protocol = "wss:";
  } else if (url.protocol === "http:") {
    url.protocol = "ws:";
  }
  return url.toString();
}

function parseServerMessage(raw: string): TerminalClientEvent | null {
  try {
    const value = JSON.parse(raw) as Record<string, unknown>;
    if (value.type === "output") {
      return { type: "output", data: typeof value.data === "string" ? value.data : "", seq: number(value.seq) };
    }
    if (value.type === "exit") {
      return { type: "exit", code: number(value.code) ?? 0, seq: number(value.seq) };
    }
    if (value.type === "error") {
      return {
        type: "error",
        code: typeof value.code === "string" ? value.code : "PTY_ERROR",
        message: typeof value.message === "string" ? value.message : "terminal error"
      };
    }
  } catch {
    return null;
  }
  return null;
}

function number(value: unknown) {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

function defaultWebSocketCtor(): TerminalWebSocketConstructor {
  if (typeof WebSocket === "undefined") {
    throw new Error("当前环境不支持 WebSocket");
  }
  return WebSocket as unknown as TerminalWebSocketConstructor;
}
