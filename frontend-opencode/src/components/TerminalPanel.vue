<script setup lang="ts">
import { computed } from "vue";
import { Maximize2, RotateCw, SendHorizontal, Trash2, X } from "lucide-vue-next";
import { useTerminalStore } from "@/stores/terminal";

const emit = defineEmits<{ reconnect: [] }>();
const terminal = useTerminalStore();
const transcript = computed(() => terminal.output.join(""));
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

    <pre class="terminal-screen" aria-label="Terminal output"><template v-if="transcript">{{ transcript }}</template><span v-else-if="terminal.status === 'opening'">opening ticket...</span><span v-else-if="terminal.status === 'connecting'">connecting terminal...</span><span v-else-if="terminal.ticket">connected through {{ terminal.ticket.webSocketUrl }}</span><span v-else>ticket required</span></pre>

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
