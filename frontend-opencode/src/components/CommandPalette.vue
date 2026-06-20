<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { Search } from "lucide-vue-next";
import type { CommandInfo } from "@test-agent/shared-types";
import { usePromptStore } from "@/stores/prompt";
import { useWorkspaceStore } from "@/stores/workspace";

const open = ref(false);
const query = ref("");
const activeIndex = ref(0);
const searchInput = ref<HTMLInputElement>();
const workspace = useWorkspaceStore();
const prompt = usePromptStore();
const filteredCommands = computed(() => {
  const needle = query.value.trim().toLowerCase();
  if (!needle) {
    return workspace.commands;
  }
  return workspace.commands.filter((command) =>
    `${command.name} ${command.description ?? ""} ${command.arguments ?? ""}`.toLowerCase().includes(needle)
  );
});
const activeCommand = computed(() => filteredCommands.value[activeIndex.value]);

watch(query, () => {
  activeIndex.value = 0;
});

watch(
  filteredCommands,
  (commands) => {
    if (!commands.length) {
      activeIndex.value = 0;
      return;
    }
    if (activeIndex.value >= commands.length) {
      activeIndex.value = commands.length - 1;
    }
  },
  { immediate: true }
);

function toggleFromClick(event: Event) {
  if ((event.target as HTMLElement).closest("[data-command-trigger]")) {
    void togglePalette();
  }
}

function handleKeydown(event: KeyboardEvent) {
  const key = event.key.toLowerCase();
  const paletteKey = (event.metaKey || event.ctrlKey) && ((event.shiftKey && key === "p") || key === "k");
  if (paletteKey) {
    event.preventDefault();
    void openPalette();
    return;
  }
  if (open.value && event.key === "Escape") {
    event.preventDefault();
    closePalette();
  }
}

async function togglePalette() {
  if (open.value) {
    closePalette();
    return;
  }
  await openPalette();
}

async function openPalette() {
  open.value = true;
  await workspace.loadCommands();
  await nextTick();
  searchInput.value?.focus();
}

function closePalette() {
  open.value = false;
  query.value = "";
  activeIndex.value = 0;
}

function moveActive(delta: number) {
  const size = filteredCommands.value.length;
  if (!size) {
    activeIndex.value = 0;
    return;
  }
  activeIndex.value = (activeIndex.value + delta + size) % size;
}

function handleSearchKeydown(event: KeyboardEvent) {
  if (event.key === "Escape") {
    event.preventDefault();
    closePalette();
    return;
  }
  if (event.key === "ArrowDown" || event.key === "ArrowUp") {
    event.preventDefault();
    moveActive(event.key === "ArrowDown" ? 1 : -1);
    return;
  }
  if (event.key === "Enter") {
    event.preventDefault();
    if (activeCommand.value) {
      selectCommand(activeCommand.value);
    }
  }
}

function selectCommand(command: CommandInfo) {
  // 原 App 的命令面板会把可执行命令统一汇入 command catalog；Vue 版将平台命令写回 composer 的 slash 入口。
  prompt.insertSlashCommand(command);
  closePalette();
}

function commandLabel(command: CommandInfo) {
  return `/${command.name} ${command.description ?? command.arguments ?? "opencode command"}`;
}

onMounted(() => {
  document.addEventListener("click", toggleFromClick);
  document.addEventListener("keydown", handleKeydown);
});
onUnmounted(() => {
  document.removeEventListener("click", toggleFromClick);
  document.removeEventListener("keydown", handleKeydown);
});
</script>

<template>
  <div v-if="open" class="modal-backdrop" @click.self="closePalette">
    <section class="command-palette" role="dialog" aria-modal="true" aria-label="Command palette">
      <label class="command-search">
        <Search :size="16" />
        <input
          ref="searchInput"
          v-model="query"
          aria-label="Search commands"
          autocomplete="off"
          placeholder="Run command or jump to a session"
          @keydown="handleSearchKeydown"
        />
      </label>
      <div v-if="filteredCommands.length" class="command-list" role="listbox" aria-label="Available commands">
        <button
          v-for="(command, index) in filteredCommands"
          :key="command.commandId"
          class="command-row"
          :class="{ active: index === activeIndex }"
          type="button"
          role="option"
          :aria-selected="index === activeIndex"
          :aria-label="commandLabel(command)"
          @mouseenter="activeIndex = index"
          @click="selectCommand(command)"
        >
          <span>/{{ command.name }}</span>
          <kbd v-if="command.arguments" aria-hidden="true">{{ command.arguments }}</kbd>
          <small>{{ command.description ?? command.arguments ?? "opencode command" }}</small>
        </button>
      </div>
      <div v-else class="empty-note">No commands match this query.</div>
    </section>
  </div>
</template>
