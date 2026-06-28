<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type AnswerPartProps = {
  part: Extract<MessagePart, { type: "text" }>;
};
</script>

<script setup lang="ts">
// 最终回答气泡：
// - 默认按 Markdown 渲染（懒加载 markdown-it + dompurify + highlight.js）
// - 用户可一键切回"查看原文"，应对长代码块、复杂表格或渲染异常时的回退
// - 流式中 part.status === "running" 时显示打字光标，提示仍在生成
import { computed, ref } from "vue";
import { Code2, FileText } from "lucide-vue-next";
import MarkdownView from "./MarkdownView.vue";

const props = defineProps<AnswerPartProps>();
const showRaw = ref(false);
const streaming = computed(() => (props.part.status ?? "").toLowerCase() === "running");
</script>

<template>
  <div
    data-testid="answer-part"
    class="rounded-md border border-[var(--ta-chat-answer-border)] bg-[var(--ta-chat-answer-bg)] px-3 py-2.5"
  >
    <div class="mb-1.5 flex items-center gap-1.5 text-[11px] font-semibold text-[var(--ta-chat-subtle)]">
      <span>最终回答</span>
      <span v-if="streaming" class="ml-1 inline-flex items-center gap-0.5 text-[var(--ta-chat-status-running)]">
        <span class="h-1.5 w-1.5 animate-pulse rounded-full bg-current" />
        生成中
      </span>
      <div class="ml-auto flex items-center gap-1">
        <button
          v-if="!showRaw"
          type="button"
          class="inline-flex items-center gap-1 rounded border border-[var(--ta-chat-border)] bg-[var(--ta-chat-process-bg)] px-1.5 py-0.5 text-[10px] font-normal text-[var(--ta-chat-muted)] hover:border-[var(--ta-chat-border-strong)] hover:text-[var(--ta-chat-text)]"
          title="切换为源码视图"
          @click="showRaw = true"
        >
          <Code2 class="h-3 w-3" />
          源码
        </button>
        <button
          v-else
          type="button"
          class="inline-flex items-center gap-1 rounded border border-[var(--ta-chat-border-strong)] bg-[var(--ta-chat-chip-bg)] px-1.5 py-0.5 text-[10px] font-normal text-[var(--ta-chat-text)] hover:border-[var(--ta-chat-border-strong)]"
          title="切换为渲染视图"
          @click="showRaw = false"
        >
          <FileText class="h-3 w-3" />
          渲染
        </button>
      </div>
    </div>
    <MarkdownView
      v-if="!showRaw"
      :source="part.text"
      body-class="text-[13px] leading-[1.4] tracking-[-0.01em] text-[var(--ta-chat-text)]"
    />
    <pre
      v-else
      data-testid="answer-part-raw"
      class="max-h-[60vh] overflow-auto whitespace-pre-wrap rounded border border-[var(--ta-chat-border)] bg-[var(--ta-chat-detail-bg)] p-2 font-mono text-[12px] leading-[1.4] tracking-[-0.01em] text-[var(--ta-chat-text)]"
    >{{ part.text }}</pre>
  </div>
</template>
