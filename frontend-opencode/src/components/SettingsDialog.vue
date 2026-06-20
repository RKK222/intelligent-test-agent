<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import type { ProviderInfo } from "@test-agent/shared-types";
import { ExternalLink, KeyRound } from "lucide-vue-next";
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

type ProviderAuthPrompt = {
  type: "text" | "select";
  key: string;
  message: string;
  placeholder?: string;
  options?: Array<{ label: string; value: string; hint?: string }>;
  when?: { key: string; op: "eq" | "neq"; value: string };
};

type ProviderAuthMethod = {
  type: "oauth" | "api" | string;
  label: string;
  prompts?: ProviderAuthPrompt[];
};

type IndexedProviderAuthMethod = ProviderAuthMethod & {
  methodIndex: number;
};

type ProviderOAuthAuthorization = {
  url: string;
  method: "auto" | "code" | string;
  instructions?: string;
};

const open = ref(false);
const providerAuth = ref<Record<string, ProviderAuthState>>({});
const providerAuthMethods = ref<Record<string, ProviderAuthMethod[]>>({});
const providerKeys = ref<Record<string, string>>({});
const providerOAuthLinks = ref<Record<string, string | undefined>>({});
const providerOAuthAuthorizations = ref<Record<string, ProviderOAuthAuthorization | undefined>>({});
const providerOAuthCodes = ref<Record<string, string>>({});
const providerOAuthInputs = ref<Record<string, Record<string, string>>>({});
const providerOAuthMethodIndexes = ref<Record<string, number | undefined>>({});
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

function closeFromKeydown(event: KeyboardEvent) {
  if (event.key === "Escape" && open.value) {
    open.value = false;
  }
}

async function loadProviderAuth() {
  providerError.value = undefined;
  try {
    const normalized = normalizeProviderAuth(await platform.api.listProviderAuth(workspace.selectedWorkspaceId));
    providerAuth.value = normalized.states;
    providerAuthMethods.value = normalized.methods;
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

async function authorizeProviderOAuth(provider: ProviderInfo) {
  providerActions.value[provider.providerId] = "oauth";
  providerError.value = undefined;
  try {
    const method = selectedOAuthMethodIndex(provider);
    const response = await platform.api.authorizeProviderOAuth(provider.providerId, {
      method,
      inputs: {
        callbackUrl: providerOAuthCallbackUrl(provider.providerId),
        ...providerOAuthPromptInputs(provider)
      }
    });
    const authorization = extractAuthorization(response);
    if (!authorization) {
      throw new Error("Provider OAuth 响应缺少授权 URL");
    }
    providerOAuthAuthorizations.value = { ...providerOAuthAuthorizations.value, [provider.providerId]: authorization };
    providerOAuthLinks.value = { ...providerOAuthLinks.value, [provider.providerId]: authorization.url };
  } catch (cause) {
    providerError.value = cause instanceof Error ? cause.message : "Provider OAuth 发起失败";
  } finally {
    providerActions.value[provider.providerId] = undefined;
  }
}

async function completeProviderOAuth(provider: ProviderInfo) {
  const authorization = providerOAuthAuthorizations.value[provider.providerId];
  const code = providerOAuthCodes.value[provider.providerId]?.trim();
  if (authorization?.method !== "auto" && !code) {
    return;
  }
  providerActions.value[provider.providerId] = "callback";
  providerError.value = undefined;
  try {
    await platform.api.completeProviderOAuth(provider.providerId, {
      method: selectedOAuthMethodIndex(provider),
      ...(authorization?.method === "auto" ? {} : { code })
    });
    providerOAuthCodes.value[provider.providerId] = "";
    await loadProviderAuth();
  } catch (cause) {
    providerError.value = cause instanceof Error ? cause.message : "Provider OAuth 回调失败";
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

function providerOAuthCallbackUrl(providerId: string) {
  return `${window.location.origin}/api/provider/${encodeURIComponent(providerId)}/oauth/callback`;
}

function oauthMethodsForProvider(provider: ProviderInfo): IndexedProviderAuthMethod[] {
  return providerMethodsForProvider(provider)
    .map((method, methodIndex) => ({ ...method, methodIndex }))
    .filter((method) => method.type === "oauth");
}

function selectedOAuthMethod(provider: ProviderInfo): IndexedProviderAuthMethod | undefined {
  const methods = oauthMethodsForProvider(provider);
  const selected = selectedOAuthMethodIndex(provider);
  return methods.find((method) => method.methodIndex === selected) ?? methods[0];
}

function selectedOAuthMethodIndex(provider: ProviderInfo) {
  const methods = oauthMethodsForProvider(provider);
  const selected = providerOAuthMethodIndexes.value[provider.providerId];
  if (selected !== undefined && methods.some((method) => method.methodIndex === selected)) {
    return selected;
  }
  return methods[0]?.methodIndex ?? 0;
}

function setProviderOAuthMethod(provider: ProviderInfo, event: Event) {
  const value = Number((event.target as HTMLSelectElement).value);
  providerOAuthMethodIndexes.value[provider.providerId] = Number.isFinite(value) ? value : undefined;
  providerOAuthInputs.value[provider.providerId] = {};
  providerOAuthLinks.value = { ...providerOAuthLinks.value, [provider.providerId]: undefined };
  providerOAuthAuthorizations.value = { ...providerOAuthAuthorizations.value, [provider.providerId]: undefined };
}

function visibleOAuthPrompts(provider: ProviderInfo) {
  const inputs = providerOAuthInputs.value[provider.providerId] ?? {};
  return (selectedOAuthMethod(provider)?.prompts ?? []).filter((prompt) => promptMatches(prompt, inputs));
}

function providerOAuthInput(provider: ProviderInfo, key: string) {
  return providerOAuthInputs.value[provider.providerId]?.[key] ?? "";
}

function setProviderOAuthInput(provider: ProviderInfo, key: string, event: Event) {
  const value = (event.target as HTMLInputElement | HTMLSelectElement).value;
  providerOAuthInputs.value[provider.providerId] = {
    ...(providerOAuthInputs.value[provider.providerId] ?? {}),
    [key]: value
  };
}

function providerOAuthPromptInputs(provider: ProviderInfo) {
  const visible = new Set(visibleOAuthPrompts(provider).map((prompt) => prompt.key));
  return Object.fromEntries(
    Object.entries(providerOAuthInputs.value[provider.providerId] ?? {})
      .filter(([key, value]) => visible.has(key) && value.trim())
      .map(([key, value]) => [key, value.trim()])
  );
}

function providerMethodsForProvider(provider: ProviderInfo) {
  const methods = providerAuthMethods.value[provider.providerId];
  if (methods?.length) {
    return methods;
  }
  return [{ type: "oauth", label: "OAuth" }];
}

function promptMatches(prompt: ProviderAuthPrompt, inputs: Record<string, string>) {
  if (!prompt.when) {
    return true;
  }
  const actual = inputs[prompt.when.key];
  if (actual === undefined) {
    return false;
  }
  return prompt.when.op === "eq" ? actual === prompt.when.value : actual !== prompt.when.value;
}

onMounted(() => {
  document.addEventListener("click", toggleFromClick);
  document.addEventListener("keydown", closeFromKeydown);
});
onUnmounted(() => {
  document.removeEventListener("click", toggleFromClick);
  document.removeEventListener("keydown", closeFromKeydown);
});

// 平台 auth 返回值兼容两类形态：平台状态列表，以及 opencode provider_auth 的 method map。
function normalizeProviderAuth(response: unknown) {
  const states: Record<string, ProviderAuthState> = {};
  const methods: Record<string, ProviderAuthMethod[]> = {};
  const source = isRecord(response) && isRecord(response.data) ? response.data : response;
  if (Array.isArray(source)) {
    Object.assign(states, normalizeProviderAuthStates(source));
    return { states, methods };
  }
  if (!isRecord(source)) {
    return { states, methods };
  }
  if (Array.isArray(source.items)) {
    Object.assign(states, normalizeProviderAuthStates(source.items));
    return { states, methods };
  }
  Object.entries(source).forEach(([providerId, value]) => {
    if (Array.isArray(value)) {
      const normalized = value.flatMap(normalizeProviderAuthMethod);
      if (normalized.length) {
        methods[providerId] = normalized;
      }
      return;
    }
    if (isRecord(value) && Array.isArray(value.methods)) {
      const normalized = value.methods.flatMap(normalizeProviderAuthMethod);
      if (normalized.length) {
        methods[providerId] = normalized;
      }
      return;
    }
    normalizeProviderAuthState(providerId, value).forEach((state) => {
      states[state.providerId] = state;
    });
  });
  return { states, methods };
}

function normalizeProviderAuthStates(items: unknown[]) {
  return Object.fromEntries(items.flatMap((item) => normalizeProviderAuthState(undefined, item).map((state) => [state.providerId, state])));
}

function normalizeProviderAuthState(providerId: string | undefined, item: unknown): ProviderAuthState[] {
  if (!isRecord(item)) {
    return [];
  }
  const id = providerId ?? readString(item.providerId) ?? readString(item.id) ?? readString(item.provider);
  if (!id) {
    return [];
  }
  return [
    {
      providerId: id,
      status: readString(item.status) ?? readString(item.state) ?? "unknown",
      account: readString(item.account) ?? readString(item.email)
    }
  ];
}

function normalizeProviderAuthMethod(item: unknown): ProviderAuthMethod[] {
  if (!isRecord(item)) {
    return [];
  }
  const type = readString(item.type);
  const label = readString(item.label) ?? (type === "api" ? "API key" : "OAuth");
  if (type !== "api" && type !== "oauth") {
    return [];
  }
  return [
    {
      type,
      label,
      prompts: Array.isArray(item.prompts) ? item.prompts.flatMap(normalizeProviderAuthPrompt) : undefined
    }
  ];
}

function normalizeProviderAuthPrompt(item: unknown): ProviderAuthPrompt[] {
  if (!isRecord(item)) {
    return [];
  }
  const type = readString(item.type);
  const key = readString(item.key);
  const message = readString(item.message);
  if ((type !== "text" && type !== "select") || !key || !message) {
    return [];
  }
  const prompt: ProviderAuthPrompt = {
    type,
    key,
    message,
    placeholder: readString(item.placeholder),
    when: normalizePromptWhen(item.when)
  };
  if (type === "select") {
    prompt.options = Array.isArray(item.options)
      ? item.options.flatMap((option) => {
          if (!isRecord(option)) {
            return [];
          }
          const value = readString(option.value);
          const label = readString(option.label) ?? value;
          return value && label ? [{ value, label, hint: readString(option.hint) }] : [];
        })
      : [];
  }
  return [prompt];
}

function normalizePromptWhen(value: unknown): ProviderAuthPrompt["when"] {
  if (!isRecord(value)) {
    return undefined;
  }
  const key = readString(value.key);
  const op = readString(value.op);
  const whenValue = readString(value.value);
  if (!key || (op !== "eq" && op !== "neq") || !whenValue) {
    return undefined;
  }
  return { key, op, value: whenValue };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function readString(value: unknown) {
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

function extractAuthorization(response: unknown): ProviderOAuthAuthorization | undefined {
  if (!isRecord(response)) {
    return undefined;
  }
  const authorization = isRecord(response.authorization) ? response.authorization : undefined;
  const source = authorization ?? response;
  const url = readString(source.url) ?? readString(source.href) ?? readString(source.authorizationUrl);
  if (!url) {
    return undefined;
  }
  return {
    url,
    method: readString(source.method) ?? "code",
    instructions: readString(source.instructions)
  };
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
              :aria-label="`Authorize ${provider.name} OAuth`"
              :disabled="providerActions[provider.providerId] === 'oauth'"
              @click="authorizeProviderOAuth(provider)"
            >
              <KeyRound :size="14" />OAuth
            </button>
            <label v-if="oauthMethodsForProvider(provider).length > 1" class="provider-oauth-method">
              <span>OAuth method</span>
              <select
                :aria-label="`${provider.name} OAuth method`"
                :value="selectedOAuthMethodIndex(provider)"
                @change="setProviderOAuthMethod(provider, $event)"
              >
                <option v-for="method in oauthMethodsForProvider(provider)" :key="method.methodIndex" :value="method.methodIndex">
                  {{ method.label }}
                </option>
              </select>
            </label>
            <small v-else-if="selectedOAuthMethod(provider)" class="auth-method-label">
              {{ selectedOAuthMethod(provider)?.label }}
            </small>
            <template v-for="prompt in visibleOAuthPrompts(provider)" :key="prompt.key">
              <label v-if="prompt.type === 'select'" class="provider-auth-prompt">
                <span>{{ prompt.message }}</span>
                <select
                  :aria-label="`${provider.name} ${prompt.message}`"
                  :value="providerOAuthInput(provider, prompt.key)"
                  @change="setProviderOAuthInput(provider, prompt.key, $event)"
                >
                  <option value="">Select...</option>
                  <option v-for="option in prompt.options ?? []" :key="option.value" :value="option.value">
                    {{ option.label }}{{ option.hint ? ` - ${option.hint}` : "" }}
                  </option>
                </select>
              </label>
              <label v-else class="provider-auth-prompt">
                <span>{{ prompt.message }}</span>
                <input
                  :aria-label="`${provider.name} ${prompt.message}`"
                  autocomplete="off"
                  :placeholder="prompt.placeholder ?? prompt.message"
                  :value="providerOAuthInput(provider, prompt.key)"
                  @input="setProviderOAuthInput(provider, prompt.key, $event)"
                />
              </label>
            </template>
            <a
              v-if="providerOAuthLinks[provider.providerId]"
              class="icon-text"
              :href="providerOAuthLinks[provider.providerId]"
              target="_blank"
              rel="noreferrer"
              :aria-label="`Open ${provider.name} OAuth URL`"
            >
              <ExternalLink :size="14" />Open OAuth
            </a>
            <small v-if="providerOAuthAuthorizations[provider.providerId]?.instructions" class="auth-instruction">
              {{ providerOAuthAuthorizations[provider.providerId]?.instructions }}
            </small>
            <input
              v-if="providerOAuthLinks[provider.providerId] && providerOAuthAuthorizations[provider.providerId]?.method !== 'auto'"
              v-model="providerOAuthCodes[provider.providerId]"
              :aria-label="`${provider.name} OAuth code`"
              autocomplete="off"
              placeholder="OAuth code"
            />
            <button
              v-if="providerOAuthLinks[provider.providerId]"
              class="primary-action"
              type="button"
              :aria-label="`Complete ${provider.name} OAuth`"
              :disabled="providerActions[provider.providerId] === 'callback'"
              @click="completeProviderOAuth(provider)"
            >
              {{ providerOAuthAuthorizations[provider.providerId]?.method === "auto" ? "Complete automatic OAuth" : "Complete OAuth" }}
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
