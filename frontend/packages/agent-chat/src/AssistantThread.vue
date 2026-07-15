<script lang="ts">
import type {
  AgentInfo,
  AgentMessage,
  CommandInfo,
  MessageScope,
  ModelInfo,
  ProviderInfo,
  RuntimeResourceInfo,
  SubagentSession,
  TodoItem
} from "@test-agent/shared-types";
import type { ComposerAttachment } from "./prompt-parts";

export type AssistantThreadProps = {
  messages: AgentMessage[];
  running?: boolean;
  commands: CommandInfo[];
  resources: RuntimeResourceInfo[];
  agents?: AgentInfo[];
  models?: ModelInfo[];
  providers?: ProviderInfo[];
  selectedAgent?: string;
  selectedProvider?: string;
  selectedModel?: string;
  mode?: string;
  todos?: TodoItem[];
  todoSnapshotsByUserMessageId?: Record<string, TodoItem[]>;
  runStatusesByRunId?: Record<string, string>;
  streamingTextByPartId?: Record<string, string>;
  messageScopesById?: Record<string, MessageScope>;
  subagentsBySessionId?: Record<string, SubagentSession>;
  subagentByTaskPartId?: Record<string, string>;
};
</script>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import ComposerArea from "./ComposerArea.vue";
import OpencodeTimeline from "./opencode-like/components/OpencodeTimeline.vue";
import RuntimeControls from "./RuntimeControls.vue";
import { createOpencodeLikeState } from "./opencode-like/state/adapter";
import { partSignature, scrollViewportToBottom, viewportIsAtBottom } from "./chat-utils";

const props = withDefaults(defineProps<AssistantThreadProps>(), {
  mode: "build",
  agents: () => [],
  models: () => [],
  providers: () => [],
  todos: () => [],
  todoSnapshotsByUserMessageId: () => ({}),
  streamingTextByPartId: () => ({}),
  messageScopesById: () => ({}),
  subagentsBySessionId: () => ({}),
  subagentByTaskPartId: () => ({})
});
const emit = defineEmits<{
  send: [prompt: string, attachments: ComposerAttachment[]];
  cancel: [];
  retry: [];
  openDiff: [];
  agentChange: [agentId: string];
  providerChange: [providerId: string];
  modelChange: [modelId: string];
  modeChange: [mode: string];
  requestNotifications: [];
}>();

const viewportRef = ref<HTMLElement | null>(null);
const isAtBottom = ref(true);
const hasNewContent = ref(false);
const userInterrupted = ref(false);
const activeSubagentSessionId = ref<string | null>(null);
const workStatusDockRef = ref<HTMLElement | null>(null);
let isProgrammaticScroll = false;

const timelineState = computed(() =>
  createOpencodeLikeState({
    messages: props.messages,
    running: props.running,
    providers: props.providers,
    models: props.models,
    todos: props.todos,
    todoSnapshotsByUserMessageId: props.todoSnapshotsByUserMessageId,
    runStatusesByRunId: props.runStatusesByRunId,
    streamingTextByPartId: props.streamingTextByPartId,
    messageScopesById: props.messageScopesById,
    subagentsBySessionId: props.subagentsBySessionId,
    subagentByTaskPartId: props.subagentByTaskPartId,
    activeSubagentSessionId: activeSubagentSessionId.value
  })
);

function selectSubagent(sessionId: string) {
  if (props.subagentsBySessionId[sessionId]) {
    activeSubagentSessionId.value = sessionId;
  }
}

function returnToRootAgent() {
  activeSubagentSessionId.value = null;
}

watch(
  () => props.subagentsBySessionId,
  (value) => {
    if (activeSubagentSessionId.value && !value[activeSubagentSessionId.value]) {
      activeSubagentSessionId.value = null;
    }
  }
);

// 流式内容指纹：变化时触发自动滚动
const streamSignature = computed(() =>
  props.messages
    .map((message) => {
      if (message.role === "assistant") {
        return `${message.id}:${message.text.length}:${message.parts?.map((part) => partSignature(part)).join("|") ?? ""}`;
      }
      return `${message.id}:${message.role}`;
    })
    .join("::")
);

function scrollToBottomProgrammatically(behavior: ScrollBehavior) {
  const viewport = viewportRef.value;
  if (!viewport) return;
  isProgrammaticScroll = true;
  scrollViewportToBottom(viewport, behavior);
  setTimeout(() => {
    isProgrammaticScroll = false;
  }, 50);
}

watch(
  streamSignature,
  (sig, prev) => {
    if (sig === prev) return;

    if (!userInterrupted.value && isAtBottom.value) {
      scrollToBottomProgrammatically("auto");
      hasNewContent.value = false;
    } else {
      hasNewContent.value = true;
    }
  },
  { flush: "post" }
);

// 首次挂载后滚到底部
nextTick(() => {
  scrollToBottomProgrammatically("auto");
});

function handleViewportScroll(event: Event) {
  const viewport = event.currentTarget as HTMLElement;
  if (isProgrammaticScroll) {
    isProgrammaticScroll = false;
    return;
  }

  const atBottom = viewportIsAtBottom(viewport, 36);
  isAtBottom.value = atBottom;
  if (atBottom) {
    hasNewContent.value = false;
    userInterrupted.value = false;
  } else {
    // 只要用户滚动离开底部，就激活锁定跟滚，防范任何流式追加导致的强制下拉
    userInterrupted.value = true;
  }
}

function jumpToBottom() {
  scrollToBottomProgrammatically("smooth");
  userInterrupted.value = false;
  hasNewContent.value = false;
  isAtBottom.value = true;
}


</script>

<template>
  <div
    class="ta-assistant-thread"
    :data-has-new-content="hasNewContent"
    :data-user-interrupted="userInterrupted"
    :data-is-at-bottom="isAtBottom"
  >
    <div
      ref="viewportRef"
      data-testid="agent-thread-viewport"
      class="ta-thread-viewport"
      @scroll="handleViewportScroll"
    >
      <div v-if="!messages.length" class="flex h-full flex-col items-center justify-center gap-1 py-10 text-center text-[var(--ta-muted)]">
        <div class="text-[13px] font-semibold text-[var(--ta-chat-text)]">开始与测试智能体对话</div>
        <div class="text-[12px] text-[var(--ta-chat-muted)]">描述测试任务，例如：跑 checkout 模块并分析失败原因</div>
      </div>
      <OpencodeTimeline
        :state="timelineState"
        :work-status-dock-target="activeSubagentSessionId ? undefined : workStatusDockRef"
        @open-diff="emit('openDiff')"
        @select-subagent="selectSubagent"
      />
      <button
        v-if="hasNewContent"
        type="button"
        class="sticky bottom-2 left-1/2 z-10 -translate-x-1/2 rounded-full border border-[var(--ta-chat-border)] bg-[var(--ta-chat-surface)] px-3 py-1 text-[11px] text-[var(--ta-chat-text)] shadow-sm hover:bg-[var(--ta-chat-hover)]"
        @click="jumpToBottom"
      >
        查看新内容
      </button>
    </div>
    <div
      v-if="!activeSubagentSessionId"
      ref="workStatusDockRef"
      data-testid="assistant-work-status-dock"
    />
    <ComposerArea
      v-if="!activeSubagentSessionId"
      :running="running"
      :commands="commands"
      :resources="resources"
      @send="(prompt, attachments) => emit('send', prompt, attachments)"
      @cancel="emit('cancel')"
      @retry="emit('retry')"
    />
    <div v-else class="ta-subagent-notice">
      <span>子 Agent 不支持对话，</span>
      <button type="button" class="ta-subagent-notice__return" @click="returnToRootAgent">切换到主 Agent</button>
      <span>。</span>
    </div>
    <RuntimeControls
      v-if="!activeSubagentSessionId"
      :agents="agents"
      :models="models"
      :commands="commands"
      :providers="providers"
      :selected-agent="selectedAgent"
      :selected-provider="selectedProvider"
      :selected-model="selectedModel"
      :mode="mode"
      @agent-change="(v) => emit('agentChange', v)"
      @provider-change="(v) => emit('providerChange', v)"
      @model-change="(v) => emit('modelChange', v)"
      @mode-change="(v) => emit('modeChange', v)"
      @request-notifications="emit('requestNotifications')"
    />
  </div>
</template>

<style scoped>
.ta-subagent-notice {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  border-top: 1px solid var(--ta-chat-border);
  color: var(--ta-chat-muted);
  font-size: 13px;
  line-height: 20px;
}

.ta-subagent-notice__return {
  border: 0;
  background: transparent;
  color: var(--ta-accent);
  font: inherit;
  font-weight: 600;
  padding: 0;
  cursor: pointer;
}

.ta-subagent-notice__return:hover {
  color: var(--ta-accent-strong, var(--ta-accent));
  text-decoration: underline;
}
</style>
