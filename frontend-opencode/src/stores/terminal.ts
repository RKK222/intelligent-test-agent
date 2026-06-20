import { defineStore } from "pinia";
import { ref } from "vue";
import type { TerminalTicketResponse } from "@test-agent/shared-types";
import { usePlatformStore } from "@/stores/platform";

export const useTerminalStore = defineStore("terminal", () => {
  const ticket = ref<TerminalTicketResponse>();
  const status = ref<"idle" | "opening" | "connecting" | "ready" | "closed" | "error">("idle");
  const output = ref<string[]>([]);
  const warnings = ref<Array<{ code: string; message: string }>>([]);
  const input = ref("");
  const cols = ref(100);
  const rows = ref(30);
  const error = ref<string>();
  let socket: WebSocket | undefined;

  async function open(sessionId: string, payload: { cwd?: string; cols?: number; rows?: number } = {}) {
    const platform = usePlatformStore();
    closeSocket();
    status.value = "opening";
    error.value = undefined;
    warnings.value = [];
    cols.value = positiveInteger(payload.cols, cols.value);
    rows.value = positiveInteger(payload.rows, rows.value);
    output.value = [];
    try {
      ticket.value = await platform.api.createTerminalTicket(sessionId, payload);
      connect(ticket.value.webSocketUrl, platform.baseUrl);
    } catch (cause) {
      status.value = "error";
      error.value = cause instanceof Error ? cause.message : "终端票据创建失败";
    }
  }

  // 终端连接只使用平台后端签发的 ticket URL，避免浏览器直连 opencode server。
  function connect(webSocketUrl: string, baseUrl: string) {
    status.value = "connecting";
    socket = new WebSocket(toWebSocketUrl(baseUrl, webSocketUrl));
    socket.onopen = () => {
      status.value = "ready";
      error.value = undefined;
    };
    socket.onmessage = (event) => handleSocketMessage(event.data);
    socket.onerror = () => {
      status.value = "error";
      error.value = "终端连接失败";
    };
    socket.onclose = () => {
      socket = undefined;
      if (status.value !== "error") {
        status.value = "closed";
      }
    };
  }

  function appendOutput(data: unknown) {
    if (typeof data === "string") {
      output.value.push(data);
    } else {
      output.value.push(String(data ?? ""));
    }
    // 与后端 PTY output buffer 上限配合，前端只保留最近片段，避免长日志撑爆 DOM。
    if (output.value.length > 500) {
      output.value = output.value.slice(-500);
    }
  }

  function handleSocketMessage(raw: unknown) {
    if (typeof raw !== "string") {
      appendOutput(raw);
      return;
    }
    const message = parseTerminalEnvelope(raw);
    if (!message) {
      appendOutput(raw);
      return;
    }
    if (message.type === "output") {
      appendOutput(message.data);
      return;
    }
    if (message.type === "warning") {
      warnings.value.push({ code: message.code, message: message.message });
      return;
    }
    if (message.type === "error") {
      status.value = "error";
      error.value = message.message;
      return;
    }
    if (message.type === "exit") {
      status.value = "closed";
    }
  }

  function sendInput() {
    const command = input.value;
    if (!command) {
      return;
    }
    send(`${command}\n`);
    input.value = "";
  }

  function send(data: string) {
    sendEnvelope({ type: "input", data });
  }

  function resize(nextCols = cols.value, nextRows = rows.value) {
    cols.value = positiveInteger(nextCols, cols.value);
    rows.value = positiveInteger(nextRows, rows.value);
    sendEnvelope({ type: "resize", cols: cols.value, rows: rows.value });
  }

  function sendEnvelope(payload: Record<string, unknown>) {
    if (!socket || status.value !== "ready") {
      error.value = "终端尚未连接";
      return;
    }
    socket.send(JSON.stringify(payload));
  }

  function clear() {
    output.value = [];
    warnings.value = [];
  }

  function close() {
    if (socket && status.value === "ready" && socket.readyState === 1) {
      socket.send(JSON.stringify({ type: "close", reason: "user" }));
    }
    closeSocket();
    status.value = "closed";
  }

  function closeSocket() {
    if (!socket) {
      return;
    }
    const current = socket;
    socket = undefined;
    current.onopen = null;
    current.onmessage = null;
    current.onerror = null;
    current.onclose = null;
    current.close();
  }

  return { ticket, status, output, warnings, input, cols, rows, error, open, sendInput, send, resize, clear, close };
});

type TerminalEnvelope =
  | { type: "output"; data: string }
  | { type: "warning"; code: string; message: string }
  | { type: "error"; code: string; message: string }
  | { type: "exit"; code: number };

function parseTerminalEnvelope(raw: string): TerminalEnvelope | undefined {
  try {
    const value = JSON.parse(raw) as Record<string, unknown>;
    if (value.type === "output") {
      return { type: "output", data: typeof value.data === "string" ? value.data : "" };
    }
    if (value.type === "warning") {
      return {
        type: "warning",
        code: typeof value.code === "string" ? value.code : "PTY_WARNING",
        message: typeof value.message === "string" ? value.message : "terminal warning"
      };
    }
    if (value.type === "error") {
      return {
        type: "error",
        code: typeof value.code === "string" ? value.code : "PTY_ERROR",
        message: typeof value.message === "string" ? value.message : "terminal error"
      };
    }
    if (value.type === "exit") {
      return { type: "exit", code: typeof value.code === "number" ? value.code : 0 };
    }
  } catch {
    return undefined;
  }
  return undefined;
}

function toWebSocketUrl(baseUrl: string, webSocketUrl: string) {
  const origin = baseUrl || globalThis.location?.origin || "http://127.0.0.1";
  const url = new URL(webSocketUrl, origin);
  if (url.protocol === "http:") {
    url.protocol = "ws:";
  } else if (url.protocol === "https:") {
    url.protocol = "wss:";
  }
  return url.toString();
}

function positiveInteger(value: unknown, fallback: number) {
  return typeof value === "number" && Number.isFinite(value) && value > 0 ? Math.round(value) : fallback;
}
