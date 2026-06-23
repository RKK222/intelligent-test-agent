<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type ReasoningPartBlockProps = {
  part: Extract<MessagePart, { type: "reasoning" }>;
  openByDefault: boolean;
};
</script>

<script setup lang="ts">
// 思考状态 part：
// - 默认折叠（除非正在生成或还没出最终回答）
// - 文本默认按 Markdown 渲染，但模型思考经常是大段散文+少量列表，仍保留"查看原文"切换
// - 暴露 part.durationMs（来自 step-finish 累计），让用户感知思考耗时
import { computed } from "vue";
import { Code2, FileText, Timer } from "lucide-vue-next";
import ProcessDisclosure from "./ProcessDisclosure.vue";
import MarkdownView from "./MarkdownView.vue";
import { normalizeProcessStatus } from "./process-status";
import { ref } from "vue";

const props = defineProps<ReasoningPartBlockProps>();
const status = computed(() => normalizeProcessStatus(props.part.status ?? "not_started"));
const showRaw = ref(false);
const summary = computed(() => props.part.title ?? (status.value === "running" ? "正在整理信息" : "思考状态"));
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
  <ProcessDisclosure
    :id="part.partId"
    :test-id="`reasoning-part-${part.partId}`"
    title="思考状态"
    :status="status"
    status-kind="thinking"
    :summary="summary"
    :default-open="openByDefault"
  >
    <div class="space-y-2">
      <div class="flex items-center justify-between gap-2 text-[11px] text-[var(--ta-chat-muted)]">
        <span v-if="durationLabel" class="inline-flex items-center gap-1">
          <Timer class="h-3 w-3" />
          思考耗时 {{ durationLabel }}
        </span>
        <div class="ml-auto flex items-center gap-1">
          <button
            v-if="!showRaw"
            type="button"
            class="inline-flex items-center gap-1 rounded border border-[var(--ta-chat-border)] bg-[var(--ta-chat-surface)] px-1.5 py-0.5 text-[10px] text-[var(--ta-chat-muted)] hover:border-[var(--ta-chat-border-strong)] hover:text-[var(--ta-chat-text)]"
            title="切换为源码视图"
            @click="showRaw = true"
          >
            <Code2 class="h-3 w-3" />
            源码
          </button>
          <button
            v-else
            type="button"
            class="inline-flex items-center gap-1 rounded border border-[var(--ta-chat-border-strong)] bg-[var(--ta-chat-chip-bg)] px-1.5 py-0.5 text-[10px] text-[var(--ta-chat-text)]"
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
        :source="part.text || '暂无详细思考内容'"
        body-class="max-h-44 overflow-auto pr-1 text-[12px] leading-6 text-[var(--ta-chat-muted)]"
      />
      <pre
        v-else
        data-testid="reasoning-part-raw"
        class="max-h-44 overflow-auto whitespace-pre-wrap rounded border border-[var(--ta-chat-border)] bg-[var(--ta-chat-detail-bg)] p-2 text-[12px] leading-6 text-[var(--ta-chat-muted)]"
      >{{ part.text || "暂无详细思考内容" }}</pre>
    </div>
  </ProcessDisclosure>
</template>
