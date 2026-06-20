<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import type { ProviderInfo } from "@test-agent/shared-types";
import SettingsMcpPanel from "@/components/SettingsMcpPanel.vue";
import SettingsWorktreePanel from "@/components/SettingsWorktreePanel.vue";
import { usePlatformStore } from "@/stores/platform";
import { useSettingsStore } from "@/stores/settings";
import { useWorkspaceStore } from "@/stores/workspace";

type ProviderAuthState = {
  providerId: string;
  status: string;
  account?: string;
};

const open = ref(false);
const providerAuth = ref<Record<string, ProviderAuthState>>({});
const providerKeys = ref<Record<string, string>>({});
const providerActions = ref<Record<string, string | undefined>>({});
const providerError = ref<string>();
const settings = useSettingsStore();
const platform = usePlatformStore();
const workspace = useWorkspaceStore();

function toggleFromClick(event: Event) {
  if ((event.target as HTMLElement).closest("[data-settings-trigger]")) {
    open.value = !open.value;
    if (open.value) {
      void loadProviderAuth();
    }
  }
}

async function loadProviderAuth() {
  providerError.value = undefined;
  try {
    providerAuth.value = normalizeProviderAuth(await platform.api.listProviderAuth(workspace.selectedWorkspaceId));
  } catch (cause) {
    providerError.value = cause instanceof Error ? cause.message : "Provider auth 加载失败";
  }
}

function authLabel(providerId: string) {
  const normalized = providerAuth.value[providerId]?.status.toLowerCase();
  if (!normalized || ["missing", "none", "not_connected", "unauthenticated", "available"].includes(normalized)) {
    return "Not connected";
  }
  if (["connected", "authenticated", "ready", "ok"].includes(normalized)) {
    return "Connected";
  }
  return providerAuth.value[providerId]?.status ?? "Unknown";
}

function authState(providerId: string) {
  return authLabel(providerId) === "Connected" ? "connected" : "missing";
}

async function saveProviderKey(provider: ProviderInfo) {
  const key = providerKeys.value[provider.providerId]?.trim();
  if (!key) {
    return;
  }
  providerActions.value[provider.providerId] = "saving";
  providerError.value = undefined;
  try {
    await platform.api.setProviderAuth(provider.providerId, { type: "api-key", key });
    providerKeys.value[provider.providerId] = "";
    await loadProviderAuth();
  } catch (cause) {
    providerError.value = cause instanceof Error ? cause.message : "Provider auth 保存失败";
  } finally {
    providerActions.value[provider.providerId] = undefined;
  }
}

async function removeProviderAuth(provider: ProviderInfo) {
  providerActions.value[provider.providerId] = "removing";
  providerError.value = undefined;
  try {
    await platform.api.removeProviderAuth(provider.providerId);
    await loadProviderAuth();
  } catch (cause) {
    providerError.value = cause instanceof Error ? cause.message : "Provider auth 移除失败";
  } finally {
    providerActions.value[provider.providerId] = undefined;
  }
}

onMounted(() => document.addEventListener("click", toggleFromClick));
onUnmounted(() => document.removeEventListener("click", toggleFromClick));

// 平台 auth 返回值来自后端透传，这里只抽取 UI 需要的稳定字段。
function normalizeProviderAuth(response: unknown) {
  const items = Array.isArray(response)
    ? response
    : isRecord(response) && Array.isArray(response.items)
      ? response.items
      : [];
  return Object.fromEntries(
    items.flatMap((item) => {
      if (!isRecord(item)) {
        return [];
      }
      const providerId = readString(item.providerId) ?? readString(item.id) ?? readString(item.provider);
      if (!providerId) {
        return [];
      }
      return [
        [
          providerId,
          {
            providerId,
            status: readString(item.status) ?? readString(item.state) ?? "unknown",
            account: readString(item.account) ?? readString(item.email)
          }
        ]
      ];
    })
  ) as Record<string, ProviderAuthState>;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function readString(value: unknown) {
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}
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
        <div v-for="provider in workspace.providers" :key="provider.providerId" class="provider-auth-row">
          <div class="provider-auth-head">
            <span>
              <strong>{{ provider.name }}</strong>
              <small>{{ provider.providerId }}</small>
            </span>
            <small class="auth-pill" :data-state="authState(provider.providerId)">
              {{ authLabel(provider.providerId) }}
            </small>
          </div>
          <form class="provider-auth-form" @submit.prevent="saveProviderKey(provider)">
            <input
              v-model="providerKeys[provider.providerId]"
              :aria-label="`${provider.name} API key`"
              type="password"
              autocomplete="off"
              placeholder="API key"
            />
            <button
              class="primary-action"
              type="submit"
              :aria-label="`Save ${provider.name} key`"
              :disabled="providerActions[provider.providerId] === 'saving'"
            >
              Save key
            </button>
            <button
              class="icon-text"
              type="button"
              :aria-label="`Remove ${provider.name} auth`"
              :disabled="providerActions[provider.providerId] === 'removing'"
              @click="removeProviderAuth(provider)"
            >
              Remove
            </button>
          </form>
        </div>
        <div v-if="!workspace.providers.length" class="empty-note">Provider auth is proxied through the backend API.</div>
        <div v-if="providerError" class="inline-alert">{{ providerError }}</div>
      </div>
      <SettingsWorktreePanel :workspace-id="workspace.selectedWorkspaceId" />
      <SettingsMcpPanel :workspace-id="workspace.selectedWorkspaceId" />
    </section>
  </div>
</template>
