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
let resizeFrame: number | null = null;
let lastObservedWidth = -1;
let lastObservedHeight = -1;

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
  }
  terminal.onData(data => sessionRef.value?.sendInput(data));
  terminal.onResize(size => sessionRef.value?.resize(size.cols, size.rows));
  xtermRef.value = terminal;
  fitAddonRef.value = fitAddon;
  if (typeof ResizeObserver !== "undefined" && host.value) {
    // 只响应终端宿主的真实尺寸变化，并延迟到下一帧执行 fit，避免 xterm 自身布局
    // 再次触发 ResizeObserver 后形成“fit -> resize -> fit”的高度反馈循环。
    resizeObserver = new ResizeObserver((entries) => {
      const rect = entries[0]?.contentRect;
      scheduleFit(rect?.width, rect?.height);
    });
    resizeObserver.observe(host.value);
  }
  void nextTick(() => scheduleFit(undefined, undefined, true));
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  if (resizeFrame !== null) {
    cancelAnimationFrame(resizeFrame);
  }
  sessionRef.value?.close("unmount");
  xtermRef.value?.dispose();
});

/**
 * 合并同一帧内的尺寸通知，并忽略数值未变化的回调。
 * 宿主尺寸由外层布局决定，xterm 采用绝对定位，不能反向撑高宿主。
 */
function scheduleFit(width = host.value?.clientWidth, height = host.value?.clientHeight, force = false) {
  const nextWidth = Math.round(width ?? 0);
  const nextHeight = Math.round(height ?? 0);
  if (nextWidth <= 0 || nextHeight <= 0) return;
  if (!force && nextWidth === lastObservedWidth && nextHeight === lastObservedHeight) return;
  lastObservedWidth = nextWidth;
  lastObservedHeight = nextHeight;
  if (resizeFrame !== null) {
    cancelAnimationFrame(resizeFrame);
  }
  resizeFrame = requestAnimationFrame(() => {
    resizeFrame = null;
    fitAddonRef.value?.fit();
  });
}

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
        if (event.type === "open") {
          // WebSocket 建立后再同步一次当前网格尺寸，并把键盘焦点交给 xterm。
          // 连接前的 fit 发生在 session 尚不存在时，其 resize 事件不会发送给后端。
          nextSession.resize(terminal?.cols ?? 120, terminal?.rows ?? 32);
          terminal?.focus();
        }
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
    <div class="ta-terminal-viewport">
      <div ref="host" class="ta-terminal-host" />
    </div>
  </div>
</template>

<style scoped>
.ta-terminal-panel { display: flex; min-width: 0; min-height: 240px; height: 100%; max-height: 100%; flex-direction: column; overflow: hidden; background: #05070b; border: 1px solid #1e293b; border-radius: 8px; }
.ta-terminal-panel.is-danger { border-color: rgba(239, 68, 68, .65); box-shadow: 0 0 0 1px rgba(239, 68, 68, .12); }
.ta-terminal-toolbar { display: flex; min-height: 42px; align-items: center; gap: 8px; border-bottom: 1px solid #1e293b; background: #020617; padding: 0 12px; }
.ta-terminal-notice, .ta-terminal-error { padding: 8px 12px; font-size: 12px; border-bottom: 1px solid #1e293b; }
.ta-terminal-notice { color: #94a3b8; }
.ta-terminal-error { color: #fecaca; background: rgba(127, 29, 29, .35); }
.ta-terminal-viewport { position: relative; min-width: 0; min-height: 0; flex: 1 1 0; overflow: hidden; }
.ta-terminal-host { position: absolute; inset: 8px; min-width: 0; min-height: 0; overflow: hidden; }
.ta-terminal-host :deep(.xterm) { width: 100%; height: 100%; }
</style>
