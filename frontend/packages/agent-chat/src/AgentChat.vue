<script lang="ts">
import type {
  AgentInfo,
  AgentMessage,
  CommandInfo,
  ModelInfo,
  PermissionRequest,
  ProviderInfo,
  QuestionRequest,
  RuntimeResourceInfo,
  RuntimeStatus,
  RuntimeToolInfo,
  TodoItem
} from "@test-agent/shared-types";
import type { ComposerAttachment } from "./prompt-parts";

export type HistoryItem = {
  id: string;
  title: string;
  preview: string;
  status: string;
  updatedAt: string;
  pinned?: boolean;
};

export type AgentChatProps = {
  messages: AgentMessage[];
  history: HistoryItem[];
  running?: boolean;
  permissions?: PermissionRequest[];
  questions?: QuestionRequest[];
  todos?: TodoItem[];
  agents?: AgentInfo[];
  models?: ModelInfo[];
  providers?: ProviderInfo[];
  commands?: CommandInfo[];
  resources?: RuntimeResourceInfo[];
  tools?: RuntimeToolInfo[];
  runtimeStatus?: RuntimeStatus;
  selectedAgent?: string;
  selectedProvider?: string;
  selectedModel?: string;
  mode?: string;
  historySearch?: string;
};

type AgentTab = "agent" | "history";
</script>

<script setup lang="ts">
import { computed, ref } from "vue";
import { Pin, Trash2 } from "lucide-vue-next";
import { Input, SegmentedTabs } from "@test-agent/ui-kit";
import AssistantThread from "./AssistantThread.vue";
import RuntimeDock from "./RuntimeDock.vue";
import { contextPercent } from "./chat-utils";

const props = withDefaults(defineProps<AgentChatProps>(), {
  permissions: () => [],
  questions: () => [],
  todos: () => [],
  agents: () => [],
  models: () => [],
  providers: () => [],
  commands: () => [],
  resources: () => [],
  tools: () => [],
  mode: "build"
});
const emit = defineEmits<{
  send: [prompt: string, attachments: ComposerAttachment[]];
  openDiff: [];
  retry: [];
  cancel: [];
  replyPermission: [requestId: string, decision: "once" | "always" | "reject"];
  replyQuestion: [requestId: string, answers: unknown[]];
  rejectQuestion: [requestId: string];
  agentChange: [agentId: string];
  providerChange: [providerId: string];
  modelChange: [modelId: string];
  modeChange: [mode: string];
  selectHistory: [sessionId: string];
  historySearchChange: [query: string];
  toggleHistoryPin: [sessionId: string, pinned: boolean];
  deleteHistory: [sessionId: string];
  requestNotifications: [];
}>();

const tab = ref<AgentTab>("agent");
const localHistorySearch = ref("");
const resolvedHistorySearch = computed(() => props.historySearch ?? localHistorySearch.value);
const filteredHistory = computed(() => {
  const query = resolvedHistorySearch.value.trim().toLowerCase();
  return query
    ? props.history.filter((item) => `${item.title} ${item.preview} ${item.status}`.toLowerCase().includes(query))
    : props.history;
});

const percent = computed(() => contextPercent(props.runtimeStatus));
const showStatusPanel = computed(
  () => Boolean(props.runtimeStatus) || props.tools.length > 0 || props.resources.length > 0
);

function onHistorySearchInput(value: string) {
  localHistorySearch.value = value;
  emit("historySearchChange", value);
}
</script>

<template>
  <div class="flex h-full min-h-0 flex-col bg-[var(--ta-chat-bg)] text-[var(--ta-chat-text)]">
    <SegmentedTabs
      v-model="tab"
      :items="[
        { id: 'agent', label: 'Agent' },
        { id: 'history', label: '历史', count: history.length }
      ]"
    />
    <template v-if="tab === 'agent'">
      <div v-if="showStatusPanel" class="flex min-h-8 flex-wrap items-center gap-2 border-b border-[var(--ta-chat-border)] bg-[var(--ta-chat-surface)] px-3 py-1.5 text-[11px] text-[var(--ta-chat-muted)]">
        <span class="rounded border border-[var(--ta-chat-border)] px-2 py-0.5">Session {{ runtimeStatus?.status ?? "idle" }}</span>
        <span v-if="runtimeStatus?.branch" class="rounded border border-[var(--ta-chat-border)] px-2 py-0.5">{{ runtimeStatus.branch }}</span>
        <span v-if="runtimeStatus?.lsp" class="rounded border border-[var(--ta-chat-border)] px-2 py-0.5">LSP {{ runtimeStatus.lsp.status }}</span>
        <span v-if="runtimeStatus?.mcp" class="rounded border border-[var(--ta-chat-border)] px-2 py-0.5">MCP {{ runtimeStatus.mcp.status }}</span>
        <span v-if="tools.length" class="rounded border border-[var(--ta-chat-border)] px-2 py-0.5">{{ tools.length }} tools</span>
        <span v-if="resources.length" class="rounded border border-[var(--ta-chat-border)] px-2 py-0.5">{{ resources.length }} refs</span>
        <span v-if="percent != null" class="flex min-w-[96px] items-center gap-2">
          <span class="h-1.5 flex-1 rounded bg-[var(--ta-chat-detail-bg)]">
            <span class="block h-1.5 rounded bg-[var(--ta-chat-status-running)]" :style="{ width: `${percent}%` }" />
          </span>
          {{ percent }}%
        </span>
      </div>
      <RuntimeDock
        :permissions="permissions"
        :questions="questions"
        @reply-permission="(id, decision) => emit('replyPermission', id, decision)"
        @reply-question="(id, answers) => emit('replyQuestion', id, answers)"
        @reject-question="(id) => emit('rejectQuestion', id)"
      />
      <section aria-label="Agent 对话线程" class="min-h-0 flex-1 overflow-hidden">
        <AssistantThread
          :messages="messages"
          :running="running"
          :commands="commands"
          :resources="resources"
          :agents="agents"
          :models="models"
          :providers="providers"
          :selected-agent="selectedAgent"
          :selected-provider="selectedProvider"
          :selected-model="selectedModel"
          :mode="mode"
          :todos="todos"
          @send="(prompt, attachments) => emit('send', prompt, attachments)"
          @cancel="emit('cancel')"
          @retry="emit('retry')"
          @open-diff="emit('openDiff')"
          @agent-change="(v) => emit('agentChange', v)"
          @provider-change="(v) => emit('providerChange', v)"
          @model-change="(v) => emit('modelChange', v)"
          @mode-change="(v) => emit('modeChange', v)"
          @request-notifications="emit('requestNotifications')"
        />
      </section>
    </template>
    <div v-else class="min-h-0 flex-1 space-y-2 overflow-auto p-3">
      <Input
        :model-value="resolvedHistorySearch"
        placeholder="搜索 Session"
        @update:model-value="onHistorySearchInput"
      />
      <div
        v-for="item in filteredHistory"
        :key="item.id"
        class="rounded-md border border-slate-800 bg-slate-950 p-3 hover:border-slate-600"
      >
        <div class="flex items-start gap-2">
          <button type="button" class="min-w-0 flex-1 text-left" @click="emit('selectHistory', item.id)">
            <div class="flex items-center gap-2">
              <span class="rounded-full bg-slate-800 px-2 py-0.5 text-[11px] text-slate-300">{{ item.status }}</span>
              <span class="min-w-0 flex-1 truncate text-[12px] font-semibold text-slate-100">{{ item.title }}</span>
              <span class="text-[11px] text-slate-500">{{ item.updatedAt }}</span>
            </div>
            <div class="mt-1 truncate text-[12px] text-slate-500">{{ item.preview }}</div>
          </button>
          <div v-if="item.id.startsWith('ses_')" class="flex shrink-0 gap-1">
            <button
              type="button"
              :title="item.pinned ? '取消置顶' : '置顶'"
              :class="['rounded border border-slate-800 p-1 hover:border-slate-600 hover:text-slate-100', item.pinned ? 'text-cyan-300' : 'text-slate-500']"
              @click="emit('toggleHistoryPin', item.id, !item.pinned)"
            >
              <Pin class="h-3.5 w-3.5" />
            </button>
            <button
              type="button"
              title="删除"
              class="rounded border border-slate-800 p-1 text-slate-500 hover:border-red-900 hover:text-red-200"
              @click="emit('deleteHistory', item.id)"
            >
              <Trash2 class="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
