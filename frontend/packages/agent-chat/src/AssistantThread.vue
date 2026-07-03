<script lang="ts">
import type {
  AgentInfo,
  AgentMessage,
  CommandInfo,
  ModelInfo,
  ProviderInfo,
  RuntimeResourceInfo,
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
  streamingTextByPartId?: Record<string, string>;
};
</script>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import ComposerArea from "./ComposerArea.vue";
import OpencodeTimeline from "./opencode-like/components/OpencodeTimeline.vue";
import RuntimeControls from "./RuntimeControls.vue";
import TaskBreakdown from "./TaskBreakdown.vue";
import { createOpencodeLikeState } from "./opencode-like/state/adapter";
import { partSignature, scrollViewportToBottom, viewportIsAtBottom } from "./chat-utils";

const props = withDefaults(defineProps<AssistantThreadProps>(), {
  mode: "build",
  agents: () => [],
  models: () => [],
  providers: () => [],
  todos: () => [],
  streamingTextByPartId: () => ({})
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

const timelineState = computed(() =>
  createOpencodeLikeState({
    messages: props.messages,
    running: props.running,
    providers: props.providers,
    models: props.models,
    todos: props.todos,
    streamingTextByPartId: props.streamingTextByPartId
  })
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

let firstPaint = true;
watch(
  streamSignature,
  (sig, prev) => {
    const viewport = viewportRef.value;
    if (!viewport) return;
    if (sig === prev) return;
    if (firstPaint || isAtBottom.value) {
      scrollViewportToBottom(viewport, firstPaint ? "auto" : "smooth");
      hasNewContent.value = false;
    } else {
      hasNewContent.value = true;
    }
    firstPaint = false;
  },
  { flush: "post" }
);

// 首次挂载后滚到底部
nextTick(() => {
  if (viewportRef.value) {
    scrollViewportToBottom(viewportRef.value, "auto");
  }
});

function handleViewportScroll(event: Event) {
  const viewport = event.currentTarget as HTMLElement;
  isAtBottom.value = viewportIsAtBottom(viewport);
  if (isAtBottom.value) {
    hasNewContent.value = false;
  }
}

function jumpToBottom() {
  if (viewportRef.value) {
    scrollViewportToBottom(viewportRef.value, "smooth");
  }
  hasNewContent.value = false;
  isAtBottom.value = true;
}

onBeforeUnmount(() => {
  firstPaint = true;
});
</script>

<template>
  <div class="ta-assistant-thread">
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
      <TaskBreakdown :todos="todos" />
      <OpencodeTimeline :state="timelineState" @open-diff="emit('openDiff')" />
      <button
        v-if="hasNewContent"
        type="button"
        class="sticky bottom-2 left-1/2 z-10 -translate-x-1/2 rounded-full border border-[var(--ta-chat-border)] bg-[var(--ta-chat-surface)] px-3 py-1 text-[11px] text-[var(--ta-chat-text)] shadow-sm hover:bg-[var(--ta-chat-hover)]"
        @click="jumpToBottom"
      >
        查看新内容
      </button>
    </div>
    <ComposerArea
      :running="running"
      :commands="commands"
      :resources="resources"
      @send="(prompt, attachments) => emit('send', prompt, attachments)"
      @cancel="emit('cancel')"
      @retry="emit('retry')"
    />
    <RuntimeControls
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
