<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import { useSettingsStore } from "@/stores/settings";
import { useWorkspaceStore } from "@/stores/workspace";

const open = ref(false);
const settings = useSettingsStore();
const workspace = useWorkspaceStore();

function toggleFromClick(event: Event) {
  if ((event.target as HTMLElement).closest("[data-settings-trigger]")) {
    open.value = !open.value;
  }
}

onMounted(() => document.addEventListener("click", toggleFromClick));
onUnmounted(() => document.removeEventListener("click", toggleFromClick));
</script>

<template>
  <div v-if="open" class="modal-backdrop" @click.self="open = false">
    <section class="settings-dialog" role="dialog" aria-modal="true" aria-label="Settings">
      <header>
        <div>
          <p class="eyebrow">Settings</p>
          <h2>opencode workspace</h2>
        </div>
        <button class="icon-text" type="button" @click="open = false">Close</button>
      </header>
      <div class="settings-grid">
        <label>
          <span>Theme</span>
          <select v-model="settings.theme">
            <option value="dark">Dark</option>
            <option value="light">Light</option>
            <option value="system">System</option>
          </select>
        </label>
        <label>
          <span>Density</span>
          <select v-model="settings.density">
            <option value="compact">Compact</option>
            <option value="comfortable">Comfortable</option>
          </select>
        </label>
        <label>
          <span>Keymap</span>
          <select v-model="settings.keymap">
            <option value="default">Default</option>
            <option value="vim">Vim</option>
          </select>
        </label>
        <label class="check-row">
          <input v-model="settings.sound" type="checkbox" />
          <span>Enable run sounds</span>
        </label>
      </div>
      <div class="settings-list">
        <div class="section-label">Providers</div>
        <div v-for="provider in workspace.providers" :key="provider.providerId" class="catalog-row">
          <span>{{ provider.name }}</span>
          <small>{{ provider.status ?? "available" }}</small>
        </div>
        <div v-if="!workspace.providers.length" class="empty-note">Provider auth is proxied through the backend API.</div>
      </div>
    </section>
  </div>
</template>
