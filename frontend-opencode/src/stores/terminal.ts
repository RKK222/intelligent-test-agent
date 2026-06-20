import { defineStore } from "pinia";
import { ref } from "vue";
import type { TerminalTicketResponse } from "@test-agent/shared-types";
import { usePlatformStore } from "@/stores/platform";

export const useTerminalStore = defineStore("terminal", () => {
  const ticket = ref<TerminalTicketResponse>();
  const status = ref<"idle" | "opening" | "ready" | "error">("idle");
  const error = ref<string>();

  async function open(sessionId: string, payload: { cwd?: string; cols?: number; rows?: number } = {}) {
    const platform = usePlatformStore();
    status.value = "opening";
    error.value = undefined;
    try {
      ticket.value = await platform.api.createTerminalTicket(sessionId, payload);
      status.value = "ready";
    } catch (cause) {
      status.value = "error";
      error.value = cause instanceof Error ? cause.message : "终端票据创建失败";
    }
  }

  return { ticket, status, error, open };
});
