<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { AlertTriangle, RefreshCw, ShieldCheck } from "lucide-vue-next";
import { BackendApiError, type BackendApiClient } from "@test-agent/backend-api";
import type { CurrentUser } from "@test-agent/shared-types";
import { applyXxlJobEmbeddedShell } from "./xxl-job-embedded-shell";

const props = defineProps<{
  currentUser: CurrentUser | null;
}>();

type ConsoleState =
  | "idle"
  | "loading"
  | "ready"
  | "forbidden"
  | "ticketExpired"
  | "sessionExpired"
  | "unavailable";

const api = inject<BackendApiClient>("api")!;
const hasSuperAdmin = computed(() => props.currentUser?.roles?.includes("SUPER_ADMIN") === true);
const formRef = ref<HTMLFormElement | null>(null);
const frameRef = ref<HTMLIFrameElement | null>(null);
const frameName = `test-agent-xxl-job-${Math.random().toString(36).slice(2)}`;
const state = ref<ConsoleState>("idle");
const ticket = ref("");
const formAction = ref("");
let requestSequence = 0;
let expiryTimer: ReturnType<typeof setTimeout> | undefined;
let loadTimer: ReturnType<typeof setTimeout> | undefined;

const statusText = computed(() => {
  switch (state.value) {
    case "ready":
      return "XXL-JOB 控制台已连接";
    case "loading":
      return "正在建立安全会话";
    case "forbidden":
      return "无管理权限";
    case "ticketExpired":
      return "登录票据已失效";
    case "sessionExpired":
      return "平台会话已失效";
    case "unavailable":
      return "管理服务不可用";
    default:
      return "等待连接";
  }
});

const stateMessage = computed(() => {
  switch (state.value) {
    case "forbidden":
      return "当前账号无定时任务管理权限";
    case "ticketExpired":
      return "一次性登录票据已过期，请重新加载控制台";
    case "sessionExpired":
      return "平台会话已失效，请重新登录后再加载控制台";
    case "unavailable":
      return "XXL-JOB 管理服务暂不可用";
    default:
      return "正在签发一次性票据并连接管理控制台";
  }
});

function clearTimers() {
  if (expiryTimer !== undefined) {
    clearTimeout(expiryTimer);
    expiryTimer = undefined;
  }
  if (loadTimer !== undefined) {
    clearTimeout(loadTimer);
    loadTimer = undefined;
  }
}

function isSafeFormAction(value: string) {
  return /^\/xxl-job-admin(?:\/|$)/.test(value) && !value.includes("?") && !value.includes("#");
}

async function loadConsole() {
  if (!hasSuperAdmin.value) {
    state.value = "forbidden";
    return;
  }
  const sequence = ++requestSequence;
  clearTimers();
  ticket.value = "";
  formAction.value = "";
  state.value = "loading";
  try {
    const issue = await api.createXxlJobSsoTicket();
    if (sequence !== requestSequence || !hasSuperAdmin.value) {
      return;
    }
    if (!issue.ticket || !isSafeFormAction(issue.formAction)) {
      state.value = "unavailable";
      return;
    }
    ticket.value = issue.ticket;
    formAction.value = issue.formAction;
    await nextTick();
    if (sequence !== requestSequence || !formRef.value) {
      return;
    }
    formRef.value.submit();

    // 票据最长只存活 60 秒；iframe 未完成导航时给出可操作的过期或不可用状态。
    const expiresIn = Math.min(60_000, Math.max(0, Date.parse(issue.expiresAt) - Date.now()));
    expiryTimer = setTimeout(() => {
      if (state.value === "loading") {
        state.value = "ticketExpired";
      }
    }, expiresIn);
    loadTimer = setTimeout(() => {
      if (state.value === "loading") {
        state.value = "unavailable";
      }
    }, Math.min(15_000, Math.max(1_000, expiresIn)));
  } catch (error) {
    if (sequence !== requestSequence) {
      return;
    }
    const status = error instanceof BackendApiError
      ? error.status
      : (error as { status?: number } | null)?.status;
    state.value = status === 401 ? "sessionExpired" : status === 403 ? "forbidden" : "unavailable";
  }
}

function onSsoMessage(event: MessageEvent) {
  if (event.origin !== window.location.origin || event.data?.type !== "test-agent-xxl-job-sso") {
    return;
  }
  clearTimers();
  if (event.data.status === "ready") {
    state.value = "ready";
  } else if (event.data.status === "expired") {
    state.value = "sessionExpired";
  } else if (event.data.status === "forbidden") {
    state.value = "ticketExpired";
  } else {
    state.value = "unavailable";
  }
}

function onFrameLoad() {
  if (frameRef.value) {
    // SSO 中转页和错误页会安全跳过；只有真实 XXL shell 才应用平台嵌入态布局。
    applyXxlJobEmbeddedShell(frameRef.value);
  }
}

watch(hasSuperAdmin, (allowed) => {
  requestSequence += 1;
  clearTimers();
  if (allowed) {
    void loadConsole();
  } else {
    ticket.value = "";
    formAction.value = "";
    state.value = "forbidden";
  }
});

onMounted(() => {
  window.addEventListener("message", onSsoMessage);
  if (hasSuperAdmin.value) {
    void loadConsole();
  } else {
    state.value = "forbidden";
  }
});

onBeforeUnmount(() => {
  requestSequence += 1;
  clearTimers();
  window.removeEventListener("message", onSsoMessage);
});
</script>

<template>
  <section class="ta-xxl-shell">
    <div v-if="!hasSuperAdmin" class="ta-xxl-placeholder">当前账号无定时任务管理权限</div>
    <template v-else>
      <header class="ta-xxl-toolbar">
        <div class="ta-xxl-heading">
          <strong>定时任务管理</strong>
        </div>
        <div class="ta-xxl-actions">
          <span :class="['ta-xxl-status', `is-${state}`]">
            <ShieldCheck v-if="state === 'ready'" aria-hidden="true" />
            <AlertTriangle v-else-if="['forbidden', 'ticketExpired', 'sessionExpired', 'unavailable'].includes(state)" aria-hidden="true" />
            <span>{{ statusText }}</span>
          </span>
          <button type="button" class="ta-xxl-reload" :disabled="state === 'loading'" aria-label="重新加载" @click="loadConsole">
            <RefreshCw aria-hidden="true" />
          </button>
        </div>
      </header>

      <div class="ta-xxl-console">
        <iframe
          ref="frameRef"
          :name="frameName"
          src="about:blank"
          title="XXL-JOB 定时任务管理"
          referrerpolicy="same-origin"
          :aria-busy="state === 'loading'"
          @load="onFrameLoad"
        />

        <div v-if="state !== 'ready'" class="ta-xxl-state" :class="{ 'is-loading': state === 'loading' }">
          <div class="ta-xxl-state-card" role="status">
            <span class="ta-xxl-state-mark" aria-hidden="true">
              <span v-if="state === 'loading'" class="ta-xxl-pulse" />
              <AlertTriangle v-else />
            </span>
            <div>
              <strong>{{ statusText }}</strong>
              <p>{{ stateMessage }}</p>
            </div>
            <button
              v-if="['forbidden', 'ticketExpired', 'sessionExpired', 'unavailable'].includes(state)"
              type="button"
              class="ta-xxl-state-action"
              @click="loadConsole"
            >
              重试
            </button>
          </div>
        </div>
      </div>

      <form ref="formRef" class="ta-xxl-ticket-form" method="post" :action="formAction" :target="frameName" autocomplete="off">
        <input type="hidden" name="ticket" :value="ticket" />
      </form>
    </template>
  </section>
</template>

<style scoped>
.ta-xxl-shell {
  display: flex;
  min-height: 0;
  height: 100%;
  flex-direction: column;
  background: #f7f8fa;
  color: #1f2937;
}

.ta-xxl-placeholder {
  margin: 16px;
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
  color: #6b7280;
  font-size: 13px;
}

.ta-xxl-toolbar {
  display: flex;
  min-height: 44px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 6px 14px 6px 16px;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
}

.ta-xxl-heading {
  display: flex;
  align-items: center;
}

.ta-xxl-heading strong {
  font-size: 14px;
  font-weight: 650;
  letter-spacing: 0.01em;
}

.ta-xxl-actions,
.ta-xxl-status,
.ta-xxl-reload {
  display: flex;
  align-items: center;
}

.ta-xxl-actions {
  gap: 10px;
}

.ta-xxl-status {
  gap: 6px;
  color: #64748b;
  font-size: 12px;
}

.ta-xxl-status svg,
.ta-xxl-reload svg {
  width: 14px;
  height: 14px;
}

.ta-xxl-status.is-ready {
  color: #15803d;
}

.ta-xxl-status.is-forbidden,
.ta-xxl-status.is-ticketExpired,
.ta-xxl-status.is-sessionExpired,
.ta-xxl-status.is-unavailable {
  color: #b45309;
}

.ta-xxl-reload,
.ta-xxl-state-action {
  justify-content: center;
  border: 1px solid #dbe1ea;
  border-radius: 6px;
  background: #fff;
  color: #334155;
  cursor: pointer;
  font: inherit;
}

.ta-xxl-reload {
  width: 32px;
  height: 32px;
  padding: 0;
}

.ta-xxl-reload:hover:not(:disabled),
.ta-xxl-reload:focus-visible,
.ta-xxl-state-action:hover,
.ta-xxl-state-action:focus-visible {
  border-color: #93b4f5;
  color: #1d4ed8;
  outline: 2px solid #dbeafe;
  outline-offset: 1px;
}

.ta-xxl-reload:disabled {
  cursor: wait;
  opacity: 0.55;
}

.ta-xxl-console {
  position: relative;
  flex: 1;
  min-height: 0;
  margin: 12px;
  overflow: hidden;
  border: 1px solid #dfe4ec;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 1px 2px rgb(15 23 42 / 5%);
}

.ta-xxl-console iframe {
  display: block;
  width: 100%;
  height: 100%;
  min-height: 520px;
  border: 0;
  background: #fff;
}

.ta-xxl-state {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgb(247 248 250 / 94%);
}

.ta-xxl-state-card {
  display: grid;
  width: min(480px, 100%);
  grid-template-columns: 38px 1fr auto;
  align-items: center;
  gap: 14px;
  padding: 20px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
}

.ta-xxl-state-card strong {
  display: block;
  margin-bottom: 4px;
  color: #0f172a;
  font-size: 14px;
}

.ta-xxl-state-card p {
  margin: 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.55;
}

.ta-xxl-state-mark {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 50%;
  background: #eff6ff;
  color: #2563eb;
}

.ta-xxl-state-mark svg {
  width: 18px;
  height: 18px;
  color: #b45309;
}

.ta-xxl-pulse {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #2563eb;
  box-shadow: 0 0 0 0 rgb(37 99 235 / 35%);
  animation: ta-xxl-pulse 1.4s ease-out infinite;
}

.ta-xxl-state-action {
  min-height: 32px;
  padding: 0 12px;
  font-size: 12px;
}

.ta-xxl-ticket-form {
  display: none;
}

@keyframes ta-xxl-pulse {
  70%, 100% {
    box-shadow: 0 0 0 10px rgb(37 99 235 / 0%);
  }
}

@media (max-width: 720px) {
  .ta-xxl-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .ta-xxl-actions {
    width: 100%;
    justify-content: space-between;
  }

  .ta-xxl-console {
    margin: 8px;
  }

  .ta-xxl-state-card {
    grid-template-columns: 38px 1fr;
  }

  .ta-xxl-state-action {
    grid-column: 2;
    justify-self: start;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ta-xxl-pulse {
    animation: none;
  }
}
</style>
