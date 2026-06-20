<script setup lang="ts">
import { onMounted, watch } from "vue";
import { RouterView, useRouter } from "vue-router";
import { Command, FilePlus2, Search, Settings } from "lucide-vue-next";
import CommandPalette from "@/components/CommandPalette.vue";
import SettingsDialog from "@/components/SettingsDialog.vue";
import ToastHost from "@/components/ToastHost.vue";
import { useSettingsStore } from "@/stores/settings";
import { useWorkspaceStore } from "@/stores/workspace";

const router = useRouter();
const settings = useSettingsStore();
const workspace = useWorkspaceStore();

onMounted(() => {
  void workspace.loadHome();
});

watch(
  () => settings.effectiveTheme,
  (theme) => {
    document.documentElement.dataset.theme = theme;
  },
  { immediate: true }
);
</script>

<template>
  <div class="app-shell" :data-density="settings.density">
    <header class="topbar" role="banner">
      <button class="brand" type="button" aria-label="Open home" @click="router.push('/')">
        <span class="brand-mark">oc</span>
        <span class="brand-word">opencode</span>
      </button>
      <label class="global-search">
        <Search :size="15" aria-hidden="true" />
        <input v-model="workspace.query" type="search" placeholder="Search sessions, files, commands" @keydown.enter="workspace.loadHome()" />
      </label>
      <nav class="topbar-actions" aria-label="Global actions">
        <button class="icon-button" type="button" aria-label="Command palette" data-command-trigger>
          <Command :size="16" aria-hidden="true" />
        </button>
        <button class="primary-action" type="button" @click="router.push('/new-session')">
          <FilePlus2 :size="16" aria-hidden="true" />
          <span>New session</span>
        </button>
        <button class="icon-button" type="button" aria-label="Settings" data-settings-trigger>
          <Settings :size="16" aria-hidden="true" />
        </button>
      </nav>
    </header>
    <RouterView />
    <CommandPalette />
    <SettingsDialog />
    <ToastHost />
  </div>
</template>
