<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { Maximize2, RotateCw, SendHorizontal, Trash2, X } from "lucide-vue-next";
import type { FitAddon } from "@xterm/addon-fit";
import type { Terminal as XTerm } from "@xterm/xterm";
import { useTerminalStore } from "@/stores/terminal";

const emit = defineEmits<{ reconnect: [] }>();
const terminal = useTerminalStore();
const transcript = computed(() => terminal.output.join(""));
const terminalHost = ref<HTMLDivElement>();
let xterm: XTerm | undefined;
let fitAddon: FitAddon | undefined;
let dataDisposable: { dispose: () => void } | undefined;
let renderedChunks = 0;
let xtermModules:
  | Promise<{
      Terminal: typeof import("@xterm/xterm").Terminal;
      FitAddon: typeof import("@xterm/addon-fit").FitAddon;
    }>
  | undefined;

onMounted(() => {
  void nextTick(() => {
    void mountXterm().then(syncXtermOutput);
  });
  window.addEventListener("resize", fitXterm);
});

onUnmounted(() => {
  window.removeEventListener("resize", fitXterm);
  dataDisposable?.dispose();
  xterm?.dispose();
  dataDisposable = undefined;
  fitAddon = undefined;
  xterm = undefined;
});

watch(
  () => terminal.output.length,
  () => syncXtermOutput(),
  { flush: "post" }
);

watch(
  () => [terminal.cols, terminal.rows] as const,
  ([cols, rows]) => {
    xterm?.resize(cols, rows);
    fitXterm();
  },
  { flush: "post" }
);

async function mountXterm() {
  if (xterm || !terminalHost.value) {
    return;
  }
  const modules = await loadXtermModules();
  if (xterm || !terminalHost.value) {
    return;
  }
  xterm = new modules.Terminal({
    cols: terminal.cols,
    rows: terminal.rows,
    convertEol: true,
    cursorBlink: true,
    scrollback: 1000,
    fontFamily: "var(--font-mono)",
    fontSize: 12,
    lineHeight: 1.35,
    theme: {
      background: "#191515",
      foreground: "#d7d1ca",
      cursor: "#f4c15d",
      selectionBackground: "#5b5148"
    }
  });
  fitAddon = new modules.FitAddon();
  xterm.loadAddon(fitAddon);
  xterm.open(terminalHost.value);
  // xterm 捕获原始按键数据，仍通过 terminal store 发送平台 JSON envelope。
  dataDisposable = xterm.onData((data) => terminal.send(data));
  fitXterm();
}

function loadXtermModules() {
  xtermModules ??= Promise.all([import("@xterm/xterm"), import("@xterm/addon-fit"), import("@xterm/xterm/css/xterm.css")]).then(
    ([terminalModule, fitModule]) => ({
      Terminal: terminalModule.Terminal,
      FitAddon: fitModule.FitAddon
    })
  );
  return xtermModules;
}

function syncXtermOutput() {
  if (!xterm) {
    return;
  }
  if (terminal.output.length < renderedChunks) {
    xterm.clear();
    renderedChunks = 0;
  }
  terminal.output.slice(renderedChunks).forEach((chunk) => xterm?.write(chunk));
  renderedChunks = terminal.output.length;
}

function fitXterm() {
  try {
    fitAddon?.fit();
  } catch {
    // jsdom 与折叠面板没有可测量尺寸时保持 store 指定的 rows/cols。
  }
}
</script>

<template>
  <div class="terminal-panel">
    <div class="terminal-header">
      <span class="terminal-status" :data-state="terminal.status">{{ terminal.status }}</span>
      <div class="terminal-size-controls" aria-label="Terminal size">
        <label>
          <span>Cols</span>
          <input v-model.number="terminal.cols" aria-label="Terminal columns" type="number" min="20" max="240" />
        </label>
        <label>
          <span>Rows</span>
          <input v-model.number="terminal.rows" aria-label="Terminal rows" type="number" min="5" max="120" />
        </label>
        <button class="icon-text" type="button" aria-label="Resize terminal" @click="terminal.resize(terminal.cols, terminal.rows)">
          <Maximize2 :size="14" />Resize
        </button>
      </div>
      <div class="terminal-actions">
        <button class="icon-button" type="button" aria-label="Clear terminal" @click="terminal.clear">
          <Trash2 :size="14" />
        </button>
        <button class="icon-button" type="button" aria-label="Reconnect terminal" @click="emit('reconnect')">
          <RotateCw :size="14" />
        </button>
        <button class="icon-button" type="button" aria-label="Close terminal" @click="terminal.close">
          <X :size="14" />
        </button>
      </div>
    </div>

    <div class="terminal-screen terminal-xterm-shell" aria-label="Terminal output">
      <div ref="terminalHost" class="terminal-xterm" />
      <div v-if="!transcript" class="terminal-placeholder">
        <span v-if="terminal.status === 'opening'">opening ticket...</span>
        <span v-else-if="terminal.status === 'connecting'">connecting terminal...</span>
        <span v-else-if="terminal.ticket">connected through {{ terminal.ticket.webSocketUrl }}</span>
        <span v-else>ticket required</span>
      </div>
      <pre class="terminal-sr-output">{{ transcript }}</pre>
    </div>

    <div v-if="terminal.warnings.length" class="terminal-warnings">
      <div v-for="warning in terminal.warnings" :key="`${warning.code}:${warning.message}`" class="inline-alert">
        {{ warning.code }}: {{ warning.message }}
      </div>
    </div>

    <form class="terminal-input-row" @submit.prevent="terminal.sendInput">
      <span class="terminal-prompt">$</span>
      <input v-model="terminal.input" aria-label="Terminal input" autocomplete="off" spellcheck="false" />
      <button class="primary-action" type="submit"><SendHorizontal :size="14" />Send</button>
    </form>

    <div v-if="terminal.error" class="inline-alert">{{ terminal.error }}</div>
  </div>
</template>
