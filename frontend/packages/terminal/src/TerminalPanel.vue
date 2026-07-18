<script lang="ts">
import type { TerminalTicketResponse } from "@test-agent/shared-types";
import type { TerminalSnapshot, TerminalSession, TerminalWebSocketConstructor } from "./terminal-client";

export type TerminalPanelProps = {
  baseUrl: string;
  createTicket: () => Promise<TerminalTicketResponse>;
  disabled?: boolean;
  disabledReason?: string;
  title?: string;
  connectLabel?: string;
  danger?: boolean;
  WebSocketCtor?: TerminalWebSocketConstructor;
};
</script>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef } from "vue";
import { Terminal as XTerm } from "@xterm/xterm";
import { FitAddon } from "@xterm/addon-fit";
import "@xterm/xterm/css/xterm.css";
import { Square, Terminal as TerminalIcon } from "lucide-vue-next";
import { Badge, Button } from "@test-agent/ui-kit";
import { createTerminalSession } from "./terminal-client";

const props = defineProps<TerminalPanelProps>();
const initialSnapshot: TerminalSnapshot = { status: "idle", output: "" };
const snapshot = ref<TerminalSnapshot>(initialSnapshot);
const host = ref<HTMLElement | null>(null);
const sessionRef = shallowRef<TerminalSession | null>(null);
const xtermRef = shallowRef<XTerm | null>(null);
const fitAddonRef = shallowRef<FitAddon | null>(null);
let resizeObserver: ResizeObserver | null = null;

const connecting = computed(() => snapshot.value.status === "connecting");
const open = computed(() => snapshot.value.status === "open");

onMounted(() => {
  const terminal = new XTerm({
    cursorBlink: true,
    convertEol: false,
    fontFamily: "JetBrains Mono, SFMono-Regular, Consolas, monospace",
    fontSize: 13,
    scrollback: 5000,
    theme: { background: "#05070b", foreground: "#d8dee9", cursor: "#34d399", selectionBackground: "#334155" }
  });
  const fitAddon = new FitAddon();
  terminal.loadAddon(fitAddon);
  if (host.value) {
    terminal.open(host.value);
    fitAddon.fit();
  }
  terminal.onData(data => sessionRef.value?.sendInput(data));
  terminal.onResize(size => sessionRef.value?.resize(size.cols, size.rows));
  xtermRef.value = terminal;
  fitAddonRef.value = fitAddon;
  if (typeof ResizeObserver !== "undefined" && host.value) {
    resizeObserver = new ResizeObserver(() => fitAddon.fit());
    resizeObserver.observe(host.value);
  }
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  sessionRef.value?.close("unmount");
  xtermRef.value?.dispose();
});

async function connect() {
  if (props.disabled || connecting.value || open.value) return;
  sessionRef.value?.close("reconnect");
  snapshot.value = { status: "connecting", output: "" };
  xtermRef.value?.clear();
  try {
    await nextTick();
    fitAddonRef.value?.fit();
    const terminal = xtermRef.value;
    const ticket = await props.createTicket();
    const nextSession = createTerminalSession({
      baseUrl: props.baseUrl,
      ticket,
      WebSocketCtor: props.WebSocketCtor,
      onEvent: event => {
        if (event.type === "output") terminal?.write(event.data);
        if (event.type === "exit") terminal?.writeln(`\r\n[进程已退出: ${event.code}]`);
        if (event.type === "warning") terminal?.writeln(`\r\n[警告 ${event.code}] ${event.message}`);
        if (event.type === "error") terminal?.writeln(`\r\n[错误 ${event.code}] ${event.message}`);
        snapshot.value = nextSession.snapshot();
      }
    });
    sessionRef.value = nextSession;
    snapshot.value = nextSession.snapshot();
  } catch (error) {
    snapshot.value = {
      status: "error",
      output: "",
      error: { code: "PTY_TICKET_FAILED", message: error instanceof Error ? error.message : "terminal ticket failed" }
    };
  }
}

function close() {
  sessionRef.value?.close("user");
  snapshot.value = sessionRef.value?.snapshot() ?? { ...initialSnapshot, status: "closed" };
}
</script>

<template>
  <div :class="['ta-terminal-panel', { 'is-danger': danger }]">
    <div class="ta-terminal-toolbar">
      <TerminalIcon class="h-4 w-4 text-emerald-300" />
      <div class="min-w-0 flex-1 text-[12px] font-semibold text-slate-200">{{ title ?? "终端" }}</div>
      <Badge :tone="snapshot.status === 'error' ? 'danger' : open ? 'success' : connecting ? 'info' : 'neutral'">
        {{ snapshot.status }}
      </Badge>
      <Button size="sm" variant="secondary" :disabled="disabled || connecting || open" @click="connect">
        {{ connectLabel ?? "连接终端" }}
      </Button>
      <Button size="sm" variant="secondary" :disabled="!open" @click="close">
        <Square class="h-3.5 w-3.5" />关闭
      </Button>
    </div>
    <div v-if="disabled" class="ta-terminal-notice">{{ disabledReason ?? "终端当前不可用" }}</div>
    <div v-if="snapshot.error" class="ta-terminal-error">{{ snapshot.error.code }}: {{ snapshot.error.message }}</div>
    <div ref="host" class="ta-terminal-host" />
  </div>
</template>

<style scoped>
.ta-terminal-panel { display: flex; min-height: 320px; height: 100%; flex-direction: column; overflow: hidden; background: #05070b; border: 1px solid #1e293b; border-radius: 8px; }
.ta-terminal-panel.is-danger { border-color: rgba(239, 68, 68, .65); box-shadow: 0 0 0 1px rgba(239, 68, 68, .12); }
.ta-terminal-toolbar { display: flex; min-height: 42px; align-items: center; gap: 8px; border-bottom: 1px solid #1e293b; background: #020617; padding: 0 12px; }
.ta-terminal-notice, .ta-terminal-error { padding: 8px 12px; font-size: 12px; border-bottom: 1px solid #1e293b; }
.ta-terminal-notice { color: #94a3b8; }
.ta-terminal-error { color: #fecaca; background: rgba(127, 29, 29, .35); }
.ta-terminal-host { min-height: 0; flex: 1; padding: 8px; }
.ta-terminal-host :deep(.xterm) { height: 100%; }
</style>
