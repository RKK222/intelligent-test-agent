<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { ExternalLink, KeyRound, RefreshCw, ServerCog, Trash2 } from "lucide-vue-next";
import { usePlatformStore } from "@/stores/platform";

type McpStatusItem = {
  name: string;
  status: string;
  error?: string;
};

const props = defineProps<{
  workspaceId?: string;
}>();

const platform = usePlatformStore();
const items = ref<McpStatusItem[]>([]);
const authLinks = ref<Record<string, string>>({});
const loading = ref(false);
const action = ref<string>();
const error = ref<string>();

const isEmpty = computed(() => !loading.value && items.value.length === 0);
const connectedCount = computed(() => items.value.filter((item) => item.status === "connected").length);

onMounted(() => void loadStatus());
watch(() => props.workspaceId, () => void loadStatus());

async function loadStatus() {
  loading.value = true;
  error.value = undefined;
  try {
    items.value = normalizeMcpStatus(await platform.api.getMcpStatus(props.workspaceId));
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : "MCP 状态加载失败";
    items.value = [];
  } finally {
    loading.value = false;
  }
}

async function startAuth(item: McpStatusItem) {
  action.value = `auth:${item.name}`;
  error.value = undefined;
  try {
    const response = await platform.api.startMcpAuth(item.name, workspacePayload());
    const url = extractUrl(response);
    if (url) {
      authLinks.value = { ...authLinks.value, [item.name]: url };
    }
    await loadStatus();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : "MCP 认证发起失败";
  } finally {
    action.value = undefined;
  }
}

async function removeAuth(item: McpStatusItem) {
  action.value = `remove:${item.name}`;
  error.value = undefined;
  try {
    await platform.api.removeMcpAuth(item.name);
    const next = { ...authLinks.value };
    delete next[item.name];
    authLinks.value = next;
    await loadStatus();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : "MCP 认证移除失败";
  } finally {
    action.value = undefined;
  }
}

function canAuthenticate(item: McpStatusItem) {
  return ["needs_auth", "needs_client_registration", "failed"].includes(item.status);
}

function statusLabel(status: string) {
  const labels: Record<string, string> = {
    connected: "Connected",
    failed: "Failed",
    needs_auth: "Needs auth",
    needs_client_registration: "Needs client registration",
    disabled: "Disabled"
  };
  return labels[status] ?? status;
}

function statusState(status: string) {
  if (status === "connected") {
    return "connected";
  }
  if (status === "failed") {
    return "failed";
  }
  if (status === "needs_auth" || status === "needs_client_registration") {
    return "warning";
  }
  return "disabled";
}

function workspacePayload() {
  return props.workspaceId ? { workspaceId: props.workspaceId } : {};
}

// MCP status 在 opencode 侧是 name -> status 对象；平台测试环境也可能包一层 data/items。
function normalizeMcpStatus(response: unknown) {
  const source = isRecord(response) && isRecord(response.data) ? response.data : response;
  if (Array.isArray(source)) {
    return source.flatMap(normalizeArrayItem).sort(sortByName);
  }
  if (!isRecord(source)) {
    return [];
  }
  const entries = Array.isArray(source.items)
    ? source.items.flatMap(normalizeArrayItem)
    : Object.entries(source).flatMap(([name, value]) => normalizeRecordItem(name, value));
  return entries.sort(sortByName);
}

function normalizeArrayItem(item: unknown) {
  if (!isRecord(item)) {
    return [];
  }
  const name = readString(item.name) ?? readString(item.id);
  if (!name) {
    return [];
  }
  return normalizeRecordItem(name, item);
}

function normalizeRecordItem(name: string, value: unknown) {
  if (!isRecord(value)) {
    return [{ name, status: readString(value) ?? "unknown" }];
  }
  return [
    {
      name,
      status: readString(value.status) ?? readString(value.state) ?? "unknown",
      error: readString(value.error) ?? readString(value.message)
    }
  ];
}

function extractUrl(response: unknown) {
  if (!isRecord(response)) {
    return undefined;
  }
  return readString(response.url) ?? readString(response.href) ?? readString(response.authorizationUrl);
}

function sortByName(left: McpStatusItem, right: McpStatusItem) {
  return left.name.localeCompare(right.name);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function readString(value: unknown) {
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}
</script>

<template>
  <section class="settings-list mcp-panel" aria-label="MCP">
    <div class="section-label">MCP</div>
    <div class="mcp-summary">
      <span>{{ connectedCount }} / {{ items.length }} connected</span>
      <button class="icon-text" type="button" :disabled="loading" @click="loadStatus">
        <RefreshCw :size="14" />Refresh
      </button>
    </div>
    <div v-if="loading && !items.length" class="empty-note">Loading MCP status...</div>
    <div v-else-if="isEmpty" class="empty-note">No MCP servers configured.</div>
    <article v-for="item in items" :key="item.name" class="mcp-row">
      <div class="mcp-head">
        <span class="mcp-title">
          <ServerCog :size="15" />
          <strong>{{ item.name }}</strong>
        </span>
        <small class="auth-pill" :data-state="statusState(item.status)">{{ statusLabel(item.status) }}</small>
      </div>
      <small v-if="item.error" class="mcp-error">{{ item.error }}</small>
      <div class="mcp-actions">
        <button
          v-if="canAuthenticate(item)"
          class="primary-action"
          type="button"
          :aria-label="`Authenticate ${item.name}`"
          :disabled="action === `auth:${item.name}`"
          @click="startAuth(item)"
        >
          <KeyRound :size="14" />Authenticate
        </button>
        <a
          v-if="authLinks[item.name]"
          class="icon-text"
          :href="authLinks[item.name]"
          target="_blank"
          rel="noreferrer"
          :aria-label="`Open ${item.name} auth URL`"
        >
          <ExternalLink :size="14" />Open auth URL
        </a>
        <button
          class="icon-text danger-action"
          type="button"
          :aria-label="`Remove ${item.name} auth`"
          :disabled="action === `remove:${item.name}`"
          @click="removeAuth(item)"
        >
          <Trash2 :size="14" />Remove auth
        </button>
      </div>
    </article>
    <div v-if="error" class="inline-alert">{{ error }}</div>
  </section>
</template>
