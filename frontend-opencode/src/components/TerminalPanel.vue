<script setup lang="ts">
import { computed } from "vue";
import { RotateCw, SendHorizontal, X } from "lucide-vue-next";
import { useTerminalStore } from "@/stores/terminal";

const emit = defineEmits<{ reconnect: [] }>();
const terminal = useTerminalStore();
const transcript = computed(() => terminal.output.join(""));
</script>

<template>
  <div class="terminal-panel">
    <div class="terminal-header">
      <span class="terminal-status" :data-state="terminal.status">{{ terminal.status }}</span>
      <div class="terminal-actions">
        <button class="icon-button" type="button" aria-label="Reconnect terminal" @click="emit('reconnect')">
          <RotateCw :size="14" />
        </button>
        <button class="icon-button" type="button" aria-label="Close terminal" @click="terminal.close">
          <X :size="14" />
        </button>
      </div>
    </div>

    <pre class="terminal-screen" aria-label="Terminal output"><template v-if="transcript">{{ transcript }}</template><span v-else-if="terminal.status === 'opening'">opening ticket...</span><span v-else-if="terminal.status === 'connecting'">connecting terminal...</span><span v-else-if="terminal.ticket">connected through {{ terminal.ticket.webSocketUrl }}</span><span v-else>ticket required</span></pre>

    <form class="terminal-input-row" @submit.prevent="terminal.sendInput">
      <span class="terminal-prompt">$</span>
      <input v-model="terminal.input" aria-label="Terminal input" autocomplete="off" spellcheck="false" />
      <button class="primary-action" type="submit"><SendHorizontal :size="14" />Send</button>
    </form>

    <div v-if="terminal.error" class="inline-alert">{{ terminal.error }}</div>
  </div>
</template>
