<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type ReasoningPartBlockProps = {
  part: Extract<MessagePart, { type: "reasoning" }>;
  openByDefault: boolean;
  running?: boolean;
};
</script>

<script setup lang="ts">
// 思考状态 part：
// - 默认折叠（除非正在生成或还没出最终回答）
// - 文本默认按 Markdown 渲染，但模型思考经常是大段散文+少量列表，仍保留"查看原文"切换
// - 暴露 part.durationMs（来自 step-finish 累计），让用户感知思考耗时
import { computed } from "vue";
import { Code2, FileText, Timer, ChevronDown, ChevronRight } from "lucide-vue-next";
import MarkdownView from "./MarkdownView.vue";
import { normalizeProcessStatus } from "./process-status";
import { ref } from "vue";

const props = defineProps<ReasoningPartBlockProps>();
const isRunning = computed(() => {
  const norm = normalizeProcessStatus(props.part.status ?? "not_started");
  return norm === "running" && props.running;
});

const showRaw = ref(false);
const expanded = ref(props.openByDefault);

// 思考耗时：reducer 已把累计 durationMs 写入 part 字段；缺省时不展示
const durationLabel = computed(() => {
  const ms = props.part.durationMs;
  if (typeof ms !== "number" || !Number.isFinite(ms) || ms <= 0) {
    return null;
  }
  if (ms < 1000) return `${ms}ms`;
  const seconds = ms / 1000;
  if (seconds < 60) return `${seconds.toFixed(seconds < 10 ? 1 : 0)}s`;
  const minutes = Math.floor(seconds / 60);
  const rest = Math.round(seconds % 60);
  return `${minutes}m ${rest}s`;
});
</script>

<template>
  <div :data-testid="`reasoning-part-${part.partId}`" class="py-1 text-[12px]">
    <!-- 头部横行 -->
    <div class="flex items-center justify-between gap-2">
      <div class="flex items-center gap-2 min-w-0">
        <!-- 状态小圆点 -->
        <span
          :class="[
            'h-1.5 w-1.5 rounded-full shrink-0',
            isRunning ? 'animate-pulse bg-[var(--ta-cyan)]' : 'bg-[var(--ta-chat-muted)]'
          ]"
        />
        <!-- 思考状态文字（流光效果或普通颜色） -->
        <span
          :class="[
            'font-medium truncate',
            isRunning ? 'ta-text-shimmer' : 'text-[var(--ta-chat-subtle)]'
          ]"
        >
          <span v-if="isRunning">思考中...</span>
          <span v-else>思考过程</span>
        </span>
        <!-- 耗时标志 -->
        <span v-if="durationLabel" class="inline-flex items-center gap-0.5 text-[10px] text-[var(--ta-chat-muted)]">
          <Timer class="h-3 w-3" />
          {{ durationLabel }}
        </span>
      </div>

      <!-- 操作区：源码/渲染切换 + 展开收起 -->
      <div class="flex items-center gap-1.5 shrink-0">
        <button
          v-if="expanded && !showRaw"
          type="button"
          class="inline-flex items-center gap-0.5 rounded border border-[var(--ta-chat-border)] bg-[var(--ta-chat-surface)] px-1.5 py-0.5 text-[9px] text-[var(--ta-chat-muted)] hover:border-[var(--ta-chat-border-strong)] hover:text-[var(--ta-chat-text)]"
          title="切换为源码视图"
          @click.stop="showRaw = true"
        >
          <Code2 class="h-2.5 w-2.5" />
          源码
        </button>
        <button
          v-if="expanded && showRaw"
          type="button"
          class="inline-flex items-center gap-0.5 rounded border border-[var(--ta-chat-border-strong)] bg-[var(--ta-chat-chip-bg)] px-1.5 py-0.5 text-[9px] text-[var(--ta-chat-text)]"
          title="切换为渲染视图"
          @click.stop="showRaw = false"
        >
          <FileText class="h-2.5 w-2.5" />
          渲染
        </button>

        <button
          type="button"
          class="inline-flex h-5 items-center gap-0.5 rounded px-1.5 text-[10px] text-[var(--ta-chat-muted)] hover:bg-[var(--ta-chat-hover)] hover:text-[var(--ta-chat-text)] cursor-pointer"
          @click="expanded = !expanded"
        >
          <span>{{ expanded ? '收起' : '查看' }}</span>
          <ChevronDown v-if="expanded" class="h-3 w-3" />
          <ChevronRight v-else class="h-3 w-3" />
        </button>
      </div>
    </div>

    <!-- 折叠的思考文本内容 -->
    <div v-if="expanded" class="mt-2 border-l border-[var(--ta-chat-border)] ml-[3px] pl-3 space-y-2">
      <MarkdownView
        v-if="!showRaw"
        :source="part.text || '暂无详细思考内容'"
        body-class="max-h-44 overflow-auto pr-1 text-[12px] leading-[1.4] tracking-[-0.01em] text-[var(--ta-chat-muted)]"
      />
      <pre
        v-else
        data-testid="reasoning-part-raw"
        class="max-h-44 overflow-auto whitespace-pre-wrap rounded border border-[var(--ta-chat-border)] bg-[var(--ta-chat-detail-bg)] p-2 text-[12px] leading-[1.4] tracking-[-0.01em] text-[var(--ta-chat-muted)]"
      >{{ part.text || "暂无详细思考内容" }}</pre>
    </div>
  </div>
</template>
