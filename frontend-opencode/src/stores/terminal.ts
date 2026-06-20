import { defineStore } from "pinia";
import { ref } from "vue";
import type { TerminalTicketResponse } from "@test-agent/shared-types";
import { usePlatformStore } from "@/stores/platform";

export const useTerminalStore = defineStore("terminal", () => {
  const ticket = ref<TerminalTicketResponse>();
  const status = ref<"idle" | "opening" | "connecting" | "ready" | "closed" | "error">("idle");
  const output = ref<string[]>([]);
  const input = ref("");
  const error = ref<string>();
  let socket: WebSocket | undefined;

  async function open(sessionId: string, payload: { cwd?: string; cols?: number; rows?: number } = {}) {
    const platform = usePlatformStore();
    closeSocket();
    status.value = "opening";
    error.value = undefined;
    output.value = [];
    try {
      ticket.value = await platform.api.createTerminalTicket(sessionId, payload);
      connect(ticket.value.webSocketUrl);
    } catch (cause) {
      status.value = "error";
      error.value = cause instanceof Error ? cause.message : "终端票据创建失败";
    }
  }

  // 终端连接只使用平台后端签发的 ticket URL，避免浏览器直连 opencode server。
  function connect(webSocketUrl: string) {
    status.value = "connecting";
    socket = new WebSocket(webSocketUrl);
    socket.onopen = () => {
      status.value = "ready";
      error.value = undefined;
    };
    socket.onmessage = (event) => appendOutput(event.data);
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
    if (output.value.length > 500) {
      output.value = output.value.slice(-500);
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
    if (!socket || status.value !== "ready") {
      error.value = "终端尚未连接";
      return;
    }
    socket.send(data);
  }

  function close() {
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

  return { ticket, status, output, input, error, open, sendInput, send, close };
});
