<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import { Search } from "lucide-vue-next";
import { useWorkspaceStore } from "@/stores/workspace";

const open = ref(false);
const workspace = useWorkspaceStore();

function toggleFromClick(event: Event) {
  if ((event.target as HTMLElement).closest("[data-command-trigger]")) {
    open.value = !open.value;
  }
}

function handleKeydown(event: KeyboardEvent) {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
    event.preventDefault();
    open.value = !open.value;
  }
  if (event.key === "Escape") {
    open.value = false;
  }
}

onMounted(() => {
  document.addEventListener("click", toggleFromClick);
  document.addEventListener("keydown", handleKeydown);
  void workspace.loadCommands();
});
onUnmounted(() => {
  document.removeEventListener("click", toggleFromClick);
  document.removeEventListener("keydown", handleKeydown);
});
</script>

<template>
  <div v-if="open" class="modal-backdrop" @click.self="open = false">
    <section class="command-palette" role="dialog" aria-modal="true" aria-label="Command palette">
      <label class="command-search">
        <Search :size="16" />
        <input autofocus placeholder="Run command or jump to a session" />
      </label>
      <button v-for="command in workspace.commands" :key="command.commandId" class="command-row" type="button">
        <span>/{{ command.name }}</span>
        <small>{{ command.description ?? command.arguments ?? "opencode command" }}</small>
      </button>
      <div v-if="!workspace.commands.length" class="empty-note">Commands load through the platform backend.</div>
    </section>
  </div>
</template>
