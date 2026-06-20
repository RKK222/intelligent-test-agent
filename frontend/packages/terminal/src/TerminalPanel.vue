<script lang="ts">
import type { TerminalTicketResponse } from "@test-agent/shared-types";
import type { TerminalSnapshot, TerminalSession, TerminalWebSocketConstructor } from "./terminal-client";

export type TerminalPanelProps = {
  baseUrl: string;
  createTicket: () => Promise<TerminalTicketResponse>;
  disabled?: boolean;
  disabledReason?: string;
  WebSocketCtor?: TerminalWebSocketConstructor;
};
</script>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef } from "vue";
import { Send, Square, Terminal as TerminalIcon } from "lucide-vue-next";
import { Badge, Button, Input } from "@test-agent/ui-kit";
import { createTerminalSession } from "./terminal-client";

const props = defineProps<TerminalPanelProps>();

const initialSnapshot: TerminalSnapshot = { status: "idle", output: "" };
const snapshot = ref<TerminalSnapshot>(initialSnapshot);
const command = ref("");
// 会话实例持有 WebSocket，用 shallowRef 避免 Vue 代理其内部结构
const sessionRef = shallowRef<TerminalSession | null>(null);

const connecting = computed(() => snapshot.value.status === "connecting");
const open = computed(() => snapshot.value.status === "open");

onBeforeUnmount(() => {
  sessionRef.value?.close("unmount");
});

async function connect() {
  if (props.disabled || connecting.value || open.value) {
    return;
  }
  sessionRef.value?.close("reconnect");
  snapshot.value = { status: "connecting", output: "" };
  try {
    const ticket = await props.createTicket();
    // ticket 只保存在当前内存会话里，WebSocket 生命周期由 terminal package 统一管理
    const nextSession = createTerminalSession({
      baseUrl: props.baseUrl,
      ticket,
      WebSocketCtor: props.WebSocketCtor,
      onEvent: () => {
        snapshot.value = nextSession.snapshot();
      }
    });
    sessionRef.value = nextSession;
    snapshot.value = nextSession.snapshot();
  } catch (error) {
    snapshot.value = {
      status: "error",
      output: "",
      error: { code: "PTY_TICKET_FAILED", message: error instanceof Error ? error.message : "terminal ticket failed" }
    };
  }
}

function send() {
  const value = command.value.trim();
  if (!value || !open.value) {
    return;
  }
  sessionRef.value?.sendInput(`${value}\n`);
  command.value = "";
}

function close() {
  sessionRef.value?.close("user");
  snapshot.value = sessionRef.value?.snapshot() ?? { ...initialSnapshot, status: "closed" };
}
</script>

<template>
  <div class="flex h-full min-h-0 flex-col bg-[var(--ta-panel)]">
    <div class="flex h-10 items-center gap-2 border-b border-slate-800 bg-slate-950 px-3">
      <TerminalIcon class="h-4 w-4 text-emerald-300" />
      <div class="min-w-0 flex-1 text-[12px] font-semibold text-slate-200">终端</div>
      <Badge :tone="snapshot.status === 'error' ? 'danger' : open ? 'success' : connecting ? 'info' : 'neutral'">
        {{ snapshot.status }}
      </Badge>
      <Button size="sm" variant="secondary" :disabled="disabled || connecting || open" @click="connect">
        连接终端
      </Button>
      <Button size="sm" variant="secondary" :disabled="!open" @click="close">
        <Square class="h-3.5 w-3.5" />
        关闭
      </Button>
    </div>
    <div v-if="disabled" class="border-b border-slate-800 px-3 py-2 text-[12px] text-slate-500">
      {{ disabledReason ?? "终端当前不可用" }}
    </div>
    <div v-if="snapshot.error" class="border-b border-[rgba(239,68,68,.3)] bg-[rgba(239,68,68,.12)] px-3 py-2 text-[12px] text-[#fca5a5]">
      {{ snapshot.error.code }}: {{ snapshot.error.message }}
    </div>
    <div
      v-for="warning in snapshot.warnings"
      :key="`${warning.code}:${warning.message}`"
      class="border-b border-[rgba(245,158,11,.3)] bg-[rgba(245,158,11,.12)] px-3 py-2 text-[12px] text-[#fcd34d]"
    >
      {{ warning.code }}: {{ warning.message }}
    </div>
    <pre class="min-h-0 flex-1 overflow-auto whitespace-pre-wrap p-3 font-mono text-[12px] leading-6 text-[var(--ta-text)]">{{ snapshot.output || "连接后显示终端输出..." }}</pre>
    <form class="flex gap-2 border-t border-slate-800 bg-slate-950 p-3" @submit.prevent="send">
      <Input v-model="command" :disabled="!open" placeholder="输入命令" />
      <Button type="submit" variant="primary" :disabled="!open || !command.trim()">
        <Send class="h-3.5 w-3.5" />
        发送
      </Button>
    </form>
  </div>
</template>
