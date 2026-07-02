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
};
</script>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import { Bot, UserRound } from "lucide-vue-next";
import AgentCard from "./AgentCard.vue";
import ComposerArea from "./ComposerArea.vue";
import MessageParts from "./MessageParts.vue";
import PlainAnswer from "./PlainAnswer.vue";
import RuntimeControls from "./RuntimeControls.vue";
import TaskBreakdown from "./TaskBreakdown.vue";
import {
  partSignature,
  scrollViewportToBottom,
  shouldOpenCardByDefault,
  viewportIsAtBottom
} from "./chat-utils";

const props = withDefaults(defineProps<AssistantThreadProps>(), {
  mode: "build",
  agents: () => [],
  models: () => [],
  providers: () => [],
  todos: () => []
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

// 默认展开的卡片：最新的 tool / diff 卡片
const defaultOpenCardIds = computed(() => {
  const reversed = [...props.messages].reverse();
  return {
    latestToolId: reversed.find((m) => m.role === "card" && m.cardType === "tool")?.id,
    latestDiffId: reversed.find((m) => m.role === "card" && m.cardType === "diff")?.id
  };
});

const lastAssistantIndex = computed(() => {
  for (let i = props.messages.length - 1; i >= 0; i--) {
    if (props.messages[i].role === "assistant") {
      return i;
    }
  }
  return -1;
});

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
      <template v-for="(message, index) in messages" :key="message.id">
        <AgentCard
          v-if="message.role === 'card'"
          :message="message"
          :default-open="shouldOpenCardByDefault(message, defaultOpenCardIds)"
          @open-diff="emit('openDiff')"
        />
        <div v-else-if="message.role === 'assistant'" class="flex justify-start gap-2">
          <span
            class="mt-6 flex h-8 w-8 shrink-0 items-center justify-center rounded border border-[var(--ta-chat-border)] bg-[var(--ta-chat-detail-bg)] text-[var(--ta-chat-subtle)]"
            aria-hidden="true"
          >
            <Bot class="h-4 w-4" />
          </span>
          <div class="max-w-[calc(100%_-_44px)] rounded-md border border border-[var(--ta-chat-border)] bg-[var(--ta-chat-message-bg)] px-3 py-3">
            <MessageParts
              v-if="message.parts?.length"
              :parts="message.parts"
              :fallback-text="message.text"
              :running="running && index === lastAssistantIndex"
            />
            <PlainAnswer v-else :text="message.text" />
          </div>
        </div>
        <div v-else class="flex justify-end gap-2">
          <div class="max-w-[78%] rounded-xl bg-ta-chat-user-bg px-3 py-2 text-[var(--ta-chat-text)]">
            <p class="m-0 whitespace-pre-wrap text-[13px] leading-6 text-[var(--ta-chat-text)]">{{ message.text }}</p>
          </div>
          <span
            class="flex h-8 w-8 shrink-0 items-center justify-center rounded bg-[var(--ta-control-strong)] text-[var(--ta-subtle)]"
            aria-hidden="true"
          >
            <UserRound class="h-4 w-4" />
          </span>
        </div>
      </template>
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
